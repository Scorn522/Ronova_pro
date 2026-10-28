package dev.ronova.pro;

import java.util.UUID;
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
    private static ProMod instance;
    private final java.util.Map<UUID,WeaponSession> weaponSessions=new java.util.HashMap<>();
    private final ThreadLocal<Authority> weaponAuthority=new ThreadLocal<>();
    private static final org.slf4j.Logger LOG=com.mojang.logging.LogUtils.getLogger();
    private record Feedback(UUID player,String last,long next,boolean failed) { }
    private final java.util.LinkedHashMap<UUID,Feedback> feedback=new java.util.LinkedHashMap<>();
    private final java.util.ArrayDeque<UUID> feedbackReady=new java.util.ArrayDeque<>();
    private void watch(net.minecraft.commands.CommandSourceStack source,ProRuntime runtime,UUID operation,net.minecraft.world.entity.Entity target) {
        String result=runtime.operationSummary(operation);
        LOG.info("RONOVA_OPERATION {} target={} type={} {}",operation,target.getUUID(),net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(target.getType()),result);
        if(!feedback.containsKey(operation)) {
            if(feedback.size()>=128) {UUID oldest=feedback.keySet().iterator().next();feedback.remove(oldest);feedbackReady.remove(oldest);}
            feedbackReady.addLast(operation);
        }
        feedback.put(operation,new Feedback(source.getEntity() instanceof ServerPlayer p?p.getUUID():null,result,runtime.server.getTickCount()+10L,false));
    }
    public ProMod() {
        // The launcher supplies premain automatically. A late attachment is reported as runtime continuation.
        ProBootstrap.install();
        bindControlOwner();
        try {
            Class<?> bridge=Class.forName("dev.ronova.pro.bootstrap.EventBusBridge",true,null);
            java.lang.invoke.MethodHandles.publicLookup().findStatic(bridge,"install",
                    java.lang.invoke.MethodType.methodType(void.class,Class.class,Class.class))
                    .invokeExact((Class<?>)ProMod.class,(Class<?>)MinecraftForge.class);
        }catch(Throwable failure){throw new IllegalStateException("RONOVA_FORGE_BUS_PROTECTION_UNAVAILABLE",failure);}
        net.minecraftforge.fml.DistExecutor.unsafeRunWhenOn(net.minecraftforge.api.distmarker.Dist.CLIENT,()->ClientFaults::install);
        ProNetwork.init();
        instance=this;
        ProWeapon.register(net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext.get().getModEventBus());
        var bus=MinecraftForge.EVENT_BUS;
        bus.addListener(this::starting);
        bus.addListener(this::stopping);
        bus.addListener(this::stopped);
        bus.addListener(EventPriority.NORMAL,this::tick);
        bus.addListener(EventPriority.LOWEST,this::feedbackTick);
        bus.addListener(this::passiveTick);
        bus.addListener(this::weaponAttack);
        bus.addListener(this::weaponBlock);
        bus.addListener(EventPriority.LOWEST,this::joined);
        bus.addListener(this::participant);
        bus.addListener(this::playerClone);
        // Fail during Mod construction rather than after generating the spawn area.
        // ProRuntime still requires the same completed preparation before publishing policy.
        try{RuntimeClasses.prepare();}
        catch(ReflectiveOperationException failure){throw new IllegalStateException("RONOVA_RUNTIME_PRELOAD_UNAVAILABLE",failure);}
    }
    private static void bindControlOwner(){
        try {
            Class.forName("dev.ronova.pro.agent.RecoveryAgent",false,ClassLoader.getSystemClassLoader())
                    .getMethod("bindModGroupCallers",Class.class,Class.class)
                    .invoke(null,ProMod.class,ProRuntime.class);
        } catch(ReflectiveOperationException unavailable) {
            LOG.warn("RONOVA_MOD_GROUP_AUTHORITY_UNAVAILABLE",unavailable);
        }
    }
    private void starting(ServerStartingEvent e) {
        try { ProRuntime.start(e.getServer());ProRuntime.declareDispatchTargets(); }
        catch(Exception ex) { throw new IllegalStateException("Ronova Pro durable authority unavailable; refusing writes",ex); }
    }
    private void stopped(ServerStoppedEvent e) {
        weaponSessions.clear();weaponAuthority.remove();passives.clear();strikes.clear();linkedStop=false;
        feedback.clear();feedbackReady.clear();
        try { ProRuntime.stop(e.getServer()); }
        catch(Exception ex) { System.err.println("RONOVA_PRO_UNSETTLED_SHUTDOWN "+ex); }
    }
    private void stopping(ServerStoppingEvent e) {
        ProRuntime runtime=ProRuntime.get(e.getServer());if(runtime!=null)runtime.beginShutdown();
    }
    private void tick(TickEvent.ServerTickEvent e) {
        if(e.phase==TickEvent.Phase.START) {
            ProRuntime r=ProRuntime.get(e.getServer());if(r!=null)r.beforeWorldTick();
        }
        if(e.phase==TickEvent.Phase.END) {
            ProRuntime r=ProRuntime.get(e.getServer());if(r!=null)r.tick();
        }
    }
    /** Human feedback has its own bounded turn and never participates in enforcement. */
    private void feedbackTick(TickEvent.ServerTickEvent e) {
        if(e.phase!=TickEvent.Phase.END||feedbackReady.isEmpty())return;
        ProRuntime runtime=ProRuntime.get(e.getServer());if(runtime==null)return;
        UUID operation=feedbackReady.removeFirst();Feedback previous=feedback.get(operation);
        if(previous==null)return;feedbackReady.addLast(operation);
        long nowTick=e.getServer().getTickCount();if(nowTick<previous.next()&&previous.next()-nowTick<=100)return;
        try {
            String now=runtime.operationSummary(operation);
            if(!now.equals(previous.last())) {
                LOG.info("RONOVA_OPERATION {} {}",operation,now);
                var player=previous.player()==null?null:e.getServer().getPlayerList().getPlayer(previous.player());
                if(player!=null)player.sendSystemMessage(Component.literal("Ronova "+operation+"："+now));
            }
            feedback.put(operation,new Feedback(previous.player(),now,nowTick+10,false));
        } catch(RuntimeException|LinkageError unavailable) {
            if(!previous.failed())LOG.warn("RONOVA_OPERATION_FEEDBACK_UNAVAILABLE {} {}",operation,unavailable.toString());
            feedback.put(operation,new Feedback(previous.player(),previous.last(),nowTick+100,true));
        }
    }
    private void joined(EntityJoinLevelEvent e) { if(!e.getLevel().isClientSide)ProRuntime.joined(e.getEntity()); }
    private void participant(PlayerEvent event) {
        if(!(event instanceof PlayerEvent.PlayerLoggedOutEvent || event instanceof PlayerEvent.PlayerLoggedInEvent
                || event instanceof PlayerEvent.PlayerChangedDimensionEvent || event instanceof PlayerEvent.PlayerRespawnEvent))return;
        if(event.getEntity() instanceof ServerPlayer p) {
            weaponSessions.remove(p.getUUID());
            if(event instanceof PlayerEvent.PlayerLoggedOutEvent){passives.remove(p.getUUID());strikes.remove(p.getUUID());}
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
    private static final StackWalker WEAPON_CALLER=StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE);
    private static final UUID NONE=new UUID(0,0);
    private final java.util.Map<UUID,PassiveState> passives=new java.util.HashMap<>();
    private final java.util.Map<UUID,Long> strikes=new java.util.HashMap<>();
    private boolean linkedStop;
    private static final class PassiveState {
        final ServerPlayer player;final boolean priorFlight;UUID protection;boolean grantedFlight;
        PassiveState(ServerPlayer player){this.player=player;priorFlight=player.getAbilities().mayfly;}
    }
    private record Authority(ServerPlayer player,PassiveState passive) {}
    private record Selection(String kind,String mod,UUID id,java.lang.ref.WeakReference<net.minecraft.world.entity.Entity> entity,java.util.Set<WeaponAction> actions) {}
    private record Row(String title,String state,Selection selection) {}
    private static final class WeaponSession {
        final ServerPlayer player;final UUID nonce=UUID.randomUUID();
        final java.util.Map<UUID,Selection> rows=new java.util.HashMap<>();
        long expires,sequence,nextTick;int tab,page,size=3;
        WeaponSession(ServerPlayer player){this.player=player;expires=player.server.getTickCount()+12000L;}
    }
    private static boolean online(ServerPlayer player){return player!=null&&player.server.isSameThread()&&player.server.getPlayerList().getPlayer(player.getUUID())==player&&ProRuntime.get(player.server)!=null;}
    private static boolean weaponPlayer(ServerPlayer player){return online(player)&&player.hasPermissions(2)&&ProWeapon.carried(player);}
    static boolean weaponActionAuthorized(ProRuntime runtime){
        ProMod mod=instance;Authority authority=mod==null?null:mod.weaponAuthority.get();
        if(authority==null||!online(authority.player())||ProRuntime.get(authority.player().server)!=runtime)return false;
        return authority.passive()==null?weaponPlayer(authority.player()):mod.passives.get(authority.player().getUUID())==authority.passive();
    }
    @FunctionalInterface private interface WeaponWork {void run(net.minecraft.commands.CommandSourceStack source)throws Exception;}
    private String weaponRun(ServerPlayer player,PassiveState passive,WeaponWork work){
        if(!(passive==null?weaponPlayer(player):online(player)&&passives.get(player.getUUID())==passive)||weaponAuthority.get()!=null)throw new SecurityException("RONOVA_WEAPON_AUTHORITY_REQUIRED");
        StringBuilder result=new StringBuilder();
        net.minecraft.commands.CommandSource output=new net.minecraft.commands.CommandSource(){
            public void sendSystemMessage(Component message){result.append(message.getString()).append('\n');}
            public boolean acceptsSuccess(){return true;}public boolean acceptsFailure(){return true;}public boolean shouldInformAdmins(){return false;}
        };
        weaponAuthority.set(new Authority(player,passive));
        try{work.run(player.createCommandSourceStack().withSource(output));}
        catch(Exception failure){result.append("操作未完成：").append(failure.getMessage()==null?failure.getClass().getSimpleName():failure.getMessage());LOG.warn("RONOVA_WEAPON_ACTION {} {}",player.getUUID(),failure.toString());}
        finally{weaponAuthority.remove();}
        return result.isEmpty()?"操作已执行。":result.toString();
    }
    private void passiveTick(TickEvent.PlayerTickEvent event){
        if(event.phase!=TickEvent.Phase.END||!(event.player instanceof ServerPlayer player)||!online(player))return;
        PassiveState state=passives.get(player.getUUID());boolean carried=ProWeapon.carried(player);
        if(carried&&player.isAlive()){
            if(state==null||state.player!=player){state=new PassiveState(player);passives.put(player.getUUID(),state);}
            PassiveState current=state;ProRuntime runtime=ProRuntime.get(player.server);
            if(!runtime.weaponProtected(player))weaponRun(player,current,source->{current.protection=runtime.protect(player);});
            if(!player.getAbilities().mayfly){player.getAbilities().mayfly=true;current.grantedFlight=true;player.onUpdateAbilities();}
        }else if(state!=null){
            PassiveState current=state;ProRuntime runtime=ProRuntime.get(player.server);
            if(current.protection!=null&&runtime.weaponOwnProtection(player,current.protection)&&!runtime.weaponNoHeal(player))weaponRun(player,current,source->runtime.cancel(current.protection));
            if(current.grantedFlight&&!current.priorFlight&&!player.isCreative()&&!player.isSpectator()){player.getAbilities().mayfly=false;player.getAbilities().flying=false;player.onUpdateAbilities();}
            passives.remove(player.getUUID());
        }
    }
    static void openWeaponPanel(ServerPlayer player){
        if(WEAPON_CALLER.getCallerClass()!=ProNetwork.class)throw new SecurityException("RONOVA_WEAPON_PACKET_REQUIRED");
        if(instance!=null)instance.openPanel(player);
    }
    private void openPanel(ServerPlayer player){
        if(!weaponPlayer(player)){player.sendSystemMessage(Component.literal("携带 Ronova Pro 武器并拥有管理员权限后才能操作控制台。"));return;}
        WeaponSession session=new WeaponSession(player);weaponSessions.put(player.getUUID(),session);sendPanel(session,"左键：96 格视线与近身抹除；右键：64 格清场。背包携带时自动守护与允许飞行。");
    }
    private static Row row(String title,String state,String kind,String mod,UUID id,net.minecraft.world.entity.Entity entity,WeaponAction... actions){
        return new Row(title,state,new Selection(kind,mod,id,entity==null?null:new java.lang.ref.WeakReference<>(entity),java.util.Set.of(actions)));
    }
    private java.util.List<Row> rows(ServerPlayer player,int tab){
        ProRuntime runtime=ProRuntime.get(player.server);java.util.List<Row> rows=new java.util.ArrayList<>();
        switch(tab){
            case 0->{for(var mod:net.minecraftforge.fml.ModList.get().getMods())if(!java.util.Set.of("minecraft","forge","ronova_pro").contains(mod.getModId()))rows.add(row(mod.getDisplayName()+" · "+mod.getModId(),runtime.weaponModState(mod.getModId()),"mod",mod.getModId(),NONE,null,WeaponAction.MOD_STOP,WeaponAction.MOD_RETIRE,WeaponAction.MOD_PROTECT,WeaponAction.MOD_STATUS));}
            case 1->{
                for(WeaponEntry entry:runtime.weaponRules())rows.add(row(entry.title(),entry.state(),"rule","",entry.id(),null,WeaponAction.RULE_RESUME));
                for(WeaponEntry entry:runtime.weaponWorldJobs())rows.add(row(entry.title(),entry.state(),"world","",entry.id(),null,WeaponAction.WORLD_ROLLBACK,WeaponAction.WORLD_STATUS,WeaponAction.WORLD_CANCEL,WeaponAction.WORLD_RETRY));
            }
            case 2->{
                var entities=player.serverLevel().getEntities(player,player.getBoundingBox().inflate(64),e->!(e instanceof net.minecraft.world.entity.player.Player)&&!e.isRemoved()&&e.distanceToSqr(player)<=64*64);
                entities.sort(java.util.Comparator.comparingDouble(player::distanceToSqr));
                for(var entity:entities)rows.add(row(entity.getName().getString()+" · "+net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()),(runtime.weaponProtected(entity)?"已保护":"未保护")+" · "+(int)Math.sqrt(entity.distanceToSqr(player))+" 格","entity","",entity.getUUID(),entity,WeaponAction.ENTITY_CLEAR,WeaponAction.ENTITY_PROTECT,WeaponAction.ENTITY_MOD_STOP,WeaponAction.ENTITY_FIELDS,WeaponAction.ENTITY_FIELDS_CLEAR,WeaponAction.ENTITY_NO_HEAL,WeaponAction.ENTITY_TELEPORT));
                for(WeaponEntry entry:runtime.weaponFields())rows.add(row(entry.title(),entry.state(),"fields","",entry.id(),null,WeaponAction.FIELD_RESTORE,WeaponAction.FIELD_FORGET,WeaponAction.FIELD_STATUS));
            }
            case 3->{for(WeaponEntry entry:runtime.weaponOperations())rows.add(row(entry.title(),entry.state(),"operation","",entry.id(),null,WeaponAction.OP_CANCEL,WeaponAction.OP_STATUS,WeaponAction.OP_RECOVERY,WeaponAction.OP_HISTORY,WeaponAction.OP_REQUIRED,WeaponAction.OP_OPTIONAL));}
            default->throw new IllegalArgumentException("未知控制台页");
        }
        return rows;
    }
    private void sendPanel(WeaponSession session,String message){
        if(!weaponPlayer(session.player))return;
        var all=rows(session.player,session.tab);int pages=Math.max(1,(all.size()+session.size-1)/session.size);session.page=Math.max(0,Math.min(session.page,pages-1));session.rows.clear();
        var output=new java.util.ArrayList<ProNetwork.WeaponRow>();int start=session.page*session.size;
        for(Row row:all.subList(start,Math.min(all.size(),start+session.size))){UUID key=UUID.randomUUID();session.rows.put(key,row.selection());output.add(new ProNetwork.WeaponRow(key,row.title(),row.state(),row.selection().actions().stream().sorted().mapToInt(Enum::ordinal).toArray()));}
        var runtime=ProRuntime.get(session.player.server);
        ProNetwork.panel(session.player,new ProNetwork.WeaponPanel(session.nonce,session.sequence,session.tab,session.page,pages,linkedStop,!runtime.weaponClockRules().isEmpty(),runtime.weaponHostProtected(),output,message.length()>2000?message.substring(0,2000):message));
    }
    static void weaponPacket(ServerPlayer player,ProNetwork.WeaponRequest request){
        if(WEAPON_CALLER.getCallerClass()!=ProNetwork.class)throw new SecurityException("RONOVA_WEAPON_PACKET_REQUIRED");
        ProMod mod=instance;if(mod==null||!weaponPlayer(player))return;
        WeaponSession session=mod.weaponSessions.get(player.getUUID());long now=player.server.getTickCount();
        if(session==null||session.player!=player||!session.nonce.equals(request.session())||now>session.expires)return;
        if(request.sequence()!=session.sequence+1||now<session.nextTick)return;
        session.sequence=request.sequence();session.expires=now+12000;session.nextTick=now+2;
        String message=mod.weaponRun(player,null,source->{
            WeaponAction action=WeaponAction.at(request.action());
            Selection selected=NONE.equals(request.selection())?null:session.rows.get(request.selection());
            if(selected!=null&&!selected.actions().contains(action)||!NONE.equals(request.selection())&&selected==null)throw new IllegalArgumentException("列表已变化，先刷新再操作");
            session.tab=Math.max(0,Math.min(3,request.tab()));session.page=Math.max(0,request.page());session.size=Math.max(1,Math.min(8,request.size()));
            mod.panelAction(player,source,session,action,selected);
        });
        ProNetwork.weaponResult(player,session.nonce,session.sequence,message);mod.sendPanel(session,message);
    }
    private net.minecraft.world.entity.Entity selectedEntity(ServerPlayer player,Selection selected){
        if(selected==null||!selected.kind().equals("entity")||selected.entity()==null)throw new IllegalArgumentException("实体选择已失效");
        var target=selected.entity().get();
        if(target==null||target.isRemoved()||player.serverLevel().getEntity(selected.id())!=target||target.distanceToSqr(player)>64*64)throw new IllegalArgumentException("目标已离场或超过 64 格");return target;
    }
    private void panelAction(ServerPlayer player,net.minecraft.commands.CommandSourceStack source,WeaponSession session,WeaponAction action,Selection selected)throws Exception {
        ProRuntime runtime=ProRuntime.get(player.server);String message="";UUID id=selected==null?null:selected.id();
        switch(action){
            case REFRESH->message="列表已刷新。";
            case LINK_STOP->{if(selected!=null)throw new IllegalArgumentException("无效开关");linkedStop=!linkedStop;message="攻击连带停 Mod："+(linkedStop?"开":"关");}
            case TIME_STOP->{var rules=runtime.weaponClockRules();if(!rules.isEmpty()){for(UUID rule:rules)runtime.worldResume(rule);message="全维度时停已关闭。";}else{var level=player.serverLevel();id=runtime.worldRule(level,new net.minecraft.core.BlockPos(-30000000,level.getMinBuildHeight(),-30000000),new net.minecraft.core.BlockPos(30000000,level.getMaxBuildHeight()-1,30000000),Integer.MAX_VALUE,true,true);message="全维度时停已开启。";}}
            case HOST->message=runtime.hostJvmProtection(!runtime.weaponHostProtected());
            case DIAGNOSTICS->message="引导："+ProBootstrap.state()+"\n分派："+ProRuntime.dispatchState()+"\n存储："+StorageNative.capability()+"\n"+runtime.worldState();
            case REPLAY->{UUID nonce=session.nonce;long sequence=session.sequence;runtime.weaponReplay().whenComplete((text,failure)->player.server.execute(()->{WeaponSession live=weaponSessions.get(player.getUUID());if(live==session&&weaponPlayer(player)){String result=failure==null?text:"重放核对失败："+failure;ProNetwork.weaponResult(player,nonce,sequence,result);}}));message="已提交只读重放核对；不会再次执行清除/恢复动作。";}
            case MOD_STOP,MOD_RETIRE,MOD_PROTECT,MOD_STATUS->{if(selected==null||!selected.kind().equals("mod"))throw new IllegalArgumentException("Mod 选择已失效");message=action==WeaponAction.MOD_PROTECT?runtime.protectModGroup(selected.mod()):action==WeaponAction.MOD_STATUS?runtime.modGroupState(selected.mod()):runtime.stopModGroup(selected.mod());if(action==WeaponAction.MOD_RETIRE)message+=";HANDOFF=RETIREMENT_REQUESTED;NEXT_JVM_REQUIRES_EXPLICIT_ARTIFACT_HANDOFF";}
            case FREEZE,SPAWN_BAN->{var position=player.blockPosition();id=runtime.worldRule(player.serverLevel(),position.offset(-32,-32,-32),position.offset(32,32,32),Integer.MAX_VALUE,action==WeaponAction.FREEZE,false);message="区域规则已开启，可在列表中一键解除。";}
            case UNLOAD->message=runtime.weaponUnload(player);
            case SNAPSHOT,REGENERATE,REGENERATE_TWO->{var level=player.serverLevel();int radius=action==WeaponAction.SNAPSHOT?1:action==WeaponAction.REGENERATE_TWO?2:0;var chunk=player.chunkPosition();id=runtime.worldSnapshot(level,new net.minecraft.core.BlockPos((chunk.x-radius)*16,level.getMinBuildHeight(),(chunk.z-radius)*16),new net.minecraft.core.BlockPos((chunk.x+radius)*16+15,level.getMaxBuildHeight()-1,(chunk.z+radius)*16+15),action!=WeaponAction.SNAPSHOT);message="世界工作已受理："+id;}
            case RULE_RESUME->{requireSelection(selected,"rule");message=runtime.worldResume(id)?"区域规则已解除。":"该规则已经结束。";}
            case WORLD_ROLLBACK,WORLD_STATUS,WORLD_CANCEL,WORLD_RETRY->{requireSelection(selected,"world");message=switch(action){case WORLD_ROLLBACK->"回溯已受理："+runtime.worldRollback(id);case WORLD_STATUS->runtime.worldHistoryState(id);case WORLD_CANCEL->"取消结果："+runtime.worldHistoryCancel(id);default->"重试结果："+runtime.worldHistoryRetry(id);};}
            case ENTITY_CLEAR,ENTITY_PROTECT,ENTITY_MOD_STOP,ENTITY_FIELDS,ENTITY_FIELDS_CLEAR,ENTITY_NO_HEAL,ENTITY_TELEPORT->{var target=selectedEntity(player,selected);switch(action){
                case ENTITY_CLEAR->dispose(player,source,java.util.List.of(target));
                case ENTITY_PROTECT->{if(runtime.weaponProtected(target)){runtime.revoke(target);message="保护已解除。";}else{id=runtime.protect(target);watch(source,runtime,id,target);message="保护已开启。";}}
                case ENTITY_MOD_STOP->{String ids=runtime.weaponTargetMods(target);message=ids.isEmpty()?"目标没有可停的第三方 Mod 归属。":runtime.stopModGroup(ids);}
                case ENTITY_FIELDS,ENTITY_FIELDS_CLEAR->{id=runtime.fieldImage(java.util.List.of(target),false,action==WeaponAction.ENTITY_FIELDS_CLEAR);message="字段图工作已受理："+id;}
                case ENTITY_NO_HEAL->message="禁疗设置结果："+runtime.noHeal(target,!runtime.weaponNoHeal(target));
                case ENTITY_TELEPORT->message="传送结果："+runtime.worldTeleport(target,player.serverLevel(),player.position().add(player.getLookAngle().scale(2)));
                default->throw new IllegalArgumentException("未知实体动作");
            }}
            case FIELD_RESTORE,FIELD_FORGET,FIELD_STATUS->{requireSelection(selected,"fields");message=action==WeaponAction.FIELD_RESTORE?"恢复结果："+runtime.fieldRestore(id):action==WeaponAction.FIELD_FORGET?"释放结果："+runtime.fieldForget(id):runtime.fieldState(id);}
            case OP_CANCEL,OP_STATUS,OP_RECOVERY,OP_HISTORY,OP_REQUIRED,OP_OPTIONAL->{requireSelection(selected,"operation");switch(action){
                case OP_CANCEL->{runtime.cancel(id);message="操作已撤销；已发生作用保留在历史中。";}
                case OP_STATUS->message=runtime.operationSummary(id);
                case OP_RECOVERY->message=String.join("\n",runtime.recoveryStatus(id));
                case OP_HISTORY->message=String.join("\n",runtime.historicalOperation(id));
                case OP_REQUIRED,OP_OPTIONAL->{for(ServerPlayer participant:player.server.getPlayerList().getPlayers())runtime.participant(id,participant.getUUID(),action==WeaponAction.OP_REQUIRED);message="已更新当前在线客户端的参与要求，已有必要客户端不会降级。";}
                default->throw new IllegalArgumentException("未知操作");
            }}
        }
        if(!message.isEmpty()){String text=message;source.sendSuccess(()->Component.literal(text),false);}
    }
    private static void requireSelection(Selection selected,String kind){if(selected==null||!selected.kind().equals(kind))throw new IllegalArgumentException("列表目标已失效");}
    private boolean weaponTurn(ServerPlayer player){if(!weaponPlayer(player)||!ProWeapon.held(player))return false;long now=player.server.getTickCount();if(now<strikes.getOrDefault(player.getUUID(),0L))return false;strikes.put(player.getUUID(),now+4);return true;}
    static void useWeapon(ServerPlayer player){
        if(WEAPON_CALLER.getCallerClass()!=ProWeapon.class)throw new SecurityException("RONOVA_WEAPON_ITEM_REQUIRED");
        if(!WEAPON_CALLER.walk(frames->frames.anyMatch(f->f.getDeclaringClass()==net.minecraft.server.level.ServerPlayerGameMode.class)))return;
        ProMod mod=instance;if(mod==null)return;if(player.isShiftKeyDown()){mod.openPanel(player);return;}if(!mod.weaponTurn(player))return;
        String result=mod.weaponRun(player,null,source->{var targets=player.serverLevel().getEntities(player,player.getBoundingBox().inflate(64),e->!(e instanceof net.minecraft.world.entity.player.Player)&&!e.isRemoved()&&e.distanceToSqr(player)<=64*64);mod.dispose(player,source,targets);});player.displayClientMessage(Component.literal(result),true);player.sendSystemMessage(Component.literal(result));
    }
    private void dispose(ServerPlayer player,net.minecraft.commands.CommandSourceStack source,java.util.Collection<? extends net.minecraft.world.entity.Entity> targets){
        ProRuntime runtime=ProRuntime.get(player.server);java.util.Set<String> stop=new java.util.LinkedHashSet<>();int count=0;
        for(var target:java.util.List.copyOf(targets)){if(linkedStop){String ids=runtime.weaponTargetMods(target);if(!ids.isEmpty())stop.addAll(java.util.List.of(ids.split(",")));}UUID id=runtime.clear(target);watch(source,runtime,id,target);count++;}
        int accepted=count;source.sendSuccess(()->Component.literal("已受理身体抹除："+accepted+" 个目标。"),false);
        if(!stop.isEmpty()){String state=runtime.stopModGroup(String.join(",",stop));source.sendSuccess(()->Component.literal(state),false);}
    }
    static void swingPacket(ServerPlayer player){if(WEAPON_CALLER.getCallerClass()!=ProNetwork.class)throw new SecurityException("RONOVA_WEAPON_PACKET_REQUIRED");if(instance!=null)instance.swing(player,null);}
    private void swing(ServerPlayer player,net.minecraft.core.BlockPos block){
        if(!weaponTurn(player))return;
        String result=weaponRun(player,null,source->{
            var runtime=ProRuntime.get(player.server);var targets=new java.util.LinkedHashSet<net.minecraft.world.entity.Entity>();var ray=runtime.weaponRayTarget(player,96);
            if(ray!=null)targets.add(ray);
            for(var entity:player.serverLevel().getEntities(player,player.getBoundingBox().inflate(4),e->!e.isRemoved())){var delta=entity.position().subtract(player.position());if(delta.lengthSqr()<=16&&delta.dot(player.getLookAngle())>=0)targets.add(entity);}
            if(!targets.isEmpty())dispose(player,source,targets);
            net.minecraft.core.BlockPos selected=block;
            if(selected==null&&ray==null&&player.pick(96,0,false) instanceof net.minecraft.world.phys.BlockHitResult hit&&hit.getType()==net.minecraft.world.phys.HitResult.Type.BLOCK)selected=hit.getBlockPos();
            if(selected!=null){UUID id=runtime.clearBlock(player.serverLevel(),selected);source.sendSuccess(()->Component.literal("方块处置已受理："+id),false);}
            else if(targets.isEmpty())source.sendSuccess(()->Component.literal("视线与前方没有目标。"),false);
        });player.displayClientMessage(Component.literal(result),true);
    }
    private void weaponAttack(net.minecraftforge.event.entity.player.AttackEntityEvent event){if(event.getEntity() instanceof ServerPlayer player&&ProWeapon.held(player)){event.setCanceled(true);if(WEAPON_CALLER.walk(frames->frames.anyMatch(f->f.getDeclaringClass()==net.minecraft.server.network.ServerGamePacketListenerImpl.class)))swing(player,null);}}
    private void weaponBlock(net.minecraftforge.event.entity.player.PlayerInteractEvent.LeftClickBlock event){if(event.getEntity() instanceof ServerPlayer player&&ProWeapon.held(player)){event.setCanceled(true);if(WEAPON_CALLER.walk(frames->frames.anyMatch(f->f.getDeclaringClass()==net.minecraft.server.level.ServerPlayerGameMode.class)))swing(player,event.getPos());}}
}
