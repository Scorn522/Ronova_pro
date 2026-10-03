package dev.ronova.pro.agent;

import java.util.Set;
import jdk.internal.org.objectweb.asm.*;
import jdk.internal.org.objectweb.asm.tree.*;

/** Observes the installed JDK's connection, delegation and actual request cleanup methods. */
final class IoBoundary {
    private static final String BRIDGE="dev/ronova/pro/bootstrap/IoBridge";
    static final Set<String> TARGETS=Set.of(
            "java/net/Socket","java/net/ServerSocket","java/net/DatagramSocket","java/net/DelegatingSocketImpl",
            "sun/nio/ch/SocketChannelImpl","sun/nio/ch/ServerSocketChannelImpl","sun/nio/ch/DatagramChannelImpl","sun/nio/ch/NioSocketImpl",
            "sun/nio/ch/SocketAdaptor","sun/nio/ch/ServerSocketAdaptor","sun/nio/ch/DatagramSocketAdaptor",
            "sun/nio/ch/AsynchronousSocketChannelImpl","sun/nio/ch/AsynchronousServerSocketChannelImpl","sun/nio/ch/AsynchronousFileChannelImpl",
            "sun/nio/ch/WindowsAsynchronousSocketChannelImpl","sun/nio/ch/WindowsAsynchronousServerSocketChannelImpl","sun/nio/ch/WindowsAsynchronousFileChannelImpl",
            "sun/nio/ch/WindowsAsynchronousSocketChannelImpl$ReadTask","sun/nio/ch/WindowsAsynchronousSocketChannelImpl$WriteTask",
            "sun/nio/ch/WindowsAsynchronousSocketChannelImpl$ConnectTask","sun/nio/ch/WindowsAsynchronousServerSocketChannelImpl$AcceptTask",
            "sun/nio/ch/WindowsAsynchronousFileChannelImpl$ReadTask","sun/nio/ch/WindowsAsynchronousFileChannelImpl$WriteTask","sun/nio/ch/WindowsAsynchronousFileChannelImpl$LockTask",
            "sun/nio/ch/IOUtil","sun/nio/ch/Util","sun/nio/ch/SocketDispatcher","sun/nio/ch/FileDispatcherImpl");
    private static final Set<String> ROOTS=Set.of("sun/nio/ch/SocketChannelImpl","sun/nio/ch/ServerSocketChannelImpl","sun/nio/ch/DatagramChannelImpl",
            "sun/nio/ch/WindowsAsynchronousSocketChannelImpl","sun/nio/ch/WindowsAsynchronousServerSocketChannelImpl","sun/nio/ch/WindowsAsynchronousFileChannelImpl","sun/nio/ch/NioSocketImpl");
    private static final Set<String> EXPOSURES=Set.of("socket","getInputStream","getOutputStream","getImpl","getChannel","delegate","getDelegate");
    private static final Set<String> ASYNC=Set.of("sun/nio/ch/WindowsAsynchronousSocketChannelImpl","sun/nio/ch/WindowsAsynchronousServerSocketChannelImpl","sun/nio/ch/WindowsAsynchronousFileChannelImpl");
    private static final Set<String> NATIVE_CALLS=Set.of("readFile","writeFile","lockFile","read0","write0","connect0","accept0","updateAcceptContext","updateConnectContext","shutdown0","closesocket0");
    static byte[] transform(ClassLoader loader,String name,byte[] bytes){
        if(loader!=null||!TARGETS.contains(name))return null;
        ClassNode node=new ClassNode();new ClassReader(bytes).accept(node,0);
        boolean task=name.indexOf('$')>=0;
        boolean delegatedReturn=name.equals("sun/nio/ch/Util")&&directTemporaryReturn(node);
        for(MethodNode method:node.methods){
            if((method.access&(Opcodes.ACC_NATIVE|Opcodes.ACC_ABSTRACT))!=0||method.name.equals("<clinit>"))continue;
            boolean statik=(method.access&Opcodes.ACC_STATIC)!=0;
            if(!statik)nativeCalls(node,method);
            if(task){task(node,method);continue;}
            if(name.equals("sun/nio/ch/Util")){
                if(Set.of("getTemporaryDirectBuffer","getTemporaryAlignedDirectBuffer").contains(method.name)){
                    for(AbstractInsnNode at:method.instructions.toArray())if(at.getOpcode()==Opcodes.ARETURN){
                        InsnList code=new InsnList();code.add(new InsnNode(Opcodes.DUP));identity(code,name,method.name);code.add(invoke("temporaryAcquired","(Ljava/nio/Buffer;Ljava/lang/Class;Ljava/lang/String;)V"));method.instructions.insertBefore(at,code);
                    }
                }else if(Set.of("releaseTemporaryDirectBuffer","offerFirstTemporaryDirectBuffer","offerLastTemporaryDirectBuffer").contains(method.name)){
                    if(delegatedReturn&&method.name.equals("releaseTemporaryDirectBuffer")&&method.desc.equals("(Ljava/nio/ByteBuffer;)V"))continue;
                    InsnList code=new InsnList();code.add(new VarInsnNode(Opcodes.ALOAD,0));identity(code,name,method.name);code.add(invoke("temporaryReturning","(Ljava/nio/Buffer;Ljava/lang/Class;Ljava/lang/String;)V"));method.instructions.insert(code);
                }
                continue;
            }
            if(method.name.equals("<init>")){
                for(AbstractInsnNode at:method.instructions.toArray())if(at.getOpcode()==Opcodes.RETURN){
                    InsnList code;
                    if(ROOTS.contains(name))code=call(0,name,method.name,"constructed");
                    else {code=new InsnList();code.add(new VarInsnNode(Opcodes.ALOAD,0));code.add(new VarInsnNode(Opcodes.ALOAD,0));identity(code,name,method.name);code.add(invoke("exposed","(Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Class;Ljava/lang/String;)V"));}
                    method.instructions.insertBefore(at,code);
                }
                continue;
            }
            if(!statik&&name.equals("sun/nio/ch/NioSocketImpl")&&(method.name.equals("create")||method.name.equals("accept")))
                for(AbstractInsnNode at:method.instructions.toArray())if(at.getOpcode()==Opcodes.RETURN)
                    method.instructions.insertBefore(at,call(method.name.equals("accept")?1:0,name,method.name,"constructed"));
            if(!statik&&EXPOSURES.contains(method.name)&&Type.getReturnType(method.desc).getSort()==Type.OBJECT){
                int result=method.maxLocals++;
                for(AbstractInsnNode at:method.instructions.toArray())if(at.getOpcode()==Opcodes.ARETURN){
                    InsnList code=new InsnList();code.add(new VarInsnNode(Opcodes.ASTORE,result));code.add(new VarInsnNode(Opcodes.ALOAD,0));code.add(new VarInsnNode(Opcodes.ALOAD,result));
                    identity(code,name,method.name);code.add(invoke("exposed","(Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Class;Ljava/lang/String;)V"));code.add(new VarInsnNode(Opcodes.ALOAD,result));method.instructions.insertBefore(at,code);
                }
            }
            if(!statik&&(method.name.equals("close")||method.name.equals("implClose")||method.name.equals("implCloseSelectableChannel")||method.name.equals("shutdownInput")||method.name.equals("shutdownOutput")))
                method.instructions.insert(call(0,name,method.name,"closing"));
            if((name.equals("sun/nio/ch/SocketDispatcher")||name.equals("sun/nio/ch/FileDispatcherImpl"))&&method.name.equals("close")&&method.desc.equals("(Ljava/io/FileDescriptor;)V")){
                descriptorCloseScope(name,method,statik?0:1);
            }
            if(!statik&&ROOTS.contains(name))for(AbstractInsnNode at:method.instructions.toArray())
                if(at instanceof MethodInsnNode nativeCall&&nativeCall.owner.equals(name)&&nativeCall.name.equals("closesocket0"))
                    method.instructions.insert(at,call(0,name,method.name,"socketClosed"));
            if(name.equals("sun/nio/ch/IOUtil"))nativeReads(node,method);
            if(!statik&&ROOTS.contains(name)&&method.name.equals("tryClose"))closeScope(name,method);
            else if(operation(method.name)&&(!statik||name.equals("sun/nio/ch/IOUtil")))scope(name,method,false);
        }
        ControlClassWriter writer=new ControlClassWriter(loader,node);node.accept(writer);return writer.toByteArray();
    }
    private static boolean directTemporaryReturn(ClassNode node){
        String descriptor="(Ljava/nio/ByteBuffer;)V";MethodNode wrapper=null,target=null;
        for(MethodNode method:node.methods)if(method.desc.equals(descriptor)){
            if(method.name.equals("releaseTemporaryDirectBuffer"))wrapper=method;
            else if(method.name.equals("offerFirstTemporaryDirectBuffer"))target=method;
        }
        int unavailable=Opcodes.ACC_NATIVE|Opcodes.ACC_ABSTRACT;
        if(wrapper==null||target==null||(wrapper.access&Opcodes.ACC_STATIC)==0||(target.access&Opcodes.ACC_STATIC)==0
                ||(wrapper.access&unavailable)!=0||(target.access&unavailable)!=0||!wrapper.tryCatchBlocks.isEmpty())return false;
        int step=0;
        for(AbstractInsnNode at:wrapper.instructions){
            if(at.getOpcode()<0)continue;
            if(step==0){if(!(at instanceof VarInsnNode load)||load.getOpcode()!=Opcodes.ALOAD||load.var!=0)return false;}
            else if(step==1){if(!(at instanceof MethodInsnNode call)||call.getOpcode()!=Opcodes.INVOKESTATIC||call.itf
                    ||!call.owner.equals(node.name)||!call.name.equals(target.name)||!call.desc.equals(descriptor))return false;}
            else if(step==2){if(at.getOpcode()!=Opcodes.RETURN)return false;}
            else return false;
            step++;
        }
        // The surviving target still authenticates its actual Class and method.
        return step==3;
    }
    private static boolean operation(String name){return Set.of("read","write","readv","writev","send","receive","connect","finishConnect","bind","listen","accept","implRead","implWrite","implConnect","implAccept",
            "setOption","getInputStream","getOutputStream","getChannel","socket","sendUrgentData","blockingRead","blockingWriteFully","blockingReceive","blockingSend","lock","tryLock","size","truncate","force").contains(name);}
    private static void nativeReads(ClassNode node,MethodNode method){
        for(AbstractInsnNode at:method.instructions.toArray())if(at instanceof MethodInsnNode call&&call.getOpcode()==Opcodes.INVOKEVIRTUAL
                &&call.owner.equals("sun/nio/ch/NativeDispatcher")&&Set.of("read","pread","readv").contains(call.name)){
            Type[] types=Type.getArgumentTypes(call.desc);
            if(types.length<3||!types[0].getDescriptor().equals("Ljava/io/FileDescriptor;")||!types[1].equals(Type.LONG_TYPE)||!types[2].equals(Type.INT_TYPE))continue;
            int[] arguments=new int[types.length];for(int i=0;i<types.length;i++){arguments[i]=method.maxLocals;method.maxLocals+=types[i].getSize();}
            int receiver=method.maxLocals++,token=method.maxLocals++,failure=method.maxLocals++;Type returned=Type.getReturnType(call.desc);int result=method.maxLocals;method.maxLocals+=returned.getSize();
            LabelNode start=new LabelNode(),end=new LabelNode(),handler=new LabelNode(),done=new LabelNode();InsnList before=new InsnList();
            for(int i=types.length-1;i>=0;i--)before.add(new VarInsnNode(types[i].getOpcode(Opcodes.ISTORE),arguments[i]));before.add(new VarInsnNode(Opcodes.ASTORE,receiver));
            before.add(new VarInsnNode(Opcodes.ALOAD,arguments[0]));before.add(new VarInsnNode(Opcodes.LLOAD,arguments[1]));before.add(new VarInsnNode(Opcodes.ILOAD,arguments[2]));
            before.add(new InsnNode(call.name.equals("readv")?Opcodes.ICONST_1:Opcodes.ICONST_0));identity(before,node.name,method.name);
            before.add(invoke("dataReadOpen","(Ljava/io/FileDescriptor;JIZLjava/lang/Class;Ljava/lang/String;)Ljava/lang/Object;"));before.add(new VarInsnNode(Opcodes.ASTORE,token));before.add(start);
            before.add(new VarInsnNode(Opcodes.ALOAD,receiver));for(int i=0;i<types.length;i++)before.add(new VarInsnNode(types[i].getOpcode(Opcodes.ILOAD),arguments[i]));method.instructions.insertBefore(at,before);
            InsnList after=new InsnList();after.add(end);after.add(new VarInsnNode(returned.getOpcode(Opcodes.ISTORE),result));after.add(new VarInsnNode(Opcodes.ALOAD,token));after.add(new VarInsnNode(returned.getOpcode(Opcodes.ILOAD),result));
            if(returned.equals(Type.INT_TYPE))after.add(new InsnNode(Opcodes.I2L));after.add(new InsnNode(Opcodes.ACONST_NULL));after.add(invoke("dataReadFinished","(Ljava/lang/Object;JLjava/lang/Throwable;)V"));after.add(new JumpInsnNode(Opcodes.GOTO,done));
            after.add(handler);after.add(new VarInsnNode(Opcodes.ASTORE,failure));after.add(new VarInsnNode(Opcodes.ALOAD,token));after.add(new InsnNode(Opcodes.LCONST_0));after.add(new VarInsnNode(Opcodes.ALOAD,failure));after.add(invoke("dataReadFinished","(Ljava/lang/Object;JLjava/lang/Throwable;)V"));
            after.add(new VarInsnNode(Opcodes.ALOAD,failure));after.add(new InsnNode(Opcodes.ATHROW));after.add(done);after.add(new VarInsnNode(returned.getOpcode(Opcodes.ILOAD),result));method.instructions.insert(at,after);
            method.tryCatchBlocks.add(0,new TryCatchBlockNode(start,end,handler,"java/lang/Throwable"));
        }
    }
    private static void nativeCalls(ClassNode node,MethodNode method){
        for(AbstractInsnNode at:method.instructions.toArray())if(at instanceof MethodInsnNode called&&called.getOpcode()==Opcodes.INVOKESTATIC&&ASYNC.contains(called.owner)&&NATIVE_CALLS.contains(called.name)){
            Type[] types=Type.getArgumentTypes(called.desc);int[] slots=new int[types.length];
            for(int i=0;i<types.length;i++){slots[i]=method.maxLocals;method.maxLocals+=types[i].getSize();}
            int numbers=method.maxLocals++,references=method.maxLocals++,token=method.maxLocals++,failure=method.maxLocals++;
            Type returned=Type.getReturnType(called.desc);int result=method.maxLocals;method.maxLocals+=returned.getSize();
            LabelNode start=new LabelNode(),end=new LabelNode(),handler=new LabelNode(),resume=new LabelNode();InsnList before=new InsnList();
            for(int i=types.length-1;i>=0;i--)before.add(new VarInsnNode(types[i].getOpcode(Opcodes.ISTORE),slots[i]));
            before.add(new LdcInsnNode(types.length));before.add(new IntInsnNode(Opcodes.NEWARRAY,Opcodes.T_LONG));before.add(new VarInsnNode(Opcodes.ASTORE,numbers));
            before.add(new LdcInsnNode(types.length));before.add(new TypeInsnNode(Opcodes.ANEWARRAY,"java/lang/Object"));before.add(new VarInsnNode(Opcodes.ASTORE,references));
            for(int i=0;i<types.length;i++){
                boolean reference=types[i].getSort()==Type.OBJECT||types[i].getSort()==Type.ARRAY;
                before.add(new VarInsnNode(Opcodes.ALOAD,reference?references:numbers));before.add(new LdcInsnNode(i));before.add(new VarInsnNode(types[i].getOpcode(Opcodes.ILOAD),slots[i]));
                if(!reference&&types[i].getSort()!=Type.LONG)before.add(new InsnNode(Opcodes.I2L));
                before.add(new InsnNode(reference?Opcodes.AASTORE:Opcodes.LASTORE));
            }
            before.add(new VarInsnNode(Opcodes.ALOAD,0));identity(before,node.name,method.name);identity(before,called.owner,called.name);
            before.add(new VarInsnNode(Opcodes.ALOAD,numbers));before.add(new VarInsnNode(Opcodes.ALOAD,references));
            before.add(invoke("nativeCallOpen","(Ljava/lang/Object;Ljava/lang/Class;Ljava/lang/String;Ljava/lang/Class;Ljava/lang/String;[J[Ljava/lang/Object;)Ljava/lang/Object;"));before.add(new VarInsnNode(Opcodes.ASTORE,token));before.add(start);
            for(int i=0;i<types.length;i++)before.add(new VarInsnNode(types[i].getOpcode(Opcodes.ILOAD),slots[i]));method.instructions.insertBefore(at,before);
            InsnList after=new InsnList();after.add(end);if(returned.getSort()!=Type.VOID)after.add(new VarInsnNode(returned.getOpcode(Opcodes.ISTORE),result));
            after.add(nativeCallFinish(token,-1));if(returned.getSort()!=Type.VOID)after.add(new VarInsnNode(returned.getOpcode(Opcodes.ILOAD),result));after.add(new JumpInsnNode(Opcodes.GOTO,resume));
            after.add(handler);after.add(new VarInsnNode(Opcodes.ASTORE,failure));after.add(nativeCallFinish(token,failure));after.add(new VarInsnNode(Opcodes.ALOAD,failure));after.add(new InsnNode(Opcodes.ATHROW));after.add(resume);
            method.instructions.insert(at,after);method.tryCatchBlocks.add(0,new TryCatchBlockNode(start,end,handler,"java/lang/Throwable"));
        }
    }
    private static InsnList nativeCallFinish(int token,int failure){InsnList code=new InsnList();code.add(new VarInsnNode(Opcodes.ALOAD,token));
        if(failure<0)code.add(new InsnNode(Opcodes.ACONST_NULL));else code.add(new VarInsnNode(Opcodes.ALOAD,failure));code.add(invoke("nativeCallClose","(Ljava/lang/Object;Ljava/lang/Throwable;)V"));return code;}
    static InsnList close(String owner,String method){return call(0,owner,method,"closing");}
    private static void descriptorCloseScope(String owner,MethodNode method,int descriptor){
        int token=method.maxLocals++,failure=method.maxLocals++;LabelNode start=new LabelNode(),end=new LabelNode(),handler=new LabelNode();
        InsnList entry=new InsnList();entry.add(new VarInsnNode(Opcodes.ALOAD,descriptor));identity(entry,owner,method.name);
        entry.add(invoke("descriptorCloseEnter","(Ljava/io/FileDescriptor;Ljava/lang/Class;Ljava/lang/String;)Ljava/lang/Object;"));entry.add(new VarInsnNode(Opcodes.ASTORE,token));entry.add(start);
        for(AbstractInsnNode at:method.instructions.toArray())if(at.getOpcode()==Opcodes.RETURN)method.instructions.insertBefore(at,descriptorCloseFinish(token,-1));
        method.instructions.insert(entry);method.instructions.add(end);method.instructions.add(handler);method.instructions.add(new VarInsnNode(Opcodes.ASTORE,failure));method.instructions.add(descriptorCloseFinish(token,failure));
        method.instructions.add(new VarInsnNode(Opcodes.ALOAD,failure));method.instructions.add(new InsnNode(Opcodes.ATHROW));method.tryCatchBlocks.add(new TryCatchBlockNode(start,end,handler,"java/lang/Throwable"));
    }
    private static InsnList descriptorCloseFinish(int token,int failure){InsnList code=new InsnList();code.add(new VarInsnNode(Opcodes.ALOAD,token));
        if(failure<0)code.add(new InsnNode(Opcodes.ACONST_NULL));else code.add(new VarInsnNode(Opcodes.ALOAD,failure));code.add(invoke("descriptorCloseExit","(Ljava/lang/Object;Ljava/lang/Throwable;)V"));return code;}
    private static void closeScope(String owner,MethodNode method){
        int token=method.maxLocals++,failure=method.maxLocals++;LabelNode start=new LabelNode(),end=new LabelNode(),handler=new LabelNode();
        InsnList entry=new InsnList();entry.add(new VarInsnNode(Opcodes.ALOAD,0));identity(entry,owner,method.name);
        entry.add(invoke("closeEnter","(Ljava/lang/Object;Ljava/lang/Class;Ljava/lang/String;)Ljava/lang/Object;"));entry.add(new VarInsnNode(Opcodes.ASTORE,token));entry.add(start);
        for(AbstractInsnNode at:method.instructions.toArray())if(at.getOpcode()>=Opcodes.IRETURN&&at.getOpcode()<=Opcodes.RETURN)method.instructions.insertBefore(at,closeFinish(token,-1));
        method.instructions.insert(entry);method.instructions.add(end);method.instructions.add(handler);method.instructions.add(new VarInsnNode(Opcodes.ASTORE,failure));method.instructions.add(closeFinish(token,failure));
        method.instructions.add(new VarInsnNode(Opcodes.ALOAD,failure));method.instructions.add(new InsnNode(Opcodes.ATHROW));method.tryCatchBlocks.add(new TryCatchBlockNode(start,end,handler,"java/lang/Throwable"));
    }
    private static InsnList closeFinish(int token,int failure){InsnList code=new InsnList();code.add(new VarInsnNode(Opcodes.ALOAD,token));
        if(failure<0)code.add(new InsnNode(Opcodes.ACONST_NULL));else code.add(new VarInsnNode(Opcodes.ALOAD,failure));code.add(invoke("closeExit","(Ljava/lang/Object;Ljava/lang/Throwable;)V"));return code;}
    private static void task(ClassNode node,MethodNode method){
        String name=node.name;
        if(method.name.equals("<init>")){
            for(AbstractInsnNode at:method.instructions.toArray())if(at.getOpcode()==Opcodes.RETURN){
                InsnList code=new InsnList();code.add(new VarInsnNode(Opcodes.ALOAD,0));code.add(new LdcInsnNode(Type.getObjectType(name)));code.add(invoke("taskCreated","(Ljava/lang/Object;Ljava/lang/Class;)V"));method.instructions.insertBefore(at,code);
            }
            return;
        }
        if((method.access&Opcodes.ACC_STATIC)!=0)return;
        if(name.endsWith("$ReadTask")&&method.name.equals("completed")&&method.desc.equals("(IZ)V")){
            InsnList code=new InsnList();code.add(new VarInsnNode(Opcodes.ALOAD,0));code.add(new VarInsnNode(Opcodes.ILOAD,1));identity(code,name,method.name);
            code.add(invoke("taskReadCompleted","(Ljava/lang/Object;ILjava/lang/Class;Ljava/lang/String;)V"));method.instructions.insert(code);
        }
        if(method.name.equals("releaseBuffers")||method.name.equals("releaseBufferIfSubstituted")){
            method.instructions.insert(call(0,name,method.name,"taskReleasing"));
            for(AbstractInsnNode at:method.instructions.toArray())if(at.getOpcode()==Opcodes.RETURN)method.instructions.insertBefore(at,call(0,name,method.name,"taskReleased"));
        }
        for(AbstractInsnNode at:method.instructions.toArray())if(at instanceof MethodInsnNode called){
            if(called.owner.equals("sun/nio/ch/PendingFuture")&&(called.name.equals("setResult")||called.name.equals("setFailure")))method.instructions.insert(at,call(0,name,method.name,"taskResult"));
            if(called.owner.equals(node.outerClass==null?name.substring(0,name.indexOf('$')):node.outerClass)
                    &&Set.of("readFile","writeFile","lockFile","read0","write0","connect0","accept0").contains(called.name))method.instructions.insertBefore(at,call(0,name,method.name,"taskNative"));
        }
        if(Set.of("run","completed","failed").contains(method.name))scope(name,method,true);
    }
    private static void scope(String owner,MethodNode method,boolean task){
        int token=method.maxLocals++,failure=method.maxLocals++;
        LabelNode start=new LabelNode(),end=new LabelNode(),handler=new LabelNode();InsnList entry=new InsnList();
        entry.add(new InsnNode(Opcodes.ACONST_NULL));entry.add(new VarInsnNode(Opcodes.ASTORE,token));entry.add(start);
        boolean statik=(method.access&Opcodes.ACC_STATIC)!=0;
        if(statik)entry.add(new InsnNode(Opcodes.ACONST_NULL));else entry.add(new VarInsnNode(Opcodes.ALOAD,0));
        if(!task)arguments(entry,method,statik);
        identity(entry,owner,method.name);
        entry.add(invoke(task?"taskEnter":"enter",task?"(Ljava/lang/Object;Ljava/lang/Class;Ljava/lang/String;)Ljava/lang/Object;":"(Ljava/lang/Object;[Ljava/lang/Object;Ljava/lang/Class;Ljava/lang/String;)Ljava/lang/Object;"));entry.add(new VarInsnNode(Opcodes.ASTORE,token));
        if(task&&method.name.equals("run")){
            LabelNode ready=new LabelNode();entry.add(new VarInsnNode(Opcodes.ALOAD,token));entry.add(invoke("taskReady","(Ljava/lang/Object;)Z"));entry.add(new JumpInsnNode(Opcodes.IFNE,ready));
            entry.add(new VarInsnNode(Opcodes.ALOAD,token));entry.add(invoke("taskRejected","(Ljava/lang/Object;)V"));entry.add(new InsnNode(Opcodes.RETURN));entry.add(ready);
        }
        for(AbstractInsnNode at:method.instructions.toArray())if(at.getOpcode()>=Opcodes.IRETURN&&at.getOpcode()<=Opcodes.RETURN)method.instructions.insertBefore(at,finish(token,-1,task));
        method.instructions.insert(entry);method.instructions.add(end);method.instructions.add(handler);method.instructions.add(new VarInsnNode(Opcodes.ASTORE,failure));
        method.instructions.add(finish(token,failure,task));method.instructions.add(new VarInsnNode(Opcodes.ALOAD,failure));method.instructions.add(new InsnNode(Opcodes.ATHROW));
        method.tryCatchBlocks.add(new TryCatchBlockNode(start,end,handler,"java/lang/Throwable"));
    }
    private static void arguments(InsnList code,MethodNode method,boolean statik){
        Type[] types=Type.getArgumentTypes(method.desc);int count=0;for(Type type:types)if(type.getSort()==Type.OBJECT||type.getSort()==Type.ARRAY)count++;
        code.add(new LdcInsnNode(count));code.add(new TypeInsnNode(Opcodes.ANEWARRAY,"java/lang/Object"));int slot=statik?0:1,index=0;
        for(int i=0;i<types.length;i++){Type type=types[i];if(type.getSort()==Type.OBJECT||type.getSort()==Type.ARRAY){
            code.add(new InsnNode(Opcodes.DUP));code.add(new LdcInsnNode(index++));code.add(new VarInsnNode(Opcodes.ALOAD,slot));
            if(type.getDescriptor().equals("[Ljava/nio/ByteBuffer;")&&i+2<types.length&&types[i+1].equals(Type.INT_TYPE)&&types[i+2].equals(Type.INT_TYPE)){
                code.add(new VarInsnNode(Opcodes.ILOAD,slot+1));code.add(new VarInsnNode(Opcodes.ILOAD,slot+2));code.add(invoke("bufferRange","([Ljava/nio/Buffer;II)[Ljava/nio/Buffer;"));
            }
            code.add(new InsnNode(Opcodes.AASTORE));}slot+=type.getSize();}
    }
    private static InsnList finish(int token,int failure,boolean task){
        InsnList code=new InsnList();code.add(new VarInsnNode(Opcodes.ALOAD,token));if(failure<0)code.add(new InsnNode(Opcodes.ACONST_NULL));else code.add(new VarInsnNode(Opcodes.ALOAD,failure));code.add(invoke(task?"taskExit":"exit","(Ljava/lang/Object;Ljava/lang/Throwable;)V"));return code;
    }
    private static InsnList call(int slot,String owner,String where,String hook){InsnList code=new InsnList();code.add(new VarInsnNode(Opcodes.ALOAD,slot));identity(code,owner,where);code.add(invoke(hook,"(Ljava/lang/Object;Ljava/lang/Class;Ljava/lang/String;)V"));return code;}
    private static void identity(InsnList code,String owner,String where){code.add(new LdcInsnNode(Type.getObjectType(owner)));code.add(new LdcInsnNode(where));}
    private static MethodInsnNode invoke(String name,String descriptor){return new MethodInsnNode(Opcodes.INVOKESTATIC,BRIDGE,name,descriptor,false);}
}
