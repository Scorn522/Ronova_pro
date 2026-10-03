package dev.ronova.pro.agent;

import jdk.internal.org.objectweb.asm.Type;
import jdk.internal.org.objectweb.asm.Opcodes;
import jdk.internal.org.objectweb.asm.tree.*;

/** Store-site guards retain original callback evaluation and the writer's real return contract. */
final class IndexValueBoundary implements Opcodes {
    static final String BRIDGE="dev/ronova/pro/bootstrap/TaskBridge";
    private static final String NODE="java/util/HashMap$Node";
    private static final String ALLOW="(Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;)Z";
    private IndexValueBoundary() { }

    static int hashStores(ClassNode owner) {
        int count=0;
        for(MethodNode method:owner.methods) {
            if((method.access&ACC_STATIC)!=0)continue;
            Integer resolved=null;
            for(AbstractInsnNode instruction:method.instructions.toArray())
                if(instruction instanceof FieldInsnNode field&&field.getOpcode()==PUTFIELD
                        &&(field.owner.equals(NODE)||owner.name.equals("java/util/LinkedHashMap")
                            &&field.owner.equals("java/util/LinkedHashMap$Entry"))
                        &&field.name.equals("value")&&field.desc.equals("Ljava/lang/Object;")) {
                    if(method.name.equals("<init>"))continue;
                    AbstractInsnNode value=previous(instruction),node=previous(value);
                    if(node instanceof VarInsnNode load&&load.getOpcode()==ALOAD)resolved=load.var;
                    int proposed=method.maxLocals++,target=method.maxLocals++,token=method.maxLocals++,error=method.maxLocals++;
                    InsnList guard=new InsnList();LabelNode allowed=new LabelNode(),skip=new LabelNode(),start=new LabelNode(),end=new LabelNode(),handler=new LabelNode();
                    guard.add(new VarInsnNode(ASTORE,proposed));guard.add(new VarInsnNode(ASTORE,target));
                    guard.add(new VarInsnNode(ALOAD,0));guard.add(new VarInsnNode(ALOAD,target));
                    guard.add(new VarInsnNode(ALOAD,proposed));
                    guard.add(new MethodInsnNode(INVOKESTATIC,BRIDGE,"beginIndexNodeWrite",
                            "(Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;",false));
                    guard.add(new VarInsnNode(ASTORE,token));
                    guard.add(new VarInsnNode(ALOAD,token));
                    guard.add(new FieldInsnNode(GETSTATIC,"java/lang/Boolean","FALSE","Ljava/lang/Boolean;"));
                    guard.add(new JumpInsnNode(IF_ACMPNE,allowed));
                    Type result=Type.getReturnType(method.desc);
                    if(result.getSort()==Type.VOID)guard.add(new JumpInsnNode(GOTO,skip));
                    else if(result.getSort()==Type.BOOLEAN) { guard.add(new InsnNode(ICONST_0));guard.add(new InsnNode(IRETURN)); }
                    else { nodeValue(guard,target);guard.add(new InsnNode(ARETURN)); }
                    guard.add(allowed);guard.add(new VarInsnNode(ALOAD,target));guard.add(new VarInsnNode(ALOAD,proposed));
                    InsnList tail=new InsnList();tail.add(end);tail.add(new VarInsnNode(ALOAD,token));
                    tail.add(new MethodInsnNode(INVOKESTATIC,BRIDGE,"endIndexMutation","(Ljava/lang/Object;)V",false));
                    tail.add(skip);
                    method.instructions.insertBefore(instruction,guard);method.instructions.insertBefore(instruction,start);
                    method.instructions.insert(instruction,tail);
                    method.instructions.add(handler);method.instructions.add(new VarInsnNode(ASTORE,error));
                    method.instructions.add(new VarInsnNode(ALOAD,token));
                    method.instructions.add(new MethodInsnNode(INVOKESTATIC,BRIDGE,"endIndexMutation","(Ljava/lang/Object;)V",false));
                    method.instructions.add(new VarInsnNode(ALOAD,error));method.instructions.add(new InsnNode(ATHROW));
                    method.tryCatchBlocks.add(new TryCatchBlockNode(start,end,handler,null));count++;
                }
            if(method.name.equals("compute")||method.name.equals("computeIfPresent")||method.name.equals("merge")) {
                if(resolved==null)throw new IllegalStateException("HASH_COMPUTE_NODE_LAYOUT:"+method.name);
                for(AbstractInsnNode instruction:method.instructions.toArray())
                    if(instruction instanceof MethodInsnNode call&&call.owner.equals("java/util/HashMap")&&call.name.equals("removeNode")) {
                        InsnList check=new InsnList();LabelNode allowed=new LabelNode();
                        hashCheck(check,resolved,-1);check.add(new JumpInsnNode(IFNE,allowed));
                        nodeValue(check,resolved);check.add(new InsnNode(ARETURN));check.add(allowed);
                        method.instructions.insertBefore(instruction,check);
                    }
            }
        }
        return count;
    }
    private static void hashCheck(InsnList code,int node,int proposed) {
        code.add(new VarInsnNode(ALOAD,0));code.add(new VarInsnNode(ALOAD,node));
        code.add(new FieldInsnNode(GETFIELD,NODE,"key","Ljava/lang/Object;"));nodeValue(code,node);
        code.add(proposed<0?new InsnNode(ACONST_NULL):new VarInsnNode(ALOAD,proposed));
        code.add(new MethodInsnNode(INVOKESTATIC,BRIDGE,"indexWriteAllowed",ALLOW,false));
    }
    private static void nodeValue(InsnList code,int slot) {
        code.add(new VarInsnNode(ALOAD,slot));code.add(new FieldInsnNode(GETFIELD,NODE,"value","Ljava/lang/Object;"));
    }
    static int defaultStores(ClassNode owner) {
        int count=0;
        for(MethodNode method:owner.methods)for(AbstractInsnNode instruction:method.instructions.toArray())
            if(instruction instanceof MethodInsnNode call&&call.getOpcode()==INVOKEINTERFACE&&call.owner.equals("java/util/Map")
                    &&(call.name.equals("put")&&call.desc.equals("(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;")
                    ||call.name.equals("remove")&&call.desc.equals("(Ljava/lang/Object;)Ljava/lang/Object;"))) {
                boolean put=call.name.equals("put");int value=method.maxLocals++,key=method.maxLocals++,map=method.maxLocals++,retained=method.maxLocals++;
                InsnList code=new InsnList();LabelNode allow=new LabelNode();
                if(put)code.add(new VarInsnNode(ASTORE,value));
                code.add(new VarInsnNode(ASTORE,key));code.add(new VarInsnNode(ASTORE,map));
                code.add(new VarInsnNode(ALOAD,map));code.add(new VarInsnNode(ALOAD,key));
                code.add(put?new VarInsnNode(ALOAD,value):new InsnNode(ACONST_NULL));
                code.add(new MethodInsnNode(INVOKESTATIC,BRIDGE,"deniedIndexCurrent","(Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;",false));
                code.add(new VarInsnNode(ASTORE,retained));code.add(new VarInsnNode(ALOAD,retained));code.add(new JumpInsnNode(IFNULL,allow));
                if(Type.getReturnType(method.desc).getSort()==Type.BOOLEAN) {code.add(new InsnNode(ICONST_0));code.add(new InsnNode(IRETURN));}
                else {code.add(new VarInsnNode(ALOAD,retained));code.add(new InsnNode(ARETURN));}
                code.add(allow);code.add(new VarInsnNode(ALOAD,map));code.add(new VarInsnNode(ALOAD,key));
                if(put)code.add(new VarInsnNode(ALOAD,value));
                method.instructions.insertBefore(instruction,code);count++;
            }
        return count;
    }
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
                    tail.add(new MethodInsnNode(INVOKESTATIC,BRIDGE,"endIndexMutation","(Ljava/lang/Object;)V",false));
                    method.instructions.insertBefore(instruction,code);method.instructions.insertBefore(instruction,start);
                    method.instructions.insert(instruction,tail);
                    method.instructions.add(handler);method.instructions.add(new VarInsnNode(ASTORE,error));
                    method.instructions.add(new VarInsnNode(ALOAD,token));
                    method.instructions.add(new MethodInsnNode(INVOKESTATIC,BRIDGE,"endIndexMutation","(Ljava/lang/Object;)V",false));
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
                    tail.add(new MethodInsnNode(INVOKESTATIC,BRIDGE,"endIndexMutation","(Ljava/lang/Object;)V",false));
                    method.instructions.insertBefore(instruction,code);method.instructions.insertBefore(instruction,start);
                    method.instructions.insert(instruction,tail);
                    method.instructions.add(handler);method.instructions.add(new VarInsnNode(ASTORE,error));
                    method.instructions.add(new VarInsnNode(ALOAD,token));
                    method.instructions.add(new MethodInsnNode(INVOKESTATIC,BRIDGE,"endIndexMutation","(Ljava/lang/Object;)V",false));
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
        code.add(new MethodInsnNode(INVOKESTATIC,BRIDGE,"indexWriteAllowed",ALLOW,false));
    }
    private static void fastBegin(InsnList code,String owner,int map,int index,int array,int value) {
        boolean wide=owner.contains("/longs/");
        code.add(new VarInsnNode(ALOAD,map));code.add(new VarInsnNode(ALOAD,map));
        code.add(new FieldInsnNode(GETFIELD,owner,"key",wide?"[J":"[I"));
        code.add(new VarInsnNode(ILOAD,index));code.add(new InsnNode(wide?LALOAD:IALOAD));
        code.add(new MethodInsnNode(INVOKESTATIC,wide?"java/lang/Long":"java/lang/Integer","valueOf",
                wide?"(J)Ljava/lang/Long;":"(I)Ljava/lang/Integer;",false));
        code.add(new VarInsnNode(ALOAD,array));code.add(new VarInsnNode(ILOAD,index));code.add(new VarInsnNode(ALOAD,value));
        code.add(new MethodInsnNode(INVOKESTATIC,BRIDGE,"beginIndexArrayWrite",
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
        code.add(new MethodInsnNode(INVOKESTATIC,BRIDGE,"beginIndexArrayRemoval",
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
    private static AbstractInsnNode previous(AbstractInsnNode instruction) {
        if(instruction==null)return null;
        for(AbstractInsnNode value=instruction.getPrevious();value!=null;value=value.getPrevious())if(value.getOpcode()>=0)return value;
        return null;
    }
}
