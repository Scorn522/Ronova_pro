package dev.ronova.pro;

import dev.ronova.pro.mixin.Access;
import java.lang.ref.WeakReference;
import java.lang.reflect.*;
import java.util.*;
import net.minecraft.world.entity.Entity;

/** Real carrier fields and backing arrays discovered from an already bound world record. */
final class BackingPolicy {
    private record Owner(WeakReference<Entity> entity) { }
    private static final Map<Object,List<Owner>> OWNERS=Collections.synchronizedMap(new WeakIdentityMap<>());
    private static final Map<Object,WeakReference<Object>> ARRAY_VALUES=Collections.synchronizedMap(new WeakIdentityMap<>());
    private static final Map<Object,WeakReference<Access.Sections>> SECTION_KEYS=Collections.synchronizedMap(new WeakIdentityMap<>());
    private static final Set<Object> LINK_ARRAYS=Collections.newSetFromMap(Collections.synchronizedMap(new WeakIdentityMap<>()));
    private static final Map<Object,Map<Field,Object>> FIELDS=Collections.synchronizedMap(new WeakIdentityMap<>());
    private static final Map<Object,WeakReference<ProRuntime>> RESOURCES=Collections.synchronizedMap(new WeakIdentityMap<>());
    private static final Set<Object> SHARED_RESOURCES=Collections.newSetFromMap(Collections.synchronizedMap(new WeakIdentityMap<>()));
    private static final StackWalker CALLER=StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE);
    private static volatile Object unsafe;
    private static volatile Method fieldOffset,staticOffset,arrayBase,arrayScale;
    private BackingPolicy() { }
    static void initialize(Class<?> bridge)throws ReflectiveOperationException {
        Class<?> agent=Class.forName("dev.ronova.pro.agent.RecoveryAgent",false,ClassLoader.getSystemClassLoader());
        Class<?>[] carriers={net.minecraft.world.entity.Entity.class,net.minecraft.server.level.ServerLevel.class,net.minecraft.server.players.PlayerList.class,
            net.minecraft.world.level.Level.class,net.minecraft.server.level.ChunkMap.class,
            net.minecraft.world.level.entity.PersistentEntitySectionManager.class,
            net.minecraft.world.level.entity.EntityLookup.class,net.minecraft.world.level.entity.EntitySectionStorage.class,
            net.minecraft.world.level.entity.EntitySection.class,net.minecraft.world.level.entity.EntityTickList.class,
            net.minecraft.util.ClassInstanceMultiMap.class,net.minecraft.network.syncher.SynchedEntityData.class,
            it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap.class,it.unimi.dsi.fastutil.ints.Int2ObjectLinkedOpenHashMap.class,
            it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap.class};
        agent.getMethod("openBackingAccess",Class.class,Class[].class).invoke(null,BackingPolicy.class,carriers);
        Class<?> type=Class.forName("sun.misc.Unsafe");Field singleton=type.getDeclaredField("theUnsafe");singleton.setAccessible(true);unsafe=singleton.get(null);
        fieldOffset=type.getMethod("objectFieldOffset",Field.class);staticOffset=type.getMethod("staticFieldOffset",Field.class);
        arrayBase=type.getMethod("arrayBaseOffset",Class.class);arrayScale=type.getMethod("arrayIndexScale",Class.class);
        var lookup=java.lang.invoke.MethodHandles.lookup();
        bridge.getMethod("installBackingPolicy",Class.class,java.lang.invoke.MethodHandle.class,java.lang.invoke.MethodHandle.class,java.lang.invoke.MethodHandle.class,java.lang.invoke.MethodHandle.class,java.lang.invoke.MethodHandle.class)
            .invoke(null,ProRuntime.class,lookup.findStatic(BackingPolicy.class,"known",java.lang.invoke.MethodType.methodType(boolean.class,Object.class)),
                lookup.findStatic(BackingPolicy.class,"elementDenied",java.lang.invoke.MethodType.methodType(boolean.class,Object.class,Object.class,Object.class)),
                lookup.findStatic(BackingPolicy.class,"arrayDenied",java.lang.invoke.MethodType.methodType(boolean.class,Object.class,int.class,Object.class)),
                lookup.findStatic(BackingPolicy.class,"offsetDenied",java.lang.invoke.MethodType.methodType(boolean.class,Object.class,long.class,Object.class)),
                lookup.findStatic(BackingPolicy.class,"memoryDenied",java.lang.invoke.MethodType.methodType(boolean.class,Object.class,long.class,long.class)));
    }
    static boolean known(Object object) {return object!=null&&(OWNERS.containsKey(object)||SourceMaps.observed(object));}
    private static void owner(Object object,Entity entity) {
        if(object==null)return;
        synchronized(OWNERS) {
            var list=new ArrayList<Owner>();for(Owner owner:OWNERS.getOrDefault(object,List.of())) {
                Entity prior=owner.entity.get();if(prior==entity)return;if(prior!=null)list.add(owner);
            }
            list.add(new Owner(new WeakReference<>(entity)));OWNERS.put(object,List.copyOf(list));
        }
    }
    private static boolean carrier(Object value) {
        return value!=null&&(value.getClass().isArray()||value instanceof Map<?,?>||value instanceof Collection<?>
                ||value instanceof Access.Server||value instanceof Access.Players||value instanceof Access.EntityState||value instanceof Access.Data
                ||value instanceof Access.Callback||value instanceof Access.Tracked
                ||value instanceof Access.Lookup||value instanceof Access.Manager||value instanceof Access.Sections||value instanceof Access.Section
                ||value instanceof Access.Group||value instanceof Access.Ticks||value instanceof Access.Tracking);
    }
    static boolean capture(EntityRecords records,Entity entity) {
        ArrayDeque<Object> queue=new ArrayDeque<>();Collections.addAll(queue,entity,records.level,records.manager,records.lookup,records.sections,records.ticks,records.tracking);
        if(entity instanceof net.minecraft.server.level.ServerPlayer)queue.add(records.level.getServer().getPlayerList());
        Set<Object> seen=Collections.newSetFromMap(new IdentityHashMap<>());
        boolean complete=true;
        while(!queue.isEmpty()&&seen.size()<2048) {
            Object object=queue.removeFirst();if(!seen.add(object))continue;owner(object,entity);
            Set<Object> expected=Collections.newSetFromMap(new IdentityHashMap<>());
            if(object instanceof Access.Server row) {
                Collections.addAll(expected,row.pro$manager(),row.pro$ticks());
                if(entity instanceof net.minecraft.server.level.ServerPlayer)expected.add(row.pro$players());
            }
            if(object instanceof Access.Players row)Collections.addAll(expected,row.pro$players(),row.pro$byUuid());
            if(object instanceof Access.Manager row)Collections.addAll(expected,row.pro$lookup(),row.pro$sections(),row.pro$known());
            if(object instanceof Access.Lookup row)Collections.addAll(expected,row.pro$ids(),row.pro$uuids());
            if(object instanceof Access.Ticks row)Collections.addAll(expected,row.pro$active(),row.pro$passive());
            if(object instanceof Access.Tracking row)expected.add(row.pro$tracked());
            if(object instanceof Access.EntityState row)Collections.addAll(expected,row.pro$data(),row.pro$callback());
            if(object instanceof Access.Sections row) {
                expected.add(row.pro$all());
                expected.add(row.pro$keys());SECTION_KEYS.put(row.pro$keys(),new WeakReference<>(row));
                Object callback=((Access.EntityState)entity).pro$callback();
                Object section=callback instanceof Access.Callback actual?actual.pro$section():null;
                if(section instanceof Access.Section access&&((Access.Group)access.pro$storage()).pro$all().stream().anyMatch(value->value==entity))
                    queue.add(section);
                else complete=false;
            }
            if(object instanceof Access.Section row)expected.add(row.pro$storage());
            if(object instanceof Access.Group row) {
                Collections.addAll(expected,row.pro$all(),row.pro$classes());
                for(List<Entity> values:row.pro$classes().values())if(values.stream().anyMatch(value->value==entity))queue.add(values);
            }
            if(object instanceof Access.Data row)expected.add(row.pro$items());
            if(object instanceof Access.Callback row)Collections.addAll(expected,row.pro$entity(),row.pro$section());
            if(object instanceof Access.Tracked row)Collections.addAll(expected,row.pro$entity(),row.pro$seen());
            if(object instanceof Object[] array) {for(Object value:array)if((node(value)||value instanceof Access.Tracked)&&target(value,entity))queue.add(value);continue;}
            if(object.getClass().isArray())continue;
            if(node(object)) {
                if(nodeFields(object.getClass()).length==0)complete=false;
                for(Field field:nodeFields(object.getClass()))try {Object value=field.get(object);if(value!=null)expected.add(value);}catch(IllegalAccessException unavailable) {complete=false;}
            }
            if(sectionTree(object))for(Field field:sectionFields(object.getClass()))try {
                Object value=field.get(object);if(value!=null)expected.add(value);
            }catch(IllegalAccessException unavailable){complete=false;}
            Map<Field,Object> observed=new HashMap<>();
            for(Class<?> type=object.getClass();type!=null&&type!=Object.class;type=type.getSuperclass())if(inspectType(object,type))for(Field field:type.getDeclaredFields()) {
                if(Modifier.isStatic(field.getModifiers()))continue;
                if(!accessible(field)) {
                    if(carrier(object)||node(object))complete=false;
                    continue;
                }
                try {
                    Object value=field.get(object);
                    boolean backing=object.getClass()==ArrayList.class&&field.getName().equals("elementData")
                            ||object.getClass()==HashSet.class&&field.getName().equals("map")
                            ||object.getClass()==HashMap.class&&field.getName().equals("table")
                            ||object.getClass().getName().startsWith("it.unimi.dsi.fastutil.")&&Set.of("key","value","link").contains(field.getName());
                    boolean counter=(object instanceof Collection<?>||object instanceof Map<?,?>||node(object)||sectionTree(object))&&field.getType().isPrimitive();
                    if(counter)observed.put(field,Boolean.TRUE);
                    else if(sectionTree(object)) {
                        observed.put(field,new WeakReference<>(value));if(value!=null)queue.add(value);
                        if(field.getName().equals("dirPath")&&value!=null)LINK_ARRAYS.add(value);
                    }
                    else if(value!=null&&(expected.contains(value)||backing)) {observed.put(field,new WeakReference<>(value));queue.add(value);}
                }catch(IllegalAccessException unavailable) {complete=false;}
            }
            FIELDS.put(object,Map.copyOf(observed));arrayPeers(object,observed);
        }
        return queue.isEmpty()&&complete;
    }
    private static boolean accessible(Field field) {
        try {return field.trySetAccessible();}catch(RuntimeException unavailable){return false;}
    }
    private static boolean inspectType(Object object,Class<?> type) {
        if(object instanceof Entity)return type==Entity.class;
        if(object instanceof net.minecraft.server.level.ServerLevel)return type==net.minecraft.server.level.ServerLevel.class;
        if(object instanceof Access.Players)return type==net.minecraft.server.players.PlayerList.class;
        if(object instanceof Access.Manager)return type==net.minecraft.world.level.entity.PersistentEntitySectionManager.class;
        if(object instanceof Access.Lookup)return type==net.minecraft.world.level.entity.EntityLookup.class;
        if(object instanceof Access.Sections)return type==net.minecraft.world.level.entity.EntitySectionStorage.class;
        if(object instanceof Access.Section)return type==net.minecraft.world.level.entity.EntitySection.class;
        if(object instanceof Access.Ticks)return type==net.minecraft.world.level.entity.EntityTickList.class;
        if(object instanceof Access.Tracking)return type==net.minecraft.server.level.ChunkMap.class;
        if(object instanceof Access.Group)return type==net.minecraft.util.ClassInstanceMultiMap.class;
        if(object instanceof Access.Data)return type==net.minecraft.network.syncher.SynchedEntityData.class;
        if(object instanceof Access.Callback||object instanceof Access.Tracked)return type.getName().startsWith("net.minecraft.");
        if(node(object))return type.getName().startsWith("java.util.HashMap$");
        if(object instanceof Map<?,?>||object instanceof Collection<?>)return type.getName().startsWith("java.util.")||type.getName().startsWith("it.unimi.dsi.fastutil.");
        return type==object.getClass();
    }
    private static boolean target(Object value,Entity entity) {
        return value==entity||value instanceof Access.Tracked tracked&&tracked.pro$entity()==entity
                ||value instanceof UUID uuid&&uuid.equals(((Access.EntityState)entity).pro$uuid())
                ||value instanceof Access.Section row&&((Access.Group)row.pro$storage()).pro$all().stream().anyMatch(e->e==entity)
                ||node(value)&&nodeContains(value,entity);
    }
    static boolean elementDenied(Object container,Object old,Object next) {
        if(SourceMaps.denies(container,null,next))return true;
        if(old==next)return false;List<Owner> owners=OWNERS.get(container);if(owners==null)return false;
        if(next instanceof Entity entity&&ProRuntime.preventAdmission(entity))return true;
        if(next instanceof Access.Tracked tracked&&ProRuntime.preventAdmission(tracked.pro$entity()))return true;
        if(next instanceof Access.Section section)for(Entity entity:((Access.Group)section.pro$storage()).pro$all())if(ProRuntime.preventAdmission(entity))return true;
        for(Owner owner:owners) {Entity entity=owner.entity.get();if(entity!=null&&(target(old,entity)||sectionKey(container,old,entity))&&!SectionMoves.allows(container,entity)&&ProRuntime.preventIndexRemoval(entity))return true;}
        return false;
    }
    private static boolean sectionKey(Object container,Object value,Entity entity) {
        var reference=SECTION_KEYS.get(container);Access.Sections sections=reference==null?null:reference.get();
        if(sections==null||!(value instanceof Long key))return false;
        Object section=sections.pro$all().get(key.longValue());return section!=null&&target(section,entity);
    }
    static boolean protectedSectionKeys(Object container) {
        if(!SECTION_KEYS.containsKey(container))return false;
        for(Owner owner:OWNERS.getOrDefault(container,List.of())){Entity entity=owner.entity.get();if(entity!=null&&ProRuntime.preventIndexRemoval(entity))return true;}
        return false;
    }
    static boolean arrayDenied(Object array,int index,Object next) {
        if(!known(array)||array==null||!array.getClass().isArray()||index<0||index>=Array.getLength(array))return false;
        Object prior=Array.get(array,index);if(array.getClass().getComponentType().isPrimitive()?Objects.equals(prior,next):prior==next)return false;
        if(SourceMaps.primitiveBackingDenied(array))return true;
        if(elementDenied(array,null,next))return true;
        var peer=ARRAY_VALUES.get(array);Object values=peer==null?null:peer.get();
        if(values instanceof Object[] objects&&index<objects.length&&elementDenied(array,objects[index],null))return true;
        if(LINK_ARRAYS.contains(array))for(Owner owner:OWNERS.getOrDefault(array,List.of())) {Entity entity=owner.entity.get();if(entity!=null&&ProRuntime.preventIndexRemoval(entity))return true;}
        for(Owner owner:OWNERS.getOrDefault(array,List.of())) {Entity entity=owner.entity.get();if(entity!=null&&target(prior,entity)&&!target(next,entity)&&!SectionMoves.allows(array,entity)&&ProRuntime.preventIndexRemoval(entity))return true;}
        return false;
    }
    static boolean backingOf(Object holder,Object backing) {
        for(Object value:FIELDS.getOrDefault(holder,Map.of()).values())if(value instanceof WeakReference<?> ref&&ref.get()==backing)return true;
        return false;
    }
    /** Forget only this entity's old section carriers; other protected occupants keep their ownership. */
    static void sectionLeft(Object section,Entity entity) {
        ArrayDeque<Object> pending=new ArrayDeque<>();pending.add(section);
        Set<Object> seen=Collections.newSetFromMap(new IdentityHashMap<>());
        while(!pending.isEmpty()) {
            Object value=pending.removeFirst();if(value instanceof Entity||!seen.add(value))continue;
            for(Object ref:FIELDS.getOrDefault(value,Map.of()).values())if(ref instanceof WeakReference<?> weak) {
                Object child=weak.get();if(child!=null&&!(child instanceof Entity)&&carrier(child))pending.add(child);
            }
            if(value instanceof Object[] array)for(Object child:array)if(child!=null&&!(child instanceof Entity)&&(node(child)||child instanceof Collection<?>))pending.add(child);
            synchronized(OWNERS) {
                var old=OWNERS.get(value);if(old==null)continue;
                var remaining=old.stream().filter(owner->owner.entity.get()!=null&&owner.entity.get()!=entity).toList();
                if(remaining.isEmpty()) {OWNERS.remove(value);FIELDS.remove(value);ARRAY_VALUES.remove(value);LINK_ARRAYS.remove(value);}
                else OWNERS.put(value,remaining);
            }
        }
    }
    private static boolean node(Object value) {return value!=null&&value.getClass().getClassLoader()==null&&(value.getClass().getName().equals("java.util.HashMap$Node")||value.getClass().getName().equals("java.util.HashMap$TreeNode"));}
    private static final ClassValue<Field[]> NODE_FIELDS=new ClassValue<>() {
        protected Field[] computeValue(Class<?> type) {
            var result=new ArrayList<Field>();for(Class<?> c=type;c!=null&&c!=Object.class;c=c.getSuperclass())for(Field f:c.getDeclaredFields())
                if(!Modifier.isStatic(f.getModifiers())&&!f.getType().isPrimitive()&&accessible(f))result.add(f);
            return result.toArray(Field[]::new);
        }
    };
    private static boolean sectionTree(Object value) {
        return value!=null&&(value.getClass()==it.unimi.dsi.fastutil.longs.LongAVLTreeSet.class
                ||value.getClass().getClassLoader()==it.unimi.dsi.fastutil.longs.LongAVLTreeSet.class.getClassLoader()
                    &&value.getClass().getName().equals("it.unimi.dsi.fastutil.longs.LongAVLTreeSet$Entry"));
    }
    private static Field[] sectionFields(Class<?> type) {
        return Arrays.stream(type.getDeclaredFields()).filter(field->!Modifier.isStatic(field.getModifiers())&&!field.getType().isPrimitive()&&accessible(field)).toArray(Field[]::new);
    }
    private static Field[] nodeFields(Class<?> type) {return NODE_FIELDS.get(type);}
    private static boolean nodeContains(Object first,Entity entity) {
        Object current=first;Set<Object> seen=Collections.newSetFromMap(new IdentityHashMap<>());
        while(node(current)&&seen.size()<8192&&seen.add(current)) {
            Object next=null;
            for(Field field:nodeFields(current.getClass()))try {
                Object value=field.get(current);
                if(field.getName().equals("value")&&(value==entity||value instanceof Access.Tracked tracked&&tracked.pro$entity()==entity))return true;
                if(field.getName().equals("key")&&value instanceof UUID uuid&&uuid.equals(((Access.EntityState)entity).pro$uuid()))return true;
                if(field.getName().equals("next"))next=value;
            }catch(IllegalAccessException unavailable) {return true;}
            current=next;
        }
        // A cyclic or over-budget chain is not proof that the protected record is absent.
        return node(current);
    }
    private static void arrayPeers(Object object,Map<Field,Object> fields) {
        Object keys=null,values=null;
        for(var entry:fields.entrySet())if(entry.getValue() instanceof WeakReference<?> ref) {
            if(entry.getKey().getName().equals("key"))keys=ref.get();
            if(entry.getKey().getName().equals("value"))values=ref.get();
            if(entry.getKey().getName().equals("link")&&ref.get()!=null)LINK_ARRAYS.add(ref.get());
        }
        if(keys!=null&&keys.getClass().isArray()&&values instanceof Object[])ARRAY_VALUES.put(keys,new WeakReference<>(values));
    }
    static boolean originalWriter(Object receiver) {
        var frame=CALLER.walk(frames->frames.filter(f->{String name=f.getClassName();return !name.startsWith("dev.ronova.pro.")
                &&!name.equals("java.lang.reflect.Field")&&!name.startsWith("jdk.internal.reflect.")&&!name.equals("sun.misc.Unsafe");}).findFirst().orElse(null));
        if(frame==null)return false;
        Class<?> writer=frame.getDeclaringClass(),holder=receiver.getClass();
        if(writer.getNestHost()==holder.getNestHost())return true;
        if(sectionTree(receiver)&&writer==it.unimi.dsi.fastutil.longs.LongAVLTreeSet.class)return true;
        // fastutil is compiled for older Java: its real iterators have an enclosing class,
        // but do not necessarily carry Java 11 NestHost metadata.
        for(Class<?> enclosing=writer.getEnclosingClass();enclosing!=null;enclosing=enclosing.getEnclosingClass())
            if(enclosing==holder)return true;
        return false;
    }
    static boolean fieldDenied(Object receiver,Class<?> declaring,String name,Object next) {
        if(SourceMaps.carrierFieldDenied(receiver,declaring,name,next))return true;
        Map<Field,Object> fields=FIELDS.get(receiver);if(fields==null)return false;
        for(var entry:fields.entrySet()) {
            Field field=entry.getKey();if(field.getDeclaringClass()!=declaring||!field.getName().equals(name))continue;
            Object prior;try {prior=field.get(receiver);}catch(IllegalAccessException unavailable){return true;}
            if(field.getType().isPrimitive()?Objects.equals(prior,next):prior==next)return false;
            if(receiver instanceof Entity entity&&declaring==Entity.class
                    &&field.getType()==net.minecraft.world.level.entity.EntityInLevelCallback.class
                    &&next instanceof Access.Callback callback
                    &&ProRuntime.allowInitialGroupCallback(entity,callback))return false;
            boolean active=false,terminal=false;for(Owner owner:OWNERS.getOrDefault(receiver,List.of())) {Entity entity=owner.entity.get();active|=entity!=null&&ProRuntime.preventIndexRemoval(entity);terminal|=entity!=null&&ProRuntime.preventAdmission(entity);}
            if(receiver instanceof Access.Ticks&&originalWriter(receiver)) {
                // Vanilla swaps its two existing maps while the old active map is being iterated.
                // Keep both old identities until the RETURN publication hook captures the pair.
                if(next==null&&(name.equals("iterated")||name.equals("f_156905_")))return false;
                for(Object recorded:fields.values())if(recorded instanceof WeakReference<?> ref&&ref.get()==next)return false;
            }
            // Only the original implementation can publish an internal grow/copy. The new backing is tracked before publication.
            boolean internal=node(receiver)||sectionTree(receiver)||receiver instanceof Access.Callback||receiver instanceof Access.Tracked
                    ||receiver.getClass()==ArrayList.class||receiver.getClass()==HashMap.class||receiver.getClass().getName().startsWith("it.unimi.dsi.fastutil.");
            if((active||terminal)&&internal&&originalWriter(receiver)) {
                if(field.getType().isPrimitive())return false;
                if(node(receiver))return false; // HashMap's entry guard already decided the exact mutation.
                if(next==null&&sectionTree(receiver))return false;
                if(!carrier(next)&&!sectionTree(next))return true;
                for(Owner owner:OWNERS.getOrDefault(receiver,List.of())) {Entity entity=owner.entity.get();if(entity!=null)owner(next,entity);}
                var replacement=new HashMap<>(fields);replacement.put(field,new WeakReference<>(next));FIELDS.put(receiver,Map.copyOf(replacement));arrayPeers(receiver,replacement);
                if(sectionTree(next))adoptSectionTree(next,OWNERS.getOrDefault(receiver,List.of()));return false;
            }
            // The real insert/value-store boundary already rejects a new terminal record.
            // Never enumerate a map inside its sealed implementation's multi-field resize:
            // key/value/link/size are only a coherent tuple once that implementation returns.
            if(terminal&&containsTerminal(next))return true;
            return active;
        }
        return false;
    }
    private static void adoptSectionTree(Object first,List<Owner> owners) {
        ArrayDeque<Object> pending=new ArrayDeque<>();Set<Object> seen=Collections.newSetFromMap(new IdentityHashMap<>());pending.add(first);
        while(!pending.isEmpty()) {
            Object node=pending.removeFirst();if(!sectionTree(node)||!seen.add(node)||FIELDS.containsKey(node))continue;
            if(seen.size()>2048)throw new IllegalStateException("SECTION_TREE_CAPTURE_CAPACITY");
            var fields=new HashMap<Field,Object>();
            for(Owner owner:owners){Entity entity=owner.entity.get();if(entity!=null)owner(node,entity);}
            for(Field field:node.getClass().getDeclaredFields())if(!Modifier.isStatic(field.getModifiers())&&accessible(field))try {
                Object value=field.get(node);if(field.getType().isPrimitive())fields.put(field,Boolean.TRUE);
                else {fields.put(field,new WeakReference<>(value));if(sectionTree(value))pending.add(value);}
            }catch(IllegalAccessException unavailable){throw new IllegalStateException("SECTION_TREE_CAPTURE",unavailable);}
            FIELDS.put(node,Map.copyOf(fields));
        }
    }
    private static boolean containsTerminal(Object first) {
        if(first==null)return false;
        ArrayDeque<Object> queue=new ArrayDeque<>();queue.add(first);Set<Object> seen=Collections.newSetFromMap(new IdentityHashMap<>());
        while(!queue.isEmpty()&&seen.size()<2048) {
            Object value=queue.removeFirst();if(!seen.add(value))continue;
            if(value instanceof Entity entity&&ProRuntime.preventAdmission(entity))return true;
            if(value instanceof Access.Tracked tracked&&ProRuntime.preventAdmission(tracked.pro$entity()))return true;
            if(value instanceof Access.Section section)queue.add(((Access.Group)section.pro$storage()).pro$all());
            else if(value instanceof Map<?,?> map)for(Object item:map.values()){if(item!=null)queue.add(item);if(queue.size()>2048)return true;}
            else if(value instanceof Collection<?> values)for(Object item:values){if(item!=null)queue.add(item);if(queue.size()>2048)return true;}
            else if(value instanceof Object[] values)for(Object item:values){if(item!=null)queue.add(item);if(queue.size()>2048)return true;}
            else if(node(value))for(Field field:nodeFields(value.getClass()))try {Object child=field.get(value);if(child!=null)queue.add(child);if(queue.size()>2048)return true;}catch(IllegalAccessException unknown){return true;}
        }
        return !queue.isEmpty();
    }
    static void controllers(Class<?> bridge)throws ReflectiveOperationException {
        var roots=new ArrayList<Object>();
        for(Class<?> type:List.of(ProRuntime.class,RecoverySources.class,RecoveryTasks.class,BackingPolicy.class,SourceMaps.class))for(Field field:type.getDeclaredFields()) {
            if(!Modifier.isStatic(field.getModifiers())||!field.trySetAccessible())continue;
            if(Map.class.isAssignableFrom(field.getType())||Collection.class.isAssignableFrom(field.getType())||java.util.concurrent.ExecutorService.class.isAssignableFrom(field.getType())) {
                Object value=field.get(null);if(value!=null) {
                    roots.add(value);
                    if(value instanceof java.util.concurrent.ExecutorService)registerResource(value,null);
                }
            }
        }
        bridge.getMethod("registerControlObjects",Class.class,Object[].class).invoke(null,ProRuntime.class,roots.toArray());
    }
    static void runtimeControllers(ProRuntime runtime,Object... owners) {
        var roots=new ArrayList<Object>();
        for(Object owner:owners)if(owner!=null)for(Class<?> type=owner.getClass();type!=null&&type!=Object.class;type=type.getSuperclass())for(Field field:type.getDeclaredFields()) {
            if(Modifier.isStatic(field.getModifiers())||!field.trySetAccessible())continue;
            if(Map.class.isAssignableFrom(field.getType())||Collection.class.isAssignableFrom(field.getType())||java.util.concurrent.ExecutorService.class.isAssignableFrom(field.getType()))
                try {Object value=field.get(owner);if(value!=null)roots.add(value);}catch(IllegalAccessException unavailable){throw new IllegalStateException(unavailable);}
        }
        try {Class.forName("dev.ronova.pro.bootstrap.TaskBridge",false,null).getMethod("registerControlObjects",Class.class,Object[].class).invoke(null,ProRuntime.class,roots.toArray());}
        catch(ReflectiveOperationException unavailable){throw new IllegalStateException("RUNTIME_CONTROL_UNAVAILABLE",unavailable);}
    }
    static void resources(ProRuntime runtime,Object... owners) {
        for(Object owner:owners)if(owner!=null)for(Class<?> type=owner.getClass();type!=null&&type!=Object.class;type=type.getSuperclass())for(Field field:type.getDeclaredFields()) {
            if(Modifier.isStatic(field.getModifiers())||!java.util.concurrent.ExecutorService.class.isAssignableFrom(field.getType())||!field.trySetAccessible())continue;
            try {Object executor=field.get(owner);if(executor!=null)registerResource(executor,runtime);}
            catch(IllegalAccessException unavailable) {throw new IllegalStateException("RESOURCE_BINDING_UNAVAILABLE",unavailable);}
        }
    }
    /** Follow only actual ExecutorService delegate fields of an owned wrapper, never a shared pool by name. */
    private static void registerResource(Object root,ProRuntime runtime) {
        ArrayDeque<Object> pending=new ArrayDeque<>();Set<Object> seen=Collections.newSetFromMap(new IdentityHashMap<>());
        pending.add(root);
        while(!pending.isEmpty()) {
            Object value=pending.removeFirst();if(!seen.add(value))continue;
            if(!(value instanceof java.util.concurrent.ExecutorService))throw new IllegalStateException("RESOURCE_DELEGATE_TYPE");
            if(runtime==null)SHARED_RESOURCES.add(value);else RESOURCES.put(value,new WeakReference<>(runtime));
            for(Class<?> type=value.getClass();type!=null&&type!=Object.class;type=type.getSuperclass())for(Field field:type.getDeclaredFields()) {
                if(Modifier.isStatic(field.getModifiers())||!java.util.concurrent.ExecutorService.class.isAssignableFrom(field.getType()))continue;
                try {
                    long offset=((Number)fieldOffset.invoke(unsafe,field)).longValue();
                    Object delegate=unsafe.getClass().getMethod("getObject",Object.class,long.class).invoke(unsafe,value,offset);
                    if(delegate!=null)pending.add(delegate);
                } catch(ReflectiveOperationException unavailable) {throw new IllegalStateException("RESOURCE_DELEGATE_UNAVAILABLE",unavailable);}
            }
            if(seen.size()>32)throw new IllegalStateException("RESOURCE_DELEGATE_DEPTH");
        }
    }
    static boolean resourceDenied(Object resource) {
        boolean shared=SHARED_RESOURCES.contains(resource);
        var owner=RESOURCES.get(resource);ProRuntime runtime=owner==null?null:owner.get();
        if(!shared&&(runtime==null||runtime.resourcesClosing()))return false;
        Class<?> caller=CALLER.walk(frames->frames.map(StackWalker.StackFrame::getDeclaringClass)
                .filter(type->type!=BackingPolicy.class&&!type.getName().startsWith("dev.ronova.pro.bootstrap.")
                    &&!type.getName().startsWith("java.util.concurrent.")).findFirst().orElse(null));
        return caller==null||!Set.of(ProRuntime.class,IntentJournal.class,RecoveryTasks.class,RecoveryStorage.class,RecoveryRecords.class,RecoveryReferences.class).contains(caller.getNestHost());
    }
    static boolean offsetDenied(Object receiver,long offset,Object next) {
        if(receiver instanceof Class<?> holder)return SourceMaps.staticHolderOffsetDenied(holder,offset,next,unsafe,staticOffset);
        if(receiver!=null&&SourceMaps.instanceHolderOffsetDenied(receiver,offset,next,unsafe,fieldOffset))return true;
        if(receiver==null||!known(receiver)||unsafe==null)return false;
        try {
            if(receiver.getClass().isArray()) {
                int base=(Integer)arrayBase.invoke(unsafe,receiver.getClass()),scale=(Integer)arrayScale.invoke(unsafe,receiver.getClass());
                long size=(long)scale*Array.getLength(receiver),relative=offset-base,width=writeWidth(next);
                if(scale<=0||relative<0||relative>size||width>size-relative)return true;
                int first=(int)(relative/scale),last=(int)((relative+width-1)/scale);
                for(int i=first;i<=last;i++)if(arrayDenied(receiver,i,first==last&&relative%scale==0?next:null))return true;
                return false;
            }
            for(Field field:FIELDS.getOrDefault(receiver,Map.of()).keySet()) {
                long at=(Long)fieldOffset.invoke(unsafe,field),size=field.getType().isPrimitive()?primitiveWidth(field.getType()):8;
                if(offset<at+size&&at<offset+writeWidth(next))
                    if(fieldDenied(receiver,field.getDeclaringClass(),field.getName(),offset==at&&writeWidth(next)==size?next:null))return true;
            }
        }catch(ReflectiveOperationException unavailable) {throw new IllegalStateException("BACKING_OFFSET_UNAVAILABLE",unavailable);}
        return false;
    }
    private static int primitiveWidth(Class<?> type) {return type==long.class||type==double.class?8:type==int.class||type==float.class?4:type==short.class||type==char.class?2:1;}
    private static int writeWidth(Object value) {return value instanceof Long||value instanceof Double||value==null||!(value instanceof Number||value instanceof Boolean||value instanceof Character)?8:value instanceof Integer||value instanceof Float?4:value instanceof Short||value instanceof Character?2:1;}
    /** Object-relative bulk writes must not cross an observed protected slot. */
    static boolean memoryDenied(Object receiver,long offset,long length) {
        if(receiver instanceof Class<?> holder&&length>0) {
            final long end;try {end=Math.addExact(offset,length);}catch(ArithmeticException overflow){return true;}
            for(Field field:holder.getDeclaredFields())if(Modifier.isStatic(field.getModifiers())&&!field.getType().isPrimitive())try {
                long at=(Long)staticOffset.invoke(unsafe,field);
                if(offset<at+8&&end>at&&SourceMaps.staticHolderRangeDenied(holder,field.getName()))return true;
            }catch(ReflectiveOperationException unavailable){return true;}
            return false;
        }
        if(receiver!=null&&SourceMaps.instanceHolderRangeDenied(receiver,offset,length,unsafe,fieldOffset))return true;
        if(length<=0||!known(receiver))return false;
        final long end;try {end=Math.addExact(offset,length);}catch(ArithmeticException overflow){return true;}
        try {
            if(receiver.getClass().isArray()) {
                int base=(Integer)arrayBase.invoke(unsafe,receiver.getClass()),scale=(Integer)arrayScale.invoke(unsafe,receiver.getClass());
                long extent=(long)base+(long)scale*Array.getLength(receiver);
                if(scale<=0||offset<base||end>extent)return true;
                long first=(offset-base)/scale,last=(end-1-base)/scale;
                if(last-first>=2048) {
                    for(Owner owner:OWNERS.getOrDefault(receiver,List.of())) {Entity entity=owner.entity.get();if(entity!=null&&ProRuntime.preventIndexRemoval(entity))return true;}
                    return false;
                }
                for(long i=first;i<=last;i++)if(arrayDenied(receiver,(int)i,null))return true;
                return false;
            }
            for(Field field:FIELDS.getOrDefault(receiver,Map.of()).keySet()) {
                long at=(Long)fieldOffset.invoke(unsafe,field),size=field.getType().isPrimitive()?Math.max(1,field.getType()==long.class||field.getType()==double.class?8:field.getType()==int.class||field.getType()==float.class?4:field.getType()==short.class||field.getType()==char.class?2:1):8;
                if(offset<at+size&&end>at&&fieldDenied(receiver,field.getDeclaringClass(),field.getName(),null))return true;
            }
        }catch(ReflectiveOperationException unavailable){throw new IllegalStateException("BACKING_MEMORY_UNAVAILABLE",unavailable);}
        return false;
    }
}
