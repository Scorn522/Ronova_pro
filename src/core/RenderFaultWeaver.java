package dev.ronova.pro;

import java.util.*;
import dev.ronova.pro.mixin.SaveFinallyPlugin;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;

/** Exact vanilla/Forge entries and exact virtual call sites; foreign same-signature helpers remain untouched. */
public final class RenderFaultWeaver {
    private static final String DRAW="dev/ronova/pro/ClientDrawFaults",STATE="dev/ronova/pro/ClientItemFaults";
    private record Entry(String owner,String srg,String named,String descriptor,String phase) { }
    private static final Entry[] ENTRIES={
        new Entry("net/minecraft/client/gui/Font","m_168645_","drawInBatch8xOutline","(Lnet/minecraft/util/FormattedCharSequence;FFIILorg/joml/Matrix4f;Lnet/minecraft/client/renderer/MultiBufferSource;I)V","font"),
        new Entry("net/minecraft/client/gui/Font","m_271703_","drawInBatch","(Ljava/lang/String;FFIZLorg/joml/Matrix4f;Lnet/minecraft/client/renderer/MultiBufferSource;Lnet/minecraft/client/gui/Font$DisplayMode;II)I","font"),
        new Entry("net/minecraft/client/gui/Font","m_272077_","drawInBatch","(Lnet/minecraft/network/chat/Component;FFIZLorg/joml/Matrix4f;Lnet/minecraft/client/renderer/MultiBufferSource;Lnet/minecraft/client/gui/Font$DisplayMode;II)I","font"),
        new Entry("net/minecraft/client/gui/Font","m_272078_","drawInBatch","(Ljava/lang/String;FFIZLorg/joml/Matrix4f;Lnet/minecraft/client/renderer/MultiBufferSource;Lnet/minecraft/client/gui/Font$DisplayMode;IIZ)I","font"),
        new Entry("net/minecraft/client/gui/Font","m_272191_","drawInBatch","(Lnet/minecraft/util/FormattedCharSequence;FFIZLorg/joml/Matrix4f;Lnet/minecraft/client/renderer/MultiBufferSource;Lnet/minecraft/client/gui/Font$DisplayMode;II)I","font"),
        new Entry("net/minecraft/client/gui/GuiGraphics","m_280053_","renderItem","(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/level/Level;Lnet/minecraft/world/item/ItemStack;III)V","gui-item"),
        new Entry("net/minecraft/client/gui/GuiGraphics","m_280056_","drawString","(Lnet/minecraft/client/gui/Font;Ljava/lang/String;IIIZ)I","font"),
        new Entry("net/minecraft/client/gui/GuiGraphics","m_280064_","renderItem","(Lnet/minecraft/world/item/ItemStack;IIII)V","gui-item"),
        new Entry("net/minecraft/client/gui/GuiGraphics","m_280153_","renderTooltip","(Lnet/minecraft/client/gui/Font;Lnet/minecraft/world/item/ItemStack;II)V","tooltip"),
        new Entry("net/minecraft/client/gui/GuiGraphics","m_280245_","renderTooltip","(Lnet/minecraft/client/gui/Font;Ljava/util/List;II)V","tooltip"),
        new Entry("net/minecraft/client/gui/GuiGraphics","m_280256_","renderItem","(Lnet/minecraft/world/item/ItemStack;III)V","gui-item"),
        new Entry("net/minecraft/client/gui/GuiGraphics","m_280302_","renderItemDecorations","(Lnet/minecraft/client/gui/Font;Lnet/minecraft/world/item/ItemStack;IILjava/lang/String;)V","item-decoration"),
        new Entry("net/minecraft/client/gui/GuiGraphics","m_280370_","renderItemDecorations","(Lnet/minecraft/client/gui/Font;Lnet/minecraft/world/item/ItemStack;II)V","item-decoration"),
        new Entry("net/minecraft/client/gui/GuiGraphics","m_280405_","renderItem","(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/level/Level;Lnet/minecraft/world/item/ItemStack;IIII)V","gui-item"),
        new Entry("net/minecraft/client/gui/GuiGraphics","m_280430_","drawString","(Lnet/minecraft/client/gui/Font;Lnet/minecraft/network/chat/Component;III)I","font"),
        new Entry("net/minecraft/client/gui/GuiGraphics","m_280480_","renderItem","(Lnet/minecraft/world/item/ItemStack;II)V","gui-item"),
        new Entry("net/minecraft/client/gui/GuiGraphics","m_280488_","drawString","(Lnet/minecraft/client/gui/Font;Ljava/lang/String;III)I","font"),
        new Entry("net/minecraft/client/gui/GuiGraphics","m_280497_","renderTooltipInternal","(Lnet/minecraft/client/gui/Font;Ljava/util/List;IILnet/minecraft/client/gui/screens/inventory/tooltip/ClientTooltipPositioner;)V","tooltip"),
        new Entry("net/minecraft/client/gui/GuiGraphics","m_280547_","renderTooltip","(Lnet/minecraft/client/gui/Font;Ljava/util/List;Lnet/minecraft/client/gui/screens/inventory/tooltip/ClientTooltipPositioner;II)V","tooltip"),
        new Entry("net/minecraft/client/gui/GuiGraphics","m_280557_","renderTooltip","(Lnet/minecraft/client/gui/Font;Lnet/minecraft/network/chat/Component;II)V","tooltip"),
        new Entry("net/minecraft/client/gui/GuiGraphics","m_280614_","drawString","(Lnet/minecraft/client/gui/Font;Lnet/minecraft/network/chat/Component;IIIZ)I","font"),
        new Entry("net/minecraft/client/gui/GuiGraphics","m_280638_","renderItem","(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/item/ItemStack;III)V","gui-item"),
        new Entry("net/minecraft/client/gui/GuiGraphics","m_280648_","drawString","(Lnet/minecraft/client/gui/Font;Lnet/minecraft/util/FormattedCharSequence;III)I","font"),
        new Entry("net/minecraft/client/gui/GuiGraphics","m_280649_","drawString","(Lnet/minecraft/client/gui/Font;Lnet/minecraft/util/FormattedCharSequence;IIIZ)I","font"),
        new Entry("net/minecraft/client/gui/GuiGraphics","m_280677_","renderTooltip","(Lnet/minecraft/client/gui/Font;Ljava/util/List;Ljava/util/Optional;II)V","tooltip"),
        new Entry("net/minecraft/client/gui/screens/Screen","m_280264_","renderWithTooltip","(Lnet/minecraft/client/gui/GuiGraphics;IIF)V","screen"),
        new Entry("net/minecraft/client/renderer/blockentity/BlockEntityRenderDispatcher","m_112267_","render","(Lnet/minecraft/world/level/block/entity/BlockEntity;FLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;)V","block-entity"),
        new Entry("net/minecraft/client/renderer/blockentity/BlockEntityRenderDispatcher","m_112272_","renderItem","(Lnet/minecraft/world/level/block/entity/BlockEntity;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;II)Z","block-entity"),
        new Entry("net/minecraft/client/renderer/entity/EntityRenderDispatcher","m_114384_","render","(Lnet/minecraft/world/entity/Entity;DDDFFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V","entity"),
        new Entry("net/minecraft/client/renderer/entity/ItemRenderer","m_269128_","renderStatic","(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemDisplayContext;IILcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;Lnet/minecraft/world/level/Level;I)V","item-model"),
        new Entry("net/minecraft/client/renderer/entity/ItemRenderer","m_269491_","renderStatic","(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemDisplayContext;ZLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;Lnet/minecraft/world/level/Level;III)V","item-model"),
        new Entry("net/minecraft/client/gui/GuiGraphics","renderTooltip","renderTooltip","(Lnet/minecraft/client/gui/Font;Ljava/util/List;Ljava/util/Optional;Lnet/minecraft/world/item/ItemStack;II)V","tooltip"),
    };
    public static void apply(ClassNode node) {
        int count=0;
        for(Entry e:ENTRIES)if(e.owner.equals(node.name)) {
            MethodNode method=find(node,e.srg,e.descriptor);if(method==null)method=find(node,e.named,e.descriptor);
            if(method==null) {System.err.println("RONOVA_DRAW_FAULT_UNAVAILABLE:"+node.name+"#"+e.srg);continue;}
            wrap(node,method,e.phase);count++;
        }
        for(MethodNode method:node.methods)for(AbstractInsnNode insn:method.instructions.toArray())if(insn instanceof MethodInsnNode call) {
            String hook=null;
            if(node.name.equals("net/minecraft/client/gui/screens/Screen") && call.owner.equals("net/minecraft/client/gui/components/Renderable") &&
                    (call.name.equals("render")||call.name.equals("m_88315_")) && call.desc.equals("(Lnet/minecraft/client/gui/GuiGraphics;IIF)V"))hook="widget";
            if(node.name.equals("net/minecraftforge/client/gui/overlay/ForgeGui") && call.owner.equals("net/minecraftforge/client/gui/overlay/IGuiOverlay") &&
                    call.name.equals("render") && call.desc.equals("(Lnet/minecraftforge/client/gui/overlay/ForgeGui;Lnet/minecraft/client/gui/GuiGraphics;FII)V"))hook="overlay";
            if(node.name.equals("net/minecraft/client/particle/ParticleEngine")) {
                if(call.owner.equals("net/minecraft/client/particle/Particle") && (call.name.equals("render")||call.name.equals("m_5744_")) &&
                        call.desc.equals("(Lcom/mojang/blaze3d/vertex/VertexConsumer;Lnet/minecraft/client/Camera;F)V"))hook="particle";
                if(call.owner.equals("net/minecraft/client/particle/ParticleRenderType")) {
                    if(call.desc.equals("(Lcom/mojang/blaze3d/vertex/BufferBuilder;Lnet/minecraft/client/renderer/texture/TextureManager;)V") && (call.name.equals("begin")||call.name.equals("m_6505_")))hook="particleBegin";
                    if(call.desc.equals("(Lcom/mojang/blaze3d/vertex/Tesselator;)V") && (call.name.equals("end")||call.name.equals("m_6294_")))hook="particleEnd";
                }
            }
            if(hook!=null) {method.instructions.set(call,new MethodInsnNode(Opcodes.INVOKESTATIC,DRAW,hook,"(L"+call.owner+";"+call.desc.substring(1),false));count++;}
        }
        // The vanilla wrapper only creates a crash report around input callbacks. Replace that
        // wrapper, not the callback body, and never retry a click that may already have sent a packet.
        if(node.name.equals("net/minecraft/client/gui/screens/Screen")) {
            String d="(Ljava/lang/Runnable;Ljava/lang/String;Ljava/lang/String;)V";
            MethodNode m=find(node,"m_96579_",d);if(m==null)m=find(node,"wrapScreenError",d);
            if(m!=null) {
                for(AbstractInsnNode insn:m.instructions.toArray())if(insn instanceof MethodInsnNode call && call.owner.equals("java/lang/Runnable") && call.name.equals("run") && call.desc.equals("()V")) {
                    InsnList arguments=new InsnList();arguments.add(new VarInsnNode(Opcodes.ALOAD,1));arguments.add(new VarInsnNode(Opcodes.ALOAD,2));m.instructions.insertBefore(call,arguments);
                    m.instructions.set(call,new MethodInsnNode(Opcodes.INVOKESTATIC,DRAW,"screenInput",d,false));m.maxStack=Math.max(m.maxStack,3);count++;
                }
            }
        }
        System.out.println("RONOVA_DRAW_FAULT_INSTALLED:"+node.name+":entries="+count);
    }
    private static MethodNode find(ClassNode node,String name,String descriptor) {
        for(MethodNode m:node.methods)if(m.name.equals(name)&&m.desc.equals(descriptor)&&(m.access&(Opcodes.ACC_ABSTRACT|Opcodes.ACC_NATIVE))==0)return m;
        return null;
    }
    private static int argument(MethodNode method,String... names) {
        int slot=(method.access&Opcodes.ACC_STATIC)==0?1:0;
        for(Type type:Type.getArgumentTypes(method.desc)) {
            for(String name:names)if(type.getDescriptor().equals("L"+name+";"))return slot;
            slot+=type.getSize();
        }
        return -1;
    }
    private static void load(InsnList list,int local) {list.add(local<0?new InsnNode(Opcodes.ACONST_NULL):new VarInsnNode(Opcodes.ALOAD,local));}
    private static void defaultReturn(InsnList code,MethodNode method,String phase) {
        Type result=Type.getReturnType(method.desc);
        if(result.getSort()==Type.VOID) {code.add(new InsnNode(Opcodes.RETURN));return;}
        // Font callers expect the final x coordinate. On a skipped draw retain its starting x.
        if(phase.equals("font") && method.desc.startsWith("(Lnet/minecraft/client/gui/Font;"))code.add(new VarInsnNode(Opcodes.ILOAD,3));
        else if(phase.equals("font")) {code.add(new VarInsnNode(Opcodes.FLOAD,2));code.add(new InsnNode(Opcodes.F2I));}
        else code.add(new InsnNode(Opcodes.ICONST_0));
        code.add(new InsnNode(Opcodes.IRETURN));
    }
    private static void wrap(ClassNode node,MethodNode method,String phase) {
        int token=method.maxLocals++;SaveFinallyPlugin.extendFramesWithLease(node,method,token);
        for(AbstractInsnNode insn:method.instructions.toArray())if(insn.getOpcode()>=Opcodes.IRETURN&&insn.getOpcode()<=Opcodes.RETURN) {
            InsnList exit=new InsnList();load(exit,token);exit.add(new MethodInsnNode(Opcodes.INVOKESTATIC,STATE,"leave","(Ljava/lang/Object;)V",false));method.instructions.insertBefore(insn,exit);
        }
        int payload=argument(method,"net/minecraft/world/item/ItemStack");
        if(payload<0)payload=argument(method,"net/minecraft/world/entity/Entity","net/minecraft/world/level/block/entity/BlockEntity","net/minecraft/util/FormattedCharSequence","net/minecraft/network/chat/Component","java/lang/String","java/util/List");
        int context=node.name.equals("net/minecraft/client/gui/GuiGraphics")?0:argument(method,"net/minecraft/client/gui/GuiGraphics","com/mojang/blaze3d/vertex/PoseStack");
        int receiver=phase.equals("font")&&node.name.equals("net/minecraft/client/gui/GuiGraphics")?1:0;
        LabelNode run=new LabelNode(),start=new LabelNode(),end=new LabelNode(),handler=new LabelNode(),rethrow=new LabelNode();
        Object[] locals=SaveFinallyPlugin.lifecycleFrameLocals(node,method,token);
        InsnList entry=new InsnList();entry.add(new LdcInsnNode(phase));load(entry,receiver);load(entry,payload);load(entry,context);
        entry.add(new MethodInsnNode(Opcodes.INVOKESTATIC,DRAW,"begin","(Ljava/lang/String;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;",false));
        entry.add(new VarInsnNode(Opcodes.ASTORE,token));load(entry,token);entry.add(new JumpInsnNode(Opcodes.IFNONNULL,run));defaultReturn(entry,method,phase);
        entry.add(run);entry.add(new FrameNode(Opcodes.F_NEW,locals.length,locals,0,new Object[0]));entry.add(start);method.instructions.insert(entry);
        method.instructions.add(end);method.instructions.add(handler);method.instructions.add(new FrameNode(Opcodes.F_NEW,locals.length,locals,1,new Object[]{"java/lang/Throwable"}));
        method.instructions.add(new InsnNode(Opcodes.DUP));load(method.instructions,token);method.instructions.add(new InsnNode(Opcodes.SWAP));
        method.instructions.add(new MethodInsnNode(Opcodes.INVOKESTATIC,STATE,"failed","(Ljava/lang/Object;Ljava/lang/Throwable;)Z",false));
        method.instructions.add(new JumpInsnNode(Opcodes.IFEQ,rethrow));method.instructions.add(new InsnNode(Opcodes.POP));defaultReturn(method.instructions,method,phase);
        method.instructions.add(rethrow);method.instructions.add(new FrameNode(Opcodes.F_NEW,locals.length,locals,1,new Object[]{"java/lang/Throwable"}));method.instructions.add(new InsnNode(Opcodes.ATHROW));
        method.tryCatchBlocks.add(new TryCatchBlockNode(start,end,handler,"java/lang/Throwable"));method.maxStack=Math.max(method.maxStack,4);
    }
}
