package dev.ronova.pro.agent;

import java.util.Set;
import jdk.internal.org.objectweb.asm.Opcodes;
import jdk.internal.org.objectweb.asm.tree.*;

/** Exact JDK 17 writer scopes, installed around existing guards and every exceptional exit. */
final class SourceMapBoundary {
    private static final String BRIDGE="dev/ronova/pro/bootstrap/SourceMapBridge";
    private static final Set<String> WRITERS=Set.of("putVal","putMapEntries","removeNode","clear","replace",
            "computeIfAbsent","computeIfPresent","compute","merge","replaceAll","forEach");
    private static final Set<String> LINKED_WRITERS=Set.of("get","getOrDefault","clear","replaceAll","forEach",
            "afterNodeAccess","afterNodeInsertion","afterNodeRemoval","newNode","replacementNode",
            "newTreeNode","replacementTreeNode","reinitialize","linkNodeLast","transferLinks");
    static int weave(ClassNode owner) {
        boolean map=owner.name.equals("java/util/HashMap"),linked=owner.name.equals("java/util/LinkedHashMap");int count=0;
        for(MethodNode method:owner.methods) {
            if((method.access&(Opcodes.ACC_STATIC|Opcodes.ACC_ABSTRACT|Opcodes.ACC_NATIVE))!=0)continue;
            if(map&&method.name.equals("putVal")) {
                InsnList check=new InsnList();LabelNode allowed=new LabelNode();
                check.add(new VarInsnNode(Opcodes.ALOAD,0));check.add(new VarInsnNode(Opcodes.ALOAD,2));check.add(new VarInsnNode(Opcodes.ALOAD,3));
                check.add(new MethodInsnNode(Opcodes.INVOKESTATIC,BRIDGE,"valueAllowed","(Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;)Z",false));
                check.add(new JumpInsnNode(Opcodes.IFNE,allowed));currentValue(check,0,2);check.add(new InsnNode(Opcodes.ARETURN));check.add(allowed);method.instructions.insert(check);
            }
            if(map&&Set.of("computeIfAbsent","compute","merge").contains(method.name)) {
                for(AbstractInsnNode at:method.instructions.toArray())if(at instanceof MethodInsnNode call
                        &&(call.name.equals("newNode")||call.name.equals("putTreeVal"))) {
                    var types=jdk.internal.org.objectweb.asm.Type.getArgumentTypes(call.desc);int[] locals=new int[types.length];
                    for(int n=0;n<types.length;n++) {locals[n]=method.maxLocals;method.maxLocals+=types[n].getSize();}
                    int receiver=method.maxLocals++;boolean tree=call.name.equals("putTreeVal");
                    int mapLocal=tree?locals[0]:receiver,key=tree?locals[3]:locals[1],value=tree?locals[4]:locals[2];
                    InsnList check=new InsnList();LabelNode allowed=new LabelNode();
                    for(int n=types.length-1;n>=0;n--)check.add(new VarInsnNode(types[n].getOpcode(Opcodes.ISTORE),locals[n]));
                    check.add(new VarInsnNode(Opcodes.ASTORE,receiver));check.add(new VarInsnNode(Opcodes.ALOAD,mapLocal));
                    check.add(new VarInsnNode(Opcodes.ALOAD,key));check.add(new VarInsnNode(Opcodes.ALOAD,value));
                    check.add(new MethodInsnNode(Opcodes.INVOKESTATIC,BRIDGE,"valueAllowed","(Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;)Z",false));
                    check.add(new JumpInsnNode(Opcodes.IFNE,allowed));currentValue(check,mapLocal,key);check.add(new InsnNode(Opcodes.ARETURN));check.add(allowed);
                    check.add(new VarInsnNode(Opcodes.ALOAD,receiver));for(int n=0;n<types.length;n++)check.add(new VarInsnNode(types[n].getOpcode(Opcodes.ILOAD),locals[n]));
                    method.instructions.insertBefore(at,check);
                }
            }
            if((map||linked)&&(method.name.equals("<init>")||method.name.equals("clone"))) {
                for(AbstractInsnNode at:method.instructions.toArray())if(at.getOpcode()==Opcodes.RETURN||at.getOpcode()==Opcodes.ARETURN) {
                    InsnList code=new InsnList();code.add(method.name.equals("clone")?new InsnNode(Opcodes.DUP):new VarInsnNode(Opcodes.ALOAD,0));
                    code.add(new MethodInsnNode(Opcodes.INVOKESTATIC,BRIDGE,"born","(Ljava/lang/Object;)V",false));method.instructions.insertBefore(at,code);
                }
            }
            if((map||linked)&&Set.of("newNode","replacementNode","newTreeNode","replacementTreeNode").contains(method.name)) {
                for(AbstractInsnNode at:method.instructions.toArray())if(at.getOpcode()==Opcodes.ARETURN) {
                    InsnList code=new InsnList();code.add(new InsnNode(Opcodes.DUP));code.add(new VarInsnNode(Opcodes.ALOAD,0));code.add(new InsnNode(Opcodes.SWAP));
                    code.add(new MethodInsnNode(Opcodes.INVOKESTATIC,BRIDGE,"node","(Ljava/lang/Object;Ljava/lang/Object;)V",false));method.instructions.insertBefore(at,code);
                }
            }
            if(map?WRITERS.contains(method.name):linked?LINKED_WRITERS.contains(method.name):method.name.equals("setValue")) { scope(method,linked);count++; }
        }
        return count;
    }
    private static void currentValue(InsnList code,int map,int key) {
        code.add(new VarInsnNode(Opcodes.ALOAD,map));code.add(new VarInsnNode(Opcodes.ALOAD,key));
        code.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL,"java/util/HashMap","get","(Ljava/lang/Object;)Ljava/lang/Object;",false));
    }
    private static void scope(MethodNode method,boolean linked) {
        int token=method.maxLocals++,error=method.maxLocals++;
        LabelNode start=new LabelNode(),end=new LabelNode(),handler=new LabelNode();
        AbstractInsnNode[] original=method.instructions.toArray();
        InsnList head=new InsnList();head.add(new VarInsnNode(Opcodes.ALOAD,0));
        if(linked&&method.name.equals("afterNodeAccess")) {
            head.add(new VarInsnNode(Opcodes.ALOAD,1));
            head.add(new MethodInsnNode(Opcodes.INVOKESTATIC,BRIDGE,"enterNodeAccess","(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;",false));
        }else {
            head.add(new InsnNode(method.name.equals("forEach")||linked&&Set.of("get","getOrDefault").contains(method.name)?Opcodes.ICONST_0:Opcodes.ICONST_1));
            head.add(new MethodInsnNode(Opcodes.INVOKESTATIC,BRIDGE,"enter","(Ljava/lang/Object;Z)Ljava/lang/Object;",false));
        }
        head.add(new VarInsnNode(Opcodes.ASTORE,token));head.add(start);method.instructions.insert(head);
        for(AbstractInsnNode at:original)if(at.getOpcode()>=Opcodes.IRETURN&&at.getOpcode()<=Opcodes.RETURN) {
            InsnList exit=new InsnList();exit.add(new VarInsnNode(Opcodes.ALOAD,token));
            exit.add(new MethodInsnNode(Opcodes.INVOKESTATIC,BRIDGE,"exit","(Ljava/lang/Object;)V",false));method.instructions.insertBefore(at,exit);
        }
        method.instructions.add(end);method.instructions.add(handler);method.instructions.add(new VarInsnNode(Opcodes.ASTORE,error));
        method.instructions.add(new VarInsnNode(Opcodes.ALOAD,token));method.instructions.add(new MethodInsnNode(Opcodes.INVOKESTATIC,BRIDGE,"exit","(Ljava/lang/Object;)V",false));
        method.instructions.add(new VarInsnNode(Opcodes.ALOAD,error));method.instructions.add(new InsnNode(Opcodes.ATHROW));
        method.tryCatchBlocks.add(new TryCatchBlockNode(start,end,handler,null));
    }
}
