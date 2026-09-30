package dev.ronova.pro.validation;

import com.mojang.blaze3d.shaders.*;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.*;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.client.renderer.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceProvider;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterShadersEvent;
import net.minecraftforge.eventbus.api.*;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL11;
import net.minecraft.world.item.*;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.texture.OverlayTexture;

/** Creates real bad shader constructors/callbacks. All isolation and cleanup belongs to production. */
@Mod.EventBusSubscriber(modid="pro_fixture",bus=Mod.EventBusSubscriber.Bus.MOD,value=Dist.CLIENT)
public final class ClientFaultFixture {
    private static int badRegistrations,partialPublished,healthyPublished,badPublished,drawCalls,supplierCalls;
    private static int failedProgramId,partialProgramId,healthyBaseId,ticks,worldTicks,stage;
    private static boolean finished,cleanBeforeNext,borrowedBeforeNext;
    private static Program borrowedVertex;
    private static ShaderInstance healthy,drawFault;
    private static CompletableFuture<Void> reload;
    private static final List<String> passes=new ArrayList<>();
    private static boolean itemProbe;
    private static int failedItems,healthyItems;
    private static int aliasCalls;
    public interface ItemAliasProbe {
        void fixture$renderAlias(ItemStack item,ItemDisplayContext context,boolean left,PoseStack pose,MultiBufferSource buffers,
                                 int light,int overlay,net.minecraft.client.resources.model.BakedModel model);
    }
    public static void aliasCalled() { aliasCalls++; }
    private static final AssertionError fatalItem=new AssertionError("fixture fatal item error");
    private static boolean enabled() { return Boolean.getBoolean("ronova.pro.validation")&&"fault-isolation".equals(System.getProperty("ronova.pro.fixture")); }

