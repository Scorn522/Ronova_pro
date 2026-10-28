package dev.ronova.pro.bootstrap;

import java.lang.invoke.*;
import java.lang.reflect.*;
import java.nio.Buffer;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/** Performance profile: observed Mod calls retain real destination checks.
 * Unwoven library bridges and pre-selection object history are explicit gaps. */
public final class GuardedCalls {
    public static final String COVERAGE="PERFORMANCE_CALL_SITES:UNWOVEN_BRIDGES_AND_PRE_SELECTION_HISTORY_NOT_COVERED";
    private GuardedCalls(){}
    private record MethodKey(Class<?> owner,String name,String descriptor,int modes,Class<?> previous){}
    private static final ThreadLocal<Boolean> RESOLVING=new ThreadLocal<>();
    private static final StackWalker FACTORIES=StackWalker.getInstance(Set.of(StackWalker.Option.RETAIN_CLASS_REFERENCE,StackWalker.Option.SHOW_HIDDEN_FRAMES,StackWalker.Option.SHOW_REFLECT_FRAMES));
    private static boolean lookupFactory(){return FACTORIES.walk(frames->frames.map(StackWalker.StackFrame::getDeclaringClass).dropWhile(type->type==GuardedCalls.class).findFirst().orElse(null))==MethodHandles.Lookup.class;}
    private static final ClassValue<ConcurrentHashMap<MethodKey,MethodHandle>> METHODS=new ClassValue<>(){
        protected ConcurrentHashMap<MethodKey,MethodHandle> computeValue(Class<?> type){return new ConcurrentHashMap<>();}
    };
    private static MethodHandle target(Class<?> owner,String name,String descriptor,MethodHandles.Lookup caller){
        return METHODS.get(caller.lookupClass()).computeIfAbsent(new MethodKey(owner,name,descriptor,caller.lookupModes(),caller.previousLookupClass()),key->{
            Boolean prior=RESOLVING.get();RESOLVING.set(Boolean.TRUE);
            try{return caller.findVirtual(owner,key.name(),MethodType.fromMethodDescriptorString(key.descriptor(),owner.getClassLoader()));}
            catch(ReflectiveOperationException failure){throw new IllegalStateException("MOD_CALL_TARGET_UNAVAILABLE",failure);}
            finally{if(prior==null)RESOLVING.remove();else RESOLVING.set(prior);}
        });
    }
    private static Object invoke(MethodHandle method,Object receiver,Object[] arguments)throws Throwable{
        Object[] actual=new Object[arguments.length+1];actual[0]=receiver;System.arraycopy(arguments,0,actual,1,arguments.length);return method.invokeWithArguments(actual);
    }
    private static boolean compare(String name){return name.startsWith("compareAndSet")||name.startsWith("compareAndSwap")||name.startsWith("weakCompareAndSet");}
    private static String kind(Class<?> type){
        if(!type.isPrimitive())return "Object";
        return type==boolean.class?"Boolean":type==byte.class?"Byte":type==char.class?"Char":type==short.class?"Short":type==int.class?"Int":type==long.class?"Long":type==float.class?"Float":"Double";
    }
    private record Write(Object receiver,long offset,long length,Object proposed,Object source,long sourceOffset,String kind,boolean bulk){}
    private static Write unsafeWrite(String name,MethodType type,Object[] args){
        boolean objects=type.parameterCount()!=0&&type.parameterType(0)==Object.class;
        if(name.equals("copyMemory")||name.equals("copySwapMemory"))return objects?
                new Write(args[2],(long)args[3],(long)args[4],null,args[0],(long)args[1],"Byte",true):
                new Write(null,(long)args[1],(long)args[2],null,null,(long)args[0],"Byte",true);
        if(name.equals("setMemory"))return objects?
                new Write(args[0],(long)args[1],(long)args[2],args[3],null,0,"Byte",true):
                new Write(null,(long)args[0],(long)args[1],args[2],null,0,"Byte",true);
        int at=objects?2:1;boolean cas=compare(name)||name.startsWith("compareAndExchange");
        int value=at+(cas?1:0);if(type.parameterCount()<=value)throw new IllegalArgumentException("UNSUPPORTED_MOD_UNSAFE_WRITER:"+name);
        return new Write(objects?args[0]:null,(long)args[objects?1:0],0,args[value],null,0,kind(type.parameterType(value)),false);
    }
    public static Object unsafe(Object accessor,Class<?> owner,String name,String descriptor,Object[] arguments,MethodHandles.Lookup caller)throws Throwable{
        return unsafe(accessor,owner,name,descriptor,arguments,target(owner,name,descriptor,caller));
    }
    private static Object unsafe(Object accessor,Class<?> owner,String name,String descriptor,Object[] arguments,MethodHandle method)throws Throwable{
        Objects.requireNonNull(accessor);
        if(!ControlBridge.UnsafeWriters.contains(owner)||accessor.getClass()!=owner)throw new SecurityException("ACTUAL_MOD_UNSAFE_RECEIVER_REQUIRED");
        MethodType type=MethodType.fromMethodDescriptorString(descriptor,owner.getClassLoader());
        Write write=unsafeWrite(name,type,arguments);
        Object receipt=ControlBridge.beginCallSiteMutation(accessor,write.receiver(),write.offset(),write.length(),write.proposed(),write.source(),write.sourceOffset(),owner.getName().replace('.','/'),name,write.kind(),write.bulk());
        if(receipt==Boolean.FALSE){if(compare(name))return false;if(type.returnType()!=void.class)return BackingBridge.retained(write.receiver(),write.offset(),write.kind());return null;}
        try{
            Object result=invoke(method,accessor,arguments);
            boolean written=!compare(name)||Boolean.TRUE.equals(result);
            if(name.startsWith("compareAndExchange"))written=sameValue(type.returnType(),result,arguments[type.parameterType(0)==Object.class?2:1]);
            ControlBridge.finishFieldMutation(receipt,written);return result;
        }catch(Throwable failure){ControlBridge.fieldMutationFailed(receipt,failure);throw failure;}
    }
    private record HandleLayout(Field offset,Field base,boolean array,boolean bytes,boolean buffer){}
    private static boolean sameValue(Class<?> type,Object actual,Object expected){
        if(type==float.class)return Float.floatToRawIntBits((Float)actual)==Float.floatToRawIntBits((Float)expected);
        if(type==double.class)return Double.doubleToRawLongBits((Double)actual)==Double.doubleToRawLongBits((Double)expected);
        return type.isPrimitive()?Objects.equals(actual,expected):actual==expected;
    }
    private static Field member(Class<?> type,String name){
        for(Class<?> at=type;at!=null;at=at.getSuperclass())try{return PlatformMetadata.declaredField(at,name);}catch(NoSuchFieldException ignored){}
        throw new IllegalStateException("VARHANDLE_LAYOUT_UNAVAILABLE:"+type.getName()+"#"+name);
    }
    private static final ClassValue<HandleLayout> HANDLES=new ClassValue<>(){
        protected HandleLayout computeValue(Class<?> type){
            if(type.getClassLoader()!=null||type.getModule()!=Object.class.getModule())throw new SecurityException("ACTUAL_JDK_VARHANDLE_REQUIRED");
            String name=type.getName();
            if(name.startsWith("java.lang.invoke.VarHandleByteArrayAs")&&name.endsWith("$ByteBufferHandle"))return new HandleLayout(null,null,false,true,true);
            if(name.startsWith("java.lang.invoke.VarHandleByteArrayAs")&&name.endsWith("$ArrayHandle"))return new HandleLayout(null,null,true,true,false);
            if(!name.matches("java\\.lang\\.invoke\\.VarHandle(?:Booleans|Bytes|Shorts|Chars|Ints|Longs|Floats|Doubles|References)\\$(?:Array|FieldInstanceReadWrite|FieldStaticReadWrite)"))throw new IllegalStateException("UNOBSERVED_VARHANDLE_LAYOUT:"+name);
            if(name.endsWith("$Array"))return new HandleLayout(null,null,true,false,false);
            return new HandleLayout(member(type,"fieldOffset"),name.endsWith("$FieldStaticReadWrite")?member(type,"base"):null,false,false,false);
        }
    };
    private static Object read(Field field,Object holder){return CodeSourceBridge.controlField(field,holder);}
    private static final class UnsafeAccess {
        static final Object INSTANCE=singleton();
        private static Object singleton(){try{Field field=PlatformMetadata.declaredField(ControlBridge.UnsafeWriters.INTERNAL,"theUnsafe");return read(field,null);}catch(NoSuchFieldException failure){throw new ExceptionInInitializerError(failure);}}
    }
    public static Object handle(Object receiver,Class<?> owner,String name,String descriptor,Object[] arguments,MethodHandles.Lookup caller)throws Throwable{
        if(owner!=VarHandle.class||!(receiver instanceof VarHandle handle))throw new SecurityException("ACTUAL_VARHANDLE_REQUIRED");
            MethodType requested=MethodType.fromMethodDescriptorString(descriptor,caller.lookupClass().getClassLoader());
            MethodHandle guarded=handleMethod(handle,VarHandle.AccessMode.valueFromMethodName(name));
            if(handle.hasInvokeExactBehavior()&&!guarded.type().equals(requested))throw new WrongMethodTypeException("expected "+guarded.type()+" but found "+requested);
            return guarded.asType(requested).invokeWithArguments(arguments);
    }
    private static Object handle(Object receiver,Class<?> owner,String name,String descriptor,Object[] arguments)throws Throwable{
        if(owner!=VarHandle.class||!(receiver instanceof VarHandle handle))throw new SecurityException("ACTUAL_VARHANDLE_REQUIRED");
        VarHandle.AccessMode mode=VarHandle.AccessMode.valueFromMethodName(name);MethodHandle target=handle.toMethodHandle(mode);
        HandleLayout layout=HANDLES.get(handle.getClass());int coordinates=handle.coordinateTypes().size();
        Object destination;long offset;String valueKind=kind(handle.varType());
        if(layout.array()){
            destination=arguments[0];if(!handle.coordinateTypes().get(0).isInstance(destination))throw new ClassCastException("VARHANDLE_ARRAY_COORDINATE");
            int index=(int)arguments[1];long[] array=ResourceBridge.arrayLayout(destination.getClass());
            if(array==null)throw new IllegalStateException("VARHANDLE_ARRAY_LAYOUT_UNAVAILABLE");
            int width=layout.bytes()?ResourceBridge.unsafeWidth(valueKind):1;
            if(index<0||index>Array.getLength(destination)-width)throw new IndexOutOfBoundsException(index);
            offset=array[0]+(long)index*(layout.bytes()?1:array[1]);
        }else if(layout.buffer()){
            Buffer buffer=(Buffer)arguments[0];int index=(int)arguments[1];
            if(buffer.isReadOnly())throw new java.nio.ReadOnlyBufferException();
            if(index<0||index>buffer.limit()-ResourceBridge.unsafeWidth(valueKind))throw new IndexOutOfBoundsException(index);
            if(!ControlBridge.bufferCallSiteAllowed(buffer))throw new SecurityException("CONTROLLED_BUFFER_VIEW_REFUSED");
            if(buffer.isDirect()){destination=null;offset=(long)read(member(Buffer.class,"address"),buffer)+index;}
            else{destination=read(member(java.nio.ByteBuffer.class,"hb"),buffer);long[] array=ResourceBridge.arrayLayout(destination.getClass());
                offset=array[0]+((Number)read(member(java.nio.ByteBuffer.class,"offset"),buffer)).intValue()+index;}
        }else{destination=layout.base()==null?arguments[0]:read(layout.base(),handle);offset=((Number)read(layout.offset(),handle)).longValue();}
        Object proposed=arguments[arguments.length-1];String operation=name;
        Object receipt=ControlBridge.beginCallSiteMutation(UnsafeAccess.INSTANCE,destination,offset,0,proposed,null,0,"jdk/internal/misc/Unsafe",operation,valueKind,false);
        if(receipt==Boolean.FALSE){if(compare(name))return false;if(target.type().returnType()==void.class)return null;return handle.toMethodHandle(VarHandle.AccessMode.GET_VOLATILE).invokeWithArguments(Arrays.copyOf(arguments,coordinates));}
        try{
            Object result=target.invokeWithArguments(arguments);boolean written=!compare(name)||Boolean.TRUE.equals(result);
            if(name.startsWith("compareAndExchange"))written=sameValue(handle.varType(),result,arguments[coordinates]);
            ControlBridge.finishFieldMutation(receipt,written);return result;
        }catch(Throwable failure){ControlBridge.fieldMutationFailed(receipt,failure);throw failure;}
    }
    public static Object buffer(Object receiver,Class<?> owner,String name,String descriptor,Object[] arguments,MethodHandles.Lookup caller)throws Throwable{
        return buffer(receiver,owner,name,arguments,target(owner,name,descriptor,caller));
    }
    private static Object buffer(Object receiver,Class<?> owner,String name,Object[] arguments,MethodHandle target)throws Throwable{
        if(!(receiver instanceof Buffer)||owner.getClassLoader()!=null||!Buffer.class.isAssignableFrom(owner))throw new SecurityException("ACTUAL_BUFFER_CALL_REQUIRED");
        if(!ControlBridge.bufferCallSiteAllowed(receiver))throw new SecurityException("CONTROLLED_BUFFER_CALL_REFUSED");
        for(Object argument:arguments)if(argument instanceof Buffer?!ControlBridge.bufferCallSiteAllowed(argument):argument!=null&&argument.getClass().isArray()&&!ControlBridge.controlMutationAllowed(argument))
            throw new SecurityException("CONTROLLED_BUFFER_ARGUMENT_REFUSED");
        Object receipt=ResourceBridge.bufferCallSite(receiver,arguments,owner,name);
        try{Object result=invoke(target,receiver,arguments);ResourceBridge.bufferCallSiteFinished(receipt,true);return result;}
        catch(Throwable failure){try{ResourceBridge.bufferCallSiteFinished(receipt,false);}catch(Throwable cleanup){if(cleanup!=failure)failure.addSuppressed(cleanup);}throw failure;}
    }
    public static Object reflect(Method method,Object receiver,Object[] arguments,MethodHandles.Lookup caller)throws IllegalAccessException,InvocationTargetException{
        Class<?> owner=method.getDeclaringClass();String name=method.getName();
        // Use the real woven caller's lookup, never this bootstrap class's
        // private access or caller-sensitive identity.
        MethodHandle delegate=caller.unreflect(method).asFixedArity();Object[] args=arguments==null?new Object[0]:arguments;
        if(args.length!=method.getParameterCount())throw new IllegalArgumentException("wrong number of arguments");
        if(!Modifier.isStatic(method.getModifiers())){Objects.requireNonNull(receiver);if(!owner.isInstance(receiver))throw new IllegalArgumentException("object is not an instance of declaring class");}
        Class<?>[] types=method.getParameterTypes();for(int i=0;i<types.length;i++)if(!reflectArgument(types[i],args[i]))throw new IllegalArgumentException("argument type mismatch");
        try{
            if(receiver instanceof Buffer&&Buffer.class.isAssignableFrom(owner))return buffer(receiver,owner,name,args,delegate);
            return Modifier.isStatic(method.getModifiers())?delegate.invokeWithArguments(args):invoke(delegate,receiver,args);
        }catch(Throwable failure){throw new InvocationTargetException(failure);}
    }
    private static boolean reflectArgument(Class<?> type,Object value){
        if(!type.isPrimitive())return value==null||type.isInstance(value);
        if(type==boolean.class)return value instanceof Boolean;
        if(type==char.class)return value instanceof Character;
        if(type==byte.class)return value instanceof Byte;
        if(type==short.class)return value instanceof Byte||value instanceof Short;
        if(type==int.class)return value instanceof Byte||value instanceof Short||value instanceof Character||value instanceof Integer;
        if(type==long.class)return reflectArgument(int.class,value)||value instanceof Long;
        if(type==float.class)return reflectArgument(long.class,value)||value instanceof Float;
        return reflectArgument(float.class,value)||value instanceof Double;
    }
    private record UnsafeHandle(MethodHandle target,Class<?> owner,String name,String descriptor){}
    private static boolean unsafeWriter(Class<?> owner,String name){return ControlBridge.UnsafeWriters.contains(owner)
            &&(name.startsWith("put")||name.startsWith("compareAnd")||name.startsWith("weakCompareAnd")||name.startsWith("getAnd")||name.equals("setMemory")||name.equals("copyMemory")||name.equals("copySwapMemory"));}
    public static MethodHandle wrapUnsafeHandle(MethodHandle original,Class<?> owner,String name,MethodType type){
        if(!owner.getName().equals("sun.misc.Unsafe")&&!owner.getName().equals("jdk.internal.misc.Unsafe"))return original;
        if(!lookupFactory())throw new SecurityException("ACTUAL_UNSAFE_HANDLE_FACTORY_REQUIRED");
        if(RESOLVING!=null&&RESOLVING.get()!=null)return original;
        return unsafeWriter(owner,name)?unsafeHandle(original,new UnsafeHandle(original,owner,name,type.toMethodDescriptorString())):original;
    }
    public static MethodHandle wrapUnsafeHandle(MethodHandle original,Method method){
        Class<?> owner=method.getDeclaringClass();String name=method.getName();
        if(!owner.getName().equals("sun.misc.Unsafe")&&!owner.getName().equals("jdk.internal.misc.Unsafe"))return original;
        if(!lookupFactory())throw new SecurityException("ACTUAL_UNSAFE_HANDLE_FACTORY_REQUIRED");
        if(RESOLVING!=null&&RESOLVING.get()!=null)return original;
        return unsafeWriter(owner,name)?unsafeHandle(original,new UnsafeHandle(original,owner,name,MethodType.methodType(method.getReturnType(),method.getParameterTypes()).toMethodDescriptorString())):original;
    }
    private static MethodHandle unsafeHandle(MethodHandle original,UnsafeHandle call){
        try{return MethodHandles.lookup().findStatic(GuardedCalls.class,"boundUnsafe",MethodType.methodType(Object.class,UnsafeHandle.class,Object[].class))
                .bindTo(call).asCollector(Object[].class,original.type().parameterCount()).asType(original.type());}
        catch(ReflectiveOperationException failure){throw new IllegalStateException("UNSAFE_HANDLE_ADAPTER_UNAVAILABLE",failure);}
    }
    private static Object boundUnsafe(UnsafeHandle call,Object[] arguments)throws Throwable{
        return unsafe(arguments[0],call.owner(),call.name(),call.descriptor(),Arrays.copyOfRange(arguments,1,arguments.length),call.target());
    }
    public static MethodHandle handleMethod(VarHandle handle,VarHandle.AccessMode mode){
        MethodHandle original=handle.toMethodHandle(mode);String name=mode.methodName();
        if(!(name.startsWith("set")||name.startsWith("compareAnd")||name.startsWith("weakCompareAnd")||name.startsWith("getAnd")))return original;
        try{return MethodHandles.lookup().findStatic(GuardedCalls.class,"boundHandle",MethodType.methodType(Object.class,VarHandle.class,String.class,Object[].class))
                .bindTo(handle).bindTo(name).asCollector(Object[].class,original.type().parameterCount()).asType(original.type());}
        catch(ReflectiveOperationException failure){throw new IllegalStateException("VARHANDLE_ADAPTER_UNAVAILABLE",failure);}
    }
    private static Object boundHandle(VarHandle handle,String name,Object[] arguments)throws Throwable{return handle(handle,VarHandle.class,name,"",arguments);}
    public static MethodHandle handleInvoker(VarHandle.AccessMode mode,MethodType type,boolean exact){
        MethodHandle original=exact?MethodHandles.varHandleExactInvoker(mode,type):MethodHandles.varHandleInvoker(mode,type);
        String name=mode.methodName();if(!(name.startsWith("set")||name.startsWith("compareAnd")||name.startsWith("weakCompareAnd")||name.startsWith("getAnd")))return original;
        try{return MethodHandles.insertArguments(MethodHandles.lookup().findStatic(GuardedCalls.class,"invokeHandle",MethodType.methodType(Object.class,VarHandle.AccessMode.class,MethodType.class,boolean.class,Object[].class)),0,mode,type,exact)
                .asCollector(Object[].class,original.type().parameterCount()).asType(original.type());}
        catch(ReflectiveOperationException failure){throw new IllegalStateException("VARHANDLE_INVOKER_UNAVAILABLE",failure);}
    }
    private static Object invokeHandle(VarHandle.AccessMode mode,MethodType requested,boolean exact,Object[] arguments)throws Throwable{
        VarHandle handle=(VarHandle)arguments[0];MethodHandle guarded=handleMethod(handle,mode);
        if(exact&&!guarded.type().equals(requested))throw new WrongMethodTypeException("expected "+requested+" but found "+guarded.type());
        return guarded.asType(requested).invokeWithArguments(Arrays.copyOfRange(arguments,1,arguments.length));
    }
}
