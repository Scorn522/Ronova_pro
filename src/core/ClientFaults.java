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
    private static volatile MethodHandle eventReadiness;
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
            Class<?> bus=Class.forName("net.minecraftforge.eventbus.EventBus",false,ClientFaults.class.getClassLoader());
            bridge.getMethod("install",Class.class,MethodHandles.Lookup.class,Class.class)
                    .invoke(null,ClientFaults.class,MethodHandles.lookup(),bus);
            eventReadiness=MethodHandles.publicLookup().findStatic(bridge,"ready",MethodType.methodType(boolean.class,Class.class))
                    .bindTo(ClientFaults.class);
            installed=true;
            LOG.info("RONOVA_CLIENT_FAULT_ISOLATION {}",eventDispatchReady()?"SHADER_REGISTRATION_AND_DRAW_READY":"HANDLER_REGISTERED_WAITING_FOR_EVENT_DISPATCH");
        } catch(ReflectiveOperationException|RuntimeException unavailable) {
            LOG.error("RONOVA_CLIENT_FAULT_ISOLATION_INSTALL_FAILED",unavailable);
        }
    }

    /** Called by the bootstrap bridge at the original per-listener dispatch point. */
    private static boolean dispatch(Object dispatcher,Object listener,Object event) throws Throwable {
        if(!(event instanceof RegisterShadersEvent shaders))return dispatchDraw(dispatcher,listener,event);
        if(!RenderSystem.isOnRenderThread())return false;
        if(disabledListeners.contains(listener))return true;
        List<Pair<ShaderInstance,Consumer<ShaderInstance>>> list=((ShaderList)shaders).ronova$shaderList();
        Scope previous=registration.get(),scope=new Scope(list,previous);
        registration.set(scope);
        try {
            ((IEventBusInvokeDispatcher)dispatcher).invoke((IEventListener)listener,shaders);
            // Publication callbacks run later, after GameRenderer takes ownership of these shaders.
            for(int i=scope.before.size();i<list.size();i++) {
                var pair=list.get(i);Consumer<ShaderInstance> consumer=pair.getSecond();
                list.set(i,Pair.of(pair.getFirst(),shader->loaded(listener,consumer,shader)));
            }
        } catch(Throwable failure) {
            if(!recoverable(failure))throw failure;
            // Undo only this callback's registrations and allocations, including a constructor that never returned.
            if(!scope.abort(failure))throw failure;
            disabledListeners.add(listener);
            report("shader-registration",listener.getClass().getName(),failure);
            ClientModGroups.isolateFault(listener);
            return true;
        } finally {
            if(previous==null)registration.remove();else registration.set(previous);
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
        if(!RenderSystem.isOnRenderThread())return false;
        Event event=(Event)object;
        Object token=ClientDrawFaults.beginEvent(listener,event.getClass(),context);if(token==null)return true;
        boolean cancelled=event.isCancelable()&&event.isCanceled();Event.Result result=event.getResult();
        net.minecraft.network.chat.Component name=object instanceof RenderNameTagEvent e?e.getContent():null;
        try {((IEventBusInvokeDispatcher)dispatcher).invoke((IEventListener)listener,event);}
        catch(Throwable failure) {
            if(!ClientDrawFaults.failed(token,failure))throw failure;
            try {
                if(event.isCancelable())event.setCanceled(cancelled);
                if(event.hasResult())event.setResult(result);
                if(object instanceof RenderNameTagEvent e)e.setContent(name);
            } catch(Throwable cleanup) { if(cleanup!=failure)failure.addSuppressed(cleanup);throw failure; }
            return true;
        }
        ClientDrawFaults.leave(token);return true;
    }

    private static void loaded(Object listener,Consumer<ShaderInstance> consumer,ShaderInstance shader) {
        if(disabledListeners.contains(listener)) { ((ShaderState)shader).ronova$disable(false);return; }
        try { consumer.accept(shader); }
        catch(Throwable failure) {
            if(!recoverable(failure))throw ClientFaults.<RuntimeException>raise(failure);
            // GameRenderer owns disposal now. Keep its ordinary close/reload path intact.
            try { ((ShaderState)shader).ronova$disable(false); }
            catch(Throwable cleanup) { if(cleanup!=failure)failure.addSuppressed(cleanup);throw ClientFaults.<RuntimeException>raise(failure); }
            disabledListeners.add(listener);
            report("shader-publication",consumer.getClass().getName(),failure);
            ClientModGroups.isolateFault(consumer);ClientModGroups.isolateFault(listener);
        }
    }

    private static final class Scope {
        final List<Pair<ShaderInstance,Consumer<ShaderInstance>>> list,before;
        final Scope previous;
        final Set<Uniform> uniforms=Collections.newSetFromMap(new IdentityHashMap<>());
        final Set<Program> programs=Collections.newSetFromMap(new IdentityHashMap<>());
        final Set<Integer> ids=new HashSet<>();
        final Set<Integer> shaderIds=new HashSet<>();
        final Set<ShaderInstance> shaders=Collections.newSetFromMap(new IdentityHashMap<>());
        Scope(List<Pair<ShaderInstance,Consumer<ShaderInstance>>> list,Scope previous) { this.list=list;before=new ArrayList<>(list);this.previous=previous; }
        boolean abort(Throwable failure) {
            boolean clean=cleanup(failure,()->{list.clear();list.addAll(before);});
            for(ShaderInstance shader:shaders)clean=cleanup(failure,()->((ShaderState)shader).ronova$disable(true))&&clean;
            for(Uniform uniform:List.copyOf(uniforms))clean=cleanup(failure,uniform::close)&&clean;
            for(int id:List.copyOf(ids))clean=cleanup(failure,()->GlStateManager.glDeleteProgram(id))&&clean;
            // Only newly compiled programs are ours. Cached programs used by other shaders are never closed here.
            for(Program program:List.copyOf(programs))clean=cleanup(failure,program::close)&&clean;
            for(int id:List.copyOf(shaderIds))clean=cleanup(failure,()->GlStateManager.glDeleteShader(id))&&clean;
            return clean;
        }
    }
    private static boolean cleanup(Throwable failure,Runnable action) {
        try {action.run();return true;}
        catch(Throwable cleanup) {if(cleanup!=failure)failure.addSuppressed(cleanup);return false;}
    }
    public static void allocatedUniform(Uniform uniform) { for(Scope s=registration.get(),owner=s;s!=null;s=s.previous)if(s.list==owner.list)s.uniforms.add(uniform); }
    public static void closedUniform(Uniform uniform) { for(Scope s=registration.get();s!=null;s=s.previous)s.uniforms.remove(uniform); }
    public static void allocatedProgram(Program program) { for(Scope s=registration.get(),owner=s;s!=null;s=s.previous)if(s.list==owner.list)s.programs.add(program); }
    public static void closedProgram(Program program) { for(Scope s=registration.get();s!=null;s=s.previous)s.programs.remove(program); }
    public static void allocatedProgramId(int id) { for(Scope s=registration.get(),owner=s;s!=null;s=s.previous)if(s.list==owner.list)s.ids.add(id); }
    public static void closedProgramId(int id) { for(Scope s=registration.get();s!=null;s=s.previous)s.ids.remove(id); }
    public static void allocatedShaderId(int id) { for(Scope s=registration.get(),owner=s;s!=null;s=s.previous)if(s.list==owner.list)s.shaderIds.add(id); }
    public static void closedShaderId(int id) { for(Scope s=registration.get();s!=null;s=s.previous)s.shaderIds.remove(id); }
    public static void constructedShader(ShaderInstance shader) {
        for(Scope s=registration.get(),owner=s;s!=null;s=s.previous)if(s.list==owner.list)s.shaders.add(shader);
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
                catch(Throwable failure) {
                    if(!recoverable(failure))throw ClientFaults.<RuntimeException>raise(failure);
                    failed=true;report("shader-supplier",supplier.getClass().getName(),failure);ClientModGroups.isolateFault(supplier);return null;
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
        report("shader-draw",shader.getClass().getName(),failure);ClientModGroups.isolateFault(shader);return true;
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
    private static boolean eventDispatchReady() {
        MethodHandle ready=eventReadiness;if(ready==null)return false;
        try {return (boolean)ready.invokeExact();}
        catch(Throwable failure){throw ClientFaults.<RuntimeException>raise(failure);}
    }
    @SuppressWarnings("unchecked")
    private static <E extends Throwable> RuntimeException raise(Throwable failure)throws E {throw (E)failure;}
    public static String status() { return "handlerRegistered="+installed+", eventDispatch="+eventDispatchReady()+", isolated="+isolated+", skippedDraws="+discarded; }
}
