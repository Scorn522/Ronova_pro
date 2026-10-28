package dev.ronova.pro;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.level.Level;
import net.minecraft.network.chat.Component;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.*;

public final class ProWeapon extends Item {
    private static final DeferredRegister<Item> ITEMS=DeferredRegister.create(ForgeRegistries.ITEMS,"ronova_pro");
    public static final RegistryObject<Item> BLADE=ITEMS.register("ronova_blade",ProWeapon::new);
    private ProWeapon(){super(new Item.Properties().stacksTo(1).fireResistant().rarity(Rarity.EPIC));}
    static void register(IEventBus bus){ITEMS.register(bus);bus.addListener(ProWeapon::creative);}
    private static void creative(BuildCreativeModeTabContentsEvent e){if(e.getTabKey()==CreativeModeTabs.COMBAT)e.accept(BLADE);}
    static boolean held(Player player){return stack(player)!=null;}
    static ItemStack stack(Player player){
        for(InteractionHand hand:InteractionHand.values()){ItemStack stack=player.getItemInHand(hand);if(stack.is(BLADE.get()))return stack;}
        return null;
    }
    static boolean carried(Player player){for(int i=0;i<player.getInventory().getContainerSize();i++)if(player.getInventory().getItem(i).is(BLADE.get()))return true;return false;}
    @Override public InteractionResultHolder<ItemStack> use(Level level,Player player,InteractionHand hand){
        if(!level.isClientSide&&player instanceof net.minecraft.server.level.ServerPlayer server)ProMod.useWeapon(server);
        return InteractionResultHolder.sidedSuccess(player.getItemInHand(hand),level.isClientSide);
    }
    @Override public net.minecraft.world.InteractionResult onItemUseFirst(ItemStack stack,net.minecraft.world.item.context.UseOnContext context){
        if(!context.getLevel().isClientSide&&context.getPlayer() instanceof net.minecraft.server.level.ServerPlayer server)ProMod.useWeapon(server);
        return net.minecraft.world.InteractionResult.sidedSuccess(context.getLevel().isClientSide);
    }
    @Override public void appendHoverText(ItemStack stack,Level level,java.util.List<Component> lines,TooltipFlag flag){
        lines.add(Component.translatable("item.ronova_pro.ronova_blade.help"));
        lines.add(Component.translatable("item.ronova_pro.ronova_blade.attack"));
    }
    @Override public boolean isFoil(ItemStack stack){return true;}
}
