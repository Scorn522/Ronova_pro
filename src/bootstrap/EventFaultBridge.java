package dev.ronova.pro.bootstrap;

import java.lang.invoke.*;

/** Optional client callback. Unselected events keep their original dispatcher and exceptions. */
public final class EventFaultBridge {
    private static volatile MethodHandle handler;
    private EventFaultBridge() { }

    public static synchronized void install(Class<?> owner, MethodHandle callback) {
        Class<?> caller=StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE).getCallerClass();
        if(caller!=owner || !owner.getName().equals("dev.ronova.pro.ClientFaults")
                || !"ronova_pro".equals(owner.getModule().getName()))
            throw new SecurityException("CLIENT_FAULT_HANDLER_OWNER");
        if(handler!=null)return;
        handler=callback.asType(MethodType.methodType(boolean.class,Object.class,Object.class,Object.class));
    }

    public static boolean dispatch(Object dispatcher,Object listener,Object event) {
        MethodHandle current=handler;
        if(current==null)return false;
        try { return (boolean)current.invokeExact(dispatcher,listener,event); }
        catch(Throwable failure) { return EventFaultBridge.<RuntimeException,Boolean>raise(failure); }
    }
    @SuppressWarnings("unchecked")
    private static <E extends Throwable,T> T raise(Throwable failure)throws E { throw (E)failure; }
}
