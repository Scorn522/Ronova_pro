package dev.ronova.pro.bootstrap;

import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.ClassDefinition;
import java.lang.reflect.Field;
import java.lang.reflect.Array;
import java.lang.reflect.Method;
import java.lang.reflect.InvocationTargetException;
import java.util.*;

/** Bootstrap-visible control of our exact Instrumentation registrations. No shared agent is removed. */
public final class ControlBridge {
    private static final StackWalker WALKER=StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE);
    private static volatile Class<?> owner;
    private static volatile ClassFileTransformer[] protectedTransformers=new ClassFileTransformer[0];
    private static volatile Field transformerField;
    private static volatile Method definitionsChanged;
    private static volatile boolean installed;
    private ControlBridge() { }
    public static synchronized void install(Class<?> agent,ClassFileTransformer[] transformers) throws ReflectiveOperationException {
        Class<?> caller=WALKER.getCallerClass();
        if(caller!=agent||agent.getClassLoader()!=ClassLoader.getSystemClassLoader()
                ||!agent.getName().equals("dev.ronova.pro.agent.RecoveryAgent")||owner!=null&&owner!=agent)
            throw new SecurityException("CONTROL_INSTALLER");
        Class<?> info=Class.forName("sun.instrument.TransformerManager$TransformerInfo",false,null);
        Field field=info.getDeclaredField("mTransformer");
        if(!field.trySetAccessible())throw new IllegalAccessException("TRANSFORMER_INFO_ACCESS");
        Method changed=agent.getMethod("externalDefinitionsChanged",Class[].class);
        owner=agent;transformerField=field;definitionsChanged=changed;ClassFileTransformer[] next=transformers.clone();
        TaskBridge.controlPublication(next,new Object[]{next,changed});TaskBridge.controlPublication(next,next);protectedTransformers=next;
    }
    /** Called only after the original Instrumentation entry completed successfully. */
    public static void definitionsChanged(Object[] changes){
        Class<?> caller=WALKER.getCallerClass();
        if(caller.getClassLoader()!=null||!caller.getName().equals("sun.instrument.InstrumentationImpl"))
            throw new SecurityException("ACTUAL_INSTRUMENTATION_COMPLETION_REQUIRED");
        Class<?>[] classes;
        if(changes instanceof Class<?>[] actuals)classes=actuals.clone();
        else if(changes instanceof ClassDefinition[] definitions){
            classes=new Class<?>[definitions.length];for(int i=0;i<classes.length;i++)classes[i]=definitions[i].getDefinitionClass();
        }else throw new IllegalArgumentException("ACTUAL_DEFINITION_ARRAY_REQUIRED");
        Method callback=definitionsChanged;if(callback==null)return;
        try{callback.invoke(null,(Object)classes);}
        catch(InvocationTargetException failure){
            Throwable cause=failure.getCause();if(cause instanceof RuntimeException runtime)throw runtime;if(cause instanceof Error error)throw error;throw new IllegalStateException(cause);
        }catch(ReflectiveOperationException failure){throw new IllegalStateException("EXTERNAL_DEFINITION_COMPLETION_UNAVAILABLE",failure);}
    }
    /** Keep the original native call and its completion on the same private argument snapshot. */
    public static Object[] definitionArguments(Object[] changes){
        Class<?> caller=WALKER.getCallerClass();
        if(caller.getClassLoader()!=null||!caller.getName().equals("sun.instrument.InstrumentationImpl"))
            throw new SecurityException("ACTUAL_INSTRUMENTATION_ARGUMENTS_REQUIRED");
        if(changes==null)return null;
        if(changes instanceof Class<?>[] actuals)return actuals.clone();
        if(changes instanceof ClassDefinition[] definitions){
            ClassDefinition[] actuals=new ClassDefinition[definitions.length];
            for(int i=0;i<actuals.length;i++){
                ClassDefinition definition=definitions[i];
                if(definition!=null)actuals[i]=new ClassDefinition(definition.getDefinitionClass(),definition.getDefinitionClassFile().clone());
            }
            return actuals;
        }
        throw new IllegalArgumentException("ACTUAL_DEFINITION_ARRAY_REQUIRED");
    }
    public static boolean owns(Class<?> type) {
        Class<?> agent=owner;
        return agent!=null&&type!=null&&type.getClassLoader()==agent.getClassLoader()
                &&DefinitionBridge.module(type)==agent.getModule()&&type.getName().startsWith("dev.ronova.pro.agent.")
                &&Objects.equals(type.getProtectionDomain().getCodeSource(),agent.getProtectionDomain().getCodeSource());
    }
    public static boolean removalAllowed(ClassFileTransformer transformer) {
        for(ClassFileTransformer own:protectedTransformers)if(own==transformer)return false;
        return true;
    }
    /** Executed on the manager's real publication, under its existing monitor, before any new snapshot is visible. */
    public static Object order(Object proposed) {
        Field field=transformerField;ClassFileTransformer[] ours=protectedTransformers;
        if(field==null||ours.length==0||proposed==null)return proposed;
        int length=Array.getLength(proposed),out=0;
        Object result=Array.newInstance(proposed.getClass().getComponentType(),length);
        try {
            for(int i=0;i<length;i++) {
                Object info=Array.get(proposed,i);if(info==null)continue;
                if(removalAllowed((ClassFileTransformer)field.get(info)))Array.set(result,out++,info);
            }
            for(ClassFileTransformer own:ours)for(int i=0;i<length;i++) {
                Object info=Array.get(proposed,i);
                if(info!=null&&field.get(info)==own)Array.set(result,out++,info);
            }
        }catch(IllegalAccessException impossible){throw new IllegalStateException("CONTROL_ORDER_ACCESS",impossible);}
        if(out!=length)throw new IllegalStateException("CONTROL_ORDER_LAYOUT");
        var controlled=new ArrayList<Object>();
        for(int i=0;i<length;i++)try {
            Object entry=Array.get(result,i);if(!removalAllowed((ClassFileTransformer)field.get(entry)))controlled.add(entry);
        }catch(IllegalAccessException impossible){throw new IllegalStateException(impossible);}
        if(!controlled.isEmpty())TaskBridge.controlPublication(result,controlled.toArray());
        return result;
    }
    public static void installed() {
        if(!owns(WALKER.getCallerClass()))throw new SecurityException("CONTROL_INSTALLER");
        installed=true;
    }
    public static boolean ready(){return installed;}
}
