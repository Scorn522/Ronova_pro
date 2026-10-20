package dev.ronova.pro.bootstrap;

import java.lang.invoke.*;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.lang.ref.WeakReference;
import java.util.Objects;

/** The one Forge world bus, kept separate from private per-Mod buses. */
public final class EventBusBridge {
    private static final String FORGE="net.minecraftforge.common.MinecraftForge";
    private static final StackWalker CALLER=StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE);
    private static volatile Class<?> core;
    private record Root(Class<?> holder,Object bus,long offset,MethodHandle read,MethodHandle cas){}
    private record Range(long offset,long length){}
    private static final class Displaced extends WeakReference<Object>{
        Displaced next;
        Displaced(Object value,Displaced next){super(value);this.next=next;}
    }
    private static volatile Root root;
    private static volatile Displaced displaced;
    private static final ClassValue<Range> ROOT_RANGE=new ClassValue<>(){
        @Override protected Range computeValue(Class<?> type){
            if(!holder(type))throw new IllegalArgumentException("FORGE_BUS_CLASS_REQUIRED");
            try{
                long[] span=ResourceBridge.fieldSpan(PlatformMetadata.declaredField(type,"EVENT_BUS"),type);
                if(span==null)throw new IllegalStateException("FORGE_BUS_LAYOUT_UNAVAILABLE");
                return new Range(span[0],span[1]);
            }
            catch(ReflectiveOperationException failure){throw new IllegalStateException("FORGE_BUS_LAYOUT_UNAVAILABLE",failure);}
        }
    };
    private EventBusBridge(){}
    private static boolean holder(Class<?> type){return type!=null&&type.getName().equals(FORGE);}
    public static synchronized void bindCore(Class<?> runtime){
        if(!ControlBridge.owns(CALLER.getCallerClass())||runtime==null
                ||!runtime.getName().equals("dev.ronova.pro.ProRuntime")||core!=null&&core!=runtime)
            throw new SecurityException("FORGE_BUS_CORE_OWNER");
        core=runtime;
    }
    public static synchronized void install(Class<?> owner,Class<?> forge)throws ReflectiveOperationException{
        Class<?> caller=CALLER.getCallerClass(),runtime=core;
        if(caller!=owner||runtime==null||!owner.getName().equals("dev.ronova.pro.ProMod")
                ||owner.getClassLoader()!=runtime.getClassLoader()||owner.getModule()!=runtime.getModule()
                ||!Objects.equals(owner.getProtectionDomain().getCodeSource(),runtime.getProtectionDomain().getCodeSource())
                ||forge!=Class.forName(FORGE,true,owner.getClassLoader()))
            throw new SecurityException("FORGE_BUS_INSTALLER");
        if(root!=null){if(root.holder()!=forge)throw new SecurityException("FORGE_BUS_IDENTITY");return;}
        Field field=PlatformMetadata.declaredField(forge,"EVENT_BUS");
        if(!Modifier.isStatic(field.getModifiers())||!field.getType().getName().equals("net.minecraftforge.eventbus.api.IEventBus"))
            throw new IllegalStateException("FORGE_BUS_FIELD");
        Class<?> unsafeType=Class.forName("sun.misc.Unsafe",true,ClassLoader.getPlatformClassLoader());
        Field singleton=PlatformMetadata.declaredField(unsafeType,"theUnsafe");singleton.setAccessible(true);
        Object unsafe=singleton.get(null);var lookup=MethodHandles.publicLookup();
        MethodHandle read=lookup.findVirtual(unsafeType,"getObjectVolatile",MethodType.methodType(Object.class,Object.class,long.class)).bindTo(unsafe);
        MethodHandle cas=lookup.findVirtual(unsafeType,"compareAndSwapObject",MethodType.methodType(boolean.class,Object.class,long.class,Object.class,Object.class)).bindTo(unsafe);
        Range range=ROOT_RANGE.get(forge);
        Object bus;
        try{bus=(Object)read.invokeExact((Object)forge,range.offset());}
        catch(Throwable failure){throw new IllegalStateException("FORGE_BUS_READ",failure);}
        Class<?> actual=Class.forName("net.minecraftforge.eventbus.EventBus",false,owner.getClassLoader());
        if(bus==null||bus.getClass()!=actual)throw new IllegalStateException("FORGE_BUS_IMPLEMENTATION_UNAVAILABLE");
        ControlBridge.controlPublication(ROOT_RANGE,new Object[]{read,cas});
        root=new Root(forge,bus,range.offset(),read,cas);
    }
    static boolean structure(Object receiver,Class<?> declaring){
        return holder(declaring)||receiver instanceof Class<?> type&&holder(type);
    }
    static boolean fieldAllowed(Class<?> declaring,String name){
        return !holder(declaring)||!name.equals("EVENT_BUS")||writer(declaring,false);
    }
    static boolean memoryAllowed(Object receiver,long offset,long length){
        if(!(receiver instanceof Class<?> type)||!holder(type))return true;
        Range range=ROOT_RANGE.get(type);
        return !ControlBridge.rangesOverlap(offset,length,range.offset(),range.length())||writer(type,false);
    }
    static boolean restoreWrite(Object receiver,long offset,Object value,int width){
        Root current=root;
        return current!=null&&receiver==current.holder()&&offset==current.offset()&&value==current.bus()
                &&width==ResourceBridge.unsafeWidth("Reference")&&writer(current.holder(),true);
    }
    private static boolean writer(Class<?> forge,boolean restoring){
        return CALLER.walk(frames->{
            var cursor=frames.iterator();
            while(cursor.hasNext()){
                var frame=cursor.next();Class<?> type=frame.getDeclaringClass();
                if(type==EventBusBridge.class){
                    if(frame.getMethodName().equals("restore"))return true;
                    continue;
                }
                if(type==ControlBridge.class||type==TaskBridge.class||type==BackingBridge.class||type==NativeControl.class
                        ||type==ResourceBridge.class||type==CodeSourceBridge.class)continue;
                String name=type.getName();
                if(type.getClassLoader()==null&&(name.startsWith("java.lang.invoke.")||name.startsWith("java.lang.reflect.")
                        ||name.startsWith("jdk.internal.reflect.")||name.equals("jdk.internal.misc.Unsafe"))
                        ||name.equals("sun.misc.Unsafe")&&type.getClassLoader()==ClassLoader.getPlatformClassLoader())continue;
                return !restoring&&type==forge&&frame.getMethodName().equals("<clinit>")&&root==null;
            }
            return false;
        });
    }
    /** Executed at the real EventBus.post entry, before reading busID/listeners. */
    public static Object dispatchBus(Object actual){
        Root current=root;if(current==null)return actual;
        if(CALLER.getCallerClass()!=ControlBridge.eventBoundary)throw new SecurityException("ACTUAL_EVENT_POST_REQUIRED");
        try{
            Object live=(Object)current.read().invokeExact((Object)current.holder(),current.offset());
            if(live==current.bus()&&displaced==null)return actual;
            boolean redirect=false;
            synchronized(EventBusBridge.class){
                Displaced previous=null,entry=displaced;boolean known=false;
                while(entry!=null){
                    Object old=entry.get();Displaced next=entry.next;
                    if(old==null){if(previous==null)displaced=next;else previous.next=next;}
                    else{if(old==actual)redirect=true;if(old==live)known=true;previous=entry;}
                    entry=next;
                }
                if(live!=current.bus()){
                    if(live!=null&&!known)displaced=new Displaced(live,displaced);
                    if(actual==live)redirect=true;
                }
            }
            if(live!=current.bus())restore(current,live);
            // Changing the receiver before the original post body preserves its
            // cancellation/dispatcher contract and delivers the event only once.
            return redirect?current.bus():actual;
        }catch(Throwable failure){throw new IllegalStateException("FORGE_BUS_CONTINUITY_FAILED",failure);}
    }
    private static void restore(Root current,Object observed)throws Throwable{
        boolean restored=(boolean)current.cas().invokeExact((Object)current.holder(),current.offset(),observed,current.bus());
        // A concurrent change is retried by the next actual dispatch.
    }
}
