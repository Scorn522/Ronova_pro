package dev.ronova.pro.mixin;

import dev.ronova.pro.BlockGroupPolicy;
import dev.ronova.pro.ProRuntime;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
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
        @Redirect(method="postProcessGeneration()V",at=@At(value="INVOKE",target="Lit/unimi/dsi/fastutil/shorts/ShortList;iterator()Lit/unimi/dsi/fastutil/shorts/ShortListIterator;",remap=false),require=1,allow=1)
        private it.unimi.dsi.fastutil.shorts.ShortListIterator pendingPostProcessing(it.unimi.dsi.fastutil.shorts.ShortList pending){
            return ProRuntime.worldPostProcessingIterator((LevelChunk)(Object)this,pending);
        }
        @Redirect(method="postProcessGeneration()V",at=@At(value="INVOKE",target="Lit/unimi/dsi/fastutil/shorts/ShortList;clear()V",remap=false),require=1,allow=1)
        private void completedPostProcessing(it.unimi.dsi.fastutil.shorts.ShortList pending){
            if(!ProRuntime.worldPostProcessingClear((LevelChunk)(Object)this,pending))pending.clear();
        }
        @Redirect(method="postProcessGeneration()V",at=@At(value="INVOKE",target="Lnet/minecraft/world/level/chunk/UpgradeData;upgrade(Lnet/minecraft/world/level/chunk/LevelChunk;)V"),require=1,allow=1)
        private void pendingUpgrade(net.minecraft.world.level.chunk.UpgradeData upgrade,LevelChunk chunk){
            if(!ProRuntime.worldDeferChunkUpgrade(chunk,upgrade))upgrade.upgrade(chunk);
        }
        @Inject(method="removeBlockEntity",at=@At("HEAD"),cancellable=true)
        private void beforeRemove(BlockPos pos,CallbackInfo callback) {
            if(ProRuntime.worldBlockMutationDenied(((LevelChunk)(Object)this).getLevel(),pos)){callback.cancel();return;}
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
            if(ProRuntime.worldBlockMutationDenied(chunk.getLevel(),pos)){callback.setReturnValue(null);return;}
            if(BlockGroupPolicy.stateChangeDenied(chunk,pos,state))callback.setReturnValue(chunk.getBlockState(pos));
        }
        @Inject(method={"setBlockEntity","addAndRegisterBlockEntity"},at=@At("HEAD"),cancellable=true)
        private void beforePublication(BlockEntity entity,CallbackInfo callback) {
            LevelChunk chunk=(LevelChunk)(Object)this;
            BlockEntity current=chunk.getBlockEntities().get(entity.getBlockPos());
            // Preserve initial disk/lazy materialization. A frozen, already bound
            // tile cannot be replaced through direct publication.
            if(current!=null&&current!=entity&&ProRuntime.worldBlockMutationDenied(chunk.getLevel(),entity.getBlockPos())
                    ||BlockGroupPolicy.publicationDenied((LevelChunk)(Object)this,entity)
                    ||BlockGroupPolicy.replacementDenied((LevelChunk)(Object)this,entity))callback.cancel();
        }
        @Inject(method="setBlockEntity",at=@At("RETURN"))
        private void afterPublication(BlockEntity entity,CallbackInfo callback) {
            if(((LevelChunk)(Object)this).getBlockEntities().get(entity.getBlockPos())==entity)
                BlockGroupPolicy.published(entity);
        }
    }
}
