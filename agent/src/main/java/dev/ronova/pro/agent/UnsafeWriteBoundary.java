package dev.ronova.pro.agent;
import java.util.Set;
import jdk.internal.org.objectweb.asm.*;
import jdk.internal.org.objectweb.asm.tree.*;
/** Java-facing sun.misc.Unsafe object puts and compare-and-swap. Raw addresses/bulk memory are not covered. */
final class UnsafeWriteBoundary {
    private static final Set<String> PUTS=Set.of("putObject","putBoolean","putByte","putShort","putChar","putInt","putLong","putFloat","putDouble",
        "putObjectVolatile","putBooleanVolatile","putByteVolatile","putShortVolatile","putCharVolatile","putIntVolatile","putLongVolatile","putFloatVolatile","putDoubleVolatile",
        "putOrderedObject","putOrderedInt","putOrderedLong","compareAndSwapObject","compareAndSwapInt","compareAndSwapLong");
    private static final Set<String> ATOMICS=Set.of("getAndSetObject","getAndSetInt","getAndSetLong","getAndAddInt","getAndAddLong");
    private static volatile int count;
    static int entries() { return count; }
    static byte[] transform(ClassLoader loader,String name,byte[] bytes) {
        if(!"sun/misc/Unsafe".equals(name))return null;
        ClassNode node=new ClassNode();new ClassReader(bytes).accept(node,0);int installed=0;
        for(MethodNode m:node.methods) {
            Type[] args=Type.getArgumentTypes(m.desc);
            if((m.name.equals("setMemory")&&m.desc.equals("(Ljava/lang/Object;JJB)V"))
                    ||(m.name.equals("copyMemory")&&m.desc.equals("(Ljava/lang/Object;JLjava/lang/Object;JJ)V"))) {
                InsnList code=new InsnList();LabelNode allow=new LabelNode();boolean copy=m.name.equals("copyMemory");
                code.add(new VarInsnNode(Opcodes.ALOAD,copy?4:1));code.add(new VarInsnNode(Opcodes.LLOAD,copy?5:2));code.add(new VarInsnNode(Opcodes.LLOAD,copy?7:4));
                code.add(new MethodInsnNode(Opcodes.INVOKESTATIC,"dev/ronova/pro/bootstrap/TaskBridge","unsafeMemoryAllowed","(Ljava/lang/Object;JJ)Z",false));
                code.add(new JumpInsnNode(Opcodes.IFNE,allow));code.add(new InsnNode(Opcodes.RETURN));code.add(allow);m.instructions.insert(code);installed++;continue;
            }
            if(!(PUTS.contains(m.name)||ATOMICS.contains(m.name))||args.length<3||!args[0].getDescriptor().equals("Ljava/lang/Object;")||args[1].getSort()!=Type.LONG)continue;
            if((m.access&(Opcodes.ACC_STATIC|Opcodes.ACC_NATIVE|Opcodes.ACC_ABSTRACT))!=0)throw new IllegalStateException("UNSAFE_WRITER_HAS_NO_JAVA_BOUNDARY:"+m.name);
            int value=1;for(int i=0;i<args.length-1;i++)value+=args[i].getSize();Type written=args[args.length-1];
            InsnList c=new InsnList();LabelNode allow=new LabelNode();
            c.add(new VarInsnNode(Opcodes.ALOAD,1));c.add(new VarInsnNode(Opcodes.LLOAD,2));c.add(new VarInsnNode(written.getOpcode(Opcodes.ILOAD),value));ControlClassWriter.box(c,written);
            if(ATOMICS.contains(m.name)) {
                c.add(new LdcInsnNode(m.name));c.add(new LdcInsnNode(written.getSort()==Type.OBJECT?"Object":written.getSort()==Type.LONG?"Long":"Int"));
                c.add(new MethodInsnNode(Opcodes.INVOKESTATIC,"dev/ronova/pro/bootstrap/BackingBridge","nativeWriteAllowed","(Ljava/lang/Object;JLjava/lang/Object;Ljava/lang/String;Ljava/lang/String;)Z",false));
            } else c.add(new MethodInsnNode(Opcodes.INVOKESTATIC,"dev/ronova/pro/bootstrap/TaskBridge","unsafeWriteAllowed","(Ljava/lang/Object;JLjava/lang/Object;)Z",false));
            c.add(new JumpInsnNode(Opcodes.IFNE,allow));
            if(Type.getReturnType(m.desc).getSort()==Type.BOOLEAN) { c.add(new InsnNode(Opcodes.ICONST_0));c.add(new InsnNode(Opcodes.IRETURN)); }
            else if(ATOMICS.contains(m.name)) {
                String kind=written.getSort()==Type.OBJECT?"Object":written.getSort()==Type.LONG?"Long":"Int";
                c.add(new VarInsnNode(Opcodes.ALOAD,1));c.add(new VarInsnNode(Opcodes.LLOAD,2));c.add(new LdcInsnNode(kind));
                c.add(new MethodInsnNode(Opcodes.INVOKESTATIC,"dev/ronova/pro/bootstrap/BackingBridge","retained","(Ljava/lang/Object;JLjava/lang/String;)Ljava/lang/Object;",false));
                if(written.getSort()==Type.OBJECT)c.add(new InsnNode(Opcodes.ARETURN));
                else {String number=written.getSort()==Type.LONG?"java/lang/Long":"java/lang/Integer";c.add(new TypeInsnNode(Opcodes.CHECKCAST,number));
                    c.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL,number,written.getSort()==Type.LONG?"longValue":"intValue",written.getSort()==Type.LONG?"()J":"()I",false));
                    c.add(new InsnNode(written.getSort()==Type.LONG?Opcodes.LRETURN:Opcodes.IRETURN));}
            }
            else c.add(new InsnNode(Opcodes.RETURN));
            c.add(allow);m.instructions.insert(c);installed++;
        }
        if(installed!=PUTS.size()+ATOMICS.size()+2)throw new IllegalStateException("UNSAFE_WRITER_LAYOUT:"+installed);
        ControlClassWriter writer=new ControlClassWriter(loader,node);node.accept(writer);count=installed;return writer.toByteArray();
    }
}
