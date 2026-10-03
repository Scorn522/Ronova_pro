package dev.ronova.pro.agent;

import java.util.*;
import jdk.internal.org.objectweb.asm.*;
import jdk.internal.org.objectweb.asm.tree.*;

/** JDK 17 handle factories and the Java VarHandle paths immediately before their Unsafe writes. */
final class HandleWriteBoundary {
    private static final String BRIDGE="dev/ronova/pro/bootstrap/BackingBridge";
    private static final List<String> TARGETS=createTargets();
    static List<String> targets() {return TARGETS;}
    private static final Set<String> INSTALLED=java.util.concurrent.ConcurrentHashMap.newKeySet();
    static boolean complete() {return INSTALLED.containsAll(TARGETS);}
    static int installedCount() {return INSTALLED.size();}
    private static List<String> createTargets() {
        List<String> names=new ArrayList<>(List.of("java/lang/invoke/MethodHandles","java/lang/invoke/MethodHandles$Lookup","java/lang/reflect/Method"));
        for(String type:List.of("Booleans","Bytes","Shorts","Chars","Ints","Longs","Floats","Doubles","References"))
            for(String kind:List.of("Array","FieldInstanceReadWrite","FieldStaticReadWrite"))names.add("java/lang/invoke/VarHandle"+type+"$"+kind);
        return names;
    }
    static byte[] transform(ClassLoader loader,String name,byte[] bytes) {
        if(name==null||!targets().contains(name))return null;
        ClassNode node=new ClassNode();new ClassReader(bytes).accept(node,0);boolean changed=false;
        for(MethodNode method:node.methods) {
            if(name.equals("java/lang/reflect/Method")&&method.name.equals("invoke")&&method.desc.equals("(Ljava/lang/Object;[Ljava/lang/Object;)Ljava/lang/Object;")) {
                InsnList code=new InsnList();LabelNode ordinary=new LabelNode();
                code.add(new VarInsnNode(Opcodes.ALOAD,0));code.add(new VarInsnNode(Opcodes.ALOAD,1));code.add(new VarInsnNode(Opcodes.ALOAD,2));
                code.add(new MethodInsnNode(Opcodes.INVOKESTATIC,BRIDGE,"reflectArray","(Ljava/lang/reflect/Method;Ljava/lang/Object;[Ljava/lang/Object;)Ljava/lang/Object;",false));
                code.add(new InsnNode(Opcodes.DUP));code.add(new FieldInsnNode(Opcodes.GETSTATIC,BRIDGE,"UNHANDLED","Ljava/lang/Object;"));
                code.add(new JumpInsnNode(Opcodes.IF_ACMPEQ,ordinary));code.add(new InsnNode(Opcodes.ARETURN));code.add(ordinary);code.add(new InsnNode(Opcodes.POP));
                method.instructions.insert(code);changed=true;continue;
            }
            if(name.equals("java/lang/invoke/MethodHandles$Lookup")&&Set.of("findStatic","unreflect").contains(method.name)) {
                for(AbstractInsnNode at:method.instructions.toArray())if(at.getOpcode()==Opcodes.ARETURN) {
                    InsnList code=new InsnList();code.add(new VarInsnNode(Opcodes.ALOAD,1));
                    if(method.name.equals("unreflect"))code.add(new MethodInsnNode(Opcodes.INVOKESTATIC,BRIDGE,"wrapArrayMethod","(Ljava/lang/invoke/MethodHandle;Ljava/lang/reflect/Method;)Ljava/lang/invoke/MethodHandle;",false));
                    else {code.add(new VarInsnNode(Opcodes.ALOAD,2));code.add(new MethodInsnNode(Opcodes.INVOKESTATIC,BRIDGE,"wrapArrayMethod","(Ljava/lang/invoke/MethodHandle;Ljava/lang/Class;Ljava/lang/String;)Ljava/lang/invoke/MethodHandle;",false));}
                    method.instructions.insertBefore(at,code);
                }changed=true;continue;
            }
            if(name.equals("java/lang/invoke/MethodHandles")&&method.name.equals("arrayElementSetter")) {
                for(AbstractInsnNode at:method.instructions.toArray())if(at.getOpcode()==Opcodes.ARETURN)method.instructions.insertBefore(at,
                    new MethodInsnNode(Opcodes.INVOKESTATIC,BRIDGE,"wrapArraySetter","(Ljava/lang/invoke/MethodHandle;)Ljava/lang/invoke/MethodHandle;",false));changed=true;continue;
            }
            if(name.equals("java/lang/invoke/MethodHandles$Lookup")&&Set.of("findSetter","findStaticSetter","unreflectSetter").contains(method.name)) {
                for(AbstractInsnNode at:method.instructions.toArray())if(at.getOpcode()==Opcodes.ARETURN) {
                    InsnList code=new InsnList();code.add(new VarInsnNode(Opcodes.ALOAD,1));
                    if(method.name.equals("unreflectSetter"))code.add(new MethodInsnNode(Opcodes.INVOKESTATIC,BRIDGE,"wrapSetter","(Ljava/lang/invoke/MethodHandle;Ljava/lang/reflect/Field;)Ljava/lang/invoke/MethodHandle;",false));
                    else {code.add(new VarInsnNode(Opcodes.ALOAD,2));code.add(new MethodInsnNode(Opcodes.INVOKESTATIC,BRIDGE,"wrapSetter","(Ljava/lang/invoke/MethodHandle;Ljava/lang/Class;Ljava/lang/String;)Ljava/lang/invoke/MethodHandle;",false));}
                    method.instructions.insertBefore(at,code);
                }changed=true;continue;
            }
            if(!name.startsWith("java/lang/invoke/VarHandle"))continue;
            // Plain array set uses xASTORE, unlike its volatile/CAS siblings.
            if(name.endsWith("$Array")&&method.name.equals("set"))for(AbstractInsnNode at:method.instructions.toArray()) {
                String store=switch(at.getOpcode()) {case Opcodes.AASTORE->"arrayStore";case Opcodes.IASTORE->"intStore";case Opcodes.LASTORE->"longStore";case Opcodes.FASTORE->"floatStore";case Opcodes.DASTORE->"doubleStore";case Opcodes.BASTORE->"byteStore";case Opcodes.CASTORE->"charStore";case Opcodes.SASTORE->"shortStore";default->null;};
                if(store==null)continue;
                String value=switch(at.getOpcode()){case Opcodes.AASTORE->"Ljava/lang/Object;";case Opcodes.LASTORE->"J";case Opcodes.FASTORE->"F";case Opcodes.DASTORE->"D";default->"I";};
                method.instructions.set(at,new MethodInsnNode(Opcodes.INVOKESTATIC,BRIDGE,store,"(Ljava/lang/Object;I"+value+")V",false));changed=true;
            }
            for(AbstractInsnNode at:method.instructions.toArray())if(at instanceof MethodInsnNode call&&call.owner.equals("jdk/internal/misc/Unsafe")
                    &&(call.name.startsWith("put")||call.name.startsWith("compareAnd")||call.name.startsWith("weakCompareAnd")||call.name.startsWith("getAnd"))) {
                Type[] arguments=Type.getArgumentTypes(call.desc);if(arguments.length<3||arguments[0].getSort()!=Type.OBJECT||arguments[1].getSort()!=Type.LONG)continue;
                int[] slots=new int[arguments.length];for(int i=0;i<slots.length;i++){slots[i]=method.maxLocals;method.maxLocals+=arguments[i].getSize();}
                int access=method.maxLocals++;InsnList code=new InsnList();LabelNode allow=new LabelNode();
                for(int i=slots.length-1;i>=0;i--)code.add(new VarInsnNode(arguments[i].getOpcode(Opcodes.ISTORE),slots[i]));
                code.add(new VarInsnNode(Opcodes.ASTORE,access));LabelNode guardStart=new LabelNode();code.add(guardStart);
                code.add(new MethodInsnNode(Opcodes.INVOKESTATIC,"dev/ronova/pro/bootstrap/TaskBridge","unsafeControlScope","()Z",false));code.add(new JumpInsnNode(Opcodes.IFNE,allow));
                code.add(new VarInsnNode(Opcodes.ALOAD,slots[0]));code.add(new VarInsnNode(Opcodes.LLOAD,slots[1]));
                Type proposed=arguments[arguments.length-1];code.add(new VarInsnNode(proposed.getOpcode(Opcodes.ILOAD),slots[slots.length-1]));
                if(name.contains("VarHandleFloats$")&&proposed.getSort()==Type.INT) {code.add(new MethodInsnNode(Opcodes.INVOKESTATIC,"java/lang/Float","intBitsToFloat","(I)F",false));proposed=Type.FLOAT_TYPE;}
                if(name.contains("VarHandleDoubles$")&&proposed.getSort()==Type.LONG) {code.add(new MethodInsnNode(Opcodes.INVOKESTATIC,"java/lang/Double","longBitsToDouble","(J)D",false));proposed=Type.DOUBLE_TYPE;}
                ControlClassWriter.box(code,proposed);
                code.add(new LdcInsnNode(call.name));code.add(new LdcInsnNode(kind(proposed)));code.add(new MethodInsnNode(Opcodes.INVOKESTATIC,BRIDGE,"nativeWriteAllowed","(Ljava/lang/Object;JLjava/lang/Object;Ljava/lang/String;Ljava/lang/String;)Z",false));
                code.add(new JumpInsnNode(Opcodes.IFNE,allow));Type returned=Type.getReturnType(method.desc);
                if(returned.getSort()==Type.VOID)code.add(new InsnNode(Opcodes.RETURN));
                else if(call.name.contains("CompareAndSet")||call.name.equals("compareAndSetReference")||call.name.startsWith("compareAndSet")) {code.add(new InsnNode(Opcodes.ICONST_0));code.add(new InsnNode(Opcodes.IRETURN));}
                else {
                    code.add(new VarInsnNode(Opcodes.ALOAD,slots[0]));code.add(new VarInsnNode(Opcodes.LLOAD,slots[1]));code.add(new LdcInsnNode(kind(returned)));
                    code.add(new MethodInsnNode(Opcodes.INVOKESTATIC,BRIDGE,"retained","(Ljava/lang/Object;JLjava/lang/String;)Ljava/lang/Object;",false));unbox(code,returned);code.add(new InsnNode(returned.getOpcode(Opcodes.IRETURN)));
                }
                code.add(allow);code.add(new VarInsnNode(Opcodes.ALOAD,access));for(int i=0;i<slots.length;i++)code.add(new VarInsnNode(arguments[i].getOpcode(Opcodes.ILOAD),slots[i]));
                method.instructions.insertBefore(at,code);MemoryWriteScope.nativeCall(method,call,arguments,slots,kind(proposed),guardStart);changed=true;
            }
        }
        if(!changed)return null;ControlClassWriter writer=new ControlClassWriter(loader,node);node.accept(writer);INSTALLED.add(name);return writer.toByteArray();
    }
    private static String kind(Type type) {
        return switch(type.getSort()){case Type.BOOLEAN->"Boolean";case Type.BYTE->"Byte";case Type.SHORT->"Short";case Type.CHAR->"Char";case Type.INT->"Int";case Type.LONG->"Long";case Type.FLOAT->"Float";case Type.DOUBLE->"Double";default->"Object";};
    }
    private static void unbox(InsnList code,Type type) {
        if(type.getSort()==Type.OBJECT||type.getSort()==Type.ARRAY) {code.add(new TypeInsnNode(Opcodes.CHECKCAST,type.getInternalName()));return;}
        String owner="java/lang/"+(kind(type).equals("Int")?"Integer":kind(type).equals("Char")?"Character":kind(type));
        code.add(new TypeInsnNode(Opcodes.CHECKCAST,owner));code.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL,owner,type.getClassName()+"Value","()"+type.getDescriptor(),false));
    }
}