    @SubscribeEvent(priority=EventPriority.HIGHEST)
    public static void broken(RegisterShadersEvent event)throws IOException {
        if(!enabled())return;
        badRegistrations++;healthyBaseId=GameRenderer.getPositionColorShader()==null?0:GameRenderer.getPositionColorShader().getId();
        ShaderInstance partial=new ShaderInstance(event.getResourceProvider(),new ResourceLocation("pro_fixture","partial"),DefaultVertexFormat.POSITION_COLOR);
        borrowedVertex=partial.getVertexProgram();
        partialProgramId=partial.getId();event.registerShader(partial,s->partialPublished++);
        new BrokenConstructor(event.getResourceProvider());
    }
    @SubscribeEvent(priority=EventPriority.LOW)
    public static void publication(RegisterShadersEvent event)throws IOException {
        if(!enabled())return;
        cleanBeforeNext=failedProgramId>0 && partialProgramId>0 && !GL20.glIsProgram(failedProgramId) && !GL20.glIsProgram(partialProgramId);
        borrowedBeforeNext=(healthyBaseId==0 || GL20.glIsProgram(healthyBaseId))
                && Program.Type.VERTEX.getPrograms().get(borrowedVertex.getName())==borrowedVertex;
        event.registerShader(new ShaderInstance(event.getResourceProvider(),new ResourceLocation("pro_fixture","publication"),DefaultVertexFormat.POSITION_COLOR),s->{
            badPublished++;throw new IllegalStateException("fixture: failed shader publication");
        });
    }
    @SubscribeEvent(priority=EventPriority.LOWEST)
    public static void healthy(RegisterShadersEvent event)throws IOException {
        if(!enabled())return;
        event.registerShader(new ShaderInstance(event.getResourceProvider(),new ResourceLocation("pro_fixture","healthy"),DefaultVertexFormat.POSITION_COLOR),s->{healthy=s;healthyPublished++;});
        event.registerShader(new BrokenDraw(event.getResourceProvider()),s->drawFault=s);
    }
    private static final class FancyUniform extends Uniform {
        FancyUniform() { super("unused",4,1,null); }
    }
    private static final class BrokenConstructor extends ShaderInstance {
        BrokenConstructor(ResourceProvider resources)throws IOException { super(resources,new ResourceLocation("pro_fixture","broken"),DefaultVertexFormat.POSITION_COLOR); }
        @Override public FancyUniform getUniform(String name) {
            failedProgramId=getId();return (FancyUniform)super.getUniform(name);
        }
    }
    private static final class BrokenDraw extends ShaderInstance {
        BrokenDraw(ResourceProvider resources)throws IOException { super(resources,new ResourceLocation("pro_fixture","draw_fault"),DefaultVertexFormat.POSITION_COLOR); }
        @Override public void apply() { drawCalls++;throw new ClassCastException("fixture: draw apply failure"); }
    }
    public static final class ProbeEvent extends Event { public ProbeEvent() { } }
    private static void check(boolean okay,String reason) { if(!okay)throw new AssertionError(reason); }
    private static void pass(String name) { passes.add(name);System.out.println("CLIENT_FAULT_CASE_PASS "+name); }
    private static void draw(ShaderInstance shader) {
        RenderSystem.setShader(()->shader);
        BufferBuilder buffer=new BufferBuilder(256);buffer.begin(VertexFormat.Mode.QUADS,DefaultVertexFormat.POSITION_COLOR);
        buffer.vertex(0,0,0).color(255,255,255,255).endVertex();buffer.vertex(0,1,0).color(255,255,255,255).endVertex();
        buffer.vertex(1,1,0).color(255,255,255,255).endVertex();buffer.vertex(1,0,0).color(255,255,255,255).endVertex();
        BufferBuilder.RenderedBuffer rendered=buffer.end();BufferUploader.drawWithShader(rendered);
        // Released buffers cannot be used twice; the production missing-shader path must consume this exact buffer.
        try { rendered.release();throw new AssertionError("draw buffer was not released"); }
        catch(IllegalStateException expected) { }
    }
    private static void fatalUnchanged() {
        var bus=BusBuilder.builder().build();LinkageError fatal=new LinkageError("fixture fatal, must propagate");
        bus.addListener(EventPriority.NORMAL,false,RegisterShadersEvent.class,e->{throw fatal;});
        try { bus.post(new RegisterShadersEvent(Minecraft.getInstance().getResourceManager(),new ArrayList<>()));throw new AssertionError("fatal swallowed"); }
        catch(LinkageError actual) { check(actual==fatal,"fatal identity changed"); }
        bus=BusBuilder.builder().build();RuntimeException ordinary=new IllegalStateException("unselected event");
        bus.addListener(EventPriority.NORMAL,false,ProbeEvent.class,e->{throw ordinary;});
        try { bus.post(new ProbeEvent());throw new AssertionError("unselected exception swallowed"); }
        catch(RuntimeException actual) { check(actual==ordinary,"unselected identity changed"); }
    }
    public static void injectedItem(ItemStack item,PoseStack pose,MultiBufferSource buffers) {
        if(!enabled() || !itemProbe)return;
        if(item.is(Items.REDSTONE))throw fatalItem;
        if(item.is(Items.BLAZE_ROD)) { healthyItems++;return; }
        if(!item.is(Items.STICK) && !item.is(Items.BONE))return;
        failedItems++;
        pose.pushPose();pose.translate(77,88,99);
        RenderSystem.getModelViewStack().pushPose();RenderSystem.getModelViewStack().translate(12,34,56);RenderSystem.applyModelViewMatrix();
        RenderSystem.setShaderColor(.1F,.2F,.3F,.4F);RenderSystem.disableCull();RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);RenderSystem.enableBlend();
        buffers.getBuffer(RenderType.lines()).vertex(0,0,0); // deliberately unfinished vertex and batch
        Uniform cosmicTime=null;cosmicTime.set(1.0F);
    }
    private static void itemDraws(Minecraft mc) {
        itemProbe=true;
        var buffers=mc.renderBuffers().bufferSource();GuiGraphics gui=new GuiGraphics(mc,buffers);
        var guiBefore=new org.joml.Matrix4f(gui.pose().last().pose());
        gui.renderItem(new ItemStack(Items.STICK),10,10);
        check(failedItems==1 && guiBefore.equals(gui.pose().last().pose()),"GUI injected item failure did not leave clean caller pose");
        PoseStack poses=new PoseStack();poses.translate(1,2,3);var before=new org.joml.Matrix4f(poses.last().pose());
        float[] color=RenderSystem.getShaderColor().clone();boolean depth=GL11.glIsEnabled(GL11.GL_DEPTH_TEST),blend=GL11.glIsEnabled(GL11.GL_BLEND),cull=GL11.glIsEnabled(GL11.GL_CULL_FACE);
        int modelViewDepth=((dev.ronova.pro.ClientItemFaults.PoseAccess)RenderSystem.getModelViewStack()).ronova$poses().size();
        mc.getItemRenderer().renderStatic(new ItemStack(Items.BONE),ItemDisplayContext.FIRST_PERSON_RIGHT_HAND,15728880,OverlayTexture.NO_OVERLAY,poses,buffers,mc.level,0);
        check(failedItems==2 && poses.clear() && before.equals(poses.last().pose()),"held item failure corrupted caller pose");
        check(Arrays.equals(color,RenderSystem.getShaderColor()) && depth==GL11.glIsEnabled(GL11.GL_DEPTH_TEST)
                && blend==GL11.glIsEnabled(GL11.GL_BLEND) && cull==GL11.glIsEnabled(GL11.GL_CULL_FACE),"item GL state was not restored");
        check(((dev.ronova.pro.ClientItemFaults.PoseAccess)RenderSystem.getModelViewStack()).ronova$poses().size()==modelViewDepth,"global model-view stack leaked");
        for(int i=0;i<20;i++) {
            gui.renderItem(new ItemStack(Items.STICK),10,10);
            mc.getItemRenderer().renderStatic(new ItemStack(Items.BONE),ItemDisplayContext.GROUND,15728880,OverlayTexture.NO_OVERLAY,poses,buffers,mc.level,0);
            gui.renderItem(new ItemStack(Items.BLAZE_ROD),30,10);
        }
        check(failedItems==2 && healthyItems==20,"item isolation affected neighbours or retried bad rendering");
        ItemStack failedItem=new ItemStack(Items.STICK);
        ((ItemAliasProbe)mc.getItemRenderer()).fixture$renderAlias(failedItem,ItemDisplayContext.GUI,false,poses,buffers,
                15728880,OverlayTexture.NO_OVERLAY,mc.getItemRenderer().getModel(failedItem,mc.level,null,0));
        check(aliasCalls==1,"same-signature helper was mistaken for the render entry");
        pass("same_signature_foreign_helper_retained_real_render_entry_guarded");
        buffers.endBatch();
        try {
            mc.getItemRenderer().renderStatic(new ItemStack(Items.REDSTONE),ItemDisplayContext.GUI,15728880,OverlayTexture.NO_OVERLAY,poses,buffers,mc.level,0);
            throw new AssertionError("fatal item failure swallowed");
        } catch(AssertionError actual) { check(actual==fatalItem,"fatal item changed"); }
        pass("injected_item_callbacks_gui_held_ground_partial_batch_pose_gl_neighbour_and_fatal");
    }
    public static void tick() {
        if(finished)return;
        Minecraft mc=Minecraft.getInstance();ticks++;
        try {
            if(ticks>2400)throw new AssertionError("timeout stage="+stage+" screen="+mc.screen);
            if(stage==0 && mc.screen instanceof TitleScreen && mc.getOverlay()==null) {
                check(badRegistrations==1 && partialPublished==0,"failed registration publication/retry");
                check(cleanBeforeNext,"partial constructor/registration program leaked before next listener");
                check(healthyPublished==1 && healthy!=null && GL20.glIsProgram(healthy.getId()),"healthy registration skipped");
                check(borrowedBeforeNext && GL20.glIsProgram(GameRenderer.getPositionColorShader().getId()),"borrowed/current vanilla program damaged");
                check(badPublished==1,"publication fault never exercised");pass("registration_partial_cleanup_healthy_listener_and_title");
                ShaderInstance previous=RenderSystem.getShader();
                try {
                    draw(null);draw(drawFault);draw(drawFault);draw(healthy);
                    check(drawCalls==1,"bad draw retried");
                    RenderStateShard.ShaderStateShard state=new RenderStateShard.ShaderStateShard(()->{supplierCalls++;throw new IllegalArgumentException("fixture supplier failure");});
                    state.setupRenderState();draw(RenderSystem.getShader());state.setupRenderState();
                    check(supplierCalls==1,"bad supplier retried");
                } finally { RenderSystem.setShader(()->previous); }
                pass("missing_shader_buffer_release_draw_fault_and_supplier_quarantine");fatalUnchanged();pass("fatal_and_unselected_events_propagate");
                reload=mc.reloadResourcePacks();stage=1;
            } else if(stage==1 && reload.isDone() && mc.getOverlay()==null) {
                reload.join();check(badRegistrations==1 && badPublished==1 && partialPublished==0,"failed callback retried on reload");
                check(healthyPublished==2 && GL20.glIsProgram(healthy.getId()),"healthy reload failed");pass("reload_keeps_failed_callbacks_disabled_and_healthy_callbacks_working");
                ShaderInstance previous=RenderSystem.getShader();try { draw(drawFault);check(drawCalls==1,"failed shader retried after reload"); }
                finally { RenderSystem.setShader(()->previous); }
                itemDraws(mc);ClientDrawFixture.gui(mc);pass("all_gui_draw_boundaries");stage=3;
            } else if(stage==3) {
                ClientDrawFixture.escape(mc);pass("input_and_screen_escape");
                ConnectScreen.startConnecting(mc.screen,mc,new ServerAddress("127.0.0.1",25892),new ServerData("Own fault fixture","127.0.0.1:25892",false),false);stage=2;
            } else if(stage==2 && mc.level!=null && mc.player!=null) {
                if(++worldTicks==10)ClientDrawFixture.world(mc);
                if(worldTicks<80)return;
                ClientDrawFixture.finish();pass("entity_block_hud_particle_boundaries");
                check(mc.player.connection.getConnection().isConnected(),"world connection lost");pass("joined_isolated_world_and_rendered_80_ticks");
                finish(mc,"CLIENT_FAULT_ISOLATION_PASS\n"+String.join("\n",passes)+"\n"+dev.ronova.pro.ClientFaults.status());
            }
        } catch(Throwable failure) {
            failure.printStackTrace();finish(mc,"FAILED "+failure+"\n"+String.join("\n",passes));
        }
    }
    private static void finish(Minecraft mc,String result) {
        finished=true;try { Files.writeString(Path.of("fault-isolation-result.txt"),result); }
        catch(IOException failure) { failure.printStackTrace(); }
        System.out.println(result);mc.stop();
    }
}
