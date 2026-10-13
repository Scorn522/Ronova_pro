package dev.ronova.pro;

import java.lang.reflect.*;
import java.util.*;

/** Actual declared fields, including final/record/hidden fields; no offsets are inferred from names. */
final class GraphFields {
    private static final Object UNSAFE;
    private static final Class<?> UNSAFE_TYPE;
    private static final Method OBJECT_OFFSET,STATIC_OFFSET,STATIC_BASE,INITIALIZE;
    private static final Method NATIVE_WRITE;
    static {
        try{
            UNSAFE_TYPE=Class.forName("sun.misc.Unsafe");Field singleton=UNSAFE_TYPE.getDeclaredField("theUnsafe");singleton.setAccessible(true);UNSAFE=singleton.get(null);
            OBJECT_OFFSET=UNSAFE_TYPE.getMethod("objectFieldOffset",Field.class);STATIC_OFFSET=UNSAFE_TYPE.getMethod("staticFieldOffset",Field.class);STATIC_BASE=UNSAFE_TYPE.getMethod("staticFieldBase",Field.class);INITIALIZE=UNSAFE_TYPE.getMethod("shouldBeInitialized",Class.class);
            NATIVE_WRITE=Class.forName("dev.ronova.pro.bootstrap.NativeControl",false,null).getMethod("restoreRecoveredField",Field.class,Object.class,Object.class,Object.class);
        }catch(ReflectiveOperationException unavailable){throw new ExceptionInInitializerError(unavailable);}
    }
    private GraphFields(){}
    static List<Field> fields(Class<?> type,boolean statics){
        List<Field> fields=new ArrayList<>();List<Class<?>> owners=new ArrayList<>();Set<Class<?>> seen=Collections.newSetFromMap(new IdentityHashMap<>());
        Deque<Class<?>> pending=new ArrayDeque<>();pending.add(type);
        while(!pending.isEmpty()){
            Class<?> owner=pending.remove();if(owner==Object.class||!seen.add(owner))continue;owners.add(owner);
            if(owner.getSuperclass()!=null)pending.add(owner.getSuperclass());
            if(statics)Collections.addAll(pending,owner.getInterfaces());
        }
        try{Class.forName("dev.ronova.pro.agent.RecoveryAgent",false,ClassLoader.getSystemClassLoader()).getMethod("openBackingAccess",Class.class,Class[].class).invoke(null,GraphFields.class,(Object)owners.toArray(Class[]::new));}
        catch(ReflectiveOperationException unavailable){throw new IllegalStateException("FIELD_GRAPH_ACCESS_UNAVAILABLE",unavailable);}
        for(Class<?> owner:owners)for(Field field:owner.getDeclaredFields())if(statics==Modifier.isStatic(field.getModifiers()))fields.add(field);
        fields.sort(Comparator.comparing((Field field)->field.getDeclaringClass().getName()).thenComparing(Field::getName).thenComparing(field->field.getType().descriptorString()));return fields;
    }
    static Object read(Field field,Object receiver)throws ReflectiveOperationException{
        boolean statik=Modifier.isStatic(field.getModifiers());if(statik&&Boolean.TRUE.equals(INITIALIZE.invoke(UNSAFE,field.getDeclaringClass())))throw new IllegalStateException("FIELD_GRAPH_CLASS_INITIALIZATION_PENDING");
        if(!statik&&!field.getDeclaringClass().isInstance(receiver))throw new IllegalArgumentException("FIELD_GRAPH_RECEIVER_CHANGED");
        if(!field.trySetAccessible())throw new IllegalAccessException("FIELD_GRAPH_DECLARATION_INACCESSIBLE");
        // Reflection reads actual hidden and record fields without inventing an Unsafe offset for them.
        return field.get(statik?null:receiver);
    }
    static boolean same(Field field,Object left,Object right){return same(field.getType(),left,right);}
    static boolean same(Class<?> type,Object left,Object right){return type.isPrimitive()?bitsEqual(type,left,right):left==right;}
    private static boolean bitsEqual(Class<?> type,Object left,Object right){
        if(type==float.class)return left instanceof Float a&&right instanceof Float b&&Float.floatToRawIntBits(a)==Float.floatToRawIntBits(b);
        if(type==double.class)return left instanceof Double a&&right instanceof Double b&&Double.doubleToRawLongBits(a)==Double.doubleToRawLongBits(b);return Objects.equals(left,right);
    }
    static Object zero(Class<?> type){if(!type.isPrimitive())return null;if(type==boolean.class)return false;if(type==byte.class)return (byte)0;if(type==short.class)return (short)0;if(type==char.class)return (char)0;if(type==int.class)return 0;if(type==long.class)return 0L;if(type==float.class)return 0F;if(type==double.class)return 0D;throw new IllegalArgumentException("FIELD_GRAPH_PRIMITIVE_UNAVAILABLE");}
    static boolean write(Field field,Object receiver,Object expected,Object value)throws ReflectiveOperationException{
        if(!field.getType().isPrimitive()&&value!=null&&!field.getType().isInstance(value))throw new IllegalArgumentException("FIELD_GRAPH_VALUE_TYPE_MISMATCH");
        if(!same(field,read(field,receiver),expected))return false;
        Object target=Modifier.isStatic(field.getModifiers())?field.getDeclaringClass():receiver;
        Class<?> bridge=Class.forName("dev.ronova.pro.bootstrap.ControlBridge",false,null);
        Object gate=bridge.getMethod("beginRecoveryMutation",Object.class).invoke(null,target);if(Boolean.FALSE.equals(gate))return false;boolean written=false;
        try{
            if(!same(field,read(field,receiver),expected))return false;
            if(!field.getDeclaringClass().isHidden()&&!field.getDeclaringClass().isRecord()){
                boolean statik=Modifier.isStatic(field.getModifiers());Object base=statik?STATIC_BASE.invoke(UNSAFE,field):receiver;long offset=((Number)(statik?STATIC_OFFSET:OBJECT_OFFSET).invoke(UNSAFE,field)).longValue();
                if(!field.getType().isPrimitive()){
                    if(!Boolean.TRUE.equals(UNSAFE_TYPE.getMethod("compareAndSwapObject",Object.class,long.class,Object.class,Object.class).invoke(UNSAFE,base,offset,expected,value)))return false;
                    written=true;
                }else{String suffix=field.getType().getName();suffix=Character.toUpperCase(suffix.charAt(0))+suffix.substring(1);written=true;UNSAFE_TYPE.getMethod("put"+suffix+"Volatile",Object.class,long.class,field.getType()).invoke(UNSAFE,base,offset,value);}
            }else{if(!Boolean.TRUE.equals(NATIVE_WRITE.invoke(null,field,receiver,expected,value)))return false;written=true;}
            return same(field,read(field,receiver),value);
        }finally{bridge.getMethod("finishFieldMutation",Object.class,boolean.class).invoke(null,gate,written);}
    }
    static boolean writeArray(Object array,int index,Object expected,Object value)throws ReflectiveOperationException{
        Class<?> type=array.getClass().getComponentType();if(type==null||index<0||index>=Array.getLength(array))throw new IllegalArgumentException("FIELD_GRAPH_ARRAY_POSITION_CHANGED");
        if(!type.isPrimitive()&&value!=null&&!type.isInstance(value))throw new IllegalArgumentException("FIELD_GRAPH_ARRAY_VALUE_TYPE_MISMATCH");
        Class<?> bridge=Class.forName("dev.ronova.pro.bootstrap.ControlBridge",false,null);Object gate=bridge.getMethod("beginRecoveryMutation",Object.class).invoke(null,array);if(Boolean.FALSE.equals(gate))return false;boolean written=false;
        try{
            Object current=Array.get(array,index);if(type.isPrimitive()?!bitsEqual(type,current,expected):current!=expected)return false;
            int base=((Number)UNSAFE_TYPE.getMethod("arrayBaseOffset",Class.class).invoke(UNSAFE,array.getClass())).intValue();
            int scale=((Number)UNSAFE_TYPE.getMethod("arrayIndexScale",Class.class).invoke(UNSAFE,array.getClass())).intValue();if(scale<=0)return false;
            long offset=base+(long)index*scale;
            if(!type.isPrimitive()){if(!Boolean.TRUE.equals(UNSAFE_TYPE.getMethod("compareAndSwapObject",Object.class,long.class,Object.class,Object.class).invoke(UNSAFE,array,offset,expected,value)))return false;written=true;}
            else{String suffix=type.getName();suffix=Character.toUpperCase(suffix.charAt(0))+suffix.substring(1);written=true;UNSAFE_TYPE.getMethod("put"+suffix+"Volatile",Object.class,long.class,type).invoke(UNSAFE,array,offset,value);}
            Object readback=Array.get(array,index);return type.isPrimitive()?bitsEqual(type,readback,value):readback==value;
        }finally{bridge.getMethod("finishFieldMutation",Object.class,boolean.class).invoke(null,gate,written);}
    }
}
