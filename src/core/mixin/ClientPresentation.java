package dev.ronova.pro.mixin;

import dev.ronova.pro.ClientPresence;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.DeathScreen;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.network.chat.Component;
import com.mojang.blaze3d.vertex.PoseStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.*;

/** Per-object submission gates. Shared buffers, normal screens and unrelated input stay usable. */
public final class ClientPresentation {
    private ClientPresentation() { }
    @Mixin(net.minecraft.client.gui.components.BossHealthOverlay.class) public interface BossAccess {
        @org.spongepowered.asm.mixin.gen.Accessor("events") java.util.Map<java.util.UUID,net.minecraft.client.gui.components.LerpingBossEvent> pro$events();
    }
    @Mixin(net.minecraft.network.protocol.game.ClientboundBossEventPacket.class) public interface BossPacket {
        @org.spongepowered.asm.mixin.gen.Accessor("id") java.util.UUID pro$id();
    }
    @Mixin(net.minecraft.client.gui.components.BossHealthOverlay.class) public abstract static class BossDraw {
        @Inject(method="drawBar(Lnet/minecraft/client/gui/GuiGraphics;IILnet/minecraft/world/BossEvent;)V",at=@At("HEAD"),cancellable=true)
        private void draw(net.minecraft.client.gui.GuiGraphics gui,int x,int y,net.minecraft.world.BossEvent boss,CallbackInfo ci) {if(ClientPresence.hudDrawDenied(boss.getId()))ci.cancel();}
        @Inject(method="update",at=@At("HEAD"),cancellable=true)
        private void update(net.minecraft.network.protocol.game.ClientboundBossEventPacket packet,CallbackInfo ci) {if(ClientPresence.hudDrawDenied(((BossPacket)packet).pro$id()))ci.cancel();}
        @Inject(method="reset",at=@At("HEAD"),cancellable=true)
        private void reset(CallbackInfo ci) {
            var events=((BossAccess)this).pro$events();boolean retained=false;
            for(var iterator=events.entrySet().iterator();iterator.hasNext();)if(ClientPresence.hudRemovalDenied(iterator.next().getKey()))retained=true;else iterator.remove();
            if(retained)ci.cancel();
        }
    }
    @Mixin(targets="net.minecraft.client.gui.components.BossHealthOverlay$1") public abstract static class BossRemoval {
        @Inject(method="remove",at=@At("HEAD"),cancellable=true)
        private void remove(java.util.UUID id,CallbackInfo ci) {if(ClientPresence.hudRemovalDenied(id))ci.cancel();}
    }
    @Mixin(net.minecraft.client.multiplayer.ClientLevel.class) public abstract static class BodyPublication {
        @Inject(method="addEntity",at=@At("HEAD"),cancellable=true)
        private void replacing(int id,Entity entity,CallbackInfo ci) {
            if(ClientPresence.replacementDenied(this,id,entity))ci.cancel();
        }
        @Inject(method="removeEntity",at=@At("HEAD"),cancellable=true)
        private void removing(int id,Entity.RemovalReason reason,CallbackInfo ci) {
            if(ClientPresence.replacementDenied(this,id,null))ci.cancel();
        }
        @Inject(method="putNonPlayerEntity",at=@At("RETURN"))
        private void published(int id,Entity entity,CallbackInfo ci) {ClientPresence.bindLocal(entity);}
        @Inject(method="addPlayer",at=@At("RETURN"))
        private void player(int id,net.minecraft.client.player.AbstractClientPlayer entity,CallbackInfo ci) {ClientPresence.bindLocal(entity);}
    }
    @Mixin(EntityRenderDispatcher.class) public abstract static class Draw {
        @Inject(method="render",at=@At("HEAD"),cancellable=true)
        private void render(Entity entity,double x,double y,double z,float rotation,float partial,PoseStack pose,MultiBufferSource buffers,int light,CallbackInfo ci) {
            if(ClientPresence.renderDenied(entity))ci.cancel();
        }
    }
    @Mixin(EntityRenderer.class) public abstract static class NameHud {
        @Inject(method="renderNameTag",at=@At("HEAD"),cancellable=true)
        private void name(Entity entity,Component text,PoseStack pose,MultiBufferSource buffers,int light,CallbackInfo ci) {
            if(ClientPresence.renderDenied(entity))ci.cancel();
        }
    }
    @Mixin(Minecraft.class) public abstract static class Screens {
        @Inject(method="setScreen",at=@At("HEAD"),cancellable=true)
        private void screen(Screen screen,CallbackInfo ci) {
            if(screen instanceof DeathScreen&&Minecraft.getInstance().isSameThread()&&ClientPresence.protectedPlayer())ci.cancel();
        }
    }
    @Mixin(net.minecraft.world.entity.LivingEntity.class) public abstract static class Health {
        @Inject(method="setHealth",at=@At("HEAD"),cancellable=true)
        private void health(float value,CallbackInfo ci) {
            if(ClientPresence.healthWriteDenied((net.minecraft.world.entity.LivingEntity)(Object)this,value))ci.cancel();
        }
    }
    @Mixin(MultiPlayerGameMode.class) public abstract static class Input {
        @Inject(method="attack",at=@At("HEAD"),cancellable=true)
        private void attack(Player player,Entity entity,CallbackInfo ci) {if(ClientPresence.interactionDenied(entity))ci.cancel();}
        @Inject(method="interact",at=@At("HEAD"),cancellable=true)
        private void interact(Player player,Entity entity,InteractionHand hand,CallbackInfoReturnable<InteractionResult> ci) {
            if(ClientPresence.interactionDenied(entity))ci.setReturnValue(InteractionResult.PASS);
        }
        @Inject(method="interactAt",at=@At("HEAD"),cancellable=true)
        private void interactAt(Player player,Entity entity,net.minecraft.world.phys.EntityHitResult hit,InteractionHand hand,CallbackInfoReturnable<InteractionResult> ci) {
            if(ClientPresence.interactionDenied(entity))ci.setReturnValue(InteractionResult.PASS);
        }
    }
}
