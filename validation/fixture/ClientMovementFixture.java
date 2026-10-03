package dev.ronova.pro.validation;

import java.nio.file.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.*;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;

final class ClientMovementFixture {
    private static boolean connecting,done;private static int ticks;
    static void tick() {
        if(done)return;Minecraft mc=Minecraft.getInstance();ticks++;
        try {
            if(ticks>1800)throw new IllegalStateException("movement client timeout");
            if(!connecting&&mc.screen instanceof TitleScreen&&mc.getOverlay()==null) {
                connecting=true;ConnectScreen.startConnecting(mc.screen,mc,new ServerAddress("127.0.0.1",25892),new ServerData("Own movement fixture","127.0.0.1:25892",false),false);
            }
            Path result=Path.of("../server/movement-result.txt");
            if(Files.isRegularFile(result)) {
                String text=Files.readString(result);
                if(mc.player==null||mc.level==null||!mc.player.connection.getConnection().isConnected())throw new IllegalStateException("player disconnected during movement");
                if(mc.level.getEntity(mc.player.getId())!=mc.player)throw new IllegalStateException("client player mirror missing");
                finish(mc,text);
            }
        } catch(Throwable failure) {failure.printStackTrace();finish(mc,"MOVEMENT_FAILED "+failure);}
    }
    private static void finish(Minecraft mc,String text) {
        done=true;try {Files.writeString(Path.of("fault-isolation-result.txt"),text);}catch(Exception ex){throw new IllegalStateException(ex);}
        System.out.println(text);mc.stop();
    }
}
