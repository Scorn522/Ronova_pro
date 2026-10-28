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
    /** Live work position, not a completion certificate. World persistence is still Minecraft's own save. */
    private enum ApplyStep { BLOCKS, TILES, TICKS, ENTITIES, METADATA, LIGHTING, SAVING, PUBLISH }
    private static final class Job {
        final UUID id,source;final Kind kind;final ResourceKey<Level> dimension;final BlockPos low,high;final long seed;
        long cursor;int blockCursor;String state="QUEUED",reason="";boolean stopped;
        ApplyStep applyStep=ApplyStep.BLOCKS;
        int tileCursor,blockTickCursor,fluidTickCursor;boolean blockTicksCleared,fluidTicksCleared,captureWritten;
        boolean blockTicksStarted,fluidTicksStarted;
        boolean vanillaFlush=true;
        long tickBase=Long.MIN_VALUE;BlockEntity pendingTile;LevelChunk applyingChunk;
        ChunkPos ticket;CompletableFuture<Either<ChunkAccess,ChunkHolder.ChunkLoadingFailure>> loading;
        CompoundTag image,expected,conditions;SeedChunks generation;CompletableFuture<ChunkAccess> lighting;CompletableFuture<Void> acceptance,receipt;
        final Map<BlockPos,BlockEntity> tileOwners=new HashMap<>();
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
    private Path application(Job job,ChunkPos position){return directory(job.id).resolve(Long.toUnsignedString(position.toLong())+".apply.nbt");}
    private Path generated(Job job,ChunkPos position){return directory(job.id).resolve(Long.toUnsignedString(position.toLong())+".seed.nbt");}
    private static CompoundTag read(Path path)throws IOException{return NbtIo.readCompressed(path.toFile());}
    private static void write(Path path,CompoundTag image)throws IOException{
        Files.createDirectories(path.getParent());Path temporary=path.resolveSibling(path.getFileName()+".pending");
        NbtIo.writeCompressed(image,temporary.toFile());try(FileChannel channel=FileChannel.open(temporary,StandardOpenOption.WRITE)){channel.force(true);}
        Files.move(temporary,path,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);
    }
    private void index(UUID additional)throws IOException{
        CompoundTag tag=new CompoundTag();tag.putInt("Format",1);tag.putUUID("Realm",journal.realm);
        ListTag ids=new ListTag();for(UUID id:jobs.keySet())ids.add(NbtUtils.createUUID(id));
        if(additional!=null&&!jobs.containsKey(additional))ids.add(NbtUtils.createUUID(additional));
        tag.put("Jobs",ids);write(root.resolve("index.nbt"),tag);
    }
    private CompoundTag manifest(Job job){
        CompoundTag tag=new CompoundTag();tag.putInt("Format",1);tag.putUUID("Realm",journal.realm);tag.putUUID("Id",job.id);if(job.source!=null)tag.putUUID("Source",job.source);
        tag.putString("Kind",job.kind.name());tag.putString("Dimension",job.dimension.location().toString());tag.put("Low",NbtUtils.writeBlockPos(job.low));tag.put("High",NbtUtils.writeBlockPos(job.high));
        tag.putLong("Seed",job.seed);tag.putLong("Cursor",job.cursor);tag.putInt("BlockCursor",job.blockCursor);tag.putString("State",job.state);tag.putString("Reason",job.reason);tag.putBoolean("Stopped",job.stopped);tag.putBoolean("VanillaFlush",job.vanillaFlush);return tag;
    }
    private Job load(CompoundTag tag)throws IOException{
        try{
            if(tag.getInt("Format")!=1||!tag.getUUID("Realm").equals(journal.realm))throw new IllegalArgumentException("realm");
            Job job=new Job(tag.getUUID("Id"),tag.hasUUID("Source")?tag.getUUID("Source"):null,Kind.valueOf(tag.getString("Kind")),ResourceKey.create(Registries.DIMENSION,new ResourceLocation(tag.getString("Dimension"))),
                    NbtUtils.readBlockPos(tag.getCompound("Low")),NbtUtils.readBlockPos(tag.getCompound("High")),tag.getLong("Seed"));
            job.cursor=tag.getLong("Cursor");job.blockCursor=tag.getInt("BlockCursor");job.state=tag.getString("State");job.reason=tag.getString("Reason");job.stopped=tag.getBoolean("Stopped");
            job.vanillaFlush=tag.getBoolean("VanillaFlush");
            if(job.kind==Kind.REGENERATE){Path conditions=directory(job.id).resolve("generation.nbt");if(Files.exists(conditions)){
                CompoundTag saved=read(conditions);if(saved.getInt("Format")!=1||!saved.hasUUID("Realm")||!saved.getUUID("Realm").equals(journal.realm))throw new IOException("WORLD_GENERATION_CONDITIONS_REALM_MISMATCH");job.conditions=saved.getCompound("Conditions");
            }}
            if(job.state.equals("ACCEPTING")){job.state="FAILED";job.reason="ACCEPTANCE_NOT_PUBLISHED";job.stopped=true;}
            if(journal.recorded(acceptanceRecord(job)))job.acceptance=CompletableFuture.completedFuture(null);
            else {job.state="FAILED";job.reason="ACCEPTANCE_RECORD_UNCONFIRMED";job.stopped=true;}
            if(job.low.getX()>job.high.getX()||job.low.getY()>job.high.getY()||job.low.getZ()>job.high.getZ()||job.cursor<0||job.cursor>job.count()||job.blockCursor<0)throw new IllegalArgumentException("bounds");
            // A journal/manifest cursor is not an atomic commit of the corresponding world chunk.
            // On a new JVM, reconcile the current chunk instead of skipping its possibly unsaved blocks.
            if(job.kind!=Kind.CAPTURE&&job.cursor<job.count())job.blockCursor=0;
            return job;
        }catch(RuntimeException invalid){throw new IOException("WORLD_HISTORY_MANIFEST_INVALID",invalid);}
    }
    private void persist(Job job)throws IOException{write(directory(job.id).resolve("manifest.nbt"),manifest(job));}
    UUID accept(ServerLevel level,BlockPos first,BlockPos second,boolean regenerate)throws IOException{
        BlockPos low=new BlockPos(Math.min(first.getX(),second.getX()),Math.min(first.getY(),second.getY()),Math.min(first.getZ(),second.getZ()));
        BlockPos high=new BlockPos(Math.max(first.getX(),second.getX()),Math.max(first.getY(),second.getY()),Math.max(first.getZ(),second.getZ()));
        if(low.getY()<level.getMinBuildHeight()||high.getY()>=level.getMaxBuildHeight()||!Level.isInSpawnableBounds(low)||!Level.isInSpawnableBounds(high))throw new IllegalArgumentException("WORLD_HISTORY_BOUNDS_INVALID");
        Job job=new Job(UUID.randomUUID(),null,regenerate?Kind.REGENERATE:Kind.CAPTURE,level.dimension(),low,high,level.getSeed());job.count();
        if(regenerate){job.conditions=SeedChunks.conditions(level);CompoundTag saved=new CompoundTag();saved.putInt("Format",1);saved.putUUID("Realm",journal.realm);saved.put("Conditions",job.conditions);write(directory(job.id).resolve("generation.nbt"),saved);}
        return accept(job,level,"WORLD/1\tACCEPT\t"+job.id+"\t"+job.kind+"\t"+job.dimension.location());
    }
    UUID restore(UUID source)throws IOException{
        Job snapshot=jobs.get(source);if(snapshot==null||snapshot.kind==Kind.RESTORE||!snapshot.state.equals("COMPLETE"))throw new IllegalArgumentException("WORLD_HISTORY_SNAPSHOT_NOT_COMPLETE");
        ServerLevel level=server.getLevel(snapshot.dimension);if(level==null)throw new IllegalStateException("WORLD_DIMENSION_UNAVAILABLE");
        Job job=new Job(UUID.randomUUID(),source,Kind.RESTORE,snapshot.dimension,snapshot.low,snapshot.high,snapshot.seed);
        return accept(job,level,"WORLD/1\tACCEPT\t"+job.id+"\tRESTORE\t"+source);
    }
    private UUID accept(Job job,ServerLevel level,String record)throws IOException{
        // The index can name an ACCEPTING job, but it cannot execute before its actual writer ACK.
        job.stopped=true;job.state="ACCEPTING";persist(job);
        try{
            index(job.id);
            job.acceptance=journal.append(record);
            publishAcceptance(job,level); // Pending ACK: retain only the stopped, queryable job.
        }catch(IOException|RuntimeException failure){
            job.stopped=true;job.state="FAILED";job.reason="ACCEPTANCE_NOT_PUBLISHED";
            try{unfreeze(job);}catch(RuntimeException cleanup){failure.addSuppressed(cleanup);}
            try{persist(job);}catch(IOException|RuntimeException cleanup){failure.addSuppressed(cleanup);}
            try{index(null);}catch(IOException|RuntimeException cleanup){failure.addSuppressed(cleanup);}
            throw failure;
        }
        jobs.put(job.id,job);return job.id;
    }
    private boolean publishAcceptance(Job job,ServerLevel level)throws IOException{
        if(job.acceptance==null)throw new IOException("WORLD_ACCEPTANCE_ACK_MISSING");
        if(!job.acceptance.isDone())return false;
        job.acceptance.join(); // Nonblocking here; failed/cancelled admission must not publish world work.
        runtime.archiveFreeze(this,job.id,level,job.low,job.high,true);
        job.stopped=false;job.state="QUEUED";persist(job);return true;
    }
    private static String acceptanceRecord(Job job){
        return "WORLD/1\tACCEPT\t"+job.id+"\t"+job.kind+"\t"+(job.kind==Kind.RESTORE?job.source:job.dimension.location());
    }
    boolean cancel(UUID id)throws IOException{Job job=jobs.get(id);if(job==null||job.state.equals("COMPLETE"))return false;job.stopped=true;job.state="CANCELLED";job.reason="ALREADY_APPLIED_EFFECTS_RETAINED";release(job);unfreeze(job);persist(job);return true;}
    boolean retry(UUID id)throws IOException{
        Job job=jobs.get(id);if(job==null||!job.state.equals("FAILED"))return false;
        if(job.acceptance==null){
            if(!journal.recorded(acceptanceRecord(job))){job.reason="ACCEPTANCE_RECORD_UNCONFIRMED";return false;}
            job.acceptance=CompletableFuture.completedFuture(null);
        }
        job.acceptance=journal.retryRejected(acceptanceRecord(job),job.acceptance);
        boolean accepting=!job.acceptance.isDone();
        if(!accepting&&(job.acceptance.isCompletedExceptionally()||job.acceptance.isCancelled())){
            job.reason="ACCEPTANCE_ACK_UNCONFIRMED";return false;
        }
        if(job.generation!=null&&job.image==null&&job.generation.canRestart())job.generation=null;
        // Otherwise resume the SAME owner: its queued continuations may be needed to finish
        // the outstanding future. Refusing to schedule it again would make that future permanent.
        if(job.lighting!=null&&(job.lighting.isCompletedExceptionally()||job.lighting.isCancelled()))job.lighting=null;
        if(job.receipt!=null&&(job.receipt.isCompletedExceptionally()||job.receipt.isCancelled())){
            String record=completionRecord(job);
            CompletableFuture<Void> retried=journal.retryRejected(record,job.receipt);
            if(retried==job.receipt){job.reason="DURABLE_WRITER_OR_COMMIT_UNAVAILABLE";return false;}
            job.receipt=retried;
        }
        String reason=job.reason;job.state=accepting?"ACCEPTING":"QUEUED";job.stopped=accepting;job.reason="";
        try{persist(job);}catch(IOException|RuntimeException failure){job.state="FAILED";job.stopped=true;job.reason=reason;throw failure;}
        return true;
    }
    String state(UUID id){Job job=jobs.get(id);return job==null?"未找到世界工作。":"WORLD="+job.id+";ACTION="+job.kind+";STATE="+job.state+";CHUNKS="+job.cursor+"/"+job.count()+";BLOCK_CURSOR="+job.blockCursor+";WORLD_SAVE="+(job.kind==Kind.CAPTURE?"NOT_CHANGED":job.vanillaFlush?"COMPLETED_CHUNKS_VANILLA_FLUSHED":"LEGACY_WORLD_SAVE_UNCONFIRMED")+";RESTART=UNVERIFIED;REASON="+job.reason;}
    List<WeaponEntry> weaponJobs(){List<WeaponEntry> result=new ArrayList<>();for(Job job:jobs.values())result.add(new WeaponEntry(job.id,(job.kind==Kind.CAPTURE?"快照":job.kind==Kind.REGENERATE?"种子重生成":"回溯")+" · "+job.dimension.location()+" ["+job.minX()+", "+job.minZ()+"]",job.state+" · "+job.cursor+"/"+job.count()));Collections.reverse(result);return List.copyOf(result);}
    void tick(){
        if(jobs.isEmpty())return;List<Job> order=new ArrayList<>(jobs.values());
        for(int i=0;i<order.size();i++){Job job=order.get(next++%order.size());if(next<0)next=0;if(job.stopped&&!job.state.equals("ACCEPTING")||job.state.equals("COMPLETE"))continue;
            try{advance(job);}catch(IOException|RuntimeException failure){
                job.state="FAILED";job.reason=failure.getClass().getSimpleName()+":"+String.valueOf(failure.getMessage());job.stopped=true;
                try{release(job);}catch(RuntimeException cleanup){job.reason+=";TICKET_RELEASE_PENDING";failure.addSuppressed(cleanup);}
                try{unfreeze(job);}catch(RuntimeException cleanup){job.reason+=";AREA_RELEASE_PENDING";failure.addSuppressed(cleanup);}
                try{persist(job);}catch(IOException|RuntimeException failedPersistence){job.reason+=";MANIFEST_WRITE_FAILED";}
            }break;
        }
    }
    private void advance(Job job)throws IOException{
        ServerLevel level=server.getLevel(job.dimension);if(level==null)throw new IllegalStateException("WORLD_DIMENSION_UNAVAILABLE");
        if(job.state.equals("ACCEPTING")){if(!publishAcceptance(job,level))return;}
        else runtime.archiveFreeze(this,job.id,level,job.low,job.high,true);
        if(job.cursor==job.count()){
            String record=completionRecord(job);
            if(job.receipt==null){
                job.receipt=journal.recorded(record)?CompletableFuture.completedFuture(null):journal.append(record);job.state="COMMIT_PENDING";persist(job);
            }else job.receipt=journal.retryRejected(record,job.receipt);
            if(job.receipt.isDone()){job.receipt.join();job.state="COMPLETE";persist(job);unfreeze(job);}return;
        }
        ChunkPos position=job.position();
        if(job.ticket==null){job.ticket=position;level.getChunkSource().addRegionTicket(TICKET,position,0,job.id);job.loading=level.getChunkSource().getChunkFuture(position.x,position.z,ChunkStatus.FULL,true);job.state="LOADING";}
        if(!job.loading.isDone())return;
        ChunkAccess loaded=job.loading.join().left().orElseThrow(()->new IllegalStateException("WORLD_SELECTED_CHUNK_LOAD_FAILED"));
        if(!(loaded instanceof LevelChunk live)||!live.getPos().equals(position))throw new IllegalStateException("WORLD_SELECTED_CHUNK_IDENTITY_MISMATCH");
        if(job.kind==Kind.CAPTURE){
            if(!job.captureWritten){if(job.image==null)job.image=capture(job,live);write(image(job.id,position),job.image);job.captureWritten=true;}
            finishChunk(job);return;
        }
        if(job.applyStep.ordinal()<ApplyStep.SAVING.ordinal()&&runtime.archiveMutationLocked(this,level,lower(job,position),upper(job,position))){
            job.state="WORLD_REGION_WRITE_LOCKED";return;
        }
        if(job.kind==Kind.REGENERATE){
            if(level.getSeed()!=job.seed)throw new IllegalStateException("WORLD_HISTORY_SEED_CHANGED");
            if(!Files.exists(image(job.id,position)))write(image(job.id,position),capture(job,live));
            prepareApplication(job,level,live);
            if(job.image==null){
                if(job.generation==null){
                    requireGenerationConditions(job,level);
                    if(Files.exists(generated(job,position)))job.image=read(generated(job,position));
                    else{job.generation=new SeedChunks(level,position);runtime.archiveControls(this,job.generation.controlObjects());job.state="GENERATING";}
                }
                if(job.image==null){job.generation.advance();if(!job.generation.ready())return;requireGenerationConditions(job,level);
                    job.image=capture(job,job.generation.result());write(generated(job,position),job.image);
                }
            }
        }else{prepareApplication(job,level,live);if(job.image==null)job.image=read(image(job.source,position));}
        apply(job,level,live);
    }
    private static void requireGenerationConditions(Job job,ServerLevel level){
        if(job.conditions==null||job.conditions.isEmpty())throw new IllegalStateException("WORLD_GENERATION_CONDITIONS_NOT_RECORDED");
        if(!job.conditions.equals(SeedChunks.conditions(level)))throw new IllegalStateException("WORLD_GENERATION_CONDITIONS_CHANGED");
    }
    private void prepareApplication(Job job,ServerLevel level,LevelChunk chunk)throws IOException{
        if(job.expected!=null)return;Path path=application(job,chunk.getPos());
        if(Files.exists(path)){
            CompoundTag saved=read(path);if(saved.getInt("Format")!=1||!saved.hasUUID("Realm")||!saved.getUUID("Realm").equals(journal.realm)||saved.getLong("Chunk")!=chunk.getPos().toLong())throw new IOException("WORLD_APPLICATION_SCOPE_MISMATCH");
            job.expected=saved.getCompound("Expected");job.tickBase=saved.getLong("TickBase");job.blockTicksStarted=saved.getBoolean("BlockTicksStarted");job.fluidTicksStarted=saved.getBoolean("FluidTicksStarted");
        }else{
            job.expected=job.kind==Kind.REGENERATE?read(image(job.id,chunk.getPos())):capture(job,chunk);
            persistApplication(job,chunk.getPos());
        }
        validateFrame(job,level,chunk,job.expected);job.tileOwners.clear();
        for(BlockPos pos:chunk.getBlockEntitiesPos())if(job.contains(pos)){BlockEntity owner=chunk.getBlockEntity(pos);if(owner!=null)job.tileOwners.put(pos,owner);}
    }
    private void persistApplication(Job job,ChunkPos position)throws IOException{
        CompoundTag saved=new CompoundTag();saved.putInt("Format",1);saved.putUUID("Realm",journal.realm);saved.putLong("Chunk",position.toLong());saved.put("Expected",job.expected);
        saved.putLong("TickBase",job.tickBase);saved.putBoolean("BlockTicksStarted",job.blockTicksStarted);saved.putBoolean("FluidTicksStarted",job.fluidTicksStarted);write(application(job,position),saved);
    }
    private static BlockPos lower(Job job,ChunkPos pos){return new BlockPos(Math.max(job.low.getX(),pos.getMinBlockX()),job.low.getY(),Math.max(job.low.getZ(),pos.getMinBlockZ()));}
    private static BlockPos upper(Job job,ChunkPos pos){return new BlockPos(Math.min(job.high.getX(),pos.getMaxBlockX()),job.high.getY(),Math.min(job.high.getZ(),pos.getMaxBlockZ()));}
    private CompoundTag capture(Job job,ChunkAccess chunk){
        ServerLevel level=server.getLevel(job.dimension);BlockPos low=lower(job,chunk.getPos()),high=upper(job,chunk.getPos());CompoundTag tag=new CompoundTag();tag.putInt("Format",1);tag.putUUID("Realm",journal.realm);tag.putLong("Chunk",chunk.getPos().toLong());
        tag.put("Low",NbtUtils.writeBlockPos(low));tag.put("High",NbtUtils.writeBlockPos(high));tag.putLong("GameTime",level.getGameTime());tag.putInt("DataVersion",net.minecraft.SharedConstants.getCurrentVersion().getDataVersion().getVersion());tag.putString("Dimension",job.dimension.location().toString());
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
        tag.putBoolean("FullChunk",full);if(full)captureMetadata(level,chunk,low,high,tag);return tag;
    }
    private static void captureMetadata(ServerLevel level,ChunkAccess chunk,BlockPos low,BlockPos high,CompoundTag tag){
            var registry=level.registryAccess().registryOrThrow(Registries.STRUCTURE);
            ListTag starts=new ListTag();chunk.getAllStarts().entrySet().stream().sorted(Comparator.comparing(entry->registry.getKey(entry.getKey()).toString())).forEach(entry->{CompoundTag saved=entry.getValue().createTag(StructurePieceSerializationContext.fromLevel(level),chunk.getPos());saved.putString("RonovaStructure",registry.getKey(entry.getKey()).toString());starts.add(saved);});tag.put("Starts",starts);
            ListTag references=new ListTag();chunk.getAllReferences().entrySet().stream().sorted(Comparator.comparing(entry->registry.getKey(entry.getKey()).toString())).forEach(entry->{CompoundTag saved=new CompoundTag();saved.putString("Type",registry.getKey(entry.getKey()).toString());long[] refs=entry.getValue().toLongArray();Arrays.sort(refs);saved.putLongArray("References",refs);references.add(saved);});tag.put("References",references);
            ListTag biomes=new ListTag();for(int y=QuartPos.fromBlock(low.getY());y<=QuartPos.fromBlock(high.getY());y++)for(int z=0;z<4;z++)for(int x=0;x<4;x++)biomes.add(StringTag.valueOf(chunk.getNoiseBiome(QuartPos.fromBlock(low.getX())+x,y,QuartPos.fromBlock(low.getZ())+z).unwrapKey().orElseThrow().location().toString()));tag.put("Biomes",biomes);
    }
    private static boolean inside(Job job,CompoundTag data){ListTag position=data.getList("Pos",Tag.TAG_DOUBLE);return position.size()==3&&job.contains(BlockPos.containing(position.getDouble(0),position.getDouble(1),position.getDouble(2)));}
    private static <T> ListTag ticks(TickContainerAccess<T> container,Job job,long now,Registry<T> registry){
        ListTag result=new ListTag();
        if(container instanceof LevelChunkTicks<T> live)live.getAll().filter(tick->job.contains(tick.pos())).forEach(tick->{CompoundTag saved=new CompoundTag();saved.putString("Type",registry.getKey(tick.type()).toString());saved.put("Position",NbtUtils.writeBlockPos(tick.pos()));saved.putLong("Delay",tick.triggerTick()-now);saved.putInt("Priority",tick.priority().getValue());saved.putLong("Order",tick.subTickOrder());result.add(saved);});
        else if(container instanceof ProtoChunkTicks<T> proto){
            for(SavedTick<T> tick:proto.scheduledTicks())if(job.contains(tick.pos())){CompoundTag saved=new CompoundTag();saved.putString("Type",registry.getKey(tick.type()).toString());saved.put("Position",NbtUtils.writeBlockPos(tick.pos()));saved.putLong("Delay",tick.delay());saved.putInt("Priority",tick.priority().getValue());saved.putLong("Order",result.size());result.add(saved);}
        }else throw new IllegalStateException("WORLD_TICK_CONTAINER_UNAVAILABLE");return result;
    }
    private static <T> void appendHeld(ListTag target,List<ScheduledTick<?>> held,long now,Registry<T> registry){
        for(ScheduledTick<?> tick:held){@SuppressWarnings("unchecked") T type=(T)tick.type();CompoundTag tag=new CompoundTag();tag.putString("Type",registry.getKey(type).toString());tag.put("Position",NbtUtils.writeBlockPos(tick.pos()));tag.putLong("Delay",tick.triggerTick()-now);tag.putInt("Priority",tick.priority().getValue());tag.putLong("Order",tick.subTickOrder());target.add(tag);}
    }
    private void validateFrame(Job job,ServerLevel level,ChunkAccess chunk,CompoundTag frame)throws IOException{
        BlockPos low=lower(job,chunk.getPos()),high=upper(job,chunk.getPos());
        if(frame.getInt("Format")!=1||!frame.hasUUID("Realm")||!frame.getUUID("Realm").equals(journal.realm)||frame.getLong("Chunk")!=chunk.getPos().toLong()
                ||!NbtUtils.readBlockPos(frame.getCompound("Low")).equals(low)||!NbtUtils.readBlockPos(frame.getCompound("High")).equals(high)
                ||!frame.getString("Dimension").equals(job.dimension.location().toString()))throw new IOException("WORLD_HISTORY_IMAGE_SCOPE_MISMATCH");
        if(!frame.contains("DataVersion",Tag.TAG_INT)||frame.getInt("DataVersion")!=net.minecraft.SharedConstants.getCurrentVersion().getDataVersion().getVersion())throw new IOException("WORLD_HISTORY_IMAGE_VERSION_UNAVAILABLE_OR_CHANGED");
        boolean full=low.getX()==chunk.getPos().getMinBlockX()&&high.getX()==chunk.getPos().getMaxBlockX()&&low.getZ()==chunk.getPos().getMinBlockZ()&&high.getZ()==chunk.getPos().getMaxBlockZ()&&low.getY()==level.getMinBuildHeight()&&high.getY()==level.getMaxBuildHeight()-1;
        if(frame.getBoolean("FullChunk")!=full)throw new IOException("WORLD_HISTORY_METADATA_SCOPE_MISMATCH");
    }
    private static List<BlockState> palette(ServerLevel level,CompoundTag frame)throws IOException{
        List<BlockState> result=new ArrayList<>();for(Tag value:frame.getList("Palette",Tag.TAG_COMPOUND)){
            CompoundTag saved=(CompoundTag)value;BlockState state=NbtUtils.readBlockState(level.holderLookup(Registries.BLOCK),saved);
            if(!NbtUtils.writeBlockState(state).equals(saved))throw new IOException("WORLD_HISTORY_BLOCK_TYPE_OR_PROPERTIES_UNAVAILABLE");result.add(state);
        }return result;
    }
    private static Map<BlockPos,CompoundTag> tileImages(Job job,CompoundTag frame)throws IOException{
        Map<BlockPos,CompoundTag> result=new LinkedHashMap<>();for(Tag value:frame.getList("Tiles",Tag.TAG_COMPOUND)){
            CompoundTag saved=(CompoundTag)value;BlockPos pos=new BlockPos(saved.getInt("x"),saved.getInt("y"),saved.getInt("z"));
            if(!job.contains(pos)||ChunkPos.asLong(pos)!=job.position().toLong()||result.put(pos,saved)!=null)throw new IOException("WORLD_TILE_IMAGE_SCOPE_OR_KEY_INVALID");
        }return result;
    }
    private static CompoundTag currentTile(LevelChunk chunk,BlockPos pos){CompoundTag saved=chunk.getBlockEntityNbtForSaving(pos);return saved==null?null:saved.copy();}
    private static void requireTileVersion(Job job,LevelChunk chunk,BlockPos pos,CompoundTag expected){
        BlockEntity current=chunk.getBlockEntity(pos);
        if(!Objects.equals(currentTile(chunk,pos),expected)||job.tileOwners.containsKey(pos)&&job.tileOwners.get(pos)!=current||!job.tileOwners.containsKey(pos)&&current!=null)
            throw new IllegalStateException("WORLD_HISTORY_LATER_TILE_CHANGE:"+pos);
    }
    private static void updateTileBaseline(Job job,LevelChunk chunk,BlockPos pos,Map<BlockPos,CompoundTag> expected){
        CompoundTag saved=currentTile(chunk,pos);if(saved==null)expected.remove(pos);else expected.put(pos,saved);
        BlockEntity current=chunk.getBlockEntity(pos);if(current==null)job.tileOwners.remove(pos);else job.tileOwners.put(pos,current);
    }
    private static void saveTileBaseline(Job job,Map<BlockPos,CompoundTag> expected){ListTag saved=new ListTag();expected.values().forEach(saved::add);job.expected.put("Tiles",saved);}
    private void apply(Job job,ServerLevel level,LevelChunk chunk)throws IOException{
        if(job.applyingChunk!=chunk){
            // A reloaded chunk is a different live owner. Do not reuse the old owner's progress.
            if(job.applyingChunk!=null&&job.lighting!=null&&!job.lighting.isDone()){job.state="LIGHTING";return;}
            boolean replaced=job.applyingChunk!=null;resetApply(job);job.applyingChunk=chunk;
            if(replaced){job.tileOwners.clear();for(BlockPos pos:chunk.getBlockEntitiesPos())if(job.contains(pos)){BlockEntity tile=chunk.getBlockEntity(pos);if(tile!=null)job.tileOwners.put(pos,tile);}}
        }
        CompoundTag frame=job.image;BlockPos low=lower(job,chunk.getPos()),high=upper(job,chunk.getPos());
        validateFrame(job,level,chunk,frame);validateFrame(job,level,chunk,job.expected);
        int[] cells=frame.getIntArray("Cells");int width=high.getX()-low.getX()+1,depth=high.getZ()-low.getZ()+1,height=high.getY()-low.getY()+1;
        if(cells.length!=Math.multiplyExact(Math.multiplyExact(width,depth),height)||job.blockCursor>cells.length)throw new IOException("WORLD_HISTORY_IMAGE_CELLS_INVALID");
        int[] previous=job.expected.getIntArray("Cells");if(previous.length!=cells.length)throw new IOException("WORLD_HISTORY_BASELINE_CELLS_INVALID");
        List<BlockState> palette=palette(level,frame),priorPalette=palette(level,job.expected);
        Map<BlockPos,CompoundTag> expectedTiles=tileImages(job,job.expected),desiredTiles=tileImages(job,frame);
        if(job.applyStep==ApplyStep.BLOCKS){
            job.state="APPLYING";for(int budget=0;job.blockCursor<cells.length&&budget<4096;budget++,job.blockCursor++){
                int index=job.blockCursor,pal=cells[index];if(pal<0||pal>=palette.size())throw new IOException("WORLD_HISTORY_PALETTE_INDEX_INVALID");
                BlockPos pos=new BlockPos(low.getX()+index%width,low.getY()+(index/width)%height,low.getZ()+index/(width*height));BlockState desired=palette.get(pal);
                if(previous[index]<0||previous[index]>=priorPalette.size())throw new IOException("WORLD_HISTORY_BASELINE_PALETTE_INDEX_INVALID");
                if(chunk.getBlockState(pos)!=desired){
                    if(chunk.getBlockState(pos)!=priorPalette.get(previous[index]))throw new IllegalStateException("WORLD_HISTORY_LATER_BLOCK_CHANGE:"+pos);
                    requireTileVersion(job,chunk,pos,expectedTiles.get(pos));
                    level.setBlock(pos,desired,Block.UPDATE_CLIENTS|Block.UPDATE_KNOWN_SHAPE|Block.UPDATE_SUPPRESS_DROPS);
                    if(chunk.getBlockState(pos)!=desired)throw new IllegalStateException("WORLD_BLOCK_RESTORE_REFUSED:"+pos);
                    updateTileBaseline(job,chunk,pos,expectedTiles);
                }
            }
            saveTileBaseline(job,expectedTiles);persistApplication(job,chunk.getPos());
            if(job.blockCursor==cells.length)job.applyStep=ApplyStep.TILES;
            persist(job);if(job.applyStep==ApplyStep.BLOCKS)return;
        }
        if(job.applyStep==ApplyStep.TILES){
            ListTag tiles=frame.getList("Tiles",Tag.TAG_COMPOUND);job.state="RESTORING_TILES";
            while(job.tileCursor<tiles.size()){
                CompoundTag saved=tiles.getCompound(job.tileCursor);BlockPos pos=new BlockPos(saved.getInt("x"),saved.getInt("y"),saved.getInt("z"));
                if(!job.contains(pos)||ChunkPos.asLong(pos)!=chunk.getPos().toLong())throw new IOException("WORLD_TILE_IMAGE_OUTSIDE_SCOPE");
                if(Objects.equals(currentTile(chunk,pos),saved)){job.pendingTile=null;job.tileCursor++;continue;}
                requireTileVersion(job,chunk,pos,expectedTiles.get(pos));
                if(job.pendingTile==null)job.pendingTile=BlockEntity.loadStatic(pos,chunk.getBlockState(pos),saved.copy());
                BlockEntity restored=job.pendingTile;if(restored==null)throw new IllegalStateException("WORLD_TILE_TYPE_UNAVAILABLE:"+pos);
                if(restored.getBlockState()!=chunk.getBlockState(pos))throw new IllegalStateException("WORLD_TILE_STATE_CHANGED:"+pos);
                if(chunk.getBlockEntity(pos)!=restored){chunk.removeBlockEntity(pos);chunk.setBlockEntity(restored);}
                restored.setChanged();if(chunk.getBlockEntity(pos)!=restored||!saved.equals(currentTile(chunk,pos)))throw new IllegalStateException("WORLD_TILE_RESTORE_REFUSED:"+pos);
                updateTileBaseline(job,chunk,pos,expectedTiles);
                job.pendingTile=null;job.tileCursor++;
            }
            Set<BlockPos> wantedTiles=new HashSet<>();for(Tag value:tiles){CompoundTag saved=(CompoundTag)value;wantedTiles.add(new BlockPos(saved.getInt("x"),saved.getInt("y"),saved.getInt("z")));}
            for(BlockPos pos:List.copyOf(chunk.getBlockEntitiesPos()))if(job.contains(pos)&&!wantedTiles.contains(pos)){requireTileVersion(job,chunk,pos,expectedTiles.get(pos));chunk.removeBlockEntity(pos);updateTileBaseline(job,chunk,pos,expectedTiles);}
            saveTileBaseline(job,expectedTiles);persistApplication(job,chunk.getPos());
            job.applyStep=ApplyStep.TICKS;
        }
        if(job.applyStep==ApplyStep.TICKS){
            job.state="RESTORING_TICKS";BoundingBox selection=new BoundingBox(low.getX(),low.getY(),low.getZ(),high.getX(),high.getY(),high.getZ());
            if(job.tickBase==Long.MIN_VALUE){job.tickBase=level.getGameTime();persistApplication(job,chunk.getPos());}
            ListTag blockTicks=frame.getList("BlockTicks",Tag.TAG_COMPOUND),fluidTicks=frame.getList("FluidTicks",Tag.TAG_COMPOUND);
            if(!job.blockTicksCleared){
                if(ticksAlreadyApplied(job,level,chunk,false,blockTicks)){job.blockTickCursor=blockTicks.size();job.blockTicksCleared=true;}
                else{job.blockTicksStarted=true;persistApplication(job,chunk.getPos());level.getBlockTicks().clearArea(selection);runtime.archiveHeldTicks(this,level,low,high,false,true);job.blockTicksCleared=true;}
            }
            if(!job.fluidTicksCleared){
                if(ticksAlreadyApplied(job,level,chunk,true,fluidTicks)){job.fluidTickCursor=fluidTicks.size();job.fluidTicksCleared=true;}
                else{job.fluidTicksStarted=true;persistApplication(job,chunk.getPos());level.getFluidTicks().clearArea(selection);runtime.archiveHeldTicks(this,level,low,high,true,true);job.fluidTicksCleared=true;}
            }
            while(job.blockTickCursor<blockTicks.size()){restoreTick(level.getBlockTicks(),blockTicks.getCompound(job.blockTickCursor),BuiltInRegistries.BLOCK,job.tickBase,job);job.blockTickCursor++;}
            while(job.fluidTickCursor<fluidTicks.size()){restoreTick(level.getFluidTicks(),fluidTicks.getCompound(job.fluidTickCursor),BuiltInRegistries.FLUID,job.tickBase,job);job.fluidTickCursor++;}
            job.applyStep=ApplyStep.ENTITIES;
        }
        if(job.applyStep==ApplyStep.ENTITIES){
            if(!runtime.archiveEntities(this,level,low,high,frame.getList("Entities",Tag.TAG_COMPOUND),job.expected.getList("Entities",Tag.TAG_COMPOUND))){job.state="ENTITY_RESTORE_PENDING";persist(job);return;}
            job.applyStep=ApplyStep.METADATA;
        }
        if(job.applyStep==ApplyStep.METADATA){
            if(frame.getBoolean("FullChunk")){
                CompoundTag current=new CompoundTag();captureMetadata(level,chunk,low,high,current);
                for(String part:List.of("Starts","References","Biomes"))if(!Objects.equals(current.get(part),job.expected.get(part))&&!Objects.equals(current.get(part),frame.get(part)))throw new IllegalStateException("WORLD_HISTORY_LATER_METADATA_CHANGE:"+part);
                if(!Objects.equals(current.get("Starts"),frame.get("Starts"))||!Objects.equals(current.get("References"),frame.get("References"))||!Objects.equals(current.get("Biomes"),frame.get("Biomes")))restoreMetadata(level,chunk,frame);
            }
            chunk.setUnsaved(true);job.applyStep=ApplyStep.LIGHTING;
        }
        if(job.applyStep==ApplyStep.LIGHTING){
            job.state="LIGHTING";
            if(job.lighting==null)job.lighting=level.getChunkSource().getLightEngine().initializeLight(chunk,false).thenCompose(actual->level.getChunkSource().getLightEngine().lightChunk(actual,false));
            if(!job.lighting.isDone())return;
            job.lighting.join();job.applyStep=ApplyStep.SAVING;job.state="SAVE_RESUMING_TICKS";unfreeze(job);persist(job);return;
        }
        if(job.applyStep==ApplyStep.SAVING){
            // Let the normal WorldControl.tick invocation put paused entries back into the
            // actual containers before vanilla serializes them. Another live freeze rule
            // may still own those entries; a world-save cursor cannot skip them.
            if(!runtime.archiveHeldTicks(this,level,low,high,false,false).isEmpty()||!runtime.archiveHeldTicks(this,level,low,high,true,false).isEmpty()){
                job.state="WORLD_SAVE_DEFERRED_TICKS_PENDING";unfreeze(job);persist(job);return;
            }
            if(level.noSave()){job.state="WORLD_SAVE_DISABLED_PENDING";persist(job);return;}
            job.state="SAVING";CompoundTag before=capture(job,chunk);
            level.getChunkSource().save(true);
            ((Access.Server)level).pro$manager().saveAll();
            if(level.players().stream().anyMatch(player->job.contains(((Access.EntityState)player).pro$blockPosition())))server.getPlayerList().saveAll();
            if(level.getChunkSource().getChunkNow(chunk.getPos().x,chunk.getPos().z)!=chunk)throw new IllegalStateException("WORLD_SAVED_CHUNK_OWNER_CHANGED");
            if(chunk.isUnsaved()){job.state="WORLD_SAVE_DIRTY_PENDING";persist(job);return;}
            CompoundTag after=capture(job,chunk);
            if(!sameFrame(job,before,after)){job.state="WORLD_SAVE_LATER_CHANGE_PENDING";persist(job);return;}
            job.applyStep=ApplyStep.PUBLISH;
        }
        if(job.applyStep==ApplyStep.PUBLISH){publish(level,chunk);finishChunk(job);}
    }
    private static boolean sameFrame(Job job,CompoundTag first,CompoundTag second)throws IOException{
        for(String part:List.of("BlockTicks","FluidTicks"))if(!absoluteTicks(job,first.getList(part,Tag.TAG_COMPOUND),first.getLong("GameTime")).equals(absoluteTicks(job,second.getList(part,Tag.TAG_COMPOUND),second.getLong("GameTime"))))return false;
        CompoundTag left=first.copy(),right=second.copy();for(String part:List.of("BlockTicks","FluidTicks","GameTime")){left.remove(part);right.remove(part);}return left.equals(right);
    }
    private static <T> void restoreTick(LevelTicks<T> container,CompoundTag tag,Registry<T> registry,long now,Job job)throws IOException{
        BlockPos pos=NbtUtils.readBlockPos(tag.getCompound("Position"));T type=registry.getOptional(new ResourceLocation(tag.getString("Type"))).orElseThrow(()->new IllegalStateException("WORLD_TICK_TYPE_UNAVAILABLE"));
        if(!job.contains(pos)||ChunkPos.asLong(pos)!=job.position().toLong())throw new IOException("WORLD_TICK_IMAGE_OUTSIDE_SCOPE");
        container.schedule(new ScheduledTick<>(type,pos,Math.addExact(now,tag.getLong("Delay")),TickPriority.byValue(tag.getInt("Priority")),tag.getLong("Order")));
    }
    private record TickKey(String type,BlockPos position){}
    private static Map<TickKey,CompoundTag> absoluteTicks(Job job,ListTag source,long base)throws IOException{
        Map<TickKey,CompoundTag> result=new HashMap<>();for(Tag value:source){
            CompoundTag saved=((CompoundTag)value).copy();BlockPos pos=NbtUtils.readBlockPos(saved.getCompound("Position"));
            if(!job.contains(pos)||ChunkPos.asLong(pos)!=job.position().toLong())throw new IOException("WORLD_TICK_IMAGE_OUTSIDE_SCOPE");
            TickKey key=new TickKey(saved.getString("Type"),pos);saved.putLong("Trigger",Math.addExact(base,saved.getLong("Delay")));saved.remove("Delay");
            CompoundTag prior=result.putIfAbsent(key,saved);if(prior!=null&&!prior.equals(saved))throw new IOException("WORLD_TICK_IMAGE_CONFLICTING_KEY");
        }return result;
    }
    private boolean ticksAlreadyApplied(Job job,ServerLevel level,LevelChunk chunk,boolean fluid,ListTag desired)throws IOException{
        long now=level.getGameTime();ListTag current=fluid?ticks(chunk.getFluidTicks(),job,now,BuiltInRegistries.FLUID):ticks(chunk.getBlockTicks(),job,now,BuiltInRegistries.BLOCK);
        if(fluid)appendHeld(current,runtime.archiveHeldTicks(this,level,lower(job,chunk.getPos()),upper(job,chunk.getPos()),true,false),now,BuiltInRegistries.FLUID);
        else appendHeld(current,runtime.archiveHeldTicks(this,level,lower(job,chunk.getPos()),upper(job,chunk.getPos()),false,false),now,BuiltInRegistries.BLOCK);
        Map<TickKey,CompoundTag> actual=absoluteTicks(job,current,now),wanted=absoluteTicks(job,desired,job.tickBase);
        if(actual.equals(wanted))return true;
        Map<TickKey,CompoundTag> expected=absoluteTicks(job,job.expected.getList(fluid?"FluidTicks":"BlockTicks",Tag.TAG_COMPOUND),job.expected.getLong("GameTime"));
        if(actual.equals(expected))return false;
        if(!(fluid?job.fluidTicksStarted:job.blockTicksStarted))throw new IllegalStateException("WORLD_HISTORY_LATER_TICK_CHANGE:"+(fluid?"fluid":"block"));
        // An interrupted clear/refill can leave a subset of this SAME request's entries.
        // A new key or changed time/priority/order stays with its later writer.
        for(var entry:actual.entrySet())if(!entry.getValue().equals(expected.get(entry.getKey()))&&!entry.getValue().equals(wanted.get(entry.getKey())))throw new IllegalStateException("WORLD_HISTORY_LATER_TICK_CHANGE:"+entry.getKey().position());
        return false;
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
    private static String completionRecord(Job job){return "WORLD/1\tCOMPLETE\t"+job.id+"\t"+job.kind+"\t"+job.count();}
    private void finishChunk(Job job)throws IOException{
        release(job);long cursor=job.cursor;int blockCursor=job.blockCursor;String state=job.state;
        job.cursor++;job.blockCursor=0;job.state="QUEUED";
        try{persist(job);}catch(IOException|RuntimeException failure){job.cursor=cursor;job.blockCursor=blockCursor;job.state=state;throw failure;}
        job.image=null;job.expected=null;job.generation=null;job.captureWritten=false;job.applyingChunk=null;job.tileOwners.clear();
        job.tickBase=Long.MIN_VALUE;job.blockTicksStarted=false;job.fluidTicksStarted=false;resetApply(job);
    }
    private static void resetApply(Job job){
        job.blockCursor=0;job.lighting=null;job.pendingTile=null;job.applyStep=ApplyStep.BLOCKS;
        job.tileCursor=0;job.blockTickCursor=0;job.fluidTickCursor=0;job.blockTicksCleared=false;job.fluidTicksCleared=false;
    }
    private void release(Job job){ServerLevel level=server.getLevel(job.dimension);if(job.ticket!=null&&level!=null)level.getChunkSource().removeRegionTicket(TICKET,job.ticket,0,job.id);job.ticket=null;job.loading=null;}
    private void unfreeze(Job job){ServerLevel level=server.getLevel(job.dimension);if(level!=null)runtime.archiveFreeze(this,job.id,level,job.low,job.high,false);}
    void close(){for(Job job:jobs.values()){release(job);unfreeze(job);}}
    Object[] controlObjects(){return new Object[]{this,jobs};}
}
