package dev.ronova.pro.mixin;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ServerboundContainerClickPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket;
import net.minecraft.network.protocol.game.ServerboundPickItemPacket;
import net.minecraft.network.protocol.game.ServerboundSetCreativeModeSlotPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerPlayerGameMode;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Exact original action bodies. SaveFinallyPlugin supplies their lease gate and outer finally. */
public final class EquipmentActionHooks {
    private EquipmentActionHooks() { }

    @Mixin(ServerGamePacketListenerImpl.class)
    public abstract static class Packets {
        @Inject(method="handleSetCarriedItem",at=@At("HEAD"))
        private void ronova$equipmentSelect(ServerboundSetCarriedItemPacket packet,CallbackInfo callback) { }
        @Inject(method="handleContainerClick",at=@At("HEAD"))
        private void ronova$equipmentMenu(ServerboundContainerClickPacket packet,CallbackInfo callback) { }
        @Inject(method="handlePlayerAction",at=@At("HEAD"))
        private void ronova$equipmentAction(ServerboundPlayerActionPacket packet,CallbackInfo callback) { }
        @Inject(method="handleSetCreativeModeSlot",at=@At("HEAD"))
        private void ronova$equipmentCreative(ServerboundSetCreativeModeSlotPacket packet,CallbackInfo callback) { }
        @Inject(method="handlePickItem",at=@At("HEAD"))
        private void ronova$equipmentPick(ServerboundPickItemPacket packet,CallbackInfo callback) { }
    }

    @Mixin(ServerPlayerGameMode.class)
    public abstract static class Use {
        @Inject(method="useItem",at=@At("HEAD"))
        private void ronova$equipmentUse(ServerPlayer player,Level level,ItemStack stack,InteractionHand hand,
                CallbackInfoReturnable<InteractionResult> callback) { }
        @Inject(method="useItemOn",at=@At("HEAD"))
        private void ronova$equipmentUseOn(ServerPlayer player,Level level,ItemStack stack,InteractionHand hand,
                BlockHitResult hit,CallbackInfoReturnable<InteractionResult> callback) { }
    }

    @Mixin(LivingEntity.class)
    public abstract static class Using {
        @Inject(method="updateUsingItem",at=@At("HEAD"))
        private void ronova$equipmentUseTick(ItemStack stack,CallbackInfo callback) { }
        @Inject(method="completeUsingItem",at=@At("HEAD"))
        private void ronova$equipmentConsume(CallbackInfo callback) { }
        @Inject(method="releaseUsingItem",at=@At("HEAD"))
        private void ronova$equipmentRelease(CallbackInfo callback) { }
    }

    @Mixin(Mob.class)
    public abstract static class MobActions {
        @Inject(method="pickUpItem",at=@At("HEAD"))
        private void ronova$equipmentPickup(ItemEntity item,CallbackInfo callback) { }
        @Inject(method="readAdditionalSaveData",at=@At("HEAD"))
        private void ronova$equipmentLoad(CompoundTag tag,CallbackInfo callback) { }
    }

    @Mixin(Player.class)
    public abstract static class PlayerLoad {
        @Inject(method="readAdditionalSaveData",at=@At("HEAD"))
        private void ronova$equipmentLoad(CompoundTag tag,CallbackInfo callback) { }
    }

    @Mixin(ArmorStand.class)
    public abstract static class StandLoad {
        @Inject(method="readAdditionalSaveData",at=@At("HEAD"))
        private void ronova$equipmentLoad(CompoundTag tag,CallbackInfo callback) { }
    }

    @Mixin(Entity.class)
    public abstract static class Load {
        @Inject(method="load",at=@At("HEAD"))
        private void ronova$equipmentLoad(CompoundTag tag,CallbackInfo callback) { }
    }
}
