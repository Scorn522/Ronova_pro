package dev.ronova.pro;

import dev.ronova.pro.persistence.JournalLineage;
import java.io.*;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;

/** Bounded storage execution. Durable descriptors remain queryable independently of entity bindings. */
final class RecoveryStorage implements AutoCloseable {
    private static final String PREFIX="STORAGE/2";
    private static final String BACKEND="win32-ntfs-txf/2";
    private final ProRuntime runtime;
    private final RecoverySources sources;
    private final Path payloads;
    private final Map<UUID,Work> work=new ConcurrentSkipListMap<>();
    private final Map<Save,List<Work>> bySave=new ConcurrentHashMap<>();
    private final Map<UUID,List<Work>> bySubject=new ConcurrentHashMap<>();
    private final Map<UUID,List<Work>> byOperation=new ConcurrentHashMap<>();
    private final LinkedHashSet<Work> ready=new LinkedHashSet<>();
    private final NavigableSet<Work> deadlines=new TreeSet<>(Comparator.comparingLong((Work w)->w.scheduledAt).thenComparing(w->w.id));
    private final ConcurrentLinkedQueue<Work> schedulingChanges=new ConcurrentLinkedQueue<>(),confirmedReadbacks=new ConcurrentLinkedQueue<>();
    private final java.util.concurrent.atomic.AtomicInteger indexedWork=new java.util.concurrent.atomic.AtomicInteger();
    private final ThreadPoolExecutor worker;
    private volatile boolean closing;
    private String gap="";
    private final java.util.concurrent.atomic.AtomicLong changes=new java.util.concurrent.atomic.AtomicLong();
    record Status(UUID id,UUID operation,String state,String reason,boolean nativeReleased,boolean transientReleased) { }
    private record KnownSave(String code,long generation,String identity,String baseline,Set<List<RecoveryImage.Step>> paths) { }
    private record Save(UUID subject,long qualification,String source,String code,long generation) { }
    private record Descriptor(String protocol,UUID id,UUID operation,UUID subject,long qualification,String source,String code,
            long saveGeneration,String identity,String baseline,String expected,String preserved,UUID session,String backend,String origins) {
        String record() {
            return protocol+"\tINTENT\t"+id+"\t"+operation+"\t"+subject+"\t"+qualification+"\t"+
                    text(source)+"\t"+text(code)+"\t"+saveGeneration+"\t"+text(identity)+"\t"+
                    baseline+"\t"+expected+"\t"+preserved+"\t"+session+(protocol.equals("STORAGE/1")?"":"\t"+backend)+(origins==null?"":"\t"+origins);
        }
        static Descriptor parse(String[] p) {
            if(p[0].equals("STORAGE/1")?p.length!=14:p.length!=15&&p.length!=16)throw new IllegalArgumentException("STORAGE_DESCRIPTOR_ARITY");
            for(int i:new int[]{10,11,12})if(!p[i].matches("[0-9a-f]{64}"))throw new IllegalArgumentException("STORAGE_HASH_ENCODING");
            Descriptor d=new Descriptor(p[0],UUID.fromString(p[2]),UUID.fromString(p[3]),UUID.fromString(p[4]),Long.parseLong(p[5]),
                    untext(p[6]),untext(p[7]),Long.parseLong(p[8]),untext(p[9]),p[10],p[11],p[12],UUID.fromString(p[13]),p.length>=15?p[14]:"win32-ntfs-txf/1",p.length==16?p[15]:null);
            if(p.length>=15&&!d.backend.equals(BACKEND))throw new IllegalArgumentException("UNSUPPORTED_STORAGE_BACKEND");
            if(d.origins!=null)decodePaths(d.origins);
            if(!d.record().equals(String.join("\t",p)))throw new IllegalArgumentException("NONCANONICAL_STORAGE_DESCRIPTOR");
            return d;
        }
    }
    private static final class Work {
        final UUID id,operation,subject;
        final long qualification,saveGeneration;
        final String source,code;
        final Set<List<RecoveryImage.Step>> paths;
        volatile ProRuntime.Subject authority;
        RecoverySources.Snapshot snapshot;
        volatile Descriptor descriptor;
        Candidate candidate;
        volatile String state="WAIT_PREPARE",reason="";
        volatile boolean running,nativeReleased=true,transientReleased=true;
        volatile long retryAt;
        // Membership and deadline keys belong only to the server tick. Workers
        // publish registration, retirement and completion through schedulingChanges.
        long scheduledAt;
        boolean queuedReady,queuedDeadline,wakeAfterRun;
        Ticket ticket;
        volatile FileInputStream queryInput;
        volatile boolean queryClosePending;
        IOException queryCloseFailure;
        boolean originalIntent,commitPossible;
        volatile boolean effectClosed;
        volatile boolean preparing;
        CompletableFuture<Void> intentAck;
        String transaction;
        UUID transactionSession;
        volatile boolean queryNativeClosed=true;
        boolean restartRetry;
        long reboundGeneration;
        volatile boolean resourcesConfirmed;
        boolean readbackEverConfirmed;
        // Per-work decode cache; independent file identity and byte reads remain live.
        String decodedImageHash,decodedProjection;
        UUID supersededBy;
        volatile long lastQuery=-1;
        String preparedSources;
        Work(UUID id,UUID operation,ProRuntime.Subject authority,RecoverySources.Snapshot snapshot,Set<List<RecoveryImage.Step>> paths) {
            this.id=id;this.operation=operation;this.authority=authority;this.subject=authority.id;
            qualification=authority.generation;this.snapshot=snapshot;source=snapshot.source.locator;code=snapshot.source.code;saveGeneration=snapshot.generation;
            this.paths=paths;
        }
        Work(Descriptor descriptor) {
            this.descriptor=descriptor;id=descriptor.id;operation=descriptor.operation;subject=descriptor.subject;
            qualification=descriptor.qualification;source=descriptor.source;code=descriptor.code;saveGeneration=descriptor.saveGeneration;
            paths=descriptor.origins==null?null:Set.copyOf(decodePaths(descriptor.origins));
            authority=null;state="RESTART_QUERY";originalIntent=true;
            nativeReleased=false;
        }
        Work(UUID id,Work original) {
            this.id=id;operation=original.operation;subject=original.subject;qualification=original.qualification;
            saveGeneration=original.saveGeneration;source=original.source;code=original.code;
            paths=original.paths;
            authority=original.authority;snapshot=original.snapshot;
        }
    }
    /** Before INTENT publication, only the actually opened new file may be continued. */
    private static final class Candidate {
        final Descriptor descriptor;
        final RecoveryImage.Encoded image;
        FileChannel channel;
        long position;
        boolean created,complete,forced,closed;
        IOException closeFailure;
        Candidate(Descriptor descriptor,RecoveryImage.Encoded image)throws IOException {
            this.descriptor=descriptor;this.image=image.retain();
        }
    }
    /** Only this executor can construct tickets; Native rejects arbitrary request classes. */
    static final class Ticket {
        private final Work work;
        private final RecoverySources.Source source;
        private final RecoveryStorage owner;
        private RecoveryImage.Encoded image;
        private boolean ready,commitEntered;
        private CompletableFuture<Void> transactionAck,armedAck;
        private final Map<Long,long[]> retirements=new HashMap<>();
        private Ticket(RecoveryStorage owner,Work work,RecoverySources.Source source,RecoveryImage.Encoded image) { this.owner=owner;this.work=work;this.source=source;this.image=image; }
        private String path() { return source.path.toString(); }

