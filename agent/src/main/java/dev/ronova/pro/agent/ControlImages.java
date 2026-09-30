package dev.ronova.pro.agent;

import java.lang.instrument.ClassFileTransformer;
import java.security.ProtectionDomain;
import java.util.*;

/** Keeps the real pre-control image for the small set of control carriers. Our current hooks are reapplied after it. */
final class ControlImages implements ClassFileTransformer {
    private static final class Ref<T> extends java.lang.ref.WeakReference<T> {
        private static final StackWalker CALLER=StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE);
        Ref(T value){super(value);}
        @Override public void clear(){
            Class<?> caller=CALLER.getCallerClass();
            if(allowed(caller))super.clear();
        }
        @Override public boolean enqueue(){return allowed(CALLER.getCallerClass())&&super.enqueue();}
        private static boolean allowed(Class<?> caller) {
            return caller.getClassLoader()==RecoveryAgent.class.getClassLoader()
                    &&caller.getModule()==RecoveryAgent.class.getModule()&&caller.getName().startsWith("dev.ronova.pro.agent.")
                    &&Objects.equals(caller.getProtectionDomain().getCodeSource(),RecoveryAgent.class.getProtectionDomain().getCodeSource());
        }
    }
    private record Image(Ref<ClassLoader> loader,boolean bootstrap,String name,byte[] bytes) { }
    private volatile Image[] images=new Image[0];
    private volatile Ref<?>[] declared=new Ref<?>[0];
    private volatile String failure;
    boolean healthy(){return failure==null;}
    synchronized void declare(Class<?> type){
        for(var entry:declared)if(entry.get()==type)return;
        var next=Arrays.copyOf(declared,declared.length+1);next[next.length-1]=new Ref<>(type);
        protect(next,next[next.length-1]);declared=next;
    }
    private boolean critical(Module module,ClassLoader loader,String name,Class<?> type) {
        if(name==null)return false;
        if(type!=null)for(var entry:declared)if(entry.get()==type)return true;
        if(name.startsWith("dev/ronova/pro/agent/")||name.startsWith("dev/ronova/pro/bootstrap/"))
            return loader==null||loader==RecoveryAgent.class.getClassLoader();
        if(name.startsWith("dev/ronova/pro/")&&!name.startsWith("dev/ronova/pro/validation/")
                &&module!=null&&"ronova_pro".equals(module.getName()))return true;
        if(loader==null)return InstrumentationBoundary.MANAGER.equals(name)||InstrumentationBoundary.API.equals(name)||JdkTaskBoundaryContract.registered(name)
                ||name.equals("java/lang/reflect/Field")||name.startsWith("java/util/HashMap")
                ||name.equals("java/util/Map")||name.startsWith("java/util/ArrayList")
                ||name.startsWith("java/util/IdentityHashMap")||name.equals("java/util/AbstractCollection")
                ||name.equals("java/util/concurrent/ConcurrentHashMap")||HandleWriteBoundary.targets().contains(name);
        return EventFaultBoundary.TARGET.equals(name)||name.equals("sun/misc/Unsafe")||FastutilIndexBoundary.targets().contains(name)||SectionKeyBoundary.targets().contains(name)
                ||name.startsWith("net/minecraft/world/level/entity/")||name.startsWith("net/minecraft/server/level/ChunkMap")
                ||Set.of("net/minecraft/world/entity/Entity","net/minecraft/world/entity/LivingEntity",
                    "net/minecraft/server/level/ServerLevel","net/minecraft/server/level/ServerPlayer",
                    "net/minecraft/world/level/Level","net/minecraft/client/multiplayer/ClientPacketListener",
                    "net/minecraft/client/multiplayer/ClientLevel").contains(name);
    }
    @Override public synchronized byte[] transform(Module module,ClassLoader loader,String name,Class<?> type,
            ProtectionDomain domain,byte[] bytes) {
        try {
        if(!critical(module,loader,name,type))return null;
        var living=new ArrayList<Image>();
        for(Image image:images) {
            ClassLoader defining=image.loader.get();if(!image.bootstrap&&defining==null)continue;
            living.add(image);
            if(defining==loader&&image.bootstrap==(loader==null)&&image.name.equals(name))return image.bytes.clone();
        }
        Image image=new Image(new Ref<>(loader),loader==null,name,bytes.clone());living.add(image);
        Image[] next=living.toArray(Image[]::new);protect(next,image,image.loader,image.bytes);images=next;
        return null;
        }catch(RuntimeException|LinkageError unavailable) {
            failure=name+":"+unavailable.getClass().getSimpleName()+":"+unavailable.getMessage();
            System.err.println("RONOVA_CONTROL_IMAGE_UNAVAILABLE:"+failure);throw unavailable;
        }
    }
    static void protect(Object... objects) {
        try {Class.forName("dev.ronova.pro.bootstrap.TaskBridge",false,null)
                .getMethod("registerAgentControls",Object[].class).invoke(null,(Object)objects);}
        catch(ReflectiveOperationException unavailable){throw new IllegalStateException("CONTROL_IMAGE_BINDING",unavailable);}
    }
}
