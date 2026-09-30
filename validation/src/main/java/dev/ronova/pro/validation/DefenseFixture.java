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
public final class DefenseFixture {
    private int age,moves,entryAttacks;private boolean finished;
    private ServerPlayer player;private Cow cow,anchor,neighbour,target;private UUID clearing;
    private double x,y,z;private float health;private UUID protection;private int handedOff=-1;
    public DefenseFixture() {
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
    private static final class CounterCow extends Cow {
        int hurtCalls,deathCalls,removeCalls;boolean spoof;
        @Override public float getHealth(){return spoof?-262:super.getHealth();}
        @Override public boolean isAlive(){return spoof?false:super.isAlive();}
        @Override public boolean isDeadOrDying(){return spoof?true:super.isDeadOrDying();}
        CounterCow(ServerLevel level) {super(EntityType.COW,level);}
        @Override public boolean hurt(net.minecraft.world.damagesource.DamageSource source,float amount) {hurtCalls++;return true;}
        @Override public void die(net.minecraft.world.damagesource.DamageSource source) {deathCalls++;}
        @Override public void remove(RemovalReason reason) {removeCalls++;super.remove(reason);}
    }
    private static Cow spawn(ServerLevel level,double x,double y,double z) {
        Cow c=new CounterCow(level);c.setNoAi(true);c.setNoGravity(true);c.moveTo(x,y,z,0,0);require(level.addFreshEntity(c),"spawn refused");return c;
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
                protection=FixtureCommands.protect(runtime,player);FixtureCommands.protect(runtime,cow);FixtureCommands.protect(runtime,anchor);intact(player);intact(cow);intact(anchor);
                var owners=ProRuntime.class.getDeclaredField("SYNC_OWNERS");owners.setAccessible(true);
                Map<?,?> cache=(Map<?,?>)owners.get(null);
                synchronized(cache) {require(!cache.isEmpty()&&cache.values().stream().allMatch(value->value instanceof java.lang.ref.WeakReference<?>),"owner observation cache retains entity strongly");}
                System.out.println("SYNC_OWNER_CACHE_WEAK_VALUES_PASS real_protection_cache");
                Files.writeString(Path.of("defense-target.txt"),cow.getId()+"\n"+cow.getUUID());
            }
            if(age>=20&&age<=60&&age%5==0) {
                if(age==20) {
                    CounterCow guarded=(CounterCow)cow;guarded.spoof=true;
                    LifeSpoof.set(player,-282);LifeSpoof.set(anchor,-282);LifeSpoof.set(neighbour,-282);
                    require(cow.getHealth()>0&&cow.isAlive()&&!cow.isDeadOrDying(),"protected no-super life getter spoof escaped");
                    require(anchor.getHealth()>0&&anchor.isAlive()&&!anchor.isDeadOrDying(),"protected merged life getter spoof escaped");
                    require(player.getHealth()==health&&player.isAlive()&&!player.isDeadOrDying(),"protected player effective life spoof escaped");
                    require(neighbour.getHealth()==-262&&!neighbour.isAlive()&&neighbour.isDeadOrDying(),"unrelated life getter modified");
                    System.out.println("EFFECTIVE_LIFE_SERVER_PASS no_super_and_merged_getters_neighbour_unchanged");
                    guarded.hurt(level.damageSources().generic(),9999);guarded.die(level.damageSources().generic());
                    require(guarded.hurtCalls==0&&guarded.deathCalls==0,"overridden damage/death body executed");
                    CounterCow normal=(CounterCow)neighbour;normal.hurt(level.damageSources().generic(),1);normal.die(level.damageSources().generic());
                    require(normal.hurtCalls==1&&normal.deathCalls==1,"unprotected override suppressed");
                    var players=(Access.Players)server.getPlayerList();
                    players.pro$players().remove(player);players.pro$byUuid().remove(player.getUUID());((Access.Server)level).pro$players().remove(player);
                    require(players.pro$players().contains(player)&&players.pro$byUuid().get(player.getUUID())==player&&level.players().contains(player),"protected player registries deleted");
                    level.removePlayerImmediately(player,Entity.RemovalReason.DISCARDED);server.getPlayerList().remove(player);
                    require(server.getPlayerList().getPlayer(player.getUUID())==player&&level.players().contains(player),"live player logout/removal allowed");
                    require(server.getPlayerList().respawn(player,false)==player&&server.getPlayerList().respawn(player,true)==player,"direct forced respawn replaced protected player");
                    require(runtime.operationSummary(protection).startsWith("防护策略已启用"),"forced respawn retired protection");intact(player);
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
            if(age>1800)throw new AssertionError("defense client timeout");
            if(age>=130&&handedOff<0&&Files.isRegularFile(Path.of("../client/defense-client-checked.txt"))
                    &&runtime.query(clearing)!=null&&runtime.query(clearing).complete()) {
                require(Files.readString(Path.of("../client/defense-client-checked.txt")).startsWith("CLIENT_DEFENSE_PASS"),"client defense failed");
                require(moves==9&&entryAttacks==9,"movement cases not exercised");intact(player);intact(cow);
                var result=runtime.query(clearing);require(result!=null&&result.complete(),"clear did not converge: "+result);
                var manager=(Access.Manager)((Access.Server)level).pro$manager();require(((Access.Lookup)manager.pro$lookup()).pro$ids().get(target.getId())==null,"clear lookup remains");
                FixtureCommands.revoke(runtime,cow);require(cow.getHealth()==-262&&!cow.isAlive()&&cow.isDeadOrDying(),"revoke still forces effective life");cow.remove(Entity.RemovalReason.DISCARDED);require(cow.isRemoved(),"revoke did not restore removal");
                ServerPlayer old=player;player.wonGame=true;
                player.connection.handleClientCommand(new net.minecraft.network.protocol.game.ServerboundClientCommandPacket(net.minecraft.network.protocol.game.ServerboundClientCommandPacket.Action.PERFORM_RESPAWN));
                player=server.getPlayerList().getPlayer(old.getUUID());
                require(player!=old&&player.connection.player==player,"legitimate end-return did not hand off player");
                require(runtime.protectionStatus(player).startsWith("ACTIVE"),"end-return lost protection");
                require(runtime.operationSummary(protection).startsWith("防护策略已启用"),"old operation falsely reports inactive after handoff");
                handedOff=age;Files.writeString(Path.of("defense-handoff.txt"),player.getId()+"\n"+player.getUUID());
            }
            if(handedOff>=0&&age-handedOff>30&&Files.isRegularFile(Path.of("../client/defense-handoff-checked.txt"))) {
                require(Files.readString(Path.of("../client/defense-handoff-checked.txt")).equals("HANDOFF_PASS"),"client handoff failed");
                player.hurt(player.damageSources().generic(),99999);require(player.getHealth()>0&&!player.isRemoved(),"protection lost after handoff");
                finish("DEFENSE_PASS client_remove_duplicate_revoke_override_bodies_player_registries_forced_respawn_end_return_movement_clear_and_neighbours");
            }
        } catch(Throwable failure) {failure.printStackTrace();finish("DEFENSE_FAILED "+failure);}
    }
    private void finish(String result) {
        finished=true;System.out.println(result);
        try {Files.writeString(Path.of("defense-result.txt"),result);}catch(Exception ex){throw new IllegalStateException(ex);}
    }
}
