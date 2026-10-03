package dev.ronova.pro.mixin;

import com.mojang.serialization.DataResult;
import com.mojang.serialization.Dynamic;
import com.mojang.serialization.DynamicLike;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.MapLike;
import dev.ronova.pro.RecoverySources;
import java.util.function.Function;
import java.util.function.Supplier;
import net.minecraft.nbt.*;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Observe the original prefix and result at the NBT operation's actual publication point. */
public final class NbtOperationsHooks {
    private NbtOperationsHooks(){}
    @Mixin(NbtOps.class)
    public abstract static class Operations {
        @Redirect(method="mergeToMap(Lnet/minecraft/nbt/Tag;Lnet/minecraft/nbt/Tag;Lnet/minecraft/nbt/Tag;)Lcom/mojang/serialization/DataResult;",
                at=@At(value="INVOKE",target="Lcom/mojang/serialization/DataResult;success(Ljava/lang/Object;)Lcom/mojang/serialization/DataResult;"))
        private DataResult<Tag> valueResult(Object result,Tag prefix,Tag key,Tag value){
            RecoverySources.nbtOperationResult(prefix,(Tag)result);return DataResult.success((Tag)result);
        }
        @Redirect(method="mergeToMap(Lnet/minecraft/nbt/Tag;Lcom/mojang/serialization/MapLike;)Lcom/mojang/serialization/DataResult;",
                at=@At(value="INVOKE",target="Lcom/mojang/serialization/DataResult;success(Ljava/lang/Object;)Lcom/mojang/serialization/DataResult;"))
        private DataResult<Tag> mapResult(Object result,Tag prefix,MapLike<Tag> values){
            RecoverySources.nbtOperationResult(prefix,(Tag)result);return DataResult.success((Tag)result);
        }
        @Redirect(method="mergeToMap(Lnet/minecraft/nbt/Tag;Lcom/mojang/serialization/MapLike;)Lcom/mojang/serialization/DataResult;",
                at=@At(value="INVOKE",target="Lcom/mojang/serialization/DataResult;error(Ljava/util/function/Supplier;Ljava/lang/Object;)Lcom/mojang/serialization/DataResult;"))
        private DataResult<Tag> partialMapResult(Supplier<String> error,Object result,Tag prefix,MapLike<Tag> values){
            RecoverySources.nbtOperationResult(prefix,(Tag)result);return DataResult.error(error,(Tag)result);
        }
        @Inject(method="remove(Lnet/minecraft/nbt/Tag;Ljava/lang/String;)Lnet/minecraft/nbt/Tag;",at=@At("RETURN"))
        private void removed(Tag prefix,String key,CallbackInfoReturnable<Tag> callback){
            RecoverySources.nbtOperationResult(prefix,callback.getReturnValue());
        }
    }
    @Mixin(targets="net.minecraft.nbt.NbtOps$NbtRecordBuilder")
    public abstract static class Builder {
        @Redirect(method="build(Lnet/minecraft/nbt/CompoundTag;Lnet/minecraft/nbt/Tag;)Lcom/mojang/serialization/DataResult;",
                at=@At(value="INVOKE",target="Lcom/mojang/serialization/DataResult;success(Ljava/lang/Object;)Lcom/mojang/serialization/DataResult;"))
        private DataResult<Tag> built(Object result,CompoundTag builder,Tag prefix){
            RecoverySources.nbtOperationResult(prefix,(Tag)result);return DataResult.success((Tag)result);
        }
    }
    @Mixin(value=Dynamic.class,remap=false)
    public abstract static class DynamicMaps<T> extends DynamicLike<T> {
        @Shadow @Final private T value;
        protected DynamicMaps(DynamicOps<T> ops){super(ops);}
        @Inject(method="updateMapValues(Ljava/util/function/Function;)Lcom/mojang/serialization/Dynamic;",at=@At("RETURN"))
        private void updated(Function<?,?> updater,CallbackInfoReturnable<Dynamic<T>> callback){
            // Custom Dynamic subclasses can replace the map access and creation semantics.
            if(((Object)this).getClass()!=Dynamic.class||(DynamicOps<?>)ops!=NbtOps.INSTANCE)return;
            Dynamic<T> result=callback.getReturnValue();
            if(result==null||result==(Object)this||result.getClass()!=Dynamic.class
                    ||(DynamicOps<?>)result.getOps()!=NbtOps.INSTANCE)return;
            if(value instanceof CompoundTag from&&result.getValue() instanceof CompoundTag to&&from!=to)
                RecoverySources.nbtDynamicMapResult(from,to);
        }
    }
}
