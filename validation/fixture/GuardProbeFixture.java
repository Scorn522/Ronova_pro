package dev.ronova.pro.validation;

import dev.ronova.pro.ProRuntime;
import dev.ronova.pro.mixin.Access;
import java.nio.file.*;
import java.util.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.Cow;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.EventPriority;

/**
 * Positive and negative evidence for the write guards, in one run.
 *
 * A protected exact body and an unprotected neighbour are driven through the same four direct state writers.
 * The protected one must not change; the neighbour must change normally on every one of them. The mirror also
 * checks that a protected body cannot lose its registration slots while the neighbour can.
 */
public final class GuardProbeFixture {
    /** Ordinary hostile business override: a target that refuses the public removal path. */
    private static final class StubbornCow extends Cow {
        StubbornCow(ServerLevel level) { super(EntityType.COW,level); }
        @Override public void remove(RemovalReason reason) { /* hostile override: does nothing */ }
    }

    private final int started;
    private int age;
    private Cow guarded,neighbour;
    private float guardedHealth,neighbourHealth;
    private double guardedMax,neighbourMax;
    private int guardedFire,neighbourFire;
    private int fieldFireObserved=-1;
    private boolean fieldFireReverted;
    private boolean reflectionWriteGranted,reflectionWriteRefused,neighbourReflectionWrite;
    private boolean guardedHeld,neighbourHeld;
    private boolean guardedAbsorbGranted,neighbourAbsorbGranted,drop;
    private UUID protection;
    private boolean finished;
    private String failure;
    private UUID capturedAlias;
    private boolean backingIndexImmediate,workerIndexImmediate,aliasImmediate,mixedClearSelective;
    private boolean trackingImmediate,tickCopyImmediate,revokeImmediate;
    private boolean indexWritesImmediate,callbackPolicyImmediate;


    public GuardProbeFixture() {
        started=0;
        MinecraftForge.EVENT_BUS.addListener(EventPriority.LOWEST,this::tick);
    }

    private void tick(TickEvent.ServerTickEvent event) {
        if(finished||event.phase!=TickEvent.Phase.END)return;
        age++;
        try { step(event.getServer().overworld(),ProRuntime.get(event.getServer())); }
        catch(Throwable error) {
            failure=error.toString();
            finish(event.getServer().overworld());
        }
    }

