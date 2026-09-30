package dev.ronova.pro;

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
        final String source,code,field,kind,key,expected,prefix;
        boolean current;
        long sampled=-1;
        String reason="REFERENCE_RESTART_PENDING",record;
        CompletableFuture<Void> ack;
        Historical(String[] row) {
            id=UUID.fromString(row[2]);operation=UUID.fromString(row[3]);subject=UUID.fromString(row[4]);session=UUID.fromString(row[6]);
            if(Long.parseLong(row[5])<1)throw new IllegalArgumentException("REFERENCE_QUALIFICATION");
            source=untext(row[7]);code=untext(row[8]);field=untext(row[9]);kind=row[10];key=untext(row[11]);
            prefix=row[0];
            expected=row.length==14?row[13]:"";
            if(row.length==14&&(!row[12].isEmpty()&&!row[12].matches("[a-f0-9]{64}")||!expected.isEmpty()&&!expected.matches("[a-f0-9]{64}")))throw new IllegalArgumentException("REFERENCE_IMAGE_HASH");
            if(!(Set.of("ATOMIC","COPY_ON_WRITE_LIST","ENTITY_MAP").contains(kind)
                    ||prefix.equals("RECORD/3")&&kind.equals("DIRECT_FIELD")
                    ||prefix.equals("RECORD/4")&&kind.equals("UUID_SELECTOR"))||!code.matches("[a-f0-9]{64}"))throw new IllegalArgumentException("REFERENCE_ADAPTER");
            for(int i:new int[]{7,8,9,11})if(!text(untext(row[i])).equals(row[i]))throw new IllegalArgumentException("REFERENCE_ENCODING");
        }
    }
    private final Map<RecoverySources.Source,Set<String>> gaps=new IdentityHashMap<>();
    private final Map<RecoverySources.Source,Long> scanned=new IdentityHashMap<>();
    private final AtomicLong epoch=new AtomicLong();
    private long cursor;
    private boolean closing;
    private String gap="";
    private final ThreadPoolExecutor worker=new ThreadPoolExecutor(1,1,0,TimeUnit.MILLISECONDS,new ArrayBlockingQueue<>(32),r->{
        Thread thread=new Thread(r,"ronova-pro-reference-edges");thread.setDaemon(true);return thread;
    },new ThreadPoolExecutor.AbortPolicy());
    private static final class Edge {
        final UUID id=UUID.randomUUID();final Policy policy;final RecoverySources.Source source;final Field field;
        final Object key;Object target;final String kind,intent,beforeImage,expectedImage,prefix;
        final WeakReference<Object> objectIdentity,containerIdentity;
        Object object,container;
        volatile boolean running,attempted,applied,observed,released;
        volatile String reason="",fact,release;
        CompletableFuture<Void> ack,factAck,releaseAck;
        Edge(Policy policy,RecoverySources.Source source,Field field,Object key,Object container,Object object,Object target,String kind,UUID session) {
            this.policy=policy;this.source=source;this.field=field;this.key=key;this.container=container;this.object=object;this.target=target;this.kind=kind;
            prefix=kind.equals("UUID_SELECTOR")?"RECORD/4":kind.equals("DIRECT_FIELD")?"RECORD/3":"RECORD/2";
            objectIdentity=new WeakReference<>(object);containerIdentity=new WeakReference<>(container);
            beforeImage=kind.equals("COPY_ON_WRITE_LIST")?listImage(container,null):"";
            expectedImage=kind.equals("COPY_ON_WRITE_LIST")?listImage(container,object):"";
            intent=prefix+"\tEDGE_INTENT\t"+id+"\t"+policy.operation+"\t"+policy.subject.id+"\t"+policy.generation+"\t"+session+"\t"+
                    text(source.locator)+"\t"+text(source.code)+"\t"+text(field.getDeclaringClass().getName()+"#"+field.getName())+"\t"+kind+"\t"+text(key==null?"":key.getClass().getName()+":"+key)+"\t"+beforeImage+"\t"+expectedImage;
        }
        Object receiver() { return source.holder instanceof Class<?>?null:source.holder; }
    }
    RecoveryReferences(ProRuntime runtime) {
        this.runtime=runtime;
        for(String record:runtime.journal.history())if(record.startsWith("RECORD/2\tEDGE_")||record.startsWith("RECORD/3\tEDGE_")||record.startsWith("RECORD/4\tEDGE_"))try {
            String[] row=record.split("\t",-1);
            if(row[1].equals("EDGE_INTENT")) {
                if(row.length!=12&&row.length!=14)throw new IllegalArgumentException("REFERENCE_INTENT_ARITY");
                Historical item=new Historical(row);historical.add(item);runtime.recoverySources.expect(item.source,item.code,item.subject,0);
            } else {
                if(row.length!=5||!Set.of("EDGE_FACT","EDGE_RELEASED","EDGE_RESTART").contains(row[1]))throw new IllegalArgumentException("REFERENCE_FACT");
                UUID.fromString(row[2]);UUID.fromString(row[3]);
            }
        } catch(RuntimeException malformed) { gap="REFERENCE_HISTORY_QUERY_ONLY:"+malformed.getMessage();runtime.journal.refuseWrites(gap); }
    }
    void qualify(ProRuntime.Subject subject,UUID operation) {
        Policy prior=policies.get(subject.id);
        if(prior!=null&&prior.generation==subject.generation)return;
        policies.put(subject.id,new Policy(subject,operation,subject.generation));
        // Resolve actual earlier reads now that the body has a subject; no global class scan.
        for(FieldRead read:fieldReads) {
            Object holder=read.holder.get(),value=read.value();if(holder==null||value==null)continue;
            if(subject(value)!=null&&subject(value).equals(subject.id))runtime.recoverySources.observedHolder(holder,subject.id);
        }
        for(var source:runtime.recoverySources.registered())if(source.subjects.contains(subject.id)) {
            Set<String> issues=new TreeSet<>();
            try {scan(source,issues);}catch(ReflectiveOperationException|RuntimeException unavailable) {issues.add("REFERENCE_SCAN_UNAVAILABLE");}
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
                if(fieldReads.size()>=4096)return null;
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
            if(creationReads.size()>=4096&&!creationReads.containsKey(entity)) {gap="CREATION_FIELD_CAPTURE_CAPACITY";return;}
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
        if(selectors.size()>=4096) {gap="SELECTOR_CAPTURE_CAPACITY";return;}
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
                edge.source.holder==edge.container:edge.kind.equals("UUID_SELECTOR")?edge.source.holder==edge.container:
                edge.field.get(edge.receiver())==edge.container);
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
    static boolean supportedField(Field field) {
        Class<?> type=field.getType();
        return AtomicReference.class.isAssignableFrom(type)||CopyOnWriteArrayList.class.isAssignableFrom(type)
                ||Entity.class.isAssignableFrom(type)||CompoundTag.class.isAssignableFrom(type)
                ||!type.isPrimitive()&&!type.isArray()&&!scalarType(type)&&type.getClassLoader()!=null;
    }
    void tick(long tick) {
        if(closing)return;
        queryHistorical(tick);
        var sources=new ArrayList<>(runtime.recoverySources.registered());
        for(int i=0;i<Math.min(32,sources.size());i++) {
            var source=sources.get((int)Math.floorMod(cursor++,sources.size()));
            if(source.subjects.stream().noneMatch(policies::containsKey))continue;
            Set<String> issues=new TreeSet<>();
            try { scan(source,issues);scanned.put(source,tick); }
            catch(ReflectiveOperationException|RuntimeException error) { issues.add("REFERENCE_SCAN_UNAVAILABLE:"+error.getClass().getSimpleName()); }
            Set<String> previous=gaps.put(source,Set.copyOf(issues));if(previous!=null&&!previous.equals(issues))epoch.incrementAndGet();
        }
        int budget=32;
        for(Edge edge:edges.values()) {
            edge.ack=runtime.journal.retryRejected(edge.intent,edge.ack);
            if(confirmed(edge.ack)&&edge.fact!=null)
                edge.factAck=edge.factAck==null?runtime.journal.append(edge.fact):runtime.journal.retryRejected(edge.fact,edge.factAck);
            edge.releaseAck=runtime.journal.retryRejected(edge.release,edge.releaseAck);
            if(edge.running)continue;
            if(edge.released) {
                if(edge.release==null) { edge.release=edge.prefix+"\tEDGE_RELEASED\t"+edge.id+"\t"+edge.policy.operation+"\tOWNED_FIELDS_CLEARED_AFTER_FRAME_EXIT";edge.releaseAck=runtime.journal.append(edge.release); }
                continue;
            }
            if(budget--<=0)continue;
            edge.running=true;
            try { worker.execute(()->{try { advance(edge); }catch(ReflectiveOperationException|RuntimeException error) { edge.reason="REFERENCE_DISPOSITION_UNRESOLVED:"+error.getClass().getSimpleName(); }finally { edge.running=false; } }); }
            catch(RejectedExecutionException pending) { edge.running=false;edge.reason="REFERENCE_WORKER_BUDGET_WAIT"; }
        }
    }
    private UUID subject(Object object) {
        Object target=ownedTarget(object,runtime,true);
        return target instanceof CompoundTag tag?RecoverySources.subject(runtime.recoverySources,tag):
                target instanceof Entity entity?runtime.recoveryTasks.subjectOf(entity):null;
    }
    private void scan(RecoverySources.Source source,Set<String> issues)throws ReflectiveOperationException {
        boolean isStatic=source.holder instanceof Class<?>;
        Class<?> holder=isStatic?(Class<?>)source.holder:source.holder.getClass();int fields=0,entries=0;
        for(Class<?> type=holder;type!=null&&type!=Object.class;type=isStatic?null:type.getSuperclass())for(Field field:type.getDeclaredFields()) {
            if(Modifier.isStatic(field.getModifiers())!=isStatic)continue;
            if(field.getType().isPrimitive()||scalarType(field.getType()))continue;
            if(++fields>64) { issues.add("REFERENCE_FIELD_BUDGET");return; }
            if(!field.trySetAccessible()) { issues.add("REFERENCE_FIELD_INACCESSIBLE:"+field.getName());continue; }
            if(!Modifier.isFinal(field.getModifiers()))
                issues.add("REFERENCE_FIELD_MUTABLE_REPLACEMENT_COVERAGE_UNPROVEN:"+field.getName());
            Object container=field.get(isStatic?null:source.holder);
            if(container==null)continue;
            if(ownedTarget(container,runtime,true)!=null) {
                if(Modifier.isFinal(field.getModifiers())||!HANDLES.get(field.getDeclaringClass()).containsKey(field))
                    issues.add("DIRECT_FIELD_CAS_UNAVAILABLE:"+field.getName());
                else capture(source,field,null,source.holder,container,"DIRECT_FIELD");
                continue;
            }
            if(container!=null&&container.getClass()==AtomicReference.class) {
                Object value=((AtomicReference<?>)container).get();
                if(unsupportedWrapper(value))issues.add("REFERENCE_WRAPPER_PROFILE_UNSUPPORTED:"+value.getClass().getName());
                else capture(source,field,null,container,value,"ATOMIC");
            }
            else if(container!=null&&container.getClass()==CopyOnWriteArrayList.class) {
                Object[] values=((CopyOnWriteArrayList<?>)container).toArray();
                if(values.length>4096) { issues.add("REFERENCE_LIST_SCAN_BUDGET");continue; }
                for(Object value:values) {
                    if(unsupportedWrapper(value))issues.add("REFERENCE_WRAPPER_PROFILE_UNSUPPORTED:"+value.getClass().getName());
                    else capture(source,field,null,container,value,"COPY_ON_WRITE_LIST");
                }
            } else if(container instanceof Map<?,?>) {
                if(!SourceMaps.supported(container)) {
                    issues.add("ENTITY_MAP_BACKEND_UNSUPPORTED:"+container.getClass().getName());continue;
                }
                var snapshot=SourceMaps.entries(container);
                if(snapshot==null) { issues.add(SourceMaps.unavailable(container)+":"+field.getName());continue; }
                SourceMaps.observe(container,source,field);
                for(var entry:snapshot) {
                    if(++entries>8192) { issues.add("REFERENCE_MAP_SCAN_BUDGET");return; }
                    Object value=entry.getValue();Object wrapped=ownedTarget(value,runtime,true);
                    if(wrapped instanceof CompoundTag)continue;
                    if(!(wrapped instanceof Entity)) {
                        if(value!=null&&!immutableScalar(value))issues.add("ENTITY_MAP_WRAPPER_PROFILE_UNSUPPORTED:"+value.getClass().getName());
                        continue;
                    }
                    Object keyValue=entry.getKey();
                    if(keyValue==null||keyValue.getClass()!=String.class&&keyValue.getClass()!=UUID.class) {
                        issues.add("ENTITY_MAP_KEY_UNSUPPORTED");continue;
                    }
                    capture(source,field,keyValue,container,value,"ENTITY_MAP");
                }
            } else if(supportedField(field))issues.add("REFERENCE_CONTAINER_UNSUPPORTED:"+field.getName());
        }
        for(Selector selector:selectors)if(selector.subject!=null&&selector.read.holder.get()==source.holder) {
            Policy policy=policies.get(selector.subject);if(policy==null||!policy.subject.terminal||policy.generation!=policy.subject.generation)continue;
            if(!selector.nullable) {issues.add("SELECTOR_RESULT_CONTROL_ONLY:"+selector.site);continue;}
            Field field=selector.read.field;
            if(field.get(isStatic?null:source.holder)!=selector.read.value())continue;
            if(Modifier.isFinal(field.getModifiers())||!HANDLES.get(field.getDeclaringClass()).containsKey(field)) {
                issues.add("SELECTOR_SLOT_CAS_UNAVAILABLE:"+field.getName());continue;
            }
            Entity output=selector.output.get();if(output==null) {issues.add("SELECTOR_OUTPUT_RELEASE_UNOBSERVED:"+field.getName());continue;}
            captureSelector(policy,source,selector,output);
        }
    }
    private void captureSelector(Policy policy,RecoverySources.Source source,Selector selector,Entity output)throws ReflectiveOperationException {
        for(Edge edge:edges.values())if(edge.policy.subject==policy.subject&&edge.source==source&&edge.kind.equals("UUID_SELECTOR")
                &&edge.field.equals(selector.read.field)&&edge.objectIdentity.get()==selector.read.value())return;
        if(edges.size()>=65536) {gap="REFERENCE_OBLIGATION_CAPACITY";return;}
        Edge edge=new Edge(policy,source,selector.read.field,selector.value,source.holder,selector.read.value(),output,"UUID_SELECTOR",runtime.session);
        edge.ack=runtime.journal.append(edge.intent);edges.put(edge.id,edge);epoch.incrementAndGet();
        if(runtime.server.isSameThread())advance(edge);
    }
    private void capture(RecoverySources.Source source,Field field,Object key,Object container,Object value,String kind) {
        Object target=ownedTarget(value,runtime,true);
        UUID subject=subject(value);Policy policy=policies.get(subject);
        if(policy==null||!source.subjects.contains(subject)||!policy.subject.terminal||policy.subject.generation!=policy.generation)return;
        for(Edge old:edges.values())if(old.source==source&&old.field.equals(field)&&old.containerIdentity.get()==container
                &&old.objectIdentity.get()==value&&Objects.equals(old.key,key)&&!old.released)return;
        if(edges.size()>=65536) { gap="REFERENCE_OBLIGATION_CAPACITY";return; }
        Edge edge=new Edge(policy,source,field,key,container,value,target,kind,runtime.session);edge.ack=runtime.journal.append(edge.intent);edges.put(edge.id,edge);epoch.incrementAndGet();
        if(kind.equals("DIRECT_FIELD")&&runtime.server.isSameThread())try {advance(edge);}
        catch(ReflectiveOperationException|RuntimeException pending) {edge.reason="DIRECT_FIELD_DISPOSITION_PENDING:"+pending.getClass().getSimpleName();}
    }
    private static final ClassValue<Field[]> WRAPPER_FIELDS=new ClassValue<>() {
        @Override protected Field[] computeValue(Class<?> type) {
            if(type.getClassLoader()==null||type.isArray())return new Field[0];
            var fields=new ArrayList<Field>();
            for(Class<?> owner=type;owner!=null&&owner!=Object.class;owner=owner.getSuperclass())for(Field field:owner.getDeclaredFields()) {
                if(Modifier.isStatic(field.getModifiers()))continue;
                if(fields.size()==32||!field.trySetAccessible()||!Modifier.isFinal(field.getModifiers())&&!scalarType(field.getType()))return new Field[0];
                fields.add(field);
            }
            return fields.toArray(Field[]::new);
        }
    };
    /** One immutable target edge; mutable primitive/immutable-scalar bookkeeping carries no extra object owner. */
    static Object wrapperTarget(Object wrapper,ProRuntime runtime,boolean allowTag) {
        if(wrapper==null||wrapper instanceof Entity||wrapper.getClass()==CompoundTag.class
                ||wrapper.getClass().getClassLoader()==null||wrapper.getClass().isArray())return null;
        Object target=null;
        for(Field field:WRAPPER_FIELDS.get(wrapper.getClass())) {
            Object value;try {value=field.get(wrapper);}catch(IllegalAccessException unavailable){return null;}
            if(value==null)continue;
            boolean candidate=value instanceof Entity||allowTag&&value.getClass()==CompoundTag.class;
            if(candidate) {
                UUID owner=value instanceof Entity?runtime.recoveryTasks.subjectOf(value):RecoverySources.subject(runtime.recoverySources,(CompoundTag)value);
                if(owner==null||target!=null&&target!=value)return null;target=value;
            } else if(!immutableScalar(value))return null;
        }
        return target;
    }
    private static boolean scalarType(Class<?> type) {
        return type.isPrimitive()||type==String.class||type==UUID.class||type==Boolean.class||type==Byte.class
                ||type==Short.class||type==Integer.class||type==Long.class||type==Float.class||type==Double.class||type==Character.class;
    }
    static Object ownedTarget(Object value,ProRuntime runtime,boolean allowTag) {
        if(value instanceof Entity||value!=null&&value.getClass()==CompoundTag.class)return value;
        return wrapperTarget(value,runtime,allowTag);
    }
    static boolean immutableScalar(Object value) {
        Class<?> type=value.getClass();
        return type==String.class||type==UUID.class||type==Boolean.class||type==Byte.class||type==Short.class
                ||type==Integer.class||type==Long.class||type==Float.class||type==Double.class||type==Character.class;
    }
    private boolean unsupportedWrapper(Object value) {
        if(value==null||value instanceof Entity||value.getClass()==CompoundTag.class||immutableScalar(value))return false;
        return wrapperTarget(value,runtime,true)==null;
    }
    private void advance(Edge edge)throws ReflectiveOperationException {
        if(edge.container!=null&&edge.container.getClass()==HashMap.class) {
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
        if(!ownerCurrent(edge)) { edge.reason="REFERENCE_OWNER_CHANGED";return; }
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
                try {
                    if(closing||!edge.policy.subject.terminal||edge.policy.subject.generation!=edge.policy.generation
                            ||!runtime.recoverySources.current(edge.source)||edge.source.writers!=0
                            ||edge.field.get(edge.receiver())!=edge.container)return value;
                    if(ownedTarget(value,runtime,true)!=edge.target) { edge.reason="REFERENCE_WRAPPER_TARGET_CHANGED";return value; }
                    edge.attempted=true;removed[0]=true;return null;
                } catch(IllegalAccessException failure) { throw new IllegalStateException(failure); }
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
                            var handle=HANDLES.get(edge.field.getDeclaringClass()).get(edge.field);if(handle==null)return;
                            edge.attempted=true;
                            edge.applied=Modifier.isStatic(edge.field.getModifiers())?handle.compareAndSet(edge.object,null):
                                    handle.compareAndSet(edge.receiver(),edge.object,null);
                        }
                        case "ATOMIC" -> {
                            if(ownedTarget(edge.object,runtime,true)!=edge.target) { edge.reason="REFERENCE_WRAPPER_TARGET_CHANGED";return; }
                            edge.attempted=true;
                            edge.applied=((AtomicReference<Object>)edge.container).compareAndSet(edge.object,null);
                        }
                        case "COPY_ON_WRITE_LIST" -> {
                            var list=(CopyOnWriteArrayList<Object>)edge.container;
                            if(!edge.beforeImage.isEmpty()&&!edge.beforeImage.equals(listImage(edge.container,null))) { edge.reason="REFERENCE_BASELINE_IMAGE_CHANGED";return; }
                            for(int i=0;i<list.size();i++)if(list.get(i)==edge.object) {
                                if(ownedTarget(edge.object,runtime,true)!=edge.target) { edge.reason="REFERENCE_WRAPPER_TARGET_CHANGED";return; }
                                edge.attempted=true;list.remove(i);edge.applied=true;break;
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
            case "COPY_ON_WRITE_LIST" -> Arrays.stream(((CopyOnWriteArrayList<?>)container).toArray()).noneMatch(value->value==object);
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
                int split=item.field.lastIndexOf('#');if(split<0)throw new IllegalArgumentException("FIELD");
                Class<?> holder=source.holder instanceof Class<?> type?type:source.holder.getClass(),declaring=null;
                for(Class<?> type=holder;type!=null;type=type.getSuperclass())if(type.getName().equals(item.field.substring(0,split))) { declaring=type;break; }
                if(declaring==null) { item.reason="REFERENCE_DEFINITION_CHANGED";continue; }
                Field field=declaring.getDeclaredField(item.field.substring(split+1));
                if(!Modifier.isFinal(field.getModifiers())||!field.trySetAccessible()) { item.reason="REFERENCE_FIELD_UNSUPPORTED";continue; }
                Object container=field.get(source.holder instanceof Class<?>?null:source.holder);boolean absent=false;
                if(item.kind.equals("DIRECT_FIELD")||item.kind.equals("UUID_SELECTOR"))absent=container==null;
                else if(item.kind.equals("ATOMIC")&&container!=null&&container.getClass()==AtomicReference.class)absent=((AtomicReference<?>)container).get()==null;
                else if(item.kind.equals("COPY_ON_WRITE_LIST")&&container!=null&&container.getClass()==CopyOnWriteArrayList.class)
                    absent=item.expected.isEmpty()?((CopyOnWriteArrayList<?>)container).isEmpty():item.expected.equals(listImage(container,null));
                else if(item.kind.equals("ENTITY_MAP")&&container instanceof Map<?,?>) {
                    Object key;
                    if(item.key.startsWith("java.lang.String:"))key=item.key.substring(17);
                    else if(item.key.startsWith("java.util.UUID:"))key=UUID.fromString(item.key.substring(15));
                    else throw new IllegalArgumentException("KEY");
                    Object[] observed=SourceMaps.peek(container,key);
                    if(observed==null) { item.reason="SOURCE_MAP_QUERY_PENDING";continue; }
                    absent=!Boolean.TRUE.equals(observed[0]);
                }
                if(!absent) { item.reason="ORIGINAL_REFERENCE_LOCATION_PRESENT_FRESH_PROVENANCE_REQUIRED";continue; }
                item.record=item.prefix+"\tEDGE_RESTART\t"+item.id+"\t"+item.operation+"\tCURRENT_LOCATION_ABSENT_PRIOR_PROCESS_ENDED";
                item.ack=item.ack==null?runtime.journal.append(item.record):runtime.journal.retryRejected(item.record,item.ack);
                item.current=confirmed(item.ack);item.reason=item.current?"":"REFERENCE_RESTART_ACK_PENDING";
            } catch(ReflectiveOperationException|RuntimeException unavailable) { item.reason="REFERENCE_RESTART_QUERY_UNAVAILABLE:"+unavailable.getClass().getSimpleName(); }
            finally { if(prior!=item.current)epoch.incrementAndGet(); }
        }
    }
    List<String> remaining(UUID operation,UUID subject) {
        List<String> out=new ArrayList<>();
        if(!gap.isEmpty())out.add(gap);
        for(FieldRead read:fieldReads)if(read.consumers.contains(subject)) {
            if(read.holder.get()==null)out.add("CONSUMED_FIELD_HOLDER_RELEASE_UNOBSERVED:"+read.field.getDeclaringClass().getName()+"#"+read.field.getName());
            else if(scalarType(read.field.getType()))
                out.add("SCALAR_PRODUCER_CONSUMPTION_REQUIRES_DISPOSITION:"+read.field.getDeclaringClass().getName()+"#"+read.field.getName());
        }
        long now=Integer.toUnsignedLong(runtime.server.getTickCount());
        for(Historical item:historical)if(item.operation.equals(operation)&&(!item.current||item.sampled<0||now-item.sampled>10))
            out.add("REFERENCE_RESTART:"+item.id+":"+item.reason);
        for(var source:runtime.recoverySources.registered())if(source.subjects.contains(subject)) {
            if(!scanned.containsKey(source))out.add("REFERENCE_HOLDER_SCAN_PENDING");out.addAll(gaps.getOrDefault(source,Set.of()));
        }
        for(Edge edge:edges.values())if(edge.policy.operation.equals(operation)) {
            if(!confirmed(edge.ack))out.add("REFERENCE_EDGE_DURABLE_INTENT_ACK:"+edge.id+":"+ackState(edge.ack));
            if(!edge.released||!confirmed(edge.releaseAck)||!confirmed(edge.factAck))out.add("REFERENCE_EDGE:"+edge.id+":"+edge.reason);
            else try {
                Object container=edge.kind.equals("DIRECT_FIELD")||edge.kind.equals("UUID_SELECTOR")?edge.source.holder:edge.field.get(edge.receiver()),object=edge.objectIdentity.get();
                if(!runtime.recoverySources.current(edge.source)||container!=edge.containerIdentity.get())out.add("REFERENCE_OWNER_CHANGED:"+edge.id);
                else if(object!=null&&!absent(edge,container,object))out.add("REFERENCE_REINSERTED:"+edge.id);
            } catch(ReflectiveOperationException unavailable) { out.add("REFERENCE_QUERY_UNAVAILABLE:"+edge.id); }
        }
        return List.copyOf(out);
    }
    private static boolean confirmed(CompletableFuture<Void> ack) { return ack!=null&&ack.isDone()&&!ack.isCompletedExceptionally()&&!ack.isCancelled(); }
    private static String ackState(CompletableFuture<Void> ack) {
        if(ack==null||!ack.isDone())return "PENDING";
        return ack.isCompletedExceptionally()||ack.isCancelled()?"UNCONFIRMED":"CONFIRMED";
    }
    private static String text(String value) { return Base64.getUrlEncoder().withoutPadding().encodeToString(value.getBytes(StandardCharsets.UTF_8)); }
    private static String untext(String value) { return new String(Base64.getUrlDecoder().decode(value),StandardCharsets.UTF_8); }
    /** Full ordered NBT image of a real list; skip exactly one captured identity for its intended projection. */
    private static String listImage(Object container,Object removed) {
        if(container==null||container.getClass()!=CopyOnWriteArrayList.class)return "";
        Object[] values=((CopyOnWriteArrayList<?>)container).toArray();if(values.length>4096)return "";
        StringBuilder canonical=new StringBuilder("NBT-LIST/1\n");boolean skipped=false;
        for(Object value:values) {
            if(removed!=null&&value==removed&&!skipped) { skipped=true;continue; }
            if(!(value instanceof CompoundTag tag)||value.getClass()!=CompoundTag.class)return "";
            try { canonical.append(RecoveryImage.semanticHash(tag)).append('\n'); }
            catch(java.io.IOException unsupported) { return ""; }
        }
        if(removed!=null&&!skipped)return "";
        return RecoveryImage.hash(canonical.toString().getBytes(StandardCharsets.UTF_8));
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
        edges.clear();policies.clear();gaps.clear();scanned.clear();fieldReads.clear();writerFields.clear();creationReads.clear();selectors.clear();
    }
}
