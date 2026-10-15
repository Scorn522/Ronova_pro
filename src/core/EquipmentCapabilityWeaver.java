package dev.ronova.pro;

import dev.ronova.pro.mixin.SaveFinallyPlugin;
import java.util.*;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;

/** Native Forge metadata operations; supplier and listener callbacks keep their original authority. */
public final class EquipmentCapabilityWeaver {
    private static final String PROVIDER="net/minecraftforge/common/capabilities/CapabilityProvider";
    private static final String DISPATCHER="net/minecraftforge/common/capabilities/CapabilityDispatcher";
    private static final String OPTIONAL="net/minecraftforge/common/util/LazyOptional";
    private static final String POLICY="dev/ronova/pro/EquipmentPolicy";
    private EquipmentCapabilityWeaver(){ }

    public static void apply(ClassNode type){
        if(type.name.equals(PROVIDER)){
            MethodNode gather=method(type,"doGatherCapabilities","(Lnet/minecraftforge/common/capabilities/ICapabilityProvider;)V");
            FieldInsnNode first=store(gather,PROVIDER,"capabilities","L"+DISPATCHER+";"),last=store(gather,PROVIDER,"initialized","Z");
            AbstractInsnNode producer=previous(first);
            if(!(producer instanceof MethodInsnNode call)||call.getOpcode()!=Opcodes.INVOKESTATIC
                    ||!call.owner.equals("net/minecraftforge/event/ForgeEventFactory")||!call.name.equals("gatherCapabilities")
                    ||!call.desc.equals("(Ljava/lang/Class;Lnet/minecraftforge/common/capabilities/ICapabilityProvider;Lnet/minecraftforge/common/capabilities/ICapabilityProvider;)L"+DISPATCHER+";"))
                throw new IllegalStateException("EQUIPMENT_CAPABILITY_GATHER_PUBLICATION_LAYOUT");
            AbstractInsnNode flag=next(first),one=next(flag);
            if(!load(flag,Opcodes.ALOAD,0)||one==null||one.getOpcode()!=Opcodes.ICONST_1||next(one)!=last||next(last).getOpcode()!=Opcodes.RETURN)
                throw new IllegalStateException("EQUIPMENT_CAPABILITY_GATHER_FLAG_LAYOUT");
            publication(type,gather,first,last,"publish");
            body(type,method(type,"invalidateCaps","()V"),"invalidate-provider",false);
            body(type,method(type,"reviveCaps","()V"),"revive-provider",false);
        }else if(type.name.equals(DISPATCHER)){
            body(type,method(type,"invalidate","()V"),"invalidate-dispatcher",false);
            query(type,method(type,"getCapability","(Lnet/minecraftforge/common/capabilities/Capability;Lnet/minecraft/core/Direction;)L"+OPTIONAL+";"));
        }else if(type.name.equals(OPTIONAL)){
            MethodNode resolve=method(type,"getValue","()Ljava/lang/Object;");
            FieldInsnNode resolved=store(resolve,OPTIONAL,"resolved","Lorg/apache/commons/lang3/mutable/Mutable;");
            AbstractInsnNode producer=previous(resolved);
            if(!(producer instanceof MethodInsnNode call)||call.getOpcode()!=Opcodes.INVOKESPECIAL||!call.owner.equals("org/apache/commons/lang3/mutable/MutableObject")
                    ||!call.name.equals("<init>")||!call.desc.equals("(Ljava/lang/Object;)V"))
                throw new IllegalStateException("EQUIPMENT_CAPABILITY_RESOLUTION_PUBLICATION_LAYOUT");
            publication(type,resolve,resolved,resolved,"resolve");
            body(type,method(type,"addListener","(Lnet/minecraftforge/common/util/NonNullConsumer;)V"),"listen-add",true);
            body(type,method(type,"removeListener","(Lnet/minecraftforge/common/util/NonNullConsumer;)V"),"listen-remove",true);
            body(type,method(type,"invalidate","()V"),"invalidate-optional",false);
        }else throw new IllegalArgumentException("ACTUAL_EQUIPMENT_CAPABILITY_CLASS_REQUIRED");
    }

