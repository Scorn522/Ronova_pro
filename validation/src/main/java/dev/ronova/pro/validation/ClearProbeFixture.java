package dev.ronova.pro.validation;

import dev.ronova.pro.ProRuntime;
import dev.ronova.pro.mixin.Access;
import com.mojang.authlib.GameProfile;
import java.nio.file.*;
import java.util.*;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundRemoveEntitiesPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.server.network.ServerPlayerConnection;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.EventPriority;

/** Ordinary hostile entity behavior; only the durable-write fault uses an explicit fault injection. */
public final class ClearProbeFixture {
    private static final class ProbePacketListener extends ServerGamePacketListenerImpl {
        Runnable sendCallback;
        boolean failNext;
        int removalPackets,failedSends;
        ProbePacketListener(MinecraftServer server,FakePlayer player) {
            super(server,new Connection(PacketFlow.SERVERBOUND),player);
        }
        @Override public void send(Packet<?> packet) {
            if(packet instanceof ClientboundRemoveEntitiesPacket)removalPackets++;
            Runnable callback=sendCallback;sendCallback=null;
            if(callback!=null)callback.run();
            if(failNext) { failNext=false;failedSends++;throw new IllegalStateException("fixture tracked removal send failure"); }
        }
    }
    private static class StubbornCow extends Cow {
        boolean breakRelations, protectDuringRemoval;
        Runnable duringTick;
        StubbornCow(ServerLevel level) { super(EntityType.COW,level); }
        @Override public void remove(RemovalReason reason) { }
        @Override protected boolean canAddPassenger(Entity entity) { return true; }
        @Override public void stopRiding() {
            if(protectDuringRemoval) {
                protectDuringRemoval=false;
                ((Access.EntityState)this).pro$removal(null);
                FixtureCommands.protect(ProRuntime.get(((ServerLevel)level()).getServer()),this);
                throw new IllegalStateException("fixture protection changed during lifecycle");
            }
            if(breakRelations) {
                // An override may sever one side and throw before it updates the other endpoints.
                ((Access.EntityState)this).pro$vehicle(null);
                ((Access.EntityState)this).pro$passengers(com.google.common.collect.ImmutableList.of());
                throw new IllegalStateException("fixture partial lifecycle");
            }
            super.stopRiding();
        }
        @Override public void tick() {
            super.tick();
            Runnable action=duringTick;
            if(action!=null) { duringTick=null;action.run(); }
        }
    }

    private int age;
    private StubbornCow target,neighbour,vehicle,passenger,sibling,tickTarget,reprotected,undurable;
    private StubbornCow trackingFaultTarget,trackingReplaceTarget,trackingNeighbour;
    private Access.Tracked trackingNeighbourRow;
    private UUID operation,tickOperation,reprotectedOperation,undurableOperation,trackingFaultOperation,trackingReplaceOperation,trackingProbePlayerId;
    private boolean finished,immediate,tickEntered,tickImmediate,relationsDetached,revocationPreserved,hardFaultPreserved;
    private boolean trackedCallbackFaultCleared,trackedReplacementPreserved,tickPendingObserved;
    private String failure;
    private final List<String> trace=new ArrayList<>();
    private int neighbourSlotLostAt=-1;
    private final BlockPos blockPos=new BlockPos(-50,100,4);

    public ClearProbeFixture() { MinecraftForge.EVENT_BUS.addListener(EventPriority.LOWEST,this::tick); }

    private void tick(TickEvent.ServerTickEvent event) {
        if(finished||event.phase!=TickEvent.Phase.END)return;
        age++;
        try { step(event.getServer().overworld(),ProRuntime.get(event.getServer())); }
        catch(Throwable error) { failure=error.toString();finish(event.getServer().overworld()); }
    }

