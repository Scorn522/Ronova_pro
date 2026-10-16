package dev.ronova.pro.validation;

import dev.ronova.pro.ProRuntime;
import java.io.*;
import java.nio.channels.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import net.minecraft.nbt.*;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.EventPriority;

/** Real files, OS locks and normal revocation. Reflection delays/observes work; it supplies no commit evidence. */
final class StorageRaceFixture {
    private static final class Data extends SavedData {
        final ConcurrentHashMap<String,CompoundTag> records=new ConcurrentHashMap<>();
        public CompoundTag save(CompoundTag root) {
            records.forEach((key,value)->root.put(key,value.copy()));root.putString("unknown","preserve-exactly");return root;
        }
    }
    private static final class SaveBomb extends Cow {
        boolean fail=true;
        SaveBomb(net.minecraft.world.level.Level level) { super(EntityType.COW,level); }
        public void addAdditionalSaveData(CompoundTag tag) {
            if(fail)throw new IllegalStateException("BUSINESS_SAVE_FAILURE");super.addAdditionalSaveData(tag);
        }
    }
    private CountDownLatch release;
    private Cow target;
    private UUID operation;
    private Data data;
    private Path file,backup;
    private byte[] baseline;
    private FileChannel locked;
    private FileLock lock;
    private int tick,phase,scenario,entered;
    private boolean finished;
    private final List<String> results=new ArrayList<>();
    private final boolean focused=Boolean.getBoolean("ronova.pro.storageSchedulingCheck");
    private final List<Path> focusedFiles=new ArrayList<>();
    StorageRaceFixture() { MinecraftForge.EVENT_BUS.addListener(EventPriority.LOWEST,this::tick); }
    private void pauseStorage(ProRuntime runtime)throws Exception {
        var field=ProRuntime.class.getDeclaredField("recoveryStorage");field.setAccessible(true);Object storage=field.get(runtime);
        field=storage.getClass().getDeclaredField("worker");field.setAccessible(true);
        CountDownLatch started=new CountDownLatch(1);release=new CountDownLatch(1);CountDownLatch local=release;
        ((Executor)field.get(storage)).execute(()->{started.countDown();try { local.await(); }catch(InterruptedException e) { Thread.currentThread().interrupt(); }});
        require(started.await(3,TimeUnit.SECONDS),"STORAGE_DELAY_DID_NOT_START");
    }
    private void tick(TickEvent.ServerTickEvent event) {
        if(finished||event.phase!=TickEvent.Phase.END)return;
        var runtime=ProRuntime.get(event.getServer());if(runtime==null)return;
        tick++;
        try {
            if(tick>1600)throw new AssertionError("STORAGE_RACE_TIMEOUT scenario="+scenario+" phase="+phase);
            if(focused) { schedulingTick(event,runtime);return; }
            if(phase==0) {
                var level=event.getServer().overworld();level.setChunkForced(0,0,true);
                target=scenario==3?new SaveBomb(level):new Cow(EntityType.COW,level);target.setNoAi(true);target.setPos(4+scenario,80,4);
                require(level.addFreshEntity(target),"SPAWN");
                if(target instanceof SaveBomb bomb) {
                    try { target.saveWithoutId(new CompoundTag());throw new AssertionError("EXPECTED_BUSINESS_SAVE_FAILURE"); }
                    catch(net.minecraft.ReportedException expected) { }
                    bomb.fail=false;
                }
                data=new Data();data.records.put("target",target.saveWithoutId(new CompoundTag()));
                CompoundTag keep=new CompoundTag();keep.putString("payload","unrelated");data.records.put("keep",keep);
                String name="pro_storage_race_"+scenario;level.getDataStorage().set(name,data);data.setDirty();level.getDataStorage().save();
                file=Path.of("Pro-Stage-B","data",name+".dat").toAbsolutePath();backup=file.resolveSibling(name+".original");
                baseline=Files.readAllBytes(file);pauseStorage(runtime);operation=FixtureCommands.clear(runtime,target);
                if(scenario==0) { Files.move(file,backup);Files.copy(backup,file); }
                if(scenario==1) { locked=FileChannel.open(file,StandardOpenOption.WRITE);lock=locked.lock(); }
                if(scenario==2)FixtureCommands.cancel(runtime,operation);
                if(scenario==4) { phase=6;entered=tick; }
                else { release.countDown();phase=1;entered=tick; }
            } else if(phase==6&&tick-entered>=8) {
                data.setDirty();event.getServer().overworld().getDataStorage().save();
                release.countDown();phase=2;
            } else if(phase==1&&tick-entered>=40) {
                List<String> rows=runtime.recoveryStatus(operation);
                if(scenario==0||scenario==1) {
                    require(rows.stream().noneMatch(row->row.contains("state=READBACK_CONFIRMED")),"CONFLICT_WAS_REPORTED_AS_COMMITTED");
                    if(rows.stream().noneMatch(row->row.contains("NATIVE_PREPARE")))return;
                    if(scenario==0) {
                        require(Arrays.equals(baseline,Files.readAllBytes(file)),"DIFFERENT_FILE_IDENTITY_WAS_WRITTEN");
                        Files.delete(file);Files.move(backup,file);results.add("SAME_BYTES_DIFFERENT_IDENTITY_BLOCKED");
                    } else { lock.release();lock=null;locked.close();locked=null;results.add("OS_FILE_LOCK_PREVENTED_COMMIT"); }
                    phase=2;
                } else if(scenario==2) {
                    require(Arrays.equals(baseline,Files.readAllBytes(file)),"REVOKED_WORK_WROTE_FILE");
                    require(!rows.contains("已知恢复链已处置"),"REVOKED_WORK_CLAIMED_COMPLETION");
                    results.add("REVOKE_BEFORE_COMMIT_NO_WRITE");scenario++;phase=0;
                } else phase=2;
            } else if(phase==2&&runtime.recoveryStatus(operation).contains("已知恢复链已处置")) {
                CompoundTag read=NbtIo.readCompressed(file.toFile()).getCompound("data");
                require(!read.contains("target")&&read.getCompound("keep").getString("payload").equals("unrelated")
                        &&read.getString("unknown").equals("preserve-exactly"),"INDEPENDENT_PRESERVATION_READBACK");
                results.add(scenario==3?"SERIALIZER_EXCEPTION_RESERVATION_RELEASED":scenario==4?"NEW_SAVE_GENERATION_SUPERSEDES_UNEXECUTED_PREPARATION":"REAL_CONFLICT_RECOVERY_SETTLED");
                scenario++;phase=0;
                if(scenario==5)finish(event,runtime,"STORAGE_RACE_PRODUCTION_PASS\n");
            }
        } catch(Throwable failure) { failure.printStackTrace();finish(event,runtime,"FAILED\n"+failure+"\n"); }
    }
    private void schedulingTick(TickEvent.ServerTickEvent event,ProRuntime runtime)throws Exception {
        if(phase==0) {
            var level=event.getServer().overworld();level.setChunkForced(0,0,true);
            target=new Cow(EntityType.COW,level);target.setNoAi(true);target.setPos(4,80,4);
            require(level.addFreshEntity(target),"SPAWN");
            CompoundTag record=target.saveWithoutId(new CompoundTag());
            Path directory=event.getServer().getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT).resolve("data");
            for(int i=0;i<40;i++) {
                Data saved=new Data();saved.records.put("target",record);
                CompoundTag keep=new CompoundTag();keep.putString("payload","unrelated");saved.records.put("keep",keep);
                String name="pro_storage_schedule_"+i;level.getDataStorage().set(name,saved);saved.setDirty();
                focusedFiles.add(directory.resolve(name+".dat"));
            }
            level.getDataStorage().save();pauseStorage(runtime);operation=FixtureCommands.clear(runtime,target);
            phase=10;entered=tick;
        } else if(phase==10&&tick-entered>=8) {
            List<String> rows=runtime.recoveryStatus(operation);
            require(!rows.contains("已知恢复链已处置"),"QUEUED_SAVES_REPORTED_SETTLED");
            if(rows.stream().noneMatch(row->row.contains("STORAGE_WORKER_BUDGET_WAIT"))) {
                require(tick-entered<20,"STORAGE_QUEUE_REJECTION_NOT_EXERCISED");return;
            }
            release.countDown();phase=11;
        } else if(phase==11&&runtime.recoveryStatus(operation).contains("已知恢复链已处置")) {
            for(Path path:focusedFiles) {
                CompoundTag read=NbtIo.readCompressed(path.toFile()).getCompound("data");
                require(!read.contains("target")&&read.getCompound("keep").getString("payload").equals("unrelated")
                        &&read.getString("unknown").equals("preserve-exactly"),"REJECTED_STORAGE_WORK_LOST:"+path.getFileName());
            }
            results.add("REJECTED_QUEUE_WORK_REQUEUED_ALL_40_REAL_SAVES");
            pauseStorage(runtime);phase=12;entered=tick;
        } else if(phase==12) {
            Object storage=observed(runtime,"recoveryStorage"),pending=null;
            for(Object item:((Map<?,?>)observed(storage,"work")).values())
                if(operation.equals(observed(item,"operation"))&&Boolean.TRUE.equals(observed(item,"running"))
                        &&"READBACK_CONFIRMED".equals(observed(item,"state"))) { pending=item;break; }
            if(pending==null) { require(tick-entered<10,"READBACK_RECHECK_NOT_QUEUED_WITHIN_LEASE");return; }
            long last=(Long)observed(pending,"lastQuery"),now=Integer.toUnsignedLong(event.getServer().getTickCount());
            require(last>=0&&now>=last&&now-last<=10,"FRESH_READBACK_WINDOW_NOT_EXERCISED");
            List<String> rows=runtime.recoveryStatus(operation);
            require(rows.contains("STORAGE_RESOURCES_PENDING:"+observed(pending,"id"))&&!rows.contains("已知恢复链已处置"),
                    "RUNNING_READBACK_REUSED_RELEASE_RECEIPT");
            results.add("RUNNING_READBACK_REMAINS_RESOURCE_PENDING");
            failedReadClose(storage,pending,focusedFiles.get(0));
            results.add("FAILED_READ_CLOSE_CANNOT_BECOME_NOOP_RELEASE_RECEIPT");
            release.countDown();phase=13;
        } else if(phase==13&&runtime.recoveryStatus(operation).contains("已知恢复链已处置"))
            finish(event,runtime,"STORAGE_SCHEDULING_PRODUCTION_PASS\n");
    }
    private static Object observed(Object owner,String name)throws ReflectiveOperationException {
        var field=owner.getClass().getDeclaredField(name);field.setAccessible(true);return field.get(owner);
    }
    private static void failedReadClose(Object storage,Object template,Path path)throws Exception {
        // This detached work owns only the real input below. It is never indexed,
        // and no descriptor, ACK, readback or release fact is supplied to live work.
        Object descriptor=observed(template,"descriptor");
        var constructor=template.getClass().getDeclaredConstructor(descriptor.getClass());constructor.setAccessible(true);
        Object detached=constructor.newInstance(descriptor);
        var inputField=detached.getClass().getDeclaredField("queryInput");inputField.setAccessible(true);
        var close=storage.getClass().getDeclaredMethod("closeQueryInput",detached.getClass());close.setAccessible(true);
        class FailedInput extends FileInputStream {
            int calls;
            FailedInput()throws FileNotFoundException { super(path.toFile()); }
            @Override public void close()throws IOException {
                calls++;super.close();if(calls==1)throw new IOException("EXPECTED_READBACK_CLOSE_FAILURE");
            }
        }
        FailedInput input=new FailedInput();inputField.set(detached,input);
        try {
            for(int attempt=0;attempt<2;attempt++)try {
                close.invoke(null,detached);throw new AssertionError("FAILED_READ_CLOSE_BECAME_RELEASE_RECEIPT");
            } catch(java.lang.reflect.InvocationTargetException failure) {
                require(failure.getCause() instanceof IOException,"UNEXPECTED_READ_CLOSE_FAILURE:"+failure.getCause());
            }
            require(input.calls==1&&observed(detached,"queryInput")==input&&Boolean.TRUE.equals(observed(detached,"queryClosePending"))
                    &&observed(detached,"queryCloseFailure") instanceof IOException,"FAILED_READ_CLOSE_STATE_LOST");
        } finally { input.close(); }
    }
    private void finish(TickEvent.ServerTickEvent event,ProRuntime runtime,String heading) {
        finished=true;if(release!=null)release.countDown();
        try { if(lock!=null)lock.release();if(locked!=null)locked.close(); }
        catch(IOException failure) { heading+="CLOSE_FAILURE="+failure+"\n"; }
        try { Files.writeString(Path.of("storage-race-result.txt"),heading+String.join("\n",results)+"\n"+String.join("\n",operation==null?List.of():runtime.recoveryStatus(operation))); }
        catch(IOException failure) { failure.printStackTrace(); }
        event.getServer().halt(false);
    }
    private static void require(boolean value,String message) { if(!value)throw new AssertionError(message); }
}
