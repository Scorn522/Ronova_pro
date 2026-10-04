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

/** JDK-only bootstrap bridge. Events describe execution, never grant disposal permission. */
public final class TaskBridge {
    public static final int ENTER=1,EXIT=2,SUBMIT=3,OVERFLOW=4,SUBMIT_ENTER=5,SUBMIT_EXIT=6,DISPATCH_ENTER=7,DISPATCH_EXIT=8,BODY=9,CANCELLED=10;
    private static final Module[] NO_SOURCES=new Module[0];
    private static final int MAX_DEPTH=128;
    private static final java.util.Set<String> CF_STAGES=java.util.Set.of("uniApplyStage","uniAcceptStage","uniRunStage",
            "uniWhenCompleteStage","uniHandleStage","uniExceptionallyStage","uniComposeExceptionallyStage",
            "uniComposeStage","uniCopyStage","uniAsMinimalStage","biApplyStage","biAcceptStage","biRunStage",
            "orApplyStage","orAcceptStage","orRunStage","asyncSupplyStage","asyncRunStage","completedFuture","completedStage",
            "allOf","anyOf","completeAsync","orTimeout","completeOnTimeout");
    private static final ThreadLocal<Frame> CURRENT=new ThreadLocal<>();
    private static final ThreadLocal<Registration> DELIVERING=new ThreadLocal<>();
    private static final ThreadLocal<java.util.ArrayDeque<Object[]>> SOURCE_DELIVERIES=new ThreadLocal<>();
    private static final ThreadLocal<Overflow> OVERFLOW_DEPTH=new ThreadLocal<>();
    private static final java.util.concurrent.atomic.AtomicLong DROPPED=new java.util.concurrent.atomic.AtomicLong();
    private static final StackWalker WALKER=StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE);
    private static final StackWalker ORIGIN_WALKER=StackWalker.getInstance(Set.of(StackWalker.Option.RETAIN_CLASS_REFERENCE,StackWalker.Option.SHOW_HIDDEN_FRAMES));
    private static final java.util.function.Function<java.util.stream.Stream<StackWalker.StackFrame>,Module> INVOKING=new Invoking();
    private static final java.util.function.Function<java.util.stream.Stream<StackWalker.StackFrame>,Boolean> STOPPED_CALL=new StoppedCall();
    private static final java.util.function.Function<java.util.stream.Stream<StackWalker.StackFrame>,Class<?>> MEMORY_CALLER=new MemoryCaller();
    private static final java.util.function.Function<java.util.stream.Stream<StackWalker.StackFrame>,Class<?>> RAW_MEMORY_CALLER=new RawMemoryCaller();
    private static final java.util.function.Function<java.util.stream.Stream<StackWalker.StackFrame>,Class<?>> CORE_CALLER=new CoreCaller();
    private static final java.util.function.Function<java.util.stream.Stream<StackWalker.StackFrame>,Module[]> INVOKING_SOURCES=new InvokingSources();
    private static final java.util.function.Function<java.util.stream.Stream<StackWalker.StackFrame>,Class<?>> CONTROL_WRITER_CALLER=new ControlWriterCaller();
    private static final java.util.function.Function<java.util.stream.Stream<StackWalker.StackFrame>,Boolean> CLASS_REFLECTION_WRITER=new ClassReflectionWriter();
    private static final java.util.function.Function<java.util.stream.Stream<StackWalker.StackFrame>,Boolean> FIELD_GATE_WRITER=new FieldGateWriter();
    // These queries run from Unsafe guards. Linking a lambda here can itself
    // enter the same guard through MethodType's ConcurrentHashMap lookup.
    private static final class MemoryCaller implements java.util.function.Function<java.util.stream.Stream<StackWalker.StackFrame>,Class<?>> {
        public Class<?> apply(java.util.stream.Stream<StackWalker.StackFrame> frames){
            var cursor=frames.iterator();while(cursor.hasNext()){
                Class<?> type=cursor.next().getDeclaringClass();if(type!=TaskBridge.class)return type;
            }return null;
        }
    }
    private static final class ClassReflectionWriter implements java.util.function.Function<java.util.stream.Stream<StackWalker.StackFrame>,Boolean> {
        public Boolean apply(java.util.stream.Stream<StackWalker.StackFrame> frames){
            var cursor=frames.iterator();StackWalker.StackFrame frame=null;
            while(cursor.hasNext()){frame=cursor.next();if(frame.getDeclaringClass()!=TaskBridge.class)break;frame=null;}
            if(frame==null||!frame.isNativeMethod()||frame.getDeclaringClass().getClassLoader()!=null
                    ||frame.getDeclaringClass().getModule()!=Class.class.getModule()
                    ||!frame.getDeclaringClass().getName().equals("jdk.internal.misc.Unsafe")
                    ||!frame.getMethodName().equals("compareAndSetReference")
                    ||!frame.getDescriptor().equals("(Ljava/lang/Object;JLjava/lang/Object;Ljava/lang/Object;)Z"))return false;
            if(!cursor.hasNext())return false;frame=cursor.next();Class<?> atomic=frame.getDeclaringClass();
            if(atomic.getClassLoader()!=null||atomic.getNestHost()!=Class.class||!atomic.getName().equals("java.lang.Class$Atomic")
                    ||!frame.getMethodName().equals("casReflectionData")
                    ||!frame.getDescriptor().equals("(Ljava/lang/Class;Ljava/lang/ref/SoftReference;Ljava/lang/ref/SoftReference;)Z"))return false;
            if(!cursor.hasNext())return false;frame=cursor.next();
            if(frame.getDeclaringClass()!=Class.class||!frame.getMethodName().equals("newReflectionData")
                    ||!frame.getDescriptor().equals("(Ljava/lang/ref/SoftReference;I)Ljava/lang/Class$ReflectionData;"))return false;
            if(!cursor.hasNext())return false;frame=cursor.next();
            return frame.getDeclaringClass()==Class.class&&frame.getMethodName().equals("reflectionData")
                    &&frame.getDescriptor().equals("()Ljava/lang/Class$ReflectionData;");
        }
    }
    private static final class FieldGateWriter implements java.util.function.Function<java.util.stream.Stream<StackWalker.StackFrame>,Boolean>{
        public Boolean apply(java.util.stream.Stream<StackWalker.StackFrame> frames){
            var cursor=frames.iterator();boolean locking=false;
            while(cursor.hasNext()){
                Class<?> type=cursor.next().getDeclaringClass(),host=type.getNestHost();
                if(!locking&&host==TaskBridge.class)continue;
                if(type.getClassLoader()==null&&type.getModule()==Object.class.getModule()){
                    if(host==java.util.concurrent.locks.ReentrantLock.class)locking=true;
                    String name=type.getName();
                    if(name.startsWith("java.")||name.startsWith("jdk.")||name.startsWith("sun."))continue;
                }
                // Native memory callbacks may have no application frame below
                // the bridge. Authenticate the actual JDK lock operation and
                // its immediate controller caller, not the callback wrapper.
                return locking&&(host==TaskBridge.class||host==ExecutionFlow.class);
            }
            return false;
        }
    }
    private static final class RawMemoryCaller implements java.util.function.Function<java.util.stream.Stream<StackWalker.StackFrame>,Class<?>> {
        public Class<?> apply(java.util.stream.Stream<StackWalker.StackFrame> frames){
            var cursor=frames.iterator();while(cursor.hasNext()){
                Class<?> type=cursor.next().getDeclaringClass(),host=type.getNestHost();
                if(host!=TaskBridge.class&&host!=NativeControl.class)return type;
            }return null;
        }
    }
    private static final class CoreCaller implements java.util.function.Function<java.util.stream.Stream<StackWalker.StackFrame>,Class<?>> {
        public Class<?> apply(java.util.stream.Stream<StackWalker.StackFrame> frames){
            var cursor=frames.iterator();while(cursor.hasNext()){
                Class<?> type=cursor.next().getDeclaringClass();if(type.getClassLoader()!=null)return type;
            }return TaskBridge.class;
        }
    }
    private static final class InvokingSources implements java.util.function.Function<java.util.stream.Stream<StackWalker.StackFrame>,Module[]> {
        public Module[] apply(java.util.stream.Stream<StackWalker.StackFrame> frames){
            var cursor=frames.iterator();while(cursor.hasNext()){
                var frame=cursor.next();Module[] sources=CodeSourceBridge.frameSources(frame);if(sources.length!=0)return sources;
                Module physical=DefinitionBridge.module(frame.getDeclaringClass());if(producer(physical))return new Module[]{physical};
            }return NO_SOURCES;
        }
    }
    private static final class ControlWriterCaller implements java.util.function.Function<java.util.stream.Stream<StackWalker.StackFrame>,Class<?>> {
        public Class<?> apply(java.util.stream.Stream<StackWalker.StackFrame> frames){
            var cursor=frames.iterator();while(cursor.hasNext()){
                var frame=cursor.next();Class<?> type=frame.getDeclaringClass();String name=type.getName();boolean selected;
                if(type==TaskBridge.class||name.startsWith("dev.ronova.pro.bootstrap.TaskBridge$")){
                    String method=frame.getMethodName();
                    selected=!EXTERNAL_GUARD_ENTRIES.contains(method)
                            &&!(name.equals("dev.ronova.pro.bootstrap.TaskBridge$ControlRef")&&(method.equals("clear")||method.equals("enqueue")));
                }else if(type==BackingBridge.class)selected=frame.getMethodName().equals("readUnsafe");
                else if(type==ResourceBridge.class){
                    String method=frame.getMethodName();selected=!(method.equals("mutationAllowed")||method.equals("critical")||method.equals("fieldMutationAllowed")||method.equals("unsafeMutationAllowed"));
                }else if(type==SourceMapBridge.class){
                    String method=frame.getMethodName();selected=!(method.equals("valueAllowed")||method.equals("checkIncoming")||method.equals("internal")||method.equals("changed"));
                }
                else selected=!name.startsWith("java.")&&!name.startsWith("jdk.")&&!name.startsWith("sun.");
                if(selected)return type;
            }return null;
        }
    }
    private static final class StoppedCall implements java.util.function.Function<java.util.stream.Stream<StackWalker.StackFrame>,Boolean> {
        public Boolean apply(java.util.stream.Stream<StackWalker.StackFrame> frames){
            var cursor=frames.iterator();while(cursor.hasNext()){
                var frame=cursor.next();if(modStopped(DefinitionBridge.module(frame.getDeclaringClass())))return true;
                for(Module source:CodeSourceBridge.frameSources(frame))if(modStopped(source))return true;
            }return false;
        }
    }
    private static final class Invoking implements java.util.function.Function<java.util.stream.Stream<StackWalker.StackFrame>,Module> {
        public Module apply(java.util.stream.Stream<StackWalker.StackFrame> frames){
            var cursor=frames.iterator();while(cursor.hasNext()){
                var frame=cursor.next();for(Module source:CodeSourceBridge.frameSources(frame))if(producer(source))return source;
                Module owner=DefinitionBridge.module(frame.getDeclaringClass());if(producer(owner))return owner;
            }return null;
        }
    }
    static void prepareDefinitionCalls(){
        if(WALKER.getCallerClass()!=DefinitionBridge.class)throw new SecurityException("DEFINITION_BRIDGE_REQUIRED");
        invokingModule();ORIGIN_WALKER.walk(STOPPED_CALL);controlCaller();
    }
    private static volatile Registration registration;
    private static volatile String coverage="AGENT_NOT_READY";
    private static final java.util.Set<String> certified=new java.util.HashSet<>();
    private static long certificationEpoch;
    private static final int ADMISSION_LIMIT=8192;
    private static final Object BLOCKED=new Object();
    private static volatile Module[] stoppedModules=new Module[0];
    private static final java.lang.ref.ReferenceQueue<Object> EVENT_QUEUE=new java.lang.ref.ReferenceQueue<>();
    private static final java.util.HashMap<EventKey,Module> EVENT_OWNERS=new java.util.HashMap<>();
    private static final java.lang.ref.ReferenceQueue<Object> CREATION_QUEUE=new java.lang.ref.ReferenceQueue<>();
    private static final java.util.HashMap<EventKey,Module> CREATION_OWNERS=new java.util.HashMap<>();
    private static final java.util.Map<EventKey,Set<Module>> CREATION_SOURCES=new java.util.HashMap<>();
    private static final Set<EventKey> CREATION_UNKNOWN=new java.util.HashSet<>();
    private static final java.util.Map<EventKey,Set<Module>> TASK_OWNERS=new java.util.HashMap<>();
    private static final java.util.Map<EventKey,java.lang.ref.WeakReference<Object>> TASK_SCHEDULERS=new java.util.HashMap<>();
    private static final java.util.Set<EventKey> SHARED_SCHEDULERS=new java.util.HashSet<>();
    private static final Map<EventKey,Set<Module>> SCHEDULER_SOURCES=new HashMap<>();
    private static final Map<EventKey,Integer> SCHEDULER_SUBMISSIONS=new HashMap<>();
    private static final Map<EventKey,SchedulerRetirement> SCHEDULER_RETIREMENTS=new HashMap<>();
    private static final Map<EventKey,ThreadOrigin> THREAD_ORIGINS=new HashMap<>();
    private static final Set<EventKey> RETIRED_THREADS=new java.util.HashSet<>();
    private static final Map<EventKey,java.lang.ref.WeakReference<Object>> THREAD_TIMERS=new HashMap<>();
    private static final class ThreadOrigin {
        final Set<Module> sources=Collections.newSetFromMap(new IdentityHashMap<>());
        final java.util.concurrent.locks.ReentrantLock io=new java.util.concurrent.locks.ReentrantLock();
        boolean unknown,interruptPending;int starting;
    }
    private static final class ThreadStart {
        Thread thread,caller=Thread.currentThread();final ThreadOrigin origin;boolean closed;
        ThreadStart(Thread thread,ThreadOrigin origin){this.thread=thread;this.origin=origin;}
    }
    private record InterruptRequest(Thread target,Object scheduler){}
    private static final ThreadLocal<InterruptRequest> RETIRE_INTERRUPTS=new ThreadLocal<>();
    private static final class ThreadIo {
        final Thread caller=Thread.currentThread();final ThreadOrigin origin;final boolean clearing;boolean closed;
        ThreadIo(ThreadOrigin origin,boolean clearing){this.origin=origin;this.clearing=clearing;}
    }
    private static final class SchedulerRetirement {
        final Set<Module> sources;
        final List<Runnable> removed=new ArrayList<>();
        boolean invoking,finished,resultUnknown;
        SchedulerRetirement(Set<Module> sources){this.sources=Set.copyOf(sources);}
    }
    private static final class SchedulerRetired extends java.util.concurrent.RejectedExecutionException {
        SchedulerRetired(){super("RONOVA_PRIVATE_SCHEDULER_RETIRED");}
    }
    private static final java.util.Map<EventKey,Thread> TIMER_RUNNING=new java.util.HashMap<>();
    private static final java.util.Set<Module> PRODUCER_MODULES=java.util.Collections.newSetFromMap(new java.util.WeakHashMap<Module,Boolean>());
    public static void registerProducerModule(Module module) {
        if(!ControlBridge.owns(WALKER.getCallerClass()))throw new SecurityException("PRODUCER_AGENT_REQUIRED");
        synchronized(PRODUCER_MODULES) {PRODUCER_MODULES.add(module);}
    }
    static Module invokingModule() {
        return ORIGIN_WALKER.walk(INVOKING);
    }
    static boolean producer(Module module) {synchronized(PRODUCER_MODULES){return PRODUCER_MODULES.contains(module);}}
    private static void taskRelation(Object task,Object scheduler,Object parent) {
        if(task==null)return;
        Module[] actual=invokingSources();Module direct=actual.length==1?actual[0]:actual.length==0?invokingModule():null;if(direct==null&&actual.length==0&&parent!=null)direct=objectModule(parent);
        if(direct==null&&task.getClass().getClassLoader()!=null)direct=task.getClass().getModule();
        synchronized(CREATION_OWNERS) {
            reapCreationOwners();EventKey key=new EventKey(task,CREATION_QUEUE);
            Set<Module> owners=TASK_OWNERS.computeIfAbsent(key,ignored->java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<Module,Boolean>()));
            // A null entry represents an actual submission with no selected producer, not an inferred owner.
            if(actual.length==0)owners.add(direct);else owners.addAll(java.util.Arrays.asList(actual));
            Set<Module> creation=CREATION_SOURCES.get(new EventKey(task,null));if(creation!=null)owners.addAll(creation);
            Module creator=CREATION_OWNERS.get(new EventKey(task,null));if(creator!=null)owners.add(creator);
            Module definition=DefinitionBridge.module(task.getClass());if(producer(definition))owners.add(definition);
            if(scheduler!=null) {
                TASK_SCHEDULERS.put(key,new java.lang.ref.WeakReference<>(scheduler));
                schedulerSources(scheduler,owners,CREATION_UNKNOWN.contains(key));
                Module poolCreator=CREATION_OWNERS.get(new EventKey(scheduler,null));
                if(poolCreator==null||owners.size()!=1||!owners.contains(poolCreator))SHARED_SCHEDULERS.add(new EventKey(scheduler,CREATION_QUEUE));
            }
        }
    }
    private static void reapCreationOwners() {
        EventKey stale;while((stale=(EventKey)CREATION_QUEUE.poll())!=null) {
            CREATION_OWNERS.remove(stale);CREATION_SOURCES.remove(stale);CREATION_UNKNOWN.remove(stale);TASK_OWNERS.remove(stale);TASK_SCHEDULERS.remove(stale);SHARED_SCHEDULERS.remove(stale);
            TIMER_RUNNING.remove(stale);
            SCHEDULER_SOURCES.remove(stale);SCHEDULER_SUBMISSIONS.remove(stale);SCHEDULER_RETIREMENTS.remove(stale);
            THREAD_ORIGINS.remove(stale);RETIRED_THREADS.remove(stale);THREAD_TIMERS.remove(stale);
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
    private static Set<Module> selectedSchedulerModules(Module[] modules){
        Set<Module> selected=Collections.newSetFromMap(new IdentityHashMap<>());
        if(modules!=null)Collections.addAll(selected,modules);selected.remove(null);return selected;
    }
    private static String schedulerDisposition(List<Object> chain,Set<Module> selected){
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
    private static void schedulerSources(Object scheduler,Collection<Module> sources,boolean unknown){
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
    private static Object schedulerCall(Object pool,String name,Class<?> result)throws ReflectiveOperationException {
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
    private static void requestThreadInterrupt(Thread thread)throws ReflectiveOperationException {
        InterruptRequest previous=RETIRE_INTERRUPTS.get();RETIRE_INTERRUPTS.set(new InterruptRequest(thread,null));
        try {
            var lookup=java.lang.invoke.MethodHandles.privateLookupIn(Thread.class,java.lang.invoke.MethodHandles.lookup());
            lookup.findSpecial(Thread.class,"interrupt",java.lang.invoke.MethodType.methodType(void.class),Thread.class).invoke(thread);
        }catch(ReflectiveOperationException failure){throw failure;}
        catch(Throwable failure){throw new java.lang.reflect.InvocationTargetException(failure);}
        finally{if(previous==null)RETIRE_INTERRUPTS.remove();else RETIRE_INTERRUPTS.set(previous);}
    }
    private static final class ThreadPlatform {
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
    private static boolean threadWorker(Thread thread,Object target){
        return thread instanceof java.util.concurrent.ForkJoinWorkerThread||thread.getClass()==ThreadPlatform.TIMER_WORKER
                ||target!=null&&target.getClass()==ThreadPlatform.POOL_WORKER;
    }
    private static Object threadScheduler(Thread thread,Object target)throws IllegalAccessException {
        if(thread instanceof java.util.concurrent.ForkJoinWorkerThread)return ThreadPlatform.FORK_POOL.get(thread);
        if(target!=null&&target.getClass()==ThreadPlatform.POOL_WORKER)return ThreadPlatform.WORKER_POOL.get(target);
        synchronized(CREATION_OWNERS){var timer=THREAD_TIMERS.get(new EventKey(thread,null));return timer==null?null:timer.get();}
    }
    public static Object threadScheduler(Thread thread)throws ReflectiveOperationException {
        if(!coreWriter())throw new SecurityException("GROUP_THREAD_CORE_REQUIRED");
        return threadScheduler(thread,ThreadPlatform.TARGET.get(thread));
    }
    private static void timerThread(Object timer){
        try{
            Object thread=ThreadPlatform.TIMER_THREAD.get(timer);if(thread!=null)
                THREAD_TIMERS.put(new EventKey(thread,CREATION_QUEUE),new java.lang.ref.WeakReference<>(timer));
        }catch(IllegalAccessException unavailable){throw new IllegalStateException("TIMER_THREAD_UNOBSERVED",unavailable);}
    }
    private static void threadSources(ThreadOrigin origin,Object object){
        if(object==null)return;EventKey key=new EventKey(object,null);
        Set<Module> creation=CREATION_SOURCES.get(key),tasks=TASK_OWNERS.get(key);
        if(creation!=null)origin.sources.addAll(creation);if(tasks!=null)origin.sources.addAll(tasks);
        if(origin.sources.remove(null)||CREATION_UNKNOWN.contains(key))origin.unknown=true;
        Module definition=DefinitionBridge.module(object.getClass());if(producer(definition))origin.sources.add(definition);
    }
    private static ThreadOrigin threadOrigin(Thread thread,Object target)throws NoSuchMethodException {
        ThreadOrigin origin=THREAD_ORIGINS.computeIfAbsent(new EventKey(thread,CREATION_QUEUE),ignored->new ThreadOrigin());
        threadSources(origin,thread);threadSources(origin,target);
        Class<?> implementation=thread.getClass().getMethod("run").getDeclaringClass();Module declaration=DefinitionBridge.module(implementation);
        if(producer(declaration))origin.sources.add(declaration);
        else if(implementation!=Thread.class&&!threadWorker(thread,target))origin.unknown=true;
        return origin;
    }
    private static boolean allThreadSourcesStopped(ThreadOrigin origin){
        if(origin.unknown||origin.sources.isEmpty())return false;
        for(Module source:origin.sources)if(!modStopped(source))return false;return true;
    }
    public static Object threadStarting(Thread thread)throws ReflectiveOperationException {
        if(WALKER.getCallerClass()!=Thread.class)throw new SecurityException("ACTUAL_THREAD_START_REQUIRED");
        return beginThreadStart(thread,null);
    }
    private static Object beginThreadStart(Thread thread,Module[] nativeSources)throws ReflectiveOperationException {
        if(ThreadPlatform.STATUS.getInt(thread)!=0)return null;
        Object target=ThreadPlatform.TARGET.get(thread);boolean worker=threadWorker(thread,target);
        Module[] sources=worker?new Module[0]:invokingSources();boolean unknown=!worker&&CodeSourceBridge.executionUnknown();
        boolean stopped=!worker&&stoppedInvocation();
        synchronized(CREATION_OWNERS){
            reapCreationOwners();ThreadOrigin origin=threadOrigin(thread,target);
            if(!worker&&nativeSources!=null)for(Module source:nativeSources){
                if(source==null)unknown=true;
                else {stopped|=modStopped(source);if(producer(source))origin.sources.add(source);}
            }
            if(!worker&&(RETIRED_THREADS.contains(new EventKey(thread,null))||stopped))throw new IllegalThreadStateException("RONOVA_RETIRED_THREAD_START");
            Collections.addAll(origin.sources,sources);origin.unknown|=unknown;
            if(!worker&&allThreadSourcesStopped(origin))throw new IllegalThreadStateException("RONOVA_STOPPED_THREAD_START");
            ThreadStart token=new ThreadStart(thread,origin);origin.starting++;return token;
        }
    }
    public static void threadStartFinished(Object value){
        if(WALKER.getCallerClass()!=Thread.class)throw new SecurityException("ACTUAL_THREAD_START_REQUIRED");
        finishThreadStart(value);
    }
    private static void finishThreadStart(Object value){
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
    private static Object beginThreadInterrupt(Thread thread,Module[] nativeSources)throws ReflectiveOperationException {
        InterruptRequest request=RETIRE_INTERRUPTS.get();
        if(request!=null&&!requestedThread(request,thread))return Boolean.FALSE;
        requireThreadControl(thread,2,nativeSources);
        if(request==null)return null;
        ThreadOrigin origin;synchronized(CREATION_OWNERS){origin=THREAD_ORIGINS.computeIfAbsent(new EventKey(thread,CREATION_QUEUE),ignored->new ThreadOrigin());}
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
    private static void finishThreadInterrupt(Object value){
        if(value==null)return;ThreadIo token=threadIoToken(value);
        synchronized(CREATION_OWNERS){if(!token.closed){token.closed=true;token.origin.io.unlock();}}
    }
    private static boolean requestedThread(InterruptRequest request,Thread thread)throws IllegalAccessException {
        return request.target()==thread||request.scheduler()!=null&&threadScheduler(thread,ThreadPlatform.TARGET.get(thread))==request.scheduler();
    }
    private static void requireThreadControl(Thread thread,int operation,Module[] nativeSources)throws ReflectiveOperationException {
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
    private static Object nativeThreadOperation(Thread thread,Module[] sources,int operation)throws ReflectiveOperationException {
        if(!NativeControl.threadOperationBoundary(thread,sources,operation))throw new SecurityException("ACTUAL_NATIVE_THREAD_OPERATION_REQUIRED");
        if(operation==1)return beginThreadStart(thread,sources);
        if(operation==2)return beginThreadInterrupt(thread,sources);
        requireThreadControl(thread,operation,sources);return null;
    }
    private static void nativeThreadOperationFinished(Object token,boolean completed){
        if(!NativeControl.threadOperationFinishing(token,completed))throw new SecurityException("ACTUAL_NATIVE_THREAD_FINISH_REQUIRED");
        if(token==null||token==Boolean.FALSE)return;
        if(token instanceof ThreadStart)finishThreadStart(token);else finishThreadInterrupt(token);
    }
    private static ThreadIo threadIoToken(Object value){
        if(!(value instanceof ThreadIo token)||token.caller!=Thread.currentThread()||!token.closed&&!token.origin.io.isHeldByCurrentThread())
            throw new SecurityException("ACTUAL_THREAD_IO_WINDOW_REQUIRED");return token;
    }
    public static Object threadBlocking(Object blocker)throws ReflectiveOperationException,java.io.InterruptedIOException {
        if(WALKER.getCallerClass()!=Thread.class)throw new SecurityException("ACTUAL_THREAD_BLOCKER_REQUIRED");
        Thread thread=Thread.currentThread();ThreadOrigin origin;
        synchronized(CREATION_OWNERS){origin=THREAD_ORIGINS.computeIfAbsent(new EventKey(thread,CREATION_QUEUE),ignored->new ThreadOrigin());}
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
    private static void timerThreadCaller(){
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
    private static boolean selectedTimerTask(Object task,Set<Module> selected){
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
    private static Object schedulerDelegate(Object value) {
        List<Object> chain=schedulerChain(value);return chain.isEmpty()?null:chain.get(chain.size()-1);
    }
    private static final class SchedulerDelegates {
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
    private static List<Object> schedulerChain(Object value){
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
    private static final class EventKey extends java.lang.ref.WeakReference<Object> {
        final int hash;
        EventKey(Object listener,java.lang.ref.ReferenceQueue<Object> queue){super(listener,queue);hash=System.identityHashCode(listener);}
        @Override public int hashCode(){return hash;}
        @Override public boolean equals(Object other){return this==other||other instanceof EventKey key&&get()!=null&&get()==key.get();}
    }
    public static boolean modOwnerAllowed(Object owner) {
        Module module=objectModule(owner);
        return !modStopped(module)&&(!stoppedInvocation()||IoBridge.retirementCloseOwner(owner)||ResourceBridge.bufferCleanupOwner(owner));
    }
    public static void modListenerRegistered(Object owner,Object listener) {
        if(owner==null||listener==null)return;
        Module module=owner instanceof Class<?> type?type.getModule():owner.getClass().getModule();
        synchronized(EVENT_OWNERS) {
            EventKey stale;while((stale=(EventKey)EVENT_QUEUE.poll())!=null)EVENT_OWNERS.remove(stale);
            EVENT_OWNERS.put(new EventKey(listener,EVENT_QUEUE),module);
        }
    }
    public static boolean modListenerAllowed(Object listener) {
        if(listener==null)return true;
        Module owner;
        synchronized(EVENT_OWNERS){owner=EVENT_OWNERS.get(new EventKey(listener,null));}
        return !modStopped(owner)&&!modStopped(listener.getClass().getModule());
    }
    public static synchronized void stopModules(Module[] modules) {
        Class<?> caller=WALKER.walk(frames->frames.skip(1).findFirst().map(StackWalker.StackFrame::getDeclaringClass).orElse(null));
        try {
            if(caller!=Class.forName("dev.ronova.pro.agent.RecoveryAgent",false,ClassLoader.getSystemClassLoader()))
                throw new SecurityException("MOD_GROUP_AGENT_REQUIRED");
        } catch(ClassNotFoundException missing) {throw new SecurityException("MOD_GROUP_AGENT_UNAVAILABLE",missing);}
        Module[] existing=stoppedModules;
        java.util.ArrayList<Module> next=new java.util.ArrayList<>(java.util.Arrays.asList(existing));
        for(Module module:modules)if(module!=null&&!next.contains(module))next.add(module);
        stoppedModules=next.toArray(Module[]::new);
    }
    static boolean modStopped(Module module) {
        for(Module entry:stoppedModules)if(entry==module)return true;
        return false;
    }
    private static boolean stoppedInvocation() {
        if(stoppedModules.length==0)return false;
        if(NetworkBridge.stoppedSource())return true;
        if(ClientBridge.stoppedSource())return true;
        for(Module source:CodeSourceBridge.executionSources())if(modStopped(source))return true;
        return ORIGIN_WALKER.walk(STOPPED_CALL);
    }
    private static final java.util.IdentityHashMap<Object,Admission> ADMISSIONS=new java.util.IdentityHashMap<>();
    private static final java.util.IdentityHashMap<Object,Integer> SUBMISSIONS=new java.util.IdentityHashMap<>();
    private static final ThreadLocal<Helper> HELPER=new ThreadLocal<>();
    private static final java.util.IdentityHashMap<Object,Integer> HELD_TASKS=new java.util.IdentityHashMap<>();
    private static final java.util.IdentityHashMap<Object[],Integer> HELD_ARRAYS=new java.util.IdentityHashMap<>();
    private static final Object HELPER_LOCK=new Object();
    private static int helperSlots;
    private static final class Helper {
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
    private static int submissionEntries;
    private static final ThreadLocal<Registration> AUTHORITY=new ThreadLocal<>();
    private static final java.util.IdentityHashMap<Object,java.util.ArrayList<QueueRelease>> QUEUE_RELEASES=new java.util.IdentityHashMap<>();
    /** Published under the ledger lock; idle workers never take that lock for an unrelated queue. */
    private static volatile Object[] queuesWithRelease=new Object[0];
    private static int queueReleaseCount;
    private static final java.util.IdentityHashMap<Object,java.util.ArrayList<Wait>> WAITERS=new java.util.IdentityHashMap<>();
    private static final ThreadLocal<Wait> CURRENT_WAIT=new ThreadLocal<>();
    private static int waiterEntries;
    private static final java.util.WeakHashMap<Object,java.lang.ref.WeakReference<Object>> EXITED_WAIT_NODES=new java.util.WeakHashMap<>();
    private static final class Wait {
        Object task;
        final Registration owner;
        Wait previous=CURRENT_WAIT.get();
        Object node;
        Thread thread=Thread.currentThread();
        boolean tracked,closed;
        Wait(Object task,Registration owner) { this.task=task;this.owner=owner; }
    }
    private static final class QueueRelease {
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
    private static final class Admission {
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
        if(expected!=certificationEpoch||required.length==0||!certified.containsAll(java.util.List.of(required)))return false;
        Registration prior=AUTHORITY.get();AUTHORITY.set(registration);
        try { action.run();return true; }
        finally { if(prior==null)AUTHORITY.remove();else AUTHORITY.set(prior); }
    }
    private TaskBridge() {}
    public static int abiVersion(){return 48;}
    private record CreationDefinition(java.lang.ref.WeakReference<ClassLoader> loader,boolean bootstrap,String name,Set<String> sites,ControlRef actual) { }
    private record CreationObserver(Class<?> owner,java.lang.invoke.MethodHandle callback,java.lang.invoke.MethodHandle failure) { }
    private static final List<CreationDefinition> CREATIONS=new ArrayList<>();
    private static final ThreadLocal<Class<?>> CREATION_DELIVERY=new ThreadLocal<>();
    private static final StackWalker CREATION_CALLER=StackWalker.getInstance(Set.of(StackWalker.Option.RETAIN_CLASS_REFERENCE,StackWalker.Option.SHOW_HIDDEN_FRAMES));
    private static volatile CreationObserver creationObserver;
    private static final ClassValue<Set<String>> CREATION_SITES=new ClassValue<>() {
        @Override protected Set<String> computeValue(Class<?> type) {
            synchronized(CREATIONS) {
                for(CreationDefinition definition:CREATIONS)if(definition.actual!=null?definition.actual.get()==type
                        :!type.isHidden()&&definition.bootstrap==(type.getClassLoader()==null)&&definition.loader.get()==type.getClassLoader()&&definition.name.equals(type.getName()))return definition.sites;
            }
            return Set.of();
        }
    };
    public static void registerCreationSites(ClassLoader loader,String name,String[] sites,Class<?> actual) {
        Class<?> caller=CREATION_CALLER.getCallerClass();
        if(caller.getClassLoader()!=ClassLoader.getSystemClassLoader()||!caller.getName().equals("dev.ronova.pro.agent.CreationBoundary"))return;
        if(!CREATION_CALLER.walk(frames->frames.anyMatch(frame->frame.getDeclaringClass().getClassLoader()==null
                &&frame.getClassName().equals("sun.instrument.TransformerManager")&&frame.getMethodName().equals("transform"))))return;
        synchronized(CREATIONS) {
            CREATIONS.removeIf(definition->!definition.bootstrap&&definition.loader.get()==null||definition.actual!=null&&definition.actual.get()==null
                    ||definition.actual==null&&definition.bootstrap==(loader==null)&&definition.loader.get()==loader&&definition.name.equals(name));
            if(sites.length!=0) {
                if(CREATIONS.size()>=65536) { lost();return; }
                CREATIONS.add(new CreationDefinition(new java.lang.ref.WeakReference<>(loader),loader==null,name,Set.of(sites),null));
            }
            if(actual!=null)CREATION_SITES.remove(actual);
        }
    }
    public static synchronized void installCreationObserver(Class<?> owner,java.lang.invoke.MethodHandle callback,java.lang.invoke.MethodHandle failure) {
        requirePolicyInstaller();
        Guards current=guards;
        if(current==null||current.owner()!=owner)throw new IllegalArgumentException("CREATION_OBSERVER_OWNER");
        creationObserver=new CreationObserver(owner,callback.asType(java.lang.invoke.MethodType.methodType(void.class,Object.class)),
                failure.asType(java.lang.invoke.MethodType.methodType(void.class,Object.class)));
    }
    public static synchronized void installSourceWriteObserver(Class<?> owner,java.lang.invoke.MethodHandle callback,java.lang.invoke.MethodHandle changed) {
        requirePolicyInstaller();
        if(guards==null||guards.owner()!=owner)throw new IllegalArgumentException("SOURCE_WRITE_OBSERVER_OWNER");
        SourceMapBridge.install(callback,changed);
    }
    private static final class ProducerFrame {
        final ProducerFrame parent=PRODUCERS.get();final Thread thread=Thread.currentThread();
        final java.util.Set<Object> inputs=new java.util.HashSet<>();
        final java.util.List<Object[]> fields=new java.util.ArrayList<>();
        final java.util.Set<Object> created=java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
        boolean closed,overflow;
    }
    private static final ThreadLocal<ProducerFrame> PRODUCERS=new ThreadLocal<>();
    private static volatile java.lang.invoke.MethodHandle producerObserver;
    private static final ThreadLocal<Class<?>> PRODUCER_DELIVERY=new ThreadLocal<>();
    private static final ThreadLocal<Boolean> PRODUCER_METHOD_RECEIVER=new ThreadLocal<>();
    public static boolean deliveringProducerTo(Class<?> owner) {return PRODUCER_DELIVERY.get()==owner;}
    public static boolean deliveringProducerReceiverTo(Class<?> owner) {return PRODUCER_DELIVERY.get()==owner&&PRODUCER_METHOD_RECEIVER.get()!=null;}
    public static synchronized void installProducerObserver(Class<?> owner,java.lang.invoke.MethodHandle observer) {
        requirePolicyInstaller();
        if(guards==null||guards.owner()!=owner)throw new IllegalArgumentException("PRODUCER_OWNER");
        producerObserver=observer.asType(java.lang.invoke.MethodType.methodType(Object.class,Object.class,Object.class));
    }
    private static boolean producerSite() {
        var frame=CREATION_CALLER.walk(stream->stream.skip(2).findFirst().orElse(null));
        return frame!=null&&CREATION_SITES.get(frame.getDeclaringClass()).contains(frame.getMethodName()+frame.getDescriptor()+":"+frame.getByteCodeIndex());
    }
    public static Object producerEnter() {
        if(producerObserver==null||!producerSite())return null;
        ProducerFrame parent=PRODUCERS.get();int depth=0;
        for(var p=parent;p!=null;p=p.parent)if(++depth>=128) {parent.overflow=true;return null;}
        ProducerFrame frame=new ProducerFrame();PRODUCERS.set(frame);return frame;
    }
    public static void producerInput(Object receiver,Object value) {
        ProducerFrame frame=PRODUCERS.get();var observer=producerObserver;
        if(frame==null||observer==null||value==null||PRODUCER_DELIVERY.get()!=null||!producerSite())return;
        PRODUCER_DELIVERY.set(guards.owner());
        try {
            Object origin=(Object)observer.invokeExact(receiver,value);
            if(origin!=null) {if(frame.inputs.size()<64)frame.inputs.add(origin);else frame.overflow=true;}
        } catch(Throwable unavailable) {frame.overflow=true;lost();}
        finally {PRODUCER_DELIVERY.remove();}
    }
    /** Captured only at woven field reads; boxed scalars remain values, never guessed subjects. */
    public static void producerFieldInput(Object receiver,Class<?> declaring,String name,String descriptor,Object value) {
        ProducerFrame frame=PRODUCERS.get();var observer=producerObserver;
        if(frame==null||observer==null||receiver==null||PRODUCER_DELIVERY.get()!=null||!producerSite())return;
        if(frame.fields.size()>=64) {frame.overflow=true;return;}
        Object[] field={receiver,declaring,name,descriptor,value};frame.fields.add(field);
        PRODUCER_DELIVERY.set(guards.owner());
        try {
            Object origin=(Object)observer.invokeExact(receiver,(Object)field);
            if(origin!=null) {if(frame.inputs.size()<64)frame.inputs.add(origin);else frame.overflow=true;}
        }catch(Throwable unavailable){frame.overflow=true;lost();}
        finally {PRODUCER_DELIVERY.remove();}
    }
    public static Object[][] producerFields() {
        Guards current=guards;if(current==null||CREATION_CALLER.getCallerClass()!=current.owner())return new Object[0][];
        java.util.List<Object[]> fields=new java.util.ArrayList<>();
        for(var frame=PRODUCERS.get();frame!=null;frame=frame.parent) {
            if(frame.overflow)return new Object[0][];
            for(Object[] field:frame.fields)fields.add(field.clone());
        }
        return fields.toArray(Object[][]::new);
    }
    /** The agent supplies a proven field-to-setUUID dataflow at this actual assignment site. */
    public static void producerIdentityAssigned(Object entity,Object holder,Class<?> owner,String field,Object uuid,boolean nullable) {
        var frame=PRODUCERS.get();var observer=producerObserver;
        if(frame==null||observer==null||!producerSite()||!frame.created.contains(entity)||PRODUCER_DELIVERY.get()!=null)return;
        var caller=CREATION_CALLER.walk(stream->stream.skip(1).findFirst().orElse(null));
        if(caller==null)return;
        // Slot retirement uses the holder's live definition. Helper classes have their own
        // lifecycle, so they receive result control until an independent profile is supported.
        nullable&=caller.getDeclaringClass()==(holder instanceof Class<?> type?type:holder.getClass());
        Object[] packet={entity,holder,owner,field,uuid,nullable,caller.getMethodName()+caller.getDescriptor()};
        PRODUCER_DELIVERY.set(guards.owner());
        try {Object ignored=(Object)observer.invokeExact(entity,(Object)packet);}
        catch(Throwable unavailable){frame.overflow=true;lost();}
        finally {PRODUCER_DELIVERY.remove();}
    }
    public static Object producerResult(Object result) {
        return producerSite()&&!taskEffectAllowed(guards,result,"source-result")?null:result;
    }
    public static void producerExit(Object token) {
        if(!(token instanceof ProducerFrame frame)||frame.thread!=Thread.currentThread()||frame.closed)return;
        if(PRODUCERS.get()!=frame) {lost();return;}frame.closed=true;
        if(frame.parent==null)PRODUCERS.remove();
        else {
            PRODUCERS.set(frame.parent);frame.parent.overflow|=frame.overflow;
            // Inputs belong to the invocation that consumed them. Carrying a child's receiver
            // into its caller would claim every later allocation in that caller, even after
            // the child has returned and its result is unrelated to the next allocation.
        }
        frame.inputs.clear();
        frame.fields.clear();
        frame.created.clear();
    }
    /** Only the installed Core owner can read the actual current invocation's inputs. */
    public static Object[] producerInputs(boolean includeParents) {
        Guards current=guards;if(current==null||CREATION_CALLER.getCallerClass()!=current.owner())return new Object[0];
        java.util.List<Object> inputs=new java.util.ArrayList<>();
        for(var frame=PRODUCERS.get();frame!=null;frame=includeParents?frame.parent:null) {
            if(frame.overflow)inputs.add(Boolean.FALSE);inputs.addAll(frame.inputs);
        }
        return inputs.toArray();
    }
    public static boolean deliveringCreationTo(Class<?> owner) { return CREATION_DELIVERY.get()==owner; }
    public static Module creationModule(Object object) {
        if(object==null)return null;
        synchronized(CREATION_OWNERS) {EventKey key=new EventKey(object,null);return CREATION_UNKNOWN.contains(key)?null:CREATION_OWNERS.get(key);}
    }
    static Module[] invokingSources(){
        Module[] scoped=scopedSources();if(scoped.length!=0)return scoped;
        return ORIGIN_WALKER.walk(INVOKING_SOURCES);
    }
    static boolean stoppedEffects(){return stoppedInvocation();}
    private static Set<Module> addModuleSources(Set<Module> sources,Module[] modules,boolean producersOnly){
        if(modules!=null)for(Module module:modules)if(!producersOnly||module==null||producer(module)){
            if(sources==null)sources=Collections.newSetFromMap(new IdentityHashMap<>());sources.add(module);
        }
        return sources;
    }
    private static Set<Module> scopedSourceSet(){
        Set<Module> modules=addModuleSources(null,NetworkBridge.currentSources(),false);
        modules=addModuleSources(modules,ClientBridge.currentSources(),false);
        modules=addModuleSources(modules,IoBridge.currentSources(),false);
        modules=addModuleSources(modules,ResourceBridge.currentBufferSources(),false);
        modules=addModuleSources(modules,CodeSourceBridge.executionSources(),false);
        return modules;
    }
    private static Module[] scopedSources(){
        Set<Module> modules=scopedSourceSet();return modules==null?NO_SOURCES:modules.toArray(Module[]::new);
    }
    private static Module[] nativeNetworkSources(){
        if(!NativeControl.sourceCapture())throw new SecurityException("ACTUAL_NATIVE_SOURCE_CAPTURE_REQUIRED");return scopedSources();
    }
    private static Module[] nativeProcessSources(){
        if(!NativeControl.sourceCapture())throw new SecurityException("ACTUAL_NATIVE_SOURCE_CAPTURE_REQUIRED");return invokingSources();
    }
    public static Module[] creationSources(Object object){
        synchronized(CREATION_OWNERS){
            Set<Module> sources=CREATION_SOURCES.get(new EventKey(object,null));if(sources!=null)return sources.toArray(Module[]::new);
            Module single=CREATION_OWNERS.get(new EventKey(object,null));return single==null?new Module[0]:new Module[]{single};
        }
    }
    public static void registerDefinitionCreationSites(Class<?> actual,String[] sites){
        if(!ControlBridge.owns(CREATION_CALLER.getCallerClass()))throw new SecurityException("ACTUAL_DEFINITION_AGENT_REQUIRED");
        if(actual==null||!actual.isHidden())throw new IllegalArgumentException("ACTUAL_HIDDEN_DEFINITION_REQUIRED");
        CreationDefinition replacement=null;
        synchronized(CREATIONS){
            CREATIONS.removeIf(definition->!definition.bootstrap&&definition.loader.get()==null||definition.actual!=null&&(definition.actual.get()==null||definition.actual.get()==actual));
            if(sites.length!=0){
                if(CREATIONS.size()>=65536){lost();return;}
                replacement=new CreationDefinition(new java.lang.ref.WeakReference<>(actual.getClassLoader()),actual.getClassLoader()==null,actual.getName(),Set.of(sites),new ControlRef(actual));
                CREATIONS.add(replacement);
            }
            CREATION_SITES.remove(actual);
        }
        if(replacement!=null)controlPublication(CREATIONS,new Object[]{replacement,replacement.loader,replacement.sites,replacement.actual,CREATION_SITES});
    }
    public static String[] definedTaskMethods(){
        if(!ControlBridge.owns(CREATION_CALLER.getCallerClass()))throw new SecurityException("ACTUAL_DEFINITION_AGENT_REQUIRED");
        DefinedTaskContracts.publish();return DefinedTaskContracts.selectors();
    }
    public static Module[] objectSources(Object object){
        if(object==null)return new Module[0];
        EventKey key=new EventKey(object,null);
        synchronized(CREATION_OWNERS){
            ThreadOrigin thread=THREAD_ORIGINS.get(key);
            if(thread!=null){
                Set<Module> sources=Collections.newSetFromMap(new IdentityHashMap<>());sources.addAll(thread.sources);
                Set<Module> creation=CREATION_SOURCES.get(key),tasks=TASK_OWNERS.get(key);
                if(creation!=null)sources.addAll(creation);if(tasks!=null)sources.addAll(tasks);return sources.toArray(Module[]::new);
            }
            Set<Module> tasks=TASK_OWNERS.get(key);if(tasks!=null)return tasks.toArray(Module[]::new);
            Set<Module> sources=CREATION_SOURCES.get(key);if(sources!=null)return sources.toArray(Module[]::new);
        }
        Module module=objectModule(object,key);return module==null?new Module[0]:new Module[]{module};
    }
    public static boolean objectSourceUnknown(Object object){synchronized(CREATION_OWNERS){
        EventKey key=new EventKey(object,null);ThreadOrigin thread=THREAD_ORIGINS.get(key);
        return CREATION_UNKNOWN.contains(key)||thread!=null&&thread.unknown;
    }}
    static String creationSourceGap(Module module){
        if(WALKER.getCallerClass()!=NativeControl.class)throw new SecurityException("ACTUAL_NATIVE_SOURCE_STATE_REQUIRED");
        Set<Object> unresolved=Collections.newSetFromMap(new IdentityHashMap<>());
        synchronized(CREATION_OWNERS){reapCreationOwners();for(EventKey key:CREATION_UNKNOWN){
            Object actual=key.get();Set<Module> sources=CREATION_SOURCES.get(key);if(actual!=null&&sources!=null&&sources.contains(module))unresolved.add(actual);
        }
            for(var entry:THREAD_ORIGINS.entrySet())if(entry.getValue().unknown&&entry.getValue().sources.contains(module)){
                Object actual=entry.getKey().get();if(actual!=null)unresolved.add(actual);
            }
        }return unresolved.isEmpty()?"":"EXTERNAL_OBJECT_SOURCE_UNOBSERVED:objects="+unresolved.size();
    }
    private static void createdBy(Object object,Module[] sources,boolean onlyAbsent){
        EventKey lookup=new EventKey(object,null);
        if(CodeSourceBridge.executionUnknown()){CREATION_UNKNOWN.add(new EventKey(object,CREATION_QUEUE));CREATION_OWNERS.remove(lookup);}
        if(onlyAbsent&&(CREATION_OWNERS.containsKey(lookup)||CREATION_SOURCES.containsKey(lookup)))return;
        EventKey key=new EventKey(object,CREATION_QUEUE);CREATION_OWNERS.remove(lookup);
        Set<Module> actual=java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<Module,Boolean>());actual.addAll(java.util.Arrays.asList(sources));actual.remove(null);
        if(actual.size()==1&&!CREATION_UNKNOWN.contains(key))CREATION_OWNERS.put(key,actual.iterator().next());
        CREATION_SOURCES.put(key,actual);
        if(object instanceof java.util.concurrent.ExecutorService)schedulerSources(object,actual,CREATION_UNKNOWN.contains(key));
        if(object.getClass()==java.util.Timer.class)timerThread(object);
    }
    static void lookupDerived(java.lang.invoke.MethodHandles.Lookup source,java.lang.invoke.MethodHandles.Lookup result,Class<?> caller) {
        if(WALKER.getCallerClass()!=DefinitionBridge.class)throw new SecurityException("DEFINITION_BRIDGE_REQUIRED");
        if(result==source)return;
        Module module=creationModule(source);
        if(module==null&&caller!=null&&producer(DefinitionBridge.module(caller)))module=DefinitionBridge.module(caller);
        if(module==null)module=DefinitionBridge.module(source.lookupClass());
        if(!producer(module))return;
        synchronized(CREATION_OWNERS){reapCreationOwners();createdBy(result,new Module[]{module},true);}
    }
    public static Module objectModule(Object object) {
        return objectModule(object,new EventKey(object,null));
    }
    private static Module objectModule(Object object,EventKey key) {
        synchronized(CREATION_OWNERS) {
            if(CREATION_UNKNOWN.contains(key))return null;
            ThreadOrigin thread=THREAD_ORIGINS.get(key);
            if(thread!=null){if(thread.unknown)return null;return singleThreadSource(key,thread);}
            Set<Module> owners=TASK_OWNERS.get(key);
            if(owners!=null) {if(owners.size()!=1)return null;return owners.iterator().next();}
            Set<Module> sources=CREATION_SOURCES.get(key);if(sources!=null){if(sources.size()!=1)return null;return sources.iterator().next();}
        }
        if(object!=null){Module definition=DefinitionBridge.module(object instanceof Class<?> type?type:object.getClass());if(producer(definition))return definition;}
        // Reuse only the identity key. The fallback still reads the actual
        // current registration after the declaration query, under its monitor.
        Module origin=null;if(object!=null)synchronized(CREATION_OWNERS){if(!CREATION_UNKNOWN.contains(key))origin=CREATION_OWNERS.get(key);}
        return origin!=null?origin:object instanceof Class<?> type?DefinitionBridge.module(type):object==null?null:DefinitionBridge.module(object.getClass());
    }
    private static Module singleThreadSource(EventKey key,ThreadOrigin thread){
        // CREATION_OWNERS is held. The caller only consumes a singleton, so
        // compare all three actual sets without constructing a union and array.
        Set<Module> creation=CREATION_SOURCES.get(key),tasks=TASK_OWNERS.get(key);Module selected=null;
        for(int i=0;i<3;i++){
            Set<Module> sources=i==0?thread.sources:i==1?creation:tasks;if(sources==null)continue;
            for(Module source:sources){if(source==null)return null;if(selected==null)selected=source;else if(selected!=source)return null;}
        }
        return selected;
    }
    public static Object[] createdGroupObjects(Module[] modules) {
        Guards current=guards;Class<?> caller=WALKER.getCallerClass();
        if(current==null||DefinitionBridge.module(caller)!=current.owner().getModule()
                ||caller.getClassLoader()!=current.owner().getClassLoader())throw new SecurityException("GROUP_CORE_REQUIRED");
        var selected=java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<Module,Boolean>());
        selected.addAll(java.util.Arrays.asList(modules));Set<Object> found=Collections.newSetFromMap(new IdentityHashMap<>());
        synchronized(CREATION_OWNERS) {
            reapCreationOwners();
            for(var entry:CREATION_OWNERS.entrySet())if(selected.contains(entry.getValue())) {
                Object value=entry.getKey().get();if(value!=null)found.add(value);
            }
            for(var entry:CREATION_SOURCES.entrySet())if(entry.getValue().size()>1&&!CREATION_UNKNOWN.contains(entry.getKey())&&selected.containsAll(entry.getValue())){
                Object value=entry.getKey().get();if(value!=null)found.add(value);
            }
            for(var entry:THREAD_ORIGINS.entrySet())if(!entry.getValue().unknown&&!entry.getValue().sources.isEmpty()&&selected.containsAll(entry.getValue().sources)){
                Object value=entry.getKey().get();if(value!=null)found.add(value);
            }
        }
        return found.toArray();
    }
    public static void creationObserved(Object object) {
        deliverCreation(object,false);
    }
    static void resourceCreated(Object object){
        if(WALKER.getCallerClass()!=ResourceBridge.class)throw new SecurityException("ACTUAL_RESOURCE_CONSTRUCTOR_REQUIRED");
        resourceCreatedFrom(object,resourceSources(null));
    }
    static void resourceCreated(Object object,Module[] operationSources){
        Class<?> caller=WALKER.getCallerClass();if(caller!=ResourceBridge.class&&caller!=IoBridge.class)throw new SecurityException("ACTUAL_RESOURCE_CONSTRUCTOR_REQUIRED");
        resourceCreatedFrom(object,resourceSources(operationSources));
    }
    private static void resourceCreatedFrom(Object object,Module[] sources){
        if(object==null||sources.length==0)return;
        synchronized(CREATION_OWNERS){reapCreationOwners();createdBy(object,sources,true);}
        CreationObserver observer=creationObserver;if(observer==null||CREATION_DELIVERY.get()!=null)return;
        CREATION_DELIVERY.set(observer.owner);
        try{observer.callback.invokeExact(object);}catch(Throwable failed){lost();}finally{CREATION_DELIVERY.remove();}
    }
    static boolean resourceExposed(Object object){
        if(WALKER.getCallerClass()!=ResourceBridge.class)throw new SecurityException("ACTUAL_RESOURCE_ACCESSOR_REQUIRED");
        return resourceExposedFrom(object,resourceSources(null));
    }
    static boolean resourceExposed(Object object,Module[] nativeSources){
        Class<?> caller=WALKER.getCallerClass();if(caller!=ResourceBridge.class&&caller!=IoBridge.class)throw new SecurityException("ACTUAL_RESOURCE_ACCESSOR_REQUIRED");
        return resourceExposedFrom(object,resourceSources(nativeSources));
    }
    static Module[] resourceOperationSources(Module[] nativeSources){
        Class<?> caller=WALKER.getCallerClass();if(caller!=ResourceBridge.class&&caller!=IoBridge.class)throw new SecurityException("ACTUAL_RESOURCE_OPERATION_REQUIRED");
        return resourceSources(nativeSources);
    }
    static boolean resourceExposedByOperation(Object object,Module[] sources){
        Class<?> caller=WALKER.getCallerClass();if(caller!=ResourceBridge.class&&caller!=IoBridge.class)throw new SecurityException("ACTUAL_RESOURCE_OPERATION_REQUIRED");
        return resourceExposedFrom(object,sources);
    }
    private static Module[] resourceSources(Module[] nativeSources){
        // Keep the one current-scope union instead of materializing it and
        // inserting it into a second map. Io sources are already included in
        // full by scopedSourceSet; filtering and adding them again adds nothing.
        Set<Module> sources=scopedSourceSet();
        if(sources==null)sources=addModuleSources(null,ORIGIN_WALKER.walk(INVOKING_SOURCES),false);
        sources=addModuleSources(sources,nativeSources,true);
        if(CodeSourceBridge.executionUnknown()){
            if(sources==null)sources=Collections.newSetFromMap(new IdentityHashMap<>());sources.add(null);
        }
        return sources==null?NO_SOURCES:sources.toArray(Module[]::new);
    }
    private static boolean resourceExposedFrom(Object object,Module[] sources){
        if(sources.length==0)return false;
        synchronized(CREATION_OWNERS){
            reapCreationOwners();Set<Module> merged=java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
            merged.addAll(java.util.Arrays.asList(objectSources(object)));merged.addAll(java.util.Arrays.asList(sources));createdBy(object,merged.toArray(Module[]::new),false);
        }
        return true;
    }
    public static void creationFailed(Throwable error) { deliverCreation(error,true); }
    public static void nativeCreated(Class<?> declaring,Object object){
        if(object==null||!NativeControl.createdObject(declaring,object))throw new SecurityException("ACTUAL_NATIVE_CREATION_REQUIRED");
        Module[] sources=invokingSources();if(sources.length==0){Module module=DefinitionBridge.module(declaring);if(!producer(module))return;sources=new Module[]{module};}
        synchronized(CREATION_OWNERS){reapCreationOwners();createdBy(object,sources,true);}
        CreationObserver observer=creationObserver;if(observer==null||CREATION_DELIVERY.get()!=null)return;
        CREATION_DELIVERY.set(observer.owner);
        try{observer.callback.invokeExact(object);}catch(Throwable failure){lost();}finally{CREATION_DELIVERY.remove();}
    }
    private static void deliverCreation(Object object,boolean failed) {
        CreationObserver observer=creationObserver;
        if(object==null||CREATION_DELIVERY.get()!=null)return;
        var caller=CREATION_CALLER.walk(frames->frames.skip(2).findFirst().orElse(null));
        if(caller==null||!CREATION_SITES.get(caller.getDeclaringClass()).contains(caller.getMethodName()+caller.getDescriptor()+":"+caller.getByteCodeIndex()))return;
        Module[] sources=CodeSourceBridge.frameSources(caller);if(sources.length==0)sources=new Module[]{DefinitionBridge.module(caller.getDeclaringClass())};
        if(!failed) synchronized(CREATION_OWNERS) {
            reapCreationOwners();
            createdBy(object,sources,false);
            if(object instanceof java.util.concurrent.ExecutorService) {
                Object delegate=schedulerDelegate(object);
                if(delegate!=null&&delegate!=object)createdBy(delegate,sources,true);
            }
        }
        if(observer==null)return;
        if(!failed&&PRODUCERS.get()!=null) {
            var frame=PRODUCERS.get();if(frame.created.size()<64)frame.created.add(object);else frame.overflow=true;
        }
        CREATION_DELIVERY.set(observer.owner);
        try { if(failed)observer.failure.invokeExact(object);else observer.callback.invokeExact(object); }
        catch(Throwable failure) { lost(); }
        finally { CREATION_DELIVERY.remove(); }
    }
    // Each policy generation is published as one immutable object. A reader captures it once,
    // so callback handles and Unsafe field coordinates always belong to the same generation.
    private record UnsafeField(Class<?> declaring,String name,long offset,Class<?> type) {}
    private record Guards(Class<?> owner,java.lang.invoke.MethodHandle field,
                          java.lang.invoke.MethodHandle dispatch,java.lang.invoke.MethodHandle task,
                          java.util.List<UnsafeField> unsafeFields,java.lang.invoke.MethodHandle index) {}
    private static final class ControlRef extends java.lang.ref.WeakReference<Object> {
        ControlRef(Object value){super(value);}
        @Override public void clear(){if(controlCaller())super.clear();}
        @Override public boolean enqueue(){return controlCaller()&&super.enqueue();}
    }
    private static volatile ControlRef[] controlObjects=new ControlRef[0];
    private static volatile boolean controlCaptureHealthy=true;
    public static boolean controlCaptureHealthy(){return controlCaptureHealthy;}
    private static final long[] controlBits=new long[1024];
    private static final ThreadLocal<Boolean> OWN_LEDGER_MUTATION=new ThreadLocal<>();
    private static final Set<String> EXTERNAL_GUARD_ENTRIES=Set.of(
            "controlCaller","controlled","criticalEntry","referenceRetirementAllowed","controlMutationAllowed","inheritControl","controlBackingPublished",
            "fieldWriteAllowed","staticFieldWriteAllowed","reflectFieldWrite",
            "beginNativeUnsafeMutation","beginExecutedUnsafeMutation","beginNativeUnsafeCopy","beginNativeFieldMutation","beginNativeArrayMutation",
            "indexDenied","indexRemovalAllowed","indexWriteAllowed","beginIndexWrite","beginIndexNodeWrite","beginIndexArrayWrite","beginIndexRemoval","beginIndexNodeRemoval","beginIndexArrayRemoval","beginIndexStructure","endIndexMutation","deniedIndexCurrent",
            "registerConsoleInput","beginConsoleCommand","endConsoleCommand","consoleDispatchTrusted",
            "clearProtectedMap","unsafeWriteAllowed","unsafeMemoryAllowed",
            "nativeArrayElementAllowed","nativePrimitiveArrayPolicy","nativeArrayPointerAllowed","nativeRegistrationAllowed","nativeRegistrationTargetAllowed","arrayWriteAllowed",
            "resourceReleaseAllowed","lifeResult","dispatchAllowed","taskEffectAllowed",
            "resultPublicationAllowed");
    static boolean controlCaller() {
        // The query omits guard entry points, preserving the real ledger writer.
        Class<?> caller=WALKER.walk(CONTROL_WRITER_CALLER);
        if(caller==null)return false;
        if(caller.getNestHost()==TaskBridge.class&&caller.getModule()==TaskBridge.class.getModule())return true;
        if(caller==policyInstaller||caller==BackingBridge.class)return true;
        if(caller==SourceMapBridge.class||caller==NativeControl.class||caller==ControlBridge.class||caller.getNestHost()==ExecutionFlow.class)return true;
        if((caller.getNestHost()==DefinitionBridge.class||caller.getNestHost()==ResourceBridge.class||caller.getNestHost()==IoBridge.class||caller.getNestHost()==CodeSourceBridge.class||caller.getNestHost()==NetworkBridge.class||caller.getNestHost()==ClientBridge.class)
                &&caller.getModule()==DefinitionBridge.class.getModule())return true;
        Guards current=guards;
        if(current!=null&&DefinitionBridge.module(caller)==current.owner().getModule()&&caller.getClassLoader()==current.owner().getClassLoader()&&caller.getName().startsWith("dev.ronova.pro."))return true;
        if(caller.getClassLoader()==ClassLoader.getSystemClassLoader()&&caller.getName().startsWith("dev.ronova.pro.agent."))try {
            Class<?> agent=Class.forName("dev.ronova.pro.agent.RecoveryAgent",false,ClassLoader.getSystemClassLoader());
            return Objects.equals(caller.getProtectionDomain().getCodeSource(),agent.getProtectionDomain().getCodeSource());
        }catch(ClassNotFoundException missing){return false;}
        return false;
    }
    static boolean controlled(Object value) {
        if(value==null)return false;if(fieldGateControl(value)||SourceMapBridge.controlled(value)||DefinitionBridge.controlled(value)||CodeSourceBridge.controlled(value)||NetworkBridge.controlled(value)||ClientBridge.controlled(value)||criticalEntry(value))return true;ControlRef[] controls=controlObjects;
        if(value==controls||value==controlBits)return true;
        int hash=System.identityHashCode(value)&65535;
        if((controlBits[hash>>>6]&(1L<<(hash&63)))==0)return false;
        for(ControlRef control:controls)if(control.get()==value)return true;return false;
    }
    private static boolean criticalEntry(Object value){
        if(value==null||value.getClass().getClassLoader()!=null||!(value instanceof java.util.Map.Entry<?,?> entry))return false;
        Object key=entry.getKey();return key!=null&&ownState(key.getClass());
    }
    public static boolean referenceRetirementAllowed(Object reference){
        return reference==null||!ownState(reference.getClass())||controlCaller();
    }
    public static boolean controlMutationAllowed(Object value) {
        if(CodeSourceBridge.fieldGateMetadata(value))
            return ORIGIN_WALKER.walk(FIELD_GATE_WRITER)||controlCaller()||NativeControl.libraryImplementationCaller();
        if(!controlled(value))return true;
        if(SourceMapBridge.internal())return true;
        if((value==ADMISSIONS||value==SUBMISSIONS||value==HELD_TASKS||value==HELD_ARRAYS)
                &&OWN_LEDGER_MUTATION.get()!=null)return true;
        return controlCaller()||NativeControl.libraryImplementationCaller();
    }
    private static void inheritControl(Object parent,Object next) {
        if(next==null||!controlled(parent)||controlled(next)||!(controlCaller()||NativeControl.libraryImplementationCaller()))return;
        if(!(next.getClass().isArray()||next instanceof java.util.Map<?,?>||next instanceof java.util.Collection<?>))return;
        CodeSourceBridge.controlBacking(parent,next);
    }
    /** Called immediately before an actual JDK container publishes its new backing array. */
    public static void controlBackingPublished(Object parent,Object next) {
        if(!controlled(parent))return;
        Class<?> caller=WALKER.getCallerClass();
        if(caller!=java.util.HashMap.class&&caller!=java.util.ArrayList.class&&caller!=java.util.IdentityHashMap.class)
            throw new SecurityException("CONTROL_BACKING_PUBLICATION_OWNER");
        if(!controlCaller()&&!NativeControl.libraryImplementationCaller())throw new SecurityException("CONTROL_BACKING_PUBLICATION_REFUSED");
        if(next!=null)CodeSourceBridge.controlBacking(parent,next);
    }
    public static void producerReceiver(Object receiver) {
        if(producerSite())producerReference(receiver,false);
    }
    public static void producerArgument(Object value) {
        if(producerSite())producerReference(value,true);
    }
    /** Run at a real factory entry, before its first business instruction. */
    public static boolean producerExecutionAllowed() {
        ProducerFrame frame=PRODUCERS.get();
        return frame==null||!producerSite()||taskEffectAllowed(guards,frame,"producer");
    }
    private static void producerReference(Object receiver,boolean argument) {
        ProducerFrame frame=PRODUCERS.get();var observer=producerObserver;
        if(frame==null||observer==null||receiver==null||PRODUCER_DELIVERY.get()!=null)return;
        PRODUCER_DELIVERY.set(guards.owner());PRODUCER_METHOD_RECEIVER.set(true);
        try {
            Object origin=(Object)observer.invokeExact(receiver,receiver);
            if(origin!=null) {if(frame.inputs.size()<64)frame.inputs.add(origin);else frame.overflow=true;}
            else if(argument)frame.overflow=true; // An opaque extra input cannot be attributed to one source.
        }catch(Throwable unavailable){frame.overflow=true;lost();}
        finally {PRODUCER_METHOD_RECEIVER.remove();PRODUCER_DELIVERY.remove();}
    }
    public static synchronized void registerControlObjects(Class<?> owner,Object[] roots) {
        requirePolicyInstaller();if(guards==null||guards.owner()!=owner)throw new SecurityException("CONTROL_OWNER");
        try {captureControls(roots);}
        catch(RuntimeException|Error failure){controlCaptureHealthy=false;throw failure;}
    }
    private static final java.util.Map<Object,Boolean> TRUSTED_CONSOLE_INPUTS=
            java.util.Collections.synchronizedMap(new java.util.WeakHashMap<>());
    private static final ThreadLocal<Boolean> CONSOLE_COMMAND=new ThreadLocal<>();
    /** Mark the exact input only when the dedicated server's console reader produced it. */
    public static void registerConsoleInput(Object input) {
        if(input==null)return;
        boolean authentic=WALKER.walk(frames->{
            var stack=frames.dropWhile(frame->frame.getDeclaringClass()==TaskBridge.class).limit(2).toList();
            if(stack.size()!=2)return false;
            var caller=stack.get(0);var source=stack.get(1);
            if(!caller.getDeclaringClass().getName().equals("net.minecraft.server.dedicated.DedicatedServer")
                    ||!(caller.getMethodName().equals("handleConsoleInput")||caller.getMethodName().equals("m_139645_")))return false;
            if(source.getDeclaringClass().getName().equals("net.minecraft.server.dedicated.DedicatedServer$1")
                    &&source.getMethodName().equals("run"))return true;
            return Boolean.getBoolean("ronova.pro.validation")
                    &&source.getDeclaringClass().getName().equals("dev.ronova.pro.validation.FixtureCommands")
                    &&source.getMethodName().equals("execute");
        });
        if(authentic)TRUSTED_CONSOLE_INPUTS.put(input,Boolean.TRUE);
    }
    public static void beginConsoleCommand(Object input) {
        if(!WALKER.getCallerClass().getName().equals("net.minecraft.server.dedicated.DedicatedServer"))
            throw new SecurityException("CONSOLE_DISPATCH_OWNER");
        if(Boolean.TRUE.equals(TRUSTED_CONSOLE_INPUTS.remove(input)))CONSOLE_COMMAND.set(Boolean.TRUE);
        else CONSOLE_COMMAND.remove();
    }
    public static void endConsoleCommand() {
        if(!WALKER.getCallerClass().getName().equals("net.minecraft.server.dedicated.DedicatedServer"))
            throw new SecurityException("CONSOLE_DISPATCH_OWNER");
        CONSOLE_COMMAND.remove();
    }
    public static boolean consoleDispatchTrusted() {
        return CONSOLE_COMMAND.get()!=null;
    }
    public static void registerAgentControls(Object[] roots) {
        if(!ControlBridge.owns(WALKER.getCallerClass()))throw new SecurityException("AGENT_CONTROL_OWNER");
        try {CodeSourceBridge.agentControls(roots);}
        catch(RuntimeException|Error failure){controlCaptureHealthy=false;throw failure;}
    }
    static synchronized void controlPublication(Object array,Object[] entries) {
        Object[] roots=new Object[entries.length+1];roots[0]=array;System.arraycopy(entries,0,roots,1,entries.length);
        try {captureControls(roots);}
        catch(RuntimeException|Error failure){controlCaptureHealthy=false;throw failure;}
    }
    private static void captureControls(Object[] roots) {
        java.util.Set<Object> found=java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
        java.util.ArrayDeque<Object> queue=new java.util.ArrayDeque<>();
        for(ControlRef old:controlObjects){Object value=old.get();if(value!=null)queue.add(value);}for(Object root:roots)if(root!=null)queue.add(root);
        for(Class<?> type:java.util.List.of(TaskBridge.class,SourceMapBridge.class,BackingBridge.class,DefinitionBridge.class,ResourceBridge.class,IoBridge.class,CodeSourceBridge.class))for(var field:type.getDeclaredFields())
            if(java.lang.reflect.Modifier.isStatic(field.getModifiers())&&(java.util.Map.class.isAssignableFrom(field.getType())||java.util.Collection.class.isAssignableFrom(field.getType()))&&(NativeControl.available()||field.trySetAccessible()))
                {Object value=CodeSourceBridge.controlField(field,null);if(value!=null)queue.add(value);}
        // The authenticated controller's real image/source records can exceed
        // a fixed object count. Visit each actual container once and retain the
        // full set instead of leaving the remaining control images unguarded.
        while(!queue.isEmpty()) {
            Object value=queue.removeFirst();if(!found.add(value)||value.getClass().isArray())continue;
            if(!(value instanceof java.util.Map<?,?>||value instanceof java.util.Collection<?>))continue;
            for(Class<?> type=value.getClass();type!=null&&type!=Object.class;type=type.getSuperclass())for(var field:type.getDeclaredFields())
                if(!java.lang.reflect.Modifier.isStatic(field.getModifiers())&&(field.getType().isArray()||java.util.Map.class.isAssignableFrom(field.getType())||java.util.Collection.class.isAssignableFrom(field.getType()))&&(NativeControl.available()||field.trySetAccessible()))
                    {Object child=CodeSourceBridge.controlField(field,value);if(child!=null)queue.add(child);}
        }
        Object[] published=found.toArray();
        for(Object value:published){int hash=System.identityHashCode(value)&65535;controlBits[hash>>>6]|=1L<<(hash&63);}
        ControlRef[] weak=new ControlRef[published.length];for(int i=0;i<published.length;i++)weak[i]=new ControlRef(published[i]);controlObjects=weak;
    }
    private static Class<?> policyInstaller,observerInstaller;
    private static Class<?> installingCaller() {
        Class<?> caller=WALKER.walk(MEMORY_CALLER);return (caller==null?TaskBridge.class:caller).getNestHost();
    }
    private static void requirePolicyInstaller() {
        Class<?> caller=installingCaller();
        if(policyInstaller==null) {
            if(!caller.getName().equals("dev.ronova.pro.ProRuntime"))throw new SecurityException("POLICY_INSTALLER_NOT_CORE");
            policyInstaller=caller;return;
        }
        if(caller==policyInstaller)return;
        Guards current=guards;
        if(current!=null&&current.owner()==policyInstaller&&caller.getName().equals("dev.ronova.pro.BackingPolicy")
                &&caller.getClassLoader()==policyInstaller.getClassLoader()&&caller.getModule()==policyInstaller.getModule())return;
        throw new SecurityException("POLICY_INSTALLER_IDENTITY_CHANGED");
    }
    private static volatile Guards guards;
    private static Guards policy(Class<?> owner,java.lang.invoke.MethodHandle field,
            java.lang.invoke.MethodHandle dispatch,java.lang.invoke.MethodHandle task,
            java.util.List<UnsafeField> fields) {
        Objects.requireNonNull(owner);
        return new Guards(owner,
            Objects.requireNonNull(field).asType(java.lang.invoke.MethodType.methodType(boolean.class,Object.class,Class.class,String.class,Object.class)),
            Objects.requireNonNull(dispatch).asType(java.lang.invoke.MethodType.methodType(boolean.class,Object.class,String.class,Object.class)),
            Objects.requireNonNull(task).asType(java.lang.invoke.MethodType.methodType(boolean.class,Object.class,String.class)),
            java.util.List.copyOf(fields),null);
    }
    /** Legacy setter: atomically starts a fresh callback generation with no stale field map. */
    public static synchronized void installGuards(Class<?> owner, java.lang.invoke.MethodHandle field,
            java.lang.invoke.MethodHandle dispatch, java.lang.invoke.MethodHandle task) {
        requirePolicyInstaller();
        Guards current=guards;
        if(current!=null&&current.owner()!=owner)throw new IllegalStateException("OTHER_CORE_GUARD_OWNER");
        guards=policy(owner,field,dispatch,task,java.util.List.of());
    }
    private static java.util.List<UnsafeField> fields(java.lang.reflect.Field[] fields,long[] offsets) {
        Objects.requireNonNull(fields);Objects.requireNonNull(offsets);
        if(fields.length!=offsets.length)throw new IllegalArgumentException("UNSAFE_FIELD_LENGTH");
        var result=new java.util.ArrayList<UnsafeField>();
        var seen=new java.util.HashMap<Class<?>,java.util.Set<Long>>();
        for(int i=0;i<fields.length;i++) {
            var field=Objects.requireNonNull(fields[i]);
            if(java.lang.reflect.Modifier.isStatic(field.getModifiers())||offsets[i]<0
                    ||!seen.computeIfAbsent(field.getDeclaringClass(),key->new java.util.HashSet<>()).add(offsets[i]))
                throw new IllegalArgumentException("UNSAFE_FIELD_MAPPING");
            result.add(new UnsafeField(field.getDeclaringClass(),field.getName(),offsets[i],field.getType()));
        }
        return java.util.List.copyOf(result);
    }
    /** Legacy setter: replacement is validated before the single snapshot publication. */
    public static synchronized void installUnsafeFields(Class<?> owner,java.lang.reflect.Field[] fields,long[] offsets) {
        requirePolicyInstaller();
        Guards current=guards;
        if(current==null||owner!=current.owner())throw new IllegalArgumentException("UNSAFE_FIELD_OWNER");
        var next=fields(fields,offsets);
        Guards replacement=policy(owner,current.field(),current.dispatch(),current.task(),next);
        guards=new Guards(replacement.owner(),replacement.field(),replacement.dispatch(),replacement.task(),replacement.unsafeFields(),current.index());
    }
    /** Atomic installation path for callers that can provide callbacks and mappings together. */
    public static synchronized void installPolicy(Class<?> owner,java.lang.invoke.MethodHandle field,
            java.lang.invoke.MethodHandle dispatch,java.lang.invoke.MethodHandle task,
            java.lang.reflect.Field[] fields,long[] offsets) {
        requirePolicyInstaller();
        Guards current=guards;
        if(current!=null&&current.owner()!=owner)throw new IllegalStateException("OTHER_CORE_GUARD_OWNER");
        guards=policy(owner,field,dispatch,task,fields(fields,offsets));
    }
    /** All active callbacks, index provenance and Unsafe mappings publish in one snapshot. */
    public static synchronized void installPolicy(Class<?> owner,java.lang.invoke.MethodHandle field,
            java.lang.invoke.MethodHandle dispatch,java.lang.invoke.MethodHandle task,
            java.lang.reflect.Field[] fields,long[] offsets,java.lang.invoke.MethodHandle index) {
        requirePolicyInstaller();
        Guards current=guards;
        if(current!=null&&current.owner()!=owner)throw new IllegalStateException("OTHER_CORE_GUARD_OWNER");
        Guards next=policy(owner,field,dispatch,task,fields(fields,offsets));
        var callback=Objects.requireNonNull(index).asType(java.lang.invoke.MethodType.methodType(boolean.class,Object.class,Object.class,Object.class));
        guards=new Guards(next.owner(),next.field(),next.dispatch(),next.task(),next.unsafeFields(),callback);
    }
    private static final ThreadLocal<Boolean> INDEX_QUERY=new ThreadLocal<>();
    private static boolean indexDenied(Guards snapshot,Object map,Object key,Object value) {
        if(snapshot==null||snapshot.index()==null||map==null||INDEX_QUERY.get()!=null||SourceMapBridge.internal())return false;
        INDEX_QUERY.set(Boolean.TRUE);
        try { return (boolean)snapshot.index().invokeExact(map,key,value); }
        catch(Throwable failure) { throw new IllegalStateException("INDEX_POLICY_FAILED",failure); }
        finally { INDEX_QUERY.remove(); }
    }
    /** Exact resolved entry at the container writer. Bookkeeping reentry performs no world mutation. */
    public static boolean indexRemovalAllowed(Object map,Object key,Object value) {
        return controlMutationAllowed(map)&&!indexDenied(guards,map,key,value);
    }
    public static boolean indexWriteAllowed(Object map,Object key,Object current,Object proposed) {
        return current==proposed||controlMutationAllowed(map)&&!indexDenied(guards,map,key,current)&&SourceMapBridge.valueAllowed(map,key,proposed);
    }
    /** Hold the receiver's short gate from the final index decision through its actual value store. */
    public static Object beginIndexWrite(Object map,Object key,Object current,Object proposed) {
        // Constructor-registered ledgers already hold their own monitor. Keep
        // the writer check without nesting a business gate under that monitor.
        if(CodeSourceBridge.executionMap(map))return controlMutationAllowed(map)?null:Boolean.FALSE;
        java.util.concurrent.locks.ReentrantLock gate=fieldGate(map);
        gate.lock();
        Object token=indexMutation(map,gate);
        boolean allowed;
        try { allowed=indexWriteAllowed(map,key,current,proposed); }
        catch(RuntimeException|Error failure) { endIndexMutation(token);throw failure; }
        if(!allowed) { endIndexMutation(token);return Boolean.FALSE; }
        return token;
    }
    public static Object beginIndexNodeWrite(Object map,Object node,Object proposed) {
        if(CodeSourceBridge.executionMap(map))return controlMutationAllowed(map)?null:Boolean.FALSE;
        Object owner=SourceMapBridge.ownerOf(node);
        java.util.concurrent.locks.ReentrantLock gate=fieldGate(owner==null?map:owner);
        gate.lock();
        Object token=indexMutation(map,gate);
        boolean allowed;
        try {
            java.util.Map.Entry<?,?> entry=(java.util.Map.Entry<?,?>)node;
            allowed=(owner==null||controlMutationAllowed(owner))&&indexWriteAllowed(map,entry.getKey(),entry.getValue(),proposed);
        } catch(RuntimeException|Error failure) { endIndexMutation(token);throw failure; }
        if(!allowed) { endIndexMutation(token);return Boolean.FALSE; }
        return token;
    }
    public static Object beginIndexArrayWrite(Object map,Object key,Object[] values,int index,Object proposed) {
        if(CodeSourceBridge.executionMap(map))return controlMutationAllowed(map)?null:Boolean.FALSE;
        java.util.concurrent.locks.ReentrantLock gate=fieldGate(map);
        gate.lock();
        Object token=indexMutation(map,gate);
        boolean allowed;
        try { allowed=indexWriteAllowed(map,key,values[index],proposed); }
        catch(RuntimeException|Error failure) { endIndexMutation(token);throw failure; }
        if(!allowed) { endIndexMutation(token);return Boolean.FALSE; }
        return token;
    }
    public static Object beginIndexRemoval(Object map,Object key,Object current) {
        if(CodeSourceBridge.executionMap(map))return controlMutationAllowed(map)?null:Boolean.FALSE;
        java.util.concurrent.locks.ReentrantLock gate=fieldGate(map);
        gate.lock();
        boolean allowed;
        try { allowed=indexRemovalAllowed(map,key,current); }
        catch(RuntimeException|Error failure) { gate.unlock();throw failure; }
        if(!allowed) { gate.unlock();return Boolean.FALSE; }
        return indexMutation(map,gate);
    }
    public static Object beginIndexNodeRemoval(Object map,Object node) {
        if(CodeSourceBridge.executionMap(map))return controlMutationAllowed(map)?null:Boolean.FALSE;
        java.util.concurrent.locks.ReentrantLock gate=fieldGate(map);
        gate.lock();
        boolean allowed;
        try {
            java.util.Map.Entry<?,?> entry=(java.util.Map.Entry<?,?>)node;
            allowed=indexRemovalAllowed(map,entry.getKey(),entry.getValue());
        } catch(RuntimeException|Error failure) { gate.unlock();throw failure; }
        if(!allowed) { gate.unlock();return Boolean.FALSE; }
        return indexMutation(map,gate);
    }
    public static Object beginIndexArrayRemoval(Object map,Object key,Object[] values,int index) {
        if(CodeSourceBridge.executionMap(map))return controlMutationAllowed(map)?null:Boolean.FALSE;
        java.util.concurrent.locks.ReentrantLock gate=fieldGate(map);
        gate.lock();
        boolean allowed;
        try { allowed=indexRemovalAllowed(map,key,values[index]); }
        catch(RuntimeException|Error failure) { gate.unlock();throw failure; }
        if(!allowed) { gate.unlock();return Boolean.FALSE; }
        return indexMutation(map,gate);
    }
    public static void endIndexMutation(Object token) {
        if(token instanceof SourceIndexMutation mutation){
            if(mutation.thread!=Thread.currentThread())throw new IllegalMonitorStateException("SOURCE_INDEX_WRITER_THREAD_REQUIRED");
            if(mutation.closed)return;
            boolean outer=true;
            synchronized(SOURCE_INDEX_MUTATIONS){
                if(SOURCE_INDEX_MUTATIONS.get(mutation.thread)!=mutation)throw new IllegalMonitorStateException("SOURCE_INDEX_SCOPE_ORDER_REQUIRED");
                for(SourceIndexMutation parent=mutation.previous;parent!=null;parent=parent.previous)if(parent.map==mutation.map){outer=false;break;}
                if(mutation.previous==null)SOURCE_INDEX_MUTATIONS.remove(mutation.thread);else SOURCE_INDEX_MUTATIONS.put(mutation.thread,mutation.previous);
            }
            try{if(outer)SourceMapBridge.holderChanged(mutation.map);}
            finally{try{mutation.publication.close();}finally{mutation.closed=true;mutation.gate.unlock();}}
            return;
        }
        if(token instanceof java.util.concurrent.locks.ReentrantLock gate)gate.unlock();
    }
    private static final class SourceIndexMutation {
        final Object map;final java.util.concurrent.locks.ReentrantLock gate;final Thread thread=Thread.currentThread();boolean closed;
        final SourceIndexMutation previous;final SourcePublication publication;
        SourceIndexMutation(Object map,java.util.concurrent.locks.ReentrantLock gate){
            this.map=map;this.gate=gate;publication=new SourcePublication(map);
            synchronized(SOURCE_INDEX_MUTATIONS){previous=SOURCE_INDEX_MUTATIONS.put(thread,this);}
        }
    }
    private static final Map<Thread,SourceIndexMutation> SOURCE_INDEX_MUTATIONS=new IdentityHashMap<>();
    static boolean primitiveSourceIndex(Object map){
        if(map==null)return false;
        return switch(map.getClass().getName()){
            case "it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap",
                 "it.unimi.dsi.fastutil.ints.Int2ObjectLinkedOpenHashMap",
                 "it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap",
                 "it.unimi.dsi.fastutil.objects.ObjectArrayList",
                 "it.unimi.dsi.fastutil.objects.ObjectOpenHashSet" -> true;
            default -> false;
        };
    }
    private static final class FastListView {
        final Object view,root,mutation;final java.lang.reflect.Field extent;final int before,size;final Thread thread=Thread.currentThread();boolean closed;
        FastListView(Object view,java.lang.reflect.Field extent,int before,int size,Object root,Object mutation){this.view=view;this.extent=extent;this.before=before;this.size=size;this.root=root;this.mutation=mutation;}
    }
    public static Object beginFastListView(Object view){
        Class<?> writer=WALKER.getCallerClass();
        if(!writer.getName().equals("it.unimi.dsi.fastutil.objects.AbstractObjectList$ObjectSubList")||view==null||writer.getClassLoader()!=view.getClass().getClassLoader())throw new SecurityException("ACTUAL_FASTUTIL_VIEW_REQUIRED");
        try{
            var parent=writer.getDeclaredField("l");var extent=writer.getDeclaredField("to");parent.setAccessible(true);extent.setAccessible(true);
            Object root=view;Set<Object> seen=Collections.newSetFromMap(new IdentityHashMap<>());
            while(writer.isInstance(root)){if(!seen.add(root))return null;root=parent.get(root);}
            if(root==null||!root.getClass().getName().equals("it.unimi.dsi.fastutil.objects.ObjectArrayList")||root.getClass().getClassLoader()!=writer.getClassLoader())return null;
            var gate=fieldGate(root);gate.lock();Object mutation=null;
            try{mutation=indexMutation(root,gate);return new FastListView(view,extent,extent.getInt(view),((List<?>)root).size(),root,mutation);}
            catch(RuntimeException|Error|ReflectiveOperationException failed){if(mutation!=null)endIndexMutation(mutation);else gate.unlock();throw failed;}
        }catch(ReflectiveOperationException unavailable){throw new IllegalStateException("FASTUTIL_VIEW_LAYOUT",unavailable);}
    }
    public static Object beginFastListIterator(Object iterator){
        Class<?> caller=WALKER.getCallerClass();String prefix="it.unimi.dsi.fastutil.objects.";
        if(!Set.of(prefix+"ObjectArrayList$1",prefix+"ObjectIterators$AbstractIndexBasedListIterator").contains(caller.getName()))throw new SecurityException("ACTUAL_FASTUTIL_ITERATOR_REQUIRED");
        if(iterator==null||caller.getClassLoader()!=iterator.getClass().getClassLoader())return null;
        String actual=iterator.getClass().getName();
        if(!Set.of(prefix+"ObjectArrayList$1",prefix+"ObjectArrayList$SubList$SubListIterator",prefix+"AbstractObjectList$ObjectSubList$RandomAccessIter").contains(actual))return null;
        try{
            var holder=iterator.getClass().getDeclaredField(actual.endsWith("$SubListIterator")?"this$1":"this$0");holder.setAccessible(true);Object root=holder.get(iterator);
            Class<?> view=Class.forName(prefix+"AbstractObjectList$ObjectSubList",false,caller.getClassLoader());var parent=view.getDeclaredField("l");parent.setAccessible(true);
            Set<Object> seen=Collections.newSetFromMap(new IdentityHashMap<>());while(view.isInstance(root)){if(!seen.add(root))return null;root=parent.get(root);}
            if(root==null||!root.getClass().getName().equals(prefix+"ObjectArrayList")||root.getClass().getClassLoader()!=caller.getClassLoader())return null;
            var gate=fieldGate(root);gate.lock();Object mutation=null;
            try{mutation=indexMutation(root,gate);return new FastListView(iterator,null,0,0,root,mutation);}
            catch(RuntimeException|Error failed){if(mutation!=null)endIndexMutation(mutation);else gate.unlock();throw failed;}
        }catch(ReflectiveOperationException unavailable){throw new IllegalStateException("FASTUTIL_ITERATOR_LAYOUT",unavailable);}
    }
    public static boolean fastListIteratorIncoming(Object token,Object value){
        return !(token instanceof FastListView view)||BackingBridge.incomingAllowed(view.root,value);
    }
    public static int fastListViewExtent(Object token,int proposed){
        if(!(token instanceof FastListView view))return proposed;
        return view.before+((List<?>)view.root).size()-view.size;
    }
    public static void endFastListView(Object token){
        if(!(token instanceof FastListView view))return;
        if(view.thread!=Thread.currentThread())throw new IllegalMonitorStateException("FASTUTIL_VIEW_THREAD_REQUIRED");if(view.closed)return;view.closed=true;
        try{if(view.extent!=null)view.extent.setInt(view.view,fastListViewExtent(view,view.before));}
        catch(ReflectiveOperationException unavailable){throw new IllegalStateException("FASTUTIL_VIEW_EXTENT",unavailable);}
        finally{endIndexMutation(view.mutation);}
    }
    static boolean sourceIndexWriting(Object map){
        synchronized(SOURCE_INDEX_MUTATIONS){for(SourceIndexMutation scope=SOURCE_INDEX_MUTATIONS.get(Thread.currentThread());scope!=null;scope=scope.previous)if(scope.map==map)return true;}
        return false;
    }
    private static Object indexMutation(Object map,java.util.concurrent.locks.ReentrantLock gate){
        return primitiveSourceIndex(map)&&SourceMapBridge.carrierRegistered(map)?new SourceIndexMutation(map,gate):gate;
    }
    public static boolean withSourceIndex(Object map,Runnable action){
        if(!coreWriter()||!primitiveSourceIndex(map))throw new SecurityException("SOURCE_INDEX_CORE_REQUIRED");
        var gate=fieldGate(map);if(!gate.tryLock())return false;
        try{SourceMapBridge.registerCarrier(map);action.run();return true;}finally{gate.unlock();}
    }
    public static long sourceIndexRevision(Object map){
        if(!coreWriter()||!primitiveSourceIndex(map))throw new SecurityException("SOURCE_INDEX_CORE_REQUIRED");
        var gate=fieldGate(map);if(!gate.tryLock())return -1;
        try{return SourceMapBridge.indexRevision(map);}finally{gate.unlock();}
    }
    /** Primitive-key map writers contain no application callback in these exact methods. */
    public static Object beginIndexStructure(Object map) {
        Class<?> writer=WALKER.getCallerClass(),owner=map==null?null:map.getClass();
        if(owner==null||!owner.getName().startsWith("it.unimi.dsi.fastutil.")
                ||writer.getClassLoader()!=owner.getClassLoader()
                ||writer!=owner&&!writer.getName().equals(owner.getName()+"$MapIterator")&&!writer.getName().equals(owner.getName()+"$MapEntry")&&!writer.getName().equals(owner.getName()+"$SetIterator"))
            throw new SecurityException("INDEX_STRUCTURE_SCOPE_OWNER");
        java.util.concurrent.locks.ReentrantLock gate=fieldGate(map);
        gate.lock();
        return indexMutation(map,gate);
    }
    /** Only registered exact index maps and callback-free UUID/int keys can take this default-Map path. */
    public static Object deniedIndexCurrent(Object container,Object key,Object proposed) {
        Guards snapshot=guards;if(snapshot==null)return null;
        if(!(key instanceof Integer||key instanceof Long||key instanceof java.util.UUID)||!indexDenied(snapshot,container,null,null))return null;
        if(!(container instanceof java.util.Map<?,?> map))return null;
        Object current=map.get(key);
        return current!=null&&current!=proposed&&indexDenied(guards,container,key,current)?current:null;
    }
    /** Mixed clear keeps protected slots and still clears unrelated entries in this exact map. */
    public static boolean clearProtectedMap(Object candidate) {
        if(!controlMutationAllowed(candidate))return true;
        Guards current=guards;
        if(!indexDenied(current,candidate,null,null))return false;
        if(candidate instanceof java.util.Collection<?> collection) {
            var values=new java.util.ArrayList<>(collection);
            for(Object value:values)if(!indexDenied(guards,candidate,value,null))collection.remove(value);
            return true;
        }
        if(!(candidate instanceof java.util.Map<?,?> map))throw new IllegalStateException("INDEX_MAP_REQUIRED");
        // Like Ronova's registered index repair, keep no live iterator across guarded mutations.
        var snapshot=new java.util.ArrayList<java.util.Map.Entry<?,?>>();
        for(var entry:map.entrySet())snapshot.add(new java.util.AbstractMap.SimpleImmutableEntry<>(entry.getKey(),entry.getValue()));
        for(var entry:snapshot)if(map.get(entry.getKey())==entry.getValue()
                &&!indexDenied(guards,map,entry.getKey(),entry.getValue()))map.remove(entry.getKey());
        return true;
    }
    private static volatile java.lang.invoke.MethodHandle resourceGuard;
    private static volatile java.lang.invoke.MethodHandle nativeRegistrationGuard;
    public static synchronized void installNativeRegistrationGuard(Class<?> owner,java.lang.invoke.MethodHandle callback){
        requirePolicyInstaller();if(guards==null||guards.owner()!=owner)throw new IllegalArgumentException("NATIVE_REGISTRATION_OWNER");
        nativeRegistrationGuard=callback.asType(java.lang.invoke.MethodType.methodType(boolean.class,Class.class,Module[].class));
    }
    public static synchronized void installResourceGuard(Class<?> owner,java.lang.invoke.MethodHandle callback) {
        requirePolicyInstaller();
        if(guards==null||guards.owner()!=owner)throw new IllegalArgumentException("RESOURCE_OWNER");
        resourceGuard=callback.asType(java.lang.invoke.MethodType.methodType(boolean.class,Object.class));
    }
    public static boolean resourceReleaseAllowed(Object resource) {
        if(!controlMutationAllowed(resource))return false;
        var guard=resourceGuard;if(guard==null)return true;
        try {return !(boolean)guard.invokeExact(resource);}catch(Throwable failure){throw new IllegalStateException("RESOURCE_RELEASE_GUARD",failure);}
    }
    public static synchronized void installBackingPolicy(Class<?> owner,java.lang.invoke.MethodHandle known,java.lang.invoke.MethodHandle element,java.lang.invoke.MethodHandle array,java.lang.invoke.MethodHandle offset,java.lang.invoke.MethodHandle memory) {
        requirePolicyInstaller();
        if(guards==null||guards.owner()!=owner)throw new IllegalArgumentException("BACKING_OWNER");
        BackingBridge.install(known,element,array,offset,memory);
    }
    private static boolean ownState(Class<?> declaring) {
        if(declaring==null)return false;Class<?> host=declaring.getNestHost();Guards current=guards;
        if(host==TaskBridge.class||host==BackingBridge.class||host==SourceMapBridge.class||host==NativeControl.class||host==DefinitionBridge.class||host==ResourceBridge.class||host==IoBridge.class||host==CodeSourceBridge.class||host==NetworkBridge.class||host==ClientBridge.class
                ||host==EventFaultBridge.class||host==ControlBridge.class||host==ExecutionFlow.class)return true;
        if(ControlBridge.owns(declaring))return true;
        return current!=null&&DefinitionBridge.module(declaring)==current.owner().getModule()&&declaring.getClassLoader()==current.owner().getClassLoader()&&declaring.getName().startsWith("dev.ronova.pro.");
    }
    private static volatile Class<?> eventBoundary;
    public static void installEventBoundary(Class<?> type) {
        if(!ControlBridge.owns(WALKER.getCallerClass())||!type.getName().equals("net.minecraftforge.eventbus.EventBus"))throw new SecurityException("EVENT_CONTROL_OWNER");
        if(eventBoundary!=null&&eventBoundary!=type)throw new SecurityException("EVENT_CONTROL_IDENTITY");
        eventBoundary=type;
    }
    public static boolean listenerRemovalAllowed(Object target) {
        if(target==null||!ownState(target instanceof Class<?> type?type:target.getClass()))return true;
        Class<?> caller=WALKER.walk(frames->frames.map(StackWalker.StackFrame::getDeclaringClass)
                .filter(type->type!=TaskBridge.class&&type!=eventBoundary&&!type.getName().startsWith("java.")&&!type.getName().startsWith("jdk."))
                .findFirst().orElse(null));
        return caller!=null&&(ownState(caller)||ControlBridge.owns(caller));
    }
    public static boolean unsafeWriteAllowed(Object receiver,long offset,Object value) {
        return unsafeWriteAllowed(receiver,offset,value,8);
    }
    public static boolean unsafeWriteAllowed(Object receiver,long offset,Object value,String kind) {
        return unsafeWriteAllowed(receiver,offset,value,ResourceBridge.unsafeWidth(kind));
    }
    private static boolean classReflectionWrite(Object receiver,long offset,int width){
        // A controller's Class mirror also holds Class's own instance cache.
        // Admit only its real JDK CAS, never an arbitrary write to that mirror.
        if(!ORIGIN_WALKER.walk(CLASS_REFLECTION_WRITER))return false;
        var field=ResourceBridge.exactField(receiver,offset,width);
        return field!=null&&field.getDeclaringClass()==Class.class&&!java.lang.reflect.Modifier.isStatic(field.getModifiers())
                &&field.getName().equals("reflectionData")&&field.getType()==java.lang.ref.SoftReference.class;
    }
    private static boolean unsafeWriteAllowed(Object receiver,long offset,Object value,int resourceWidth) {
        // The exact controller gate must also operate while its caller's
        // business module is stopping. Its existing control-writer check is
        // authoritative; it is not a business field or backing-policy target.
        if(CodeSourceBridge.fieldGateMetadata(receiver))return controlMutationAllowed(receiver);
        if(!CodeSourceBridge.fieldRangeAllowed(receiver,offset,resourceWidth)&&!coreWriter())return false;
        if(!ResourceBridge.unsafeMutationAllowed(receiver,offset,resourceWidth))return false;
        if((modStopped(objectModule(receiver))||stoppedInvocation())&&!coreWriter())return false;
        if(receiver instanceof Class<?> owner&&ownState(owner)&&!classReflectionWrite(receiver,offset,resourceWidth)
                ||receiver!=null&&ownState(receiver.getClass()))return false;
        if(receiver==null)return true; // Raw-address writes have no exact object binding here.
        if(receiver instanceof java.util.Map.Entry<?,?>&&controlled(SourceMapBridge.ownerOf(receiver))&&!controlCaller())return false;
        if(!controlMutationAllowed(receiver)||!BackingBridge.offsetAllowed(receiver,offset,value))return false;
        Guards current=guards;
        if(current==null)return true;
        for(UnsafeField field:current.unsafeFields())if(field.declaring().isInstance(receiver))
            try {
                int width=value instanceof Long||value instanceof Double||value==null||!value.getClass().isPrimitive()&&!(value instanceof Number)&&!(value instanceof Boolean)&&!(value instanceof Character)?8:value instanceof Integer||value instanceof Float?4:value instanceof Short||value instanceof Character?2:1;
                if(!rangesOverlap(offset,width,field.offset(),field.type().isPrimitive()?primitiveWidth(field.type()):8))continue;
                Object proposed=offset==field.offset()?fieldValue(field.type(),value):null;
                if((boolean)current.field().invokeExact(receiver,field.declaring(),field.name(),proposed))return false;
            }
            catch(Throwable failure) { throw new IllegalStateException("FIELD_POLICY_FAILED",failure); }
        return true;
    }
    private static int primitiveWidth(Class<?> type) {
        return type==long.class||type==double.class?8:type==int.class||type==float.class?4:type==short.class||type==char.class?2:1;
    }
    private static boolean rangesOverlap(long left,long leftSize,long right,long rightSize) {
        return left>=0&&right>=0&&leftSize>0&&rightSize>0&&left<right+rightSize&&right<left+leftSize;
    }
    private static Object fieldValue(Class<?> type,Object raw) {
        if(type==double.class&&raw instanceof Long bits)return Double.longBitsToDouble(bits);
        if(type==float.class&&raw instanceof Integer bits)return Float.intBitsToFloat(bits);
        if(type==int.class)return raw instanceof Integer?raw:null;
        if(type==long.class)return raw instanceof Long?raw:null;
        if(type==short.class)return raw instanceof Short?raw:null;
        if(type==char.class)return raw instanceof Character?raw:null;
        if(type==byte.class)return raw instanceof Byte?raw:null;
        if(type==boolean.class)return raw instanceof Boolean?raw:null;
        if(type==float.class)return raw instanceof Float?raw:null;
        if(type==double.class)return raw instanceof Double?raw:null;
        return raw;
    }
    public static boolean unsafeMemoryAllowed(Object receiver,long offset,long length) {
        if(!CodeSourceBridge.fieldRangeAllowed(receiver,offset,length)&&!coreWriter())return false;
        if(!ResourceBridge.unsafeMutationAllowed(receiver,offset,length))return false;
        if((modStopped(objectModule(receiver))||stoppedInvocation())&&!coreWriter())return false;
        Guards current=guards;if(current==null||receiver==null||length<=0)return true;
        if(receiver instanceof Class<?> owner&&ownState(owner)||ownState(receiver.getClass()))return false;
        if(!BackingBridge.memoryWriteAllowed(receiver,offset,length))return false;
        final long end;try {end=Math.addExact(offset,length);}catch(ArithmeticException overflow){return false;}
        for(UnsafeField field:current.unsafeFields())if(field.declaring().isInstance(receiver)) {
            long at=field.offset();
            if(offset<at+8&&end>at)try {
                if((boolean)current.field().invokeExact(receiver,field.declaring(),field.name(),(Object)null))return false;
            }catch(Throwable failure){throw new IllegalStateException("UNSAFE_MEMORY_POLICY_FAILED",failure);}
        }
        return true;
    }
    private static boolean coreWriter() {
        Guards current=guards;if(current==null)return false;
        Class<?> caller=WALKER.walk(CORE_CALLER);
        return DefinitionBridge.module(caller)==current.owner().getModule()&&caller.getClassLoader()==current.owner().getClassLoader()
                &&Objects.equals(caller.getProtectionDomain().getCodeSource(),current.owner().getProtectionDomain().getCodeSource());
    }
    private static final ThreadLocal<Boolean> FIELD_POLICY_ACTIVE=new ThreadLocal<>();
    // The gate directory must not itself use an observed Map: an index hook can
    // re-enter it while SourceMapBridge decides whether that Map may publish.
    private static final class FieldGateNode extends java.lang.ref.WeakReference<Object> {
        final int hash;
        final java.util.concurrent.locks.ReentrantLock gate=newFieldGate();
        FieldGateNode previous,next;
        FieldGateNode(Object receiver,int hash,java.lang.ref.ReferenceQueue<Object> queue,FieldGateNode next) {
            super(receiver,queue);this.hash=hash;this.next=next;if(next!=null)next.previous=this;
        }
    }
    public static boolean nativeArrayElementAllowed(Object array,int index,Object value){
        // arrayAllowed performs the current array guard itself, followed by
        // the control and exact value policy; no permission is carried forward.
        return BackingBridge.arrayAllowed(array,index,value);
    }
    public static int nativePrimitiveArrayPolicy(Object array){return BackingBridge.nativePrimitiveArrayPolicy(array);}
    public static boolean nativeArrayPointerAllowed(Object array){
        return arrayWriteAllowed(array)&&BackingBridge.nativePointerAllowed(array);
    }
    static boolean arrayWriteAllowed(Object array){return !(controlled(array)&&!controlCaller())&&!((modStopped(objectModule(array))||stoppedInvocation())&&!coreWriter());}
    public static boolean nativeRegistrationTargetAllowed(Class<?> type){
        // A public JNI registration is a write to this exact declaring class.
        // No caller may replace a controller's entry points through RegisterNatives.
        if(type==null||ownState(type)||modStopped(DefinitionBridge.module(type))||stoppedInvocation())return false;
        if(type.getClassLoader()==null)return ORIGIN_WALKER.walk(frames->frames.filter(StackWalker.StackFrame::isNativeMethod)
                .findFirst().map(frame->frame.getDeclaringClass()==type
                        &&(frame.getMethodName().equals("registerNatives")||frame.getMethodName().equals("initIDs"))).orElse(false));
        return true;
    }
    public static boolean nativeRegistrationAllowed(Class<?> type,Module[] sources){
        if(!nativeRegistrationTargetAllowed(type))return false;
        if(sources!=null)for(Module source:sources)if(source==null||modStopped(source))return false;
        var guard=nativeRegistrationGuard;
        if(guard!=null)try{if((boolean)guard.invokeExact(type,sources))return false;}
            catch(Throwable failure){throw new IllegalStateException("NATIVE_REGISTRATION_GUARD",failure);}
        return true;
    }
    static boolean recoveryWriter(){return coreWriter()||ControlBridge.owns(WALKER.getCallerClass());}
    static boolean coreResourceCallback(Object callback){
        if(WALKER.getCallerClass()!=ResourceBridge.class||callback==null||guards==null)return false;
        Class<?> type=callback.getClass(),owner=guards.owner();
        return DefinitionBridge.module(type)==owner.getModule()&&type.getClassLoader()==owner.getClassLoader()
                &&Objects.equals(type.getProtectionDomain().getCodeSource(),owner.getProtectionDomain().getCodeSource());
    }
    private static final class FieldGateBucket {
        final java.lang.ref.ReferenceQueue<Object> retired=new java.lang.ref.ReferenceQueue<>();
        // This directory cannot invoke monitored JDK maps. The outer stripe
        // uses the low ten hash bits; its local table uses the remaining bits.
        volatile FieldGateNode[] table=new FieldGateNode[16];int size;
        private static int bucket(int hash,int length){return ((hash>>>10)^(hash>>>20))&(length-1);}
        synchronized java.util.concurrent.locks.ReentrantLock get(Object receiver) {
            for(int i=0;i<16;i++) {
                FieldGateNode dead=(FieldGateNode)retired.poll();if(dead==null)break;
                int at=bucket(dead.hash,table.length);FieldGateNode previous=dead.previous,next=dead.next;
                if(previous==null){if(table[at]!=dead)continue;table[at]=next;}
                else {if(previous.next!=dead)continue;previous.next=next;}
                if(next!=null)next.previous=previous;
                dead.previous=null;dead.next=null;size--;
            }
            int hash=System.identityHashCode(receiver);
            int at=bucket(hash,table.length);
            for(FieldGateNode node=table[at];node!=null;node=node.next)if(node.hash==hash&&node.get()==receiver)return node.gate;
            if(size>=table.length-(table.length>>>2)&&table.length<(1<<30)){
                FieldGateNode[] next=new FieldGateNode[table.length*2];
                for(FieldGateNode first:table)for(FieldGateNode node=first;node!=null;){
                    FieldGateNode following=node.next;int slot=bucket(node.hash,next.length);
                    node.previous=null;node.next=next[slot];if(node.next!=null)node.next.previous=node;next[slot]=node;node=following;
                }
                table=next;at=bucket(hash,table.length);
            }
            FieldGateNode added=new FieldGateNode(receiver,hash,retired,table[at]);
            table[at]=added;size++;return added.gate;
        }
    }
    private static java.util.concurrent.locks.ReentrantLock newFieldGate(){
        if(WALKER.getCallerClass()!=FieldGateNode.class)throw new SecurityException("ACTUAL_FIELD_GATE_OWNER_REQUIRED");
        var gate=new java.util.concurrent.locks.ReentrantLock();CodeSourceBridge.fieldGateControls(gate);return gate;
    }
    private static final FieldGateBucket[] FIELD_GATES=new FieldGateBucket[1024];
    static {for(int i=0;i<FIELD_GATES.length;i++)FIELD_GATES[i]=new FieldGateBucket();}
    private static boolean fieldGateControl(Object value){
        FieldGateBucket[] buckets=FIELD_GATES;if(buckets==null)return false;
        if(value==buckets)return true;
        if(value instanceof FieldGateNode[])for(FieldGateBucket bucket:buckets)if(bucket!=null&&bucket.table==value)return true;
        return false;
    }
    private static java.util.concurrent.locks.ReentrantLock fieldGate(Object receiver) {
        if(receiver==null)throw new IllegalArgumentException("INDEX_GATE_RECEIVER_REQUIRED");
        return FIELD_GATES[System.identityHashCode(receiver)&(FIELD_GATES.length-1)].get(receiver);
    }
    // The publication belongs to a real writer scope, not to replaceable ThreadLocal state.
    // A nested writer for the same receiver retains incoming locks until its outer writer exits.
    private static final Map<Thread,SourcePublication> SOURCE_PUBLICATIONS=new IdentityHashMap<>();
    private static final class SourcePublication {
        final Object receiver;final Thread thread=Thread.currentThread();final SourcePublication previous;
        List<java.util.concurrent.locks.Lock> locks;boolean closed;
        SourcePublication(Object receiver){
            this.receiver=receiver;CodeSourceBridge.mutationControls(this,SOURCE_PUBLICATIONS);
            synchronized(SOURCE_PUBLICATIONS){previous=SOURCE_PUBLICATIONS.put(thread,this);}
        }
        void retain(java.util.concurrent.locks.Lock lock){
            try{
                if(locks==null){locks=new ArrayList<>();CodeSourceBridge.mutationControls(locks);}
                locks.add(lock);
            }catch(RuntimeException|Error failure){lock.unlock();throw failure;}
        }
        void close(){
            if(closed)return;
            synchronized(SOURCE_PUBLICATIONS){
                if(thread!=Thread.currentThread()||SOURCE_PUBLICATIONS.get(thread)!=this)
                    throw new IllegalMonitorStateException("SOURCE_PUBLICATION_THREAD_AND_ORDER_REQUIRED");
                if(previous==null)SOURCE_PUBLICATIONS.remove(thread);else SOURCE_PUBLICATIONS.put(thread,previous);
                closed=true;
            }
            if(locks!=null)for(int i=locks.size()-1;i>=0;i--)locks.get(i).unlock();
        }
    }
    static Object beginSourcePublication(Object receiver){
        if(WALKER.getCallerClass()!=SourceMapBridge.class)throw new SecurityException("ACTUAL_SOURCE_WRITER_REQUIRED");
        return receiver==null?null:new SourcePublication(receiver);
    }
    static void endSourcePublication(Object token){
        if(WALKER.getCallerClass()!=SourceMapBridge.class)throw new SecurityException("ACTUAL_SOURCE_WRITER_REQUIRED");
        if(token instanceof SourcePublication publication)publication.close();
    }
    private static final class FieldMutation {
        final java.util.concurrent.locks.ReentrantLock gate;final Object source,receiver;
        final Thread thread=Thread.currentThread();final SourcePublication publication;boolean closed;
        FieldMutation(java.util.concurrent.locks.ReentrantLock gate,Object source,Object receiver){
            this.gate=gate;this.source=source;this.receiver=receiver;
            publication=SourceMapBridge.holderRegistered(receiver)||SourceMapBridge.carrierRegistered(receiver)?new SourcePublication(receiver):null;
        }
        java.util.concurrent.locks.ReentrantLock gate(){return gate;}
        Object source(){return source;}
        Object receiver(){return receiver;}
        void close(){
            if(closed)return;
            if(thread!=Thread.currentThread())throw new IllegalMonitorStateException("FIELD_MUTATION_THREAD_REQUIRED");
            try{if(publication!=null)publication.close();}finally{closed=true;gate.unlock();}
        }
    }
    public static void observeSourceHolder(Object holder){
        if(!coreWriter())throw new SecurityException("SOURCE_HOLDER_CORE_REQUIRED");
        SourceMapBridge.registerHolder(holder);
    }
    public static void observeSourceCarrier(Object carrier){
        if(!coreWriter())throw new SecurityException("SOURCE_CARRIER_CORE_REQUIRED");
        SourceMapBridge.registerCarrier(carrier);
    }
    public static void recoveredSourceChange(Object map){
        if(!coreWriter())throw new SecurityException("SOURCE_RECOVERY_CORE_REQUIRED");
        if(primitiveSourceIndex(map)){
            if(!fieldGate(map).isHeldByCurrentThread())throw new IllegalMonitorStateException("SOURCE_INDEX_GATE_REQUIRED");
            SourceMapBridge.holderChanged(map);return;
        }
        SourceMapBridge.recoveredChange(map);
    }
    public static void finishSourceCarrier(Object scope,Object mutation,boolean written){
        try{finishFieldMutation(mutation,written);}finally{SourceMapBridge.exit(scope);}
    }
    public static void failedSourceCarrier(Object scope,Object mutation,Throwable failure){
        try{fieldMutationFailed(mutation,failure);}finally{
            try{SourceMapBridge.exit(scope);}catch(RuntimeException|Error unavailable){if(unavailable!=failure)failure.addSuppressed(unavailable);}
        }
    }
    /** The incoming carrier stays locked through actual write, readback and failed-CAS cleanup. */
    public static boolean holdSourcePublication(Object holder,Object incoming){
        if(!coreWriter())throw new SecurityException("SOURCE_PUBLICATION_CORE_REQUIRED");
        SourcePublication mutation=null;
        synchronized(SOURCE_PUBLICATIONS){
            for(SourcePublication frame=SOURCE_PUBLICATIONS.get(Thread.currentThread());frame!=null;frame=frame.previous)
                if(frame.receiver==holder&&!frame.closed)mutation=frame;
        }
        if(mutation==null)return false;
        if(incoming==null)return true;
        java.util.concurrent.locks.Lock gate;
        if(incoming.getClass()==java.util.concurrent.atomic.AtomicReference.class
                ||incoming.getClass()==java.util.concurrent.CopyOnWriteArrayList.class||incoming instanceof Object[]
                ||incoming.getClass()==java.util.HashSet.class||incoming.getClass()==java.util.LinkedHashSet.class||primitiveSourceIndex(incoming)
                ||incoming.getClass().getClassLoader()!=null&&!(incoming instanceof Map<?,?>)&&!(incoming instanceof Collection<?>)){
            gate=fieldGate(incoming);if(!gate.tryLock())return false;
        }else {gate=SourceMapBridge.publicationGate(incoming);if(gate==null)return false;}
        mutation.retain(gate);
        if(incoming.getClass()==java.util.concurrent.atomic.AtomicReference.class||incoming.getClass()==java.util.concurrent.CopyOnWriteArrayList.class){
            java.util.concurrent.locks.Lock direct=SourceMapBridge.publicationGate(incoming);if(direct==null)return false;
            mutation.retain(direct);
        }
        return true;
    }
    private record RecoveryArrayMutation(Object array,FieldMutation mutation,int start){
        RecoveryArrayMutation{CodeSourceBridge.mutationControls(this);}
    }
    private static final class RawMemoryMutation {
        final long token;final boolean reading;final Thread thread=Thread.currentThread();boolean closed;
        RawMemoryMutation(long token,boolean reading){this.token=token;this.reading=reading;CodeSourceBridge.mutationControls(this);}
    }
    private static final class HeapMemoryRead {
        final java.util.concurrent.locks.ReentrantLock gate;final Object record;final Thread thread=Thread.currentThread();boolean closed;
        HeapMemoryRead(java.util.concurrent.locks.ReentrantLock gate,Object record){this.gate=gate;this.record=record;CodeSourceBridge.mutationControls(this);}
    }
    // Unsafe can replace Thread.threadLocals while one of these scopes is live.
    // Keep the actual thread's stack outside that mutable carrier, and release
    // its strong thread reference when the last operation finishes.
    private static final Map<Thread,MemoryCopy> MEMORY_COPIES=new IdentityHashMap<>();
    private static MemoryCopy memoryCopy(){synchronized(MEMORY_COPIES){return MEMORY_COPIES.get(Thread.currentThread());}}
    private static void memoryCopy(MemoryCopy copy){synchronized(MEMORY_COPIES){if(copy==null)MEMORY_COPIES.remove(Thread.currentThread());else MEMORY_COPIES.put(Thread.currentThread(),copy);}}
    private static final class MemoryCopy {
        final Object source,read,destination;final Module[] before;final long offset,length;final Thread thread=Thread.currentThread();final MemoryCopy previous=memoryCopy();boolean closed;
        MemoryCopy(Object source,long offset,long length,Object read,Object destination,Module[] before){this.source=source;this.offset=offset;this.length=length;this.read=read;this.destination=destination;this.before=before;CodeSourceBridge.mutationControls(this,before,MEMORY_COPIES);memoryCopy(this);}
    }
    private static final Map<Thread,UnsafeMutation> UNSAFE_MUTATIONS=new IdentityHashMap<>();
    private static UnsafeMutation unsafeMutation(){synchronized(UNSAFE_MUTATIONS){return UNSAFE_MUTATIONS.get(Thread.currentThread());}}
    private static void unsafeMutation(UnsafeMutation mutation){synchronized(UNSAFE_MUTATIONS){if(mutation==null)UNSAFE_MUTATIONS.remove(Thread.currentThread());else UNSAFE_MUTATIONS.put(Thread.currentThread(),mutation);}}
    private static final class UnsafeMutation {
        final Object receiver;final long offset,length;final FieldMutation receipt;final UnsafeMutation root,previous;final Module[] contributors;
        boolean written,closed;
        UnsafeMutation(Object receiver,long offset,long length,FieldMutation receipt,UnsafeMutation previous,UnsafeMutation root,Module[] contributors){
            this.receiver=receiver;this.offset=offset;this.length=length;this.receipt=receipt;this.previous=previous;this.root=root==null?this:root;this.contributors=contributors;
            CodeSourceBridge.mutationControls(this,contributors,UNSAFE_MUTATIONS);
        }
    }
    private static Object memoryReceipt(java.util.concurrent.locks.ReentrantLock gate,Object receiver){
        try{return new FieldMutation(gate,CodeSourceBridge.executionMemoryBefore(receiver,invokingSources()),receiver);}
        catch(RuntimeException|Error failure){gate.unlock();throw failure;}
    }
    private static Object fieldReceipt(java.util.concurrent.locks.ReentrantLock gate,Object receiver,Class<?> declaring,String name,String descriptor,boolean instruction){
        try{return new FieldMutation(gate,CodeSourceBridge.executionFieldMemoryBefore(receiver,declaring,name,descriptor,invokingSources(),instruction),receiver);}
        catch(RuntimeException|Error failure){gate.unlock();throw failure;}
    }
    /** A selected field mutation holds the same short gate as the target's policy publication. */

    private static Object staticFieldMutation(Object value,Class<?> symbolic,String signature){
        int split=signature.lastIndexOf('\0');
        if(split<0)throw new IllegalArgumentException("ACTUAL_FIELD_SIGNATURE_REQUIRED");
        Object token=beginFieldMutation(null,symbolic,signature.substring(0,split),signature.substring(split+1),value);
        return token==Boolean.FALSE?null:token;
    }
    public static Object beginOwnStaticFieldMutation(Object value,String signature){return staticFieldMutation(value,WALKER.getCallerClass(),signature);}
    public static Object beginStaticFieldMutation(Object value,Class<?> symbolic,String signature){return staticFieldMutation(value,symbolic,signature);}
    public static Object beginOwnStaticFieldMutation(boolean value,String signature){return staticFieldMutation(Boolean.valueOf(value),WALKER.getCallerClass(),signature);}
    public static Object beginStaticFieldMutation(boolean value,Class<?> symbolic,String signature){return staticFieldMutation(Boolean.valueOf(value),symbolic,signature);}
    public static Object beginOwnStaticFieldMutation(byte value,String signature){return staticFieldMutation(Byte.valueOf(value),WALKER.getCallerClass(),signature);}
    public static Object beginStaticFieldMutation(byte value,Class<?> symbolic,String signature){return staticFieldMutation(Byte.valueOf(value),symbolic,signature);}
    public static Object beginOwnStaticFieldMutation(char value,String signature){return staticFieldMutation(Character.valueOf(value),WALKER.getCallerClass(),signature);}
    public static Object beginStaticFieldMutation(char value,Class<?> symbolic,String signature){return staticFieldMutation(Character.valueOf(value),symbolic,signature);}
    public static Object beginOwnStaticFieldMutation(short value,String signature){return staticFieldMutation(Short.valueOf(value),WALKER.getCallerClass(),signature);}
    public static Object beginStaticFieldMutation(short value,Class<?> symbolic,String signature){return staticFieldMutation(Short.valueOf(value),symbolic,signature);}
    public static Object beginOwnStaticFieldMutation(int value,String signature){return staticFieldMutation(Integer.valueOf(value),WALKER.getCallerClass(),signature);}
    public static Object beginStaticFieldMutation(int value,Class<?> symbolic,String signature){return staticFieldMutation(Integer.valueOf(value),symbolic,signature);}
    public static Object beginOwnStaticFieldMutation(long value,String signature){return staticFieldMutation(Long.valueOf(value),WALKER.getCallerClass(),signature);}
    public static Object beginStaticFieldMutation(long value,Class<?> symbolic,String signature){return staticFieldMutation(Long.valueOf(value),symbolic,signature);}
    public static Object beginOwnStaticFieldMutation(float value,String signature){return staticFieldMutation(Float.valueOf(value),WALKER.getCallerClass(),signature);}
    public static Object beginStaticFieldMutation(float value,Class<?> symbolic,String signature){return staticFieldMutation(Float.valueOf(value),symbolic,signature);}
    public static Object beginOwnStaticFieldMutation(double value,String signature){return staticFieldMutation(Double.valueOf(value),WALKER.getCallerClass(),signature);}
    public static Object beginStaticFieldMutation(double value,Class<?> symbolic,String signature){return staticFieldMutation(Double.valueOf(value),symbolic,signature);}
    public static Object beginFieldMutation(Object receiver,Class<?> symbolic,String name,String descriptor,Object value) {
        Class<?> declaring=actualFieldOwner(symbolic,name,descriptor);
        if(declaring==null||declaring==Void.class){lost();return Boolean.FALSE;}
        Object target=receiver==null?declaring:receiver;
        java.util.concurrent.locks.ReentrantLock gate=fieldGate(target);gate.lock();
        Object receipt=fieldReceipt(gate,target,declaring,name,descriptor,true);
        try {if(fieldWriteAllowed(target,declaring,name,value))return receipt;}
        catch(RuntimeException|Error failure){fieldMutationFailed(receipt,failure);throw failure;}
        finishFieldMutation(receipt,false);return Boolean.FALSE;
    }
    public static void endFieldMutation(Object token) {
        finishFieldMutation(token,true);
    }
    public static void finishFieldMutation(Object token,boolean written){
        if(token instanceof MemoryCopy copy){
            if(copy.closed)return;if(copy.thread!=Thread.currentThread()||memoryCopy()!=copy)throw new IllegalMonitorStateException("MEMORY_COPY_THREAD_REQUIRED");
            Throwable failure=null;
            try{
                // A failed copy can already have changed its destination. Retain
                // writers seen during the source window before closing either end.
                Module[] sources=copySources(copy);
                if(copy.destination instanceof RawMemoryMutation write)NativeControl.memoryWriteContributors(write.token,sources);
                else if(copy.destination instanceof UnsafeMutation write)CodeSourceBridge.executionMemoryContributors(write.receipt.source(),sources);
            }catch(RuntimeException|Error unavailable){failure=unavailable;}
            failure=finishMutationRetaining(copy.destination,written&&failure==null,failure);
            failure=finishMutationRetaining(copy.read,written,failure);copy.closed=true;
            memoryCopy(copy.previous);
            if(failure instanceof RuntimeException exception)throw exception;if(failure instanceof Error error)throw error;
        }else if(token instanceof HeapMemoryRead read){
            if(read.closed)return;if(read.thread!=Thread.currentThread()||read.gate!=null&&!read.gate.isHeldByCurrentThread())throw new IllegalMonitorStateException("HEAP_MEMORY_READ_THREAD_REQUIRED");
            try{CodeSourceBridge.executionByteMemoryReadEnd(read.record);}finally{read.closed=true;if(read.gate!=null)read.gate.unlock();}
        }else if(token instanceof RawMemoryMutation mutation){
            if(mutation.closed)return;if(mutation.thread!=Thread.currentThread())throw new IllegalMonitorStateException("RAW_MEMORY_MUTATION_THREAD_REQUIRED");
            NativeControl.memoryWriteEnd(mutation.token,written);mutation.closed=true;
        }else if(token instanceof UnsafeMutation mutation){
            if(mutation.closed)return;
            if(unsafeMutation()!=mutation||!mutation.receipt.gate().isHeldByCurrentThread())throw new IllegalMonitorStateException("UNSAFE_MUTATION_THREAD_AND_ORDER_REQUIRED");
            try{
                if(written){
                    mutation.root.written=true;
                    CodeSourceBridge.executionMemoryContributors(mutation.receipt.source(),mutation.contributors);
                }
            }finally{
                mutation.closed=true;
                unsafeMutation(mutation.previous);
                if(mutation.root==mutation)try{
                    try{CodeSourceBridge.executionMemoryAfter(mutation.receipt.source(),written||mutation.written);}
                    finally{if(written||mutation.written)SourceMapBridge.holderChanged(mutation.receipt.receiver());}
                }finally{mutation.receipt.close();}
            }
        }else if(token instanceof FieldMutation mutation){
            if(mutation.closed)return;
            if(!mutation.gate().isHeldByCurrentThread())throw new IllegalMonitorStateException("FIELD_MUTATION_THREAD_REQUIRED");
            try{
                try{CodeSourceBridge.executionMemoryAfter(mutation.source(),written);}
                finally{if(written)SourceMapBridge.holderChanged(mutation.receiver());}
            }finally{mutation.close();}
        }else if(token instanceof java.util.concurrent.locks.ReentrantLock gate)gate.unlock();
    }
    private static Throwable finishMutationRetaining(Object token,boolean written,Throwable original){
        try{finishFieldMutation(token,written);}catch(RuntimeException|Error failure){if(original==null)return failure;if(failure!=original)original.addSuppressed(failure);}return original;
    }
    public static void fieldMutationFailed(Object token,Throwable original){finishMutationRetaining(token,false,Objects.requireNonNull(original));}
    /** The caller still applies its policy inside this exact receiver's write gate. */
    public static Object beginMemoryMutation(Object receiver) {
        if(WALKER.getCallerClass()!=BackingBridge.class&&!coreWriter()&&!NativeControl.memoryMutationBoundary(receiver))
            throw new SecurityException("ACTUAL_MEMORY_MUTATION_BOUNDARY_REQUIRED");
        java.util.concurrent.locks.ReentrantLock gate=fieldGate(receiver);gate.lock();
        return memoryReceipt(gate,receiver);
    }
    public static Object beginArrayMutation(Object receiver,int start,int length){
        if(WALKER.getCallerClass()!=BackingBridge.class&&!coreWriter()&&!NativeControl.memoryMutationBoundary(receiver))
            throw new SecurityException("ACTUAL_MEMORY_MUTATION_BOUNDARY_REQUIRED");
        java.util.concurrent.locks.ReentrantLock gate=fieldGate(receiver);gate.lock();
        try{return new FieldMutation(gate,CodeSourceBridge.executionArrayMemoryBefore(receiver,start,length,invokingSources()),receiver);}
        catch(RuntimeException|Error failure){gate.unlock();throw failure;}
    }
    private static Module[] mutationSources(Module[] nativeSources){
        Set<Module> sources=addModuleSources(null,invokingSources(),false);
        sources=addModuleSources(sources,Objects.requireNonNull(nativeSources),false);
        return sources==null?new Module[0]:sources.toArray(Module[]::new);
    }
    public static Object beginNativeArrayMutation(Object receiver,int start,int length,Module[] contributors){
        if(!NativeControl.memoryMutationBoundary(receiver))throw new SecurityException("ACTUAL_NATIVE_ARRAY_MUTATION_REQUIRED");
        java.util.concurrent.locks.ReentrantLock gate=fieldGate(receiver);gate.lock();
        try{return new FieldMutation(gate,CodeSourceBridge.executionArrayMemoryBefore(receiver,start,length,mutationSources(contributors)),receiver);}
        catch(RuntimeException|Error failure){gate.unlock();throw failure;}
    }
    public static Object beginNativeFieldMutation(Object receiver,Class<?> declaring,String name,String descriptor,Module[] contributors){
        if(!NativeControl.memoryMutationBoundary(receiver))throw new SecurityException("ACTUAL_NATIVE_FIELD_MUTATION_REQUIRED");
        java.util.concurrent.locks.ReentrantLock gate=fieldGate(receiver);gate.lock();
        try{return new FieldMutation(gate,CodeSourceBridge.executionFieldMemoryBefore(receiver,declaring,name,descriptor,mutationSources(contributors),false),receiver);}
        catch(RuntimeException|Error failure){gate.unlock();throw failure;}
    }
    public static Object beginNativeUnsafeMutation(Object receiver,long offset,long length,Object proposed,String kind,boolean bulk,Module[] contributors){
        if(!NativeControl.unsafeMutationBoundary(receiver,offset,length,kind,bulk))throw new SecurityException("ACTUAL_NATIVE_UNSAFE_MUTATION_REQUIRED");
        long bytes=bulk?length:ResourceBridge.unsafeWidth(kind);
        if(receiver==null)return (bulk?unsafeMemoryAllowed(null,offset,bytes):unsafeWriteAllowed(null,offset,proposed,kind))?rawReceipt(offset,bytes,mutationSources(contributors)):Boolean.FALSE;
        // Exact gate metadata has no business receipt. Still evaluate the
        // actual write policy before admitting it; avoid collecting sources
        // merely to discard them inside unsafeReceipt.
        if(CodeSourceBridge.fieldGateMetadata(receiver))return (bulk?unsafeMemoryAllowed(receiver,offset,bytes):unsafeWriteAllowed(receiver,offset,proposed,kind))?null:Boolean.FALSE;
        Object token=unsafeReceipt(receiver,offset,bytes,mutationSources(contributors),!bulk&&referenceKind(kind));
        try{
            if(bulk?unsafeMemoryAllowed(receiver,offset,bytes):unsafeWriteAllowed(receiver,offset,proposed,kind))return token;
        }catch(RuntimeException|Error failure){fieldMutationFailed(token,failure);throw failure;}
        finishFieldMutation(token,false);return Boolean.FALSE;
    }
    public static Object beginNativeUnsafeRead(Object receiver,long offset,String kind,Module[] contributors){
        if(!NativeControl.unsafeReadBoundary(receiver,offset,kind))throw new SecurityException("ACTUAL_NATIVE_UNSAFE_READ_REQUIRED");
        return receiver==null?readReceipt(null,offset,ResourceBridge.unsafeWidth(kind),mutationSources(contributors)):null;
    }
    public static Object beginNativeUnsafeCopy(Object source,long sourceOffset,Object receiver,long offset,long length,Module[] contributors){
        if(!NativeControl.unsafeCopyBoundary(source,sourceOffset,receiver,offset,length))throw new SecurityException("ACTUAL_NATIVE_UNSAFE_COPY_REQUIRED");
        return copyReceipt(source,sourceOffset,receiver,offset,length,mutationSources(contributors));
    }
    static Object beginExecutedUnsafeMutation(Object accessor,Object receiver,long offset,long length,Object proposed,Object source,long sourceOffset,String owner,String operation,String kind,boolean bulk,Module[] contributors){
        if(WALKER.getCallerClass()!=CodeSourceBridge.class)throw new SecurityException("ACTUAL_EXECUTED_UNSAFE_MUTATION_REQUIRED");
        Objects.requireNonNull(accessor);Class<?> actual;
        try{
            actual=switch(owner){case "sun/misc/Unsafe"->Class.forName("sun.misc.Unsafe",false,ClassLoader.getPlatformClassLoader());case "jdk/internal/misc/Unsafe"->Class.forName("jdk.internal.misc.Unsafe",false,null);default->throw new SecurityException("ACTUAL_UNSAFE_DECLARATION_REQUIRED");};
        }catch(ClassNotFoundException unavailable){throw new IllegalStateException("ACTUAL_UNSAFE_WRITE_UNAVAILABLE",unavailable);}
        if(accessor.getClass()!=actual)throw new SecurityException("ACTUAL_UNSAFE_RECEIVER_REQUIRED");
        long bytes=bulk?length:ResourceBridge.unsafeWidth(kind);
        if(operation.equals("copyMemory")||operation.equals("copySwapMemory"))return copyReceipt(source,sourceOffset,receiver,offset,bytes,mutationSources(contributors));
        if(operation.startsWith("get")&&!operation.startsWith("getAnd"))return readReceipt(receiver,offset,bytes,receiver==null?mutationSources(contributors):null);
        if(receiver==null)return (bulk?unsafeMemoryAllowed(null,offset,bytes):BackingBridge.nativeWriteAllowed(null,offset,proposed,operation,kind))?rawReceipt(offset,bytes,mutationSources(contributors)):Boolean.FALSE;
        Object token=unsafeReceipt(receiver,offset,bytes,mutationSources(contributors),!bulk&&referenceKind(kind));
        try{
            if(bulk?unsafeMemoryAllowed(receiver,offset,bytes):BackingBridge.nativeWriteAllowed(receiver,offset,proposed,operation,kind))return token;
        }catch(RuntimeException|Error failure){fieldMutationFailed(token,failure);throw failure;}
        finishFieldMutation(token,false);return Boolean.FALSE;
    }
    public static Object beginReflectionMutation(java.lang.reflect.Field field,Object receiver){
        if(WALKER.getCallerClass()!=java.lang.reflect.Field.class)throw new SecurityException("ACTUAL_REFLECTION_WRITE_REQUIRED");
        Class<?> declaring=field.getDeclaringClass();boolean statik=java.lang.reflect.Modifier.isStatic(field.getModifiers());
        if(!statik&&(receiver==null||!declaring.isInstance(receiver)))return null;
        Object target=statik?declaring:receiver;java.util.concurrent.locks.ReentrantLock gate=fieldGate(target);gate.lock();
        return fieldReceipt(gate,target,declaring,field.getName(),field.getType().descriptorString(),false);
    }
    private static void requireUnsafeWriter(){
        Class<?> caller,actual,internal;
        caller=actualMemoryCaller();
        try{actual=Class.forName("sun.misc.Unsafe",false,ClassLoader.getPlatformClassLoader());internal=Class.forName("jdk.internal.misc.Unsafe",false,null);}
        catch(ClassNotFoundException unavailable){throw new IllegalStateException("ACTUAL_UNSAFE_WRITE_UNAVAILABLE",unavailable);}
        if(caller!=actual&&caller!=internal)throw new SecurityException("ACTUAL_UNSAFE_WRITE_REQUIRED");
    }
    private static Object unsafeReceipt(Object receiver,long offset,long length){
        if(NativeControl.unsafeControlScope()||CodeSourceBridge.fieldGateMetadata(receiver))return null;
        return unsafeReceipt(receiver,offset,length,invokingSources());
    }
    private static Object unsafeReceipt(Object receiver,long offset,long length,Module[] contributors){
        return unsafeReceipt(receiver,offset,length,contributors,false);
    }
    private static boolean referenceKind(String kind){return "Object".equals(kind)||"Reference".equals(kind);}
    private static Object unsafeReceipt(Object receiver,long offset,long length,Module[] contributors,boolean reference){
        if(receiver==null)return rawReceipt(offset,length,contributors);
        if(CodeSourceBridge.fieldGateMetadata(receiver))return null;
        UnsafeMutation previous=unsafeMutation();
        if(sameUnsafeSpan(previous,receiver,offset,length)){
            UnsafeMutation use=new UnsafeMutation(receiver,offset,length,previous.receipt,previous,previous.root,contributors);
            unsafeMutation(use);return use;
        }
        java.util.concurrent.locks.ReentrantLock gate=fieldGate(receiver);gate.lock();
        try{
            FieldMutation receipt=new FieldMutation(gate,CodeSourceBridge.executionByteMemoryBefore(receiver,offset,length,contributors,reference),receiver);
            UnsafeMutation use=new UnsafeMutation(receiver,offset,length,receipt,previous,null,contributors);unsafeMutation(use);return use;
        }
        catch(RuntimeException|Error failure){gate.unlock();throw failure;}
    }
    static Object bufferDataBefore(Object destination,long address,long bytes,Object source,long sourceOffset,long sourceBytes,boolean copy,Module[] contributors){
        if(WALKER.getCallerClass()!=ResourceBridge.class)throw new SecurityException("ACTUAL_BUFFER_DATA_OPERATION_REQUIRED");
        Object token=copy?copyReceipt(source,sourceOffset,sourceBytes,destination,address,bytes,contributors):unsafeReceipt(destination,address,bytes,contributors);
        if(token==Boolean.FALSE)throw new IllegalStateException("RONOVA_TERMINAL_BUFFER_WRITE");return token;
    }
    static Object bufferReadBefore(Object holder,long offset,long bytes,Module[] contributors){
        if(WALKER.getCallerClass()!=ResourceBridge.class)throw new SecurityException("ACTUAL_BUFFER_DATA_READ_REQUIRED");
        return bytes<=0?null:readReceipt(holder,offset,bytes,contributors);
    }
    static Module[] bufferReadSources(Object token){
        if(WALKER.getCallerClass()!=ResourceBridge.class)throw new SecurityException("ACTUAL_BUFFER_READ_RESULT_REQUIRED");
        return token instanceof MemoryCopy copy?copySources(copy):readSources(token);
    }
    static void bufferDataFinished(Object token,boolean written){
        if(WALKER.getCallerClass()!=ResourceBridge.class)throw new SecurityException("ACTUAL_BUFFER_DATA_COMPLETION_REQUIRED");
        finishFieldMutation(token,written);
    }
    private static Object rawReceipt(long address,long bytes,Module[] contributors){
        return rawReceipt(address,bytes,contributors,false);
    }
    private static Object rawReceipt(long address,long bytes,Module[] contributors,boolean reading){
        if(bytes<=0||!NativeControl.available())return null;
        long token=NativeControl.memoryWriteBegin(address,bytes,contributors,reading);return token==0?null:new RawMemoryMutation(token,reading);
    }
    private static Object readReceipt(Object receiver,long offset,long bytes,Module[] contributors){
        if(receiver==null)return rawReceipt(offset,bytes,contributors,true);
        if(CodeSourceBridge.fieldGateMetadata(receiver))return null;
        for(MemoryCopy copy=memoryCopy();copy!=null;copy=copy.previous)
            if(!copy.closed&&copy.source==receiver&&offset>=copy.offset&&bytes<=copy.length&&offset-copy.offset<=copy.length-bytes)
                return new HeapMemoryRead(null,CodeSourceBridge.executionByteMemoryReadBefore(receiver,offset,bytes));
        java.util.concurrent.locks.ReentrantLock gate=fieldGate(receiver);gate.lock();
        try{return new HeapMemoryRead(gate,CodeSourceBridge.executionByteMemoryReadBefore(receiver,offset,bytes));}
        catch(RuntimeException|Error failure){gate.unlock();throw failure;}
    }
    static Module[] executedUnsafeReadSources(Object token){
        if(WALKER.getCallerClass()!=CodeSourceBridge.class)throw new SecurityException("ACTUAL_EXECUTED_UNSAFE_READ_REQUIRED");
        return readSources(token);
    }
    private static Module[] readSources(Object token){
        if(token instanceof RawMemoryMutation read&&read.reading){
            if(read.closed||read.thread!=Thread.currentThread())throw new IllegalMonitorStateException("RAW_MEMORY_READ_THREAD_REQUIRED");
            return NativeControl.memoryReadSources(read.token);
        }
        if(token instanceof HeapMemoryRead read){
            if(read.closed||read.thread!=Thread.currentThread()||read.gate!=null&&!read.gate.isHeldByCurrentThread())throw new IllegalMonitorStateException("HEAP_MEMORY_READ_THREAD_REQUIRED");
            return read.record==null?new Module[0]:CodeSourceBridge.executionByteMemoryReadSources(read.record);
        }
        return new Module[0];
    }
    private static Object copyReceipt(Object source,long sourceOffset,Object receiver,long offset,long bytes,Module[] contributors){
        return copyReceipt(source,sourceOffset,bytes,receiver,offset,bytes,contributors);
    }
    private static Object copyReceipt(Object source,long sourceOffset,long sourceBytes,Object receiver,long offset,long bytes,Module[] contributors){
        if(!unsafeMemoryAllowed(receiver,offset,bytes))return Boolean.FALSE;if(bytes<=0)return null;
        Object read=null,destination=null;
        try{
            if(source==null&&NativeControl.available()){
                long token=NativeControl.memoryCopyReadBegin(sourceOffset,sourceBytes,contributors);if(token!=0)read=new RawMemoryMutation(token,true);
            }else if(source!=null)read=new HeapMemoryRead(null,CodeSourceBridge.executionByteMemoryReadBefore(source,sourceOffset,sourceBytes));
            Module[] existing=readSources(read);
            Set<Module> combined=Collections.newSetFromMap(new IdentityHashMap<>());Collections.addAll(combined,contributors);Collections.addAll(combined,existing);
            destination=unsafeReceipt(receiver,offset,bytes,combined.toArray(Module[]::new));
            if(!unsafeMemoryAllowed(receiver,offset,bytes)){
                Object deniedDestination=destination,deniedRead=read;destination=null;read=null;
                Throwable failure=finishMutationRetaining(deniedDestination,false,null);failure=finishMutationRetaining(deniedRead,false,failure);
                if(failure instanceof RuntimeException exception)throw exception;if(failure instanceof Error error)throw error;
                return Boolean.FALSE;
            }
            return new MemoryCopy(source,sourceOffset,sourceBytes,read,destination,existing);
        }catch(RuntimeException|Error failure){finishMutationRetaining(destination,false,failure);finishMutationRetaining(read,false,failure);throw failure;}
    }
    private static Module[] copySources(MemoryCopy copy){
        if(copy.closed||copy.thread!=Thread.currentThread())throw new IllegalMonitorStateException("MEMORY_COPY_THREAD_REQUIRED");
        Set<Module> sources=Collections.newSetFromMap(new IdentityHashMap<>());Collections.addAll(sources,copy.before);
        Collections.addAll(sources,readSources(copy.read));
        return sources.toArray(Module[]::new);
    }
    private static boolean rawMemoryCaller(){
        Class<?> caller=WALKER.walk(RAW_MEMORY_CALLER);
        if(caller==ResourceBridge.class||caller==CodeSourceBridge.class||caller!=null&&caller.getNestHost()==ExecutionFlow.class||ControlBridge.owns(caller))return true;
        try{
            if(caller==Class.forName("sun.misc.Unsafe",false,ClassLoader.getPlatformClassLoader())||caller==Class.forName("jdk.internal.misc.Unsafe",false,null))return true;
        }catch(ClassNotFoundException unavailable){return false;}
        return caller!=null&&caller.getClassLoader()==null&&HandleWriters.roots.contains(caller.getNestHost());
    }
    private static boolean sameUnsafeSpan(UnsafeMutation use,Object receiver,long offset,long length){
        return use!=null&&!use.closed&&use.receiver==receiver&&use.offset==offset&&use.length==length&&use.receipt.gate().isHeldByCurrentThread();
    }
    public static boolean unsafeControlScope(){
        if(!NativeControl.unsafeControlScope())return false;
        Class<?> caller=actualMemoryCaller();
        try{
            if(caller==Class.forName("sun.misc.Unsafe",false,ClassLoader.getPlatformClassLoader())||caller==Class.forName("jdk.internal.misc.Unsafe",false,null))return true;
        }catch(ClassNotFoundException unavailable){throw new IllegalStateException("ACTUAL_UNSAFE_WRITE_UNAVAILABLE",unavailable);}
        return caller!=null&&caller.getClassLoader()==null&&HandleWriters.roots.contains(caller.getNestHost());
    }
    public static Object beginUnsafeMutation(Object receiver,long offset,long length){
        requireUnsafeWriter();return unsafeReceipt(receiver,offset,length);
    }
    public static Object beginUnsafeMutation(Object receiver,long offset,String kind){
        requireUnsafeWriter();return NativeControl.unsafeControlScope()||CodeSourceBridge.fieldGateMetadata(receiver)?null:unsafeReceipt(receiver,offset,ResourceBridge.unsafeWidth(kind),invokingSources(),referenceKind(kind));
    }
    public static Object beginUnsafeRead(Object receiver,long offset,String kind){
        requireUnsafeWriter();return receiver!=null||NativeControl.unsafeControlScope()?null:rawReceipt(offset,ResourceBridge.unsafeWidth(kind),invokingSources(),true);
    }
    public static Object beginUnsafeCopy(Object source,long sourceOffset,Object receiver,long offset,long length){
        requireUnsafeWriter();return NativeControl.unsafeControlScope()?null:copyReceipt(source,sourceOffset,receiver,offset,length,invokingSources());
    }
    private static final class HandleWriters {
        static final Set<Class<?>> roots=roots();
        private static Set<Class<?>> roots(){
            Set<Class<?>> result=Collections.newSetFromMap(new IdentityHashMap<>());
            for(String family:List.of("Booleans","Bytes","Shorts","Chars","Ints","Longs","Floats","Doubles","References"))
                try{result.add(Class.forName("java.lang.invoke.VarHandle"+family,false,null));}
                catch(ClassNotFoundException unavailable){throw new ExceptionInInitializerError(unavailable);}
            return Collections.unmodifiableSet(result);
        }
    }
    public static Object beginHandleMutation(Object receiver,long offset,String kind){
        requireHandleWriter(actualMemoryCaller());return NativeControl.unsafeControlScope()||CodeSourceBridge.fieldGateMetadata(receiver)?null:unsafeReceipt(receiver,offset,ResourceBridge.unsafeWidth(kind),invokingSources(),referenceKind(kind));
    }
    public static Object beginHandleRead(Object receiver,long offset,String kind){
        requireHandleWriter(actualMemoryCaller());return receiver!=null||NativeControl.unsafeControlScope()?null:rawReceipt(offset,ResourceBridge.unsafeWidth(kind),invokingSources(),true);
    }
    private static Class<?> actualMemoryCaller(){
        // getCallerClass always hides MethodHandle/VarHandle frames, including
        // real JDK writers. Inspect the first actual frame outside this bridge.
        return ORIGIN_WALKER.walk(MEMORY_CALLER);
    }
    private static void requireHandleWriter(Class<?> caller){
        Class<?> internal;try{internal=Class.forName("jdk.internal.misc.Unsafe",false,null);}catch(ClassNotFoundException unavailable){throw new IllegalStateException("ACTUAL_UNSAFE_WRITE_UNAVAILABLE",unavailable);}
        if(caller!=internal&&(caller==null||caller.getClassLoader()!=null||!HandleWriters.roots.contains(caller.getNestHost())))throw new SecurityException("ACTUAL_HANDLE_WRITE_REQUIRED");
    }
    public static Object beginRecoveryMutation(Object receiver) {
        if(!coreWriter())throw new SecurityException("GROUP_RECOVERY_CORE_REQUIRED");
        java.util.concurrent.locks.ReentrantLock gate=fieldGate(receiver);if(!gate.tryLock())return Boolean.FALSE;
        return memoryReceipt(gate,receiver);
    }
    public static Object beginRecoveryArrayMutation(Object array,int start,int length,long previousRevision){
        if(!coreWriter())throw new SecurityException("GROUP_RECOVERY_CORE_REQUIRED");
        return recoveryArrayMutation(array,start,length,previousRevision,true);
    }
    public static Object beginRecoveryArrayMutation(Object array,int start,int length){
        if(!coreWriter())throw new SecurityException("GROUP_RECOVERY_CORE_REQUIRED");
        return recoveryArrayMutation(array,start,length,0,false);
    }
    public static Object beginRecoveryArrayRead(Object array){
        if(!coreWriter())throw new SecurityException("GROUP_RECOVERY_CORE_REQUIRED");
        if(array==null||!array.getClass().isArray())throw new IllegalArgumentException("ACTUAL_RECOVERY_ARRAY_REQUIRED");
        java.util.concurrent.locks.ReentrantLock gate=fieldGate(array);return gate.tryLock()?gate:Boolean.FALSE;
    }
    private static Object recoveryArrayMutation(Object array,int start,int length,long previousRevision,boolean resume){
        if(array==null||!array.getClass().isArray()||start<0||length<=0||start>java.lang.reflect.Array.getLength(array)-length)
            throw new IllegalArgumentException("ACTUAL_RECOVERY_ARRAY_RANGE_REQUIRED");
        java.util.concurrent.locks.ReentrantLock gate=fieldGate(array);if(!gate.tryLock())return Boolean.FALSE;
        try {
            if(resume&&previousRevision!=CodeSourceBridge.executionArrayRevision(array))start=0;
            FieldMutation mutation=new FieldMutation(gate,CodeSourceBridge.executionArrayMemoryBefore(array,start,length,invokingSources()),array);
            return new RecoveryArrayMutation(array,mutation,start);
        }catch(RuntimeException|Error failure){gate.unlock();throw failure;}
    }
    public static int recoveryArrayStart(Object token){
        if(!coreWriter()||!(token instanceof RecoveryArrayMutation mutation)||!mutation.mutation().gate().isHeldByCurrentThread())
            throw new SecurityException("ACTUAL_RECOVERY_ARRAY_MUTATION_REQUIRED");
        return mutation.start();
    }
    public static long finishRecoveryArrayMutation(Object token,boolean written){
        if(!coreWriter()||!(token instanceof RecoveryArrayMutation mutation)||!mutation.mutation().gate().isHeldByCurrentThread())
            throw new SecurityException("ACTUAL_RECOVERY_ARRAY_MUTATION_REQUIRED");
        try {
            CodeSourceBridge.executionMemoryAfter(mutation.mutation().source(),written);
            if(written)SourceMapBridge.holderChanged(mutation.array());
            return CodeSourceBridge.executionArrayRevision(mutation.array());
        }finally {mutation.mutation().close();}
    }
    static java.util.concurrent.locks.ReentrantLock heapRecoveryGate(Object receiver){
        if(WALKER.getCallerClass()!=CodeSourceBridge.class||!recoveryWriter())throw new SecurityException("HEAP_RECOVERY_CORE_REQUIRED");
        java.util.concurrent.locks.ReentrantLock gate=fieldGate(receiver);return gate.tryLock()?gate:null;
    }
    public static Object beginPolicyMutation(Object receiver) {
        Class<?> caller=WALKER.getCallerClass(),expected=guards==null?null:guards.owner();
        if(expected==null||caller.getNestHost()!=expected||receiver==null)throw new SecurityException("POLICY_GATE_OWNER");
        java.util.concurrent.locks.ReentrantLock gate=fieldGate(receiver);gate.lock();return gate;
    }
    public static void endPolicyMutation(Object token) {
        if(guards==null||WALKER.getCallerClass().getNestHost()!=guards.owner())throw new SecurityException("POLICY_GATE_OWNER");
        endFieldMutation(token);
    }
    private static final ClassValue<java.util.concurrent.ConcurrentHashMap<String,Class<?>>> FIELD_DECLARING=
            new ClassValue<>() {
                @Override protected java.util.concurrent.ConcurrentHashMap<String,Class<?>> computeValue(Class<?> type) {
                    return new java.util.concurrent.ConcurrentHashMap<>();
                }
            };
    private static Class<?> actualFieldOwner(Class<?> symbolic,String name,String descriptor) {
        if(symbolic==null||name==null||descriptor==null)return null;
        String key=name+'\u0000'+descriptor;
        var fields=FIELD_DECLARING.get(symbolic);Class<?> known=fields.get(key);
        if(known!=null)return known;
        return fields.computeIfAbsent(key,ignored->{
            Class<?> declaring=findFieldOwner(symbolic,name,descriptor);
            return declaring==null?Void.class:declaring;
        });
    }
    private static Class<?> findFieldOwner(Class<?> type,String name,String descriptor) {
        if(type==null)return null;
        for(java.lang.reflect.Field field:type.getDeclaredFields())
            if(field.getName().equals(name)&&field.getType().descriptorString().equals(descriptor))return type;
        // JVM field resolution searches direct superinterfaces before the superclass.
        for(Class<?> parent:type.getInterfaces()) {
            Class<?> declaring=findFieldOwner(parent,name,descriptor);if(declaring!=null)return declaring;
        }
        return findFieldOwner(type.getSuperclass(),name,descriptor);
    }
    /** Symbolic owners can name an inherited field; resolve its actual declaring class from the live definition. */
    public static boolean fieldWriteAllowed(Object receiver,Class<?> symbolic,String name,String descriptor,Object value) {
        Class<?> actual=actualFieldOwner(symbolic,name,descriptor);
        if(actual==null||actual==Void.class){lost();return false;}
        return fieldWriteAllowed(receiver==null?actual:receiver,actual,name,value);
    }
    public static boolean staticFieldWriteAllowed(Class<?> symbolic,String name,String descriptor,Object value) {
        Class<?> actual=actualFieldOwner(symbolic,name,descriptor);
        if(actual==null||actual==Void.class){lost();return false;}
        return staticFieldWriteAllowed(actual,name,value);
    }
    public static boolean fieldWriteAllowed(Object receiver,Class<?> declaring,String name,Object value) {
        if(CodeSourceBridge.fieldGateMetadata(receiver))return controlMutationAllowed(receiver);
        if(!CodeSourceBridge.fieldAllowed(receiver,declaring,name)&&!coreWriter())return false;
        if(!ResourceBridge.fieldMutationAllowed(receiver,declaring))return false;
        if((modStopped(objectModule(receiver==null?declaring:receiver))||stoppedInvocation())&&!coreWriter()&&!ClientBridge.cleanupField(receiver,declaring,name))return false;
        Guards current=guards;var guard=current==null?null:current.field();
        if(FIELD_POLICY_ACTIVE.get()!=null) {
            // Linking the policy MethodHandle itself uses ordinary JDK collection writes.
            // No protected receiver or owned controller is exempt from a nested policy call.
            if(receiver!=null&&!controlled(receiver)&&(receiver.getClass()==java.util.ArrayList.class||receiver.getClass()==java.util.HashMap.class
                    ||receiver.getClass().getName().startsWith("java.util.HashMap$")))return true;
            return false;
        }
        if(ownState(declaring)&&!coreWriter()||controlled(receiver)&&!controlCaller())return false;
        inheritControl(receiver,value);
        if(guard==null||receiver==null)return true;
        FIELD_POLICY_ACTIVE.set(true);
        try { return !(boolean)guard.invokeExact(receiver,declaring,name,value); }
        catch(Throwable failure) { throw new IllegalStateException("FIELD_POLICY_FAILED",failure); }
        finally {FIELD_POLICY_ACTIVE.remove();}
    }
    public static boolean staticFieldWriteAllowed(Class<?> declaring,String name,Object value) {
        return fieldWriteAllowed(declaring,declaring,name,value);
    }
    public static boolean reflectFieldWrite(java.lang.reflect.Field field,Object receiver,Object value) {
        if(java.lang.reflect.Modifier.isStatic(field.getModifiers()))receiver=field.getDeclaringClass();
        if(CodeSourceBridge.fieldGateMetadata(receiver))return controlMutationAllowed(receiver);
        if(!CodeSourceBridge.fieldAllowed(receiver,field.getDeclaringClass(),field.getName())&&!coreWriter())return false;
        if(!ResourceBridge.fieldMutationAllowed(receiver,field.getDeclaringClass()))return false;
        if((modStopped(objectModule(receiver==null?field.getDeclaringClass():receiver))||stoppedInvocation())&&!coreWriter()&&!ClientBridge.cleanupField(receiver,field.getDeclaringClass(),field.getName()))return false;
        if(receiver instanceof java.util.Map.Entry<?,?>&&controlled(SourceMapBridge.ownerOf(receiver))&&!controlCaller())return false;
        if(ownState(field.getDeclaringClass())||receiver!=null&&ownState(receiver.getClass())||controlled(receiver)&&!controlCaller())return false;
        Guards current=guards;
        if(current==null)return true;
        Object target=receiver==null?field.getDeclaringClass():receiver;
        try { return !(boolean)current.field().invokeExact(target,field.getDeclaringClass(),field.getName(),value); }
        catch(Throwable failure) { throw new IllegalStateException("FIELD_POLICY_FAILED",failure); }
    }
    private static volatile java.lang.invoke.MethodHandle lifeResults;
    public static synchronized void installLifeResults(Class<?> owner,java.lang.invoke.MethodHandle result) {
        requirePolicyInstaller();
        Guards current=guards;
        if(current==null||current.owner()!=owner)throw new SecurityException("LIFE_RESULT_OWNER");
        lifeResults=Objects.requireNonNull(result).asType(java.lang.invoke.MethodType.methodType(Object.class,Object.class,String.class));
    }
    /** Null means no current exact protection; the original getter then runs unchanged. */
    public static Object lifeResult(Object receiver,String operation) {
        var guard=lifeResults;
        if(guard==null||receiver==null)return null;
        try {return (Object)guard.invokeExact(receiver,operation);}
        catch(Throwable failure){throw new IllegalStateException("LIFE_RESULT_POLICY_FAILED",failure);}
    }
    public static boolean dispatchAllowed(Object receiver,String operation,Object value) {
        if((modStopped(objectModule(receiver))||stoppedInvocation())&&!coreWriter())return false;
        Guards current=guards;var guard=current==null?null:current.dispatch();
        if(guard==null||receiver==null)return true;
        try { return !(boolean)guard.invokeExact(receiver,operation,value); }
        catch(Throwable failure) { throw new IllegalStateException("DISPATCH_POLICY_FAILED",failure); }
    }
    private static boolean taskEffectAllowed(Guards current,Object task,String effect) {
        if(modStopped(objectModule(task))||stoppedInvocation())return false;
        var guard=current==null?null:current.task();
        if(guard==null||task==null)return true;
        try { return !(boolean)guard.invokeExact(task,effect); }
        catch(Throwable failure) { throw new IllegalStateException("TASK_POLICY_FAILED",failure); }
    }
    public static boolean taskEffectAllowed(Object task,String effect) {
        return taskEffectAllowed(guards,task,effect);
    }
    /** Called at the actual Future result writer, not from the later observer drain. */
    public static boolean resultPublicationAllowed(Object result) {
        Guards current=guards;
        return taskEffectAllowed(current,result,"publish") && taskEffectAllowed(current,currentTask(),"publish");
    }
    private static volatile java.lang.invoke.MethodHandle publicationGate;
    public static synchronized void installTaskPublicationGate(Class<?> owner,java.lang.invoke.MethodHandle callback) {
        requirePolicyInstaller();
        if(guards==null||guards.owner()!=owner)throw new SecurityException("TASK_PUBLICATION_OWNER");
        publicationGate=Objects.requireNonNull(callback).asType(
                java.lang.invoke.MethodType.methodType(AutoCloseable.class,Object.class,Object.class));
    }
    private static final class Publication implements AutoCloseable {
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
    private static final class NativeClone {
        static final java.lang.invoke.MethodHandle CALL;
        static {
            try {
                var lookup=java.lang.invoke.MethodHandles.privateLookupIn(Object.class,java.lang.invoke.MethodHandles.lookup());
                CALL=lookup.findSpecial(Object.class,"clone",java.lang.invoke.MethodType.methodType(Object.class),Object.class);
            } catch(ReflectiveOperationException unavailable) { throw new ExceptionInInitializerError(unavailable); }
        }
    }
    public static Object cloneObject(Object source)throws CloneNotSupportedException {
        final Object result;
        try {
            if(!taskEffectAllowed(source,"native-clone"))throw new CloneNotSupportedException("RONOVA_TERMINAL_CLONE_REFUSED");
            result=NativeControl.available()?NativeControl.cloneExact(source):(Object)NativeClone.CALL.invokeExact(source);
        }
        catch(CloneNotSupportedException expected) { throw expected; }
        catch(RuntimeException|Error failure) { throw failure; }
        catch(Throwable failure) { throw new IllegalStateException("NATIVE_CLONE_BOUNDARY_UNAVAILABLE",failure); }
        // There is no public API that accepts an asserted clone result: it always comes from Object.clone itself.
        Class<?> caller=WALKER.walk(frames->frames.dropWhile(frame->frame.getDeclaringClass()==TaskBridge.class)
                .findFirst().map(StackWalker.StackFrame::getDeclaringClass).orElse(null));
        emit(registration,25,source,result,currentTask(),caller);
        return result;
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
    private static void replaceHelperLocal(Helper frame,int slot,Object value) {
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
    private static boolean helperBoundary() {
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
    private static boolean waitBoundary() {
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
    private static void retireQueueRequest(QueueRelease request) {
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
    private static boolean queueBoundary() {
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
    private static final class Registration {
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
            if(current!=null&&task!=null&&boundary(false))emit(current,11,task,null,currentTask(),null);
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
    private static void sourceEvent(Registration current,int kind,Object task,Object scheduler,Object parent,Object execution) {
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
    private static final class Inline {
        Thread thread=Thread.currentThread();
        Object source,other,callback,output,frame;
        volatile boolean closed;
        Inline(Object source,Object other,Object callback) { this.source=source;this.other=other;this.callback=callback; }
    }
    private static boolean inlineBoundary() {
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
    private static Object enterFrame(Object task,boolean dispatch) {
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
    private static void exitFrame(Object token) {
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
            if(!frame.dispatch) { frame.task=null;frame.delegateOwner=null;frame.scheduler=null;frame.previous=null;frame.thread=null; }
        }
    }
    private static synchronized boolean denied(Registration current,Object task) {
        Admission admission=ADMISSIONS.get(task);
        if(admission==null||admission.owner!=current||!admission.fenced)return false;
        if(admission.denied<Long.MAX_VALUE)admission.denied++;return true;
    }
    private static synchronized void releaseAdmission(Frame frame) {
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
            frame.task=null;frame.delegateOwner=null;frame.scheduler=null;frame.previous=null;frame.thread=null;
        } catch(Throwable unavailable){lost();}
    }
    private static void restore(Frame frame) {
        if(frame.previous==null)CURRENT.remove();
        else CURRENT.set(frame.previous);
    }
    private static Frame currentFrame(Registration current) {
        Frame frame=CURRENT.get();
        return frame!=null&&(frame.registration==current||frame.registration==null)?frame:null;
    }
    private static Object logicalParent(Frame frame) {
        Frame parent=frame.previous;
        return parent!=null&&(parent.registration==frame.registration||parent.registration==null)?parent.task:null;
    }
    public static void submitted(Object scheduler,Object task) {
        try{submit(scheduler,task);}catch(Throwable unavailable){lost();}
    }
    private static Object scheduler(Object scheduler,Object task) {
        if(scheduler==task&&task instanceof java.util.concurrent.ForkJoinTask<?>) {
            Object pool=java.util.concurrent.ForkJoinTask.getPool();
            return pool==null?java.util.concurrent.ForkJoinPool.commonPool():pool;
        }
        return scheduler;
    }
    private static void submit(Object scheduler,Object task) {
        Registration current=registration;
        if(current==null||task==null||DELIVERING.get()!=null||!boundary(true))return;
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
    private static final class Submission {
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
    private static final class Overflow {
        final Registration registration;
        final Thread thread=Thread.currentThread();
        int depth=1;
        Overflow(Registration registration){this.registration=registration;}
    }
    public static Object currentTask(){Frame frame=currentFrame(registration);return overflowing()||DELIVERING.get()!=null||frame==null?null:frame.task;}
    public static Object currentExecution(){return overflowing()||DELIVERING.get()!=null?null:currentFrame(registration);}
    private static boolean overflowing() {
        Overflow overflow=OVERFLOW_DEPTH.get();
        if(overflow!=null&&overflow.registration!=registration){OVERFLOW_DEPTH.remove();return false;}
        return overflow!=null;
    }
    public static long droppedEvents(){return DROPPED.get();}
    private static volatile String firstLossSite="";
    public static String firstCaptureLoss(){return firstLossSite;}
    private static void lost(){lost(null);}
    private static void lost(Throwable failure){
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
    private static void emit(Registration expected,int kind,Object task,Object scheduler,Object parent,Object execution) {
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
    private static boolean boundary(boolean submission) {
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
    private static final class Frame {
        Object task;
        Frame previous;
        final Registration registration;
        Thread thread=Thread.currentThread();
        final int depth;
        final boolean dispatch,emitted;
        volatile boolean exited,retired;
        boolean admitted,blocked;
        Object delegateOwner,scheduler;
        Frame(Object task,Frame previous,boolean dispatch,Registration registration) {
            this.task=task;this.previous=previous;this.dispatch=dispatch;this.registration=registration;
            this.emitted=dispatch||previous==null||previous.task!=task||previous.dispatch;
            depth=previous==null?1:previous.depth+1;
        }
    }
}
