package dev.ronova.pro.mixin;

import dev.ronova.pro.ClientPresence;
import dev.ronova.pro.ClientWorldVisuals;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import java.nio.ByteBuffer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.particle.*;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.block.LiquidBlockRenderer;
import net.minecraft.client.renderer.block.ModelBlockRenderer;
import net.minecraft.client.renderer.blockentity.*;
import net.minecraft.client.renderer.chunk.RenderChunkRegion;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.texture.SpriteContents;
import net.minecraft.client.renderer.texture.SpriteLoader;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.resources.model.BakedModel;
import it.unimi.dsi.fastutil.floats.Float2FloatFunction;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraftforge.client.model.data.ModelData;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;

/** Client simulation and draw clocks use the current connection's exact world selection. */
public final class ClientWorldHooks {
    private ClientWorldHooks() {}

    @Mixin(ClientLevel.class)
    public static abstract class World {
        @Shadow private void tickPassenger(Entity vehicle,Entity passenger){throw new UnsupportedOperationException();}
        @Inject(method="tickTime",at=@At("HEAD"),cancellable=true)
        private void pro$time(CallbackInfo ci){if(ClientPresence.worldClockFrozen((ClientLevel)(Object)this))ci.cancel();}
        @Inject(method="tickNonPassenger",at=@At("HEAD"),cancellable=true)
        private void pro$entity(Entity entity,CallbackInfo ci){if(ClientPresence.worldFrozen(entity)){for(Entity passenger:entity.getPassengers())tickPassenger(entity,passenger);ci.cancel();}}
        @Inject(method="tickPassenger",at=@At("HEAD"),cancellable=true)
        private void pro$passenger(Entity vehicle,Entity passenger,CallbackInfo ci){if(ClientPresence.worldFrozen(passenger)){for(Entity nested:passenger.getPassengers())tickPassenger(passenger,nested);ci.cancel();}}
        @Inject(method="doAnimateTick",at=@At(value="INVOKE",target="Lnet/minecraft/core/BlockPos$MutableBlockPos;set(III)Lnet/minecraft/core/BlockPos$MutableBlockPos;",shift=At.Shift.AFTER),cancellable=true)
        private void pro$ambient(int x,int y,int z,int range,RandomSource random,Block marker,BlockPos.MutableBlockPos sampled,CallbackInfo ci){
            if(ClientPresence.worldFrozen((ClientLevel)(Object)this,sampled))ci.cancel();
        }
    }

    @Mixin(ClientPacketListener.class)
    public static abstract class TimePacket {
        @Inject(method={"handleLogin","handleRespawn"},at=@At(value="INVOKE",target="Lnet/minecraft/network/protocol/PacketUtils;ensureRunningOnSameThread(Lnet/minecraft/network/protocol/Packet;Lnet/minecraft/network/PacketListener;Lnet/minecraft/util/thread/BlockableEventLoop;)V",shift=At.Shift.AFTER))
        private void pro$handoff(CallbackInfo ci){ClientPresence.beginWorldHandoff(this);}
        @Redirect(method="handleSetTime",at=@At(value="INVOKE",target="Lnet/minecraft/client/multiplayer/ClientLevel;setGameTime(J)V"))
        private void pro$gameTime(ClientLevel level,long value){if(!ClientPresence.worldClockFrozen(level))level.setGameTime(value);}
        @Redirect(method="handleSetTime",at=@At(value="INVOKE",target="Lnet/minecraft/client/multiplayer/ClientLevel;setDayTime(J)V"))
        private void pro$dayTime(ClientLevel level,long value){if(!ClientPresence.worldClockFrozen(level))level.setDayTime(value);}
    }

    @Mixin(targets="net.minecraft.world.level.chunk.LevelChunk$BoundTickingBlockEntity")
    public static abstract class BlockTick {
        @Shadow @Final private BlockEntity blockEntity;
        @Inject(method="tick",at=@At("HEAD"),cancellable=true)
        private void pro$tick(CallbackInfo ci){if(blockEntity.getLevel()!=null&&blockEntity.getLevel().isClientSide&&ClientPresence.worldFrozen(blockEntity.getLevel(),blockEntity.getBlockPos()))ci.cancel();}
    }

