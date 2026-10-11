package dev.ronova.pro;

import com.mojang.blaze3d.vertex.*;
import java.util.*;
import java.lang.ref.*;
import net.minecraft.client.*;
import net.minecraft.client.gui.*;
import net.minecraft.client.gui.components.Renderable;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.particle.*;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.network.chat.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.client.gui.overlay.*;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.renderer.*;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.world.item.*;
import org.joml.*;
import org.lwjgl.opengl.*;

/** Local presentation failures share one cleanup and quarantine policy, never a world mutation policy. */
public final class ClientDrawFaults {
    record Key(String phase,Object definition,Object detail) { }
    private record Fonts(Class<?> renderer,Set<ResourceLocation> resources) { }
    private static final Set<Key> disabled=new HashSet<>();
    private static final ReferenceQueue<Object> retired=new ReferenceQueue<>();
    private static final Set<Screen> failedInputs=Collections.newSetFromMap(new WeakHashMap<>());
    private static final ThreadLocal<Layer> layer=new ThreadLocal<>();
    private ClientDrawFaults() { }

    /** Called at real draw entries after mixins have merged; no user callback executes outside the guard. */
    public static Object begin(String phase,Object receiver,Object payload,Object context) {
        Object definition=receiver==null?ClientDrawFaults.class:receiver.getClass(),detail=null;
        if(payload instanceof ItemStack stack)definition=stack.getItem();
        else if(payload instanceof Entity entity) {definition=entity.getType();detail=entity.getClass();}
        else if(payload instanceof BlockEntity block) {definition=block.getType();detail=block.getClass();}
        else if(phase.equals("tooltip") && receiver instanceof GuiGraphics gui) {
            ItemStack stack=((ClientDrawFaults.TooltipAccess)gui).ronova$tooltipStack();
            definition=stack.isEmpty()?(payload==null?Void.class:payload.getClass()):stack.getItem();
        } else if(phase.equals("font")) {
            // The actual Style font ids distinguish a broken effect font from ordinary menu text.
            Set<ResourceLocation> fonts=new HashSet<>();
            try {
                FormattedCharSequence text=payload instanceof Component c?c.getVisualOrderText():payload instanceof FormattedCharSequence s?s:null;
                if(text!=null)text.accept((index,style,codepoint)->{fonts.add(style.getFont());return true;});
                else fonts.add(Style.DEFAULT_FONT);
            } catch(RuntimeException failure) {
                // Let the guarded rendering execute and record its original failure. Never invent a font id.
                if(!ClientFaults.recoverable(failure))throw failure;
                detail=payload==null?Void.class:payload.getClass();
            }
            definition=new Fonts(receiver.getClass(),Set.copyOf(fonts));
        }
        Key key=new Key(phase,definition,detail);
        if(disabled.contains(key)) {
            if(phase.equals("screen")&&receiver instanceof Screen screen)failedScreen(screen);
            return null;
        }
        return ClientDrawFaults.openDraw(key,context,receiver);
    }
    private static Object callback(String phase,Object identity,Object context) {
        pruneCallbacks();Key key=new Key(phase,new Identity(identity),null);
        return disabled.contains(key)?null:ClientDrawFaults.openDraw(key,context,identity);
    }
    private static final class Identity extends WeakReference<Object> {
        final int hash;final String name;Key registered;
        Identity(Object value) {this(value,false);}
        Identity(Object value,boolean watch) {super(value,watch?retired:null);hash=System.identityHashCode(value);name=value.getClass().getName();}
        public int hashCode() {return hash;}
        public boolean equals(Object other) {return this==other||other instanceof Identity id&&get()!=null&&get()==id.get();}
        public String toString() {return name;}
    }
    private static void pruneCallbacks() {
        for(Reference<?> ref;(ref=retired.poll())!=null;) {
            if(ref instanceof Identity id&&id.registered!=null)disabled.remove(id.registered);
        }
    }
    static void disable(Key key,Object source,Throwable failure) {
        if(key.definition() instanceof Identity id&&id.get()!=null) {
            Identity watched=new Identity(id.get(),true);key=new Key(key.phase(),watched,key.detail());watched.registered=key;
        }
        disabled.add(key);ClientFaults.report(key.phase(),String.valueOf(key.definition()),failure);
        if(key.phase().equals("screen")&&source instanceof Screen screen)failedScreen(screen);
    }
    private static void failedScreen(Screen screen) {
        Minecraft mc=Minecraft.getInstance();
        if(mc.screen!=screen)return;
        // A visible escape route, not an invisible screen swallowing all input forever.
        mc.tell(()->{if(mc.screen==screen)mc.setScreen(new FaultScreen(screen.getClass().getSimpleName()));});
    }
    public static void widget(Renderable widget,GuiGraphics gui,int x,int y,float tick) {
        Object token=callback("widget",widget,gui);if(token==null)return;
        try {widget.render(gui,x,y,tick);}catch(Throwable failure) {if(!ClientDrawFaults.failed(token,failure))throwUnchecked(failure);return;}
        ClientDrawFaults.leave(token);
    }
    public static void overlay(IGuiOverlay overlay,ForgeGui forge,GuiGraphics gui,float tick,int width,int height) {
        Object token=callback("hud-overlay",overlay,gui);if(token==null)return;
        int left=forge.leftHeight,right=forge.rightHeight;
        try {overlay.render(forge,gui,tick,width,height);}
        catch(Throwable failure) {
            if(!ClientDrawFaults.failed(token,failure))throwUnchecked(failure);
            forge.leftHeight=left;forge.rightHeight=right;return;
        }
        ClientDrawFaults.leave(token);
    }
    public static Object beginEvent(Object listener,Class<?> event,Object context) {
        pruneCallbacks();Key key=new Key("render-event",new Identity(listener),event);
        return disabled.contains(key)?null:ClientDrawFaults.openDraw(key,context,listener);
    }

