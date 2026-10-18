package dev.ronova.pro;

import dev.ronova.pro.mixin.Access;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;

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
    private boolean released,removedObserved,groupClearRequested,groupStatePending,groupUpdatesPending;
    private final java.lang.ref.WeakReference<BlockState> stateIdentity;
    public BlockRecords(ServerLevel level,BlockPos pos) {
        this.level=level; this.pos=pos.immutable();
        chunk=level.getChunkAt(pos);
        entity=chunk.getBlockEntities().get(pos);
        if(entity==null) throw new IllegalArgumentException("NO_BLOCK_ENTITY");
        state=level.getBlockState(pos);stateIdentity=new java.lang.ref.WeakReference<>(state);
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
    /** A stopped Mod's callbacks cannot be the only path out of its real holders. */
    public void clearGroup() {
        if(!bound()||((Access.Block)entity).pro$level()!=level||!pos.equals(((Access.Block)entity).pro$pos()))
            throw new IllegalStateException("GROUP_BLOCK_BINDING_CHANGED");
        groupClearRequested=true;groupStatePending=true;
        LevelChunkSection[] sections=chunk.getSections();int index=chunk.getSectionIndex(pos.getY());
        LevelChunkSection section=index>=0&&index<sections.length?sections[index]:null;
        Throwable failure=null;
        try {
            // Preserve vanilla callbacks and notifications when they can run.
            level.setBlock(pos,Blocks.AIR.defaultBlockState(),3);
        } catch(Throwable unavailable) {failure=unavailable;}
        finally {
            try {finishGroupState(sections,index,section);}
            catch(Throwable unavailable) {failure=append(failure,unavailable);}
            try {detachGroupRecords();}
            catch(Throwable unavailable) {failure=append(failure,unavailable);}
        }
        if(failure!=null)rethrow(failure);
        Set<String> left=remaining();if(!left.isEmpty())throw new IllegalStateException("GROUP_BLOCK_REMAINS:"+left);
    }
    private boolean ownersCurrent() {
        return !released&&level.getServer().isSameThread()&&level.hasChunkAt(pos)&&level.getChunkAt(pos)==chunk
                &&chunk.getBlockEntities()==recordOwner&&((Access.Chunk)chunk).pro$tickers()==tickerOwner;
    }
    private void finishGroupState(LevelChunkSection[] sections,int index,LevelChunkSection section) {
        if(!ownersCurrent())throw new IllegalStateException("GROUP_BLOCK_OWNER_CHANGED");
        BlockEntity current=chunk.getBlockEntities().get(pos);BlockState actual=chunk.getBlockState(pos);
        // An original callback may already have published a different live body.
        if(current!=null&&current!=entity||actual!=state&&!actual.isAir()) {
            groupStatePending=false;groupUpdatesPending=false;return;
        }
        if(section==null||chunk.getSections()!=sections||sections[index]!=section)
            throw new IllegalStateException("GROUP_BLOCK_SECTION_CHANGED");
        int x=pos.getX()&15,y=pos.getY(),z=pos.getZ()&15;
        BlockState air=Blocks.AIR.defaultBlockState();
        if(actual==state&&!actual.isAir()) {
            if(section.getBlockState(x,y&15,z)!=state)throw new IllegalStateException("GROUP_BLOCK_STATE_CHANGED");
            section.setBlockState(x,y&15,z,air);
        }
        if(chunk.getBlockState(pos)!=air)throw new IllegalStateException("GROUP_BLOCK_AIR_WRITE_REFUSED");
        groupStatePending=false;groupUpdatesPending=true;
        Throwable failure=null;
        // A direct section write owns the same vanilla metadata obligations.
        // Complete each one even if another notification throws.
        failure=attempt(failure,()->chunk.setUnsaved(true));
        for(var height:chunk.getHeightmaps())failure=attempt(failure,()->height.getValue().update(x,y,z,air));
        failure=attempt(failure,()->chunk.getSkyLightSources().update(chunk,x,y,z));
        failure=attempt(failure,()->level.getChunkSource().getLightEngine().updateSectionStatus(SectionPos.of(pos),section.hasOnlyAir()));
        failure=attempt(failure,()->level.getChunkSource().getLightEngine().checkBlock(pos));
        failure=attempt(failure,()->level.getChunkSource().blockChanged(pos));
        failure=attempt(failure,()->level.sendBlockUpdated(pos,state,air,3));
        if(failure!=null)rethrow(failure);
        groupUpdatesPending=false;
    }
    private void detachGroupRecords() {
        Throwable failure=null;
        try {
            if(!ownersCurrent())throw new IllegalStateException("GROUP_BLOCK_OWNER_CHANGED");
            if(chunk.getBlockEntities().get(pos)==entity) {
                try {level.removeBlockEntity(pos);}
                catch(Throwable unavailable) {failure=append(failure,unavailable);}
                finally {
                    if(ownersCurrent()&&chunk.getBlockEntities().get(pos)==entity)chunk.getBlockEntities().remove(pos,entity);
                }
            }
        } catch(Throwable unavailable) {failure=append(failure,unavailable);}
        finally {
            // This exact old body remains the selected Mod's body even when a
            // callback installed an unrelated replacement in its former slot.
            failure=attempt(failure,()->((Access.Block)entity).pro$removed(true));
            try {
                if(!ownersCurrent())throw new IllegalStateException("GROUP_BLOCK_OWNER_CHANGED");
                var tickers=((Access.Chunk)chunk).pro$tickers();BlockEntity current=chunk.getBlockEntities().get(pos);
                if(current==null&&tickers.get(pos)==ticker)tickers.remove(pos,ticker);
                if(ticker!=null&&tickers.values().stream().noneMatch(value->value==ticker)) {
                    failure=attempt(failure,()->((Access.World)level).pro$tickers().removeIf(value->value==ticker));
                    failure=attempt(failure,()->((Access.World)level).pro$pending().removeIf(value->value==ticker));
                }
            } catch(Throwable unavailable) {failure=append(failure,unavailable);}
        }
        if(failure!=null)rethrow(failure);
    }
    private static Throwable attempt(Throwable failure,Runnable action) {
        try {action.run();}catch(Throwable unavailable) {failure=append(failure,unavailable);}return failure;
    }
    private static Throwable append(Throwable failure,Throwable incoming) {
        if(failure==null)return incoming;if(failure!=incoming)failure.addSuppressed(incoming);return failure;
    }
    private static void rethrow(Throwable failure) {
        if(failure instanceof Error error)throw error;
        if(failure instanceof RuntimeException error)throw error;
        throw new IllegalStateException("GROUP_BLOCK_LIFECYCLE_FAILED",failure);
    }
    public Set<String> remaining() {
        Set<String> r=new LinkedHashSet<>();
        LevelChunk chunk=chunkIdentity.get();BlockEntity entity=entityIdentity.get();Object ticker=tickerIdentity.get();
        if(entity!=null&&!((Access.Block)entity).pro$removed()||entity==null&&!removedObserved)r.add("BLOCK_BODY");
        if(!level.hasChunkAt(pos) || level.getChunkAt(pos)!=chunk) { r.add("CHUNK_UNOBSERVED"); return r; }
        if(chunk.getBlockEntities()!=recordIdentity.get() || ((Access.Chunk)chunk).pro$tickers()!=tickerOwnerIdentity.get()) {
            r.add("BLOCK_OWNER_UNOBSERVED");return r;
        }
        if(groupClearRequested) {
            BlockEntity current=chunk.getBlockEntities().get(pos);boolean replacement=current!=null&&current!=entity;
            if(!replacement&&chunk.getBlockState(pos)==stateIdentity.get()&&!chunk.getBlockState(pos).isAir())r.add("BLOCK_STATE");
            if(!replacement&&groupStatePending)r.add("BLOCK_STATE_UNOBSERVED");
            if(!replacement&&groupUpdatesPending)r.add("BLOCK_UPDATES_PENDING");
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
