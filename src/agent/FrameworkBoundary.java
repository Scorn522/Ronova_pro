package dev.ronova.pro.agent;

import jdk.internal.org.objectweb.asm.*;
import jdk.internal.org.objectweb.asm.tree.*;
import dev.ronova.pro.bootstrap.FrameworkBuffers;

/** Exact framework allocations and one early-window frame, never a Mod exemption. */
final class FrameworkBoundary {
    private static final String BRIDGE="dev/ronova/pro/bootstrap/FrameworkBuffers";
    static boolean target(String name){
        return "org/objectweb/asm/ByteVector".equals(name)
                ||"net/minecraftforge/fml/earlydisplay/SimpleBufferBuilder".equals(name)
                ||"net/minecraftforge/fml/earlydisplay/DisplayWindow".equals(name);
    }
    static byte[] transform(ClassLoader loader,Module module,String name,byte[] bytes){
        boolean vector=name.equals("org/objectweb/asm/ByteVector");
        boolean builder=name.equals("net/minecraftforge/fml/earlydisplay/SimpleBufferBuilder");
        boolean display=name.equals("net/minecraftforge/fml/earlydisplay/DisplayWindow");
        if(loader==null||module==null||!vector&&!builder&&!display)return null;
        if(!vector&&(!module.isNamed()||!"fmlearlydisplay".equals(module.getName())))return null;
        FrameworkBuffers.expect(name.replace('/','.'),loader,module);
        if(vector)ExternalCodeImages.expect(name.replace('/','.'),loader,module);
        ClassNode node=new ClassNode();new ClassReader(bytes).accept(node,0);boolean changed=false;
        for(MethodNode method:node.methods){
            if(vector)for(AbstractInsnNode at:method.instructions.toArray())
                if(at instanceof IntInsnNode array&&at.getOpcode()==Opcodes.NEWARRAY&&array.operand==Opcodes.T_BYTE){
                    InsnList code=new InsnList();code.add(new LdcInsnNode(Type.getObjectType(name)));
                    code.add(new MethodInsnNode(Opcodes.INVOKESTATIC,BRIDGE,"generated","([BLjava/lang/Class;)[B",false));
                    method.instructions.insert(at,code);changed=true;
                }
            if(builder)for(AbstractInsnNode at:method.instructions.toArray())
                if(at instanceof MethodInsnNode call&&call.getOpcode()==Opcodes.INVOKESTATIC
                        &&call.owner.equals("org/lwjgl/system/MemoryUtil")&&call.name.equals("memByteBuffer")
                        &&call.desc.equals("(JI)Ljava/nio/ByteBuffer;")){
                    InsnList code=new InsnList();code.add(new InsnNode(Opcodes.DUP));code.add(new LdcInsnNode(Type.getObjectType(name)));
                    code.add(new MethodInsnNode(Opcodes.INVOKESTATIC,BRIDGE,"earlyBuffer","(Ljava/nio/Buffer;Ljava/lang/Class;)V",false));
                    method.instructions.insert(at,code);changed=true;
                }
            if(display&&method.name.equals("paintFramebuffer")&&method.desc.equals("()V")){
                int token=method.maxLocals++,failure=method.maxLocals++;
                LabelNode start=new LabelNode(),end=new LabelNode(),handler=new LabelNode();
                InsnList enter=new InsnList();enter.add(new LdcInsnNode(Type.getObjectType(name)));
                enter.add(new MethodInsnNode(Opcodes.INVOKESTATIC,BRIDGE,"renderEnter","(Ljava/lang/Class;)Ljava/lang/Object;",false));
                enter.add(new VarInsnNode(Opcodes.ASTORE,token));enter.add(start);method.instructions.insert(enter);
                for(AbstractInsnNode at:method.instructions.toArray())if(at.getOpcode()==Opcodes.RETURN)method.instructions.insertBefore(at,exit(token));
                method.instructions.add(end);method.instructions.add(handler);method.instructions.add(new VarInsnNode(Opcodes.ASTORE,failure));
                method.instructions.add(exit(token));method.instructions.add(new VarInsnNode(Opcodes.ALOAD,failure));method.instructions.add(new InsnNode(Opcodes.ATHROW));
                method.tryCatchBlocks.add(new TryCatchBlockNode(start,end,handler,"java/lang/Throwable"));changed=true;
            }
        }
        if(!changed)return null;ControlClassWriter writer=new ControlClassWriter(loader,node);node.accept(writer);return writer.toByteArray();
    }
    private static InsnList exit(int token){
        InsnList code=new InsnList();code.add(new VarInsnNode(Opcodes.ALOAD,token));
        code.add(new MethodInsnNode(Opcodes.INVOKESTATIC,BRIDGE,"renderExit","(Ljava/lang/Object;)V",false));return code;
    }
}
