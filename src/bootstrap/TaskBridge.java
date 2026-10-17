package dev.ronova.pro.bootstrap;

import java.util.Objects;
import java.util.List;
import java.util.Set;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

import static dev.ronova.pro.bootstrap.ControlBridge.*;

/** Actual task admission, execution, queues, waiters and retirement. */
public final class TaskBridge {
    public static final int ENTER=1,EXIT=2,SUBMIT=3,OVERFLOW=4,SUBMIT_ENTER=5,SUBMIT_EXIT=6,DISPATCH_ENTER=7,DISPATCH_EXIT=8,BODY=9,CANCELLED=10;
    static final java.util.Set<String> CF_STAGES=java.util.Set.of("uniApplyStage","uniAcceptStage","uniRunStage",
            "uniWhenCompleteStage","uniHandleStage","uniExceptionallyStage","uniComposeExceptionallyStage",
            "uniComposeStage","uniCopyStage","uniAsMinimalStage","biApplyStage","biAcceptStage","biRunStage",
            "orApplyStage","orAcceptStage","orRunStage","asyncSupplyStage","asyncRunStage","completedFuture","completedStage",
            "allOf","anyOf","completeAsync","orTimeout","completeOnTimeout");
    static final ThreadLocal<Frame> CURRENT=new ThreadLocal<>();
    static final ThreadLocal<Registration> DELIVERING=new ThreadLocal<>();
    static final ThreadLocal<java.util.ArrayDeque<Object[]>> SOURCE_DELIVERIES=new ThreadLocal<>();
    static final ThreadLocal<Overflow> OVERFLOW_DEPTH=new ThreadLocal<>();
    static final java.util.concurrent.atomic.AtomicLong DROPPED=new java.util.concurrent.atomic.AtomicLong();
    // Diagnostic loss never resets. Only failures with no real event identity veto every observer.
    static final java.util.concurrent.atomic.AtomicLong UNKNOWN_LOSS=new java.util.concurrent.atomic.AtomicLong();
    static long registrationEpoch;
    static volatile Registration registration;
    static volatile String coverage="AGENT_NOT_READY";
    static final java.util.Set<String> certified=new java.util.HashSet<>();
    private static final Set<String> futureBoundaries=new java.util.HashSet<>(),pendingBoundaries=new java.util.HashSet<>();
    static long certificationEpoch;
    private static volatile Map.Entry<Long,Set<String>> boundaryCapabilities=Map.entry(0L,Set.of());
    static final int ADMISSION_LIMIT=8192;
    static final Object BLOCKED=new Object();
    static final java.util.Map<EventKey,Set<Module>> TASK_OWNERS=new java.util.HashMap<>();
    static final java.util.Map<EventKey,java.lang.ref.WeakReference<Object>> TASK_SCHEDULERS=new java.util.HashMap<>();
    static final java.util.Set<EventKey> SHARED_SCHEDULERS=new java.util.HashSet<>();
    static final Map<EventKey,Set<Module>> SCHEDULER_SOURCES=new HashMap<>();
    static final Map<EventKey,Integer> SCHEDULER_SUBMISSIONS=new HashMap<>();
    static final Map<EventKey,SchedulerRetirement> SCHEDULER_RETIREMENTS=new HashMap<>();
    static final Map<EventKey,ThreadOrigin> THREAD_ORIGINS=new HashMap<>();
    static final Set<EventKey> RETIRED_THREADS=new java.util.HashSet<>();
    static final Map<EventKey,java.lang.ref.WeakReference<Object>> THREAD_TIMERS=new HashMap<>();
    private static final Object EQUIPMENT_ASYNC_LOCK=new Object();
    private static final java.lang.ref.ReferenceQueue<Object> EQUIPMENT_ASYNC_QUEUE=new java.lang.ref.ReferenceQueue<>();
    private static final Map<EventKey,AsyncBinding> EQUIPMENT_ASYNC_TASKS=new HashMap<>();
    private static final Map<EventKey,java.lang.ref.WeakReference<Object>> EQUIPMENT_ASYNC_CALLBACKS=new HashMap<>();
    private static volatile EquipmentAsync equipmentAsync;
    private static final class EquipmentAsync {
        final Class<?> owner;final java.lang.invoke.MethodHandle capture,invoke;
        EquipmentAsync(Class<?> owner,java.lang.invoke.MethodHandle capture,java.lang.invoke.MethodHandle invoke){this.owner=owner;this.capture=capture;this.invoke=invoke;}
    }
    private static volatile EquipmentDefined equipmentDefined;
    private static final class EquipmentDefined {
        final Class<?> owner;final java.lang.invoke.MethodHandle invoke;
        EquipmentDefined(Class<?> owner,java.lang.invoke.MethodHandle invoke){this.owner=owner;this.invoke=invoke;}
    }
    /** Shared by the real wrapper/delegate identities; only a leaf may claim it once. */
    private static final class AsyncBinding {
        final EquipmentAsync installation;Object ticket;boolean claimed;
        AsyncBinding(EquipmentAsync installation,Object ticket){this.installation=installation;this.ticket=ticket;}
    }
    static final class ThreadOrigin {
        final Set<Module> sources=Collections.newSetFromMap(new IdentityHashMap<>());
        final java.util.concurrent.locks.ReentrantLock io=new java.util.concurrent.locks.ReentrantLock();
        boolean unknown,interruptPending;int starting;
    }
    static final class ThreadStart {
        Thread thread,caller=Thread.currentThread();final ThreadOrigin origin;boolean closed;
        ThreadStart(Thread thread,ThreadOrigin origin){this.thread=thread;this.origin=origin;}
    }
    record InterruptRequest(Thread target,Object scheduler){}
    static final ThreadLocal<InterruptRequest> RETIRE_INTERRUPTS=new ThreadLocal<>();
    static final class SchedulerRetirement {
        final Set<Module> sources;
        final List<Runnable> removed=new ArrayList<>();
        boolean invoking,finished,resultUnknown;
        SchedulerRetirement(Set<Module> sources){this.sources=Set.copyOf(sources);}
    }
    static final class SchedulerRetired extends java.util.concurrent.RejectedExecutionException {
        SchedulerRetired(){super("RONOVA_PRIVATE_SCHEDULER_RETIRED");}
    }
    private record SchedulerCalls(java.lang.invoke.MethodHandle shutdown,java.lang.invoke.MethodHandle terminated) { }
    private static final java.util.concurrent.ConcurrentMap<Class<?>,SchedulerCalls> SCHEDULER_CALLS=new java.util.concurrent.ConcurrentHashMap<>();
    private static volatile java.lang.invoke.MethodHandle threadInterrupt;
    /** Definition metadata only. Thread targets and source ownership are still read at every use. */
    private static final ClassValue<Class<?>> THREAD_RUN_IMPLEMENTATIONS=new ClassValue<>() {
        @Override protected Class<?> computeValue(Class<?> type) {
            try{return type.getMethod("run").getDeclaringClass();}
            catch(NoSuchMethodException unavailable){throw new IllegalStateException("THREAD_RUN_UNAVAILABLE",unavailable);}
        }
    };
    static final java.util.Map<EventKey,Thread> TIMER_RUNNING=new java.util.HashMap<>();
    // Source registration already holds CREATION_OWNERS. Notify only a real
    // addition, before publishing it, so repeated observations keep a heap
    // callback's unchanged target permission.
    static void taskSource(Object object,Set<Module> sources,Module source){
        if(sources.contains(source))return;
        NativeControl.heapPolicyChanged(object);sources.add(source);
    }
    static void taskRelation(Object task,Object scheduler,Object parent) {
        if(task==null)return;
        Module[] actual=invokingSources();Module direct=actual.length==1?actual[0]:actual.length==0?invokingModule():null;if(direct==null&&actual.length==0&&parent!=null)direct=objectModule(parent);
        if(direct==null&&task.getClass().getClassLoader()!=null)direct=task.getClass().getModule();
        synchronized(CREATION_OWNERS) {
            reapCreationOwners();EventKey key=new EventKey(task,CREATION_QUEUE);
            Set<Module> owners=TASK_OWNERS.computeIfAbsent(key,ignored->java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<Module,Boolean>()));
            // A null entry represents an actual submission with no selected producer, not an inferred owner.
            if(actual.length==0)taskSource(task,owners,direct);else for(Module source:actual)taskSource(task,owners,source);
            Set<Module> creation=CREATION_SOURCES.get(new EventKey(task,null));if(creation!=null)for(Module source:creation)taskSource(task,owners,source);
            Module creator=CREATION_OWNERS.get(new EventKey(task,null));if(creator!=null)taskSource(task,owners,creator);
            Module definition=DefinitionBridge.module(task.getClass());if(producer(definition))taskSource(task,owners,definition);
            if(scheduler!=null) {
                TASK_SCHEDULERS.put(key,new java.lang.ref.WeakReference<>(scheduler));
                schedulerSources(scheduler,owners,CREATION_UNKNOWN.contains(key));
                Module poolCreator=CREATION_OWNERS.get(new EventKey(scheduler,null));
                if(poolCreator==null||owners.size()!=1||!owners.contains(poolCreator))SHARED_SCHEDULERS.add(new EventKey(scheduler,CREATION_QUEUE));
            }
        }
    }

    public static boolean privateScheduler(Object value,Module module) {
        return schedulerDisposition(value,new Module[]{module}).equals("PRIVATE");
    }
    public static String schedulerDisposition(Object value,Module[] modules){
        if(!coreWriter())throw new SecurityException("GROUP_CORE_REQUIRED");
        List<Object> chain=schedulerChain(value);Set<Module> selected=selectedSchedulerModules(modules);
        synchronized(CREATION_OWNERS){reapCreationOwners();return schedulerDisposition(chain,selected);}
    }
    static Set<Module> selectedSchedulerModules(Module[] modules){
        Set<Module> selected=Collections.newSetFromMap(new IdentityHashMap<>());
        if(modules!=null)Collections.addAll(selected,modules);selected.remove(null);return selected;
    }
    static String schedulerDisposition(List<Object> chain,Set<Module> selected){
        if(chain.isEmpty()||selected.isEmpty())return "UNRESOLVED";
        Object pool=chain.get(chain.size()-1);if(pool==java.util.concurrent.ForkJoinPool.commonPool())return "SHARED";
        SchedulerRetirement retiring=SCHEDULER_RETIREMENTS.get(new EventKey(pool,null));
        if(retiring!=null){
            if(!selected.containsAll(retiring.sources))return "SHARED";
            return retiring.invoking?"BUSY":"PRIVATE";
        }
        boolean unknown=false,busy=false;
        for(Object actual:chain){
            EventKey key=new EventKey(actual,null);Set<Module> creation=CREATION_SOURCES.get(key),uses=SCHEDULER_SOURCES.get(key);
            if(CREATION_UNKNOWN.contains(key)||creation==null||creation.isEmpty())unknown=true;
            if(creation!=null&&!selected.containsAll(creation))return "SHARED";
            if(uses!=null)for(Module source:uses){if(source==null)unknown=true;else if(!selected.contains(source))return "SHARED";}
            busy|=SCHEDULER_SUBMISSIONS.getOrDefault(key,0)!=0;
        }
        return unknown?"UNRESOLVED":busy?"BUSY":"PRIVATE";
    }
    static void schedulerSources(Object scheduler,Collection<Module> sources,boolean unknown){
        List<Object> chain=schedulerChain(scheduler);if(chain.isEmpty())chain=List.of(scheduler);
        for(Object actual:chain){
            Set<Module> uses=SCHEDULER_SOURCES.computeIfAbsent(new EventKey(actual,CREATION_QUEUE),ignored->Collections.newSetFromMap(new IdentityHashMap<>()));
            uses.addAll(sources);if(unknown||sources.isEmpty())uses.add(null);
        }
    }
    public static Object realScheduler(Object value) {
        if(!coreWriter())throw new SecurityException("GROUP_CORE_REQUIRED");
        return schedulerDelegate(value);
    }
    public static java.util.List<Runnable> retireScheduler(Object value,Module module)throws ReflectiveOperationException {
        return retireScheduler(value,new Module[]{module});
    }
    public static java.util.List<Runnable> retireScheduler(Object value,Module[] modules)throws ReflectiveOperationException {
        if(!coreWriter())throw new SecurityException("PRIVATE_SCHEDULER_CORE_REQUIRED");
        List<Object> chain=schedulerChain(value);Set<Module> selected=selectedSchedulerModules(modules);
        for(Module source:selected)if(!modStopped(source))throw new SecurityException("STOPPED_SCHEDULER_SOURCES_REQUIRED");
        SchedulerRetirement retiring;Object pool;
        synchronized(CREATION_OWNERS){reapCreationOwners();String disposition=schedulerDisposition(chain,selected);
            if(!disposition.equals("PRIVATE"))throw new IllegalStateException("SCHEDULER_"+disposition);
            pool=chain.get(chain.size()-1);EventKey key=new EventKey(pool,CREATION_QUEUE);
            retiring=SCHEDULER_RETIREMENTS.get(key);
            if(retiring==null){retiring=new SchedulerRetirement(selected);SCHEDULER_RETIREMENTS.put(key,retiring);}
            if(retiring.finished)return List.copyOf(retiring.removed);
            // The same monitor admits platform submissions. Seal the pool here,
            // but never hold this metadata monitor across shutdown callbacks.
            retiring.invoking=true;
        }
        InterruptRequest previous=RETIRE_INTERRUPTS.get();RETIRE_INTERRUPTS.set(new InterruptRequest(null,pool));
        try{
            @SuppressWarnings("unchecked") List<Runnable> removed=(List<Runnable>)schedulerCall(pool,"shutdownNow",List.class);
            synchronized(CREATION_OWNERS){
                retiring.removed.addAll(removed);retiring.finished=true;return List.copyOf(retiring.removed);
            }
        }catch(ReflectiveOperationException|RuntimeException|Error failure){
            synchronized(CREATION_OWNERS){retiring.resultUnknown=true;}throw failure;
        }finally{
            if(previous==null)RETIRE_INTERRUPTS.remove();else RETIRE_INTERRUPTS.set(previous);
            synchronized(CREATION_OWNERS){retiring.invoking=false;}
        }
    }
    public static void schedulerDrainAccepted(Object value,Object task){
        if(!coreWriter())throw new SecurityException("GROUP_SCHEDULER_CORE_REQUIRED");
        Object pool=schedulerDelegate(value);if(pool==null)throw new IllegalStateException("SCHEDULER_DELEGATION_UNRESOLVED");
        synchronized(CREATION_OWNERS){
            SchedulerRetirement retiring=SCHEDULER_RETIREMENTS.get(new EventKey(pool,null));
            if(retiring==null||!retiring.finished)throw new IllegalStateException("SCHEDULER_DRAIN_UNOBSERVED");
            retiring.removed.removeIf(actual->actual==task);
        }
    }
    public static boolean schedulerResourceEnded(Object value)throws ReflectiveOperationException {
        if(!coreWriter())throw new SecurityException("GROUP_SCHEDULER_CORE_REQUIRED");
        Object pool=schedulerDelegate(value);if(pool==null)throw new IllegalAccessException("SCHEDULER_DELEGATION_UNRESOLVED");
        synchronized(CREATION_OWNERS){
            SchedulerRetirement retiring=SCHEDULER_RETIREMENTS.get(new EventKey(pool,null));
            if(retiring!=null){
                if(retiring.resultUnknown)throw new IllegalStateException("SCHEDULER_DRAIN_RESULT_UNOBSERVED");
                if(retiring.invoking||!retiring.finished||!retiring.removed.isEmpty())return false;
            }
        }
        return Boolean.TRUE.equals(schedulerCall(pool,"isTerminated",boolean.class));
    }
    static Object schedulerCall(Object pool,String name,Class<?> result)throws ReflectiveOperationException {
        Class<?> base=pool instanceof java.util.concurrent.ThreadPoolExecutor?java.util.concurrent.ThreadPoolExecutor.class:java.util.concurrent.ForkJoinPool.class;
        SchedulerCalls calls=SCHEDULER_CALLS.get(base);
        if(calls==null) {
            var lookup=java.lang.invoke.MethodHandles.privateLookupIn(base,java.lang.invoke.MethodHandles.lookup());
            SchedulerCalls resolved=new SchedulerCalls(lookup.findSpecial(base,"shutdownNow",java.lang.invoke.MethodType.methodType(List.class),base),
                    lookup.findSpecial(base,"isTerminated",java.lang.invoke.MethodType.methodType(boolean.class),base));
            SchedulerCalls prior=SCHEDULER_CALLS.putIfAbsent(base,resolved);calls=prior==null?resolved:prior;
        }
        java.lang.invoke.MethodHandle method=switch(name){case "shutdownNow"->calls.shutdown;case "isTerminated"->calls.terminated;default->null;};
        if(method==null||method.type().returnType()!=result)throw new NoSuchMethodException("SCHEDULER_OPERATION_UNAVAILABLE:"+name);
        try {
            return method.invoke(pool);
        }catch(ReflectiveOperationException failure){throw failure;}
        catch(Throwable failure){throw new java.lang.reflect.InvocationTargetException(failure);}
    }
    public static boolean retireThread(Thread thread,Module module)throws ReflectiveOperationException {
        return retireThread(thread,new Module[]{module});
    }
    public static boolean retireThread(Thread thread,Module[] modules)throws ReflectiveOperationException {
        if(!coreWriter())throw new SecurityException("GROUP_THREAD_CORE_REQUIRED");
        synchronized(TaskBridge.class){if(!certified.contains("java/lang/Thread"))throw new IllegalStateException("THREAD_START_BOUNDARY_UNAVAILABLE");}
        Set<Module> selected=selectedSchedulerModules(modules);if(selected.isEmpty())throw new SecurityException("GROUP_THREAD_SOURCES_REQUIRED");
        for(Module source:selected)if(!modStopped(source))throw new SecurityException("STOPPED_THREAD_SOURCES_REQUIRED");
        Object target=ThreadPlatform.TARGET.get(thread);
        synchronized(CREATION_OWNERS){
            ThreadOrigin prior=THREAD_ORIGINS.get(new EventKey(thread,null));
            if(!thread.isAlive()&&ThreadPlatform.STATUS.getInt(thread)!=0&&(prior==null||prior.starting==0))return true;
        }
        if(threadWorker(thread,target)){
            Object scheduler=threadScheduler(thread,target);
            if(scheduler==null)throw new IllegalStateException("THREAD_SCHEDULER_UNOBSERVED");
            List<Object> chain=scheduler instanceof java.util.Timer?List.of(scheduler):schedulerChain(scheduler);
            String disposition;synchronized(CREATION_OWNERS){disposition=schedulerDisposition(chain,selected);}
            if(disposition.equals("SHARED"))return true;
            if(!disposition.equals("PRIVATE")&&!disposition.equals("BUSY"))throw new IllegalStateException("THREAD_SCHEDULER_"+disposition);
            // The scheduler retires all of its workers through its own contract.
            return !thread.isAlive()&&ThreadPlatform.STATUS.getInt(thread)!=0;
        }
        synchronized(CREATION_OWNERS){
            ThreadOrigin origin=threadOrigin(thread,target);if(!selected.containsAll(origin.sources))return true;
            if(origin.unknown||origin.sources.isEmpty())throw new IllegalStateException("THREAD_SOURCES_UNOBSERVED");
            RETIRED_THREADS.add(new EventKey(thread,CREATION_QUEUE));
            if(origin.starting!=0)return false;
            if(!thread.isAlive())return true;
        }
        requestThreadInterrupt(thread);
        // Interrupt requests retirement. Only actual thread exit settles the resource.
        return !thread.isAlive();
    }
    static void requestThreadInterrupt(Thread thread)throws ReflectiveOperationException {
        InterruptRequest previous=RETIRE_INTERRUPTS.get();RETIRE_INTERRUPTS.set(new InterruptRequest(thread,null));
        try {
            java.lang.invoke.MethodHandle interrupt=threadInterrupt;
            if(interrupt==null) {
                var lookup=java.lang.invoke.MethodHandles.privateLookupIn(Thread.class,java.lang.invoke.MethodHandles.lookup());
                interrupt=lookup.findSpecial(Thread.class,"interrupt",java.lang.invoke.MethodType.methodType(void.class),Thread.class);threadInterrupt=interrupt;
            }
            interrupt.invoke(thread);
        }catch(ReflectiveOperationException failure){throw failure;}
        catch(Throwable failure){throw new java.lang.reflect.InvocationTargetException(failure);}
        finally{if(previous==null)RETIRE_INTERRUPTS.remove();else RETIRE_INTERRUPTS.set(previous);}
    }
    static final class ThreadPlatform {
        static final Class<?> POOL_WORKER=type("java.util.concurrent.ThreadPoolExecutor$Worker"),TIMER_WORKER=type("java.util.TimerThread");
        static final java.lang.reflect.Field TARGET=field(Thread.class,"target"),STATUS=field(Thread.class,"threadStatus"),BLOCKER=field(Thread.class,"blocker"),
                WORKER_POOL=field(POOL_WORKER,"this$0"),FORK_POOL=field(java.util.concurrent.ForkJoinWorkerThread.class,"pool"),TIMER_THREAD=field(java.util.Timer.class,"thread");
        private static Class<?> type(String name){try{return Class.forName(name,false,null);}catch(ClassNotFoundException unavailable){throw new ExceptionInInitializerError(unavailable);}}
        private static java.lang.reflect.Field field(Class<?> owner,String name){
            try{var field=owner.getDeclaredField(name);field.setAccessible(true);return field;}
            catch(ReflectiveOperationException unavailable){throw new ExceptionInInitializerError(unavailable);}
        }
    }
    public static void prepareThreadBoundary(){
        if(!ControlBridge.owns(WALKER.getCallerClass()))throw new SecurityException("THREAD_BOUNDARY_AGENT_REQUIRED");
        Objects.requireNonNull(ThreadPlatform.TARGET);
    }
    static boolean threadWorker(Thread thread,Object target){
        return thread instanceof java.util.concurrent.ForkJoinWorkerThread||thread.getClass()==ThreadPlatform.TIMER_WORKER
                ||target!=null&&target.getClass()==ThreadPlatform.POOL_WORKER;
    }
    static Object threadScheduler(Thread thread,Object target)throws IllegalAccessException {
        if(thread instanceof java.util.concurrent.ForkJoinWorkerThread)return ThreadPlatform.FORK_POOL.get(thread);
        if(target!=null&&target.getClass()==ThreadPlatform.POOL_WORKER)return ThreadPlatform.WORKER_POOL.get(target);
        synchronized(CREATION_OWNERS){var timer=THREAD_TIMERS.get(new EventKey(thread,null));return timer==null?null:timer.get();}
    }
    public static Object threadScheduler(Thread thread)throws ReflectiveOperationException {
        if(!coreWriter())throw new SecurityException("GROUP_THREAD_CORE_REQUIRED");
        return threadScheduler(thread,ThreadPlatform.TARGET.get(thread));
    }
    static void timerThread(Object timer){
        try{
            Object thread=ThreadPlatform.TIMER_THREAD.get(timer);if(thread!=null)
                THREAD_TIMERS.put(new EventKey(thread,CREATION_QUEUE),new java.lang.ref.WeakReference<>(timer));
        }catch(IllegalAccessException unavailable){throw new IllegalStateException("TIMER_THREAD_UNOBSERVED",unavailable);}
    }
    static ThreadOrigin observedThreadOrigin(Thread thread){
        EventKey key=new EventKey(thread,null);ThreadOrigin origin=THREAD_ORIGINS.get(key);
        if(origin!=null)return origin;
        NativeControl.heapPolicyChanged(thread);origin=new ThreadOrigin();
        THREAD_ORIGINS.put(new EventKey(thread,CREATION_QUEUE),origin);return origin;
    }
    static void threadSources(Thread thread,ThreadOrigin origin,Object object){
        if(object==null)return;EventKey key=new EventKey(object,null);
        Set<Module> creation=CREATION_SOURCES.get(key),tasks=TASK_OWNERS.get(key);
        if(creation!=null)for(Module source:creation)taskSource(thread,origin.sources,source);
        if(tasks!=null)for(Module source:tasks)taskSource(thread,origin.sources,source);
        if(origin.sources.contains(null)||CREATION_UNKNOWN.contains(key)){
            if(!origin.unknown)NativeControl.heapPolicyChanged(thread);
            origin.sources.remove(null);origin.unknown=true;
        }
        Module definition=DefinitionBridge.module(object.getClass());if(producer(definition))taskSource(thread,origin.sources,definition);
    }
    static ThreadOrigin threadOrigin(Thread thread,Object target)throws NoSuchMethodException {
        ThreadOrigin origin=observedThreadOrigin(thread);
        threadSources(thread,origin,thread);threadSources(thread,origin,target);
        Class<?> implementation=THREAD_RUN_IMPLEMENTATIONS.get(thread.getClass());Module declaration=DefinitionBridge.module(implementation);
        if(producer(declaration))taskSource(thread,origin.sources,declaration);
        else if(implementation!=Thread.class&&!threadWorker(thread,target)){
            if(!origin.unknown)NativeControl.heapPolicyChanged(thread);origin.unknown=true;
        }
        return origin;
    }
    static boolean allThreadSourcesStopped(ThreadOrigin origin){
        if(origin.unknown||origin.sources.isEmpty())return false;
        for(Module source:origin.sources)if(!modStopped(source))return false;return true;
    }
    public static Object threadStarting(Thread thread)throws ReflectiveOperationException {
        if(WALKER.getCallerClass()!=Thread.class)throw new SecurityException("ACTUAL_THREAD_START_REQUIRED");
        return beginThreadStart(thread,null);
    }
    static Object beginThreadStart(Thread thread,Module[] nativeSources)throws ReflectiveOperationException {
        if(ThreadPlatform.STATUS.getInt(thread)!=0)return null;
        Object target=ThreadPlatform.TARGET.get(thread);boolean worker=threadWorker(thread,target);
        Module[] sources=worker?new Module[0]:invokingSources();boolean unknown=!worker&&CodeSourceBridge.executionUnknown();
        boolean stopped=!worker&&stoppedInvocation();
        synchronized(CREATION_OWNERS){
            reapCreationOwners();ThreadOrigin origin=threadOrigin(thread,target);
            if(!worker&&nativeSources!=null)for(Module source:nativeSources){
                if(source==null)unknown=true;
                else {stopped|=modStopped(source);if(producer(source))taskSource(thread,origin.sources,source);}
            }
            if(!worker&&(RETIRED_THREADS.contains(new EventKey(thread,null))||stopped))throw new IllegalThreadStateException("RONOVA_RETIRED_THREAD_START");
            for(Module source:sources)taskSource(thread,origin.sources,source);
            if(unknown&&!origin.unknown)NativeControl.heapPolicyChanged(thread);origin.unknown|=unknown;
            if(!worker&&allThreadSourcesStopped(origin))throw new IllegalThreadStateException("RONOVA_STOPPED_THREAD_START");
            ThreadStart token=new ThreadStart(thread,origin);origin.starting++;return token;
        }
    }
    public static void threadStartFinished(Object value){
        if(WALKER.getCallerClass()!=Thread.class)throw new SecurityException("ACTUAL_THREAD_START_REQUIRED");
        finishThreadStart(value);
    }
    static void finishThreadStart(Object value){
        if(value==null)return;
        if(!(value instanceof ThreadStart token)||token.caller!=Thread.currentThread())throw new SecurityException("ACTUAL_THREAD_START_TOKEN_REQUIRED");
        synchronized(CREATION_OWNERS){
            if(token.closed)return;token.closed=true;token.origin.starting--;token.thread=null;token.caller=null;
        }
    }
    public static boolean threadExecutionAllowed(Thread thread)throws ReflectiveOperationException {
        if(WALKER.getCallerClass()!=Thread.class)throw new SecurityException("ACTUAL_THREAD_RUN_REQUIRED");
        Object target=ThreadPlatform.TARGET.get(thread);if(threadWorker(thread,target))return true;
        synchronized(CREATION_OWNERS){
            return !RETIRED_THREADS.contains(new EventKey(thread,null))&&!allThreadSourcesStopped(threadOrigin(thread,target));
        }
    }
    public static Object threadInterrupting(Thread thread)throws ReflectiveOperationException {
        if(WALKER.getCallerClass()!=Thread.class)throw new SecurityException("ACTUAL_THREAD_INTERRUPT_REQUIRED");
        return beginThreadInterrupt(thread,null);
    }
    static Object beginThreadInterrupt(Thread thread,Module[] nativeSources)throws ReflectiveOperationException {
        InterruptRequest request=RETIRE_INTERRUPTS.get();
        if(request!=null&&!requestedThread(request,thread))return Boolean.FALSE;
        requireThreadControl(thread,2,nativeSources);
        if(request==null)return null;
        ThreadOrigin origin;synchronized(CREATION_OWNERS){origin=observedThreadOrigin(thread);}
        boolean locked=origin.io.tryLock();
        if(!locked)synchronized(CREATION_OWNERS){
            // A blocker-removal callback unlocks under this same monitor. Retry
            // there so a request cannot arrive just after its final pending check.
            locked=origin.io.tryLock();if(!locked){origin.interruptPending=true;return Boolean.FALSE;}
        }
        try{
            synchronized(CREATION_OWNERS){
                if(ThreadPlatform.BLOCKER.get(thread)!=null){origin.interruptPending=true;origin.io.unlock();return Boolean.FALSE;}
                origin.interruptPending=false;
            }
            return new ThreadIo(origin,false);
        }catch(ReflectiveOperationException|RuntimeException|Error failure){origin.io.unlock();throw failure;}
    }
    public static void threadInterruptFinished(Object value,boolean completed){
        if(WALKER.getCallerClass()!=Thread.class)throw new SecurityException("ACTUAL_THREAD_INTERRUPT_REQUIRED");
        finishThreadInterrupt(value);
    }
    static void finishThreadInterrupt(Object value){
        if(value==null)return;ThreadIo token=threadIoToken(value);
        synchronized(CREATION_OWNERS){if(!token.closed){token.closed=true;token.origin.io.unlock();}}
    }
    static boolean requestedThread(InterruptRequest request,Thread thread)throws IllegalAccessException {
        return request.target()==thread||request.scheduler()!=null&&threadScheduler(thread,ThreadPlatform.TARGET.get(thread))==request.scheduler();
    }
    static void requireThreadControl(Thread thread,int operation,Module[] nativeSources)throws ReflectiveOperationException {
        if(coreWriter())return;
        InterruptRequest request=RETIRE_INTERRUPTS.get();
        if(operation==2&&request!=null&&requestedThread(request,thread))return;
        if(nativeSources!=null)for(Module source:nativeSources)if(modStopped(source))throw new SecurityException("RONOVA_STOPPED_NATIVE_THREAD_CONTROL");
        String effect=switch(operation){case 2->"thread-interrupt";case 3->"thread-stop";case 4->"thread-suspend";
            case 5->"thread-resume";case 6->"thread-priority";case 7->"thread-name";default->throw new IllegalArgumentException("THREAD_CONTROL_OPERATION");};
        Object target=ThreadPlatform.TARGET.get(thread),scheduler=threadScheduler(thread,target);
        if(!taskEffectAllowed(thread,effect)||!resourceReleaseAllowed(thread)
                ||target!=null&&!resourceReleaseAllowed(target)||scheduler!=null&&!resourceReleaseAllowed(scheduler))
            throw new SecurityException("RONOVA_PROTECTED_THREAD_CONTROL");
    }
    public static void threadControl(Thread thread,int operation)throws ReflectiveOperationException {
        if(WALKER.getCallerClass()!=Thread.class||operation<3||operation>7)throw new SecurityException("ACTUAL_THREAD_CONTROL_REQUIRED");
        requireThreadControl(thread,operation,null);
    }
    public static Object threadBlocking(Object blocker)throws ReflectiveOperationException,java.io.InterruptedIOException {
        if(WALKER.getCallerClass()!=Thread.class)throw new SecurityException("ACTUAL_THREAD_BLOCKER_REQUIRED");
        Thread thread=Thread.currentThread();ThreadOrigin origin;
        synchronized(CREATION_OWNERS){origin=observedThreadOrigin(thread);}
        origin.io.lock();
        try{
            if(blocker!=null){
                Object scheduler=threadScheduler(thread,ThreadPlatform.TARGET.get(thread));
                synchronized(CREATION_OWNERS){
                    if(RETIRED_THREADS.contains(new EventKey(thread,null))||origin.interruptPending
                            ||scheduler!=null&&SCHEDULER_RETIREMENTS.containsKey(new EventKey(scheduler,null)))
                        throw new java.io.InterruptedIOException("RONOVA_RETIRED_THREAD_IO");
                }
            }
            return new ThreadIo(origin,blocker==null);
        }catch(ReflectiveOperationException|java.io.InterruptedIOException|RuntimeException|Error failure){origin.io.unlock();throw failure;}
    }
    public static void threadBlockerFinished(Object value,boolean completed)throws ReflectiveOperationException {
        if(WALKER.getCallerClass()!=Thread.class)throw new SecurityException("ACTUAL_THREAD_BLOCKER_REQUIRED");
        ThreadIo token=threadIoToken(value);
        synchronized(CREATION_OWNERS){
            if(token.closed)return;
            try{
                if(completed&&token.clearing&&token.origin.interruptPending&&ThreadPlatform.BLOCKER.get(Thread.currentThread())==null)
                    requestThreadInterrupt(Thread.currentThread());
            }finally{token.closed=true;token.origin.io.unlock();}
        }
    }
    public static void timerSubmitted(Object timer,Object task){
        if(WALKER.getCallerClass()!=java.util.Timer.class)throw new SecurityException("ACTUAL_TIMER_REQUIRED");
        synchronized(CREATION_OWNERS){timerThread(timer);}
        taskRelation(task,timer,null);
    }
    public static void timerAdmission(Object task){
        if(WALKER.getCallerClass()!=java.util.Timer.class)throw new SecurityException("ACTUAL_TIMER_REQUIRED");
        if(!modOwnerAllowed(task))throw new IllegalStateException("RONOVA_STOPPED_TIMER_PUBLICATION");
    }
    static void timerThreadCaller(){
        Class<?> caller=WALKER.walk(frames->frames.dropWhile(frame->frame.getDeclaringClass()==TaskBridge.class).findFirst().orElseThrow().getDeclaringClass());
        if(caller.getClassLoader()!=null||caller.getModule()!=Object.class.getModule()||!caller.getName().equals("java.util.TimerThread"))throw new SecurityException("ACTUAL_TIMER_THREAD_REQUIRED");
    }
    public static boolean timerEnter(Object task){
        timerThreadCaller();synchronized(CREATION_OWNERS){if(!modOwnerAllowed(task))return false;TIMER_RUNNING.put(new EventKey(task,CREATION_QUEUE),Thread.currentThread());return true;}
    }
    public static void timerExit(Object task){
        timerThreadCaller();synchronized(CREATION_OWNERS){EventKey key=new EventKey(task,null);if(TIMER_RUNNING.get(key)==Thread.currentThread())TIMER_RUNNING.remove(key);}
    }
    private static final class TimerPlatform {
        static final Class<?> TYPE=ThreadPlatform.type("java.util.TaskQueue");
        static final java.lang.reflect.Field QUEUE=ThreadPlatform.field(java.util.Timer.class,"queue"),TABLE=ThreadPlatform.field(TYPE,"queue"),SIZE=ThreadPlatform.field(TYPE,"size"),
                STATE=ThreadPlatform.field(java.util.TimerTask.class,"state"),LOCK=ThreadPlatform.field(java.util.TimerTask.class,"lock");
        static final java.lang.reflect.Method REMOVE=method("quickRemove",int.class),HEAP=method("heapify");
        private static java.lang.reflect.Method method(String name,Class<?>... arguments){
            try{var method=TYPE.getDeclaredMethod(name,arguments);method.setAccessible(true);return method;}
            catch(ReflectiveOperationException unavailable){throw new ExceptionInInitializerError(unavailable);}
        }
    }
    public static java.util.List<Runnable> retireTimer(Object value,Module module)throws ReflectiveOperationException {
        return retireTimer(value,new Module[]{module});
    }
    public static java.util.List<Runnable> retireTimer(Object value,Module[] modules)throws ReflectiveOperationException {
        if(!coreWriter()||value==null||value.getClass()!=java.util.Timer.class)throw new SecurityException("GROUP_TIMER_CORE_REQUIRED");
        Set<Module> selected=selectedSchedulerModules(modules);if(selected.isEmpty())throw new SecurityException("GROUP_TIMER_SOURCES_REQUIRED");
        for(Module source:selected)if(!modStopped(source))throw new SecurityException("STOPPED_TIMER_SOURCES_REQUIRED");
        Object queue=TimerPlatform.QUEUE.get(value);
        if(queue==null||queue.getClass()!=TimerPlatform.TYPE)throw new IllegalStateException("TIMER_QUEUE_LAYOUT_UNAVAILABLE");
        java.util.List<Runnable> removed=new java.util.ArrayList<>();java.util.Set<Object> seen=java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
        synchronized(queue){
            if(TimerPlatform.QUEUE.get(value)!=queue)throw new IllegalStateException("TIMER_QUEUE_OWNER_CHANGED");
            Object[] table=(Object[])TimerPlatform.TABLE.get(queue);
            for(int i=TimerPlatform.SIZE.getInt(queue);i>=1;i--){Object task=table[i];if(!selectedTimerTask(task,selected))continue;
                Object lock=TimerPlatform.LOCK.get(task);
                synchronized(lock){
                    if(TimerPlatform.QUEUE.get(value)!=queue||TimerPlatform.LOCK.get(task)!=lock)throw new IllegalStateException("TIMER_QUEUE_OWNER_CHANGED");
                    TimerPlatform.STATE.setInt(task,3);TimerPlatform.REMOVE.invoke(queue,i);removed.add((Runnable)task);seen.add(task);
                }
            }
            if(!removed.isEmpty())TimerPlatform.HEAP.invoke(queue);
        }
        java.util.List<Object> published=new java.util.ArrayList<>();synchronized(CREATION_OWNERS){
            for(var entry:TASK_SCHEDULERS.entrySet())if(entry.getValue().get()==value){Object task=entry.getKey().get();if(task!=null&&task instanceof java.util.TimerTask)published.add(task);}
        }
        for(Object task:published)if(!seen.contains(task)&&selectedTimerTask(task,selected)) {
            Object lock=TimerPlatform.LOCK.get(task);
            synchronized(lock){
                if(TimerPlatform.LOCK.get(task)!=lock)throw new IllegalStateException("TIMER_TASK_LOCK_CHANGED");
                TimerPlatform.STATE.setInt(task,3);removed.add((Runnable)task);
            }
        }
        synchronized(queue){
            if(TimerPlatform.QUEUE.get(value)!=queue)throw new IllegalStateException("TIMER_QUEUE_OWNER_CHANGED");
            // Successful publication holds this same queue monitor. Decide and cancel
            // without allowing a new outside submission between the two operations.
            boolean exclusive;synchronized(CREATION_OWNERS){
                exclusive=schedulerDisposition(List.of(value),selected).equals("PRIVATE");
                if(TimerPlatform.SIZE.getInt(queue)!=0){exclusive=false;SHARED_SCHEDULERS.add(new EventKey(value,CREATION_QUEUE));}
            }
            if(exclusive){
                ((java.util.Timer)value).cancel();
                Thread thread=(Thread)ThreadPlatform.TIMER_THREAD.get(value);
                synchronized(CREATION_OWNERS){RETIRED_THREADS.add(new EventKey(thread,CREATION_QUEUE));}
                requestThreadInterrupt(thread);
            }
        }
        return removed;
    }
    static boolean selectedTimerTask(Object task,Set<Module> selected){
        if(task==null)return false;synchronized(CREATION_OWNERS){
            EventKey key=new EventKey(task,null);if(CREATION_UNKNOWN.contains(key))return false;
            Set<Module> sources=TASK_OWNERS.get(key);if(sources==null)sources=CREATION_SOURCES.get(key);
            return sources!=null&&!sources.isEmpty()&&!sources.contains(null)&&selected.containsAll(sources);
        }
    }
    public static boolean timerResourceEnded(Object value)throws ReflectiveOperationException {
        if(!coreWriter()||value==null||value.getClass()!=java.util.Timer.class)throw new SecurityException("GROUP_TIMER_CORE_REQUIRED");
        synchronized(CREATION_OWNERS){if(SHARED_SCHEDULERS.contains(new EventKey(value,null)))return true;}
        return !((Thread)ThreadPlatform.TIMER_THREAD.get(value)).isAlive();
    }
    public static boolean timerResourceEnded(Object value,Module[] modules)throws ReflectiveOperationException {
        if(!coreWriter()||value==null||value.getClass()!=java.util.Timer.class)throw new SecurityException("GROUP_TIMER_CORE_REQUIRED");
        String disposition;synchronized(CREATION_OWNERS){disposition=schedulerDisposition(List.of(value),selectedSchedulerModules(modules));}
        if(disposition.equals("SHARED"))return true;
        if(!disposition.equals("PRIVATE"))throw new IllegalStateException("TIMER_"+disposition);
        return !((Thread)ThreadPlatform.TIMER_THREAD.get(value)).isAlive();
    }
    public static boolean timerTaskRetired(Object task)throws ReflectiveOperationException {
        if(!coreWriter()||!(task instanceof java.util.TimerTask))throw new SecurityException("GROUP_TIMER_CORE_REQUIRED");
        synchronized(CREATION_OWNERS){if(TIMER_RUNNING.containsKey(new EventKey(task,null)))return false;}
        Object lock=TimerPlatform.LOCK.get(task);
        synchronized(lock){return TimerPlatform.LOCK.get(task)==lock&&TimerPlatform.STATE.getInt(task)==3;}
    }
    static Object schedulerDelegate(Object value) {
        List<Object> chain=schedulerChain(value);return chain.isEmpty()?null:chain.get(chain.size()-1);
    }
    static final class SchedulerDelegates {
        static final Map<Class<?>,java.lang.reflect.Field> FIELDS=fields();
        static final java.lang.ref.ReferenceQueue<Object> QUEUE=new java.lang.ref.ReferenceQueue<>();
        static final Map<EventKey,SchedulerBinding> BINDINGS=new HashMap<>();
        private static Map<Class<?>,java.lang.reflect.Field> fields(){
            Map<Class<?>,java.lang.reflect.Field> fields=new IdentityHashMap<>();
            try{
                Class<?> base=Class.forName("java.util.concurrent.Executors$DelegatedExecutorService",false,null);
                java.lang.reflect.Field delegate=base.getDeclaredField("e");
                for(String name:List.of("DelegatedExecutorService","DelegatedScheduledExecutorService","FinalizableDelegatedExecutorService"))
                    fields.put(Class.forName("java.util.concurrent.Executors$"+name,false,null),delegate);
            }catch(ReflectiveOperationException unavailable){return Map.of();}
            return Collections.unmodifiableMap(fields);
        }
    }
    /** Weak actual chains plus their read handles. Disposition, submissions and termination are never retained. */
    private static final class SchedulerBinding {
        final List<java.lang.ref.WeakReference<Object>> objects;
        final List<java.lang.reflect.Field> delegates;
        final Object backendImage;
        SchedulerBinding(List<Object> chain,List<java.lang.reflect.Field> delegates,Object backendImage) {
            var references=new ArrayList<java.lang.ref.WeakReference<Object>>();
            for(Object actual:chain)references.add(new java.lang.ref.WeakReference<>(actual));
            objects=List.copyOf(references);this.delegates=List.copyOf(delegates);this.backendImage=backendImage;
        }
        List<Object> current(Object value) {
            if(backendImage!=boundaryCapabilities)return null;
            var chain=new ArrayList<Object>(objects.size());Object actual=value;
            for(int i=0;i<objects.size();i++) {
                if(actual==null||objects.get(i).get()!=actual)return null;
                chain.add(actual);
                if(i<delegates.size())try{actual=delegates.get(i).get(actual);}catch(IllegalAccessException unavailable){return null;}
            }
            return List.copyOf(chain);
        }
    }
    static List<Object> schedulerChain(Object value){
        if(value==null)return List.of();
        synchronized(SchedulerDelegates.BINDINGS) {
            EventKey stale;while((stale=(EventKey)SchedulerDelegates.QUEUE.poll())!=null)SchedulerDelegates.BINDINGS.remove(stale);
            EventKey key=new EventKey(value,null);SchedulerBinding previous=SchedulerDelegates.BINDINGS.get(key);
            if(previous!=null) {
                List<Object> actual=previous.current(value);if(actual!=null)return actual;
                SchedulerDelegates.BINDINGS.remove(key);
            }
            List<Object> chain=new ArrayList<>();var delegates=new ArrayList<java.lang.reflect.Field>();
            Set<Object> seen=Collections.newSetFromMap(new IdentityHashMap<>());Object current=value;
            while(current!=null&&seen.add(current)){
                chain.add(current);
                if(current instanceof java.util.concurrent.ThreadPoolExecutor||current instanceof java.util.concurrent.ForkJoinPool) {
                    SchedulerBinding resolved=new SchedulerBinding(chain,delegates,boundaryCapabilities);List<Object> actual=resolved.current(value);
                    if(actual==null)return List.of();
                    SchedulerDelegates.BINDINGS.put(new EventKey(value,SchedulerDelegates.QUEUE),resolved);return actual;
                }
                java.lang.reflect.Field delegate=SchedulerDelegates.FIELDS.get(current.getClass());
                if(delegate==null||!delegate.canAccess(current)&&!delegate.trySetAccessible())return List.of();
                delegates.add(delegate);
                try{current=delegate.get(current);}catch(IllegalAccessException unavailable){return List.of();}
            }
            return List.of();
        }
    }
    public static Object[][] groupTaskSnapshot(Module[] modules) {
        if(!coreWriter())throw new SecurityException("GROUP_CORE_REQUIRED");
        var selected=java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<Module,Boolean>());selected.addAll(java.util.Arrays.asList(modules));
        var rows=new java.util.ArrayList<Object[]>();
        synchronized(CREATION_OWNERS) {
            reapCreationOwners();for(var entry:TASK_OWNERS.entrySet()) {
                Object task=entry.getKey().get();if(task==null||entry.getValue().isEmpty()||!selected.containsAll(entry.getValue()))continue;
                var scheduler=TASK_SCHEDULERS.get(entry.getKey());rows.add(new Object[]{task,scheduler==null?null:scheduler.get()});
            }
        }
        return rows.toArray(Object[][]::new);
    }



    static final java.util.IdentityHashMap<Object,Admission> ADMISSIONS=new java.util.IdentityHashMap<>();
    static final java.util.IdentityHashMap<Object,Integer> SUBMISSIONS=new java.util.IdentityHashMap<>();
    static final ThreadLocal<Helper> HELPER=new ThreadLocal<>();
    static final java.util.IdentityHashMap<Object,Integer> HELD_TASKS=new java.util.IdentityHashMap<>();
    static final java.util.IdentityHashMap<Object[],Integer> HELD_ARRAYS=new java.util.IdentityHashMap<>();
    static final Object HELPER_LOCK=new Object();
    static int helperSlots;
    static final class Helper {
        final Helper parent=HELPER.get();
        final Thread owner=Thread.currentThread();
        // Thread-owned slot bookkeeping has integer keys only. TreeMap avoids the global
        // observed-HashMap source protocol for a table that is never a recovery source.
        final java.util.NavigableMap<Integer,Object> locals=new java.util.TreeMap<>();
        final Object pool;
        int handoff=-1;
        boolean closed;
        Helper(Object receiver) {
            pool=receiver instanceof java.util.concurrent.ForkJoinPool?receiver:
                    owner instanceof java.util.concurrent.ForkJoinWorkerThread worker?worker.getPool():parent==null?null:parent.pool;
        }
    }
    static int submissionEntries;
    static final ThreadLocal<Registration> AUTHORITY=new ThreadLocal<>();
    private static final Object FORK_QUEUE_LOCK=new Object();
    private static final java.lang.ref.ReferenceQueue<Object> FORK_QUEUE_GC=new java.lang.ref.ReferenceQueue<>();
    private static final Map<EventKey,ForkPoolImage> FORK_POOLS=new HashMap<>();
    private static final Map<EventKey,ForkPoolImage> FORK_POOL_ARRAYS=new HashMap<>();
    private static final Map<EventKey,ForkQueueImage> FORK_QUEUES=new HashMap<>();
    private static final Map<EventKey,ForkArrayImage> FORK_ARRAYS=new HashMap<>();
    private static long forkQueueSequence,forkQueueLosses;
    private static final Object FORK_QUIESCENCE_GATE=new Object();
    private static volatile long forkQueueQuiescentEpoch=-1;
    private static long forkQueueQuiescenceRetry;
    private static boolean forkQueueQuiescenceProbing;
    private static final String FORK_POOL_NAME="java.util.concurrent.ForkJoinPool",FORK_QUEUE_NAME=FORK_POOL_NAME+"$WorkQueue";
    private static final class ForkPoolImage {
        final java.lang.ref.WeakReference<Object> pool;
        java.lang.ref.WeakReference<Object[]> image=new java.lang.ref.WeakReference<>(null);
        final Set<ForkQueueImage> queues=Collections.newSetFromMap(new IdentityHashMap<>());
        final Map<EventKey,Set<ForkQueueImage>> locations=new HashMap<>();
        long epoch,seedAttempt=-1,certification=-1,losses=-1,retryAfter;
        int pending;
        boolean complete;
        ForkPoolImage(Object pool){this.pool=new java.lang.ref.WeakReference<>(pool);}
    }
    private static final class ForkQueueImage {
        final java.lang.ref.WeakReference<Object> queue;
        ForkPoolImage pool;
        ForkArrayImage array;
        int pending;
        ForkQueueImage(Object queue){this.queue=new java.lang.ref.WeakReference<>(queue);}
    }
    private static final class ForkArrayImage {
        final java.lang.ref.WeakReference<Object[]> array;
        final Map<Integer,EventKey> cells=new HashMap<>();
        final Map<EventKey,Integer> counts=new HashMap<>();
        final Set<ForkQueueImage> owners=Collections.newSetFromMap(new IdentityHashMap<>());
        boolean complete;
        ForkArrayImage(Object[] array){this.array=new java.lang.ref.WeakReference<>(array);}
    }
    private static final class ForkQueueTransition {
        final ForkPoolImage pool;
        final ForkQueueImage queue;
        final Thread thread=Thread.currentThread();
        boolean closed;
        ForkQueueTransition(ForkPoolImage pool,ForkQueueImage queue){this.pool=pool;this.queue=queue;}
    }
    private static final class ForkExternalMutation {
        final ForkPoolImage pool;
        final List<ForkQueueImage> queues;
        final Thread thread=Thread.currentThread();
        boolean closed;
        ForkExternalMutation(ForkPoolImage pool,List<ForkQueueImage> queues){this.pool=pool;this.queues=queues;}
    }
    private static final class ForkQueueLayout {
        static final Class<?> TYPE=queueType();
        static final java.lang.reflect.Field POOL=field(java.util.concurrent.ForkJoinPool.class,"queues"),MODE=field(java.util.concurrent.ForkJoinPool.class,"mode"),ARRAY=field(TYPE,"array"),OWNER=field(TYPE,"owner"),
                TOP=field(TYPE,"top"),BASE=field(TYPE,"base"),SOURCE=field(TYPE,"source");
        static final java.lang.reflect.Method REMOVE=remove();
        static Class<?> queueType(){try{return Class.forName(FORK_QUEUE_NAME,false,null);}catch(ClassNotFoundException unavailable){throw new ExceptionInInitializerError(unavailable);}}
        static java.lang.reflect.Field field(Class<?> owner,String name) {
            try {var field=owner.getDeclaredField(name);field.setAccessible(true);return field;}
            catch(ReflectiveOperationException unavailable){throw new ExceptionInInitializerError(unavailable);}
        }
        static java.lang.reflect.Method remove() {
            try {var method=TYPE.getDeclaredMethod("tryRemove",java.util.concurrent.ForkJoinTask.class,boolean.class);method.setAccessible(true);return method;}
            catch(ReflectiveOperationException unavailable){throw new ExceptionInInitializerError(unavailable);}
        }
    }
    private static void forkQueueCaller(Class<?> caller,boolean pool) {
        if(caller.getClassLoader()!=null||!(caller.getName().equals(FORK_QUEUE_NAME)||pool&&caller.getName().equals(FORK_POOL_NAME)))
            throw new SecurityException("FORK_QUEUE_PLATFORM_CALLER_REQUIRED");
    }
    private static void reapForkQueues() {
        EventKey key;
        while((key=(EventKey)FORK_QUEUE_GC.poll())!=null) {
            FORK_POOLS.remove(key);FORK_POOL_ARRAYS.remove(key);FORK_ARRAYS.remove(key);
            ForkQueueImage queue=FORK_QUEUES.remove(key);if(queue!=null)detachForkQueue(queue);
        }
    }
    private static ForkPoolImage forkPool(Object pool) {
        ForkPoolImage state=FORK_POOLS.get(new EventKey(pool,null));
        if(state==null){state=new ForkPoolImage(pool);FORK_POOLS.put(new EventKey(pool,FORK_QUEUE_GC),state);touchForkPool(state);}
        return state;
    }
    private static ForkQueueImage forkQueue(Object queue) {
        ForkQueueImage state=FORK_QUEUES.get(new EventKey(queue,null));
        if(state==null){state=new ForkQueueImage(queue);FORK_QUEUES.put(new EventKey(queue,FORK_QUEUE_GC),state);}
        return state;
    }
    private static void touchForkPool(ForkPoolImage pool) {
        if(forkQueueSequence==Long.MAX_VALUE){pool.complete=false;return;}
        pool.epoch=++forkQueueSequence;
    }
    private static void forkQueueObservationLost() {
        if(forkQueueLosses<Long.MAX_VALUE)forkQueueLosses++;
        else for(ForkPoolImage pool:FORK_POOLS.values())pool.complete=false;
    }
    private static void forkLocation(ForkQueueImage queue,EventKey task,boolean present) {
        ForkPoolImage pool=queue.pool;if(pool==null)return;
        if(present)pool.locations.computeIfAbsent(task,ignored->Collections.newSetFromMap(new IdentityHashMap<>())).add(queue);
        else {Set<ForkQueueImage> locations=pool.locations.get(task);if(locations!=null){locations.remove(queue);if(locations.isEmpty())pool.locations.remove(task);}}
    }
    private static void detachForkQueue(ForkQueueImage queue) {
        ForkPoolImage pool=queue.pool;if(pool==null)return;
        if(queue.array!=null)for(EventKey task:queue.array.counts.keySet())forkLocation(queue,task,false);
        pool.queues.remove(queue);pool.pending-=queue.pending;queue.pool=null;touchForkPool(pool);
    }
    private static ForkArrayImage forkArray(Object[] array) {
        ForkArrayImage state=FORK_ARRAYS.get(new EventKey(array,null));
        if(state!=null)return state;
        state=new ForkArrayImage(array);FORK_ARRAYS.put(new EventKey(array,FORK_QUEUE_GC),state);
        seedForkArray(state,array);return state;
    }
    private static void seedForkArray(ForkArrayImage state,Object[] array) {
        for(ForkQueueImage owner:state.owners)for(EventKey task:state.counts.keySet())forkLocation(owner,task,false);
        state.cells.clear();state.counts.clear();state.complete=true;
        for(int index=0;index<array.length;index++)if(array[index]!=null) {
            if(!(array[index] instanceof java.util.concurrent.ForkJoinTask<?>)){state.complete=false;continue;}
            EventKey task=new EventKey(array[index],null);state.cells.put(index,task);state.counts.merge(task,1,Integer::sum);
        }
        for(ForkQueueImage owner:state.owners) {
            for(EventKey task:state.counts.keySet())forkLocation(owner,task,true);
            if(owner.pool!=null&&!state.complete)owner.pool.complete=false;
        }
    }
    private static void bindForkArray(ForkQueueImage queue,Object[] array) {
        if(queue.array!=null) {
            for(EventKey task:queue.array.counts.keySet())forkLocation(queue,task,false);
            queue.array.owners.remove(queue);
        }
        queue.array=array==null?null:forkArray(array);
        if(queue.array!=null) {
            queue.array.owners.add(queue);
            for(EventKey task:queue.array.counts.keySet())forkLocation(queue,task,true);
            if(queue.pool!=null&&!queue.array.complete)queue.pool.complete=false;
        }
        if(queue.pool!=null)touchForkPool(queue.pool);
    }
    private static void bindForkPool(ForkPoolImage pool,Object[] image)throws IllegalAccessException {
        Set<ForkQueueImage> present=Collections.newSetFromMap(new IdentityHashMap<>());
        if(image!=null)for(Object value:image)if(value!=null) {
            if(value.getClass()!=ForkQueueLayout.TYPE){pool.complete=false;continue;}
            ForkQueueImage queue=forkQueue(value);present.add(queue);
            if(queue.pool!=pool) {
                if(queue.pool!=null){queue.pool.complete=false;detachForkQueue(queue);pool.complete=false;}
                queue.pool=pool;pool.queues.add(queue);pool.pending+=queue.pending;
                Object array=ForkQueueLayout.ARRAY.get(value);
                if(array!=null&&!(array instanceof java.util.concurrent.ForkJoinTask<?>[])){pool.complete=false;continue;}
                bindForkArray(queue,(Object[])array);
            }
        }
        for(ForkQueueImage queue:List.copyOf(pool.queues))if(!present.contains(queue))detachForkQueue(queue);
        Object[] prior=pool.image.get();if(prior!=null&&prior!=image)FORK_POOL_ARRAYS.remove(new EventKey(prior,null));
        pool.image=new java.lang.ref.WeakReference<>(image);
        if(image!=null)FORK_POOL_ARRAYS.put(new EventKey(image,FORK_QUEUE_GC),pool);
        touchForkPool(pool);
    }
    private static void recordForkCell(ForkArrayImage array,int index,Object before,Object after) {
        EventKey prior=array.cells.get(index);Object recorded=prior==null?null:prior.get();
        if(recorded!=before)array.complete=false;
        if(prior!=null) {
            Integer count=array.counts.get(prior);
            if(count==null||count<1)array.complete=false;
            else if(count==1){array.counts.remove(prior);for(ForkQueueImage owner:array.owners)forkLocation(owner,prior,false);}
            else array.counts.put(prior,count-1);
        }
        array.cells.remove(index);
        if(after!=null) {
            if(!(after instanceof java.util.concurrent.ForkJoinTask<?>))array.complete=false;
            else {
                EventKey task=new EventKey(after,null);array.cells.put(index,task);array.counts.merge(task,1,Integer::sum);
                for(ForkQueueImage owner:array.owners)forkLocation(owner,task,true);
            }
        }
        for(ForkQueueImage owner:array.owners)if(owner.pool!=null){if(!array.complete)owner.pool.complete=false;touchForkPool(owner.pool);}
    }
    private static ForkArrayImage observeForkArray(Object[] array) {
        try{return forkArray(array);}catch(Throwable unavailable){forkQueueObservationLost();return null;}
    }
    private static void observedForkCell(ForkArrayImage state,int index,Object before,Object after) {
        try{if(state!=null)recordForkCell(state,index,before,after);else forkQueueObservationLost();}
        catch(Throwable unavailable){forkQueueObservationLost();}
    }
    /** The actual platform mutation runs exactly once under the short receipt monitor. */
    public static java.util.concurrent.ForkJoinTask<?> forkQueueGetAndClear(java.lang.invoke.VarHandle handle,java.util.concurrent.ForkJoinTask<?>[] array,int index,Object ignored) {
        forkQueueCaller(WALKER.getCallerClass(),false);synchronized(FORK_QUEUE_LOCK) {
            boolean nested=OWN_LEDGER_MUTATION.get()!=null;OWN_LEDGER_MUTATION.set(true);
            try {ForkArrayImage state=observeForkArray(array);var prior=(java.util.concurrent.ForkJoinTask<?>)handle.getAndSet(array,index,(java.util.concurrent.ForkJoinTask<?>)null);observedForkCell(state,index,prior,null);return prior;}
            finally {if(!nested)OWN_LEDGER_MUTATION.remove();}
        }
    }
    public static boolean forkQueueCompareAndClear(java.lang.invoke.VarHandle handle,java.util.concurrent.ForkJoinTask<?>[] array,int index,java.util.concurrent.ForkJoinTask<?> expected,Object ignored) {
        forkQueueCaller(WALKER.getCallerClass(),false);synchronized(FORK_QUEUE_LOCK) {
            boolean nested=OWN_LEDGER_MUTATION.get()!=null;OWN_LEDGER_MUTATION.set(true);
            try {ForkArrayImage state=observeForkArray(array);boolean changed=handle.compareAndSet(array,index,expected,(java.util.concurrent.ForkJoinTask<?>)null);if(changed)observedForkCell(state,index,expected,null);return changed;}
            finally {if(!nested)OWN_LEDGER_MUTATION.remove();}
        }
    }
    public static void forkQueueSetVolatile(java.lang.invoke.VarHandle handle,java.util.concurrent.ForkJoinTask<?>[] array,int index,java.util.concurrent.ForkJoinTask<?> task) {
        forkQueueCaller(WALKER.getCallerClass(),false);synchronized(FORK_QUEUE_LOCK) {
            boolean nested=OWN_LEDGER_MUTATION.get()!=null;OWN_LEDGER_MUTATION.set(true);
            try {ForkArrayImage state=observeForkArray(array);Object prior=array[index];handle.setVolatile(array,index,task);observedForkCell(state,index,prior,task);}
            finally {if(!nested)OWN_LEDGER_MUTATION.remove();}
        }
    }
    public static void forkQueueArrayStore(Object[] array,int index,Object task) {
        forkQueueCaller(WALKER.getCallerClass(),false);synchronized(FORK_QUEUE_LOCK) {
            boolean nested=OWN_LEDGER_MUTATION.get()!=null;OWN_LEDGER_MUTATION.set(true);
            try {ForkArrayImage state=observeForkArray(array);Object prior=array[index];array[index]=task;observedForkCell(state,index,prior,task);}
            finally {if(!nested)OWN_LEDGER_MUTATION.remove();}
        }
    }
    public static void forkPoolQueueStore(Object[] array,int index,Object queue) {
        forkQueueCaller(WALKER.getCallerClass(),true);synchronized(FORK_QUEUE_LOCK) {
            boolean nested=OWN_LEDGER_MUTATION.get()!=null;OWN_LEDGER_MUTATION.set(true);
            try {
                array[index]=queue;
                try {ForkPoolImage pool=FORK_POOL_ARRAYS.get(new EventKey(array,null));if(pool!=null)bindForkPool(pool,array);}
                catch(Throwable unavailable){forkQueueObservationLost();}
            } finally {if(!nested)OWN_LEDGER_MUTATION.remove();}
        }
    }
    public static Object forkQueueFieldBegin(Object receiver,boolean poolField) {
        forkQueueCaller(WALKER.getCallerClass(),true);return beginForkQueueTransition(receiver,poolField);
    }
    private static Object beginForkQueueTransition(Object receiver,boolean poolField) {
        synchronized(FORK_QUEUE_LOCK) {
            boolean nested=OWN_LEDGER_MUTATION.get()!=null;OWN_LEDGER_MUTATION.set(true);
            try {
                ForkPoolImage pool=poolField?forkPool(receiver):null;ForkQueueImage queue=poolField?null:forkQueue(receiver);
                if(pool!=null){pool.pending++;touchForkPool(pool);}
                else {queue.pending++;if(queue.pool!=null){queue.pool.pending++;touchForkPool(queue.pool);}}
                return new ForkQueueTransition(pool,queue);
            } catch(Throwable unavailable){forkQueueObservationLost();return null;}
            finally {if(!nested)OWN_LEDGER_MUTATION.remove();}
        }
    }
    public static void forkQueueFieldEnd(Object token,Object image) {
        forkQueueCaller(WALKER.getCallerClass(),true);synchronized(FORK_QUEUE_LOCK) {
            boolean nested=OWN_LEDGER_MUTATION.get()!=null;OWN_LEDGER_MUTATION.set(true);
            try {
                if(!(token instanceof ForkQueueTransition transition)||transition.closed||transition.thread!=Thread.currentThread()){forkQueueObservationLost();return;}
                if(image!=null&&!(image instanceof Object[])){forkQueueObservationLost();return;}
                if(transition.pool!=null)bindForkPool(transition.pool,(Object[])image);else bindForkArray(transition.queue,(Object[])image);
                finishForkTransition(transition,true);
            } catch(Throwable unavailable){forkQueueObservationLost();if(token instanceof ForkQueueTransition transition&&!transition.closed)finishForkTransition(transition,false);}
            finally {if(!nested)OWN_LEDGER_MUTATION.remove();}
        }
    }
    public static Object forkQueueTransitionBegin(Object queue) {
        forkQueueCaller(WALKER.getCallerClass(),false);return beginForkQueueTransition(queue,false);
    }
    public static void forkQueueTransitionEnd(Object token,boolean normal) {
        forkQueueCaller(WALKER.getCallerClass(),false);synchronized(FORK_QUEUE_LOCK) {
            boolean nested=OWN_LEDGER_MUTATION.get()!=null;OWN_LEDGER_MUTATION.set(true);
            try {if(token instanceof ForkQueueTransition transition&&!transition.closed&&transition.thread==Thread.currentThread())finishForkTransition(transition,normal);else forkQueueObservationLost();}
            catch(Throwable unavailable){forkQueueObservationLost();}
            finally {if(!nested)OWN_LEDGER_MUTATION.remove();}
        }
    }
    private static void finishForkTransition(ForkQueueTransition transition,boolean normal) {
        transition.closed=true;
        ForkPoolImage pool=transition.pool;
        if(transition.queue!=null){transition.queue.pending--;pool=transition.queue.pool;}
        if(pool!=null){pool.pending--;if(!normal)pool.complete=false;touchForkPool(pool);}
    }
    /** Existing field/Unsafe/JNI/array receipts invalidate only actual platform queue receivers. */
    static Object externalQueueMutationBegin(Object receiver) {
        if(OWN_LEDGER_MUTATION.get()!=null||receiver==null)return null;
        Class<?> type=receiver.getClass();boolean pool=receiver instanceof java.util.concurrent.ForkJoinPool;
        boolean queue=type.getClassLoader()==null&&type.getName().equals(FORK_QUEUE_NAME);
        boolean tasks=receiver instanceof java.util.concurrent.ForkJoinTask<?>[];
        boolean queueArray=receiver instanceof Object[]&&type.getComponentType().getClassLoader()==null&&type.getComponentType().getName().equals(FORK_QUEUE_NAME);
        if(!pool&&!queue&&!tasks&&!queueArray)return null;
        if(WALKER.getCallerClass().getNestHost()!=ControlBridge.class)throw new SecurityException("FORK_QUEUE_MEMORY_RECEIPT_REQUIRED");
        synchronized(FORK_QUEUE_LOCK) {
            boolean nested=OWN_LEDGER_MUTATION.get()!=null;OWN_LEDGER_MUTATION.set(true);
            try {
                ForkPoolImage direct=pool?forkPool(receiver):queueArray?FORK_POOL_ARRAYS.get(new EventKey(receiver,null)):null;
                List<ForkQueueImage> owners;
                if(queue)owners=List.of(forkQueue(receiver));
                else if(tasks) {
                    ForkArrayImage array=FORK_ARRAYS.get(new EventKey(receiver,null));
                    if(array==null)return null;
                    array.complete=false;owners=List.copyOf(array.owners);
                } else owners=List.of();
                if(direct==null&&owners.isEmpty())return null;
                ForkExternalMutation mutation=new ForkExternalMutation(direct,owners);
                if(direct!=null){direct.pending++;direct.complete=false;touchForkPool(direct);}
                for(ForkQueueImage owner:owners) {
                    owner.pending++;
                    if(owner.pool!=null){owner.pool.pending++;owner.pool.complete=false;touchForkPool(owner.pool);}
                }
                return mutation;
            } catch(Throwable unavailable){forkQueueObservationLost();return null;}
            finally {if(!nested)OWN_LEDGER_MUTATION.remove();}
        }
    }
    static void externalQueueMutationEnd(Object token) {
        if(token==null)return;
        if(WALKER.getCallerClass().getNestHost()!=ControlBridge.class)throw new SecurityException("FORK_QUEUE_MEMORY_RECEIPT_REQUIRED");
        synchronized(FORK_QUEUE_LOCK) {
            boolean nested=OWN_LEDGER_MUTATION.get()!=null;OWN_LEDGER_MUTATION.set(true);
            try {
                if(!(token instanceof ForkExternalMutation mutation)||mutation.closed||mutation.thread!=Thread.currentThread()){forkQueueObservationLost();return;}
                mutation.closed=true;
                if(mutation.pool!=null){mutation.pool.pending--;mutation.pool.complete=false;mutation.pool.seedAttempt=-1;touchForkPool(mutation.pool);}
                for(ForkQueueImage owner:mutation.queues) {
                    owner.pending--;
                    if(owner.pool!=null){owner.pool.pending--;owner.pool.complete=false;owner.pool.seedAttempt=-1;touchForkPool(owner.pool);}
                }
            } catch(Throwable unavailable){forkQueueObservationLost();}
            finally {if(!nested)OWN_LEDGER_MUTATION.remove();}
        }
    }
    /** One pool seed covers entries which predate attachment; steady queries visit this task's locations only. */
    public static Object[] forkQueueSnapshot(Object value,Object task)throws ReflectiveOperationException {
        if(!(value instanceof java.util.concurrent.ForkJoinPool)||!(task instanceof java.util.concurrent.ForkJoinTask<?>))return new Object[]{-1L,-1,new Object[0]};
        long[] boundary=boundarySnapshot(new String[]{"java/util/concurrent/ForkJoinPool","java/util/concurrent/ForkJoinPool$WorkQueue"});
        if(!forkQueuePlatformReady(boundary))return new Object[]{-1L,-1,new Object[0]};
        Object[] result=forkQueueSnapshotLocked(value,task,boundary);
        long[] after=boundarySnapshot(new String[]{"java/util/concurrent/ForkJoinPool","java/util/concurrent/ForkJoinPool$WorkQueue"});
        return boundary[1]==1&&after[1]==1&&boundary[0]==after[0]?result:new Object[]{result[0],-1,new Object[0]};
    }
    /** Old uninstrumented mutation frames must physically retire before receipts can prove absence. */
    private static boolean forkQueuePlatformReady(long[] boundary) {
        if(boundary[1]!=1)return false;
        if(forkQueueQuiescentEpoch==boundary[0])return true;
        synchronized(FORK_QUIESCENCE_GATE) {
            if(forkQueueQuiescentEpoch==boundary[0])return true;
            if(forkQueueQuiescenceProbing||System.nanoTime()<forkQueueQuiescenceRetry)return false;
            forkQueueQuiescenceProbing=true;
        }
        boolean ready=false;
        try {
            ready=NativeControl.platformQueueQuiescent(java.util.concurrent.ForkJoinPool.class,ForkQueueLayout.TYPE);
            long[] after=boundarySnapshot(new String[]{"java/util/concurrent/ForkJoinPool","java/util/concurrent/ForkJoinPool$WorkQueue"});
            ready&=after[1]==1&&after[0]==boundary[0];
            if(ready)forkQueueQuiescentEpoch=boundary[0];
            return ready;
        } catch(RuntimeException|LinkageError unavailable){return false;}
        finally {synchronized(FORK_QUIESCENCE_GATE){forkQueueQuiescenceProbing=false;forkQueueQuiescenceRetry=System.nanoTime()+50_000_000L;}}
    }
    private static Object[] forkQueueSnapshotLocked(Object value,Object task,long[] boundary)throws ReflectiveOperationException {
        synchronized(FORK_QUEUE_LOCK) {
            boolean nested=OWN_LEDGER_MUTATION.get()!=null;OWN_LEDGER_MUTATION.set(true);
            try {
                reapForkQueues();ForkPoolImage pool=forkPool(value);
                if(boundary[1]!=1||forkQueueSequence==Long.MAX_VALUE||forkQueueLosses==Long.MAX_VALUE)return new Object[]{pool.epoch,-1,new Object[0]};
                Object image=ForkQueueLayout.POOL.get(value);
                if(image!=null&&!(image instanceof Object[]))return new Object[]{pool.epoch,-1,new Object[0]};
                if(pool.image.get()!=image){pool.complete=false;touchForkPool(pool);}
                if(pool.certification!=boundary[0]||pool.losses!=forkQueueLosses)pool.complete=false;
                if(!pool.complete&&pool.pending==0&&(pool.seedAttempt<0||System.nanoTime()>=pool.retryAfter||pool.certification!=boundary[0]||pool.losses!=forkQueueLosses)) {
                    pool.complete=image!=null||(ForkQueueLayout.MODE.getInt(value)&(1<<25))!=0;bindForkPool(pool,(Object[])image);
                    for(ForkQueueImage queue:pool.queues) {
                        Object actual=queue.queue.get();Object array=actual==null?null:ForkQueueLayout.ARRAY.get(actual);
                        if(actual==null||array!=null&&!(array instanceof java.util.concurrent.ForkJoinTask<?>[])){pool.complete=false;continue;}
                        int base=ForkQueueLayout.BASE.getInt(actual),top=ForkQueueLayout.TOP.getInt(actual);
                        boolean shared=ForkQueueLayout.OWNER.get(actual)==null;
                        if(shared&&ForkQueueLayout.SOURCE.getInt(actual)!=0){pool.complete=false;continue;}
                        java.lang.invoke.VarHandle.acquireFence();
                        if(queue.array==null||queue.array.array.get()!=array)bindForkArray(queue,(Object[])array);
                        if(queue.array!=null) {
                            seedForkArray(queue.array,(Object[])array);
                            int span=top-base;
                            if(!queue.array.complete||span<0||span>((Object[])array).length||queue.array.cells.size()!=span)pool.complete=false;
                        }
                        java.lang.invoke.VarHandle.acquireFence();
                        if(ForkQueueLayout.ARRAY.get(actual)!=array||ForkQueueLayout.BASE.getInt(actual)!=base||ForkQueueLayout.TOP.getInt(actual)!=top
                                ||shared&&ForkQueueLayout.SOURCE.getInt(actual)!=0)pool.complete=false;
                    }
                    if(ForkQueueLayout.POOL.get(value)!=image)pool.complete=false;
                    pool.certification=boundary[0];pool.losses=forkQueueLosses;pool.seedAttempt=pool.epoch;pool.retryAfter=System.nanoTime()+50_000_000L;
                }
                if(!pool.complete||pool.pending!=0)return new Object[]{pool.epoch,-1,new Object[0]};
                Set<ForkQueueImage> known=pool.locations.get(new EventKey(task,null));var queues=new ArrayList<Object>();
                if(known!=null)for(ForkQueueImage queue:known) {
                    Object actual=queue.queue.get();Object[] array=queue.array==null?null:queue.array.array.get();
                    if(actual==null||queue.pool!=pool||array==null||ForkQueueLayout.ARRAY.get(actual)!=array){pool.complete=false;touchForkPool(pool);return new Object[]{pool.epoch,-1,new Object[0]};}
                    queues.add(actual);
                }
                return new Object[]{pool.epoch,queues.isEmpty()?1:0,queues.toArray()};
            } catch(ReflectiveOperationException|RuntimeException|LinkageError unavailable){forkQueueObservationLost();throw unavailable;}
            finally {if(!nested)OWN_LEDGER_MUTATION.remove();}
        }
    }
    /** A negative version invalidates a readback during resize, registration, missing coverage or observation loss. */
    public static long forkQueueEpoch(Object value) {
        long[] boundary=boundarySnapshot(new String[]{"java/util/concurrent/ForkJoinPool","java/util/concurrent/ForkJoinPool$WorkQueue"});
        if(!forkQueuePlatformReady(boundary))return -1;
        long observed;
        synchronized(FORK_QUEUE_LOCK) {
            ForkPoolImage pool=FORK_POOLS.get(new EventKey(value,null));
            if(pool!=null)try {
                if(pool.image.get()!=ForkQueueLayout.POOL.get(value)){pool.complete=false;touchForkPool(pool);}
            } catch(ReflectiveOperationException|RuntimeException|LinkageError unavailable){forkQueueObservationLost();return -1;}
            observed=pool==null||!pool.complete||pool.pending!=0||boundary[1]!=1||pool.certification!=boundary[0]||pool.losses!=forkQueueLosses
                    ||forkQueueSequence==Long.MAX_VALUE||forkQueueLosses==Long.MAX_VALUE?-1:pool.epoch;
        }
        long[] after=boundarySnapshot(new String[]{"java/util/concurrent/ForkJoinPool","java/util/concurrent/ForkJoinPool$WorkQueue"});
        return boundary[1]==1&&after[1]==1&&boundary[0]==after[0]?observed:-1;
    }
    static final java.util.IdentityHashMap<Object,java.util.ArrayList<QueueRelease>> QUEUE_RELEASES=new java.util.IdentityHashMap<>();
    /** Published under the ledger lock; idle workers never take that lock for an unrelated queue. */
    static volatile Object[] queuesWithRelease=new Object[0];
    static int queueReleaseCount;
    static final java.util.IdentityHashMap<Object,java.util.ArrayList<Wait>> WAITERS=new java.util.IdentityHashMap<>();
    static final ThreadLocal<Wait> CURRENT_WAIT=new ThreadLocal<>();
    static int waiterEntries;
    static final java.util.WeakHashMap<Object,java.lang.ref.WeakReference<Object>> EXITED_WAIT_NODES=new java.util.WeakHashMap<>();
    static final class Wait {
        Object task;
        final Registration owner;
        Wait previous=CURRENT_WAIT.get();
        Object node;
        Thread thread=Thread.currentThread();
        boolean tracked,closed;
        Wait(Object task,Registration owner) { this.task=task;this.owner=owner; }
    }
    static final class QueueRelease {
        final Registration owner;
        final Object queue,task;
        final java.util.function.Predicate<Runnable> gate;
        boolean working,retired,cancelled;
        int stalled;
        long retryAfter;
        QueueRelease(Registration owner,Object queue,Object task,java.util.function.Predicate<Runnable> gate) {
            this.owner=owner;this.queue=queue;this.task=task;this.gate=gate;
        }
    }
    static final class Admission {
        Registration owner;
        int active;
        boolean fenced;
        long denied;
        Admission(Registration owner) { this.owner=owner; }
    }
    /** Agent publishes readiness only after retransformClasses has returned successfully. */
    public static synchronized void boundaryState(String name,boolean ready) {
        requireBoundaryAgent();
        boolean changed=ready?certified.add(name):certified.remove(name);
        changed|=ready?pendingBoundaries.remove(name):pendingBoundaries.add(name);
        if(changed)publishBoundaryCapabilities();
    }
    private static void requireBoundaryAgent() {
        if(TaskBridge.class.getClassLoader()==null&&!WALKER.walk(frames->frames
                .dropWhile(frame->frame.getDeclaringClass()==TaskBridge.class).findFirst()
                .map(frame->frame.getDeclaringClass().getName().equals("dev.ronova.pro.agent.RecoveryAgent")
                        &&frame.getDeclaringClass().getClassLoader()==ClassLoader.getSystemClassLoader()).orElse(false)))
            throw new SecurityException("TASK_BOUNDARY_AGENT_CALLER_REQUIRED");
    }
    /** Covers not-yet-loaded images only while the installed transformer remains their boundary. */
    public static synchronized void boundaryFutureTargets(String[] names) {
        requireBoundaryAgent();
        Set<String> replacement=Set.of(names);
        if(futureBoundaries.equals(replacement))return;
        futureBoundaries.clear();futureBoundaries.addAll(replacement);publishBoundaryCapabilities();
    }
    private static void publishBoundaryCapabilities() {
        certificationEpoch=Math.addExact(certificationEpoch,1);
        Set<String> usable=new java.util.HashSet<>(futureBoundaries);usable.addAll(certified);usable.removeAll(pendingBoundaries);
        boundaryCapabilities=Map.entry(certificationEpoch,Set.copyOf(usable));
    }
    /** Immutable installation image. Readers reuse membership results only for this exact image. */
    public static Map.Entry<Long,Set<String>> boundaryCapabilities() { return boundaryCapabilities; }
    public static synchronized long[] boundarySnapshot(String[] required) {
        return new long[]{certificationEpoch,required.length>0&&boundaryCapabilities.getValue().containsAll(java.util.List.of(required))?1:0};
    }
    public static synchronized boolean withBoundaries(String[] required,long expected,Runnable action) {
        Registration current=registration;
        if(current==null||!WALKER.walk(frames->frames.dropWhile(frame->frame.getDeclaringClass()==TaskBridge.class).findFirst()
                .map(frame->frame.getDeclaringClass().getNestHost()==current.sink.getClass().getNestHost()).orElse(false)))return false;
        if(expected!=certificationEpoch||required.length==0||!boundaryCapabilities.getValue().containsAll(java.util.List.of(required)))return false;
        Registration prior=AUTHORITY.get();AUTHORITY.set(registration);
        try { action.run();return true; }
        finally { if(prior==null)AUTHORITY.remove();else AUTHORITY.set(prior); }
    }
    private TaskBridge() {}
    public static int abiVersion(){return 102;}
    public static synchronized void installEquipmentAsync(Class<?> owner,java.lang.invoke.MethodHandle capture,java.lang.invoke.MethodHandle invoke){
        requirePolicyInstaller();if(guards==null||guards.owner()!=owner||equipmentAsync!=null&&equipmentAsync.owner!=owner)throw new SecurityException("EQUIPMENT_ASYNC_OWNER");
        equipmentAsync=new EquipmentAsync(owner,Objects.requireNonNull(capture).asType(java.lang.invoke.MethodType.methodType(Object.class,Object.class,Object.class,Object.class)),
                Objects.requireNonNull(invoke).asType(java.lang.invoke.MethodType.methodType(Object.class,Object.class,Object.class,Object.class,Object.class,String.class)));
    }
    public static synchronized void installEquipmentDefinedDelegate(Class<?> owner,java.lang.invoke.MethodHandle invoke){
        requirePolicyInstaller();if(guards==null||guards.owner()!=owner||equipmentDefined!=null&&equipmentDefined.owner!=owner)throw new SecurityException("EQUIPMENT_DEFINED_OWNER");
        equipmentDefined=new EquipmentDefined(owner,Objects.requireNonNull(invoke).asType(java.lang.invoke.MethodType.methodType(Object.class,Object.class,Object.class,Object.class,String.class)));
    }
    private static void reapEquipmentAsync(){
        EventKey stale;while((stale=(EventKey)EQUIPMENT_ASYNC_QUEUE.poll())!=null){EQUIPMENT_ASYNC_TASKS.remove(stale);EQUIPMENT_ASYNC_CALLBACKS.remove(stale);}
    }
    private static void bindEquipmentAsync(Object task,Object callback,AsyncBinding binding){
        if(task==null||callback==null||binding==null||binding.claimed||binding.ticket==null)return;
        EventKey lookup=new EventKey(task,null);AsyncBinding previous=EQUIPMENT_ASYNC_TASKS.get(lookup);
        if(previous!=null){
            java.lang.ref.WeakReference<Object> expected=EQUIPMENT_ASYNC_CALLBACKS.get(lookup);
            if(previous!=binding||expected==null||expected.get()!=callback){discardEquipmentAsync(previous);discardEquipmentAsync(binding);}
            return;
        }
        EventKey key=new EventKey(task,EQUIPMENT_ASYNC_QUEUE);EQUIPMENT_ASYNC_TASKS.put(key,binding);EQUIPMENT_ASYNC_CALLBACKS.put(key,new java.lang.ref.WeakReference<>(callback));
    }
    private static void discardEquipmentAsync(AsyncBinding binding){
        if(binding==null)return;binding.claimed=true;binding.ticket=null;
        for(var entries=EQUIPMENT_ASYNC_TASKS.entrySet().iterator();entries.hasNext();){var entry=entries.next();if(entry.getValue()==binding){EQUIPMENT_ASYNC_CALLBACKS.remove(entry.getKey());entries.remove();}}
    }
    /** No Core call occurs under either task/source metadata monitor. */
    private static void captureEquipmentAsync(Object task,Object scheduler,Object parent,Object callback){
        EquipmentAsync installation=equipmentAsync;if(installation==null||task==null||callback==null||DELIVERING.get()!=null)return;
        Registration observer=registration;
        synchronized(EQUIPMENT_ASYNC_LOCK){reapEquipmentAsync();if(EQUIPMENT_ASYNC_TASKS.containsKey(new EventKey(task,null)))return;}
        try{
            Object ticket=(Object)installation.capture.invokeExact(task,scheduler,parent);if(ticket==null)return;
            synchronized(EQUIPMENT_ASYNC_LOCK){reapEquipmentAsync();if(equipmentAsync==installation&&!EQUIPMENT_ASYNC_TASKS.containsKey(new EventKey(task,null)))bindEquipmentAsync(task,callback,new AsyncBinding(installation,ticket));}
        }catch(Throwable unavailable){lostTask(observer,11,task,scheduler,parent,null,unavailable);}
    }
    private static void captureEquipmentSubmission(Object task,Object scheduler,Object parent){
        // Completion callbacks are paired at their real constructor return with
        // the current stage's exact fn operand, rather than a generic parent.
        if(!EquipmentAsyncFields.completion(task))captureEquipmentAsync(task,scheduler,parent,task);
    }
    private static void inheritEquipmentCompletion(Object task){
        if(equipmentAsync==null)return;
        Object callback=EquipmentAsyncFields.completionCallback(task);if(callback==null)return;
        Frame frame=currentFrame(registration);
        if(frame==null||!(frame.task instanceof Inline inline)||inline.closed||inline.thread!=Thread.currentThread()||inline.callback!=callback)return;
        synchronized(EQUIPMENT_ASYNC_LOCK){reapEquipmentAsync();AsyncBinding binding=EQUIPMENT_ASYNC_TASKS.get(new EventKey(inline,null));if(binding!=null)bindEquipmentAsync(task,callback,binding);}
    }
    private static void inheritEquipmentDelegate(Object wrapper,Object delegate){
        if(equipmentAsync==null||wrapper==null||delegate==null||!EquipmentAsyncFields.delegate(wrapper,delegate))return;
        synchronized(EQUIPMENT_ASYNC_LOCK){
            reapEquipmentAsync();EventKey key=new EventKey(wrapper,null);AsyncBinding binding=EQUIPMENT_ASYNC_TASKS.get(key);
            java.lang.ref.WeakReference<Object> expected=EQUIPMENT_ASYNC_CALLBACKS.get(key);
            if(binding!=null&&expected!=null&&expected.get()==wrapper)bindEquipmentAsync(delegate,delegate,binding);
        }
    }
    private static void removeEquipmentTask(Object task){
        if(equipmentAsync==null||task==null)return;synchronized(EQUIPMENT_ASYNC_LOCK){reapEquipmentAsync();EventKey key=new EventKey(task,null);EQUIPMENT_ASYNC_TASKS.remove(key);EQUIPMENT_ASYNC_CALLBACKS.remove(key);}
    }
    private static void cancelEquipmentTask(Object task){
        if(equipmentAsync==null||task==null)return;synchronized(EQUIPMENT_ASYNC_LOCK){reapEquipmentAsync();discardEquipmentAsync(EQUIPMENT_ASYNC_TASKS.get(new EventKey(task,null)));}
    }
    private static AsyncBinding claimEquipmentAsync(Object callback){
        if(equipmentAsync==null||callback==null||DELIVERING.get()!=null||overflowing()||EquipmentAsyncFields.wrapper(callback))return null;
        // Only the current physical invocation owns this callback. An unrelated
        // nested task must not search an outer invocation for a matching operand.
        Frame frame=currentFrame(registration);
        if(frame==null||frame.dispatch||frame.exited||frame.blocked||frame.thread!=Thread.currentThread())return null;
        synchronized(EQUIPMENT_ASYNC_LOCK){
            reapEquipmentAsync();
            EventKey key=new EventKey(frame.task,null);AsyncBinding binding=EQUIPMENT_ASYNC_TASKS.get(key);java.lang.ref.WeakReference<Object> expected=EQUIPMENT_ASYNC_CALLBACKS.get(key);
            if(binding==null||binding.installation!=equipmentAsync||binding.claimed||binding.ticket==null||expected==null||expected.get()!=callback)return null;
            binding.claimed=true;
            for(var entries=EQUIPMENT_ASYNC_TASKS.entrySet().iterator();entries.hasNext();){var entry=entries.next();if(entry.getValue()==binding){EQUIPMENT_ASYNC_CALLBACKS.remove(entry.getKey());entries.remove();}}
            return binding;
        }
    }
    /** The original callback position, after the ordinary source/stop task guard. */
    public static Object invokeEquipmentCallback(Object callback,Object first,Object second,String contract){
        requireInvocation(callback);
        AsyncBinding binding=equipmentAsync!=null&&equipmentAsyncCaller(contract)?claimEquipmentAsync(callback):null;
        if(binding==null)return invokeEquipmentOriginal(callback,first,second,contract);
        try{return (Object)binding.installation.invoke.invokeExact(binding.ticket,callback,first,second,contract);}
        catch(Throwable original){throw TaskBridge.<RuntimeException>equipmentAsyncFailure(original);}
        finally{binding.ticket=null;}
    }
    /** The execution bridge has already matched this original SAM site and its actual operands. */
    public static Object invokeDefinedEquipmentCallback(Object callback,Object first,Object second,String contract){
        if(WALKER.getCallerClass()!=CodeSourceBridge.class)throw new SecurityException("ACTUAL_DEFINED_CALLBACK_BRIDGE_REQUIRED");
        requireInvocation(callback);
        EquipmentDefined installation=equipmentDefined;
        if(installation==null)return invokeEquipmentOriginal(callback,first,second,contract);
        try{return (Object)installation.invoke.invokeExact(callback,first,second,contract);}
        catch(Throwable original){throw TaskBridge.<RuntimeException>equipmentAsyncFailure(original);}
    }
    @SuppressWarnings("unchecked") private static <E extends Throwable> RuntimeException equipmentAsyncFailure(Throwable original)throws E{throw (E)original;}
    @SuppressWarnings("unchecked") private static Object invokeEquipmentOriginal(Object callback,Object first,Object second,String contract){
        try{return switch(contract){
            case "function"->((java.util.function.Function<Object,Object>)callback).apply(first);
            case "consumer"->{((Consumer<Object>)callback).accept(first);yield null;}
            case "supplier"->((java.util.function.Supplier<Object>)callback).get();
            case "runnable"->{((Runnable)callback).run();yield null;}
            case "callable"->((java.util.concurrent.Callable<Object>)callback).call();
            case "bifunction"->((java.util.function.BiFunction<Object,Object,Object>)callback).apply(first,second);
            case "biconsumer"->{((java.util.function.BiConsumer<Object,Object>)callback).accept(first,second);yield null;}
            case "predicate"->Boolean.valueOf(((java.util.function.Predicate<Object>)callback).test(first));
            default->throw new IllegalArgumentException("EQUIPMENT_ASYNC_CONTRACT");
        };}catch(Throwable original){throw TaskBridge.<RuntimeException>equipmentAsyncFailure(original);}
    }
    private static boolean equipmentAsyncCaller(String contract){
        return WALKER.walk(stream->{var frame=stream.dropWhile(value->value.getDeclaringClass()==TaskBridge.class).findFirst().orElse(null);if(frame==null)return false;
            Class<?> type=frame.getDeclaringClass();if(type.getClassLoader()!=null||type.getModule()!=Object.class.getModule())return false;
            String owner=type.getName(),method=frame.getMethodName(),descriptor=frame.getDescriptor();
            synchronized(TaskBridge.class){if(!certified.contains(owner.replace('.','/')))return false;}
            if(type==java.util.concurrent.CompletableFuture.class)return switch(method){
                case "uniApplyNow"->contract.equals("function")&&descriptor.equals("(Ljava/lang/Object;Ljava/util/concurrent/Executor;Ljava/util/function/Function;)Ljava/util/concurrent/CompletableFuture;");
                case "uniAcceptNow"->contract.equals("consumer")&&descriptor.equals("(Ljava/lang/Object;Ljava/util/concurrent/Executor;Ljava/util/function/Consumer;)Ljava/util/concurrent/CompletableFuture;");
                case "uniRunNow"->contract.equals("runnable")&&descriptor.equals("(Ljava/lang/Object;Ljava/util/concurrent/Executor;Ljava/lang/Runnable;)Ljava/util/concurrent/CompletableFuture;");
                case "uniWhenComplete"->contract.equals("biconsumer")&&descriptor.equals("(Ljava/lang/Object;Ljava/util/function/BiConsumer;Ljava/util/concurrent/CompletableFuture$UniWhenComplete;)Z");
                case "uniHandle"->contract.equals("bifunction")&&descriptor.equals("(Ljava/lang/Object;Ljava/util/function/BiFunction;Ljava/util/concurrent/CompletableFuture$UniHandle;)Z");
                case "uniExceptionally"->contract.equals("function")&&descriptor.equals("(Ljava/lang/Object;Ljava/util/function/Function;Ljava/util/concurrent/CompletableFuture$UniExceptionally;)Z");
                case "uniComposeStage","uniComposeExceptionallyStage"->contract.equals("function")&&descriptor.equals("(Ljava/util/concurrent/Executor;Ljava/util/function/Function;)Ljava/util/concurrent/CompletableFuture;");
                case "biApply"->contract.equals("bifunction")&&descriptor.equals("(Ljava/lang/Object;Ljava/lang/Object;Ljava/util/function/BiFunction;Ljava/util/concurrent/CompletableFuture$BiApply;)Z");
                case "biAccept"->contract.equals("biconsumer")&&descriptor.equals("(Ljava/lang/Object;Ljava/lang/Object;Ljava/util/function/BiConsumer;Ljava/util/concurrent/CompletableFuture$BiAccept;)Z");
                case "biRun"->contract.equals("runnable")&&descriptor.equals("(Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Runnable;Ljava/util/concurrent/CompletableFuture$BiRun;)Z");
                default->false;
            };
            if(type==java.util.concurrent.FutureTask.class)return contract.equals("callable")&&(method.equals("run")&&descriptor.equals("()V")||method.equals("runAndReset")&&descriptor.equals("()Z"));
            if(type==java.util.concurrent.ThreadPoolExecutor.class)return contract.equals("runnable")&&method.equals("runWorker")&&descriptor.equals("(Ljava/util/concurrent/ThreadPoolExecutor$Worker;)V");
            if(type==java.util.concurrent.ThreadPoolExecutor.CallerRunsPolicy.class)return contract.equals("runnable")&&method.equals("rejectedExecution")&&descriptor.equals("(Ljava/lang/Runnable;Ljava/util/concurrent/ThreadPoolExecutor;)V");
            if(owner.equals("java.util.concurrent.Executors$RunnableAdapter"))return contract.equals("runnable")&&method.equals("call")&&descriptor.equals("()Ljava/lang/Object;");
            if(owner.equals("java.util.concurrent.ForkJoinTask$AdaptedCallable"))return contract.equals("callable")&&method.equals("exec")&&descriptor.equals("()Z");
            if(Set.of("java.util.concurrent.ForkJoinTask$AdaptedRunnable","java.util.concurrent.ForkJoinTask$AdaptedRunnableAction","java.util.concurrent.ForkJoinTask$RunnableExecuteAction").contains(owner))return contract.equals("runnable")&&method.equals("exec")&&descriptor.equals("()Z");
            if(owner.equals("java.util.concurrent.CompletableFuture$AsyncSupply"))return contract.equals("supplier")&&method.equals("run")&&descriptor.equals("()V");
            if(owner.equals("java.util.concurrent.CompletableFuture$AsyncRun"))return contract.equals("runnable")&&method.equals("run")&&descriptor.equals("()V");
            if(!method.equals("tryFire")||!descriptor.equals("(I)Ljava/util/concurrent/CompletableFuture;"))return false;
            return switch(owner){
                case "java.util.concurrent.CompletableFuture$UniApply","java.util.concurrent.CompletableFuture$UniCompose","java.util.concurrent.CompletableFuture$UniComposeExceptionally","java.util.concurrent.CompletableFuture$OrApply"->contract.equals("function");
                case "java.util.concurrent.CompletableFuture$UniAccept","java.util.concurrent.CompletableFuture$OrAccept"->contract.equals("consumer");
                case "java.util.concurrent.CompletableFuture$UniRun","java.util.concurrent.CompletableFuture$OrRun"->contract.equals("runnable");
                default->false;
            };
        });
    }
    private static boolean equipmentCancellation(Object task,int expected,int update){
        if(equipmentAsync==null||!(task instanceof java.util.concurrent.FutureTask<?>)||expected!=0||update!=4&&update!=5)return false;
        return WALKER.walk(stream->stream.dropWhile(frame->frame.getDeclaringClass()==TaskBridge.class).findFirst().map(frame->{
            if(frame.getDeclaringClass()!=java.util.concurrent.FutureTask.class||!frame.getMethodName().equals("cancel")||!frame.getDescriptor().equals("(Z)Z"))return false;
            synchronized(TaskBridge.class){return certified.contains("java/util/concurrent/FutureTask");}
        }).orElse(false));
    }
    /** Only existing, exact JDK fields are read; no accessor, task or callback is invoked. */
    private static final class EquipmentAsyncFields {
        private static final Map<Class<?>,java.lang.reflect.Field> FIELDS=new HashMap<>();
        static boolean completion(Object task){Class<?> type=task==null?null:task.getClass();return type!=null&&type.getClassLoader()==null&&type.getModule()==Object.class.getModule()&&type.getName().startsWith("java.util.concurrent.CompletableFuture$");}
        private static Object read(Object target,Class<?> declaring,String name,Class<?> fieldType){
            try{
                java.lang.reflect.Field field;synchronized(FIELDS){field=FIELDS.get(declaring);if(field==null){field=declaring.getDeclaredField(name);if(field.getType()!=fieldType||java.lang.reflect.Modifier.isStatic(field.getModifiers()))throw new IllegalStateException("EQUIPMENT_ASYNC_FIELD_LAYOUT");field.setAccessible(true);FIELDS.put(declaring,field);}}
                if(!field.getName().equals(name)||field.getType()!=fieldType||!declaring.isInstance(target))throw new IllegalStateException("EQUIPMENT_ASYNC_FIELD_IDENTITY");return field.get(target);
            }catch(ReflectiveOperationException unavailable){throw new IllegalStateException("EQUIPMENT_ASYNC_FIELD_UNAVAILABLE",unavailable);}
        }
        static Object completionCallback(Object task){
            if(!completion(task))return null;Class<?> type=task.getClass();Class<?> callback=switch(type.getName()){
                case "java.util.concurrent.CompletableFuture$UniApply","java.util.concurrent.CompletableFuture$UniCompose","java.util.concurrent.CompletableFuture$UniComposeExceptionally","java.util.concurrent.CompletableFuture$UniExceptionally","java.util.concurrent.CompletableFuture$OrApply"->java.util.function.Function.class;
                case "java.util.concurrent.CompletableFuture$UniAccept","java.util.concurrent.CompletableFuture$OrAccept"->Consumer.class;
                case "java.util.concurrent.CompletableFuture$UniRun","java.util.concurrent.CompletableFuture$BiRun","java.util.concurrent.CompletableFuture$OrRun","java.util.concurrent.CompletableFuture$AsyncRun"->Runnable.class;
                case "java.util.concurrent.CompletableFuture$UniWhenComplete","java.util.concurrent.CompletableFuture$BiAccept"->java.util.function.BiConsumer.class;
                case "java.util.concurrent.CompletableFuture$UniHandle","java.util.concurrent.CompletableFuture$BiApply"->java.util.function.BiFunction.class;
                case "java.util.concurrent.CompletableFuture$AsyncSupply"->java.util.function.Supplier.class;
                default->null;
            };return callback==null?null:read(task,type,"fn",callback);
        }
        static boolean wrapper(Object callback){
            return callback instanceof java.util.concurrent.FutureTask<?>||callback instanceof java.util.concurrent.ForkJoinTask<?>||callback.getClass().getClassLoader()==null
                    &&callback.getClass().getModule()==Object.class.getModule()&&callback.getClass().getName().equals("java.util.concurrent.Executors$RunnableAdapter");
        }
        static boolean delegate(Object wrapper,Object actual){
            var frame=WALKER.walk(stream->stream.dropWhile(value->value.getDeclaringClass().getNestHost()==TaskBridge.class).findFirst().orElse(null));if(frame==null)return false;
            Class<?> caller=frame.getDeclaringClass();if(caller.getClassLoader()!=null||caller.getModule()!=Object.class.getModule())return false;
            String method=frame.getMethodName(),descriptor=frame.getDescriptor(),name=caller.getName();
            synchronized(TaskBridge.class){if(!certified.contains(name.replace('.','/')))return false;}
            if(caller==java.util.concurrent.FutureTask.class&&(method.equals("run")&&descriptor.equals("()V")||method.equals("runAndReset")&&descriptor.equals("()Z")))return wrapper instanceof java.util.concurrent.FutureTask<?> &&read(wrapper,caller,"callable",java.util.concurrent.Callable.class)==actual;
            if(wrapper.getClass()!=caller)return false;
            if(name.equals("java.util.concurrent.Executors$RunnableAdapter")&&method.equals("call")&&descriptor.equals("()Ljava/lang/Object;"))return read(wrapper,caller,"task",Runnable.class)==actual;
            if(!method.equals("exec")||!descriptor.equals("()Z"))return false;
            if(name.equals("java.util.concurrent.ForkJoinTask$AdaptedCallable"))return read(wrapper,caller,"callable",java.util.concurrent.Callable.class)==actual;
            if(Set.of("java.util.concurrent.ForkJoinTask$AdaptedRunnable","java.util.concurrent.ForkJoinTask$AdaptedRunnableAction","java.util.concurrent.ForkJoinTask$RunnableExecuteAction").contains(name))return read(wrapper,caller,"runnable",Runnable.class)==actual;
            return false;
        }
    }
    public static synchronized void installTaskPublicationGate(Class<?> owner,java.lang.invoke.MethodHandle callback) {
        requirePolicyInstaller();
        if(guards==null||guards.owner()!=owner)throw new SecurityException("TASK_PUBLICATION_OWNER");
        publicationGate=Objects.requireNonNull(callback).asType(
                java.lang.invoke.MethodType.methodType(AutoCloseable.class,Object.class,Object.class));
    }
    static final class Publication implements AutoCloseable {
        final AutoCloseable gate;boolean closed;
        Publication(AutoCloseable gate){this.gate=gate;}
        @Override public void close() {
            if(closed)return;closed=true;
            if(gate!=null)try {gate.close();}
            catch(Exception unavailable){throw new IllegalStateException("TASK_PUBLICATION_GATE_RELEASE",unavailable);}
        }
    }
    /** The caller releases this token at the last actual result write, before callbacks run. */
    public static Object beginTaskPublication(Object result) {
        Publication token=null;Registration observer=registration;
        try {
            var callback=publicationGate;
            token=new Publication(callback==null?null:(AutoCloseable)callback.invokeExact(result,currentTask()));
            if(!resultPublicationAllowed(result)){token.close();return Boolean.FALSE;}
            return token;
        } catch(Throwable failure) {
            if(token!=null)token.close();lostTask(observer,13,result,null,null,null,failure);return Boolean.FALSE;
        }
    }
    public static void endTaskPublication(Object token) {
        if(token instanceof Publication publication)publication.close();
    }
    /** Called inside the JDK's normal callback exception handling, so rejection completes its Future. */
    public static void requireInvocation(Object callback) {
        Guards current=guards;
        if(!taskEffectAllowed(current,callback,"invoke")||!taskEffectAllowed(current,currentTask(),"invoke"))
            throw new java.util.concurrent.CancellationException("RONOVA_TASK_INVOCATION_REFUSED");
    }
    static final class NativeClone {
        static final java.lang.invoke.MethodHandle CALL;
        static {
            try {
                var lookup=java.lang.invoke.MethodHandles.privateLookupIn(Object.class,java.lang.invoke.MethodHandles.lookup());
                CALL=lookup.findSpecial(Object.class,"clone",java.lang.invoke.MethodType.methodType(Object.class),Object.class);
            } catch(ReflectiveOperationException unavailable) { throw new ExceptionInInitializerError(unavailable); }
        }
    }

    /** Tracks the actual helper-frame local stores, including a return handoff to its caller. */
    public static Object helperEnter(Object receiver) {
        try {
            Class<?> caller=WALKER.getCallerClass();
            if(caller.getClassLoader()!=null||!(caller.getName().equals("java.util.concurrent.ForkJoinPool")||caller.getName().equals("java.util.concurrent.ForkJoinPool$WorkQueue")
                    ||caller.getName().equals("java.util.concurrent.CompletableFuture")||caller.getName().startsWith("java.util.concurrent.CompletableFuture$")))return null;
            Helper value=new Helper(receiver);HELPER.set(value);return value;
        } catch(Throwable unavailable) { lost();return null; }
    }
    public static void helperLocal(Object token,int slot,Object value) {
        try {
            if(!(token instanceof Helper frame)||frame.closed||frame.owner!=Thread.currentThread()||HELPER.get()!=frame)return;
            Object incoming=value instanceof java.util.concurrent.ForkJoinTask<?>||value instanceof java.util.concurrent.ForkJoinTask<?>[]?value:null;
            // The frame-local map is owned by this thread. Unrelated stores do not touch
            // the shared task-reference ledger unless they overwrite a tracked local.
            if(incoming==null&&!frame.locals.containsKey(slot))return;
            synchronized(HELPER_LOCK) {
                // A returned task is now represented by its real local, with no zero-count publication gap.
                replaceHelperLocal(frame,slot,incoming);
                if(incoming!=null&&frame.handoff!=-1) {
                    Integer handedOff=null;
                    for(var entry:frame.locals.entrySet()) {
                        if(entry.getKey()>=0)break;
                        if(entry.getValue()==incoming) {handedOff=entry.getKey();break;}
                    }
                    if(handedOff!=null)replaceHelperLocal(frame,handedOff,null);
                }
            }
        } catch(Throwable unavailable) { lost(); }
    }
    static void replaceHelperLocal(Helper frame,int slot,Object value) {
        boolean nested=OWN_LEDGER_MUTATION.get()!=null;OWN_LEDGER_MUTATION.set(true);
        try {
            Object prior=frame.locals.get(slot);if(prior==value)return;
            if(value!=null) {
                if(helperSlots>=65536) { lost();return; }
                if(value instanceof Object[] array)HELD_ARRAYS.merge(array,1,Integer::sum);else HELD_TASKS.merge(value,1,Integer::sum);helperSlots++;
            }
            if(prior!=null) {
                Integer count=prior instanceof Object[] array?HELD_ARRAYS.get(array):HELD_TASKS.get(prior);
                if(count==null||count<1)lost();else { if(count==1)HELD_TASKS.remove(prior);else HELD_TASKS.put(prior,count-1);helperSlots--; }
                if(prior instanceof Object[] array&&count!=null&&count>0) {
                    HELD_TASKS.remove(prior);if(count==1)HELD_ARRAYS.remove(array);else HELD_ARRAYS.put(array,count-1);
                }
            }
            if(value==null)frame.locals.remove(slot);else frame.locals.put(slot,value);
        } finally {if(!nested)OWN_LEDGER_MUTATION.remove();}
    }
    public static void helperExit(Object token,Object returned) {
        try {
            if(!(token instanceof Helper frame)||frame.closed||frame.owner!=Thread.currentThread()||HELPER.get()!=frame)return;
            synchronized(HELPER_LOCK) {
                if(frame.parent!=null&&(returned instanceof java.util.concurrent.ForkJoinTask<?>||returned instanceof java.util.concurrent.ForkJoinTask<?>[])) {
                    if(frame.parent.handoff==Integer.MIN_VALUE)lost();
                    else replaceHelperLocal(frame.parent,frame.parent.handoff--,returned);
                }
                while(!frame.locals.isEmpty())replaceHelperLocal(frame,frame.locals.firstKey(),null);
                frame.closed=true;
            }
            if(frame.parent==null)HELPER.remove();else HELPER.set(frame.parent);
        } catch(Throwable unavailable) { lost(); }
    }
    public static int helperReferences(Object task) {
        synchronized(HELPER_LOCK) {
            int count=HELD_TASKS.getOrDefault(task,0),cells=0;
            for(Object[] array:HELD_ARRAYS.keySet()) {
                if(array.length>131072-cells)return -1;cells+=array.length;
                for(Object value:array)if(value==task)count++;
            }
            return count;
        }
    }
    static boolean helperBoundary() {
        return WALKER.walk(frames->frames.dropWhile(frame->frame.getDeclaringClass()==TaskBridge.class).findFirst()
                .map(frame->frame.getDeclaringClass().getClassLoader()==null&&java.util.Set.of(
                        "java.util.concurrent.ForkJoinPool","java.util.concurrent.ForkJoinPool$WorkQueue")
                        .contains(frame.getDeclaringClass().getName())).orElse(false));
    }
    public static Object beginWait(Object task) {
        Registration observer=registration;
        try {
            if(task==null||!waitBoundary())return null;
            Wait wait=new Wait(task,observer);
            synchronized(TaskBridge.class) {
                if(waiterEntries>=ADMISSION_LIMIT)lostTask(observer,18,task,null,currentTask(),wait,null);
                else { WAITERS.computeIfAbsent(task,key->new java.util.ArrayList<>()).add(wait);waiterEntries++;wait.tracked=true; }
            }
            CURRENT_WAIT.set(wait);
            emit(wait.owner,18,task,null,currentTask(),wait);return wait;
        } catch(Throwable unavailable) {lostTask(observer,18,task,null,null,null,unavailable);return null;}
    }
    public static void endWait(Object token) {
        try {
            if(!(token instanceof Wait wait)||wait.thread!=Thread.currentThread()||wait.closed||CURRENT_WAIT.get()!=wait||!waitBoundary())return;
            synchronized(TaskBridge.class) {
                if(wait.tracked) {
                    var entries=WAITERS.get(wait.task);
                    if(entries==null||!entries.remove(wait))lostTask(wait.owner,19,wait.task,null,currentTask(),wait,null);else { waiterEntries--;if(entries.isEmpty())WAITERS.remove(wait.task); }
                }
                wait.closed=true;
                if(wait.node!=null) {
                    if(EXITED_WAIT_NODES.size()>=65536)lostTask(wait.owner,19,wait.task,null,currentTask(),wait,null);
                    else EXITED_WAIT_NODES.put(wait.node,new java.lang.ref.WeakReference<>(wait.task));
                }
            }
            try { emit(registration,wait.owner==registration?19:20,wait.task,null,currentTask(),wait); }
            finally {
                wait.thread=null;wait.node=null;wait.task=null;
                if(wait.previous==null)CURRENT_WAIT.remove();else CURRENT_WAIT.set(wait.previous);wait.previous=null;
            }
        } catch(Throwable unavailable) {
            if(token instanceof Wait wait)lostTask(wait.owner,19,wait.task,null,null,wait,unavailable);else lost(unavailable);
        }
    }
    public static synchronized int waiterCount(Object task) { var entries=WAITERS.get(task);return entries==null?0:entries.size(); }
    public static synchronized boolean waiterNodeObserved(Object task,Object node) {
        var entries=WAITERS.get(task);if(entries!=null)for(Wait wait:entries)if(wait.node==node&&!wait.closed)return true;
        var exited=EXITED_WAIT_NODES.get(node);return exited!=null&&exited.get()==task;
    }
    public static void waitNode(Object node) {
        Wait observed=CURRENT_WAIT.get();
        try {
            Wait wait=CURRENT_WAIT.get();if(wait==null||node==null)return;
            boolean valid=WALKER.walk(frames->{
                var callers=frames.dropWhile(frame->frame.getDeclaringClass()==TaskBridge.class).limit(2).toList();
                if(callers.size()!=2||!callers.get(0).getMethodName().equals("<init>")||callers.get(0).getDeclaringClass()!=node.getClass()
                        ||node.getClass().getClassLoader()!=null)return false;
                String name=node.getClass().getName();var caller=callers.get(1);
                return name.equals("java.util.concurrent.FutureTask$WaitNode")&&caller.getDeclaringClass()==java.util.concurrent.FutureTask.class&&caller.getMethodName().equals("awaitDone")
                        ||name.equals("java.util.concurrent.ForkJoinTask$Aux")&&caller.getDeclaringClass()==java.util.concurrent.ForkJoinTask.class&&caller.getMethodName().equals("awaitDone")
                        ||name.equals("java.util.concurrent.CompletableFuture$Signaller")&&caller.getDeclaringClass()==java.util.concurrent.CompletableFuture.class
                            &&java.util.Set.of("waitingGet","timedGet").contains(caller.getMethodName());
            });
            if(valid)synchronized(TaskBridge.class) { if(wait.node!=null&&wait.node!=node)lostTask(wait.owner,18,wait.task,null,null,wait,null);else wait.node=node; }
        } catch(Throwable unavailable) {
            if(observed!=null)lostTask(observed.owner,18,observed.task,null,null,observed,unavailable);else lost(unavailable);
        }
    }
    static boolean waitBoundary() {
        return WALKER.walk(frames->frames.dropWhile(frame->frame.getDeclaringClass()==TaskBridge.class).findFirst().map(frame->{
            Class<?> type=frame.getDeclaringClass();String method=frame.getMethodName();
            return type==java.util.concurrent.FutureTask.class&&method.equals("awaitDone")
                    ||type==java.util.concurrent.ForkJoinTask.class&&method.equals("awaitDone")
                    ||type==java.util.concurrent.CompletableFuture.class&&(method.equals("waitingGet")||method.equals("timedGet"));
        }).orElse(false));
    }
    public static synchronized Object requestOwnedQueueRelease(Object expected,Object queue,Object task,java.util.function.Predicate<Runnable> gate) {
        Registration current=registration;Admission admission=ADMISSIONS.get(task);
        if(current==null||current.sink!=expected||AUTHORITY.get()!=current||queue==null||gate==null||admission==null
                ||admission.owner!=current||!admission.fenced||queueReleaseCount>=ADMISSION_LIMIT)return null;
        for(QueueRelease prior:QUEUE_RELEASES.getOrDefault(queue,new java.util.ArrayList<>()))
            if(prior.owner==current&&prior.task==task&&!prior.retired)return prior;
        var request=new QueueRelease(current,queue,task,gate);
        QUEUE_RELEASES.computeIfAbsent(queue,key->new java.util.ArrayList<>()).add(request);
        queuesWithRelease=QUEUE_RELEASES.keySet().toArray();queueReleaseCount++;return request;
    }
    public static boolean cancelOwnedQueueRelease(Object expected,Object token) {
        QueueRelease request;
        synchronized(TaskBridge.class) {
            if(!(token instanceof QueueRelease value)||registration==null||registration.sink!=expected||value.owner!=registration||value.retired)return false;
            request=value;request.cancelled=true;
            if(request.working)return false;
            retireQueueRequest(request);
        }
        emit(request.owner,16,request.task,request.queue,null,request);return true;
    }
    public static void wakeOwnedQueue(Object expected,Object token) {
        try {
            synchronized(TaskBridge.class) {
                if(!(token instanceof QueueRelease request)||registration==null||registration.sink!=expected||request.owner!=registration
                        ||request.retired||request.working||System.nanoTime()<request.retryAfter)return;
                if(request.queue.getClass()==ForkQueueLayout.TYPE)java.util.concurrent.locks.LockSupport.unpark((Thread)ForkQueueLayout.OWNER.get(request.queue));
            }
        } catch(ReflectiveOperationException unavailable) {
            if(token instanceof QueueRelease request)lostTask(request.owner,16,request.task,request.queue,null,request,unavailable);else lost(unavailable);
        }
    }
    static void retireQueueRequest(QueueRelease request) {
        request.retired=true;
        var pending=QUEUE_RELEASES.get(request.queue);
        if(pending!=null&&pending.remove(request)) {
            queueReleaseCount--;if(pending.isEmpty())QUEUE_RELEASES.remove(request.queue);
            queuesWithRelease=QUEUE_RELEASES.keySet().toArray();
        }
    }
    /** Called at verified queue-owner safe points, never on the server in place of the owner. */
    public static void serviceOwnedQueue(Object queue,Thread owner) {
        java.util.List<QueueRelease> observed=null;
        try {
            boolean relevant=false;for(Object active:queuesWithRelease)if(active==queue){relevant=true;break;}
            if(!relevant)return;
            if(queue==null||owner!=Thread.currentThread()||!queueBoundary())return;
            java.util.List<QueueRelease> pending;
            synchronized(TaskBridge.class) {
                var entries=QUEUE_RELEASES.get(queue);if(entries==null)return;
                pending=new java.util.ArrayList<>(entries.subList(0,Math.min(8,entries.size())));
                observed=pending;
                // Rotate visited work so a busy or revoked first entry cannot starve later requests.
                for(QueueRelease item:pending) { entries.remove(item);entries.add(item); }
            }
            if(queue.getClass()!=ForkQueueLayout.TYPE)return;
            var remove=ForkQueueLayout.REMOVE;var top=ForkQueueLayout.TOP;var base=ForkQueueLayout.BASE;var array=ForkQueueLayout.ARRAY;var actualOwner=ForkQueueLayout.OWNER;
            for(QueueRelease request:pending) {
                synchronized(TaskBridge.class) {
                    if(request.retired||request.working||request.owner!=registration||System.nanoTime()<request.retryAfter)continue;
                    request.working=true;request.retryAfter=System.nanoTime()+50_000_000L;
                }
                boolean[] ran={false},removed={false};
                try {
                    request.gate.test(()->{
                        try {
                            if(request.cancelled||Thread.currentThread()!=owner||actualOwner.get(queue)!=owner)return;
                            Object image=array.get(queue);if(image!=null&&!(image instanceof Object[]))return;
                            int capacity=image instanceof Object[] entries?entries.length:0;
                            int size=top.getInt(queue)-base.getInt(queue);if(size<0||size>capacity||array.get(queue)!=image)return;
                            removed[0]=Boolean.TRUE.equals(remove.invoke(queue,request.task,true));ran[0]=true;
                        } catch(ReflectiveOperationException failure) { throw new IllegalStateException(failure); }
                    });
                } finally {
                    boolean retired;
                    synchronized(TaskBridge.class) {
                        request.working=false;retired=ran[0]||request.cancelled;
                        if(!retired) {
                            request.retryAfter=System.nanoTime()+(50_000_000L<<request.stalled);
                            request.stalled=Math.min(6,request.stalled+1);
                        }
                        if(retired&&!request.retired)retireQueueRequest(request);
                    }
                    if(ran[0])emit(request.owner,removed[0]?15:17,request.task,queue,null,request);
                    if(retired)emit(request.owner,16,request.task,queue,null,request);
                }
            }
        } catch(Throwable unavailable) {
            if(observed==null||observed.isEmpty())lost(unavailable);
            else for(QueueRelease request:observed)lostTask(request.owner,17,request.task,queue,null,request,unavailable);
        }
    }
    static boolean queueBoundary() {
        return WALKER.walk(frames->frames.dropWhile(frame->frame.getDeclaringClass()==TaskBridge.class).findFirst()
                .map(frame->frame.getDeclaringClass().getClassLoader()==null&&(
                        frame.getDeclaringClass().getName().equals("java.util.concurrent.ForkJoinPool$WorkQueue")
                            &&java.util.Set.of("nextLocalTask","push","topLevelExec").contains(frame.getMethodName())
                        ||frame.getDeclaringClass()==java.util.concurrent.ForkJoinPool.class&&frame.getMethodName().equals("awaitWork"))).orElse(false));
    }
    /** Only the installed observer's current certified action can install an exact-object admission fence. */
    public static synchronized boolean fenceTask(Object expected,Object task) {
        Registration current=registration;
        if(current==null||current.sink!=expected||AUTHORITY.get()!=current||!(task instanceof java.util.concurrent.ForkJoinTask<?>))return false;
        Admission admission=ADMISSIONS.get(task);
        if(admission!=null&&admission.fenced&&admission.owner!=current)return false;
        if(admission==null) {
            if(ADMISSIONS.size()>=ADMISSION_LIMIT)return false;
            admission=new Admission(current);ADMISSIONS.put(task,admission);
        }
        admission.owner=current;admission.fenced=true;return true;
    }
    public static synchronized int activeInvocations(Object task) {
        Admission admission=ADMISSIONS.get(task);
        return (admission==null?0:admission.active)+SUBMISSIONS.getOrDefault(task,0);
    }
    public static boolean submissionClosed(Object token,Object task) {
        return token instanceof Submission value&&value.identity.get()==task&&value.tracked&&value.ended;
    }
    public static synchronized long[] admissionState(Object expected,Object task) {
        Registration current=registration;Admission admission=ADMISSIONS.get(task);
        if(current==null||current.sink!=expected||admission==null||admission.owner!=current)return new long[]{0,-1,0};
        return new long[]{admission.fenced?1:0,admission.active,admission.denied};
    }
    /** Own registry release, after Core independently proves the callback cannot be restored by its supported backend. */
    public static synchronized boolean releaseFence(Object expected,Object task) {
        Registration current=registration;Admission admission=ADMISSIONS.get(task);
        if(current==null||current.sink!=expected||AUTHORITY.get()!=current||admission==null||admission.owner!=current
                ||!admission.fenced||admission.active!=0)return false;
        ADMISSIONS.remove(task);return true;
    }
    public static boolean blocked(Object token) { return token==BLOCKED; }
    /** Identity, not the consumer class or a wrapping counter, separates observer lifetimes. */
    static final class Registration {
        final Consumer<Object[]> sink;
        final long epoch;
        final Map<Long,Object[]> captureLosses=new java.util.LinkedHashMap<>();
        int delivering;
        Registration(Consumer<Object[]> sink){this.sink=sink;this.epoch=++registrationEpoch;}
    }
    public static String coverage(){return coverage;}
    public static void coverage(String value){coverage=Objects.requireNonNull(value);}
    public static synchronized void install(Consumer<Object[]> value) {
        Objects.requireNonNull(value);
        Class<?> caller=installingCaller();if(observerInstaller!=null&&caller!=observerInstaller)throw new SecurityException("TASK_INSTALLER_IDENTITY_CHANGED");
        Registration current=registration;
        if(current!=null) {
            if(current.sink!=value)throw new IllegalStateException("TASK_OBSERVER_ALREADY_INSTALLED");
            return;
        }
        if(registrationEpoch==Long.MAX_VALUE)throw new IllegalStateException("TASK_REGISTRATION_EPOCH_EXHAUSTED");
        registration=new Registration(value);observerInstaller=caller;
    }
    /** Only the installed owner may detach. Detachment never fabricates an execution exit. */
    public static synchronized boolean uninstall(Object expected) {
        Registration current=registration;
        if(installingCaller()!=observerInstaller)return false;
        if(current==null||current.sink!=expected||current.delivering!=0||!current.captureLosses.isEmpty())return false;
        for(Admission admission:ADMISSIONS.values())if(admission.owner==current&&admission.fenced)return false;
        for(var requests:QUEUE_RELEASES.values())for(QueueRelease request:requests)if(request.owner==current)return false;
        registration=null;return true;
    }
    public static synchronized int pendingDeliveries() { return registration==null?0:registration.delivering; }
    public static boolean observerMatches(Object expected) {
        Registration current=registration;return current!=null&&current.sink==expected;
    }
    public static boolean deliveringTo(Object expected) {
        Registration current=registration;
        return current!=null&&current.sink==expected&&DELIVERING.get()==current;
    }
    /** Loss rows retain their real event identity until Core routes them to its published owners. */
    public static synchronized Object[] captureState(Object expected) {
        Registration current=registration;
        if(current==null||current.sink!=expected)return null;
        Object[][] losses=new Object[current.captureLosses.size()][];int index=0;
        for(Object[] row:current.captureLosses.values())losses[index++]=row.clone();
        return new Object[]{current,current.epoch,UNKNOWN_LOSS.get(),DROPPED.get(),losses};
    }
    public static synchronized boolean acknowledgeCaptureLoss(Object expected,Object identity,long epoch,long sequence) {
        Registration current=registration;
        if(current==null||current.sink!=expected||current!=identity||current.epoch!=epoch
                ||installingCaller()!=observerInstaller)return false;
        return current.captureLosses.remove(sequence)!=null;
    }
    /** Ordinary cancellation is unchanged. Cancellation and actual exit are distinct events. */
    public static boolean cancelState(java.lang.invoke.VarHandle handle,Object task,int expected,int update) {
        boolean changed=handle.compareAndSet(task,expected,update);
        if(changed){if(equipmentCancellation(task,expected,update))cancelEquipmentTask(task);emit(registration,CANCELLED,task,null,currentTask(),null);}
        return changed;
    }
    public static void body(Object task) {
        Registration current=registration;
        try {
            if(task!=null&&boundary(false))emit(current,BODY,task,null,currentTask(),currentExecution());
        } catch(Throwable unavailable){lostTask(current,BODY,task,null,null,null,unavailable);}
    }
    public static void created(Object task) {
        Registration current=registration;
        try {
            if(task!=null&&boundary(false)){
                taskRelation(task,null,currentTask());
                inheritEquipmentCompletion(task);
                emit(current,11,task,null,currentTask(),null);
            }
        } catch(Throwable unavailable){lostTask(current,11,task,null,null,null,unavailable);}
    }
    public static void completionMode(Object task,int mode) {
        Registration current=registration;
        try {
            if(current!=null&&task!=null&&boundary(false))emit(current,13,task,Integer.valueOf(mode),currentTask(),null);
        } catch(Throwable unavailable){lostTask(current,13,task,Integer.valueOf(mode),null,null,unavailable);}
    }
    public static void delegated(Object wrapper,Object delegate) {
        Registration current=registration;
        try {
            if(wrapper!=null&&delegate!=null&&boundary(false)){taskRelation(delegate,null,wrapper);inheritEquipmentDelegate(wrapper,delegate);}
            if(wrapper!=null&&delegate!=null&&boundary(false))emit(current,21,delegate,wrapper,wrapper,null);
        } catch(Throwable unavailable) {lostTask(current,21,delegate,wrapper,wrapper,null,unavailable);}
    }
    /** The production source observer binds a real save/load to the actual nested execution frames. */
    public static void sourceOrigin(Object expected,Object owner,Object subject) {
        Registration current=registration;if(current==null||current.sink!=expected)return;
        if(!WALKER.walk(frames->frames.dropWhile(frame->frame.getDeclaringClass()==TaskBridge.class).findFirst()
                .map(frame->frame.getDeclaringClass().getNestHost()==current.sink.getClass().getNestHost()).orElse(false)))return;
        // Nested platform frames can share a task identity (ScheduledFutureTask.run -> runAndReset).
        // Keep every invocation token; deduplicating by task would lose the outer exit obligation.
        for(Frame frame=CURRENT.get();frame!=null;frame=frame.previous) {
            if(frame.registration!=null&&frame.registration!=current)continue;
            sourceEvent(current,24,frame.task,subject,logicalParent(frame),new Object[]{owner,frame,frame.scheduler});
            if(frame.delegateOwner!=null)sourceEvent(current,21,frame.task,frame.delegateOwner,frame.delegateOwner,null);
        }
    }
    static void sourceEvent(Registration current,int kind,Object task,Object scheduler,Object parent,Object execution) {
        if(DELIVERING.get()!=current) { emit(current,kind,task,scheduler,parent,execution);return; }
        // Source discovery may happen inside the clone event consumer. Keep the real frame
        // evidence until that delivery returns; ordinary observer-created tasks stay suppressed.
        var queue=SOURCE_DELIVERIES.get();
        if(queue==null) { queue=new java.util.ArrayDeque<>();SOURCE_DELIVERIES.set(queue); }
        Object[] row=captureRow(current,kind,task,scheduler,parent,execution);
        if(queue.size()>=MAX_DEPTH*2) { lost(current,row,null);return; }
        queue.addLast(row);
    }
    public static synchronized long[] invocationState(Object token) {
        if(!(token instanceof Frame frame))return new long[]{-1,0,0,0};
        return new long[]{frame.dispatch?1:0,frame.exited?1:0,frame.retired?1:0,
                frame.emitted&&frame.registration!=null&&frame.registration==registration?1:0};
    }

    /** One actual platform stage invocation, including its synchronous fast path. No synthetic Future is executed. */
    public static Object beginInline(Object source,Object other,Object callback) {
        Inline task=null;Registration observer=registration;
        try {
            if(observer==null||DELIVERING.get()!=null||!inlineBoundary())return null;
            task=new Inline(source,other,callback);
            captureEquipmentAsync(task,null,currentTask(),callback);
            task.frame=enterFrame(task,false);
            return task;
        } catch(Throwable unavailable){lostTask(observer,ENTER,task,null,null,null,unavailable);return null;}
    }
    public static void endInline(Object token,Object output) {
        try {
            if(!(token instanceof Inline task)||task.thread!=Thread.currentThread()||task.closed||!inlineBoundary())return;
            task.output=output;
            try { exitFrame(task.frame); }
            finally {
                removeEquipmentTask(task);
                task.source=null;task.other=null;task.callback=null;task.output=null;task.frame=null;task.thread=null;
                task.closed=true;
            }
        } catch(Throwable unavailable){
            if(token instanceof Inline task&&task.frame instanceof Frame frame)lostFrame(frame,unavailable);else lost(unavailable);
        }
    }
    /** Snapshot is queried synchronously by the installed observer during ENTER/EXIT publication. */
    public static Object[] inlineDetails(Object token) {
        if(!(token instanceof Inline task)||DELIVERING.get()==null)return null;
        return new Object[]{task.source,task.other,task.callback,task.output};
    }
    public static boolean inlineClosed(Object token) { return token instanceof Inline task&&task.closed; }
    static final class Inline {
        Thread thread=Thread.currentThread();
        Object source,other,callback,output,frame;
        volatile boolean closed;
        Inline(Object source,Object other,Object callback) { this.source=source;this.other=other;this.callback=callback; }
    }
    static boolean inlineBoundary() {
        return WALKER.walk(frames->frames.dropWhile(frame->frame.getDeclaringClass()==TaskBridge.class).findFirst()
                .map(frame->frame.getDeclaringClass()==java.util.concurrent.CompletableFuture.class
                        &&CF_STAGES.contains(frame.getMethodName())).orElse(false));
    }

    /** Opaque token is returned only at a registered JDK entry and closed in its finally. */
    public static Object enter(Object task) {
        Registration current=registration;
        try{return boundary(false)?enterFrame(task,false):null;}catch(Throwable unavailable){lostTask(current,ENTER,task,null,null,null,unavailable);return null;}
    }
    public static Object definedTaskEnter(Object task,String selector){
        if(task==null||!producerSite()||!DefinedTaskContracts.matches(task,selector))return null;
        Frame current=currentFrame(registration);if(current!=null&&current.task==task&&!current.dispatch)return null;
        taskRelation(task,null,current==null?null:current.task);
        Object token=enterFrame(task,false);
        try{if(token!=BLOCKED)emit(registration,BODY,task,null,currentTask(),currentExecution());return token;}
        catch(RuntimeException|Error failure){exitFrame(token);throw failure;}
    }
    public static void definedTaskExit(Object token){
        if(!producerSite())return;
        try{exitFrame(token);}catch(Throwable unavailable){
            lostFrame(token,unavailable);if(token instanceof Frame frame&&frame.thread==Thread.currentThread()&&CURRENT.get()==frame){releaseAdmission(frame);restore(frame);}
        }
    }
    public static Object enterDelegate(Object wrapper,Object task) {
        Registration current=registration;
        try {
            if(wrapper==null||!boundary(false))return null;
            inheritEquipmentDelegate(wrapper,task);
            Object token=enterFrame(task,false);
            if(token instanceof Frame frame)frame.delegateOwner=wrapper;
            emit(registration,21,task,wrapper,wrapper,null);
            return token;
        } catch(Throwable unavailable) {lostTask(current,21,task,wrapper,wrapper,null,unavailable);return null;}
    }
    public static Object enterDispatch(Object scheduler,Object task) {
        Registration current=registration;
        try {
            if(!boundary(false))return null;
            Object token=enterFrame(task,true);
            if(token instanceof Frame frame)frame.scheduler=scheduler;
            return token;
        }catch(Throwable unavailable){lostTask(current,DISPATCH_ENTER,task,scheduler,null,null,unavailable);return null;}
    }
    static Object enterFrame(Object task,boolean dispatch) {
        Registration current=registration;
        // All four entry callers authenticate their real JDK boundary before reaching here.
        if(task==null||DELIVERING.get()!=null)return null;
        if(modStopped(objectModule(task))||stoppedInvocation())return BLOCKED;
        if(!dispatch&&denied(current,task)) {
            emit(current,14,task,null,currentTask(),null);return BLOCKED;
        }
        Frame parent=currentFrame(current);
        Overflow overflow=OVERFLOW_DEPTH.get();
        if(overflow!=null&&overflow.registration!=current) { OVERFLOW_DEPTH.remove();overflow=null; }
        if(overflow!=null){lostTask(current,OVERFLOW,task,null,parent==null?null:parent.task,null,null);overflow.depth=Math.addExact(overflow.depth,1);return overflow;}
        if(parent!=null&&parent.depth>=MAX_DEPTH) {
            lostTask(current,OVERFLOW,task,null,parent.task,null,null);
            overflow=new Overflow(current);OVERFLOW_DEPTH.set(overflow);
            emit(current,OVERFLOW,task,null,parent.task,null);return overflow;
        }
        // Physical nesting must survive a registration change so the old invocation can exit.
        // Only the matching logical parent is published to the new observer.
        Frame frame=new Frame(task,CURRENT.get(),dispatch,current);
        synchronized(TaskBridge.class) {
            Admission admission=ADMISSIONS.get(task);
            {
                if(admission==null&&ADMISSIONS.size()<ADMISSION_LIMIT) {
                    admission=new Admission(current);
                    boolean nested=OWN_LEDGER_MUTATION.get()!=null;OWN_LEDGER_MUTATION.set(true);
                    try {ADMISSIONS.put(task,admission);}finally {if(!nested)OWN_LEDGER_MUTATION.remove();}
                }
                if(admission==null)lostTask(current,dispatch?DISPATCH_ENTER:ENTER,task,null,parent==null?null:parent.task,frame,null);
                else if(!dispatch&&admission.fenced) { if(admission.denied<Long.MAX_VALUE)admission.denied++;frame.blocked=true; }
                else if(admission.active==Integer.MAX_VALUE)lostTask(current,dispatch?DISPATCH_ENTER:ENTER,task,null,parent==null?null:parent.task,frame,null);
                else { admission.active++;frame.admitted=true; }
            }
        }
        if(frame.blocked) { emit(current,14,task,null,currentTask(),null);return BLOCKED; }
        CURRENT.set(frame);
        Object pool=null;
        if(!dispatch&&task instanceof java.util.concurrent.ForkJoinTask<?>) {
            pool=java.util.concurrent.ForkJoinTask.getPool();
            if(pool==null&&HELPER.get()!=null)pool=HELPER.get().pool;
        }
        if(frame.emitted)emit(current,dispatch?DISPATCH_ENTER:ENTER,task,pool,parent==null?null:parent.task,frame);
        return frame;
    }
    public static void exit(Object token) {
        try{if(boundary(false))exitFrame(token);}catch(Throwable unavailable){lostFrame(token,unavailable);
            if(token instanceof Frame frame&&frame.thread==Thread.currentThread()&&CURRENT.get()==frame) { releaseAdmission(frame);restore(frame); }
        }
    }
    static void exitFrame(Object token) {
        if(token instanceof Overflow overflow) {
            if(overflow.thread!=Thread.currentThread()||OVERFLOW_DEPTH.get()!=overflow)return;
            if(--overflow.depth==0)OVERFLOW_DEPTH.remove();return;
        }
        if(!(token instanceof Frame frame)||frame.thread!=Thread.currentThread()||CURRENT.get()!=frame)return;
        try {
            // An old finally may clean up its old frame, but never sends an EXIT to a new observer.
            if(frame.emitted)emit(frame.registration,frame.dispatch?DISPATCH_EXIT:EXIT,frame.task,null,
                    logicalParent(frame),frame);
        } finally {
            frame.exited=true;if(!frame.dispatch)releaseAdmission(frame);restore(frame);
            if(!frame.dispatch) { frame.task=null;frame.delegateOwner=null;frame.scheduler=null;frame.previous=null;frame.thread=null;frame.sources=NO_SOURCES; }
        }
    }
    static synchronized boolean denied(Registration current,Object task) {
        Admission admission=ADMISSIONS.get(task);
        if(admission==null||admission.owner!=current||!admission.fenced)return false;
        if(admission.denied<Long.MAX_VALUE)admission.denied++;return true;
    }
    static synchronized void releaseAdmission(Frame frame) {
        if(!frame.admitted)return;
        Admission admission=ADMISSIONS.get(frame.task);
        if(admission==null||admission.active<=0) {lostTask(frame.registration,frame.dispatch?DISPATCH_EXIT:EXIT,frame.task,null,logicalParent(frame),frame,null);return;}
        frame.admitted=false;
        if(--admission.active==0&&!admission.fenced) {
            boolean nested=OWN_LEDGER_MUTATION.get()!=null;OWN_LEDGER_MUTATION.set(true);
            try {ADMISSIONS.remove(frame.task);}finally {if(!nested)OWN_LEDGER_MUTATION.remove();}
        }
    }
    /** Called only after the verified worker task-local clear and successful Worker.unlock. */
    public static void retiredDispatch(Object token) {
        try {
            if(!(token instanceof Frame frame)||!frame.dispatch||!frame.exited||frame.retired
                    ||frame.thread!=Thread.currentThread()||!boundary(false))return;
            frame.retired=true;
            releaseAdmission(frame);
            emit(frame.registration,12,frame.task,null,logicalParent(frame),frame);
            frame.task=null;frame.delegateOwner=null;frame.scheduler=null;frame.previous=null;frame.thread=null;frame.sources=NO_SOURCES;
        } catch(Throwable unavailable){lostFrame(token,unavailable);}
    }
    static void restore(Frame frame) {
        if(frame.previous==null)CURRENT.remove();
        else CURRENT.set(frame.previous);
    }
    static Frame currentFrame(Registration current) {
        Frame frame=CURRENT.get();
        return frame!=null&&(frame.registration==current||frame.registration==null)?frame:null;
    }
    static Object logicalParent(Frame frame) {
        Frame parent=frame.previous;
        return parent!=null&&(parent.registration==frame.registration||parent.registration==null)?parent.task:null;
    }
    public static void submitted(Object scheduler,Object task) {
        Registration current=registration;
        try{submit(scheduler,task);}catch(Throwable unavailable){lostTask(current,SUBMIT,task,scheduler,null,null,unavailable);}
    }
    static Object scheduler(Object scheduler,Object task) {
        if(scheduler==task&&task instanceof java.util.concurrent.ForkJoinTask<?>) {
            Object pool=java.util.concurrent.ForkJoinTask.getPool();
            return pool==null?java.util.concurrent.ForkJoinPool.commonPool():pool;
        }
        return scheduler;
    }
    static void submit(Object scheduler,Object task) {
        Registration current=registration;
        if(task==null||DELIVERING.get()!=null||!boundary(true))return;
        Frame frame=currentFrame(current);
        taskRelation(task,scheduler(scheduler,task),frame==null?null:frame.task);
        captureEquipmentSubmission(task,scheduler(scheduler,task),frame==null?null:frame.task);
        emit(current,SUBMIT,task,scheduler(scheduler,task),frame==null?null:frame.task,frame);
    }
    /** Publication spans the entire platform submission, including rejection/CallerRuns. */
    public static Object beginSubmission(Object scheduler,Object task) {
        Submission token=null;Registration current=registration;
        try {
            if(task==null||DELIVERING.get()!=null||!boundary(true))return null;
            Frame frame=currentFrame(current);
            token=new Submission(task,scheduler(scheduler,task),frame==null?null:frame.task,current);
            if(token.pool!=null)synchronized(CREATION_OWNERS){
                if(SCHEDULER_RETIREMENTS.containsKey(new EventKey(token.pool,null)))throw new SchedulerRetired();
                SCHEDULER_SUBMISSIONS.merge(new EventKey(token.pool,CREATION_QUEUE),1,Integer::sum);token.schedulerTracked=true;
            }
            taskRelation(task,token.scheduler,token.parent);
            captureEquipmentSubmission(task,token.scheduler,token.parent);
            synchronized(TaskBridge.class) {
                if(submissionEntries>=ADMISSION_LIMIT)lostTask(current,SUBMIT_ENTER,task,token.scheduler,token.parent,token,null);
                else {
                    boolean nested=OWN_LEDGER_MUTATION.get()!=null;OWN_LEDGER_MUTATION.set(true);
                    try {SUBMISSIONS.merge(task,1,Integer::sum);}finally {if(!nested)OWN_LEDGER_MUTATION.remove();}
                    submissionEntries++;token.tracked=true;
                }
            }
            emit(current,SUBMIT_ENTER,task,token.scheduler,token.parent,token);return token;
        } catch(SchedulerRetired refused){throw refused;}
        catch(Throwable unavailable){
            if(token!=null&&token.pool!=null)synchronized(CREATION_OWNERS){schedulerSources(token.scheduler,List.of(),true);}
            lostTask(current,SUBMIT_ENTER,task,token==null?scheduler:token.scheduler,token==null?null:token.parent,token,unavailable);return token;
        }
    }
    public static void endSubmission(Object value) {
        if(!(value instanceof Submission token)||token.owner!=Thread.currentThread()||token.ended||!boundary(true))return;
        try {
            synchronized(TaskBridge.class) {
                if(token.tracked) {
                    Integer count=SUBMISSIONS.get(token.task);
                    if(count==null||count<=0)lostTask(token.registration,SUBMIT_EXIT,token.task,token.scheduler,token.parent,token,null);
                    else {
                        boolean nested=OWN_LEDGER_MUTATION.get()!=null;OWN_LEDGER_MUTATION.set(true);
                        try {if(count==1)SUBMISSIONS.remove(token.task);else SUBMISSIONS.put(token.task,count-1);}
                        finally {if(!nested)OWN_LEDGER_MUTATION.remove();}
                        submissionEntries--;
                    }
                }
                token.ended=true;
            }
            emit(token.registration,SUBMIT_EXIT,token.task,token.scheduler,token.parent,token);
        } catch(Throwable unavailable){lostTask(token.registration,SUBMIT_EXIT,token.task,token.scheduler,token.parent,token,unavailable);}
        finally{
            token.ended=true;
            try{
                if(token.schedulerTracked)synchronized(CREATION_OWNERS){
                    EventKey key=new EventKey(token.pool,null);Integer count=SCHEDULER_SUBMISSIONS.get(key);
                    if(count==null||count<=0)lostTask(token.registration,SUBMIT_EXIT,token.task,token.scheduler,token.parent,token,null);else if(count==1)SCHEDULER_SUBMISSIONS.remove(key);else SCHEDULER_SUBMISSIONS.put(key,count-1);
                    token.schedulerTracked=false;
                }
            }finally{token.task=null;token.scheduler=null;token.parent=null;token.owner=null;token.pool=null;}
        }
    }
    static final class Submission {
        Object task,scheduler,parent,pool;
        Thread owner=Thread.currentThread();
        final java.lang.ref.WeakReference<Object> identity;
        final Registration registration;
        boolean tracked,schedulerTracked;
        volatile boolean ended;
        Submission(Object task,Object scheduler,Object parent,Registration registration) {
            this.task=task;this.scheduler=scheduler;this.parent=parent;this.registration=registration;
            this.pool=schedulerDelegate(scheduler);
            this.identity=new java.lang.ref.WeakReference<>(task);
        }
    }
    static final class Overflow {
        final Registration registration;
        final Thread thread=Thread.currentThread();
        int depth=1;
        Overflow(Registration registration){this.registration=registration;}
    }
    public static Object currentTask(){Frame frame=currentFrame(registration);return overflowing()||DELIVERING.get()!=null||frame==null?null:frame.task;}
    public static Object currentExecution(){return overflowing()||DELIVERING.get()!=null?null:currentFrame(registration);}
    static boolean overflowing() {
        Overflow overflow=OVERFLOW_DEPTH.get();
        if(overflow!=null&&overflow.registration!=registration){OVERFLOW_DEPTH.remove();return false;}
        return overflow!=null;
    }
    public static long droppedEvents(){return DROPPED.get();}
    static volatile String firstLossSite="";
    public static String firstCaptureLoss(){return firstLossSite;}
    static void lost(){lost(null);}
    static void lost(Throwable failure){
        incrementLoss(UNKNOWN_LOSS);diagnosticLoss(failure);
    }
    static long incrementLoss(java.util.concurrent.atomic.AtomicLong counter) {
        for(;;) {long prior=counter.get();if(prior==Long.MAX_VALUE)return prior;if(counter.compareAndSet(prior,prior+1))return prior+1;}
    }
    static long diagnosticLoss(Throwable failure){
        for(;;) {
            long n=DROPPED.get();if(n==Long.MAX_VALUE||!DROPPED.compareAndSet(n,n+1)) {if(n==Long.MAX_VALUE)return n;continue;}
            if(n==0) {
                try {
                    String site=WALKER.walk(frames->frames.skip(2).findFirst().map(frame->
                            frame.getClassName()+"#"+frame.getMethodName()+":"+frame.getLineNumber()).orElse("UNKNOWN"));
                    firstLossSite=site+(failure==null?"":":"+failure.getClass().getName()+":"+String.valueOf(failure.getMessage()));
                    System.err.println("RONOVA_TASK_CAPTURE_LOSS_AT:"+firstLossSite);
                } catch(Throwable diagnosticFailure) {firstLossSite="DIAGNOSTIC_UNAVAILABLE";}
            }
            return n+1;
        }
    }
    static void lost(Registration expected,Object[] row,Throwable failure) {
        long sequence=diagnosticLoss(failure);
        synchronized(TaskBridge.class) {
            if(expected==null||row==null||row.length!=11||row[1]==null
                    ||row[8]!=expected||!(row[9] instanceof Long epoch)||epoch!=expected.epoch
                    ||sequence==Long.MAX_VALUE||expected.captureLosses.size()>=ADMISSION_LIMIT) {
                incrementLoss(UNKNOWN_LOSS);return;
            }
            Object[] lost=java.util.Arrays.copyOf(row,12);lost[11]=sequence;
            expected.captureLosses.put(sequence,lost);
        }
    }
    static void lostTask(Registration expected,int kind,Object task,Object scheduler,Object parent,Object execution,Throwable failure) {
        try {lost(expected,captureRow(expected,kind,task,scheduler,parent,execution),failure);}
        catch(Throwable unavailable) {lost(failure);}
    }
    static void lostFrame(Object token,Throwable failure) {
        if(token instanceof Frame frame)lostTask(frame.registration,frame.dispatch?DISPATCH_EXIT:EXIT,frame.task,frame.scheduler,logicalParent(frame),frame,failure);
        else lost(failure);
    }
    static Object[] captureRow(Registration expected,int kind,Object task,Object scheduler,Object parent,Object execution) {
        return new Object[]{kind,task,scheduler,parent,execution,Thread.currentThread(),System.nanoTime(),DROPPED.get(),
                expected,expected==null?-1L:expected.epoch,UNKNOWN_LOSS.get()};
    }
    static void emit(Registration expected,int kind,Object task,Object scheduler,Object parent,Object execution) {
        synchronized(TaskBridge.class) {
            if(expected==null||registration!=expected||DELIVERING.get()!=null)return;
            expected.delivering++;
        }
        DELIVERING.set(expected);
        Object[] row=null;
        try {row=captureRow(expected,kind,task,scheduler,parent,execution);expected.sink.accept(row);}
        catch(Throwable unavailable){lost(expected,row,unavailable);}
        finally {
            try {
                for(int budget=0;budget<MAX_DEPTH*2;budget++) {
                    var queue=SOURCE_DELIVERIES.get();if(queue==null||queue.isEmpty())break;
                    if(registration!=expected)break;
                    Object[] source=queue.removeFirst();
                    try { expected.sink.accept(source); }catch(Throwable failure) { lost(expected,source,failure); }
                }
                var queue=SOURCE_DELIVERIES.get();if(queue!=null)while(!queue.isEmpty())lost(expected,queue.removeFirst(),null);
            } finally {
                SOURCE_DELIVERIES.remove();DELIVERING.remove();
                synchronized(TaskBridge.class) { expected.delivering--; }
            }
        }
    }
    static boolean boundary(boolean submission) {
        return WALKER.walk(frames->frames.dropWhile(frame->frame.getDeclaringClass()==TaskBridge.class).findFirst().map(frame->{
            Class<?> owner=frame.getDeclaringClass();
            if(owner.getClassLoader()!=null||!"java.base".equals(owner.getModule().getName()))return false;
            String name=owner.getName(),method=frame.getMethodName();
            if(submission)return name.equals("java.util.concurrent.ThreadPoolExecutor")&&method.equals("execute")
                ||name.equals("java.util.concurrent.ScheduledThreadPoolExecutor")&&(method.equals("delayedExecute")||method.equals("reExecutePeriodic"))
                ||name.equals("java.util.concurrent.ForkJoinPool")&&method.equals("externalSubmit")
                ||name.equals("java.util.concurrent.ForkJoinTask")&&method.equals("fork");
            return name.equals("java.util.concurrent.CompletableFuture")&&CF_STAGES.contains(method)
                ||name.startsWith("java.util.concurrent.CompletableFuture$")
                    &&(method.equals("<init>")||method.equals("tryFire")||method.equals("run"))
                ||name.equals("java.util.concurrent.FutureTask")&&(method.equals("run")||method.equals("runAndReset"))
                ||name.equals("java.util.concurrent.ThreadPoolExecutor")&&method.equals("runWorker")
                ||name.equals("java.util.concurrent.ThreadPoolExecutor$CallerRunsPolicy")&&method.equals("rejectedExecution")
                ||name.equals("java.util.concurrent.ScheduledThreadPoolExecutor$ScheduledFutureTask")&&method.equals("run")
                ||name.equals("java.util.concurrent.Executors$RunnableAdapter")&&method.equals("call")
                ||java.util.Set.of("java.util.concurrent.ForkJoinTask$AdaptedRunnable","java.util.concurrent.ForkJoinTask$AdaptedRunnableAction",
                        "java.util.concurrent.ForkJoinTask$RunnableExecuteAction","java.util.concurrent.ForkJoinTask$AdaptedCallable").contains(name)&&method.equals("exec")
                ||name.equals("java.util.concurrent.ForkJoinTask")&&method.equals("doExec");
        }).orElse(false));
    }
    static final class Frame {
        Object task;
        Frame previous;
        Module[] sources;final boolean sourcesUnknown;
        final Registration registration;
        Thread thread=Thread.currentThread();
        final int depth;
        final boolean dispatch,emitted;
        volatile boolean exited,retired;
        boolean admitted,blocked;
        Object delegateOwner,scheduler;
        Frame(Object task,Frame previous,boolean dispatch,Registration registration) {
            this.task=task;this.previous=previous;this.dispatch=dispatch;this.registration=registration;
            Module[] origins=objectSources(task);Set<Module> selected=addModuleSources(null,origins,true);
            sources=selected==null?NO_SOURCES:selected.toArray(new Module[0]);
            sourcesUnknown=objectSourceUnknown(task)||sourceUnknown()||selected!=null&&selected.contains(null);
            this.emitted=dispatch||previous==null||previous.task!=task||previous.dispatch;
            depth=previous==null?1:previous.depth+1;
        }
    }
}