    private void step(ServerLevel level,ProRuntime runtime)throws Exception {
        if(age==20) {
            guarded=spawn(level,-40,"guard-probe-target");
            neighbour=spawn(level,-36,"guard-probe-neighbour");
            guardedHealth=health(guarded);neighbourHealth=health(neighbour);
            capturedAlias=UUID.randomUUID();lookup(level).pro$uuids().put(capturedAlias,guarded);
            protection=FixtureCommands.protect(runtime,guarded);
            // The guard is effective at request time; the durable fact and its state label land later.
            runtime.tick();
            require(runtime.protectionStatus(guarded).startsWith("ACTIVE"),"protection effective at request");
            level.getServer().getCommands().performPrefixedCommand(level.getServer().createCommandSourceStack(),
                    "ronova_pro release "+guarded.getUUID());
            require(runtime.protectionStatus(guarded).startsWith("ACTIVE"),"forged privileged dispatcher cannot revoke");
            net.minecraft.server.dedicated.DedicatedServer dedicated=
                    (net.minecraft.server.dedicated.DedicatedServer)level.getServer();
            dedicated.handleConsoleInput("ronova_pro release "+guarded.getUUID(),dedicated.createCommandSourceStack());
            dedicated.handleConsoleInputs();
            require(runtime.protectionStatus(guarded).startsWith("ACTIVE"),"forged queued console input cannot revoke");
            require(runtime.query(protection)!=null,"protection operation is queryable");
            require(health(guarded)==guardedHealth,"protection activation itself changed health");
            return;
        }
        if(age<40)return;
        if(age==40) {
            // Four direct writers against the protected exact body.
            guarded.setHealth(guardedHealth-6);
            guarded.setAbsorptionAmount(4);
            guardedAbsorbGranted=absorption(guarded)==4;
            // Observed before the write: this is the guard's own answer, independent of any later repair.
            drop=ProRuntime.preventFireWrite(guarded,200);
            // Reflection field write on the protected body and on the unprotected neighbour. The neighbour
            // result shows the mechanism itself works, so a refusal on the protected body is the guard acting.
            int beforeReflection=((Access.EntityState)guarded).pro$fire();
            java.lang.reflect.Field field;
            try { field=Entity.class.getDeclaredField("f_19831_"); }
            catch(NoSuchFieldException development) { field=Entity.class.getDeclaredField("remainingFireTicks"); }
            field.trySetAccessible();
            try { field.setInt(guarded,180);reflectionWriteGranted=true; }
            catch(ReflectiveOperationException|RuntimeException refused) { reflectionWriteRefused=true; }
            try { field.setInt(neighbour,180);neighbourReflectionWrite=true; }
            catch(ReflectiveOperationException|RuntimeException refused) { }
            guardedFire=((Access.EntityState)guarded).pro$fire();
            require(reflectionWriteRefused&&!reflectionWriteGranted,"reflection blocked before maintenance");
            require(neighbourReflectionWrite,"reflection permits unrelated neighbour");
            require(guardedFire==beforeReflection,"reflection cannot change current field");
            Class<?> unsafeClass=Class.forName("sun.misc.Unsafe");var access=unsafeClass.getDeclaredField("theUnsafe");access.setAccessible(true);Object unsafe=access.get(null);
            long offset=(Long)unsafeClass.getMethod("objectFieldOffset",java.lang.reflect.Field.class).invoke(unsafe,field);
            unsafeClass.getMethod("putInt",Object.class,long.class,int.class).invoke(unsafe,guarded,offset,250);
            require(((Access.EntityState)guarded).pro$fire()==beforeReflection,"Unsafe cannot change protected Entity field");
            unsafeClass.getMethod("putInt",Object.class,long.class,int.class).invoke(unsafe,neighbour,offset,250);
            require(((Access.EntityState)neighbour).pro$fire()==250,"Unsafe neighbour remains writable");
            require(agentFlag("unsafeFieldWriteGuarded").equals("true"),"Unsafe object writer guard installed");
            var data=((Access.EntityState)guarded).pro$data();
            var health=((Access.Data)data).pro$item(Access.Living.pro$healthKey());
            java.lang.reflect.Field healthValue;
            try { healthValue=net.minecraft.network.syncher.SynchedEntityData.DataItem.class.getDeclaredField("f_135391_"); }
            catch(NoSuchFieldException development) { healthValue=net.minecraft.network.syncher.SynchedEntityData.DataItem.class.getDeclaredField("value"); }
            healthValue.setAccessible(true);
            try { healthValue.set(health,null);throw new AssertionError("null health write escaped"); }
            catch(IllegalAccessException expected) { }
            require(health.getValue()!=null,"invalid health payload refused at writer");
            guardedMax=MaxHealthBase(guarded);
            setMaxHealth(guarded,Math.max(1,guardedMax-8));
            // The same four against the unprotected neighbour.
            neighbour.setHealth(neighbourHealth-6);
            neighbour.setAbsorptionAmount(4);
            neighbourAbsorbGranted=absorption(neighbour)==4;
            ((Access.EntityState)neighbour).pro$fire(200);
            neighbourFire=((Access.EntityState)neighbour).pro$fire();
            neighbourMax=MaxHealthBase(neighbour);
            setMaxHealth(neighbour,Math.max(1,neighbourMax-8));
            return;
        }
        if(age<45)return;
        if(age==45) {
            require(health(guarded)==guardedHealth,"protected health writer refused");
            require(guardedAbsorbGranted,"protected absorption increase allowed");
            require(MaxHealthBase(guarded)==guardedMax,"protected max-health writer refused");
            // The real method path is the guarded one: a protected body refuses it.
            int beforeMethod=((Access.EntityState)guarded).pro$fire();
            guarded.setRemainingFireTicks(150);
            require(((Access.EntityState)guarded).pro$fire()==beforeMethod,"protected fire method writer refused");
            require(health(neighbour)==neighbourHealth-6,"unprotected neighbour health changed normally");
            require(neighbourAbsorbGranted,"unprotected neighbour absorption write took effect");
            require(neighbourFire>0,"unprotected neighbour fire changed normally");
            require(MaxHealthBase(neighbour)==neighbourMax-8,"unprotected neighbour max health changed normally");
            // Direct field writes must be refused before the next maintenance tick.
            int beforeDirect=((Access.EntityState)guarded).pro$fire();
            ((Access.EntityState)guarded).pro$fire(200);
            fieldFireObserved=((Access.EntityState)guarded).pro$fire();
            require(fieldFireObserved==beforeDirect,"direct PUTFIELD blocked before maintenance");
            require(agentFlag("reflectionFieldWriteGuarded").equals("true"),"reflection installed in actual Agent");
            require(!agentString("dispatchState").endsWith(":TRANSFORMED:0"),"dispatch entries transformed");
            return;
        }
        if(age<50)return;
        if(age==50) {
            exerciseBackingSlots(level,runtime);
            // Check raw mutations before any maintenance tick has an opportunity to repair them.
            var lookup=lookup(level);
            lookup.pro$ids().remove(guarded.getId());
            lookup.pro$uuids().remove(guarded.getUUID());
            lookup.pro$ids().remove(neighbour.getId());
            lookup.pro$uuids().remove(neighbour.getUUID());
            require(lookup.pro$ids().get(guarded.getId())==guarded&&lookup.pro$uuids().get(guarded.getUUID())==guarded,"actual backing slots refused immediately");
            require(!lookup.pro$ids().containsKey(neighbour.getId())&&!lookup.pro$uuids().containsKey(neighbour.getUUID()),"actual neighbour deletions took effect immediately");
            backingIndexImmediate=true;
            return;
        }
        if(age<60)return;
        if(age==60) {
            // Maintenance is the fallback for a state change that reached no guarded writer at all.
            fieldFireReverted=((Access.EntityState)guarded).pro$fire()==0;
            var lookup=lookup(level);
            require(lookup.pro$ids().get(guarded.getId())==guarded,"protected id slot kept");
            require(lookup.pro$uuids().get(guarded.getUUID())==guarded,"protected uuid slot kept");
            require(lookup.pro$ids().get(neighbour.getId())==null,"unprotected id slot removable");
            require(lookup.pro$uuids().get(neighbour.getUUID())==null,"unprotected uuid slot removable");
            // Independent physical check: a body still held in the world is reported, not inferred.
            guardedHeld=heldInWorld(level,guarded);
            neighbourHeld=heldInWorld(level,neighbour);
            FixtureCommands.revoke(runtime,guarded);
            lookup.pro$ids().remove(guarded.getId());lookup.pro$uuids().remove(guarded.getUUID());
            require(!lookup.pro$ids().containsKey(guarded.getId())&&!lookup.pro$uuids().containsKey(guarded.getUUID()),"revocation immediately permits raw index removal");
            revokeImmediate=true;
            finish(level);
        }
    }

