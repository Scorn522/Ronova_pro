package dev.ronova.pro.agent;

import java.util.*;
import jdk.internal.org.objectweb.asm.*;
import jdk.internal.org.objectweb.asm.tree.*;

/** Guards actual native write calls inside the bootstrap Unsafe's Java implementations. */
final class InternalUnsafeBoundary {
    private static final String TARGET="jdk/internal/misc/Unsafe",TASKS="dev/ronova/pro/bootstrap/TaskBridge",BACKING="dev/ronova/pro/bootstrap/BackingBridge";
    private InternalUnsafeBoundary(){}
    static byte[] transform(ClassLoader loader,String name,byte[] bytes){
        if(loader!=null||!TARGET.equals(name))return null;
        ClassNode node=new ClassNode();new ClassReader(bytes).accept(node,0);Set<String> natives=new HashSet<>();
        for(MethodNode method:node.methods)if((method.access&Opcodes.ACC_NATIVE)!=0)natives.add(method.name+method.desc);
        boolean changed=false;
        for(MethodNode method:node.methods){
            if((method.access&(Opcodes.ACC_NATIVE|Opcodes.ACC_ABSTRACT))!=0)continue;
            for(AbstractInsnNode at:method.instructions.toArray())if(at instanceof MethodInsnNode call&&call.owner.equals(TARGET)&&natives.contains(call.name+call.desc)){
                Type[] arguments=Type.getArgumentTypes(call.desc);int destination=-1,offset=-1,length=-1;
                if(call.name.equals("setMemory0")&&call.desc.equals("(Ljava/lang/Object;JJB)V")){destination=0;offset=1;length=2;}
                else if(call.name.equals("copyMemory0")&&call.desc.equals("(Ljava/lang/Object;JLjava/lang/Object;JJ)V")
                        ||call.name.equals("copySwapMemory0")&&call.desc.equals("(Ljava/lang/Object;JLjava/lang/Object;JJJ)V")){destination=2;offset=3;length=4;}
                boolean scalar=(call.name.startsWith("put")||call.name.startsWith("compareAnd")||call.name.startsWith("weakCompareAnd")||call.name.startsWith("getAnd"))
                        &&arguments.length>=3&&arguments[0].getDescriptor().equals("Ljava/lang/Object;")&&arguments[1].getSort()==Type.LONG;
                boolean reading=call.name.startsWith("get")&&!call.name.startsWith("getAnd")&&arguments.length==2
                        &&arguments[0].getDescriptor().equals("Ljava/lang/Object;")&&arguments[1].getSort()==Type.LONG;
                if(destination<0&&!scalar&&!reading)continue;
                int[] slots=new int[arguments.length];for(int i=0;i<slots.length;i++){slots[i]=method.maxLocals;method.maxLocals+=arguments[i].getSize();}
                int access=call.getOpcode()==Opcodes.INVOKESTATIC?-1:method.maxLocals++;InsnList guard=new InsnList();LabelNode admitted=new LabelNode();
                for(int i=slots.length-1;i>=0;i--)guard.add(new VarInsnNode(arguments[i].getOpcode(Opcodes.ISTORE),slots[i]));
                if(access>=0)guard.add(new VarInsnNode(Opcodes.ASTORE,access));
                if(reading){
                    if(access>=0)guard.add(new VarInsnNode(Opcodes.ALOAD,access));for(int i=0;i<slots.length;i++)guard.add(new VarInsnNode(arguments[i].getOpcode(Opcodes.ILOAD),slots[i]));
                    method.instructions.insertBefore(call,guard);MemoryWriteScope.nativeReadCall(method,call,arguments,slots,kind(Type.getReturnType(call.desc)));changed=true;continue;
                }
                LabelNode guardStart=new LabelNode();guard.add(guardStart);
                guard.add(new MethodInsnNode(Opcodes.INVOKESTATIC,TASKS,"unsafeControlScope","()Z",false));guard.add(new JumpInsnNode(Opcodes.IFNE,admitted));
                Type proposed=arguments[arguments.length-1];
                if(destination>=0){
                    guard.add(new VarInsnNode(Opcodes.ALOAD,slots[destination]));guard.add(new VarInsnNode(Opcodes.LLOAD,slots[offset]));guard.add(new VarInsnNode(Opcodes.LLOAD,slots[length]));
                    guard.add(new MethodInsnNode(Opcodes.INVOKESTATIC,TASKS,"unsafeMemoryAllowed","(Ljava/lang/Object;JJ)Z",false));
                }else{
                    guard.add(new VarInsnNode(Opcodes.ALOAD,slots[0]));guard.add(new VarInsnNode(Opcodes.LLOAD,slots[1]));guard.add(new VarInsnNode(proposed.getOpcode(Opcodes.ILOAD),slots[slots.length-1]));ControlClassWriter.box(guard,proposed);
                    guard.add(new LdcInsnNode(call.name));guard.add(new LdcInsnNode(kind(proposed)));guard.add(new MethodInsnNode(Opcodes.INVOKESTATIC,BACKING,"nativeWriteAllowed","(Ljava/lang/Object;JLjava/lang/Object;Ljava/lang/String;Ljava/lang/String;)Z",false));
                }
                guard.add(new JumpInsnNode(Opcodes.IFNE,admitted));denied(guard,method);guard.add(admitted);
                if(access>=0)guard.add(new VarInsnNode(Opcodes.ALOAD,access));for(int i=0;i<slots.length;i++)guard.add(new VarInsnNode(arguments[i].getOpcode(Opcodes.ILOAD),slots[i]));
                method.instructions.insertBefore(call,guard);
                if(destination>=0)MemoryWriteScope.nativeBulkCall(method,call,slots[destination],slots[offset],slots[length],destination==2?slots[0]:-1,destination==2?slots[1]:-1);
                else MemoryWriteScope.nativeCall(method,call,arguments,slots,kind(proposed),guardStart);changed=true;
            }
        }
        if(!changed)return null;ControlClassWriter writer=new ControlClassWriter(loader,node);node.accept(writer);return writer.toByteArray();
    }
    private static void denied(InsnList code,MethodNode method){
        Type result=Type.getReturnType(method.desc);
        if(result.getSort()==Type.VOID){code.add(new InsnNode(Opcodes.RETURN));return;}
        if(result.getSort()==Type.BOOLEAN&&(method.name.startsWith("compareAndSet")||method.name.startsWith("weakCompareAndSet"))){code.add(new InsnNode(Opcodes.ICONST_0));code.add(new InsnNode(Opcodes.IRETURN));return;}
        Type[] arguments=Type.getArgumentTypes(method.desc);
        if(arguments.length<2||!arguments[0].getDescriptor().equals("Ljava/lang/Object;")||arguments[1].getSort()!=Type.LONG)throw new IllegalStateException("UNSAFE_NATIVE_CALLER_LAYOUT:"+method.name+method.desc);
        int receiver=(method.access&Opcodes.ACC_STATIC)==0?1:0;code.add(new VarInsnNode(Opcodes.ALOAD,receiver));code.add(new VarInsnNode(Opcodes.LLOAD,receiver+1));code.add(new LdcInsnNode(kind(result)));
        code.add(new MethodInsnNode(Opcodes.INVOKESTATIC,BACKING,"retained","(Ljava/lang/Object;JLjava/lang/String;)Ljava/lang/Object;",false));
        if(result.getSort()==Type.OBJECT||result.getSort()==Type.ARRAY)code.add(new TypeInsnNode(Opcodes.CHECKCAST,result.getInternalName()));
        else{String boxed="java/lang/"+(result.getSort()==Type.INT?"Integer":result.getSort()==Type.CHAR?"Character":kind(result));code.add(new TypeInsnNode(Opcodes.CHECKCAST,boxed));
            code.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL,boxed,result.getClassName()+"Value","()"+result.getDescriptor(),false));}
        code.add(new InsnNode(result.getOpcode(Opcodes.IRETURN)));
    }
    private static String kind(Type type){return switch(type.getSort()){case Type.BOOLEAN->"Boolean";case Type.BYTE->"Byte";case Type.CHAR->"Char";case Type.SHORT->"Short";case Type.INT->"Int";case Type.FLOAT->"Float";case Type.LONG->"Long";case Type.DOUBLE->"Double";default->"Object";};}
}
