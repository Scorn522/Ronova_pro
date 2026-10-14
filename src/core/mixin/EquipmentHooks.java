package dev.ronova.pro.mixin;

import dev.ronova.pro.EquipmentPolicy;
import java.util.List;
import java.util.function.*;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.*;
import net.minecraft.util.RandomSource;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.*;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;

/** Refuse before equipment callbacks, copying a removed item, or publishing a dropped item. */
public final class EquipmentHooks {
    private EquipmentHooks(){ }
    @Mixin({Player.class,Mob.class,ArmorStand.class})
    public abstract static class Equipping {
        @Inject(method="setItemSlot",at=@At("HEAD"),cancellable=true)
        private void ronova$equipmentSlot(EquipmentSlot slot,ItemStack stack,CallbackInfo callback){
            if(EquipmentPolicy.itemSlotDenied((LivingEntity)(Object)this,slot,stack))callback.cancel();
        }
    }
    @Mixin(LivingEntity.class)
    public abstract static class Hands {
        @Inject(method="setItemInHand",at=@At("HEAD"),cancellable=true)
        private void ronova$equipmentHand(InteractionHand hand,ItemStack stack,CallbackInfo callback){
            if(hand!=null&&EquipmentPolicy.itemSlotDenied((LivingEntity)(Object)this,hand==InteractionHand.MAIN_HAND?EquipmentSlot.MAINHAND:EquipmentSlot.OFFHAND,stack))callback.cancel();
        }
    }
    @Mixin(NonNullList.class)
    public abstract static class Lists {
        @Inject(method="set(ILjava/lang/Object;)Ljava/lang/Object;",at=@At("HEAD"),cancellable=true)
        private void ronova$equipmentList(int index,Object next,CallbackInfoReturnable<Object> callback){
            if(next==null)return; // Preserve the original null/range failure contract.
            List<?> values=((Access.EquipmentList)this).pro$list();
            if(index>=0&&index<values.size()&&EquipmentPolicy.listWriteDenied(this,index,next))callback.setReturnValue(values.get(index));
        }
    }
    @Mixin(ItemStack.class)
    public abstract static class Stacks {
        @Inject(method="setCount",at=@At("HEAD"),cancellable=true)
        private void ronova$equipmentCount(int count,CallbackInfo callback){if(EquipmentPolicy.stackEntryDenied((ItemStack)(Object)this,"setCount",new Object[]{count}))callback.cancel();}
        @Inject(method="grow",at=@At("HEAD"),cancellable=true)
        private void ronova$equipmentGrow(int count,CallbackInfo callback){if(EquipmentPolicy.stackEntryDenied((ItemStack)(Object)this,"grow",new Object[]{count}))callback.cancel();}
        @Inject(method="shrink",at=@At("HEAD"),cancellable=true)
        private void ronova$equipmentShrink(int count,CallbackInfo callback){if(EquipmentPolicy.stackEntryDenied((ItemStack)(Object)this,"shrink",new Object[]{count}))callback.cancel();}
        @Inject(method="setTag",at=@At("HEAD"),cancellable=true)
        private void ronova$equipmentTag(CompoundTag tag,CallbackInfo callback){if(EquipmentPolicy.stackEntryDenied((ItemStack)(Object)this,"setTag",new Object[]{tag}))callback.cancel();}
        @Inject(method="setDamageValue",at=@At("HEAD"),cancellable=true)
        private void ronova$equipmentDamage(int damage,CallbackInfo callback){if(EquipmentPolicy.stackEntryDenied((ItemStack)(Object)this,"setDamageValue",new Object[]{damage}))callback.cancel();}
        @Inject(method="split",at=@At("HEAD"),cancellable=true)
        private void ronova$equipmentSplit(int count,CallbackInfoReturnable<ItemStack> callback){if(EquipmentPolicy.stackEntryDenied((ItemStack)(Object)this,"split",new Object[]{count}))callback.setReturnValue(ItemStack.EMPTY);}
        @Inject(method="copyAndClear",at=@At("HEAD"),cancellable=true)
        private void ronova$equipmentCopyClear(CallbackInfoReturnable<ItemStack> callback){if(EquipmentPolicy.stackEntryDenied((ItemStack)(Object)this,"copyAndClear",new Object[0]))callback.setReturnValue(ItemStack.EMPTY);}
        @Inject(method="hurt",at=@At("HEAD"),cancellable=true)
        private void ronova$equipmentHurt(int damage,RandomSource random,ServerPlayer player,CallbackInfoReturnable<Boolean> callback){if(EquipmentPolicy.stackEntryDenied((ItemStack)(Object)this,"hurt",new Object[]{damage,random,player}))callback.setReturnValue(false);}
        @Inject(method="hurtAndBreak",at=@At("HEAD"),cancellable=true)
        private <T extends LivingEntity> void ronova$equipmentBreak(int damage,T entity,Consumer<T> broken,CallbackInfo callback){if(EquipmentPolicy.stackEntryDenied((ItemStack)(Object)this,"hurtAndBreak",new Object[]{damage,entity,broken}))callback.cancel();}
        @Inject(method="deserializeNBT(Lnet/minecraft/nbt/CompoundTag;)V",at=@At("HEAD"),cancellable=true,remap=false)
        private void ronova$equipmentDeserialize(CompoundTag tag,CallbackInfo callback){if(EquipmentPolicy.stackEntryDenied((ItemStack)(Object)this,"deserializeNBT",new Object[]{tag}))callback.cancel();}
    }
    @Mixin(Inventory.class)
    public abstract static class InventoryBody {
        @Inject(method="setItem",at=@At("HEAD"),cancellable=true)
        private void ronova$equipmentSet(int index,ItemStack stack,CallbackInfo callback){if(EquipmentPolicy.inventoryEntryDenied((Inventory)(Object)this,"setItem",new Object[]{index,stack}))callback.cancel();}
        @Inject(method="removeItem(II)Lnet/minecraft/world/item/ItemStack;",at=@At("HEAD"),cancellable=true)
        private void ronova$equipmentTake(int index,int count,CallbackInfoReturnable<ItemStack> callback){if(count>0&&EquipmentPolicy.inventoryEntryDenied((Inventory)(Object)this,"removeItem",new Object[]{index,count}))callback.setReturnValue(ItemStack.EMPTY);}
        @Inject(method="removeItem(Lnet/minecraft/world/item/ItemStack;)V",at=@At("HEAD"),cancellable=true)
        private void ronova$equipmentRemove(ItemStack stack,CallbackInfo callback){if(EquipmentPolicy.inventoryEntryDenied((Inventory)(Object)this,"removeItem",new Object[]{stack}))callback.cancel();}
        @Inject(method="removeItemNoUpdate",at=@At("HEAD"),cancellable=true)
        private void ronova$equipmentTakeAll(int index,CallbackInfoReturnable<ItemStack> callback){if(EquipmentPolicy.inventoryEntryDenied((Inventory)(Object)this,"removeItemNoUpdate",new Object[]{index}))callback.setReturnValue(ItemStack.EMPTY);}
        @Inject(method="removeFromSelected",at=@At("HEAD"),cancellable=true)
        private void ronova$equipmentTakeSelected(boolean all,CallbackInfoReturnable<ItemStack> callback){if(EquipmentPolicy.inventoryEntryDenied((Inventory)(Object)this,"removeFromSelected",new Object[]{all}))callback.setReturnValue(ItemStack.EMPTY);}
        @Inject(method="dropAll",at=@At("HEAD"),cancellable=true)
        private void ronova$equipmentDrop(CallbackInfo callback){if(EquipmentPolicy.inventoryEntryDenied((Inventory)(Object)this,"dropAll",new Object[0]))callback.cancel();}
        @Inject(method="clearContent",at=@At("HEAD"),cancellable=true)
        private void ronova$equipmentClear(CallbackInfo callback){if(EquipmentPolicy.inventoryEntryDenied((Inventory)(Object)this,"clearContent",new Object[0]))callback.cancel();}
        @Inject(method="load",at=@At("HEAD"),cancellable=true)
        private void ronova$equipmentLoad(ListTag tag,CallbackInfo callback){if(EquipmentPolicy.inventoryEntryDenied((Inventory)(Object)this,"load",new Object[]{tag}))callback.cancel();}
        @Inject(method="replaceWith",at=@At("HEAD"),cancellable=true)
        private void ronova$equipmentReplace(Inventory other,CallbackInfo callback){if(EquipmentPolicy.inventoryEntryDenied((Inventory)(Object)this,"replaceWith",new Object[]{other}))callback.cancel();}
        @Inject(method="clearOrCountMatchingItems",at=@At("HEAD"),cancellable=true)
        private void ronova$equipmentMatching(Predicate<ItemStack> predicate,int amount,Container other,CallbackInfoReturnable<Integer> callback){
            if(amount!=0&&EquipmentPolicy.inventoryEntryDenied((Inventory)(Object)this,"clearOrCountMatchingItems",new Object[]{predicate,amount,other}))callback.setReturnValue(0);
        }
    }
    @Mixin(ContainerHelper.class)
    public abstract static class Helpers {
        @Inject(method="removeItem",at=@At("HEAD"),cancellable=true)
        private static void ronova$equipmentTake(List<ItemStack> list,int index,int count,CallbackInfoReturnable<ItemStack> callback){if(EquipmentPolicy.takeDenied(list,index,count))callback.setReturnValue(ItemStack.EMPTY);}
        @Inject(method="takeItem",at=@At("HEAD"),cancellable=true)
        private static void ronova$equipmentTakeAll(List<ItemStack> list,int index,CallbackInfoReturnable<ItemStack> callback){if(EquipmentPolicy.takeDenied(list,index,1))callback.setReturnValue(ItemStack.EMPTY);}
    }
}
