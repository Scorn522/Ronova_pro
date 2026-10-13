package dev.ronova.pro;

import java.lang.ref.WeakReference;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerPlayer;

/** Actual server BossBar slots have their own client duty; they are never Entity bindings. */
final class PresentationRecords {
    private record Address(UUID owner,UUID entry) { }
    private static final class Viewer {
        final UUID player;
        UUID challenge=UUID.randomUUID();
        net.minecraft.server.network.ServerGamePacketListenerImpl connection;
        String dimension;
        long scope,sequence=-1,last=-1;
        boolean absent;
        Viewer(ServerPlayer player){this.player=player.getUUID();}
    }
    private static final class Slot {
        final UUID operation=UUID.randomUUID(),entry;
        final ProRuntime.Subject owner;
        final WeakReference<ServerBossEvent> event;
        final Map<UUID,Viewer> viewers=new LinkedHashMap<>();
        final Set<ServerPlayer> players;
        final String intent;
        CompletableFuture<Void> ack;
        ServerBossEvent pending;
        boolean cleared,cancelled,publicationUnknown;
        String state="PRESENTATION_PENDING",reason="";
        Slot(ProRuntime runtime,ServerBossEvent event,ProRuntime.Subject owner){
            this.owner=owner;this.entry=runtime.presentationId(event);this.players=runtime.presentationPlayers(event);this.event=new WeakReference<>(event);pending=event;
            intent="PRESENTATION_INTENT\t"+operation+"\t"+owner.id+"\t"+entry+"\t"+runtime.session+"\trealm="+runtime.journal.realm;
        }
    }
    private record Publication(ServerPlayer player,net.minecraft.network.Connection connection,Module[] sources){}
    private final Map<UUID,List<Publication>> publications=new LinkedHashMap<>();
    private final ProRuntime runtime;
    private final Map<Address,Slot> slots=new LinkedHashMap<>();
    private final Map<UUID,Slot> operations=new LinkedHashMap<>();
    private final Map<ServerBossEvent,Slot> identities=new WeakIdentityMap<>();
    PresentationRecords(ProRuntime runtime){this.runtime=runtime;}
    Object[] controls(){return new Object[]{this,slots,operations,identities,publications};}
    void observe(ServerBossEvent event,ProRuntime.Subject owner){
        if(owner==null||runtime.presentationSubject(event)!=owner)return;
        Address address=new Address(owner.id,runtime.presentationId(event));Slot prior=slots.get(address);
        if(prior!=null){if(prior.event.get()!=event)throw new IllegalStateException("PRESENTATION_ACTUAL_IDENTITY_CHANGED");return;}
        if(slots.size()>=65536)throw new IllegalStateException("PRESENTATION_CAPACITY");
        // A shared bar spanning another server is not an exclusive slot of this runtime.
        for(ServerPlayer player:runtime.presentationPlayers(event))if(player.server!=runtime.server)throw new IllegalStateException("PRESENTATION_SHARED_SERVER");
        Slot slot=new Slot(runtime,event,owner);
        for(ServerPlayer player:runtime.presentationPlayers(event))slot.viewers.put(player.getUUID(),new Viewer(player));
        for(Publication publication:publications.getOrDefault(slot.entry,List.of()))applyPublication(slot,publication);
        slots.put(address,slot);operations.put(slot.operation,slot);identities.put(event,slot);
        runtime.presentationControls(controls());runtime.presentationControls(slot,slot.viewers,slot.event);
        slot.ack=runtime.journal.append(slot.intent);
        retire(slot);
    }
    private void applyPublication(Slot slot,Publication publication){
        if(publication.player()!=null&&runtime.presentationPublicationOwned(publication.sources(),slot.owner))slot.viewers.computeIfAbsent(publication.player().getUUID(),ignored->new Viewer(publication.player()));
        else slot.publicationUnknown=true;
    }
    void published(UUID entry,net.minecraft.network.Connection connection,ServerPlayer player,Module[] sources){
        if(player!=null&&player.server!=runtime.server)return;
        if(!publications.containsKey(entry)&&publications.size()>=65536)throw new IllegalStateException("PRESENTATION_PUBLICATION_CAPACITY");
        List<Publication> rows=publications.computeIfAbsent(entry,ignored->new ArrayList<>());
        Publication actual=new Publication(player,connection,sources==null?null:sources.clone());
        rows.removeIf(old->old.player()!=null&&player!=null&&old.connection()==connection);rows.add(actual);runtime.presentationControls(rows,actual,actual.sources());
        for(Slot slot:slots.values())if(slot.entry.equals(entry))applyPublication(slot,actual);
    }
    boolean bound(UUID owner,UUID entry){return slots.containsKey(new Address(owner,entry));}
    UUID clear(UUID owner,UUID entry){
        Slot slot=slots.get(new Address(owner,entry));
        if(slot==null)throw new UnsupportedOperationException("PRESENTATION_OWNER_ADAPTER_REQUIRED_FOR_ACTUAL_ENTRY");
        if(slot.cancelled)throw new IllegalStateException("PRESENTATION_OPERATION_CANCELLED");
        retire(slot);return slot.operation;
    }
    private void retire(Slot slot){
        ServerBossEvent event=slot.event.get();
        if(event==null){if(!slot.cleared){slot.reason="PRESENTATION_ACTUAL_OWNER_UNAVAILABLE";slot.state="PRESENTATION_PENDING";}return;}
        if(!runtime.presentationId(event).equals(slot.entry)||runtime.presentationPlayers(event)!=slot.players||runtime.presentationSubject(event)!=slot.owner){slot.cleared=false;slot.state="PRESENTATION_PENDING";slot.reason="PRESENTATION_OWNERSHIP_CHANGED";return;}
        try{
            for(ServerPlayer player:runtime.presentationPlayers(event)){
                if(player.server!=runtime.server)throw new IllegalStateException("PRESENTATION_SHARED_SERVER");
                slot.viewers.computeIfAbsent(player.getUUID(),ignored->new Viewer(player));
            }
            runtime.presentationControls(slot.viewers.values().toArray());
            slot.cleared=runtime.presentationClear(event,slot.players,slot.entry,!slot.publicationUnknown&&!runtime.presentationPublicationPending());
            if(slot.cleared){slot.pending=null;slot.reason="";}else slot.reason="PRESENTATION_ORIGINAL_SLOT_NOT_CLEARED";
        }catch(RuntimeException|LinkageError failure){slot.cleared=false;slot.reason="PRESENTATION_RETIREMENT:"+failure.getClass().getSimpleName();}
    }
    boolean denied(ServerBossEvent event){Slot slot=identities.get(event);return slot!=null&&slot.event.get()==event&&slot.entry.equals(runtime.presentationId(event))&&runtime.presentationPlayers(event)==slot.players&&(!slot.cancelled||slot.owner.terminal)&&runtime.presentationSubject(event)==slot.owner;}
    void policies(ServerPlayer player,Map<UUID,Integer> desired){
        if(runtime.presentationPublicationPending())return;
        for(Slot slot:slots.values()){
            ServerBossEvent event=slot.event.get();
            if(!slot.publicationUnknown&&(!slot.cancelled||slot.owner.terminal)&&event!=null&&slot.entry.equals(runtime.presentationId(event))
                    &&runtime.presentationPlayers(event)==slot.players&&runtime.presentationSubject(event)==slot.owner&&slot.viewers.containsKey(player.getUUID()))desired.put(slot.entry,2);
        }
    }
    void tick(long tick){
        for(Slot slot:slots.values()){
            if(slot.cancelled)continue;
            slot.ack=runtime.journal.retryRejected(slot.intent,slot.ack);retire(slot);
            boolean clients=!slot.publicationUnknown;
            for(Viewer viewer:slot.viewers.values()){
                ServerPlayer player=runtime.server.getPlayerList().getPlayer(viewer.player);
                long scope=runtime.presentationScope(player);
                String dimension=player==null?null:player.serverLevel().dimension().location().toString();
                if(player==null||player.connection==null||!player.connection.connection.isConnected()||scope<=0){viewer.absent=false;clients=false;continue;}
                if(viewer.connection!=player.connection||viewer.scope!=scope||!Objects.equals(viewer.dimension,dimension)){
                    if(viewer.connection!=null&&viewer.connection.connection.isConnected())ProNetwork.forget(viewer.connection.player,runtime.session,slot.operation,viewer.challenge);
                    viewer.connection=player.connection;viewer.scope=scope;viewer.dimension=dimension;viewer.challenge=UUID.randomUUID();viewer.sequence=-1;viewer.last=-1;viewer.absent=false;
                }
                if(viewer.last<0||tick-viewer.last>20){viewer.absent=false;clients=false;}
                if(!viewer.absent)clients=false;
                if(tick%5==0)ProNetwork.presentationProbe(player,runtime.session,slot.operation,viewer.challenge,scope,dimension,slot.owner.id,slot.entry);
            }
            boolean current=slot.cleared&&clients;
            slot.state=current?"PRESENTATION_CLEAR_CURRENT":"PRESENTATION_PENDING";
            if(!clients&&slot.reason.isEmpty())slot.reason="CLIENT_PRESENTATION_UNCONFIRMED";else if(current)slot.reason="";
        }
    }
    void reply(ServerPlayer player,ProNetwork.PresentationReply reply,long tick){
        Slot slot=operations.get(reply.operation());if(slot==null||slot.cancelled||!runtime.session.equals(reply.session())||!slot.owner.id.equals(reply.owner())||!slot.entry.equals(reply.entry()))return;
        Viewer viewer=slot.viewers.get(player.getUUID());if(viewer==null||viewer.connection!=player.connection||viewer.scope!=reply.scope()||!Objects.equals(viewer.dimension,reply.dimension())
                ||!viewer.challenge.equals(reply.challenge())||reply.sequence()<=viewer.sequence||runtime.presentationScope(player)!=reply.scope()
                ||!player.serverLevel().dimension().location().toString().equals(reply.dimension()))return;
        viewer.sequence=reply.sequence();viewer.absent=reply.absent();viewer.last=tick;
    }
    private boolean current(Slot slot){
        if(slot.cancelled||slot.publicationUnknown||!slot.cleared||runtime.presentationPublicationPending())return false;
        ServerBossEvent event=slot.event.get();
        if(event!=null&&(runtime.presentationSubject(event)!=slot.owner||runtime.presentationPlayers(event)!=slot.players||!runtime.presentationId(event).equals(slot.entry)||runtime.presentationVisible(event)||!slot.players.isEmpty()))return false;
        long now=runtime.presentationTick();
        for(Viewer viewer:slot.viewers.values()){
            ServerPlayer player=runtime.server.getPlayerList().getPlayer(viewer.player);
            if(player==null||player.connection!=viewer.connection||player.connection==null||!player.connection.connection.isConnected()||!viewer.absent||viewer.last<0||now-viewer.last>20
                    ||runtime.presentationScope(player)!=viewer.scope||!Objects.equals(player.serverLevel().dimension().location().toString(),viewer.dimension)||!runtime.presentationTerminal(player,slot.entry))return false;
        }
        return true;
    }
    ProRuntime.Status query(UUID operation){
        Slot slot=operations.get(operation);if(slot==null)return null;
        String durable=slot.ack==null||!slot.ack.isDone()?"DURABLE_ACK_PENDING":slot.ack.isCompletedExceptionally()?"DURABLE_ACK_UNCONFIRMED":"DURABLE_ACK_CONFIRMED";
        boolean confirmed=current(slot);String state=slot.cancelled?slot.state:confirmed?"PRESENTATION_CLEAR_CURRENT":"PRESENTATION_PENDING";
        return new ProRuntime.Status(slot.operation,"PRESENTATION_CLEAR",state,confirmed?"":slot.reason.isEmpty()?"CURRENT_PRESENTATION_SCOPE_UNCONFIRMED":slot.reason,confirmed,
                Map.of("PRESENTATION_SERVER",slot.cleared?"ORIGINAL_SLOT_CLEARED":"PENDING","CLIENT_PRESENTATION",slot.publicationUnknown?"PUBLICATION_SOURCE_UNRESOLVED":slot.viewers.isEmpty()?"NO_OBSERVED_ORIGINAL_PLAYER_OR_CONNECTION_RECIPIENT":confirmed?"CURRENT_SCOPED_ABSENCE":"REQUIRED_PENDING","DURABLE_INTENT",durable),
                "ACTUAL_BASE_BOSS_EVENT_VISIBILITY_AND_PLAYER_SET;ORIGINAL_REMOVE_PACKET","ACTUAL_SERVER_BOSS_EVENT;CURRENT_CONNECTION_SESSION_SCOPE_AND_HUD_ENTRY",false);
    }
    List<UUID> operations(){return List.copyOf(operations.keySet());}
    List<String> groupGaps(ProRuntime.Subject owner){
        List<String> gaps=new ArrayList<>();for(Slot slot:slots.values())if(slot.owner==owner){
            if(!current(slot))gaps.add("PURE_PRESENTATION:"+slot.operation+":"+slot.state+":"+slot.reason);
            if(slot.ack==null||!slot.ack.isDone()||slot.ack.isCancelled()||slot.ack.isCompletedExceptionally())gaps.add("PURE_PRESENTATION_INTENT_UNCONFIRMED:"+slot.operation);
        }
        return List.copyOf(gaps);
    }
    boolean cancel(UUID operation){
        Slot slot=operations.get(operation);if(slot==null)return false;
        slot.cancelled=true;slot.pending=null;slot.state="FENCED";slot.reason="PRESENTATION_OPERATION_CANCELLED";
        for(Viewer viewer:slot.viewers.values())if(viewer.connection!=null&&viewer.connection.connection.isConnected())ProNetwork.forget(viewer.connection.player,runtime.session,slot.operation,viewer.challenge);
        runtime.journal.append("CANCEL\t"+slot.operation+"\t"+runtime.session);return true;
    }
    void close(){for(Slot slot:slots.values()){slot.pending=null;slot.state="FENCED";slot.reason="RUNTIME_STOPPED";}identities.clear();slots.clear();operations.clear();publications.clear();}
}