        private String fileIdentity() { return work.descriptor.identity; }
        private String baselineHash() { return work.descriptor.baseline; }
        private String expectedHash() { return work.descriptor.expected; }
        private synchronized long imageSize()throws IOException { return image==null?-1:image.size(); }
        private synchronized int readImage(long offset,byte[] buffer)throws IOException { if(image==null)throw new IOException("TICKET_IMAGE_RETIRED");return image.read(offset,buffer,0,buffer.length); }
        private boolean nativeKeepAlive() { return !owner.closing&&work.preparing&&work.ticket==this; }
        private synchronized void nativeRetired(long token,long outcome,boolean closed,long error) {
            if(token>0&&closed)retirements.put(token,new long[]{outcome,1,error});
        }
        private void nativeAllocated(long value) { token=value;work.nativeReleased=false; }
        private void nativeTransaction(String value) {
            if(value==null||!value.matches("[0-9a-f]{32}"))throw new IllegalArgumentException("INVALID_TRANSACTION_ID");
            work.transaction=value;work.transactionSession=source.owner.runtimeSession();work.effectClosed=false;
        }
        private synchronized long[] nativeRetirement(long token) {
            long[] result=retirements.get(token);return result==null?new long[]{0,0,0}:result.clone();
        }
        private synchronized void release() { if(image!=null)image.close();image=null; }
        volatile long token;
    }
    /** One-shot final gate. Java holds both qualification and source gates throughout the Native commit. */
    static final class Permit {
        private final Ticket ticket;
        private final RecoveryStorage owner;
        private boolean used;
        private Permit(RecoveryStorage owner,Ticket ticket) { this.owner=owner;this.ticket=ticket; }
        private boolean beginCommitFor(Ticket request) {
            if(used||request!=ticket)return false;
            used=true;
            Work w=ticket.work;
            return w.authority.commitGate.isHeldByCurrentThread()&&ticket.source.gate.isHeldByCurrentThread()
                    &&!owner.closing&&w.authority.terminal&&w.authority.generation==w.qualification
                    &&owner.runtime.canonicalSubject(w.subject)==w.authority&&owner.currentPaths(w,ticket.source)
                    &&(w.restartRetry?ticket.source.owner.current(ticket.source)&&ticket.source.writers==0
                        &&ticket.source.generation==w.reboundGeneration:ticket.source.current(w.snapshot))
                    &&Objects.equals(w.preparedSources,ticket.source.owner.dependencies(w.subject))
                    &&owner.runtime.groupDurable(w.subject)
                    &&w.originalIntent&&w.commitPossible;
        }
    }
    RecoveryStorage(ProRuntime runtime,RecoverySources sources,Path directory)throws IOException {
        this.runtime=runtime;this.sources=sources;payloads=directory.resolve("storage-images");
        Files.createDirectories(payloads);
        worker=new ThreadPoolExecutor(1,1,0,TimeUnit.MILLISECONDS,new ArrayBlockingQueue<>(32),r->{
            Thread t=new Thread(r,"ronova-pro-storage");t.setDaemon(true);return t;
        },new ThreadPoolExecutor.AbortPolicy());
        restore();
        for(Work item:work.values())index(item);
    }
    private void index(Work item) {
        Save key=new Save(item.subject,item.qualification,item.source,item.code,item.saveGeneration);
        bySave.computeIfAbsent(key,ignored->new CopyOnWriteArrayList<>()).add(item);
        bySubject.computeIfAbsent(item.subject,ignored->new CopyOnWriteArrayList<>()).add(item);
        byOperation.computeIfAbsent(item.operation,ignored->new CopyOnWriteArrayList<>()).add(item);
        indexedWork.incrementAndGet();schedulingChanges.add(item);
    }
    private void unindex(Work item) {
        List<Work> saves=bySave.get(new Save(item.subject,item.qualification,item.source,item.code,item.saveGeneration));
        if(saves!=null)saves.remove(item);
        List<Work> subjects=bySubject.get(item.subject);if(subjects!=null)subjects.remove(item);
        List<Work> operations=byOperation.get(item.operation);if(operations!=null)operations.remove(item);
        indexedWork.decrementAndGet();schedulingChanges.add(item);
    }
    private void restore() {
        for(String line:runtime.journal.history()) {
            if(!line.startsWith("STORAGE/"))continue;
            try {
                String[] p=line.split("\t",-1);
                if(!p[0].equals(PREFIX)&&!p[0].equals("STORAGE/1"))throw new IllegalArgumentException("FUTURE_STORAGE_VERSION");
                if(p[1].equals("INTENT")) {
                    Descriptor d=Descriptor.parse(p);Work previous=work.putIfAbsent(d.id,new Work(d));
                    sources.expect(d.source,d.code,d.subject,d.saveGeneration);
                    if(previous!=null&&!previous.descriptor.equals(d))throw new IllegalArgumentException("WORK_KEY_REDEFINED");
                } else {
                    if(p.length!=4)throw new IllegalArgumentException("STORAGE_FACT_ARITY");
                    Work w=work.get(UUID.fromString(p[2]));if(w==null)throw new IllegalArgumentException("ORPHAN_STORAGE_FACT");
                    switch(p[1]) {
                        case "COMMIT_ARMED" -> w.commitPossible=true;
                        case "TRANSACTION" -> {
                            String[] binding=untext(p[3]).split("\\|",-1);
                            if(!p[0].equals(PREFIX)||binding.length!=3||!binding[0].equals(BACKEND)||!binding[1].matches("[0-9a-f]{32}"))throw new IllegalArgumentException("TRANSACTION_BINDING_INVALID");
                            w.transaction=binding[1];w.transactionSession=UUID.fromString(binding[2]);
                        }
                        case "REQUALIFIED" -> { /* Evidence is rechecked in the current session. */ }
                        case "NONCOMMIT" -> {
                            String detail=untext(p[3]);
                            if(detail.startsWith("SUPERSEDED_BEFORE_NATIVE_COMMIT|"))w.supersededBy=UUID.fromString(detail.substring(detail.indexOf('|')+1));
                        }
                        case "SUPERSEDED" -> w.supersededBy=UUID.fromString(untext(p[3]));
                        case "READBACK" -> { w.readbackEverConfirmed=true;w.state="RESTART_QUERY";w.reason="HISTORICAL_READBACK_REQUIRES_CURRENT_QUERY"; }
                        case "RESOURCES" -> w.resourcesConfirmed=true;
                        case "RESULT", "ROLLBACK", "PREPARE_FAILED" -> { /* Preserve historical meaning; no implicit write permission. */ }
                        default -> throw new IllegalArgumentException("UNKNOWN_STORAGE_FACT");
                    }
                }
            } catch(RuntimeException failure) {
                gap="STORAGE_HISTORY_QUERY_ONLY:"+failure.getMessage();runtime.journal.refuseWrites(gap);break;
            }
        }
    }
    void discover(ProRuntime.Subject subject,UUID operation) {
        ProRuntime.Subject canonical=runtime.canonicalSubject(subject.id);
        if(canonical!=null&&canonical!=subject) {discover(canonical,runtime.groupOperation(canonical.id,operation));return;}
        if(closing||!subject.terminal)return;
        for(Work item:bySubject.getOrDefault(subject.id,List.of()))if(item.qualification==subject.generation)
            item.authority=subject;
        Set<UUID> targets=runtime.groupTargets(subject.id);
        for(RecoverySources.Source source:sources.registered()) {
            RecoverySources.Snapshot snapshot=source.snapshot;
            if(snapshot==null||Collections.disjoint(source.subjects,targets))continue;
            Save key=new Save(subject.id,subject.generation,source.locator,source.code,snapshot.generation);
            Set<List<RecoveryImage.Step>> paths=selectedPaths(snapshot,targets);
            boolean exists=bySave.getOrDefault(key,List.of()).stream().anyMatch(w->
                    Objects.equals(w.paths,paths)&&(w.descriptor==null?w.snapshot==snapshot:w.descriptor.identity.equals(snapshot.identity)&&w.descriptor.baseline.equals(snapshot.hash)));
            if(!exists) {
                // Retain every actual obligation and its original durable intent.
                UUID id=UUID.randomUUID();Work item=new Work(id,operation,subject,snapshot,paths);
                work.put(id,item);index(item);changes.incrementAndGet();
            }
        }
    }
    private static Set<List<RecoveryImage.Step>> selectedPaths(RecoverySources.Snapshot snapshot,Set<UUID> targets){
        Set<List<RecoveryImage.Step>> paths=new HashSet<>();
        for(var selection:snapshot.selections)if(targets.contains(selection.subject()))paths.add(selection.path());
        return Set.copyOf(paths);
    }
    private boolean currentPaths(Work item,RecoverySources.Source source){
        return item.paths!=null&&source.snapshot!=null&&item.paths.equals(selectedPaths(source.snapshot,runtime.groupTargets(item.subject)));
    }
    void tick(long tick) {
        if(closing)return;
        drainScheduling(tick);
        while(!deadlines.isEmpty()&&deadlines.first().scheduledAt<=tick) {
            Work due=deadlines.pollFirst();due.queuedDeadline=false;
            due.queuedReady=true;ready.add(due);
        }
        // Current obligations get a complete selection round within five ticks;
        // running work and retired generations do not consume that selection share.
        int count=indexedWork.get(),budget=Math.min(count,Math.max(16,(count+4)/5)),accepted=0,available=ready.size();
        for(int i=0;i<available&&accepted<budget&&!ready.isEmpty();i++) {
            Iterator<Work> cursor=ready.iterator();Work w=cursor.next();cursor.remove();w.queuedReady=false;
            if(work.get(w.id)!=w||w.running)continue;
            // These are live decisions, not cached settlement or release authority.
            if(superseded(w)) { schedule(w,tick+5,tick);continue; }
            if(w.state.equals("FENCED")&&w.nativeReleased&&w.transientReleased&&!w.queryClosePending&&w.queryInput==null&&w.queryNativeClosed&&successor(w)==null) {
                schedule(w,tick+20,tick);continue;
            }
            w.retryAt=tick+(w.preparing?1:w.state.equals("READBACK_CONFIRMED")?5:20);
            w.running=true;
            try { worker.execute(()->{
                String before=w.state+":"+w.reason+":"+w.nativeReleased+":"+w.transientReleased+":"+w.resourcesConfirmed;
                try { advance(w); }
                catch(Throwable failure) {
                    if(w.preparing)try { retire(w,"PREPARATION_EXCEPTION"); }catch(Throwable retirement) { failure.addSuppressed(retirement); }
                    w.state=w.commitPossible?"COMMIT_QUERY_REQUIRED":"PREPARE_UNRESOLVED";w.reason=failure.getClass().getSimpleName()+":"+failure.getMessage();
                }
                finally {
                    // A first preparation/readback can change its retry class in
                    // this invocation; keep the existing 1/5/20 tick periods.
                    if(w.preparing)w.retryAt=Math.min(w.retryAt,tick+1);
                    else if(w.state.equals("READBACK_CONFIRMED"))w.retryAt=Math.min(w.retryAt,tick+5);
                    w.running=false;
                    if(!before.equals(w.state+":"+w.reason+":"+w.nativeReleased+":"+w.transientReleased+":"+w.resourcesConfirmed))changes.incrementAndGet();
                    schedulingChanges.add(w);
                }
            });accepted++; }
            catch(RejectedExecutionException capacity) {
                w.running=false;w.retryAt=tick+1;w.reason="STORAGE_WORKER_BUDGET_WAIT";
                schedule(w,w.retryAt,tick);
                break;
            }
        }
    }
    private void drainScheduling(long tick) {
        for(Work item;(item=schedulingChanges.poll())!=null;) {
            if(work.get(item.id)!=item) { unschedule(item);continue; }
            if(!item.running&&!item.queuedReady&&!item.queuedDeadline) {
                long due=item.wakeAfterRun?tick:item.retryAt;item.wakeAfterRun=false;schedule(item,due,tick);
            }
        }
        for(Work newer;(newer=confirmedReadbacks.poll())!=null;) {
            if(work.get(newer.id)!=newer)continue;
            // A newly confirmed save can retire a fenced private candidate before
            // its next slow retry. advance() still rechecks the actual successor.
            for(UUID member:runtime.groupTargets(newer.subject))for(Work older:bySubject.getOrDefault(member,List.of()))
                if(relatedSuccessor(older,newer)) {
                    if(older.running)older.wakeAfterRun=true;
                    else if(!older.queuedReady)schedule(older,tick,tick);
                }
        }
    }
    private void schedule(Work item,long due,long tick) {
        unschedule(item);
        if(closing||work.get(item.id)!=item||item.running)return;
        item.retryAt=due;item.scheduledAt=due;
        if(due<=tick) { item.queuedReady=true;ready.add(item); }
        else { item.queuedDeadline=true;deadlines.add(item); }
    }
    private void unschedule(Work item) {
        if(item.queuedReady) { ready.remove(item);item.queuedReady=false; }
        if(item.queuedDeadline) { deadlines.remove(item);item.queuedDeadline=false; }
    }
    private void advance(Work w)throws IOException {
        if(work.get(w.id)!=w)return;
        if(!StorageNative.available()) { w.state="UNSUPPORTED";w.reason=StorageNative.reason();return; }
        StorageNative.maintain0();
        if(w.queryClosePending)closeQueryInput(w);
        if(w.ticket!=null&&!w.nativeReleased&&!w.preparing) {
            long[] retired=w.ticket.nativeRetirement(w.ticket.token);
            if(retired[1]!=1) { w.reason="NATIVE_RESOURCE_RETIREMENT_PENDING";return; }
            w.nativeReleased=true;
            if(retired[0]==1||retired[0]==2)w.effectClosed=true;
        }
        if(w.descriptor!=null&&!w.preparing)confirmClosedEffect(w);
        Work newer=successor(w);
        if(newer!=null&&!w.readbackEverConfirmed&&w.effectClosed&&w.nativeReleased&&!w.queryClosePending) {
            release(w);
            if(w.descriptor==null) {
                // Only an unpersisted preparation cache is removed. It never had write authority.
                if(work.remove(w.id,w))unindex(w);changes.incrementAndGet();return;
            }
            ack(fact("SUPERSEDED",w,newer.id.toString()));
            ack(fact("RESOURCES",w,"CLOSED_ORIGINAL_EFFECT_REFERENCES_RELEASED"));
            w.resourcesConfirmed=true;w.supersededBy=newer.id;w.state="SUPERSEDED_AFTER_CLOSED_EFFECT";return;
        }
        if(w.state.equals("FENCED")) { release(w);discardUnpublished(w);return; }
        if(w.preparing) { continuePreparation(w);return; }
        if(w.state.equals("READBACK_CONFIRMED")) {
            query(w);return;
        }
        if(w.descriptor!=null&&(!w.restartRetry&&w.snapshot==null||w.authority==null||w.commitPossible)) { query(w);return; }
        if(w.ticket!=null&&w.nativeReleased)releaseBuffers(w);
        RecoverySources.Snapshot snapshot=w.snapshot;
        RecoverySources.Source source=w.restartRetry?sources.resolve(w.descriptor.source,w.descriptor.code):snapshot==null?null:snapshot.source;
        if(source==null) { w.state="WAIT_SOURCE";w.reason="ORIGINAL_SOURCE_NOT_LOADED";return; }
        boolean current;
        try(var storageGate=source.gate.enter()) {
            current=(w.restartRetry?source.writers==0&&source.generation==w.reboundGeneration&&sources.current(source):source.current(snapshot))&&currentPaths(w,source);
        }
        if(!current) { w.state="FENCED";w.reason="SAVE_GENERATION_OR_OWNER_CHANGED";release(w);discardUnpublished(w);return; }
        try(var authorityGate=w.authority.commitGate.enter()) {
            current=!closing&&w.authority.terminal&&w.authority.generation==w.qualification&&runtime.canonicalSubject(w.subject)==w.authority;
        }
        if(!current) { w.state="FENCED";w.reason="QUALIFICATION_CHANGED";release(w);discardUnpublished(w);return; }
        if(!prepareIntent(w))return;
        if(w.descriptor.baseline.equals(w.descriptor.expected)) { query(w);return; }
        if(!prepareKnownSources(w)) { w.state="WAIT_SOURCE_INTENTS";w.reason="KNOWN_SAVE_OBLIGATIONS_NOT_ALL_DURABLE";return; }
        RecoveryImage.Encoded image=loadImage(w.descriptor);
        Ticket ticket=new Ticket(this,w,source,image);w.ticket=ticket;w.transientReleased=false;w.resourcesConfirmed=false;w.effectClosed=false;w.preparing=true;
        continuePreparation(w);
    }
    private void continuePreparation(Work w)throws IOException {
        Ticket ticket=w.ticket;RecoverySources.Source source=ticket.source;
        boolean current;
        try(var binding=source.gate.enter()) {
            current=(w.restartRetry?source.writers==0&&source.generation==w.reboundGeneration&&sources.current(source):source.current(w.snapshot))&&currentPaths(w,source);
        }
        ProRuntime.Subject authority=w.authority;
        if(authority!=null)try(var qualification=authority.commitGate.enter()) {
            current&=!closing&&authority.terminal&&authority.generation==w.qualification&&runtime.canonicalSubject(w.subject)==authority;
        } else current=false;
        if(!current) { retire(w,"SAVE_GENERATION_OR_QUALIFICATION_CHANGED");w.state="FENCED";w.reason="SAVE_GENERATION_OR_QUALIFICATION_CHANGED";return; }
        if(!ticket.ready) {
            long[] staged=ticket.token==0?StorageNative.prepare0(ticket):StorageNative.advance0(ticket,ticket.token);
            if(staged==null||staged.length!=3)throw new IOException("INVALID_NATIVE_PREPARE_RESULT");
            if(ticket.token!=0&&ticket.token!=staged[1])throw new IOException("NATIVE_TOKEN_BINDING_CHANGED");
            ticket.token=staged[1];w.nativeReleased=ticket.token==0||ticket.nativeRetirement(ticket.token)[1]==1;
            if(staged[0]==7) { w.state="PREPARING";w.reason="ORIGINAL_NATIVE_PREPARATION_CONTINUES";w.retryAt=0;return; }
            if(staged[0]!=1) {
                w.preparing=false;
                w.state=staged[0]==4?"UNSUPPORTED":staged[0]==5?"BASELINE_CONFLICT":"PREPARE_UNRESOLVED";
                w.reason="NATIVE_PREPARE:"+Arrays.toString(staged);ack(fact("PREPARE_FAILED",w,w.reason));
                if(w.nativeReleased)releaseBuffers(w);return;
            }
            ticket.ready=true;w.nativeReleased=false;
        }
        // A source appearing during a long preparation still needs its original intent.
        if(!prepareKnownSources(w)) { w.state="WAIT_SOURCE_INTENTS";w.reason="KNOWN_SAVE_OBLIGATIONS_NOT_ALL_DURABLE";return; }
        if(w.transaction==null)throw new IOException("TRANSACTION_ID_NOT_CAPTURED");
        String transaction=fact("TRANSACTION",w,BACKEND+"|"+w.transaction+"|"+w.transactionSession);
        ticket.transactionAck=pollAck(transaction,ticket.transactionAck);
        if(!ticket.transactionAck.isDone()) { w.state="WAIT_TRANSACTION_ACK";return; }
        String armed=fact("COMMIT_ARMED",w,"backend="+BACKEND);
        ticket.armedAck=pollAck(armed,ticket.armedAck);
        if(!ticket.armedAck.isDone()) { w.state="WAIT_COMMIT_ACK";return; }
        w.commitPossible=true;
        try {
            long[] result;
            try(var authorityGate=w.authority.commitGate.enter()) {
                try(var storageGate=source.gate.enter()) {
                    ticket.commitEntered=true;
                    result=StorageNative.finish0(ticket,ticket.token,new Permit(this,ticket));
                }
            }
            w.preparing=false;
            if(result==null||result.length!=3)throw new IOException("INVALID_NATIVE_FINISH_RESULT");
            w.nativeReleased=result[1]==1;
            if(w.nativeReleased&&(result[0]==1||result[0]==2))w.effectClosed=true;
            w.state=result[0]==1?"COMMIT_QUERY_REQUIRED":result[0]==2?"ROLLBACK_CONFIRMED":"EFFECT_UNKNOWN";
            w.reason="NATIVE_RESULT:"+Arrays.toString(result);ack(fact("RESULT",w,w.reason));
            if(result[0]==2) {
                ack(fact("ROLLBACK",w,"CONFIRMED"));w.commitPossible=false;
                // Same original intent; only an explicit rollback permits a fresh private preparation.
                if(w.nativeReleased)releaseBuffers(w);
            } else query(w);
        } finally {
            // A failure after COMMIT_ARMED can occur before the native call. The private
            // live ticket must still be retired; finish(null) cannot undo a committed TxF.
            if(!w.nativeReleased)retire(w,"EXCEPTION_PATH_CONFIRMED");
            w.preparing=false;
        }
    }
    private CompletableFuture<Void> pollAck(String record,CompletableFuture<Void> pending)throws IOException {
        if(pending==null)pending=runtime.journal.append(record);
        pending=runtime.journal.retryRejected(record,pending);
        if(pending.isDone())awaitAck(pending);
        return pending;
    }
    private void retire(Work w,String reason)throws IOException {
        w.preparing=false;Ticket ticket=w.ticket;if(ticket==null)return;
        if(ticket.token==0) { w.nativeReleased=true;releaseBuffers(w);return; }
        long[] result=StorageNative.finish0(ticket,ticket.token,null);
        if(result!=null&&result.length==3) {
            w.nativeReleased=result[1]==1;
            if(w.nativeReleased&&(result[0]==1||result[0]==2))w.effectClosed=true;
            if(result[0]==2) { w.commitPossible=false;ack(fact("ROLLBACK",w,reason)); }
            else if(result[0]==1) { w.commitPossible=true;w.state="COMMIT_QUERY_REQUIRED"; }
            else if(ticket.commitEntered) { w.commitPossible=true;w.state="EFFECT_UNKNOWN"; }
        } else if(ticket.commitEntered) { w.commitPossible=true;w.state="EFFECT_UNKNOWN"; }
        if(w.nativeReleased)releaseBuffers(w);
    }
    private void query(Work w)throws IOException {
        Descriptor d=w.descriptor;
        if(!w.nativeReleased&&w.ticket==null&&!d.session.equals(runtime.session)) {
            if(w.transaction!=null) {
                long[] transaction=StorageNative.queryTransaction0(w.transaction);
                w.queryNativeClosed=transaction!=null&&transaction.length==3&&transaction[1]==1;
                w.nativeReleased=w.queryNativeClosed&&(transaction[0]==1||transaction[0]==2
                        ||transaction[0]==4&&w.transactionSession!=null&&runtime.journal.processEnded(w.transactionSession));
            } else w.nativeReleased=!w.commitPossible&&runtime.journal.processEnded(d.session);
        }
        confirmClosedEffect(w);
        RecoverySources.Source source=sources.resolve(d.source,d.code);
        if(source==null) { w.state="WAIT_SOURCE";w.reason="REGISTERED_SOURCE_NOT_LOADED_OR_CODE_CHANGED";return; }
        RecoveryImage.Encoded actual=null;
        String identityBefore,identityAfter;
        FileInputStream input=new FileInputStream(source.path.toFile());w.queryInput=input;
        try {
            identityBefore=StorageNative.identity(input.getFD(),source.path.toString());
            actual=RecoveryImage.capture(input);
            identityAfter=StorageNative.identity(input.getFD(),source.path.toString());
        } catch(Throwable failure) {
            if(actual!=null)actual.close();throw failure;
        } finally {
            try { closeQueryInput(w); }
            catch(IOException failure) { if(actual!=null)actual.close();throw failure; }
        }
        // A successful independent close above is part of the readback fact.
        try {
            if(!d.identity.equals(identityBefore)||!d.identity.equals(identityAfter)) { w.state="IDENTITY_CONFLICT";w.reason="INDEPENDENT_READ_FILE_IDENTITY_CHANGED";return; }
            String hash=actual.hash();
            boolean expected=hash.equals(d.expected);
            if(expected&&!hash.equals(w.decodedImageHash)) {
                String projection=RecoveryImage.semanticHash(RecoveryImage.decode(actual));
                w.decodedProjection=projection;w.decodedImageHash=hash;
            }
            if(expected&&d.preserved.equals(w.decodedProjection)) {
                boolean changed=!w.state.equals("READBACK_CONFIRMED"),released=w.resourcesConfirmed;
                if(changed)ack(fact("READBACK",w,"IDENTITY_EXPECTED_IMAGE_AND_FULL_PROJECTION_CONFIRMED"));
                w.readbackEverConfirmed=true;w.state="READBACK_CONFIRMED";w.reason="TASK_MEMORY_SOURCE_AND_RESOURCES_STILL_SEPARATE";
                RecoverySources.Snapshot original=w.snapshot==null?source.snapshot:w.snapshot;
                if(w.paths!=null&&original!=null&&original.generation==d.saveGeneration&&original.identity.equals(d.identity)&&original.hash.equals(d.baseline))
                    sources.projectedReadback(source,original,d.identity,actual,w.paths);
                if(w.nativeReleased&&w.queryNativeClosed&&!w.resourcesConfirmed) { release(w);ack(fact("RESOURCES",w,"EXECUTOR_BUFFER_AND_NATIVE_HANDLES_RELEASED"));w.resourcesConfirmed=true; }
                w.lastQuery=Integer.toUnsignedLong(runtime.server.getTickCount());
                if(changed||!released&&w.resourcesConfirmed)confirmedReadbacks.add(w);
            } else {
                w.state=w.commitPossible?"COMMIT_QUERY_REQUIRED":"RESTART_QUERY";
                w.reason=hash.equals(d.baseline)?"BASELINE_PRESENT_OLD_TRANSACTION_NONCOMMIT_NOT_PROVEN":"NEITHER_EXPECTED_NOR_BASELINE_IMAGE";
                if(hash.equals(d.baseline))requalifyOriginal(w,source);
            }
        } finally { actual.close(); }
    }
    private static void closeQueryInput(Work w)throws IOException {
        // FileInputStream can mark itself closed before its descriptor close
        // throws. A later no-op close is not a release receipt for that failure.
        if(w.queryCloseFailure!=null)throw new IOException("INDEPENDENT_READ_CLOSE_UNCONFIRMED",w.queryCloseFailure);
        w.queryClosePending=true;
        try { w.queryInput.close(); }
        catch(IOException failure) { w.queryCloseFailure=failure;throw failure; }
        w.queryInput=null;w.queryClosePending=false;
    }
    /** Private candidates and descriptors are prepared on this worker, outside Root gates.
     * A crash in the first source's commit cannot discard the other already-known saves. */
    private boolean prepareKnownSources(Work current)throws IOException {
        String dependencies=sources.dependencies(current.subject);int budget=16;
        for(UUID member:runtime.groupTargets(current.subject))for(Work older:bySubject.getOrDefault(member,List.of()))
            if(older!=current&&sameSource(older,current)&&older.saveGeneration<=current.saveGeneration
                    &&(older.saveGeneration<current.saveGeneration||!older.subject.equals(current.subject)||!Objects.equals(older.paths,current.paths))){
                confirmClosedEffect(older);
                if(!older.effectClosed||!older.nativeReleased||older.preparing){
                    older.retryAt=0;schedulingChanges.add(older);return false;
                }
            }
        List<Work> related=bySubject.getOrDefault(current.subject,List.of());
        for(Work item:related)if(item.qualification==current.qualification&&!item.originalIntent&&item.snapshot!=null&&item.snapshot.source.current(item.snapshot)&&currentPaths(item,item.snapshot.source)) {
            if(budget--==0||!prepareIntent(item))return false;
        }
        Map<String,Set<KnownSave>> captured=new HashMap<>();
        for(Work item:related)if(item.qualification==current.qualification&&item.originalIntent) {
            Descriptor descriptor=item.descriptor;
            captured.computeIfAbsent(descriptor.source,key->new HashSet<>()).add(
                    new KnownSave(descriptor.code,descriptor.saveGeneration,descriptor.identity,descriptor.baseline,item.paths));
        }
        for(RecoverySources.Source source:sources.registered()) {
            RecoverySources.Snapshot image=source.snapshot;
            if(image!=null&&!Collections.disjoint(source.subjects,runtime.groupTargets(current.subject))
                    &&!captured.getOrDefault(source.locator,Set.of()).contains(new KnownSave(source.code,image.generation,image.identity,image.hash,selectedPaths(image,runtime.groupTargets(current.subject)))))return false;
        }
        if(!sources.dependencies(current.subject).equals(dependencies))return false;
        current.preparedSources=dependencies;return true;
    }
    private boolean prepareIntent(Work w)throws IOException {
        if(w.descriptor==null) {
            if(w.candidate==null) {
                RecoverySources.Snapshot snapshot=w.snapshot;if(snapshot==null)throw new IOException("SAVE_SNAPSHOT_NOT_AVAILABLE");
                RecoveryImage.Projection projection;
                Set<UUID> targets=runtime.groupTargets(w.subject);
                if(!Objects.equals(w.paths,selectedPaths(snapshot,targets)))throw new IOException("SAVE_REMOVAL_SCOPE_CHANGED");
                try(RecoveryImage.Encoded baseline=snapshot.bytes()) {
                    if(snapshot.selections.stream().anyMatch(selection->targets.contains(selection.subject())))projection=RecoveryImage.project(baseline,snapshot.selections,targets);
                    else {
                        String hash=baseline.hash(),preserved=RecoveryImage.semanticHash(RecoveryImage.decode(baseline));
                        projection=new RecoveryImage.Projection(baseline.retain(),hash,hash,preserved);
                    }
                }
                try(projection) {
                    Descriptor descriptor=new Descriptor(PREFIX,w.id,w.operation,w.subject,w.qualification,snapshot.source.locator,
                            snapshot.source.code,snapshot.generation,snapshot.identity,projection.baselineHash(),projection.expectedHash(),
                            projection.preservedHash(),runtime.session,BACKEND,encodePaths(snapshot.selections.stream().filter(selection->targets.contains(selection.subject())).map(RecoveryImage.Selection::path).toList()));
                    w.candidate=new Candidate(descriptor,projection.encoded());
                    w.transientReleased=false;w.resourcesConfirmed=false;
                }
            }
            if(!persistImage(w))return false;
            Candidate completed=w.candidate;
            // Byte equality alone is not durability. Both receipts precede publication.
            if(!completed.forced||!completed.closed)throw new IOException("DURABLE_CANDIDATE_NOT_CONFIRMED");
            w.descriptor=completed.descriptor;
            completed.image.close();w.candidate=null;w.transientReleased=w.ticket==null;
            w.intentAck=runtime.journal.append(w.descriptor.record());
        }
        if(!w.originalIntent) {
            w.intentAck=runtime.journal.retryRejected(w.descriptor.record(),w.intentAck);
            if(w.intentAck==null)throw new IOException("ORIGINAL_INTENT_SUBMISSION_UNCONFIRMED");
            if(!w.intentAck.isDone()) { w.state="WAIT_INTENT_ACK";w.reason="ORIGINAL_INTENT_ACK_PENDING";return false; }
            awaitAck(w.intentAck);w.originalIntent=true;
        }
        return true;
    }
    private void requalifyOriginal(Work w,RecoverySources.Source source)throws IOException {
        Descriptor d=w.descriptor;
        if(!d.protocol.equals(PREFIX)) { w.reason="LEGACY_DESCRIPTOR_QUERY_ONLY_NO_FRESH_WRITE_EVIDENCE";return; }
        if(w.readbackEverConfirmed) { w.reason="CONFIRMED_EFFECT_CHANGED_FRESH_SAVE_GENERATION_REQUIRED";return; }
        if(source.generation!=d.saveGeneration) { w.reason="SAVE_GENERATION_CHANGED_ORIGINAL_INTENT_QUERY_ONLY";return; }
        boolean cannotCommit=false;
        if(w.transaction!=null) {
            long[] result=StorageNative.queryTransaction0(w.transaction);
            w.queryNativeClosed=result!=null&&result.length==3&&result[1]==1;
            if(!w.queryNativeClosed) { w.reason="ORIGINAL_TRANSACTION_QUERY_HANDLE_PENDING";return; }
            cannotCommit=result[0]==2||result[0]==4&&w.transactionSession!=null&&runtime.journal.processEnded(w.transactionSession);
        } else cannotCommit=!w.commitPossible&&runtime.journal.processEnded(d.session);
        if(!cannotCommit)return;
        ack(fact("NONCOMMIT",w,"ORIGINAL_TRANSACTION_CANNOT_LATER_COMMIT"));w.commitPossible=false;
        ProRuntime.Subject authority=w.authority;
        if(authority==null||!authority.terminal||authority.generation!=w.qualification||runtime.canonicalSubject(w.subject)!=authority) { w.reason="NONCOMMIT_CONFIRMED_CURRENT_QUALIFICATION_REQUIRED";return; }
        try(var qualification=authority.commitGate.enter();var binding=source.gate.enter()) {
            if(closing||!authority.terminal||authority.generation!=w.qualification||!sources.current(source)||source.writers!=0)return;
            w.reboundGeneration=source.generation;
        }
        // This ACK cannot revive stale evidence: both identities/gates are checked again by Native final commit.
        ack(fact("REQUALIFIED",w,runtime.session+"|"+w.qualification+"|"+w.reboundGeneration));
        w.nativeReleased=true;w.restartRetry=true;w.state="WAIT_PREPARE";w.reason="ORIGINAL_INTENT_REQUALIFIED";
    }
    String dependencies(UUID operation) {
        StringBuilder key=new StringBuilder();
        for(Work item:byOperation.getOrDefault(operation,List.of()))key.append('|').append(item.id).append(':')
            .append(item.saveGeneration).append(':').append(item.state).append(':').append(item.commitPossible)
            .append(':').append(item.nativeReleased).append(':').append(item.transientReleased).append(':').append(item.resourcesConfirmed)
            .append(':').append(item.effectClosed)
            .append(':').append(item.supersededBy);
        return key.toString();
    }
    long epoch() { return changes.get(); }
    void readObserved(Path path,String identity,RecoveryImage.Encoded bytes,net.minecraft.nbt.CompoundTag decoded)throws IOException {
        String hash=bytes.hash();
        for(Work item:work.values()) {
            Descriptor descriptor=item.descriptor;
            if(descriptor==null||descriptor.origins==null||!descriptor.identity.equals(identity)||!descriptor.baseline.equals(hash)
                    ||!runtime.subjects.containsKey(descriptor.subject)||!sources.acceptRead(descriptor.source,descriptor.code,path))continue;
            try {
                if(!RecoveryImage.semanticHash(decoded).equals(RecoveryImage.semanticHash(RecoveryImage.decode(bytes))))throw new IOException("READ_CODEC_SEMANTICS_CHANGED");
                for(var location:decodePaths(descriptor.origins)) {
                    var record=RecoveryImage.resolve(decoded,location);
                    if(!(record instanceof net.minecraft.nbt.CompoundTag tag))throw new IOException("ORIGINAL_TYPED_RECORD_PATH_CHANGED");
                    sources.observed(tag,descriptor.subject);
                }
            } catch(IOException|RuntimeException invalid) { gap="ORIGINAL_SOURCE_READ_PROVENANCE_UNRESOLVED:"+invalid.getMessage(); }
        }
    }
    private static String encodePaths(List<List<RecoveryImage.Step>> paths) {
        try {
            var buffer=new ByteArrayOutputStream();var output=new DataOutputStream(buffer);output.writeInt(paths.size());
            for(var path:paths) { output.writeInt(path.size());for(var step:path) { output.writeBoolean(step.key()!=null);if(step.key()!=null)output.writeUTF(step.key());else output.writeInt(step.index()); } }
            output.flush();
            return Base64.getUrlEncoder().withoutPadding().encodeToString(buffer.toByteArray());
        } catch(IOException impossible) { throw new IllegalArgumentException("ORIGIN_ENCODING_UNAVAILABLE",impossible); }
    }
    private static List<List<RecoveryImage.Step>> decodePaths(String encoded) {
        try {
            var input=new DataInputStream(new ByteArrayInputStream(Base64.getUrlDecoder().decode(encoded)));
            // Each path consumes its four-byte length, even when it has no steps.
            // Do not allocate from an untrusted count before those bytes exist.
            int count=input.readInt();if(count<0||count>input.available()/Integer.BYTES)throw new IOException("ORIGIN_COUNT");
            List<List<RecoveryImage.Step>> paths=new ArrayList<>();
            for(int i=0;i<count;i++) {
                int length=input.readInt();
                long remaining=input.available()-(long)(count-i-1)*Integer.BYTES;
                // The shortest step is a key marker plus writeUTF's two-byte length.
                // Reserve all later path headers; every accepted iteration consumes bytes.
                if(length<0||remaining<0||length>remaining/3)throw new IOException("ORIGIN_DEPTH");
                List<RecoveryImage.Step> path=new ArrayList<>();
                for(int p=0;p<length;p++) {
                    int type=input.readUnsignedByte();
                    if(type==1)path.add(RecoveryImage.Step.key(input.readUTF()));
                    else if(type==0)path.add(RecoveryImage.Step.index(input.readInt()));
                    else throw new IOException("ORIGIN_STEP_TYPE");
                }
                paths.add(List.copyOf(path));
            }
            if(input.available()!=0||!encodePaths(paths).equals(encoded))throw new IOException("NONCANONICAL_ORIGIN");
            return List.copyOf(paths);
        } catch(IOException|IllegalArgumentException invalid) { throw new IllegalArgumentException("ORIGINAL_PATHS_INVALID",invalid); }
    }
    private boolean superseded(Work old) {
        if(old.running||!old.effectClosed||!old.nativeReleased||!old.transientReleased||!old.resourcesConfirmed||old.queryClosePending||old.queryInput!=null||!old.queryNativeClosed)return false;
        Work newer=successor(old);
        return newer!=null&&(old.readbackEverConfirmed||newer.id.equals(old.supersededBy));
    }
    private Work successor(Work old) {
        long now=Integer.toUnsignedLong(runtime.server.getTickCount());
        for(UUID member:runtime.groupTargets(old.subject))for(Work newer:bySubject.getOrDefault(member,List.of()))if(relatedSuccessor(old,newer)&&newer.state.equals("READBACK_CONFIRMED")
                &&!newer.running&&newer.resourcesConfirmed&&newer.nativeReleased&&newer.transientReleased&&!newer.queryClosePending&&newer.queryInput==null&&newer.queryNativeClosed
                &&newer.lastQuery>=0&&now>=newer.lastQuery&&now-newer.lastQuery<=10)return newer;
        return null;
    }
    private static String fileIdentity(Work item){return item.descriptor!=null?item.descriptor.identity:item.snapshot==null?null:item.snapshot.identity;}
    private static boolean sameSource(Work first,Work second){
        String identity=fileIdentity(first);
        return identity!=null&&identity.equals(fileIdentity(second))&&first.source.equals(second.source)&&first.code.equals(second.code);
    }
    private boolean relatedSuccessor(Work old,Work newer){
        if(old==newer||!sameSource(old,newer)||newer.saveGeneration<old.saveGeneration)return false;
        ProRuntime.Subject group=runtime.canonicalSubject(old.subject);
        if(group==null||!group.terminal||group!=runtime.canonicalSubject(newer.subject)||newer.qualification!=group.generation)return false;
        if(old.subject.equals(newer.subject)&&(old.qualification!=newer.qualification
                ||newer.saveGeneration==old.saveGeneration&&Objects.equals(old.paths,newer.paths)))return false;
        if(old.paths!=null&&newer.paths!=null){
            boolean covered=true;
            for(var original:old.paths){boolean found=false;
                for(var replacement:newer.paths)if(replacement.size()<=original.size()&&original.subList(0,replacement.size()).equals(replacement)){found=true;break;}
                if(!found){covered=false;break;}
            }
            if(covered)return true;
        }
        // A confirmed image may become the next actual baseline after its
        // deleted list entries have been relocated. No old index is reused.
        return old.readbackEverConfirmed&&old.descriptor!=null&&newer.descriptor!=null&&old.descriptor.expected.equals(newer.descriptor.baseline);
    }
    /** Exact original transaction query; a later group image never closes an unknown old effect. */
    private void confirmClosedEffect(Work item){
        if(item.effectClosed||item.preparing)return;
        if(item.descriptor==null){item.effectClosed=item.nativeReleased&&!item.originalIntent&&item.intentAck==null;return;}
        if(item.transaction==null){
            item.effectClosed=!item.commitPossible&&item.nativeReleased
                    &&(item.descriptor.session.equals(runtime.session)||runtime.journal.processEnded(item.descriptor.session));
            return;
        }
        long[] result=StorageNative.queryTransaction0(item.transaction);
        item.queryNativeClosed=result!=null&&result.length==3&&result[1]==1;
        if(item.queryNativeClosed&&(result[0]==1||result[0]==2
                ||result[0]==4&&item.transactionSession!=null&&runtime.journal.processEnded(item.transactionSession)))item.effectClosed=true;
    }
    private void discardUnpublished(Work item){
        if(item.descriptor==null&&!item.originalIntent&&item.intentAck==null&&item.nativeReleased&&item.transientReleased
                &&!item.queryClosePending&&item.queryInput==null&&work.remove(item.id,item)){unindex(item);changes.incrementAndGet();}
    }
    List<String> remaining(UUID operation) {
        List<String> out=new ArrayList<>();if(!gap.isEmpty())out.add(gap);
        for(Work item:byOperation.getOrDefault(operation,List.of())) {
            if(superseded(item))continue;
            if(!item.state.equals("READBACK_CONFIRMED"))out.add("STORAGE:"+item.id+":"+item.state+":"+item.reason);
            if(item.commitPossible&&!item.effectClosed)out.add("STORAGE_ORIGINAL_TRANSACTION_TERMINAL_PENDING:"+item.id);
            long now=Integer.toUnsignedLong(runtime.server.getTickCount());
            if(item.lastQuery<0||now<item.lastQuery||now-item.lastQuery>10)out.add("STORAGE_READBACK_EXPIRED:"+item.id);
            if(item.running||!item.nativeReleased||!item.transientReleased||!item.resourcesConfirmed||item.queryClosePending||item.queryInput!=null||!item.queryNativeClosed)
                out.add("STORAGE_RESOURCES_PENDING:"+item.id);
        }
        return List.copyOf(out);
    }
    private void release(Work w)throws IOException {
        if(!w.nativeReleased)return;
        retireCandidate(w);
        releaseBuffers(w);
        // Snapshot belongs to its registered source; releasing this reference is not claiming source disposal.
        w.snapshot=null;
    }
    private void releaseBuffers(Work w) {
        if(!w.nativeReleased)return;
        if(w.ticket!=null)w.ticket.release();w.ticket=null;w.preparing=false;w.transientReleased=w.candidate==null;
    }
    private boolean persistImage(Work w)throws IOException {
        Candidate candidate=w.candidate;
        if(candidate.closeFailure!=null)throw new IOException("DURABLE_CANDIDATE_CLOSE_UNCONFIRMED",candidate.closeFailure);
        if(candidate.closed) {
            if(candidate.forced)return true;
            if(candidate.created) { restartUnpublished(w);return false; }
            candidate.closed=false;
        }
        if(candidate.channel!=null&&!candidate.channel.isOpen()) {
            // An interrupt may close the original channel. Never reopen its pathname
            // for writing, even when the bytes there look like our partial candidate.
            closeCandidate(candidate);
            if(candidate.created) { restartUnpublished(w);return false; }
            candidate.closed=false;candidate.complete=false;candidate.forced=false;candidate.position=0;
        }
        if(candidate.channel==null) {
            Path path=payloads.resolve(w.id+".nbt.gz");
            try {
                candidate.channel=FileChannel.open(path,StandardOpenOption.CREATE_NEW,StandardOpenOption.READ,StandardOpenOption.WRITE);
                candidate.created=true;
            } catch(FileAlreadyExistsException prior) {
                // No content writes are allowed on this branch. Use the same opened
                // object for strict comparison and force; equality is not a force receipt.
                candidate.channel=FileChannel.open(path,StandardOpenOption.READ,StandardOpenOption.WRITE);
            }
        }
        w.state="PERSISTING_CANDIDATE";w.reason="ORIGINAL_PRIVATE_CANDIDATE_CONTINUES";
        if(!candidate.complete) {
            byte[] expected=new byte[65536],actual=candidate.created?null:new byte[65536];
            try {
                if(!candidate.created)candidate.position=0;
                for(;;) {
                    int count=candidate.image.read(candidate.position,expected,0,expected.length);if(count<0)break;
                    if(candidate.created) {
                        ByteBuffer buffer=ByteBuffer.wrap(expected,0,count);
                        while(buffer.hasRemaining()) {
                            int written=candidate.channel.write(buffer,candidate.position);
                            if(written==0)return false;
                            candidate.position+=written;
                        }
                    } else {
                        ByteBuffer buffer=ByteBuffer.wrap(actual,0,count);long position=candidate.position;
                        while(buffer.hasRemaining()) {
                            int read=candidate.channel.read(buffer,position);
                            if(read<0)throw new IOException("DURABLE_IMAGE_KEY_CONFLICT");
                            if(read==0)return false;position+=read;
                        }
                        if(!Arrays.equals(expected,0,count,actual,0,count))throw new IOException("DURABLE_IMAGE_KEY_CONFLICT");
                        candidate.position=position;
                    }
                }
                if(!candidate.created) {
                    ByteBuffer tail=ByteBuffer.allocate(1);
                    int read=candidate.channel.read(tail,candidate.position);
                    if(read==0)return false;
                    if(read!=-1)throw new IOException("DURABLE_IMAGE_KEY_CONFLICT");
                }
                candidate.complete=true;
            } catch(IOException failure) {
                if(!candidate.created)try {
                    closeCandidate(candidate);candidate.closed=false;candidate.position=0;
                } catch(IOException closeFailure) { failure.addSuppressed(closeFailure); }
                throw failure;
            } finally { Arrays.fill(expected,(byte)0);if(actual!=null)Arrays.fill(actual,(byte)0); }
        }
        if(!candidate.forced) { candidate.channel.force(true);candidate.forced=true; }
        closeCandidate(candidate);return true;
    }
    private static void closeCandidate(Candidate candidate)throws IOException {
        if(candidate.closeFailure!=null)throw new IOException("DURABLE_CANDIDATE_CLOSE_UNCONFIRMED",candidate.closeFailure);
        if(candidate.channel!=null) {
            try { candidate.channel.close(); }
            // FileChannel marks itself closed before its close implementation runs.
            // A later no-op close cannot turn an earlier IOException into a receipt.
            catch(IOException failure) { candidate.closeFailure=failure;throw failure; }
            candidate.channel=null;
        }
        candidate.closed=true;
    }
    private static void retireCandidate(Work w)throws IOException {
        Candidate candidate=w.candidate;if(candidate==null)return;
        closeCandidate(candidate);candidate.image.close();w.candidate=null;w.transientReleased=w.ticket==null;
    }
    private void restartUnpublished(Work original)throws IOException {
        Candidate candidate=original.candidate;
        if(original.descriptor!=null||original.originalIntent||original.intentAck!=null||!candidate.created||!candidate.closed)
            throw new IOException("PUBLISHED_OR_UNOWNED_CANDIDATE_CANNOT_RESTART");
        Descriptor d=candidate.descriptor;
        for(;;) {
            UUID id=UUID.randomUUID();Work next=new Work(id,original);
            next.candidate=new Candidate(new Descriptor(d.protocol,id,d.operation,d.subject,d.qualification,d.source,d.code,
                    d.saveGeneration,d.identity,d.baseline,d.expected,d.preserved,d.session,d.backend,d.origins),candidate.image);
            next.transientReleased=false;next.reason="RETRY_AFTER_CLOSED_PRIVATE_CANDIDATE:"+original.id;
            if(work.putIfAbsent(id,next)!=null) { next.candidate.image.close();continue; }
            index(next);
            // The incomplete file is retained. There is no path-based deletion or
            // overwrite, and no published descriptor is abandoned by this retry.
            candidate.image.close();original.candidate=null;original.transientReleased=true;
            if(work.remove(original.id,original))unindex(original);changes.incrementAndGet();return;
        }
    }
    private RecoveryImage.Encoded loadImage(Descriptor descriptor)throws IOException {
        RecoveryImage.Encoded bytes=null;
        try(var input=Files.newInputStream(payloads.resolve(descriptor.id+".nbt.gz"))) { bytes=RecoveryImage.capture(input); }
        catch(Throwable failure) { if(bytes!=null)bytes.close();throw failure; }
        if(!bytes.hash().equals(descriptor.expected)) {
            bytes.close();throw new IOException("DURABLE_CANDIDATE_HASH_MISMATCH");
        }
        return bytes;
    }
    private void ack(String record)throws IOException {
        CompletableFuture<Void> ack=runtime.journal.append(record);
        awaitAck(ack);
    }
    private static void awaitAck(CompletableFuture<Void> ack)throws IOException {
        try { ack.get(10,TimeUnit.SECONDS); }
        catch(InterruptedException ex) { Thread.currentThread().interrupt();throw new IOException("ACK_INTERRUPTED_UNKNOWN",ex); }
        catch(ExecutionException|TimeoutException ex) { throw new IOException("ACK_UNCONFIRMED_NO_REPLAY",ex); }
    }
    private static String fact(String kind,Work w,String detail) { return PREFIX+"\t"+kind+"\t"+w.id+"\t"+text(detail); }
    private static String text(String value) { return JournalLineage.encodeText(value); }
    private static String untext(String value) { return JournalLineage.decodeText(value); }
    List<Status> status(UUID operation) {
        return byOperation.getOrDefault(operation,List.of()).stream()
                .map(w->new Status(w.id,w.operation,w.state,w.reason,w.nativeReleased,w.transientReleased)).toList();
    }
    String gap() { return gap; }
    @Override public void close()throws IOException {
        closing=true;worker.shutdown();
        try { if(!worker.awaitTermination(15,TimeUnit.SECONDS))throw new IOException("STORAGE_WORKER_EXIT_NOT_OBSERVED"); }
        catch(InterruptedException ex) { Thread.currentThread().interrupt();throw new IOException(ex); }
        IOException candidateClose=null;
        for(Work w:work.values()) {
            try { retireCandidate(w); }
            catch(IOException failure) { if(candidateClose==null)candidateClose=failure;else candidateClose.addSuppressed(failure); }
            if(w.queryClosePending)try { closeQueryInput(w); }
            catch(IOException failure) { if(candidateClose==null)candidateClose=failure;else candidateClose.addSuppressed(failure); }
            if(w.ticket!=null) {
                if(w.ticket.token==0&&w.nativeReleased) { release(w);continue; }
                long[] retired=StorageNative.finish0(w.ticket,w.ticket.token,null);
                w.nativeReleased=retired!=null&&retired.length==3&&retired[1]==1;
                if(w.nativeReleased)release(w);else gap="NATIVE_RETIREMENT_PENDING_AT_STOP";
            }
        }
        for(Work w:work.values())if(!w.queryNativeClosed&&w.transaction!=null) {
            long[] query=StorageNative.queryTransaction0(w.transaction);
            w.queryNativeClosed=query!=null&&query.length==3&&query[1]==1;
        }
        // Historic transaction uncertainty is not a handle owned by this JVM. The
        // unknown effect stays in the journal; only actual current-session resources block close.
        // Stop is not a settlement receipt. Durable work remains in the journal.
        if(candidateClose!=null)throw candidateClose;
        if(work.values().stream().anyMatch(w->w.candidate!=null||w.ticket!=null&&!w.nativeReleased||w.queryClosePending||w.queryInput!=null||!w.queryNativeClosed))
            throw new IOException("STORAGE_RESOURCES_RETAINED_FOR_RETIREMENT");
    }
}
