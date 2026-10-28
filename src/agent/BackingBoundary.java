package dev.ronova.pro.agent;

import java.util.*;
import jdk.internal.org.objectweb.asm.*;
import jdk.internal.org.objectweb.asm.tree.*;

/** Caller-site array stores/copies and exact executor resource-release boundaries. */
final class BackingBoundary {
    private static final String BRIDGE="dev/ronova/pro/bootstrap/BackingBridge";
    private static final Set<String> CORE_INSTALLED=java.util.concurrent.ConcurrentHashMap.newKeySet();
    static boolean actualMutationCalls(byte[] bytes){
        boolean[] writes={false};
        new ClassReader(bytes).accept(new ClassVisitor(Opcodes.ASM8){
            @Override public void visitPermittedSubclassExperimental(String name){}
            @Override public MethodVisitor visitMethod(int access,String name,String descriptor,String signature,String[] exceptions){
                return new MethodVisitor(Opcodes.ASM8){
                    @Override public void visitMethodInsn(int opcode,String owner,String name,String descriptor,boolean itf){
                        if((owner.equals("java/lang/reflect/Array")||owner.equals("java/lang/reflect/Field"))
                                &&Set.of("set","setBoolean","setByte","setChar","setShort","setInt","setLong","setFloat","setDouble").contains(name)
                                ||owner.equals("java/lang/reflect/Method")&&name.equals("invoke")
                                ||(owner.equals("sun/misc/Unsafe")||owner.equals("jdk/internal/misc/Unsafe"))
                                &&(name.startsWith("put")||name.startsWith("compareAnd")||name.startsWith("getAnd")||name.equals("copyMemory")||name.equals("copySwapMemory")||name.equals("setMemory")))writes[0]=true;
                    }
                };
            }
        },ClassReader.SKIP_DEBUG|ClassReader.SKIP_FRAMES);
        return writes[0];
    }
    static boolean coreInstalled() {return CORE_INSTALLED.containsAll(Set.of("java/util/concurrent/ThreadPoolExecutor","java/util/concurrent/ScheduledThreadPoolExecutor"));}
    static boolean installed(String name) {return CORE_INSTALLED.contains(name);}
    static byte[] transform(ClassLoader loader,String name,byte[] bytes) {
        ClassNode node=new ClassNode();new ClassReader(bytes).accept(node,0);
        if(!transform(loader,null,name,node))return null;
        ControlClassWriter writer=new ControlClassWriter(loader,node);node.accept(writer);
        byte[] result=writer.toByteArray();published(loader,name);return result;
    }
    static void published(ClassLoader loader,String name){if(loader==null)CORE_INSTALLED.add(name);}
    static boolean transform(ClassLoader loader,Module module,String name,ClassNode node) {
        boolean changed=false;
        if(loader==null&&(name.equals("java/util/concurrent/ThreadPoolExecutor")||name.equals("java/util/concurrent/ScheduledThreadPoolExecutor"))) {
            CORE_INSTALLED.remove(name);
            for(MethodNode method:node.methods)if(method.name.equals("shutdown")||method.name.equals("shutdownNow")) {
                InsnList code=new InsnList();LabelNode allowed=new LabelNode();code.add(new VarInsnNode(Opcodes.ALOAD,0));
                code.add(new MethodInsnNode(Opcodes.INVOKESTATIC,"dev/ronova/pro/bootstrap/ControlBridge","resourceReleaseAllowed","(Ljava/lang/Object;)Z",false));
                code.add(new JumpInsnNode(Opcodes.IFNE,allowed));
                if(method.name.equals("shutdownNow")) {code.add(new MethodInsnNode(Opcodes.INVOKESTATIC,"java/util/Collections","emptyList","()Ljava/util/List;",false));code.add(new InsnNode(Opcodes.ARETURN));}
                else code.add(new InsnNode(Opcodes.RETURN));
                code.add(allowed);method.instructions.insert(code);method.maxStack=Math.max(method.maxStack,1);changed=true;
            }
        } else if(loader!=null&&(module==null||!ModGroupBoundary.ids(module).contains("ronova_pro"))) {
            for(MethodNode method:node.methods)for(AbstractInsnNode at:method.instructions.toArray()) {
                int op=at.getOpcode();
                if(op==Opcodes.AASTORE) {
                    method.instructions.set(at,new MethodInsnNode(Opcodes.INVOKESTATIC,BRIDGE,"arrayStore","(Ljava/lang/Object;ILjava/lang/Object;)V",false));changed=true;
                } else if(op>=Opcodes.IASTORE&&op<=Opcodes.SASTORE) {
                    String helper=switch(op) {case Opcodes.IASTORE->"intStore";case Opcodes.LASTORE->"longStore";case Opcodes.FASTORE->"floatStore";case Opcodes.DASTORE->"doubleStore";case Opcodes.BASTORE->"byteStore";case Opcodes.CASTORE->"charStore";default->"shortStore";};
                    String type=op==Opcodes.LASTORE?"J":op==Opcodes.FASTORE?"F":op==Opcodes.DASTORE?"D":"I";
                    method.instructions.set(at,new MethodInsnNode(Opcodes.INVOKESTATIC,BRIDGE,helper,"(Ljava/lang/Object;I"+type+")V",false));changed=true;
                } else if(at instanceof MethodInsnNode call&&call.getOpcode()==Opcodes.INVOKESTATIC&&call.owner.equals("java/util/Arrays")
                        &&Set.of("fill","sort","parallelSort","setAll","parallelSetAll").contains(call.name)) {
                    call.owner=BRIDGE;changed=true;
                } else if(at instanceof MethodInsnNode call&&call.getOpcode()==Opcodes.INVOKESTATIC&&call.owner.equals("java/lang/System")&&call.name.equals("arraycopy")) {
                    call.owner=BRIDGE;call.name="copy";changed=true;
                } else if(at instanceof MethodInsnNode call&&call.getOpcode()==Opcodes.INVOKESTATIC&&call.owner.equals("java/lang/reflect/Array")&&Set.of("set","setBoolean","setByte","setChar","setShort","setInt","setLong","setFloat","setDouble").contains(call.name)) {
                    call.owner=BRIDGE;changed=true;
                }
            }
        }
        return changed;
    }
}
