package dev.ronova.pro;

import java.lang.ref.WeakReference;
import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.Entity;

/** Actual memory-source cleanup under the current group policy. */
final class RecoveryRecords implements AutoCloseable {
    private final CopyOnWriteArrayList<FieldRead> fieldReads=new CopyOnWriteArrayList<>();
    private final Map<Object,Map<Class<?>,Map<String,FieldRead>>> writerFields=Collections.synchronizedMap(new WeakIdentityMap<>());
    private volatile boolean writerFieldsActive;
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
                fields.putIfAbsent(field.getName()+":"+field.getType().descriptorString(),field);
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
    private final Map<RecoverySources.Source,Set<String>> gaps=new IdentityHashMap<>();
    private final Map<RecoverySources.Source,Long> scanned=new IdentityHashMap<>();
    private final Map<RecoverySources.Source,ReferenceScan> scans=new IdentityHashMap<>();
    private static final class ReferenceScan {
        final WeakReference<Object> holder;
        final List<Field> fields=new ArrayList<>();
        final Set<String> issues=new TreeSet<>();
        Set<String> completedIssues=Set.of();
        final Map<Field,WeakReference<Object>> containers=new HashMap<>();
        final Map<Field,WeakReference<Object>> listBackings=new HashMap<>();
        final Map<Field,Long> listRevisions=new HashMap<>();
        final Map<Field,Long> mapRevisions=new HashMap<>(),arrayRevisions=new HashMap<>();
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
    private final AtomicLong epoch=new AtomicLong();
    private long cursor;
    private long edgeCursor;
    private boolean closing;
    private String gap="";
    private final ThreadPoolExecutor worker=new ThreadPoolExecutor(1,1,0,TimeUnit.MILLISECONDS,new ArrayBlockingQueue<>(32),r->{
        Thread thread=new Thread(r,"ronova-pro-memory-sources");thread.setDaemon(true);return thread;
    },new ThreadPoolExecutor.AbortPolicy());
    private static final class Edge {
        final UUID id=UUID.randomUUID();volatile Policy policy;final RecoverySources.Source source;final Field field;
        final Object key;Object target;final String kind;
        final WeakReference<Object> objectIdentity,containerIdentity;
        Object object,container;
        SourceMaps.Cursor removalCursor;
        volatile boolean running,attempted,applied,observed,released;
        volatile String reason="";
        Edge(Policy policy,RecoverySources.Source source,Field field,Object key,Object container,Object object,Object target,String kind) {
            this.policy=policy;this.source=source;this.field=field;this.key=key;this.container=container;this.object=object;this.target=target;this.kind=kind;
            objectIdentity=new WeakReference<>(object);containerIdentity=new WeakReference<>(container);
        }
        Object receiver()throws IllegalAccessException {Object holder=source.holder();if(holder==null)throw new IllegalAccessException("HOLDER_RELEASE_UNOBSERVED");return holder instanceof Class<?>?null:holder;}
    }
    RecoveryRecords(ProRuntime runtime,RecoverySources sources) {
        this.runtime=runtime;
    }
    /** Resolve the field reader's own return/cast types before publishing a callable field policy. */
    static void prepareFieldPolicy()throws ReflectiveOperationException {
        ClassLoader loader=RecoveryRecords.class.getClassLoader();
        Class.forName(FieldRead.class.getName(),true,loader);
        Class.forName(Selector.class.getName(),true,loader);
        java.lang.invoke.MethodHandles.lookup().findVirtual(RecoveryRecords.class,"fieldRead",
                java.lang.invoke.MethodType.methodType(FieldRead.class,Object.class,Class.class,String.class));
    }

