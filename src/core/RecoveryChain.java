package dev.ronova.pro;

import dev.ronova.pro.mixin.Access;
import dev.ronova.pro.persistence.JournalLineage;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import net.minecraft.world.entity.Entity;

/** Durable known-source scope. O01 and opaque callback effects retain their original contracts. */
final class RecoveryChain {
    private static final String PREFIX="CHAIN/1";
    private final ProRuntime runtime;
    private final Map<UUID,Work> work=new LinkedHashMap<>();
    private final Map<UUID,List<Work>> bySubject=new HashMap<>();
    private long historyCursor;
    private static final class Work {
        final UUID id,subject,session,entity,incarnation;
        final long generation;
        final String dimension,rootHash,intent;
        CompletableFuture<Void> ack,receiptAck;
        String receipt,state="WAIT_ORIGINAL_INTENT_ACK",fingerprint="";
        List<String> remaining=List.of("INITIAL_QUERY_PENDING");
        long stable=-1,last=-1;
        boolean restarted,bodyRetired;
        Work(UUID id,UUID subject,long generation,UUID session,String dimension,UUID entity,UUID incarnation,String hash) {
            this.id=id;this.subject=subject;this.generation=generation;this.session=session;
            this.dimension=dimension;this.entity=entity;this.incarnation=incarnation;rootHash=hash;
            intent=PREFIX+"\tINTENT\t"+id+"\t"+subject+"\t"+generation+"\t"+session+"\t"+text(dimension)+"\t"+entity+"\t"+incarnation+"\t"+hash;
        }
    }
    RecoveryChain(ProRuntime runtime) {
        this.runtime=runtime;Map<UUID,String> roots=new HashMap<>();Map<UUID,Long> revoked=new HashMap<>();
        List<String> history=runtime.journal.history();
        for(String line:history)try {
            String[] row=line.split("\t",-1);
            if(row[0].equals("INTENT"))roots.put(UUID.fromString(row[1]),line);
            if(row[0].equals("GROUP/1")&&row[1].equals("STOP")&&(row.length==6||row.length==7))roots.put(UUID.fromString(row[3]),line);
            if(row[0].equals("REVOKE"))revoked.merge(UUID.fromString(row[1]),Long.parseLong(row[2]),Math::max);
        } catch(RuntimeException invalid) { runtime.journal.refuseWrites("INVALID_ROOT_RECOVERY_HISTORY"); }
        for(String line:history)if(line.startsWith("CHAIN/"))try {
            String[] row=line.split("\t",-1);
            if(!row[0].equals(PREFIX))throw new IllegalArgumentException("FUTURE_CHAIN_VERSION");
            if(row[1].equals("INTENT")) {
                if(row.length!=10)throw new IllegalArgumentException("CHAIN_ARITY");
                Work item=new Work(UUID.fromString(row[2]),UUID.fromString(row[3]),Long.parseLong(row[4]),UUID.fromString(row[5]),
                        untext(row[6]),UUID.fromString(row[7]),UUID.fromString(row[8]),row[9]);
                String original=roots.get(item.id);
                if(original==null||!hash(original).equals(item.rootHash)||!item.intent.equals(line)||item.generation<1)
                    throw new IllegalArgumentException("ORIGINAL_ROOT_INTENT_MISMATCH");
                Work prior=work.putIfAbsent(item.id,item);
                if(prior!=null&&!prior.intent.equals(item.intent))throw new IllegalArgumentException("CHAIN_KEY_REDEFINED");
                item.restarted=true;item.ack=CompletableFuture.completedFuture(null);
                var subject=runtime.subjects.computeIfAbsent(item.subject,ProRuntime.Subject::new);
                subject.generation=Math.max(subject.generation,Math.max(item.generation,revoked.getOrDefault(item.subject,0L)));
                subject.terminal=subject.generation==item.generation&&subject.generation>revoked.getOrDefault(item.subject,0L);
            } else if(!Set.of("RECEIPT","INVALIDATED").contains(row[1])||row.length!=6)
                throw new IllegalArgumentException("UNKNOWN_CHAIN_FACT");
        } catch(RuntimeException invalid) { runtime.journal.refuseWrites("CHAIN_HISTORY_QUERY_ONLY:"+invalid.getMessage()); }
        for(Work item:work.values())bySubject.computeIfAbsent(item.subject,key->new ArrayList<>()).add(item);
        for(String line:history)if(line.startsWith("SOURCE_UNKNOWN/"))try {
            String[] row=line.split("\t",-1);
            if(!row[0].equals("SOURCE_UNKNOWN/1")||row.length!=5)throw new IllegalArgumentException("UNKNOWN_SOURCE_PROTOCOL");
            var subject=runtime.subjects.get(UUID.fromString(row[1]));
            if(subject!=null)subject.unresolvedSources.add(row[4]);
        } catch(RuntimeException invalid) { runtime.journal.refuseWrites("UNKNOWN_SOURCE_HISTORY_QUERY_ONLY"); }
    }
    void accept(UUID operation,ProRuntime.Subject subject,String dimension,UUID entity,UUID incarnation,String root) {
        Work item=new Work(operation,subject.id,subject.generation,runtime.session,dimension,entity,incarnation,hash(root));
        if(work.putIfAbsent(operation,item)!=null)throw new IllegalStateException("CHAIN_OPERATION_ALREADY_EXISTS");
        bySubject.computeIfAbsent(item.subject,key->new ArrayList<>()).add(item);
        item.ack=runtime.journal.append(item.intent);
    }
    void retireBody(UUID operation) { Work item=work.get(operation);if(item!=null)item.bodyRetired=true; }
    void acceptGroup(UUID operation,ProRuntime.Subject subject,String root) {
        if(work.containsKey(operation))return;
        UUID absent=new UUID(0,0);
        accept(operation,subject,"",absent,absent,root);
    }
    UUID operationFor(UUID subject) {
        for(Work item:work.values())if(item.subject.equals(subject))return item.id;
        return null;
    }
    void tick(long tick) {
        List<Work> active=new ArrayList<>(),history=new ArrayList<>();
        for(Work item:work.values()) {
            var authority=runtime.subjects.get(item.subject);
            if(authority!=null&&authority.terminal&&authority.generation==item.generation)active.add(item);
            else history.add(item);
        }
        // Size the current-window share to a five-tick round, including at capacity.
        // Obsolete qualifications retain durable duties but cannot consume current leases.
        int budget=sampleBudget(active.size());
        active.sort(Comparator.comparingLong(item->item.last<0?tick-5:item.last));
        for(int i=0;i<budget;i++)sample(active.get(i),tick);
        for(int i=0;i<Math.min(16,history.size());i++)sample(history.get((int)Math.floorMod(historyCursor++,history.size())),tick);
    }
    static int sampleBudget(int active) {return active==0?0:Math.max(1,(active+4)/5);}
    private void sample(Work item,long tick) {
        item.ack=runtime.journal.retryRejected(item.intent,item.ack);
        List<String> remaining=remaining(item,true);
        String current=fingerprint(item);
        if(!remaining.isEmpty()||!current.equals(item.fingerprint)||item.last<0||tick<item.last||tick-item.last>10) {
            invalidateObservation(item,tick);
        }
        item.fingerprint=current;item.last=tick;item.remaining=List.copyOf(remaining);
        if(!remaining.isEmpty()) { item.state="RECOVERY_SOURCE_PENDING";return; }
        if(item.stable<0)item.stable=tick;
        if(tick-item.stable<20) { item.state="STABLE_WINDOW_PENDING";return; }
        if(item.receipt==null)item.receipt=PREFIX+"\tRECEIPT\t"+item.id+"\t"+runtime.session+"\t"+tick+"\tO02-KNOWN-v1+O05-DURABLE-v1:"+hash(current);
        item.receiptAck=item.receiptAck==null?runtime.journal.append(item.receipt):runtime.journal.retryRejected(item.receipt,item.receiptAck);
        item.state=confirmed(item.receiptAck)?"KNOWN_RECOVERY_CHAIN_SETTLED":"WAIT_CHAIN_RECEIPT_ACK";
    }
    private void invalidateObservation(Work item,long tick) {
        if(item.state.equals("KNOWN_RECOVERY_CHAIN_SETTLED"))runtime.journal.append(PREFIX+"\tINVALIDATED\t"+item.id+"\t"+runtime.session+"\t"+tick+"\tDEPENDENCIES_CHANGED");
        item.stable=-1;item.receiptAck=null;item.receipt=null;
    }
    // Queries retain live readback, but never qualify/discover work or create chain receipts.
    private List<String> remaining(Work item,boolean advance) {
        List<String> remaining=new ArrayList<>();
        var authority=runtime.subjects.get(item.subject);
        if(!confirmed(item.ack))remaining.add("ORIGINAL_CHAIN_INTENT_ACK_PENDING");
        if(!runtime.groupDurable(item.subject))remaining.add("GROUP_ORIGINAL_RECORD_ACK_PENDING");
        if(authority==null||!authority.terminal||authority.generation!=item.generation)remaining.add("QUALIFICATION_REVOKED_OR_CHANGED");
        if(!item.dimension.isEmpty()&&(item.restarted||item.bodyRetired)) {
            if(item.restarted&&!runtime.journal.processEnded(item.session))remaining.add("PRIOR_PROCESS_STILL_POSSIBLE_NO_REPLAY");
            boolean loaded=false;
            for(var level:runtime.server.getAllLevels())if(level.dimension().location().toString().equals(item.dimension)) {
                loaded=true;
                Access.Manager manager=(Access.Manager)((Access.Server)level).pro$manager();
                Access.Lookup lookup=(Access.Lookup)manager.pro$lookup();
                for(Object value:lookup.pro$ids().values())if(value instanceof Entity entity&&((Access.EntityState)entity).pro$uuid().equals(item.entity))
                    remaining.add("RESTART_LOCATION_OCCUPIED_REQUIRES_ACTUAL_SOURCE_BINDING");
                if(lookup.pro$uuids().containsKey(item.entity))remaining.add("RESTART_UUID_INDEX_PRESENT");
            }
            if(!loaded)remaining.add("ORIGINAL_WORLD_NOT_LOADED");
        }
        remaining.addAll(runtime.recoveryBodyGaps(item.id,item.subject));
        remaining.addAll(runtime.groupCodeGaps(item.subject));
        if(advance&&authority!=null&&authority.terminal&&authority.generation==item.generation&&confirmed(item.ack)) {
            runtime.recoveryTasks.qualify(authority,item.id);
            runtime.recoveryRecords.qualify(authority,item.id);
            runtime.recoveryStorage.discover(authority,item.id);
        }
        remaining.addAll(runtime.recoverySources.remaining(item.subject));
        for(UUID operation:operations(item)) {
            remaining.addAll(runtime.recoveryTasks.remaining(operation,item.subject));
            remaining.addAll(runtime.recoveryRecords.remaining(operation,item.subject));
            remaining.addAll(runtime.recoveryStorage.remaining(operation));
        }
        if(!runtime.journal.healthy())remaining.add("DURABLE_WRITER_UNAVAILABLE");
        return remaining;
    }
    private String fingerprint(Work item) {
        var authority=runtime.subjects.get(item.subject);
        StringBuilder key=new StringBuilder(authority==null?"missing":authority.generation+":"+authority.sourceRevision.get());
        key.append('|').append(runtime.recoverySources.dependencies(item.subject));
        key.append('|').append(runtime.groupDurable(item.subject));
        for(UUID operation:operations(item)) {
            key.append('|').append(operation).append(':').append(runtime.recoveryTasks.dependencies(operation,item.subject))
                .append(':').append(runtime.recoveryRecords.dependencies(operation,item.subject))
                .append(':').append(runtime.recoveryStorage.dependencies(operation));
        }
        return key.toString();
    }
    private Set<UUID> operations(Work item) {
        Set<UUID> result=new LinkedHashSet<>();result.add(runtime.groupOperation(item.subject,item.id));
        Set<UUID> members=runtime.groupTargets(item.subject);
        for(Work related:work.values())if(members.contains(related.subject)&&(members.size()>1||related.generation==item.generation))result.add(related.id);
        return result;
    }
    List<String> status(UUID operation) {
        Work item=work.get(operation);if(item==null)return List.of("NO_VERSIONED_KNOWN_RECOVERY_CHAIN");
        long now=Integer.toUnsignedLong(runtime.server.getTickCount());
        List<String> remaining=remaining(item,false);
        boolean current=remaining.isEmpty()&&now>=item.last&&item.last>=0&&now-item.last<=10
                &&fingerprint(item).equals(item.fingerprint);
        // A query may revoke evidence it actually sees fail, but it never creates or extends evidence.
        if(!current) {
            invalidateObservation(item,now);item.state="RECOVERY_SOURCE_PENDING";
        }
        String visible=item.state;
        if(visible.equals("KNOWN_RECOVERY_CHAIN_SETTLED")&&!confirmed(item.receiptAck))visible="WAIT_CHAIN_RECEIPT_ACK";
        List<String> result=new ArrayList<>();
        result.add(visible.equals("KNOWN_RECOVERY_CHAIN_SETTLED")?"已知恢复链已处置":"恢复源仍待处理");
        result.add("CHAIN:"+visible+":operation="+item.id+":stableSince="+item.stable+":lastSample="+item.last);
        result.addAll(remaining);result.add("SCOPE:KNOWN_REGISTERED_RECOVERY;OPAQUE_LIFECYCLE_EFFECTS_UNCHANGED;O01_CONTRACT_UNCHANGED");
        return List.copyOf(result);
    }
    private static boolean confirmed(CompletableFuture<Void> ack) { return ack!=null&&ack.isDone()&&!ack.isCompletedExceptionally()&&!ack.isCancelled(); }
    private static String hash(String text) { return RecoveryImage.hash(text.getBytes(StandardCharsets.UTF_8)); }
    private static String text(String text) { return JournalLineage.encodeText(text); }
    private static String untext(String text) { return JournalLineage.decodeText(text); }
}
