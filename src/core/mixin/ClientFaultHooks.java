package dev.ronova.pro.mixin;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.shaders.*;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import com.mojang.datafixers.util.Pair;
import dev.ronova.pro.ClientFaults;
import java.util.*;
import java.util.function.*;
import net.minecraft.client.renderer.*;
import net.minecraftforge.client.event.RegisterShadersEvent;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;

public final class ClientFaultHooks {
    @Mixin(value=RegisterShadersEvent.class,remap=false)
    public interface Registration extends ClientFaults.ShaderList {
        @Accessor("shaderList") List<Pair<ShaderInstance,Consumer<ShaderInstance>>> ronova$shaderList();
    }
    @Mixin(Uniform.class)
    public static abstract class UniformAllocation {
        @Inject(method="<init>",at=@At("RETURN"))
        private void allocated(CallbackInfo ci) { ClientFaults.allocatedUniform((Uniform)(Object)this); }
        @Inject(method="close",at=@At("RETURN"))
        private void closed(CallbackInfo ci) { ClientFaults.closedUniform((Uniform)(Object)this); }
    }
    @Mixin(Program.class)
    public static abstract class ProgramAllocation {
        @Inject(method="compileShader",at=@At("RETURN"))
        private static void allocated(CallbackInfoReturnable<Program> cir) { ClientFaults.allocatedProgram(cir.getReturnValue()); }
        @Inject(method="close",at=@At("RETURN"))
        private void closed(CallbackInfo ci) { ClientFaults.closedProgram((Program)(Object)this); }
    }
    @Mixin(ProgramManager.class)
    public static abstract class ProgramIds {
        @Inject(method="createProgram",at=@At("RETURN"))
        private static void allocated(CallbackInfoReturnable<Integer> cir) { ClientFaults.allocatedProgramId(cir.getReturnValue()); }
    }
    @Mixin(GlStateManager.class)
    public static abstract class DeletedProgramIds {
        @Inject(method="glDeleteProgram",at=@At("RETURN"),remap=false)
        private static void closed(int id,CallbackInfo ci) { ClientFaults.closedProgramId(id); }
        @Inject(method="glCreateShader",at=@At("RETURN"),remap=false)
        private static void shaderAllocated(int type,CallbackInfoReturnable<Integer> cir) { ClientFaults.allocatedShaderId(cir.getReturnValue()); }
        @Inject(method="glDeleteShader",at=@At("RETURN"),remap=false)
        private static void shaderClosed(int id,CallbackInfo ci) { ClientFaults.closedShaderId(id); }
    }
    @Mixin(ShaderInstance.class)
    public static abstract class Shader implements ClientFaults.ShaderState,dev.ronova.pro.ClientItemFaults.ShaderCache {
        @Unique private boolean ronova$faulted,ronova$disposed;
        @Shadow @Final private String name;
        @Shadow private static int lastProgramId;
        @Shadow private static ShaderInstance lastAppliedShader;
        @Shadow @Final private List<Integer> samplerLocations;
        @Shadow @Final private List<String> samplerNames;
        @Shadow @Final private Map<String,Object> samplerMap;
        public boolean ronova$faulted() { return ronova$faulted; }
        public String ronova$shaderName() { return name; }
        public void ronova$invalidateShaderCache() { lastProgramId=-1;lastAppliedShader=null; }
        public void ronova$disable(boolean disposed) { ronova$faulted=true;ronova$disposed|=disposed; }
        public void ronova$clearFailedDraw() {
            ProgramManager.glUseProgram(0);lastProgramId=-1;lastAppliedShader=null;
            int active=GlStateManager._getActiveTexture();
            try {
                for(int i=0;i<samplerLocations.size();i++)if(samplerMap.get(samplerNames.get(i))!=null) {
                    GlStateManager._activeTexture(33984+i);GlStateManager._bindTexture(0);
                }
            } finally { GlStateManager._activeTexture(active); }
        }
        @Inject(method="<init>*",at=@At("RETURN"))
        private void constructed(CallbackInfo ci) { ClientFaults.constructedShader((ShaderInstance)(Object)this); }
        @Inject(method="close",at=@At("HEAD"),cancellable=true)
        private void skipDisposed(CallbackInfo ci) { if(ronova$disposed)ci.cancel(); }
        @Inject(method="close",at=@At("RETURN"))
        private void disposed(CallbackInfo ci) { ronova$disposed=true; }
    }
    @Mixin(RenderStateShard.ShaderStateShard.class)
    public static abstract class SupplierGuard {
        @ModifyVariable(method="<init>(Ljava/util/function/Supplier;)V",at=@At("HEAD"),argsOnly=true)
        private static Supplier<ShaderInstance> guard(Supplier<ShaderInstance> supplier) { return ClientFaults.guardSupplier(supplier); }
    }
    @Mixin(BufferUploader.class)
    public static abstract class PendingDraw {
        @Inject(method="_drawWithShader",at=@At("HEAD"),cancellable=true)
        private static void skipMissing(BufferBuilder.RenderedBuffer buffer,CallbackInfo ci) {
            if(!ClientFaults.drawable(RenderSystem.getShader())) { buffer.release();ci.cancel(); }
        }
    }
    @Mixin(VertexBuffer.class)
    public static abstract class Draw {
        @Inject(method="_drawWithShader",at=@At("HEAD"),cancellable=true)
        private void skipFailed(Matrix4f model,Matrix4f projection,ShaderInstance shader,CallbackInfo ci) {
            if(!ClientFaults.drawable(shader))ci.cancel();
        }
        // SaveFinallyPlugin adds the exceptional exit around this exact draw method.
    }
}
