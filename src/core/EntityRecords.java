package dev.ronova.pro;

import dev.ronova.pro.mixin.Access;
import java.util.*;
import java.util.function.BooleanSupplier;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.*;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.entity.*;

/** Exact reference operations on the actual registered Minecraft collections. Server thread only. */
public final class EntityRecords {
    private record Registration(java.lang.ref.WeakReference<Object> manager,Set<UUID> aliases) { }
    private static final Map<Entity,Registration> REGISTRATIONS=new WeakIdentityMap<>();
    public static boolean registeredUuid(Object manager,Set<Object> known,Object value,EntityAccess target) {
        // This is the registration/add path. A protected entity must be allowed to acquire its
        // first UUID slot; removal policy is enforced at the actual remove/write boundaries.
        boolean added=known.add(value); // The actual game mutation, once; a caller cannot supply a success receipt.
        if(added&&value instanceof UUID uuid&&target instanceof Entity entity
                &&((Access.EntityState)entity).pro$level() instanceof ServerLevel level&&level.getServer().isSameThread()
                &&((Access.Server)level).pro$manager()==manager&&((Access.Manager)manager).pro$known()==(Object)known) {
            synchronized(REGISTRATIONS) {
                Registration row=REGISTRATIONS.get(entity);
                if(row==null||row.manager.get()!=manager) { row=new Registration(new java.lang.ref.WeakReference<>(manager),new HashSet<>());REGISTRATIONS.put(entity,row); }
                row.aliases.add(uuid);
            }
        }
        return added;
    }
    final ServerLevel level;
    private Entity ownedEntity;
    private final java.lang.ref.WeakReference<Entity> observedEntity;
    private Entity originalVehicle;
    private List<Entity> originalPassengers;
    private final java.lang.ref.WeakReference<Entity> vehicleIdentity;
    private final List<java.lang.ref.WeakReference<Entity>> passengerIdentities;
    private boolean referencesReleased;
    final int id;
    final UUID uuid;
    final Access.Server server;
    final Access.Manager manager;
    final Access.Lookup lookup;
    final Access.Ticks ticks;
    final Access.Sections sections;
    final Access.Tracking tracking;
    final long section;
    final ClassLoader loader;
    final Class<?> definition;
    final boolean originallyTicking;
    private final Object idOwner, uuidOwner, knownOwner, sectionOwner, trackingOwner;
    private final Object visibilityOwner,sectionKeysOwner,tickActiveOwner,tickPassiveOwner,visibilityProvider,getterOwner;
    private final java.lang.ref.WeakReference<Object> capturedSection;
    private final LevelCallback<Entity> levelCallbacks;
    private final Set<UUID> ownedUuids=new HashSet<>();
    private boolean lifecycleUnresolved;
    private boolean currentCheckUnresolved;
    private int structuralStepsUnresolved;
    private EntityInLevelCallback capturedCallback;
    private final boolean capturedCanUpdate,capturedNoPhysics;
    private final net.minecraft.world.phys.AABB capturedBox;
    private final net.minecraft.world.phys.Vec3 capturedPosition;

