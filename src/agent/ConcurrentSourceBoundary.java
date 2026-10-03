package dev.ronova.pro.agent;

import java.util.Set;
import jdk.internal.org.objectweb.asm.*;
import jdk.internal.org.objectweb.asm.tree.*;

/** CHM keeps its original bin/reservation finally blocks; a refused incoming value exits through them. */
final class ConcurrentSourceBoundary {
    private static final String MAP="java/util/concurrent/ConcurrentHashMap",BRIDGE="dev/ronova/pro/bootstrap/SourceMapBridge";
    private static volatile boolean installed;
    static boolean installed() { return installed; }
    static byte[] transform(ClassLoader loader,String name,byte[] bytes) {
        if(loader!=null||!MAP.equals(name))return null;
        try {
            ClassNode node=new ClassNode();new ClassReader(bytes).accept(node,0);
            if(node.version!=Opcodes.V17)return null;int count=0;
            for(MethodNode method:node.methods) {
                if(method.name.equals("<init>")||method.name.equals("readObject"))
                    for(AbstractInsnNode at:method.instructions.toArray())if(at.getOpcode()==Opcodes.RETURN){
                        InsnList born=new InsnList();born.add(new VarInsnNode(Opcodes.ALOAD,0));
                        born.add(new MethodInsnNode(Opcodes.INVOKESTATIC,BRIDGE,"born","(Ljava/lang/Object;)V",false));method.instructions.insertBefore(at,born);
                    }
                if(method.name.equals("replace")||method.name.equals("replaceAll")) {
                    for(AbstractInsnNode at:method.instructions.toArray())if(at instanceof MethodInsnNode call&&call.owner.equals(MAP)&&call.name.equals("replaceNode")) {
                        InsnList translate=new InsnList();translate.add(new InsnNode(method.name.equals("replace")&&Type.getArgumentTypes(method.desc).length==3?Opcodes.ICONST_1:Opcodes.ICONST_0));
                        translate.add(new MethodInsnNode(Opcodes.INVOKESTATIC,BRIDGE,"publicResult","(Ljava/lang/Object;Z)Ljava/lang/Object;",false));method.instructions.insert(at,translate);
                    }
                }
                if(!Set.of("putVal","replaceNode","computeIfAbsent","computeIfPresent","compute","merge").contains(method.name))continue;
                AbstractInsnNode[] original=method.instructions.toArray();
                for(AbstractInsnNode at:original) {
                    if(at instanceof FieldInsnNode field&&field.getOpcode()==Opcodes.PUTFIELD&&field.name.equals("val")
                            &&field.owner.startsWith(MAP+"$")) {
                        int value=method.maxLocals++,entry=method.maxLocals++;
                        InsnList check=new InsnList();check.add(new VarInsnNode(Opcodes.ASTORE,value));check.add(new VarInsnNode(Opcodes.ASTORE,entry));
                        incoming(check,1,value);check.add(new VarInsnNode(Opcodes.ALOAD,entry));check.add(new VarInsnNode(Opcodes.ALOAD,value));
                        method.instructions.insertBefore(at,check);
                    } else if(at instanceof MethodInsnNode call&&(
                            call.name.equals("<init>")&&call.owner.equals(MAP+"$Node")
                            ||call.name.equals("putTreeVal")&&call.owner.equals(MAP+"$TreeBin"))) {
                        Type[] arguments=Type.getArgumentTypes(call.desc);
                        // Both exact JDK layouts carry (hash, key, value, ...). Never pass the uninitialized Node.
                        if(arguments.length<3||!arguments[1].getDescriptor().equals("Ljava/lang/Object;"))throw new IllegalStateException("CHM_NODE_LAYOUT");
                        int[] locals=new int[arguments.length];
                        for(int i=0;i<locals.length;i++) { locals[i]=method.maxLocals;method.maxLocals+=arguments[i].getSize(); }
                        InsnList check=new InsnList();
                        for(int i=locals.length-1;i>=0;i--)check.add(new VarInsnNode(arguments[i].getOpcode(Opcodes.ISTORE),locals[i]));
                        incoming(check,locals[1],locals[2]);
                        for(int i=0;i<locals.length;i++)check.add(new VarInsnNode(arguments[i].getOpcode(Opcodes.ILOAD),locals[i]));
                        method.instructions.insertBefore(at,check);
                    }
                }
                LabelNode start=new LabelNode(),end=new LabelNode(),refused=new LabelNode(),failed=new LabelNode();
                method.instructions.insert(start);
                method.instructions.add(end);method.instructions.add(refused);int error=method.maxLocals++;
                method.instructions.add(new VarInsnNode(Opcodes.ASTORE,error));
                method.instructions.add(new VarInsnNode(Opcodes.ALOAD,error));method.instructions.add(new VarInsnNode(Opcodes.ALOAD,0));
                method.instructions.add(new MethodInsnNode(Opcodes.INVOKESTATIC,BRIDGE,"ownsRefusal","(Ljava/lang/Throwable;Ljava/lang/Object;)Z",false));
                LabelNode ours=new LabelNode();method.instructions.add(new JumpInsnNode(Opcodes.IFNE,ours));
                method.instructions.add(new VarInsnNode(Opcodes.ALOAD,error));method.instructions.add(new InsnNode(Opcodes.ATHROW));method.instructions.add(ours);
                method.instructions.add(new VarInsnNode(Opcodes.ALOAD,error));
                if(!method.name.equals("replaceNode")) {
                    method.instructions.add(new InsnNode(Opcodes.ICONST_0));
                    method.instructions.add(new MethodInsnNode(Opcodes.INVOKESTATIC,BRIDGE,"publicResult","(Ljava/lang/Object;Z)Ljava/lang/Object;",false));
                }
                method.instructions.add(new InsnNode(Opcodes.ARETURN));
                method.instructions.add(failed);method.instructions.add(new VarInsnNode(Opcodes.ASTORE,error));
                method.instructions.add(new VarInsnNode(Opcodes.ALOAD,error));method.instructions.add(new InsnNode(Opcodes.ATHROW));
                method.tryCatchBlocks.add(new TryCatchBlockNode(start,end,refused,BRIDGE+"$Refused"));
                method.tryCatchBlocks.add(new TryCatchBlockNode(start,end,failed,null));count++;
            }
            if(count!=6)throw new IllegalStateException("CHM_METHOD_LAYOUT:"+count);
            int scopes=0;
            for(MethodNode method:node.methods)if(Set.of("putVal","replaceNode","computeIfAbsent","computeIfPresent","compute","merge","clear").contains(method.name)){
                scope(method);scopes++;
            }
            if(scopes!=7)throw new IllegalStateException("CHM_PUBLICATION_LAYOUT:"+scopes);
            ClassWriter writer=new ClassWriter(ClassWriter.COMPUTE_FRAMES|ClassWriter.COMPUTE_MAXS);node.accept(writer);
            installed=true;return writer.toByteArray();
        } catch(Throwable failure) { installed=false;System.err.println("RONOVA_CHM_SOURCE_UNAVAILABLE:"+failure);return null; }
    }
    private static void incoming(InsnList code,int key,int value) {
        code.add(new VarInsnNode(Opcodes.ALOAD,0));code.add(new VarInsnNode(Opcodes.ALOAD,key));code.add(new VarInsnNode(Opcodes.ALOAD,value));
        code.add(new MethodInsnNode(Opcodes.INVOKESTATIC,BRIDGE,"checkIncoming","(Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;)V",false));
    }
    private static void scope(MethodNode method){
        int ticket=method.maxLocals++,error=method.maxLocals++;
        LabelNode start=new LabelNode(),end=new LabelNode(),handler=new LabelNode();
        AbstractInsnNode[] original=method.instructions.toArray();InsnList enter=new InsnList();
        enter.add(new VarInsnNode(Opcodes.ALOAD,0));enter.add(new MethodInsnNode(Opcodes.INVOKESTATIC,BRIDGE,"enterConcurrent","(Ljava/lang/Object;)Ljava/lang/Object;",false));
        enter.add(new VarInsnNode(Opcodes.ASTORE,ticket));enter.add(start);method.instructions.insert(enter);
        for(AbstractInsnNode at:original)if(at.getOpcode()>=Opcodes.IRETURN&&at.getOpcode()<=Opcodes.RETURN)method.instructions.insertBefore(at,exit(ticket));
        method.instructions.add(end);method.instructions.add(handler);method.instructions.add(new VarInsnNode(Opcodes.ASTORE,error));method.instructions.add(exit(ticket));
        method.instructions.add(new VarInsnNode(Opcodes.ALOAD,error));method.instructions.add(new InsnNode(Opcodes.ATHROW));
        method.tryCatchBlocks.add(new TryCatchBlockNode(start,end,handler,null));
    }
    private static InsnList exit(int ticket){
        InsnList code=new InsnList();code.add(new VarInsnNode(Opcodes.ALOAD,ticket));
        code.add(new MethodInsnNode(Opcodes.INVOKESTATIC,BRIDGE,"exitConcurrent","(Ljava/lang/Object;)V",false));return code;
    }
}
