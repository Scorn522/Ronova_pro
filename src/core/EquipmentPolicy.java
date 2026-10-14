package dev.ronova.pro;

import dev.ronova.pro.mixin.Access;
import java.lang.invoke.*;
import java.lang.ref.WeakReference;
import java.lang.reflect.*;
import java.util.*;
import net.minecraft.core.NonNullList;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.*;
import net.minecraft.network.protocol.game.ServerboundInteractPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.server.level.*;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.*;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.*;

/** Current equipment shares, rather than a snapshot of items the body once held. */
public final class EquipmentPolicy {
    private enum Kind { BODY, GAME_MODE, INVENTORY, COMPARTMENTS, COMPARTMENT_ARRAY, EQUIPMENT_LIST, EQUIPMENT_BACKING, EQUIPMENT_ARRAY, STACK, NBT }
    private record Slot(Field field,long offset,int width) { }
    private static final class Owner {
        final WeakReference<LivingEntity> entity;final long generation;
        Owner(LivingEntity entity,long generation){this.entity=new WeakReference<>(entity);this.generation=generation;}
    }
    private static final class Link {
        final Owner owner;final Kind kind;final int part;final WeakReference<ItemStack> stack;
        Link(Owner owner,Kind kind,int part,ItemStack stack){this.owner=owner;this.kind=kind;this.part=part;this.stack=new WeakReference<>(stack);}
    }
    private static final class Scope {
        final Owner owner;final String operation;final Class<?> anchor;final String method,descriptor;
        final WeakReference<Thread> thread=new WeakReference<>(Thread.currentThread());
        final WeakReference<ItemStack> used;final WeakReference<Object> menu;final Scope previous;final int mask,occurrences;final Class<?> itemType;
        boolean closed;
        Intent intent;DigFrame dig;WeakReference<Entity> attacked;
        Scope(Owner owner,String operation,StackWalker.StackFrame frame,Scope previous,int mask,ItemStack used,Object menu,int occurrences){
            this.owner=owner;this.operation=operation;anchor=frame.getDeclaringClass();method=frame.getMethodName();descriptor=frame.getDescriptor();
            this.previous=previous;this.mask=mask;this.used=new WeakReference<>(used);this.menu=new WeakReference<>(menu);this.occurrences=occurrences;this.itemType=itemType(used);
        }
    }
    private record Visit(Object value,int limit) { }
    private record Position(int x,int y,int z){
        static Position of(BlockPos pos){return pos!=null&&pos.getClass()==BlockPos.class?new Position(pos.getX(),pos.getY(),pos.getZ()):null;}
        boolean matches(BlockPos pos){return pos!=null&&pos.getClass()==BlockPos.class&&x==pos.getX()&&y==pos.getY()&&z==pos.getZ();}
    }
    private static final class Intent {
        final Object packet;final WeakReference<ServerGamePacketListenerImpl> listener;
        final WeakReference<ServerLevel> level;final WeakReference<Entity> target;final int id,sequence;
        final Object action,direction;final Position position;
        Intent(Object packet,ServerGamePacketListenerImpl listener,ServerLevel level,Entity target,int id,Object action,Object direction,Position position,int sequence){
            this.packet=packet;this.listener=new WeakReference<>(listener);this.level=new WeakReference<>(level);
            this.target=new WeakReference<>(target);this.id=id;this.action=action;this.direction=direction;this.position=position;this.sequence=sequence;
        }
    }
    private static final class DigState {
        final WeakReference<ServerLevel> level;final WeakReference<ServerPlayer> player;final Object mode;
        final boolean destroying,delayed;final int start,ticks,delayedStart,last;
        final WeakReference<BlockPos> pos,delayedPos;final Position coordinates,delayedCoordinates;
        DigState(ServerPlayerGameMode game){
            Access.EquipmentGameMode raw=(Access.EquipmentGameMode)game;
            level=new WeakReference<>(raw.pro$level());player=new WeakReference<>(raw.pro$player());mode=raw.pro$gameType();
            destroying=raw.pro$destroying();delayed=raw.pro$delayed();start=raw.pro$destroyStart();ticks=raw.pro$gameTicks();delayedStart=raw.pro$delayedStart();last=raw.pro$lastDestroyState();
            pos=new WeakReference<>(raw.pro$destroyPos());delayedPos=new WeakReference<>(raw.pro$delayedPos());
            coordinates=Position.of(raw.pro$destroyPos());delayedCoordinates=Position.of(raw.pro$delayedPos());
        }
        boolean same(DigState next){return level.get()!=null&&level.get()==next.level.get()&&player.get()!=null&&player.get()==next.player.get()&&mode==next.mode
                &&destroying==next.destroying&&delayed==next.delayed&&start==next.start&&ticks==next.ticks&&delayedStart==next.delayedStart&&last==next.last
                &&pos.get()==next.pos.get()&&delayedPos.get()==next.delayedPos.get()&&Objects.equals(coordinates,next.coordinates)&&Objects.equals(delayedCoordinates,next.delayedCoordinates);}
    }
    private static final class Mining {
        final Owner owner;final WeakReference<ServerPlayerGameMode> game;final WeakReference<ItemStack> tool;
        final Position target;final int selected,start;final DigState state;final boolean delayed;
        Mining(Owner owner,ServerPlayerGameMode game,ItemStack tool,Position target,int selected,int start,DigState state,boolean delayed){
            this.owner=owner;this.game=new WeakReference<>(game);this.tool=new WeakReference<>(tool);this.target=target;this.selected=selected;this.start=start;this.state=state;this.delayed=delayed;
        }
    }
    private static final class DigFrame {
        final WeakReference<ServerPlayerGameMode> game;final DigState before;final Position target;final Object action;
        final WeakReference<ItemStack> tool;final int selected,start;final Mining continuation;final Intent intent;
        final WeakReference<ServerLevel> destination;WeakReference<BlockPos> position;boolean consumed;
        DigFrame(ServerPlayerGameMode game,DigState before,Position target,Object action,ItemStack tool,int selected,int start,Mining continuation,Intent intent,ServerLevel destination){
            this.game=new WeakReference<>(game);this.before=before;this.target=target;this.action=action;this.tool=new WeakReference<>(tool);
            this.selected=selected;this.start=start;this.continuation=continuation;this.intent=intent;this.destination=new WeakReference<>(destination);
        }
    }
    private static final class Driver {
        final WeakReference<ServerPlayerGameMode> game;final WeakReference<ServerPlayer> player;final Driver previous;
        Driver(ServerPlayerGameMode game,ServerPlayer player,Driver previous){this.game=new WeakReference<>(game);this.player=new WeakReference<>(player);this.previous=previous;}
    }
    private static final int ALL=63,MAX_GRAPH=4096;
    private static volatile boolean initialized;
    private static Map<LivingEntity,Owner> OWNERS;
    private static Map<Object,List<Link>> CARRIERS;
    private static Map<Thread,Scope> SCOPES;
    private static Map<LivingEntity,Mining> MINING;
    private static Map<Thread,Driver> DRIVERS;
    private static Map<Class<?>,List<Slot>> FIELDS;
    private static StackWalker WALKER;
    private static Class<?> ARRAY_LIST,NODE,IMMUTABLE_LIST;
    private static Field ARRAY_VALUES,LIST_VALUES,LIST_SIZE,MAP_TABLE,NODE_NEXT,NODE_VALUE;
    private static Field INTERACT_ID,INTERACT_ACTION;private static Object ATTACK_ACTION;
    private static MethodHandle ACTION_SOURCE,ACTION_DELEGATE;
    private static volatile int REFERENCE_WIDTH=8;
    private EquipmentPolicy(){ }

