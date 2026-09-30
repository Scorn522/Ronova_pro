package dev.ronova.pro.validation;

import com.mojang.blaze3d.shaders.Uniform;
import com.mojang.blaze3d.vertex.*;
import java.util.*;
import net.minecraft.client.*;
import net.minecraft.client.gui.*;
import net.minecraft.client.gui.components.Renderable;
import net.minecraft.client.gui.screens.*;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.client.particle.*;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.*;
import net.minecraft.world.inventory.tooltip.TooltipComponent;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.*;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.*;
import net.minecraftforge.eventbus.api.*;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;

/** Ordinary draw calls with injected business failures. Never installs quarantine or writes a production receipt. */
@Mod.EventBusSubscriber(modid="pro_fixture",bus=Mod.EventBusSubscriber.Bus.MOD,value=Dist.CLIENT)
public final class ClientDrawFixture {
    public static boolean probing;
    private static int fonts,entities,blocks,widgets,healthyWidgets,badHud,goodHud,badParticles,goodParticles,layerBegins;
    private static final int[] tips=new int[3];
    private static final ResourceLocation BAD_FONT=new ResourceLocation("pro_fixture","effect_font");
    private static final AssertionError FATAL=new AssertionError("draw fixture fatal");
    private record Tip(int mode) implements TooltipComponent { }
    private static boolean enabled() {return Boolean.getBoolean("ronova.pro.validation")&&"fault-isolation".equals(System.getProperty("ronova.pro.fixture"));}
    private static void check(boolean good,String reason) {if(!good)throw new AssertionError(reason);}
    @SubscribeEvent public static void tooltipFactory(RegisterClientTooltipComponentFactoriesEvent event) {
        if(enabled())event.register(Tip.class,tip->new ClientTooltipComponent() {
            public int getHeight() {return 12;}
            public int getWidth(Font font) {if(tip.mode==0){tips[0]++;throw new NullPointerException("fixture tooltip width");}return 35;}
            public void renderText(Font font,int x,int y,Matrix4f pose,MultiBufferSource.BufferSource buffers) {
                if(tip.mode==1) {tips[1]++;buffers.getBuffer(RenderType.lines()).vertex(1,2,3);throw new IllegalStateException("fixture tooltip text");}
            }
            public void renderImage(Font font,int x,int y,GuiGraphics gui) {
                if(tip.mode==2) {tips[2]++;gui.pose().pushPose();gui.enableScissor(0,0,4,4);throw new IllegalArgumentException("fixture tooltip image");}
            }
        });
    }
    @SubscribeEvent public static void hud(RegisterGuiOverlaysEvent event) {
        if(!enabled())return;
        event.registerAboveAll("broken_draw",(forge,gui,tick,w,h)->{
            if(!probing)return;badHud++;forge.leftHeight+=500;gui.pose().pushPose();throw new NullPointerException("fixture HUD");
        });
        event.registerAboveAll("healthy_draw",(forge,gui,tick,w,h)->{if(probing){goodHud++;check(forge.leftHeight<500,"HUD height leaked");}});
    }
    public static void font(FormattedCharSequence text) {
        if(!probing)return;boolean[] bad={false};text.accept((i,style,c)->{if(BAD_FONT.equals(style.getFont()))bad[0]=true;return true;});
        if(bad[0]) {fonts++;Uniform cosmicExternalScale=null;cosmicExternalScale.set(1F);}
    }
    public static void entity(Entity entity,PoseStack pose) {
        if(probing&&entity.getType()==EntityType.CHICKEN) {entities++;pose.pushPose();throw new IllegalArgumentException("fixture entity renderer");}
    }
    public static void block(BlockEntity block,PoseStack pose) {
        if(probing&&block.getType()==BlockEntityType.CHEST) {blocks++;pose.pushPose();throw new NullPointerException("fixture block renderer");}
    }
    private static final class Widgets extends Screen {
        Widgets() {super(Component.literal("fixture widgets"));}
        protected void init() {
            addRenderableOnly((gui,x,y,tick)->{widgets++;gui.pose().pushPose();throw new NullPointerException("fixture widget");});
            addRenderableOnly((gui,x,y,tick)->healthyWidgets++);
        }
    }
    private static final class BrokenScreen extends Screen {
        int calls;BrokenScreen() {super(Component.literal("fixture broken screen"));}
        public void render(GuiGraphics gui,int x,int y,float tick) {calls++;gui.pose().pushPose();throw new NullPointerException("fixture screen");}
    }
    public static void gui(Minecraft mc) {
        probing=true;GuiGraphics gui=new GuiGraphics(mc,mc.renderBuffers().bufferSource());
        Matrix4f original=new Matrix4f(gui.pose().last().pose());
        for(int repeat=0;repeat<3;repeat++)for(int mode=0;mode<3;mode++)
            gui.renderTooltip(mc.font,List.of(Component.literal("own tooltip")),Optional.of(new Tip(mode)),new ItemStack(new Item[]{Items.PAPER,Items.FEATHER,Items.LEATHER}[mode]),10,10);
        check(Arrays.equals(tips,new int[]{1,1,1}),"tooltip width/text/image quarantine failed: "+Arrays.toString(tips));
        check(gui.pose().clear()&&original.equals(gui.pose().last().pose()),"tooltip pose leaked");
        check(!org.lwjgl.opengl.GL11.glIsEnabled(org.lwjgl.opengl.GL11.GL_SCISSOR_TEST),"tooltip scissor leaked");
        gui.enableScissor(0,0,10,10);gui.disableScissor();
        var text=Component.literal("effect").withStyle(style->style.withFont(BAD_FONT)).getVisualOrderText();
        for(int i=0;i<3;i++) {
            gui.drawString(mc.font,text,1,1,0xFFFFFF);
            new net.minecraft.client.gui.screens.inventory.tooltip.ClientTextTooltip(text).renderText(mc.font,1,1,new Matrix4f(),mc.renderBuffers().bufferSource());
            check(gui.drawString(mc.font,"ordinary neighbour",1,20,0xFFFFFF)>1,"ordinary font was disabled");
        }
        check(fonts==1,"effect font retried across surfaces: "+fonts);
        Widgets widgetsScreen=new Widgets();widgetsScreen.init(mc,mc.getWindow().getGuiScaledWidth(),mc.getWindow().getGuiScaledHeight());
        for(int i=0;i<3;i++)widgetsScreen.renderWithTooltip(gui,0,0,0);
        check(widgets==1&&healthyWidgets==3&&gui.pose().clear(),"widget isolation/neighbour/pose failed");
        BrokenScreen screen=new BrokenScreen();screen.renderWithTooltip(gui,0,0,0);screen.renderWithTooltip(gui,0,0,0);
        check(screen.calls==1&&gui.pose().clear(),"screen fallback/retry failed");
        var bus=BusBuilder.builder().build();int[] bad={0},good={0};
        bus.addListener(EventPriority.HIGH,false,RenderGuiEvent.Pre.class,e->{bad[0]++;e.setCanceled(true);e.getGuiGraphics().pose().pushPose();throw new NullPointerException("fixture render event");});
        bus.addListener(EventPriority.LOW,false,RenderGuiEvent.Pre.class,e->good[0]++);
        for(int i=0;i<3;i++)check(!bus.post(new RenderGuiEvent.Pre(mc.getWindow(),gui,0)),"cancel state leaked from failed callback");
        check(bad[0]==1&&good[0]==3&&gui.pose().clear(),"event quarantine/neighbour/pose failed");
        bus=BusBuilder.builder().build();bus.addListener(EventPriority.NORMAL,false,RenderGuiEvent.Pre.class,e->{throw FATAL;});
        try {bus.post(new RenderGuiEvent.Pre(mc.getWindow(),gui,0));throw new AssertionError("fatal event swallowed");}
        catch(AssertionError actual) {check(actual==FATAL,"fatal event identity changed");}
        Screen previous=mc.screen;mc.setScreen(widgetsScreen);int[] clicks={0};
        Screen.wrapScreenError(()->{clicks[0]++;throw new NullPointerException("fixture click after side effect");},"mouse click",widgetsScreen.getClass().getName());
        Screen.wrapScreenError(()->clicks[0]++,"queued mouse click",widgetsScreen.getClass().getName());
        check(clicks[0]==1,"failed input was replayed or queued callbacks kept running");mc.setScreen(previous);
        mc.renderBuffers().bufferSource().endBatch();
        mc.setScreen(screen);screen.renderWithTooltip(gui,0,0,0);
        System.out.println("CLIENT_DRAW_CASE_PASS tooltip_font_widget_screen_event_cleanup_and_neighbours");
    }
    public static void escape(Minecraft mc) {
        check(mc.screen!=null&&mc.screen.getClass().getName().endsWith("ClientDrawFaults$FaultScreen"),"failed screen had no visible escape route");
        for(var child:mc.screen.children())if(child instanceof net.minecraft.client.gui.components.Button button) {button.onPress();break;}
        check(mc.screen instanceof TitleScreen,"screen escape did not return to title");
        System.out.println("CLIENT_DRAW_CASE_PASS input_no_replay_and_visible_screen_escape");
    }
    private static final ParticleRenderType TYPE=new ParticleRenderType() {
        public void begin(BufferBuilder b,TextureManager t) {RenderSystemBridge.shader();b.begin(VertexFormat.Mode.QUADS,DefaultVertexFormat.POSITION_COLOR);}
        public void end(Tesselator t) {t.end();}
    };
    private static final ParticleRenderType BAD_LAYER=new ParticleRenderType() {
        public void begin(BufferBuilder b,TextureManager t) {layerBegins++;b.begin(VertexFormat.Mode.QUADS,DefaultVertexFormat.POSITION_COLOR);throw new NullPointerException("fixture layer begin");}
        public void end(Tesselator t) {throw new AssertionError("failed particle layer was ended");}
    };
    private static final class RenderSystemBridge {
        static void shader() {com.mojang.blaze3d.systems.RenderSystem.setShader(GameRenderer::getPositionColorShader);}
    }
    private static class GoodParticle extends Particle {
        private final ParticleRenderType type;
        GoodParticle(Minecraft mc,ParticleRenderType type) {super(mc.level,mc.player.getX(),mc.player.getY(),mc.player.getZ());this.type=type;lifetime=200;}
        public ParticleRenderType getRenderType() {return type;}
        public boolean shouldCull() {return false;}
        public void render(VertexConsumer b,Camera c,float t) {goodParticles++;for(int i=0;i<4;i++)b.vertex(i&1,(i>>1)&1,0).color(255,255,255,255).endVertex();}
    }
    private static final class BadParticle extends GoodParticle {
        BadParticle(Minecraft mc) {super(mc,TYPE);}
        public void render(VertexConsumer b,Camera c,float t) {badParticles++;b.vertex(1,2,3);throw new NullPointerException("fixture partial particle");}
    }
    public static void world(Minecraft mc) {
        PoseStack pose=new PoseStack();var buffers=mc.renderBuffers().bufferSource();
        Entity bad=EntityType.CHICKEN.create(mc.level);
        BlockEntity chest=new ChestBlockEntity(BlockPos.ZERO,Blocks.CHEST.defaultBlockState());
        for(int i=0;i<3;i++) {
            mc.getEntityRenderDispatcher().render(bad,0,0,0,0,0,pose,buffers,15728880);
            mc.getBlockEntityRenderDispatcher().render(chest,0,pose,buffers);
        }
        check(entities==1&&blocks==1&&pose.clear(),"entity/block render quarantine and pose failed");
        mc.particleEngine.add(new GoodParticle(mc,TYPE));mc.particleEngine.add(new BadParticle(mc));mc.particleEngine.add(new GoodParticle(mc,TYPE));
        mc.particleEngine.add(new GoodParticle(mc,BAD_LAYER));
        System.out.println("CLIENT_DRAW_CASE_PASS entity_and_block_renderer_cleanup");
    }
    public static void finish() {
        check(badHud==1&&goodHud>1,"HUD not exercised or retried: "+badHud+"/"+goodHud);
        check(badParticles==1&&goodParticles>1&&layerBegins==1,"particle draw/layer not isolated: "+badParticles+"/"+goodParticles+"/"+layerBegins);
        System.out.println("CLIENT_DRAW_CASE_PASS hud_particle_batch_restart_and_neighbours");
    }
}
