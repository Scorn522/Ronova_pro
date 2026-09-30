package dev.ronova.pro.mixin;

import dev.ronova.pro.ProRuntime;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Guards at the actual state writers instead of the public API head. Each guard decides only for the exact
 * object it is handed: an unprotected neighbour, a legal increase and an ordinary desync all pass unchanged.
 * A refusal happens before the value is stored, so no repair pass is needed to undo it.
 */
public final class WriterHooks {
    private WriterHooks() { }

    @Mixin({LivingEntity.class,Player.class})
    public abstract static class Absorption {
        @Inject(method="setAbsorptionAmount",at=@At("HEAD"),cancellable=true)
        private void absorption(float value,CallbackInfo callback) {
            if(ProRuntime.preventAbsorptionLoss((LivingEntity)(Object)this,value))callback.cancel();
        }
    }

    @Mixin({Entity.class,Player.class})
    public abstract static class Fire {
        @Inject(method="setRemainingFireTicks",at=@At("HEAD"),cancellable=true)
        private void fire(int ticks,CallbackInfo callback) {
            if(ProRuntime.preventFireWrite((Entity)(Object)this,ticks))callback.cancel();
        }
    }

    /**
     * The fire field reached through an accessor write is a direct field write: it never enters the method above,
     * so no method-level guard can see it. That path is covered by the disposal path clearing fire to zero and by
     * the recorded gap, not by a value repaired afterwards; a positive fire value is refused where the method is
     * used, which is the path real attackers take.
     */
    @Mixin(AttributeInstance.class)
    public abstract static class MaxHealth {
        @Inject(method="setBaseValue",at=@At("HEAD"),cancellable=true)
        private void base(double value,CallbackInfo callback) {
            if(((AttributeInstance)(Object)this).getAttribute()!=Attributes.MAX_HEALTH)return;
            LivingEntity owner=ProRuntime.attributeOwner(this);
            if(owner!=null&&ProRuntime.preventMaxHealthLoss(owner,value))callback.cancel();
        }
    }

    @Mixin(SynchedEntityData.DataItem.class)
    public abstract static class SyncedWrite {
        @Inject(method="setValue",at=@At("HEAD"),cancellable=true)
        private void value(Object written,CallbackInfo callback) {
            if(ProRuntime.preventDataWrite(this,written))callback.cancel();
        }
    }
}
