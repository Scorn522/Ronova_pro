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
    public interface CacheOwner {
        Map<String,SavedData> pro$cacheView();
        File pro$dataFolder();
    }
    public interface GraphNode { boolean pro$closedGraph(); }
    private static final Map<CompoundTag,Origin> TAGS=Collections.synchronizedMap(new WeakIdentityMap<>());
    private static final Map<FileOutputStream,Capture> OPEN=Collections.synchronizedMap(new IdentityHashMap<>());
    private record LoadedHolder(Set<UUID> subjects,Set<UUID> unlocated){}
    private static final Map<SavedData,Map<RecoverySources,LoadedHolder>> LOADED_HOLDERS=Collections.synchronizedMap(new WeakIdentityMap<>());
    private static final Map<CompoundTag,Map<RecoverySources,Map<Object,UUID>>> MIGRATIONS=Collections.synchronizedMap(new WeakIdentityMap<>());
    private static final java.util.concurrent.locks.ReentrantReadWriteLock GRAPH=new java.util.concurrent.locks.ReentrantReadWriteLock();
    private static final Map<CompoundTag,Integer> SAVING=new IdentityHashMap<>();
    private static final Set<CompoundTag> AMBIGUOUS=Collections.newSetFromMap(new WeakIdentityMap<>());
    private static final Set<Class<?>> SCALARS=Set.of(EndTag.class,ByteTag.class,ShortTag.class,IntTag.class,LongTag.class,
            FloatTag.class,DoubleTag.class,ByteArrayTag.class,StringTag.class,IntArrayTag.class,LongArrayTag.class);
    private static volatile boolean graphGap;
    private static final ThreadLocal<SavedData> HOLDER=new ThreadLocal<>();
    private static final ThreadLocal<Integer> CAPTURE_DEPTH=ThreadLocal.withInitial(()->0);
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
    private final Map<UUID,Integer> externalFieldCursors=new HashMap<>();
    private final Map<UUID,Integer> externalHeapCursors=new HashMap<>();
    private final Map<UUID,long[]> externalMemoryCursors=new HashMap<>();
    private final Map<Source,String> holderDiscovery=new WeakIdentityMap<>();
    private final Map<Source,HolderWalk> holderWalks=new IdentityHashMap<>();
    private final Map<Source,Map<UUID,GroupWalk>> groupWalks=Collections.synchronizedMap(new IdentityHashMap<>());
    private final Map<UUID,Long> groupWalkCursors=new HashMap<>();
    private final java.util.concurrent.ConcurrentLinkedQueue<LoadRoot> groupRoots=new java.util.concurrent.ConcurrentLinkedQueue<>();
    private record LoadRoot(Object value,UUID subject) { }
    private volatile LoadRoot pendingGroupRoot;
    private static final class HolderWalk {
        final Set<Object> seen=Collections.newSetFromMap(new IdentityHashMap<>());
        final ArrayDeque<Object> pending=new ArrayDeque<>();
        final ArrayDeque<HolderCursor> paused=new ArrayDeque<>();
        HolderCursor current;
        Object processing;
        final long revision;
        String gap="";
        HolderWalk(Object root,long revision) {pending.add(root);this.revision=revision;}
    }
    private static final class HolderCursor {
        final Object value;
        List<java.lang.reflect.Field> fields=List.of();List<String> keys;
        int position;Object stamp;long revision=-1;
        SourceMaps.Cursor map;
        HolderCursor(Object value){this.value=value;}
    }
    private static final class GroupCursor {
        final Object value;
        List<java.lang.reflect.Field> fields=List.of();int field,element;
        Object listStamp;long listRevision=-1,arrayRevision=-1;
        SourceMaps.Cursor mapCursor;
        GroupCursor(Object value){this.value=value;if(value instanceof List<?>)element=-1;}
    }
    private static final class GroupWalk {
        final Source source;final UUID subject;
        final Set<Object> seen=Collections.newSetFromMap(new IdentityHashMap<>());
        final ArrayDeque<Object> pending=new ArrayDeque<>();
        GroupCursor current;
        final ArrayDeque<GroupCursor> paused=new ArrayDeque<>();
        String gap="";boolean complete;
        final boolean rootObserved;
        GroupWalk(Source source,UUID subject) {this.source=source;this.subject=subject;Object root=source.holder();rootObserved=root!=null;if(root!=null)pending.add(root);else gap="HOLDER_RELEASE_UNOBSERVED";}
    }
    private final java.util.concurrent.ConcurrentLinkedQueue<Load> loads=new java.util.concurrent.ConcurrentLinkedQueue<>();
    private static final class Load {
        final Entity entity;final UUID subject;final Class<?> holder;
        volatile String reason="";
        Load(Entity entity,UUID subject,Class<?> holder){this.entity=entity;this.subject=subject;this.holder=holder;}
    }
    private volatile Load pendingLoad;
    private volatile String pendingLoadReason="";
    private final List<FileOutputStream> pendingCloses=Collections.synchronizedList(new ArrayList<>());
    private volatile String gap="";
    private volatile boolean closed;
    private record Origin(RecoverySources owner,UUID subject,Object record) { }
    static final class Source {
        final RecoverySources owner;
        final String locator,code;
        final Path path;
        /** Identity of the registered holder, so a holder with no map wrapper can still be checked. */
        final java.lang.ref.WeakReference<Object> holderIdentity;
        private Object resource,nestedRetained,nestedCarrier;
        volatile String liveDefinition;
        final ProRuntime.CommitGate gate;
        final Set<UUID> subjects=ConcurrentHashMap.newKeySet();
        final Set<UUID> unlocatedReads=ConcurrentHashMap.newKeySet();
        private java.lang.ref.WeakReference<Map<?,?>> cache=new java.lang.ref.WeakReference<>(null);
        String cacheKey;
        private java.lang.ref.WeakReference<DimensionDataStorage> manager=new java.lang.ref.WeakReference<>(null);
        volatile boolean groupDisposed;
        Source nestedParent;java.lang.reflect.Field nestedField;String nestedKind;Object nestedKey;
        boolean nestedAbsent,nestedQuery;
        final Set<String> nestedRegistrationGaps=ConcurrentHashMap.newKeySet();
        final Set<String> clearedFields=ConcurrentHashMap.newKeySet();
        final AtomicLong mutations=new AtomicLong();
        volatile long generation;
        volatile int writers;
        volatile Snapshot snapshot;
        volatile String gap="SAVE_NOT_OBSERVED";
        Source(RecoverySources owner,String locator,String code,Path path,Object holder) {
            this.owner=owner;this.locator=locator;this.code=code;this.path=path;
            this.holderIdentity=new java.lang.ref.WeakReference<>(holder);
            if(holder instanceof Closeable||holder instanceof java.util.concurrent.ExecutorService||holder instanceof Timer||holder instanceof Process||holder instanceof Thread)resource=holder;
            liveDefinition=holder==null?null:definition(holder instanceof Class<?> type?type:holder.getClass(),false);
            gate=owner.registrationGate;
        }
        Object holder(){return resource!=null?resource:nestedRetained!=null?nestedRetained:holderIdentity.get();}
        boolean disposed(){return groupDisposed||nestedAbsent;}
        Map<?,?> cache(){return cache.get();}
        DimensionDataStorage manager(){return manager.get();}
        void cached(Map<?,?> actual,String key,DimensionDataStorage store){cache=new java.lang.ref.WeakReference<>(actual);cacheKey=key;manager=new java.lang.ref.WeakReference<>(store);}
        boolean cleared(java.lang.reflect.Field field){return clearedFields.contains(field.getDeclaringClass().getName()+"#"+field.getName()+":"+field.getType().descriptorString());}
        void cleared(java.lang.reflect.Field field,Object actual){if(actual==holder())clearedFields.add(field.getDeclaringClass().getName()+"#"+field.getName()+":"+field.getType().descriptorString());}
        boolean current(Snapshot image) { return image!=null&&writers==0&&snapshot==image&&generation==image.generation&&gap.isEmpty()&&owner.gap.isEmpty()&&owner.current(this); }
    }
    static final class Snapshot {
        final Source source;
        final long generation;
        final String identity,hash;
        final List<RecoveryImage.Selection> selections;
        private RecoveryImage.Encoded encoded;
        Snapshot(Source source,long generation,String identity,RecoveryImage.Encoded encoded,List<RecoveryImage.Selection> selections)throws IOException {
            this.source=source;this.generation=generation;this.identity=identity;
            hash=encoded.hash();this.selections=List.copyOf(selections);
            this.encoded=encoded.retain();
        }
        synchronized RecoveryImage.Encoded bytes()throws IOException { if(encoded==null)throw new IOException("SNAPSHOT_RETIRED");return encoded.retain(); }
        synchronized void release() { if(encoded!=null)encoded.close();encoded=null; }
    }
    RecoverySources(ProRuntime runtime) { this.runtime=runtime; }
    UUID runtimeSession() { return runtime.session; }
    ProRuntime runtimeOwner() { return runtime; }
    boolean liveRegistration(Source source) {
        if(closed||source.nestedQuery||sources.get(source.locator)!=source)return false;
        Object holder=source.holder();Map<?,?> cache=source.cache();DimensionDataStorage manager=source.manager();
        if(holder==null)return source.disposed();
        if(cache==null)return source.cacheKey==null;
        return manager instanceof CacheOwner owner&&owner.pro$cacheView()==cache&&cache.get(source.cacheKey)==holder;
    }
    long epoch() { return epoch.get(); }
    void mapChanged(Source source) {source.mutations.incrementAndGet();epoch.incrementAndGet();}
    String dependencies(UUID subject) {
        Set<UUID> targets=runtime.groupTargets(subject);
        StringBuilder key=new StringBuilder(gap).append(':').append(graphGap).append(':').append(new TreeSet<>(targets));
        for(Source source:registered().stream().sorted(Comparator.comparing(s->s.locator)).toList())if(!Collections.disjoint(source.subjects,targets)) {
            Snapshot image=source.snapshot;
            key.append('|').append(source.locator).append(':').append(source.code).append(':').append(source.generation)
                .append(':').append(source.mutations.get()).append(':').append(source.writers).append(':').append(source.gap)
                .append(':').append(new TreeSet<>(source.unlocatedReads))
                .append(':').append(image==null?"none":image.identity+":"+image.hash);
        }
        return key.toString();
    }
    String gap() { return gap; }
    Collection<Source> registered() {
        for(Source source:sources.values())source.subjects.addAll(expectedSubjects.getOrDefault(source.locator,Set.of()));
        return sources.values().stream().filter(source->!source.nestedQuery).toList();
    }
    void expect(String locator,String code) { expected.add(locator+"\n"+code); }
    void expect(String locator,String code,UUID subject,long generation) {
        expect(locator,code);expectedSubjects.computeIfAbsent(locator,key->ConcurrentHashMap.newKeySet()).add(subject);
        generationFloors.merge(locator,generation,Math::max);
        if(locator.startsWith("NESTED|")){
            String[] parts=locator.split("\\|",7);
            if(parts.length==7)expect(dev.ronova.pro.persistence.JournalLineage.decodeText(parts[1]),dev.ronova.pro.persistence.JournalLineage.decodeText(parts[2]),subject,0);
        }
    }
    List<String> remaining(UUID subject) {
        List<String> remaining=new ArrayList<>();
        String mapObserver=SourceMaps.observerGap(this,subject);if(!mapObserver.isEmpty())remaining.add(mapObserver);
        if(!gap.isEmpty())remaining.add("SOURCE:"+gap);
        Load queued=loads.stream().filter(load->load.subject==null||runtime.groupTargets(subject).contains(load.subject)).findFirst().orElse(null);
        if(queued!=null)remaining.add("SOURCE_LOAD_BINDING_PENDING"+(queued.reason.isEmpty()?"":":"+queued.reason));
        Load waiting=pendingLoad;
        if(waiting!=null&&(waiting.subject==null||runtime.groupTargets(subject).contains(waiting.subject)))remaining.add("SOURCE_LOAD_BINDING_PENDING:"+pendingLoadReason);
        LoadRoot root=pendingGroupRoot;
        if((root!=null&&runtime.groupTargets(subject).contains(root.subject))
                ||groupRoots.stream().anyMatch(item->runtime.groupTargets(subject).contains(item.subject)))remaining.add("GROUP_ROOT_BINDING_PENDING");
        for(Source source:registered()) {
            if(Collections.disjoint(source.subjects,runtime.groupTargets(subject)))continue;
            String discovery=holderDiscovery.get(source);
            // A cached holder is already a possible source before its first disk save.
            // No recorded output is not evidence that it contains no recovery records.
            if(discovery==null)remaining.add("HOLDER_DISCOVERY_PENDING:"+source.locator);
            else if(!discovery.isEmpty())remaining.add("HOLDER_DISCOVERY:"+source.locator+":"+discovery);
        }
        synchronized(pendingCloses) { if(!pendingCloses.isEmpty())remaining.add("SOURCE_STREAM_RESOURCE_PENDING"); }
        synchronized(groupWalks) {for(var groups:groupWalks.values())for(GroupWalk walk:groups.values())if(runtime.groupTargets(subject).contains(walk.subject)&&(!walk.complete||!walk.gap.isEmpty()))
            remaining.add("GROUP_SOURCE:"+walk.source.locator+":"+(walk.gap.isEmpty()?"DISPOSITION_PENDING":walk.gap));}
        for(String entry:expected) {
            int split=entry.lastIndexOf('\n');String locator=entry.substring(0,split),expectedCode=entry.substring(split+1);
            if(Collections.disjoint(expectedSubjects.getOrDefault(locator,Set.of()),runtime.groupTargets(subject)))continue;
            Source source=sources.get(locator);
            if(source==null||!expectedCode.equals(source.code)||!bound(source))
                remaining.add("SOURCE_NOT_LOADED:"+locator+":"+restartDiagnostics.getOrDefault(locator,"WAIT_REGISTERED_READER"));
        }
        for(Source source:registered())if(!Collections.disjoint(source.subjects,runtime.groupTargets(subject))) {
            for(String field:source.nestedRegistrationGaps)remaining.add("NESTED_SOURCE_REGISTRATION_PENDING:"+source.locator+":"+field);
            if(!Collections.disjoint(source.unlocatedReads,runtime.groupTargets(subject)))remaining.add("SOURCE_MIGRATION_RECORD_LOCATION_UNRESOLVED:"+source.locator);
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
            Object holder=source.holder();Map<?,?> cache=source.cache();DimensionDataStorage manager=source.manager();
            if(source.nestedParent!=null){
                if(!current(source.nestedParent))return "NESTED_PARENT_BINDING_CHANGED";
                if(source.nestedQuery)return "";
                if(source.nestedAbsent)return nestedValue(source.nestedParent,source.nestedField,source.nestedKind,source.nestedKey)==null?"":"NESTED_LOCATION_REPOPULATED";
            }
            if(holder==null)return source.groupDisposed?"":"HOLDER_RELEASE_UNOBSERVED";
            String current=definition(holder instanceof Class<?> type?type:holder.getClass(),false);
            if(source.liveDefinition==null||current==null)return "DEFINITION_UNOBSERVED";
            if(!source.liveDefinition.equals(current))return "DEFINITION_CHANGED";
            if(holder instanceof Class<?>)return "";
            if(cache==null)return source.cacheKey==null?"":"CACHE_RELEASE_UNOBSERVED";
            if(cache.getClass()!=HashMap.class)return "CACHE_TYPE_CHANGED";
            if(!(manager instanceof CacheOwner owner)||owner.pro$cacheView()!=cache)return "CACHE_OWNER_CHANGED";
            return cache.get(source.cacheKey)==holder?"":"CACHE_ENTRY_CHANGED";
        }catch(ReflectiveOperationException unavailable){return "NESTED_LOCATION_UNAVAILABLE";}
    }
    void maintainResources() {
        List<String> pending=new ArrayList<>(expected);
        for(int i=0;i<Math.min(4,pending.size());i++) {
            String entry=pending.get((int)Math.floorMod(restartCursor++,pending.size()));
            int split=entry.lastIndexOf('\n');String locator=entry.substring(0,split),expectedCode=entry.substring(split+1);
            Source observed=sources.get(locator);
            if(observed!=null&&expectedCode.equals(observed.code)&&bound(observed)) { restartDiagnostics.remove(locator);continue; }
            if(locator.startsWith("NESTED|")){restoreNestedSource(locator,expectedCode,observed);continue;}
            if(locator.startsWith("SAVED_DATA|")) { restoreCachedSource(locator,expectedCode);continue; }
            if(observed!=null) { restartDiagnostics.put(locator,expectedCode.equals(observed.code)?bindingProblem(observed):"CODE_IDENTITY_CHANGED:expected="+expectedCode+":actual="+observed.code);continue; }
            if(!locator.startsWith("STATIC|")&&!locator.startsWith("MEMORY|"))continue;
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
        drainLoads(128);
        retireOutputHandles();
        discoverHolders();
        drainGroupRoots(128);
    }
    /** Rebind an existing intent only to the holder in the current real dimension cache. */
    private void restoreCachedSource(String locator,String expectedCode) {
        if(closed||!runtime.server.isSameThread())return;
        String[] parts=locator.split("\\|",3);
        if(parts.length!=3)return;
        Path expectedPath;
        try { expectedPath=Path.of(parts[2]); }
        catch(RuntimeException unavailable) { restartDiagnostics.put(locator,"SAVED_DATA_PATH_UNAVAILABLE");return; }
        String reason="SAVED_DATA_CACHE_NOT_LOADED";
        for(var level:runtime.server.getAllLevels()) {
            DimensionDataStorage manager=level.getDataStorage();
            if(!(manager instanceof CacheOwner actual))continue;
            File directory=actual.pro$dataFolder();Map<String,SavedData> cache=actual.pro$cacheView();
            if(directory==null||directory.getClass()!=File.class||cache==null||cache.getClass()!=HashMap.class)continue;
            Path folder=directory.toPath().toAbsolutePath().normalize();
            if(!folder.equals(expectedPath.getParent()))continue;
            String file=expectedPath.getFileName().toString();
            if(!file.endsWith(".dat"))continue;
            String name=file.substring(0,file.length()-4);
            if(!savedDataKey(name)||!folder.resolve(name+".dat").equals(expectedPath))continue;
            try(var gate=registrationGate.enter()) {
                if(closed||actual.pro$cacheView()!=cache||actual.pro$dataFolder()!=directory)continue;
                SavedData holder=cache.get(name);
                if(holder==null||holder==net.minecraftforge.common.util.DummySavedData.DUMMY)continue;
                if(!holder.getClass().getName().equals(parts[1])) { reason="SAVED_DATA_HOLDER_CLASS_CHANGED";continue; }
                String currentCode=code(holder.getClass());
                if(!expectedCode.equals(currentCode)) { reason="CODE_IDENTITY_CHANGED:expected="+expectedCode+":actual="+currentCode;continue; }
                cacheSeen(manager,directory,cache,name,holder);
                Source source=sources.get(locator);
                if(source!=null&&expectedCode.equals(source.code)&&source.holder()==holder&&bound(source)) {
                    source.subjects.addAll(expectedSubjects.getOrDefault(locator,Set.of()));
                    restartDiagnostics.remove(locator);return;
                }
                reason="SAVED_DATA_CACHE_BINDING_PENDING";
            }
        }
        restartDiagnostics.put(locator,reason);
    }
    private static boolean savedDataKey(String name) {
        return name!=null&&!name.isEmpty()&&name.length()<=512&&name.indexOf('/')<0&&name.indexOf('\\')<0
                &&name.indexOf(':')<0&&name.chars().noneMatch(Character::isISOControl);
    }
    private void drainGroupRoots(int budget) {
        LoadRoot firstDeferred=null;
        while(budget-->0) {
            LoadRoot root=pendingGroupRoot;
            if(root==null)pendingGroupRoot=root=groupRoots.poll();
            if(root==null)break;
            if(root==firstDeferred)break; // The deferred part has circled back; retry it next round.
            Source source=observedHolder(root.value,root.subject);
            if(source!=null)registerGroupWalk(source,root.subject,true);
            else {
                if(firstDeferred==null)firstDeferred=root;
                groupRoots.add(root);pendingGroupRoot=null;continue;
            }
            runtime.recoveryTasks.observed(root.value,root.subject);
            pendingGroupRoot=null;epoch.incrementAndGet();
        }
    }
    private void drainLoads(int budget) {
        Load firstDeferred=null;
        while(budget-->0) {
            Load load=pendingLoad;
            if(load==null)pendingLoad=load=loads.poll();
            if(load==null)break;
            if(load==firstDeferred){pendingLoadReason=load.reason;break;}
            pendingLoadReason="ACTUAL_LOADED_BINDING_PENDING";
            if(load.holder!=null&&!memorySource(load.holder,load.subject)||!runtime.linkLoaded(load.entity,load.subject)) {
                // Keep the exact event and its failure while giving other real
                // sources a turn. A failed source cannot stall the whole group.
                load.reason=pendingLoadReason;if(firstDeferred==null)firstDeferred=load;
                loads.add(load);pendingLoad=null;pendingLoadReason="";continue;
            }
            load.reason="";
            pendingLoad=null;pendingLoadReason="";epoch.incrementAndGet();
        }
    }
    void groupCreated(Object value,UUID subject) {
        if(value==null)return;
        synchronized(loads) {
            if(closed)return;
            groupRoots.add(new LoadRoot(value,subject));epoch.incrementAndGet();
        }
    }
    void refreshGroupDefinitions(UUID subject) {
        for(Source source:registered())if(source.subjects.contains(subject)) {
            Object holder=source.holder();
            if(holder!=null)source.liveDefinition=holder==null?null:definition(holder instanceof Class<?> type?type:holder.getClass(),false);
        }
        for(Source source:registered())if(source.subjects.contains(subject)) {
            if(!source.groupDisposed||source.holder()!=null)registerGroupWalk(source,subject,false);
        }
    }
    private void registerGroupWalk(Source source,UUID subject,boolean newRoot) {
        ProRuntime.Subject canonical=runtime.canonicalSubject(subject);
        UUID group=canonical==null?subject:canonical.id;
        synchronized(groupWalks) {
            Map<UUID,GroupWalk> groups=groupWalks.computeIfAbsent(source,key->new HashMap<>());
            GroupWalk prior=groups.get(group);
            if(prior==null||newRoot&&prior.complete) {
                groups.put(group,new GroupWalk(source,group));source.groupDisposed=false;
            }
        }
    }
    void retireGroup(ProRuntime.Subject authority) {
        if(!runtime.server.isSameThread()||closed||!authority.terminal)return;
        retireExternalFields(authority);
        retireExternalHeapValues(authority);
        retireExternalMemory(authority);
        List<GroupWalk> walks;
        synchronized(groupWalks) {walks=groupWalks.values().stream().flatMap(groups->groups.values().stream())
                .filter(w->runtime.canonicalSubject(w.subject)==authority&&!w.complete).toList();}
        if(walks.isEmpty()){groupWalkCursors.remove(authority.id);return;}
        int budget=256,share=Math.max(1,256/Math.min(walks.size(),8));
        int start=(int)Math.floorMod(groupWalkCursors.getOrDefault(authority.id,0L),walks.size()),visited=0;
        for(int i=0;i<walks.size();i++) {
            if(budget<=0)break;
            GroupWalk walk=walks.get((start+i)%walks.size());visited=i+1;
            if(!walk.rootObserved)continue;
            walk.gap="";
            Object processing=null;
            int quota=Math.min(budget,share),remaining=quota;
            try {
                while(remaining-->0&&!walk.complete) {
                    processing=null;
                    if(walk.current==null) {
                        if(walk.pending.isEmpty()) {
                            if(walk.paused.isEmpty()){walk.complete=walk.gap.isEmpty();break;}
                            walk.current=walk.paused.removeFirst();
                        }else {
                        Object value=walk.pending.removeFirst();if(!walk.seen.add(value))continue;processing=value;
                        if(value instanceof Entity)continue;
                        if(value==null||value instanceof String||value instanceof UUID||value instanceof Number||value instanceof Boolean||value instanceof Character)continue;
                        if(characterWrapper(value)&&runtime.groupSubject(value)==authority){
                            if(walk.pending.size()>4080){walk.seen.remove(value);walk.pending.addLast(value);break;}
                            if(!retireDescriptorStream(value,authority)){walk.seen.remove(value);walk.pending.addLast(value);walk.gap="CHARACTER_WRAPPER_RELEASE_PENDING";break;}
                            Object[] children=(Object[])Class.forName("dev.ronova.pro.bootstrap.ResourceBridge",false,null).getMethod("wrapperChildren",Object.class).invoke(null,value);
                            for(Object child:children)enqueue(walk.pending,child);continue;
                        }
                        if(!retireResource(value,authority)) {walk.gap="RESOURCE_RELEASE_PENDING:"+value.getClass().getName();walk.seen.remove(value);walk.pending.addLast(value);break;}
                        if(value.getClass().getClassLoader()==null&&!platformStreamWrapper(value)&&(value instanceof java.io.Closeable||value instanceof java.util.concurrent.ExecutorService||value instanceof Timer||value instanceof Thread))continue;
                        if(value.getClass().isArray()) {
                            walk.current=new GroupCursor(value);
                        }
                        else if(value.getClass()==java.util.concurrent.atomic.AtomicReference.class) {
                            @SuppressWarnings("unchecked") var atomic=(java.util.concurrent.atomic.AtomicReference<Object>)value;Object child=atomic.get();enqueue(walk.pending,child);
                            if(runtime.groupSubject(child)==authority)atomic.compareAndSet(child,null);continue;
                        }
                        else if(SourceMaps.supported(value)||SourceMaps.supportedSet(value)) {
                            walk.current=new GroupCursor(value);
                        }
                        else if(SourceMaps.supportedList(value)) {
                            walk.current=new GroupCursor(value);
                        }else {
                            if(value instanceof Map<?,?>||value instanceof Collection<?>)throw new IOException("GROUP_CONTAINER_BACKEND_UNSUPPORTED:"+value.getClass().getName());
                            boolean statik=value instanceof Class<?>;Class<?> type=statik?(Class<?>)value:value.getClass();
                            if(runtime.groupSubject(value)!=authority)continue;
                            GroupCursor cursor=new GroupCursor(value);cursor.fields=new ArrayList<>();
                            for(Class<?> owner=type;owner!=null&&owner!=Object.class&&(owner.getClassLoader()!=null||platformStreamWrapper(value));owner=statik?null:owner.getSuperclass())
                                for(var field:owner.getDeclaredFields())if(java.lang.reflect.Modifier.isStatic(field.getModifiers())==statik
                                        &&(!platformStreamWrapper(value)||Set.of("in","out","buf","bytearr","readBuffer").contains(field.getName())))cursor.fields.add(field);
                            walk.current=cursor;
                        }
                        }
                    }
                    if(walk.current!=null) {
                        GroupCursor cursor=walk.current;Object value=cursor.value;
                        if(value.getClass().isArray()) {
                            int allowance=Math.min(32,remaining+1);remaining-=allowance-1;
                            retireArray(walk,authority,allowance);
                        }else if(SourceMaps.supportedList(value)) {
                            int allowance=Math.min(32,remaining+1);remaining-=allowance-1;
                            retireList(walk,authority,allowance);
                        }else if(SourceMaps.supported(value)||SourceMaps.supportedSet(value)) {
                            int allowance=Math.min(32,remaining+1);remaining-=allowance-1;
                            retireMap(walk,authority,allowance);
                        }else {
                        if(cursor.field==cursor.fields.size()){walk.current=null;continue;}
                        if(walk.pending.size()>=4096){walk.paused.addLast(cursor);walk.current=null;continue;}
                        var field=cursor.fields.get(cursor.field);if(!field.trySetAccessible())throw new IOException("FIELD_INACCESSIBLE:"+field);
                        Object receiver=value instanceof Class<?>?null:value;Object child=field.get(receiver);
                        if(!field.getType().isPrimitive())enqueue(walk.pending,child);
                        // The selected holder owns this slot. Children are disposed independently before the traversal releases them.
                        SourceMaps.retireField(runtime,field,receiver,child,()->clearField(field,receiver,child));
                        walk.source.cleared(field,receiver==null?field.getDeclaringClass():receiver);
                        runtime.recoveryRecords.groupFieldCleared(field,receiver==null?field.getDeclaringClass():receiver);
                        walk.source.mutations.incrementAndGet();cursor.field++;
                        }
                        // Process discovered children before resuming a large
                        // parent, so its bounded pending queue can drain.
                        if(walk.current!=null&&!walk.pending.isEmpty()){walk.paused.addLast(walk.current);walk.current=null;}
                    }
                }
                if(walk.complete) {
                    walk.seen.clear();walk.pending.clear();walk.paused.clear();walk.current=null;
                    synchronized(groupWalks) {
                        if(groupWalks.get(walk.source).values().stream().allMatch(group->group.complete)) {
                            walk.source.groupDisposed=true;walk.source.resource=null;walk.source.nestedRetained=null;walk.source.nestedCarrier=null;
                            holderWalks.remove(walk.source);holderDiscovery.put(walk.source,"");
                        }
                    }
                }
            }catch(ReflectiveOperationException|IOException|RuntimeException unavailable) {
                if(processing!=null&&walk.current==null) {walk.seen.remove(processing);walk.pending.addFirst(processing);}
                walk.gap="GROUP_DISPOSITION:"+unavailable.getMessage();
            }finally{budget-=quota-Math.max(0,remaining);}
        }
        groupWalkCursors.put(authority.id,(long)(start+Math.max(1,visited))%walks.size());
    }
    private void retireExternalFields(ProRuntime.Subject authority){
        try{
            Object[][] rows=runtime.externalGroupFields(authority);authority.unresolvedSources.remove("EXTERNAL_FIELD_DISCOVERY_UNAVAILABLE");
            if(rows.length==0){externalFieldCursors.remove(authority.id);authority.unresolvedSources.remove("EXTERNAL_FIELD_DISPOSITION_PENDING");return;}
            int start=externalFieldCursors.getOrDefault(authority.id,0)%rows.length,visited=Math.min(rows.length,256);
            for(int i=0;i<visited;i++){
                Object[] row=rows[(start+i)%rows.length];var field=(java.lang.reflect.Field)row[0];Object receiver=row[1];
                String key="EXTERNAL_FIELD_DISPOSITION:"+field+":"+System.identityHashCode(receiver)+(row.length>5?":"+System.identityHashCode(row[5]):"");
                try{
                    if(row.length>2&&!Boolean.TRUE.equals(row[2]))throw new IllegalStateException("EXTERNAL_FIELD_INITIALIZATION_PENDING");
                    if(!field.trySetAccessible())throw new IllegalAccessException("EXTERNAL_FIELD_INACCESSIBLE");
                    if(row.length>5){restoreExternalConstant(field,row);authority.unresolvedSources.remove(key);continue;}
                    Object value=field.get(receiver),zero=field.getType().isPrimitive()?zero(field.getType()):null;
                    if(!Objects.equals(value,zero)){clearField(field,receiver,value);if(value!=null&&!field.getType().isPrimitive()&&runtime.groupSubject(value)==authority)groupCreated(value,authority.id);}
                    authority.unresolvedSources.remove(key);
                }catch(ReflectiveOperationException|RuntimeException unavailable){authority.unresolvedSources.add(key);}
            }
            int next=(start+visited)%rows.length;externalFieldCursors.put(authority.id,next);
            if(visited<rows.length&&next!=0)authority.unresolvedSources.add("EXTERNAL_FIELD_DISPOSITION_PENDING");else authority.unresolvedSources.remove("EXTERNAL_FIELD_DISPOSITION_PENDING");
        }catch(ReflectiveOperationException|RuntimeException unavailable){authority.unresolvedSources.add("EXTERNAL_FIELD_DISCOVERY_UNAVAILABLE");}
    }
    private static void restoreExternalConstant(java.lang.reflect.Field field,Object[] row)throws ReflectiveOperationException {
        Class<?> bridge=Class.forName("dev.ronova.pro.bootstrap.TaskBridge",false,null);
        Object gate=bridge.getMethod("beginRecoveryMutation",Object.class).invoke(null,field.getDeclaringClass());
        if(Boolean.FALSE.equals(gate))throw new IllegalStateException("EXTERNAL_FIELD_WRITE_IN_FLIGHT");
        try{
            Object current=field.get(null),expected=row[3],baseline=row[4];
            if(!sameConstantValue(current,baseline)){
                if(!sameConstantValue(current,expected))throw new IllegalStateException("EXTERNAL_FIELD_OUTSIDE_VALUE_PRESERVED");
                boolean restored=Boolean.TRUE.equals(Class.forName("dev.ronova.pro.bootstrap.NativeControl",false,null)
                        .getMethod("restoreRecoveredField",java.lang.reflect.Field.class,Object.class,Object.class,Object.class).invoke(null,field,null,current,baseline));
                if(!restored||!sameConstantValue(field.get(null),baseline))throw new IllegalStateException("EXTERNAL_FIELD_CONSTANT_RESTORE_PENDING");
            }
            Class.forName("dev.ronova.pro.bootstrap.CodeSourceBridge",false,null).getMethod("constantRestored",Object.class,java.lang.reflect.Field.class).invoke(null,row[5],field);
        }finally{bridge.getMethod("endFieldMutation",Object.class).invoke(null,gate);}
    }
    private void retireExternalHeapValues(ProRuntime.Subject authority){
        try{
            Object[][] rows=runtime.externalGroupHeapValues(authority);authority.unresolvedSources.remove("EXTERNAL_HEAP_DISCOVERY_UNAVAILABLE");
            Set<String> present=new HashSet<>();for(Object[] row:rows)present.add("EXTERNAL_HEAP_VALUE:"+System.identityHashCode(row[1])+':'+row[2]);
            authority.unresolvedSources.removeIf(key->key.startsWith("EXTERNAL_HEAP_VALUE:")&&!present.contains(key));
            if(rows.length==0){externalHeapCursors.remove(authority.id);authority.unresolvedSources.remove("EXTERNAL_HEAP_DISPOSITION_PENDING");return;}
            Class<?> bridge=Class.forName("dev.ronova.pro.bootstrap.CodeSourceBridge",false,null);var restore=bridge.getMethod("restoreHeapValue",Object.class);
            int start=externalHeapCursors.getOrDefault(authority.id,0)%rows.length,visited=Math.min(rows.length,256);
            for(int i=0;i<visited;i++){
                Object[] row=rows[(start+i)%rows.length];String key="EXTERNAL_HEAP_VALUE:"+System.identityHashCode(row[1])+':'+row[2];
                try{if(Boolean.TRUE.equals(restore.invoke(null,row[0])))authority.unresolvedSources.remove(key);else authority.unresolvedSources.add(key);}
                catch(ReflectiveOperationException|RuntimeException unavailable){authority.unresolvedSources.add(key);}
            }
            int next=(start+visited)%rows.length;externalHeapCursors.put(authority.id,next);
            if(visited<rows.length&&next!=0)authority.unresolvedSources.add("EXTERNAL_HEAP_DISPOSITION_PENDING");else authority.unresolvedSources.remove("EXTERNAL_HEAP_DISPOSITION_PENDING");
        }catch(ReflectiveOperationException|RuntimeException unavailable){authority.unresolvedSources.add("EXTERNAL_HEAP_DISCOVERY_UNAVAILABLE");}
    }
    private static boolean sameConstantValue(Object first,Object second){
        if(first instanceof Float a&&second instanceof Float b)return Float.floatToRawIntBits(a)==Float.floatToRawIntBits(b);
        if(first instanceof Double a&&second instanceof Double b)return Double.doubleToRawLongBits(a)==Double.doubleToRawLongBits(b);
        return first instanceof String||second instanceof String?first==second:Objects.equals(first,second);
    }
    private void retireExternalMemory(ProRuntime.Subject authority){
        long[] cursor=externalMemoryCursors.computeIfAbsent(authority.id,ignored->new long[3]);
        try{
            long[] result=runtime.restoreExternalGroupMemory(authority,cursor[0],cursor[1],256);
            if(result==null||result.length<5)throw new IllegalStateException("NATIVE_MEMORY_RECOVERY_UNAVAILABLE");
            authority.unresolvedSources.remove("EXTERNAL_NATIVE_MEMORY_RECOVERY_UNAVAILABLE");
            cursor[0]=result[0];cursor[1]=result[1];if(result[2]!=0)cursor[2]=1;
            if(result[4]!=0){
                if(cursor[2]!=0)authority.unresolvedSources.add("EXTERNAL_NATIVE_MEMORY_RECOVERY_PENDING");
                else authority.unresolvedSources.remove("EXTERNAL_NATIVE_MEMORY_RECOVERY_PENDING");
                cursor[0]=0;cursor[1]=0;cursor[2]=0;
            }else authority.unresolvedSources.add("EXTERNAL_NATIVE_MEMORY_RECOVERY_PENDING");
        }catch(ReflectiveOperationException|RuntimeException unavailable){authority.unresolvedSources.add("EXTERNAL_NATIVE_MEMORY_RECOVERY_UNAVAILABLE");}
    }
    private boolean retireResource(Object value,ProRuntime.Subject authority)throws IOException,ReflectiveOperationException {
        if(runtime.groupSubject(value)!=authority)return true;
        // Detach the selected wrapper's actual fields in GroupWalk. Its captured
        // endpoint is handled separately, so a shared endpoint is never closed
        // by a wrapper close() and buffered output is not flushed on retirement.
        if(platformStreamWrapper(value))return true;
        if(value instanceof Thread thread) {
            Class<?> bridge=Class.forName("dev.ronova.pro.bootstrap.TaskBridge",false,null);
            Object scheduler=bridge.getMethod("threadScheduler",Thread.class).invoke(null,thread);
            if(scheduler!=null)runtime.recoveryTasks.groupScheduler(scheduler,authority);
            return Boolean.TRUE.equals(bridge.getMethod("retireThread",Thread.class,Module[].class).invoke(null,thread,(Object)runtime.groupModules(authority)));
        }
        if(value instanceof java.util.concurrent.ExecutorService) {
            Class<?> bridge=Class.forName("dev.ronova.pro.bootstrap.TaskBridge",false,null);
            if(bridge.getMethod("realScheduler",Object.class).invoke(null,value)==null)
                throw new IOException("SCHEDULER_DELEGATION_UNRESOLVED:"+value.getClass().getName());
            Module[] modules=runtime.groupModules(authority);
            String disposition=(String)bridge.getMethod("schedulerDisposition",Object.class,Module[].class).invoke(null,value,(Object)modules);
            if(disposition.equals("SHARED"))return true;
            if(!disposition.equals("PRIVATE"))throw new IOException("SCHEDULER_"+disposition);
            @SuppressWarnings("unchecked") List<Runnable> removed=(List<Runnable>)bridge.getMethod("retireScheduler",Object.class,Module[].class).invoke(null,value,(Object)modules);
            runtime.recoveryTasks.schedulerDrained(value,removed,authority);
            return Boolean.TRUE.equals(bridge.getMethod("schedulerResourceEnded",Object.class).invoke(null,value));
        }
        if(value.getClass()==Timer.class){
            Class<?> bridge=Class.forName("dev.ronova.pro.bootstrap.TaskBridge",false,null);
            Module[] modules=runtime.groupModules(authority);
            @SuppressWarnings("unchecked") List<Runnable> removed=(List<Runnable>)bridge.getMethod("retireTimer",Object.class,Module[].class).invoke(null,value,(Object)modules);
            runtime.recoveryTasks.schedulerDrained(value,removed,authority);
            return Boolean.TRUE.equals(bridge.getMethod("timerResourceEnded",Object.class,Module[].class).invoke(null,value,(Object)modules));
        }
        if(value instanceof Process process&&value.getClass().getClassLoader()==null){
            return retireProcess(process,authority);
        }
        if(value instanceof java.nio.Buffer buffer){
            if(!buffer.isDirect())return true;
            return retireDescriptorStream(value,authority);
        }
        if(Boolean.TRUE.equals(Class.forName("dev.ronova.pro.bootstrap.ResourceBridge",false,null).getMethod("bufferResource",Object.class).invoke(null,value)))
            return retireDescriptorStream(value,authority);
        if(Boolean.TRUE.equals(Class.forName("dev.ronova.pro.bootstrap.ResourceBridge",false,null).getMethod("ioResource",Object.class).invoke(null,value)))
            return retireDescriptorStream(value,authority);
        if(value.getClass()==FileInputStream.class||value.getClass()==FileOutputStream.class||value.getClass()==RandomAccessFile.class||value instanceof java.nio.channels.FileChannel)
            return retireDescriptorStream(value,authority);
        // Invoke only platform close implementations; a selected user override is already suppressed.
        if(value instanceof java.io.Closeable closeable&&value.getClass().getClassLoader()==null) {
            if(value.getClass()==ByteArrayInputStream.class||value.getClass()==ByteArrayOutputStream.class||value.getClass()==StringReader.class||value.getClass()==StringWriter.class){closeable.close();return true;}
            if(value instanceof java.nio.channels.Channel)throw new IOException("CHANNEL_ORIGINAL_HANDLE_ASSOCIATION_UNOBSERVED");
            throw new IOException("PLATFORM_RESOURCE_DELEGATION_UNRESOLVED:"+value.getClass().getName());
        }
        if(value instanceof Closeable||value instanceof java.util.concurrent.ExecutorService||value instanceof Process)
            throw new IOException("RESOURCE_DELEGATION_UNRESOLVED:"+value.getClass().getName());
        return true;
    }
    private static boolean characterWrapper(Object value){
        Class<?> type=value.getClass();return type==BufferedReader.class||type==BufferedWriter.class||type==PushbackReader.class||type==LineNumberReader.class
                ||type==InputStreamReader.class||type==OutputStreamWriter.class||type==FileReader.class||type==FileWriter.class
                ||type.getClassLoader()==null&&(type.getName().equals("sun.nio.cs.StreamDecoder")||type.getName().equals("sun.nio.cs.StreamEncoder"));
    }
    private static boolean platformStreamWrapper(Object value){
        Class<?> type=value.getClass();return type==BufferedInputStream.class||type==BufferedOutputStream.class
                ||type==DataInputStream.class||type==DataOutputStream.class||type==PushbackInputStream.class
                ||type==FilterInputStream.class||type==FilterOutputStream.class;
    }
    private boolean retireProcess(Process process,ProRuntime.Subject authority)throws IOException,ReflectiveOperationException {
        Class<?> bridge=Class.forName("dev.ronova.pro.bootstrap.ResourceBridge",false,null);
        java.util.function.Predicate<Object> selected=value->runtime.groupSubject(value)==authority;
        long generation=authority.generation;
        java.util.concurrent.Callable<String> action=()->{
            if(!authority.terminal||authority.generation!=generation||runtime.groupSubject(process)!=authority)return "PROCESS_QUALIFICATION_CHANGED";
            return (String)bridge.getMethod("releaseProcess",Process.class,java.util.function.Predicate.class,Module[].class).invoke(null,process,selected,(Object)runtime.groupModules(authority));
        };
        try{return Boolean.TRUE.equals(bridge.getMethod("retireProcess",Process.class,java.util.concurrent.Callable.class).invoke(null,process,action));}
        catch(java.lang.reflect.InvocationTargetException failed){if(failed.getCause() instanceof IOException unavailable)throw unavailable;throw failed;}
    }
    private boolean retireDescriptorStream(Object stream,ProRuntime.Subject authority)throws ReflectiveOperationException,IOException{
        Class<?> bridge=Class.forName("dev.ronova.pro.bootstrap.ResourceBridge",false,null);
        java.util.function.Predicate<Object> selected=value->runtime.groupSubject(value)==authority;
        long generation=authority.generation;
        java.util.concurrent.Callable<String> action=()->{
            if(!authority.terminal||authority.generation!=generation||runtime.groupSubject(stream)!=authority)return "RESOURCE_QUALIFICATION_CHANGED";
            return (String)bridge.getMethod("release",Object.class,java.util.function.Predicate.class,Module[].class).invoke(null,stream,selected,(Object)runtime.groupModules(authority));
        };
        try{return Boolean.TRUE.equals(bridge.getMethod("retire",Object.class,java.util.concurrent.Callable.class).invoke(null,stream,action));}
        catch(java.lang.reflect.InvocationTargetException failed){
            if(failed.getCause() instanceof IOException unavailable)throw unavailable;
            throw failed;
        }
    }
    private void retireMap(GroupWalk walk,ProRuntime.Subject authority,int limit)throws IOException,ReflectiveOperationException {
        GroupCursor cursor=walk.current;Object map=SourceMaps.supportedSet(cursor.value)?SourceMaps.setMap(cursor.value):cursor.value;
        if(cursor.mapCursor==null||cursor.mapCursor.map!=map)cursor.mapCursor=new SourceMaps.Cursor(map);
        if(!SourceMaps.visit(map,cursor.mapCursor,limit,(key,value,node,position)->{
            if(walk.pending.size()>4094)return false;
            enqueue(walk.pending,key);enqueue(walk.pending,value);
            SourceMaps.removeVisited(position,node,runtime,authority);return true;
        }))throw new IOException("GROUP_MAP_BUSY");
        if(SourceMaps.supportedSet(cursor.value)&&SourceMaps.setMap(cursor.value)!=map){cursor.mapCursor=null;throw new IOException("GROUP_SET_BACKING_CHANGED");}
        if(cursor.mapCursor.complete)walk.current=null;
    }
    private int retireArray(GroupWalk walk,ProRuntime.Subject authority,int limit)throws IOException,ReflectiveOperationException {
        GroupCursor cursor=walk.current;Object value=cursor.value;
        Class<?> component=value.getClass().getComponentType();int length=java.lang.reflect.Array.getLength(value);
        if(cursor.element==length){walk.current=null;return 0;}
        int count=Math.min(limit,length-cursor.element);
        if(!component.isPrimitive())count=Math.min(count,4096-walk.pending.size());
        if(count==0)return 0;
        Class<?> bridge=Class.forName("dev.ronova.pro.bootstrap.TaskBridge",false,null);
        Object gate=bridge.getMethod("beginRecoveryArrayMutation",Object.class,int.class,int.class,long.class).invoke(null,value,cursor.element,count,cursor.arrayRevision);
        if(Boolean.FALSE.equals(gate))throw new IOException("GROUP_ARRAY_WRITE_IN_FLIGHT");
        int processed=0;boolean written=false;
        try {
            cursor.element=(Integer)bridge.getMethod("recoveryArrayStart",Object.class).invoke(null,gate);
            var elements=component.isPrimitive()?null:java.lang.invoke.MethodHandles.arrayElementVarHandle(value.getClass());
            while(processed<count){
                int index=cursor.element;Object child=java.lang.reflect.Array.get(value,index);
                if(component.isPrimitive()) {
                    if(runtime.groupSubject(value)==authority){java.lang.reflect.Array.set(value,index,zero(component));
                        if(!Objects.equals(java.lang.reflect.Array.get(value,index),zero(component)))throw new IOException("GROUP_ARRAY_CLEAR_READBACK_FAILED");}
                }else {
                    if(child!=null&&!walk.seen.contains(child))enqueue(walk.pending,child);
                    if(runtime.groupSubject(child)==authority){elements.compareAndSet(value,index,child,null);
                        if(java.lang.reflect.Array.get(value,index)==child)throw new IOException("GROUP_ARRAY_CLEAR_READBACK_FAILED");}
                }
                cursor.element++;processed++;
            }
            written=true;
        }finally {
            try{cursor.arrayRevision=(Long)bridge.getMethod("finishRecoveryArrayMutation",Object.class,boolean.class).invoke(null,gate,written);}
            catch(ReflectiveOperationException|RuntimeException unavailable){cursor.element=0;throw unavailable;}
        }
        if(cursor.element==length)walk.current=null;
        return processed;
    }
    private int retireList(GroupWalk walk,ProRuntime.Subject authority,int limit)throws IOException,ReflectiveOperationException {
        GroupCursor cursor=walk.current;Object value=cursor.value;
        boolean copyOnWrite=value.getClass()==java.util.concurrent.CopyOnWriteArrayList.class;
        
        java.lang.reflect.Field array=copyOnWrite?java.util.concurrent.CopyOnWriteArrayList.class.getDeclaredField("array"):null;
        if(array!=null)array.setAccessible(true);
        IOException[] failure={null};int[] processed={0};Runnable action=()->{
            try {
                List<?> list=(List<?>)value;
                Object stamp=copyOnWrite?array.get(value):null;
                long currentRevision=copyOnWrite?0:RecoveryReferences.arrayListRevision(value);
                if(!copyOnWrite&&currentRevision<0)throw new IOException("ARRAY_LIST_REVISION_UNOBSERVED");
                if(copyOnWrite?cursor.listStamp!=stamp:cursor.listRevision!=currentRevision)cursor.element=list.size()-1;
                while(cursor.element>=0&&processed[0]<limit) {
                    Object child=list.get(cursor.element);
                    if(child!=null&&!walk.seen.contains(child)){
                        if(walk.pending.size()>=4096)break;
                        enqueue(walk.pending,child);
                    }
                    if(runtime.groupSubject(child)==authority)SourceMaps.removeListIndex(list,cursor.element);
                    cursor.element--;processed[0]++;
                }
                if(copyOnWrite)cursor.listStamp=array.get(value);
                else cursor.listRevision=RecoveryReferences.arrayListRevision(value)+(SourceMaps.fastList(value)?0:1); // withList publishes its own revision on return.
                if(cursor.element<0)walk.current=null;
            }catch(IOException unavailable){failure[0]=unavailable;}
            catch(ReflectiveOperationException unavailable){failure[0]=new IOException("GROUP_LIST_VERSION_UNAVAILABLE",unavailable);}
        };
        if(copyOnWrite) {
            var lock=java.util.concurrent.CopyOnWriteArrayList.class.getDeclaredField("lock");lock.setAccessible(true);
            synchronized(lock.get(value)) {action.run();}
        }else {
            if(!RecoveryReferences.withArrayList(value,true,action))throw new IOException("ARRAY_LIST_SCOPE_BUSY_OR_UNOBSERVED");
        }
        if(failure[0]!=null)throw failure[0];
        return processed[0];
    }
    private static Object zero(Class<?> type) {
        if(type==boolean.class)return false;if(type==char.class)return (char)0;if(type==byte.class)return (byte)0;
        if(type==short.class)return (short)0;if(type==int.class)return 0;if(type==long.class)return 0L;if(type==float.class)return 0F;return 0D;
    }
    static void clearField(java.lang.reflect.Field field,Object receiver,Object expected)throws ReflectiveOperationException {
        Class<?> bridge=Class.forName("dev.ronova.pro.bootstrap.TaskBridge",false,null);
        Object gate=bridge.getMethod("beginRecoveryMutation",Object.class).invoke(null,receiver==null?field.getDeclaringClass():receiver);
        if(Boolean.FALSE.equals(gate))throw new IllegalStateException("GROUP_FIELD_WRITE_IN_FLIGHT");
        try {clearFieldLocked(field,receiver,expected);}finally {bridge.getMethod("endFieldMutation",Object.class).invoke(null,gate);}
    }
    static boolean clearReferenceField(java.lang.reflect.Field field,Object receiver,Object expected,Runnable attempted)throws ReflectiveOperationException{
        Class<?> bridge=Class.forName("dev.ronova.pro.bootstrap.TaskBridge",false,null);
        Object gate=bridge.getMethod("beginRecoveryMutation",Object.class).invoke(null,receiver==null?field.getDeclaringClass():receiver);
        if(Boolean.FALSE.equals(gate))return false;
        try{attempted.run();clearFieldLocked(field,receiver,expected);return field.get(receiver)==null;}
        finally{bridge.getMethod("endFieldMutation",Object.class).invoke(null,gate);}
    }
    private static void clearFieldLocked(java.lang.reflect.Field field,Object receiver,Object expected)throws ReflectiveOperationException {
        if(field.getDeclaringClass().isHidden()){
            boolean cleared=Boolean.TRUE.equals(Class.forName("dev.ronova.pro.bootstrap.NativeControl",false,null)
                    .getMethod("clearRecoveredField",java.lang.reflect.Field.class,Object.class,Object.class).invoke(null,field,receiver,expected));
            if(!cleared||!Objects.equals(field.get(receiver),field.getType().isPrimitive()?zero(field.getType()):null))
                throw new IllegalStateException("HIDDEN_GROUP_FIELD_CLEAR_UNAVAILABLE_OR_CHANGED");
            return;
        }
        Class<?> type=Class.forName("sun.misc.Unsafe");var singleton=type.getDeclaredField("theUnsafe");singleton.setAccessible(true);Object unsafe=singleton.get(null);
        boolean statik=java.lang.reflect.Modifier.isStatic(field.getModifiers());
        Object base=statik?type.getMethod("staticFieldBase",java.lang.reflect.Field.class).invoke(unsafe,field):receiver;
        long offset=(Long)type.getMethod(statik?"staticFieldOffset":"objectFieldOffset",java.lang.reflect.Field.class).invoke(unsafe,field);
        if(!field.getType().isPrimitive()) {
            boolean changed=Boolean.TRUE.equals(type.getMethod("compareAndSwapObject",Object.class,long.class,Object.class,Object.class).invoke(unsafe,base,offset,expected,null));
            if(!changed&&field.get(receiver)!=null)throw new IllegalStateException("GROUP_FIELD_CLEAR_REFUSED_OR_CHANGED");
            if(field.get(receiver)!=null)throw new IllegalStateException("GROUP_FIELD_CLEAR_READBACK_FAILED");return;
        }
        Class<?> primitive=field.getType();String suffix=primitive.getName();suffix=Character.toUpperCase(suffix.charAt(0))+suffix.substring(1);
        type.getMethod("put"+suffix+"Volatile",Object.class,long.class,primitive).invoke(unsafe,base,offset,zero(primitive));
        if(!Objects.equals(field.get(receiver),zero(primitive)))throw new IllegalStateException("GROUP_SCALAR_CLEAR_READBACK_FAILED");
    }
    /** Read-only discovery of exact observed tags in registered holders, including POJO wrappers.
     * This does not authorize unlinking an opaque record or writing an unobserved file. */
    private void discoverHolders() {
        if(!runtime.server.isSameThread()||closed)return;
        List<Source> registered=new ArrayList<>(registered());
        for(int i=0;i<Math.min(8,registered.size());i++) {
            Source source=registered.get((int)Math.floorMod(holderCursor++,registered.size()));
            Object holder=source.holder();
            if(holder==null){holderDiscovery.put(source,source.disposed()?"":"HOLDER_RELEASE_UNOBSERVED");continue;}
            String result="HOLDER_DISCOVERY_PENDING";
            if(!current(source))result="HOLDER_OWNER_CHANGED:"+bindingProblem(source);
            else if(!tryRecordGraph())result="HOLDER_GRAPH_BUSY";
            else try {
                HolderWalk walk=holderWalks.get(source);
                if(walk==null||walk.revision!=source.mutations.get()){walk=new HolderWalk(holder,source.mutations.get());holderWalks.put(source,walk);}
                int budget=512;
                while(budget-->0) {
                    walk.processing=null;
                    if(walk.current==null){
                    if(walk.pending.isEmpty()) {
                        if(walk.paused.isEmpty()){result=walk.gap;holderWalks.remove(source);break;}
                        walk.current=walk.paused.removeFirst();
                    }else{
                    Object value=walk.pending.removeFirst();if(!walk.seen.add(value))continue;walk.processing=value;
                    Class<?> type=value.getClass();
                    HolderCursor cursor=new HolderCursor(value);
                    if(type==CompoundTag.class) {
                        CompoundTag tag=(CompoundTag)value;UUID subject=subject(this,tag);
                        if(subject!=null&&source.subjects.add(subject))epoch.incrementAndGet();
                        cursor.keys=List.copyOf(tag.getAllKeys());walk.current=cursor;
                    }else if(type==ListTag.class)walk.current=cursor;
                    else if(SCALARS.contains(type)||type==String.class||type==UUID.class||type.isEnum()||isPrimitiveCollection(value))continue;
                    else if(SourceMaps.supported(value)||SourceMaps.supportedSet(value)||SourceMaps.supportedList(value))walk.current=cursor;
                    else if(type==java.util.concurrent.atomic.AtomicReference.class){
                        if(walk.pending.size()>=4096){walk.seen.remove(value);walk.pending.addLast(value);continue;}
                        enqueue(walk.pending,((java.util.concurrent.atomic.AtomicReference<?>)value).get());
                    }
                    else if(type.isArray()) {if(!type.getComponentType().isPrimitive())walk.current=cursor;}
                    else if(value instanceof Entity) {
                        UUID subject=runtime.recoveryTasks.subjectOf(value);if(subject!=null&&source.subjects.add(subject))epoch.incrementAndGet();
                    }else if(value instanceof Map<?,?>||value instanceof Collection<?>)walk.gap="HOLDER_CONTAINER_UNSUPPORTED:"+type.getName();
                    else {
                        boolean statik=value==source.holder()&&value instanceof Class<?>;type=statik?(Class<?>)value:type;
                        if(type.getClassLoader()==null)continue;
                        cursor.fields=new ArrayList<>();walk.current=cursor;
                        for(Class<?> owner=type;owner!=null&&owner!=Object.class&&owner!=SavedData.class;owner=statik?null:owner.getSuperclass())
                            for(var field:owner.getDeclaredFields())if(java.lang.reflect.Modifier.isStatic(field.getModifiers())==statik&&!field.getType().isPrimitive())cursor.fields.add(field);
                    }
                    }
                    }
                    if(walk.current!=null){
                        int allowance=Math.min(32,budget+1);budget-=allowance-1;
                        discoverCursor(walk,allowance);
                        if(walk.current!=null&&!walk.pending.isEmpty()){walk.paused.addLast(walk.current);walk.current=null;}
                    }
                }
            }catch(ReflectiveOperationException|IOException|RuntimeException unavailable) {
                HolderWalk walk=holderWalks.get(source);
                if(walk!=null&&walk.processing!=null&&walk.current==null){walk.seen.remove(walk.processing);walk.pending.addFirst(walk.processing);}
                result="HOLDER_DISCOVERY_UNRESOLVED:"+unavailable.getMessage();
            }finally {releaseRecordGraph();}
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
    private static Input actualInput(InputStream stream){
        return stream instanceof Input input?input:stream instanceof ObservedPushback pushback?pushback.actual:
                stream instanceof ObservedDataInput input?input.actual:null;
    }
    private static final class ObservedDataInput extends DataInputStream {
        final Input actual;
        ObservedDataInput(InputStream input){super(input);actual=actualInput(input);}
    }
    public static DataInputStream dataInput(InputStream stream){return new ObservedDataInput(stream);}
    public static CompoundTag readInput(InputStream stream)throws IOException {
        Input observed=actualInput(stream);
        if(observed==null)return RecoveryImage.readCompressed(stream);
        try {
            CompoundTag decoded=RecoveryImage.readCompressed(new FilterInputStream(stream) { @Override public void close() { } });
            readCompleted(observed,stream,decoded);
            return decoded;
        } finally { observed.close(); }
    }
    public static CompoundTag readPlain(DataInput input)throws IOException {return readPlain(input,NbtAccounter.UNLIMITED);}
    public static CompoundTag readPlain(DataInput input,NbtAccounter limit)throws IOException {
        CompoundTag decoded=RecoveryImage.read(input,limit);
        if(input instanceof ObservedDataInput stream&&stream.actual!=null)readCompleted(stream.actual,stream,decoded);
        return decoded;
    }
    private static void readCompleted(Input observed,InputStream stream,CompoundTag decoded)throws IOException {
        if(observed.identity==null){observed.retainedDone=true;observed.bytes.close();return;}
        byte[] rest=new byte[8192];while(stream.read(rest)>=0) { }
        String ending=StorageNative.identity(observed.getFD(),observed.path.toString());
        observed.retainedDone=true;
        try(RecoveryImage.Encoded bytes=observed.bytes.finish()) {
            if(observed.identity.equals(ending))for(ProRuntime runtime:ProRuntime.sourceRuntimes()) {
                runtime.recoverySources.liveReadObserved(observed.path,ending,bytes,decoded);
                runtime.recoveryStorage.readObserved(observed.path,ending,bytes,decoded);
            }
        } finally {observed.bytes.close();}
    }
    private void liveReadObserved(Path path,String identity,RecoveryImage.Encoded bytes,CompoundTag decoded)throws IOException {
        String hash=bytes.hash();
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
        final RecoveryImage.Builder bytes=new RecoveryImage.Builder();
        boolean retainedDone;
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
        private void retain(byte[] data,int offset,int count)throws IOException { if(!retainedDone)bytes.write(data,offset,count); }
        @Override public void close()throws IOException { try { super.close(); }finally { bytes.close(); } }
    }
    void observed(CompoundTag tag,UUID subject) {
        observed(tag,subject,null);
    }
    private void observed(CompoundTag tag,UUID subject,Object record) {
        GRAPH.writeLock().lock();
        try {
        synchronized(TAGS) {
            Origin prior=TAGS.get(tag);
            if(prior!=null&&(prior.owner!=this||!prior.subject.equals(subject))) { AMBIGUOUS.add(tag);gap="CONFLICTING_TAG_ORIGIN";epoch.incrementAndGet();return; }
            if(prior!=null&&record!=null&&prior.record!=record){AMBIGUOUS.add(tag);gap="CONFLICTING_TAG_RECORD_IDENTITY";epoch.incrementAndGet();return;}
            TAGS.put(tag,new Origin(this,subject,record!=null?record:prior==null?new Object():prior.record));
            if(prior==null)epoch.incrementAndGet();
            runtime.recoveryTasks.observed(tag,subject);
        }
        } finally { GRAPH.writeLock().unlock(); }
    }
    public static void copied(CompoundTag from,CompoundTag to) {
        if(StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE).getCallerClass()!=ProRuntime.class)throw new SecurityException("ACTUAL_NBT_COPY_RUNTIME_REQUIRED");
        copyRecord(from,to);
    }
    static void projectedCopy(CompoundTag from,CompoundTag to) {
        if(StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE).getCallerClass()!=RecoveryImage.class)throw new SecurityException("ACTUAL_NBT_PROJECTION_REQUIRED");
        copyRecord(from,to);
    }
    private static void copyRecord(CompoundTag from,CompoundTag to) {
        GRAPH.writeLock().lock();
        try {
        forwardMigration(from,to);
        synchronized(TAGS) {
            Origin origin=TAGS.get(from);
            if(AMBIGUOUS.contains(from)) {
                // Copying bytes does not resolve conflicting provenance. Retain the old
                // owner only for diagnostics/retirement, never as an execution authority.
                AMBIGUOUS.add(to);
                if(origin!=null) {
                    TAGS.put(to,origin);
                    origin.owner.epoch.incrementAndGet();
                }
                return;
            }
            if(origin!=null)origin.owner.observed(to,origin.subject,origin.record);
        }
        } finally { GRAPH.writeLock().unlock(); }
    }
    /** The original map operation supplied this prefix and this produced container. */
    public static void nbtOperationResult(Tag prefix,Tag result){
        Class<?> caller=StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE).getCallerClass();
        boolean actual=caller==NbtOps.class||nbtRecordBuilder(caller);
        if(!actual)throw new SecurityException("ACTUAL_NBT_OPERATION_REQUIRED");
        if(prefix instanceof CompoundTag from&&result instanceof CompoundTag to&&from!=to)copyRecord(from,to);
    }
    /** A completed original Dynamic map rewrite, using NbtOps at both ends. */
    public static void nbtDynamicMapResult(CompoundTag from,CompoundTag to){
        if(StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE).getCallerClass()!=com.mojang.serialization.Dynamic.class)
            throw new SecurityException("ACTUAL_NBT_DYNAMIC_MAP_REQUIRED");
        if(from!=to)copyRecord(from,to);
    }
    private static boolean nbtRecordBuilder(Class<?> caller){
        try{return caller==Class.forName("net.minecraft.nbt.NbtOps$NbtRecordBuilder",false,NbtOps.class.getClassLoader());}
        catch(ClassNotFoundException unavailable){throw new IllegalStateException("ACTUAL_NBT_RECORD_BUILDER_UNAVAILABLE",unavailable);}
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
                    if(type==CompoundTag.class&&nbtRecordBuilder(caller)&&frame.getMethodType().equals(java.lang.invoke.MethodType.methodType(
                            com.mojang.serialization.DataResult.class,CompoundTag.class,Tag.class)))return true;
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
            Origin origin=TAGS.get(root);if(origin==null||origin.owner!=owner||owner.runtime.canonicalSubject(origin.subject)!=owner.runtime.canonicalSubject(subject))return "RECORD_ORIGIN_CHANGED";
            while(!pending.isEmpty()) {
                Tag node=pending.removeFirst();if(!seen.add(node))continue;
                if(node instanceof CompoundTag compound) {
                    if(node.getClass()!=CompoundTag.class||!(node instanceof GraphNode graph)||!graph.pro$closedGraph())return "RECORD_GRAPH_BACKING_UNPROVEN_OR_EXPOSED";
                    if(SAVING.containsKey(compound))return "RECORD_SERIALIZATION_NOT_CLOSED";
                    if(AMBIGUOUS.contains(compound))return "RECORD_ORIGIN_AMBIGUOUS";
                    Origin child=TAGS.get(compound);
                    if(child!=null&&(child.owner!=owner||owner.runtime.canonicalSubject(child.subject)!=owner.runtime.canonicalSubject(subject)))return "FOREIGN_SUBJECT_IN_RECORD_SUBTREE";
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
            origin.owner.loads.add(new Load(entity,origin.subject,null));
        }
    }
    void cloned(Entity clone,UUID subject,Class<?> holder) {
        produced(clone,subject,holder,"NATIVE_CLONE_BINDING_CAPACITY");
    }
    void produced(Entity clone,UUID subject,Class<?> holder,String overflow) {
        runtime.recoveryTasks.observed(clone,subject);
        synchronized(loads) {
            if(closed)return;
            epoch.incrementAndGet();
            loads.add(new Load(clone,subject,holder));
        }
    }
    private boolean memorySource(Class<?> holder,UUID subject) {
        String code=code(holder);if(code==null) { pendingLoadReason="CLONE_HOLDER_LIVE_DEFINITION_UNAVAILABLE";return false; }
        String locator="MEMORY|"+holder.getName()+"|"+code;
        try(var gate=registrationGate.enter()) {
            Source source=sources.get(locator);
            if(source==null) {
                source=new Source(this,locator,code,null,holder);sources.put(locator,source);
            } else if(source.holder()!=holder) {
                // Equal names and code bytes do not replace an already registered Class.
                source=observedHolder(holder,subject);
                if(source==null||source.holder()!=holder||!source.code.equals(code)) {
                    pendingLoadReason="ACTUAL_MEMORY_DEFINITION_BINDING_PENDING";return false;
                }
            }
            source.subjects.add(subject);source.gap="";epoch.incrementAndGet();
            return true;
        }
    }
    /** Actual producer receiver, including a live SavedData or BlockEntity before its first save. */
    Source observedHolder(Object holder,UUID subject) {
        if(closed||holder==null)return null;
        for(Source source:sources.values())if(source.holder()==holder) {
            if(source.subjects.add(subject))epoch.incrementAndGet();return source;
        }
        Class<?> type=holder instanceof Class<?> clazz?clazz:holder.getClass();
        String code=code(type);if(code==null)return null;
        try(var gate=registrationGate.enter()) {
            for(Source source:sources.values())if(source.holder()==holder) {
                if(source.subjects.add(subject))epoch.incrementAndGet();return source;
            }
            String locator=holder instanceof Class<?>?"MEMORY|"+type.getName()+"|"+code:
                    "MEMORY_OBJECT|"+type.getName()+"|"+UUID.randomUUID();
            Source named=sources.get(locator);
            if(named!=null&&named.holder()!=holder) {
                // This label is local to the actual object. Restart lookup must not
                // select another loader's same-named definition to stand in for it.
                locator="MEMORY_OBJECT|"+type.getName()+"|"+UUID.randomUUID();
            }
            Source source=new Source(this,locator,code,null,holder);source.subjects.add(subject);source.gap="";
            sources.put(locator,source);epoch.incrementAndGet();return source;
        }
    }
    /** A nested source keeps its exact object after detachment; its durable locator follows the original parent slot on restart. */
    Source nestedSource(Source parent,java.lang.reflect.Field field,String kind,Object key,Object value)throws ReflectiveOperationException{
        Object holder=parent.holder();if(holder==null)return null;
        return nestedSource(parent,field,kind,key,value,field.get(holder instanceof Class<?>?null:holder));
    }
    Source nestedSource(Source parent,java.lang.reflect.Field field,String kind,Object key,Object value,Object carrier)throws ReflectiveOperationException{
        if(!RecoveryReferences.nestedHolder(value)||!current(parent))return null;
        for(Source ancestor=parent;ancestor!=null;ancestor=ancestor.nestedParent)if(ancestor.holder()==value)return ancestor;
        String code=code(value.getClass());if(code==null)return null;
        try(var gate=registrationGate.enter()){
            if(kind.equals("SET")){
                if(!SourceMaps.supportedSet(carrier))return null;
                List<Map.Entry<Object,Object>> rows=SourceMaps.entries(SourceMaps.setMap(carrier));if(rows==null)return null;
                boolean found=false;for(var row:rows)if(row.getKey()==value){found=true;break;}if(!found)return null;
            }else if(nestedValue(carrier,kind,key)!=value)return null;
            for(Source source:sources.values())if(source.nestedParent==parent&&field.equals(source.nestedField)
                    &&kind.equals(source.nestedKind)&&Objects.equals(key,source.nestedKey)&&source.holder()==value&&source.nestedCarrier==carrier){
                if(source.subjects.addAll(parent.subjects))epoch.incrementAndGet();return source;
            }
            String selector=field.getDeclaringClass().getName()+"#"+field.getName();
            String locator="NESTED|"+dev.ronova.pro.persistence.JournalLineage.encodeText(parent.locator)+"|"+
                    dev.ronova.pro.persistence.JournalLineage.encodeText(parent.code)+"|"+dev.ronova.pro.persistence.JournalLineage.encodeText(selector)+"|"+
                    kind+"|"+dev.ronova.pro.persistence.JournalLineage.encodeText(key==null&&!kind.equals("MAP")?"":SourceMaps.encodeKey(key))+"|"+UUID.randomUUID();
            Source source=new Source(this,locator,code,null,value);source.nestedRetained=value;source.nestedCarrier=carrier;source.nestedParent=parent;source.nestedField=field;source.nestedKind=kind;source.nestedKey=key;
            source.subjects.addAll(parent.subjects);source.gap="";sources.put(locator,source);epoch.incrementAndGet();return source;
        }
    }
    private Object nestedValue(Source parent,java.lang.reflect.Field field,String kind,Object key)throws ReflectiveOperationException{
        Object holder=parent.holder();if(holder==null){if(parent.disposed())return null;throw new IllegalAccessException("NESTED_PARENT_RELEASE_UNOBSERVED");}
        Object carrier=field.get(holder instanceof Class<?>?null:holder);
        return nestedValue(carrier,kind,key);
    }
    private Object nestedValue(Object carrier,String kind,Object key)throws ReflectiveOperationException{
        if(carrier==null||kind.equals("FIELD"))return carrier;
        if(kind.equals("BACKING")){Object[] values=SourceMaps.valueBacking(carrier);int index=(Integer)key;return index<values.length?values[index]:null;}
        if(kind.equals("MAP")){
            Object[] value=SourceMaps.peek(carrier,key);if(value==null)throw new IllegalAccessException("NESTED_MAP_BUSY_OR_UNSUPPORTED");return value[1];
        }
        if(kind.equals("ATOMIC")&&carrier.getClass()==java.util.concurrent.atomic.AtomicReference.class)return ((java.util.concurrent.atomic.AtomicReference<?>)carrier).get();
        if(kind.equals("ARRAY")&&carrier instanceof Object[] values){int index=(Integer)key;return index<values.length?values[index]:null;}
        if(kind.equals("LIST")&&(SourceMaps.supportedList(carrier))){
            int index=(Integer)key;
            if(carrier.getClass()==java.util.concurrent.CopyOnWriteArrayList.class){Object[] values=((List<?>)carrier).toArray();return index<values.length?values[index]:null;}
            Object[] result={null};boolean ran=RecoveryReferences.withArrayList(carrier,false,()->{List<?> values=(List<?>)carrier;result[0]=index<values.size()?values.get(index):null;});
            if(!ran)throw new IllegalAccessException("NESTED_LIST_BUSY");return result[0];
        }
        throw new IllegalAccessException("NESTED_CONTAINER_CHANGED");
    }
    private void restoreNestedSource(String locator,String code,Source prior){
        if(prior!=null&&!prior.nestedAbsent&&!prior.nestedQuery)return;
        try{
            String[] parts=locator.split("\\|",7);if(parts.length!=7)return;
            Source parent=sources.get(dev.ronova.pro.persistence.JournalLineage.decodeText(parts[1]));
            if(parent==null||!parent.code.equals(dev.ronova.pro.persistence.JournalLineage.decodeText(parts[2]))||!current(parent))return;
            String selector=dev.ronova.pro.persistence.JournalLineage.decodeText(parts[3]);int split=selector.lastIndexOf('#');
            if(parts[4].equals("SET")||parent.nestedQuery){
                Source source=new Source(this,locator,code,null,null);source.nestedParent=parent;source.nestedKind=parts[4];source.nestedQuery=true;
                String encoded=dev.ronova.pro.persistence.JournalLineage.decodeText(parts[5]);source.nestedKey=encoded.isEmpty()?null:SourceMaps.decodeKey(encoded);
                source.subjects.addAll(expectedSubjects.getOrDefault(locator,Set.of()));source.gap="";
                try(var gate=registrationGate.enter()){
                    if(prior==null?sources.putIfAbsent(locator,source)==null:sources.replace(locator,prior,source)){restartDiagnostics.remove(locator);epoch.incrementAndGet();}
                }
                return;
            }
            Object holder=parent.holder();java.lang.reflect.Field field=null;
            if(holder!=null){
                for(Class<?> type=holder instanceof Class<?> c?c:holder.getClass();type!=null;type=type.getSuperclass())
                    if(type.getName().equals(selector.substring(0,split))){field=type.getDeclaredField(selector.substring(split+1));break;}
                if(field==null||!field.trySetAccessible())return;
            }else if(!parent.disposed())return;
            String encoded=dev.ronova.pro.persistence.JournalLineage.decodeText(parts[5]);Object key=encoded.isEmpty()?null:SourceMaps.decodeKey(encoded);
            Object value=holder==null?null:nestedValue(parent,field,parts[4],key);
            if(value!=null&&(!RecoveryReferences.nestedHolder(value)||!code.equals(code(value.getClass()))))return;
            Source source=new Source(this,locator,code,null,value);source.nestedRetained=value;source.nestedParent=parent;source.nestedField=field;source.nestedKind=parts[4];source.nestedKey=key;source.nestedAbsent=value==null;
            source.subjects.addAll(expectedSubjects.getOrDefault(locator,Set.of()));source.gap="";
            try(var gate=registrationGate.enter()){
                if(prior==null?sources.putIfAbsent(locator,source)==null:sources.replace(locator,prior,source)){restartDiagnostics.remove(locator);epoch.incrementAndGet();}
            }
        }catch(ReflectiveOperationException|RuntimeException unavailable){restartDiagnostics.put(locator,"NESTED_LOCATION_UNAVAILABLE");}
    }
    @FunctionalInterface interface SourceQuery {boolean absent(Source source)throws ReflectiveOperationException;}
    /** A historical Set edge has no stable element index. Query every current matching holder;
     * these views grant no mutation authority and never stand in for a live source object. */
    boolean queryCurrent(Source source,SourceQuery query)throws ReflectiveOperationException{
        if(!current(source))throw new IllegalAccessException("NESTED_SOURCE_BINDING_CHANGED");
        if(!source.nestedQuery)return query.absent(source);
        return queryCurrent(source.nestedParent,parent->{
            Object holder=parent.holder();
            if(holder==null){if(parent.disposed())return true;throw new IllegalAccessException("NESTED_PARENT_RELEASE_UNOBSERVED");}
            String[] parts=source.locator.split("\\|",7);
            String selector=dev.ronova.pro.persistence.JournalLineage.decodeText(parts[3]);int split=selector.lastIndexOf('#');
            java.lang.reflect.Field selected=null;
            for(Class<?> type=holder instanceof Class<?> c?c:holder.getClass();type!=null;type=type.getSuperclass())
                if(type.getName().equals(selector.substring(0,split))){selected=type.getDeclaredField(selector.substring(split+1));break;}
            if(selected==null||!selected.trySetAccessible())throw new IllegalAccessException("NESTED_FIELD_UNAVAILABLE");
            final java.lang.reflect.Field field=selected;
            Object receiver=holder instanceof Class<?>?null:holder,carrier=field.get(receiver);
            if(carrier==null)return SourceMaps.replacementCovered(parent,field,null)||parent.cleared(field);
            if(source.nestedKind.equals("SET")){
                if(!SourceMaps.supportedSet(carrier))throw new IllegalAccessException("NESTED_SET_BACKEND_CHANGED");
                boolean[] absent={true};
                if(!SourceMaps.with(SourceMaps.setMap(carrier),()->{
                    List<Map.Entry<Object,Object>> rows=SourceMaps.entries(SourceMaps.setMap(carrier));
                    if(rows==null)throw new IllegalAccessException("NESTED_SET_QUERY_PENDING");
                    for(var row:rows){
                        Object value=row.getKey();if(value==null||!value.getClass().getName().equals(source.nestedKey))continue;
                        if(!queryNestedCandidate(source,parent,field,value,query)){absent[0]=false;break;}
                    }
                    if(field.get(receiver)!=carrier)throw new IllegalAccessException("NESTED_SET_HOLDER_CHANGED");
                }))throw new IllegalAccessException("NESTED_SET_QUERY_PENDING");
                return absent[0];
            }
            Object value=nestedValue(parent,field,source.nestedKind,source.nestedKey);
            boolean absent=value==null||queryNestedCandidate(source,parent,field,value,query);
            if(nestedValue(parent,field,source.nestedKind,source.nestedKey)!=value)throw new IllegalAccessException("NESTED_LOCATION_CHANGED");
            return absent;
        });
    }
    private boolean queryNestedCandidate(Source original,Source parent,java.lang.reflect.Field field,Object value,SourceQuery query)throws ReflectiveOperationException{
        if(!RecoveryReferences.nestedHolder(value)||!original.code.equals(code(value.getClass())))throw new IllegalAccessException("NESTED_DEFINITION_CHANGED");
        Source actual=nestedSource(parent,field,original.nestedKind,original.nestedKey,value);
        if(actual==null)throw new IllegalAccessException("NESTED_SOURCE_BINDING_PENDING");
        actual.subjects.addAll(original.subjects);
        return query.absent(actual);
    }
    public static void savedData(SavedData holder,CompoundTag image,File file)throws IOException {
        SavedData parent=HOLDER.get();HOLDER.set(holder);
        try { NbtIo.writeCompressed(image,file); }
        finally { if(parent==null)HOLDER.remove();else HOLDER.set(parent); }
    }
    private static Map<RecoverySources,Map<Object,UUID>> imageOrigins(CompoundTag image,boolean includePending)throws IOException {
        Map<CompoundTag,UUID> identities=new IdentityHashMap<>();Map<CompoundTag,Origin> origins=new IdentityHashMap<>();
        synchronized(TAGS){for(var entry:TAGS.entrySet())if(!AMBIGUOUS.contains(entry.getKey())){identities.put(entry.getKey(),entry.getValue().subject);origins.put(entry.getKey(),entry.getValue());}}
        Map<RecoverySources,Map<Object,UUID>> result=new IdentityHashMap<>();
        for(var selection:RecoveryImage.locate(image,identities)){
            CompoundTag tag=(CompoundTag)RecoveryImage.resolve(image,selection.path());Origin origin=origins.get(tag);
            result.computeIfAbsent(origin.owner,ignored->new IdentityHashMap<>()).put(origin.record,selection.subject());
        }
        if(includePending)synchronized(MIGRATIONS){Map<RecoverySources,Map<Object,UUID>> pending=MIGRATIONS.get(image);if(pending!=null)for(var entry:pending.entrySet())result.computeIfAbsent(entry.getKey(),ignored->new IdentityHashMap<>()).putAll(entry.getValue());}
        return result;
    }
    /** A pending record is resolved only by that same record identity at an actual output location. */
    private static void resolvedMigrations(CompoundTag image,Map<RecoverySources,Map<Object,UUID>> located){
        synchronized(MIGRATIONS){
            Map<RecoverySources,Map<Object,UUID>> pending=MIGRATIONS.get(image);if(pending==null)return;
            var owners=pending.entrySet().iterator();
            while(owners.hasNext()){
                var entry=owners.next();Map<Object,UUID> actual=located.get(entry.getKey());
                if(actual!=null)entry.getValue().entrySet().removeIf(record->Objects.equals(record.getValue(),actual.get(record.getKey())));
                if(entry.getValue().isEmpty())owners.remove();
            }
            if(pending.isEmpty())MIGRATIONS.remove(image);
        }
    }
    private void discoverCursor(HolderWalk walk,int limit)throws ReflectiveOperationException,IOException {
        HolderCursor cursor=walk.current;Object value=cursor.value;Class<?> type=value.getClass();
        if(SourceMaps.supported(value)||SourceMaps.supportedSet(value)){
            Object map=SourceMaps.supportedSet(value)?SourceMaps.setMap(value):value;
            if(cursor.map==null||cursor.map.map!=map)cursor.map=new SourceMaps.Cursor(map);
            if(!SourceMaps.visit(map,cursor.map,limit,(key,child,node,position)->{
                if(walk.pending.size()>4094)return false;
                enqueue(walk.pending,key);enqueue(walk.pending,child);return true;
            }))throw new IOException("HOLDER_MAP_BUSY");
            if(SourceMaps.supportedSet(value)&&SourceMaps.setMap(value)!=map){cursor.map=null;throw new IOException("HOLDER_SET_BACKING_CHANGED");}
            if(cursor.map.complete)walk.current=null;return;
        }
        if(SourceMaps.supportedList(value)){discoverList(walk,cursor,limit);return;}
        int processed=0;
        while(processed++<limit){
            if(walk.pending.size()>=4096)return;
            Object child;
            if(cursor.keys!=null){
                if(cursor.position==cursor.keys.size()){walk.current=null;return;}
                child=((CompoundTag)value).get(cursor.keys.get(cursor.position));
            }else if(type==ListTag.class){
                ListTag list=(ListTag)value;if(cursor.position>=list.size()){walk.current=null;return;}child=list.get(cursor.position);
            }else if(type.isArray()){
                Object[] array=(Object[])value;if(cursor.position==array.length){walk.current=null;return;}child=array[cursor.position];
            }else{
                if(cursor.position==cursor.fields.size()){walk.current=null;return;}
                var field=cursor.fields.get(cursor.position);if(!field.trySetAccessible())throw new IOException("HOLDER_FIELD_INACCESSIBLE:"+field);
                child=field.get(value instanceof Class<?>?null:value);
            }
            enqueue(walk.pending,child);cursor.position++;
        }
    }
    private void discoverList(HolderWalk walk,HolderCursor cursor,int limit)throws ReflectiveOperationException,IOException {
        Object value=cursor.value;boolean copy=value.getClass()==java.util.concurrent.CopyOnWriteArrayList.class;
        Class<?> bridge=Class.forName("dev.ronova.pro.bootstrap.SourceMapBridge",false,null);
        ReflectiveOperationException[] reflection={null};IOException[] failure={null};
        Runnable read=()->{
            try{
                Object stamp=SourceMaps.listBacking(value);long revision=copy?0:RecoveryReferences.arrayListRevision(value);
                if(!copy&&revision<0)throw new IOException("HOLDER_LIST_REVISION_UNAVAILABLE");
                if(cursor.stamp!=stamp||cursor.revision!=revision){cursor.stamp=stamp;cursor.revision=revision;cursor.position=0;}
                List<?> list=(List<?>)value;int processed=0;
                while(cursor.position<list.size()&&processed++<limit){
                    if(walk.pending.size()>=4096)return;enqueue(walk.pending,list.get(cursor.position));cursor.position++;
                }
                if(cursor.position>=list.size())walk.current=null;
            }catch(ReflectiveOperationException unavailable){reflection[0]=unavailable;}catch(IOException unavailable){failure[0]=unavailable;}
        };
        if(copy){var lock=value.getClass().getDeclaredField("lock");lock.setAccessible(true);synchronized(lock.get(value)){read.run();}}
        else if(!RecoveryReferences.withArrayList(value,false,read))throw new IOException("HOLDER_LIST_BUSY");
        if(reflection[0]!=null)throw reflection[0];if(failure[0]!=null)throw failure[0];
    }
    /** Keep actual record identities through the original DataFixer invocation. */
    public static CompoundTag updateSavedData(net.minecraft.util.datafix.DataFixTypes type,com.mojang.datafixers.DataFixer fixer,CompoundTag input,int before,int after){
        Map<RecoverySources,Map<Object,UUID>> origins;
        GRAPH.writeLock().lock();try{origins=imageOrigins(input,true);}catch(IOException failure){markRelatedGap(input,"MIGRATION_INPUT_UNRESOLVED:"+failure.getMessage());throw new IllegalStateException(failure);}finally{GRAPH.writeLock().unlock();}
        CompoundTag result=type.update(fixer,input,before,after);if(origins.isEmpty()||result==null)return result;
        GRAPH.writeLock().lock();
        try{
            Map<RecoverySources,Map<Object,UUID>> located=imageOrigins(result,false),missing=new IdentityHashMap<>();
            resolvedMigrations(result,located);
            for(var entry:origins.entrySet()){
                Map<Object,UUID> absent=new IdentityHashMap<>(entry.getValue());Map<Object,UUID> preserved=located.get(entry.getKey());
                if(preserved!=null)absent.entrySet().removeIf(record->Objects.equals(record.getValue(),preserved.get(record.getKey())));
                if(!absent.isEmpty())missing.put(entry.getKey(),absent);
            }
            if(!missing.isEmpty())synchronized(MIGRATIONS){
                Map<RecoverySources,Map<Object,UUID>> previous=MIGRATIONS.get(result),merged=new IdentityHashMap<>();
                if(previous!=null)for(var entry:previous.entrySet())merged.put(entry.getKey(),new IdentityHashMap<>(entry.getValue()));
                for(var entry:missing.entrySet())merged.computeIfAbsent(entry.getKey(),ignored->new IdentityHashMap<>()).putAll(entry.getValue());MIGRATIONS.put(result,merged);
            }
            return result;
        }catch(IOException failure){for(RecoverySources owner:origins.keySet()){owner.gap="MIGRATION_OUTPUT_UNRESOLVED:"+failure.getMessage();owner.epoch.incrementAndGet();}throw new IllegalStateException(failure);}
        finally{GRAPH.writeLock().unlock();}
    }
    public static CompoundTag savedDataPart(CompoundTag image,String key){
        CompoundTag result=image.getCompound(key);forwardMigration(image,result);return result;
    }
    private static void forwardMigration(CompoundTag from,CompoundTag to){
        synchronized(MIGRATIONS){
            Map<RecoverySources,Map<Object,UUID>> input=MIGRATIONS.get(from);if(input==null||from==to)return;
            Map<RecoverySources,Map<Object,UUID>> output=MIGRATIONS.computeIfAbsent(to,ignored->new IdentityHashMap<>());
            for(var entry:input.entrySet())output.computeIfAbsent(entry.getKey(),ignored->new IdentityHashMap<>()).putAll(entry.getValue());
        }
    }
    /** Follow the original reader's actual input and returned holder, before cache publication. */
    public static SavedData readSavedData(DimensionDataStorage manager,java.util.function.Function<CompoundTag,SavedData> reader,CompoundTag input){
        ProRuntime runtime=ProRuntime.sourceRuntime(manager);if(runtime==null)return reader.apply(input);
        RecoverySources owner=runtime.recoverySources;CompoundTag consumed=input;List<RecoveryImage.Selection> selections;Set<UUID> unlocated;
        GRAPH.writeLock().lock();
        try {
            // A later real rewrite/copy may restore a previously missing record.
            // Resolve it under the same graph lock as the locations used by this reader.
            boolean pending;synchronized(MIGRATIONS){pending=MIGRATIONS.containsKey(input);}
            if(pending)resolvedMigrations(input,imageOrigins(input,false));
            synchronized(MIGRATIONS){Map<RecoverySources,Map<Object,UUID>> migration=MIGRATIONS.get(input);Map<Object,UUID> records=migration==null?null:migration.get(owner);unlocated=records==null?Set.of():Set.copyOf(records.values());}
            for(UUID subject:unlocated)if(runtime.terminalSource(subject))throw new IllegalStateException("SAVED_DATA_MIGRATED_SOURCE_LOCATION_UNRESOLVED");
            Map<CompoundTag,UUID> identities=new IdentityHashMap<>();
            synchronized(TAGS){for(var entry:TAGS.entrySet())if(entry.getValue().owner==owner&&!AMBIGUOUS.contains(entry.getKey()))identities.put(entry.getKey(),entry.getValue().subject);}
            selections=RecoveryImage.locate(input,identities);Set<UUID> removed=new HashSet<>();
            for(var selection:selections)if(runtime.terminalSource(selection.subject()))removed.add(selection.subject());
            if(!removed.isEmpty()){
                RecoveryImage.Filtered filtered=RecoveryImage.filter(input,selections,removed);consumed=filtered.image();selections=filtered.remaining();
            }
        }catch(IOException failure){
            owner.gap="SAVED_DATA_READER_SOURCE_UNRESOLVED:"+failure.getMessage();owner.epoch.incrementAndGet();throw new IllegalStateException(owner.gap,failure);
        }finally{GRAPH.writeLock().unlock();}
        SavedData result=reader.apply(consumed);
        if(result==null||result==net.minecraftforge.common.util.DummySavedData.DUMMY||selections.isEmpty()&&unlocated.isEmpty())return result;
        Set<UUID> subjects=new HashSet<>(unlocated);for(var selection:selections)subjects.add(selection.subject());
        synchronized(LOADED_HOLDERS){
            Map<RecoverySources,LoadedHolder> origins=LOADED_HOLDERS.get(result);
            if(origins==null){
                origins=new IdentityHashMap<>();LOADED_HOLDERS.put(result,origins);
            }
            LoadedHolder prior=origins.get(owner);if(prior!=null){subjects.addAll(prior.subjects());Set<UUID> merged=new HashSet<>(unlocated);merged.addAll(prior.unlocated());unlocated=Set.copyOf(merged);}
            origins.put(owner,new LoadedHolder(Set.copyOf(subjects),unlocated));
        }
        for(UUID subject:subjects)runtime.recoveryTasks.observed(result,subject);return result;
    }
    private void loadedHolder(Source source,SavedData actual){
        LoadedHolder record;
        synchronized(LOADED_HOLDERS){Map<RecoverySources,LoadedHolder> origins=LOADED_HOLDERS.get(actual);record=origins==null?null:origins.get(this);}
        if(record!=null){boolean changed=source.subjects.addAll(record.subjects());changed|=source.unlocatedReads.addAll(record.unlocated());if(changed){source.mutations.incrementAndGet();epoch.incrementAndGet();}}
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
        if(!savedDataKey(name)) {
            owner.gap="SAVED_DATA_KEY_UNSUPPORTED";return unknownCacheMutation(owner,manager,cache,key,value);
        }
        Path path=directory.toPath().toAbsolutePath().normalize().resolve(name+".dat");
        String code=code(holder.getClass());
        String locator="SAVED_DATA|"+holder.getClass().getName()+"|"+path;
        try(var storageGate=owner.registrationGate.enter()) {
            long generation=0;
            for(Source prior:owner.sources.values())if(Objects.equals(prior.path,path)) {
                generation=Math.max(generation,prior.generation);
                if(prior.holder()!=holder) { prior.generation++;prior.gap="SOURCE_OWNER_REPLACED"; }
            }
            owner.epoch.incrementAndGet();
            Object previous=cache.put(key,value); // Exactly one original mutation; never retried by the observer.
            Source current=owner.sources.get(locator);
            if(current!=null&&current.holder()==holder) { current.cached(cache,name,manager);owner.loadedHolder(current,holder);return previous; }
            if(code==null) { owner.gap="SOURCE_CODE_IDENTITY_UNAVAILABLE";return previous; }
            Source registered=new Source(owner,locator,code,path,holder);
            registered.generation=Math.max(generation+1,owner.generationFloors.getOrDefault(locator,0L));
            registered.cached(cache,name,manager);
            owner.loadedHolder(registered,holder);
            owner.sources.put(locator,registered);
            return previous;
        }
    }
    private static Object unknownCacheMutation(RecoverySources owner,DimensionDataStorage manager,Map<Object,Object> cache,Object key,Object value) {
        try(var storageGate=owner.registrationGate.enter()) {
            for(Source source:owner.sources.values())if((source.manager()==manager||source.cache()==cache)&&source.cacheKey!=null&&source.cacheKey.equals(key)) {
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
        if(runtime==null||directory==null||directory.getClass()!=File.class||!savedDataKey(name))return;
        RecoverySources owner=runtime.recoverySources;
        Path path=directory.toPath().toAbsolutePath().normalize().resolve(name+".dat");
        String code=code(holder.getClass());if(code==null) { owner.gap="SOURCE_CODE_IDENTITY_UNAVAILABLE";return; }
        String locator="SAVED_DATA|"+holder.getClass().getName()+"|"+path;
        try(var storageGate=owner.registrationGate.enter()) {
            if(owner.closed||cache.get(name)!=holder)return;
            Source prior=owner.sources.get(locator);
            if(prior!=null&&prior.holder()==holder&&prior.code.equals(code)) {
                prior.cached(cache,name,manager);owner.loadedHolder(prior,holder);return;
            }
            long generation=Math.max(prior==null?0:prior.generation+1,owner.generationFloors.getOrDefault(locator,0L));
            if(prior!=null) { prior.generation++;prior.gap="SOURCE_OWNER_REPLACED"; }
            Source registered=new Source(owner,locator,code,path,holder);registered.generation=generation;
            registered.cached(cache,name,manager);
            owner.loadedHolder(registered,holder);
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
                owner.sources.put(locator,new Source(owner,locator,code,path,caller));owner.epoch.incrementAndGet();
            }
        }
    }
    /** Called from the real FileOutputStream construction, before a truncate can happen. */
    public static FileOutputStream open(CompoundTag image,File file)throws FileNotFoundException {
        return open(image,file,true);
    }
    public static FileOutputStream open(CompoundTag image,File file,boolean compressed)throws FileNotFoundException {
        Capture capture=null;
        try { capture=begin(image,file,compressed); }
        catch(RuntimeException|LinkageError failure) {
            markRelatedGap(image,"SAVE_OBSERVER_UNAVAILABLE");
            FileNotFoundException refused=new FileNotFoundException("RONOVA_SAVE_PREOPEN_REFUSED:"+failure.getMessage());refused.initCause(failure);throw refused;
        }
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
        writeCaptured(capture);
    }
    private static final class ObservedDataOutput extends DataOutputStream {
        final FileOutputStream actual;
        ObservedDataOutput(OutputStream output){super(output);actual=output instanceof FileOutputStream file?file:null;}
    }
    public static DataOutputStream dataOutput(OutputStream stream){return new ObservedDataOutput(stream);}
    public static void writePlain(CompoundTag image,DataOutput output)throws IOException {
        Capture capture=output instanceof ObservedDataOutput stream&&stream.actual!=null?OPEN.remove(stream.actual):null;
        if(capture==null){NbtIo.write(image,output);return;}
        writeCaptured(capture);
    }
    private static void writeCaptured(Capture capture)throws IOException {
        boolean success=false;
        int depth=CAPTURE_DEPTH.get();CAPTURE_DEPTH.set(depth+1);
        try {
            try(OutputStream output=capture.output()) {capture.planned.writeTo(output);}
            success=true;
        }
        finally { if(depth==0)CAPTURE_DEPTH.remove();else CAPTURE_DEPTH.set(depth);capture.finish(success); }
    }
    public static void outputObserved(CompoundTag image) {
        if(CAPTURE_DEPTH.get()==0)markRelatedGap(image,"UNREGISTERED_OUTPUT_STREAM_OR_ENCODING");
    }
    private static Capture begin(CompoundTag image,File file,boolean compressed) {
        SavedData holder=HOLDER.get();Class<?> caller=holder==null?caller():holder.getClass();
        Map<CompoundTag,UUID> identities=new IdentityHashMap<>();RecoverySources owner=null;
        synchronized(TAGS) {
            // Only actual identities in this image matter; no class/name/UUID similarity matching.
            try {
                var all=new IdentityHashMap<CompoundTag,UUID>();TAGS.forEach((tag,origin)->all.put(tag,origin.subject));
                for(var selection:RecoveryImage.locate(image,all)) {
                    CompoundTag tag=(CompoundTag)RecoveryImage.resolve(image,selection.path());
                    Origin origin=TAGS.get(tag);
                    if(owner!=null&&owner!=origin.owner)throw new IllegalStateException("CROSS_REALM_SAVE");
                    owner=origin.owner;identities.put(tag,origin.subject);
                }
            } catch(IOException failure) { markRelatedGap(image,failure.getMessage());throw new IllegalStateException(failure); }
        }
        for(ProRuntime candidate:ProRuntime.sourceRuntimes()) {
            ProRuntime.Subject group=candidate.groupSubject(holder==null?caller:holder);
            if(group==null)continue;
            if(owner!=null&&owner!=candidate.recoverySources)throw new IllegalStateException("CROSS_REALM_GROUP_SAVE");
            owner=candidate.recoverySources;
        }
        if(owner==null&&file!=null&&file.getClass()==File.class) {
            Path knownPath=file.toPath().toAbsolutePath().normalize();
            for(ProRuntime candidate:ProRuntime.sourceRuntimes())for(Source source:candidate.recoverySources.registered())
                if(Objects.equals(source.path,knownPath)) {
                    if(owner!=null&&owner!=source.owner)throw new IllegalStateException("CROSS_REALM_REGISTERED_PATH");
                    owner=source.owner;
                }
        }
        if(owner==null)return null;
        if(owner.closed)throw new IllegalStateException("REGISTERED_SAVE_OWNER_CLOSED");
        if(file.getClass()!=File.class)throw new IllegalStateException("OPAQUE_FILE_PATH");
        Path path=file.toPath().toAbsolutePath().normalize();
        if(caller==null)throw new IllegalStateException("STATIC_SAVE_CALLER_UNOBSERVED");
        String code=code(caller);
        if(code==null)throw new IllegalStateException("SOURCE_CODE_IDENTITY_UNAVAILABLE");
        // Static caller identity is recorded, but memory-holder disposal still needs its registered adapter.
        Object holderIdentity=holder==null?caller:holder;
        String locator=(holder==null?"STATIC|":"SAVED_DATA|")+caller.getName()+"|"+path;
        Source source=owner.sources.get(locator);
        if(source==null) {
            Source fresh=new Source(owner,locator,code,path,holderIdentity);
            Source prior=owner.sources.putIfAbsent(locator,fresh);source=prior==null?fresh:prior;
        }
        for(UUID subject:source.unlocatedReads)if(owner.runtime.terminalSource(subject))throw new IllegalStateException("SAVE_MIGRATED_SOURCE_LOCATION_UNRESOLVED");
        final List<RecoveryImage.Selection> selections;
        final String semantic;
        final RecoveryImage.Encoded planned;
        List<RecoveryImage.Selection> observed;
        int depth=CAPTURE_DEPTH.get();CAPTURE_DEPTH.set(depth+1);
        try {
            observed=RecoveryImage.locate(image,identities);Set<UUID> removed=new HashSet<>();
            for(var selection:observed)if(owner.runtime.terminalSource(selection.subject()))removed.add(selection.subject());
            RecoveryImage.Filtered filtered=RecoveryImage.filter(image,observed,removed);
            selections=filtered.remaining();semantic=RecoveryImage.semanticHash(filtered.image());planned=RecoveryImage.encode(filtered.image(),compressed);
        }catch(IOException failure) {source.gap=failure.getMessage();owner.epoch.incrementAndGet();throw new IllegalStateException(failure);}
        finally {if(depth==0)CAPTURE_DEPTH.remove();else CAPTURE_DEPTH.set(depth);}
        try(var storageGate=source.gate.enter()) {
            ProRuntime.Subject group=owner.runtime.groupSubject(holderIdentity);
            if(group!=null)source.subjects.add(group.id);
            if(group!=null&&group.terminal&&observed.stream().noneMatch(selection->group.members.contains(selection.subject()))) {
                planned.close();source.gap="GROUP_SAVE_RECORD_OWNERSHIP_UNRESOLVED";
                throw new IllegalStateException(source.gap);
            }
            owner.epoch.incrementAndGet();source.generation=Math.max(source.generation,owner.generationFloors.getOrDefault(source.locator,0L))+1;source.writers++;
            for(RecoveryImage.Selection selection:observed)source.subjects.add(selection.subject());
            if(source.holder()!=holderIdentity||!source.code.equals(code))source.gap="SOURCE_OWNER_REPLACED";
            else source.gap="SAVE_IN_PROGRESS";
            for(Source other:owner.sources.values())if(other!=source&&Objects.equals(other.path,path)) {
                other.generation++;other.gap="PATH_WRITTEN_BY_OTHER_SOURCE";
            }
            if(!owner.bound(source)) {source.writers--;planned.close();throw new IllegalStateException("SAVE_SOURCE_BINDING_CHANGED");}
            return new Capture(source,source.generation,selections,semantic,planned);
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
            String bytesHash=RecoveryImage.hash(input);
            String live=definition(type,true);if(live==null)return null;
            String image=live.substring(0,live.indexOf(':'));
            return RecoveryImage.hash(("source-definition/canonical-v2\n"+bytesHash+"\n"+image).getBytes(java.nio.charset.StandardCharsets.UTF_8));
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
            while(!queue.isEmpty()) {
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
        final RecoveryImage.Encoded planned;
        final RecoveryImage.Builder bytes=new RecoveryImage.Builder();
        FileOutputStream stream;String identity,error="";boolean closed,finished;
        Capture(Source source,long generation,List<RecoveryImage.Selection> selections,String semantic,RecoveryImage.Encoded planned) {
            this.source=source;this.generation=generation;this.selections=selections;this.semantic=semantic;this.planned=planned;
        }
        OutputStream output() {
            return new FilterOutputStream(stream) {
                private void retain(byte[] data,int off,int len)throws IOException {
                    if(!error.isEmpty())return;
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
                try(RecoveryImage.Encoded encoded=bytes.finish()) {
                    if(!semantic.equals(RecoveryImage.semanticHash(RecoveryImage.decode(encoded))))throw new IOException("ENCODING_OR_IMAGE_CHANGED_DURING_SAVE");
                    next=new Snapshot(source,generation,identity,encoded,selections);
                }
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
                    bytes.close();planned.close();source.owner.epoch.incrementAndGet();
                }
            }
            if(retired!=null)retired.release();if(next!=null)next.release();
        }
    }
    void beginStop() {
        synchronized(loads) {
            // Producers use the same close barrier. Only already accepted events
            // remain here, and a failed current event stays owned for the report.
            drainLoads(Integer.MAX_VALUE);
            drainGroupRoots(Integer.MAX_VALUE);
            try(var storageGate=registrationGate.enter()) { closed=true;epoch.incrementAndGet(); }
        }
        SourceMaps.close(this);
    }
    @Override public void close()throws IOException {
        closed=true;
        if(pendingLoad!=null||!loads.isEmpty())throw new IOException("SOURCE_LOAD_BINDING_EVENTS_PENDING");
        if(pendingGroupRoot!=null||!groupRoots.isEmpty())throw new IOException("GROUP_ROOT_BINDING_EVENTS_PENDING");
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
            if(source.snapshot!=null)source.snapshot.release();source.nestedRetained=null;source.nestedCarrier=null;source.gap="SOURCE_UNLOADED";
        }
        // Do not discard sources/handles whose release has not been observed.
        if(!pendingCloses.isEmpty())throw new IOException("OUTPUT_RESOURCES_RETAINED_FOR_RETIREMENT");
        synchronized(LOADED_HOLDERS){
            var iterator=LOADED_HOLDERS.entrySet().iterator();while(iterator.hasNext()){var entry=iterator.next();entry.getValue().remove(this);if(entry.getValue().isEmpty())iterator.remove();}
        }
        synchronized(MIGRATIONS){
            var iterator=MIGRATIONS.entrySet().iterator();while(iterator.hasNext()){var entry=iterator.next();entry.getValue().remove(this);if(entry.getValue().isEmpty())iterator.remove();}
        }
    }
}
