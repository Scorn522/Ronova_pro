package dev.ronova.pro.mixin;

import dev.ronova.pro.RecoverySources;
import java.util.List;
import net.minecraft.nbt.ListTag;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.*;

@Mixin(ListTag.class)
public abstract class ListTagHooks implements RecoverySources.GraphNode {
    @Shadow @Final private java.util.List<net.minecraft.nbt.Tag> list;
    @Unique private boolean pro$closedGraph;
    @Inject(method="<init>(Ljava/util/List;B)V",at=@At("RETURN"))
    private void constructed(java.util.List<net.minecraft.nbt.Tag> items,byte type,org.spongepowered.asm.mixin.injection.callback.CallbackInfo callback) {
        pro$closedGraph=items.getClass()==java.util.ArrayList.class&&RecoverySources.graphConstructor(ListTag.class);
    }
    @Override public final boolean pro$closedGraph() { return pro$closedGraph&&list.getClass()==java.util.ArrayList.class; }
    @Redirect(method="setTag",at=@At(value="INVOKE",target="Ljava/util/List;set(ILjava/lang/Object;)Ljava/lang/Object;"))
    private Object set(List<Object> list,int index,Object value) { return RecoverySources.listSet(list,index,value); }
    @Redirect(method="addTag",at=@At(value="INVOKE",target="Ljava/util/List;add(ILjava/lang/Object;)V"))
    private void add(List<Object> list,int index,Object value) { RecoverySources.listAdd(list,index,value); }
}
