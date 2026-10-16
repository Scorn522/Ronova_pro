package dev.ronova.pro.agent;

import jdk.internal.org.objectweb.asm.Type;
import jdk.internal.org.objectweb.asm.Opcodes;
import jdk.internal.org.objectweb.asm.tree.*;

/** Store-site guards retain original callback evaluation and the writer's real return contract. */
final class IndexValueBoundary implements Opcodes {
    static final String BRIDGE="dev/ronova/pro/bootstrap/ControlBridge";
    private static final String ALLOW="(Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;)Z";
    private IndexValueBoundary() { }

    static int fastutilStores(ClassNode owner,String mapOwner,boolean entry) {
        int count=0;
        for(MethodNode method:owner.methods) {
            if(!java.util.Set.of("put","putAndMoveToFirst","putAndMoveToLast","replace","computeIfAbsent","computeIfPresent","compute","merge","setValue","remove").contains(method.name))continue;
            for(AbstractInsnNode instruction:method.instructions.toArray()) {
                if(instruction.getOpcode()==AASTORE) {
                    int value=method.maxLocals++,index=method.maxLocals++,array=method.maxLocals++,map=method.maxLocals++,token=method.maxLocals++,error=method.maxLocals++;
                    InsnList code=new InsnList();LabelNode allow=new LabelNode(),start=new LabelNode(),end=new LabelNode(),handler=new LabelNode();
                    code.add(new VarInsnNode(ASTORE,value));code.add(new VarInsnNode(ISTORE,index));code.add(new VarInsnNode(ASTORE,array));
                    code.add(new VarInsnNode(ALOAD,0));
                    if(entry)code.add(new FieldInsnNode(GETFIELD,owner.name,"this$0","L"+mapOwner+";"));
                    code.add(new VarInsnNode(ASTORE,map));
                    code.add(new InsnNode(ACONST_NULL));code.add(new VarInsnNode(ASTORE,token));
                    code.add(new VarInsnNode(ALOAD,array));code.add(new VarInsnNode(ALOAD,map));
                    code.add(new FieldInsnNode(GETFIELD,mapOwner,"value","[Ljava/lang/Object;"));code.add(new JumpInsnNode(IF_ACMPNE,allow));
                    fastBegin(code,mapOwner,map,index,array,value);code.add(new VarInsnNode(ASTORE,token));
                    code.add(new VarInsnNode(ALOAD,token));
                    code.add(new FieldInsnNode(GETSTATIC,"java/lang/Boolean","FALSE","Ljava/lang/Boolean;"));
                    code.add(new JumpInsnNode(IF_ACMPNE,allow));
                    deniedFastReturn(code,method,mapOwner,map,index);
                    code.add(allow);code.add(new VarInsnNode(ALOAD,array));code.add(new VarInsnNode(ILOAD,index));code.add(new VarInsnNode(ALOAD,value));
                    InsnList tail=new InsnList();tail.add(end);tail.add(new VarInsnNode(ALOAD,token));
                    tail.add(new MethodInsnNode(INVOKESTATIC,"dev/ronova/pro/bootstrap/ControlBridge","endIndexMutation","(Ljava/lang/Object;)V",false));
                    method.instructions.insertBefore(instruction,code);method.instructions.insertBefore(instruction,start);
                    method.instructions.insert(instruction,tail);
                    method.instructions.add(handler);method.instructions.add(new VarInsnNode(ASTORE,error));
                    method.instructions.add(new VarInsnNode(ALOAD,token));
                    method.instructions.add(new MethodInsnNode(INVOKESTATIC,"dev/ronova/pro/bootstrap/ControlBridge","endIndexMutation","(Ljava/lang/Object;)V",false));
                    method.instructions.add(new VarInsnNode(ALOAD,error));method.instructions.add(new InsnNode(ATHROW));
                    method.tryCatchBlocks.add(new TryCatchBlockNode(start,end,handler,null));count++;
                } else if((method.name.equals("compute")||method.name.equals("computeIfPresent")||method.name.equals("merge")||method.name.equals("remove")&&Type.getReturnType(method.desc).getSort()==Type.BOOLEAN)
                        &&instruction instanceof MethodInsnNode call&&call.owner.equals(mapOwner)
                        &&(call.name.equals("removeEntry")||call.name.equals("removeNullEntry"))) {
                    int map=method.maxLocals++,index=method.maxLocals++,token=method.maxLocals++,error=method.maxLocals++;boolean zero=call.name.equals("removeNullEntry");
                    InsnList code=new InsnList();LabelNode allow=new LabelNode(),start=new LabelNode(),end=new LabelNode(),handler=new LabelNode();
                    if(!zero)code.add(new VarInsnNode(ISTORE,index));
                    code.add(new VarInsnNode(ASTORE,map));
                    if(zero) {code.add(new VarInsnNode(ALOAD,map));code.add(new FieldInsnNode(GETFIELD,mapOwner,"n","I"));code.add(new VarInsnNode(ISTORE,index));}
                    fastBeginRemoval(code,mapOwner,map,index);code.add(new VarInsnNode(ASTORE,token));
                    code.add(new VarInsnNode(ALOAD,token));
                    code.add(new FieldInsnNode(GETSTATIC,"java/lang/Boolean","FALSE","Ljava/lang/Boolean;"));
                    code.add(new JumpInsnNode(IF_ACMPNE,allow));
                    deniedFastReturn(code,method,mapOwner,map,index);
                    code.add(allow);code.add(new VarInsnNode(ALOAD,map));if(!zero)code.add(new VarInsnNode(ILOAD,index));
                    InsnList tail=new InsnList();tail.add(end);tail.add(new VarInsnNode(ALOAD,token));
                    tail.add(new MethodInsnNode(INVOKESTATIC,"dev/ronova/pro/bootstrap/ControlBridge","endIndexMutation","(Ljava/lang/Object;)V",false));
                    method.instructions.insertBefore(instruction,code);method.instructions.insertBefore(instruction,start);
                    method.instructions.insert(instruction,tail);
                    method.instructions.add(handler);method.instructions.add(new VarInsnNode(ASTORE,error));
                    method.instructions.add(new VarInsnNode(ALOAD,token));
                    method.instructions.add(new MethodInsnNode(INVOKESTATIC,"dev/ronova/pro/bootstrap/ControlBridge","endIndexMutation","(Ljava/lang/Object;)V",false));
                    method.instructions.add(new VarInsnNode(ALOAD,error));method.instructions.add(new InsnNode(ATHROW));
                    method.tryCatchBlocks.add(new TryCatchBlockNode(start,end,handler,null));
                }
            }
        }
        return count;
    }
    private static void fastCheck(InsnList code,String owner,int map,int index,int value) {
        boolean wide=owner.contains("/longs/");
        code.add(new VarInsnNode(ALOAD,map));code.add(new VarInsnNode(ALOAD,map));code.add(new FieldInsnNode(GETFIELD,owner,"key",wide?"[J":"[I"));
        code.add(new VarInsnNode(ILOAD,index));code.add(new InsnNode(wide?LALOAD:IALOAD));
        code.add(new MethodInsnNode(INVOKESTATIC,wide?"java/lang/Long":"java/lang/Integer","valueOf",wide?"(J)Ljava/lang/Long;":"(I)Ljava/lang/Integer;",false));
        fastValue(code,owner,map,index);
        code.add(value<0?new InsnNode(ACONST_NULL):new VarInsnNode(ALOAD,value));
        code.add(new MethodInsnNode(INVOKESTATIC,"dev/ronova/pro/bootstrap/ControlBridge","indexWriteAllowed",ALLOW,false));
    }
    private static void fastBegin(InsnList code,String owner,int map,int index,int array,int value) {
        boolean wide=owner.contains("/longs/");
        code.add(new VarInsnNode(ALOAD,map));code.add(new VarInsnNode(ALOAD,map));
        code.add(new FieldInsnNode(GETFIELD,owner,"key",wide?"[J":"[I"));
        code.add(new VarInsnNode(ILOAD,index));code.add(new InsnNode(wide?LALOAD:IALOAD));
        code.add(new MethodInsnNode(INVOKESTATIC,wide?"java/lang/Long":"java/lang/Integer","valueOf",
                wide?"(J)Ljava/lang/Long;":"(I)Ljava/lang/Integer;",false));
        code.add(new VarInsnNode(ALOAD,array));code.add(new VarInsnNode(ILOAD,index));code.add(new VarInsnNode(ALOAD,value));
        code.add(new MethodInsnNode(INVOKESTATIC,"dev/ronova/pro/bootstrap/ControlBridge","beginIndexArrayWrite",
                "(Ljava/lang/Object;Ljava/lang/Object;[Ljava/lang/Object;ILjava/lang/Object;)Ljava/lang/Object;",false));
    }
    private static void fastBeginRemoval(InsnList code,String owner,int map,int index) {
        boolean wide=owner.contains("/longs/");
        code.add(new VarInsnNode(ALOAD,map));code.add(new VarInsnNode(ALOAD,map));
        code.add(new FieldInsnNode(GETFIELD,owner,"key",wide?"[J":"[I"));
        code.add(new VarInsnNode(ILOAD,index));code.add(new InsnNode(wide?LALOAD:IALOAD));
        code.add(new MethodInsnNode(INVOKESTATIC,wide?"java/lang/Long":"java/lang/Integer","valueOf",
                wide?"(J)Ljava/lang/Long;":"(I)Ljava/lang/Integer;",false));
        code.add(new VarInsnNode(ALOAD,map));code.add(new FieldInsnNode(GETFIELD,owner,"value","[Ljava/lang/Object;"));
        code.add(new VarInsnNode(ILOAD,index));
        code.add(new MethodInsnNode(INVOKESTATIC,"dev/ronova/pro/bootstrap/ControlBridge","beginIndexArrayRemoval",
                "(Ljava/lang/Object;Ljava/lang/Object;[Ljava/lang/Object;I)Ljava/lang/Object;",false));
    }
    private static void fastValue(InsnList code,String owner,int map,int index) {
        code.add(new VarInsnNode(ALOAD,map));code.add(new FieldInsnNode(GETFIELD,owner,"value","[Ljava/lang/Object;"));
        code.add(new VarInsnNode(ILOAD,index));code.add(new InsnNode(AALOAD));
    }
    private static void deniedFastReturn(InsnList code,MethodNode method,String owner,int map,int index) {
        if(Type.getReturnType(method.desc).getSort()==Type.BOOLEAN) {code.add(new InsnNode(ICONST_0));code.add(new InsnNode(IRETURN));}
        else {fastValue(code,owner,map,index);code.add(new InsnNode(ARETURN));}
    }
}
