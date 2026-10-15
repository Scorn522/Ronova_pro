package dev.ronova.pro;

import dev.ronova.pro.mixin.Access;
import java.lang.reflect.*;
import java.nio.ByteOrder;
import java.util.*;
import net.minecraft.core.IdMap;
import net.minecraft.core.IdMapper;
import it.unimi.dsi.fastutil.Hash;
import it.unimi.dsi.fastutil.HashCommon;
import it.unimi.dsi.fastutil.objects.AbstractObject2IntFunction;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenCustomHashMap;
import net.minecraft.util.*;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.*;

/** Read the current world chain; neither a captured graph nor the caller grants a write. */
final class WorldStatePolicy {
    private enum Kind { KNOWN, ARRAY, FIELD, OFFSET, MEMORY, MAPPING, REHASH }
    private record Slot(Field field,long offset,int width) { }
    static final class Query {
        final Kind kind;final Object receiver,next;final Field field;final int index;final long offset,length;
        Query(Kind kind,Object receiver,Field field,int index,Object next,long offset,long length){this.kind=kind;this.receiver=receiver;this.field=field;this.index=index;this.next=next;this.offset=offset;this.length=length;}
    }
    static final class Slice {
        final LevelChunk chunk;final LevelChunkSection[] sections;final int section;final LevelChunkSection body;final PalettedContainer<?> states;
        final int minX,minY,minZ,maxX,maxY,maxZ;
        Slice(LevelChunk chunk,LevelChunkSection[] sections,int section,LevelChunkSection body,PalettedContainer<?> states,int minX,int minY,int minZ,int maxX,int maxY,int maxZ){
            this.chunk=chunk;this.sections=sections;this.section=section;this.body=body;this.states=states;this.minX=minX;this.minY=minY;this.minZ=minZ;this.maxX=maxX;this.maxY=maxY;this.maxZ=maxZ;
        }
    }
    private static final Object UNKNOWN=new Object();
    private static final boolean LITTLE=ByteOrder.nativeOrder()==ByteOrder.LITTLE_ENDIAN;
    private static volatile boolean initialized;
    private static Class<?> DATA;
    private static Field SECTIONS,SECTION_STATES,PALETTE_DATA,STRATEGY,REGISTRY,STORAGE,PALETTE,SIMPLE_DATA,SINGLE_VALUE,LINEAR_VALUES,LINEAR_SIZE,HASH_VALUES,BY_ID,GLOBAL_REGISTRY;
    private static Field ID_LIST,ID_REVERSE,LIST_DATA,LIST_SIZE,REVERSE_KEYS,REVERSE_VALUES,REVERSE_MASK,REVERSE_STRATEGY,REVERSE_DEFAULT;
    private static Object IDENTITY_STRATEGY;
    private static Map<Class<?>,List<Slot>> FIELDS=Map.of();
    private static long LONG_BASE,INT_BASE,REFERENCE_BASE;private static int REFERENCE_WIDTH;
    private record Anchor(Class<?> owner,String method,int bci) { }
    private static final StackWalker WALKER=StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE);
    private static final ThreadLocal<Rehash> REHASH=new ThreadLocal<>();
    private static final class Rehash {
        final Object receiver;final Object[] keys;final int[] values;final int mask;final List<Anchor> anchor;int step;
        Rehash(Object receiver,Object[] keys,int[] values,int mask,List<Anchor> anchor){this.receiver=receiver;this.keys=keys;this.values=values;this.mask=mask;this.anchor=anchor;}
    }
    private WorldStatePolicy() { }

    static synchronized void initialize()throws ReflectiveOperationException{
        if(initialized)return;
        DATA=Class.forName("net.minecraft.world.level.chunk.PalettedContainer$Data",false,PalettedContainer.class.getClassLoader());
        Class<?> unsafeType=Class.forName("jdk.internal.misc.Unsafe",false,null);
        Class<?>[] carriers={ChunkAccess.class,LevelChunk.class,LevelChunkSection.class,PalettedContainer.class,DATA,SimpleBitStorage.class,ZeroBitStorage.class,
                SingleValuePalette.class,LinearPalette.class,HashMapPalette.class,GlobalPalette.class,CrudeIncrementalIntIdentityHashBiMap.class,
                IdMapper.class,ArrayList.class,Object2IntOpenCustomHashMap.class,AbstractObject2IntFunction.class,unsafeType};
        Class.forName("dev.ronova.pro.agent.RecoveryAgent",false,ClassLoader.getSystemClassLoader()).getMethod("openBackingAccess",Class.class,Class[].class)
                .invoke(null,WorldStatePolicy.class,(Object)carriers);
        Object unsafe=unsafeType.getMethod("getUnsafe").invoke(null);Method offset=unsafeType.getMethod("objectFieldOffset",Field.class);
        LONG_BASE=((Number)unsafeType.getMethod("arrayBaseOffset",Class.class).invoke(unsafe,long[].class)).longValue();
        INT_BASE=((Number)unsafeType.getMethod("arrayBaseOffset",Class.class).invoke(unsafe,int[].class)).longValue();
        REFERENCE_BASE=((Number)unsafeType.getMethod("arrayBaseOffset",Class.class).invoke(unsafe,Object[].class)).longValue();
        REFERENCE_WIDTH=((Number)unsafeType.getMethod("arrayIndexScale",Class.class).invoke(unsafe,Object[].class)).intValue();
        if(REFERENCE_WIDTH<=0)throw new IllegalStateException("WORLD_STATE_REFERENCE_WIDTH_UNAVAILABLE");
        SECTIONS=typed(ChunkAccess.class,LevelChunkSection[].class);SECTION_STATES=typed(LevelChunkSection.class,PalettedContainer.class);
        PALETTE_DATA=typed(PalettedContainer.class,DATA);STRATEGY=typed(PalettedContainer.class,PalettedContainer.Strategy.class);REGISTRY=typed(PalettedContainer.class,IdMap.class);
        STORAGE=typed(DATA,BitStorage.class);PALETTE=typed(DATA,Palette.class);SIMPLE_DATA=typed(SimpleBitStorage.class,long[].class);
        SINGLE_VALUE=typed(SingleValuePalette.class,Object.class);LINEAR_VALUES=typed(LinearPalette.class,Object[].class);LINEAR_SIZE=named(LinearPalette.class,"size","f_63030_");
        HASH_VALUES=typed(HashMapPalette.class,CrudeIncrementalIntIdentityHashBiMap.class);BY_ID=named(CrudeIncrementalIntIdentityHashBiMap.class,"byId","f_13548_");GLOBAL_REGISTRY=typed(GlobalPalette.class,IdMap.class);
        ID_LIST=typed(IdMapper.class,List.class);ID_REVERSE=typed(IdMapper.class,Object2IntMap.class);
        LIST_DATA=typed(ArrayList.class,Object[].class);LIST_SIZE=named(ArrayList.class,"size");
        REVERSE_KEYS=named(Object2IntOpenCustomHashMap.class,"key");REVERSE_VALUES=named(Object2IntOpenCustomHashMap.class,"value");REVERSE_MASK=named(Object2IntOpenCustomHashMap.class,"mask");
        REVERSE_STRATEGY=typed(Object2IntOpenCustomHashMap.class,Hash.Strategy.class);REVERSE_DEFAULT=named(AbstractObject2IntFunction.class,"defRetValue");
        IDENTITY_STRATEGY=net.minecraft.Util.identityStrategy();
        Map<Class<?>,List<Slot>> fields=new HashMap<>();
        for(Class<?> type:carriers){
            if(type==unsafeType)continue;List<Slot> slots=new ArrayList<>();
            for(Class<?> owner=type;owner!=null&&owner!=Object.class;owner=owner.getSuperclass())for(Field field:owner.getDeclaredFields())if(!Modifier.isStatic(field.getModifiers())&&selected(field)){
                field.setAccessible(true);slots.add(new Slot(field,((Number)offset.invoke(unsafe,field)).longValue(),width(field.getType())));
            }
            fields.put(type,List.copyOf(slots));
        }
        FIELDS=Map.copyOf(fields);initialized=true;
    }
    private static Field typed(Class<?> owner,Class<?> type)throws ReflectiveOperationException{
        Field found=null;for(Field field:owner.getDeclaredFields())if(!Modifier.isStatic(field.getModifiers())&&field.getType()==type){if(found!=null)throw new NoSuchFieldException("WORLD_STATE_AMBIGUOUS_FIELD:"+owner+":"+type);found=field;}
        if(found==null)throw new NoSuchFieldException("WORLD_STATE_FIELD:"+owner+":"+type);found.setAccessible(true);return found;
    }
    private static Field named(Class<?> owner,String... names)throws ReflectiveOperationException{
        for(String name:names)try{Field field=owner.getDeclaredField(name);field.setAccessible(true);return field;}catch(NoSuchFieldException missing){ }
        throw new NoSuchFieldException(owner+":"+Arrays.toString(names));
    }
    private static boolean selected(Field field){
        Class<?> owner=field.getDeclaringClass(),type=field.getType();
        if(owner==ChunkAccess.class)return type==LevelChunkSection[].class||type==ChunkPos.class||type==LevelHeightAccessor.class;
        if(owner==LevelChunk.class)return type==Level.class;
        if(owner==LevelChunkSection.class)return field.equals(SECTION_STATES);
        if(owner==PalettedContainer.class)return field.equals(PALETTE_DATA)||field.equals(STRATEGY)||field.equals(REGISTRY);
        if(owner==DATA||owner==SimpleBitStorage.class||owner==ZeroBitStorage.class)return true;
        return field.equals(SINGLE_VALUE)||field.equals(LINEAR_VALUES)||field.equals(LINEAR_SIZE)||field.equals(HASH_VALUES)||field.equals(BY_ID)||field.equals(GLOBAL_REGISTRY)
                ||field.equals(ID_LIST)||field.equals(ID_REVERSE)||field.equals(LIST_DATA)||field.equals(LIST_SIZE)||field.equals(REVERSE_KEYS)||field.equals(REVERSE_VALUES)
                ||field.equals(REVERSE_MASK)||field.equals(REVERSE_STRATEGY)||field.equals(REVERSE_DEFAULT);
    }
    private static int width(Class<?> type){return !type.isPrimitive()?REFERENCE_WIDTH:type==long.class||type==double.class?8:type==int.class||type==float.class?4:type==short.class||type==char.class?2:1;}
    private static boolean candidate(Object value){
        if(!initialized||value==null)return false;Class<?> type=value.getClass();
        if(type==long[].class||type==LevelChunkSection[].class)return true;
        if(type==IdMapper.class||type==ArrayList.class||type==Object2IntOpenCustomHashMap.class||type==int[].class)return globalCarrier(value);
        // An unused ID may have been cleared or changed without affecting a
        // frozen cell. Any remaining frozen BlockState still makes its actual
        // table a candidate; ordinary Object[] traffic stays outside the world walk.
        if(type==Object[].class){for(Object item:(Object[])value)if(item instanceof BlockState)return true;return false;}
        return FIELDS.containsKey(type);
    }
    static boolean known(Object value){return candidate(value)&&ProRuntime.worldStateQuery(new Query(Kind.KNOWN,value,null,-1,null,0,0));}
    static boolean arrayDenied(Object array,int index,Object next){
        if(!candidate(array)||!array.getClass().isArray()||index<0||index>=Array.getLength(array))return false;
        Object prior=Array.get(array,index);if(same(prior,next,array.getClass().getComponentType()))return false;
        return ProRuntime.worldStateQuery(new Query(Kind.ARRAY,array,null,index,next,0,0));
    }
    static boolean fieldDenied(Object receiver,Class<?> declaring,String name,Object next){
        if(!candidate(receiver)||declaring==null)return false;
        for(Slot slot:FIELDS.getOrDefault(receiver.getClass(),List.of()))if(slot.field.getDeclaringClass()==declaring&&slot.field.getName().equals(name)){
            if(rehashWrite(receiver,slot.field,next))return false;
            if(same(read(receiver,slot.field,null),next,slot.field.getType()))return false;
            return ProRuntime.worldStateQuery(new Query(Kind.FIELD,receiver,slot.field,-1,next,slot.offset,slot.width));
        }return false;
    }
    static boolean offsetDenied(Object receiver,long offset,Object next){
        if(!candidate(receiver))return false;
        if(receiver instanceof Object[]){
            // This value-only backing ABI cannot distinguish putObject(Long)
            // from an eight-byte primitive store into compressed reference slots.
            // Do not let the latter silently cross into the next frozen entry.
            if(scalar(next))return memoryDenied(receiver,offset,Math.max(REFERENCE_WIDTH,scalarWidth(next)));
            if(offset<REFERENCE_BASE||(offset-REFERENCE_BASE)%REFERENCE_WIDTH!=0)return memoryDenied(receiver,offset,scalarWidth(next));
            return ProRuntime.worldStateQuery(new Query(Kind.OFFSET,receiver,null,-1,next,offset,REFERENCE_WIDTH));
        }
        if(receiver instanceof long[]||receiver instanceof int[])return scalar(next)?ProRuntime.worldStateQuery(new Query(Kind.OFFSET,receiver,null,-1,next,offset,scalarWidth(next))):memoryDenied(receiver,offset,scalarWidth(next));
        int length=scalarWidth(next);for(Slot slot:FIELDS.getOrDefault(receiver.getClass(),List.of()))if(overlap(offset,length,slot.offset,slot.width)){
            if(slot.field.getType().isPrimitive()&&scalar(next)){
                Object prior=read(receiver,slot.field,null);if(prior==UNKNOWN)return true;
                long bits=patch(scalarBits(prior),slot.offset,slot.width,offset,length,scalarBits(next));
                if(fieldDenied(receiver,slot.field.getDeclaringClass(),slot.field.getName(),boxed(bits,slot.field.getType())))return true;
            }else if(offset==slot.offset&&!slot.field.getType().isPrimitive()){
                if(fieldDenied(receiver,slot.field.getDeclaringClass(),slot.field.getName(),next))return true;
            }else if(ProRuntime.worldStateQuery(new Query(Kind.MEMORY,receiver,slot.field,-1,null,offset,length)))return true;
        }return false;
    }
    static boolean memoryDenied(Object receiver,long offset,long length){return length>0&&candidate(receiver)&&ProRuntime.worldStateQuery(new Query(Kind.MEMORY,receiver,null,-1,null,offset,length));}
    static boolean storageDenied(SimpleBitStorage storage,int index,int value){
        if(!initialized||storage.getClass()!=SimpleBitStorage.class)return false;
        Access.StateBits raw=(Access.StateBits)storage;int word=raw.pro$cell(index),shift=(index-word*raw.pro$perLong())*storage.getBits();long mask=raw.pro$mask();long[] data=raw.pro$words();
        long prior=data[word],next=(prior&~(mask<<shift))|(((long)value&mask)<<shift);
        return prior!=next&&arrayDenied(data,word,next);
    }
    static boolean mappingDenied(IdMapper<?> registry,Object state,int id){
        return initialized&&registry==Block.BLOCK_STATE_REGISTRY&&registry.getClass()==IdMapper.class
                &&ProRuntime.worldStateQuery(new Query(Kind.MAPPING,registry,null,id,state,0,0));
    }
    static Object rehashEnter(Object receiver,int capacity,int mask,Object[] oldKeys,int[] oldValues,Object[] keys,int[] values){
        if(!initialized||receiver==null||receiver.getClass()!=Object2IntOpenCustomHashMap.class||receiver!=read(Block.BLOCK_STATE_REGISTRY,ID_REVERSE,null))return null;
        GlobalTable current=new GlobalTable(null);if(!current.identity)return null;
        List<Anchor> anchor=rehashAnchor();if(anchor==null)throw new SecurityException("ACTUAL_WORLD_REGISTRY_REHASH_REQUIRED");
        if(REHASH.get()!=null)throw new SecurityException("WORLD_REGISTRY_REHASH_REENTRY");
        if(current.keys!=oldKeys||current.ids!=oldValues||capacity<=0||(capacity&(capacity-1))!=0||mask!=capacity-1
                ||keys==null||values==null||(long)capacity+1!=keys.length||keys.length!=values.length)
            throw new SecurityException("ACTUAL_WORLD_REGISTRY_REHASH_TUPLE_REQUIRED");
        Rehash tuple=new Rehash(receiver,keys,values,mask,anchor);
        if(ProRuntime.worldStateQuery(new Query(Kind.REHASH,receiver,null,-1,tuple,0,0)))throw new SecurityException("FROZEN_WORLD_REGISTRY_REHASH_REJECTED");
        REHASH.set(tuple);return tuple;
    }
    static void rehashExit(Object token){
        if(token==null)return;Rehash tuple=REHASH.get();
        // Removal is unconditional for this exact invocation, including an error
        // from another field policy while the original stores are in progress.
        if(tuple==token)REHASH.remove();
    }
    private static boolean rehashWrite(Object receiver,Field field,Object next){
        Rehash tuple=REHASH.get();if(tuple==null||tuple.receiver!=receiver||!tuple.anchor.equals(rehashAnchor()))return false;
        Field expected=tuple.step==0?REVERSE_MASK:tuple.step==1?REVERSE_KEYS:tuple.step==2?REVERSE_VALUES:null;
        Object value=tuple.step==0?tuple.mask:tuple.step==1?tuple.keys:tuple.values;
        if(!field.equals(expected)||!same(value,next,field.getType())||receiver!=read(Block.BLOCK_STATE_REGISTRY,ID_REVERSE,null))
            throw new SecurityException("WORLD_REGISTRY_REHASH_PUBLICATION_MISMATCH");
        tuple.step++;return true;
    }
    private static List<Anchor> rehashAnchor(){
        return WALKER.walk(stream->{
            var frames=stream.filter(frame->!frame.getClassName().startsWith("dev.ronova.pro.")).toList();
            if(frames.isEmpty()||frames.get(0).getDeclaringClass()!=Object2IntOpenCustomHashMap.class||!frames.get(0).getMethodName().equals("rehash"))return null;
            // The caller tail distinguishes this invocation from a nested call
            // with the same method name; the current rehash BCI necessarily moves.
            return frames.subList(1,frames.size()).stream().map(frame->new Anchor(frame.getDeclaringClass(),frame.getMethodName(),frame.getByteCodeIndex())).toList();
        });
    }
    static List<Field> unsafeFields(){List<Field> result=new ArrayList<>();for(List<Slot> slots:FIELDS.values())for(Slot slot:slots)if(!result.contains(slot.field))result.add(slot.field);return result;}
    static long actualOffset(Field field){for(List<Slot> slots:FIELDS.values())for(Slot slot:slots)if(slot.field.equals(field))return slot.offset;throw new IllegalArgumentException("WORLD_STATE_FIELD_NOT_MAPPED:"+field);}
    private static boolean same(Object left,Object right,Class<?> type){return type.isPrimitive()?Objects.equals(left,right):left==right;}
    private static boolean overlap(long a,long size,long b,long width){return size>0&&width>0&&a<(b>Long.MAX_VALUE-width?Long.MAX_VALUE:b+width)&&b<(a>Long.MAX_VALUE-size?Long.MAX_VALUE:a+size);}
    private static int scalarWidth(Object value){return value instanceof Long||value instanceof Double?8:value instanceof Integer||value instanceof Float?4:value instanceof Short||value instanceof Character?2:value instanceof Byte||value instanceof Boolean?1:REFERENCE_WIDTH;}
    private static boolean scalar(Object value){return value instanceof Long||value instanceof Integer||value instanceof Short||value instanceof Byte||value instanceof Character||value instanceof Boolean||value instanceof Float||value instanceof Double;}
    private static long scalarBits(Object value){
        if(value instanceof Long v)return v;if(value instanceof Integer v)return Integer.toUnsignedLong(v);if(value instanceof Short v)return Short.toUnsignedLong(v);
        if(value instanceof Byte v)return Byte.toUnsignedLong(v);if(value instanceof Character v)return v;if(value instanceof Boolean v)return v?1:0;
        if(value instanceof Float v)return Integer.toUnsignedLong(Float.floatToRawIntBits(v));if(value instanceof Double v)return Double.doubleToRawLongBits(v);return 0;
    }
    private static Object boxed(long value,Class<?> type){
        if(type==long.class)return value;if(type==int.class)return (int)value;if(type==short.class)return (short)value;if(type==byte.class)return (byte)value;
        if(type==char.class)return (char)value;if(type==boolean.class)return value!=0;if(type==float.class)return Float.intBitsToFloat((int)value);return Double.longBitsToDouble(value);
    }
    private static long patch(long prior,long first,int width,long offset,int length,long bits){
        for(int part=0;part<width;part++){
            long at=first+part;if(!overlap(at,1,offset,length))continue;int source=(int)(at-offset);
            int targetShift=(LITTLE?part:width-1-part)*8,sourceShift=(LITTLE?source:length-1-source)*8;
            prior=(prior&~(255L<<targetShift))|(((bits>>>sourceShift)&255L)<<targetShift);
        }return prior;
    }
    private static Object read(Object receiver,Field field,Query change){
        if(change!=null&&change.kind==Kind.FIELD&&change.receiver==receiver&&field.equals(change.field))return change.next;
        if(change!=null&&change.kind==Kind.REHASH&&change.receiver==receiver){Rehash tuple=(Rehash)change.next;
            if(field.equals(REVERSE_KEYS))return tuple.keys;if(field.equals(REVERSE_VALUES))return tuple.values;if(field.equals(REVERSE_MASK))return tuple.mask;
        }
        try{return field.get(receiver);}catch(IllegalAccessException|IllegalArgumentException unavailable){return UNKNOWN;}
    }
    private static Object element(Object array,int index,Query change){
        if(array==null||!array.getClass().isArray()||index<0||index>=Array.getLength(array))return UNKNOWN;
        if(change!=null&&change.receiver==array){
            if(change.kind==Kind.ARRAY&&change.index==index)return change.next;
            if(change.kind==Kind.OFFSET&&array instanceof Object[]&&change.offset==REFERENCE_BASE+(long)index*REFERENCE_WIDTH)return change.next;
            if(change.kind==Kind.OFFSET&&array instanceof int[] ints)return (int)patch(Integer.toUnsignedLong(ints[index]),INT_BASE+(long)index*4,4,change.offset,(int)change.length,scalarBits(change.next));
        }return Array.get(array,index);
    }
    private static long word(long[] array,int index,Query change){
        long prior=array[index];if(change==null||change.receiver!=array)return prior;
        if(change.kind==Kind.ARRAY&&change.index==index)return scalarBits(change.next);
        if(change.kind!=Kind.OFFSET)return prior;
        return patch(prior,LONG_BASE+(long)index*8,8,change.offset,(int)change.length,scalarBits(change.next));
    }
    private static boolean globalCarrier(Object target){
        Object registry=Block.BLOCK_STATE_REGISTRY;if(registry.getClass()!=IdMapper.class)return false;
        if(target==registry)return true;
        Object list=read(registry,ID_LIST,null),map=read(registry,ID_REVERSE,null);
        if(target==list||target==map)return true;
        if(list!=null&&list.getClass()==ArrayList.class&&target==read(list,LIST_DATA,null))return true;
        return map!=null&&map.getClass()==Object2IntOpenCustomHashMap.class&&(target==read(map,REVERSE_KEYS,null)||target==read(map,REVERSE_VALUES,null));
    }
    /** Only the standard identity strategy is decoded; no candidate supplies a callback. */
    private static final class GlobalTable {
        final Object registry,list,map,values,keys,ids,strategy;final int size,mask;final Integer missing;final boolean identity;
        GlobalTable(Query change){
            registry=Block.BLOCK_STATE_REGISTRY;boolean standard=registry.getClass()==IdMapper.class;
            list=standard?read(registry,ID_LIST,change):UNKNOWN;map=standard?read(registry,ID_REVERSE,change):UNKNOWN;
            boolean arrayList=list!=null&&list.getClass()==ArrayList.class,customMap=map!=null&&map.getClass()==Object2IntOpenCustomHashMap.class;
            values=arrayList?read(list,LIST_DATA,change):UNKNOWN;Object count=arrayList?read(list,LIST_SIZE,change):UNKNOWN;size=count instanceof Integer n?n:-1;
            keys=customMap?read(map,REVERSE_KEYS,change):UNKNOWN;ids=customMap?read(map,REVERSE_VALUES,change):UNKNOWN;
            Object hashed=customMap?read(map,REVERSE_MASK,change):UNKNOWN,def=customMap?read(map,REVERSE_DEFAULT,change):UNKNOWN;
            mask=hashed instanceof Integer n?n:-1;missing=def instanceof Integer n?n:null;
            strategy=customMap?read(map,REVERSE_STRATEGY,change):UNKNOWN;identity=strategy==IDENTITY_STRATEGY;
        }
        boolean linked(Object target){return target==registry||target==list||target==map||target==values||target==keys||target==ids;}
        boolean current(){
            if(registry!=Block.BLOCK_STATE_REGISTRY||read(registry,ID_LIST,null)!=list||read(registry,ID_REVERSE,null)!=map)return false;
            if(list!=null&&list.getClass()==ArrayList.class&&(read(list,LIST_DATA,null)!=values||!Objects.equals(read(list,LIST_SIZE,null),size)))return false;
            return map==null||map.getClass()!=Object2IntOpenCustomHashMap.class||read(map,REVERSE_KEYS,null)==keys&&read(map,REVERSE_VALUES,null)==ids
                    &&Objects.equals(read(map,REVERSE_MASK,null),mask)&&Objects.equals(read(map,REVERSE_DEFAULT,null),missing)&&read(map,REVERSE_STRATEGY,null)==strategy;
        }
        Object state(int id,Query change){return values instanceof Object[] array&&size>=0&&size<=array.length&&id>=0&&id<size?element(array,id,change):UNKNOWN;}
        Integer reverse(Object state,Query change){
            if(!identity||!(keys instanceof Object[] array)||!(ids instanceof int[])||mask<0)return null;
            int at=HashCommon.mix(System.identityHashCode(state))&mask;
            for(int remaining=array.length;remaining>0;remaining--){
                Object key=element(array,at,change);if(key==UNKNOWN)return null;if(key==null)return missing;
                if(key==state){Object id=element(ids,at,change);return id instanceof Integer n?n:null;}
                at=(at+1)&mask;
            }return null;
        }
        boolean memory(Object state,int globalId,Query change){
            Object target=change.receiver;
            if(target==registry)return globalId>=0&&touches(ID_LIST,change)||touches(ID_REVERSE,change);
            if(target==list)return globalId>=0&&(touches(LIST_DATA,change)||touches(LIST_SIZE,change));
            if(target==map)return touches(REVERSE_KEYS,change)||touches(REVERSE_VALUES,change)||touches(REVERSE_MASK,change)||touches(REVERSE_STRATEGY,change)
                    ||touches(REVERSE_DEFAULT,change)&&reverseSlot(state)<0;
            if(target==values&&globalId>=0&&overlap(change.offset,change.length,REFERENCE_BASE+(long)globalId*REFERENCE_WIDTH,REFERENCE_WIDTH))return true;
            if(target!=keys&&target!=ids)return false;
            if(!identity||!(keys instanceof Object[] array)||!(ids instanceof int[])||mask<0)return true;
            int at=HashCommon.mix(System.identityHashCode(state))&mask;
            for(int remaining=array.length;remaining>0;remaining--){
                Object key=element(array,at,null);if(key==UNKNOWN)return true;
                if(target==keys&&overlap(change.offset,change.length,REFERENCE_BASE+(long)at*REFERENCE_WIDTH,REFERENCE_WIDTH))return true;
                if(key==null)return false;
                if(key==state)return target==ids&&overlap(change.offset,change.length,INT_BASE+(long)at*4,4);
                at=(at+1)&mask;
            }return true;
        }
        private int reverseSlot(Object state){
            if(!identity||!(keys instanceof Object[] array)||mask<0)return -1;
            int at=HashCommon.mix(System.identityHashCode(state))&mask;
            for(int remaining=array.length;remaining>0;remaining--){Object key=element(array,at,null);if(key==UNKNOWN||key==null)return -1;if(key==state)return at;at=(at+1)&mask;}return -1;
        }
        private boolean touches(Field field,Query change){
            for(Slot slot:FIELDS.getOrDefault(change.receiver.getClass(),List.of()))if(slot.field.equals(field))return overlap(change.offset,change.length,slot.offset,slot.width);return false;
        }
    }
    private static final class View {
        final Object data,storage,palette,values;final long[] words;final Object single;final int bits,perLong,size;final long mask;final boolean global;final GlobalTable table;
        View(Object data,Object storage,Object palette,Object values,long[] words,Object single,int bits,int perLong,int size,long mask,boolean global,GlobalTable table){this.data=data;this.storage=storage;this.palette=palette;this.values=values;this.words=words;this.single=single;this.bits=bits;this.perLong=perLong;this.size=size;this.mask=mask;this.global=global;this.table=table;}
        int id(int index,Query change){
            if(storage.getClass()==ZeroBitStorage.class)return index>=0&&index<((ZeroBitStorage)storage).getSize()?0:-1;
            if(index<0||index>=((SimpleBitStorage)storage).getSize()||bits<1||bits>32||perLong<1)return -1;
            int cell=((Access.StateBits)storage).pro$cell(index);if(words==null||cell<0||cell>=words.length)return -1;
            return (int)((word(words,cell,change)>>>((index-cell*perLong)*bits))&mask);
        }
        Object state(int index,Query change){
            int id=id(index,change);if(id<0)return UNKNOWN;
            Object result=global?table.state(id,change):palette.getClass()==SingleValuePalette.class?id==0?single:UNKNOWN:id<size?element(values,id,change):UNKNOWN;
            return result instanceof BlockState?result:UNKNOWN;
        }
    }
    private static View view(Slice slice,Query change){
        Object sections=read(slice.chunk,SECTIONS,change),body=element(sections,slice.section,change);
        if(body==UNKNOWN||body==null||body.getClass()!=LevelChunkSection.class)return null;
        Object states=read(body,SECTION_STATES,change);if(states==null||states.getClass()!=PalettedContainer.class||read(states,STRATEGY,change)!=PalettedContainer.Strategy.SECTION_STATES||read(states,REGISTRY,change)!=Block.BLOCK_STATE_REGISTRY)return null;
        Object data=read(states,PALETTE_DATA,change);if(data==null||data.getClass()!=DATA)return null;
        Object storage=read(data,STORAGE,change),palette=read(data,PALETTE,change);if(storage==null||palette==null||storage.getClass()!=SimpleBitStorage.class&&storage.getClass()!=ZeroBitStorage.class)return null;
        long[] words=null;int bits=0,perLong=0;long mask=0;
        if(storage.getClass()==SimpleBitStorage.class){Object raw=read(storage,SIMPLE_DATA,change);if(!(raw instanceof long[] actual))return null;words=actual;SimpleBitStorage stored=(SimpleBitStorage)storage;bits=stored.getBits();perLong=((Access.StateBits)storage).pro$perLong();mask=((Access.StateBits)storage).pro$mask();}
        Object values=null,single=null;int size=0;boolean global=false;Class<?> type=palette.getClass();
        if(type==SingleValuePalette.class){single=read(palette,SINGLE_VALUE,change);size=1;}
        else if(type==LinearPalette.class){values=read(palette,LINEAR_VALUES,change);Object count=read(palette,LINEAR_SIZE,change);if(!(values instanceof Object[])||!(count instanceof Integer n)||n<0||n>((Object[])values).length)return null;size=n;}
        else if(type==HashMapPalette.class){Object map=read(palette,HASH_VALUES,change);if(map==null||map.getClass()!=CrudeIncrementalIntIdentityHashBiMap.class)return null;values=read(map,BY_ID,change);if(!(values instanceof Object[] array))return null;size=array.length;}
        else if(type==GlobalPalette.class){if(read(palette,GLOBAL_REGISTRY,change)!=Block.BLOCK_STATE_REGISTRY)return null;global=true;}
        else return null;
        return new View(data,storage,palette,values,words,single,bits,perLong,size,mask,global,new GlobalTable(change));
    }
    static boolean matches(Query change,Slice slice){
        View before=view(slice,null);Object target=change.receiver;
        boolean structural=target==slice.chunk||target==slice.sections||target==slice.body||target==slice.states;
        boolean registryLinked=before!=null&&before.table.linked(target);
        boolean linked=structural||registryLinked||before!=null&&(target==before.data||target==before.storage||target==before.words||target==before.palette||target==before.values
                ||before.palette.getClass()==HashMapPalette.class&&target==read(before.palette,HASH_VALUES,null));
        if(!linked)return false;
        return denied(change,slice,before,registryLinked)&&(!registryLinked||before.table.current());
    }
    private static boolean denied(Query change,Slice slice,View before,boolean registryLinked){
        Object target=change.receiver;if(change.kind==Kind.KNOWN)return true;
        if(change.kind==Kind.FIELD){
            if((target==slice.chunk&&!change.field.equals(SECTIONS)||target==slice.states&&!change.field.equals(PALETTE_DATA)
                    ||before!=null&&target==before.storage&&!change.field.equals(SIMPLE_DATA)))return true;
        }
        if(change.kind==Kind.MEMORY&&!registryLinked&&!(target instanceof Object[])&&!(target instanceof long[])){
            for(Slot slot:FIELDS.getOrDefault(target.getClass(),List.of()))if((change.field==null||change.field.equals(slot.field))&&overlap(change.offset,change.length,slot.offset,slot.width))return true;
            return false;
        }
        View after=change.kind==Kind.MEMORY?before:view(slice,change);if(before==null||after==null)return true;
        for(int y=slice.minY;y<=slice.maxY;y++)for(int z=slice.minZ;z<=slice.maxZ;z++)for(int x=slice.minX;x<=slice.maxX;x++){
            int index=(y<<8)|(z<<4)|x;
            Object old=before.state(index,null);
            if(change.kind==Kind.MAPPING){
                if(before.global&&before.id(index,null)==change.index&&old!=change.next||old==change.next&&!Objects.equals(before.table.reverse(old,null),change.index))return true;
                continue;
            }
            if(change.kind==Kind.MEMORY){
                if(registryLinked&&before.table.memory(old,before.global?before.id(index,null):-1,change))return true;
                if(target==slice.sections&&overlap(change.offset,change.length,REFERENCE_BASE+(long)slice.section*REFERENCE_WIDTH,REFERENCE_WIDTH))return true;
                if(target==before.values){int id=before.id(index,null);if(id>=0&&overlap(change.offset,change.length,REFERENCE_BASE+(long)id*REFERENCE_WIDTH,REFERENCE_WIDTH))return true;}
                if(target==before.words&&before.storage.getClass()==SimpleBitStorage.class){
                    int cell=((Access.StateBits)before.storage).pro$cell(index),shift=(index-cell*before.perLong)*before.bits;long mask=before.mask<<shift;
                    for(int part=0;part<8;part++)if((mask&(255L<<((LITTLE?part:7-part)*8)))!=0&&overlap(change.offset,change.length,LONG_BASE+(long)cell*8+part,1))return true;
                }
            }else{
                Object next=after.state(index,change);if(old==UNKNOWN||next==UNKNOWN||old!=next)return true;
                if(registryLinked){Integer prior=before.table.reverse(old,null),proposed=after.table.reverse(old,change);if(prior==null||proposed==null||!prior.equals(proposed))return true;}
            }
        }
        return false;
    }
}
