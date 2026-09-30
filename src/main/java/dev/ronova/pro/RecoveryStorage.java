package dev.ronova.pro;

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
    private final ThreadPoolExecutor worker;
    private volatile boolean closing;
    private long cursor;
    private String gap="";
    private final java.util.concurrent.atomic.AtomicLong changes=new java.util.concurrent.atomic.AtomicLong();
    record Status(UUID id,UUID operation,String state,String reason,boolean nativeReleased,boolean transientReleased) { }
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
        final String source;
        volatile ProRuntime.Subject authority;
        RecoverySources.Snapshot snapshot;
        volatile Descriptor descriptor;
        volatile String state="WAIT_PREPARE",reason="";
        volatile boolean running,nativeReleased=true,transientReleased=true;
        volatile long retryAt;
        Ticket ticket;
        FileInputStream queryInput;
        boolean queryClosePending;
        boolean originalIntent,commitPossible;
        CompletableFuture<Void> intentAck;
        String transaction;
        UUID transactionSession;
        boolean queryNativeClosed=true,restartRetry;
        long reboundGeneration;
        volatile boolean resourcesConfirmed;
        boolean readbackEverConfirmed;
        UUID supersededBy;
        volatile long lastQuery=-1;
        long preparedSourceEpoch=-1;
        Work(UUID id,UUID operation,ProRuntime.Subject authority,RecoverySources.Snapshot snapshot) {
            this.id=id;this.operation=operation;this.authority=authority;this.subject=authority.id;
            qualification=authority.generation;this.snapshot=snapshot;source=snapshot.source.locator;saveGeneration=snapshot.generation;
        }
        Work(Descriptor descriptor) {
            this.descriptor=descriptor;id=descriptor.id;operation=descriptor.operation;subject=descriptor.subject;
            qualification=descriptor.qualification;source=descriptor.source;saveGeneration=descriptor.saveGeneration;
            authority=null;state="RESTART_QUERY";originalIntent=true;
            nativeReleased=false;
        }
    }
    /** Only this executor can construct tickets; Native rejects arbitrary request classes. */
    static final class Ticket {
        private final Work work;
        private final RecoverySources.Source source;
        private byte[] image;
        private final Map<Long,long[]> retirements=new HashMap<>();
        private Ticket(Work work,RecoverySources.Source source,byte[] image) { this.work=work;this.source=source;this.image=image; }
        private String path() { return source.path.toString(); }
        private boolean validationFault(String point) {
            try { ValidationFaults.hit(point);return false; }catch(IOException injected) { return true; }
        }
        private String fileIdentity() { return work.descriptor.identity; }
        private String baselineHash() { return work.descriptor.baseline; }
        private String expectedHash() { return work.descriptor.expected; }
        private synchronized byte[] encoded() { return image==null?null:image.clone(); }
        private synchronized void nativeRetired(long token,long outcome,boolean closed,long error) {
            if(token>0&&closed)retirements.put(token,new long[]{outcome,1,error});
        }
        private void nativeAllocated(long value) { token=value;work.nativeReleased=false; }
        private void nativeTransaction(String value) {
            if(value==null||!value.matches("[0-9a-f]{32}"))throw new IllegalArgumentException("INVALID_TRANSACTION_ID");
            work.transaction=value;work.transactionSession=source.owner.runtimeSession();
        }
        private synchronized long[] nativeRetirement(long token) {
            long[] result=retirements.get(token);return result==null?new long[]{0,0,0}:result.clone();
        }
        private synchronized void release() { if(image!=null)Arrays.fill(image,(byte)0);image=null; }
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
                    &&(w.restartRetry?ticket.source.owner.current(ticket.source)&&ticket.source.writers==0
                        &&ticket.source.generation==w.reboundGeneration:ticket.source.current(w.snapshot))
                    &&w.preparedSourceEpoch==ticket.source.owner.epoch()
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
        if(closing||!subject.terminal)return;
        for(Work item:work.values())if(item.subject.equals(subject.id)&&item.qualification==subject.generation)
            item.authority=subject;
        for(RecoverySources.Source source:sources.registered()) {
            RecoverySources.Snapshot snapshot=source.snapshot;
            if(snapshot==null||!source.subjects.contains(subject.id))continue;
            boolean exists=work.values().stream().anyMatch(w->w.subject.equals(subject.id)&&w.qualification==subject.generation
                    &&w.source.equals(source.locator)&&w.saveGeneration==snapshot.generation);
            if(!exists) {
                // Durable work is not evicted to free an active slot. Backpressure is an explicit remaining obligation.
                if(work.size()>=65536) { gap="STORAGE_OBLIGATION_CAPACITY";return; }
                UUID id=UUID.randomUUID();work.put(id,new Work(id,operation,subject,snapshot));changes.incrementAndGet();
            }
        }
    }
    void tick(long tick) {
        if(closing)return;
        List<Work> pending=new ArrayList<>(work.values());
        int count=Math.min(16,pending.size());
        for(int i=0;i<count;i++) {
            Work w=pending.get((int)Math.floorMod(cursor++,pending.size()));
            if(superseded(w))continue;
            if(w.running||tick<w.retryAt||w.state.equals("FENCED")
                    &&w.nativeReleased&&w.transientReleased&&!w.queryClosePending&&successor(w)==null)continue;
            w.retryAt=tick+(w.state.equals("READBACK_CONFIRMED")?5:20);
            w.running=true;
            try { worker.execute(()->{
                String before=w.state+":"+w.reason+":"+w.nativeReleased+":"+w.transientReleased+":"+w.resourcesConfirmed;
                try { advance(w); }
                catch(Throwable failure) { w.state=w.commitPossible?"COMMIT_QUERY_REQUIRED":"PREPARE_UNRESOLVED";w.reason=failure.getClass().getSimpleName()+":"+failure.getMessage(); }
                finally { w.running=false;if(!before.equals(w.state+":"+w.reason+":"+w.nativeReleased+":"+w.transientReleased+":"+w.resourcesConfirmed))changes.incrementAndGet(); }
            }); }
            catch(RejectedExecutionException budget) { w.running=false;w.reason="STORAGE_WORKER_BUDGET_WAIT"; }
        }
    }
    private void advance(Work w)throws IOException {
        if(!StorageNative.available()) { w.state="UNSUPPORTED";w.reason=StorageNative.reason();return; }
        StorageNative.maintain0();
        if(w.queryClosePending) {
            w.queryInput.close();w.queryInput=null;w.queryClosePending=false;
        }
        if(w.ticket!=null&&!w.nativeReleased) {
            long[] retired=w.ticket.nativeRetirement(w.ticket.token);
            if(retired[1]!=1) { w.reason="NATIVE_RESOURCE_RETIREMENT_PENDING";return; }
            w.nativeReleased=true;
        }
        Work newer=successor(w);
        if(newer!=null&&!w.readbackEverConfirmed&&!w.commitPossible&&w.nativeReleased&&!w.queryClosePending
                &&(w.descriptor==null||w.descriptor.session.equals(runtime.session))) {
            release(w);
            if(w.descriptor==null) {
                // Only an unpersisted preparation cache is removed. It never had write authority.
                work.remove(w.id,w);changes.incrementAndGet();return;
            }
            ack(fact("NONCOMMIT",w,"SUPERSEDED_BEFORE_NATIVE_COMMIT|"+newer.id));
            ack(fact("RESOURCES",w,"UNCOMMITTED_PREPARATION_REFERENCES_RELEASED"));
            w.resourcesConfirmed=true;w.supersededBy=newer.id;w.state="SUPERSEDED_WITHOUT_EXECUTION";return;
        }
        if(w.state.equals("FENCED")) { release(w);return; }
        if(w.state.equals("READBACK_CONFIRMED")) {
            query(w);return;
        }
        if(w.descriptor!=null&&(!w.restartRetry&&w.snapshot==null||w.authority==null||w.commitPossible)) { query(w);return; }
        if(w.ticket!=null&&w.nativeReleased)releaseBuffers(w);
        RecoverySources.Snapshot snapshot=w.snapshot;
        RecoverySources.Source source=w.restartRetry?sources.resolve(w.descriptor.source,w.descriptor.code):snapshot==null?null:snapshot.source;
        if(source==null) { w.state="WAIT_SOURCE";w.reason="ORIGINAL_SOURCE_NOT_LOADED";return; }
        try(var storageGate=source.gate.enter()) {
            if(w.restartRetry?source.writers!=0||source.generation!=w.reboundGeneration||!sources.current(source):!source.current(snapshot)) {
                w.state="FENCED";w.reason="SAVE_GENERATION_OR_OWNER_CHANGED";release(w);return;
            }
        }
        try(var authorityGate=w.authority.commitGate.enter()) {
            if(closing||!w.authority.terminal||w.authority.generation!=w.qualification) { w.state="FENCED";w.reason="QUALIFICATION_CHANGED";release(w);return; }
        }
        prepareIntent(w);
        if(w.descriptor.baseline.equals(w.descriptor.expected)) { query(w);return; }
        if(!prepareKnownSources(w)) { w.state="WAIT_SOURCE_INTENTS";w.reason="KNOWN_SAVE_OBLIGATIONS_NOT_ALL_DURABLE";return; }
        byte[] image=loadImage(w.descriptor);
        Ticket ticket=new Ticket(w,source,image);w.ticket=ticket;w.transientReleased=false;w.resourcesConfirmed=false;
        long[] staged=StorageNative.prepare0(ticket);
        if(staged==null||staged.length!=3)throw new IOException("INVALID_NATIVE_PREPARE_RESULT");
        if(ticket.token!=0&&ticket.token!=staged[1])throw new IOException("NATIVE_TOKEN_BINDING_CHANGED");
        ticket.token=staged[1];w.nativeReleased=ticket.token==0||ticket.nativeRetirement(ticket.token)[1]==1;
        if(staged[0]!=1) {
            w.state=staged[0]==4?"UNSUPPORTED":staged[0]==5?"BASELINE_CONFLICT":"PREPARE_UNRESOLVED";
            w.reason="NATIVE_PREPARE:"+Arrays.toString(staged);ack(fact("PREPARE_FAILED",w,w.reason));
            if(w.nativeReleased)releaseBuffers(w);return;
        }
        w.nativeReleased=false;
        try {
            // If this record is absent after restart, this code could not have called commit.
            if(w.transaction==null)throw new IOException("TRANSACTION_ID_NOT_CAPTURED");
            ack(fact("TRANSACTION",w,BACKEND+"|"+w.transaction+"|"+w.transactionSession));
            ack(fact("COMMIT_ARMED",w,"backend="+BACKEND));w.commitPossible=true;
            ValidationFaults.hit("BEFORE_COMMIT");
            long[] result;
            try(var authorityGate=w.authority.commitGate.enter()) {
                try(var storageGate=source.gate.enter()) {
                    result=StorageNative.finish0(ticket,ticket.token,new Permit(this,ticket));
                }
            }
            if(result==null||result.length!=3)throw new IOException("INVALID_NATIVE_FINISH_RESULT");
            if(result[0]==1)ValidationFaults.hit("AFTER_COMMIT");
            w.nativeReleased=result[1]==1;
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
            if(!w.nativeReleased) {
                long[] rolled=StorageNative.finish0(ticket,ticket.token,null);
                if(rolled!=null&&rolled.length==3) {
                    w.nativeReleased=rolled[1]==1;
                    if(rolled[0]==2) { w.commitPossible=false;ack(fact("ROLLBACK",w,"EXCEPTION_PATH_CONFIRMED")); }
                    else { w.commitPossible=true;w.state=rolled[0]==1?"COMMIT_QUERY_REQUIRED":"EFFECT_UNKNOWN";w.reason="EXCEPTION_PATH_NATIVE_OUTCOME:"+rolled[0]; }
                } else { w.commitPossible=true;w.state="EFFECT_UNKNOWN"; }
                if(w.nativeReleased)releaseBuffers(w);
            }
        }
    }
    private void query(Work w)throws IOException {
        ValidationFaults.hit("READBACK");
        Descriptor d=w.descriptor;
        if(!w.nativeReleased&&w.ticket==null&&!d.session.equals(runtime.session)) {
            if(w.transaction!=null) {
                long[] transaction=StorageNative.queryTransaction0(w.transaction);
                w.queryNativeClosed=transaction!=null&&transaction.length==3&&transaction[1]==1;
                w.nativeReleased=w.queryNativeClosed&&(transaction[0]==1||transaction[0]==2
                        ||transaction[0]==4&&w.transactionSession!=null&&runtime.journal.processEnded(w.transactionSession));
            } else w.nativeReleased=!w.commitPossible&&runtime.journal.processEnded(d.session);
        }
        RecoverySources.Source source=sources.resolve(d.source,d.code);
        if(source==null) { w.state="WAIT_SOURCE";w.reason="REGISTERED_SOURCE_NOT_LOADED_OR_CODE_CHANGED";return; }
        byte[] actual;
        String identityBefore,identityAfter;
        FileInputStream input=new FileInputStream(source.path.toFile());w.queryInput=input;
        try {
            identityBefore=StorageNative.identity(input.getFD(),source.path.toString());
            actual=input.readNBytes(RecoveryImage.MAX_BYTES+1);
            identityAfter=StorageNative.identity(input.getFD(),source.path.toString());
        } finally {
            w.queryClosePending=true;
            input.close();w.queryInput=null;w.queryClosePending=false;
        }
        // A successful independent close above is part of the readback fact.
        try {
            if(!d.identity.equals(identityBefore)||!d.identity.equals(identityAfter)) { w.state="IDENTITY_CONFLICT";w.reason="INDEPENDENT_READ_FILE_IDENTITY_CHANGED";return; }
            if(actual.length>RecoveryImage.MAX_BYTES)throw new IOException("READBACK_IMAGE_LIMIT");
            String hash=RecoveryImage.hash(actual);
            if(hash.equals(d.expected)&&RecoveryImage.semanticHash(RecoveryImage.decode(actual)).equals(d.preserved)) {
                if(!w.state.equals("READBACK_CONFIRMED"))ack(fact("READBACK",w,"IDENTITY_EXPECTED_IMAGE_AND_FULL_PROJECTION_CONFIRMED"));
                w.readbackEverConfirmed=true;w.state="READBACK_CONFIRMED";w.reason="TASK_MEMORY_SOURCE_AND_RESOURCES_STILL_SEPARATE";
                if(w.nativeReleased&&w.queryNativeClosed&&!w.resourcesConfirmed) { release(w);ack(fact("RESOURCES",w,"EXECUTOR_BUFFER_AND_NATIVE_HANDLES_RELEASED"));w.resourcesConfirmed=true; }
                w.lastQuery=Integer.toUnsignedLong(runtime.server.getTickCount());
            } else {
                w.state=w.commitPossible?"COMMIT_QUERY_REQUIRED":"RESTART_QUERY";
                w.reason=hash.equals(d.baseline)?"BASELINE_PRESENT_OLD_TRANSACTION_NONCOMMIT_NOT_PROVEN":"NEITHER_EXPECTED_NOR_BASELINE_IMAGE";
                if(hash.equals(d.baseline))requalifyOriginal(w,source);
            }
        } finally { Arrays.fill(actual,(byte)0); }
    }
    /** Private candidates and descriptors are prepared on this worker, outside Root gates.
     * A crash in the first source's commit cannot discard the other already-known saves. */
    private boolean prepareKnownSources(Work current)throws IOException {
        long epoch=sources.epoch();int budget=16;
        List<Work> related=work.values().stream().filter(item->item.subject.equals(current.subject)
                &&item.qualification==current.qualification).toList();
        for(Work item:related)if(!item.originalIntent&&item.snapshot!=null&&item.snapshot.source.current(item.snapshot)) {
            if(budget--==0)return false;prepareIntent(item);
        }
        Map<String,Set<Long>> captured=new HashMap<>();
        for(Work item:related)if(item.originalIntent)captured.computeIfAbsent(item.source,key->new HashSet<>()).add(item.saveGeneration);
        for(RecoverySources.Source source:sources.registered()) {
            RecoverySources.Snapshot image=source.snapshot;
            if(image!=null&&source.subjects.contains(current.subject)
                    &&!captured.getOrDefault(source.locator,Set.of()).contains(image.generation))return false;
        }
        if(sources.epoch()!=epoch)return false;
        current.preparedSourceEpoch=epoch;return true;
    }
    private void prepareIntent(Work w)throws IOException {
        if(w.descriptor==null) {
            RecoverySources.Snapshot snapshot=w.snapshot;if(snapshot==null)throw new IOException("SAVE_SNAPSHOT_NOT_AVAILABLE");
            byte[] baseline=snapshot.bytes();RecoveryImage.Projection projection;
            try {
                projection=snapshot.selections.stream().anyMatch(selection->selection.subject().equals(w.subject))?
                        RecoveryImage.project(baseline,snapshot.selections,w.subject):
                        new RecoveryImage.Projection(baseline.clone(),RecoveryImage.hash(baseline),RecoveryImage.hash(baseline),RecoveryImage.semanticHash(RecoveryImage.decode(baseline)));
            } finally { Arrays.fill(baseline,(byte)0); }
            Descriptor descriptor=new Descriptor(PREFIX,w.id,w.operation,w.subject,w.qualification,snapshot.source.locator,
                    snapshot.source.code,snapshot.generation,snapshot.identity,projection.baselineHash(),projection.expectedHash(),
                    projection.preservedHash(),runtime.session,BACKEND,encodePaths(snapshot.selections.stream().filter(selection->selection.subject().equals(w.subject)).map(RecoveryImage.Selection::path).toList()));
            try { persistImage(w.id,projection.encoded()); }finally { Arrays.fill(projection.encoded(),(byte)0); }
            w.descriptor=descriptor;w.intentAck=runtime.journal.append(descriptor.record());
        }
        if(!w.originalIntent) {
            w.intentAck=runtime.journal.retryRejected(w.descriptor.record(),w.intentAck);
            awaitAck(w.intentAck);w.originalIntent=true;
        }
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
        if(authority==null||!authority.terminal||authority.generation!=w.qualification) { w.reason="NONCOMMIT_CONFIRMED_CURRENT_QUALIFICATION_REQUIRED";return; }
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
        for(Work item:work.values())if(item.operation.equals(operation))key.append('|').append(item.id).append(':')
            .append(item.saveGeneration).append(':').append(item.state).append(':').append(item.commitPossible)
            .append(':').append(item.nativeReleased).append(':').append(item.transientReleased).append(':').append(item.resourcesConfirmed)
            .append(':').append(item.supersededBy);
        return key.toString();
    }
    long epoch() { return changes.get(); }
    void readObserved(Path path,String identity,byte[] bytes,net.minecraft.nbt.CompoundTag decoded) {
        String hash=RecoveryImage.hash(bytes);
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
            output.flush();if(buffer.size()>32768)throw new IllegalArgumentException("DURABLE_ORIGIN_DESCRIPTOR_LIMIT");
            return Base64.getUrlEncoder().withoutPadding().encodeToString(buffer.toByteArray());
        } catch(IOException impossible) { throw new IllegalArgumentException("ORIGIN_ENCODING_UNAVAILABLE",impossible); }
    }
    private static List<List<RecoveryImage.Step>> decodePaths(String encoded) {
        try {
            if(encoded.length()>44000)throw new IOException("ORIGIN_LIMIT");
            var input=new DataInputStream(new ByteArrayInputStream(Base64.getUrlDecoder().decode(encoded)));
            int count=input.readInt();if(count<0||count>4096)throw new IOException("ORIGIN_COUNT");
            List<List<RecoveryImage.Step>> paths=new ArrayList<>();
            for(int i=0;i<count;i++) {
                int length=input.readInt();if(length<0||length>128)throw new IOException("ORIGIN_DEPTH");
                List<RecoveryImage.Step> path=new ArrayList<>();
                for(int p=0;p<length;p++)path.add(input.readBoolean()?RecoveryImage.Step.key(input.readUTF()):RecoveryImage.Step.index(input.readInt()));
                paths.add(List.copyOf(path));
            }
            if(input.available()!=0||!encodePaths(paths).equals(encoded))throw new IOException("NONCANONICAL_ORIGIN");
            return List.copyOf(paths);
        } catch(IOException|IllegalArgumentException invalid) { throw new IllegalArgumentException("ORIGINAL_PATHS_INVALID",invalid); }
    }
    private boolean superseded(Work old) {
        if(!old.nativeReleased||!old.transientReleased||!old.resourcesConfirmed||old.queryClosePending||!old.queryNativeClosed)return false;
        Work newer=successor(old);
        return newer!=null&&(old.readbackEverConfirmed||newer.id.equals(old.supersededBy));
    }
    private Work successor(Work old) {
        long now=Integer.toUnsignedLong(runtime.server.getTickCount());
        for(Work newer:work.values())if(newer!=old&&newer.subject.equals(old.subject)&&newer.qualification==old.qualification
                &&newer.source.equals(old.source)&&newer.saveGeneration>old.saveGeneration&&newer.state.equals("READBACK_CONFIRMED")
                &&newer.resourcesConfirmed&&newer.nativeReleased&&newer.transientReleased&&newer.lastQuery>=0&&now-newer.lastQuery<=10)return newer;
        return null;
    }
    List<String> remaining(UUID operation) {
        List<String> out=new ArrayList<>();if(!gap.isEmpty())out.add(gap);
        for(Work item:work.values())if(item.operation.equals(operation)) {
            if(superseded(item))continue;
            if(!item.state.equals("READBACK_CONFIRMED"))out.add("STORAGE:"+item.id+":"+item.state+":"+item.reason);
            long now=Integer.toUnsignedLong(runtime.server.getTickCount());
            if(item.lastQuery<0||now<item.lastQuery||now-item.lastQuery>10)out.add("STORAGE_READBACK_EXPIRED:"+item.id);
            if(item.running&&!item.state.equals("READBACK_CONFIRMED")||!item.nativeReleased||!item.transientReleased||!item.resourcesConfirmed||item.queryClosePending||!item.queryNativeClosed)
                out.add("STORAGE_RESOURCES_PENDING:"+item.id);
        }
        return List.copyOf(out);
    }
    private void release(Work w) {
        if(!w.nativeReleased)return;
        releaseBuffers(w);
        // Snapshot belongs to its registered source; releasing this reference is not claiming source disposal.
        w.snapshot=null;
    }
    private void releaseBuffers(Work w) {
        if(!w.nativeReleased)return;
        if(w.ticket!=null)w.ticket.release();w.ticket=null;w.transientReleased=true;
    }
    private void persistImage(UUID id,byte[] image)throws IOException {
        Path path=payloads.resolve(id+".nbt.gz");
        try(var output=FileChannel.open(path,StandardOpenOption.CREATE_NEW,StandardOpenOption.WRITE)) {
            ByteBuffer buffer=ByteBuffer.wrap(image);while(buffer.hasRemaining())output.write(buffer);output.force(true);
        } catch(FileAlreadyExistsException prior) {
            byte[] existing;
            try(var input=Files.newInputStream(path)) { existing=input.readNBytes(RecoveryImage.MAX_BYTES+1); }
            try { if(!Arrays.equals(existing,image))throw new IOException("DURABLE_IMAGE_KEY_CONFLICT"); }
            finally { Arrays.fill(existing,(byte)0); }
        }
    }
    private byte[] loadImage(Descriptor descriptor)throws IOException {
        byte[] bytes;
        try(var input=Files.newInputStream(payloads.resolve(descriptor.id+".nbt.gz"))) { bytes=input.readNBytes(RecoveryImage.MAX_BYTES+1); }
        if(bytes.length>RecoveryImage.MAX_BYTES||!RecoveryImage.hash(bytes).equals(descriptor.expected)) {
            Arrays.fill(bytes,(byte)0);throw new IOException("DURABLE_CANDIDATE_HASH_MISMATCH");
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
    private static String text(String value) { return Base64.getUrlEncoder().withoutPadding().encodeToString(value.getBytes(StandardCharsets.UTF_8)); }
    private static String untext(String value) { return new String(Base64.getUrlDecoder().decode(value),StandardCharsets.UTF_8); }
    List<Status> status(UUID operation) {
        return work.values().stream().filter(w->w.operation.equals(operation))
                .map(w->new Status(w.id,w.operation,w.state,w.reason,w.nativeReleased,w.transientReleased)).toList();
    }
    String gap() { return gap; }
    @Override public void close()throws IOException {
        closing=true;worker.shutdown();
        try { if(!worker.awaitTermination(15,TimeUnit.SECONDS))throw new IOException("STORAGE_WORKER_EXIT_NOT_OBSERVED"); }
        catch(InterruptedException ex) { Thread.currentThread().interrupt();throw new IOException(ex); }
        for(Work w:work.values()) {
            if(w.queryClosePending) { w.queryInput.close();w.queryInput=null;w.queryClosePending=false; }
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
        if(work.values().stream().anyMatch(w->w.ticket!=null&&!w.nativeReleased||w.queryClosePending||w.queryInput!=null||!w.queryNativeClosed))
            throw new IOException("STORAGE_RESOURCES_RETAINED_FOR_RETIREMENT");
    }
}
