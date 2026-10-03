package dev.ronova.pro.agent;

import java.util.Set;
import jdk.internal.org.objectweb.asm.*;
import jdk.internal.org.objectweb.asm.tree.*;

/** Captures real JDK file construction, descriptor attachment and lazy channel publication. */
final class ResourceBoundary {
    private static final String BRIDGE="dev/ronova/pro/bootstrap/ResourceBridge";
    static final Set<String> CHARACTER_WRAPPERS=Set.of("java/io/BufferedReader","java/io/BufferedWriter","java/io/PushbackReader","java/io/LineNumberReader",
            "java/io/InputStreamReader","java/io/OutputStreamWriter","java/io/FileReader","java/io/FileWriter","sun/nio/cs/StreamDecoder","sun/nio/cs/StreamEncoder");
    static final Set<String> TARGETS=Set.of("java/io/FileDescriptor","java/io/FileInputStream","java/io/FileOutputStream","java/io/RandomAccessFile","java/lang/ProcessImpl",
            "sun/nio/ch/FileChannelImpl","sun/nio/fs/WindowsChannelFactory","java/nio/channels/spi/AbstractInterruptibleChannel");
    static byte[] transform(ClassLoader loader,String name,byte[] bytes){
        if(loader==null&&CHARACTER_WRAPPERS.contains(name))return characterWrapper(loader,name,bytes);
        byte[] buffers=BufferBoundary.transform(loader,name,bytes);if(buffers!=null)return buffers;
        byte[] io=IoBoundary.transform(loader,name,bytes);if(io!=null)return io;
        if(loader!=null||!TARGETS.contains(name))return null;
        ClassNode node=new ClassNode();new ClassReader(bytes).accept(node,0);boolean changed=false;
        if(name.equals("java/lang/ProcessImpl"))return process(loader,node);
        if(name.equals("sun/nio/ch/FileChannelImpl"))return fileChannel(loader,node);
        if(name.equals("sun/nio/fs/WindowsChannelFactory"))return fileFactory(loader,node);
        if(name.equals("java/nio/channels/spi/AbstractInterruptibleChannel")){
            MethodNode close=node.methods.stream().filter(method->method.name.equals("close")&&method.desc.equals("()V")).findFirst().orElseThrow();
            close.instructions.insert(fileCall(node.name,close.name,"fileClosing",true));
            close.instructions.insert(IoBoundary.close(node.name,close.name));
            return write(loader,node);
        }
        for(MethodNode method:node.methods){
            boolean descriptor=name.equals("java/io/FileDescriptor");
            boolean attached=descriptor&&method.name.equals("attach")&&method.desc.equals("(Ljava/io/Closeable;)V");
            if(descriptor&&method.name.equals("close")&&method.desc.equals("()V")){
                InsnList guard=new InsnList();guard.add(new VarInsnNode(Opcodes.ALOAD,0));
                guard.add(new MethodInsnNode(Opcodes.INVOKESTATIC,BRIDGE,"descriptorClosing","(Ljava/io/FileDescriptor;)V",false));method.instructions.insert(guard);changed=true;
            }
            boolean constructed=!descriptor&&method.name.equals("<init>");
            boolean channel=!descriptor&&method.name.equals("getChannel")&&method.desc.equals("()Ljava/nio/channels/FileChannel;");
            if(attached){
                InsnList guard=new InsnList();guard.add(new VarInsnNode(Opcodes.ALOAD,0));
                guard.add(new MethodInsnNode(Opcodes.INVOKESTATIC,BRIDGE,"descriptorAttaching","(Ljava/io/FileDescriptor;)V",false));method.instructions.insert(guard);changed=true;
            }
            if(attached||constructed||channel)for(AbstractInsnNode instruction:method.instructions.toArray()){
                if(instruction.getOpcode()!=(channel?Opcodes.ARETURN:Opcodes.RETURN))continue;
                InsnList code=new InsnList();
                if(channel){code.add(new InsnNode(Opcodes.DUP));code.add(new VarInsnNode(Opcodes.ALOAD,0));code.add(new InsnNode(Opcodes.SWAP));}
                else code.add(new VarInsnNode(Opcodes.ALOAD,0));
                if(attached)code.add(new VarInsnNode(Opcodes.ALOAD,1));
                else code.add(new LdcInsnNode(Type.getObjectType(name)));
                code.add(new MethodInsnNode(Opcodes.INVOKESTATIC,BRIDGE,attached?"attached":channel?"channel":"constructed",
                        attached?"(Ljava/io/FileDescriptor;Ljava/io/Closeable;)V":channel?"(Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Class;)V":"(Ljava/lang/Object;Ljava/lang/Class;)V",false));
                method.instructions.insertBefore(instruction,code);changed=true;
            }
            if(!descriptor&&(method.access&(Opcodes.ACC_NATIVE|Opcodes.ACC_ABSTRACT|Opcodes.ACC_STATIC))==0){
                if(constructed||method.name.equals("open"))method.instructions.insert(fileCall(node.name,method.name,"fileOpening",false));
                else if(method.name.equals("close"))method.instructions.insert(fileCall(node.name,method.name,"fileClosing",true));
                else scopedFile(node.name,method);
                changed=true;
            }
        }
        if(!changed)throw new IllegalStateException("FILE_RESOURCE_LAYOUT_UNAVAILABLE:"+name);
        return write(loader,node);
    }
    private static byte[] characterWrapper(ClassLoader loader,String name,byte[] bytes){
        ClassNode node=new ClassNode();new ClassReader(bytes).accept(node,0);
        for(MethodNode method:node.methods){
            if(method.name.equals("<init>")){
                for(AbstractInsnNode at:method.instructions.toArray())if(at.getOpcode()==Opcodes.RETURN){
                    InsnList code=new InsnList();code.add(new VarInsnNode(Opcodes.ALOAD,0));code.add(new LdcInsnNode(Type.getObjectType(name)));
                    code.add(new MethodInsnNode(Opcodes.INVOKESTATIC,BRIDGE,"wrapperConstructed","(Ljava/lang/Object;Ljava/lang/Class;)V",false));method.instructions.insertBefore(at,code);
                }
            }else if((method.access&Opcodes.ACC_PUBLIC)!=0&&(method.access&(Opcodes.ACC_STATIC|Opcodes.ACC_ABSTRACT|Opcodes.ACC_NATIVE))==0){
                InsnList code=new InsnList();code.add(new VarInsnNode(Opcodes.ALOAD,0));code.add(new LdcInsnNode(Type.getObjectType(name)));code.add(new LdcInsnNode(method.name));
                code.add(new MethodInsnNode(Opcodes.INVOKESTATIC,BRIDGE,"wrapperOperation","(Ljava/lang/Object;Ljava/lang/Class;Ljava/lang/String;)V",false));method.instructions.insert(code);
            }
        }
        return write(loader,node);
    }
    private static byte[] write(ClassLoader loader,ClassNode node){ControlClassWriter writer=new ControlClassWriter(loader,node);node.accept(writer);return writer.toByteArray();}
    private static InsnList fileCall(String owner,String method,String hook,boolean receiver){
        InsnList code=new InsnList();if(receiver)code.add(new VarInsnNode(Opcodes.ALOAD,0));
        code.add(new LdcInsnNode(Type.getObjectType(owner)));code.add(new LdcInsnNode(method));
        code.add(new MethodInsnNode(Opcodes.INVOKESTATIC,BRIDGE,hook,(receiver?"(Ljava/lang/Object;":"(")+"Ljava/lang/Class;Ljava/lang/String;)V",false));return code;
    }
    private static void scopedFile(String owner,MethodNode method){
        int token=method.maxLocals++,failure=method.maxLocals++;
        LabelNode start=new LabelNode(),end=new LabelNode(),handler=new LabelNode();
        InsnList entry=new InsnList();entry.add(new VarInsnNode(Opcodes.ALOAD,0));entry.add(new LdcInsnNode(Type.getObjectType(owner)));entry.add(new LdcInsnNode(method.name));
        entry.add(new MethodInsnNode(Opcodes.INVOKESTATIC,BRIDGE,"fileOperation","(Ljava/lang/Object;Ljava/lang/Class;Ljava/lang/String;)Ljava/lang/Object;",false));
        entry.add(new VarInsnNode(Opcodes.ASTORE,token));entry.add(start);method.instructions.insert(entry);
        for(AbstractInsnNode instruction:method.instructions.toArray())if(instruction.getOpcode()>=Opcodes.IRETURN&&instruction.getOpcode()<=Opcodes.RETURN)
            method.instructions.insertBefore(instruction,fileFinished(token));
        method.instructions.add(end);method.instructions.add(handler);method.instructions.add(new VarInsnNode(Opcodes.ASTORE,failure));
        method.instructions.add(fileFinished(token));method.instructions.add(new VarInsnNode(Opcodes.ALOAD,failure));method.instructions.add(new InsnNode(Opcodes.ATHROW));
        method.tryCatchBlocks.add(new TryCatchBlockNode(start,end,handler,"java/lang/Throwable"));
    }
    private static InsnList fileFinished(int token){
        InsnList code=new InsnList();code.add(new VarInsnNode(Opcodes.ALOAD,token));
        code.add(new MethodInsnNode(Opcodes.INVOKESTATIC,BRIDGE,"fileOperationFinished","(Ljava/lang/Object;)V",false));return code;
    }
    private static byte[] fileFactory(ClassLoader loader,ClassNode node){
        boolean opened=false;
        for(MethodNode method:node.methods)if(method.name.equals("open")&&Type.getReturnType(method.desc).equals(Type.getType("Ljava/io/FileDescriptor;"))){
            method.instructions.insert(fileCall(node.name,method.name,"fileOpening",false));
            for(AbstractInsnNode instruction:method.instructions.toArray())if(instruction.getOpcode()==Opcodes.ARETURN){
                InsnList code=new InsnList();code.add(new InsnNode(Opcodes.DUP));
                code.add(new MethodInsnNode(Opcodes.INVOKESTATIC,BRIDGE,"descriptorOpened","(Ljava/io/FileDescriptor;)V",false));method.instructions.insertBefore(instruction,code);opened=true;
            }
        }
        if(!opened)throw new IllegalStateException("FILE_FACTORY_DESCRIPTOR_RETURN_UNAVAILABLE");return write(loader,node);
    }
    private static byte[] fileChannel(ClassLoader loader,ClassNode node){
        boolean constructed=false,cleanup=false,closed=false;
        for(MethodNode method:node.methods){
            if(method.name.equals("<init>")&&method.desc.equals("(Ljava/io/FileDescriptor;Ljava/lang/String;ZZZLjava/lang/Object;)V")){
                for(AbstractInsnNode instruction:method.instructions.toArray()){
                    if(instruction instanceof MethodInsnNode call&&call.getOpcode()==Opcodes.INVOKESPECIAL&&call.owner.equals(node.superName)&&call.name.equals("<init>")){
                        InsnList code=new InsnList();code.add(new VarInsnNode(Opcodes.ALOAD,0));code.add(new VarInsnNode(Opcodes.ALOAD,1));code.add(new VarInsnNode(Opcodes.ALOAD,2));code.add(new VarInsnNode(Opcodes.ALOAD,6));
                        code.add(new MethodInsnNode(Opcodes.INVOKESTATIC,BRIDGE,"fileChannelConstructing","(Ljava/lang/Object;Ljava/io/FileDescriptor;Ljava/lang/String;Ljava/lang/Object;)V",false));method.instructions.insert(call,code);constructed=true;
                    }
                    if(instruction instanceof MethodInsnNode call&&call.owner.equals("java/lang/ref/Cleaner")&&call.name.equals("register")&&call.desc.equals("(Ljava/lang/Object;Ljava/lang/Runnable;)Ljava/lang/ref/Cleaner$Cleanable;")){
                        int action=method.maxLocals++;InsnList code=new InsnList();code.add(new VarInsnNode(Opcodes.ASTORE,action));code.add(new VarInsnNode(Opcodes.ALOAD,0));code.add(new VarInsnNode(Opcodes.ALOAD,action));
                        code.add(new MethodInsnNode(Opcodes.INVOKESTATIC,BRIDGE,"fileChannelCleanup","(Ljava/lang/Object;Ljava/lang/Runnable;)Ljava/lang/Runnable;",false));method.instructions.insertBefore(call,code);cleanup=true;
                    }
                    if(instruction.getOpcode()==Opcodes.RETURN){
                        InsnList code=new InsnList();code.add(new VarInsnNode(Opcodes.ALOAD,0));code.add(new MethodInsnNode(Opcodes.INVOKESTATIC,BRIDGE,"fileChannelConstructed","(Ljava/lang/Object;)V",false));method.instructions.insertBefore(instruction,code);
                    }
                }
            }else if(method.name.equals("implCloseChannel")&&method.desc.equals("()V")){
                for(AbstractInsnNode instruction:method.instructions.toArray())if(instruction.getOpcode()==Opcodes.RETURN){
                    InsnList code=new InsnList();code.add(new VarInsnNode(Opcodes.ALOAD,0));code.add(new MethodInsnNode(Opcodes.INVOKESTATIC,BRIDGE,"fileChannelClosed","(Ljava/lang/Object;)V",false));method.instructions.insertBefore(instruction,code);closed=true;
                }
            }else if((method.access&Opcodes.ACC_PUBLIC)!=0&&(method.access&(Opcodes.ACC_STATIC|Opcodes.ACC_NATIVE|Opcodes.ACC_ABSTRACT))==0){scopedFile(node.name,method);BufferBoundary.scope(node.name,method);}
            if(method.name.equals("mapInternal")&&Type.getReturnType(method.desc).getDescriptor().equals("Lsun/nio/ch/FileChannelImpl$Unmapper;")){
                for(AbstractInsnNode at:method.instructions.toArray())if(at.getOpcode()==Opcodes.ARETURN){
                    InsnList code=new InsnList();code.add(new InsnNode(Opcodes.DUP));code.add(new VarInsnNode(Opcodes.ALOAD,0));
                    code.add(new MethodInsnNode(Opcodes.INVOKESTATIC,BRIDGE,"mappingCreated","(Ljava/lang/Object;Ljava/lang/Object;)V",false));method.instructions.insertBefore(at,code);
                }
            }
        }
        if(!constructed||!cleanup||!closed)throw new IllegalStateException("FILE_CHANNEL_ORIGINAL_LIFETIME_LAYOUT_UNAVAILABLE");return write(loader,node);
    }
    private static byte[] process(ClassLoader loader,ClassNode node){
        // This constructor and native close belong to the installed Windows JDK implementation.
        if(node.fields.stream().noneMatch(field->field.name.equals("handle")&&field.desc.equals("J")))return null;
        boolean constructor=false,creation=false,cleaner=false,close=false;
        for(MethodNode method:node.methods){
            if(method.name.equals("start")&&(method.access&Opcodes.ACC_STATIC)!=0){
                InsnList code=new InsnList();code.add(new MethodInsnNode(Opcodes.INVOKESTATIC,BRIDGE,"processStart","()V",false));method.instructions.insert(code);
                for(AbstractInsnNode instruction:method.instructions.toArray())if(instruction.getOpcode()==Opcodes.ARETURN){
                    InsnList result=new InsnList();result.add(new InsnNode(Opcodes.DUP));result.add(new VarInsnNode(Opcodes.ALOAD,3));
                    result.add(new MethodInsnNode(Opcodes.INVOKESTATIC,BRIDGE,"processStarted","(Ljava/lang/Process;[Ljava/lang/ProcessBuilder$Redirect;)V",false));method.instructions.insertBefore(instruction,result);
                }
            }
            if(method.name.equals("<init>")&&method.desc.equals("([Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;[JZZ)V")){
                constructor=true;
                for(AbstractInsnNode instruction:method.instructions.toArray()){
                    if(instruction instanceof MethodInsnNode call&&call.getOpcode()==Opcodes.INVOKESTATIC&&call.owner.equals(node.name)
                            &&call.name.equals("create")&&call.desc.equals("(Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;[JZ)J")){
                        int redirect=method.maxLocals++,handles=method.maxLocals++,directory=method.maxLocals++,environment=method.maxLocals++,command=method.maxLocals++;
                        int token=method.maxLocals;method.maxLocals+=2;int result=method.maxLocals;method.maxLocals+=2;int failure=method.maxLocals++;
                        LabelNode start=new LabelNode(),end=new LabelNode(),handler=new LabelNode(),resume=new LabelNode();
                        InsnList before=new InsnList();
                        before.add(new VarInsnNode(Opcodes.ISTORE,redirect));before.add(new VarInsnNode(Opcodes.ASTORE,handles));
                        before.add(new VarInsnNode(Opcodes.ASTORE,directory));before.add(new VarInsnNode(Opcodes.ASTORE,environment));before.add(new VarInsnNode(Opcodes.ASTORE,command));
                        before.add(new VarInsnNode(Opcodes.ALOAD,handles));before.add(new MethodInsnNode(Opcodes.INVOKESTATIC,BRIDGE,"processCreateBegin","([J)J",false));before.add(new VarInsnNode(Opcodes.LSTORE,token));
                        before.add(new VarInsnNode(Opcodes.ALOAD,command));before.add(new VarInsnNode(Opcodes.ALOAD,environment));before.add(new VarInsnNode(Opcodes.ALOAD,directory));
                        before.add(new VarInsnNode(Opcodes.ALOAD,handles));before.add(new VarInsnNode(Opcodes.ILOAD,redirect));before.add(start);method.instructions.insertBefore(call,before);
                        InsnList after=new InsnList();after.add(end);after.add(new VarInsnNode(Opcodes.LSTORE,result));
                        after.add(new VarInsnNode(Opcodes.LLOAD,token));after.add(new VarInsnNode(Opcodes.LLOAD,result));after.add(new VarInsnNode(Opcodes.ALOAD,handles));
                        after.add(new MethodInsnNode(Opcodes.INVOKESTATIC,BRIDGE,"processCreateEnd","(JJ[J)V",false));after.add(new VarInsnNode(Opcodes.LLOAD,result));after.add(new JumpInsnNode(Opcodes.GOTO,resume));
                        after.add(handler);after.add(new VarInsnNode(Opcodes.ASTORE,failure));after.add(new VarInsnNode(Opcodes.LLOAD,token));after.add(new InsnNode(Opcodes.LCONST_0));after.add(new VarInsnNode(Opcodes.ALOAD,handles));
                        after.add(new MethodInsnNode(Opcodes.INVOKESTATIC,BRIDGE,"processCreateEnd","(JJ[J)V",false));after.add(new VarInsnNode(Opcodes.ALOAD,failure));after.add(new InsnNode(Opcodes.ATHROW));after.add(resume);
                        method.instructions.insert(call,after);method.tryCatchBlocks.add(0,new TryCatchBlockNode(start,end,handler,"java/lang/Throwable"));creation=true;
                    }
                    if(instruction instanceof MethodInsnNode call&&call.owner.equals("java/lang/ref/Cleaner")&&call.name.equals("register")&&call.desc.equals("(Ljava/lang/Object;Ljava/lang/Runnable;)Ljava/lang/ref/Cleaner$Cleanable;")){
                        int action=method.maxLocals++;InsnList before=new InsnList();before.add(new VarInsnNode(Opcodes.ASTORE,action));before.add(new VarInsnNode(Opcodes.ALOAD,0));before.add(new VarInsnNode(Opcodes.ALOAD,action));
                        before.add(new MethodInsnNode(Opcodes.INVOKESTATIC,BRIDGE,"processCleanupAction","(Ljava/lang/Process;Ljava/lang/Runnable;)Ljava/lang/Runnable;",false));before.add(new InsnNode(Opcodes.DUP));before.add(new VarInsnNode(Opcodes.ASTORE,action));method.instructions.insertBefore(call,before);
                        InsnList after=new InsnList();after.add(new InsnNode(Opcodes.DUP));after.add(new VarInsnNode(Opcodes.ALOAD,0));after.add(new InsnNode(Opcodes.SWAP));after.add(new VarInsnNode(Opcodes.ALOAD,action));
                        after.add(new MethodInsnNode(Opcodes.INVOKESTATIC,BRIDGE,"processCleaner","(Ljava/lang/Process;Ljava/lang/ref/Cleaner$Cleanable;Ljava/lang/Runnable;)V",false));method.instructions.insert(call,after);cleaner=true;
                    }
                    if(instruction.getOpcode()==Opcodes.RETURN){
                        InsnList code=new InsnList();code.add(new VarInsnNode(Opcodes.ALOAD,0));code.add(new VarInsnNode(Opcodes.ALOAD,4));code.add(new VarInsnNode(Opcodes.ILOAD,5));
                        code.add(new MethodInsnNode(Opcodes.INVOKESTATIC,BRIDGE,"processConstructed","(Ljava/lang/Process;[JZ)V",false));method.instructions.insertBefore(instruction,code);
                    }
                }
            }
            for(AbstractInsnNode instruction:method.instructions.toArray()){
                if(instruction instanceof MethodInsnNode call&&call.owner.equals(node.name)&&call.name.equals("closeHandle")&&call.desc.equals("(J)Z")){
                    int handle=method.maxLocals;method.maxLocals+=2;int result=method.maxLocals++;
                    InsnList before=new InsnList();before.add(new InsnNode(Opcodes.DUP2));before.add(new VarInsnNode(Opcodes.LSTORE,handle));method.instructions.insertBefore(call,before);
                    InsnList after=new InsnList();after.add(new VarInsnNode(Opcodes.ISTORE,result));after.add(new VarInsnNode(Opcodes.LLOAD,handle));after.add(new VarInsnNode(Opcodes.ILOAD,result));after.add(new LdcInsnNode(method.name));
                    after.add(new MethodInsnNode(Opcodes.INVOKESTATIC,BRIDGE,"processHandleClosed","(JZLjava/lang/String;)V",false));after.add(new VarInsnNode(Opcodes.ILOAD,result));method.instructions.insert(call,after);close=true;
                }
                if(instruction.getOpcode()==Opcodes.ARETURN&&Set.of("getOutputStream","getInputStream","getErrorStream","toHandle").contains(method.name)){
                    InsnList code=new InsnList();code.add(new InsnNode(Opcodes.DUP));code.add(new VarInsnNode(Opcodes.ALOAD,0));code.add(new InsnNode(Opcodes.SWAP));code.add(new LdcInsnNode(method.name));
                    code.add(new MethodInsnNode(Opcodes.INVOKESTATIC,BRIDGE,"processPipeExposed","(Ljava/lang/Process;Ljava/lang/Object;Ljava/lang/String;)V",false));method.instructions.insertBefore(instruction,code);
                }
            }
        }
        if(!constructor||!creation||!cleaner||!close)throw new IllegalStateException("PROCESS_ORIGINAL_LIFETIME_LAYOUT_UNAVAILABLE");
        ControlClassWriter writer=new ControlClassWriter(loader,node);node.accept(writer);return writer.toByteArray();
    }
}
