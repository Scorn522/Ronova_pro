package dev.ronova.pro;

import dev.ronova.pro.mixin.Access;
import dev.ronova.pro.mixin.ClientModGroupHooks;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReferenceArray;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;

/** Locally authorized whole-Mod client policy and actual current presentation roots. */
public final class ClientModGroups {
    private static final Set<Module> stopped=ConcurrentHashMap.newKeySet(),protectedModules=ConcurrentHashMap.newKeySet();
    private static final Map<String,Run> runs=new LinkedHashMap<>();
    private static final ConcurrentLinkedQueue<Observation> observations=new ConcurrentLinkedQueue<>();
    private static volatile Run[] observing=new Run[0];
    private static ClientLevel world;
    private static boolean bootLoaded;
    private static long tick,runOrder;
    private static java.lang.reflect.Method origins,unknownOrigin,moduleQuery;
    private record Observation(Object value,ClientLevel world,Object connection,Run[] runs) { }
    private static final class Work {
        final Object value;final ClientLevel level;final boolean inventory;
        long ready;String gap="";
        Object soundEngine;net.minecraft.client.sounds.ChannelAccess.ChannelHandle handle;boolean soundCaptured;
        ClientPresence.GroupRetirement body;LevelChunk blockChunk;net.minecraft.core.BlockPos blockPos;Object blockTicker;boolean blockCaptured;
        Work(Object value,ClientLevel level,boolean inventory){this.value=value;this.level=level;this.inventory=inventory;ready=tick+1;}
    }
    private static final class Run {
        final ProNetwork.ModGroup message;final boolean reply;final Object connection;final long order=++runOrder;
        final Set<Module> modules=Collections.newSetFromMap(new IdentityHashMap<>());
        final Map<Object,Work> pending=new IdentityHashMap<>();final ArrayDeque<Work> queue=new ArrayDeque<>();
        final Map<Object,Boolean> completed=new WeakIdentityMap<>();final AtomicInteger incoming=new AtomicInteger();
        int cleared,protectedCount,retries,leftWorld;long sequence,sentAt;String code="WAITING_FOR_MODULE_POLICY",policyGap="",captureGap="",sent;
        boolean capture=true,policyReady,retirementReady,superseded;
        Run(ProNetwork.ModGroup message,boolean reply,Object connection){this.message=message;this.reply=reply;this.connection=connection;}
        String state(){
            int failed=(policyGap.isEmpty()?0:1)+(captureGap.isEmpty()?0:1);
            for(Work work:pending.values())if(!work.gap.isEmpty())failed++;
            boolean stock=!capture&&pending.isEmpty()&&incoming.get()==0;
            return "FINAL="+(superseded?"SUPERSEDED":policyReady&&retirementReady&&stock?"COMPLETE":"PENDING")
                    +";STOCK="+(stock?"ENUMERATED":"PENDING")+";RETIREMENT="+(retirementReady?"COMPLETE":"PENDING")
                    +";CLEARED="+cleared+";PROTECTED="+protectedCount+";LEFT_WORLD="+leftWorld+";FAILED="+failed
                    +";PENDING_OBJECTS="+pending.size()+";HANDOFF="+incoming.get()+";RETRIES="+retries
                    +";POLICY_GAP="+policyGap+";CAPTURE_GAP="+captureGap+";CODE="+code;
        }
    }
    private ClientModGroups(){}
    private static Class<?> agent()throws ReflectiveOperationException{return Class.forName("dev.ronova.pro.agent.RecoveryAgent",false,ClassLoader.getSystemClassLoader());}
    private static Module owner(Object value,Set<Module> modules){
        if(value==null||modules.isEmpty())return null;
        try{if(moduleQuery==null)moduleQuery=Class.forName("dev.ronova.pro.bootstrap.DefinitionBridge",false,null).getMethod("module",Class.class);
            Module direct=(Module)moduleQuery.invoke(null,value instanceof Class<?> type?type:value.getClass());if(modules.contains(direct))return direct;
            if(origins==null){Class<?> bridge=Class.forName("dev.ronova.pro.bootstrap.ControlBridge",false,null);origins=bridge.getMethod("creationSources",Object.class);unknownOrigin=bridge.getMethod("objectSourceUnknown",Object.class);}
            if(Boolean.TRUE.equals(unknownOrigin.invoke(null,value)))return null;
            Module[] sources=(Module[])origins.invoke(null,value);if(sources.length==0)return null;
            for(Module source:sources)if(source==null||!modules.contains(source))return null;
            return sources[0];
        }catch(ReflectiveOperationException unavailable){/* An unobserved origin grants no authority over a shared carrier. */}
        return null;
    }
    public static boolean stopped(Object value){return owner(value,stopped)!=null;}
    public static boolean protectedObject(Object value){return !stopped(value)&&owner(value,protectedModules)!=null;}
    private static Object transport(Minecraft mc){return mc.getConnection()==null?null:mc.getConnection().getConnection();}
    private static boolean current(Run run){return !run.reply||ClientPresence.modGroupScope(run.connection,run.message.session());}
    private static boolean live(Run run){return !run.superseded&&runs.containsValue(run)&&current(run);}
    private static void enqueue(Run run,Object value,ClientLevel level,boolean publication){
        if(value==null||!live(run)||run.modules.isEmpty())return;
        Minecraft mc=Minecraft.getInstance();
        if(owner(value,run.modules)==null&&value!=mc.player)return;
        if(publication)run.completed.remove(value);
        if(run.pending.containsKey(value)||run.completed.containsKey(value))return;
        Work work=new Work(value,level,value==mc.player);run.pending.put(value,work);run.queue.addLast(work);
    }
    public static void observed(Object value){
        if(value==null)return;Minecraft mc=Minecraft.getInstance();Run[] selected=observing;
        if(mc.isSameThread()){for(Run run:selected)enqueue(run,value,mc.level,true);return;}
        if(selected.length==0)return;
        for(Run run:selected)run.incoming.incrementAndGet();
        observations.add(new Observation(value,mc.level,transport(mc),selected));
    }
    static void apply(ProNetwork.ModGroup message,boolean reply){
        Minecraft mc=Minecraft.getInstance();if(!mc.isSameThread())throw new IllegalStateException("CLIENT_THREAD_REQUIRED");
        Object connection=reply?transport(mc):null;
        if(reply&&!ClientPresence.modGroupScope(connection,message.session()))return;
        Set<String> ids=new HashSet<>(Arrays.asList(message.ids().split(",")));
        for(Run prior:runs.values())if(prior.reply==reply&&(!reply||prior.connection==connection&&prior.message.session().equals(message.session()))
                &&Arrays.stream(prior.message.ids().split(",")).anyMatch(ids::contains)){
            if(reply&&prior.message.generation()>message.generation())return;
            if(prior.message.generation()==message.generation()&&prior.message.ids().equals(message.ids())
                    &&prior.message.protect()==message.protect()&&prior.message.returns().equals(message.returns())){notify(prior);return;}
            supersede(prior);
        }
        Run run=new Run(message,reply,connection);
        runs.put((reply?"REMOTE:":"LOCAL:")+message.ids()+":"+message.protect()+":"+message.generation(),run);
        observing=runs.values().stream().filter(value->!value.superseded).toArray(Run[]::new);
        refresh(run);notify(run);
    }
    private static void supersede(Run run){run.superseded=true;run.pending.clear();run.queue.clear();run.capture=false;notify(run);}
    static void disconnected(){
        runs.entrySet().removeIf(entry->entry.getValue().reply);world=null;
        for(Run run:runs.values())run.capture=true;
        observing=runs.values().stream().filter(value->!value.superseded).toArray(Run[]::new);
    }
    private static boolean state(String value,String policy){return (";"+value+";").contains(";STATE="+policy+";");}
    private static void refresh(Run run){
        if(!live(run))return;
        try{
            String[] ids=run.message.ids().split(",");Class<?> api=agent();
            Module[] actual=(Module[])api.getMethod("modGroupModules",String[].class).invoke(null,(Object)ids);
            if(actual.length==0)throw new IllegalStateException("CLIENT_MODULE_SELECTION_PENDING");
            for(Run newer:runs.values())if(newer!=run&&!newer.superseded&&newer.reply==run.reply
                    &&(!run.reply||newer.connection==run.connection&&newer.message.session().equals(run.message.session()))
                    &&(newer.order>run.order||run.reply&&newer.message.generation()>run.message.generation())
                    &&!Collections.disjoint(newer.modules,Arrays.asList(actual))){supersede(run);return;}
            String expected=run.message.protect()?"PROTECTED":"STOPPED";boolean published=true;
            for(Module module:actual){String value=String.valueOf(api.getMethod("modGroupState",Module.class).invoke(null,module));
                if(!state(value,expected)&&!state(value,expected+"_PARTIAL"))published=false;}
            if(!published){
                if(run.message.protect())api.getMethod("protectModIds",String[].class).invoke(null,(Object)ids);
                else api.getMethod("stopModIds",String[].class,String.class).invoke(null,(Object)ids,run.message.returns());
            }
            List<String> states=new ArrayList<>();boolean all=true,complete=true;
            for(Module module:actual){String value=String.valueOf(api.getMethod("modGroupState",Module.class).invoke(null,module));states.add(value);
                boolean applied=state(value,expected)||state(value,expected+"_PARTIAL");all&=applied;complete&=state(value,expected);
                if(applied){if(run.modules.add(module))run.capture=true;
                    if(run.message.protect())protectedModules.add(module);else{stopped.add(module);protectedModules.remove(module);}}
            }
            run.code=String.join("|",states);run.policyReady=all;run.retirementReady=all&&complete;
            run.policyGap=all?"":"CLIENT_MODULE_POLICY_PUBLICATION_PENDING";
            // A newer command owns any overlapping physical Mod module, including file aliases.
            for(Run prior:runs.values())if(prior!=run&&!prior.superseded&&prior.reply==run.reply
                    &&(!run.reply||prior.connection==run.connection&&prior.message.session().equals(run.message.session()))
                    &&!Collections.disjoint(prior.modules,run.modules)){
                if(prior.order>run.order||run.reply&&prior.message.generation()>run.message.generation()){supersede(run);return;}
                supersede(prior);
            }
        }catch(ReflectiveOperationException|RuntimeException|LinkageError failure){
            run.policyReady=false;run.retirementReady=false;run.policyGap=failure.getClass().getSimpleName()+":"+String.valueOf(failure.getMessage());run.retries++;
        }
    }
    private static void notify(Run run){
        if(!run.reply||!current(run))return;String value=run.state();if(value.equals(run.sent))return;
        if(run.sent!=null&&value.startsWith("FINAL=PENDING;")&&run.sent.startsWith("FINAL=PENDING;")&&tick-run.sentAt<20)return;
        try{ProNetwork.modGroupResult(run.message,run.sequence+1,value);run.sequence++;run.sent=value;run.sentAt=tick;}
        catch(RuntimeException|LinkageError unavailable){/* Keep this generation's unsent result for the next client tick. */}
    }
    /** Local opt-in fault isolation uses the same real Mod selection and stop path. */
    static void isolateFault(Object source){
        if(source==null||System.getProperty("ronova.pro.clientIsolateMods","").isBlank())return;
        Minecraft mc=Minecraft.getInstance();
        if(!mc.isSameThread()){mc.execute(()->isolateFault(source));return;}
        if(stopped(source))return;
        Set<String> allowed=new LinkedHashSet<>();
        for(String raw:System.getProperty("ronova.pro.clientIsolateMods","").split(",")){
            String id=raw.trim().toLowerCase(Locale.ROOT);if(id.matches("[a-z][a-z0-9_]{1,63}"))allowed.add(id);
        }
        Set<String> targets=new LinkedHashSet<>();
        try{
            Class<?> api=agent();
            Module eventOwner=(Module)Class.forName("dev.ronova.pro.bootstrap.ControlBridge",false,null).getMethod("eventListenerModule",Object.class).invoke(null,source);
            for(var info:net.minecraftforge.fml.ModList.get().getMods())if(allowed.contains(info.getModId())){
                Set<String> aliases=new LinkedHashSet<>();aliases.add(info.getModId());
                if(info.getOwningFile()!=null)for(var alias:info.getOwningFile().getMods())aliases.add(alias.getModId());
                Module[] modules=(Module[])api.getMethod("modGroupModules",String[].class).invoke(null,(Object)aliases.toArray(String[]::new));
                if(owner(source,new HashSet<>(Arrays.asList(modules)))!=null||eventOwner!=null&&Arrays.asList(modules).contains(eventOwner))targets.addAll(aliases);
            }
            if(!targets.isEmpty())apply(new ProNetwork.ModGroup(new UUID(0,0),String.join(",",targets),"default",false),false);
        }catch(ReflectiveOperationException|RuntimeException|LinkageError failure){System.err.println("RONOVA_CLIENT_GROUP_FAULT_ISOLATION_FAILED:"+failure);}
    }
    private static void boot(){
        if(bootLoaded)return;bootLoaded=true;
        for(boolean protect:new boolean[]{false,true}){
            String selection=System.getProperty(protect?"ronova.pro.bootProtect":"ronova.pro.bootStop","");
            if(selection.isBlank())continue;
            Map<String,List<String>> modes=new LinkedHashMap<>();
            for(String id:selection.split(","))if(!id.isBlank())modes.computeIfAbsent(protect?"default":System.getProperty("ronova.pro.bootReturns."+id,System.getProperty("ronova.pro.bootReturns","default")),ignored->new ArrayList<>()).add(id);
            for(var mode:modes.entrySet())apply(new ProNetwork.ModGroup(new UUID(0,0),String.join(",",mode.getValue()),mode.getKey(),protect),false);
        }
    }
    private static List<LevelChunk> chunks(ClientLevel level)throws ReflectiveOperationException{
        Object cache=level.getChunkSource();java.lang.reflect.Field storage=null;
        for(var field:PlatformAccess.declaredFields(cache.getClass()))if(java.lang.reflect.Modifier.isVolatile(field.getModifiers())&&field.getType().getEnclosingClass()==cache.getClass()){storage=field;break;}
        if(storage==null)throw new IllegalStateException("CLIENT_CHUNK_STORAGE_UNAVAILABLE");
        agent().getMethod("openBackingAccess",Class.class,Class[].class).invoke(null,ClientModGroups.class,(Object)new Class<?>[]{cache.getClass(),storage.getType()});
        storage.setAccessible(true);Object owner=storage.get(cache);AtomicReferenceArray<?> array=null;
        for(var field:PlatformAccess.declaredFields(owner.getClass()))if(field.getType()==AtomicReferenceArray.class){field.setAccessible(true);array=(AtomicReferenceArray<?>)field.get(owner);break;}
        if(array==null)throw new IllegalStateException("CLIENT_CHUNK_ARRAY_UNAVAILABLE");
        List<LevelChunk> chunks=new ArrayList<>();for(int i=0;i<array.length();i++)if(array.get(i) instanceof LevelChunk chunk)chunks.add(chunk);return chunks;
    }
    private interface Capture { void collect(Set<Object> roots)throws ReflectiveOperationException; }
    private static void captureStage(Run run,Set<Object> roots,String name,Capture stage){
        try{stage.collect(roots);}
        catch(ReflectiveOperationException|RuntimeException|LinkageError unavailable){
            run.capture=true;run.captureGap+=(run.captureGap.isEmpty()?"":",")+name+":"+unavailable.getClass().getSimpleName();run.retries++;
        }
    }
    private static void capture(Minecraft mc,Run run){
        run.capture=false;run.captureGap="";Set<Object> roots=Collections.newSetFromMap(new IdentityHashMap<>());
        if(mc.level!=null){
            captureStage(run,roots,"ENTITIES",values->mc.level.entitiesForRendering().forEach(values::add));
            captureStage(run,roots,"PARTS",values->values.addAll(((ClientModGroupHooks.LevelAccess)mc.level).pro$parts().values()));
            captureStage(run,roots,"SECTIONS",values->{
                var manager=(ClientModGroupHooks.StorageAccess)((ClientPresence.ClientStorage)mc.level).pro$clientEntityStorage();
                for(var section:((Access.Sections)manager.pro$sections()).pro$all().values())values.addAll(((Access.Group)((Access.Section)section).pro$storage()).pro$all());
            });
            captureStage(run,roots,"CHUNKS",values->{for(LevelChunk chunk:chunks(mc.level))values.addAll(chunk.getBlockEntities().values());});
        }
        captureStage(run,roots,"BOSSES",values->values.addAll(((dev.ronova.pro.mixin.ClientPresentation.BossAccess)mc.gui.getBossOverlay()).pro$events().values()));
        if(mc.screen!=null)roots.add(mc.screen);
        captureStage(run,roots,"PARTICLES",values->{var particles=(ClientModGroupHooks.ParticlesAccess)mc.particleEngine;
            values.addAll(particles.pro$pending());values.addAll(particles.pro$emitters());for(var queue:particles.pro$particles().values())values.addAll(queue);});
        captureStage(run,roots,"SOUNDS",values->{var sounds=(ClientModGroupHooks.SoundAccess)((ClientModGroupHooks.SoundManagerAccess)mc.getSoundManager()).pro$engine();
            values.addAll(sounds.pro$channels().keySet());values.addAll(sounds.pro$queued().keySet());values.addAll(sounds.pro$ticking());});
        if(mc.player!=null)roots.add(mc.player);
        for(Object value:roots)enqueue(run,value,mc.level,false);
    }
    static void tick(Minecraft mc){
        tick++;boot();
        runs.entrySet().removeIf(entry->entry.getValue().reply&&!current(entry.getValue()));
        if(world!=mc.level){world=mc.level;for(Run run:runs.values()){
            run.capture=true;run.completed.entrySet().removeIf(entry->entry.getKey() instanceof Entity||entry.getKey() instanceof BlockEntity);}}
        for(int left=Math.min(observations.size(),128);left>0;left--){Observation publication=observations.poll();if(publication==null)break;
            try{if(publication.world()==mc.level&&publication.connection()==transport(mc))
                for(Run run:publication.runs())enqueue(run,publication.value(),publication.world(),true);}
            finally{for(Run run:publication.runs())run.incoming.decrementAndGet();}
        }
        for(Run run:runs.values())if(live(run)){refresh(run);if(live(run)&&!run.modules.isEmpty()&&run.capture)capture(mc,run);}
        long deadline=System.nanoTime()+2_000_000L;int budget=128;
        long active=runs.values().stream().filter(run->live(run)&&!run.modules.isEmpty()).count();
        int share=(int)Math.max(1,128/Math.max(1,active));
        for(Run run:runs.values())if(live(run)&&!run.modules.isEmpty()){
            // A work item present at the start of this pass is attempted at most once in this tick.
            for(int left=Math.min(run.queue.size(),share);left>0&&budget>0&&System.nanoTime()<deadline;left--){
                Work work=run.queue.removeFirst();if(run.pending.get(work.value)!=work)continue;
                if(work.ready>tick){run.queue.addLast(work);continue;}budget--;
                try{
                    if(!live(run))break;
                    if((work.value instanceof Entity||work.value instanceof BlockEntity)&&work.level!=mc.level){
                        run.leftWorld++;run.pending.remove(work.value);run.completed.remove(work.value);run.capture=true;continue;}
                    if(work.inventory&&work.value!=mc.player){run.pending.remove(work.value);run.completed.remove(work.value);run.capture=true;continue;}
                    boolean complete;
                    if(work.inventory){if(!run.message.protect())run.cleared+=ModInventory.clear(mc.player,item->owner(item,run.modules)!=null);complete=true;}
                    else if(run.message.protect()){
                        complete=!(work.value instanceof Entity entity)||ClientPresence.bindGroupBody(entity);
                        if(complete)run.protectedCount++;else work.gap="CLIENT_GROUP_HEALTH_STORAGE_CAPTURE_PENDING";
                    }
                    else{complete=retire(mc,work);if(complete)run.cleared++;}
                    if(complete){finish(run,work);continue;}
                }catch(RuntimeException|LinkageError failure){work.gap=failure.getClass().getSimpleName()+":"+String.valueOf(failure.getMessage());run.retries++;}
                work.ready=tick+1;run.queue.addLast(work);
            }
        }
        for(Run run:runs.values())notify(run);
        runs.entrySet().removeIf(entry->entry.getValue().superseded&&(!entry.getValue().reply||Objects.equals(entry.getValue().sent,entry.getValue().state())));
        observing=runs.values().stream().filter(value->!value.superseded).toArray(Run[]::new);
    }
    private static void finish(Run run,Work work){run.pending.remove(work.value);run.completed.put(work.value,Boolean.TRUE);}
    private static net.minecraft.client.sounds.ChannelAccess.ChannelHandle soundHandle(ClientModGroupHooks.SoundAccess access,SoundInstance sound){
        for(var entry:access.pro$channels().entrySet())if(entry.getKey()==sound)return entry.getValue();return null;
    }
    private static boolean retire(Minecraft mc,Work work){
        Object value=work.value;work.gap="";
        if(value instanceof Entity entity){if(entity==mc.player)throw new IllegalStateException("LOCAL_PLAYER_HANDOFF_REQUIRED");
            if(work.body==null)work.body=new ClientPresence.GroupRetirement(entity);ClientPresence.retireGroupBody(work.body);return true;}
        if(value instanceof BlockEntity block){var raw=(Access.Block)block;if(raw.pro$level()!=work.level||mc.level==null)return true;
            if(!work.blockCaptured){var pos=raw.pro$pos();if(!mc.level.hasChunkAt(pos))return true;LevelChunk chunk=mc.level.getChunkAt(pos);
                if(chunk.getBlockEntities().get(pos)!=block)return true;work.blockPos=pos;work.blockChunk=chunk;work.blockTicker=((Access.Chunk)chunk).pro$tickers().get(pos);work.blockCaptured=true;}
            var pos=work.blockPos;LevelChunk chunk=work.blockChunk;Object ticker=work.blockTicker;
            ((Access.Block)block).pro$removed(true);
            if(!((Access.Block)block).pro$removed())throw new IllegalStateException("CLIENT_BLOCK_REMOVAL_WRITE_PENDING");
            if(chunk.getBlockEntities().get(pos)==block)chunk.getBlockEntities().remove(pos);
            if(ticker!=null&&chunk.getBlockEntities().get(pos)!=null&&((Access.Chunk)chunk).pro$tickers().get(pos)==ticker){work.gap="CLIENT_BLOCK_TICKER_REBOUND_TO_OTHER_BODY";return false;}
            if(chunk.getBlockEntities().get(pos)==null&&((Access.Chunk)chunk).pro$tickers().get(pos)==ticker)((Access.Chunk)chunk).pro$tickers().remove(pos);
            if(ticker!=null){((Access.World)mc.level).pro$tickers().removeIf(row->row==ticker);((Access.World)mc.level).pro$pending().removeIf(row->row==ticker);}return true;
        }
        // The instrumented original presentation boundary owns lifecycle calls and their partial outcomes.
        if(value instanceof net.minecraft.client.gui.screens.Screen screen){if(mc.screen==screen){work.gap="CLIENT_PRESENTATION_RETIREMENT_PENDING";return false;}return true;}
        if(value instanceof Particle particle){var access=(ClientModGroupHooks.ParticlesAccess)mc.particleEngine;
            access.pro$pending().removeIf(row->row==particle);access.pro$emitters().removeIf(row->row==particle);for(var queue:access.pro$particles().values())queue.removeIf(row->row==particle);return true;}
        if(value instanceof SoundInstance sound){var access=(ClientModGroupHooks.SoundAccess)((ClientModGroupHooks.SoundManagerAccess)mc.getSoundManager()).pro$engine();
            if(!work.soundCaptured){work.soundEngine=access;work.handle=soundHandle(access,sound);work.soundCaptured=true;}
            if(access!=work.soundEngine){work.gap="CLIENT_ORIGINAL_SOUND_ENGINE_CHANGED";return false;}
            var current=soundHandle(access,sound);
            if(current!=null&&current!=work.handle){work.gap="CLIENT_ORIGINAL_SOUND_HANDLE_CHANGED";return false;}
            if(work.handle!=null&&!work.handle.isStopped()){work.gap="CLIENT_SOUND_THREAD_HANDOFF_PENDING";return false;}
            access.pro$queued().keySet().removeIf(row->row==sound);access.pro$ticking().removeIf(row->row==sound);return true;}
        var bosses=((dev.ronova.pro.mixin.ClientPresentation.BossAccess)mc.gui.getBossOverlay()).pro$events();
        bosses.values().removeIf(row->row==value);return true;
    }
}
