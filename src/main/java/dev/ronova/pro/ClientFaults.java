package dev.ronova.pro;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.shaders.Program;
import com.mojang.blaze3d.shaders.Uniform;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.datafixers.util.Pair;
import com.mojang.logging.LogUtils;
import java.io.IOException;
import java.lang.invoke.*;
import java.util.*;
import java.util.function.*;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraftforge.client.event.RegisterShadersEvent;
import net.minecraftforge.client.event.*;
import net.minecraftforge.eventbus.api.*;
import org.slf4j.Logger;

/** Client rendering faults only. No task, world-save, class-loading or VM exception is hidden here. */
public final class ClientFaults {
    private static final Logger LOG=LogUtils.getLogger();
    private static final Set<Object> disabledListeners=Collections.newSetFromMap(new IdentityHashMap<>());
    private static final ThreadLocal<Scope> registration=new ThreadLocal<>();
    private record FailedShader(Class<?> type,String name) { }
    private static final Set<FailedShader> failedShaders=new HashSet<>();
    private static boolean installed,emptyShaderReported;
    private static long isolated,discarded;
    private ClientFaults() { }

    /** Implemented on the real shader, so failed shaders do not need a global strong-reference cache. */
    public interface ShaderState {
        boolean ronova$faulted();
        String ronova$shaderName();
        void ronova$disable(boolean disposed);
        void ronova$clearFailedDraw();
    }
    public interface ShaderList {
        List<Pair<ShaderInstance,Consumer<ShaderInstance>>> ronova$shaderList();
    }

    public static synchronized void install() {
        if(installed)return;
        try {
            Class<?> bridge=Class.forName("dev.ronova.pro.bootstrap.EventFaultBridge",true,null);
            var callback=MethodHandles.lookup().findStatic(ClientFaults.class,"dispatch",
                    MethodType.methodType(boolean.class,Object.class,Object.class,Object.class));
            bridge.getMethod("install",Class.class,MethodHandle.class).invoke(null,ClientFaults.class,callback);
            Class<?> agent=Class.forName("dev.ronova.pro.agent.RecoveryAgent",false,ClassLoader.getSystemClassLoader());
            installed=Boolean.TRUE.equals(agent.getMethod("eventFaultGuarded").invoke(null));
            LOG.info("RONOVA_CLIENT_FAULT_ISOLATION {}",installed?"SHADER_REGISTRATION_AND_DRAW_READY":"EVENT_DISPATCH_UNAVAILABLE");
        } catch(ReflectiveOperationException|RuntimeException unavailable) {
            LOG.error("RONOVA_CLIENT_FAULT_ISOLATION_INSTALL_FAILED",unavailable);
        }
    }

    /** Called by the bootstrap bridge at the original per-listener dispatch point. */
    public static boolean dispatch(Object dispatcher,Object listener,Object event) throws Throwable {
        if(!RenderSystem.isOnRenderThread())return false;
        if(!(event instanceof RegisterShadersEvent shaders))return dispatchDraw(dispatcher,listener,event);
        if(disabledListeners.contains(listener))return true;
        List<Pair<ShaderInstance,Consumer<ShaderInstance>>> list=((ShaderList)shaders).ronova$shaderList();
        Scope previous=registration.get(),scope=new Scope(list);
        registration.set(scope);
        try {
            ((IEventBusInvokeDispatcher)dispatcher).invoke((IEventListener)listener,shaders);
        } catch(Throwable failure) {
            if(!recoverable(failure))throw failure;
            // Undo only this callback's registrations and allocations, including a constructor that never returned.
            try { scope.abort(); }
            catch(Throwable cleanup) { if(cleanup!=failure)failure.addSuppressed(cleanup);throw failure; }
            disabledListeners.add(listener);
            report("shader-registration",listener.getClass().getName(),failure);
            return true;
        } finally {
            if(previous==null)registration.remove();else registration.set(previous);
        }
        // Publication callbacks run later, after GameRenderer takes ownership of these shaders.
        for(int i=scope.before.size();i<list.size();i++) {
            var pair=list.get(i);Consumer<ShaderInstance> consumer=pair.getSecond();
            list.set(i,Pair.of(pair.getFirst(),shader->loaded(listener,consumer,shader)));
        }
        return true;
    }

    private static boolean dispatchDraw(Object dispatcher,Object listener,Object object)throws Throwable {
        Object context;
        if(object instanceof RenderGuiEvent e)context=e.getGuiGraphics();
        else if(object instanceof RenderGuiOverlayEvent e)context=e.getGuiGraphics();
        else if(object instanceof ScreenEvent.Render e)context=e.getGuiGraphics();
        else if(object instanceof RenderLevelStageEvent e)context=e.getPoseStack();
        else if(object instanceof RenderLivingEvent<?,?> e)context=e.getPoseStack();
        else if(object instanceof RenderPlayerEvent e)context=e.getPoseStack();
        else if(object instanceof RenderNameTagEvent e)context=e.getPoseStack();
        else return false;
        Event event=(Event)object;
        Object token=ClientDrawFaults.beginEvent(listener,event.getClass(),context);if(token==null)return true;
        boolean cancelled=event.isCancelable()&&event.isCanceled();Event.Result result=event.getResult();
        net.minecraft.network.chat.Component name=object instanceof RenderNameTagEvent e?e.getContent():null;
        try {((IEventBusInvokeDispatcher)dispatcher).invoke((IEventListener)listener,event);}
        catch(Throwable failure) {
            if(!ClientItemFaults.failed(token,failure))throw failure;
            if(event.isCancelable())event.setCanceled(cancelled);
            if(event.hasResult())event.setResult(result);
            if(object instanceof RenderNameTagEvent e)e.setContent(name);
            return true;
        }
        ClientItemFaults.leave(token);return true;
    }

