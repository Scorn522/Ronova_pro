package dev.ronova.pro.validation;

import dev.ronova.pro.ClientPresence;
import java.nio.file.*;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.*;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.Entity;

final class ClientAuditR6Fixture {
    private static boolean connecting,done,changed,observed;private static int ticks,id;private static UUID uuid;private static Cow target;
    static void tick() {
        if(done)return;Minecraft mc=Minecraft.getInstance();ticks++;
        try {
            if(ticks>3000)throw new IllegalStateException("audit client timeout");
            if(!connecting&&mc.screen instanceof TitleScreen&&mc.getOverlay()==null) {
                connecting=true;ConnectScreen.startConnecting(mc.screen,mc,new ServerAddress("127.0.0.1",25892),new ServerData("Own R6 audit","127.0.0.1:25892",false),false);
            }
            Path address=Path.of("../server/audit-target.txt");
            if(!changed&&mc.level!=null&&Files.exists(address)) {
                var rows=Files.readAllLines(address);id=Integer.parseInt(rows.get(0));uuid=UUID.fromString(rows.get(1));
                if(mc.level.getEntity(id) instanceof Cow cow&&ClientPresence.removalDenied(id,uuid)) {
                    target=cow;target.setId(id+50000);if(target.getId()!=id)throw new AssertionError("client identity setter escaped");changed=true;Files.writeString(Path.of("audit-client-ready.txt"),"ready");
                }
            }
            if(changed&&!observed&&!ClientPresence.removalDenied(id,uuid)) {
                float before=target.getHealth();target.setHealth(0);target.remove(Entity.RemovalReason.DISCARDED);
                String result="CLIENT_POLICY_REVOKED=true\nCLIENT_REVOKED_BODY_STILL_GUARDED="+(target.getHealth()==before&&!target.isRemoved());
                Files.writeString(Path.of("audit-client-facts.txt"),result);System.out.println("R6_AUDIT "+result);observed=true;
                target.setId(id);
            }
            Path result=Path.of("../server/audit-result.txt");if(Files.exists(result))finish(mc,Files.readString(result));
        } catch(Throwable failure) {failure.printStackTrace();finish(mc,"AUDIT_FAILED "+failure);}
    }
    private static void finish(Minecraft mc,String text) {done=true;try {Files.writeString(Path.of("fault-isolation-result.txt"),text);}catch(Exception ex){throw new IllegalStateException(ex);}System.out.println(text);mc.stop();}
}
