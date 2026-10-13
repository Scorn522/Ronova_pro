package dev.ronova.pro;

import java.util.*;
import java.util.concurrent.*;
import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Codec;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.*;
import net.minecraft.resources.*;
import net.minecraft.server.level.*;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.*;
import net.minecraft.world.level.levelgen.WorldOptions;

/** Seed generation uses private ProtoChunks, including dependencies; no halo is installed into the world. */
final class SeedChunks {
    private record Stage(long position,ChunkStatus status){}
    private static final class Frame {
        final Stage stage;List<Stage> dependencies;int next;
        Frame(Stage stage){this.stage=stage;}
    }
    private final ServerLevel level;
    private final long seed;
    private final ChunkGenerator generator;
    private final net.minecraft.core.RegistryAccess registry;
    private final WorldOptions options;
    private final WorldDataConfiguration data;
    private final Tag generatorImage;
    private final Map<Long,ChunkAccess> chunks=new LinkedHashMap<>();
    private final Deque<Frame> pending=new ArrayDeque<>();
    private final Queue<Runnable> execution=new ConcurrentLinkedQueue<>();
    private final java.util.concurrent.Executor executor=execution::add;
    private CompletableFuture<Either<ChunkAccess,ChunkHolder.ChunkLoadingFailure>> generation;
    private Frame generating;
    private final ChunkPos target;
    private boolean spawned;
    SeedChunks(ServerLevel level,ChunkPos target){
        this.level=level;this.target=target;seed=level.getSeed();generator=level.getChunkSource().getGenerator();registry=level.registryAccess();
        options=level.getServer().getWorldData().worldGenOptions();data=level.getServer().getWorldData().getDataConfiguration();generatorImage=encode(ChunkGenerator.CODEC,generator,RegistryOps.create(NbtOps.INSTANCE,registry));
        pending.push(new Frame(new Stage(target.toLong(),ChunkStatus.FEATURES)));
    }
    /** Actual serialized generator inputs, retained with the job rather than inferred from its seed. */
    static CompoundTag conditions(ServerLevel level){
        RegistryOps<Tag> ops=RegistryOps.create(NbtOps.INSTANCE,level.registryAccess());CompoundTag result=new CompoundTag();
        result.putInt("DataVersion",net.minecraft.SharedConstants.getCurrentVersion().getDataVersion().getVersion());
        result.putString("GameVersion",net.minecraft.SharedConstants.getCurrentVersion().getId());result.putString("ForgeVersion",net.minecraftforge.versions.forge.ForgeVersion.getVersion());
        result.putString("Dimension",level.dimension().location().toString());result.putInt("MinY",level.getMinBuildHeight());result.putInt("Height",level.getHeight());result.putLong("Seed",level.getSeed());
        result.put("Generator",encode(ChunkGenerator.CODEC,level.getChunkSource().getGenerator(),ops));
        result.put("Options",encode(WorldOptions.CODEC.codec(),level.getServer().getWorldData().worldGenOptions(),ops));
        result.put("DataConfiguration",encode(WorldDataConfiguration.CODEC,level.getServer().getWorldData().getDataConfiguration(),ops));
        CompoundTag definitions=new CompoundTag();for(RegistryDataLoader.RegistryData<?> definition:RegistryDataLoader.WORLDGEN_REGISTRIES)definition(level,definition,ops,definitions);result.put("Registries",definitions);
        ListTag mods=new ListTag();net.minecraftforge.fml.ModList.get().getMods().stream().sorted(Comparator.comparing(net.minecraftforge.forgespi.language.IModInfo::getModId)).forEach(mod->{
            CompoundTag entry=new CompoundTag();entry.putString("Id",mod.getModId());entry.putString("Version",mod.getVersion().toString());mods.add(entry);
        });result.put("ModVersions",mods);return result;
    }
    private static <T> Tag encode(Codec<T> codec,T value,RegistryOps<Tag> ops){
        var encoded=codec.encodeStart(ops,value);return encoded.result().orElseThrow(()->new IllegalStateException("WORLD_GENERATION_CONDITIONS_UNAVAILABLE:"+encoded.error().map(error->error.message()).orElse("unknown")));
    }
    private static <T> void definition(ServerLevel level,RegistryDataLoader.RegistryData<T> definition,RegistryOps<Tag> ops,CompoundTag result){
        Registry<T> registry=level.registryAccess().registryOrThrow(definition.key());CompoundTag saved=new CompoundTag(),entries=new CompoundTag(),tags=new CompoundTag();
        registry.entrySet().stream().sorted(Comparator.comparing(entry->entry.getKey().location().toString())).forEach(entry->entries.put(entry.getKey().location().toString(),encode(definition.elementCodec(),entry.getValue(),ops)));
        registry.getTags().forEach(tag->{List<String> names=new ArrayList<>();for(var holder:tag.getSecond())names.add(holder.unwrapKey().orElseThrow(()->new IllegalStateException("WORLD_GENERATION_TAG_ENTRY_UNREGISTERED")).location().toString());
            Collections.sort(names);ListTag values=new ListTag();for(String name:names)values.add(StringTag.valueOf(name));tags.put(tag.getFirst().location().toString(),values);
        });saved.put("Entries",entries);saved.put("Tags",tags);result.put(definition.key().location().toString(),saved);
    }
    private ChunkAccess chunk(long position){return chunks.computeIfAbsent(position,key->new ProtoChunk(new ChunkPos(key),UpgradeData.EMPTY,level,level.registryAccess().registryOrThrow(Registries.BIOME),null));}
    private static ChunkStatus dependency(ChunkStatus status,int distance){return distance==0?status.getParent():ChunkStatus.getStatusAroundFullChunk(ChunkStatus.getDistance(status)+distance);}
    private List<Stage> dependencies(Stage stage){
        int radius=Math.max(0,stage.status().getRange());ChunkPos center=new ChunkPos(stage.position());List<Stage> result=new ArrayList<>();
        for(int z=-radius;z<=radius;z++)for(int x=-radius;x<=radius;x++)result.add(new Stage(ChunkPos.asLong(center.x+x,center.z+z),dependency(stage.status(),Math.max(Math.abs(x),Math.abs(z)))));
        return result;
    }
    private List<ChunkAccess> neighborhood(Stage stage){
        int radius=Math.max(0,stage.status().getRange());ChunkPos center=new ChunkPos(stage.position());List<ChunkAccess> result=new ArrayList<>();
        for(int z=-radius;z<=radius;z++)for(int x=-radius;x<=radius;x++)result.add(chunk(ChunkPos.asLong(center.x+x,center.z+z)));return result;
    }
    void advance(){
        if(level.getSeed()!=seed||level.getChunkSource().getGenerator()!=generator||level.registryAccess()!=registry
                ||level.getServer().getWorldData().worldGenOptions()!=options||!level.getServer().getWorldData().getDataConfiguration().equals(data)
                ||!generatorImage.equals(encode(ChunkGenerator.CODEC,generator,RegistryOps.create(NbtOps.INSTANCE,registry))))throw new IllegalStateException("WORLD_GENERATOR_CHANGED_DURING_REGENERATION");
        for(int work=0;work<32;work++){
            Runnable action=execution.poll();if(action!=null){action.run();continue;}
            if(generation!=null){
                if(!generation.isDone())return;
                Either<ChunkAccess,ChunkHolder.ChunkLoadingFailure> produced=generation.join();ChunkAccess actual=produced.left().orElseThrow(()->new IllegalStateException("SEED_GENERATION_FAILED:"+produced.right().orElse(null)));
                if(actual.getPos().toLong()!=generating.stage.position())throw new IllegalStateException("SEED_GENERATION_WRONG_CHUNK");
                chunks.put(generating.stage.position(),actual);generation=null;generating=null;pending.pop();continue;
            }
            if(pending.isEmpty()){
                if(!spawned){ChunkAccess actual=chunk(target.toLong());generator.spawnOriginalMobs(new WorldGenRegion(level,List.of(actual),ChunkStatus.SPAWN,-1));spawned=true;}return;
            }
            Frame frame=pending.peek();ChunkAccess actual=chunk(frame.stage.position());
            if(actual.getStatus().isOrAfter(frame.stage.status())){pending.pop();continue;}
            if(frame.dependencies==null)frame.dependencies=dependencies(frame.stage);
            boolean waiting=false;
            while(frame.next<frame.dependencies.size()){
                Stage needed=frame.dependencies.get(frame.next++);ChunkAccess neighbor=chunk(needed.position());
                if(!neighbor.getStatus().isOrAfter(needed.status())){pending.push(new Frame(needed));waiting=true;break;}
            }
            if(waiting)continue;
            if(frame.stage.status()==ChunkStatus.STRUCTURE_STARTS){
                // ChunkStatus's live-world notification is deliberately deferred until the selected chunk is committed.
                if(level.getServer().getWorldData().worldGenOptions().generateStructures())generator.createStructures(level.registryAccess(),level.getChunkSource().getGeneratorState(),level.structureManager(),actual,level.getStructureManager());
                ((ProtoChunk)actual).setStatus(ChunkStatus.STRUCTURE_STARTS);pending.pop();continue;
            }
            generating=frame;generation=frame.stage.status().generate(executor,level,generator,level.getStructureManager(),level.getChunkSource().getLightEngine(),
                    ignored->{throw new IllegalStateException("PRIVATE_GENERATION_CANNOT_PUBLISH_FULL_CHUNK");},neighborhood(frame.stage));
        }
    }
    /** Never discard queued continuations, even when the stage future is already terminal. */
    boolean canRestart(){return execution.isEmpty()&&(generation==null||generation.isDone());}
    boolean ready(){return pending.isEmpty()&&generation==null&&spawned;}
    ProtoChunk result(){if(!ready())throw new IllegalStateException("SEED_GENERATION_PENDING");return (ProtoChunk)chunk(target.toLong());}
    Object[] controlObjects(){return new Object[]{this,chunks,pending,execution,executor,generatorImage};}
}
