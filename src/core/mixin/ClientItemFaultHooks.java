package dev.ronova.pro.mixin;

import com.mojang.blaze3d.vertex.*;
import dev.ronova.pro.ClientItemFaults;
import java.util.*;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.gen.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

public final class ClientItemFaultHooks {
    // Applied after ordinary mod mixins. The plugin encloses their HEAD callbacks as well as the vanilla body.
    @Mixin(value=ItemRenderer.class,priority=1)
    public static abstract class Item { }

    @Mixin(value=GuiGraphics.class,priority=1)
    public interface Tooltip extends ClientItemFaults.TooltipAccess {
        @Accessor(value="tooltipStack",remap=false) ItemStack ronova$tooltipStack();
        @Accessor("managed") boolean ronova$managed();
        @Accessor("managed") void ronova$managed(boolean value);
    }
    @Mixin(targets="net.minecraft.client.gui.GuiGraphics$ScissorStack")
    public static abstract class Scissors {
        @Shadow @Final private Deque<ScreenRectangle> stack;
        @Inject(method={"push","pop"},at=@At("HEAD"))
        private void remember(org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable<ScreenRectangle> cir) {
            ClientItemFaults.beforeScissor(stack);
        }
    }

    @Mixin(PoseStack.class)
    public interface Pose extends ClientItemFaults.PoseAccess {
        @Accessor("poseStack") Deque<PoseStack.Pose> ronova$poses();
    }
    @Mixin(BufferBuilder.class)
    public static abstract class Builder implements ClientItemFaults.BufferAbort {
        @Shadow public abstract void discard();
        @Shadow private void reset() { throw new AssertionError("mixin shadow"); }
        public void ronova$abortItemBatch() { reset();discard();((BufferBuilder)(Object)this).unsetDefaultColor(); }
        @Inject(method="begin",at=@At("HEAD"))
        private void begin(CallbackInfo ci) { ClientItemFaults.touched(this); }
    }
    @Mixin(MultiBufferSource.BufferSource.class)
    public static abstract class Source implements ClientItemFaults.BufferAbort {
        @Shadow @Final protected Set<BufferBuilder> startedBuffers;
        @Shadow protected Optional<RenderType> lastState;
        @Inject(method="getBuffer",at=@At("HEAD"))
        private void touched(RenderType type,org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable<VertexConsumer> ci) { ClientItemFaults.touched(this); }
        public void ronova$abortItemBatch() {
            for(BufferBuilder buffer:startedBuffers)((ClientItemFaults.BufferAbort)buffer).ronova$abortItemBatch();
            startedBuffers.clear();lastState=Optional.empty();
        }
    }
}
