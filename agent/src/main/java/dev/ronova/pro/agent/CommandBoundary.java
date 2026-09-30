package dev.ronova.pro.agent;

import jdk.internal.org.objectweb.asm.ClassReader;
import jdk.internal.org.objectweb.asm.Opcodes;
import jdk.internal.org.objectweb.asm.tree.*;

/** Refuses a stopped module's registered Brigadier callback at its shared invocation. */
final class CommandBoundary {
    static final String TARGET="com/mojang/brigadier/CommandDispatcher";
    private static final String BRIDGE="dev/ronova/pro/bootstrap/TaskBridge";
    private static volatile boolean installed;
    private CommandBoundary() { }
    static boolean installed() { return installed; }
    static byte[] transform(ClassLoader loader,String name,byte[] bytes) {
        if(!TARGET.equals(name))return null;
        ClassNode node=new ClassNode();new ClassReader(bytes).accept(node,0);
        for(MethodNode method:node.methods)for(AbstractInsnNode insn:method.instructions.toArray())
            if(insn instanceof MethodInsnNode call&&call.owner.equals(BRIDGE)&&call.name.equals("modOwnerAllowed")) {
                installed=true;return null;
            }
        boolean changed=false;
        for(MethodNode method:node.methods)for(AbstractInsnNode insn:method.instructions.toArray())
            if(insn instanceof MethodInsnNode call&&call.getOpcode()==Opcodes.INVOKEINTERFACE
                    &&call.owner.equals("com/mojang/brigadier/Command")&&call.name.equals("run")) {
                InsnList gate=new InsnList();LabelNode allowed=new LabelNode(),done=new LabelNode();
                gate.add(new InsnNode(Opcodes.DUP2));gate.add(new InsnNode(Opcodes.POP));
                gate.add(new MethodInsnNode(Opcodes.INVOKESTATIC,BRIDGE,"modOwnerAllowed","(Ljava/lang/Object;)Z",false));
                gate.add(new JumpInsnNode(Opcodes.IFNE,allowed));
                gate.add(new InsnNode(Opcodes.POP2));gate.add(new InsnNode(Opcodes.ICONST_0));
                gate.add(new JumpInsnNode(Opcodes.GOTO,done));
                gate.add(allowed);
                method.instructions.insertBefore(call,gate);
                method.instructions.insert(call,done);
                changed=true;
            }
        if(!changed)return null;
        ControlClassWriter writer=new ControlClassWriter(loader,node);node.accept(writer);
        installed=true;return writer.toByteArray();
    }
}
