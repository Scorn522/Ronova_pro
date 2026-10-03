package dev.ronova.pro.agent;

import java.util.*;
import jdk.internal.org.objectweb.asm.*;
import jdk.internal.org.objectweb.asm.tree.*;

/** Actual platform buffer views, synchronous uses and original cleanup actions. */
final class BufferBoundary {
    private static final String BRIDGE="dev/ronova/pro/bootstrap/ResourceBridge";
    static final Set<String> TARGETS=targets();
    private static Set<String> targets(){
        Set<String> result=new HashSet<>(List.of("java/nio/Buffer","java/nio/ByteBuffer","java/nio/MappedByteBuffer",
                "java/nio/DirectByteBuffer","java/nio/DirectByteBufferR","java/nio/HeapByteBuffer","java/nio/HeapByteBufferR","java/nio/DirectByteBuffer$Deallocator",
                "sun/nio/ch/FileChannelImpl$Unmapper","jdk/internal/ref/Cleaner"));
        for(String kind:List.of("Char","Short","Int","Long","Float","Double")){
            result.add("java/nio/"+kind+"Buffer");
            result.add("java/nio/Heap"+kind+"Buffer");result.add("java/nio/Heap"+kind+"BufferR");
            for(String suffix:List.of("U","S","RU","RS"))result.add("java/nio/Direct"+kind+"Buffer"+suffix);
            for(String suffix:List.of("B","L","RB","RL"))result.add("java/nio/ByteBufferAs"+kind+"Buffer"+suffix);
        }
        return Set.copyOf(result);
    }
    static byte[] transform(ClassLoader loader,String name,byte[] bytes){
        if(loader!=null||!TARGETS.contains(name))return null;
        ClassNode node=new ClassNode();new ClassReader(bytes).accept(node,0);
        if(name.equals("jdk/internal/ref/Cleaner")){
            for(MethodNode method:node.methods)if(method.name.equals("clean")&&method.desc.equals("()V")){
                InsnList guard=new InsnList();guard.add(new VarInsnNode(Opcodes.ALOAD,0));
                guard.add(new MethodInsnNode(Opcodes.INVOKESTATIC,BRIDGE,"bufferCleanerAllowed","(Ljava/lang/Object;)Z",false));
                LabelNode allowed=new LabelNode();guard.add(new JumpInsnNode(Opcodes.IFNE,allowed));guard.add(new InsnNode(Opcodes.RETURN));guard.add(allowed);method.instructions.insert(guard);
            }
        }else if(name.equals("java/nio/DirectByteBuffer$Deallocator")||name.equals("sun/nio/ch/FileChannelImpl$Unmapper")){
            for(MethodNode method:node.methods){
                if(method.desc.equals("()V")&&method.name.equals(name.endsWith("$Unmapper")?"unmap":"run"))action(node.name,method);
                else if(name.endsWith("$Unmapper")&&(method.name.equals("address")&&method.desc.equals("()J")
                        ||method.name.equals("fileDescriptor")&&method.desc.equals("()Ljava/io/FileDescriptor;"))){
                    InsnList code=new InsnList();code.add(new VarInsnNode(Opcodes.ALOAD,0));code.add(new LdcInsnNode(method.name));
                    code.add(new MethodInsnNode(Opcodes.INVOKESTATIC,BRIDGE,"mappingExposed","(Ljava/lang/Object;Ljava/lang/String;)V",false));method.instructions.insert(code);
                }
            }
        }else for(MethodNode method:node.methods){
            if((method.access&(Opcodes.ACC_STATIC|Opcodes.ACC_NATIVE|Opcodes.ACC_ABSTRACT))!=0)continue;
            if(method.name.equals("<init>")){
                if(name.startsWith("java/nio/Direct")||name.startsWith("java/nio/ByteBufferAs"))constructed(node,method);
            }else scope(node.name,method);
        }
        ControlClassWriter writer=new ControlClassWriter(loader,node);node.accept(writer);return writer.toByteArray();
    }
    private static void constructed(ClassNode node,MethodNode method){
        for(AbstractInsnNode at:method.instructions.toArray()){
            if(at instanceof MethodInsnNode call&&call.owner.equals("jdk/internal/ref/Cleaner")&&call.name.equals("create")
                    &&call.desc.equals("(Ljava/lang/Object;Ljava/lang/Runnable;)Ljdk/internal/ref/Cleaner;")){
                int action=method.maxLocals++;InsnList before=new InsnList();before.add(new VarInsnNode(Opcodes.ASTORE,action));
                before.add(new InsnNode(Opcodes.DUP));before.add(new VarInsnNode(Opcodes.ALOAD,action));
                before.add(new MethodInsnNode(Opcodes.INVOKESTATIC,BRIDGE,"bufferCleanup","(Ljava/lang/Object;Ljava/lang/Runnable;)Ljava/lang/Runnable;",false));method.instructions.insertBefore(call,before);
                InsnList after=new InsnList();after.add(new InsnNode(Opcodes.DUP));after.add(new VarInsnNode(Opcodes.ALOAD,0));
                after.add(new MethodInsnNode(Opcodes.INVOKESTATIC,BRIDGE,"bufferCleaner","(Ljava/lang/Object;Ljava/nio/Buffer;)V",false));method.instructions.insert(call,after);
            }
            if(at.getOpcode()==Opcodes.RETURN){
                InsnList code=new InsnList();code.add(new VarInsnNode(Opcodes.ALOAD,0));code.add(new LdcInsnNode(Type.getObjectType(node.name)));
                code.add(new MethodInsnNode(Opcodes.INVOKESTATIC,BRIDGE,"bufferConstructed","(Ljava/nio/Buffer;Ljava/lang/Class;)V",false));method.instructions.insertBefore(at,code);
            }
        }
        if(node.name.equals("java/nio/DirectByteBuffer"))allocationScope(node,method);
    }
    private static void allocationScope(ClassNode node,MethodNode method){
        AbstractInsnNode initialized=null;
        for(AbstractInsnNode at:method.instructions.toArray())if(at instanceof MethodInsnNode call&&call.getOpcode()==Opcodes.INVOKESPECIAL&&call.name.equals("<init>")&&(call.owner.equals(node.name)||call.owner.equals(node.superName))){initialized=at;break;}
        if(initialized==null)throw new IllegalStateException("BUFFER_CONSTRUCTOR_INITIALIZATION_UNAVAILABLE");
        int token=method.maxLocals++,failure=method.maxLocals++;LabelNode start=new LabelNode(),end=new LabelNode(),handler=new LabelNode();
        InsnList entry=new InsnList();entry.add(new VarInsnNode(Opcodes.ALOAD,0));entry.add(new MethodInsnNode(Opcodes.INVOKESTATIC,BRIDGE,"bufferAllocationEnter","(Ljava/nio/Buffer;)Ljava/lang/Object;",false));entry.add(new VarInsnNode(Opcodes.ASTORE,token));entry.add(start);
        method.instructions.insert(initialized,entry);
        for(AbstractInsnNode at:method.instructions.toArray())if(at.getOpcode()==Opcodes.RETURN)method.instructions.insertBefore(at,allocationFinish(token,-1));
        method.instructions.add(end);method.instructions.add(handler);method.instructions.add(new VarInsnNode(Opcodes.ASTORE,failure));method.instructions.add(allocationFinish(token,failure));method.instructions.add(new VarInsnNode(Opcodes.ALOAD,failure));method.instructions.add(new InsnNode(Opcodes.ATHROW));
        method.tryCatchBlocks.add(new TryCatchBlockNode(start,end,handler,"java/lang/Throwable"));
    }
    private static InsnList allocationFinish(int token,int failure){InsnList code=new InsnList();code.add(new VarInsnNode(Opcodes.ALOAD,token));
        if(failure<0)code.add(new InsnNode(Opcodes.ACONST_NULL));else code.add(new VarInsnNode(Opcodes.ALOAD,failure));code.add(new MethodInsnNode(Opcodes.INVOKESTATIC,BRIDGE,"bufferAllocationExit","(Ljava/lang/Object;Ljava/lang/Throwable;)V",false));return code;}
    static void scope(String owner,MethodNode method){
        int token=method.maxLocals++,failure=method.maxLocals++;
        LabelNode start=new LabelNode(),end=new LabelNode(),handler=new LabelNode();
        InsnList entry=new InsnList();entry.add(new VarInsnNode(Opcodes.ALOAD,0));
        Type[] arguments=Type.getArgumentTypes(method.desc);
        entry.add(new LdcInsnNode(arguments.length));entry.add(new TypeInsnNode(Opcodes.ANEWARRAY,"java/lang/Object"));
        int slot=1,index=0;
        for(Type argument:arguments){
            entry.add(new InsnNode(Opcodes.DUP));entry.add(new LdcInsnNode(index++));entry.add(new VarInsnNode(argument.getOpcode(Opcodes.ILOAD),slot));
            if(argument.getSort()!=Type.OBJECT&&argument.getSort()!=Type.ARRAY){
                String box=switch(argument.getSort()){case Type.BOOLEAN->"Boolean";case Type.BYTE->"Byte";case Type.CHAR->"Character";case Type.SHORT->"Short";
                    case Type.INT->"Integer";case Type.LONG->"Long";case Type.FLOAT->"Float";case Type.DOUBLE->"Double";default->throw new IllegalArgumentException("BUFFER_ARGUMENT_TYPE");};
                entry.add(new MethodInsnNode(Opcodes.INVOKESTATIC,"java/lang/"+box,"valueOf","("+argument.getDescriptor()+")Ljava/lang/"+box+";",false));
            }
            entry.add(new InsnNode(Opcodes.AASTORE));
            slot+=argument.getSize();
        }
        entry.add(new LdcInsnNode(Type.getObjectType(owner)));entry.add(new LdcInsnNode(method.name));
        entry.add(new MethodInsnNode(Opcodes.INVOKESTATIC,BRIDGE,"bufferOperation","(Ljava/lang/Object;[Ljava/lang/Object;Ljava/lang/Class;Ljava/lang/String;)Ljava/lang/Object;",false));
        entry.add(new VarInsnNode(Opcodes.ASTORE,token));entry.add(start);
        for(AbstractInsnNode at:method.instructions.toArray())if(at.getOpcode()>=Opcodes.IRETURN&&at.getOpcode()<=Opcodes.RETURN)method.instructions.insertBefore(at,finish(token));
        method.instructions.insert(entry);method.instructions.add(end);method.instructions.add(handler);method.instructions.add(new VarInsnNode(Opcodes.ASTORE,failure));
        method.instructions.add(new VarInsnNode(Opcodes.ALOAD,token));method.instructions.add(new VarInsnNode(Opcodes.ALOAD,failure));
        method.instructions.add(new MethodInsnNode(Opcodes.INVOKESTATIC,BRIDGE,"bufferOperationFailed","(Ljava/lang/Object;Ljava/lang/Throwable;)V",false));
        method.instructions.add(new VarInsnNode(Opcodes.ALOAD,failure));method.instructions.add(new InsnNode(Opcodes.ATHROW));
        method.tryCatchBlocks.add(new TryCatchBlockNode(start,end,handler,"java/lang/Throwable"));
    }
    private static InsnList finish(int token){
        InsnList code=new InsnList();code.add(new VarInsnNode(Opcodes.ALOAD,token));
        code.add(new MethodInsnNode(Opcodes.INVOKESTATIC,BRIDGE,"bufferOperationFinished","(Ljava/lang/Object;)V",false));return code;
    }
    private static void action(String owner,MethodNode method){
        int token=method.maxLocals++,failure=method.maxLocals++;LabelNode start=new LabelNode(),end=new LabelNode(),handler=new LabelNode();
        InsnList enter=new InsnList();enter.add(new VarInsnNode(Opcodes.ALOAD,0));enter.add(new LdcInsnNode(Type.getObjectType(owner)));
        enter.add(new MethodInsnNode(Opcodes.INVOKESTATIC,BRIDGE,"bufferAction","(Ljava/lang/Runnable;Ljava/lang/Class;)Ljava/lang/Object;",false));
        enter.add(new VarInsnNode(Opcodes.ASTORE,token));enter.add(new VarInsnNode(Opcodes.ALOAD,token));
        enter.add(new FieldInsnNode(Opcodes.GETSTATIC,"java/lang/Boolean","FALSE","Ljava/lang/Boolean;"));enter.add(new JumpInsnNode(Opcodes.IF_ACMPNE,start));enter.add(new InsnNode(Opcodes.RETURN));enter.add(start);
        for(AbstractInsnNode at:method.instructions.toArray())if(at.getOpcode()==Opcodes.RETURN)method.instructions.insertBefore(at,actionFinish(token,true));
        method.instructions.insert(enter);method.instructions.add(end);method.instructions.add(handler);method.instructions.add(new VarInsnNode(Opcodes.ASTORE,failure));
        method.instructions.add(actionFinish(token,false));method.instructions.add(new VarInsnNode(Opcodes.ALOAD,failure));method.instructions.add(new InsnNode(Opcodes.ATHROW));
        method.tryCatchBlocks.add(new TryCatchBlockNode(start,end,handler,"java/lang/Throwable"));
    }
    private static InsnList actionFinish(int token,boolean completed){
        InsnList code=new InsnList();code.add(new VarInsnNode(Opcodes.ALOAD,token));code.add(new InsnNode(completed?Opcodes.ICONST_1:Opcodes.ICONST_0));
        code.add(new MethodInsnNode(Opcodes.INVOKESTATIC,BRIDGE,"bufferActionFinished","(Ljava/lang/Object;Z)V",false));return code;
    }
}
