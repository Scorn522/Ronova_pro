package dev.ronova.pro.validation.mixin;
import dev.ronova.pro.validation.LifeSpoof;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
@Mixin(LivingEntity.class)
public abstract class LifeSpoofMixin {
    @Inject(method={"getHealth","m_21223_"},at=@At("RETURN"),cancellable=true,remap=false)
    private void health(CallbackInfoReturnable<Float> result){Float delta=LifeSpoof.delta(this);if(delta!=null)result.setReturnValue(Math.min(result.getReturnValue(),20+delta));}
    @Inject(method={"isAlive","m_6084_"},at=@At("RETURN"),cancellable=true,remap=false)
    private void alive(CallbackInfoReturnable<Boolean> result){if(LifeSpoof.delta(this)!=null)result.setReturnValue(false);}
    @Inject(method={"isDeadOrDying","m_21224_"},at=@At("RETURN"),cancellable=true,remap=false)
    private void dead(CallbackInfoReturnable<Boolean> result){if(LifeSpoof.delta(this)!=null)result.setReturnValue(true);}
}
