package dev.ronova.pro.agent;

import java.util.*;
import jdk.internal.org.objectweb.asm.*;
import jdk.internal.org.objectweb.asm.tree.*;

/** Original java.base composition bodies, selected by their real factory's metafactory handle. */
final class EquipmentFunctionBoundary {
    private static final String PREFIX="java/util/function/",BRIDGE="dev/ronova/pro/bootstrap/ControlBridge";
    private static final String META="(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;Ljava/lang/invoke/MethodType;Ljava/lang/invoke/MethodHandle;Ljava/lang/invoke/MethodType;)Ljava/lang/invoke/CallSite;";
    private static final String HOOK="(Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/String;)Ljava/lang/Object;";
    private EquipmentFunctionBoundary(){ }

    static boolean target(String name){return (PREFIX+"Function").equals(name)||(PREFIX+"Consumer").equals(name)||(PREFIX+"Predicate").equals(name)||(PREFIX+"Supplier").equals(name);}
    static byte[] transform(ClassLoader loader,String name,byte[] bytes){
        if(loader!=null||!target(name))return null;
        ClassNode node=new ClassNode();new ClassReader(bytes).accept(node,0);
        if(!name.equals(node.name)||!"java/lang/Object".equals(node.superName)||(node.access&(Opcodes.ACC_PUBLIC|Opcodes.ACC_INTERFACE|Opcodes.ACC_ABSTRACT))!=(Opcodes.ACC_PUBLIC|Opcodes.ACC_INTERFACE|Opcodes.ACC_ABSTRACT)||!node.interfaces.isEmpty())
            throw unresolved("INTERFACE_LAYOUT:"+name);
        if(name.equals(PREFIX+"Supplier")){
            // This JDK's Supplier declares only abstract get; it has no natural
            // combination factory or Java callback body to instrument.
            MethodNode get=method(node,"get","()Ljava/lang/Object;");
            if((get.access&(Opcodes.ACC_PUBLIC|Opcodes.ACC_ABSTRACT))!=(Opcodes.ACC_PUBLIC|Opcodes.ACC_ABSTRACT)||(get.access&Opcodes.ACC_STATIC)!=0||node.methods.size()!=1)
                throw unresolved("SUPPLIER_LAYOUT");return null;
        }
        if(name.equals(PREFIX+"Function")){
            compose(node,"compose","lambda$compose$0","function",true,2,new int[]{Opcodes.ALOAD,Opcodes.ALOAD,Opcodes.ALOAD,Opcodes.INVOKEINTERFACE,Opcodes.INVOKEINTERFACE,Opcodes.ARETURN},new int[]{0,1,2},new int[0]);
            compose(node,"andThen","lambda$andThen$1","function",true,2,new int[]{Opcodes.ALOAD,Opcodes.ALOAD,Opcodes.ALOAD,Opcodes.INVOKEINTERFACE,Opcodes.INVOKEINTERFACE,Opcodes.ARETURN},new int[]{1,0,2},new int[0]);
        }else if(name.equals(PREFIX+"Consumer")){
            compose(node,"andThen","lambda$andThen$0","consumer",true,2,new int[]{Opcodes.ALOAD,Opcodes.ALOAD,Opcodes.INVOKEINTERFACE,Opcodes.ALOAD,Opcodes.ALOAD,Opcodes.INVOKEINTERFACE,Opcodes.RETURN},new int[]{0,2,1,2},new int[0]);
        }else {
            compose(node,"and","lambda$and$0","predicate",true,2,new int[]{Opcodes.ALOAD,Opcodes.ALOAD,Opcodes.INVOKEINTERFACE,Opcodes.IFEQ,Opcodes.ALOAD,Opcodes.ALOAD,Opcodes.INVOKEINTERFACE,Opcodes.IFEQ,Opcodes.ICONST_1,Opcodes.GOTO,Opcodes.ICONST_0,Opcodes.IRETURN},new int[]{0,2,1,2},new int[]{10,10,11});
            compose(node,"or","lambda$or$2","predicate",true,2,new int[]{Opcodes.ALOAD,Opcodes.ALOAD,Opcodes.INVOKEINTERFACE,Opcodes.IFNE,Opcodes.ALOAD,Opcodes.ALOAD,Opcodes.INVOKEINTERFACE,Opcodes.IFEQ,Opcodes.ICONST_1,Opcodes.GOTO,Opcodes.ICONST_0,Opcodes.IRETURN},new int[]{0,2,1,2},new int[]{8,10,11});
            compose(node,"negate","lambda$negate$1","predicate",false,1,new int[]{Opcodes.ALOAD,Opcodes.ALOAD,Opcodes.INVOKEINTERFACE,Opcodes.IFNE,Opcodes.ICONST_1,Opcodes.GOTO,Opcodes.ICONST_0,Opcodes.IRETURN},new int[]{0,1},new int[]{6,7});
        }
        ControlClassWriter writer=new ControlClassWriter(null,node);node.accept(writer);return writer.toByteArray();
    }
    private static MethodNode method(ClassNode node,String name,String descriptor){
        MethodNode found=null;for(MethodNode method:node.methods)if(method.name.equals(name)&&method.desc.equals(descriptor)){if(found!=null)throw unresolved("AMBIGUOUS_METHOD:"+name);found=method;}
        if(found==null)throw unresolved("METHOD:"+name+descriptor);return found;
    }
    private static List<AbstractInsnNode> code(MethodNode method){
        List<AbstractInsnNode> result=new ArrayList<>();for(AbstractInsnNode instruction:method.instructions)if(instruction.getOpcode()>=0)result.add(instruction);return result;
    }
    private static void shape(MethodNode method,int[] opcodes,int[] locals,int[] targets){
        List<AbstractInsnNode> actual=code(method);if(actual.size()!=opcodes.length||!method.tryCatchBlocks.isEmpty())throw unresolved("BODY_LAYOUT:"+method.name);
        int variable=0,jump=0;for(int at=0;at<opcodes.length;at++){
            AbstractInsnNode instruction=actual.get(at);if(instruction.getOpcode()!=opcodes[at])throw unresolved("BODY_OPCODE:"+method.name+":"+at);
            if(instruction instanceof VarInsnNode local&&(variable>=locals.length||local.var!=locals[variable++]))throw unresolved("BODY_LOCAL:"+method.name+":"+at);
            if(instruction instanceof JumpInsnNode branch){AbstractInsnNode destination=branch.label;while(destination!=null&&destination.getOpcode()<0)destination=destination.getNext();
                if(jump>=targets.length||actual.indexOf(destination)!=targets[jump++])throw unresolved("BODY_BRANCH:"+method.name+":"+at);}
        }
        if(variable!=locals.length||jump!=targets.length)throw unresolved("BODY_OPERANDS:"+method.name);
    }
    private static void compose(ClassNode node,String factoryName,String helperName,String contract,boolean argument,int count,int[] shape,int[] locals,int[] targets){
        String callbackName=contract.equals("function")?"apply":contract.equals("consumer")?"accept":"test";
        String result=contract.equals("function")?"Ljava/lang/Object;":contract.equals("consumer")?"V":"Z";
        String callbackDescriptor="(Ljava/lang/Object;)"+result,parameter=argument?"L"+node.name+";":"";
        MethodNode factory=method(node,factoryName,"("+parameter+")L"+node.name+";");
        if((factory.access&Opcodes.ACC_PUBLIC)==0||(factory.access&(Opcodes.ACC_STATIC|Opcodes.ACC_ABSTRACT|Opcodes.ACC_NATIVE))!=0)throw unresolved("FACTORY_ACCESS:"+factoryName);
        List<AbstractInsnNode> factoryCode=code(factory);int[] factoryOps=argument?new int[]{Opcodes.ALOAD,Opcodes.INVOKESTATIC,Opcodes.POP,Opcodes.ALOAD,Opcodes.ALOAD,Opcodes.INVOKEDYNAMIC,Opcodes.ARETURN}:new int[]{Opcodes.ALOAD,Opcodes.INVOKEDYNAMIC,Opcodes.ARETURN};
        shape(factory,factoryOps,argument?new int[]{1,0,1}:new int[]{0},new int[0]);
        if(argument){MethodInsnNode nonnull=(MethodInsnNode)factoryCode.get(1);if(!nonnull.owner.equals("java/util/Objects")||!nonnull.name.equals("requireNonNull")||!nonnull.desc.equals("(Ljava/lang/Object;)Ljava/lang/Object;")||nonnull.itf)throw unresolved("FACTORY_NULL_CHECK:"+factoryName);}
        InvokeDynamicInsnNode indy=(InvokeDynamicInsnNode)factoryCode.get(argument?5:1);Handle bootstrap=indy.bsm;
        if(!indy.name.equals(callbackName)||!indy.desc.equals("(L"+node.name+";"+parameter+")L"+node.name+";")
                ||bootstrap.getTag()!=Opcodes.H_INVOKESTATIC||bootstrap.isInterface()||!bootstrap.getOwner().equals("java/lang/invoke/LambdaMetafactory")||!bootstrap.getName().equals("metafactory")||!bootstrap.getDesc().equals(META)
                ||indy.bsmArgs.length!=3||!(indy.bsmArgs[0] instanceof Type sam)||!sam.getDescriptor().equals(callbackDescriptor)||!(indy.bsmArgs[1] instanceof Handle implementation)
                ||!(indy.bsmArgs[2] instanceof Type instantiated)||!instantiated.getDescriptor().equals(callbackDescriptor))throw unresolved("FACTORY_METAFACTORY:"+factoryName);
        String helperDescriptor="("+parameter+"Ljava/lang/Object;)"+result;
        if(implementation.getTag()!=Opcodes.H_INVOKEINTERFACE||!implementation.isInterface()||!implementation.getOwner().equals(node.name)||!implementation.getName().equals(helperName)||!implementation.getDesc().equals(helperDescriptor))
            throw unresolved("FACTORY_IMPLEMENTATION:"+factoryName);
        MethodNode helper=method(node,implementation.getName(),implementation.getDesc());
        if((helper.access&(Opcodes.ACC_PRIVATE|Opcodes.ACC_SYNTHETIC))!=(Opcodes.ACC_PRIVATE|Opcodes.ACC_SYNTHETIC)||(helper.access&(Opcodes.ACC_STATIC|Opcodes.ACC_ABSTRACT|Opcodes.ACC_NATIVE))!=0)throw unresolved("HELPER_ACCESS:"+helper.name);
        shape(helper,shape,locals,targets);List<MethodInsnNode> calls=new ArrayList<>();
        for(AbstractInsnNode instruction:helper.instructions)if(instruction instanceof MethodInsnNode call){
            if(call.getOpcode()!=Opcodes.INVOKEINTERFACE||!call.itf||!call.owner.equals(node.name)||!call.name.equals(callbackName)||!call.desc.equals(callbackDescriptor))throw unresolved("HELPER_CALLBACK:"+helper.name);calls.add(call);
        }
        if(calls.size()!=count)throw unresolved("HELPER_CALLBACK_COUNT:"+helper.name);
        for(MethodInsnNode call:calls){
            // The original operand stack holds callback,value. Compose's second
            // callback consumes the first one's real result; no callback is replayed.
            helper.instructions.insertBefore(call,new LdcInsnNode(contract));
            MethodInsnNode hook=new MethodInsnNode(Opcodes.INVOKESTATIC,BRIDGE,"capabilityComposed",HOOK,false);helper.instructions.set(call,hook);
            if(result.equals("V"))helper.instructions.insert(hook,new InsnNode(Opcodes.POP));
            else if(result.equals("Z")){InsnList unbox=new InsnList();unbox.add(new TypeInsnNode(Opcodes.CHECKCAST,"java/lang/Boolean"));unbox.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL,"java/lang/Boolean","booleanValue","()Z",false));helper.instructions.insert(hook,unbox);}
        }
    }
    private static IllegalStateException unresolved(String detail){return new IllegalStateException("EQUIPMENT_FUNCTION_COMPOSITION_LAYOUT:"+detail);}
}