    @SuppressWarnings({"unchecked","rawtypes"})
    private void exerciseBackingSlots(ServerLevel level,ProRuntime runtime)throws Exception {
        require(ProRuntime.indexGuardState().startsWith("INSTALLED"),"real HashMap backing guard installed");
        var lookup=lookup(level);
        exerciseWrites((Map)lookup.pro$uuids(),guarded.getUUID(),guarded,neighbour.getUUID(),neighbour,runtime);
        exerciseWrites((Map)lookup.pro$ids(),guarded.getId(),guarded,neighbour.getId(),neighbour,runtime);
        exerciseMap((Map)lookup.pro$uuids(),guarded.getUUID(),guarded,neighbour.getUUID(),neighbour);
        exerciseMap((Map)lookup.pro$ids(),guarded.getId(),guarded,neighbour.getId(),neighbour);
        var tracked=((Access.Tracking)level.getChunkSource().chunkMap).pro$tracked();
        Object trackedTarget=tracked.get(guarded.getId()),trackedNeighbour=tracked.get(neighbour.getId());
        require(trackedTarget instanceof Access.Tracked&&trackedNeighbour instanceof Access.Tracked,"both actual tracking rows present");
        exerciseWrites((Map)tracked,guarded.getId(),trackedTarget,neighbour.getId(),trackedNeighbour,runtime);
        indexWritesImmediate=true;
        exerciseMap((Map)tracked,guarded.getId(),trackedTarget,neighbour.getId(),trackedNeighbour);trackingImmediate=true;
        var tickOwner=(Access.Ticks)((Access.Server)level).pro$ticks();
        require(tickOwner.pro$active().get(guarded.getId())==guarded,"protected body is actually ticking");
        Object oldActive=tickOwner.pro$active();
        ((net.minecraft.world.level.entity.EntityTickList)tickOwner).forEach(entity->{
            if(entity!=guarded)return;
            tickOwner.pro$ensureActiveIsNotIterated();
            require(tickOwner.pro$active()!=oldActive,"vanilla actually published a copied active tick map");
            tickOwner.pro$active().remove(guarded.getId());
            require(tickOwner.pro$active().get(guarded.getId())==guarded,"copied tick map guards active immediately");
        });
        tickCopyImmediate=tickOwner.pro$active()!=oldActive;
        require(tickCopyImmediate,"tick-copy callback ran");
        lookup.pro$uuids().remove(capturedAlias);
        require(lookup.pro$uuids().get(capturedAlias)==guarded,"captured old UUID alias protected by actual owner");aliasImmediate=true;
        var error=new java.util.concurrent.atomic.AtomicReference<Throwable>();
        Thread worker=new Thread(()-> {
            try {
                lookup.pro$ids().remove(guarded.getId());lookup.pro$uuids().remove(guarded.getUUID());
                require(lookup.pro$ids().get(guarded.getId())==guarded&&lookup.pro$uuids().get(guarded.getUUID())==guarded,"worker writer sees exact current policy");
            } catch(Throwable ex) { error.set(ex); }
        },"fixture-owned-index-writer");
        worker.start();worker.join(5000);require(!worker.isAlive(),"test writer really exited");
        if(error.get()!=null)throw new AssertionError("worker raw-map protection",error.get());workerIndexImmediate=true;
        // A reference to the same entity in an unrelated map must not inherit world-index authority.
        Map<Object,Object> foreign=new HashMap<>();foreign.put(guarded.getUUID(),guarded);
        require(foreign.remove(guarded.getUUID())==guarded&&foreign.isEmpty(),"unrelated map with same body remains writable");
        var unrelatedIds=new it.unimi.dsi.fastutil.ints.Int2ObjectLinkedOpenHashMap<Object>();unrelatedIds.put(guarded.getId(),guarded);
        require(unrelatedIds.remove(guarded.getId())==guarded,"unrelated fastutil map remains writable");
        mixedClearSelective=true;
    }
    private void exerciseWrites(Map<Object,Object> map,Object key,Object target,Object neighbourKey,Object other,ProRuntime runtime) {
        require(map.put(key,other)==target&&map.get(key)==target,"real index put refuses replacing protected value");
        require(map.replace(key,other)==target&&!map.replace(key,target,other)&&map.get(key)==target,"real index replace refuses and returns current result");
        int[] calls={0};
        require(map.compute(key,(k,v)->{calls[0]++;return other;})==target&&map.get(key)==target,"real compute refuses post-callback overwrite");
        require(map.computeIfPresent(key,(k,v)->{calls[0]++;return null;})==target&&map.get(key)==target,"real null compute cannot remove target");
        require(map.merge(key,other,(a,b)->{calls[0]++;return null;})==target&&map.get(key)==target,"real null merge retains target");
        require(calls[0]==3,"real remapping callbacks run once each");
        var entry=map.entrySet().stream().filter(e->e.getKey().equals(key)).findFirst().orElseThrow();
        require(entry.setValue(other)==target&&map.get(key)==target,"real entry value assignment guarded at store");
        map.replaceAll((k,v)->k.equals(key)?other:k.equals(neighbourKey)?null:v);
        require(map.get(key)==target&&map.containsKey(neighbourKey)&&map.get(neighbourKey)==null,"mixed replaceAll preserves target and writes neighbour");
        map.put(neighbourKey,other);
        map.putAll(Map.of(key,other,neighbourKey,other));
        require(map.get(key)==target&&map.get(neighbourKey)==other,"mixed putAll preserves exact current target");
        require(map.compute(key,(k,v)->{FixtureCommands.revoke(runtime,guarded);return other;})==other&&map.get(key)==other,"callback revocation permits actual replacement");
        map.put(key,target);
        require(map.compute(key,(k,v)->{FixtureCommands.protect(runtime,guarded);return other;})==target&&map.get(key)==target,"callback activation prevents following store immediately");
        Map<Object,Object> unrelated=new HashMap<>();unrelated.put(key,target);
        unrelated.entrySet().iterator().next().setValue(other);
        require(unrelated.get(key)==other,"foreign node with same key/body is not part of world index");
        if(map.getClass()==HashMap.class) {
            FixtureCommands.revoke(runtime,guarded);
            Object removed=map.remove(key);
            require(removed==target&&!map.containsKey(key),"revoke must release the exact HashMap slot before reinsertion");
            map.put(key,target);FixtureCommands.protect(runtime,guarded);
            entry.setValue(other);
            require(entry.getValue()==other&&map.get(key)==target,
                    "detached old HashMap node has no authority over replacement node: old="+(entry.getValue()==other)
                    +" live="+(map.get(key)==target)+" coreDenied="+ProRuntime.preventIndexSlotRemoval(entry,key,target)
                    +" status="+runtime.protectionStatus(guarded));
        }
        callbackPolicyImmediate=true;
    }
    private void exerciseMap(Map<Object,Object> map,Object protectedKey,Object protectedValue,Object neighbourKey,Object neighbourValue) {
        Map<Object,Object> baseline=new HashMap<>(map);
        boolean coreDenied=ProRuntime.preventIndexSlotRemoval(map,protectedKey,protectedValue);
        map.remove(protectedKey);require(map.get(protectedKey)==protectedValue,"raw remove blocked before maintenance: "+map.getClass().getName()+" coreDenied="+coreDenied);
        map.remove(protectedKey,protectedValue);require(map.get(protectedKey)==protectedValue,"conditional raw remove blocked");
        map.keySet().remove(protectedKey);require(map.get(protectedKey)==protectedValue,"key view remove blocked");
        map.values().remove(protectedValue);require(map.get(protectedKey)==protectedValue,"value view remove blocked physically");
        map.entrySet().remove(new AbstractMap.SimpleImmutableEntry<>(protectedKey,protectedValue));
        require(map.get(protectedKey)==protectedValue,"entry view remove blocked");
        var iterator=map.entrySet().iterator();int visited=0;
        while(iterator.hasNext()) {var entry=iterator.next();visited++;if(entry.getValue()==protectedValue)iterator.remove();}
        require(visited==baseline.size()&&map.get(protectedKey)==protectedValue,"refused iterator deletion preserves progress");
        map.values().removeIf(value->value==protectedValue||value==neighbourValue);
        require(map.get(protectedKey)==protectedValue&&!map.containsKey(neighbourKey),"mixed view batch removes only allowed neighbour");
        // Restore only the unrelated business record after the assertions. Never restore the protected row.
        map.put(neighbourKey,neighbourValue);
        map.clear();require(map.get(protectedKey)==protectedValue&&!map.containsKey(neighbourKey),"mixed clear preserves exact protected slot");
        for(var entry:baseline.entrySet())if(entry.getValue()!=protectedValue&&!map.containsKey(entry.getKey()))map.put(entry.getKey(),entry.getValue());
    }

