package dev.ronova.pro.mixin;

import dev.ronova.pro.ProRuntime;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;

/** Tick effects are selected by actual level and coordinates; connection/control ticks remain active. */
public final class WorldControlHooks {
    private WorldControlHooks(){}
    @Mixin(ServerLevel.class) public abstract static class World {
        @Inject(method="tickNonPassenger",at=@At("HEAD"),cancellable=true)
        private void entity(Entity entity,CallbackInfo callback){if(ProRuntime.worldFrozen(entity))callback.cancel();}
        @Inject(method="tickPassenger",at=@At("HEAD"),cancellable=true)
        private void passenger(Entity vehicle,Entity passenger,CallbackInfo callback){if(ProRuntime.worldFrozen(passenger))callback.cancel();}
        @Inject(method={"tickTime","advanceWeatherCycle"},at=@At("HEAD"),cancellable=true)
        private void clock(CallbackInfo callback){if(ProRuntime.worldClockFrozen((ServerLevel)(Object)this))callback.cancel();}
        @Inject(method="addFreshEntity",at=@At("HEAD"),cancellable=true)
        private void spawn(Entity entity,CallbackInfoReturnable<Boolean> callback){if(ProRuntime.worldSpawnDenied(entity))callback.setReturnValue(false);}
        // The resolved lightning target can be in another chunk; do not gate the whole input chunk.
        @Redirect(method="tickChunk",at=@At(value="INVOKE",target="Lnet/minecraft/server/level/ServerLevel;isRainingAt(Lnet/minecraft/core/BlockPos;)Z"))
        private boolean lightning(ServerLevel level,BlockPos pos){return !ProRuntime.worldFrozen(level,pos)&&level.isRainingAt(pos);}
        @Redirect(method="tickChunk",at=@At(value="INVOKE",target="Lnet/minecraft/world/level/biome/Biome;shouldFreeze(Lnet/minecraft/world/level/LevelReader;Lnet/minecraft/core/BlockPos;)Z"))
        private boolean ice(net.minecraft.world.level.biome.Biome biome,net.minecraft.world.level.LevelReader reader,BlockPos pos){
            return !ProRuntime.worldFrozen((ServerLevel)(Object)this,pos)&&biome.shouldFreeze(reader,pos);
        }
        // Snow raises entities before the block write, so the decision itself is the effect boundary.
        @Redirect(method="tickChunk",at=@At(value="INVOKE",target="Lnet/minecraft/world/level/biome/Biome;shouldSnow(Lnet/minecraft/world/level/LevelReader;Lnet/minecraft/core/BlockPos;)Z"))
        private boolean snow(net.minecraft.world.level.biome.Biome biome,net.minecraft.world.level.LevelReader reader,BlockPos pos){
            return !ProRuntime.worldFrozen((ServerLevel)(Object)this,pos)&&biome.shouldSnow(reader,pos);
        }
        @Redirect(method="tickChunk",at=@At(value="INVOKE",target="Lnet/minecraft/world/level/block/Block;handlePrecipitation(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/biome/Biome$Precipitation;)V"))
        private void precipitation(net.minecraft.world.level.block.Block block,net.minecraft.world.level.block.state.BlockState state,Level level,BlockPos pos,net.minecraft.world.level.biome.Biome.Precipitation precipitation){
            if(!ProRuntime.worldFrozen(level,pos))block.handlePrecipitation(state,level,pos,precipitation);
        }
        @Redirect(method="tickChunk",at=@At(value="INVOKE",target="Lnet/minecraft/world/level/block/state/BlockState;randomTick(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/core/BlockPos;Lnet/minecraft/util/RandomSource;)V"))
        private void randomBlock(net.minecraft.world.level.block.state.BlockState state,ServerLevel level,BlockPos pos,net.minecraft.util.RandomSource random){if(!ProRuntime.worldFrozen(level,pos))state.randomTick(level,pos,random);}
        @Redirect(method="tickChunk",at=@At(value="INVOKE",target="Lnet/minecraft/world/level/material/FluidState;randomTick(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/util/RandomSource;)V"))
        private void randomFluid(net.minecraft.world.level.material.FluidState state,Level level,BlockPos pos,net.minecraft.util.RandomSource random){if(!ProRuntime.worldFrozen(level,pos))state.randomTick(level,pos,random);}
    }
    @Mixin(targets="net.minecraft.world.level.chunk.LevelChunk$BoundTickingBlockEntity") public abstract static class Block {
        @Shadow @Final private net.minecraft.world.level.block.entity.BlockEntity blockEntity;
        @Inject(method="tick",at=@At("HEAD"),cancellable=true)
        private void tick(CallbackInfo callback){if(ProRuntime.worldFrozen(blockEntity.getLevel(),blockEntity.getBlockPos()))callback.cancel();}
    }
    @Mixin(net.minecraft.world.ticks.LevelTicks.class) public abstract static class Scheduled implements ProRuntime.WorldTickQueue {
        @Shadow @Final private it.unimi.dsi.fastutil.longs.Long2ObjectMap<net.minecraft.world.ticks.LevelChunkTicks<Object>> allContainers;
        @Shadow @Final private it.unimi.dsi.fastutil.longs.Long2LongMap nextTickForContainer;
        @Inject(method="scheduleForThisTick",at=@At("HEAD"),cancellable=true)
        private void defer(net.minecraft.world.ticks.ScheduledTick<?> tick,CallbackInfo callback){if(ProRuntime.deferWorldTick(this,tick))callback.cancel();}
        @Inject(method="schedule",at=@At("HEAD"),cancellable=true)
        private void duplicate(net.minecraft.world.ticks.ScheduledTick<?> tick,CallbackInfo callback){if(ProRuntime.worldTickHeld(this,tick.pos(),tick.type()))callback.cancel();}
        @Inject(method="hasScheduledTick",at=@At("HEAD"),cancellable=true)
        private void reserved(BlockPos pos,Object type,CallbackInfoReturnable<Boolean> callback){if(ProRuntime.worldTickHeld(this,pos,type))callback.setReturnValue(true);}
        @Override @SuppressWarnings("unchecked") public boolean pro$restoreWorldTick(net.minecraft.world.ticks.ScheduledTick<?> original){
            ProRuntime.requireWorldTickRestore(this,original);
            long chunkPos=net.minecraft.world.level.ChunkPos.asLong(original.pos());
            net.minecraft.world.ticks.LevelChunkTicks<Object> chunk=allContainers.get(chunkPos);if(chunk==null)return false;
            chunk.removeIf(tick->tick.type()==original.type()&&tick.pos().equals(original.pos()));
            // A disk-loaded container can still hold SavedTick reservations until vanilla unpack.
            if(chunk.hasScheduledTick(original.pos(),original.type()))return false;
            chunk.schedule((net.minecraft.world.ticks.ScheduledTick<Object>)original);
            net.minecraft.world.ticks.ScheduledTick<Object> first=chunk.peek();
            if(first==null)nextTickForContainer.remove(chunkPos);else nextTickForContainer.put(chunkPos,first.triggerTick());
            return true;
        }
    }
    @Mixin(net.minecraft.world.entity.raid.Raid.class) public abstract static class RaidTick {
        @Inject(method="tick",at=@At("HEAD"),cancellable=true)
        private void tick(CallbackInfo callback){net.minecraft.world.entity.raid.Raid raid=(net.minecraft.world.entity.raid.Raid)(Object)this;
            if(ProRuntime.worldFrozen(raid.getLevel(),raid.getCenter()))callback.cancel();
        }
        @Inject(method="tick",at=@At(value="INVOKE",target="Lnet/minecraft/world/entity/raid/Raid;moveRaidCenterToNearbyVillageSection()V",shift=At.Shift.AFTER),cancellable=true)
        private void movedCenter(CallbackInfo callback){net.minecraft.world.entity.raid.Raid raid=(net.minecraft.world.entity.raid.Raid)(Object)this;
            if(ProRuntime.worldFrozen(raid.getLevel(),raid.getCenter()))callback.cancel();
        }
    }
    @Mixin(net.minecraft.world.entity.raid.Raids.class) public abstract static class RaidManagement {
        @Redirect(method="tick",at=@At(value="INVOKE",target="Lnet/minecraft/world/entity/raid/Raid;stop()V"))
        private void disabled(net.minecraft.world.entity.raid.Raid raid){if(!ProRuntime.worldFrozen(raid.getLevel(),raid.getCenter()))raid.stop();}
        // This is the calculated village center (slot 4 in 1.20.1), before ID allocation,
        // publication, bad-omen consumption, packets and trigger awards.
        @Inject(method="createOrExtendRaid",at=@At(value="INVOKE",target="Lnet/minecraft/world/entity/raid/Raids;getOrCreateRaid(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/entity/raid/Raid;"),locals=LocalCapture.CAPTURE_FAILHARD,cancellable=true)
        private void start(net.minecraft.server.level.ServerPlayer player,CallbackInfoReturnable<net.minecraft.world.entity.raid.Raid> callback,
                           net.minecraft.world.level.dimension.DimensionType dimension,BlockPos playerPos,BlockPos center){
            ServerLevel level=player.serverLevel();net.minecraft.world.entity.raid.Raid existing=level.getRaidAt(center);
            if(existing==null?ProRuntime.worldFrozen(level,center):ProRuntime.worldFrozen(existing.getLevel(),existing.getCenter()))callback.setReturnValue(null);
        }
    }
    @Mixin(net.minecraft.world.entity.LightningBolt.class) public abstract static class Lightning {
        @Redirect(method="spawnFire",at=@At(value="INVOKE",target="Lnet/minecraft/world/level/block/state/BlockState;canSurvive(Lnet/minecraft/world/level/LevelReader;Lnet/minecraft/core/BlockPos;)Z"))
        private boolean fire(net.minecraft.world.level.block.state.BlockState state,net.minecraft.world.level.LevelReader reader,BlockPos pos){
            return !ProRuntime.worldFrozen(((Entity)(Object)this).level(),pos)&&state.canSurvive(reader,pos);
        }
        @Redirect(method="tick",at=@At(value="INVOKE",target="Lnet/minecraft/world/level/Level;getEntities(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/phys/AABB;Ljava/util/function/Predicate;)Ljava/util/List;"))
        private java.util.List<Entity> targets(Level level,Entity source,net.minecraft.world.phys.AABB box,java.util.function.Predicate<? super Entity> predicate){
            return level.getEntities(source,box,entity->!ProRuntime.worldFrozen(entity)&&predicate.test(entity));
        }
        @Redirect(method="clearCopperOnLightningStrike",at=@At(value="INVOKE",target="Lnet/minecraft/world/level/Level;setBlockAndUpdate(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;)Z"))
        private static boolean copper(Level level,BlockPos pos,net.minecraft.world.level.block.state.BlockState state){return !ProRuntime.worldFrozen(level,pos)&&level.setBlockAndUpdate(pos,state);}
        @Redirect(method="randomStepCleaningCopper",at=@At(value="INVOKE",target="Lnet/minecraft/world/level/Level;getBlockState(Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/level/block/state/BlockState;"))
        private static net.minecraft.world.level.block.state.BlockState copperStep(Level level,BlockPos pos){
            // Skip this candidate before the lambda write, level event and next walk position.
            return ProRuntime.worldFrozen(level,pos)?net.minecraft.world.level.block.Blocks.AIR.defaultBlockState():level.getBlockState(pos);
        }
    }
    @Mixin(net.minecraft.world.level.block.LightningRodBlock.class) public abstract static class LightningRod {
        @Inject(method="onLightningStrike",at=@At("HEAD"),cancellable=true)
        private void strike(net.minecraft.world.level.block.state.BlockState state,Level level,BlockPos pos,CallbackInfo callback){if(ProRuntime.worldFrozen(level,pos))callback.cancel();}
    }
    @Mixin(Entity.class) public abstract static class Motion {
        @Inject(method="setPosRaw",at=@At("HEAD"),cancellable=true)
        private void position(double x,double y,double z,CallbackInfo callback){if(ProRuntime.worldFrozen((Entity)(Object)this))callback.cancel();}
        @Inject(method="move",at=@At("HEAD"),cancellable=true)
        private void movement(net.minecraft.world.entity.MoverType type,net.minecraft.world.phys.Vec3 motion,CallbackInfo callback){if(ProRuntime.worldFrozen((Entity)(Object)this))callback.cancel();}
    }
    @Mixin(net.minecraft.server.level.ServerPlayer.class) public abstract static class PlayerTick {
        @Inject(method="doTick",at=@At("HEAD"),cancellable=true)
        private void tick(CallbackInfo callback){if(ProRuntime.worldFrozen((Entity)(Object)this))callback.cancel();}
    }
    @Mixin(net.minecraft.server.network.ServerGamePacketListenerImpl.class) public abstract static class PlayerMovement {
        @Shadow public net.minecraft.server.level.ServerPlayer player;
        @Inject(method="handleMovePlayer",at=@At(value="INVOKE",target="Lnet/minecraft/network/protocol/PacketUtils;ensureRunningOnSameThread(Lnet/minecraft/network/protocol/Packet;Lnet/minecraft/network/PacketListener;Lnet/minecraft/server/level/ServerLevel;)V",shift=At.Shift.AFTER),cancellable=true)
        private void player(net.minecraft.network.protocol.game.ServerboundMovePlayerPacket packet,CallbackInfo callback){
            if(ProRuntime.worldFrozen(player)){player.connection.teleport(player.getX(),player.getY(),player.getZ(),player.getYRot(),player.getXRot());callback.cancel();}
        }
        @Inject(method="handleMoveVehicle",at=@At(value="INVOKE",target="Lnet/minecraft/network/protocol/PacketUtils;ensureRunningOnSameThread(Lnet/minecraft/network/protocol/Packet;Lnet/minecraft/network/PacketListener;Lnet/minecraft/server/level/ServerLevel;)V",shift=At.Shift.AFTER),cancellable=true)
        private void vehicle(net.minecraft.network.protocol.game.ServerboundMoveVehiclePacket packet,CallbackInfo callback){
            Entity vehicle=player.getRootVehicle();if(vehicle!=player&&ProRuntime.worldFrozen(vehicle)){
                player.connection.send(new net.minecraft.network.protocol.game.ClientboundMoveVehiclePacket(vehicle));callback.cancel();
            }
        }
    }
}
