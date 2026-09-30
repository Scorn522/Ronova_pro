package dev.ronova.pro.mixin;

import dev.ronova.pro.ProRuntime;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;

@Mixin(Entity.class)
public abstract class EntityHooks {
    @Inject(method="saveWithoutId",at=@At("HEAD"))
    private void saving(CompoundTag input,CallbackInfoReturnable<CompoundTag> ci) {
        dev.ronova.pro.RecoverySources.saveBeginning((Entity)(Object)this,input);
    }
    @Inject(method={"remove","setRemoved"},at=@At("HEAD"),cancellable=true)
    private void guard(Entity.RemovalReason reason,CallbackInfo ci) {
        if (ProRuntime.preventRemoval((Entity)(Object)this,reason)) ci.cancel();
    }
    @Inject(method="saveWithoutId",at=@At("RETURN"))
    private void saved(CompoundTag input,CallbackInfoReturnable<CompoundTag> ci) {
        ProRuntime.saved((Entity)(Object)this,ci.getReturnValue());
    }
    @Inject(method="load",at=@At("RETURN"))
    private void loaded(CompoundTag input,CallbackInfo ci) {
        ProRuntime.loaded((Entity)(Object)this,input);
    }
    @Inject(method="load",at=@At("HEAD"),cancellable=true)
    private void consuming(CompoundTag input,CallbackInfo ci) {
        Entity entity=(Entity)(Object)this;
        if(dev.ronova.pro.RecoverySources.refuseConsumption(input,((Access.EntityState)entity).pro$level()))ci.cancel();
    }
}
