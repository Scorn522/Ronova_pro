package dev.ronova.pro.bootstrap;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.invoke.VarHandle;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Set;

/** VM declarations, independent of Class.ReflectionData and its mutable arrays. */
public final class PlatformMetadata {
    private static final MethodHandle FIELDS, METHODS, CONSTRUCTORS;
    private static final VarHandle CLASS_REVISION;
    private static final Class<?> REFLECTION_DATA, INSTRUMENTATION;
    static final Class<?> CLASS_ATOMIC, ANNOTATION_DATA, ANNOTATION_TYPE, ANNOTATION_ACCESS;
    private static final StackWalker WALKER=StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE);
    private static final Set<String> CLASS_WRITERS=Set.of("privateGetDeclaredFields","privateGetPublicFields",
            "privateGetDeclaredMethods","privateGetPublicMethods","privateGetDeclaredConstructors",
            "privateGetPublicConstructors","reflectionData","newReflectionData");
    static {
        try {
            REFLECTION_DATA=Class.forName("java.lang.Class$ReflectionData",false,null);
            CLASS_ATOMIC=Class.forName("java.lang.Class$Atomic",false,null);
            ANNOTATION_DATA=Class.forName("java.lang.Class$AnnotationData",false,null);
            ANNOTATION_TYPE=Class.forName("sun.reflect.annotation.AnnotationType",false,null);
            ANNOTATION_ACCESS=Class.forName("java.lang.System$2",false,null);
            INSTRUMENTATION=Class.forName("sun.instrument.InstrumentationImpl",false,null);
            MethodHandles.Lookup access=MethodHandles.privateLookupIn(Class.class,MethodHandles.lookup());
            FIELDS=access.findVirtual(Class.class,"getDeclaredFields0",MethodType.methodType(Field[].class,boolean.class));
            METHODS=access.findVirtual(Class.class,"getDeclaredMethods0",MethodType.methodType(Method[].class,boolean.class));
            CONSTRUCTORS=access.findVirtual(Class.class,"getDeclaredConstructors0",MethodType.methodType(Constructor[].class,boolean.class));
            VarHandle revision;
            try{revision=access.findVarHandle(Class.class,"classRedefinedCount",int.class);}
            catch(NoSuchFieldException|IllegalAccessException unavailable){revision=null;}
            CLASS_REVISION=revision;
        } catch(ReflectiveOperationException failure) {
            throw new ExceptionInInitializerError(failure);
        }
    }
    private PlatformMetadata(){}
    static Object[] controls(){return new Object[]{FIELDS,METHODS,CONSTRUCTORS,CLASS_REVISION,CLASS_WRITERS,FIELD_DECLARATIONS};}
    private record FieldDeclarations(int revision,boolean nativeBound,Field[] roots){}
    private static final class FieldSlot {volatile FieldDeclarations value;}
    private static final ClassValue<FieldSlot> FIELD_DECLARATIONS=new ClassValue<>(){
        @Override protected FieldSlot computeValue(Class<?> type){return new FieldSlot();}
    };
    // The VM advances this same counter for Class's own reflection-data
    // invalidation, including superclass redefinition. An unavailable counter
    // disables caching. Never retain a layout without a VM definition counter.
    static int classRevision(Class<?> type){
        if(CLASS_REVISION==null)return -1;
        // Direct volatile field access does not customize a protected
        // MethodHandle's mutable form on this rendering hot path.
        try{return (int)CLASS_REVISION.getVolatile(type);}
        catch(Throwable failure){throw unavailable(type,failure);}
    }
    public static Field[] declaredFields(Class<?> type) {
        try {
            return readFields(type,NativeControl.declarationsReady());
        }
        catch(Throwable failure){throw unavailable(type,failure);}
    }
    public static Field declaredField(Class<?> type,String name)throws NoSuchFieldException {
        for(Field field:declaredFields(type))if(field.getName().equals(name))return field;
        throw new NoSuchFieldException(type.getName()+"."+name);
    }
    static Field[] controlFields(Class<?> type){
        // Only the control-backing scanner consumes these private mirrors.
        // Public reflection results remain fresh: sharing Field.root would
        // allow an escaped mirror to poison a later metadata read.
        if(WALKER.getCallerClass()!=CodeSourceBridge.class)throw new SecurityException("ACTUAL_CONTROL_LAYOUT_READER_REQUIRED");
        try{return fieldDeclarations(type);}catch(Throwable failure){throw unavailable(type,failure);}
    }
    private static Field[] fieldDeclarations(Class<?> type)throws Throwable {
        int revision=classRevision(type);boolean bound=NativeControl.declarationsReady();
        if(revision<0)return readFields(type,bound);
        FieldSlot slot=FIELD_DECLARATIONS.get(type);FieldDeclarations found=slot.value;
        if(found!=null&&found.revision()==revision&&found.nativeBound()==bound)return found.roots();
        // A miss can race a redefinition or the Native handoff. Read again
        // instead of labelling old declarations with the newer generation.
        // Do not hold a class-local monitor across a JNI metadata callback.
        for(;;){
            Field[] fields=readFields(type,bound);
            if(revision==classRevision(type)&&bound==NativeControl.declarationsReady()){
                slot.value=new FieldDeclarations(revision,bound,fields);return fields;
            }
            revision=classRevision(type);bound=NativeControl.declarationsReady();
            if(revision<0)return readFields(type,bound);
        }
    }
    private static Field[] readFields(Class<?> type,boolean bound)throws Throwable {
        return bound?NativeControl.vmDeclaredFields(type):(Field[])FIELDS.invokeExact(type,false);
    }
    public static Method[] declaredMethods(Class<?> type) {
        try {
            if(NativeControl.declarationsReady())return NativeControl.vmDeclaredMethods(type);
            return (Method[])METHODS.invokeExact(type,false);
        }
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
            Constructor<?>[] constructors=NativeControl.declarationsReady()?NativeControl.vmDeclaredConstructors(type)
                    :(Constructor<?>[])CONSTRUCTORS.invokeExact(type,false);
            for(Constructor<?> constructor:constructors)
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
        return platformStructure(type)||platformStructure(declaring);
    }
    private static boolean platformStructure(Class<?> type){
        return type!=null&&(type==REFLECTION_DATA||type==ANNOTATION_DATA||type==ANNOTATION_TYPE||type==INSTRUMENTATION);
    }
    static boolean fieldMutationAllowed(Object receiver,Class<?> declaring,String name) {
        if(declaring==Class.class){
            try{
                Field field=declaredField(Class.class,name);
                if(!java.lang.reflect.Modifier.isStatic(field.getModifiers()))return ControlBridge.classCacheFieldWrite(field);
            }catch(NoSuchFieldException missing){return false;}
        }
        return !structure(receiver,declaring)||platformWriter(platformStructure(declaring)?declaring:receiver.getClass());
    }
    static boolean memoryMutationAllowed(Object receiver) {
        return !structure(receiver,null)||platformWriter(receiver.getClass());
    }
    private static boolean platformWriter(Class<?> structure) {
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
                if(structure==REFLECTION_DATA)return type==Class.class&&CLASS_WRITERS.contains(frame.getMethodName())
                        ||type==REFLECTION_DATA&&frame.getMethodName().equals("<init>");
                if(structure==ANNOTATION_DATA)return type==ANNOTATION_DATA&&frame.getMethodName().equals("<init>")
                        &&frame.getDescriptor().equals("(Ljava/util/Map;Ljava/util/Map;I)V");
                if(structure==ANNOTATION_TYPE)return type==ANNOTATION_TYPE&&frame.getMethodName().equals("<init>")
                        &&frame.getDescriptor().equals("(Ljava/lang/Class;)V");
                return structure==INSTRUMENTATION&&type==INSTRUMENTATION;
            }
            return false;
        });
    }
}
