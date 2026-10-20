package dev.ronova.pro;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

/** Core access to raw VM declarations; never consumes a target's ReflectionData. */
final class PlatformAccess {
    private static final MethodHandle FIELDS, FIELD, METHODS, METHOD, CONSTRUCTOR;
    static {
        try {
            Class<?> bridge=Class.forName("dev.ronova.pro.bootstrap.PlatformMetadata",true,null);
            var lookup=MethodHandles.publicLookup();
            FIELDS=lookup.findStatic(bridge,"declaredFields",MethodType.methodType(Field[].class,Class.class));
            FIELD=lookup.findStatic(bridge,"declaredField",MethodType.methodType(Field.class,Class.class,String.class));
            METHODS=lookup.findStatic(bridge,"declaredMethods",MethodType.methodType(Method[].class,Class.class));
            METHOD=lookup.findStatic(bridge,"declaredMethod",MethodType.methodType(Method.class,Class.class,String.class,Class[].class));
            CONSTRUCTOR=lookup.findStatic(bridge,"declaredConstructor",MethodType.methodType(Constructor.class,Class.class,Class[].class));
        } catch(ReflectiveOperationException failure){throw new ExceptionInInitializerError(failure);}
    }
    private PlatformAccess(){}
    static Field[] declaredFields(Class<?> type){
        try{return (Field[])FIELDS.invokeExact(type);}catch(Throwable failure){throw failed(failure);}
    }
    static Method[] declaredMethods(Class<?> type){
        try{return (Method[])METHODS.invokeExact(type);}catch(Throwable failure){throw failed(failure);}
    }
    static Field declaredField(Class<?> type,String name)throws NoSuchFieldException {
        try{return (Field)FIELD.invokeExact(type,name);}
        catch(NoSuchFieldException absent){throw absent;}
        catch(Throwable failure){throw failed(failure);}
    }
    static Method declaredMethod(Class<?> type,String name,Class<?>... parameters)throws NoSuchMethodException {
        try{return (Method)METHOD.invokeExact(type,name,parameters);}
        catch(NoSuchMethodException absent){throw absent;}
        catch(Throwable failure){throw failed(failure);}
    }
    static Constructor<?> declaredConstructor(Class<?> type,Class<?>... parameters)throws NoSuchMethodException {
        try{return (Constructor<?>)CONSTRUCTOR.invokeExact(type,parameters);}
        catch(NoSuchMethodException absent){throw absent;}
        catch(Throwable failure){throw failed(failure);}
    }
    private static RuntimeException failed(Throwable failure) {
        if(failure instanceof Error error)throw error;
        return new IllegalStateException("VM_DECLARATIONS_UNAVAILABLE",failure);
    }
}
