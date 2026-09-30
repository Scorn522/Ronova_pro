package dev.ronova.pro.validation;

import dev.ronova.pro.ProRuntime;
import java.io.*;
import java.lang.ref.WeakReference;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import net.minecraft.nbt.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.EventPriority;

/** Ordinary business persistence/cloning and public operation queries; supplies no execution evidence. */
public final class ChainFixture {
    public static final ConcurrentHashMap<String,CompoundTag> STATIC_RECORDS=new ConcurrentHashMap<>();
    private static final Path FILE=Path.of("chain-business","static.dat"),OPERATION=Path.of("chain-operation.txt");
    public static final class Data extends SavedData {
        public final ConcurrentHashMap<String,CompoundTag> records=new ConcurrentHashMap<>();
        public final AtomicReference<CompoundTag> retained=new AtomicReference<>();
        public final CopyOnWriteArrayList<CompoundTag> copies=new CopyOnWriteArrayList<>();
        @Override public CompoundTag save(CompoundTag root) {
            CompoundTag entries=new CompoundTag();records.forEach((key,value)->entries.put(key,value.copy()));root.put("records",entries);
            if(retained.get()!=null)root.put("retained",retained.get().copy());
            ListTag list=new ListTag();copies.forEach(value->list.add(value.copy()));root.put("copies",list);
            root.putString("unknown-key","preserve-me");return root;
        }
        static Data read(CompoundTag root) {
            Data data=new Data();CompoundTag entries=root.getCompound("records");for(String key:entries.getAllKeys())data.records.put(key,entries.getCompound(key));
            if(root.contains("retained"))data.retained.set(root.getCompound("retained"));
            for(Tag tag:root.getList("copies",10))data.copies.add((CompoundTag)tag);
            return data;
        }
    }
    public static final class Target extends Cow implements Cloneable {
        public static final AtomicReference<Target> PROTOTYPE=new AtomicReference<>();
        Target(ServerLevel level) { super(EntityType.COW,level);setNoAi(true); }
        Target copy()throws CloneNotSupportedException { return (Target)super.clone(); }
        @Override public void remove(RemovalReason reason) { /* Hostile virtual remove, no test evidence. */ }
    }
    private UUID operation;
    private Data data;
    private WeakReference<Target> original;
    private int tick,phase,stableAt=-1;
    private boolean finished;
    private final boolean restart="chain-restart".equals(System.getProperty("ronova.pro.fixture"));
    ChainFixture() { MinecraftForge.EVENT_BUS.addListener(EventPriority.LOWEST,this::tick); }
    public static void saveStatic()throws IOException {
        Files.createDirectories(FILE.getParent());CompoundTag root=new CompoundTag();
        STATIC_RECORDS.forEach((key,value)->root.put(key,value.copy()));root.putString("unknown-key","static-preserved");NbtIo.writeCompressed(root,FILE.toFile());
    }
    public static void readStatic()throws IOException {
        CompoundTag root=NbtIo.readCompressed(FILE.toFile());
        for(String key:root.getAllKeys())if(root.get(key) instanceof CompoundTag value)STATIC_RECORDS.put(key,value);
    }
    private void tick(TickEvent.ServerTickEvent event) {
        if(finished||event.phase!=TickEvent.Phase.END)return;
        ProRuntime runtime=ProRuntime.get(event.getServer());if(runtime==null)return;
        tick++;
        try {
            if(tick>1600)throw new AssertionError("CHAIN_TIMEOUT phase="+phase);
            var level=event.getServer().overworld();
            if(phase==0) {
                level.setChunkForced(0,0,true);
                if(restart) {
                    operation=UUID.fromString(Files.readString(OPERATION).strip());
                    data=level.getDataStorage().get(Data::read,"pro_chain_fixture");if(data==null)throw new AssertionError("SAVED_DATA_NOT_RELOADED");
                    readStatic();Class.forName(Target.class.getName(),true,Target.class.getClassLoader());phase=5;
                } else {
                    Target target=new Target(level);target.setPos(2,80,2);require(level.addFreshEntity(target),"SPAWN");
                    original=new WeakReference<>(target);Target.PROTOTYPE.set(target);
                    CompoundTag record=target.saveWithoutId(new CompoundTag());CompoundTag unrelated=new CompoundTag();unrelated.putString("foreign","untouched");
                    data=new Data();data.records.put("target",record);data.records.put("unrelated",unrelated);
                    data.retained.set(record);data.copies.add(record);data.copies.add(unrelated);
                    STATIC_RECORDS.put("target",record);STATIC_RECORDS.put("unrelated",unrelated);
                    level.getDataStorage().set("pro_chain_fixture",data);data.setDirty();level.getDataStorage().save();saveStatic();
                    operation=FixtureCommands.clear(runtime,target);Files.writeString(OPERATION,operation.toString());phase=1;
                }
            }
            if(tick%20==0&&operation!=null)Files.writeString(Path.of("chain-progress.txt"),"phase="+phase+" tick="+tick+"\n"+String.join("\n",runtime.recoveryStatus(operation)));
            if(!restart&&tick>=100&&Set.of("ACK_FORCE","NATIVE_COMMIT").contains(System.getProperty("ronova.pro.fault.point",""))
                    &&Files.exists(Path.of("ronova-validation","fault-reached.txt"))) {
                require(!settled(runtime),"FAILED_EFFECT_MUST_STAY_UNRESOLVED");
                CompoundTag staticDisk=NbtIo.readCompressed(FILE.toFile());
                CompoundTag savedDisk=NbtIo.readCompressed(Path.of("Pro-Stage-B/data/pro_chain_fixture.dat").toFile()).getCompound("data");
                require(staticDisk.contains("target")||savedDisk.getCompound("records").contains("target"),"FAILED_COMMIT_BLINDLY_REPLAYED");
                finish(event,"EXPECTED_FAULT_UNRESOLVED_NO_REPLAY_PASS\n",runtime);return;
            }
            if(phase==1) {
                var body=runtime.query(operation);
                if(body!=null&&body.complete()&&body.receiptDurable()) {
                    // A real no-NBT clone, with a different UUID, after the first body's clear.
                    Target clone=Target.PROTOTYPE.get().copy();clone.revive();clone.setUUID(UUID.randomUUID());clone.setId(clone.getId()+100000);
                    clone.setPos(5,80,5);require(level.addFreshEntity(clone),"CLONE_SPAWN");phase=2;
                }
            } else if(phase==2&&Target.PROTOTYPE.get()==null&&!data.records.containsKey("target")
                    &&data.retained.get()==null&&data.copies.size()==1&&!STATIC_RECORDS.containsKey("target")) {
                require(data.records.get("unrelated").getString("foreign").equals("untouched"),"UNRELATED_MEMORY");phase=3;
            } else if(phase==3&&settled(runtime)) {
                checkDisk(level);
                // Normal GC request tests continued metadata settlement; it supplies no release fact.
                System.gc();phase=4;stableAt=tick;
            } else if(phase==4&&tick-stableAt>=40&&original.get()==null&&settled(runtime)) {
                checkDisk(level);finish(event,"CHAIN_PRODUCTION_PASS\nSTATIC_SAVED_DATA_ATOMIC_LIST_NATIVE_CLONE_UUID_CHANGE_GC_STABLE\n",runtime);
            } else if(phase==5&&settled(runtime)) {
                checkDisk(level);finish(event,"CHAIN_ORIGINAL_INTENT_RESTART_PASS\n",runtime);
            }
        } catch(Throwable failure) {
            try { Files.writeString(Path.of(restart?"chain-restart-result.txt":"chain-result.txt"),"FAILED\n"+failure+"\n"+
                    String.join("\n",operation==null?List.of():runtime.recoveryStatus(operation))); }
            catch(IOException writeFailure) { failure.addSuppressed(writeFailure); }
            finished=true;failure.printStackTrace();event.getServer().halt(false);
        }
    }
    private boolean settled(ProRuntime runtime) { return runtime.recoveryStatus(operation).stream().anyMatch(row->row.equals("已知恢复链已处置")); }
    private void checkDisk(ServerLevel level)throws IOException {
        CompoundTag root=NbtIo.readCompressed(FILE.toFile());
        require(!root.contains("target")&&root.getCompound("unrelated").getString("foreign").equals("untouched")
                &&root.getString("unknown-key").equals("static-preserved"),"STATIC_PRESERVATION");
        Path saved=level.getServer().getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT).resolve("data/pro_chain_fixture.dat");
        CompoundTag encoded=NbtIo.readCompressed(saved.toFile()).getCompound("data");
        require(!encoded.getCompound("records").contains("target")&&!encoded.contains("retained")&&encoded.getList("copies",10).size()==1
                &&encoded.getString("unknown-key").equals("preserve-me"),"SAVED_DATA_PRESERVATION");
    }
    private void finish(TickEvent.ServerTickEvent event,String title,ProRuntime runtime)throws IOException {
        Files.writeString(Path.of(restart?"chain-restart-result.txt":"chain-result.txt"),title+String.join("\n",runtime.recoveryStatus(operation)));
        finished=true;event.getServer().halt(false);
    }
    private static void require(boolean condition,String reason) { if(!condition)throw new AssertionError(reason); }
}
