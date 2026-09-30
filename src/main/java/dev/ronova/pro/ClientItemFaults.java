package dev.ronova.pro;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import java.util.*;
import net.minecraft.client.renderer.*;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.world.item.*;
import org.joml.*;
import org.lwjgl.opengl.*;

/** Shared draw scope and state cleanup, retaining precise item/model quarantine from the original item boundary. */
public final class ClientItemFaults {
    private record Key(Item item,Class<?> model) { }
    private static final Map<Item,Set<Class<?>>> disabled=new IdentityHashMap<>();
    private static final ThreadLocal<Scope> active=new ThreadLocal<>();
    private ClientItemFaults() { }
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
        Key key=new Key(item.getItem(),model==null?null:model.getClass());
        Set<Class<?>> models=disabled.get(key.item());if(models!=null && models.contains(key.model()))return null;
        Scope scope=new Scope(key,pose,active.get());active.set(scope);return scope;
    }
    public static PoseStack pose(Object token) { return ((Scope)token).pose; }
    static Object openDraw(ClientDrawFaults.Key key,Object context,Object source) {
        Scope scope=new DrawScope(key,context,source,active.get());active.set(scope);return scope;
    }
    public static void beforeScissor(Deque<ScreenRectangle> stack) {
        for(Scope scope=active.get();scope!=null;scope=scope.previous) {
            if(scope.scissors==null)scope.scissors=new IdentityHashMap<>();
            scope.scissors.computeIfAbsent(stack,ignored->new ArrayList<>(stack));
        }
    }
    public static void leave(Object token) {
        Scope previous=((Scope)token).previous;
        if(previous==null)active.remove();else active.set(previous);
    }
    public static void touched(Object buffer) {
        for(Scope scope=active.get();scope!=null;scope=scope.previous) {
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
            disabled.computeIfAbsent(scope.key.item(),ignored->new HashSet<>()).add(scope.key.model());
            ClientFaults.report("item-render",net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(scope.key.item())
                    +" / "+(scope.key.model()==null?"null":scope.key.model().getName()),failure);
            return true;
        } catch(Throwable cleanup) {
            if(cleanup!=failure)failure.addSuppressed(cleanup);return false;
        } finally { leave(token); }
    }
    private static class Scope {
        final Key key;final Scope previous;final PoseStack pose=new PoseStack();
        final State state=new State();Set<BufferAbort> buffers;
        Map<Deque<ScreenRectangle>,List<ScreenRectangle>> scissors;
        Scope(Key key,PoseStack original,Scope previous) {
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
