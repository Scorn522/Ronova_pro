package dev.ronova.pro;

import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/** Exact memory-map backends. An unavailable observer never becomes an ordinary overwrite. */
final class SourceMaps {
    private record Owner(java.lang.ref.WeakReference<RecoverySources.Source> source,Field field,
                         java.lang.ref.WeakReference<Object> map) { }
    private static final Map<Object,List<Owner>> OWNERS=Collections.synchronizedMap(new WeakIdentityMap<>());
    private static final Map<Object,List<Owner>> HOLDERS=Collections.synchronizedMap(new WeakIdentityMap<>());
    private record RetiringField(Object holder,Field field,Object expected) { }
    private static final ThreadLocal<RetiringField> RETIRING_FIELD=new ThreadLocal<>();
    private record RetiringList(Object list,Object backing,Object[] before){}
    private static final ThreadLocal<RetiringList> RETIRING_LIST=new ThreadLocal<>();
    private static final ThreadLocal<Set<Object>> NESTED_OBSERVATIONS=new ThreadLocal<>();
    /** Exact JDK removal may shift another selected element before clearing the final slot. */
    static void removeListIndex(List<?> list,int index)throws ReflectiveOperationException{
        if(!supportedList(list))throw new IllegalArgumentException("ACTUAL_SOURCE_LIST_REQUIRED");
        RetiringList previous=RETIRING_LIST.get();RETIRING_LIST.set(new RetiringList(list,listBacking(list),list.toArray()));
        try{list.remove(index);}finally{if(previous==null)RETIRING_LIST.remove();else RETIRING_LIST.set(previous);}
    }
    private static final Object RAW_RANGE=new Object();
    private static final class Publications {
        static final Method OBSERVE=method("observeSourceHolder",Object.class),CARRIER=method("observeSourceCarrier",Object.class),HOLD=method("holdSourcePublication",Object.class,Object.class),CHANGED=method("recoveredSourceChange",Object.class);
        private static Method method(String name,Class<?>... parameters){
            try{return Class.forName("dev.ronova.pro.bootstrap.TaskBridge",false,null).getMethod(name,parameters);}
            catch(ReflectiveOperationException unavailable){throw new ExceptionInInitializerError(unavailable);}
        }
    }
    static void retireField(ProRuntime runtime,Field field,Object receiver,Object expected,Action action)throws ReflectiveOperationException {
        Object holder=receiver==null?field.getDeclaringClass():receiver;ProRuntime.Subject group=runtime.groupSubject(holder);
        if(group==null||!group.terminal)throw new IllegalStateException("GROUP_FIELD_AUTHORITY_CHANGED");
        RetiringField previous=RETIRING_FIELD.get();RETIRING_FIELD.set(new RetiringField(holder,field,expected));
        try {action.run();}finally {if(previous==null)RETIRING_FIELD.remove();else RETIRING_FIELD.set(previous);}
    }
    private static volatile Method entries,peek,withMap,revision,state,bridgeState;
    private SourceMaps() { }
    static boolean hashBacked(Object map) {return map!=null&&(map.getClass()==HashMap.class||map.getClass()==LinkedHashMap.class);}
    static boolean primitiveMap(Object map){return map!=null&&(map.getClass()==it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap.class
            ||map.getClass()==it.unimi.dsi.fastutil.ints.Int2ObjectLinkedOpenHashMap.class
            ||map.getClass()==it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap.class);}
    static boolean supported(Object map) { return hashBacked(map)||primitiveMap(map)||map!=null&&map.getClass()==ConcurrentHashMap.class; }
    static boolean serialized(Object map){return hashBacked(map)||primitiveMap(map);}
    static boolean fastList(Object value){return value!=null&&value.getClass()==it.unimi.dsi.fastutil.objects.ObjectArrayList.class;}
    static boolean fastSet(Object value){return value!=null&&value.getClass()==it.unimi.dsi.fastutil.objects.ObjectOpenHashSet.class;}
    static boolean scopedFastutil(Object value){return primitiveMap(value)||fastList(value)||fastSet(value);}
    static boolean scopedList(Object value){return value!=null&&(value.getClass()==ArrayList.class||fastList(value));}
    static boolean supportedList(Object value){return scopedList(value)||value!=null&&value.getClass()==java.util.concurrent.CopyOnWriteArrayList.class;}
    static boolean supportedKey(Object key){return key==null||key instanceof String||key instanceof UUID||key instanceof Integer||key instanceof Long;}
    static String encodeKey(Object key){if(!supportedKey(key))throw new IllegalArgumentException("SOURCE_KEY_TYPE");return key==null?"null:":key.getClass().getName()+":"+key;}
    static Object decodeKey(String encoded){
        int split=encoded.indexOf(':');if(split<0)throw new IllegalArgumentException("SOURCE_KEY");
        String value=encoded.substring(split+1);
        return switch(encoded.substring(0,split)){
            case "null" -> {if(!value.isEmpty())throw new IllegalArgumentException("SOURCE_NULL_KEY");yield null;}
            case "java.lang.String" -> value;case "java.util.UUID" -> UUID.fromString(value);
            case "java.lang.Integer" -> Integer.valueOf(value);case "java.lang.Long" -> Long.valueOf(value);
            default -> throw new IllegalArgumentException("SOURCE_KEY_TYPE");
        };
    }
    private static boolean primitiveReady(Object map){
        try{
            Class<?> boundary=Class.forName("dev.ronova.pro.agent.FastutilIndexBoundary",false,ClassLoader.getSystemClassLoader());
            Method state=boundary.getMethod("state",String.class);String name=map.getClass().getName().replace('.','/');
            for(String suffix:fastList(map)?List.of(""):fastSet(map)?List.of("","$SetIterator"):List.of("","$MapIterator","$MapEntry")){
                Class.forName(map.getClass().getName()+suffix,false,map.getClass().getClassLoader());
                if(!"INSTALLED".equals(state.invoke(null,name+suffix)))return false;
            }
            if(fastList(map)){
                for(String view:List.of("it.unimi.dsi.fastutil.objects.AbstractObjectList$ObjectSubList","it.unimi.dsi.fastutil.objects.ObjectArrayList$1","it.unimi.dsi.fastutil.objects.ObjectIterators$AbstractIndexBasedListIterator")){
                    Class.forName(view,false,map.getClass().getClassLoader());if(!"INSTALLED".equals(state.invoke(null,view.replace('.','/'))))return false;
                }
            }
            return available();
        }catch(ReflectiveOperationException|LinkageError unavailable){return false;}
    }
    static boolean supportedSet(Object set) {return fastSet(set)||set!=null&&(set.getClass()==HashSet.class||set.getClass()==LinkedHashSet.class);}
    static boolean referenceContainer(Object value){return supported(value)||supportedSet(value)||fastList(value)||value instanceof Object[]
            ||value!=null&&(value.getClass()==ArrayList.class||value.getClass()==java.util.concurrent.CopyOnWriteArrayList.class
            ||value.getClass()==java.util.concurrent.atomic.AtomicReference.class);}
    static boolean observed(Object value){return value!=null&&OWNERS.containsKey(value);}
    static boolean replacementCovered(RecoverySources.Source source,Field field,Object value){
        if(Modifier.isFinal(field.getModifiers()))return true;
        if(!available()||!source.owner.liveRegistration(source)||scopedFastutil(value)&&!primitiveReady(value))return false;
        for(Owner owner:HOLDERS.getOrDefault(source.holder(),List.of()))
            if(owner.source.get()==source&&owner.field.equals(field))return value==null||referenceContainer(value)
                    ||value instanceof net.minecraft.world.entity.Entity||value instanceof net.minecraft.nbt.CompoundTag
                    ||RecoveryReferences.ownedTarget(value,source.owner.runtimeOwner(),true)!=null||RecoveryReferences.emptyWrapper(value,source.owner.runtimeOwner())||RecoveryReferences.immutableScalar(value)||RecoveryReferences.nestedHolder(value);
        return false;
    }
    static boolean disposedField(RecoverySources.Source source,Field field)throws IllegalAccessException {
        if(source.nestedAbsent)return source.owner.current(source);
        if(!source.groupDisposed||!source.cleared(field)||!source.owner.current(source))return false;
        Object holder=source.holder();
        return holder==null||field.get(holder instanceof Class<?>?null:holder)==null;
    }
    static Object setMap(Object set)throws ReflectiveOperationException {
        if(!supportedSet(set))throw new IllegalArgumentException("ACTUAL_HASHSET_REQUIRED");
        if(fastSet(set))return set;
        Object map=field(set,"map");if(!hashBacked(map))throw new IllegalStateException("ACTUAL_HASHSET_MAP_UNAVAILABLE");return map;
    }
    static void observe(Object map,RecoverySources.Source source,Field field) {
        if(!supported(map))return;
        if(primitiveMap(map))try{Publications.CARRIER.invoke(null,map);}catch(ReflectiveOperationException unavailable){source.gap="SOURCE_INDEX_SCOPE_UNAVAILABLE";return;}
        if(map.getClass()==ConcurrentHashMap.class)try {
            Class.forName("dev.ronova.pro.bootstrap.SourceMapBridge",false,null).getMethod("observeConcurrent",Object.class).invoke(null,map);
        } catch(ReflectiveOperationException unavailable) { /* Disposition remains available; publication coverage is reported below. */ }
        observeField(source,field,map);
    }
    /** Retain the actual declared slot even after its container has been cleared or collected. */
    static void observeField(RecoverySources.Source source,Field field,Object value) {
        Object holder=source.holder();if(holder==null)return;
        synchronized(HOLDERS) {
            for(Owner owner:HOLDERS.getOrDefault(holder,List.of()))
                if(owner.source.get()==source&&owner.field.equals(field)&&owner.map.get()==value&&source.nestedRegistrationGaps.isEmpty())return;
        }
        Owner registered=new Owner(new java.lang.ref.WeakReference<>(source),field,new java.lang.ref.WeakReference<>(value));
        try{Publications.OBSERVE.invoke(null,holder);}catch(ReflectiveOperationException unavailable){source.gap="SOURCE_HOLDER_SCOPE_UNAVAILABLE";return;}
        if(referenceContainer(value))
            try{Publications.CARRIER.invoke(null,value);}catch(ReflectiveOperationException unavailable){source.gap="SOURCE_CARRIER_SCOPE_UNAVAILABLE";return;}
        Object backend=value;
        if(supportedSet(value))try {backend=setMap(value);Publications.CARRIER.invoke(null,backend);}
        catch(ReflectiveOperationException unavailable){source.gap="SOURCE_SET_BACKING_UNAVAILABLE";backend=null;}
        synchronized(OWNERS) {
            if(supported(backend)||fastSet(backend)||fastList(backend)||backend!=null&&(backend.getClass()==ArrayList.class
                    ||backend.getClass()==java.util.concurrent.CopyOnWriteArrayList.class
                    ||backend.getClass()==java.util.concurrent.atomic.AtomicReference.class||backend instanceof Object[])) {
                var next=new ArrayList<Owner>();
                for(Owner owner:OWNERS.getOrDefault(backend,List.of())) {
                    var prior=owner.source.get();
                    if(prior!=null&&!(prior==source&&owner.field.equals(field)))next.add(owner);
                }
                next.add(registered);OWNERS.put(backend,List.copyOf(next));
            }
            if(supportedSet(value)){
                var next=new ArrayList<Owner>();
                for(Owner owner:OWNERS.getOrDefault(value,List.of()))if(owner.source.get()!=null&&!(owner.source.get()==source&&owner.field.equals(field)))next.add(owner);
                next.add(registered);OWNERS.put(value,List.copyOf(next));
            }
            synchronized(HOLDERS) {
                var holders=new ArrayList<Owner>();
                for(Owner owner:HOLDERS.getOrDefault(holder,List.of())) {
                    var prior=owner.source.get();
                    if(prior!=null&&!(prior==source&&owner.field.equals(field)))holders.add(owner);
                }
                holders.add(registered);HOLDERS.put(holder,List.copyOf(holders));
            }
            observeBacking(registered,value);
        }
        observeNested(source,field,value);
    }
    private static void observeNested(RecoverySources.Source source,Field field,Object carrier){
        String location=field.getDeclaringClass().getName()+"#"+field.getName();
        if(carrier==null){source.nestedRegistrationGaps.remove(location);return;}
        Set<Object> seen=NESTED_OBSERVATIONS.get();boolean outer=seen==null;
        if(outer){seen=Collections.newSetFromMap(new IdentityHashMap<>());NESTED_OBSERVATIONS.set(seen);}
        if(!seen.add(carrier))return;
        try{
            if(RecoveryReferences.nestedHolder(carrier))observeChild(source,field,carrier,"FIELD",null,carrier);
            else if(supported(carrier)){
                List<Map.Entry<Object,Object>> rows=entries(carrier);if(rows==null)throw new IllegalAccessException("NESTED_MAP_BUSY");
                for(var row:rows)if(supportedKey(row.getKey()))observeChild(source,field,carrier,"MAP",row.getKey(),row.getValue());
            }else if(supportedSet(carrier)){
                List<Map.Entry<Object,Object>> rows=entries(setMap(carrier));if(rows==null)throw new IllegalAccessException("NESTED_SET_BUSY");
                for(var row:rows){Object child=row.getKey();if(child!=null)observeChild(source,field,carrier,"SET",child.getClass().getName(),child);}
            }else if(carrier.getClass()==java.util.concurrent.atomic.AtomicReference.class)
                observeChild(source,field,carrier,"ATOMIC",null,((java.util.concurrent.atomic.AtomicReference<?>)carrier).get());
            else if(carrier instanceof Object[] values){for(int i=0;i<values.length;i++)observeChild(source,field,carrier,"ARRAY",i,values[i]);}
            else if(supportedList(carrier)){
                Object[][] snapshot={null};
                if(scopedList(carrier)){if(!RecoveryReferences.withArrayList(carrier,false,()->snapshot[0]=((List<?>)carrier).toArray()))throw new IllegalAccessException("NESTED_LIST_BUSY");}
                else snapshot[0]=((List<?>)carrier).toArray();
                for(int i=0;i<snapshot[0].length;i++)observeChild(source,field,carrier,"LIST",i,snapshot[0][i]);
            }
            source.nestedRegistrationGaps.remove(location);
        }catch(ReflectiveOperationException unavailable){source.nestedRegistrationGaps.add(location);}
        finally{if(outer)NESTED_OBSERVATIONS.remove();}
    }
    private static void observeChild(RecoverySources.Source parent,Field field,Object carrier,String kind,Object key,Object child)throws ReflectiveOperationException{
        if(!RecoveryReferences.nestedHolder(child)||RecoveryReferences.ownedTarget(child,parent.owner.runtimeOwner(),true)!=null)return;
        RecoverySources.Source nested=parent.owner.nestedSource(parent,field,kind,key,child,carrier);
        if(nested==null)throw new IllegalAccessException("NESTED_SOURCE_BINDING_PENDING");
        if(nested==parent)return;
        for(RecoverySources.Source ancestor=parent;ancestor!=null;ancestor=ancestor.nestedParent)if(ancestor==nested)return;
        for(Class<?> type=child.getClass();type!=null&&type!=Object.class;type=type.getSuperclass())for(Field member:type.getDeclaredFields())
            if(!Modifier.isStatic(member.getModifiers())&&!member.getType().isPrimitive()){
                if(!member.trySetAccessible())throw new IllegalAccessException("NESTED_FIELD_INACCESSIBLE");
                observeField(nested,member,member.get(child));
            }
    }
    static Object listBacking(Object value)throws ReflectiveOperationException{
        if(fastList(value))return field(value,"a");
        if(value!=null&&value.getClass()==ArrayList.class)return field(value,"elementData");
        if(value!=null&&value.getClass()==java.util.concurrent.CopyOnWriteArrayList.class)return field(value,"array");
        return null;
    }
    static Object[] valueBacking(Object value)throws ReflectiveOperationException{
        if(value instanceof Object[] array)return array;
        Object backing=fastSet(value)?field(value,"key"):primitiveMap(value)?field(value,"value"):listBacking(value);
        if(backing instanceof Object[] array)return array;
        throw new IllegalAccessException("NESTED_BACKING_ARRAY_UNAVAILABLE");
    }
    private static void observeBacking(Owner registered,Object value){
        try{
            var arrays=new ArrayList<Object>();Object list=listBacking(value);if(list!=null)arrays.add(list);
            if(fastSet(value))arrays.add(field(value,"key"));
            if(primitiveMap(value))for(String name:List.of("key","value","link"))try{Object array=field(value,name);if(array!=null)arrays.add(array);}catch(NoSuchFieldException absent){}
            for(Object array:arrays){
            Publications.CARRIER.invoke(null,array);
            synchronized(OWNERS){
                var next=new ArrayList<Owner>();
                for(Owner owner:OWNERS.getOrDefault(array,List.of()))if(owner.source.get()!=null
                        &&!(owner.source.get()==registered.source.get()&&owner.field.equals(registered.field)))next.add(owner);
                next.add(registered);OWNERS.put(array,List.copyOf(next));
            }
            }
        }catch(ReflectiveOperationException unavailable){var source=registered.source.get();if(source!=null)source.gap="SOURCE_LIST_BACKING_UNAVAILABLE";}
    }
    static boolean currentSource(Object map,RecoverySources sources,UUID subject) {
        List<Owner> owners=OWNERS.get(map);if(owners==null)return false;
        for(Owner owner:owners) {
            var source=owner.source.get();if(source==null||source.owner!=sources||Collections.disjoint(source.subjects,sources.runtimeOwner().groupTargets(subject))||!sources.liveRegistration(source))continue;
            Object holder=source.holder();if(holder==null)continue;
            if(currentOwner(owner,map))return true;
        }
        return false;
    }
    static boolean staticHolderDenied(Class<?> holder,String name,Object next) {
        return holderDenied(holder,null,name,null,next,null,null,true);
    }
    static boolean staticHolderOffsetDenied(Class<?> holder,long offset,Object next,Object unsafe,Method offsetMethod) {
        return holderDenied(holder,null,null,offset,next,unsafe,offsetMethod,true);
    }
    static boolean instanceHolderDenied(Object holder,Class<?> declaring,String name,Object next) {
        return holderDenied(holder,declaring,name,null,next,null,null,false);
    }
    static boolean instanceHolderOffsetDenied(Object holder,long offset,Object next,Object unsafe,Method offsetMethod) {
        if(observed(holder))try{
            for(Class<?> type=holder.getClass();type!=null&&type!=Object.class;type=type.getSuperclass())for(Field field:type.getDeclaredFields())
                if(!Modifier.isStatic(field.getModifiers())&&(Long)offsetMethod.invoke(unsafe,field)==offset
                        &&carrierFieldDenied(holder,type,field.getName(),next))return true;
        }catch(ReflectiveOperationException unavailable){return true;}
        return holderDenied(holder,null,null,offset,next,unsafe,offsetMethod,false);
    }
    static boolean instanceHolderRangeDenied(Object holder,long offset,long length,Object unsafe,Method offsetMethod) {
        if(length<=0)return false;
        final long end;try {end=Math.addExact(offset,length);}catch(ArithmeticException overflow){return true;}
        for(Owner owner:HOLDERS.getOrDefault(holder,List.of())) {
            var source=owner.source.get();Field field=owner.field;
            if(source==null||source.holder()!=holder||Modifier.isStatic(field.getModifiers()))continue;
            long at;try {at=(Long)offsetMethod.invoke(unsafe,field);}catch(ReflectiveOperationException unavailable){source.gap="SOURCE_FIELD_OFFSET_UNAVAILABLE";return true;}
            if(offset<at+8&&end>at&&holderDenied(holder,field.getDeclaringClass(),field.getName(),null,RAW_RANGE,null,null,false))return true;
        }
        return false;
    }
    private static boolean holderDenied(Object holder,Class<?> declaring,String name,Long offset,Object next,Object unsafe,Method offsetMethod,boolean statik) {
        RetiringField retiring=RETIRING_FIELD.get();
        for(Owner owner:HOLDERS.getOrDefault(holder,List.of())) {
            var source=owner.source.get();Field field=owner.field;
            if(source==null||source.holder()!=holder||!source.owner.liveRegistration(source)||Modifier.isStatic(field.getModifiers())!=statik)continue;
            if(statik&&field.getDeclaringClass()!=holder)continue;
            if(declaring!=null&&field.getDeclaringClass()!=declaring)continue;
            if(name!=null&&!field.getName().equals(name))continue;
            if(offset!=null)try {if((Long)offsetMethod.invoke(unsafe,field)!=offset.longValue())continue;}
                catch(ReflectiveOperationException unavailable){source.gap="SOURCE_STATIC_OFFSET_UNAVAILABLE";return true;}
            boolean terminal=false;ProRuntime runtime=source.owner.runtimeOwner();
            for(UUID subject:source.subjects)if(runtime.terminalSource(subject)) {terminal=true;break;}
            if(!terminal)continue;
            try {
                Object actual=field.get(statik?null:holder);
                if(next==actual)continue;
                if(retiring!=null&&retiring.holder==holder&&retiring.field.equals(field)&&retiring.expected==actual&&next==null)continue;
                if(next==RAW_RANGE)return true;
                if(next==null){if(!holdPublication(holder,null))return true;continue;}
                if(referenceContainer(next)||RecoveryReferences.nestedHolder(next)){
                    // Drain incoming writers before inspecting the full carrier. Ownership is
                    // attached only by changed() after successful readback, under the same gate.
                    if(primitiveMap(next)&&!primitiveReady(next))return true;
                    if(supportedSet(next)&&!holdPublication(holder,next))return true;
                    Object backend=supportedSet(next)?setMap(next):next;
                    if(!holdPublication(holder,backend))return true;
                    Object backing=listBacking(backend);
                    if(backing!=null&&!holdPublication(holder,backing)||incomingDenied(source,holder,backend))return true;
                    continue;
                }
                if(deniesValue(source,holder,next))return true;
                if(RecoveryReferences.immutableScalar(next)||next instanceof net.minecraft.world.entity.Entity
                        ||RecoveryReferences.ownedTarget(next,runtime,true)!=null||RecoveryReferences.emptyWrapper(next,runtime))continue;
                return true;
            }
            catch(ReflectiveOperationException unavailable){source.gap="SOURCE_HOLDER_PUBLICATION_UNAVAILABLE";return true;}
        }
        return false;
    }
    static boolean staticHolderRangeDenied(Class<?> holder,String name){return holderDenied(holder,null,name,null,RAW_RANGE,null,null,true);}
    private static boolean holdPublication(Object holder,Object incoming)throws ReflectiveOperationException{
        return Boolean.TRUE.equals(Publications.HOLD.invoke(null,holder,incoming));
    }
    private static boolean incomingDenied(RecoverySources.Source source,Object holder,Object incoming)throws ReflectiveOperationException{
        ArrayDeque<Object> pending=new ArrayDeque<>();Set<Object> seen=Collections.newSetFromMap(new IdentityHashMap<>());pending.add(incoming);
        while(!pending.isEmpty()){
            Object value=pending.removeFirst();if(!seen.add(value))continue;
            boolean leaf=RecoveryReferences.immutableScalar(value)||value instanceof net.minecraft.world.entity.Entity||value instanceof net.minecraft.nbt.Tag;
            if(!leaf){
                if(!referenceContainer(value)&&!RecoveryReferences.nestedHolder(value))return true;
                if(scopedFastutil(value)&&!primitiveReady(value)||!holdPublication(holder,value))return true;
                if(supportedSet(value)&&!holdPublication(holder,setMap(value)))return true;
                Object backing=listBacking(value);if(backing!=null&&!holdPublication(holder,backing))return true;
                if(primitiveMap(value)||fastSet(value))for(String name:primitiveMap(value)?List.of("key","value","link"):List.of("key")){
                    try{Object array=field(value,name);if(array!=null&&!holdPublication(holder,array))return true;}
                    catch(NoSuchFieldException absent){if(!name.equals("link"))throw absent;}
                }
            }
            if(deniesTarget(source,value))return true;
            if(leaf)continue;
            if(value instanceof Map<?,?> map){for(var entry:map.entrySet()){if(entry.getKey()!=null)pending.add(entry.getKey());if(entry.getValue()!=null)pending.add(entry.getValue());}}
            else if(value instanceof Collection<?> list){for(Object child:list)if(child!=null)pending.add(child);}
            else if(value instanceof Object[] array){for(Object child:array)if(child!=null)pending.add(child);}
            else if(value instanceof java.util.concurrent.atomic.AtomicReference<?> atomic){Object child=atomic.get();if(child!=null)pending.add(child);}
            else for(Class<?> type=value.getClass();type!=null&&type!=Object.class;type=type.getSuperclass())for(Field field:type.getDeclaredFields())
                if(!Modifier.isStatic(field.getModifiers())&&!field.getType().isPrimitive()){
                    if(!field.trySetAccessible())return true;Object child=field.get(value);if(child!=null)pending.add(child);
                }
        }
        return false;
    }
    private static boolean deniesValue(RecoverySources.Source source,Object receiver,Object value){
        if(value==null)return false;
        boolean terminal=false;for(UUID subject:source.subjects)if(source.owner.runtimeOwner().terminalSource(subject)){terminal=true;break;}
        if(!terminal)return false;
        if(referenceContainer(value)||RecoveryReferences.nestedHolder(value)){
            try{return incomingDenied(source,receiver,value);}
            catch(ReflectiveOperationException unavailable){return true;}
        }
        return deniesTarget(source,value);
    }
    private static boolean deniesTarget(RecoverySources.Source source,Object value){
        ProRuntime runtime=source.owner.runtimeOwner();ProRuntime.Subject group=runtime.groupSubject(value);
        if(group!=null&&group.terminal&&!Collections.disjoint(source.subjects,runtime.groupTargets(group.id)))return true;
        Object target=RecoveryReferences.ownedTarget(value,runtime,true);
        UUID subject=target instanceof net.minecraft.world.entity.Entity?runtime.recoveryTasks.subjectOf(target):
                target instanceof net.minecraft.nbt.CompoundTag tag?RecoverySources.subject(source.owner,tag):null;
        return subject!=null&&!Collections.disjoint(source.subjects,runtime.groupTargets(subject))&&runtime.terminalSource(subject);
    }
    /** Actual atomic/list/set carrier fields also need the source guard at their final store. */
    static boolean carrierFieldDenied(Object receiver,Class<?> declaring,String name,Object next){
        if(receiver==null||!observed(receiver))return false;
        if(scopedFastutil(receiver)&&Set.of("a","key","value","link","n","mask","size","first","last","containsNullKey","containsNull","maxFill","minN","f","wrapped").contains(name)){
            try{Object prior=slot(declaring,name).get(receiver);if(prior==next||prior instanceof Number&&prior.equals(next)||prior instanceof Boolean&&prior.equals(next))return false;}
            catch(ReflectiveOperationException unavailable){return true;}
            if(BackingPolicy.originalWriter(receiver))return false;
            return terminalOwner(receiver);
        }
        if(receiver.getClass()==java.util.concurrent.atomic.AtomicReference.class&&declaring==receiver.getClass()&&name.equals("value"))return denies(receiver,null,next);
        if(receiver.getClass()==java.util.concurrent.CopyOnWriteArrayList.class&&declaring==receiver.getClass()&&name.equals("array")){
            if(!(next instanceof Object[] values))return true;
            try{
                Object[] before=(Object[])listBacking(receiver);
                Map<Object,Integer> retained=new IdentityHashMap<>();for(Object value:before)retained.merge(value,1,Integer::sum);
                for(Object value:values)if(denies(receiver,null,value)){
                    int count=retained.getOrDefault(value,0);if(count==0)return true;retained.put(value,count-1);
                }
            }catch(ReflectiveOperationException unavailable){return true;}
        }
        if(supportedSet(receiver)&&declaring==HashSet.class&&name.equals("map")){
            try{if(next==setMap(receiver))return false;}catch(ReflectiveOperationException unavailable){return true;}
            // HashSet's private carrier is changed only by construction, clone and readObject.
            // A live registered set cannot silently detach its observed node backend.
            return true;
        }
        return false;
    }
    static void changed(Object map) {
        // A holder write can replace a whole container. Read back the actual slot;
        // failed CAS proposals and detached old containers never acquire ownership.
        for(Owner owner:HOLDERS.getOrDefault(map,List.of())) {
            var source=owner.source.get();
            if(source==null||source.holder()!=map||!source.owner.liveRegistration(source))continue;
            try {
                Object actual=owner.field.get(map instanceof Class<?>?null:map);
                if(actual!=owner.map.get()) {
                    if(supported(actual))observe(actual,source,owner.field);
                    else observeField(source,owner.field,actual);
                }
                source.owner.mapChanged(source);
            }catch(IllegalAccessException unavailable){source.gap="SOURCE_HOLDER_READBACK_UNAVAILABLE";source.owner.mapChanged(source);}
        }
        List<Owner> owners=OWNERS.get(map);if(owners==null)return;
        for(Owner owner:owners) {
            var source=owner.source.get();
            if(currentOwner(owner,map)){
                Object carrier=owner.map.get();if(map!=carrier&&scopedFastutil(carrier)&&BackingPolicy.originalWriter(carrier))continue;observeBacking(owner,carrier);observeNested(source,owner.field,carrier);
                if(map instanceof Object[] values&&map!=carrier)try{
                    for(int index=0;index<values.length;index++)observeChild(source,owner.field,values,"BACKING",index,values[index]);
                }catch(ReflectiveOperationException unavailable){source.nestedRegistrationGaps.add(owner.field.getDeclaringClass().getName()+"#"+owner.field.getName());}
                source.owner.mapChanged(source);
            }
        }
    }
    private static boolean currentOwner(Owner owner,Object map) {
        // OWNERS contains only actual observed receiver identities. Replacing a
        // holder's field does not revoke the old container or backing alias's
        // obligation, and never transfers its policy to an unrelated instance.
        var source=owner.source.get();
        return map!=null&&source!=null&&source.owner.liveRegistration(source)&&source.holder()!=null;
    }
    static String observerGap(RecoverySources sources,UUID subject) {
        boolean needed=false;
        synchronized(HOLDERS) { for(var row:HOLDERS.values())for(Owner owner:row) {
            var source=owner.source.get();if(source!=null&&source.owner==sources&&!Collections.disjoint(source.subjects,sources.runtimeOwner().groupTargets(subject)))needed=true;
        } }
        return !needed||available()?"":"SOURCE_WRITE_OBSERVER_UNAVAILABLE";
    }
    static void close(RecoverySources sources) {
        synchronized(OWNERS) {
            var iterator=OWNERS.entrySet().iterator();
            while(iterator.hasNext()) {
                var row=iterator.next();var keep=row.getValue().stream().filter(owner->{var source=owner.source.get();return source!=null&&source.owner!=sources;}).toList();
                if(keep.isEmpty())iterator.remove();else row.setValue(keep);
            }
        }
        synchronized(HOLDERS) {
            var iterator=HOLDERS.entrySet().iterator();
            while(iterator.hasNext()) {
                var row=iterator.next();var keep=row.getValue().stream().filter(owner->{var source=owner.source.get();return source!=null&&source.owner!=sources;}).toList();
                if(keep.isEmpty())iterator.remove();else row.setValue(keep);
            }
        }
    }
    static boolean denies(Object map,Object key,Object value) {
        RetiringList retiring=RETIRING_LIST.get();
        if(retiring!=null&&(map==retiring.list||map==retiring.backing)){
            for(Object retained:retiring.before)if(retained==value)return false;
        }
        List<Owner> owners=OWNERS.get(map);if(owners==null)return false;
        for(Owner owner:owners) {
            var source=owner.source.get();
            if(!currentOwner(owner,map))continue;
            Object actual=owner.map.get();
            if(map!=actual&&scopedFastutil(actual)&&BackingPolicy.originalWriter(actual))continue;
            if(deniesValue(source,map,key)||deniesValue(source,map,value))return true;
        }
        return false;
    }
    private static boolean terminalOwner(Object value){
        for(Owner owner:OWNERS.getOrDefault(value,List.of()))if(currentOwner(owner,value)){
            var source=owner.source.get();for(UUID subject:source.subjects)if(source.owner.runtimeOwner().terminalSource(subject))return true;
        }return false;
    }
    static boolean primitiveBackingDenied(Object array){
        for(Owner owner:OWNERS.getOrDefault(array,List.of())){
            Object map=owner.map.get();if(primitiveMap(map)&&map!=array&&currentOwner(owner,array)
                    &&!BackingPolicy.originalWriter(map)&&array.getClass().getComponentType()!=null&&array.getClass().getComponentType().isPrimitive()&&terminalOwner(map))return true;
        }return false;
    }
    private static synchronized boolean available() {
        try {
            if(entries==null) {
                Class<?> bridge=Class.forName("dev.ronova.pro.bootstrap.SourceMapBridge",false,null);
                if(!Integer.valueOf(1).equals(bridge.getMethod("version").invoke(null)))return false;
                Class<?> agent=Class.forName("dev.ronova.pro.agent.RecoveryAgent",false,ClassLoader.getSystemClassLoader());
                Method ready=agent.getMethod("sourcePublicationState"),status=bridge.getMethod("state");
                Method read=bridge.getMethod("entries",Object.class),get=bridge.getMethod("peek",Object.class,Object.class),
                        run=bridge.getMethod("withMap",Object.class,Runnable.class),rev=bridge.getMethod("revision",Object.class);
                state=ready;bridgeState=status;peek=get;withMap=run;revision=rev;entries=read;
            }
            return "INSTALLED".equals(state.invoke(null))&&"HASHMAP_SCOPES_INSTALLED".equals(bridgeState.invoke(null));
        } catch(ReflectiveOperationException|RuntimeException|LinkageError missing) { return false; }
    }
    static String unavailable(Object map) {
        return "SOURCE_MAP_BUSY_OR_UNOBSERVED";
    }
    static List<Map.Entry<Object,Object>> entries(Object map) {
        if(!supported(map)&&!fastSet(map))return null;
        if(fastSet(map)){
            var rows=new ArrayList<Map.Entry<Object,Object>>();
            try{if(!with(map,()->{Object[] keys=(Object[])field(map,"key");int n=(Integer)field(map,"n");
                for(int i=0;i<=n;i++)if(setOccupied(map,keys,i,n))rows.add(new AbstractMap.SimpleImmutableEntry<>(keys[i],keys[i]));
            }))return null;return rows;}catch(ReflectiveOperationException unavailable){return null;}
        }
        if(primitiveMap(map)){
            var rows=new ArrayList<Map.Entry<Object,Object>>();
            try{if(!with(map,()->{Object keys=field(map,"key");Object[] values=(Object[])field(map,"value");int n=(Integer)field(map,"n");
                for(int i=0;i<=n;i++)if(primitiveOccupied(map,keys,i,n))rows.add(new AbstractMap.SimpleImmutableEntry<>(primitiveKey(map,keys,i),values[i]));
            }))return null;return rows;}catch(ReflectiveOperationException unavailable){return null;}
        }
        if(map.getClass()==ConcurrentHashMap.class) {
            var result=new ArrayList<Map.Entry<Object,Object>>();
            var cursor=((Map<?,?>)map).entrySet().iterator();
            while(cursor.hasNext()) { var row=cursor.next();result.add(new AbstractMap.SimpleImmutableEntry<>(row.getKey(),row.getValue())); }
            return result;
        }
        if(!available())return null;
        try {
            Object[][] rows=(Object[][])entries.invoke(null,map);if(rows==null)return null;
            var result=new ArrayList<Map.Entry<Object,Object>>(rows.length);
            for(Object[] row:rows)result.add(new AbstractMap.SimpleImmutableEntry<>(row[0],row[1]));return result;
        } catch(ReflectiveOperationException failure) { return null; }
    }
    static Object[] peek(Object map,Object key) {
        if(!supported(map))return null;
        if(primitiveMap(map)){
            Object[][] result={null};try{if(!with(map,()->{Map<?,?> actual=(Map<?,?>)map;boolean present=actual.containsKey(key);result[0]=new Object[]{present,present?actual.get(key):null};}))return null;}
            catch(ReflectiveOperationException unavailable){return null;}return result[0];
        }
        if(map.getClass()==ConcurrentHashMap.class) {
            if(key==null)return new Object[]{false,null};
            // This backend cannot contain null values; one lookup supplies both facts.
            Object value=((Map<?,?>)map).get(key);
            return new Object[]{value!=null,value};
        }
        if(!available())return null;
        try { return (Object[])peek.invoke(null,map,key); }catch(ReflectiveOperationException failure) { return null; }
    }
    static long revision(Object map) {
        if(scopedFastutil(map))try{return (long)Class.forName("dev.ronova.pro.bootstrap.TaskBridge",false,null).getMethod("sourceIndexRevision",Object.class).invoke(null,map);}
        catch(ReflectiveOperationException unavailable){return -1;}
        if(!hashBacked(map))return 0;
        if(!available())return -1;
        try { return (long)revision.invoke(null,map); }catch(ReflectiveOperationException failure) { return -1; }
    }
    static final class Cursor {
        final Object map;
        Object[] root,table;Object next,previous,primitiveKeys;
        int bucket;long revision=-1;
        boolean complete,revisit,removed;
        final ArrayDeque<Object[]> pending=new ArrayDeque<>();
        final Set<Object[]> seen=Collections.newSetFromMap(new IdentityHashMap<>());
        Cursor(Object map){this.map=map;}
        private void reset(Object[] root,long revision){
            this.root=root;table=root;bucket=0;next=null;previous=null;this.revision=revision;complete=false;revisit=false;removed=false;
            pending.clear();seen.clear();if(root!=null)seen.add(root);
        }
    }
    @FunctionalInterface interface Visitor {
        boolean entry(Object key,Object value,Object node,Cursor cursor)throws ReflectiveOperationException,java.io.IOException;
    }
    /** Traverses actual nodes under the existing HashMap gate, or the original weak-consistent concurrent rules. */
    static boolean visit(Object map,Cursor cursor,int budget,Visitor visitor)throws ReflectiveOperationException,java.io.IOException {
        if(cursor.map!=map||!supported(map)&&!fastSet(map))throw new IllegalArgumentException("ACTUAL_MAP_CURSOR_REQUIRED");
        if(fastSet(map))return visitSet(map,cursor,budget,visitor);
        if(primitiveMap(map))return visitPrimitive(map,cursor,budget,visitor);
        java.io.IOException[] failure={null};
        boolean ran=with(map,()->{
            boolean concurrent=map.getClass()==ConcurrentHashMap.class;
            long current=revision(map);if(current<0)throw new IllegalStateException("GROUP_MAP_REVISION_UNAVAILABLE");
            Object[] root=(Object[])field(map,"table");
            if(cursor.root!=root||cursor.revision!=current)cursor.reset(root,current);
            Method at=null;
            if(concurrent&&root!=null){at=ConcurrentHashMap.class.getDeclaredMethod("tabAt",root.getClass(),int.class);at.setAccessible(true);}
            int remaining=budget;
            while(remaining-->0&&!cursor.complete){
                if(cursor.next!=null){
                    Object node=cursor.next,successor=field(node,"next");
                    cursor.removed=false;
                    try{if(!visitor.entry(field(node,"key"),field(node,concurrent?"val":"value"),node,cursor))break;}
                    catch(java.io.IOException unavailable){failure[0]=unavailable;break;}
                    long after=revision(map);
                    if(!concurrent&&!cursor.removed&&after!=cursor.revision){cursor.reset((Object[])field(map,"table"),after);continue;}
                    if(cursor.revisit){cursor.next=null;cursor.previous=null;cursor.revisit=false;}
                    else{if(!cursor.removed)cursor.previous=node;cursor.next=successor;if(successor==null){cursor.bucket++;cursor.previous=null;}}
                    cursor.revision=after;
                    continue;
                }
                if(cursor.table==null||cursor.bucket==cursor.table.length){
                    if(cursor.pending.isEmpty()){cursor.complete=true;break;}
                    cursor.table=cursor.pending.removeFirst();cursor.bucket=0;cursor.previous=null;continue;
                }
                Object head=concurrent?at.invoke(null,cursor.table,cursor.bucket):cursor.table[cursor.bucket];
                if(head==null){cursor.bucket++;continue;}
                int hash=concurrent?(Integer)field(head,"hash"):0;
                if(concurrent&&hash==-1){
                    Object[] next=(Object[])field(head,"nextTable");
                    if(next==null)throw new IllegalStateException("SOURCE_MAP_FORWARDING_TABLE_UNAVAILABLE");
                    if(cursor.seen.add(next))cursor.pending.addLast(next);cursor.bucket++;continue;
                }
                if(concurrent&&hash==-3)throw new IllegalStateException("SOURCE_MAP_RESERVATION_PENDING");
                cursor.next=concurrent&&hash==-2?field(head,"first"):head;cursor.previous=null;
                if(cursor.next==null)cursor.bucket++;
            }
            cursor.revision=revision(map);
        });
        if(failure[0]!=null)throw failure[0];return ran;
    }
    private static Object primitiveKey(Object map,Object keys,int index){
        if(map.getClass()==it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap.class)return Long.valueOf(((long[])keys)[index]);
        return Integer.valueOf(((int[])keys)[index]);
    }
    private static boolean primitiveOccupied(Object map,Object keys,int index,int n)throws ReflectiveOperationException{
        return index==n?(Boolean)field(map,"containsNullKey"):((Number)primitiveKey(map,keys,index)).longValue()!=0;
    }
    private static boolean visitPrimitive(Object map,Cursor cursor,int budget,Visitor visitor)throws ReflectiveOperationException,java.io.IOException{
        java.io.IOException[] failure={null};
        boolean ran=with(map,()->{
            Object keys=field(map,"key");Object[] values=(Object[])field(map,"value");long revision=revision(map);
            if(cursor.root!=values||cursor.primitiveKeys!=keys||cursor.revision!=revision){cursor.reset(values,revision);cursor.primitiveKeys=keys;}
            int n=(Integer)field(map,"n"),remaining=budget;
            while(remaining-->0&&!cursor.complete){
                if(cursor.bucket>n){cursor.complete=true;break;}
                int slot=cursor.bucket;cursor.removed=false;
                if(primitiveOccupied(map,keys,slot,n)){
                    cursor.next=values[slot];
                    try{if(!visitor.entry(primitiveKey(map,keys,slot),values[slot],Integer.valueOf(slot),cursor))break;}
                    catch(java.io.IOException unavailable){failure[0]=unavailable;break;}
                }
                if(cursor.removed){
                    keys=field(map,"key");values=(Object[])field(map,"value");n=(Integer)field(map,"n");
                    cursor.reset(values,revision(map));cursor.primitiveKeys=keys;
                }else if(cursor.revision!=revision(map)){cursor.reset((Object[])field(map,"value"),revision(map));cursor.primitiveKeys=field(map,"key");break;}
                else cursor.bucket++;
            }
            cursor.revision=revision(map);
        });
        if(failure[0]!=null)throw failure[0];return ran;
    }
    private static boolean setOccupied(Object set,Object[] keys,int at,int n)throws ReflectiveOperationException{
        return at==n?(Boolean)field(set,"containsNull"):keys[at]!=null;
    }
    private static boolean visitSet(Object set,Cursor cursor,int budget,Visitor visitor)throws ReflectiveOperationException,java.io.IOException{
        java.io.IOException[] failure={null};
        boolean ran=with(set,()->{
            Object[] keys=(Object[])field(set,"key");long version=revision(set);
            if(version<0)throw new IllegalStateException("SOURCE_SET_REVISION_UNAVAILABLE");
            if(cursor.root!=keys||cursor.revision!=version)cursor.reset(keys,version);
            int n=(Integer)field(set,"n"),remaining=budget;
            while(remaining-->0&&!cursor.complete&&cursor.bucket<=n){
                int at=cursor.bucket;cursor.removed=false;
                if(setOccupied(set,keys,at,n)){
                    cursor.next=keys[at];
                    try{if(!visitor.entry(keys[at],keys[at],Integer.valueOf(at),cursor))break;}
                    catch(java.io.IOException unavailable){failure[0]=unavailable;break;}
                }
                if(cursor.removed||cursor.revision!=revision(set)){
                    keys=(Object[])field(set,"key");n=(Integer)field(set,"n");cursor.reset(keys,revision(set));
                }else cursor.bucket++;
            }
            if(cursor.bucket>n)cursor.complete=true;
        });
        if(failure[0]!=null)throw failure[0];return ran;
    }
    private static boolean removeSetIdentity(Cursor cursor,Object key)throws ReflectiveOperationException{
        Object set=cursor.map;Object[] keys=(Object[])field(set,"key");int n=(Integer)field(set,"n"),at=cursor.bucket;
        if(keys!=cursor.root||revision(set)!=cursor.revision||at>n||!setOccupied(set,keys,at,n)||keys[at]!=key)return false;
        Method remove=set.getClass().getDeclaredMethod(at==n?"removeNullEntry":"removeEntry",at==n?new Class<?>[0]:new Class<?>[]{int.class});remove.setAccessible(true);
        if(!Boolean.TRUE.equals(at==n?remove.invoke(set):remove.invoke(set,at)))return false;
        Object[] after=(Object[])field(set,"key");int end=(Integer)field(set,"n");
        for(int i=0;i<=end;i++)if(setOccupied(set,after,i,end)&&after[i]==key)throw new IllegalStateException("SOURCE_SET_REMOVAL_READBACK_FAILED");
        cursor.removed=true;return true;
    }
    /** Unlinks only the actual visited node; publication never selects a bucket through a Mod hashCode. */
    static boolean removeIdentityVisited(Cursor cursor,Object key)throws ReflectiveOperationException {
        if(fastSet(cursor.map))return removeSetIdentity(cursor,key);
        if(!hashBacked(cursor.map)||cursor.next==null)return false;
        Object previous=cursor.previous,node=previous==null?cursor.table[cursor.bucket]:field(previous,"next");
        if(node!=cursor.next||revision(cursor.map)!=cursor.revision){cursor.revisit=true;return false;}
        if(field(node,"key")!=key)return false;
        unlinkHashNode(cursor,previous,node);return true;
    }
    private static void unlinkHashNode(Cursor cursor,Object previous,Object node)throws ReflectiveOperationException {
        if(node.getClass().getName().equals("java.util.HashMap$TreeNode")){
            Method remove=node.getClass().getDeclaredMethod("removeTreeNode",HashMap.class,cursor.table.getClass(),boolean.class);remove.setAccessible(true);
            remove.invoke(node,cursor.map,cursor.table,true);cursor.revisit=true;
        }else if(previous==null)cursor.table[cursor.bucket]=field(node,"next");else slot(previous.getClass(),"next").set(previous,field(node,"next"));
        Field size=slot(HashMap.class,"size"),modCount=slot(HashMap.class,"modCount");
        size.setInt(cursor.map,size.getInt(cursor.map)-1);modCount.setInt(cursor.map,modCount.getInt(cursor.map)+1);
        removedHashNode(cursor.map,node);cursor.removed=true;Publications.CHANGED.invoke(null,cursor.map);
    }
    static void removeVisited(Cursor cursor,Object target,ProRuntime runtime,ProRuntime.Subject group)throws ReflectiveOperationException {
        if(fastSet(cursor.map)){
            if(!RecoverySources.tryRecordGraph())throw new IllegalStateException("GROUP_RECORD_GRAPH_BUSY");
            try{Object value=cursor.next;if(runtime.groupSubject(value)==group||member(value,runtime,group)){validateRecordKey(value,runtime,group);removeSetIdentity(cursor,value);}}
            finally{RecoverySources.releaseRecordGraph();}return;
        }
        if(primitiveMap(cursor.map)){
            if(!RecoverySources.tryRecordGraph())throw new IllegalStateException("GROUP_RECORD_GRAPH_BUSY");
            try{
                Object map=cursor.map,keys=field(map,"key");Object[] values=(Object[])field(map,"value");int n=(Integer)field(map,"n"),at=(Integer)target;
                if(keys!=cursor.primitiveKeys||values!=cursor.root||revision(map)!=cursor.revision||at>n||!primitiveOccupied(map,keys,at,n))return;
                Object value=values[at];if(runtime.groupSubject(value)!=group&&!member(value,runtime,group))return;
                validateRecordKey(value,runtime,group);
                Method remove=map.getClass().getDeclaredMethod(at==n?"removeNullEntry":"removeEntry",at==n?new Class<?>[0]:new Class<?>[]{int.class});remove.setAccessible(true);
                Object key=primitiveKey(map,keys,at);
                if(at==n)remove.invoke(map);else remove.invoke(map,at);
                if(((Map<?,?>)map).containsKey(key)&&((Map<?,?>)map).get(key)==value)throw new IllegalStateException("GROUP_SOURCE_INDEX_REMOVAL_REFUSED");
                cursor.removed=true;
            }finally{RecoverySources.releaseRecordGraph();}return;
        }
        if(hashBacked(cursor.map)){
            if(!RecoverySources.tryRecordGraph())throw new IllegalStateException("GROUP_RECORD_GRAPH_BUSY");
            try{
                Object previous=cursor.previous,node=previous==null?cursor.table[cursor.bucket]:field(previous,"next");
                if(node!=target){cursor.revisit=true;return;}
                if(!selected(node,runtime,group,"value"))return;
                if(revision(cursor.map)!=cursor.revision){cursor.revisit=true;return;}
                unlinkHashNode(cursor,previous,node);
            }finally{RecoverySources.releaseRecordGraph();}
            return;
        }
        Class<?> type=ConcurrentHashMap.class,arrayType=cursor.table.getClass();
        Method at=type.getDeclaredMethod("tabAt",arrayType,int.class),set=type.getDeclaredMethod("setTabAt",arrayType,int.class,arrayType.getComponentType()),count=type.getDeclaredMethod("addCount",long.class,int.class);
        at.setAccessible(true);set.setAccessible(true);count.setAccessible(true);
        Object head=at.invoke(null,cursor.table,cursor.bucket);if(head==null)return;
        int hash=(Integer)field(head,"hash");
        if(hash==-1){Object[] next=(Object[])field(head,"nextTable");if(next==null)throw new IllegalStateException("SOURCE_MAP_FORWARDING_TABLE_UNAVAILABLE");if(cursor.seen.add(next))cursor.pending.addLast(next);cursor.revisit=true;return;}
        synchronized(head){
            if(at.invoke(null,cursor.table,cursor.bucket)!=head){cursor.revisit=true;return;}
            if(!RecoverySources.tryRecordGraph())throw new IllegalStateException("GROUP_RECORD_GRAPH_BUSY");
            try{
                if(hash==-3)throw new IllegalStateException("SOURCE_MAP_RESERVATION_PENDING");
                Object previous=null,node=hash==-2?field(head,"first"):head;
                while(node!=null&&node!=target){previous=node;node=field(node,"next");}
                if(node==null||!selected(node,runtime,group,"val"))return;
                if(hash==-2){
                    Method remove=head.getClass().getDeclaredMethod("removeTreeNode",node.getClass());remove.setAccessible(true);
                    if((Boolean)remove.invoke(head,node)){
                        Method convert=type.getDeclaredMethod("untreeify",arrayType.getComponentType());convert.setAccessible(true);
                        set.invoke(null,cursor.table,cursor.bucket,convert.invoke(null,field(head,"first")));cursor.revisit=true;
                    }
                }else if(previous==null)set.invoke(null,cursor.table,cursor.bucket,field(node,"next"));else slot(previous.getClass(),"next").set(previous,field(node,"next"));
                count.invoke(cursor.map,-1L,-1);changed(cursor.map);
            }finally{RecoverySources.releaseRecordGraph();}
        }
    }
    /** Unlink the actual node, using its stored hash. A suppressed Mod hashCode must never choose another bucket. */
    static void removeGroup(Object map,ProRuntime runtime,ProRuntime.Subject group)throws ReflectiveOperationException {
        if(hashBacked(map)) {
            if(!with(map,()->{
                if(!RecoverySources.tryRecordGraph())throw new IllegalStateException("GROUP_RECORD_GRAPH_BUSY");
                try {removeHashNodes(map,runtime,group);}finally {RecoverySources.releaseRecordGraph();}
            }))throw new IllegalStateException("GROUP_MAP_BUSY");
        } else if(map.getClass()==ConcurrentHashMap.class)removeConcurrentNodes(map,runtime,group);
    }
    private static Object field(Object owner,String name)throws ReflectiveOperationException {
        Field field=slot(owner.getClass(),name);return field.get(owner);
    }
    private static Field slot(Class<?> type,String name)throws ReflectiveOperationException {
        for(Class<?> owner=type;owner!=null;owner=owner.getSuperclass())try {
            Field field=owner.getDeclaredField(name);field.setAccessible(true);return field;
        }catch(NoSuchFieldException absent) { }
        throw new NoSuchFieldException(name);
    }
    private static boolean selected(Object node,ProRuntime runtime,ProRuntime.Subject group,String valueField)throws ReflectiveOperationException {
        Object key=field(node,"key"),value=field(node,valueField);
        boolean selected=runtime.groupSubject(key)==group||runtime.groupSubject(value)==group||member(key,runtime,group)||member(value,runtime,group);
        if(selected){validateRecordKey(key,runtime,group);validateRecordKey(value,runtime,group);}
        return selected;
    }
    private static void validateRecordKey(Object value,ProRuntime runtime,ProRuntime.Subject group){
        if(value instanceof net.minecraft.nbt.CompoundTag tag){
            UUID subject=RecoverySources.subject(runtime.recoverySources,tag);
            if(subject==null||!group.members.contains(subject))throw new IllegalStateException("GROUP_MAP_RECORD_ORIGIN_UNRESOLVED");
            String gap=RecoverySources.recordGraphGap(runtime.recoverySources,tag,subject);
            if(!gap.isEmpty())throw new IllegalStateException(gap);
        }
    }
    private static boolean member(Object value,ProRuntime runtime,ProRuntime.Subject group) {
        UUID id=value instanceof net.minecraft.world.entity.Entity?runtime.recoveryTasks.subjectOf(value):
                value instanceof net.minecraft.nbt.CompoundTag tag?RecoverySources.subject(runtime.recoverySources,tag):null;
        return id!=null&&group.members.contains(id);
    }
    private static void removedHashNode(Object map,Object node)throws ReflectiveOperationException {
        if(map.getClass()==LinkedHashMap.class) {
            Method unlink=LinkedHashMap.class.getDeclaredMethod("afterNodeRemoval",Class.forName("java.util.HashMap$Node",false,null));
            unlink.setAccessible(true);unlink.invoke(map,node);
        }
    }
    private static void removeHashNodes(Object map,ProRuntime runtime,ProRuntime.Subject group)throws ReflectiveOperationException {
        Object[] table=(Object[])field(map,"table");if(table==null)return;
        Field next=null;int removed=0;
        Field size=slot(HashMap.class,"size"),modCount=slot(HashMap.class,"modCount");
        try {
        for(int bucket=0;bucket<table.length;bucket++) {
            Object previous=null,node=table[bucket];
            while(node!=null) {
                next=slot(node.getClass(),"next");Object successor=next.get(node);
                if(selected(node,runtime,group,"value")) {
                    if(node.getClass().getName().equals("java.util.HashMap$TreeNode")) {
                        Method remove=node.getClass().getDeclaredMethod("removeTreeNode",HashMap.class,table.getClass(),boolean.class);remove.setAccessible(true);
                        Object removedNode=node;remove.invoke(node,map,table,true);previous=null;node=table[bucket];removed++;
                        size.setInt(map,size.getInt(map)-1);modCount.setInt(map,modCount.getInt(map)+1);removedHashNode(map,removedNode);continue;
                    }
                    if(previous==null)table[bucket]=successor;else next.set(previous,successor);removed++;
                    size.setInt(map,size.getInt(map)-1);modCount.setInt(map,modCount.getInt(map)+1);removedHashNode(map,node);
                }else previous=node;
                node=successor;
            }
        }
        }finally {if(removed!=0)Publications.CHANGED.invoke(null,map);}
    }
    private static void removeConcurrentNodes(Object map,ProRuntime runtime,ProRuntime.Subject group)throws ReflectiveOperationException {
        Class<?> type=ConcurrentHashMap.class;Object[] table=(Object[])field(map,"table");if(table==null)return;
        Class<?> arrayType=table.getClass();Method at=type.getDeclaredMethod("tabAt",arrayType,int.class),set=type.getDeclaredMethod("setTabAt",arrayType,int.class,arrayType.getComponentType());
        Method count=type.getDeclaredMethod("addCount",long.class,int.class);at.setAccessible(true);set.setAccessible(true);count.setAccessible(true);
        ArrayDeque<Object[]> tables=new ArrayDeque<>();Set<Object[]> seen=Collections.newSetFromMap(new IdentityHashMap<>());tables.add(table);int totalRemoved=0;
        try {
        while(!tables.isEmpty()) {
            Object[] current=tables.removeFirst();if(!seen.add(current))continue;
            for(int bucket=0;bucket<current.length;bucket++) {
                Object head=at.invoke(null,current,bucket);if(head==null)continue;
                int hash=(Integer)field(head,"hash");
                if(hash==-1) {tables.add((Object[])field(head,"nextTable"));continue;}
                synchronized(head) {
                    if(at.invoke(null,current,bucket)!=head) {bucket--;continue;}
                    if(!RecoverySources.tryRecordGraph())throw new IllegalStateException("GROUP_RECORD_GRAPH_BUSY");
                    try {
                    if(hash==-3)throw new IllegalStateException("SOURCE_MAP_RESERVATION_PENDING");
                    Object previous=null,node=hash==-2?field(head,"first"):head;
                    while(node!=null) {
                        Field next=slot(node.getClass(),"next");Object successor=next.get(node);
                        if(selected(node,runtime,group,"val")) {
                            if(hash==-2) {
                                Method remove=head.getClass().getDeclaredMethod("removeTreeNode",node.getClass());remove.setAccessible(true);
                                boolean untree=(Boolean)remove.invoke(head,node);
                                totalRemoved++;count.invoke(map,-1L,-1);
                                if(untree) {
                                    Method convert=type.getDeclaredMethod("untreeify",arrayType.getComponentType());convert.setAccessible(true);
                                    set.invoke(null,current,bucket,convert.invoke(null,field(head,"first")));
                                    bucket--;break;
                                }
                            } else if(previous==null) {
                                set.invoke(null,current,bucket,successor);totalRemoved++;count.invoke(map,-1L,-1);
                                bucket--;break;
                            }
                            else {next.set(previous,successor);totalRemoved++;count.invoke(map,-1L,-1);}
                        }else previous=node;
                        node=successor;
                    }
                    }finally {RecoverySources.releaseRecordGraph();}
                }
            }
        }
        }finally {if(totalRemoved!=0)changed(map);}
    }
    @FunctionalInterface interface Action { void run() throws ReflectiveOperationException; }
    static boolean with(Object map,Action action)throws ReflectiveOperationException {
        if(scopedFastutil(map)){
            if(!primitiveReady(map))return false;
            ReflectiveOperationException[] thrown={null};
            Runnable run=()->{try{action.run();}catch(ReflectiveOperationException failure){thrown[0]=failure;}};
            try{
                boolean ran=Boolean.TRUE.equals(Class.forName("dev.ronova.pro.bootstrap.TaskBridge",false,null).getMethod("withSourceIndex",Object.class,Runnable.class).invoke(null,map,run));
                if(thrown[0]!=null)throw thrown[0];return ran;
            }catch(InvocationTargetException failure){if(failure.getCause() instanceof RuntimeException error)throw error;if(failure.getCause() instanceof Error error)throw error;throw failure;}
        }
        if(map.getClass()==ConcurrentHashMap.class) { action.run();return true; }
        if(!available())return false;
        ReflectiveOperationException[] thrown={null};
        Runnable run=()->{try { action.run(); }catch(ReflectiveOperationException failure) { thrown[0]=failure; }};
        try {
            boolean ran=(boolean)withMap.invoke(null,map,run);if(thrown[0]!=null)throw thrown[0];return ran;
        } catch(InvocationTargetException failure) {
            if(failure.getCause() instanceof RuntimeException error)throw error;
            if(failure.getCause() instanceof Error error)throw error;
            throw failure;
        }
    }
}
