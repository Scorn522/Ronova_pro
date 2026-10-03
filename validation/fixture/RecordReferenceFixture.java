package dev.ronova.pro.validation;

import dev.ronova.pro.ProRuntime;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.nbt.*;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.EventPriority;

/** Only normal business save/reinsert behavior and result checks; no observer or final-binding calls. */
public final class RecordReferenceFixture {
    private static volatile Cow DIRECT_TARGET,DIRECT_NEIGHBOR;
    private static long scalarLong=17L;
    private static double scalarDouble=0.5;
    public static final ConcurrentHashMap<String,CompoundTag> STATIC_RECORDS=new ConcurrentHashMap<>();
    public static final class Data extends SavedData {
        public volatile CompoundTag directSnapshot;
        public CompoundTag directRead() {return directSnapshot;}
        public final ConcurrentHashMap<String,CompoundTag> records=new ConcurrentHashMap<>();
        public final java.util.LinkedHashMap<String,CompoundTag> orderedRecords=new java.util.LinkedHashMap<>();
        public final it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap<CompoundTag> intRecords=new it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap<>();
        public final it.unimi.dsi.fastutil.ints.Int2ObjectLinkedOpenHashMap<CompoundTag> linkedIntRecords=new it.unimi.dsi.fastutil.ints.Int2ObjectLinkedOpenHashMap<>();
        public final it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap<CompoundTag> longRecords=new it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap<>();
        public final it.unimi.dsi.fastutil.objects.ObjectArrayList<CompoundTag> objectRecords=new it.unimi.dsi.fastutil.objects.ObjectArrayList<>();
        public final it.unimi.dsi.fastutil.objects.ObjectOpenHashSet<CompoundTag> setRecords=new it.unimi.dsi.fastutil.objects.ObjectOpenHashSet<>();
        public final it.unimi.dsi.fastutil.objects.ObjectOpenHashSet<SharedRecord> objectSharedSet=new it.unimi.dsi.fastutil.objects.ObjectOpenHashSet<>();
        public final HashMap<String,CompoundTag> nullRecords=new HashMap<>();
        public final SharedRecord shared=new SharedRecord();
        public final HashSet<SharedRecord> sharedSet=new HashSet<>();
        public HashMap<String,SharedRecord> sharedMap=new HashMap<>();
        public final ConcurrentHashMap<String,SharedRecord> concurrentSharedMap=new ConcurrentHashMap<>();
        public final ConcurrentHashMap<String,BusinessRecord> businessRecords=new ConcurrentHashMap<>();
        @Override public CompoundTag save(CompoundTag root) {
            CompoundTag entries=new CompoundTag();records.forEach((key,value)->entries.put(key,value.copy()));
            root.put("records",entries);root.putString("unknown-marker","must-survive");
            CompoundTag business=new CompoundTag();businessRecords.forEach((key,value)->business.put(key,value.snapshot.copy()));
            root.put("business-records",business);
            return root;
        }
    }
    public static final class SharedRecord {
        public CompoundTag target,neighbor;
        public final String marker="shared-metadata-preserved";
    }
    /** Exact immutable business wrapper profile; the snapshot is the sole owned target. */
    private static final class BusinessRecord {
        final CompoundTag snapshot;final String marker;final UUID recordId;final int revision;
        BusinessRecord(CompoundTag snapshot,String marker,UUID recordId,int revision) {
            this.snapshot=snapshot;this.marker=marker;this.recordId=recordId;this.revision=revision;
        }
    }
    private static final class RebuildTask implements Runnable {
        final Cow owner;final ConcurrentHashMap<String,BusinessRecord> records;
        final BusinessRecord prior;final AtomicInteger bodies;
        RebuildTask(Cow owner,ConcurrentHashMap<String,BusinessRecord> records,BusinessRecord prior,AtomicInteger bodies) {
            this.owner=owner;this.records=records;this.prior=prior;this.bodies=bodies;
        }
        @Override public void run() {
            bodies.incrementAndGet();
            // Ordinary business callback would reinsert the previously captured source slot.
            if(owner.getUUID()!=null)records.putIfAbsent("target",prior);
        }
    }
    private final Data data=new Data();
    private Cow target,neighbor;
    private CompoundTag original,unrelated,mixed;
    private UUID operation;
    private UUID scalarOperation;
    private int tick,phase;
    private boolean finished;
    private final CountDownLatch writerGate=new CountDownLatch(1),writerEntered=new CountDownLatch(1);
    private final ExecutorService immediate=Executors.newSingleThreadExecutor(r->{Thread t=new Thread(r,"record-fixture-immediate");t.setDaemon(true);return t;});
    private final ScheduledThreadPoolExecutor delayed=new ScheduledThreadPoolExecutor(1,r->{Thread t=new Thread(r,"record-fixture-delayed");t.setDaemon(true);return t;});
    private final AtomicInteger taskBodies=new AtomicInteger();
    private boolean businessVariant;
    private boolean directVariant;
    private BusinessRecord wrapped,mixedWrapped;
    private Cow ordinaryFresh;
    public RecordReferenceFixture() {
        businessVariant="business-wrapper".equals(System.getProperty("ronova.pro.fixture.variant"));
        directVariant="direct-fields".equals(System.getProperty("ronova.pro.fixture.variant"));
        MinecraftForge.EVENT_BUS.addListener(EventPriority.LOWEST,this::tick);
    }
    public static void saveStatic(File file)throws IOException {
        CompoundTag root=new CompoundTag();STATIC_RECORDS.forEach((key,value)->root.put(key,value.copy()));
        root.putString("unknown-marker","static-preserved");NbtIo.writeCompressed(root,file);
    }
    private void tick(TickEvent.ServerTickEvent event) {
        if(finished||event.phase!=TickEvent.Phase.END)return;
        var runtime=ProRuntime.get(event.getServer());if(runtime==null)return;
        tick++;
        try {
            if(businessVariant) { businessTick(event,runtime);return; }
            if(directVariant) {directTick(event,runtime);return;}
            if(tick>1600)throw new AssertionError("RECORD_REFERENCE_TIMEOUT phase="+phase);
            if(phase==0) {
                var level=event.getServer().overworld();level.setChunkForced(0,0,true);
                target=new Cow(EntityType.COW,level);target.setNoAi(true);target.setPos(3,80,3);
                if(!level.addFreshEntity(target))throw new AssertionError("spawn");
                original=target.saveWithoutId(new CompoundTag());
                neighbor=new Cow(EntityType.COW,level);neighbor.setNoAi(true);neighbor.setPos(6,80,3);
                if(!level.addFreshEntity(neighbor))throw new AssertionError("neighbor spawn");
                mixed=target.saveWithoutId(new CompoundTag());
                mixed.put("foreign-entity",neighbor.saveWithoutId(new CompoundTag()));
                // Equal content and UUID do not make this independent root the observed original object.
                unrelated=new CompoundTag();unrelated.merge(original);
                data.records.put("target",original);data.records.put("equal-but-unlinked",unrelated);
                data.records.put("mixed-must-remain",mixed);
                data.orderedRecords.put("target",original);data.orderedRecords.put("equal-but-unlinked",unrelated);
                data.intRecords.put(0,original);data.intRecords.put(7,unrelated);
                data.linkedIntRecords.put(0,original);data.linkedIntRecords.put(7,unrelated);
                data.longRecords.put(0L,original);data.longRecords.put(0x100000007L,unrelated);
                data.shared.target=original;data.shared.neighbor=neighbor.saveWithoutId(new CompoundTag());
                SharedRecord setRecord=new SharedRecord();setRecord.target=original;setRecord.neighbor=data.shared.neighbor;
                data.objectRecords.add(original);data.objectRecords.add(unrelated);
                data.setRecords.add(original);data.setRecords.add(data.shared.neighbor);
                data.nullRecords.put(null,original);data.nullRecords.put("null:",unrelated);
                data.objectSharedSet.add(setRecord);data.sharedMap.put(null,setRecord);
                data.sharedSet.add(setRecord);data.sharedMap.put("initial",setRecord);data.concurrentSharedMap.put("initial",setRecord);
                STATIC_RECORDS.put("target",original);STATIC_RECORDS.put("equal-but-unlinked",unrelated);
                level.getDataStorage().set("pro_record_reference_fixture",data);data.setDirty();
                level.getDataStorage().save();
                Files.createDirectories(Path.of("record-reference-fixture"));
                saveStatic(Path.of("record-reference-fixture","static.dat").toFile());
                operation=FixtureCommands.clear(runtime,target);phase=1;
            } else if(phase==1&&!data.records.containsKey("target")&&!STATIC_RECORDS.containsKey("target")&&!data.orderedRecords.containsKey("target")) {
                if(data.intRecords.containsKey(0)||data.linkedIntRecords.containsKey(0)||data.longRecords.containsKey(0L)||data.shared.target!=null)return;
                for(SharedRecord shared:data.sharedSet)if(shared.target!=null)return;
                if(data.objectRecords.stream().anyMatch(value->value==original)||data.setRecords.stream().anyMatch(value->value==original)||data.nullRecords.containsKey(null))return;
                if(runtime.recoveryStatus(operation).stream().filter(row->row.startsWith("MEMORY_RECORD:")&&row.contains(":REFERENCE_DISPOSED:")).count()<6)return;
                require(data.records.get("equal-but-unlinked")==unrelated&&STATIC_RECORDS.get("equal-but-unlinked")==unrelated,"equal unrelated identity was changed");
                data.records.put("target",original);STATIC_RECORDS.put("target",original);data.orderedRecords.put("target",original);
                data.intRecords.put(0,original);data.linkedIntRecords.put(0,original);data.longRecords.put(0L,original);data.shared.target=original;
                require(!data.intRecords.containsKey(0)&&!data.linkedIntRecords.containsKey(0)&&!data.longRecords.containsKey(0L)&&data.shared.target==null,"primitive keys and shared child fields refuse the old record");
                require(!data.records.containsKey("target")&&!STATIC_RECORDS.containsKey("target")&&!data.orderedRecords.containsKey("target"),"old record refill must be refused at publication");phase=2;
                require(data.objectRecords.size()==1&&data.objectRecords.get(0)==unrelated&&data.setRecords.size()==1&&data.setRecords.iterator().next()==data.shared.neighbor,"fastutil cleanup preserves unrelated identities");
                require(data.nullRecords.get("null:")==unrelated,"null key encoding does not collide with a literal key");
                data.objectRecords.add(original);data.objectRecords.set(0,original);data.objectRecords.addAll(List.of(original));
                data.objectRecords.addElements(0,new CompoundTag[]{original});
                var view=data.objectRecords.subList(0,1);view.addAll(List.of(original));
                var listIterator=data.objectRecords.listIterator();listIterator.add(original);var viewIterator=view.listIterator();viewIterator.add(original);
                require(listIterator.next()==unrelated&&viewIterator.next()==unrelated,"refused iterator additions preserve the next unrelated identity");
                require(view.size()==1&&view.get(0)==unrelated,"refused sublist publication preserves the actual view extent");
                data.objectRecords.elements()[0]=original;data.setRecords.add(original);data.setRecords.addOrGet(original);data.setRecords.addAll(List.of(original));data.nullRecords.put(null,original);
                require(data.objectRecords.size()==1&&data.objectRecords.get(0)==unrelated&&data.setRecords.size()==1&&!data.nullRecords.containsKey(null),"fastutil bulk, backing alias and null-key refills are refused");
                SharedRecord incoming=new SharedRecord();incoming.neighbor=data.shared.neighbor;
                data.sharedMap.put("published",incoming);data.concurrentSharedMap.put("published",incoming);
                require(data.sharedMap.get("published")==incoming&&data.concurrentSharedMap.get("published")==incoming,"unrelated mutable wrapper publication remains allowed");
                incoming.target=original;
                require(incoming.target==null,"successfully published mutable wrapper refuses a later old-target field write");
                SharedRecord refused=new SharedRecord();refused.target=original;refused.neighbor=data.shared.neighbor;
                data.sharedMap.put("refused",refused);data.concurrentSharedMap.put("refused",refused);data.sharedSet.add(refused);data.objectSharedSet.add(refused);
                require(!data.sharedMap.containsKey("refused")&&!data.concurrentSharedMap.containsKey("refused")&&!data.sharedSet.contains(refused)&&!data.objectSharedSet.contains(refused),"shared wrapper containing the old target is refused before publication");
                HashMap<String,SharedRecord> detached=data.sharedMap;data.sharedMap=new HashMap<>();
                require(data.sharedMap!=detached,"legitimate source-container replacement remains allowed");
                SharedRecord late=new SharedRecord();late.neighbor=data.shared.neighbor;detached.put("late",late);
                require(detached.get("late")==late,"detached original source permits the unrelated wrapper");
                late.target=original;detached.put("refused",refused);
                require(late.target==null&&!detached.containsKey("refused"),"old source alias retains its field and publication guards after holder replacement");
            } else if(phase==2&&!data.records.containsKey("target")&&!STATIC_RECORDS.containsKey("target")) {
                require(data.records.get("equal-but-unlinked")==unrelated&&STATIC_RECORDS.get("equal-but-unlinked")==unrelated,"unrelated entry after reinsertion");
                List<String> status=runtime.recoveryStatus(operation);
                if(status.stream().filter(row->row.startsWith("MEMORY_RECORD:")&&row.contains(":REFERENCE_DISPOSED:")).count()<6)return;
                require(data.intRecords.get(7)==unrelated&&data.linkedIntRecords.get(7)==unrelated&&data.longRecords.get(0x100000007L)==unrelated,"primitive-map neighbors and long key width survive");
                require(data.linkedIntRecords.firstIntKey()==7&&data.linkedIntRecords.lastIntKey()==7,"linked primitive map retains a valid order chain");
                require(data.shared.neighbor!=null&&data.shared.neighbor.getUUID("UUID").equals(neighbor.getUUID())&&data.shared.marker.equals("shared-metadata-preserved"),"shared wrapper preserves its other record and metadata");
                require(data.sharedSet.size()==1,"shared Set wrapper remains present");
                for(SharedRecord shared:data.sharedSet)require(shared.target==null&&shared.neighbor==data.shared.neighbor&&shared.marker.equals("shared-metadata-preserved"),"shared Set clears only the selected child field");
                require(data.orderedRecords.get("equal-but-unlinked")==unrelated&&data.orderedRecords.size()==1,"ordered map preserves the unrelated record and its linked structure");
                require(status.stream().noneMatch(row->row.contains("MEMORY_CONTAINER_BACKEND_UNSUPPORTED:orderedRecords")),"ordered map uses the implemented node backend");
                require(data.records.get("mixed-must-remain")==mixed&&!neighbor.isRemoved(),"foreign record or neighbor was affected");
                require(status.stream().anyMatch(row->row.contains("FOREIGN_SUBJECT_IN_RECORD_SUBTREE")),"mixed record must remain unresolved");
                Files.writeString(Path.of("record-reference-result.txt"),"EXACT_STATIC_AND_SAVED_DATA_REFERENCES_DISPOSED\n"+
                        "REINSERTION_REFUSED_BEFORE_REPUBLISH\nEQUAL_UNLINKED_RECORDS_PRESERVED\nORDERED_MAP_REFERENCE_DISPOSED_WITH_NEIGHBOR_PRESERVED\nO02_O05_NOT_CERTIFIED\n"+
                        String.join("\n",status));finished=true;
                event.getServer().halt(false);
            }
        } catch(Throwable failure) {
            finish();
            try { Files.writeString(Path.of(businessVariant?"record-reference-business-result.txt":directVariant?"record-reference-direct-result.txt":"record-reference-result.txt"),"FAILED\n"+failure+"\n"+
                    String.join("\n",operation==null?List.of():runtime.recoveryStatus(operation))); }
            catch(IOException writeFailure) { failure.addSuppressed(writeFailure); }
            failure.printStackTrace();
            event.getServer().halt(false);
        }
    }
    private static Cow directRead() {return DIRECT_TARGET;}
    private static Cow neighborRead() {return DIRECT_NEIGHBOR;}
    private static Cow freshFromScalars(net.minecraft.server.level.ServerLevel level) {
        long seed=scalarLong;double height=scalarDouble;
        Cow fresh=new Cow(EntityType.COW,level);fresh.setPos(seed,80+height,3);return fresh;
    }
    private void directTick(TickEvent.ServerTickEvent event,ProRuntime runtime)throws Exception {
        var level=event.getServer().overworld();
        if(phase==0) {
            level.setChunkForced(0,0,true);
            Cow old=new Cow(EntityType.COW,level),other=new Cow(EntityType.COW,level);
            old.setNoAi(true);other.setNoAi(true);old.setPos(3,80,3);other.setPos(6,80,3);
            require(level.addFreshEntity(old)&&level.addFreshEntity(other),"direct source setup");
            CompoundTag tag=old.saveWithoutId(new CompoundTag());
            DIRECT_TARGET=old;DIRECT_NEIGHBOR=other;data.directSnapshot=tag;
            level.getDataStorage().set("pro_direct_field_fixture",data);
            FixtureCommands.protect(runtime,other);
            require(directRead()==old&&neighborRead()==other&&data.directRead()==tag,"ordinary field consumption");
            DIRECT_NEIGHBOR=null;
            require(DIRECT_NEIGHBOR==other,"protected observed alias cannot be cleared");
            operation=FixtureCommands.clear(runtime,old);
            require(DIRECT_TARGET==null&&data.directSnapshot==null,"direct fields cleared in request without ACK wait");
            DIRECT_TARGET=old;data.directSnapshot=tag;
            require(DIRECT_TARGET==null&&data.directSnapshot==null,"terminal source cannot republish old references");
            Cow fresh=freshFromScalars(level);
            require(level.addFreshEntity(fresh)&&!fresh.isRemoved(),"unrelated scalar factory is not attributed by class or location");
            require(!fresh.getUUID().equals(old.getUUID())&&scalarLong==17&&scalarDouble==0.5,"scalar values and unrelated UUID preserved");
            FixtureCommands.revoke(runtime,other);DIRECT_NEIGHBOR=null;
            require(DIRECT_NEIGHBOR==null&&!other.isRemoved(),"revocation releases alias guard and preserves neighbor");
            ordinaryFresh=fresh;scalarOperation=FixtureCommands.clear(runtime,fresh);phase=1;
            return;
        } else if(phase==1&&tick>=12) {
            require(runtime.recoveryStatus(scalarOperation).stream().anyMatch(row->row.contains("SCALAR_PRODUCER_CONSUMPTION_REQUIRES_DISPOSITION:")&&row.contains("scalarLong")),
                    "first scalar-created body must retain actual source consumption when later qualified: "+captureDiagnostic(runtime,ordinaryFresh)+runtime.recoveryStatus(scalarOperation));
            require(scalarLong==17L&&scalarDouble==0.5,"shared scalar conditions are not reset to manufacture source termination");
            Files.writeString(Path.of("record-reference-direct-result.txt"),
                    "V15_DIRECT_FIELDS_RUNTIME_PASS\nSTATIC_AND_SAVED_DATA_ALIAS_CLEARED_IN_REQUEST\nREPUBLICATION_REFUSED\nPROTECTED_ALIAS_AND_REVOKE_PASS\nLONG_DOUBLE_AND_UNRELATED_FACTORY_PASS\nFIRST_CREATION_SCALAR_SOURCE_RETAINED_NOT_CLEARED\nO02_O05_NOT_CERTIFIED\n");
            finished=true;finish();event.getServer().halt(false);
        }
    }
    private static String captureDiagnostic(ProRuntime runtime,Cow created)throws Exception {
        var recordsField=ProRuntime.class.getDeclaredField("recoveryRecords");recordsField.setAccessible(true);
        Object records=recordsField.get(runtime);var refsField=records.getClass().getDeclaredField("references");refsField.setAccessible(true);
        Object refs=refsField.get(records);var pending=refs.getClass().getDeclaredField("creationReads");pending.setAccessible(true);
        var reads=refs.getClass().getDeclaredField("fieldReads");reads.setAccessible(true);
        StringBuilder result=new StringBuilder("pending=").append(((Map<?,?>)pending.get(refs)).get(created)).append(";reads=");
        for(Object read:(List<?>)reads.get(refs)) {
            var field=read.getClass().getDeclaredField("field");field.setAccessible(true);
            var consumers=read.getClass().getDeclaredField("consumers");consumers.setAccessible(true);
            result.append(((java.lang.reflect.Field)field.get(read)).getName()).append(':').append(consumers.get(read)).append(';');
        }
        Class<?> bridge=Class.forName("dev.ronova.pro.bootstrap.TaskBridge",false,null);
        var sites=bridge.getDeclaredField("CREATION_SITES");sites.setAccessible(true);
        return result.append(";sites=").append(((ClassValue<?>)sites.get(null)).get(RecordReferenceFixture.class)).toString();
    }
    private void businessTick(TickEvent.ServerTickEvent event,ProRuntime runtime)throws Exception {
        if(tick>900)throw new AssertionError("RECORD_REFERENCE_BUSINESS_TIMEOUT phase="+phase);
        var level=event.getServer().overworld();
        if(phase==0) {
            level.setChunkForced(0,0,true);
            target=new Cow(EntityType.COW,level);target.setNoAi(true);target.setPos(3,80,3);
            if(!level.addFreshEntity(target))throw new AssertionError("business target spawn");
            neighbor=new Cow(EntityType.COW,level);neighbor.setNoAi(true);neighbor.setPos(6,80,3);
            if(!level.addFreshEntity(neighbor))throw new AssertionError("business neighbor spawn");
            ordinaryFresh=new Cow(EntityType.COW,level);ordinaryFresh.setNoAi(true);ordinaryFresh.setPos(9,80,3);
            if(!level.addFreshEntity(ordinaryFresh))throw new AssertionError("ordinary constructor spawn");
            require(!ordinaryFresh.getUUID().equals(target.getUUID()),"ordinary constructor must allocate a fresh UUID");
            original=target.saveWithoutId(new CompoundTag());
            original.putByteArray("scalar-bytes",new byte[]{1,2,3});
            original.putLongArray("scalar-longs",new long[]{4,5,6});
            wrapped=new BusinessRecord(original,"target",UUID.randomUUID(),1);
            mixed=target.saveWithoutId(new CompoundTag());
            mixed.put("foreign-entity",neighbor.saveWithoutId(new CompoundTag()));
            mixedWrapped=new BusinessRecord(mixed,"mixed-foreign",UUID.randomUUID(),1);
            data.businessRecords.put("target",wrapped);
            data.businessRecords.put("mixed-foreign",mixedWrapped);
            unrelated=new CompoundTag();unrelated.merge(original);
            data.records.put("equal-but-unlinked",unrelated);
            level.getDataStorage().set("pro_record_reference_business_fixture",data);data.setDirty();
            level.getDataStorage().save();
            var journalField=ProRuntime.class.getDeclaredField("journal");journalField.setAccessible(true);
            Object journal=journalField.get(runtime);
            var writerField=dev.ronova.pro.IntentJournal.class.getDeclaredField("writer");writerField.setAccessible(true);
            ((Executor)writerField.get(journal)).execute(()->{
                writerEntered.countDown();
                try { writerGate.await(10,TimeUnit.SECONDS); }
                catch(InterruptedException interrupted) { Thread.currentThread().interrupt(); }
            });
            phase=1;
        } else if(phase==1&&writerEntered.getCount()==0) {
            operation=FixtureCommands.clear(runtime,target);
            // Immediate and delayed ordinary tasks both retain the actual owner; their body would
            // recreate the old exact slot only if it were allowed to run after clear.
            RebuildTask now=new RebuildTask(target,data.businessRecords,wrapped,taskBodies);
            immediate.submit(now);
            delayed.schedule(new RebuildTask(target,data.businessRecords,wrapped,taskBodies),1500,TimeUnit.MILLISECONDS);
            phase=2;
        } else if(phase==2&&tick>25&&!data.businessRecords.containsKey("target")) {
            require(target.isRemoved(),"CLEAR physical entity action must finish while journal writer is blocked");
            require(data.businessRecords.get("mixed-foreign")==mixedWrapped,"mixed foreign wrapper must remain untouched");
            require(data.records.get("equal-but-unlinked")==unrelated,"equal but unobserved tag identity must remain untouched");
            require(!neighbor.isRemoved()&&level.getEntity(neighbor.getUUID())==neighbor,"same-holder neighbor must remain");
            List<String> status=runtime.recoveryStatus(operation);
            require(status.stream().anyMatch(row->row.contains("MEMORY_RECORD:")&&row.contains("WAIT_INTENT_ACK_FOR_FACT")),
                    "physical source unlink must remain an open durable obligation during writer stall");
            require(status.stream().noneMatch(row->row.contains("已知恢复链已处置")),"ACK blockage cannot imply completed chain");
            writerGate.countDown();phase=3;
        } else if(phase==3&&tick>125) {
            require(!data.businessRecords.containsKey("target"),"released worker/task must not rebuild the disposed source");
            require(data.businessRecords.get("mixed-foreign")==mixedWrapped,"foreign mixed wrapper remains");
            require(data.records.get("equal-but-unlinked")==unrelated,"equal independent tag remains");
            require(!neighbor.isRemoved()&&level.getEntity(ordinaryFresh.getUUID())==ordinaryFresh,"unrelated ordinary entities remain");
            require(taskBodies.get()==0,"post-clear immediate and delayed rebuild callbacks were suppressed");
            List<String> status=runtime.recoveryStatus(operation);
            require(status.stream().noneMatch(row->row.contains("HOLDER_CONTAINER_UNSUPPORTED:net.minecraft.nbt.IntArrayTag")
                    ||row.contains("HOLDER_CONTAINER_UNSUPPORTED:net.minecraft.nbt.ByteArrayTag")
                    ||row.contains("HOLDER_CONTAINER_UNSUPPORTED:net.minecraft.nbt.LongArrayTag")),
                    "primitive NBT array payloads must not create unsupported object-container gaps");
            Files.writeString(Path.of("record-reference-business-result.txt"),
                    String.join(System.lineSeparator(),List.of(
                            "BUSINESS_WRAPPER_SOURCE_UNLINKED_WHILE_INTENT_ACK_BLOCKED",
                            "MIXED_FOREIGN_AND_EQUAL_UNOBSERVED_PRESERVED",
                            "ORDINARY_CONSTRUCTOR_FRESH_UUID",
                            "IMMEDIATE_AND_DELAYED_REBUILD_TASKS_DID_NOT_RUN",
                            "ACK_RELEASED;CHAIN_COMPLETION_NOT_ASSERTED"))+
                    System.lineSeparator()+String.join(System.lineSeparator(),status));
            finish();event.getServer().halt(false);
        }
    }
    private void finish() {
        finished=true;writerGate.countDown();immediate.shutdownNow();delayed.shutdownNow();
    }
    private static void require(boolean condition,String message) { if(!condition)throw new AssertionError(message); }
}
