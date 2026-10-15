package dev.ronova.pro.agent;

import java.lang.instrument.Instrumentation;
import java.util.*;
import jdk.internal.org.objectweb.asm.*;
import jdk.internal.org.objectweb.asm.tree.*;

/** Actual virtual receivers and argument tuples, including inherited contracts and real overrides. */
final class EquipmentActionBoundary {
    private record Entry(Class<?> owner,String method,String descriptor,String operation,boolean virtual) { }
    private static volatile Entry[] entries=new Entry[0];
    private static final class Carrier {
        final java.lang.ref.WeakReference<Class<?>> type;
        volatile boolean transformed;
        Carrier(Class<?> actual){type=new java.lang.ref.WeakReference<>(actual);}
    }
    private static volatile Carrier[] carriers=new Carrier[0];
    private static final String BRIDGE="dev/ronova/pro/bootstrap/ControlBridge";
    private static Carrier carrier(Class<?> actual) {
        if(actual!=null)for(Carrier entry:carriers)if(entry.type.get()==actual)return entry;
        return null;
    }
    static void transformFailed(Class<?> actual){Carrier entry=carrier(actual);if(entry!=null)entry.transformed=false;EquipmentSlotBoundary.transformFailed(actual);}
    private static synchronized Carrier declareCarrier(Class<?> actual) {
        Carrier present=carrier(actual);if(present!=null)return present;
        var living=new ArrayList<Carrier>();for(Carrier entry:carriers)if(entry.type.get()!=null)living.add(entry);
        Carrier added=new Carrier(actual);living.add(added);Carrier[] next=living.toArray(Carrier[]::new);
        ControlImages.protect(next,added,added.type);carriers=next;return added;
    }
    /** One actual data class at a time; no virtual class-name or module-wide grant. */
    static void installCarrier(Instrumentation api,Class<?> actual) {
        Objects.requireNonNull(actual);
        if(api==null||actual.isArray()||actual.isPrimitive()||actual.getClassLoader()==null||!api.isModifiableClass(actual))
            throw new IllegalStateException("EQUIPMENT_CARRIER_INSTALL_UNAVAILABLE:"+actual.getName());
        Carrier entry=declareCarrier(actual);
        synchronized(entry) {
            if(entry.transformed)return;
            try{api.retransformClasses(actual);}
            catch(java.lang.instrument.UnmodifiableClassException failure){entry.transformed=false;throw new IllegalStateException("EQUIPMENT_CARRIER_INSTALL_FAILED:"+actual.getName(),failure);}
            catch(RuntimeException|Error failure){entry.transformed=false;throw failure;}
            if(!entry.transformed)throw new IllegalStateException("EQUIPMENT_CARRIER_TRANSFORM_MISSING:"+actual.getName());
        }
    }
    static synchronized boolean declare(Class<?> owner,String method,String descriptor,String operation) {
        Objects.requireNonNull(owner);Objects.requireNonNull(method);Objects.requireNonNull(descriptor);Objects.requireNonNull(operation);
        java.lang.reflect.Method definition=null;
        for(var candidate:owner.getDeclaredMethods())if(candidate.getName().equals(method)&&java.lang.invoke.MethodType.methodType(candidate.getReturnType(),candidate.getParameterTypes()).descriptorString().equals(descriptor)){definition=candidate;break;}
        if(definition==null)throw new IllegalArgumentException("EQUIPMENT_ACTION_DECLARATION_MISSING:"+owner.getName()+"#"+method+descriptor);
        int modifiers=definition.getModifiers();
        Entry next=new Entry(owner,method,descriptor,operation,!java.lang.reflect.Modifier.isPrivate(modifiers)&&!java.lang.reflect.Modifier.isFinal(modifiers));
        for(Entry prior:entries)if(prior.equals(next))return false;
        Entry[] changed=Arrays.copyOf(entries,entries.length+1);changed[changed.length-1]=next;
        ControlImages.protect(changed,next);entries=changed;return true;
    }
    private static boolean matches(Entry entry,ClassLoader loader,String name,Class<?> actual,ClassNode node) {
        if(entry.owner.getName().replace('.','/').equals(name))return entry.owner.getClassLoader()==loader;
        if(!entry.virtual)return false;
        if(actual!=null)return entry.owner.isAssignableFrom(actual);
        try {
            if(node.superName!=null&&entry.owner.isAssignableFrom(Class.forName(node.superName.replace('/','.'),false,loader)))return true;
            for(String contract:node.interfaces)if(entry.owner.isAssignableFrom(Class.forName(contract.replace('/','.'),false,loader)))return true;
        }catch(ClassNotFoundException|LinkageError unavailable){ }
        return false;
    }
    static byte[] transform(ClassLoader loader,String name,Class<?> actual,byte[] bytes) {
        Carrier data=carrier(actual);
        if(name==null||entries.length==0&&data==null||loader==null)return null;
        ClassNode node=new ClassNode();new ClassReader(bytes).accept(node,0);boolean changed=false;
        for(MethodNode method:node.methods) {
            if(method.name.startsWith("<")||(method.access&(Opcodes.ACC_STATIC|Opcodes.ACC_ABSTRACT|Opcodes.ACC_NATIVE))!=0)continue;
            Entry selected=null;
            for(Entry entry:entries)if(entry.method.equals(method.name)&&entry.descriptor.equals(method.desc)
                    &&((method.access&Opcodes.ACC_PRIVATE)==0||entry.owner.getName().replace('.','/').equals(name)&&entry.owner.getClassLoader()==loader)
                    &&matches(entry,loader,name,actual,node)){selected=entry;break;}
            if(selected==null&&data==null)continue;
            weave(method,selected==null?"carrier-call":selected.operation);changed=true;
        }
        if(!changed){if(data!=null)data.transformed=true;return null;}
        ControlClassWriter writer=new ControlClassWriter(loader,node);node.accept(writer);byte[] result=writer.toByteArray();
        if(data!=null)data.transformed=true;return result;
    }
    private static void weave(MethodNode method,String operation) {
        int arguments=method.maxLocals++,lease=method.maxLocals++,failure=method.maxLocals++;
        Type[] types=Type.getArgumentTypes(method.desc);Type result=Type.getReturnType(method.desc);
        InsnList entry=new InsnList();entry.add(new LdcInsnNode(types.length));entry.add(new TypeInsnNode(Opcodes.ANEWARRAY,"java/lang/Object"));
        int local=1;
        for(int i=0;i<types.length;i++) {
            entry.add(new InsnNode(Opcodes.DUP));entry.add(new LdcInsnNode(i));entry.add(new VarInsnNode(types[i].getOpcode(Opcodes.ILOAD),local));
            ControlClassWriter.box(entry,types[i]);entry.add(new InsnNode(Opcodes.AASTORE));local+=types[i].getSize();
        }
        entry.add(new VarInsnNode(Opcodes.ASTORE,arguments));entry.add(new VarInsnNode(Opcodes.ALOAD,0));entry.add(new VarInsnNode(Opcodes.ALOAD,arguments));entry.add(new LdcInsnNode(operation));
        entry.add(new MethodInsnNode(Opcodes.INVOKESTATIC,BRIDGE,"beginEquipmentInvocation","(Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/String;)Ljava/lang/Object;",false));
        entry.add(new VarInsnNode(Opcodes.ASTORE,lease));
        LabelNode start=new LabelNode(),body=new LabelNode(),end=new LabelNode(),handler=new LabelNode();entry.add(start);
        entry.add(new VarInsnNode(Opcodes.ALOAD,lease));entry.add(new MethodInsnNode(Opcodes.INVOKESTATIC,BRIDGE,"equipmentInvocationDenied","(Ljava/lang/Object;)Z",false));
        entry.add(new JumpInsnNode(Opcodes.IFEQ,body));
        if(operation.equals("carrier-call")) {
            entry.add(new TypeInsnNode(Opcodes.NEW,"java/lang/SecurityException"));entry.add(new InsnNode(Opcodes.DUP));
            entry.add(new LdcInsnNode("EQUIPMENT_CARRIER_INVOCATION_REFUSED"));
            entry.add(new MethodInsnNode(Opcodes.INVOKESPECIAL,"java/lang/SecurityException","<init>","(Ljava/lang/String;)V",false));entry.add(new InsnNode(Opcodes.ATHROW));
        }else if(result.getSort()==Type.OBJECT||result.getSort()==Type.ARRAY) {
            entry.add(new VarInsnNode(Opcodes.ALOAD,0));entry.add(new LdcInsnNode(operation));entry.add(new VarInsnNode(Opcodes.ALOAD,arguments));
            entry.add(new MethodInsnNode(Opcodes.INVOKESTATIC,BRIDGE,"equipmentInvocationResult","(Ljava/lang/Object;Ljava/lang/String;Ljava/lang/Object;)Ljava/lang/Object;",false));
            entry.add(new TypeInsnNode(Opcodes.CHECKCAST,result.getInternalName()));entry.add(new InsnNode(Opcodes.ARETURN));
        }else if(result.getSort()==Type.VOID)entry.add(new InsnNode(Opcodes.RETURN));
        else {entry.add(new InsnNode(result.getSort()==Type.LONG?Opcodes.LCONST_0:result.getSort()==Type.FLOAT?Opcodes.FCONST_0:result.getSort()==Type.DOUBLE?Opcodes.DCONST_0:Opcodes.ICONST_0));entry.add(new InsnNode(result.getOpcode(Opcodes.IRETURN)));}
        entry.add(body);method.instructions.insert(entry);
        // Keep every original result on the operand stack while closing its exact invocation.
        for(AbstractInsnNode instruction:method.instructions.toArray())if(instruction.getOpcode()>=Opcodes.IRETURN&&instruction.getOpcode()<=Opcodes.RETURN) {
            InsnList close=new InsnList();close.add(new VarInsnNode(Opcodes.ALOAD,lease));close.add(new MethodInsnNode(Opcodes.INVOKESTATIC,BRIDGE,"endEquipmentInvocation","(Ljava/lang/Object;)V",false));
            method.instructions.insertBefore(instruction,close);
        }
        method.instructions.add(end);method.instructions.add(handler);method.instructions.add(new VarInsnNode(Opcodes.ASTORE,failure));
        method.instructions.add(new VarInsnNode(Opcodes.ALOAD,lease));method.instructions.add(new VarInsnNode(Opcodes.ALOAD,failure));
        method.instructions.add(new MethodInsnNode(Opcodes.INVOKESTATIC,BRIDGE,"failedEquipmentInvocation","(Ljava/lang/Object;Ljava/lang/Throwable;)V",false));
        method.instructions.add(new VarInsnNode(Opcodes.ALOAD,failure));method.instructions.add(new InsnNode(Opcodes.ATHROW));
        method.tryCatchBlocks.add(new TryCatchBlockNode(start,end,handler,"java/lang/Throwable"));
    }
    static void retransformDeclared(Instrumentation api) {
        for(Class<?> type:RecoveryAgent.loadedClasses()) {
            if(type.isArray()||type.isPrimitive()||!api.isModifiableClass(type))continue;
            boolean wanted=false;for(Entry entry:entries)if(entry.owner.isAssignableFrom(type)){wanted=true;break;}
            if(wanted)try{api.retransformClasses(type);}catch(java.lang.instrument.UnmodifiableClassException failure){throw new IllegalStateException("EQUIPMENT_ACTION_INSTALL_FAILED:"+type.getName(),failure);}
        }
    }
}
