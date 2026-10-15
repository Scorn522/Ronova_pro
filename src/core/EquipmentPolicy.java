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
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraftforge.common.capabilities.CapabilityProvider;
import net.minecraftforge.common.capabilities.CapabilityDispatcher;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.fluids.capability.templates.FluidHandlerItemStack;
import net.minecraftforge.fluids.capability.templates.FluidHandlerItemStackSimple;
import net.minecraftforge.fluids.capability.wrappers.FluidBucketWrapper;
import org.apache.commons.lang3.mutable.MutableObject;

/** Current equipment shares, rather than a snapshot of items the body once held. */
public final class EquipmentPolicy {
    private enum Kind { BODY, GAME_MODE, INVENTORY, COMPARTMENTS, COMPARTMENT_ARRAY, EQUIPMENT_LIST, EQUIPMENT_BACKING, EQUIPMENT_ARRAY, CURATIVE_LIST, CURATIVE_ARRAY, CURATIVE_CARRIER, STACK, NBT, CAPABILITY }
    private record Slot(Field field,long offset,int width) { }
    private static final class Owner {
        final WeakReference<LivingEntity> entity;final long generation;
        Owner(LivingEntity entity,long generation){this.entity=new WeakReference<>(entity);this.generation=generation;}
    }
    private static final class Link {
        final Owner owner;final Kind kind;final int part;final WeakReference<ItemStack> stack;final WeakReference<Object> root;
        Link(Owner owner,Kind kind,int part,ItemStack stack,Object root){this.owner=owner;this.kind=kind;this.part=part;this.stack=new WeakReference<>(stack);this.root=new WeakReference<>(root);}
    }
    private static final class Scope {
        final Owner owner;final String operation;final Class<?> anchor;final String method,descriptor;
        final WeakReference<Thread> thread=new WeakReference<>(Thread.currentThread());
        final WeakReference<ItemStack> used;final WeakReference<Object> menu;final Scope previous;final int mask,occurrences;final Class<?> itemType;
        final WeakReference<ServerPlayerGameMode> game;
        boolean closed;
        Intent intent;DigFrame dig;WeakReference<Entity> attacked;
        Scope(Owner owner,String operation,StackWalker.StackFrame frame,Scope previous,int mask,ItemStack used,Object menu,int occurrences,ServerPlayerGameMode game){
            this.owner=owner;this.operation=operation;anchor=frame.getDeclaringClass();method=frame.getMethodName();descriptor=frame.getDescriptor();
            this.previous=previous;this.mask=mask;this.used=new WeakReference<>(used);this.menu=new WeakReference<>(menu);this.occurrences=occurrences;this.itemType=itemType(used);
            this.game=new WeakReference<>(game);
        }
    }
    private static final class Invocation {
        final Scope action;final WeakReference<Object> receiver;final Object[] arguments;final String operation;
        final Class<?> anchor;final String method,descriptor;final int occurrences;final Invocation previous;final boolean opensAction;
        final WeakReference<Thread> thread=new WeakReference<>(Thread.currentThread());final WeakReference<ItemStack> stack;
        final int mask;boolean closed;
        Invocation(Scope action,Object receiver,Object[] arguments,String operation,StackWalker.StackFrame frame,int occurrences,Invocation previous,boolean opensAction,ItemStack stack,int mask){
            this.action=action;this.receiver=new WeakReference<>(receiver);this.arguments=arguments.clone();this.operation=operation;
            anchor=frame.getDeclaringClass();method=frame.getMethodName();descriptor=frame.getDescriptor();this.occurrences=occurrences;
            this.previous=previous;this.opensAction=opensAction;this.stack=new WeakReference<>(stack);this.mask=mask;
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
    private static Map<Thread,Invocation> INVOCATIONS;
    private static final Object INVOCATION_REFUSED=new Object();
    private static Map<Class<?>,List<Slot>> FIELDS;
    private static StackWalker WALKER;
    private static Class<?> ARRAY_LIST,NODE,IMMUTABLE_LIST,IMMUTABLE_N,IMMUTABLE_12,SINGLETON_LIST,EMPTY_LIST,GUAVA_SINGLETON;
    private static Field IMMUTABLE_VALUES,IMMUTABLE_FIRST,IMMUTABLE_SECOND,SINGLETON_VALUE,GUAVA_VALUES,GUAVA_ELEMENT;
    private static Object IMMUTABLE_EMPTY;
    private static Field ARRAY_VALUES,LIST_VALUES,LIST_SIZE,MAP_TABLE,NODE_NEXT,NODE_VALUE;
    private static Field INTERACT_ID,INTERACT_ACTION;private static Object ATTACK_ACTION;
    private static Field CONTEXT_PLAYER,CONTEXT_STACK;
    private static MethodHandle ACTION_SOURCE,ACTION_DELEGATE,CARRIER_SOURCE;
    private static Class<?> UNMODIFIABLE_COLLECTION,UNMODIFIABLE_LIST,UNMODIFIABLE_RANDOM;
    private static Field CAP_DISPATCHER,CAP_VALID,CAP_INITIALIZED,CAP_DATA;
    private static Field DISPATCH_PROVIDERS,DISPATCH_WRITERS,DISPATCH_NAMES,DISPATCH_LISTENERS;
    private static Field OPTIONAL_SUPPLIER,OPTIONAL_VALUE,OPTIONAL_LISTENERS,OPTIONAL_VALID,MUTABLE_VALUE,SET_MAP,UNMODIFIABLE_VALUES;
    private static Map<Thread,CapabilityScope> CAP_SCOPES;
    private static Map<Thread,CapabilityQuery> CAP_QUERIES;
    private static final class CapabilityQuery {
        final Object dispatcher,request,direction;final Thread thread=Thread.currentThread();final CapabilityQuery previous;
        final List<Link> related;final Class<?> anchor;final String method,descriptor;final List<StackWalker.StackFrame> tail;
        final List<EquipmentCarrierGraph.Observation> observed=new ArrayList<>();boolean closed;
        CapabilityQuery(Object dispatcher,Object request,Object direction,CapabilityQuery previous,List<Link> related,List<StackWalker.StackFrame> frames){
            this.dispatcher=dispatcher;this.request=request;this.direction=direction;this.previous=previous;this.related=related;
            var entry=frames.get(0);anchor=entry.getDeclaringClass();method=entry.getMethodName();descriptor=entry.getDescriptor();tail=List.copyOf(frames.subList(1,frames.size()));
        }
    }
    private static final class CapabilityScope {
        final Object receiver,next,listeners;final String phase;final Class<?> anchor;final String method,descriptor;
        final List<StackWalker.StackFrame> tail;final Thread thread=Thread.currentThread();final CapabilityScope previous;boolean closed;Object gate;
        CapabilityScope(Object receiver,Object next,Object listeners,String phase,StackWalker.StackFrame entry,List<StackWalker.StackFrame> tail,CapabilityScope previous){
            this.receiver=receiver;this.next=next;this.listeners=listeners;this.phase=phase;this.anchor=entry.getDeclaringClass();this.method=entry.getMethodName();this.descriptor=entry.getDescriptor();this.tail=tail;this.previous=previous;
        }
    }
    private static volatile int REFERENCE_WIDTH=8;
    private EquipmentPolicy(){ }

    static synchronized void initialize()throws ReflectiveOperationException {
        if(initialized)return;
        ARRAY_LIST=Class.forName("java.util.Arrays$ArrayList",false,null);
        NODE=Class.forName("java.util.HashMap$Node",false,null);
        Class<?> tree=Class.forName("java.util.HashMap$TreeNode",false,null);
        IMMUTABLE_LIST=Class.forName("com.google.common.collect.RegularImmutableList",false,EquipmentPolicy.class.getClassLoader());
        GUAVA_SINGLETON=Class.forName("com.google.common.collect.SingletonImmutableList",false,EquipmentPolicy.class.getClassLoader());
        IMMUTABLE_N=Class.forName("java.util.ImmutableCollections$ListN",false,null);
        IMMUTABLE_12=Class.forName("java.util.ImmutableCollections$List12",false,null);
        SINGLETON_LIST=Class.forName("java.util.Collections$SingletonList",false,null);
        EMPTY_LIST=Class.forName("java.util.Collections$EmptyList",false,null);
        UNMODIFIABLE_COLLECTION=Class.forName("java.util.Collections$UnmodifiableCollection",false,null);
        UNMODIFIABLE_LIST=Class.forName("java.util.Collections$UnmodifiableList",false,null);
        UNMODIFIABLE_RANDOM=Class.forName("java.util.Collections$UnmodifiableRandomAccessList",false,null);
        Class<?>[] carriers={Player.class,ServerPlayer.class,ServerPlayerGameMode.class,ServerboundInteractPacket.class,UseOnContext.class,Inventory.class,Mob.class,ArmorStand.class,NonNullList.class,ItemStack.class,
                CompoundTag.class,ListTag.class,ByteArrayTag.class,IntArrayTag.class,LongArrayTag.class,
                ByteTag.class,ShortTag.class,IntTag.class,LongTag.class,FloatTag.class,DoubleTag.class,StringTag.class,
                HashMap.class,LinkedHashMap.class,ArrayList.class,ARRAY_LIST,NODE,tree,IMMUTABLE_LIST,IMMUTABLE_N,IMMUTABLE_12,SINGLETON_LIST,EMPTY_LIST,GUAVA_SINGLETON};
        Class<?>[] capabilityCarriers={CapabilityProvider.class,CapabilityDispatcher.class,LazyOptional.class,MutableObject.class,HashSet.class,
                UNMODIFIABLE_COLLECTION,UNMODIFIABLE_LIST,UNMODIFIABLE_RANDOM,FluidBucketWrapper.class,
                FluidHandlerItemStack.class,FluidHandlerItemStack.Consumable.class,FluidHandlerItemStack.SwapEmpty.class,
                FluidHandlerItemStackSimple.class,FluidHandlerItemStackSimple.Consumable.class,FluidHandlerItemStackSimple.SwapEmpty.class};
        carriers=java.util.stream.Stream.concat(Arrays.stream(carriers),Arrays.stream(capabilityCarriers)).toArray(Class<?>[]::new);
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
        IMMUTABLE_VALUES=access(IMMUTABLE_N,"elements");IMMUTABLE_FIRST=access(IMMUTABLE_12,"e0");IMMUTABLE_SECOND=access(IMMUTABLE_12,"e1");
        SINGLETON_VALUE=access(SINGLETON_LIST,"element");GUAVA_VALUES=access(IMMUTABLE_LIST,"array");GUAVA_ELEMENT=access(GUAVA_SINGLETON,"element");
        IMMUTABLE_EMPTY=access(Class.forName("java.util.ImmutableCollections",false,null),"EMPTY").get(null);
        MAP_TABLE=access(HashMap.class,"table");NODE_NEXT=access(NODE,"next");NODE_VALUE=access(NODE,"value");
        INTERACT_ID=accessAny(ServerboundInteractPacket.class,"entityId","f_134030_");INTERACT_ACTION=accessAny(ServerboundInteractPacket.class,"action","f_134031_");
        ATTACK_ACTION=accessAny(ServerboundInteractPacket.class,"ATTACK_ACTION","f_179595_").get(null);
        CONTEXT_PLAYER=accessAny(UseOnContext.class,"player","f_43703_");CONTEXT_STACK=accessAny(UseOnContext.class,"itemStack","f_43707_");
        CAP_DISPATCHER=access(CapabilityProvider.class,"capabilities");CAP_VALID=access(CapabilityProvider.class,"valid");CAP_INITIALIZED=access(CapabilityProvider.class,"initialized");
        CAP_DATA=access(CapabilityProvider.class,"lazyData");
        DISPATCH_PROVIDERS=access(CapabilityDispatcher.class,"caps");DISPATCH_WRITERS=access(CapabilityDispatcher.class,"writers");DISPATCH_NAMES=access(CapabilityDispatcher.class,"names");DISPATCH_LISTENERS=access(CapabilityDispatcher.class,"listeners");
        OPTIONAL_SUPPLIER=access(LazyOptional.class,"supplier");OPTIONAL_VALUE=access(LazyOptional.class,"resolved");OPTIONAL_LISTENERS=access(LazyOptional.class,"listeners");OPTIONAL_VALID=access(LazyOptional.class,"isValid");
        MUTABLE_VALUE=access(MutableObject.class,"value");SET_MAP=access(HashSet.class,"map");UNMODIFIABLE_VALUES=access(UNMODIFIABLE_COLLECTION,"c");
        Class<?> bridge=Class.forName("dev.ronova.pro.bootstrap.ControlBridge",false,null);
        ACTION_SOURCE=MethodHandles.publicLookup().findStatic(bridge,"equipmentActionSourceAllowed",MethodType.methodType(boolean.class));
        ACTION_DELEGATE=MethodHandles.publicLookup().findStatic(bridge,"equipmentActionSourceAllowed",MethodType.methodType(boolean.class,Class.class,Class.class));
        CARRIER_SOURCE=MethodHandles.publicLookup().findStatic(bridge,"equipmentCarrierSourceAllowed",MethodType.methodType(boolean.class,Class.class,Class.class,Class[].class));
        EquipmentCarrierGraph.initialize();
        OWNERS=Collections.synchronizedMap(new WeakIdentityMap<>());CARRIERS=Collections.synchronizedMap(new WeakIdentityMap<>());
        SCOPES=Collections.synchronizedMap(new WeakIdentityMap<>());FIELDS=Map.copyOf(fields);
        MINING=Collections.synchronizedMap(new WeakIdentityMap<>());DRIVERS=Collections.synchronizedMap(new WeakIdentityMap<>());INVOCATIONS=Collections.synchronizedMap(new WeakIdentityMap<>());
        CAP_SCOPES=Collections.synchronizedMap(new WeakIdentityMap<>());
        CAP_QUERIES=Collections.synchronizedMap(new WeakIdentityMap<>());
        WALKER=StackWalker.getInstance(Set.of(StackWalker.Option.RETAIN_CLASS_REFERENCE,StackWalker.Option.SHOW_HIDDEN_FRAMES));initialized=true;
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
        if(type==CapabilityProvider.class)return names(name,"baseClass","capabilities","valid","isLazy","lazyParentSupplier","lazyData","initialized");
        if(type==CapabilityDispatcher.class||type==LazyOptional.class)return true;
        if(type==MutableObject.class)return name.equals("value");
        if(type==HashSet.class)return name.equals("map");
        if(type==UNMODIFIABLE_COLLECTION||type==UNMODIFIABLE_LIST||type==UNMODIFIABLE_RANDOM)return true;
        if(capabilityProviderType(type))return names(name,"holder","container","capacity","emptyContainer");
        if(type==HashMap.class)return Set.of("table","size","modCount","threshold","loadFactor").contains(name);
        if(type==LinkedHashMap.class)return Set.of("head","tail","accessOrder").contains(name);
        if(type==ArrayList.class)return names(name,"elementData","size");
        if(type==AbstractList.class)return name.equals("modCount");
        if(type==ARRAY_LIST||type==IMMUTABLE_LIST||type==IMMUTABLE_N||type==IMMUTABLE_12||type==SINGLETON_LIST||type==EMPTY_LIST||type==GUAVA_SINGLETON)return true;
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
    private static List<Slot> mutationSlots(Object value){
        boolean dynamic=false;for(Link link:links(value))if((link.kind==Kind.CAPABILITY||link.kind==Kind.NBT||link.kind==Kind.CURATIVE_CARRIER||link.kind==Kind.CURATIVE_LIST
                ||link.kind==Kind.STACK&&value.getClass()!=ItemStack.class)&&current(link,value)){dynamic=true;break;}
        if(!dynamic)return slots(value);
        List<Slot> result=new ArrayList<>(slots(value));
        for(EquipmentCarrierGraph.Slot actual:EquipmentCarrierGraph.fields(value)){
            boolean found=false;for(Slot prior:result)if(prior.field.equals(actual.field())){found=true;break;}
            if(!found)result.add(new Slot(actual.field(),actual.offset(),actual.width()));
        }return result;
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
        link(owner,value,kind,part,stack,stack);
    }
    private static void link(Owner owner,Object value,Kind kind,int part,ItemStack stack,Object root){
        if(value==null||value==ItemStack.EMPTY)return;
        synchronized(CARRIERS){
            List<Link> next=new ArrayList<>();boolean found=false;
            for(Link prior:CARRIERS.getOrDefault(value,List.of())){
                if(prior.owner!=owner&&!active(prior.owner))continue;
                if(prior.owner==owner&&prior.kind==kind&&prior.part==part&&prior.stack.get()==stack&&prior.root.get()==root)found=true;
                next.add(prior);
            }
            if(!found)next.add(new Link(owner,kind,part,stack,root));CARRIERS.put(value,List.copyOf(next));
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
            case CURATIVE_LIST -> LiveStatePolicy.curativeListCurrent(owner.entity.get(),owner.generation,value);
            case CURATIVE_ARRAY -> LiveStatePolicy.curativeArrayCurrent(owner.entity.get(),owner.generation,value);
            case CURATIVE_CARRIER -> link.root.get()!=null&&LiveStatePolicy.curativeListCurrent(owner.entity.get(),owner.generation,link.root.get())&&EquipmentCarrierGraph.curatives(link.root.get()).contains(value);
            case STACK -> stackCurrent(owner,link.stack.get())&&link.stack.get()==value;
            case NBT -> stackCurrent(owner,link.stack.get())&&walk(owner,link.stack.get(),value,false,null);
            case CAPABILITY -> stackCurrent(owner,link.stack.get())&&capabilityWalk(owner,link.stack.get(),value,false,null);
        };}catch(ReflectiveOperationException failure){throw new IllegalStateException("EQUIPMENT_CURRENT_CARRIER_ACCESS",failure);}
    }
    private static boolean curativeStack(Owner owner,ItemStack stack){return LiveStatePolicy.curativeStackCurrent(owner.entity.get(),owner.generation,stack);}
    private static boolean stackCurrent(Owner owner,ItemStack stack){return shares(owner,stack)!=0||curativeStack(owner,stack);}
    private static boolean compartmentArray(Owner owner,Object expected)throws IllegalAccessException{
        Inventory inventory=inventory(owner);if(inventory==null)return false;Object compartments=((Access.InventoryState)inventory).pro$compartments();
        for(Slot slot:slots(compartments))if(slot.field.getType().isArray()&&slot.field.get(compartments)==expected)return true;
        return false;
    }
    static boolean known(Object value){for(Link link:links(value))if(current(link,value))return true;return false;}
    /** The existing successful-store observer supplies this actual receiver. */
    static void carrierChanged(Object receiver){
        if(!initialized||receiver==null)return;
        for(Link link:links(receiver))if(active(link.owner)){
            ItemStack root=link.stack.get();if(root==null||!stackCurrent(link.owner,root))continue;
            walk(link.owner,root,null,true,null);capabilityWalk(link.owner,root,null,true,null);
        }
    }
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
    private static void captureStack(Owner owner,ItemStack stack){if(stack!=null&&stack!=ItemStack.EMPTY){link(owner,stack,Kind.STACK,-1,stack);walk(owner,stack,null,true,null);capabilityWalk(owner,stack,null,true,null);}}
    record CurativeList(Object[] values,int size,boolean backed) { }
    static CurativeList knownCurativeLayout(Object list){
        if(!initialized||list==null)return null;Class<?> type=list.getClass();
        return type==ArrayList.class||type==ARRAY_LIST||type==IMMUTABLE_LIST||type==IMMUTABLE_N||type==IMMUTABLE_12||type==SINGLETON_LIST||type==EMPTY_LIST||type==GUAVA_SINGLETON?curativeLayout(list):null;
    }
    /** Use the same actual list storage and stack/NBT graph as equipped stacks. */
    static CurativeList curativeLayout(Object list){
        if(!initialized)throw new IllegalStateException("CURATIVE_GRAPH_NOT_INITIALIZED");
        try {
            Object values;int size;boolean backed=true;
            if(list!=null&&list.getClass()==ArrayList.class){values=LIST_VALUES.get(list);size=LIST_SIZE.getInt(list);}
            else if(list!=null&&list.getClass()==ARRAY_LIST){values=ARRAY_VALUES.get(list);size=values instanceof Object[] array?array.length:-1;}
            else if(list!=null&&list.getClass()==IMMUTABLE_N){values=IMMUTABLE_VALUES.get(list);size=values instanceof Object[] array?array.length:-1;}
            else if(list!=null&&list.getClass()==IMMUTABLE_LIST){values=GUAVA_VALUES.get(list);size=values instanceof Object[] array?array.length:-1;}
            else if(list!=null&&list.getClass()==IMMUTABLE_12){Object first=IMMUTABLE_FIRST.get(list),second=IMMUTABLE_SECOND.get(list);values=second==IMMUTABLE_EMPTY?new Object[]{first}:new Object[]{first,second};size=((Object[])values).length;backed=false;}
            else if(list!=null&&list.getClass()==SINGLETON_LIST){values=new Object[]{SINGLETON_VALUE.get(list)};size=1;backed=false;}
            else if(list!=null&&list.getClass()==GUAVA_SINGLETON){values=new Object[]{GUAVA_ELEMENT.get(list)};size=1;backed=false;}
            else if(list!=null&&list.getClass()==EMPTY_LIST){values=new Object[0];size=0;backed=false;}
            else throw new IllegalStateException("CURATIVE_LIST_IMPLEMENTATION_UNRESOLVED:"+(list==null?"null":list.getClass().getName()));
            if(!(values instanceof Object[] array)||size<0||size>array.length||size>MAX_GRAPH)throw new IllegalStateException("CURATIVE_LIST_STORAGE_UNRESOLVED");
            return new CurativeList(array,size,backed);
        }catch(IllegalAccessException failure){throw new IllegalStateException("CURATIVE_LIST_STORAGE_ACCESS",failure);}
    }
    static void captureCurativeItems(LivingEntity entity,long generation,Object list){
        if(list==null)return;EquipmentCarrierGraph.View graph=EquipmentCarrierGraph.curatives(list);
        if(entity==null||generation<=0||ProRuntime.liveStateGeneration(entity)!=generation)return;
        Owner owner=OWNERS.get(entity);
        if(owner==null||owner.generation!=generation){MINING.remove(entity);owner=new Owner(entity,generation);OWNERS.put(entity,owner);}
        link(owner,list,Kind.CURATIVE_LIST,-1,null,list);
        for(Object carrier:graph.nodes.keySet())if(carrier!=list)link(owner,carrier,Kind.CURATIVE_CARRIER,-1,null,list);
        for(ItemStack stack:graph.stacks())captureStack(owner,stack);
        EquipmentCarrierGraph.declare(graph);
    }
    /** Traverse only real NBT storage. A HashMap node belongs only while linked from the current table. */
    private static boolean walk(Owner owner,ItemStack stack,Object expected,boolean capture,Object proposed){
        if(stack==null||stack==ItemStack.EMPTY)return false;
        EquipmentCarrierGraph.View graph=proposed==null?EquipmentCarrierGraph.nbt(stack):EquipmentCarrierGraph.candidate(stack,proposed);
        if(capture){for(Object carrier:graph.nodes.keySet())link(owner,carrier,Kind.NBT,-1,stack);EquipmentCarrierGraph.declare(graph);}
        return expected!=null&&graph.contains(expected);
    }
    private static boolean capabilityProviderType(Class<?> type){
        return type==FluidBucketWrapper.class||type==FluidHandlerItemStack.class||type==FluidHandlerItemStack.Consumable.class||type==FluidHandlerItemStack.SwapEmpty.class
                ||type==FluidHandlerItemStackSimple.class||type==FluidHandlerItemStackSimple.Consumable.class||type==FluidHandlerItemStackSimple.SwapEmpty.class;
    }
    /** Current standard storage only. Provider callbacks, suppliers and listener objects are leaves. */
    private static boolean capabilityWalk(Owner owner,ItemStack stack,Object expected,boolean capture,Object proposed){
        if(stack==null||stack==ItemStack.EMPTY)return false;
        EquipmentCarrierGraph.View graph=proposed==null?EquipmentCarrierGraph.capabilities(stack):EquipmentCarrierGraph.candidate(stack,proposed);
        if(capture){for(Object carrier:graph.nodes.keySet())link(owner,carrier,Kind.CAPABILITY,-1,stack);EquipmentCarrierGraph.declare(graph);}
        return expected!=null&&graph.contains(expected);
    }
    private static EquipmentCarrierGraph.Node heldNode(Link link,Object receiver){
        return (link.kind==Kind.NBT?EquipmentCarrierGraph.nbt(link.stack.get()):EquipmentCarrierGraph.capabilities(link.stack.get())).node(receiver);
    }
    private static Object capRead(Field field,Object receiver){try{return field.get(receiver);}catch(IllegalAccessException failure){throw new IllegalStateException("EQUIPMENT_CAPABILITY_FIELD_ACCESS",failure);}}
    private static boolean capBool(Field field,Object receiver){return Boolean.TRUE.equals(capRead(field,receiver));}
    private static List<StackWalker.StackFrame> capabilityFrames(){return WALKER.walk(stream->stream.filter(frame->!infrastructure(frame.getDeclaringClass())).toList());}
    private static boolean capabilityEntry(StackWalker.StackFrame frame,String phase){
        Class<?> type=frame.getDeclaringClass();String name=frame.getMethodName(),descriptor=frame.getDescriptor();
        return phase.equals("publish")&&type==CapabilityProvider.class&&name.equals("doGatherCapabilities")&&descriptor.equals("(Lnet/minecraftforge/common/capabilities/ICapabilityProvider;)V")
                ||phase.equals("resolve")&&type==LazyOptional.class&&name.equals("getValue")&&descriptor.equals("()Ljava/lang/Object;")
                ||phase.equals("listen-add")&&type==LazyOptional.class&&name.equals("addListener")&&descriptor.equals("(Lnet/minecraftforge/common/util/NonNullConsumer;)V")
                ||phase.equals("listen-remove")&&type==LazyOptional.class&&name.equals("removeListener")&&descriptor.equals("(Lnet/minecraftforge/common/util/NonNullConsumer;)V")
                ||phase.equals("invalidate-provider")&&type==CapabilityProvider.class&&name.equals("invalidateCaps")&&descriptor.equals("()V")
                ||phase.equals("revive-provider")&&type==CapabilityProvider.class&&name.equals("reviveCaps")&&descriptor.equals("()V")
                ||phase.equals("invalidate-dispatcher")&&type==CapabilityDispatcher.class&&name.equals("invalidate")&&descriptor.equals("()V")
                ||phase.equals("invalidate-optional")&&type==LazyOptional.class&&name.equals("invalidate")&&descriptor.equals("()V");
    }
    private static boolean capabilityAnchor(CapabilityScope scope,boolean metadata){
        if(scope.closed||scope.thread!=Thread.currentThread())return false;
        var frames=capabilityFrames();int found=-1;
        for(int at=0;at<frames.size();at++)if(sameAnchor(frames.get(at),scope.anchor,scope.method,scope.descriptor)){
            if(found!=-1)return false;found=at;
        }
        if(found<0||frames.size()-found-1!=scope.tail.size())return false;
        for(int at=0;at<scope.tail.size();at++){var left=scope.tail.get(at);var right=frames.get(found+1+at);
            if(!sameAnchor(right,left.getDeclaringClass(),left.getMethodName(),left.getDescriptor())||right.getByteCodeIndex()!=left.getByteCodeIndex())return false;
        }
        if(metadata)for(int at=0;at<found;at++){
            Class<?> type=frames.get(at).getDeclaringClass();
            if(type!=HashSet.class&&type.getNestHost()!=HashMap.class&&type!=LazyOptional.class)return false;
        }
        return true;
    }
    /** Called only at the original body entry or after its supplier returned. */
    public static Object capabilityEnter(Object receiver,Object next,String phase){
        if(!initialized||receiver==null)return null;
        List<Link> selected=new ArrayList<>();for(Link link:links(receiver))if((link.kind==Kind.STACK||link.kind==Kind.CAPABILITY||link.kind==Kind.NBT)&&current(link,receiver))selected.add(link);
        if(selected.isEmpty())return null;
        if(receiver.getClass()==LazyOptional.class&&!capBool(OPTIONAL_VALID,receiver)&&(phase.equals("invalidate-optional")||phase.equals("listen-add")))return null;
        if(receiver instanceof CapabilityProvider<?> &&phase.equals("revive-provider")&&capBool(CAP_VALID,receiver))return null;
        var frames=capabilityFrames();if(frames.isEmpty()||!capabilityEntry(frames.get(0),phase)||!frames.get(0).getDeclaringClass().isInstance(receiver))throw new SecurityException("ACTUAL_EQUIPMENT_CAPABILITY_BODY_REQUIRED");
        boolean publish=phase.equals("publish"),resolve=phase.equals("resolve"),listen=phase.startsWith("listen-");
        Object listeners=receiver.getClass()==LazyOptional.class?capRead(OPTIONAL_LISTENERS,receiver):null;
        CapabilityScope previous=CAP_SCOPES.get(Thread.currentThread());
        for(CapabilityScope parent=previous;parent!=null;parent=parent.previous)if(parent.receiver==receiver&&parent.phase.equals(phase))throw new SecurityException("EQUIPMENT_CAPABILITY_REENTRY");
        CapabilityScope scope=new CapabilityScope(receiver,next,listeners,phase,frames.get(0),List.copyOf(frames.subList(1,frames.size())),previous);
        boolean installed=false;
        try{
            if(publish||resolve)scope.gate=ProRuntime.equipmentCapabilityGate(receiver);
            if(publish){
                if(!(receiver instanceof ItemStack)||capBool(CAP_INITIALIZED,receiver)||capRead(CAP_DISPATCHER,receiver)!=null||next!=null&&next.getClass()!=CapabilityDispatcher.class)
                    throw new SecurityException("EQUIPMENT_CAPABILITY_INITIAL_PUBLICATION_REQUIRED");
            }else if(resolve){
                if(receiver.getClass()!=LazyOptional.class||!capBool(OPTIONAL_VALID,receiver)||capRead(OPTIONAL_VALUE,receiver)!=null||next==null||next.getClass()!=MutableObject.class)
                    throw new SecurityException("EQUIPMENT_CAPABILITY_INITIAL_RESOLUTION_REQUIRED");
                // The candidate is the original supplier's actual MutableObject,
                // after the callback returned. No callback runs inside this lease.
                for(Link link:selected)if(heldNode(link,receiver)==null)throw new SecurityException("EQUIPMENT_CAPABILITY_CURRENT_HOLDER_REQUIRED");
            }else if(listen){
                if(receiver.getClass()!=LazyOptional.class||listeners==null||listeners.getClass()!=HashSet.class)
                    throw new SecurityException("EQUIPMENT_CAPABILITY_LISTENER_STORAGE_UNRESOLVED");
                Object map=capRead(SET_MAP,listeners);if(map==null||map.getClass()!=HashMap.class)throw new SecurityException("EQUIPMENT_CAPABILITY_LISTENER_STORAGE_UNRESOLVED");
                for(Link link:selected)if(!permitStack(link,false))throw new SecurityException("EQUIPMENT_CAPABILITY_WRITE_REJECTED");
            }else for(Link link:selected)if(!permitStack(link,false))throw new SecurityException("EQUIPMENT_CAPABILITY_WRITE_REJECTED");
            if(listeners!=null)for(Link link:links(listeners))if(current(link,listeners)&&heldNode(link,receiver)==null)
                throw new SecurityException("EQUIPMENT_CAPABILITY_SHARED_LISTENERS_REJECTED");
            CAP_SCOPES.put(Thread.currentThread(),scope);installed=true;return scope;
        }finally{if(!installed&&scope.gate!=null)capabilityGateExit(scope);}
    }
    private static void capabilityGateExit(CapabilityScope scope){
        Object gate=scope.gate;scope.gate=null;if(gate!=null)ProRuntime.equipmentCapabilityGateExit(gate);
    }
    public static void capabilityExit(Object token){closeCapability(token,true);}
    private static void closeCapability(Object token,boolean success){
        if(!(token instanceof CapabilityScope scope)||scope.closed)return;
        if(scope.thread!=Thread.currentThread()||CAP_SCOPES.get(Thread.currentThread())!=scope)throw new IllegalMonitorStateException("EQUIPMENT_CAPABILITY_SCOPE_ORDER");
        try{
            if(success&&(scope.phase.equals("publish")&&(!capBool(CAP_INITIALIZED,scope.receiver)||capRead(CAP_DISPATCHER,scope.receiver)!=scope.next)
                    ||scope.phase.equals("resolve")&&capRead(OPTIONAL_VALUE,scope.receiver)!=scope.next))throw new SecurityException("EQUIPMENT_CAPABILITY_PUBLICATION_WITHHELD");
            for(Link link:links(scope.receiver))if(current(link,scope.receiver)){ItemStack stack=link.stack.get();if(stack!=null){walk(link.owner,stack,null,true,null);capabilityWalk(link.owner,stack,null,true,null);}}
        }finally{scope.closed=true;if(scope.previous==null)CAP_SCOPES.remove(Thread.currentThread());else CAP_SCOPES.put(Thread.currentThread(),scope.previous);capabilityGateExit(scope);}
    }
    public static void capabilityFailed(Object token,Throwable failure){try{closeCapability(token,false);}catch(RuntimeException|Error cleanup){if(cleanup!=failure)failure.addSuppressed(cleanup);}}
    public static Object capabilityQueryEnter(Object dispatcher,Object request,Object direction){
        if(!initialized||dispatcher==null)return null;List<Link> related=new ArrayList<>();for(Link link:links(dispatcher))if(current(link,dispatcher))related.add(link);if(related.isEmpty())return null;
        List<StackWalker.StackFrame> frames=capabilityFrames();
        if(frames.isEmpty()||frames.get(0).getDeclaringClass()!=CapabilityDispatcher.class||!frames.get(0).getMethodName().equals("getCapability")
                ||!frames.get(0).getDescriptor().equals("(Lnet/minecraftforge/common/capabilities/Capability;Lnet/minecraft/core/Direction;)Lnet/minecraftforge/common/util/LazyOptional;"))
            throw new SecurityException("ACTUAL_CAPABILITY_QUERY_BODY_REQUIRED");
        CapabilityQuery previous=CAP_QUERIES.get(Thread.currentThread());
        for(CapabilityQuery prior=previous;prior!=null;prior=prior.previous)if(prior.dispatcher==dispatcher&&prior.request==request&&prior.direction==direction)throw new SecurityException("EQUIPMENT_CAPABILITY_QUERY_REENTRY");
        CapabilityQuery query=new CapabilityQuery(dispatcher,request,direction,previous,List.copyOf(related),frames);CAP_QUERIES.put(Thread.currentThread(),query);return query;
    }
    private static boolean queryPath(CapabilityQuery query){
        if(query.closed||query.thread!=Thread.currentThread()||CAP_QUERIES.get(Thread.currentThread())!=query)return false;
        List<StackWalker.StackFrame> frames=capabilityFrames();if(frames.isEmpty()||!sameAnchor(frames.get(0),query.anchor,query.method,query.descriptor)||frames.size()!=query.tail.size()+1)return false;
        for(int at=0;at<query.tail.size();at++){var expected=query.tail.get(at);var actual=frames.get(at+1);
            if(!sameAnchor(actual,expected.getDeclaringClass(),expected.getMethodName(),expected.getDescriptor())||actual.getByteCodeIndex()!=expected.getByteCodeIndex())return false;}
        return true;
    }
    private static void queryCapture(CapabilityQuery query){
        for(Link link:query.related)if(current(link,query.dispatcher)){
            ItemStack root=link.stack.get();if(root!=null){walk(link.owner,root,null,true,null);capabilityWalk(link.owner,root,null,true,null);}
            else if(link.root.get()!=null&&LiveStatePolicy.curativeListCurrent(link.owner.entity.get(),link.owner.generation,link.root.get()))captureCurativeItems(link.owner.entity.get(),link.owner.generation,link.root.get());
        }
    }
    public static void capabilityQueryObserved(Object token,Object provider,Object originalResult){
        if(token==null)return;if(!(token instanceof CapabilityQuery query)||!queryPath(query))throw new SecurityException("ACTUAL_CAPABILITY_QUERY_RETURN_REQUIRED");
        if(!EquipmentCarrierGraph.providerCurrent(query.dispatcher,provider))throw new SecurityException("CAPABILITY_QUERY_PROVIDER_DETACHED");
        boolean current=false;for(Link link:query.related)if(current(link,query.dispatcher)){current=true;break;}if(!current)return;
        EquipmentCarrierGraph.Observation observed=EquipmentCarrierGraph.observe(query.dispatcher,provider,query.request,query.direction,originalResult);
        if(observed!=null){query.observed.add(observed);queryCapture(query);}
    }
    public static void capabilityQueryExit(Object token,Object actualReturn){closeQuery(token,actualReturn,true);}
    private static void closeQuery(Object token,Object result,boolean success){
        if(!(token instanceof CapabilityQuery query)||query.closed)return;
        if(query.thread!=Thread.currentThread()||CAP_QUERIES.get(Thread.currentThread())!=query)throw new IllegalMonitorStateException("EQUIPMENT_CAPABILITY_QUERY_SCOPE_ORDER");
        EquipmentCarrierGraph.Observation retained=null;
        try{
            if(success){if(!queryPath(query))throw new SecurityException("ACTUAL_CAPABILITY_QUERY_EXIT_REQUIRED");
                EquipmentCarrierGraph.Observation chosen=null;for(EquipmentCarrierGraph.Observation observed:query.observed)if(observed.optional.get()==result)chosen=observed;
                if(chosen!=null){EquipmentCarrierGraph.retain(chosen);queryCapture(query);retained=chosen;}}
        }finally{
            for(EquipmentCarrierGraph.Observation observed:query.observed)if(observed!=retained)EquipmentCarrierGraph.forget(observed);
            query.closed=true;if(query.previous==null)CAP_QUERIES.remove(Thread.currentThread());else CAP_QUERIES.put(Thread.currentThread(),query.previous);
        }
    }
    public static void capabilityQueryFailed(Object token,Throwable failure){try{closeQuery(token,null,false);}catch(RuntimeException|Error cleanup){if(cleanup!=failure)failure.addSuppressed(cleanup);}}
    private static boolean listenerCarrier(CapabilityScope scope,Object target){
        Object set=scope.listeners;if(set==null||set.getClass()!=HashSet.class)return false;Object map=capRead(SET_MAP,set);if(map==null||map.getClass()!=HashMap.class)return false;
        if(target==set||target==map)return true;Object table=capRead(MAP_TABLE,map);if(target==table)return true;
        if(table instanceof Object[] heads){int count=0;for(Object node:heads)while(node!=null&&NODE.isInstance(node)&&count++<MAX_GRAPH){if(node==target)return true;node=capRead(NODE_NEXT,node);}}
        return false;
    }
    private static boolean capabilityListeners(Link link,Object target){
        CapabilityScope scope=CAP_SCOPES.get(Thread.currentThread());
        return scope!=null&&(scope.phase.startsWith("listen-")||scope.phase.equals("invalidate-optional"))&&capabilityAnchor(scope,true)
                &&heldNode(link,scope.receiver)!=null&&listenerCarrier(scope,target);
    }
    /** null means this is ordinary stack/NBT data rather than capability metadata. */
    private static Boolean capabilityField(Link link,Object receiver,Field field,Object next){
        CapabilityScope scope=CAP_SCOPES.get(Thread.currentThread());Class<?> declaring=field.getDeclaringClass();
        if(scope!=null&&scope.receiver==receiver&&capabilityAnchor(scope,true)){
            if(scope.phase.equals("publish")){
                if(field.equals(CAP_DISPATCHER)&&next==scope.next&&!capBool(CAP_INITIALIZED,receiver)&&capRead(CAP_DISPATCHER,receiver)==null)return true;
                if(field.equals(CAP_INITIALIZED)&&Boolean.TRUE.equals(next)&&!capBool(CAP_INITIALIZED,receiver)&&capRead(CAP_DISPATCHER,receiver)==scope.next)return true;
            }
            if(scope.phase.equals("resolve")&&field.equals(OPTIONAL_VALUE)&&next==scope.next&&capRead(OPTIONAL_VALUE,receiver)==null)return true;
            if((scope.phase.equals("invalidate-provider")||scope.phase.equals("revive-provider"))&&field.equals(CAP_VALID))return Objects.equals(next,scope.phase.equals("revive-provider"));
            if(scope.phase.equals("invalidate-optional")&&field.equals(OPTIONAL_VALID))return Boolean.FALSE.equals(next);
        }
        if(capabilityListeners(link,receiver))return declaring!=HashSet.class;
        if(declaring==CapabilityProvider.class){if(field.equals(CAP_DATA))return permitStack(link,false);return false;}
        EquipmentCarrierGraph.Node node=heldNode(link,receiver);
        if(node!=null){
            if(node.metadata())return false;
            if(receiver instanceof ItemStack&&names(field.getName(),"item","f_41589_","delegate"))return false;
            if(receiver instanceof ItemStack&&names(field.getName(),"count","f_41587_")&&(!(next instanceof Integer count)||count<0))return false;
            if(immutableTag(receiver))return false;
            if(field.getDeclaringClass()==CompoundTag.class||field.getDeclaringClass()==ListTag.class&&field.getType()==List.class)return false;
            if(NODE.isInstance(receiver)&&names(field.getName(),"key","hash"))return false;
            return node.data()&&permitStack(link,false);
        }
        if(declaring==CapabilityDispatcher.class||declaring==LazyOptional.class)return false;
        return null;
    }
    private static boolean immutableTag(Object receiver){Class<?> type=receiver.getClass();return type==ByteTag.class||type==ShortTag.class||type==IntTag.class||type==LongTag.class||type==FloatTag.class||type==DoubleTag.class||type==StringTag.class||type==EndTag.class;}
    private static boolean sourceAllowed(){try{return (boolean)ACTION_SOURCE.invokeExact();}catch(Throwable failure){throw new IllegalStateException("EQUIPMENT_ACTION_SOURCE_UNAVAILABLE",failure);}}
    private static boolean sourceAllowed(Class<?> game,Class<?> item){try{return (boolean)ACTION_DELEGATE.invokeExact(game,item);}catch(Throwable failure){throw new IllegalStateException("EQUIPMENT_DELEGATE_SOURCE_UNAVAILABLE",failure);}}
    private static boolean sourceAllowed(Scope scope){return sourceAllowed(scope,null,null);}
    private static boolean sourceAllowed(Scope scope,Class<?> receiver,Class<?> body){
        List<Class<?>> carriers=new ArrayList<>();if(receiver!=null)carriers.add(receiver);if(body!=null&&body!=receiver)carriers.add(body);
        for(Invocation invocation=INVOCATIONS.get(Thread.currentThread());invocation!=null;invocation=invocation.previous)
            if(invocation.action==scope&&invocation.operation.equals("carrier-call")&&invocationCurrent(invocation)){
                Object actual=invocation.receiver.get();if(actual!=null&&!carriers.contains(actual.getClass()))carriers.add(actual.getClass());
                if(!carriers.contains(invocation.anchor))carriers.add(invocation.anchor);
            }
        ServerPlayerGameMode game=scope.game.get();Class<?> gameType=game==null?null:game.getClass();
        if(carriers.isEmpty())return sourceAllowed(gameType,scope.itemType);
        try{return (boolean)CARRIER_SOURCE.invokeExact(gameType,scope.itemType,carriers.toArray(Class<?>[]::new));}catch(Throwable failure){throw new IllegalStateException("EQUIPMENT_CARRIER_SOURCE_UNAVAILABLE",failure);}
    }
    private static Item item(ItemStack stack){
        if(stack==null||stack==ItemStack.EMPTY)return null;Access.StackState state=(Access.StackState)(Object)stack;
        Object delegate=state.pro$delegate();Item item=delegate instanceof net.minecraft.core.Holder<?> holder&&holder.value() instanceof Item actual?actual:state.pro$item();
        return item;
    }
    private static Class<?> itemType(ItemStack stack){Item item=item(stack);return item==null?null:item.getClass();}
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
            if(args.length!=2||!(args[0] instanceof BlockPos position)||!(args[1] instanceof Boolean)||!gameParentCaller(caller,game,"equipment-destroy")
                    ||parent==null||parent.owner!=owner||parent.closed||!parent.operation.equals("equipment-destroy")||parent.dig==null||parent.dig.game.get()!=game||parent.dig.target==null||!parent.dig.target.matches(position)||!digContext(parent))return null;
            DigFrame prior=parent.dig;return new DigFrame(game,before,prior.target,prior.action,prior.tool.get(),prior.selected,prior.start,prior.continuation,prior.intent,null);
        }
        if(operation.equals("equipment-destroy")){
            if(args.length!=1||!(args[0] instanceof BlockPos position)||caller==null||!(gameParentCaller(caller,game,"equipment-dig-tick")||gameParentCaller(caller,game,"equipment-dig-action")
                    ||caller.getDeclaringClass()==ServerPlayerGameMode.class&&named(caller,"destroyAndAck","m_215116_")))return null;
            Scope parent=SCOPES.get(Thread.currentThread());
            if(parent==null||parent.owner!=owner||parent.closed||parent.dig==null||parent.dig.game.get()!=game||parent.dig.consumed||parent.dig.target==null||!parent.dig.target.matches(position)||!digContext(parent))return null;
            List<StackWalker.StackFrame> frames=invocationFrames();if(!scopePath(parent,frames.subList(1,frames.size())))return null;
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
    private static List<StackWalker.StackFrame> invocationFrames(){return WALKER.walk(stream->stream.filter(frame->!infrastructure(frame.getDeclaringClass())).toList());}
    private static boolean gameParentCaller(StackWalker.StackFrame caller,ServerPlayerGameMode game,String operation){
        Invocation invocation=INVOCATIONS.get(Thread.currentThread());
        return caller!=null&&invocation!=null&&invocation.receiver.get()==game&&invocation.operation.equals(operation)
                &&sameAnchor(caller,invocation.anchor,invocation.method,invocation.descriptor)&&invocationCurrent(invocation);
    }
    private static boolean sameArguments(Object[] first,Object[] second){
        if(first.length!=second.length)return false;
        for(int i=0;i<first.length;i++)if(first[i] instanceof Number||first[i] instanceof Boolean||first[i] instanceof Character){if(!Objects.equals(first[i],second[i]))return false;}else if(first[i]!=second[i])return false;
        return true;
    }
    private static boolean actionCurrent(Scope scope,ItemStack stack,int mask){
        if(scope==null||scope.closed||!active(scope.owner)||scope.thread.get()!=Thread.currentThread()||(scope.mask&mask)!=mask)return false;
        if(scope.game.get()!=null&&!gameOwned(scope.owner,scope.game.get()))return false;
        if(scope.used.get()!=null&&(scope.used.get()!=stack||itemType(stack)!=scope.itemType||(shares(scope.owner,stack)&mask)!=mask))return false;
        if(scope.intent!=null&&!currentIntent(scope.owner,scope.intent,scope.operation.equals("equipment-attack"))||scope.dig!=null&&!digContext(scope))return false;
        if(scope.operation.equals("equipment-attack")&&(scope.attacked.get()==null||scope.attacked.get()!=scope.intent.target.get()||main(scope.owner)!=stack))return false;
        if(Set.of("equipment-consume","equipment-release","equipment-use-tick").contains(scope.operation)
                &&(((Access.Living)scope.owner.entity.get()).pro$useItem()!=stack||useHand(scope.owner.entity.get())!=scope.mask))return false;
        return scope.menu.get()==null||scope.owner.entity.get() instanceof Player player&&player.containerMenu==scope.menu.get();
    }
    private static boolean invocationCurrent(Invocation invocation){
        if(invocation==null||invocation.closed||invocation.thread.get()!=Thread.currentThread())return false;
        Scope scope=invocation.action;
        if(invocation.operation.equals("carrier-call")){
            Object receiver=invocation.receiver.get();ItemStack root=invocation.stack.get();
            if(receiver==null||root==null||curativeStack(scope.owner,root)||!actionCurrent(scope,root,invocation.mask))return false;
            for(Link link:links(receiver))if(link.owner==scope.owner&&link.stack.get()==root&&(link.kind==Kind.CAPABILITY||link.kind==Kind.NBT||link.kind==Kind.STACK)&&current(link,receiver)){
                EquipmentCarrierGraph.Node node=heldNode(link,receiver);if(node!=null&&node.data()&&!node.metadata())return true;
            }return false;
        }
        if(invocation.receiver.get() instanceof ServerPlayerGameMode game){
            if(scope.game.get()!=game||!gameOwned(scope.owner,game)||scope.closed||scope.thread.get()!=Thread.currentThread())return false;
            if(actionCurrent(scope,scope.used.get(),scope.mask))return true;
            // The real player driver may advance only its clock while a level handoff is pending.
            if(!scope.operation.equals("equipment-dig-tick")||scope.dig==null||!driven(scope.owner,game))return false;
            Access.EquipmentGameMode raw=(Access.EquipmentGameMode)game;
            return raw.pro$gameType()==scope.dig.before.mode&&(raw.pro$gameTicks()==scope.dig.before.ticks||raw.pro$gameTicks()==scope.dig.before.ticks+1);
        }
        ItemStack stack=invocation.stack.get();
        return stack!=null&&item(stack)==invocation.receiver.get()&&shares(scope.owner,stack)==invocation.mask&&actionCurrent(scope,stack,invocation.mask)&&itemArguments(scope,invocation.operation,invocation.arguments,stack);
    }
    private static boolean invocationFrame(StackWalker.StackFrame frame,Scope scope,boolean item){
        for(Invocation invocation=INVOCATIONS.get(Thread.currentThread());invocation!=null;invocation=invocation.previous)
            if(!invocation.operation.equals("carrier-call")&&invocation.action==scope&&(invocation.receiver.get() instanceof Item)==item&&sameAnchor(frame,invocation.anchor,invocation.method,invocation.descriptor)&&invocationCurrent(invocation))return true;
        return false;
    }
    private static boolean carrierFrame(StackWalker.StackFrame frame,Scope scope){
        for(Invocation invocation=INVOCATIONS.get(Thread.currentThread());invocation!=null;invocation=invocation.previous)
            if(invocation.operation.equals("carrier-call")&&invocation.action==scope&&sameAnchor(frame,invocation.anchor,invocation.method,invocation.descriptor)&&invocationCurrent(invocation))return true;
        return false;
    }
    private static boolean invocationDepths(Scope scope,List<StackWalker.StackFrame> frames){
        List<Invocation> checked=new ArrayList<>();
        for(Invocation invocation=INVOCATIONS.get(Thread.currentThread());invocation!=null;invocation=invocation.previous)if(invocation.action==scope&&!invocation.closed){
            int count=0;for(var frame:frames)if(sameAnchor(frame,invocation.anchor,invocation.method,invocation.descriptor))count++;
            if(invocation.operation.equals("carrier-call")){
                boolean prior=false;for(Invocation done:checked)if(done.anchor==invocation.anchor&&done.method.equals(invocation.method)&&done.descriptor.equals(invocation.descriptor)){prior=true;break;}if(prior)continue;
                int expected=invocation.occurrences;if(count!=expected)return false;
                for(Invocation level=invocation;level!=null;level=level.previous)if(level.action==scope&&!level.closed&&level.operation.equals("carrier-call")
                        &&level.anchor==invocation.anchor&&level.method.equals(invocation.method)&&level.descriptor.equals(invocation.descriptor)){
                    if(level.occurrences!=expected--||!invocationCurrent(level))return false;
                }
                int inside=0;for(var frame:frames){if(sameAnchor(frame,scope.anchor,scope.method,scope.descriptor))break;if(sameAnchor(frame,invocation.anchor,invocation.method,invocation.descriptor))inside++;}
                if(inside!=count-expected)return false;checked.add(invocation);
            }else if(count!=invocation.occurrences)return false;
        }return true;
    }
    private static boolean scopePath(Scope scope,List<StackWalker.StackFrame> frames){
        if(!invocationDepths(scope,frames))return false;
        int occurrences=0;for(var frame:frames)if(sameAnchor(frame,scope.anchor,scope.method,scope.descriptor))occurrences++;
        if(occurrences!=scope.occurrences)return false;
        for(var frame:frames){
            if(sameAnchor(frame,scope.anchor,scope.method,scope.descriptor))return true;
            if(infrastructure(frame.getDeclaringClass()))continue;
            if(!gameFrame(frame,scope))return false;
        }return false;
    }
    /** No caller-supplied object can mint a lease: the first frame must be the actual mapped virtual body. */
    public static Object beginInvocation(Object receiver,Object arguments,String operation){
        if(!initialized||!(arguments instanceof Object[] args))return null;
        List<StackWalker.StackFrame> frames=invocationFrames();if(frames.isEmpty())return INVOCATION_REFUSED;
        StackWalker.StackFrame entry=frames.get(0),caller=frames.size()>1?frames.get(1):null;
        Invocation previous=INVOCATIONS.get(Thread.currentThread());Scope scope;ItemStack stack=null;int mask=0;boolean opens=false;
        if(operation.equals("carrier-call")){
            if(receiver==null||!EquipmentCarrierGraph.declared(entry.getDeclaringClass())||!entry.getDeclaringClass().isInstance(receiver)||carrierCallback(receiver,entry))return null;
            scope=SCOPES.get(Thread.currentThread());if(scope==null||scope.closed||!scopePath(scope,frames.subList(1,frames.size())))return null;
            boolean found=false;
            for(Link link:links(receiver))if((link.kind==Kind.CAPABILITY||link.kind==Kind.NBT||link.kind==Kind.STACK)&&current(link,receiver)){
                EquipmentCarrierGraph.Node node=heldNode(link,receiver);if(node==null||!node.data()||node.metadata())continue;
                ItemStack root=link.stack.get();int actualMask=shares(link.owner,root);
                if(link.owner!=scope.owner||root==null||curativeStack(link.owner,root)||!actionCurrent(scope,root,actualMask)||stack!=null&&stack!=root)return null;
                stack=root;mask=actualMask;found=true;
            }
            if(!found||!sourceAllowed(scope,receiver.getClass(),entry.getDeclaringClass()))return null;
        }else if(receiver instanceof ServerPlayerGameMode game){
            LivingEntity entity=actionEntity(receiver,args);Owner owner=entity==null?null:OWNERS.get(entity);if(!active(owner))return null;
            if(!mappedGame(entry,operation)||!entry.getDeclaringClass().isInstance(receiver)||!gameOwned(owner,game))return INVOCATION_REFUSED;
            // An override invoking its real super implementation continues the same action once.
            if(previous!=null&&previous.receiver.get()==receiver&&previous.operation.equals(operation)&&sameArguments(previous.arguments,args)
                    &&caller!=null&&sameAnchor(caller,previous.anchor,previous.method,previous.descriptor)&&entry.getDeclaringClass()!=previous.anchor
                    &&entry.getDeclaringClass().isAssignableFrom(previous.anchor)&&invocationCurrent(previous))scope=previous.action;
            else{Object token=beginAction(receiver,args,operation);if(!(token instanceof Scope accepted))return INVOCATION_REFUSED;scope=accepted;opens=true;}
            try{if(!sourceAllowed(scope)){if(opens)closeAction(scope,false);return INVOCATION_REFUSED;}}
            catch(RuntimeException|Error failure){if(opens)failedAction(scope,failure);throw failure;}
        }else if(receiver instanceof Item actual){
            if(!mappedItem(entry,operation)||!entry.getDeclaringClass().isInstance(receiver))return INVOCATION_REFUSED;
            stack=itemStack(operation,args);LivingEntity actor=itemActor(operation,args);
            Owner owner=actor==null?null:OWNERS.get(actor);
            if(stack!=null)for(Link link:links(stack))if(link.kind==Kind.STACK&&current(link,stack)){
                if(owner==null)owner=link.owner;else if(owner!=link.owner)return itemRead(operation)?null:INVOCATION_REFUSED;
            }
            if(!active(owner))return null;
            if(stack==null&&operation.equals("item-use")&&args.length==3&&args[2] instanceof InteractionHand hand)stack=handStack(owner,hand);
            scope=SCOPES.get(Thread.currentThread());mask=shares(owner,stack);
            boolean valid=scope!=null&&scope.owner==owner&&stack!=null&&item(stack)==actual&&scope.itemType==actual.getClass()&&mask!=0
                    &&actionCurrent(scope,stack,mask)&&itemArguments(scope,operation,args,stack)&&sourceAllowed(scope)&&scopePath(scope,frames.subList(1,frames.size()));
            if(!valid)return itemRead(operation)?null:INVOCATION_REFUSED;
        }else return null;
        Invocation invocation=null;
        try{
            int occurrences=0;for(var frame:frames)if(sameAnchor(frame,entry.getDeclaringClass(),entry.getMethodName(),entry.getDescriptor()))occurrences++;
            // Reentrant calls to the same body cannot consume a second dispatch using the outer receiver tuple.
            for(Invocation prior=previous;!operation.equals("carrier-call")&&prior!=null;prior=prior.previous)if(!prior.closed&&prior.receiver.get()==receiver&&sameAnchor(entry,prior.anchor,prior.method,prior.descriptor)){
                if(opens)closeAction(scope,false);return itemRead(operation)?null:INVOCATION_REFUSED;
            }
            invocation=new Invocation(scope,receiver,args,operation,entry,occurrences,previous,opens,stack,mask);INVOCATIONS.put(Thread.currentThread(),invocation);return invocation;
        }catch(RuntimeException|Error failure){
            if(invocation!=null&&INVOCATIONS.get(Thread.currentThread())==invocation)failedInvocation(invocation,failure);
            else if(opens)failedAction(scope,failure);
            throw failure;
        }
    }
    public static boolean invocationDenied(Object token){return token==INVOCATION_REFUSED;}
    public static Object invocationResult(Object receiver,String operation,Object arguments){
        Object[] args=arguments instanceof Object[] values?values:new Object[0];
        if(operation.equals("item-finishUsingItem")||operation.equals("item-turnBottleIntoItem"))return args.length>0&&args[0] instanceof ItemStack?args[0]:ItemStack.EMPTY;
        if(operation.equals("item-use")){
            LivingEntity actor=itemActor(operation,args);Owner owner=actor==null?null:OWNERS.get(actor);
            ItemStack stack=active(owner)&&args.length==3&&args[2] instanceof InteractionHand hand?handStack(owner,hand):ItemStack.EMPTY;
            return InteractionResultHolder.pass(stack==null?ItemStack.EMPTY:stack);
        }
        return InteractionResult.PASS;
    }
    public static void endInvocation(Object token){closeInvocation(token,true);}
    private static void closeInvocation(Object token,boolean success){
        if(!(token instanceof Invocation invocation)||invocation.closed)return;
        if(invocation.thread.get()!=Thread.currentThread()||INVOCATIONS.get(Thread.currentThread())!=invocation)throw new IllegalMonitorStateException("EQUIPMENT_INVOCATION_ORDER");
        try{if(invocation.opensAction)closeAction(invocation.action,success);else if(!success)cancelMining(invocation.action.owner);
            else if(invocation.operation.equals("carrier-call"))carrierChanged(invocation.receiver.get());}
        finally{invocation.closed=true;if(invocation.previous==null)INVOCATIONS.remove(Thread.currentThread());else INVOCATIONS.put(Thread.currentThread(),invocation.previous);}
    }
    public static void failedInvocation(Object token,Throwable failure){try{closeInvocation(token,false);}catch(RuntimeException|Error cleanup){if(cleanup!=failure)failure.addSuppressed(cleanup);}}
    private static boolean carrierCallback(Object receiver,StackWalker.StackFrame frame){
        String name=frame.getMethodName(),descriptor=frame.getDescriptor();
        if(name.startsWith("lambda$"))return true;
        if(receiver instanceof net.minecraftforge.common.capabilities.ICapabilityProvider&&name.equals("getCapability")&&descriptor.equals("(Lnet/minecraftforge/common/capabilities/Capability;Lnet/minecraft/core/Direction;)Lnet/minecraftforge/common/util/LazyOptional;"))return true;
        if(receiver instanceof net.minecraftforge.common.util.INBTSerializable<?>&&(name.equals("serializeNBT")&&descriptor.startsWith("()")||name.equals("deserializeNBT")&&descriptor.endsWith(")V")))return true;
        if(receiver instanceof Runnable&&name.equals("run")&&descriptor.equals("()V")||receiver instanceof java.util.concurrent.Callable<?> &&name.equals("call")&&descriptor.equals("()Ljava/lang/Object;"))return true;
        if(receiver instanceof Comparator<?> &&name.equals("compare"))return true;
        for(Class<?> type=receiver.getClass();type!=null;type=type.getSuperclass())for(Class<?> contract:type.getInterfaces())
            if(functionalContract(contract)&&Set.of("accept","apply","applyAsInt","applyAsLong","applyAsDouble","get","getAsBoolean","getAsInt","getAsLong","getAsDouble","test").contains(name))return true;
        return false;
    }
    private static boolean functionalContract(Class<?> type){
        if(type.getPackageName().equals("java.util.function")||type==net.minecraftforge.common.util.NonNullSupplier.class||type==net.minecraftforge.common.util.NonNullConsumer.class||type==net.minecraftforge.common.util.NonNullFunction.class)return true;
        for(Class<?> parent:type.getInterfaces())if(functionalContract(parent))return true;return false;
    }
    private static ItemStack handStack(Owner owner,InteractionHand hand){return hand==InteractionHand.MAIN_HAND?main(owner):itemAt(owner,owner.entity.get() instanceof Player?2:0,owner.entity.get() instanceof Player?0:1);}
    private static UseOnContext itemContext(String operation,Object[] args){
        Object context=operation.equals("item-useOn")||operation.equals("item-place")?args.length==1?args[0]:null:operation.equals("item-onItemUseFirst")&&args.length==2?args[1]:null;
        return context!=null&&(operation.equals("item-place")?context.getClass()==BlockPlaceContext.class:context.getClass()==UseOnContext.class)?(UseOnContext)context:null;
    }
    private static Object contextValue(String operation,Object[] args,Field field){
        Object context=operation.equals("item-useOn")||operation.equals("item-place")?args.length==1?args[0]:null:operation.equals("item-onItemUseFirst")&&args.length==2?args[1]:null;
        if(!(context instanceof UseOnContext))return null;
        try{return field.get(context);}catch(IllegalAccessException failure){throw new IllegalStateException("EQUIPMENT_ACTION_CONTEXT_ACCESS",failure);}
    }
    private static ItemStack itemStack(String operation,Object[] args){
        Object contextual=contextValue(operation,args,CONTEXT_STACK);if(contextual instanceof ItemStack stack)return stack;
        if(operation.equals("item-onUseTick"))return args.length==4&&args[2] instanceof ItemStack stack?stack:null;
        return args.length>0&&args[0] instanceof ItemStack stack?stack:null;
    }
    private static LivingEntity itemActor(String operation,Object[] args){
        Object contextual=contextValue(operation,args,CONTEXT_PLAYER);if(contextual instanceof LivingEntity entity)return entity;
        int index=operation.equals("item-use")||operation.equals("item-onUseTick")||operation.equals("item-turnBottleIntoItem")?1:operation.equals("item-mineBlock")?4:2;
        return args.length>index&&args[index] instanceof LivingEntity entity?entity:null;
    }
    private static boolean itemRead(String operation){return operation.equals("item-getDamage")||operation.equals("item-getMaxDamage");}
    private static boolean itemArguments(Scope scope,String operation,Object[] args,ItemStack stack){
        LivingEntity entity=scope.owner.entity.get();if(entity==null)return false;
        LivingEntity actor=itemActor(operation,args);if(actor!=null&&actor!=entity)return false;
        Object level=((Access.EntityState)entity).pro$level();UseOnContext context=itemContext(operation,args);
        if(operation.equals("item-use"))return scope.operation.equals("equipment-use")&&args.length==3&&args[0]==level&&args[1]==entity&&args[2] instanceof InteractionHand hand&&handStack(scope.owner,hand)==stack&&(hand==InteractionHand.MAIN_HAND?1:2)==scope.mask;
        if(operation.equals("item-useOn")||operation.equals("item-onItemUseFirst")||operation.equals("item-place"))return scope.operation.equals("equipment-use")&&context!=null&&context.getHand()!=null&&(!operation.equals("item-onItemUseFirst")||args[0]==stack)&&context.getPlayer()==entity&&context.getLevel()==level&&context.getItemInHand()==stack&&handStack(scope.owner,context.getHand())==stack&&(context.getHand()==InteractionHand.MAIN_HAND?1:2)==scope.mask;
        if(operation.equals("item-finishUsingItem"))return scope.operation.equals("equipment-consume")&&args.length==3&&args[0]==stack&&args[1]==level&&args[2]==entity;
        if(operation.equals("item-releaseUsing"))return scope.operation.equals("equipment-release")&&args.length==4&&args[0]==stack&&args[1]==level&&args[2]==entity&&args[3] instanceof Integer remaining&&remaining==((Access.Living)entity).pro$useRemaining();
        if(operation.equals("item-onUseTick"))return scope.operation.equals("equipment-use-tick")&&args.length==4&&args[0]==level&&args[1]==entity&&args[2]==stack&&args[3] instanceof Integer remaining&&remaining==((Access.Living)entity).pro$useRemaining();
        if(operation.equals("item-turnBottleIntoItem"))return scope.operation.equals("equipment-use")&&args.length==3&&args[0]==stack&&args[1]==entity&&args[2] instanceof ItemStack;
        if(operation.equals("item-hurtEnemy"))return scope.operation.equals("equipment-attack")&&args.length==3&&args[0]==stack&&args[1]==scope.attacked.get()&&args[2]==entity;
        if(operation.equals("item-mineBlock"))return scope.operation.equals("equipment-destroy")&&args.length==5&&args[0]==stack&&args[1]==level&&args[3] instanceof BlockPos position&&scope.dig!=null&&scope.dig.target!=null&&scope.dig.target.matches(position)&&args[4]==entity;
        if(operation.equals("item-onBlockStartBreak"))return scope.operation.equals("equipment-destroy")&&args.length==3&&args[0]==stack&&args[1] instanceof BlockPos position&&scope.dig!=null&&scope.dig.target!=null&&scope.dig.target.matches(position)&&args[2]==entity;
        if(operation.equals("item-damageItem"))return args.length==4&&args[0]==stack&&args[1] instanceof Integer&&args[2]==entity&&args[3] instanceof java.util.function.Consumer<?>;
        if(operation.equals("item-setDamage"))return args.length==2&&args[0]==stack&&args[1] instanceof Integer;
        return itemRead(operation)&&args.length==1&&args[0]==stack;
    }
    /** Consumption delegates only the actual current Item invocation, never arbitrary effect callbacks. */
    static boolean consumptionFrame(StackWalker.StackFrame frame,LivingEntity entity,ItemStack stack){
        for(Invocation invocation=INVOCATIONS.get(Thread.currentThread());invocation!=null;invocation=invocation.previous)
            if(invocation.action.owner.entity.get()==entity&&invocation.stack.get()==stack&&invocation.operation.equals("item-finishUsingItem")
                    &&invocation.action.operation.equals("equipment-consume")&&sameAnchor(frame,invocation.anchor,invocation.method,invocation.descriptor)
                    &&invocationCurrent(invocation)&&sourceAllowed(invocation.action))return true;
        return false;
    }
    /** Called by exact original action bodies, after packet thread handoff and before business publication. */
    public static Object beginAction(Object receiver,Object argument,String operation){
        if(!initialized)return null;Object[] args=argument instanceof Object[] values?values:new Object[0];
        LivingEntity entity=actionEntity(receiver,args);Owner owner=entity==null?null:OWNERS.get(entity);
        if(!active(owner)||!serverThread(entity))return null;
        if(receiver instanceof ServerPlayerGameMode&&operation.equals("equipment-use")&&(args.length==0||args[0]!=entity))return null;
        if(receiver instanceof ServerGamePacketListenerImpl listener&&(!(entity instanceof ServerPlayer player)||player.connection!=listener||listener.player!=player))return null;
        List<StackWalker.StackFrame> frames=invocationFrames();
        if(frames.isEmpty())return null;StackWalker.StackFrame entry=frames.get(0),caller=frames.size()>1?frames.get(1):null;
        boolean gameEntry=receiver instanceof ServerPlayerGameMode game&&mappedGame(entry,operation)&&entry.getDeclaringClass().isInstance(game);
        if(gameEntry?!sourceAllowed(receiver.getClass(),null):!sourceAllowed())return null;
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
            accepted=gameEntry;
            if(accepted)dig=beginDig(owner,(ServerPlayerGameMode)receiver,args,operation,caller);
            accepted&=dig!=null;mask=operation.equals("equipment-destroy")?1:0;
            if(dig!=null){if(args.length>0&&args[0] instanceof BlockPos position)dig.position=new WeakReference<>(position);if(mask!=0)used=dig.tool.get();}
        }
        else if(operation.equals("equipment-creative")||operation.equals("equipment-pick")){
            accepted=anchor==ServerGamePacketListenerImpl.class&&named(entry,operation.equals("equipment-pick")?"handlePickItem":"handleSetCreativeModeSlot",operation.equals("equipment-pick")?"m_7965_":"m_5964_")&&packetCaller(caller);
        }else if(operation.equals("equipment-use")){
            accepted=gameEntry&&gameOwned(owner,(ServerPlayerGameMode)receiver)&&caller!=null&&caller.getDeclaringClass()==ServerGamePacketListenerImpl.class&&named(caller,"handleUseItem","m_5760_","handleUseItemOn","m_6371_");
            if(args.length>3&&args[1]==((Access.EntityState)entity).pro$level()&&args[2] instanceof ItemStack stack&&args[3] instanceof InteractionHand hand){used=stack;mask=hand==InteractionHand.MAIN_HAND?1:2;accepted&=shares(owner,used)!=0&&(shares(owner,used)&mask)!=0;}else accepted=false;
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
        if(!accepted||!gameEntry&&!vanilla(entry))return null;
        Scope previous=SCOPES.get(Thread.currentThread());
        for(Scope prior=previous;prior!=null;prior=prior.previous)if(!prior.closed&&prior.owner==owner&&prior.anchor==anchor&&prior.method.equals(entry.getMethodName()))return null;
        int occurrences=0;for(var frame:frames)if(sameAnchor(frame,entry.getDeclaringClass(),entry.getMethodName(),entry.getDescriptor()))occurrences++;
        Scope scope=new Scope(owner,operation,entry,previous,mask,used,menu,occurrences,receiver instanceof ServerPlayerGameMode game?game:null);scope.intent=actionIntent;scope.dig=dig;scope.attacked=new WeakReference<>(attacked);
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
        return name.startsWith("dev.ronova.pro.bootstrap.")||name.equals("java.lang.reflect.Field")||name.startsWith("jdk.internal.reflect.")||name.startsWith("java.lang.invoke.")||name.equals("sun.misc.Unsafe")||name.equals("jdk.internal.misc.Unsafe")
                ||Set.of("java.util.HashMap","java.util.LinkedHashMap","java.util.AbstractMap","java.util.ArrayList","java.util.AbstractList","java.util.AbstractCollection","java.util.Arrays","java.util.Collections").contains(name)||type==ARRAY_LIST;
    }
    private static boolean gameFrame(StackWalker.StackFrame frame,Scope scope){
        Class<?> type=frame.getDeclaringClass();String name=frame.getMethodName();
        if(carrierFrame(frame,scope))return true;
        if(EquipmentCarrierLayouts.known(type)||EquipmentCarrierLayouts.known(type.getNestHost()))return true;
        if(Item.class.isAssignableFrom(type)){
            return invocationFrame(frame,scope,true);
        }
        if(ServerPlayerGameMode.class.isAssignableFrom(type))return invocationFrame(frame,scope,false)||type==ServerPlayerGameMode.class&&named(frame,"incrementDestroyProgress","m_9276_","destroyAndAck","m_215116_");
        if(ownedNbtRedirect(frame))return true;
        if(type==CapabilityProvider.class)return names(name,"invalidateCaps","reviveCaps","deserializeCaps","getCapabilities","doGatherCapabilities","gatherCapabilities");
        if(type==CapabilityDispatcher.class)return names(name,"invalidate","deserializeNBT","getCapability");
        if(type==LazyOptional.class)return names(name,"invalidate","addListener","removeListener");
        if(capabilityProviderType(type))return names(name,"fill","drain","setFluid","setContainerToEmpty");
        if(!vanilla(frame)){
            if(type==net.minecraftforge.common.extensions.IForgeItem.class)return invocationFrame(frame,scope,true);
            if(type==net.minecraftforge.common.extensions.IForgeItemStack.class)return names(name,"onBlockStartBreak")&&frame.getDescriptor().equals("(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/entity/player/Player;)Z");
            if(type==net.minecraftforge.common.ForgeHooks.class)return name.equals("onPlaceItemIntoWorld")&&frame.getDescriptor().equals("(Lnet/minecraft/world/item/context/UseOnContext;)Lnet/minecraft/world/InteractionResult;")&&scope.operation.equals("equipment-use");
            return false;
        }
        if(type==NonNullList.class||type.getPackageName().equals("net.minecraft.nbt"))return true;
        if(type==ItemUtils.class)return names(name,"createFilledResult","m_41813_","m_41817_")
                &&(frame.getDescriptor().equals("(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/world/item/ItemStack;)Lnet/minecraft/world/item/ItemStack;")
                        ||frame.getDescriptor().equals("(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/world/item/ItemStack;Z)Lnet/minecraft/world/item/ItemStack;"));
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
        if(type==ServerGamePacketListenerImpl.class)return !name.contains("$");
        return false;
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
            if(!actionCurrent(scope,stack,mask))continue;
            if(scope.intent!=null&&!currentIntent(owner,scope.intent,scope.operation.equals("equipment-attack"))||scope.dig!=null&&!digContext(scope))continue;
            if(scope.operation.equals("equipment-attack")&&(scope.attacked.get()==null||scope.attacked.get()!=scope.intent.target.get()||main(owner)!=scope.used.get()))continue;
            if(mask==0&&!Set.of("equipment-select","equipment-menu","equipment-pick","equipment-creative","equipment-load","equipment-load-root").contains(scope.operation))continue;
            if(scope.used.get()!=null&&scope.used.get()!=stack)continue;
            if(scope.menu.get()!=null&&(!(entity instanceof Player player)||player.containerMenu!=scope.menu.get()))continue;
            if(!sourceAllowed(scope))continue;
            Scope selected=scope;
            boolean path=WALKER.walk(frames->{var actual=frames.toList();int occurrences=0;for(var frame:actual)if(sameAnchor(frame,selected.anchor,selected.method,selected.descriptor))occurrences++;
                if(occurrences!=selected.occurrences||!invocationDepths(selected,actual))return false;boolean first=true;for(var frame:actual){
                if(frame.getDeclaringClass()==selected.anchor&&frame.getMethodName().equals(selected.method)&&frame.getDescriptor().equals(selected.descriptor))return true;
                if(infrastructure(frame.getDeclaringClass()))continue;
                if(first&&entryQuery&&Set.of(ItemStack.class,Inventory.class,NonNullList.class,LivingEntity.class,Player.class,Mob.class,ArmorStand.class,ContainerHelper.class).contains(frame.getDeclaringClass())&&frame.getMethodName().contains("$ronova$equipment")){first=false;continue;}
                first=false;if(!gameFrame(frame,selected))return false;
            }return false;});
            if(path)return true;
        }return false;
    }
    private static boolean permitStack(Link link,boolean entry){
        ItemStack stack=link.stack.get();
        // Vanilla replaces a curative recipe list; it never edits its ItemStacks
        // or NBT in place. An equipment action cannot lend it that permission.
        return !curativeStack(link.owner,stack)&&permitted(link.owner,shares(link.owner,stack),stack,entry);
    }
    private static boolean anySlotDenied(Owner owner,boolean entry){
        for(int part=0;part<(owner.entity.get() instanceof Player?3:2);part++){
            Object[] values=(Object[])array(backing(list(owner,part)));if(values==null)continue;
            for(int i=0;i<values.length;i++){int mask=bit(owner,part,i);if(mask!=0&&!permitted(owner,mask,values[i] instanceof ItemStack stack?stack:null,entry))return true;}
        }return false;
    }
    public static boolean listWriteDenied(Object receiver,int index,Object next){
        if(!initialized)return false;
        for(Link link:links(receiver))if((link.kind==Kind.CURATIVE_LIST||link.kind==Kind.CURATIVE_ARRAY)&&current(link,receiver))return true;
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
                if(link.kind==Kind.NBT||link.kind==Kind.CURATIVE_LIST)return true;
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
    static boolean elementDenied(Object container,Object old,Object next){return elementDenied(container,old,next,-1);}
    private static boolean elementDenied(Object container,Object old,Object next,int index){
        if(old==next&&old!=null)return false;
        for(Link link:links(container))if(current(link,container)){
            if(link.kind==Kind.CAPABILITY||link.kind==Kind.NBT){
                EquipmentCarrierGraph.Node node=heldNode(link,container);if(node==null||index>=0&&!node.contains(index))continue;
                if(node.metadata()?!capabilityListeners(link,container):!node.data()||!permitStack(link,false))throw new SecurityException("EQUIPMENT_CAPABILITY_WRITE_REJECTED");
                cancelToolMining(link);if(next!=null){if(link.kind==Kind.NBT)walk(link.owner,link.stack.get(),null,true,next);else capabilityWalk(link.owner,link.stack.get(),null,true,next);}continue;
            }
            if(link.kind==Kind.CURATIVE_CARRIER){EquipmentCarrierGraph.Node node=EquipmentCarrierGraph.curatives(link.root.get()).node(container);if(node!=null&&(index<0||node.contains(index)))throw new SecurityException("CURATIVE_CARRIER_WRITE_REJECTED");continue;}
            if(link.kind==Kind.CURATIVE_LIST||link.kind==Kind.CURATIVE_ARRAY)return true;
            if(link.kind==Kind.NBT){if(!permitStack(link,false))return true;cancelToolMining(link);if(next!=null)walk(link.owner,link.stack.get(),null,true,next);}
            else if(link.kind==Kind.COMPARTMENTS||link.kind==Kind.COMPARTMENT_ARRAY)return true;
            else if(link.kind==Kind.EQUIPMENT_LIST||link.kind==Kind.EQUIPMENT_BACKING){
                if(old instanceof ItemStack stack&&stack!=ItemStack.EMPTY){int mask=shares(link.owner,stack);if(mask!=0&&!permitted(link.owner,mask,stack,false))return true;if((mask&1)!=0)cancelMining(link.owner);}
            }
        }return false;
    }
    static boolean arrayDenied(Object array,int index,Object next){
        if(array==null||!array.getClass().isArray()||index<0||index>=Array.getLength(array))return false;
        Object old=Array.get(array,index);if(array.getClass().getComponentType().isPrimitive()?samePrimitive(old,next):old==next)return false;
        if(listWriteDenied(array,index,next))return true;
        return elementDenied(array,old,next,index);
    }
    static boolean fieldDenied(Object receiver,Class<?> declaring,String name,Object next){
        if(!initialized||receiver==null||declaring==null)return false;Slot selected=null;
        for(Slot slot:mutationSlots(receiver))if(slot.field.getDeclaringClass()==declaring&&slot.field.getName().equals(name)){selected=slot;break;}
        if(selected==null)return false;
        Object old;
        if(!links(receiver).isEmpty())old=EquipmentCarrierGraph.read(receiver,selected.field);
        else try{old=selected.field.get(receiver);}catch(IllegalAccessException failure){throw new IllegalStateException("EQUIPMENT_FIELD_READ",failure);}
        if(selected.field.getType().isPrimitive()?samePrimitive(old,next):old==next)return false;
        for(Link link:links(receiver))if(current(link,receiver)){
            if(link.kind==Kind.STACK||link.kind==Kind.CAPABILITY||link.kind==Kind.NBT){
                Boolean capability=capabilityField(link,receiver,selected.field,next);
                if(capability!=null){
                    if(!capability)throw new SecurityException("EQUIPMENT_CAPABILITY_WRITE_REJECTED");
                    if(next!=null&&!selected.field.getType().isPrimitive()){if(link.kind==Kind.NBT)walk(link.owner,link.stack.get(),null,true,next);else capabilityWalk(link.owner,link.stack.get(),null,true,next);}
                    if(next instanceof Tag)walk(link.owner,link.stack.get(),null,true,next);
                    if(link.kind==Kind.CAPABILITY||link.kind==Kind.NBT||selected.field.equals(CAP_DATA))cancelToolMining(link);
                    continue;
                }
                if(link.kind==Kind.CAPABILITY||link.kind==Kind.NBT)throw new SecurityException("EQUIPMENT_CAPABILITY_WRITE_REJECTED");
            }
            if(link.kind==Kind.CURATIVE_CARRIER)throw new SecurityException("CURATIVE_CARRIER_WRITE_REJECTED");
            if(link.kind==Kind.CURATIVE_LIST)return true;
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
    private static boolean samePrimitive(Object first,Object second){
        if(first instanceof Float a&&second instanceof Float b)return Float.floatToRawIntBits(a)==Float.floatToRawIntBits(b);
        if(first instanceof Double a&&second instanceof Double b)return Double.doubleToRawLongBits(a)==Double.doubleToRawLongBits(b);
        return Objects.equals(first,second);
    }
    private static void cancelToolMining(Link link){
        Mining mining=MINING.get(link.owner.entity.get());if(mining!=null&&mining.tool.get()==link.stack.get())cancelMining(link.owner);
    }
    /** GameMode state never inherits the current Item's equipment delegation. */
    private static Scope gameWriteScope(Owner owner,ServerPlayerGameMode game){
        if(!gameOwned(owner,game))return null;
        for(Scope scope=SCOPES.get(Thread.currentThread());scope!=null;scope=scope.previous){
            if(scope.owner!=owner||scope.closed||scope.thread.get()!=Thread.currentThread()||scope.dig==null||scope.dig.game.get()!=game)continue;
            if(!sourceAllowed(scope))continue;
            boolean context=digContext(scope);
            if(!context&&scope.operation.equals("equipment-dig-tick")){
                Access.EquipmentGameMode raw=(Access.EquipmentGameMode)game;
                context=driven(owner,game)&&raw.pro$gameType()==scope.dig.before.mode&&(raw.pro$gameTicks()==scope.dig.before.ticks||raw.pro$gameTicks()==scope.dig.before.ticks+1);
            }
            if(!context)continue;Scope selected=scope;
            boolean path=WALKER.walk(stream->{List<StackWalker.StackFrame> frames=stream.toList();int occurrences=0;
                for(var frame:frames)if(sameAnchor(frame,selected.anchor,selected.method,selected.descriptor))occurrences++;
                if(occurrences!=selected.occurrences||!invocationDepths(selected,frames))return false;
                for(var frame:frames){
                    if(sameAnchor(frame,selected.anchor,selected.method,selected.descriptor))return true;
                    if(infrastructure(frame.getDeclaringClass()))continue;
                    if(invocationFrame(frame,selected,false))continue;
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
        for(Slot slot:mutationSlots(receiver))if(overlaps(offset,width,slot.offset,slot.width)){
            if((offset!=slot.offset||width!=slot.width)&&carrierRangeDenied(receiver,slot))return true;
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
                if(link.kind==Kind.CAPABILITY||link.kind==Kind.NBT){
                    EquipmentCarrierGraph.Node node=heldNode(link,receiver);if(node==null||!node.overlaps(offset,length))continue;
                    if(!receiver.getClass().getComponentType().isPrimitive()||(node.metadata()?!capabilityListeners(link,receiver):!node.data()||!permitStack(link,false)))
                        throw new SecurityException("EQUIPMENT_CAPABILITY_MEMORY_WRITE_REJECTED");continue;
                }
                if(link.kind==Kind.CURATIVE_CARRIER){EquipmentCarrierGraph.Node node=EquipmentCarrierGraph.curatives(link.root.get()).node(receiver);if(node!=null&&node.overlaps(offset,length))throw new SecurityException("CURATIVE_CARRIER_MEMORY_WRITE_REJECTED");continue;}
                if(link.kind==Kind.EQUIPMENT_ARRAY||link.kind==Kind.COMPARTMENT_ARRAY||link.kind==Kind.CURATIVE_ARRAY)return true;
                if(link.kind==Kind.NBT){if(!permitStack(link,false))return true;cancelToolMining(link);}
            }return false;
        }
        for(Slot slot:mutationSlots(receiver))if(overlaps(offset,length,slot.offset,slot.width)){
            if(carrierRangeDenied(receiver,slot))return true;
            if(fieldDenied(receiver,slot.field.getDeclaringClass(),slot.field.getName(),null))return true;
        }
        return false;
    }
    private static boolean carrierRangeDenied(Object receiver,Slot slot){
        for(Link link:links(receiver))if(current(link,receiver)){
            if(link.kind==Kind.CURATIVE_CARRIER||link.kind==Kind.CURATIVE_LIST)throw new SecurityException("CURATIVE_CARRIER_MEMORY_WRITE_REJECTED");
            if(link.kind==Kind.CAPABILITY||link.kind==Kind.NBT){EquipmentCarrierGraph.Node node=heldNode(link,receiver);
                if(!slot.field.getType().isPrimitive()||node==null||node.metadata()||!node.data()||!permitStack(link,false))throw new SecurityException("EQUIPMENT_CAPABILITY_MEMORY_WRITE_REJECTED");}
        }return false;
    }
    static List<Field> unsafeFields(){if(!initialized)return List.of();List<Field> result=new ArrayList<>();for(var slots:FIELDS.values())for(Slot slot:slots)if(!result.contains(slot.field))result.add(slot.field);return result;}
    private static final String[][] GAME_ACTIONS={
        {"useItem","m_6261_","(Lnet/minecraft/server/level/ServerPlayer;Lnet/minecraft/world/level/Level;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/InteractionHand;)Lnet/minecraft/world/InteractionResult;","equipment-use"},
        {"useItemOn","m_7179_","(Lnet/minecraft/server/level/ServerPlayer;Lnet/minecraft/world/level/Level;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/InteractionHand;Lnet/minecraft/world/phys/BlockHitResult;)Lnet/minecraft/world/InteractionResult;","equipment-use"},
        {"handleBlockBreakAction","m_214168_","(Lnet/minecraft/core/BlockPos;Lnet/minecraft/network/protocol/game/ServerboundPlayerActionPacket$Action;Lnet/minecraft/core/Direction;II)V","equipment-dig-action"},
        {"tick","m_7712_","()V","equipment-dig-tick"},{"destroyBlock","m_9280_","(Lnet/minecraft/core/BlockPos;)Z","equipment-destroy"},
        {"removeBlock","","(Lnet/minecraft/core/BlockPos;Z)Z","equipment-remove-block"},{"setLevel","m_9260_","(Lnet/minecraft/server/level/ServerLevel;)V","equipment-dig-level"}
    };
    private static final String[][] ITEM_ACTIONS={
        {"use","m_7203_","(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/world/InteractionHand;)Lnet/minecraft/world/InteractionResultHolder;","item-use"},
        {"useOn","m_6225_","(Lnet/minecraft/world/item/context/UseOnContext;)Lnet/minecraft/world/InteractionResult;","item-useOn"},
        {"finishUsingItem","m_5922_","(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/level/Level;Lnet/minecraft/world/entity/LivingEntity;)Lnet/minecraft/world/item/ItemStack;","item-finishUsingItem"},
        {"releaseUsing","m_5551_","(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/level/Level;Lnet/minecraft/world/entity/LivingEntity;I)V","item-releaseUsing"},
        {"onUseTick","m_5929_","(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/item/ItemStack;I)V","item-onUseTick"},
        {"onItemUseFirst","","(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/context/UseOnContext;)Lnet/minecraft/world/InteractionResult;","item-onItemUseFirst"},
        {"onBlockStartBreak","","(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/entity/player/Player;)Z","item-onBlockStartBreak"},
        {"setDamage","","(Lnet/minecraft/world/item/ItemStack;I)V","item-setDamage"},
        {"damageItem","","(Lnet/minecraft/world/item/ItemStack;ILnet/minecraft/world/entity/LivingEntity;Ljava/util/function/Consumer;)I","item-damageItem"},
        {"getDamage","","(Lnet/minecraft/world/item/ItemStack;)I","item-getDamage"},{"getMaxDamage","","(Lnet/minecraft/world/item/ItemStack;)I","item-getMaxDamage"},
        {"mineBlock","m_6813_","(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/level/Level;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/entity/LivingEntity;)Z","item-mineBlock"},
        {"hurtEnemy","m_7579_","(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/entity/LivingEntity;)Z","item-hurtEnemy"}
    };
    private static final String[][] BLOCK_ACTIONS={{"place","m_40576_","(Lnet/minecraft/world/item/context/BlockPlaceContext;)Lnet/minecraft/world/InteractionResult;","item-place"}};
    private static final String[][] BOTTLE_ACTIONS={{"turnBottleIntoItem","m_40651_","(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/world/item/ItemStack;)Lnet/minecraft/world/item/ItemStack;","item-turnBottleIntoItem"}};
    private static boolean mapped(StackWalker.StackFrame frame,String operation,String[][] mappings){
        for(String[] mapping:mappings)if(mapping[3].equals(operation)&&names(frame.getMethodName(),mapping[0],mapping[1])&&frame.getDescriptor().equals(mapping[2]))return true;
        return false;
    }
    private static boolean virtualBody(StackWalker.StackFrame frame){
        for(Method method:frame.getDeclaringClass().getDeclaredMethods())if(method.getName().equals(frame.getMethodName())&&MethodType.methodType(method.getReturnType(),method.getParameterTypes()).descriptorString().equals(frame.getDescriptor()))
            return !Modifier.isPrivate(method.getModifiers())&&!Modifier.isStatic(method.getModifiers());
        return false;
    }
    private static boolean mappedGame(StackWalker.StackFrame frame,String operation){return ServerPlayerGameMode.class.isAssignableFrom(frame.getDeclaringClass())&&mapped(frame,operation,GAME_ACTIONS)
            &&(operation.equals("equipment-remove-block")?frame.getDeclaringClass()==ServerPlayerGameMode.class:virtualBody(frame));}
    private static boolean mappedItem(StackWalker.StackFrame frame,String operation){return virtualBody(frame)&&(Item.class.isAssignableFrom(frame.getDeclaringClass())||frame.getDeclaringClass()==net.minecraftforge.common.extensions.IForgeItem.class)
            &&(mapped(frame,operation,ITEM_ACTIONS)||BlockItem.class.isAssignableFrom(frame.getDeclaringClass())&&mapped(frame,operation,BLOCK_ACTIONS)||BottleItem.class.isAssignableFrom(frame.getDeclaringClass())&&mapped(frame,operation,BOTTLE_ACTIONS));}
    static void declareDispatchTargets(Class<?> agent)throws ReflectiveOperationException {
        Method declare=agent.getMethod("declareDispatchArgumentsTarget",Class.class,String.class,String.class);
        declare(declare,LivingEntity.class,new String[][]{{"setItemSlot","m_8061_","(Lnet/minecraft/world/entity/EquipmentSlot;Lnet/minecraft/world/item/ItemStack;)V"},{"setItemInHand","m_21008_","(Lnet/minecraft/world/InteractionHand;Lnet/minecraft/world/item/ItemStack;)V"}});
        declare(declare,ItemStack.class,new String[][]{{"setCount","m_41764_","(I)V"},{"grow","m_41769_","(I)V"},{"shrink","m_41774_","(I)V"},{"setTag","m_41751_","(Lnet/minecraft/nbt/CompoundTag;)V"},{"setDamageValue","m_41721_","(I)V"},{"split","m_41620_","(I)Lnet/minecraft/world/item/ItemStack;"},{"copyAndClear","m_278832_","()Lnet/minecraft/world/item/ItemStack;"},{"deserializeNBT","","(Lnet/minecraft/nbt/CompoundTag;)V"},{"hurtAndBreak","m_41622_","(ILnet/minecraft/world/entity/LivingEntity;Ljava/util/function/Consumer;)V"},{"hurt","m_220157_","(ILnet/minecraft/util/RandomSource;Lnet/minecraft/server/level/ServerPlayer;)Z"}});
        declare(declare,Inventory.class,new String[][]{{"setItem","m_6836_","(ILnet/minecraft/world/item/ItemStack;)V"},{"removeItem","m_7407_","(II)Lnet/minecraft/world/item/ItemStack;"},{"removeItemNoUpdate","m_8016_","(I)Lnet/minecraft/world/item/ItemStack;"},{"removeItem","m_36057_","(Lnet/minecraft/world/item/ItemStack;)V"},{"removeFromSelected","m_182403_","(Z)Lnet/minecraft/world/item/ItemStack;"},{"dropAll","m_36071_","()V"},{"clearContent","m_6211_","()V"},{"load","m_36035_","(Lnet/minecraft/nbt/ListTag;)V"},{"replaceWith","m_36006_","(Lnet/minecraft/world/entity/player/Inventory;)V"}});
        declare(declare,ContainerHelper.class,new String[][]{{"takeItem","m_18966_","(Ljava/util/List;I)Lnet/minecraft/world/item/ItemStack;"},{"removeItem","m_18969_","(Ljava/util/List;II)Lnet/minecraft/world/item/ItemStack;"}});
        declare(declare,ARRAY_LIST,new String[][]{{"set","","(ILjava/lang/Object;)Ljava/lang/Object;"},{"replaceAll","","(Ljava/util/function/UnaryOperator;)V"},{"sort","","(Ljava/util/Comparator;)V"}});
        Method actions=agent.getMethod("declareEquipmentActionTarget",Class.class,String.class,String.class,String.class);
        declareActions(actions,ServerPlayerGameMode.class,GAME_ACTIONS);
        declareActions(actions,Item.class,ITEM_ACTIONS);
        declareActions(actions,net.minecraftforge.common.extensions.IForgeItem.class,ITEM_ACTIONS);
        declareActions(actions,BlockItem.class,BLOCK_ACTIONS);declareActions(actions,BottleItem.class,BOTTLE_ACTIONS);
    }
    private static void declareActions(Method declare,Class<?> owner,String[][] mappings)throws ReflectiveOperationException{
        for(String[] mapping:mappings)for(Method method:owner.getDeclaredMethods())if(names(method.getName(),mapping[0],mapping[1])
                &&MethodType.methodType(method.getReturnType(),method.getParameterTypes()).toMethodDescriptorString().equals(mapping[2]))
            declare.invoke(null,owner,method.getName(),mapping[2],mapping[3]);
    }
    private static void declare(Method declare,Class<?> owner,String[][] entries)throws ReflectiveOperationException{
        for(String[] entry:entries)for(Method method:owner.getDeclaredMethods())if((method.getName().equals(entry[0])||!entry[1].isEmpty()&&method.getName().equals(entry[1]))
                &&MethodType.methodType(method.getReturnType(),method.getParameterTypes()).toMethodDescriptorString().equals(entry[2]))declare.invoke(null,owner,method.getName(),entry[2]);
    }
}
