package dev.ronova.pro.agent;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

/** Resolve VM declarations through the bootstrap reader, including before Core loads. */
final class AgentMetadata {
    private static final MethodHandle FIELDS, FIELD, METHOD;
    static {
        try {
            Class<?> bridge=Class.forName("dev.ronova.pro.bootstrap.PlatformMetadata",true,null);
            var lookup=MethodHandles.publicLookup();
            FIELDS=lookup.findStatic(bridge,"declaredFields",MethodType.methodType(Field[].class,Class.class));
            FIELD=lookup.findStatic(bridge,"declaredField",MethodType.methodType(Field.class,Class.class,String.class));
            METHOD=lookup.findStatic(bridge,"declaredMethod",MethodType.methodType(Method.class,Class.class,String.class,Class[].class));
        }catch(ReflectiveOperationException failure){throw new ExceptionInInitializerError(failure);}
    }
    private AgentMetadata(){}
    static Field[] declaredFields(Class<?> type){
        try{return (Field[])FIELDS.invokeExact(type);}catch(Throwable failure){throw failed(failure);}
    }
    static Field declaredField(Class<?> type,String name)throws NoSuchFieldException{
        try{return (Field)FIELD.invokeExact(type,name);}
        catch(NoSuchFieldException absent){throw absent;}
        catch(Throwable failure){throw failed(failure);}
    }
    static Method declaredMethod(Class<?> type,String name,Class<?>... parameters)throws NoSuchMethodException{
        try{return (Method)METHOD.invokeExact(type,name,parameters);}
        catch(NoSuchMethodException absent){throw absent;}
        catch(Throwable failure){throw failed(failure);}
    }
    private static RuntimeException failed(Throwable failure){
        if(failure instanceof Error error)throw error;
        return new IllegalStateException("AGENT_VM_DECLARATIONS_UNAVAILABLE",failure);
    }
}
