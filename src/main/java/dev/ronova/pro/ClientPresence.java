package dev.ronova.pro;

import java.util.*;
import dev.ronova.pro.mixin.Access;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Client-side presence observation and the scoped mirror of server protection policy. */
@Mod.EventBusSubscriber(modid="ronova_pro",value=Dist.CLIENT)
public final class ClientPresence {
    private static final int WATCH_LIMIT=256,CHALLENGE_TOMBSTONE_LIMIT=16384;
    private static final Map<UUID,Watch> watches=new LinkedHashMap<>();
    private static final Set<Challenge> retiredChallenges=new HashSet<>();
    private static final ClientPolicyState policy=new ClientPolicyState(),terminalPolicy=new ClientPolicyState();
    private static final System.Logger LOG=System.getLogger("ronova_pro.client");
    private record Hud(long revision,boolean protect,boolean terminal) { }
    private static final Map<UUID,Hud> hudPolicies=new HashMap<>();
    private record LocalBody(java.lang.ref.WeakReference<net.minecraft.world.entity.LivingEntity> entity,float floor,int id,UUID uuid) { }
    private static final Map<Object,LocalBody> localBodies=Collections.synchronizedMap(new WeakIdentityMap<>());
    private static boolean controlsInstalled;
    private static volatile Object connection;
    private static UUID watchSession;
    private static long tick,awaitingAfterScope=-1,worldAppliedScope=-1,lastResetScope=-1;
    private static String lastResetDimension;
    private static volatile Object world;
    private static boolean challengeOverflow,policyOverflowReported;
    private static volatile boolean awaitingScope;
    private record Challenge(UUID session,UUID operation,UUID challenge) { }
    private static final class Watch {
        final ProNetwork.Probe p;
        long sequence,lastRequest;
        Watch(ProNetwork.Probe p) { this.p=p;lastRequest=tick; }
    }
    private ClientPresence() { }
    static void modGroup(ProNetwork.ModGroup message,Object source) {
        if(message==null||!current(source))return;
        UUID known=policy.session();
        if(known!=null&&!known.equals(message.session()))return;
        if(!Set.of("default","null","empty","uuid-fixed","uuid-each","invalid-id").contains(message.returns()))return;
        // A remote server cannot turn its policy packet into a client-side JVM mod shutdown.
        // Dedicated multiplayer clients opt in locally before launch; integrated play owns both sides.
        boolean integrated=Minecraft.getInstance().getSingleplayerServer()!=null;
        Set<String> selected=new HashSet<>();
        if(!integrated) {
            String configured=System.getProperty("ronova.pro.bootStop","")+","
                    +System.getProperty("ronova.pro.clientAllowedStop","")+","
                    +System.getenv().getOrDefault("RONOVA_PRO_BOOT_STOP","");
            for(String id:configured.split(","))if(!id.isBlank())selected.add(id.trim().toLowerCase(Locale.ROOT));
        }
        List<String> accepted=new ArrayList<>();
        for(String raw:message.ids().split(",")) {
            String id=raw.trim().toLowerCase(Locale.ROOT);
            if((integrated||selected.contains(id))&&net.minecraftforge.fml.ModList.get().getModContainerById(id).isPresent())
                accepted.add(id);
        }
        if(accepted.isEmpty())return;
        try {
            Class<?> agent=Class.forName("dev.ronova.pro.agent.RecoveryAgent",false,ClassLoader.getSystemClassLoader());
            String result=String.valueOf(agent.getMethod("stopModIds",String[].class,String.class).invoke(null,
                    (Object)accepted.toArray(String[]::new),message.returns()));
            LOG.log(System.Logger.Level.INFO,"RONOVA_CLIENT_MOD_GROUP "+result);
        } catch(ReflectiveOperationException unavailable) {
            LOG.log(System.Logger.Level.WARNING,"RONOVA_CLIENT_MOD_GROUP_UNAVAILABLE "+unavailable.getClass().getSimpleName());
        }
    }

