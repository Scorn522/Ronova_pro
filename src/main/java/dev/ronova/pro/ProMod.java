package dev.ronova.pro;

import java.util.UUID;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.arguments.BoolArgumentType;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.network.chat.Component;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.*;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.server.*;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.fml.common.Mod;

@Mod("ronova_pro")
public final class ProMod {
    private static final org.slf4j.Logger LOG=com.mojang.logging.LogUtils.getLogger();
    private record Feedback(UUID player,String last) { }
    private final java.util.LinkedHashMap<UUID,Feedback> feedback=new java.util.LinkedHashMap<>();
    private void watch(net.minecraft.commands.CommandSourceStack source,ProRuntime runtime,UUID operation,net.minecraft.world.entity.Entity target) {
        String result=runtime.operationSummary(operation);
        LOG.info("RONOVA_OPERATION {} target={} type={} {}",operation,target.getUUID(),net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(target.getType()),result);
        if(feedback.size()>=128)feedback.remove(feedback.keySet().iterator().next());
        feedback.put(operation,new Feedback(source.getEntity() instanceof ServerPlayer p?p.getUUID():null,result));
    }
    public ProMod() {
        // Zero-argument startup: the player supplies no Ronova JVM argument. The mod extracts its own agent and
        // bootstrap payloads and has a short-lived helper attach them to this exact process.
        ProBootstrap.install();
        try {
            Class.forName("dev.ronova.pro.agent.RecoveryAgent",false,ClassLoader.getSystemClassLoader())
                    .getMethod("bindModGroupCallers",Class.class,Class.class)
                    .invoke(null,ProMod.class,ProRuntime.class);
        } catch(ReflectiveOperationException unavailable) {
            LOG.warn("RONOVA_MOD_GROUP_AUTHORITY_UNAVAILABLE",unavailable);
        }
        net.minecraftforge.fml.DistExecutor.unsafeRunWhenOn(net.minecraftforge.api.distmarker.Dist.CLIENT,()->ClientFaults::install);
        ProNetwork.init();
        var bus=MinecraftForge.EVENT_BUS;
        bus.addListener(this::starting);
        bus.addListener(this::stopping);
        bus.addListener(this::stopped);
        bus.addListener(EventPriority.NORMAL,this::tick);
        bus.addListener(this::commands);
        bus.addListener(EventPriority.LOWEST,this::joined);
        bus.addListener(this::participant);
        bus.addListener(this::playerClone);
    }
    private void starting(ServerStartingEvent e) {
        try { ProRuntime.start(e.getServer());ProRuntime.declareDispatchTargets(); }
        catch(Exception ex) { throw new IllegalStateException("Ronova Pro durable authority unavailable; refusing writes",ex); }
    }
    private void stopped(ServerStoppedEvent e) {
        feedback.clear();
        try { ProRuntime.stop(e.getServer()); }
        catch(Exception ex) { System.err.println("RONOVA_PRO_UNSETTLED_SHUTDOWN "+ex); }
    }
    private void stopping(ServerStoppingEvent e) {
        ProRuntime runtime=ProRuntime.get(e.getServer());if(runtime!=null)runtime.beginShutdown();
    }
    private void tick(TickEvent.ServerTickEvent e) {
        if(e.phase==TickEvent.Phase.END) {
            ProRuntime r=ProRuntime.get(e.getServer()); if(r!=null) {
                r.tick();
                if(e.getServer().getTickCount()%10==0)for(var entry:java.util.List.copyOf(feedback.entrySet())) {
                    String now=r.operationSummary(entry.getKey());Feedback previous=entry.getValue();
                    if(now.equals(previous.last()))continue;
                    LOG.info("RONOVA_OPERATION {} {}",entry.getKey(),now);
                    var player=previous.player()==null?null:e.getServer().getPlayerList().getPlayer(previous.player());
                    if(player!=null)player.sendSystemMessage(Component.literal("Ronova "+entry.getKey()+"："+now));
                    feedback.put(entry.getKey(),new Feedback(previous.player(),now));
                }
            }
        }
    }
    private void joined(EntityJoinLevelEvent e) { if(!e.getLevel().isClientSide)ProRuntime.joined(e.getEntity()); }
    private void participant(PlayerEvent event) {
        if(!(event instanceof PlayerEvent.PlayerLoggedOutEvent || event instanceof PlayerEvent.PlayerLoggedInEvent
                || event instanceof PlayerEvent.PlayerChangedDimensionEvent || event instanceof PlayerEvent.PlayerRespawnEvent))return;
        if(event.getEntity() instanceof ServerPlayer p) {
            ProRuntime r=ProRuntime.get(p.server);if(r==null)return;
            r.participantChanged(p);
            if(event instanceof PlayerEvent.PlayerLoggedInEvent) {r.playerPolicyChanged(p);r.sendModGroup(p);}
            else if(event instanceof PlayerEvent.PlayerLoggedOutEvent)r.playerDisconnected(p);
            else if(event instanceof PlayerEvent.PlayerChangedDimensionEvent)r.playerChangedDimension(p);
            else if(event instanceof PlayerEvent.PlayerRespawnEvent)r.playerRespawned(p);
        }
    }
    private void playerClone(PlayerEvent.Clone event) {
        if(event.getOriginal() instanceof ServerPlayer original&&event.getEntity() instanceof ServerPlayer replacement) {
            ProRuntime r=ProRuntime.get(replacement.server);if(r!=null)r.playerCloned(original,replacement);
        }
    }
    private void commands(RegisterCommandsEvent e) {
        e.getDispatcher().register(Commands.literal("ronova_pro").requires(s->s.hasPermission(2))
            .then(Commands.literal("clear").then(Commands.argument("target",EntityArgument.entity()).executes(c-> {
                var r=ProRuntime.get(c.getSource().getServer());
                var target=EntityArgument.getEntity(c,"target");UUID id=r.clear(target);watch(c.getSource(),r,id,target);
                c.getSource().sendSuccess(()->Component.literal("身体处置已受理："+id),false);return 1;
            })))
            .then(Commands.literal("mod")
                .then(Commands.literal("stop").then(Commands.argument("ids",StringArgumentType.greedyString()).executes(c->{
                    String result=ProRuntime.get(c.getSource().getServer()).stopModGroup(StringArgumentType.getString(c,"ids"));
                    c.getSource().sendSuccess(()->Component.literal(result),false);
                    return result.startsWith("GROUP_FAILURES=")?result.startsWith("GROUP_FAILURES=0;")?1:0
                            :result.contains(";FAILED=0")&&result.contains("BODY_FAILURES=0")?1:0;
                })))
                .then(Commands.literal("protect").then(Commands.argument("ids",StringArgumentType.greedyString()).executes(c->{
                    String result=ProRuntime.get(c.getSource().getServer()).protectModGroup(StringArgumentType.getString(c,"ids"));
                    c.getSource().sendSuccess(()->Component.literal(result),false);
                    return result.startsWith("GROUP_FAILURES=")?result.startsWith("GROUP_FAILURES=0;")?1:0
                            :result.contains("FAILED=0")?1:0;
                })))
                .then(Commands.literal("status")
                    .then(Commands.argument("ids",StringArgumentType.greedyString()).executes(c->{
                        String state=ProRuntime.get(c.getSource().getServer()).modGroupState(StringArgumentType.getString(c,"ids"));
                        c.getSource().sendSuccess(()->Component.literal(state),false);return 1;
                    }))
                    .executes(c->{
                    String state=ProRuntime.get(c.getSource().getServer()).modGroupState();
                    c.getSource().sendSuccess(()->Component.literal(state),false);return 1;
                })))
            .then(Commands.literal("protect").then(Commands.argument("target",EntityArgument.entity()).executes(c-> {
                var runtime=ProRuntime.get(c.getSource().getServer());var target=EntityArgument.getEntity(c,"target");
                UUID id=runtime.protect(target);watch(c.getSource(),runtime,id,target);
                c.getSource().sendSuccess(()->Component.literal(runtime.operationSummary(id)+"；操作："+id),false);return 1;
            })))
            .then(Commands.literal("release").then(Commands.argument("target",EntityArgument.entity()).executes(c-> {
                ProRuntime.get(c.getSource().getServer()).revoke(EntityArgument.getEntity(c,"target"));return 1;
            })))
            .then(Commands.literal("block").then(Commands.argument("position",BlockPosArgument.blockPos()).executes(c-> {
                UUID id=ProRuntime.get(c.getSource().getServer()).clearBlock(c.getSource().getLevel(),BlockPosArgument.getLoadedBlockPos(c,"position"));
                c.getSource().sendSuccess(()->Component.literal("方块实体处置已受理："+id),false);return 1;
            })))
            .then(Commands.literal("status").then(Commands.argument("operation",StringArgumentType.word()).executes(c-> {
                var runtime=ProRuntime.get(c.getSource().getServer());
                UUID operation=UUID.fromString(StringArgumentType.getString(c,"operation"));
                c.getSource().sendSuccess(()->Component.literal(runtime.operationSummary(operation)),false);
                return runtime.query(operation)==null?0:1;
            })))
            .then(Commands.literal("cancel").then(Commands.argument("operation",StringArgumentType.word()).executes(c-> {
                ProRuntime.get(c.getSource().getServer()).cancel(UUID.fromString(StringArgumentType.getString(c,"operation")));
                c.getSource().sendSuccess(()->Component.literal("当前操作已撤销；已发生的效果保留在历史记录中。"),false);return 1;
            })))
            .then(Commands.literal("participant")
                .then(Commands.argument("operation",StringArgumentType.word())
                .then(Commands.argument("player",EntityArgument.player())
                .then(Commands.argument("required",BoolArgumentType.bool()).executes(c-> {
                    ProRuntime.get(c.getSource().getServer()).participant(UUID.fromString(StringArgumentType.getString(c,"operation")),
                            EntityArgument.getPlayer(c,"player").getUUID(),BoolArgumentType.getBool(c,"required"));
                    c.getSource().sendSuccess(()->Component.literal("参与要求已登记；已有必要客户端不会被降为可选。"),false);return 1;
                })))))
            .then(Commands.literal("recovery").then(Commands.argument("operation",StringArgumentType.word()).executes(c-> {
                var rows=ProRuntime.get(c.getSource().getServer()).recoveryStatus(UUID.fromString(StringArgumentType.getString(c,"operation")));
                for(String row:rows)c.getSource().sendSuccess(()->Component.literal(row),false);
                return rows.size();
            })))
            .then(Commands.literal("history").then(Commands.argument("operation",StringArgumentType.word()).executes(c-> {
                var rows=ProRuntime.get(c.getSource().getServer()).historicalOperation(UUID.fromString(StringArgumentType.getString(c,"operation")));
                for(String row:rows)c.getSource().sendSuccess(()->Component.literal(row),false);
                return rows.size();
            })))
            .then(Commands.literal("bootstrap").executes(c-> {
                // Honest startup report: the exact state and, when unavailable, the exact reason.
                c.getSource().sendSuccess(()->Component.literal("引导状态："+ProBootstrap.state()),false);
                c.getSource().sendSuccess(()->Component.literal("分派控制："+ProRuntime.dispatchState()),false);
                c.getSource().sendSuccess(()->Component.literal("存储 Native："+StorageNative.capability()),false);
                return ProBootstrap.available()?1:0;
            }))
        );
    }
}
