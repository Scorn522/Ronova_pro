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
        ownedUuids.add(uuid);
        captureAliases();
    }
    public boolean bound() {
        Entity entity=observedEntity.get();var raw=(Access.EntityState)entity;
        return ownersCurrent()&&(entity==null?referencesReleased:
                raw.pro$level()==level&&raw.pro$id()==id&&raw.pro$uuid().equals(uuid)&&entity.getClass().getClassLoader()==loader);
    }
    private boolean ownersCurrent() {
        return level.getServer().isSameThread()&&level.getServer().getLevel(level.dimension())==level
            && server.pro$manager()==manager && server.pro$ticks()==ticks
            && manager.pro$lookup()==lookup && manager.pro$sections()==sections
            && level.getChunkSource().chunkMap==tracking
            && lookup.pro$ids()==idOwner && lookup.pro$uuids()==uuidOwner && manager.pro$known()==knownOwner
            && sections.pro$all()==sectionOwner && tracking.pro$tracked()==trackingOwner;
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
                || remaining().stream().anyMatch(gap->Set.of("LOOKUP_ID","LOOKUP_UUID","SECTION","TICK","TICK_ITERATION_PENDING","TRACKING","KNOWN_UUID").contains(gap));
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
        if(lookup.pro$uuids().getClass()!=HashMap.class || manager.pro$known().getClass()!=HashSet.class
                || lookup.pro$ids().getClass()!=it.unimi.dsi.fastutil.ints.Int2ObjectLinkedOpenHashMap.class
                || ticks.pro$active().getClass()!=it.unimi.dsi.fastutil.ints.Int2ObjectLinkedOpenHashMap.class
                || sections.pro$all().getClass()!=it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap.class) return false;
        EntitySection<Entity> row=sections.pro$all().get(section);
        if(row==null)return false;
        Access.Group g=(Access.Group)((Access.Section)row).pro$storage();
        if(g.pro$all().getClass()!=ArrayList.class || g.pro$classes().getClass()!=HashMap.class)return false;
        for(List<Entity> list:g.pro$classes().values())if(list.getClass()!=ArrayList.class)return false;
        return true;
    }
    private boolean standardTracking() {
        Class<?> type=tracking.pro$tracked().getClass();
        return type==it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap.class
                ||type==it.unimi.dsi.fastutil.ints.Int2ObjectLinkedOpenHashMap.class;
    }
    /** Rebind only within the same real world/manager and exact object identity. Never publish old containers. */
    EntityRecords repairProtectedCarrier() {
        Entity entity=observedEntity.get();if(entity==null)return null;var raw=(Access.EntityState)entity;
        if(raw.pro$level()!=level||raw.pro$id()!=id||!raw.pro$uuid().equals(uuid)||entity.getClass()!=definition
                ||server.pro$manager()!=manager||server.pro$ticks()!=ticks||manager.pro$lookup()!=lookup
                ||manager.pro$sections()!=sections||level.getChunkSource().chunkMap!=tracking)return null;
        EntityRecords current=new EntityRecords(level,entity);
        if(!current.bound()||current.conflict()||!current.softRepairSupported()||!current.standardTracking())return null;
        if(!(capturedCallback instanceof Access.Callback callback)||callback.pro$entity()!=entity)return null;
        Object tracked=current.tracking.pro$tracked().get(id);
        if(tracked!=null&&(!(tracked instanceof Access.Tracked row)||row.pro$entity()!=entity))return null;
        if(!current.bound())return null;
        raw.pro$removal(null);raw.pro$canUpdate(capturedCanUpdate);raw.pro$noPhysics(capturedNoPhysics);raw.pro$added(true);
        callback.pro$sectionKey(current.section);callback.pro$section(current.sections.pro$all().get(current.section));
        raw.pro$callback(capturedCallback);
        if(entity instanceof net.minecraft.world.entity.LivingEntity) {
            ((Access.Living)entity).pro$dead(false);((Access.Living)entity).pro$deathTime(0);
        }
        var box=entity.getBoundingBox();
        if(!Double.isFinite(box.getSize())||box.getXsize()<=0||box.getYsize()<=0||box.getZsize()<=0)
            entity.setBoundingBox(capturedBox.move(raw.pro$position().subtract(capturedPosition)));
        if(!current.repair())return null;
        if(entity instanceof ServerPlayer player) {
            // Keep the live protected session in the actual player registries as well as entity indices.
            var players=(Access.Players)level.getServer().getPlayerList();
            ServerPlayer indexed=players.pro$byUuid().get(uuid);
            if(indexed!=null&&indexed!=player)return null;
            if(!current.server.pro$players().contains(player))current.server.pro$players().add(player);
            if(!players.pro$players().contains(player))players.pro$players().add(player);
            players.pro$byUuid().putIfAbsent(uuid,player);
        }
        if(tracked==null)current.tracking.pro$add(entity);
        if(!current.bound()||!current.registered())return null;
        Object restored=current.tracking.pro$tracked().get(id);
        if(!(restored instanceof Access.Tracked row)||row.pro$entity()!=entity)return null;
        // Capture the repaired state, not the attacker's disabled callback/flags.
        return new EntityRecords(level,entity);
    }
    public boolean repair() {
        Entity entity=observedEntity.get();if(entity==null)return false;var raw=(Access.EntityState)entity;
        if(!softRepairSupported())return false;
        if(!bound() || conflict() || raw.pro$removal()!=null || SectionPos.asLong(raw.pro$blockPosition())!=section) return false;
        EntitySection<Entity> row=sections.pro$all().get(section);
        if(row==null) return false; // A lost owner/section is not an invitation to rebuild an unknown callback.
        lookup.pro$ids().putIfAbsent(id,entity);
        lookup.pro$uuids().putIfAbsent(uuid,entity);
        manager.pro$known().add(uuid);
        Access.Group group=(Access.Group)((Access.Section)row).pro$storage();
        if(group.pro$all().stream().noneMatch(e->e==entity)) group.pro$all().add(entity);
        for(var entry:group.pro$classes().entrySet())
            if(entry.getKey().isInstance(entity) && entry.getValue().stream().noneMatch(e->e==entity))
                entry.getValue().add(entity);
        if(originallyTicking && ((EntitySection<Entity>)row).getStatus().isTicking()) ticks.pro$active().putIfAbsent(id,entity);
        return registered();
    }
    void releaseOwnedReferences() {
        ownedEntity=null;originalVehicle=null;originalPassengers=List.of();capturedCallback=null;referencesReleased=true;
    }
    boolean ownedReferencesReleased() { return referencesReleased&&ownedEntity==null&&originalVehicle==null&&originalPassengers.isEmpty()&&capturedCallback==null; }
}
