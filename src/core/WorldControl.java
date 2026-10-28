package dev.ronova.pro;

import java.util.*;
import it.unimi.dsi.fastutil.shorts.AbstractShortListIterator;
import it.unimi.dsi.fastutil.shorts.ShortList;
import it.unimi.dsi.fastutil.shorts.ShortListIterator;
import dev.ronova.pro.mixin.Access;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction8;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ChunkHolder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.PalettedContainer;
import net.minecraft.world.level.chunk.ProtoChunk;
import net.minecraft.world.level.chunk.UpgradeData;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.ticks.LevelTicks;
import net.minecraft.world.ticks.SavedTick;
import net.minecraft.world.ticks.ScheduledTick;
import net.minecraft.world.ticks.SerializableTickContainer;

/** Selected world areas share the existing runtime authority and body operations. */
final class WorldControl {
    private record Area(ServerLevel level,BlockPos low,BlockPos high) {
        boolean contains(BlockPos pos){return pos.getX()>=low.getX()&&pos.getX()<=high.getX()&&pos.getY()>=low.getY()&&pos.getY()<=high.getY()&&pos.getZ()>=low.getZ()&&pos.getZ()<=high.getZ();}
        AABB box(){return new AABB(low.getX(),low.getY(),low.getZ(),(double)high.getX()+1,(double)high.getY()+1,(double)high.getZ()+1);}
    }
    private static final class Rule {
        final UUID id;final Area area;final boolean freeze,clock,archive;final long until,gameTime;
        final Map<Entity,Boolean> bodies=Collections.synchronizedMap(new IdentityHashMap<>());
        Rule(Area area,boolean freeze,boolean clock,long until){this(UUID.randomUUID(),area,freeze,clock,until,false);}
        Rule(UUID id,Area area,boolean freeze,boolean clock,long until,boolean archive){this.id=id;this.area=area;this.freeze=freeze;this.clock=clock;this.until=until;this.archive=archive;this.gameTime=area.level().getGameTime();}
    }
    private record Held(LevelTicks<Object> container,ScheduledTick<Object> tick){}
    private static final class PostProcess {
        final LevelChunk chunk;final ShortList pending;final int section;
        boolean finished,resuming;
        PostProcess(LevelChunk chunk,ShortList pending,int section){this.chunk=chunk;this.pending=pending;this.section=section;}
        BlockPos position(short packed){return ProtoChunk.unpackOffsetCoordinates(packed,chunk.getSectionYFromSectionIndex(section),chunk.getPos());}
    }
    private final Map<UUID,Rule> rules=new LinkedHashMap<>();
    private volatile List<Rule> activeRules=List.of();
    private volatile List<ServerLevel> levels=List.of();
    private volatile List<Area> spawnAreas=List.of();
    private volatile List<Area> frozenAreas=List.of(),mutationAreas=List.of();
    private volatile Set<ServerLevel> clockLevels=Set.of();
    private volatile String clockUnknown="";
    private final Map<LevelTicks<?>,List<Held>> held=new IdentityHashMap<>();
    private final Map<LevelChunk,Map<ShortList,PostProcess>> postProcessing=new IdentityHashMap<>();
    private final Map<LevelChunk,UpgradeData> upgrades=new IdentityHashMap<>();
    private final ThreadLocal<Entity> moving=new ThreadLocal<>();
    private long tick;private volatile long revision=1;
    long revision(){return revision;}
    List<ProNetwork.WorldArea> clientAreas(ServerLevel level){
        List<ProNetwork.WorldArea> result=new ArrayList<>();
        for(Rule rule:activeRules)if(rule.freeze&&rule.area.level()==level)result.add(new ProNetwork.WorldArea(rule.id,rule.area.low(),rule.area.high(),rule.clock,rule.gameTime));
        return List.copyOf(result);
    }
    List<ProNetwork.WorldBody> clientBodies(ServerLevel level){
        Set<ProNetwork.WorldBody> result=new LinkedHashSet<>();
        for(Rule rule:activeRules)if(rule.freeze&&rule.area.level()==level)for(Entity entity:bodySnapshot(rule)){
            Access.EntityState state=(Access.EntityState)entity;
            if(state.pro$level()==level&&level.getEntity(state.pro$uuid())==entity)result.add(new ProNetwork.WorldBody(state.pro$id(),state.pro$uuid()));
        }
        return List.copyOf(result);
    }
    private static Area area(ServerLevel level,BlockPos first,BlockPos second){
        return new Area(level,new BlockPos(Math.min(first.getX(),second.getX()),Math.min(first.getY(),second.getY()),Math.min(first.getZ(),second.getZ())),
                new BlockPos(Math.max(first.getX(),second.getX()),Math.max(first.getY(),second.getY()),Math.max(first.getZ(),second.getZ())));
    }
    UUID rule(ServerLevel level,BlockPos from,BlockPos to,int duration,boolean freeze,boolean clock){
        if(duration<1)throw new IllegalArgumentException("WORLD_RULE_DURATION_REQUIRED");Rule rule=new Rule(area(level,from,to),freeze,clock,tick+(long)duration);
        if(freeze)for(Entity entity:level.getAllEntities())if(rule.area.contains(((Access.EntityState)entity).pro$blockPosition()))rule.bodies.put(entity,true);
        policyChange(level.getServer(),()->{
            if(clock&&WorldStatePolicy.clockChain(level)==null)throw new IllegalStateException("WORLD_CLOCK_BACKING_UNSUPPORTED:"+level.dimension().location());
            rules.put(rule.id,rule);publishAreas();revision++;
        });return rule.id;
    }
    boolean resume(UUID id){Rule rule=rules.get(id);if(rule==null||rule.archive)return false;policyChange(rule.area.level().getServer(),()->{rules.remove(id);publishAreas();revision++;});return true;}
    private static List<Entity> bodySnapshot(Rule rule){synchronized(rule.bodies){return List.copyOf(rule.bodies.keySet());}}
    /** Only policy and real backing metadata run under these existing field gates. */
    private void policyChange(MinecraftServer server,Runnable change){
        ProRuntime runtime=ProRuntime.get(server);if(runtime==null)throw new IllegalStateException("WORLD_RUNTIME_UNAVAILABLE");
        List<AutoCloseable> gates=new ArrayList<>();Set<Object> locked=Collections.newSetFromMap(new IdentityHashMap<>());
        List<ServerLevel> current=new ArrayList<>();for(ServerLevel level:server.getAllLevels())current.add(level);
        try{
            for(ServerLevel level:current)if(locked.add(level))gates.add(runtime.worldPolicyGate(this,level));
            boolean added;
            do{
                added=false;
                for(ServerLevel level:current){List<Object> chain=WorldStatePolicy.clockChain(level);if(chain==null)continue;
                    for(Object carrier:chain)if(locked.add(carrier)){gates.add(runtime.worldPolicyGate(this,carrier));added=true;}}
            }while(added);
            levels=List.copyOf(current);change.run();
        }finally{
            RuntimeException failure=null;
            for(int i=gates.size()-1;i>=0;i--)try{gates.get(i).close();}catch(Exception unavailable){if(failure==null)failure=new IllegalStateException("WORLD_POLICY_GATE_RELEASE_FAILED",unavailable);else failure.addSuppressed(unavailable);}
            if(failure!=null)throw failure;
        }
    }
    private void publishAreas(){
        activeRules=List.copyOf(rules.values());
        spawnAreas=rules.values().stream().filter(rule->!rule.freeze).map(rule->rule.area).toList();
        frozenAreas=rules.values().stream().filter(rule->rule.freeze).map(rule->rule.area).toList();
        // Archive's private pause holds simulation while its checked apply path writes
        // history. It does not confer the user's separate region-write lock.
        mutationAreas=rules.values().stream().filter(rule->rule.freeze&&!rule.archive).map(rule->rule.area).toList();
        Set<ServerLevel> clocks=Collections.newSetFromMap(new IdentityHashMap<>());
        Set<String> unknown=new TreeSet<>();
        for(Rule rule:activeRules)if(rule.freeze&&rule.clock){
            List<Object> selected=WorldStatePolicy.clockChain(rule.area.level());clocks.add(rule.area.level());
            if(selected==null)unknown.add(rule.area.level().dimension().location().toString());
            for(ServerLevel level:levels){List<Object> chain=WorldStatePolicy.clockChain(level);
                if(chain==null){unknown.add(level.dimension().location().toString());continue;}
                if(WorldStatePolicy.sharedClock(selected,chain))clocks.add(level);
            }
        }
        clockLevels=Collections.unmodifiableSet(clocks);
        clockUnknown=String.join(",",unknown);
    }
    void archive(UUID id,ServerLevel level,BlockPos low,BlockPos high,boolean active){
        if(!active){Rule rule=rules.get(id);if(rule!=null&&rule.archive)policyChange(level.getServer(),()->{rules.remove(id);publishAreas();revision++;});return;}
        if(rules.containsKey(id))return;Rule rule=new Rule(id,area(level,low,high),true,false,Long.MAX_VALUE,true);
        for(Entity entity:level.getAllEntities())if(rule.area.contains(((Access.EntityState)entity).pro$blockPosition()))rule.bodies.put(entity,true);
        policyChange(level.getServer(),()->{rules.put(id,rule);publishAreas();revision++;});
    }
    List<ScheduledTick<?>> heldTicks(LevelTicks<?> container,BlockPos low,BlockPos high,boolean remove){
        List<Held> queue=held.get(container);if(queue==null)return List.of();List<ScheduledTick<?>> result=new ArrayList<>();
        for(var iterator=queue.iterator();iterator.hasNext();){Held entry=iterator.next();BlockPos pos=entry.tick().pos();
            if(pos.getX()>=low.getX()&&pos.getX()<=high.getX()&&pos.getY()>=low.getY()&&pos.getY()<=high.getY()&&pos.getZ()>=low.getZ()&&pos.getZ()<=high.getZ()){result.add(entry.tick());if(remove)iterator.remove();}}
        if(queue.isEmpty())held.remove(container);return result;
    }
    ChunkAccess.TicksToSave ticksForSave(ServerLevel level,ChunkAccess chunk,ChunkAccess.TicksToSave original){
        if(!(chunk instanceof LevelChunk live)||live.getLevel()!=level)return original;
        var blocks=heldForSave(level.getBlockTicks(),chunk.getPos());var fluids=heldForSave(level.getFluidTicks(),chunk.getPos());
        if(blocks.isEmpty()&&fluids.isEmpty())return original;
        return new ChunkAccess.TicksToSave(withHeldTicks(original.blocks(),blocks),withHeldTicks(original.fluids(),fluids));
    }
    @SuppressWarnings("unchecked") private <T> List<ScheduledTick<T>> heldForSave(LevelTicks<T> container,ChunkPos chunk){
        List<Held> pending=held.get(container);if(pending==null)return List.of();List<ScheduledTick<T>> result=new ArrayList<>();
        for(Held waiting:pending)if(ChunkPos.asLong(waiting.tick().pos())==chunk.toLong())result.add((ScheduledTick<T>)(ScheduledTick<?>)waiting.tick());
        return result;
    }
    /** Serialize the held logical entries without resuming or changing either live queue. */
    static <T> SerializableTickContainer<T> withHeldTicks(SerializableTickContainer<T> original,List<ScheduledTick<T>> deferred){
        if(deferred.isEmpty())return original;
        Map<T,Set<BlockPos>> positions=new IdentityHashMap<>();List<ScheduledTick<T>> unique=new ArrayList<>();
        for(ScheduledTick<T> tick:deferred)if(positions.computeIfAbsent(tick.type(),ignored->new HashSet<>()).add(tick.pos()))unique.add(tick);
        List<ScheduledTick<T>> snapshot=List.copyOf(unique);
        return (now,names)->{
            Tag saved=original.save(now,names);if(!(saved instanceof ListTag rows)||!rows.isEmpty()&&rows.getElementType()!=Tag.TAG_COMPOUND)throw new IllegalStateException("WORLD_HELD_TICK_SAVE_FORMAT_UNAVAILABLE");
            Map<String,T> types=new HashMap<>();
            for(T type:positions.keySet()){
                String name=names.apply(type);T prior=types.putIfAbsent(name,type);
                if(prior!=null&&prior!=type)throw new IllegalStateException("WORLD_HELD_TICK_SAVE_TYPE_NAME_COLLISION");
            }
            ListTag result=new ListTag();
            for(Tag row:rows){
                if(row instanceof CompoundTag data){
                    Optional<SavedTick<T>> entry=SavedTick.loadTick(data,name->Optional.ofNullable(types.get(name)));
                    if(entry.isPresent()&&positions.get(entry.get().type()).contains(entry.get().pos()))continue;
                }
                result.add(row.copy());
            }
            // Use the SAME now/name function as vanilla, including its int delay and priority format.
            for(ScheduledTick<T> tick:snapshot)result.add(SavedTick.saveTick(tick,names,now));
            return result;
        };
    }
    boolean clock(ServerLevel level){
        if(clockLevels.contains(level))return true;
        List<Object> chain=null;
        for(Rule rule:activeRules)if(rule.freeze&&rule.clock){
            if(rule.area.level().getServer()!=level.getServer())continue;
            if(chain==null)chain=WorldStatePolicy.clockChain(level);
            if(WorldStatePolicy.sharedClock(chain,WorldStatePolicy.clockChain(rule.area.level())))return true;
        }return false;
    }
    boolean frozen(Level level,BlockPos pos){for(Area area:frozenAreas)if(area.level()==level&&area.contains(pos))return true;return false;}
    boolean mutationDenied(Level level,BlockPos pos){for(Area area:mutationAreas)if(area.level()==level&&area.contains(pos))return true;return false;}
    boolean mutationLocked(Level level,BlockPos low,BlockPos high){
        for(Area area:mutationAreas)if(area.level()==level&&area.low().getX()<=high.getX()&&area.high().getX()>=low.getX()
                &&area.low().getY()<=high.getY()&&area.high().getY()>=low.getY()&&area.low().getZ()<=high.getZ()&&area.high().getZ()>=low.getZ())return true;
        return false;
    }
    boolean sectionMutationDenied(LevelChunkSection section,int x,int y,int z){
        // The same actual state palette can be shared by two section objects.
        // Refuse before the section updates its block/fluid counters as well.
        PalettedContainer<?> states=section.getStates();
        if(states.getClass()!=PalettedContainer.class||((Access.StatePalette)states).pro$strategy()!=PalettedContainer.Strategy.SECTION_STATES)return false;
        return paletteMutationDenied(states,PalettedContainer.Strategy.SECTION_STATES.getIndex(x,y,z));
    }
    boolean paletteMutationDenied(PalettedContainer<?> states,int index){
        if(index<0||index>=4096||states.getClass()!=PalettedContainer.class
                ||((Access.StatePalette)states).pro$strategy()!=PalettedContainer.Strategy.SECTION_STATES)return false;
        int x=index&15,y=(index>>8)&15,z=(index>>4)&15;
        for(Area area:mutationAreas){
            ServerLevel level=area.level();
            // getChunkNow deliberately returns null on a worker. Vanilla instead
            // publishes this cloned visible map through a volatile field, and a
            // holder publishes its full chunk through a volatile completed future.
            // Read those actual owners without loading chunks or retaining a grant.
            Access.Tracking tracking=(Access.Tracking)level.getChunkSource().chunkMap;
            var visible=tracking.pro$visibleChunks();
            for(ChunkHolder holder:visible.values()){
                ChunkPos origin=holder.getPos();int worldX=origin.getMinBlockX()+x,worldZ=origin.getMinBlockZ()+z;
                if(worldX<area.low().getX()||worldX>area.high().getX()||worldZ<area.low().getZ()||worldZ>area.high().getZ())continue;
                LevelChunk chunk=holder.getFullChunk();
                if(chunk==null||chunk.getClass()!=LevelChunk.class||chunk.getLevel()!=level)continue;
                LevelChunkSection[] sections=chunk.getSections();
                int first=Math.max(0,chunk.getSectionIndex(area.low().getY())),last=Math.min(sections.length-1,chunk.getSectionIndex(area.high().getY()));
                for(int section=first;section<=last;section++){
                    LevelChunkSection actual=sections[section];int worldY=(chunk.getSectionYFromSectionIndex(section)<<4)+y;
                    if(worldY<area.low().getY()||worldY>area.high().getY()||actual==null||actual.getClass()!=LevelChunkSection.class||actual.getStates()!=states)continue;
                    // A retired holder, replaced section or detached copy cannot
                    // inherit the old binding. Shared live palettes retain every
                    // affected coordinate, rather than trusting a first owner.
                    if(tracking.pro$visibleChunks().get(origin.toLong())==holder&&holder.getFullChunk()==chunk
                            &&chunk.getSections()[section]==actual&&actual.getStates()==states)return true;
                }
            }
        }
        return false;
    }
    boolean stateMutationDenied(WorldStatePolicy.Query query){
        if(WorldStatePolicy.clockCandidate(query.receiver)){
            for(Rule rule:activeRules)if(rule.freeze&&rule.clock){
                List<Object> selected=WorldStatePolicy.clockChain(rule.area.level());
                if(WorldStatePolicy.clockMatches(query,selected==null?List.of(rule.area.level()):selected))return true;
                for(ServerLevel level:levels){List<Object> chain=WorldStatePolicy.clockChain(level);
                    if(WorldStatePolicy.sharedClock(selected,chain)&&WorldStatePolicy.clockMatches(query,chain))return true;}
            }
            return false;
        }
        for(Area area:mutationAreas){
            ServerLevel level=area.level();Access.Tracking tracking=(Access.Tracking)level.getChunkSource().chunkMap;
            for(ChunkHolder holder:tracking.pro$visibleChunks().values()){
                ChunkPos origin=holder.getPos();
                int minX=Math.max(0,area.low().getX()-origin.getMinBlockX()),maxX=Math.min(15,area.high().getX()-origin.getMinBlockX());
                int minZ=Math.max(0,area.low().getZ()-origin.getMinBlockZ()),maxZ=Math.min(15,area.high().getZ()-origin.getMinBlockZ());
                if(minX>maxX||minZ>maxZ)continue;LevelChunk chunk=holder.getFullChunk();
                if(chunk==null||chunk.getClass()!=LevelChunk.class||chunk.getLevel()!=level)continue;
                LevelChunkSection[] sections=chunk.getSections();
                int first=Math.max(0,chunk.getSectionIndex(area.low().getY())),last=Math.min(sections.length-1,chunk.getSectionIndex(area.high().getY()));
                for(int section=first;section<=last;section++){
                    LevelChunkSection body=sections[section];if(body==null||body.getClass()!=LevelChunkSection.class)continue;
                    PalettedContainer<?> states=body.getStates();if(states.getClass()!=PalettedContainer.class||((Access.StatePalette)states).pro$strategy()!=PalettedContainer.Strategy.SECTION_STATES)continue;
                    int base=chunk.getSectionYFromSectionIndex(section)<<4,minY=Math.max(0,area.low().getY()-base),maxY=Math.min(15,area.high().getY()-base);
                    if(minY>maxY)continue;
                    var slice=new WorldStatePolicy.Slice(chunk,sections,section,body,states,minX,minY,minZ,maxX,maxY,maxZ);
                    if(WorldStatePolicy.matches(query,slice)&&tracking.pro$visibleChunks().get(origin.toLong())==holder&&holder.getFullChunk()==chunk
                            &&chunk.getSections()==sections&&sections[section]==body&&body.getStates()==states)return true;
                }
            }
        }return false;
    }
    private static int postProcessingSection(LevelChunk chunk,ShortList pending){
        // These are the vanilla field's actual slots, not a caller-supplied list
        // or an assumption about an unknown chunk implementation.
        if(chunk.getClass()!=LevelChunk.class)return -1;
        ShortList[] sections=chunk.getPostProcessing();
        for(int section=0;section<sections.length;section++)if(sections[section]==pending)return section;
        return -1;
    }
    ShortListIterator postProcessingIterator(LevelChunk chunk,ShortList pending){
        int section=postProcessingSection(chunk,pending);if(section<0)return pending.iterator();
        Map<ShortList,PostProcess> parts=postProcessing.computeIfAbsent(chunk,ignored->new IdentityHashMap<>());
        PostProcess part=parts.computeIfAbsent(pending,ignored->new PostProcess(chunk,pending,section));part.finished=false;
        return postProcessingIterator(part);
    }
    private ShortListIterator postProcessingIterator(PostProcess part){
        ShortListIterator original=part.pending.iterator();
        return new AbstractShortListIterator(){
            private short next;private boolean ready,delivered;
            @Override public boolean hasNext(){
                // Vanilla asks again only after this coordinate's fluid/shape body
                // returned. A throwing body therefore leaves its own original item.
                if(delivered){
                    if(!mutationDenied(part.chunk.getLevel(),part.position(next))){original.remove();part.chunk.setUnsaved(true);}
                    delivered=false;
                }
                if(ready)return true;
                while(original.hasNext()){
                    short candidate=original.nextShort();
                    if(mutationDenied(part.chunk.getLevel(),part.position(candidate)))continue;
                    next=candidate;ready=true;return true;
                }
                return false;
            }
            @Override public short nextShort(){if(!hasNext())throw new NoSuchElementException();ready=false;delivered=true;return next;}
            @Override public boolean hasPrevious(){return false;}
            @Override public short previousShort(){throw new NoSuchElementException();}
            @Override public int nextIndex(){return original.nextIndex();}
            @Override public int previousIndex(){return original.previousIndex();}
        };
    }
    boolean postProcessingClear(LevelChunk chunk,ShortList pending){
        Map<ShortList,PostProcess> parts=postProcessing.get(chunk);PostProcess part=parts==null?null:parts.get(pending);
        if(part==null||postProcessingSection(chunk,pending)!=part.section)return false;
        part.finished=true;
        // Consumed items are already gone. Frozen items stay in vanilla's exact
        // PostProcessing array and are saved/reloaded by ChunkSerializer itself.
        if(!pending.isEmpty())chunk.setUnsaved(true);
        if(pending.isEmpty()){parts.remove(pending);if(parts.isEmpty()&&postProcessing.get(chunk)==parts)postProcessing.remove(chunk);}
        return true;
    }
    void postProcessingUnloaded(LevelChunk chunk){postProcessing.remove(chunk);upgrades.remove(chunk);}
    boolean deferUpgrade(LevelChunk chunk,UpgradeData upgrade){
        if(chunk.getClass()!=LevelChunk.class||upgrade.getClass()!=UpgradeData.class||chunk.getUpgradeData()!=upgrade)return false;
        if(upgrade.isEmpty()||!upgradeLocked(chunk,upgrade)){upgrades.remove(chunk,upgrade);return false;}
        // No part of this call has run. Its original indices/sides remain in
        // chunk.getUpgradeData(), which vanilla serializes and reloads itself.
        upgrades.put(chunk,upgrade);chunk.setUnsaved(true);return true;
    }
    private boolean upgradeLocked(LevelChunk chunk,UpgradeData upgrade){
        if(mutationAreas.isEmpty())return false;
        CompoundTag data=upgrade.write(),indices=data.getCompound("Indices");ChunkPos origin=chunk.getPos();
        for(int section=0;section<chunk.getSectionsCount();section++)for(int packed:indices.getIntArray(Integer.toString(section))){
            BlockPos pos=new BlockPos(origin.getMinBlockX()+(packed&15),(chunk.getSectionYFromSectionIndex(section)<<4)+((packed>>8)&15),origin.getMinBlockZ()+((packed>>4)&15));
            if(upgradePositionLocked(chunk,pos))return true;
        }
        int sides=data.getByte("Sides")&255;
        for(Direction8 side:Direction8.values())if((sides&(1<<side.ordinal()))!=0){
            Set<Direction> directions=side.getDirections();boolean straight=directions.size()==1;
            boolean east=directions.contains(Direction.EAST),west=directions.contains(Direction.WEST),north=directions.contains(Direction.NORTH),south=directions.contains(Direction.SOUTH);
            int minX=origin.getMinBlockX()+(straight&&(north||south)?1:west?0:15),maxX=origin.getMinBlockX()+(straight&&(north||south)?14:west?0:15);
            int minZ=origin.getMinBlockZ()+(straight&&(east||west)?1:north?0:15),maxZ=origin.getMinBlockZ()+(straight&&(east||west)?14:north?0:15);
            for(BlockPos pos:BlockPos.betweenClosed(minX,chunk.getMinBuildHeight(),minZ,maxX,chunk.getMaxBuildHeight()-1,maxZ))if(upgradePositionLocked(chunk,pos))return true;
        }
        return false;
    }
    private boolean upgradePositionLocked(LevelChunk chunk,BlockPos pos){
        Level level=chunk.getLevel();if(mutationDenied(level,pos))return true;
        var state=chunk.getBlockState(pos);
        // Vanilla's leaf fixer publishes its queued distance cascade after the
        // direct indices/sides. Its bounded neighboring footprint also waits.
        if(state.getBlock() instanceof LeavesBlock&&mutationLocked(level,pos.offset(-6,-6,-6),pos.offset(6,6,6)))return true;
        if((state.is(Blocks.CHEST)||state.is(Blocks.TRAPPED_CHEST))&&state.getValue(ChestBlock.TYPE)==ChestType.SINGLE){
            Direction facing=state.getValue(ChestBlock.FACING);
            for(Direction direction:Direction.Plane.HORIZONTAL)if(direction.getAxis()!=facing.getAxis()){
                BlockPos neighbor=pos.relative(direction);if(!mutationDenied(level,neighbor))continue;
                if(!(level instanceof ServerLevel serverLevel))continue;
                LevelChunk actual=serverLevel.getChunkSource().getChunkNow(neighbor.getX()>>4,neighbor.getZ()>>4);
                if(actual==null)return true;
                var adjacent=actual.getBlockState(neighbor);
                if(adjacent.is(state.getBlock())&&adjacent.getValue(ChestBlock.TYPE)==ChestType.SINGLE&&adjacent.getValue(ChestBlock.FACING)==facing)return true;
            }
        }
        return false;
    }
    private void resumePostProcessing(MinecraftServer server){
        for(var parts:List.copyOf(postProcessing.values()))for(PostProcess part:List.copyOf(parts.values())){
            LevelChunk chunk=part.chunk;
            if(!part.finished||part.resuming||!(chunk.getLevel() instanceof ServerLevel level)||level.getServer()!=server
                    ||level.getChunkSource().getChunkNow(chunk.getPos().x,chunk.getPos().z)!=chunk)continue;
            if(postProcessingSection(chunk,part.pending)!=part.section){parts.remove(part.pending);if(parts.isEmpty()&&postProcessing.get(chunk)==parts)postProcessing.remove(chunk);continue;}
            part.resuming=true;
            try{
                ShortListIterator pending=postProcessingIterator(part);
                while(pending.hasNext()){
                    BlockPos pos=part.position(pending.nextShort());
                    // Only the saved coordinate body is resumed. One-time pending
                    // block-entity publication and UpgradeData are not replayed here.
                    var state=chunk.getBlockState(pos);var fluid=state.getFluidState();
                    if(!fluid.isEmpty())fluid.tick(level,pos);
                    if(!(state.getBlock() instanceof LiquidBlock)&&!mutationDenied(level,pos)){
                        var changed=Block.updateFromNeighbourShapes(state,level,pos);
                        level.setBlock(pos,changed,20);
                    }
                }
            }finally{part.resuming=false;}
            if(part.pending.isEmpty()){parts.remove(part.pending);if(parts.isEmpty()&&postProcessing.get(chunk)==parts)postProcessing.remove(chunk);}
        }
        for(LevelChunk chunk:List.copyOf(upgrades.keySet())){
            UpgradeData upgrade=upgrades.get(chunk);if(upgrade==null)continue;
            if(!(chunk.getLevel() instanceof ServerLevel level)||level.getServer()!=server
                    ||level.getChunkSource().getChunkNow(chunk.getPos().x,chunk.getPos().z)!=chunk)continue;
            if(chunk.getUpgradeData()!=upgrade){upgrades.remove(chunk,upgrade);continue;}
            if(upgradeLocked(chunk,upgrade))continue;
            // Remove before the one original upgrade call; an exception must not
            // replay a partly consumed upgrade and its neighbor ticks/fixers.
            upgrades.remove(chunk,upgrade);upgrade.upgrade(chunk);chunk.setUnsaved(true);
        }
    }
    boolean frozen(Entity entity){
        Access.EntityState state=(Access.EntityState)entity;
        if(moving.get()==entity||state.pro$level()==null||state.pro$level().isClientSide)return false;
        for(Rule rule:activeRules)if(rule.freeze&&rule.area.level()==state.pro$level()){
            if(rule.bodies.containsKey(entity))return true;
            if(rule.area.contains(state.pro$blockPosition()))return true;
        }return false;
    }
    boolean spawnDenied(ServerLevel level,Entity entity){
        if(entity instanceof Player)return false;
        Access.EntityState state=(Access.EntityState)entity;
        for(Area area:spawnAreas)if(area.level()==level&&area.contains(state.pro$blockPosition()))return true;return false;
    }
    boolean held(LevelTicks<?> container,BlockPos pos,Object type){
        List<Held> pending=held.get(container);if(pending==null)return false;
        for(Held prior:pending)if(prior.tick().type()==type&&prior.tick().pos().equals(pos))return true;return false;
    }
    boolean restorePending(LevelTicks<?> container,ScheduledTick<?> original,MinecraftServer server){
        if(!server.isSameThread()||original==null)return false;
        ServerLevel owner=null;for(ServerLevel level:server.getAllLevels())if(level.getBlockTicks()==container||level.getFluidTicks()==container){owner=level;break;}
        if(owner==null||frozen(owner,original.pos())||!owner.hasChunkAt(original.pos()))return false;
        List<Held> pending=held.get(container);if(pending==null)return false;
        for(Held waiting:pending)if(waiting.container()==container&&waiting.tick()==original)return true;return false;
    }
    @SuppressWarnings("unchecked") boolean defer(LevelTicks<?> container,ScheduledTick<?> scheduled,MinecraftServer server){
        ServerLevel owner=null;for(ServerLevel level:server.getAllLevels())if(level.getBlockTicks()==container||level.getFluidTicks()==container){owner=level;break;}
        if(owner==null)return false;
        List<Held> pending=held.get(container);
        // The original keeps its key until restoration, including a saved copy reloaded after resume.
        if(pending!=null)for(Held prior:pending)if(prior.tick().type()==scheduled.type()&&prior.tick().pos().equals(scheduled.pos()))return true;
        if(!frozen(owner,scheduled.pos()))return false;
        if(pending==null){pending=new ArrayList<>();held.put(container,pending);}
        pending.add(new Held((LevelTicks<Object>)container,(ScheduledTick<Object>)scheduled));
        LevelChunk chunk=owner.getChunkSource().getChunkNow(scheduled.pos().getX()>>4,scheduled.pos().getZ()>>4);
        if(chunk!=null)chunk.setUnsaved(true);return true;
    }
    void tick(MinecraftServer server){
        tick++;boolean expired=activeRules.stream().anyMatch(rule->tick>=rule.until);
        if(expired||!clockLevels.isEmpty()&&levelsChanged(server))policyChange(server,()->{rules.values().removeIf(rule->tick>=rule.until);publishAreas();revision++;});
        resumePostProcessing(server);
        for(Rule rule:activeRules)if(rule.freeze){
            synchronized(rule.bodies){for(var iterator=rule.bodies.entrySet().iterator();iterator.hasNext();){
                var body=iterator.next();Entity entity=body.getKey();Access.EntityState state=(Access.EntityState)entity;
                if(state.pro$removal()!=null){iterator.remove();revision++;continue;}
                boolean visible=state.pro$level()==rule.area.level()&&rule.area.level().getEntity(state.pro$uuid())==entity;
                // The same captured player may leave and return to this dimension while the rule lives.
                if(body.getValue()!=visible){body.setValue(visible);revision++;}
            }}
            for(Entity entity:rule.area.level().getAllEntities())if(!rule.bodies.containsKey(entity)&&rule.area.contains(((Access.EntityState)entity).pro$blockPosition())){rule.bodies.put(entity,true);revision++;}
        }
        for(var association:held.entrySet()){
            ServerLevel owner=null;for(ServerLevel level:server.getAllLevels())if(level.getBlockTicks()==association.getKey()||level.getFluidTicks()==association.getKey()){owner=level;break;}
            if(owner==null)continue;
            for(var iterator=association.getValue().iterator();iterator.hasNext();){Held waiting=iterator.next();BlockPos pos=waiting.tick().pos();
                if(frozen(owner,pos)||!owner.hasChunkAt(pos))continue;
                // Keep the original type, priority, order and due time; resume does not recreate a default tick.
                // A later direct chunk schedule must not keep this older logical entry waiting
                // until after that competitor runs. Restore the original entry at its unique key.
                if(((ProRuntime.WorldTickQueue)waiting.container()).pro$restoreWorldTick(waiting.tick()))iterator.remove();
            }
        }
        held.values().removeIf(List::isEmpty);
    }
    private boolean levelsChanged(MinecraftServer server){
        int index=0;List<ServerLevel> prior=levels;
        for(ServerLevel level:server.getAllLevels())if(index>=prior.size()||prior.get(index++)!=level)return true;
        return index!=prior.size();
    }
    boolean teleport(Entity entity,ServerLevel destination,Vec3 position){
        if(!Double.isFinite(position.x)||!Double.isFinite(position.y)||!Double.isFinite(position.z)||!Level.isInSpawnableBounds(BlockPos.containing(position)))throw new IllegalArgumentException("WORLD_TELEPORT_OUT_OF_BOUNDS");
        Entity previous=moving.get();moving.set(entity);
        try{
            boolean moved=entity.teleportTo(destination,position.x,position.y,position.z,Set.of(),entity.getYRot(),entity.getXRot());
            if(moved)for(Rule rule:rules.values())if(rule.bodies.remove(entity)!=null)revision++;return moved;
        }finally{if(previous==null)moving.remove();else moving.set(previous);}
    }
    void loadHistory(Entity entity,net.minecraft.nbt.CompoundTag data){
        Entity previous=moving.get();moving.set(entity);try{entity.load(data);}finally{if(previous==null)moving.remove();else moving.set(previous);}
    }
    List<Entity> selected(ServerLevel level,BlockPos first,BlockPos second){Area selected=area(level,first,second);List<Entity> result=new ArrayList<>();for(Entity entity:level.getAllEntities())if(selected.box().intersects(((Access.EntityState)entity).pro$box()))result.add(entity);return result;}
    Entity ray(Entity observer,double distance){
        if(!Double.isFinite(distance)||distance<=0)throw new IllegalArgumentException("WORLD_RAY_DISTANCE_REQUIRED");
        Vec3 eye=observer.getEyePosition(),end=eye.add(observer.getLookAngle().scale(distance));
        var block=observer.level().clip(new net.minecraft.world.level.ClipContext(eye,end,net.minecraft.world.level.ClipContext.Block.COLLIDER,net.minecraft.world.level.ClipContext.Fluid.NONE,observer));
        end=block.getLocation();double nearest=eye.distanceToSqr(end);Entity selected=null;
        for(Entity entity:observer.level().getEntities(observer,new AABB(eye,end).inflate(1),entity->entity.isPickable()&&!entity.isRemoved())){
            AABB box=((Access.EntityState)entity).pro$box().inflate(entity.getPickRadius());Optional<Vec3> crossing=box.clip(eye,end);
            double hit=box.contains(eye)?0:crossing.map(eye::distanceToSqr).orElse(Double.POSITIVE_INFINITY);
            if(hit<nearest){nearest=hit;selected=entity;}
        }return selected;
    }
    String state(){return "WORLD_RULES="+rules.size()+";DEFERRED_TICKS="+held.values().stream().mapToLong(List::size).sum()
            +";CLOCK_DIMENSIONS="+clockLevels.stream().map(level->level.dimension().location().toString()).sorted().toList()
            +";CLOCK_BACKING_UNSUPPORTED="+(clockUnknown.isEmpty()?"NONE":clockUnknown);}
    List<WeaponEntry> weaponRules(){
        List<WeaponEntry> result=new ArrayList<>();
        for(Rule rule:rules.values())if(!rule.archive)result.add(new WeaponEntry(rule.id,(rule.clock?"全维度时停":rule.freeze?"区域冻结":"区域禁生成")+" · "+rule.area.level().dimension().location(),"剩余 "+Math.max(0,rule.until-tick)+" tick"));
        return List.copyOf(result);
    }
    List<UUID> weaponClockRules(){return rules.values().stream().filter(rule->rule.clock&&!rule.archive).map(rule->rule.id).toList();}
    Object[] controlObjects(){return new Object[]{this,rules,activeRules,levels,held,postProcessing,upgrades,moving,spawnAreas,frozenAreas,mutationAreas,clockLevels};}
}
