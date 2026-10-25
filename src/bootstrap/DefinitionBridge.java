package dev.ronova.pro.bootstrap;

import java.lang.invoke.MethodHandles;
import java.lang.reflect.Array;
import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Future;
import java.util.function.Function;
import java.util.stream.DoubleStream;
import java.util.stream.IntStream;
import java.util.stream.LongStream;
import java.util.stream.Stream;

/** Records actual definition scopes; an application stack alone is never a class origin. */
public final class DefinitionBridge {
    private static final StackWalker WALKER=StackWalker.getInstance(Set.of(StackWalker.Option.RETAIN_CLASS_REFERENCE,StackWalker.Option.SHOW_HIDDEN_FRAMES));
    // Named, eagerly created walkers must not link a lambda while Lookup is defining that lambda.
    private static final Function<Stream<StackWalker.StackFrame>,Class<?>> CALLER=new Caller();
    private static final Function<Stream<StackWalker.StackFrame>,StackWalker.StackFrame> FRAME=new CallerFrame();
    private static final Function<Stream<StackWalker.StackFrame>,Class<?>> LOOKUP_CONSUMER=new LookupConsumer();
    private static final class LookupConsumer implements Function<Stream<StackWalker.StackFrame>,Class<?>> {
        public Class<?> apply(Stream<StackWalker.StackFrame> frames){
            var cursor=frames.iterator();boolean platform=false,handleProxy=false;
            while(cursor.hasNext()) {
                var frame=cursor.next();Class<?> type=frame.getDeclaringClass();
                if(type.getNestHost()==DefinitionBridge.class)continue;
                if(type==MethodHandles.class||type==MethodHandles.Lookup.class||type==ClassLoader.class){platform=true;continue;}
                if(!platform)return null;
                if(handleProxy&&java.lang.reflect.Proxy.isProxyClass(type)){handleProxy=false;continue;}
                // These are the VM's actual reflection and method-handle adapters.
                // The first consumer beyond them is the caller of this derivation.
                if(type.getClassLoader()==null&&(type.getName().startsWith("java.lang.invoke.")
                        ||type.getName().startsWith("jdk.internal.reflect.")||type==Method.class)) {
                    if(type.getName().equals("java.lang.invoke.MethodHandleProxies$1")&&frame.getMethodName().equals("invoke"))handleProxy=true;
                    continue;
                }
                return type;
            }
            return null;
        }
    }
    private static final class CallerFrame implements Function<Stream<StackWalker.StackFrame>,StackWalker.StackFrame> {
        public StackWalker.StackFrame apply(Stream<StackWalker.StackFrame> frames){
            var cursor=frames.iterator();while(cursor.hasNext()){var frame=cursor.next();if(frame.getDeclaringClass().getNestHost()!=DefinitionBridge.class)return frame;}return null;
        }
    }
    private static final class Caller implements Function<Stream<StackWalker.StackFrame>,Class<?>> {
        public Class<?> apply(Stream<StackWalker.StackFrame> frames) {
            Iterator<StackWalker.StackFrame> cursor=frames.iterator();
            while(cursor.hasNext()){Class<?> type=cursor.next().getDeclaringClass();if(type.getNestHost()!=DefinitionBridge.class)return type;}
            return null;
        }
    }
    // Each class owns its authoritative binding slot. Reads observe late
    // publication directly; registration and batch snapshots share this monitor.
    // ClassValue also retires the slot with its class, without a second weak map.
    private static final ClassValue<OriginSlot> ORIGINS=new ClassValue<>(){
        protected OriginSlot computeValue(Class<?> type){return new OriginSlot();}
    };
    private static final IdentityHashMap<Module,String> RETURNS=new IdentityHashMap<>();
    private static final IdentityHashMap<Module,UUID> FIXED=new IdentityHashMap<>();
    private static final IdentityHashMap<Thread,Scope> CURRENT=new IdentityHashMap<>();
    private static volatile Scope[] liveScopes=new Scope[0];
    private static volatile Method transformer,acceptedDefinition;
    private static final class Origin {
        final Module module;volatile String acceptedImage;
        Origin(Module module){this.module=module;}
    }
    private static final class OriginSlot { volatile Origin origin; }
    private static final class Scope {
        final Scope previous;final Module module;final ClassLoader loader;final String name;
        final byte[] bytes;final Thread thread;
        Scope(Scope previous,Module module,ClassLoader loader,String name,byte[] bytes){
            if(WALKER.getCallerClass()!=DefinitionBridge.class)throw new SecurityException("DEFINITION_SCOPE_OWNER");
            this.previous=previous;this.module=module;this.loader=loader;this.name=name;this.bytes=bytes;thread=Thread.currentThread();
        }
    }
    private DefinitionBridge() { }
    public static void install(Class<?> agent) throws ReflectiveOperationException {
        requireAgent(WALKER.getCallerClass());if(!ControlBridge.owns(agent))throw new SecurityException("DEFINITION_AGENT_REQUIRED");
        transformer=agent.getMethod("transformDefined",Module.class,ClassLoader.class,byte[].class,boolean.class);
        acceptedDefinition=agent.getMethod("externalDefinitionAccepted",Class.class,byte[].class);
        directCaller();ControlBridge.prepareDefinitionCalls();
        publish();
    }
    private static void requireAgent(Class<?> caller){if(!ControlBridge.owns(caller))throw new SecurityException("DEFINITION_AGENT_REQUIRED");}
    private static void publish(){ControlBridge.controlPublication(CURRENT,new Object[]{ORIGINS,RETURNS,FIXED,transformer,acceptedDefinition});}
    public static Module module(Class<?> type) {
        Origin origin=ORIGINS.get(type).origin;return origin==null?type.getModule():origin.module;
    }
    private static Origin bind(Class<?> actual,Module module) {
        synchronized(ORIGINS) {
            OriginSlot slot=ORIGINS.get(actual);Origin origin=slot.origin;
            if(origin==null){origin=new Origin(module);slot.origin=origin;}return origin;
        }
    }
    private static void accepted(Class<?> actual,Scope scope){
        if(scope.module==null)return;
        Origin origin=bind(actual,scope.module);
        if(!actual.isHidden())return; // Named definitions have a subsequent VM transformer chain.
        Method callback=acceptedDefinition;
        if(callback!=null)try{callback.invoke(null,actual,scope.bytes);}
        catch(java.lang.reflect.InvocationTargetException failure){
            Throwable cause=failure.getCause();if(cause instanceof RuntimeException runtime)throw runtime;if(cause instanceof Error error)throw error;
            throw new IllegalStateException("ACTUAL_HIDDEN_DEFINITION_RECORD_FAILED",cause);
        }catch(ReflectiveOperationException failure){throw new IllegalStateException("ACTUAL_HIDDEN_DEFINITION_RECORD_FAILED",failure);}
        try{
            String image=HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(scope.bytes));
            origin.acceptedImage=image+":accepted-hidden";
        }catch(java.security.NoSuchAlgorithmException impossible){throw new AssertionError(impossible);}
    }
    /** This is recorded only after the VM supplied the actual class or entered its initializer. */
    public static String acceptedImage(Class<?> actual){requireAgent(WALKER.getCallerClass());Origin origin=actual==null?null:ORIGINS.get(actual).origin;return origin==null?null:origin.acceptedImage;}
    /** The final transformer may consult only an actual recorded class or this exact definition scope. */
    public static Module origin(Class<?> type) {requireAgent(WALKER.getCallerClass());Origin origin=type==null?null:ORIGINS.get(type).origin;return origin==null?null:origin.module;}
    public static Module[] origins(Class<?>[] types) {
        requireAgent(WALKER.getCallerClass());Module[] origins=new Module[types.length];
        synchronized(ORIGINS){for(int i=0;i<types.length;i++)if(types[i]!=null){Origin origin=ORIGINS.get(types[i]).origin;origins[i]=origin==null?null:origin.module;}}
        return origins;
    }
    public static Module definingModule(ClassLoader loader,String name) {
        requireAgent(WALKER.getCallerClass());synchronized(CURRENT){Scope scope=CURRENT.get(Thread.currentThread());return scope!=null&&scope.loader==loader&&Objects.equals(scope.name,name)?scope.module:null;}
    }
    public static void policy(Module module,String mode) {
        requireAgent(WALKER.getCallerClass());publishPolicy(module,mode,null,false);
    }
    public static void policy(Module module,String mode,String fixedUuid) {
        requireAgent(WALKER.getCallerClass());publishPolicy(module,mode,fixedUuid,true);
    }
    private static void publishPolicy(Module module,String mode,String fixedUuid,boolean supplied) {
        if(!Set.of("default","null","empty","uuid-fixed","uuid-each","invalid-id").contains(mode))throw new IllegalArgumentException(mode);
        UUID fixed=mode.equals("uuid-fixed")&&supplied?(UUID)returnIdentifier(mode,fixedUuid,false):null;
        synchronized(RETURNS){synchronized(FIXED){
            if(mode.equals("uuid-fixed")) {
                // The generation-aware agent supplies the same UUID used by
                // its Java bodies. The older overload retains its module value.
                if(!supplied){fixed=FIXED.get(module);if(fixed==null)fixed=UUID.randomUUID();}
                FIXED.put(module,fixed);
            }
            RETURNS.put(module,mode);
        }}
        publish();
    }
    /** Produces a return value only; it neither installs a policy nor grants control. */
    public static Object returnIdentifier(String mode,String fixedUuid,boolean stringResult) {
        UUID id=switch(mode) {
            case "uuid-fixed" -> {
                if(fixedUuid==null)throw new IllegalArgumentException("FIXED_RETURN_UUID_REQUIRED");
                yield UUID.fromString(fixedUuid);
            }
            case "uuid-each" -> NativeControl.returnIdentifier();
            default -> throw new IllegalArgumentException("IDENTIFIER_RETURN_POLICY:"+mode);
        };
        return stringResult?id.toString():id;
    }
    /** A fresh terminal adapter; no caller-supplied target or work enters its construction. */
    public static CompletableFuture<?> returnFuture() {
        return NativeControl.returnFuture();
    }
    public static Object before(Object host,byte[] bytes,int offset,int length) {
        Class<?> caller=directCaller();
        if(caller!=MethodHandles.Lookup.class&&caller!=ClassLoader.class)throw new SecurityException("DEFINITION_PLATFORM_REQUIRED");
        Objects.checkFromIndexSize(offset,length,bytes.length);
        // A Lookup defines for its real lookup class, including JDK-generated helpers. A shared
        // platform lookup first reached by a Mod does not turn a platform helper into that Mod.
        Class<?> lookupClass=host instanceof MethodHandles.Lookup lookup?lookup.lookupClass():null;
        Module owner=ControlBridge.creationModule(host);
        // A borrowed platform Lookup does not turn a Mod's explicit definition
        // into trusted platform code. Use this actual API consumer, not an older
        // Mod frame underneath unrelated library execution.
        if((lookupClass!=null||host instanceof ClassLoader)&&(owner==null||!ControlBridge.producer(owner))) {
            Class<?> consumer=WALKER.walk(LOOKUP_CONSUMER);
            if(consumer!=null&&ControlBridge.producer(module(consumer)))owner=module(consumer);
        }
        if(owner==null&&lookupClass!=null)owner=module(lookupClass);
        if(owner==null&&host instanceof ClassLoader)owner=module(host.getClass());
        if(owner!=null&&!ControlBridge.producer(owner))owner=null;
        ClassLoader loader=lookupClass!=null?lookupClass.getClassLoader():host instanceof ClassLoader actual?actual:null;
        byte[] image=Arrays.copyOfRange(bytes,offset,offset+length);Scope parent;
        synchronized(CURRENT){parent=CURRENT.get(Thread.currentThread());}
        boolean delegated=parent!=null&&bytes==parent.bytes&&offset==0&&length==bytes.length;
        if(delegated){owner=parent.module;image=bytes;}
        String name=className(image);
        var invocation=WALKER.walk(FRAME);boolean hidden=caller==MethodHandles.Lookup.class&&invocation!=null
                &&(invocation.getMethodName().equals("defineHiddenClass")||invocation.getMethodName().equals("defineHiddenClassWithClassData"));
        if(!delegated&&owner!=null&&transformer!=null)try {image=(byte[])transformer.invoke(null,owner,loader,image,hidden);}
        catch(ReflectiveOperationException failure){throw new IllegalStateException("ACTUAL_DEFINITION_CONTROL_FAILED",failure);}
        Scope scope=new Scope(parent,owner,loader,name,image);
        synchronized(CURRENT){NativeControl.heapPolicyChanged(scope.bytes);CURRENT.put(scope.thread,scope);liveScopes=CURRENT.values().toArray(new Scope[0]);}return scope;
    }
    public static MethodHandles.Lookup lookupDerived(MethodHandles.Lookup result,MethodHandles.Lookup source) {
        var frame=WALKER.walk(FRAME);
        if(frame==null||!(frame.getDeclaringClass()==MethodHandles.Lookup.class
                &&(frame.getMethodName().equals("in")||frame.getMethodName().equals("dropLookupMode"))
                ||frame.getDeclaringClass()==MethodHandles.class&&frame.getMethodName().equals("privateLookupIn")))
            throw new SecurityException("ACTUAL_LOOKUP_DERIVATION_REQUIRED");
        ControlBridge.lookupDerived(source,result,WALKER.walk(LOOKUP_CONSUMER));return result;
    }
    private static String className(byte[] bytes) {
        try(var input=new java.io.DataInputStream(new java.io.ByteArrayInputStream(bytes))) {
            if(input.readInt()!=0xcafebabe)throw new IllegalArgumentException("INVALID_DEFINITION_CLASS");
            input.readInt();int count=input.readUnsignedShort();Object[] pool=new Object[count];
            for(int i=1;i<count;i++)switch(input.readUnsignedByte()) {
                case 1->pool[i]=input.readUTF();case 7->pool[i]=input.readUnsignedShort();
                case 3,4,9,10,11,12,17,18->input.skipNBytes(4);case 5,6->{input.skipNBytes(8);i++;}
                case 8,16,19,20->input.skipNBytes(2);case 15->input.skipNBytes(3);
                default->throw new IllegalArgumentException("INVALID_DEFINITION_POOL");
            }
            input.readUnsignedShort();int actual=input.readUnsignedShort();return (String)pool[(Integer)pool[actual]];
        }catch(java.io.IOException|ClassCastException|IndexOutOfBoundsException malformed){throw new IllegalArgumentException("INVALID_DEFINITION_CLASS",malformed);}
    }
    public static byte[] image(Object token) {return scope(token).bytes;}
    public static byte[] bufferImage(java.nio.ByteBuffer buffer) {java.nio.ByteBuffer copy=buffer.duplicate();byte[] bytes=new byte[copy.remaining()];copy.get(bytes);return bytes;}
    public static Object defined(Object token,Object result) {
        Scope scope=scope(token);Class<?> actual=result instanceof Class<?> type?type:result instanceof MethodHandles.Lookup lookup?lookup.lookupClass():null;
        if(actual!=null&&scope.module!=null)accepted(actual,scope);return result;
    }
    public static void initializing(Class<?> actual) {
        var caller=WALKER.walk(FRAME);
        if(caller==null||caller.getDeclaringClass()!=actual||!caller.getMethodName().equals("<clinit>"))throw new SecurityException("ACTUAL_DEFINED_INITIALIZER_REQUIRED");
        if(ORIGINS.get(actual).origin!=null)return;
        synchronized(CURRENT){Scope scope=CURRENT.get(Thread.currentThread());if(scope==null||scope.module==null)return;
            String name=actual.getName().replace('.','/');
            boolean matches=scope.loader==actual.getClassLoader()&&(name.equals(scope.name)||actual.isHidden()&&name.startsWith(scope.name+"/0x"));
            if(matches)accepted(actual,scope);else throw new SecurityException("ACTUAL_DEFINITION_SCOPE_REQUIRED");
        }
    }
    /** ClassPrepare runs before a hidden initializer. Only the already verified
     * VM callback may join this real current definition scope to its Class. */
    static void prepared(Class<?> actual){
        if(WALKER.getCallerClass()!=CodeSourceBridge.class)throw new SecurityException("ACTUAL_VM_CLASS_PREPARATION_REQUIRED");
        Scope scope;synchronized(CURRENT){scope=CURRENT.get(Thread.currentThread());}
        if(scope==null||scope.module==null||scope.loader!=actual.getClassLoader())return;
        String name=actual.getName().replace('.','/');
        if(name.equals(scope.name)||actual.isHidden()&&name.startsWith(scope.name+"/0x"))accepted(actual,scope);
    }
    public static void after(Object token) {
        Scope scope=scope(token);leave(scope);
    }
    private static void leave(Scope scope){synchronized(CURRENT){if(scope.previous==null)CURRENT.remove(scope.thread);else CURRENT.put(scope.thread,scope.previous);liveScopes=CURRENT.values().toArray(new Scope[0]);}}
    static boolean controlled(Object value){
        Scope[] active=liveScopes;if(value==active)return true;
        if(!(value instanceof Scope)&&!(value instanceof byte[]))return false;
        for(Scope head:active)for(Scope scope=head;scope!=null;scope=scope.previous)if(value==scope||value==scope.bytes)return true;
        return false;
    }
    private static Class<?> directCaller() {return WALKER.walk(CALLER);}
    private static Scope scope(Object token) {
        if(!(token instanceof Scope scope)||scope.thread!=Thread.currentThread())throw new IllegalStateException("DEFINITION_SCOPE_CHANGED");
        synchronized(CURRENT){if(CURRENT.get(scope.thread)!=scope)throw new IllegalStateException("DEFINITION_SCOPE_CHANGED");}
        Class<?> caller=directCaller();if(caller!=MethodHandles.Lookup.class&&caller!=ClassLoader.class)throw new SecurityException("DEFINITION_PLATFORM_REQUIRED");return scope;
    }
    public static boolean allowed(Class<?> type) {return !ControlBridge.modStopped(module(type));}
    public static Object nativeReturn(Module owner,Class<?> type,String descriptor) {
        return stoppedReturn(owner==null?module(type):owner,java.lang.invoke.MethodType.fromMethodDescriptorString(descriptor,type.getClassLoader()).returnType());
    }
    public static byte[] nativeImage(Class<?> declaring,ClassLoader loader,byte[] bytes) {
        if(!NativeControl.beginDefinition(declaring,loader,bytes))throw new SecurityException("ACTUAL_NATIVE_DEFINITION_REQUIRED");
        try{
            Module owner=module(declaring);String name=className(bytes);
            byte[] image=transformer==null?bytes:(byte[])transformer.invoke(null,owner,loader,bytes,false);
            synchronized(CURRENT){Scope scope=new Scope(CURRENT.get(Thread.currentThread()),owner,loader,name,image);NativeControl.heapPolicyChanged(scope.bytes);CURRENT.put(scope.thread,scope);liveScopes=CURRENT.values().toArray(new Scope[0]);}
            return image;
        }
        catch(ReflectiveOperationException failure){throw new IllegalStateException("NATIVE_DEFINITION_CONTROL_FAILED",failure);}
    }
    public static void nativeAfter(Class<?> declaring){
        if(!NativeControl.endDefinition(declaring))throw new SecurityException("ACTUAL_NATIVE_DEFINITION_FINISH_REQUIRED");
        synchronized(CURRENT){Scope scope=CURRENT.get(Thread.currentThread());if(scope==null||scope.module!=module(declaring))throw new IllegalStateException("NATIVE_DEFINITION_SCOPE_CHANGED");leave(scope);}
    }
    public static void nativeDefined(Class<?> declaring,Class<?> actual) {
        if(!NativeControl.definedClass(declaring,actual))throw new SecurityException("ACTUAL_NATIVE_DEFINITION_RESULT_REQUIRED");bind(actual,module(declaring));
    }
    public static Object stoppedReturn(Class<?> type,Class<?> result) {
        return stoppedReturn(module(type),result);
    }
    private static Object stoppedReturn(Module owner,Class<?> result) {
        String mode;UUID fixed;synchronized(RETURNS){synchronized(FIXED){
            mode=RETURNS.getOrDefault(owner,"default");fixed=FIXED.get(owner);
        }}
        if(result==void.class)return null;
        if(result==boolean.class)return false;if(result==byte.class)return (byte)0;if(result==char.class)return (char)0;
        if(result==short.class)return (short)0;if(result==int.class)return mode.equals("invalid-id")?-1:0;
        if(result==long.class)return 0L;if(result==float.class)return 0F;if(result==double.class)return 0D;
        if(mode.equals("null"))return null;
        if((mode.equals("uuid-fixed")||mode.equals("uuid-each"))&&(result==UUID.class||result==String.class))
            return returnIdentifier(mode,fixed==null?null:fixed.toString(),result==String.class);
        if(mode.equals("empty")) {
            if(result.isArray())return Array.newInstance(result.componentType(),0);
            if(result==String.class||result==CharSequence.class)return "";
        }
        if(result==Optional.class)return Optional.empty();if(result==OptionalInt.class)return OptionalInt.empty();
        if(result==OptionalLong.class)return OptionalLong.empty();if(result==OptionalDouble.class)return OptionalDouble.empty();
        if(result==List.class||result==Collection.class||result==Iterable.class)return List.of();
        if(result==Set.class)return Set.of();if(result==Map.class)return Map.of();
        if(result==Iterator.class)return Collections.emptyIterator();if(result==Enumeration.class)return Collections.emptyEnumeration();
        if(result==Stream.class)return Stream.empty();if(result==IntStream.class)return IntStream.empty();
        if(result==LongStream.class)return LongStream.empty();if(result==DoubleStream.class)return DoubleStream.empty();
        if(result==Future.class||result==CompletionStage.class||result==CompletableFuture.class)return returnFuture();
        return null;
    }
}
