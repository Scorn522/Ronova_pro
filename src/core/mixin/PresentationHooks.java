package dev.ronova.pro.mixin;

import dev.ronova.pro.ProRuntime;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Prevent an exact retired server HUD slot from republishing a client entry. */
@Mixin(ServerBossEvent.class)
public abstract class PresentationHooks {
    @Inject(method="addPlayer",at=@At("HEAD"),cancellable=true)
    private void add(ServerPlayer player,CallbackInfo callback){if(ProRuntime.presentationPublicationDenied((ServerBossEvent)(Object)this))callback.cancel();}
    @Inject(method="setVisible",at=@At("HEAD"),cancellable=true)
    private void show(boolean visible,CallbackInfo callback){if(visible&&ProRuntime.presentationPublicationDenied((ServerBossEvent)(Object)this))callback.cancel();}
}
