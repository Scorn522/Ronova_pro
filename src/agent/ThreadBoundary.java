package dev.ronova.pro.agent;

import jdk.internal.org.objectweb.asm.*;
import jdk.internal.org.objectweb.asm.tree.*;

/** Observe the real Thread.start window and the platform Runnable dispatch. */
final class ThreadBoundary {
    private static final String BRIDGE="dev/ronova/pro/bootstrap/TaskBridge";
    private static volatile boolean installed;
    private ThreadBoundary(){}
    static boolean installed(){return installed;}
    static byte[] transform(ClassLoader loader,String name,byte[] bytes){
        if(loader!=null||!"java/lang/Thread".equals(name))return null;
        installed=false;ClassNode node=new ClassNode();new ClassReader(bytes).accept(node,0);
        MethodNode start=null,run=null,interrupt=null,blocked=null;int controls=0;
        for(MethodNode method:node.methods){
            if(method.name.equals("start")&&method.desc.equals("()V"))start=method;
            if(method.name.equals("run")&&method.desc.equals("()V"))run=method;
            if(method.name.equals("interrupt")&&method.desc.equals("()V"))interrupt=method;
            if(method.name.equals("blockedOn")&&method.desc.equals("(Lsun/nio/ch/Interruptible;)V"))blocked=method;
            if(controlOperation(method)!=0)controls++;
        }
        if(start==null||run==null||interrupt==null||blocked==null||controls!=5||(start.access&Opcodes.ACC_SYNCHRONIZED)==0)return null;
        boolean nativeStart=false;for(AbstractInsnNode at:start.instructions.toArray())
            if(at instanceof MethodInsnNode call&&call.owner.equals(node.name)&&call.name.equals("start0")&&call.desc.equals("()V"))nativeStart=true;
        if(!nativeStart)return null;
        int token=start.maxLocals++,failure=start.maxLocals++;
        LabelNode beginning=new LabelNode(),end=new LabelNode(),handler=new LabelNode();
        InsnList enter=new InsnList();enter.add(new VarInsnNode(Opcodes.ALOAD,0));
        enter.add(new MethodInsnNode(Opcodes.INVOKESTATIC,BRIDGE,"threadStarting","(Ljava/lang/Thread;)Ljava/lang/Object;",false));
        enter.add(new VarInsnNode(Opcodes.ASTORE,token));enter.add(beginning);
        for(AbstractInsnNode at:start.instructions.toArray())if(at.getOpcode()==Opcodes.RETURN)start.instructions.insertBefore(at,exit(token));
        start.instructions.insert(enter);start.instructions.add(end);start.instructions.add(handler);
        start.instructions.add(new VarInsnNode(Opcodes.ASTORE,failure));start.instructions.add(exit(token));
        start.instructions.add(new VarInsnNode(Opcodes.ALOAD,failure));start.instructions.add(new InsnNode(Opcodes.ATHROW));
        start.tryCatchBlocks.add(new TryCatchBlockNode(beginning,end,handler,"java/lang/Throwable"));
        LabelNode allowed=new LabelNode();InsnList dispatch=new InsnList();dispatch.add(new VarInsnNode(Opcodes.ALOAD,0));
        dispatch.add(new MethodInsnNode(Opcodes.INVOKESTATIC,BRIDGE,"threadExecutionAllowed","(Ljava/lang/Thread;)Z",false));
        dispatch.add(new JumpInsnNode(Opcodes.IFNE,allowed));dispatch.add(new InsnNode(Opcodes.RETURN));dispatch.add(allowed);run.instructions.insert(dispatch);
        scoped(interrupt,"threadInterrupting","(Ljava/lang/Thread;)Ljava/lang/Object;","threadInterruptFinished",true);
        scoped(blocked,"threadBlocking","(Ljava/lang/Object;)Ljava/lang/Object;","threadBlockerFinished",false);
        for(MethodNode method:node.methods){
            int operation=controlOperation(method);if(operation==0)continue;
            InsnList guard=new InsnList();guard.add(new VarInsnNode(Opcodes.ALOAD,0));guard.add(new LdcInsnNode(operation));
            guard.add(new MethodInsnNode(Opcodes.INVOKESTATIC,BRIDGE,"threadControl","(Ljava/lang/Thread;I)V",false));method.instructions.insert(guard);
        }
        ControlClassWriter writer=new ControlClassWriter(loader,node);node.accept(writer);byte[] result=writer.toByteArray();installed=true;return result;
    }
    private static int controlOperation(MethodNode method){
        if((method.access&(Opcodes.ACC_STATIC|Opcodes.ACC_NATIVE|Opcodes.ACC_ABSTRACT))!=0)return 0;
        return switch(method.name+method.desc){case "stop()V"->3;case "suspend()V"->4;case "resume()V"->5;
            case "setPriority(I)V"->6;case "setName(Ljava/lang/String;)V"->7;default->0;};
    }
    private static void scoped(MethodNode method,String begin,String descriptor,String finish,boolean refusal){
        int token=method.maxLocals++,failure=method.maxLocals++;
        LabelNode start=new LabelNode(),end=new LabelNode(),handler=new LabelNode();
        InsnList entry=new InsnList();entry.add(new VarInsnNode(Opcodes.ALOAD,0));
        entry.add(new MethodInsnNode(Opcodes.INVOKESTATIC,BRIDGE,begin,descriptor,false));entry.add(new VarInsnNode(Opcodes.ASTORE,token));
        if(refusal){
            entry.add(new VarInsnNode(Opcodes.ALOAD,token));entry.add(new FieldInsnNode(Opcodes.GETSTATIC,"java/lang/Boolean","FALSE","Ljava/lang/Boolean;"));
            entry.add(new JumpInsnNode(Opcodes.IF_ACMPNE,start));entry.add(new InsnNode(Opcodes.RETURN));
        }
        entry.add(start);
        for(AbstractInsnNode at:method.instructions.toArray())if(at.getOpcode()==Opcodes.RETURN)method.instructions.insertBefore(at,finish(finish,token,true));
        method.instructions.insert(entry);method.instructions.add(end);method.instructions.add(handler);
        method.instructions.add(new VarInsnNode(Opcodes.ASTORE,failure));method.instructions.add(finish(finish,token,false));
        method.instructions.add(new VarInsnNode(Opcodes.ALOAD,failure));method.instructions.add(new InsnNode(Opcodes.ATHROW));
        method.tryCatchBlocks.add(new TryCatchBlockNode(start,end,handler,"java/lang/Throwable"));
    }
    private static InsnList finish(String name,int token,boolean normal){
        InsnList code=new InsnList();code.add(new VarInsnNode(Opcodes.ALOAD,token));code.add(new InsnNode(normal?Opcodes.ICONST_1:Opcodes.ICONST_0));
        code.add(new MethodInsnNode(Opcodes.INVOKESTATIC,BRIDGE,name,"(Ljava/lang/Object;Z)V",false));return code;
    }
    private static InsnList exit(int token){
        InsnList code=new InsnList();code.add(new VarInsnNode(Opcodes.ALOAD,token));
        code.add(new MethodInsnNode(Opcodes.INVOKESTATIC,BRIDGE,"threadStartFinished","(Ljava/lang/Object;)V",false));return code;
    }
}
