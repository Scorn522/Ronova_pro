package dev.ronova.pro.bootstrap;

import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.ClassDefinition;
import java.lang.reflect.Field;
import java.lang.reflect.Array;
import java.lang.reflect.Method;
import java.lang.reflect.InvocationTargetException;
import java.util.*;
import static dev.ronova.pro.bootstrap.TaskBridge.*;

/** Bootstrap effect policy, source/creation state and exact Instrumentation registrations. */
public final class ControlBridge {
    private static final StackWalker INSTRUMENTATION_CALLER=StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE);
    private static volatile Class<?> instrumentationOwner;
    private static volatile ClassFileTransformer[] protectedTransformers=new ClassFileTransformer[0];
    private static volatile Field transformerField;
    private static volatile Method definitionsChanged;
    private static volatile boolean installed;
    // Only immutable Class loader/name/ProtectionDomain facts are cached.
    // Every operation still selects its actual current writer from the stack.
    private static final ClassValue<Boolean> AGENT_WRITERS=new ClassValue<>(){
        @Override protected Boolean computeValue(Class<?> caller){
            try{
                Class<?> agent=Class.forName("dev.ronova.pro.agent.RecoveryAgent",false,ClassLoader.getSystemClassLoader());
                return Objects.equals(caller.getProtectionDomain().getCodeSource(),agent.getProtectionDomain().getCodeSource());
            }catch(ClassNotFoundException missing){return false;}
        }
    };
    private ControlBridge() { }
    public static int abiVersion(){return 89;}
    public static long arrayRevision(Object value){if(!recoveryWriter())throw new SecurityException("RECOVERY_ARRAY_READER_REQUIRED");return CodeSourceBridge.executionArrayRevision(value);}

    public static synchronized void install(Class<?> agent,ClassFileTransformer[] transformers) throws ReflectiveOperationException {
        Class<?> caller=INSTRUMENTATION_CALLER.getCallerClass();
        if(caller!=agent||agent.getClassLoader()!=ClassLoader.getSystemClassLoader()
                ||!agent.getName().equals("dev.ronova.pro.agent.RecoveryAgent")||instrumentationOwner!=null&&instrumentationOwner!=agent)
            throw new SecurityException("CONTROL_INSTALLER");
        Class<?> info=Class.forName("sun.instrument.TransformerManager$TransformerInfo",false,null);
        Field field=info.getDeclaredField("mTransformer");
        if(!field.trySetAccessible())throw new IllegalAccessException("TRANSFORMER_INFO_ACCESS");
        Method changed=agent.getMethod("externalDefinitionsChanged",Class[].class);
        if(instrumentationOwner==null)NativeControl.heapPolicyChanged(null);
        instrumentationOwner=agent;transformerField=field;definitionsChanged=changed;ClassFileTransformer[] next=transformers.clone();
        ControlBridge.controlPublication(next,new Object[]{next,changed});ControlBridge.controlPublication(next,next);protectedTransformers=next;
    }
    /** Called only after the original Instrumentation entry completed successfully. */
    public static void definitionsChanged(Object[] changes){
        Class<?> caller=INSTRUMENTATION_CALLER.getCallerClass();
        if(caller.getClassLoader()!=null||!caller.getName().equals("sun.instrument.InstrumentationImpl"))
            throw new SecurityException("ACTUAL_INSTRUMENTATION_COMPLETION_REQUIRED");
        Class<?>[] classes;
        if(changes instanceof Class<?>[] actuals)classes=actuals.clone();
        else if(changes instanceof ClassDefinition[] definitions){
            classes=new Class<?>[definitions.length];for(int i=0;i<classes.length;i++)classes[i]=definitions[i].getDefinitionClass();
        }else throw new IllegalArgumentException("ACTUAL_DEFINITION_ARRAY_REQUIRED");
        Method callback=definitionsChanged;if(callback==null)return;
        deliverDefinitionsChanged(callback,classes);
    }
    /** NativeControl has consumed this thread's exact successful JVMTI scope. */
    static boolean nativeDefinitionsChanged(Class<?>[] classes){
        if(INSTRUMENTATION_CALLER.getCallerClass()!=NativeControl.class)
            throw new SecurityException("ACTUAL_JVMTI_COMPLETION_REQUIRED");
        Method callback=definitionsChanged;if(callback==null)return false;
        deliverDefinitionsChanged(callback,classes);return true;
    }
    private static void deliverDefinitionsChanged(Method callback,Class<?>[] classes){
        try{callback.invoke(null,(Object)classes);}
        catch(InvocationTargetException failure){
            Throwable cause=failure.getCause();if(cause instanceof RuntimeException runtime)throw runtime;if(cause instanceof Error error)throw error;throw new IllegalStateException(cause);
        }catch(ReflectiveOperationException failure){throw new IllegalStateException("EXTERNAL_DEFINITION_COMPLETION_UNAVAILABLE",failure);}
    }
    /** Keep the original native call and its completion on the same private argument snapshot. */
    public static Object[] definitionArguments(Object[] changes){
        Class<?> caller=INSTRUMENTATION_CALLER.getCallerClass();
        if(caller.getClassLoader()!=null||!caller.getName().equals("sun.instrument.InstrumentationImpl"))
            throw new SecurityException("ACTUAL_INSTRUMENTATION_ARGUMENTS_REQUIRED");
        if(changes==null)return null;
        if(changes instanceof Class<?>[] actuals)return actuals.clone();
        if(changes instanceof ClassDefinition[] definitions){
            ClassDefinition[] actuals=new ClassDefinition[definitions.length];
            for(int i=0;i<actuals.length;i++){
                ClassDefinition definition=definitions[i];
                if(definition!=null)actuals[i]=new ClassDefinition(definition.getDefinitionClass(),definition.getDefinitionClassFile().clone());
            }
            return actuals;
        }
        throw new IllegalArgumentException("ACTUAL_DEFINITION_ARRAY_REQUIRED");
    }
    public static boolean owns(Class<?> type) {
        Class<?> agent=instrumentationOwner;
        return agent!=null&&type!=null&&type.getClassLoader()==agent.getClassLoader()
                &&DefinitionBridge.module(type)==agent.getModule()&&type.getName().startsWith("dev.ronova.pro.agent.")
                &&Objects.equals(type.getProtectionDomain().getCodeSource(),agent.getProtectionDomain().getCodeSource());
    }
    public static boolean removalAllowed(ClassFileTransformer transformer) {
        for(ClassFileTransformer own:protectedTransformers)if(own==transformer)return false;
        return true;
    }
    /** Executed on the manager's real publication, under its existing monitor, before any new snapshot is visible. */
    public static Object order(Object proposed) {
        Field field=transformerField;ClassFileTransformer[] ours=protectedTransformers;
        if(field==null||ours.length==0||proposed==null)return proposed;
        int length=Array.getLength(proposed),out=0;
        Object result=Array.newInstance(proposed.getClass().getComponentType(),length);
        try {
            for(int i=0;i<length;i++) {
                Object info=Array.get(proposed,i);if(info==null)continue;
                if(removalAllowed((ClassFileTransformer)field.get(info)))Array.set(result,out++,info);
            }
            for(ClassFileTransformer own:ours)for(int i=0;i<length;i++) {
                Object info=Array.get(proposed,i);
                if(info!=null&&field.get(info)==own)Array.set(result,out++,info);
            }
        }catch(IllegalAccessException impossible){throw new IllegalStateException("CONTROL_ORDER_ACCESS",impossible);}
        if(out!=length)throw new IllegalStateException("CONTROL_ORDER_LAYOUT");
        var controlled=new ArrayList<Object>();
        for(int i=0;i<length;i++)try {
            Object entry=Array.get(result,i);if(!removalAllowed((ClassFileTransformer)field.get(entry)))controlled.add(entry);
        }catch(IllegalAccessException impossible){throw new IllegalStateException(impossible);}
        if(!controlled.isEmpty())ControlBridge.controlPublication(result,controlled.toArray());
        return result;
    }
    public static void installed() {
        if(!owns(INSTRUMENTATION_CALLER.getCallerClass()))throw new SecurityException("CONTROL_INSTALLER");
        installed=true;
    }
    public static boolean ready(){return installed;}


    static final Module[] NO_SOURCES=new Module[0];
    private static final Module[] QUERY_UNKNOWN_SOURCES=new Module[]{null};
    private static final ThreadLocal<SourceQuery> SOURCE_QUERIES=new ThreadLocal<>();
    private static final class SourceQuery {
        final Thread thread=Thread.currentThread();Module[] partial=QUERY_UNKNOWN_SOURCES;
        void retain(Module[] incoming){
            if(incoming==null||incoming.length==0)return;int additions=0;
            for(int i=0;i<incoming.length;i++){
                Module source=incoming[i];if(source==null)continue;boolean known=false;
                for(Module retained:partial)if(retained==source){known=true;break;}
                for(int j=0;j<i&&!known;j++)known=incoming[j]==source;
                if(!known)additions++;
            }
            if(additions==0)return;Module[] next=new Module[partial.length+additions];int at=0;
            // A source query may be observing arraycopy itself. Its partial
            // snapshot must not perform another copy to retain the old sources.
            for(Module source:partial)next[at++]=source;
            for(Module source:incoming){if(source==null)continue;boolean known=false;for(int i=0;i<at;i++)if(next[i]==source){known=true;break;}if(!known)next[at++]=source;}
            partial=next;CodeSourceBridge.mutationControls((Object)next);
        }
    }
    private static SourceQuery beginSourceQuery(Module[] retained){
        SourceQuery query=new SourceQuery();SOURCE_QUERIES.set(query);
        try{query.retain(retained);CodeSourceBridge.mutationControls(query,SOURCE_QUERIES,query.partial);return query;}
        catch(RuntimeException|Error failure){SOURCE_QUERIES.remove();throw failure;}
    }
    private static void endSourceQuery(SourceQuery query){
        if(query.thread!=Thread.currentThread()||SOURCE_QUERIES.get()!=query)throw new IllegalStateException("SOURCE_QUERY_THREAD_AND_ORDER_REQUIRED");
        SOURCE_QUERIES.remove();
    }
    static boolean sourceQueryActive(){SourceQuery query=SOURCE_QUERIES.get();return WALKER.getCallerClass()==NativeControl.class&&query!=null&&query.thread==Thread.currentThread();}
    static final int MAX_DEPTH=128;
    static final StackWalker WALKER=StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE);
    static final StackWalker ORIGIN_WALKER=StackWalker.getInstance(Set.of(StackWalker.Option.RETAIN_CLASS_REFERENCE,StackWalker.Option.SHOW_HIDDEN_FRAMES));
    static final java.util.function.Function<java.util.stream.Stream<StackWalker.StackFrame>,Module> INVOKING=new Invoking();
    static final java.util.function.Function<java.util.stream.Stream<StackWalker.StackFrame>,Boolean> STOPPED_CALL=new StoppedCall();
    static final java.util.function.Function<java.util.stream.Stream<StackWalker.StackFrame>,Class<?>> MEMORY_CALLER=new MemoryCaller();
    static final java.util.function.Function<java.util.stream.Stream<StackWalker.StackFrame>,Class<?>> RAW_MEMORY_CALLER=new RawMemoryCaller();
    static final java.util.function.Function<java.util.stream.Stream<StackWalker.StackFrame>,Class<?>> CORE_CALLER=new CoreCaller();
    static final java.util.function.Function<java.util.stream.Stream<StackWalker.StackFrame>,Module[]> INVOKING_SOURCES=new InvokingSources();
    static final java.util.function.Function<java.util.stream.Stream<StackWalker.StackFrame>,Class<?>> CONTROL_WRITER_CALLER=new ControlWriterCaller();
    static final java.util.function.Function<java.util.stream.Stream<StackWalker.StackFrame>,Boolean> CLASS_REFLECTION_WRITER=new ClassReflectionWriter();
    static final java.util.function.Function<java.util.stream.Stream<StackWalker.StackFrame>,Boolean> FIELD_GATE_WRITER=new FieldGateWriter();
    // These queries run from Unsafe guards. Linking a lambda here can itself
    // enter the same guard through MethodType's ConcurrentHashMap lookup.
    static final class MemoryCaller implements java.util.function.Function<java.util.stream.Stream<StackWalker.StackFrame>,Class<?>> {
        public Class<?> apply(java.util.stream.Stream<StackWalker.StackFrame> frames){
            var cursor=frames.iterator();while(cursor.hasNext()){
                Class<?> type=cursor.next().getDeclaringClass();if(type!=TaskBridge.class&&type!=ControlBridge.class)return type;
            }return null;
        }
    }
    static final class ClassReflectionWriter implements java.util.function.Function<java.util.stream.Stream<StackWalker.StackFrame>,Boolean> {
        public Boolean apply(java.util.stream.Stream<StackWalker.StackFrame> frames){
            var cursor=frames.iterator();StackWalker.StackFrame frame=null;
            while(cursor.hasNext()){frame=cursor.next();if(frame.getDeclaringClass()!=TaskBridge.class&&frame.getDeclaringClass()!=ControlBridge.class)break;frame=null;}
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
    static final class FieldGateWriter implements java.util.function.Function<java.util.stream.Stream<StackWalker.StackFrame>,Boolean>{
        public Boolean apply(java.util.stream.Stream<StackWalker.StackFrame> frames){
            var cursor=frames.iterator();boolean locking=false;
            while(cursor.hasNext()){
                Class<?> type=cursor.next().getDeclaringClass(),host=type.getClassLoader()==null?type.getNestHost():type;
                if(!locking&&(host==TaskBridge.class||host==ControlBridge.class))continue;
                if(type.getClassLoader()==null&&type.getModule()==Object.class.getModule()){
                    if(host==java.util.concurrent.locks.ReentrantLock.class)locking=true;
                    String name=type.getName();
                    if(name.startsWith("java.")||name.startsWith("jdk.")||name.startsWith("sun."))continue;
                }
                // Native memory callbacks may have no application frame below
                // the bridge. Authenticate the actual JDK lock operation and
                // its immediate controller caller, not the callback wrapper.
                return locking&&(host==TaskBridge.class||host==ControlBridge.class||host==ExecutionFlow.class);
            }
            return false;
        }
    }
    static final class RawMemoryCaller implements java.util.function.Function<java.util.stream.Stream<StackWalker.StackFrame>,Class<?>> {
        public Class<?> apply(java.util.stream.Stream<StackWalker.StackFrame> frames){
            var cursor=frames.iterator();while(cursor.hasNext()){
                Class<?> type=cursor.next().getDeclaringClass();
                if(rawMemoryWriterFrame(type,null))return type;
            }return null;
        }
    }
    static final class CoreCaller implements java.util.function.Function<java.util.stream.Stream<StackWalker.StackFrame>,Class<?>> {
        public Class<?> apply(java.util.stream.Stream<StackWalker.StackFrame> frames){
            var cursor=frames.iterator();while(cursor.hasNext()){
                Class<?> type=cursor.next().getDeclaringClass();if(type.getClassLoader()!=null)return type;
            }return TaskBridge.class;
        }
    }
    static final class InvokingSources implements java.util.function.Function<java.util.stream.Stream<StackWalker.StackFrame>,Module[]> {
        public Module[] apply(java.util.stream.Stream<StackWalker.StackFrame> frames){
            var cursor=frames.iterator();while(cursor.hasNext()){
                var frame=cursor.next();Module[] sources=CodeSourceBridge.frameSources(frame);if(sources.length!=0)return retainQuerySources(sources);
                sources=producerRootSources(frame);if(sources.length!=0)return retainQuerySources(sources);
                Module physical=DefinitionBridge.module(frame.getDeclaringClass());if(producer(physical))return retainQuerySources(new Module[]{physical});
            }
            Frame task=CURRENT.get();
            if(task==null||task.exited||DELIVERING.get()!=null||CREATION_DELIVERY.get()!=null||PRODUCER_DELIVERY.get()!=null||OWN_LEDGER_MUTATION.get()!=null)return NO_SOURCES;
            retainQuerySources(task.sources);
            return task.sources.clone();
        }
    }
    static final class ControlWriterCaller implements java.util.function.Function<java.util.stream.Stream<StackWalker.StackFrame>,Class<?>> {
        public Class<?> apply(java.util.stream.Stream<StackWalker.StackFrame> frames){
            var cursor=frames.iterator();while(cursor.hasNext()){
                var frame=cursor.next();Class<?> type=frame.getDeclaringClass();
                if(controlWriterFrame(type,frame.getMethodName()))return type;
            }return null;
        }
    }
    // Shared by the Java fallback and the live JVMTI frame reader.
    static boolean controlWriterFrame(Class<?> type,String method){
        String name=type.getName();
        if(type==CodeSourceBridge.class&&method.equals("executionEquipmentCallback"))return false;
        if(type==NativeControl.class&&(method.equals("controlWriterCaller")||method.equals("controlWriter0")
                ||method.equals("heapPolicyChanged")||method.equals("heapPolicyChanged0")
                ||method.equals("jvmtiDefinitionsChanged")||method.equals("jvmtiDefinitionCompletion0")
                ||method.equals("jvmtiDefinitionsPending0")))return false;
        if(type==TaskBridge.class||type==ControlBridge.class||name.startsWith("dev.ronova.pro.bootstrap.TaskBridge$")||name.startsWith("dev.ronova.pro.bootstrap.ControlBridge$"))
            return !EXTERNAL_GUARD_ENTRIES.contains(method)
                    &&!(name.equals("dev.ronova.pro.bootstrap.ControlBridge$ControlRef")&&(method.equals("clear")||method.equals("enqueue")));
        if(name.equals("dev.ronova.pro.bootstrap.ResourceBridge$BufferWrapWriter"))return false;
        if(type==BackingBridge.class)return method.equals("readUnsafe");
        if(type==ResourceBridge.class)return !(method.equals("mutationAllowed")||method.equals("critical")||method.equals("fieldMutationAllowed")||method.equals("unsafeMutationAllowed")||method.equals("bufferWrapMutationAllowed"));
        if(type==SourceMapBridge.class)return !(method.equals("valueAllowed")||method.equals("checkIncoming")||method.equals("internal")||method.equals("changed"));
        return !name.startsWith("java.")&&!name.startsWith("jdk.")&&!name.startsWith("sun.");
    }
    static final class StoppedCall implements java.util.function.Function<java.util.stream.Stream<StackWalker.StackFrame>,Boolean> {
        public Boolean apply(java.util.stream.Stream<StackWalker.StackFrame> frames){
            SourceQuery previous=SOURCE_QUERIES.get(),query=previous==null?beginSourceQuery(NO_SOURCES):previous;
            try{
                var cursor=frames.iterator();while(cursor.hasNext()){
                    var frame=cursor.next();if(modStopped(DefinitionBridge.module(frame.getDeclaringClass())))return true;
                    for(Module source:retainQuerySources(CodeSourceBridge.frameSources(frame)))if(modStopped(source))return true;
                }return false;
            }finally{if(previous==null)endSourceQuery(query);}
        }
    }
    static final class Invoking implements java.util.function.Function<java.util.stream.Stream<StackWalker.StackFrame>,Module> {
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
    static volatile Module[] stoppedModules=new Module[0];
    static final class ThreadIo {
        final Thread caller=Thread.currentThread();final ThreadOrigin origin;final boolean clearing;boolean closed;
        ThreadIo(ThreadOrigin origin,boolean clearing){this.origin=origin;this.clearing=clearing;}
    }
    static final java.util.Set<Module> PRODUCER_MODULES=java.util.Collections.newSetFromMap(new java.util.WeakHashMap<Module,Boolean>());
    public static void registerProducerModule(Module module) {
        if(!ControlBridge.owns(WALKER.getCallerClass()))throw new SecurityException("PRODUCER_AGENT_REQUIRED");
        synchronized(PRODUCER_MODULES) {
            if(!PRODUCER_MODULES.contains(module)){NativeControl.heapPolicyChanged(null);PRODUCER_MODULES.add(module);}
        }
    }
    static Module invokingModule() {
        // A singleton cannot represent an incomplete query. Its caller still
        // observes sourceUnknown(), while the actual outer query keeps running.
        if(SOURCE_QUERIES.get()!=null)return null;
        SourceQuery query=beginSourceQuery(NO_SOURCES);
        try{return ORIGIN_WALKER.walk(INVOKING);}finally{endSourceQuery(query);}
    }
    static boolean producer(Module module) {synchronized(PRODUCER_MODULES){return PRODUCER_MODULES.contains(module);}}
    static Object nativeThreadOperation(Thread thread,Module[] sources,int operation)throws ReflectiveOperationException {
        if(!NativeControl.threadOperationBoundary(thread,sources,operation))throw new SecurityException("ACTUAL_NATIVE_THREAD_OPERATION_REQUIRED");
        if(operation==1)return beginThreadStart(thread,sources);
        if(operation==2)return beginThreadInterrupt(thread,sources);
        requireThreadControl(thread,operation,sources);return null;
    }
    static void nativeThreadOperationFinished(Object token,boolean completed){
        if(!NativeControl.threadOperationFinishing(token,completed))throw new SecurityException("ACTUAL_NATIVE_THREAD_FINISH_REQUIRED");
        if(token==null||token==Boolean.FALSE)return;
        if(token instanceof ThreadStart)finishThreadStart(token);else finishThreadInterrupt(token);
    }
    static ThreadIo threadIoToken(Object value){
        if(!(value instanceof ThreadIo token)||token.caller!=Thread.currentThread()||!token.closed&&!token.origin.io.isHeldByCurrentThread())
            throw new SecurityException("ACTUAL_THREAD_IO_WINDOW_REQUIRED");return token;
    }
    public static boolean modOwnerAllowed(Object owner) {
        // This predicate governs stopped modules only. Read the current volatile
        // policy before resolving ownership; an empty policy cannot deny an
        // owner or an invocation. Field/index protection uses its own gates.
        if(stoppedModules.length==0)return true;
        Module module=objectModule(owner);
        return !modStopped(module)&&(!stoppedInvocation()||IoBridge.retirementCloseOwner(owner)||ResourceBridge.bufferCleanupOwner(owner));
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
        if(next.size()!=existing.length){NativeControl.heapPolicyChanged(null);stoppedModules=next.toArray(Module[]::new);}
    }
    static boolean hasStoppedModules(){return stoppedModules.length!=0;}
    static boolean modStopped(Module module) {
        for(Module entry:stoppedModules)if(entry==module)return true;
        return false;
    }
    static boolean stoppedInvocation() {
        if(stoppedModules.length==0)return false;
        SourceQuery previous=SOURCE_QUERIES.get(),query=previous==null?beginSourceQuery(NO_SOURCES):previous;
        try{
            if(NetworkBridge.stoppedSource())return true;
            if(ClientBridge.stoppedSource())return true;
            for(Module source:retainQuerySources(CodeSourceBridge.executionSources()))if(modStopped(source))return true;
            for(Module source:query.partial)if(modStopped(source))return true;
            // The outer stop query can resize its Java walker just like a
            // reentrant one. The installed reader observes the same actual
            // stack and dynamic frames without copying a Java frame batch.
            // Only an outer Java-only query has the original walker fallback;
            // an unavailable nested reader remains an observation failure.
            return NativeControl.available()||previous!=null?NativeControl.sourceQueryStopped():ORIGIN_WALKER.walk(STOPPED_CALL);
        }finally{if(previous==null)endSourceQuery(query);}
    }
    record CreationDefinition(java.lang.ref.WeakReference<ClassLoader> loader,boolean bootstrap,String name,Set<String> sites,ControlRef actual) { }
    record CreationObserver(Class<?> owner,java.lang.invoke.MethodHandle callback,java.lang.invoke.MethodHandle failure) { }
    static final List<CreationDefinition> CREATIONS=new ArrayList<>();
    static final ThreadLocal<Class<?>> CREATION_DELIVERY=new ThreadLocal<>();
    static final StackWalker CREATION_CALLER=StackWalker.getInstance(Set.of(StackWalker.Option.RETAIN_CLASS_REFERENCE,StackWalker.Option.SHOW_HIDDEN_FRAMES));
    static volatile CreationObserver creationObserver;
    static final ClassValue<Set<String>> CREATION_SITES=new ClassValue<>() {
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
    static final class ProducerFrame {
        final ProducerFrame parent=PRODUCERS.get();final Thread thread=Thread.currentThread();
        final Class<?> declaring;final String selector;final Module module;final Module[] sources;
        java.util.Set<Object> inputs;java.util.List<Object[]> fields;java.util.Set<Object> created;Object receiver;
        boolean closed,overflow;
        ProducerFrame(StackWalker.StackFrame actual){
            declaring=actual.getDeclaringClass();selector=actual.getMethodName()+actual.getDescriptor();module=DefinitionBridge.module(declaring);
            // Declaration is a root fact. Instruction contributors remain a
            // live CodeSourceBridge query and are never frozen at method entry.
            sources=new Module[]{module};
        }
        java.util.Set<Object> inputs(){if(inputs==null)inputs=new java.util.HashSet<>();return inputs;}
        java.util.List<Object[]> fields(){if(fields==null)fields=new java.util.ArrayList<>();return fields;}
        java.util.Set<Object> created(){if(created==null)created=java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());return created;}
    }
    static final ThreadLocal<ProducerFrame> PRODUCERS=new ThreadLocal<>();
    static volatile java.lang.invoke.MethodHandle producerObserver;
    static final ThreadLocal<Class<?>> PRODUCER_DELIVERY=new ThreadLocal<>();
    static final ThreadLocal<Boolean> PRODUCER_METHOD_RECEIVER=new ThreadLocal<>();
    public static boolean deliveringProducerTo(Class<?> owner) {return PRODUCER_DELIVERY.get()==owner;}
    public static boolean deliveringProducerReceiverTo(Class<?> owner) {return PRODUCER_DELIVERY.get()==owner&&PRODUCER_METHOD_RECEIVER.get()!=null;}
    public static synchronized void installProducerObserver(Class<?> owner,java.lang.invoke.MethodHandle observer) {
        requirePolicyInstaller();
        if(guards==null||guards.owner()!=owner)throw new IllegalArgumentException("PRODUCER_OWNER");
        producerObserver=observer.asType(java.lang.invoke.MethodType.methodType(Object.class,Object.class,Object.class));
    }
    static boolean producerSite() {
        var frame=CREATION_CALLER.walk(stream->stream.skip(2).findFirst().orElse(null));
        return frame!=null&&CREATION_SITES.get(frame.getDeclaringClass()).contains(frame.getMethodName()+frame.getDescriptor()+":"+frame.getByteCodeIndex());
    }
    public static Object producerEnter() {
        var actual=CREATION_CALLER.walk(stream->stream.skip(1).findFirst().orElse(null));
        if(actual==null||!CREATION_SITES.get(actual.getDeclaringClass()).contains(actual.getMethodName()+actual.getDescriptor()+":"+actual.getByteCodeIndex()))return null;
        ProducerFrame parent=PRODUCERS.get();int depth=0;
        for(var p=parent;p!=null;p=p.parent)if(++depth>=128) {parent.overflow=true;return null;}
        // Capture the actual production root even before Core installs its
        // input observer. No business graph or disposal authority is created.
        ProducerFrame frame=new ProducerFrame(actual);PRODUCERS.set(frame);return frame;
    }
    static Module[] producerRootSources(StackWalker.StackFrame actual){
        for(ProducerFrame frame=PRODUCERS.get();frame!=null;frame=frame.parent)
            if(!frame.closed&&actual.getDeclaringClass()==frame.declaring
                    &&(actual.getMethodName()+actual.getDescriptor()).equals(frame.selector)
                    &&DefinitionBridge.module(frame.declaring)==frame.module&&producer(frame.module))return frame.sources.clone();
        return NO_SOURCES;
    }
    public static void producerInput(Object receiver,Object value) {
        ProducerFrame frame=PRODUCERS.get();var observer=producerObserver;
        if(frame==null||observer==null||value==null||PRODUCER_DELIVERY.get()!=null||!producerSite())return;
        PRODUCER_DELIVERY.set(guards.owner());
        try {
            Object origin=(Object)observer.invokeExact(receiver,value);
            if(origin!=null) {if(frame.inputs().size()<64)frame.inputs.add(origin);else frame.overflow=true;}
        } catch(Throwable unavailable) {frame.overflow=true;lost();}
        finally {PRODUCER_DELIVERY.remove();}
    }
    /** Captured only at woven field reads; boxed scalars remain values, never guessed subjects. */
    public static void producerFieldInput(Object receiver,Class<?> declaring,String name,String descriptor,Object value) {
        ProducerFrame frame=PRODUCERS.get();var observer=producerObserver;
        if(frame==null||observer==null||receiver==null||PRODUCER_DELIVERY.get()!=null||!producerSite())return;
        if(frame.fields().size()>=64) {frame.overflow=true;return;}
        Object[] field={receiver,declaring,name,descriptor,value};frame.fields.add(field);
        PRODUCER_DELIVERY.set(guards.owner());
        try {
            Object origin=(Object)observer.invokeExact(receiver,(Object)field);
            if(origin!=null) {if(frame.inputs().size()<64)frame.inputs.add(origin);else frame.overflow=true;}
        }catch(Throwable unavailable){frame.overflow=true;lost();}
        finally {PRODUCER_DELIVERY.remove();}
    }
    public static Object[][] producerFields() {
        Guards current=guards;if(current==null||CREATION_CALLER.getCallerClass()!=current.owner())return new Object[0][];
        java.util.List<Object[]> fields=new java.util.ArrayList<>();
        for(var frame=PRODUCERS.get();frame!=null;frame=frame.parent) {
            if(frame.overflow)return new Object[0][];
            if(frame.fields!=null)for(Object[] field:frame.fields)fields.add(field.clone());
        }
        return fields.toArray(Object[][]::new);
    }
    /** The agent supplies a proven field-to-setUUID dataflow at this actual assignment site. */
    public static void producerIdentityAssigned(Object entity,Object holder,Class<?> owner,String field,Object uuid,boolean nullable) {
        var frame=PRODUCERS.get();var observer=producerObserver;
        if(frame==null||observer==null||!producerSite()||frame.created==null||!frame.created.contains(entity)||PRODUCER_DELIVERY.get()!=null)return;
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
        if(frame.inputs!=null)frame.inputs.clear();
        if(frame.fields!=null)frame.fields.clear();
        if(frame.created!=null)frame.created.clear();frame.receiver=null;
    }
    /** Only the installed Core owner can read the actual current invocation's inputs. */
    public static Object[] producerInputs(boolean includeParents) {
        Guards current=guards;if(current==null||CREATION_CALLER.getCallerClass()!=current.owner())return new Object[0];
        java.util.List<Object> inputs=new java.util.ArrayList<>();
        for(var frame=PRODUCERS.get();frame!=null;frame=includeParents?frame.parent:null) {
            if(frame.overflow)inputs.add(Boolean.FALSE);if(frame.inputs!=null)inputs.addAll(frame.inputs);
        }
        return inputs.toArray();
    }
    public static boolean deliveringCreationTo(Class<?> owner) { return CREATION_DELIVERY.get()==owner; }
    public static Module creationModule(Object object) {
        if(object==null)return null;
        synchronized(CREATION_OWNERS) {EventKey key=new EventKey(object,null);return CREATION_UNKNOWN.contains(key)?null:CREATION_OWNERS.get(key);}
    }
    static Module[] invokingSources(){
        return invokingSources(NO_SOURCES);
    }
    private static Module[] invokingSources(Module[] retained){
        SourceQuery active=SOURCE_QUERIES.get();if(active!=null){active.retain(retained);return active.partial;}
        SourceQuery query=beginSourceQuery(retained);
        try{
            Module[] scoped=scopedSources();if(scoped.length!=0)return scoped;
            return sourceFrameWalk();
        }finally{endSourceQuery(query);}
    }
    static boolean sourceUnknown(){
        if(SOURCE_QUERIES.get()!=null)return true;
        if(NetworkBridge.sourceUnknown())return true;
        if(ClientBridge.sourceUnknown())return true;
        if(CodeSourceBridge.executionUnknown())return true;
        ProducerFrame producer=PRODUCERS.get();if(producer!=null&&producer.overflow)return true;
        Frame task=CURRENT.get();return task!=null&&!task.exited&&task.sourcesUnknown;
    }
    static Module[] mutationSources(Object receiver){
        SourceQuery query=SOURCE_QUERIES.get();if(query!=null)return query.partial;
        Module[] scoped=scopedSources();if(scoped.length!=0)return scoped;
        // Ordinary stores keep their real gate/policy, but need no historical
        // provenance work before this receiver or execution has been observed.
        // Producer methods and changed shared code carry ExecutionFlow frames;
        // native writes pass their captured contributors through their own path.
        if(CodeSourceBridge.executionMutationTracked(receiver)
                ||SourceMapBridge.holderRegistered(receiver)||SourceMapBridge.carrierRegistered(receiver))return frameSources();
        return NO_SOURCES;
    }
    static Module[] frameSources(){
        SourceQuery active=SOURCE_QUERIES.get();if(active!=null)return active.partial;
        SourceQuery query=beginSourceQuery(NO_SOURCES);
        try{return sourceFrameWalk();}finally{endSourceQuery(query);}
    }
    private static Module[] sourceFrameWalk(){
        // Inspect the live registries on every operation. Before any producer,
        // external instruction row or execution frame exists, walking all of
        // Minecraft's construction frames can only return this same empty set.
        boolean producers;synchronized(PRODUCER_MODULES){producers=!PRODUCER_MODULES.isEmpty();}
        return producers||CodeSourceBridge.hasFrameSources()?ORIGIN_WALKER.walk(INVOKING_SOURCES):NO_SOURCES;
    }
    static boolean stoppedEffects(){return stoppedInvocation();}
    static Set<Module> addModuleSources(Set<Module> sources,Module[] modules,boolean producersOnly){
        if(modules!=null)for(Module module:modules)if(!producersOnly||module==null||producer(module)){
            if(sources==null)sources=Collections.newSetFromMap(new IdentityHashMap<>());sources.add(module);
        }
        return sources;
    }
    static Set<Module> scopedSourceSet(){
        Set<Module> modules=addModuleSources(null,retainQuerySources(NetworkBridge.currentSources()),false);
        modules=addModuleSources(modules,retainQuerySources(ClientBridge.currentSources()),false);
        modules=addModuleSources(modules,retainQuerySources(IoBridge.currentSources()),false);
        modules=addModuleSources(modules,retainQuerySources(ResourceBridge.currentFileSources()),false);
        modules=addModuleSources(modules,retainQuerySources(ResourceBridge.currentBufferSources()),false);
        modules=addModuleSources(modules,retainQuerySources(CodeSourceBridge.executionSources()),false);
        return modules;
    }
    private static Module[] retainQuerySources(Module[] sources){SourceQuery query=SOURCE_QUERIES.get();if(query!=null)query.retain(sources);return sources;}
    private static Module[] unknownSources(Module[] sources){
        for(Module source:sources)if(source==null)return sources;
        Module[] partial=new Module[sources.length+1];for(int i=0;i<sources.length;i++)partial[i+1]=sources[i];return partial;
    }
    static Module[] scopedSources(){
        // This query also runs while LambdaMetafactory updates its own counters.
        // An array factory here would link through the same guarded Unsafe path.
        Set<Module> modules=scopedSourceSet();return modules==null?NO_SOURCES:modules.toArray(new Module[0]);
    }
    static Module[] nativeNetworkSources(){
        if(!NativeControl.sourceCapture())throw new SecurityException("ACTUAL_NATIVE_SOURCE_CAPTURE_REQUIRED");
        SourceQuery query=SOURCE_QUERIES.get();if(query!=null)return query.partial;
        Module[] sources=scopedSources();return sourceUnknown()?unknownSources(sources):sources;
    }
    static Module[] nativeProcessSources(){
        if(!NativeControl.sourceCapture())throw new SecurityException("ACTUAL_NATIVE_SOURCE_CAPTURE_REQUIRED");return invokingSources();
    }
    public static Module[] creationSources(Object object){
        synchronized(CREATION_OWNERS){
            Set<Module> sources=CREATION_SOURCES.get(new EventKey(object,null));if(sources!=null)return sources.toArray(new Module[0]);
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
                if(creation!=null)sources.addAll(creation);if(tasks!=null)sources.addAll(tasks);return sources.toArray(new Module[0]);
            }
            Set<Module> tasks=TASK_OWNERS.get(key);if(tasks!=null)return tasks.toArray(new Module[0]);
            Set<Module> sources=CREATION_SOURCES.get(key);if(sources!=null)return sources.toArray(new Module[0]);
        }
        Module module=objectModule(object,key);return module==null?new Module[0]:new Module[]{module};
    }
    public static boolean objectSourceUnknown(Object object){synchronized(CREATION_OWNERS){
        EventKey key=new EventKey(object,null);ThreadOrigin thread=THREAD_ORIGINS.get(key);
        return CREATION_UNKNOWN.contains(key)||thread!=null&&thread.unknown;
    }}
    /** Native heap mutations use this actual object's current observed sources. */
    public static synchronized boolean nativeHeapTargetAllowed(Object object,Module[] actualSources){
        if(object==null||actualSources==null||actualSources.length==0
                ||controlled(object)||ownState(object instanceof Class<?> type?type:object.getClass()))return false;
        for(Module source:actualSources)if(source==null||modStopped(source))return false;
        synchronized(CREATION_OWNERS){
            if(objectSourceUnknown(object))return false;
            Module[] sources=objectSources(object);if(sources.length==0)return false;
            for(Module source:sources){
                if(source==null||!producer(source)||modStopped(source))return false;
                boolean observed=false;for(Module actual:actualSources)if(actual==source){observed=true;break;}
                if(!observed)return false;
            }
            return true;
        }
    }
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
    static void createdBy(Object object,Module[] sources,boolean onlyAbsent){
        EventKey lookup=new EventKey(object,null);
        if(sourceUnknown()){
            if(!CREATION_UNKNOWN.contains(lookup))NativeControl.heapPolicyChanged(object);
            CREATION_UNKNOWN.add(new EventKey(object,CREATION_QUEUE));CREATION_OWNERS.remove(lookup);
        }
        if(onlyAbsent&&(CREATION_OWNERS.containsKey(lookup)||CREATION_SOURCES.containsKey(lookup)))return;
        EventKey key=new EventKey(object,CREATION_QUEUE);CREATION_OWNERS.remove(lookup);
        Set<Module> actual=java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<Module,Boolean>());actual.addAll(java.util.Arrays.asList(sources));actual.remove(null);
        Set<Module> previous=CREATION_SOURCES.get(lookup);
        if(previous==null||!previous.equals(actual))NativeControl.heapPolicyChanged(object);
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
    static Module objectModule(Object object,EventKey key) {
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
    static Module singleThreadSource(EventKey key,ThreadOrigin thread){
        // CREATION_OWNERS is held. The caller only consumes a singleton, so
        // compare all three actual sets without constructing a union and array.
        Set<Module> creation=CREATION_SOURCES.get(key),tasks=TASK_OWNERS.get(key);Module selected=null;
        for(int i=0;i<3;i++){
            Set<Module> sources=i==0?thread.sources:i==1?creation:tasks;if(sources==null)continue;
            for(Module source:sources){if(source==null)return null;if(selected==null)selected=source;else if(selected!=source)return null;}
        }
        return selected;
    }
    /** Keep only the actual carrier declaration out of this slot's business origins. */
    public static Module[] presentationSources(Object value,Class<?> carrier){
        Guards current=guards;Class<?> caller=WALKER.getCallerClass();
        if(current==null||caller!=current.owner()||!carrier.isInstance(value))throw new SecurityException("ACTUAL_PRESENTATION_CORE_AND_CARRIER_REQUIRED");
        if(objectSourceUnknown(value))return null;
        Module declaration=DefinitionBridge.module(carrier);if(producer(declaration))return null;
        Set<Module> actual=Collections.newSetFromMap(new IdentityHashMap<>());
        for(Module source:objectSources(value)){if(source==null)return null;if(source!=declaration)actual.add(source);}
        return actual.isEmpty()?null:actual.toArray(Module[]::new);
    }
    public static Object[] createdPresentationObjects(Module[] modules,Class<?> carrier){
        Guards current=guards;Class<?> caller=WALKER.getCallerClass();
        if(current==null||caller!=current.owner())throw new SecurityException("ACTUAL_PRESENTATION_CORE_REQUIRED");
        Set<Module> selected=Collections.newSetFromMap(new IdentityHashMap<>());selected.addAll(Arrays.asList(modules));
        Set<Object> found=Collections.newSetFromMap(new IdentityHashMap<>());
        synchronized(CREATION_OWNERS){
            reapCreationOwners();
            for(var entry:CREATION_OWNERS.entrySet())if(selected.contains(entry.getValue())){Object value=entry.getKey().get();if(value!=null&&carrier.isInstance(value))found.add(value);}
            for(var entry:CREATION_SOURCES.entrySet())if(entry.getValue().stream().anyMatch(selected::contains)){Object value=entry.getKey().get();if(value!=null&&carrier.isInstance(value))found.add(value);}
        }
        return found.toArray();
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
    static void resourceCreatedFrom(Object object,Module[] sources){
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
        // Empty is also a captured result, but only this actual live scope can
        // supply it. New execution contributors and unknown state remain live.
        boolean captured=nativeSources!=null&&(nativeSources==IoBridge.currentSources()||nativeSources==ResourceBridge.currentFileSources());
        return resourceSources(nativeSources,captured);
    }
    static boolean resourceExposedByOperation(Object object,Module[] sources){
        Class<?> caller=WALKER.getCallerClass();if(caller!=ResourceBridge.class&&caller!=IoBridge.class)throw new SecurityException("ACTUAL_RESOURCE_OPERATION_REQUIRED");
        return resourceExposedFrom(object,sources);
    }
    static Module[] resourceSources(Module[] nativeSources){return resourceSources(nativeSources,false);}
    private static Module[] resourceSources(Module[] nativeSources,boolean captured){
        // Keep the one current-scope union instead of materializing it and
        // inserting it into a second map. Io sources are already included in
        // full by scopedSourceSet; filtering and adding them again adds nothing.
        Set<Module> sources=scopedSourceSet();
        if(sources==null&&!captured)sources=addModuleSources(null,frameSources(),false);
        sources=addModuleSources(sources,nativeSources,true);
        if(sourceUnknown()){
            if(sources==null)sources=Collections.newSetFromMap(new IdentityHashMap<>());sources.add(null);
        }
        return sources==null?NO_SOURCES:sources.toArray(new Module[0]);
    }
    static boolean resourceExposedFrom(Object object,Module[] sources){
        if(sources.length==0)return false;
        synchronized(CREATION_OWNERS){
            reapCreationOwners();Set<Module> merged=java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
            merged.addAll(java.util.Arrays.asList(objectSources(object)));merged.addAll(java.util.Arrays.asList(sources));createdBy(object,merged.toArray(new Module[0]),false);
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
    static void deliverCreation(Object object,boolean failed) {
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
            var frame=PRODUCERS.get();if(frame.created().size()<64)frame.created.add(object);else frame.overflow=true;
        }
        CREATION_DELIVERY.set(observer.owner);
        try { if(failed)observer.failure.invokeExact(object);else observer.callback.invokeExact(object); }
        catch(Throwable failure) { lost(); }
        finally { CREATION_DELIVERY.remove(); }
    }
    // Each policy generation is published as one immutable object. A reader captures it once,
    // so callback handles and Unsafe field coordinates always belong to the same generation.
    record UnsafeField(Class<?> declaring,String name,long offset,Class<?> type) {}
    record Guards(Class<?> owner,java.lang.invoke.MethodHandle field,
                          java.lang.invoke.MethodHandle dispatch,java.lang.invoke.MethodHandle task,
                          java.util.List<UnsafeField> unsafeFields,java.lang.invoke.MethodHandle index) {}
    static final class ControlRef extends java.lang.ref.WeakReference<Object> {
        ControlRef(Object value){super(value);}
        @Override public void clear(){if(controlCaller())super.clear();}
        @Override public boolean enqueue(){return controlCaller()&&super.enqueue();}
    }
    static boolean sharedControlRootsPublished;
    static volatile boolean controlCaptureHealthy=true;
    public static boolean controlCaptureHealthy(){return controlCaptureHealthy;}
    static final ThreadLocal<Boolean> OWN_LEDGER_MUTATION=new ThreadLocal<>();
    static final Set<String> EXTERNAL_GUARD_ENTRIES=Set.of(
            "nativeDefinitionsChanged","deliverDefinitionsChanged",
            "controlCaller","controlWriterAllowed","controlled","criticalEntry","referenceRetirementAllowed","controlMutationAllowed","fieldGateMutationAllowed","inheritControl","controlBackingPublished",
            "fieldWriteAllowed","staticFieldWriteAllowed","reflectFieldWrite",
            // Field-write entry wrappers are still live at the policy query;
            // they must not lend their own controller identity to that write.
            "beginFieldMutation","staticFieldMutation","beginOwnStaticFieldMutation","beginStaticFieldMutation",
            "beginNativeUnsafeMutation","beginExecutedUnsafeMutation","beginNativeUnsafeCopy","beginNativeFieldMutation","beginNativeArrayMutation","beginNativeArrayCopyRead","beginNativeArrayCopy",
            "indexDenied","indexRemovalAllowed","indexWriteAllowed","beginIndexWrite","beginIndexNodeWrite","beginIndexArrayWrite","beginIndexRemoval","beginIndexNodeRemoval","beginIndexArrayRemoval","beginIndexStructure","endIndexMutation","deniedIndexCurrent",
            "registerConsoleInput","beginConsoleCommand","endConsoleCommand","consoleDispatchTrusted",
            "clearProtectedMap","unsafeWriteAllowed","unsafeMemoryAllowed","networkRetirementWriteAllowed","networkRetirementOwnerAllowed","networkRetirementActive",
            "nativeArrayElementAllowed","nativePrimitiveArrayPolicy","nativeArrayPointerAllowed","nativeArrayCopyInternal","nativeRegistrationAllowed","nativeRegistrationTargetAllowed","nativeHeapTargetAllowed","arrayWriteAllowed",
            "resourceReleaseAllowed","lifeResult","dispatchAllowed","dispatchDeniedResult","equipmentActionSourceAllowed","equipmentCarrierSourceAllowed","beginEquipmentInvocation","equipmentInvocationDenied","equipmentInvocationResult","endEquipmentInvocation","failedEquipmentInvocation","taskEffectAllowed",
            "beginEquipmentSlotRead","equipmentSlotFieldRead","equipmentSlotArrayRead","equipmentSlotContainerRead","equipmentSlotSelectorRead","equipmentSlotUnresolved","observeEquipmentSlot","failedEquipmentSlotRead",
            "capabilityAccept","capabilitySupply","capabilityApply","capabilityTest","capabilityDerived",
            "capabilityComposed","capabilityComposedBi","beginEquipmentSlotCall","endEquipmentSlotCall","failedEquipmentSlotCall","beginEquipmentSlotMethod","endEquipmentSlotMethod","failedEquipmentSlotMethod",
            "invokeEquipmentCallback","invokeEquipmentOriginal","invokeDefinedEquipmentCallback","executionEquipmentCallback","resultPublicationAllowed");
    static boolean controlCaller() {
        // The query omits guard entry points, preserving the real ledger writer.
        Class<?> caller=NativeControl.available()?NativeControl.controlWriterCaller():WALKER.walk(CONTROL_WRITER_CALLER);
        // JVMTI includes hidden frames that the original walker omits.
        // Preserve that original selection on the uncommon hidden path.
        if(caller!=null&&caller.isHidden())caller=WALKER.walk(CONTROL_WRITER_CALLER);
        return controlWriterAllowed(caller);
    }
    static boolean controlWriterAllowed(Class<?> caller){
        if(caller==null)return false;
        // These exact classes were already admitted below. Avoid native nest-host
        // lookup for the frequent direct ledger writers; every call still walks
        // its actual current writer, and nested/foreign classes keep the full checks.
        if(caller==TaskBridge.class||caller==ControlBridge.class||caller==policyInstaller||caller==BackingBridge.class
                ||caller==SourceMapBridge.class||caller==NativeControl.class||caller==ControlBridge.class
                ||caller==ExecutionFlow.class||caller==NetworkCleanup.class)return true;
        if((caller==DefinitionBridge.class||caller==ResourceBridge.class||caller==IoBridge.class
                ||caller==CodeSourceBridge.class||caller==NetworkBridge.class||caller==ClientBridge.class)
                &&caller.getModule()==DefinitionBridge.class.getModule())return true;
        if(caller.getClassLoader()==ClassLoader.getSystemClassLoader()&&caller.getName().startsWith("dev.ronova.pro.agent.")&&AGENT_WRITERS.get(caller))return true;
        // All bridge nests below are defined by the bootstrap loader. A foreign
        // nest cannot be one of them. Querying its host here can initiate an
        // outer class while that same class is still being defined by Forge.
        Class<?> nest=caller.getClassLoader()==null?caller.getNestHost():caller;
        if((nest==TaskBridge.class||nest==ControlBridge.class)&&caller.getModule()==TaskBridge.class.getModule())return true;
        if(caller==policyInstaller||caller==BackingBridge.class)return true;
        if(caller==SourceMapBridge.class||caller==NativeControl.class||caller==ControlBridge.class||nest==ExecutionFlow.class)return true;
        if((nest==DefinitionBridge.class||nest==ResourceBridge.class||nest==IoBridge.class||nest==CodeSourceBridge.class||nest==NetworkBridge.class||nest==NetworkCleanup.class||nest==ClientBridge.class)
                &&caller.getModule()==DefinitionBridge.class.getModule())return true;
        Guards current=guards;
        if(current!=null&&DefinitionBridge.module(caller)==current.owner().getModule()&&caller.getClassLoader()==current.owner().getClassLoader()&&caller.getName().startsWith("dev.ronova.pro."))return true;
        if(caller.getClassLoader()==ClassLoader.getSystemClassLoader()&&caller.getName().startsWith("dev.ronova.pro.agent."))
            return AGENT_WRITERS.get(caller);
        return false;
    }
    static boolean controlled(Object value) {
        return value!=null&&(fieldGateControl(value)||SourceMapBridge.controlled(value)||DefinitionBridge.controlled(value)||CodeSourceBridge.controlled(value)||criticalEntry(value));
    }
    static boolean criticalEntry(Object value){
        if(value==null||value.getClass().getClassLoader()!=null||!(value instanceof java.util.Map.Entry<?,?> entry))return false;
        Object key=entry.getKey();return key!=null&&ownState(key.getClass());
    }
    public static boolean referenceRetirementAllowed(Object reference){
        return reference==null||!ownState(reference.getClass())||controlCaller();
    }
    // Every caller has just established this exact receiver's gate role. Reuse
    // that positive lookup only within this operation; authenticate the live
    // writer here every time. This helper is itself an external guard frame.
    static boolean fieldGateMutationAllowed(){
        return ORIGIN_WALKER.walk(FIELD_GATE_WRITER)||controlCaller()||NativeControl.libraryImplementationCaller();
    }
    public static boolean controlMutationAllowed(Object value) {
        if(CodeSourceBridge.fieldGateMetadata(value))return fieldGateMutationAllowed();
        if(!controlled(value))return true;
        if(SourceMapBridge.internal())return true;
        if((value==ADMISSIONS||value==SUBMISSIONS||value==HELD_TASKS||value==HELD_ARRAYS)
                &&OWN_LEDGER_MUTATION.get()!=null)return true;
        return controlCaller()||NativeControl.libraryImplementationCaller();
    }
    static void inheritControl(Object parent,Object next) {
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
        if(producerSite()){ProducerFrame frame=PRODUCERS.get();if(frame!=null)frame.receiver=receiver;producerReference(receiver,false);}
    }
    public static void producerArgument(Object value) {
        if(producerSite())producerReference(value,true);
    }
    /** Run at a real factory entry, before its first business instruction. */
    public static boolean producerExecutionAllowed() {
        ProducerFrame frame=PRODUCERS.get();
        return frame==null||!producerSite()||taskEffectAllowed(guards,frame,"producer");
    }
    static void producerReference(Object receiver,boolean argument) {
        ProducerFrame frame=PRODUCERS.get();var observer=producerObserver;
        if(frame==null||observer==null||receiver==null||PRODUCER_DELIVERY.get()!=null)return;
        PRODUCER_DELIVERY.set(guards.owner());PRODUCER_METHOD_RECEIVER.set(true);
        try {
            Object origin=(Object)observer.invokeExact(receiver,receiver);
            if(origin!=null) {if(frame.inputs().size()<64)frame.inputs.add(origin);else frame.overflow=true;}
            else if(argument)frame.overflow=true; // An opaque extra input cannot be attributed to one source.
        }catch(Throwable unavailable){frame.overflow=true;lost();}
        finally {PRODUCER_METHOD_RECEIVER.remove();PRODUCER_DELIVERY.remove();}
    }
    public static synchronized void registerControlObjects(Class<?> owner,Object[] roots) {
        requirePolicyInstaller();if(guards==null||guards.owner()!=owner)throw new SecurityException("CONTROL_OWNER");
        try {captureControls(roots);}
        catch(RuntimeException|Error failure){controlCaptureHealthy=false;throw failure;}
    }
    static final java.util.Map<Object,Boolean> TRUSTED_CONSOLE_INPUTS=
            java.util.Collections.synchronizedMap(new java.util.WeakHashMap<>());
    static final ThreadLocal<Boolean> CONSOLE_COMMAND=new ThreadLocal<>();
    /** Mark the exact input only when the dedicated server's console reader produced it. */
    public static void registerConsoleInput(Object input) {
        if(input==null)return;
        boolean authentic=WALKER.walk(frames->{
            var stack=frames.dropWhile(frame->(frame.getDeclaringClass()==ControlBridge.class||frame.getDeclaringClass()==TaskBridge.class)).limit(2).toList();
            if(stack.size()!=2)return false;
            var caller=stack.get(0);var source=stack.get(1);
            if(!caller.getDeclaringClass().getName().equals("net.minecraft.server.dedicated.DedicatedServer")
                    ||!(caller.getMethodName().equals("handleConsoleInput")||caller.getMethodName().equals("m_139645_")))return false;
            if(source.getDeclaringClass().getName().equals("net.minecraft.server.dedicated.DedicatedServer$1")
                    &&source.getMethodName().equals("run"))return true;
            return false;
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
    static void captureControls(Object[] roots) {
        // One control directory retains old weak identities and their backings.
        // Actual backing publications already register replacements; a new
        // definition must not rescan all previous records into a parallel array.
        java.util.List<Object> supplied=new java.util.ArrayList<>(java.util.Arrays.asList(roots));
        if(!sharedControlRootsPublished)
            for(Class<?> type:java.util.List.of(ControlBridge.class,TaskBridge.class,SourceMapBridge.class,BackingBridge.class,DefinitionBridge.class,ResourceBridge.class,IoBridge.class,CodeSourceBridge.class))for(var field:type.getDeclaredFields())
                if(java.lang.reflect.Modifier.isStatic(field.getModifiers())&&(java.util.Map.class.isAssignableFrom(field.getType())||java.util.Collection.class.isAssignableFrom(field.getType()))) {
                    Object value=CodeSourceBridge.controlField(field,null);if(value!=null)supplied.add(value);
                }
        CodeSourceBridge.agentControls(supplied.toArray());
        sharedControlRootsPublished=true;
    }
    static Class<?> policyInstaller,observerInstaller;
    static Class<?> installingCaller() {
        Class<?> caller=WALKER.walk(MEMORY_CALLER);return (caller==null?TaskBridge.class:caller).getNestHost();
    }
    static void requirePolicyInstaller() {
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
    static volatile Guards guards;
    static Guards policy(Class<?> owner,java.lang.invoke.MethodHandle field,
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
        Guards next=policy(owner,field,dispatch,task,java.util.List.of());
        if(current==null)NativeControl.heapPolicyChanged(null);
        guards=next;
    }
    static java.util.List<UnsafeField> fields(java.lang.reflect.Field[] fields,long[] offsets) {
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
        Guards next=policy(owner,field,dispatch,task,fields(fields,offsets));
        if(current==null)NativeControl.heapPolicyChanged(null);
        guards=next;
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
        if(current==null)NativeControl.heapPolicyChanged(null);
        guards=new Guards(next.owner(),next.field(),next.dispatch(),next.task(),next.unsafeFields(),callback);
    }
    static final ThreadLocal<Boolean> INDEX_QUERY=new ThreadLocal<>();
    static boolean indexDenied(Guards snapshot,Object map,Object key,Object value) {
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
    static final class SourceIndexMutation {
        final Object map;final java.util.concurrent.locks.ReentrantLock gate;final Thread thread=Thread.currentThread();boolean closed;
        final SourceIndexMutation previous;final SourcePublication publication;
        SourceIndexMutation(Object map,java.util.concurrent.locks.ReentrantLock gate){
            this.map=map;this.gate=gate;publication=new SourcePublication(map);
            synchronized(SOURCE_INDEX_MUTATIONS){previous=SOURCE_INDEX_MUTATIONS.put(thread,this);}
        }
    }
    static final Map<Thread,SourceIndexMutation> SOURCE_INDEX_MUTATIONS=new IdentityHashMap<>();
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
    static final class FastListView {
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
    static Object indexMutation(Object map,java.util.concurrent.locks.ReentrantLock gate){
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
    static volatile java.lang.invoke.MethodHandle resourceGuard;
    static volatile java.lang.invoke.MethodHandle nativeRegistrationGuard;
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
    static boolean ownState(Class<?> declaring) {
        if(declaring==null)return false;Class<?> host=declaring.getClassLoader()==null?declaring.getNestHost():declaring;Guards current=guards;
        if(host==TaskBridge.class||host==BackingBridge.class||host==CarrierBridge.class||host==SourceMapBridge.class||host==NativeControl.class||host==DefinitionBridge.class||host==ResourceBridge.class||host==IoBridge.class||host==CodeSourceBridge.class||host==NetworkBridge.class||host==ClientBridge.class
                ||host==EventFaultBridge.class||host==ControlBridge.class||host==ExecutionFlow.class||host==NetworkCleanup.class)return true;
        if(ControlBridge.owns(declaring))return true;
        return current!=null&&DefinitionBridge.module(declaring)==current.owner().getModule()&&declaring.getClassLoader()==current.owner().getClassLoader()&&declaring.getName().startsWith("dev.ronova.pro.");
    }
    static volatile Class<?> eventBoundary;
    public static void installEventBoundary(Class<?> type) {
        if(!ControlBridge.owns(WALKER.getCallerClass())||!type.getName().equals("net.minecraftforge.eventbus.EventBus"))throw new SecurityException("EVENT_CONTROL_OWNER");
        if(eventBoundary!=null&&eventBoundary!=type)throw new SecurityException("EVENT_CONTROL_IDENTITY");
        eventBoundary=type;
    }
    public static boolean listenerRemovalAllowed(Object target) {
        if(target==null||!ownState(target instanceof Class<?> type?type:target.getClass()))return true;
        Class<?> caller=WALKER.walk(frames->frames.map(StackWalker.StackFrame::getDeclaringClass)
                .filter(type->type!=TaskBridge.class&&type!=ControlBridge.class&&type!=eventBoundary&&!type.getName().startsWith("java.")&&!type.getName().startsWith("jdk."))
                .findFirst().orElse(null));
        return caller!=null&&(ownState(caller)||ControlBridge.owns(caller));
    }
    public static boolean unsafeWriteAllowed(Object receiver,long offset,Object value) {
        return unsafeWriteAllowed(receiver,offset,value,8);
    }
    public static boolean unsafeWriteAllowed(Object receiver,long offset,Object value,String kind) {
        return unsafeWriteAllowed(receiver,offset,value,ResourceBridge.unsafeWidth(kind));
    }
    static boolean classReflectionWrite(Object receiver,long offset,int width){
        // A Class mirror also holds Class's own instance cache, separately
        // from the represented declaration and its static business fields.
        // Admit only its real JDK CAS, never an arbitrary write to that mirror.
        if(!ORIGIN_WALKER.walk(CLASS_REFLECTION_WRITER))return false;
        var field=ResourceBridge.exactField(receiver,offset,width);
        return field!=null&&field.getDeclaringClass()==Class.class&&!java.lang.reflect.Modifier.isStatic(field.getModifiers())
                &&field.getName().equals("reflectionData")&&field.getType()==java.lang.ref.SoftReference.class;
    }
    public static boolean networkRetirementWriteAllowed(Object receiver,long offset,Object value,String kind){
        return NetworkBridge.retirementWriteAllowed(receiver,offset,value,ResourceBridge.unsafeWidth(kind));
    }
    public static boolean networkRetirementOwnerAllowed(Object owner,Object receiver,long offset,Object value,String kind){
        return !modStopped(objectModule(owner))&&networkRetirementWriteAllowed(receiver,offset,value,kind);
    }
    public static boolean networkRetirementActive(){return NetworkBridge.retirementActive();}
    static boolean unsafeWriteAllowed(Object receiver,long offset,Object value,int resourceWidth) {
        // The exact controller gate must also operate while its caller's
        // business module is stopping. Its existing control-writer check is
        // authoritative; it is not a business field or backing-policy target.
        if(CodeSourceBridge.fieldGateMetadata(receiver))return fieldGateMutationAllowed();
        if(!CodeSourceBridge.fieldRangeAllowed(receiver,offset,resourceWidth)&&!coreWriter())return false;
        if(!ResourceBridge.bufferWrapMutationAllowed(receiver,offset,value,resourceWidth)
                &&!ResourceBridge.unsafeMutationAllowed(receiver,offset,resourceWidth))return false;
        boolean targetStopped=modStopped(objectModule(receiver));
        boolean ownMirror=receiver instanceof Class<?> owner&&ownState(owner);
        boolean reflectionCache=receiver instanceof Class<?>&&(targetStopped||ownMirror)&&classReflectionWrite(receiver,offset,resourceWidth);
        // Stopping a represented class must not turn Class.newReflectionData's
        // own cache CAS into a permanently false retry. Its exact JDK/instance
        // field proof excludes that target attribution only; the real caller's
        // stopped sources and all following receiver policies still apply.
        if(((targetStopped&&!reflectionCache)||stoppedInvocation())&&!coreWriter()
                &&!NetworkBridge.retirementWriteAllowed(receiver,offset,value,resourceWidth))return false;
        if(ownMirror&&!reflectionCache||receiver!=null&&ownState(receiver.getClass()))return false;
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
    static int primitiveWidth(Class<?> type) {
        return type==long.class||type==double.class?8:type==int.class||type==float.class?4:type==short.class||type==char.class?2:1;
    }
    static boolean rangesOverlap(long left,long leftSize,long right,long rightSize) {
        return left>=0&&right>=0&&leftSize>0&&rightSize>0&&left<right+rightSize&&right<left+leftSize;
    }
    static Object fieldValue(Class<?> type,Object raw) {
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
    static boolean coreWriter() {
        Guards current=guards;if(current==null)return false;
        Class<?> caller=WALKER.walk(CORE_CALLER);
        return DefinitionBridge.module(caller)==current.owner().getModule()&&caller.getClassLoader()==current.owner().getClassLoader()
                &&Objects.equals(caller.getProtectionDomain().getCodeSource(),current.owner().getProtectionDomain().getCodeSource());
    }
    static final ThreadLocal<Boolean> FIELD_POLICY_ACTIVE=new ThreadLocal<>();
    // The gate directory must not itself use an observed Map: an index hook can
    // re-enter it while SourceMapBridge decides whether that Map may publish.
    static final class FieldGateNode extends java.lang.ref.WeakReference<Object> {
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
    public static boolean nativeArrayCopyInternal(){
        if(!NativeControl.arrayCopyInternalBoundary())throw new SecurityException("ACTUAL_NATIVE_ARRAYCOPY_QUERY_REQUIRED");
        // Native installs this entry before available is published. The actual
        // native writer reader is already prepared at that point; a StackWalker
        // fallback here could copy its frame batch and recursively query itself.
        Class<?> caller=NativeControl.controlWriterCaller();
        return caller!=null&&!caller.isHidden()&&controlWriterAllowed(caller);
    }
    public static int nativePrimitiveArrayPolicy(Object array){return BackingBridge.nativePrimitiveArrayPolicy(array);}
    public static boolean nativeArrayPointerAllowed(Object array){
        return arrayWriteAllowed(array)&&BackingBridge.nativePointerAllowed(array);
    }
    static boolean arrayWriteAllowed(Object array){return !(controlled(array)&&!controlCaller())&&!((modStopped(objectModule(array))||stoppedInvocation())&&!coreWriter());}
    static boolean arrayWriteAllowed(Object array,int index,Object value){
        return !(controlled(array)&&!controlCaller())&&(!((modStopped(objectModule(array))||stoppedInvocation())&&!coreWriter())
                ||NetworkBridge.retirementArrayAllowed(array,index,value));
    }
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
    static final class FieldGateBucket {
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
    static java.util.concurrent.locks.ReentrantLock newFieldGate(){
        if(WALKER.getCallerClass()!=FieldGateNode.class)throw new SecurityException("ACTUAL_FIELD_GATE_OWNER_REQUIRED");
        var gate=new java.util.concurrent.locks.ReentrantLock();CodeSourceBridge.fieldGateControls(gate);return gate;
    }
    static final FieldGateBucket[] FIELD_GATES=new FieldGateBucket[1024];
    static {for(int i=0;i<FIELD_GATES.length;i++)FIELD_GATES[i]=new FieldGateBucket();}
    static boolean fieldGateControl(Object value){
        FieldGateBucket[] buckets=FIELD_GATES;if(buckets==null)return false;
        if(value==buckets)return true;
        if(value instanceof FieldGateNode[])for(FieldGateBucket bucket:buckets)if(bucket!=null&&bucket.table==value)return true;
        return false;
    }
    static java.util.concurrent.locks.ReentrantLock fieldGate(Object receiver) {
        if(receiver==null)throw new IllegalArgumentException("INDEX_GATE_RECEIVER_REQUIRED");
        return FIELD_GATES[System.identityHashCode(receiver)&(FIELD_GATES.length-1)].get(receiver);
    }
    // The publication belongs to a real writer scope, not to replaceable ThreadLocal state.
    // A nested writer for the same receiver retains incoming locks until its outer writer exits.
    static final Map<Thread,SourcePublication> SOURCE_PUBLICATIONS=new IdentityHashMap<>();
    static final class SourcePublication {
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
    static final class FieldMutation {
        final java.util.concurrent.locks.ReentrantLock gate;final Object source,receiver;
        final Thread thread=Thread.currentThread();final SourcePublication publication;boolean closed,retirementQualified;
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
    record RecoveryArrayMutation(Object array,FieldMutation mutation,int start){
        RecoveryArrayMutation{CodeSourceBridge.mutationControls(this);}
    }
    static final class RawMemoryMutation {
        final long token;final boolean reading;final Thread thread=Thread.currentThread();boolean closed;
        RawMemoryMutation(long token,boolean reading){this.token=token;this.reading=reading;CodeSourceBridge.mutationControls(this);}
    }
    static final class HeapMemoryRead {
        final java.util.concurrent.locks.ReentrantLock gate;final Object record;final Thread thread=Thread.currentThread();boolean closed;
        HeapMemoryRead(java.util.concurrent.locks.ReentrantLock gate,Object record){this.gate=gate;this.record=record;CodeSourceBridge.mutationControls(this);}
    }
    // Unsafe can replace Thread.threadLocals while one of these scopes is live.
    // Keep the actual thread's stack outside that mutable carrier, and release
    // its strong thread reference when the last operation finishes.
    static final Map<Thread,MemoryCopy> MEMORY_COPIES=new IdentityHashMap<>();
    static MemoryCopy memoryCopy(){synchronized(MEMORY_COPIES){return MEMORY_COPIES.get(Thread.currentThread());}}
    static void memoryCopy(MemoryCopy copy){synchronized(MEMORY_COPIES){if(copy==null)MEMORY_COPIES.remove(Thread.currentThread());else MEMORY_COPIES.put(Thread.currentThread(),copy);}}
    static final class MemoryCopy {
        final Object source,read,destination;final Module[] before;final long offset,length;final Thread thread=Thread.currentThread();final MemoryCopy previous=memoryCopy();boolean closed;
        MemoryCopy(Object source,long offset,long length,Object read,Object destination,Module[] before){this.source=source;this.offset=offset;this.length=length;this.read=read;this.destination=destination;this.before=before;CodeSourceBridge.mutationControls(this,before,MEMORY_COPIES);memoryCopy(this);}
    }
    static final Map<Thread,UnsafeMutation> UNSAFE_MUTATIONS=new IdentityHashMap<>();
    static UnsafeMutation unsafeMutation(){synchronized(UNSAFE_MUTATIONS){return UNSAFE_MUTATIONS.get(Thread.currentThread());}}
    static void unsafeMutation(UnsafeMutation mutation){synchronized(UNSAFE_MUTATIONS){if(mutation==null)UNSAFE_MUTATIONS.remove(Thread.currentThread());else UNSAFE_MUTATIONS.put(Thread.currentThread(),mutation);}}
    static final class UnsafeMutation {
        final Object receiver;final long offset,length;final FieldMutation receipt;final UnsafeMutation root,previous;final Module[] contributors;
        final int writesAtStart;int completedWrites;boolean written,closed,retirementQualified,retirementOnly=true;
        UnsafeMutation(Object receiver,long offset,long length,FieldMutation receipt,UnsafeMutation previous,UnsafeMutation root,Module[] contributors){
            this.receiver=receiver;this.offset=offset;this.length=length;this.receipt=receipt;this.previous=previous;this.root=root==null?this:root;this.contributors=contributors;writesAtStart=this.root.completedWrites;
            CodeSourceBridge.mutationControls(this,contributors,UNSAFE_MUTATIONS);
        }
    }
    private static Object retirementReceipt(Object token,boolean qualified){
        if(qualified){if(token instanceof UnsafeMutation mutation)mutation.retirementQualified=true;else if(token instanceof FieldMutation mutation)mutation.retirementQualified=true;}
        return token;
    }
    static Object memoryReceipt(java.util.concurrent.locks.ReentrantLock gate,Object receiver){
        try{return new FieldMutation(gate,CodeSourceBridge.executionMemoryBefore(receiver,invokingSources()),receiver);}
        catch(RuntimeException|Error failure){gate.unlock();throw failure;}
    }
    static Object fieldReceipt(java.util.concurrent.locks.ReentrantLock gate,Object receiver,Class<?> declaring,String name,String descriptor,boolean instruction){
        try{return new FieldMutation(gate,CodeSourceBridge.executionFieldMemoryBefore(receiver,declaring,name,descriptor,mutationSources(receiver),instruction),receiver);}
        catch(RuntimeException|Error failure){gate.unlock();throw failure;}
    }
    /** A selected field mutation holds the same short gate as the target's policy publication. */

    static Object staticFieldMutation(Object value,Class<?> symbolic,String signature){
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
        try {if(fieldWriteAllowed(target,declaring,name,value))return retirementReceipt(receipt,NetworkBridge.retirementActive()&&NetworkBridge.retirementFieldAllowed(target,declaring,name,value));}
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
                else if(copy.destination instanceof FieldMutation write)CodeSourceBridge.executionMemoryContributors(write.source(),sources);
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
                    if(mutation.root.completedWrites==mutation.writesAtStart){mutation.root.completedWrites++;mutation.root.retirementOnly&=mutation.retirementQualified;}
                    mutation.root.written=true;
                    CodeSourceBridge.executionMemoryContributors(mutation.receipt.source(),mutation.contributors);
                }
            }finally{
                mutation.closed=true;
                unsafeMutation(mutation.previous);
                if(mutation.root==mutation)try{
                    try{
                        if(mutation.written&&mutation.completedWrites>0&&mutation.retirementOnly)CodeSourceBridge.executionMemoryRetirement(mutation.receipt.source());
                        CodeSourceBridge.executionMemoryAfter(mutation.receipt.source(),written||mutation.written);
                    }
                    finally{if(written||mutation.written)SourceMapBridge.holderChanged(mutation.receipt.receiver());}
                }finally{mutation.receipt.close();}
            }
        }else if(token instanceof FieldMutation mutation){
            if(mutation.closed)return;
            if(!mutation.gate().isHeldByCurrentThread())throw new IllegalMonitorStateException("FIELD_MUTATION_THREAD_REQUIRED");
            try{
                try{if(written&&mutation.retirementQualified)CodeSourceBridge.executionMemoryRetirement(mutation.source());CodeSourceBridge.executionMemoryAfter(mutation.source(),written);}
                finally{if(written)SourceMapBridge.holderChanged(mutation.receiver());}
            }finally{mutation.close();}
        }else if(token instanceof java.util.concurrent.locks.ReentrantLock gate)gate.unlock();
    }
    static Throwable finishMutationRetaining(Object token,boolean written,Throwable original){
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
        try{return retirementReceipt(new FieldMutation(gate,CodeSourceBridge.executionArrayMemoryBefore(receiver,start,length,mutationSources(receiver)),receiver),
                length==1&&NetworkBridge.retirementActive()&&NetworkBridge.retirementArrayAllowed(receiver,start,Array.get(receiver,start)));}
        catch(RuntimeException|Error failure){gate.unlock();throw failure;}
    }
    static Module[] mutationSources(Module[] nativeSources){
        Objects.requireNonNull(nativeSources);
        Set<Module> sources=addModuleSources(null,invokingSources(nativeSources),false);
        sources=addModuleSources(sources,nativeSources,false);
        return sources==null?new Module[0]:sources.toArray(new Module[0]);
    }
    public static Object beginNativeArrayMutation(Object receiver,int start,int length,Module[] contributors){
        if(!NativeControl.memoryMutationBoundary(receiver))throw new SecurityException("ACTUAL_NATIVE_ARRAY_MUTATION_REQUIRED");
        java.util.concurrent.locks.ReentrantLock gate=fieldGate(receiver);gate.lock();
        try{return new FieldMutation(gate,CodeSourceBridge.executionArrayMemoryBefore(receiver,start,length,mutationSources(contributors)),receiver);}
        catch(RuntimeException|Error failure){gate.unlock();throw failure;}
    }
    public static Object beginNativeArrayCopyRead(Object source,int from,int length){
        if(!NativeControl.arrayCopyReadBoundary(source,from,length))throw new SecurityException("ACTUAL_NATIVE_ARRAYCOPY_READ_REQUIRED");
        long[] layout=ResourceBridge.arrayLayout(source.getClass());
        if(layout==null)throw new IllegalStateException("NATIVE_ARRAYCOPY_SOURCE_LAYOUT_UNAVAILABLE");
        // Start before the native snapshot. Keep the existing read-window
        // protocol without a source lock or a second source/destination order.
        return new HeapMemoryRead(null,CodeSourceBridge.executionByteMemoryReadBefore(source,layout[0]+(long)from*layout[1],(long)length*layout[1]));
    }
    public static Object beginNativeArrayCopy(Object source,int from,int length,Object target,int to,int prefix,Object read,Module[] contributors){
        if(!NativeControl.arrayCopyBoundary(source,from,length,target,to,prefix,read))throw new SecurityException("ACTUAL_NATIVE_ARRAYCOPY_REQUIRED");
        if(!(read instanceof HeapMemoryRead))throw new IllegalStateException("NATIVE_ARRAYCOPY_SOURCE_READ_REQUIRED");
        long[] layout=ResourceBridge.arrayLayout(source.getClass());
        if(layout==null)throw new IllegalStateException("NATIVE_ARRAYCOPY_SOURCE_LAYOUT_UNAVAILABLE");
        Module[] existing=readSources(read);
        Set<Module> combined=Collections.newSetFromMap(new IdentityHashMap<>());
        Collections.addAll(combined,mutationSources(contributors));Collections.addAll(combined,existing);
        java.util.concurrent.locks.ReentrantLock gate=fieldGate(target);gate.lock();FieldMutation destination=null;
        try{
            destination=new FieldMutation(gate,CodeSourceBridge.executionArrayMemoryBefore(target,to,prefix,combined.toArray(new Module[0])),target);
            // The source window is the exact requested range. On a partial
            // reference copy its suffix sources are retained conservatively;
            // only the compatible destination prefix receives write history.
            return new MemoryCopy(source,layout[0]+(long)from*layout[1],(long)length*layout[1],read,destination,existing);
        }catch(RuntimeException|Error failure){
            if(destination==null)gate.unlock();else finishMutationRetaining(destination,false,failure);
            // Native still owns and closes the read if this transfer fails.
            throw failure;
        }
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
        if(CodeSourceBridge.fieldGateMetadata(receiver))return (bulk?unsafeMemoryAllowed(receiver,offset,bytes):fieldGateMutationAllowed())?null:Boolean.FALSE;
        Object token=unsafeReceipt(receiver,offset,bytes,mutationSources(contributors),!bulk&&referenceKind(kind));
        try{
            if(bulk?unsafeMemoryAllowed(receiver,offset,bytes):unsafeWriteAllowed(receiver,offset,proposed,kind))
                return retirementReceipt(token,!bulk&&NetworkBridge.retirementActive()&&NetworkBridge.retirementWriteAllowed(receiver,offset,proposed,(int)bytes));
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
            if(bulk?unsafeMemoryAllowed(receiver,offset,bytes):BackingBridge.nativeWriteAllowed(receiver,offset,proposed,operation,kind))
                return retirementReceipt(token,!bulk&&NetworkBridge.retirementActive()&&NetworkBridge.retirementWriteAllowed(receiver,offset,proposed,(int)bytes));
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
    static void requireUnsafeWriter(){
        Class<?> caller,actual,internal;
        caller=actualMemoryCaller();
        try{actual=Class.forName("sun.misc.Unsafe",false,ClassLoader.getPlatformClassLoader());internal=Class.forName("jdk.internal.misc.Unsafe",false,null);}
        catch(ClassNotFoundException unavailable){throw new IllegalStateException("ACTUAL_UNSAFE_WRITE_UNAVAILABLE",unavailable);}
        if(caller!=actual&&caller!=internal)throw new SecurityException("ACTUAL_UNSAFE_WRITE_REQUIRED");
    }
    static Object unsafeReceipt(Object receiver,long offset,long length){
        if(NativeControl.unsafeControlScope()||CodeSourceBridge.fieldGateMetadata(receiver))return null;
        return unsafeReceipt(receiver,offset,length,receiver==null?invokingSources():mutationSources(receiver));
    }
    static Object unsafeReceipt(Object receiver,long offset,long length,Module[] contributors){
        return unsafeReceipt(receiver,offset,length,contributors,false);
    }
    static boolean referenceKind(String kind){return "Object".equals(kind)||"Reference".equals(kind);}
    static Object unsafeReceipt(Object receiver,long offset,long length,Module[] contributors,boolean reference){
        if(receiver==null)return rawReceipt(offset,length,contributors);
        if(CodeSourceBridge.fieldGateMetadata(receiver))return null;
        UnsafeMutation previous=unsafeMutation();
        if(sameUnsafeSpan(previous,receiver,offset,length)){
            UnsafeMutation use=new UnsafeMutation(receiver,offset,length,previous.receipt,previous,previous.root,contributors);
            unsafeMutation(use);return use;
        }
        java.util.concurrent.locks.ReentrantLock gate=fieldGate(receiver);gate.lock();
        try{
            Object observed=CodeSourceBridge.executionByteMemoryBefore(receiver,offset,length,contributors,reference);
            FieldMutation receipt=new FieldMutation(gate,observed,receiver);
            // An unobserved Java heap span has no source receipt to combine
            // across the VarHandle/Unsafe adapters. Keep its real receiver gate
            // and publication callback without creating a controller-owned
            // thread directory entry for every ordinary AtomicBoolean CAS.
            if(observed==null&&contributors.length==0)return receipt;
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
    static Object rawReceipt(long address,long bytes,Module[] contributors){
        return rawReceipt(address,bytes,contributors,false);
    }
    static Object rawReceipt(long address,long bytes,Module[] contributors,boolean reading){
        if(bytes<=0||!NativeControl.available())return null;
        long token=NativeControl.memoryWriteBegin(address,bytes,contributors,reading);return token==0?null:new RawMemoryMutation(token,reading);
    }
    static Object readReceipt(Object receiver,long offset,long bytes,Module[] contributors){
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
    static Module[] readSources(Object token){
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
    static Object copyReceipt(Object source,long sourceOffset,Object receiver,long offset,long bytes,Module[] contributors){
        return copyReceipt(source,sourceOffset,bytes,receiver,offset,bytes,contributors);
    }
    static Object copyReceipt(Object source,long sourceOffset,long sourceBytes,Object receiver,long offset,long bytes,Module[] contributors){
        if(!unsafeMemoryAllowed(receiver,offset,bytes))return Boolean.FALSE;if(bytes<=0)return null;
        Object read=null,destination=null;
        try{
            if(source==null&&NativeControl.available()){
                long token=NativeControl.memoryCopyReadBegin(sourceOffset,sourceBytes,contributors);if(token!=0)read=new RawMemoryMutation(token,true);
            }else if(source!=null)read=new HeapMemoryRead(null,CodeSourceBridge.executionByteMemoryReadBefore(source,sourceOffset,sourceBytes));
            Module[] existing=readSources(read);
            Set<Module> combined=Collections.newSetFromMap(new IdentityHashMap<>());Collections.addAll(combined,contributors);Collections.addAll(combined,existing);
            destination=unsafeReceipt(receiver,offset,bytes,combined.toArray(new Module[0]));
            if(!unsafeMemoryAllowed(receiver,offset,bytes)){
                Object deniedDestination=destination,deniedRead=read;destination=null;read=null;
                Throwable failure=finishMutationRetaining(deniedDestination,false,null);failure=finishMutationRetaining(deniedRead,false,failure);
                if(failure instanceof RuntimeException exception)throw exception;if(failure instanceof Error error)throw error;
                return Boolean.FALSE;
            }
            return new MemoryCopy(source,sourceOffset,sourceBytes,read,destination,existing);
        }catch(RuntimeException|Error failure){finishMutationRetaining(destination,false,failure);finishMutationRetaining(read,false,failure);throw failure;}
    }
    static Module[] copySources(MemoryCopy copy){
        if(copy.closed||copy.thread!=Thread.currentThread())throw new IllegalMonitorStateException("MEMORY_COPY_THREAD_REQUIRED");
        Set<Module> sources=Collections.newSetFromMap(new IdentityHashMap<>());Collections.addAll(sources,copy.before);
        Collections.addAll(sources,readSources(copy.read));
        return sources.toArray(new Module[0]);
    }
    static boolean rawMemoryWriterFrame(Class<?> type,String ignored){
        Class<?> host=type.getClassLoader()==null?type.getNestHost():type;
        return host!=TaskBridge.class&&host!=ControlBridge.class&&host!=NativeControl.class;
    }
    static boolean rawMemoryCaller(){
        Class<?> caller=NativeControl.available()?NativeControl.rawMemoryWriterCaller():WALKER.walk(RAW_MEMORY_CALLER);
        // Reflection and MethodHandle implementation frames are omitted by the
        // original walker; use its exact selection when native sees one first.
        if(caller!=null){String name=caller.getName();
            if(caller.isHidden()||name.startsWith("java.lang.reflect.")||name.startsWith("jdk.internal.reflect.")||name.startsWith("java.lang.invoke."))
                caller=WALKER.walk(RAW_MEMORY_CALLER);
        }
        if(caller==ResourceBridge.class||caller==CodeSourceBridge.class||caller!=null&&caller.getClassLoader()==null&&caller.getNestHost()==ExecutionFlow.class||ControlBridge.owns(caller))return true;
        try{
            if(caller==Class.forName("sun.misc.Unsafe",false,ClassLoader.getPlatformClassLoader())||caller==Class.forName("jdk.internal.misc.Unsafe",false,null))return true;
        }catch(ClassNotFoundException unavailable){return false;}
        return caller!=null&&caller.getClassLoader()==null&&HandleWriters.roots.contains(caller.getNestHost());
    }
    static boolean sameUnsafeSpan(UnsafeMutation use,Object receiver,long offset,long length){
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
        requireUnsafeWriter();return NativeControl.unsafeControlScope()||CodeSourceBridge.fieldGateMetadata(receiver)?null:unsafeReceipt(receiver,offset,ResourceBridge.unsafeWidth(kind),receiver==null?invokingSources():mutationSources(receiver),referenceKind(kind));
    }
    public static Object beginUnsafeRead(Object receiver,long offset,String kind){
        requireUnsafeWriter();return receiver!=null||NativeControl.unsafeControlScope()?null:rawReceipt(offset,ResourceBridge.unsafeWidth(kind),invokingSources(),true);
    }
    public static Object beginUnsafeCopy(Object source,long sourceOffset,Object receiver,long offset,long length){
        requireUnsafeWriter();return NativeControl.unsafeControlScope()?null:copyReceipt(source,sourceOffset,receiver,offset,length,invokingSources());
    }
    static final class HandleWriters {
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
        requireHandleWriter(actualMemoryCaller());return NativeControl.unsafeControlScope()||CodeSourceBridge.fieldGateMetadata(receiver)?null:unsafeReceipt(receiver,offset,ResourceBridge.unsafeWidth(kind),receiver==null?invokingSources():mutationSources(receiver),referenceKind(kind));
    }
    public static Object beginHandleRead(Object receiver,long offset,String kind){
        requireHandleWriter(actualMemoryCaller());return receiver!=null||NativeControl.unsafeControlScope()?null:rawReceipt(offset,ResourceBridge.unsafeWidth(kind),invokingSources(),true);
    }
    static Class<?> actualMemoryCaller(){
        // getCallerClass always hides MethodHandle/VarHandle frames, including
        // real JDK writers. Inspect the first actual frame outside this bridge.
        return ORIGIN_WALKER.walk(MEMORY_CALLER);
    }
    static void requireHandleWriter(Class<?> caller){
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
    static Object recoveryArrayMutation(Object array,int start,int length,long previousRevision,boolean resume){
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
    static final ClassValue<java.util.concurrent.ConcurrentHashMap<String,Class<?>>> FIELD_DECLARING=
            new ClassValue<>() {
                @Override protected java.util.concurrent.ConcurrentHashMap<String,Class<?>> computeValue(Class<?> type) {
                    return new java.util.concurrent.ConcurrentHashMap<>();
                }
            };
    static Class<?> actualFieldOwner(Class<?> symbolic,String name,String descriptor) {
        if(symbolic==null||name==null||descriptor==null)return null;
        String key=name+'\u0000'+descriptor;
        var fields=FIELD_DECLARING.get(symbolic);Class<?> known=fields.get(key);
        if(known!=null)return known;
        return fields.computeIfAbsent(key,ignored->{
            Class<?> declaring=findFieldOwner(symbolic,name,descriptor);
            return declaring==null?Void.class:declaring;
        });
    }
    static Class<?> findFieldOwner(Class<?> type,String name,String descriptor) {
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
        if(CodeSourceBridge.fieldGateMetadata(receiver))return fieldGateMutationAllowed();
        if(!CodeSourceBridge.fieldAllowed(receiver,declaring,name)&&!coreWriter())return false;
        if(!ResourceBridge.fieldMutationAllowed(receiver,declaring))return false;
        if((modStopped(objectModule(receiver==null?declaring:receiver))||stoppedInvocation())&&!coreWriter()&&!ClientBridge.cleanupField(receiver,declaring,name)
                &&!NetworkBridge.retirementFieldAllowed(receiver,declaring,name,value))return false;
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
        if(CodeSourceBridge.fieldGateMetadata(receiver))return fieldGateMutationAllowed();
        if(!CodeSourceBridge.fieldAllowed(receiver,field.getDeclaringClass(),field.getName())&&!coreWriter())return false;
        if(!ResourceBridge.fieldMutationAllowed(receiver,field.getDeclaringClass()))return false;
        if((modStopped(objectModule(receiver==null?field.getDeclaringClass():receiver))||stoppedInvocation())&&!coreWriter()&&!ClientBridge.cleanupField(receiver,field.getDeclaringClass(),field.getName())
                &&!NetworkBridge.retirementFieldAllowed(receiver,field.getDeclaringClass(),field.getName(),value))return false;
        if(receiver instanceof java.util.Map.Entry<?,?>&&controlled(SourceMapBridge.ownerOf(receiver))&&!controlCaller())return false;
        if(ownState(field.getDeclaringClass())||receiver!=null&&ownState(receiver.getClass())||controlled(receiver)&&!controlCaller())return false;
        Guards current=guards;
        if(current==null)return true;
        Object target=receiver==null?field.getDeclaringClass():receiver;
        try { return !(boolean)current.field().invokeExact(target,field.getDeclaringClass(),field.getName(),value); }
        catch(Throwable failure) { throw new IllegalStateException("FIELD_POLICY_FAILED",failure); }
    }
    static volatile java.lang.invoke.MethodHandle lifeResults;
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
    static volatile java.lang.invoke.MethodHandle dispatchResults;
    public static synchronized void installDispatchResults(Class<?> owner,java.lang.invoke.MethodHandle result) {
        requirePolicyInstaller();
        Guards current=guards;
        if(current==null||current.owner()!=owner)throw new SecurityException("DISPATCH_RESULT_OWNER");
        dispatchResults=Objects.requireNonNull(result).asType(java.lang.invoke.MethodType.methodType(Object.class,Object.class,String.class,Object.class));
    }
    public static Object dispatchDeniedResult(Object receiver,String operation,Object arguments) {
        var result=dispatchResults;
        if(result==null)throw new IllegalStateException("DISPATCH_RESULT_POLICY_UNAVAILABLE");
        try{return (Object)result.invokeExact(receiver,operation,arguments);}
        catch(Throwable failure){throw new IllegalStateException("DISPATCH_RESULT_POLICY_FAILED",failure);}
    }
    record EquipmentInvocations(java.lang.invoke.MethodHandle begin,java.lang.invoke.MethodHandle denied,
            java.lang.invoke.MethodHandle result,java.lang.invoke.MethodHandle end,java.lang.invoke.MethodHandle failed) { }
    static volatile EquipmentInvocations equipmentInvocations;
    private static volatile java.lang.invoke.MethodHandle[] capabilityCallbacks;
    public static synchronized void installCapabilityCallbacks(Class<?> owner,java.lang.invoke.MethodHandle[] callbacks){
        requirePolicyInstaller();if(guards==null||guards.owner()!=owner)throw new SecurityException("EQUIPMENT_CALLBACK_OWNER");
        java.lang.invoke.MethodType[] types={
            java.lang.invoke.MethodType.methodType(void.class,Object.class,Object.class,Object.class,String.class),
            java.lang.invoke.MethodType.methodType(Object.class,Object.class,Object.class,String.class),
            java.lang.invoke.MethodType.methodType(Object.class,Object.class,Object.class,Object.class,String.class),
            java.lang.invoke.MethodType.methodType(boolean.class,Object.class,Object.class,Object.class,String.class),
            java.lang.invoke.MethodType.methodType(void.class,Object.class,Object.class,String.class),
            java.lang.invoke.MethodType.methodType(Object.class,Object.class,Object.class,String.class),
            java.lang.invoke.MethodType.methodType(Object.class,Object.class,Object.class,Object.class,String.class)};
        if(callbacks==null||callbacks.length!=types.length)throw new IllegalArgumentException("EQUIPMENT_CALLBACK_HANDLES");
        var next=callbacks.clone();for(int at=0;at<next.length;at++)next[at]=Objects.requireNonNull(next[at]).asType(types[at]);capabilityCallbacks=next;
    }
    @SuppressWarnings("unchecked") private static <E extends Throwable> RuntimeException capabilityFailure(Throwable failure)throws E{throw (E)failure;}
    @SuppressWarnings("unchecked") public static void capabilityAccept(Object optional,Object consumer,Object value,String phase){
        var callbacks=capabilityCallbacks;
        if(callbacks==null){((java.util.function.Consumer<Object>)consumer).accept(value);return;}
        try{callbacks[0].invokeExact(optional,consumer,value,phase);}catch(Throwable failure){throw ControlBridge.<RuntimeException>capabilityFailure(failure);}
    }
    @SuppressWarnings("unchecked") public static Object capabilitySupply(Object optional,Object supplier,String phase){
        var callbacks=capabilityCallbacks;if(callbacks==null)return ((java.util.function.Supplier<Object>)supplier).get();
        try{return (Object)callbacks[1].invokeExact(optional,supplier,phase);}catch(Throwable failure){throw ControlBridge.<RuntimeException>capabilityFailure(failure);}
    }
    @SuppressWarnings("unchecked") public static Object capabilityApply(Object optional,Object function,Object value,String phase){
        var callbacks=capabilityCallbacks;if(callbacks==null)return ((java.util.function.Function<Object,Object>)function).apply(value);
        try{return (Object)callbacks[2].invokeExact(optional,function,value,phase);}catch(Throwable failure){throw ControlBridge.<RuntimeException>capabilityFailure(failure);}
    }
    @SuppressWarnings("unchecked") public static boolean capabilityTest(Object optional,Object predicate,Object value,String phase){
        var callbacks=capabilityCallbacks;if(callbacks==null)return ((java.util.function.Predicate<Object>)predicate).test(value);
        try{return (boolean)callbacks[3].invokeExact(optional,predicate,value,phase);}catch(Throwable failure){throw ControlBridge.<RuntimeException>capabilityFailure(failure);}
    }
    public static void capabilityDerived(Object parent,Object returned,String phase){
        var callbacks=capabilityCallbacks;if(callbacks==null)return;
        try{callbacks[4].invokeExact(parent,returned,phase);}catch(Throwable failure){throw ControlBridge.<RuntimeException>capabilityFailure(failure);}
    }
    @SuppressWarnings("unchecked") public static Object capabilityComposed(Object callback,Object value,String contract){
        var callbacks=capabilityCallbacks;
        if(callbacks==null)return switch(contract){case "function"->((java.util.function.Function<Object,Object>)callback).apply(value);case "consumer"->{((java.util.function.Consumer<Object>)callback).accept(value);yield null;}case "predicate"->Boolean.valueOf(((java.util.function.Predicate<Object>)callback).test(value));default->throw new IllegalArgumentException("EQUIPMENT_CALLBACK_CONTRACT");};
        try{return (Object)callbacks[5].invokeExact(callback,value,contract);}catch(Throwable failure){throw ControlBridge.<RuntimeException>capabilityFailure(failure);}
    }
    @SuppressWarnings("unchecked") public static Object capabilityComposedBi(Object callback,Object first,Object second,String contract){
        var callbacks=capabilityCallbacks;
        if(callbacks==null)return switch(contract){case "bifunction"->((java.util.function.BiFunction<Object,Object,Object>)callback).apply(first,second);case "biconsumer"->{((java.util.function.BiConsumer<Object,Object>)callback).accept(first,second);yield null;}default->throw new IllegalArgumentException("EQUIPMENT_CALLBACK_CONTRACT");};
        try{return (Object)callbacks[6].invokeExact(callback,first,second,contract);}catch(Throwable failure){throw ControlBridge.<RuntimeException>capabilityFailure(failure);}
    }
    private static volatile java.lang.invoke.MethodHandle[] equipmentSlots;
    public static synchronized void installEquipmentSlots(Class<?> owner,java.lang.invoke.MethodHandle[] callbacks){
        requirePolicyInstaller();if(guards==null||guards.owner()!=owner)throw new SecurityException("EQUIPMENT_SLOT_OBSERVER_OWNER");
        java.lang.invoke.MethodType[] types={
            java.lang.invoke.MethodType.methodType(Object.class,Object.class,Object.class),
            java.lang.invoke.MethodType.methodType(void.class,Object.class,Object.class,Class.class,String.class,String.class,Object.class),
            java.lang.invoke.MethodType.methodType(void.class,Object.class,Object.class,int.class,Object.class),
            java.lang.invoke.MethodType.methodType(void.class,Object.class,Object.class,Object.class,Object.class,boolean.class),
            java.lang.invoke.MethodType.methodType(void.class,Object.class,Object.class,String.class,String.class,Object.class),
            java.lang.invoke.MethodType.methodType(void.class,Object.class),
            java.lang.invoke.MethodType.methodType(void.class,Object.class,Object.class),
            java.lang.invoke.MethodType.methodType(void.class,Object.class,Throwable.class),
            java.lang.invoke.MethodType.methodType(Object.class,Object.class,Object.class,Class.class,String.class,String.class,int.class,Object[].class),
            java.lang.invoke.MethodType.methodType(void.class,Object.class,Object.class),
            java.lang.invoke.MethodType.methodType(void.class,Object.class,Throwable.class),
            java.lang.invoke.MethodType.methodType(Object.class,Object.class,Class.class,String.class,String.class,Object[].class),
            java.lang.invoke.MethodType.methodType(void.class,Object.class,Object.class),
            java.lang.invoke.MethodType.methodType(void.class,Object.class,Throwable.class)};
        if(callbacks==null||callbacks.length!=types.length)throw new IllegalArgumentException("EQUIPMENT_SLOT_CALLBACKS");
        var next=callbacks.clone();for(int at=0;at<next.length;at++)next[at]=Objects.requireNonNull(next[at]).asType(types[at]);equipmentSlots=next;
    }
    public static Object beginEquipmentSlotRead(Object receiver,Object slot){
        var callbacks=equipmentSlots;if(callbacks==null)return null;
        try{return (Object)callbacks[0].invokeExact(receiver,slot);}catch(Throwable failure){throw slotFailure(failure);}
    }
    public static void equipmentSlotFieldRead(Object token,Object holder,Class<?> symbolic,String name,String descriptor,Object value){
        var callbacks=equipmentSlots;if(callbacks==null||token==null)return;
        try{callbacks[1].invokeExact(token,holder,symbolic,name,descriptor,value);}catch(Throwable failure){throw slotFailure(failure);}
    }
    public static void equipmentSlotArrayRead(Object token,Object array,int index,Object value){
        var callbacks=equipmentSlots;if(callbacks==null||token==null)return;
        try{callbacks[2].invokeExact(token,array,index,value);}catch(Throwable failure){throw slotFailure(failure);}
    }
    public static void equipmentSlotContainerRead(Object token,Object container,Object key,Object value,boolean map){
        var callbacks=equipmentSlots;if(callbacks==null||token==null)return;
        try{callbacks[3].invokeExact(token,container,key,value,map);}catch(Throwable failure){throw slotFailure(failure);}
    }
    public static void equipmentSlotSelectorRead(Object token,Object receiver,String name,String descriptor,Object value){
        var callbacks=equipmentSlots;if(callbacks==null||token==null)return;
        try{callbacks[4].invokeExact(token,receiver,name,descriptor,value);}catch(Throwable failure){throw slotFailure(failure);}
    }
    public static void equipmentSlotUnresolved(Object token){
        var callbacks=equipmentSlots;if(callbacks==null||token==null)return;
        try{callbacks[5].invokeExact(token);}catch(Throwable failure){throw slotFailure(failure);}
    }
    public static void observeEquipmentSlot(Object token,Object value){
        var callbacks=equipmentSlots;if(callbacks==null||token==null)return;
        try{callbacks[6].invokeExact(token,value);}catch(Throwable failure){throw slotFailure(failure);}
    }
    public static void failedEquipmentSlotRead(Object token,Throwable original){
        var callbacks=equipmentSlots;if(callbacks==null||token==null)return;
        try{callbacks[7].invokeExact(token,original);}catch(Throwable failure){if(failure!=original)original.addSuppressed(failure);}
    }
    public static Object beginEquipmentSlotCall(Object token,Object receiver,Class<?> symbolic,String name,String descriptor,int opcode,Object[] arguments){
        var callbacks=equipmentSlots;if(callbacks==null||token==null)return null;
        try{return (Object)callbacks[8].invokeExact(token,receiver,symbolic,name,descriptor,opcode,arguments);}catch(Throwable failure){throw slotFailure(failure);}
    }
    public static void endEquipmentSlotCall(Object token,Object result){
        var callbacks=equipmentSlots;if(callbacks==null||token==null)return;
        try{callbacks[9].invokeExact(token,result);}catch(Throwable failure){throw slotFailure(failure);}
    }
    public static void failedEquipmentSlotCall(Object token,Throwable original){
        var callbacks=equipmentSlots;if(callbacks==null||token==null)return;
        try{callbacks[10].invokeExact(token,original);}catch(Throwable failure){if(failure!=original)original.addSuppressed(failure);}
    }
    public static Object beginEquipmentSlotMethod(Object receiver,Class<?> declaring,String name,String descriptor,Object[] arguments){
        var callbacks=equipmentSlots;if(callbacks==null)return null;
        try{return (Object)callbacks[11].invokeExact(receiver,declaring,name,descriptor,arguments);}catch(Throwable failure){throw slotFailure(failure);}
    }
    public static void endEquipmentSlotMethod(Object token,Object result){
        var callbacks=equipmentSlots;if(callbacks==null||token==null)return;
        try{callbacks[12].invokeExact(token,result);}catch(Throwable failure){throw slotFailure(failure);}
    }
    public static void failedEquipmentSlotMethod(Object token,Throwable original){
        var callbacks=equipmentSlots;if(callbacks==null||token==null)return;
        try{callbacks[13].invokeExact(token,original);}catch(Throwable failure){if(failure!=original)original.addSuppressed(failure);}
    }
    private static RuntimeException slotFailure(Throwable failure){
        if(failure instanceof Error error)throw error;if(failure instanceof RuntimeException runtime)return runtime;
        return new IllegalStateException("EQUIPMENT_SLOT_OBSERVER_FAILED",failure);
    }
    public static synchronized void installEquipmentActions(Class<?> owner,java.lang.invoke.MethodHandle begin,java.lang.invoke.MethodHandle denied,
            java.lang.invoke.MethodHandle result,java.lang.invoke.MethodHandle end,java.lang.invoke.MethodHandle failed) {
        requirePolicyInstaller();if(guards==null||guards.owner()!=owner)throw new SecurityException("EQUIPMENT_INVOCATION_OWNER");
        var next=new EquipmentInvocations(
                Objects.requireNonNull(begin).asType(java.lang.invoke.MethodType.methodType(Object.class,Object.class,Object.class,String.class)),
                Objects.requireNonNull(denied).asType(java.lang.invoke.MethodType.methodType(boolean.class,Object.class)),
                Objects.requireNonNull(result).asType(java.lang.invoke.MethodType.methodType(Object.class,Object.class,String.class,Object.class)),
                Objects.requireNonNull(end).asType(java.lang.invoke.MethodType.methodType(void.class,Object.class)),
                Objects.requireNonNull(failed).asType(java.lang.invoke.MethodType.methodType(void.class,Object.class,Throwable.class)));
        equipmentInvocations=next;
    }
    public static Object beginEquipmentInvocation(Object receiver,Object arguments,String operation) {
        var callbacks=equipmentInvocations;if(callbacks==null)return null;
        try{return (Object)callbacks.begin.invokeExact(receiver,arguments,operation);}
        catch(Throwable failure){throw new IllegalStateException("EQUIPMENT_INVOCATION_BEGIN_FAILED",failure);}
    }
    public static boolean equipmentInvocationDenied(Object token) {
        var callbacks=equipmentInvocations;if(callbacks==null)return false;
        try{return (boolean)callbacks.denied.invokeExact(token);}
        catch(Throwable failure){throw new IllegalStateException("EQUIPMENT_INVOCATION_GATE_FAILED",failure);}
    }
    public static Object equipmentInvocationResult(Object receiver,String operation,Object arguments) {
        var callbacks=equipmentInvocations;if(callbacks==null)throw new IllegalStateException("EQUIPMENT_INVOCATION_POLICY_UNAVAILABLE");
        try{return (Object)callbacks.result.invokeExact(receiver,operation,arguments);}
        catch(Throwable failure){throw new IllegalStateException("EQUIPMENT_INVOCATION_RESULT_FAILED",failure);}
    }
    public static void endEquipmentInvocation(Object token) {
        var callbacks=equipmentInvocations;if(callbacks==null)return;
        try{callbacks.end.invokeExact(token);}
        catch(RuntimeException|Error failure){throw failure;}
        catch(Throwable failure){throw new IllegalStateException("EQUIPMENT_INVOCATION_END_FAILED",failure);}
    }
    public static void failedEquipmentInvocation(Object token,Throwable failure) {
        var callbacks=equipmentInvocations;if(callbacks==null)return;
        try{callbacks.failed.invokeExact(token,failure);}
        catch(Throwable cleanup){if(cleanup!=failure)failure.addSuppressed(cleanup);}
    }
    /** Existing source continuity is a condition of an exact game action, never an input credential. */
    public static boolean equipmentActionSourceAllowed() {
        return equipmentActionSourcesAllowed(WALKER.getCallerClass(),null,null,null);
    }
    public static boolean equipmentActionSourceAllowed(Class<?> delegatedItem) {
        return equipmentActionSourcesAllowed(WALKER.getCallerClass(),null,delegatedItem,null);
    }
    public static boolean equipmentActionSourceAllowed(Class<?> gameMode,Class<?> delegatedItem) {
        return equipmentActionSourcesAllowed(WALKER.getCallerClass(),gameMode,delegatedItem,null);
    }
    /** Only the Core's current receiver chain may extend an existing action's source set. */
    public static boolean equipmentCarrierSourceAllowed(Class<?> gameMode,Class<?> delegatedItem,Class<?>[] actualCarriers) {
        Objects.requireNonNull(actualCarriers);
        return equipmentActionSourcesAllowed(WALKER.getCallerClass(),gameMode,delegatedItem,actualCarriers);
    }
    private static boolean equipmentSourceAllowed(Module source,Module owner,Module delegate,Module game,Class<?>[] carriers) {
        if(source==null||modStopped(source))return false;
        if(!producer(source)||source==owner||source==delegate||source==game)return true;
        if(carriers!=null)for(Class<?> carrier:carriers)if(carrier!=null&&source==DefinitionBridge.module(carrier))return true;
        return false;
    }
    private static boolean equipmentActionSourcesAllowed(Class<?> caller,Class<?> gameMode,Class<?> delegatedItem,Class<?>[] carriers) {
        Guards current=guards;
        if(current==null||!caller.getName().equals("dev.ronova.pro.EquipmentPolicy")
                ||caller.getClassLoader()!=current.owner().getClassLoader()||DefinitionBridge.module(caller)!=current.owner().getModule()
                ||!Objects.equals(caller.getProtectionDomain().getCodeSource(),current.owner().getProtectionDomain().getCodeSource()))
            throw new SecurityException("EQUIPMENT_ACTION_SOURCE_OWNER");
        if(sourceUnknown()||stoppedInvocation())return false;
        Module delegate=delegatedItem==null?null:DefinitionBridge.module(delegatedItem);
        Module game=gameMode==null?null:DefinitionBridge.module(gameMode);
        for(Module source:scopedSources())if(!equipmentSourceAllowed(source,current.owner().getModule(),delegate,game,carriers))return false;
        return ORIGIN_WALKER.walk(frames->{var cursor=frames.iterator();while(cursor.hasNext()) {
            var frame=cursor.next();Class<?> type=frame.getDeclaringClass();
            if(ownState(type)||ControlBridge.owns(type))continue;
            for(Module source:CodeSourceBridge.frameSources(frame))
                if(!equipmentSourceAllowed(source,current.owner().getModule(),delegate,game,carriers))return false;
            Module physical=DefinitionBridge.module(type);
            if(!equipmentSourceAllowed(physical,current.owner().getModule(),delegate,game,carriers))return false;
        }return true;});
    }
    static boolean taskEffectAllowed(Guards current,Object task,String effect) {
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
    static volatile java.lang.invoke.MethodHandle publicationGate;
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

    static final class EventKey extends java.lang.ref.WeakReference<Object> {
        final int hash;
        EventKey(Object listener,java.lang.ref.ReferenceQueue<Object> queue){super(listener,queue);hash=System.identityHashCode(listener);}
        @Override public int hashCode(){return hash;}
        @Override public boolean equals(Object other){return this==other||other instanceof EventKey key&&get()!=null&&get()==key.get();}
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
    static void reapCreationOwners() {
        EventKey stale;while((stale=(EventKey)CREATION_QUEUE.poll())!=null) {
            CREATION_OWNERS.remove(stale);CREATION_SOURCES.remove(stale);CREATION_UNKNOWN.remove(stale);TASK_OWNERS.remove(stale);TASK_SCHEDULERS.remove(stale);SHARED_SCHEDULERS.remove(stale);
            TIMER_RUNNING.remove(stale);
            SCHEDULER_SOURCES.remove(stale);SCHEDULER_SUBMISSIONS.remove(stale);SCHEDULER_RETIREMENTS.remove(stale);
            THREAD_ORIGINS.remove(stale);RETIRED_THREADS.remove(stale);THREAD_TIMERS.remove(stale);
        }
    }
    static final java.lang.ref.ReferenceQueue<Object> EVENT_QUEUE=new java.lang.ref.ReferenceQueue<>();

    static final java.util.HashMap<EventKey,Module> EVENT_OWNERS=new java.util.HashMap<>();

    static final java.lang.ref.ReferenceQueue<Object> CREATION_QUEUE=new java.lang.ref.ReferenceQueue<>();

    static final java.util.HashMap<EventKey,Module> CREATION_OWNERS=new java.util.HashMap<>();

    static final java.util.Map<EventKey,Set<Module>> CREATION_SOURCES=new java.util.HashMap<>();

    static final Set<EventKey> CREATION_UNKNOWN=new java.util.HashSet<>();

}
