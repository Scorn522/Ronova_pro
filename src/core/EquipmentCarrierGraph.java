package dev.ronova.pro;

import java.lang.invoke.*;
import java.lang.ref.*;
import java.lang.reflect.*;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.material.Fluid;
import net.minecraftforge.common.capabilities.*;
import net.minecraftforge.common.util.LazyOptional;
import org.apache.commons.lang3.mutable.MutableObject;

/** A fresh view of actual held capability data, with global/owner references left borrowed. */
final class EquipmentCarrierGraph {
    static final int DATA=1,METADATA=2,STACK=4,NBT=8,EXPOSED=16;
    private static final int MAX_GRAPH=16384;
    record Slot(Field field,Class<?> type,long offset,int width,Object value) { }
    record Span(int from,int to,int stride,int role) {
        boolean contains(int index){return index>=from&&index<to&&(index-from)%stride==0;}
    }
    static final class Node {
        final Object value;int roles;boolean callable;
        final List<Slot> fields=new ArrayList<>();final List<Span> spans=new ArrayList<>();
        Node(Object value,int roles){this.value=value;this.roles=roles;}
        boolean data(){return (roles&DATA)!=0;}
        boolean metadata(){return (roles&METADATA)!=0;}
        boolean contains(int index){for(Span span:spans)if(span.contains(index))return true;return false;}
        boolean overlaps(long offset,long length){
            if(length<=0||!value.getClass().isArray())return false;
            int base=arrayBase(value.getClass()),scale=arrayScale(value.getClass());
            if(offset<0||offset>Long.MAX_VALUE-length)return true;long end=offset+length;
            for(Span span:spans){
                long first=(long)base+(long)scale*span.from,last=(long)base+(long)scale*span.to;
                if(offset>=last||end<=first)continue;
                long at=Math.max(span.from,Math.floorDiv(offset-base,scale));
                long remainder=Math.floorMod(at-span.from,span.stride);if(remainder!=0)at+=span.stride-remainder;
                if(at<span.to&&(long)base+at*scale<end)return true;
            }return false;
        }
    }
    static final class View {
        final IdentityHashMap<Object,Node> nodes=new IdentityHashMap<>();
        private final List<FieldRead> roots=new ArrayList<>();
        private final List<EquipmentCarrierLayouts.ArrayImage> arrays=new ArrayList<>();
        private final Set<EquipmentCarrierLayouts.ArrayImage> retainedArrays=Collections.newSetFromMap(new IdentityHashMap<>());
        private final IdentityHashMap<Object[],EquipmentCarrierLayouts.ArrayImage> ownArrays=new IdentityHashMap<>();
        private int arraySlots;
        Node node(Object value){return nodes.get(value);}
        boolean contains(Object value){return nodes.containsKey(value);}
        List<ItemStack> stacks(){List<ItemStack> result=new ArrayList<>();for(Object value:nodes.keySet())if(value instanceof ItemStack stack&&stack!=ItemStack.EMPTY)result.add(stack);return result;}
        void watch(Object receiver,Slot slot){roots.add(new FieldRead(receiver,slot));}
        void remember(EquipmentCarrierLayouts.ArrayImage image){
            if(!retainedArrays.add(image))return;
            if(image.values.length>MAX_GRAPH*4-arraySlots)throw new IllegalStateException("EQUIPMENT_CARRIER_ARRAY_SLOT_CAPACITY");arraySlots+=image.values.length;
            arrays.add(image);
        }
        EquipmentCarrierLayouts.ArrayImage image(Object[] actual){
            EquipmentCarrierLayouts.ArrayImage before=ownArrays.get(actual);if(before!=null)return before;
            if(actual.length>MAX_GRAPH*4-arraySlots)throw new IllegalStateException("EQUIPMENT_CARRIER_ARRAY_SLOT_CAPACITY");
            var result=new EquipmentCarrierLayouts.ArrayImage(actual);ownArrays.put(actual,result);remember(result);return result;
        }
        View stable(){
            for(FieldRead edge:roots)check(edge.receiver,edge.slot);
            for(Node node:nodes.values())for(Slot slot:node.fields)check(node.value,slot);
            for(var array:arrays)if(!array.current())throw new IllegalStateException("EQUIPMENT_CARRIER_ARRAY_PATH_CHANGED");
            return this;
        }
        private static void check(Object receiver,Slot slot){
            if(!EquipmentCarrierLayouts.sameValue(slot.type,slot.value,read(receiver,slot.field)))throw new IllegalStateException("EQUIPMENT_CARRIER_FIELD_PATH_CHANGED:"+slot.field.getName());
        }
    }
    private record FieldRead(Object receiver,Slot slot) { }
    private record Pending(Object value,int role) { }
    static final class Observation {
        final WeakReference<Object> dispatcher,provider,request,optional;final Object direction;boolean retained;
        Observation(Object dispatcher,Object provider,Object request,Object direction,Object optional){
            this.dispatcher=new WeakReference<>(dispatcher);this.provider=new WeakReference<>(provider);this.request=new WeakReference<>(request);
            this.optional=new WeakReference<>(optional);this.direction=direction;
        }
    }
    private static final Map<Object,List<Observation>> OBSERVED=Collections.synchronizedMap(new WeakIdentityMap<>());
    private static final Map<Class<?>,Boolean> DECLARED=Collections.synchronizedMap(new WeakIdentityMap<>());
    private static MethodHandle FIELDS,READ,DECLARE;private static Object unsafe;private static Method ARRAY_BASE,ARRAY_SCALE;private static volatile boolean ready;
    private EquipmentCarrierGraph(){ }

