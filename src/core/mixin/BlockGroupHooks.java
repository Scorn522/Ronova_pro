package dev.ronova.pro.mixin;

import dev.ronova.pro.BlockGroupPolicy;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** The block holder, rather than the ticker list, decides actual protection. */
public final class BlockGroupHooks {
    private BlockGroupHooks() { }

    @Mixin(BlockEntity.class)
    public abstract static class Body {
        @Inject(method="setRemoved",at=@At("HEAD"),cancellable=true)
        private void beforeRemove(CallbackInfo callback) {
            if(BlockGroupPolicy.removalDenied((BlockEntity)(Object)this))callback.cancel();
        }
    }

    @Mixin(LevelChunk.class)
    public abstract static class Holder {
        @Inject(method="removeBlockEntity",at=@At("HEAD"),cancellable=true)
        private void beforeRemove(BlockPos pos,CallbackInfo callback) {
            BlockEntity current=((LevelChunk)(Object)this).getBlockEntities().get(pos);
            if(current!=null&&BlockGroupPolicy.removalDenied(current))callback.cancel();
        }
        @Inject(method="removeBlockEntityTicker",at=@At("HEAD"),cancellable=true)
        private void beforeTickerRemoval(BlockPos pos,CallbackInfo callback) {
            BlockEntity current=((LevelChunk)(Object)this).getBlockEntities().get(pos);
            if(current!=null&&BlockGroupPolicy.removalDenied(current))callback.cancel();
        }
        @Inject(method="setBlockState",at=@At("HEAD"),cancellable=true)
        private void beforeStateChange(BlockPos pos,BlockState state,boolean moved,
                CallbackInfoReturnable<BlockState> callback) {
            LevelChunk chunk=(LevelChunk)(Object)this;
            if(BlockGroupPolicy.stateChangeDenied(chunk,pos,state))callback.setReturnValue(chunk.getBlockState(pos));
        }
        @Inject(method="setBlockEntity",at=@At("HEAD"),cancellable=true)
        private void beforePublication(BlockEntity entity,CallbackInfo callback) {
            if(BlockGroupPolicy.publicationDenied((LevelChunk)(Object)this,entity)
                    ||BlockGroupPolicy.replacementDenied((LevelChunk)(Object)this,entity))callback.cancel();
        }
        @Inject(method="setBlockEntity",at=@At("RETURN"))
        private void afterPublication(BlockEntity entity,CallbackInfo callback) {
            if(((LevelChunk)(Object)this).getBlockEntities().get(entity.getBlockPos())==entity)
                BlockGroupPolicy.published(entity);
        }
    }
}