    public EntityRecords(ServerLevel level, Entity entity) {
        if(!level.getServer().isSameThread()) throw new IllegalStateException("SERVER_THREAD_REQUIRED");
        this.level=level;ownedEntity=entity;observedEntity=new java.lang.ref.WeakReference<>(entity);
        var raw=(Access.EntityState)entity;
        capturedCallback=raw.pro$callback();capturedCanUpdate=raw.pro$canUpdate();capturedNoPhysics=raw.pro$noPhysics();
        capturedBox=raw.pro$box();capturedPosition=raw.pro$position();
        id=raw.pro$id(); uuid=raw.pro$uuid();
        originalVehicle=raw.pro$vehicle(); originalPassengers=List.copyOf(raw.pro$passengers());
        vehicleIdentity=new java.lang.ref.WeakReference<>(originalVehicle);
        passengerIdentities=originalPassengers.stream().map(java.lang.ref.WeakReference::new).toList();
        loader=entity.getClass().getClassLoader();
        definition=entity.getClass();
        server=(Access.Server)level;
        manager=(Access.Manager)server.pro$manager();
        lookup=(Access.Lookup)manager.pro$lookup();
        ticks=(Access.Ticks)server.pro$ticks();
        sections=(Access.Sections)manager.pro$sections();
        tracking=(Access.Tracking)level.getChunkSource().chunkMap;
        section=SectionPos.asLong(raw.pro$blockPosition());
        var currentSection=sections.pro$all().get(section);
        originallyTicking=ticks.pro$active().values().stream().anyMatch(e->e==entity)
                || currentSection!=null && currentSection.getStatus().isTicking();
        idOwner=lookup.pro$ids(); uuidOwner=lookup.pro$uuids(); knownOwner=manager.pro$known();
        sectionOwner=sections.pro$all(); trackingOwner=tracking.pro$tracked();
        visibilityOwner=manager.pro$visibility();sectionKeysOwner=sections.pro$keys();
        tickActiveOwner=ticks.pro$active();tickPassiveOwner=ticks.pro$passive();capturedSection=new java.lang.ref.WeakReference<>(currentSection);
        levelCallbacks=manager.pro$callbacks();visibilityProvider=sections.pro$visibility();getterOwner=manager.pro$getter();
        ownedUuids.add(uuid);
        captureAliases();
    }
    public boolean bound() {
        Object body=observedEntity.get();if(body!=null&&body.getClass()!=definition)return false;
        Entity entity=(Entity)body;
        var raw=(Access.EntityState)entity;
        return ownersCurrent()&&(entity==null?referencesReleased:
                entity.getClass()==definition&&raw.pro$level()==level&&raw.pro$id()==id&&Objects.equals(raw.pro$uuid(),uuid));
    }
    private boolean ownersCurrent() {
        return level.getServer().isSameThread()&&level.getServer().getLevel(level.dimension())==level
            && server.pro$manager()==manager && server.pro$ticks()==ticks
            && manager.pro$lookup()==lookup && manager.pro$sections()==sections
            && level.getChunkSource().chunkMap==tracking
            && lookup.pro$ids()==idOwner && lookup.pro$uuids()==uuidOwner && manager.pro$known()==knownOwner
            && sections.pro$all()==sectionOwner && sections.pro$keys()==sectionKeysOwner
            && manager.pro$visibility()==visibilityOwner && tracking.pro$tracked()==trackingOwner;
    }
    public boolean conflict() {
        Entity entity=observedEntity.get();
        return (lookup.pro$ids().get(id)!=null && lookup.pro$ids().get(id)!=entity)
            || (lookup.pro$uuids().get(uuid)!=null && lookup.pro$uuids().get(uuid)!=entity);
    }
    public boolean registered() {
        Entity entity=observedEntity.get();
        return entity!=null&&lookup.pro$ids().values().stream().anyMatch(value->value==entity)
                &&lookup.pro$uuids().values().stream().anyMatch(value->value==entity)
                &&ownedUuids.stream().anyMatch(manager.pro$known()::contains)&&inSections();
    }
    public Set<UUID> participants() {
        Entity entity=observedEntity.get();
        Set<UUID> ids=new HashSet<>();
        for(Object value:tracking.pro$tracked().values()) {
            Access.Tracked row=(Access.Tracked)value;
            if(row.pro$entity()==entity)
                for(var connection:row.pro$seen()) ids.add(connection.getPlayer().getUUID());
        }
        return ids;
    }
    private boolean inSections() {
        Entity entity=observedEntity.get();if(entity==null)return false;
        for(var row:sections.pro$all().values()) {
            Access.Group g=(Access.Group)((Access.Section)row).pro$storage();
            if(g.pro$all().stream().anyMatch(e->e==entity)) return true;
            for(var list:g.pro$classes().values()) if(list.stream().anyMatch(e->e==entity)) return true;
        }
        return false;
    }
    public Set<String> remaining() {
        Set<String> result=new LinkedHashSet<>();
        if(!bound()) { result.add("RECORD_OWNER_UNOBSERVED"); return result; }
        Entity entity=observedEntity.get();var raw=(Access.EntityState)entity;
        if(entity==null) {
            // Own references were explicitly cleared after a real durable body fact. GC is not that fact.
            // Independently query every old UUID slot, including raw physical section members.
            if(!referencesReleased)result.add("BODY_RELEASE_NOT_OBSERVED");
            for(UUID owned:ownedUuids)if(lookup.pro$uuids().containsKey(owned)||manager.pro$known().contains(owned)||usedByOther(owned))
                result.add("ORIGINAL_LOCATION_PRESENT_REQUIRES_NEW_BINDING");
            return result;
        }
        Entity vehicle=referencesReleased?vehicleIdentity.get():originalVehicle;
        List<Entity> passengers=referencesReleased?passengerIdentities.stream().map(java.lang.ref.WeakReference::get).filter(Objects::nonNull).toList():originalPassengers;
        if(raw.pro$removal()==null) result.add("BODY");
        if(raw.pro$vehicle()!=null || !raw.pro$passengers().isEmpty()) result.add("RELATIONS");
        if(vehicle!=null && ((Access.EntityState)vehicle).pro$passengers().stream().anyMatch(e->e==entity)) result.add("VEHICLE_EDGE");
        for(Entity passenger:passengers)
            if(((Access.EntityState)passenger).pro$vehicle()==entity) result.add("PASSENGER_EDGE");
        if(lookup.pro$ids().values().stream().anyMatch(e->e==entity)) result.add("LOOKUP_ID");
        if(lookup.pro$uuids().values().stream().anyMatch(e->e==entity)) result.add("LOOKUP_UUID");
        if(inSections()) result.add("SECTION");
        if(ticks.pro$active().values().stream().anyMatch(e->e==entity)
                || ticks.pro$passive().values().stream().anyMatch(e->e==entity)) result.add("TICK");
        if(((dev.ronova.pro.mixin.BlockGroupHooks.NavigationAccess)level).pro$navigating().stream().anyMatch(value->value==entity))result.add("NAVIGATION");
        if(server.pro$parts().values().stream().anyMatch(value->value==entity
                ||value!=null&&((dev.ronova.pro.mixin.BlockGroupHooks.PartAccess)value).pro$parent()==entity))result.add("MULTIPART");
        var iterated=ticks.pro$iterated();
        if(iterated!=null&&iterated.values().stream().anyMatch(e->e==entity))result.add("TICK_ITERATION_PENDING");
        for(Object v:tracking.pro$tracked().values())
            if(((Access.Tracked)v).pro$entity()==entity) result.add("TRACKING");
        // A UUID slot belonging to another legitimate incarnation is not ours to remove.
        for(UUID owned:ownedUuids)
            if(manager.pro$known().contains(owned) && lookup.pro$uuids().get(owned)==null && !usedByOther(owned))
                result.add("KNOWN_UUID");
        return result;
    }
    /**
     * Exact-body disposal. Each step is independent: a hostile lifecycle override or one unreachable structure
     * cannot stop the field, index and relation work that does not depend on it. A step whose execution is
     * unknown is recorded separately and never replayed blindly.
     */
    public void clear() {
        clear(()->true);
    }
    public void clear(BooleanSupplier current) {
        if(!current(current))return;
        Entity entity=observedEntity.get();
        if(entity==null) return;
        var raw=(Access.EntityState)entity;
        captureAliases();
        // A hostile setRemoved may throw after it has already written, so the flag is read back afterwards.
        try { if(raw.pro$removal()==null)entity.setRemoved(Entity.RemovalReason.DISCARDED); }
        catch(RuntimeException|LinkageError overriding) { lifecycleUnresolved=true; }
        if(!current(current))return;
        step(()->{ if(raw.pro$removal()==null)raw.pro$removal(Entity.RemovalReason.DISCARDED); },current);
        step(()->raw.pro$canUpdate(false),current);
        step(()->raw.pro$added(false),current);
        if(!current(current))return;
        clearRelations(entity,raw,current);
        // The index work runs regardless of what the lifecycle entry did or failed to do.
        if(current(current))clearRecords(current);
    }
    /** True when the lifecycle entry failed and its real effect could not be confirmed. */
    public boolean lifecycleUnresolved() { return lifecycleUnresolved; }
    public boolean currentCheckUnresolved() { return currentCheckUnresolved; }
    private boolean current(BooleanSupplier current) { try{return current.getAsBoolean();}catch(RuntimeException|LinkageError unavailable){currentCheckUnresolved=true;return false;} }
    private void step(Runnable action,BooleanSupplier current) {
        if(!current(current))return;
        try { action.run(); }
        catch(RuntimeException|LinkageError unavailable) { structuralStepsUnresolved++; }
    }
    /** Idempotent record removal. A slot held by another legitimate incarnation is left alone. */
    void clearRecords() { clearRecords(()->true); }
    void clearRecords(BooleanSupplier current) {
        if(!current(current))return;
        Entity entity=observedEntity.get();
        if(entity==null) return;
        captureAliases();
        var tracked=tracking.pro$tracked();
        // Capture the row identity. A callback may replace the same key; never delete its replacement.
        if(standardTracking()) for(int key:tracked.keySet().toIntArray()) {
            Object row=tracked.get(key);
            if(!(row instanceof Access.Tracked trackedRow))continue;
            boolean exact;
            try { exact=trackedRow.pro$entity()==entity; }
            catch(RuntimeException|LinkageError inaccessible) { structuralStepsUnresolved++;continue; }
            if(!exact)continue;
            if(current(current))try { trackedRow.pro$removed(); }
            catch(RuntimeException|LinkageError opaqueCallback) { lifecycleUnresolved=true;structuralStepsUnresolved++; }
            if(!current(current))return;
            step(()->{if(tracked.get(key)==row)tracked.remove(key);},current);
            if(!current(current))return;
        }
        if(!current(current))return;
        if(standardTracking())step(()->removeKeysWhere(tracked,entity),current);
        if(lookup.pro$ids().getClass()==it.unimi.dsi.fastutil.ints.Int2ObjectLinkedOpenHashMap.class)
            step(()->removeKeysWhere(lookup.pro$ids(),entity),current);
        if(lookup.pro$uuids().getClass()==HashMap.class)step(()->removeKeysWhere(lookup.pro$uuids(),entity),current);
        if(ticks.pro$active().getClass()==it.unimi.dsi.fastutil.ints.Int2ObjectLinkedOpenHashMap.class)
            step(()->{ticks.pro$ensureActiveIsNotIterated();var iterated=ticks.pro$iterated();var active=ticks.pro$active();if(active!=iterated)removeKeysWhere(active,entity);},current);
        if(ticks.pro$passive().getClass()==it.unimi.dsi.fastutil.ints.Int2ObjectLinkedOpenHashMap.class)
            step(()->{var iterated=ticks.pro$iterated();var passive=ticks.pro$passive();if(passive!=iterated)removeKeysWhere(passive,entity);},current);
        step(()->((dev.ronova.pro.mixin.BlockGroupHooks.NavigationAccess)level).pro$navigating().removeIf(value->value==entity),current);
        var parts=server.pro$parts();
        for(int key:parts.keySet().toIntArray()){
            var part=parts.get(key);if(part==null||part!=entity&&((dev.ronova.pro.mixin.BlockGroupHooks.PartAccess)part).pro$parent()!=entity)continue;
            step(()->{
                var state=(Access.EntityState)part;
                state.pro$removal(Entity.RemovalReason.DISCARDED);state.pro$canUpdate(false);state.pro$added(false);state.pro$callback(EntityInLevelCallback.NULL);
                if(state.pro$removal()==null||state.pro$canUpdate())throw new IllegalStateException("MULTIPART_BODY_WRITE_REFUSED");
                if(parts.get(key)==part)parts.remove(key);
            },current);
            if(!current(current))return;
        }
        if(sections.pro$all().getClass()==it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap.class)
        for(var entry:new ArrayList<>(sections.pro$all().entrySet())) {
            Object row=entry.getValue();
            step(()->{
                if(sections.pro$all().get(entry.getKey())!=row)return;
                Access.Group group=(Access.Group)((Access.Section)row).pro$storage();
                if(group.pro$all().getClass()==ArrayList.class)step(()->removeValuesWhere(group.pro$all(),entity),current);
                if(group.pro$classes().getClass()==HashMap.class)
                    for(List<Entity> list:new ArrayList<>(group.pro$classes().values()))
                        if(list.getClass()==ArrayList.class){if(!current(current))return;step(()->removeValuesWhere(list,entity),current);}
            },current);
            if(!current(current))return;
        }
        if(manager.pro$known().getClass()==HashSet.class)
            for(UUID owned:ownedUuids)step(()->{
                if(lookup.pro$uuids().get(owned)==null && !usedByOther(owned))manager.pro$known().remove(owned);
            },current);
    }
    /** Slots that stayed behind after a step failed; the caller records this instead of claiming success. */
    public boolean slotsRemaining(Entity entity) {
        return lookup.pro$ids().values().stream().anyMatch(value->value==entity)
                || lookup.pro$uuids().values().stream().anyMatch(value->value==entity)
                 || remaining().stream().anyMatch(gap->Set.of("LOOKUP_ID","LOOKUP_UUID","SECTION","TICK","TICK_ITERATION_PENDING","TRACKING","KNOWN_UUID","NAVIGATION","MULTIPART").contains(gap));
    }
    private static void removeKeysWhere(Map<?,?> map,Object target) {
        if(map==null||map.isEmpty())return;
        for(Object key:List.copyOf(map.keySet())) { Object value=map.get(key);if(value==target)map.remove(key,value); }
    }
    private static void removeKeysWhere(it.unimi.dsi.fastutil.ints.Int2ObjectMap<?> map,Object target) {
        if(map==null||map.isEmpty())return;
        for(int key:map.keySet().toIntArray())if(map.get(key)==target)map.remove(key,target);
    }
    private static void removeValuesWhere(List<Entity> list,Entity target) {
        for(int i=list.size()-1;i>=0;i--)if(list.get(i)==target)list.remove(i);
    }
    /** Detach only edges whose opposite endpoint still names this exact entity; preserve every neighbor. */
    private void clearRelations(Entity entity,Access.EntityState raw,BooleanSupplier current) {
        Set<Entity> vehicles=Collections.newSetFromMap(new IdentityHashMap<>());
        if(originalVehicle!=null)vehicles.add(originalVehicle);
        Entity weakVehicle=vehicleIdentity.get();if(weakVehicle!=null)vehicles.add(weakVehicle);
        Entity currentVehicle=raw.pro$vehicle();if(currentVehicle!=null)vehicles.add(currentVehicle);
        for(Entity vehicle:vehicles) {
            Access.EntityState vr=(Access.EntityState)vehicle;
            step(()->{
                if(raw.pro$vehicle()==vehicle)raw.pro$vehicle(null);
                if(vr.pro$passengers().stream().anyMatch(e->e==entity))
                    vr.pro$passengers(com.google.common.collect.ImmutableList.copyOf(vr.pro$passengers().stream().filter(e->e!=entity).toList()));
            },current);
            if(!current(current))return;
        }
        Set<Entity> passengers=Collections.newSetFromMap(new IdentityHashMap<>());
        passengers.addAll(originalPassengers);
        for(var ref:passengerIdentities){Entity passenger=ref.get();if(passenger!=null)passengers.add(passenger);}
        passengers.addAll(raw.pro$passengers());
        for(Entity passenger:passengers) {
            Access.EntityState pr=(Access.EntityState)passenger;
            step(()->{
                if(pr.pro$vehicle()==entity)pr.pro$vehicle(null);
                if(raw.pro$passengers().stream().anyMatch(e->e==passenger))
                    raw.pro$passengers(com.google.common.collect.ImmutableList.copyOf(raw.pro$passengers().stream().filter(e->e!=passenger).toList()));
            },current);
            if(!current(current))return;
        }
    }
    Set<UUID> indexUuids() { return Set.copyOf(ownedUuids); }
    private void captureAliases() {
        Entity entity=observedEntity.get();if(entity==null)return;
        for(var entry:lookup.pro$uuids().entrySet())if(entry.getValue()==entity)ownedUuids.add(entry.getKey());
        synchronized(REGISTRATIONS) {
            Registration registered=REGISTRATIONS.get(entity);
            if(registered!=null&&registered.manager.get()==manager)ownedUuids.addAll(registered.aliases);
        }
    }
    private boolean usedByOther(UUID value) {
        Entity entity=observedEntity.get();
        for(var row:sections.pro$all().values()) {
            var g=(Access.Group)((Access.Section)row).pro$storage();
            for(Entity other:g.pro$all())
                if(other!=entity && ((Access.EntityState)other).pro$uuid().equals(value))return true;
        }
        return false;
    }
    private boolean knownContainers() {
        if(lookup.pro$uuids().getClass()!=HashMap.class || manager.pro$known().getClass()!=HashSet.class
                || lookup.pro$ids().getClass()!=it.unimi.dsi.fastutil.ints.Int2ObjectLinkedOpenHashMap.class
                || ticks.pro$active().getClass()!=it.unimi.dsi.fastutil.ints.Int2ObjectLinkedOpenHashMap.class
                || ticks.pro$passive().getClass()!=it.unimi.dsi.fastutil.ints.Int2ObjectLinkedOpenHashMap.class
                || !standardTracking()
                || sections.pro$all().getClass()!=it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap.class) return false;
        for(var row:sections.pro$all().values()) {
            var g=(Access.Group)((Access.Section)row).pro$storage();
            if(g.pro$all().getClass()!=ArrayList.class || g.pro$classes().getClass()!=HashMap.class)return false;
            for(var list:g.pro$classes().values())if(list.getClass()!=ArrayList.class)return false;
        }
        return true;
    }
    public boolean softRepairSupported() {
        // No virtual container callbacks are allowed to acquire the SOFT classification.
        return platformCarriers(level)&&ownersCurrent()&&platformSection(sections.pro$all().get(section));
    }
    private boolean standardTracking() {
        Class<?> type=tracking.pro$tracked().getClass();
        return type==it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap.class
                ||type==it.unimi.dsi.fastutil.ints.Int2ObjectLinkedOpenHashMap.class;
    }
    private static boolean exact(Object value,Class<?> type) {return value!=null&&value.getClass()==type;}
    /** Validate the fixed platform holders before invoking any replacement collection. */
    private static boolean platformCarriers(ServerLevel level) {
        Object manager=((Access.Server)level).pro$manager(),ticks=((Access.Server)level).pro$ticks();
        Object tracking=level.getChunkSource().chunkMap;
        if(!exact(manager,PersistentEntitySectionManager.class)||!exact(ticks,EntityTickList.class)||!exact(tracking,ChunkMap.class))return false;
        Access.Manager m=(Access.Manager)manager;Access.Ticks t=(Access.Ticks)ticks;
        if(!exact(m.pro$lookup(),EntityLookup.class)||!exact(m.pro$sections(),EntitySectionStorage.class)
                ||!exact(m.pro$known(),HashSet.class)||!exact(m.pro$visibility(),it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap.class))return false;
        Access.Lookup l=(Access.Lookup)m.pro$lookup();Access.Sections s=(Access.Sections)m.pro$sections();
        return exact(l.pro$ids(),it.unimi.dsi.fastutil.ints.Int2ObjectLinkedOpenHashMap.class)&&exact(l.pro$uuids(),HashMap.class)
            &&exact(t.pro$active(),it.unimi.dsi.fastutil.ints.Int2ObjectLinkedOpenHashMap.class)
            &&exact(t.pro$passive(),it.unimi.dsi.fastutil.ints.Int2ObjectLinkedOpenHashMap.class)
            &&(t.pro$iterated()==null||exact(t.pro$iterated(),it.unimi.dsi.fastutil.ints.Int2ObjectLinkedOpenHashMap.class))
            &&exact(s.pro$all(),it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap.class)
            &&exact(s.pro$keys(),it.unimi.dsi.fastutil.longs.LongAVLTreeSet.class)
            &&(exact(((Access.Tracking)tracking).pro$tracked(),it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap.class)
                ||exact(((Access.Tracking)tracking).pro$tracked(),it.unimi.dsi.fastutil.ints.Int2ObjectLinkedOpenHashMap.class));
    }
    private static boolean platformSection(EntitySection<Entity> row) {
        if(!exact(row,EntitySection.class)||!exact(((Access.Section)row).pro$storage(),net.minecraft.util.ClassInstanceMultiMap.class))return false;
        Access.Group group=(Access.Group)((Access.Section)row).pro$storage();
        if(group.pro$baseClass()!=Entity.class||!exact(group.pro$all(),ArrayList.class)||!exact(group.pro$classes(),HashMap.class)
                ||group.pro$classes().get(Entity.class)!=group.pro$all())return false;
        for(List<Entity> list:group.pro$classes().values())if(!exact(list,ArrayList.class))return false;
        return true;
    }
    private boolean registeredHere(Entity entity,EntitySection<Entity> row) {
        if(!platformSection(row)||sections.pro$all().get(section)!=row||lookup.pro$ids().get(id)!=entity
                ||lookup.pro$uuids().get(uuid)!=entity||!manager.pro$known().contains(uuid)||!sections.pro$keys().contains(section))return false;
        Access.Group group=(Access.Group)((Access.Section)row).pro$storage();
        if(group.pro$all().stream().noneMatch(value->value==entity))return false;
        for(var entry:group.pro$classes().entrySet())if(entry.getKey().isInstance(entity)&&entry.getValue().stream().noneMatch(value->value==entity))return false;
        return true;
    }
    private boolean repairCurrent(BooleanSupplier authority) {return current(authority)&&bound();}
    private boolean writeCurrent(Object holder,BooleanSupplier authority,Runnable write) {
        return ProRuntime.withEntityRecordGate(holder,()->{
            if(!repairCurrent(authority))return false;
            write.run();return repairCurrent(authority);
        });
    }
    private EntityRecords captureCurrent(Entity entity,BooleanSupplier authority) {
        return ProRuntime.withEntityRecordGate(level,()->{
            if(!current(authority)||entity.getClass()!=definition||!platformCarriers(level))return null;
            Access.Manager m=(Access.Manager)server.pro$manager();Access.Ticks t=(Access.Ticks)server.pro$ticks();
            Access.Lookup l=(Access.Lookup)m.pro$lookup();Access.Sections s=(Access.Sections)m.pro$sections();
            Access.Tracking tracking=(Access.Tracking)level.getChunkSource().chunkMap;
            return ProRuntime.withEntityRecordGate(m,()->ProRuntime.withEntityRecordGate(t,()->
                ProRuntime.withEntityRecordGate(l,()->ProRuntime.withEntityRecordGate(s,()->ProRuntime.withEntityRecordGate(tracking,()->{
                    if(!current(authority)||entity.getClass()!=definition||!platformCarriers(level)||server.pro$manager()!=m||server.pro$ticks()!=t
                            ||m.pro$lookup()!=l||m.pro$sections()!=s||level.getChunkSource().chunkMap!=tracking)return null;
                    EntitySection<Entity> row=s.pro$all().get(SectionPos.asLong(((Access.EntityState)entity).pro$blockPosition()));
                    if(row!=null&&!platformSection(row))return null;
                    EntityRecords captured=new EntityRecords(level,entity);
                    return current(authority)&&captured.definition==definition&&captured.level==level&&captured.id==id
                            &&Objects.equals(captured.uuid,uuid)&&captured.bound()?captured:null;
                })))));
        });
    }
    private static boolean getterCurrent(Access.Manager manager,Access.Lookup lookup,Access.Sections sections) {
        Object getter=manager.pro$getter();return exact(getter,LevelEntityGetterAdapter.class)
                &&((Access.Getter)getter).pro$lookup()==lookup&&((Access.Getter)getter).pro$sections()==sections;
    }
    /** Known Minecraft callback construction; no foreign callback or cached reflection image is used. */
    private static final class CallbackLayout {
        static final Class<?> TYPE,WORLD_TYPE;
        static final java.lang.reflect.Constructor<?> CREATE;
        static final java.lang.reflect.Field MANAGER,WORLD;
        static {
            try {
                TYPE=Class.forName("net.minecraft.world.level.entity.PersistentEntitySectionManager$Callback",false,PersistentEntitySectionManager.class.getClassLoader());
                WORLD_TYPE=Class.forName("net.minecraft.server.level.ServerLevel$EntityCallbacks",false,ServerLevel.class.getClassLoader());
                openCallbackAccess(TYPE,WORLD_TYPE);
                CREATE=PlatformAccess.declaredConstructor(TYPE,PersistentEntitySectionManager.class,EntityAccess.class,long.class,EntitySection.class);
                MANAGER=ownerField(TYPE,PersistentEntitySectionManager.class);WORLD=ownerField(WORLD_TYPE,ServerLevel.class);
                if(!CREATE.trySetAccessible())throw new IllegalStateException("PLATFORM_ENTITY_CALLBACK_CONSTRUCTOR_UNAVAILABLE");
            } catch(ReflectiveOperationException failure) {throw new ExceptionInInitializerError(failure);}
        }
        private static java.lang.reflect.Field ownerField(Class<?> type,Class<?> owner) {
            java.lang.reflect.Field found=null;
            for(var field:PlatformAccess.declaredFields(type))if(!java.lang.reflect.Modifier.isStatic(field.getModifiers())&&field.getType()==owner) {
                if(found!=null)throw new IllegalStateException("PLATFORM_ENTITY_CALLBACK_OWNER_AMBIGUOUS");found=field;
            }
            if(found==null||!found.trySetAccessible())throw new IllegalStateException("PLATFORM_ENTITY_CALLBACK_OWNER_UNAVAILABLE");return found;
        }
        static boolean world(LevelCallback<Entity> callback,ServerLevel level)throws IllegalAccessException {
            return exact(callback,WORLD_TYPE)&&WORLD.get(callback)==level;
        }
        static boolean owned(EntityInLevelCallback value,Object manager,Entity entity)throws IllegalAccessException {
            return exact(value,TYPE)&&value instanceof Access.Callback callback&&callback.pro$entity()==entity&&MANAGER.get(value)==manager;
        }
    }
    private static void openCallbackAccess(Class<?>... types)throws ReflectiveOperationException {
        Class.forName("dev.ronova.pro.agent.RecoveryAgent",false,ClassLoader.getSystemClassLoader())
            .getMethod("openBackingAccess",Class.class,Class[].class).invoke(null,EntityRecords.class,(Object)types);
    }
    /** Rebind the exact body to the current platform holders in its original real world. */
    EntityRecords repairProtectedCarrier(BooleanSupplier authority) {
        Object body=observedEntity.get();
        if(body==null||body.getClass()!=definition||!current(authority))return null;
        Entity entity=(Entity)body;Access.EntityState raw=(Access.EntityState)entity;
        if(raw.pro$level()!=level||raw.pro$id()!=id||!Objects.equals(raw.pro$uuid(),uuid)
                ||level.getServer().getLevel(level.dimension())!=level||!platformCarriers(level))return null;
        EntityRecords live=captureCurrent(entity,authority);
        if(live==null||!live.repairCurrent(authority)||live.conflict())return null;
        // Derive a missing/replaced section's state from the original world's real loaded chunk.
        var chunk=level.getChunkSource().getChunkNow(SectionPos.x(live.section),SectionPos.z(live.section));
        if(!exact(chunk,net.minecraft.world.level.chunk.LevelChunk.class))return null;
        FullChunkStatus chunkStatus=chunk.getFullStatus();Visibility visibility=Visibility.fromFullChunkStatus(chunkStatus);
        if(!visibility.isAccessible())return null;
        BooleanSupplier current=()->current(authority)&&level.getChunkSource().getChunkNow(SectionPos.x(live.section),SectionPos.z(live.section))==chunk
                &&chunk.getFullStatus()==chunkStatus&&SectionPos.asLong(raw.pro$blockPosition())==live.section;
        Object tracked=live.tracking.pro$tracked().get(id);
        if(tracked!=null&&(!(tracked instanceof Access.Tracked row)||row.pro$entity()!=entity))return null;
        if(live.ticks.pro$active().get(id)!=null&&live.ticks.pro$active().get(id)!=entity)return null;
        try {
            if(!CallbackLayout.world(levelCallbacks,level))return null;
            if(!live.writeCurrent(live.manager,current,()->{
                if(live.manager.pro$callbacks()!=levelCallbacks)live.manager.pro$callbacks(levelCallbacks);
                if(!getterCurrent(live.manager,live.lookup,live.sections))
                    live.manager.pro$getter(new LevelEntityGetterAdapter<>((EntityLookup<Entity>)live.lookup,(EntitySectionStorage<Entity>)live.sections));
            }))return null;
            if(manager!=live.manager||sections!=live.sections||visibilityOwner!=live.visibilityOwner||visibilityProvider!=live.sections.pro$visibility()) {
                Access.Manager liveManager=live.manager;
                if(!live.writeCurrent(live.sections,current,()->live.sections.pro$visibility(key->liveManager.pro$visibility().get(key))))return null;
            }
            var visibilityMap=live.manager.pro$visibility();
            long chunkKey=net.minecraft.world.level.ChunkPos.asLong(SectionPos.x(live.section),SectionPos.z(live.section));
            if(!live.writeCurrent(visibilityMap,current,()->visibilityMap.put(chunkKey,visibility)))return null;
            var sectionMap=live.sections.pro$all();
            if(!live.writeCurrent(sectionMap,current,()->{
                if(sectionMap.get(live.section)==null)sectionMap.putIfAbsent(live.section,new EntitySection<>(Entity.class,visibility));
            }))return null;
            EntitySection<Entity> row=sectionMap.get(live.section);
            if(!platformSection(row))return null;
            Object visibilityProvider=live.sections.pro$visibility();
            BooleanSupplier located=()->current.getAsBoolean()&&sectionMap.get(live.section)==row&&live.manager.pro$callbacks()==levelCallbacks
                    &&getterCurrent(live.manager,live.lookup,live.sections)&&live.sections.pro$visibility()==visibilityProvider;
            if(!live.writeCurrent(row,located,()->row.updateChunkStatus(visibility)))return null;
            var sectionKeys=live.sections.pro$keys();
            if(!live.writeCurrent(sectionKeys,located,()->sectionKeys.add(live.section)))return null;
            EntityInLevelCallback observed=raw.pro$callback(),restored=observed;
            if(!CallbackLayout.owned(restored,live.manager,entity))
                restored=CallbackLayout.owned(capturedCallback,live.manager,entity)?capturedCallback:
                    (EntityInLevelCallback)CallbackLayout.CREATE.newInstance(live.manager,entity,live.section,row);
            EntityInLevelCallback callback=restored;Access.Callback callbackState=(Access.Callback)callback;
            if(!live.writeCurrent(callback,located,()->{callbackState.pro$sectionKey(live.section);callbackState.pro$section(row);}))return null;
            if(!live.writeCurrent(entity,located,()->{
                if(raw.pro$callback()!=observed||sectionMap.get(live.section)!=row)return;
                raw.pro$removal(null);raw.pro$canUpdate(capturedCanUpdate);raw.pro$noPhysics(capturedNoPhysics);raw.pro$added(true);raw.pro$callback(callback);
                if(entity instanceof net.minecraft.world.entity.LivingEntity) {
                    ((Access.Living)entity).pro$dead(false);((Access.Living)entity).pro$deathTime(0);
                }
                var box=raw.pro$box();
                if(!Double.isFinite(box.getSize())||box.getXsize()<=0||box.getYsize()<=0||box.getZsize()<=0)
                    raw.pro$box(capturedBox.move(raw.pro$position().subtract(capturedPosition)));
            })||raw.pro$callback()!=callback||raw.pro$removal()!=null)return null;
            BooleanSupplier restoredBody=()->located.getAsBoolean()&&raw.pro$callback()==callback
                    &&callbackState.pro$entity()==entity&&callbackState.pro$sectionKey()==live.section&&callbackState.pro$section()==row;
            if(!live.repairCurrent(restoredBody))return null;
            BackingPolicy.captureRepairCarriers(live,entity,row,callback);
            if(!live.repair(restoredBody,originallyTicking||live.originallyTicking))return null;
            if(entity instanceof ServerPlayer player&&!live.repairPlayer(player,restoredBody))return null;
            if(tracked==null) {
                Object trackingMap=live.tracking.pro$tracked();
                if(!live.writeCurrent(trackingMap,restoredBody,()->{
                    if(live.tracking.pro$tracked()==trackingMap&&live.tracking.pro$tracked().get(id)==null)live.tracking.pro$add(entity);
                }))return null;
            }
            if(!live.repairCurrent(restoredBody)||!live.registeredHere(entity,row))return null;
            Object restoredTracking=live.tracking.pro$tracked().get(id);
            if(!(restoredTracking instanceof Access.Tracked trackingRow)||trackingRow.pro$entity()!=entity)return null;
            // Subsequent protection captures the repaired callback, flags and current carrier identities.
            EntityRecords result=live.captureCurrent(entity,restoredBody);
            return result!=null&&result.repairCurrent(restoredBody)&&result.registeredHere(entity,row)?result:null;
        } catch(ReflectiveOperationException unavailable) {return null;}
    }
    EntityRecords repairProtectedCarrier() {return repairProtectedCarrier(()->true);}
    private boolean repairPlayer(ServerPlayer player,BooleanSupplier authority) {
        var players=(Access.Players)level.getServer().getPlayerList();
        List<ServerPlayer> local=server.pro$players(),all=players.pro$players();Map<UUID,ServerPlayer> byUuid=players.pro$byUuid();
        if(!exact(local,ArrayList.class)||!exact(all,ArrayList.class)||!exact(byUuid,HashMap.class))return false;
        ServerPlayer indexed=byUuid.get(uuid);if(indexed!=null&&indexed!=player)return false;
        if(!writeCurrent(local,authority,()->{if(server.pro$players()==local&&local.stream().noneMatch(value->value==player))local.add(player);}))return false;
        if(!writeCurrent(all,authority,()->{if(players.pro$players()==all&&all.stream().noneMatch(value->value==player))all.add(player);}))return false;
        if(!writeCurrent(byUuid,authority,()->{if(players.pro$byUuid()==byUuid)byUuid.putIfAbsent(uuid,player);}))return false;
        return server.pro$players()==local&&players.pro$players()==all&&players.pro$byUuid()==byUuid
                &&byUuid.get(uuid)==player&&local.stream().anyMatch(value->value==player)&&all.stream().anyMatch(value->value==player);
    }
    public boolean repair() {return repair(()->true,originallyTicking);}
    private boolean repair(BooleanSupplier authority,boolean keepTicking) {
        Object body=observedEntity.get();if(body==null||body.getClass()!=definition)return false;
        Entity entity=(Entity)body;Access.EntityState raw=(Access.EntityState)entity;
        if(!repairCurrent(authority)||!softRepairSupported()||conflict()||raw.pro$removal()!=null||SectionPos.asLong(raw.pro$blockPosition())!=section)return false;
        EntitySection<Entity> row=sections.pro$all().get(section);if(!platformSection(row))return false;
        Access.Group group=(Access.Group)((Access.Section)row).pro$storage();
        var ids=lookup.pro$ids();var uuids=lookup.pro$uuids();var known=manager.pro$known();var keys=sections.pro$keys();
        if(!writeCurrent(ids,authority,()->ids.putIfAbsent(id,entity))||ids.get(id)!=entity)return false;
        if(!writeCurrent(uuids,authority,()->uuids.putIfAbsent(uuid,entity))||uuids.get(uuid)!=entity)return false;
        if(!writeCurrent(known,authority,()->known.add(uuid)))return false;
        if(!writeCurrent(keys,authority,()->{if(sections.pro$all().get(section)==row)keys.add(section);}))return false;
        List<Entity> all=group.pro$all();Map<Class<?>,List<Entity>> classes=group.pro$classes();
        BooleanSupplier membership=()->current(authority)&&sections.pro$all().get(section)==row
                &&((Access.Section)row).pro$storage()==group&&group.pro$all()==all&&group.pro$classes()==classes;
        if(!writeCurrent(all,membership,()->{if(all.stream().noneMatch(value->value==entity))all.add(entity);}))return false;
        for(var entry:new ArrayList<>(classes.entrySet()))if(entry.getKey().isInstance(entity)) {
            List<Entity> list=entry.getValue();
            if(!exact(list,ArrayList.class)||!writeCurrent(list,membership,()->{if(classes.get(entry.getKey())==list&&list.stream().noneMatch(value->value==entity))list.add(entity);})
                    ||classes.get(entry.getKey())!=list)return false;
        }
        if(keepTicking&&(row.getStatus().isTicking()||entity instanceof ServerPlayer)) {
            if(!repairCurrent(membership))return false;
            ticks.pro$ensureActiveIsNotIterated();
            var active=ticks.pro$active();
            if(!exact(active,it.unimi.dsi.fastutil.ints.Int2ObjectLinkedOpenHashMap.class)||active==ticks.pro$iterated())return false;
            if(!writeCurrent(active,membership,()->{if(ticks.pro$active()==active)active.putIfAbsent(id,entity);})
                    ||ticks.pro$active()!=active||active.get(id)!=entity)return false;
        }
        return repairCurrent(membership)&&!conflict()&&registeredHere(entity,row);
    }
    /** Snapshot roots retain only this body's ownership when platform carriers are replaced. */
    Object[] carrierRoots() {
        return new Object[]{level,manager,lookup,ticks,sections,tracking,idOwner,uuidOwner,knownOwner,sectionOwner,trackingOwner,
            visibilityOwner,sectionKeysOwner,tickActiveOwner,tickPassiveOwner,capturedSection.get(),capturedCallback,getterOwner,visibilityProvider,levelCallbacks};
    }
    boolean carrierIdentityChanged(EntityRecords next) {
        Object[] previous=carrierRoots(),current=next.carrierRoots();
        for(int index=0;index<previous.length;index++)if(previous[index]!=current[index])return true;
        return false;
    }
    void releaseOwnedReferences() {
        ownedEntity=null;originalVehicle=null;originalPassengers=List.of();capturedCallback=null;referencesReleased=true;
    }
    boolean ownedReferencesReleased() { return referencesReleased&&ownedEntity==null&&originalVehicle==null&&originalPassengers.isEmpty()&&capturedCallback==null; }
}