    static void initialize()throws ReflectiveOperationException{
        if(ready)return;Class<?> bridge=Class.forName("dev.ronova.pro.bootstrap.CarrierBridge",false,null);
        FIELDS=MethodHandles.publicLookup().findStatic(bridge,"fields",MethodType.methodType(Object[][].class,Object.class));
        READ=MethodHandles.publicLookup().findStatic(bridge,"read",MethodType.methodType(Object.class,Object.class,Field.class));
        Class<?> agent=Class.forName("dev.ronova.pro.agent.RecoveryAgent",false,ClassLoader.getSystemClassLoader());
        DECLARE=MethodHandles.publicLookup().findStatic(agent,"declareEquipmentCarrier",MethodType.methodType(void.class,Class.class));
        Class<?> raw=Class.forName("sun.misc.Unsafe",false,ClassLoader.getPlatformClassLoader());Field singleton=raw.getDeclaredField("theUnsafe");singleton.setAccessible(true);unsafe=singleton.get(null);
        ARRAY_BASE=raw.getMethod("arrayBaseOffset",Class.class);ARRAY_SCALE=raw.getMethod("arrayIndexScale",Class.class);
        ready=true;
    }
    static List<Slot> fields(Object receiver){
        final Object[][] values;try{values=(Object[][])FIELDS.invokeExact(receiver);}catch(Throwable failure){throw failure("EQUIPMENT_CARRIER_FIELDS_UNAVAILABLE",failure);}
        List<Slot> slots=new ArrayList<>(values.length);
        for(Object[] row:values){
            if(row==null||row.length!=8||!(row[0] instanceof Field field)||row[1]!=field.getDeclaringClass()||!field.getName().equals(row[2])
                    ||!(row[4] instanceof Class<?> type)||field.getType()!=type||!(row[5] instanceof Long offset)||offset<0||!(row[6] instanceof Integer width)||width<=0)
                throw new IllegalStateException("EQUIPMENT_CARRIER_ACTUAL_FIELD_REQUIRED");
            slots.add(new Slot(field,type,offset,width,row[7]));
        }return List.copyOf(slots);
    }
    static Object read(Object receiver,Field field){try{return (Object)READ.invokeExact(receiver,field);}catch(Throwable failure){throw failure("EQUIPMENT_CARRIER_FIELD_READ_FAILED",failure);}}
    private static RuntimeException failure(String message,Throwable failure){if(failure instanceof Error error)throw error;return new IllegalStateException(message,failure);}
    private static int arrayBase(Class<?> type){try{return (Integer)ARRAY_BASE.invoke(unsafe,type);}catch(ReflectiveOperationException failure){throw new IllegalStateException("EQUIPMENT_CARRIER_ARRAY_LAYOUT",failure);}}
    private static int arrayScale(Class<?> type){try{int scale=(Integer)ARRAY_SCALE.invoke(unsafe,type);if(scale<=0)throw new IllegalStateException("EQUIPMENT_CARRIER_ARRAY_SCALE");return scale;}catch(ReflectiveOperationException failure){throw new IllegalStateException("EQUIPMENT_CARRIER_ARRAY_LAYOUT",failure);}}
    private static Slot find(List<Slot> slots,Class<?> declaring,String name){for(Slot slot:slots)if(slot.field.getDeclaringClass()==declaring&&slot.field.getName().equals(name))return slot;throw new IllegalStateException("EQUIPMENT_CARRIER_FIELD_MISSING:"+declaring.getName()+"#"+name);}
    private static Object value(Object receiver,Class<?> declaring,String name){return find(fields(receiver),declaring,name).value;}