    @Mixin(LevelRenderer.class)
    public static abstract class Render {
        @Shadow private int ticks;
        @ModifyVariable(method="renderEntity",at=@At("HEAD"),argsOnly=true,ordinal=0)
        private float pro$entityPartial(float value,Entity entity,double x,double y,double z,float partial,PoseStack pose,MultiBufferSource buffers){return ClientPresence.worldPartial(entity,value);}
        @Inject(method="renderSnowAndRain",at=@At("HEAD"))
        private void pro$weatherBegin(LightTexture light,float partial,double x,double y,double z,CallbackInfo ci){ClientPresence.beginWeather(ticks,partial,x,y,z);}
        @Inject(method="renderSnowAndRain",at=@At("RETURN"))
        private void pro$weatherEnd(LightTexture light,float partial,double x,double y,double z,CallbackInfo ci){ClientPresence.endWeather();}
        @Redirect(method="renderSnowAndRain",at=@At(value="INVOKE",target="Lnet/minecraft/world/level/biome/Biome;getPrecipitationAt(Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/level/biome/Biome$Precipitation;"))
        private Biome.Precipitation pro$weatherColumn(Biome biome,BlockPos pos){return ClientPresence.weatherColumn(biome,pos);}
        @Redirect(method="renderSnowAndRain",at=@At(value="INVOKE",target="Lcom/mojang/blaze3d/vertex/BufferBuilder;vertex(DDD)Lcom/mojang/blaze3d/vertex/VertexConsumer;"))
        private VertexConsumer pro$weatherVertex(BufferBuilder builder,double x,double y,double z){return ClientPresence.weatherVertex(builder,x,y,z);}
        @Redirect(method="tickRain",at=@At(value="INVOKE",target="Lnet/minecraft/world/level/biome/Biome;getPrecipitationAt(Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/level/biome/Biome$Precipitation;"))
        private Biome.Precipitation pro$rainTarget(Biome biome,BlockPos target){
            return ClientPresence.worldFrozen(Minecraft.getInstance().level,target.below())?Biome.Precipitation.NONE:biome.getPrecipitationAt(target);
        }
        // The first field read belongs to the custom-dimension callback; the second drives vanilla drift.
        @Redirect(method="renderClouds",at=@At(value="FIELD",target="Lnet/minecraft/client/renderer/LevelRenderer;ticks:I",ordinal=1))
        private int pro$cloudClock(LevelRenderer renderer){return (int)Math.floor(ClientPresence.worldRendererClock(ticks+Minecraft.getInstance().getFrameTime()));}
        @ModifyVariable(method="renderClouds",at=@At(value="INVOKE",target="Lnet/minecraft/client/renderer/DimensionSpecialEffects;getCloudHeight()F",shift=At.Shift.AFTER),argsOnly=true,ordinal=0)
        private float pro$cloudPartial(float partial){float clock=ClientPresence.worldRendererClock(ticks+partial);return clock-(float)Math.floor(clock);}
    }

    @Mixin(BlockEntityRenderDispatcher.class)
    public static abstract class BlockDraw {
        @ModifyVariable(method="render(Lnet/minecraft/world/level/block/entity/BlockEntity;FLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;)V",at=@At("HEAD"),argsOnly=true,ordinal=0)
        private float pro$partial(float value,BlockEntity entity,float partial,PoseStack pose,MultiBufferSource buffers){return ClientPresence.worldPartial(entity,value);}
    }

