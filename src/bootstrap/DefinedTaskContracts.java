package dev.ronova.pro.bootstrap;

import java.lang.invoke.MethodType;
import java.lang.reflect.Modifier;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.*;

/** Actual JDK callback contracts; a matching method name alone does not make a receiver a task. */
final class DefinedTaskContracts {
    private static final Class<?>[] TYPES={Runnable.class,Callable.class,ThreadFactory.class,Executor.class,
            ForkJoinPool.ForkJoinWorkerThreadFactory.class,
            Supplier.class,BooleanSupplier.class,IntSupplier.class,LongSupplier.class,DoubleSupplier.class,
            Consumer.class,BiConsumer.class,IntConsumer.class,LongConsumer.class,DoubleConsumer.class,
            ObjIntConsumer.class,ObjLongConsumer.class,ObjDoubleConsumer.class,
            Function.class,BiFunction.class,IntFunction.class,LongFunction.class,DoubleFunction.class,
            ToIntFunction.class,ToLongFunction.class,ToDoubleFunction.class,
            ToIntBiFunction.class,ToLongBiFunction.class,ToDoubleBiFunction.class,
            IntToLongFunction.class,IntToDoubleFunction.class,LongToIntFunction.class,LongToDoubleFunction.class,
            DoubleToIntFunction.class,DoubleToLongFunction.class,
            Predicate.class,BiPredicate.class,IntPredicate.class,LongPredicate.class,DoublePredicate.class,
            IntUnaryOperator.class,LongUnaryOperator.class,DoubleUnaryOperator.class,
            IntBinaryOperator.class,LongBinaryOperator.class,DoubleBinaryOperator.class};
    private static final Map<String,List<Class<?>>> METHODS=methods();
    private DefinedTaskContracts(){}
    private static Map<String,List<Class<?>>> methods(){
        Map<String,List<Class<?>>> entries=new LinkedHashMap<>();
        for(Class<?> type:TYPES)for(var method:type.getMethods())if(Modifier.isAbstract(method.getModifiers())&&!Modifier.isStatic(method.getModifiers())){
            String selector=method.getName()+MethodType.methodType(method.getReturnType(),method.getParameterTypes()).descriptorString();
            entries.computeIfAbsent(selector,ignored->new ArrayList<>()).add(type);
        }
        entries.computeIfAbsent("exec()Z",ignored->new ArrayList<>()).add(ForkJoinTask.class);
        Map<String,List<Class<?>>> result=new LinkedHashMap<>();for(var entry:entries.entrySet())result.put(entry.getKey(),List.copyOf(entry.getValue()));
        return Collections.unmodifiableMap(result);
    }
    static String[] selectors(){return METHODS.keySet().toArray(String[]::new);}
    static boolean matches(Object receiver,String selector){
        List<Class<?>> contracts=METHODS.get(selector);if(contracts==null)return false;
        for(Class<?> contract:contracts)if(contract.isInstance(receiver))return true;return false;
    }
    /** Exact original interface instructions, rather than a name supplied by the caller. */
    static String callbackContract(int opcode,String owner,String name,String descriptor){
        if(opcode!=185)return null;
        return switch(owner){
            case "java/lang/Runnable"->name.equals("run")&&descriptor.equals("()V")?"runnable":null;
            case "java/util/concurrent/Callable"->name.equals("call")&&descriptor.equals("()Ljava/lang/Object;")?"callable":null;
            case "java/util/function/Supplier"->name.equals("get")&&descriptor.equals("()Ljava/lang/Object;")?"supplier":null;
            case "java/util/function/Function"->name.equals("apply")&&descriptor.equals("(Ljava/lang/Object;)Ljava/lang/Object;")?"function":null;
            case "java/util/function/Consumer"->name.equals("accept")&&descriptor.equals("(Ljava/lang/Object;)V")?"consumer":null;
            case "java/util/function/BiFunction"->name.equals("apply")&&descriptor.equals("(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;")?"bifunction":null;
            case "java/util/function/BiConsumer"->name.equals("accept")&&descriptor.equals("(Ljava/lang/Object;Ljava/lang/Object;)V")?"biconsumer":null;
            case "java/util/function/Predicate"->name.equals("test")&&descriptor.equals("(Ljava/lang/Object;)Z")?"predicate":null;
            default->null;
        };
    }
    static boolean callbackReceiver(Object receiver,String contract){
        return receiver!=null&&switch(contract){
            case "runnable"->receiver instanceof Runnable;
            case "callable"->receiver instanceof Callable<?>;
            case "supplier"->receiver instanceof Supplier<?>;
            case "function"->receiver instanceof Function<?,?>;
            case "consumer"->receiver instanceof Consumer<?>;
            case "bifunction"->receiver instanceof BiFunction<?,?,?>;
            case "biconsumer"->receiver instanceof BiConsumer<?,?>;
            case "predicate"->receiver instanceof Predicate<?>;
            default->false;
        };
    }
    static void publish(){ControlBridge.controlPublication(METHODS,new Object[]{TYPES,METHODS});}
}
