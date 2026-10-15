package dev.ronova.pro.agent;

import jdk.internal.org.objectweb.asm.ClassReader;
import jdk.internal.org.objectweb.asm.Opcodes;
import jdk.internal.org.objectweb.asm.tree.*;

/** The actual JDK Optional callback operands, before their one original invocation. */
final class EquipmentOptionalBoundary {
    private static final String OPTIONAL="java/util/Optional",BRIDGE="dev/ronova/pro/bootstrap/ControlBridge";
    private static final String FUNCTION="java/util/function/Function",CONSUMER="java/util/function/Consumer";
    private static final String PREDICATE="java/util/function/Predicate",SUPPLIER="java/util/function/Supplier";
    private EquipmentOptionalBoundary(){ }

    static byte[] transform(ClassLoader loader,String name,byte[] bytes){
        if(loader!=null||!OPTIONAL.equals(name))return null;
        ClassNode node=new ClassNode();new ClassReader(bytes).accept(node,0);
        if(!node.name.equals(OPTIONAL)||!node.superName.equals("java/lang/Object")||(node.access&Opcodes.ACC_FINAL)==0)
            throw new IllegalStateException("EQUIPMENT_OPTIONAL_CLASS_LAYOUT");
        functional(method(node,"ifPresent","(L"+CONSUMER+";)V"),CONSUMER,"accept","(Ljava/lang/Object;)V","capabilityAccept","jdk-if-present");
        MethodNode map=method(node,"map","(L"+FUNCTION+";)L"+OPTIONAL+";"),filter=method(node,"filter","(L"+PREDICATE+";)L"+OPTIONAL+";"),flatMap=method(node,"flatMap","(L"+FUNCTION+";)L"+OPTIONAL+";");
        functional(map,FUNCTION,"apply","(Ljava/lang/Object;)Ljava/lang/Object;","capabilityApply","jdk-map");
        functional(filter,PREDICATE,"test","(Ljava/lang/Object;)Z","capabilityTest","jdk-filter");
        functional(flatMap,FUNCTION,"apply","(Ljava/lang/Object;)Ljava/lang/Object;","capabilityApply","jdk-flat-map");
        functional(method(node,"orElseGet","(L"+SUPPLIER+";)Ljava/lang/Object;"),SUPPLIER,"get","()Ljava/lang/Object;","capabilitySupply","jdk-or-else-get");
        functional(method(node,"orElseThrow","(L"+SUPPLIER+";)Ljava/lang/Object;"),SUPPLIER,"get","()Ljava/lang/Object;","capabilitySupply","jdk-or-else-throw");
        derived(map,"jdk-map");derived(filter,"jdk-filter");derived(flatMap,"jdk-flat-map");
        ControlClassWriter writer=new ControlClassWriter(null,node);node.accept(writer);return writer.toByteArray();
    }
    private static MethodNode method(ClassNode node,String name,String descriptor){
        MethodNode selected=null;
        for(MethodNode method:node.methods)if(method.name.equals(name)&&method.desc.equals(descriptor)){
            if(selected!=null)throw new IllegalStateException("EQUIPMENT_OPTIONAL_METHOD_AMBIGUOUS:"+name);selected=method;
        }
        if(selected==null||(selected.access&Opcodes.ACC_PUBLIC)==0||(selected.access&(Opcodes.ACC_STATIC|Opcodes.ACC_NATIVE|Opcodes.ACC_ABSTRACT))!=0)
            throw new IllegalStateException("EQUIPMENT_OPTIONAL_METHOD_LAYOUT:"+name+descriptor);
        for(AbstractInsnNode instruction:selected.instructions)if(instruction instanceof MethodInsnNode call&&call.owner.equals(BRIDGE)&&call.name.startsWith("capability"))
            throw new IllegalStateException("EQUIPMENT_OPTIONAL_ALREADY_WOVEN:"+name);
        return selected;
    }
    private static int local(MethodNode method){
        if(method.maxLocals>=65535)throw new IllegalStateException("EQUIPMENT_OPTIONAL_LOCAL_LIMIT");return method.maxLocals++;
    }
    private static void functional(MethodNode method,String owner,String name,String descriptor,String hook,String phase){
        MethodInsnNode original=null;
        for(AbstractInsnNode instruction:method.instructions)if(instruction instanceof MethodInsnNode call&&call.getOpcode()==Opcodes.INVOKEINTERFACE
                &&call.owner.equals(owner)&&call.name.equals(name)&&call.desc.equals(descriptor)){
            if(original!=null)throw new IllegalStateException("EQUIPMENT_OPTIONAL_CALLBACK_AMBIGUOUS:"+method.name);original=call;
        }
        if(original==null)throw new IllegalStateException("EQUIPMENT_OPTIONAL_CALLBACK_MISSING:"+method.name);
        boolean supplier=descriptor.equals("()Ljava/lang/Object;");int callback=local(method),value=supplier?-1:local(method);
        InsnList before=new InsnList();
        if(!supplier)before.add(new VarInsnNode(Opcodes.ASTORE,value));before.add(new VarInsnNode(Opcodes.ASTORE,callback));
        before.add(new VarInsnNode(Opcodes.ALOAD,0));before.add(new VarInsnNode(Opcodes.ALOAD,callback));
        if(!supplier)before.add(new VarInsnNode(Opcodes.ALOAD,value));before.add(new LdcInsnNode(phase));
        method.instructions.insertBefore(original,before);
        method.instructions.set(original,new MethodInsnNode(Opcodes.INVOKESTATIC,BRIDGE,hook,
                "(Ljava/lang/Object;Ljava/lang/Object;"+(supplier?"":"Ljava/lang/Object;")+"Ljava/lang/String;)"+descriptor.substring(descriptor.indexOf(')')+1),false));
    }
    private static void derived(MethodNode method,String phase){
        int returns=0;
        for(AbstractInsnNode instruction:method.instructions.toArray())if(instruction.getOpcode()==Opcodes.ARETURN){
            InsnList observed=new InsnList();observed.add(new InsnNode(Opcodes.DUP));observed.add(new VarInsnNode(Opcodes.ALOAD,0));observed.add(new InsnNode(Opcodes.SWAP));observed.add(new LdcInsnNode(phase));
            observed.add(new MethodInsnNode(Opcodes.INVOKESTATIC,BRIDGE,"capabilityDerived","(Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/String;)V",false));method.instructions.insertBefore(instruction,observed);returns++;
        }
        if(returns==0)throw new IllegalStateException("EQUIPMENT_OPTIONAL_RETURN_MISSING:"+method.name);
    }
}