    @Mixin(ChestRenderer.class)
    public static abstract class ChestOpening {
        // T extends BlockEntity & LidBlockEntity erases to BlockEntity in the actual public render.
        // Keep its exact descriptor: the private render overload draws the models after this consumer.
        @Redirect(method="render(Lnet/minecraft/world/level/block/entity/BlockEntity;FLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;II)V",at=@At(value="INVOKE",target="Lit/unimi/dsi/fastutil/floats/Float2FloatFunction;get(F)F",remap=false),require=1,allow=1)
        private float pro$opening(Float2FloatFunction combiner,float interpolation,BlockEntity entity,float partial,PoseStack pose,MultiBufferSource buffers,int light,int overlay){
            float combined=combiner.get(interpolation);
            return ClientPresence.worldChestOpening(entity,combined);
        }
    }

    @Mixin(BeaconRenderer.class)
    public static abstract class Beacon {
        @Redirect(method="render(Lnet/minecraft/world/level/block/entity/BeaconBlockEntity;FLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;II)V",at=@At(value="INVOKE",target="Lnet/minecraft/world/level/Level;getGameTime()J"))
        private long pro$clock(Level level,BeaconBlockEntity entity,float partial,PoseStack pose,MultiBufferSource buffers,int light,int overlay){return ClientPresence.worldGameTime(entity,level.getGameTime());}
    }
    @Mixin(BannerRenderer.class)
    public static abstract class Banner {
        @Redirect(method="render(Lnet/minecraft/world/level/block/entity/BannerBlockEntity;FLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;II)V",at=@At(value="INVOKE",target="Lnet/minecraft/world/level/Level;getGameTime()J"))
        private long pro$clock(Level level,BannerBlockEntity entity,float partial,PoseStack pose,MultiBufferSource buffers,int light,int overlay){return ClientPresence.worldGameTime(entity,level.getGameTime());}
    }
    @Mixin(TheEndGatewayRenderer.class)
    public static abstract class Gateway {
        @Redirect(method="render(Lnet/minecraft/world/level/block/entity/TheEndGatewayBlockEntity;FLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;II)V",at=@At(value="INVOKE",target="Lnet/minecraft/world/level/Level;getGameTime()J"))
        private long pro$clock(Level level,TheEndGatewayBlockEntity entity,float partial,PoseStack pose,MultiBufferSource buffers,int light,int overlay){return ClientPresence.worldGameTime(entity,level.getGameTime());}
    }

    @Mixin(TheEndPortalRenderer.class)
    public static abstract class PortalSurface {
        // Gateway calls this same body; its virtual renderType still chooses the gateway shader.
        @Redirect(method="render(Lnet/minecraft/world/level/block/entity/TheEndPortalBlockEntity;FLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;II)V",at=@At(value="INVOKE",target="Lnet/minecraft/client/renderer/MultiBufferSource;getBuffer(Lnet/minecraft/client/renderer/RenderType;)Lcom/mojang/blaze3d/vertex/VertexConsumer;"),require=1,allow=1)
        private VertexConsumer pro$buffer(MultiBufferSource source,RenderType type,TheEndPortalBlockEntity entity,float partial,PoseStack pose,MultiBufferSource buffers,int light,int overlay){
            return ClientWorldVisuals.objectBuffer(source,type,entity);
        }
    }

    @Mixin(EntityRenderDispatcher.class)
    public static abstract class EntityFlame {
        @Redirect(method="renderFlame(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;Lnet/minecraft/world/entity/Entity;)V",at=@At(value="INVOKE",target="Lnet/minecraft/client/renderer/MultiBufferSource;getBuffer(Lnet/minecraft/client/renderer/RenderType;)Lcom/mojang/blaze3d/vertex/VertexConsumer;"),require=1,allow=1)
        private VertexConsumer pro$buffer(MultiBufferSource source,RenderType type,PoseStack pose,MultiBufferSource buffers,Entity entity){
            return ClientWorldVisuals.objectBuffer(source,type,entity);
        }
    }

    @Mixin(ScreenEffectRenderer.class)
    public static abstract class ScreenFire {
        @Redirect(method="renderFire(Lnet/minecraft/client/Minecraft;Lcom/mojang/blaze3d/vertex/PoseStack;)V",at=@At(value="INVOKE",target="Lcom/mojang/blaze3d/vertex/BufferUploader;drawWithShader(Lcom/mojang/blaze3d/vertex/BufferBuilder$RenderedBuffer;)V"),require=1,allow=1)
        private static void pro$draw(BufferBuilder.RenderedBuffer buffer,Minecraft client,PoseStack pose){
            ClientWorldVisuals.objectDraw(client.player,()->BufferUploader.drawWithShader(buffer));
        }
    }

