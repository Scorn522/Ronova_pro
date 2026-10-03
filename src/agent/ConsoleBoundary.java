package dev.ronova.pro.agent;

import jdk.internal.org.objectweb.asm.ClassReader;
import jdk.internal.org.objectweb.asm.ClassWriter;
import jdk.internal.org.objectweb.asm.Opcodes;
import jdk.internal.org.objectweb.asm.tree.*;

/** Binds a queued console command to the actual dedicated-server reader and dispatch. */
final class ConsoleBoundary implements Opcodes {
    static final String TARGET="net/minecraft/server/dedicated/DedicatedServer";
    private static final String BRIDGE="dev/ronova/pro/bootstrap/TaskBridge";
    private static volatile String state="NOT_SEEN";
    private ConsoleBoundary() { }

    static byte[] transform(ClassLoader loader,String name,byte[] bytes) {
        if(!TARGET.equals(name))return null;
        try {
            ClassNode node=new ClassNode();new ClassReader(bytes).accept(node,ClassReader.EXPAND_FRAMES);
            if(!TARGET.equals(node.name))throw new IllegalStateException("CLASS_NAME_CHANGED");
            MethodNode enqueue=find(node,"m_139645_","handleConsoleInput","(Ljava/lang/String;Lnet/minecraft/commands/CommandSourceStack;)V");
            MethodNode dispatch=find(node,"m_139665_","handleConsoleInputs","()V");
            if(enqueue==null||dispatch==null)throw new IllegalStateException("CONSOLE_METHOD_LAYOUT_CHANGED");
            int registered=0,entered=0;
            for(AbstractInsnNode insn:enqueue.instructions.toArray())if(insn instanceof MethodInsnNode call
                    &&call.getOpcode()==INVOKEINTERFACE&&call.owner.equals("java/util/List")
                    &&call.name.equals("add")&&call.desc.equals("(Ljava/lang/Object;)Z")) {
                InsnList before=new InsnList();before.add(new InsnNode(DUP));
                before.add(new MethodInsnNode(INVOKESTATIC,BRIDGE,"registerConsoleInput","(Ljava/lang/Object;)V",false));
                enqueue.instructions.insertBefore(call,before);registered++;
            }
            for(AbstractInsnNode insn:dispatch.instructions.toArray())if(insn instanceof MethodInsnNode call
                    &&call.getOpcode()==INVOKEVIRTUAL&&call.owner.equals("net/minecraft/commands/Commands")
                    &&(call.name.equals("m_230957_")||call.name.equals("performPrefixedCommand"))
                    &&call.desc.equals("(Lnet/minecraft/commands/CommandSourceStack;Ljava/lang/String;)I")) {
                InsnList before=new InsnList();before.add(new VarInsnNode(ALOAD,1));
                before.add(new MethodInsnNode(INVOKESTATIC,BRIDGE,"beginConsoleCommand","(Ljava/lang/Object;)V",false));
                LabelNode start=new LabelNode(),end=new LabelNode(),handler=new LabelNode();
                before.add(start);dispatch.instructions.insertBefore(call,before);
                InsnList after=new InsnList();after.add(end);
                after.add(new MethodInsnNode(INVOKESTATIC,BRIDGE,"endConsoleCommand","()V",false));
                dispatch.instructions.insert(call,after);
                int failure=dispatch.maxLocals++;
                dispatch.instructions.add(handler);
                dispatch.instructions.add(new VarInsnNode(ASTORE,failure));
                dispatch.instructions.add(new MethodInsnNode(INVOKESTATIC,BRIDGE,"endConsoleCommand","()V",false));
                dispatch.instructions.add(new VarInsnNode(ALOAD,failure));
                dispatch.instructions.add(new InsnNode(ATHROW));
                dispatch.tryCatchBlocks.add(new TryCatchBlockNode(start,end,handler,null));entered++;
            }
            if(registered!=1||entered!=1)throw new IllegalStateException("CONSOLE_CALL_LAYOUT:"+registered+":"+entered);
            ClassWriter writer=new SafeWriter(ClassWriter.COMPUTE_FRAMES|ClassWriter.COMPUTE_MAXS,loader);
            node.accept(writer);state="INSTALLED";return writer.toByteArray();
        } catch(Throwable failure) {
            state="UNSUPPORTED:"+failure.getClass().getSimpleName()+":"+failure.getMessage();
            return null;
        }
    }
    static String state() {return state;}
    static boolean installed() {return state.equals("INSTALLED");}
    static void unavailable(String reason) {state="UNAVAILABLE:"+reason;}
    private static MethodNode find(ClassNode node,String srg,String mapped,String desc) {
        return node.methods.stream().filter(m->m.desc.equals(desc)&&(m.name.equals(srg)||m.name.equals(mapped))).findFirst().orElse(null);
    }
    private static final class SafeWriter extends ClassWriter {
        private final ClassLoader loader;
        SafeWriter(int flags,ClassLoader loader){super(flags);this.loader=loader;}
        @Override protected String getCommonSuperClass(String left,String right) {
            try {
                Class<?> a=Class.forName(left.replace('/','.'),false,loader),b=Class.forName(right.replace('/','.'),false,loader);
                if(a.isAssignableFrom(b))return left;
                if(b.isAssignableFrom(a))return right;
                if(a.isInterface()||b.isInterface())return "java/lang/Object";
                do {a=a.getSuperclass();} while(!a.isAssignableFrom(b));
                return a.getName().replace('.','/');
            } catch(ClassNotFoundException failure) {throw new IllegalStateException("FRAME_TYPE_UNAVAILABLE",failure);}
        }
    }
}
