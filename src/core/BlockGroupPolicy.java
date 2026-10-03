package dev.ronova.pro;

import dev.ronova.pro.mixin.Access;
import java.util.*;
import java.lang.ref.WeakReference;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ChunkHolder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.ChunkAccess;

/** Exact, loaded BlockEntity ownership. No chunk is loaded or generated to make a decision. */
public final class BlockGroupPolicy {
    private record Entry(ProRuntime runtime,ServerLevel level,BlockPos pos,
            WeakReference<Map<BlockPos,BlockEntity>> container) { }
    private record Slot(BlockPos pos,WeakReference<BlockEntity> entity) { }
    private static final Map<BlockEntity,Entry> protectedBlocks=Collections.synchronizedMap(new WeakIdentityMap<>());
    private static final Map<Object,List<Slot>> containerSlots=Collections.synchronizedMap(new WeakIdentityMap<>());
    private BlockGroupPolicy() { }

    static List<BlockEntity> loaded(ServerLevel level) {
        Access.Tracking map=(Access.Tracking)level.getChunkSource().chunkMap;
        Set<ChunkHolder> holders=Collections.newSetFromMap(new IdentityHashMap<>());
        holders.addAll(map.pro$updatingChunks().values());
        holders.addAll(map.pro$visibleChunks().values());
        holders.addAll(map.pro$pendingChunks().values());
        Set<BlockEntity> found=Collections.newSetFromMap(new IdentityHashMap<>());
        for(ChunkHolder holder:holders) {
            LevelChunk chunk=holder.getFullChunk();
            if(chunk!=null)found.addAll(chunk.getBlockEntities().values());
        }
        return List.copyOf(found);
    }

    static boolean protect(ProRuntime runtime,BlockEntity entity) {
        if(entity==null||entity.isRemoved())return false;
        Level level=((Access.Block)entity).pro$level();
        if(!(level instanceof ServerLevel server)||server.getServer()!=runtime.server)return false;
        BlockPos pos=((Access.Block)entity).pro$pos();
        if(!server.hasChunkAt(pos))return false;
        Map<BlockPos,BlockEntity> container=server.getChunkAt(pos).getBlockEntities();
        return ProRuntime.withBlockIndexGate(container,()->{
            if(container.get(pos)!=entity)return false;
            protectedBlocks.put(entity,new Entry(runtime,server,pos.immutable(),new WeakReference<>(container)));
            synchronized(containerSlots) {
                List<Slot> slots=containerSlots.get(container);
                if(slots==null) {slots=new ArrayList<>();containerSlots.put(container,slots);}
                slots.removeIf(slot->slot.entity().get()==null||slot.entity().get()==entity);
                slots.add(new Slot(pos.immutable(),new WeakReference<>(entity)));
            }
            return true;
        });
    }

    static void release(BlockEntity entity) {
        Entry entry=protectedBlocks.get(entity);
        if(entry==null)return;
        Map<BlockPos,BlockEntity> container=entry.container().get();
        if(container==null) {protectedBlocks.remove(entity);return;}
        ProRuntime.withBlockIndexGate(container,()->{
            protectedBlocks.remove(entity);
            synchronized(containerSlots) {
                List<Slot> slots=containerSlots.get(container);
                if(slots!=null) {
                    slots.removeIf(slot->slot.entity().get()==null||slot.entity().get()==entity);
                    if(slots.isEmpty())containerSlots.remove(container);
                }
            }
            return null;
        });
    }

    public static boolean removalDenied(BlockEntity entity) {
        Entry entry=protectedBlocks.get(entity);
        return entry!=null&&!entry.runtime().groupClosing()
                &&((Access.Block)entity).pro$level()==entry.level()
                &&Objects.equals(((Access.Block)entity).pro$pos(),entry.pos());
    }

    public static boolean replacementDenied(LevelChunk chunk,BlockEntity replacement) {
        if(replacement==null)return false;
        BlockEntity previous=chunk.getBlockEntities().get(replacement.getBlockPos());
        return previous!=replacement&&previous!=null&&removalDenied(previous);
    }

    public static boolean fieldDenied(Object receiver,Class<?> declaring,String field,Object value) {
        if(receiver instanceof LevelChunk chunk&&declaring==ChunkAccess.class
                &&(field.equals("blockEntities")||field.equals("f_187610_"))) {
            Map<BlockPos,BlockEntity> container=chunk.getBlockEntities();
            return value!=container&&containerMutationDenied(container,null,null);
        }
        if(!(receiver instanceof BlockEntity block)||declaring!=BlockEntity.class||!removalDenied(block))return false;
        return switch(field) {
            case "remove","f_58859_" -> Boolean.TRUE.equals(value);
            case "level","f_58857_" -> value!=((Access.Block)block).pro$level();
            case "worldPosition","f_58858_" -> !Objects.equals(value,((Access.Block)block).pro$pos());
            default -> false;
        };
    }

    /** The JDK HashMap writer asks about its exact current entry, including mixed clear. */
    public static boolean containerMutationDenied(Object container,Object key,Object value) {
        if(!(container instanceof Map<?,?> map))return false;
        List<Slot> slots;
        synchronized(containerSlots) {
            List<Slot> current=containerSlots.get(container);
            if(current==null)return false;
            slots=List.copyOf(current);
        }
        for(Slot slot:slots) {
            BlockEntity entity=slot.entity().get();
            if(entity==null||!removalDenied(entity)||map.get(slot.pos())!=entity)continue;
            if(key==null&&value==null||Objects.equals(key,slot.pos())&&value==entity)return true;
        }
        return false;
    }

    /** A detached HashMap node is never authority for the still-registered block entry. */
    public static boolean nodeMutationDenied(Object node,Object key,Object value) {
        List<Object> maps;
        synchronized(containerSlots) {maps=new ArrayList<>(containerSlots.keySet());}
        for(Object owner:maps)if(owner instanceof HashMap<?,?> map
                &&containerMutationDenied(map,key,value))
            for(var actual:map.entrySet())if(actual==node)return true;
        return false;
    }

    public static boolean stateChangeDenied(LevelChunk chunk,BlockPos pos,BlockState next) {
        BlockEntity previous=chunk.getBlockEntities().get(pos);
        return previous!=null&&removalDenied(previous)
                &&next.getBlock()!=chunk.getBlockState(pos).getBlock();
    }

    public static boolean publicationDenied(LevelChunk chunk,BlockEntity entity) {
        Level level=((Access.Chunk)chunk).pro$level();
        if(!(level instanceof ServerLevel server))return false;
        ProRuntime runtime=ProRuntime.get(server.getServer());
        return runtime!=null&&runtime.modStopped(entity.getClass().getModule());
    }

    public static void published(BlockEntity entity) {
        Level level=((Access.Block)entity).pro$level();
        if(!(level instanceof ServerLevel server))return;
        ProRuntime runtime=ProRuntime.get(server.getServer());
        if(runtime!=null&&runtime.modProtected(entity.getClass().getModule()))protect(runtime,entity);
    }
}
