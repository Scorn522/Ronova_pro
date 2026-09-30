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
            ItemStack stack=((ClientItemFaults.TooltipAccess)gui).ronova$tooltipStack();
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
        return ClientItemFaults.openDraw(key,context,receiver);
    }
    private static Object callback(String phase,Object identity,Object context) {
        pruneCallbacks();Key key=new Key(phase,new Identity(identity),null);
        return disabled.contains(key)?null:ClientItemFaults.openDraw(key,context,identity);
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
        try {widget.render(gui,x,y,tick);}catch(Throwable failure) {if(!ClientItemFaults.failed(token,failure))throwUnchecked(failure);return;}
        ClientItemFaults.leave(token);
    }
    public static void overlay(IGuiOverlay overlay,ForgeGui forge,GuiGraphics gui,float tick,int width,int height) {
        Object token=callback("hud-overlay",overlay,gui);if(token==null)return;
        int left=forge.leftHeight,right=forge.rightHeight;
        try {overlay.render(forge,gui,tick,width,height);}
        catch(Throwable failure) {
            if(!ClientItemFaults.failed(token,failure))throwUnchecked(failure);
            forge.leftHeight=left;forge.rightHeight=right;return;
        }
        ClientItemFaults.leave(token);
    }
    public static Object beginEvent(Object listener,Class<?> event,Object context) {
        pruneCallbacks();Key key=new Key("render-event",new Identity(listener),event);
        return disabled.contains(key)?null:ClientItemFaults.openDraw(key,context,listener);
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
        catch(Throwable failure) {current.broken=true;if(!ClientItemFaults.failed(token,failure))throwUnchecked(failure);}
    }
    public static void particle(Particle particle,VertexConsumer vertices,Camera camera,float tick) {
        Layer current=layer.get();if(current!=null&&current.broken)return;
        Object token=begin("particle",particle,null,null);if(token==null)return;
        try {particle.render(vertices,camera,tick);}
        catch(Throwable failure) {
            if(!ClientItemFaults.failed(token,failure))throwUnchecked(failure);
            // A partial vertex cannot be submitted. Discard this unfinished layer batch, then
            // restart its real begin hook so the next healthy particle can still render.
            if(current!=null) {
                ((ClientItemFaults.BufferAbort)current.buffer).ronova$abortItemBatch();
                try {current.type.begin(current.buffer,current.textures);}
                catch(Throwable restart) {
                    current.broken=true;
                    if(!ClientItemFaults.failed(current.token,restart))throwUnchecked(restart);
                }
            }
            return;
        }
        ClientItemFaults.leave(token);
    }
    public static void particleEnd(ParticleRenderType type,Tesselator tesselator) {
        Layer current=layer.get();layer.remove();
        if(current==null) {type.end(tesselator);return;}
        if(current.broken)return;
        try {type.end(tesselator);}
        catch(Throwable failure) {if(!ClientItemFaults.failed(current.token,failure))throwUnchecked(failure);return;}
        ClientItemFaults.leave(current.token);
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
}
