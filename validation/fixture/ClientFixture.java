package dev.ronova.pro.validation;

import java.nio.file.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.*;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid="pro_fixture",value=Dist.CLIENT)
public final class ClientFixture {
    static boolean connecting, joined, sawTarget, sawNeighbor, sawTargetDisappear;
    static int ticks;
    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent e) {
        if(!Boolean.getBoolean("ronova.pro.validation") || e.phase!=TickEvent.Phase.END)return;
        if("audit-r6".equals(System.getProperty("ronova.pro.fixture"))) {ClientAuditR6Fixture.tick();return;}
        if("defense".equals(System.getProperty("ronova.pro.fixture"))) {ClientDefenseFixture.tick();return;}
        if("movement".equals(System.getProperty("ronova.pro.fixture"))) {ClientMovementFixture.tick();return;}
        if("fault-isolation".equals(System.getProperty("ronova.pro.fixture"))) { ClientFaultFixture.tick();return; }
        if("mod-group".equals(System.getProperty("ronova.pro.fixture"))) { modGroupTick();return; }
        if("client-policy".equals(System.getProperty("ronova.pro.fixture"))) { policyTick();return; }
        var mc=Minecraft.getInstance();ticks++;
        if(ticks%200==0 && !joined) System.out.println("PRO_CLIENT_WAIT screen="+(mc.screen==null?"none":mc.screen.getClass().getName()));
        if(!connecting && mc.screen instanceof TitleScreen && ticks>20) {
            connecting=true;
            ConnectScreen.startConnecting(mc.screen,mc,new ServerAddress("127.0.0.1",25719),
                new ServerData("Pro isolated verification","127.0.0.1:25719",false),false);
        }
        if(mc.level!=null) {
            joined=true;boolean target=false;
            for(var entity:mc.level.entitiesForRendering()) {
                if(entity.getName().getString().equals("ProTarget")) { sawTarget=true;target=true; }
                if(entity.getName().getString().equals("ProNeighbor"))sawNeighbor=true;
            }
            if(sawTarget && !target)sawTargetDisappear=true;
        }
        if((joined && mc.level==null) || ticks>4000) {
            try {
                Files.writeString(Path.of("client-result.json"),
                    "{\"joined\":"+joined+",\"sawTarget\":"+sawTarget+",\"sawNeighbor\":"+sawNeighbor+",\"sawTargetDisappear\":"+sawTargetDisappear+"}");
            }catch(Exception ex){throw new RuntimeException(ex);}
            mc.stop();
        }
    }
    private static void modGroupTick() {
        Minecraft mc=Minecraft.getInstance();
        ticks++;
        if(ticks%20==0)try {Files.writeString(Path.of("mod-group-client-ticks.txt"),Integer.toString(ticks));}
        catch(java.io.IOException unavailable) {throw new IllegalStateException(unavailable);}
        if(!connecting&&mc.screen instanceof TitleScreen&&mc.getOverlay()==null) {
            connecting=true;
            ConnectScreen.startConnecting(mc.screen,mc,new ServerAddress("127.0.0.1",25894),
                    new ServerData("Own mod group fixture","127.0.0.1:25894",false),false);
        }
        if(!joined&&mc.level!=null) {
            joined=true;
            try {Files.writeString(Path.of("mod-group-client-joined.txt"),"joined");}
            catch(java.io.IOException unavailable) {throw new IllegalStateException(unavailable);}
        }
    }
    private static final Path POLICY_EXCHANGE=Path.of("..","exchange").toAbsolutePath().normalize();
    private static Object firstConnection,dimensionPlayer,observedLoginConnection;
    private static boolean reconnecting,finished;
    private static final java.util.List<String> policyPasses=new java.util.ArrayList<>();
    private static String acknowledged="";
    private static void policyAck(String name)throws java.io.IOException {
        if(acknowledged.equals(name))return;
        acknowledged=name;policyPasses.add(name);Files.createDirectories(POLICY_EXCHANGE);
        Files.writeString(POLICY_EXCHANGE.resolve("client-ack.txt"),name);
    }
    private static void policyTick() {
        if(finished)return;
        Minecraft mc=Minecraft.getInstance();ticks++;
        try {
            if(ticks>12000)throw new AssertionError("client policy timeout; ack="+acknowledged);
            if(Files.exists(POLICY_EXCHANGE.resolve("server-failed.txt")))throw new AssertionError(Files.readString(POLICY_EXCHANGE.resolve("server-failed.txt")));
            if(ticks%100==0&&mc.level==null)System.out.println("PRO_CLIENT_POLICY_CONNECT screen="+(mc.screen==null?"null":mc.screen.getClass().getName()));
            if(Boolean.getBoolean("ronova.pro.validation.networkDiagnostics")&&mc.screen instanceof ConnectScreen)for(var field:mc.screen.getClass().getDeclaredFields())if(field.getType()==net.minecraft.network.Connection.class) {
                field.setAccessible(true);var login=(net.minecraft.network.Connection)field.get(mc.screen);
                if(login!=null&&login.channel()!=null&&observedLoginConnection!=login) {
                    observedLoginConnection=login;
                    System.out.println("PRO_LOGIN_CHANNEL active="+login.isConnected()+" autoRead="+login.channel().config().isAutoRead()+" pipeline="+login.channel().pipeline().names());
                    login.channel().eventLoop().execute(()-> {
                        login.channel().pipeline().addFirst("fixture-login-bytes",new io.netty.channel.ChannelInboundHandlerAdapter() {
                            int count;
                            @Override public void channelRead(io.netty.channel.ChannelHandlerContext ctx,Object msg)throws Exception {
                                if(count++<6)System.out.println("PRO_LOGIN_BYTES "+(msg instanceof io.netty.buffer.ByteBuf buf?buf.readableBytes():msg.getClass().getName()));
                                ctx.fireChannelRead(msg);
                            }
                        });
                        login.channel().pipeline().addBefore("packet_handler","fixture-login-packets",new io.netty.channel.ChannelInboundHandlerAdapter() {
                            int count;
                            @Override public void channelRead(io.netty.channel.ChannelHandlerContext ctx,Object msg)throws Exception {
                                if(count++<8)System.out.println("PRO_LOGIN_PACKET "+msg.getClass().getName());ctx.fireChannelRead(msg);
                            }
                            @Override public void exceptionCaught(io.netty.channel.ChannelHandlerContext ctx,Throwable ex)throws Exception {
                                System.out.println("PRO_LOGIN_EXCEPTION "+ex);ex.printStackTrace();ctx.fireExceptionCaught(ex);
                            }
                        });
                        login.channel().eventLoop().schedule(()-> {
                            try {
                                Class<?> nio=Class.forName("io.netty.channel.nio.AbstractNioChannel");var keyField=nio.getDeclaredField("selectionKey");keyField.setAccessible(true);
                                var key=(java.nio.channels.SelectionKey)keyField.get(login.channel());
                                System.out.println("PRO_LOGIN_LATE_KEY autoRead="+login.channel().config().isAutoRead()+" active="+login.isConnected()+" valid="+key.isValid()+" interest="+(key.isValid()?key.interestOps():-1));
                            } catch(Throwable ex) {System.out.println("PRO_LOGIN_LATE_KEY_FAILURE "+ex);}
                        },5,java.util.concurrent.TimeUnit.SECONDS);
                        System.out.println("PRO_LOGIN_OBSERVERS_INSTALLED autoRead="+login.channel().config().isAutoRead());
                    });
                }
                if(login!=null&&ticks%100==0)System.out.println("PRO_LOGIN_STATUS autoRead="+(login.channel()!=null&&login.channel().config().isAutoRead())+" received="+login.getAverageReceivedPackets()+" sent="+login.getAverageSentPackets());
            }
            if(mc.screen instanceof net.minecraft.client.gui.screens.DisconnectedScreen) {
                String reason="";
                for(var field:mc.screen.getClass().getDeclaredFields())if(net.minecraft.network.chat.Component.class.isAssignableFrom(field.getType())) {
                    field.setAccessible(true);var text=(net.minecraft.network.chat.Component)field.get(mc.screen);reason+=field.getName()+"="+(text==null?"null":text.getString())+";";
                }
                throw new AssertionError("actual disconnected screen: "+reason);
            }
            if(mc.screen instanceof net.minecraft.client.gui.screens.AccessibilityOnboardingScreen) { mc.setScreen(new TitleScreen()); }
            if(!connecting&&mc.level==null&&mc.screen instanceof TitleScreen&&ticks>20) {
                connecting=true;
                ConnectScreen.startConnecting(mc.screen,mc,new ServerAddress("127.0.0.1",25890),
                    new ServerData("Ronova isolated client policy","127.0.0.1:25890",false),false);
            }
            if(acknowledged.equals("logout-started")&&mc.level==null&&mc.getConnection()==null&&Files.exists(POLICY_EXCHANGE.resolve("server.properties"))) {
                var end=new java.util.Properties();try(var reader=Files.newBufferedReader(POLICY_EXCHANGE.resolve("server.properties"))) { end.load(reader); }
                if("done".equals(end.getProperty("phase"))) {
                    policyAck("done");finished=true;Files.writeString(Path.of("client-policy-client-result.txt"),"CLIENT_POLICY_CLIENT_PASS\n"+String.join("\n",policyPasses));mc.stop();
                }
                return;
            }
            if(mc.level==null||mc.getConnection()==null||ticks%5!=0||!Files.exists(POLICY_EXCHANGE.resolve("server.properties")))return;
            if(firstConnection==null)firstConnection=mc.getConnection().getConnection();
            java.util.Properties p=new java.util.Properties();
            try(var reader=Files.newBufferedReader(POLICY_EXCHANGE.resolve("server.properties"))) { p.load(reader); }
            String phase=p.getProperty("phase","");
            int id=Integer.parseInt(p.getProperty("targetId")),other=Integer.parseInt(p.getProperty("neighbourId"));
            java.util.UUID old=java.util.UUID.fromString(p.getProperty("targetUuid"));
            java.util.UUID boss=java.util.UUID.fromString(p.getProperty("bossId"));
            var bossRows=((dev.ronova.pro.mixin.ClientPresentation.BossAccess)mc.gui.getBossOverlay()).pro$events();
            var target=mc.level.getEntity(id);
            boolean guarded=dev.ronova.pro.ClientPresence.removalDenied(id,old);
            if(ticks%100==0)System.out.println("PRO_CLIENT_POLICY_WAIT phase="+phase+" target="+(target!=null)+" guarded="+guarded+" state="+dev.ronova.pro.ClientPresence.policyDiagnostic());
            if(phase.equals("ready")&&target!=null&&target.getUUID().equals(old)&&mc.level.getEntity(other)!=null&&guarded&&bossRows.containsKey(boss))policyAck("ready");
            else if(phase.equals("mixed")&&target!=null&&target.getUUID().equals(old)&&mc.level.getEntity(other)==null&&guarded&&bossRows.containsKey(boss)) {
                policyPasses.add("EXACT_PROTECTED_BOSS_REMOVAL_REFUSED");policyAck("mixed");
            }
            else if(phase.equals("revoked")&&target==null&&!guarded&&!bossRows.containsKey(boss)) {
                policyPasses.add("BOSS_REMOVAL_RESUMED_AFTER_POLICY_REVOKE");policyAck("revoked");
            }
            else if(phase.equals("reused")||phase.equals("reconnect")) {
                java.util.UUID current=java.util.UUID.fromString(p.getProperty("newUuid"));
                boolean currentProtected=target!=null&&target.getUUID().equals(current)&&!guarded
                    &&dev.ronova.pro.ClientPresence.removalDenied(id,current);
                if(phase.equals("reused")&&currentProtected)policyAck("reused");
                else if(phase.equals("reconnect")&&!reconnecting&&currentProtected) {
                    reconnecting=true;
                    mc.getConnection().getConnection().disconnect(net.minecraft.network.chat.Component.literal("fixture connection rotation"));
                    mc.clearLevel(new TitleScreen());connecting=false;
                } else if(phase.equals("reconnect")&&reconnecting&&currentProtected&&mc.getConnection().getConnection()!=firstConnection)policyAck("rejoined");
            } else if(phase.equals("player-protected")&&mc.player!=null&&dev.ronova.pro.ClientPresence.removalDenied(mc.player.getId(),mc.player.getUUID())) {
                float before=mc.player.getHealth();mc.player.setHealth(Math.max(0.1f,before-5));
                if(mc.player.getHealth()!=before)throw new AssertionError("local protected health mutated before next client tick");
                mc.setScreen(new DeathScreen(net.minecraft.network.chat.Component.literal("fixture local death"),false));
                if(mc.screen instanceof DeathScreen)throw new AssertionError("protected player death screen opened");
                policyPasses.add("LOCAL_HEALTH_AND_DEATH_UI_WRITE_REFUSED");
                policyAck("player-protected");
            } else if(phase.equals("dimension")&&mc.player!=null&&mc.level.dimension().location().toString().equals(p.getProperty("dimension"))
                    &&dev.ronova.pro.ClientPresence.removalDenied(mc.player.getId(),mc.player.getUUID())) {
                dimensionPlayer=mc.player;policyAck("dimension");
            } else if(phase.equals("respawn")&&mc.player!=null&&mc.player!=dimensionPlayer
                    &&mc.level.dimension().location().toString().equals(p.getProperty("dimension"))
                    &&dev.ronova.pro.ClientPresence.removalDenied(mc.player.getId(),mc.player.getUUID())) { policyAck("respawn");dimensionPlayer=mc.player; }
            else if(phase.equals("same-dimension-respawn")&&mc.player!=null&&mc.player!=dimensionPlayer
                    &&dev.ronova.pro.ClientPresence.removalDenied(mc.player.getId(),mc.player.getUUID()))policyAck("same-dimension-respawn");
            else if(phase.equals("protected-logout")&&mc.player!=null&&dev.ronova.pro.ClientPresence.removalDenied(mc.player.getId(),mc.player.getUUID())) {
                policyAck("logout-started");mc.getConnection().getConnection().disconnect(net.minecraft.network.chat.Component.literal("fixture protected logout"));mc.clearLevel(new TitleScreen());connecting=true;
            }
            else if(phase.equals("player-released")&&mc.player!=null&&!dev.ronova.pro.ClientPresence.removalDenied(mc.player.getId(),mc.player.getUUID()))policyAck("player-released");
            else if(phase.equals("done")) {
                policyAck("done");finished=true;
                Files.writeString(Path.of("client-policy-client-result.txt"),"CLIENT_POLICY_CLIENT_PASS\n"+String.join("\n",policyPasses));
                mc.stop();
            }
        } catch(Throwable failure) {
            finished=true;
            try { Files.createDirectories(POLICY_EXCHANGE);Files.writeString(POLICY_EXCHANGE.resolve("client-failed.txt"),failure.toString());
                Files.writeString(Path.of("client-policy-client-result.txt"),"FAILED\n"+failure+"\n"+String.join("\n",policyPasses)); }
            catch(Exception secondary) { failure.addSuppressed(secondary); }
            failure.printStackTrace();mc.stop();
        }
    }

}
