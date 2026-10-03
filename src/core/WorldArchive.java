package dev.ronova.pro;

import java.io.*;
import java.nio.channels.FileChannel;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import com.mojang.datafixers.util.Either;
import dev.ronova.pro.mixin.Access;
import net.minecraft.core.*;
import net.minecraft.core.registries.*;
import net.minecraft.nbt.*;
import net.minecraft.resources.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.*;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;
import net.minecraft.world.ticks.*;

/** Chunk-sized durable history records and selected seed regeneration under the existing runtime authority. */
final class WorldArchive {
    private static final TicketType<UUID> TICKET=TicketType.create("ronova_world_archive",Comparator.comparing(UUID::toString));
    private enum Kind { CAPTURE, RESTORE, REGENERATE }
    private static final class Job {
        final UUID id,source;final Kind kind;final ResourceKey<Level> dimension;final BlockPos low,high;final long seed;
        long cursor;int blockCursor;String state="QUEUED",reason="";boolean stopped;
        ChunkPos ticket;CompletableFuture<Either<ChunkAccess,ChunkHolder.ChunkLoadingFailure>> loading;
        CompoundTag image;SeedChunks generation;CompletableFuture<ChunkAccess> lighting;CompletableFuture<Void> receipt;
        Job(UUID id,UUID source,Kind kind,ResourceKey<Level> dimension,BlockPos low,BlockPos high,long seed){this.id=id;this.source=source;this.kind=kind;this.dimension=dimension;this.low=low;this.high=high;this.seed=seed;}
        int minX(){return Math.floorDiv(low.getX(),16);}int minZ(){return Math.floorDiv(low.getZ(),16);}
        long width(){return (long)Math.floorDiv(high.getX(),16)-minX()+1;}long depth(){return (long)Math.floorDiv(high.getZ(),16)-minZ()+1;}
        long count(){return Math.multiplyExact(width(),depth());}
        ChunkPos position(){return new ChunkPos((int)(minX()+cursor%width()),(int)(minZ()+cursor/width()));}
        boolean contains(BlockPos pos){return pos.getX()>=low.getX()&&pos.getX()<=high.getX()&&pos.getY()>=low.getY()&&pos.getY()<=high.getY()&&pos.getZ()>=low.getZ()&&pos.getZ()<=high.getZ();}
    }
    private final MinecraftServer server;
    private final ProRuntime runtime;
    private final IntentJournal journal;
    private final Path root;
    private final Map<UUID,Job> jobs=new LinkedHashMap<>();
    private int next;
    WorldArchive(MinecraftServer server,ProRuntime runtime,IntentJournal journal,Path root)throws IOException{
        this.server=server;this.runtime=runtime;this.journal=journal;this.root=root.toAbsolutePath().normalize();
        Path index=this.root.resolve("index.nbt");if(!Files.exists(index))return;CompoundTag saved=read(index);
        if(saved.getInt("Format")!=1||!saved.hasUUID("Realm")||!saved.getUUID("Realm").equals(journal.realm))throw new IOException("WORLD_HISTORY_REALM_MISMATCH");
        for(Tag value:saved.getList("Jobs",Tag.TAG_INT_ARRAY)){
            UUID id=NbtUtils.loadUUID(value);Job job=load(read(directory(id).resolve("manifest.nbt")));
            if(!job.id.equals(id))throw new IOException("WORLD_HISTORY_JOB_ID_MISMATCH");jobs.put(id,job);
        }
    }
    private Path directory(UUID id){return root.resolve(id.toString());}
    private Path image(UUID id,ChunkPos position){return directory(id).resolve(Long.toUnsignedString(position.toLong())+".nbt");}
    private static CompoundTag read(Path path)throws IOException{return NbtIo.readCompressed(path.toFile());}
    private static void write(Path path,CompoundTag image)throws IOException{
        Files.createDirectories(path.getParent());Path temporary=path.resolveSibling(path.getFileName()+".pending");
        NbtIo.writeCompressed(image,temporary.toFile());try(FileChannel channel=FileChannel.open(temporary,StandardOpenOption.WRITE)){channel.force(true);}
        Files.move(temporary,path,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);
    }
    private void index()throws IOException{CompoundTag tag=new CompoundTag();tag.putInt("Format",1);tag.putUUID("Realm",journal.realm);ListTag ids=new ListTag();for(UUID id:jobs.keySet())ids.add(NbtUtils.createUUID(id));tag.put("Jobs",ids);write(root.resolve("index.nbt"),tag);}
    private CompoundTag manifest(Job job){
        CompoundTag tag=new CompoundTag();tag.putInt("Format",1);tag.putUUID("Realm",journal.realm);tag.putUUID("Id",job.id);if(job.source!=null)tag.putUUID("Source",job.source);
        tag.putString("Kind",job.kind.name());tag.putString("Dimension",job.dimension.location().toString());tag.put("Low",NbtUtils.writeBlockPos(job.low));tag.put("High",NbtUtils.writeBlockPos(job.high));
        tag.putLong("Seed",job.seed);tag.putLong("Cursor",job.cursor);tag.putInt("BlockCursor",job.blockCursor);tag.putString("State",job.state);tag.putString("Reason",job.reason);tag.putBoolean("Stopped",job.stopped);return tag;
    }
    private Job load(CompoundTag tag)throws IOException{
        try{
            if(tag.getInt("Format")!=1||!tag.getUUID("Realm").equals(journal.realm))throw new IllegalArgumentException("realm");
            Job job=new Job(tag.getUUID("Id"),tag.hasUUID("Source")?tag.getUUID("Source"):null,Kind.valueOf(tag.getString("Kind")),ResourceKey.create(Registries.DIMENSION,new ResourceLocation(tag.getString("Dimension"))),
                    NbtUtils.readBlockPos(tag.getCompound("Low")),NbtUtils.readBlockPos(tag.getCompound("High")),tag.getLong("Seed"));
            job.cursor=tag.getLong("Cursor");job.blockCursor=tag.getInt("BlockCursor");job.state=tag.getString("State");job.reason=tag.getString("Reason");job.stopped=tag.getBoolean("Stopped");
            if(job.low.getX()>job.high.getX()||job.low.getY()>job.high.getY()||job.low.getZ()>job.high.getZ()||job.cursor<0||job.cursor>job.count()||job.blockCursor<0)throw new IllegalArgumentException("bounds");return job;
        }catch(RuntimeException invalid){throw new IOException("WORLD_HISTORY_MANIFEST_INVALID",invalid);}
    }
    private void persist(Job job)throws IOException{write(directory(job.id).resolve("manifest.nbt"),manifest(job));}
    UUID accept(ServerLevel level,BlockPos first,BlockPos second,boolean regenerate)throws IOException{
        BlockPos low=new BlockPos(Math.min(first.getX(),second.getX()),Math.min(first.getY(),second.getY()),Math.min(first.getZ(),second.getZ()));
        BlockPos high=new BlockPos(Math.max(first.getX(),second.getX()),Math.max(first.getY(),second.getY()),Math.max(first.getZ(),second.getZ()));
        if(low.getY()<level.getMinBuildHeight()||high.getY()>=level.getMaxBuildHeight()||!Level.isInSpawnableBounds(low)||!Level.isInSpawnableBounds(high))throw new IllegalArgumentException("WORLD_HISTORY_BOUNDS_INVALID");
        Job job=new Job(UUID.randomUUID(),null,regenerate?Kind.REGENERATE:Kind.CAPTURE,level.dimension(),low,high,level.getSeed());job.count();
        persist(job);jobs.put(job.id,job);index();runtime.archiveFreeze(this,job.id,level,low,high,true);journal.append("WORLD/1\tACCEPT\t"+job.id+"\t"+job.kind+"\t"+job.dimension.location());return job.id;
    }
    UUID restore(UUID source)throws IOException{
        Job snapshot=jobs.get(source);if(snapshot==null||snapshot.kind==Kind.RESTORE||!snapshot.state.equals("COMPLETE"))throw new IllegalArgumentException("WORLD_HISTORY_SNAPSHOT_NOT_COMPLETE");
        Job job=new Job(UUID.randomUUID(),source,Kind.RESTORE,snapshot.dimension,snapshot.low,snapshot.high,snapshot.seed);persist(job);jobs.put(job.id,job);index();
        ServerLevel level=server.getLevel(job.dimension);if(level==null)throw new IllegalStateException("WORLD_DIMENSION_UNAVAILABLE");runtime.archiveFreeze(this,job.id,level,job.low,job.high,true);
        journal.append("WORLD/1\tACCEPT\t"+job.id+"\tRESTORE\t"+source);return job.id;
    }
    boolean cancel(UUID id)throws IOException{Job job=jobs.get(id);if(job==null||job.state.equals("COMPLETE"))return false;job.stopped=true;job.state="CANCELLED";job.reason="ALREADY_APPLIED_EFFECTS_RETAINED";release(job);unfreeze(job);persist(job);return true;}
    boolean retry(UUID id)throws IOException{Job job=jobs.get(id);if(job==null||!job.state.equals("FAILED"))return false;job.stopped=false;job.state="QUEUED";job.reason="";persist(job);return true;}
    String state(UUID id){Job job=jobs.get(id);return job==null?"未找到世界工作。":"WORLD="+job.id+";ACTION="+job.kind+";STATE="+job.state+";CHUNKS="+job.cursor+"/"+job.count()+";BLOCK_CURSOR="+job.blockCursor+";WORLD_SAVE="+(job.kind==Kind.CAPTURE?"NOT_CHANGED":"NORMAL_WORLD_SAVE_UNCONFIRMED")+";REASON="+job.reason;}
    void tick(){
        if(jobs.isEmpty())return;List<Job> order=new ArrayList<>(jobs.values());
        for(int i=0;i<order.size();i++){Job job=order.get(next++%order.size());if(next<0)next=0;if(job.stopped||job.state.equals("COMPLETE"))continue;
            try{advance(job);}catch(IOException|RuntimeException failure){job.state="FAILED";job.reason=failure.getClass().getSimpleName()+":"+String.valueOf(failure.getMessage());job.stopped=true;release(job);unfreeze(job);try{persist(job);}catch(IOException failedPersistence){job.reason+=";MANIFEST_WRITE_FAILED";}}break;
        }
    }
    private void advance(Job job)throws IOException{
        ServerLevel level=server.getLevel(job.dimension);if(level==null)throw new IllegalStateException("WORLD_DIMENSION_UNAVAILABLE");
        runtime.archiveFreeze(this,job.id,level,job.low,job.high,true);
        if(job.cursor==job.count()){
            if(job.receipt==null){String record="WORLD/1\tCOMPLETE\t"+job.id+"\t"+job.kind+"\t"+job.count();
                job.receipt=journal.history().contains(record)?CompletableFuture.completedFuture(null):journal.append(record);job.state="COMMIT_PENDING";persist(job);}
            if(job.receipt.isDone()){job.receipt.join();job.state="COMPLETE";persist(job);unfreeze(job);}return;
        }
        ChunkPos position=job.position();
        if(job.ticket==null){job.ticket=position;level.getChunkSource().addRegionTicket(TICKET,position,0,job.id);job.loading=level.getChunkSource().getChunkFuture(position.x,position.z,ChunkStatus.FULL,true);job.state="LOADING";}
        if(!job.loading.isDone())return;
        ChunkAccess loaded=job.loading.join().left().orElseThrow(()->new IllegalStateException("WORLD_SELECTED_CHUNK_LOAD_FAILED"));
        if(!(loaded instanceof LevelChunk live)||!live.getPos().equals(position))throw new IllegalStateException("WORLD_SELECTED_CHUNK_IDENTITY_MISMATCH");
        if(job.kind==Kind.CAPTURE){write(image(job.id,position),capture(job,live));finishChunk(job);return;}
        if(job.kind==Kind.REGENERATE){
            if(level.getSeed()!=job.seed)throw new IllegalStateException("WORLD_HISTORY_SEED_CHANGED");
            if(!Files.exists(image(job.id,position)))write(image(job.id,position),capture(job,live));
            if(job.generation==null){job.generation=new SeedChunks(level,position);runtime.archiveControls(this,job.generation.controlObjects());job.state="GENERATING";}
            job.generation.advance();if(!job.generation.ready())return;
            if(job.image==null)job.image=capture(job,job.generation.result());
        }else if(job.image==null)job.image=read(image(job.source,position));
        if(job.lighting!=null){if(!job.lighting.isDone())return;job.lighting.join();publish(level,live);finishChunk(job);return;}
        apply(job,level,live);
    }
    private static BlockPos lower(Job job,ChunkPos pos){return new BlockPos(Math.max(job.low.getX(),pos.getMinBlockX()),job.low.getY(),Math.max(job.low.getZ(),pos.getMinBlockZ()));}
    private static BlockPos upper(Job job,ChunkPos pos){return new BlockPos(Math.min(job.high.getX(),pos.getMaxBlockX()),job.high.getY(),Math.min(job.high.getZ(),pos.getMaxBlockZ()));}
    private CompoundTag capture(Job job,ChunkAccess chunk){
        ServerLevel level=server.getLevel(job.dimension);BlockPos low=lower(job,chunk.getPos()),high=upper(job,chunk.getPos());CompoundTag tag=new CompoundTag();tag.putInt("Format",1);tag.putUUID("Realm",journal.realm);tag.putLong("Chunk",chunk.getPos().toLong());
        tag.put("Low",NbtUtils.writeBlockPos(low));tag.put("High",NbtUtils.writeBlockPos(high));tag.putLong("GameTime",level.getGameTime());
        Map<BlockState,Integer> palette=new LinkedHashMap<>();ListTag states=new ListTag();int count=Math.multiplyExact(Math.multiplyExact(high.getX()-low.getX()+1,high.getZ()-low.getZ()+1),high.getY()-low.getY()+1);int[] cells=new int[count];int at=0;
        for(BlockPos pos:BlockPos.betweenClosed(low,high)){BlockState state=chunk.getBlockState(pos);Integer index=palette.get(state);if(index==null){index=palette.size();palette.put(state,index);states.add(NbtUtils.writeBlockState(state));}cells[at++]=index;}
        tag.put("Palette",states);tag.putIntArray("Cells",cells);ListTag tiles=new ListTag();
        for(BlockPos pos:chunk.getBlockEntitiesPos())if(job.contains(pos)){CompoundTag data=chunk.getBlockEntityNbtForSaving(pos);if(data!=null)tiles.add(data.copy());}tag.put("Tiles",tiles);
        ListTag bodies=new ListTag();
        if(chunk instanceof ProtoChunk proto){for(CompoundTag data:proto.getEntities())if(data.contains("Pos",Tag.TAG_LIST)&&inside(job,data))bodies.add(data.copy());}
        else for(Entity entity:level.getAllEntities()){
            Access.EntityState body=(Access.EntityState)entity;BlockPos pos=body.pro$blockPosition();if(ChunkPos.asLong(pos)!=chunk.getPos().toLong()||!job.contains(pos))continue;
            CompoundTag data=new CompoundTag();entity.saveWithoutId(data);data.putString("id",BuiltInRegistries.ENTITY_TYPE.getKey(body.pro$type()).toString());data.putUUID("UUID",body.pro$uuid());data.remove("Passengers");
            if(body.pro$vehicle()!=null)data.putUUID("RonovaVehicle",((Access.EntityState)body.pro$vehicle()).pro$uuid());bodies.add(data);
        }tag.put("Entities",bodies);
        ListTag blockTicks=ticks(chunk.getBlockTicks(),job,level.getGameTime(),BuiltInRegistries.BLOCK),fluidTicks=ticks(chunk.getFluidTicks(),job,level.getGameTime(),BuiltInRegistries.FLUID);
        if(chunk instanceof LevelChunk){appendHeld(blockTicks,runtime.archiveHeldTicks(this,level,low,high,false,false),level.getGameTime(),BuiltInRegistries.BLOCK);appendHeld(fluidTicks,runtime.archiveHeldTicks(this,level,low,high,true,false),level.getGameTime(),BuiltInRegistries.FLUID);}
        tag.put("BlockTicks",blockTicks);tag.put("FluidTicks",fluidTicks);
        boolean full=low.getX()==chunk.getPos().getMinBlockX()&&high.getX()==chunk.getPos().getMaxBlockX()&&low.getZ()==chunk.getPos().getMinBlockZ()&&high.getZ()==chunk.getPos().getMaxBlockZ()&&low.getY()==level.getMinBuildHeight()&&high.getY()==level.getMaxBuildHeight()-1;
        tag.putBoolean("FullChunk",full);if(full){
            ListTag starts=new ListTag();chunk.getAllStarts().forEach((structure,start)->{CompoundTag saved=start.createTag(StructurePieceSerializationContext.fromLevel(level),chunk.getPos());saved.putString("RonovaStructure",level.registryAccess().registryOrThrow(Registries.STRUCTURE).getKey(structure).toString());starts.add(saved);});tag.put("Starts",starts);
            ListTag references=new ListTag();chunk.getAllReferences().forEach((structure,refs)->{CompoundTag saved=new CompoundTag();saved.putString("Type",level.registryAccess().registryOrThrow(Registries.STRUCTURE).getKey(structure).toString());saved.putLongArray("References",refs.toLongArray());references.add(saved);});tag.put("References",references);
            ListTag biomes=new ListTag();for(int y=QuartPos.fromBlock(low.getY());y<=QuartPos.fromBlock(high.getY());y++)for(int z=0;z<4;z++)for(int x=0;x<4;x++)biomes.add(StringTag.valueOf(chunk.getNoiseBiome(QuartPos.fromBlock(low.getX())+x,y,QuartPos.fromBlock(low.getZ())+z).unwrapKey().orElseThrow().location().toString()));tag.put("Biomes",biomes);
        }return tag;
    }
    private static boolean inside(Job job,CompoundTag data){ListTag position=data.getList("Pos",Tag.TAG_DOUBLE);return position.size()==3&&job.contains(BlockPos.containing(position.getDouble(0),position.getDouble(1),position.getDouble(2)));}
    private static <T> ListTag ticks(TickContainerAccess<T> container,Job job,long now,Registry<T> registry){
        ListTag result=new ListTag();
        if(container instanceof LevelChunkTicks<T> live)live.getAll().filter(tick->job.contains(tick.pos())).forEach(tick->{CompoundTag saved=new CompoundTag();saved.putString("Type",registry.getKey(tick.type()).toString());saved.put("Position",NbtUtils.writeBlockPos(tick.pos()));saved.putLong("Delay",tick.triggerTick()-now);saved.putInt("Priority",tick.priority().getValue());saved.putLong("Order",tick.subTickOrder());result.add(saved);});
        else if(container instanceof ProtoChunkTicks<T> proto)for(SavedTick<T> tick:proto.scheduledTicks())if(job.contains(tick.pos())){CompoundTag saved=new CompoundTag();saved.putString("Type",registry.getKey(tick.type()).toString());saved.put("Position",NbtUtils.writeBlockPos(tick.pos()));saved.putLong("Delay",tick.delay());saved.putInt("Priority",tick.priority().getValue());saved.putLong("Order",result.size());result.add(saved);}
        else throw new IllegalStateException("WORLD_TICK_CONTAINER_UNAVAILABLE");return result;
    }
    private static <T> void appendHeld(ListTag target,List<ScheduledTick<?>> held,long now,Registry<T> registry){
        for(ScheduledTick<?> tick:held){@SuppressWarnings("unchecked") T type=(T)tick.type();CompoundTag tag=new CompoundTag();tag.putString("Type",registry.getKey(type).toString());tag.put("Position",NbtUtils.writeBlockPos(tick.pos()));tag.putLong("Delay",tick.triggerTick()-now);tag.putInt("Priority",tick.priority().getValue());tag.putLong("Order",tick.subTickOrder());target.add(tag);}
    }
    private void apply(Job job,ServerLevel level,LevelChunk chunk)throws IOException{
        CompoundTag frame=job.image;BlockPos low=lower(job,chunk.getPos()),high=upper(job,chunk.getPos());
        if(frame.getInt("Format")!=1||!frame.getUUID("Realm").equals(journal.realm)||frame.getLong("Chunk")!=chunk.getPos().toLong()||!NbtUtils.readBlockPos(frame.getCompound("Low")).equals(low)||!NbtUtils.readBlockPos(frame.getCompound("High")).equals(high))throw new IOException("WORLD_HISTORY_IMAGE_SCOPE_MISMATCH");
        int[] cells=frame.getIntArray("Cells");int width=high.getX()-low.getX()+1,depth=high.getZ()-low.getZ()+1,height=high.getY()-low.getY()+1;
        if(cells.length!=Math.multiplyExact(Math.multiplyExact(width,depth),height)||job.blockCursor>cells.length)throw new IOException("WORLD_HISTORY_IMAGE_CELLS_INVALID");
        List<BlockState> palette=new ArrayList<>();for(Tag value:frame.getList("Palette",Tag.TAG_COMPOUND))palette.add(NbtUtils.readBlockState(level.holderLookup(Registries.BLOCK),(CompoundTag)value));
        job.state="APPLYING";for(int budget=0;job.blockCursor<cells.length&&budget<4096;budget++,job.blockCursor++){
            int index=job.blockCursor,pal=cells[index];if(pal<0||pal>=palette.size())throw new IOException("WORLD_HISTORY_PALETTE_INDEX_INVALID");
            BlockPos pos=new BlockPos(low.getX()+index%width,low.getY()+(index/width)%height,low.getZ()+index/(width*height));BlockState desired=palette.get(pal);
            if(chunk.getBlockState(pos)!=desired){level.setBlock(pos,desired,Block.UPDATE_CLIENTS|Block.UPDATE_KNOWN_SHAPE|Block.UPDATE_SUPPRESS_DROPS);
                if(chunk.getBlockState(pos)!=desired)throw new IllegalStateException("WORLD_BLOCK_RESTORE_REFUSED:"+pos);}
        }persist(job);if(job.blockCursor!=cells.length)return;
        Set<BlockPos> wantedTiles=new HashSet<>();for(Tag value:frame.getList("Tiles",Tag.TAG_COMPOUND)){CompoundTag saved=(CompoundTag)value;BlockPos pos=new BlockPos(saved.getInt("x"),saved.getInt("y"),saved.getInt("z"));
            if(!job.contains(pos)||ChunkPos.asLong(pos)!=chunk.getPos().toLong())throw new IOException("WORLD_TILE_IMAGE_OUTSIDE_SCOPE");wantedTiles.add(pos);
            BlockEntity restored=BlockEntity.loadStatic(pos,chunk.getBlockState(pos),saved.copy());if(restored==null)throw new IllegalStateException("WORLD_TILE_TYPE_UNAVAILABLE:"+pos);chunk.removeBlockEntity(pos);chunk.setBlockEntity(restored);restored.setChanged();
            if(chunk.getBlockEntity(pos)!=restored)throw new IllegalStateException("WORLD_TILE_RESTORE_REFUSED:"+pos);
        }
        for(BlockPos pos:List.copyOf(chunk.getBlockEntitiesPos()))if(job.contains(pos)&&!wantedTiles.contains(pos))chunk.removeBlockEntity(pos);
        BoundingBox selection=new BoundingBox(low.getX(),low.getY(),low.getZ(),high.getX(),high.getY(),high.getZ());level.getBlockTicks().clearArea(selection);level.getFluidTicks().clearArea(selection);
        runtime.archiveHeldTicks(this,level,low,high,false,true);runtime.archiveHeldTicks(this,level,low,high,true,true);
        restoreTicks(level.getBlockTicks(),frame.getList("BlockTicks",Tag.TAG_COMPOUND),BuiltInRegistries.BLOCK,level.getGameTime(),job);
        restoreTicks(level.getFluidTicks(),frame.getList("FluidTicks",Tag.TAG_COMPOUND),BuiltInRegistries.FLUID,level.getGameTime(),job);
        if(!runtime.archiveEntities(this,level,low,high,frame.getList("Entities",Tag.TAG_COMPOUND))){job.state="ENTITY_RESTORE_PENDING";persist(job);return;}
        if(frame.getBoolean("FullChunk"))restoreMetadata(level,chunk,frame);
        chunk.setUnsaved(true);job.state="LIGHTING";job.lighting=level.getChunkSource().getLightEngine().initializeLight(chunk,false).thenCompose(actual->level.getChunkSource().getLightEngine().lightChunk(actual,false));
    }
    private static <T> void restoreTicks(LevelTicks<T> container,ListTag saved,Registry<T> registry,long now,Job job)throws IOException{
        for(Tag value:saved){CompoundTag tag=(CompoundTag)value;BlockPos pos=NbtUtils.readBlockPos(tag.getCompound("Position"));T type=registry.getOptional(new ResourceLocation(tag.getString("Type"))).orElseThrow(()->new IllegalStateException("WORLD_TICK_TYPE_UNAVAILABLE"));
            if(!job.contains(pos))throw new IOException("WORLD_TICK_IMAGE_OUTSIDE_SCOPE");container.schedule(new ScheduledTick<>(type,pos,Math.addExact(now,tag.getLong("Delay")),TickPriority.byValue(tag.getInt("Priority")),tag.getLong("Order")));}
    }
    private static void restoreMetadata(ServerLevel level,LevelChunk chunk,CompoundTag saved){
        var structures=level.registryAccess().registryOrThrow(Registries.STRUCTURE);Map<net.minecraft.world.level.levelgen.structure.Structure,StructureStart> starts=new HashMap<>();
        for(Tag value:saved.getList("Starts",Tag.TAG_COMPOUND)){CompoundTag tag=(CompoundTag)value;var type=structures.getOptional(new ResourceLocation(tag.getString("RonovaStructure"))).orElseThrow();StructureStart start=StructureStart.loadStaticStart(StructurePieceSerializationContext.fromLevel(level),tag,level.getSeed());if(start!=null)starts.put(type,start);}
        chunk.setAllStarts(starts);Map<net.minecraft.world.level.levelgen.structure.Structure,it.unimi.dsi.fastutil.longs.LongSet> references=new HashMap<>();
        for(Tag value:saved.getList("References",Tag.TAG_COMPOUND)){CompoundTag tag=(CompoundTag)value;references.put(structures.getOptional(new ResourceLocation(tag.getString("Type"))).orElseThrow(),new it.unimi.dsi.fastutil.longs.LongOpenHashSet(tag.getLongArray("References")));}chunk.setAllReferences(references);level.onStructureStartsAvailable(chunk);
        ListTag encoded=saved.getList("Biomes",Tag.TAG_STRING);var biomes=level.registryAccess().registryOrThrow(Registries.BIOME);int minimum=QuartPos.fromBlock(level.getMinBuildHeight());
        if(encoded.size()!=(QuartPos.fromBlock(level.getMaxBuildHeight()-1)-minimum+1)*16)throw new IllegalStateException("WORLD_BIOME_IMAGE_INVALID");
        chunk.fillBiomesFromNoise((x,y,z,sampler)->biomes.getHolderOrThrow(ResourceKey.create(Registries.BIOME,new ResourceLocation(encoded.getString((y-minimum)*16+Math.floorMod(z,4)*4+Math.floorMod(x,4))))),level.getChunkSource().randomState().sampler());
    }
    private static void publish(ServerLevel level,LevelChunk chunk){
        var packet=new net.minecraft.network.protocol.game.ClientboundLevelChunkWithLightPacket(chunk,level.getChunkSource().getLightEngine(),null,null);
        for(ServerPlayer player:level.getChunkSource().chunkMap.getPlayers(chunk.getPos(),false))player.connection.send(packet);
    }
    private void finishChunk(Job job)throws IOException{release(job);job.cursor++;job.blockCursor=0;job.image=null;job.generation=null;job.lighting=null;job.state="QUEUED";persist(job);}
    private void release(Job job){ServerLevel level=server.getLevel(job.dimension);if(job.ticket!=null&&level!=null)level.getChunkSource().removeRegionTicket(TICKET,job.ticket,0,job.id);job.ticket=null;job.loading=null;}
    private void unfreeze(Job job){ServerLevel level=server.getLevel(job.dimension);if(level!=null)runtime.archiveFreeze(this,job.id,level,job.low,job.high,false);}
    void close(){for(Job job:jobs.values()){release(job);unfreeze(job);}}
    Object[] controlObjects(){return new Object[]{this,jobs};}
}
