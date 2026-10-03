package dev.ronova.pro;

import dev.ronova.pro.mixin.Access;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;

/** BlockEntity has its own holder and ticking path; never uses Entity removal. */
public final class BlockRecords {
    final ServerLevel level;
    LevelChunk chunk;
    BlockEntity entity;
    final BlockPos pos;
    BlockState state;
    Object ticker;
    private Object recordOwner,tickerOwner;
    private final java.lang.ref.WeakReference<LevelChunk> chunkIdentity;
    private final java.lang.ref.WeakReference<BlockEntity> entityIdentity;
    private final java.lang.ref.WeakReference<Object> tickerIdentity,recordIdentity,tickerOwnerIdentity;
    private boolean released,removedObserved;
    public BlockRecords(ServerLevel level,BlockPos pos) {
        this.level=level; this.pos=pos.immutable();
        chunk=level.getChunkAt(pos);
        entity=chunk.getBlockEntities().get(pos);
        if(entity==null) throw new IllegalArgumentException("NO_BLOCK_ENTITY");
        state=level.getBlockState(pos);
        ticker=((Access.Chunk)chunk).pro$tickers().get(pos);
        recordOwner=chunk.getBlockEntities(); tickerOwner=((Access.Chunk)chunk).pro$tickers();
        chunkIdentity=new java.lang.ref.WeakReference<>(chunk);entityIdentity=new java.lang.ref.WeakReference<>(entity);
        tickerIdentity=new java.lang.ref.WeakReference<>(ticker);recordIdentity=new java.lang.ref.WeakReference<>(recordOwner);tickerOwnerIdentity=new java.lang.ref.WeakReference<>(tickerOwner);
    }
    public boolean bound() {
        return !released&&level.getServer().isSameThread() && level.hasChunkAt(pos) && level.getChunkAt(pos)==chunk
            && level.getBlockState(pos)==state && chunk.getBlockEntities().get(pos)==entity
            && chunk.getBlockEntities()==recordOwner && ((Access.Chunk)chunk).pro$tickers()==tickerOwner;
    }
    public void clear() {
        // HARD world mutation. setBlock drives the actual block lifecycle and client block update.
        level.setBlock(pos,Blocks.AIR.defaultBlockState(),3);
        if(chunk.getBlockEntities().get(pos)==entity) level.removeBlockEntity(pos);
        ((Access.Block)entity).pro$removed(true);
        // Lifecycle callbacks may have installed an unrelated replacement at this position.
        var tickers=((Access.Chunk)chunk).pro$tickers();
        if(tickers==tickerOwner&&tickers.get(pos)==ticker&&chunk.getBlockEntities().get(pos)!=entity
                &&chunk.getBlockEntities().get(pos)==null)tickers.remove(pos,ticker);
        if(ticker!=null&&chunk.getBlockEntities().get(pos)==null&&tickers.values().stream().noneMatch(value->value==ticker)){
            ((Access.World)level).pro$tickers().removeIf(value->value==ticker);
            ((Access.World)level).pro$pending().removeIf(value->value==ticker);
        }
    }
    public Set<String> remaining() {
        Set<String> r=new LinkedHashSet<>();
        LevelChunk chunk=chunkIdentity.get();BlockEntity entity=entityIdentity.get();Object ticker=tickerIdentity.get();
        if(entity!=null&&!((Access.Block)entity).pro$removed()||entity==null&&!removedObserved)r.add("BLOCK_BODY");
        if(!level.hasChunkAt(pos) || level.getChunkAt(pos)!=chunk) { r.add("CHUNK_UNOBSERVED"); return r; }
        if(chunk.getBlockEntities()!=recordIdentity.get() || ((Access.Chunk)chunk).pro$tickers()!=tickerOwnerIdentity.get()) {
            r.add("BLOCK_OWNER_UNOBSERVED");return r;
        }
        if(entity!=null&&chunk.getBlockEntities().values().stream().anyMatch(e->e==entity))r.add("BLOCK_RECORD");
        if(ticker!=null&&(((Access.World)level).pro$tickers().stream().anyMatch(value->value==ticker)
                ||((Access.World)level).pro$pending().stream().anyMatch(value->value==ticker)
                ||((Access.Chunk)chunk).pro$tickers().values().stream().anyMatch(value->value==ticker)))r.add("BLOCK_TICK");
        return r;
    }
    boolean releaseOwnedReferences(){
        if(released)return true;if(!remaining().isEmpty())return false;
        removedObserved=entity!=null&&((Access.Block)entity).pro$removed();
        chunk=null;entity=null;state=null;ticker=null;recordOwner=null;tickerOwner=null;released=true;return true;
    }
}