    /** Runs on the client thread and invalidates all connection-owned state on transport replacement. */
    private static Minecraft context() {
        Minecraft mc=Minecraft.getInstance();
        if(!mc.isSameThread())throw new IllegalStateException("CLIENT_THREAD_REQUIRED");
        Object active=mc.getConnection()==null?null:mc.getConnection().getConnection();
        if(connection!=active) {
            connection=active;hudPolicies.clear();localBodies.clear();watchSession=null;watches.clear();retiredChallenges.clear();challengeOverflow=false;
            policy.connection(active);terminalPolicy.connection(active);policyOverflowReported=false;awaitingScope=false;awaitingAfterScope=-1;
            worldAppliedScope=-1;lastResetScope=-1;lastResetDimension=null;
        }
        if(world!=mc.level) {
            world=mc.level;localBodies.clear();clearWatches();
            String currentDimension=levelDimension(mc);
            if(connection!=null) {
                if(lastResetScope>worldAppliedScope&&Objects.equals(lastResetDimension,currentDimension)) {
                    worldAppliedScope=lastResetScope;awaitingScope=false;awaitingAfterScope=-1;
                } else if(!awaitingScope) { hudPolicies.clear();awaitingScope=true;awaitingAfterScope=worldAppliedScope; }
            }
        }
        return mc;
    }
    private static void clearWatches() {
        for(Watch watch:new ArrayList<>(watches.values()))retire(challenge(watch.p));
        watches.clear();
    }
    private static boolean current(Object source) { context();return source!=null&&source==connection; }
    private static Challenge challenge(ProNetwork.Probe p) { return new Challenge(p.session(),p.operation(),p.challenge()); }
    private static boolean retire(Challenge old) {
        if(retiredChallenges.contains(old))return true;
        if(retiredChallenges.size()>=CHALLENGE_TOMBSTONE_LIMIT) {
            boolean first=!challengeOverflow;
            challengeOverflow=true;watches.clear();
            if(first)LOG.log(System.Logger.Level.WARNING,"CLIENT_CHALLENGE_TOMBSTONE_CAPACITY_REACHED; rejecting new probes until connection changes");
            return false;
        }
        retiredChallenges.add(old);return true;
    }
    static void watch(ProNetwork.Probe p,Object source) {
        if(p==null||!current(source))return;
        Minecraft mc=Minecraft.getInstance();
        if(mc.level==null||!mc.level.dimension().location().toString().equals(p.dimension()))return;
        if(challengeOverflow)return;
        UUID expected=policy.session();
        if((expected!=null&&!expected.equals(p.session()))||(watchSession!=null&&!watchSession.equals(p.session())))return;
        if(watchSession==null)watchSession=p.session();
        Challenge incoming=challenge(p);
        if(retiredChallenges.contains(incoming))return;
        Watch found=watches.get(p.operation());
        if(found!=null&&found.p.session().equals(p.session())&&found.p.challenge().equals(p.challenge())) {
            found.lastRequest=tick;return;
        }
        if(found==null&&watches.size()>=WATCH_LIMIT)return; // Coverage failure never produces a positive reply.
        if(found!=null&&!retire(challenge(found.p)))return;
        watches.put(p.operation(),new Watch(p));
    }
    static void forget(ProNetwork.Forget p,Object source) {
        if(p==null||!current(source)||challengeOverflow)return;
        if((watchSession!=null&&!watchSession.equals(p.session()))||(policy.session()!=null&&!policy.session().equals(p.session())))return;
        Watch w=watches.get(p.operation());
        Challenge forgotten=new Challenge(p.session(),p.operation(),p.challenge());
        retire(forgotten); // Also covers a Forget that reaches the client before its queued Probe.
        if(w!=null&&w.p.session().equals(p.session())&&w.p.challenge().equals(p.challenge()))
            watches.remove(p.operation());
    }
    static void reset(ProNetwork.PolicyReset p,Object source) {
        if(p==null||!current(source))return;
        long previousScope=policy.scope();
        if(policy.reset(source,p.session(),p.scope(),p.dimension())) {
            terminalPolicy.reset(source,p.session(),p.scope(),p.dimension());if(p.scope()>previousScope) {hudPolicies.clear();localBodies.clear();}
            if(watchSession==null)watchSession=p.session();
            else if(!watchSession.equals(p.session())) { clearWatches();watchSession=p.session(); }
            lastResetScope=p.scope();lastResetDimension=p.dimension();
            String currentDimension=levelDimension(Minecraft.getInstance());
            if(Objects.equals(currentDimension,p.dimension())) {
                worldAppliedScope=p.scope();
                if(awaitingScope&&p.scope()>awaitingAfterScope) { awaitingScope=false;awaitingAfterScope=-1; }
            }
            policyOverflowReported=false;
        }
    }
    static void policy(ProNetwork.Protection p,Object source) {
        if(p==null||!current(source))return;
        if(policy.accept(source,p.session(),p.scope(),p.dimension(),p.revision(),p.entityId(),p.entityUuid(),p.protectedNow())) {
            if(!p.protectedNow()) {
                synchronized(localBodies) {localBodies.entrySet().removeIf(entry->entry.getValue().id()==p.entityId()&&entry.getValue().uuid().equals(p.entityUuid()));}
            }
            var mc=Minecraft.getInstance();
            if(mc.level!=null) {
                var entity=mc.level.getEntity(p.entityId());
                if(mc.player!=null&&mc.player.getId()==p.entityId()&&mc.player.getUUID().equals(p.entityUuid()))entity=mc.player;
                bindLocal(entity);
            }
            installControls();
        }
        if(policy.overflowed()&&!policyOverflowReported) {
            policyOverflowReported=true;
            LOG.log(System.Logger.Level.WARNING,policy.diagnostic());
        }
    }
    private static void installControls() {
        if(controlsInstalled)return;
        try {
            var lookup=java.lang.invoke.MethodHandles.lookup();
            ProRuntime.installClientControls(lookup.findStatic(ClientPresence.class,"clientField",java.lang.invoke.MethodType.methodType(boolean.class,Object.class,Class.class,String.class,Object.class)),
                    lookup.findStatic(ClientPresence.class,"clientDispatch",java.lang.invoke.MethodType.methodType(boolean.class,Object.class,String.class,Object.class)),
                    lookup.findStatic(ClientPresence.class,"clientLifeResult",java.lang.invoke.MethodType.methodType(Object.class,Object.class,String.class)));
            ProRuntime.registerClientControlObjects(watches,retiredChallenges,hudPolicies,localBodies,
                    policy.controlObjects()[0],policy.controlObjects()[1],terminalPolicy.controlObjects()[0],terminalPolicy.controlObjects()[1]);
            controlsInstalled=true;
        }catch(ReflectiveOperationException|RuntimeException unavailable){LOG.log(System.Logger.Level.WARNING,"CLIENT_CONTROL_INSTALL_UNAVAILABLE",unavailable);}
    }
    public static void bindLocal(net.minecraft.world.entity.Entity entity) {
        if(!(entity instanceof net.minecraft.world.entity.LivingEntity living))return;
        var raw=(Access.EntityState)entity;
        if(!removalDenied(raw.pro$id(),raw.pro$uuid())) {removeLocal(entity);return;}
        if(raw.pro$level()!=Minecraft.getInstance().level)return;
        if(Minecraft.getInstance().level.getEntity(raw.pro$id())!=entity&&Minecraft.getInstance().player!=entity)return;
        if(!removalDenied(raw.pro$id(),raw.pro$uuid())) {removeLocal(entity);return;}
        var item=((Access.Data)raw.pro$data()).pro$item(Access.Living.pro$healthKey());float floor=item.getValue();
        if(Float.isFinite(floor)&&floor>0) {
            LocalBody body=new LocalBody(new java.lang.ref.WeakReference<>(living),floor,raw.pro$id(),raw.pro$uuid());localBodies.put(entity,body);localBodies.put(item,body);
        }
    }
    private static void removeLocal(Object entity) {
        synchronized(localBodies) {localBodies.entrySet().removeIf(entry->entry.getKey()==entity||entry.getValue().entity().get()==entity);}
    }
    private static boolean currentBody(LocalBody body) {
        if(body==null)return false;
        var entity=body.entity().get();if(entity==null||localBodies.get(entity)!=body)return false;
        boolean current=Minecraft.getInstance().isSameThread()?removalDenied(body.id(),body.uuid()):
                !awaitingScope&&policy.denies(connection,policy.dimension(),body.id(),body.uuid());
        if(!current) {removeLocal(entity);return false;}
        return ((Access.EntityState)entity).pro$level()==world;
    }
    public static boolean healthWriteDenied(net.minecraft.world.entity.LivingEntity entity,float value) {
        if(entity==null||!Minecraft.getInstance().isSameThread())return false;
        LocalBody body=localBodies.get(entity);
        if(!currentBody(body)||body.entity.get()!=entity)return false;
        var raw=(Access.EntityState)entity;
        return removalDenied(raw.pro$id(),raw.pro$uuid())&&(!Float.isFinite(value)||value<body.floor);
    }
    public static boolean clientField(Object receiver,Class<?> declaring,String field,Object value) {
        LocalBody body=localBodies.get(receiver);
        if(currentBody(body)&&localBodies.containsKey(body.entity.get())) {
            if(receiver instanceof net.minecraft.network.syncher.SynchedEntityData.DataItem<?> &&(field.equals("value")||field.equals("f_135391_")))return !(value instanceof Float number)||!Float.isFinite(number)||number<body.floor;
            return switch(field) {case "id","f_19848_"->!(value instanceof Number n)||n.intValue()!=body.id();case "uuid","f_19820_"->!Objects.equals(value,body.uuid());case "stringUUID","f_19821_"->!Objects.equals(value,body.uuid().toString());case "dead","f_20890_"->Boolean.TRUE.equals(value);case "deathTime","f_20919_"->value instanceof Number n&&n.intValue()>0;case "removalReason","f_146795_"->value!=null;default->false;};
        }
        if(receiver instanceof net.minecraftforge.eventbus.api.Event event&&(field.equals("isCanceled")||field.equals("canceled")))return clientDispatch(event,"setCanceled",value);
        return false;
    }
    public static Object clientLifeResult(Object receiver,String method) {
        LocalBody body=localBodies.get(receiver);
        if(!currentBody(body)||body.entity().get()!=receiver)return null;
        return switch(method) {
            case "getHealth","m_21223_" -> {
                float actual=((Access.Data)((Access.EntityState)receiver).pro$data()).pro$item(Access.Living.pro$healthKey()).getValue();
                yield Float.valueOf(Float.isFinite(actual)?Math.max(actual,body.floor()):body.floor());
            }
            case "isAlive","m_6084_" -> Boolean.TRUE;
            case "isDeadOrDying","m_21224_" -> Boolean.FALSE;
            default -> null;
        };
    }
    public static boolean clientDispatch(Object receiver,String method,Object value) {
        if(receiver instanceof net.minecraft.world.entity.Entity entity) {
            LocalBody body=localBodies.get(entity);
            if(!currentBody(body)||body.entity.get()!=entity)return false;
            return switch(method) {
                case "remove","m_142467_","setRemoved","m_142687_","discard","m_146870_","kill","m_6074_",
                     "hurt","m_6469_","actuallyHurt","m_6475_","die","m_6667_" -> true;
                case "setHealth","m_21153_" -> value instanceof Number n&&(!Float.isFinite(n.floatValue())||n.floatValue()<body.floor);
                default -> false;
            };
        }
        if(!method.equals("setCanceled")||!Boolean.TRUE.equals(value)||!Minecraft.getInstance().isSameThread())return false;
        if(receiver instanceof net.minecraftforge.client.event.RenderLivingEvent.Pre<?,?> event) {
            var raw=(Access.EntityState)event.getEntity();return removalDenied(raw.pro$id(),raw.pro$uuid());
        }
        if(receiver instanceof net.minecraftforge.client.event.RenderGuiOverlayEvent.Pre event)
            return protectedPlayer()&&event.getOverlay().id().equals(net.minecraftforge.client.gui.overlay.VanillaGuiOverlay.PLAYER_HEALTH.id());
        if(receiver instanceof net.minecraftforge.client.event.CustomizeGuiOverlayEvent.BossEventProgress event)return hudRemovalDenied(event.getBossEvent().getId());
        return false;
    }
    /** Refuse the whole mutation before ClientLevel can partly remove a protected mirror. */
    public static boolean replacementDenied(Object level,int id,net.minecraft.world.entity.Entity replacement) {
        Minecraft mc=context();if(level!=mc.level||mc.level==null)return false;
        var previous=mc.level.getEntity(id);
        if(previous==null||previous==replacement)return false;
        // Respawn/dimension handoff has already changed the actual current player or world.
        if(previous instanceof net.minecraft.client.player.LocalPlayer&&previous!=mc.player) {
            removeLocal(previous);return false;
        }
        var raw=(Access.EntityState)previous;
        return removalDenied(raw.pro$id(),raw.pro$uuid());
    }
    static void hudPolicy(ProNetwork.HudPolicy p,Object source) {
        Minecraft mc=context();if(!current(source)||!Objects.equals(policy.session(),p.session())||policy.scope()!=p.scope()||!Objects.equals(policy.dimension(),p.dimension()))return;
        Hud prior=hudPolicies.get(p.hud());if(p.revision()<=0||prior!=null&&p.revision()<=prior.revision())return;
        if(prior==null&&hudPolicies.size()>=131072) {LOG.log(System.Logger.Level.WARNING,"HUD_POLICY_CAPACITY");return;}
        hudPolicies.put(p.hud(),new Hud(p.revision(),p.protectedNow(),p.terminal()));
        if(p.terminal()&&!awaitingScope&&Objects.equals(levelDimension(mc),p.dimension()))((dev.ronova.pro.mixin.ClientPresentation.BossAccess)mc.gui.getBossOverlay()).pro$events().remove(p.hud());
    }
    public static boolean hudRemovalDenied(UUID id) {context();Hud policy=hudPolicies.get(id);return !awaitingScope&&Objects.equals(ClientPresence.policy.dimension(),levelDimension(Minecraft.getInstance()))&&policy!=null&&policy.protect();}
    public static boolean hudDrawDenied(UUID id) {context();Hud policy=hudPolicies.get(id);return !awaitingScope&&Objects.equals(ClientPresence.policy.dimension(),levelDimension(Minecraft.getInstance()))&&policy!=null&&policy.terminal();}
    static void terminal(ProNetwork.Terminal p,Object source) {
        if(p==null||!current(source))return;
        terminalPolicy.accept(source,p.session(),p.scope(),p.dimension(),p.revision(),p.entityId(),p.entityUuid(),p.terminal());
    }
    public static boolean renderDenied(net.minecraft.world.entity.Entity entity) {
        Minecraft mc=context();if(awaitingScope||mc.level==null||entity==null)return false;
        var raw=(Access.EntityState)entity;
        return terminalPolicy.denies(connection,levelDimension(mc),raw.pro$id(),raw.pro$uuid());
    }
    public static boolean protectedPlayer() {
        Minecraft mc=context();if(mc.player==null)return false;
        var raw=(Access.EntityState)mc.player;return removalDenied(raw.pro$id(),raw.pro$uuid());
    }
    public static boolean interactionDenied(net.minecraft.world.entity.Entity entity) {return renderDenied(entity);}
    private static String levelDimension(Minecraft mc) { return mc.level==null?null:mc.level.dimension().location().toString(); }
    /** Unknown, disconnected, wrong-dimension, pending-scope and reused-id objects are never treated as protected. */
    public static boolean removalDenied(int entityId,UUID entityUuid) {
        Minecraft mc=context();
        return !awaitingScope&&mc.level!=null&&policy.denies(connection,levelDimension(mc),entityId,entityUuid);
    }
    /** Uses the mixin's actual entity identity fields and verifies the direct id lookup before trusting it. */
    public static UUID identityOf(int entityId) {
        Minecraft mc=context();
        if(mc.level==null)return null;
        var direct=mc.level.getEntity(entityId);
        if(direct!=null) {
            var state=(Access.EntityState)direct;
            if(state.pro$id()==entityId)return state.pro$uuid();
        }
        for(var entity:mc.level.entitiesForRendering()) {
            var state=(Access.EntityState)entity;
            if(state.pro$id()==entityId)return state.pro$uuid();
        }
        return null;
    }
    public static String policyDiagnostic() {
        context();return awaitingScope?"CLIENT_WORLD_SCOPE_AWAITING_NEWER_RESET":""+policy.diagnostic();
    }
    public static String challengeDiagnostic() {
        context();return challengeOverflow?"CLIENT_CHALLENGE_TOMBSTONE_CAPACITY_REACHED_CONNECTION_RESET_REQUIRED":"CLIENT_CHALLENGES_TRACKED";
    }
    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent event) {
        if(event.phase!=TickEvent.Phase.END)return;
        Minecraft mc=context();tick++;
        if(mc.level==null||mc.getConnection()==null||tick%5!=0)return;
        var expired=new ArrayList<UUID>();
        for(var entry:watches.entrySet())if(tick-entry.getValue().lastRequest>1200)expired.add(entry.getKey());
        for(UUID operation:expired) {
            Watch old=watches.remove(operation);
            if(old!=null&&!retire(challenge(old.p)))break;
        }
        for(Watch w:watches.values()) {
            boolean absent=mc.level.dimension().location().toString().equals(w.p.dimension());
            if(absent&&mc.level.getEntity(w.p.entityId())!=null)absent=false;
            if(absent)for(var entity:mc.level.entitiesForRendering()) {
                var state=(Access.EntityState)entity;
                if(state.pro$id()==w.p.entityId()||state.pro$uuid().equals(w.p.entityUuid())) { absent=false;break; }
            }
            if(connection!=null&&watchSession!=null&&watchSession.equals(w.p.session()))
                ProNetwork.reply(w.p,++w.sequence,absent);
        }
    }
}
