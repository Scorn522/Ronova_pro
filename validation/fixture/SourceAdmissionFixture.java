package dev.ronova.pro.validation;

import dev.ronova.pro.ProRuntime;
import dev.ronova.pro.mixin.Access;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.entity.*;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.registries.*;

/** Ordinary save/load, factory, clone and publication calls. No asserted origin or success receipt. */
public final class SourceAdmissionFixture {
    private static final DeferredRegister<EntityType<?>> TYPES=DeferredRegister.create(ForgeRegistries.ENTITY_TYPES,"pro_fixture");
    private static final RegistryObject<EntityType<TemplateCow>> TYPE=TYPES.register("admission_cow",()->
            EntityType.Builder.<TemplateCow>of(TemplateCow::new,MobCategory.CREATURE).sized(.9F,1.4F).build("pro_fixture:admission_cow"));
    private static final AtomicInteger constructed=new AtomicInteger(),loads=new AtomicInteger();
    private static Runnable constructorAction;
    private static final class TemplateCow extends Cow implements Cloneable {
        CountDownLatch entered,resume;
        TemplateCow(EntityType<? extends Cow> type,Level level) {
            super(type,level);constructed.incrementAndGet();setNoAi(true);
            Runnable action=constructorAction;constructorAction=null;if(action!=null)action.run();
        }
        TemplateCow copy()throws CloneNotSupportedException { return (TemplateCow)super.clone(); }
        @Override public void readAdditionalSaveData(CompoundTag tag) {
            if(entered!=null) {
                entered.countDown();
                try { if(!resume.await(10,TimeUnit.SECONDS))throw new AssertionError("INFLIGHT_LOAD_NOT_RELEASED"); }
                catch(InterruptedException failure) { Thread.currentThread().interrupt();throw new AssertionError(failure); }
            }
            loads.incrementAndGet();super.readAdditionalSaveData(tag);
        }
    }
    private final Map<Entity,Integer> joins=new IdentityHashMap<>();
    private final List<String> facts=new ArrayList<>();
    private int age;
    private boolean finished;
    public SourceAdmissionFixture() {
        TYPES.register(FMLJavaModLoadingContext.get().getModEventBus());
        FMLJavaModLoadingContext.get().getModEventBus().addListener((net.minecraftforge.event.entity.EntityAttributeCreationEvent event)->
                event.put(TYPE.get(),Cow.createAttributes().build()));
        MinecraftForge.EVENT_BUS.addListener(EventPriority.HIGHEST,this::joined);
        MinecraftForge.EVENT_BUS.addListener(EventPriority.LOWEST,this::tick);
    }
    private void joined(EntityJoinLevelEvent event) {
        if(!event.getLevel().isClientSide())joins.merge(event.getEntity(),1,Integer::sum);
    }
    private static TemplateCow body(ServerLevel level,int x) {
        TemplateCow cow=TYPE.get().create(level);if(cow==null)throw new AssertionError("FACTORY_NULL");
        cow.setPos(x,90,0);return cow;
    }
    private static void fresh(Entity entity) { entity.setUUID(UUID.randomUUID());entity.setId(entity.getId()+1_000_000); }
    private static void require(boolean value,String message) { if(!value)throw new AssertionError(message); }
    private void rejected(ServerLevel level,Entity entity,String context) {
        require(!level.addFreshEntity(entity),context+":server-add");
        var manager=((Access.Server)level).pro$manager();
        require(!manager.addNewEntity(entity),context+":manager-add");
        require(!manager.addNewEntityWithoutEvent(entity),context+":manager-add-no-event");
        manager.addLegacyChunkEntities(java.util.stream.Stream.of(entity));
        manager.addWorldGenChunkEntities(java.util.stream.Stream.of(entity));
        require(joins.getOrDefault(entity,0)==0,context+":join-event-side-effect");
        require(level.getEntity(entity.getUUID())!=entity&&level.getEntity(entity.getId())!=entity,context+":lookup");
        require(!((Access.Manager)manager).pro$known().contains(entity.getUUID()),context+":known-uuid");
        // Direct lower-level game entry points cannot publish the same already-attributed object either.
        var lookup=((Access.Manager)manager).pro$lookup();lookup.add(entity);
        require(lookup.getEntity(entity.getId())!=entity,context+":direct-lookup");
        ((Access.Server)level).pro$ticks().add(entity);
        require(!((Access.Server)level).pro$ticks().contains(entity),context+":direct-tick");
        EntitySection<Entity> section=new EntitySection<>(Entity.class,Visibility.TICKING);section.add(entity);
        require(section.getEntities().noneMatch(candidate->candidate==entity),context+":direct-section");
        ((Access.Tracking)level.getChunkSource().chunkMap).pro$add(entity);
        require(!((Access.Tracking)level.getChunkSource().chunkMap).pro$tracked().containsKey(entity.getId()),context+":direct-tracking");
    }
    private void tick(TickEvent.ServerTickEvent event) {
        if(finished||event.phase!=TickEvent.Phase.END||++age<20)return;
        var runtime=ProRuntime.get(event.getServer());if(runtime==null)return;
        finished=true;
        Thread loader=null;CountDownLatch resume=new CountDownLatch(1);
        try {
            ServerLevel level=event.getServer().overworld();
            TemplateCow target=body(level,2),neighbor=body(level,6);
            require(level.addFreshEntity(target)&&level.addFreshEntity(neighbor),"INITIAL_SPAWNS");
            CompoundTag tag=new CompoundTag();require(target.save(tag),"NORMAL_SAVE");
            TemplateCow cached=body(level,3);cached.load(tag.copy());fresh(cached);
            TemplateCow clone=target.copy();fresh(clone);
            TemplateCow late=body(level,4);
            late.entered=new CountDownLatch(1);late.resume=resume;
            var failure=new java.util.concurrent.atomic.AtomicReference<Throwable>();
            CompoundTag inflight=tag.copy();
            loader=new Thread(()->{try { late.load(inflight); }catch(Throwable error) { failure.set(error); }},"pro-fixture-source-load");
            loader.setDaemon(true);loader.start();
            require(late.entered.await(10,TimeUnit.SECONDS),"WORKER_LOAD_ENTERED");
            UUID operation=FixtureCommands.clear(runtime,target);
            resume.countDown();loader.join(10_000);
            require(!loader.isAlive()&&failure.get()==null,"WORKER_LOAD_EXIT:"+failure.get());fresh(late);
            // All checks below run in this same tick: no server maintenance can adopt the late load first.
            rejected(level,cached,"CACHED_LOAD");rejected(level,clone,"CACHED_NO_NBT_CLONE");rejected(level,late,"INFLIGHT_WORKER_LOAD");
            facts.add("KNOWN_PRECREATED_AND_INFLIGHT_NEW_UUID_BODIES_REJECTED_BEFORE_JOIN");
            facts.add("LOOKUP_SECTION_TICK_TRACKING_DIRECT_PUBLICATION_REFUSED");
            int before=constructed.get(),reads=loads.get();var callback=new AtomicInteger();
            require(EntityType.create(tag.copy(),level).isEmpty(),"TAG_FACTORY_REFUSED");
            require(EntityType.loadEntityRecursive(tag.copy(),level,e->{callback.incrementAndGet();return e;})==null,"RECURSIVE_FACTORY_REFUSED");
            require(TYPE.get().create(level,tag.copy(),e->callback.incrementAndGet(),new BlockPos(2,90,0),MobSpawnType.COMMAND,false,false)==null,"CONFIGURED_FACTORY_REFUSED");
            require(constructed.get()==before&&loads.get()==reads&&callback.get()==0,"REFUSED_FACTORY_EXECUTED_BUSINESS");
            facts.add("TERMINAL_SOURCE_FACTORY_REFUSED_BEFORE_CONSTRUCTOR_LOAD_AND_CALLBACK");
            UUID neighborId=neighbor.getUUID();double neighborX=neighbor.getX();
            neighbor.load(tag.copy());
            require(neighbor.getUUID().equals(neighborId)&&neighbor.getX()==neighborX&&loads.get()==reads,"REFUSED_LOAD_CHANGED_NEIGHBOR");
            require(!ProRuntime.preventAdmission(neighbor),"REFUSED_LOAD_POISONED_EXISTING_RECEIVER");
            facts.add("DIRECT_LOAD_REFUSED_BEFORE_MUTATION_WITHOUT_RELABELING_NEIGHBOR");
            CompoundTag unrelated=TagParser.parseTag(tag.toString());
            Entity independent=EntityType.create(unrelated,level).orElseThrow();fresh(independent);
            require(level.addFreshEntity(independent),"EQUAL_UNOBSERVED_INPUT_AFFECTED");
            TemplateCow ordinary=body(level,9);require(level.addFreshEntity(ordinary),"ORDINARY_CONSTRUCTOR_AFFECTED");
            require(level.getEntity(neighborId)==neighbor,"NEIGHBOR_LOST");
            facts.add("SAME_TYPE_AND_EQUAL_UNOBSERVED_INPUT_PRESERVED");
            TemplateCow racing=body(level,11);require(level.addFreshEntity(racing),"RACING_SOURCE_SPAWN");
            CompoundTag racingTag=new CompoundTag();require(racing.save(racingTag),"RACING_SOURCE_SAVE");
            before=constructed.get();reads=loads.get();callback.set(0);
            var effects=new AtomicInteger();constructorAction=()->{effects.incrementAndGet();FixtureCommands.clear(runtime,racing);};
            require(EntityType.loadEntityRecursive(racingTag,level,e->{callback.incrementAndGet();return e;})==null,"POST_CONSTRUCTION_POLICY_CHANGE_ESCAPED");
            require(effects.get()==1&&constructed.get()==before+1&&loads.get()==reads&&callback.get()==0,"CONSTRUCTOR_EFFECTS_OR_CALLBACK_MISREPORTED");
            facts.add("POLICY_CHANGE_INSIDE_CONSTRUCTOR_WITHHOLDS_FACTORY_RESULT_AND_RECURSIVE_CALLBACK");
            TemplateCow configured=body(level,12);require(level.addFreshEntity(configured),"CONFIGURED_RACING_SPAWN");
            CompoundTag configuredTag=new CompoundTag();require(configured.save(configuredTag),"CONFIGURED_RACING_SAVE");
            before=constructed.get();callback.set(0);effects.set(0);
            constructorAction=()->{effects.incrementAndGet();FixtureCommands.clear(runtime,configured);};
            require(TYPE.get().create(level,configuredTag,e->callback.incrementAndGet(),new BlockPos(12,90,0),MobSpawnType.COMMAND,false,false)==null,"CONFIGURED_RACING_RESULT_ESCAPED");
            require(effects.get()==1&&constructed.get()==before+1&&callback.get()==0,"CONFIGURED_CONSTRUCTOR_EFFECTS_OR_CALLBACK_MISREPORTED");
            facts.add("CONFIGURED_FACTORY_STOPS_AFTER_REAL_CONSTRUCTOR_BEFORE_CONFIGURATION");
            CompoundTag conflicting=tag.copy();require(neighbor.save(conflicting),"REUSED_SAVE_BUFFER");
            Entity ambiguous=EntityType.create(conflicting.copy(),level).orElseThrow();fresh(ambiguous);
            require(level.addFreshEntity(ambiguous),"AMBIGUOUS_COPY_INHERITED_STALE_TARGET_AUTHORITY");
            require(runtime.recoveryStatus(operation).stream().noneMatch(s->s.contains("已知恢复链已处置")),"AMBIGUOUS_SOURCE_FALSE_COMPLETION");
            facts.add("CONFLICTING_SOURCE_AND_ITS_COPY_REMAIN_UNRESOLVED_WITHOUT_STALE_AUTHORITY");
            FixtureCommands.revoke(runtime,target);
            Entity allowed=EntityType.create(tag.copy(),level).orElseThrow();fresh(allowed);
            require(level.addFreshEntity(allowed),"REVOKED_FACTORY_OR_ADMISSION_STALE");
            require(level.addFreshEntity(late),"REVOKED_WORKER_ORIGIN_STALE");
            require(level.addFreshEntity(clone),"REVOKED_CLONE_ORIGIN_STALE");
            facts.add("REVOCATION_IMMEDIATELY_RELEASES_FACTORY_AND_PREMERGE_ADMISSION");
            Files.writeString(Path.of("source-admission-result.txt"),"SOURCE_ADMISSION_PASS\n"+String.join("\n",facts));
        } catch(Throwable error) {
            resume.countDown();error.printStackTrace();
            try { Files.writeString(Path.of("source-admission-result.txt"),"FAILED\n"+error+"\n"+String.join("\n",facts)); }
            catch(Exception failed) { error.addSuppressed(failed); }
        } finally { event.getServer().halt(false); }
    }
}
