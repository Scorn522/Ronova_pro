package dev.ronova.pro.agent;

import java.util.*;
import jdk.internal.org.objectweb.asm.*;
import jdk.internal.org.objectweb.asm.tree.*;

/** Gates individual client callbacks while keeping the enclosing framework cleanup and other shares. */
final class ClientBoundary {
    private static final String BRIDGE="dev/ronova/pro/bootstrap/ClientBridge";
    private static final String PARTICLES="net/minecraft/client/particle/ParticleEngine",TEXTURES="net/minecraft/client/renderer/texture/TextureManager";
    private static final String DYNAMIC="net/minecraft/client/renderer/texture/DynamicTexture",IMAGE="com/mojang/blaze3d/platform/NativeImage";
    static final Set<String> TARGETS=Set.of(
            "net/minecraft/client/Minecraft","net/minecraft/client/KeyboardHandler","net/minecraft/client/MouseHandler",
            "net/minecraft/client/renderer/GameRenderer","net/minecraft/client/renderer/entity/EntityRenderDispatcher",
            "net/minecraft/client/renderer/blockentity/BlockEntityRenderDispatcher",PARTICLES,TEXTURES,
            "net/minecraft/client/particle/Particle","net/minecraft/client/renderer/texture/AbstractTexture",
            DYNAMIC,IMAGE,"net/minecraft/client/renderer/texture/SimpleTexture","net/minecraft/client/renderer/texture/HttpTexture",
            "net/minecraft/client/gui/screens/Screen","net/minecraft/client/gui/components/events/AbstractContainerEventHandler",
            "net/minecraftforge/client/gui/overlay/ForgeGui","net/minecraft/client/gui/components/toasts/ToastComponent",
            "net/minecraft/client/sounds/SoundEngine","com/mojang/blaze3d/systems/RenderSystem",
            "net/minecraft/client/sounds/ChannelAccess","net/minecraft/client/sounds/ChannelAccess$ChannelHandle","com/mojang/blaze3d/audio/Channel",
            "net/minecraft/server/packs/resources/ReloadableResourceManager","net/minecraft/server/packs/resources/SimpleReloadInstance",
            "net/minecraft/server/packs/resources/ProfiledReloadInstance","net/minecraft/server/packs/resources/SimpleReloadInstance$1",
            "net/minecraft/server/packs/resources/PreparableReloadListener$PreparationBarrier",
            "com/mojang/blaze3d/vertex/BufferBuilder","com/mojang/blaze3d/vertex/BufferBuilder$RenderedBuffer","com/mojang/blaze3d/vertex/BufferBuilder$DrawState",
            "com/mojang/blaze3d/vertex/BufferBuilder$SortState","com/mojang/blaze3d/vertex/VertexBuffer","com/mojang/blaze3d/vertex/VertexFormat$Mode",
            "com/mojang/blaze3d/vertex/VertexFormat$IndexType","com/mojang/blaze3d/platform/GlStateManager",
            "com/mojang/blaze3d/vertex/VertexFormat",
            "com/mojang/blaze3d/vertex/BufferUploader");
    private static final Map<String,Set<String>> CALLBACKS=Map.ofEntries(
            Map.entry("net/minecraft/client/renderer/entity/EntityRenderer",Set.of("render")),
            Map.entry("net/minecraft/client/renderer/blockentity/BlockEntityRenderer",Set.of("render")),
            Map.entry("net/minecraft/client/particle/Particle",Set.of("render","tick")),
            Map.entry("net/minecraft/client/particle/TrackingEmitter",Set.of("tick")),
            Map.entry("net/minecraft/client/particle/ParticleProvider",Set.of("createParticle")),
            Map.entry("net/minecraft/client/particle/ParticleProvider$Sprite",Set.of("createParticle")),
            Map.entry("net/minecraft/client/renderer/texture/Tickable",Set.of("tick")),
            Map.entry("net/minecraftforge/client/gui/overlay/IGuiOverlay",Set.of("render")),
            Map.entry("net/minecraft/client/gui/screens/Screen",Set.of("tick","renderWithTooltip","keyPressed","keyReleased","charTyped","mouseClicked","mouseReleased","mouseDragged","mouseScrolled")),
            Map.entry("net/minecraft/client/gui/screens/Overlay",Set.of("render")),
            Map.entry("net/minecraft/client/gui/components/Renderable",Set.of("render")),
            Map.entry("net/minecraft/client/gui/components/events/GuiEventListener",Set.of("keyPressed","keyReleased","charTyped","mouseClicked","mouseReleased","mouseDragged","mouseScrolled")),
            Map.entry("net/minecraft/client/gui/components/toasts/Toast",Set.of("render")),
            Map.entry("net/minecraft/client/resources/sounds/TickableSoundInstance",Set.of("tick")),
            Map.entry("net/minecraft/server/packs/resources/PreparableReloadListener",Set.of("reload","getName")));
    private ClientBoundary(){}
    static byte[] transform(ClassLoader loader,String name,byte[] bytes){
        if(!TARGETS.contains(name))return null;ClassNode node=new ClassNode();new ClassReader(bytes).accept(node,0);boolean changed=false;
        for(MethodNode method:node.methods){
            if((method.access&(Opcodes.ACC_ABSTRACT|Opcodes.ACC_NATIVE))!=0)continue;
            if((name.equals(DYNAMIC)||name.equals(IMAGE))&&(method.name.equals("<init>")||name.equals(DYNAMIC)&&method.name.equals("setPixels"))){
                for(AbstractInsnNode at:method.instructions.toArray())if(at.getOpcode()==Opcodes.RETURN){InsnList hook=new InsnList();hook.add(new VarInsnNode(Opcodes.ALOAD,0));call(hook,"resourceConstructed","(Ljava/lang/Object;)V");method.instructions.insertBefore(at,hook);}changed=true;
            }
            if(name.equals(DYNAMIC)&&method.name.equals("getPixels")&&Type.getReturnType(method.desc).getDescriptor().equals("Lcom/mojang/blaze3d/platform/NativeImage;")){
                for(AbstractInsnNode at:method.instructions.toArray())if(at.getOpcode()==Opcodes.ARETURN){InsnList hook=new InsnList();hook.add(new InsnNode(Opcodes.DUP));hook.add(new VarInsnNode(Opcodes.ALOAD,0));hook.add(new InsnNode(Opcodes.SWAP));call(hook,"pixelsExposed","(Ljava/lang/Object;Ljava/lang/Object;)V");method.instructions.insertBefore(at,hook);}changed=true;
            }
            if(name.equals(IMAGE)&&method.name.equals("close")&&method.desc.equals("()V")){imageClose(method);changed=true;}
            if(method.name.startsWith("<"))continue;
            Type[] args=Type.getArgumentTypes(method.desc);int[] vars=locals(method,args);
            if(name.equals("com/mojang/blaze3d/vertex/BufferBuilder")){
                if(method.name.equals("begin")){afterVoid(method,"verticesBegin");changed=true;}
                if(method.name.equals("endVertex")||method.name.equals("putBulkData")){vertices(method,method.name.equals("putBulkData")&&args.length==1?vars[0]:-1);changed=true;}
                if(method.name.equals("storeRenderedBuffer")){afterObject(method,"verticesStored");changed=true;}
                if(method.name.equals("getSortState")){afterObject(method,"verticesSortState");changed=true;}
                if(method.name.equals("restoreSortState")&&args.length==1){for(AbstractInsnNode at:method.instructions.toArray())if(at.getOpcode()==Opcodes.RETURN){InsnList hook=new InsnList();hook.add(new VarInsnNode(Opcodes.ALOAD,0));hook.add(new VarInsnNode(Opcodes.ALOAD,vars[0]));call(hook,"verticesSortRestored","(Ljava/lang/Object;Ljava/lang/Object;)V");method.instructions.insertBefore(at,hook);}changed=true;}
            }
            if(name.equals("com/mojang/blaze3d/vertex/VertexBuffer")){
                if(method.name.equals("upload")&&args.length==1){upload(method,vars[0]);changed=true;}
                if(method.name.equals("draw")&&method.desc.equals("()V")){draw(method);changed=true;}
                if(method.name.equals("close")){afterVoid(method,"verticesClosed");changed=true;}
            }
            if(name.equals("com/mojang/blaze3d/platform/GlStateManager")){
                if(method.name.equals("glShaderSource")&&method.desc.equals("(ILjava/util/List;)V")){InsnList hook=new InsnList();hook.add(new VarInsnNode(Opcodes.ILOAD,vars[0]));hook.add(new VarInsnNode(Opcodes.ALOAD,vars[1]));call(hook,"shaderSource","(ILjava/util/List;)Ljava/util/List;");hook.add(new VarInsnNode(Opcodes.ASTORE,vars[1]));method.instructions.insert(hook);changed=true;}
                if(Set.of("glCompileShader","glLinkProgram","glDeleteProgram","glDeleteShader").contains(method.name)&&method.desc.equals("(I)V")){for(AbstractInsnNode at:method.instructions.toArray())if(at.getOpcode()==Opcodes.RETURN){InsnList hook=new InsnList();hook.add(new VarInsnNode(Opcodes.ILOAD,vars[0]));call(hook,method.name.equals("glCompileShader")?"shaderCompiled":method.name.equals("glLinkProgram")?"shaderLinked":method.name.equals("glDeleteProgram")?"shaderProgramClosed":"shaderClosed","(I)V");method.instructions.insertBefore(at,hook);}changed=true;}
            }
            if(name.equals("net/minecraft/server/packs/resources/ReloadableResourceManager")&&method.name.equals("registerReloadListener")&&args.length==1){InsnList hook=new InsnList();hook.add(new VarInsnNode(Opcodes.ALOAD,0));hook.add(new VarInsnNode(Opcodes.ALOAD,vars[0]));call(hook,"reloadListenerRegistered","(Ljava/lang/Object;Ljava/lang/Object;)V");method.instructions.insert(hook);changed=true;}
            if(name.equals("net/minecraft/server/packs/resources/ReloadableResourceManager")&&method.name.equals("createReload")){InsnList hook=new InsnList();hook.add(new VarInsnNode(Opcodes.ALOAD,0));call(hook,"reloadStarting","(Ljava/lang/Object;)V");method.instructions.insert(hook);changed=true;}
            if(name.equals(PARTICLES)&&method.name.equals("add")&&args.length==1){guard(method,vars[0],"particleAdmitted");changed=true;}
            if(name.equals(PARTICLES)&&method.name.equals("tickParticle")&&args.length==1){guard(method,vars[0],"particleTick");changed=true;}
            if(name.equals(TEXTURES)&&method.name.equals("register")&&args.length==2&&args[0].getDescriptor().equals("Lnet/minecraft/resources/ResourceLocation;")&&args[1].getDescriptor().equals("Lnet/minecraft/client/renderer/texture/AbstractTexture;")){
                int original=method.maxLocals++;InsnList retain=new InsnList();retain.add(new VarInsnNode(Opcodes.ALOAD,vars[1]));retain.add(new VarInsnNode(Opcodes.ASTORE,original));method.instructions.insert(retain);
                for(AbstractInsnNode at:method.instructions.toArray())if(at.getOpcode()==Opcodes.RETURN){InsnList hook=new InsnList();hook.add(new VarInsnNode(Opcodes.ALOAD,0));hook.add(new VarInsnNode(Opcodes.ALOAD,vars[0]));hook.add(new VarInsnNode(Opcodes.ALOAD,original));call(hook,"textureRegistered","(Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;)V");method.instructions.insertBefore(at,hook);}changed=true;
            }
            if(name.equals(TEXTURES)&&method.name.equals("tick")&&method.desc.equals("()V")){InsnList hook=new InsnList();hook.add(new VarInsnNode(Opcodes.ALOAD,0));call(hook,"textureTick","(Ljava/lang/Object;)V");method.instructions.insert(hook);changed=true;}
            if(name.equals("net/minecraft/client/Minecraft")&&method.name.equals("setScreen")&&args.length==1){guard(method,vars[0],"allowed");changed=true;}
            if(name.equals("net/minecraft/client/Minecraft")&&method.name.equals("runTick")){InsnList hook=new InsnList();call(hook,"gpuMaintenance","()V");method.instructions.insert(hook);changed=true;}
            if(name.equals("net/minecraft/client/Minecraft")&&method.name.equals("close"))for(AbstractInsnNode at:method.instructions.toArray())if(at instanceof MethodInsnNode invoke&&invoke.owner.equals("com/mojang/blaze3d/platform/Window")&&invoke.name.equals("close")&&invoke.desc.equals("()V")){InsnList hook=new InsnList();call(hook,"gpuContextClosing","()V");method.instructions.insertBefore(at,hook);changed=true;}
            if(name.equals("net/minecraft/client/sounds/SoundEngine")&&Set.of("play","playDelayed","queueTickingSound").contains(method.name)&&args.length>=1){guard(method,vars[0],"soundAdmitted");changed=true;}
            if(name.equals("net/minecraft/client/sounds/SoundEngine")&&method.name.equals("tick")&&Type.getReturnType(method.desc).getSort()==Type.VOID){InsnList hook=new InsnList();hook.add(new VarInsnNode(Opcodes.ALOAD,0));call(hook,"soundTick","(Ljava/lang/Object;)V");method.instructions.insert(hook);changed=true;}
            if((name.equals("com/mojang/blaze3d/vertex/BufferUploader")&&Set.of("drawWithShader","draw").contains(method.name)
                    ||name.equals("com/mojang/blaze3d/systems/RenderSystem")&&Set.of("drawElements","recordRenderCall").contains(method.name))&&Type.getReturnType(method.desc).getSort()==Type.VOID){
                InsnList gate=new InsnList();LabelNode allowed=new LabelNode();gate.add(new InsnNode(Opcodes.ACONST_NULL));call(gate,"allowed","(Ljava/lang/Object;)Z");gate.add(new JumpInsnNode(Opcodes.IFNE,allowed));gate.add(new InsnNode(Opcodes.RETURN));gate.add(allowed);method.instructions.insert(gate);changed=true;
            }
            for(AbstractInsnNode at:method.instructions.toArray())if(at instanceof MethodInsnNode invoke&&invoke.getOpcode()!=Opcodes.INVOKESTATIC&&invoke.getOpcode()!=Opcodes.INVOKESPECIAL
                    &&CALLBACKS.getOrDefault(invoke.owner,Set.of()).contains(invoke.name)){
                callback(method,invoke);changed=true;
            }
        }
        if(!changed)return null;ControlClassWriter writer=new ControlClassWriter(loader,node);node.accept(writer);return writer.toByteArray();
    }
    private static int[] locals(MethodNode method,Type[] args){int[] vars=new int[args.length];int next=(method.access&Opcodes.ACC_STATIC)==0?1:0;for(int i=0;i<args.length;i++){vars[i]=next;next+=args[i].getSize();}return vars;}
    private static void call(InsnList code,String name,String descriptor){code.add(new MethodInsnNode(Opcodes.INVOKESTATIC,BRIDGE,name,descriptor,false));}
    private static void afterVoid(MethodNode method,String name){for(AbstractInsnNode at:method.instructions.toArray())if(at.getOpcode()==Opcodes.RETURN){InsnList hook=new InsnList();hook.add(new VarInsnNode(Opcodes.ALOAD,0));call(hook,name,"(Ljava/lang/Object;)V");method.instructions.insertBefore(at,hook);}}
    private static void afterObject(MethodNode method,String name){for(AbstractInsnNode at:method.instructions.toArray())if(at.getOpcode()==Opcodes.ARETURN){InsnList hook=new InsnList();hook.add(new InsnNode(Opcodes.DUP));hook.add(new VarInsnNode(Opcodes.ALOAD,0));hook.add(new InsnNode(Opcodes.SWAP));call(hook,name,"(Ljava/lang/Object;Ljava/lang/Object;)V");method.instructions.insertBefore(at,hook);}}
    private static void vertices(MethodNode method,int bulk){int token=method.maxLocals++;InsnList entry=new InsnList();entry.add(new VarInsnNode(Opcodes.ALOAD,0));if(bulk<0)entry.add(new InsnNode(Opcodes.ACONST_NULL));else entry.add(new VarInsnNode(Opcodes.ALOAD,bulk));call(entry,"verticesEntering","(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;");entry.add(new VarInsnNode(Opcodes.ASTORE,token));method.instructions.insert(entry);
        for(AbstractInsnNode at:method.instructions.toArray())if(at.getOpcode()==Opcodes.RETURN){InsnList exit=new InsnList();exit.add(new VarInsnNode(Opcodes.ALOAD,0));exit.add(new VarInsnNode(Opcodes.ALOAD,token));call(exit,"verticesCompleted","(Ljava/lang/Object;Ljava/lang/Object;)V");method.instructions.insertBefore(at,exit);}}
    private static void upload(MethodNode method,int rendered){int token=method.maxLocals++;InsnList entry=new InsnList();entry.add(new VarInsnNode(Opcodes.ALOAD,0));entry.add(new VarInsnNode(Opcodes.ALOAD,rendered));call(entry,"verticesUploading","(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;");entry.add(new VarInsnNode(Opcodes.ASTORE,token));method.instructions.insert(entry);
        for(AbstractInsnNode at:method.instructions.toArray())if(at.getOpcode()==Opcodes.RETURN){InsnList exit=new InsnList();exit.add(new VarInsnNode(Opcodes.ALOAD,0));exit.add(new VarInsnNode(Opcodes.ALOAD,token));call(exit,"verticesUploaded","(Ljava/lang/Object;Ljava/lang/Object;)V");method.instructions.insertBefore(at,exit);}}
    private static void draw(MethodNode method){int token=method.maxLocals++,error=method.maxLocals++;LabelNode start=new LabelNode(),end=new LabelNode(),complete=new LabelNode(),failed=new LabelNode(),allowed=new LabelNode();
        for(AbstractInsnNode at:method.instructions.toArray())if(at.getOpcode()==Opcodes.RETURN)method.instructions.set(at,new JumpInsnNode(Opcodes.GOTO,complete));
        InsnList entry=new InsnList();entry.add(new VarInsnNode(Opcodes.ALOAD,0));call(entry,"verticesDrawing","(Ljava/lang/Object;)Ljava/lang/Object;");entry.add(new VarInsnNode(Opcodes.ASTORE,token));entry.add(new VarInsnNode(Opcodes.ALOAD,token));entry.add(new FieldInsnNode(Opcodes.GETSTATIC,"java/lang/Boolean","FALSE","Ljava/lang/Boolean;"));entry.add(new JumpInsnNode(Opcodes.IF_ACMPNE,allowed));entry.add(new InsnNode(Opcodes.RETURN));entry.add(allowed);entry.add(start);method.instructions.insert(entry);
        InsnList exit=new InsnList();exit.add(end);exit.add(complete);exit.add(new VarInsnNode(Opcodes.ALOAD,token));call(exit,"verticesDrawn","(Ljava/lang/Object;)V");exit.add(new InsnNode(Opcodes.RETURN));exit.add(failed);exit.add(new VarInsnNode(Opcodes.ASTORE,error));exit.add(new VarInsnNode(Opcodes.ALOAD,token));call(exit,"verticesDrawn","(Ljava/lang/Object;)V");exit.add(new VarInsnNode(Opcodes.ALOAD,error));exit.add(new InsnNode(Opcodes.ATHROW));method.instructions.add(exit);method.tryCatchBlocks.add(new TryCatchBlockNode(start,end,failed,null));
    }
    private static void imageClose(MethodNode method){
        int token=method.maxLocals++,error=method.maxLocals++;LabelNode start=new LabelNode(),end=new LabelNode(),complete=new LabelNode(),failed=new LabelNode();
        for(AbstractInsnNode at:method.instructions.toArray())if(at.getOpcode()==Opcodes.RETURN)method.instructions.set(at,new JumpInsnNode(Opcodes.GOTO,complete));
        InsnList entry=new InsnList();entry.add(new VarInsnNode(Opcodes.ALOAD,0));call(entry,"imageCloseEnter","(Ljava/lang/Object;)Ljava/lang/Object;");entry.add(new VarInsnNode(Opcodes.ASTORE,token));entry.add(start);method.instructions.insert(entry);
        InsnList exit=new InsnList();exit.add(end);exit.add(complete);exit.add(new VarInsnNode(Opcodes.ALOAD,0));exit.add(new VarInsnNode(Opcodes.ALOAD,token));exit.add(new InsnNode(Opcodes.ICONST_1));call(exit,"imageCloseExit","(Ljava/lang/Object;Ljava/lang/Object;Z)V");exit.add(new InsnNode(Opcodes.RETURN));
        exit.add(failed);exit.add(new VarInsnNode(Opcodes.ASTORE,error));exit.add(new VarInsnNode(Opcodes.ALOAD,0));exit.add(new VarInsnNode(Opcodes.ALOAD,token));exit.add(new InsnNode(Opcodes.ICONST_0));call(exit,"imageCloseExit","(Ljava/lang/Object;Ljava/lang/Object;Z)V");exit.add(new VarInsnNode(Opcodes.ALOAD,error));exit.add(new InsnNode(Opcodes.ATHROW));
        method.instructions.add(exit);method.tryCatchBlocks.add(new TryCatchBlockNode(start,end,failed,null));
    }
    private static void guard(MethodNode method,int object,String operation){InsnList code=new InsnList();LabelNode allowed=new LabelNode();code.add(new VarInsnNode(Opcodes.ALOAD,object));call(code,operation,"(Ljava/lang/Object;)Z");code.add(new JumpInsnNode(Opcodes.IFNE,allowed));defaultValue(code,Type.getReturnType(method.desc));code.add(new InsnNode(Type.getReturnType(method.desc).getOpcode(Opcodes.IRETURN)));code.add(allowed);method.instructions.insert(code);}
    private static void defaultValue(InsnList code,Type result){switch(result.getSort()){
        case Type.VOID->{}case Type.LONG->code.add(new InsnNode(Opcodes.LCONST_0));case Type.FLOAT->code.add(new InsnNode(Opcodes.FCONST_0));case Type.DOUBLE->code.add(new InsnNode(Opcodes.DCONST_0));case Type.OBJECT,Type.ARRAY->code.add(new InsnNode(Opcodes.ACONST_NULL));default->code.add(new InsnNode(Opcodes.ICONST_0));}}
    private static void callback(MethodNode method,MethodInsnNode invoke){
        Type[] args=Type.getArgumentTypes(invoke.desc);Type result=Type.getReturnType(invoke.desc);int[] vars=new int[args.length];for(int i=0;i<args.length;i++){vars[i]=method.maxLocals;method.maxLocals+=args[i].getSize();}
        int receiver=method.maxLocals++,token=method.maxLocals++,error=method.maxLocals++,output=method.maxLocals;method.maxLocals+=result.getSize();
        LabelNode allowed=new LabelNode(),start=new LabelNode(),end=new LabelNode(),failed=new LabelNode(),done=new LabelNode();InsnList code=new InsnList();
        for(int i=args.length-1;i>=0;i--)code.add(new VarInsnNode(args[i].getOpcode(Opcodes.ISTORE),vars[i]));code.add(new VarInsnNode(Opcodes.ASTORE,receiver));
        code.add(new VarInsnNode(Opcodes.ALOAD,receiver));call(code,"allowed","(Ljava/lang/Object;)Z");code.add(new JumpInsnNode(Opcodes.IFNE,allowed));
        boolean reload=invoke.owner.equals("net/minecraft/server/packs/resources/PreparableReloadListener")&&invoke.name.equals("reload");
        if(reload){code.add(new VarInsnNode(Opcodes.ALOAD,vars[0]));call(code,"skipReload","(Ljava/lang/Object;)Ljava/util/concurrent/CompletableFuture;");}
        else if(invoke.owner.equals("net/minecraft/client/gui/components/toasts/Toast")&&invoke.name.equals("render"))code.add(new FieldInsnNode(Opcodes.GETSTATIC,"net/minecraft/client/gui/components/toasts/Toast$Visibility","HIDE",result.getDescriptor()));else defaultValue(code,result);
        code.add(new JumpInsnNode(Opcodes.GOTO,done));
        code.add(allowed);code.add(new VarInsnNode(Opcodes.ALOAD,receiver));call(code,"enter","(Ljava/lang/Object;)Ljava/lang/Object;");code.add(new VarInsnNode(Opcodes.ASTORE,token));code.add(start);
        code.add(new VarInsnNode(Opcodes.ALOAD,receiver));for(int i=0;i<args.length;i++)code.add(new VarInsnNode(args[i].getOpcode(Opcodes.ILOAD),vars[i]));
        code.add(new MethodInsnNode(invoke.getOpcode(),invoke.owner,invoke.name,invoke.desc,invoke.itf));if(result.getSort()!=Type.VOID)code.add(new VarInsnNode(result.getOpcode(Opcodes.ISTORE),output));
        if(reload){code.add(new VarInsnNode(Opcodes.ALOAD,receiver));code.add(new VarInsnNode(Opcodes.ALOAD,output));call(code,"reloadReturned","(Ljava/lang/Object;Ljava/lang/Object;)V");}code.add(end);
        code.add(new VarInsnNode(Opcodes.ALOAD,token));call(code,"exit","(Ljava/lang/Object;)V");if(result.getSort()!=Type.VOID)code.add(new VarInsnNode(result.getOpcode(Opcodes.ILOAD),output));code.add(new JumpInsnNode(Opcodes.GOTO,done));
        code.add(failed);code.add(new VarInsnNode(Opcodes.ASTORE,error));code.add(new VarInsnNode(Opcodes.ALOAD,token));call(code,"exit","(Ljava/lang/Object;)V");code.add(new VarInsnNode(Opcodes.ALOAD,error));code.add(new InsnNode(Opcodes.ATHROW));code.add(done);
        method.instructions.insertBefore(invoke,code);method.instructions.remove(invoke);method.tryCatchBlocks.add(0,new TryCatchBlockNode(start,end,failed,null));
    }
}