    /** Independent of the registration maps: walk the real physical section members for this exact object. */
    private static boolean heldInWorld(ServerLevel level,Entity target) {
        var sections=(Access.Sections)((Access.Manager)((Access.Server)level).pro$manager()).pro$sections();
        for(var row:sections.pro$all().values()) {
            var group=(Access.Group)((Access.Section)row).pro$storage();
            for(Entity member:group.pro$all())if(member==target)return true;
        }
        return false;
    }

    private void finish(ServerLevel level) {
        finished=true;
        var lines=new ArrayList<String>();
        lines.add(failure==null?"GUARD_PROBE_PASS":"GUARD_PROBE_FAILED");
        if(failure!=null)lines.add(failure);
        lines.add("protected_health_unchanged="+(guarded!=null&&health(guarded)==guardedHealth));
        lines.add("protected_fire_method_denied=true");
        lines.add("guard_answered_fire="+drop);
        lines.add("field_write_observed="+fieldFireObserved);
        lines.add("agent_state_probe="+agentState());
        lines.add("reflection_field_guarded="+agentFlag("reflectionFieldWriteGuarded"));
        lines.add("unsafe_field_guarded="+agentFlag("unsafeFieldWriteGuarded"));
        lines.add("dispatch_state="+agentString("dispatchState"));
        lines.add("core_dispatch_state="+ProRuntime.dispatchState());
        lines.add("index_guard_state="+ProRuntime.indexGuardState());
        lines.add("backing_index_immediate="+backingIndexImmediate);
        lines.add("tracking_immediate="+trackingImmediate);
        lines.add("tick_copy_immediate="+tickCopyImmediate);
        lines.add("revoke_immediate="+revokeImmediate);
        lines.add("index_writes_immediate="+indexWritesImmediate);
        lines.add("callback_policy_immediate="+callbackPolicyImmediate);
        lines.add("worker_index_immediate="+workerIndexImmediate);
        lines.add("captured_uuid_alias_preserved="+aliasImmediate);
        lines.add("mixed_clear_preserves_protected_removes_neighbour="+mixedClearSelective);
        lines.add("reflection_write_refused="+reflectionWriteRefused);
        lines.add("reflection_write_granted="+reflectionWriteGranted);
        lines.add("neighbour_reflection_write_ok="+neighbourReflectionWrite);
        lines.add("field_write_reverted_by_maintenance="+fieldFireReverted);
        lines.add("protected_max_health_denied="+(guarded!=null&&MaxHealthBase(guarded)==guardedMax));
        lines.add("protected_absorption_increase_allowed="+guardedAbsorbGranted);
        lines.add("neighbour_health_changed="+(neighbour!=null&&health(neighbour)==neighbourHealth-6));
        lines.add("neighbour_fire_changed="+(neighbourFire>0));
        lines.add("neighbour_max_health_changed="+(neighbour!=null&&MaxHealthBase(neighbour)==neighbourMax-8));
        lines.add("protected_body_still_held_in_world="+guardedHeld);
        lines.add("neighbour_body_still_held_after_slot_removal="+neighbourHeld);
        try { Files.writeString(Path.of("guard-probe-result.txt"),String.join("\n",lines)); }
        catch(Exception unavailable) { /* the result file is evidence, not authority */ }
        System.out.println("PRO_GUARD_PROBE "+lines.get(0));
        try { level.getServer().halt(false); } catch(RuntimeException ignored) { }
    }

