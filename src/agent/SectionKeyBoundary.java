package dev.ronova.pro.agent;
import jdk.internal.org.objectweb.asm.*;
import jdk.internal.org.objectweb.asm.tree.*;
import java.util.List;
/** Primitive Section keys are a real index, separately from the section-object map. */
final class SectionKeyBoundary {
    static final String SET="it/unimi/dsi/fastutil/longs/LongAVLTreeSet";
    static List<String> targets(){return List.of(SET,SET+"$Entry",SET+"$SetIterator",SET+"$Subset",SET+"$Subset$SubsetIterator");}
    static byte[] transform(ClassLoader loader,String name,byte[] bytes) {
        if(!targets().contains(name))return null;
        ClassNode node=new ClassNode();new ClassReader(bytes).accept(node,0);
        if(name.equals(SET)) {
            int count=0;
            for(MethodNode method:node.methods) {
                if(method.name.equals("remove")&&method.desc.equals("(J)Z")) {
                    InsnList code=new InsnList();LabelNode allowed=new LabelNode();code.add(new VarInsnNode(Opcodes.ALOAD,0));
                    code.add(new VarInsnNode(Opcodes.LLOAD,1));
                    code.add(new MethodInsnNode(Opcodes.INVOKESTATIC,"java/lang/Long","valueOf","(J)Ljava/lang/Long;",false));
                    code.add(new InsnNode(Opcodes.ACONST_NULL));
                    code.add(new MethodInsnNode(Opcodes.INVOKESTATIC,"dev/ronova/pro/bootstrap/TaskBridge","indexRemovalAllowed","(Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;)Z",false));
                    code.add(new JumpInsnNode(Opcodes.IFNE,allowed));code.add(new InsnNode(Opcodes.ICONST_0));code.add(new InsnNode(Opcodes.IRETURN));
                    code.add(allowed);method.instructions.insert(code);count++;
                }
                if(method.name.equals("clear")&&method.desc.equals("()V")) {
                    InsnList code=new InsnList();LabelNode proceed=new LabelNode();code.add(new VarInsnNode(Opcodes.ALOAD,0));
                    code.add(new MethodInsnNode(Opcodes.INVOKESTATIC,"dev/ronova/pro/bootstrap/TaskBridge","clearProtectedMap","(Ljava/lang/Object;)Z",false));
                    code.add(new JumpInsnNode(Opcodes.IFEQ,proceed));code.add(new InsnNode(Opcodes.RETURN));code.add(proceed);
                    method.instructions.insert(code);count++;
                }
            }
            if(count!=2)throw new IllegalStateException("SECTION_KEY_SET_LAYOUT:"+count);
        }
        ControlClassWriter writer=new ControlClassWriter(loader,node);node.accept(writer);
        byte[] output=writer.toByteArray();byte[] guarded=FieldWriteBoundary.transform(loader,output);
        return guarded==null?output:guarded;
    }
}