    @Mixin(BlockRenderDispatcher.class)
    public static abstract class TerrainSource {
        // Forge's ModelData/RenderType overload is the actual tessellation call, not the vanilla delegate.
        @Redirect(method="renderBatched(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/BlockAndTintGetter;Lcom/mojang/blaze3d/vertex/PoseStack;Lcom/mojang/blaze3d/vertex/VertexConsumer;ZLnet/minecraft/util/RandomSource;Lnet/minecraftforge/client/model/data/ModelData;Lnet/minecraft/client/renderer/RenderType;)V",remap=false,at=@At(value="INVOKE",target="Lnet/minecraft/client/renderer/block/ModelBlockRenderer;tesselateBlock(Lnet/minecraft/world/level/BlockAndTintGetter;Lnet/minecraft/client/resources/model/BakedModel;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/core/BlockPos;Lcom/mojang/blaze3d/vertex/PoseStack;Lcom/mojang/blaze3d/vertex/VertexConsumer;ZLnet/minecraft/util/RandomSource;JILnet/minecraftforge/client/model/data/ModelData;Lnet/minecraft/client/renderer/RenderType;)V",remap=false),require=1,allow=1)
        private void pro$block(ModelBlockRenderer renderer,BlockAndTintGetter view,BakedModel model,BlockState state,BlockPos position,PoseStack pose,VertexConsumer vertices,boolean checkSides,RandomSource random,long seed,int overlay,ModelData data,RenderType layer){
            ClientWorldVisuals.block(vertices,view,position,()->renderer.tesselateBlock(view,model,state,position,pose,vertices,checkSides,random,seed,overlay,data,layer));
        }
        @Redirect(method="renderLiquid(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/BlockAndTintGetter;Lcom/mojang/blaze3d/vertex/VertexConsumer;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/material/FluidState;)V",at=@At(value="INVOKE",target="Lnet/minecraft/client/renderer/block/LiquidBlockRenderer;tesselate(Lnet/minecraft/world/level/BlockAndTintGetter;Lnet/minecraft/core/BlockPos;Lcom/mojang/blaze3d/vertex/VertexConsumer;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/material/FluidState;)V"),require=1,allow=1)
        private void pro$liquid(LiquidBlockRenderer renderer,BlockAndTintGetter view,BlockPos position,VertexConsumer vertices,BlockState state,FluidState fluid){
            ClientWorldVisuals.block(vertices,view,position,()->renderer.tesselate(view,position,vertices,state,fluid));
        }
    }

    @Mixin(RenderChunkRegion.class)
    public interface ChunkRegion {
        @Accessor("level") Level pro$level();
    }
    @Mixin(BufferBuilder.class)
    public interface BuilderLabels {
        @Accessor("vertices") int pro$vertices();
    }
    @Mixin(BufferBuilder.class)
    public static abstract class BuilderBatches {
        @Inject(method="end()Lcom/mojang/blaze3d/vertex/BufferBuilder$RenderedBuffer;",at=@At("RETURN"),require=1,allow=1)
        private void pro$end(CallbackInfoReturnable<BufferBuilder.RenderedBuffer> cir){
            ClientWorldVisuals.stored((BufferBuilder)(Object)this,cir.getReturnValue());
        }
        // Chunk compilation uses this method; its two returns are the empty and stored paths.
        @Inject(method="endOrDiscardIfEmpty()Lcom/mojang/blaze3d/vertex/BufferBuilder$RenderedBuffer;",at=@At("RETURN"),require=1,allow=2)
        private void pro$endOrDiscard(CallbackInfoReturnable<BufferBuilder.RenderedBuffer> cir){
            BufferBuilder builder=(BufferBuilder)(Object)this;BufferBuilder.RenderedBuffer buffer=cir.getReturnValue();
            if(buffer==null)ClientWorldVisuals.discard(builder);else ClientWorldVisuals.stored(builder,buffer);
        }
        // clear() delegates to discard(), including release of the final outstanding batch.
        @Inject(method="discard()V",at=@At("RETURN"),require=1,allow=1)
        private void pro$discard(CallbackInfo ci){ClientWorldVisuals.discard((BufferBuilder)(Object)this);}
    }
    @Mixin(BufferBuilder.RenderedBuffer.class)
    public static abstract class RenderedBatch {
        @Inject(method="release()V",at=@At("RETURN"),require=1,allow=1)
        private void pro$release(CallbackInfo ci){ClientWorldVisuals.released((BufferBuilder.RenderedBuffer)(Object)this);}
    }

