package dev.ronova.pro.validation.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.ronova.pro.validation.ClientDrawFixture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;

public final class ClientDrawFaultProbe {
    @Mixin(net.minecraft.client.gui.Font.class) public static abstract class Font {
        @Inject(method={"drawInBatch(Lnet/minecraft/util/FormattedCharSequence;FFIZLorg/joml/Matrix4f;Lnet/minecraft/client/renderer/MultiBufferSource;Lnet/minecraft/client/gui/Font$DisplayMode;II)I","m_272191_"},at=@At("HEAD"),remap=false)
        private void draw(FormattedCharSequence text,float x,float y,int color,boolean shadow,org.joml.Matrix4f pose,MultiBufferSource buffer,net.minecraft.client.gui.Font.DisplayMode mode,int background,int light,CallbackInfoReturnable<Integer> ci) {ClientDrawFixture.font(text);}
    }
    @Mixin(net.minecraft.client.renderer.entity.EntityRenderDispatcher.class) public static abstract class Entities {
        @Inject(method={"render","m_114384_"},at=@At("HEAD"),remap=false)
        private void draw(Entity entity,double x,double y,double z,float yaw,float tick,PoseStack pose,MultiBufferSource buffer,int light,CallbackInfo ci) {ClientDrawFixture.entity(entity,pose);}
    }
    @Mixin(net.minecraft.client.renderer.blockentity.BlockEntityRenderDispatcher.class) public static abstract class Blocks {
        @Inject(method={"render","m_112267_"},at=@At("HEAD"),remap=false)
        private void draw(BlockEntity entity,float tick,PoseStack pose,MultiBufferSource buffer,CallbackInfo ci) {ClientDrawFixture.block(entity,pose);}
    }
}
