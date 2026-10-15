package dev.ronova.pro;

import dev.ronova.pro.mixin.Access;
import java.lang.reflect.*;
import java.nio.ByteOrder;
import java.util.*;
import net.minecraft.core.IdMap;
import net.minecraft.util.*;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.*;

/** Read the current world chain; neither a captured graph nor the caller grants a write. */
final class WorldStatePolicy {
    private enum Kind { KNOWN, ARRAY, FIELD, OFFSET, MEMORY }
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
    private static Map<Class<?>,List<Slot>> FIELDS=Map.of();
    private static long LONG_BASE,REFERENCE_BASE;private static int REFERENCE_WIDTH;
    private WorldStatePolicy() { }

    static synchronized void initialize()throws ReflectiveOperationException{
        if(initialized)return;
        DATA=Class.forName("net.minecraft.world.level.chunk.PalettedContainer$Data",false,PalettedContainer.class.getClassLoader());
        Class<?> unsafeType=Class.forName("jdk.internal.misc.Unsafe",false,null);
        Class<?>[] carriers={ChunkAccess.class,LevelChunk.class,LevelChunkSection.class,PalettedContainer.class,DATA,SimpleBitStorage.class,ZeroBitStorage.class,
                SingleValuePalette.class,LinearPalette.class,HashMapPalette.class,GlobalPalette.class,CrudeIncrementalIntIdentityHashBiMap.class,unsafeType};
        Class.forName("dev.ronova.pro.agent.RecoveryAgent",false,ClassLoader.getSystemClassLoader()).getMethod("openBackingAccess",Class.class,Class[].class)
                .invoke(null,WorldStatePolicy.class,(Object)carriers);
        Object unsafe=unsafeType.getMethod("getUnsafe").invoke(null);Method offset=unsafeType.getMethod("objectFieldOffset",Field.class);
        LONG_BASE=((Number)unsafeType.getMethod("arrayBaseOffset",Class.class).invoke(unsafe,long[].class)).longValue();
        REFERENCE_BASE=((Number)unsafeType.getMethod("arrayBaseOffset",Class.class).invoke(unsafe,Object[].class)).longValue();
        REFERENCE_WIDTH=((Number)unsafeType.getMethod("arrayIndexScale",Class.class).invoke(unsafe,Object[].class)).intValue();
        if(REFERENCE_WIDTH<=0)throw new IllegalStateException("WORLD_STATE_REFERENCE_WIDTH_UNAVAILABLE");
        SECTIONS=typed(ChunkAccess.class,LevelChunkSection[].class);SECTION_STATES=typed(LevelChunkSection.class,PalettedContainer.class);
        PALETTE_DATA=typed(PalettedContainer.class,DATA);STRATEGY=typed(PalettedContainer.class,PalettedContainer.Strategy.class);REGISTRY=typed(PalettedContainer.class,IdMap.class);
        STORAGE=typed(DATA,BitStorage.class);PALETTE=typed(DATA,Palette.class);SIMPLE_DATA=typed(SimpleBitStorage.class,long[].class);
        SINGLE_VALUE=typed(SingleValuePalette.class,Object.class);LINEAR_VALUES=typed(LinearPalette.class,Object[].class);LINEAR_SIZE=named(LinearPalette.class,"size","f_63030_");
        HASH_VALUES=typed(HashMapPalette.class,CrudeIncrementalIntIdentityHashBiMap.class);BY_ID=named(CrudeIncrementalIntIdentityHashBiMap.class,"byId","f_13548_");GLOBAL_REGISTRY=typed(GlobalPalette.class,IdMap.class);
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
        return field.equals(SINGLE_VALUE)||field.equals(LINEAR_VALUES)||field.equals(LINEAR_SIZE)||field.equals(HASH_VALUES)||field.equals(BY_ID)||field.equals(GLOBAL_REGISTRY);
    }
    private static int width(Class<?> type){return !type.isPrimitive()?REFERENCE_WIDTH:type==long.class||type==double.class?8:type==int.class||type==float.class?4:type==short.class||type==char.class?2:1;}
    private static boolean candidate(Object value){
        if(!initialized||value==null)return false;Class<?> type=value.getClass();
        if(type==long[].class||type==LevelChunkSection[].class)return true;
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
        if(receiver instanceof long[])return scalar(next)?ProRuntime.worldStateQuery(new Query(Kind.OFFSET,receiver,null,-1,next,offset,scalarWidth(next))):memoryDenied(receiver,offset,scalarWidth(next));
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
        try{return field.get(receiver);}catch(IllegalAccessException|IllegalArgumentException unavailable){return UNKNOWN;}
    }
    private static Object element(Object array,int index,Query change){
        if(array==null||!array.getClass().isArray()||index<0||index>=Array.getLength(array))return UNKNOWN;
        if(change!=null&&change.receiver==array){
            if(change.kind==Kind.ARRAY&&change.index==index)return change.next;
            if(change.kind==Kind.OFFSET&&array instanceof Object[]&&change.offset==REFERENCE_BASE+(long)index*REFERENCE_WIDTH)return change.next;
        }return Array.get(array,index);
    }
    private static long word(long[] array,int index,Query change){
        long prior=array[index];if(change==null||change.receiver!=array)return prior;
        if(change.kind==Kind.ARRAY&&change.index==index)return scalarBits(change.next);
        if(change.kind!=Kind.OFFSET)return prior;
        return patch(prior,LONG_BASE+(long)index*8,8,change.offset,(int)change.length,scalarBits(change.next));
    }
    private static final class View {
        final Object data,storage,palette,values;final long[] words;final Object single;final int bits,perLong,size;final long mask;final boolean global;
        View(Object data,Object storage,Object palette,Object values,long[] words,Object single,int bits,int perLong,int size,long mask,boolean global){this.data=data;this.storage=storage;this.palette=palette;this.values=values;this.words=words;this.single=single;this.bits=bits;this.perLong=perLong;this.size=size;this.mask=mask;this.global=global;}
        int id(int index,Query change){
            if(storage.getClass()==ZeroBitStorage.class)return index>=0&&index<((ZeroBitStorage)storage).getSize()?0:-1;
            if(index<0||index>=((SimpleBitStorage)storage).getSize()||bits<1||bits>32||perLong<1)return -1;
            int cell=((Access.StateBits)storage).pro$cell(index);if(words==null||cell<0||cell>=words.length)return -1;
            return (int)((word(words,cell,change)>>>((index-cell*perLong)*bits))&mask);
        }
        Object state(int index,Query change){
            int id=id(index,change);if(id<0)return UNKNOWN;
            Object result=global?Block.BLOCK_STATE_REGISTRY.byId(id):palette.getClass()==SingleValuePalette.class?id==0?single:UNKNOWN:id<size?element(values,id,change):UNKNOWN;
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
        return new View(data,storage,palette,values,words,single,bits,perLong,size,mask,global);
    }
    static boolean matches(Query change,Slice slice){
        View before=view(slice,null);Object target=change.receiver;
        boolean structural=target==slice.chunk||target==slice.sections||target==slice.body||target==slice.states;
        boolean linked=structural||before!=null&&(target==before.data||target==before.storage||target==before.words||target==before.palette||target==before.values
                ||before.palette.getClass()==HashMapPalette.class&&target==read(before.palette,HASH_VALUES,null));
        if(!linked)return false;if(change.kind==Kind.KNOWN)return true;
        if(change.kind==Kind.FIELD){
            if((target==slice.chunk&&!change.field.equals(SECTIONS)||target==slice.states&&!change.field.equals(PALETTE_DATA)
                    ||before!=null&&target==before.storage&&!change.field.equals(SIMPLE_DATA)))return true;
        }
        if(change.kind==Kind.MEMORY&&!(target instanceof Object[])&&!(target instanceof long[])){
            for(Slot slot:FIELDS.getOrDefault(target.getClass(),List.of()))if((change.field==null||change.field.equals(slot.field))&&overlap(change.offset,change.length,slot.offset,slot.width))return true;
            return false;
        }
        View after=change.kind==Kind.MEMORY?before:view(slice,change);if(before==null||after==null)return true;
        for(int y=slice.minY;y<=slice.maxY;y++)for(int z=slice.minZ;z<=slice.maxZ;z++)for(int x=slice.minX;x<=slice.maxX;x++){
            int index=(y<<8)|(z<<4)|x;
            if(change.kind==Kind.MEMORY){
                if(target==slice.sections&&overlap(change.offset,change.length,REFERENCE_BASE+(long)slice.section*REFERENCE_WIDTH,REFERENCE_WIDTH))return true;
                if(target==before.values){int id=before.id(index,null);if(id>=0&&overlap(change.offset,change.length,REFERENCE_BASE+(long)id*REFERENCE_WIDTH,REFERENCE_WIDTH))return true;}
                if(target==before.words&&before.storage.getClass()==SimpleBitStorage.class){
                    int cell=((Access.StateBits)before.storage).pro$cell(index),shift=(index-cell*before.perLong)*before.bits;long mask=before.mask<<shift;
                    for(int part=0;part<8;part++)if((mask&(255L<<((LITTLE?part:7-part)*8)))!=0&&overlap(change.offset,change.length,LONG_BASE+(long)cell*8+part,1))return true;
                }
            }else{Object old=before.state(index,null),next=after.state(index,change);if(old==UNKNOWN||next==UNKNOWN||old!=next)return true;}
        }
        return false;
    }
}
