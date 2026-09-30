package dev.ronova.pro.agent;

import java.util.*;
import jdk.internal.org.objectweb.asm.*;
import jdk.internal.org.objectweb.asm.tree.*;

/** Array stores/copies at actual caller sites, and exact ArrayList mutation bodies including views. */
final class BackingBoundary {
    private static final String BRIDGE="dev/ronova/pro/bootstrap/BackingBridge";
    private static final Set<String> CORE_INSTALLED=java.util.concurrent.ConcurrentHashMap.newKeySet();
    static boolean coreInstalled() {return CORE_INSTALLED.containsAll(Set.of("java/util/AbstractCollection","java/util/ArrayList","java/util/ArrayList$Itr","java/util/ArrayList$ListItr","java/util/ArrayList$SubList","java/util/ArrayList$SubList$1","java/util/IdentityHashMap",
        "java/util/IdentityHashMap$IdentityHashMapIterator","java/util/IdentityHashMap$EntryIterator$Entry",
        "java/util/concurrent/ThreadPoolExecutor","java/util/concurrent/ScheduledThreadPoolExecutor"));}
    static byte[] transform(ClassLoader loader,String name,byte[] bytes) {
        ClassNode node=new ClassNode();new ClassReader(bytes).accept(node,0);boolean changed=false;
        if(loader==null&&(name.equals("java/util/concurrent/ThreadPoolExecutor")||name.equals("java/util/concurrent/ScheduledThreadPoolExecutor"))) {
            for(MethodNode method:node.methods)if(method.name.equals("shutdown")||method.name.equals("shutdownNow")) {
                InsnList code=new InsnList();LabelNode allowed=new LabelNode();code.add(new VarInsnNode(Opcodes.ALOAD,0));
                code.add(new MethodInsnNode(Opcodes.INVOKESTATIC,"dev/ronova/pro/bootstrap/TaskBridge","resourceReleaseAllowed","(Ljava/lang/Object;)Z",false));
                code.add(new JumpInsnNode(Opcodes.IFNE,allowed));
                if(method.name.equals("shutdownNow")) {code.add(new MethodInsnNode(Opcodes.INVOKESTATIC,"java/util/Collections","emptyList","()Ljava/util/List;",false));code.add(new InsnNode(Opcodes.ARETURN));}
                else code.add(new InsnNode(Opcodes.RETURN));
                code.add(allowed);method.instructions.insert(code);changed=true;
            }
        } else if(loader==null&&name.equals("java/util/IdentityHashMap")) {
            for(MethodNode method:node.methods) {
                if(!Set.of("put","putAll","remove","clear","replaceAll").contains(method.name))continue;
                Type[] args=Type.getArgumentTypes(method.desc);InsnList code=new InsnList();LabelNode original=new LabelNode();int result=method.maxLocals++;
                code.add(new VarInsnNode(Opcodes.ALOAD,0));code.add(new LdcInsnNode(method.name));code.add(new LdcInsnNode(args.length));code.add(new TypeInsnNode(Opcodes.ANEWARRAY,"java/lang/Object"));
                int local=1;for(int i=0;i<args.length;i++){code.add(new InsnNode(Opcodes.DUP));code.add(new LdcInsnNode(i));code.add(new VarInsnNode(args[i].getOpcode(Opcodes.ILOAD),local));ControlClassWriter.box(code,args[i]);code.add(new InsnNode(Opcodes.AASTORE));local+=args[i].getSize();}
                code.add(new MethodInsnNode(Opcodes.INVOKESTATIC,BRIDGE,"identityMutation","(Ljava/lang/Object;Ljava/lang/String;[Ljava/lang/Object;)Ljava/lang/Object;",false));
                code.add(new VarInsnNode(Opcodes.ASTORE,result));code.add(new VarInsnNode(Opcodes.ALOAD,result));code.add(new FieldInsnNode(Opcodes.GETSTATIC,BRIDGE,"UNHANDLED","Ljava/lang/Object;"));code.add(new JumpInsnNode(Opcodes.IF_ACMPEQ,original));
                Type returned=Type.getReturnType(method.desc);
                if(returned.getSort()==Type.VOID)code.add(new InsnNode(Opcodes.RETURN));
                else if(returned.getSort()==Type.BOOLEAN){code.add(new InsnNode(Opcodes.ICONST_0));code.add(new InsnNode(Opcodes.IRETURN));}
                else {code.add(new VarInsnNode(Opcodes.ALOAD,result));code.add(new InsnNode(Opcodes.ARETURN));}
                code.add(original);method.instructions.insert(code);changed=true;
            }
        } else if(loader==null&&(name.equals("java/util/IdentityHashMap$IdentityHashMapIterator")||name.equals("java/util/IdentityHashMap$EntryIterator$Entry"))) {
            for(MethodNode method:node.methods)if(method.name.equals("remove")||method.name.equals("setValue")) {
                InsnList code=new InsnList();LabelNode allowed=new LabelNode();code.add(new VarInsnNode(Opcodes.ALOAD,0));
                if(method.name.equals("setValue"))code.add(new FieldInsnNode(Opcodes.GETFIELD,name,"this$1","Ljava/util/IdentityHashMap$EntryIterator;"));
                code.add(new FieldInsnNode(Opcodes.GETFIELD,"java/util/IdentityHashMap$IdentityHashMapIterator","this$0","Ljava/util/IdentityHashMap;"));
                code.add(new MethodInsnNode(Opcodes.INVOKESTATIC,"dev/ronova/pro/bootstrap/TaskBridge","controlMutationAllowed","(Ljava/lang/Object;)Z",false));code.add(new JumpInsnNode(Opcodes.IFNE,allowed));
                if(method.name.equals("setValue")){code.add(new VarInsnNode(Opcodes.ALOAD,0));code.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL,name,"getValue","()Ljava/lang/Object;",false));code.add(new InsnNode(Opcodes.ARETURN));}
                else {code.add(new VarInsnNode(Opcodes.ALOAD,0));code.add(new InsnNode(Opcodes.ICONST_M1));code.add(new FieldInsnNode(Opcodes.PUTFIELD,name,"lastReturnedIndex","I"));code.add(new InsnNode(Opcodes.RETURN));}
                code.add(allowed);method.instructions.insert(code);changed=true;
            }
        } else if(loader==null&&name.equals("java/util/AbstractCollection")) {
            for(MethodNode method:node.methods)if(method.name.equals("remove")&&method.desc.equals("(Ljava/lang/Object;)Z")) {
                InsnList code=new InsnList();LabelNode allowed=new LabelNode();code.add(new VarInsnNode(Opcodes.ALOAD,0));code.add(new VarInsnNode(Opcodes.ALOAD,1));
                code.add(new MethodInsnNode(Opcodes.INVOKESTATIC,BRIDGE,"listViewRemovalDenied","(Ljava/lang/Object;Ljava/lang/Object;)Z",false));
                code.add(new JumpInsnNode(Opcodes.IFEQ,allowed));code.add(new InsnNode(Opcodes.ICONST_0));code.add(new InsnNode(Opcodes.IRETURN));code.add(allowed);method.instructions.insert(code);changed=true;
            }
        } else if(loader==null&&(name.equals("java/util/ArrayList$Itr")||name.equals("java/util/ArrayList$ListItr")||name.equals("java/util/ArrayList$SubList$1"))) {
            for(MethodNode method:node.methods)if(method.name.equals("remove")||method.name.equals("add")) {
                InsnList code=new InsnList();LabelNode allowed=new LabelNode();code.add(new VarInsnNode(Opcodes.ALOAD,0));
                boolean add=method.name.equals("add");code.add(add?new VarInsnNode(Opcodes.ALOAD,1):new InsnNode(Opcodes.ACONST_NULL));
                code.add(new InsnNode(add?Opcodes.ICONST_1:Opcodes.ICONST_0));
                code.add(new MethodInsnNode(Opcodes.INVOKESTATIC,BRIDGE,"listIteratorDenied","(Ljava/lang/Object;Ljava/lang/Object;Z)Z",false));
                code.add(new JumpInsnNode(Opcodes.IFEQ,allowed));code.add(new InsnNode(Opcodes.RETURN));code.add(allowed);method.instructions.insert(code);changed=true;
            }
        } else if(loader==null&&name.equals("java/util/ArrayList$SubList")) {
            for(MethodNode method:node.methods) {
                if(!Set.of("set","remove","add","addAll","removeRange","removeIf","batchRemove","replaceAll").contains(method.name))continue;
                Type[] args=Type.getArgumentTypes(method.desc);InsnList code=new InsnList();LabelNode original=new LabelNode();int result=method.maxLocals++;
                code.add(new VarInsnNode(Opcodes.ALOAD,0));code.add(new LdcInsnNode(method.name));code.add(new LdcInsnNode(args.length));code.add(new TypeInsnNode(Opcodes.ANEWARRAY,"java/lang/Object"));
                int local=1;for(int i=0;i<args.length;i++){code.add(new InsnNode(Opcodes.DUP));code.add(new LdcInsnNode(i));code.add(new VarInsnNode(args[i].getOpcode(Opcodes.ILOAD),local));ControlClassWriter.box(code,args[i]);code.add(new InsnNode(Opcodes.AASTORE));local+=args[i].getSize();}
                code.add(new MethodInsnNode(Opcodes.INVOKESTATIC,BRIDGE,"listViewMutation","(Ljava/lang/Object;Ljava/lang/String;[Ljava/lang/Object;)Ljava/lang/Object;",false));
                code.add(new VarInsnNode(Opcodes.ASTORE,result));code.add(new VarInsnNode(Opcodes.ALOAD,result));code.add(new FieldInsnNode(Opcodes.GETSTATIC,BRIDGE,"UNHANDLED","Ljava/lang/Object;"));code.add(new JumpInsnNode(Opcodes.IF_ACMPEQ,original));
                Type returned=Type.getReturnType(method.desc);
                if(returned.getSort()==Type.VOID)code.add(new InsnNode(Opcodes.RETURN));
                else {code.add(new VarInsnNode(Opcodes.ALOAD,result));if(returned.getSort()==Type.BOOLEAN){code.add(new TypeInsnNode(Opcodes.CHECKCAST,"java/lang/Boolean"));code.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL,"java/lang/Boolean","booleanValue","()Z",false));code.add(new InsnNode(Opcodes.IRETURN));}else code.add(new InsnNode(Opcodes.ARETURN));}
                code.add(original);method.instructions.insert(code);changed=true;
            }
        } else if(loader==null&&name.equals("java/util/ArrayList")) {
            for(MethodNode method:node.methods) {
                String operation=method.name;
                if(operation.equals("remove"))operation=method.desc.startsWith("(I)")?"removeIndex":"removeObject";
                if(!Set.of("set","removeIndex","removeObject","fastRemove","clear","removeRange","removeIf","batchRemove","replaceAllRange","add","addAll","sort","ensureCapacity","trimToSize").contains(operation))continue;
                Type[] args=Type.getArgumentTypes(method.desc);InsnList code=new InsnList();LabelNode original=new LabelNode();int result=method.maxLocals++;
                code.add(new VarInsnNode(Opcodes.ALOAD,0));code.add(new LdcInsnNode(operation));code.add(new LdcInsnNode(args.length));code.add(new TypeInsnNode(Opcodes.ANEWARRAY,"java/lang/Object"));
                int local=1;
                for(int i=0;i<args.length;i++) {
                    code.add(new InsnNode(Opcodes.DUP));code.add(new LdcInsnNode(i));code.add(new VarInsnNode(args[i].getOpcode(Opcodes.ILOAD),local));
                    ControlClassWriter.box(code,args[i]);code.add(new InsnNode(Opcodes.AASTORE));local+=args[i].getSize();
                }
                code.add(new MethodInsnNode(Opcodes.INVOKESTATIC,BRIDGE,"listMutation","(Ljava/lang/Object;Ljava/lang/String;[Ljava/lang/Object;)Ljava/lang/Object;",false));
                code.add(new VarInsnNode(Opcodes.ASTORE,result));code.add(new VarInsnNode(Opcodes.ALOAD,result));code.add(new FieldInsnNode(Opcodes.GETSTATIC,BRIDGE,"UNHANDLED","Ljava/lang/Object;"));
                code.add(new JumpInsnNode(Opcodes.IF_ACMPEQ,original));Type returned=Type.getReturnType(method.desc);
                if(returned.getSort()==Type.VOID)code.add(new InsnNode(Opcodes.RETURN));
                else {
                    code.add(new VarInsnNode(Opcodes.ALOAD,result));
                    if(returned.getSort()==Type.BOOLEAN) {code.add(new TypeInsnNode(Opcodes.CHECKCAST,"java/lang/Boolean"));code.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL,"java/lang/Boolean","booleanValue","()Z",false));code.add(new InsnNode(Opcodes.IRETURN));}
                    else {code.add(new TypeInsnNode(Opcodes.CHECKCAST,returned.getInternalName()));code.add(new InsnNode(Opcodes.ARETURN));}
                }
                code.add(original);method.instructions.insert(code);changed=true;
            }
        } else if(loader!=null&&!name.startsWith("dev/ronova/pro/")&&!name.startsWith("it/unimi/dsi/fastutil/")||name.startsWith("dev/ronova/pro/validation/")) {
            for(MethodNode method:node.methods)for(AbstractInsnNode at:method.instructions.toArray()) {
                int op=at.getOpcode();
                if(op==Opcodes.AASTORE) {
                    method.instructions.set(at,new MethodInsnNode(Opcodes.INVOKESTATIC,BRIDGE,"arrayStore","(Ljava/lang/Object;ILjava/lang/Object;)V",false));changed=true;
                } else if(op>=Opcodes.IASTORE&&op<=Opcodes.SASTORE) {
                    String helper=switch(op) {case Opcodes.IASTORE->"intStore";case Opcodes.LASTORE->"longStore";case Opcodes.FASTORE->"floatStore";case Opcodes.DASTORE->"doubleStore";case Opcodes.BASTORE->"byteStore";case Opcodes.CASTORE->"charStore";default->"shortStore";};
                    String type=op==Opcodes.LASTORE?"J":op==Opcodes.FASTORE?"F":op==Opcodes.DASTORE?"D":"I";
                    method.instructions.set(at,new MethodInsnNode(Opcodes.INVOKESTATIC,BRIDGE,helper,"(Ljava/lang/Object;I"+type+")V",false));changed=true;
                } else if(at instanceof MethodInsnNode call&&call.getOpcode()==Opcodes.INVOKESTATIC&&call.owner.equals("java/util/Arrays")
                        &&Set.of("fill","sort","parallelSort","setAll","parallelSetAll").contains(call.name)) {
                    call.owner=BRIDGE;changed=true;
                } else if(at instanceof MethodInsnNode call&&call.getOpcode()==Opcodes.INVOKESTATIC&&call.owner.equals("java/lang/System")&&call.name.equals("arraycopy")) {
                    call.owner=BRIDGE;call.name="copy";changed=true;
                } else if(at instanceof MethodInsnNode call&&call.getOpcode()==Opcodes.INVOKESTATIC&&call.owner.equals("java/lang/reflect/Array")&&Set.of("set","setBoolean","setByte","setChar","setShort","setInt","setLong","setFloat","setDouble").contains(call.name)) {
                    call.owner=BRIDGE;changed=true;
                }
            }
        }
        if(!changed)return null;ControlClassWriter writer=new ControlClassWriter(loader,node);node.accept(writer);
        if(loader==null)CORE_INSTALLED.add(name);return writer.toByteArray();
    }
}
