package dev.ronova.pro;

import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.*;

/** Focused source-list regression and historical metadata checks; neither certifies gameplay. */
public final class AuditFixCheck {
    private static final sun.misc.Unsafe U;
    static {try {var field=sun.misc.Unsafe.class.getDeclaredField("theUnsafe");field.setAccessible(true);U=(sun.misc.Unsafe)field.get(null);}catch(Exception e){throw new ExceptionInInitializerError(e);}}
    private static void put(Object target,String name,Object value)throws Exception {var f=target.getClass().getDeclaredField(name);f.setAccessible(true);f.set(target,value);}
    private static void require(boolean yes,String message) {if(!yes)throw new AssertionError(message);}
    private static Object invoke(Object receiver,String name)throws Exception {var m=receiver.getClass().getDeclaredMethod(name);m.setAccessible(true);try{return m.invoke(receiver);}catch(InvocationTargetException e){if(e.getCause() instanceof Exception x)throw x;throw e;}}
    public static void main(String[] args)throws Exception {
        if(args.length==1&&args[0].equals("source-list-fixes")){sourceListFixes();return;}
        ProRuntime runtime=(ProRuntime)U.allocateInstance(ProRuntime.class);
        Map<UUID,Object> work=new LinkedHashMap<>();put(runtime,"work",work);
        var subject=new ProRuntime.Subject(UUID.randomUUID());var records=(EntityRecords)U.allocateInstance(EntityRecords.class);put(records,"originalPassengers",List.of());
        var binding=(ProRuntime.Binding)U.allocateInstance(ProRuntime.Binding.class);put(binding,"subject",subject);put(binding,"records",records);
        Class<?> rootWork=Class.forName("dev.ronova.pro.ProRuntime$Work");var rootConstructor=rootWork.getDeclaredConstructor(ProRuntime.Binding.class,BlockRecords.class,long.class,String.class);rootConstructor.setAccessible(true);Object retired=rootConstructor.newInstance(binding,null,0L,"PROTECT");put(retired,"referencesReleased",true);put(retired,"cancelled",true);put(retired,"state","FENCED");put(retired,"ack",CompletableFuture.completedFuture(null));
        for(int i=0;i<65536;i++)work.put(new UUID(1,i),retired);
        invoke(runtime,"ensureWorkCapacity");require(work.size()==65535,"retired history did not free an admission slot");
        Object pending=rootConstructor.newInstance(binding,null,0L,"CLEAR");put(pending,"ack",new CompletableFuture<>());work.clear();
        for(int i=0;i<65536;i++)work.put(new UUID(2,i),pending);
        try {invoke(runtime,"ensureWorkCapacity");throw new AssertionError("unsettled budget accepted");}
        catch(IllegalStateException expected){require(work.size()==65536&&!subject.terminal&&subject.generation==0,"budget rejection changed duties or authority");}
        System.out.println("CAPACITY_METADATA_PASS retired_slot_reclaimed pending_duties_retained authority_unchanged");

        Class<?> descriptor=Class.forName("dev.ronova.pro.RecoveryStorage$Descriptor");var constructor=descriptor.getDeclaredConstructors()[0];constructor.setAccessible(true);
        Object d=constructor.newInstance("STORAGE/2",UUID.randomUUID(),UUID.randomUUID(),UUID.randomUUID(),1L,"source","code",1L,"identity","0".repeat(64),"1".repeat(64),"2".repeat(64),UUID.randomUUID(),"win32-ntfs-txf/2",null);
        Class<?> storageWork=Class.forName("dev.ronova.pro.RecoveryStorage$Work");var restoredConstructor=storageWork.getDeclaredConstructor(descriptor);restoredConstructor.setAccessible(true);Object restored=restoredConstructor.newInstance(d);
        var storage=(RecoveryStorage)U.allocateInstance(RecoveryStorage.class);
        put(storage,"work",new java.util.concurrent.ConcurrentSkipListMap<>(Map.of(UUID.randomUUID(),restored)));
        var worker=new ThreadPoolExecutor(1,1,0,TimeUnit.MILLISECONDS,new ArrayBlockingQueue<>(1));put(storage,"worker",worker);
        storage.close();var nativeReleased=storageWork.getDeclaredField("nativeReleased");nativeReleased.setAccessible(true);var resourcesConfirmed=storageWork.getDeclaredField("resourcesConfirmed");resourcesConfirmed.setAccessible(true);
        require(worker.isTerminated()&&!nativeReleased.getBoolean(restored)&&!resourcesConfirmed.getBoolean(restored),"shutdown fabricated a historic transaction settlement");
        System.out.println("STORAGE_SHUTDOWN_METADATA_PASS no_current_handle historic_unknown_preserved worker_closed");
    }
    private static void sourceListFixes()throws Exception {
        Class<?> publications=Class.forName("dev.ronova.pro.SourceMaps$Publications");
        Class<?> controls=Class.forName("dev.ronova.pro.bootstrap.ControlBridge",false,null);
        for(String name:List.of("OBSERVE","CARRIER","HOLD","CHANGED")){
            Field field=publications.getDeclaredField(name);field.setAccessible(true);
            require(((Method)field.get(null)).getDeclaringClass()==controls,"source method resolved to wrong bridge: "+name);
        }
        net.minecraft.nbt.CompoundTag target=new net.minecraft.nbt.CompoundTag();target.putString("marker","equal-value");
        net.minecraft.nbt.CompoundTag neighbor=target.copy();
        require(target!=neighbor&&target.equals(neighbor),"fixture must distinguish identity from equal NBT");
        Method image=RecoveryRecords.class.getDeclaredMethod("listImage",Object.class,Object.class);image.setAccessible(true);
        List<List<net.minecraft.nbt.CompoundTag>> carriers=List.of(
                new ArrayList<>(List.of(target,neighbor,target)),
                new CopyOnWriteArrayList<>(List.of(target,neighbor,target)),
                new it.unimi.dsi.fastutil.objects.ObjectArrayList<>(List.of(target,neighbor,target)));
        for(List<net.minecraft.nbt.CompoundTag> carrier:carriers){
            String projected=(String)image.invoke(null,carrier,target);
            require(!projected.isEmpty(),"NBT projection unavailable: "+carrier.getClass().getName());
            require(SourceMaps.removeListIdentity(carrier,target),"duplicate removal not applied: "+carrier.getClass().getName());
            require(carrier.size()==1&&carrier.get(0)==neighbor,"duplicate target retained or equal neighbor damaged: "+carrier.getClass().getName());
            require(projected.equals(image.invoke(null,carrier,null)),"projection does not match all removed aliases: "+carrier.getClass().getName());
            require(!SourceMaps.removeListIdentity(carrier,target)&&carrier.get(0)==neighbor,"absent target changed neighbor: "+carrier.getClass().getName());
        }
        System.out.println("SOURCE_LIST_FIXES_PASS bridge_owner array_list copy_on_write fastutil duplicate_identity preserved_equal_neighbor exact_nbt_projection");
    }
}
