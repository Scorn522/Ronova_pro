package dev.ronova.pro;

import java.util.*;
import dev.ronova.pro.mixin.Access;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.ticks.LevelTicks;
import net.minecraft.world.ticks.ScheduledTick;

/** Selected world areas share the existing runtime authority and body operations. */
final class WorldControl {
    private record Area(ServerLevel level,BlockPos low,BlockPos high) {
        boolean contains(BlockPos pos){return pos.getX()>=low.getX()&&pos.getX()<=high.getX()&&pos.getY()>=low.getY()&&pos.getY()<=high.getY()&&pos.getZ()>=low.getZ()&&pos.getZ()<=high.getZ();}
        AABB box(){return new AABB(low.getX(),low.getY(),low.getZ(),(double)high.getX()+1,(double)high.getY()+1,(double)high.getZ()+1);}
    }
    private static final class Rule {
        final UUID id;final Area area;final boolean freeze,clock,archive;final long until,gameTime;
        final Map<Entity,Boolean> bodies=new IdentityHashMap<>();
        Rule(Area area,boolean freeze,boolean clock,long until){this(UUID.randomUUID(),area,freeze,clock,until,false);}
        Rule(UUID id,Area area,boolean freeze,boolean clock,long until,boolean archive){this.id=id;this.area=area;this.freeze=freeze;this.clock=clock;this.until=until;this.archive=archive;this.gameTime=area.level().getGameTime();}
    }
    private record Held(LevelTicks<Object> container,ScheduledTick<Object> tick){}
    private final Map<UUID,Rule> rules=new LinkedHashMap<>();
    private final Map<LevelTicks<?>,List<Held>> held=new IdentityHashMap<>();
    private final ThreadLocal<Entity> moving=new ThreadLocal<>();
    private long tick,revision=1;
    long revision(){return revision;}
    List<ProNetwork.WorldArea> clientAreas(ServerLevel level){
        List<ProNetwork.WorldArea> result=new ArrayList<>();
        for(Rule rule:rules.values())if(rule.freeze&&rule.area.level()==level)result.add(new ProNetwork.WorldArea(rule.id,rule.area.low(),rule.area.high(),rule.clock,rule.gameTime));
        return List.copyOf(result);
    }
    List<ProNetwork.WorldBody> clientBodies(ServerLevel level){
        Set<ProNetwork.WorldBody> result=new LinkedHashSet<>();
        for(Rule rule:rules.values())if(rule.freeze&&rule.area.level()==level)for(Entity entity:rule.bodies.keySet()){
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
        rules.put(rule.id,rule);revision++;return rule.id;
    }
    boolean resume(UUID id){Rule rule=rules.get(id);if(rule==null||rule.archive||rules.remove(id)==null)return false;revision++;return true;}
    void archive(UUID id,ServerLevel level,BlockPos low,BlockPos high,boolean active){
        if(!active){Rule rule=rules.get(id);if(rule!=null&&rule.archive){rules.remove(id);revision++;}return;}
        if(rules.containsKey(id))return;Rule rule=new Rule(id,area(level,low,high),true,false,Long.MAX_VALUE,true);
        for(Entity entity:level.getAllEntities())if(rule.area.contains(((Access.EntityState)entity).pro$blockPosition()))rule.bodies.put(entity,true);rules.put(id,rule);revision++;
    }
    List<ScheduledTick<?>> heldTicks(LevelTicks<?> container,BlockPos low,BlockPos high,boolean remove){
        List<Held> queue=held.get(container);if(queue==null)return List.of();List<ScheduledTick<?>> result=new ArrayList<>();
        for(var iterator=queue.iterator();iterator.hasNext();){Held entry=iterator.next();BlockPos pos=entry.tick().pos();
            if(pos.getX()>=low.getX()&&pos.getX()<=high.getX()&&pos.getY()>=low.getY()&&pos.getY()<=high.getY()&&pos.getZ()>=low.getZ()&&pos.getZ()<=high.getZ()){result.add(entry.tick());if(remove)iterator.remove();}}
        if(queue.isEmpty())held.remove(container);return result;
    }
    boolean clock(ServerLevel level){for(Rule rule:rules.values())if(rule.freeze&&rule.clock&&rule.area.level()==level)return true;return false;}
    boolean frozen(Level level,BlockPos pos){for(Rule rule:rules.values())if(rule.freeze&&rule.area.level()==level&&rule.area.contains(pos))return true;return false;}
    boolean frozen(Entity entity){
        Access.EntityState state=(Access.EntityState)entity;
        if(moving.get()==entity||state.pro$level()==null||state.pro$level().isClientSide)return false;
        for(Rule rule:rules.values())if(rule.freeze&&rule.area.level()==state.pro$level()){
            if(rule.bodies.containsKey(entity))return true;
            if(rule.area.contains(state.pro$blockPosition())){rule.bodies.put(entity,true);revision++;return true;}
        }return false;
    }
    boolean spawnDenied(Entity entity){
        if(entity instanceof Player)return false;
        Access.EntityState state=(Access.EntityState)entity;
        for(Rule rule:rules.values())if(!rule.freeze&&rule.area.level()==state.pro$level()&&rule.area.contains(state.pro$blockPosition()))return true;return false;
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
        if(owner==null||!frozen(owner,scheduled.pos()))return false;
        List<Held> pending=held.computeIfAbsent(container,ignored->new ArrayList<>());
        // LevelChunkTicks keeps one entry per type identity and position, including while paused.
        for(Held prior:pending)if(prior.tick().type()==scheduled.type()&&prior.tick().pos().equals(scheduled.pos()))return true;
        pending.add(new Held((LevelTicks<Object>)container,(ScheduledTick<Object>)scheduled));return true;
    }
    void tick(MinecraftServer server){
        tick++;if(rules.values().removeIf(rule->tick>=rule.until))revision++;
        for(Rule rule:rules.values())if(rule.freeze){
            for(var iterator=rule.bodies.entrySet().iterator();iterator.hasNext();){
                var body=iterator.next();Entity entity=body.getKey();Access.EntityState state=(Access.EntityState)entity;
                if(state.pro$removal()!=null){iterator.remove();revision++;continue;}
                boolean visible=state.pro$level()==rule.area.level()&&rule.area.level().getEntity(state.pro$uuid())==entity;
                // The same captured player may leave and return to this dimension while the rule lives.
                if(body.getValue()!=visible){body.setValue(visible);revision++;}
            }
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
    String state(){return "WORLD_RULES="+rules.size()+";DEFERRED_TICKS="+held.values().stream().mapToLong(List::size).sum();}
    Object[] controlObjects(){return new Object[]{this,rules,held,moving};}
}