    private static Cow spawn(ServerLevel level,double x,String name) {
        StubbornCow cow=new StubbornCow(level);
        cow.setPos(x,100,4);cow.setNoAi(true);
        require(level.addFreshEntity(cow),"spawn "+name);
        return cow;
    }
    private static float health(LivingEntity entity) {
        return ((Access.Data)((Access.EntityState)entity).pro$data()).pro$item(Access.Living.pro$healthKey()).getValue();
    }
    private static float absorption(LivingEntity entity) { return entity.getAbsorptionAmount(); }
    private static double MaxHealth(LivingEntity entity) { return instance(entity).getValue(); }
    private static double MaxHealthBase(LivingEntity entity) { return instance(entity).getBaseValue(); }
    private static void setMaxHealth(LivingEntity entity,double value) { instance(entity).setBaseValue(value); }
    private static AttributeInstance instance(LivingEntity entity) {
        return ((Access.Living)entity).pro$attributes().getInstance(Attributes.MAX_HEALTH);
    }
    private static Access.Lookup lookup(ServerLevel level) {
        return (Access.Lookup)((Access.Manager)((Access.Server)level).pro$manager()).pro$lookup();
    }
    /** Agent state is read reflectively so the fixture never links against the agent at compile time. */
    private static String agentState() {
        try {
            Class<?> agent=Class.forName("dev.ronova.pro.agent.RecoveryAgent",false,ClassLoader.getSystemClassLoader());
            return String.valueOf(agent.getMethod("installState").invoke(null));
        } catch(Throwable unavailable) { return "AGENT_CLASS_UNAVAILABLE:"+unavailable.getClass().getSimpleName(); }
    }
    private static String agentString(String method) {
        try {
            Class<?> agent=Class.forName("dev.ronova.pro.agent.RecoveryAgent",false,ClassLoader.getSystemClassLoader());
            return String.valueOf(agent.getMethod(method).invoke(null));
        } catch(Throwable unavailable) { return "UNAVAILABLE"; }
    }
    private static String agentFlag(String method) {
        try {
            Class<?> agent=Class.forName("dev.ronova.pro.agent.RecoveryAgent",false,ClassLoader.getSystemClassLoader());
            return String.valueOf(agent.getMethod(method).invoke(null));
        } catch(Throwable unavailable) { return "UNAVAILABLE"; }
    }
    private static void require(boolean condition,String message) { if(!condition)throw new AssertionError(message); }
}
