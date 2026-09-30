package dev.ronova.pro.validation;

import dev.ronova.pro.ProRuntime;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.registries.*;

/** Ordinary real BlockEntity ticker, SavedData factory and worker construction. No supplied provenance. */
public final class ProducerSelectorsFixture {
    static final DeferredRegister<Block> BLOCKS=DeferredRegister.create(ForgeRegistries.BLOCKS,"pro_fixture");
    static final DeferredRegister<BlockEntityType<?>> TYPES=DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES,"pro_fixture");
    static final RegistryObject<Block> CORE=BLOCKS.register("selector_core",CoreBlock::new);
    static final RegistryObject<BlockEntityType<Core>> CORE_TYPE=TYPES.register("selector_core",()->BlockEntityType.Builder.of(Core::new,CORE.get()).build(null));
    public static final class CoreBlock extends BaseEntityBlock {
        CoreBlock(){super(BlockBehaviour.Properties.of().strength(1));}
        @Override public RenderShape getRenderShape(BlockState state){return RenderShape.INVISIBLE;}
        @Override public BlockEntity newBlockEntity(BlockPos pos,BlockState state){return new Core(pos,state);}
        @Override public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level,BlockState state,BlockEntityType<T> type){
            return level.isClientSide||type!=CORE_TYPE.get()?null:(l,p,s,e)->((Core)e).businessTick((ServerLevel)l);
        }
    }
    public static final class Core extends BlockEntity {
        public UUID targetId,neighborId;
        public boolean enabled=true;
        public int ticks;
        Core(BlockPos pos,BlockState state){super(CORE_TYPE.get(),pos,state);}
        Cow target(ServerLevel level){UUID id=targetId;if(id==null)return null;Cow e=new Cow(EntityType.COW,level);e.setUUID(id);e.setPos(3,80,3);e.setNoAi(true);return e;}
        Cow neighbor(ServerLevel level){UUID id=neighborId;if(id==null)return null;Cow e=new Cow(EntityType.COW,level);e.setUUID(id);e.setPos(7,80,3);e.setNoAi(true);return e;}
        void businessTick(ServerLevel level){
            ticks++;if(!enabled)return;
            if(targetId!=null&&level.getEntity(targetId)==null){Cow e=target(level);if(e!=null)level.addFreshEntity(e);}
            if(neighborId!=null&&level.getEntity(neighborId)==null){Cow e=neighbor(level);if(e!=null)level.addFreshEntity(e);}
        }
        @Override protected void saveAdditional(CompoundTag tag){super.saveAdditional(tag);if(targetId!=null)tag.putUUID("target",targetId);if(neighborId!=null)tag.putUUID("neighbor",neighborId);tag.putBoolean("enabled",enabled);}
    }
    public static final class Data extends SavedData {
        public UUID id;
        public int bodies;
        Cow make(ServerLevel level){Cow e=new Cow(EntityType.COW,level);bodies++;e.setUUID(id);return e;}
        @Override public CompoundTag save(CompoundTag tag){tag.putUUID("id",id);tag.putString("unknown","preserved");return tag;}
    }
    private static long workerSeed=33;
    private static double workerHeight=0.25;
    private static Cow firstWorker(ServerLevel level){long seed=workerSeed;double height=workerHeight;Cow e=new Cow(EntityType.COW,level);e.setPos(seed,80+height,3);return e;}
    private final BlockPos position=new BlockPos(3,80,3);
    private final Data data=new Data(),otherData=new Data();
    private final ExecutorService worker=Executors.newSingleThreadExecutor(r->{Thread t=new Thread(r,"selector-fixture-business-worker");t.setDaemon(true);return t;});
    private CompletableFuture<Cow> future;
    private Core core;
    private UUID targetId,neighborId,workerOperation;
    private Cow neighbor;
    private int tick,phase,startTicks;
    private boolean finished;
    public ProducerSelectorsFixture(){MinecraftForge.EVENT_BUS.addListener(EventPriority.LOWEST,this::tick);}
    private void tick(TickEvent.ServerTickEvent event){
        if(finished||event.phase!=TickEvent.Phase.END)return;
        ProRuntime runtime=ProRuntime.get(event.getServer());if(runtime==null)return;
        ServerLevel level=event.getServer().overworld();tick++;
        try {
            if(tick>600)throw new AssertionError("PRODUCER_SELECTOR_TIMEOUT phase="+phase);
            if(phase==0){
                level.setChunkForced(0,0,true);level.setBlock(position,CORE.get().defaultBlockState(),3);
                core=(Core)level.getBlockEntity(position);require(core!=null,"real core placement");
                targetId=UUID.randomUUID();neighborId=UUID.randomUUID();core.targetId=targetId;core.neighborId=neighborId;phase=1;
            }else if(phase==1&&level.getEntity(targetId) instanceof Cow old&&level.getEntity(neighborId) instanceof Cow other){
                neighbor=other;FixtureCommands.protect(runtime,neighbor);
                core.neighborId=null;require(core.neighborId.equals(neighborId),"protected producer selector cannot be removed");
                FixtureCommands.clear(runtime,old);require(core.targetId==null,"nullable exact UUID selector retired in request");
                require(core.neighborId.equals(neighborId)&&core.enabled,"shared source and enabled flag preserved");
                core.targetId=targetId;require(core.targetId==null,"old selector cannot be republished");
                require(core.target(level)==null,"retired source no longer produces before allocation");
                data.id=UUID.randomUUID();otherData.id=UUID.randomUUID();
                level.getDataStorage().set("selector_data",data);level.getDataStorage().set("selector_other",otherData);
                Cow prior=data.make(level);require(level.addFreshEntity(prior),"ordinary saved-data producer");FixtureCommands.clear(runtime,prior);
                int count=data.bodies;Cow refused=data.make(level);
                require(refused==null&&data.bodies==count+1&&data.id!=null,"nonnullable source withholds result without faking constructor rollback or physical retirement");
                Cow unrelated=otherData.make(level);require(unrelated!=null&&level.addFreshEntity(unrelated),"same factory different holder/selector remains live");
                future=CompletableFuture.supplyAsync(()->firstWorker(level),worker);startTicks=core.ticks;phase=2;
            }else if(phase==2&&future.isDone()){
                Cow e=future.join();require(e!=null&&level.addFreshEntity(e),"ordinary first worker output admitted");workerOperation=FixtureCommands.clear(runtime,e);phase=3;
            }else if(phase==3&&tick>30){
                require(core.ticks>startTicks+5&&core.enabled,"real shared ticker continues after target-source disposal");
                require(level.getEntity(targetId)==null&&core.targetId==null,"disposed core source cannot rebuild across ordinary ticks");
                require(level.getEntity(neighborId)==neighbor&&!neighbor.isRemoved(),"same core unrelated body preserved");
                require(runtime.recoveryStatus(workerOperation).stream().anyMatch(row->row.contains("SCALAR_PRODUCER_CONSUMPTION_REQUIRES_DISPOSITION")&&row.contains("workerSeed")),"first background scalar consumption survives server binding");
                require(workerSeed==33&&workerHeight==0.25,"shared worker scalar values unchanged");
                FixtureCommands.revoke(runtime,neighbor);core.neighborId=null;require(core.neighborId==null&&!neighbor.isRemoved(),"revocation releases source-slot protection without removing body");
                Files.writeString(Path.of("producer-selector-result.txt"),"PRODUCER_SELECTORS_RUNTIME_PASS\nREAL_CORE_SLOT_RETIRED_IN_REQUEST\nSHARED_TICKER_AND_NEIGHBOR_PRESERVED\nSELECTOR_REINSERTION_REFUSED\nPROTECTED_SELECTOR_AND_REVOKE_PASS\nNONNULLABLE_RESULT_WITHHELD_NOT_RETIRED\nOTHER_HOLDER_ALLOWED\nBACKGROUND_FIRST_SCALAR_CONSUMPTION_RETAINED\nO02_O05_NOT_CERTIFIED\n");
                finish();event.getServer().halt(false);
            }
        }catch(Throwable failure){
            try{Files.writeString(Path.of("producer-selector-result.txt"),"FAILED\n"+failure+"\nphase="+phase+"\n"+(workerOperation==null?"":String.join("\n",runtime.recoveryStatus(workerOperation))));}catch(Exception ignored){}
            failure.printStackTrace();finish();event.getServer().halt(false);
        }
    }
    private void finish(){finished=true;worker.shutdown();}
    private static void require(boolean condition,String message){if(!condition)throw new AssertionError(message);}
}
