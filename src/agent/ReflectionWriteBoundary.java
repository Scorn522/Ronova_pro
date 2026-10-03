package dev.ronova.pro.agent;
import java.util.Set;
import jdk.internal.org.objectweb.asm.*;
import jdk.internal.org.objectweb.asm.tree.*;
/** All nine Field setters consult an already-connected bootstrap policy. */
final class ReflectionWriteBoundary {
    private static final Set<String> SETTERS=Set.of("set","setBoolean","setByte","setChar","setShort","setInt","setLong","setFloat","setDouble");
    private static volatile int guarded;
    static int guardedMethods() { return guarded; }
    static String state() { return "REFLECTION_FIELD_GUARD_INSTALLED:"+guarded; }
    static int guardedCount() { return guarded; }
    static byte[] transform(ClassLoader loader,String name,byte[] bytes) {
        if(!"java/lang/reflect/Field".equals(name))return null;
        ClassNode node=new ClassNode();new ClassReader(bytes).accept(node,0);int count=0;
        for(MethodNode m:node.methods) {
            if(!SETTERS.contains(m.name))continue;
            Type[] args=Type.getArgumentTypes(m.desc);
            if(args.length!=2||args[0].getSort()!=Type.OBJECT)continue;
            boolean exists=false;
            for(AbstractInsnNode i:m.instructions.toArray())if(i instanceof MethodInsnNode c&&c.owner.equals("dev/ronova/pro/bootstrap/TaskBridge")&&c.name.equals("reflectFieldWrite"))exists=true;
            if(!exists) {
                // Preserve CallerSensitive caller capture, access checks and accessor acquisition exactly.
                // Guard the actual write call only after the JDK has established access to this field.
                MethodInsnNode write=null;
                for(AbstractInsnNode i:m.instructions.toArray())if(i instanceof MethodInsnNode call
                        &&call.owner.equals("jdk/internal/reflect/FieldAccessor")&&call.name.equals(m.name)&&call.desc.equals(m.desc)) {
                    if(write!=null)throw new IllegalStateException("REFLECTION_AMBIGUOUS_WRITE_SITE:"+m.name);
                    write=call;
                }
                if(write==null)throw new IllegalStateException("REFLECTION_WRITE_SITE_UNAVAILABLE:"+m.name);
                InsnList c=new InsnList();LabelNode allowed=new LabelNode();
                c.add(new VarInsnNode(Opcodes.ALOAD,0));c.add(new VarInsnNode(Opcodes.ALOAD,1));
                c.add(new VarInsnNode(args[1].getOpcode(Opcodes.ILOAD),2));ControlClassWriter.box(c,args[1]);
                c.add(new MethodInsnNode(Opcodes.INVOKESTATIC,"dev/ronova/pro/bootstrap/TaskBridge","reflectFieldWrite","(Ljava/lang/reflect/Field;Ljava/lang/Object;Ljava/lang/Object;)Z",false));
                c.add(new JumpInsnNode(Opcodes.IFNE,allowed));
                c.add(new TypeInsnNode(Opcodes.NEW,"java/lang/IllegalAccessException"));c.add(new InsnNode(Opcodes.DUP));
                c.add(new LdcInsnNode("RONOVA_PROTECTED_FIELD_WRITE_REFUSED"));
                c.add(new MethodInsnNode(Opcodes.INVOKESPECIAL,"java/lang/IllegalAccessException","<init>","(Ljava/lang/String;)V",false));
                c.add(new InsnNode(Opcodes.ATHROW));c.add(allowed);
                int ticket=m.maxLocals++,error=m.maxLocals++;LabelNode start=new LabelNode(),end=new LabelNode(),handler=new LabelNode(),done=new LabelNode();
                InsnList begin=new InsnList();begin.add(new VarInsnNode(Opcodes.ALOAD,0));begin.add(new VarInsnNode(Opcodes.ALOAD,1));
                begin.add(new MethodInsnNode(Opcodes.INVOKESTATIC,"dev/ronova/pro/bootstrap/TaskBridge","beginReflectionMutation","(Ljava/lang/reflect/Field;Ljava/lang/Object;)Ljava/lang/Object;",false));
                begin.add(new VarInsnNode(Opcodes.ASTORE,ticket));begin.add(start);begin.add(c);m.instructions.insertBefore(write,begin);
                InsnList finish=new InsnList();finish.add(end);finish.add(new VarInsnNode(Opcodes.ALOAD,ticket));finish.add(new InsnNode(Opcodes.ICONST_1));
                finish.add(new MethodInsnNode(Opcodes.INVOKESTATIC,"dev/ronova/pro/bootstrap/TaskBridge","finishFieldMutation","(Ljava/lang/Object;Z)V",false));finish.add(new JumpInsnNode(Opcodes.GOTO,done));
                finish.add(handler);finish.add(new VarInsnNode(Opcodes.ASTORE,error));finish.add(new VarInsnNode(Opcodes.ALOAD,ticket));finish.add(new InsnNode(Opcodes.ICONST_0));
                finish.add(new MethodInsnNode(Opcodes.INVOKESTATIC,"dev/ronova/pro/bootstrap/TaskBridge","finishFieldMutation","(Ljava/lang/Object;Z)V",false));
                finish.add(new VarInsnNode(Opcodes.ALOAD,error));finish.add(new InsnNode(Opcodes.ATHROW));finish.add(done);m.instructions.insert(write,finish);
                m.tryCatchBlocks.add(0,new TryCatchBlockNode(start,end,handler,"java/lang/Throwable"));
            }
            count++;
        }
        if(count!=9)throw new IllegalStateException("REFLECTION_SETTER_LAYOUT:"+count);
        ControlClassWriter writer=new ControlClassWriter(loader,node);node.accept(writer);guarded=count;return writer.toByteArray();
    }
}
