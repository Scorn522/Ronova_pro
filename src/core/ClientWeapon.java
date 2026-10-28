package dev.ronova.pro;

import net.minecraft.client.Minecraft;
import net.minecraft.client.KeyMapping;
import net.minecraft.network.Connection;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import com.mojang.blaze3d.platform.InputConstants;

@Mod.EventBusSubscriber(modid="ronova_pro",value=Dist.CLIENT)
public final class ClientWeapon {
    private static final KeyMapping PANEL=new KeyMapping("key.ronova_pro.panel",InputConstants.Type.KEYSYM,org.lwjgl.glfw.GLFW.GLFW_KEY_R,"key.categories.ronova_pro");
    private static java.lang.ref.WeakReference<WeaponScreen> active=new java.lang.ref.WeakReference<>(null);
    private ClientWeapon(){}
    @Mod.EventBusSubscriber(modid="ronova_pro",value=Dist.CLIENT,bus=Mod.EventBusSubscriber.Bus.MOD)
    public static final class Keys {
        @SubscribeEvent public static void keys(RegisterKeyMappingsEvent event){event.register(PANEL);}
    }
    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent event){
        if(event.phase!=TickEvent.Phase.END)return;
        var minecraft=Minecraft.getInstance();
        while(PANEL.consumeClick())if(minecraft.screen==null&&minecraft.player!=null&&ProWeapon.carried(minecraft.player)&&minecraft.getConnection()!=null)ProNetwork.weaponOpen();
    }
    @SubscribeEvent public static void swing(net.minecraftforge.event.entity.player.PlayerInteractEvent.LeftClickEmpty event){if(event.getLevel().isClientSide&&ProWeapon.held(event.getEntity()))ProNetwork.weaponSwing();}
    static void panel(ProNetwork.WeaponPanel panel,Connection connection){
        var minecraft=Minecraft.getInstance();
        if(minecraft.getConnection()==null||minecraft.getConnection().getConnection()!=connection||minecraft.player==null||!ProWeapon.carried(minecraft.player))return;
        WeaponScreen screen=active.get();
        if(screen!=null&&screen.showing(minecraft.screen)&&screen.session().equals(panel.session()))screen.apply(panel);
        else{screen=new WeaponScreen(panel,connection);active=new java.lang.ref.WeakReference<>(screen);minecraft.setScreen(screen);}
    }
    static void result(ProNetwork.WeaponResult result,Connection connection){
        var minecraft=Minecraft.getInstance();
        WeaponScreen screen=active.get();if(minecraft.getConnection()!=null&&minecraft.getConnection().getConnection()==connection&&screen!=null&&screen.showing(minecraft.screen))screen.result(result,connection);
    }
}
