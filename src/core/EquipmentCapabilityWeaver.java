package dev.ronova.pro;

import dev.ronova.pro.mixin.SaveFinallyPlugin;
import java.util.*;
import org.objectweb.asm.Handle;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;

/** Native Forge metadata operations; supplier and listener callbacks keep their original authority. */
public final class EquipmentCapabilityWeaver {
    private static final String PROVIDER="net/minecraftforge/common/capabilities/CapabilityProvider";
    private static final String DISPATCHER="net/minecraftforge/common/capabilities/CapabilityDispatcher";
    private static final String OPTIONAL="net/minecraftforge/common/util/LazyOptional";
    private static final String SUPPLIER="net/minecraftforge/common/util/NonNullSupplier",CONSUMER="net/minecraftforge/common/util/NonNullConsumer";
    private static final String FUNCTION="net/minecraftforge/common/util/NonNullFunction",PREDICATE="net/minecraftforge/common/util/NonNullPredicate";
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
            // The supplier still runs inside Forge's original monitor, before
            // either publication store has acquired a metadata gate.
            functional(resolve,"supply",SUPPLIER,"get","()Ljava/lang/Object;","capabilitySupply");
            publication(type,resolve,resolved,resolved,"resolve");
            MethodNode add=method(type,"addListener","(L"+CONSUMER+";)V");
            functional(add,"listener-immediate",CONSUMER,"accept","(Ljava/lang/Object;)V","capabilityAccept");
            body(type,add,"listen-add",true);
            body(type,method(type,"removeListener","(Lnet/minecraftforge/common/util/NonNullConsumer;)V"),"listen-remove",true);
            MethodNode invalidate=method(type,"invalidate","()V"),lazyMap=method(type,"lazyMap","(L"+FUNCTION+";)L"+OPTIONAL+";");
            MethodNode invalidated=lambda(type,invalidate,"accept","(L"+OPTIONAL+";)Ljava/util/function/Consumer;","lambda$invalidate$1","(L"+CONSUMER+";)V");
            MethodNode mapped=lambda(type,lazyMap,"get","(L"+OPTIONAL+";L"+FUNCTION+";)L"+SUPPLIER+";","lambda$lazyMap$0","(L"+FUNCTION+";)Ljava/lang/Object;");
            // Each actual listener is the original lambda body's operand. The
            // forEach Consumer captures only this optional, never that listener.
            functional(invalidated,"listener-invalidate",CONSUMER,"accept","(Ljava/lang/Object;)V","capabilityAccept");
            functional(mapped,"lazy-map",FUNCTION,"apply","(Ljava/lang/Object;)Ljava/lang/Object;","capabilityApply");
            forEach(invalidate);
            body(type,invalidate,"invalidate-optional",false);
            functional(method(type,"ifPresent","(L"+CONSUMER+";)V"),"if-present",CONSUMER,"accept","(Ljava/lang/Object;)V","capabilityAccept");
            MethodNode map=method(type,"map","(L"+FUNCTION+";)Ljava/util/Optional;");
            functional(map,"map",FUNCTION,"apply","(Ljava/lang/Object;)Ljava/lang/Object;","capabilityApply");
            MethodNode filter=method(type,"filter","(L"+PREDICATE+";)Ljava/util/Optional;");
            functional(filter,"filter",PREDICATE,"test","(Ljava/lang/Object;)Z","capabilityTest");
            functional(method(type,"orElseGet","(L"+SUPPLIER+";)Ljava/lang/Object;"),"or-else-get",SUPPLIER,"get","()Ljava/lang/Object;","capabilitySupply");
            functional(method(type,"orElseThrow","(L"+SUPPLIER+";)Ljava/lang/Object;"),"or-else-throw",SUPPLIER,"get","()Ljava/lang/Object;","capabilitySupply");
            derived(map,"map");derived(lazyMap,"lazy-map");derived(filter,"filter");
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
    /** Validate the real invokedynamic target; no hidden capture is inspected or replayed. */
    private static MethodNode lambda(ClassNode type,MethodNode caller,String name,String descriptor,String target,String targetDescriptor){
        InvokeDynamicInsnNode original=null;
        for(AbstractInsnNode instruction:caller.instructions)if(instruction instanceof InvokeDynamicInsnNode call){
            if(original!=null)throw new IllegalStateException("EQUIPMENT_CAPABILITY_LAMBDA_AMBIGUOUS:"+caller.name);original=call;
        }
        if(original==null||!original.name.equals(name)||!original.desc.equals(descriptor)||original.bsm.getTag()!=Opcodes.H_INVOKESTATIC
                ||!original.bsm.getOwner().equals("java/lang/invoke/LambdaMetafactory")||!original.bsm.getName().equals("metafactory")
                ||!original.bsm.getDesc().equals("(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;Ljava/lang/invoke/MethodType;Ljava/lang/invoke/MethodHandle;Ljava/lang/invoke/MethodType;)Ljava/lang/invoke/CallSite;")
                ||original.bsmArgs.length!=3||!(original.bsmArgs[1] instanceof Handle handle)||handle.getTag()!=Opcodes.H_INVOKEVIRTUAL
                ||!handle.getOwner().equals(OPTIONAL)||!handle.getName().equals(target)||!handle.getDesc().equals(targetDescriptor)||handle.isInterface())
            throw new IllegalStateException("EQUIPMENT_CAPABILITY_LAMBDA_LAYOUT:"+caller.name);
        MethodNode body=method(type,target,targetDescriptor);
        if((body.access&(Opcodes.ACC_PRIVATE|Opcodes.ACC_SYNTHETIC))!=(Opcodes.ACC_PRIVATE|Opcodes.ACC_SYNTHETIC)
                ||(body.access&(Opcodes.ACC_STATIC|Opcodes.ACC_NATIVE|Opcodes.ACC_ABSTRACT))!=0)
            throw new IllegalStateException("EQUIPMENT_CAPABILITY_LAMBDA_BODY:"+target);
        return body;
    }
    /** Replace exactly one natural interface invocation, retaining its actual operands and result. */
    private static void functional(MethodNode method,String phase,String owner,String name,String descriptor,String hook){
        MethodInsnNode original=null;
        for(AbstractInsnNode instruction:method.instructions)if(instruction instanceof MethodInsnNode call&&call.getOpcode()==Opcodes.INVOKEINTERFACE
                &&call.owner.equals(owner)&&call.name.equals(name)&&call.desc.equals(descriptor)){
            if(original!=null)throw new IllegalStateException("EQUIPMENT_CAPABILITY_FUNCTIONAL_AMBIGUOUS:"+method.name);original=call;
        }
        if(original==null)throw new IllegalStateException("EQUIPMENT_CAPABILITY_FUNCTIONAL_MISSING:"+method.name);
        boolean supplier=descriptor.equals("()Ljava/lang/Object;");
        if(!supplier&&!descriptor.equals("(Ljava/lang/Object;)V")&&!descriptor.equals("(Ljava/lang/Object;)Ljava/lang/Object;")&&!descriptor.equals("(Ljava/lang/Object;)Z"))
            throw new IllegalStateException("EQUIPMENT_CAPABILITY_FUNCTIONAL_SIGNATURE:"+descriptor);
        int callback=method.maxLocals++,value=supplier?-1:method.maxLocals++;InsnList args=new InsnList();
        if(!supplier)args.add(new VarInsnNode(Opcodes.ASTORE,value));args.add(new VarInsnNode(Opcodes.ASTORE,callback));
        args.add(new VarInsnNode(Opcodes.ALOAD,0));args.add(new VarInsnNode(Opcodes.ALOAD,callback));
        if(!supplier)args.add(new VarInsnNode(Opcodes.ALOAD,value));args.add(new LdcInsnNode(phase));
        method.instructions.insertBefore(original,args);
        String result=descriptor.substring(descriptor.indexOf(')')+1);
        method.instructions.set(original,new MethodInsnNode(Opcodes.INVOKESTATIC,POLICY,hook,
                "(Ljava/lang/Object;Ljava/lang/Object;"+(supplier?"":"Ljava/lang/Object;")+"Ljava/lang/String;)"+result,false));
        method.maxStack=Math.max(method.maxStack+2,4);
    }
    /** Observe only the normal original return, leaving its exact operand for ARETURN. */
    private static void derived(MethodNode method,String phase){
        int returns=0;
        for(AbstractInsnNode instruction:method.instructions.toArray())if(instruction.getOpcode()==Opcodes.ARETURN){
            InsnList observed=new InsnList();observed.add(new InsnNode(Opcodes.DUP));observed.add(new VarInsnNode(Opcodes.ALOAD,0));observed.add(new InsnNode(Opcodes.SWAP));observed.add(new LdcInsnNode(phase));
            observed.add(new MethodInsnNode(Opcodes.INVOKESTATIC,POLICY,"capabilityDerived","(Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/String;)V",false));method.instructions.insertBefore(instruction,observed);returns++;
        }
        if(returns==0)throw new IllegalStateException("EQUIPMENT_CAPABILITY_DERIVED_RETURN_MISSING:"+method.name);
        method.maxStack=Math.max(method.maxStack+3,4);
    }
    /** Preserve the actual set and its original captured Consumer at this one forEach call. */
    private static void forEach(MethodNode method){
        MethodInsnNode original=null;
        for(AbstractInsnNode instruction:method.instructions)if(instruction instanceof MethodInsnNode call&&call.getOpcode()==Opcodes.INVOKEINTERFACE
                &&call.owner.equals("java/util/Set")&&call.name.equals("forEach")&&call.desc.equals("(Ljava/util/function/Consumer;)V")){
            if(original!=null)throw new IllegalStateException("EQUIPMENT_CAPABILITY_FOREACH_AMBIGUOUS");original=call;
        }
        if(original==null)throw new IllegalStateException("EQUIPMENT_CAPABILITY_FOREACH_MISSING");
        int consumer=method.maxLocals++,set=method.maxLocals++;InsnList before=new InsnList();
        before.add(new VarInsnNode(Opcodes.ASTORE,consumer));before.add(new VarInsnNode(Opcodes.ASTORE,set));
        before.add(new VarInsnNode(Opcodes.ALOAD,0));before.add(new VarInsnNode(Opcodes.ALOAD,set));before.add(new VarInsnNode(Opcodes.ALOAD,consumer));
        method.instructions.insertBefore(original,before);
        method.instructions.set(original,new MethodInsnNode(Opcodes.INVOKESTATIC,POLICY,"capabilityForEach","(Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;)V",false));
        method.maxStack=Math.max(method.maxStack+1,3);
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
