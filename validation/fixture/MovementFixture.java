package dev.ronova.pro.validation;

import dev.ronova.pro.*;
import dev.ronova.pro.mixin.Access;
import java.nio.file.*;
import java.util.*;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.animal.Cow;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityEvent;
import net.minecraftforge.eventbus.api.EventPriority;

/** Real player plus ordinary entity moves. Checks immediate state before runtime maintenance. */
public final class MovementFixture {
    private int age,moves,entryAttacks;private boolean finished;
    private ServerPlayer player;private Cow cow,anchor,neighbour,target;private UUID clearing;
    private double x,y,z;private float health;
    public MovementFixture() {
        MinecraftForge.EVENT_BUS.addListener(EventPriority.HIGHEST,this::tick);
        MinecraftForge.EVENT_BUS.addListener(EventPriority.NORMAL,this::entered);
    }
    private void entered(EntityEvent.EnteringSection event) {
        if(finished||event.getEntity()!=cow||age<20)return;
        var section=((Access.Callback)((Access.EntityState)cow).pro$callback()).pro$section();
        // This is an ordinary mod callback during the move, not the vanilla removal itself.
        section.remove(cow);entryAttacks++;
        if(!((Access.Group)((Access.Section)section).pro$storage()).pro$all().contains(cow))throw new AssertionError("move event could remove newly published protected record");
    }
    private static void require(boolean good,String why) {if(!good)throw new AssertionError(why);}
    private static void intact(Entity entity) {
        var level=(ServerLevel)((Access.EntityState)entity).pro$level();
        var manager=(Access.Manager)((Access.Server)level).pro$manager();
        var lookup=(Access.Lookup)manager.pro$lookup();
        require(lookup.pro$ids().get(entity.getId())==entity&&lookup.pro$uuids().get(entity.getUUID())==entity,"lookup missing");
        int count=0;long actual=Long.MIN_VALUE;
        for(var entry:((Access.Sections)manager.pro$sections()).pro$all().long2ObjectEntrySet()) {
            var list=((Access.Group)((Access.Section)entry.getValue()).pro$storage()).pro$all();
            for(Object value:list)if(value==entity) {count++;actual=entry.getLongKey();}
        }
        require(count==1&&actual==SectionPos.asLong(entity.blockPosition()),"section mismatch/duplicate count="+count+" actual="+actual+" expected="+SectionPos.asLong(entity.blockPosition()));
        require(new EntityRecords(level,entity).registered(),"registration incomplete");
    }
    private static Cow spawn(ServerLevel level,double x,double y,double z) {
        Cow c=EntityType.COW.create(level);c.setNoAi(true);c.setNoGravity(true);c.moveTo(x,y,z,0,0);require(level.addFreshEntity(c),"spawn refused");return c;
    }
    private void tick(TickEvent.ServerTickEvent event) {
        if(finished||event.phase!=TickEvent.Phase.START)return;
        var server=event.getServer();if(server.getPlayerList().getPlayers().isEmpty())return;
        try {
            age++;var runtime=ProRuntime.get(server);var level=server.overworld();
            if(age==10) {
                player=server.getPlayerList().getPlayers().get(0);x=player.getX();y=player.getY()+4;z=player.getZ();
                player.setNoGravity(true);player.connection.teleport(x,y,z,0,0);health=player.getHealth();
                cow=spawn(level,x+2,y,z);anchor=spawn(level,x+2,y,z);neighbour=spawn(level,x+3,y,z);
                FixtureCommands.execute(server,player.createCommandSourceStack().withPermission(4),"protect @s");
                FixtureCommands.protect(runtime,cow);FixtureCommands.protect(runtime,anchor);intact(player);intact(cow);intact(anchor);
            }
            if(age>=20&&age<=60&&age%5==0) {
                if(age==20) {
                    var ticks=(Access.Ticks)((Access.Server)level).pro$ticks();var original=ticks.pro$active();
                    boolean[] copied={false};
                    ((net.minecraft.world.level.entity.EntityTickList)ticks).forEach(entity->{
                        if(entity==cow) {
                            ((net.minecraft.world.level.entity.EntityTickList)ticks).remove(neighbour);
                            require(ticks.pro$iterated()==original&&ticks.pro$active()!=original,"vanilla tick copy/swap blocked");
                            require(original.get(neighbour.getId())==neighbour&&ticks.pro$active().get(neighbour.getId())==null,"tick copy modified live iteration");copied[0]=true;
                        }
                    });
                    require(copied[0],"tick copy not exercised");require(ticks.pro$iterated()==null,"iteration marker did not release");
                    ((net.minecraft.world.level.entity.EntityTickList)ticks).add(neighbour);
                    var ids=((Access.Lookup)((Access.Manager)((Access.Server)level).pro$manager()).pro$lookup()).pro$ids();int size=ids.size();
                    var iterator=ids.int2ObjectEntrySet().iterator();boolean removed=false;
                    while(iterator.hasNext())if(iterator.next().getValue()==neighbour) {iterator.remove();removed=true;break;}
                    require(removed&&ids.size()==size-1&&ids.get(cow.getId())==cow&&ids.get(player.getId())==player,"mixed iterator removal corrupted linked map");
                    ids.put(neighbour.getId(),neighbour);intact(neighbour);
                }
                double next=x+(moves%2==0?20:-20);level.getChunk((int)Math.floor(next)>>4,(int)Math.floor(z)>>4);
                player.connection.teleport(next,y,z,0,0);intact(player);cow.setPos(next+2,y,z);moves++;
                intact(player);intact(cow);intact(anchor);
                var callback=((Access.EntityState)cow).pro$callback();callback.onRemove(Entity.RemovalReason.DISCARDED);
                var section=((Access.Callback)callback).pro$section();section.remove(cow);
                ((Access.Group)((Access.Section)section).pro$storage()).pro$all().remove(cow);
                for(Entity.RemovalReason reason:Entity.RemovalReason.values()) {cow.remove(reason);require(!cow.isRemoved(),"removal reason bypass: "+reason);}
                player.hurt(level.damageSources().generic(),9999);player.setHealth(0);
                require(!cow.isRemoved()&&player.getHealth()==health,"removal/health guard lost after movement");intact(cow);intact(player);
                if(age==20) {neighbour.remove(Entity.RemovalReason.DISCARDED);require(neighbour.isRemoved(),"unrelated removal blocked");}
            }
            if(age==65) {
                target=new Cow(EntityType.COW,level) {public void remove(RemovalReason reason) { }};
                target.setNoAi(true);target.moveTo(player.getX()+2,y,z,0,0);require(level.addFreshEntity(target),"clear target spawn");
                var before=new HashSet<>(runtime.operations());
                FixtureCommands.execute(server,player.createCommandSourceStack().withPermission(4),"clear "+target.getUUID());
                clearing=runtime.operations().stream().filter(id->!before.contains(id)&&runtime.query(id).action().equals("CLEAR")).findFirst().orElseThrow();
            }
            if(age>=130) {
                require(moves==9&&entryAttacks==9,"movement cases not exercised");intact(player);intact(cow);
                var result=runtime.query(clearing);require(result!=null&&result.complete(),"clear did not converge: "+result);
                var manager=(Access.Manager)((Access.Server)level).pro$manager();require(((Access.Lookup)manager.pro$lookup()).pro$ids().get(target.getId())==null,"clear lookup remains");
                FixtureCommands.revoke(runtime,cow);cow.remove(Entity.RemovalReason.DISCARDED);require(cow.isRemoved(),"revoke did not restore removal");
                finish("MOVEMENT_PASS player_and_cow_9_moves_unique_sections_tick_copy_linked_iterator_all_removal_reasons_immediate_guards_reentrant_callback_neighbour_clear_and_revoke");
            }
        } catch(Throwable failure) {failure.printStackTrace();finish("MOVEMENT_FAILED "+failure);}
    }
    private void finish(String result) {
        finished=true;System.out.println(result);
        try {Files.writeString(Path.of("movement-result.txt"),result);}catch(Exception ex){throw new IllegalStateException(ex);}
    }
}
