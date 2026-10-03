package dev.ronova.pro.mixin;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.players.PlayerList;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.RelativeMovement;
import net.minecraftforge.common.util.ITeleporter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import java.util.Set;

/** Marker mixins whose only purpose is to give SaveFinallyPlugin an exact target for lifecycle wrapping. */
public final class PlayerLifecycleHooks {
    private PlayerLifecycleHooks() { }

    @Mixin(PlayerList.class)
    public abstract static class Respawn {
        @Inject(method="respawn(Lnet/minecraft/server/level/ServerPlayer;Z)Lnet/minecraft/server/level/ServerPlayer;",at=@At("HEAD"),cancellable=true)
        private void ronova$respawn(ServerPlayer original,boolean alive,CallbackInfoReturnable<ServerPlayer> callback) {
            if(dev.ronova.pro.ProRuntime.preventForcedRespawn(original,alive))callback.setReturnValue(original);
        }
        @Inject(method="remove(Lnet/minecraft/server/level/ServerPlayer;)V",at=@At("HEAD"),cancellable=true)
        private void ronova$logout(ServerPlayer player,CallbackInfo callback) {
            if(dev.ronova.pro.ProRuntime.preventLiveLogout(player))callback.cancel();
        }
    }

    @Mixin(Entity.class)
    public abstract static class DimensionChange {
        @Inject(method="changeDimension(Lnet/minecraft/server/level/ServerLevel;)Lnet/minecraft/world/entity/Entity;",at=@At("HEAD"))
        private void ronova$changeDimension(ServerLevel destination,CallbackInfoReturnable<Entity> callback) { }
        @Inject(method="changeDimension(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraftforge/common/util/ITeleporter;)Lnet/minecraft/world/entity/Entity;",at=@At("HEAD"),remap=false)
        private void ronova$changeDimensionWithTeleporter(ServerLevel destination,ITeleporter teleporter,CallbackInfoReturnable<Entity> callback) { }
    }

    @Mixin(ServerPlayer.class)
    public abstract static class ServerPlayerTransfer {
        @Inject(method="changeDimension(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraftforge/common/util/ITeleporter;)Lnet/minecraft/world/entity/Entity;",at=@At("HEAD"),remap=false)
        private void ronova$changeDimensionWithTeleporter(ServerLevel destination,ITeleporter teleporter,CallbackInfoReturnable<Entity> callback) { }
        @Inject(method="teleportTo(Lnet/minecraft/server/level/ServerLevel;DDDLjava/util/Set;FF)Z",at=@At("HEAD"))
        private void ronova$teleportToSet(ServerLevel destination,double x,double y,double z,Set<RelativeMovement> relatives,
                float yaw,float pitch,CallbackInfoReturnable<Boolean> callback) { }
        @Inject(method="teleportTo(Lnet/minecraft/server/level/ServerLevel;DDDFF)V",at=@At("HEAD"))
        private void ronova$teleportTo(ServerLevel destination,double x,double y,double z,float yaw,float pitch,CallbackInfo callback) { }
    }
}
