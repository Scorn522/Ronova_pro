package dev.ronova.pro;

import java.util.*;
import dev.ronova.pro.mixin.SaveFinallyPlugin;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;

/** The standard identity map's completed rehash tuple, with an outer finally. */
public final class WorldRegistryWeaver {
    private static final String OWNER="it/unimi/dsi/fastutil/objects/Object2IntOpenCustomHashMap",RUNTIME="dev/ronova/pro/ProRuntime";
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
        begin.add(new VarInsnNode(Opcodes.ALOAD,0));begin.add(new VarInsnNode(Opcodes.ILOAD,1));begin.add(new VarInsnNode(Opcodes.ILOAD,4));
        begin.add(new VarInsnNode(Opcodes.ALOAD,2));begin.add(new VarInsnNode(Opcodes.ALOAD,3));begin.add(new VarInsnNode(Opcodes.ALOAD,5));begin.add(new VarInsnNode(Opcodes.ALOAD,6));
        begin.add(new MethodInsnNode(Opcodes.INVOKESTATIC,RUNTIME,"worldRegistryRehashEnter","(Ljava/lang/Object;II[Ljava/lang/Object;[I[Ljava/lang/Object;[I)Ljava/lang/Object;",false));
        begin.add(new VarInsnNode(Opcodes.ASTORE,token));begin.add(start);rehash.instructions.insertBefore(anchor,begin);
        for(AbstractInsnNode insn:rehash.instructions.toArray())if(insn.getOpcode()==Opcodes.RETURN)rehash.instructions.insertBefore(insn,exit(token));
        rehash.instructions.add(end);rehash.instructions.add(handler);Object[] locals=SaveFinallyPlugin.lifecycleFrameLocals(type,rehash,token);
        rehash.instructions.add(new FrameNode(Opcodes.F_NEW,locals.length,locals,1,new Object[]{"java/lang/Throwable"}));rehash.instructions.add(new VarInsnNode(Opcodes.ASTORE,failure));
        rehash.instructions.add(exit(token));rehash.instructions.add(new VarInsnNode(Opcodes.ALOAD,failure));rehash.instructions.add(new InsnNode(Opcodes.ATHROW));
        rehash.tryCatchBlocks.add(new TryCatchBlockNode(start,end,handler,"java/lang/Throwable"));rehash.maxStack=Math.max(rehash.maxStack,7);
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
