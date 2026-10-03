package dev.ronova.pro.mixin;

import java.util.*;
import dev.ronova.pro.RenderFaultWeaver;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.*;
import org.spongepowered.asm.mixin.extensibility.*;

/** A real outer finally after EntityHooks has merged, including exceptional serializers. */
public final class SaveFinallyPlugin implements IMixinConfigPlugin {
    @Override public void onLoad(String mixinPackage) { dev.ronova.pro.ProBootstrap.install(); }
    @Override public String getRefMapperConfig() { return null; }
    @Override public boolean shouldApplyMixin(String target,String mixin) { return true; }
    @Override public void acceptTargets(Set<String> mine,Set<String> others) { }
    @Override public List<String> getMixins() { return null; }
    @Override public void preApply(String target,ClassNode node,String mixin,IMixinInfo info) { }
    @Override public void postApply(String target,ClassNode node,String mixin,IMixinInfo info) {
        if(mixin.endsWith(".IndexHooks$Movement")) { weaveSectionMove(node);return; }
        if(mixin.endsWith(".ClientItemFaultHooks$Item")) { weaveItemDraw(node);RenderFaultWeaver.apply(node);return; }
        if(mixin.endsWith(".ClientItemFaultHooks$Tooltip") || mixin.contains(".ClientDrawFaultHooks$")) { RenderFaultWeaver.apply(node);return; }
        if(mixin.endsWith(".ClientFaultHooks$Draw")) { weaveShaderDraw(node);return; }
        if(mixin.endsWith(".EntityHooks")) { weaveEntitySave(node);return; }
        if(mixin.endsWith(".PlayerLifecycleHooks$Respawn")) {
            weaveLifecycle(node,"respawn","(Lnet/minecraft/server/level/ServerPlayer;Z)Lnet/minecraft/server/level/ServerPlayer;",1,-1);
            weaveLifecycle(node,"remove","(Lnet/minecraft/server/level/ServerPlayer;)V",1,-1);return;
        }
        if(mixin.endsWith(".PlayerLifecycleHooks$DimensionChange")) {
            weaveLifecycle(node,"changeDimension","(Lnet/minecraft/server/level/ServerLevel;)Lnet/minecraft/world/entity/Entity;",0,1);
            weaveLifecycle(node,"changeDimension","(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraftforge/common/util/ITeleporter;)Lnet/minecraft/world/entity/Entity;",0,1);return;
        }
        if(mixin.endsWith(".PlayerLifecycleHooks$ServerPlayerTransfer")) {
            weaveLifecycle(node,"changeDimension","(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraftforge/common/util/ITeleporter;)Lnet/minecraft/world/entity/Entity;",0,1);
            weaveLifecycle(node,"teleportTo","(Lnet/minecraft/server/level/ServerLevel;DDDLjava/util/Set;FF)Z",0,1);
            weaveLifecycle(node,"teleportTo","(Lnet/minecraft/server/level/ServerLevel;DDDFF)V",0,1);
        }
    }
    private static void weaveSectionMove(ClassNode node) {
        int patched=0;
        for(MethodNode method:node.methods)if((method.name.equals("onMove")||method.name.equals("m_142044_"))&&method.desc.equals("()V"))
            for(AbstractInsnNode insn:method.instructions.toArray())if(insn instanceof MethodInsnNode call
                    &&call.owner.equals("net/minecraft/world/level/entity/EntitySection")
                    &&(call.name.equals("remove")||call.name.equals("m_188355_"))&&call.desc.equals("(Lnet/minecraft/world/level/entity/EntityAccess;)Z")) {
                method.instructions.insertBefore(call,new VarInsnNode(Opcodes.ALOAD,0));
                method.instructions.set(call,new MethodInsnNode(Opcodes.INVOKESTATIC,"dev/ronova/pro/SectionMoves","remove",
                    "(Lnet/minecraft/world/level/entity/EntitySection;Lnet/minecraft/world/level/entity/EntityAccess;Ljava/lang/Object;)Z",false));
                method.maxStack=Math.max(method.maxStack,3);patched++;
            }
        int published=0;
        for(MethodNode method:node.methods)if((method.name.equals("onMove")||method.name.equals("m_142044_"))&&method.desc.equals("()V"))
            for(AbstractInsnNode insn:method.instructions.toArray())if(insn instanceof MethodInsnNode call&&call.owner.equals(node.name)
                    &&call.desc.equals("(Lnet/minecraft/world/level/entity/Visibility;Lnet/minecraft/world/level/entity/Visibility;)V")
                    &&(call.name.equals("updateStatus")||call.name.equals("m_157620_"))) {
                InsnList publish=new InsnList();publish.add(new VarInsnNode(Opcodes.ALOAD,0));
                publish.add(new MethodInsnNode(Opcodes.INVOKESTATIC,"dev/ronova/pro/ProRuntime","sectionMoved","(Ljava/lang/Object;)V",false));
                method.instructions.insertBefore(call,publish);method.maxStack++;published++;
            }
        if(patched!=1||published!=1)throw new IllegalStateException("SECTION_MOVEMENT_BOUNDARY_UNAVAILABLE:"+patched+"/"+published);
    }
    private static void weaveEntitySave(ClassNode node) {
        var found=node.methods.stream().filter(method->method.desc.equals("(Lnet/minecraft/nbt/CompoundTag;)Lnet/minecraft/nbt/CompoundTag;")
                &&(method.access&Opcodes.ACC_STATIC)==0).toList();
        if(found.size()!=1)throw new IllegalStateException("ENTITY_SAVE_FINALLY_BOUNDARY_UNAVAILABLE");
        MethodNode method=found.get(0);expandFrames(node,method);int result=method.maxLocals++,failure=method.maxLocals++;
        LabelNode start=new LabelNode(),end=new LabelNode(),normal=new LabelNode(),handler=new LabelNode();
        method.instructions.insert(start);
        for(AbstractInsnNode instruction:method.instructions.toArray())if(instruction.getOpcode()==Opcodes.ARETURN) {
            var exit=new InsnList();exit.add(new VarInsnNode(Opcodes.ASTORE,result));exit.add(new JumpInsnNode(Opcodes.GOTO,normal));
            method.instructions.insertBefore(instruction,exit);method.instructions.remove(instruction);
        }
        method.instructions.add(end);method.instructions.add(normal);
        Object[] locals=new Object[result+1];Arrays.fill(locals,Opcodes.TOP);locals[0]=node.name;locals[1]="net/minecraft/nbt/CompoundTag";locals[result]="net/minecraft/nbt/CompoundTag";
        method.instructions.add(new FrameNode(Opcodes.F_NEW,locals.length,locals,0,new Object[0]));
        finish(method.instructions);method.instructions.add(new VarInsnNode(Opcodes.ALOAD,result));method.instructions.add(new InsnNode(Opcodes.ARETURN));
        method.instructions.add(handler);
        method.instructions.add(new FrameNode(Opcodes.F_NEW,2,new Object[]{node.name,"net/minecraft/nbt/CompoundTag"},1,new Object[]{"java/lang/Throwable"}));
        method.instructions.add(new VarInsnNode(Opcodes.ASTORE,failure));finish(method.instructions);
        method.instructions.add(new VarInsnNode(Opcodes.ALOAD,failure));method.instructions.add(new InsnNode(Opcodes.ATHROW));
        method.tryCatchBlocks.add(new TryCatchBlockNode(start,end,handler,"java/lang/Throwable"));
        method.maxStack=Math.max(method.maxStack,2);
    }
    private static void weaveItemDraw(ClassNode node) {
        String descriptor="(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemDisplayContext;ZLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;IILnet/minecraft/client/resources/model/BakedModel;)V";
        // Foreign mixins may add instance helpers with this exact descriptor. Only the real
        // entry is ours: prefer the production SRG name, then the development mapping.
        var matches=node.methods.stream().filter(m->m.name.equals("m_115143_")&&m.desc.equals(descriptor)
                &&(m.access&(Opcodes.ACC_STATIC|Opcodes.ACC_ABSTRACT|Opcodes.ACC_NATIVE))==0).toList();
        if(matches.isEmpty())matches=node.methods.stream().filter(m->m.name.equals("render")&&m.desc.equals(descriptor)
                &&(m.access&(Opcodes.ACC_STATIC|Opcodes.ACC_ABSTRACT|Opcodes.ACC_NATIVE))==0).toList();
        if(matches.size()!=1) {
            // Optional error isolation must not poison RenderType/class initialization if a
            // different renderer backend has removed the supported entry. Do not claim coverage.
            System.err.println("RONOVA_ITEM_FAULT_BOUNDARY_UNAVAILABLE:"+node.name+":matches="+matches.size());return;
        }
        MethodNode method=matches.get(0);int token=method.maxLocals++;String helper="dev/ronova/pro/ClientItemFaults";
        extendFramesWithLease(node,method,token);
        for(AbstractInsnNode instruction:method.instructions.toArray())if(instruction.getOpcode()==Opcodes.RETURN) {
            InsnList finish=new InsnList();finish.add(new VarInsnNode(Opcodes.ALOAD,token));
            finish.add(new MethodInsnNode(Opcodes.INVOKESTATIC,helper,"leave","(Ljava/lang/Object;)V",false));
            method.instructions.insertBefore(instruction,finish);
        }
        LabelNode start=new LabelNode(),end=new LabelNode(),handler=new LabelNode(),rethrow=new LabelNode(),run=new LabelNode();
        InsnList entry=new InsnList();
        for(int local:new int[]{1,8,4,5})entry.add(new VarInsnNode(Opcodes.ALOAD,local));
        entry.add(new MethodInsnNode(Opcodes.INVOKESTATIC,helper,"begin",
                "(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/client/resources/model/BakedModel;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;)Ljava/lang/Object;",false));
        entry.add(new VarInsnNode(Opcodes.ASTORE,token));entry.add(new VarInsnNode(Opcodes.ALOAD,token));
        entry.add(new JumpInsnNode(Opcodes.IFNONNULL,run));entry.add(new InsnNode(Opcodes.RETURN));entry.add(run);
        Object[] locals=lifecycleFrameLocals(node,method,token);
        entry.add(new FrameNode(Opcodes.F_NEW,locals.length,locals,0,new Object[0]));
        entry.add(new VarInsnNode(Opcodes.ALOAD,token));entry.add(new MethodInsnNode(Opcodes.INVOKESTATIC,helper,"pose","(Ljava/lang/Object;)Lcom/mojang/blaze3d/vertex/PoseStack;",false));
        entry.add(new VarInsnNode(Opcodes.ASTORE,4));entry.add(start);method.instructions.insert(entry);
        method.instructions.add(end);method.instructions.add(handler);
        method.instructions.add(new FrameNode(Opcodes.F_NEW,locals.length,locals,1,new Object[]{"java/lang/Throwable"}));
        method.instructions.add(new InsnNode(Opcodes.DUP));method.instructions.add(new VarInsnNode(Opcodes.ALOAD,token));method.instructions.add(new InsnNode(Opcodes.SWAP));
        method.instructions.add(new MethodInsnNode(Opcodes.INVOKESTATIC,helper,"failed","(Ljava/lang/Object;Ljava/lang/Throwable;)Z",false));
        method.instructions.add(new JumpInsnNode(Opcodes.IFEQ,rethrow));method.instructions.add(new InsnNode(Opcodes.POP));method.instructions.add(new InsnNode(Opcodes.RETURN));
        method.instructions.add(rethrow);method.instructions.add(new FrameNode(Opcodes.F_NEW,locals.length,locals,1,new Object[]{"java/lang/Throwable"}));
        method.instructions.add(new InsnNode(Opcodes.ATHROW));method.tryCatchBlocks.add(new TryCatchBlockNode(start,end,handler,"java/lang/Throwable"));
        method.maxStack=Math.max(method.maxStack,4);
        System.out.println("RONOVA_ITEM_FAULT_BOUNDARY_INSTALLED:"+node.name+"#"+method.name);
    }
    private static void weaveShaderDraw(ClassNode node) {
        String descriptor="(Lorg/joml/Matrix4f;Lorg/joml/Matrix4f;Lnet/minecraft/client/renderer/ShaderInstance;)V";
        var matches=node.methods.stream().filter(m->m.desc.equals(descriptor)
                &&(m.access&Opcodes.ACC_PRIVATE)!=0&&(m.access&Opcodes.ACC_SYNTHETIC)==0).toList();
        if(matches.size()!=1)throw new IllegalStateException("SHADER_DRAW_BOUNDARY_UNAVAILABLE");
        MethodNode method=matches.get(0);expandFrames(node,method);
        LabelNode start=new LabelNode(),end=new LabelNode(),handler=new LabelNode(),rethrow=new LabelNode();
        method.instructions.insert(start);method.instructions.add(end);method.instructions.add(handler);
        Object[] locals={node.name,"org/joml/Matrix4f","org/joml/Matrix4f","net/minecraft/client/renderer/ShaderInstance"};
        method.instructions.add(new FrameNode(Opcodes.F_NEW,locals.length,locals,1,new Object[]{"java/lang/RuntimeException"}));
        method.instructions.add(new InsnNode(Opcodes.DUP));method.instructions.add(new VarInsnNode(Opcodes.ALOAD,3));
        method.instructions.add(new InsnNode(Opcodes.SWAP));
        method.instructions.add(new MethodInsnNode(Opcodes.INVOKESTATIC,"dev/ronova/pro/ClientFaults","drawFailed",
                "(Lnet/minecraft/client/renderer/ShaderInstance;Ljava/lang/Throwable;)Z",false));
        method.instructions.add(new JumpInsnNode(Opcodes.IFEQ,rethrow));
        method.instructions.add(new InsnNode(Opcodes.POP));method.instructions.add(new InsnNode(Opcodes.RETURN));
        method.instructions.add(rethrow);
        method.instructions.add(new FrameNode(Opcodes.F_NEW,locals.length,locals,1,new Object[]{"java/lang/RuntimeException"}));
        method.instructions.add(new InsnNode(Opcodes.ATHROW));
        method.tryCatchBlocks.add(new TryCatchBlockNode(start,end,handler,"java/lang/RuntimeException"));
        method.maxStack=Math.max(method.maxStack,3);
    }
    private static void weaveLifecycle(ClassNode node,String name,String descriptor,int entityLocal,int destinationLocal) {
        var matches=node.methods.stream().filter(method->lifecycleName(method.name,name,descriptor)&&method.desc.equals(descriptor)
                &&(method.access&Opcodes.ACC_STATIC)==0).toList();
        if(matches.size()!=1)throw new IllegalStateException("PLAYER_LIFECYCLE_BOUNDARY_UNAVAILABLE:"+node.name+":"+name+descriptor);
        MethodNode method=matches.get(0);int lease=method.maxLocals++;
        var enter=new InsnList();enter.add(new VarInsnNode(Opcodes.ALOAD,entityLocal));
        if(destinationLocal<0)enter.add(new InsnNode(Opcodes.ACONST_NULL));else enter.add(new VarInsnNode(Opcodes.ALOAD,destinationLocal));
        enter.add(new MethodInsnNode(Opcodes.INVOKESTATIC,"dev/ronova/pro/ProRuntime","beginPlayerLifecycle",
                "(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/server/level/ServerLevel;)Ljava/lang/Object;",false));
        enter.add(new VarInsnNode(Opcodes.ASTORE,lease));
        LabelNode start=new LabelNode(),end=new LabelNode(),handler=new LabelNode();enter.add(start);method.instructions.insert(enter);
        extendFramesWithLease(node,method,lease);
        for(AbstractInsnNode instruction:method.instructions.toArray()) {
            int opcode=instruction.getOpcode();
            if(opcode>=Opcodes.IRETURN&&opcode<=Opcodes.RETURN) {
                var leave=new InsnList();leave.add(new VarInsnNode(Opcodes.ALOAD,lease));
                leave.add(new MethodInsnNode(Opcodes.INVOKESTATIC,"dev/ronova/pro/ProRuntime","endPlayerLifecycle","(Ljava/lang/Object;)V",false));
                method.instructions.insertBefore(instruction,leave);
            }
        }
        method.instructions.add(end);method.instructions.add(handler);
        Object[] locals=lifecycleFrameLocals(node,method,lease);
        method.instructions.add(new FrameNode(Opcodes.F_NEW,locals.length,locals,1,new Object[]{"java/lang/Throwable"}));
        method.instructions.add(new VarInsnNode(Opcodes.ALOAD,lease));
        method.instructions.add(new MethodInsnNode(Opcodes.INVOKESTATIC,"dev/ronova/pro/ProRuntime","endPlayerLifecycle","(Ljava/lang/Object;)V",false));
        method.instructions.add(new InsnNode(Opcodes.ATHROW));
        method.tryCatchBlocks.add(new TryCatchBlockNode(start,end,handler,"java/lang/Throwable"));
        method.maxStack=Math.max(method.maxStack,3);
    }
    private static boolean lifecycleName(String actual,String named,String descriptor) {
        if(actual.equals(named))return true;
        return switch(named) {
            case "respawn" -> actual.equals("m_11236_");
            case "remove" -> actual.equals("m_11286_");
            case "changeDimension" -> actual.equals("m_5489_");
            case "teleportTo" -> descriptor.endsWith(")Z")?actual.equals("m_264318_"):actual.equals("m_8999_");
            default -> false;
        };
    }
    public static void extendFramesWithLease(ClassNode node,MethodNode method,int lease) {
        expandFrames(node,method);
        for(AbstractInsnNode instruction:method.instructions.toArray())if(instruction instanceof FrameNode frame) {
            var extended=new ArrayList<>(frame.local);int slots=localSlots(extended);
            while(slots<lease) { extended.add(Opcodes.TOP);slots++; }
            if(slots!=lease)throw new IllegalStateException("LIFECYCLE_LEASE_SLOT_OVERLAP");
            extended.add("java/lang/Object");frame.local=extended;
        }
    }
    private static void expandFrames(ClassNode node,MethodNode method) {
        // ASM requires one representation per method. New handler/run frames
        // use F_NEW, so preserve each old frame's state in that same form.
        java.util.List<Object> current=initialLocals(node,method);
        for(AbstractInsnNode instruction:method.instructions.toArray())if(instruction instanceof FrameNode frame) {
            java.util.List<Object> base=new ArrayList<>(current),stack;
            switch(frame.type) {
                case Opcodes.F_NEW,Opcodes.F_FULL -> base=frame.local==null?new ArrayList<>():new ArrayList<>(frame.local);
                case Opcodes.F_APPEND -> base.addAll(frame.local);
                case Opcodes.F_CHOP -> {
                    int chop=frame.local==null?0:frame.local.size();
                    if(chop>base.size())throw new IllegalStateException("INVALID_LIFECYCLE_CHOP_FRAME");
                    base.subList(base.size()-chop,base.size()).clear();
                }
                case Opcodes.F_SAME,Opcodes.F_SAME1 -> { }
                default -> throw new IllegalStateException("INVALID_LIFECYCLE_FRAME_TYPE:"+frame.type);
            }
            stack=frame.type==Opcodes.F_SAME1||frame.type==Opcodes.F_NEW||frame.type==Opcodes.F_FULL
                    ?(frame.stack==null?new ArrayList<>():new ArrayList<>(frame.stack)):new ArrayList<>();
            frame.type=Opcodes.F_NEW;frame.local=base;frame.stack=stack;
            current=base;
        }
    }
    private static ArrayList<Object> initialLocals(ClassNode node,MethodNode method) {
        var locals=new ArrayList<Object>();
        if((method.access&Opcodes.ACC_STATIC)==0)locals.add(node.name);
        for(Type argument:Type.getArgumentTypes(method.desc))locals.add(frameType(argument));
        return locals;
    }
    private static int localSlots(java.util.List<Object> locals) {
        int slots=0;for(Object value:locals)slots+=value==Opcodes.LONG||value==Opcodes.DOUBLE?2:1;return slots;
    }
    public static Object[] lifecycleFrameLocals(ClassNode node,MethodNode method,int lease) {
        var locals=initialLocals(node,method);int slots=localSlots(locals);
        while(slots<lease) { locals.add(Opcodes.TOP);slots++; }
        if(slots!=lease)throw new IllegalStateException("LIFECYCLE_HANDLER_SLOT_OVERLAP");
        locals.add("java/lang/Object");return locals.toArray();
    }
    private static Object frameType(Type type) {
        return switch(type.getSort()) {
            case Type.BOOLEAN,Type.BYTE,Type.CHAR,Type.SHORT,Type.INT -> Opcodes.INTEGER;
            case Type.FLOAT -> Opcodes.FLOAT;
            case Type.LONG -> Opcodes.LONG;
            case Type.DOUBLE -> Opcodes.DOUBLE;
            case Type.ARRAY -> type.getDescriptor();
            case Type.OBJECT -> type.getInternalName();
            default -> throw new IllegalStateException("UNSUPPORTED_LIFECYCLE_ARGUMENT");
        };
    }
    private static void finish(InsnList code) {
        code.add(new VarInsnNode(Opcodes.ALOAD,1));code.add(new MethodInsnNode(Opcodes.INVOKESTATIC,"dev/ronova/pro/RecoverySources","saveFinished","(Lnet/minecraft/nbt/CompoundTag;)V",false));
    }
}
