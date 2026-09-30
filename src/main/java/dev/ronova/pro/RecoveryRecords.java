package dev.ronova.pro;

import java.io.IOException;
import java.lang.ref.WeakReference;
import java.lang.reflect.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.saveddata.SavedData;

/** Exact holder-edge disposal; never treats a matching UUID or equal NBT as object identity. */
final class RecoveryRecords implements AutoCloseable {
    private static final String PREFIX="RECORD/1";
    private final ProRuntime runtime;
    private final RecoverySources sources;
    private final RecoveryReferences references;
    private final Map<UUID,Policy> policies=new LinkedHashMap<>();
    private final Map<RecoverySources.Source,Scan> scans=new IdentityHashMap<>();
    private final Map<UUID,Work> work=new LinkedHashMap<>();
    private final List<String> historical=new ArrayList<>();
    private long cursor,priorCursor;
    private volatile boolean closing;
    private final ThreadPoolExecutor executor=new ThreadPoolExecutor(1,1,0,TimeUnit.MILLISECONDS,new ArrayBlockingQueue<>(32),task->{
        Thread thread=new Thread(task,"ronova-pro-records");thread.setDaemon(true);return thread;
    },new ThreadPoolExecutor.AbortPolicy());
    private String gap="";
    private final java.util.concurrent.atomic.AtomicLong changes=new java.util.concurrent.atomic.AtomicLong();
    private final Map<UUID,Prior> prior=new LinkedHashMap<>();
    private static final class Prior {
        final UUID id,operation,subject,session;
        final String source,code,field,key;
        CompletableFuture<Void> ack;
        String record,reason="ORIGINAL_REFERENCE_RESTART_QUERY_PENDING";
        boolean current;
        Prior(String[] row) {
            id=UUID.fromString(row[2]);operation=UUID.fromString(row[3]);subject=UUID.fromString(row[4]);session=UUID.fromString(row[6]);
            source=untext(row[7]);code=untext(row[8]);field=untext(row[9]);key=untext(row[10]);
        }
    }
    private record Policy(ProRuntime.Subject subject,UUID operation,long generation) { }
    private static final class Scan {
        final RecoverySources.Source source;
        final List<Field> fields;
        int fieldIndex;
        Field field;
        Map<Object,Object> map;
        Iterator<Map.Entry<Object,Object>> entries;
        long passes;
        final Map<Field,Long> revisions=new HashMap<>();
        String state="MEMORY_SCAN_PENDING";
        final Set<String> gaps=new TreeSet<>();
        final Map<Field,Map<Object,Work>> slots=new HashMap<>();
        Scan(RecoverySources.Source source)throws IOException {
            this.source=source;fields=new ArrayList<>();
            boolean extra=false;
            boolean isStatic=source.holder instanceof Class<?>;
            Class<?> type=isStatic?(Class<?>)source.holder:source.holder.getClass();
            for(Class<?> owner=type;owner!=null&&owner!=Object.class&&owner!=SavedData.class;owner=isStatic?null:owner.getSuperclass()) {
                for(Field field:owner.getDeclaredFields()) {
                    if(Modifier.isStatic(field.getModifiers())==isStatic&&RecoveryReferences.supportedField(field))extra=true;
                    if(Modifier.isStatic(field.getModifiers())!=isStatic||!Map.class.isAssignableFrom(field.getType()))continue;
                    if(!field.trySetAccessible()) {
                        state="INACCESSIBLE_HOLDER_FIELD";gaps.add(state+":"+field.getName());continue;
                    }
                    // A mutable registered field can be queried and unlinked in this process:
                    // observe the exact map, then re-read this field at the final gate. Its
                    // full writer coverage and restart identity remain separate obligations.
                    if(!Modifier.isFinal(field.getModifiers()))
                        gaps.add("MUTABLE_HOLDER_REPLACEMENT_COVERAGE_UNPROVEN:"+field.getName());
                    fields.add(field);
                    if(fields.size()>64)throw new IOException("MEMORY_FIELD_BUDGET");
                }
            }
            fields.sort(Comparator.comparing(f->f.getDeclaringClass().getName()+"#"+f.getName()));
            if(fields.isEmpty()) {
                if(extra) { state="ADDITIONAL_EXACT_REFERENCE_ADAPTER";passes=1; }
                else { state="MEMORY_OWNER_LAYOUT_UNSUPPORTED";gaps.add(state); }
            }
        }
        Object receiver() { return source.holder instanceof Class<?>?null:source.holder; }
    }
    private static final class Work {
        final UUID id=UUID.randomUUID();
        final Policy policy;
        final Scan scan;
        final Field field;
        final Object key;
        final String intent;
        final WeakReference<Object> recordIdentity;
        final WeakReference<Object> mapIdentity;
        Map<Object,Object> map;
        Object record;
        CompoundTag recordRoot;
        volatile CompletableFuture<Void> ack,factAck,releaseAck;
        volatile String fact,release,state="WAIT_ACK",reason="";
        long retryAt;
        boolean attempted,applied,dispositionConfirmed;
        volatile boolean reinserted;
        volatile boolean running;
        Work(Policy policy,Scan scan,Field field,Object key,Map<Object,Object> map,Object record,CompoundTag recordRoot,UUID session) {
            this.policy=policy;this.scan=scan;this.field=field;this.key=key;this.map=map;this.record=record;this.recordRoot=recordRoot;
            recordIdentity=new WeakReference<>(record);mapIdentity=new WeakReference<>(map);
            intent=PREFIX+"\tINTENT\t"+id+"\t"+policy.operation+"\t"+policy.subject.id+"\t"+policy.generation+
                    "\t"+session+"\t"+text(scan.source.locator)+"\t"+text(scan.source.code)+"\t"+
                    text(field.getDeclaringClass().getName()+"#"+field.getName())+"\t"+text(key.getClass().getName()+":"+key);
            if(intent.getBytes(StandardCharsets.UTF_8).length>48000)throw new IllegalArgumentException("MEMORY_DESCRIPTOR_LIMIT");
        }
    }
    RecoveryRecords(ProRuntime runtime,RecoverySources sources) {
        this.runtime=runtime;this.sources=sources;
        references=new RecoveryReferences(runtime);
        for(String row:runtime.journal.history())if(row.startsWith("RECORD/")) {
            String[] fields=row.split("\t",-1);
            historical.add(row);
            if((fields[0].equals("RECORD/2")||fields[0].equals("RECORD/3")||fields[0].equals("RECORD/4"))&&fields.length>1&&fields[1].startsWith("EDGE_"))continue;
            if(fields[0].equals("RECORD/2")&&fields.length==5&&fields[1].equals("RESTART_QUERY"))continue;
            if(!fields[0].equals(PREFIX)||fields.length<4||!Set.of("INTENT","FACT","RELEASED").contains(fields[1])) {
                runtime.journal.refuseWrites("UNKNOWN_RECORD_PROTOCOL_QUERY_ONLY");gap="RECORD_HISTORY_UNKNOWN";
                continue;
            }
            int length=fields.length;
            if((fields[1].equals("INTENT")&&length!=11)||(fields[1].equals("FACT")&&length!=5)
                    ||(fields[1].equals("RELEASED")&&length!=4)) {
                runtime.journal.refuseWrites("INVALID_RECORD_DESCRIPTOR_ARITY");gap="RECORD_HISTORY_INVALID";
                continue;
            }
            try { UUID.fromString(fields[2]); }
            catch(IllegalArgumentException malformed) {
                runtime.journal.refuseWrites("INVALID_RECORD_OPERATION_ID");gap="RECORD_HISTORY_INVALID";continue;
            }
            if(fields[1].equals("INTENT")) {
                try {
                    for(int i:new int[]{2,3,4,6})UUID.fromString(fields[i]);
                    if(Long.parseLong(fields[5])<0)throw new IllegalArgumentException("qualification");
                    for(int i=7;i<11;i++)if(!text(untext(fields[i])).equals(fields[i]))throw new IllegalArgumentException("encoding");
                    if(!untext(fields[8]).matches("[0-9a-f]{64}"))throw new IllegalArgumentException("code locator");
                    sources.expect(untext(fields[7]),untext(fields[8]),UUID.fromString(fields[4]),0);
                    Prior item=new Prior(fields);prior.put(item.id,item);
                }
                catch(IllegalArgumentException malformed) { runtime.journal.refuseWrites("INVALID_RECORD_DESCRIPTOR");gap="RECORD_HISTORY_INVALID"; }
            }
        }
    }
    void qualify(ProRuntime.Subject subject,UUID operation) {
        if(closing||!subject.terminal)return;
        Policy prior=policies.get(subject.id);
        if(prior==null||prior.generation!=subject.generation)policies.put(subject.id,new Policy(subject,operation,subject.generation));
        references.qualify(subject,operation);
    }
    Object observedField(Object receiver,Object[] field) {return references.observeField(receiver,field);}
    void consumedFields(UUID subject,Object[][] fields) {references.consumedFields(subject,fields);}
    void creationFields(Entity entity,Object[][] fields) {references.creationFields(entity,fields);}
    void creationBound(Entity entity,UUID subject) {references.creationBound(entity,subject);}
    void identityAssigned(Entity entity,Object[] packet) {references.identityAssigned(entity,packet);}
    boolean fieldWriteDenied(Object receiver,Class<?> owner,String name,Object value) {return references.fieldWriteDenied(receiver,owner,name,value);}
    void tick(long tick) {
        if(closing)return;
        references.tick(tick);
        queryPrior();
        List<RecoverySources.Source> registered=new ArrayList<>(sources.registered());
        for(int budget=0;budget<Math.min(8,registered.size());budget++) {
            RecoverySources.Source source=registered.get((int)Math.floorMod(cursor++,registered.size()));
            if(source.subjects.stream().noneMatch(policies::containsKey))continue;
            try {
                Scan scan=scans.get(source);
                if(scan==null) { scan=new Scan(source);scans.put(source,scan); }
                scan(scan,tick);
            } catch(ReflectiveOperationException|IOException|RuntimeException unavailable) {
                gap="MEMORY_SOURCE_UNRESOLVED:"+unavailable.getClass().getSimpleName()+":"+unavailable.getMessage();
            }
        }
        List<Work> active=new ArrayList<>(work.values());
        for(int budget=0;budget<Math.min(32,active.size());budget++) {
            Work item=active.get((int)Math.floorMod(workCursor++,active.size()));
            if(item.running||tick<item.retryAt||item.state.equals("REFERENCE_DISPOSED")||item.state.equals("REINSERTION_OBSERVED")||item.state.equals("FENCED"))continue;
            item.retryAt=tick+20;
            item.running=true;
            try { executor.execute(()->{
                try { advance(item); }
                catch(ReflectiveOperationException|RuntimeException failure) {
                    item.state=item.attempted?"EFFECT_QUERY_REQUIRED":"MEMORY_SOURCE_UNRESOLVED";item.reason=failure.toString();
                } finally { item.running=false; }
            }); } catch(RejectedExecutionException busy) { item.running=false;item.reason="MEMORY_EXECUTOR_BUDGET_WAIT"; }
        }
    }
    private long workCursor;
    @SuppressWarnings("unchecked")
    private void scan(Scan scan,long tick)throws ReflectiveOperationException {
        if(!sources.current(scan.source)) { scan.state="SOURCE_OWNER_REPLACED";return; }
        if(scan.fields.isEmpty())return;
        for(int budget=0;budget<64;budget++) {
            if(scan.entries==null) {
                scan.field=scan.fields.get(scan.fieldIndex++);
                if(scan.fieldIndex==scan.fields.size())scan.fieldIndex=0;
                Object value=scan.field.get(scan.receiver());
                if(!SourceMaps.supported(value)) {
                    scan.state="MEMORY_CONTAINER_BACKEND_UNSUPPORTED";scan.gaps.add(scan.state+":"+scan.field.getName());return;
                }
                scan.map=(Map<Object,Object>)value;
                long revision=SourceMaps.revision(value);
                var snapshot=SourceMaps.entries(value);
                if(snapshot==null||revision<0) { scan.state=SourceMaps.unavailable(value);return; }
                SourceMaps.observe(value,scan.source,scan.field);
                if(!Objects.equals(scan.revisions.put(scan.field,revision),revision))changes.incrementAndGet();
                scan.entries=snapshot.iterator();
            }
            if(scan.field.get(scan.receiver())!=scan.map) {
                scan.entries=null;scan.map=null;scan.state="MEMORY_FIELD_OWNER_REPLACED";scan.gaps.add(scan.state);return;
            }
            if(!scan.entries.hasNext()) {
                scan.entries=null;scan.map=null;scan.state="REGISTERED_FIELDS_SCANNED";
                if(scan.fieldIndex==0)scan.passes++;
                return;
            }
            Map.Entry<Object,Object> entry=scan.entries.next();Object key=entry.getKey(),value=entry.getValue();
            Object target=RecoveryReferences.ownedTarget(value,runtime,true);
            if(!(target instanceof CompoundTag recordRoot)) {
                if(value!=null&&value.getClass()!=CompoundTag.class&&!(value instanceof Entity)
                        &&!(RecoveryReferences.wrapperTarget(value,runtime,false) instanceof Entity)
                        &&!RecoveryReferences.immutableScalar(value))
                    scan.gaps.add("MEMORY_WRAPPER_PROFILE_UNSUPPORTED:"+value.getClass().getName());
                continue;
            }
            if(key!=null&&(key.getClass()==String.class||key.getClass()==UUID.class)) {
                Object[] current=SourceMaps.peek(scan.map,key);
                if(current==null) { scan.state="MEMORY_SOURCE_MAP_BUSY_OR_UNOBSERVED";return; }
                if(current[1]!=value)continue;
            }
            UUID subject=RecoverySources.subject(sources,recordRoot);
            Policy policy=policies.get(subject);
            if(policy==null||!policy.subject.terminal||policy.subject.generation!=policy.generation||!scan.source.subjects.contains(subject))continue;
            if(key==null||(key.getClass()!=String.class&&key.getClass()!=UUID.class)) { scan.state="MEMORY_KEY_SEMANTICS_UNSUPPORTED";continue; }
            if(key instanceof String text&&text.length()>1024) { scan.gaps.add("MEMORY_KEY_LIMIT");continue; }
            Map<Object,Work> slots=scan.slots.computeIfAbsent(scan.field,f->new HashMap<>());
            Work prior=slots.get(key);
            if(prior!=null&&prior.mapIdentity.get()==scan.map&&prior.recordIdentity.get()==value&&prior.policy.generation==policy.generation
                    &&!prior.state.equals("REFERENCE_DISPOSED")&&!(prior.reinserted&&confirmed(prior.factAck)))continue;
            if(work.size()>=65536) { gap="MEMORY_OBLIGATION_CAPACITY";return; }
            Work item=new Work(policy,scan,scan.field,key,scan.map,value,recordRoot,runtime.session);
            item.ack=runtime.journal.append(item.intent);work.put(item.id,item);slots.put(key,item);
            changes.incrementAndGet();
            scan.state="RECORD_REFERENCE_INTENT_PENDING";
        }
        // Cursor and map are retained; the next visit resumes rather than rescanning the first entries.
        scan.state="MEMORY_SCAN_BUDGET_PENDING";
    }
    private void advance(Work item)throws ReflectiveOperationException {
        // The map scope is taken before graph/subject/source gates; its registration drains old writers.
        // The entire original query/mutation sequence stays under this scope for HashMap.
        if(item.map!=null&&item.map.getClass()==HashMap.class) {
            if(!SourceMaps.with(item.map,()->advanceLocked(item))) {
                item.state="MEMORY_SOURCE_MAP_BUSY_OR_UNOBSERVED";item.reason="SOURCE_MAP_SCOPE_NOT_READY";
            }
        } else advanceLocked(item);
    }
    private void advanceLocked(Work item)throws ReflectiveOperationException {
        item.ack=runtime.journal.retryRejected(item.intent,item.ack);
        if(item.fact!=null) {
            if(!confirmed(item.ack)) { item.state="WAIT_INTENT_ACK_FOR_FACT";item.reason="DURABLE_INTENT_ACK_"+ackState(item.ack);return; }
            item.state="WAIT_REFERENCE_FACT_ACK";item.reason="";
            item.factAck=item.factAck==null?runtime.journal.append(item.fact):runtime.journal.retryRejected(item.fact,item.factAck);
            if(!confirmed(item.factAck))return;
            // Explicitly drop executor-owned strong references only after the disposition fact is durable.
            item.record=null;item.recordRoot=null;item.map=null;
            if(item.release==null) {
                item.release=PREFIX+"\tRELEASED\t"+item.id+"\tWORK_DIRECT_FIELDS_CLEARED_OTHER_OWNERS_UNSETTLED";
                item.releaseAck=runtime.journal.append(item.release);
            } else item.releaseAck=runtime.journal.retryRejected(item.release,item.releaseAck);
            if(confirmed(item.releaseAck)) {
                item.state=item.dispositionConfirmed?"REFERENCE_DISPOSED":item.reinserted?"REINSERTION_OBSERVED":"NOT_EXECUTED_AT_STOP";
                changes.incrementAndGet();
            }
            return;
        }
        // Durable intent is an obligation, not a permission gate for this exact CHM slot.
        // Its post-effect FACT is delayed until the intent is confirmed so journal order stays valid.
        if(item.attempted) { query(item);return; } // An uncertain attempt never grants another mutation.
        if(!bound(item)) { item.state="FENCED";item.reason="SOURCE_FIELD_OR_CONTAINER_REPLACED";return; }
        boolean[] locks={false,false,false};boolean[] decided={false};
        try {
            // CHM may wait on a business writer. No Root/source gate is held while waiting for its bin lock.
            item.map.computeIfPresent(item.key,(key,value)->{
                if(value!=item.record)return value;
                if(!RecoverySources.tryRecordGraph())return value;locks[2]=true;
                if(RecoveryReferences.ownedTarget(value,runtime,true)!=item.recordRoot) {
                    item.reason="MEMORY_WRAPPER_TARGET_CHANGED";return value;
                }
                String graphGap=RecoverySources.recordGraphGap(sources,item.recordRoot,item.policy.subject.id);
                if(!graphGap.isEmpty()) { item.reason=graphGap;return value; }
                if(!item.policy.subject.commitGate.tryLock())return value;locks[0]=true;
                if(!item.scan.source.gate.tryLock())return value;locks[1]=true;
                try {
                    if(closing||!item.policy.subject.terminal||item.policy.subject.generation!=item.policy.generation
                            ||!sources.gap().isEmpty()||!sources.current(item.scan.source)||item.scan.source.writers!=0
                            ||item.field.get(item.scan.receiver())!=item.map)return value;
                    if(RecoveryReferences.ownedTarget(value,runtime,true)!=item.recordRoot) {
                        item.reason="MEMORY_WRAPPER_TARGET_CHANGED";return value;
                    }
                    item.attempted=true;item.reason="";decided[0]=true;
                    return null; // Both gates remain held until CHM has actually unlinked the exact entry.
                } catch(IllegalAccessException failure) { throw new IllegalStateException(failure); }
            });
            item.applied=decided[0];
        } finally {
            if(locks[1])item.scan.source.gate.unlock();
            if(locks[0])item.policy.subject.commitGate.unlock();
            if(locks[2])RecoverySources.releaseRecordGraph();
        }
        if(!decided[0]&&item.map.get(item.key)==item.record) {
            item.state="FINAL_GATE_OR_SAVE_BUSY";return;
        }
        query(item);
    }
    private boolean bound(Work item)throws IllegalAccessException {
        return sources.current(item.scan.source)&&item.map!=null&&item.field.get(item.scan.receiver())==item.map;
    }
    private void query(Work item)throws IllegalAccessException {
        // Query the original bound container even after revocation/unloading; never resurrect a write permit.
        if(item.map==null||item.field.get(item.scan.receiver())!=item.map) { item.state="OWNER_UNRESOLVED_AFTER_ATTEMPT";return; }
        Object current=item.map.get(item.key);
        if(current==item.record) {
            if(item.applied) {
                // The atomic command returned after removal; a later presence is a new edge, not a retry permit.
                item.reinserted=true;
                item.fact=PREFIX+"\tFACT\t"+item.id+"\tREMOVAL_EXECUTED_REINSERTION_OBSERVED\tCURRENT_REFERENCE_UNRESOLVED";
                if(confirmed(item.ack))item.factAck=runtime.journal.append(item.fact);
                item.state=confirmed(item.ack)?"WAIT_REFERENCE_FACT_ACK":"WAIT_INTENT_ACK_FOR_FACT";
                if(!confirmed(item.ack))item.reason="DURABLE_INTENT_ACK_"+ackState(item.ack);
            } else item.state=item.attempted?"EFFECT_QUERY_REQUIRED":"REFERENCE_PRESENT";
            return;
        }
        item.fact=PREFIX+"\tFACT\t"+item.id+"\t"+(item.applied?"EXACT_REFERENCE_REMOVED":"EXACT_REFERENCE_ABSENCE_OBSERVED")+
                "\tOTHER_KEYS_NOT_WRITTEN_BY_THIS_OPERATION";
        item.reason="";
        item.dispositionConfirmed=true;
        if(confirmed(item.ack))item.factAck=runtime.journal.append(item.fact);
        item.state=confirmed(item.ack)?"WAIT_REFERENCE_FACT_ACK":"WAIT_INTENT_ACK_FOR_FACT";
        if(!confirmed(item.ack))item.reason="DURABLE_INTENT_ACK_"+ackState(item.ack);
    }
    List<String> status(UUID operation) {
        var rows=new ArrayList<String>();if(!gap.isEmpty())rows.add("MEMORY_RECORD_GAP:"+gap);
        for(Work item:work.values())if(item.policy.operation.equals(operation)) {
            String current=item.state;
            if(current.equals("REFERENCE_DISPOSED")) {
                Object record=item.recordIdentity.get();
                try {
                    Object map=item.field.get(item.scan.receiver());
                    if(!sources.current(item.scan.source)||!SourceMaps.supported(map))current="HISTORICAL_FACT_SOURCE_OWNER_CHANGED";
                    else {
                        Object[] observed=SourceMaps.peek(map,item.key);
                        if(observed==null) { current="SOURCE_MAP_QUERY_PENDING";rows.add("MEMORY_RECORD:"+item.id+":"+current);continue; }
                        Object slot=observed[1];
                        if(record!=null&&slot==record)current="REFERENCE_REINSERTED";
                        else if(item.policy.subject.id.equals(recordSubject(slot)))current="NEW_SUBJECT_REFERENCE_OBSERVED";
                    }
                } catch(ReflectiveOperationException failure) { current="HISTORICAL_FACT_CURRENT_REFERENCE_UNOBSERVED"; }
            }
            rows.add("MEMORY_RECORD:"+item.id+":"+current+":"+item.reason);
        }
        for(Scan scan:scans.values())rows.add("MEMORY_SOURCE:"+scan.source.locator+":"+scan.state+":"+scan.gaps);
        if(!historical.isEmpty())rows.add("MEMORY_RESTART:ORIGINAL_REFERENCE_IDENTITIES_QUERY_ONLY;NO_POINTER_REUSE");
        rows.add("MEMORY_SCOPE:REGISTERED_EXACT_MAP_ATOMIC_AND_COPY_ON_WRITE_EDGES;UNSUPPORTED_EDGES_REPORTED_IN_REMAINING");
        return List.copyOf(rows);
    }
    String dependencies(UUID operation,UUID subject) {
        StringBuilder key=new StringBuilder(gap);
        for(Work item:work.values())if(item.policy.operation.equals(operation))key.append('|').append(item.id).append(':')
            .append(item.state).append(':').append(item.reinserted).append(':').append(item.dispositionConfirmed);
        for(var source:sources.registered())if(source.subjects.contains(subject)) {
            Scan scan=scans.get(source);if(scan==null)continue;
            for(Field field:scan.fields)try {key.append('|').append(source.locator).append(':').append(field.getName()).append(':').append(SourceMaps.revision(field.get(scan.receiver())));}
            catch(ReflectiveOperationException unavailable){key.append("|UNREADABLE:").append(source.locator);}
        }
        return key.append('|').append(references.dependencies(operation)).toString();
    }
    long epoch() { return changes.get()+references.epoch(); }
    private void queryPrior() {
        List<Prior> pending=new ArrayList<>(prior.values());
        for(int budget=0;budget<Math.min(32,pending.size());budget++) {
            Prior item=pending.get((int)Math.floorMod(priorCursor++,pending.size()));
            boolean before=item.current;item.current=false;
            try {
                var source=sources.resolve(item.source,item.code);
                if(source==null) { item.reason="REGISTERED_MEMORY_SOURCE_NOT_LOADED";continue; }
                if(!runtime.journal.processEnded(item.session)) { item.reason="PRIOR_PROCESS_REFERENCE_LIFETIME_UNPROVEN";continue; }
                int split=item.field.lastIndexOf('#');if(split<0)throw new IllegalArgumentException("FIELD_DESCRIPTOR");
                Class<?> holder=source.holder instanceof Class<?> type?type:source.holder.getClass(),declaring=null;
                for(Class<?> type=holder;type!=null;type=type.getSuperclass())if(type.getName().equals(item.field.substring(0,split))) { declaring=type;break; }
                if(declaring==null) { item.reason="ORIGINAL_FIELD_DEFINITION_CHANGED";continue; }
                Field field=declaring.getDeclaredField(item.field.substring(split+1));
                if(!Modifier.isFinal(field.getModifiers())||!field.trySetAccessible()) { item.reason="ORIGINAL_FIELD_UNSUPPORTED";continue; }
                Object map=field.get(source.holder instanceof Class<?>?null:source.holder);
                if(!SourceMaps.supported(map)) { item.reason="ORIGINAL_CONTAINER_UNSUPPORTED";continue; }
                Object key;
                if(item.key.startsWith("java.lang.String:"))key=item.key.substring(17);
                else if(item.key.startsWith("java.util.UUID:"))key=UUID.fromString(item.key.substring(15));
                else { item.reason="ORIGINAL_KEY_UNSUPPORTED";continue; }
                Object[] observed=SourceMaps.peek(map,key);
                if(observed==null) { item.reason="SOURCE_MAP_QUERY_PENDING";continue; }
                if(Boolean.TRUE.equals(observed[0])) { item.reason="ORIGINAL_SLOT_PRESENT_NEW_IDENTITY_REQUIRES_FRESH_PROVENANCE";continue; }
                item.record="RECORD/2\tRESTART_QUERY\t"+item.id+"\t"+item.operation+"\tCURRENT_REGISTERED_SLOT_ABSENT_AND_PRIOR_PROCESS_ENDED";
                item.ack=item.ack==null?runtime.journal.append(item.record):runtime.journal.retryRejected(item.record,item.ack);
                item.current=confirmed(item.ack);item.reason=item.current?"":"RESTART_REFERENCE_FACT_ACK_PENDING";
            } catch(ReflectiveOperationException|RuntimeException unavailable) { item.reason="MEMORY_RESTART_QUERY_UNAVAILABLE:"+unavailable.getClass().getSimpleName(); }
            finally { if(before!=item.current)changes.incrementAndGet(); }
        }
    }
    List<String> remaining(UUID operation,UUID subject) {
        List<String> out=new ArrayList<>();if(!gap.isEmpty())out.add(gap);
        for(Prior item:prior.values())if(item.operation.equals(operation)&&!item.current)out.add("MEMORY_RESTART:"+item.id+":"+item.reason);
        for(var source:sources.registered())if(source.subjects.contains(subject)) {
            Scan scan=scans.get(source);
            if(scan==null||scan.passes==0)out.add("MEMORY_SOURCE_SCAN_PENDING:"+source.locator);
            else { out.addAll(scan.gaps);if(!sources.current(source))out.add("MEMORY_SOURCE_OWNER_CHANGED");
                if((scan.state.equals("MEMORY_SOURCE_MAP_BUSY_OR_UNOBSERVED")||scan.state.equals("SOURCE_MAP_BUSY_OR_UNOBSERVED")||scan.state.equals("SOURCE_MAP_SNAPSHOT_CAPACITY")))out.add(scan.state+":"+source.locator);
            }
        }
        for(Work item:work.values())if(item.policy.operation.equals(operation)) {
            if(!confirmed(item.ack))out.add("MEMORY_RECORD_DURABLE_INTENT_ACK:"+item.id+":"+ackState(item.ack));
            if(!item.state.equals("REFERENCE_DISPOSED")||!confirmed(item.releaseAck)||!confirmed(item.factAck))out.add("MEMORY_RECORD:"+item.id+":"+item.state+":"+item.reason);
            else try {
                Object map=item.field.get(item.scan.receiver());
                if(!SourceMaps.supported(map)||!sources.current(item.scan.source))out.add("MEMORY_RECORD_OWNER_CHANGED:"+item.id);
                else {
                    Object[] observed=SourceMaps.peek(map,item.key);
                    if(observed==null) { out.add("SOURCE_MAP_QUERY_PENDING:"+item.id);continue; }
                    Object current=observed[1],original=item.recordIdentity.get();
                    if(original!=null&&current==original)out.add("MEMORY_RECORD_REINSERTED:"+item.id);
                    // A collected weak identity is irrelevant: the durable unlink and fresh exact slot query are the evidence.
                    if(subject.equals(recordSubject(current)))out.add("MEMORY_RECORD_NEW_SUBJECT_BINDING:"+item.id);
                }
            } catch(ReflectiveOperationException failure) { out.add("MEMORY_RECORD_QUERY_UNAVAILABLE:"+item.id); }
        }
        out.addAll(references.remaining(operation,subject));return List.copyOf(out);
    }
    private UUID recordSubject(Object value) {
        Object root=RecoveryReferences.ownedTarget(value,runtime,true);
        return root instanceof CompoundTag tag?RecoverySources.subject(sources,tag):null;
    }
    private static String ackState(CompletableFuture<Void> ack) {
        if(ack==null||!ack.isDone())return "PENDING";
        return ack.isCompletedExceptionally()||ack.isCancelled()?"UNCONFIRMED":"CONFIRMED";
    }
    private static boolean confirmed(CompletableFuture<Void> ack) { return ack!=null&&ack.isDone()&&!ack.isCompletedExceptionally()&&!ack.isCancelled(); }
    private static String text(String value) { return Base64.getUrlEncoder().withoutPadding().encodeToString(value.getBytes(StandardCharsets.UTF_8)); }
    private static String untext(String value) { return new String(Base64.getUrlDecoder().decode(value),StandardCharsets.UTF_8); }
    @Override public void close()throws IOException {
        closing=true;
        references.close();
        executor.shutdown();
        if(!executor.isTerminated())throw new IOException("MEMORY_EXECUTOR_EXIT_PENDING");
        boolean pending=false;
        for(Work item:work.values()) {
            // Stop does not convert an unexecuted intent or unknown effect to a successful release.
            if(item.attempted&&item.fact==null) {
                try { query(item); } catch(ReflectiveOperationException failure) { throw new IOException("MEMORY_RECORD_QUERY_UNRESOLVED",failure); }
                if(item.fact==null)throw new IOException("MEMORY_RECORD_EFFECT_UNKNOWN_AT_STOP");
            }
            item.ack=runtime.journal.retryRejected(item.intent,item.ack);
            if(!confirmed(item.ack)) { pending=true;continue; }
            if(item.fact==null) {
                item.fact=PREFIX+"\tFACT\t"+item.id+"\tNOT_EXECUTED_AT_STOP\tNO_DISPOSITION_CLAIM";
                item.factAck=runtime.journal.append(item.fact);
            }
            try { advance(item); }
            catch(ReflectiveOperationException failure) { throw new IOException("MEMORY_RECORD_RETIREMENT_UNRESOLVED",failure); }
            if(!confirmed(item.releaseAck))pending=true;
        }
        if(pending)throw new IOException("MEMORY_RECORD_RETIREMENT_ACK_PENDING");
        for(Scan scan:scans.values()) { scan.entries=null;scan.map=null; }
        scans.clear();policies.clear();work.clear();
    }
}