    @Mixin(VertexBuffer.class)
    public static abstract class GpuBuffer {
        @Shadow private VertexFormat uploadVertexBuffer(BufferBuilder.DrawState state,ByteBuffer bytes){throw new UnsupportedOperationException();}
        @Shadow private RenderSystem.AutoStorageIndexBuffer uploadIndexBuffer(BufferBuilder.DrawState state,ByteBuffer bytes){throw new UnsupportedOperationException();}
        @Unique private static void pro$uploadFailed(VertexBuffer buffer,Throwable failure){
            try{ClientWorldVisuals.closed(buffer);}catch(Throwable cleanup){if(cleanup!=failure)failure.addSuppressed(cleanup);}
        }
        @Redirect(method="upload(Lcom/mojang/blaze3d/vertex/BufferBuilder$RenderedBuffer;)V",at=@At(value="INVOKE",target="Lcom/mojang/blaze3d/vertex/VertexBuffer;uploadVertexBuffer(Lcom/mojang/blaze3d/vertex/BufferBuilder$DrawState;Ljava/nio/ByteBuffer;)Lcom/mojang/blaze3d/vertex/VertexFormat;"),require=1,allow=1)
        private VertexFormat pro$vertices(VertexBuffer buffer,BufferBuilder.DrawState state,ByteBuffer bytes){
            try{return uploadVertexBuffer(state,bytes);}catch(Throwable failure){pro$uploadFailed(buffer,failure);throw failure;}
        }
        @Redirect(method="upload(Lcom/mojang/blaze3d/vertex/BufferBuilder$RenderedBuffer;)V",at=@At(value="INVOKE",target="Lcom/mojang/blaze3d/vertex/VertexBuffer;uploadIndexBuffer(Lcom/mojang/blaze3d/vertex/BufferBuilder$DrawState;Ljava/nio/ByteBuffer;)Lcom/mojang/blaze3d/systems/RenderSystem$AutoStorageIndexBuffer;"),require=1,allow=1)
        private RenderSystem.AutoStorageIndexBuffer pro$indices(VertexBuffer buffer,BufferBuilder.DrawState state,ByteBuffer bytes,BufferBuilder.RenderedBuffer upload){
            try{
                RenderSystem.AutoStorageIndexBuffer result=uploadIndexBuffer(state,bytes);
                ClientWorldVisuals.uploaded(buffer,upload);return result;
            }catch(Throwable failure){pro$uploadFailed(buffer,failure);throw failure;}
        }
        @Inject(method="close()V",at=@At("RETURN"),require=1,allow=1)
        private void pro$close(CallbackInfo ci){ClientWorldVisuals.closed((VertexBuffer)(Object)this);}
        @Redirect(method="draw()V",at=@At(value="INVOKE",target="Lcom/mojang/blaze3d/systems/RenderSystem;drawElements(III)V",remap=false),require=1,allow=1)
        private void pro$draw(int mode,int count,int type){ClientWorldVisuals.draw((VertexBuffer)(Object)this,mode,count,type);}
        @Redirect(method="_drawWithShader(Lorg/joml/Matrix4f;Lorg/joml/Matrix4f;Lnet/minecraft/client/renderer/ShaderInstance;)V",at=@At(value="INVOKE",target="Lcom/mojang/blaze3d/systems/RenderSystem;getShaderGameTime()F",remap=false),require=1,allow=1)
        private float pro$shaderTime(){return ClientWorldVisuals.shaderTime(RenderSystem.getShaderGameTime());}
    }

