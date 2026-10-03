package dev.ronova.pro.mixin;

import dev.ronova.pro.ProRuntime;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.entity.*;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(PersistentEntitySectionManager.class)
public abstract class AdmissionHooks {
    @Inject(method="addEntity",at=@At("HEAD"),cancellable=true)
    private void beforeJoinEvent(EntityAccess entity,boolean existing,CallbackInfoReturnable<Boolean> ci) {
        if(entity instanceof Entity e&&ProRuntime.preventAdmission(e))ci.setReturnValue(false);
    }
    @Inject(method="addEntity",at=@At("RETURN"))
    private void afterRealPublication(EntityAccess entity,boolean existing,CallbackInfoReturnable<Boolean> ci) {
        if(Boolean.TRUE.equals(ci.getReturnValue())&&entity instanceof Entity e)ProRuntime.entityPublished(e);
    }
    @Redirect(method="addEntityUuid",at=@At(value="INVOKE",target="Ljava/util/Set;add(Ljava/lang/Object;)Z"))
    private boolean uuid(java.util.Set<Object> known,Object value,EntityAccess entity) {
        return dev.ronova.pro.EntityRecords.registeredUuid(this,known,value,entity);
    }
    @Inject(method="addEntityWithoutEvent",remap=false,at=@At("HEAD"),cancellable=true)
    private void admission(EntityAccess entity,boolean existing,CallbackInfoReturnable<Boolean> ci) {
        if (entity instanceof Entity e && ProRuntime.preventAdmission(e)) ci.setReturnValue(false);
    }

    @Mixin(net.minecraft.world.entity.EntityType.class)
    public abstract static class Factory {
        @Inject(method="create(Lnet/minecraft/nbt/CompoundTag;Lnet/minecraft/world/level/Level;)Ljava/util/Optional;",at=@At("HEAD"),cancellable=true)
        private static void beforeFactory(net.minecraft.nbt.CompoundTag tag,net.minecraft.world.level.Level level,
                CallbackInfoReturnable<java.util.Optional<Entity>> ci) {
            if(dev.ronova.pro.RecoverySources.refuseConsumption(tag,level))ci.setReturnValue(java.util.Optional.empty());
        }
        @Inject(method="create(Lnet/minecraft/nbt/CompoundTag;Lnet/minecraft/world/level/Level;)Ljava/util/Optional;",at=@At("RETURN"),cancellable=true)
        private static void factoryResult(net.minecraft.nbt.CompoundTag tag,net.minecraft.world.level.Level level,
                CallbackInfoReturnable<java.util.Optional<Entity>> ci) {
            java.util.Optional<Entity> result=ci.getReturnValue();
            if(result.isEmpty())return;
            // The factory's actual input/output relation survives a policy change inside
            // construction, even when the later load was refused. Run no caller callback.
            Entity entity=result.get();ProRuntime.loaded(entity,tag);
            if(ProRuntime.preventAdmission(entity))ci.setReturnValue(java.util.Optional.empty());
        }
        @Inject(method="create(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/nbt/CompoundTag;Ljava/util/function/Consumer;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/entity/MobSpawnType;ZZ)Lnet/minecraft/world/entity/Entity;",at=@At("HEAD"),cancellable=true)
        private void beforeConfiguredFactory(net.minecraft.server.level.ServerLevel level,net.minecraft.nbt.CompoundTag tag,
                java.util.function.Consumer<?> configure,net.minecraft.core.BlockPos pos,net.minecraft.world.entity.MobSpawnType reason,
                boolean align,boolean invert,CallbackInfoReturnable<Entity> ci) {
            if(dev.ronova.pro.RecoverySources.refuseConsumption(tag,level))ci.setReturnValue(null);
        }
        @Redirect(method="create(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/nbt/CompoundTag;Ljava/util/function/Consumer;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/entity/MobSpawnType;ZZ)Lnet/minecraft/world/entity/Entity;",
                at=@At(value="INVOKE",target="Lnet/minecraft/world/entity/EntityType;create(Lnet/minecraft/world/level/Level;)Lnet/minecraft/world/entity/Entity;"))
        private Entity configuredAllocation(net.minecraft.world.entity.EntityType<?> type,net.minecraft.world.level.Level allocationLevel,
                net.minecraft.server.level.ServerLevel level,net.minecraft.nbt.CompoundTag tag,java.util.function.Consumer<?> configure,
                net.minecraft.core.BlockPos pos,net.minecraft.world.entity.MobSpawnType reason,boolean align,boolean invert) {
            if(dev.ronova.pro.RecoverySources.refuseConsumption(tag,level))return null;
            Entity entity=type.create(allocationLevel);
            // A constructor that already ran is not rolled back. Withhold the result
            // before finalizeSpawn/configuration when its source became terminal meanwhile.
            return dev.ronova.pro.RecoverySources.refuseConsumption(tag,level)?null:entity;
        }
    }
}