    static View capabilities(ItemStack root){
        View view=new View();if(root==null||root==ItemStack.EMPTY)return view;
        List<Slot> rootFields=fields(root);Slot initialized=find(rootFields,CapabilityProvider.class,"initialized");view.watch(root,initialized);
        if(Boolean.TRUE.equals(initialized.value)){
            Slot dispatcher=find(rootFields,CapabilityProvider.class,"capabilities"),valid=find(rootFields,CapabilityProvider.class,"valid");view.watch(root,dispatcher);view.watch(root,valid);
            if(dispatcher.value!=null)walk(view,root,dispatcher.value,METADATA|(Boolean.TRUE.equals(valid.value)?EXPOSED:0),false);
        }
        if(root.getClass()!=ItemStack.class){Node node=node(view,root,DATA|STACK);node.callable=true;
            for(Slot slot:rootFields)if(slot.field.getDeclaringClass()!=ItemStack.class&&slot.field.getDeclaringClass()!=CapabilityProvider.class){slots(node,List.of(slot));if(!slot.type.isPrimitive()&&slot.value!=null)walk(view,root,slot.value,DATA,false);}}
        return view.stable();
    }
    static View nbt(ItemStack root){
        View view=new View();if(root==null||root==ItemStack.EMPTY)return view;
        for(Slot slot:fields(root))if(slot.field.getDeclaringClass()==ItemStack.class&&Set.of("tag","f_41590_","capNBT").contains(slot.field.getName())
                ||slot.field.getDeclaringClass()==CapabilityProvider.class&&slot.field.getName().equals("lazyData"))
            {view.watch(root,slot);if(slot.value!=null)walk(view,root,slot.value,DATA,false);}
        return view.stable();
    }
    static View candidate(ItemStack root,Object candidate){View view=new View();if(candidate!=null)walk(view,root,candidate,DATA,false);return view.stable();}
    static View curatives(Object root){
        if(!(root instanceof List<?>))throw new IllegalStateException("CURATIVE_LIST_ACTUAL_LIST_REQUIRED");
        View view=new View();walk(view,null,root,DATA,true);return view.stable();
    }
    private static Node node(View view,Object value,int role){
        Node result=view.nodes.get(value);if(result==null){if(view.nodes.size()==MAX_GRAPH)throw new IllegalStateException("EQUIPMENT_CARRIER_GRAPH_CAPACITY");result=new Node(value,role);view.nodes.put(value,result);}else result.roles|=role;return result;
    }
    private static void slots(Node node,List<Slot> slots){for(Slot slot:slots){boolean found=false;for(Slot prior:node.fields)if(prior.field.equals(slot.field)){
        if(!EquipmentCarrierLayouts.sameValue(slot.type,prior.value,slot.value))throw new IllegalStateException("EQUIPMENT_CARRIER_MIXED_FIELD_IMAGE:"+slot.field.getName());found=true;break;
    }if(!found)node.fields.add(slot);}}
    private static void enqueue(ArrayDeque<Pending> pending,Object value,int role){if(value!=null){if(pending.size()>=MAX_GRAPH)throw new IllegalStateException("EQUIPMENT_CARRIER_GRAPH_CAPACITY");pending.addLast(new Pending(value,role));}}
    private static void array(View view,ArrayDeque<Pending> pending,Object value,int from,int to,int stride,int role,boolean children){
        if(value==null||!value.getClass().isArray()||from<0||to<from||to>Array.getLength(value)||stride<=0)throw new IllegalStateException("EQUIPMENT_CARRIER_ACTUAL_ARRAY_RANGE_REQUIRED");
        Node carrier=node(view,value,role);carrier.spans.add(new Span(from,to,stride,role));
        if(children&&!value.getClass().getComponentType().isPrimitive()){
            var image=view.image((Object[])value);for(int at=from;at<to;at+=stride)enqueue(pending,image.at(at),role);
        }
    }
    private static void walk(View view,ItemStack root,Object first,int initialRole,boolean curative){
        ArrayDeque<Pending> pending=new ArrayDeque<>();IdentityHashMap<Object,Integer> visited=new IdentityHashMap<>();enqueue(pending,first,initialRole);
        while(!pending.isEmpty()){
            Pending step=pending.removeFirst();Object actual=step.value;int role=step.role;
            if(actual==root||borrowed(actual,(role&EXPOSED)==0))continue;
            int prior=visited.getOrDefault(actual,0);if((prior&role)==role)continue;visited.put(actual,prior|role);
            Class<?> type=actual.getClass();
            if(type==CapabilityDispatcher.class){
                Node current=node(view,actual,METADATA);List<Slot> fields=fields(actual);slots(current,fields);
                for(String name:List.of("caps","writers")){Object entries=find(fields,CapabilityDispatcher.class,name).value;
                    if(entries!=null){array(view,pending,entries,0,Array.getLength(entries),1,METADATA,false);for(Object provider:view.image((Object[])entries).all())enqueue(pending,provider,DATA|EXPOSED);}}
                Object names=find(fields,CapabilityDispatcher.class,"names").value;if(names!=null)array(view,pending,names,0,Array.getLength(names),1,METADATA,false);
                enqueue(pending,find(fields,CapabilityDispatcher.class,"listeners").value,METADATA);
                if((role&(DATA|EXPOSED))!=0)for(Observation observation:observations(actual))enqueue(pending,observation.optional.get(),METADATA);
                continue;
            }
            if(type==LazyOptional.class){
                List<Slot> fields=fields(actual);Slot supplier=find(fields,LazyOptional.class,"supplier");view.watch(actual,supplier);if(supplier.value==null)continue;
                Node current=node(view,actual,METADATA);slots(current,fields);
                Object resolved=find(fields,LazyOptional.class,"resolved").value;
                if(resolved!=null){Node holder=node(view,resolved,METADATA);List<Slot> holderFields=fields(resolved);slots(holder,holderFields);
                    if(resolved.getClass()!=MutableObject.class)throw new IllegalStateException("EQUIPMENT_CAPABILITY_RESOLVED_CARRIER_UNSUPPORTED");
                    if(Boolean.TRUE.equals(find(fields,LazyOptional.class,"isValid").value))enqueue(pending,find(holderFields,MutableObject.class,"value").value,DATA|EXPOSED);}
                enqueue(pending,find(fields,LazyOptional.class,"listeners").value,METADATA);continue;
            }
            if(type.isArray()){array(view,pending,actual,0,Array.getLength(actual),1,role,(role&METADATA)==0);continue;}
            EquipmentCarrierLayouts.Layout layout=EquipmentCarrierLayouts.layout(actual);
            if(layout!=null){
                if(!layout.shared())node(view,actual,role);
                IdentityHashMap<Object,List<Slot>> read=new IdentityHashMap<>();Set<Field> covered=new HashSet<>();
                for(EquipmentCarrierLayouts.Slot slot:layout.slots()){
                    Object receiver=slot.receiver();Node carrier=node(view,receiver,role);List<Slot> fields=read.computeIfAbsent(receiver,EquipmentCarrierGraph::fields);
                    Slot exact=find(fields,slot.field().getDeclaringClass(),slot.field().getName());
                    if(exact.offset!=slot.offset()||exact.width!=slot.width()||!EquipmentCarrierLayouts.sameValue(exact.type,exact.value,slot.value()))throw new IllegalStateException("EQUIPMENT_CARRIER_LAYOUT_FIELD_CHANGED");slots(carrier,List.of(exact));covered.add(exact.field);
                }
                for(var image:layout.images())view.remember(image);
                for(EquipmentCarrierLayouts.Range range:layout.arrays())array(view,pending,range.array(),range.from(),range.to(),range.stride(),role,false);
                for(EquipmentCarrierLayouts.Edge edge:layout.children())if(edge.storage()){if(edge.value()!=null&&!borrowed(edge.value()))node(view,edge.value(),role);}else if((role&METADATA)==0)enqueue(pending,edge.value(),DATA);
                if(layout.complete())continue;
                List<Slot> extra=new ArrayList<>();for(Slot slot:fields(actual))if(!covered.contains(slot.field))extra.add(slot);
                custom(view,pending,actual,role,extra,false);continue;
            }
            if((role&METADATA)!=0){Node current=node(view,actual,METADATA);slots(current,fields(actual));continue;}
            custom(view,pending,actual,role,fields(actual),curative&&actual==first);
        }
    }
    private static void custom(View view,ArrayDeque<Pending> pending,Object actual,int role,List<Slot> fields,boolean curativeRoot){
        if(actual instanceof ItemStack)role|=STACK;if(actual instanceof Tag)role|=NBT;
        Node current=node(view,actual,role);slots(current,fields);boolean observable=false;
        if(actual instanceof ItemStack stack){
            for(Slot slot:fields){Class<?> declaring=slot.field.getDeclaringClass();String name=slot.field.getName();
                if(declaring==CapabilityProvider.class){if(name.equals("capabilities")&&Boolean.TRUE.equals(find(fields,CapabilityProvider.class,"initialized").value))enqueue(pending,slot.value,METADATA|(Boolean.TRUE.equals(find(fields,CapabilityProvider.class,"valid").value)?EXPOSED:0));else if(name.equals("lazyData"))enqueue(pending,slot.value,DATA);continue;}
                if(!slot.type.isPrimitive())enqueue(pending,slot.value,DATA);
            }
        }else for(Slot slot:fields)if(!slot.type.isPrimitive()){
            if(!borrowedType(slot.type))observable=true;
            enqueue(pending,slot.value,DATA);
        }
        if(curativeRoot&&!observable)throw new IllegalStateException("CURATIVE_LIST_HEAP_CARRIER_UNRESOLVED");
        Class<?> type=actual.getClass();
        if(!EquipmentCarrierLayouts.known(type)&&type.getClassLoader()!=null
                &&(!(actual instanceof Tag)&&!(actual instanceof ItemStack)||type.getModule()!=Entity.class.getModule()))current.callable=true;
    }
    private static boolean borrowed(Object value){return borrowed(value,true);}
    private static boolean borrowed(Object value,boolean callbacks){
        if(value==null||value==ItemStack.EMPTY)return true;Class<?> type=value.getClass();
        return type==String.class||type==Byte.class||type==Short.class||type==Integer.class||type==Long.class||type==Float.class||type==Double.class||type==Boolean.class||type==Character.class
                ||type==java.math.BigInteger.class||type==java.math.BigDecimal.class||type==UUID.class||value instanceof Enum<?>
                ||borrowedType(type)||callbacks&&callback(value)&&!dataCarrier(value);
    }
    // A data container can also implement a callback contract. Its actual held
    // storage remains data; invocation policy still excludes the callback body.
    private static boolean dataCarrier(Object value){
        return value instanceof Collection<?>||value instanceof Map<?,?>||value instanceof ItemStack||value instanceof Tag;
    }
    private static boolean borrowedType(Class<?> type){
        Class<?> host=type.getNestHost();
        if(host==ThreadLocal.class||host==Reference.class||host==ReferenceQueue.class||host==Thread.class||host==ClassLoader.class
                ||host==java.util.concurrent.locks.AbstractQueuedSynchronizer.class||host==java.net.Socket.class)return true;
        return Class.class.isAssignableFrom(type)||Module.class.isAssignableFrom(type)||ClassLoader.class.isAssignableFrom(type)||Thread.class.isAssignableFrom(type)||ThreadGroup.class.isAssignableFrom(type)
                ||Entity.class.isAssignableFrom(type)||Level.class.isAssignableFrom(type)||MinecraftServer.class.isAssignableFrom(type)||BlockEntity.class.isAssignableFrom(type)||ChunkAccess.class.isAssignableFrom(type)
                ||Item.class.isAssignableFrom(type)||Block.class.isAssignableFrom(type)||Fluid.class.isAssignableFrom(type)||BlockState.class.isAssignableFrom(type)
                ||Registry.class.isAssignableFrom(type)||Holder.class.isAssignableFrom(type)||Capability.class.isAssignableFrom(type)
                ||AccessibleObject.class.isAssignableFrom(type)||MethodHandle.class.isAssignableFrom(type)||VarHandle.class.isAssignableFrom(type)
                ||java.lang.ref.Reference.class.isAssignableFrom(type)||java.lang.ref.ReferenceQueue.class.isAssignableFrom(type)||ThreadLocal.class.isAssignableFrom(type)
                ||java.util.concurrent.locks.Lock.class.isAssignableFrom(type)||java.util.concurrent.locks.ReadWriteLock.class.isAssignableFrom(type)||java.util.concurrent.locks.AbstractOwnableSynchronizer.class.isAssignableFrom(type)
                ||java.net.Socket.class.isAssignableFrom(type)||java.net.SocketImpl.class.isAssignableFrom(type)||java.net.ServerSocket.class.isAssignableFrom(type)||java.net.DatagramSocket.class.isAssignableFrom(type)
                ||java.nio.channels.Channel.class.isAssignableFrom(type)||io.netty.channel.Channel.class.isAssignableFrom(type)||java.nio.Buffer.class.isAssignableFrom(type)
                ||java.security.ProtectionDomain.class.isAssignableFrom(type)
                ||net.minecraft.resources.ResourceLocation.class.isAssignableFrom(type)||net.minecraft.resources.ResourceKey.class.isAssignableFrom(type);
    }
    private static boolean callback(Object value){
        if(value instanceof Runnable||value instanceof java.util.concurrent.Callable<?>||value instanceof Comparator<?>)return true;
        for(Class<?> type=value.getClass();type!=null;type=type.getSuperclass())for(Class<?> contract:type.getInterfaces())if(function(contract))return true;
        return false;
    }
    private static boolean function(Class<?> type){
        if(type.getPackageName().equals("java.util.function")||type==net.minecraftforge.common.util.NonNullSupplier.class||type==net.minecraftforge.common.util.NonNullConsumer.class||type==net.minecraftforge.common.util.NonNullFunction.class)return true;
        for(Class<?> contract:type.getInterfaces())if(function(contract))return true;return false;
    }
    static void declare(View view){
        for(Node node:view.nodes.values())if(node.callable)declare(node.value.getClass(),new IdentityHashMap<>());
    }
    private static void declare(Class<?> type,IdentityHashMap<Class<?>,Boolean> seen){
        if(type==null||type==Object.class||type.getClassLoader()==null||borrowedType(type)||type.isInterface()&&function(type)||type==Runnable.class||type==java.util.concurrent.Callable.class||seen.put(type,Boolean.TRUE)!=null)return;
        if(!DECLARED.containsKey(type))try{DECLARE.invokeExact(type);DECLARED.put(type,Boolean.TRUE);}catch(Throwable failure){throw failure("EQUIPMENT_CARRIER_ACTION_INSTALL_FAILED",failure);}
        for(Class<?> contract:type.getInterfaces())declare(contract,seen);declare(type.getSuperclass(),seen);
    }
    static boolean declared(Class<?> type){return DECLARED.containsKey(type);}
    static boolean providerCurrent(Object dispatcher,Object provider){
        if(dispatcher==null||dispatcher.getClass()!=CapabilityDispatcher.class||provider==null)return false;
        Object caps=value(dispatcher,CapabilityDispatcher.class,"caps");if(caps instanceof Object[] entries)for(Object actual:entries)if(actual==provider)return true;return false;
    }
    static Observation observe(Object dispatcher,Object provider,Object request,Object direction,Object optional){
        if(optional==null||optional.getClass()!=LazyOptional.class||request==null||!providerCurrent(dispatcher,provider))return null;
        Observation observed=new Observation(dispatcher,provider,request,direction,optional);
        synchronized(OBSERVED){List<Observation> entries=new ArrayList<>(OBSERVED.getOrDefault(dispatcher,List.of()));entries.removeIf(value->!valid(value));entries.add(observed);OBSERVED.put(dispatcher,List.copyOf(entries));}
        return observed;
    }
    static void forget(Observation observation){
        if(observation==null)return;Object dispatcher=observation.dispatcher.get();if(dispatcher==null)return;
        synchronized(OBSERVED){List<Observation> entries=new ArrayList<>(OBSERVED.getOrDefault(dispatcher,List.of()));entries.removeIf(value->value==observation||!valid(value));if(entries.isEmpty())OBSERVED.remove(dispatcher);else OBSERVED.put(dispatcher,List.copyOf(entries));}
    }
    static void retain(Observation observation){
        if(observation==null)return;Object dispatcher=observation.dispatcher.get();if(dispatcher==null)return;
        synchronized(OBSERVED){List<Observation> entries=new ArrayList<>();for(Observation prior:OBSERVED.getOrDefault(dispatcher,List.of())){
            if(!valid(prior))continue;
            if(prior!=observation&&prior.retained&&prior.provider.get()==observation.provider.get()&&prior.request.get()==observation.request.get()&&prior.direction==observation.direction)continue;
            entries.add(prior);
        }observation.retained=true;OBSERVED.put(dispatcher,List.copyOf(entries));}
    }
    private static boolean valid(Observation observation){
        Object dispatcher=observation.dispatcher.get(),provider=observation.provider.get(),optional=observation.optional.get();
        return dispatcher!=null&&provider!=null&&observation.request.get()!=null&&optional!=null&&providerCurrent(dispatcher,provider)&&Boolean.TRUE.equals(value(optional,LazyOptional.class,"isValid"));
    }
    private static List<Observation> observations(Object dispatcher){
        synchronized(OBSERVED){List<Observation> entries=new ArrayList<>();for(Observation observation:OBSERVED.getOrDefault(dispatcher,List.of()))if(valid(observation))entries.add(observation);
            if(entries.isEmpty())OBSERVED.remove(dispatcher);else OBSERVED.put(dispatcher,List.copyOf(entries));return entries;}
    }
}
