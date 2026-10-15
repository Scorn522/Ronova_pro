package dev.ronova.pro.mixin;

import org.spongepowered.asm.mixin.Mixin;

/** Exact Forge bodies are completed by SaveFinallyPlugin after mixin application. */
public final class EquipmentCapabilityHooks {
    private EquipmentCapabilityHooks(){ }
    @Mixin(value=net.minecraftforge.common.capabilities.CapabilityProvider.class,remap=false)
    public abstract static class Provider { }
    @Mixin(value=net.minecraftforge.common.capabilities.CapabilityDispatcher.class,remap=false)
    public abstract static class Dispatcher { }
    @Mixin(value=net.minecraftforge.common.util.LazyOptional.class,remap=false)
    public abstract static class Optional { }
}
