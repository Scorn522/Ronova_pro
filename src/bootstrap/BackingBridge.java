package dev.ronova.pro.bootstrap;

import java.lang.invoke.*;
import java.lang.reflect.*;
import java.util.*;
import java.util.function.*;

/** Actual collection/array mutation adapters. No registration or authority is accepted from a caller. */
public final class BackingBridge {
    public static final Object UNHANDLED=new Object();
    private record Policy(MethodHandle known,MethodHandle element,MethodHandle array,MethodHandle offset,MethodHandle memory) { }
    private static volatile Policy policy;
    private static final ThreadLocal<Boolean> POLICY_CALLBACK=new ThreadLocal<>();
    private static final Field MOD_COUNT;
    static {try {MOD_COUNT=AbstractList.class.getDeclaredField("modCount");MOD_COUNT.setAccessible(true);}catch(Exception e){throw new ExceptionInInitializerError(e);}}
    private BackingBridge() { }
    static void install(MethodHandle known,MethodHandle element,MethodHandle array,MethodHandle offset,MethodHandle memory) {
        policy=new Policy(known.asType(MethodType.methodType(boolean.class,Object.class)),
                element.asType(MethodType.methodType(boolean.class,Object.class,Object.class,Object.class)),
                array.asType(MethodType.methodType(boolean.class,Object.class,int.class,Object.class)),
                offset.asType(MethodType.methodType(boolean.class,Object.class,long.class,Object.class)),
                memory.asType(MethodType.methodType(boolean.class,Object.class,long.class,long.class)));
    }
    public static boolean offsetAllowed(Object receiver,long offset,Object value) {
        Policy current=policy;if(current==null)return true;
        Boolean active=POLICY_CALLBACK.get();POLICY_CALLBACK.set(true);
        try {return !(boolean)current.offset.invokeExact(receiver,offset,value);}catch(Throwable e){throw new IllegalStateException("BACKING_OFFSET_GUARD",e);}
        finally {if(active==null)POLICY_CALLBACK.remove();else POLICY_CALLBACK.set(active);}
    }
    public static boolean arrayAllowed(Object array,int index,Object value) {
        if(!TaskBridge.arrayWriteAllowed(array))return false;
        if(!TaskBridge.controlMutationAllowed(array))return false;
        Policy current=policy;if(current==null)return true;
        Boolean active=POLICY_CALLBACK.get();POLICY_CALLBACK.set(true);
        try {return !(boolean)current.array.invokeExact(array,index,value);}catch(Throwable e){throw new IllegalStateException("BACKING_ARRAY_GUARD",e);}
        finally {if(active==null)POLICY_CALLBACK.remove();else POLICY_CALLBACK.set(active);}
    }
    /** 0 denies the store, 1 requires the existing value checks, 2 permits this bulk store. */
    static int nativePrimitiveArrayPolicy(Object array) {
        if(array==null||!array.getClass().isArray()||!array.getClass().getComponentType().isPrimitive())return 0;
        if(!TaskBridge.arrayWriteAllowed(array)||!TaskBridge.controlMutationAllowed(array))return 0;
        // As with copy/fill, an untracked carrier has no per-element backing policy.
        // The native caller still owns its exact receiver gate and write history.
        return policy==null||!known(array)?2:1;
    }
    private static boolean known(Object value) {
        if(TaskBridge.controlled(value))return true;
        if(value!=null&&value.getClass().isArray()&&!TaskBridge.arrayWriteAllowed(value))return true;
        Policy current=policy;if(current==null)return false;
        Boolean active=POLICY_CALLBACK.get();POLICY_CALLBACK.set(true);
        try {return (boolean)current.known.invokeExact(value);}catch(Throwable e){throw new IllegalStateException("BACKING_LOOKUP",e);}
        finally {if(active==null)POLICY_CALLBACK.remove();else POLICY_CALLBACK.set(active);}
    }
    static boolean nativePointerAllowed(Object array){
        if(!TaskBridge.controlMutationAllowed(array))return false;
        if(!known(array))return true;
        try{
            Class<?> type=Class.forName("sun.misc.Unsafe");Field field=type.getDeclaredField("theUnsafe");field.setAccessible(true);Object unsafe=field.get(null);
            int base=(Integer)type.getMethod("arrayBaseOffset",Class.class).invoke(unsafe,array.getClass());
            int scale=(Integer)type.getMethod("arrayIndexScale",Class.class).invoke(unsafe,array.getClass());
            return memoryWriteAllowed(array,base,(long)scale*Array.getLength(array));
        }catch(ReflectiveOperationException unavailable){throw new IllegalStateException("ACTUAL_ARRAY_POINTER_POLICY_UNAVAILABLE",unavailable);}
    }
    private static boolean deny(Object container,Object prior,Object next) {
        if(!TaskBridge.controlMutationAllowed(container))return true;
        Policy current=policy;if(current==null)return false;
        Boolean active=POLICY_CALLBACK.get();POLICY_CALLBACK.set(true);
        try {return (boolean)current.element.invokeExact(container,prior,next);}catch(Throwable e){throw new IllegalStateException("BACKING_ELEMENT_GUARD",e);}
        finally {if(active==null)POLICY_CALLBACK.remove();else POLICY_CALLBACK.set(active);}
    }
    public static boolean incomingAllowed(Object receiver,Object value) {return !deny(receiver,null,value);}
    /** Checks Java array type/range even when policy withholds a store. */
    public static void arrayStore(Object array,int index,Object value) {
        int length=Array.getLength(array);if(index<0||index>=length)throw new ArrayIndexOutOfBoundsException(index);
        Class<?> component=array.getClass().getComponentType();
        if(!component.isPrimitive()&&value!=null&&!component.isInstance(value))throw new ArrayStoreException(value.getClass().getName());
        Object gate=TaskBridge.beginArrayMutation(array,index,1);
        boolean written=false;try {if(arrayAllowed(array,index,value)){Array.set(array,index,value);written=true;}}finally {TaskBridge.finishFieldMutation(gate,written);}
    }
    public static void intStore(Object array,int index,int value) {
        int[] values=(int[])array;int prior=values[index];int next=(int)value;
        Object gate=TaskBridge.beginArrayMutation(array,index,1);
        boolean written=false;try {if(arrayAllowed(array,index,Integer.valueOf(next))){values[index]=next;written=true;}}finally {TaskBridge.finishFieldMutation(gate,written);}
    }
    public static void longStore(Object array,int index,long value) {
        long[] values=(long[])array;long prior=values[index];long next=(long)value;
        Object gate=TaskBridge.beginArrayMutation(array,index,1);
        boolean written=false;try {if(arrayAllowed(array,index,Long.valueOf(next))){values[index]=next;written=true;}}finally {TaskBridge.finishFieldMutation(gate,written);}
    }
    public static void floatStore(Object array,int index,float value) {
        float[] values=(float[])array;float prior=values[index];float next=(float)value;
        Object gate=TaskBridge.beginArrayMutation(array,index,1);
        boolean written=false;try {if(arrayAllowed(array,index,Float.valueOf(next))){values[index]=next;written=true;}}finally {TaskBridge.finishFieldMutation(gate,written);}
    }
    public static void doubleStore(Object array,int index,double value) {
        double[] values=(double[])array;double prior=values[index];double next=(double)value;
        Object gate=TaskBridge.beginArrayMutation(array,index,1);
        boolean written=false;try {if(arrayAllowed(array,index,Double.valueOf(next))){values[index]=next;written=true;}}finally {TaskBridge.finishFieldMutation(gate,written);}
    }
    public static void charStore(Object array,int index,int value) {
        char[] values=(char[])array;char prior=values[index];char next=(char)value;
        Object gate=TaskBridge.beginArrayMutation(array,index,1);
        boolean written=false;try {if(arrayAllowed(array,index,Character.valueOf(next))){values[index]=next;written=true;}}finally {TaskBridge.finishFieldMutation(gate,written);}
    }
    public static void shortStore(Object array,int index,int value) {
        short[] values=(short[])array;short prior=values[index];short next=(short)value;
        Object gate=TaskBridge.beginArrayMutation(array,index,1);
        boolean written=false;try {if(arrayAllowed(array,index,Short.valueOf(next))){values[index]=next;written=true;}}finally {TaskBridge.finishFieldMutation(gate,written);}
    }
    public static void byteStore(Object array,int index,int value) {
        if(array instanceof boolean[] values) {boolean prior=values[index],next=(value&1)!=0;
            Object gate=TaskBridge.beginArrayMutation(array,index,1);boolean written=false;try {if(arrayAllowed(array,index,Boolean.valueOf(next))){values[index]=next;written=true;}}finally {TaskBridge.finishFieldMutation(gate,written);}}
        else {byte[] values=(byte[])array;byte prior=values[index],next=(byte)value;
            Object gate=TaskBridge.beginArrayMutation(array,index,1);boolean written=false;try {if(arrayAllowed(array,index,Byte.valueOf(next))){values[index]=next;written=true;}}finally {TaskBridge.finishFieldMutation(gate,written);}}
    }
    public static void copy(Object source,int from,Object target,int to,int count) {
        if(source==null||target==null)throw new NullPointerException();
        Class<?> left=source.getClass().getComponentType(),right=target.getClass().getComponentType();
        if(left==null||right==null||left.isPrimitive()!=right.isPrimitive()||left.isPrimitive()&&left!=right)throw new ArrayStoreException();
        int a=Array.getLength(source),z=Array.getLength(target);
        if(from<0||to<0||count<0||from>a-count||to>z-count)throw new ArrayIndexOutOfBoundsException();
        if(!known(target)) {System.arraycopy(source,from,target,to,count);return;}
        Object snapshot=Array.newInstance(left,count);System.arraycopy(source,from,snapshot,0,count);
        for(int i=0;i<count;i++)arrayStore(target,to+i,Array.get(snapshot,i));
    }
    private static void range(int length,int from,int to) {
        if(from>to)throw new IllegalArgumentException("fromIndex > toIndex");
        if(from<0||to>length)throw new ArrayIndexOutOfBoundsException();
    }
    private static boolean reorderDenied(Object array,int from,int to) {
        if(!known(array))return false;
        for(int i=from;i<to;i++)if(!arrayAllowed(array,i,null))return true;
        return false;
    }
    public static void fill(Object[] array,Object value) {fill(array,0,array.length,value);}
    public static void fill(Object[] array,int from,int to,Object value) {
        range(array.length,from,to);if(!known(array)) {Arrays.fill(array,from,to,value);return;}
        for(int i=from;i<to;i++)arrayStore(array,i,value);
    }
    public static void sort(Object[] array) {sort(array,0,array.length);}
    public static void sort(Object[] array,int from,int to) {range(array.length,from,to);if(!reorderDenied(array,from,to))Arrays.sort(array,from,to);}
    public static <T extends Comparable<? super T>> void parallelSort(T[] array) {parallelSort(array,0,array.length);}
    public static <T extends Comparable<? super T>> void parallelSort(T[] array,int from,int to) {range(array.length,from,to);if(!reorderDenied(array,from,to))Arrays.parallelSort(array,from,to);}
    public static void fill(int[] array,int value) {fill(array,0,array.length,value);}
    public static void fill(int[] array,int from,int to,int value) {
        range(array.length,from,to);if(!known(array)) {Arrays.fill(array,from,to,value);return;}
        for(int i=from;i<to;i++)intStore(array,i,value);
    }
    public static void sort(int[] array) {sort(array,0,array.length);}
    public static void sort(int[] array,int from,int to) {range(array.length,from,to);if(!reorderDenied(array,from,to))Arrays.sort(array,from,to);}
    public static void parallelSort(int[] array) {parallelSort(array,0,array.length);}
    public static void parallelSort(int[] array,int from,int to) {range(array.length,from,to);if(!reorderDenied(array,from,to))Arrays.parallelSort(array,from,to);}
    public static void fill(long[] array,long value) {fill(array,0,array.length,value);}
    public static void fill(long[] array,int from,int to,long value) {
        range(array.length,from,to);if(!known(array)) {Arrays.fill(array,from,to,value);return;}
        for(int i=from;i<to;i++)longStore(array,i,value);
    }
    public static void sort(long[] array) {sort(array,0,array.length);}
    public static void sort(long[] array,int from,int to) {range(array.length,from,to);if(!reorderDenied(array,from,to))Arrays.sort(array,from,to);}
    public static void parallelSort(long[] array) {parallelSort(array,0,array.length);}
    public static void parallelSort(long[] array,int from,int to) {range(array.length,from,to);if(!reorderDenied(array,from,to))Arrays.parallelSort(array,from,to);}
    public static void fill(float[] array,float value) {fill(array,0,array.length,value);}
    public static void fill(float[] array,int from,int to,float value) {
        range(array.length,from,to);if(!known(array)) {Arrays.fill(array,from,to,value);return;}
        for(int i=from;i<to;i++)floatStore(array,i,value);
    }
    public static void sort(float[] array) {sort(array,0,array.length);}
    public static void sort(float[] array,int from,int to) {range(array.length,from,to);if(!reorderDenied(array,from,to))Arrays.sort(array,from,to);}
    public static void parallelSort(float[] array) {parallelSort(array,0,array.length);}
    public static void parallelSort(float[] array,int from,int to) {range(array.length,from,to);if(!reorderDenied(array,from,to))Arrays.parallelSort(array,from,to);}
    public static void fill(double[] array,double value) {fill(array,0,array.length,value);}
    public static void fill(double[] array,int from,int to,double value) {
        range(array.length,from,to);if(!known(array)) {Arrays.fill(array,from,to,value);return;}
        for(int i=from;i<to;i++)doubleStore(array,i,value);
    }
    public static void sort(double[] array) {sort(array,0,array.length);}
    public static void sort(double[] array,int from,int to) {range(array.length,from,to);if(!reorderDenied(array,from,to))Arrays.sort(array,from,to);}
    public static void parallelSort(double[] array) {parallelSort(array,0,array.length);}
    public static void parallelSort(double[] array,int from,int to) {range(array.length,from,to);if(!reorderDenied(array,from,to))Arrays.parallelSort(array,from,to);}
    public static void fill(byte[] array,byte value) {fill(array,0,array.length,value);}
    public static void fill(byte[] array,int from,int to,byte value) {
        range(array.length,from,to);if(!known(array)) {Arrays.fill(array,from,to,value);return;}
        for(int i=from;i<to;i++)byteStore(array,i,value);
    }
    public static void sort(byte[] array) {sort(array,0,array.length);}
    public static void sort(byte[] array,int from,int to) {range(array.length,from,to);if(!reorderDenied(array,from,to))Arrays.sort(array,from,to);}
    public static void parallelSort(byte[] array) {parallelSort(array,0,array.length);}
    public static void parallelSort(byte[] array,int from,int to) {range(array.length,from,to);if(!reorderDenied(array,from,to))Arrays.parallelSort(array,from,to);}
    public static void fill(short[] array,short value) {fill(array,0,array.length,value);}
    public static void fill(short[] array,int from,int to,short value) {
        range(array.length,from,to);if(!known(array)) {Arrays.fill(array,from,to,value);return;}
        for(int i=from;i<to;i++)shortStore(array,i,value);
    }
    public static void sort(short[] array) {sort(array,0,array.length);}
    public static void sort(short[] array,int from,int to) {range(array.length,from,to);if(!reorderDenied(array,from,to))Arrays.sort(array,from,to);}
    public static void parallelSort(short[] array) {parallelSort(array,0,array.length);}
    public static void parallelSort(short[] array,int from,int to) {range(array.length,from,to);if(!reorderDenied(array,from,to))Arrays.parallelSort(array,from,to);}
    public static void fill(char[] array,char value) {fill(array,0,array.length,value);}
    public static void fill(char[] array,int from,int to,char value) {
        range(array.length,from,to);if(!known(array)) {Arrays.fill(array,from,to,value);return;}
        for(int i=from;i<to;i++)charStore(array,i,value);
    }
    public static void sort(char[] array) {sort(array,0,array.length);}
    public static void sort(char[] array,int from,int to) {range(array.length,from,to);if(!reorderDenied(array,from,to))Arrays.sort(array,from,to);}
    public static void parallelSort(char[] array) {parallelSort(array,0,array.length);}
    public static void parallelSort(char[] array,int from,int to) {range(array.length,from,to);if(!reorderDenied(array,from,to))Arrays.parallelSort(array,from,to);}
    public static void fill(boolean[] array,boolean value) {fill(array,0,array.length,value);}
    public static void fill(boolean[] array,int from,int to,boolean value) {
        range(array.length,from,to);if(!known(array)) {Arrays.fill(array,from,to,value);return;}
        for(int i=from;i<to;i++)byteStore(array,i,value?1:0);
    }
    public static <T> void sort(T[] array,Comparator<? super T> comparator) {sort(array,0,array.length,comparator);}
    public static <T> void sort(T[] array,int from,int to,Comparator<? super T> comparator) {range(array.length,from,to);if(!reorderDenied(array,from,to))Arrays.sort(array,from,to,comparator);}
    public static <T> void parallelSort(T[] array,Comparator<? super T> comparator) {parallelSort(array,0,array.length,comparator);}
    public static <T> void parallelSort(T[] array,int from,int to,Comparator<? super T> comparator) {range(array.length,from,to);if(!reorderDenied(array,from,to))Arrays.parallelSort(array,from,to,comparator);}
    public static <T> void setAll(T[] array,IntFunction<? extends T> function) {Objects.requireNonNull(function);for(int i=0;i<array.length;i++)arrayStore(array,i,function.apply(i));}
    public static <T> void parallelSetAll(T[] array,IntFunction<? extends T> function) {Objects.requireNonNull(function);java.util.stream.IntStream.range(0,array.length).parallel().forEach(i->arrayStore(array,i,function.apply(i)));}
    public static void setAll(int[] array,IntUnaryOperator function) {Objects.requireNonNull(function);for(int i=0;i<array.length;i++)intStore(array,i,function.applyAsInt(i));}
    public static void parallelSetAll(int[] array,IntUnaryOperator function) {Objects.requireNonNull(function);java.util.stream.IntStream.range(0,array.length).parallel().forEach(i->intStore(array,i,function.applyAsInt(i)));}
    public static void setAll(long[] array,IntToLongFunction function) {Objects.requireNonNull(function);for(int i=0;i<array.length;i++)longStore(array,i,function.applyAsLong(i));}
    public static void parallelSetAll(long[] array,IntToLongFunction function) {Objects.requireNonNull(function);java.util.stream.IntStream.range(0,array.length).parallel().forEach(i->longStore(array,i,function.applyAsLong(i)));}
    public static void setAll(double[] array,IntToDoubleFunction function) {Objects.requireNonNull(function);for(int i=0;i<array.length;i++)doubleStore(array,i,function.applyAsDouble(i));}
    public static void parallelSetAll(double[] array,IntToDoubleFunction function) {Objects.requireNonNull(function);java.util.stream.IntStream.range(0,array.length).parallel().forEach(i->doubleStore(array,i,function.applyAsDouble(i)));}
    private static final ThreadLocal<Boolean> WRAPPING=new ThreadLocal<>();
    private static final ThreadLocal<Boolean> LIST_INTERNAL=new ThreadLocal<>();
    private static Object unsafe;
    private static final Map<String,Method> READERS=new HashMap<>();
    private static synchronized Object readUnsafe(Object receiver,long offset,String kind) {
        try {
            if(unsafe==null) {Class<?> type=Class.forName("sun.misc.Unsafe",true,ClassLoader.getPlatformClassLoader());Field singleton=type.getDeclaredField("theUnsafe");singleton.setAccessible(true);unsafe=singleton.get(null);}
            Method reader=READERS.get(kind);if(reader==null) {reader=unsafe.getClass().getMethod("get"+kind,Object.class,long.class);READERS.put(kind,reader);}
            return reader.invoke(unsafe,receiver,offset);
        }catch(ReflectiveOperationException e){throw new IllegalStateException("ACTUAL_RETAINED_VALUE_UNAVAILABLE",e);}
    }
    public static Object retained(Object receiver,long offset,String kind) {return readUnsafe(receiver,offset,kind);}
    public static boolean nativeWriteAllowed(Object receiver,long offset,Object value,String operation,String kind) {
        Object proposed=value;
        if(operation.startsWith("getAndAdd")||operation.startsWith("getAndBitwise")) {
            Object old=readUnsafe(receiver,offset,kind);
            if(old instanceof Number number&&value instanceof Number operand) {
                long a=number.longValue(),z=operand.longValue();long result=operation.startsWith("getAndAdd")?a+z:operation.contains("And")&&!operation.contains("Xor")&&!operation.contains("Or")?a&z:operation.contains("Xor")?a^z:a|z;
                proposed=switch(kind) {case "Long"->Long.valueOf(result);case "Byte"->Byte.valueOf((byte)result);case "Short"->Short.valueOf((short)result);case "Float"->Float.valueOf(number.floatValue()+operand.floatValue());case "Double"->Double.valueOf(number.doubleValue()+operand.doubleValue());default->Integer.valueOf((int)result);};
            } else if(old instanceof Boolean a&&value instanceof Boolean z)proposed=operation.contains("Xor")?a^z:operation.contains("BitwiseOr")?a|z:a&z;
        }
        return TaskBridge.unsafeWriteAllowed(receiver,offset,proposed,kind);
    }
    private static boolean setterAllowed(Class<?> owner,String name,Object receiver,Object value) {
        Class<?> declaring=owner;
        while(declaring!=null)try {declaring.getDeclaredField(name);break;}catch(NoSuchFieldException absent){declaring=declaring.getSuperclass();}
        return declaring==null||TaskBridge.fieldWriteAllowed(receiver,declaring,name,value);
    }
    private static boolean staticSetterAllowed(Class<?> owner,String name,Object value) {return setterAllowed(owner,name,owner,value);}
    public static MethodHandle wrapSetter(MethodHandle original,Class<?> owner,String name) {
        if(WRAPPING.get()!=null)return original;WRAPPING.set(true);
        try {
            boolean statik=original.type().parameterCount()==1;
            MethodHandle test=MethodHandles.lookup().findStatic(BackingBridge.class,statik?"staticSetterAllowed":"setterAllowed",
                    statik?MethodType.methodType(boolean.class,Class.class,String.class,Object.class):MethodType.methodType(boolean.class,Class.class,String.class,Object.class,Object.class));
            test=MethodHandles.insertArguments(test,0,owner,name).asType(original.type().changeReturnType(boolean.class));
            return MethodHandles.guardWithTest(test,original,MethodHandles.empty(original.type()));
        }catch(ReflectiveOperationException e){throw new IllegalStateException("SETTER_WRAPPER",e);}finally {WRAPPING.remove();}
    }
    public static MethodHandle wrapSetter(MethodHandle original,Field field) {return wrapSetter(original,field.getDeclaringClass(),field.getName());}
    private static boolean arraySetterAllowed(Object array,int index,Object value) {
        int length=Array.getLength(array);if(index<0||index>=length)throw new ArrayIndexOutOfBoundsException(index);
        Class<?> component=array.getClass().getComponentType();
        if(!component.isPrimitive()&&value!=null&&!component.isInstance(value))throw new ArrayStoreException(value.getClass().getName());
        return arrayAllowed(array,index,value);
    }
    public static boolean memoryWriteAllowed(Object receiver,long offset,long length) {
        if(receiver==null||length<0||length==0)return true;
        if(!TaskBridge.controlMutationAllowed(receiver))return false;
        Policy current=policy;if(current==null)return true;
        Boolean active=POLICY_CALLBACK.get();POLICY_CALLBACK.set(true);
        try {return !(boolean)current.memory.invokeExact(receiver,offset,length);}catch(Throwable e){throw new IllegalStateException("BACKING_MEMORY_GUARD",e);}
        finally {if(active==null)POLICY_CALLBACK.remove();else POLICY_CALLBACK.set(active);}
    }
    public static MethodHandle wrapArraySetter(MethodHandle original) {
        if(WRAPPING.get()!=null)return original;WRAPPING.set(true);
        try {
            MethodHandle test=MethodHandles.lookup().findStatic(BackingBridge.class,"arraySetterAllowed",MethodType.methodType(boolean.class,Object.class,int.class,Object.class));
            return MethodHandles.guardWithTest(test.asType(original.type().changeReturnType(boolean.class)),original,MethodHandles.empty(original.type()));
        }catch(ReflectiveOperationException e){throw new IllegalStateException("ARRAY_SETTER_WRAPPER",e);}finally {WRAPPING.remove();}
    }
    private static boolean arrayWrite(Class<?> owner,String name) {
        return owner==Array.class&&Set.of("set","setBoolean","setByte","setChar","setShort","setInt","setLong","setFloat","setDouble").contains(name);
    }
    public static Object reflectArray(Method method,Object receiver,Object[] arguments)throws IllegalAccessException,InvocationTargetException {
        if(!arrayWrite(method.getDeclaringClass(),method.getName()))return UNHANDLED;
        try {
            // Identical public static signature: reflection retains argument conversion and target-exception wrapping.
            return BackingBridge.class.getMethod(method.getName(),method.getParameterTypes()).invoke(null,arguments);
        }catch(NoSuchMethodException mismatch){throw new IllegalStateException("ARRAY_REFLECTION_SIGNATURE",mismatch);}
    }
    public static MethodHandle wrapArrayMethod(MethodHandle original,Method method) {
        return wrapArrayMethod(original,method.getDeclaringClass(),method.getName());
    }
    public static MethodHandle wrapArrayMethod(MethodHandle original,Class<?> owner,String name) {
        if(!arrayWrite(owner,name)||WRAPPING.get()!=null)return original;WRAPPING.set(true);
        try {return MethodHandles.lookup().findStatic(BackingBridge.class,name,original.type());}
        catch(ReflectiveOperationException mismatch){throw new IllegalStateException("ARRAY_METHOD_SIGNATURE",mismatch);}
        finally {WRAPPING.remove();}
    }
    public static void set(Object array,int index,Object value) {
        if(!known(array)) {Array.set(array,index,value);return;}
        int length=Array.getLength(array);if(index<0||index>=length)throw new ArrayIndexOutOfBoundsException(index);
        Object check=Array.newInstance(array.getClass().getComponentType(),1);Array.set(check,0,value);Object normalized=Array.get(check,0);
        if(arrayAllowed(array,index,normalized))Array.set(array,index,normalized);
    }
    private static void typedWrite(Object array,int index,Object value,char kind) {
        switch(kind) {
            case 'Z'->Array.setBoolean(array,index,(Boolean)value);case 'B'->Array.setByte(array,index,(Byte)value);
            case 'C'->Array.setChar(array,index,(Character)value);case 'S'->Array.setShort(array,index,(Short)value);
            case 'I'->Array.setInt(array,index,(Integer)value);case 'J'->Array.setLong(array,index,(Long)value);
            case 'F'->Array.setFloat(array,index,(Float)value);case 'D'->Array.setDouble(array,index,(Double)value);
            default->throw new IllegalArgumentException("ARRAY_WRITE_KIND");
        }
    }
    private static void typedSet(Object array,int index,Object value,char kind) {
        if(!known(array)){typedWrite(array,index,value,kind);return;}
        int length=Array.getLength(array);if(index<0||index>=length)throw new ArrayIndexOutOfBoundsException(index);
        Object check=Array.newInstance(array.getClass().getComponentType(),1);typedWrite(check,0,value,kind);
        if(arrayAllowed(array,index,Array.get(check,0)))typedWrite(array,index,value,kind);
    }
    public static void setBoolean(Object array,int index,boolean value) {typedSet(array,index,value,'Z');}
    public static void setByte(Object array,int index,byte value) {typedSet(array,index,value,'B');}
    public static void setChar(Object array,int index,char value) {typedSet(array,index,value,'C');}
    public static void setShort(Object array,int index,short value) {typedSet(array,index,value,'S');}
    public static void setInt(Object array,int index,int value) {typedSet(array,index,value,'I');}
    public static void setLong(Object array,int index,long value) {typedSet(array,index,value,'J');}
    public static void setFloat(Object array,int index,float value) {typedSet(array,index,value,'F');}
    public static void setDouble(Object array,int index,double value) {typedSet(array,index,value,'D');}
    private static int version(ArrayList<?> list) {
        try {return MOD_COUNT.getInt(list);}catch(IllegalAccessException e){throw new IllegalStateException(e);}
    }
    private static void version(ArrayList<?> list,int value) {
        try {MOD_COUNT.setInt(list,value);}catch(IllegalAccessException e){throw new IllegalStateException(e);}
    }
    public static Object identityMutation(Object receiver,String method,Object[] args) {
        if(TaskBridge.controlMutationAllowed(receiver))return UNHANDLED;
        if(method.equals("put"))return ((Map<?,?>)receiver).get(args[0]);
        if(method.equals("remove")&&args.length==2)return Boolean.FALSE;
        return null;
    }
    private static final class ListViews {
        static final Field ROOT,OFFSET,SIZE,PARENT,ITR_ROOT,ITR_LAST,ITR_MOD,VIEW_ITR_ROOT,VIEW_ITR_LAST,VIEW_ITR_MOD;
        static {
            try {
                Class<?> view=Class.forName("java.util.ArrayList$SubList"),itr=Class.forName("java.util.ArrayList$Itr"),viewItr=Class.forName("java.util.ArrayList$SubList$1");
                ROOT=field(view,"root");OFFSET=field(view,"offset");SIZE=field(view,"size");PARENT=field(view,"parent");
                ITR_ROOT=field(itr,"this$0");ITR_LAST=field(itr,"lastRet");ITR_MOD=field(itr,"expectedModCount");
                VIEW_ITR_ROOT=field(viewItr,"this$0");VIEW_ITR_LAST=field(viewItr,"lastRet");VIEW_ITR_MOD=field(viewItr,"expectedModCount");
            }catch(Exception failure){throw new ExceptionInInitializerError(failure);}
        }
        private static Field field(Class<?> type,String name)throws ReflectiveOperationException {Field field=type.getDeclaredField(name);field.setAccessible(true);return field;}
    }
    public static boolean listViewRemovalDenied(Object receiver,Object value) {
        if(receiver==null||!receiver.getClass().getName().equals("java.util.ArrayList$SubList")
                ||POLICY_CALLBACK.get()!=null||LIST_INTERNAL.get()!=null)return false;
        try {
            ArrayList<?> root=(ArrayList<?>)ListViews.ROOT.get(receiver);if(!known(root))return false;
            List<?> view=(List<?>)receiver;int at=view.indexOf(value);
            return at>=0&&deny(root,view.get(at),null);
        }catch(ReflectiveOperationException failure){throw new IllegalStateException("LIST_VIEW_REMOVE_OBJECT",failure);}
    }
    /** Reject before the iterator rewinds its cursor or changes a view's size. */
    public static boolean listIteratorDenied(Object iterator,Object value,boolean add) {
        if(POLICY_CALLBACK.get()!=null||LIST_INTERNAL.get()!=null)return false;
        try {
            boolean view=iterator.getClass().getName().equals("java.util.ArrayList$SubList$1");
            Object owner=(view?ListViews.VIEW_ITR_ROOT:ListViews.ITR_ROOT).get(iterator);
            ArrayList<?> root=(ArrayList<?>)(view?ListViews.ROOT.get(owner):owner);
            if(!known(root))return false;
            Field last=view?ListViews.VIEW_ITR_LAST:ListViews.ITR_LAST;
            if((view?ListViews.VIEW_ITR_MOD:ListViews.ITR_MOD).getInt(iterator)!=version(root))return false;
            if(add)return deny(root,null,value);
            int at=last.getInt(iterator);if(at<0)return false;
            int offset=view?ListViews.OFFSET.getInt(owner):0;
            if(deny(root,root.get(offset+at),null)) {last.setInt(iterator,-1);return true;}
            return false;
        }catch(ReflectiveOperationException failure){throw new IllegalStateException("LIST_ITERATOR_BOUNDARY",failure);}
    }
    private static final class ViewProgress {
        final ArrayList<?> root;int expected;
        ViewProgress(ArrayList<?> root){this.root=root;expected=version(root);}
        void changed(int before){if(expected==before)expected=version(root);}
    }
    @SuppressWarnings({"rawtypes","unchecked"})
    public static Object listViewMutation(Object view,String method,Object[] args) {
        if(POLICY_CALLBACK.get()!=null||LIST_INTERNAL.get()!=null)return UNHANDLED;
        try {
            ArrayList root=(ArrayList)ListViews.ROOT.get(view);if(!known(root))return UNHANDLED;
            if(MOD_COUNT.getInt(view)!=version(root))throw new ConcurrentModificationException();
            int offset=ListViews.OFFSET.getInt(view),size=ListViews.SIZE.getInt(view),before=root.size();Object result;ViewProgress progress=new ViewProgress(root);
            try {switch(method) {
                case "set" -> {int at=(Integer)args[0];Objects.checkIndex(at,size);return root.set(offset+at,args[1]);}
                case "remove" -> {int at=(Integer)args[0];Objects.checkIndex(at,size);result=root.remove(offset+at);progress.changed(progress.expected);}
                case "add" -> {int at=(Integer)args[0];Objects.checkIndex(at,size+1);root.add(offset+at,args[1]);progress.changed(progress.expected);result=null;}
                case "addAll" -> {int at=args.length==2?(Integer)args[0]:size;Objects.checkIndex(at,size+1);Object[] input=((Collection)Objects.requireNonNull(args[args.length-1])).toArray();
                    if(version(root)!=progress.expected)throw new ConcurrentModificationException();
                    result=root.addAll(offset+at,Arrays.asList(input));progress.changed(progress.expected);}
                case "removeRange" -> {int from=(Integer)args[0],to=(Integer)args[1];Objects.checkFromToIndex(from,to,size);result=listMutation(root,"removeRange",new Object[]{offset+from,offset+to},progress);}
                case "removeIf" -> {result=listMutation(root,"removeIf",new Object[]{args[0],offset,offset+size},progress);}
                case "batchRemove" -> {result=listMutation(root,"batchRemove",new Object[]{args[0],args[1],offset,offset+size},progress);}
                case "replaceAll" -> {return listMutation(root,"replaceAllRange",new Object[]{args[0],offset,offset+size},progress);}
                default -> {return UNHANDLED;}
            }
            return result;
            } finally {
                int delta=root.size()-before;
                // Only our own structural writes advance this view. A callback's root mutation
                // keeps the original fail-fast version, including when its exception is not CME.
                for(Object current=version(root)==progress.expected?view:null;current!=null;current=ListViews.PARENT.get(current)) {
                    ListViews.SIZE.setInt(current,ListViews.SIZE.getInt(current)+delta);MOD_COUNT.setInt(current,version(root));
                }
            }
        }catch(ReflectiveOperationException failure){throw new IllegalStateException("LIST_VIEW_BOUNDARY",failure);}
    }
    /** The exact fastutil writer owns the receiver gate before inspecting proposals. */
    @SuppressWarnings({"rawtypes","unchecked"})
    public static Object fastCollectionMutation(Object receiver,String method,Object[] args){
        if(POLICY_CALLBACK.get()!=null||receiver==null||!known(receiver))return UNHANDLED;
        String type=receiver.getClass().getName();
        boolean listType=type.equals("it.unimi.dsi.fastutil.objects.ObjectArrayList");
        if(!listType&&!type.equals("it.unimi.dsi.fastutil.objects.ObjectOpenHashSet"))return UNHANDLED;
        if(method.equals("add")||method.equals("addOrGet")){
            Object proposed=args[args.length-1];
            if(listType&&args.length==2)Objects.checkIndex((Integer)args[0],((List)receiver).size()+1);
            if(!deny(receiver,null,proposed))return UNHANDLED;
            return method.equals("addOrGet")||args.length==2?null:Boolean.FALSE;
        }
        if(method.equals("set")){
            List list=(List)receiver;Object prior=list.get((Integer)args[0]);return deny(receiver,prior,args[1])?prior:UNHANDLED;
        }
        if(method.equals("addAll")){
            Collection input=(Collection)Objects.requireNonNull(args[args.length-1]);
            int at=listType?(Integer)args[0]:0;if(listType)Objects.checkIndex(at,((List)receiver).size()+1);
            // Capture once. Calling the input again after the guard would reopen publication.
            Object[] values=input.toArray();boolean changed=false;
            for(Object value:values)if(!deny(receiver,null,value)){
                if(listType){List list=(List)receiver;int before=list.size();list.add(at,value);if(list.size()>before){at++;changed=true;}}
                else changed|=((Collection)receiver).add(value);
            }
            return changed;
        }
        if(listType&&(method.equals("addElements")||method.equals("setElements"))){
            List list=(List)receiver;int at=(Integer)args[0],offset=(Integer)args[2],length=(Integer)args[3];
            Object[] input=(Object[])Objects.requireNonNull(args[1]);Objects.checkFromIndexSize(offset,length,input.length);
            if(method.equals("addElements"))Objects.checkIndex(at,list.size()+1);else Objects.checkFromIndexSize(at,length,list.size());
            Object[] values=Arrays.copyOfRange(input,offset,offset+length);
            for(Object value:values){
                if(method.equals("setElements")){list.set(at++,value);}
                else if(!deny(list,null,value)){int before=list.size();list.add(at,value);if(list.size()>before)at++;}
            }
            return null;
        }
        return UNHANDLED;
    }
    @SuppressWarnings({"rawtypes","unchecked"})
    public static Object listMutation(Object receiver,String method,Object[] args) {
        return listMutation(receiver,method,args,null);
    }
    @SuppressWarnings({"rawtypes","unchecked"})
    private static Object listMutation(Object receiver,String method,Object[] args,ViewProgress progress) {
        if(LIST_INTERNAL.get()!=null||POLICY_CALLBACK.get()!=null)return UNHANDLED;
        if(receiver==null||receiver.getClass()!=ArrayList.class||!known(receiver))return UNHANDLED;
        ArrayList list=(ArrayList)receiver;
        if(TaskBridge.controlled(receiver)&&!TaskBridge.controlCaller())return switch(method) {
            case "set","removeIndex"->list.get((Integer)args[0]);case "add","addAll","removeObject","removeIf","batchRemove"->Boolean.FALSE;default->null;
        };
        switch(method) {
            case "add" -> {
                Object proposed=args.length==2?args[1]:args[0];
                if(!deny(list,null,proposed))return UNHANDLED;
                if(args.length==2)Objects.checkIndex((Integer)args[0],list.size()+1);
                return args.length==1?Boolean.FALSE:null;
            }
            case "addAll" -> {
                int at=args.length==2?(Integer)args[0]:list.size();Objects.checkIndex(at,list.size()+1);
                Collection input=(Collection)Objects.requireNonNull(args[args.length-1]);Object[] values=input.toArray();
                var accepted=new ArrayList<Object>(values.length);
                for(Object value:values)if(!deny(list,null,value))accepted.add(value);
                if(accepted.isEmpty())return Boolean.FALSE;
                LIST_INTERNAL.set(true);
                try {return list.addAll(at,accepted);}
                finally {LIST_INTERNAL.remove();}
            }
            case "set" -> {
                int at=(Integer)args[0];Object prior=list.get(at);return deny(list,prior,args[1])?prior:UNHANDLED;
            }
            case "removeIndex" -> {Object prior=list.get((Integer)args[0]);return deny(list,prior,null)?prior:UNHANDLED;}
            case "fastRemove" -> {
                Object[] values=(Object[])args[0];int at=(Integer)args[1];
                return deny(list,values[at],null)?null:UNHANDLED;
            }
            case "removeObject" -> {
                int at=list.indexOf(args[0]);return at>=0&&deny(list,list.get(at),null)?Boolean.FALSE:UNHANDLED;
            }
            case "clear","removeRange" -> {
                int from=method.equals("clear")?0:(Integer)args[0],to=method.equals("clear")?list.size():(Integer)args[1];
                Objects.checkFromToIndex(from,to,list.size());
                for(int i=to-1;i>=from;i--)if(!deny(list,list.get(i),null)) {
                    int before=version(list);list.remove(i);if(progress!=null)progress.changed(before);
                }
                return null;
            }
            case "removeIf","batchRemove" -> {
                Object callback=Objects.requireNonNull(args[0]);boolean batch=method.equals("batchRemove");
                int from=batch?(Integer)args[2]:args.length==3?(Integer)args[1]:0;
                int to=batch?(Integer)args[3]:args.length==3?(Integer)args[2]:list.size();
                Objects.checkFromToIndex(from,to,list.size());boolean changed=false;int stamp=version(list);
                for(int i=from;i<to;) {
                    Object value=list.get(i);
                    boolean remove=batch?((Collection)callback).contains(value)!=(Boolean)args[1]:((Predicate)callback).test(value);
                    if(stamp!=version(list))throw new ConcurrentModificationException();
                    if(remove&&!deny(list,value,null)) {int before=version(list);list.remove(i);if(progress!=null)progress.changed(before);to--;stamp=version(list);changed=true;}else i++;
                }
                return changed;
            }
            case "replaceAllRange" -> {
                UnaryOperator callback=(UnaryOperator)Objects.requireNonNull(args[0]);int from=(Integer)args[1],to=(Integer)args[2];
                Objects.checkFromToIndex(from,to,list.size());int stamp=version(list);
                for(int i=from;i<to;i++) {
                    Object value=callback.apply(list.get(i));if(stamp!=version(list))throw new ConcurrentModificationException();list.set(i,value);
                }
                return null;
            }
            default -> {return UNHANDLED;}
        }
    }
}
