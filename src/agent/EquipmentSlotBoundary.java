package dev.ronova.pro.agent;

import java.lang.instrument.Instrumentation;
import java.lang.ref.WeakReference;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import jdk.internal.org.objectweb.asm.*;
import jdk.internal.org.objectweb.asm.tree.*;

/** Read witnesses from the original body of one exactly declared virtual slot getter. */
final class EquipmentSlotBoundary {
    static final String DESCRIPTOR="(Lnet/minecraft/world/entity/EquipmentSlot;)Lnet/minecraft/world/item/ItemStack;";
    private static final String SLOT="net/minecraft/world/entity/EquipmentSlot",STACK="net/minecraft/world/item/ItemStack",BRIDGE="dev/ronova/pro/bootstrap/ControlBridge";
    private static final String TOKEN="Ljava/lang/Object;";
    private static final Set<String> MAPS=Set.of("java/util/Map","java/util/AbstractMap","java/util/HashMap","java/util/LinkedHashMap","java/util/IdentityHashMap","java/util/EnumMap",
            "java/util/ImmutableCollections$Map1","java/util/ImmutableCollections$MapN","java/util/Collections$SingletonMap","java/util/Collections$UnmodifiableMap","java/util/Collections$UnmodifiableSortedMap","java/util/Collections$UnmodifiableNavigableMap",
            "java/util/Collections$SynchronizedMap","java/util/Collections$SynchronizedSortedMap","java/util/Collections$SynchronizedNavigableMap",
            "com/google/common/collect/ImmutableMap","com/google/common/collect/ImmutableBiMap","com/google/common/collect/RegularImmutableMap","com/google/common/collect/SingletonImmutableBiMap","com/google/common/collect/JdkBackedImmutableMap");
    private static final Set<String> LISTS=Set.of("java/util/List","java/util/AbstractList","java/util/AbstractSequentialList","java/util/ArrayList","java/util/ArrayList$SubList","java/util/LinkedList","java/util/Arrays$ArrayList",
            "java/util/ImmutableCollections$AbstractImmutableList","java/util/ImmutableCollections$ListN","java/util/ImmutableCollections$List12","java/util/ImmutableCollections$SubList",
            "java/util/Collections$SingletonList","java/util/Collections$EmptyList","java/util/Collections$CopiesList","java/util/Collections$UnmodifiableList","java/util/Collections$UnmodifiableRandomAccessList","java/util/Collections$SynchronizedList","java/util/Collections$SynchronizedRandomAccessList",
            "com/google/common/collect/ImmutableList","com/google/common/collect/RegularImmutableList","com/google/common/collect/SingletonImmutableList","com/google/common/collect/ImmutableList$SubList","com/google/common/collect/ImmutableList$ReverseImmutableList","com/google/common/collect/RegularImmutableAsList", "net/minecraft/core/NonNullList");
    private static final class Getter {
        final WeakReference<Class<?>> owner;final String name,descriptor;volatile boolean transformed;
        Getter(Class<?> actual,String name,String descriptor){owner=new WeakReference<>(actual);this.name=name;this.descriptor=descriptor;}
    }
    private static volatile Getter[] getters=new Getter[0];
    private EquipmentSlotBoundary(){ }

