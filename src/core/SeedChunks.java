package dev.ronova.pro;

import java.util.*;
import java.util.concurrent.*;
import com.mojang.datafixers.util.Either;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.*;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.*;

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
    private final Map<Long,ChunkAccess> chunks=new LinkedHashMap<>();
    private final Deque<Frame> pending=new ArrayDeque<>();
    private final Queue<Runnable> execution=new ConcurrentLinkedQueue<>();
    private final java.util.concurrent.Executor executor=execution::add;
    private CompletableFuture<Either<ChunkAccess,ChunkHolder.ChunkLoadingFailure>> generation;
    private Frame generating;
    private final ChunkPos target;
    private boolean spawned;
    SeedChunks(ServerLevel level,ChunkPos target){
        this.level=level;this.target=target;seed=level.getSeed();generator=level.getChunkSource().getGenerator();pending.push(new Frame(new Stage(target.toLong(),ChunkStatus.FEATURES)));
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
        if(level.getSeed()!=seed||level.getChunkSource().getGenerator()!=generator)throw new IllegalStateException("WORLD_GENERATOR_CHANGED_DURING_REGENERATION");
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
    boolean ready(){return pending.isEmpty()&&generation==null&&spawned;}
    ProtoChunk result(){if(!ready())throw new IllegalStateException("SEED_GENERATION_PENDING");return (ProtoChunk)chunk(target.toLong());}
    Object[] controlObjects(){return new Object[]{this,chunks,pending,execution,executor};}
}
