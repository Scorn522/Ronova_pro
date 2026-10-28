package dev.ronova.pro;

import java.util.UUID;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.*;
import net.minecraftforge.network.simple.SimpleChannel;

public final class ProNetwork {
    static final String WIRE_VERSION="v1.5-weapon-panel-2";
    static final SimpleChannel CHANNEL=NetworkRegistry.newSimpleChannel(new ResourceLocation("ronova_pro","presence"),
            ()->WIRE_VERSION,v->v.equals(WIRE_VERSION)||v.equals(NetworkRegistry.ABSENT),v->v.equals(WIRE_VERSION)||v.equals(NetworkRegistry.ABSENT));
    public record Probe(UUID session,UUID operation,UUID challenge,String dimension,int entityId,UUID entityUuid) {}
    record Reply(UUID session,UUID operation,UUID challenge,long sequence,boolean absent) {}
    record PresentationProbe(UUID session,UUID operation,UUID challenge,long scope,String dimension,UUID owner,UUID entry) {}
    record PresentationReply(UUID session,UUID operation,UUID challenge,long scope,String dimension,UUID owner,UUID entry,long sequence,boolean absent) {}
    record Forget(UUID session,UUID operation,UUID challenge) {}
    /**
     * The actual protection policy as the server applies it, for one exact object. This is not a presence
     * observation: presence says whether an entity was seen, policy says whether its removal is allowed.
     */
    record Protection(UUID session,long scope,String dimension,long revision,int entityId,UUID entityUuid,boolean protectedNow,boolean noHeal) {}
    record HudPolicy(UUID session,long scope,String dimension,long revision,UUID hud,boolean protectedNow,boolean terminal) {}
    record Terminal(UUID session,long scope,String dimension,long revision,int entityId,UUID entityUuid,boolean terminal) {}
    record PolicyReset(UUID session,long scope,String dimension) {}
    record ModGroup(UUID session,long generation,String ids,String returns,boolean protect) {
        ModGroup(UUID session,String ids,String returns,boolean protect){this(session,0,ids,returns,protect);}
    }
    record ModGroupResult(UUID session,long generation,String ids,boolean protect,long sequence,String state) {}
    record WorldArea(UUID rule,BlockPos low,BlockPos high,boolean clock,long gameTime) {
        boolean contains(BlockPos pos){return pos.getX()>=low.getX()&&pos.getX()<=high.getX()&&pos.getY()>=low.getY()&&pos.getY()<=high.getY()&&pos.getZ()>=low.getZ()&&pos.getZ()<=high.getZ();}
    }
    record WorldBody(int id,UUID uuid) {}
    record WorldPolicy(UUID session,long scope,String dimension,long revision,long gameTime,long dayTime,boolean daylight,boolean clock,int page,int pages,List<WorldArea> areas,List<WorldBody> bodies) {}
    private ProNetwork() {}
    record WeaponOpen() {}
    record WeaponSwing() {}
    record WeaponRow(UUID key,String title,String state,int[] actions) {}
    record WeaponPanel(UUID session,long sequence,int tab,int page,int pages,boolean linkedStop,boolean clock,boolean host,List<WeaponRow> rows,String message) {}
    record WeaponRequest(UUID session,long sequence,int action,int tab,int page,int size,UUID selection) {}
    record WeaponResult(UUID session,long sequence,int page,int pages,String text) {}
    static void panel(ServerPlayer player,WeaponPanel panel){if(present(player))CHANNEL.send(PacketDistributor.PLAYER.with(()->player),panel);}
    static void weaponResult(ServerPlayer player,UUID session,long sequence,String text){
        if(!present(player))return;var parts=new java.util.ArrayList<String>();
        for(int start=0;start<text.length();){int end=Math.min(text.length(),start+24000);if(end<text.length()&&Character.isHighSurrogate(text.charAt(end-1)))end--;parts.add(text.substring(start,end));start=end;}
        if(parts.isEmpty())parts.add("");for(int page=0;page<parts.size();page++)CHANNEL.send(PacketDistributor.PLAYER.with(()->player),new WeaponResult(session,sequence,page,parts.size(),parts.get(page)));
    }
    static void weaponOpen(){CHANNEL.sendToServer(new WeaponOpen());}
    static void weaponSwing(){CHANNEL.sendToServer(new WeaponSwing());}
    static void weaponRequest(WeaponRequest request){CHANNEL.sendToServer(request);}
    private static WeaponPanel readPanel(FriendlyByteBuf b){
        UUID session=b.readUUID();long sequence=b.readLong();int tab=b.readVarInt(),page=b.readVarInt(),pages=b.readVarInt();boolean linked=b.readBoolean(),clock=b.readBoolean(),host=b.readBoolean();
        int count=b.readVarInt();if(count<0||count>8)throw new IllegalArgumentException("Invalid weapon row count");var rows=new java.util.ArrayList<WeaponRow>(count);
        for(int i=0;i<count;i++){UUID key=b.readUUID();String title=b.readUtf(512),state=b.readUtf(2048);int actions=b.readVarInt();if(actions<0||actions>8)throw new IllegalArgumentException("Invalid weapon action count");int[] values=new int[actions];for(int j=0;j<actions;j++)values[j]=b.readVarInt();rows.add(new WeaponRow(key,title,state,values));}
        return new WeaponPanel(session,sequence,tab,page,pages,linked,clock,host,List.copyOf(rows),b.readUtf(2000));
    }
    public static void init() {
        CHANNEL.messageBuilder(WeaponOpen.class,12,NetworkDirection.PLAY_TO_SERVER)
            .encoder((p,b)->{}).decoder(b->new WeaponOpen())
            .consumerMainThread((p,c)->{ServerPlayer player=c.get().getSender();if(player!=null)ProMod.openWeaponPanel(player);}).add();
        CHANNEL.messageBuilder(WeaponPanel.class,13,NetworkDirection.PLAY_TO_CLIENT)
            .encoder((p,b)->{b.writeUUID(p.session);b.writeLong(p.sequence);b.writeVarInt(p.tab);b.writeVarInt(p.page);b.writeVarInt(p.pages);b.writeBoolean(p.linkedStop);b.writeBoolean(p.clock);b.writeBoolean(p.host);b.writeVarInt(p.rows.size());for(WeaponRow row:p.rows){b.writeUUID(row.key);b.writeUtf(row.title,512);b.writeUtf(row.state,2048);b.writeVarInt(row.actions.length);for(int action:row.actions)b.writeVarInt(action);}b.writeUtf(p.message,2000);})
            .decoder(ProNetwork::readPanel)
            .consumerMainThread((p,c)->DistExecutor.unsafeRunWhenOn(Dist.CLIENT,()->()->ClientWeapon.panel(p,c.get().getNetworkManager()))).add();
        CHANNEL.messageBuilder(WeaponRequest.class,14,NetworkDirection.PLAY_TO_SERVER)
            .encoder((p,b)->{b.writeUUID(p.session);b.writeLong(p.sequence);b.writeVarInt(p.action);b.writeVarInt(p.tab);b.writeVarInt(p.page);b.writeVarInt(p.size);b.writeUUID(p.selection);})
            .decoder(b->new WeaponRequest(b.readUUID(),b.readLong(),b.readVarInt(),b.readVarInt(),b.readVarInt(),b.readVarInt(),b.readUUID()))
            .consumerMainThread((p,c)->{ServerPlayer player=c.get().getSender();if(player!=null)ProMod.weaponPacket(player,p);}).add();
        CHANNEL.messageBuilder(WeaponSwing.class,16,NetworkDirection.PLAY_TO_SERVER)
            .encoder((p,b)->{}).decoder(b->new WeaponSwing())
            .consumerMainThread((p,c)->{ServerPlayer player=c.get().getSender();if(player!=null)ProMod.swingPacket(player);}).add();
        CHANNEL.messageBuilder(WeaponResult.class,15,NetworkDirection.PLAY_TO_CLIENT)
            .encoder((p,b)->{b.writeUUID(p.session);b.writeLong(p.sequence);b.writeVarInt(p.page);b.writeVarInt(p.pages);b.writeUtf(p.text,24000);})
            .decoder(b->new WeaponResult(b.readUUID(),b.readLong(),b.readVarInt(),b.readVarInt(),b.readUtf(24000)))
            .consumerMainThread((p,c)->DistExecutor.unsafeRunWhenOn(Dist.CLIENT,()->()->ClientWeapon.result(p,c.get().getNetworkManager()))).add();
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
            .encoder((p,b)->{b.writeUUID(p.session);b.writeLong(p.scope);b.writeUtf(p.dimension,256);b.writeLong(p.revision);b.writeVarInt(p.entityId);b.writeUUID(p.entityUuid);b.writeBoolean(p.protectedNow);b.writeBoolean(p.noHeal);})
            .decoder(b->new Protection(b.readUUID(),b.readLong(),b.readUtf(256),b.readLong(),b.readVarInt(),b.readUUID(),b.readBoolean(),b.readBoolean()))
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
            .encoder((p,b)->{b.writeUUID(p.session);b.writeLong(p.generation);b.writeUtf(p.ids,2048);b.writeUtf(p.returns,32);b.writeBoolean(p.protect);})
            .decoder(b->new ModGroup(b.readUUID(),b.readLong(),b.readUtf(2048),b.readUtf(32),b.readBoolean()))
            .consumerMainThread((p,c)->DistExecutor.unsafeRunWhenOn(Dist.CLIENT,()->()->ClientPresence.modGroup(p,c.get().getNetworkManager()))).add();

        CHANNEL.messageBuilder(WorldPolicy.class,8,NetworkDirection.PLAY_TO_CLIENT)
            .encoder((p,b)->{
                b.writeUUID(p.session);b.writeLong(p.scope);b.writeUtf(p.dimension,256);b.writeLong(p.revision);b.writeLong(p.gameTime);b.writeLong(p.dayTime);b.writeBoolean(p.daylight);b.writeBoolean(p.clock);b.writeVarInt(p.page);b.writeVarInt(p.pages);
                b.writeCollection(p.areas,(buffer,area)->{buffer.writeUUID(area.rule);buffer.writeBlockPos(area.low);buffer.writeBlockPos(area.high);buffer.writeBoolean(area.clock);buffer.writeLong(area.gameTime);});
                b.writeCollection(p.bodies,(buffer,body)->{buffer.writeVarInt(body.id);buffer.writeUUID(body.uuid);});
            })
            .decoder(b->new WorldPolicy(b.readUUID(),b.readLong(),b.readUtf(256),b.readLong(),b.readLong(),b.readLong(),b.readBoolean(),b.readBoolean(),b.readVarInt(),b.readVarInt(),
                b.readList(buffer->new WorldArea(buffer.readUUID(),buffer.readBlockPos(),buffer.readBlockPos(),buffer.readBoolean(),buffer.readLong())),
                b.readList(buffer->new WorldBody(buffer.readVarInt(),buffer.readUUID()))))
            .consumerMainThread((p,c)->DistExecutor.unsafeRunWhenOn(Dist.CLIENT,()->()->ClientPresence.worldPolicy(p,c.get().getNetworkManager()))).add();

        CHANNEL.messageBuilder(PresentationProbe.class,9,NetworkDirection.PLAY_TO_CLIENT)
            .encoder((p,b)->{b.writeUUID(p.session);b.writeUUID(p.operation);b.writeUUID(p.challenge);b.writeLong(p.scope);b.writeUtf(p.dimension,256);b.writeUUID(p.owner);b.writeUUID(p.entry);})
            .decoder(b->new PresentationProbe(b.readUUID(),b.readUUID(),b.readUUID(),b.readLong(),b.readUtf(256),b.readUUID(),b.readUUID()))
            .consumerMainThread((p,c)->DistExecutor.unsafeRunWhenOn(Dist.CLIENT,()->()->ClientPresence.presentationWatch(p,c.get().getNetworkManager()))).add();
        CHANNEL.messageBuilder(PresentationReply.class,10,NetworkDirection.PLAY_TO_SERVER)
            .encoder((p,b)->{b.writeUUID(p.session);b.writeUUID(p.operation);b.writeUUID(p.challenge);b.writeLong(p.scope);b.writeUtf(p.dimension,256);b.writeUUID(p.owner);b.writeUUID(p.entry);b.writeLong(p.sequence);b.writeBoolean(p.absent);})
            .decoder(b->new PresentationReply(b.readUUID(),b.readUUID(),b.readUUID(),b.readLong(),b.readUtf(256),b.readUUID(),b.readUUID(),b.readLong(),b.readBoolean()))
            .consumerMainThread((p,c)->{ServerPlayer player=c.get().getSender();if(player!=null){ProRuntime runtime=ProRuntime.get(player.server);if(runtime!=null)runtime.presentationReply(player,p);}}).add();
        CHANNEL.messageBuilder(ModGroupResult.class,11,NetworkDirection.PLAY_TO_SERVER)
            .encoder((p,b)->{b.writeUUID(p.session);b.writeLong(p.generation);b.writeUtf(p.ids,2048);b.writeBoolean(p.protect);b.writeLong(p.sequence);b.writeUtf(p.state,4096);})
            .decoder(b->new ModGroupResult(b.readUUID(),b.readLong(),b.readUtf(2048),b.readBoolean(),b.readLong(),b.readUtf(4096)))
            .consumerMainThread((p,c)->{ServerPlayer player=c.get().getSender();if(player!=null){ProRuntime runtime=ProRuntime.get(player.server);if(runtime!=null)runtime.modGroupResult(player,p);}}).add();
    }
    static boolean presentationProbe(ServerPlayer player,UUID session,UUID operation,UUID challenge,long scope,String dimension,UUID owner,UUID entry){
        if(!present(player)||scope<=0)return false;
        CHANNEL.send(PacketDistributor.PLAYER.with(()->player),new PresentationProbe(session,operation,challenge,scope,dimension,owner,entry));return true;
    }
    static void presentationReply(PresentationProbe p,long sequence,boolean absent){
        CHANNEL.sendToServer(new PresentationReply(p.session,p.operation,p.challenge,p.scope,p.dimension,p.owner,p.entry,sequence,absent));
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
    static boolean protection(ServerPlayer p,UUID session,long scope,String dimension,long revision,int entityId,UUID entityUuid,boolean protectedNow,boolean noHeal) {
        if(!present(p)||session==null||scope<=0||dimension==null||revision<=0||entityUuid==null)return false;
        CHANNEL.send(PacketDistributor.PLAYER.with(()->p),new Protection(session,scope,dimension,revision,entityId,entityUuid,protectedNow,noHeal));return true;
    }
    static boolean terminal(ServerPlayer player,UUID session,long scope,String dimension,long revision,int id,UUID uuid,boolean terminal) {
        if(!present(player)||session==null||scope<=0||dimension==null||revision<=0||uuid==null)return false;
        CHANNEL.send(PacketDistributor.PLAYER.with(()->player),new Terminal(session,scope,dimension,revision,id,uuid,terminal));return true;
    }

    static boolean hud(ServerPlayer player,UUID session,long scope,String dimension,long revision,UUID hud,boolean protect,boolean terminal) {
        if(!present(player)||hud==null)return false;
        CHANNEL.send(PacketDistributor.PLAYER.with(()->player),new HudPolicy(session,scope,dimension,revision,hud,protect,terminal));return true;
    }
    static void modGroup(ServerPlayer player,UUID session,long generation,String ids,String returns,boolean protect) {
        if(present(player)&&session!=null&&!ids.isBlank())
            CHANNEL.send(PacketDistributor.PLAYER.with(()->player),new ModGroup(session,generation,ids,returns,protect));
    }
    static void modGroupResult(ModGroup message,String state){modGroupResult(message,0,state);}
    static void modGroupResult(ModGroup message,long sequence,String state){
        CHANNEL.sendToServer(new ModGroupResult(message.session(),message.generation(),message.ids(),message.protect(),sequence,state.length()>4096?state.substring(0,4096):state));
    }
    static boolean world(ServerPlayer player,UUID session,long scope,String dimension,long revision,WorldControl controls){
        if(!present(player))return false;
        var level=player.serverLevel();
        List<WorldArea> areas=controls.clientAreas(level);List<WorldBody> bodies=controls.clientBodies(level);
        int pageSize=2048,pages=(int)Math.max(1,Math.max(((long)areas.size()+pageSize-1)/pageSize,((long)bodies.size()+pageSize-1)/pageSize));
        long gameTime=level.getGameTime(),dayTime=level.getDayTime();boolean daylight=level.getGameRules().getBoolean(net.minecraft.world.level.GameRules.RULE_DAYLIGHT);
        for(int page=0;page<pages;page++){
            long offset=(long)page*pageSize;int a=(int)Math.min(offset,areas.size()),b=(int)Math.min(offset,bodies.size());
            CHANNEL.send(PacketDistributor.PLAYER.with(()->player),new WorldPolicy(session,scope,dimension,revision,gameTime,dayTime,daylight,controls.clock(level),page,pages,
                    areas.subList(a,(int)Math.min(offset+pageSize,areas.size())),bodies.subList(b,(int)Math.min(offset+pageSize,bodies.size()))));
        }
        return true;
    }

}
