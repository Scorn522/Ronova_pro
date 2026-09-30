package dev.ronova.pro.validation;

import dev.ronova.pro.ProRuntime;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.dedicated.DedicatedServer;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.world.entity.Entity;
import java.util.List;
import java.util.UUID;

/** Scenario trigger: use the installed player command instead of calling the protected runtime API. */
final class FixtureCommands {
    private FixtureCommands() { }
    static UUID clear(ProRuntime runtime, Entity target) { return entityAction(runtime,"clear",target); }
    static UUID protect(ProRuntime runtime, Entity target) { return entityAction(runtime,"protect",target); }
    static void revoke(ProRuntime runtime, Entity target) {
        execute(target.getServer(),"release "+target.getUUID());
        if(runtime.protectionStatus(target).startsWith("ACTIVE"))throw new IllegalStateException("FIXTURE_COMMAND_RELEASE_DID_NOT_CHANGE_POLICY");
    }
    static void cancel(ProRuntime runtime, UUID operation) {
        MinecraftServer server=server(runtime);
        execute(server,"cancel "+operation);
    }
    private static UUID entityAction(ProRuntime runtime,String action,Entity target) {
        List<UUID> before=runtime.operations();
        execute(target.getServer(),action+" "+target.getUUID());
        List<UUID> after=runtime.operations();
        for(int i=after.size()-1;i>=0;i--)if(!before.contains(after.get(i)))return after.get(i);
        throw new IllegalStateException("FIXTURE_COMMAND_NO_NEW_OPERATION:"+action+":"+target.getUUID());
    }
    private static MinecraftServer server(ProRuntime runtime) {
        try {
            var field=ProRuntime.class.getDeclaredField("server");
            field.setAccessible(true);
            return (MinecraftServer)field.get(runtime);
        } catch(ReflectiveOperationException unavailable) { throw new IllegalStateException(unavailable); }
    }
    static void execute(MinecraftServer server,String command) {
        execute(server,server.createCommandSourceStack(),command);
    }
    static void execute(MinecraftServer server,CommandSourceStack source,String command) {
        if(server==null||!server.isSameThread())throw new IllegalStateException("FIXTURE_COMMAND_SERVER_THREAD_REQUIRED");
        if(!(server instanceof DedicatedServer dedicated))throw new IllegalStateException("FIXTURE_REQUIRES_ISOLATED_DEDICATED_SERVER");
        dedicated.handleConsoleInput("ronova_pro "+command,source);
        dedicated.handleConsoleInputs();
    }
}
