package dev.ronova.pro.agent;

import jdk.internal.org.objectweb.asm.*;
import jdk.internal.org.objectweb.asm.tree.*;

/** Dispatch one recoverable client listener without aborting the remaining mod listeners. */
final class EventFaultBoundary {
    static final String TARGET="net/minecraftforge/eventbus/EventBus";
    private static final String BRIDGE="dev/ronova/pro/bootstrap/EventFaultBridge";
    static byte[] transform(ClassLoader loader,byte[] bytes) {
        ClassNode node=new ClassNode();new ClassReader(bytes).accept(node,0);
        int replaced=0,removal=0;
        for(MethodNode method:node.methods) {
            if(method.name.equals("unregister")&&method.desc.equals("(Ljava/lang/Object;)V")) {
                InsnList code=new InsnList();LabelNode allowed=new LabelNode();code.add(new VarInsnNode(Opcodes.ALOAD,1));
                code.add(new MethodInsnNode(Opcodes.INVOKESTATIC,"dev/ronova/pro/bootstrap/TaskBridge","listenerRemovalAllowed","(Ljava/lang/Object;)Z",false));
                code.add(new JumpInsnNode(Opcodes.IFNE,allowed));code.add(new InsnNode(Opcodes.RETURN));code.add(allowed);
                method.instructions.insert(code);removal++;
            }
            if(!method.name.equals("post") || !method.desc.equals("(Lnet/minecraftforge/eventbus/api/Event;Lnet/minecraftforge/eventbus/api/IEventBusInvokeDispatcher;)Z"))continue;
            for(AbstractInsnNode instruction:method.instructions.toArray()) {
                if(!(instruction instanceof MethodInsnNode call)
                        || !call.owner.equals("net/minecraftforge/eventbus/api/IEventBusInvokeDispatcher")
                        || !call.name.equals("invoke") || !call.desc.equals("(Lnet/minecraftforge/eventbus/api/IEventListener;Lnet/minecraftforge/eventbus/api/Event;)V"))continue;
                int event=method.maxLocals++,listener=method.maxLocals++,dispatcher=method.maxLocals++;
                InsnList code=new InsnList();LabelNode done=new LabelNode();
                code.add(new VarInsnNode(Opcodes.ASTORE,event));
                code.add(new VarInsnNode(Opcodes.ASTORE,listener));
                code.add(new VarInsnNode(Opcodes.ASTORE,dispatcher));
                code.add(new VarInsnNode(Opcodes.ALOAD,dispatcher));
                code.add(new VarInsnNode(Opcodes.ALOAD,listener));
                code.add(new VarInsnNode(Opcodes.ALOAD,event));
                code.add(new MethodInsnNode(Opcodes.INVOKESTATIC,BRIDGE,"dispatch","(Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;)Z",false));
                code.add(new JumpInsnNode(Opcodes.IFNE,done));
                code.add(new VarInsnNode(Opcodes.ALOAD,dispatcher));
                code.add(new VarInsnNode(Opcodes.ALOAD,listener));
                code.add(new VarInsnNode(Opcodes.ALOAD,event));
                code.add(new MethodInsnNode(call.getOpcode(),call.owner,call.name,call.desc,call.itf));
                code.add(done);method.instructions.insertBefore(call,code);method.instructions.remove(call);replaced++;
            }
        }
        if(replaced!=1||removal!=1)throw new IllegalStateException("EVENT_FAULT_DISPATCH_SITE:"+replaced+":"+removal);
        ControlClassWriter writer=new ControlClassWriter(loader,node);node.accept(writer);return writer.toByteArray();
    }
}
