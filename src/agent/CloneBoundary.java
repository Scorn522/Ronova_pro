package dev.ronova.pro.agent;

import jdk.internal.org.objectweb.asm.*;

/** Only Object.clone's native operation is replaced by an equivalent bootstrap call that observes its actual result. */
final class CloneBoundary {
    static byte[] transform(ClassLoader loader,byte[] bytes) {
        ClassReader reader=new ClassReader(bytes);
        // An invokespecial clone must reference this exact name/descriptor.
        // Absence in the real constant pool means there is nothing to rewrite.
        char[] characters=new char[reader.getMaxStringLength()];boolean possible=false;
        for(int index=1;index<reader.getItemCount();index++){
            int at=reader.getItem(index);if(at==0)continue;
            int tag=reader.readByte(at-1);if(tag!=10&&tag!=11)continue;
            int nameType=reader.getItem(reader.readUnsignedShort(at+2));
            if("clone".equals(reader.readUTF8(nameType,characters))
                    &&"()Ljava/lang/Object;".equals(reader.readUTF8(nameType+2,characters))){possible=true;break;}
        }
        if(!possible)return null;
        boolean[] changed={false};ClassWriter writer=new ClassWriter(reader,0);
        reader.accept(new ClassVisitor(Opcodes.ASM8,writer){
            // JDK 17 exposes this attribute through its experimental visitor
            // method. Delegate unchanged instead of asking ASM8 to interpret it.
            @Override public void visitPermittedSubclassExperimental(String name){writer.visitPermittedSubclassExperimental(name);}
            @Override public MethodVisitor visitMethod(int access,String name,String descriptor,String signature,String[] exceptions){
                return new MethodVisitor(Opcodes.ASM8,super.visitMethod(access,name,descriptor,signature,exceptions)){
                    @Override public void visitMethodInsn(int opcode,String owner,String method,String desc,boolean itf){
                        if(opcode==Opcodes.INVOKESPECIAL&&method.equals("clone")&&desc.equals("()Ljava/lang/Object;")&&nativeObjectClone(loader,owner)){
                            changed[0]=true;super.visitMethodInsn(Opcodes.INVOKESTATIC,"dev/ronova/pro/bootstrap/ControlBridge","cloneObject","(Ljava/lang/Object;)Ljava/lang/Object;",false);
                        }else super.visitMethodInsn(opcode,owner,method,desc,itf);
                    }
                };
            }
        },0);
        return changed[0]?writer.toByteArray():null;
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
