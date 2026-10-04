package dev.ronova.pro.agent;

import java.lang.reflect.Modifier;
import java.util.*;
import jdk.internal.org.objectweb.asm.*;
import jdk.internal.org.objectweb.asm.tree.*;
import jdk.internal.org.objectweb.asm.tree.analysis.*;

/** Keeps exact construction types separate from declared reference types in the original business graph. */
final class ExternalReceiverFlow {
    record Receiver(Class<?> actual,boolean self){
        public boolean equals(Object other){return other instanceof Receiver receiver&&actual==receiver.actual&&self==receiver.self;}
        public int hashCode(){return Objects.hashCode(actual)*31+Boolean.hashCode(self);}
    }
    /** Reuses only the ASM layout within the current graph batch, never a source verdict. */
    static final class Layout {
        final ClassLoader loader;final Class<?> actual;final Object image;
        final Map<String,Class<?>> types;final Map<Class<?>,Integer> modifiers;
        final Map<MethodInsnNode,Set<Receiver>> receivers;
        Layout(ExactTypes interpreter,ExternalCodeFlow.Image image,Map<MethodInsnNode,Set<Receiver>> receivers){
            this.loader=interpreter.loader;this.actual=interpreter.actual;this.image=image.identity();
            this.types=Collections.unmodifiableMap(new HashMap<>(interpreter.loaded));
            Map<Class<?>,Integer> modifiers=new IdentityHashMap<>();
            if(actual!=null)modifiers.put(actual,actual.getModifiers());
            for(Class<?> type:types.values())if(type!=null)modifiers.put(type,type.getModifiers());
            this.modifiers=Collections.unmodifiableMap(modifiers);this.receivers=Map.copyOf(receivers);
            List<Object> controls=new ArrayList<>(Arrays.asList(this,this.types,this.modifiers,this.receivers));
            for(Set<Receiver> values:this.receivers.values()){controls.add(values);controls.addAll(values);}
            ControlImages.protect(controls.toArray());
        }
        boolean matches(ClassLoader loader,Class<?> actual,ExternalCodeFlow.Image image){
            if(this.loader!=loader||this.actual!=actual||this.image!=image.identity())return false;
            // Include unresolved names: loading a formerly absent class can
            // turn an unknown receiver into an exact one in this very batch.
            for(var type:types.entrySet())if(ExternalCodeDefinitions.initiated(loader,type.getKey())!=type.getValue())return false;
            for(var type:modifiers.entrySet())if(type.getKey().getModifiers()!=type.getValue())return false;
            return true;
        }
    }
    private static final class ExactValue implements Value {
        final BasicValue value;final Set<Receiver> receivers;final boolean unknown;
        ExactValue(BasicValue value,Set<Receiver> receivers,boolean unknown){this.value=value;this.receivers=Set.copyOf(receivers);this.unknown=unknown;}
        public int getSize(){return value.getSize();}
        public boolean equals(Object other){return other instanceof ExactValue exact&&value.equals(exact.value)&&receivers.equals(exact.receivers)&&unknown==exact.unknown;}
        public int hashCode(){return Objects.hash(value,receivers,unknown);}
    }
    private static final class ExactTypes extends Interpreter<ExactValue> {
        final BasicInterpreter types=new BasicInterpreter();final ClassLoader loader;final Class<?> actual;final ClassNode image;
        final Map<String,Class<?>> loaded=new HashMap<>();
        ExactTypes(ClassLoader loader,Class<?> actual,ClassNode image){super(Opcodes.ASM8);this.loader=loader;this.actual=actual;this.image=image;}
        private Class<?> type(Type type){
            if(type.getSort()==Type.ARRAY){Class<?> element=type(type.getElementType());if(element==null)return null;for(int i=0;i<type.getDimensions();i++)element=element.arrayType();return element;}
            if(type.getSort()==Type.OBJECT){
                String name=type.getInternalName();if(name.equals(image.name))return actual;
                if(!loaded.containsKey(name))loaded.put(name,ExternalCodeDefinitions.initiated(loader,name));return loaded.get(name);
            }
            return switch(type.getSort()){case Type.BOOLEAN->boolean.class;case Type.BYTE->byte.class;case Type.CHAR->char.class;case Type.SHORT->short.class;
                case Type.INT->int.class;case Type.FLOAT->float.class;case Type.LONG->long.class;case Type.DOUBLE->double.class;default->null;};
        }
        private static boolean fixed(Class<?> type){
            while(type.isArray())type=type.getComponentType();return type.isPrimitive()||Modifier.isFinal(type.getModifiers());
        }
        private ExactValue known(BasicValue value,Class<?> type){return new ExactValue(value,Set.of(new Receiver(type,false)),false);}
        private ExactValue reference(BasicValue value,Type declared,boolean allocation){
            if(value==null)return null;if(declared==null||declared.getSort()!=Type.OBJECT&&declared.getSort()!=Type.ARRAY)return new ExactValue(value,Set.of(),false);
            if(declared.getSort()==Type.OBJECT&&declared.getInternalName().equals(image.name)&&actual==null
                    &&(allocation||(image.access&Opcodes.ACC_FINAL)!=0))return allocation&&(image.access&(Opcodes.ACC_ABSTRACT|Opcodes.ACC_INTERFACE))!=0
                            ?ordinary(value):new ExactValue(value,Set.of(new Receiver(null,true)),false);
            Class<?> type=type(declared);
            if(allocation&&type!=null&&!type.isArray()&&(type.isInterface()||Modifier.isAbstract(type.getModifiers())))return ordinary(value);
            return type!=null&&(allocation||fixed(type))?known(value,type):new ExactValue(value,Set.of(),true);
        }
        private ExactValue ordinary(BasicValue value){return value==null?null:new ExactValue(value,Set.of(),value.isReference());}
        public ExactValue newValue(Type type){return reference(types.newValue(type),type,false);}
        public ExactValue newOperation(AbstractInsnNode instruction)throws AnalyzerException {
            BasicValue value=types.newOperation(instruction);
            if(instruction.getOpcode()==Opcodes.ACONST_NULL)return new ExactValue(value,Set.of(),false);
            if(instruction instanceof TypeInsnNode allocation&&allocation.getOpcode()==Opcodes.NEW)return reference(value,Type.getObjectType(allocation.desc),true);
            if(instruction instanceof FieldInsnNode field&&field.getOpcode()==Opcodes.GETSTATIC)return reference(value,Type.getType(field.desc),false);
            if(instruction instanceof LdcInsnNode constant){
                if(constant.cst instanceof String)return known(value,String.class);
                if(constant.cst instanceof Type type)return known(value,type.getSort()==Type.METHOD?java.lang.invoke.MethodType.class:Class.class);
                if(constant.cst instanceof ConstantDynamic dynamic)return reference(value,Type.getType(dynamic.getDescriptor()),false);
            }
            return ordinary(value);
        }
        public ExactValue copyOperation(AbstractInsnNode instruction,ExactValue value)throws AnalyzerException {
            BasicValue copied=types.copyOperation(instruction,value.value);
            return value.value.equals(copied)?value:new ExactValue(copied,value.receivers,value.unknown);
        }
        public ExactValue unaryOperation(AbstractInsnNode instruction,ExactValue input)throws AnalyzerException {
            BasicValue value=types.unaryOperation(instruction,input.value);if(value==null)return null;
            if(instruction instanceof FieldInsnNode field&&field.getOpcode()==Opcodes.GETFIELD)return reference(value,Type.getType(field.desc),false);
            if(instruction instanceof TypeInsnNode cast&&cast.getOpcode()==Opcodes.CHECKCAST){
                if(!input.unknown)return new ExactValue(value,input.receivers,false);
                return reference(value,Type.getObjectType(cast.desc),false);
            }
            if(instruction.getOpcode()==Opcodes.NEWARRAY){
                Class<?> element=switch(((IntInsnNode)instruction).operand){case Opcodes.T_BOOLEAN->boolean.class;case Opcodes.T_CHAR->char.class;case Opcodes.T_FLOAT->float.class;
                    case Opcodes.T_DOUBLE->double.class;case Opcodes.T_BYTE->byte.class;case Opcodes.T_SHORT->short.class;case Opcodes.T_INT->int.class;case Opcodes.T_LONG->long.class;default->null;};
                return element==null?ordinary(value):known(value,element.arrayType());
            }
            if(instruction instanceof TypeInsnNode array&&array.getOpcode()==Opcodes.ANEWARRAY){
                Class<?> element=type(Type.getObjectType(array.desc));return element==null?ordinary(value):known(value,element.arrayType());
            }
            return ordinary(value);
        }
        public ExactValue binaryOperation(AbstractInsnNode instruction,ExactValue left,ExactValue right)throws AnalyzerException {
            BasicValue value=types.binaryOperation(instruction,left.value,right.value);if(value==null)return null;
            if(instruction.getOpcode()==Opcodes.AALOAD&&!left.unknown&&!left.receivers.isEmpty()){
                Set<Receiver> elements=new HashSet<>();boolean unknown=false;
                for(Receiver receiver:left.receivers){Class<?> type=receiver.actual();
                    if(type==null||!type.isArray()||!fixed(type.getComponentType()))unknown=true;
                    else elements.add(new Receiver(type.getComponentType(),false));
                }
                return new ExactValue(value,elements,unknown);
            }
            return ordinary(value);
        }
        public ExactValue ternaryOperation(AbstractInsnNode instruction,ExactValue first,ExactValue second,ExactValue third)throws AnalyzerException {
            return ordinary(types.ternaryOperation(instruction,first.value,second.value,third.value));
        }
        public ExactValue naryOperation(AbstractInsnNode instruction,List<? extends ExactValue> inputs)throws AnalyzerException {
            List<BasicValue> values=new ArrayList<>(inputs.size());for(ExactValue input:inputs)values.add(input.value);
            BasicValue value=types.naryOperation(instruction,values);if(value==null)return null;
            if(instruction instanceof MultiANewArrayInsnNode allocation)return reference(value,Type.getType(allocation.desc),true);
            String descriptor=instruction instanceof MethodInsnNode call?call.desc:((InvokeDynamicInsnNode)instruction).desc;
            return reference(value,Type.getReturnType(descriptor),false);
        }
        public void returnOperation(AbstractInsnNode instruction,ExactValue value,ExactValue expected)throws AnalyzerException {types.returnOperation(instruction,value.value,expected.value);}
        public ExactValue merge(ExactValue first,ExactValue second){
            if(first==second||first.equals(second))return first;
            // Both inputs are frozen; subset merges already have the exact union.
            Set<Receiver> receivers;
            if(first.receivers.containsAll(second.receivers))receivers=first.receivers;
            else if(second.receivers.containsAll(first.receivers))receivers=second.receivers;
            else {receivers=new HashSet<>(first.receivers);receivers.addAll(second.receivers);}
            BasicValue value=types.merge(first.value,second.value);boolean unknown=first.unknown||second.unknown;
            if(first.value.equals(value)&&first.receivers.equals(receivers)&&first.unknown==unknown)return first;
            if(second.value.equals(value)&&second.receivers.equals(receivers)&&second.unknown==unknown)return second;
            return new ExactValue(value,receivers,unknown);
        }
    }
    private ExternalReceiverFlow(){}
    static Map<MethodInsnNode,Set<Receiver>> calls(ClassLoader loader,Class<?> actual,ExternalCodeFlow.Image image,MethodNode method,Map<MethodNode,Layout> layouts){
        if(!image.rows().containsKey(method.name+method.desc)||(method.access&(Opcodes.ACC_NATIVE|Opcodes.ACC_ABSTRACT))!=0)return Map.of();
        Layout prior=layouts.get(method);if(prior!=null&&prior.matches(loader,actual,image))return prior.receivers;
        AbstractInsnNode[] code=method.instructions.toArray();boolean needsReceiver=false;
        for(AbstractInsnNode instruction:code)if(instruction instanceof MethodInsnNode call&&ExternalCodeFlow.returnsValue(call)
                &&(call.getOpcode()==Opcodes.INVOKEVIRTUAL||call.getOpcode()==Opcodes.INVOKEINTERFACE)){needsReceiver=true;break;}
        if(!needsReceiver)return Map.of();
        Map<MethodInsnNode,Set<Receiver>> receivers=new IdentityHashMap<>();
        try{
            ExactTypes interpreter=new ExactTypes(loader,actual,image.node());
            Frame<ExactValue>[] frames=new Analyzer<>(interpreter).analyze(image.node().name,method);
            for(int i=0;i<code.length;i++)if(code[i] instanceof MethodInsnNode call&&frames[i]!=null&&ExternalCodeFlow.returnsValue(call)
                    &&(call.getOpcode()==Opcodes.INVOKEVIRTUAL||call.getOpcode()==Opcodes.INVOKEINTERFACE)){
                Frame<ExactValue> frame=frames[i];int receiver=frame.getStackSize()-Type.getArgumentTypes(call.desc).length-1;
                ExactValue value=frame.getStack(receiver);if(!value.unknown&&!value.receivers.isEmpty())receivers.put(call,value.receivers);
            }
            Layout layout=new Layout(interpreter,image,receivers);layouts.put(method,layout);return layout.receivers;
        }catch(AnalyzerException|RuntimeException unavailable){
            Set<Module> affected=Collections.newSetFromMap(new IdentityHashMap<>());for(Module[] row:image.rows().get(method.name+method.desc))Collections.addAll(affected,row);
            if(actual!=null){Module owner=RecoveryAgent.logicalModule(actual);if(RecoveryAgent.producerModule(owner))affected.add(owner);}
            ExternalCodeImages.failed(affected.toArray(Module[]::new),image.node().name+'#'+method.name+method.desc+":EXTERNAL_RECEIVER_LAYOUT_UNAVAILABLE");
        }
        return receivers;
    }
}
