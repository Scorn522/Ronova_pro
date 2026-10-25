package dev.ronova.pro;

import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/** Actual selected source containers. Unknown key methods are never used to remove an entry. */
final class SourceMaps {
    private record Owner(java.lang.ref.WeakReference<RecoverySources.Source> source,Field field,
                         java.lang.ref.WeakReference<Object> map) { }
    private static final Map<Object,List<Owner>> OWNERS=new WeakIdentityMap<>();
    private static final Map<Object,List<Owner>> HOLDERS=new WeakIdentityMap<>();
    private record RetiringField(Object holder,Field field,Object expected) { }
    private static final ThreadLocal<RetiringField> RETIRING_FIELD=new ThreadLocal<>();
    private record RetiringList(Object list,Object backing,Object[] before){}
    private static final ThreadLocal<RetiringList> RETIRING_LIST=new ThreadLocal<>();
    private static final ThreadLocal<Set<Object>> NESTED_OBSERVATIONS=new ThreadLocal<>();
    /** Exact retained list removal may shift another selected element before clearing the final slot. */
    static void removeListIndex(List<?> list,int index)throws ReflectiveOperationException{
        if(!supportedList(list))throw new IllegalArgumentException("ACTUAL_SOURCE_LIST_REQUIRED");
        if(!fastList(list)){
            if(!with(list,()->list.remove(index)))throw new IllegalStateException("SOURCE_LIST_BUSY");
            changed(list);return;
        }
        RetiringList previous=RETIRING_LIST.get();RETIRING_LIST.set(new RetiringList(list,listBacking(list),list.toArray()));
        try{list.remove(index);}finally{if(previous==null)RETIRING_LIST.remove();else RETIRING_LIST.set(previous);}
    }
    /** Remove every occurrence of this exact identity under the carrier's existing write scope. */
    static boolean removeListIdentity(List<?> list,Object expected)throws ReflectiveOperationException{
        if(!supportedList(list))throw new IllegalArgumentException("ACTUAL_SOURCE_LIST_REQUIRED");
        if(!fastList(list)){
            boolean[] removed={false};
            if(!with(list,()->removed[0]=list.removeIf(value->value==expected)))throw new IllegalStateException("SOURCE_LIST_BUSY");
            if(removed[0])changed(list);return removed[0];
        }
        RetiringList previous=RETIRING_LIST.get();RETIRING_LIST.set(new RetiringList(list,listBacking(list),list.toArray()));
        try{return list.removeIf(value->value==expected);}
        finally{if(previous==null)RETIRING_LIST.remove();else RETIRING_LIST.set(previous);}
    }
    private static final Object RAW_RANGE=new Object();
    private static final class Publications {
        static final Method OBSERVE=method("observeSourceHolder",Object.class),CARRIER=method("observeSourceCarrier",Object.class),HOLD=method("holdSourcePublication",Object.class,Object.class),CHANGED=method("recoveredSourceChange",Object.class);
        private static Method method(String name,Class<?>... parameters){
            try{return PlatformAccess.declaredMethod(Class.forName("dev.ronova.pro.bootstrap.ControlBridge",false,null),name,parameters);}
            catch(ReflectiveOperationException unavailable){throw new ExceptionInInitializerError(unavailable);}
        }
    }
    /** Publish the real holder before a field/source registration becomes visible. */
    static void observeHolder(Object holder){
        if(holder==null)return;
        try{Publications.OBSERVE.invoke(null,holder);}
        catch(ReflectiveOperationException unavailable){throw new IllegalStateException("SOURCE_HOLDER_SCOPE_UNAVAILABLE",unavailable);}
    }
    static void retireField(ProRuntime runtime,Field field,Object receiver,Object expected,Action action)throws ReflectiveOperationException {
        Object holder=receiver==null?field.getDeclaringClass():receiver;ProRuntime.Subject group=runtime.groupSubject(holder);
        if(group==null||!group.terminal)throw new IllegalStateException("GROUP_FIELD_AUTHORITY_CHANGED");
        RetiringField previous=RETIRING_FIELD.get();RETIRING_FIELD.set(new RetiringField(holder,field,expected));
        try {action.run();}finally {if(previous==null)RETIRING_FIELD.remove();else RETIRING_FIELD.set(previous);}
    }
    private static volatile Method state,bridgeState;
    private SourceMaps() { }
    static boolean hashBacked(Object map) {return map!=null&&(map.getClass()==HashMap.class||map.getClass()==LinkedHashMap.class);}
    static boolean primitiveMap(Object map){return map!=null&&(map.getClass()==it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap.class
            ||map.getClass()==it.unimi.dsi.fastutil.ints.Int2ObjectLinkedOpenHashMap.class
            ||map.getClass()==it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap.class);}
    static boolean supported(Object map) { return hashBacked(map)||primitiveMap(map)||map!=null&&map.getClass()==ConcurrentHashMap.class; }
    /** Exact direct layouts do not grant ordinary Map.get/compute comparator or equality authority. */
    static boolean groupMap(Object value){return supported(value)||KnownContainers.map(value);}
    static boolean groupSequence(Object value){return supportedList(value)||KnownContainers.sequence(value);}
    static boolean groupSet(Object value){return supportedSet(value)||KnownContainers.set(value);}
    static boolean serialized(Object map){return hashBacked(map)||primitiveMap(map);}
    static boolean fastList(Object value){return value!=null&&value.getClass()==it.unimi.dsi.fastutil.objects.ObjectArrayList.class;}
    static boolean fastSet(Object value){return value!=null&&value.getClass()==it.unimi.dsi.fastutil.objects.ObjectOpenHashSet.class;}
    static boolean scopedFastutil(Object value){return primitiveMap(value)||fastList(value)||fastSet(value);}
    static boolean scopedList(Object value){return value!=null&&(value.getClass()==ArrayList.class||fastList(value));}
    static boolean supportedList(Object value){return scopedList(value)||value!=null&&value.getClass()==java.util.concurrent.CopyOnWriteArrayList.class;}
    /** Retained name for callers: only an unsupported bootstrap container lacks a direct backend. */
    static boolean removedJdkContainer(Object value){return value!=null&&value.getClass().getClassLoader()==null
            &&(value instanceof Map<?,?>||value instanceof Collection<?>||value instanceof java.util.concurrent.atomic.AtomicReference<?>)
            &&!groupMap(value)&&!groupSet(value)&&!groupSequence(value)&&value.getClass()!=java.util.concurrent.atomic.AtomicReference.class;}
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
            Method state=PlatformAccess.declaredMethod(boundary,"state",String.class);String name=map.getClass().getName().replace('.','/');
            for(String suffix:fastList(map)?List.of(""):fastSet(map)?List.of("","$SetIterator"):List.of("","$MapIterator","$MapEntry")){
                Class.forName(map.getClass().getName()+suffix,false,map.getClass().getClassLoader());
                if(!"INSTALLED".equals(state.invoke(null,name+suffix)))return false;
            }
            if(fastList(map)){
                for(String view:List.of("it.unimi.dsi.fastutil.objects.AbstractObjectList$ObjectSubList","it.unimi.dsi.fastutil.objects.ObjectArrayList$1","it.unimi.dsi.fastutil.objects.ObjectIterators$AbstractIndexBasedListIterator")){
                    Class.forName(view,false,map.getClass().getClassLoader());if(!"INSTALLED".equals(state.invoke(null,view.replace('.','/'))))return false;
                }
            }
            return fieldObserverAvailable();
        }catch(ReflectiveOperationException|LinkageError unavailable){return false;}
    }
    static boolean supportedSet(Object set) {return fastSet(set)||set!=null&&(set.getClass()==HashSet.class||set.getClass()==LinkedHashSet.class);}
    static boolean referenceContainer(Object value){return groupMap(value)||groupSet(value)||groupSequence(value)||value instanceof Object[]
            ||value!=null&&(value.getClass()==ArrayList.class||value.getClass()==java.util.concurrent.CopyOnWriteArrayList.class
            ||value.getClass()==java.util.concurrent.atomic.AtomicReference.class);}
    static boolean observed(Object value){return value!=null&&OWNERS.containsKey(value);}
    static boolean replacementCovered(RecoverySources.Source source,Field field,Object value){
        if(KnownContainers.supported(value)||KnownContainers.set(value))return false;
        if(Modifier.isFinal(field.getModifiers()))return true;
        if(removedJdkContainer(value)||!fieldObserverAvailable()||!source.owner.liveRegistration(source)||scopedFastutil(value)&&!primitiveReady(value))return false;
        for(Owner owner:HOLDERS.getOrDefault(source.holder(),List.of()))
            if(owner.source.get()==source&&owner.field.equals(field))return value==null||referenceContainer(value)
                    ||value instanceof net.minecraft.world.entity.Entity||value instanceof net.minecraft.nbt.CompoundTag
                    ||RecoveryRecords.ownedTarget(value,source.owner.runtimeOwner(),true)!=null||RecoveryRecords.emptyWrapper(value,source.owner.runtimeOwner())||RecoveryRecords.immutableScalar(value)||RecoveryRecords.nestedHolder(value);
        return false;
    }
    static boolean disposedField(RecoverySources.Source source,Field field)throws IllegalAccessException {
        if(source.nestedAbsent)return source.owner.current(source);
        if(!source.groupDisposed||!source.cleared(field)||!source.owner.current(source))return false;
        Object holder=source.holder();
        return holder==null||field.get(holder instanceof Class<?>?null:holder)==null;
    }
    static Object setMap(Object set)throws ReflectiveOperationException {
        if(KnownContainers.set(set))return KnownContainers.setMap(set);
        if(!supportedSet(set))throw new IllegalArgumentException("ACTUAL_HASHSET_REQUIRED");
        if(fastSet(set))return set;
        Object map=field(set,"map");if(!hashBacked(map))throw new IllegalStateException("ACTUAL_HASHSET_MAP_UNAVAILABLE");return map;
    }
    static void observe(Object map,RecoverySources.Source source,Field field) {
        if(!supported(map))return;
        if(primitiveMap(map))try{Publications.CARRIER.invoke(null,map);}catch(ReflectiveOperationException unavailable){source.gap="SOURCE_INDEX_SCOPE_UNAVAILABLE";return;}
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
        if(groupSet(value))try {backend=setMap(value);Publications.CARRIER.invoke(null,backend);}
        catch(ReflectiveOperationException unavailable){source.gap="SOURCE_SET_BACKING_UNAVAILABLE";backend=null;}
        synchronized(OWNERS) {
            if(groupMap(backend)||KnownContainers.supported(backend)||fastSet(backend)||fastList(backend)||backend!=null&&(backend.getClass()==ArrayList.class
                    ||backend.getClass()==java.util.concurrent.CopyOnWriteArrayList.class
                    ||backend.getClass()==java.util.concurrent.atomic.AtomicReference.class||backend instanceof Object[])) {
                var next=new ArrayList<Owner>();
                for(Owner owner:OWNERS.getOrDefault(backend,List.of())) {
                    var prior=owner.source.get();
                    if(prior!=null&&!(prior==source&&owner.field.equals(field)))next.add(owner);
                }
                next.add(registered);OWNERS.put(backend,List.copyOf(next));
            }
            if(groupSet(value)){
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
            if(removedJdkContainer(carrier))throw new IllegalAccessException("JDK_CONTAINER_BACKEND_UNSUPPORTED");
            if(RecoveryRecords.nestedHolder(carrier))observeChild(source,field,carrier,"FIELD",null,carrier);
            else if(groupMap(carrier)){
                List<Map.Entry<Object,Object>> rows=entries(carrier);if(rows==null)throw new IllegalAccessException("NESTED_MAP_BUSY");
                // A comparator map and an identity map have no durable equals-key selector.
                if(!KnownContainers.map(carrier)){
                    for(var row:rows)if(supportedKey(row.getKey()))observeChild(source,field,carrier,"MAP",row.getKey(),row.getValue());
                }else for(var row:rows)if(RecoveryRecords.nestedHolder(row.getValue()))throw new IllegalAccessException("NESTED_MAP_KEY_SEMANTICS_UNOBSERVED");
            }else if(groupSet(carrier)){
                List<Map.Entry<Object,Object>> rows=entries(setMap(carrier));if(rows==null)throw new IllegalAccessException("NESTED_SET_BUSY");
                for(var row:rows){Object child=row.getKey();if(child!=null)observeChild(source,field,carrier,"SET",child.getClass().getName(),child);}
            }else if(carrier.getClass()==java.util.concurrent.atomic.AtomicReference.class)
                observeChild(source,field,carrier,"ATOMIC",null,((java.util.concurrent.atomic.AtomicReference<?>)carrier).get());
            else if(carrier instanceof Object[] values){for(int i=0;i<values.length;i++)observeChild(source,field,carrier,"ARRAY",i,values[i]);}
            else if(KnownContainers.sequence(carrier)){
                if(!with(carrier,()->{KnownContainers.Image image=KnownContainers.snapshot(carrier);
                    for(int i=0;i<image.rows.length;i++)observeChild(source,field,carrier,"SEQUENCE",i,image.rows[i].value);
                    if(!KnownContainers.unchanged(image))throw new IllegalAccessException("NESTED_SEQUENCE_CHANGED");
                }))throw new IllegalAccessException("NESTED_SEQUENCE_BUSY");
            }
            else if(supportedList(carrier)){
                Object[][] snapshot={null};
                if(!with(carrier,()->snapshot[0]=((List<?>)carrier).toArray()))throw new IllegalAccessException("NESTED_LIST_BUSY");
                for(int i=0;i<snapshot[0].length;i++)observeChild(source,field,carrier,"LIST",i,snapshot[0][i]);
            }
            source.nestedRegistrationGaps.remove(location);
        }catch(ReflectiveOperationException unavailable){source.nestedRegistrationGaps.add(location);}
        finally{if(outer)NESTED_OBSERVATIONS.remove();}
    }
    private static void observeChild(RecoverySources.Source parent,Field field,Object carrier,String kind,Object key,Object child)throws ReflectiveOperationException{
        if(!RecoveryRecords.nestedHolder(child)||RecoveryRecords.ownedTarget(child,parent.owner.runtimeOwner(),true)!=null)return;
        RecoverySources.Source nested=parent.owner.nestedSource(parent,field,kind,key,child,carrier);
        if(nested==null)throw new IllegalAccessException("NESTED_SOURCE_BINDING_PENDING");
        if(nested==parent)return;
        for(RecoverySources.Source ancestor=parent;ancestor!=null;ancestor=ancestor.nestedParent)if(ancestor==nested)return;
        for(Class<?> type=child.getClass();type!=null&&type!=Object.class;type=type.getSuperclass())for(Field member:PlatformAccess.declaredFields(type))
            if(!Modifier.isStatic(member.getModifiers())&&!member.getType().isPrimitive()){
                if(!member.trySetAccessible())throw new IllegalAccessException("NESTED_FIELD_INACCESSIBLE");
                observeField(nested,member,member.get(child));
            }
    }
    static Object listBacking(Object value)throws ReflectiveOperationException{
        if(fastList(value))return field(value,"a");
        if(value!=null&&value.getClass()==ArrayList.class)return field(value,"elementData");
        if(value!=null&&value.getClass()==java.util.concurrent.CopyOnWriteArrayList.class)return field(value,"array");
        if(KnownContainers.supported(value))return KnownContainers.arrayBacking(value);
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
            for(Class<?> type=holder.getClass();type!=null&&type!=Object.class;type=type.getSuperclass())for(Field field:PlatformAccess.declaredFields(type))
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
                if(referenceContainer(next)||RecoveryRecords.nestedHolder(next)){
                    // Drain incoming writers before inspecting the full carrier. Ownership is
                    // attached only by changed() after successful readback, under the same gate.
                    if(primitiveMap(next)&&!primitiveReady(next))return true;
                    if(groupSet(next)&&!holdPublication(holder,next))return true;
                    Object backend=groupSet(next)?setMap(next):next;
                    if(!holdPublication(holder,backend))return true;
                    Object backing=listBacking(backend);
                    if(backing!=null&&!holdPublication(holder,backing)||incomingDenied(source,holder,backend))return true;
                    continue;
                }
                if(deniesValue(source,holder,next))return true;
                if(RecoveryRecords.immutableScalar(next)||next instanceof net.minecraft.world.entity.Entity
                        ||RecoveryRecords.ownedTarget(next,runtime,true)!=null||RecoveryRecords.emptyWrapper(next,runtime))continue;
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
            boolean leaf=RecoveryRecords.immutableScalar(value)||value instanceof net.minecraft.world.entity.Entity||value instanceof net.minecraft.nbt.Tag;
            if(!leaf){
                if(!referenceContainer(value)&&!RecoveryRecords.nestedHolder(value))return true;
                if(scopedFastutil(value)&&!primitiveReady(value)||!holdPublication(holder,value))return true;
                if(groupSet(value)&&!holdPublication(holder,setMap(value)))return true;
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
            else for(Class<?> type=value.getClass();type!=null&&type!=Object.class;type=type.getSuperclass())for(Field field:PlatformAccess.declaredFields(type))
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
        if(referenceContainer(value)||RecoveryRecords.nestedHolder(value)){
            try{return incomingDenied(source,receiver,value);}
            catch(ReflectiveOperationException unavailable){return true;}
        }
        return deniesTarget(source,value);
    }
    private static boolean deniesTarget(RecoverySources.Source source,Object value){
        ProRuntime runtime=source.owner.runtimeOwner();ProRuntime.Subject group=runtime.groupSubject(value);
        if(group!=null&&group.terminal&&!Collections.disjoint(source.subjects,runtime.groupTargets(group.id)))return true;
        Object target=RecoveryRecords.ownedTarget(value,runtime,true);
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
        if(KnownContainers.set(receiver)&&declaring==TreeSet.class&&name.equals("m")){
            try{return next!=setMap(receiver);}catch(ReflectiveOperationException unavailable){return true;}
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
        boolean needed=false,removed=false;
        synchronized(HOLDERS) { for(var row:HOLDERS.values())for(Owner owner:row) {
            var source=owner.source.get();if(source!=null&&source.owner==sources&&!Collections.disjoint(source.subjects,sources.runtimeOwner().groupTargets(subject))){
                needed=true;if(removedJdkContainer(owner.map.get()))removed=true;
            }
        } }
        return removed?"JDK_CONTAINER_BACKEND_UNSUPPORTED":!needed||fieldObserverAvailable()?"":"SOURCE_FIELD_OBSERVER_UNAVAILABLE";
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
    private static synchronized boolean fieldObserverAvailable() {
        try {
            if(state==null) {
                Class<?> bridge=Class.forName("dev.ronova.pro.bootstrap.SourceMapBridge",false,null);
                if(!Integer.valueOf(2).equals(PlatformAccess.declaredMethod(bridge,"version").invoke(null)))return false;
                Class<?> agent=Class.forName("dev.ronova.pro.agent.RecoveryAgent",false,ClassLoader.getSystemClassLoader());
                bridgeState=PlatformAccess.declaredMethod(bridge,"state");state=PlatformAccess.declaredMethod(agent,"sourceFieldState");
            }
            return "INSTALLED".equals(state.invoke(null))&&"SOURCE_FIELDS_INSTALLED:JDK_CONTAINER_HOOKS_REMOVED".equals(bridgeState.invoke(null));
        } catch(ReflectiveOperationException|RuntimeException|LinkageError missing) { return false; }
    }
    static String unavailable(Object map) {
        return removedJdkContainer(map)?"JDK_CONTAINER_BACKEND_UNSUPPORTED":"SOURCE_MAP_BUSY_OR_UNOBSERVED";
    }
    static List<Map.Entry<Object,Object>> entries(Object map) {
        if(KnownContainers.map(map)||KnownContainers.enumSet(map)){
            var rows=new ArrayList<Map.Entry<Object,Object>>();
            try{if(!with(map,()->{KnownContainers.Image image=KnownContainers.snapshot(map);
                for(KnownContainers.Row row:image.rows)rows.add(new AbstractMap.SimpleImmutableEntry<>(row.key,row.value));
                if(!KnownContainers.unchanged(image))throw new IllegalAccessException("SOURCE_JDK_MAP_CHANGED");
            }))return null;return rows;}catch(ReflectiveOperationException unavailable){return null;}
        }
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
        var rows=new ArrayList<Map.Entry<Object,Object>>();
        try{
            if(!with(map,()->{
                // The receiver is an exact JDK map; these are its own entries, not supplied getters.
                for(var row:((Map<?,?>)map).entrySet())rows.add(new AbstractMap.SimpleImmutableEntry<>(row.getKey(),row.getValue()));
            }))return null;
            return rows;
        }catch(ReflectiveOperationException|ConcurrentModificationException unavailable){return null;}
    }
    static Object[] peek(Object map,Object key) {
        if(!supported(map))return null;
        if(primitiveMap(map)){
            Object[][] result={null};try{if(!with(map,()->{Map<?,?> actual=(Map<?,?>)map;boolean present=actual.containsKey(key);result[0]=new Object[]{present,present?actual.get(key):null};}))return null;}
            catch(ReflectiveOperationException unavailable){return null;}return result[0];
        }
        if(!supportedKey(key))return null;
        List<Map.Entry<Object,Object>> rows=entries(map);if(rows==null)return null;
        for(var row:rows)if(key==row.getKey()||key!=null&&key.equals(row.getKey()))return new Object[]{true,row.getValue()};
        return new Object[]{false,null};
    }
    static long revision(Object map) {
        if(KnownContainers.supported(map))try{return KnownContainers.revision(map);}catch(ReflectiveOperationException unavailable){return -1;}
        if(scopedFastutil(map))try{return (long)PlatformAccess.declaredMethod(Class.forName("dev.ronova.pro.bootstrap.ControlBridge",false,null),"sourceIndexRevision",Object.class).invoke(null,map);}
        catch(ReflectiveOperationException unavailable){return -1;}
        try{
            if(hashBacked(map)||map!=null&&map.getClass()==ArrayList.class)return Integer.toUnsignedLong((Integer)field(map,"modCount"));
            if(groupSet(map))return revision(setMap(map));
        }catch(ReflectiveOperationException unavailable){return -1;}
        return removedJdkContainer(map)?-1:0;
    }
    static final class Cursor {
        final Object map;
        Object[] root;Object next,primitiveKeys;
        KnownContainers.Image known;
        int bucket;long revision=-1;
        String gap="";
        boolean complete,removed,setElements;
        Cursor(Object map){this.map=map;}
        private void reset(Object[] root,long revision){
            this.root=root;bucket=0;next=null;this.revision=revision;complete=false;removed=false;known=null;gap="";
        }
    }
    @FunctionalInterface interface Visitor {
        boolean entry(Object key,Object value,Object node,Cursor cursor)throws ReflectiveOperationException,java.io.IOException;
    }
    private record MapRow(Object key,Object value) { }
    /** Traverse only the existing exact backends; every removal rechecks the actual selected identity. */
    static boolean visit(Object map,Cursor cursor,int budget,Visitor visitor)throws ReflectiveOperationException,java.io.IOException {
        if(cursor.map!=map||!groupMap(map)&&!fastSet(map)&&!KnownContainers.supported(map))throw new IllegalArgumentException("ACTUAL_MAP_CURSOR_REQUIRED");
        if(KnownContainers.supported(map))return visitKnown(map,cursor,budget,visitor);
        if(fastSet(map))return visitSet(map,cursor,budget,visitor);
        if(primitiveMap(map))return visitPrimitive(map,cursor,budget,visitor);
        java.io.IOException[] failure={null};
        boolean ran=with(map,()->{
            long version=revision(map);if(version<0)throw new IllegalAccessException("SOURCE_MAP_REVISION_UNAVAILABLE");
            if(cursor.root==null||cursor.revision!=version){
                List<Map.Entry<Object,Object>> rows=entries(map);if(rows==null)throw new IllegalAccessException("SOURCE_MAP_BUSY");
                Object[] snapshot=new Object[rows.size()];int i=0;
                for(var row:rows)snapshot[i++]=new MapRow(row.getKey(),row.getValue());
                cursor.reset(snapshot,version);
            }
            int remaining=budget;
            while(remaining-->0&&cursor.bucket<cursor.root.length){
                MapRow row=(MapRow)cursor.root[cursor.bucket];cursor.next=row;cursor.removed=false;
                try{if(!visitor.entry(row.key,row.value,row,cursor))break;}
                catch(java.io.IOException unavailable){failure[0]=unavailable;break;}
                long after=revision(map);
                if(!cursor.removed&&cursor.revision!=after){cursor.reset(null,after);return;}
                cursor.revision=after;cursor.bucket++;
            }
            if(cursor.bucket>=cursor.root.length)cursor.complete=true;
        });
        if(failure[0]!=null)throw failure[0];return ran;
    }
    private static boolean visitKnown(Object carrier,Cursor cursor,int budget,Visitor visitor)throws ReflectiveOperationException,java.io.IOException{
        java.io.IOException[] failure={null};
        boolean ran=with(carrier,()->{
            if(cursor.known==null||!KnownContainers.unchanged(cursor.known)){
                KnownContainers.Image image=KnownContainers.snapshot(carrier);cursor.reset(image.rows,image.revision);cursor.known=image;
            }
            int remaining=budget;
            while(remaining-->0&&cursor.bucket<cursor.root.length){
                KnownContainers.Row row=(KnownContainers.Row)cursor.root[cursor.bucket];cursor.next=row;cursor.removed=false;
                try{if(!visitor.entry(row.key,row.value,row,cursor))break;}
                catch(java.io.IOException unavailable){failure[0]=unavailable;break;}
                if(cursor.removed){
                    KnownContainers.Image image=KnownContainers.snapshot(carrier);cursor.reset(image.rows,image.revision);cursor.known=image;
                }else if(cursor.revision!=revision(carrier)){cursor.reset(null,revision(carrier));return;}
                else cursor.bucket++;
            }
            if(cursor.root!=null&&cursor.bucket>=cursor.root.length){
                if(!KnownContainers.unchanged(cursor.known)){cursor.reset(null,revision(carrier));return;}
                cursor.complete=true;
            }
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
        // OpenHashSet shifts and can rehash every surviving key during removeEntry.
        // Its layout stores no cached hash for an unknown object's implementation.
        for(int i=0;i<n;i++)if(i!=at&&keys[i]!=null&&!supportedKey(keys[i]))
            throw new IllegalStateException("SOURCE_SET_KEY_METHOD_UNAVAILABLE");
        Method remove=PlatformAccess.declaredMethod(set.getClass(),at==n?"removeNullEntry":"removeEntry",at==n?new Class<?>[0]:new Class<?>[]{int.class});remove.setAccessible(true);
        if(!Boolean.TRUE.equals(at==n?remove.invoke(set):remove.invoke(set,at)))return false;
        Object[] after=(Object[])field(set,"key");int end=(Integer)field(set,"n");
        for(int i=0;i<=end;i++)if(setOccupied(set,after,i,end)&&after[i]==key)throw new IllegalStateException("SOURCE_SET_REMOVAL_READBACK_FAILED");
        cursor.removed=true;return true;
    }
    /** Remove only the visited identity; unknown object-key hash/equals methods stay uncalled. */
    static boolean removeIdentityVisited(Cursor cursor,Object key)throws ReflectiveOperationException {
        if(cursor.next instanceof KnownContainers.Row row&&row.key==key)return removeKnown(cursor,row);
        if(fastSet(cursor.map))return removeSetIdentity(cursor,key);
        if(cursor.next instanceof MapRow row&&row.key==key)return removeJdkRow(cursor,row);
        return false;
    }
    /** All original children have been retained by the caller before this exact private backend is cleared. */
    static boolean clearPrivate(Cursor cursor,Object carrier,ProRuntime runtime,ProRuntime.Subject group)throws ReflectiveOperationException {
        boolean[] cleared={false};
        return with(cursor.map,()->{
            if(!privateContainer(carrier,runtime,group)||!cursor.complete)return;
            if(revision(cursor.map)!=cursor.revision){cursor.reset(null,revision(cursor.map));return;}
            if(KnownContainers.supported(cursor.map)){
                if(cursor.known==null||!KnownContainers.unchanged(cursor.known))return;
                for(KnownContainers.Row row:cursor.known.rows)if(!(cursor.setElements?privateElement(row.key,runtime,group):privateEntry(row.key,row.value,runtime,group)))return;
                cleared[0]=KnownContainers.clear(cursor.known);changed(carrier);return;
            }
            List<Map.Entry<Object,Object>> rows=entries(cursor.map);if(rows==null)return;
            for(var row:rows)if(!(cursor.setElements?privateElement(row.getKey(),runtime,group):privateEntry(row.getKey(),row.getValue(),runtime,group)))return;
            if(fastSet(cursor.map))((Set<?>)cursor.map).clear();else ((Map<?,?>)cursor.map).clear();
            if(fastSet(cursor.map)?!((Set<?>)cursor.map).isEmpty():!((Map<?,?>)cursor.map).isEmpty())throw new IllegalStateException("GROUP_PRIVATE_CONTAINER_CLEAR_READBACK_FAILED");
            changed(carrier);cleared[0]=true;
        })&&cleared[0];
    }
    static void removeVisited(Cursor cursor,Object target,ProRuntime runtime,ProRuntime.Subject group)throws ReflectiveOperationException {
        removeVisited(cursor,target,runtime,group,false);
    }
    static void removeVisited(Cursor cursor,Object target,ProRuntime runtime,ProRuntime.Subject group,boolean privateContents)throws ReflectiveOperationException {
        if(target instanceof KnownContainers.Row row&&KnownContainers.supported(cursor.map)){
            if(!RecoverySources.tryRecordGraph())throw new IllegalStateException("GROUP_RECORD_GRAPH_BUSY");
            try{
                boolean selectedKey=selectedElement(row.key,runtime,group),selectedValue=!cursor.setElements&&selectedElement(row.value,runtime,group);
                if(!selectedKey&&!selectedValue&&!(privateContents&&(cursor.setElements?privateElement(row.key,runtime,group):privateEntry(row.key,row.value,runtime,group))))return;
                if(selectedKey)validateRecordKey(row.key,runtime,group);if(selectedValue)validateRecordKey(row.value,runtime,group);
                removeKnown(cursor,row);
            }finally{RecoverySources.releaseRecordGraph();}return;
        }
        if(fastSet(cursor.map)){
            if(!RecoverySources.tryRecordGraph())throw new IllegalStateException("GROUP_RECORD_GRAPH_BUSY");
            try{Object value=cursor.next;if(selectedElement(value,runtime,group)||privateContents&&privateElement(value,runtime,group)){validateRecordKey(value,runtime,group);removeSetIdentity(cursor,value);}}
            finally{RecoverySources.releaseRecordGraph();}return;
        }
        if(primitiveMap(cursor.map)){
            if(!RecoverySources.tryRecordGraph())throw new IllegalStateException("GROUP_RECORD_GRAPH_BUSY");
            try{
                Object map=cursor.map,keys=field(map,"key");Object[] values=(Object[])field(map,"value");int n=(Integer)field(map,"n"),at=(Integer)target;
                if(keys!=cursor.primitiveKeys||values!=cursor.root||revision(map)!=cursor.revision||at>n||!primitiveOccupied(map,keys,at,n))return;
                Object value=values[at];if(!selectedElement(value,runtime,group)&&!(privateContents&&privateElement(value,runtime,group)))return;
                validateRecordKey(value,runtime,group);
                Method remove=PlatformAccess.declaredMethod(map.getClass(),at==n?"removeNullEntry":"removeEntry",at==n?new Class<?>[0]:new Class<?>[]{int.class});remove.setAccessible(true);
                Object key=primitiveKey(map,keys,at);
                if(at==n)remove.invoke(map);else remove.invoke(map,at);
                if(((Map<?,?>)map).containsKey(key)&&((Map<?,?>)map).get(key)==value)throw new IllegalStateException("GROUP_SOURCE_INDEX_REMOVAL_REFUSED");
                cursor.removed=true;
            }finally{RecoverySources.releaseRecordGraph();}return;
        }
        if(supported(cursor.map)&&target instanceof MapRow row){
            if(!RecoverySources.tryRecordGraph())throw new IllegalStateException("GROUP_RECORD_GRAPH_BUSY");
            try{
                boolean selectedKey=selectedElement(row.key,runtime,group);
                boolean selectedValue=!cursor.setElements&&selectedElement(row.value,runtime,group);
                if(!selectedKey&&!selectedValue&&!(privateContents&&(cursor.setElements?privateElement(row.key,runtime,group):privateEntry(row.key,row.value,runtime,group))))return;
                if(selectedKey)validateRecordKey(row.key,runtime,group);
                if(selectedValue)validateRecordKey(row.value,runtime,group);
                removeJdkRow(cursor,row);
            }finally{RecoverySources.releaseRecordGraph();}return;
        }
        throw new IllegalStateException(unavailable(cursor.map));
    }
    private static boolean removeKnown(Cursor cursor,KnownContainers.Row row)throws ReflectiveOperationException{
        if(cursor.known==null)return false;
        String gap=KnownContainers.removalGap(cursor.known,row);if(!gap.isEmpty()){cursor.gap=gap;return false;}
        RetiringList previous=RETIRING_LIST.get();Object backing=KnownContainers.arrayBacking(cursor.map);
        if(backing!=null)RETIRING_LIST.set(new RetiringList(cursor.map,backing,cursor.known.slots));
        boolean removed;
        try{removed=KnownContainers.remove(cursor.known,row);}
        finally{if(backing!=null){if(previous==null)RETIRING_LIST.remove();else RETIRING_LIST.set(previous);}}
        if(!removed)return false;
        cursor.removed=true;cursor.revision=revision(cursor.map);changed(cursor.map);return true;
    }
    /** Creation attribution is required; an observed outside holder or source prevents whole-carrier disposal. */
    static boolean privateContainer(Object carrier,ProRuntime runtime,ProRuntime.Subject group){
        if(carrier==null||group==null||!group.terminal||runtime.groupSubject(carrier)!=group)return false;
        if(!privateOwners(carrier,runtime,group))return false;
        if(KnownContainers.supported(carrier))try{
            Object backing=KnownContainers.arrayBacking(carrier);
            if(backing!=null&&(!privateOwners(backing,runtime,group)||runtime.groupSubject(backing)!=null&&runtime.groupSubject(backing)!=group))return false;
        }catch(ReflectiveOperationException unavailable){return false;}
        return true;
    }
    private static boolean privateOwners(Object carrier,ProRuntime runtime,ProRuntime.Subject group){
        for(Owner owner:OWNERS.getOrDefault(carrier,List.of())){
            var source=owner.source.get();if(!currentOwner(owner,carrier))continue;
            if(source.owner!=runtime.recoverySources||runtime.groupSubject(source.holder())!=group)return false;
            for(UUID subject:source.subjects)if(runtime.canonicalSubject(subject)!=group)return false;
        }
        return true;
    }
    static boolean privateEntry(Object key,Object value,ProRuntime runtime,ProRuntime.Subject group){
        return privateElement(key,runtime,group)&&privateElement(value,runtime,group);
    }
    static boolean privateElement(Object value,ProRuntime runtime,ProRuntime.Subject group){
        return value==null||RecoveryRecords.immutableScalar(value)||selectedElement(value,runtime,group);
    }
    static boolean selectedElement(Object value,ProRuntime runtime,ProRuntime.Subject group){
        return value!=null&&(runtime.groupSubject(value)==group||member(value,runtime,group));
    }
    static boolean unresolvedElement(Object value,ProRuntime runtime,ProRuntime.Subject group){
        if(privateElement(value,runtime,group)||runtime.groupSubject(value)!=null)return false;
        if(value instanceof net.minecraft.world.entity.Entity&&runtime.recoveryTasks.subjectOf(value)!=null)return false;
        if(value instanceof net.minecraft.nbt.CompoundTag tag&&RecoverySources.subject(runtime.recoverySources,tag)!=null)return false;
        try{
            Class<?> bridge=Class.forName("dev.ronova.pro.bootstrap.ControlBridge",false,null);
            if(Boolean.TRUE.equals(PlatformAccess.declaredMethod(bridge,"objectSourceUnknown",Object.class).invoke(null,value)))return true;
            Module[] origins=(Module[])PlatformAccess.declaredMethod(bridge,"objectSources",Object.class).invoke(null,value);
            if(origins.length==0)return true;for(Module origin:origins)if(origin==null)return true;return false;
        }catch(ReflectiveOperationException unavailable){return true;}
    }
    static Object sequenceValue(Object carrier,int index)throws ReflectiveOperationException{
        if(!KnownContainers.sequence(carrier))throw new IllegalAccessException("NESTED_SEQUENCE_BACKEND_CHANGED");
        Object[] value={null};if(!with(carrier,()->{
            KnownContainers.Image image=KnownContainers.snapshot(carrier);value[0]=index>=0&&index<image.rows.length?image.rows[index].value:null;
            if(!KnownContainers.unchanged(image))throw new IllegalAccessException("NESTED_SEQUENCE_CHANGED");
        }))throw new IllegalAccessException("NESTED_SEQUENCE_BUSY");return value[0];
    }
    @SuppressWarnings("unchecked") private static boolean removeJdkRow(Cursor cursor,MapRow row)throws ReflectiveOperationException{
        if(hashBacked(cursor.map)){
            for(var actual:((Map<?,?>)cursor.map).entrySet())if(actual.getKey()==row.key&&actual.getValue()==row.value){
                // removeNode consumes the node's cached hash. Both comparisons use our pure
                // identity matcher, so no method of a supplied key or value runs during unlink.
                int hash=(Integer)field(actual,"hash");
                Method remove=PlatformAccess.declaredMethod(HashMap.class,"removeNode",int.class,Object.class,Object.class,boolean.class,boolean.class);
                if(!remove.trySetAccessible())throw new IllegalAccessException("SOURCE_MAP_IDENTITY_REMOVE_UNAVAILABLE");
                Object removed=remove.invoke(cursor.map,hash,new IdentityMatch(row.key),new IdentityMatch(row.value),true,true);
                if(removed==null)return false;
                if(removed!=actual)throw new IllegalStateException("SOURCE_MAP_IDENTITY_REMOVE_CHANGED");
                cursor.removed=true;cursor.revision=revision(cursor.map);changed(cursor.map);return true;
            }
            return false;
        }
        if(!supportedKey(row.key))throw new IllegalStateException("SOURCE_MAP_KEY_METHOD_UNAVAILABLE");
        Map<Object,Object> map=(Map<Object,Object>)cursor.map;boolean[] removed={false};
        if(row.value==null)removed[0]=map.remove(row.key,null);
        else map.computeIfPresent(row.key,(key,value)->{
            if(value!=row.value)return value;
            removed[0]=true;return null;
        });
        if(removed[0]){
            cursor.removed=true;cursor.revision=revision(map);changed(map);
        }
        return removed[0];
    }
    private static final class IdentityMatch {
        private final Object expected;
        IdentityMatch(Object expected){this.expected=expected;}
        @Override public boolean equals(Object value){return value==expected;}
        @Override public int hashCode(){return System.identityHashCode(expected);}
    }
    private static Object field(Object owner,String name)throws ReflectiveOperationException {
        Field field=slot(owner.getClass(),name);return field.get(owner);
    }
    private static Field slot(Class<?> type,String name)throws ReflectiveOperationException {
        for(Class<?> owner=type;owner!=null;owner=owner.getSuperclass())try {
            Field field=PlatformAccess.declaredField(owner,name);field.setAccessible(true);return field;
        }catch(NoSuchFieldException absent) { }
        throw new NoSuchFieldException(name);
    }
    private static void validateRecordKey(Object value,ProRuntime runtime,ProRuntime.Subject group){
        Object target=RecoveryRecords.ownedTarget(value,runtime,true);
        if(target instanceof net.minecraft.nbt.CompoundTag tag){
            UUID subject=RecoverySources.subject(runtime.recoverySources,tag);
            if(subject==null||!group.members.contains(subject))throw new IllegalStateException("GROUP_MAP_RECORD_ORIGIN_UNRESOLVED");
            String gap=RecoverySources.recordGraphGap(runtime.recoverySources,tag,subject);
            if(!gap.isEmpty())throw new IllegalStateException(gap);
        }
    }
    private static boolean member(Object value,ProRuntime runtime,ProRuntime.Subject group) {
        Object target=RecoveryRecords.ownedTarget(value,runtime,true);
        UUID id=target instanceof net.minecraft.world.entity.Entity?runtime.recoveryTasks.subjectOf(target):
                target instanceof net.minecraft.nbt.CompoundTag tag?RecoverySources.subject(runtime.recoverySources,tag):null;
        return id!=null&&group.members.contains(id);
    }
    @FunctionalInterface interface Action { void run() throws ReflectiveOperationException; }
    static boolean with(Object map,Action action)throws ReflectiveOperationException {
        if(scopedFastutil(map)){
            if(!primitiveReady(map))return false;
            ReflectiveOperationException[] thrown={null};
            Runnable run=()->{try{action.run();}catch(ReflectiveOperationException failure){thrown[0]=failure;}};
            try{
                boolean ran=Boolean.TRUE.equals(PlatformAccess.declaredMethod(Class.forName("dev.ronova.pro.bootstrap.ControlBridge",false,null),"withSourceIndex",Object.class,Runnable.class).invoke(null,map,run));
                if(thrown[0]!=null)throw thrown[0];return ran;
            }catch(InvocationTargetException failure){if(failure.getCause() instanceof RuntimeException error)throw error;if(failure.getCause() instanceof Error error)throw error;throw failure;}
        }
        if(KnownContainers.lockFree(map)||KnownContainers.blocking(map))return KnownContainers.withConcurrentScope(map,action);
        if(KnownContainers.supported(map)||hashBacked(map)||map!=null&&(map.getClass()==ConcurrentHashMap.class||map.getClass()==ArrayList.class
                ||map.getClass()==java.util.concurrent.atomic.AtomicReference.class)){
            synchronized(map){action.run();}return true;
        }
        if(map!=null&&map.getClass()==java.util.concurrent.CopyOnWriteArrayList.class){
            // This is the list's actual JDK writer lock, so identity selection and remove share it.
            Object lock=field(map,"lock");synchronized(lock){action.run();}return true;
        }
        if(groupSet(map))return with(setMap(map),action);
        return false;
    }
}
