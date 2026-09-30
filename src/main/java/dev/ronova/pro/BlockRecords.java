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
    final LevelChunk chunk;
    final BlockEntity entity;
    final BlockPos pos;
    final BlockState state;
    final Object ticker;
    private final Object recordOwner, tickerOwner;
    public BlockRecords(ServerLevel level,BlockPos pos) {
        this.level=level; this.pos=pos.immutable();
        chunk=level.getChunkAt(pos);
        entity=chunk.getBlockEntities().get(pos);
        if(entity==null) throw new IllegalArgumentException("NO_BLOCK_ENTITY");
        state=level.getBlockState(pos);
        ticker=((Access.Chunk)chunk).pro$tickers().get(pos);
        recordOwner=chunk.getBlockEntities(); tickerOwner=((Access.Chunk)chunk).pro$tickers();
    }
    public boolean bound() {
        return level.getServer().isSameThread() && level.hasChunkAt(pos) && level.getChunkAt(pos)==chunk
            && level.getBlockState(pos)==state && chunk.getBlockEntities().get(pos)==entity
            && chunk.getBlockEntities()==recordOwner && ((Access.Chunk)chunk).pro$tickers()==tickerOwner;
    }
    public void clear() {
        // HARD world mutation. setBlock drives the actual block lifecycle and client block update.
        level.setBlock(pos,Blocks.AIR.defaultBlockState(),3);
        if(chunk.getBlockEntities().get(pos)==entity) level.removeBlockEntity(pos);
        // Lifecycle callbacks may have installed an unrelated replacement at this position.
        var tickers=((Access.Chunk)chunk).pro$tickers();
        if(tickers==tickerOwner&&tickers.get(pos)==ticker&&chunk.getBlockEntities().get(pos)!=entity
                &&chunk.getBlockEntities().get(pos)==null)tickers.remove(pos,ticker);
    }
    public Set<String> remaining() {
        Set<String> r=new LinkedHashSet<>();
        if(!entity.isRemoved()) r.add("BLOCK_BODY");
        if(!level.hasChunkAt(pos) || level.getChunkAt(pos)!=chunk) { r.add("CHUNK_UNOBSERVED"); return r; }
        if(chunk.getBlockEntities()!=recordOwner || ((Access.Chunk)chunk).pro$tickers()!=tickerOwner) {
            r.add("BLOCK_OWNER_UNOBSERVED");return r;
        }
        if(chunk.getBlockEntities().values().stream().anyMatch(e->e==entity)) r.add("BLOCK_RECORD");
        if(ticker instanceof TickingBlockEntity t && !t.isRemoved()) r.add("BLOCK_TICK");
        return r;
    }
}
