package dev.ronova.pro.agent;

import java.util.*;
import jdk.internal.org.objectweb.asm.*;
import jdk.internal.org.objectweb.asm.tree.*;

/** Cold handle factories; VarHandle data operations are guarded at Mod call sites. */
final class HandleWriteBoundary {
    private static final String BRIDGE="dev/ronova/pro/bootstrap/BackingBridge";
    private static final List<String> TARGETS=createTargets();
    static List<String> targets() {return TARGETS;}
    private static final Set<String> INSTALLED=java.util.concurrent.ConcurrentHashMap.newKeySet();
    static boolean complete() {return INSTALLED.containsAll(TARGETS);}
    static boolean installed(String name) {return INSTALLED.contains(name);}
    static int installedCount() {return INSTALLED.size();}
    private static List<String> createTargets() {
        List<String> names=new ArrayList<>(List.of("java/lang/invoke/MethodHandles","java/lang/invoke/MethodHandles$Lookup"));
        return names;
    }
    static byte[] transform(ClassLoader loader,String name,byte[] bytes) {
        if(name==null||!targets().contains(name))return null;
        INSTALLED.remove(name);
        ClassNode node=new ClassNode();new ClassReader(bytes).accept(node,0);boolean changed=false;
        for(MethodNode method:node.methods) {
            if(name.equals("java/lang/invoke/MethodHandles$Lookup")&&method.name.equals("findVirtual")&&method.desc.equals("(Ljava/lang/Class;Ljava/lang/String;Ljava/lang/invoke/MethodType;)Ljava/lang/invoke/MethodHandle;")){
                for(AbstractInsnNode at:method.instructions.toArray())if(at.getOpcode()==Opcodes.ARETURN){
                    InsnList code=new InsnList();code.add(new VarInsnNode(Opcodes.ALOAD,1));code.add(new VarInsnNode(Opcodes.ALOAD,2));code.add(new VarInsnNode(Opcodes.ALOAD,3));
                    code.add(new MethodInsnNode(Opcodes.INVOKESTATIC,"dev/ronova/pro/bootstrap/GuardedCalls","wrapUnsafeHandle","(Ljava/lang/invoke/MethodHandle;Ljava/lang/Class;Ljava/lang/String;Ljava/lang/invoke/MethodType;)Ljava/lang/invoke/MethodHandle;",false));method.instructions.insertBefore(at,code);
                }changed=true;continue;
            }
            if(name.equals("java/lang/invoke/MethodHandles$Lookup")&&Set.of("findStatic","unreflect").contains(method.name)) {
                for(AbstractInsnNode at:method.instructions.toArray())if(at.getOpcode()==Opcodes.ARETURN) {
                    InsnList code=new InsnList();code.add(new VarInsnNode(Opcodes.ALOAD,1));
                    if(method.name.equals("unreflect")){
                        code.add(new MethodInsnNode(Opcodes.INVOKESTATIC,BRIDGE,"wrapArrayMethod","(Ljava/lang/invoke/MethodHandle;Ljava/lang/reflect/Method;)Ljava/lang/invoke/MethodHandle;",false));
                        code.add(new VarInsnNode(Opcodes.ALOAD,1));code.add(new MethodInsnNode(Opcodes.INVOKESTATIC,"dev/ronova/pro/bootstrap/GuardedCalls","wrapUnsafeHandle","(Ljava/lang/invoke/MethodHandle;Ljava/lang/reflect/Method;)Ljava/lang/invoke/MethodHandle;",false));
                    }
                    else {code.add(new VarInsnNode(Opcodes.ALOAD,2));code.add(new MethodInsnNode(Opcodes.INVOKESTATIC,BRIDGE,"wrapArrayMethod","(Ljava/lang/invoke/MethodHandle;Ljava/lang/Class;Ljava/lang/String;)Ljava/lang/invoke/MethodHandle;",false));}
                    method.instructions.insertBefore(at,code);
                }changed=true;continue;
            }
            if(name.equals("java/lang/invoke/MethodHandles")&&method.name.equals("arrayElementSetter")) {
                for(AbstractInsnNode at:method.instructions.toArray())if(at.getOpcode()==Opcodes.ARETURN)method.instructions.insertBefore(at,
                    new MethodInsnNode(Opcodes.INVOKESTATIC,BRIDGE,"wrapArraySetter","(Ljava/lang/invoke/MethodHandle;)Ljava/lang/invoke/MethodHandle;",false));changed=true;continue;
            }
            if(name.equals("java/lang/invoke/MethodHandles$Lookup")&&Set.of("findSetter","findStaticSetter","unreflectSetter").contains(method.name)) {
                for(AbstractInsnNode at:method.instructions.toArray())if(at.getOpcode()==Opcodes.ARETURN) {
                    InsnList code=new InsnList();code.add(new VarInsnNode(Opcodes.ALOAD,1));
                    if(method.name.equals("unreflectSetter"))code.add(new MethodInsnNode(Opcodes.INVOKESTATIC,BRIDGE,"wrapSetter","(Ljava/lang/invoke/MethodHandle;Ljava/lang/reflect/Field;)Ljava/lang/invoke/MethodHandle;",false));
                    else {code.add(new VarInsnNode(Opcodes.ALOAD,2));code.add(new MethodInsnNode(Opcodes.INVOKESTATIC,BRIDGE,"wrapSetter","(Ljava/lang/invoke/MethodHandle;Ljava/lang/Class;Ljava/lang/String;)Ljava/lang/invoke/MethodHandle;",false));}
                    method.instructions.insertBefore(at,code);
                }changed=true;continue;
            }
        }
        if(!changed)return null;ControlClassWriter writer=new ControlClassWriter(loader,node);node.accept(writer);INSTALLED.add(name);return writer.toByteArray();
    }
}
