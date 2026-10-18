package dev.ronova.pro;

import dev.ronova.pro.mixin.Access;
import dev.ronova.pro.mixin.ClientModGroupHooks;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
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
    private static final ArrayDeque<Object> stock=new ArrayDeque<>();
    private static ClientLevel world;
    private static boolean bootLoaded,rescan;
    private static java.lang.reflect.Method origins;
    private static final class Run {
        final ProNetwork.ModGroup message;final Set<Module> modules;
        int cleared,protectedCount,failed;String code;boolean pending=true;
        final Map<net.minecraft.client.sounds.ChannelAccess.ChannelHandle,SoundInstance> audio=new IdentityHashMap<>();
        Run(ProNetwork.ModGroup message,Module[] modules,String code){this.message=message;this.modules=Set.of(modules);this.code=code;
            if(!code.contains("FAILED=0")&&!code.contains("GROUP_FAILURES=0"))failed++;}
        String state(){return "CODE="+code+";STOCK="+(pending?"PENDING":"ENUMERATED")+";CLEARED="+cleared+";PROTECTED="+protectedCount+";FAILED="+failed;}
    }
    private ClientModGroups(){}
    private static Class<?> agent()throws ReflectiveOperationException{return Class.forName("dev.ronova.pro.agent.RecoveryAgent",false,ClassLoader.getSystemClassLoader());}
    private static Module owner(Object value,Set<Module> modules){
        if(value==null||modules.isEmpty())return null;Module direct=(value instanceof Class<?> type?type:value.getClass()).getModule();
        if(modules.contains(direct))return direct;
        try{if(origins==null)origins=Class.forName("dev.ronova.pro.bootstrap.ControlBridge",false,null).getMethod("creationSources",Object.class);
            for(Module source:(Module[])origins.invoke(null,value))if(modules.contains(source))return source;
        }catch(ReflectiveOperationException unavailable){/* An unobserved origin grants no authority over a shared carrier. */}
        return null;
    }
    public static boolean stopped(Object value){return owner(value,stopped)!=null;}
    public static boolean protectedObject(Object value){return !stopped(value)&&owner(value,protectedModules)!=null;}
    public static void observed(Object value){if(value!=null&&Minecraft.getInstance().isSameThread()&&(stopped(value)||protectedObject(value)))stock.addLast(value);}
    static void apply(ProNetwork.ModGroup message,boolean reply){
        Minecraft mc=Minecraft.getInstance();if(!mc.isSameThread())throw new IllegalStateException("CLIENT_THREAD_REQUIRED");
        try{
            String[] ids=message.ids().split(",");Class<?> agent=agent();
            String result=message.protect()?String.valueOf(agent.getMethod("protectModIds",String[].class).invoke(null,(Object)ids)):
                String.valueOf(agent.getMethod("stopModIds",String[].class,String.class).invoke(null,(Object)ids,message.returns()));
            Module[] modules=(Module[])agent.getMethod("modGroupModules",String[].class).invoke(null,(Object)ids);
            if(modules.length==0)throw new IllegalStateException("CLIENT_MODULES_NOT_BOUND");
            if(message.protect())protectedModules.addAll(Arrays.asList(modules));
            else{stopped.addAll(Arrays.asList(modules));protectedModules.removeAll(Arrays.asList(modules));}
            Run run=new Run(reply?message:new ProNetwork.ModGroup(message.session(),message.ids(),message.returns(),message.protect()),modules,result);
            runs.put((reply?"REMOTE:":"LOCAL:")+message.ids()+":"+message.protect(),run);rescan=true;
            if(reply)ProNetwork.modGroupResult(message,run.state());
        }catch(ReflectiveOperationException|RuntimeException|LinkageError failure){
            if(reply)ProNetwork.modGroupResult(message,"FAILED:"+failure.getClass().getSimpleName()+":"+String.valueOf(failure.getMessage()));
            System.err.println("RONOVA_CLIENT_GROUP_FAILED:"+failure);
        }
    }
    static void disconnected(){runs.entrySet().removeIf(entry->entry.getKey().startsWith("REMOTE:"));stock.clear();world=null;rescan=true;}
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
        for(var field:cache.getClass().getDeclaredFields())if(java.lang.reflect.Modifier.isVolatile(field.getModifiers())&&field.getType().getEnclosingClass()==cache.getClass()){storage=field;break;}
        if(storage==null)throw new IllegalStateException("CLIENT_CHUNK_STORAGE_UNAVAILABLE");
        agent().getMethod("openBackingAccess",Class.class,Class[].class).invoke(null,ClientModGroups.class,(Object)new Class<?>[]{cache.getClass(),storage.getType()});
        storage.setAccessible(true);Object owner=storage.get(cache);AtomicReferenceArray<?> array=null;
        for(var field:owner.getClass().getDeclaredFields())if(field.getType()==AtomicReferenceArray.class){field.setAccessible(true);array=(AtomicReferenceArray<?>)field.get(owner);break;}
        if(array==null)throw new IllegalStateException("CLIENT_CHUNK_ARRAY_UNAVAILABLE");
        List<LevelChunk> chunks=new ArrayList<>();for(int i=0;i<array.length();i++)if(array.get(i) instanceof LevelChunk chunk)chunks.add(chunk);return chunks;
    }
    private static void capture(Minecraft mc){
        stock.clear();Set<Object> roots=Collections.newSetFromMap(new IdentityHashMap<>());
        if(mc.level!=null){mc.level.entitiesForRendering().forEach(roots::add);
            roots.addAll(((ClientModGroupHooks.LevelAccess)mc.level).pro$parts().values());
            var manager=(ClientModGroupHooks.StorageAccess)((ClientPresence.ClientStorage)mc.level).pro$clientEntityStorage();
            for(var section:((Access.Sections)manager.pro$sections()).pro$all().values())roots.addAll(((Access.Group)((Access.Section)section).pro$storage()).pro$all());
            try{for(LevelChunk chunk:chunks(mc.level))roots.addAll(chunk.getBlockEntities().values());}
            catch(ReflectiveOperationException|RuntimeException failure){for(Run run:runs.values())run.failed++;System.err.println("RONOVA_CLIENT_CHUNKS:"+failure);}
        }
        roots.addAll(((dev.ronova.pro.mixin.ClientPresentation.BossAccess)mc.gui.getBossOverlay()).pro$events().values());
        if(mc.screen!=null)roots.add(mc.screen);
        var particles=(ClientModGroupHooks.ParticlesAccess)mc.particleEngine;
        roots.addAll(particles.pro$pending());roots.addAll(particles.pro$emitters());for(var queue:particles.pro$particles().values())roots.addAll(queue);
        var sounds=(ClientModGroupHooks.SoundAccess)((ClientModGroupHooks.SoundManagerAccess)mc.getSoundManager()).pro$engine();
        roots.addAll(sounds.pro$channels().keySet());roots.addAll(sounds.pro$queued().keySet());roots.addAll(sounds.pro$ticking());
        if(mc.player!=null)roots.add(mc.player);
        stock.addAll(roots);for(Run run:runs.values())run.pending=true;
    }
    static void tick(Minecraft mc){
        boot();if(runs.isEmpty())return;if(world!=mc.level){world=mc.level;rescan=true;}
        if(rescan){rescan=false;capture(mc);}
        long deadline=System.nanoTime()+2_000_000L;int budget=128;
        while(!stock.isEmpty()&&budget-->0&&System.nanoTime()<deadline){Object value=stock.removeFirst();
            for(Run run:runs.values())if(owner(value,run.modules)!=null||value==mc.player){
                    try{if(value==mc.player){if(!run.message.protect())run.cleared+=ModInventory.clear(mc.player,item->owner(item,run.modules)!=null);}
                    else if(run.message.protect()){if(value instanceof Entity entity)ClientPresence.bindLocal(entity);run.protectedCount++;}
                    else if(retire(mc,value,run))run.cleared++;
                }catch(RuntimeException|LinkageError failure){run.failed++;System.err.println("RONOVA_CLIENT_GROUP_RETIRE:"+failure);}
            }
        }
        for(var entry:runs.entrySet()){Run run=entry.getValue();
            for(var audio=run.audio.entrySet().iterator();audio.hasNext();)if(audio.next().getKey().isStopped()){audio.remove();run.cleared++;}
            if(stock.isEmpty()&&run.audio.isEmpty()&&run.pending){run.pending=false;
                if(entry.getKey().startsWith("REMOTE:")&&mc.getConnection()!=null)ProNetwork.modGroupResult(run.message,run.state());}}
    }
    private static boolean retire(Minecraft mc,Object value,Run run){
        if(value instanceof Entity entity){if(entity==mc.player)throw new IllegalStateException("LOCAL_PLAYER_HANDOFF_REQUIRED");
            ClientPresence.retireGroupBody(entity);return true;}
        if(value instanceof BlockEntity block){var raw=(Access.Block)block;if(raw.pro$level()!=mc.level||mc.level==null)return false;
            var pos=raw.pro$pos();if(!mc.level.hasChunkAt(pos))return false;LevelChunk chunk=mc.level.getChunkAt(pos);
            if(chunk.getBlockEntities().get(pos)!=block)return false;
            Object ticker=((Access.Chunk)chunk).pro$tickers().get(pos);
            chunk.getBlockEntities().remove(pos,block);((Access.Block)block).pro$removed(true);
            if(chunk.getBlockEntities().get(pos)==null)((Access.Chunk)chunk).pro$tickers().remove(pos,ticker);
            ((Access.World)mc.level).pro$tickers().removeIf(row->row==ticker);((Access.World)mc.level).pro$pending().removeIf(row->row==ticker);return true;
        }
        if(value instanceof net.minecraft.client.gui.screens.Screen screen){if(mc.screen==screen)mc.setScreen(null);return mc.screen!=screen;}
        if(value instanceof Particle particle){var access=(ClientModGroupHooks.ParticlesAccess)mc.particleEngine;
            access.pro$pending().removeIf(row->row==particle);access.pro$emitters().removeIf(row->row==particle);for(var queue:access.pro$particles().values())queue.removeIf(row->row==particle);return true;}
        if(value instanceof SoundInstance sound){var access=(ClientModGroupHooks.SoundAccess)((ClientModGroupHooks.SoundManagerAccess)mc.getSoundManager()).pro$engine();
            var handle=access.pro$channels().get(sound);
            if(handle!=null&&run.audio.containsKey(handle))return false;
            mc.getSoundManager().stop(sound);access.pro$queued().remove(sound);access.pro$ticking().removeIf(row->row==sound);
            if(handle!=null&&!handle.isStopped()){run.audio.put(handle,sound);return false;}return true;}
        var bosses=((dev.ronova.pro.mixin.ClientPresentation.BossAccess)mc.gui.getBossOverlay()).pro$events();
        return bosses.values().removeIf(row->row==value);
    }
}
