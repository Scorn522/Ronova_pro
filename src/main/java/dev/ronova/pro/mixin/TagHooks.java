package dev.ronova.pro.mixin;

import dev.ronova.pro.ProRuntime;
import net.minecraft.nbt.CompoundTag;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(CompoundTag.class)
public abstract class TagHooks implements dev.ronova.pro.RecoverySources.GraphNode {
    @Shadow @Final private java.util.Map<String,net.minecraft.nbt.Tag> tags;
    @Unique private boolean pro$closedGraph;
    @Inject(method="<init>(Ljava/util/Map;)V",at=@At("RETURN"))
    private void constructed(java.util.Map<String,net.minecraft.nbt.Tag> map,org.spongepowered.asm.mixin.injection.callback.CallbackInfo callback) {
        pro$closedGraph=map.getClass()==java.util.HashMap.class&&dev.ronova.pro.RecoverySources.graphConstructor(CompoundTag.class);
    }
    @Inject(method="entries",at=@At("HEAD"))
    private void exposed(CallbackInfoReturnable<java.util.Map<String,net.minecraft.nbt.Tag>> callback) {
        dev.ronova.pro.RecoverySources.graphExposed(()->pro$closedGraph=false);
    }
    @Override public final boolean pro$closedGraph() { return pro$closedGraph&&tags.getClass()==java.util.HashMap.class; }
    @Redirect(method={"put","putByte","putShort","putInt","putLong","putFloat","putDouble","putString",
            "putByteArray","putIntArray","putLongArray","merge","copy"},
            at=@At(value="INVOKE",target="Ljava/util/Map;put(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;"))
    private Object mutation(java.util.Map<Object,Object> map,Object key,Object value) {
        return dev.ronova.pro.RecoverySources.tagPut(map,key,value);
    }
    @Inject(method="copy()Lnet/minecraft/nbt/CompoundTag;",at=@At("RETURN"))
    private void copied(CallbackInfoReturnable<CompoundTag> ci) {
        ProRuntime.copied((CompoundTag)(Object)this,ci.getReturnValue());
    }
}
