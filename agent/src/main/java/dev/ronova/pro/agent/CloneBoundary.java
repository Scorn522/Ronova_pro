package dev.ronova.pro.agent;

import jdk.internal.org.objectweb.asm.*;
import jdk.internal.org.objectweb.asm.tree.*;

/** Only Object.clone's native operation is replaced by an equivalent bootstrap call that observes its actual result. */
final class CloneBoundary {
    static byte[] transform(ClassLoader loader,byte[] bytes) {
        ClassNode node=new ClassNode();new ClassReader(bytes).accept(node,0);boolean changed=false;
        for(MethodNode method:node.methods)for(AbstractInsnNode instruction:method.instructions.toArray())
            if(instruction instanceof MethodInsnNode call&&call.getOpcode()==Opcodes.INVOKESPECIAL
                    &&call.name.equals("clone")&&call.desc.equals("()Ljava/lang/Object;")&&nativeObjectClone(loader,call.owner)) {
                call.setOpcode(Opcodes.INVOKESTATIC);call.owner="dev/ronova/pro/bootstrap/TaskBridge";
                call.name="cloneObject";call.desc="(Ljava/lang/Object;)Ljava/lang/Object;";call.itf=false;changed=true;
            }
        if(!changed)return null;ClassWriter writer=new ClassWriter(0);node.accept(writer);return writer.toByteArray();
    }
    private static boolean nativeObjectClone(ClassLoader loader,String owner) {
        if(owner.equals("java/lang/Object"))return true;
        try {
            // javac may name Cow or another superclass although resolution reaches Object.clone.
            // Resolve without initialization and retain every real override's behavior.
            for(Class<?> type=Class.forName(owner.replace('/','.'),false,loader);type!=null;type=type.getSuperclass()) {
                for(var method:type.getDeclaredMethods())if(method.getName().equals("clone")&&method.getParameterCount()==0)
                    return type==Object.class&&java.lang.reflect.Modifier.isNative(method.getModifiers());
            }
        } catch(ClassNotFoundException|RuntimeException|LinkageError unavailable) { }
        return false;
    }
}
