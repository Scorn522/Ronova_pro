package dev.ronova.pro.mixin;

import dev.ronova.pro.ProRuntime;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.SynchedEntityData;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;

/** Entry guards for actual base/player lifecycle paths, before their damage/death side effects. */
public final class ProtectionHooks {
    private ProtectionHooks() { }
    @Mixin({LivingEntity.class,Player.class,ServerPlayer.class})
    public abstract static class Lifecycle {
        @Inject(method="hurt",at=@At("HEAD"),cancellable=true)
        private void damage(DamageSource source,float amount,CallbackInfoReturnable<Boolean> callback) {
            if(ProRuntime.preventDamage((Entity)(Object)this))callback.setReturnValue(false);
        }
        @Inject(method="die",at=@At("HEAD"),cancellable=true)
        private void death(DamageSource source,CallbackInfo callback) {
            if(ProRuntime.preventDamage((Entity)(Object)this))callback.cancel();
        }
    }
    @Mixin({LivingEntity.class,Player.class})
    public abstract static class Damage {
        @Inject(method="actuallyHurt",at=@At("HEAD"),cancellable=true)
        private void damage(DamageSource source,float amount,CallbackInfo callback) {
            if(ProRuntime.preventDamage((Entity)(Object)this))callback.cancel();
        }
    }
    @Mixin(LivingEntity.class)
    public abstract static class Health {
        @Inject(method="setHealth",at=@At("HEAD"),cancellable=true)
        private void health(float value,CallbackInfo callback) {
            if(ProRuntime.preventHealthLoss((LivingEntity)(Object)this,value))callback.cancel();
        }
    }
    @Mixin(SynchedEntityData.class)
    public abstract static class SyncedHealth {
        @Shadow @Final private Entity entity;
        @Inject(method="set(Lnet/minecraft/network/syncher/EntityDataAccessor;Ljava/lang/Object;Z)V",at=@At("HEAD"),cancellable=true)
        private <T> void health(EntityDataAccessor<T> key,T value,boolean force,CallbackInfo callback) {
            if(entity instanceof LivingEntity living&&key==Access.Living.pro$healthKey()&&value instanceof Float health
                    &&ProRuntime.preventHealthLoss(living,health))callback.cancel();
        }
    }
    @Mixin(net.minecraft.server.level.ServerLevel.class)
    public abstract static class PlayerMembership {
        @Inject(method="removePlayerImmediately",at=@At("HEAD"),cancellable=true)
        private void removePlayer(ServerPlayer player,Entity.RemovalReason reason,CallbackInfo callback) {
            if(ProRuntime.preventRemoval(player,reason))callback.cancel();
        }
    }
}
