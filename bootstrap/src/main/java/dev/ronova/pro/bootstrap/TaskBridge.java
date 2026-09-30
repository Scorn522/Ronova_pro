package dev.ronova.pro.bootstrap;

import java.util.Objects;
import java.util.List;
import java.util.Set;
import java.util.ArrayList;
import java.util.function.Consumer;

/** JDK-only bootstrap bridge. Events describe execution, never grant disposal permission. */
public final class TaskBridge {
    public static final int ENTER=1,EXIT=2,SUBMIT=3,OVERFLOW=4,SUBMIT_ENTER=5,SUBMIT_EXIT=6,DISPATCH_ENTER=7,DISPATCH_EXIT=8,BODY=9,CANCELLED=10;
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
    private static final class EventKey extends java.lang.ref.WeakReference<Object> {
        final int hash;
        EventKey(Object listener,java.lang.ref.ReferenceQueue<Object> queue){super(listener,queue);hash=System.identityHashCode(listener);}
        @Override public int hashCode(){return hash;}
        @Override public boolean equals(Object other){return this==other||other instanceof EventKey key&&get()!=null&&get()==key.get();}
    }
    public static boolean modOwnerAllowed(Object owner) {
        Module module=owner instanceof Class<?> type?type.getModule():owner==null?null:owner.getClass().getModule();
        return !modStopped(module)&&!stoppedInvocation();
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
    private static boolean modStopped(Module module) {
        for(Module entry:stoppedModules)if(entry==module)return true;
        return false;
    }
    private static boolean stoppedInvocation() {
        if(stoppedModules.length==0)return false;
        return WALKER.walk(frames->frames.anyMatch(frame->modStopped(frame.getDeclaringClass().getModule())));
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
    public static int abiVersion(){return 25;}
    private record CreationDefinition(java.lang.ref.WeakReference<ClassLoader> loader,String name,Set<String> sites) { }
    private record CreationObserver(Class<?> owner,java.lang.invoke.MethodHandle callback,java.lang.invoke.MethodHandle failure) { }
    private static final List<CreationDefinition> CREATIONS=new ArrayList<>();
    private static final ThreadLocal<Class<?>> CREATION_DELIVERY=new ThreadLocal<>();
    private static final StackWalker CREATION_CALLER=StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE);
    private static volatile CreationObserver creationObserver;
    private static final ClassValue<Set<String>> CREATION_SITES=new ClassValue<>() {
        @Override protected Set<String> computeValue(Class<?> type) {
            synchronized(CREATIONS) {
                for(CreationDefinition definition:CREATIONS)if(definition.loader.get()==type.getClassLoader()&&definition.name.equals(type.getName()))return definition.sites;
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
            CREATIONS.removeIf(definition->definition.loader.get()==null||definition.loader.get()==loader&&definition.name.equals(name));
            if(sites.length!=0) {
                if(CREATIONS.size()>=65536) { lost();return; }
                CREATIONS.add(new CreationDefinition(new java.lang.ref.WeakReference<>(loader),name,Set.of(sites)));
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
        synchronized(CREATION_OWNERS) {return CREATION_OWNERS.get(new EventKey(object,null));}
    }
    public static void creationObserved(Object object) {
        deliverCreation(object,false);
    }
    public static void creationFailed(Throwable error) { deliverCreation(error,true); }
    private static void deliverCreation(Object object,boolean failed) {
        CreationObserver observer=creationObserver;
        if(observer==null||object==null||CREATION_DELIVERY.get()!=null)return;
        var caller=CREATION_CALLER.walk(frames->frames.skip(2).findFirst().orElse(null));
        if(caller==null||!CREATION_SITES.get(caller.getDeclaringClass()).contains(caller.getMethodName()+caller.getDescriptor()+":"+caller.getByteCodeIndex()))return;
        if(!failed) synchronized(CREATION_OWNERS) {
            EventKey stale;while((stale=(EventKey)CREATION_QUEUE.poll())!=null)CREATION_OWNERS.remove(stale);
            CREATION_OWNERS.put(new EventKey(object,CREATION_QUEUE),caller.getDeclaringClass().getModule());
        }
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
            "controlCaller","controlled","controlMutationAllowed","inheritControl",
            "fieldWriteAllowed","staticFieldWriteAllowed","reflectFieldWrite",
            "indexDenied","indexRemovalAllowed","indexWriteAllowed","beginIndexWrite","beginIndexNodeWrite","beginIndexArrayWrite","beginIndexRemoval","beginIndexNodeRemoval","beginIndexArrayRemoval","beginIndexStructure","endIndexMutation","deniedIndexCurrent",
            "registerConsoleInput","beginConsoleCommand","endConsoleCommand","consoleDispatchTrusted",
            "clearProtectedMap","unsafeWriteAllowed","unsafeMemoryAllowed",
            "resourceReleaseAllowed","lifeResult","dispatchAllowed","taskEffectAllowed",
            "resultPublicationAllowed");
    static boolean controlCaller() {
        Class<?> caller=WALKER.walk(frames->frames.filter(frame->{
            Class<?> type=frame.getDeclaringClass();String name=type.getName();
            // A JDK collection invoked *from* one of our real ledger writers is authorized.
            // Guard entry points themselves are omitted, so an external caller cannot turn
            // a direct protected-map mutation into an internal write by calling a guard.
            if(type==TaskBridge.class||name.startsWith("dev.ronova.pro.bootstrap.TaskBridge$"))
                return !EXTERNAL_GUARD_ENTRIES.contains(frame.getMethodName())
                        &&!(name.equals("dev.ronova.pro.bootstrap.TaskBridge$ControlRef")&&Set.of("clear","enqueue").contains(frame.getMethodName()));
            if(type==BackingBridge.class)return frame.getMethodName().equals("readUnsafe");
            if(type==SourceMapBridge.class&&java.util.Set.of("valueAllowed","checkIncoming","internal","changed").contains(frame.getMethodName()))return false;
            return !name.startsWith("java.")&&!name.startsWith("jdk.")&&!name.startsWith("sun.");
        }).map(StackWalker.StackFrame::getDeclaringClass).findFirst().orElse(null));
        if(caller==null)return false;
        if(caller==TaskBridge.class||caller.getName().startsWith("dev.ronova.pro.bootstrap.TaskBridge$"))return true;
        if(caller==policyInstaller||caller==BackingBridge.class)return true;
        if(caller==SourceMapBridge.class||caller==NativeControl.class||caller==ControlBridge.class)return true;
        Guards current=guards;
        if(current!=null&&caller.getModule()==current.owner().getModule()&&caller.getClassLoader()==current.owner().getClassLoader()&&caller.getName().startsWith("dev.ronova.pro."))return true;
        if(caller.getClassLoader()==ClassLoader.getSystemClassLoader()&&caller.getName().startsWith("dev.ronova.pro.agent."))try {
            Class<?> agent=Class.forName("dev.ronova.pro.agent.RecoveryAgent",false,ClassLoader.getSystemClassLoader());
            return Objects.equals(caller.getProtectionDomain().getCodeSource(),agent.getProtectionDomain().getCodeSource());
        }catch(ClassNotFoundException missing){return false;}
        return false;
    }
    static boolean controlled(Object value) {
        if(value==null)return false;ControlRef[] controls=controlObjects;
        if(value==controls||value==controlBits)return true;
        int hash=System.identityHashCode(value)&65535;
        if((controlBits[hash>>>6]&(1L<<(hash&63)))==0)return false;
        for(ControlRef control:controls)if(control.get()==value)return true;return false;
    }
    public static boolean controlMutationAllowed(Object value) {
        if(!controlled(value))return true;
        if(SourceMapBridge.internal())return true;
        if((value==ADMISSIONS||value==SUBMISSIONS||value==HELD_TASKS||value==HELD_ARRAYS)
                &&OWN_LEDGER_MUTATION.get()!=null)return true;
        return controlCaller();
    }
    private static void inheritControl(Object parent,Object next) {
        if(next==null||!controlled(parent)||controlled(next)||!controlCaller())return;
        if(!(next.getClass().isArray()||next instanceof java.util.Map<?,?>||next instanceof java.util.Collection<?>))return;
        synchronized(TaskBridge.class) {if(!controlled(next))captureControls(new Object[]{next});}
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
    public static synchronized void registerAgentControls(Object[] roots) {
        if(!ControlBridge.owns(WALKER.getCallerClass()))throw new SecurityException("AGENT_CONTROL_OWNER");
        try {captureControls(roots);}
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
        for(ControlRef old:controlObjects)if(old.get()!=null)queue.add(old.get());for(Object root:roots)if(root!=null)queue.add(root);
        for(Class<?> type:java.util.List.of(TaskBridge.class,SourceMapBridge.class,BackingBridge.class))for(var field:type.getDeclaredFields())
            if(java.lang.reflect.Modifier.isStatic(field.getModifiers())&&(java.util.Map.class.isAssignableFrom(field.getType())||java.util.Collection.class.isAssignableFrom(field.getType()))&&field.trySetAccessible())
                try {Object value=field.get(null);if(value!=null)queue.add(value);}catch(IllegalAccessException impossible){throw new IllegalStateException(impossible);}
        while(!queue.isEmpty()&&found.size()<2048) {
            Object value=queue.removeFirst();if(!found.add(value)||value.getClass().isArray())continue;
            if(!(value instanceof java.util.Map<?,?>||value instanceof java.util.Collection<?>))continue;
            for(Class<?> type=value.getClass();type!=null&&type!=Object.class;type=type.getSuperclass())for(var field:type.getDeclaredFields())
                if(!java.lang.reflect.Modifier.isStatic(field.getModifiers())&&(field.getType().isArray()||java.util.Map.class.isAssignableFrom(field.getType())||java.util.Collection.class.isAssignableFrom(field.getType()))&&field.trySetAccessible())
                    try {Object child=field.get(value);if(child!=null)queue.add(child);}catch(IllegalAccessException unavailable){throw new IllegalStateException(unavailable);}
        }
        if(!queue.isEmpty()) {
            controlCaptureHealthy=false;
            throw new IllegalStateException("CONTROL_CAPTURE_CAPACITY");
        }
        Object[] published=found.toArray();
        for(Object value:published){int hash=System.identityHashCode(value)&65535;controlBits[hash>>>6]|=1L<<(hash&63);}
        ControlRef[] weak=new ControlRef[published.length];for(int i=0;i<published.length;i++)weak[i]=new ControlRef(published[i]);controlObjects=weak;
    }
    private static Class<?> policyInstaller,observerInstaller;
    private static Class<?> installingCaller() {
        return WALKER.walk(frames->frames.map(StackWalker.StackFrame::getDeclaringClass).filter(type->type!=TaskBridge.class).findFirst().orElse(TaskBridge.class)).getNestHost();
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
        java.util.concurrent.locks.ReentrantLock gate=fieldGate(map);
        gate.lock();
        boolean allowed;
        try { allowed=indexWriteAllowed(map,key,current,proposed); }
        catch(RuntimeException|Error failure) { gate.unlock();throw failure; }
        if(!allowed) { gate.unlock();return Boolean.FALSE; }
        return gate;
    }
    public static Object beginIndexNodeWrite(Object map,Object node,Object proposed) {
        Object owner=SourceMapBridge.ownerOf(node);
        java.util.concurrent.locks.ReentrantLock gate=fieldGate(owner==null?map:owner);
        gate.lock();
        boolean allowed;
        try {
            java.util.Map.Entry<?,?> entry=(java.util.Map.Entry<?,?>)node;
            allowed=indexWriteAllowed(map,entry.getKey(),entry.getValue(),proposed);
        } catch(RuntimeException|Error failure) { gate.unlock();throw failure; }
        if(!allowed) { gate.unlock();return Boolean.FALSE; }
        return gate;
    }
    public static Object beginIndexArrayWrite(Object map,Object key,Object[] values,int index,Object proposed) {
        java.util.concurrent.locks.ReentrantLock gate=fieldGate(map);
        gate.lock();
        boolean allowed;
        try { allowed=indexWriteAllowed(map,key,values[index],proposed); }
        catch(RuntimeException|Error failure) { gate.unlock();throw failure; }
        if(!allowed) { gate.unlock();return Boolean.FALSE; }
        return gate;
    }
    public static Object beginIndexRemoval(Object map,Object key,Object current) {
        java.util.concurrent.locks.ReentrantLock gate=fieldGate(map);
        gate.lock();
        boolean allowed;
        try { allowed=indexRemovalAllowed(map,key,current); }
        catch(RuntimeException|Error failure) { gate.unlock();throw failure; }
        if(!allowed) { gate.unlock();return Boolean.FALSE; }
        return gate;
    }
    public static Object beginIndexNodeRemoval(Object map,Object node) {
        java.util.concurrent.locks.ReentrantLock gate=fieldGate(map);
        gate.lock();
        boolean allowed;
        try {
            java.util.Map.Entry<?,?> entry=(java.util.Map.Entry<?,?>)node;
            allowed=indexRemovalAllowed(map,entry.getKey(),entry.getValue());
        } catch(RuntimeException|Error failure) { gate.unlock();throw failure; }
        if(!allowed) { gate.unlock();return Boolean.FALSE; }
        return gate;
    }
    public static Object beginIndexArrayRemoval(Object map,Object key,Object[] values,int index) {
        java.util.concurrent.locks.ReentrantLock gate=fieldGate(map);
        gate.lock();
        boolean allowed;
        try { allowed=indexRemovalAllowed(map,key,values[index]); }
        catch(RuntimeException|Error failure) { gate.unlock();throw failure; }
        if(!allowed) { gate.unlock();return Boolean.FALSE; }
        return gate;
    }
    public static void endIndexMutation(Object token) {
        if(token instanceof java.util.concurrent.locks.ReentrantLock gate)gate.unlock();
    }
    /** Primitive-key map writers contain no application callback in these exact methods. */
    public static Object beginIndexStructure(Object map) {
        Class<?> writer=WALKER.getCallerClass(),owner=map==null?null:map.getClass();
        if(owner==null||!owner.getName().startsWith("it.unimi.dsi.fastutil.")
                ||writer.getClassLoader()!=owner.getClassLoader()
                ||writer!=owner&&!writer.getName().equals(owner.getName()+"$MapIterator"))
            throw new SecurityException("INDEX_STRUCTURE_SCOPE_OWNER");
        java.util.concurrent.locks.ReentrantLock gate=fieldGate(map);
        gate.lock();
        return gate;
    }
    /** Only registered exact index maps and callback-free UUID/int keys can take this default-Map path. */
    public static Object deniedIndexCurrent(Object container,Object key,Object proposed) {
        Guards snapshot=guards;
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
        if(host==TaskBridge.class||host==BackingBridge.class||host==SourceMapBridge.class||host==NativeControl.class
                ||host==EventFaultBridge.class||host==ControlBridge.class)return true;
        if(ControlBridge.owns(declaring))return true;
        return current!=null&&declaring.getModule()==current.owner().getModule()&&declaring.getClassLoader()==current.owner().getClassLoader()&&declaring.getName().startsWith("dev.ronova.pro.");
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
        Guards current=guards;
        if(current==null)return true;
        if(receiver instanceof Class<?> owner&&ownState(owner)||receiver!=null&&ownState(receiver.getClass()))return false;
        if(receiver==null)return true; // Raw-address writes have no exact object binding here.
        if(!controlMutationAllowed(receiver)||!BackingBridge.offsetAllowed(receiver,offset,value))return false;
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
        Class<?> caller=WALKER.walk(frames->frames.map(StackWalker.StackFrame::getDeclaringClass)
                .filter(type->type!=TaskBridge.class&&type!=BackingBridge.class).findFirst().orElse(TaskBridge.class));
        return caller.getModule()==current.owner().getModule()&&caller.getClassLoader()==current.owner().getClassLoader()&&caller.getName().startsWith("dev.ronova.pro.");
    }
    private static final ThreadLocal<Boolean> FIELD_POLICY_ACTIVE=new ThreadLocal<>();
    // The gate directory must not itself use an observed Map: an index hook can
    // re-enter it while SourceMapBridge decides whether that Map may publish.
    private static final class FieldGateNode extends java.lang.ref.WeakReference<Object> {
        final int hash;
        final java.util.concurrent.locks.ReentrantLock gate=new java.util.concurrent.locks.ReentrantLock();
        FieldGateNode next;
        FieldGateNode(Object receiver,int hash,java.lang.ref.ReferenceQueue<Object> queue,FieldGateNode next) {
            super(receiver,queue);this.hash=hash;this.next=next;
        }
    }
    private static final class FieldGateBucket {
        final java.lang.ref.ReferenceQueue<Object> retired=new java.lang.ref.ReferenceQueue<>();
        // TreeMap is not a monitored HashMap/ConcurrentHashMap source. The exact
        // identity hash gives logarithmic lookup; rare collisions use a short chain.
        final java.util.TreeMap<Integer,FieldGateNode> byHash=new java.util.TreeMap<>();
        synchronized java.util.concurrent.locks.ReentrantLock get(Object receiver) {
            for(int i=0;i<16;i++) {
                FieldGateNode dead=(FieldGateNode)retired.poll();if(dead==null)break;
                FieldGateNode previous=null,node=byHash.get(dead.hash);
                while(node!=null&&node!=dead){previous=node;node=node.next;}
                if(node==null)continue;
                if(previous!=null)previous.next=node.next;
                else if(node.next==null)byHash.remove(dead.hash);
                else byHash.put(dead.hash,node.next);
            }
            int hash=System.identityHashCode(receiver);
            FieldGateNode first=byHash.get(hash);
            for(FieldGateNode node=first;node!=null;node=node.next)if(node.get()==receiver)return node.gate;
            FieldGateNode added=new FieldGateNode(receiver,hash,retired,first);
            byHash.put(hash,added);return added.gate;
        }
    }
    private static final FieldGateBucket[] FIELD_GATES=new FieldGateBucket[1024];
    static {for(int i=0;i<FIELD_GATES.length;i++)FIELD_GATES[i]=new FieldGateBucket();}
    private static java.util.concurrent.locks.ReentrantLock fieldGate(Object receiver) {
        if(receiver==null)throw new IllegalArgumentException("INDEX_GATE_RECEIVER_REQUIRED");
        return FIELD_GATES[System.identityHashCode(receiver)&(FIELD_GATES.length-1)].get(receiver);
    }
    /** A selected field mutation holds the same short gate as the target's policy publication. */
    public static Object beginFieldMutation(Object receiver,Class<?> symbolic,String name,String descriptor,Object value) {
        java.util.concurrent.locks.ReentrantLock gate=fieldGate(receiver==null?symbolic:receiver);gate.lock();
        boolean allowed;
        try {allowed=fieldWriteAllowed(receiver,symbolic,name,descriptor,value);}
        catch(RuntimeException|Error failure){gate.unlock();throw failure;}
        if(!allowed){gate.unlock();return Boolean.FALSE;}
        return gate;
    }
    public static void endFieldMutation(Object token) {
        if(token instanceof java.util.concurrent.locks.ReentrantLock gate)gate.unlock();
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
        return FIELD_DECLARING.get(symbolic).computeIfAbsent(key,ignored->{
            for(Class<?> type=symbolic;type!=null;type=type.getSuperclass())
                for(java.lang.reflect.Field field:type.getDeclaredFields())
                    if(field.getName().equals(name)&&field.getType().descriptorString().equals(descriptor))return type;
            return Void.class;
        });
    }
    /** Symbolic owners can name an inherited field; resolve its actual declaring class from the live definition. */
    public static boolean fieldWriteAllowed(Object receiver,Class<?> symbolic,String name,String descriptor,Object value) {
        Class<?> actual=actualFieldOwner(symbolic,name,descriptor);
        if(actual==null||actual==Void.class){lost();return false;}
        return fieldWriteAllowed(receiver,actual,name,value);
    }
    public static boolean staticFieldWriteAllowed(Class<?> symbolic,String name,String descriptor,Object value) {
        Class<?> actual=actualFieldOwner(symbolic,name,descriptor);
        if(actual==null||actual==Void.class){lost();return false;}
        return staticFieldWriteAllowed(actual,name,value);
    }
    public static boolean fieldWriteAllowed(Object receiver,Class<?> declaring,String name,Object value) {
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
        Guards current=guards;var guard=current==null?null:current.dispatch();
        if(guard==null||receiver==null)return true;
        try { return !(boolean)guard.invokeExact(receiver,operation,value); }
        catch(Throwable failure) { throw new IllegalStateException("DISPATCH_POLICY_FAILED",failure); }
    }
    private static boolean taskEffectAllowed(Guards current,Object task,String effect) {
        if(task!=null&&(modStopped(task.getClass().getModule())||stoppedInvocation()))return false;
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
            var top=type.getDeclaredField("top");var base=type.getDeclaredField("base");
            if(!remove.trySetAccessible()||!top.trySetAccessible()||!base.trySetAccessible())return;
            for(QueueRelease request:pending) {
                synchronized(TaskBridge.class) {
                    if(request.retired||request.working||request.owner!=registration||System.nanoTime()<request.retryAfter)continue;
                    request.working=true;request.retryAfter=System.nanoTime()+50_000_000L;
                }
                boolean[] ran={false},removed={false};
                try {
                    request.gate.test(()->{
                        try {
                            if(request.cancelled||Thread.currentThread()!=owner)return;
                            int size=top.getInt(queue)-base.getInt(queue);if(size<0||size>4096)return;
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
        if(modStopped(task.getClass().getModule())||stoppedInvocation())return BLOCKED;
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
        emit(current,SUBMIT,task,scheduler(scheduler,task),frame==null?null:frame.task,frame);
    }
    /** Publication spans the entire platform submission, including rejection/CallerRuns. */
    public static Object beginSubmission(Object scheduler,Object task) {
        try {
            Registration current=registration;
            if(task==null||DELIVERING.get()!=null||!boundary(true))return null;
            Frame frame=currentFrame(current);
            var token=new Submission(task,scheduler(scheduler,task),frame==null?null:frame.task,current);
            synchronized(TaskBridge.class) {
                if(submissionEntries>=ADMISSION_LIMIT)lost();
                else {
                    boolean nested=OWN_LEDGER_MUTATION.get()!=null;OWN_LEDGER_MUTATION.set(true);
                    try {SUBMISSIONS.merge(task,1,Integer::sum);}finally {if(!nested)OWN_LEDGER_MUTATION.remove();}
                    submissionEntries++;token.tracked=true;
                }
            }
            emit(current,SUBMIT_ENTER,task,token.scheduler,token.parent,token);return token;
        } catch(Throwable unavailable){lost();return null;}
    }
    public static void endSubmission(Object value) {
        try {
            if(!(value instanceof Submission token)||token.owner!=Thread.currentThread()||token.ended||!boundary(true))return;
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
            try { emit(token.registration,SUBMIT_EXIT,token.task,token.scheduler,token.parent,token); }
            finally { token.task=null;token.scheduler=null;token.parent=null;token.owner=null; }
        } catch(Throwable unavailable){lost();}
    }
    private static final class Submission {
        Object task,scheduler,parent;
        Thread owner=Thread.currentThread();
        final java.lang.ref.WeakReference<Object> identity;
        final Registration registration;
        boolean tracked;
        volatile boolean ended;
        Submission(Object task,Object scheduler,Object parent,Registration registration) {
            this.task=task;this.scheduler=scheduler;this.parent=parent;this.registration=registration;
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