    static synchronized void initialize()throws ReflectiveOperationException {
        if(initialized)return;
        ARRAY_LIST=Class.forName("java.util.Arrays$ArrayList",false,null);
        NODE=Class.forName("java.util.HashMap$Node",false,null);
        Class<?> tree=Class.forName("java.util.HashMap$TreeNode",false,null);
        IMMUTABLE_LIST=Class.forName("com.google.common.collect.RegularImmutableList",false,EquipmentPolicy.class.getClassLoader());
        Class<?>[] carriers={Player.class,ServerPlayer.class,ServerPlayerGameMode.class,ServerboundInteractPacket.class,Inventory.class,Mob.class,ArmorStand.class,NonNullList.class,ItemStack.class,
                CompoundTag.class,ListTag.class,ByteArrayTag.class,IntArrayTag.class,LongArrayTag.class,
                ByteTag.class,ShortTag.class,IntTag.class,LongTag.class,FloatTag.class,DoubleTag.class,StringTag.class,
                HashMap.class,LinkedHashMap.class,ArrayList.class,ARRAY_LIST,NODE,tree,IMMUTABLE_LIST};
        Class.forName("dev.ronova.pro.agent.RecoveryAgent",false,ClassLoader.getSystemClassLoader())
                .getMethod("openBackingAccess",Class.class,Class[].class).invoke(null,EquipmentPolicy.class,(Object)carriers);
        Class<?> type=Class.forName("sun.misc.Unsafe",false,ClassLoader.getPlatformClassLoader());
        Field singleton=type.getDeclaredField("theUnsafe");singleton.setAccessible(true);Object unsafe=singleton.get(null);
        Method offset=type.getMethod("objectFieldOffset",Field.class);
        REFERENCE_WIDTH=((Number)type.getMethod("arrayIndexScale",Class.class).invoke(unsafe,Object[].class)).intValue();
        if(REFERENCE_WIDTH<=0)throw new IllegalStateException("EQUIPMENT_REFERENCE_WIDTH_UNAVAILABLE");
        Map<Class<?>,List<Slot>> fields=new HashMap<>();
        for(Class<?> carrier:carriers){
            List<Slot> slots=new ArrayList<>();
            for(Class<?> declaring=carrier;declaring!=null&&declaring!=Object.class;declaring=declaring.getSuperclass())
                for(Field field:declaring.getDeclaredFields())if(!Modifier.isStatic(field.getModifiers())&&selected(field)){
                    if(!field.trySetAccessible())throw new IllegalStateException("EQUIPMENT_FIELD_ACCESS:"+field);
                    slots.add(new Slot(field,(Long)offset.invoke(unsafe,field),width(field.getType())));
                }
            fields.put(carrier,List.copyOf(slots));
        }
        ARRAY_VALUES=access(ARRAY_LIST,"a");LIST_VALUES=access(ArrayList.class,"elementData");LIST_SIZE=access(ArrayList.class,"size");
        MAP_TABLE=access(HashMap.class,"table");NODE_NEXT=access(NODE,"next");NODE_VALUE=access(NODE,"value");
        INTERACT_ID=accessAny(ServerboundInteractPacket.class,"entityId","f_134030_");INTERACT_ACTION=accessAny(ServerboundInteractPacket.class,"action","f_134031_");
        ATTACK_ACTION=accessAny(ServerboundInteractPacket.class,"ATTACK_ACTION","f_179595_").get(null);
        Class<?> bridge=Class.forName("dev.ronova.pro.bootstrap.ControlBridge",false,null);
        ACTION_SOURCE=MethodHandles.publicLookup().findStatic(bridge,"equipmentActionSourceAllowed",MethodType.methodType(boolean.class));
        ACTION_DELEGATE=MethodHandles.publicLookup().findStatic(bridge,"equipmentActionSourceAllowed",MethodType.methodType(boolean.class,Class.class));
        OWNERS=Collections.synchronizedMap(new WeakIdentityMap<>());CARRIERS=Collections.synchronizedMap(new WeakIdentityMap<>());
        SCOPES=Collections.synchronizedMap(new WeakIdentityMap<>());FIELDS=Map.copyOf(fields);
        MINING=Collections.synchronizedMap(new WeakIdentityMap<>());DRIVERS=Collections.synchronizedMap(new WeakIdentityMap<>());
        WALKER=StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE);initialized=true;
    }
    private static Field access(Class<?> type,String name)throws ReflectiveOperationException{Field field=type.getDeclaredField(name);field.setAccessible(true);return field;}
    private static Field accessAny(Class<?> type,String... names)throws ReflectiveOperationException{for(String name:names)try{return access(type,name);}catch(NoSuchFieldException ignored){ }throw new NoSuchFieldException(type.getName()+":"+Arrays.toString(names));}
    private static int width(Class<?> type){return !type.isPrimitive()?REFERENCE_WIDTH:type==long.class||type==double.class?8:type==int.class||type==float.class?4:type==short.class||type==char.class?2:1;}
    private static boolean names(String actual,String... names){for(String name:names)if(actual.equals(name))return true;return false;}
    private static boolean selected(Field field){
        Class<?> type=field.getDeclaringClass();String name=field.getName();
        if(type==Player.class)return names(name,"inventory","f_36093_");
        if(type==ServerPlayer.class)return names(name,"gameMode","f_8941_");
        if(type==ServerPlayerGameMode.class)return names(name,"level","f_9244_","player","f_9245_","isDestroyingBlock","f_9249_","destroyProgressStart","f_9250_","destroyPos","f_9251_","gameTicks","f_9252_","hasDelayedDestroy","f_9253_","delayedDestroyPos","f_9254_","delayedTickStart","f_9255_","lastSentState","f_9256_");
        if(type==Inventory.class)return names(name,"items","f_35974_","armor","f_35975_","offhand","f_35976_","selected","f_35977_","player","f_35978_","compartments","f_35979_");
        if(type==Mob.class)return names(name,"handItems","f_21350_","armorItems","f_21351_");
        if(type==ArmorStand.class)return names(name,"handItems","f_31538_","armorItems","f_31539_");
        if(type==NonNullList.class)return names(name,"list","f_122773_","defaultValue","f_122774_");
        if(type==ItemStack.class)return names(name,"count","f_41587_","item","f_41589_","tag","f_41590_","delegate","capNBT");
        if(type==HashMap.class)return Set.of("table","size","modCount","threshold","loadFactor").contains(name);
        if(type==LinkedHashMap.class)return Set.of("head","tail","accessOrder").contains(name);
        if(type==ArrayList.class)return names(name,"elementData","size");
        if(type==AbstractList.class)return name.equals("modCount");
        if(type==ARRAY_LIST||type==IMMUTABLE_LIST)return true;
        return type.getPackageName().equals("net.minecraft.nbt")||NODE!=null&&NODE.isAssignableFrom(type);
    }
    private static List<Slot> slots(Object value){
        if(value==null||!initialized)return List.of();
        List<Slot> exact=FIELDS.get(value.getClass());if(exact!=null)return exact;
        if(value instanceof ServerPlayer)return FIELDS.get(ServerPlayer.class);
        if(value instanceof ServerPlayerGameMode)return FIELDS.get(ServerPlayerGameMode.class);
        if(value instanceof Player)return FIELDS.get(Player.class);
        if(value instanceof Mob)return FIELDS.get(Mob.class);
        if(value instanceof ArmorStand)return FIELDS.get(ArmorStand.class);
        if(value instanceof Inventory)return FIELDS.get(Inventory.class);
        return List.of();
    }
    private static Inventory inventory(Owner owner){LivingEntity entity=owner.entity.get();return entity instanceof Player player?((Access.PlayerState)player).pro$inventory():null;}
    private static NonNullList<ItemStack> list(Owner owner,int part){
        LivingEntity entity=owner.entity.get();
        if(entity instanceof Player){Access.InventoryState state=(Access.InventoryState)inventory(owner);return part==0?state.pro$items():part==1?state.pro$armor():part==2?state.pro$offhand():null;}
        if(entity instanceof Mob mob){Access.MobEquipment state=(Access.MobEquipment)mob;return part==0?state.pro$hands():part==1?state.pro$armor():null;}
        if(entity instanceof ArmorStand stand){Access.StandEquipment state=(Access.StandEquipment)stand;return part==0?state.pro$hands():part==1?state.pro$armor():null;}
        return null;
    }
    private static Object backing(NonNullList<?> list){return list==null?null:((Access.EquipmentList)list).pro$list();}
    private static Object array(Object list){try{return list!=null&&list.getClass()==ARRAY_LIST?ARRAY_VALUES.get(list):null;}catch(IllegalAccessException failure){throw new IllegalStateException("EQUIPMENT_LIST_ARRAY_ACCESS",failure);}}
    private static boolean active(Owner owner){LivingEntity entity=owner==null?null:owner.entity.get();return entity!=null&&ProRuntime.liveStateGeneration(entity)==owner.generation;}
    private static int bit(Owner owner,int part,int index){
        LivingEntity entity=owner.entity.get();
        if(entity instanceof Player){
            if(part==0)return index==((Access.InventoryState)inventory(owner)).pro$selected()?1:0;
            if(part==1)return index>=0&&index<4?1<<(index+2):0;
            return part==2&&index==0?2:0;
        }
        return part==0&&index>=0&&index<2?1<<index:part==1&&index>=0&&index<4?1<<(index+2):0;
    }
    private static ItemStack itemAt(Owner owner,int part,int index){
        Object[] values=(Object[])array(backing(list(owner,part)));
        return values!=null&&index>=0&&index<values.length&&values[index] instanceof ItemStack stack?stack:null;
    }
    private static int shares(Owner owner,ItemStack stack){
        if(stack==null||stack==ItemStack.EMPTY)return 0;int mask=0;
        for(int part=0;part<(owner.entity.get() instanceof Player?3:2);part++){
            Object[] values=(Object[])array(backing(list(owner,part)));if(values==null)continue;
            for(int i=0;i<values.length;i++)if(values[i]==stack)mask|=bit(owner,part,i);
        }return mask;
    }
    private static List<Link> links(Object value){return !initialized||value==null?List.of():CARRIERS.getOrDefault(value,List.of());}
    private static void link(Owner owner,Object value,Kind kind,int part,ItemStack stack){
        if(value==null||value==ItemStack.EMPTY)return;
        synchronized(CARRIERS){
            List<Link> next=new ArrayList<>();boolean found=false;
            for(Link prior:CARRIERS.getOrDefault(value,List.of())){
                if(prior.owner!=owner&&!active(prior.owner))continue;
                if(prior.owner==owner&&prior.kind==kind&&prior.part==part&&prior.stack.get()==stack)found=true;
                next.add(prior);
            }
            if(!found)next.add(new Link(owner,kind,part,stack));CARRIERS.put(value,List.copyOf(next));
        }
    }
    private static boolean current(Link link,Object value){
        Owner owner=link.owner;if(!active(owner))return false;
        try{return switch(link.kind){
            case BODY -> owner.entity.get()==value;
            case GAME_MODE -> owner.entity.get() instanceof ServerPlayer player&&player.gameMode==value&&((Access.EquipmentGameMode)value).pro$player()==player;
            case INVENTORY -> inventory(owner)==value;
            case COMPARTMENTS -> inventory(owner)!=null&&((Access.InventoryState)inventory(owner)).pro$compartments()==value;
            case COMPARTMENT_ARRAY -> compartmentArray(owner,value);
            case EQUIPMENT_LIST -> list(owner,link.part)==value;
            case EQUIPMENT_BACKING -> backing(list(owner,link.part))==value;
            case EQUIPMENT_ARRAY -> array(backing(list(owner,link.part)))==value;
            case STACK -> shares(owner,link.stack.get())!=0&&link.stack.get()==value;
            case NBT -> shares(owner,link.stack.get())!=0&&walk(owner,link.stack.get(),value,false,null);
        };}catch(ReflectiveOperationException failure){throw new IllegalStateException("EQUIPMENT_CURRENT_CARRIER_ACCESS",failure);}
    }
    private static boolean compartmentArray(Owner owner,Object expected)throws IllegalAccessException{
        Inventory inventory=inventory(owner);if(inventory==null)return false;Object compartments=((Access.InventoryState)inventory).pro$compartments();
        for(Slot slot:slots(compartments))if(slot.field.getType().isArray()&&slot.field.get(compartments)==expected)return true;
        return false;
    }
    static boolean known(Object value){for(Link link:links(value))if(current(link,value))return true;return false;}
    static void publish(Entity entity,long generation){
        if(!initialized||!(entity instanceof LivingEntity living))return;
        if(generation<=0){OWNERS.remove(living);MINING.remove(living);return;}
        Owner owner=OWNERS.get(living);if(owner==null||owner.generation!=generation){MINING.remove(living);owner=new Owner(living,generation);OWNERS.put(living,owner);}
        refresh(owner);
        if(!(living instanceof Player||living instanceof Mob||living instanceof ArmorStand))throw new IllegalStateException("EQUIPMENT_CUSTOM_CARRIER_UNRESOLVED:"+living.getClass().getName());
        try{
            Method getter=null;for(String name:List.of("getItemBySlot","m_6844_"))try{getter=living.getClass().getMethod(name,EquipmentSlot.class);break;}catch(NoSuchMethodException ignored){ }
            if(getter!=null&&!Set.of(Player.class,Mob.class,ArmorStand.class).contains(getter.getDeclaringClass()))
                throw new IllegalStateException("EQUIPMENT_CUSTOM_SLOT_CARRIER_UNRESOLVED:"+getter.getDeclaringClass().getName());
        }catch(SecurityException failure){throw new IllegalStateException("EQUIPMENT_SLOT_DEFINITION_UNRESOLVED",failure);}
    }
    private static void refresh(Owner owner){
        LivingEntity entity=owner.entity.get();if(entity==null)return;link(owner,entity,Kind.BODY,-1,null);
        if(entity instanceof ServerPlayer player&&player.gameMode!=null)link(owner,player.gameMode,Kind.GAME_MODE,-1,null);
        if(entity instanceof Player){
            Inventory inventory=inventory(owner);if(inventory==null)throw new IllegalStateException("EQUIPMENT_INVENTORY_UNAVAILABLE");
            link(owner,inventory,Kind.INVENTORY,-1,null);Object compartments=((Access.InventoryState)inventory).pro$compartments();
            if(compartments==null||compartments.getClass()!=IMMUTABLE_LIST)throw new IllegalStateException("EQUIPMENT_COMPARTMENTS_IMPLEMENTATION_UNRESOLVED");
            link(owner,compartments,Kind.COMPARTMENTS,-1,null);
            try{for(Slot slot:slots(compartments))if(slot.field.getType().isArray())link(owner,slot.field.get(compartments),Kind.COMPARTMENT_ARRAY,-1,null);}
            catch(IllegalAccessException failure){throw new IllegalStateException("EQUIPMENT_COMPARTMENTS_ACCESS",failure);}
        }
        if(!(entity instanceof Player||entity instanceof Mob||entity instanceof ArmorStand))return;
        for(int part=0;part<(entity instanceof Player?3:2);part++){
            NonNullList<ItemStack> list=list(owner,part);Object backing=backing(list),array=array(backing);
            if(list==null||list.getClass()!=NonNullList.class||backing==null||backing.getClass()!=ARRAY_LIST||!(array instanceof Object[] values))
                throw new IllegalStateException("EQUIPMENT_LIST_IMPLEMENTATION_UNRESOLVED");
            int size=entity instanceof Player?part==0?36:part==1?4:1:part==0?2:4;
            if(values.length!=size)throw new IllegalStateException("EQUIPMENT_LIST_SIZE_UNRESOLVED");
            link(owner,list,Kind.EQUIPMENT_LIST,part,null);link(owner,backing,Kind.EQUIPMENT_BACKING,part,null);link(owner,array,Kind.EQUIPMENT_ARRAY,part,null);
            for(int i=0;i<values.length;i++)if(bit(owner,part,i)!=0){
                if(!(values[i] instanceof ItemStack stack))throw new IllegalStateException("EQUIPMENT_SLOT_VALUE_UNRESOLVED");captureStack(owner,stack);
            }
        }
    }
    private static void captureStack(Owner owner,ItemStack stack){if(stack!=null&&stack!=ItemStack.EMPTY){link(owner,stack,Kind.STACK,-1,stack);walk(owner,stack,null,true,null);}}
    /** Traverse only real NBT storage. A HashMap node belongs only while linked from the current table. */
    private static boolean walk(Owner owner,ItemStack stack,Object expected,boolean capture,Object proposed){
        if(stack==null||stack==ItemStack.EMPTY)return false;
        if(proposed instanceof String)return false; // Compound keys are shared immutable labels, never equipment carriers.
        Visit[] queue=new Visit[MAX_GRAPH];Object[] seen=new Object[MAX_GRAPH];int head=0,tail=0,count=0;
        Access.StackState state=(Access.StackState)(Object)stack;
        if(proposed!=null)queue[tail++]=new Visit(proposed,-1);
        else{if(state.pro$tag()!=null)queue[tail++]=new Visit(state.pro$tag(),-1);if(state.pro$capNBT()!=null&&state.pro$capNBT()!=state.pro$tag())queue[tail++]=new Visit(state.pro$capNBT(),-1);}
        try{while(head<tail){
            Visit visit=queue[head++];Object value=visit.value;if(value==null||value==EndTag.INSTANCE)continue;
            boolean found=false;for(int i=0;i<count;i++)if(seen[i]==value){found=true;break;}if(found)continue;
            if(count==MAX_GRAPH)throw new IllegalStateException("EQUIPMENT_NBT_GRAPH_CAPACITY");seen[count++]=value;
            if(value==expected)return true;
            if(capture)link(owner,value,Kind.NBT,-1,stack);
            Object[] children=null;int limit=-1;
            if(value.getClass()==CompoundTag.class){children=new Object[]{((Access.EquipmentCompound)value).pro$tags()};}
            else if(value.getClass()==ListTag.class){children=new Object[]{((Access.EquipmentTags)value).pro$list()};}
            else if(value.getClass()==HashMap.class||value.getClass()==LinkedHashMap.class){children=new Object[]{MAP_TABLE.get(value)};}
            else if(NODE.isInstance(value)){children=new Object[]{NODE_VALUE.get(value),NODE_NEXT.get(value)};}
            else if(value.getClass()==ArrayList.class){children=new Object[]{LIST_VALUES.get(value)};limit=LIST_SIZE.getInt(value);}
            else if(value.getClass()==ARRAY_LIST){children=new Object[]{ARRAY_VALUES.get(value)};}
            else if(value instanceof Object[] values){int end=visit.limit<0?values.length:visit.limit;if(end<0||end>values.length)throw new IllegalStateException("EQUIPMENT_NBT_LIST_SIZE_UNRESOLVED");children=Arrays.copyOf(values,end);}
            else if(value.getClass()==ByteArrayTag.class||value.getClass()==IntArrayTag.class||value.getClass()==LongArrayTag.class){
                for(Slot slot:slots(value))if(slot.field.getType().isArray()){children=new Object[]{slot.field.get(value)};break;}
            }else if(value.getClass().isArray()&&value.getClass().getComponentType().isPrimitive()){ }
            else if(!(value.getClass()==ByteTag.class||value.getClass()==ShortTag.class||value.getClass()==IntTag.class||value.getClass()==LongTag.class
                    ||value.getClass()==FloatTag.class||value.getClass()==DoubleTag.class||value.getClass()==StringTag.class))
                throw new IllegalStateException("EQUIPMENT_NBT_IMPLEMENTATION_UNRESOLVED:"+value.getClass().getName());
            if(children!=null)for(Object child:children)if(child!=null){if(tail==MAX_GRAPH)throw new IllegalStateException("EQUIPMENT_NBT_GRAPH_CAPACITY");queue[tail++]=new Visit(child,limit);}
        }}catch(IllegalAccessException failure){throw new IllegalStateException("EQUIPMENT_NBT_STORAGE_ACCESS",failure);}
        return false;
    }
    private static boolean sourceAllowed(){try{return (boolean)ACTION_SOURCE.invokeExact();}catch(Throwable failure){throw new IllegalStateException("EQUIPMENT_ACTION_SOURCE_UNAVAILABLE",failure);}}
    private static boolean sourceAllowed(Scope scope){try{return (boolean)ACTION_DELEGATE.invokeExact(scope.itemType);}catch(Throwable failure){throw new IllegalStateException("EQUIPMENT_DELEGATE_SOURCE_UNAVAILABLE",failure);}}
    private static Class<?> itemType(ItemStack stack){
        if(stack==null||stack==ItemStack.EMPTY)return null;Access.StackState state=(Access.StackState)(Object)stack;
        Object delegate=state.pro$delegate();Item item=delegate instanceof net.minecraft.core.Holder<?> holder&&holder.value() instanceof Item actual?actual:state.pro$item();
        return item==null?null:item.getClass();
    }
    private static int selected(Owner owner){Inventory inventory=inventory(owner);return inventory==null?-1:((Access.InventoryState)inventory).pro$selected();}
    private static ItemStack main(Owner owner){return itemAt(owner,0,selected(owner));}
    private static ServerPlayerGameMode currentGame(Owner owner){return owner.entity.get() instanceof ServerPlayer player?player.gameMode:null;}
    private static boolean gameOwned(Owner owner,ServerPlayerGameMode game){return active(owner)&&owner.entity.get() instanceof ServerPlayer player&&player.gameMode==game&&((Access.EquipmentGameMode)game).pro$player()==player;}
    private static boolean worldCurrent(Owner owner,ServerPlayerGameMode game,ServerLevel expected){return gameOwned(owner,game)&&expected!=null&&((Access.EntityState)owner.entity.get()).pro$level()==expected&&((Access.EquipmentGameMode)game).pro$level()==expected;}
    private static void cancelMining(Owner owner){LivingEntity entity=owner==null?null:owner.entity.get();if(entity!=null)MINING.remove(entity);}
    /** The actual ServerPlayer.tick invocation supplies both receiver identities. This creates no equipment permission. */
    public static void tickGameMode(Object receiver,Object actor){
        ServerPlayerGameMode game=(ServerPlayerGameMode)Objects.requireNonNull(receiver);Driver previous=initialized?DRIVERS.get(Thread.currentThread()):null,driver=null;
        if(initialized&&actor instanceof ServerPlayer player&&player.gameMode==game&&((Access.EquipmentGameMode)game).pro$player()==player&&player.server.isSameThread()&&sourceAllowed()){
            var caller=WALKER.walk(frames->frames.filter(frame->frame.getDeclaringClass().getNestHost()!=EquipmentPolicy.class).findFirst().orElse(null));
            if(caller!=null&&caller.getDeclaringClass()==ServerPlayer.class&&named(caller,"tick","m_8119_")){
                driver=new Driver(game,player,previous);DRIVERS.put(Thread.currentThread(),driver);
            }
        }
        Throwable failure=null;
        try{game.tick();}catch(RuntimeException|Error thrown){failure=thrown;throw thrown;}
        finally{try{if(driver!=null){if(DRIVERS.get(Thread.currentThread())!=driver)throw new IllegalMonitorStateException("EQUIPMENT_GAME_TICK_DRIVER_ORDER");if(previous==null)DRIVERS.remove(Thread.currentThread());else DRIVERS.put(Thread.currentThread(),previous);}}
            catch(RuntimeException|Error cleanup){if(failure==null)throw cleanup;if(cleanup!=failure)failure.addSuppressed(cleanup);}}
    }
    private static boolean driven(Owner owner,ServerPlayerGameMode game){Driver driver=DRIVERS.get(Thread.currentThread());return driver!=null&&driver.game.get()==game&&driver.player.get()==owner.entity.get()&&gameOwned(owner,game);}
    private static Intent intent(Owner owner,ServerGamePacketListenerImpl listener,Object packet){
        if(!(owner.entity.get() instanceof ServerPlayer player)||player.connection!=listener||listener.player!=player||!(((Access.EntityState)player).pro$level() instanceof ServerLevel level))return null;
        try{
            if(packet!=null&&packet.getClass()==ServerboundInteractPacket.class){
                int id=INTERACT_ID.getInt(packet);Object action=INTERACT_ACTION.get(packet);Entity target=level.getEntityOrPart(id);
                return new Intent(packet,listener,level,target,id,action,null,null,0);
            }
            if(packet!=null&&packet.getClass()==ServerboundPlayerActionPacket.class){
                ServerboundPlayerActionPacket input=(ServerboundPlayerActionPacket)packet;Position position=Position.of(input.getPos());
                if(position!=null)return new Intent(packet,listener,level,null,0,input.getAction(),input.getDirection(),position,input.getSequence());
            }
        }catch(IllegalAccessException failure){throw new IllegalStateException("EQUIPMENT_PACKET_INTENT_ACCESS",failure);}
        return null;
    }
    private static boolean currentIntent(Owner owner,Intent intent){return currentIntent(owner,intent,false);}
    private static boolean currentIntent(Owner owner,Intent intent,boolean completedAttack){
        if(intent==null||!(owner.entity.get() instanceof ServerPlayer player)||player.connection!=intent.listener.get()||intent.listener.get()==null||intent.listener.get().player!=player
                ||intent.level.get()==null||((Access.EntityState)player).pro$level()!=intent.level.get())return false;
        Object packet=intent.packet;
        try{
            if(packet!=null&&packet.getClass()==ServerboundInteractPacket.class){
                Entity target=intent.target.get(),indexed=intent.level.get().getEntityOrPart(intent.id);
                return INTERACT_ID.getInt(packet)==intent.id&&INTERACT_ACTION.get(packet)==intent.action
                        &&(target==null||indexed==target||completedAttack&&indexed==null&&((Access.EntityState)target).pro$level()==intent.level.get());
            }
            if(packet!=null&&packet.getClass()==ServerboundPlayerActionPacket.class){
                ServerboundPlayerActionPacket input=(ServerboundPlayerActionPacket)packet;
                return input.getAction()==intent.action&&input.getDirection()==intent.direction&&input.getSequence()==intent.sequence&&intent.position.matches(input.getPos());
            }
        }catch(IllegalAccessException failure){throw new IllegalStateException("EQUIPMENT_PACKET_INTENT_ACCESS",failure);}
        return false;
    }
    private static Scope parentIntent(Owner owner,String operation){
        Scope scope=SCOPES.get(Thread.currentThread());
        return scope!=null&&!scope.closed&&scope.owner==owner&&scope.operation.equals(operation)&&currentIntent(owner,scope.intent)?scope:null;
    }
    private static Mining currentMining(Owner owner,ServerPlayerGameMode game,DigState state){
        Mining mining=MINING.get(owner.entity.get());
        if(mining==null)return null;
        if(mining.owner!=owner||mining.game.get()!=game||!worldCurrent(owner,game,mining.state.level.get())||mining.selected!=selected(owner)||mining.tool.get()!=main(owner)||!mining.state.same(state)){
            cancelMining(owner);return null;
        }
        return mining;
    }
    private static DigFrame beginDig(Owner owner,ServerPlayerGameMode game,Object[] args,String operation,StackWalker.StackFrame caller){
        if(!gameOwned(owner,game))return null;DigState before=new DigState(game);ItemStack tool=main(owner);int selected=selected(owner);
        if(operation.equals("equipment-dig-level")){
            if(args.length!=1||!(args[0] instanceof ServerLevel destination)||!ProRuntime.liveStateLifecycleAllowed(owner.entity.get()))return null;
            cancelMining(owner);return new DigFrame(game,before,null,null,tool,selected,0,null,null,destination);
        }
        if(!worldCurrent(owner,game,before.level.get())){cancelMining(owner);return operation.equals("equipment-dig-tick")&&driven(owner,game)?new DigFrame(game,before,null,null,tool,selected,0,null,null,null):null;}
        if(operation.equals("equipment-dig-action")){
            Scope parent=parentIntent(owner,"equipment-player-action");
            if(parent==null||caller==null||caller.getDeclaringClass()!=ServerGamePacketListenerImpl.class||!named(caller,"handlePlayerAction","m_7502_")||args.length!=5||!(args[0] instanceof BlockPos position)||args[1]!=parent.intent.action||args[2]!=parent.intent.direction||!(args[4] instanceof Integer sequence)||sequence!=parent.intent.sequence||!parent.intent.position.matches(position))return null;
            Object action=args[1];Mining previous=currentMining(owner,game,before);Position target=parent.intent.position;
            if(action==ServerboundPlayerActionPacket.Action.START_DESTROY_BLOCK){cancelMining(owner);return new DigFrame(game,before,target,action,tool,selected,before.ticks,null,parent.intent,null);}
            if(action==ServerboundPlayerActionPacket.Action.ABORT_DESTROY_BLOCK){cancelMining(owner);return new DigFrame(game,before,target,action,tool,selected,before.start,null,parent.intent,null);}
            if(action==ServerboundPlayerActionPacket.Action.STOP_DESTROY_BLOCK){
                boolean valid=previous!=null&&previous.target.equals(target)&&before.destroying&&target.matches(before.pos.get());
                if(!valid)cancelMining(owner);
                return new DigFrame(game,before,target,action,tool,selected,valid?previous.start:before.start,valid?previous:null,parent.intent,null);
            }
            return null;
        }
        if(operation.equals("equipment-dig-tick")){
            if(!driven(owner,game))return null;Mining continuation=currentMining(owner,game,before);
            return new DigFrame(game,before,continuation==null?null:continuation.target,null,tool,selected,continuation==null?0:continuation.start,continuation,null,null);
        }
        if(operation.equals("equipment-remove-block")){
            Scope parent=SCOPES.get(Thread.currentThread());
            if(args.length!=2||!(args[0] instanceof BlockPos position)||!(args[1] instanceof Boolean)||caller==null||caller.getDeclaringClass()!=ServerPlayerGameMode.class||!named(caller,"destroyBlock","m_9280_")
                    ||parent==null||parent.owner!=owner||parent.closed||!parent.operation.equals("equipment-destroy")||parent.dig==null||parent.dig.game.get()!=game||parent.dig.target==null||!parent.dig.target.matches(position)||!digContext(parent))return null;
            DigFrame prior=parent.dig;return new DigFrame(game,before,prior.target,prior.action,prior.tool.get(),prior.selected,prior.start,prior.continuation,prior.intent,null);
        }
        if(operation.equals("equipment-destroy")){
            if(args.length!=1||!(args[0] instanceof BlockPos position)||caller==null||caller.getDeclaringClass()!=ServerPlayerGameMode.class||!named(caller,"tick","m_7712_","destroyAndAck","m_215116_"))return null;
            Scope parent=SCOPES.get(Thread.currentThread());
            if(parent==null||parent.owner!=owner||parent.closed||parent.dig==null||parent.dig.game.get()!=game||parent.dig.consumed||parent.dig.target==null||!parent.dig.target.matches(position)||!digContext(parent))return null;
            DigFrame prior=parent.dig;boolean authorized;
            if(parent.operation.equals("equipment-dig-tick"))authorized=prior.continuation!=null&&prior.continuation.delayed&&prior.before.delayed&&prior.target.matches(prior.before.delayedPos.get())&&prior.before.delayedStart==prior.start;
            else authorized=parent.operation.equals("equipment-dig-action")&&(prior.action==ServerboundPlayerActionPacket.Action.START_DESTROY_BLOCK
                    ||prior.action==ServerboundPlayerActionPacket.Action.STOP_DESTROY_BLOCK&&prior.continuation!=null);
            if(!authorized)return null;
            prior.consumed=true;cancelMining(owner);
            return new DigFrame(game,before,prior.target,prior.action,tool,selected,prior.start,prior.continuation,prior.intent,null);
        }
        return null;
    }
    private static boolean digContext(Scope scope){
        DigFrame dig=scope.dig;if(dig==null||dig.game.get()==null||!gameOwned(scope.owner,dig.game.get()))return false;
        if(scope.operation.equals("equipment-dig-level"))return ProRuntime.liveStateLifecycleAllowed(scope.owner.entity.get());
        if(!worldCurrent(scope.owner,dig.game.get(),dig.before.level.get())||selected(scope.owner)!=dig.selected||main(scope.owner)!=dig.tool.get())return false;
        if(dig.intent!=null&&!currentIntent(scope.owner,dig.intent))return false;
        Access.EquipmentGameMode raw=(Access.EquipmentGameMode)dig.game.get();
        if(raw.pro$gameType()!=dig.before.mode){cancelMining(scope.owner);return false;}
        if(dig.position!=null&&(dig.target==null||!dig.target.matches(dig.position.get())))return false;
        if(scope.operation.equals("equipment-dig-tick")||scope.operation.equals("equipment-destroy")||scope.operation.equals("equipment-remove-block")){
            if(raw.pro$destroyStart()!=dig.before.start||raw.pro$delayedStart()!=dig.before.delayedStart
                    ||raw.pro$destroyPos()!=dig.before.pos.get()||raw.pro$delayedPos()!=dig.before.delayedPos.get()
                    ||!Objects.equals(Position.of(raw.pro$destroyPos()),dig.before.coordinates)||!Objects.equals(Position.of(raw.pro$delayedPos()),dig.before.delayedCoordinates))return false;
        }
        if(scope.operation.equals("equipment-dig-tick"))return driven(scope.owner,dig.game.get())&&(raw.pro$gameTicks()==dig.before.ticks||raw.pro$gameTicks()==dig.before.ticks+1);
        if(scope.operation.equals("equipment-dig-action")&&raw.pro$gameTicks()!=dig.before.ticks)return false;
        return true;
    }
    private static void finishDig(Scope scope,boolean success){
        if(scope.dig==null)return;DigFrame dig=scope.dig;
        if(!success||!active(scope.owner)||dig.consumed||scope.operation.equals("equipment-destroy")||scope.operation.equals("equipment-remove-block")||scope.operation.equals("equipment-dig-level")||dig.action==ServerboundPlayerActionPacket.Action.ABORT_DESTROY_BLOCK||!digContext(scope)){
            cancelMining(scope.owner);return;
        }
        if(scope.operation.equals("equipment-dig-tick")&&dig.continuation==null)return;
        if(scope.operation.equals("equipment-dig-action")&&dig.action==ServerboundPlayerActionPacket.Action.STOP_DESTROY_BLOCK&&dig.continuation==null)return;
        if(dig.target==null)return;DigState after=new DigState(dig.game.get());
        boolean starting=after.destroying&&dig.target.matches(after.pos.get())&&after.start==dig.start;
        boolean delayed=after.delayed&&dig.target.matches(after.delayedPos.get())&&after.delayedStart==dig.start
                &&(scope.operation.equals("equipment-dig-tick")&&dig.continuation!=null&&dig.continuation.delayed
                ||scope.operation.equals("equipment-dig-action")&&dig.action==ServerboundPlayerActionPacket.Action.STOP_DESTROY_BLOCK&&dig.continuation!=null&&!dig.before.delayed);
        if(starting||delayed)MINING.put(scope.owner.entity.get(),new Mining(scope.owner,dig.game.get(),dig.tool.get(),dig.target,dig.selected,dig.start,after,delayed));else cancelMining(scope.owner);
    }
    private static LivingEntity actionEntity(Object receiver,Object[] args){
        if(receiver instanceof LivingEntity living)return living;
        if(receiver instanceof Inventory inventory)return ((Access.InventoryState)inventory).pro$player();
        if(receiver instanceof ServerGamePacketListenerImpl listener)return listener.player;
        return receiver instanceof ServerPlayerGameMode?((Access.EquipmentGameMode)receiver).pro$player():null;
    }
    private static boolean serverThread(LivingEntity entity){return entity instanceof ServerPlayer player?player.server.isSameThread():((Access.EntityState)entity).pro$level() instanceof ServerLevel level&&level.getServer().isSameThread();}
    private static boolean named(StackWalker.StackFrame frame,String... names){return names(frame.getMethodName(),names);}
    private static boolean vanilla(StackWalker.StackFrame frame){
        Class<?> type=frame.getDeclaringClass();return type.getModule()==Entity.class.getModule()&&type.getClassLoader()==Entity.class.getClassLoader()
                &&(!frame.getMethodName().contains("$")||frame.getMethodName().startsWith("lambda$")||frame.getMethodName().matches("m_[0-9]+_"));
    }
    /** Called by exact original action bodies, after packet thread handoff and before business publication. */
    public static Object beginAction(Object receiver,Object argument,String operation){
        if(!initialized)return null;Object[] args=argument instanceof Object[] values?values:new Object[0];
        LivingEntity entity=actionEntity(receiver,args);Owner owner=entity==null?null:OWNERS.get(entity);
        if(!active(owner)||!serverThread(entity)||!sourceAllowed())return null;
        if(receiver instanceof ServerPlayerGameMode&&operation.equals("equipment-use")&&(args.length==0||args[0]!=entity))return null;
        if(receiver instanceof ServerGamePacketListenerImpl listener&&(!(entity instanceof ServerPlayer player)||player.connection!=listener||listener.player!=player))return null;
        List<StackWalker.StackFrame> frames=WALKER.walk(stream->stream.filter(frame->frame.getDeclaringClass().getNestHost()!=EquipmentPolicy.class).toList());
        if(frames.isEmpty())return null;StackWalker.StackFrame entry=frames.get(0),caller=frames.size()>1?frames.get(1):null;
        int mask=ALL;ItemStack used=null;Object menu=null;boolean accepted=false;Intent actionIntent=null;DigFrame dig=null;Entity attacked=null;
        Class<?> anchor=entry.getDeclaringClass();
        if(operation.equals("equipment-select")){accepted=anchor==ServerGamePacketListenerImpl.class&&named(entry,"handleSetCarriedItem","m_7798_")&&packetCaller(caller);mask=0;}
        else if(operation.equals("equipment-menu")){accepted=anchor==ServerGamePacketListenerImpl.class&&named(entry,"handleContainerClick","m_5914_")&&packetCaller(caller);menu=entity instanceof Player player?player.containerMenu:null;}
        else if(operation.equals("equipment-player-action")){
            accepted=anchor==ServerGamePacketListenerImpl.class&&named(entry,"handlePlayerAction","m_7502_")&&packetCaller(caller);mask=3;
            if(accepted&&receiver instanceof ServerGamePacketListenerImpl listener&&args.length==1)actionIntent=intent(owner,listener,args[0]);
            accepted&=actionIntent!=null;
        }else if(operation.equals("equipment-interact")){
            accepted=anchor==ServerGamePacketListenerImpl.class&&named(entry,"handleInteract","m_6946_")&&packetCaller(caller);mask=0;
            if(accepted&&receiver instanceof ServerGamePacketListenerImpl listener&&args.length==1)actionIntent=intent(owner,listener,args[0]);
            accepted&=actionIntent!=null;
        }else if(operation.equals("equipment-attack")){
            Scope parent=parentIntent(owner,"equipment-interact");
            accepted=anchor==Player.class&&named(entry,"attack","m_5706_")&&caller!=null&&caller.getDeclaringClass()==ServerPlayer.class&&named(caller,"attack","m_5706_")
                    &&parent!=null&&parent.intent.action==ATTACK_ACTION&&args.length==1&&args[0] instanceof Entity&&args[0]==parent.intent.target.get();
            if(accepted){actionIntent=parent.intent;attacked=(Entity)args[0];used=main(owner);}mask=1;
        }else if(operation.equals("equipment-dig-action")||operation.equals("equipment-dig-tick")||operation.equals("equipment-destroy")||operation.equals("equipment-remove-block")||operation.equals("equipment-dig-level")){
            accepted=anchor==ServerPlayerGameMode.class&&receiver instanceof ServerPlayerGameMode
                    &&(operation.equals("equipment-dig-action")&&named(entry,"handleBlockBreakAction","m_214168_")
                    ||operation.equals("equipment-dig-tick")&&named(entry,"tick","m_7712_")
                    ||operation.equals("equipment-destroy")&&named(entry,"destroyBlock","m_9280_")
                    ||operation.equals("equipment-remove-block")&&named(entry,"removeBlock")
                    ||operation.equals("equipment-dig-level")&&named(entry,"setLevel","m_9260_"));
            if(accepted)dig=beginDig(owner,(ServerPlayerGameMode)receiver,args,operation,caller);
            accepted&=dig!=null;mask=operation.equals("equipment-destroy")?1:0;
            if(dig!=null){if(args.length>0&&args[0] instanceof BlockPos position)dig.position=new WeakReference<>(position);if(mask!=0)used=dig.tool.get();}
        }
        else if(operation.equals("equipment-creative")||operation.equals("equipment-pick")){
            accepted=anchor==ServerGamePacketListenerImpl.class&&named(entry,operation.equals("equipment-pick")?"handlePickItem":"handleSetCreativeModeSlot",operation.equals("equipment-pick")?"m_7965_":"m_5964_")&&packetCaller(caller);
        }else if(operation.equals("equipment-use")){
            accepted=anchor==ServerPlayerGameMode.class&&named(entry,"useItem","m_6261_","useItemOn","m_7179_")&&caller!=null&&caller.getDeclaringClass()==ServerGamePacketListenerImpl.class&&named(caller,"handleUseItem","m_5760_","handleUseItemOn","m_6371_");
            if(args.length>3&&args[2] instanceof ItemStack stack&&args[3] instanceof InteractionHand hand){used=stack;mask=hand==InteractionHand.MAIN_HAND?1:2;accepted&=shares(owner,used)!=0&&(shares(owner,used)&mask)!=0;}else accepted=false;
        }else if(operation.equals("equipment-consume")||operation.equals("equipment-release")||operation.equals("equipment-use-tick")){
            accepted=anchor==LivingEntity.class&&caller!=null;
            if(operation.equals("equipment-consume"))accepted&=named(entry,"completeUsingItem","m_8095_")&&caller.getDeclaringClass()==LivingEntity.class&&named(caller,"updateUsingItem","m_142106_","updatingUsingItem","m_21329_");
            else if(operation.equals("equipment-release"))accepted&=named(entry,"releaseUsingItem","m_21253_")&&(caller.getDeclaringClass()==ServerGamePacketListenerImpl.class&&named(caller,"handlePlayerAction","m_7502_")||caller.getDeclaringClass()==LivingEntity.class&&named(caller,"completeUsingItem","m_8095_"));
            else accepted&=named(entry,"updateUsingItem","m_142106_")&&caller.getDeclaringClass()==LivingEntity.class&&named(caller,"updatingUsingItem","m_21329_");
            used=((Access.Living)entity).pro$useItem();mask=useHand(entity);accepted&=used!=null&&used!=ItemStack.EMPTY&&mask!=0&&(shares(owner,used)&mask)!=0;
            if(operation.equals("equipment-use-tick"))accepted&=args.length>0&&args[0]==used;
        }else if(operation.equals("equipment-pickup"))accepted=anchor==Mob.class&&named(entry,"pickUpItem","m_7581_")&&caller!=null&&caller.getDeclaringClass()==Mob.class&&named(caller,"aiStep","m_8107_");
        else if(operation.equals("equipment-load-root"))accepted=anchor==Entity.class&&named(entry,"load","m_20258_")&&ProRuntime.liveStateLifecycleAllowed(entity);
        else if(operation.equals("equipment-load")){
            accepted=Set.of(Mob.class,Player.class,ArmorStand.class).contains(anchor)&&named(entry,"readAdditionalSaveData","m_7378_")
                    &&(ProRuntime.liveStateLifecycleAllowed(entity)||hasLoadRoot(owner));
        }
        if(!accepted||!vanilla(entry))return null;
        Scope previous=SCOPES.get(Thread.currentThread());
        for(Scope prior=previous;prior!=null;prior=prior.previous)if(!prior.closed&&prior.owner==owner&&prior.anchor==anchor&&prior.method.equals(entry.getMethodName()))return null;
        int occurrences=0;for(var frame:frames)if(sameAnchor(frame,entry.getDeclaringClass(),entry.getMethodName(),entry.getDescriptor()))occurrences++;
        Scope scope=new Scope(owner,operation,entry,previous,mask,used,menu,occurrences);scope.intent=actionIntent;scope.dig=dig;scope.attacked=new WeakReference<>(attacked);
        if(dig==null&&(!operation.equals("equipment-player-action")||actionIntent.action!=ServerboundPlayerActionPacket.Action.STOP_DESTROY_BLOCK))cancelMining(owner);
        SCOPES.put(Thread.currentThread(),scope);return scope;
    }
    private static int useHand(LivingEntity entity){
        var item=((Access.Data)((Access.EntityState)entity).pro$data()).pro$item(Access.Living.pro$useFlags());
        Byte flags=item==null?null:item.getValue();return flags==null?0:(flags&2)==0?1:2;
    }
    private static boolean sameAnchor(StackWalker.StackFrame frame,Class<?> type,String method,String descriptor){return frame.getDeclaringClass()==type&&frame.getMethodName().equals(method)&&frame.getDescriptor().equals(descriptor);}
    private static boolean packetCaller(StackWalker.StackFrame caller){return caller!=null&&vanilla(caller)&&caller.getDeclaringClass().getName().startsWith("net.minecraft.network.protocol.game.Serverbound")&&named(caller,"handle","m_5797_");}
    private static boolean hasLoadRoot(Owner owner){for(Scope scope=SCOPES.get(Thread.currentThread());scope!=null;scope=scope.previous)if(!scope.closed&&scope.owner==owner&&scope.operation.equals("equipment-load-root"))return true;return false;}
    public static boolean anchorDenied(Object receiver,String operation,Object token){
        if(!initialized)return false;LivingEntity entity=actionEntity(receiver,new Object[0]);
        if(entity==null&&token instanceof Scope scope)entity=scope.owner.entity.get();
        Owner owner=entity==null?null:OWNERS.get(entity);if(!active(owner))return false;
        return !(token instanceof Scope scope)||scope.owner!=owner||scope.closed||scope.thread.get()!=Thread.currentThread()||scope!=SCOPES.get(Thread.currentThread())||!scope.operation.equals(operation);
    }
    public static void endAction(Object token){closeAction(token,true);}
    private static void closeAction(Object token,boolean success){
        if(!(token instanceof Scope scope)||scope.closed)return;
        if(scope.thread.get()!=Thread.currentThread()||SCOPES.get(Thread.currentThread())!=scope)throw new IllegalMonitorStateException("EQUIPMENT_ACTION_SCOPE_ORDER");
        try{if(!success)cancelMining(scope.owner);finishDig(scope,success);if(active(scope.owner))refresh(scope.owner);}catch(RuntimeException|Error failure){cancelMining(scope.owner);throw failure;}
        finally{scope.closed=true;if(scope.previous==null)SCOPES.remove(Thread.currentThread());else SCOPES.put(Thread.currentThread(),scope.previous);}
    }
    public static void failedAction(Object token,Throwable failure){try{closeAction(token,false);}catch(RuntimeException|Error cleanup){if(cleanup!=failure)failure.addSuppressed(cleanup);}}
    private static boolean infrastructure(Class<?> type){
        if(type.getClassLoader()==EquipmentPolicy.class.getClassLoader()&&type.getModule()==EquipmentPolicy.class.getModule()&&type.getName().startsWith("dev.ronova.pro."))return true;
        if(type.getClassLoader()!=null)return false;String name=type.getNestHost().getName();
        return name.startsWith("dev.ronova.pro.bootstrap.")||name.equals("java.lang.reflect.Field")||name.startsWith("jdk.internal.reflect.")||name.equals("sun.misc.Unsafe")||name.equals("jdk.internal.misc.Unsafe")
                ||Set.of("java.util.HashMap","java.util.LinkedHashMap","java.util.AbstractMap","java.util.ArrayList","java.util.AbstractList","java.util.AbstractCollection","java.util.Arrays","java.util.Collections").contains(name)||type==ARRAY_LIST;
    }
    private static boolean gameFrame(StackWalker.StackFrame frame,Scope scope){
        Class<?> type=frame.getDeclaringClass();String name=frame.getMethodName();
        if(Item.class.isAssignableFrom(type)){
            return scope.itemType!=null&&type.isAssignableFrom(scope.itemType)&&itemActionMethod(frame);
        }
        if(ownedNbtRedirect(frame))return true;
        if(!vanilla(frame)){
            if(type==net.minecraftforge.common.extensions.IForgeItem.class)return itemActionMethod(frame)||names(name,"getDamage","getMaxDamage")&&frame.getDescriptor().equals("(Lnet/minecraft/world/item/ItemStack;)I");
            if(type==net.minecraftforge.common.extensions.IForgeItemStack.class)return names(name,"onBlockStartBreak")&&frame.getDescriptor().equals("(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/entity/player/Player;)Z");
            return false;
        }
        if(type==NonNullList.class||type.getPackageName().equals("net.minecraft.nbt"))return true;
        if(type==ItemStack.class)return !name.contains("$")||name.startsWith("lambda$onItemUse")||name.startsWith("lambda$useOn");
        if(type==Inventory.class)return !name.contains("$")||name.startsWith("lambda$hurtArmor");
        if(type==ContainerHelper.class)return names(name,"takeItem","m_18966_","removeItem","m_18969_","clearOrCountMatchingItems","m_18961_","m_18956_");
        if(type.getPackageName().equals("net.minecraft.world.inventory"))return !name.contains("$")||name.startsWith("lambda$doClick");
        if(type==LivingEntity.class)return names(name,"setItemInHand","m_21008_","completeUsingItem","m_8095_","releaseUsingItem","m_21253_","updateUsingItem","m_142106_","updatingUsingItem","m_21329_","eat","m_5584_");
        if(type==Player.class)return names(name,"setItemSlot","m_8061_","drop","m_36176_","m_7197_","dropEquipment","m_5907_","readAdditionalSaveData","m_7378_","eat","m_5584_","attack","m_5706_","hurtCurrentlyUsedShield","m_7909_");
        if(type==Mob.class)return names(name,"setItemSlot","m_8061_","equipItemIfPossible","m_255207_","setItemSlotAndDropWhenKilled","m_21468_","pickUpItem","m_7581_","readAdditionalSaveData","m_7378_");
        if(type==ArmorStand.class)return names(name,"setItemSlot","m_8061_","swapItem","m_31588_","readAdditionalSaveData","m_7378_");
        if(type==Entity.class)return names(name,"load","m_20258_","restoreFrom","m_20361_");
        if(type==ServerPlayer.class)return names(name,"restoreFrom","m_9015_","drop","m_7197_","m_182294_","readAdditionalSaveData","m_7378_","attack","m_5706_");
        if(type.getName().equals("net.minecraft.server.network.ServerGamePacketListenerImpl$1"))return named(frame,"onAttack","m_141994_");
        if(type==ServerPlayerGameMode.class||type==ServerGamePacketListenerImpl.class)return !name.contains("$");
        return false;
    }
    private static boolean itemActionMethod(StackWalker.StackFrame frame){
        String name=frame.getMethodName(),descriptor=frame.getDescriptor();
        if(names(name,"use","m_7203_"))return descriptor.equals("(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/world/InteractionHand;)Lnet/minecraft/world/InteractionResultHolder;");
        if(names(name,"useOn","m_6225_"))return descriptor.equals("(Lnet/minecraft/world/item/context/UseOnContext;)Lnet/minecraft/world/InteractionResult;");
        if(names(name,"finishUsingItem","m_5922_"))return descriptor.equals("(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/level/Level;Lnet/minecraft/world/entity/LivingEntity;)Lnet/minecraft/world/item/ItemStack;");
        if(names(name,"releaseUsing","m_5551_"))return descriptor.equals("(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/level/Level;Lnet/minecraft/world/entity/LivingEntity;I)V");
        if(names(name,"onUseTick","m_5929_"))return descriptor.equals("(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/item/ItemStack;I)V");
        if(name.equals("onItemUseFirst"))return descriptor.equals("(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/context/UseOnContext;)Lnet/minecraft/world/InteractionResult;");
        if(name.equals("onBlockStartBreak"))return descriptor.equals("(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/entity/player/Player;)Z");
        if(name.equals("setDamage"))return descriptor.equals("(Lnet/minecraft/world/item/ItemStack;I)V");
        if(name.equals("damageItem"))return descriptor.equals("(Lnet/minecraft/world/item/ItemStack;ILnet/minecraft/world/entity/LivingEntity;Ljava/util/function/Consumer;)I");
        if(names(name,"mineBlock","m_6813_"))return descriptor.equals("(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/level/Level;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/entity/LivingEntity;)Z");
        return names(name,"hurtEnemy","m_7579_")&&descriptor.equals("(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/entity/LivingEntity;)Z");
    }
    private static boolean ownedNbtRedirect(StackWalker.StackFrame frame){
        String name=frame.getMethodName(),descriptor=frame.getDescriptor();Class<?> type=frame.getDeclaringClass();
        return type==CompoundTag.class&&name.endsWith("$mutation")&&descriptor.equals("(Ljava/util/Map;Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;")
                ||type==ListTag.class&&(name.endsWith("$set")&&descriptor.equals("(Ljava/util/List;ILjava/lang/Object;)Ljava/lang/Object;")
                        ||name.endsWith("$add")&&descriptor.equals("(Ljava/util/List;ILjava/lang/Object;)V"));
    }
    private static boolean permitted(Owner owner,int mask,ItemStack stack,boolean entryQuery){
        if(!active(owner))return false;LivingEntity entity=owner.entity.get();
        if(ProRuntime.liveStateLifecycleAllowed(entity))return true;
        for(Scope scope=SCOPES.get(Thread.currentThread());scope!=null;scope=scope.previous){
            if(scope.closed||scope.owner!=owner||scope.thread.get()!=Thread.currentThread()||(scope.mask&mask)!=mask)continue;
            if(scope.intent!=null&&!currentIntent(owner,scope.intent,scope.operation.equals("equipment-attack"))||scope.dig!=null&&!digContext(scope))continue;
            if(scope.operation.equals("equipment-attack")&&(scope.attacked.get()==null||scope.attacked.get()!=scope.intent.target.get()||main(owner)!=scope.used.get()))continue;
            if(mask==0&&!Set.of("equipment-select","equipment-menu","equipment-pick","equipment-creative","equipment-load","equipment-load-root").contains(scope.operation))continue;
            if(scope.used.get()!=null&&scope.used.get()!=stack)continue;
            if(scope.menu.get()!=null&&(!(entity instanceof Player player)||player.containerMenu!=scope.menu.get()))continue;
            if(!sourceAllowed(scope))continue;
            Scope selected=scope;
            boolean path=WALKER.walk(frames->{var actual=frames.toList();int occurrences=0;for(var frame:actual)if(sameAnchor(frame,selected.anchor,selected.method,selected.descriptor))occurrences++;
                if(occurrences!=selected.occurrences)return false;boolean first=true;for(var frame:actual){
                if(frame.getDeclaringClass()==selected.anchor&&frame.getMethodName().equals(selected.method)&&frame.getDescriptor().equals(selected.descriptor))return true;
                if(infrastructure(frame.getDeclaringClass()))continue;
                if(first&&entryQuery&&frame.getMethodName().contains("$ronova$equipment")){first=false;continue;}
                first=false;if(!gameFrame(frame,selected))return false;
            }return false;});
            if(path)return true;
        }return false;
    }
    private static boolean permitStack(Link link,boolean entry){ItemStack stack=link.stack.get();return permitted(link.owner,shares(link.owner,stack),stack,entry);}
    private static boolean anySlotDenied(Owner owner,boolean entry){
        for(int part=0;part<(owner.entity.get() instanceof Player?3:2);part++){
            Object[] values=(Object[])array(backing(list(owner,part)));if(values==null)continue;
            for(int i=0;i<values.length;i++){int mask=bit(owner,part,i);if(mask!=0&&!permitted(owner,mask,values[i] instanceof ItemStack stack?stack:null,entry))return true;}
        }return false;
    }
    public static boolean listWriteDenied(Object receiver,int index,Object next){
        if(!initialized)return false;
        for(Link link:links(receiver))if((link.kind==Kind.EQUIPMENT_LIST||link.kind==Kind.EQUIPMENT_BACKING||link.kind==Kind.EQUIPMENT_ARRAY)&&current(link,receiver)){
            Object[] values=(Object[])array(backing(list(link.owner,link.part)));if(index<0||index>=values.length)return false;
            if(values[index]==next)continue;int mask=bit(link.owner,link.part,index);if(mask==0)continue;
            if(!(next instanceof ItemStack))return true;
            if(!permitted(link.owner,mask,values[index] instanceof ItemStack stack?stack:null,true))return true;
            if((mask&1)!=0)cancelMining(link.owner);
            if(next instanceof ItemStack stack)captureStack(link.owner,stack);
        }
        return false;
    }
    public static boolean itemSlotDenied(LivingEntity entity,EquipmentSlot slot,ItemStack next){
        if(!initialized||slot==null||next==null)return false;Owner owner=OWNERS.get(entity);if(!active(owner))return false;
        int mask=slot==EquipmentSlot.MAINHAND?1:slot==EquipmentSlot.OFFHAND?2:1<<(slot.getIndex()+2);
        int part=slot.getType()==EquipmentSlot.Type.ARMOR?1:entity instanceof Player&&slot==EquipmentSlot.OFFHAND?2:0;
        int index=slot.getType()==EquipmentSlot.Type.ARMOR?slot.getIndex():entity instanceof Player?slot==EquipmentSlot.OFFHAND?0:((Access.InventoryState)inventory(owner)).pro$selected():slot.getIndex();
        ItemStack current=itemAt(owner,part,index);if(current==next)return false;
        if(!permitted(owner,mask,current,true))return true;if((mask&1)!=0)cancelMining(owner);captureStack(owner,next);return false;
    }
    public static boolean stackEntryDenied(ItemStack stack,String operation,Object[] args){
        if(!initialized||stack==ItemStack.EMPTY)return false;
        for(Link link:links(stack))if(link.kind==Kind.STACK&&current(link,stack)&&!permitStack(link,true))return true;
        return false;
    }
    public static boolean inventoryEntryDenied(Inventory inventory,String operation,Object[] args){
        if(!initialized)return false;
        for(Link link:links(inventory))if(link.kind==Kind.INVENTORY&&current(link,inventory)){
            if(names(operation,"removeFromSelected","m_182403_")){
                int index=((Access.InventoryState)inventory).pro$selected();
                if(!permitted(link.owner,1,itemAt(link.owner,0,index),true))return true;continue;
            }
            if(args.length>0&&args[0] instanceof Integer index&&names(operation,"setItem","m_6836_","removeItem","m_7407_","removeItemNoUpdate","m_8016_")){
                int part=index<36?0:index<40?1:2,at=part==0?index:part==1?index-36:index-40;
                if(part>2||index<0||index>40)continue;int mask=bit(link.owner,part,at);if(mask==0)continue;
                if(names(operation,"setItem","m_6836_")&&args.length>1&&args[1]==itemAt(link.owner,part,at))continue;
                if(!permitted(link.owner,mask,itemAt(link.owner,part,at),true))return true;
            }else if(args.length>0&&args[0] instanceof ItemStack stack&&names(operation,"removeItem","m_36057_")){
                int mask=shares(link.owner,stack);if(mask!=0&&!permitted(link.owner,mask,stack,true))return true;
            }else if(anySlotDenied(link.owner,true))return true;
        }return false;
    }
    public static boolean takeDenied(Object list,int index,int amount){
        if(amount<=0)return false;
        for(Link link:links(list))if((link.kind==Kind.EQUIPMENT_LIST||link.kind==Kind.EQUIPMENT_BACKING)&&current(link,list)){
            int mask=bit(link.owner,link.part,index);if(mask!=0&&!permitted(link.owner,mask,itemAt(link.owner,link.part,index),true))return true;
        }return false;
    }
    static boolean dispatchDenied(Object receiver,String operation,Object payload){
        Object[] args=payload instanceof Object[] values?values:new Object[]{payload};
        if(receiver instanceof LivingEntity entity&&names(operation,"setItemSlot","m_8061_")&&args.length>1&&args[0] instanceof EquipmentSlot slot&&args[1] instanceof ItemStack stack)return itemSlotDenied(entity,slot,stack);
        if(receiver instanceof LivingEntity entity&&names(operation,"setItemInHand","m_21008_")&&args.length>1&&args[0] instanceof InteractionHand hand&&args[1] instanceof ItemStack stack)return itemSlotDenied(entity,hand==InteractionHand.MAIN_HAND?EquipmentSlot.MAINHAND:EquipmentSlot.OFFHAND,stack);
        if(receiver instanceof ItemStack stack)return stackEntryDenied(stack,operation,args);
        if(receiver instanceof Inventory inventory)return inventoryEntryDenied(inventory,operation,args);
        if(receiver==ContainerHelper.class&&args.length>1&&args[1] instanceof Integer index)return takeDenied(args[0],index,args.length>2&&args[2] instanceof Integer amount?amount:1);
        if(receiver!=null&&receiver.getClass()==ARRAY_LIST){
            if(operation.equals("set")&&args.length>1&&args[0] instanceof Integer index){
                if(listWriteDenied(receiver,index,args[1]))return true;
                Object[] values=(Object[])array(receiver);return index>=0&&index<values.length&&elementDenied(receiver,values[index],args[1]);
            }
            for(Link link:links(receiver))if(current(link,receiver)){
                if(link.kind==Kind.NBT)return true;
                if(link.kind==Kind.EQUIPMENT_BACKING&&anySlotDenied(link.owner,true))return true;
            }
        }
        return false;
    }
    public static Object deniedResult(Object receiver,String operation,Object payload){
        Object[] args=payload instanceof Object[] values?values:new Object[]{payload};
        if(receiver!=null&&receiver.getClass()==ARRAY_LIST&&operation.equals("set")&&args.length>0&&args[0] instanceof Integer index){Object[] values=(Object[])array(receiver);return values[index];}
        return ItemStack.EMPTY;
    }
    static boolean elementDenied(Object container,Object old,Object next){
        if(old==next&&old!=null)return false;
        for(Link link:links(container))if(current(link,container)){
            if(link.kind==Kind.NBT){if(!permitStack(link,false))return true;cancelToolMining(link);if(next!=null)walk(link.owner,link.stack.get(),null,true,next);}
            else if(link.kind==Kind.COMPARTMENTS||link.kind==Kind.COMPARTMENT_ARRAY)return true;
            else if(link.kind==Kind.EQUIPMENT_LIST||link.kind==Kind.EQUIPMENT_BACKING){
                if(old instanceof ItemStack stack&&stack!=ItemStack.EMPTY){int mask=shares(link.owner,stack);if(mask!=0&&!permitted(link.owner,mask,stack,false))return true;if((mask&1)!=0)cancelMining(link.owner);}
            }
        }return false;
    }
    static boolean arrayDenied(Object array,int index,Object next){
        if(array==null||!array.getClass().isArray()||index<0||index>=Array.getLength(array))return false;
        Object old=Array.get(array,index);if(array.getClass().getComponentType().isPrimitive()?Objects.equals(old,next):old==next)return false;
        if(listWriteDenied(array,index,next))return true;
        return elementDenied(array,old,next);
    }
    static boolean fieldDenied(Object receiver,Class<?> declaring,String name,Object next){
        if(!initialized||receiver==null||declaring==null)return false;Slot selected=null;
        for(Slot slot:slots(receiver))if(slot.field.getDeclaringClass()==declaring&&slot.field.getName().equals(name)){selected=slot;break;}
        if(selected==null)return false;
        Object old;try{old=selected.field.get(receiver);}catch(IllegalAccessException failure){throw new IllegalStateException("EQUIPMENT_FIELD_READ",failure);}
        if(selected.field.getType().isPrimitive()?Objects.equals(old,next):old==next)return false;
        for(Link link:links(receiver))if(current(link,receiver)){
            if(link.kind==Kind.GAME_MODE){if(gameFieldDenied(link.owner,(ServerPlayerGameMode)receiver,name,next))return true;continue;}
            if(link.kind==Kind.BODY||link.kind==Kind.INVENTORY||link.kind==Kind.EQUIPMENT_LIST||link.kind==Kind.EQUIPMENT_BACKING||link.kind==Kind.COMPARTMENTS){if(structureFieldDenied(link,declaring,name,next))return true;continue;}
            if(link.kind==Kind.STACK){
                if(names(name,"item","f_41589_","delegate"))return true;
                if(!permitStack(link,false))return true;
                if(names(name,"count","f_41587_")&&(!(next instanceof Integer count)||count<0))return true;
                cancelToolMining(link);
                if(next instanceof Tag)walk(link.owner,link.stack.get(),null,true,next);
            }else if(link.kind==Kind.NBT){
                // Numeric/string tags are immutable and can be VM-wide cached values. Legal edits replace their map/list entry.
                if(receiver instanceof Tag&&!(receiver instanceof CompoundTag||receiver instanceof CollectionTag<?>))return true;
                if(receiver instanceof CompoundTag||receiver instanceof ListTag&&selected.field.getType()==List.class)return true;
                if(NODE.isInstance(receiver)&&names(name,"key","hash"))return true;
                if(NODE.isInstance(receiver)&&name.equals("value")&&!(next instanceof Tag))return true;
                if(!permitStack(link,false))return true;
                cancelToolMining(link);
                if(next!=null&&!selected.field.getType().isPrimitive()&&!(next instanceof String))walk(link.owner,link.stack.get(),null,true,next);
            }
        }return false;
    }
    private static void cancelToolMining(Link link){
        Mining mining=MINING.get(link.owner.entity.get());if(mining!=null&&mining.tool.get()==link.stack.get())cancelMining(link.owner);
    }
    /** GameMode state never inherits the current Item's equipment delegation. */
    private static Scope gameWriteScope(Owner owner,ServerPlayerGameMode game){
        if(!gameOwned(owner,game)||!sourceAllowed())return null;
        for(Scope scope=SCOPES.get(Thread.currentThread());scope!=null;scope=scope.previous){
            if(scope.owner!=owner||scope.closed||scope.thread.get()!=Thread.currentThread()||scope.dig==null||scope.dig.game.get()!=game)continue;
            boolean context=digContext(scope);
            if(!context&&scope.operation.equals("equipment-dig-tick")){
                Access.EquipmentGameMode raw=(Access.EquipmentGameMode)game;
                context=driven(owner,game)&&raw.pro$gameType()==scope.dig.before.mode&&(raw.pro$gameTicks()==scope.dig.before.ticks||raw.pro$gameTicks()==scope.dig.before.ticks+1);
            }
            if(!context)continue;Scope selected=scope;
            boolean path=WALKER.walk(stream->{List<StackWalker.StackFrame> frames=stream.toList();int occurrences=0;
                for(var frame:frames)if(sameAnchor(frame,selected.anchor,selected.method,selected.descriptor))occurrences++;
                if(occurrences!=selected.occurrences)return false;
                for(var frame:frames){
                    if(sameAnchor(frame,selected.anchor,selected.method,selected.descriptor))return true;
                    if(infrastructure(frame.getDeclaringClass()))continue;
                    if(frame.getDeclaringClass()!=ServerPlayerGameMode.class||!named(frame,"incrementDestroyProgress","m_9276_"))return false;
                }return false;});
            if(path)return scope;
        }return null;
    }
    private static boolean gameFieldDenied(Owner owner,ServerPlayerGameMode game,String name,Object next){
        if(names(name,"player","f_9245_"))return true;
        Scope scope=gameWriteScope(owner,game);if(scope==null)return true;DigFrame dig=scope.dig;
        if(names(name,"level","f_9244_"))return !scope.operation.equals("equipment-dig-level")||next!=dig.destination.get()||next==null||!ProRuntime.liveStateLifecycleAllowed(owner.entity.get());
        boolean tick=scope.operation.equals("equipment-dig-tick"),action=scope.operation.equals("equipment-dig-action");
        if(!tick&&!action)return true;
        if(names(name,"gameTicks","f_9252_"))return !tick||!(next instanceof Integer value)||value!=dig.before.ticks+1;
        if(names(name,"lastSentState","f_9256_"))return !(next instanceof Integer)||!(tick||dig.action==ServerboundPlayerActionPacket.Action.START_DESTROY_BLOCK);
        if(names(name,"isDestroyingBlock","f_9249_")){
            if(!(next instanceof Boolean value))return true;
            return value? !action||dig.action!=ServerboundPlayerActionPacket.Action.START_DESTROY_BLOCK:!(tick||action);
        }
        if(names(name,"hasDelayedDestroy","f_9253_"))return !(next instanceof Boolean value)||(value? !action||dig.action!=ServerboundPlayerActionPacket.Action.STOP_DESTROY_BLOCK||dig.continuation==null:!tick);
        if(names(name,"destroyProgressStart","f_9250_"))return !action||dig.action!=ServerboundPlayerActionPacket.Action.START_DESTROY_BLOCK||!(next instanceof Integer value)||value!=dig.before.ticks;
        if(names(name,"destroyPos","f_9251_"))return !action||dig.action!=ServerboundPlayerActionPacket.Action.START_DESTROY_BLOCK||!(next instanceof BlockPos position)||dig.target==null||!dig.target.matches(position);
        if(names(name,"delayedDestroyPos","f_9254_"))return !action||dig.action!=ServerboundPlayerActionPacket.Action.STOP_DESTROY_BLOCK||dig.continuation==null||!(next instanceof BlockPos position)||!dig.target.matches(position);
        if(names(name,"delayedTickStart","f_9255_"))return !action||dig.action!=ServerboundPlayerActionPacket.Action.STOP_DESTROY_BLOCK||dig.continuation==null||!(next instanceof Integer value)||value!=dig.start;
        return true;
    }
    private static boolean structureFieldDenied(Link link,Class<?> declaring,String name,Object next){
        if(link.kind==Kind.INVENTORY&&names(name,"selected","f_35977_")){
            if(!(next instanceof Integer index)||index<0||index>=9)return true;
            if(!permitted(link.owner,0,null,false))return true;
            cancelMining(link.owner);captureStack(link.owner,itemAt(link.owner,0,index));return false;
        }
        // Standard bodies never replace an inventory, fixed equipment list, its array or the immutable compartments tuple.
        return true;
    }
    private static boolean overlaps(long first,long length,long second,long width){return first>=0&&length>0&&second>=0&&width>0&&first<=Long.MAX_VALUE-length&&second<=Long.MAX_VALUE-width&&first<second+width&&second<first+length;}
    static boolean offsetDenied(Object receiver,long offset,Object next){
        if(!initialized||receiver==null)return false;int width=next==null||!(next instanceof Number||next instanceof Boolean||next instanceof Character)?REFERENCE_WIDTH:next instanceof Long||next instanceof Double?8:next instanceof Byte||next instanceof Boolean?1:next instanceof Short||next instanceof Character?2:4;
        for(Slot slot:slots(receiver))if(overlaps(offset,width,slot.offset,slot.width)){
            Object value=offset==slot.offset&&width==slot.width?next:null;
            if(slot.field.getType()==float.class&&value instanceof Integer bits)value=Float.intBitsToFloat(bits);
            if(slot.field.getType()==double.class&&value instanceof Long bits)value=Double.longBitsToDouble(bits);
            if(fieldDenied(receiver,slot.field.getDeclaringClass(),slot.field.getName(),value))return true;
        }return false;
    }
    static boolean memoryDenied(Object receiver,long offset,long length){
        if(!initialized||receiver==null||length<=0)return false;
        if(receiver.getClass().isArray()){
            for(Link link:links(receiver))if(current(link,receiver)){
                if(link.kind==Kind.EQUIPMENT_ARRAY||link.kind==Kind.COMPARTMENT_ARRAY)return true;
                if(link.kind==Kind.NBT){if(!permitStack(link,false))return true;cancelToolMining(link);}
            }return false;
        }
        for(Slot slot:slots(receiver))if(overlaps(offset,length,slot.offset,slot.width)&&fieldDenied(receiver,slot.field.getDeclaringClass(),slot.field.getName(),null))return true;
        return false;
    }
    static List<Field> unsafeFields(){if(!initialized)return List.of();List<Field> result=new ArrayList<>();for(var slots:FIELDS.values())for(Slot slot:slots)if(!result.contains(slot.field))result.add(slot.field);return result;}
    static void declareDispatchTargets(Class<?> agent)throws ReflectiveOperationException {
        Method declare=agent.getMethod("declareDispatchArgumentsTarget",Class.class,String.class,String.class);
        declare(declare,LivingEntity.class,new String[][]{{"setItemSlot","m_8061_","(Lnet/minecraft/world/entity/EquipmentSlot;Lnet/minecraft/world/item/ItemStack;)V"},{"setItemInHand","m_21008_","(Lnet/minecraft/world/InteractionHand;Lnet/minecraft/world/item/ItemStack;)V"}});
        declare(declare,ItemStack.class,new String[][]{{"setCount","m_41764_","(I)V"},{"grow","m_41769_","(I)V"},{"shrink","m_41774_","(I)V"},{"setTag","m_41751_","(Lnet/minecraft/nbt/CompoundTag;)V"},{"setDamageValue","m_41721_","(I)V"},{"split","m_41620_","(I)Lnet/minecraft/world/item/ItemStack;"},{"copyAndClear","m_278832_","()Lnet/minecraft/world/item/ItemStack;"},{"deserializeNBT","","(Lnet/minecraft/nbt/CompoundTag;)V"},{"hurtAndBreak","m_41622_","(ILnet/minecraft/world/entity/LivingEntity;Ljava/util/function/Consumer;)V"},{"hurt","m_220157_","(ILnet/minecraft/util/RandomSource;Lnet/minecraft/server/level/ServerPlayer;)Z"}});
        declare(declare,Inventory.class,new String[][]{{"setItem","m_6836_","(ILnet/minecraft/world/item/ItemStack;)V"},{"removeItem","m_7407_","(II)Lnet/minecraft/world/item/ItemStack;"},{"removeItemNoUpdate","m_8016_","(I)Lnet/minecraft/world/item/ItemStack;"},{"removeItem","m_36057_","(Lnet/minecraft/world/item/ItemStack;)V"},{"removeFromSelected","m_182403_","(Z)Lnet/minecraft/world/item/ItemStack;"},{"dropAll","m_36071_","()V"},{"clearContent","m_6211_","()V"},{"load","m_36035_","(Lnet/minecraft/nbt/ListTag;)V"},{"replaceWith","m_36006_","(Lnet/minecraft/world/entity/player/Inventory;)V"}});
        declare(declare,ContainerHelper.class,new String[][]{{"takeItem","m_18966_","(Ljava/util/List;I)Lnet/minecraft/world/item/ItemStack;"},{"removeItem","m_18969_","(Ljava/util/List;II)Lnet/minecraft/world/item/ItemStack;"}});
        declare(declare,ARRAY_LIST,new String[][]{{"set","","(ILjava/lang/Object;)Ljava/lang/Object;"},{"replaceAll","","(Ljava/util/function/UnaryOperator;)V"},{"sort","","(Ljava/util/Comparator;)V"}});
    }
    private static void declare(Method declare,Class<?> owner,String[][] entries)throws ReflectiveOperationException{
        for(String[] entry:entries)for(Method method:owner.getDeclaredMethods())if((method.getName().equals(entry[0])||!entry[1].isEmpty()&&method.getName().equals(entry[1]))
                &&MethodType.methodType(method.getReturnType(),method.getParameterTypes()).toMethodDescriptorString().equals(entry[2]))declare.invoke(null,owner,method.getName(),entry[2]);
    }
}
