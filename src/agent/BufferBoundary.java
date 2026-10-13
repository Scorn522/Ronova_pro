package dev.ronova.pro.agent;

import java.util.*;
import jdk.internal.org.objectweb.asm.*;
import jdk.internal.org.objectweb.asm.tree.*;

/** Actual platform buffer views, synchronous uses and original cleanup actions. */
final class BufferBoundary {
    private static final String BRIDGE="dev/ronova/pro/bootstrap/ResourceBridge";
    static final String WRAP_TARGET="org/lwjgl/system/MemoryUtil";
    private static final String WRAP_DESCRIPTOR="(Ljava/lang/Class;JI)Ljava/nio/Buffer;",
            SLICE_DESCRIPTOR="(Ljava/nio/ByteBuffer;JI)Ljava/nio/ByteBuffer;",UNSAFE="sun/misc/Unsafe";
    static final Set<String> TARGETS=targets();
    static boolean wrapTarget(String name){return WRAP_TARGET.equals(name);}
    static byte[] transformWrap(ClassLoader loader,String name,byte[] bytes){
        if(!wrapTarget(name))return null;
        ClassNode node=new ClassNode();new ClassReader(bytes).accept(node,0);
        if(!node.name.equals(name))throw new IllegalStateException("BUFFER_WRAP_CLASS_CHANGED");
        MethodNode wrap=null,slice=null;
        for(MethodNode method:node.methods){
            if(method.name.equals("wrap")&&method.desc.equals(WRAP_DESCRIPTOR)){
                if(wrap!=null)throw new IllegalStateException("BUFFER_WRAP_METHOD_AMBIGUOUS");wrap=method;
            }else if(method.name.equals("slice")&&method.desc.equals(SLICE_DESCRIPTOR)){
                if(slice!=null)throw new IllegalStateException("BUFFER_SLICE_METHOD_AMBIGUOUS");slice=method;
            }
        }
        if(wrap==null||slice==null)throw new IllegalStateException("BUFFER_FACTORY_METHOD_UNAVAILABLE");
        MethodInsnNode wrapAllocation=wrapLayout(node,wrap,false),sliceAllocation=wrapLayout(node,slice,true);
        factoryScope(node,wrap,wrapAllocation,false);factoryScope(node,slice,sliceAllocation,true);
        ControlClassWriter writer=new ControlClassWriter(loader,node);node.accept(writer);return writer.toByteArray();
    }
    private static void factoryScope(ClassNode node,MethodNode method,MethodInsnNode allocation,boolean slice){
        int requested=method.maxLocals++,token=method.maxLocals++,failure=method.maxLocals++;
        InsnList capture=new InsnList();capture.add(new InsnNode(Opcodes.DUP));capture.add(new VarInsnNode(Opcodes.ASTORE,requested));
        method.instructions.insertBefore(allocation,capture);
        LabelNode start=new LabelNode(),end=new LabelNode(),handler=new LabelNode();
        InsnList enter=new InsnList();enter.add(new InsnNode(Opcodes.DUP));enter.add(new VarInsnNode(Opcodes.ALOAD,requested));
        if(slice)enter.add(new VarInsnNode(Opcodes.ALOAD,0));
        enter.add(new VarInsnNode(Opcodes.LLOAD,1));enter.add(new VarInsnNode(Opcodes.ILOAD,3));enter.add(new LdcInsnNode(Type.getObjectType(node.name)));
        enter.add(new MethodInsnNode(Opcodes.INVOKESTATIC,BRIDGE,slice?"bufferSliceEnter":"bufferWrapEnter",
                slice?"(Ljava/lang/Object;Ljava/lang/Class;Ljava/nio/ByteBuffer;JILjava/lang/Class;)Ljava/lang/Object;":"(Ljava/lang/Object;Ljava/lang/Class;JILjava/lang/Class;)Ljava/lang/Object;",false));
        enter.add(new VarInsnNode(Opcodes.ASTORE,token));enter.add(start);method.instructions.insert(allocation,enter);
        // The original allocation-failure handler lies inside the later scope's code range.
        // Its path has no scope, so retain a null token for its translated exception.
        InsnList initial=new InsnList();initial.add(new InsnNode(Opcodes.ACONST_NULL));initial.add(new VarInsnNode(Opcodes.ASTORE,token));method.instructions.insert(initial);
        for(AbstractInsnNode at:method.instructions.toArray())if(at.getOpcode()==Opcodes.ARETURN)method.instructions.insertBefore(at,wrapFinish(token,-1));
        method.instructions.add(end);method.instructions.add(handler);method.instructions.add(new VarInsnNode(Opcodes.ASTORE,failure));
        method.instructions.add(wrapFinish(token,failure));method.instructions.add(new VarInsnNode(Opcodes.ALOAD,failure));method.instructions.add(new InsnNode(Opcodes.ATHROW));
        method.tryCatchBlocks.add(new TryCatchBlockNode(start,end,handler,"java/lang/Throwable"));
    }
    /** Pin these actual factory bodies, including the ByteBuffer slice's parent and order semantics. */
    private static MethodInsnNode wrapLayout(ClassNode node,MethodNode method,boolean slice){
        List<AbstractInsnNode> code=new ArrayList<>();for(AbstractInsnNode at:method.instructions.toArray())if(at.getOpcode()>=0)code.add(at);
        boolean valid=(method.access&(Opcodes.ACC_STATIC|Opcodes.ACC_NATIVE|Opcodes.ACC_ABSTRACT))==Opcodes.ACC_STATIC
                &&code.size()==(slice?51:34)&&method.tryCatchBlocks.size()==1;
        if(valid){
            TryCatchBlockNode trap=method.tryCatchBlocks.get(0);
            valid=wrapField(code.get(0),node.name,"UNSAFE","Lsun/misc/Unsafe;")
                    &&(slice?wrapField(code.get(1),node.name,"BUFFER_BYTE","Ljava/lang/Class;"):wrapVar(code.get(1),Opcodes.ALOAD,0))
                    &&wrapCall(code.get(2),Opcodes.INVOKEVIRTUAL,UNSAFE,"allocateInstance","(Ljava/lang/Class;)Ljava/lang/Object;")
                    &&wrapType(code.get(3),Opcodes.CHECKCAST,slice?"java/nio/ByteBuffer":"java/nio/Buffer")&&wrapVar(code.get(4),Opcodes.ASTORE,4)
                    &&code.get(5) instanceof JumpInsnNode jump&&jump.getOpcode()==Opcodes.GOTO&&wrapNext(jump.label)==code.get(12)
                    &&wrapVar(code.get(6),Opcodes.ASTORE,5)&&wrapType(code.get(7),Opcodes.NEW,"java/lang/UnsupportedOperationException")
                    &&code.get(8).getOpcode()==Opcodes.DUP&&wrapVar(code.get(9),Opcodes.ALOAD,5)
                    &&wrapCall(code.get(10),Opcodes.INVOKESPECIAL,"java/lang/UnsupportedOperationException","<init>","(Ljava/lang/Throwable;)V")
                    &&code.get(11).getOpcode()==Opcodes.ATHROW&&"java/lang/InstantiationException".equals(trap.type)
                    &&wrapNext(trap.start)==code.get(0)&&wrapNext(trap.end)==code.get(5)&&wrapNext(trap.handler)==code.get(6);
            String[] fields={"ADDRESS","MARK","LIMIT","CAPACITY"};
            for(int i=0;valid&&i<fields.length;i++){
                int at=12+i*5;boolean address=i==0;
                valid=wrapField(code.get(at),node.name,"UNSAFE","Lsun/misc/Unsafe;")&&wrapVar(code.get(at+1),Opcodes.ALOAD,4)
                        &&wrapField(code.get(at+2),node.name,fields[i],"J")
                        &&(address?wrapVar(code.get(at+3),Opcodes.LLOAD,1):i==1?code.get(at+3).getOpcode()==Opcodes.ICONST_M1:wrapVar(code.get(at+3),Opcodes.ILOAD,3))
                        &&wrapCall(code.get(at+4),Opcodes.INVOKEVIRTUAL,UNSAFE,address?"putLong":"putInt",address?"(Ljava/lang/Object;JJ)V":"(Ljava/lang/Object;JI)V");
            }
            if(slice&&valid){
                valid=wrapField(code.get(32),node.name,"UNSAFE","Lsun/misc/Unsafe;")&&wrapVar(code.get(33),Opcodes.ALOAD,0)
                        &&wrapField(code.get(34),node.name,"PARENT_BYTE","J")&&wrapCall(code.get(35),Opcodes.INVOKEVIRTUAL,UNSAFE,"getObject","(Ljava/lang/Object;J)Ljava/lang/Object;")
                        &&wrapVar(code.get(36),Opcodes.ASTORE,5)&&wrapField(code.get(37),node.name,"UNSAFE","Lsun/misc/Unsafe;")
                        &&wrapVar(code.get(38),Opcodes.ALOAD,4)&&wrapField(code.get(39),node.name,"PARENT_BYTE","J")&&wrapVar(code.get(40),Opcodes.ALOAD,5)
                        &&code.get(41) instanceof JumpInsnNode present&&present.getOpcode()==Opcodes.IFNONNULL&&wrapNext(present.label)==code.get(44)
                        &&wrapVar(code.get(42),Opcodes.ALOAD,0)&&code.get(43) instanceof JumpInsnNode parent&&parent.getOpcode()==Opcodes.GOTO&&wrapNext(parent.label)==code.get(45)
                        &&wrapVar(code.get(44),Opcodes.ALOAD,5)&&wrapCall(code.get(45),Opcodes.INVOKEVIRTUAL,UNSAFE,"putObject","(Ljava/lang/Object;JLjava/lang/Object;)V")
                        &&wrapVar(code.get(46),Opcodes.ALOAD,4)&&wrapVar(code.get(47),Opcodes.ALOAD,0)
                        &&wrapCall(code.get(48),Opcodes.INVOKEVIRTUAL,"java/nio/ByteBuffer","order","()Ljava/nio/ByteOrder;")
                        &&wrapCall(code.get(49),Opcodes.INVOKEVIRTUAL,"java/nio/ByteBuffer","order","(Ljava/nio/ByteOrder;)Ljava/nio/ByteBuffer;")&&code.get(50).getOpcode()==Opcodes.ARETURN;
            }else if(valid)valid=wrapVar(code.get(32),Opcodes.ALOAD,4)&&code.get(33).getOpcode()==Opcodes.ARETURN;
        }
        if(!valid)throw new IllegalStateException(slice?"BUFFER_SLICE_ORIGINAL_LAYOUT_CHANGED":"BUFFER_WRAP_ORIGINAL_LAYOUT_CHANGED");return (MethodInsnNode)code.get(2);
    }
    private static AbstractInsnNode wrapNext(AbstractInsnNode at){while(at!=null&&at.getOpcode()<0)at=at.getNext();return at;}
    private static boolean wrapField(AbstractInsnNode at,String owner,String name,String descriptor){return at instanceof FieldInsnNode field&&field.getOpcode()==Opcodes.GETSTATIC&&field.owner.equals(owner)&&field.name.equals(name)&&field.desc.equals(descriptor);}
    private static boolean wrapVar(AbstractInsnNode at,int opcode,int slot){return at instanceof VarInsnNode variable&&variable.getOpcode()==opcode&&variable.var==slot;}
    private static boolean wrapType(AbstractInsnNode at,int opcode,String name){return at instanceof TypeInsnNode type&&type.getOpcode()==opcode&&type.desc.equals(name);}
    private static boolean wrapCall(AbstractInsnNode at,int opcode,String owner,String name,String descriptor){return at instanceof MethodInsnNode call&&call.getOpcode()==opcode&&call.owner.equals(owner)&&call.name.equals(name)&&call.desc.equals(descriptor)&&!call.itf;}
    private static InsnList wrapFinish(int token,int failure){
        InsnList code=new InsnList();code.add(new VarInsnNode(Opcodes.ALOAD,token));
        if(failure<0)code.add(new InsnNode(Opcodes.ACONST_NULL));else code.add(new VarInsnNode(Opcodes.ALOAD,failure));
        code.add(new MethodInsnNode(Opcodes.INVOKESTATIC,BRIDGE,"bufferWrapExit","(Ljava/lang/Object;Ljava/lang/Throwable;)V",false));return code;
    }
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