    private void step(ServerLevel level,ProRuntime runtime)throws Exception {
        if(age==20) {
            target=spawn(level,-60);neighbour=spawn(level,-56);
            vehicle=spawn(level,-61);passenger=spawn(level,-62);sibling=spawn(level,-63);
            require(target.startRiding(vehicle,true),"target rides vehicle");
            require(sibling.startRiding(vehicle,true),"sibling shares vehicle");
            require(passenger.startRiding(target,true),"target carries passenger");
            target.breakRelations=true;
            operation=FixtureCommands.clear(runtime,target);
            immediate=absent(level,target)&&((Access.EntityState)target).pro$removal()!=null;
            require(immediate,"request returns after physical body/indices clear");
            relationsDetached=((Access.EntityState)passenger).pro$vehicle()==null
                &&((Access.EntityState)vehicle).pro$passengers().stream().noneMatch(e->e==target)
                &&((Access.EntityState)sibling).pro$vehicle()==vehicle
                &&((Access.EntityState)vehicle).pro$passengers().stream().anyMatch(e->e==sibling);
            require(relationsDetached,"partial callback leaves no captured relation edges; sibling stays mounted");
            require(registered(level,neighbour),"neighbour remains registered before next maintenance");
            require(!runtime.query(operation).complete(),"no stable completion at request return");

            // Use native ChunkMap.TrackedEntity instances produced by normal entity registration.
            trackingFaultTarget=spawn(level,-59);trackingNeighbour=spawn(level,-58);trackingReplaceTarget=spawn(level,-57);
            Access.Tracked faultRow=trackedRow(level,trackingFaultTarget);
            Access.Tracked replacementRow=trackedRow(level,trackingReplaceTarget);
            trackingNeighbourRow=trackedRow(level,trackingNeighbour);
            FakePlayer fakePlayer=new FakePlayer(level,new GameProfile(UUID.randomUUID(),"RonovaClearProbe"));
            trackingProbePlayerId=fakePlayer.getUUID();
            ProbePacketListener listener=new ProbePacketListener(level.getServer(),fakePlayer);
            ServerPlayerConnection seenBy=new ServerPlayerConnection() {
                @Override public ServerPlayer getPlayer() { return fakePlayer; }
                @Override public void send(Packet<?> packet) { listener.send(packet); }
            };
            faultRow.pro$seen().add(seenBy);
            trackingFaultTarget.breakRelations=true;
            int faultPacketStart=listener.removalPackets;
            listener.failNext=true;
            trackingFaultOperation=FixtureCommands.clear(runtime,trackingFaultTarget);
            trackedCallbackFaultCleared=absent(level,trackingFaultTarget)
                &&listener.removalPackets==faultPacketStart+1&&listener.failedSends==1
                &&runtime.query(trackingFaultOperation)!=null&&!runtime.query(trackingFaultOperation).complete();
            require(trackedCallbackFaultCleared,"native tracked-row send failure still clears target records and stays unresolved");

            replacementRow.pro$seen().add(seenBy);
            trackingReplaceTarget.breakRelations=true;
            int replacedKey=((Access.EntityState)trackingReplaceTarget).pro$id();
            var tracked=tracking(level).pro$tracked();
            int replacementPacketStart=listener.removalPackets;
            listener.sendCallback=()->putTracked(level,replacedKey,trackingNeighbourRow);
            listener.failNext=true;
            trackingReplaceOperation=FixtureCommands.clear(runtime,trackingReplaceTarget);
            trackedReplacementPreserved=absent(level,trackingReplaceTarget)
                &&listener.removalPackets==replacementPacketStart+1&&listener.failedSends==2
                &&tracked.get(replacedKey)==trackingNeighbourRow&&trackingNeighbourRow.pro$entity()==trackingNeighbour
                &&registered(level,trackingNeighbour)&&runtime.query(trackingReplaceOperation)!=null
                &&!runtime.query(trackingReplaceOperation).complete();
            require(trackedReplacementPreserved,"native callback replacement survives exact target-row cleanup after send throws");

            reprotected=spawn(level,-52);reprotected.protectDuringRemoval=true;
            reprotectedOperation=FixtureCommands.clear(runtime,reprotected);
            revocationPreserved=registered(level,reprotected)&&((Access.EntityState)reprotected).pro$removal()==null
                &&runtime.protectionStatus(reprotected).startsWith("ACTIVE");
            require(revocationPreserved,"reentrant protection stops old clear before remaining writes");

            tickTarget=spawn(level,-48);
            tickTarget.duringTick=()->{
                try {
                    var ticks=(Access.Ticks)((Access.Server)level).pro$ticks();
                    tickEntered=ticks.pro$iterated()!=null;
                    require(tickEntered,"business callback runs inside actual tick iteration");
                    tickOperation=FixtureCommands.clear(runtime,tickTarget);
                    tickPendingObserved=pendingIteration(level,tickTarget);
                    tickImmediate=absent(level,tickTarget)&&tickPendingObserved&&((Access.EntityState)tickTarget).pro$removal()!=null;
                    require(tickImmediate,"clear inside entity tick removes active registrations immediately");
                } catch(Throwable error) { failure=error.toString(); }
            };
        }
        if(failure!=null) { finish(level);return; }
        if(age>=20&&age%5==0) {
            boolean present=registered(level,neighbour);
            trace.add(age+":"+(present?"registered":"MISSING"));
            if(!present&&neighbourSlotLostAt<0)neighbourSlotLostAt=age;
        }
        if(age==40) {
            require(tickEntered&&tickImmediate,"self-tick scenario executed");
            level.setBlock(blockPos,Blocks.CHEST.defaultBlockState(),3);
            Object block=level.getBlockEntity(blockPos);
            require(block!=null,"fault control block exists");
            var field=ProRuntime.class.getDeclaredField("journal");field.setAccessible(true);
            Object journal=field.get(runtime);
            var refuse=journal.getClass().getDeclaredMethod("refuseWrites",String.class);refuse.setAccessible(true);
            refuse.invoke(journal,"FIXTURE_DURABLE_WRITE_FAILURE");
            undurable=spawn(level,-44);undurableOperation=FixtureCommands.clear(runtime,undurable);
            require(absent(level,undurable),"durable-write failure does not block exact memory clear");
            FixtureCommands.protect(runtime,neighbour);
            require(runtime.protectionStatus(neighbour).startsWith("ACTIVE"),"durable-write failure does not delay protection");
            boolean rejected=false;
            try { runtime.clearBlock(level,blockPos); }
            catch(IllegalStateException expected) { rejected=true; }
            hardFaultPreserved=rejected&&level.getBlockEntity(blockPos)==block&&level.getBlockState(blockPos).is(Blocks.CHEST);
            require(hardFaultPreserved,"HARD world mutation preserves block when durable authority unavailable");
        }
        if(age==65) {
            require(immediate&&tickImmediate&&relationsDetached&&revocationPreserved&&hardFaultPreserved
                &&trackedCallbackFaultCleared&&trackedReplacementPreserved,"all scenarios ran");
            require(absent(level,target)&&absent(level,tickTarget)&&absent(level,undurable)
                &&absent(level,trackingFaultTarget)&&absent(level,trackingReplaceTarget),"cleared objects stay outside registrations");
            var tracked=tracking(level).pro$tracked();
            int replaceKey=((Access.EntityState)trackingReplaceTarget).pro$id();
            Object preserved=tracked.get(replaceKey);
            require(preserved==trackingNeighbourRow&&tracked.get(((Access.EntityState)trackingNeighbour).pro$id())==trackingNeighbourRow,
                "native neighbor row survives at its own id through cleanup");
            if(preserved==trackingNeighbourRow)tracked.remove(replaceKey);
            require(registered(level,neighbour)&&registered(level,vehicle)&&registered(level,passenger)&&registered(level,sibling),"unrelated bodies and exact neighbour edges survive");
            require(registered(level,reprotected)&&((Access.EntityState)reprotected).pro$removal()==null,"new protection survives subsequent ticks");
            require(runtime.query(undurableOperation)!=null&&!runtime.query(undurableOperation).complete(),"undurable work remains explicitly incomplete");
            require(runtime.query(reprotectedOperation)!=null&&!runtime.query(reprotectedOperation).complete(),"revoked old operation cannot complete");
            require(runtime.query(trackingFaultOperation)!=null&&!runtime.query(trackingFaultOperation).complete()
                &&runtime.query(trackingReplaceOperation)!=null&&!runtime.query(trackingReplaceOperation).complete(),
                "native tracking callback failures remain unresolved after later samples");
            require(trackingCallbackAndClientUnknown(runtime,trackingFaultOperation)
                &&trackingCallbackAndClientUnknown(runtime,trackingReplaceOperation),
                "tracking callback effect and absent real client readback remain explicit obligations");
            finish(level);
        }
    }

