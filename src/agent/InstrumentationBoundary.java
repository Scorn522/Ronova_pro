package dev.ronova.pro.agent;

import jdk.internal.org.objectweb.asm.*;
import jdk.internal.org.objectweb.asm.tree.*;

/** Guards real transformer removal and array publication, rather than checking an install flag on a later tick. */
final class InstrumentationBoundary {
    static final String MANAGER="sun/instrument/TransformerManager";
    static final String API="sun/instrument/InstrumentationImpl";
    private static final String BRIDGE="dev/ronova/pro/bootstrap/ControlBridge";
    static byte[] transform(ClassLoader loader,String name,byte[] bytes) {
        if(loader!=null||!MANAGER.equals(name)&&!API.equals(name))return null;
        ClassNode node=new ClassNode();new ClassReader(bytes).accept(node,0);
        int removal=0,publications=0;
        for(MethodNode method:node.methods) {
            if(name.equals(API)&&(method.name.equals("retransformClasses")&&method.desc.equals("([Ljava/lang/Class;)V")
                    ||method.name.equals("redefineClasses")&&method.desc.equals("([Ljava/lang/instrument/ClassDefinition;)V"))){
                InsnList arguments=new InsnList();arguments.add(new VarInsnNode(Opcodes.ALOAD,1));
                arguments.add(new MethodInsnNode(Opcodes.INVOKESTATIC,BRIDGE,"definitionArguments","([Ljava/lang/Object;)[Ljava/lang/Object;",false));
                arguments.add(new TypeInsnNode(Opcodes.CHECKCAST,Type.getArgumentTypes(method.desc)[0].getDescriptor()));
                arguments.add(new VarInsnNode(Opcodes.ASTORE,1));method.instructions.insert(arguments);
                for(AbstractInsnNode instruction:method.instructions.toArray())if(instruction.getOpcode()==Opcodes.RETURN){
                    InsnList done=new InsnList();done.add(new VarInsnNode(Opcodes.ALOAD,1));
                    done.add(new MethodInsnNode(Opcodes.INVOKESTATIC,BRIDGE,"definitionsChanged","([Ljava/lang/Object;)V",false));method.instructions.insertBefore(instruction,done);
                }
            }
            if(method.name.equals("removeTransformer")&&method.desc.equals("(Ljava/lang/instrument/ClassFileTransformer;)Z")) {
                InsnList code=new InsnList();LabelNode allowed=new LabelNode();
                code.add(new VarInsnNode(Opcodes.ALOAD,1));
                code.add(new MethodInsnNode(Opcodes.INVOKESTATIC,BRIDGE,"removalAllowed","(Ljava/lang/instrument/ClassFileTransformer;)Z",false));
                code.add(new JumpInsnNode(Opcodes.IFNE,allowed));code.add(new InsnNode(Opcodes.ICONST_0));
                code.add(new InsnNode(Opcodes.IRETURN));code.add(allowed);method.instructions.insert(code);removal++;
            }
            if(method.name.equals("<init>"))continue;
            for(AbstractInsnNode insn:method.instructions.toArray())if(insn instanceof FieldInsnNode field
                    &&field.getOpcode()==Opcodes.PUTFIELD&&field.owner.equals(MANAGER)&&field.name.equals("mTransformerList")) {
                InsnList code=new InsnList();
                code.add(new MethodInsnNode(Opcodes.INVOKESTATIC,BRIDGE,"order","(Ljava/lang/Object;)Ljava/lang/Object;",false));
                code.add(new TypeInsnNode(Opcodes.CHECKCAST,field.desc));method.instructions.insertBefore(field,code);publications++;
            }
        }
        if(removal!=1||publications!=(name.equals(MANAGER)?2:0))throw new IllegalStateException("INSTRUMENTATION_MANAGER_LAYOUT:"+name+":"+removal+":"+publications);
        ControlClassWriter writer=new ControlClassWriter(loader,node);node.accept(writer);return writer.toByteArray();
    }
}
