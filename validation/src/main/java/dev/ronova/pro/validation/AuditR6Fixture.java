package dev.ronova.pro.validation;

import dev.ronova.pro.*;
import dev.ronova.pro.mixin.Access;
import java.nio.file.*;
import java.util.*;
import net.minecraft.server.level.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.level.Level;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityTravelToDimensionEvent;
import net.minecraftforge.eventbus.api.EventPriority;

/** Audit observations only. Deliberate mutations affect this isolated fixture's objects. */
public final class AuditR6Fixture {
    private int age,afterReturn=-1;private boolean done,armed,released;
    private ServerPlayer player;private Cow cow,unrelated;private UUID operation;
    private final List<String> facts=new ArrayList<>();
    static final net.minecraftforge.registries.DeferredRegister<net.minecraft.world.level.block.Block> BLOCKS=net.minecraftforge.registries.DeferredRegister.create(net.minecraftforge.registries.ForgeRegistries.BLOCKS,"pro_fixture");
    static final net.minecraftforge.registries.RegistryObject<net.minecraft.world.level.block.Block> REPLACING=BLOCKS.register("audit_replacing",()->new net.minecraft.world.level.block.FurnaceBlock(net.minecraft.world.level.block.state.BlockBehaviour.Properties.copy(net.minecraft.world.level.block.Blocks.FURNACE)) {
        @Override public void onRemove(net.minecraft.world.level.block.state.BlockState state,net.minecraft.world.level.Level level,net.minecraft.core.BlockPos pos,net.minecraft.world.level.block.state.BlockState next,boolean moving) {
            super.onRemove(state,level,pos,next,moving);
            if(next.isAir())level.setBlock(pos,net.minecraft.world.level.block.Blocks.FURNACE.defaultBlockState(),3);
        }
    });
    public AuditR6Fixture() {
        MinecraftForge.EVENT_BUS.addListener(EventPriority.HIGHEST,this::tick);
        MinecraftForge.EVENT_BUS.addListener(EventPriority.NORMAL,this::travel);
    }
    private void record(String key,Object value) {String line=key+"="+value;facts.add(line);System.out.println("R6_AUDIT "+line);}
    private void travel(EntityTravelToDimensionEvent event) {
        if(!armed||event.getEntity()!=player)return;
        var players=(Access.Players)player.server.getPlayerList();
        boolean removed=players.pro$players().remove(player);
        boolean uuid=players.pro$byUuid().remove(player.getUUID())==player;
        record("LIFECYCLE_CALLBACK_REMOVED_PLAYER",removed&&uuid);
        // Restore the fixture-owned scene and cancel this deliberate trip after observing the deletion.
        if(!players.pro$players().contains(player))players.pro$players().add(player);
        players.pro$byUuid().putIfAbsent(player.getUUID(),player);
        event.setCanceled(true);armed=false;
    }
    private void checkIterators() {
        var list=((Access.Players)player.server.getPlayerList()).pro$players();
        ServerPlayer neighbor=new ServerPlayer(player.server,player.serverLevel(),new com.mojang.authlib.GameProfile(UUID.randomUUID(),"IteratorNeighbor"));
        list.add(neighbor);var it=list.iterator();int visits=0;
        while(it.hasNext()&&visits<4) {it.next();visits++;it.remove();}
        record("ITERATOR_PROGRESS_AND_NEIGHBOR_REMOVAL",!it.hasNext()&&visits==2&&list.contains(player)&&!list.contains(neighbor));
        list.add(neighbor);var view=list.subList(0,list.size()).subList(0,list.size());int before=view.size();
        int at=view.indexOf(player);view.remove(at);
        boolean stable=view.size()==before&&view.contains(player)&&!view.remove((Object)player);
        var vi=view.listIterator();visits=0;while(vi.hasNext()&&visits<4){vi.next();visits++;vi.remove();}
        record("NESTED_VIEW_ITERATOR_PROGRESS",stable&&!vi.hasNext()&&visits==2&&view.size()==1&&list.size()==1);
        list.add(neighbor);var range=list.subList(0,list.size());range.clear();
        record("VIEW_CLEAR_PRESERVES_OWNER",range.size()==1&&list.size()==1&&list.get(0)==player);
    }
    private void checkBlockReplacement() {
        var level=player.serverLevel();var pos=player.blockPosition().offset(4,0,0);level.setBlock(pos,REPLACING.get().defaultBlockState(),3);
        BlockRecords record=new BlockRecords(level,pos);record.clear();
        var actual=level.getBlockEntity(pos);var ticker=((Access.Chunk)level.getChunkAt(pos)).pro$tickers().get(pos);
        record("CALLBACK_REPLACEMENT_TICKER_PRESERVED",actual!=null&&level.getBlockState(pos).is(net.minecraft.world.level.block.Blocks.FURNACE)&&ticker!=null);
        level.removeBlockEntity(pos);level.setBlock(pos,net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(),3);
    }
    private void tick(TickEvent.ServerTickEvent event) {
        if(done||event.phase!=TickEvent.Phase.START)return;
        var server=event.getServer();if(server.getPlayerList().getPlayers().isEmpty())return;
        try {
            age++;var runtime=ProRuntime.get(server);var level=server.overworld();
            if(age==10) {
                player=server.getPlayerList().getPlayers().get(0);FixtureCommands.protect(runtime,player);
                cow=EntityType.COW.create(level);cow.setNoAi(true);cow.moveTo(player.getX()+2,player.getY(),player.getZ(),0,0);level.addFreshEntity(cow);FixtureCommands.protect(runtime,cow);
                Files.writeString(Path.of("audit-target.txt"),cow.getId()+"\n"+cow.getUUID());
            }
            if(age==20) {
                int id=cow.getId();UUID uuid=cow.getUUID(),next=UUID.randomUUID();
                cow.setId(id+50000);cow.setUUID(next);
                record("PROTECTED_ID_UUID_CHANGED",cow.getId()!=id&&!cow.getUUID().equals(uuid));
                record("PROTECTION_DURING_IDENTITY_CHANGE",runtime.protectionStatus(cow));
                cow.setId(id);cow.setUUID(uuid);
                checkIterators();checkBlockReplacement();
                armed=true;player.changeDimension(server.getLevel(Level.NETHER));
                if(armed)throw new AssertionError("travel callback was not reached");
            }
            if(!released&&Files.exists(Path.of("../client/audit-client-ready.txt"))) {
                FixtureCommands.revoke(runtime,cow);released=true;
            }
            if(afterReturn<0&&Files.exists(Path.of("../client/audit-client-facts.txt"))) {
                record("COVERAGE_BEFORE_END_RETURN",runtime.observedSaveCopyLoadCovered());
                ServerPlayer old=player;player.wonGame=true;
                player.connection.handleClientCommand(new net.minecraft.network.protocol.game.ServerboundClientCommandPacket(net.minecraft.network.protocol.game.ServerboundClientCommandPacket.Action.PERFORM_RESPAWN));
                player=server.getPlayerList().getPlayer(old.getUUID());
                record("ACTUAL_END_RETURN_NEW_PLAYER",player!=old);
                record("COVERAGE_AFTER_END_RETURN",runtime.observedSaveCopyLoadCovered());
                Cow target=EntityType.COW.create(player.serverLevel());target.setNoAi(true);target.moveTo(player.getX()+2,player.getY(),player.getZ(),0,0);player.serverLevel().addFreshEntity(target);
                unrelated=EntityType.COW.create(player.serverLevel());unrelated.setNoAi(true);unrelated.moveTo(player.getX()+4,player.getY(),player.getZ(),0,0);player.serverLevel().addFreshEntity(unrelated);FixtureCommands.protect(runtime,unrelated);
                operation=FixtureCommands.clear(runtime,target);afterReturn=age;
            }
            if(afterReturn>=0&&age-afterReturn<200&&age%2==0) {
                Cow other=unrelated;java.util.concurrent.CompletableFuture.runAsync(()->other.getUUID());
            }
            if(afterReturn>=0&&age-afterReturn>=200) {
                record("LATER_BODY_COMPLETE",runtime.query(operation).complete());
                record("LATER_CHAIN_SETTLED",runtime.recoverySettled(operation));record("CHAIN_WITH_UNRELATED_TASK_CHURN",runtime.recoverySettled(operation));
                for(String row:runtime.recoveryStatus(operation))if(row.contains("GAP")||row.startsWith("CHAIN:"))record("LATER_CHAIN_DETAIL",row);
                var all=ProRuntime.class.getDeclaredField("work");all.setAccessible(true);
                Object item=((Map<?,?>)all.get(runtime)).get(operation);
                var recordsField=item.getClass().getDeclaredField("records");recordsField.setAccessible(true);Object records=recordsField.get(item);
                var releasedField=item.getClass().getDeclaredField("referencesReleased");releasedField.setAccessible(true);
                var callbackField=EntityRecords.class.getDeclaredField("capturedCallback");callbackField.setAccessible(true);Object callback=callbackField.get(records);
                record("CORE_MARKED_REFERENCES_RELEASED",releasedField.getBoolean(item));
                record("RELEASED_RECORDS_STILL_HOLD_ENTITY",callback instanceof Access.Callback c&&c.pro$entity()!=null);
                facts.add(Files.readString(Path.of("../client/audit-client-facts.txt")));
                finish("AUDIT_FINISHED\n"+String.join("\n",facts));
            }
            if(age>2400)throw new AssertionError("audit timeout");
        } catch(Throwable failure) {failure.printStackTrace();finish("AUDIT_FAILED "+failure+"\n"+String.join("\n",facts));}
    }
    private void finish(String result) {done=true;try {Files.writeString(Path.of("audit-result.txt"),result);}catch(Exception ex){throw new IllegalStateException(ex);}System.out.println(result);}
}
