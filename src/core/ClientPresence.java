package dev.ronova.pro;

import java.util.*;
import dev.ronova.pro.mixin.Access;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.client.particle.Particle;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.util.RandomSource;
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
    private static List<ProNetwork.WorldArea> worldAreas=List.of();
    private static final Set<ProNetwork.WorldBody> worldBodies=new HashSet<>();
    private static final Map<Object,WorldFrame> worldFrames=new WeakIdentityMap<>();
    private static final Map<BlockPos,ClientWorldVisuals.Frame> terrainFrames=new HashMap<>();
    private static final Map<WeatherColumn,NavigableMap<Integer,WeatherFrame>> weatherFrames=new HashMap<>();
    private record WeatherColumn(int x,int z) {}
    private static final Map<UUID,Float> worldRuleClocks=new HashMap<>();
    private record WeatherFrame(float time,int spanSum,int high) {}
    private static final class WorldFrame {
        final float partial;final long gameTime;
        Float chestOpening,bodyYaw,headBodyYaw;
        ClientWorldVisuals.Frame visual;
        WorldFrame(float partial,long gameTime){this.partial=partial;this.gameTime=gameTime;}
        float partial(){return partial;}
        long gameTime(){return gameTime;}
    }
    private static long worldRevision=-1,worldMirrorScope=-1;
    private static ProNetwork.WorldPolicy pendingWorld;
    private static final Map<Integer,ProNetwork.WorldPolicy> worldPages=new HashMap<>();
    private static long gatheringWorldRevision=-1;
    private static Float rendererClock;
    private static WeatherVertices weatherVertices;
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
            clearWorldPolicy();pendingWorld=null;worldRevision=-1;clearWorldPages();
        }
        if(world!=mc.level) {
            long oldMirrorScope=worldMirrorScope;
            world=mc.level;localBodies.clear();clearWatches();clearWorldPolicy();
            if(pendingWorld!=null&&pendingWorld.scope()<=oldMirrorScope)pendingWorld=null;
            String currentDimension=levelDimension(mc);
            if(connection!=null) {
                if(lastResetScope>worldAppliedScope&&Objects.equals(lastResetDimension,currentDimension)) {
                    worldAppliedScope=lastResetScope;awaitingScope=false;awaitingAfterScope=-1;
                } else if(!awaitingScope) { hudPolicies.clear();awaitingScope=true;awaitingAfterScope=worldAppliedScope; }
            }
            applyPendingWorld(mc);
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
            terminalPolicy.reset(source,p.session(),p.scope(),p.dimension());if(p.scope()>previousScope) {hudPolicies.clear();localBodies.clear();clearWorldPolicy();pendingWorld=null;worldRevision=-1;clearWorldPages();}
            if(watchSession==null)watchSession=p.session();
            else if(!watchSession.equals(p.session())) { clearWatches();watchSession=p.session(); }
            lastResetScope=p.scope();lastResetDimension=p.dimension();
            String currentDimension=levelDimension(Minecraft.getInstance());
            if(Objects.equals(currentDimension,p.dimension())) {
                worldAppliedScope=p.scope();
                if(awaitingScope&&p.scope()>awaitingAfterScope) { awaitingScope=false;awaitingAfterScope=-1; }
            }
            policyOverflowReported=false;
            applyPendingWorld(Minecraft.getInstance());
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
                    policy.controlObjects()[0],policy.controlObjects()[1],terminalPolicy.controlObjects()[0],terminalPolicy.controlObjects()[1],worldBodies,worldFrames,terrainFrames,weatherFrames,worldPages,worldRuleClocks);
            ProRuntime.registerClientControlObjects(ClientWorldVisuals.controlObjects());
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
    private static void clearWorldPolicy(){ClientWorldVisuals.clearFrames();worldAreas=List.of();worldBodies.clear();worldFrames.clear();terrainFrames.clear();weatherFrames.clear();worldRuleClocks.clear();rendererClock=null;weatherVertices=null;worldMirrorScope=-1;}
    private static void clearWorldPages(){worldPages.clear();gatheringWorldRevision=-1;}
    public static void beginWorldHandoff(Object listener){
        Minecraft mc=context();if(mc.getConnection()!=listener)return;
        clearWorldPolicy();pendingWorld=null;clearWorldPages();hudPolicies.clear();localBodies.clear();clearWatches();
        awaitingScope=true;awaitingAfterScope=Math.max(policy.scope(),worldAppliedScope);
    }
    static void worldPolicy(ProNetwork.WorldPolicy packet,Object source){
        Minecraft mc=context();
        if(packet==null||!current(source)||!Objects.equals(policy.session(),packet.session())||policy.scope()!=packet.scope()
                ||!Objects.equals(policy.dimension(),packet.dimension())||packet.revision()<=worldRevision||packet.revision()<=0)return;
        if(packet.pages()<1||packet.page()<0||packet.page()>=packet.pages()||packet.revision()<gatheringWorldRevision)return;
        for(ProNetwork.WorldArea area:packet.areas())if(area.rule()==null||area.low().getX()>area.high().getX()||area.low().getY()>area.high().getY()||area.low().getZ()>area.high().getZ())return;
        if(packet.revision()!=gatheringWorldRevision){clearWorldPages();gatheringWorldRevision=packet.revision();}
        if(!worldPages.isEmpty()){
            ProNetwork.WorldPolicy first=worldPages.values().iterator().next();
            if(first.pages()!=packet.pages()||first.gameTime()!=packet.gameTime()||first.dayTime()!=packet.dayTime()||first.daylight()!=packet.daylight()){clearWorldPages();gatheringWorldRevision=packet.revision();}
        }
        worldPages.put(packet.page(),packet);if(worldPages.size()!=packet.pages())return;
        List<ProNetwork.WorldArea> areas=new ArrayList<>();List<ProNetwork.WorldBody> bodies=new ArrayList<>();
        for(int page=0;page<packet.pages();page++){ProNetwork.WorldPolicy part=worldPages.get(page);if(part==null)return;areas.addAll(part.areas());bodies.addAll(part.bodies());}
        worldRevision=packet.revision();pendingWorld=new ProNetwork.WorldPolicy(packet.session(),packet.scope(),packet.dimension(),packet.revision(),packet.gameTime(),packet.dayTime(),packet.daylight(),0,1,List.copyOf(areas),List.copyOf(bodies));
        clearWorldPages();applyPendingWorld(mc);installControls();
    }
    private static void applyPendingWorld(Minecraft mc){
        ProNetwork.WorldPolicy packet=pendingWorld;
        if(packet==null||awaitingScope||mc.level==null||connection==null||!Objects.equals(policy.session(),packet.session())
                ||policy.scope()!=packet.scope()||!Objects.equals(levelDimension(mc),packet.dimension()))return;
        boolean wasClock=worldAreas.stream().anyMatch(ProNetwork.WorldArea::clock);
        worldAreas=List.copyOf(packet.areas());worldBodies.clear();worldBodies.addAll(packet.bodies());worldMirrorScope=packet.scope();
        float receivedClock=mc.levelRenderer.getTicks()+mc.getFrameTime();
        Set<UUID> currentRules=new HashSet<>();for(ProNetwork.WorldArea area:worldAreas){currentRules.add(area.rule());worldRuleClocks.putIfAbsent(area.rule(),receivedClock);}
        worldRuleClocks.keySet().retainAll(currentRules);
        boolean clock=worldAreas.stream().anyMatch(ProNetwork.WorldArea::clock);
        if(!clock)rendererClock=null;
        else if(!wasClock)rendererClock=receivedClock;
        if(clock||wasClock){
            mc.level.setGameTime(packet.gameTime());mc.level.setDayTime(packet.dayTime());
            mc.level.getGameRules().getRule(net.minecraft.world.level.GameRules.RULE_DAYLIGHT).set(packet.daylight(),null);
        }
        worldFrames.entrySet().removeIf(entry->{
            if(selectedWorldObject(mc,entry.getKey()))return false;
            if(entry.getValue().visual!=null)entry.getValue().visual.close();return true;
        });
        terrainFrames.entrySet().removeIf(entry->{
            if(selectedWorldPosition(entry.getKey()))return false;
            entry.getValue().close();return true;
        });
        ClientWorldVisuals.pruneFrames();
        weatherFrames.entrySet().removeIf(entry->{
            WeatherColumn column=entry.getKey();entry.getValue().keySet().removeIf(y->!selectedWorldPosition(new BlockPos(column.x(),y,column.z())));return entry.getValue().isEmpty();
        });
    }
    private static boolean activeWorld(Minecraft mc,Level level){return level!=null&&!awaitingScope&&connection!=null&&level==mc.level&&worldMirrorScope==policy.scope()&&Objects.equals(policy.dimension(),levelDimension(mc));}
    private static boolean selectedWorldPosition(BlockPos pos){for(ProNetwork.WorldArea area:worldAreas)if(area.contains(pos))return true;return false;}
    public static boolean worldFrozen(Level level,BlockPos pos){Minecraft mc=context();return pos!=null&&activeWorld(mc,level)&&selectedWorldPosition(pos);}
    public static boolean worldClockFrozen(Level level){Minecraft mc=context();return activeWorld(mc,level)&&worldAreas.stream().anyMatch(ProNetwork.WorldArea::clock);}
    private static boolean selectedEntity(Minecraft mc,Entity entity){
        if(entity==null)return false;Access.EntityState state=(Access.EntityState)entity;
        if(state.pro$level()!=mc.level||mc.level==null||(mc.level.getEntity(state.pro$id())!=entity&&mc.player!=entity))return false;
        return worldBodies.contains(new ProNetwork.WorldBody(state.pro$id(),state.pro$uuid()))||selectedWorldPosition(state.pro$blockPosition());
    }
    public static boolean worldFrozen(Entity entity){Minecraft mc=context();return activeWorld(mc,mc.level)&&selectedEntity(mc,entity);}
    private static boolean selectedWorldObject(Minecraft mc,Object object){
        if(object instanceof Entity entity)return selectedEntity(mc,entity);
        if(object instanceof BlockEntity block)return mc.level!=null&&block.getLevel()==mc.level&&mc.level.getBlockEntity(block.getBlockPos())==block&&selectedWorldPosition(block.getBlockPos());
        if(object instanceof Particle particle){var state=(dev.ronova.pro.mixin.ClientWorldHooks.ParticleState)particle;return state.pro$world()==mc.level&&selectedWorldPosition(BlockPos.containing(state.pro$x(),state.pro$y(),state.pro$z()));}
        return false;
    }
    public static boolean worldFrozen(Particle particle){Minecraft mc=context();return particle!=null&&activeWorld(mc,mc.level)&&selectedWorldObject(mc,particle);}
    private static void dropWorldFrame(Object object){WorldFrame previous=worldFrames.remove(object);if(previous!=null&&previous.visual!=null)previous.visual.close();}
    public static boolean worldVisualsActive(){Minecraft mc=context();return activeWorld(mc,mc.level)&&(!worldAreas.isEmpty()||!worldBodies.isEmpty());}
    public static ClientWorldVisuals.Frame worldVisualFrame(Object object){
        WorldFrame frame=renderingFrame(object);if(frame==null)return null;
        if(frame.visual==null||!frame.visual.isOpen())frame.visual=new ClientWorldVisuals.Frame(object);
        return frame.visual;
    }
    public static ClientWorldVisuals.Frame terrainVisualFrame(Level level,BlockPos position){
        Minecraft mc=context();if(position==null||!activeWorld(mc,level))return null;
        if(!selectedWorldPosition(position)){ClientWorldVisuals.Frame previous=terrainFrames.remove(position);if(previous!=null)previous.close();return null;}
        return terrainFrames.compute(position.immutable(),(key,previous)->previous!=null&&previous.isOpen()?previous:new ClientWorldVisuals.Frame(key));
    }
    static boolean currentVisualFrame(Object object,ClientWorldVisuals.Frame frame){
        Minecraft mc=context();if(!activeWorld(mc,mc.level))return false;
        if(object instanceof BlockPos position)return selectedWorldPosition(position)&&terrainFrames.get(position)==frame
                &&mc.level.getChunkSource().getChunk(position.getX()>>4,position.getZ()>>4,net.minecraft.world.level.chunk.ChunkStatus.FULL,false)!=null;
        WorldFrame current=worldFrames.get(object);return current!=null&&current.visual==frame&&selectedWorldObject(mc,object);
    }
    public static float worldPartial(Object object,float partial){
        Minecraft mc=context();
        if(!activeWorld(mc,mc.level)||!selectedWorldObject(mc,object)){dropWorldFrame(object);return partial;}
        return worldFrames.computeIfAbsent(object,ignored->new WorldFrame(partial,object instanceof BlockEntity block?selectedWorldTime(block.getBlockPos(),mc.level.getGameTime()):mc.level.getGameTime())).partial();
    }
    private static WorldFrame renderingFrame(Object object){
        Minecraft mc=context();
        if(!activeWorld(mc,mc.level)||!selectedWorldObject(mc,object)){dropWorldFrame(object);return null;}
        return worldFrames.computeIfAbsent(object,ignored->new WorldFrame(mc.getFrameTime(),object instanceof BlockEntity block?selectedWorldTime(block.getBlockPos(),mc.level.getGameTime()):mc.level.getGameTime()));
    }
    /** Capture after the real double-chest combiner has run, for only the chest being drawn. */
    public static float worldChestOpening(BlockEntity entity,float combined){
        WorldFrame frame=renderingFrame(entity);if(frame==null)return combined;
        if(frame.chestOpening==null)frame.chestOpening=combined;return frame.chestOpening;
    }
    /** setupRotations consumes this yaw after vanilla has combined the passenger and its living vehicle. */
    public static float worldBodyYaw(Entity entity,float combined){
        WorldFrame frame=renderingFrame(entity);if(frame==null)return combined;
        if(frame.bodyYaw==null)frame.bodyYaw=combined;return frame.bodyYaw;
    }
    /** Both the model and its render layers receive the same frozen, already-combined relative head yaw. */
    public static float worldHeadBodyYaw(Entity entity,float combined){
        WorldFrame frame=renderingFrame(entity);if(frame==null)return combined;
        if(frame.headBodyYaw==null)frame.headBodyYaw=combined;return frame.headBodyYaw;
    }
    private static long selectedWorldTime(BlockPos position,long current){
        boolean found=false;long selected=current;for(ProNetwork.WorldArea area:worldAreas)if(area.contains(position)){if(!found||area.gameTime()<selected)selected=area.gameTime();found=true;}return selected;
    }
    public static long worldGameTime(BlockEntity entity,long current){
        Minecraft mc=context();
        if(!activeWorld(mc,entity.getLevel())||!selectedWorldObject(mc,entity)){dropWorldFrame(entity);return current;}
        return worldFrames.computeIfAbsent(entity,ignored->new WorldFrame(mc.getFrameTime(),selectedWorldTime(entity.getBlockPos(),current))).gameTime();
    }
    public static float worldRendererClock(float current){
        Minecraft mc=context();if(!worldClockFrozen(mc.level)){rendererClock=null;return current;}
        if(rendererClock==null)rendererClock=current;return rendererClock;
    }
    public static void beginWeather(int ticks,float partial,double cameraX,double cameraY,double cameraZ){
        context();weatherVertices=new WeatherVertices(ticks,partial,cameraX,cameraY,cameraZ);
    }
    public static void endWeather(){weatherVertices=null;}
    public static Biome.Precipitation weatherColumn(Biome biome,BlockPos position){
        Biome.Precipitation precipitation=biome.getPrecipitationAt(position);
        if(weatherVertices!=null){weatherVertices.column=position.immutable();weatherVertices.precipitation=precipitation;}
        return precipitation;
    }
    public static VertexConsumer weatherVertex(BufferBuilder builder,double x,double y,double z){
        WeatherVertices batch=weatherVertices;
        if(batch==null)return builder.vertex(x,y,z);
        batch.builder=builder;batch.current=new WeatherVertex(x,y,z);return batch;
    }
    private static final class WeatherVertex {
        final double x,y,z;float u,v;int r,g,b,a,lightX,lightY;
        WeatherVertex(double x,double y,double z){this.x=x;this.y=y;this.z=z;}
    }
    /** Vanilla precipitation supplies four vertices per exact column. Split only its selected Y intervals. */
    private static final class WeatherVertices implements VertexConsumer {
        final int ticks;final float partial;final double cameraX,cameraY,cameraZ;
        final List<WeatherVertex> quad=new ArrayList<>(4);
        BufferBuilder builder;WeatherVertex current;BlockPos column;Biome.Precipitation precipitation;
        WeatherVertices(int ticks,float partial,double x,double y,double z){this.ticks=ticks;this.partial=partial;cameraX=x;cameraY=y;cameraZ=z;}
        public VertexConsumer vertex(double x,double y,double z){current=new WeatherVertex(x,y,z);return this;}
        public VertexConsumer color(int r,int g,int b,int a){current.r=r;current.g=g;current.b=b;current.a=a;return this;}
        public VertexConsumer uv(float u,float v){current.u=u;current.v=v;return this;}
        public VertexConsumer uv2(int x,int y){current.lightX=x;current.lightY=y;return this;}
        public VertexConsumer overlayCoords(int x,int y){return this;}
        public VertexConsumer normal(float x,float y,float z){return this;}
        public void defaultColor(int r,int g,int b,int a){builder.defaultColor(r,g,b,a);}
        public void unsetDefaultColor(){builder.unsetDefaultColor();}
        public void endVertex(){quad.add(current);if(quad.size()==4){emit();quad.clear();}}
        private float[] phase(float time){
            int whole=(int)Math.floor(time);float fraction=time-whole;int x=column.getX(),z=column.getZ();
            RandomSource random=RandomSource.create((long)(x*x*3121+x*45238971^z*z*418711+z*13761));
            if(precipitation==Biome.Precipitation.RAIN){int wrapped=(whole+x*x*3121+x*45238971+z*z*418711+z*13761)&31;return new float[]{0,-(wrapped+fraction)/32.0F*(3.0F+random.nextFloat())};}
            float vertical=-((whole&511)+fraction)/512.0F;
            float u=(float)(random.nextDouble()+(double)time*.01D*(double)(float)random.nextGaussian());
            float v=(float)(random.nextDouble()+(double)(time*(float)random.nextGaussian())*.001D);
            return new float[]{u,vertical+v};
        }
        private void emit(){
            if(column==null){for(WeatherVertex vertex:quad)put(vertex,vertex.y,vertex.u,vertex.v);return;}
            int low=(int)Math.round(quad.get(2).y+cameraY),high=(int)Math.round(quad.get(0).y+cameraY);
            WeatherColumn key=new WeatherColumn(column.getX(),column.getZ());NavigableMap<Integer,WeatherFrame> frames=weatherFrames.get(key);
            SortedSet<Integer> cuts=new TreeSet<>();cuts.add(low);cuts.add(high);
            for(ProNetwork.WorldArea area:worldAreas)if(column.getX()>=area.low().getX()&&column.getX()<=area.high().getX()&&column.getZ()>=area.low().getZ()&&column.getZ()<=area.high().getZ()){
                if(area.low().getY()>low&&area.low().getY()<high)cuts.add(area.low().getY());
                long end=(long)area.high().getY()+1;if(end>low&&end<high)cuts.add((int)end);
            }
            // Removing one overlapping rule must not merge previously frozen phases into a new phase.
            if(frames!=null)for(var entry:frames.entrySet()){
                int start=entry.getKey(),end=entry.getValue().high();if(start>low&&start<high)cuts.add(start);if(end>low&&end<high)cuts.add(end);
            }
            float time=ticks+partial;float[] original=phase(time);Integer previous=null;
            for(int end:cuts){if(previous==null){previous=end;continue;}int start=previous;previous=end;if(end<=start)continue;
                BlockPos selected=new BlockPos(column.getX(),start,column.getZ());
                float frozen=worldRendererClock(time);
                float anchor=0;
                if(selectedWorldPosition(selected)){
                    if(frames==null){frames=new TreeMap<>();weatherFrames.put(key,frames);}
                    WeatherFrame frame=frames.get(start);
                    if(frame==null){
                        for(var prior=frames.floorEntry(start);prior!=null;prior=frames.lowerEntry(prior.getKey()))if(prior.getValue().high()>start){frame=prior.getValue();break;}
                        if(frame==null)frame=new WeatherFrame(frozenTime(selected,time),low+high,end);
                        frames.put(start,frame);
                    }
                    frozen=frame.time();anchor=(frame.spanSum()-(low+high))*.25F;
                }
                float[] selectedPhase=phase(frozen);float du=selectedPhase[0]-original[0],dv=selectedPhase[1]-original[1]+anchor;
                segment(0,end,low,high,du,dv);segment(1,end,low,high,du,dv);segment(2,start,low,high,du,dv);segment(3,start,low,high,du,dv);
            }
        }
        private float frozenTime(BlockPos position,float current){
            float time=worldRendererClock(current);boolean found=false;
            for(ProNetwork.WorldArea area:worldAreas)if(area.contains(position)){float started=worldRuleClocks.getOrDefault(area.rule(),time);if(!found||started<time){time=started;found=true;}}
            return time;
        }
        private void segment(int index,int y,int low,int high,float du,float dv){
            WeatherVertex vertex=quad.get(index);float amount=(float)(high-y)/(high-low);
            float top=quad.get(index==0||index==3?0:1).v,bottom=quad.get(index==0||index==3?3:2).v;
            put(vertex,y-cameraY,vertex.u+du,top+(bottom-top)*amount+dv);
        }
        private void put(WeatherVertex vertex,double y,float u,float v){builder.vertex(vertex.x,y,vertex.z).uv(u,v).color(vertex.r,vertex.g,vertex.b,vertex.a).uv2(vertex.lightX,vertex.lightY).endVertex();}
    }
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
        worldFrames.entrySet().removeIf(entry->{
            if(activeWorld(mc,mc.level)&&selectedWorldObject(mc,entry.getKey()))return false;
            if(entry.getValue().visual!=null)entry.getValue().visual.close();return true;
        });
        ClientWorldVisuals.pruneFrames();
        terrainFrames.entrySet().removeIf(entry->!entry.getValue().isOpen());
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
