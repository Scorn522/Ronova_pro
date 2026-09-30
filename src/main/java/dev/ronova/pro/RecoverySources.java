package dev.ronova.pro;

import java.io.*;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import net.minecraft.nbt.*;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.storage.DimensionDataStorage;
import net.minecraft.world.entity.Entity;

/** Production NBT stream observer. A pathname or caller supplied digest is never a save observation. */
public final class RecoverySources implements AutoCloseable {
    public interface CacheOwner { Map<String,SavedData> pro$cacheView(); }
    public interface GraphNode { boolean pro$closedGraph(); }
    private static final Map<CompoundTag,Origin> TAGS=Collections.synchronizedMap(new WeakIdentityMap<>());
    private static final Map<FileOutputStream,Capture> OPEN=Collections.synchronizedMap(new IdentityHashMap<>());
    private static final java.util.concurrent.locks.ReentrantReadWriteLock GRAPH=new java.util.concurrent.locks.ReentrantReadWriteLock();
    private static final Map<CompoundTag,Integer> SAVING=new IdentityHashMap<>();
    private static final Set<CompoundTag> AMBIGUOUS=Collections.newSetFromMap(new WeakIdentityMap<>());
    private static final Set<Class<?>> SCALARS=Set.of(EndTag.class,ByteTag.class,ShortTag.class,IntTag.class,LongTag.class,
            FloatTag.class,DoubleTag.class,ByteArrayTag.class,StringTag.class,IntArrayTag.class,LongArrayTag.class);
    private static volatile boolean graphGap;
    private static final ThreadLocal<SavedData> HOLDER=new ThreadLocal<>();
    private static final ThreadLocal<Integer> CAPTURE_DEPTH=ThreadLocal.withInitial(()->0);
    private static final int TAG_LIMIT=65536, SOURCE_LIMIT=4096;
    private final ProRuntime runtime;
    private final ProRuntime.CommitGate registrationGate=new ProRuntime.CommitGate();
    private final Map<String,Source> sources=new ConcurrentHashMap<>();
    private final Set<String> expected=ConcurrentHashMap.newKeySet();
    private final Map<String,Set<UUID>> expectedSubjects=new ConcurrentHashMap<>();
    private final Map<String,Long> generationFloors=new ConcurrentHashMap<>();
    private final Map<String,String> restartDiagnostics=new ConcurrentHashMap<>();
    private final AtomicLong epoch=new AtomicLong();
    private long restartCursor;
    private long holderCursor;
    private final Map<Source,String> holderDiscovery=new WeakIdentityMap<>();
    private final java.util.concurrent.ArrayBlockingQueue<Load> loads=new java.util.concurrent.ArrayBlockingQueue<>(4096);
    private record Load(Entity entity,UUID subject,Class<?> holder) { }
    private final List<FileOutputStream> pendingCloses=Collections.synchronizedList(new ArrayList<>());
    private volatile String gap="";
    private volatile boolean closed;
    private record Origin(RecoverySources owner,UUID subject) { }
    static final class Source {
        final RecoverySources owner;
        final String locator,code;
        final Path path;
        final Object holder;
        /** Identity of the registered holder, so a holder with no map wrapper can still be checked. */
        final java.lang.ref.WeakReference<Object> holderIdentity;
        final String liveDefinition;
        final ProRuntime.CommitGate gate;
        final Set<UUID> subjects=ConcurrentHashMap.newKeySet();
        Map<?,?> cache;
        String cacheKey;
        DimensionDataStorage manager;
        final AtomicLong mutations=new AtomicLong();
        volatile long generation;
        volatile int writers;
        volatile Snapshot snapshot;
        volatile String gap="SAVE_NOT_OBSERVED";
        Source(RecoverySources owner,String locator,String code,Path path,Object holder) {
            this.owner=owner;this.locator=locator;this.code=code;this.path=path;this.holder=holder;
            this.holderIdentity=new java.lang.ref.WeakReference<>(holder);
            liveDefinition=definition(holder instanceof Class<?> type?type:holder.getClass(),false);
            gate=owner.registrationGate;
        }
        boolean current(Snapshot image) { return image!=null&&writers==0&&snapshot==image&&generation==image.generation&&gap.isEmpty()&&owner.gap.isEmpty()&&owner.current(this); }
    }
    static final class Snapshot {
        final Source source;
        final long generation;
        final String identity,hash;
        final List<RecoveryImage.Selection> selections;
        private byte[] encoded;
        Snapshot(Source source,long generation,String identity,byte[] encoded,List<RecoveryImage.Selection> selections) {
            this.source=source;this.generation=generation;this.identity=identity;this.encoded=encoded;
            hash=RecoveryImage.hash(encoded);this.selections=List.copyOf(selections);
        }
        synchronized byte[] bytes()throws IOException { if(encoded==null)throw new IOException("SNAPSHOT_RETIRED");return encoded.clone(); }
        synchronized void release() { if(encoded!=null)Arrays.fill(encoded,(byte)0);encoded=null; }
    }
    RecoverySources(ProRuntime runtime) { this.runtime=runtime; }
    UUID runtimeSession() { return runtime.session; }
    ProRuntime runtimeOwner() { return runtime; }
    boolean liveRegistration(Source source) {
        if(closed||sources.get(source.locator)!=source)return false;
        if(source.cache==null)return true;
        return source.manager instanceof CacheOwner owner&&owner.pro$cacheView()==source.cache
                &&source.cache.get(source.cacheKey)==source.holder;
    }
    long epoch() { return epoch.get(); }
    void mapChanged(Source source) {source.mutations.incrementAndGet();epoch.incrementAndGet();}
    String dependencies(UUID subject) {
        StringBuilder key=new StringBuilder(gap).append(':').append(graphGap);
        for(Source source:registered().stream().sorted(Comparator.comparing(s->s.locator)).toList())if(source.subjects.contains(subject)) {
            Snapshot image=source.snapshot;
            key.append('|').append(source.locator).append(':').append(source.code).append(':').append(source.generation)
                .append(':').append(source.mutations.get()).append(':').append(source.writers).append(':').append(source.gap)
                .append(':').append(image==null?"none":image.identity+":"+image.hash);
        }
        return key.toString();
    }
    String gap() { return gap; }
    Collection<Source> registered() {
        for(Source source:sources.values())source.subjects.addAll(expectedSubjects.getOrDefault(source.locator,Set.of()));
        return List.copyOf(sources.values());
    }
    void expect(String locator,String code) { expected.add(locator+"\n"+code); }
    void expect(String locator,String code,UUID subject,long generation) {
        expect(locator,code);expectedSubjects.computeIfAbsent(locator,key->ConcurrentHashMap.newKeySet()).add(subject);
        generationFloors.merge(locator,generation,Math::max);
    }
    List<String> remaining(UUID subject) {
        List<String> remaining=new ArrayList<>();
        String mapObserver=SourceMaps.observerGap(this,subject);if(!mapObserver.isEmpty())remaining.add(mapObserver);
        if(!gap.isEmpty())remaining.add("SOURCE:"+gap);
        if(loads.stream().anyMatch(load->load.subject==null||load.subject.equals(subject)))remaining.add("SOURCE_LOAD_BINDING_PENDING");
        for(Source source:registered()) {
            String discovery=holderDiscovery.get(source);
            // A cached holder is already a possible source before its first disk save.
            // No recorded output is not evidence that it contains no recovery records.
            if(discovery==null)remaining.add("HOLDER_DISCOVERY_PENDING:"+source.locator);
            else if(!discovery.isEmpty())remaining.add("HOLDER_DISCOVERY:"+source.locator+":"+discovery);
        }
        synchronized(pendingCloses) { if(!pendingCloses.isEmpty())remaining.add("SOURCE_STREAM_RESOURCE_PENDING"); }
        for(var entry:expectedSubjects.entrySet())if(entry.getValue().contains(subject)&&!sources.containsKey(entry.getKey()))
            remaining.add("SOURCE_NOT_LOADED:"+entry.getKey()+":"+restartDiagnostics.getOrDefault(entry.getKey(),"WAIT_REGISTERED_READER"));
        for(Source source:registered())if(source.subjects.contains(subject)) {
            if(!current(source))remaining.add("SOURCE_OWNER_UNRESOLVED:"+source.locator);
            if(source.path!=null&&source.snapshot==null)remaining.add("SOURCE_SAVE_NOT_OBSERVED:"+source.locator);
            if(source.writers!=0)remaining.add("SOURCE_SAVE_IN_PROGRESS:"+source.locator);
            if(!source.gap.isEmpty()&&!source.gap.equals("SAVE_NOT_OBSERVED"))remaining.add("SOURCE:"+source.gap);
        }
        return List.copyOf(remaining);
    }
    boolean current(Source source) {
        return !closed&&bound(source);
    }
    boolean bound(Source source) { return bindingProblem(source).isEmpty(); }
    private String bindingProblem(Source source) {
        try(var storageGate=registrationGate.enter()) {
            if(sources.get(source.locator)!=source)return "REGISTRATION_REPLACED";
            String current=definition(source.holder instanceof Class<?> type?type:source.holder.getClass(),false);
            if(source.liveDefinition==null||current==null)return "DEFINITION_UNOBSERVED";
            if(!source.liveDefinition.equals(current))return "DEFINITION_CHANGED";
            if(source.holder instanceof Class<?>)return "";
            if(source.cache==null)return source.holderIdentity.get()==source.holder?"":"HOLDER_IDENTITY_CHANGED";
            if(source.cache.getClass()!=HashMap.class)return "CACHE_TYPE_CHANGED";
            if(!(source.manager instanceof CacheOwner owner)||owner.pro$cacheView()!=source.cache)return "CACHE_OWNER_CHANGED";
            return source.cache.get(source.cacheKey)==source.holder?"":"CACHE_ENTRY_CHANGED";
        }
    }
    void maintainResources() {
        List<String> pending=new ArrayList<>(expected);
        for(int i=0;i<Math.min(4,pending.size());i++) {
            String entry=pending.get((int)Math.floorMod(restartCursor++,pending.size()));
            int split=entry.lastIndexOf('\n');String locator=entry.substring(0,split),expectedCode=entry.substring(split+1);
            if(sources.containsKey(locator)||!locator.startsWith("STATIC|")&&!locator.startsWith("MEMORY|"))continue;
            String[] parts=locator.split("\\|",3);if(parts.length!=3)continue;
            try {
                Class<?> agent=Class.forName("dev.ronova.pro.agent.RecoveryAgent",false,ClassLoader.getSystemClassLoader());
                Class<?> holder=(Class<?>)agent.getMethod("initializedSource",String.class).invoke(null,parts[1]);
                if(holder==null) { restartDiagnostics.put(locator,"DEFINITION_NOT_INITIALIZED_OR_LOADER_AMBIGUOUS");continue; }
                String actual=code(holder);
                if(!expectedCode.equals(actual)) { restartDiagnostics.put(locator,"CODE_IDENTITY_CHANGED:expected="+expectedCode+":actual="+actual);continue; }
                try(var gate=registrationGate.enter()) {
                    Source source=new Source(this,locator,expectedCode,parts[0].equals("STATIC")?Path.of(parts[2]):null,holder);
                    source.generation=generationFloors.getOrDefault(locator,0L);source.subjects.addAll(expectedSubjects.getOrDefault(locator,Set.of()));
                    if(parts[0].equals("MEMORY"))source.gap="";
                    if(sources.putIfAbsent(locator,source)==null) { restartDiagnostics.remove(locator);epoch.incrementAndGet(); }
                }
            } catch(ReflectiveOperationException|RuntimeException|LinkageError unavailable) { /* Keep the named source waiting; never initialize arbitrary holders. */ }
        }
        for(int budget=0;budget<128;budget++) {
            Load load=loads.poll();if(load==null)break;
            if(load.holder!=null)memorySource(load.holder,load.subject);
            runtime.linkLoaded(load.entity,load.subject);
        }
        retireOutputHandles();
        discoverHolders();
    }
    /** Read-only discovery of exact observed tags in registered holders, including POJO wrappers.
     * This does not authorize unlinking an opaque record or writing an unobserved file. */
    private void discoverHolders() {
        if(!runtime.server.isSameThread()||closed)return;
        List<Source> registered=new ArrayList<>(sources.values());
        for(int i=0;i<Math.min(8,registered.size());i++) {
            Source source=registered.get((int)Math.floorMod(holderCursor++,registered.size()));
            String result="";
            if(!current(source))result="HOLDER_OWNER_CHANGED:"+bindingProblem(source);
            else if(!tryRecordGraph())result="HOLDER_GRAPH_BUSY";
            else try {
                Set<Object> seen=Collections.newSetFromMap(new IdentityHashMap<>());
                ArrayDeque<Object> queue=new ArrayDeque<>();queue.add(source.holder);
                int fields=0;
                while(!queue.isEmpty()) {
                    Object value=queue.removeFirst();if(!seen.add(value))continue;
                    if(seen.size()>4096) { result="HOLDER_DISCOVERY_BUDGET";break; }
                    if(value.getClass()==CompoundTag.class) {
                        CompoundTag tag=(CompoundTag)value;UUID subject=subject(this,tag);
                        if(subject!=null&&source.subjects.add(subject))epoch.incrementAndGet();
                        for(String key:tag.getAllKeys())enqueue(queue,tag.get(key));
                    } else if(value.getClass()==ListTag.class) {
                        for(Tag child:(ListTag)value)enqueue(queue,child);
                    } else if(SCALARS.contains(value.getClass())) {
                        // Array tags implement Collection, but their exact vanilla layouts
                        // contain primitive data, not entity/tag reference edges.
                    } else if(value.getClass()==HashMap.class||value.getClass()==ConcurrentHashMap.class) {
                        var entries=SourceMaps.entries(value);
                        if(entries==null) { result="HOLDER_"+SourceMaps.unavailable(value);continue; }
                        for(var entry:entries) { enqueue(queue,entry.getKey());enqueue(queue,entry.getValue()); }
                    } else if(value.getClass()==ArrayList.class||value.getClass()==HashSet.class
                            ||value.getClass()==java.util.concurrent.CopyOnWriteArrayList.class) {
                        for(Object child:(Collection<?>)value)enqueue(queue,child);
                    } else if(value.getClass()==java.util.concurrent.atomic.AtomicReference.class) {
                        enqueue(queue,((java.util.concurrent.atomic.AtomicReference<?>)value).get());
                    } else if(value.getClass().isArray()&&!value.getClass().getComponentType().isPrimitive()) {
                        for(Object child:(Object[])value)enqueue(queue,child);
                    } else if(isPrimitiveCollection(value)) {
                        // A primitive-typed collection cannot hold an object record, so its contents are consumed
                        // without reflection and nothing is enqueued. Treating it as an opaque container used to
                        // leave the whole chain unsettled even though there was nothing recoverable inside it.
                    } else if(value.getClass().isArray()&&value.getClass().getComponentType().isPrimitive()) {
                        // The same holds for a primitive array.
                    } else if(value instanceof Entity) {
                        UUID subject=runtime.recoveryTasks.subjectOf(value);
                        if(subject!=null&&source.subjects.add(subject))epoch.incrementAndGet();
                    } else if(value instanceof Map<?,?>||value instanceof Collection<?>) {
                        // Do not run arbitrary collection callbacks to guess their contents.
                        result="HOLDER_CONTAINER_UNSUPPORTED:"+value.getClass().getName();
                    } else {
                        boolean rootClass=value==source.holder&&value instanceof Class<?>;
                        Class<?> type=rootClass?(Class<?>)value:value.getClass();
                        if(SCALARS.contains(type)||type==String.class||type==UUID.class||type.isEnum()
                                ||type.getClassLoader()==null)continue;
                        for(Class<?> owner=type;owner!=null&&owner!=Object.class&&owner!=SavedData.class;
                                owner=rootClass?null:owner.getSuperclass()) {
                            for(var field:owner.getDeclaredFields()) {
                                if(java.lang.reflect.Modifier.isStatic(field.getModifiers())!=rootClass||field.getType().isPrimitive())continue;
                                if(++fields>512)throw new IOException("HOLDER_FIELD_BUDGET");
                                if(!field.trySetAccessible())throw new IOException("HOLDER_FIELD_INACCESSIBLE");
                                enqueue(queue,field.get(rootClass?null:value));
                            }
                        }
                    }
                    if(queue.size()>4096)throw new IOException("HOLDER_DISCOVERY_BUDGET");
                }
            } catch(ReflectiveOperationException|IOException|RuntimeException unavailable) {
                result="HOLDER_DISCOVERY_UNRESOLVED:"+unavailable.getClass().getSimpleName();
            } finally { releaseRecordGraph(); }
            if(!Objects.equals(holderDiscovery.put(source,result),result))epoch.incrementAndGet();
        }
    }
    /**
     * A container whose element type is primitive. Such a container cannot hold an object record, and iterating
     * it runs no user callback, so it is consumed rather than reported as an unsupported opaque layout.
     */
    private static boolean isPrimitiveCollection(Object value) {
        return value instanceof it.unimi.dsi.fastutil.longs.LongCollection
                ||value instanceof it.unimi.dsi.fastutil.ints.IntCollection
                ||value instanceof it.unimi.dsi.fastutil.shorts.ShortCollection
                ||value instanceof it.unimi.dsi.fastutil.bytes.ByteCollection
                ||value instanceof it.unimi.dsi.fastutil.chars.CharCollection
                ||value instanceof it.unimi.dsi.fastutil.booleans.BooleanCollection
                ||value instanceof it.unimi.dsi.fastutil.floats.FloatCollection
                ||value instanceof it.unimi.dsi.fastutil.doubles.DoubleCollection;
    }
    private static void enqueue(ArrayDeque<Object> queue,Object value)throws IOException {        if(value==null)return;
        if(queue.size()>=4096)throw new IOException("HOLDER_DISCOVERY_BUDGET");
        queue.addLast(value);
    }
    private void retireOutputHandles() {
        synchronized(pendingCloses) {
            var iterator=pendingCloses.iterator();int budget=8;
            while(iterator.hasNext()&&budget-->0) {
                FileOutputStream stream=iterator.next();
                try { stream.close();iterator.remove(); }
                catch(IOException failure) { gap="OUTPUT_RESOURCE_CLOSE_UNCONFIRMED"; }
            }
        }
    }
    Source resolve(String locator,String code) {
        Source source=sources.get(locator);return source!=null&&source.code.equals(code)&&bound(source)?source:null;
    }
    boolean acceptRead(String locator,String expectedCode,Path path) {
        String[] parts=locator.split("\\|",3);
        if(parts.length!=3||parts[0].equals("MEMORY")||!Path.of(parts[2]).equals(path)||!expected.contains(locator+"\n"+expectedCode))return false;
        try {
            Class<?> agent=Class.forName("dev.ronova.pro.agent.RecoveryAgent",false,ClassLoader.getSystemClassLoader());
            Class<?> holder=(Class<?>)agent.getMethod("loadedSource",String.class).invoke(null,parts[1]);
            return holder!=null&&expectedCode.equals(code(holder));
        } catch(ReflectiveOperationException|RuntimeException|LinkageError unavailable) { return false; }
    }
    public static FileInputStream openInput(File file)throws FileNotFoundException { return new Input(file); }
    private static final class ObservedPushback extends PushbackInputStream {
        final Input actual;
        ObservedPushback(InputStream input,int size) { super(input,size);actual=input instanceof Input observed?observed:null; }
    }
    public static PushbackInputStream pushback(InputStream input,int size) { return new ObservedPushback(input,size); }
    public static CompoundTag readInput(InputStream stream)throws IOException {
        Input observed=stream instanceof Input input?input:stream instanceof ObservedPushback pushback?pushback.actual:null;
        if(observed==null)return NbtIo.readCompressed(stream);
        try {
            CompoundTag decoded=NbtIo.readCompressed(new FilterInputStream(stream) { @Override public void close() { } });
            byte[] rest=new byte[8192];while(observed.read(rest)>=0) { }
            String ending=StorageNative.identity(observed.getFD(),observed.path.toString());
            byte[] bytes=observed.bytes.toByteArray();
            observed.close();
            try {
                if(!observed.overflow&&observed.identity!=null&&observed.identity.equals(ending))
                    for(ProRuntime runtime:ProRuntime.sourceRuntimes()) {
                        runtime.recoverySources.liveReadObserved(observed.path,ending,bytes,decoded);
                        runtime.recoveryStorage.readObserved(observed.path,ending,bytes,decoded);
                    }
            } finally { Arrays.fill(bytes,(byte)0);observed.bytes.reset(); }
            return decoded;
        } finally { observed.close(); }
    }
    private void liveReadObserved(Path path,String identity,byte[] bytes,CompoundTag decoded) {
        String hash=RecoveryImage.hash(bytes);
        for(Source source:registered()) {
            Snapshot snapshot=source.snapshot;
            if(!Objects.equals(path,source.path)||snapshot==null||!snapshot.identity.equals(identity)||!snapshot.hash.equals(hash)||!current(source))continue;
            try {
                if(!RecoveryImage.semanticHash(decoded).equals(RecoveryImage.semanticHash(RecoveryImage.decode(bytes))))throw new IOException("LIVE_READ_ENCODING_CHANGED");
                for(var selection:snapshot.selections) {
                    if(!runtime.subjects.containsKey(selection.subject()))continue;
                    var value=RecoveryImage.resolve(decoded,selection.path());
                    if(value instanceof CompoundTag tag)observed(tag,selection.subject());
                    else throw new IOException("LIVE_READ_TYPED_PATH_CHANGED");
                }
            } catch(IOException|RuntimeException invalid) { gap="LIVE_READ_PROVENANCE_UNRESOLVED:"+invalid.getMessage();epoch.incrementAndGet(); }
        }
    }
    private static final class Input extends FileInputStream {
        final Path path;
        final String identity;
        final ByteArrayOutputStream bytes=new ByteArrayOutputStream();
        boolean overflow;
        Input(File file)throws FileNotFoundException {
            super(file);path=file.toPath().toAbsolutePath().normalize();String captured=null;
            try { captured=StorageNative.identity(getFD(),path.toString()); }catch(IOException|RuntimeException|LinkageError unavailable) { }
            identity=captured;
        }
        @Override public int read()throws IOException {
            int value=super.read();if(value>=0)retain(new byte[]{(byte)value},0,1);return value;
        }
        @Override public int read(byte[] bytes,int offset,int length)throws IOException {
            int count=super.read(bytes,offset,length);if(count>0)retain(bytes,offset,count);return count;
        }
        @Override public int read(byte[] bytes)throws IOException { return read(bytes,0,bytes.length); }
        private void retain(byte[] data,int offset,int count) {
            if(overflow)return;if(bytes.size()>RecoveryImage.MAX_BYTES-count) { overflow=true;return; }bytes.write(data,offset,count);
        }
    }
    void observed(CompoundTag tag,UUID subject) {
        GRAPH.writeLock().lock();
        try {
        synchronized(TAGS) {
            Origin prior=TAGS.get(tag);
            if(prior!=null&&(prior.owner!=this||!prior.subject.equals(subject))) { AMBIGUOUS.add(tag);gap="CONFLICTING_TAG_ORIGIN";epoch.incrementAndGet();return; }
            if(prior==null&&TAGS.size()>=TAG_LIMIT) { gap="TAG_CAPTURE_CAPACITY";epoch.incrementAndGet();return; }
            TAGS.put(tag,new Origin(this,subject));
            if(prior==null)epoch.incrementAndGet();
            runtime.recoveryTasks.observed(tag,subject);
        }
        } finally { GRAPH.writeLock().unlock(); }
    }
    public static void copied(CompoundTag from,CompoundTag to) {
        GRAPH.writeLock().lock();
        try {
        synchronized(TAGS) {
            Origin origin=TAGS.get(from);
            if(AMBIGUOUS.contains(from)) {
                // Copying bytes does not resolve conflicting provenance. Retain the old
                // owner only for diagnostics/retirement, never as an execution authority.
                AMBIGUOUS.add(to);
                if(origin!=null) {
                    if(TAGS.size()<TAG_LIMIT||TAGS.containsKey(to))TAGS.put(to,origin);
                    else origin.owner.gap="TAG_CAPTURE_CAPACITY";
                    origin.owner.epoch.incrementAndGet();
                }
                return;
            }
            if(origin!=null)origin.owner.observed(to,origin.subject);
        }
        } finally { GRAPH.writeLock().unlock(); }
    }
    public static Object tagPut(Map<Object,Object> map,Object key,Object value) {
        GRAPH.writeLock().lock();try { return map.put(key,value); }finally { GRAPH.writeLock().unlock(); }
    }
    public static boolean graphConstructor(Class<?> type) {
        try { return StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE).walk(frames->frames
                .dropWhile(frame->frame.getDeclaringClass()!=type||!frame.getMethodName().equals("<init>"))
                .skip(1).findFirst().map(frame->{
                    Class<?> caller=frame.getDeclaringClass();
                    if(caller==type&&(frame.getMethodName().equals("<init>")||frame.getMethodName().equals("copy")
                            ||frame.getMethodName().equals("m_6426_")&&frame.getMethodType().parameterCount()==0))return true;
                    Object codec=type==CompoundTag.class?CompoundTag.TYPE:type==ListTag.class?ListTag.TYPE:null;
                    return codec!=null&&caller==codec.getClass();
                }).orElse(false));
        } catch(RuntimeException|LinkageError unavailable) { return false; }
    }
    public static void graphExposed(Runnable invalidate) {
        GRAPH.writeLock().lock();try { invalidate.run(); }finally { GRAPH.writeLock().unlock(); }
    }
    public static Object listSet(List<Object> list,int index,Object value) {
        GRAPH.writeLock().lock();try { return list.set(index,value); }finally { GRAPH.writeLock().unlock(); }
    }
    public static void listAdd(List<Object> list,int index,Object value) {
        GRAPH.writeLock().lock();try { list.add(index,value); }finally { GRAPH.writeLock().unlock(); }
    }
    public static void saveBeginning(Entity entity,CompoundTag input) {
        if(input==null)return;
        GRAPH.writeLock().lock();
        try {
            if(SAVING.size()>=TAG_LIMIT&&!SAVING.containsKey(input)) { graphGap=true;return; }
            SAVING.merge(input,1,Integer::sum);
            synchronized(TAGS) {
                Origin prior=TAGS.get(input);
                if(prior!=null&&!prior.subject.equals(prior.owner.runtime.recoveryTasks.subjectOf(entity))) {
                    AMBIGUOUS.add(input);
                    try(var storageGate=prior.owner.registrationGate.enter()) {
                        prior.owner.gap="TAG_REBOUND_OR_UNKNOWN_SAVER";prior.owner.epoch.incrementAndGet();
                    }
                }
            }
        } finally { GRAPH.writeLock().unlock(); }
    }
    public static void saveFinished(CompoundTag input) {
        GRAPH.writeLock().lock();
        try { Integer count=SAVING.get(input);if(count!=null) { if(count==1)SAVING.remove(input);else SAVING.put(input,count-1); } }
        finally { GRAPH.writeLock().unlock(); }
    }
    static boolean tryRecordGraph() { return !graphGap&&GRAPH.readLock().tryLock(); }
    static void releaseRecordGraph() { GRAPH.readLock().unlock(); }
    /** Caller holds the graph read lock through unlink. Standard NBT insertions cannot add a foreign record meanwhile. */
    static String recordGraphGap(RecoverySources owner,CompoundTag root,UUID subject) {
        if(GRAPH.getReadHoldCount()==0||graphGap)return "RECORD_GRAPH_CAPTURE_UNAVAILABLE";
        ArrayDeque<Tag> pending=new ArrayDeque<>();pending.add(root);
        Set<Tag> seen=Collections.newSetFromMap(new IdentityHashMap<>());
        synchronized(TAGS) {
            Origin origin=TAGS.get(root);if(origin==null||origin.owner!=owner||!origin.subject.equals(subject))return "RECORD_ORIGIN_CHANGED";
            while(!pending.isEmpty()) {
                Tag node=pending.removeFirst();if(!seen.add(node))continue;
                if(seen.size()>4096)return "RECORD_GRAPH_BUDGET";
                if(node instanceof CompoundTag compound) {
                    if(node.getClass()!=CompoundTag.class||!(node instanceof GraphNode graph)||!graph.pro$closedGraph())return "RECORD_GRAPH_BACKING_UNPROVEN_OR_EXPOSED";
                    if(SAVING.containsKey(compound))return "RECORD_SERIALIZATION_NOT_CLOSED";
                    if(AMBIGUOUS.contains(compound))return "RECORD_ORIGIN_AMBIGUOUS";
                    Origin child=TAGS.get(compound);
                    if(child!=null&&(child.owner!=owner||!child.subject.equals(subject)))return "FOREIGN_SUBJECT_IN_RECORD_SUBTREE";
                    for(String key:compound.getAllKeys())pending.addLast(compound.get(key));
                } else if(node instanceof ListTag list) {
                    if(node.getClass()!=ListTag.class||!(node instanceof GraphNode graph)||!graph.pro$closedGraph())return "RECORD_LIST_BACKING_UNPROVEN";
                    for(Tag child:list)pending.addLast(child);
                } else if(!SCALARS.contains(node.getClass()))return "OPAQUE_RECORD_TAG";
            }
        }
        return "";
    }
    static UUID subject(RecoverySources owner,CompoundTag tag) {
        synchronized(TAGS) {
            Origin origin=TAGS.get(tag);
            return !AMBIGUOUS.contains(tag)&&origin!=null&&origin.owner==owner?origin.subject:null;
        }
    }
    /** A real observed source can be refused before the factory or load consumes it.
     * A rejected load has produced no lineage: it must not relabel its existing receiver. */
    public static boolean refuseConsumption(CompoundTag tag,net.minecraft.world.level.Level level) {
        if(tag==null||!(level instanceof net.minecraft.server.level.ServerLevel serverLevel))return false;
        Origin origin;
        synchronized(TAGS) { origin=AMBIGUOUS.contains(tag)?null:TAGS.get(tag); }
        return origin!=null&&!origin.owner.closed&&origin.owner.runtime.server==serverLevel.getServer()
                &&origin.owner.runtime.terminalSource(origin.subject);
    }
    static void loadedOffThread(Entity entity,CompoundTag tag) {
        Origin origin;
        synchronized(TAGS) { origin=AMBIGUOUS.contains(tag)?null:TAGS.get(tag); }
        if(origin==null)return;
        if(!(((dev.ronova.pro.mixin.Access.EntityState)entity).pro$level() instanceof net.minecraft.server.level.ServerLevel level)
                ||level.getServer()!=origin.owner.runtime.server) {
            origin.owner.gap="SOURCE_LOAD_REALM_UNRESOLVED";origin.owner.epoch.incrementAndGet();return;
        }
        // Capture on the real loader thread; replaying this on the server loses its task parent.
        origin.owner.runtime.recoveryTasks.observed(entity,origin.subject);
        synchronized(origin.owner.loads) {
            if(origin.owner.closed)return;
            origin.owner.epoch.incrementAndGet();
            if(!origin.owner.loads.offer(new Load(entity,origin.subject,null)))origin.owner.gap="ASYNC_LOAD_CAPTURE_CAPACITY";
        }
    }
    void cloned(Entity clone,UUID subject,Class<?> holder) {
        produced(clone,subject,holder,"NATIVE_CLONE_BINDING_CAPACITY");
    }
    void produced(Entity clone,UUID subject,Class<?> holder,String overflow) {
        runtime.recoveryTasks.observed(clone,subject);
        epoch.incrementAndGet();
        if(!loads.offer(new Load(clone,subject,holder)))gap=overflow;
    }
    private void memorySource(Class<?> holder,UUID subject) {
        String code=code(holder);if(code==null) { gap="CLONE_HOLDER_LIVE_DEFINITION_UNAVAILABLE";return; }
        String locator="MEMORY|"+holder.getName()+"|"+code;
        try(var gate=registrationGate.enter()) {
            Source source=sources.get(locator);
            if(source==null) {
                if(sources.size()>=SOURCE_LIMIT) { gap="MEMORY_SOURCE_CAPACITY";return; }
                source=new Source(this,locator,code,null,holder);sources.put(locator,source);
            }
            if(source.holder!=holder) { gap="SAME_NAME_DIFFERENT_LOADER_MEMORY_SOURCE";return; }
            source.subjects.add(subject);source.gap="";epoch.incrementAndGet();
        }
    }
    /** Actual producer receiver, including a live SavedData or BlockEntity before its first save. */
    Source observedHolder(Object holder,UUID subject) {
        if(closed||holder==null)return null;
        for(Source source:sources.values())if(source.holder==holder) {
            if(source.subjects.add(subject))epoch.incrementAndGet();return source;
        }
        Class<?> type=holder instanceof Class<?> clazz?clazz:holder.getClass();
        String code=code(type);if(code==null)return null;
        try(var gate=registrationGate.enter()) {
            for(Source source:sources.values())if(source.holder==holder) {
                if(source.subjects.add(subject))epoch.incrementAndGet();return source;
            }
            if(sources.size()>=SOURCE_LIMIT) {gap="MEMORY_SOURCE_CAPACITY";return null;}
            String locator=holder instanceof Class<?>?"MEMORY|"+type.getName()+"|"+code:
                    "MEMORY_OBJECT|"+type.getName()+"|"+UUID.randomUUID();
            Source source=new Source(this,locator,code,null,holder);source.subjects.add(subject);source.gap="";
            sources.put(locator,source);epoch.incrementAndGet();return source;
        }
    }
    public static void savedData(SavedData holder,CompoundTag image,File file)throws IOException {
        SavedData parent=HOLDER.get();HOLDER.set(holder);
        try { NbtIo.writeCompressed(image,file); }
        finally { if(parent==null)HOLDER.remove();else HOLDER.set(parent); }
    }
    /** Observe the actual DimensionDataStorage cache replacement, including ordinary load completion. */
    public static Object cachePut(DimensionDataStorage manager,File directory,Map<Object,Object> cache,Object key,Object value) {
        ProRuntime runtime=ProRuntime.sourceRuntime(manager);
        if(runtime==null)return cache.put(key,value);
        RecoverySources owner=runtime.recoverySources;
        // Forge caches this exact singleton for a failed disk lookup and get() returns null for it.
        // Preserve the original put and invalidate any previous real holder, but do not invent a data source.
        if(value==net.minecraftforge.common.util.DummySavedData.DUMMY)
            return unknownCacheMutation(owner,manager,cache,key,value);
        if(!(key instanceof String name)||!(value instanceof SavedData holder)||directory==null||directory.getClass()!=File.class)
            return unknownCacheMutation(owner,manager,cache,key,value);
        if(cache.getClass()!=HashMap.class)return unknownCacheMutation(owner,manager,cache,key,value);
        if(name.isEmpty()||name.length()>512||name.indexOf('/')>=0||name.indexOf('\\')>=0||name.indexOf(':')>=0
                ||name.chars().anyMatch(Character::isISOControl)) {
            owner.gap="SAVED_DATA_KEY_UNSUPPORTED";return unknownCacheMutation(owner,manager,cache,key,value);
        }
        Path path=directory.toPath().toAbsolutePath().normalize().resolve(name+".dat");
        String code=code(holder.getClass());
        String locator="SAVED_DATA|"+holder.getClass().getName()+"|"+path;
        try(var storageGate=owner.registrationGate.enter()) {
            long generation=0;
            for(Source prior:owner.sources.values())if(Objects.equals(prior.path,path)) {
                generation=Math.max(generation,prior.generation);
                if(prior.holder!=holder) { prior.generation++;prior.gap="SOURCE_OWNER_REPLACED"; }
            }
            owner.epoch.incrementAndGet();
            Object previous=cache.put(key,value); // Exactly one original mutation; never retried by the observer.
            Source current=owner.sources.get(locator);
            if(current!=null&&current.holder==holder) { current.cache=cache;current.cacheKey=name;current.manager=manager;return previous; }
            if(code==null) { owner.gap="SOURCE_CODE_IDENTITY_UNAVAILABLE";return previous; }
            if(current==null&&owner.sources.size()>=SOURCE_LIMIT) { owner.gap="SOURCE_CAPACITY";return previous; }
            Source registered=new Source(owner,locator,code,path,holder);
            registered.generation=Math.max(generation+1,owner.generationFloors.getOrDefault(locator,0L));
            registered.cache=cache;registered.cacheKey=name;registered.manager=manager;
            owner.sources.put(locator,registered);
            return previous;
        }
    }
    private static Object unknownCacheMutation(RecoverySources owner,DimensionDataStorage manager,Map<Object,Object> cache,Object key,Object value) {
        try(var storageGate=owner.registrationGate.enter()) {
            for(Source source:owner.sources.values())if((source.manager==manager||source.cache==cache)&&source.cacheKey!=null&&source.cacheKey.equals(key)) {
                source.generation++;source.gap="REGISTERED_CACHE_ENTRY_REPLACED_OR_REMOVED";
            }
            owner.epoch.incrementAndGet();
            if(cache.getClass()==HashMap.class)return cache.put(key,value);
        }
        // Never execute an opaque map callback while holding a production commit gate.
        return cache.put(key,value);
    }
    /** A cache hit at ordinary get() is sufficient to register a live holder for read-only restart queries. */
    public static void cacheSeen(DimensionDataStorage manager,File directory,Map<String,SavedData> cache,String name,SavedData holder) {
        if(holder==null||holder==net.minecraftforge.common.util.DummySavedData.DUMMY||cache.getClass()!=HashMap.class||cache.get(name)!=holder)return;
        ProRuntime runtime=ProRuntime.sourceRuntime(manager);
        if(runtime==null||directory==null||directory.getClass()!=File.class||name==null||name.isEmpty()
                ||name.length()>512||name.indexOf('/')>=0||name.indexOf('\\')>=0||name.indexOf(':')>=0
                ||name.chars().anyMatch(Character::isISOControl))return;
        RecoverySources owner=runtime.recoverySources;
        Path path=directory.toPath().toAbsolutePath().normalize().resolve(name+".dat");
        String code=code(holder.getClass());if(code==null) { owner.gap="SOURCE_CODE_IDENTITY_UNAVAILABLE";return; }
        String locator="SAVED_DATA|"+holder.getClass().getName()+"|"+path;
        try(var storageGate=owner.registrationGate.enter()) {
            if(owner.closed||cache.get(name)!=holder)return;
            Source prior=owner.sources.get(locator);
            if(prior!=null&&prior.holder==holder&&prior.code.equals(code)) {
                prior.cache=cache;prior.cacheKey=name;prior.manager=manager;return;
            }
            if(prior==null&&owner.sources.size()>=SOURCE_LIMIT) { owner.gap="SOURCE_CAPACITY";return; }
            long generation=Math.max(prior==null?0:prior.generation+1,owner.generationFloors.getOrDefault(locator,0L));
            if(prior!=null) { prior.generation++;prior.gap="SOURCE_OWNER_REPLACED"; }
            Source registered=new Source(owner,locator,code,path,holder);registered.generation=generation;
            registered.cache=cache;registered.cacheKey=name;registered.manager=manager;
            owner.sources.put(locator,registered);owner.epoch.incrementAndGet();
        }
    }
    /** Registers only a source named by an existing intent, after its real static reader ran. */
    public static void readFile(File file) {
        if(file==null||file.getClass()!=File.class)return;
        Class<?> caller=caller();if(caller==null)return;
        String code=code(caller);if(code==null)return;
        Path path=file.toPath().toAbsolutePath().normalize();
        String locator="STATIC|"+caller.getName()+"|"+path;
        for(ProRuntime runtime:ProRuntime.sourceRuntimes()) {
            RecoverySources owner=runtime.recoverySources;
            if(!owner.expected.contains(locator+"\n"+code))continue;
            try(var storageGate=owner.registrationGate.enter()) {
                if(owner.closed||owner.sources.containsKey(locator))continue;
                if(owner.sources.size()>=SOURCE_LIMIT) { owner.gap="SOURCE_CAPACITY";continue; }
                owner.sources.put(locator,new Source(owner,locator,code,path,caller));owner.epoch.incrementAndGet();
            }
        }
    }
    /** Called from the real FileOutputStream construction, before a truncate can happen. */
    public static FileOutputStream open(CompoundTag image,File file)throws FileNotFoundException {
        Capture capture=null;
        try { capture=begin(image,file); }
        catch(RuntimeException|LinkageError ignored) { markRelatedGap(image,"SAVE_OBSERVER_UNAVAILABLE"); }
        try {
            FileOutputStream stream=new FileOutputStream(file);
            if(capture!=null) {
                capture.stream=stream;
                try { capture.identity=StorageNative.identity(stream.getFD(),capture.source.path.toString()); }
                catch(IOException|RuntimeException|LinkageError unavailable) { capture.error="OUTPUT_HANDLE_IDENTITY_UNAVAILABLE"; }
                OPEN.put(stream,capture);
            }
            return stream;
        } catch(FileNotFoundException|RuntimeException|Error failure) {
            if(capture!=null)capture.finish(false);
            throw failure;
        }
    }
    public static void write(CompoundTag image,OutputStream stream)throws IOException {
        Capture capture=stream instanceof FileOutputStream file?OPEN.remove(file):null;
        if(capture==null) { NbtIo.writeCompressed(image,stream);return; }
        boolean success=false;
        int depth=CAPTURE_DEPTH.get();CAPTURE_DEPTH.set(depth+1);
        try { NbtIo.writeCompressed(image,capture.output());success=true; }
        finally { if(depth==0)CAPTURE_DEPTH.remove();else CAPTURE_DEPTH.set(depth);capture.finish(success); }
    }
    public static void outputObserved(CompoundTag image) {
        if(CAPTURE_DEPTH.get()==0)markRelatedGap(image,"UNREGISTERED_OUTPUT_STREAM_OR_ENCODING");
    }
    private static Capture begin(CompoundTag image,File file) {
        Map<CompoundTag,UUID> identities=new IdentityHashMap<>();RecoverySources owner=null;
        synchronized(TAGS) {
            // Only actual identities in this image matter; no class/name/UUID similarity matching.
            try {
                var all=new IdentityHashMap<CompoundTag,UUID>();TAGS.forEach((tag,origin)->all.put(tag,origin.subject));
                for(var selection:RecoveryImage.locate(image,all)) {
                    CompoundTag tag=(CompoundTag)RecoveryImage.resolve(image,selection.path());
                    Origin origin=TAGS.get(tag);
                    if(owner!=null&&owner!=origin.owner) { owner.gap="CROSS_REALM_SAVE";origin.owner.gap="CROSS_REALM_SAVE";return null; }
                    owner=origin.owner;identities.put(tag,origin.subject);
                }
            } catch(IOException failure) { markRelatedGap(image,failure.getMessage());return null; }
        }
        if(owner==null&&file!=null&&file.getClass()==File.class) {
            Path knownPath=file.toPath().toAbsolutePath().normalize();
            for(ProRuntime candidate:ProRuntime.sourceRuntimes())for(Source source:candidate.recoverySources.registered())
                if(Objects.equals(source.path,knownPath)) {
                    if(owner!=null&&owner!=source.owner) { owner.gap="CROSS_REALM_REGISTERED_PATH";source.owner.gap="CROSS_REALM_REGISTERED_PATH";return null; }
                    owner=source.owner;
                }
        }
        if(owner==null||owner.closed)return null;
        if(file.getClass()!=File.class) { owner.gap="OPAQUE_FILE_PATH";return null; }
        Path path=file.toPath().toAbsolutePath().normalize();
        SavedData holder=HOLDER.get();
        Class<?> caller=holder==null?caller():holder.getClass();
        if(caller==null) { owner.gap="STATIC_SAVE_CALLER_UNOBSERVED";return null; }
        String code=code(caller);
        if(code==null) { owner.gap="SOURCE_CODE_IDENTITY_UNAVAILABLE";return null; }
        // Static caller identity is recorded, but memory-holder disposal still needs its registered adapter.
        Object holderIdentity=holder==null?caller:holder;
        String locator=(holder==null?"STATIC|":"SAVED_DATA|")+caller.getName()+"|"+path;
        Source source=owner.sources.get(locator);
        if(source==null) {
            if(owner.sources.size()>=SOURCE_LIMIT) { owner.gap="SOURCE_CAPACITY";return null; }
            Source fresh=new Source(owner,locator,code,path,holderIdentity);
            Source prior=owner.sources.putIfAbsent(locator,fresh);source=prior==null?fresh:prior;
        }
        final List<RecoveryImage.Selection> selections;
        final String semantic;
        try { selections=RecoveryImage.locate(image,identities);semantic=RecoveryImage.semanticHash(image); }
        catch(IOException failure) { source.gap=failure.getMessage();owner.epoch.incrementAndGet();return null; }
        try(var storageGate=source.gate.enter()) {
            owner.epoch.incrementAndGet();source.generation=Math.max(source.generation,owner.generationFloors.getOrDefault(source.locator,0L))+1;source.writers++;
            for(RecoveryImage.Selection selection:selections)source.subjects.add(selection.subject());
            if(source.holder!=holderIdentity||!source.code.equals(code))source.gap="SOURCE_OWNER_REPLACED";
            else source.gap="SAVE_IN_PROGRESS";
            for(Source other:owner.sources.values())if(other!=source&&Objects.equals(other.path,path)) {
                other.generation++;other.gap="PATH_WRITTEN_BY_OTHER_SOURCE";
            }
            return new Capture(source,source.generation,selections,semantic);
        }
    }
    private static Class<?> caller() {
        return StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE).walk(frames->frames
            .filter(frame->{ Class<?> c=frame.getDeclaringClass();return c!=RecoverySources.class&&c!=Capture.class&&c!=NbtIo.class
                &&!c.getName().startsWith("dev.ronova.pro.mixin.")&&!c.getName().startsWith("java."); })
            .findFirst().map(frame->{
                try {
                    Class<?> type=frame.getDeclaringClass();
                    var method=type.getDeclaredMethod(frame.getMethodName(),frame.getMethodType().parameterArray());
                    return java.lang.reflect.Modifier.isStatic(method.getModifiers())?type:null;
                } catch(ReflectiveOperationException|RuntimeException unavailable) { return null; }
            }).orElse(null));
    }
    private static String code(Class<?> type) {
        try(var input=type.getResourceAsStream("/"+type.getName().replace('.','/')+".class")) {
            if(input==null)return null;
            byte[] bytes=input.readNBytes(RecoveryImage.MAX_BYTES+1);
            if(bytes.length>RecoveryImage.MAX_BYTES)return null;
            String live=definition(type,true);if(live==null)return null;
            String image=live.substring(0,live.indexOf(':'));
            return RecoveryImage.hash(("source-definition/canonical-v2\n"+RecoveryImage.hash(bytes)+"\n"+image).getBytes(java.nio.charset.StandardCharsets.UTF_8));
        } catch(IOException|RuntimeException unavailable) { return null; }
    }
    private static String definition(Class<?> type,boolean watch) {
        try {
            Class<?> agent=Class.forName("dev.ronova.pro.agent.RecoveryAgent",false,ClassLoader.getSystemClassLoader());
            return (String)agent.getMethod(watch?"watchDefinition":"definition",Class.class).invoke(null,type);
        } catch(ReflectiveOperationException|RuntimeException|LinkageError unavailable) { return null; }
    }
    private static void markRelatedGap(CompoundTag image,String reason) {
        Set<Tag> seen=Collections.newSetFromMap(new IdentityHashMap<>());ArrayDeque<Tag> queue=new ArrayDeque<>();queue.add(image);
        synchronized(TAGS) {
            while(!queue.isEmpty()&&seen.size()<RecoveryImage.MAX_NODES) {
                Tag node=queue.removeFirst();if(!seen.add(node))continue;
                Origin origin=TAGS.get(node);
                if(origin!=null) { origin.owner.gap=reason;origin.owner.epoch.incrementAndGet(); }
                if(node.getClass()==CompoundTag.class) {
                    CompoundTag map=(CompoundTag)node;for(String key:map.getAllKeys())queue.add(map.get(key));
                } else if(node.getClass()==ListTag.class)for(Tag child:(ListTag)node)queue.add(child);
            }
        }
    }
    private static final class Capture {
        final Source source;final long generation;final List<RecoveryImage.Selection> selections;final String semantic;
        final ByteArrayOutputStream bytes=new ByteArrayOutputStream();
        FileOutputStream stream;String identity,error="";boolean closed,finished;
        Capture(Source source,long generation,List<RecoveryImage.Selection> selections,String semantic) {
            this.source=source;this.generation=generation;this.selections=selections;this.semantic=semantic;
        }
        OutputStream output() {
            return new FilterOutputStream(stream) {
                private void retain(byte[] data,int off,int len) {
                    if(!error.isEmpty())return;
                    if(len<0||bytes.size()>RecoveryImage.MAX_BYTES-len) { error="ENCODED_CAPTURE_LIMIT";return; }
                    bytes.write(data,off,len);
                }
                @Override public void write(int value)throws IOException { out.write(value);retain(new byte[]{(byte)value},0,1); }
                @Override public void write(byte[] data,int off,int len)throws IOException { out.write(data,off,len);retain(data,off,len); }
                @Override public void close()throws IOException {
                    try {
                        String end=StorageNative.identity(stream.getFD(),source.path.toString());
                        if(identity==null||!identity.equals(end))error="OUTPUT_HANDLE_IDENTITY_CHANGED_OR_UNAVAILABLE";
                    } catch(IOException|RuntimeException|LinkageError unavailable) { error="OUTPUT_HANDLE_IDENTITY_UNAVAILABLE"; }
                    finally {
                        try { out.close();closed=true; }
                        catch(IOException failure) { error="OUTPUT_HANDLE_CLOSE_UNCONFIRMED";throw failure; }
                    }
                }
            };
        }
        void finish(boolean success) {
            if(finished)return;finished=true;
            Snapshot next=null;String failureReason="";
            // Decode, hash and compare the image without holding any source/qualification commit gate.
            try {
                if(!success)throw new IOException("ORIGINAL_SAVE_FAILED");
                if(!closed)throw new IOException("OUTPUT_CLOSE_NOT_OBSERVED");
                if(identity==null)throw new IOException(StorageNative.reason());
                if(!error.isEmpty())throw new IOException(error);
                byte[] encoded=bytes.toByteArray();
                if(!semantic.equals(RecoveryImage.semanticHash(RecoveryImage.decode(encoded))))throw new IOException("ENCODING_OR_IMAGE_CHANGED_DURING_SAVE");
                next=new Snapshot(source,generation,identity,encoded,selections);
            } catch(IOException|RuntimeException failure) { failureReason=String.valueOf(failure.getMessage()); }
            Snapshot retired=null;
            try(var storageGate=source.gate.enter()) {
                source.writers--;
                try {
                    if(!failureReason.isEmpty())throw new IOException(failureReason);
                    if(next==null)throw new IOException("SAVE_IMAGE_CAPTURE_UNAVAILABLE");
                    if(generation!=source.generation||source.writers!=0)throw new IOException("OVERLAPPING_SAVE_GENERATION");
                    if(source.gap.equals("SOURCE_OWNER_REPLACED"))throw new IOException(source.gap);
                    retired=source.snapshot;
                    source.snapshot=next;next=null;source.gap="";
                } catch(IOException|RuntimeException failure) { source.gap=failure.getMessage(); }
                finally {
                    if(!closed&&stream!=null) {
                        source.owner.gap="OUTPUT_RESOURCE_CLOSE_UNCONFIRMED";source.owner.pendingCloses.add(stream);
                    }
                    bytes.reset();source.owner.epoch.incrementAndGet();
                }
            }
            if(retired!=null)retired.release();if(next!=null)next.release();
        }
    }
    void beginStop() {
        SourceMaps.close(this);
        synchronized(loads) {
            try(var storageGate=registrationGate.enter()) { closed=true;epoch.incrementAndGet(); }
            Load load;
            while((load=loads.poll())!=null)runtime.linkLoaded(load.entity,load.subject);
        }
    }
    @Override public void close()throws IOException {
        closed=true;
        retireOutputHandles();
        for(Source source:sources.values())try(var storageGate=source.gate.enter()) {
            if(source.writers!=0)throw new IOException("ORIGINAL_SAVE_STREAM_EXIT_PENDING");
        }
        GRAPH.writeLock().lock();
        try {
            synchronized(TAGS) {
                for(var entry:TAGS.entrySet())if(entry.getValue().owner==this&&SAVING.containsKey(entry.getKey()))
                    throw new IOException("SOURCE_SERIALIZER_EXIT_UNOBSERVED");
                var iterator=TAGS.entrySet().iterator();
                while(iterator.hasNext()) { var entry=iterator.next();if(entry.getValue().owner==this) { AMBIGUOUS.remove(entry.getKey());iterator.remove(); } }
            }
        } finally { GRAPH.writeLock().unlock(); }
        for(Source source:sources.values())try(var storageGate=source.gate.enter()) {
            if(source.snapshot!=null)source.snapshot.release();source.gap="SOURCE_UNLOADED";
        }
        // Do not discard sources/handles whose release has not been observed.
        if(!pendingCloses.isEmpty())throw new IOException("OUTPUT_RESOURCES_RETAINED_FOR_RETIREMENT");
    }
}
