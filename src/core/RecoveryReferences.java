package dev.ronova.pro;

import dev.ronova.pro.persistence.JournalLineage;
import java.lang.ref.WeakReference;
import java.lang.reflect.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.Entity;

/** Exact atomic/list/entity-map edges from already registered holders. No global class or file scan. */
final class RecoveryReferences implements AutoCloseable {
    private final CopyOnWriteArrayList<FieldRead> fieldReads=new CopyOnWriteArrayList<>();
    private final Map<Object,Map<Class<?>,Map<String,FieldRead>>> writerFields=Collections.synchronizedMap(new WeakIdentityMap<>());
    private final Map<Entity,List<FieldRead>> creationReads=Collections.synchronizedMap(new WeakIdentityMap<>());
    private final CopyOnWriteArrayList<Selector> selectors=new CopyOnWriteArrayList<>();
    private static final class Selector {
        final FieldRead read;final WeakReference<Entity> output;final UUID value;final boolean nullable;final String site;
        volatile UUID subject;
        volatile RecoverySources.Source source;
        Selector(FieldRead read,Entity output,UUID value,boolean nullable,String site) {
            this.read=read;this.output=new WeakReference<>(output);this.value=value;this.nullable=nullable;this.site=site;
        }
    }
    private static final class FieldRead {
        final Field field;final WeakReference<Object> holder,value;
        final Object scalar;
        final Set<UUID> consumers=ConcurrentHashMap.newKeySet();
        volatile boolean disposed;
        CopyOnWriteArrayList<Selector> selectors=new CopyOnWriteArrayList<>();
        FieldRead(Field field,Object holder,Object value) {
            this.field=field;this.holder=new WeakReference<>(holder);this.value=new WeakReference<>(value);
            scalar=field.getType().isPrimitive()||field.getType()==UUID.class?value:null;
        }
        Object value() {return scalar!=null?scalar:value.get();}
    }
    private static final ClassValue<Map<String,Field>> FIELDS=new ClassValue<>() {
        @Override protected Map<String,Field> computeValue(Class<?> type) {
            Map<String,Field> fields=new HashMap<>();
            for(Class<?> owner=type;owner!=null&&owner!=Object.class;owner=owner.getSuperclass())for(Field field:owner.getDeclaredFields())
                if(field.trySetAccessible())fields.putIfAbsent(field.getName()+":"+field.getType().descriptorString(),field);
            return Map.copyOf(fields);
        }
    };
    private static final ClassValue<Map<Field,java.lang.invoke.VarHandle>> HANDLES=new ClassValue<>() {
        @Override protected Map<Field,java.lang.invoke.VarHandle> computeValue(Class<?> type) {
            Map<Field,java.lang.invoke.VarHandle> handles=new HashMap<>();
            try {
                var lookup=java.lang.invoke.MethodHandles.privateLookupIn(type,java.lang.invoke.MethodHandles.lookup());
                for(Field field:type.getDeclaredFields())if(!Modifier.isFinal(field.getModifiers()))
                    try {handles.put(field,lookup.unreflectVarHandle(field));}catch(IllegalAccessException unavailable) { }
            }catch(IllegalAccessException inaccessible) { }
            return Map.copyOf(handles);
        }
    };
    private final ProRuntime runtime;
    private final Map<UUID,Policy> policies=new LinkedHashMap<>();
    private record Policy(ProRuntime.Subject subject,UUID operation,long generation) { }
    private final Map<UUID,Edge> edges=new LinkedHashMap<>();
    private final List<Historical> historical=new ArrayList<>();
    private long historyCursor;
    private static final class Historical {
        final UUID id,operation,subject,session;
        final String source,code,field,kind,key,prefix;
        String before,expected;
        boolean imageApplied;
        boolean current;
        long sampled=-1;
        String reason="REFERENCE_RESTART_PENDING",record;
        CompletableFuture<Void> ack;
        Historical(String[] row) {
            id=UUID.fromString(row[2]);operation=UUID.fromString(row[3]);subject=UUID.fromString(row[4]);session=UUID.fromString(row[6]);
            if(Long.parseLong(row[5])<1)throw new IllegalArgumentException("REFERENCE_QUALIFICATION");
            source=untext(row[7]);code=untext(row[8]);field=untext(row[9]);kind=row[10];key=untext(row[11]);
            prefix=row[0];
            before=row.length==14?row[12]:"";
            expected=row.length==14?row[13]:"";
            if(row.length==14&&(!row[12].isEmpty()&&!row[12].matches("[a-f0-9]{64}")||!expected.isEmpty()&&!expected.matches("[a-f0-9]{64}")))throw new IllegalArgumentException("REFERENCE_IMAGE_HASH");
            if(!(Set.of("ATOMIC","COPY_ON_WRITE_LIST","ENTITY_MAP").contains(kind)
                    ||prefix.equals("RECORD/5")&&Set.of("ARRAY_LIST","OBJECT_ARRAY").contains(kind)
                    ||prefix.equals("RECORD/6")&&kind.equals("HASH_SET")
                    ||prefix.equals("RECORD/3")&&kind.equals("DIRECT_FIELD")
                    ||prefix.equals("RECORD/4")&&kind.equals("UUID_SELECTOR"))||!code.matches("[a-f0-9]{64}"))throw new IllegalArgumentException("REFERENCE_ADAPTER");
            for(int i:new int[]{7,8,9,11})if(!text(untext(row[i])).equals(row[i]))throw new IllegalArgumentException("REFERENCE_ENCODING");
        }
    }
    private final Map<RecoverySources.Source,Set<String>> gaps=new IdentityHashMap<>();
    private final Map<RecoverySources.Source,Long> scanned=new IdentityHashMap<>();
    private final Map<RecoverySources.Source,ReferenceScan> scans=new IdentityHashMap<>();
    private static final class ReferenceScan {
        final WeakReference<Object> holder;
        final List<Field> fields=new ArrayList<>();
        final Set<String> issues=new TreeSet<>();
        Set<String> completedIssues=Set.of();
        final Map<Field,WeakReference<Object>> containers=new HashMap<>();
        final Map<Field,WeakReference<Object[]>> lists=new HashMap<>();
        final Map<Field,Long> listRevisions=new HashMap<>();
        int fieldIndex,listIndex;
        long startedMutation;
        Field field;
        Object container;
        Object[] list;
        SourceMaps.Cursor map;
        ReferenceScan(Object actual) {
            holder=new WeakReference<>(actual);
            boolean statik=actual instanceof Class<?>;
            for(Class<?> type=statik?(Class<?>)actual:actual.getClass();type!=null&&type!=Object.class;type=statik?null:type.getSuperclass())
                for(Field candidate:type.getDeclaredFields())
                    if(Modifier.isStatic(candidate.getModifiers())==statik&&!candidate.getType().isPrimitive()&&!scalarType(candidate.getType()))fields.add(candidate);
        }
        void finishField(){field=null;container=null;list=null;map=null;listIndex=0;fieldIndex++;}
    }
    private static final ClassValue<Field> LIST_ARRAY=new ClassValue<>() {
        @Override protected Field computeValue(Class<?> type) {
            try {Field field=type.getDeclaredField("array");field.setAccessible(true);return field;}
            catch(ReflectiveOperationException unavailable){throw new IllegalStateException("REFERENCE_LIST_LAYOUT_UNAVAILABLE",unavailable);}
        }
    };
    private final AtomicLong epoch=new AtomicLong();
    private long cursor;
    private long edgeCursor;
    private boolean closing;
    private String gap="";
    private final ThreadPoolExecutor worker=new ThreadPoolExecutor(1,1,0,TimeUnit.MILLISECONDS,new ArrayBlockingQueue<>(32),r->{
        Thread thread=new Thread(r,"ronova-pro-reference-edges");thread.setDaemon(true);return thread;
    },new ThreadPoolExecutor.AbortPolicy());
    private static final class Edge {
        final UUID id=UUID.randomUUID();final Policy policy;final RecoverySources.Source source;final Field field;
        final Object key;Object target;final String kind,intent,prefix;
        String beforeImage,expectedImage,imageRecord;
        final WeakReference<Object> objectIdentity,containerIdentity;
        Object object,container;
        SourceMaps.Cursor removalCursor;
        volatile boolean running,attempted,applied,observed,released;
        volatile String reason="",fact,release;
        CompletableFuture<Void> ack,factAck,releaseAck,imageAck;
        Edge(Policy policy,RecoverySources.Source source,Field field,Object key,Object container,Object object,Object target,String kind,UUID session) {
            this.policy=policy;this.source=source;this.field=field;this.key=key;this.container=container;this.object=object;this.target=target;this.kind=kind;
            prefix=kind.equals("HASH_SET")?"RECORD/6":Set.of("ARRAY_LIST","OBJECT_ARRAY").contains(kind)?"RECORD/5":kind.equals("UUID_SELECTOR")?"RECORD/4":kind.equals("DIRECT_FIELD")?"RECORD/3":"RECORD/2";
            objectIdentity=new WeakReference<>(object);containerIdentity=new WeakReference<>(container);
            beforeImage=Set.of("COPY_ON_WRITE_LIST","ARRAY_LIST","HASH_SET").contains(kind)?listImage(container,null):"";
            expectedImage=Set.of("COPY_ON_WRITE_LIST","ARRAY_LIST","HASH_SET").contains(kind)?listImage(container,object):"";
            intent=prefix+"\tEDGE_INTENT\t"+id+"\t"+policy.operation+"\t"+policy.subject.id+"\t"+policy.generation+"\t"+session+"\t"+
                    text(source.locator)+"\t"+text(source.code)+"\t"+text(field.getDeclaringClass().getName()+"#"+field.getName())+"\t"+kind+"\t"+text(key==null&&!kind.equals("ENTITY_MAP")?"":SourceMaps.encodeKey(key))+"\t"+beforeImage+"\t"+expectedImage;
        }
        Object receiver()throws IllegalAccessException {Object holder=source.holder();if(holder==null)throw new IllegalAccessException("HOLDER_RELEASE_UNOBSERVED");return holder instanceof Class<?>?null:holder;}
    }
    RecoveryReferences(ProRuntime runtime) {
        this.runtime=runtime;
        for(String record:runtime.journal.history())if(record.startsWith("RECORD/2\tEDGE_")||record.startsWith("RECORD/3\tEDGE_")||record.startsWith("RECORD/4\tEDGE_")||record.startsWith("RECORD/5\tEDGE_")||record.startsWith("RECORD/6\tEDGE_"))try {
            String[] row=record.split("\t",-1);
            if(row[1].equals("EDGE_INTENT")) {
                if(row.length!=12&&row.length!=14)throw new IllegalArgumentException("REFERENCE_INTENT_ARITY");
                Historical item=new Historical(row);historical.add(item);runtime.recoverySources.expect(item.source,item.code,item.subject,0);
            } else if(row[1].equals("EDGE_IMAGE")) {
                if(row.length!=6||!row[4].matches("[a-f0-9]{64}")||!row[5].matches("[a-f0-9]{64}"))throw new IllegalArgumentException("REFERENCE_IMAGE_RECORD");
                UUID id=UUID.fromString(row[2]),operation=UUID.fromString(row[3]);Historical selected=null;
                for(Historical item:historical)if(item.id.equals(id)){selected=item;break;}
                if(selected==null||!selected.operation.equals(operation)||!selected.prefix.equals(row[0])
                        ||!Set.of("COPY_ON_WRITE_LIST","ARRAY_LIST","HASH_SET").contains(selected.kind))throw new IllegalArgumentException("REFERENCE_IMAGE_OWNER");
                selected.before=row[4];selected.expected=row[5];selected.imageApplied=false;
            } else {
                if(row.length!=5||!Set.of("EDGE_FACT","EDGE_RELEASED","EDGE_RESTART").contains(row[1]))throw new IllegalArgumentException("REFERENCE_FACT");
                UUID.fromString(row[2]);UUID.fromString(row[3]);
                if(row[1].equals("EDGE_FACT"))for(Historical item:historical)if(item.id.toString().equals(row[2])) {
                    if(!item.operation.toString().equals(row[3])||!item.prefix.equals(row[0]))throw new IllegalArgumentException("REFERENCE_FACT_OWNER");
                    item.imageApplied=row[4].equals("EXACT_OWNED_EDGE_REMOVED_AND_ABSENT")||row[4].equals("EDGE_REMOVED_CURRENT_DUPLICATE_OR_REINSERTION_PENDING");
                    break;
                }
            }
        } catch(RuntimeException malformed) { gap="REFERENCE_HISTORY_QUERY_ONLY:"+malformed.getMessage();runtime.journal.refuseWrites(gap); }
    }
    void qualify(ProRuntime.Subject subject,UUID operation) {
        ProRuntime.Subject canonical=runtime.canonicalSubject(subject.id);
        if(canonical!=null&&canonical!=subject) {qualify(canonical,runtime.groupOperation(canonical.id,operation));return;}
        Policy prior=policies.get(subject.id);
        if(prior!=null&&prior.generation==subject.generation)return;
        policies.put(subject.id,new Policy(subject,operation,subject.generation));
        // Resolve actual earlier reads now that the body has a subject; no global class scan.
        for(FieldRead read:fieldReads) {
            Object holder=read.holder.get(),value=read.value();if(holder==null||value==null)continue;
            if(subject(value)!=null&&subject(value).equals(subject.id))runtime.recoverySources.observedHolder(holder,subject.id);
        }
        for(var source:runtime.recoverySources.registered())if(!Collections.disjoint(source.subjects,runtime.groupTargets(subject.id))) {
            Set<String> issues=new TreeSet<>();
            try {scan(source,issues);}catch(ReflectiveOperationException|java.io.IOException|RuntimeException unavailable) {issues.add("REFERENCE_SCAN_UNAVAILABLE");}
            gaps.put(source,Set.copyOf(issues));
        }
    }
    /** Metadata comes from the bootstrap's actual woven read, not a caller-declared source. */
    Object observeField(Object receiver,Object[] packet) {
        if(packet.length!=5||packet[0]!=receiver||!(packet[1] instanceof Class<?> owner)
                ||!(packet[2] instanceof String name)||!(packet[3] instanceof String descriptor)||receiver instanceof Entity)return null;
        if(owner.getClassLoader()==null||owner.getName().startsWith("net.minecraft.")||owner.getName().startsWith("dev.ronova.pro.")&&!owner.getName().startsWith("dev.ronova.pro.validation."))return null;
        Field field=FIELDS.get(owner).get(name+":"+descriptor);if(field==null)return null;
        if(Modifier.isStatic(field.getModifiers())!=(receiver instanceof Class<?>))return null;
        if(receiver instanceof Class<?>)receiver=field.getDeclaringClass();
        Object value=packet[4];
        try {if(!sameFieldValue(field,field.get(receiver instanceof Class<?>?null:receiver),value))return null;}
        catch(IllegalAccessException unavailable){return null;}
        FieldRead read=fieldRead(receiver,field.getDeclaringClass(),name);
        FieldRead previous=read;
        if(read!=null&&!sameFieldValue(field,read.value(),value))read=null;
        if(read==null) {
            synchronized(fieldReads) {
                fieldReads.removeIf(old->old.holder.get()==null||old.value()==null&&old.consumers.isEmpty());
                read=new FieldRead(field,receiver,value);fieldReads.add(read);
                if(previous!=null)read.selectors=previous.selectors;
                synchronized(writerFields) {
                    writerFields.computeIfAbsent(receiver,key->new HashMap<>()).computeIfAbsent(field.getDeclaringClass(),key->new HashMap<>()).put(name,read);
                }
            }
        }
        Object target=ownedTarget(value,runtime,true);UUID subject=target==null?null:subject(target);
        if(subject!=null)runtime.recoverySources.observedHolder(receiver,subject);
        return subject==null?null:new ProRuntime.ProducerOrigin(runtime,subject,false);
    }
    void consumedFields(UUID subject,Object[][] fields) {
        for(FieldRead read:resolveFields(fields))consume(subject,read);
    }
    void creationFields(Entity entity,Object[][] fields) {
        List<FieldRead> reads=resolveFields(fields);if(reads.isEmpty())return;
        synchronized(creationReads) {
            creationReads.put(entity,reads);
        }
    }
    void creationBound(Entity entity,UUID subject) {
        List<FieldRead> reads=creationReads.get(entity);
        if(reads!=null)for(FieldRead read:reads)consume(subject,read);
        for(Selector selector:selectors)if(selector.output.get()==entity&&selector.value.equals(((dev.ronova.pro.mixin.Access.EntityState)entity).pro$uuid())) {
            selector.subject=subject;consume(subject,selector.read);
            Object holder=selector.read.holder.get();if(holder!=null)selector.source=runtime.recoverySources.observedHolder(holder,subject);
        }
    }
    void identityAssigned(Entity entity,Object[] packet) {
        if(packet.length!=7||packet[0]!=entity||!(packet[2] instanceof Class<?> owner)||!(packet[3] instanceof String name)
                ||!(packet[4] instanceof UUID value)||!(packet[5] instanceof Boolean nullable)||!(packet[6] instanceof String site)
                ||!value.equals(((dev.ronova.pro.mixin.Access.EntityState)entity).pro$uuid()))return;
        Object holder=packet[1];
        observeField(holder,new Object[]{holder,owner,name,"Ljava/util/UUID;",value});
        Field field=FIELDS.get(owner).get(name+":Ljava/util/UUID;");if(field==null)return;
        if(holder instanceof Class<?>)holder=field.getDeclaringClass();
        FieldRead read=fieldRead(holder,field.getDeclaringClass(),name);
        if(read==null||read.value()!=value) {
            read=null;for(FieldRead prior:fieldReads)if(prior.field.equals(field)&&prior.holder.get()==holder&&prior.value()==value) {read=prior;break;}
        }
        if(read==null)return;
        UUID related=null;
        for(Selector old:read.selectors)if(old.subject!=null&&old.value.equals(value)) {
            if(old.source==null||!runtime.recoverySources.current(old.source)) {gap="SELECTOR_SOURCE_BINDING_CHANGED";return;}
            if(related!=null&&!related.equals(old.subject)) {gap="SELECTOR_MULTIPLE_SUBJECTS";return;}related=old.subject;
        }
        if(related!=null) {
            UUID existing=runtime.recoveryTasks.subjectOf(entity);
            if(existing!=null&&!existing.equals(related)) {gap="SELECTOR_OUTPUT_ORIGIN_CONFLICT";return;}
            runtime.recoverySources.produced(entity,related,null,"SELECTOR_OUTPUT_BINDING_CAPACITY");
            consume(related,read);return;
        }
        Selector selector=new Selector(read,entity,value,nullable,site);selectors.add(selector);read.selectors.add(selector);
        UUID bound=runtime.recoveryTasks.subjectOf(entity);if(bound!=null)creationBound(entity,bound);
    }
    private List<FieldRead> resolveFields(Object[][] fields) {
        List<FieldRead> reads=new ArrayList<>();
        for(Object[] packet:fields)if(packet.length==5&&packet[1] instanceof Class<?> owner&&packet[2] instanceof String name) {
            observeField(packet[0],packet);
            Field field=FIELDS.get(owner).get(name+":"+packet[3]);if(field==null)continue;
            Object receiver=packet[0] instanceof Class<?>?field.getDeclaringClass():packet[0];
            FieldRead read=fieldRead(receiver,field.getDeclaringClass(),name);
            if(read!=null&&sameFieldValue(field,read.value(),packet[4]))reads.add(read);
        }
        return List.copyOf(reads);
    }
    private void consume(UUID subject,FieldRead read) {
        if(read.consumers.add(subject))epoch.incrementAndGet();
        fieldReads.addIfAbsent(read);
        Object holder=read.holder.get();if(holder==null)return;
        runtime.recoverySources.observedHolder(holder,subject);
    }
    boolean fieldWriteDenied(Object receiver,Class<?> declaring,String name,Object incoming) {
        FieldRead read=fieldRead(receiver,declaring,name);
        if(read!=null) {
            if(incoming instanceof UUID uuid)for(Selector selector:read.selectors)
                if(selector.subject!=null&&selector.value.equals(uuid)&&runtime.terminalSource(selector.subject)
                        &&selector.source!=null&&runtime.recoverySources.current(selector.source))return true;
            for(Selector selector:read.selectors) {
                Entity output=selector.output.get();
                if(output==null||selector.subject==null||selector.source==null||!runtime.recoverySources.current(selector.source)
                        ||!ProRuntime.preventIndexRemoval(output)||selector.value.equals(incoming))continue;
                try {if(read.field.get(receiver instanceof Class<?>?null:receiver)==selector.read.value())return true;}
                catch(IllegalAccessException unavailable) { }
            }
            UUID incomingSubject=incoming==null?null:subject(incoming);
            if(incomingSubject!=null&&runtime.terminalSource(incomingSubject)) {
                if(!(incoming instanceof CompoundTag tag))return true;
                if(RecoverySources.tryRecordGraph())try {
                    if(RecoverySources.recordGraphGap(runtime.recoverySources,tag,incomingSubject).isEmpty())return true;
                }finally {RecoverySources.releaseRecordGraph();}
            }
            Object prior=read.value();
            try {if(read.field.get(receiver instanceof Class<?>?null:receiver)!=prior)return false;}
            catch(IllegalAccessException unavailable) {return false;}
            if(prior instanceof Entity entity&&incoming!=prior&&ProRuntime.preventIndexRemoval(entity))return true;
        }
        return false;
    }
    private FieldRead fieldRead(Object receiver,Class<?> owner,String name) {
        synchronized(writerFields) {
            var receiverFields=writerFields.get(receiver);if(receiverFields==null)return null;
            var fields=receiverFields.get(owner);return fields==null?null:fields.get(name);
        }
    }
    private static boolean sameFieldValue(Field field,Object left,Object right) {
        return field.getType().isPrimitive()?Objects.equals(left,right):left==right;
    }
    private boolean ownerCurrent(Edge edge) throws IllegalAccessException {
        return runtime.recoverySources.current(edge.source)&&(edge.kind.equals("DIRECT_FIELD")?
                edge.source.holder()==edge.container:edge.kind.equals("UUID_SELECTOR")?edge.source.holder()==edge.container:
                edge.container!=null);
    }
    String dependencies(UUID operation) {
        StringBuilder key=new StringBuilder(gap);
        for(Policy policy:policies.values())if(policy.operation.equals(operation))
            for(FieldRead read:fieldReads)if(read.consumers.contains(policy.subject.id))
                key.append("|CONSUMED:").append(read.field.getDeclaringClass().getName()).append('#').append(read.field.getName());
        for(Edge edge:edges.values())if(edge.policy.operation.equals(operation))key.append('|').append(edge.id).append(':')
            .append(edge.attempted).append(':').append(edge.applied).append(':').append(edge.observed).append(':').append(edge.released);
        for(Historical item:historical)if(item.operation.equals(operation))key.append('|').append(item.id).append(':').append(item.current);
        return key.toString();
    }
    long epoch() { return epoch.get(); }
    void groupFieldCleared(Field field,Object holder){
        for(FieldRead read:fieldReads)if(read.field.equals(field)&&read.holder.get()==holder)read.disposed=true;
    }
    static boolean supportedField(Field field) {
        Class<?> type=field.getType();
        return AtomicReference.class.isAssignableFrom(type)||List.class.isAssignableFrom(type)||Set.class.isAssignableFrom(type)
                ||type.isArray()&&!type.getComponentType().isPrimitive()
                ||Entity.class.isAssignableFrom(type)||CompoundTag.class.isAssignableFrom(type)
                ||!type.isPrimitive()&&!type.isArray()&&!scalarType(type);
    }
    void tick(long tick) {
        if(closing)return;
        queryHistorical(tick);
        var sources=new ArrayList<>(runtime.recoverySources.registered());
        for(int i=0;i<Math.min(32,sources.size());i++) {
            var source=sources.get((int)Math.floorMod(cursor++,sources.size()));
            if(source.subjects.stream().noneMatch(id->policy(id)!=null))continue;
            Set<String> issues=new TreeSet<>();
            try { scan(source,issues); }
            catch(ReflectiveOperationException|java.io.IOException|RuntimeException error) { issues.add("REFERENCE_SCAN_UNAVAILABLE:"+error.getClass().getSimpleName()); }
            Set<String> previous=gaps.put(source,Set.copyOf(issues));if(previous!=null&&!previous.equals(issues))epoch.incrementAndGet();
        }
        List<Edge> active=new ArrayList<>(edges.values());
        for(Edge edge:active) {
            edge.ack=runtime.journal.retryRejected(edge.intent,edge.ack);
            if(confirmed(edge.ack)&&edge.fact!=null)
                edge.factAck=edge.factAck==null?runtime.journal.append(edge.fact):runtime.journal.retryRejected(edge.fact,edge.factAck);
            edge.releaseAck=runtime.journal.retryRejected(edge.release,edge.releaseAck);
            if(edge.running)continue;
            if(edge.released) {
                if(edge.release==null) { edge.release=edge.prefix+"\tEDGE_RELEASED\t"+edge.id+"\t"+edge.policy.operation+"\tOWNED_FIELDS_CLEARED_AFTER_FRAME_EXIT";edge.releaseAck=runtime.journal.append(edge.release); }
            }
        }
        int budget=32;
        // Keep every durable obligation; a busy earlier edge must not monopolize the next tick.
        for(int visited=0;visited<active.size()&&budget>0;visited++) {
            Edge edge=active.get((int)Math.floorMod(edgeCursor++,active.size()));
            if(edge.running||edge.released)continue;
            budget--;
            edge.running=true;
            try { worker.execute(()->{try { advance(edge); }catch(ReflectiveOperationException|RuntimeException error) { edge.reason="REFERENCE_DISPOSITION_UNRESOLVED:"+error.getClass().getSimpleName(); }finally { edge.running=false; } }); }
            catch(RejectedExecutionException pending) { edge.running=false;edge.reason="REFERENCE_WORKER_BUDGET_WAIT"; }
        }
    }
    private Policy policy(UUID id) {
        ProRuntime.Subject canonical=runtime.canonicalSubject(id);
        return policies.get(canonical==null?id:canonical.id);
    }
    private UUID subject(Object object) {
        Object target=ownedTarget(object,runtime,true);
        return target instanceof CompoundTag tag?RecoverySources.subject(runtime.recoverySources,tag):
                target instanceof Entity entity?runtime.recoveryTasks.subjectOf(entity):null;
    }
    private boolean scan(RecoverySources.Source source,Set<String> issues)throws ReflectiveOperationException,java.io.IOException {
        Object actualHolder=source.holder();
        if(actualHolder==null){scans.remove(source);if(!source.disposed()){scanned.remove(source);issues.add("HOLDER_RELEASE_UNOBSERVED");}else scanned.put(source,source.mutations.get());return source.disposed();}
        boolean isStatic=actualHolder instanceof Class<?>;
        ReferenceScan scan=scans.get(source);
        if(scan==null||scan.holder.get()!=actualHolder){scan=new ReferenceScan(actualHolder);scans.put(source,scan);scanned.remove(source);}
        if(scan.fieldIndex==0&&scan.field==null){scan.issues.clear();scan.startedMutation=source.mutations.get();}
        boolean complete=false;
        int remaining=64;
        while(remaining>0&&scan.fieldIndex<scan.fields.size()) {
            if(scan.field==null)scan.field=scan.fields.get(scan.fieldIndex);
            Field field=scan.field;remaining--;
            if(!field.trySetAccessible()) { scan.issues.add("REFERENCE_FIELD_INACCESSIBLE:"+field.getName());scan.finishField();continue; }
            Object container=field.get(isStatic?null:actualHolder);
            SourceMaps.observeField(source,field,container);
            if(!Modifier.isFinal(field.getModifiers())&&!SourceMaps.replacementCovered(source,field,container))
                scan.issues.add("REFERENCE_FIELD_MUTABLE_REPLACEMENT_COVERAGE_UNPROVEN:"+field.getName());
            WeakReference<Object> previousContainer=scan.containers.get(field);
            if(previousContainer==null||previousContainer.get()!=container) {
                scan.containers.put(field,new WeakReference<>(container));scan.lists.remove(field);scan.listRevisions.remove(field);scanned.remove(source);
            }
            if(container!=scan.container){
                scan.container=container;scan.map=null;scan.list=null;scan.listIndex=0;
            }
            if(container==null){scan.finishField();continue;}
            if(ownedTarget(container,runtime,true)!=null) {
                capture(source,field,null,actualHolder,container,"DIRECT_FIELD",scan.issues);
                scan.finishField();continue;
            }
            if(nestedHolder(container)){
                nested(source,field,"FIELD",null,container,scan.issues);scan.finishField();continue;
            }
            if(container.getClass()==AtomicReference.class) {
                Object value=((AtomicReference<?>)container).get();
                if(nestedHolder(value)&&ownedTarget(value,runtime,true)==null)nested(source,field,"ATOMIC",null,value,scan.issues);
                else if(unsupportedWrapper(value))scan.issues.add("REFERENCE_WRAPPER_PROFILE_UNSUPPORTED:"+value.getClass().getName());
                else capture(source,field,null,container,value,"ATOMIC",scan.issues);
                scan.finishField();
            }
            else if(container.getClass()==CopyOnWriteArrayList.class) {
                Object[] values=(Object[])LIST_ARRAY.get(container.getClass()).get(container);
                if(values!=scan.list){
                    WeakReference<Object[]> previous=scan.lists.put(field,new WeakReference<>(values));
                    if(previous==null||previous.get()!=values)scanned.remove(source);
                    scan.list=values;scan.listIndex=0;
                }
                int limit=Math.min(32,remaining);
                while(limit-->0&&scan.listIndex<values.length) {
                    Object value=values[scan.listIndex];
                    if(nestedHolder(value)&&ownedTarget(value,runtime,true)==null)nested(source,field,"LIST",scan.listIndex,value,scan.issues);
                    else if(unsupportedWrapper(value))scan.issues.add("REFERENCE_WRAPPER_PROFILE_UNSUPPORTED:"+value.getClass().getName());
                    else capture(source,field,null,container,value,"COPY_ON_WRITE_LIST",scan.issues);
                    scan.listIndex++;remaining--;
                }
                if(field.get(isStatic?null:actualHolder)!=container||LIST_ARRAY.get(container.getClass()).get(container)!=values){scan.container=null;scan.list=null;scan.listIndex=0;scanned.remove(source);break;}
                if(scan.listIndex==values.length)scan.finishField();else break;
            } else if(container instanceof Object[] values) {
                if(values.length==0){scan.finishField();continue;}
                int limit=Math.min(Math.min(32,remaining),values.length-scan.listIndex);
                Object ticket=ArrayAccess.READ.invoke(null,container);
                if(Boolean.FALSE.equals(ticket)){issues.add("REFERENCE_ARRAY_BUSY:"+field.getName());break;}
                try {
                    int end=Math.min(values.length,scan.listIndex+limit);
                    while(scan.listIndex<end) {
                        int index=scan.listIndex++;Object value=values[index];remaining--;
                        if(nestedHolder(value)&&ownedTarget(value,runtime,true)==null)nested(source,field,"ARRAY",index,value,scan.issues);
                        else if(unsupportedWrapper(value))scan.issues.add("REFERENCE_WRAPPER_PROFILE_UNSUPPORTED:"+value.getClass().getName());
                        else capture(source,field,index,container,value,"OBJECT_ARRAY",scan.issues);
                    }
                } finally {ArrayAccess.END_READ.invoke(null,ticket);}
                if(field.get(isStatic?null:actualHolder)!=container){scan.container=null;scanned.remove(source);break;}
                if(scan.listIndex==values.length)scan.finishField();else break;
            } else if(SourceMaps.scopedList(container)) {
                ReferenceScan active=scan;int limit=Math.min(32,remaining);int[] used={0};boolean[] done={false};
                boolean ran=withArrayList(container,false,()->{
                    long revision=arrayListRevision(container);
                    if(!Objects.equals(active.listRevisions.put(field,revision),revision)) {
                        active.listIndex=0;scanned.remove(source);
                    }
                    List<?> list=(List<?>)container;
                    while(used[0]<limit&&active.listIndex<list.size()) {
                        Object value=list.get(active.listIndex++);used[0]++;
                        if(nestedHolder(value)&&ownedTarget(value,runtime,true)==null)nested(source,field,"LIST",active.listIndex-1,value,active.issues);
                        else if(unsupportedWrapper(value))active.issues.add("REFERENCE_WRAPPER_PROFILE_UNSUPPORTED:"+value.getClass().getName());
                        else capture(source,field,null,container,value,"ARRAY_LIST",active.issues);
                    }
                    done[0]=active.listIndex==list.size();
                });
                remaining-=used[0];
                if(!ran){issues.add("REFERENCE_ARRAY_LIST_BUSY_OR_UNOBSERVED:"+field.getName());break;}
                if(field.get(isStatic?null:actualHolder)!=container){scan.container=null;scan.listRevisions.remove(field);scanned.remove(source);break;}
                if(done[0])scan.finishField();else break;
            } else if(SourceMaps.supportedSet(container)){
                Object backend=SourceMaps.setMap(container);
                if(scan.map==null||scan.map.map!=backend)scan.map=new SourceMaps.Cursor(backend);
                ReferenceScan active=scan;int limit=Math.min(32,remaining);
                boolean ran=SourceMaps.visit(backend,scan.map,limit,(key,value,node,position)->{
                    if(nestedHolder(key)&&ownedTarget(key,runtime,true)==null)nested(source,field,"SET",key.getClass().getName(),key,active.issues);
                    else if(unsupportedWrapper(key))active.issues.add("REFERENCE_WRAPPER_PROFILE_UNSUPPORTED:"+key.getClass().getName());
                    else capture(source,field,null,container,key,"HASH_SET",active.issues);
                    return true;
                });remaining-=limit;
                if(!ran){issues.add("REFERENCE_SET_SCOPE_BUSY_OR_UNOBSERVED:"+field.getName());break;}
                if(field.get(isStatic?null:actualHolder)!=container){scan.container=null;scan.map=null;scanned.remove(source);break;}
                if(scan.map.complete)scan.finishField();else break;
            } else if(container instanceof Map<?,?>) {
                if(!SourceMaps.supported(container)) {
                    scan.issues.add("ENTITY_MAP_BACKEND_UNSUPPORTED:"+container.getClass().getName());scan.finishField();continue;
                }
                SourceMaps.observe(container,source,field);
                if(scan.map==null)scan.map=new SourceMaps.Cursor(container);
                ReferenceScan active=scan;int limit=Math.min(32,remaining);
                boolean ran=SourceMaps.visit(container,scan.map,limit,(key,value,node,position)->captureMap(source,active,field,container,key,value));remaining-=limit;
                if(!ran){issues.add("SOURCE_MAP_BUSY_OR_UNOBSERVED:"+field.getName());break;}
                if(field.get(isStatic?null:actualHolder)!=container){scan.container=null;scan.map=null;scanned.remove(source);break;}
                if(scan.map.complete)scan.finishField();else break;
            } else {
                if(supportedField(field)&&unsupportedWrapper(container))scan.issues.add("REFERENCE_CONTAINER_UNSUPPORTED:"+field.getName());
                scan.finishField();
            }
        }
        if(scan.fieldIndex==scan.fields.size()) {
            complete=source.mutations.get()==scan.startedMutation;
            scan.fieldIndex=0;
            if(!complete)scanned.remove(source);
            else scan.completedIssues=Set.copyOf(scan.issues);
        }
        issues.addAll(scan.completedIssues);
        issues.addAll(scan.issues);
        for(Selector selector:selectors)if(selector.subject!=null&&selector.read.holder.get()==source.holder()) {
            Policy policy=policy(selector.subject);if(policy==null||!policy.subject.terminal||policy.generation!=policy.subject.generation)continue;
            if(!selector.nullable) {issues.add("SELECTOR_RESULT_CONTROL_ONLY:"+selector.site);continue;}
            Field field=selector.read.field;
            if(field.get(isStatic?null:source.holder())!=selector.read.value())continue;
            if(Modifier.isFinal(field.getModifiers())||!HANDLES.get(field.getDeclaringClass()).containsKey(field)) {
                issues.add("SELECTOR_SLOT_CAS_UNAVAILABLE:"+field.getName());continue;
            }
            Entity output=selector.output.get();if(output==null) {issues.add("SELECTOR_OUTPUT_RELEASE_UNOBSERVED:"+field.getName());continue;}
            captureSelector(policy,source,selector,output,issues);
        }
        if(complete&&source.mutations.get()==scan.startedMutation)scanned.put(source,scan.startedMutation);
        if(!Objects.equals(scanned.get(source),source.mutations.get()))issues.add("REFERENCE_HOLDER_SCAN_PENDING");
        return complete;
    }
    private boolean captureMap(RecoverySources.Source source,ReferenceScan scan,Field field,Object container,Object key,Object value)throws ReflectiveOperationException {
        Object wrapped=ownedTarget(value,runtime,true);
        if(wrapped instanceof CompoundTag&&Map.class.isAssignableFrom(field.getType()))return true;
        if(!(wrapped instanceof Entity)&&!(wrapped instanceof CompoundTag)) {
            if(nestedHolder(value)&&SourceMaps.supportedKey(key)){nested(source,field,"MAP",key,value,scan.issues);return true;}
            if(value!=null&&!immutableScalar(value)&&!emptyWrapper(value,runtime))scan.issues.add("ENTITY_MAP_WRAPPER_PROFILE_UNSUPPORTED:"+value.getClass().getName());
            return true;
        }
        if(!SourceMaps.supportedKey(key)) {scan.issues.add("ENTITY_MAP_KEY_UNSUPPORTED");return true;}
        capture(source,field,key,container,value,"ENTITY_MAP",scan.issues);return true;
    }
    private boolean scanCurrent(RecoverySources.Source source)throws ReflectiveOperationException {
        if(!Objects.equals(scanned.get(source),source.mutations.get()))return false;
        ReferenceScan scan=scans.get(source);
        Object holder=source.holder();
        if(holder==null)return source.disposed();
        if(scan==null||scan.holder.get()!=holder)return false;
        Object receiver=holder instanceof Class<?>?null:holder;
        for(var entry:scan.containers.entrySet()) {
            Object actual=entry.getKey().get(receiver);
            if(actual!=entry.getValue().get())return false;
            WeakReference<Object[]> list=scan.lists.get(entry.getKey());
            if(list!=null&&(actual==null||actual.getClass()!=CopyOnWriteArrayList.class||LIST_ARRAY.get(actual.getClass()).get(actual)!=list.get()))return false;
            Long revision=scan.listRevisions.get(entry.getKey());
            if(revision!=null) {
                boolean[] same={false};
                if(!withArrayList(actual,false,()->same[0]=revision.longValue()==arrayListRevision(actual))||!same[0])return false;
            }
        }
        return true;
    }
    private void captureSelector(Policy policy,RecoverySources.Source source,Selector selector,Entity output,Set<String> issues)throws ReflectiveOperationException {
        for(Edge edge:edges.values())if(edge.policy==policy&&!edge.released&&edge.target==output&&edge.source==source&&edge.kind.equals("UUID_SELECTOR")
                &&edge.field.equals(selector.read.field)&&edge.objectIdentity.get()==selector.read.value())return;
        Edge edge=new Edge(policy,source,selector.read.field,selector.value,source.holder(),selector.read.value(),output,"UUID_SELECTOR",runtime.session);
        if(!IntentJournal.frameFits(edge.intent)) {issues.add("REFERENCE_DESCRIPTOR_LIMIT:"+selector.read.field.getName());return;}
        edge.ack=runtime.journal.append(edge.intent);edges.put(edge.id,edge);epoch.incrementAndGet();
        if(runtime.server.isSameThread())advance(edge);
    }
    private void capture(RecoverySources.Source source,Field field,Object key,Object container,Object value,String kind,Set<String> issues) {
        Object target=ownedTarget(value,runtime,true);
        UUID subject=subject(value);Policy policy=policy(subject);
        if(policy==null||!source.subjects.contains(subject)||!policy.subject.terminal||policy.subject.generation!=policy.generation)return;
        for(Edge old:edges.values())if(old.policy==policy&&old.target==target&&old.kind.equals(kind)&&old.source==source&&old.field.equals(field)&&old.containerIdentity.get()==container
                &&old.objectIdentity.get()==value&&Objects.equals(old.key,key)&&!old.released)return;
        Edge edge=new Edge(policy,source,field,key,container,value,target,kind,runtime.session);
        if(!IntentJournal.frameFits(edge.intent)) {issues.add("REFERENCE_DESCRIPTOR_LIMIT:"+field.getName());return;}
        edge.ack=runtime.journal.append(edge.intent);edges.put(edge.id,edge);epoch.incrementAndGet();
        if(kind.equals("DIRECT_FIELD")&&runtime.server.isSameThread())try {advance(edge);}
        catch(ReflectiveOperationException|RuntimeException pending) {edge.reason="DIRECT_FIELD_DISPOSITION_PENDING:"+pending.getClass().getSimpleName();}
    }
    static boolean nestedHolder(Object value){
        if(value==null||value instanceof Entity||value instanceof net.minecraft.nbt.Tag||immutableScalar(value)||value instanceof Class<?>
                ||value instanceof Map<?,?>||value instanceof Collection<?>||value.getClass().isArray()||value instanceof java.lang.ref.Reference<?>)return false;
        Class<?> type=value.getClass();return type.getClassLoader()!=null&&!type.isEnum();
    }
    private void nested(RecoverySources.Source source,Field field,String kind,Object key,Object value,Set<String> issues){
        try{if(runtime.recoverySources.nestedSource(source,field,kind,key,value)==null)issues.add("NESTED_SOURCE_BINDING_PENDING:"+field.getName());}
        catch(ReflectiveOperationException unavailable){issues.add("NESTED_SOURCE_BINDING_PENDING:"+field.getName());}
    }
    private static final Field[] OPAQUE_WRAPPER=new Field[0];
    private static final ClassValue<Field[]> WRAPPER_FIELDS=new ClassValue<>() {
        @Override protected Field[] computeValue(Class<?> type) {
            if(type.getClassLoader()==null||type.isArray())return OPAQUE_WRAPPER;
            var fields=new ArrayList<Field>();
            for(Class<?> owner=type;owner!=null&&owner!=Object.class;owner=owner.getSuperclass())for(Field field:owner.getDeclaredFields()) {
                if(Modifier.isStatic(field.getModifiers()))continue;
                if(!field.trySetAccessible()||!Modifier.isFinal(field.getModifiers())&&!scalarType(field.getType()))return OPAQUE_WRAPPER;
                fields.add(field);
            }
            return fields.toArray(Field[]::new);
        }
    };
    /** One immutable target edge; mutable primitive/immutable-scalar bookkeeping carries no extra object owner. */
    static Object wrapperTarget(Object wrapper,ProRuntime runtime,boolean allowTag) {
        Object target=wrapperProfile(wrapper,runtime,allowTag);return target==Boolean.TRUE?null:target;
    }
    /** Follow all immutable delegate layers; an empty wrapper is not an unsupported edge. */
    private static Object wrapperProfile(Object wrapper,ProRuntime runtime,boolean allowTag){
        if(wrapper==null||wrapper instanceof Entity||wrapper.getClass()==CompoundTag.class
                ||wrapper.getClass().getClassLoader()==null||wrapper.getClass().isArray())return null;
        Object target=null;ArrayDeque<Object> pending=new ArrayDeque<>();Set<Object> seen=Collections.newSetFromMap(new IdentityHashMap<>());pending.add(wrapper);
        while(!pending.isEmpty()){
            Object current=pending.removeLast();if(!seen.add(current))continue;
            Field[] fields=WRAPPER_FIELDS.get(current.getClass());if(fields==OPAQUE_WRAPPER)return null;
            for(Field field:fields){
                Object value;try {value=field.get(current);}catch(IllegalAccessException unavailable){return null;}
                if(value==null)continue;
                boolean candidate=value instanceof Entity||allowTag&&value.getClass()==CompoundTag.class;
                if(candidate){
                    UUID owner=value instanceof Entity?runtime.recoveryTasks.subjectOf(value):RecoverySources.subject(runtime.recoverySources,(CompoundTag)value);
                    if(owner==null||target!=null&&target!=value)return null;target=value;
                }else if(!immutableScalar(value))pending.addLast(value);
            }
        }
        return target==null?Boolean.TRUE:target;
    }
    private static boolean scalarType(Class<?> type) {
        return type.isPrimitive()||type==String.class||type==UUID.class||type==Boolean.class||type==Byte.class
                ||type==Short.class||type==Integer.class||type==Long.class||type==Float.class||type==Double.class||type==Character.class;
    }
    static Object ownedTarget(Object value,ProRuntime runtime,boolean allowTag) {
        if(value instanceof Entity||value!=null&&value.getClass()==CompoundTag.class)return value;
        return wrapperTarget(value,runtime,allowTag);
    }
    static boolean emptyWrapper(Object value,ProRuntime runtime){return wrapperProfile(value,runtime,true)==Boolean.TRUE;}
    static boolean immutableScalar(Object value) {
        Class<?> type=value.getClass();
        return type==String.class||type==UUID.class||type==Boolean.class||type==Byte.class||type==Short.class
                ||type==Integer.class||type==Long.class||type==Float.class||type==Double.class||type==Character.class;
    }
    private boolean unsupportedWrapper(Object value) {
        if(value==null||value instanceof Entity||value.getClass()==CompoundTag.class||immutableScalar(value))return false;
        return wrapperProfile(value,runtime,true)==null;
    }
    private void advance(Edge edge)throws ReflectiveOperationException {
        if(edge.kind.equals("HASH_SET")&&SourceMaps.supportedSet(edge.container)){
            if(!SourceMaps.with(SourceMaps.setMap(edge.container),()->advanceLocked(edge)))edge.reason="REFERENCE_SET_SCOPE_BUSY_OR_UNOBSERVED";
        }else if(edge.kind.equals("OBJECT_ARRAY")&&edge.container instanceof Object[] values) {
            int index=(Integer)edge.key;
            if(index<0||index>=values.length){edge.reason="REFERENCE_ARRAY_INDEX_CHANGED";return;}
            Object ticket=ArrayAccess.BEGIN.invoke(null,values,index,1);
            if(Boolean.FALSE.equals(ticket)){edge.reason="REFERENCE_ARRAY_BUSY";return;}
            boolean before=edge.applied;
            try {
                advanceLocked(edge);
            }finally {ArrayAccess.FINISH.invoke(null,ticket,!before&&edge.applied);}
        }else if(edge.kind.equals("ARRAY_LIST")&&edge.container!=null) {
            ReflectiveOperationException[] failure={null};
            boolean ran=withArrayList(edge.container,true,()->{
                try{advanceLocked(edge);}catch(ReflectiveOperationException unavailable){failure[0]=unavailable;}
            });
            if(failure[0]!=null)throw failure[0];
            if(!ran)edge.reason="REFERENCE_ARRAY_LIST_BUSY_OR_UNOBSERVED";
        }else if(SourceMaps.serialized(edge.container)) {
            if(!SourceMaps.with(edge.container,()->advanceLocked(edge)))edge.reason="SOURCE_MAP_BUSY_OR_UNOBSERVED";
        } else advanceLocked(edge);
    }
    private void advanceLocked(Edge edge)throws ReflectiveOperationException {
        edge.ack=runtime.journal.retryRejected(edge.intent,edge.ack);
        if(edge.fact!=null) {
            if(!confirmed(edge.ack)) { edge.reason="DURABLE_INTENT_ACK_"+ackState(edge.ack);return; }
            edge.reason="";
            edge.factAck=edge.factAck==null?runtime.journal.append(edge.fact):runtime.journal.retryRejected(edge.fact,edge.factAck);
            if(confirmed(edge.factAck)) { edge.object=null;edge.target=null;edge.container=null;edge.released=true;epoch.incrementAndGet(); }
            return;
        }
        if(SourceMaps.disposedField(edge.source,edge.field)) {
            if(edge.kind.equals("DIRECT_FIELD")||edge.kind.equals("UUID_SELECTOR")||absent(edge,edge.container,edge.object)){recordFact(edge,true);return;}
            // A cleared holder does not erase the old alias. Continue the captured
            // exact-container obligation instead of waiting forever on detachment.
        }
        if(!ownerCurrent(edge)) {edge.reason="REFERENCE_OWNER_CHANGED";return;}
        // ACK records the durable obligation; exact idempotent memory sources may be unlinked before it arrives.
        // A FACT is held in memory until the INTENT ACK is confirmed, preserving the on-disk record order.
        if(edge.attempted) {
            boolean absent=absent(edge,edge.container,edge.object);
            if(edge.applied||absent)recordFact(edge,absent);
            else edge.reason="EFFECT_QUERY_REQUIRED";
            return;
        }
        if(edge.kind.equals("COPY_ON_WRITE_LIST")) {
            Field lock=CopyOnWriteArrayList.class.getDeclaredField("lock");if(!lock.trySetAccessible()) { edge.reason="LIST_LOCK_UNAVAILABLE";return; }
            synchronized(lock.get(edge.container)) { remove(edge); }
        } else if(edge.kind.equals("ENTITY_MAP"))removeMap(edge);else remove(edge);
        boolean absent=absent(edge,edge.container,edge.object);
        if(edge.applied&&!edge.expectedImage.isEmpty()&&!edge.expectedImage.equals(listImage(edge.container,null))) {
            edge.reason="REFERENCE_PROJECTED_IMAGE_CHANGED_QUERY_REQUIRED";return;
        }
        if(edge.applied||absent)recordFact(edge,absent);
    }
    private void recordFact(Edge edge,boolean absent) {
        edge.observed=true;edge.reason="";
        edge.fact=edge.prefix+"\tEDGE_FACT\t"+edge.id+"\t"+edge.policy.operation+"\t"+
                (absent?(edge.applied?"EXACT_OWNED_EDGE_REMOVED_AND_ABSENT":"EXACT_EDGE_ABSENCE_OBSERVED"):"EDGE_REMOVED_CURRENT_DUPLICATE_OR_REINSERTION_PENDING");
        if(confirmed(edge.ack))edge.factAck=runtime.journal.append(edge.fact);
        else edge.reason="DURABLE_INTENT_ACK_"+ackState(edge.ack);
        epoch.incrementAndGet();
    }
    /** Removes one exact mapped value from the known concurrent backend; plain Map backends remain read-only gaps. */
    @SuppressWarnings("unchecked") private void removeMap(Edge edge)throws ReflectiveOperationException {
        if(edge.applied)return;
        boolean[] locks={false,false,false};
        boolean[] removed={false};
        try {
            if(!SourceMaps.supported(edge.container))return;
            // Enter the CHM bin first. Every higher-level gate below is nonblocking and is
            // acquired only inside the mapping callback, so a bin wait cannot retain them.
            ((Map<Object,Object>)edge.container).computeIfPresent(edge.key,(key,value)->{
                if(value!=edge.object)return value;
                if(ownedTarget(value,runtime,true)!=edge.target) { edge.reason="REFERENCE_WRAPPER_TARGET_CHANGED";return value; }
                if(!RecoverySources.tryRecordGraph())return value;locks[0]=true;
                if(edge.target instanceof CompoundTag tag) {
                    String graphGap=RecoverySources.recordGraphGap(runtime.recoverySources,tag,edge.policy.subject.id);
                    if(!graphGap.isEmpty()) { edge.reason=graphGap;return value; }
                }
                if(!edge.policy.subject.commitGate.tryLock())return value;locks[1]=true;
                if(!edge.source.gate.tryLock())return value;locks[2]=true;
                if(closing||!edge.policy.subject.terminal||edge.policy.subject.generation!=edge.policy.generation
                        ||!runtime.recoverySources.current(edge.source)||edge.source.writers!=0)return value;
                if(ownedTarget(value,runtime,true)!=edge.target) { edge.reason="REFERENCE_WRAPPER_TARGET_CHANGED";return value; }
                edge.attempted=true;removed[0]=true;return null;
            });
            edge.applied=removed[0];
        } finally {
            // Keep graph/subject/source gates until compute returns, covering the actual unlink.
            if(locks[2])edge.source.gate.unlock();
            if(locks[1])edge.policy.subject.commitGate.unlock();
            if(locks[0])RecoverySources.releaseRecordGraph();
        }
    }
    @SuppressWarnings("unchecked") private void remove(Edge edge)throws ReflectiveOperationException {
        if(edge.applied)return; // Query an uncertain or already observed effect; do not replay it.
        boolean graph=edge.target instanceof CompoundTag;
        if(graph&&!RecoverySources.tryRecordGraph())return;
        try {
            if(edge.target instanceof CompoundTag tag) {
                String gap=RecoverySources.recordGraphGap(runtime.recoverySources,tag,edge.policy.subject.id);
                if(!gap.isEmpty()) { edge.reason=gap;return; }
            }
            if(!edge.kind.equals("UUID_SELECTOR")&&ownedTarget(edge.object,runtime,true)!=edge.target) { edge.reason="REFERENCE_WRAPPER_TARGET_CHANGED";return; }
            if(!edge.policy.subject.commitGate.tryLock())return;
            try {
                if(!edge.source.gate.tryLock())return;
                try {
                    if(closing||!edge.policy.subject.terminal||edge.policy.subject.generation!=edge.policy.generation
                            ||!ownerCurrent(edge)||edge.source.writers!=0)return;
                    switch(edge.kind) {
                        case "DIRECT_FIELD", "UUID_SELECTOR" -> {
                            var handle=HANDLES.get(edge.field.getDeclaringClass()).get(edge.field);
                            if(handle==null&&Modifier.isFinal(edge.field.getModifiers())&&edge.kind.equals("DIRECT_FIELD")){
                                edge.applied=RecoverySources.clearReferenceField(edge.field,edge.receiver(),edge.object,()->edge.attempted=true);break;
                            }
                            if(handle==null)return;
                            edge.attempted=true;
                            edge.applied=Modifier.isStatic(edge.field.getModifiers())?handle.compareAndSet(edge.object,null):
                                    handle.compareAndSet(edge.receiver(),edge.object,null);
                        }
                        case "ATOMIC" -> {
                            if(ownedTarget(edge.object,runtime,true)!=edge.target) { edge.reason="REFERENCE_WRAPPER_TARGET_CHANGED";return; }
                            edge.attempted=true;
                            edge.applied=((AtomicReference<Object>)edge.container).compareAndSet(edge.object,null);
                        }
                        case "OBJECT_ARRAY" -> {
                            var elements=java.lang.invoke.MethodHandles.arrayElementVarHandle(edge.container.getClass());
                            edge.attempted=true;
                            edge.applied=elements.compareAndSet(edge.container,(Integer)edge.key,edge.object,null);
                        }
                        case "HASH_SET" -> {
                            if(!prepareListImage(edge,(Collection<?>)edge.container))return;
                            Object backend=SourceMaps.setMap(edge.container);
                            if(edge.removalCursor==null||edge.removalCursor.map!=backend)edge.removalCursor=new SourceMaps.Cursor(backend);
                            try{SourceMaps.visit(backend,edge.removalCursor,32,(key,value,node,position)->{
                                if(key!=edge.object)return true;
                                edge.attempted=true;edge.applied=SourceMaps.removeIdentityVisited(position,edge.object);return false;
                            });}catch(java.io.IOException unavailable){edge.reason="REFERENCE_SET_SCAN_UNAVAILABLE";}
                        }
                        case "COPY_ON_WRITE_LIST", "ARRAY_LIST" -> {
                            var list=(List<Object>)edge.container;
                            if(!prepareListImage(edge,list))return;
                            for(int i=0;i<list.size();i++)if(list.get(i)==edge.object) {
                                if(ownedTarget(edge.object,runtime,true)!=edge.target) { edge.reason="REFERENCE_WRAPPER_TARGET_CHANGED";return; }
                                edge.attempted=true;SourceMaps.removeListIndex(list,i);edge.applied=true;break;
                            }
                        }
                        default -> throw new IllegalStateException("UNKNOWN_REFERENCE_ADAPTER");
                    }
                } finally { edge.source.gate.unlock(); }
            } finally { edge.policy.subject.commitGate.unlock(); }
        } finally { if(graph)RecoverySources.releaseRecordGraph(); }
    }
    private boolean absent(Edge edge,Object container,Object object) {
        return switch(edge.kind) {
            case "DIRECT_FIELD", "UUID_SELECTOR" -> {
                try {yield edge.field.get(edge.receiver())!=object;}
                catch(IllegalAccessException unavailable) {edge.reason="DIRECT_FIELD_READ_UNAVAILABLE";yield false;}
            }
            case "ATOMIC" -> ((AtomicReference<?>)container).get()!=object;
            case "OBJECT_ARRAY" -> ((Object[])container)[(Integer)edge.key]!=object;
            case "COPY_ON_WRITE_LIST" -> Arrays.stream(((CopyOnWriteArrayList<?>)container).toArray()).noneMatch(value->value==object);
            case "ARRAY_LIST" -> {
                boolean found=false;for(Object value:(List<?>)container)if(value==object){found=true;break;}
                yield !found;
            }
            case "HASH_SET" -> {
                try{
                    List<Map.Entry<Object,Object>> rows=SourceMaps.entries(SourceMaps.setMap(container));
                    if(rows==null){edge.reason="REFERENCE_SET_QUERY_PENDING";yield false;}
                    boolean found=false;for(var row:rows)if(row.getKey()==object){found=true;break;}yield !found;
                }catch(ReflectiveOperationException unavailable){edge.reason="REFERENCE_SET_BACKING_UNAVAILABLE";yield false;}
            }
            case "ENTITY_MAP" -> {
                Object[] observed=SourceMaps.peek(container,edge.key);
                if(observed==null)edge.reason="SOURCE_MAP_QUERY_PENDING";
                yield observed!=null&&observed[1]!=object;
            }
            default -> false;
        };
    }
    private void queryHistorical(long tick) {
        for(int i=0;i<Math.min(128,historical.size());i++) {
            Historical item=historical.get((int)Math.floorMod(historyCursor++,historical.size()));
            boolean prior=item.current;item.current=false;item.sampled=tick;
            try {
                var source=runtime.recoverySources.resolve(item.source,item.code);
                if(source==null) { item.reason="REGISTERED_REFERENCE_SOURCE_NOT_LOADED";continue; }
                if(!runtime.journal.processEnded(item.session)) { item.reason="PRIOR_PROCESS_REFERENCES_UNPROVEN";continue; }
                if(!runtime.recoverySources.queryCurrent(source,actual->historicalAbsent(item,actual)))continue;
                item.record=item.prefix+"\tEDGE_RESTART\t"+item.id+"\t"+item.operation+"\tCURRENT_LOCATION_ABSENT_PRIOR_PROCESS_ENDED";
                item.ack=item.ack==null?runtime.journal.append(item.record):runtime.journal.retryRejected(item.record,item.ack);
                item.current=confirmed(item.ack);item.reason=item.current?"":"REFERENCE_RESTART_ACK_PENDING";
            } catch(ReflectiveOperationException|RuntimeException unavailable) { item.reason="REFERENCE_RESTART_QUERY_UNAVAILABLE:"+unavailable.getClass().getSimpleName(); }
            finally { if(prior!=item.current)epoch.incrementAndGet(); }
        }
    }
    private boolean historicalAbsent(Historical item,RecoverySources.Source source)throws ReflectiveOperationException{
                int split=item.field.lastIndexOf('#');if(split<0)throw new IllegalArgumentException("FIELD");
                Object actualHolder=source.holder();boolean absent=false;
                if(actualHolder==null) {
                    if(!source.disposed()) { item.reason="HOLDER_RELEASE_UNOBSERVED";return false; }
                    absent=true;
                } else {
                    Class<?> holder=actualHolder instanceof Class<?> type?type:actualHolder.getClass(),declaring=null;
                    for(Class<?> type=holder;type!=null;type=type.getSuperclass())if(type.getName().equals(item.field.substring(0,split))) { declaring=type;break; }
                    if(declaring==null) { item.reason="REFERENCE_DEFINITION_CHANGED";return false; }
                    Field field=declaring.getDeclaredField(item.field.substring(split+1));
                    boolean direct=item.kind.equals("DIRECT_FIELD")||item.kind.equals("UUID_SELECTOR");
                    if(!field.trySetAccessible()) { item.reason="REFERENCE_FIELD_UNSUPPORTED";return false; }
                    Object container=field.get(actualHolder instanceof Class<?>?null:actualHolder);
                    SourceMaps.observeField(source,field,container);
                    if(!direct&&!SourceMaps.replacementCovered(source,field,container)){item.reason="REFERENCE_FIELD_PUBLICATION_UNAVAILABLE";return false;}
                    if(container==null&&(source.cleared(field)||SourceMaps.replacementCovered(source,field,container)))absent=true;
                    else if(direct)absent=container==null;
                    else if(item.kind.equals("ATOMIC")&&container!=null&&container.getClass()==AtomicReference.class)absent=((AtomicReference<?>)container).get()==null;
                    else if(item.kind.equals("OBJECT_ARRAY")&&container instanceof Object[] values) {
                        if(!item.key.startsWith("java.lang.Integer:"))throw new IllegalArgumentException("ARRAY_INDEX");
                        int index=Integer.parseInt(item.key.substring("java.lang.Integer:".length()));
                        if(index<0)throw new IllegalArgumentException("ARRAY_INDEX");
                        if(index>=values.length)absent=true;
                        else {
                            Object ticket=ArrayAccess.READ.invoke(null,values);
                            if(Boolean.FALSE.equals(ticket)){item.reason="REFERENCE_ARRAY_QUERY_PENDING";return false;}
                            try{absent=values[index]==null;}finally{ArrayAccess.END_READ.invoke(null,ticket);}
                        }
                    }
                    else if(item.kind.equals("COPY_ON_WRITE_LIST")&&container!=null&&container.getClass()==CopyOnWriteArrayList.class)
                        absent=item.expected.isEmpty()?((CopyOnWriteArrayList<?>)container).isEmpty():listProjectionCurrent(item,listImage(container,null));
                    else if(item.kind.equals("ARRAY_LIST")&&container!=null&&SourceMaps.scopedList(container)) {
                        boolean[] result={false};
                        if(!withArrayList(container,false,()->result[0]=item.expected.isEmpty()?((List<?>)container).isEmpty():listProjectionCurrent(item,listImage(container,null)))) {
                            item.reason="REFERENCE_ARRAY_LIST_QUERY_PENDING";return false;
                        }
                        absent=result[0];
                    }
                    else if(item.kind.equals("HASH_SET")&&SourceMaps.supportedSet(container)){
                        boolean[] result={false};Object current=container;
                        if(!SourceMaps.with(SourceMaps.setMap(container),()->result[0]=item.expected.isEmpty()?((Set<?>)current).isEmpty():listProjectionCurrent(item,listImage(current,null)))){
                            item.reason="REFERENCE_SET_QUERY_PENDING";return false;
                        }
                        absent=result[0];
                    }
                    else if(item.kind.equals("ENTITY_MAP")&&container instanceof Map<?,?>) {
                        Object key=SourceMaps.decodeKey(item.key);
                        Object[] observed=SourceMaps.peek(container,key);
                        if(observed==null) { item.reason="SOURCE_MAP_QUERY_PENDING";return false; }
                        absent=!Boolean.TRUE.equals(observed[0]);
                    }
                }
                if(!absent) { item.reason="ORIGINAL_REFERENCE_LOCATION_PRESENT_FRESH_PROVENANCE_REQUIRED";return false; }
                return true;
    }
    List<String> remaining(UUID operation,UUID subject) {
        List<String> out=new ArrayList<>();
        if(!gap.isEmpty())out.add(gap);
        for(FieldRead read:fieldReads)if(read.consumers.contains(subject)) {
            if(read.disposed)continue;
            if(read.holder.get()==null)out.add("CONSUMED_FIELD_HOLDER_RELEASE_UNOBSERVED:"+read.field.getDeclaringClass().getName()+"#"+read.field.getName());
            else if(scalarType(read.field.getType()))
                out.add("SCALAR_PRODUCER_CONSUMPTION_REQUIRES_DISPOSITION:"+read.field.getDeclaringClass().getName()+"#"+read.field.getName());
        }
        long now=Integer.toUnsignedLong(runtime.server.getTickCount());
        for(Historical item:historical)if(item.operation.equals(operation)&&(!item.current||item.sampled<0||now-item.sampled>10))
            out.add("REFERENCE_RESTART:"+item.id+":"+item.reason);
        for(var source:runtime.recoverySources.registered())if(!Collections.disjoint(source.subjects,runtime.groupTargets(subject))) {
            try {if(!scanCurrent(source))out.add("REFERENCE_HOLDER_SCAN_PENDING");}
            catch(ReflectiveOperationException|RuntimeException unavailable){out.add("REFERENCE_HOLDER_SCAN_UNAVAILABLE");}
            out.addAll(gaps.getOrDefault(source,Set.of()));
        }
        for(Edge edge:edges.values())if(edge.policy.operation.equals(operation)) {
            if(!confirmed(edge.ack))out.add("REFERENCE_EDGE_DURABLE_INTENT_ACK:"+edge.id+":"+ackState(edge.ack));
            if(!edge.released||!confirmed(edge.releaseAck)||!confirmed(edge.factAck))out.add("REFERENCE_EDGE:"+edge.id+":"+edge.reason);
            else try {
                if(edge.source.disposed()&&edge.source.holder()==null)continue;
                Object container=edge.containerIdentity.get(),object=edge.objectIdentity.get();
                // The acknowledged FACT already established absence in this original
                // container. New field contents have their own scan and obligations.
                if(container==null)continue;
                if(!runtime.recoverySources.current(edge.source))out.add("REFERENCE_OWNER_CHANGED:"+edge.id);
                else if(object!=null&&!queryAbsent(edge,container,object))out.add("REFERENCE_REINSERTED_OR_QUERY_PENDING:"+edge.id);
            } catch(ReflectiveOperationException unavailable) { out.add("REFERENCE_QUERY_UNAVAILABLE:"+edge.id); }
        }
        return List.copyOf(out);
    }
    private boolean queryAbsent(Edge edge,Object container,Object object)throws ReflectiveOperationException {
        if(edge.kind.equals("HASH_SET")){
            boolean[] absent={false};return SourceMaps.with(SourceMaps.setMap(container),()->absent[0]=absent(edge,container,object))&&absent[0];
        }
        if(edge.kind.equals("ARRAY_LIST")) {
            boolean[] absent={false};
            return withArrayList(container,false,()->absent[0]=absent(edge,container,object))&&absent[0];
        }
        if(edge.kind.equals("OBJECT_ARRAY")) {
            Object ticket=ArrayAccess.READ.invoke(null,container);
            if(Boolean.FALSE.equals(ticket))return false;
            try{return absent(edge,container,object);}finally{ArrayAccess.END_READ.invoke(null,ticket);}
        }
        return absent(edge,container,object);
    }
    private static boolean confirmed(CompletableFuture<Void> ack) { return ack!=null&&ack.isDone()&&!ack.isCompletedExceptionally()&&!ack.isCancelled(); }
    private static String ackState(CompletableFuture<Void> ack) {
        if(ack==null||!ack.isDone())return "PENDING";
        return ack.isCompletedExceptionally()||ack.isCancelled()?"UNCONFIRMED":"CONFIRMED";
    }
    private static String text(String value) { return JournalLineage.encodeText(value); }
    private static String untext(String value) { return JournalLineage.decodeText(value); }
    /** Later durable removals from this same registered list may extend an earlier preserved image. */
    private boolean listProjectionCurrent(Historical item,String actual) {
        if(actual.isEmpty()||item.expected.isEmpty())return false;
        if(item.expected.equals(actual))return true;
        ArrayDeque<String> pending=new ArrayDeque<>();Set<String> seen=new HashSet<>();
        pending.add(item.expected);
        while(!pending.isEmpty()) {
            String step=pending.removeFirst();if(!seen.add(step))continue;
            for(Historical next:historical) {
                if(!next.imageApplied||!next.kind.equals(item.kind)
                        ||!next.source.equals(item.source)||!next.code.equals(item.code)||!next.field.equals(item.field)
                        ||!next.before.equals(step)||next.expected.isEmpty())continue;
                if(next.expected.equals(actual))return true;
                pending.addLast(next.expected);
            }
        }
        return false;
    }
    /** Full ordered NBT image of a real list; skip exactly one captured identity for its intended projection. */
    private static String listImage(Object container,Object removed) {
        boolean set=SourceMaps.supportedSet(container);
        if(container==null||!set&&container.getClass()!=CopyOnWriteArrayList.class&&!SourceMaps.scopedList(container))return "";
        Object[] values=((Collection<?>)container).toArray();
        StringBuilder canonical=new StringBuilder(set?"NBT-SET/1\n":"NBT-LIST/1\n");boolean skipped=false;
        List<String> hashes=set?new ArrayList<>():null;
        for(Object value:values) {
            if(removed!=null&&value==removed&&!skipped) { skipped=true;continue; }
            if(!(value instanceof CompoundTag tag)||value.getClass()!=CompoundTag.class)return "";
            try {String hash=RecoveryImage.semanticHash(tag);if(set)hashes.add(hash);else canonical.append(hash).append('\n');}
            catch(java.io.IOException unsupported) { return ""; }
        }
        if(removed!=null&&!skipped)return "";
        if(set){Collections.sort(hashes);for(String hash:hashes)canonical.append(hash).append('\n');}
        return RecoveryImage.hash(canonical.toString().getBytes(StandardCharsets.UTF_8));
    }
    /** Rebase only the exact identity removal; persist the new preserved image before its write. */
    private boolean prepareListImage(Edge edge,Collection<?> list) {
        if(edge.imageRecord!=null) {
            edge.imageAck=runtime.journal.retryRejected(edge.imageRecord,edge.imageAck);
            if(!confirmed(edge.imageAck)){edge.reason="REFERENCE_LIST_IMAGE_ACK_PENDING";return false;}
        }
        if(edge.beforeImage.isEmpty())return true;
        boolean present=false;for(Object value:list)if(value==edge.object){present=true;break;}
        if(!present)return true;
        String actual=listImage(list,null);
        if(edge.beforeImage.equals(actual))return true;
        if(actual.isEmpty()){edge.reason="REFERENCE_LIST_IMAGE_UNAVAILABLE";return false;}
        String projected=listImage(list,edge.object);
        if(projected.isEmpty()){edge.reason="REFERENCE_LIST_PROJECTION_UNAVAILABLE";return false;}
        if(!confirmed(edge.ack)){edge.reason="DURABLE_INTENT_ACK_"+ackState(edge.ack);return false;}
        edge.beforeImage=actual;edge.expectedImage=projected;
        edge.imageRecord=edge.prefix+"\tEDGE_IMAGE\t"+edge.id+"\t"+edge.policy.operation+"\t"+actual+"\t"+projected;
        edge.imageAck=runtime.journal.append(edge.imageRecord);edge.reason="REFERENCE_LIST_IMAGE_ACK_PENDING";
        epoch.incrementAndGet();return false;
    }
    private static final class ArrayListAccess {
        static final Method READ=method("withListRead",Object.class,Runnable.class),WRITE=method("withList",Object.class,Runnable.class),REVISION=method("listRevision",Object.class);
        private static Method method(String name,Class<?>... parameters){
            try{return Class.forName("dev.ronova.pro.bootstrap.SourceMapBridge",false,null).getMethod(name,parameters);}
            catch(ReflectiveOperationException unavailable){throw new ExceptionInInitializerError(unavailable);}
        }
    }
    private static final class ArrayAccess {
        static final Method BEGIN=method("beginRecoveryArrayMutation",Object.class,int.class,int.class),
                READ=method("beginRecoveryArrayRead",Object.class),END_READ=method("endFieldMutation",Object.class),
                FINISH=method("finishRecoveryArrayMutation",Object.class,boolean.class);
        private static Method method(String name,Class<?>... parameters){
            try{return Class.forName("dev.ronova.pro.bootstrap.TaskBridge",false,null).getMethod(name,parameters);}
            catch(ReflectiveOperationException unavailable){throw new ExceptionInInitializerError(unavailable);}
        }
    }
    static boolean withArrayList(Object list,boolean write,Runnable action)throws ReflectiveOperationException {
        if(!SourceMaps.scopedList(list))return false;
        if(SourceMaps.fastList(list))return SourceMaps.with(list,action::run);
        return Boolean.TRUE.equals((write?ArrayListAccess.WRITE:ArrayListAccess.READ).invoke(null,list,action));
    }
    static long arrayListRevision(Object list) {
        try {long revision=SourceMaps.fastList(list)?SourceMaps.revision(list):(Long)ArrayListAccess.REVISION.invoke(null,list);if(revision<0)throw new IllegalStateException("REFERENCE_ARRAY_LIST_UNOBSERVED");return revision;}
        catch(ReflectiveOperationException unavailable){throw new IllegalStateException("REFERENCE_ARRAY_LIST_REVISION_UNAVAILABLE",unavailable);}
    }
    @Override public void close()throws java.io.IOException {
        closing=true;worker.shutdown();if(!worker.isTerminated())throw new java.io.IOException("REFERENCE_WORKER_EXIT_PENDING");
        for(Edge edge:edges.values())if(edge.running)throw new java.io.IOException("REFERENCE_WORKER_FRAME_PENDING");
        boolean pending=false;
        for(Edge edge:edges.values()) {
            edge.ack=runtime.journal.retryRejected(edge.intent,edge.ack);
            if(!confirmed(edge.ack)) { pending=true;continue; }
            if(edge.fact==null) {
                edge.fact=edge.prefix+"\tEDGE_FACT\t"+edge.id+"\t"+edge.policy.operation+"\t"+
                        (edge.applied?"REMOVAL_EXECUTED_CURRENT_QUERY_RETAINED":"NOT_EXECUTED_AT_STOP");
                edge.factAck=runtime.journal.append(edge.fact);
            } else edge.factAck=edge.factAck==null?runtime.journal.append(edge.fact):runtime.journal.retryRejected(edge.fact,edge.factAck);
            if(!confirmed(edge.factAck)) { pending=true;continue; }
            edge.object=null;edge.target=null;edge.container=null;edge.released=true;
            if(edge.release==null) { edge.release=edge.prefix+"\tEDGE_RELEASED\t"+edge.id+"\t"+edge.policy.operation+"\tOWNED_OBSERVER_FIELDS_CLEARED_AT_STOP";edge.releaseAck=runtime.journal.append(edge.release); }
            else edge.releaseAck=runtime.journal.retryRejected(edge.release,edge.releaseAck);
            if(!confirmed(edge.releaseAck))pending=true;
        }
        if(pending)throw new java.io.IOException("REFERENCE_RETIREMENT_ACK_PENDING");
        edges.clear();policies.clear();gaps.clear();scanned.clear();scans.clear();fieldReads.clear();writerFields.clear();creationReads.clear();selectors.clear();
    }
}