    private void finish(ServerLevel level) {
        finished=true;
        var lines=new ArrayList<String>();
        lines.add(failure==null?"CLEAR_PROBE_PASS":"CLEAR_PROBE_FAILED");
        if(failure!=null)lines.add(failure);
        lines.add("request_immediate="+immediate);
        lines.add("self_tick_entered="+tickEntered);
        lines.add("self_tick_immediate="+tickImmediate);
        lines.add("self_tick_pending_snapshot="+tickPendingObserved);
        lines.add("captured_relations_detached="+relationsDetached);
        lines.add("reentrant_protection_preserved="+revocationPreserved);
        lines.add("hard_write_failure_preserved="+hardFaultPreserved);
        lines.add("tracked_callback_fault_cleared="+trackedCallbackFaultCleared);
        lines.add("tracked_replacement_preserved="+trackedReplacementPreserved);
        lines.add("neighbour_slot_lost_at_tick="+neighbourSlotLostAt);
        lines.add("slot_trace="+String.join(",",trace));
        try { Files.writeString(Path.of("clear-probe-result.txt"),String.join("\n",lines)); }
        catch(Exception unavailable) { System.err.println("CLEAR_PROBE_RESULT_WRITE_FAILED:"+unavailable); }
        System.out.println("PRO_CLEAR_PROBE "+lines.get(0));
        level.getServer().halt(false);
    }