    private static final class Layer {
        final ParticleRenderType type;final BufferBuilder buffer;final TextureManager textures;final Object token;
        boolean broken;
        Layer(ParticleRenderType type,BufferBuilder buffer,TextureManager textures,Object token) {
            this.type=type;this.buffer=buffer;this.textures=textures;this.token=token;broken=token==null;
        }
    }
    public static void particleBegin(ParticleRenderType type,BufferBuilder buffer,TextureManager textures) {
        Object token=callback("particle-layer",type,null);Layer current=new Layer(type,buffer,textures,token);layer.set(current);
        if(current.broken)return;
        try {type.begin(buffer,textures);}
        catch(Throwable failure) {current.broken=true;if(!ClientDrawFaults.failed(token,failure))throwUnchecked(failure);}
    }
    public static void particle(Particle particle,VertexConsumer vertices,Camera camera,float tick) {
        Layer current=layer.get();if(current!=null&&current.broken)return;
        Object token=begin("particle",particle,null,null);if(token==null)return;
        try {particle.render(vertices,camera,tick);}
        catch(Throwable failure) {
            if(!ClientDrawFaults.failed(token,failure))throwUnchecked(failure);
            // A partial vertex cannot be submitted. Discard this unfinished layer batch, then
            // restart its real begin hook so the next healthy particle can still render.
            if(current!=null) {
                ((ClientDrawFaults.BufferAbort)current.buffer).ronova$abortItemBatch();
                try {current.type.begin(current.buffer,current.textures);}
                catch(Throwable restart) {
                    current.broken=true;
                    if(!ClientDrawFaults.failed(current.token,restart))throwUnchecked(restart);
                }
            }
            return;
        }
        ClientDrawFaults.leave(token);
    }
    public static void particleEnd(ParticleRenderType type,Tesselator tesselator) {
        Layer current=layer.get();layer.remove();
        if(current==null) {type.end(tesselator);return;}
        if(current.broken)return;
        try {type.end(tesselator);}
        catch(Throwable failure) {if(!ClientDrawFaults.failed(current.token,failure))throwUnchecked(failure);return;}
        ClientDrawFaults.leave(current.token);
    }
    /** Input callbacks have irreversible side effects: skip the failing callback, never retry it. */
    public static void screenInput(Runnable action,String title,String screenName) {
        Screen screen=Minecraft.getInstance().screen;
        if(screen!=null&&failedInputs.contains(screen))return;
        try {action.run();}
        catch(Throwable failure) {
            if(!ClientFaults.recoverable(failure))throwUnchecked(failure);
            ClientFaults.report("screen-input",screenName+" / "+title,failure);
            if(screen!=null) {failedInputs.add(screen);failedScreen(screen);}
        }
    }
    @SuppressWarnings("unchecked") private static <T extends Throwable> void throwUnchecked(Throwable failure)throws T {throw (T)failure;}

    private static final class FaultScreen extends Screen {
        private final String source;
        FaultScreen(String source) {super(Component.literal("Ronova: screen disabled"));this.source=source;}
        @Override protected void init() {
            addRenderableWidget(net.minecraft.client.gui.components.Button.builder(Component.literal("Continue"),b->{
                minecraft.setScreen(minecraft.level==null?new net.minecraft.client.gui.screens.TitleScreen():null);
            }).bounds(width/2-100,height/2+25,200,20).build());
        }
        @Override public void render(GuiGraphics gui,int x,int y,float tick) {
            renderBackground(gui);gui.drawCenteredString(font,title,width/2,height/2-35,0xFFFFFF);
            gui.drawCenteredString(font,Component.literal(source+" failed. Details are in latest.log."),width/2,height/2-12,0xFFAAAA);
            super.render(gui,x,y,tick);
        }
    }

