package dev.ronova.pro.mixin;

import dev.ronova.pro.ProRuntime;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.entity.EntityAccess;
import net.minecraft.world.level.entity.EntityLookup;
import net.minecraft.world.level.entity.EntityTickList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Minecraft-level entry guards and tick-container publication; fastutil internals are guarded by the agent transformer. */
public final class IndexHooks {
    private IndexHooks() { }

    @Mixin(targets="net.minecraft.world.level.entity.PersistentEntitySectionManager$Callback",priority=1)
    public abstract static class Movement {
        @Inject(method="onMove",at=@At("RETURN"))
        private void moved(CallbackInfo callback) {ProRuntime.sectionMoved(this);}
        @Inject(method="onRemove",at=@At("HEAD"),cancellable=true)
        private void removed(Entity.RemovalReason reason,CallbackInfo callback) {
            EntityAccess value=((Access.Callback)this).pro$entity();
            if(value instanceof Entity entity&&ProRuntime.preventIndexRemoval(entity))callback.cancel();
        }
    }

    @Mixin(EntityLookup.class)
    public abstract static class Lookup {
        @Inject(method="add",at=@At("HEAD"),cancellable=true)
        private void admission(EntityAccess target,CallbackInfo callback) {
            if(target instanceof Entity entity&&ProRuntime.preventAdmission(entity))callback.cancel();
        }
        @Inject(method="remove",at=@At("HEAD"),cancellable=true)
        private void guard(EntityAccess target,CallbackInfo callback) {
            if(target instanceof Entity entity && ProRuntime.preventIndexRemoval(entity)) callback.cancel();
        }
    }

    @Mixin(EntityTickList.class)
    public abstract static class Ticking {
        @Inject(method="add",at=@At("HEAD"),cancellable=true)
        private void admission(Entity entity,CallbackInfo callback) {
            if(ProRuntime.preventAdmission(entity))callback.cancel();
        }
        @Inject(method="ensureActiveIsNotIterated()V",at=@At("RETURN"))
        private void publishedTickContainers(CallbackInfo callback) {
            ProRuntime.tickContainersPublished(this);
        }

        @Inject(method="remove",at=@At("HEAD"),cancellable=true)
        private void guard(Entity entity,CallbackInfo callback) {
            if(ProRuntime.preventIndexRemoval(entity)) callback.cancel();
        }
    }

    @Mixin(net.minecraft.world.level.entity.EntitySection.class)
    public abstract static class Section {
        @Inject(method="add",at=@At("HEAD"),cancellable=true)
        private void admission(EntityAccess target,CallbackInfo callback) {
            if(target instanceof Entity entity&&ProRuntime.preventAdmission(entity))callback.cancel();
        }
    }

    @Mixin(net.minecraft.server.level.ChunkMap.class)
    public abstract static class Tracking {
        @Inject(method="addEntity",at=@At("HEAD"),cancellable=true)
        private void admission(Entity entity,CallbackInfo callback) {
            if(ProRuntime.preventAdmission(entity))callback.cancel();
        }
    }
}