    private static Access.Lookup lookup(ServerLevel level) {
        return (Access.Lookup)((Access.Manager)((Access.Server)level).pro$manager()).pro$lookup();
    }
    private static boolean registered(ServerLevel level,Entity target) {
        return target!=null&&lookup(level).pro$uuids().get(((Access.EntityState)target).pro$uuid())==target;
    }
    private static boolean absent(ServerLevel level,Entity target) {
        var lookup=lookup(level);
        if(lookup.pro$ids().values().stream().anyMatch(e->e==target)||lookup.pro$uuids().values().stream().anyMatch(e->e==target))return false;
        var sections=(Access.Sections)((Access.Manager)((Access.Server)level).pro$manager()).pro$sections();
        for(var row:sections.pro$all().values()) {
            var group=(Access.Group)((Access.Section)row).pro$storage();
            if(group.pro$all().stream().anyMatch(e->e==target))return false;
            for(var list:group.pro$classes().values())if(list.stream().anyMatch(e->e==target))return false;
        }
        var ticks=(Access.Ticks)((Access.Server)level).pro$ticks();
        var iterated=ticks.pro$iterated();
        if(ticks.pro$active()!=iterated&&ticks.pro$active().values().stream().anyMatch(e->e==target))return false;
        if(ticks.pro$passive()!=iterated&&ticks.pro$passive().values().stream().anyMatch(e->e==target))return false;
        var tracked=tracking(level).pro$tracked();
        var manager=(Access.Manager)((Access.Server)level).pro$manager();
        return !manager.pro$known().contains(((Access.EntityState)target).pro$uuid())
            &&tracked.values().stream().noneMatch(value->value instanceof Access.Tracked row&&row.pro$entity()==target);
    }
    private static boolean pendingIteration(ServerLevel level,Entity target) {
        var iterated=((Access.Ticks)((Access.Server)level).pro$ticks()).pro$iterated();
        return iterated!=null&&iterated.values().stream().anyMatch(e->e==target);
    }
    private boolean trackingCallbackAndClientUnknown(ProRuntime runtime,UUID operation) {
        var status=runtime.query(operation);
        return status!=null&&!status.complete()
            &&"EFFECT_UNKNOWN_QUERY_ONLY".equals(status.obligations().get("LIFECYCLE"))
            &&"REQUIRED_UNKNOWN:CLIENT_DISCONNECTED".equals(status.obligations().get("CLIENT:"+trackingProbePlayerId));
    }
    private static Access.Tracking tracking(ServerLevel level) { return (Access.Tracking)level.getChunkSource().chunkMap; }
    @SuppressWarnings({"rawtypes","unchecked"})
    private static void putTracked(ServerLevel level,int key,Object row) { ((it.unimi.dsi.fastutil.ints.Int2ObjectMap)tracking(level).pro$tracked()).put(key,row); }
    private static Access.Tracked trackedRow(ServerLevel level,Entity entity) {
        Object row=tracking(level).pro$tracked().get(((Access.EntityState)entity).pro$id());
        require(row instanceof Access.Tracked tracked&&tracked.pro$entity()==entity,"normal ChunkMap registration exposes exact native tracking row");
        return (Access.Tracked)row;
    }
    private static StubbornCow spawn(ServerLevel level,double x) {
        StubbornCow cow=new StubbornCow(level);cow.setPos(x,100,4);cow.setNoAi(true);
        require(level.addFreshEntity(cow),"fixture spawn");return cow;
    }
    private static void require(boolean condition,String message) { if(!condition)throw new AssertionError(message); }
}
