package dev.ronova.pro.agent;

import java.util.*;
import jdk.internal.org.objectweb.asm.*;
import jdk.internal.org.objectweb.asm.tree.*;

/** Gates individual client callbacks while keeping the enclosing framework cleanup and other shares. */
final class ClientBoundary {
    private static final String BRIDGE="dev/ronova/pro/bootstrap/ClientBridge";
    private static final String PARTICLES="net/minecraft/client/particle/ParticleEngine",TEXTURES="net/minecraft/client/renderer/texture/TextureManager";
    private static final String DYNAMIC="net/minecraft/client/renderer/texture/DynamicTexture",IMAGE="com/mojang/blaze3d/platform/NativeImage";
    private static final String TOAST_COMPONENT="net/minecraft/client/gui/components/toasts/ToastComponent",TOAST_INSTANCE=TOAST_COMPONENT+"$ToastInstance";
    private static final String TOAST="Lnet/minecraft/client/gui/components/toasts/Toast;";
    private static final String SCREEN="net/minecraft/client/gui/screens/Screen",WIDGET="Lnet/minecraft/client/gui/components/events/GuiEventListener;",RENDERABLE="Lnet/minecraft/client/gui/components/Renderable;";
    private static final String NARRATABLE="net/minecraft/client/gui/narration/NarratableEntry",NARRATION="(Lnet/minecraft/client/gui/narration/NarrationElementOutput;)V";
    private static final String OVERLAY_MANAGER="net/minecraftforge/client/gui/overlay/GuiOverlayManager",OVERLAY_EVENT="net/minecraftforge/client/event/RegisterGuiOverlaysEvent";
    private static final String OVERLAY_REGISTER="(Lnet/minecraftforge/client/event/RegisterGuiOverlaysEvent$Ordering;Lnet/minecraft/resources/ResourceLocation;Ljava/lang/String;Lnet/minecraftforge/client/gui/overlay/IGuiOverlay;)V";
    private static final String OVERLAY_RENDER="(Lnet/minecraft/client/gui/GuiGraphics;FLnet/minecraftforge/client/gui/overlay/NamedGuiOverlay;)V";
    private static final String BUILDER="com/mojang/blaze3d/vertex/BufferBuilder",VERTEX_BUFFER="com/mojang/blaze3d/vertex/VertexBuffer";
    private static final String RENDER_SYSTEM="com/mojang/blaze3d/systems/RenderSystem",RENDER_CALL="com/mojang/blaze3d/pipeline/RenderCall";
    private static final String RENDERED="Lcom/mojang/blaze3d/vertex/BufferBuilder$RenderedBuffer;",SORTING="Lcom/mojang/blaze3d/vertex/BufferBuilder$SortState;";
    private static final String BEGIN="(Lcom/mojang/blaze3d/vertex/VertexFormat$Mode;Lcom/mojang/blaze3d/vertex/VertexFormat;)V";
    private record ClientCallback(String named,String installed,String descriptor){
        boolean matches(MethodInsnNode invoke){return (invoke.name.equals(named)||invoke.name.equals(installed))&&invoke.desc.equals(descriptor);}
    }
    private static final List<ClientCallback> INPUT_CALLBACKS=List.of(
            new ClientCallback("keyPressed","m_7933_","(III)Z"),new ClientCallback("keyReleased","m_7920_","(III)Z"),
            new ClientCallback("charTyped","m_5534_","(CI)Z"),new ClientCallback("mouseClicked","m_6375_","(DDI)Z"),
            new ClientCallback("mouseReleased","m_6348_","(DDI)Z"),new ClientCallback("mouseDragged","m_7979_","(DDIDD)Z"),
            new ClientCallback("mouseScrolled","m_6050_","(DDD)Z"),new ClientCallback("mouseMoved","m_94757_","(DD)V"),
            new ClientCallback("isMouseOver","m_5953_","(DD)Z"),new ClientCallback("setFocused","m_93692_","(Z)V"),
            new ClientCallback("nextFocusPath","m_264064_","(Lnet/minecraft/client/gui/navigation/FocusNavigationEvent;)Lnet/minecraft/client/gui/ComponentPath;"));
    static final Set<String> TARGETS=Set.of(
            "net/minecraft/client/Minecraft","net/minecraft/client/KeyboardHandler","net/minecraft/client/MouseHandler",
            "net/minecraft/client/renderer/GameRenderer","net/minecraft/client/renderer/entity/EntityRenderDispatcher",
            "net/minecraft/client/renderer/blockentity/BlockEntityRenderDispatcher",PARTICLES,TEXTURES,
            "net/minecraft/client/particle/Particle","net/minecraft/client/renderer/texture/AbstractTexture",
            DYNAMIC,IMAGE,"net/minecraft/client/renderer/texture/SimpleTexture","net/minecraft/client/renderer/texture/HttpTexture",
            "net/minecraft/client/gui/screens/Screen","net/minecraft/client/gui/components/events/AbstractContainerEventHandler","net/minecraft/client/gui/components/events/ContainerEventHandler",
            "net/minecraftforge/client/gui/overlay/ForgeGui",OVERLAY_MANAGER,OVERLAY_EVENT,"net/minecraftforge/client/gui/overlay/NamedGuiOverlay",
            "net/minecraft/client/gui/components/toasts/ToastComponent","net/minecraft/client/gui/components/toasts/ToastComponent$ToastInstance",
            "net/minecraft/client/sounds/SoundEngine","com/mojang/blaze3d/systems/RenderSystem",
            "net/minecraft/client/sounds/ChannelAccess","net/minecraft/client/sounds/ChannelAccess$ChannelHandle","com/mojang/blaze3d/audio/Channel","com/mojang/blaze3d/audio/Library",
            "net/minecraft/server/packs/resources/ReloadableResourceManager","net/minecraft/server/packs/resources/SimpleReloadInstance",
            "net/minecraft/server/packs/resources/ProfiledReloadInstance","net/minecraft/server/packs/resources/SimpleReloadInstance$1",
            "net/minecraft/server/packs/resources/PreparableReloadListener$PreparationBarrier",
            "com/mojang/blaze3d/vertex/BufferBuilder","com/mojang/blaze3d/vertex/BufferBuilder$RenderedBuffer","com/mojang/blaze3d/vertex/BufferBuilder$DrawState",
            "com/mojang/blaze3d/vertex/BufferBuilder$SortState","com/mojang/blaze3d/vertex/VertexBuffer","com/mojang/blaze3d/vertex/VertexFormat$Mode",
            "com/mojang/blaze3d/vertex/VertexFormat$IndexType","com/mojang/blaze3d/platform/GlStateManager",
            "com/mojang/blaze3d/vertex/VertexFormat",
            "com/mojang/blaze3d/vertex/BufferUploader");
    private static final Map<String,List<ClientCallback>> CALLBACKS=Map.ofEntries(
            Map.entry("net/minecraft/client/renderer/entity/EntityRenderer",List.of(new ClientCallback("render","m_7392_","(Lnet/minecraft/world/entity/Entity;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V"))),
            Map.entry("net/minecraft/client/renderer/blockentity/BlockEntityRenderer",List.of(new ClientCallback("render","m_6922_","(Lnet/minecraft/world/level/block/entity/BlockEntity;FLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;II)V"))),
            Map.entry("net/minecraft/client/particle/Particle",List.of(new ClientCallback("render","m_5744_","(Lcom/mojang/blaze3d/vertex/VertexConsumer;Lnet/minecraft/client/Camera;F)V"),new ClientCallback("tick","m_5989_","()V"))),
            Map.entry("net/minecraft/client/particle/TrackingEmitter",List.of(new ClientCallback("tick","m_5989_","()V"))),
            Map.entry("net/minecraft/client/particle/ParticleProvider",List.of(new ClientCallback("createParticle","m_6966_","(Lnet/minecraft/core/particles/ParticleOptions;Lnet/minecraft/client/multiplayer/ClientLevel;DDDDDD)Lnet/minecraft/client/particle/Particle;"))),
            Map.entry("net/minecraft/client/particle/ParticleProvider$Sprite",List.of(new ClientCallback("createParticle","m_272232_","(Lnet/minecraft/core/particles/ParticleOptions;Lnet/minecraft/client/multiplayer/ClientLevel;DDDDDD)Lnet/minecraft/client/particle/TextureSheetParticle;"))),
            Map.entry("net/minecraft/client/renderer/texture/Tickable",List.of(new ClientCallback("tick","m_7673_","()V"))),
            Map.entry("net/minecraftforge/client/gui/overlay/IGuiOverlay",List.of(new ClientCallback("render","render","(Lnet/minecraftforge/client/gui/overlay/ForgeGui;Lnet/minecraft/client/gui/GuiGraphics;FII)V"))),
            Map.entry("net/minecraft/client/gui/screens/Screen",screenCallbacks()),
            Map.entry("net/minecraft/client/gui/screens/Overlay",List.of(new ClientCallback("render","m_88315_","(Lnet/minecraft/client/gui/GuiGraphics;IIF)V"))),
            Map.entry("net/minecraft/client/gui/components/Renderable",List.of(new ClientCallback("render","m_88315_","(Lnet/minecraft/client/gui/GuiGraphics;IIF)V"))),
            Map.entry("net/minecraft/client/gui/components/events/GuiEventListener",INPUT_CALLBACKS),
            Map.entry(NARRATABLE,List.of(new ClientCallback("updateNarration","m_142291_",NARRATION),new ClientCallback("narrationPriority","m_142684_","()Lnet/minecraft/client/gui/narration/NarratableEntry$NarrationPriority;"))),
            Map.entry("net/minecraft/client/gui/narration/NarrationSupplier",List.of(new ClientCallback("updateNarration","m_142291_",NARRATION))),
            Map.entry("net/minecraft/client/gui/components/toasts/Toast",List.of(new ClientCallback("render","m_7172_","(Lnet/minecraft/client/gui/GuiGraphics;Lnet/minecraft/client/gui/components/toasts/ToastComponent;J)Lnet/minecraft/client/gui/components/toasts/Toast$Visibility;"))),
            Map.entry("net/minecraft/client/resources/sounds/TickableSoundInstance",List.of(new ClientCallback("tick","m_7788_","()V"))),
            Map.entry("net/minecraft/client/sounds/SoundEventListener",List.of(new ClientCallback("onPlaySound","m_6985_","(Lnet/minecraft/client/resources/sounds/SoundInstance;Lnet/minecraft/client/sounds/WeighedSoundEvents;)V"))),
            Map.entry("net/minecraft/server/packs/resources/PreparableReloadListener",List.of(new ClientCallback("reload","m_5540_","(Lnet/minecraft/server/packs/resources/PreparableReloadListener$PreparationBarrier;Lnet/minecraft/server/packs/resources/ResourceManager;Lnet/minecraft/util/profiling/ProfilerFiller;Lnet/minecraft/util/profiling/ProfilerFiller;Ljava/util/concurrent/Executor;Ljava/util/concurrent/Executor;)Ljava/util/concurrent/CompletableFuture;"),new ClientCallback("getName","m_7812_","()Ljava/lang/String;"))));
    private ClientBoundary(){}
    private static List<ClientCallback> screenCallbacks(){List<ClientCallback> callbacks=new ArrayList<>(INPUT_CALLBACKS);callbacks.add(new ClientCallback("tick","m_86600_","()V"));callbacks.add(new ClientCallback("render","m_88315_","(Lnet/minecraft/client/gui/GuiGraphics;IIF)V"));callbacks.add(new ClientCallback("renderWithTooltip","m_280264_","(Lnet/minecraft/client/gui/GuiGraphics;IIF)V"));callbacks.add(new ClientCallback("onFilesDrop","m_7400_","(Ljava/util/List;)V"));return List.copyOf(callbacks);}
    private static boolean member(MethodNode method,String named,String installed,String descriptor){return (method.name.equals(named)||method.name.equals(installed))&&method.desc.equals(descriptor);}
    private static ClientCallback clientCallback(MethodInsnNode invoke){for(ClientCallback callback:CALLBACKS.getOrDefault(invoke.owner,List.of()))if(callback.matches(invoke))return callback;return null;}
    static byte[] transform(ClassLoader loader,String name,byte[] bytes){
        if(!TARGETS.contains(name))return null;ClassNode node=new ClassNode();new ClassReader(bytes).accept(node,0);boolean changed=false;
        for(MethodNode method:node.methods){
            if((method.access&(Opcodes.ACC_ABSTRACT|Opcodes.ACC_NATIVE))!=0)continue;
            if((name.equals(DYNAMIC)||name.equals(IMAGE))&&(method.name.equals("<init>")||name.equals(DYNAMIC)&&Set.of("setPixels","m_117988_").contains(method.name)&&method.desc.equals("(Lcom/mojang/blaze3d/platform/NativeImage;)V"))){
                for(AbstractInsnNode at:method.instructions.toArray())if(at.getOpcode()==Opcodes.RETURN){InsnList hook=new InsnList();hook.add(new VarInsnNode(Opcodes.ALOAD,0));call(hook,"resourceConstructed","(Ljava/lang/Object;)V");method.instructions.insertBefore(at,hook);}changed=true;
            }
            if(name.equals(DYNAMIC)&&Set.of("getPixels","m_117991_").contains(method.name)&&method.desc.equals("()Lcom/mojang/blaze3d/platform/NativeImage;")){
                for(AbstractInsnNode at:method.instructions.toArray())if(at.getOpcode()==Opcodes.ARETURN){InsnList hook=new InsnList();hook.add(new InsnNode(Opcodes.DUP));hook.add(new VarInsnNode(Opcodes.ALOAD,0));hook.add(new InsnNode(Opcodes.SWAP));call(hook,"pixelsExposed","(Ljava/lang/Object;Ljava/lang/Object;)V");method.instructions.insertBefore(at,hook);}changed=true;
            }
            if(name.equals(IMAGE)&&method.name.equals("close")&&method.desc.equals("()V")){imageClose(method);changed=true;}
            if(name.equals(SCREEN)&&method.name.equals("<init>")&&method.desc.equals("(Lnet/minecraft/network/chat/Component;)V")){afterVoid(method,"screenConstructed");changed=true;}
            if(name.equals(OVERLAY_EVENT)&&method.name.equals("<init>")&&method.desc.equals("(Ljava/util/Map;Ljava/util/List;)V")){
                for(AbstractInsnNode at:method.instructions.toArray())if(at.getOpcode()==Opcodes.RETURN){InsnList hook=new InsnList();hook.add(new VarInsnNode(Opcodes.ALOAD,0));hook.add(new VarInsnNode(Opcodes.ALOAD,1));hook.add(new VarInsnNode(Opcodes.ALOAD,2));call(hook,"overlayEventConstructed","(Ljava/lang/Object;Ljava/util/Map;Ljava/util/List;)V");method.instructions.insertBefore(at,hook);}changed=true;
            }
            if(method.name.startsWith("<"))continue;
            if(name.equals(OVERLAY_EVENT)&&method.name.equals("register")&&method.desc.equals(OVERLAY_REGISTER)){overlayRegister(node,method);changed=true;}
            if(name.equals(OVERLAY_MANAGER)&&method.name.equals("init")&&method.desc.equals("()V")){overlayInit(node,method);changed=true;}
            if(name.equals("net/minecraftforge/client/gui/overlay/ForgeGui")&&method.name.equals("lambda$render$0")&&method.desc.equals(OVERLAY_RENDER)){overlayRender(method);changed=true;}
            Type[] args=Type.getArgumentTypes(method.desc);int[] vars=locals(method,args);
            if(name.equals(SCREEN)){
                if(member(method,"addRenderableWidget","m_142416_","("+WIDGET+")"+WIDGET)||member(method,"addWidget","m_7787_","("+WIDGET+")"+WIDGET)
                        ||member(method,"addRenderableOnly","m_169394_","("+RENDERABLE+")"+RENDERABLE)||member(method,"addEventWidget","addEventWidget","("+WIDGET+")V")){
                    screenPublication(method,vars[0]);changed=true;
                }
                if(member(method,"removeWidget","m_169411_","("+WIDGET+")V")||member(method,"clearWidgets","m_169413_","()V")){afterVoid(method,"screenWidgetsRemoved");changed=true;}
                if(member(method,"render","m_88315_","(Lnet/minecraft/client/gui/GuiGraphics;IIF)V")||member(method,"renderWithTooltip","m_280264_","(Lnet/minecraft/client/gui/GuiGraphics;IIF)V")
                        ||member(method,"keyPressed","m_7933_","(III)Z")||member(method,"setInitialFocus","m_264313_","("+WIDGET+")V")||member(method,"updateNarratedWidget","m_142227_",NARRATION)){
                    screenExecution(method);changed=true;
                }
            }
            if(name.equals("net/minecraft/client/gui/components/events/AbstractContainerEventHandler")&&member(method,"setFocused","m_7522_","("+WIDGET+")V")){
                screenExecution(method,"containerEntering");changed=true;
            }
            if(name.equals(TOAST_COMPONENT)){
                if(member(method,"addToast","m_94922_","("+TOAST+")V")){toastLifecycle(method,"toastAdding",vars[0],true);changed=true;}
                if(member(method,"render","m_94920_","(Lnet/minecraft/client/gui/GuiGraphics;)V")){toastLifecycle(method,"toastFrameEntering",-1,false);changed=true;}
                if(member(method,"lambda$render$1","m_243085_","("+TOAST+")Z")){toastLifecycle(method,"toastQueued",vars[0],false);changed=true;}
                if(member(method,"clear","m_94919_","()V")){afterVoid(method,"toastCleared");changed=true;}
            }
            if(name.equals(TOAST_INSTANCE)&&member(method,"render","m_280442_","(ILnet/minecraft/client/gui/GuiGraphics;)Z")){toastLifecycle(method,"toastDrawing",-1,false);changed=true;}
            if(name.equals(BUILDER)){
                if(member(method,"begin","m_166779_",BEGIN)){afterVoid(method,"verticesBegin");changed=true;}
                if(member(method,"ensureCapacity","m_85722_","(I)V")){capacityWrites(node,method);changed=true;}
                if(member(method,"setQuadSorting","m_277127_","(Lcom/mojang/blaze3d/vertex/VertexSorting;)V")){afterVoid(method,"verticesSortingChanged");changed=true;}
                if(member(method,"endVertex","m_5752_","()V")||member(method,"putBulkData","putBulkData","(Ljava/nio/ByteBuffer;)V")){vertices(node,method,args.length==1?vars[0]:-1);changed=true;}
                if(member(method,"storeRenderedBuffer","m_231177_","()"+RENDERED)){afterObject(method,"verticesStored");changed=true;}
                if(member(method,"discard","m_85730_","()V")){afterVoid(method,"verticesDiscarded");changed=true;}
                if(member(method,"getSortState","m_166770_","()"+SORTING)){afterObject(method,"verticesSortState");changed=true;}
                if(member(method,"restoreSortState","m_166775_","("+SORTING+")V")){for(AbstractInsnNode at:method.instructions.toArray())if(at.getOpcode()==Opcodes.RETURN){InsnList hook=new InsnList();hook.add(new VarInsnNode(Opcodes.ALOAD,0));hook.add(new VarInsnNode(Opcodes.ALOAD,vars[0]));call(hook,"verticesSortRestored","(Ljava/lang/Object;Ljava/lang/Object;)V");method.instructions.insertBefore(at,hook);}sortRestore(method,vars[0]);changed=true;}
            }
            if(name.equals(BUILDER+"$RenderedBuffer")&&member(method,"release","m_231200_","()V")){afterVoid(method,"verticesReleased");changed=true;}
            if(name.equals(VERTEX_BUFFER)){
                if(member(method,"upload","m_231221_","("+RENDERED+")V")){upload(method,vars[0]);changed=true;}
                if(member(method,"draw","m_166882_","()V")){draw(method);changed=true;}
                if(member(method,"close","close","()V")){afterVoid(method,"verticesClosed");changed=true;}
            }
            if(name.equals("com/mojang/blaze3d/platform/GlStateManager")){
                if(method.name.equals("glShaderSource")&&method.desc.equals("(ILjava/util/List;)V")){InsnList hook=new InsnList();hook.add(new VarInsnNode(Opcodes.ILOAD,vars[0]));hook.add(new VarInsnNode(Opcodes.ALOAD,vars[1]));call(hook,"shaderSource","(ILjava/util/List;)Ljava/util/List;");hook.add(new VarInsnNode(Opcodes.ASTORE,vars[1]));method.instructions.insert(hook);changed=true;}
                if(Set.of("glCompileShader","glLinkProgram","glDeleteProgram","glDeleteShader").contains(method.name)&&method.desc.equals("(I)V")){for(AbstractInsnNode at:method.instructions.toArray())if(at.getOpcode()==Opcodes.RETURN){InsnList hook=new InsnList();hook.add(new VarInsnNode(Opcodes.ILOAD,vars[0]));call(hook,method.name.equals("glCompileShader")?"shaderCompiled":method.name.equals("glLinkProgram")?"shaderLinked":method.name.equals("glDeleteProgram")?"shaderProgramClosed":"shaderClosed","(I)V");method.instructions.insertBefore(at,hook);}changed=true;}
            }
            if(name.equals("net/minecraft/server/packs/resources/ReloadableResourceManager")&&member(method,"registerReloadListener","m_7217_","(Lnet/minecraft/server/packs/resources/PreparableReloadListener;)V")){InsnList hook=new InsnList();hook.add(new VarInsnNode(Opcodes.ALOAD,0));hook.add(new VarInsnNode(Opcodes.ALOAD,vars[0]));call(hook,"reloadListenerRegistered","(Ljava/lang/Object;Ljava/lang/Object;)V");method.instructions.insert(hook);changed=true;}
            if(name.equals("net/minecraft/server/packs/resources/ReloadableResourceManager")&&member(method,"createReload","m_142463_","(Ljava/util/concurrent/Executor;Ljava/util/concurrent/Executor;Ljava/util/concurrent/CompletableFuture;Ljava/util/List;)Lnet/minecraft/server/packs/resources/ReloadInstance;")){InsnList hook=new InsnList();hook.add(new VarInsnNode(Opcodes.ALOAD,0));call(hook,"reloadStarting","(Ljava/lang/Object;)V");method.instructions.insert(hook);changed=true;}
            if(name.equals(PARTICLES)&&member(method,"add","m_107344_","(Lnet/minecraft/client/particle/Particle;)V")){guard(method,vars[0],"particleAdmitted");changed=true;}
            if(name.equals(PARTICLES)&&member(method,"tickParticle","m_107393_","(Lnet/minecraft/client/particle/Particle;)V")){guard(method,vars[0],"particleTick");changed=true;}
            if(name.equals(TEXTURES)&&member(method,"register","m_118495_","(Lnet/minecraft/resources/ResourceLocation;Lnet/minecraft/client/renderer/texture/AbstractTexture;)V")){
                int original=method.maxLocals++;InsnList retain=new InsnList();retain.add(new VarInsnNode(Opcodes.ALOAD,vars[1]));retain.add(new VarInsnNode(Opcodes.ASTORE,original));method.instructions.insert(retain);
                for(AbstractInsnNode at:method.instructions.toArray())if(at.getOpcode()==Opcodes.RETURN){InsnList hook=new InsnList();hook.add(new VarInsnNode(Opcodes.ALOAD,0));hook.add(new VarInsnNode(Opcodes.ALOAD,vars[0]));hook.add(new VarInsnNode(Opcodes.ALOAD,original));call(hook,"textureRegistered","(Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;)V");method.instructions.insertBefore(at,hook);}changed=true;
            }
            if(name.equals(TEXTURES)&&member(method,"tick","m_7673_","()V")){InsnList hook=new InsnList();hook.add(new VarInsnNode(Opcodes.ALOAD,0));call(hook,"textureTick","(Ljava/lang/Object;)V");method.instructions.insert(hook);changed=true;}
            if(name.equals(TEXTURES)&&member(method,"close","close","()V"))changed|=textureClose(node,method);
            if(name.equals("net/minecraft/client/Minecraft")&&args.length==1&&Type.getReturnType(method.desc).getSort()==Type.VOID
                    &&(Set.of("setScreen","m_91152_").contains(method.name)&&args[0].getDescriptor().equals("Lnet/minecraft/client/gui/screens/Screen;")
                    ||Set.of("setOverlay","m_91150_").contains(method.name)&&args[0].getDescriptor().equals("Lnet/minecraft/client/gui/screens/Overlay;"))){
                presentation(method,vars[0],args[0].getDescriptor().endsWith("/Screen;"));changed=true;
            }
            if(name.equals("net/minecraft/client/Minecraft")&&member(method,"runTick","m_91383_","(Z)V")){
                InsnList hook=new InsnList();call(hook,"gpuMaintenance","()V");hook.add(new VarInsnNode(Opcodes.ALOAD,0));call(hook,"presentationMaintenance","(Ljava/lang/Object;)V");method.instructions.insert(hook);changed=true;
            }
            if(name.equals("net/minecraft/client/Minecraft")&&member(method,"close","close","()V"))for(AbstractInsnNode at:method.instructions.toArray())if(at instanceof MethodInsnNode invoke&&invoke.owner.equals("com/mojang/blaze3d/platform/Window")&&invoke.name.equals("close")&&invoke.desc.equals("()V")){InsnList hook=new InsnList();call(hook,"gpuContextClosing","()V");method.instructions.insertBefore(at,hook);changed=true;}
            if(name.equals("net/minecraft/client/sounds/SoundEngine")){
                if(member(method,"play","m_120312_","(Lnet/minecraft/client/resources/sounds/SoundInstance;)V")||member(method,"playDelayed","m_120276_","(Lnet/minecraft/client/resources/sounds/SoundInstance;I)V")||member(method,"queueTickingSound","m_120282_","(Lnet/minecraft/client/resources/sounds/TickableSoundInstance;)V")){guard(method,vars[0],"soundAdmitted");changed=true;}
                if(member(method,"tick","m_120302_","(Z)V")){InsnList hook=new InsnList();hook.add(new VarInsnNode(Opcodes.ALOAD,0));call(hook,"soundTick","(Ljava/lang/Object;)V");method.instructions.insert(hook);changed=true;}
                if(member(method,"addEventListener","m_120295_","(Lnet/minecraft/client/sounds/SoundEventListener;)V")){for(AbstractInsnNode at:method.instructions.toArray())if(at.getOpcode()==Opcodes.RETURN){InsnList hook=new InsnList();hook.add(new VarInsnNode(Opcodes.ALOAD,0));hook.add(new VarInsnNode(Opcodes.ALOAD,vars[0]));call(hook,"soundListenerRegistered","(Ljava/lang/Object;Ljava/lang/Object;)V");method.instructions.insertBefore(at,hook);}changed=true;}
            }
            if(name.equals("net/minecraft/client/sounds/ChannelAccess$ChannelHandle")&&member(method,"release","m_120156_","()V")){soundRelease(method);changed=true;}
            if(name.equals("com/mojang/blaze3d/audio/Channel")&&member(method,"destroy","m_83665_","()V")){afterVoid(method,"soundChannelDestroyed");changed=true;}
            if(name.equals(RENDER_SYSTEM)&&member(method,"recordRenderCall","recordRenderCall","(L"+RENDER_CALL+";)V")){
                renderPublication(node,method,vars[0]);changed=true;
            }
            if(name.equals(RENDER_SYSTEM)&&member(method,"replayQueue","replayQueue","()V")){
                renderReplay(node,method);changed=true;
            }
            if(name.equals("com/mojang/blaze3d/vertex/BufferUploader")&&(member(method,"drawWithShader","m_231202_","("+RENDERED+")V")||member(method,"draw","m_231209_","("+RENDERED+")V"))
                    ||name.equals(RENDER_SYSTEM)&&member(method,"drawElements","drawElements","(III)V")){
                InsnList gate=new InsnList();LabelNode allowed=new LabelNode();gate.add(new InsnNode(Opcodes.ACONST_NULL));call(gate,"allowed","(Ljava/lang/Object;)Z");gate.add(new JumpInsnNode(Opcodes.IFNE,allowed));gate.add(new InsnNode(Opcodes.RETURN));gate.add(allowed);method.instructions.insert(gate);changed=true;
            }
            for(AbstractInsnNode at:method.instructions.toArray())if(at instanceof MethodInsnNode invoke&&invoke.getOpcode()!=Opcodes.INVOKESTATIC&&invoke.getOpcode()!=Opcodes.INVOKESPECIAL){
                ClientCallback callback=clientCallback(invoke);if(callback!=null){callback(method,invoke,callback.named(),name);changed=true;}
            }
        }
        if(!changed)return null;ControlClassWriter writer=new ControlClassWriter(loader,node);node.accept(writer);return writer.toByteArray();
    }
    private static boolean textureClose(ClassNode node,MethodNode close){
        MethodInsnNode missing=null;
        for(MethodNode method:node.methods)if(member(method,"safeClose","m_118508_","(Lnet/minecraft/resources/ResourceLocation;Lnet/minecraft/client/renderer/texture/AbstractTexture;)V"))
            for(AbstractInsnNode at:method.instructions.toArray())if(at instanceof MethodInsnNode invoke
                    &&invoke.getOpcode()==Opcodes.INVOKESTATIC&&invoke.owner.equals("net/minecraft/client/renderer/texture/MissingTextureAtlasSprite")
                    &&(invoke.name.equals("getTexture")||invoke.name.equals("m_118080_"))&&invoke.desc.equals("()L"+DYNAMIC+";"))missing=invoke;
        if(missing==null)return false;
        for(AbstractInsnNode at:close.instructions.toArray())if(at instanceof MethodInsnNode invoke
                &&invoke.getOpcode()==Opcodes.INVOKEINTERFACE&&invoke.owner.equals("java/util/Map")
                &&invoke.name.equals("forEach")&&invoke.desc.equals("(Ljava/util/function/BiConsumer;)V")){
            // safeClose lazily obtains the missing texture, whose constructor
            // path registers it in this same manager. Resolve that side effect
            // before forEach takes its modCount snapshot, not in its callback.
            // Keep the original map/consumer and all per-texture cleanup. An
            // empty traversal must not create a texture just to close nothing.
            InsnList prepare=new InsnList();LabelNode ready=new LabelNode();
            prepare.add(new InsnNode(Opcodes.DUP2));prepare.add(new InsnNode(Opcodes.POP));
            prepare.add(new MethodInsnNode(Opcodes.INVOKEINTERFACE,"java/util/Map","isEmpty","()Z",true));
            prepare.add(new JumpInsnNode(Opcodes.IFNE,ready));
            prepare.add(new MethodInsnNode(missing.getOpcode(),missing.owner,missing.name,missing.desc,missing.itf));
            prepare.add(new InsnNode(Opcodes.POP));prepare.add(ready);close.instructions.insertBefore(invoke,prepare);return true;
        }
        return false;
    }
    private static boolean renderQueueField(AbstractInsnNode at,String owner){
        return at instanceof FieldInsnNode field&&field.getOpcode()==Opcodes.GETSTATIC&&field.owner.equals(owner)
                &&field.name.equals("recordingQueue")&&field.desc.equals("Ljava/util/concurrent/ConcurrentLinkedQueue;");
    }
    private static void renderPublication(ClassNode node,MethodNode method,int incoming){
        List<AbstractInsnNode> code=new ArrayList<>();for(AbstractInsnNode at:method.instructions.toArray())if(at.getOpcode()>=0)code.add(at);
        MethodInsnNode addition=null;
        for(int index=0;index<code.size();index++)if(code.get(index) instanceof MethodInsnNode invoke
                &&invoke.owner.equals("java/util/concurrent/ConcurrentLinkedQueue")&&invoke.name.equals("add")&&invoke.desc.equals("(Ljava/lang/Object;)Z")){
            if(addition!=null||index<2||invoke.getOpcode()!=Opcodes.INVOKEVIRTUAL||!renderQueueField(code.get(index-2),node.name)
                    ||!(code.get(index-1) instanceof VarInsnNode value)||value.getOpcode()!=Opcodes.ALOAD||value.var!=incoming)
                throw new IllegalStateException("CLIENT_RENDER_QUEUE_PUBLICATION_LAYOUT");
            addition=invoke;
        }
        if(addition==null||(method.access&Opcodes.ACC_STATIC)==0)throw new IllegalStateException("CLIENT_RENDER_QUEUE_PUBLICATION_MISSING");
        int token=method.maxLocals++,wrapped=method.maxLocals++,error=method.maxLocals++;
        LabelNode accepted=new LabelNode(),start=new LabelNode(),end=new LabelNode(),complete=new LabelNode(),failed=new LabelNode(),write=new LabelNode(),written=new LabelNode();
        for(AbstractInsnNode at:method.instructions.toArray())if(at.getOpcode()==Opcodes.RETURN)method.instructions.set(at,new JumpInsnNode(Opcodes.GOTO,complete));
        InsnList gate=new InsnList();gate.add(new InsnNode(Opcodes.DUP2));gate.add(new VarInsnNode(Opcodes.ALOAD,token));
        call(gate,"renderCallValue","(Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;");
        gate.add(new VarInsnNode(Opcodes.ASTORE,wrapped));gate.add(new InsnNode(Opcodes.POP));
        gate.add(new VarInsnNode(Opcodes.ALOAD,wrapped));gate.add(new FieldInsnNode(Opcodes.GETSTATIC,"java/lang/Boolean","FALSE","Ljava/lang/Boolean;"));
        gate.add(new JumpInsnNode(Opcodes.IF_ACMPNE,write));gate.add(new InsnNode(Opcodes.POP));gate.add(new InsnNode(Opcodes.ICONST_0));gate.add(new JumpInsnNode(Opcodes.GOTO,written));
        gate.add(write);gate.add(new VarInsnNode(Opcodes.ALOAD,wrapped));method.instructions.insertBefore(addition,gate);method.instructions.insert(addition,written);
        InsnList entry=new InsnList();entry.add(new FieldInsnNode(Opcodes.GETSTATIC,node.name,"recordingQueue","Ljava/util/concurrent/ConcurrentLinkedQueue;"));
        entry.add(new VarInsnNode(Opcodes.ALOAD,incoming));call(entry,"renderCallEntering","(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;");entry.add(new VarInsnNode(Opcodes.ASTORE,token));
        entry.add(new VarInsnNode(Opcodes.ALOAD,token));entry.add(new FieldInsnNode(Opcodes.GETSTATIC,"java/lang/Boolean","FALSE","Ljava/lang/Boolean;"));entry.add(new JumpInsnNode(Opcodes.IF_ACMPNE,accepted));
        entry.add(new InsnNode(Opcodes.RETURN));entry.add(accepted);entry.add(start);method.instructions.insert(entry);
        InsnList tail=new InsnList();tail.add(end);tail.add(complete);tail.add(new VarInsnNode(Opcodes.ALOAD,token));call(tail,"renderCallSubmitted","(Ljava/lang/Object;)V");tail.add(new InsnNode(Opcodes.RETURN));
        tail.add(failed);tail.add(new VarInsnNode(Opcodes.ASTORE,error));tail.add(new VarInsnNode(Opcodes.ALOAD,token));call(tail,"renderCallSubmitted","(Ljava/lang/Object;)V");tail.add(new VarInsnNode(Opcodes.ALOAD,error));tail.add(new InsnNode(Opcodes.ATHROW));
        method.instructions.add(tail);method.tryCatchBlocks.add(new TryCatchBlockNode(start,end,failed,null));
    }
    private static void renderReplay(ClassNode node,MethodNode method){
        List<AbstractInsnNode> code=new ArrayList<>();for(AbstractInsnNode at:method.instructions.toArray())if(at.getOpcode()>=0)code.add(at);
        MethodInsnNode poll=null,execute=null;
        for(int index=0;index<code.size();index++)if(code.get(index) instanceof MethodInsnNode invoke
                &&invoke.owner.equals("java/util/concurrent/ConcurrentLinkedQueue")&&invoke.name.equals("poll")&&invoke.desc.equals("()Ljava/lang/Object;")){
            if(poll!=null||index<1||index+4>=code.size()||!renderQueueField(code.get(index-1),node.name)||invoke.getOpcode()!=Opcodes.INVOKEVIRTUAL
                    ||!(code.get(index+1) instanceof TypeInsnNode cast)||cast.getOpcode()!=Opcodes.CHECKCAST||!cast.desc.equals(RENDER_CALL)
                    ||!(code.get(index+2) instanceof VarInsnNode stored)||stored.getOpcode()!=Opcodes.ASTORE
                    ||!(code.get(index+3) instanceof VarInsnNode loaded)||loaded.getOpcode()!=Opcodes.ALOAD||loaded.var!=stored.var
                    ||!(code.get(index+4) instanceof MethodInsnNode call)||call.getOpcode()!=Opcodes.INVOKEINTERFACE
                    ||!call.owner.equals(RENDER_CALL)||!Set.of("execute","m_83909_").contains(call.name)||!call.desc.equals("()V"))
                throw new IllegalStateException("CLIENT_RENDER_QUEUE_CONSUMPTION_LAYOUT");
            poll=invoke;execute=call;
        }
        if(poll==null||(method.access&Opcodes.ACC_STATIC)==0)throw new IllegalStateException("CLIENT_RENDER_QUEUE_CONSUMPTION_MISSING");
        method.instructions.set(poll,new MethodInsnNode(Opcodes.INVOKESTATIC,BRIDGE,"renderCallPoll","(Ljava/lang/Object;)Ljava/lang/Object;",false));
        method.instructions.set(execute,new MethodInsnNode(Opcodes.INVOKESTATIC,BRIDGE,"renderCallExecute","(Ljava/lang/Object;)V",false));
        int token=method.maxLocals++,error=method.maxLocals++;
        LabelNode accepted=new LabelNode(),start=new LabelNode(),end=new LabelNode(),complete=new LabelNode(),failed=new LabelNode();
        for(AbstractInsnNode at:method.instructions.toArray())if(at.getOpcode()==Opcodes.RETURN)method.instructions.set(at,new JumpInsnNode(Opcodes.GOTO,complete));
        InsnList entry=new InsnList();entry.add(new FieldInsnNode(Opcodes.GETSTATIC,node.name,"recordingQueue","Ljava/util/concurrent/ConcurrentLinkedQueue;"));call(entry,"renderQueueEntering","(Ljava/lang/Object;)Ljava/lang/Object;");entry.add(new VarInsnNode(Opcodes.ASTORE,token));
        entry.add(new VarInsnNode(Opcodes.ALOAD,token));entry.add(new FieldInsnNode(Opcodes.GETSTATIC,"java/lang/Boolean","FALSE","Ljava/lang/Boolean;"));entry.add(new JumpInsnNode(Opcodes.IF_ACMPNE,accepted));
        entry.add(new InsnNode(Opcodes.RETURN));entry.add(accepted);entry.add(start);method.instructions.insert(entry);
        InsnList tail=new InsnList();tail.add(end);tail.add(complete);tail.add(new VarInsnNode(Opcodes.ALOAD,token));call(tail,"renderQueueExiting","(Ljava/lang/Object;)V");tail.add(new InsnNode(Opcodes.RETURN));
        tail.add(failed);tail.add(new VarInsnNode(Opcodes.ASTORE,error));tail.add(new VarInsnNode(Opcodes.ALOAD,token));call(tail,"renderQueueExiting","(Ljava/lang/Object;)V");tail.add(new VarInsnNode(Opcodes.ALOAD,error));tail.add(new InsnNode(Opcodes.ATHROW));
        method.instructions.add(tail);method.tryCatchBlocks.add(new TryCatchBlockNode(start,end,failed,null));
    }
    /** Capture only this invocation's actual List.add publications, including a partially throwing registration. */
    private static void screenPublication(MethodNode method,int incoming){
        Type result=Type.getReturnType(method.desc);int token=method.maxLocals++,error=method.maxLocals++,output=method.maxLocals;method.maxLocals+=result.getSize();
        LabelNode accepted=new LabelNode(),start=new LabelNode(),end=new LabelNode(),complete=new LabelNode(),failed=new LabelNode();
        for(AbstractInsnNode at:method.instructions.toArray()){
            if(at.getOpcode()==Opcodes.ARETURN){InsnList save=new InsnList();save.add(new VarInsnNode(Opcodes.ASTORE,output));save.add(new JumpInsnNode(Opcodes.GOTO,complete));method.instructions.insertBefore(at,save);method.instructions.remove(at);}
            else if(at.getOpcode()==Opcodes.RETURN)method.instructions.set(at,new JumpInsnNode(Opcodes.GOTO,complete));
            else if(at instanceof MethodInsnNode invoke&&invoke.getOpcode()==Opcodes.INVOKEINTERFACE&&invoke.owner.equals("java/util/List")&&invoke.name.equals("add")&&invoke.desc.equals("(Ljava/lang/Object;)Z")){
                LabelNode write=new LabelNode(),done=new LabelNode();InsnList gate=new InsnList();gate.add(new InsnNode(Opcodes.DUP2));gate.add(new VarInsnNode(Opcodes.ALOAD,token));
                call(gate,"screenWriting","(Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;)Z");gate.add(new JumpInsnNode(Opcodes.IFNE,write));gate.add(new InsnNode(Opcodes.POP2));gate.add(new InsnNode(Opcodes.ICONST_0));gate.add(new JumpInsnNode(Opcodes.GOTO,done));gate.add(write);
                method.instructions.insertBefore(invoke,gate);method.instructions.insert(invoke,done);
            }
        }
        InsnList entry=new InsnList();entry.add(new VarInsnNode(Opcodes.ALOAD,0));entry.add(new VarInsnNode(Opcodes.ALOAD,incoming));call(entry,"screenAdding","(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;");entry.add(new VarInsnNode(Opcodes.ASTORE,token));
        entry.add(new VarInsnNode(Opcodes.ALOAD,token));entry.add(new FieldInsnNode(Opcodes.GETSTATIC,"java/lang/Boolean","FALSE","Ljava/lang/Boolean;"));entry.add(new JumpInsnNode(Opcodes.IF_ACMPNE,accepted));
        if(result.getSort()!=Type.VOID)entry.add(new VarInsnNode(Opcodes.ALOAD,incoming));entry.add(new InsnNode(result.getOpcode(Opcodes.IRETURN)));entry.add(accepted);entry.add(start);method.instructions.insert(entry);
        InsnList tail=new InsnList();tail.add(end);tail.add(complete);tail.add(new VarInsnNode(Opcodes.ALOAD,token));call(tail,"screenAdded","(Ljava/lang/Object;)V");
        if(result.getSort()!=Type.VOID)tail.add(new VarInsnNode(Opcodes.ALOAD,output));tail.add(new InsnNode(result.getOpcode(Opcodes.IRETURN)));
        tail.add(failed);tail.add(new VarInsnNode(Opcodes.ASTORE,error));tail.add(new VarInsnNode(Opcodes.ALOAD,token));call(tail,"screenAdded","(Ljava/lang/Object;)V");tail.add(new VarInsnNode(Opcodes.ALOAD,error));tail.add(new InsnNode(Opcodes.ATHROW));
        method.instructions.add(tail);method.tryCatchBlocks.add(new TryCatchBlockNode(start,end,failed,null));
    }
    private static void screenExecution(MethodNode method){screenExecution(method,"screenEntering");}
    private static void screenExecution(MethodNode method,String entering){
        Type result=Type.getReturnType(method.desc);int token=method.maxLocals++,error=method.maxLocals++,output=method.maxLocals;method.maxLocals+=result.getSize();
        LabelNode start=new LabelNode(),end=new LabelNode(),complete=new LabelNode(),failed=new LabelNode();
        for(AbstractInsnNode at:method.instructions.toArray()){
            if(at.getOpcode()==result.getOpcode(Opcodes.IRETURN)){
                InsnList save=new InsnList();if(result.getSort()!=Type.VOID)save.add(new VarInsnNode(result.getOpcode(Opcodes.ISTORE),output));save.add(new JumpInsnNode(Opcodes.GOTO,complete));method.instructions.insertBefore(at,save);method.instructions.remove(at);
            }else if(at instanceof FieldInsnNode field&&field.getOpcode()==Opcodes.PUTFIELD
                    &&(field.owner.equals("net/minecraft/client/gui/components/events/AbstractContainerEventHandler")&&Set.of("focused","f_94673_").contains(field.name)&&field.desc.equals(WIDGET)
                    ||field.owner.equals(SCREEN)&&Set.of("lastNarratable","f_169365_").contains(field.name)&&field.desc.equals("Lnet/minecraft/client/gui/narration/NarratableEntry;"))){
                LabelNode write=new LabelNode(),done=new LabelNode();InsnList gate=new InsnList();gate.add(new InsnNode(Opcodes.DUP2));call(gate,"widgetReferenceWriting","(Ljava/lang/Object;Ljava/lang/Object;)Z");gate.add(new JumpInsnNode(Opcodes.IFNE,write));gate.add(new InsnNode(Opcodes.POP2));gate.add(new JumpInsnNode(Opcodes.GOTO,done));gate.add(write);
                int owner=method.maxLocals++,widget=method.maxLocals++;gate.add(new InsnNode(Opcodes.DUP2));gate.add(new VarInsnNode(Opcodes.ASTORE,widget));gate.add(new VarInsnNode(Opcodes.ASTORE,owner));
                InsnList published=new InsnList();published.add(new VarInsnNode(Opcodes.ALOAD,owner));published.add(new VarInsnNode(Opcodes.ALOAD,widget));call(published,"widgetReferenceWritten","(Ljava/lang/Object;Ljava/lang/Object;)V");published.add(done);
                method.instructions.insertBefore(field,gate);method.instructions.insert(field,published);
            }
        }
        InsnList entry=new InsnList();entry.add(new VarInsnNode(Opcodes.ALOAD,0));call(entry,entering,"(Ljava/lang/Object;)Ljava/lang/Object;");entry.add(new VarInsnNode(Opcodes.ASTORE,token));entry.add(start);method.instructions.insert(entry);
        InsnList tail=new InsnList();tail.add(end);tail.add(complete);tail.add(new VarInsnNode(Opcodes.ALOAD,token));call(tail,"exit","(Ljava/lang/Object;)V");if(result.getSort()!=Type.VOID)tail.add(new VarInsnNode(result.getOpcode(Opcodes.ILOAD),output));tail.add(new InsnNode(result.getOpcode(Opcodes.IRETURN)));
        tail.add(failed);tail.add(new VarInsnNode(Opcodes.ASTORE,error));tail.add(new VarInsnNode(Opcodes.ALOAD,token));call(tail,"exit","(Ljava/lang/Object;)V");tail.add(new VarInsnNode(Opcodes.ALOAD,error));tail.add(new InsnNode(Opcodes.ATHROW));
        method.instructions.add(tail);method.tryCatchBlocks.add(new TryCatchBlockNode(start,end,failed,null));
    }
    /** One scope covers the original callbacks, exact publication and every normal/throwing return. */
    private static void toastLifecycle(MethodNode method,String entering,int incoming,boolean publication){
        if((method.access&Opcodes.ACC_STATIC)!=0)throw new IllegalStateException("CLIENT_TOAST_INSTANCE_METHOD_REQUIRED");
        Type result=Type.getReturnType(method.desc);if(result.getSort()!=Type.VOID&&result.getSort()!=Type.BOOLEAN)throw new IllegalStateException("CLIENT_TOAST_RETURN_LAYOUT_CHANGED");
        int token=method.maxLocals++,failure=method.maxLocals++,output=method.maxLocals++;
        LabelNode start=new LabelNode(),end=new LabelNode(),handler=new LabelNode(),complete=new LabelNode();
        MethodInsnNode publish=null;
        for(AbstractInsnNode at:method.instructions.toArray()){
            if(at.getOpcode()==Opcodes.RETURN||at.getOpcode()==Opcodes.IRETURN){InsnList finish=new InsnList();if(result.getSort()!=Type.VOID)finish.add(new VarInsnNode(Opcodes.ISTORE,output));finish.add(new JumpInsnNode(Opcodes.GOTO,complete));method.instructions.insertBefore(at,finish);method.instructions.remove(at);}
            else if(publication&&at instanceof MethodInsnNode invoke&&invoke.getOpcode()==Opcodes.INVOKEINTERFACE&&invoke.owner.equals("java/util/Deque")&&invoke.name.equals("add")&&invoke.desc.equals("(Ljava/lang/Object;)Z")){
                if(publish!=null)throw new IllegalStateException("CLIENT_TOAST_QUEUE_PUBLICATION_CHANGED");publish=invoke;
            }
        }
        if(publication){
            if(publish==null)throw new IllegalStateException("CLIENT_TOAST_QUEUE_PUBLICATION_UNAVAILABLE");
            int queued=method.maxLocals++,toast=method.maxLocals++,accepted=method.maxLocals++;LabelNode write=new LabelNode();
            InsnList gate=new InsnList();gate.add(new InsnNode(Opcodes.DUP2));gate.add(new VarInsnNode(Opcodes.ASTORE,toast));gate.add(new VarInsnNode(Opcodes.ASTORE,queued));
            gate.add(new VarInsnNode(Opcodes.ALOAD,queued));gate.add(new VarInsnNode(Opcodes.ALOAD,toast));gate.add(new VarInsnNode(Opcodes.ALOAD,token));call(gate,"toastWriting","(Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;)Z");
            gate.add(new JumpInsnNode(Opcodes.IFNE,write));gate.add(new InsnNode(Opcodes.POP2));gate.add(new JumpInsnNode(Opcodes.GOTO,complete));gate.add(write);method.instructions.insertBefore(publish,gate);
            InsnList observed=new InsnList();observed.add(new InsnNode(Opcodes.DUP));observed.add(new VarInsnNode(Opcodes.ISTORE,accepted));observed.add(new VarInsnNode(Opcodes.ALOAD,queued));observed.add(new VarInsnNode(Opcodes.ALOAD,toast));
            observed.add(new VarInsnNode(Opcodes.ALOAD,token));observed.add(new VarInsnNode(Opcodes.ILOAD,accepted));call(observed,"toastAdded","(Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Z)V");method.instructions.insert(publish,observed);
        }
        InsnList entry=new InsnList();entry.add(new VarInsnNode(Opcodes.ALOAD,0));if(incoming>=0)entry.add(new VarInsnNode(Opcodes.ALOAD,incoming));
        call(entry,entering,incoming>=0?"(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;":"(Ljava/lang/Object;)Ljava/lang/Object;");entry.add(new VarInsnNode(Opcodes.ASTORE,token));
        entry.add(new VarInsnNode(Opcodes.ALOAD,token));entry.add(new FieldInsnNode(Opcodes.GETSTATIC,"java/lang/Boolean","FALSE","Ljava/lang/Boolean;"));entry.add(new JumpInsnNode(Opcodes.IF_ACMPNE,start));
        if(result.getSort()!=Type.VOID)entry.add(new InsnNode(Opcodes.ICONST_1));entry.add(new InsnNode(result.getSort()==Type.VOID?Opcodes.RETURN:Opcodes.IRETURN));entry.add(start);method.instructions.insert(entry);
        method.instructions.add(end);method.instructions.add(complete);method.instructions.add(new VarInsnNode(Opcodes.ALOAD,token));call(method.instructions,"toastExiting","(Ljava/lang/Object;)V");
        if(result.getSort()!=Type.VOID)method.instructions.add(new VarInsnNode(Opcodes.ILOAD,output));method.instructions.add(new InsnNode(result.getSort()==Type.VOID?Opcodes.RETURN:Opcodes.IRETURN));
        method.instructions.add(handler);method.instructions.add(new VarInsnNode(Opcodes.ASTORE,failure));method.instructions.add(new VarInsnNode(Opcodes.ALOAD,token));call(method.instructions,"toastExiting","(Ljava/lang/Object;)V");
        method.instructions.add(new VarInsnNode(Opcodes.ALOAD,failure));method.instructions.add(new InsnNode(Opcodes.ATHROW));method.tryCatchBlocks.add(new TryCatchBlockNode(start,end,handler,null));
    }
    private static boolean overlayVar(AbstractInsnNode at,int opcode,int slot){return at instanceof VarInsnNode value&&value.getOpcode()==opcode&&value.var==slot;}
    private static boolean overlayField(AbstractInsnNode at,String owner,String name,String descriptor){return at instanceof FieldInsnNode value&&value.getOpcode()==Opcodes.GETFIELD&&value.owner.equals(owner)&&value.name.equals(name)&&value.desc.equals(descriptor);}
    private static boolean overlayCall(AbstractInsnNode at,int opcode,String owner,String name,String descriptor){return at instanceof MethodInsnNode value&&value.getOpcode()==opcode&&value.owner.equals(owner)&&value.name.equals(name)&&value.desc.equals(descriptor);}
    /** Observe only the actual completed Map.put and indexed List.add tail of Forge's private register. */
    private static void overlayRegister(ClassNode node,MethodNode method){
        List<AbstractInsnNode> code=new ArrayList<>();for(AbstractInsnNode at:method.instructions.toArray())if(at.getOpcode()>=0)code.add(at);
        int begin=code.size()-12;
        if(begin<0||(method.access&Opcodes.ACC_PRIVATE)==0
                ||!overlayVar(code.get(begin),Opcodes.ALOAD,0)||!overlayField(code.get(begin+1),node.name,"overlays","Ljava/util/Map;")
                ||!overlayVar(code.get(begin+2),Opcodes.ALOAD,5)||!overlayVar(code.get(begin+3),Opcodes.ALOAD,4)
                ||!overlayCall(code.get(begin+4),Opcodes.INVOKEINTERFACE,"java/util/Map","put","(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;")
                ||code.get(begin+5).getOpcode()!=Opcodes.POP||!overlayVar(code.get(begin+6),Opcodes.ALOAD,0)
                ||!overlayField(code.get(begin+7),node.name,"orderedOverlays","Ljava/util/List;")||!overlayVar(code.get(begin+8),Opcodes.ILOAD,6)
                ||!overlayVar(code.get(begin+9),Opcodes.ALOAD,5)||!overlayCall(code.get(begin+10),Opcodes.INVOKEINTERFACE,"java/util/List","add","(ILjava/lang/Object;)V")
                ||code.get(begin+11).getOpcode()!=Opcodes.RETURN)throw new IllegalStateException("CLIENT_OVERLAY_REGISTER_LAYOUT_CHANGED");
        InsnList hook=new InsnList();hook.add(new VarInsnNode(Opcodes.ALOAD,0));hook.add(new VarInsnNode(Opcodes.ALOAD,0));hook.add(new FieldInsnNode(Opcodes.GETFIELD,node.name,"overlays","Ljava/util/Map;"));
        hook.add(new VarInsnNode(Opcodes.ALOAD,0));hook.add(new FieldInsnNode(Opcodes.GETFIELD,node.name,"orderedOverlays","Ljava/util/List;"));hook.add(new VarInsnNode(Opcodes.ALOAD,5));hook.add(new VarInsnNode(Opcodes.ALOAD,4));
        call(hook,"overlayRegistered","(Ljava/lang/Object;Ljava/util/Map;Ljava/util/List;Ljava/lang/Object;Ljava/lang/Object;)V");method.instructions.insertBefore(code.get(begin+11),hook);
    }
    private static void overlayInit(ClassNode node,MethodNode method){
        if((method.access&Opcodes.ACC_STATIC)==0)throw new IllegalStateException("CLIENT_OVERLAY_INIT_LAYOUT_CHANGED");
        int listWrites=0,mapWrites=0,events=0;
        for(AbstractInsnNode at:method.instructions.toArray()){
            if(at instanceof FieldInsnNode slot&&slot.getOpcode()==Opcodes.PUTSTATIC&&slot.owner.equals(node.name)){
                if(slot.name.equals("OVERLAYS")&&slot.desc.equals("Lcom/google/common/collect/ImmutableList;"))listWrites++;
                if(slot.name.equals("OVERLAYS_BY_NAME")&&slot.desc.equals("Lcom/google/common/collect/ImmutableMap;"))mapWrites++;
            }else if(overlayCall(at,Opcodes.INVOKESPECIAL,OVERLAY_EVENT,"<init>","(Ljava/util/Map;Ljava/util/List;)V"))events++;
        }
        if(listWrites!=1||mapWrites!=1||events!=1)throw new IllegalStateException("CLIENT_OVERLAY_INIT_PUBLICATION_CHANGED");
        int token=method.maxLocals++,failure=method.maxLocals++;LabelNode start=new LabelNode(),end=new LabelNode(),handler=new LabelNode();
        InsnList enter=new InsnList();enter.add(new LdcInsnNode(Type.getObjectType(node.name)));call(enter,"overlayInitEntering","(Ljava/lang/Class;)Ljava/lang/Object;");enter.add(new VarInsnNode(Opcodes.ASTORE,token));enter.add(start);
        for(AbstractInsnNode at:method.instructions.toArray())if(at.getOpcode()==Opcodes.RETURN){
            InsnList exit=new InsnList();exit.add(new LdcInsnNode(Type.getObjectType(node.name)));exit.add(new VarInsnNode(Opcodes.ALOAD,0));exit.add(new VarInsnNode(Opcodes.ALOAD,1));
            call(exit,"overlayPublished","(Ljava/lang/Class;Ljava/util/Map;Ljava/util/List;)V");exit.add(new VarInsnNode(Opcodes.ALOAD,token));call(exit,"overlayInitExiting","(Ljava/lang/Object;)V");method.instructions.insertBefore(at,exit);
        }
        method.instructions.insert(enter);method.instructions.add(end);method.instructions.add(handler);method.instructions.add(new VarInsnNode(Opcodes.ASTORE,failure));method.instructions.add(new VarInsnNode(Opcodes.ALOAD,token));
        call(method.instructions,"overlayInitExiting","(Ljava/lang/Object;)V");method.instructions.add(new VarInsnNode(Opcodes.ALOAD,failure));method.instructions.add(new InsnNode(Opcodes.ATHROW));method.tryCatchBlocks.add(new TryCatchBlockNode(start,end,handler,null));
    }
    /** A cached old immutable iteration still crosses the actual Named slot boundary. */
    private static void overlayRender(MethodNode method){
        int token=method.maxLocals++,failure=method.maxLocals++;LabelNode start=new LabelNode(),end=new LabelNode(),handler=new LabelNode();
        InsnList enter=new InsnList();enter.add(new VarInsnNode(Opcodes.ALOAD,3));call(enter,"overlayEntering","(Ljava/lang/Object;)Ljava/lang/Object;");enter.add(new VarInsnNode(Opcodes.ASTORE,token));
        enter.add(new VarInsnNode(Opcodes.ALOAD,token));enter.add(new FieldInsnNode(Opcodes.GETSTATIC,"java/lang/Boolean","FALSE","Ljava/lang/Boolean;"));enter.add(new JumpInsnNode(Opcodes.IF_ACMPNE,start));enter.add(new InsnNode(Opcodes.RETURN));enter.add(start);
        for(AbstractInsnNode at:method.instructions.toArray())if(at.getOpcode()==Opcodes.RETURN){InsnList exit=new InsnList();exit.add(new VarInsnNode(Opcodes.ALOAD,token));call(exit,"overlayExiting","(Ljava/lang/Object;)V");method.instructions.insertBefore(at,exit);}
        method.instructions.insert(enter);method.instructions.add(end);method.instructions.add(handler);method.instructions.add(new VarInsnNode(Opcodes.ASTORE,failure));method.instructions.add(new VarInsnNode(Opcodes.ALOAD,token));
        call(method.instructions,"overlayExiting","(Ljava/lang/Object;)V");method.instructions.add(new VarInsnNode(Opcodes.ALOAD,failure));method.instructions.add(new InsnNode(Opcodes.ATHROW));method.tryCatchBlocks.add(new TryCatchBlockNode(start,end,handler,null));
    }
    /** Gates the actual slot after opening/closing callbacks, with one finally for every real setter outcome. */
    private static void presentation(MethodNode method,int incoming,boolean screen){
        int token=method.maxLocals++,error=method.maxLocals++;
        LabelNode allowed=new LabelNode(),start=new LabelNode(),end=new LabelNode(),complete=new LabelNode(),failed=new LabelNode();
        for(AbstractInsnNode at:method.instructions.toArray()){
            if(at.getOpcode()==Opcodes.RETURN)method.instructions.set(at,new JumpInsnNode(Opcodes.GOTO,complete));
            else if(at instanceof FieldInsnNode slot&&slot.getOpcode()==Opcodes.PUTFIELD&&slot.owner.equals("net/minecraft/client/Minecraft")
                    &&(screen?Set.of("screen","f_91080_"):Set.of("overlay","f_91081_")).contains(slot.name)
                    &&slot.desc.equals(screen?"Lnet/minecraft/client/gui/screens/Screen;":"Lnet/minecraft/client/gui/screens/Overlay;")){
                LabelNode write=new LabelNode();InsnList gate=new InsnList();gate.add(new InsnNode(Opcodes.DUP2));gate.add(new VarInsnNode(Opcodes.ALOAD,token));
                call(gate,"presentationWriting","(Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;)Z");gate.add(new JumpInsnNode(Opcodes.IFNE,write));
                gate.add(new InsnNode(Opcodes.POP2));gate.add(new JumpInsnNode(Opcodes.GOTO,complete));gate.add(write);method.instructions.insertBefore(slot,gate);
            }
        }
        InsnList entry=new InsnList();entry.add(new VarInsnNode(Opcodes.ALOAD,0));entry.add(new VarInsnNode(Opcodes.ALOAD,incoming));
        call(entry,"presentationEntering","(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;");entry.add(new VarInsnNode(Opcodes.ASTORE,token));
        entry.add(new VarInsnNode(Opcodes.ALOAD,token));entry.add(new FieldInsnNode(Opcodes.GETSTATIC,"java/lang/Boolean","FALSE","Ljava/lang/Boolean;"));
        entry.add(new JumpInsnNode(Opcodes.IF_ACMPNE,allowed));entry.add(new InsnNode(Opcodes.RETURN));entry.add(allowed);entry.add(start);method.instructions.insert(entry);
        InsnList exit=new InsnList();exit.add(end);exit.add(complete);exit.add(new VarInsnNode(Opcodes.ALOAD,token));call(exit,"presentationExiting","(Ljava/lang/Object;)V");exit.add(new InsnNode(Opcodes.RETURN));
        exit.add(failed);exit.add(new VarInsnNode(Opcodes.ASTORE,error));exit.add(new VarInsnNode(Opcodes.ALOAD,token));call(exit,"presentationExiting","(Ljava/lang/Object;)V");
        exit.add(new VarInsnNode(Opcodes.ALOAD,error));exit.add(new InsnNode(Opcodes.ATHROW));method.instructions.add(exit);method.tryCatchBlocks.add(new TryCatchBlockNode(start,end,failed,null));
    }
    private static int[] locals(MethodNode method,Type[] args){int[] vars=new int[args.length];int next=(method.access&Opcodes.ACC_STATIC)==0?1:0;for(int i=0;i<args.length;i++){vars[i]=next;next+=args[i].getSize();}return vars;}
    private static void call(InsnList code,String name,String descriptor){code.add(new MethodInsnNode(Opcodes.INVOKESTATIC,BRIDGE,name,descriptor,false));}
    private static void afterVoid(MethodNode method,String name){for(AbstractInsnNode at:method.instructions.toArray())if(at.getOpcode()==Opcodes.RETURN){InsnList hook=new InsnList();hook.add(new VarInsnNode(Opcodes.ALOAD,0));call(hook,name,"(Ljava/lang/Object;)V");method.instructions.insertBefore(at,hook);}}
    private static void capacityWrites(ClassNode owner,MethodNode method){
        java.util.Set<String> storage=new java.util.HashSet<>();
        for(AbstractInsnNode at:method.instructions.toArray())if(at instanceof FieldInsnNode field&&field.getOpcode()==Opcodes.PUTFIELD&&field.owner.equals(owner.name)&&field.desc.equals("Ljava/nio/ByteBuffer;"))storage.add(field.name);
        if(storage.size()!=1)throw new IllegalStateException("GPU_CAPACITY_STORAGE_LAYOUT_CHANGED");
        // ensureCapacity is called after every vertex. Notify on the actual storage
        // replacement, preserving realloc validation without work on its no-op branch.
        for(AbstractInsnNode at:method.instructions.toArray())if(at instanceof FieldInsnNode field&&field.getOpcode()==Opcodes.PUTFIELD&&field.owner.equals(owner.name)&&storage.contains(field.name)&&field.desc.equals("Ljava/nio/ByteBuffer;")){
            InsnList hook=new InsnList();hook.add(new VarInsnNode(Opcodes.ALOAD,0));call(hook,"verticesCapacityChanged","(Ljava/lang/Object;)V");method.instructions.insert(at,hook);
        }
    }
    private static void afterObject(MethodNode method,String name){for(AbstractInsnNode at:method.instructions.toArray())if(at.getOpcode()==Opcodes.ARETURN){InsnList hook=new InsnList();hook.add(new InsnNode(Opcodes.DUP));hook.add(new VarInsnNode(Opcodes.ALOAD,0));hook.add(new InsnNode(Opcodes.SWAP));call(hook,name,"(Ljava/lang/Object;Ljava/lang/Object;)V");method.instructions.insertBefore(at,hook);}}
    private static String vertexCounter(ClassNode node){
        java.util.Set<String> fields=new java.util.HashSet<>();
        for(MethodNode method:node.methods)if(member(method,"endVertex","m_5752_","()V")){
            AbstractInsnNode previous=null,before=null;
            for(AbstractInsnNode at:method.instructions)if(at.getOpcode()>=0){
                if(at.getOpcode()==Opcodes.IADD&&previous!=null&&previous.getOpcode()==Opcodes.ICONST_1&&before instanceof FieldInsnNode field&&field.getOpcode()==Opcodes.GETFIELD&&field.owner.equals(node.name)&&field.desc.equals("I"))fields.add(field.name);
                before=previous;previous=at;
            }
        }
        if(fields.size()!=1)throw new IllegalStateException("GPU_VERTEX_COUNTER_LAYOUT_CHANGED");return fields.iterator().next();
    }
    private static void vertices(ClassNode node,MethodNode method,int bulk){
        String count=vertexCounter(node);int token=method.maxLocals++,failure=method.maxLocals++;LabelNode start=new LabelNode(),end=new LabelNode(),handler=new LabelNode();InsnList entry=new InsnList();entry.add(new VarInsnNode(Opcodes.ALOAD,0));if(bulk<0)entry.add(new InsnNode(Opcodes.ACONST_NULL));else entry.add(new VarInsnNode(Opcodes.ALOAD,bulk));
        entry.add(new VarInsnNode(Opcodes.ALOAD,0));entry.add(new FieldInsnNode(Opcodes.GETFIELD,node.name,count,"I"));call(entry,"verticesEntering","(Ljava/lang/Object;Ljava/lang/Object;I)Ljava/lang/Object;");entry.add(new VarInsnNode(Opcodes.ASTORE,token));entry.add(start);method.instructions.insert(entry);
        for(AbstractInsnNode at:method.instructions.toArray())if(at.getOpcode()==Opcodes.RETURN){InsnList exit=new InsnList();exit.add(new VarInsnNode(Opcodes.ALOAD,0));exit.add(new VarInsnNode(Opcodes.ALOAD,token));exit.add(new VarInsnNode(Opcodes.ALOAD,0));exit.add(new FieldInsnNode(Opcodes.GETFIELD,node.name,count,"I"));call(exit,"verticesCompleted","(Ljava/lang/Object;Ljava/lang/Object;I)V");method.instructions.insertBefore(at,exit);}
        method.instructions.add(end);method.instructions.add(handler);method.instructions.add(new VarInsnNode(Opcodes.ASTORE,failure));method.instructions.add(new VarInsnNode(Opcodes.ALOAD,0));method.instructions.add(new VarInsnNode(Opcodes.ALOAD,token));method.instructions.add(new VarInsnNode(Opcodes.ALOAD,0));method.instructions.add(new FieldInsnNode(Opcodes.GETFIELD,node.name,count,"I"));call(method.instructions,"verticesFailed","(Ljava/lang/Object;Ljava/lang/Object;I)V");method.instructions.add(new VarInsnNode(Opcodes.ALOAD,failure));method.instructions.add(new InsnNode(Opcodes.ATHROW));method.tryCatchBlocks.add(new TryCatchBlockNode(start,end,handler,"java/lang/Throwable"));method.maxStack=Math.max(method.maxStack,4);
    }
    private static void upload(MethodNode method,int rendered){int token=method.maxLocals++;
        for(AbstractInsnNode at:method.instructions.toArray())if(at.getOpcode()==Opcodes.RETURN){InsnList exit=new InsnList();exit.add(new VarInsnNode(Opcodes.ALOAD,0));exit.add(new VarInsnNode(Opcodes.ALOAD,token));call(exit,"verticesUploaded","(Ljava/lang/Object;Ljava/lang/Object;)V");method.instructions.insertBefore(at,exit);}
        LabelNode allowed=new LabelNode();InsnList entry=new InsnList();entry.add(new VarInsnNode(Opcodes.ALOAD,0));entry.add(new VarInsnNode(Opcodes.ALOAD,rendered));call(entry,"verticesUploading","(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;");entry.add(new VarInsnNode(Opcodes.ASTORE,token));entry.add(new VarInsnNode(Opcodes.ALOAD,token));entry.add(new FieldInsnNode(Opcodes.GETSTATIC,"java/lang/Boolean","FALSE","Ljava/lang/Boolean;"));entry.add(new JumpInsnNode(Opcodes.IF_ACMPNE,allowed));entry.add(new InsnNode(Opcodes.RETURN));entry.add(allowed);method.instructions.insert(entry);}
    private static void sortRestore(MethodNode method,int sorting){LabelNode allowed=new LabelNode();InsnList entry=new InsnList();entry.add(new VarInsnNode(Opcodes.ALOAD,0));entry.add(new VarInsnNode(Opcodes.ALOAD,sorting));call(entry,"verticesSortAllowed","(Ljava/lang/Object;Ljava/lang/Object;)Z");entry.add(new JumpInsnNode(Opcodes.IFNE,allowed));entry.add(new InsnNode(Opcodes.RETURN));entry.add(allowed);method.instructions.insert(entry);}
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
    private static void soundRelease(MethodNode method){
        int token=method.maxLocals++,error=method.maxLocals++;LabelNode start=new LabelNode(),end=new LabelNode(),complete=new LabelNode(),failed=new LabelNode();
        for(AbstractInsnNode at:method.instructions.toArray())if(at.getOpcode()==Opcodes.RETURN)method.instructions.set(at,new JumpInsnNode(Opcodes.GOTO,complete));
        InsnList entry=new InsnList();entry.add(new VarInsnNode(Opcodes.ALOAD,0));call(entry,"soundReleaseEntering","(Ljava/lang/Object;)Ljava/lang/Object;");entry.add(new VarInsnNode(Opcodes.ASTORE,token));entry.add(start);method.instructions.insert(entry);
        InsnList exit=new InsnList();exit.add(end);exit.add(complete);exit.add(new VarInsnNode(Opcodes.ALOAD,token));exit.add(new InsnNode(Opcodes.ICONST_1));call(exit,"soundReleaseExiting","(Ljava/lang/Object;Z)V");exit.add(new InsnNode(Opcodes.RETURN));exit.add(failed);exit.add(new VarInsnNode(Opcodes.ASTORE,error));exit.add(new VarInsnNode(Opcodes.ALOAD,token));exit.add(new InsnNode(Opcodes.ICONST_0));call(exit,"soundReleaseExiting","(Ljava/lang/Object;Z)V");exit.add(new VarInsnNode(Opcodes.ALOAD,error));exit.add(new InsnNode(Opcodes.ATHROW));method.instructions.add(exit);method.tryCatchBlocks.add(new TryCatchBlockNode(start,end,failed,null));
    }
    private static void guard(MethodNode method,int object,String operation){InsnList code=new InsnList();LabelNode allowed=new LabelNode();code.add(new VarInsnNode(Opcodes.ALOAD,object));call(code,operation,"(Ljava/lang/Object;)Z");code.add(new JumpInsnNode(Opcodes.IFNE,allowed));defaultValue(code,Type.getReturnType(method.desc));code.add(new InsnNode(Type.getReturnType(method.desc).getOpcode(Opcodes.IRETURN)));code.add(allowed);method.instructions.insert(code);}
    private static void defaultValue(InsnList code,Type result){switch(result.getSort()){
        case Type.VOID->{}case Type.LONG->code.add(new InsnNode(Opcodes.LCONST_0));case Type.FLOAT->code.add(new InsnNode(Opcodes.FCONST_0));case Type.DOUBLE->code.add(new InsnNode(Opcodes.DCONST_0));case Type.OBJECT,Type.ARRAY->code.add(new InsnNode(Opcodes.ACONST_NULL));default->code.add(new InsnNode(Opcodes.ICONST_0));}}
    private static void callback(MethodNode method,MethodInsnNode invoke,String named,String caller){
        Type[] args=Type.getArgumentTypes(invoke.desc);Type result=Type.getReturnType(invoke.desc);int[] vars=new int[args.length];for(int i=0;i<args.length;i++){vars[i]=method.maxLocals;method.maxLocals+=args[i].getSize();}
        int receiver=method.maxLocals++,token=method.maxLocals++,error=method.maxLocals++,output=method.maxLocals;method.maxLocals+=result.getSize();
        LabelNode allowed=new LabelNode(),start=new LabelNode(),end=new LabelNode(),failed=new LabelNode(),done=new LabelNode();InsnList code=new InsnList();
        for(int i=args.length-1;i>=0;i--)code.add(new VarInsnNode(args[i].getOpcode(Opcodes.ISTORE),vars[i]));code.add(new VarInsnNode(Opcodes.ASTORE,receiver));
        code.add(new VarInsnNode(Opcodes.ALOAD,receiver));call(code,"allowed","(Ljava/lang/Object;)Z");code.add(new JumpInsnNode(Opcodes.IFNE,allowed));
        boolean reload=invoke.owner.equals("net/minecraft/server/packs/resources/PreparableReloadListener")&&named.equals("reload");
        if(reload){code.add(new VarInsnNode(Opcodes.ALOAD,vars[0]));call(code,"skipReload","(Ljava/lang/Object;)Ljava/util/concurrent/CompletableFuture;");}
        else if(invoke.owner.equals("net/minecraft/client/gui/components/toasts/Toast")&&named.equals("render")){
            if(caller.equals(TOAST_INSTANCE)&&member(method,"render","m_280442_","(ILnet/minecraft/client/gui/GuiGraphics;)Z")){
                code.add(new VarInsnNode(Opcodes.ALOAD,receiver));call(code,"toastRenderSkipped","(Ljava/lang/Object;)Ljava/lang/Object;");code.add(new TypeInsnNode(Opcodes.CHECKCAST,result.getInternalName()));
            }else code.add(new FieldInsnNode(Opcodes.GETSTATIC,"net/minecraft/client/gui/components/toasts/Toast$Visibility","HIDE",result.getDescriptor()));
        }else if(invoke.owner.equals(NARRATABLE)&&named.equals("narrationPriority"))code.add(new FieldInsnNode(Opcodes.GETSTATIC,NARRATABLE+"$NarrationPriority","NONE",result.getDescriptor()));
        else defaultValue(code,result);
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