    private record ItemKey(Item item,Class<?> model) { }
    private static final Map<Item,Set<Class<?>>> disabledItems=new IdentityHashMap<>();
    private static final ThreadLocal<Scope> drawScopes=new ThreadLocal<>();

    public interface BufferAbort { void ronova$abortItemBatch(); }
    public interface PoseAccess { Deque<PoseStack.Pose> ronova$poses(); }
    public interface ShaderCache { void ronova$invalidateShaderCache(); }
    public interface TooltipAccess {
        ItemStack ronova$tooltipStack();
        boolean ronova$managed();
        void ronova$managed(boolean value);
    }

    public static Object begin(ItemStack item,BakedModel model,PoseStack pose,MultiBufferSource buffers) {
        if(item.isEmpty())return null;
        ItemKey key=new ItemKey(item.getItem(),model==null?null:model.getClass());
        Set<Class<?>> models=disabledItems.get(key.item());if(models!=null && models.contains(key.model()))return null;
        Scope scope=new Scope(key,pose,drawScopes.get());drawScopes.set(scope);return scope;
    }
    public static PoseStack pose(Object token) { return ((Scope)token).pose; }
    static Object openDraw(ClientDrawFaults.Key key,Object context,Object source) {
        Scope scope=new DrawScope(key,context,source,drawScopes.get());drawScopes.set(scope);return scope;
    }
    public static void beforeScissor(Deque<ScreenRectangle> stack) {
        for(Scope scope=drawScopes.get();scope!=null;scope=scope.previous) {
            if(scope.scissors==null)scope.scissors=new IdentityHashMap<>();
            scope.scissors.computeIfAbsent(stack,ignored->new ArrayList<>(stack));
        }
    }
    public static void leave(Object token) {
        Scope previous=((Scope)token).previous;
        if(previous==null)drawScopes.remove();else drawScopes.set(previous);
    }
    public static void touched(Object buffer) {
        for(Scope scope=drawScopes.get();scope!=null;scope=scope.previous) {
            if(scope.buffers==null)scope.buffers=Collections.newSetFromMap(new IdentityHashMap<>());
            scope.buffers.add((BufferAbort)buffer);
        }
    }
    public static boolean failed(Object token,Throwable failure) {
        Scope scope=(Scope)token;
        try {
            if(!ClientFaults.recoverable(failure))return false;
            // Drop the unfinished frame batches, never delete the item, model, entity or saved data.
            if(scope.buffers!=null)for(BufferAbort buffer:scope.buffers)buffer.ronova$abortItemBatch();
            if(scope.scissors!=null)scope.scissors.forEach((stack,saved)->{stack.clear();stack.addAll(saved);});
            scope.state.restore();
            if(scope instanceof DrawScope draw) {
                if(draw.original!=null)restorePose(draw.original,draw.savedPose);
                if(draw.gui!=null)((TooltipAccess)draw.gui).ronova$managed(draw.managed);
                ClientDrawFaults.disable(draw.drawKey,draw.source,failure);return true;
            }
            disabledItems.computeIfAbsent(scope.key.item(),ignored->new HashSet<>()).add(scope.key.model());
            ClientFaults.report("item-render",net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(scope.key.item())
                    +" / "+(scope.key.model()==null?"null":scope.key.model().getName()),failure);
            return true;
        } catch(Throwable cleanup) {
            if(cleanup!=failure)failure.addSuppressed(cleanup);return false;
        } finally { leave(token); }
    }
    private static class Scope {
        final ItemKey key;final Scope previous;final PoseStack pose=new PoseStack();
        final State state=new State();Set<BufferAbort> buffers;
        Map<Deque<ScreenRectangle>,List<ScreenRectangle>> scissors;
        Scope(ItemKey key,PoseStack original,Scope previous) {
            this.key=key;this.previous=previous;
            pose.last().pose().set(original.last().pose());pose.last().normal().set(original.last().normal());
        }
    }
    private static final class DrawScope extends Scope {
        final ClientDrawFaults.Key drawKey;final Object source;final GuiGraphics gui;
        final PoseStack original;final List<SavedPose> savedPose;final boolean managed;
        DrawScope(ClientDrawFaults.Key key,Object context,Object source,Scope previous) {
            super(null,context instanceof GuiGraphics gui?gui.pose():context instanceof PoseStack pose?pose:new PoseStack(),previous);
            drawKey=key;this.source=source;gui=context instanceof GuiGraphics value?value:null;
            original=gui!=null?gui.pose():context instanceof PoseStack pose?pose:null;
            savedPose=original==null?List.of():savePose(original);
            managed=gui!=null&&((TooltipAccess)gui).ronova$managed();
        }
    }
    private record SavedPose(PoseStack.Pose pose,Matrix4f matrix,Matrix3f normal) { }
    private static List<SavedPose> savePose(PoseStack pose) {
        List<SavedPose> saved=new ArrayList<>();
        for(PoseStack.Pose entry:((PoseAccess)pose).ronova$poses())saved.add(new SavedPose(entry,new Matrix4f(entry.pose()),new Matrix3f(entry.normal())));
        return saved;
    }
    private static void restorePose(PoseStack pose,List<SavedPose> saved) {
        Deque<PoseStack.Pose> stack=((PoseAccess)pose).ronova$poses();stack.clear();
        for(SavedPose entry:saved) { entry.pose.pose().set(entry.matrix);entry.pose.normal().set(entry.normal);stack.addLast(entry.pose); }
    }
    /** Render state only; no proof ledger or persistent work is created for a failed frame. */
    private static final class State {
        final ShaderInstance shader=RenderSystem.getShader();
        final float[] color=RenderSystem.getShaderColor().clone();
        final Matrix4f projection=new Matrix4f(RenderSystem.getProjectionMatrix()),texture=new Matrix4f(RenderSystem.getTextureMatrix());
        final VertexSorting sorting=RenderSystem.getVertexSorting();
        final List<SavedPose> modelView=new ArrayList<>();
        final boolean blend=GL11.glIsEnabled(GL11.GL_BLEND),depth=GL11.glIsEnabled(GL11.GL_DEPTH_TEST),cull=GL11.glIsEnabled(GL11.GL_CULL_FACE),scissor=GL11.glIsEnabled(GL11.GL_SCISSOR_TEST);
        final boolean depthMask=GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK);
        final int depthFunc=GL11.glGetInteger(GL11.GL_DEPTH_FUNC),srcRgb=GL11.glGetInteger(GL14.GL_BLEND_SRC_RGB),dstRgb=GL11.glGetInteger(GL14.GL_BLEND_DST_RGB),
                srcAlpha=GL11.glGetInteger(GL14.GL_BLEND_SRC_ALPHA),dstAlpha=GL11.glGetInteger(GL14.GL_BLEND_DST_ALPHA),
                drawFbo=GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING),readFbo=GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING),
                program=GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM),activeTexture=GlStateManager._getActiveTexture();
        final int[] viewport=new int[4],scissorBox=new int[4],textures=new int[12];
        State() {
            GL11.glGetIntegerv(GL11.GL_VIEWPORT,viewport);if(scissor)GL11.glGetIntegerv(GL11.GL_SCISSOR_BOX,scissorBox);
            for(PoseStack.Pose entry:((PoseAccess)RenderSystem.getModelViewStack()).ronova$poses())
                modelView.add(new SavedPose(entry,new Matrix4f(entry.pose()),new Matrix3f(entry.normal())));
            for(int i=0;i<textures.length;i++)textures[i]=RenderSystem.getShaderTexture(i);
        }
        void restore() {
            Deque<PoseStack.Pose> stack=((PoseAccess)RenderSystem.getModelViewStack()).ronova$poses();stack.clear();
            for(SavedPose entry:modelView) { entry.pose.pose().set(entry.matrix);entry.pose.normal().set(entry.normal);stack.addLast(entry.pose); }
            RenderSystem.applyModelViewMatrix();RenderSystem.setProjectionMatrix(projection,sorting);RenderSystem.setTextureMatrix(texture);
            RenderSystem.setShaderColor(color[0],color[1],color[2],color[3]);
            if(blend)RenderSystem.enableBlend();else RenderSystem.disableBlend();
            RenderSystem.blendFuncSeparate(srcRgb,dstRgb,srcAlpha,dstAlpha);
            if(depth)RenderSystem.enableDepthTest();else RenderSystem.disableDepthTest();
            RenderSystem.depthFunc(depthFunc);RenderSystem.depthMask(depthMask);
            if(cull)RenderSystem.enableCull();else RenderSystem.disableCull();
            if(scissor)RenderSystem.enableScissor(scissorBox[0],scissorBox[1],scissorBox[2],scissorBox[3]);else RenderSystem.disableScissor();
            GlStateManager._glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER,drawFbo);GlStateManager._glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER,readFbo);
            RenderSystem.viewport(viewport[0],viewport[1],viewport[2],viewport[3]);
            for(int i=0;i<textures.length;i++)RenderSystem.setShaderTexture(i,textures[i]);
            GlStateManager._activeTexture(activeTexture);
            ShaderInstance current=RenderSystem.getShader();
            if(current!=null)((ShaderCache)current).ronova$invalidateShaderCache();
            else if(shader!=null)((ShaderCache)shader).ronova$invalidateShaderCache();
            RenderSystem.setShader(()->shader);GlStateManager._glUseProgram(program);BufferUploader.reset();
        }
    }

}