    void qualify(ProRuntime.Subject subject,UUID operation) {
        ProRuntime.Subject canonical=runtime.canonicalSubject(subject.id);
        if(canonical!=null&&canonical!=subject) {qualify(canonical,runtime.groupOperation(canonical.id,operation));return;}
        if(closing||!subject.terminal)return;
        Policy prior=policies.get(subject.id);
        if(prior!=null&&prior.generation==subject.generation)return;
        Policy current=new Policy(subject,operation,subject.generation);policies.put(subject.id,current);
        for(Edge edge:edges.values())if(!edge.running&&runtime.canonicalSubject(edge.policy.subject.id)==subject)edge.policy=current;
        // Resolve actual earlier reads now that the body has a subject; no global class scan.
        for(FieldRead read:fieldReads) {
            Object holder=read.holder.get(),value=read.value();if(holder==null||value==null)continue;
            if(subject(value)!=null&&subject(value).equals(subject.id))runtime.recoverySources.observedHolder(holder,subject.id);
        }
        for(var source:runtime.recoverySources.registered())if(!Collections.disjoint(source.subjects,runtime.groupTargets(subject.id))) {
            scanned.remove(source);ReferenceScan scan=scans.get(source);if(scan!=null){scan.fieldIndex=0;scan.field=null;scan.container=null;scan.list=null;scan.map=null;}
            Set<String> issues=new TreeSet<>();
            try {scan(source,issues);}catch(ReflectiveOperationException|java.io.IOException|RuntimeException unavailable) {issues.add("REFERENCE_SCAN_UNAVAILABLE");}
            gaps.put(source,Set.copyOf(issues));
        }
    }
    /** Metadata comes from the bootstrap's actual woven read, not a caller-declared source. */
    Object observedField(Object receiver,Object[] packet) {
        if(packet.length!=5||packet[0]!=receiver||!(packet[1] instanceof Class<?> owner)
                ||!(packet[2] instanceof String name)||!(packet[3] instanceof String descriptor)||receiver instanceof Entity)return null;
        if(owner.getClassLoader()==null||owner.getName().startsWith("net.minecraft.")||owner.getName().startsWith("com.mojang.")||owner.getName().startsWith("dev.ronova.pro."))return null;
        Field field=FIELDS.get(owner).get(name+":"+descriptor);if(field==null)return null;
        if(field.getType().isPrimitive())return null;
        if(Modifier.isStatic(field.getModifiers())!=(receiver instanceof Class<?>))return null;
        if(receiver instanceof Class<?>)receiver=field.getDeclaringClass();
        Object value=packet[4];
        try {if(!field.trySetAccessible()||!sameFieldValue(field,field.get(receiver instanceof Class<?>?null:receiver),value))return null;}
        catch(IllegalAccessException|RuntimeException unavailable){return null;}
        FieldRead read=fieldRead(receiver,field.getDeclaringClass(),name);
        FieldRead previous=read;
        if(read!=null&&!sameFieldValue(field,read.value(),value))read=null;
        if(read==null) {
            SourceMaps.observeHolder(receiver);
            synchronized(fieldReads) {
                fieldReads.removeIf(old->old.holder.get()==null||old.value()==null&&old.consumers.isEmpty());
                read=new FieldRead(field,receiver,value);fieldReads.add(read);
                if(previous!=null)read.selectors=previous.selectors;
                synchronized(writerFields) {
                    writerFields.computeIfAbsent(receiver,key->new HashMap<>()).computeIfAbsent(field.getDeclaringClass(),key->new HashMap<>()).put(name,read);
                    writerFieldsActive=true;
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
        observedField(holder,new Object[]{holder,owner,name,"Ljava/util/UUID;",value});
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
            observedField(packet[0],packet);
            Field field=FIELDS.get(owner).get(name+":"+packet[3]);if(field==null)continue;
            Object receiver=packet[0] instanceof Class<?>?field.getDeclaringClass():packet[0];
            FieldRead read=fieldRead(receiver,field.getDeclaringClass(),name);
            if(read!=null&&sameFieldValue(field,read.value(),packet[4]))reads.add(read);
        }
        return List.copyOf(reads);
    }
    private void consume(UUID subject,FieldRead read) {
        boolean added=read.consumers.add(subject);if(added)epoch.incrementAndGet();
        fieldReads.addIfAbsent(read);
        Object holder=read.holder.get();if(holder==null)return;
        RecoverySources.Source source=runtime.recoverySources.observedHolder(holder,subject);
        if(added&&source!=null)scanned.remove(source);
    }
    boolean fieldWriteDenied(Object receiver,Class<?> declaring,String name,Object incoming) {
        if(!writerFieldsActive||declaring==null)return false;
        String owner=declaring.getName();
        if(owner.startsWith("net.minecraft.")||owner.startsWith("com.mojang.")||owner.startsWith("dev.ronova.pro."))return false;
        if(PRIMITIVES.get(declaring).contains(name))return false;
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
    private static final ClassValue<Set<String>> PRIMITIVES=new ClassValue<>(){
        @Override protected Set<String> computeValue(Class<?> type){Set<String> names=new HashSet<>();for(Field field:type.getDeclaredFields())if(field.getType().isPrimitive())names.add(field.getName());return Set.copyOf(names);}
    };
    private static boolean sameFieldValue(Field field,Object left,Object right) {
        return field.getType().isPrimitive()?Objects.equals(left,right):left==right;
    }
    private boolean ownerCurrent(Edge edge) throws IllegalAccessException {
        return runtime.recoverySources.current(edge.source)&&(edge.kind.equals("DIRECT_FIELD")?
                edge.source.holder()==edge.container:edge.kind.equals("UUID_SELECTOR")?edge.source.holder()==edge.container:
                edge.container!=null);
    }
    String dependencies(UUID operation,UUID subject) {
        StringBuilder key=new StringBuilder(gap);
        for(Policy policy:policies.values())if(policy.operation.equals(operation))
            for(FieldRead read:fieldReads)if(read.consumers.contains(policy.subject.id))
                key.append("|CONSUMED:").append(read.field.getDeclaringClass().getName()).append('#').append(read.field.getName());
        for(Edge edge:edges.values())if(edge.policy.operation.equals(operation))key.append('|').append(edge.id).append(':')
            .append(edge.attempted).append(':').append(edge.applied).append(':').append(edge.observed).append(':').append(edge.released);
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
        for(Edge edge:active)if(!edge.running){Policy current=policy(edge.policy.subject.id);if(current!=null)edge.policy=current;}
        int budget=32;
        // A busy earlier source must not monopolize the next tick.
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
        ReferenceScan existing=scans.get(source);
        if(existing!=null&&existing.fieldIndex==0&&existing.field==null&&existing.completedIssues.isEmpty()&&scanCurrent(source))return true;
        boolean isStatic=actualHolder instanceof Class<?>;
        ReferenceScan scan=existing;
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
                scan.containers.put(field,new WeakReference<>(container));scan.listRevisions.remove(field);scan.listBackings.remove(field);scan.mapRevisions.remove(field);scan.arrayRevisions.remove(field);scanned.remove(source);
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
            if(SourceMaps.removedJdkContainer(container)){
                scan.issues.add("JDK_CONTAINER_BACKEND_UNSUPPORTED:"+field.getName());scan.finishField();continue;
            }
            if(container instanceof Object[] values) {
                if(values.length==0){scan.finishField();continue;}
                int limit=Math.min(Math.min(32,remaining),values.length-scan.listIndex);
                Object ticket=ArrayAccess.READ.invoke(null,container);
                if(Boolean.FALSE.equals(ticket)){issues.add("REFERENCE_ARRAY_BUSY:"+field.getName());break;}
                long revision=(Long)ArrayAccess.REVISION.invoke(null,container);
                if(!Objects.equals(scan.arrayRevisions.put(field,revision),revision)){scan.listIndex=0;scanned.remove(source);}
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
            } else if(container.getClass()==AtomicReference.class){
                Object value=((AtomicReference<?>)container).get();
                if(nestedHolder(value)&&ownedTarget(value,runtime,true)==null)nested(source,field,"ATOMIC",null,value,scan.issues);
                else if(unsupportedWrapper(value))scan.issues.add("REFERENCE_WRAPPER_PROFILE_UNSUPPORTED:"+value.getClass().getName());
                else capture(source,field,null,container,value,"ATOMIC",scan.issues);
                scan.finishField();
            } else if(SourceMaps.supportedList(container)) {
                ReferenceScan active=scan;int limit=Math.min(32,remaining);int[] used={0};boolean[] done={false};
                boolean ran=withArrayList(container,false,()->{
                    long revision=arrayListRevision(container);
                    Object backing;
                    try{backing=SourceMaps.listBacking(container);}catch(ReflectiveOperationException unavailable){throw new IllegalStateException("REFERENCE_LIST_BACKING_UNAVAILABLE",unavailable);}
                    WeakReference<Object> prior=active.listBackings.put(field,new WeakReference<>(backing));
                    if(!Objects.equals(active.listRevisions.put(field,revision),revision)||prior==null||prior.get()!=backing) {
                        active.listIndex=0;scanned.remove(source);
                    }
                    List<?> list=(List<?>)container;
                    while(used[0]<limit&&active.listIndex<list.size()) {
                        Object value=list.get(active.listIndex++);used[0]++;
                        if(nestedHolder(value)&&ownedTarget(value,runtime,true)==null)nested(source,field,"LIST",active.listIndex-1,value,active.issues);
                        else if(unsupportedWrapper(value))active.issues.add("REFERENCE_WRAPPER_PROFILE_UNSUPPORTED:"+value.getClass().getName());
                        else capture(source,field,null,container,value,container.getClass()==CopyOnWriteArrayList.class?"COPY_ON_WRITE_LIST":"ARRAY_LIST",active.issues);
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
                scan.mapRevisions.put(field,scan.map.revision);
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
                scan.mapRevisions.put(field,scan.map.revision);
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
            Long mapRevision=scan.mapRevisions.get(entry.getKey());
            if(mapRevision!=null){Object map=SourceMaps.supportedSet(actual)?SourceMaps.setMap(actual):actual;if(mapRevision.longValue()!=SourceMaps.revision(map))return false;}
            Long arrayRevision=scan.arrayRevisions.get(entry.getKey());
            if(arrayRevision!=null){
                Object ticket=ArrayAccess.READ.invoke(null,actual);if(Boolean.FALSE.equals(ticket))return false;
                try{if(arrayRevision.longValue()!=(Long)ArrayAccess.REVISION.invoke(null,actual))return false;}
                finally{ArrayAccess.END_READ.invoke(null,ticket);}
            }
            Long revision=scan.listRevisions.get(entry.getKey());
            if(revision!=null) {
                boolean[] same={false};
                if(!withArrayList(actual,false,()->same[0]=revision.longValue()==arrayListRevision(actual))||!same[0])return false;
                WeakReference<Object> backing=scan.listBackings.get(entry.getKey());
                if(backing==null||backing.get()!=SourceMaps.listBacking(actual))return false;
            }
        }
        return true;
    }
    private void captureSelector(Policy policy,RecoverySources.Source source,Selector selector,Entity output,Set<String> issues)throws ReflectiveOperationException {
        for(Edge edge:edges.values())if(edge.policy==policy&&!edge.released&&edge.target==output&&edge.source==source&&edge.kind.equals("UUID_SELECTOR")
                &&edge.field.equals(selector.read.field)&&edge.objectIdentity.get()==selector.read.value())return;
        Edge edge=new Edge(policy,source,selector.read.field,selector.value,source.holder(),selector.read.value(),output,"UUID_SELECTOR");
        edges.put(edge.id,edge);epoch.incrementAndGet();
        if(runtime.server.isSameThread())advance(edge);
    }
    private void capture(RecoverySources.Source source,Field field,Object key,Object container,Object value,String kind,Set<String> issues) {
        Object target=ownedTarget(value,runtime,true);
        UUID subject=subject(value);Policy policy=policy(subject);
        if(policy==null||!source.subjects.contains(subject)||!policy.subject.terminal||policy.subject.generation!=policy.generation)return;
        for(Edge old:edges.values())if(old.policy==policy&&old.target==target&&old.kind.equals(kind)&&old.source==source&&old.field.equals(field)&&old.containerIdentity.get()==container
                &&old.objectIdentity.get()==value&&Objects.equals(old.key,key)&&!old.released)return;
        Edge edge=new Edge(policy,source,field,key,container,value,target,kind);
        edges.put(edge.id,edge);epoch.incrementAndGet();
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
    /** Only profiles whose ownership cannot change through a later record
     * delegate query may participate in definition-only bulk retirement. */
    static boolean definitionOnlyValue(Object value) {
        if(value==null||value instanceof Entity||value instanceof net.minecraft.nbt.Tag||value instanceof Thread
                ||value instanceof Runnable||value instanceof java.util.concurrent.Callable<?>||value instanceof java.util.concurrent.Future<?>
                ||value instanceof java.util.concurrent.CompletionStage<?>||value instanceof java.util.concurrent.Executor)return false;
        Field[] fields=WRAPPER_FIELDS.get(value.getClass());if(fields==OPAQUE_WRAPPER)return true;
        for(Field field:fields)if(!field.getType().isPrimitive())return false;return true;
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
    private static boolean removedContainerEdge(String kind,Object container){
        return !kind.equals("DIRECT_FIELD")&&!kind.equals("UUID_SELECTOR")
                &&SourceMaps.removedJdkContainer(container);
    }
    private void advance(Edge edge)throws ReflectiveOperationException {
        if(removedContainerEdge(edge.kind,edge.container)){edge.reason="JDK_CONTAINER_BACKEND_UNSUPPORTED";return;}
        if(edge.kind.equals("HASH_SET")&&SourceMaps.supportedSet(edge.container)){
            if(!SourceMaps.with(SourceMaps.setMap(edge.container),()->advanceLocked(edge)))edge.reason="REFERENCE_SET_SCOPE_BUSY_OR_UNOBSERVED";
        }else if(edge.kind.equals("OBJECT_ARRAY")&&edge.container instanceof Object[] values) {
            int index=(Integer)edge.key;
            if(index<0||index>=values.length){edge.reason="REFERENCE_ARRAY_INDEX_CHANGED";return;}
            Object ticket=ArrayAccess.BEGIN.invoke(null,values,index,1);
            if(Boolean.FALSE.equals(ticket)){edge.reason="REFERENCE_ARRAY_BUSY";return;}
            edge.applied=false;
            try {
                advanceLocked(edge);
            }finally {ArrayAccess.FINISH.invoke(null,ticket,edge.applied);}
        }else if((edge.kind.equals("ARRAY_LIST")||edge.kind.equals("COPY_ON_WRITE_LIST"))&&edge.container!=null) {
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
        if(SourceMaps.disposedField(edge.source,edge.field)) {
            if(edge.kind.equals("DIRECT_FIELD")||edge.kind.equals("UUID_SELECTOR")||absent(edge,edge.container,edge.object)){releaseObservedReference(edge);return;}
            // A cleared holder does not erase the old alias. Continue the captured
            // exact-container obligation instead of waiting forever on detachment.
        }
        if(!ownerCurrent(edge)) {edge.reason="REFERENCE_OWNER_CHANGED";return;}
        if(absent(edge,edge.container,edge.object)){releaseObservedReference(edge);return;}
        // Identity-conditional memory cleanup is idempotent under the current group policy.
        if(edge.kind.equals("ENTITY_MAP"))removeMap(edge);else remove(edge);
        boolean absent=absent(edge,edge.container,edge.object);
        if(absent)releaseObservedReference(edge);else if(edge.reason.isEmpty())edge.reason="REFERENCE_IDENTITY_PRESENT";
    }
    private void releaseObservedReference(Edge edge) {
        edge.observed=true;edge.released=true;edge.reason="";
        edge.object=null;edge.target=null;edge.container=null;
        epoch.incrementAndGet();
    }
    /** Remove the captured identity under its actual supported map write scope. */
    @SuppressWarnings("unchecked") private void removeMap(Edge edge)throws ReflectiveOperationException {
        if(edge.released)return;
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
                    String graphGap=RecoverySources.recordGraphGap(runtime.recoverySources,tag,RecoverySources.subject(runtime.recoverySources,tag));
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
        if(removed[0]&&!SourceMaps.primitiveMap(edge.container))SourceMaps.changed(edge.container);
    }
    @SuppressWarnings("unchecked") private void remove(Edge edge)throws ReflectiveOperationException {
        if(edge.released)return;
        boolean graph=edge.target instanceof CompoundTag;
        if(graph&&!RecoverySources.tryRecordGraph())return;
        try {
            if(edge.target instanceof CompoundTag tag) {
                String gap=RecoverySources.recordGraphGap(runtime.recoverySources,tag,RecoverySources.subject(runtime.recoverySources,tag));
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
                        case "OBJECT_ARRAY" -> {
                            var elements=java.lang.invoke.MethodHandles.arrayElementVarHandle(edge.container.getClass());
                            edge.attempted=true;
                            edge.applied=elements.compareAndSet(edge.container,(Integer)edge.key,edge.object,null);
                        }
                        case "HASH_SET" -> {
                            Object backend=SourceMaps.setMap(edge.container);
                            if(edge.removalCursor==null||edge.removalCursor.map!=backend)edge.removalCursor=new SourceMaps.Cursor(backend);
                            try{SourceMaps.visit(backend,edge.removalCursor,32,(key,value,node,position)->{
                                if(key!=edge.object)return true;
                                edge.attempted=true;edge.applied=SourceMaps.removeIdentityVisited(position,edge.object);return false;
                            });}catch(java.io.IOException unavailable){edge.reason="REFERENCE_SET_SCAN_UNAVAILABLE";}
                        }
                        case "ATOMIC" -> {
                            var atomic=(AtomicReference<Object>)edge.container;
                            edge.attempted=true;edge.applied=atomic.compareAndSet(edge.object,null);
                            if(edge.applied)SourceMaps.changed(edge.container);
                        }
                        case "ARRAY_LIST", "COPY_ON_WRITE_LIST" -> {
                            var list=(List<Object>)edge.container;
                            if(ownedTarget(edge.object,runtime,true)!=edge.target) { edge.reason="REFERENCE_WRAPPER_TARGET_CHANGED";return; }
                            edge.attempted=true;edge.applied=SourceMaps.removeListIdentity(list,edge.object);
                        }
                        default -> throw new IllegalStateException("UNKNOWN_REFERENCE_ADAPTER");
                    }
                } finally { edge.source.gate.unlock(); }
            } finally { edge.policy.subject.commitGate.unlock(); }
        } finally { if(graph)RecoverySources.releaseRecordGraph(); }
    }
    private boolean absent(Edge edge,Object container,Object object) {
        if(removedContainerEdge(edge.kind,container)){edge.reason="JDK_CONTAINER_BACKEND_UNSUPPORTED";return false;}
        return switch(edge.kind) {
            case "DIRECT_FIELD", "UUID_SELECTOR" -> {
                try {yield edge.field.get(edge.receiver())!=object;}
                catch(IllegalAccessException unavailable) {edge.reason="DIRECT_FIELD_READ_UNAVAILABLE";yield false;}
            }
            case "OBJECT_ARRAY" -> ((Object[])container)[(Integer)edge.key]!=object;
            case "ATOMIC" -> ((AtomicReference<?>)container).get()!=object;
            case "ARRAY_LIST", "COPY_ON_WRITE_LIST" -> {
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
    List<String> remaining(UUID operation,UUID subject) {
        List<String> out=new ArrayList<>();
        if(!gap.isEmpty())out.add(gap);
        Set<UUID> targets=runtime.groupTargets(subject);
        for(FieldRead read:fieldReads)if(!Collections.disjoint(read.consumers,targets)) {
            if(read.disposed)continue;
            if(read.holder.get()==null)out.add("CONSUMED_FIELD_HOLDER_RELEASE_UNOBSERVED:"+read.field.getDeclaringClass().getName()+"#"+read.field.getName());
            else if(scalarType(read.field.getType()))
                out.add("SCALAR_PRODUCER_CONSUMPTION_REQUIRES_DISPOSITION:"+read.field.getDeclaringClass().getName()+"#"+read.field.getName());
        }
        for(var source:runtime.recoverySources.registered())if(!Collections.disjoint(source.subjects,runtime.groupTargets(subject))) {
            try {if(!scanCurrent(source))out.add("REFERENCE_HOLDER_SCAN_PENDING");}
            catch(ReflectiveOperationException|RuntimeException unavailable){out.add("REFERENCE_HOLDER_SCAN_UNAVAILABLE");}
            out.addAll(gaps.getOrDefault(source,Set.of()));
        }
        for(Edge edge:edges.values())if(edge.policy.operation.equals(operation)) {
            if(edge.running||!edge.released||!edge.observed)out.add("REFERENCE_EDGE:"+edge.id+":"+edge.reason);
            else try {
                if(edge.source.disposed()&&edge.source.holder()==null)continue;
                Object container=edge.containerIdentity.get(),object=edge.objectIdentity.get();
                // Recheck the original identity after its actual absence was observed.
                if(container==null)continue;
                if(!runtime.recoverySources.current(edge.source))out.add("REFERENCE_OWNER_CHANGED:"+edge.id);
                else if(object!=null&&!queryAbsent(edge,container,object))out.add("REFERENCE_REINSERTED_OR_QUERY_PENDING:"+edge.id);
            } catch(ReflectiveOperationException unavailable) { out.add("REFERENCE_QUERY_UNAVAILABLE:"+edge.id); }
        }
        return List.copyOf(out);
    }
    List<String> status(UUID operation) {
        List<String> rows=new ArrayList<>();
        for(Edge edge:edges.values())if(edge.policy.operation.equals(operation))
            rows.add("MEMORY_EDGE:"+edge.id+":"+edge.kind+":"+(edge.released?"RELEASED":edge.applied?"APPLIED":edge.attempted?"EFFECT_QUERY_REQUIRED":"PENDING")+":"+edge.reason);
        return List.copyOf(rows);
    }
    private boolean queryAbsent(Edge edge,Object container,Object object)throws ReflectiveOperationException {
        if(edge.kind.equals("HASH_SET")){
            boolean[] absent={false};return SourceMaps.with(SourceMaps.setMap(container),()->absent[0]=absent(edge,container,object))&&absent[0];
        }
        if(edge.kind.equals("ARRAY_LIST")||edge.kind.equals("COPY_ON_WRITE_LIST")) {
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
    private static final class ArrayAccess {
        static final java.lang.reflect.Method REVISION=arrayRevisionMethod();
        private static java.lang.reflect.Method arrayRevisionMethod(){try{return Class.forName("dev.ronova.pro.bootstrap.ControlBridge",false,null).getMethod("arrayRevision",Object.class);}catch(ReflectiveOperationException failure){throw new ExceptionInInitializerError(failure);}}
        static final Method BEGIN=method("beginRecoveryArrayMutation",Object.class,int.class,int.class),
                READ=method("beginRecoveryArrayRead",Object.class),END_READ=method("endFieldMutation",Object.class),
                FINISH=method("finishRecoveryArrayMutation",Object.class,boolean.class);
        private static Method method(String name,Class<?>... parameters){
            try{return Class.forName("dev.ronova.pro.bootstrap.ControlBridge",false,null).getMethod(name,parameters);}
            catch(ReflectiveOperationException unavailable){throw new ExceptionInInitializerError(unavailable);}
        }
    }
    static boolean withArrayList(Object list,boolean write,Runnable action)throws ReflectiveOperationException {
        if(!SourceMaps.supportedList(list))return false;
        return SourceMaps.with(list,action::run);
    }
    static long arrayListRevision(Object list) {
        long revision=SourceMaps.revision(list);if(revision<0)throw new IllegalStateException("REFERENCE_ARRAY_LIST_UNOBSERVED");return revision;
    }
    @Override public void close()throws java.io.IOException {
        closing=true;worker.shutdown();if(!worker.isTerminated())throw new java.io.IOException("REFERENCE_WORKER_EXIT_PENDING");
        for(Edge edge:edges.values())if(edge.running)throw new java.io.IOException("REFERENCE_WORKER_FRAME_PENDING");
        for(Edge edge:edges.values()) {
            edge.object=null;edge.target=null;edge.container=null;
        }
        edges.clear();policies.clear();gaps.clear();scanned.clear();scans.clear();fieldReads.clear();writerFields.clear();writerFieldsActive=false;creationReads.clear();selectors.clear();
    }
}
