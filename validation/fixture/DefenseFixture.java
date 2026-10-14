package dev.ronova.pro.validation;

import dev.ronova.pro.*;
import dev.ronova.pro.mixin.Access;
import java.nio.file.*;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.core.SectionPos;
import net.minecraft.nbt.*;
import net.minecraft.server.level.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.item.*;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityEvent;
import net.minecraftforge.eventbus.api.EventPriority;

/** Real player plus ordinary entity moves. Checks immediate state before runtime maintenance. */
public final class DefenseFixture {
    private int age,moves,entryAttacks;private boolean finished;
    private ServerPlayer player;private Cow cow,anchor,neighbour,target;private UUID clearing;
    private double x,y,z;private float health;private UUID protection;private int handedOff=-1;
    private int actionPhase,actionAttackCalls;private boolean actionMiningProbe,actionProbing;
    private ItemStack actionTool;private BlockPos actionMine,actionOther;private float actionVictimHealth;
    private Throwable actionCallbackFailure;
    public DefenseFixture() {
        MinecraftForge.EVENT_BUS.addListener(EventPriority.HIGHEST,this::tick);
        MinecraftForge.EVENT_BUS.addListener(EventPriority.NORMAL,this::entered);
        if("equipment-actions".equals(System.getProperty("ronova.pro.fixture.variant"))){
            MinecraftForge.EVENT_BUS.addListener(EventPriority.NORMAL,this::actionAttack);
            MinecraftForge.EVENT_BUS.addListener(EventPriority.NORMAL,this::actionBreakSpeed);
        }
    }
    private void entered(EntityEvent.EnteringSection event) {
        if(finished||"no-heal".equals(System.getProperty("ronova.pro.fixture.variant"))||"live-state".equals(System.getProperty("ronova.pro.fixture.variant"))||"equipment".equals(System.getProperty("ronova.pro.fixture.variant"))||"equipment-actions".equals(System.getProperty("ronova.pro.fixture.variant"))||event.getEntity()!=cow||age<20)return;
        var section=((Access.Callback)((Access.EntityState)cow).pro$callback()).pro$section();
        // This is an ordinary mod callback during the move, not the vanilla removal itself.
        section.remove(cow);entryAttacks++;
        if(!((Access.Group)((Access.Section)section).pro$storage()).pro$all().contains(cow))throw new AssertionError("move event could remove newly published protected record");
    }
    private static void require(boolean good,String why) {if(!good)throw new AssertionError(why);}
    private static void intact(Entity entity) {
        var level=(ServerLevel)((Access.EntityState)entity).pro$level();
        var manager=(Access.Manager)((Access.Server)level).pro$manager();
        var lookup=(Access.Lookup)manager.pro$lookup();
        require(lookup.pro$ids().get(entity.getId())==entity&&lookup.pro$uuids().get(entity.getUUID())==entity,"lookup missing");
        int count=0;long actual=Long.MIN_VALUE;
        for(var entry:((Access.Sections)manager.pro$sections()).pro$all().long2ObjectEntrySet()) {
            var list=((Access.Group)((Access.Section)entry.getValue()).pro$storage()).pro$all();
            for(Object value:list)if(value==entity) {count++;actual=entry.getLongKey();}
        }
        require(count==1&&actual==SectionPos.asLong(entity.blockPosition()),"section mismatch/duplicate count="+count+" actual="+actual+" expected="+SectionPos.asLong(entity.blockPosition()));
        require(new EntityRecords(level,entity).registered(),"registration incomplete");
    }
    private static final class CounterCow extends Cow {
        int hurtCalls,deathCalls,removeCalls;boolean spoof;
        @Override public float getHealth(){return spoof?-262:super.getHealth();}
        @Override public boolean isAlive(){return spoof?false:super.isAlive();}
        @Override public boolean isDeadOrDying(){return spoof?true:super.isDeadOrDying();}
        CounterCow(ServerLevel level) {super(EntityType.COW,level);}
        @Override public boolean hurt(net.minecraft.world.damagesource.DamageSource source,float amount) {hurtCalls++;return true;}
        @Override public void die(net.minecraft.world.damagesource.DamageSource source) {deathCalls++;}
        @Override public void remove(RemovalReason reason) {removeCalls++;super.remove(reason);}
    }
    private static Cow spawn(ServerLevel level,double x,double y,double z) {
        Cow c=new CounterCow(level);c.setNoAi(true);c.setNoGravity(true);c.moveTo(x,y,z,0,0);require(level.addFreshEntity(c),"spawn refused");return c;
    }
    private static Cow noHealCow(ServerLevel level,double x,double y,double z) {
        // Use vanilla damage/health bodies; CounterCow intentionally replaces hurt.
        Cow c=new Cow(EntityType.COW,level);c.setNoAi(true);c.setNoGravity(true);c.moveTo(x,y,z,0,0);
        require(level.addFreshEntity(c),"no-heal cow spawn refused");c.setHealth(4);return c;
    }
    private static net.minecraft.network.syncher.SynchedEntityData.DataItem<Float> healthItem(Cow entity) {
        return ((Access.Data)((Access.EntityState)entity).pro$data()).pro$item(Access.Living.pro$healthKey());
    }
    private static void health(Cow entity,float expected,String why) {
        require(entity.getHealth()==expected&&healthItem(entity).getValue()==expected,why);
    }
    private static void gainDenied(Cow entity) {
        float before=healthItem(entity).getValue();
        entity.heal(1);health(entity,before,"no-heal allowed ordinary heal");
        entity.setHealth(before+1);health(entity,before,"no-heal allowed direct setHealth gain");
        entity.getEntityData().set(Access.Living.pro$healthKey(),before+1);health(entity,before,"no-heal allowed synchronized health gain");
        healthItem(entity).setValue(before+1);health(entity,before,"no-heal allowed direct health item gain");
    }
    private static void rawDuration(net.minecraft.world.effect.MobEffectInstance effect,int value)throws ReflectiveOperationException {
        var singleton=sun.misc.Unsafe.class.getDeclaredField("theUnsafe");singleton.setAccessible(true);
        var unsafe=(sun.misc.Unsafe)singleton.get(null);java.lang.reflect.Field duration;
        try{duration=net.minecraft.world.effect.MobEffectInstance.class.getDeclaredField("duration");}
        catch(NoSuchFieldException production){duration=net.minecraft.world.effect.MobEffectInstance.class.getDeclaredField("f_19503_");}
        unsafe.putInt(effect,unsafe.objectFieldOffset(duration),value);
    }
    private static sun.misc.Unsafe equipmentUnsafe()throws ReflectiveOperationException{
        var field=sun.misc.Unsafe.class.getDeclaredField("theUnsafe");field.setAccessible(true);return (sun.misc.Unsafe)field.get(null);
    }
    private static ItemStack equipmentStack(){
        ItemStack stack=new ItemStack(Items.PAPER,8);CompoundTag tag=new CompoundTag();
        // This leaf is outside IntTag's shared value cache; no global tag singleton is mutated.
        tag.putInt("counter",7197);tag.putIntArray("carrier",new int[]{31,47});stack.setTag(tag);return stack;
    }
    private static void equipmentTag(Cow entity,sun.misc.Unsafe unsafe,boolean denied)throws ReflectiveOperationException{
        CompoundTag tag=entity.getMainHandItem().getTag();require(tag!=null,"actual equipped tag absent");
        IntTag leaf=(IntTag)tag.get("counter");int before=leaf.getAsInt();java.lang.reflect.Field data;
        try{data=IntTag.class.getDeclaredField("data");}catch(NoSuchFieldException production){data=IntTag.class.getDeclaredField("f_128671_");}
        unsafe.putInt(leaf,unsafe.objectFieldOffset(data),before+1);
        require(leaf.getAsInt()==(denied?before:before+1),"equipped numeric leaf write denied="+denied);
        int[] actual=tag.getIntArray("carrier");int first=actual[0];actual[0]=first+1;
        require(tag.getIntArray("carrier")[0]==(denied?first:first+1),"equipped actual primitive array write denied="+denied);
        tag.putInt("counter",before+2);
        require(tag.getInt("counter")== (denied?before:before+2),"equipped CompoundTag replacement denied="+denied);
        if(denied)require(tag.get("counter")==leaf,"denied tag replacement changed leaf identity");
    }
    @SuppressWarnings("unchecked") private static void equipmentCarriers(Cow entity,sun.misc.Unsafe unsafe,boolean denied)throws ReflectiveOperationException{
        NonNullList<ItemStack> hands=((Access.MobEquipment)entity).pro$hands();
        List<ItemStack> backing=(List<ItemStack>)((Access.EquipmentList)hands).pro$list();
        require(backing.getClass().getName().equals("java.util.Arrays$ArrayList"),"actual vanilla equipment backing changed");
        for(List<ItemStack> carrier:List.of(hands,backing)){
            ItemStack before=entity.getMainHandItem(),next=new ItemStack(Items.DIRT,2);
            require(carrier.set(0,next)==before,"actual equipment list set return contract");
            require(entity.getMainHandItem()==(denied?before:next),"actual "+carrier.getClass().getName()+" write denied="+denied);
        }
        var arrayField=backing.getClass().getDeclaredField("a");
        Object[] array=(Object[])unsafe.getObject(backing,unsafe.objectFieldOffset(arrayField));
        ItemStack before=entity.getMainHandItem(),next=new ItemStack(Items.DIRT,3);array[0]=next;
        require(entity.getMainHandItem()==(denied?before:next),"actual equipment backing array write denied="+denied);
    }
    private static void equipmentAllowed(Cow entity,sun.misc.Unsafe unsafe)throws ReflectiveOperationException{
        ItemStack stack=entity.getMainHandItem();equipmentTag(entity,unsafe,false);
        stack.setCount(7);require(stack.getCount()==7,"ordinary equipment setCount blocked");
        stack.shrink(1);require(stack.getCount()==6,"ordinary equipment shrink blocked");
        ItemStack split=stack.split(2);require(split!=stack&&split.getCount()==2&&stack.getCount()==4,"ordinary equipment split changed");
        ItemStack taken=stack.copyAndClear();require(taken!=stack&&taken.getCount()==4&&stack.isEmpty(),"ordinary equipment copyAndClear changed");
        ItemStack replacement=new ItemStack(Items.DIRT,4);entity.setItemSlot(EquipmentSlot.MAINHAND,replacement);
        require(entity.getMainHandItem()==replacement,"ordinary equipment setItemSlot blocked");equipmentCarriers(entity,unsafe,false);
    }
    private void equipmentTick(TickEvent.ServerTickEvent event){
        var server=event.getServer();age++;if(age!=10)return;
        try{
            var runtime=ProRuntime.get(server);var level=server.overworld();var pos=level.getSharedSpawnPos();
            level.getChunk(pos.getX()>>4,pos.getZ()>>4);
            cow=noHealCow(level,pos.getX()+.5,pos.getY()+4,pos.getZ()+.5);
            neighbour=noHealCow(level,pos.getX()+2.5,pos.getY()+4,pos.getZ()+.5);
            ItemStack held=equipmentStack();cow.setItemSlot(EquipmentSlot.MAINHAND,held);neighbour.setItemSlot(EquipmentSlot.MAINHAND,equipmentStack());
            require(cow.getMainHandItem()==held,"initial real mainhand publication failed");
            var unsafe=equipmentUnsafe();FixtureCommands.protect(runtime,cow);
            held.setCount(0);require(held.getCount()==8,"protected mainhand setCount changed count");
            held.shrink(2);require(held.getCount()==8,"protected mainhand shrink changed count");
            require(held.split(2)==ItemStack.EMPTY&&held.getCount()==8,"protected split leaked a copy or changed count");
            require(held.copyAndClear()==ItemStack.EMPTY&&held.getCount()==8,"protected copyAndClear leaked a copy or cleared held stack");
            cow.setItemSlot(EquipmentSlot.MAINHAND,new ItemStack(Items.DIRT,4));
            require(cow.getMainHandItem()==held,"protected setItemSlot replaced exact mainhand");
            equipmentTag(cow,unsafe,true);equipmentCarriers(cow,unsafe,true);
            require(cow.getMainHandItem()==held&&held.getCount()==8,"direct carrier writes changed protected mainhand");
            equipmentAllowed(neighbour,unsafe);
            require(cow.getMainHandItem()==held&&held.getCount()==8,"ordinary neighbour writes affected protected body");
            FixtureCommands.revoke(runtime,cow);equipmentAllowed(cow,unsafe);
            finish("EQUIPMENT_PASS exact_mainhand_count_split_copyAndClear_no_copy_leak_slot_nbt_primitive_leaf_NonNullList_ArraysList_array_neighbour_release");
            server.halt(false);
        }catch(Throwable failure){failure.printStackTrace();try{finish("EQUIPMENT_FAILED "+failure);}finally{server.halt(false);}}
    }
    private void actionAttack(net.minecraftforge.event.entity.player.AttackEntityEvent event){
        if(finished||event.getEntity()!=player||event.getTarget()!=cow||actionTool==null)return;
        try{
            int before=actionTool.getDamageValue();actionTool.setDamageValue(before+11);
            require(actionTool.getDamageValue()==before,"foreign AttackEntityEvent callback borrowed equipment action");actionAttackCalls++;
        }catch(Throwable failure){if(actionCallbackFailure==null)actionCallbackFailure=failure;}
    }
    private static java.lang.reflect.Field actionField(String mapped,String srg)throws NoSuchFieldException{
        try{return ServerPlayerGameMode.class.getDeclaredField(mapped);}catch(NoSuchFieldException production){return ServerPlayerGameMode.class.getDeclaredField(srg);}
    }
    private void actionBreakSpeed(net.minecraftforge.event.entity.player.PlayerEvent.BreakSpeed event){
        if(finished||actionPhase!=2||event.getEntity()!=player||actionMiningProbe||actionProbing)return;
        try{
            var unsafe=equipmentUnsafe();var mode=player.gameMode;
            long delayed=unsafe.objectFieldOffset(actionField("hasDelayedDestroy","f_9253_"));
            if(!unsafe.getBoolean(mode,delayed))return;
            actionProbing=true;
            long pos=unsafe.objectFieldOffset(actionField("delayedDestroyPos","f_9254_"));
            long start=unsafe.objectFieldOffset(actionField("delayedTickStart","f_9255_"));
            long ticks=unsafe.objectFieldOffset(actionField("gameTicks","f_9252_"));
            Object previousPos=unsafe.getObject(mode,pos);int previousStart=unsafe.getInt(mode,start),previousTicks=unsafe.getInt(mode,ticks),damage=actionTool.getDamageValue();
            require(actionMine.equals(previousPos),"delayed mining did not retain actual target");
            unsafe.putBoolean(mode,delayed,false);unsafe.putObject(mode,pos,actionOther);
            unsafe.putInt(mode,start,previousStart-1000);unsafe.putInt(mode,ticks,previousTicks+1000);
            require(unsafe.getBoolean(mode,delayed)&&unsafe.getObject(mode,pos)==previousPos&&unsafe.getInt(mode,start)==previousStart
                    &&unsafe.getInt(mode,ticks)==previousTicks,"foreign BreakSpeed callback changed live GameMode mining fields");
            mode.tick();require(unsafe.getInt(mode,ticks)==previousTicks,"foreign BreakSpeed callback recursively advanced GameMode tick");
            require(!mode.destroyBlock(actionOther)&&player.serverLevel().getBlockState(actionOther).is(Blocks.STONE),
                    "foreign BreakSpeed callback borrowed destroyBlock for another target");
            require(actionTool.getDamageValue()==damage,"foreign BreakSpeed callback borrowed delayed tool lease");actionMiningProbe=true;
        }catch(Throwable failure){if(actionCallbackFailure==null)actionCallbackFailure=failure;}finally{actionProbing=false;}
    }
    private void actionStage(String stage)throws java.io.IOException{
        Files.writeString(Path.of("defense-equipment-actions.txt"),stage+"\n"+cow.getId()+"\n"+actionMine.getX()+"\n"+actionMine.getY()+"\n"+actionMine.getZ()
                +"\n"+player.getX()+"\n"+player.getY()+"\n"+player.getZ()+"\n");
    }
    private void equipmentActionsTick(TickEvent.ServerTickEvent event){
        var server=event.getServer();if(actionPhase==0&&server.getPlayerList().getPlayers().isEmpty())return;age++;
        try{
            if(actionCallbackFailure!=null)throw new AssertionError("equipment action callback failed",actionCallbackFailure);
            require(age<=1200,"equipment-actions timeout phase="+actionPhase);var runtime=ProRuntime.get(server);var level=server.overworld();
            if(actionPhase==0){
                player=server.getPlayerList().getPlayers().get(0);require(player.connection!=null,"real connected player required");
                var spawn=level.getSharedSpawnPos();int bx=spawn.getX()+160,bz=spawn.getZ()+160;level.getChunk(bx>>4,bz>>4);
                BlockPos floor=new BlockPos(bx,level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,bx,bz)+2,bz);
                for(int dx=-2;dx<=2;dx++)for(int dz=-2;dz<=2;dz++)level.setBlock(floor.offset(dx,0,dz),Blocks.STONE.defaultBlockState(),3);
                actionMine=floor.offset(1,1,0);actionOther=floor.offset(-1,1,0);
                level.setBlock(actionMine,Blocks.STONE.defaultBlockState(),3);level.setBlock(actionOther,Blocks.STONE.defaultBlockState(),3);
                player.setGameMode(GameType.SURVIVAL);player.connection.teleport(bx+.5,floor.getY()+1,bz+.5,0,0);
                cow=noHealCow(level,bx+.5,floor.getY()+1,bz+2.5);neighbour=noHealCow(level,bx+2.5,floor.getY()+1,bz+2.5);
                actionVictimHealth=cow.getHealth();actionTool=new ItemStack(Items.WOODEN_PICKAXE);player.setItemSlot(EquipmentSlot.MAINHAND,actionTool);
                ItemStack ordinary=new ItemStack(Items.WOODEN_PICKAXE);neighbour.setItemSlot(EquipmentSlot.MAINHAND,ordinary);
                FixtureCommands.protect(runtime,player);ordinary.setDamageValue(1);require(ordinary.getDamageValue()==1,"player equipment action guard affected ordinary neighbour");
                player.inventoryMenu.broadcastChanges();actionPhase=1;actionStage("attack");
            }else if(actionPhase==1&&actionAttackCalls>0){
                // Actual DiggerItem.hurtEnemy consumes exactly two durability, without enchantment randomness.
                require(actionAttackCalls==1&&player.getMainHandItem()==actionTool&&actionTool.getDamageValue()==2&&cow.getHealth()<actionVictimHealth,
                        "real client attack did not damage target and apply original pickaxe wear: calls="+actionAttackCalls+" damage="+actionTool.getDamageValue());
                actionPhase=2;actionStage("mine");
            }else if(actionPhase==2&&level.getBlockState(actionMine).isAir()){
                // Actual DiggerItem.mineBlock consumes one durability for nonzero-hardness stone.
                require(actionMiningProbe&&player.getMainHandItem()==actionTool&&actionTool.getDamageValue()==3,
                        "real early STOP did not finish through delayed GameMode tick: probe="+actionMiningProbe+" damage="+actionTool.getDamageValue());
                require(level.getBlockState(actionOther).is(Blocks.STONE),"callback changed neighbouring block");
                FixtureCommands.revoke(runtime,player);actionTool.setDamageValue(0);require(actionTool.getDamageValue()==0,"release retained tool field guard");
                require(player.gameMode.destroyBlock(actionOther)&&level.getBlockState(actionOther).isAir()&&actionTool.getDamageValue()==1,
                        "release did not restore direct ordinary block destruction and original tool wear");
                finish("EQUIPMENT_ACTIONS_PASS real_client_attack_pickaxe_two_durability_START_early_STOP_real_delayed_tick_one_durability_foreign_callbacks_fields_nested_tick_other_destroy_denied_neighbour_release");
                server.halt(false);
            }
        }catch(Throwable failure){failure.printStackTrace();try{finish("EQUIPMENT_ACTIONS_FAILED "+failure);}finally{server.halt(false);}}
    }
    private void noHealTick(TickEvent.ServerTickEvent event) {
        var server=event.getServer();
        try {
            age++;var runtime=ProRuntime.get(server);var level=server.overworld();
            if(age==10) {
                var spawn=level.getSharedSpawnPos();level.getChunk(spawn.getX()>>4,spawn.getZ()>>4);
                cow=noHealCow(level,spawn.getX()+0.5,spawn.getY()+4,spawn.getZ()+0.5);
                neighbour=noHealCow(level,spawn.getX()+2.5,spawn.getY()+4,spawn.getZ()+0.5);
                FixtureCommands.noHeal(cow,true);gainDenied(cow);
                neighbour.heal(1);health(neighbour,5,"no-heal blocked neighbour heal");
                neighbour.setHealth(6);health(neighbour,6,"no-heal blocked neighbour setHealth");
                neighbour.getEntityData().set(Access.Living.pro$healthKey(),7F);health(neighbour,7,"no-heal blocked neighbour synchronized health");
                healthItem(neighbour).setValue(8F);health(neighbour,8,"no-heal blocked neighbour health item");
                require(cow.hurt(level.damageSources().generic(),1),"standalone no-heal blocked actual damage");
                health(cow,3,"standalone no-heal did not permit life loss");
                require(neighbour.hurt(level.damageSources().generic(),1),"no-heal blocked neighbour damage");
                health(neighbour,7,"neighbour damage did not reduce actual health");
                FixtureCommands.noHeal(cow,false);cow.heal(2);health(cow,5,"disabling standalone no-heal did not restore healing");
            }
            if(age==40) {
                // Thirty real ticks separate the first damage from this phase,
                // so vanilla invulnerableTime cannot impersonate protection.
                health(cow,5,"standalone no-heal release changed later health");
                FixtureCommands.noHeal(cow,true);FixtureCommands.protect(runtime,cow);gainDenied(cow);
                require(!cow.hurt(level.damageSources().generic(),1),"combined protection allowed actual damage");
                health(cow,5,"combined protection lost health");
                FixtureCommands.noHeal(cow,false);cow.heal(1);health(cow,6,"disabling no-heal left healing blocked by protection");
                require(!cow.hurt(level.damageSources().generic(),1),"disabling no-heal also removed protection");
                health(cow,6,"independent no-heal release lost damage protection");
                neighbour.heal(1);health(neighbour,8,"combined policy blocked neighbour heal");
                require(neighbour.hurt(level.damageSources().generic(),1),"combined policy blocked neighbour damage");
                health(neighbour,7,"combined policy changed neighbour life loss");
                FixtureCommands.noHeal(cow,true);cow.heal(1);health(cow,6,"combined no-heal re-enable did not deny healing");
                FixtureCommands.revoke(runtime,cow);
                require(cow.hurt(level.damageSources().generic(),1),"release left damage protection active");
                health(cow,5,"release did not restore actual damage");
                cow.heal(1);health(cow,6,"release did not preserve restored healing");
                finish("NO_HEAL_PASS standalone_gains_denied_actual_damage_allowed_disable_restores_heal_combined_protect_independent_disable_and_neighbour");
                server.halt(false);
            }
        } catch(Throwable failure) {
            failure.printStackTrace();
            try{finish("NO_HEAL_FAILED "+failure);}finally{server.halt(false);}
        }
    }
    private void liveStateTick(TickEvent.ServerTickEvent event) {
        var server=event.getServer();
        try {
            age++;var runtime=ProRuntime.get(server);var level=server.overworld();
            if(age==10) {
                var pos=level.getSharedSpawnPos();level.getChunk(pos.getX()>>4,pos.getZ()>>4);
                cow=noHealCow(level,pos.getX()+0.5,pos.getY()+4,pos.getZ()+0.5);
                neighbour=noHealCow(level,pos.getX()+2.5,pos.getY()+4,pos.getZ()+0.5);
                var speed=new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.MOVEMENT_SPEED,200);
                var jump=new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.JUMP,8);
                require(cow.addEffect(speed)&&cow.addEffect(jump),"initial live effects were not installed");
                MinecraftForge.EVENT_BUS.addListener(EventPriority.NORMAL,(net.minecraftforge.event.entity.living.MobEffectEvent.Expired expiry)->{
                    if(expiry.getEntity()!=cow)return;
                    cow.getActiveEffectsMap().clear();
                    require(cow.getEffect(net.minecraft.world.effect.MobEffects.MOVEMENT_SPEED)==speed,
                            "foreign expiry callback inherited vanilla map-removal permission");
                    entryAttacks++;
                });
                FixtureCommands.protect(runtime,cow);
                require(!cow.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.POISON,100)),
                        "protected body accepted adverse effect through public entry");
                cow.getActiveEffectsMap().put(net.minecraft.world.effect.MobEffects.WITHER,
                        new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.WITHER,100));
                require(!cow.hasEffect(net.minecraft.world.effect.MobEffects.WITHER),"raw map bypass installed adverse effect");
                cow.getActiveEffectsMap().clear();cow.removeEffect(net.minecraft.world.effect.MobEffects.MOVEMENT_SPEED);
                var values=cow.getActiveEffectsMap().values().iterator();while(values.hasNext()){values.next();values.remove();}
                for(var entry:cow.getActiveEffectsMap().entrySet())if(entry.getKey()==net.minecraft.world.effect.MobEffects.MOVEMENT_SPEED)
                    entry.setValue(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.POISON,100));
                rawDuration(speed,0);require(speed.getDuration()==200,"raw effect field bypass ended protected effect");
                require(cow.getEffect(net.minecraft.world.effect.MobEffects.MOVEMENT_SPEED)==speed&&cow.hasEffect(net.minecraft.world.effect.MobEffects.JUMP),
                        "foreign clear/removal/entry replacement destroyed active protected effects");
                require(neighbour.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.POISON,100)),
                        "live policy affected ordinary neighbour");
                var poison=neighbour.getEffect(net.minecraft.world.effect.MobEffects.POISON);rawDuration(poison,60);
                require(poison.getDuration()==60,"raw effect field guard affected ordinary neighbour");
            }
            if(age==30) {
                require(entryAttacks>0,"real vanilla expiry callback did not run");
                require(!cow.hasEffect(net.minecraft.world.effect.MobEffects.JUMP),"protection froze normal effect expiry");
                var speed=cow.getEffect(net.minecraft.world.effect.MobEffects.MOVEMENT_SPEED);
                require(speed!=null&&speed.getDuration()<200&&speed.getDuration()>0,"normal effect clock or neighbour survival was lost");
                FixtureCommands.revoke(runtime,cow);
                require(cow.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.POISON,100)),
                        "release retained effect immunity");
                cow.removeAllEffects();require(cow.getActiveEffectsMap().isEmpty(),"release retained map-removal guard");
                finish("LIVE_EFFECTS_PASS adverse_public_and_map_denied_foreign_clear_and_expiry_callback_denied_vanilla_expiry_clock_neighbour_release");
                server.halt(false);
            }
        } catch(Throwable failure) {
            failure.printStackTrace();
            try{finish("LIVE_EFFECTS_FAILED "+failure);}finally{server.halt(false);}
        }
    }
    private void tick(TickEvent.ServerTickEvent event) {
        if(finished||event.phase!=TickEvent.Phase.START)return;
        if("no-heal".equals(System.getProperty("ronova.pro.fixture.variant"))){noHealTick(event);return;}
        if("live-state".equals(System.getProperty("ronova.pro.fixture.variant"))){liveStateTick(event);return;}
        if("equipment".equals(System.getProperty("ronova.pro.fixture.variant"))){equipmentTick(event);return;}
        if("equipment-actions".equals(System.getProperty("ronova.pro.fixture.variant"))){equipmentActionsTick(event);return;}
        var server=event.getServer();if(server.getPlayerList().getPlayers().isEmpty())return;
        try {
            age++;var runtime=ProRuntime.get(server);var level=server.overworld();
            if(age==10) {
                player=server.getPlayerList().getPlayers().get(0);x=player.getX();y=player.getY()+4;z=player.getZ();
                player.setNoGravity(true);player.connection.teleport(x,y,z,0,0);health=player.getHealth();
                cow=spawn(level,x+2,y,z);anchor=spawn(level,x+2,y,z);neighbour=spawn(level,x+3,y,z);
                FixtureCommands.execute(server,player.createCommandSourceStack().withPermission(4),"protect @s");
                protection=FixtureCommands.protect(runtime,player);FixtureCommands.protect(runtime,cow);FixtureCommands.protect(runtime,anchor);intact(player);intact(cow);intact(anchor);
                var owners=ProRuntime.class.getDeclaredField("SYNC_OWNERS");owners.setAccessible(true);
                Map<?,?> cache=(Map<?,?>)owners.get(null);
                synchronized(cache) {require(!cache.isEmpty()&&cache.values().stream().allMatch(value->value instanceof java.lang.ref.WeakReference<?>),"owner observation cache retains entity strongly");}
                System.out.println("SYNC_OWNER_CACHE_WEAK_VALUES_PASS real_protection_cache");
                Files.writeString(Path.of("defense-target.txt"),cow.getId()+"\n"+cow.getUUID());
            }
            if(age>=20&&age<=60&&age%5==0) {
                if(age==20) {
                    CounterCow guarded=(CounterCow)cow;guarded.spoof=true;
                    LifeSpoof.set(player,-282);LifeSpoof.set(anchor,-282);LifeSpoof.set(neighbour,-282);
                    require(cow.getHealth()>0&&cow.isAlive()&&!cow.isDeadOrDying(),"protected no-super life getter spoof escaped");
                    require(anchor.getHealth()>0&&anchor.isAlive()&&!anchor.isDeadOrDying(),"protected merged life getter spoof escaped");
                    require(player.getHealth()==health&&player.isAlive()&&!player.isDeadOrDying(),"protected player effective life spoof escaped");
                    require(neighbour.getHealth()==-262&&!neighbour.isAlive()&&neighbour.isDeadOrDying(),"unrelated life getter modified");
                    System.out.println("EFFECTIVE_LIFE_SERVER_PASS no_super_and_merged_getters_neighbour_unchanged");
                    guarded.hurt(level.damageSources().generic(),9999);guarded.die(level.damageSources().generic());
                    require(guarded.hurtCalls==0&&guarded.deathCalls==0,"overridden damage/death body executed");
                    CounterCow normal=(CounterCow)neighbour;normal.hurt(level.damageSources().generic(),1);normal.die(level.damageSources().generic());
                    require(normal.hurtCalls==1&&normal.deathCalls==1,"unprotected override suppressed");
                    var players=(Access.Players)server.getPlayerList();
                    players.pro$players().remove(player);players.pro$byUuid().remove(player.getUUID());((Access.Server)level).pro$players().remove(player);
                    require(players.pro$players().contains(player)&&players.pro$byUuid().get(player.getUUID())==player&&level.players().contains(player),"protected player registries deleted");
                    level.removePlayerImmediately(player,Entity.RemovalReason.DISCARDED);server.getPlayerList().remove(player);
                    require(server.getPlayerList().getPlayer(player.getUUID())==player&&level.players().contains(player),"live player logout/removal allowed");
                    require(server.getPlayerList().respawn(player,false)==player&&server.getPlayerList().respawn(player,true)==player,"direct forced respawn replaced protected player");
                    require(runtime.operationSummary(protection).startsWith("防护策略已启用"),"forced respawn retired protection");intact(player);
                    var ticks=(Access.Ticks)((Access.Server)level).pro$ticks();var original=ticks.pro$active();
                    boolean[] copied={false};
                    ((net.minecraft.world.level.entity.EntityTickList)ticks).forEach(entity->{
                        if(entity==cow) {
                            ((net.minecraft.world.level.entity.EntityTickList)ticks).remove(neighbour);
                            require(ticks.pro$iterated()==original&&ticks.pro$active()!=original,"vanilla tick copy/swap blocked");
                            require(original.get(neighbour.getId())==neighbour&&ticks.pro$active().get(neighbour.getId())==null,"tick copy modified live iteration");copied[0]=true;
                        }
                    });
                    require(copied[0],"tick copy not exercised");require(ticks.pro$iterated()==null,"iteration marker did not release");
                    ((net.minecraft.world.level.entity.EntityTickList)ticks).add(neighbour);
                    var ids=((Access.Lookup)((Access.Manager)((Access.Server)level).pro$manager()).pro$lookup()).pro$ids();int size=ids.size();
                    var iterator=ids.int2ObjectEntrySet().iterator();boolean removed=false;
                    while(iterator.hasNext())if(iterator.next().getValue()==neighbour) {iterator.remove();removed=true;break;}
                    require(removed&&ids.size()==size-1&&ids.get(cow.getId())==cow&&ids.get(player.getId())==player,"mixed iterator removal corrupted linked map");
                    ids.put(neighbour.getId(),neighbour);intact(neighbour);
                }
                double next=x+(moves%2==0?20:-20);level.getChunk((int)Math.floor(next)>>4,(int)Math.floor(z)>>4);
                player.connection.teleport(next,y,z,0,0);intact(player);cow.setPos(next+2,y,z);moves++;
                intact(player);intact(cow);intact(anchor);
                var callback=((Access.EntityState)cow).pro$callback();callback.onRemove(Entity.RemovalReason.DISCARDED);
                var section=((Access.Callback)callback).pro$section();section.remove(cow);
                ((Access.Group)((Access.Section)section).pro$storage()).pro$all().remove(cow);
                for(Entity.RemovalReason reason:Entity.RemovalReason.values()) {cow.remove(reason);require(!cow.isRemoved(),"removal reason bypass: "+reason);}
                player.hurt(level.damageSources().generic(),9999);player.setHealth(0);
                require(!cow.isRemoved()&&player.getHealth()==health,"removal/health guard lost after movement");intact(cow);intact(player);
                if(age==20) {neighbour.remove(Entity.RemovalReason.DISCARDED);require(neighbour.isRemoved(),"unrelated removal blocked");}
            }
            if(age==65) {
                target=new Cow(EntityType.COW,level) {public void remove(RemovalReason reason) { }};
                target.setNoAi(true);target.moveTo(player.getX()+2,y,z,0,0);require(level.addFreshEntity(target),"clear target spawn");
                var before=new HashSet<>(runtime.operations());
                FixtureCommands.execute(server,player.createCommandSourceStack().withPermission(4),"clear "+target.getUUID());
                clearing=runtime.operations().stream().filter(id->!before.contains(id)&&runtime.query(id).action().equals("CLEAR")).findFirst().orElseThrow();
            }
            if(age>1800)throw new AssertionError("defense client timeout");
            if(age>=130&&handedOff<0&&Files.isRegularFile(Path.of("../client/defense-client-checked.txt"))
                    &&runtime.query(clearing)!=null&&runtime.query(clearing).complete()) {
                require(Files.readString(Path.of("../client/defense-client-checked.txt")).startsWith("CLIENT_DEFENSE_PASS"),"client defense failed");
                require(moves==9&&entryAttacks==9,"movement cases not exercised");intact(player);intact(cow);
                var result=runtime.query(clearing);require(result!=null&&result.complete(),"clear did not converge: "+result);
                var manager=(Access.Manager)((Access.Server)level).pro$manager();require(((Access.Lookup)manager.pro$lookup()).pro$ids().get(target.getId())==null,"clear lookup remains");
                FixtureCommands.revoke(runtime,cow);require(cow.getHealth()==-262&&!cow.isAlive()&&cow.isDeadOrDying(),"revoke still forces effective life");cow.remove(Entity.RemovalReason.DISCARDED);require(cow.isRemoved(),"revoke did not restore removal");
                ServerPlayer old=player;player.wonGame=true;
                player.connection.handleClientCommand(new net.minecraft.network.protocol.game.ServerboundClientCommandPacket(net.minecraft.network.protocol.game.ServerboundClientCommandPacket.Action.PERFORM_RESPAWN));
                player=server.getPlayerList().getPlayer(old.getUUID());
                require(player!=old&&player.connection.player==player,"legitimate end-return did not hand off player");
                require(runtime.protectionStatus(player).startsWith("ACTIVE"),"end-return lost protection");
                require(runtime.operationSummary(protection).startsWith("防护策略已启用"),"old operation falsely reports inactive after handoff");
                handedOff=age;Files.writeString(Path.of("defense-handoff.txt"),player.getId()+"\n"+player.getUUID());
            }
            if(handedOff>=0&&age-handedOff>30&&Files.isRegularFile(Path.of("../client/defense-handoff-checked.txt"))) {
                require(Files.readString(Path.of("../client/defense-handoff-checked.txt")).equals("HANDOFF_PASS"),"client handoff failed");
                player.hurt(player.damageSources().generic(),99999);require(player.getHealth()>0&&!player.isRemoved(),"protection lost after handoff");
                finish("DEFENSE_PASS client_remove_duplicate_revoke_override_bodies_player_registries_forced_respawn_end_return_movement_clear_and_neighbours");
            }
        } catch(Throwable failure) {failure.printStackTrace();finish("DEFENSE_FAILED "+failure);}
    }
    private void finish(String result) {
        finished=true;System.out.println(result);
        try {Files.writeString(Path.of("defense-result.txt"),result);}catch(Exception ex){throw new IllegalStateException(ex);}
    }
}