    @Mixin(NativeImage.class)
    public interface PixelImage {
        @Accessor("pixels") long pro$pixels();
    }
    @Mixin(SpriteContents.class)
    public static abstract class SpriteUpload {
        // Both AnimatedTexture frames and InterpolationData's CPU image call this exact uploader.
        @Inject(method="upload(IIII[Lcom/mojang/blaze3d/platform/NativeImage;)V",at=@At("RETURN"),require=1,allow=1)
        private void pro$uploaded(int x,int y,int sourceX,int sourceY,NativeImage[] images,CallbackInfo ci){
            ClientWorldVisuals.spriteUploaded((SpriteContents)(Object)this,x,y,sourceX,sourceY,images);
        }
    }
    @Mixin(TextureAtlas.class)
    public static abstract class AtlasUpload {
        @Inject(method="upload(Lnet/minecraft/client/renderer/texture/SpriteLoader$Preparations;)V",at=@At("RETURN"),require=1,allow=1)
        private void pro$uploaded(SpriteLoader.Preparations preparations,CallbackInfo ci){ClientWorldVisuals.atlasUploaded((TextureAtlas)(Object)this);}
        @Inject(method="clearTextureData()V",at=@At("HEAD"),require=1,allow=1)
        private void pro$clear(CallbackInfo ci){ClientWorldVisuals.atlasCleared((TextureAtlas)(Object)this);}
    }

