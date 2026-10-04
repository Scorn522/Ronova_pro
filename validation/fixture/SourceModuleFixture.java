package dev.ronova.pro.validation;

import dev.ronova.pro.ProRuntime;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import net.minecraft.nbt.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.EventPriority;

/** Ordinary mixed-source business data and one shared constructor-only factory. No asserted bindings. */
final class SourceModuleFixture {
    static final class Recipe {
        final Cow prototype;UUID current;int attempts;
        Recipe(Cow prototype) { this.prototype=prototype;current=prototype.getUUID(); }
    }
    static final class Data extends SavedData {
        HashMap<String,Recipe> recipes=new HashMap<>();
        final ConcurrentHashMap<String,CompoundTag> snapshots=new ConcurrentHashMap<>();
        @Override public CompoundTag save(CompoundTag root) { return root; }
    }
    static final class StaticData {
        static HashMap<String,CompoundTag> snapshots=new HashMap<>();
        static void save()throws Exception {
            CompoundTag root=new CompoundTag();snapshots.forEach((k,v)->root.put(k,v.copy()));
            NbtIo.writeCompressed(root,Path.of("static-source-module.dat").toFile());
        }
    }
    static Cow factory(Recipe recipe,ServerLevel level) {
        Cow body=new Cow(EntityType.COW,level); // Deliberately no Entity.load and no clone.
        body.setUUID(UUID.randomUUID());body.setNoAi(true);body.setPos(recipe.prototype.getX(),90,0);
        recipe.current=body.getUUID();recipe.attempts++;return body;
    }
    static final class Job implements Runnable {
        final Recipe recipe;final ServerLevel level;final CountDownLatch entered=new CountDownLatch(1),resume=new CountDownLatch(1),done=new CountDownLatch(1);
        final AtomicReference<Cow> output=new AtomicReference<>();final AtomicReference<Throwable> failure=new AtomicReference<>();
        final AtomicInteger interruptions=new AtomicInteger();
        Job(Recipe recipe,ServerLevel level) { this.recipe=recipe;this.level=level; }
        @Override public void run() {
            entered.countDown();
            try {
                boolean released=false;while(!released)try { released=resume.await(10,TimeUnit.SECONDS);if(!released)throw new AssertionError("job gate timeout"); }
                catch(InterruptedException unexpected) { interruptions.incrementAndGet(); }
                output.set(factory(recipe,level));
            } catch(Throwable error) { failure.set(error); }
            finally { done.countDown(); }
        }
    }
    static final class FailedBody extends Cow {
        FailedBody(ServerLevel level,AtomicBoolean refused) {
            super(EntityType.COW,level);
            refused.set(!level.addFreshEntity(this));
            throw CONSTRUCTION_FAILURE;
        }
    }
    private static final RuntimeException CONSTRUCTION_FAILURE=new RuntimeException("business constructor failure");
    static final class FailedJob implements Runnable {
        final Recipe recipe;final ServerLevel level;
        final CountDownLatch entered=new CountDownLatch(1),resume=new CountDownLatch(1),done=new CountDownLatch(1);
        final AtomicBoolean refused=new AtomicBoolean();volatile Throwable caught;
        FailedJob(Recipe recipe,ServerLevel level) { this.recipe=recipe;this.level=level; }
        @Override public void run() {
            entered.countDown();
            try {if(!resume.await(10,TimeUnit.SECONDS))throw new AssertionError("failed ctor gate");new FailedBody(level,refused);}
            catch(Throwable error) { caught=error; }finally {done.countDown();}
        }
    }
    static final class MixedJob implements Runnable {
        final Recipe left,right;final ServerLevel level;final CountDownLatch done=new CountDownLatch(1);
        volatile Cow output;
        MixedJob(Recipe left,Recipe right,ServerLevel level) { this.left=left;this.right=right;this.level=level; }
        @Override public void run() { try { output=factory(right,level); }finally { done.countDown(); } }
    }
    static final class WideJob implements Runnable {
        final Recipe left;final ServerLevel level;final CountDownLatch entered=new CountDownLatch(1),resume=new CountDownLatch(1),done=new CountDownLatch(1);
        final int pad0=0;
        final int pad1=0;
        final int pad2=0;
        final int pad3=0;
        final int pad4=0;
        final int pad5=0;
        final int pad6=0;
        final int pad7=0;
        final int pad8=0;
        final int pad9=0;
        final int pad10=0;
        final int pad11=0;
        final int pad12=0;
        final int pad13=0;
        final int pad14=0;
        final int pad15=0;
        final int pad16=0;
        final int pad17=0;
        final int pad18=0;
        final int pad19=0;
        final int pad20=0;
        final int pad21=0;
        final int pad22=0;
        final int pad23=0;
        final int pad24=0;
        final int pad25=0;
        final int pad26=0;
        final int pad27=0;
        final int pad28=0;
        final int pad29=0;
        final int pad30=0;
        final int pad31=0;
        final int pad32=0;
        final int pad33=0;
        final int pad34=0;
        final int pad35=0;
        final int pad36=0;
        final int pad37=0;
        final int pad38=0;
        final int pad39=0;
        final int pad40=0;
        final int pad41=0;
        final int pad42=0;
        final int pad43=0;
        final int pad44=0;
        final int pad45=0;
        final int pad46=0;
        final int pad47=0;
        final int pad48=0;
        final int pad49=0;
        final int pad50=0;
        final int pad51=0;
        final int pad52=0;
        final int pad53=0;
        final int pad54=0;
        final int pad55=0;
        final int pad56=0;
        final int pad57=0;
        final int pad58=0;
        final int pad59=0;
        final int pad60=0;
        final int pad61=0;
        final int pad62=0;
        final int pad63=0;
        final int pad64=0;
        final int pad65=0;
        final int pad66=0;
        final int pad67=0;
        final int pad68=0;
        final int pad69=0;
        final int pad70=0;
        final int pad71=0;
        final int pad72=0;
        final int pad73=0;
        final int pad74=0;
        final int pad75=0;
        final int pad76=0;
        final int pad77=0;
        final int pad78=0;
        final int pad79=0;
        final int pad80=0;
        final int pad81=0;
        final int pad82=0;
        final int pad83=0;
        final int pad84=0;
        final int pad85=0;
        final int pad86=0;
        final int pad87=0;
        final int pad88=0;
        final int pad89=0;
        final int pad90=0;
        final int pad91=0;
        final int pad92=0;
        final int pad93=0;
        final int pad94=0;
        final int pad95=0;
        final Recipe right;volatile Cow output;volatile Throwable failure;
        WideJob(Recipe left,Recipe right,ServerLevel level) {this.left=left;this.right=right;this.level=level;}
        @Override public void run() {entered.countDown();try {if(!resume.await(10,TimeUnit.SECONDS))throw new AssertionError("wide gate");output=factory(right,level);}catch(Throwable error){failure=error;}finally {done.countDown();}}
    }
    private final Data data=new Data();
    private final ExecutorService shared=Executors.newFixedThreadPool(3,r->{Thread t=new Thread(r,"source-module-shared");t.setDaemon(true);return t;});
    private final List<String> facts=new ArrayList<>();
    private Cow target,neighbor;private Recipe removed,retained;private CompoundTag original,foreign;
    private WideJob wideJob;private FailedJob failedJob;private Job inFlight,queued;private Future<?> queuedFuture;private UUID operation;private int age,phase;private boolean finished;
    SourceModuleFixture() { MinecraftForge.EVENT_BUS.addListener(EventPriority.LOWEST,this::tick); }
    private static void require(boolean condition,String text) { if(!condition)throw new AssertionError(text); }
    private static Cow spawn(ServerLevel level,int x) {
        Cow body=new Cow(EntityType.COW,level);body.setPos(x,90,0);body.setNoAi(true);
        require(level.addFreshEntity(body),"normal spawn");return body;
    }
    private void tick(TickEvent.ServerTickEvent event) {
        if(finished||event.phase!=TickEvent.Phase.END)return;
        var runtime=ProRuntime.get(event.getServer());if(runtime==null)return;
        try {
            if(++age>500)throw new AssertionError("source module timeout phase="+phase+" "+runtime.recoveryStatus(operation));
            ServerLevel level=event.getServer().overworld();
            if(phase==0&&age>=20) {
                target=spawn(level,2);neighbor=spawn(level,8);
                original=target.saveWithoutId(new CompoundTag());foreign=neighbor.saveWithoutId(new CompoundTag());
                removed=new Recipe(target);retained=new Recipe(neighbor);
                data.recipes.put("target",removed);data.recipes.put("neighbor",retained);
                data.snapshots.put("target",original);data.snapshots.put("neighbor",foreign);
                level.getDataStorage().set("source_module_fixture",data);
                StaticData.snapshots.put("target",original.copy());StaticData.snapshots.put("neighbor",foreign.copy());StaticData.save();
                inFlight=new Job(removed,level);shared.submit(inFlight);
                require(inFlight.entered.await(10,TimeUnit.SECONDS),"real task entered before clear");
                failedJob=new FailedJob(removed,level);shared.submit(failedJob);
                require(failedJob.entered.await(10,TimeUnit.SECONDS),"failed constructor invocation began before clear");
                wideJob=new WideJob(removed,retained,level);shared.submit(wideJob);
                require(wideJob.entered.await(10,TimeUnit.SECONDS),"wide mixed-input invocation began before clear");
                operation=FixtureCommands.clear(runtime,target);inFlight.resume.countDown();failedJob.resume.countDown();wideJob.resume.countDown();
                require(inFlight.done.await(10,TimeUnit.SECONDS)&&inFlight.failure.get()==null,"in-flight factory returned:"+inFlight.failure.get());
                Cow result=inFlight.output.get();require(result!=null&&!result.getUUID().equals(target.getUUID()),"constructor-only fresh UUID output");
                require(!level.addFreshEntity(result)&&level.getEntity(result.getUUID())!=result,"in-flight constructor output escaped first publication");
                require(inFlight.interruptions.get()==0,"shared worker was interrupted");
                facts.add("INFLIGHT_TASK_PLAIN_CONSTRUCTOR_NEW_UUID_REFUSED_WITHOUT_NBT_OR_CLONE");
                require(failedJob.done.await(10,TimeUnit.SECONDS)&&failedJob.caught==CONSTRUCTION_FAILURE,"original constructor exception reaches business catch");
                require(failedJob.refused.get(),"self-publication from a failed old-task constructor escaped");
                require(runtime.recoveryStatus(operation).stream().anyMatch(row->row.contains("CONSTRUCTOR_SIDE_EFFECTS_UNKNOWN")),"constructor failure side effects were silently settled");
                facts.add("CONSTRUCTOR_SELF_PUBLICATION_REFUSED_AND_EXCEPTION_EFFECTS_EXPLICIT");
                require(wideJob.done.await(10,TimeUnit.SECONDS)&&wideJob.failure==null&&wideJob.output!=null&&level.addFreshEntity(wideJob.output),"truncated capture falsely assigned neighbor output to cleared input:"+wideJob.failure);
                require(runtime.recoveryStatus(operation).stream().anyMatch(row->row.contains("CONSTRUCTOR_CAPTURE_INCOMPLETE")),"capture budget gap was not explicit");
                facts.add("INCOMPLETE_CAPTURE_NEVER_GRANTS_CONSTRUCTOR_DISPOSITION");
                Job control=new Job(retained,level);control.resume.countDown();shared.submit(control);
                require(control.done.await(10,TimeUnit.SECONDS)&&control.failure.get()==null,"same pool neighbor task");
                require(level.addFreshEntity(control.output.get()),"shared factory confused the neighbor input");
                facts.add("SAME_FACTORY_OTHER_INPUT_AND_SHARED_POOL_PRESERVED");phase=1;
            }
            if(phase==1&&!data.recipes.containsKey("target")&&!data.snapshots.containsKey("target")&&!StaticData.snapshots.containsKey("target")) {
                require(data.recipes.get("neighbor")==retained&&data.snapshots.get("neighbor")==foreign,"unrelated memory record lost");
                require(StaticData.snapshots.containsKey("neighbor"),"unrelated static record lost");
                facts.add("PLAIN_HASHMAP_MUTABLE_BOOKKEEPING_WRAPPER_AND_STATIC_SOURCE_PHYSICALLY_UNLINKED");
                var originalRecipes=data.recipes;data.recipes=new HashMap<>();
                require(data.recipes==originalRecipes,"direct SavedData source Map replacement escaped");
                facts.add("DIRECT_SAVED_DATA_MAP_REPLACEMENT_REFUSED");
                var originalStatic=StaticData.snapshots;
                StaticData.snapshots=new HashMap<>();
                require(StaticData.snapshots==originalStatic,"direct static holder replacement escaped");
                var sourceField=StaticData.class.getDeclaredField("snapshots");sourceField.setAccessible(true);
                try {sourceField.set(null,new HashMap<String,CompoundTag>());}catch(IllegalAccessException refused) { }
                require(StaticData.snapshots==originalStatic,"reflective static holder replacement escaped");
                java.lang.invoke.MethodHandles.privateLookupIn(StaticData.class,java.lang.invoke.MethodHandles.lookup())
                    .findStaticSetter(StaticData.class,"snapshots",HashMap.class).invoke(new HashMap<String,CompoundTag>());
                require(StaticData.snapshots==originalStatic,"handle static holder replacement escaped");
                Class<?> unsafeType=Class.forName("sun.misc.Unsafe");var singleton=unsafeType.getDeclaredField("theUnsafe");
                singleton.setAccessible(true);Object unsafe=singleton.get(null);
                Object base=unsafeType.getMethod("staticFieldBase",java.lang.reflect.Field.class).invoke(unsafe,sourceField);
                long offset=(Long)unsafeType.getMethod("staticFieldOffset",java.lang.reflect.Field.class).invoke(unsafe,sourceField);
                unsafeType.getMethod("putObject",Object.class,long.class,Object.class).invoke(unsafe,base,offset,new HashMap<String,CompoundTag>());
                require(StaticData.snapshots==originalStatic,"Unsafe static holder replacement escaped");
                facts.add("DIRECT_REFLECTION_HANDLE_UNSAFE_STATIC_HOLDER_REPLACEMENT_REFUSED");
                AtomicInteger calls=new AtomicInteger();
                data.recipes.put("target",removed);data.recipes.putIfAbsent("absent",removed);
                data.recipes.computeIfAbsent("compute",k->{calls.incrementAndGet();return removed;});
                data.recipes.compute("new",(k,v)->{calls.incrementAndGet();return removed;});
                data.recipes.merge("merge",removed,(a,b)->b);
                data.recipes.putAll(Map.of("bulk-target",removed,"bulk-neighbor",retained));
                require(data.recipes.keySet().equals(Set.of("neighbor","bulk-neighbor")),"HashMap incoming source paths republished old record");
                require(calls.get()==2,"HashMap callbacks repeated or skipped");
                require(!data.recipes.replace("neighbor",retained,removed),"conditional HashMap replace reported success");
                data.recipes.replaceAll((k,v)->removed);
                require(data.recipes.values().stream().allMatch(v->v==retained),"replaceAll overwrote unrelated records");
                data.recipes.entrySet().iterator().next().setValue(removed);
                require(data.recipes.values().stream().allMatch(v->v==retained),"entry setter republished source");
                data.snapshots.put("target",original);data.snapshots.computeIfAbsent("missing",k->original);
                data.snapshots.compute("new",(k,v)->original);data.snapshots.merge("merged",original,(a,b)->b);
                require(data.snapshots.keySet().equals(Set.of("neighbor")),"CHM incoming source paths republished old record");
                require(!data.snapshots.replace("neighbor",foreign,original),"conditional CHM replace reported success");
                data.snapshots.replaceAll((k,v)->original);
                require(data.snapshots.get("neighbor")==foreign,"CHM replaceAll changed the unrelated record");
                CompoundTag mixed=original.copy();mixed.put("foreign",foreign.copy());
                data.snapshots.put("mixed-foreign",mixed);require(data.snapshots.get("mixed-foreign")==mixed,"mixed foreign source was rejected as target-only");
                StaticData.snapshots.put("target",original.copy());require(!StaticData.snapshots.containsKey("target"),"static source reinsertion");
                facts.add("HASHMAP_AND_CHM_INSERT_COMPUTE_MERGE_REPLACE_BULK_AND_ENTRY_REFILL_REFUSED");
                queued=new Job(removed,level);queued.resume.countDown();queuedFuture=shared.submit(queued);
                phase=2;
            }
            if(phase==2&&age>=100) {
                require(queuedFuture.isDone()&&queued.entered.getCount()==1&&removed.attempts==1,"queued old-source constructor task was not precluded");
                facts.add("QUEUED_OLD_SOURCE_FACTORY_PRECLUDED_BEFORE_ITS_BODY");
                require(!data.recipes.containsValue(removed)&&!data.snapshots.containsValue(original),"source grew back after repeated maintenance");
                MixedJob mixed=new MixedJob(removed,retained,level);shared.submit(mixed);
                require(mixed.done.await(10,TimeUnit.SECONDS)&&mixed.output!=null,"mixed task did not run");
                require(level.addFreshEntity(mixed.output),"ambiguous task output was guessed to belong to the removed subject");
                require(runtime.recoveryStatus(operation).stream().anyMatch(row->row.contains("MIXED_TASK_CONSTRUCTOR_OUTPUT")),"ambiguous producer was not reported");
                require(level.getEntity(neighbor.getUUID())==neighbor,"neighbor body lost");
                facts.add("MIXED_TASK_REMAINS_EXPLICITLY_UNRESOLVED_WITHOUT_COLLATERAL_REMOVAL");
                // The cleared body is no longer resolvable by an entity selector.
                // Cancel its original operation through the real command instead.
                FixtureCommands.cancel(runtime,operation);
                require(runtime.query(operation)!=null&&"OPERATION_CANCELLED".equals(runtime.query(operation).reason()),"original source qualification was not revoked");
                require(!data.recipes.containsValue(removed)&&!StaticData.snapshots.containsKey("target"),"disposition was only a temporary read filter");
                data.recipes.put("after-revoke",removed);data.snapshots.put("after-revoke",original);
                require(data.recipes.get("after-revoke")==removed&&data.snapshots.get("after-revoke")==original,"source guard retained stale qualification");
                facts.add("PHYSICAL_UNLINK_SURVIVES_FENCE_RELEASE_AND_NEW_EXPLICIT_WRITES_RESUME");
                finish(event,"SOURCE_MODULE_PASS\n"+String.join("\n",facts));
            }
        } catch(Throwable failure) {
            failure.printStackTrace();finish(event,"FAILED\n"+failure+"\n"+String.join("\n",facts)+"\n"+
                    (operation==null?"":String.join("\n",runtime.recoveryStatus(operation))));
        }
    }
    private void finish(TickEvent.ServerTickEvent event,String text) {
        finished=true;if(inFlight!=null)inFlight.resume.countDown();if(failedJob!=null)failedJob.resume.countDown();if(wideJob!=null)wideJob.resume.countDown();shared.shutdown();
        try { Files.writeString(Path.of("source-module-result.txt"),text); }catch(Exception failed) { failed.printStackTrace(); }
        event.getServer().halt(false);
    }
}
