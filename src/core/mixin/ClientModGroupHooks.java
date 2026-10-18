package dev.ronova.pro.mixin;

import dev.ronova.pro.ClientModGroups;
import java.util.*;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.*;
import net.minecraft.client.resources.sounds.*;
import net.minecraft.client.sounds.*;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.entity.*;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;

/** Actual client stores and publication boundaries for the selected whole Mod. */
public final class ClientModGroupHooks {
    private ClientModGroupHooks(){}
    @Mixin(ParticleEngine.class) public interface ParticlesAccess {
        @Accessor("particles") Map<ParticleRenderType,Queue<Particle>> pro$particles();
        @Accessor("particlesToAdd") Queue<Particle> pro$pending();
        @Accessor("trackingEmitters") Queue<TrackingEmitter> pro$emitters();
    }
    @Mixin(SoundManager.class) public interface SoundManagerAccess {@Accessor("soundEngine") SoundEngine pro$engine();}
    @Mixin(SoundEngine.class) public interface SoundAccess {
        @Accessor("instanceToChannel") Map<SoundInstance,ChannelAccess.ChannelHandle> pro$channels();
        @Accessor("queuedSounds") Map<SoundInstance,Integer> pro$queued();
        @Accessor("queuedTickableSounds") List<TickableSoundInstance> pro$ticking();
    }
    @Mixin(ClientLevel.class) public interface LevelAccess {
        @Accessor("tickingEntities") EntityTickList pro$ticks();
        @Accessor("players") List<net.minecraft.client.player.AbstractClientPlayer> pro$players();
        @Accessor(value="partEntities",remap=false) it.unimi.dsi.fastutil.ints.Int2ObjectMap<net.minecraftforge.entity.PartEntity<?>> pro$parts();
    }
    @Mixin(TransientEntitySectionManager.class) public interface StorageAccess {
        @Accessor("entityStorage") EntityLookup<Entity> pro$lookup();
        @Accessor("sectionStorage") EntitySectionStorage<Entity> pro$sections();
    }
    @Mixin(ParticleEngine.class) public abstract static class Particles {
        @Inject(method="add",at=@At("HEAD"),cancellable=true)
        private void admission(Particle value,CallbackInfo ci){if(ClientModGroups.stopped(value))ci.cancel();}
    }
    @Mixin(SoundEngine.class) public abstract static class Sounds {
        @Inject(method="play(Lnet/minecraft/client/resources/sounds/SoundInstance;)V",at=@At("HEAD"),cancellable=true)
        private void admission(SoundInstance value,CallbackInfo ci){if(ClientModGroups.stopped(value))ci.cancel();}
        @Inject(method="queueTickingSound",at=@At("HEAD"),cancellable=true)
        private void queued(TickableSoundInstance value,CallbackInfo ci){if(ClientModGroups.stopped(value))ci.cancel();}
    }
    @Mixin(net.minecraft.client.renderer.blockentity.BlockEntityRenderDispatcher.class) public abstract static class Blocks {
        @Inject(method="render",at=@At("HEAD"),cancellable=true)
        private void draw(BlockEntity entity,float partial,com.mojang.blaze3d.vertex.PoseStack pose,net.minecraft.client.renderer.MultiBufferSource buffers,CallbackInfo ci){
            var dispatcher=(net.minecraft.client.renderer.blockentity.BlockEntityRenderDispatcher)(Object)this;
            if(ClientModGroups.stopped(entity)||ClientModGroups.stopped(dispatcher.getRenderer(entity)))ci.cancel();
        }
    }
    @Mixin(net.minecraft.world.level.chunk.LevelChunk.class) public abstract static class BlockAdmission {
        @Inject(method="setBlockEntity",at=@At("HEAD"),cancellable=true)
        private void publish(BlockEntity entity,CallbackInfo ci){
            if(((Access.Chunk)this).pro$level() instanceof ClientLevel){
                var previous=((net.minecraft.world.level.chunk.LevelChunk)(Object)this).getBlockEntities().get(((Access.Block)entity).pro$pos());
                if(ClientModGroups.stopped(entity)||previous!=null&&previous!=entity&&ClientModGroups.protectedObject(previous))ci.cancel();
            }
        }
        @Inject(method="setBlockEntity",at=@At("RETURN"))
        private void observed(BlockEntity entity,CallbackInfo ci){if(((Access.Chunk)this).pro$level() instanceof ClientLevel)ClientModGroups.observed(entity);}
        @Inject(method="removeBlockEntity",at=@At("HEAD"),cancellable=true)
        private void remove(net.minecraft.core.BlockPos pos,CallbackInfo ci){
            var chunk=(net.minecraft.world.level.chunk.LevelChunk)(Object)this;
            if(((Access.Chunk)this).pro$level() instanceof ClientLevel&&ClientModGroups.protectedObject(chunk.getBlockEntities().get(pos)))ci.cancel();
        }
    }
    @Mixin(BlockEntity.class) public abstract static class BlockRemoval {
        @Inject(method="setRemoved",at=@At("HEAD"),cancellable=true)
        private void remove(CallbackInfo ci){if(((Access.Block)this).pro$level() instanceof ClientLevel&&ClientModGroups.protectedObject(this))ci.cancel();}
    }
    @Mixin(Entity.class) public abstract static class EntityRemoval {
        @Inject(method={"remove","setRemoved"},at=@At("HEAD"),cancellable=true)
        private void remove(Entity.RemovalReason reason,CallbackInfo ci){if(dev.ronova.pro.ClientPresence.groupRemovalDenied((Entity)(Object)this))ci.cancel();}
        @Inject(method={"discard","kill"},at=@At("HEAD"),cancellable=true)
        private void remove(CallbackInfo ci){if(dev.ronova.pro.ClientPresence.groupRemovalDenied((Entity)(Object)this))ci.cancel();}
    }
}
