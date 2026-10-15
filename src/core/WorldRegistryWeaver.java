package dev.ronova.pro;

import java.util.*;
import dev.ronova.pro.mixin.SaveFinallyPlugin;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;

/** Exact standard map publication and deletion moves, each with an outer finally. */
public final class WorldRegistryWeaver {
    private static final String OWNER="it/unimi/dsi/fastutil/objects/Object2IntOpenCustomHashMap",ITERATOR=OWNER+"$MapIterator",RUNTIME="dev/ronova/pro/ProRuntime";
    private WorldRegistryWeaver() { }
    public static void apply(ClassNode type){
        if(!type.name.equals(OWNER))throw new IllegalArgumentException("ACTUAL_WORLD_REGISTRY_MAP_CLASS_REQUIRED");
        MethodNode rehash=null;
        for(MethodNode method:type.methods)if(method.name.equals("rehash")&&method.desc.equals("(I)V")){
            if(rehash!=null)throw new IllegalStateException("WORLD_REGISTRY_REHASH_AMBIGUOUS");rehash=method;
        }
        if(rehash==null)throw new IllegalStateException("WORLD_REGISTRY_REHASH_MISSING");
        List<FieldInsnNode> stores=new ArrayList<>();
        for(AbstractInsnNode insn:rehash.instructions)if(insn instanceof FieldInsnNode field&&field.getOpcode()==Opcodes.PUTFIELD&&field.owner.equals(OWNER))stores.add(field);
        String[] names={"n","mask","maxFill","key","value"},descriptors={"I","I","I","[Ljava/lang/Object;","[I"};
        if(stores.size()!=names.length)throw new IllegalStateException("WORLD_REGISTRY_REHASH_PUBLICATION_LAYOUT");
        for(int at=0;at<names.length;at++)if(!stores.get(at).name.equals(names[at])||!stores.get(at).desc.equals(descriptors[at]))
            throw new IllegalStateException("WORLD_REGISTRY_REHASH_PUBLICATION_LAYOUT");
        // In fastutil 8.5.9, locals 2/3 are the current arrays, 4 the new
        // mask, 5/6 the fully built arrays. Assert the concrete producer slots
        // before using them; an unfamiliar implementation gets no continuation.
        requireLoad(stores.get(0),Opcodes.ILOAD,1);requireLoad(stores.get(1),Opcodes.ILOAD,4);
        requireLoad(stores.get(3),Opcodes.ALOAD,5);requireLoad(stores.get(4),Opcodes.ALOAD,6);
        Set<Integer> referenceLocals=new HashSet<>();
        for(AbstractInsnNode insn:rehash.instructions){
            if(insn==stores.get(0))break;
            if(insn instanceof VarInsnNode variable&&variable.getOpcode()==Opcodes.ASTORE)referenceLocals.add(variable.var);
        }
        if(!referenceLocals.containsAll(Set.of(2,3,5,6)))throw new IllegalStateException("WORLD_REGISTRY_REHASH_LOCAL_LAYOUT");
        AbstractInsnNode anchor=previous(previous(stores.get(0)));
        if(!(anchor instanceof VarInsnNode variable)||variable.getOpcode()!=Opcodes.ALOAD||variable.var!=0)
            throw new IllegalStateException("WORLD_REGISTRY_REHASH_ANCHOR_LAYOUT");
        int token=rehash.maxLocals++,failure=rehash.maxLocals++;
        SaveFinallyPlugin.extendFramesWithLease(type,rehash,token);
        InsnList empty=new InsnList();empty.add(new InsnNode(Opcodes.ACONST_NULL));empty.add(new VarInsnNode(Opcodes.ASTORE,token));rehash.instructions.insert(empty);
        LabelNode start=new LabelNode(),end=new LabelNode(),handler=new LabelNode();InsnList begin=new InsnList();
        begin.add(start);begin.add(new VarInsnNode(Opcodes.ALOAD,0));begin.add(new VarInsnNode(Opcodes.ILOAD,1));begin.add(new VarInsnNode(Opcodes.ILOAD,4));
        begin.add(new VarInsnNode(Opcodes.ALOAD,2));begin.add(new VarInsnNode(Opcodes.ALOAD,3));begin.add(new VarInsnNode(Opcodes.ALOAD,5));begin.add(new VarInsnNode(Opcodes.ALOAD,6));
        begin.add(new MethodInsnNode(Opcodes.INVOKESTATIC,RUNTIME,"worldRegistryRehashEnter","(Ljava/lang/Object;II[Ljava/lang/Object;[I[Ljava/lang/Object;[I)Ljava/lang/Object;",false));
        begin.add(new VarInsnNode(Opcodes.ASTORE,token));rehash.instructions.insertBefore(anchor,begin);
        for(AbstractInsnNode insn:rehash.instructions.toArray())if(insn.getOpcode()==Opcodes.RETURN)rehash.instructions.insertBefore(insn,exit(token));
        rehash.instructions.add(end);rehash.instructions.add(handler);Object[] locals=SaveFinallyPlugin.lifecycleFrameLocals(type,rehash,token);
        rehash.instructions.add(new FrameNode(Opcodes.F_NEW,locals.length,locals,1,new Object[]{"java/lang/Throwable"}));rehash.instructions.add(new VarInsnNode(Opcodes.ASTORE,failure));
        rehash.instructions.add(exit(token));rehash.instructions.add(new VarInsnNode(Opcodes.ALOAD,failure));rehash.instructions.add(new InsnNode(Opcodes.ATHROW));
        rehash.tryCatchBlocks.add(new TryCatchBlockNode(start,end,handler,"java/lang/Throwable"));rehash.maxStack=Math.max(rehash.maxStack,7);
        removalEntry(type,method(type,"removeEntry","(I)I"),false);
        removalEntry(type,method(type,"removeNullEntry","()I"),true);
        MethodNode clear=method(type,"clear","()V");InsnList guard=new InsnList();guard.add(new VarInsnNode(Opcodes.ALOAD,0));
        guard.add(new MethodInsnNode(Opcodes.INVOKESTATIC,RUNTIME,"worldRegistryClear","(Ljava/lang/Object;)V",false));clear.instructions.insert(guard);
        shift(type,method(type,"shiftKeys","(I)V"));
    }
    public static void applyIterator(ClassNode type){
        if(!type.name.equals(ITERATOR))throw new IllegalArgumentException("ACTUAL_WORLD_REGISTRY_ITERATOR_CLASS_REQUIRED");
        MethodNode remove=method(type,"remove","()V");InsnList guard=new InsnList();guard.add(new VarInsnNode(Opcodes.ALOAD,0));
        guard.add(new MethodInsnNode(Opcodes.INVOKESTATIC,RUNTIME,"worldRegistryIteratorRemove","(Ljava/lang/Object;)V",false));remove.instructions.insert(guard);
        shift(type,method(type,"shiftKeys","(I)V"));
    }
    private static MethodNode method(ClassNode type,String name,String descriptor){
        var matches=type.methods.stream().filter(method->method.name.equals(name)&&method.desc.equals(descriptor)).toList();
        if(matches.size()!=1)throw new IllegalStateException("WORLD_REGISTRY_REMOVAL_METHOD_LAYOUT:"+name+descriptor);return matches.get(0);
    }
    private static void removalEntry(ClassNode type,MethodNode method,boolean nullEntry){
        InsnList guard=new InsnList();guard.add(new VarInsnNode(Opcodes.ALOAD,0));
        guard.add(nullEntry?new InsnNode(Opcodes.ICONST_0):new VarInsnNode(Opcodes.ILOAD,1));guard.add(new InsnNode(nullEntry?Opcodes.ICONST_1:Opcodes.ICONST_0));
        guard.add(new MethodInsnNode(Opcodes.INVOKESTATIC,RUNTIME,"worldRegistryRemove","(Ljava/lang/Object;IZ)V",false));method.instructions.insert(guard);
        method.maxStack=Math.max(method.maxStack,3);
    }
    private static void shift(ClassNode type,MethodNode method){
        List<AbstractInsnNode> keys=new ArrayList<>(),values=new ArrayList<>();
        for(AbstractInsnNode insn:method.instructions){if(insn.getOpcode()==Opcodes.AASTORE)keys.add(insn);if(insn.getOpcode()==Opcodes.IASTORE)values.add(insn);}
        if(keys.size()!=2||values.size()!=1||previous(keys.get(0)).getOpcode()!=Opcodes.ACONST_NULL)
            throw new IllegalStateException("WORLD_REGISTRY_SHIFT_STORE_LAYOUT");
        AbstractInsnNode store=keys.get(1),state=previous(store),index=previous(state),array=previous(index);
        if(!load(state,Opcodes.ALOAD,4)||!load(index,Opcodes.ILOAD,2)||!load(array,Opcodes.ALOAD,5))
            throw new IllegalStateException("WORLD_REGISTRY_SHIFT_KEY_LAYOUT");
        requireValueMove(type,store,values.get(0));
        int token=method.maxLocals++,failure=method.maxLocals++;SaveFinallyPlugin.extendFramesWithLease(type,method,token);
        LabelNode start=new LabelNode(),end=new LabelNode(),handler=new LabelNode();InsnList head=new InsnList();
        head.add(new InsnNode(Opcodes.ACONST_NULL));head.add(new VarInsnNode(Opcodes.ASTORE,token));head.add(start);
        head.add(new VarInsnNode(Opcodes.ALOAD,0));head.add(new VarInsnNode(Opcodes.ILOAD,1));head.add(new InsnNode(Opcodes.ICONST_0));
        head.add(new MethodInsnNode(Opcodes.INVOKESTATIC,RUNTIME,"worldRegistryRemove","(Ljava/lang/Object;IZ)V",false));method.instructions.insert(head);
        InsnList begin=new InsnList();begin.add(new VarInsnNode(Opcodes.ALOAD,0));begin.add(new VarInsnNode(Opcodes.ALOAD,5));
        begin.add(new VarInsnNode(Opcodes.ILOAD,1));begin.add(new VarInsnNode(Opcodes.ILOAD,2));begin.add(new VarInsnNode(Opcodes.ALOAD,4));
        begin.add(new MethodInsnNode(Opcodes.INVOKESTATIC,RUNTIME,"worldRegistryShiftEnter","(Ljava/lang/Object;[Ljava/lang/Object;IILjava/lang/Object;)Ljava/lang/Object;",false));
        begin.add(new VarInsnNode(Opcodes.ASTORE,token));method.instructions.insertBefore(array,begin);
        InsnList committed=new InsnList();committed.add(new VarInsnNode(Opcodes.ALOAD,token));
        committed.add(new MethodInsnNode(Opcodes.INVOKESTATIC,RUNTIME,"worldRegistryShiftCommit","(Ljava/lang/Object;)V",false));
        committed.add(new InsnNode(Opcodes.ACONST_NULL));committed.add(new VarInsnNode(Opcodes.ASTORE,token));method.instructions.insert(values.get(0),committed);
        InsnList cleared=new InsnList();cleared.add(new VarInsnNode(Opcodes.ALOAD,0));cleared.add(new VarInsnNode(Opcodes.ALOAD,5));cleared.add(new VarInsnNode(Opcodes.ILOAD,2));
        cleared.add(new MethodInsnNode(Opcodes.INVOKESTATIC,RUNTIME,"worldRegistryShiftCleared","(Ljava/lang/Object;[Ljava/lang/Object;I)V",false));method.instructions.insert(keys.get(0),cleared);
        for(AbstractInsnNode insn:method.instructions.toArray())if(insn.getOpcode()==Opcodes.RETURN)method.instructions.insertBefore(insn,shiftExit(token));
        method.instructions.add(end);method.instructions.add(handler);Object[] locals=SaveFinallyPlugin.lifecycleFrameLocals(type,method,token);
        method.instructions.add(new FrameNode(Opcodes.F_NEW,locals.length,locals,1,new Object[]{"java/lang/Throwable"}));method.instructions.add(new VarInsnNode(Opcodes.ASTORE,failure));
        method.instructions.add(new VarInsnNode(Opcodes.ALOAD,token));method.instructions.add(new VarInsnNode(Opcodes.ALOAD,failure));
        method.instructions.add(new MethodInsnNode(Opcodes.INVOKESTATIC,RUNTIME,"worldRegistryShiftFailed","(Ljava/lang/Object;Ljava/lang/Throwable;)V",false));
        method.instructions.add(new VarInsnNode(Opcodes.ALOAD,failure));method.instructions.add(new InsnNode(Opcodes.ATHROW));
        method.tryCatchBlocks.add(new TryCatchBlockNode(start,end,handler,"java/lang/Throwable"));method.maxStack=Math.max(method.maxStack,5);
    }
    private static void requireValueMove(ClassNode type,AbstractInsnNode keyStore,AbstractInsnNode valueStore){
        var sequence=new ArrayList<AbstractInsnNode>();
        for(AbstractInsnNode insn=keyStore.getNext();insn!=null;insn=insn.getNext())if(insn.getOpcode()>=0){sequence.add(insn);if(insn==valueStore)break;}
        int at=0;boolean iterator=type.name.equals(ITERATOR);
        if(sequence.size()!=(iterator?10:8)||!load(sequence.get(at++),Opcodes.ALOAD,0))throw new IllegalStateException("WORLD_REGISTRY_SHIFT_VALUE_LAYOUT");
        if(iterator&&!field(sequence.get(at++),ITERATOR,"this$0","L"+OWNER+";"))throw new IllegalStateException("WORLD_REGISTRY_SHIFT_OWNER_LAYOUT");
        if(!field(sequence.get(at++),OWNER,"value","[I")||!load(sequence.get(at++),Opcodes.ILOAD,2)||!load(sequence.get(at++),Opcodes.ALOAD,0))
            throw new IllegalStateException("WORLD_REGISTRY_SHIFT_VALUE_LAYOUT");
        if(iterator&&!field(sequence.get(at++),ITERATOR,"this$0","L"+OWNER+";"))throw new IllegalStateException("WORLD_REGISTRY_SHIFT_OWNER_LAYOUT");
        if(!field(sequence.get(at++),OWNER,"value","[I")||!load(sequence.get(at++),Opcodes.ILOAD,1)||sequence.get(at++).getOpcode()!=Opcodes.IALOAD
                ||sequence.get(at).getOpcode()!=Opcodes.IASTORE)throw new IllegalStateException("WORLD_REGISTRY_SHIFT_VALUE_LAYOUT");
    }
    private static boolean load(AbstractInsnNode insn,int opcode,int slot){return insn instanceof VarInsnNode load&&load.getOpcode()==opcode&&load.var==slot;}
    private static boolean field(AbstractInsnNode insn,String owner,String name,String descriptor){
        return insn instanceof FieldInsnNode field&&field.getOpcode()==Opcodes.GETFIELD&&field.owner.equals(owner)&&field.name.equals(name)&&field.desc.equals(descriptor);
    }
    private static InsnList shiftExit(int token){
        InsnList result=new InsnList();result.add(new VarInsnNode(Opcodes.ALOAD,token));
        result.add(new MethodInsnNode(Opcodes.INVOKESTATIC,RUNTIME,"worldRegistryShiftExit","(Ljava/lang/Object;)V",false));return result;
    }
    private static void requireLoad(AbstractInsnNode store,int opcode,int slot){
        AbstractInsnNode value=previous(store),receiver=previous(value);
        if(!(value instanceof VarInsnNode load)||load.getOpcode()!=opcode||load.var!=slot
                ||!(receiver instanceof VarInsnNode object)||object.getOpcode()!=Opcodes.ALOAD||object.var!=0)
            throw new IllegalStateException("WORLD_REGISTRY_REHASH_VALUE_LAYOUT");
    }
    private static AbstractInsnNode previous(AbstractInsnNode insn){
        if(insn==null)return null;AbstractInsnNode prior=insn.getPrevious();while(prior!=null&&prior.getOpcode()<0)prior=prior.getPrevious();return prior;
    }
    private static InsnList exit(int token){
        InsnList result=new InsnList();result.add(new VarInsnNode(Opcodes.ALOAD,token));
        result.add(new MethodInsnNode(Opcodes.INVOKESTATIC,RUNTIME,"worldRegistryRehashExit","(Ljava/lang/Object;)V",false));return result;
    }
}
