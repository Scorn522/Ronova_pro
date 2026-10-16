package dev.ronova.pro;

import java.lang.invoke.*;
import java.lang.ref.WeakReference;
import java.lang.reflect.*;
import java.util.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.item.ItemStack;

/** Actual reads of one naturally executed slot getter; no entity heap search or getter replay. */
final class EquipmentSlotGraph {
    sealed interface Read permits FieldRead,ArrayRead,ListRead,MapRead,ContainerRead,BuiltinRead { Object receiver();Object value(); }
    record FieldRead(Object receiver,Field field,Object value) implements Read { }
    record ArrayRead(Object array,int index,Object value) implements Read { public Object receiver(){return array;} }
    record ListRead(Object list,int index,Object value) implements Read { public Object receiver(){return list;} }
    record MapRead(Object map,Object key,Object value) implements Read { public Object receiver(){return map;} }
    private record MethodImage(long method,long constantMethod,long constants,long length) {
        static MethodImage of(long[] values){return values!=null&&values.length==4&&values[0]!=0&&values[1]!=0&&values[2]!=0&&values[3]>0?new MethodImage(values[0],values[1],values[2],values[3]):null;}
    }
    private record Definition(Class<?> declaring,String name,String descriptor,MethodImage image) {
        boolean current(){return image!=null&&image.equals(methodImage(declaring,name,descriptor));}
    }
    private record Query(boolean map,Class<?> symbolic,String name,String descriptor,int opcode,Class<?> caller,Class<?> body,MethodImage image) { }
    private record ContainerRead(Object receiver,Object key,Object value,Query query,KeySnapshot keys) implements Read { }
    private record BuiltinRead(Object receiver,Object value,BuiltinCall call) implements Read { }
    record KeyTarget(Object receiver,Class<?> declaring,String name,String descriptor) { }
    static final class MethodRead {
        private final Definition definition;
        private MethodRead(Definition definition){this.definition=definition;}
        private void current(){if(!definition.current())throw unsupported("KEY_METHOD_CHANGED");}
    }
    private record KeyStep(Object receiver,Query query,Class<?> layout) { }
    static final class QueryKeys {
        private final Object receiver,key,semanticReceiver;private final Query query;
        private final List<KeyStep> steps;private final List<HeldRead> connections;private final List<KeyTarget> targets;
        private final List<MethodRead> methods=new ArrayList<>();
        private QueryKeys(Object receiver,Object key,Object semanticReceiver,Query query,List<KeyStep> steps,List<HeldRead> connections,List<KeyTarget> targets){
            this.receiver=receiver;this.key=key;this.semanticReceiver=semanticReceiver;this.query=query;this.steps=List.copyOf(steps);this.connections=List.copyOf(connections);this.targets=List.copyOf(targets);
            for(KeyStep step:steps)methods.add(new MethodRead(new Definition(step.query.body,step.query.name,step.query.descriptor,step.query.image)));
        }
        private void current(Uses uses){
            for(MethodRead method:methods)method.current();for(KeyStep step:steps)if(queryBase(step.receiver,step.query)==null)throw unsupported("KEY_QUERY_CHANGED");
            for(HeldRead read:connections)if(!read.value.same(read.current(uses,false)))throw unsupported("KEY_QUERY_CONNECTION_CHANGED");
            for(MethodRead method:methods)method.current();
        }
    }
    private record KeySnapshot(List<HeldRead> connections,List<MethodRead> methods,List<KeySemantics> semantics) {
        private void current(Uses uses){for(MethodRead method:methods)method.current();for(HeldRead read:connections)if(!read.value.same(read.current(uses,false)))throw unsupported("KEY_QUERY_CONNECTION_CHANGED");for(KeySemantics semantic:semantics)semantic.current(uses);for(MethodRead method:methods)method.current();}
    }
    static final class KeySemantics {
        private final Value receiver;private final List<Value> arguments;private final String name,descriptor;private final Object result;
        private final List<HeldRead> reads;private final List<MethodRead> methods;
        private KeySemantics(Object receiver,String name,String descriptor,Object[] arguments,Object result,List<Read> reads,List<MethodRead> methods){
            this.receiver=new Value(receiver,false);this.name=name;this.descriptor=descriptor;this.result=result;List<Value> values=new ArrayList<>();for(Object argument:arguments)values.add(new Value(argument,false));this.arguments=List.copyOf(values);
            List<HeldRead> held=new ArrayList<>();for(Read read:reads)held.add(new HeldRead(read));this.reads=List.copyOf(held);this.methods=List.copyOf(methods);
        }
        private boolean matches(Object receiver,String name,String descriptor,Object[] arguments){
            if(!this.receiver.same(receiver)||!this.name.equals(name)||!this.descriptor.equals(descriptor)||this.arguments.size()!=arguments.length)return false;
            for(int at=0;at<arguments.length;at++)if(!this.arguments.get(at).same(arguments[at]))return false;return true;
        }
        private Object current(Uses uses){
            if(!receiver.alive())throw unsupported("DETACHED_KEY_RECEIVER");for(Value value:arguments)if(!value.alive())throw unsupported("DETACHED_KEY_ARGUMENT");
            for(MethodRead method:methods)method.current();for(HeldRead read:reads)if(!read.value.same(read.current(uses,false)))throw unsupported("KEY_READ_CHANGED");
            for(MethodRead method:methods)method.current();return result;
        }
    }
    private record BuiltinCall(String name,String descriptor,List<Value> arguments,List<MethodRead> methods) {
        private Object current(Uses uses,Object receiver){
            for(MethodRead method:methods)method.current();
            if(name.equals("compare")&&(receiver==Integer.class||receiver==Long.class)){
                long first=receiver==Integer.class?((Integer)arguments.get(0).get()).intValue():((Long)arguments.get(0).get()).longValue();
                long second=receiver==Integer.class?((Integer)arguments.get(1).get()).intValue():((Long)arguments.get(1).get()).longValue();
                int result=first<second?-1:first==second?0:1;for(MethodRead method:methods)method.current();return Integer.valueOf(result);
            }
            Object argument=arguments.isEmpty()?null:arguments.get(0).get();
            if(!arguments.isEmpty()&&!arguments.get(0).alive())throw unsupported("DETACHED_BUILTIN_KEY_ARGUMENT");Object result;
            if(name.equals("hashCode"))result=Integer.valueOf(keyHash(uses,receiver));
            else if(name.equals("equals"))result=Boolean.valueOf(keyEqual(uses,receiver,argument));
            else if(receiver instanceof Enum<?> first){if(!(argument instanceof Enum<?> second)||enumType(first)!=enumType(second))throw unsupported("ENUM_COMPARE_TYPE");result=Integer.valueOf(enumOrdinal(uses,first)-enumOrdinal(uses,second));}
            else result=Integer.valueOf(keyComparable(uses,receiver,argument));
            for(MethodRead method:methods)method.current();return result;
        }
    }
    record FieldUse(Object receiver,Field field,long offset,int width,int mask,boolean terminal) { }
    record ArrayUse(Object array,int from,int to,int stride,int mask,boolean terminal) { }
    static final class View {
        private final ItemStack[] stacks=new ItemStack[6];
        private final List<FieldUse> fields=new ArrayList<>();private final List<ArrayUse> arrays=new ArrayList<>();
        ItemStack stack(EquipmentSlot slot){int at=ordinal(slot);return at<0?null:stacks[at];}
        int resolvedMask(){int mask=0;for(int at=0;at<6;at++)if(stacks[at]!=null)mask|=1<<at;return mask;}
        List<FieldUse> fields(){return List.copyOf(fields);}List<ArrayUse> arrays(){return List.copyOf(arrays);}
        boolean contains(Object receiver){for(FieldUse use:fields)if(use.receiver==receiver)return true;for(ArrayUse use:arrays)if(use.array==receiver)return true;return false;}
        int mask(Object receiver){int mask=0;for(FieldUse use:fields)if(use.receiver==receiver)mask|=use.mask;for(ArrayUse use:arrays)if(use.array==receiver)mask|=use.mask;return mask;}
        int fieldMask(Object receiver,Class<?> declaring,String name){int mask=0;for(FieldUse use:fields)if(use.receiver==receiver&&use.field.getDeclaringClass()==declaring&&use.field.getName().equals(name))mask|=use.mask;return mask;}
        int arrayMask(Object array,int index){int mask=0;for(ArrayUse use:arrays)if(use.array==array&&index>=use.from&&index<use.to&&(index-use.from)%use.stride==0)mask|=use.mask;return mask;}
        int listMask(Object receiver,int index){try{Uses selected=new Uses(0);list(selected,receiver,index,true,new IdentityHashMap<>());int mask=0;
            for(FieldUse use:selected.fields)if(use.terminal)for(FieldUse actual:fields)if(actual.terminal&&actual.receiver==use.receiver&&actual.field.equals(use.field))mask|=actual.mask;
            for(ArrayUse use:selected.arrays)if(use.terminal)mask|=arrayMask(use.array,use.from);return mask;
        }catch(Unresolved unavailable){return 0;}}
        int memoryMask(Object receiver,long offset,long length){
            int mask=0;for(FieldUse use:fields)if(use.receiver==receiver&&overlap(offset,length,use.offset,use.width))mask|=use.mask;
            if(receiver!=null&&receiver.getClass().isArray()){int base=arrayBase(receiver.getClass()),scale=arrayScale(receiver.getClass());
                for(ArrayUse use:arrays)if(use.array==receiver)for(int at=use.from;at<use.to;at+=use.stride)if(overlap(offset,length,(long)base+(long)scale*at,scale)){mask|=use.mask;break;}}
            return mask;
        }
    }
    private static final int LIMIT=4096;
    private static final String C="java.util.Collections$",I="java.util.ImmutableCollections$",G="com.google.common.collect.";
    private static final EquipmentSlot[] SIX={EquipmentSlot.MAINHAND,EquipmentSlot.OFFHAND,EquipmentSlot.FEET,EquipmentSlot.LEGS,EquipmentSlot.CHEST,EquipmentSlot.HEAD};
    private static final Map<LivingEntity,State> STATES=Collections.synchronizedMap(new WeakIdentityMap<>());
    private static final MethodHandle READ,STATIC,METHOD,IMAGE,FRAME,DEPTH;private static final Object UNSAFE;private static final Method BASE,SCALE,STATIC_BASE,STATIC_OFFSET;
    static {
        try {Class<?> bridge=Class.forName("dev.ronova.pro.bootstrap.CarrierBridge",false,null);var lookup=MethodHandles.publicLookup();
            READ=lookup.findStatic(bridge,"read",MethodType.methodType(Object.class,Object.class,Field.class));
            STATIC=lookup.findStatic(bridge,"staticValue",MethodType.methodType(Object.class,Class.class,String.class,String.class));
            METHOD=lookup.findStatic(bridge,"method",MethodType.methodType(Class.class,Object.class,Class.class,String.class,String.class,int.class,Class.class));
            IMAGE=lookup.findStatic(bridge,"methodImage",MethodType.methodType(long[].class,Class.class,String.class,String.class));
            FRAME=lookup.findStatic(bridge,"frame",MethodType.methodType(long[].class,Class.class,String.class,String.class,int.class));
            DEPTH=lookup.findStatic(bridge,"callbackDepth",MethodType.methodType(int.class,Class.class,String.class,String.class));
            Class<?> type=Class.forName("sun.misc.Unsafe",false,ClassLoader.getPlatformClassLoader());Field singleton=type.getDeclaredField("theUnsafe");singleton.setAccessible(true);UNSAFE=singleton.get(null);
            BASE=type.getMethod("arrayBaseOffset",Class.class);SCALE=type.getMethod("arrayIndexScale",Class.class);
            STATIC_BASE=type.getMethod("staticFieldBase",Field.class);STATIC_OFFSET=type.getMethod("staticFieldOffset",Field.class);
        }catch(ReflectiveOperationException failure){throw new ExceptionInInitializerError(failure);}
    }
    private EquipmentSlotGraph(){ }
    private static final class State {final long generation;final Binding[] bindings=new Binding[6];State(long generation){this.generation=generation;}}
    private static final class Value {
        final Object scalar;final WeakReference<Object> reference;final boolean primitive,wasNull;
        Value(Object value,boolean primitive){this.primitive=primitive;scalar=primitive?value:null;reference=new WeakReference<>(primitive?null:value);wasNull=value==null;}
        Object get(){return primitive?scalar:reference.get();}boolean alive(){return primitive||wasNull||reference.get()!=null;}
        boolean same(Object actual){return alive()&&(primitive?samePrimitive(scalar,actual):reference.get()==actual);}
    }
    private static final class HeldRead {
        final int kind,index;final WeakReference<Object> receiver,key;final boolean nullKey;final Field field;final Value value;final Query query;final KeySnapshot keys;final BuiltinCall builtin;
        HeldRead(Read read){receiver=new WeakReference<>(read.receiver());field=read instanceof FieldRead f?f.field:null;query=read instanceof ContainerRead c?c.query:null;
            keys=read instanceof ContainerRead c?c.keys:null;builtin=read instanceof BuiltinRead b?b.call:null;
            index=read instanceof ArrayRead a?a.index:read instanceof ListRead l?l.index:read instanceof ContainerRead c&&!c.query.map&&c.key instanceof Integer i?i.intValue():-1;
            Object actualKey=read instanceof MapRead m?m.key:read instanceof ContainerRead c&&c.query.map?c.key:null;key=new WeakReference<>(actualKey);nullKey=actualKey==null;
            kind=read instanceof FieldRead?0:read instanceof ArrayRead?1:read instanceof BuiltinRead?4:read instanceof ListRead||query!=null&&!query.map?2:3;
            value=new Value(read.value(),builtin!=null|| (field!=null?field.getType().isPrimitive():read instanceof ArrayRead a&&a.array.getClass().getComponentType().isPrimitive()));}
        Object current(Uses uses,boolean terminal){Object holder=receiver.get();if(holder==null)throw unsupported("DETACHED_READ");
            if(kind==0)return uses.field(holder,field,terminal);
            if(kind==1)return uses.array(holder,index,terminal);
            if(kind==4)return builtin.current(uses,holder);
            List<KeySemantics> previous=uses.keys;Object result;
            try {
                if(keys!=null){keys.current(uses);uses.keys=keys.semantics;}
                if(kind==2)result=list(uses,holder,index,terminal,new IdentityHashMap<>(),query);
                else {Object actualKey=key.get();if(actualKey==null&&!nullKey)throw unsupported("DETACHED_KEY");result=map(uses,holder,actualKey,terminal,new IdentityHashMap<>(),query);}
                if(keys!=null)keys.current(uses);
                if(query!=null&&!query.image.equals(methodImage(query.body,query.name,query.descriptor)))throw unsupported("QUERY_DEFINITION_CHANGED");return result;
            }finally{uses.keys=previous;}
        }
        boolean sameAddress(HeldRead other){return kind==other.kind&&Objects.equals(query,other.query)&&Objects.equals(builtin,other.builtin)&&receiver.get()!=null&&receiver.get()==other.receiver.get()&&(kind==0?field.equals(other.field):kind==3?key.get()==other.key.get():index==other.index);}
    }
    private static final class Binding {
        final List<HeldRead> reads;final int terminal;final Definition definition;
        Binding(List<Read> reads,int terminal,Definition definition){List<HeldRead> copy=new ArrayList<>();for(Read read:reads)copy.add(new HeldRead(read));this.reads=List.copyOf(copy);this.terminal=terminal;this.definition=definition;}
        ItemStack resolve(Uses uses){if(definition!=null&&!definition.current())throw unsupported("GETTER_DEFINITION_CHANGED");HeldRead selected=reads.get(terminal);Object result=null;
            for(HeldRead read:reads){boolean actual=read.sameAddress(selected);Object value=read.current(uses,actual);if(actual){if(!(value instanceof ItemStack))throw unsupported("SLOT_VALUE");result=value;}else if(!read.value.same(value))throw unsupported("CHANGED_READ");}
            if(definition!=null&&!definition.current())throw unsupported("GETTER_DEFINITION_CHANGED");
            if(!(result instanceof ItemStack stack))throw unsupported("MISSING_TERMINAL");return stack;
        }
    }
    private record Replacement(Object receiver,Class<?> declaring,String name,int index,Object value) {
        boolean field(Object actual,Field field){return receiver==actual&&declaring==field.getDeclaringClass()&&field.getName().equals(name);}
    }
    static int fieldChangeMask(LivingEntity entity,long generation,Object receiver,Class<?> declaring,String name,Object next){return changeMask(entity,generation,new Replacement(receiver,declaring,name,-1,next));}
    static int arrayChangeMask(LivingEntity entity,long generation,Object array,int index,Object next){return changeMask(entity,generation,new Replacement(array,null,null,index,next));}
    private static int changeMask(LivingEntity entity,long generation,Replacement replacement){
        State state=STATES.get(entity);if(state==null||state.generation!=generation||ProRuntime.liveStateGeneration(entity)!=generation)return 0;Binding[] bindings;synchronized(STATES){bindings=state.bindings.clone();}int mask=0;
        for(int at=0;at<6;at++)if(bindings[at]!=null){Uses before=new Uses(1<<at);try{ItemStack current=bindings[at].resolve(before);boolean touched=false;
                if(replacement.declaring!=null){for(FieldUse use:before.fields)if(replacement.field(use.receiver,use.field)){touched=true;break;}}
                else for(ArrayUse use:before.arrays)if(use.array==replacement.receiver&&replacement.index>=use.from&&replacement.index<use.to&&(replacement.index-use.from)%use.stride==0){touched=true;break;}
                if(touched){try{if(bindings[at].resolve(new Uses(1<<at,replacement))!=current)mask|=1<<at;}catch(Unresolved changed){mask|=1<<at;}}
            }catch(Unresolved detached){ /* A disconnected old path cannot protect its old carrier. */ }}
        return mask;
    }
    static int ordinal(EquipmentSlot slot){for(int at=0;at<6;at++)if(SIX[at]==slot)return at;return -1;}
    static EquipmentSlot slot(int at){return at>=0&&at<6?SIX[at]:null;}
    static void publish(LivingEntity entity,long generation){synchronized(STATES){if(generation<=0){STATES.remove(entity);return;}State state=STATES.get(entity);if(state==null||state.generation!=generation)STATES.put(entity,new State(generation));}}
    static void forget(LivingEntity entity,long generation,EquipmentSlot slot){synchronized(STATES){State state=STATES.get(entity);int at=ordinal(slot);if(state!=null&&state.generation==generation&&at>=0)state.bindings[at]=null;}}
    static boolean observe(LivingEntity entity,long generation,EquipmentSlot slot,ItemStack result,List<Read> reads){
        return observe(entity,generation,slot,result,reads,null);
    }
    static boolean observe(LivingEntity entity,long generation,EquipmentSlot slot,ItemStack result,List<Read> reads,Class<?> declaring,String name,String descriptor,long[] image){
        MethodImage actual=MethodImage.of(image);
        if(declaring==null||name==null||descriptor==null||actual==null){forget(entity,generation,slot);return false;}
        return observe(entity,generation,slot,result,reads,new Definition(declaring,name,descriptor,actual));
    }
    private static boolean observe(LivingEntity entity,long generation,EquipmentSlot slot,ItemStack result,List<Read> reads,Definition definition){
        int at=ordinal(slot);if(entity==null||at<0||result==null||reads==null||reads.isEmpty()||reads.size()>LIMIT||ProRuntime.liveStateGeneration(entity)!=generation)return false;
        try {
            if(definition!=null&&!definition.current())throw unsupported("GETTER_DEFINITION_CHANGED");
            IdentityHashMap<Object,Boolean> rooted=new IdentityHashMap<>();rooted.put(entity,Boolean.TRUE);List<HeldRead> held=new ArrayList<>();int terminal=-1;
            for(int index=0;index<reads.size();index++){
                Read read=reads.get(index);if(read==null||read.receiver()==null)throw unsupported("NULL_READ");HeldRead actual=new HeldRead(read);held.add(actual);
                Object value=actual.current(new Uses(1),false);if(!actual.value.same(value))throw unsupported("READ_CHANGED");
                boolean staticRead=read instanceof FieldRead f&&Modifier.isStatic(f.field.getModifiers());boolean connected=!staticRead&&rooted.containsKey(read.receiver());
                if(!connected&&!dependencyReceiver(read.receiver(),slot,reads,index))throw unsupported("BORROWED_READ");
                if(connected&&value==result){if(terminal>=0&&!actual.sameAddress(held.get(terminal)))throw unsupported("AMBIGUOUS_TERMINAL");terminal=index;}
                if(connected&&value!=null&&!(value instanceof ItemStack)&&!scalar(value)&&!borrowed(value))rooted.put(value,Boolean.TRUE);
            }
            if(terminal<0)throw unsupported("NO_ROOTED_SLOT");Binding binding=new Binding(reads,terminal,definition);Uses uses=new Uses(1<<at);if(binding.resolve(uses)!=result)throw unsupported("RETURN_CHANGED");
            synchronized(STATES){State state=STATES.get(entity);if(state==null||state.generation!=generation)return false;state.bindings[at]=binding;}return true;
        }catch(Unresolved failure){forget(entity,generation,slot);return false;}
    }
    private static boolean dependencyReceiver(Object receiver,EquipmentSlot slot,List<Read> reads,int before){
        if(receiver==slot||receiver==EquipmentSlot.Type.HAND||receiver==EquipmentSlot.Type.ARMOR||receiver instanceof Class<?>)return true;
        // Compiler switch arrays and their values are dependencies only, never storage roots.
        if(receiver.getClass().isArray())for(int at=0;at<before;at++)if(reads.get(at).value()==receiver&&reads.get(at) instanceof FieldRead field&&Modifier.isStatic(field.field.getModifiers())&&receiver.getClass().getComponentType().isPrimitive())return true;
        return false;
    }
    static boolean helperReceiver(LivingEntity entity,EquipmentSlot slot,Object receiver,List<Read> reads){
        if(receiver==entity||receiver==slot||receiver==EquipmentSlot.Type.HAND||receiver==EquipmentSlot.Type.ARMOR)return true;
        IdentityHashMap<Object,Boolean> rooted=new IdentityHashMap<>();rooted.put(entity,Boolean.TRUE);
        for(Read read:reads)if(!(read instanceof FieldRead field&&Modifier.isStatic(field.field().getModifiers()))&&rooted.containsKey(read.receiver())){
            Object value=read.value();if(value!=null&&!(value instanceof ItemStack)&&!scalar(value)&&!borrowed(value))rooted.put(value,Boolean.TRUE);
        }
        return rooted.containsKey(receiver);
    }
    static ItemStack stack(LivingEntity entity,long generation,EquipmentSlot slot){return view(entity,generation).stack(slot);}
    static View view(LivingEntity entity,long generation){
        View result=new View();if(entity==null||generation<=0||ProRuntime.liveStateGeneration(entity)!=generation)return result;State state=STATES.get(entity);if(state==null||state.generation!=generation)return result;
        Binding[] bindings; synchronized(STATES){bindings=state.bindings.clone();}
        for(int at=0;at<6;at++)if(bindings[at]!=null){Uses uses=new Uses(1<<at);try{ItemStack stack=bindings[at].resolve(uses);result.stacks[at]=stack;result.fields.addAll(uses.fields);result.arrays.addAll(uses.arrays);}
            catch(Unresolved stale){synchronized(STATES){if(state.bindings[at]==bindings[at])state.bindings[at]=null;}}}
        return result;
    }
    /** Resolve the symbolic JVM field against real instance metadata, never a custom accessor. */
    static FieldRead fieldRead(Object holder,Class<?> symbolic,String name,String descriptor,Object result){
        if(holder==null||symbolic==null||name==null||descriptor==null)throw unsupported("FIELD_TUPLE");Field selected=null;
        if(holder instanceof Class<?> actual){
            if(actual!=symbolic)throw unsupported("STATIC_HOLDER");
            for(Class<?> at=symbolic;at!=null;at=at.getSuperclass())try{Field field=at.getDeclaredField(name);if(!Modifier.isStatic(field.getModifiers())||!field.getType().descriptorString().equals(descriptor))throw unsupported("STATIC_DESCRIPTOR");selected=field;break;}catch(NoSuchFieldException ignored){ }
        }else {
            if(!symbolic.isInstance(holder))throw unsupported("SYMBOLIC_RECEIVER");
            for(var slot:EquipmentCarrierGraph.fields(holder))if(slot.field().getName().equals(name)&&slot.field().getType().descriptorString().equals(descriptor)&&slot.field().getDeclaringClass().isAssignableFrom(symbolic))
                if(selected==null||selected.getDeclaringClass().isAssignableFrom(slot.field().getDeclaringClass()))selected=slot.field();
        }
        if(selected==null)throw unsupported("ACTUAL_FIELD");Object current=read(holder,selected);
        if(selected.getType().isPrimitive()?!samePrimitive(current,result):current!=result)throw unsupported("FIELD_VALUE");return new FieldRead(holder,selected,result);
    }
    static boolean containerSupported(Object value,boolean map){try{return queryBase(value,map)!=null;}catch(Unresolved unavailable){return false;}}
    static boolean containerSupported(Object receiver,boolean map,Class<?> symbolic,String name,String descriptor,int opcode,Class<?> caller){
        try{return query(receiver,map,symbolic,name,descriptor,opcode,caller)!=null;}catch(Unresolved unavailable){return false;}
    }
    static Read containerRead(Object receiver,Object key,Object value,boolean map,Class<?> symbolic,String name,String descriptor,int opcode,Class<?> caller){
        Query query=query(receiver,map,symbolic,name,descriptor,opcode,caller);
        if(query==null||!map&&!(key instanceof Integer))throw unsupported("QUERY_CONTRACT");
        return new ContainerRead(receiver,key,value,query,null);
    }
    static MethodRead methodRead(Class<?> declaring,String name,String descriptor,long[] image){
        MethodImage actual=MethodImage.of(image);if(declaring==null||name==null||descriptor==null||actual==null)throw unsupported("KEY_METHOD_IMAGE");
        MethodRead read=new MethodRead(new Definition(declaring,name,descriptor,actual));read.current();return read;
    }
    private static MethodRead methodRead(Class<?> declaring,String name,String descriptor){
        MethodImage image=methodImage(declaring,name,descriptor);if(image==null)throw unsupported("KEY_METHOD_IMAGE");return new MethodRead(new Definition(declaring,name,descriptor,image));
    }
    static KeySemantics keySemantics(Object receiver,String name,String descriptor,Object[] arguments,Object result,List<Read> reads,List<MethodRead> methods){
        int kind=keyKind(name,descriptor);if(receiver==null||arguments==null||arguments.length!=(kind==0?0:1)||kind<0
                ||(kind==1?!(result instanceof Boolean):!(result instanceof Integer))||reads==null||reads.size()>LIMIT||methods==null||methods.isEmpty()||methods.size()>LIMIT)throw unsupported("KEY_SEMANTICS_TUPLE");
        MethodRead first=methods.get(0);Class<?> symbolic=kind==2?Comparable.class:Object.class;int opcode=kind==2?185:182;
        if(first==null||!first.definition.name.equals(name)||!first.definition.descriptor.equals(descriptor)
                ||dispatch(receiver,symbolic,name,descriptor,opcode,EquipmentSlotGraph.class)!=first.definition.declaring)throw unsupported("KEY_SEMANTICS_DISPATCH");
        for(Read read:reads)if(read==null||read.receiver()==null)throw unsupported("KEY_SEMANTICS_READ");
        KeySemantics semantics=new KeySemantics(receiver,name,descriptor,arguments,result,reads,methods);semantics.current(new Uses(0));return semantics;
    }
    static List<KeyTarget> keyTargets(QueryKeys keys){return keys==null?List.of():keys.targets;}
    static QueryKeys prepareKeys(Object receiver,Object key,Class<?> symbolic,String name,String descriptor,int opcode,Class<?> caller){
        Query root=query(receiver,true,symbolic,name,descriptor,opcode,caller);if(root==null)throw unsupported("KEY_QUERY_CONTRACT");
        List<KeyStep> steps=new ArrayList<>();List<HeldRead> connections=new ArrayList<>();IdentityHashMap<Object,Boolean> seen=new IdentityHashMap<>();Object at=receiver;Query contract=root;Class<?> layout;
        while(true){
            layout=queryBase(at,contract);if(layout==null||seen.put(at,Boolean.TRUE)!=null||steps.size()>=16)throw unsupported("KEY_QUERY_PATH");steps.add(new KeyStep(at,contract,layout));
            String type=layout.getName();String owner=null,field=null;
            if(type.startsWith(C+"Unmodifiable")||type.startsWith(C+"Synchronized")){owner=C+(type.startsWith(C+"Unmodifiable")?"Unmodifiable":"Synchronized")+"Map";field="m";}
            else if(type.equals(G+"JdkBackedImmutableMap")){owner=type;field="delegateMap";}
            if(owner==null)break;FieldRead read=knownField(at,owner,field);connections.add(new HeldRead(read));at=read.value;
            contract=query(at,true,Map.class,"get","(Ljava/lang/Object;)Ljava/lang/Object;",185,contract.body);if(contract==null)throw unsupported("KEY_QUERY_DELEGATE");
        }
        Object semantic=key;String leaf=layout.getName();
        if(leaf.equals(G+"SingletonImmutableBiMap")){FieldRead stored=knownField(at,leaf,"singleKey");connections.add(new HeldRead(stored));semantic=stored.value;}
        List<KeyTarget> targets=new ArrayList<>();boolean identity=layout==IdentityHashMap.class||layout==EnumMap.class||leaf.equals(C+"EmptyMap");
        if(!identity&&!builtinKey(semantic)&&semantic!=null){
            if(layout==HashMap.class||layout==LinkedHashMap.class||leaf.equals(I+"MapN")||leaf.equals(G+"RegularImmutableMap"))keyTarget(targets,semantic,"hashCode","()I",Object.class,182);
            keyTarget(targets,semantic,"equals","(Ljava/lang/Object;)Z",Object.class,182);
            if((layout==HashMap.class||layout==LinkedHashMap.class)&&selfComparable(semantic))keyTarget(targets,semantic,"compareTo","(Ljava/lang/Object;)I",Comparable.class,185);
        }
        QueryKeys keys=new QueryKeys(receiver,key,semantic,root,steps,connections,targets);
        if(!identity&&!builtinKey(semantic)&&semantic!=null&&identityMethod(semantic,"equals"))keys.methods.add(methodRead(Object.class,"equals","(Ljava/lang/Object;)Z"));
        keys.current(new Uses(0));return keys;
    }
    private static void keyTarget(List<KeyTarget> targets,Object receiver,String name,String descriptor,Class<?> symbolic,int opcode){
        Class<?> actual=dispatch(receiver,symbolic,name,descriptor,opcode,EquipmentSlotGraph.class);
        if(actual!=null&&actual.getClassLoader()!=null)targets.add(new KeyTarget(receiver,actual,name,descriptor));
    }
    private static FieldRead knownField(Object receiver,String owner,String name){
        Class<?> declaring=declaring(receiver.getClass(),owner);
        for(var slot:EquipmentCarrierGraph.fields(receiver))if(slot.field().getDeclaringClass()==declaring&&slot.field().getName().equals(name))return new FieldRead(receiver,slot.field(),read(receiver,slot.field()));
        throw unsupported("KEY_QUERY_FIELD");
    }
    static Read containerRead(QueryKeys keys,Object result,List<KeySemantics> semantics){
        if(keys==null||semantics==null||semantics.size()>LIMIT)throw unsupported("KEY_QUERY_RESULTS");keys.current(new Uses(0));
        KeySnapshot snapshot=new KeySnapshot(keys.connections,List.copyOf(keys.methods),List.copyOf(semantics));
        return new ContainerRead(keys.receiver,keys.key,result,keys.query,snapshot);
    }
    private static int keyKind(String name,String descriptor){
        if("hashCode".equals(name)&&"()I".equals(descriptor))return 0;
        if("equals".equals(name)&&"(Ljava/lang/Object;)Z".equals(descriptor))return 1;
        return "compareTo".equals(name)&&"(Ljava/lang/Object;)I".equals(descriptor)?2:-1;
    }
    private static boolean builtinKey(Object key){return key==null||key instanceof Enum<?>||key.getClass()==Integer.class||key.getClass()==String.class;}
    private static Class<?> enumType(Enum<?> value){Class<?> type=value.getClass();return type.getSuperclass()==Enum.class?type:type.getSuperclass();}
    private static boolean selfComparable(Object key){
        if(!(key instanceof Comparable<?>))return false;Class<?> type=key.getClass();
        for(java.lang.reflect.Type contract:type.getGenericInterfaces())if(contract instanceof ParameterizedType parameterized&&parameterized.getRawType()==Comparable.class){
            java.lang.reflect.Type[] arguments=parameterized.getActualTypeArguments();if(arguments.length==1&&arguments[0]==type)return true;
        }return false;
    }
    static boolean keyReadSupported(Object receiver,Class<?> symbolic,String name,String descriptor,int opcode,Class<?> caller,Object[] arguments){
        try{return builtinCall(receiver,symbolic,name,descriptor,opcode,caller,arguments)!=null;}catch(Unresolved unsupported){return false;}
    }
    static Read keyRead(Object receiver,Class<?> symbolic,String name,String descriptor,int opcode,Class<?> caller,Object[] arguments,Object result){
        BuiltinCall call=builtinCall(receiver,symbolic,name,descriptor,opcode,caller,arguments);if(call==null)throw unsupported("BUILTIN_KEY_CONTRACT");
        Object holder=receiver==null?symbolic:receiver;Object current=call.current(new Uses(0),holder);if(!samePrimitive(current,result))throw unsupported("BUILTIN_KEY_RETURN");return new BuiltinRead(holder,result,call);
    }
    private static BuiltinCall builtinCall(Object receiver,Class<?> symbolic,String name,String descriptor,int opcode,Class<?> caller,Object[] arguments){
        if(opcode==184&&receiver==null&&name!=null&&name.equals("compare")&&arguments!=null&&arguments.length==2&&caller!=null){
            Class<?> boxed=symbolic==Integer.class&&"(II)I".equals(descriptor)?Integer.class:symbolic==Long.class&&"(JJ)I".equals(descriptor)?Long.class:null;
            if(boxed==null||arguments[0]==null||arguments[1]==null||arguments[0].getClass()!=boxed||arguments[1].getClass()!=boxed
                    ||dispatch(null,symbolic,name,descriptor,opcode,caller)!=boxed)return null;
            return new BuiltinCall(name,descriptor,List.of(new Value(arguments[0],true),new Value(arguments[1],true)),List.of(methodRead(boxed,name,descriptor)));
        }
        if(receiver==null||!builtinKey(receiver)||arguments==null||symbolic==null||caller==null||opcode!=182&&opcode!=183&&opcode!=185)return null;
        Class<?> type=receiver instanceof Enum<?>?Enum.class:receiver.getClass();int kind=keyKind(name,descriptor);
        String typed="(L"+type.getName().replace('.','/')+";)I";
        if(kind<0&&"compareTo".equals(name)&&typed.equals(descriptor))kind=2;
        if(kind<0||arguments.length!=(kind==0?0:1)||dispatch(receiver,symbolic,name,descriptor,opcode,caller)!=type)return null;
        if(kind==2){Object argument=arguments[0];if(argument==null)return null;
            if(type==Enum.class){if(!(argument instanceof Enum<?> other)||enumType((Enum<?>)receiver)!=enumType(other))return null;}
            else if(argument.getClass()!=type)return null;
        }
        List<MethodRead> methods=new ArrayList<>();methods.add(methodRead(type,name,descriptor));
        if(kind==2&&!typed.equals(descriptor))methods.add(methodRead(type,name,typed));
        List<Value> values=arguments.length==0?List.of():List.of(new Value(arguments[0],false));return new BuiltinCall(name,descriptor,values,List.copyOf(methods));
    }
    /** Only the exact natural key callback inside this prepared platform get.
     * The Policy also checks the original get's physical bottom/site and the
     * first installed KEY token; no frame-name verdict is an authorization. */
    static boolean keyCall(QueryKeys keys,Object receiver,Class<?> declaring,String name,String descriptor,Object[] arguments,List<StackWalker.StackFrame> frames){
        try {
            int kind=keyKind(name,descriptor);if(keys==null||kind<0||receiver!=keys.semanticReceiver||arguments==null||arguments.length!=(kind==0?0:1)||frames==null||frames.size()<2)return false;
            boolean target=false;for(KeyTarget actual:keys.targets)if(actual.receiver==receiver&&actual.declaring==declaring&&actual.name.equals(name)&&actual.descriptor.equals(descriptor)){target=true;break;}
            if(!target||!frame(frames,0,declaring.getName(),name,descriptor)||frames.get(0).getDeclaringClass()!=declaring)return false;
            keys.current(new Uses(0));KeyStep leaf=keys.steps.get(keys.steps.size()-1);String layout=leaf.layout.getName();int at=1;
            if(leaf.layout==HashMap.class||leaf.layout==LinkedHashMap.class){
                if(kind==0){if(!frame(frames,at++,"java.util.HashMap","hash","(Ljava/lang/Object;)I"))return false;}
                else if(kind==2){if(!selfComparable(receiver)||arguments[0]==null||arguments[0].getClass()!=receiver.getClass()
                        ||!frame(frames,at++,"java.util.HashMap","compareComparables","(Ljava/lang/Class;Ljava/lang/Object;Ljava/lang/Object;)I"))return false;}
                if(kind==2||frame(frames,at,"java.util.HashMap$TreeNode","find","(ILjava/lang/Object;Ljava/lang/Class;)Ljava/util/HashMap$TreeNode;")){
                    int first=at;while(frame(frames,at,"java.util.HashMap$TreeNode","find","(ILjava/lang/Object;Ljava/lang/Class;)Ljava/util/HashMap$TreeNode;"))at++;
                    if(at==first||!frame(frames,at++,"java.util.HashMap$TreeNode","getTreeNode","(ILjava/lang/Object;)Ljava/util/HashMap$TreeNode;"))return false;
                }
                if(!frame(frames,at++,"java.util.HashMap","getNode","(Ljava/lang/Object;)Ljava/util/HashMap$Node;"))return false;
            }else if(layout.equals(I+"MapN")){
                if(kind==2||!frame(frames,at++,layout,"probe","(Ljava/lang/Object;)I"))return false;
            }else if(layout.equals(C+"SingletonMap")){
                if(kind!=1||!frame(frames,at++,"java.util.Collections","eq","(Ljava/lang/Object;Ljava/lang/Object;)Z"))return false;
                if(arguments[0]!=knownField(leaf.receiver,layout,"k").value)return false;
            }else if(layout.equals(I+"Map1")){
                if(kind!=1||arguments[0]!=knownField(leaf.receiver,layout,"k0").value)return false;
            }else if(layout.equals(G+"SingletonImmutableBiMap")){
                if(kind!=1||arguments[0]!=keys.key)return false;
            }else if(layout.equals(G+"RegularImmutableMap")){
                if(kind==2||!frame(frames,at++,layout,"get","(Ljava/lang/Object;[Lcom/google/common/collect/ImmutableMapEntry;I)Ljava/lang/Object;"))return false;
            }else return false;
            for(int index=keys.steps.size()-1;index>=0;index--){KeyStep step=keys.steps.get(index);
                if(!frame(frames,at++,step.query.body.getName(),step.query.name,step.query.descriptor)||frames.get(at-1).getDeclaringClass()!=step.query.body)return false;
            }
            int bottom=(int)DEPTH.invokeExact(declaring,name,descriptor);if(bottom<=at)return false;
            for(int index=0;index<at;index++){
                var selected=frames.get(index);long[] physical=(long[])FRAME.invokeExact(selected.getDeclaringClass(),selected.getMethodName(),selected.getDescriptor(),bottom-index);
                if(physical==null||physical.length!=6)return false;
                MethodRead observed=methodRead(selected.getDeclaringClass(),selected.getMethodName(),selected.getDescriptor(),Arrays.copyOfRange(physical,2,6));
                boolean present=false;for(MethodRead method:keys.methods)if(method.definition.equals(observed.definition)){present=true;break;}
                if(!present){if(keys.methods.size()>=LIMIT)return false;keys.methods.add(observed);}
            }
            return true;
        }catch(Unresolved unavailable){return false;}catch(Error failure){throw failure;}catch(Throwable unavailable){return false;}
    }
    private static boolean frame(List<StackWalker.StackFrame> frames,int at,String owner,String name,String descriptor){
        if(at<0||at>=frames.size())return false;var frame=frames.get(at);Class<?> type=frame.getDeclaringClass();
        boolean platform=owner.startsWith("java.")?jdk(type,owner):owner.startsWith(G)?guava(type,owner):type.getName().equals(owner);
        return platform&&frame.getMethodName().equals(name)&&frame.getDescriptor().equals(descriptor);
    }
    private static Query query(Object receiver,boolean map,Class<?> symbolic,String name,String descriptor,int opcode,Class<?> caller){
        if(receiver==null||symbolic==null||caller==null||!(map?receiver instanceof Map<?,?>:receiver instanceof List<?>)||!"get".equals(name)
                ||!(map?"(Ljava/lang/Object;)Ljava/lang/Object;":"(I)Ljava/lang/Object;").equals(descriptor)||opcode!=182&&opcode!=183&&opcode!=185)return null;
        Class<?> actual=dispatch(receiver,symbolic,name,descriptor,opcode,caller);
        MethodImage image=actual==null?null:methodImage(actual,name,descriptor);
        Query query=image==null?null:new Query(map,symbolic,name,descriptor,opcode,caller,actual,image);
        return query!=null&&queryBase(receiver,query)!=null?query:null;
    }
    private static MethodImage methodImage(Class<?> declaring,String name,String descriptor){
        try{return MethodImage.of((long[])IMAGE.invokeExact(declaring,name,descriptor));}
        catch(RuntimeException failure){throw unsupported("METHOD_IMAGE");}catch(Error failure){throw failure;}catch(Throwable failure){throw unsupported("METHOD_IMAGE");}
    }
    private static Class<?> dispatch(Object receiver,Class<?> symbolic,String name,String descriptor,int opcode,Class<?> caller){
        try{return (Class<?>)METHOD.invokeExact(receiver,symbolic,name,descriptor,opcode,caller);}
        catch(RuntimeException failure){throw unsupported("QUERY_DISPATCH");}catch(Error failure){throw failure;}catch(Throwable failure){throw unsupported("QUERY_DISPATCH");}
    }
    /** The original super/virtual contract is retained; reading its storage never runs an override. */
    private static Class<?> queryBase(Object receiver,Query query){
        if(receiver==null||!(query.map?receiver instanceof Map<?,?>:receiver instanceof List<?>)||!EquipmentCarrierLayouts.known(query.body)
                ||!query.image.equals(methodImage(query.body,query.name,query.descriptor))
                ||dispatch(receiver,query.symbolic,query.name,query.descriptor,query.opcode,query.caller)!=query.body)return null;
        for(Class<?> at=receiver.getClass();at!=null;at=at.getSuperclass())if(EquipmentCarrierLayouts.known(at))
            return (query.map?Map.class.isAssignableFrom(at):List.class.isAssignableFrom(at))&&query.body.isAssignableFrom(at)?at:null;
        return null;
    }
    /** Resolve the public virtual query, without running a container or key method. */
    private static Class<?> queryBase(Object receiver,boolean map){
        if(receiver==null||!(map?receiver instanceof Map<?,?>:receiver instanceof List<?>))return null;
        Class<?> base=null;for(Class<?> at=receiver.getClass();at!=null;at=at.getSuperclass())if(EquipmentCarrierLayouts.known(at)){base=at;break;}
        if(base==null||!(map?Map.class.isAssignableFrom(base):List.class.isAssignableFrom(base)))return null;
        Class<?> contract=map?Map.class:List.class;String descriptor=map?"(Ljava/lang/Object;)Ljava/lang/Object;":"(I)Ljava/lang/Object;";Class<?> actual;
        actual=dispatch(receiver,contract,"get",descriptor,185,EquipmentSlotGraph.class);
        // A custom override must take the original helper trace. Only an actual
        // supported platform body may use the corresponding standard storage.
        return actual!=null&&EquipmentCarrierLayouts.known(actual)&&actual.isAssignableFrom(base)?base:null;
    }
    private static final class Uses {
        final int mask;final List<FieldUse> fields=new ArrayList<>();final List<ArrayUse> arrays=new ArrayList<>();int count;
        List<KeySemantics> keys=List.of();
        final Replacement replacement;Uses(int mask){this(mask,null);}Uses(int mask,Replacement replacement){this.mask=mask;this.replacement=replacement;}
        Object field(Object receiver,Field actual,boolean terminal){
            if(++count>LIMIT)throw unsupported("READ_CAPACITY");Object value=replacement!=null&&replacement.field(receiver,actual)?replacement.value:read(receiver,actual);long offset=-1;int width;
            if(Modifier.isStatic(actual.getModifiers())){try{offset=(Long)STATIC_OFFSET.invoke(UNSAFE,actual);}catch(ReflectiveOperationException failure){throw unsupported("STATIC_LAYOUT");}width=width(actual.getType());}
            else {var meta=metadata(receiver,actual);offset=meta.offset();width=meta.width();}
            fields.add(new FieldUse(receiver,actual,offset,width,mask,terminal));
            if(Modifier.isStatic(actual.getModifiers()))try{Object base=STATIC_BASE.invoke(UNSAFE,actual);if(base!=receiver)fields.add(new FieldUse(base,actual,offset,width,mask,terminal));}catch(ReflectiveOperationException failure){throw unsupported("STATIC_BASE");}
            return value;
        }
        Object get(Object receiver,String owner,String... names){Class<?> declaring=declaring(receiver.getClass(),owner);for(var slot:EquipmentCarrierGraph.fields(receiver))if(slot.field().getDeclaringClass()==declaring)for(String name:names)if(slot.field().getName().equals(name))return field(receiver,slot.field(),false);throw unsupported("FIELD:"+owner+":"+names[0]);}
        Object constant(Class<?> declaring,String name){try{Field actual=declaring.getDeclaredField(name);if(!Modifier.isStatic(actual.getModifiers()))throw unsupported("STATIC_FIELD");return field(declaring,actual,false);}catch(NoSuchFieldException failure){throw unsupported("STATIC_FIELD:"+declaring.getName()+":"+name);}}
        Object terminal(Object receiver,String owner,String name,boolean terminal){Class<?> declaring=declaring(receiver.getClass(),owner);for(var slot:EquipmentCarrierGraph.fields(receiver))if(slot.field().getDeclaringClass()==declaring&&slot.field().getName().equals(name))return field(receiver,slot.field(),terminal);throw unsupported("TERMINAL_FIELD");}
        Object array(Object array,int index,boolean terminal){if(array==null||!array.getClass().isArray()||index<0||index>=Array.getLength(array))throw unsupported("ARRAY_INDEX");if(++count>LIMIT)throw unsupported("READ_CAPACITY");Object value=replacement!=null&&replacement.declaring==null&&replacement.receiver==array&&replacement.index==index?replacement.value:Array.get(array,index);arrays.add(new ArrayUse(array,index,index+1,1,mask,terminal));return value;}
        Object key(Object receiver,String name,String descriptor,Object... arguments){
            Object result=null;boolean found=false;
            for(KeySemantics semantic:keys)if(semantic.matches(receiver,name,descriptor,arguments)){
                Object current=semantic.current(this);if(found&&!samePrimitive(result,current))throw unsupported("AMBIGUOUS_KEY_SEMANTICS");result=current;found=true;
            }
            if(!found)throw unsupported("MISSING_NATURAL_KEY_SEMANTICS");return result;
        }
    }
    private static Object list(Uses uses,Object receiver,int index,boolean terminal,IdentityHashMap<Object,Boolean> path){return list(uses,receiver,index,terminal,path,null);}
    private static Object list(Uses uses,Object receiver,int index,boolean terminal,IdentityHashMap<Object,Boolean> path,Query query){
        Class<?> type=query==null?queryBase(receiver,false):queryBase(receiver,query);if(type==null||path.put(receiver,Boolean.TRUE)!=null||path.size()>LIMIT)throw unsupported("LIST_IMPLEMENTATION");
        String name=type.getName();try{
            if(index<0)throw unsupported("LIST_INDEX");
            if(name.equals("java.util.ArrayList")){int size=integer(uses.get(receiver,name,"size"));if(index>=size)throw unsupported("LIST_INDEX");return uses.array(uses.get(receiver,name,"elementData"),index,terminal);}
            if(name.equals("java.util.Arrays$ArrayList"))return uses.array(uses.get(receiver,name,"a"),index,terminal);
            if(name.equals(I+"ListN")||name.equals(G+"RegularImmutableList"))return uses.array(uses.get(receiver,name,name.equals(I+"ListN")?"elements":"array"),index,terminal);
            if(name.equals(I+"List12")){Object second=uses.get(receiver,name,"e1");if(index==0)return uses.terminal(receiver,name,"e0",terminal);if(index==1&&second!=staticValue(declaring(receiver.getClass(),I+"List12").getEnclosingClass(),"EMPTY","Ljava/lang/Object;"))return uses.terminal(receiver,name,"e1",terminal);throw unsupported("LIST_INDEX");}
            if(name.equals(C+"SingletonList")||name.equals(G+"SingletonImmutableList")){if(index!=0)throw unsupported("LIST_INDEX");return uses.terminal(receiver,name,"element",terminal);}
            if(name.equals(C+"CopiesList")){if(index>=integer(uses.get(receiver,name,"n")))throw unsupported("LIST_INDEX");return uses.terminal(receiver,name,"element",terminal);}
            if(name.equals("java.util.LinkedList")){int size=integer(uses.get(receiver,name,"size"));if(index>=size)throw unsupported("LIST_INDEX");Object node=uses.get(receiver,name,"first");IdentityHashMap<Object,Boolean> seen=new IdentityHashMap<>();
                for(int at=0;at<index;at++){if(node==null||!jdk(node.getClass(),"java.util.LinkedList$Node")||seen.put(node,Boolean.TRUE)!=null)throw unsupported("LIST_NODE");node=uses.get(node,"java.util.LinkedList$Node","next");}
                if(node==null||!jdk(node.getClass(),"java.util.LinkedList$Node"))throw unsupported("LIST_NODE");return uses.terminal(node,"java.util.LinkedList$Node","item",terminal);}
            if(name.equals("java.util.ArrayList$SubList")||name.equals(I+"SubList")||name.equals(G+"ImmutableList$SubList")){
                int size=integer(uses.get(receiver,name,name.equals(G+"ImmutableList$SubList")?"length":"size")),offset=integer(uses.get(receiver,name,"offset"));if(index>=size||offset<0||offset>Integer.MAX_VALUE-index)throw unsupported("SUBLIST_INDEX");
                Object parent=uses.get(receiver,name,name.equals(G+"ImmutableList$SubList")?"this$0":"root");
                if(name.equals("java.util.ArrayList$SubList")&&integer(uses.get(receiver,"java.util.AbstractList","modCount"))!=integer(uses.get(parent,"java.util.AbstractList","modCount")))throw unsupported("SUBLIST_MODIFICATION");
                return list(uses,parent,offset+index,terminal,path);}
            if(name.equals(G+"ImmutableList$ReverseImmutableList")){Object forward=uses.get(receiver,name,"forwardList");EquipmentCarrierLayouts.Layout layout=EquipmentCarrierLayouts.layout(forward);if(layout==null||!layout.complete()||layout.elements()==null||index>=layout.elements().length)throw unsupported("REVERSE_LIST");return list(uses,forward,layout.elements().length-1-index,terminal,path);}
            if(name.equals(G+"RegularImmutableAsList"))return list(uses,uses.get(receiver,name,"delegateList"),index,terminal,path);
            if(name.startsWith(C+"Unmodifiable")||name.startsWith(C+"Synchronized")){String prefix=C+(name.startsWith(C+"Unmodifiable")?"Unmodifiable":"Synchronized");Object delegate=uses.get(receiver,prefix+"List","list");if(delegate!=uses.get(receiver,prefix+"Collection","c"))throw unsupported("LIST_ALIAS");return list(uses,delegate,index,terminal,path);}
            if(type==net.minecraft.core.NonNullList.class)return list(uses,uses.get(receiver,name,"list","f_122773_"),index,terminal,path);
            throw unsupported("LIST_LAYOUT");
        }finally{path.remove(receiver);}
    }
    private static Object map(Uses uses,Object receiver,Object key,boolean terminal,IdentityHashMap<Object,Boolean> path){return map(uses,receiver,key,terminal,path,null);}
    private static Object map(Uses uses,Object receiver,Object key,boolean terminal,IdentityHashMap<Object,Boolean> path,Query query){
        Class<?> type=query==null?queryBase(receiver,true):queryBase(receiver,query);if(type==null)throw unsupported("MAP_IMPLEMENTATION");
        if(path.put(receiver,Boolean.TRUE)!=null||path.size()>LIMIT)throw unsupported("MAP_CYCLE");String name=type.getName();try{
            if(type==EnumMap.class){Object keyType=uses.get(receiver,name,"keyType");if(!(key instanceof Enum<?> actual)||key.getClass()!=keyType&&key.getClass().getSuperclass()!=keyType)return null;
                Object value=uses.array(uses.get(receiver,name,"vals"),enumOrdinal(uses,actual),terminal);return value==uses.constant(EnumMap.class,"NULL")?null:value;}
            if(name.startsWith(C+"Unmodifiable")||name.startsWith(C+"Synchronized")){String owner=C+(name.startsWith(C+"Unmodifiable")?"Unmodifiable":"Synchronized")+"Map";return map(uses,uses.get(receiver,owner,"m"),key,terminal,path);}
            if(type==HashMap.class||type==LinkedHashMap.class)return hashMap(uses,receiver,key,terminal);
            if(type==IdentityHashMap.class){Object[] table=references(uses.get(receiver,name,"table"));Object actualKey=key==null?uses.constant(type,"NULL_KEY"):key;int length=table.length;if(length<2||(length&1)!=0)throw unsupported("IDENTITY_TABLE");int h=System.identityHashCode(actualKey),at=((h<<1)-(h<<8))&(length-1),start=at;
                do{Object found=uses.array(table,at,false);if(found==actualKey)return uses.array(table,at+1,terminal);if(found==null)break;at=at+2<length?at+2:0;}while(at!=start);return null;}
            if(name.equals(I+"MapN"))return immutableMap(uses,receiver,key,terminal);
            if(name.equals(I+"Map1")){if(key==null)throw unsupported("NULL_IMMUTABLE_MAP_KEY");return keyEqual(uses,key,uses.get(receiver,name,"k0"))?uses.terminal(receiver,name,"v0",terminal):null;}
            if(name.equals(C+"SingletonMap"))return keyEqual(uses,key,uses.get(receiver,name,"k"))?uses.terminal(receiver,name,"v",terminal):null;
            if(name.equals(G+"SingletonImmutableBiMap"))return keyEqual(uses,uses.get(receiver,name,"singleKey"),key)?uses.terminal(receiver,name,"singleValue",terminal):null;
            if(name.equals(G+"RegularImmutableMap"))return guavaMap(uses,receiver,key,terminal);
            if(name.equals(G+"JdkBackedImmutableMap"))return map(uses,uses.get(receiver,name,"delegateMap"),key,terminal,path);
            if(name.equals(C+"EmptyMap"))return null;
            throw unsupported("MAP_LAYOUT");
        }finally{path.remove(receiver);}
    }
    /** JDK 17 getNode: the initial next test precedes getTreeNode even for a tree head. */
    private static Object hashMap(Uses uses,Object receiver,Object key,boolean terminal){
        Object table=uses.get(receiver,"java.util.HashMap","table");if(table==null)return null;Object[] values=references(table);if(values.length==0)return null;
        int hash=keyHash(uses,key);hash^=hash>>>16;Object first=uses.array(table,(values.length-1)&hash,false);if(first==null)return null;node(first);
        if(integer(uses.get(first,"java.util.HashMap$Node","hash"))==hash&&hashKeyEqual(uses,key,uses.get(first,"java.util.HashMap$Node","key")))return uses.terminal(first,"java.util.HashMap$Node","value",terminal);
        Object next=uses.get(first,"java.util.HashMap$Node","next");if(next==null)return null;
        if(tree(first)){
            Object root=first;IdentityHashMap<Object,Boolean> parents=new IdentityHashMap<>();
            while(true){if(!tree(root)||parents.put(root,Boolean.TRUE)!=null||parents.size()>LIMIT)throw unsupported("TREE_PARENT");Object parent=uses.get(root,"java.util.HashMap$TreeNode","parent");if(parent==null)break;root=parent;}
            Object found=treeFind(uses,root,hash,key,new IdentityHashMap<>());return found==null?null:uses.terminal(found,"java.util.HashMap$Node","value",terminal);
        }
        IdentityHashMap<Object,Boolean> seen=new IdentityHashMap<>();seen.put(first,Boolean.TRUE);
        for(Object at=next;at!=null;at=uses.get(at,"java.util.HashMap$Node","next")){
            node(at);if(seen.put(at,Boolean.TRUE)!=null||seen.size()>LIMIT)throw unsupported("MAP_NODE_CYCLE");
            if(integer(uses.get(at,"java.util.HashMap$Node","hash"))==hash&&hashKeyEqual(uses,key,uses.get(at,"java.util.HashMap$Node","key")))return uses.terminal(at,"java.util.HashMap$Node","value",terminal);
        }return null;
    }
    /** Mirror find's right-first fallback, recording the actual parent/child routes rather than next. */
    private static Object treeFind(Uses uses,Object current,int hash,Object key,IdentityHashMap<Object,Boolean> seen){
        for(Object at=current;at!=null;){
            if(!tree(at)||seen.put(at,Boolean.TRUE)!=null||seen.size()>LIMIT)throw unsupported("TREE_NODE_CYCLE");
            Object left=uses.get(at,"java.util.HashMap$TreeNode","left"),right=uses.get(at,"java.util.HashMap$TreeNode","right");int actualHash=integer(uses.get(at,"java.util.HashMap$Node","hash"));
            if(actualHash>hash){at=left;continue;}if(actualHash<hash){at=right;continue;}
            Object actualKey=uses.get(at,"java.util.HashMap$Node","key");if(hashKeyEqual(uses,key,actualKey))return at;
            if(left==null){at=right;continue;}if(right==null){at=left;continue;}
            int compared=keyComparable(uses,key,actualKey);if(compared!=0){at=compared<0?left:right;continue;}
            Object found=treeFind(uses,right,hash,key,seen);if(found!=null)return found;at=left;
        }return null;
    }
    private static Object immutableMap(Uses uses,Object receiver,Object key,boolean terminal){
        if(key==null)throw unsupported("NULL_IMMUTABLE_MAP_KEY");int size=integer(uses.get(receiver,I+"MapN","size"));if(size==0)return null;if(size<0)throw unsupported("IMMUTABLE_MAP_SIZE");
        Object[] table=references(uses.get(receiver,I+"MapN","table"));if(table.length==0||(table.length&1)!=0)throw unsupported("IMMUTABLE_MAP_TABLE");
        int at=Math.floorMod(keyHash(uses,key),table.length>>>1)<<1,start=at;
        do{Object actualKey=uses.array(table,at,false);if(actualKey==null)return null;if(keyEqual(uses,key,actualKey))return uses.array(table,at+1,terminal);at=at+2==table.length?0:at+2;}while(at!=start);
        throw unsupported("IMMUTABLE_MAP_PROBE_CYCLE");
    }
    private static Object guavaMap(Uses uses,Object receiver,Object key,boolean terminal){
        Object table=uses.get(receiver,G+"RegularImmutableMap","table");int mask=integer(uses.get(receiver,G+"RegularImmutableMap","mask"));if(key==null||table==null)return null;
        Object[] actual=references(table);int hash=461845907*Integer.rotateLeft(-862048943*keyHash(uses,key),15),index=hash&mask;
        if(index<0||index>=actual.length)throw unsupported("GUAVA_BUCKET");Object entry=uses.array(table,index,false);IdentityHashMap<Object,Boolean> seen=new IdentityHashMap<>();
        while(entry!=null){
            if(!guavaEntry(entry)||seen.put(entry,Boolean.TRUE)!=null||seen.size()>LIMIT)throw unsupported("GUAVA_ENTRY");
            Object actualKey=uses.get(entry,G+"ImmutableEntry","key");if(keyEqual(uses,key,actualKey))return uses.terminal(entry,G+"ImmutableEntry","value",terminal);
            entry=guava(entry.getClass(),G+"ImmutableMapEntry$NonTerminalImmutableMapEntry")?uses.get(entry,G+"ImmutableMapEntry$NonTerminalImmutableMapEntry","nextInKeyBucket"):null;
        }return null;
    }
    private static boolean guavaEntry(Object value){return guava(value.getClass(),G+"ImmutableMapEntry")||guava(value.getClass(),G+"ImmutableMapEntry$NonTerminalImmutableMapEntry");}
    private static boolean tree(Object value){return value!=null&&jdk(value.getClass(),"java.util.HashMap$TreeNode");}
    private static void node(Object value){if(value==null||!jdkNode(value.getClass())&&!tree(value))throw unsupported("MAP_NODE");}
    private static int keyHash(Uses uses,Object key){
        if(key==null)return 0;if(key instanceof Enum<?>)return System.identityHashCode(key);
        if(key.getClass()==Integer.class)return integer(uses.get(key,"java.lang.Integer","value"));
        if(key.getClass()==String.class){int hash=integer(uses.get(key,"java.lang.String","hash"));if(hash!=0)return hash;
            Object zero=uses.get(key,"java.lang.String","hashIsZero");if(!(zero instanceof Boolean))throw unsupported("STRING_ZERO_HASH");if(Boolean.TRUE.equals(zero))return 0;
            StringData data=stringData(uses,key);for(int at=0;at<data.length();at++)hash=31*hash+data.character(uses,at);return hash;}
        if(identityMethod(key,"hashCode"))return System.identityHashCode(key);
        return integer(uses.key(key,"hashCode","()I"));
    }
    private static boolean hashKeyEqual(Uses uses,Object key,Object actual){return key==actual||key!=null&&keyEqual(uses,key,actual);}
    /** The first argument is the real equals receiver; Guava's singleton uses the stored key. */
    private static boolean keyEqual(Uses uses,Object first,Object second){
        if(first==null)return second==null;if(first instanceof Enum<?>)return first==second;
        if(first.getClass()==Integer.class)return second!=null&&second.getClass()==Integer.class&&integer(uses.get(first,"java.lang.Integer","value"))==integer(uses.get(second,"java.lang.Integer","value"));
        if(first.getClass()==String.class){if(first==second)return true;if(second==null||second.getClass()!=String.class)return false;
            boolean compact=compactStrings(uses);if(compact&&coder(uses,first)!=coder(uses,second))return false;byte[] a=bytes(uses.get(first,"java.lang.String","value")),b=bytes(uses.get(second,"java.lang.String","value"));if(a.length!=b.length)return false;
            for(int at=0;at<a.length;at++)if(byteAt(uses,a,at)!=byteAt(uses,b,at))return false;return true;}
        if(identityMethod(first,"equals"))return first==second;
        Object result=uses.key(first,"equals","(Ljava/lang/Object;)Z",second);if(result instanceof Boolean actual)return actual;throw unsupported("KEY_EQUALS_RETURN");
    }
    private static int keyComparable(Uses uses,Object key,Object actual){
        if(key==null)return 0;
        if(key.getClass()==Integer.class)return actual!=null&&actual.getClass()==Integer.class?Integer.compare(integer(uses.get(key,"java.lang.Integer","value")),integer(uses.get(actual,"java.lang.Integer","value"))):0;
        if(key.getClass()==String.class){if(actual==null||actual.getClass()!=String.class)return 0;StringData a=stringData(uses,key),b=stringData(uses,actual);int count=Math.min(a.length(),b.length());
            for(int at=0;at<count;at++){int difference=a.character(uses,at)-b.character(uses,at);if(difference!=0)return difference;}return a.length()-b.length();}
        if(key instanceof Enum<?>){
            // comparableClassFor inspects only the actual class's direct generic
            // interfaces. An ordinary enum inherits Comparable from Enum.
            for(Class<?> contract:key.getClass().getInterfaces())if(contract==Comparable.class)throw unsupported("ENUM_DIRECT_COMPARABLE");return 0;
        }
        if(!selfComparable(key)||actual==null||actual.getClass()!=key.getClass())return 0;
        return integer(uses.key(key,"compareTo","(Ljava/lang/Object;)I",actual));
    }
    private static boolean identityMethod(Object receiver,String name){
        try {Method method=receiver.getClass().getMethod(name,name.equals("equals")?new Class<?>[]{Object.class}:new Class<?>[0]);return method.getDeclaringClass()==Object.class&&!Modifier.isStatic(method.getModifiers());}
        catch(ReflectiveOperationException unavailable){throw unsupported("KEY_IDENTITY_DISPATCH");}
    }
    private record StringData(byte[] bytes,int coder,int high,int low){
        int length(){return bytes.length>>coder;}
        int character(Uses uses,int index){return coder==0?byteAt(uses,bytes,index):(byteAt(uses,bytes,index<<1)<<high)|(byteAt(uses,bytes,(index<<1)+1)<<low);}
    }
    private static StringData stringData(Uses uses,Object value){
        byte[] actual=bytes(uses.get(value,"java.lang.String","value"));int coder=compactStrings(uses)?coder(uses,value):1;int high=0,low=0;
        if(coder==1){if((actual.length&1)!=0)throw unsupported("UTF16_BYTE_LENGTH");Class<?> utf16;try{utf16=Class.forName("java.lang.StringUTF16",false,null);}catch(ClassNotFoundException missing){throw unsupported("UTF16_CLASS");}
            high=integer(uses.constant(utf16,"HI_BYTE_SHIFT"));low=integer(uses.constant(utf16,"LO_BYTE_SHIFT"));if(high!=0&&high!=8||low!=0&&low!=8||high==low)throw unsupported("UTF16_LAYOUT");}
        return new StringData(actual,coder,high,low);
    }
    private static boolean compactStrings(Uses uses){Object value=uses.constant(String.class,"COMPACT_STRINGS");if(value instanceof Boolean result)return result;throw unsupported("COMPACT_STRINGS");}
    private static int coder(Uses uses,Object value){Object coder=uses.get(value,"java.lang.String","coder");if(coder instanceof Byte actual&&(actual==0||actual==1))return actual.intValue();throw unsupported("STRING_CODER");}
    private static int byteAt(Uses uses,byte[] value,int at){Object actual=uses.array(value,at,false);if(actual instanceof Byte result)return result.intValue()&255;throw unsupported("STRING_BYTE");}
    private static byte[] bytes(Object value){if(value instanceof byte[] actual)return actual;throw unsupported("STRING_VALUE");}
    private static int enumOrdinal(Uses uses,Enum<?> value){return integer(uses.get(value,"java.lang.Enum","ordinal"));}
    private static boolean scalar(Object value){
        if(value==null)return true;Class<?> type=value.getClass();
        return type==String.class||type==Byte.class||type==Short.class||type==Integer.class||type==Long.class||type==Float.class||type==Double.class
                ||type==Boolean.class||type==Character.class||type==java.math.BigInteger.class||type==java.math.BigDecimal.class||value instanceof Enum<?>||value instanceof Class<?>;
    }
    private static boolean borrowed(Object value){return value instanceof Entity||value instanceof net.minecraft.world.level.Level||value instanceof net.minecraft.server.MinecraftServer||value instanceof net.minecraft.world.level.block.entity.BlockEntity||value instanceof net.minecraft.world.level.chunk.ChunkAccess
            ||value instanceof ClassLoader||value instanceof Thread||value instanceof ThreadGroup||value instanceof Module||value instanceof java.lang.ref.Reference<?>||value instanceof java.lang.ref.ReferenceQueue<?>||value instanceof ThreadLocal<?>||value instanceof java.util.concurrent.locks.Lock||value instanceof java.nio.Buffer||value instanceof java.nio.channels.Channel||value instanceof java.net.Socket
            ||value instanceof net.minecraft.world.item.Item||value instanceof net.minecraft.core.Registry<?>||value instanceof net.minecraft.core.Holder<?>;}
    private static boolean jdk(Class<?> type,String name){return type.getName().equals(name)&&type.getClassLoader()==null&&type.getModule()==Object.class.getModule();}
    private static boolean guava(Class<?> type,String name){return type.getName().equals(name)&&type.getClassLoader()==com.google.common.collect.ImmutableList.class.getClassLoader()&&type.getModule()==com.google.common.collect.ImmutableList.class.getModule();}
    private static boolean jdkNode(Class<?> type){return jdk(type,"java.util.HashMap$Node")||jdk(type,"java.util.LinkedHashMap$Entry");}
    private static Class<?> declaring(Class<?> type,String name){for(Class<?> at=type;at!=null;at=at.getSuperclass())if(at.getName().equals(name))return at;throw unsupported("DECLARING_CLASS:"+name);}
    private static EquipmentCarrierGraph.Slot metadata(Object receiver,Field field){for(var slot:EquipmentCarrierGraph.fields(receiver))if(slot.field().equals(field))return slot;throw unsupported("FIELD_METADATA");}
    static Object readField(Object receiver,Field field){return read(receiver,field);}
    private static Object read(Object receiver,Field field){try{if(Modifier.isStatic(field.getModifiers()))return (Object)STATIC.invokeExact(field.getDeclaringClass(),field.getName(),field.getType().descriptorString());return (Object)READ.invokeExact(receiver,field);}catch(RuntimeException|Error failure){throw failure;}catch(Throwable failure){throw new IllegalStateException("EQUIPMENT_SLOT_FIELD_READ",failure);}}
    private static Object staticValue(Class<?> type,String name,String descriptor){try{return (Object)STATIC.invokeExact(type,name,descriptor);}catch(RuntimeException|Error failure){throw failure;}catch(Throwable failure){throw new IllegalStateException("EQUIPMENT_SLOT_STATIC_READ",failure);}}
    private static int integer(Object value){if(value instanceof Integer result)return result;throw unsupported("INTEGER");}
    private static Object[] references(Object value){if(value instanceof Object[] result)return result;throw unsupported("REFERENCE_ARRAY");}
    private static boolean samePrimitive(Object first,Object second){if(first instanceof Float a&&second instanceof Float b)return Float.floatToRawIntBits(a)==Float.floatToRawIntBits(b);if(first instanceof Double a&&second instanceof Double b)return Double.doubleToRawLongBits(a)==Double.doubleToRawLongBits(b);return Objects.equals(first,second);}
    private static int arrayBase(Class<?> type){try{return (Integer)BASE.invoke(UNSAFE,type);}catch(ReflectiveOperationException failure){throw unsupported("ARRAY_BASE");}}
    private static int arrayScale(Class<?> type){try{int value=(Integer)SCALE.invoke(UNSAFE,type);if(value<=0)throw unsupported("ARRAY_SCALE");return value;}catch(ReflectiveOperationException failure){throw unsupported("ARRAY_SCALE");}}
    private static int width(Class<?> type){return type==long.class||type==double.class?8:type==int.class||type==float.class?4:type==short.class||type==char.class?2:type.isPrimitive()?1:arrayScale(Object[].class);}
    private static boolean overlap(long first,long length,long second,long width){return first>=0&&second>=0&&length>0&&width>0&&first<=Long.MAX_VALUE-length&&second<=Long.MAX_VALUE-width&&first<second+width&&second<first+length;}
    static final class Unresolved extends RuntimeException {private Unresolved(String reason){super("EQUIPMENT_SLOT_CARRIER_UNRESOLVED:"+reason);}}
    private static Unresolved unsupported(String reason){return new Unresolved(reason);}
}
