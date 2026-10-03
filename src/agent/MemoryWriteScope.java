package dev.ronova.pro.agent;

import jdk.internal.org.objectweb.asm.*;
import jdk.internal.org.objectweb.asm.tree.*;

/** Keeps an admitted Java Unsafe operation open until its actual return or exception. */
final class MemoryWriteScope {
    private static final String BRIDGE="dev/ronova/pro/bootstrap/TaskBridge";
    private MemoryWriteScope(){}
    static void nativeCall(MethodNode method,MethodInsnNode call,Type[] arguments,int[] slots,String kind){
        nativeCall(method,call,arguments,slots,kind,false,call);
    }
    static void nativeCall(MethodNode method,MethodInsnNode call,Type[] arguments,int[] slots,String kind,AbstractInsnNode guard){
        nativeCall(method,call,arguments,slots,kind,false,guard);
    }
    static void nativeReadCall(MethodNode method,MethodInsnNode call,Type[] arguments,int[] slots,String kind){
        nativeCall(method,call,arguments,slots,kind,true,call);
    }
    private static void nativeCall(MethodNode method,MethodInsnNode call,Type[] arguments,int[] slots,String kind,boolean reading,AbstractInsnNode guard){
        Type result=Type.getReturnType(call.desc);int ticket=method.maxLocals++,error=method.maxLocals++,returned=method.maxLocals;method.maxLocals+=result.getSize();
        LabelNode start=new LabelNode(),end=new LabelNode(),handler=new LabelNode(),resume=new LabelNode();InsnList enter=new InsnList();
        enter.add(new VarInsnNode(Opcodes.ALOAD,slots[0]));enter.add(new VarInsnNode(Opcodes.LLOAD,slots[1]));enter.add(new LdcInsnNode(kind));
        enter.add(new MethodInsnNode(Opcodes.INVOKESTATIC,BRIDGE,reading?"beginHandleRead":"beginHandleMutation","(Ljava/lang/Object;JLjava/lang/String;)Ljava/lang/Object;",false));
        // The policy and every refusal return belong to the same actual write scope.
        for(AbstractInsnNode at=guard;at!=call;at=at.getNext())if(at.getOpcode()>=Opcodes.IRETURN&&at.getOpcode()<=Opcodes.RETURN){
            InsnList refused=new InsnList();refused.add(new VarInsnNode(Opcodes.ALOAD,ticket));refused.add(new InsnNode(Opcodes.ICONST_0));
            refused.add(new MethodInsnNode(Opcodes.INVOKESTATIC,BRIDGE,"finishFieldMutation","(Ljava/lang/Object;Z)V",false));method.instructions.insertBefore(at,refused);
        }
        enter.add(new VarInsnNode(Opcodes.ASTORE,ticket));enter.add(start);method.instructions.insertBefore(guard,enter);
        InsnList finish=new InsnList();finish.add(end);
        if(result.getSort()!=Type.VOID)finish.add(new VarInsnNode(result.getOpcode(Opcodes.ISTORE),returned));
        finish.add(new VarInsnNode(Opcodes.ALOAD,ticket));applied(finish,call.name,result,returned,arguments,slots);
        finish.add(new MethodInsnNode(Opcodes.INVOKESTATIC,BRIDGE,"finishFieldMutation","(Ljava/lang/Object;Z)V",false));
        if(result.getSort()!=Type.VOID)finish.add(new VarInsnNode(result.getOpcode(Opcodes.ILOAD),returned));
        finish.add(new JumpInsnNode(Opcodes.GOTO,resume));finish.add(handler);finish.add(new VarInsnNode(Opcodes.ASTORE,error));
        finish.add(new VarInsnNode(Opcodes.ALOAD,ticket));finish.add(new VarInsnNode(Opcodes.ALOAD,error));
        finish.add(new MethodInsnNode(Opcodes.INVOKESTATIC,BRIDGE,"fieldMutationFailed","(Ljava/lang/Object;Ljava/lang/Throwable;)V",false));
        finish.add(new VarInsnNode(Opcodes.ALOAD,error));finish.add(new InsnNode(Opcodes.ATHROW));finish.add(resume);
        method.instructions.insert(call,finish);method.tryCatchBlocks.add(0,new TryCatchBlockNode(start,end,handler,"java/lang/Throwable"));
    }
    static void applied(InsnList code,String operation,Type result,int returned,Type[] arguments,int[] slots){
        if(result.getSort()==Type.BOOLEAN&&(operation.startsWith("compareAndSet")||operation.startsWith("weakCompareAndSet")||operation.startsWith("compareAndSwap"))){code.add(new VarInsnNode(Opcodes.ILOAD,returned));return;}
        if(!operation.startsWith("compareAndExchange")){code.add(new InsnNode(Opcodes.ICONST_1));return;}
        LabelNode different=new LabelNode(),done=new LabelNode();code.add(new VarInsnNode(result.getOpcode(Opcodes.ILOAD),returned));
        if(result.getSort()==Type.FLOAT)code.add(new MethodInsnNode(Opcodes.INVOKESTATIC,"java/lang/Float","floatToRawIntBits","(F)I",false));
        else if(result.getSort()==Type.DOUBLE)code.add(new MethodInsnNode(Opcodes.INVOKESTATIC,"java/lang/Double","doubleToRawLongBits","(D)J",false));
        Type expected=arguments[2];code.add(new VarInsnNode(expected.getOpcode(Opcodes.ILOAD),slots[2]));
        if(expected.getSort()==Type.FLOAT)code.add(new MethodInsnNode(Opcodes.INVOKESTATIC,"java/lang/Float","floatToRawIntBits","(F)I",false));
        else if(expected.getSort()==Type.DOUBLE)code.add(new MethodInsnNode(Opcodes.INVOKESTATIC,"java/lang/Double","doubleToRawLongBits","(D)J",false));
        if(result.getSort()==Type.LONG||result.getSort()==Type.DOUBLE){code.add(new InsnNode(Opcodes.LCMP));code.add(new JumpInsnNode(Opcodes.IFNE,different));}
        else code.add(new JumpInsnNode(result.getSort()==Type.OBJECT||result.getSort()==Type.ARRAY?Opcodes.IF_ACMPNE:Opcodes.IF_ICMPNE,different));
        code.add(new InsnNode(Opcodes.ICONST_1));code.add(new JumpInsnNode(Opcodes.GOTO,done));code.add(different);code.add(new InsnNode(Opcodes.ICONST_0));code.add(done);
    }
    static void unsafe(MethodNode method,int receiver,int offset,int length,String kind,LabelNode admitted){
        unsafe(method,receiver,offset,length,kind,admitted,false);
    }
    static void unsafeRead(MethodNode method,int receiver,int offset,String kind,LabelNode admitted){
        unsafe(method,receiver,offset,-1,kind,admitted,true);
    }
    private static void unsafe(MethodNode method,int receiver,int offset,int length,String kind,LabelNode admitted,boolean reading){
        AbstractInsnNode[] original=method.instructions.toArray();Type result=Type.getReturnType(method.desc);
        Type[] arguments=Type.getArgumentTypes(method.desc);int[] slots=new int[arguments.length];int local=(method.access&Opcodes.ACC_STATIC)==0?1:0;
        for(int i=0;i<slots.length;i++){slots[i]=local;local+=arguments[i].getSize();}
        int ticket=method.maxLocals++,error=method.maxLocals++,returned=method.maxLocals;method.maxLocals+=result.getSize();
        int accepted=method.maxLocals++;
        LabelNode start=new LabelNode(),end=new LabelNode(),handler=new LabelNode();InsnList enter=new InsnList();
        boolean copy=method.name.equals("copyMemory")||method.name.equals("copySwapMemory");
        if(copy){enter.add(receiver<0?new InsnNode(Opcodes.ACONST_NULL):new VarInsnNode(Opcodes.ALOAD,slots[0]));enter.add(new VarInsnNode(Opcodes.LLOAD,slots[receiver<0?0:1]));}
        enter.add(receiver<0?new InsnNode(Opcodes.ACONST_NULL):new VarInsnNode(Opcodes.ALOAD,receiver));enter.add(new VarInsnNode(Opcodes.LLOAD,offset));
        enter.add(length>=0?new VarInsnNode(Opcodes.LLOAD,length):new LdcInsnNode(kind));
        enter.add(new MethodInsnNode(Opcodes.INVOKESTATIC,BRIDGE,copy?"beginUnsafeCopy":reading?"beginUnsafeRead":"beginUnsafeMutation",copy?"(Ljava/lang/Object;JLjava/lang/Object;JJ)Ljava/lang/Object;":length>=0?"(Ljava/lang/Object;JJ)Ljava/lang/Object;":"(Ljava/lang/Object;JLjava/lang/String;)Ljava/lang/Object;",false));
        enter.add(new VarInsnNode(Opcodes.ASTORE,ticket));
        if(copy){enter.add(new VarInsnNode(Opcodes.ALOAD,ticket));enter.add(new FieldInsnNode(Opcodes.GETSTATIC,"java/lang/Boolean","FALSE","Ljava/lang/Boolean;"));enter.add(new JumpInsnNode(Opcodes.IF_ACMPNE,start));enter.add(new InsnNode(Opcodes.RETURN));}
        boolean scopedGuard=!reading&&!copy&&length<0;
        enter.add(start);
        if(scopedGuard){
            enter.add(new InsnNode(Opcodes.ICONST_0));enter.add(new VarInsnNode(Opcodes.ISTORE,accepted));method.instructions.insertBefore(original[0],enter);
            InsnList mark=new InsnList();mark.add(new InsnNode(Opcodes.ICONST_1));mark.add(new VarInsnNode(Opcodes.ISTORE,accepted));method.instructions.insert(admitted,mark);
        }else method.instructions.insert(admitted,enter);
        boolean body=scopedGuard;for(AbstractInsnNode at:original){
            if(at==admitted){body=true;continue;}if(!body||at.getOpcode()<Opcodes.IRETURN||at.getOpcode()>Opcodes.RETURN)continue;
            InsnList finish=new InsnList();if(result.getSort()!=Type.VOID)finish.add(new VarInsnNode(result.getOpcode(Opcodes.ISTORE),returned));
            finish.add(new VarInsnNode(Opcodes.ALOAD,ticket));
            if(scopedGuard){
                LabelNode refused=new LabelNode(),ready=new LabelNode();finish.add(new VarInsnNode(Opcodes.ILOAD,accepted));finish.add(new JumpInsnNode(Opcodes.IFEQ,refused));
                applied(finish,method.name,result,returned,arguments,slots);finish.add(new JumpInsnNode(Opcodes.GOTO,ready));finish.add(refused);finish.add(new InsnNode(Opcodes.ICONST_0));finish.add(ready);
            }else applied(finish,method.name,result,returned,arguments,slots);
            finish.add(new MethodInsnNode(Opcodes.INVOKESTATIC,BRIDGE,"finishFieldMutation","(Ljava/lang/Object;Z)V",false));
            if(result.getSort()!=Type.VOID)finish.add(new VarInsnNode(result.getOpcode(Opcodes.ILOAD),returned));method.instructions.insertBefore(at,finish);
        }
        method.instructions.add(end);method.instructions.add(handler);method.instructions.add(new VarInsnNode(Opcodes.ASTORE,error));
        method.instructions.add(new VarInsnNode(Opcodes.ALOAD,ticket));method.instructions.add(new VarInsnNode(Opcodes.ALOAD,error));
        method.instructions.add(new MethodInsnNode(Opcodes.INVOKESTATIC,BRIDGE,"fieldMutationFailed","(Ljava/lang/Object;Ljava/lang/Throwable;)V",false));
        method.instructions.add(new VarInsnNode(Opcodes.ALOAD,error));method.instructions.add(new InsnNode(Opcodes.ATHROW));
        method.tryCatchBlocks.add(new TryCatchBlockNode(start,end,handler,"java/lang/Throwable"));
    }
    static void nativeBulkCall(MethodNode method,MethodInsnNode call,int receiver,int offset,int length,int source,int sourceOffset){
        int ticket=method.maxLocals++,error=method.maxLocals++;LabelNode start=new LabelNode(),end=new LabelNode(),handler=new LabelNode(),resume=new LabelNode();
        boolean copy=sourceOffset>=0;InsnList enter=new InsnList();
        if(copy){enter.add(new VarInsnNode(Opcodes.ALOAD,source));enter.add(new VarInsnNode(Opcodes.LLOAD,sourceOffset));}
        enter.add(new VarInsnNode(Opcodes.ALOAD,receiver));enter.add(new VarInsnNode(Opcodes.LLOAD,offset));enter.add(new VarInsnNode(Opcodes.LLOAD,length));
        enter.add(new MethodInsnNode(Opcodes.INVOKESTATIC,BRIDGE,copy?"beginUnsafeCopy":"beginUnsafeMutation",copy?"(Ljava/lang/Object;JLjava/lang/Object;JJ)Ljava/lang/Object;":"(Ljava/lang/Object;JJ)Ljava/lang/Object;",false));enter.add(new VarInsnNode(Opcodes.ASTORE,ticket));
        if(copy){
            enter.add(new VarInsnNode(Opcodes.ALOAD,ticket));enter.add(new FieldInsnNode(Opcodes.GETSTATIC,"java/lang/Boolean","FALSE","Ljava/lang/Boolean;"));enter.add(new JumpInsnNode(Opcodes.IF_ACMPNE,start));
            Type[] arguments=Type.getArgumentTypes(call.desc);for(int i=arguments.length-1;i>=0;i--)enter.add(new InsnNode(arguments[i].getSize()==2?Opcodes.POP2:Opcodes.POP));
            if(call.getOpcode()!=Opcodes.INVOKESTATIC)enter.add(new InsnNode(Opcodes.POP));enter.add(new InsnNode(Opcodes.RETURN));
        }
        enter.add(start);
        method.instructions.insertBefore(call,enter);InsnList finish=new InsnList();finish.add(end);
        finish.add(new VarInsnNode(Opcodes.ALOAD,ticket));finish.add(new InsnNode(Opcodes.ICONST_1));finish.add(new MethodInsnNode(Opcodes.INVOKESTATIC,BRIDGE,"finishFieldMutation","(Ljava/lang/Object;Z)V",false));
        finish.add(new JumpInsnNode(Opcodes.GOTO,resume));finish.add(handler);finish.add(new VarInsnNode(Opcodes.ASTORE,error));finish.add(new VarInsnNode(Opcodes.ALOAD,ticket));finish.add(new VarInsnNode(Opcodes.ALOAD,error));
        finish.add(new MethodInsnNode(Opcodes.INVOKESTATIC,BRIDGE,"fieldMutationFailed","(Ljava/lang/Object;Ljava/lang/Throwable;)V",false));finish.add(new VarInsnNode(Opcodes.ALOAD,error));finish.add(new InsnNode(Opcodes.ATHROW));finish.add(resume);
        method.instructions.insert(call,finish);method.tryCatchBlocks.add(0,new TryCatchBlockNode(start,end,handler,"java/lang/Throwable"));
    }
}
