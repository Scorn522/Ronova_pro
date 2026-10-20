package dev.ronova.pro.agent;

import jdk.internal.org.objectweb.asm.*;
import jdk.internal.org.objectweb.asm.tree.*;

/** Dispatch one recoverable client listener without aborting the remaining mod listeners. */
final class EventFaultBoundary {
    static final String TARGET="net/minecraftforge/eventbus/EventBus";
    private static final String BRIDGE="dev/ronova/pro/bootstrap/EventFaultBridge";
    private static final String CONTROL="dev/ronova/pro/bootstrap/ControlBridge";
    private static final String CONTINUITY="dev/ronova/pro/bootstrap/EventBusBridge";
    private static final String POST="(Lnet/minecraftforge/eventbus/api/Event;Lnet/minecraftforge/eventbus/api/IEventBusInvokeDispatcher;)Z";
    private static final String DISPATCH="(Ljava/lang/Class;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;)Z";
    private static final String INVOKE="(Lnet/minecraftforge/eventbus/api/IEventListener;Lnet/minecraftforge/eventbus/api/Event;)V";
    static byte[] transform(ClassLoader loader,byte[] bytes) {
        ClassNode node=new ClassNode();new ClassReader(bytes).accept(node,0);
        if(!TARGET.equals(node.name))throw new IllegalArgumentException("EVENT_FAULT_DEFINITION");
        int existingDispatch=0,existingRemoval=0,existingContinuity=0;
        for(MethodNode method:node.methods)for(AbstractInsnNode instruction:method.instructions.toArray())if(instruction instanceof MethodInsnNode call) {
            if(method.name.equals("post")&&method.desc.equals(POST)&&call.owner.equals(BRIDGE)&&call.name.equals("dispatch")) {
                if(!call.desc.equals(DISPATCH))throw new IllegalStateException("EVENT_FAULT_DISPATCH_CONTRACT");
                existingDispatch++;
            }
            if(method.name.equals("unregister")&&method.desc.equals("(Ljava/lang/Object;)V")&&call.owner.equals(CONTROL)
                    &&call.name.equals("listenerRemovalAllowed")&&call.desc.equals("(Ljava/lang/Object;)Z"))existingRemoval++;
            if(method.name.equals("post")&&method.desc.equals(POST)&&call.owner.equals(CONTINUITY)&&call.name.equals("dispatchBus")){
                if(!call.desc.equals("(Ljava/lang/Object;)Ljava/lang/Object;"))throw new IllegalStateException("EVENT_CONTINUITY_CONTRACT");
                existingContinuity++;
            }
        }
        if(existingDispatch>1||existingRemoval>1||existingContinuity>1)throw new IllegalStateException("EVENT_FAULT_DUPLICATE_BOUNDARY");
        if(existingDispatch==1&&existingRemoval==1&&existingContinuity==1) {
            if(!faultSites(node))throw new IllegalStateException("EVENT_FAULT_CHANGED_BOUNDARY");
            return null;
        }
        int replaced=existingDispatch,removal=existingRemoval;
        for(MethodNode method:node.methods) {
            if(existingContinuity==0&&method.name.equals("post")&&method.desc.equals(POST)){
                InsnList code=new InsnList();code.add(new VarInsnNode(Opcodes.ALOAD,0));
                code.add(new MethodInsnNode(Opcodes.INVOKESTATIC,CONTINUITY,"dispatchBus","(Ljava/lang/Object;)Ljava/lang/Object;",false));
                code.add(new TypeInsnNode(Opcodes.CHECKCAST,TARGET));code.add(new VarInsnNode(Opcodes.ASTORE,0));
                method.instructions.insert(code);
            }
            if(existingRemoval==0&&method.name.equals("unregister")&&method.desc.equals("(Ljava/lang/Object;)V")) {
                InsnList code=new InsnList();LabelNode allowed=new LabelNode();code.add(new VarInsnNode(Opcodes.ALOAD,1));
                code.add(new MethodInsnNode(Opcodes.INVOKESTATIC,CONTROL,"listenerRemovalAllowed","(Ljava/lang/Object;)Z",false));
                code.add(new JumpInsnNode(Opcodes.IFNE,allowed));code.add(new InsnNode(Opcodes.RETURN));code.add(allowed);
                method.instructions.insert(code);removal++;
            }
            if(existingDispatch!=0||!method.name.equals("post") || !method.desc.equals(POST))continue;
            for(AbstractInsnNode instruction:method.instructions.toArray()) {
                if(!(instruction instanceof MethodInsnNode call)
                        || !call.owner.equals("net/minecraftforge/eventbus/api/IEventBusInvokeDispatcher")
                        || !call.name.equals("invoke") || !call.desc.equals(INVOKE))continue;
                int event=method.maxLocals++,listener=method.maxLocals++,dispatcher=method.maxLocals++;
                InsnList code=new InsnList();LabelNode done=new LabelNode();
                code.add(new VarInsnNode(Opcodes.ASTORE,event));
                code.add(new VarInsnNode(Opcodes.ASTORE,listener));
                code.add(new VarInsnNode(Opcodes.ASTORE,dispatcher));
                code.add(new LdcInsnNode(Type.getObjectType(TARGET)));
                code.add(new VarInsnNode(Opcodes.ALOAD,dispatcher));
                code.add(new VarInsnNode(Opcodes.ALOAD,listener));
                code.add(new VarInsnNode(Opcodes.ALOAD,event));
                code.add(new MethodInsnNode(Opcodes.INVOKESTATIC,BRIDGE,"dispatch",DISPATCH,false));
                code.add(new JumpInsnNode(Opcodes.IFNE,done));
                code.add(new VarInsnNode(Opcodes.ALOAD,dispatcher));
                code.add(new VarInsnNode(Opcodes.ALOAD,listener));
                code.add(new VarInsnNode(Opcodes.ALOAD,event));
                code.add(new MethodInsnNode(call.getOpcode(),call.owner,call.name,call.desc,call.itf));
                code.add(done);method.instructions.insertBefore(call,code);method.instructions.remove(call);replaced++;
            }
        }
        if(replaced!=1||removal!=1)throw new IllegalStateException("EVENT_FAULT_DISPATCH_SITE:"+replaced+":"+removal);
        if(!faultSites(node))throw new IllegalStateException("EVENT_FAULT_CHANGED_BOUNDARY");
        ControlClassWriter writer=new ControlClassWriter(loader,node);node.accept(writer);return writer.toByteArray();
    }

