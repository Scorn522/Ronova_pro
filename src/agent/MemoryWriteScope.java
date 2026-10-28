package dev.ronova.pro.agent;
import jdk.internal.org.objectweb.asm.*;
import jdk.internal.org.objectweb.asm.tree.*;
/** Shared result semantics for observed writer scopes. */
final class MemoryWriteScope {
    private MemoryWriteScope(){}
    static void applied(InsnList code,String operation,Type result,int returned,Type[] arguments,int[] slots){
        if(result.getSort()==Type.BOOLEAN&&(operation.startsWith("compareAndSet")||operation.startsWith("weakCompareAndSet")||operation.startsWith("compareAndSwap"))){code.add(new VarInsnNode(Opcodes.ILOAD,returned));return;}
        if(!operation.startsWith("compareAndExchange")){code.add(new InsnNode(Opcodes.ICONST_1));return;}
        LabelNode different=new LabelNode(),done=new LabelNode();code.add(new VarInsnNode(result.getOpcode(Opcodes.ILOAD),returned));
        if(result.getSort()==Type.FLOAT)code.add(new MethodInsnNode(Opcodes.INVOKESTATIC,"java/lang/Float","floatToRawIntBits","(F)I",false));
        else if(result.getSort()==Type.DOUBLE)code.add(new MethodInsnNode(Opcodes.INVOKESTATIC,"java/lang/Double","doubleToRawLongBits","(D)J",false));
        Type expected=arguments[2];code.add(new VarInsnNode(expected.getOpcode(Opcodes.ILOAD),slots[2]));
        if(expected.getSort()==Type.FLOAT)code.add(new MethodInsnNode(Opcodes.INVOKESTATIC,"java/lang/Float","floatToRawIntBits","(F)I",false));
        else if(expected.getSort()==Type.DOUBLE)code.add(new MethodInsnNode(Opcodes.INVOKESTATIC,"java/lang/Double","doubleToRawLongBits","(D)J",false));
        if(result.getSort()==Type.LONG||result.getSort()==Type.DOUBLE){code.add(new InsnNode(Opcodes.LCMP));code.add(new JumpInsnNode(Opcodes.IFNE,different));}
        else code.add(new JumpInsnNode(result.getSort()==Type.OBJECT||result.getSort()==Type.ARRAY?Opcodes.IF_ACMPNE:Opcodes.IF_ICMPNE,different));
        code.add(new InsnNode(Opcodes.ICONST_1));code.add(new JumpInsnNode(Opcodes.GOTO,done));code.add(different);code.add(new InsnNode(Opcodes.ICONST_0));code.add(done);
    }
}
