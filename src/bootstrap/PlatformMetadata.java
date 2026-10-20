package dev.ronova.pro.bootstrap;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Set;

/** VM declarations, independent of Class.ReflectionData and its mutable arrays. */
public final class PlatformMetadata {
    private static final MethodHandle FIELDS, METHODS, CONSTRUCTORS;
    private static final Class<?> REFLECTION_DATA, INSTRUMENTATION;
    private static final StackWalker WALKER=StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE);
    private static final Set<String> CLASS_WRITERS=Set.of("privateGetDeclaredFields","privateGetPublicFields",
            "privateGetDeclaredMethods","privateGetPublicMethods","privateGetDeclaredConstructors",
            "privateGetPublicConstructors","reflectionData","newReflectionData");
    static {
        try {
            MethodHandles.Lookup access=MethodHandles.privateLookupIn(Class.class,MethodHandles.lookup());
            FIELDS=access.findVirtual(Class.class,"getDeclaredFields0",MethodType.methodType(Field[].class,boolean.class));
            METHODS=access.findVirtual(Class.class,"getDeclaredMethods0",MethodType.methodType(Method[].class,boolean.class));
            CONSTRUCTORS=access.findVirtual(Class.class,"getDeclaredConstructors0",MethodType.methodType(Constructor[].class,boolean.class));
            REFLECTION_DATA=Class.forName("java.lang.Class$ReflectionData",false,null);
            INSTRUMENTATION=Class.forName("sun.instrument.InstrumentationImpl",false,null);
        } catch(ReflectiveOperationException failure) {
            throw new ExceptionInInitializerError(failure);
        }
    }
    private PlatformMetadata(){}
    static Object[] controls(){return new Object[]{FIELDS,METHODS,CONSTRUCTORS,CLASS_WRITERS};}
    public static Field[] declaredFields(Class<?> type) {
        try {return (Field[])FIELDS.invokeExact(type,false);}
        catch(Throwable failure){throw unavailable(type,failure);}
    }
    public static Field declaredField(Class<?> type,String name)throws NoSuchFieldException {
        for(Field field:declaredFields(type))if(field.getName().equals(name))return field;
        throw new NoSuchFieldException(type.getName()+"."+name);
    }
    public static Method[] declaredMethods(Class<?> type) {
        try {return (Method[])METHODS.invokeExact(type,false);}
        catch(Throwable failure){throw unavailable(type,failure);}
    }
    public static Method declaredMethod(Class<?> type,String name,Class<?>... parameters)throws NoSuchMethodException {
        Method selected=null;
        for(Method method:declaredMethods(type))if(method.getName().equals(name)&&Arrays.equals(method.getParameterTypes(),parameters)) {
            if(selected==null||selected.getReturnType().isAssignableFrom(method.getReturnType()))selected=method;
        }
        if(selected==null)throw new NoSuchMethodException(type.getName()+"."+name);
        return selected;
    }
    public static Constructor<?> declaredConstructor(Class<?> type,Class<?>... parameters)throws NoSuchMethodException {
        try {
            for(Constructor<?> constructor:(Constructor<?>[])CONSTRUCTORS.invokeExact(type,false))
                if(Arrays.equals(constructor.getParameterTypes(),parameters))return constructor;
        } catch(Throwable failure){throw unavailable(type,failure);}
        throw new NoSuchMethodException(type.getName()+".<init>");
    }
    private static RuntimeException unavailable(Class<?> type,Throwable failure) {
        if(failure instanceof Error error)throw error;
        return new IllegalStateException("VM_DECLARATIONS_UNAVAILABLE:"+type.getName(),failure);
    }
    static boolean structure(Object receiver,Class<?> declaring) {
        Class<?> type=receiver==null?declaring:receiver.getClass();
        return type==REFLECTION_DATA||type==INSTRUMENTATION||declaring==REFLECTION_DATA||declaring==INSTRUMENTATION;
    }
    static boolean fieldMutationAllowed(Object receiver,Class<?> declaring,String name) {
        boolean cache=declaring==Class.class&&name.equals("reflectionData");
        return !cache&&!structure(receiver,declaring)||platformWriter(cache||declaring==REFLECTION_DATA
                ||receiver!=null&&receiver.getClass()==REFLECTION_DATA);
    }
    static boolean memoryMutationAllowed(Object receiver) {
        return !structure(receiver,null)||platformWriter(receiver.getClass()==REFLECTION_DATA);
    }
    private static boolean platformWriter(boolean reflection) {
        // Admit the actual JDK cache publication, not an arbitrary reflective
        // invocation whose outer stack happens to contain a platform frame.
        return WALKER.walk(frames->{
            var cursor=frames.iterator();
            while(cursor.hasNext()) {
                var frame=cursor.next();Class<?> type=frame.getDeclaringClass();String name=type.getName();
                if(type==PlatformMetadata.class||type.getClassLoader()==null&&type.getNestHost()==PlatformMetadata.class
                        ||type==ControlBridge.class||type==BackingBridge.class||type==NativeControl.class
                        ||type==ResourceBridge.class||type==CodeSourceBridge.class)continue;
                if(type.getClassLoader()==null&&(name.startsWith("java.lang.invoke.")||name.startsWith("java.lang.reflect.")
                        ||name.startsWith("jdk.internal.reflect.")||name.equals("jdk.internal.misc.Unsafe"))
                        ||name.equals("sun.misc.Unsafe")&&type.getClassLoader()==ClassLoader.getPlatformClassLoader())continue;
                if(reflection)return type==Class.class&&CLASS_WRITERS.contains(frame.getMethodName())
                        ||type==REFLECTION_DATA&&frame.getMethodName().equals("<init>");
                return type==INSTRUMENTATION;
            }
            return false;
        });
    }
}