    /** Check the actual final EventBus image, after all three composing boundaries. */
    static boolean verify(byte[] bytes) {
        if(bytes==null)return false;
        ClassNode node=new ClassNode();new ClassReader(bytes).accept(node,0);
        if(!TARGET.equals(node.name)||!faultSites(node))return false;
        MethodNode post=method(node,"post",POST),add=method(node,"addToListeners",EventBusBoundary.ADD_LISTENER);
        if(post==null||add==null)return false;
        MethodInsnNode allowed=one(post,CONTROL,"modListenerAllowed","(Ljava/lang/Object;)Z");
        MethodInsnNode dispatch=one(post,BRIDGE,"dispatch",DISPATCH);
        if(allowed==null||!(next(allowed) instanceof JumpInsnNode gate)||gate.getOpcode()!=Opcodes.IFEQ
                ||post.instructions.indexOf(allowed)>=post.instructions.indexOf(dispatch)
                ||post.instructions.indexOf(gate.label)<=post.instructions.indexOf(dispatch))return false;
        MethodInsnNode owner=one(add,CONTROL,"modOwnerAllowed","(Ljava/lang/Object;)Z");
        MethodInsnNode registered=one(add,CONTROL,"modListenerRegistered",EventBusBoundary.REGISTERED);
        return owner!=null&&registered!=null&&returnGate(owner,1)
                &&add.instructions.indexOf(owner)<add.instructions.indexOf(registered)
                &&registered.getOpcode()==Opcodes.INVOKESTATIC
                &&load(previous(registered),3)&&load(previous(previous(registered)),1)
                &&load(previous(previous(previous(registered))),0)
                &&next(registered)!=null&&next(registered).getOpcode()==Opcodes.RETURN;
    }
    private static boolean faultSites(ClassNode node) {
        MethodNode post=method(node,"post",POST),remove=method(node,"unregister","(Ljava/lang/Object;)V");
        if(post==null||remove==null)return false;
        MethodInsnNode continuity=one(post,CONTINUITY,"dispatchBus","(Ljava/lang/Object;)Ljava/lang/Object;");
        if(continuity==null||continuity.getOpcode()!=Opcodes.INVOKESTATIC||!load(previous(continuity),0)
                ||!(next(continuity) instanceof TypeInsnNode cast)||cast.getOpcode()!=Opcodes.CHECKCAST||!cast.desc.equals(TARGET)
                ||!store(next(cast),0))return false;
        MethodInsnNode dispatch=one(post,BRIDGE,"dispatch",DISPATCH);
        MethodInsnNode original=one(post,"net/minecraftforge/eventbus/api/IEventBusInvokeDispatcher","invoke",INVOKE);
        MethodInsnNode removal=one(remove,CONTROL,"listenerRemovalAllowed","(Ljava/lang/Object;)Z");
        if(dispatch==null||original==null||removal==null||!returnGate(removal,1))return false;
        AbstractInsnNode event=previous(dispatch),listener=previous(event),dispatcher=previous(listener),literal=previous(dispatcher);
        if(!(event instanceof VarInsnNode e)||e.getOpcode()!=Opcodes.ALOAD
                ||!(listener instanceof VarInsnNode l)||l.getOpcode()!=Opcodes.ALOAD
                ||!(dispatcher instanceof VarInsnNode d)||d.getOpcode()!=Opcodes.ALOAD
                ||!(literal instanceof LdcInsnNode constant)||!(constant.cst instanceof Type type)||!type.equals(Type.getObjectType(TARGET)))return false;
        AbstractInsnNode stored=previous(literal);
        if(!store(stored,d.var)||!store(previous(stored),l.var)||!store(previous(previous(stored)),e.var))return false;
        if(!(next(dispatch) instanceof JumpInsnNode handled)||handled.getOpcode()!=Opcodes.IFNE)return false;
        AbstractInsnNode fallback=next(handled);
        if(!load(fallback,d.var)||!load(next(fallback),l.var)||!load(next(next(fallback)),e.var)
                ||next(next(next(fallback)))!=original||original.getOpcode()!=Opcodes.INVOKEINTERFACE)return false;
        for(AbstractInsnNode cursor=original.getNext();cursor!=handled.label;cursor=cursor.getNext())
            if(cursor==null||cursor.getOpcode()>=0)return false;
        return true;
    }
    private static boolean returnGate(MethodInsnNode call,int argument) {
        if(call.getOpcode()!=Opcodes.INVOKESTATIC||!load(previous(call),argument)
                ||!(next(call) instanceof JumpInsnNode allowed)||allowed.getOpcode()!=Opcodes.IFNE)return false;
        AbstractInsnNode denied=next(allowed);
        if(denied==null||denied.getOpcode()!=Opcodes.RETURN)return false;
        for(AbstractInsnNode cursor=denied.getNext();cursor!=allowed.label;cursor=cursor.getNext())
            if(cursor==null||cursor.getOpcode()>=0)return false;
        return true;
    }
    private static MethodNode method(ClassNode node,String name,String descriptor) {
        MethodNode found=null;
        for(MethodNode method:node.methods)if(method.name.equals(name)&&method.desc.equals(descriptor)) {
            if(found!=null)return null;found=method;
        }
        return found;
    }
    private static MethodInsnNode one(MethodNode method,String owner,String name,String descriptor) {
        MethodInsnNode found=null;
        for(AbstractInsnNode instruction:method.instructions)if(instruction instanceof MethodInsnNode call&&call.owner.equals(owner)&&call.name.equals(name)) {
            if(found!=null||!call.desc.equals(descriptor))return null;found=call;
        }
        return found;
    }
    private static boolean load(AbstractInsnNode instruction,int variable) {
        return instruction instanceof VarInsnNode load&&load.getOpcode()==Opcodes.ALOAD&&load.var==variable;
    }
    private static boolean store(AbstractInsnNode instruction,int variable) {
        return instruction instanceof VarInsnNode store&&store.getOpcode()==Opcodes.ASTORE&&store.var==variable;
    }
    private static AbstractInsnNode previous(AbstractInsnNode instruction) {
        if(instruction==null)return null;
        do {instruction=instruction.getPrevious();}while(instruction!=null&&instruction.getOpcode()<0);
        return instruction;
    }
    private static AbstractInsnNode next(AbstractInsnNode instruction) {
        if(instruction==null)return null;
        do {instruction=instruction.getNext();}while(instruction!=null&&instruction.getOpcode()<0);
        return instruction;
    }
}
