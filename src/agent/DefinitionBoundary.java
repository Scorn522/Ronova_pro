package dev.ronova.pro.agent;

import jdk.internal.org.objectweb.asm.*;
import jdk.internal.org.objectweb.asm.tree.*;

/** Rewrites the bytes handed to the VM, before hidden initialization can run. */
final class DefinitionBoundary {
    private static final String BRIDGE="dev/ronova/pro/bootstrap/DefinitionBridge";
    static byte[] platform(ClassLoader loader,String name,byte[] bytes) {
        byte[] timer=TimerBoundary.transform(loader,name,bytes);if(timer!=null)return timer;
        if(loader==null&&"java/lang/ref/Reference".equals(name)) {
            ClassNode reference=new ClassNode();new ClassReader(bytes).accept(reference,0);
            for(MethodNode method:reference.methods)if(method.name.equals("clear")&&method.desc.equals("()V")||method.name.equals("enqueue")&&method.desc.equals("()Z")) {
                InsnList guard=new InsnList();LabelNode allowed=new LabelNode();guard.add(new VarInsnNode(Opcodes.ALOAD,0));
                guard.add(new MethodInsnNode(Opcodes.INVOKESTATIC,"dev/ronova/pro/bootstrap/TaskBridge","referenceRetirementAllowed","(Ljava/lang/Object;)Z",false));guard.add(new JumpInsnNode(Opcodes.IFNE,allowed));
                if(method.desc.equals("()Z"))guard.add(new InsnNode(Opcodes.ICONST_0));guard.add(new InsnNode(method.desc.equals("()Z")?Opcodes.IRETURN:Opcodes.RETURN));guard.add(allowed);method.instructions.insert(guard);
            }
            ControlClassWriter writer=new ControlClassWriter(loader,reference);reference.accept(writer);return writer.toByteArray();
        }
        if(loader!=null||name==null||!name.equals("java/lang/invoke/MethodHandles$Lookup")&&!name.equals("java/lang/invoke/MethodHandles")&&!name.equals("java/lang/ClassLoader"))return null;
        ClassNode node=new ClassNode();new ClassReader(bytes).accept(node,0);boolean changed=false;
        for(MethodNode method:node.methods) {
            boolean derived=name.equals("java/lang/invoke/MethodHandles$Lookup")
                    &&(method.name.equals("in")&&method.desc.equals("(Ljava/lang/Class;)Ljava/lang/invoke/MethodHandles$Lookup;")
                    ||method.name.equals("dropLookupMode")&&method.desc.equals("(I)Ljava/lang/invoke/MethodHandles$Lookup;"));
            boolean privateLookup=name.equals("java/lang/invoke/MethodHandles")&&method.name.equals("privateLookupIn")
                    &&method.desc.equals("(Ljava/lang/Class;Ljava/lang/invoke/MethodHandles$Lookup;)Ljava/lang/invoke/MethodHandles$Lookup;");
            if(derived||privateLookup) {
                for(AbstractInsnNode at:method.instructions.toArray())if(at.getOpcode()==Opcodes.ARETURN) {
                    InsnList observe=new InsnList();observe.add(new VarInsnNode(Opcodes.ALOAD,privateLookup?1:0));
                    observe.add(new MethodInsnNode(Opcodes.INVOKESTATIC,BRIDGE,"lookupDerived",
                            "(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/invoke/MethodHandles$Lookup;)Ljava/lang/invoke/MethodHandles$Lookup;",false));
                    method.instructions.insertBefore(at,observe);
                }
                changed=true;continue;
            }
            boolean lookup=name.endsWith("$Lookup")&&method.name.startsWith("define")
                    &&(method.name.equals("defineClass")||method.name.equals("defineHiddenClass")||method.name.equals("defineHiddenClassWithClassData"))
                    &&method.desc.startsWith("([B");
            boolean loaderBytes=name.equals("java/lang/ClassLoader")&&method.name.equals("defineClass")
                    &&method.desc.equals("(Ljava/lang/String;[BIILjava/security/ProtectionDomain;)Ljava/lang/Class;");
            boolean loaderBuffer=name.equals("java/lang/ClassLoader")&&method.name.equals("defineClass")
                    &&method.desc.equals("(Ljava/lang/String;Ljava/nio/ByteBuffer;Ljava/security/ProtectionDomain;)Ljava/lang/Class;");
            if(!lookup&&!loaderBytes&&!loaderBuffer)continue;
            int token=method.maxLocals++,result=method.maxLocals++,error=method.maxLocals++;
            LabelNode start=new LabelNode(),end=new LabelNode(),handler=new LabelNode();InsnList entry=new InsnList();
            entry.add(new VarInsnNode(Opcodes.ALOAD,0));
            entry.add(new VarInsnNode(Opcodes.ALOAD,lookup?1:2));
            if(loaderBuffer)entry.add(new MethodInsnNode(Opcodes.INVOKESTATIC,BRIDGE,"bufferImage","(Ljava/nio/ByteBuffer;)[B",false));
            if(loaderBytes) {entry.add(new VarInsnNode(Opcodes.ILOAD,3));entry.add(new VarInsnNode(Opcodes.ILOAD,4));}
            else {entry.add(new InsnNode(Opcodes.ICONST_0));entry.add(new InsnNode(Opcodes.DUP2));entry.add(new InsnNode(Opcodes.POP));entry.add(new InsnNode(Opcodes.ARRAYLENGTH));}
            // Keep the actual Lookup identity; privateLookupIn's producer is distinct
            // from the physical module of its target class.
            entry.add(new MethodInsnNode(Opcodes.INVOKESTATIC,BRIDGE,"before","(Ljava/lang/Object;[BII)Ljava/lang/Object;",false));
            entry.add(new VarInsnNode(Opcodes.ASTORE,token));entry.add(start);
            entry.add(new VarInsnNode(Opcodes.ALOAD,token));entry.add(new MethodInsnNode(Opcodes.INVOKESTATIC,BRIDGE,"image","(Ljava/lang/Object;)[B",false));
            if(loaderBuffer)entry.add(new MethodInsnNode(Opcodes.INVOKESTATIC,"java/nio/ByteBuffer","wrap","([B)Ljava/nio/ByteBuffer;",false));
            entry.add(new VarInsnNode(Opcodes.ASTORE,lookup?1:2));
            if(loaderBytes) {entry.add(new InsnNode(Opcodes.ICONST_0));entry.add(new VarInsnNode(Opcodes.ISTORE,3));entry.add(new VarInsnNode(Opcodes.ALOAD,2));entry.add(new InsnNode(Opcodes.ARRAYLENGTH));entry.add(new VarInsnNode(Opcodes.ISTORE,4));}
            for(AbstractInsnNode instruction:method.instructions.toArray())if(instruction.getOpcode()==Opcodes.ARETURN) {
                InsnList leave=new InsnList();leave.add(new VarInsnNode(Opcodes.ASTORE,result));leave.add(new VarInsnNode(Opcodes.ALOAD,token));leave.add(new VarInsnNode(Opcodes.ALOAD,result));
                leave.add(new MethodInsnNode(Opcodes.INVOKESTATIC,BRIDGE,"defined","(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;",false));
                leave.add(new TypeInsnNode(Opcodes.CHECKCAST,Type.getReturnType(method.desc).getInternalName()));
                leave.add(new VarInsnNode(Opcodes.ALOAD,token));leave.add(new MethodInsnNode(Opcodes.INVOKESTATIC,BRIDGE,"after","(Ljava/lang/Object;)V",false));
                method.instructions.insertBefore(instruction,leave);
            }
            method.instructions.insert(entry);method.instructions.add(end);method.instructions.add(handler);
            method.instructions.add(new VarInsnNode(Opcodes.ASTORE,error));method.instructions.add(new VarInsnNode(Opcodes.ALOAD,token));
            method.instructions.add(new MethodInsnNode(Opcodes.INVOKESTATIC,BRIDGE,"after","(Ljava/lang/Object;)V",false));
            method.instructions.add(new VarInsnNode(Opcodes.ALOAD,error));method.instructions.add(new InsnNode(Opcodes.ATHROW));
            method.tryCatchBlocks.add(new TryCatchBlockNode(start,end,handler,"java/lang/Throwable"));changed=true;
        }
        if(!changed)return null;ControlClassWriter writer=new ControlClassWriter(loader,node);node.accept(writer);return writer.toByteArray();
    }
    static byte[] generated(ClassLoader loader,byte[] bytes) {
        return generated(loader,bytes,false);
    }
    static byte[] generated(ClassLoader loader,byte[] bytes,boolean recorded) {
        ClassNode node=new ClassNode();new ClassReader(bytes).accept(node,0);
        CreationBoundary.Weaving creations=recorded?CreationBoundary.recorded(bytes,node):null;
        for(MethodNode method:node.methods)for(AbstractInsnNode i:method.instructions.toArray())
            if(!recorded&&i instanceof MethodInsnNode call&&call.owner.equals(BRIDGE))throw new IllegalArgumentException("UNTRUSTED_DEFINITION_BRIDGE_CALL");
        for(MethodNode method:node.methods) {
            if((method.access&(Opcodes.ACC_ABSTRACT|Opcodes.ACC_NATIVE))!=0)continue;
            if(recorded&&guarded(node.name,method))continue;
            InsnList entry=guard(node.name,method);
            if(method.name.equals("<clinit>")) {
                InsnList bind=new InsnList();bind.add(new LdcInsnNode(Type.getObjectType(node.name)));
                bind.add(new MethodInsnNode(Opcodes.INVOKESTATIC,BRIDGE,"initializing","(Ljava/lang/Class;)V",false));bind.add(entry);entry=bind;
            }
            method.instructions.insert(entry);
        }
        ControlClassWriter writer=new ControlClassWriter(loader,node);node.accept(writer);byte[] result=writer.toByteArray();CreationBoundary.transferred(result,creations);return result;
    }
    private static boolean guarded(String owner,MethodNode method){
        AbstractInsnNode first=method.instructions.getFirst();while(first!=null&&first.getOpcode()<0)first=first.getNext();
        if(method.name.equals("<clinit>")&&first instanceof LdcInsnNode literal&&Type.getObjectType(owner).equals(literal.cst)
                &&first.getNext() instanceof MethodInsnNode bind&&bind.owner.equals(BRIDGE)&&bind.name.equals("initializing"))first=bind.getNext();
        return first instanceof LdcInsnNode literal&&Type.getObjectType(owner).equals(literal.cst)
                &&first.getNext() instanceof MethodInsnNode call&&call.owner.equals(BRIDGE)&&call.name.equals("allowed")&&call.desc.equals("(Ljava/lang/Class;)Z")
                &&call.getNext() instanceof JumpInsnNode jump&&jump.getOpcode()==Opcodes.IFNE;
    }
    static InsnList guard(String owner,MethodNode method) {
        LabelNode allowed=new LabelNode();InsnList code=new InsnList();code.add(new LdcInsnNode(Type.getObjectType(owner)));
        code.add(new MethodInsnNode(Opcodes.INVOKESTATIC,BRIDGE,"allowed","(Ljava/lang/Class;)Z",false));code.add(new JumpInsnNode(Opcodes.IFNE,allowed));
        if(method.name.equals("<init>")) {
            code.add(new TypeInsnNode(Opcodes.NEW,"java/lang/IllegalStateException"));code.add(new InsnNode(Opcodes.DUP));code.add(new LdcInsnNode("RONOVA_DERIVED_GROUP_CREATION_REFUSED"));
            code.add(new MethodInsnNode(Opcodes.INVOKESPECIAL,"java/lang/IllegalStateException","<init>","(Ljava/lang/String;)V",false));code.add(new InsnNode(Opcodes.ATHROW));
        }else code.add(stoppedReturn(owner,Type.getReturnType(method.desc)));
        code.add(allowed);return code;
    }
    static InsnList stoppedReturn(String owner,Type result){
        InsnList code=new InsnList();
        if(result.getSort()==Type.VOID){code.add(new InsnNode(Opcodes.RETURN));return code;}
        code.add(new LdcInsnNode(Type.getObjectType(owner)));String wrapper=wrapper(result);
        if(wrapper==null)code.add(new LdcInsnNode(result));else code.add(new FieldInsnNode(Opcodes.GETSTATIC,wrapper,"TYPE","Ljava/lang/Class;"));
        code.add(new MethodInsnNode(Opcodes.INVOKESTATIC,BRIDGE,"stoppedReturn","(Ljava/lang/Class;Ljava/lang/Class;)Ljava/lang/Object;",false));
        if(wrapper==null)code.add(new TypeInsnNode(Opcodes.CHECKCAST,result.getInternalName()));
        else {code.add(new TypeInsnNode(Opcodes.CHECKCAST,wrapper));code.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL,wrapper,result.getClassName()+"Value","()"+result.getDescriptor(),false));}
        code.add(new InsnNode(result.getOpcode(Opcodes.IRETURN)));return code;
    }
    private static String wrapper(Type type) {return switch(type.getSort()) {
        case Type.BOOLEAN->"java/lang/Boolean";case Type.BYTE->"java/lang/Byte";case Type.CHAR->"java/lang/Character";
        case Type.SHORT->"java/lang/Short";case Type.INT->"java/lang/Integer";case Type.LONG->"java/lang/Long";
        case Type.FLOAT->"java/lang/Float";case Type.DOUBLE->"java/lang/Double";default->null;};}
}
