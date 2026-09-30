package dev.ronova.pro;

import java.util.UUID;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.*;
import net.minecraftforge.network.simple.SimpleChannel;

public final class ProNetwork {
    static final String WIRE_VERSION="r3-client-4";
    static final SimpleChannel CHANNEL=NetworkRegistry.newSimpleChannel(new ResourceLocation("ronova_pro","presence"),
            ()->WIRE_VERSION,v->v.equals(WIRE_VERSION)||v.equals(NetworkRegistry.ABSENT),v->v.equals(WIRE_VERSION)||v.equals(NetworkRegistry.ABSENT));
    public record Probe(UUID session,UUID operation,UUID challenge,String dimension,int entityId,UUID entityUuid) {}
    record Reply(UUID session,UUID operation,UUID challenge,long sequence,boolean absent) {}
    record Forget(UUID session,UUID operation,UUID challenge) {}
    /**
     * The actual protection policy as the server applies it, for one exact object. This is not a presence
     * observation: presence says whether an entity was seen, policy says whether its removal is allowed.
     */
    record Protection(UUID session,long scope,String dimension,long revision,int entityId,UUID entityUuid,boolean protectedNow) {}
    record HudPolicy(UUID session,long scope,String dimension,long revision,UUID hud,boolean protectedNow,boolean terminal) {}
    record Terminal(UUID session,long scope,String dimension,long revision,int entityId,UUID entityUuid,boolean terminal) {}
    record PolicyReset(UUID session,long scope,String dimension) {}
    record ModGroup(UUID session,String ids) {}
    private ProNetwork() {}
    public static void init() {
        CHANNEL.messageBuilder(Probe.class,0,NetworkDirection.PLAY_TO_CLIENT)
            .encoder((p,b)-> { b.writeUUID(p.session);b.writeUUID(p.operation);b.writeUUID(p.challenge);b.writeUtf(p.dimension,256);b.writeVarInt(p.entityId);b.writeUUID(p.entityUuid); })
            .decoder(b->new Probe(b.readUUID(),b.readUUID(),b.readUUID(),b.readUtf(256),b.readVarInt(),b.readUUID()))
            .consumerMainThread((p,c)->DistExecutor.unsafeRunWhenOn(Dist.CLIENT,()->()->ClientPresence.watch(p,c.get().getNetworkManager()))).add();
        CHANNEL.messageBuilder(Reply.class,1,NetworkDirection.PLAY_TO_SERVER)
            .encoder((p,b)-> {b.writeUUID(p.session);b.writeUUID(p.operation);b.writeUUID(p.challenge);b.writeLong(p.sequence);b.writeBoolean(p.absent);})
            .decoder(b->new Reply(b.readUUID(),b.readUUID(),b.readUUID(),b.readLong(),b.readBoolean()))
            .consumerMainThread((p,c)-> {
                ServerPlayer player=c.get().getSender();
                if(player!=null) {
                    ProRuntime r=ProRuntime.get(player.server);
                    if(r!=null)r.reply(player,p.session,p.operation,p.challenge,p.sequence,p.absent);
                }
            }).add();
        CHANNEL.messageBuilder(Forget.class,2,NetworkDirection.PLAY_TO_CLIENT)
            .encoder((p,b)->{b.writeUUID(p.session);b.writeUUID(p.operation);b.writeUUID(p.challenge);})
            .decoder(b->new Forget(b.readUUID(),b.readUUID(),b.readUUID()))
            .consumerMainThread((p,c)->DistExecutor.unsafeRunWhenOn(Dist.CLIENT,()->()->ClientPresence.forget(p,c.get().getNetworkManager()))).add();
        CHANNEL.messageBuilder(Protection.class,3,NetworkDirection.PLAY_TO_CLIENT)
            .encoder((p,b)->{b.writeUUID(p.session);b.writeLong(p.scope);b.writeUtf(p.dimension,256);b.writeLong(p.revision);b.writeVarInt(p.entityId);b.writeUUID(p.entityUuid);b.writeBoolean(p.protectedNow);})
            .decoder(b->new Protection(b.readUUID(),b.readLong(),b.readUtf(256),b.readLong(),b.readVarInt(),b.readUUID(),b.readBoolean()))
            .consumerMainThread((p,c)->DistExecutor.unsafeRunWhenOn(Dist.CLIENT,()->()->ClientPresence.policy(p,c.get().getNetworkManager()))).add();
        CHANNEL.messageBuilder(PolicyReset.class,4,NetworkDirection.PLAY_TO_CLIENT)
            .encoder((p,b)->{b.writeUUID(p.session);b.writeLong(p.scope);b.writeUtf(p.dimension,256);})
            .decoder(b->new PolicyReset(b.readUUID(),b.readLong(),b.readUtf(256)))
            .consumerMainThread((p,c)->DistExecutor.unsafeRunWhenOn(Dist.CLIENT,()->()->ClientPresence.reset(p,c.get().getNetworkManager()))).add();
        CHANNEL.messageBuilder(Terminal.class,5,NetworkDirection.PLAY_TO_CLIENT)
            .encoder((p,b)->{b.writeUUID(p.session);b.writeLong(p.scope);b.writeUtf(p.dimension,256);b.writeLong(p.revision);b.writeVarInt(p.entityId);b.writeUUID(p.entityUuid);b.writeBoolean(p.terminal);})
            .decoder(b->new Terminal(b.readUUID(),b.readLong(),b.readUtf(256),b.readLong(),b.readVarInt(),b.readUUID(),b.readBoolean()))
            .consumerMainThread((p,c)->DistExecutor.unsafeRunWhenOn(Dist.CLIENT,()->()->ClientPresence.terminal(p,c.get().getNetworkManager()))).add();

        CHANNEL.messageBuilder(HudPolicy.class,6,NetworkDirection.PLAY_TO_CLIENT)
            .encoder((p,b)->{b.writeUUID(p.session);b.writeLong(p.scope);b.writeUtf(p.dimension,256);b.writeLong(p.revision);b.writeUUID(p.hud);b.writeBoolean(p.protectedNow);b.writeBoolean(p.terminal);})
            .decoder(b->new HudPolicy(b.readUUID(),b.readLong(),b.readUtf(256),b.readLong(),b.readUUID(),b.readBoolean(),b.readBoolean()))
            .consumerMainThread((p,c)->DistExecutor.unsafeRunWhenOn(Dist.CLIENT,()->()->ClientPresence.hudPolicy(p,c.get().getNetworkManager()))).add();
        CHANNEL.messageBuilder(ModGroup.class,7,NetworkDirection.PLAY_TO_CLIENT)
            .encoder((p,b)->{b.writeUUID(p.session);b.writeUtf(p.ids,2048);})
            .decoder(b->new ModGroup(b.readUUID(),b.readUtf(2048)))
            .consumerMainThread((p,c)->DistExecutor.unsafeRunWhenOn(Dist.CLIENT,()->()->ClientPresence.modGroup(p,c.get().getNetworkManager()))).add();

    }
    static void probe(ServerPlayer p,UUID session,UUID op,UUID challenge,String dim,int id,UUID uuid) {
        if(CHANNEL.isRemotePresent(p.connection.connection))
            CHANNEL.send(PacketDistributor.PLAYER.with(()->p),new Probe(session,op,challenge,dim,id,uuid));
    }
    static void reply(Probe p,long sequence,boolean absent) {
        CHANNEL.sendToServer(new Reply(p.session,p.operation,p.challenge,sequence,absent));
    }
    static void forget(ServerPlayer p,UUID session,UUID op,UUID challenge) {
        if(CHANNEL.isRemotePresent(p.connection.connection))
            CHANNEL.send(PacketDistributor.PLAYER.with(()->p),new Forget(session,op,challenge));
    }
    static boolean present(ServerPlayer p) {
        return p!=null&&CHANNEL.isRemotePresent(p.connection.connection);
    }
    static boolean reset(ServerPlayer p,UUID session,long scope,String dimension) {
        if(!present(p)||session==null||scope<=0||dimension==null)return false;
        CHANNEL.send(PacketDistributor.PLAYER.with(()->p),new PolicyReset(session,scope,dimension));return true;
    }
    static boolean protection(ServerPlayer p,UUID session,long scope,String dimension,long revision,int entityId,UUID entityUuid,boolean protectedNow) {
        if(!present(p)||session==null||scope<=0||dimension==null||revision<=0||entityUuid==null)return false;
        CHANNEL.send(PacketDistributor.PLAYER.with(()->p),new Protection(session,scope,dimension,revision,entityId,entityUuid,protectedNow));return true;
    }
    static boolean terminal(ServerPlayer player,UUID session,long scope,String dimension,long revision,int id,UUID uuid,boolean terminal) {
        if(!present(player)||session==null||scope<=0||dimension==null||revision<=0||uuid==null)return false;
        CHANNEL.send(PacketDistributor.PLAYER.with(()->player),new Terminal(session,scope,dimension,revision,id,uuid,terminal));return true;
    }

    static boolean hud(ServerPlayer player,UUID session,long scope,String dimension,long revision,UUID hud,boolean protect,boolean terminal) {
        if(!present(player)||hud==null)return false;
        CHANNEL.send(PacketDistributor.PLAYER.with(()->player),new HudPolicy(session,scope,dimension,revision,hud,protect,terminal));return true;
    }
    static void modGroup(ServerPlayer player,UUID session,String ids) {
        if(present(player)&&session!=null&&!ids.isBlank())
            CHANNEL.send(PacketDistributor.PLAYER.with(()->player),new ModGroup(session,ids));
    }

}
