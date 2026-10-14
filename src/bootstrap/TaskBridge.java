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
    static volatile Registration registration;
    static volatile String coverage="AGENT_NOT_READY";
    static final java.util.Set<String> certified=new java.util.HashSet<>();
    static long certificationEpoch;
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
        try {
            var lookup=java.lang.invoke.MethodHandles.privateLookupIn(base,java.lang.invoke.MethodHandles.lookup());
            return lookup.findSpecial(base,name,java.lang.invoke.MethodType.methodType(result),base).invoke(pool);
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
            var lookup=java.lang.invoke.MethodHandles.privateLookupIn(Thread.class,java.lang.invoke.MethodHandles.lookup());
            lookup.findSpecial(Thread.class,"interrupt",java.lang.invoke.MethodType.methodType(void.class),Thread.class).invoke(thread);
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
        Class<?> implementation=thread.getClass().getMethod("run").getDeclaringClass();Module declaration=DefinitionBridge.module(implementation);
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
    public static java.util.List<Runnable> retireTimer(Object value,Module module)throws ReflectiveOperationException {
        return retireTimer(value,new Module[]{module});
    }
    public static java.util.List<Runnable> retireTimer(Object value,Module[] modules)throws ReflectiveOperationException {
        if(!coreWriter()||value==null||value.getClass()!=java.util.Timer.class)throw new SecurityException("GROUP_TIMER_CORE_REQUIRED");
        Set<Module> selected=selectedSchedulerModules(modules);if(selected.isEmpty())throw new SecurityException("GROUP_TIMER_SOURCES_REQUIRED");
        for(Module source:selected)if(!modStopped(source))throw new SecurityException("STOPPED_TIMER_SOURCES_REQUIRED");
        var queueField=java.util.Timer.class.getDeclaredField("queue");queueField.setAccessible(true);Object queue=queueField.get(value);
        Class<?> type=queue.getClass();java.lang.reflect.Field tableField=type.getDeclaredField("queue"),sizeField=type.getDeclaredField("size");tableField.setAccessible(true);sizeField.setAccessible(true);
        java.lang.reflect.Method remove=type.getDeclaredMethod("quickRemove",int.class),heap=type.getDeclaredMethod("heapify");remove.setAccessible(true);heap.setAccessible(true);
        java.lang.reflect.Field stateField=java.util.TimerTask.class.getDeclaredField("state"),lockField=java.util.TimerTask.class.getDeclaredField("lock");stateField.setAccessible(true);lockField.setAccessible(true);
        java.util.List<Runnable> removed=new java.util.ArrayList<>();java.util.Set<Object> seen=java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
        synchronized(queue){Object[] table=(Object[])tableField.get(queue);
            for(int i=sizeField.getInt(queue);i>=1;i--){Object task=table[i];if(!selectedTimerTask(task,selected))continue;
                synchronized(lockField.get(task)){stateField.setInt(task,3);remove.invoke(queue,i);removed.add((Runnable)task);seen.add(task);}
            }
            if(!removed.isEmpty())heap.invoke(queue);
        }
        java.util.List<Object> published=new java.util.ArrayList<>();synchronized(CREATION_OWNERS){
            for(var entry:TASK_SCHEDULERS.entrySet())if(entry.getValue().get()==value){Object task=entry.getKey().get();if(task!=null&&task instanceof java.util.TimerTask)published.add(task);}
        }
        for(Object task:published)if(!seen.contains(task)&&selectedTimerTask(task,selected))synchronized(lockField.get(task)){stateField.setInt(task,3);removed.add((Runnable)task);}
        synchronized(queue){
            // Successful publication holds this same queue monitor. Decide and cancel
            // without allowing a new outside submission between the two operations.
            boolean exclusive;synchronized(CREATION_OWNERS){
                exclusive=schedulerDisposition(List.of(value),selected).equals("PRIVATE");
                if(sizeField.getInt(queue)!=0){exclusive=false;SHARED_SCHEDULERS.add(new EventKey(value,CREATION_QUEUE));}
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
        var field=java.util.Timer.class.getDeclaredField("thread");field.setAccessible(true);return !((Thread)field.get(value)).isAlive();
    }
    public static boolean timerResourceEnded(Object value,Module[] modules)throws ReflectiveOperationException {
        if(!coreWriter()||value==null||value.getClass()!=java.util.Timer.class)throw new SecurityException("GROUP_TIMER_CORE_REQUIRED");
        String disposition;synchronized(CREATION_OWNERS){disposition=schedulerDisposition(List.of(value),selectedSchedulerModules(modules));}
        if(disposition.equals("SHARED"))return true;
        if(!disposition.equals("PRIVATE"))throw new IllegalStateException("TIMER_"+disposition);
        var field=java.util.Timer.class.getDeclaredField("thread");field.setAccessible(true);return !((Thread)field.get(value)).isAlive();
    }
    public static boolean timerTaskRetired(Object task)throws ReflectiveOperationException {
        if(!coreWriter()||!(task instanceof java.util.TimerTask))throw new SecurityException("GROUP_TIMER_CORE_REQUIRED");
        synchronized(CREATION_OWNERS){if(TIMER_RUNNING.containsKey(new EventKey(task,null)))return false;}
        java.lang.reflect.Field stateField=java.util.TimerTask.class.getDeclaredField("state"),lockField=java.util.TimerTask.class.getDeclaredField("lock");stateField.setAccessible(true);lockField.setAccessible(true);
        synchronized(lockField.get(task)){return stateField.getInt(task)==3;}
    }
    static Object schedulerDelegate(Object value) {
        List<Object> chain=schedulerChain(value);return chain.isEmpty()?null:chain.get(chain.size()-1);
    }
    static final class SchedulerDelegates {
        static final Map<Class<?>,java.lang.reflect.Field> FIELDS=fields();
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
    static List<Object> schedulerChain(Object value){
        List<Object> chain=new ArrayList<>();Set<Object> seen=Collections.newSetFromMap(new IdentityHashMap<>());Object current=value;
        while(current!=null&&seen.add(current)){
            chain.add(current);if(current instanceof java.util.concurrent.ThreadPoolExecutor||current instanceof java.util.concurrent.ForkJoinPool)return chain;
            java.lang.reflect.Field delegate=SchedulerDelegates.FIELDS.get(current.getClass());if(delegate==null||!delegate.trySetAccessible())return List.of();
            try{current=delegate.get(current);}catch(IllegalAccessException unavailable){return List.of();}
        }
        return List.of();
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
        if(TaskBridge.class.getClassLoader()==null&&!WALKER.walk(frames->frames
                .dropWhile(frame->frame.getDeclaringClass()==TaskBridge.class).findFirst()
                .map(frame->frame.getDeclaringClass().getName().equals("dev.ronova.pro.agent.RecoveryAgent")
                        &&frame.getDeclaringClass().getClassLoader()==ClassLoader.getSystemClassLoader()).orElse(false)))
            throw new SecurityException("TASK_BOUNDARY_AGENT_CALLER_REQUIRED");
        boolean changed=ready?certified.add(name):certified.remove(name);
        if(changed)certificationEpoch=Math.addExact(certificationEpoch,1);
    }
    public static synchronized long[] boundarySnapshot(String[] required) {
        return new long[]{certificationEpoch,required.length>0&&certified.containsAll(java.util.List.of(required))?1:0};
    }
    public static synchronized boolean withBoundaries(String[] required,long expected,Runnable action) {
        Registration current=registration;
        if(current==null||!WALKER.walk(frames->frames.dropWhile(frame->frame.getDeclaringClass()==TaskBridge.class).findFirst()
                .map(frame->frame.getDeclaringClass().getNestHost()==current.sink.getClass().getNestHost()).orElse(false)))return false;
        if(required.length==0||!certified.containsAll(java.util.List.of(required)))return false;
        Registration prior=AUTHORITY.get();AUTHORITY.set(registration);
        try { action.run();return true; }
        finally { if(prior==null)AUTHORITY.remove();else AUTHORITY.set(prior); }
    }
    private TaskBridge() {}
    public static int abiVersion(){return 63;}
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
        Publication token=null;
        try {
            var callback=publicationGate;
            token=new Publication(callback==null?null:(AutoCloseable)callback.invokeExact(result,currentTask()));
            if(!resultPublicationAllowed(result)){token.close();return Boolean.FALSE;}
            return token;
        } catch(Throwable failure) {
            if(token!=null)token.close();lost();return Boolean.FALSE;
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
        try {
            if(task==null||!waitBoundary())return null;
            Wait wait=new Wait(task,registration);
            synchronized(TaskBridge.class) {
                if(waiterEntries>=ADMISSION_LIMIT)lost();
                else { WAITERS.computeIfAbsent(task,key->new java.util.ArrayList<>()).add(wait);waiterEntries++;wait.tracked=true; }
            }
            CURRENT_WAIT.set(wait);
            emit(wait.owner,18,task,null,currentTask(),wait);return wait;
        } catch(Throwable unavailable) { lost();return null; }
    }
    public static void endWait(Object token) {
        try {
            if(!(token instanceof Wait wait)||wait.thread!=Thread.currentThread()||wait.closed||CURRENT_WAIT.get()!=wait||!waitBoundary())return;
            synchronized(TaskBridge.class) {
                if(wait.tracked) {
                    var entries=WAITERS.get(wait.task);
                    if(entries==null||!entries.remove(wait))lost();else { waiterEntries--;if(entries.isEmpty())WAITERS.remove(wait.task); }
                }
                wait.closed=true;
                if(wait.node!=null) {
                    if(EXITED_WAIT_NODES.size()>=65536)lost();
                    else EXITED_WAIT_NODES.put(wait.node,new java.lang.ref.WeakReference<>(wait.task));
                }
            }
            try { emit(registration,wait.owner==registration?19:20,wait.task,null,currentTask(),wait); }
            finally {
                wait.thread=null;wait.node=null;wait.task=null;
                if(wait.previous==null)CURRENT_WAIT.remove();else CURRENT_WAIT.set(wait.previous);wait.previous=null;
            }
        } catch(Throwable unavailable) { lost(); }
    }
    public static synchronized int waiterCount(Object task) { var entries=WAITERS.get(task);return entries==null?0:entries.size(); }
    public static synchronized boolean waiterNodeObserved(Object task,Object node) {
        var entries=WAITERS.get(task);if(entries!=null)for(Wait wait:entries)if(wait.node==node&&!wait.closed)return true;
        var exited=EXITED_WAIT_NODES.get(node);return exited!=null&&exited.get()==task;
    }
    public static void waitNode(Object node) {
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
            if(valid)synchronized(TaskBridge.class) { if(wait.node!=null&&wait.node!=node)lost();else wait.node=node; }
        } catch(Throwable unavailable) { lost(); }
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
                var field=request.queue.getClass().getDeclaredField("owner");
                if(field.trySetAccessible())java.util.concurrent.locks.LockSupport.unpark((Thread)field.get(request.queue));
            }
        } catch(ReflectiveOperationException unavailable) { lost(); }
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
        try {
            boolean relevant=false;for(Object active:queuesWithRelease)if(active==queue){relevant=true;break;}
            if(!relevant)return;
            if(queue==null||owner!=Thread.currentThread()||!queueBoundary())return;
            java.util.List<QueueRelease> pending;
            synchronized(TaskBridge.class) {
                var entries=QUEUE_RELEASES.get(queue);if(entries==null)return;
                pending=new java.util.ArrayList<>(entries.subList(0,Math.min(8,entries.size())));
                // Rotate visited work so a busy or revoked first entry cannot starve later requests.
                for(QueueRelease item:pending) { entries.remove(item);entries.add(item); }
            }
            var type=queue.getClass();var remove=type.getDeclaredMethod("tryRemove",java.util.concurrent.ForkJoinTask.class,boolean.class);
            var top=type.getDeclaredField("top");var base=type.getDeclaredField("base");var array=type.getDeclaredField("array");var actualOwner=type.getDeclaredField("owner");
            if(!remove.trySetAccessible()||!top.trySetAccessible()||!base.trySetAccessible()||!array.trySetAccessible()||!actualOwner.trySetAccessible())return;
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
        } catch(Throwable unavailable) { lost(); }
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
        int delivering;
        Registration(Consumer<Object[]> sink){this.sink=sink;}
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
        registration=new Registration(value);observerInstaller=caller;
    }
    /** Only the installed owner may detach. Detachment never fabricates an execution exit. */
    public static synchronized boolean uninstall(Object expected) {
        Registration current=registration;
        if(installingCaller()!=observerInstaller)return false;
        if(current==null||current.sink!=expected||current.delivering!=0)return false;
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
    /** Ordinary cancellation is unchanged. Cancellation and actual exit are distinct events. */
    public static boolean cancelState(java.lang.invoke.VarHandle handle,Object task,int expected,int update) {
        boolean changed=handle.compareAndSet(task,expected,update);
        if(changed)emit(registration,CANCELLED,task,null,currentTask(),null);
        return changed;
    }
    public static void body(Object task) {
        try {
            Registration current=registration;
            if(task!=null&&boundary(false))emit(current,BODY,task,null,currentTask(),currentExecution());
        } catch(Throwable unavailable){lost();}
    }
    public static void created(Object task) {
        try {
            Registration current=registration;
            if(task!=null&&boundary(false)){
                taskRelation(task,null,currentTask());
                emit(current,11,task,null,currentTask(),null);
            }
        } catch(Throwable unavailable){lost();}
    }
    public static void completionMode(Object task,int mode) {
        try {
            if(registration!=null&&task!=null&&boundary(false))emit(registration,13,task,Integer.valueOf(mode),currentTask(),null);
        } catch(Throwable unavailable){lost();}
    }
    public static void delegated(Object wrapper,Object delegate) {
        try {
            if(wrapper!=null&&delegate!=null&&boundary(false))taskRelation(delegate,null,wrapper);
            if(wrapper!=null&&delegate!=null&&boundary(false))emit(registration,21,delegate,wrapper,wrapper,null);
        } catch(Throwable unavailable) { lost(); }
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
        if(queue.size()>=MAX_DEPTH*2) { lost();return; }
        queue.addLast(new Object[]{kind,task,scheduler,parent,execution,Thread.currentThread(),System.nanoTime(),DROPPED.get()});
    }
    public static synchronized long[] invocationState(Object token) {
        if(!(token instanceof Frame frame))return new long[]{-1,0,0,0};
        return new long[]{frame.dispatch?1:0,frame.exited?1:0,frame.retired?1:0,
                frame.emitted&&frame.registration!=null&&frame.registration==registration?1:0};
    }

    /** One actual platform stage invocation, including its synchronous fast path. No synthetic Future is executed. */
    public static Object beginInline(Object source,Object other,Object callback) {
        try {
            if(registration==null||DELIVERING.get()!=null||!inlineBoundary())return null;
            Inline task=new Inline(source,other,callback);
            task.frame=enterFrame(task,false);
            return task;
        } catch(Throwable unavailable){lost();return null;}
    }
    public static void endInline(Object token,Object output) {
        try {
            if(!(token instanceof Inline task)||task.thread!=Thread.currentThread()||task.closed||!inlineBoundary())return;
            task.output=output;
            try { exitFrame(task.frame); }
            finally {
                task.source=null;task.other=null;task.callback=null;task.output=null;task.frame=null;task.thread=null;
                task.closed=true;
            }
        } catch(Throwable unavailable){lost();}
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
        try{return boundary(false)?enterFrame(task,false):null;}catch(Throwable unavailable){lost();return null;}
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
            lost();if(token instanceof Frame frame&&frame.thread==Thread.currentThread()&&CURRENT.get()==frame){releaseAdmission(frame);restore(frame);}
        }
    }
    public static Object enterDelegate(Object wrapper,Object task) {
        try {
            if(wrapper==null||!boundary(false))return null;
            Object token=enterFrame(task,false);
            if(token instanceof Frame frame)frame.delegateOwner=wrapper;
            emit(registration,21,task,wrapper,wrapper,null);
            return token;
        } catch(Throwable unavailable) { lost();return null; }
    }
    public static Object enterDispatch(Object scheduler,Object task) {
        try {
            if(!boundary(false))return null;
            Object token=enterFrame(task,true);
            if(token instanceof Frame frame)frame.scheduler=scheduler;
            return token;
        }catch(Throwable unavailable){lost();return null;}
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
        if(overflow!=null){lost();overflow.depth=Math.addExact(overflow.depth,1);return overflow;}
        if(parent!=null&&parent.depth>=MAX_DEPTH) {
            lost();
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
                if(admission==null)lost();
                else if(!dispatch&&admission.fenced) { if(admission.denied<Long.MAX_VALUE)admission.denied++;frame.blocked=true; }
                else if(admission.active==Integer.MAX_VALUE)lost();
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
        try{if(boundary(false))exitFrame(token);}catch(Throwable unavailable){lost();
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
        if(admission==null||admission.active<=0) { lost();return; }
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
        } catch(Throwable unavailable){lost();}
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
        try{submit(scheduler,task);}catch(Throwable unavailable){lost();}
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
        emit(current,SUBMIT,task,scheduler(scheduler,task),frame==null?null:frame.task,frame);
    }
    /** Publication spans the entire platform submission, including rejection/CallerRuns. */
    public static Object beginSubmission(Object scheduler,Object task) {
        Submission token=null;
        try {
            Registration current=registration;
            if(task==null||DELIVERING.get()!=null||!boundary(true))return null;
            Frame frame=currentFrame(current);
            token=new Submission(task,scheduler(scheduler,task),frame==null?null:frame.task,current);
            if(token.pool!=null)synchronized(CREATION_OWNERS){
                if(SCHEDULER_RETIREMENTS.containsKey(new EventKey(token.pool,null)))throw new SchedulerRetired();
                SCHEDULER_SUBMISSIONS.merge(new EventKey(token.pool,CREATION_QUEUE),1,Integer::sum);token.schedulerTracked=true;
            }
            taskRelation(task,token.scheduler,token.parent);
            synchronized(TaskBridge.class) {
                if(submissionEntries>=ADMISSION_LIMIT)lost();
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
            lost();return token;
        }
    }
    public static void endSubmission(Object value) {
        if(!(value instanceof Submission token)||token.owner!=Thread.currentThread()||token.ended||!boundary(true))return;
        try {
            synchronized(TaskBridge.class) {
                if(token.tracked) {
                    Integer count=SUBMISSIONS.get(token.task);
                    if(count==null||count<=0)lost();
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
        } catch(Throwable unavailable){lost();}
        finally{
            token.ended=true;
            try{
                if(token.schedulerTracked)synchronized(CREATION_OWNERS){
                    EventKey key=new EventKey(token.pool,null);Integer count=SCHEDULER_SUBMISSIONS.get(key);
                    if(count==null||count<=0)lost();else if(count==1)SCHEDULER_SUBMISSIONS.remove(key);else SCHEDULER_SUBMISSIONS.put(key,count-1);
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
        for(;;) {
            long n=DROPPED.get();if(n==Long.MAX_VALUE||!DROPPED.compareAndSet(n,n+1)) {if(n==Long.MAX_VALUE)return;continue;}
            if(n==0) {
                try {
                    String site=WALKER.walk(frames->frames.skip(2).findFirst().map(frame->
                            frame.getClassName()+"#"+frame.getMethodName()+":"+frame.getLineNumber()).orElse("UNKNOWN"));
                    firstLossSite=site+(failure==null?"":":"+failure.getClass().getName()+":"+String.valueOf(failure.getMessage()));
                    System.err.println("RONOVA_TASK_CAPTURE_LOSS_AT:"+firstLossSite);
                } catch(Throwable diagnosticFailure) {firstLossSite="DIAGNOSTIC_UNAVAILABLE";}
            }
            return;
        }
    }
    static void emit(Registration expected,int kind,Object task,Object scheduler,Object parent,Object execution) {
        synchronized(TaskBridge.class) {
            if(expected==null||registration!=expected||DELIVERING.get()!=null)return;
            expected.delivering++;
        }
        DELIVERING.set(expected);
        try {expected.sink.accept(new Object[]{kind,task,scheduler,parent,execution,Thread.currentThread(),System.nanoTime(),DROPPED.get()});}
        catch(Throwable unavailable){lost(unavailable);}
        finally {
            try {
                for(int budget=0;budget<MAX_DEPTH*2;budget++) {
                    var queue=SOURCE_DELIVERIES.get();if(queue==null||queue.isEmpty())break;
                    if(registration!=expected)break;
                    try { expected.sink.accept(queue.removeFirst()); }catch(Throwable failure) { lost(failure); }
                }
                var queue=SOURCE_DELIVERIES.get();if(queue!=null&&!queue.isEmpty())lost();
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
