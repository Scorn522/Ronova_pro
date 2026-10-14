package dev.ronova.pro;

import dev.ronova.pro.mixin.Access;
import java.lang.invoke.*;
import java.lang.ref.WeakReference;
import java.lang.reflect.*;
import java.util.*;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.*;
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
    private enum Kind { BODY, INVENTORY, COMPARTMENTS, COMPARTMENT_ARRAY, EQUIPMENT_LIST, EQUIPMENT_BACKING, EQUIPMENT_ARRAY, STACK, NBT }
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
        Scope(Owner owner,String operation,StackWalker.StackFrame frame,Scope previous,int mask,ItemStack used,Object menu,int occurrences){
            this.owner=owner;this.operation=operation;anchor=frame.getDeclaringClass();method=frame.getMethodName();descriptor=frame.getDescriptor();
            this.previous=previous;this.mask=mask;this.used=new WeakReference<>(used);this.menu=new WeakReference<>(menu);this.occurrences=occurrences;this.itemType=itemType(used);
        }
    }
    private record Visit(Object value,int limit) { }
    private static final int ALL=63,MAX_GRAPH=4096;
    private static volatile boolean initialized;
    private static Map<LivingEntity,Owner> OWNERS;
    private static Map<Object,List<Link>> CARRIERS;
    private static Map<Thread,Scope> SCOPES;
    private static Map<Class<?>,List<Slot>> FIELDS;
    private static StackWalker WALKER;
    private static Class<?> ARRAY_LIST,NODE,IMMUTABLE_LIST;
    private static Field ARRAY_VALUES,LIST_VALUES,LIST_SIZE,MAP_TABLE,NODE_NEXT,NODE_VALUE;
    private static MethodHandle ACTION_SOURCE,ACTION_DELEGATE;
    private static volatile int REFERENCE_WIDTH=8;
    private EquipmentPolicy(){ }

    static synchronized void initialize()throws ReflectiveOperationException {
        if(initialized)return;
        ARRAY_LIST=Class.forName("java.util.Arrays$ArrayList",false,null);
        NODE=Class.forName("java.util.HashMap$Node",false,null);
        Class<?> tree=Class.forName("java.util.HashMap$TreeNode",false,null);
        IMMUTABLE_LIST=Class.forName("com.google.common.collect.RegularImmutableList",false,EquipmentPolicy.class.getClassLoader());
        Class<?>[] carriers={Player.class,Inventory.class,Mob.class,ArmorStand.class,NonNullList.class,ItemStack.class,
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
        Class<?> bridge=Class.forName("dev.ronova.pro.bootstrap.ControlBridge",false,null);
        ACTION_SOURCE=MethodHandles.publicLookup().findStatic(bridge,"equipmentActionSourceAllowed",MethodType.methodType(boolean.class));
        ACTION_DELEGATE=MethodHandles.publicLookup().findStatic(bridge,"equipmentActionSourceAllowed",MethodType.methodType(boolean.class,Class.class));
        OWNERS=Collections.synchronizedMap(new WeakIdentityMap<>());CARRIERS=Collections.synchronizedMap(new WeakIdentityMap<>());
        SCOPES=Collections.synchronizedMap(new WeakIdentityMap<>());FIELDS=Map.copyOf(fields);
        WALKER=StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE);initialized=true;
    }
    private static Field access(Class<?> type,String name)throws ReflectiveOperationException{Field field=type.getDeclaredField(name);field.setAccessible(true);return field;}
    private static int width(Class<?> type){return !type.isPrimitive()?REFERENCE_WIDTH:type==long.class||type==double.class?8:type==int.class||type==float.class?4:type==short.class||type==char.class?2:1;}
    private static boolean names(String actual,String... names){for(String name:names)if(actual.equals(name))return true;return false;}
    private static boolean selected(Field field){
        Class<?> type=field.getDeclaringClass();String name=field.getName();
        if(type==Player.class)return names(name,"inventory","f_36093_");
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
        if(generation<=0){OWNERS.remove(living);return;}
        Owner owner=OWNERS.get(living);if(owner==null||owner.generation!=generation){owner=new Owner(living,generation);OWNERS.put(living,owner);}
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
        if(receiver instanceof ServerPlayerGameMode&&(args.length==0||args[0]!=entity))return null;
        List<StackWalker.StackFrame> frames=WALKER.walk(stream->stream.filter(frame->frame.getDeclaringClass().getNestHost()!=EquipmentPolicy.class).toList());
        if(frames.isEmpty())return null;StackWalker.StackFrame entry=frames.get(0),caller=frames.size()>1?frames.get(1):null;
        int mask=ALL;ItemStack used=null;Object menu=null;boolean accepted=false;
        Class<?> anchor=entry.getDeclaringClass();
        if(operation.equals("equipment-select")){accepted=anchor==ServerGamePacketListenerImpl.class&&named(entry,"handleSetCarriedItem","m_7798_")&&packetCaller(caller);mask=0;}
        else if(operation.equals("equipment-menu")){accepted=anchor==ServerGamePacketListenerImpl.class&&named(entry,"handleContainerClick","m_5914_")&&packetCaller(caller);menu=entity instanceof Player player?player.containerMenu:null;}
        else if(operation.equals("equipment-player-action")){accepted=anchor==ServerGamePacketListenerImpl.class&&named(entry,"handlePlayerAction","m_7502_")&&packetCaller(caller);mask=3;}
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
        Scope scope=new Scope(owner,operation,entry,previous,mask,used,menu,occurrences);SCOPES.put(Thread.currentThread(),scope);return scope;
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
    public static void endAction(Object token){
        if(!(token instanceof Scope scope)||scope.closed)return;
        if(scope.thread.get()!=Thread.currentThread()||SCOPES.get(Thread.currentThread())!=scope)throw new IllegalMonitorStateException("EQUIPMENT_ACTION_SCOPE_ORDER");
        try{if(active(scope.owner))refresh(scope.owner);}finally{scope.closed=true;if(scope.previous==null)SCOPES.remove(Thread.currentThread());else SCOPES.put(Thread.currentThread(),scope.previous);}
    }
    public static void failedAction(Object token,Throwable failure){try{endAction(token);}catch(RuntimeException|Error cleanup){if(cleanup!=failure)failure.addSuppressed(cleanup);}}
    private static boolean infrastructure(Class<?> type){
        if(type.getClassLoader()==EquipmentPolicy.class.getClassLoader()&&type.getModule()==EquipmentPolicy.class.getModule()&&type.getName().startsWith("dev.ronova.pro."))return true;
        if(type.getClassLoader()!=null)return false;String name=type.getNestHost().getName();
        return name.startsWith("dev.ronova.pro.bootstrap.")||name.equals("java.lang.reflect.Field")||name.startsWith("jdk.internal.reflect.")||name.equals("sun.misc.Unsafe")||name.equals("jdk.internal.misc.Unsafe")
                ||Set.of("java.util.HashMap","java.util.LinkedHashMap","java.util.AbstractMap","java.util.ArrayList","java.util.AbstractList","java.util.AbstractCollection","java.util.Arrays","java.util.Collections").contains(name)||type==ARRAY_LIST;
    }
    private static boolean gameFrame(StackWalker.StackFrame frame,Scope scope){
        Class<?> type=frame.getDeclaringClass();String name=frame.getMethodName();
        if(Item.class.isAssignableFrom(type)){
            return scope.itemType!=null&&type.isAssignableFrom(scope.itemType)&&names(name,"use","m_7203_","useOn","m_6225_","finishUsingItem","m_5922_","releaseUsing","m_5551_","onUseTick","m_5929_","onItemUseFirst","setDamage","damageItem","mineBlock","m_6813_","hurtEnemy","m_7579_");
        }
        if(ownedNbtRedirect(frame))return true;
        if(!vanilla(frame)){
            if(type.getName().equals("net.minecraftforge.common.extensions.IForgeItem")||type.getName().equals("net.minecraftforge.common.extensions.IForgeItemStack"))return names(name,"setDamage","getDamage","damageItem","getMaxDamage");
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
        if(type==ServerPlayer.class)return names(name,"restoreFrom","m_9015_","drop","m_7197_","m_182294_","readAdditionalSaveData","m_7378_");
        if(type==ServerPlayerGameMode.class||type==ServerGamePacketListenerImpl.class)return !name.contains("$");
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
        if(!permitted(owner,mask,current,true))return true;captureStack(owner,next);return false;
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
            if(link.kind==Kind.NBT){if(!permitStack(link,false))return true;if(next!=null)walk(link.owner,link.stack.get(),null,true,next);}
            else if(link.kind==Kind.COMPARTMENTS||link.kind==Kind.COMPARTMENT_ARRAY)return true;
            else if(link.kind==Kind.EQUIPMENT_LIST||link.kind==Kind.EQUIPMENT_BACKING){
                if(old instanceof ItemStack stack&&stack!=ItemStack.EMPTY){int mask=shares(link.owner,stack);if(mask!=0&&!permitted(link.owner,mask,stack,false))return true;}
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
            if(link.kind==Kind.BODY||link.kind==Kind.INVENTORY||link.kind==Kind.EQUIPMENT_LIST||link.kind==Kind.EQUIPMENT_BACKING||link.kind==Kind.COMPARTMENTS){if(structureFieldDenied(link,declaring,name,next))return true;continue;}
            if(link.kind==Kind.STACK){
                if(names(name,"item","f_41589_","delegate"))return true;
                if(!permitStack(link,false))return true;
                if(names(name,"count","f_41587_")&&(!(next instanceof Integer count)||count<0))return true;
                if(next instanceof Tag)walk(link.owner,link.stack.get(),null,true,next);
            }else if(link.kind==Kind.NBT){
                // Numeric/string tags are immutable and can be VM-wide cached values. Legal edits replace their map/list entry.
                if(receiver instanceof Tag&&!(receiver instanceof CompoundTag||receiver instanceof CollectionTag<?>))return true;
                if(receiver instanceof CompoundTag||receiver instanceof ListTag&&selected.field.getType()==List.class)return true;
                if(NODE.isInstance(receiver)&&names(name,"key","hash"))return true;
                if(NODE.isInstance(receiver)&&name.equals("value")&&!(next instanceof Tag))return true;
                if(!permitStack(link,false))return true;
                if(next!=null&&!selected.field.getType().isPrimitive()&&!(next instanceof String))walk(link.owner,link.stack.get(),null,true,next);
            }
        }return false;
    }
    private static boolean structureFieldDenied(Link link,Class<?> declaring,String name,Object next){
        if(link.kind==Kind.INVENTORY&&names(name,"selected","f_35977_")){
            if(!(next instanceof Integer index)||index<0||index>=9)return true;
            if(!permitted(link.owner,0,null,false))return true;
            captureStack(link.owner,itemAt(link.owner,0,index));return false;
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
                if(link.kind==Kind.NBT&&!permitStack(link,false))return true;
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
