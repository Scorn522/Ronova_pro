package dev.ronova.pro.validation;

import dev.ronova.pro.ClientPresence;
import java.nio.file.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.*;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.animal.Cow;

/** Uses the real mirror, add-entity packet and handoff; supplies no production policy or receipts. */
final class ClientDefenseFixture {
    private static boolean connecting,done,checked,handoff;private static int ticks;
    private static Entity originalPlayer;private static Cow target;
    private static void require(boolean ok,String message) {if(!ok)throw new AssertionError(message);}
    static void tick() {
        if(done)return;Minecraft mc=Minecraft.getInstance();ticks++;
        try {
            if(ticks>1800)throw new IllegalStateException("defense client timeout");
            if(!connecting&&mc.screen instanceof TitleScreen&&mc.getOverlay()==null) {
                connecting=true;ConnectScreen.startConnecting(mc.screen,mc,new ServerAddress("127.0.0.1",25892),new ServerData("Own defense fixture","127.0.0.1:25892",false),false);
            }
            Path address=Path.of("../server/defense-target.txt");
            if(!checked&&mc.level!=null&&Files.isRegularFile(address)) {
                int id=Integer.parseInt(Files.readAllLines(address).get(0));
                var entity=mc.level.getEntity(id);
                if(entity instanceof Cow cow&&ClientPresence.removalDenied(id,cow.getUUID())) {
                    target=cow;originalPlayer=mc.player;float health=cow.getHealth();
                    LifeSpoof.set(cow,-282);LifeSpoof.set(mc.player,-282);
                    require(cow.getHealth()==health&&cow.isAlive()&&!cow.isDeadOrDying(),"client mirror effective life spoof escaped");
                    require(mc.player.getHealth()>0&&mc.player.isAlive()&&!mc.player.isDeadOrDying(),"client player effective life spoof escaped");
                    System.out.println("EFFECTIVE_LIFE_CLIENT_PASS merged_getters_real_protection_mirror");
                    for(Entity.RemovalReason reason:Entity.RemovalReason.values()) {
                        cow.remove(reason);require(!cow.isRemoved(),"client outer removal allowed: "+reason);
                        cow.setRemoved(reason);require(!cow.isRemoved(),"client setRemoved allowed: "+reason);
                    }
                    cow.die(mc.level.damageSources().generic());cow.setHealth(0);
                    require(cow.getHealth()==health&&!cow.isDeadOrDying(),"client death/health allowed");
                    mc.level.removeEntity(id,Entity.RemovalReason.DISCARDED);
                    require(mc.level.getEntity(id)==cow,"client level partially removed protected mirror");
                    mc.getConnection().handleAddEntity(new net.minecraft.network.protocol.game.ClientboundAddEntityPacket(cow));
                    require(mc.level.getEntity(id)==cow&&!cow.isRemoved(),"duplicate spawn replaced protected mirror");
                    Cow neighbour=new Cow(net.minecraft.world.entity.EntityType.COW,mc.level);neighbour.setHealth(0);
                    neighbour.remove(Entity.RemovalReason.DISCARDED);require(neighbour.isRemoved()&&neighbour.getHealth()==0,"unprotected client neighbour blocked");
                    checked=true;Files.writeString(Path.of("defense-client-checked.txt"),"CLIENT_DEFENSE_PASS");
                }
            }
            if(checked&&!handoff&&Files.isRegularFile(Path.of("../server/defense-handoff.txt"))
                    &&mc.player!=null&&mc.player!=originalPlayer&&ClientPresence.removalDenied(mc.player.getId(),mc.player.getUUID())) {
                target.setHealth(0);require(target.getHealth()<=0&&!target.isAlive()&&target.isDeadOrDying(),"revoked old client body still guarded");
                float health=mc.player.getHealth();mc.player.setHealth(0);require(mc.player.getHealth()==health,"new client player unprotected");
                mc.player.remove(Entity.RemovalReason.DISCARDED);require(!mc.player.isRemoved(),"new player removal allowed");
                require(mc.level.getEntity(mc.player.getId())==mc.player,"new player absent from client lookup");
                handoff=true;Files.writeString(Path.of("defense-handoff-checked.txt"),"HANDOFF_PASS");
            }
            Path result=Path.of("../server/defense-result.txt");
            if(Files.isRegularFile(result))finish(mc,Files.readString(result));
        } catch(Throwable failure) {failure.printStackTrace();finish(mc,"DEFENSE_FAILED "+failure);}
    }
    private static void finish(Minecraft mc,String text) {
        done=true;try {Files.writeString(Path.of("fault-isolation-result.txt"),text);}catch(Exception ex){throw new IllegalStateException(ex);}
        System.out.println(text);mc.stop();
    }
}
