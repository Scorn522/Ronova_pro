package dev.ronova.pro.mixin;

import java.util.*;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.*;
import net.minecraft.server.network.ServerPlayerConnection;
import net.minecraft.util.ClassInstanceMultiMap;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.entity.*;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.*;

/** Fixed Forge/Minecraft record access. No policy, authority or per-scenario adapters. */
public final class Access {
    private Access() {}
    @Mixin(net.minecraft.world.entity.LivingEntity.class) public interface Living {
        @Accessor("dead") boolean pro$dead();
        @Accessor("dead") void pro$dead(boolean value);
        @Accessor("deathTime") void pro$deathTime(int value);
        @Accessor("DATA_HEALTH_ID") static net.minecraft.network.syncher.EntityDataAccessor<Float> pro$healthKey() { throw new AssertionError(); }
        @Accessor("attributes") net.minecraft.world.entity.ai.attributes.AttributeMap pro$attributes();
    }
    @Mixin(net.minecraft.network.syncher.SynchedEntityData.class) public interface Data {
        @Invoker("getItem") <T> net.minecraft.network.syncher.SynchedEntityData.DataItem<T> pro$item(net.minecraft.network.syncher.EntityDataAccessor<T> key);
        @Accessor("isDirty") void pro$dirty(boolean dirty);
        @Accessor("itemsById") Int2ObjectMap<net.minecraft.network.syncher.SynchedEntityData.DataItem<?>> pro$items();
    }
    @Mixin(Entity.class) public interface EntityState {
        @Accessor("remainingFireTicks") void pro$fire(int ticks);
        @Accessor("remainingFireTicks") int pro$fire();
        @Accessor("id") int pro$id();
        @Accessor("uuid") UUID pro$uuid();
        @Accessor("level") Level pro$level();
        @Accessor("blockPosition") BlockPos pro$blockPosition();
        @Accessor("removalReason") Entity.RemovalReason pro$removal();
        @Accessor("removalReason") void pro$removal(Entity.RemovalReason reason);
        @Accessor("levelCallback") EntityInLevelCallback pro$callback();
        @Accessor("levelCallback") void pro$callback(EntityInLevelCallback callback);
        @Accessor("position") net.minecraft.world.phys.Vec3 pro$position();
        @Accessor("bb") net.minecraft.world.phys.AABB pro$box();
        @Accessor("canUpdate") boolean pro$canUpdate();
        @Accessor("canUpdate") void pro$canUpdate(boolean value);
        @Accessor("noPhysics") boolean pro$noPhysics();
        @Accessor("noPhysics") void pro$noPhysics(boolean value);
        @Accessor("isAddedToWorld") void pro$added(boolean value);
        @Accessor("entityData") net.minecraft.network.syncher.SynchedEntityData pro$data();
        @Accessor("vehicle") Entity pro$vehicle();
        @Accessor("vehicle") void pro$vehicle(Entity value);
        @Accessor("passengers") com.google.common.collect.ImmutableList<Entity> pro$passengers();
        @Accessor("passengers") void pro$passengers(com.google.common.collect.ImmutableList<Entity> value);
    }
    @Mixin(ServerLevel.class) public interface Server {
        @Accessor("entityManager") PersistentEntitySectionManager<Entity> pro$manager();
        @Accessor("entityTickList") EntityTickList pro$ticks();
        @Accessor("players") List<ServerPlayer> pro$players();
    }
    @Mixin(net.minecraft.server.players.PlayerList.class) public interface Players {
        @Accessor("players") List<ServerPlayer> pro$players();
        @Accessor("playersByUUID") Map<UUID,ServerPlayer> pro$byUuid();
    }
    @Mixin(PersistentEntitySectionManager.class) public interface Manager {
        @Accessor("visibleEntityStorage") EntityLookup<Entity> pro$lookup();
        @Accessor("sectionStorage") EntitySectionStorage<Entity> pro$sections();
        @Accessor("knownUuids") Set<UUID> pro$known();
    }
    @Mixin(EntityLookup.class) public interface Lookup {
        @Accessor("byId") Int2ObjectMap<EntityAccess> pro$ids();
        @Accessor("byUuid") Map<UUID,EntityAccess> pro$uuids();
    }
    @Mixin(EntityTickList.class) public interface Ticks {
        @Invoker("ensureActiveIsNotIterated") void pro$ensureActiveIsNotIterated();
        @Accessor("active") Int2ObjectMap<Entity> pro$active();
        @Accessor("passive") Int2ObjectMap<Entity> pro$passive();
        @Accessor("iterated") Int2ObjectMap<Entity> pro$iterated();
    }
    @Mixin(EntitySectionStorage.class) public interface Sections {
        @Accessor("sections") Long2ObjectMap<EntitySection<Entity>> pro$all();
        @Accessor("sectionIds") it.unimi.dsi.fastutil.longs.LongSortedSet pro$keys();
    }
    @Mixin(EntitySection.class) public interface Section {
        @Accessor("storage") ClassInstanceMultiMap<Entity> pro$storage();
    }
    @Mixin(ClassInstanceMultiMap.class) public interface Group {
        @Accessor("allInstances") List<Entity> pro$all();
        @Accessor("byClass") Map<Class<?>,List<Entity>> pro$classes();
    }
    @Mixin(ChunkMap.class) public interface Tracking {
        @Accessor("entityMap") Int2ObjectMap<?> pro$tracked();
        @Accessor("updatingChunkMap") it.unimi.dsi.fastutil.longs.Long2ObjectLinkedOpenHashMap<ChunkHolder> pro$updatingChunks();
        @Accessor("visibleChunkMap") it.unimi.dsi.fastutil.longs.Long2ObjectLinkedOpenHashMap<ChunkHolder> pro$visibleChunks();
        @Accessor("pendingUnloads") it.unimi.dsi.fastutil.longs.Long2ObjectLinkedOpenHashMap<ChunkHolder> pro$pendingChunks();
        @Invoker("removeEntity") void pro$remove(Entity entity);
        @Invoker("addEntity") void pro$add(Entity entity);
    }
    @Mixin(targets="net.minecraft.world.level.entity.PersistentEntitySectionManager$Callback") public interface Callback {
        @Accessor("entity") EntityAccess pro$entity();
        @Accessor("currentSection") EntitySection<Entity> pro$section();
        @Accessor("currentSectionKey") void pro$sectionKey(long key);
        @Accessor("currentSectionKey") long pro$sectionKey();
        @Accessor("currentSection") void pro$section(EntitySection<Entity> section);
    }
    @Mixin(targets="net.minecraft.server.level.ChunkMap$TrackedEntity") public interface Tracked {
        @Accessor("entity") Entity pro$entity();
        @Accessor("seenBy") Set<ServerPlayerConnection> pro$seen();
        @Invoker("broadcastRemoved") void pro$removed();
    }
    @Mixin(LevelChunk.class) public interface Chunk {
        @Accessor("level") Level pro$level();
        @Accessor("tickersInLevel") Map<BlockPos,?> pro$tickers();
    }
    @Mixin(BlockEntity.class) public interface Block {
        @Accessor("level") Level pro$level();
        @Accessor("worldPosition") BlockPos pro$pos();
        @Accessor("remove") boolean pro$removed();
    }
    @Mixin(Level.class) public interface World {
        @Accessor("blockEntityTickers") List<TickingBlockEntity> pro$tickers();
        @Accessor("pendingBlockEntityTickers") List<TickingBlockEntity> pro$pending();
    }
    /** The removal batch is filtered in place so only the entries the current policy does not cover are delivered. */
    @Mixin(net.minecraft.network.protocol.game.ClientboundRemoveEntitiesPacket.class) public interface Packet {
        @Mutable @Accessor("entityIds") void pro$entityIds(it.unimi.dsi.fastutil.ints.IntList ids);
    }
}