    private static MethodNode method(ClassNode type,String name,String descriptor){
        var matches=type.methods.stream().filter(method->method.name.equals(name)&&method.desc.equals(descriptor)).toList();
        if(matches.size()!=1)throw new IllegalStateException("EQUIPMENT_CAPABILITY_METHOD_LAYOUT:"+name+descriptor);
        MethodNode result=matches.get(0);
        for(AbstractInsnNode instruction:result.instructions)if(instruction instanceof MethodInsnNode call&&call.owner.equals(POLICY)&&call.name.startsWith("capability"))
            throw new IllegalStateException("EQUIPMENT_CAPABILITY_ALREADY_WOVEN:"+name);
        return result;
    }
    private static FieldInsnNode store(MethodNode method,String owner,String name,String descriptor){
        FieldInsnNode result=null;
        for(AbstractInsnNode instruction:method.instructions)if(instruction instanceof FieldInsnNode field&&field.getOpcode()==Opcodes.PUTFIELD
                &&field.owner.equals(owner)&&field.name.equals(name)&&field.desc.equals(descriptor)){
            if(result!=null)throw new IllegalStateException("EQUIPMENT_CAPABILITY_STORE_AMBIGUOUS:"+name);result=field;
        }
        if(result==null)throw new IllegalStateException("EQUIPMENT_CAPABILITY_STORE_MISSING:"+name);return result;
    }
    private static int prepare(ClassNode type,MethodNode method){
        int token=method.maxLocals++;SaveFinallyPlugin.extendFramesWithLease(type,method,token);return token;
    }
    private static InsnList initialize(int token,LabelNode start){
        InsnList head=new InsnList();head.add(new InsnNode(Opcodes.ACONST_NULL));head.add(new VarInsnNode(Opcodes.ASTORE,token));head.add(start);return head;
    }
    private static InsnList enter(int token,String phase,AbstractInsnNode candidate){
        InsnList code=new InsnList();code.add(new VarInsnNode(Opcodes.ALOAD,0));code.add(candidate);code.add(new LdcInsnNode(phase));
        code.add(new MethodInsnNode(Opcodes.INVOKESTATIC,POLICY,"capabilityEnter","(Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/String;)Ljava/lang/Object;",false));
        code.add(new VarInsnNode(Opcodes.ASTORE,token));return code;
    }
    private static InsnList exit(int token){
        InsnList code=new InsnList();code.add(new VarInsnNode(Opcodes.ALOAD,token));
        code.add(new MethodInsnNode(Opcodes.INVOKESTATIC,POLICY,"capabilityExit","(Ljava/lang/Object;)V",false));
        code.add(new InsnNode(Opcodes.ACONST_NULL));code.add(new VarInsnNode(Opcodes.ASTORE,token));return code;
    }
    /** The only live scope is the two original stores, after all event/supplier callbacks. */
    private static void publication(ClassNode type,MethodNode method,FieldInsnNode first,FieldInsnNode last,String phase){
        int token=prepare(type,method),candidate=method.maxLocals++;
        LabelNode start=new LabelNode();method.instructions.insert(initialize(token,start));
        InsnList begin=new InsnList();begin.add(new VarInsnNode(Opcodes.ASTORE,candidate));begin.add(new InsnNode(Opcodes.POP));
        begin.add(enter(token,phase,new VarInsnNode(Opcodes.ALOAD,candidate)));
        begin.add(new VarInsnNode(Opcodes.ALOAD,0));begin.add(new VarInsnNode(Opcodes.ALOAD,candidate));method.instructions.insertBefore(first,begin);
        method.instructions.insert(last,exit(token));
        finish(type,method,token,start);
    }
    private static void body(ClassNode type,MethodNode method,String phase,boolean listener){
        int token=prepare(type,method);LabelNode start=new LabelNode();InsnList head=initialize(token,start);
        head.add(enter(token,phase,listener?new VarInsnNode(Opcodes.ALOAD,1):new InsnNode(Opcodes.ACONST_NULL)));method.instructions.insert(head);
        finish(type,method,token,start);
    }
    /** Observe the original provider return before Forge's own isPresent can resolve it. */
    private static void query(ClassNode type,MethodNode method){
        MethodInsnNode original=null;
        for(AbstractInsnNode instruction:method.instructions)if(instruction instanceof MethodInsnNode call&&call.getOpcode()==Opcodes.INVOKEINTERFACE
                &&call.owner.equals("net/minecraftforge/common/capabilities/ICapabilityProvider")&&call.name.equals("getCapability")&&call.desc.equals(method.desc)){
            if(original!=null)throw new IllegalStateException("EQUIPMENT_CAPABILITY_QUERY_AMBIGUOUS");original=call;
        }
        if(original==null||!load(previous(original),Opcodes.ALOAD,2)||!load(previous(previous(original)),Opcodes.ALOAD,1))
            throw new IllegalStateException("EQUIPMENT_CAPABILITY_QUERY_ARGUMENT_LAYOUT");
        int token=prepare(type,method),provider=method.maxLocals++,request=method.maxLocals++,direction=method.maxLocals++,result=method.maxLocals++;
        LabelNode start=new LabelNode();InsnList head=initialize(token,start);
        head.add(new VarInsnNode(Opcodes.ALOAD,0));head.add(new VarInsnNode(Opcodes.ALOAD,1));head.add(new VarInsnNode(Opcodes.ALOAD,2));
        head.add(new MethodInsnNode(Opcodes.INVOKESTATIC,POLICY,"capabilityQueryEnter","(Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;",false));
        head.add(new VarInsnNode(Opcodes.ASTORE,token));method.instructions.insert(head);
        // Preserve the actual invocation operand, rather than guessing a provider local or replaying the call.
        InsnList before=new InsnList();before.add(new VarInsnNode(Opcodes.ASTORE,direction));before.add(new VarInsnNode(Opcodes.ASTORE,request));
        before.add(new InsnNode(Opcodes.DUP));before.add(new VarInsnNode(Opcodes.ASTORE,provider));
        before.add(new VarInsnNode(Opcodes.ALOAD,request));before.add(new VarInsnNode(Opcodes.ALOAD,direction));method.instructions.insertBefore(original,before);
        InsnList observed=new InsnList();observed.add(new InsnNode(Opcodes.DUP));observed.add(new VarInsnNode(Opcodes.ASTORE,result));
        observed.add(new VarInsnNode(Opcodes.ALOAD,token));observed.add(new VarInsnNode(Opcodes.ALOAD,provider));observed.add(new VarInsnNode(Opcodes.ALOAD,result));
        observed.add(new MethodInsnNode(Opcodes.INVOKESTATIC,POLICY,"capabilityQueryObserved","(Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;)V",false));method.instructions.insert(original,observed);
        for(AbstractInsnNode instruction:method.instructions.toArray())if(instruction.getOpcode()==Opcodes.ARETURN){
            InsnList close=new InsnList();close.add(new InsnNode(Opcodes.DUP));close.add(new VarInsnNode(Opcodes.ALOAD,token));close.add(new InsnNode(Opcodes.SWAP));
            close.add(new MethodInsnNode(Opcodes.INVOKESTATIC,POLICY,"capabilityQueryExit","(Ljava/lang/Object;Ljava/lang/Object;)V",false));method.instructions.insertBefore(instruction,close);
        }
        int failure=method.maxLocals++;LabelNode end=new LabelNode(),handler=new LabelNode();method.instructions.add(end);method.instructions.add(handler);
        Object[] locals=SaveFinallyPlugin.lifecycleFrameLocals(type,method,token);
        method.instructions.add(new FrameNode(Opcodes.F_NEW,locals.length,locals,1,new Object[]{"java/lang/Throwable"}));
        method.instructions.add(new VarInsnNode(Opcodes.ASTORE,failure));method.instructions.add(new VarInsnNode(Opcodes.ALOAD,token));method.instructions.add(new VarInsnNode(Opcodes.ALOAD,failure));
        method.instructions.add(new MethodInsnNode(Opcodes.INVOKESTATIC,POLICY,"capabilityQueryFailed","(Ljava/lang/Object;Ljava/lang/Throwable;)V",false));
        method.instructions.add(new VarInsnNode(Opcodes.ALOAD,failure));method.instructions.add(new InsnNode(Opcodes.ATHROW));
        method.tryCatchBlocks.add(new TryCatchBlockNode(start,end,handler,"java/lang/Throwable"));method.maxStack=Math.max(method.maxStack+3,6);
    }
    private static void finish(ClassNode type,MethodNode method,int token,LabelNode start){
        // Append after the original handlers, particularly getValue's monitor exit.
        // Their exceptional control flow runs first; this outer handler only closes our scope.
        for(AbstractInsnNode instruction:method.instructions.toArray())if(instruction.getOpcode()>=Opcodes.IRETURN&&instruction.getOpcode()<=Opcodes.RETURN)
            method.instructions.insertBefore(instruction,exit(token));
        int failure=method.maxLocals++;LabelNode end=new LabelNode(),handler=new LabelNode();method.instructions.add(end);method.instructions.add(handler);
        Object[] locals=SaveFinallyPlugin.lifecycleFrameLocals(type,method,token);
        method.instructions.add(new FrameNode(Opcodes.F_NEW,locals.length,locals,1,new Object[]{"java/lang/Throwable"}));
        method.instructions.add(new VarInsnNode(Opcodes.ASTORE,failure));method.instructions.add(new VarInsnNode(Opcodes.ALOAD,token));method.instructions.add(new VarInsnNode(Opcodes.ALOAD,failure));
        method.instructions.add(new MethodInsnNode(Opcodes.INVOKESTATIC,POLICY,"capabilityFailed","(Ljava/lang/Object;Ljava/lang/Throwable;)V",false));
        method.instructions.add(new VarInsnNode(Opcodes.ALOAD,failure));method.instructions.add(new InsnNode(Opcodes.ATHROW));
        method.tryCatchBlocks.add(new TryCatchBlockNode(start,end,handler,"java/lang/Throwable"));method.maxStack=Math.max(method.maxStack+3,5);
    }
    private static boolean load(AbstractInsnNode instruction,int opcode,int slot){return instruction instanceof VarInsnNode variable&&variable.getOpcode()==opcode&&variable.var==slot;}
    private static AbstractInsnNode previous(AbstractInsnNode instruction){
        AbstractInsnNode prior=instruction.getPrevious();while(prior!=null&&prior.getOpcode()<0)prior=prior.getPrevious();return prior;
    }
    private static AbstractInsnNode next(AbstractInsnNode instruction){
        if(instruction==null)return null;AbstractInsnNode following=instruction.getNext();while(following!=null&&following.getOpcode()<0)following=following.getNext();return following;
    }
}
