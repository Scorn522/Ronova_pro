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

/** Read witnesses from an exact slot getter and its synchronously invoked Java bodies. */
final class EquipmentSlotBoundary {
    static final String DESCRIPTOR="(Lnet/minecraft/world/entity/EquipmentSlot;)Lnet/minecraft/world/item/ItemStack;";
    private static final String SLOT="net/minecraft/world/entity/EquipmentSlot",STACK="net/minecraft/world/item/ItemStack",BRIDGE="dev/ronova/pro/bootstrap/ControlBridge";
    private static final String TOKEN="Ljava/lang/Object;";
    private static final class Getter {
        final WeakReference<Class<?>> owner;final String name,descriptor;volatile boolean helper,transformed,transformedHelper;
        Getter(Class<?> actual,String name,String descriptor,boolean helper){owner=new WeakReference<>(actual);this.name=name;this.descriptor=descriptor;this.helper=helper;}
    }
    private static volatile Getter[] getters=new Getter[0];
    private EquipmentSlotBoundary(){ }

    private static synchronized Getter declare(Class<?> actual,String name,String descriptor,boolean helper){
        List<Getter> living=new ArrayList<>();Getter found=null;
        for(Getter getter:getters)if(getter.owner.get()!=null){living.add(getter);if(getter.owner.get()==actual&&getter.name.equals(name)&&getter.descriptor.equals(descriptor))found=getter;}
        if(found!=null){
            if(found.helper!=helper){
                if(!DESCRIPTOR.equals(descriptor)||!name.equals("getItemBySlot")&&!name.equals("m_6844_"))throw new IllegalStateException("EQUIPMENT_SLOT_METHOD_ROLE_CONFLICT:"+actual.getName()+"#"+name+descriptor);
                // A canonical getter's root hook also services an exact nested
                // helper call. Upgrade a helper-only body once; never double-weave it.
                if(!helper){found.helper=false;found.transformed=false;}
            }
            return found;
        }
        Getter added=new Getter(actual,name,descriptor,helper);living.add(added);Getter[] next=living.toArray(Getter[]::new);
        ControlImages.protect(next,added,added.owner);getters=next;return added;
    }
    /** No loaded-class scan or class-loader-wide registration is involved. */
    static void install(Instrumentation api,Class<?> actual,String name,String descriptor){
        Objects.requireNonNull(actual);Objects.requireNonNull(name);Objects.requireNonNull(descriptor);
        if(!DESCRIPTOR.equals(descriptor)||!name.equals("getItemBySlot")&&!name.equals("m_6844_"))throw new IllegalArgumentException("EQUIPMENT_SLOT_GETTER_SIGNATURE_REQUIRED");
        install(api,actual,name,descriptor,false);
    }
    static void installHelper(Instrumentation api,Class<?> actual,String name,String descriptor){
        Objects.requireNonNull(actual);Objects.requireNonNull(name);Objects.requireNonNull(descriptor);
        if(actual.getClassLoader()==null)throw new IllegalStateException("EQUIPMENT_SLOT_PLATFORM_HELPER_UNSUPPORTED:"+actual.getName());
        if(name.startsWith("<"))throw new IllegalArgumentException("EQUIPMENT_SLOT_CONSTRUCTOR_HELPER_UNSUPPORTED");
        install(api,actual,name,descriptor,true);
    }
    private static void install(Instrumentation api,Class<?> actual,String name,String descriptor,boolean helper){
        if(api==null||actual.isArray()||actual.isPrimitive()||!api.isModifiableClass(actual))throw new IllegalStateException("EQUIPMENT_SLOT_INSTALL_UNAVAILABLE:"+actual.getName());
        java.lang.reflect.Method declared=null;
        for(var method:actual.getDeclaredMethods())if(method.getName().equals(name)&&Type.getMethodDescriptor(method).equals(descriptor)){declared=method;break;}
        if(declared==null)throw new IllegalArgumentException("EQUIPMENT_SLOT_DECLARATION_MISSING:"+actual.getName()+"#"+name);
        int access=declared.getModifiers();
        if(Modifier.isNative(access))throw new IllegalStateException("EQUIPMENT_SLOT_NATIVE_GETTER_UNSUPPORTED:"+actual.getName());
        if(Modifier.isAbstract(access))throw new IllegalStateException("EQUIPMENT_SLOT_ABSTRACT_GETTER_UNSUPPORTED:"+actual.getName());
        if(!helper&&(Modifier.isStatic(access)||Modifier.isPrivate(access)))throw new IllegalStateException("EQUIPMENT_SLOT_VIRTUAL_GETTER_REQUIRED:"+actual.getName());
        Getter getter=declare(actual,name,descriptor,helper);
        synchronized(getter){
            if(getter.transformed&&getter.transformedHelper==getter.helper)return;
            try{api.retransformClasses(actual);}
            catch(java.lang.instrument.UnmodifiableClassException failure){getter.transformed=false;throw new IllegalStateException("EQUIPMENT_SLOT_INSTALL_FAILED:"+actual.getName(),failure);}
            catch(RuntimeException|Error failure){getter.transformed=false;throw failure;}
            if(!getter.transformed||getter.transformedHelper!=getter.helper)throw new IllegalStateException("EQUIPMENT_SLOT_TRANSFORM_MISSING:"+actual.getName());
        }
    }
    static void transformFailed(Class<?> actual){if(actual!=null)for(Getter getter:getters)if(getter.owner.get()==actual)getter.transformed=false;}
    /** This pass must precede dispatch/action fallbacks, which are not natural returns. */
    static byte[] transform(ClassLoader loader,String name,Class<?> actual,byte[] bytes){
        if(actual==null||name==null||actual.getClassLoader()!=loader||!actual.getName().replace('.','/').equals(name))return null;
        List<Getter> selected=new ArrayList<>();java.util.IdentityHashMap<Getter,Boolean> roles=new java.util.IdentityHashMap<>();
        for(Getter getter:getters)if(getter.owner.get()==actual){getter.transformed=false;selected.add(getter);roles.put(getter,getter.helper);}
        if(selected.isEmpty())return null;
        ClassNode node=new ClassNode();new ClassReader(bytes).accept(node,0);
        for(Getter getter:selected){
            boolean helper=roles.get(getter);
            MethodNode found=null;for(MethodNode method:node.methods)if(getter.name.equals(method.name)&&getter.descriptor.equals(method.desc)){if(found!=null)throw new IllegalStateException("EQUIPMENT_SLOT_AMBIGUOUS_BODY:"+name);found=method;}
            if(found==null)throw new IllegalStateException("EQUIPMENT_SLOT_BODY_MISSING:"+name);
            if(found.name.startsWith("<")||(found.access&(Opcodes.ACC_NATIVE|Opcodes.ACC_ABSTRACT))!=0
                    ||!helper&&(found.access&(Opcodes.ACC_STATIC|Opcodes.ACC_PRIVATE))!=0)throw new IllegalStateException("EQUIPMENT_SLOT_BODY_UNSUPPORTED:"+name);
            if(helper)weave(node.name,found,true);else weave(found);
        }
        ControlClassWriter writer=new ControlClassWriter(loader,node);node.accept(writer);byte[] result=writer.toByteArray();
        for(Getter getter:selected){getter.transformedHelper=roles.get(getter);getter.transformed=true;}return result;
    }
    private static void call(InsnList code,String name,String descriptor){code.add(new MethodInsnNode(Opcodes.INVOKESTATIC,BRIDGE,name,descriptor,false));}
    private static int local(MethodNode method,Type type){int at=method.maxLocals;method.maxLocals+=type.getSize();if(method.maxLocals>65535)throw new IllegalStateException("EQUIPMENT_SLOT_LOCAL_LIMIT");return at;}
    private static void load(InsnList code,Type type,int slot){code.add(new VarInsnNode(type.getOpcode(Opcodes.ILOAD),slot));}
    private static void store(InsnList code,Type type,int slot){code.add(new VarInsnNode(type.getOpcode(Opcodes.ISTORE),slot));}
    private static void unresolved(InsnList code,int token){code.add(new VarInsnNode(Opcodes.ALOAD,token));call(code,"equipmentSlotUnresolved","("+TOKEN+")V");}
    private static void weave(MethodNode method){weave(null,method,false);}
    private static void arguments(InsnList code,Type[] types,int[] locals){
        code.add(new LdcInsnNode(types.length));code.add(new TypeInsnNode(Opcodes.ANEWARRAY,"java/lang/Object"));
        for(int at=0;at<types.length;at++){
            code.add(new InsnNode(Opcodes.DUP));code.add(new LdcInsnNode(at));load(code,types[at],locals[at]);ControlClassWriter.box(code,types[at]);code.add(new InsnNode(Opcodes.AASTORE));
        }
    }
    private static void weave(String declaring,MethodNode method,boolean helper){
        AbstractInsnNode[] original=method.instructions.toArray();
        Type result=Type.getReturnType(method.desc);int token=local(method,Type.getType(Object.class)),returned=result.getSort()==Type.VOID?-1:local(method,result),failure=local(method,Type.getType(Throwable.class));
        LabelNode start=new LabelNode(),end=new LabelNode(),handler=new LabelNode();InsnList head=new InsnList();
        if(helper){
            boolean statik=(method.access&Opcodes.ACC_STATIC)!=0;Type[] types=Type.getArgumentTypes(method.desc);int[] slots=new int[types.length];int cursor=statik?0:1;
            for(int at=0;at<types.length;at++){slots[at]=cursor;cursor+=types[at].getSize();}
            head.add(statik?new InsnNode(Opcodes.ACONST_NULL):new VarInsnNode(Opcodes.ALOAD,0));head.add(new LdcInsnNode(Type.getObjectType(declaring)));
            head.add(new LdcInsnNode(method.name));head.add(new LdcInsnNode(method.desc));arguments(head,types,slots);
            call(head,"beginEquipmentSlotMethod","("+TOKEN+"Ljava/lang/Class;Ljava/lang/String;Ljava/lang/String;[Ljava/lang/Object;)"+TOKEN);
        }else{
            int receiver=local(method,Type.getType(Object.class)),slot=local(method,Type.getType(Object.class));
            head.add(new VarInsnNode(Opcodes.ALOAD,0));head.add(new VarInsnNode(Opcodes.ASTORE,receiver));head.add(new VarInsnNode(Opcodes.ALOAD,1));head.add(new VarInsnNode(Opcodes.ASTORE,slot));
            head.add(new VarInsnNode(Opcodes.ALOAD,receiver));head.add(new VarInsnNode(Opcodes.ALOAD,slot));call(head,"beginEquipmentSlotRead","("+TOKEN+TOKEN+")"+TOKEN);
        }
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
                if(invoke.name.startsWith("<")){InsnList unknown=new InsnList();unresolved(unknown,token);method.instructions.insertBefore(instruction,unknown);}
                else if(virtual&&selector(invoke))selectorRead(method,invoke,token);
                else invocation(method,invoke,token);
                continue;
            }
            if(instruction instanceof InvokeDynamicInsnNode){InsnList unknown=new InsnList();unresolved(unknown,token);method.instructions.insertBefore(instruction,unknown);continue;}
            if(opcode==Opcodes.IF_ACMPEQ||opcode==Opcodes.IF_ACMPNE||opcode==Opcodes.IFNULL||opcode==Opcodes.IFNONNULL){stackComparison(method,instruction,token);continue;}
            if(opcode>=Opcodes.IRETURN&&opcode<=Opcodes.RETURN){
                InsnList finish=new InsnList();if(returned>=0)store(finish,result,returned);finish.add(new VarInsnNode(Opcodes.ALOAD,token));
                if(returned<0)finish.add(new InsnNode(Opcodes.ACONST_NULL));else{load(finish,result,returned);ControlClassWriter.box(finish,result);}
                call(finish,helper?"endEquipmentSlotMethod":"observeEquipmentSlot","("+TOKEN+TOKEN+")V");
                if(returned>=0)load(finish,result,returned);method.instructions.insertBefore(instruction,finish);
            }
        }
        method.instructions.add(end);method.instructions.add(handler);method.instructions.add(new VarInsnNode(Opcodes.ASTORE,failure));
        method.instructions.add(new VarInsnNode(Opcodes.ALOAD,token));method.instructions.add(new VarInsnNode(Opcodes.ALOAD,failure));
        method.instructions.add(new MethodInsnNode(Opcodes.INVOKESTATIC,BRIDGE,helper?"failedEquipmentSlotMethod":"failedEquipmentSlotRead","("+TOKEN+"Ljava/lang/Throwable;)V",false));
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
    private static void invocation(MethodNode method,MethodInsnNode invoke,int token){
        Type[] types=Type.getArgumentTypes(invoke.desc);Type result=Type.getReturnType(invoke.desc);boolean statik=invoke.getOpcode()==Opcodes.INVOKESTATIC;
        int[] args=new int[types.length];for(int at=0;at<types.length;at++)args[at]=local(method,types[at]);
        int receiver=statik?-1:local(method,Type.getType(Object.class)),callToken=local(method,Type.getType(Object.class)),value=result.getSort()==Type.VOID?-1:local(method,result),failure=local(method,Type.getType(Throwable.class));
        LabelNode start=new LabelNode(),end=new LabelNode(),handler=new LabelNode(),resume=new LabelNode();InsnList before=new InsnList();
        for(int at=types.length-1;at>=0;at--)store(before,types[at],args[at]);
        if(!statik)before.add(new VarInsnNode(Opcodes.ASTORE,receiver));
        before.add(new VarInsnNode(Opcodes.ALOAD,token));before.add(statik?new InsnNode(Opcodes.ACONST_NULL):new VarInsnNode(Opcodes.ALOAD,receiver));
        before.add(new LdcInsnNode(Type.getObjectType(invoke.owner)));before.add(new LdcInsnNode(invoke.name));before.add(new LdcInsnNode(invoke.desc));before.add(new LdcInsnNode(invoke.getOpcode()));arguments(before,types,args);
        call(before,"beginEquipmentSlotCall","("+TOKEN+TOKEN+"Ljava/lang/Class;Ljava/lang/String;Ljava/lang/String;I[Ljava/lang/Object;)"+TOKEN);before.add(new VarInsnNode(Opcodes.ASTORE,callToken));
        if(!statik)before.add(new VarInsnNode(Opcodes.ALOAD,receiver));for(int at=0;at<types.length;at++)load(before,types[at],args[at]);before.add(start);method.instructions.insertBefore(invoke,before);
        InsnList after=new InsnList();after.add(end);
        if(value>=0)store(after,result,value);after.add(new VarInsnNode(Opcodes.ALOAD,callToken));
        if(value<0)after.add(new InsnNode(Opcodes.ACONST_NULL));else{load(after,result,value);ControlClassWriter.box(after,result);}
        call(after,"endEquipmentSlotCall","("+TOKEN+TOKEN+")V");after.add(new JumpInsnNode(Opcodes.GOTO,resume));
        // The narrow handler remains at the original call's position inside
        // its original try regions, so the same Throwable reaches their handlers.
        after.add(handler);after.add(new VarInsnNode(Opcodes.ASTORE,failure));after.add(new VarInsnNode(Opcodes.ALOAD,callToken));after.add(new VarInsnNode(Opcodes.ALOAD,failure));
        call(after,"failedEquipmentSlotCall","("+TOKEN+"Ljava/lang/Throwable;)V");after.add(new VarInsnNode(Opcodes.ALOAD,failure));after.add(new InsnNode(Opcodes.ATHROW));
        after.add(resume);if(value>=0)load(after,result,value);method.instructions.insert(invoke,after);
        method.tryCatchBlocks.add(0,new TryCatchBlockNode(start,end,handler,"java/lang/Throwable"));
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
