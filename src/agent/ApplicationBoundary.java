package dev.ronova.pro.agent;

import jdk.internal.org.objectweb.asm.ClassReader;
import jdk.internal.org.objectweb.asm.Opcodes;
import jdk.internal.org.objectweb.asm.Type;
import jdk.internal.org.objectweb.asm.tree.*;

/** Consecutive internal stages share a tree; external transformer images stay separate. */
final class ApplicationBoundary {
    private ApplicationBoundary(){}
    static byte[] transform(ClassLoader loader,Module module,String name,Class<?> actual,byte[] bytes,boolean observeInputs){
        Object scope=dev.ronova.pro.bootstrap.TransformScope.enter(null);try {
        ClassNode node=new ClassNode();new ClassReader(bytes).accept(node,0);
        boolean changed=ModGroupBoundary.entries(loader,module,node);
        if(changed)locals(node);
        changed|=FieldWriteBoundary.transform(module,node);
        boolean producer=RecoveryAgent.producerModule(module);
        boolean backings=producer&&BackingBoundary.transform(loader,module,name,node);changed|=backings;
        if(producer||observeInputs)changed|=ModMemoryBoundary.transform(node);
        if(changed)locals(node);
        byte[] observed=producer?CreationBoundary.transform(loader,name,actual,node,true):null;
        byte[] result=observed!=null?observed:changed?write(loader,node):null;
        if(result!=null&&backings)BackingBoundary.published(loader,name);return result;
        }finally{dev.ronova.pro.bootstrap.TransformScope.exit(scope);}
    }
    static byte[] defined(ClassLoader loader,Module module,byte[] bytes){
        Object scope=dev.ronova.pro.bootstrap.TransformScope.enter(null);try {
        ClassNode node=new ClassNode();new ClassReader(bytes).accept(node,0);
        boolean changed=ModGroupBoundary.entries(loader,module,node);
        if(changed)locals(node);
        changed|=FieldWriteBoundary.transform(module,node);
        if(RecoveryAgent.producerModule(module))changed|=ModMemoryBoundary.transform(node);
        return changed?write(loader,node):null;
        }finally{dev.ronova.pro.bootstrap.TransformScope.exit(scope);}
    }
    static byte[] client(ClassLoader loader,Module module,String name,Class<?> actual,byte[] bytes,boolean observeInputs){
        Object scope=dev.ronova.pro.bootstrap.TransformScope.enter(null);try {
        ClassNode node=new ClassNode();new ClassReader(bytes).accept(node,0);
        boolean changed=FieldWriteBoundary.transform(module,node);
        if(changed)locals(node);
        if(RecoveryAgent.producerModule(module)||observeInputs)changed|=ModMemoryBoundary.transform(node);
        byte[] observed=RecoveryAgent.producerModule(module)?CreationBoundary.transform(loader,name,actual,node,true):null;
        return observed!=null?observed:changed?write(loader,node):bytes;
        }finally{dev.ronova.pro.bootstrap.TransformScope.exit(scope);}
    }
    static byte[] index(ClassLoader loader,Module module,String name,byte[] bytes){
        // FastutilIndexBoundary already seals each real container operation.
        // Its internal field/array instructions no longer carry duplicate gates.
        return bytes;
    }
    private static void locals(ClassNode node){
        // The removed intermediate COMPUTE_FRAMES writers derived locals from
        // actual instructions/debug ranges. Preserve that allocation baseline
        // rather than appending guard slots to an overstated input maxLocals.
        for(MethodNode method:node.methods){
            if((method.access&(Opcodes.ACC_ABSTRACT|Opcodes.ACC_NATIVE))!=0)continue;
            int count=(method.access&Opcodes.ACC_STATIC)==0?1:0;
            for(Type argument:Type.getArgumentTypes(method.desc))count+=argument.getSize();
            for(AbstractInsnNode instruction:method.instructions){
                if(instruction instanceof VarInsnNode local){
                    int op=local.getOpcode(),size=op==Opcodes.LLOAD||op==Opcodes.DLOAD||op==Opcodes.LSTORE||op==Opcodes.DSTORE?2:1;
                    count=Math.max(count,local.var+size);
                }else if(instruction instanceof IincInsnNode local)count=Math.max(count,local.var+1);
            }
            if(method.localVariables!=null)for(LocalVariableNode local:method.localVariables)count=Math.max(count,local.index+Type.getType(local.desc).getSize());
            method.maxLocals=count;
        }
    }
    private static byte[] write(ClassLoader loader,ClassNode node){
        ControlClassWriter writer=new ControlClassWriter(loader,node);node.accept(writer);return writer.toByteArray();
    }
}
