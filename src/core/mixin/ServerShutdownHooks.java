package dev.ronova.pro.mixin;

import net.minecraft.server.MinecraftServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Keep the server's existing queues making progress while it drains the world. */
@Mixin(MinecraftServer.class)
abstract class ServerShutdownHooks {
    @Shadow
    private boolean haveTime() {
        throw new AssertionError();
    }

    // stopServer sets its next deadline before ticking the chunk sources. If
    // that work consumes the deadline, these two admission checks otherwise
    // starve the very completions that shutdown is waiting for. Only the
    // owning server thread drains without a tick deadline after running ends;
    // task execution, source checks and the wait predicate remain unchanged.
    @Redirect(
        method = {"pollTaskInternal", "shouldRun(Lnet/minecraft/server/TickTask;)Z"},
        at = @At(value = "INVOKE", target = "Lnet/minecraft/server/MinecraftServer;haveTime()Z"),
        require = 2,
        allow = 2
    )
    private boolean ronova$shutdownTaskTime(MinecraftServer server) {
        return !server.isRunning() && server.isSameThread() || haveTime();
    }
}
