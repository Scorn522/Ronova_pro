package dev.ronova.pro;

import dev.ronova.pro.mixin.Access;
import java.util.function.Predicate;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/** Actual inventory slots; item getters from a stopped Mod are never used for selection. */
final class ModInventory {
    private ModInventory(){}
    static int clear(Player player,Predicate<Object> selected){
        int removed=0;var inventory=((Access.PlayerState)player).pro$inventory();
        for(var list:((Access.InventoryState)inventory).pro$compartments())for(int slot=0;slot<list.size();slot++){
            ItemStack stack=list.get(slot);Object item=((Access.StackState)(Object)stack).pro$item();
            if(selected.test(item)||selected.test(stack)){list.set(slot,ItemStack.EMPTY);removed++;}
        }
        if(removed>0)inventory.setChanged();return removed;
    }
}