    private static void loaded(Object listener,Consumer<ShaderInstance> consumer,ShaderInstance shader) {
        if(disabledListeners.contains(listener)) { ((ShaderState)shader).ronova$disable(false);return; }
        try { consumer.accept(shader); }
        catch(RuntimeException failure) {
            if(!recoverable(failure))throw failure;
            // GameRenderer owns disposal now. Keep its ordinary close/reload path intact.
            ((ShaderState)shader).ronova$disable(false);disabledListeners.add(listener);
            report("shader-publication",consumer.getClass().getName(),failure);
        }
    }

    private static final class Scope {
        final List<Pair<ShaderInstance,Consumer<ShaderInstance>>> list,before;
        final Set<Uniform> uniforms=Collections.newSetFromMap(new IdentityHashMap<>());
        final Set<Program> programs=Collections.newSetFromMap(new IdentityHashMap<>());
        final Set<Integer> ids=new HashSet<>();
        final Set<Integer> shaderIds=new HashSet<>();
        final Set<ShaderInstance> shaders=Collections.newSetFromMap(new IdentityHashMap<>());
        Scope(List<Pair<ShaderInstance,Consumer<ShaderInstance>>> list) { this.list=list;before=new ArrayList<>(list); }
        void abort() {
            list.clear();list.addAll(before);
            for(ShaderInstance shader:shaders)((ShaderState)shader).ronova$disable(true);
            for(Uniform uniform:List.copyOf(uniforms))uniform.close();
            for(int id:List.copyOf(ids))GlStateManager.glDeleteProgram(id);
            // Only newly compiled programs are ours. Cached programs used by other shaders are never closed here.
            for(Program program:List.copyOf(programs))program.close();
            for(int id:List.copyOf(shaderIds))GlStateManager.glDeleteShader(id);
        }
    }
    public static void allocatedUniform(Uniform uniform) { Scope s=registration.get();if(s!=null)s.uniforms.add(uniform); }
    public static void closedUniform(Uniform uniform) { Scope s=registration.get();if(s!=null)s.uniforms.remove(uniform); }
    public static void allocatedProgram(Program program) { Scope s=registration.get();if(s!=null)s.programs.add(program); }
    public static void closedProgram(Program program) { Scope s=registration.get();if(s!=null)s.programs.remove(program); }
    public static void allocatedProgramId(int id) { Scope s=registration.get();if(s!=null)s.ids.add(id); }
    public static void closedProgramId(int id) { Scope s=registration.get();if(s!=null)s.ids.remove(id); }
    public static void allocatedShaderId(int id) { Scope s=registration.get();if(s!=null)s.shaderIds.add(id); }
    public static void closedShaderId(int id) { Scope s=registration.get();if(s!=null)s.shaderIds.remove(id); }
    public static void constructedShader(ShaderInstance shader) {
        Scope s=registration.get();if(s!=null)s.shaders.add(shader);
        ShaderState state=(ShaderState)shader;
        if(failedShaders.contains(new FailedShader(shader.getClass(),state.ronova$shaderName())))state.ronova$disable(false);
    }

    /** One wrapper per render-state shader supplier, not one allocation per frame. */
    public static Supplier<ShaderInstance> guardSupplier(Supplier<ShaderInstance> supplier) {
        return new Supplier<>() {
            boolean failed;
            public ShaderInstance get() {
                if(failed)return null;
                try { return supplier.get(); }
                catch(RuntimeException failure) {
                    if(!recoverable(failure))throw failure;
                    failed=true;report("shader-supplier",supplier.getClass().getName(),failure);return null;
                }
            }
        };
    }
    public static boolean drawable(ShaderInstance shader) {
        if(shader!=null && !((ShaderState)shader).ronova$faulted())return true;
        discarded++;
        if(shader==null && !emptyShaderReported) {
            emptyShaderReported=true;LOG.warn("RONOVA_CLIENT_FAULT missing shader: skipped draw and released its pending buffer; other rendering continues");
        }
        return false;
    }
    public static boolean drawFailed(ShaderInstance shader,Throwable failure) {
        if(!recoverable(failure))return false;
        try {
            ((ShaderState)shader).ronova$disable(false);
            failedShaders.add(new FailedShader(shader.getClass(),((ShaderState)shader).ronova$shaderName()));
            // This bypasses an overridden, possibly broken clear() and resets the vanilla shader binding cache.
            ((ShaderState)shader).ronova$clearFailedDraw();
            BufferUploader.reset();
        } catch(Throwable cleanup) { if(cleanup!=failure)failure.addSuppressed(cleanup);return false; }
        report("shader-draw",shader.getClass().getName(),failure);return true;
    }
    static boolean recoverable(Throwable failure) {
        Set<Throwable> seen=Collections.newSetFromMap(new IdentityHashMap<>());
        Deque<Throwable> pending=new ArrayDeque<>();pending.add(failure);
        while(!pending.isEmpty()) {
            Throwable next=pending.removeFirst();if(!seen.add(next))continue;
            if(!(next instanceof RuntimeException || next instanceof IOException))return false;
            if(next.getCause()!=null)pending.add(next.getCause());
            Collections.addAll(pending,next.getSuppressed());
        }
        return true;
    }
    static void report(String phase,String source,Throwable failure) {
        isolated++;LOG.error("RONOVA_CLIENT_FAULT [{}] {} disabled for this session; no automatic retry",phase,source,failure);
    }
    public static String status() { return "registration="+installed+", isolated="+isolated+", skippedDraws="+discarded; }
}
