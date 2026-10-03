package dev.ronova.pro.mixin;

import org.spongepowered.asm.mixin.Mixin;

/** Late markers let the common weaver enclose injected callbacks as well as original bodies. */
public final class ClientDrawFaultHooks {
    @Mixin(value=net.minecraft.client.gui.Font.class,priority=1) public static abstract class Font { }
    @Mixin(value=net.minecraft.client.gui.screens.Screen.class,priority=1) public static abstract class Screen { }
    @Mixin(value=net.minecraft.client.renderer.entity.EntityRenderDispatcher.class,priority=1) public static abstract class Entity { }
    @Mixin(value=net.minecraft.client.renderer.blockentity.BlockEntityRenderDispatcher.class,priority=1) public static abstract class Block { }
    @Mixin(value=net.minecraft.client.particle.ParticleEngine.class,priority=1) public static abstract class Particles { }
    @Mixin(value=net.minecraftforge.client.gui.overlay.ForgeGui.class,priority=1,remap=false) public static abstract class Hud { }
}