    @Mixin(Particle.class)
    public interface ParticleState {
        @Accessor("level") ClientLevel pro$world();
        @Accessor("x") double pro$x();
        @Accessor("y") double pro$y();
        @Accessor("z") double pro$z();
    }
    @Mixin(ParticleEngine.class)
    public static abstract class Particles {
        @Shadow protected ClientLevel level;
        @Inject(method="tickParticle",at=@At("HEAD"),cancellable=true)
        private void pro$tick(Particle particle,CallbackInfo ci){if(ClientPresence.worldFrozen(particle))ci.cancel();}
        @Inject(method="createParticle",at=@At("HEAD"),cancellable=true)
        private void pro$create(ParticleOptions type,double x,double y,double z,double vx,double vy,double vz,CallbackInfoReturnable<Particle> cir){
            if(ClientPresence.worldFrozen(level,BlockPos.containing(x,y,z)))cir.setReturnValue(null);
        }
        @Inject(method="add",at=@At("HEAD"),cancellable=true)
        private void pro$add(Particle particle,CallbackInfo ci){if(ClientPresence.worldFrozen(particle))ci.cancel();}
        @Redirect(method="render(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource$BufferSource;Lnet/minecraft/client/renderer/LightTexture;Lnet/minecraft/client/Camera;FLnet/minecraft/client/renderer/culling/Frustum;)V",remap=false,at=@At(value="INVOKE",target="Lnet/minecraft/client/particle/Particle;render(Lcom/mojang/blaze3d/vertex/VertexConsumer;Lnet/minecraft/client/Camera;F)V",remap=true))
        private void pro$render(Particle particle,VertexConsumer vertices,Camera camera,float partial){particle.render(vertices,camera,ClientPresence.worldPartial(particle,partial));}
    }
    @Mixin(TrackingEmitter.class)
    public static abstract class Emitter {
        @Shadow @Final private Entity entity;
        @Inject(method="tick",at=@At("HEAD"),cancellable=true)
        private void pro$tick(CallbackInfo ci){if(ClientPresence.worldFrozen(entity))ci.cancel();}
    }
    @Mixin(ItemInHandRenderer.class)
    public static abstract class Hands {
        @Inject(method="tick",at=@At("HEAD"),cancellable=true)
        private void pro$tick(CallbackInfo ci){if(ClientPresence.worldFrozen(Minecraft.getInstance().player))ci.cancel();}
        @ModifyVariable(method="renderHandsWithItems",at=@At("HEAD"),argsOnly=true,ordinal=0)
        private float pro$partial(float value,float partial,PoseStack pose,MultiBufferSource.BufferSource buffers,LocalPlayer player,int light){return ClientPresence.worldPartial(player,value);}
    }
    @Mixin(Entity.class)
    public static abstract class Turn {
        @Inject(method="turn",at=@At("HEAD"),cancellable=true)
        private void pro$turn(double yaw,double pitch,CallbackInfo ci){
            Entity entity=(Entity)(Object)this;
            if(entity.level()!=null&&entity.level().isClientSide&&ClientPresence.worldFrozen(entity))ci.cancel();
        }
    }
    @Mixin(Camera.class)
    public static abstract class CameraView {
        @Shadow private Entity entity;
        @Inject(method="tick",at=@At("HEAD"),cancellable=true)
        private void pro$eyeHeight(CallbackInfo ci){if(ClientPresence.worldFrozen(entity))ci.cancel();}
        @ModifyVariable(method="setup",at=@At("HEAD"),argsOnly=true,ordinal=0)
        private float pro$partial(float value,BlockGetter level,Entity viewed,boolean detached,boolean mirrored,float partial){return ClientPresence.worldPartial(viewed,value);}
    }
    @Mixin(GameRenderer.class)
    public static abstract class View {
        @Inject(method="tickFov",at=@At("HEAD"),cancellable=true)
        private void pro$fovTick(CallbackInfo ci){if(ClientPresence.worldFrozen(Minecraft.getInstance().getCameraEntity()))ci.cancel();}
        @ModifyVariable(method={"bobView","bobHurt"},at=@At("HEAD"),argsOnly=true,ordinal=0)
        private float pro$bob(float partial){return ClientPresence.worldPartial(Minecraft.getInstance().getCameraEntity(),partial);}
        @ModifyVariable(method="getFov",at=@At("HEAD"),argsOnly=true,ordinal=0)
        private float pro$fov(float value,Camera camera,float partial,boolean useSettings){return ClientPresence.worldPartial(camera.getEntity(),value);}
        @ModifyVariable(method="getNightVisionScale",at=@At("HEAD"),argsOnly=true,ordinal=0)
        private static float pro$vision(float value,LivingEntity entity,float partial){return ClientPresence.worldPartial(entity,value);}
    }
    @Mixin(LivingEntityRenderer.class)
    public static abstract class RendererState {
        @ModifyArgs(method="render(Lnet/minecraft/world/entity/LivingEntity;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",at=@At(value="INVOKE",target="Lnet/minecraft/client/renderer/entity/LivingEntityRenderer;setupRotations(Lnet/minecraft/world/entity/LivingEntity;Lcom/mojang/blaze3d/vertex/PoseStack;FFF)V"))
        private void pro$bodyYaw(Args arguments){arguments.set(3,ClientPresence.worldBodyYaw(arguments.get(0),arguments.get(3)));}
        @ModifyArgs(method="render(Lnet/minecraft/world/entity/LivingEntity;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",at=@At(value="INVOKE",target="Lnet/minecraft/client/model/EntityModel;setupAnim(Lnet/minecraft/world/entity/Entity;FFFFF)V"))
        private void pro$modelHeadYaw(Args arguments){arguments.set(4,ClientPresence.worldHeadBodyYaw(arguments.get(0),arguments.get(4)));}
        @ModifyArgs(method="render(Lnet/minecraft/world/entity/LivingEntity;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",at=@At(value="INVOKE",target="Lnet/minecraft/client/renderer/entity/layers/RenderLayer;render(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;ILnet/minecraft/world/entity/Entity;FFFFFF)V"))
        private void pro$layerHeadYaw(Args arguments){arguments.set(8,ClientPresence.worldHeadBodyYaw(arguments.get(3),arguments.get(8)));}
    }
}
