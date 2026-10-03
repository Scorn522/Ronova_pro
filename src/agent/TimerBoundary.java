package dev.ronova.pro.agent;

import jdk.internal.org.objectweb.asm.*;
import jdk.internal.org.objectweb.asm.tree.*;

/** Actual Timer queue publication and the TimerThread call, without stopping a shared timer. */
final class TimerBoundary {
    private static final java.util.Set<String> installed=java.util.concurrent.ConcurrentHashMap.newKeySet();
    static boolean installed(String name){return installed.contains(name);}
    private static final String BRIDGE="dev/ronova/pro/bootstrap/TaskBridge";
    static byte[] transform(ClassLoader loader,String name,byte[] bytes){
        if(loader!=null||!"java/util/Timer".equals(name)&&!"java/util/TimerThread".equals(name))return null;
        ClassNode node=new ClassNode();new ClassReader(bytes).accept(node,0);boolean changed=false;
        for(MethodNode method:node.methods){
            if(name.equals("java/util/Timer")&&method.name.equals("sched")&&method.desc.equals("(Ljava/util/TimerTask;JJ)V")){
                InsnList admission=new InsnList();admission.add(new VarInsnNode(Opcodes.ALOAD,1));admission.add(new MethodInsnNode(Opcodes.INVOKESTATIC,BRIDGE,"timerAdmission","(Ljava/lang/Object;)V",false));method.instructions.insert(admission);
                for(AbstractInsnNode instruction:method.instructions.toArray())if(instruction instanceof MethodInsnNode call&&call.owner.equals("java/util/TaskQueue")&&call.name.equals("add")&&call.desc.equals("(Ljava/util/TimerTask;)V")){
                    InsnList accepted=new InsnList();accepted.add(new VarInsnNode(Opcodes.ALOAD,0));accepted.add(new VarInsnNode(Opcodes.ALOAD,1));
                    accepted.add(new MethodInsnNode(Opcodes.INVOKESTATIC,BRIDGE,"timerSubmitted","(Ljava/lang/Object;Ljava/lang/Object;)V",false));method.instructions.insert(instruction,accepted);changed=true;
                }
            }
            if(name.equals("java/util/TimerThread")&&method.name.equals("mainLoop"))for(AbstractInsnNode instruction:method.instructions.toArray())
                if(instruction instanceof MethodInsnNode call&&call.owner.equals("java/util/TimerTask")&&call.name.equals("run")&&call.desc.equals("()V")){
                    int task=method.maxLocals++,error=method.maxLocals++;LabelNode start=new LabelNode(),end=new LabelNode(),handler=new LabelNode(),next=new LabelNode();
                    InsnList before=new InsnList();before.add(new VarInsnNode(Opcodes.ASTORE,task));before.add(new VarInsnNode(Opcodes.ALOAD,task));
                    before.add(new MethodInsnNode(Opcodes.INVOKESTATIC,BRIDGE,"timerEnter","(Ljava/lang/Object;)Z",false));before.add(new JumpInsnNode(Opcodes.IFEQ,next));
                    before.add(start);before.add(new VarInsnNode(Opcodes.ALOAD,task));method.instructions.insertBefore(instruction,before);
                    InsnList after=new InsnList();after.add(end);after.add(new VarInsnNode(Opcodes.ALOAD,task));after.add(new MethodInsnNode(Opcodes.INVOKESTATIC,BRIDGE,"timerExit","(Ljava/lang/Object;)V",false));after.add(new JumpInsnNode(Opcodes.GOTO,next));
                    // Keep the rethrow inside the original protected region so Timer's own
                    // InterruptedException handler still decides whether its shared thread continues.
                    after.add(handler);after.add(new VarInsnNode(Opcodes.ASTORE,error));after.add(new VarInsnNode(Opcodes.ALOAD,task));
                    after.add(new MethodInsnNode(Opcodes.INVOKESTATIC,BRIDGE,"timerExit","(Ljava/lang/Object;)V",false));after.add(new VarInsnNode(Opcodes.ALOAD,error));after.add(new InsnNode(Opcodes.ATHROW));after.add(next);method.instructions.insert(instruction,after);
                    method.tryCatchBlocks.add(0,new TryCatchBlockNode(start,end,handler,"java/lang/Throwable"));changed=true;
                }
        }
        if(!changed){installed.remove(name);return null;}ControlClassWriter writer=new ControlClassWriter(loader,node);node.accept(writer);byte[] result=writer.toByteArray();installed.add(name);return result;
    }
}
