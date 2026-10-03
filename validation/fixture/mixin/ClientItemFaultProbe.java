package dev.ronova.pro.validation.mixin;

import dev.ronova.pro.validation.ClientFaultFixture;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.world.item.*;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Ordinary-priority foreign HEAD injection, before any vanilla item vertices are submitted. */
@Mixin(value=ItemRenderer.class,priority=1000)
public abstract class ClientItemFaultProbe implements ClientFaultFixture.ItemAliasProbe {
    // Same shape as ordinary third-party render aliases. R2 incorrectly counted this as a second entry.
    @Unique public void fixture$renderAlias(ItemStack stack,ItemDisplayContext context,boolean left,PoseStack pose,MultiBufferSource buffers,
                                            int light,int overlay,BakedModel model) {
        ClientFaultFixture.aliasCalled();
    }
    @Inject(method={"render","m_115143_"},at=@At("HEAD"),remap=false)
    private void fail(ItemStack stack,ItemDisplayContext context,boolean left,PoseStack pose,MultiBufferSource buffers,
                      int light,int overlay,BakedModel model,CallbackInfo ci) {
        ClientFaultFixture.injectedItem(stack,pose,buffers);
    }
}