    private static synchronized Getter declare(Class<?> actual,String name,String descriptor){
        List<Getter> living=new ArrayList<>();Getter found=null;
        for(Getter getter:getters)if(getter.owner.get()!=null){living.add(getter);if(getter.owner.get()==actual&&getter.name.equals(name)&&getter.descriptor.equals(descriptor))found=getter;}
        if(found!=null)return found;
        Getter added=new Getter(actual,name,descriptor);living.add(added);Getter[] next=living.toArray(Getter[]::new);
        ControlImages.protect(next,added,added.owner);getters=next;return added;
    }
    /** No loaded-class scan or class-loader-wide registration is involved. */
    static void install(Instrumentation api,Class<?> actual,String name,String descriptor){
        Objects.requireNonNull(actual);Objects.requireNonNull(name);Objects.requireNonNull(descriptor);
        if(!DESCRIPTOR.equals(descriptor)||!name.equals("getItemBySlot")&&!name.equals("m_6844_"))throw new IllegalArgumentException("EQUIPMENT_SLOT_GETTER_SIGNATURE_REQUIRED");
        if(api==null||actual.isArray()||actual.isPrimitive()||!api.isModifiableClass(actual))throw new IllegalStateException("EQUIPMENT_SLOT_INSTALL_UNAVAILABLE:"+actual.getName());
        java.lang.reflect.Method declared=null;
        for(var method:actual.getDeclaredMethods())if(method.getName().equals(name)&&Type.getMethodDescriptor(method).equals(descriptor)){declared=method;break;}
        if(declared==null)throw new IllegalArgumentException("EQUIPMENT_SLOT_DECLARATION_MISSING:"+actual.getName()+"#"+name);
        int access=declared.getModifiers();
        if(Modifier.isNative(access))throw new IllegalStateException("EQUIPMENT_SLOT_NATIVE_GETTER_UNSUPPORTED:"+actual.getName());
        if(Modifier.isAbstract(access))throw new IllegalStateException("EQUIPMENT_SLOT_ABSTRACT_GETTER_UNSUPPORTED:"+actual.getName());
        if(Modifier.isStatic(access)||Modifier.isPrivate(access))throw new IllegalStateException("EQUIPMENT_SLOT_VIRTUAL_GETTER_REQUIRED:"+actual.getName());
        Getter getter=declare(actual,name,descriptor);
        synchronized(getter){
            if(getter.transformed)return;
            try{api.retransformClasses(actual);}
            catch(java.lang.instrument.UnmodifiableClassException failure){getter.transformed=false;throw new IllegalStateException("EQUIPMENT_SLOT_INSTALL_FAILED:"+actual.getName(),failure);}
            catch(RuntimeException|Error failure){getter.transformed=false;throw failure;}
            if(!getter.transformed)throw new IllegalStateException("EQUIPMENT_SLOT_TRANSFORM_MISSING:"+actual.getName());
        }
    }
    static void transformFailed(Class<?> actual){if(actual!=null)for(Getter getter:getters)if(getter.owner.get()==actual)getter.transformed=false;}
    /** This pass must precede dispatch/action fallbacks, which are not natural returns. */
    static byte[] transform(ClassLoader loader,String name,Class<?> actual,byte[] bytes){
        if(actual==null||name==null||actual.getClassLoader()!=loader||!actual.getName().replace('.','/').equals(name))return null;
        List<Getter> selected=new ArrayList<>();for(Getter getter:getters)if(getter.owner.get()==actual){getter.transformed=false;selected.add(getter);}
        if(selected.isEmpty())return null;
        ClassNode node=new ClassNode();new ClassReader(bytes).accept(node,0);
        for(Getter getter:selected){
            MethodNode found=null;for(MethodNode method:node.methods)if(getter.name.equals(method.name)&&getter.descriptor.equals(method.desc)){if(found!=null)throw new IllegalStateException("EQUIPMENT_SLOT_AMBIGUOUS_BODY:"+name);found=method;}
            if(found==null)throw new IllegalStateException("EQUIPMENT_SLOT_BODY_MISSING:"+name);
            if((found.access&(Opcodes.ACC_STATIC|Opcodes.ACC_PRIVATE|Opcodes.ACC_NATIVE|Opcodes.ACC_ABSTRACT))!=0)throw new IllegalStateException("EQUIPMENT_SLOT_BODY_UNSUPPORTED:"+name);
            weave(found);
        }
        ControlClassWriter writer=new ControlClassWriter(loader,node);node.accept(writer);byte[] result=writer.toByteArray();
        for(Getter getter:selected)getter.transformed=true;return result;
    }
    private static void call(InsnList code,String name,String descriptor){code.add(new MethodInsnNode(Opcodes.INVOKESTATIC,BRIDGE,name,descriptor,false));}
    private static int local(MethodNode method,Type type){int at=method.maxLocals;method.maxLocals+=type.getSize();if(method.maxLocals>65535)throw new IllegalStateException("EQUIPMENT_SLOT_LOCAL_LIMIT");return at;}
    private static void load(InsnList code,Type type,int slot){code.add(new VarInsnNode(type.getOpcode(Opcodes.ILOAD),slot));}
    private static void store(InsnList code,Type type,int slot){code.add(new VarInsnNode(type.getOpcode(Opcodes.ISTORE),slot));}
    private static void unresolved(InsnList code,int token){code.add(new VarInsnNode(Opcodes.ALOAD,token));call(code,"equipmentSlotUnresolved","("+TOKEN+")V");}
    private static void weave(MethodNode method){
        AbstractInsnNode[] original=method.instructions.toArray();
        int receiver=local(method,Type.getType(Object.class)),slot=local(method,Type.getType(Object.class)),token=local(method,Type.getType(Object.class)),returned=local(method,Type.getType(Object.class)),failure=local(method,Type.getType(Throwable.class));
        LabelNode start=new LabelNode(),end=new LabelNode(),handler=new LabelNode();InsnList head=new InsnList();
        head.add(new VarInsnNode(Opcodes.ALOAD,0));head.add(new VarInsnNode(Opcodes.ASTORE,receiver));head.add(new VarInsnNode(Opcodes.ALOAD,1));head.add(new VarInsnNode(Opcodes.ASTORE,slot));
        head.add(new VarInsnNode(Opcodes.ALOAD,receiver));head.add(new VarInsnNode(Opcodes.ALOAD,slot));call(head,"beginEquipmentSlotRead","("+TOKEN+TOKEN+")"+TOKEN);
        head.add(new VarInsnNode(Opcodes.ASTORE,token));head.add(start);method.instructions.insert(head);
        // An original handler can swallow a failed read/call before its success
        // witness exists. Its fallback must not masquerade as a complete trace.
        Set<LabelNode> caught=java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
        for(TryCatchBlockNode block:method.tryCatchBlocks)if(caught.add(block.handler)){InsnList unknown=new InsnList();unresolved(unknown,token);method.instructions.insert(block.handler,unknown);}
        for(AbstractInsnNode instruction:original){
            if(instruction instanceof FieldInsnNode field&&(field.getOpcode()==Opcodes.GETFIELD||field.getOpcode()==Opcodes.GETSTATIC)){fieldRead(method,field,token);continue;}
            int opcode=instruction.getOpcode();
            if(opcode>=Opcodes.IALOAD&&opcode<=Opcodes.SALOAD){arrayRead(method,instruction,token);continue;}
            if(instruction instanceof MethodInsnNode invoke){
                boolean virtual=invoke.getOpcode()==Opcodes.INVOKEVIRTUAL||invoke.getOpcode()==Opcodes.INVOKEINTERFACE;
                if(virtual&&invoke.name.equals("get")&&invoke.desc.equals("(Ljava/lang/Object;)Ljava/lang/Object;")&&MAPS.contains(invoke.owner))containerRead(method,invoke,token,true);
                else if(virtual&&invoke.name.equals("get")&&invoke.desc.equals("(I)Ljava/lang/Object;")&&LISTS.contains(invoke.owner))containerRead(method,invoke,token,false);
                else if(virtual&&selector(invoke))selectorRead(method,invoke,token);
                else{InsnList unknown=new InsnList();unresolved(unknown,token);method.instructions.insertBefore(instruction,unknown);}
                continue;
            }
            if(instruction instanceof InvokeDynamicInsnNode){InsnList unknown=new InsnList();unresolved(unknown,token);method.instructions.insertBefore(instruction,unknown);continue;}
            if(opcode==Opcodes.IF_ACMPEQ||opcode==Opcodes.IF_ACMPNE||opcode==Opcodes.IFNULL||opcode==Opcodes.IFNONNULL){stackComparison(method,instruction,token);continue;}
            if(opcode==Opcodes.ARETURN){
                InsnList finish=new InsnList();finish.add(new VarInsnNode(Opcodes.ASTORE,returned));finish.add(new VarInsnNode(Opcodes.ALOAD,token));finish.add(new VarInsnNode(Opcodes.ALOAD,returned));
                call(finish,"observeEquipmentSlot","("+TOKEN+TOKEN+")V");finish.add(new VarInsnNode(Opcodes.ALOAD,returned));method.instructions.insertBefore(instruction,finish);
            }
        }
        method.instructions.add(end);method.instructions.add(handler);method.instructions.add(new VarInsnNode(Opcodes.ASTORE,failure));
        method.instructions.add(new VarInsnNode(Opcodes.ALOAD,token));method.instructions.add(new VarInsnNode(Opcodes.ALOAD,failure));
        method.instructions.add(new MethodInsnNode(Opcodes.INVOKESTATIC,BRIDGE,"failedEquipmentSlotRead","("+TOKEN+"Ljava/lang/Throwable;)V",false));
        method.instructions.add(new VarInsnNode(Opcodes.ALOAD,failure));method.instructions.add(new InsnNode(Opcodes.ATHROW));
        method.tryCatchBlocks.add(new TryCatchBlockNode(start,end,handler,"java/lang/Throwable"));
    }
    private static void fieldRead(MethodNode method,FieldInsnNode field,int token){
        boolean statik=field.getOpcode()==Opcodes.GETSTATIC;Type type=Type.getType(field.desc);int holder=statik?-1:local(method,Type.getType(Object.class)),value=local(method,type);
        if(!statik){InsnList before=new InsnList();before.add(new InsnNode(Opcodes.DUP));before.add(new VarInsnNode(Opcodes.ASTORE,holder));method.instructions.insertBefore(field,before);}
        InsnList after=new InsnList();store(after,type,value);after.add(new VarInsnNode(Opcodes.ALOAD,token));
        after.add(statik?new InsnNode(Opcodes.ACONST_NULL):new VarInsnNode(Opcodes.ALOAD,holder));after.add(new LdcInsnNode(Type.getObjectType(field.owner)));
        after.add(new LdcInsnNode(field.name));after.add(new LdcInsnNode(field.desc));load(after,type,value);ControlClassWriter.box(after,type);
        call(after,"equipmentSlotFieldRead","("+TOKEN+TOKEN+"Ljava/lang/Class;Ljava/lang/String;Ljava/lang/String;"+TOKEN+")V");load(after,type,value);method.instructions.insert(field,after);
    }
    private static Type arrayType(int opcode){return switch(opcode){case Opcodes.AALOAD->Type.getType(Object.class);case Opcodes.LALOAD->Type.LONG_TYPE;case Opcodes.FALOAD->Type.FLOAT_TYPE;case Opcodes.DALOAD->Type.DOUBLE_TYPE;default->Type.INT_TYPE;};}
    private static void arrayRead(MethodNode method,AbstractInsnNode instruction,int token){
        Type type=arrayType(instruction.getOpcode());int array=local(method,Type.getType(Object.class)),index=local(method,Type.INT_TYPE),value=local(method,type);
        InsnList before=new InsnList();before.add(new VarInsnNode(Opcodes.ISTORE,index));before.add(new VarInsnNode(Opcodes.ASTORE,array));before.add(new VarInsnNode(Opcodes.ALOAD,array));before.add(new VarInsnNode(Opcodes.ILOAD,index));method.instructions.insertBefore(instruction,before);
        InsnList after=new InsnList();store(after,type,value);after.add(new VarInsnNode(Opcodes.ALOAD,token));after.add(new VarInsnNode(Opcodes.ALOAD,array));after.add(new VarInsnNode(Opcodes.ILOAD,index));load(after,type,value);ControlClassWriter.box(after,type);
        call(after,"equipmentSlotArrayRead","("+TOKEN+TOKEN+"I"+TOKEN+")V");load(after,type,value);method.instructions.insert(instruction,after);
    }
    private static void containerRead(MethodNode method,MethodInsnNode invoke,int token,boolean map){
        Type keyType=map?Type.getType(Object.class):Type.INT_TYPE;int key=local(method,keyType),container=local(method,Type.getType(Object.class)),value=local(method,Type.getType(Object.class));
        InsnList before=new InsnList();store(before,keyType,key);before.add(new VarInsnNode(Opcodes.ASTORE,container));before.add(new VarInsnNode(Opcodes.ALOAD,container));load(before,keyType,key);method.instructions.insertBefore(invoke,before);
        InsnList after=new InsnList();after.add(new VarInsnNode(Opcodes.ASTORE,value));after.add(new VarInsnNode(Opcodes.ALOAD,token));after.add(new VarInsnNode(Opcodes.ALOAD,container));load(after,keyType,key);ControlClassWriter.box(after,keyType);
        after.add(new VarInsnNode(Opcodes.ALOAD,value));after.add(new InsnNode(map?Opcodes.ICONST_1:Opcodes.ICONST_0));call(after,"equipmentSlotContainerRead","("+TOKEN+TOKEN+TOKEN+TOKEN+"Z)V");after.add(new VarInsnNode(Opcodes.ALOAD,value));method.instructions.insert(invoke,after);
    }
    private static boolean selector(MethodInsnNode invoke){
        if(invoke.owner.equals("java/lang/Enum")&&invoke.name.equals("ordinal")&&invoke.desc.equals("()I"))return true;
        if(invoke.owner.equals(SLOT+"$Type")&&invoke.name.equals("ordinal")&&invoke.desc.equals("()I"))return true;
        return invoke.owner.equals(SLOT)&&((invoke.name.equals("ordinal")||invoke.name.equals("getIndex")||invoke.name.equals("m_20749_"))&&invoke.desc.equals("()I")
                ||(invoke.name.equals("getType")||invoke.name.equals("m_20743_"))&&invoke.desc.equals("()L"+SLOT+"$Type;"));
    }
    private static void selectorRead(MethodNode method,MethodInsnNode invoke,int token){
        Type type=Type.getReturnType(invoke.desc);int receiver=local(method,Type.getType(Object.class)),value=local(method,type);
        InsnList before=new InsnList();before.add(new InsnNode(Opcodes.DUP));before.add(new VarInsnNode(Opcodes.ASTORE,receiver));method.instructions.insertBefore(invoke,before);
        InsnList after=new InsnList();store(after,type,value);after.add(new VarInsnNode(Opcodes.ALOAD,token));after.add(new VarInsnNode(Opcodes.ALOAD,receiver));after.add(new LdcInsnNode(invoke.name));after.add(new LdcInsnNode(invoke.desc));load(after,type,value);ControlClassWriter.box(after,type);
        call(after,"equipmentSlotSelectorRead","("+TOKEN+TOKEN+"Ljava/lang/String;Ljava/lang/String;"+TOKEN+")V");load(after,type,value);method.instructions.insert(invoke,after);
    }
    private static void stackComparison(MethodNode method,AbstractInsnNode instruction,int token){
        InsnList before=new InsnList();LabelNode ordinary=new LabelNode();
        if(instruction.getOpcode()==Opcodes.IF_ACMPEQ||instruction.getOpcode()==Opcodes.IF_ACMPNE){
            before.add(new InsnNode(Opcodes.DUP2));before.add(new TypeInsnNode(Opcodes.INSTANCEOF,STACK));before.add(new InsnNode(Opcodes.SWAP));before.add(new TypeInsnNode(Opcodes.INSTANCEOF,STACK));before.add(new InsnNode(Opcodes.IOR));
        }else{before.add(new InsnNode(Opcodes.DUP));before.add(new TypeInsnNode(Opcodes.INSTANCEOF,STACK));}
        before.add(new JumpInsnNode(Opcodes.IFEQ,ordinary));unresolved(before,token);before.add(ordinary);method.instructions.insertBefore(instruction,before);
    }
}
