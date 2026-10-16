package dev.ronova.pro;

import dev.ronova.pro.mixin.Access;
import dev.ronova.pro.persistence.JournalLineage;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import net.minecraft.world.entity.Entity;

/** Durable known-source scope. O01 and opaque callback effects retain their original contracts. */
final class RecoveryChain {
    private static final String PREFIX="CHAIN/1";
    private final ProRuntime runtime;
    private final Map<UUID,Work> work=new LinkedHashMap<>();
    private final Map<UUID,List<Work>> bySubject=new HashMap<>();
    private final LinkedHashSet<Work> active=new LinkedHashSet<>(),history=new LinkedHashSet<>();
    private final Map<UUID,Qualification> qualifications=new HashMap<>();
    private final ConcurrentLinkedQueue<UUID> qualificationChanges=new ConcurrentLinkedQueue<>();
    private final Set<UUID> queuedQualifications=ConcurrentHashMap.newKeySet();
    private record Qualification(ProRuntime.Subject authority,long generation,boolean terminal) { }
    private static final class Work {
        final UUID id,subject,session,entity,incarnation;
        final long generation;
        final String dimension,rootHash,intent;
        CompletableFuture<Void> ack;
        String state="WAIT_ORIGINAL_INTENT_ACK";
        List<String> remaining=List.of("INITIAL_QUERY_PENDING");
        long last=-1;
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
        for(UUID subject:bySubject.keySet())refreshQualification(subject);
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
        refreshQualification(item.subject);
        if(current(item)) { history.remove(item);active.add(item); }
        else { active.remove(item);history.add(item); }
    }
    void retireBody(UUID operation) { Work item=work.get(operation);if(item!=null)item.bodyRetired=true; }
    void acceptGroup(UUID operation,ProRuntime.Subject subject,String root) {
        if(work.containsKey(operation))return;
        UUID absent=new UUID(0,0);
        accept(operation,subject,"",absent,absent,root);
    }
    UUID operationFor(UUID subject) {
        refreshQualification(subject);
        var authority=runtime.subjects.get(subject);
        if(authority!=null&&authority.terminal)for(Work item:bySubject.getOrDefault(subject,List.of()))
            if(item.generation==authority.generation)return item.id;
        return null;
    }
    // Qualification gates may notify from another thread. Only tick/accept/query
    // ownership changes the rings; notification never enters a recovery query.
    void qualificationChanged(UUID subject) {
        if(queuedQualifications.add(subject))qualificationChanges.add(subject);
    }
    private void refreshQualification(UUID subject) {
        var authority=runtime.subjects.get(subject);
        long generation=authority==null?0:authority.generation;boolean terminal=authority!=null&&authority.terminal;
        Qualification previous=qualifications.get(subject);
        if(previous!=null&&previous.authority==authority&&previous.generation==generation&&previous.terminal==terminal)return;
        Qualification next=new Qualification(authority,generation,terminal);qualifications.put(subject,next);
        for(Work item:bySubject.getOrDefault(subject,List.of())) {
            boolean selected=next.terminal&&item.generation==next.generation;
            if(selected) { history.remove(item);active.add(item); }
            else { active.remove(item);history.add(item); }
        }
    }
    private boolean current(Work item) {
        var authority=runtime.subjects.get(item.subject);
        return authority!=null&&authority.terminal&&authority.generation==item.generation;
    }
    private static Work rotate(LinkedHashSet<Work> ring) {
        Iterator<Work> cursor=ring.iterator();Work item=cursor.next();cursor.remove();ring.add(item);return item;
    }
    void tick(long tick) {
        for(UUID subject;(subject=qualificationChanges.poll())!=null;) {
            queuedQualifications.remove(subject);refreshQualification(subject);
        }
        // Size the current-window share to a five-tick round, including at capacity.
        // Obsolete qualifications retain durable duties but cannot consume current leases.
        int budget=sampleBudget(active.size()),selected=0,available=active.size();
        while(selected<budget&&available-->0&&!active.isEmpty()) {
            Work item=rotate(active);
            if(!current(item)) { refreshQualification(item.subject);continue; }
            sample(item,tick);selected++;
        }
        int historical=Math.min(16,history.size());
        for(int i=0;i<historical&&!history.isEmpty();i++) {
            Work item=rotate(history);
            if(current(item)) { refreshQualification(item.subject);continue; }
            sample(item,tick);
        }
    }
    static int sampleBudget(int active) {return active==0?0:Math.max(1,(active+4)/5);}
    private void sample(Work item,long tick) {
        item.ack=runtime.journal.retryRejected(item.intent,item.ack);
        List<String> remaining=remaining(item);
        item.last=tick;
        item.remaining=List.copyOf(remaining);
        item.state=remaining.isEmpty()?"KNOWN_RECOVERY_CHAIN_SETTLED":"RECOVERY_SOURCE_PENDING";
    }
    // Live aggregator only: no stability window, success receipt, or invalidation receipt.
    // Queries retain live readback, but never qualify/discover work or create chain receipts.
    private List<String> remaining(Work item) {
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
        remaining.addAll(runtime.recoverySources.remaining(item.subject));
        for(UUID operation:operations(item)) {
            remaining.addAll(runtime.recoveryTasks.remaining(operation,item.subject));
            remaining.addAll(runtime.recoveryRecords.remaining(operation,item.subject));
            remaining.addAll(runtime.recoveryStorage.remaining(operation));
        }
        if(!runtime.journal.healthy())remaining.add("DURABLE_WRITER_UNAVAILABLE");
        return remaining;
    }
    private Set<UUID> operations(Work item) {
        Set<UUID> result=new LinkedHashSet<>();result.add(runtime.groupOperation(item.subject,item.id));
        Set<UUID> members=runtime.groupTargets(item.subject);
        for(UUID member:members)for(Work related:bySubject.getOrDefault(member,List.of()))
            if(members.size()>1||related.generation==item.generation)result.add(related.id);
        return result;
    }
    List<String> status(UUID operation) {
        Work item=work.get(operation);if(item==null)return List.of("NO_VERSIONED_KNOWN_RECOVERY_CHAIN");
        List<String> remaining=remaining(item);
        String visible=remaining.isEmpty()?"KNOWN_RECOVERY_CHAIN_SETTLED":"RECOVERY_SOURCE_PENDING";
        List<String> result=new ArrayList<>();
        result.add(visible.equals("KNOWN_RECOVERY_CHAIN_SETTLED")?"已知恢复链已处置":"恢复源仍待处理");
        result.add("CHAIN:"+visible+":operation="+item.id+":lastSample="+item.last);
        result.addAll(remaining);result.add("SCOPE:KNOWN_REGISTERED_RECOVERY;OPAQUE_LIFECYCLE_EFFECTS_UNCHANGED;O01_CONTRACT_UNCHANGED");
        return List.copyOf(result);
    }
    private static boolean confirmed(CompletableFuture<Void> ack) { return ack!=null&&ack.isDone()&&!ack.isCompletedExceptionally()&&!ack.isCancelled(); }
    private static String hash(String text) { return RecoveryImage.hash(text.getBytes(StandardCharsets.UTF_8)); }
    private static String text(String text) { return JournalLineage.encodeText(text); }
    private static String untext(String text) { return JournalLineage.decodeText(text); }
}
