package dev.ronova.pro.agent;

import jdk.internal.org.objectweb.asm.*;
import jdk.internal.org.objectweb.asm.tree.*;

/** Checks live destinations at observed Mod call sites; never rewrites JDK writers. */
final class ModMemoryBoundary {
    private static final String BRIDGE="dev/ronova/pro/bootstrap/GuardedCalls";
    private ModMemoryBoundary(){}
    static boolean transform(ClassNode node){
        boolean changed=false;
        for(MethodNode method:node.methods)for(AbstractInsnNode at:method.instructions.toArray()){
            if(!(at instanceof MethodInsnNode call))continue;
            if(call.getOpcode()==Opcodes.INVOKESTATIC&&call.owner.equals("java/lang/invoke/MethodHandles")&&(call.name.equals("varHandleInvoker")||call.name.equals("varHandleExactInvoker"))){
                method.instructions.insertBefore(call,new InsnNode(call.name.equals("varHandleExactInvoker")?Opcodes.ICONST_1:Opcodes.ICONST_0));
                call.owner=BRIDGE;call.name="handleInvoker";call.desc="(Ljava/lang/invoke/VarHandle$AccessMode;Ljava/lang/invoke/MethodType;Z)Ljava/lang/invoke/MethodHandle;";method.maxStack++;changed=true;continue;
            }
            if(call.getOpcode()!=Opcodes.INVOKEVIRTUAL)continue;
            if(call.owner.equals("java/lang/invoke/VarHandle")&&call.name.equals("toMethodHandle")&&call.desc.equals("(Ljava/lang/invoke/VarHandle$AccessMode;)Ljava/lang/invoke/MethodHandle;")){
                call.setOpcode(Opcodes.INVOKESTATIC);call.owner=BRIDGE;call.name="handleMethod";call.desc="(Ljava/lang/invoke/VarHandle;Ljava/lang/invoke/VarHandle$AccessMode;)Ljava/lang/invoke/MethodHandle;";call.itf=false;changed=true;continue;
            }
            String entry;
            if((call.owner.equals("sun/misc/Unsafe")||call.owner.equals("jdk/internal/misc/Unsafe"))&&writer(call.name))entry="unsafe";
            else if(call.owner.equals("java/lang/invoke/VarHandle")&&handleWriter(call.name))entry="handle";
            else if(call.owner.startsWith("java/nio/")&&call.owner.endsWith("Buffer")&&bufferOperation(call.name))entry="buffer";
            else if(call.owner.equals("java/lang/reflect/Method")&&call.name.equals("invoke")&&call.desc.equals("(Ljava/lang/Object;[Ljava/lang/Object;)Ljava/lang/Object;")){
                method.instructions.insertBefore(call,new MethodInsnNode(Opcodes.INVOKESTATIC,"java/lang/invoke/MethodHandles","lookup","()Ljava/lang/invoke/MethodHandles$Lookup;",false));
                call.setOpcode(Opcodes.INVOKESTATIC);call.owner=BRIDGE;call.name="reflect";call.desc="(Ljava/lang/reflect/Method;Ljava/lang/Object;[Ljava/lang/Object;Ljava/lang/invoke/MethodHandles$Lookup;)Ljava/lang/Object;";call.itf=false;method.maxStack++;changed=true;continue;
            }else continue;
            Type[] arguments=Type.getArgumentTypes(call.desc);Type result=Type.getReturnType(call.desc);
            int[] slots=new int[arguments.length];for(int i=0;i<slots.length;i++){slots[i]=method.maxLocals;method.maxLocals+=arguments[i].getSize();}
            int receiver=method.maxLocals++;InsnList code=new InsnList();
            for(int i=slots.length-1;i>=0;i--)code.add(new VarInsnNode(arguments[i].getOpcode(Opcodes.ISTORE),slots[i]));
            code.add(new VarInsnNode(Opcodes.ASTORE,receiver));code.add(new VarInsnNode(Opcodes.ALOAD,receiver));
            code.add(new LdcInsnNode(Type.getObjectType(call.owner)));code.add(new LdcInsnNode(call.name));code.add(new LdcInsnNode(call.desc));
            code.add(new LdcInsnNode(arguments.length));code.add(new TypeInsnNode(Opcodes.ANEWARRAY,"java/lang/Object"));
            for(int i=0;i<slots.length;i++){code.add(new InsnNode(Opcodes.DUP));code.add(new LdcInsnNode(i));code.add(new VarInsnNode(arguments[i].getOpcode(Opcodes.ILOAD),slots[i]));ControlClassWriter.box(code,arguments[i]);code.add(new InsnNode(Opcodes.AASTORE));}
            code.add(new MethodInsnNode(Opcodes.INVOKESTATIC,"java/lang/invoke/MethodHandles","lookup","()Ljava/lang/invoke/MethodHandles$Lookup;",false));
            code.add(new MethodInsnNode(Opcodes.INVOKESTATIC,BRIDGE,entry,"(Ljava/lang/Object;Ljava/lang/Class;Ljava/lang/String;Ljava/lang/String;[Ljava/lang/Object;Ljava/lang/invoke/MethodHandles$Lookup;)Ljava/lang/Object;",false));
            if(result.getSort()==Type.VOID)code.add(new InsnNode(Opcodes.POP));
            else if(result.getSort()==Type.OBJECT||result.getSort()==Type.ARRAY)code.add(new TypeInsnNode(Opcodes.CHECKCAST,result.getInternalName()));
            else {
                String box=switch(result.getSort()){case Type.BOOLEAN->"Boolean";case Type.BYTE->"Byte";case Type.CHAR->"Character";case Type.SHORT->"Short";case Type.INT->"Integer";case Type.LONG->"Long";case Type.FLOAT->"Float";case Type.DOUBLE->"Double";default->throw new IllegalStateException("CALL_RESULT_TYPE");};
                code.add(new TypeInsnNode(Opcodes.CHECKCAST,"java/lang/"+box));code.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL,"java/lang/"+box,result.getClassName()+"Value","()"+result.getDescriptor(),false));
            }
            method.instructions.insertBefore(call,code);method.instructions.remove(call);method.maxStack+=8;changed=true;
        }
        return changed;
    }
    static boolean writer(String name){return name.startsWith("put")||name.startsWith("compareAnd")||name.startsWith("weakCompareAnd")||name.startsWith("getAnd")||name.equals("setMemory")||name.equals("copyMemory")||name.equals("copySwapMemory");}
    static boolean handleWriter(String name){return name.startsWith("set")||name.startsWith("compareAnd")||name.startsWith("weakCompareAnd")||name.startsWith("getAnd");}
    private static boolean bufferOperation(String name){return name.startsWith("get")||name.startsWith("put")||name.equals("compact")||name.equals("slice")||name.equals("duplicate")||name.equals("asReadOnlyBuffer")||name.matches("as(?:Char|Short|Int|Long|Float|Double)Buffer");}
}
