package dev.ronova.pro.mixin;

import dev.ronova.pro.LiveStatePolicy;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.FoodData;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;

/** Public entries reject effects before event/attribute side effects. Original scopes have real outer finally. */
public final class LiveStateHooks {
    private LiveStateHooks() { }
    @Mixin(LivingEntity.class)
    public abstract static class Effects {
        @Inject(method="tickEffects",at=@At("HEAD"))
        private void ronova$liveTick(CallbackInfo callback) { }
        @Inject(method="completeUsingItem",at=@At("HEAD"))
        private void ronova$liveConsume(CallbackInfo callback) { }
        @Inject(method="addEffect(Lnet/minecraft/world/effect/MobEffectInstance;Lnet/minecraft/world/entity/Entity;)Z",at=@At("HEAD"),cancellable=true)
        private void ronova$liveAdd(MobEffectInstance effect,Entity source,CallbackInfoReturnable<Boolean> callback) {
            if(LiveStatePolicy.effectEntryDenied((LivingEntity)(Object)this,"addEffect",effect))callback.setReturnValue(false);
        }
        @Inject(method="forceAddEffect",at=@At("HEAD"),cancellable=true)
        private void ronova$liveForce(MobEffectInstance effect,Entity source,CallbackInfo callback) {
            if(LiveStatePolicy.effectEntryDenied((LivingEntity)(Object)this,"forceAddEffect",effect))callback.cancel();
        }
        @Inject(method="removeEffect",at=@At("HEAD"),cancellable=true)
        private void ronova$liveRemove(MobEffect effect,CallbackInfoReturnable<Boolean> callback) {
            if(LiveStatePolicy.effectEntryDenied((LivingEntity)(Object)this,"removeEffect",null))callback.setReturnValue(false);
        }
        @Inject(method="removeEffectNoUpdate",at=@At("HEAD"),cancellable=true)
        private void ronova$liveRemoveRaw(MobEffect effect,CallbackInfoReturnable<MobEffectInstance> callback) {
            if(LiveStatePolicy.effectEntryDenied((LivingEntity)(Object)this,"removeEffectNoUpdate",null))callback.setReturnValue(null);
        }
        @Inject(method="removeAllEffects",at=@At("HEAD"),cancellable=true)
        private void ronova$liveClear(CallbackInfoReturnable<Boolean> callback) {
            if(LiveStatePolicy.effectEntryDenied((LivingEntity)(Object)this,"removeAllEffects",null))callback.setReturnValue(false);
        }
        @Inject(method="curePotionEffects",at=@At("HEAD"),cancellable=true,remap=false)
        private void ronova$liveCure(ItemStack item,CallbackInfoReturnable<Boolean> callback) {
            if(LiveStatePolicy.effectEntryDenied((LivingEntity)(Object)this,"curePotionEffects",null))callback.setReturnValue(false);
        }
        @Inject(method={"onEffectAdded","onEffectUpdated","onEffectRemoved"},at=@At("RETURN"))
        private void ronova$liveChanged(CallbackInfo callback) { LiveStatePolicy.changed((LivingEntity)(Object)this); }
    }
    @Mixin(MobEffectInstance.class)
    public abstract static class EffectValue {
        @Inject(method="tick",at=@At("HEAD"),cancellable=true)
        private void ronova$liveTick(LivingEntity entity,Runnable changed,CallbackInfoReturnable<Boolean> callback) {
            if(LiveStatePolicy.effectTickDenied((MobEffectInstance)(Object)this,entity))callback.setReturnValue(false);
        }
    }
    @Mixin(MobEffectInstance.FactorData.class)
    public abstract static class Factors {
        @Inject(method="tick",at=@At("HEAD"))
        private void ronova$liveFactorTick(MobEffectInstance effect,CallbackInfo callback) { }
        @Inject(method="getFactor",at=@At("HEAD"))
        private void ronova$liveFactorRead(LivingEntity entity,float partialTick,CallbackInfoReturnable<Float> callback) { }
    }
    @Mixin(FoodData.class)
    public abstract static class Food {
        @Inject(method="tick",at=@At("HEAD"))
        private void ronova$liveTick(Player player,CallbackInfo callback) { }
        @Inject(method="eat(IF)V",at=@At("HEAD"),cancellable=true)
        private void ronova$liveEat(int nutrition,float saturation,CallbackInfo callback) {
            if(LiveStatePolicy.foodEntryDenied((FoodData)(Object)this,"eat"))callback.cancel();
        }
        @Inject(method={"addExhaustion","setSaturation","setExhaustion"},at=@At("HEAD"),cancellable=true)
        private void ronova$liveFloat(float value,CallbackInfo callback) {
            if(LiveStatePolicy.foodEntryDenied((FoodData)(Object)this,"food-write"))callback.cancel();
        }
        @Inject(method="setFoodLevel",at=@At("HEAD"),cancellable=true)
        private void ronova$liveLevel(int value,CallbackInfo callback) {
            if(LiveStatePolicy.foodEntryDenied((FoodData)(Object)this,"food-write"))callback.cancel();
        }
    }
    @Mixin(Player.class)
    public abstract static class FoodAction {
        @Inject(method="causeFoodExhaustion",at=@At("HEAD"))
        private void ronova$liveExhaustion(float value,CallbackInfo callback) { }
    }
}
