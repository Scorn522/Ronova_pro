package dev.ronova.pro.agent;

import jdk.internal.org.objectweb.asm.ClassReader;
import jdk.internal.org.objectweb.asm.ClassWriter;
import jdk.internal.org.objectweb.asm.Opcodes;
import jdk.internal.org.objectweb.asm.tree.*;

/** Keeps a mod-owned listener from executing through Forge's shared dispatcher. */
final class EventBusBoundary {
    static final String TARGET="net/minecraftforge/eventbus/EventBus";
    private static final String BRIDGE="dev/ronova/pro/bootstrap/TaskBridge";
    private static volatile boolean installed;
    private EventBusBoundary() {}
    static String state() {return installed?"EVENT_REGISTRATION_AND_DISPATCH_INSTALLED":"EVENT_GATE_NOT_INSTALLED";}
    static byte[] transform(String name,byte[] bytes) {
        if(!TARGET.equals(name))return null;
        ClassNode node=new ClassNode();new ClassReader(bytes).accept(node,0);
        for(MethodNode method:node.methods)for(AbstractInsnNode insn:method.instructions.toArray())
            if(insn instanceof MethodInsnNode call&&call.owner.equals(BRIDGE)&&call.name.equals("modListenerAllowed"))
                return null;
        boolean registration=false,dispatch=false;
        for(MethodNode method:node.methods) {
            if(method.name.equals("addToListeners")&&method.desc.equals("(Ljava/lang/Object;Ljava/lang/Class;Lnet/minecraftforge/eventbus/api/IEventListener;Lnet/minecraftforge/eventbus/api/EventPriority;)V")) {
                LabelNode allowed=new LabelNode();InsnList code=new InsnList();
                code.add(new VarInsnNode(Opcodes.ALOAD,1));
                code.add(new MethodInsnNode(Opcodes.INVOKESTATIC,BRIDGE,"modOwnerAllowed","(Ljava/lang/Object;)Z",false));
                code.add(new JumpInsnNode(Opcodes.IFNE,allowed));code.add(new InsnNode(Opcodes.RETURN));
                code.add(allowed);code.add(new FrameNode(Opcodes.F_SAME,0,null,0,null));
                code.add(new VarInsnNode(Opcodes.ALOAD,1));code.add(new VarInsnNode(Opcodes.ALOAD,3));
                code.add(new MethodInsnNode(Opcodes.INVOKESTATIC,BRIDGE,"modListenerRegistered",
                        "(Ljava/lang/Object;Ljava/lang/Object;)V",false));
                method.instructions.insert(code);registration=true;
            }
            if(method.name.equals("post")&&method.desc.equals("(Lnet/minecraftforge/eventbus/api/Event;Lnet/minecraftforge/eventbus/api/IEventBusInvokeDispatcher;)Z")) {
                for(AbstractInsnNode insn:method.instructions.toArray())if(insn instanceof MethodInsnNode call
                        &&call.name.equals("invoke")&&call.owner.equals("net/minecraftforge/eventbus/api/IEventBusInvokeDispatcher")) {
                    AbstractInsnNode start=insn.getPrevious();
                    for(int i=0;i<5&&start!=null;i++,start=start.getPrevious())
                        if(start instanceof VarInsnNode variable&&variable.getOpcode()==Opcodes.ALOAD&&variable.var==2)break;
                    if(!(start instanceof VarInsnNode variable)||variable.var!=2)continue;
                    LabelNode skip=null;
                    for(AbstractInsnNode next=insn.getNext();next!=null;next=next.getNext()) {
                        if(next instanceof LabelNode label) {skip=label;break;}
                    }
                    if(skip==null)continue;
                    InsnList gate=new InsnList();gate.add(new VarInsnNode(Opcodes.ALOAD,3));
                    gate.add(new VarInsnNode(Opcodes.ILOAD,4));gate.add(new InsnNode(Opcodes.AALOAD));
                    gate.add(new MethodInsnNode(Opcodes.INVOKESTATIC,BRIDGE,"modListenerAllowed","(Ljava/lang/Object;)Z",false));
                    gate.add(new JumpInsnNode(Opcodes.IFEQ,skip));
                    method.instructions.insertBefore(start,gate);dispatch=true;
                    break;
                }
            }
        }
        if(!registration||!dispatch)return null;
        ClassWriter writer=new ClassWriter(ClassWriter.COMPUTE_MAXS);node.accept(writer);installed=true;return writer.toByteArray();
    }
}
