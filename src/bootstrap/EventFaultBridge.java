package dev.ronova.pro.bootstrap;

import java.lang.invoke.*;
import java.util.Objects;

/** Actual listener dispatch, with locally selected whole-Mod fault isolation. */
public final class EventFaultBridge {
    private static final StackWalker CALLER=StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE);
    private static final MethodType CALLBACK=MethodType.methodType(boolean.class,Object.class,Object.class,Object.class);
    private static final String BUS="net.minecraftforge.eventbus.EventBus";
    private static final String POST="(Lnet/minecraftforge/eventbus/api/Event;Lnet/minecraftforge/eventbus/api/IEventBusInvokeDispatcher;)Z";
    private record Handler(Class<?> owner,Class<?> bus,MethodHandle callback) { }
    private static volatile Class<?> core;
    private static volatile MethodHandle boundaryReady;
    private static volatile MethodHandle runtimeFault;
    private record RuntimeDispatch(Class<?> bus,MethodHandle invoke) { }
    private static volatile RuntimeDispatch runtimeDispatch;
    private static volatile Handler handler;
    private EventFaultBridge() { }

    /** The agent has already bound this exact Core instance through ProMod. */
    public static synchronized void bindCore(Class<?> runtime)throws ReflectiveOperationException {
        Class<?> caller=CALLER.getCallerClass();
        if(!ControlBridge.owns(caller)||runtime==null||!runtime.getName().equals("dev.ronova.pro.ProRuntime")
                ||!"ronova_pro".equals(runtime.getModule().getName())||core!=null&&core!=runtime)
            throw new SecurityException("CLIENT_FAULT_CORE_OWNER");
        MethodHandle ready=MethodHandles.publicLookup().findStatic(caller,"eventFaultGuarded",
                MethodType.methodType(boolean.class,Class.class));
        MethodHandle fault=MethodHandles.publicLookup().findStatic(runtime,"handleEventFault",MethodType.methodType(boolean.class,Module.class,Throwable.class));
        core=runtime;boundaryReady=ready;runtimeFault=fault;
    }

    /** The owner's lookup creates its one real callback; no supplied MethodHandle can replace it. */
    public static synchronized void install(Class<?> owner,MethodHandles.Lookup lookup,Class<?> bus)throws ReflectiveOperationException {
        Class<?> caller=CALLER.getCallerClass(),runtime=core;
        if(caller!=owner||runtime==null||!owner.getName().equals("dev.ronova.pro.ClientFaults")
                ||owner.getClassLoader()!=runtime.getClassLoader()||owner.getModule()!=runtime.getModule()
                ||!Objects.equals(owner.getProtectionDomain().getCodeSource(),runtime.getProtectionDomain().getCodeSource())
                ||lookup==null||lookup.lookupClass()!=owner||!lookup.hasFullPrivilegeAccess())
            throw new SecurityException("CLIENT_FAULT_HANDLER_OWNER");
        if(bus==null||bus!=Class.forName(BUS,false,owner.getClassLoader()))
            throw new SecurityException("CLIENT_FAULT_EVENT_BUS_IDENTITY");
        // Shared API names do not grant a different eventbus definition this callback.
        for(String api:new String[]{"Event","IEventListener","IEventBusInvokeDispatcher"}) {
            String name="net.minecraftforge.eventbus.api."+api;
            if(Class.forName(name,false,bus.getClassLoader())!=Class.forName(name,false,owner.getClassLoader()))
                throw new SecurityException("CLIENT_FAULT_EVENT_API_IDENTITY");
        }
        Handler current=handler;
        if(current!=null) {
            if(current.owner()!=owner||current.bus()!=bus)throw new SecurityException("CLIENT_FAULT_HANDLER_REPLACEMENT");
            return;
        }
        handler=new Handler(owner,bus,lookup.findStatic(owner,"dispatch",CALLBACK));
    }

    public static boolean ready(Class<?> owner) {
        Handler current=handler;
        return current!=null&&current.owner()==owner&&boundaryReady(current.bus());
    }
    private static boolean boundaryReady(Class<?> bus) {
        MethodHandle ready=boundaryReady;if(ready==null)return false;
        try {return (boolean)ready.invokeExact(bus);}
        catch(Throwable failure){return EventFaultBridge.<RuntimeException,Boolean>raise(failure);}
    }

    public static boolean dispatch(Class<?> bus,Object dispatcher,Object listener,Object event) {
        Handler current=handler;MethodHandle fault=runtimeFault;
        boolean isolate=fault!=null&&!System.getProperty("ronova.pro.isolateMods",System.getProperty("ronova.pro.clientIsolateMods","")).isBlank();
        if((current==null||current.bus()!=bus)&&!isolate)return false;
        StackWalker.StackFrame frame=CALLER.walk(frames->frames.skip(1).findFirst().orElse(null));
        if(frame==null||frame.getDeclaringClass()!=bus||!frame.getMethodName().equals("post")
                ||!frame.getDescriptor().equals(POST)||!boundaryReady(bus))return false;
        if(current!=null&&current.bus()==bus)try{if((boolean)current.callback().invokeExact(dispatcher,listener,event))return true;}
        catch(Throwable failure){return EventFaultBridge.<RuntimeException,Boolean>raise(failure);}
        if(!isolate)return false;
        RuntimeDispatch actual;
        try{actual=runtimeDispatch(bus);}
        catch(ReflectiveOperationException unavailable){return false;}
        if(actual==null)return false;
        try{actual.invoke().invokeExact(dispatcher,listener,event);return true;}
        catch(Throwable failure){
            Module owner=ControlBridge.eventListenerModule(listener);
            try{if(owner!=null&&(boolean)fault.invokeExact(owner,failure))return true;}
            catch(Throwable isolationFailure){if(isolationFailure!=failure)failure.addSuppressed(isolationFailure);}
            return EventFaultBridge.<RuntimeException,Boolean>raise(failure);
        }
    }
    private static synchronized RuntimeDispatch runtimeDispatch(Class<?> bus)throws ReflectiveOperationException{
        RuntimeDispatch current=runtimeDispatch;if(current!=null)return current.bus()==bus?current:null;
        Class<?> runtime=core;if(runtime==null||bus!=Class.forName(BUS,false,runtime.getClassLoader()))return null;
        ClassLoader loader=bus.getClassLoader();
        Class<?> dispatcher=Class.forName("net.minecraftforge.eventbus.api.IEventBusInvokeDispatcher",false,loader);
        Class<?> listener=Class.forName("net.minecraftforge.eventbus.api.IEventListener",false,loader);
        Class<?> event=Class.forName("net.minecraftforge.eventbus.api.Event",false,loader);
        MethodHandle invoke=MethodHandles.publicLookup().findVirtual(dispatcher,"invoke",MethodType.methodType(void.class,listener,event))
                .asType(MethodType.methodType(void.class,Object.class,Object.class,Object.class));
        return runtimeDispatch=new RuntimeDispatch(bus,invoke);
    }
    @SuppressWarnings("unchecked")
    private static <E extends Throwable,T> T raise(Throwable failure)throws E { throw (E)failure; }
}
