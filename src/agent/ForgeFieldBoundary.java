package dev.ronova.pro.agent;

import jdk.internal.org.objectweb.asm.*;
import jdk.internal.org.objectweb.asm.tree.*;

/** Keep Forge's ItemStack getter redirect after an earlier public access transformer. */
final class ForgeFieldBoundary {
    static final String TARGET="net/minecraftforge/coremod/api/ASMAPI";
    private static final String NODE="org/objectweb/asm/tree/ClassNode",FIELD="org/objectweb/asm/tree/FieldNode";
    private ForgeFieldBoundary(){}
    static byte[] transform(ClassLoader loader,byte[] bytes){
        ClassNode node=new ClassNode();new ClassReader(bytes).accept(node,0);
        if(!TARGET.equals(node.name))throw new IllegalStateException("FORGE_FIELD_API_CLASS_CHANGED");
        int patched=0;
        for(MethodNode method:node.methods){
            if(!method.name.equals("redirectFieldToMethod")
                    ||!method.desc.equals("(L"+NODE+";Ljava/lang/String;Ljava/lang/String;)V")
                    ||(method.access&(Opcodes.ACC_PUBLIC|Opcodes.ACC_STATIC))!=(Opcodes.ACC_PUBLIC|Opcodes.ACC_STATIC))continue;
            for(AbstractInsnNode at:method.instructions.toArray()){
                if(!(at instanceof MethodInsnNode call)||call.getOpcode()!=Opcodes.INVOKESTATIC
                        ||!call.owner.equals("java/lang/reflect/Modifier")||!call.name.equals("isPrivate")||!call.desc.equals("(I)Z"))continue;
                AbstractInsnNode access=previous(call),receiver=previous(access),branch=next(call);
                if(!(access instanceof FieldInsnNode field)||field.getOpcode()!=Opcodes.GETFIELD
                        ||!field.owner.equals(FIELD)||!field.name.equals("access")||!field.desc.equals("I")
                        ||!(receiver instanceof VarInsnNode load)||load.getOpcode()!=Opcodes.ALOAD
                        ||!(branch instanceof JumpInsnNode jump)||jump.getOpcode()!=Opcodes.IFEQ)
                    throw new IllegalStateException("FORGE_FIELD_PRIVATE_CHECK_CHANGED");
                method.instructions.insert(call,publicItemStack(load.var));patched++;
            }
        }
        if(patched!=1)throw new IllegalStateException("FORGE_FIELD_PRIVATE_CHECK_COUNT:"+patched);
        ControlClassWriter writer=new ControlClassWriter(loader,node);node.accept(writer);return writer.toByteArray();
    }
    private static InsnList publicItemStack(int field){
        InsnList code=new InsnList();LabelNode accepted=new LabelNode(),rejected=new LabelNode(),mapped=new LabelNode(),ready=new LabelNode();
        // Consume the real Modifier.isPrivate result. Every existing private
        // field retains Forge's original path; only this exact public field
        // gets the compatibility branch. No tree access flags are rewritten.
        code.add(new JumpInsnNode(Opcodes.IFNE,accepted));
        code.add(new VarInsnNode(Opcodes.ALOAD,field));code.add(new FieldInsnNode(Opcodes.GETFIELD,FIELD,"access","I"));
        code.add(new InsnNode(Opcodes.ICONST_1));code.add(new InsnNode(Opcodes.IAND));code.add(new JumpInsnNode(Opcodes.IFEQ,rejected));
        equalsField(code,0,NODE,"name","net/minecraft/world/item/ItemStack",rejected);
        equalsField(code,field,FIELD,"desc","Lnet/minecraft/world/item/Item;",rejected);
        code.add(new LdcInsnNode("f_41589_"));code.add(new VarInsnNode(Opcodes.ALOAD,1));
        code.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL,"java/lang/String","equals","(Ljava/lang/Object;)Z",false));
        code.add(new JumpInsnNode(Opcodes.IFEQ,mapped));
        equalsArgument(code,2,"m_41720_",rejected);code.add(new JumpInsnNode(Opcodes.GOTO,accepted));
        code.add(mapped);equalsArgument(code,1,"item",rejected);equalsArgument(code,2,"getItem",rejected);
        code.add(accepted);code.add(new InsnNode(Opcodes.ICONST_1));code.add(new JumpInsnNode(Opcodes.GOTO,ready));
        code.add(rejected);code.add(new InsnNode(Opcodes.ICONST_0));code.add(ready);
        // The original following isStatic check and all getter/body checks run.
        return code;
    }
    private static void equalsField(InsnList code,int local,String owner,String name,String value,LabelNode rejected){
        code.add(new LdcInsnNode(value));code.add(new VarInsnNode(Opcodes.ALOAD,local));
        code.add(new FieldInsnNode(Opcodes.GETFIELD,owner,name,"Ljava/lang/String;"));equals(code,rejected);
    }
    private static void equalsArgument(InsnList code,int local,String value,LabelNode rejected){
        code.add(new LdcInsnNode(value));code.add(new VarInsnNode(Opcodes.ALOAD,local));equals(code,rejected);
    }
    private static void equals(InsnList code,LabelNode rejected){
        code.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL,"java/lang/String","equals","(Ljava/lang/Object;)Z",false));
        code.add(new JumpInsnNode(Opcodes.IFEQ,rejected));
    }
    private static AbstractInsnNode previous(AbstractInsnNode at){do{at=at.getPrevious();}while(at!=null&&at.getOpcode()<0);return at;}
    private static AbstractInsnNode next(AbstractInsnNode at){do{at=at.getNext();}while(at!=null&&at.getOpcode()<0);return at;}
}
