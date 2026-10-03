package dev.ronova.pro.agent;

import java.util.Set;
import jdk.internal.org.objectweb.asm.*;
import jdk.internal.org.objectweb.asm.tree.*;

/** Records the actual VM load/unload operation, including JNI_OnLoad/OnUnload. */
final class NativeLibraryBoundary {
    private static final String LIBRARIES="jdk/internal/loader/NativeLibraries";
    private static final String BRIDGE="dev/ronova/pro/bootstrap/NativeControl";
    static final Set<String> TARGETS=Set.of(LIBRARIES+"$NativeLibraryImpl",LIBRARIES+"$Unloader");
    static byte[] transform(ClassLoader loader,String name,byte[] bytes){
        if(loader!=null||!TARGETS.contains(name))return null;
        boolean loading=name.endsWith("$NativeLibraryImpl");
        ClassNode node=new ClassNode();new ClassReader(bytes).accept(node,0);boolean changed=false;
        for(MethodNode method:node.methods){
            if(loading&&method.name.equals("unloader")&&method.desc.equals("()Ljava/lang/Runnable;")){
                for(AbstractInsnNode at:method.instructions.toArray())if(at.getOpcode()==Opcodes.ARETURN){
                    InsnList code=new InsnList();code.add(new InsnNode(Opcodes.DUP));code.add(new VarInsnNode(Opcodes.ALOAD,0));code.add(new InsnNode(Opcodes.SWAP));
                    code.add(new VarInsnNode(Opcodes.ALOAD,0));code.add(new FieldInsnNode(Opcodes.GETFIELD,name,"handle","J"));
                    code.add(new MethodInsnNode(Opcodes.INVOKESTATIC,BRIDGE,"libraryUnloader","(Ljava/lang/Object;Ljava/lang/Object;J)V",false));method.instructions.insertBefore(at,code);changed=true;
                }
                continue;
            }
            if(!method.name.equals(loading?"open":"run")||!method.desc.equals(loading?"()Z":"()V"))continue;
            for(AbstractInsnNode at:method.instructions.toArray()){
                if(!(at instanceof MethodInsnNode call)||!call.owner.equals(LIBRARIES)||!call.name.equals(loading?"load":"unload"))continue;
                int token=method.maxLocals++,result=method.maxLocals++,error=method.maxLocals++;
                LabelNode start=new LabelNode(),end=new LabelNode(),handler=new LabelNode(),resume=new LabelNode();
                InsnList before=new InsnList();before.add(new VarInsnNode(Opcodes.ALOAD,0));
                if(loading){before.add(new VarInsnNode(Opcodes.ALOAD,0));before.add(new FieldInsnNode(Opcodes.GETFIELD,name,"fromClass","Ljava/lang/Class;"));}
                else {before.add(new VarInsnNode(Opcodes.ALOAD,0));before.add(new FieldInsnNode(Opcodes.GETFIELD,name,"handle","J"));}
                before.add(new MethodInsnNode(Opcodes.INVOKESTATIC,BRIDGE,loading?"libraryBegin":"libraryUnloadBegin",
                        loading?"(Ljava/lang/Object;Ljava/lang/Class;)J":"(Ljava/lang/Object;J)J",false));
                // A long token occupies two locals; keep result/error beyond it.
                method.maxLocals++;result++;error++;
                before.add(new VarInsnNode(Opcodes.LSTORE,token));before.add(start);method.instructions.insertBefore(at,before);
                InsnList after=new InsnList();if(loading)after.add(new VarInsnNode(Opcodes.ISTORE,result));after.add(end);
                finish(after,name,token,loading,result,true);if(loading)after.add(new VarInsnNode(Opcodes.ILOAD,result));
                after.add(new JumpInsnNode(Opcodes.GOTO,resume));after.add(handler);after.add(new VarInsnNode(Opcodes.ASTORE,error));
                finish(after,name,token,loading,result,false);after.add(new VarInsnNode(Opcodes.ALOAD,error));after.add(new InsnNode(Opcodes.ATHROW));after.add(resume);
                method.instructions.insert(at,after);method.tryCatchBlocks.add(0,new TryCatchBlockNode(start,end,handler,null));changed=true;
            }
        }
        if(!changed)throw new IllegalStateException("NATIVE_LIBRARY_OPERATION_UNOBSERVED:"+name);
        ControlClassWriter writer=new ControlClassWriter(loader,node);node.accept(writer);return writer.toByteArray();
    }
    private static void finish(InsnList code,String name,int token,boolean loading,int result,boolean normal){
        code.add(new VarInsnNode(Opcodes.LLOAD,token));code.add(new VarInsnNode(Opcodes.ALOAD,0));
        code.add(new FieldInsnNode(Opcodes.GETFIELD,name,"handle","J"));
        code.add(normal?(loading?new VarInsnNode(Opcodes.ILOAD,result):new InsnNode(Opcodes.ICONST_1)):new InsnNode(Opcodes.ICONST_0));
        code.add(new MethodInsnNode(Opcodes.INVOKESTATIC,BRIDGE,"libraryEnd","(JJZ)V",false));
    }
}
