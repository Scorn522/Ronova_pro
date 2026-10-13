package dev.ronova.pro.bootstrap;

import java.lang.ref.*;
import java.lang.reflect.*;
import java.util.*;

/** Actual client contribution objects, their invocation scopes and texture registry shares. */
public final class ClientBridge {
    private static final StackWalker WALKER=StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE);
    private static final ReferenceQueue<Object> DEAD=new ReferenceQueue<>();
    private static final Map<Key,Module[]> SOURCES=new HashMap<>();
    private static final Map<Key,TextureRegistry> TEXTURES=new HashMap<>();
    private static final Map<Key,ImageRecord> IMAGES=new HashMap<>();
    private static final Map<Key,Ref<Object>> PIXELS=new HashMap<>();
    private static final Map<Key,TextureRetirement> RETIREMENTS=new HashMap<>();
    private static final Map<Key,Module[]> FAILURES=new HashMap<>();
    private static final Map<Key,SoundRegistry> SOUNDS=new HashMap<>();
    private static final Map<Key,SoundStop> SOUND_STOPS=new HashMap<>();
    private static final Map<Key,Ref<Collection<?>>> RELOAD_LISTENERS=new HashMap<>();
    private static final Map<Key,ReloadWork> RELOAD_WORK=new HashMap<>();
    private static final Map<Key,UiFields> UI_OWNERS=new HashMap<>();
    private static final ThreadLocal<UiRetirement> UI_RETIRING=new ThreadLocal<>();
    private static final String OVERLAY_MANAGER="net.minecraftforge.client.gui.overlay.GuiOverlayManager",OVERLAY_EVENT="net.minecraftforge.client.event.RegisterGuiOverlaysEvent";
    private static final String OVERLAY_NAMED="net.minecraftforge.client.gui.overlay.NamedGuiOverlay",OVERLAY_CALLBACK="net.minecraftforge.client.gui.overlay.IGuiOverlay";
    private static final String OVERLAY_REGISTER="(Lnet/minecraftforge/client/event/RegisterGuiOverlaysEvent$Ordering;Lnet/minecraft/resources/ResourceLocation;Ljava/lang/String;Lnet/minecraftforge/client/gui/overlay/IGuiOverlay;)V";
    private static final String OVERLAY_RENDER="(Lnet/minecraft/client/gui/GuiGraphics;FLnet/minecraftforge/client/gui/overlay/NamedGuiOverlay;)V";
    private static final Map<Key,OverlayEvent> OVERLAY_EVENTS=new HashMap<>();
    private static final Map<Key,OverlayRegistry> OVERLAY_REGISTRIES=new HashMap<>();
    private static final Map<Key,OverlaySlot> OVERLAY_NAMES=new HashMap<>();
    private static final ThreadLocal<OverlayInit> OVERLAY_INITIALIZING=new ThreadLocal<>();
    private static final ClassValue<OverlayFields> OVERLAY_FIELDS=new ClassValue<>(){
        protected OverlayFields computeValue(Class<?> type){try{return new OverlayFields(type);}catch(ReflectiveOperationException unavailable){throw new IllegalStateException("CLIENT_OVERLAY_FIELDS_UNAVAILABLE",unavailable);}}
    };
    private static final System.Logger UI_LOG=System.getLogger("ronova_pro.client.lifecycle");
    private static final ClassValue<UiFields> UI_FIELDS=new ClassValue<>(){
        protected UiFields computeValue(Class<?> type){try{return new UiFields(type);}catch(ReflectiveOperationException unavailable){throw new IllegalStateException("CLIENT_UI_FIELDS_UNAVAILABLE",unavailable);}}
    };
    private static final GpuImages GPU=new GpuImages();
    private static final List<Role> ROLES=new ArrayList<>();
    private static final Set<Module> REQUESTED=Collections.newSetFromMap(new IdentityHashMap<>());
    private static final ThreadLocal<Scope> CURRENT=new ThreadLocal<>();
    private static final ThreadLocal<Object> RELEASING=new ThreadLocal<>();
    private static final Set<Scope> ACTIVE=Collections.newSetFromMap(new IdentityHashMap<>());
    private static long retired,shared,failed;
    private record Role(String name,Ref<ClassLoader> loader,Ref<Module> module){}
    private record TextureRegistry(Class<?> type,Ref<Map<?,?>> map,Map<Object,Texture> entries){}
    private record Texture(Ref<Object> object,Module[] modules){}
    private record SoundRegistry(Ref<Map<?,?>> instances){}
    private record ReloadWork(java.util.concurrent.CompletableFuture<?> future,Module[] modules){}
    private static final class OverlayFields {
        final Class<?> type,named,event;final Field list,map,id,callback,eventMap,eventOrder;final Method listCopy,mapCopy;
        final java.util.concurrent.locks.ReentrantLock gate=new java.util.concurrent.locks.ReentrantLock();long generation;int initializing;String pending="";
        OverlayFields(Class<?> type)throws ReflectiveOperationException{
            this.type=type;list=type.getDeclaredField("OVERLAYS");map=type.getDeclaredField("OVERLAYS_BY_NAME");list.setAccessible(true);map.setAccessible(true);
            named=Class.forName(OVERLAY_NAMED,false,type.getClassLoader());event=Class.forName(OVERLAY_EVENT,false,type.getClassLoader());
            id=named.getDeclaredField("id");callback=named.getDeclaredField("overlay");eventMap=event.getDeclaredField("overlays");eventOrder=event.getDeclaredField("orderedOverlays");
            for(Field member:new Field[]{id,callback,eventMap,eventOrder})member.setAccessible(true);
            if(!Modifier.isStatic(list.getModifiers())||!Modifier.isStatic(map.getModifiers())||!list.getType().getName().equals("com.google.common.collect.ImmutableList")
                    ||!map.getType().getName().equals("com.google.common.collect.ImmutableMap")||!named.isRecord()||!Modifier.isFinal(named.getModifiers())
                    ||!id.getType().getName().equals("net.minecraft.resources.ResourceLocation")||!callback.getType().getName().equals(OVERLAY_CALLBACK)
                    ||eventMap.getType()!=Map.class||eventOrder.getType()!=List.class||named.getModule()!=type.getModule()||event.getModule()!=type.getModule())throw new IllegalStateException("CLIENT_OVERLAY_FIELD_TYPES_CHANGED");
            listCopy=list.getType().getMethod("copyOf",Collection.class);mapCopy=map.getType().getMethod("copyOf",Map.class);
            overlayProtect(this,gate,list,map,id,callback,eventMap,eventOrder,listCopy,mapCopy);
        }
    }
    private static final class OverlayInit {
        final OverlayFields fields;final long generation;final Thread thread=Thread.currentThread();final OverlayInit previous=OVERLAY_INITIALIZING.get();
        OverlayEvent event;boolean ended,published;
        OverlayInit(OverlayFields fields,long generation){this.fields=fields;this.generation=generation;}
    }
    private static final class OverlayEvent {
        final OverlayInit init;final Ref<Map<?,?>> map;final Ref<List<?>> order;final Map<Object,OverlayRegistration> registrations=new IdentityHashMap<>();
        OverlayEvent(OverlayInit init,Map<?,?> map,List<?> order){this.init=init;this.map=new Ref<>(map);this.order=new Ref<>(order);overlayProtect(this,this.map,this.order,registrations);}
    }
    private record OverlayRegistration(Ref<Object> callback,Module[] modules){}
    private static final class OverlaySlot {
        final OverlayFields fields;final long generation;final Object key;final Ref<Object> named,callback;final Module[] modules;final boolean observed;boolean retired;
        OverlaySlot(OverlayFields fields,long generation,Object key,Object named,Object callback,Module[] modules,boolean observed){
            this.fields=fields;this.generation=generation;this.key=key;this.named=new Ref<>(named);this.callback=new Ref<>(callback);this.modules=modules;this.observed=observed;overlayProtect(this,this.named,this.callback,modules);
        }
    }
    private static final class OverlayRegistry {
        final OverlayFields fields;final long generation;final Object list,map;final List<OverlaySlot> slots;OverlayRetirement retirement;
        OverlayRegistry(OverlayFields fields,long generation,Object list,Object map,List<OverlaySlot> slots){this.fields=fields;this.generation=generation;this.list=list;this.map=map;this.slots=slots;overlayProtect(this,slots);}
    }
    private static final class OverlayRetirement {
        final OverlayRegistry before;final Object list,map;final List<OverlaySlot> kept,removed;String pending="CLIENT_OVERLAY_PAIR_PUBLICATION_PENDING";
        OverlayRetirement(OverlayRegistry before,Object list,Object map,List<OverlaySlot> kept,List<OverlaySlot> removed){this.before=before;this.list=list;this.map=map;this.kept=kept;this.removed=removed;overlayProtect(this,kept,removed);}
    }
    private static final class UiFields {
        final Class<?> type;final Field instance,thread,screen,overlay;final Method setScreen,setOverlay;
        UiFields(Class<?> type)throws ReflectiveOperationException{
            this.type=type;instance=uiField(type,"instance","f_90981_");thread=uiField(type,"gameThread","f_91018_");
            screen=uiField(type,"screen","f_91080_");overlay=uiField(type,"overlay","f_91081_");
            if(instance.getType()!=type||!Modifier.isStatic(instance.getModifiers())||thread.getType()!=Thread.class
                    ||!screen.getType().getName().equals("net.minecraft.client.gui.screens.Screen")
                    ||!overlay.getType().getName().equals("net.minecraft.client.gui.screens.Overlay"))throw new IllegalStateException("CLIENT_UI_FIELD_TYPES_CHANGED");
            setScreen=uiSetter(type,"setScreen","m_91152_",screen.getType());setOverlay=uiSetter(type,"setOverlay","m_91150_",overlay.getType());
        }
        Object current(Object owner,boolean screens)throws IllegalAccessException{return (screens?screen:overlay).get(owner);}
    }
    private static final class UiRetirement {
        final Object owner,target;final boolean screen;UiCall call;
        UiRetirement(Object owner,Object target,boolean screen){this.owner=owner;this.target=target;this.screen=screen;}
    }
    private static final class UiCall {
        final Object owner,input,before;final UiFields fields;final boolean screen,retirement;final String method;final Thread thread;
        boolean ended;
        UiCall(Object owner,Object input,Object before,UiFields fields,boolean screen,boolean retirement,String method){this.owner=owner;this.input=input;this.before=before;this.fields=fields;this.screen=screen;this.retirement=retirement;this.method=method;thread=Thread.currentThread();}
    }
    private static final class SoundStop {
        final Object handle,channel,access;final Class<?> handleType,channelType,accessType;final Module[] modules;
        final java.util.concurrent.atomic.AtomicBoolean stopped=new java.util.concurrent.atomic.AtomicBoolean();boolean requested;volatile String pending="";
        SoundStop(Object handle,Object channel,Object access,Class<?> handleType,Class<?> channelType,Class<?> accessType,Module[] modules){this.handle=handle;this.channel=channel;this.access=access;this.handleType=handleType;this.channelType=channelType;this.accessType=accessType;this.modules=modules;}
    }
    private static final class ImageRecord {
        final long pointer,size;final java.util.concurrent.locks.ReentrantLock gate=new java.util.concurrent.locks.ReentrantLock();
        Module[] exposures=new Module[0];boolean unknownExposure,closed;
        ImageRecord(long pointer,long size){this.pointer=pointer;this.size=size;}
    }
    private static final class TextureRetirement {
        final Object object;final Module[] modules;final Ref<Object> manager;final TextureRegistry registry;String pending="";boolean pixelsReleased,detached;
        TextureRetirement(Object object,Module[] modules,Object manager,TextureRegistry registry){this.object=object;this.modules=modules;this.manager=new Ref<>(manager);this.registry=registry;}
    }
    private static final class Scope {
        final Scope parent;final Module[] sources;final Class<?> type;final String method;final Thread thread;
        final OverlaySlot overlay;boolean ended;
        Scope(Scope parent,Module[] sources,StackWalker.StackFrame frame){this(parent,sources,frame,null);}
        Scope(Scope parent,Module[] sources,StackWalker.StackFrame frame,OverlaySlot overlay){this.parent=parent;this.sources=sources;type=frame.getDeclaringClass();method=frame.getMethodName();thread=Thread.currentThread();this.overlay=overlay;}
    }
    private static final class Ref<T> extends WeakReference<T>{
        Ref(T object){super(object);}
        public void clear(){if(writer())super.clear();}
        public boolean enqueue(){return writer()&&super.enqueue();}
    }
    private static final class Key extends WeakReference<Object>{
        final int hash;
        Key(Object object,boolean stored){super(object,stored?DEAD:null);hash=System.identityHashCode(object);}
        public int hashCode(){return hash;}
        public boolean equals(Object other){return this==other||other instanceof Key key&&get()!=null&&get()==key.get();}
        public void clear(){if(writer())super.clear();}
        public boolean enqueue(){return writer()&&super.enqueue();}
    }
    private ClientBridge(){}
    private static Class<?> caller(){return WALKER.walk(frames->frames.map(StackWalker.StackFrame::getDeclaringClass).filter(type->type.getNestHost()!=ClientBridge.class).findFirst().orElse(null));}
    private static boolean writer(){Class<?> caller=caller();return caller!=null&&(ControlBridge.owns(caller)||caller.getNestHost()==TaskBridge.class);}
    private static void agent(){if(!ControlBridge.owns(caller()))throw new SecurityException("CLIENT_AGENT_REQUIRED");}
    public static void expect(String name,ClassLoader loader,Module module){
        agent();synchronized(SOURCES){
            for(Role role:ROLES)if(role.name().equals(name)&&role.loader().get()==loader){if(role.module().get()!=module)throw new IllegalStateException("CLIENT_ROLE_MODULE_CHANGED");return;}
            ROLES.add(new Role(name,new Ref<>(loader),new Ref<>(module)));
        }
    }
    private static boolean role(Class<?> type){synchronized(SOURCES){for(Role role:ROLES)if(role.name().equals(type.getName())&&role.loader().get()==type.getClassLoader()&&role.module().get()==DefinitionBridge.module(type))return true;return false;}}
    private static StackWalker.StackFrame boundary(){
        var frame=WALKER.walk(frames->frames.filter(value->value.getDeclaringClass().getNestHost()!=ClientBridge.class).findFirst().orElse(null));
        if(frame==null||!role(frame.getDeclaringClass()))throw new SecurityException("ACTUAL_CLIENT_BOUNDARY_REQUIRED");return frame;
    }
    private static Module[] union(Module[]... groups){
        Set<Module> result=null;for(Module[] group:groups)if(group!=null)for(Module module:group)if(module!=null){
            if(result==null)result=Collections.newSetFromMap(new IdentityHashMap<>());result.add(module);
        }
        return result==null?new Module[0]:result.toArray(Module[]::new);
    }
    private static Module[] origins(Object object){return object==null?new Module[0]:Arrays.stream(ControlBridge.objectSources(object)).filter(ControlBridge::producer).toArray(Module[]::new);}
    private static boolean stopped(Module[] sources){for(Module module:sources)if(ControlBridge.modStopped(module))return true;return false;}
    private static boolean exclusive(Module[] sources){if(sources.length==0)return false;for(Module module:sources)if(!ControlBridge.modStopped(module))return false;return true;}
    private static Module[] sources(Object object){Module[] real=origins(object);synchronized(SOURCES){return union(real,SOURCES.get(new Key(object,false)));}}
    private static void record(Object object,Module[] modules){if(object==null)return;synchronized(SOURCES){reap();Module[] sources=union(SOURCES.get(new Key(object,false)),modules);if(sources.length!=0){SOURCES.put(new Key(object,true),sources);CodeSourceBridge.contributionControl(sources);}}}
    public static boolean allowed(Object contribution){boundary();return !stopped(sources(contribution))&&!ControlBridge.stoppedEffects();}
    private static Field uiField(Class<?> type,String named,String installed)throws ReflectiveOperationException{
        Field result;try{result=type.getDeclaredField(named);}catch(NoSuchFieldException production){result=type.getDeclaredField(installed);}result.setAccessible(true);return result;
    }
    private static Method uiSetter(Class<?> type,String named,String installed,Class<?> parameter)throws ReflectiveOperationException{
        Method result;try{result=type.getDeclaredMethod(named,parameter);}catch(NoSuchMethodException production){result=type.getDeclaredMethod(installed,parameter);}
        if(Modifier.isStatic(result.getModifiers())||result.getReturnType()!=void.class)throw new IllegalStateException("CLIENT_UI_SETTER_CHANGED");return result;
    }
    private static boolean screenSetter(StackWalker.StackFrame frame){return Set.of("setScreen","m_91152_").contains(frame.getMethodName());}
    private static UiFields uiOwner(Object owner,StackWalker.StackFrame frame)throws ReflectiveOperationException{
        Class<?> type=frame.getDeclaringClass();
        if(!type.getName().equals("net.minecraft.client.Minecraft")||owner==null||owner.getClass()!=type)throw new SecurityException("ACTUAL_MINECRAFT_UI_OWNER_REQUIRED");
        UiFields fields=UI_FIELDS.get(type);if(fields.instance.get(null)!=owner)throw new SecurityException("ACTUAL_MINECRAFT_SINGLETON_REQUIRED");
        synchronized(SOURCES){UI_OWNERS.put(new Key(owner,true),fields);}return fields;
    }
    private static void uiFailure(Object value,Module[] modules,Throwable failure){
        synchronized(SOURCES){FAILURES.put(new Key(value,true),union(FAILURES.get(new Key(value,false)),modules));}
        UI_LOG.log(System.Logger.Level.WARNING,"CLIENT_PRESENTATION_LIFECYCLE_PENDING",failure);
    }
    public static Object presentationEntering(Object owner,Object input){
        var frame=boundary();if(!screenSetter(frame)&&!Set.of("setOverlay","m_91150_").contains(frame.getMethodName()))throw new SecurityException("ACTUAL_UI_SETTER_REQUIRED");
        Module[] modules=union(origins(input),ControlBridge.invokingSources(),currentSources());record(input,modules);
        if(stopped(sources(input))||ControlBridge.stoppedEffects())return Boolean.FALSE;
        try{
            UiFields fields=uiOwner(owner,frame);boolean screen=screenSetter(frame);Object before=fields.current(owner,screen);
            UiRetirement requested=UI_RETIRING.get();boolean retirement=requested!=null&&requested.owner==owner&&requested.screen==screen&&requested.target==before&&input==null&&requested.call==null;
            UiCall call=new UiCall(owner,input,before,fields,screen,retirement,frame.getMethodName());
            if(retirement)requested.call=call;CodeSourceBridge.contributionControl(call);return call;
        }catch(ReflectiveOperationException|IllegalStateException unavailable){uiFailure(owner,modules,unavailable);return null;}
    }
    private static UiCall uiCall(Object token,StackWalker.StackFrame frame){
        if(!(token instanceof UiCall call)||call.fields.type!=frame.getDeclaringClass()||!call.method.equals(frame.getMethodName())||call.thread!=Thread.currentThread())throw new SecurityException("ACTUAL_UI_INVOCATION_REQUIRED");return call;
    }
    public static boolean presentationWriting(Object owner,Object next,Object token){
        var frame=boundary();
        if(token==null)return !stopped(sources(next))&&!ControlBridge.stoppedEffects();
        UiCall call=uiCall(token,frame);if(call.owner!=owner||call.ended)throw new SecurityException("ACTUAL_UI_SLOT_REQUIRED");
        record(next,union(origins(next),next==call.input?ControlBridge.invokingSources():new Module[0],currentSources()));
        if(stopped(sources(next))||ControlBridge.stoppedEffects())return false;
        try{
            // Closing callbacks can publish a new legitimate UI. The old retirement cannot overwrite it.
            return !call.retirement||call.fields.current(owner,call.screen)==call.before;
        }catch(ReflectiveOperationException unavailable){uiFailure(owner,union(sources(call.before),sources(next)),unavailable);return false;}
    }
    public static void presentationExiting(Object token){
        var frame=boundary();if(token==null)return;UiCall call=uiCall(token,frame);if(call.ended)return;call.ended=true;
    }
    public static void presentationMaintenance(Object owner){
        var frame=boundary();if(!Set.of("runTick","m_91383_").contains(frame.getMethodName()))throw new SecurityException("ACTUAL_CLIENT_FRAME_REQUIRED");
        UiFields fields;
        try{
            fields=uiOwner(owner,frame);if(fields.thread.get(owner)!=Thread.currentThread())throw new IllegalStateException("CLIENT_UI_MAIN_THREAD_REQUIRED");
        }catch(ReflectiveOperationException|IllegalStateException unavailable){uiFailure(owner,new Module[0],unavailable);return;}
        UiRetirement active=UI_RETIRING.get();if(active!=null&&active.owner==owner)return;
        retireOverlays();retirePresentation(owner,fields,false);retirePresentation(owner,fields,true);
    }
    private static void overlayProtect(Object... objects){for(Object object:objects)CodeSourceBridge.contributionControl(object);}
    private static void overlayBoundary(StackWalker.StackFrame frame,String type,String method,String descriptor){
        if(!frame.getDeclaringClass().getName().equals(type)||!frame.getMethodName().equals(method)||!frame.getDescriptor().equals(descriptor))throw new SecurityException("ACTUAL_CLIENT_OVERLAY_BOUNDARY_REQUIRED");
    }
    public static Object overlayInitEntering(Class<?> manager){
        var frame=boundary();overlayBoundary(frame,OVERLAY_MANAGER,"init","()V");if(frame.getDeclaringClass()!=manager)throw new SecurityException("ACTUAL_OVERLAY_MANAGER_REQUIRED");
        OverlayFields fields=OVERLAY_FIELDS.get(manager);fields.gate.lock();
        try{synchronized(SOURCES){OverlayInit init=new OverlayInit(fields,++fields.generation);fields.initializing++;overlayProtect(init);OVERLAY_INITIALIZING.set(init);return init;}}
        finally{fields.gate.unlock();} // The original event post runs without this metadata lock.
    }
    public static void overlayInitExiting(Object token){
        var frame=boundary();overlayBoundary(frame,OVERLAY_MANAGER,"init","()V");
        if(!(token instanceof OverlayInit init)||init.thread!=Thread.currentThread()||init.fields.type!=frame.getDeclaringClass())throw new SecurityException("ACTUAL_OVERLAY_INIT_SCOPE_REQUIRED");
        if(init.ended)return;if(OVERLAY_INITIALIZING.get()!=init)throw new IllegalStateException("CLIENT_OVERLAY_INIT_ORDER_CHANGED");
        init.fields.gate.lock();try{synchronized(SOURCES){init.ended=true;init.fields.initializing--;if(!init.published)init.fields.pending="CLIENT_OVERLAY_INIT_PUBLICATION_UNOBSERVED";}}
        finally{if(init.previous==null)OVERLAY_INITIALIZING.remove();else OVERLAY_INITIALIZING.set(init.previous);init.fields.gate.unlock();}
    }
    public static void overlayEventConstructed(Object event,Map<?,?> registrations,List<?> order){
        var frame=boundary();overlayBoundary(frame,OVERLAY_EVENT,"<init>","(Ljava/util/Map;Ljava/util/List;)V");OverlayInit init=OVERLAY_INITIALIZING.get();
        if(init==null||init.ended||init.thread!=Thread.currentThread())return;
        if(init.event!=null)return;
        if(frame.getDeclaringClass()!=init.fields.event||event==null||event.getClass()!=init.fields.event||registrations.getClass()!=HashMap.class||order.getClass()!=ArrayList.class)
            throw new SecurityException("ACTUAL_OVERLAY_EVENT_PAIR_REQUIRED");
        try{
            if(init.fields.eventMap.get(event)!=registrations||init.fields.eventOrder.get(event)!=order)throw new IllegalStateException("CLIENT_OVERLAY_EVENT_PAIR_CHANGED");
            synchronized(SOURCES){OverlayEvent observed=new OverlayEvent(init,registrations,order);init.event=observed;OVERLAY_EVENTS.put(new Key(event,true),observed);}
        }catch(ReflectiveOperationException unavailable){throw new IllegalStateException("CLIENT_OVERLAY_EVENT_CAPTURE_FAILED",unavailable);}
    }
    public static void overlayRegistered(Object event,Map<?,?> registrations,List<?> order,Object key,Object callback){
        var frame=boundary();overlayBoundary(frame,OVERLAY_EVENT,"register",OVERLAY_REGISTER);OverlayEvent observed;
        synchronized(SOURCES){observed=OVERLAY_EVENTS.get(new Key(event,false));}if(observed==null||observed.init.ended)return;
        if(observed.init.thread!=Thread.currentThread()||event.getClass()!=observed.init.fields.event||observed.map.get()!=registrations||observed.order.get()!=order)
            throw new SecurityException("ACTUAL_OVERLAY_REGISTRATION_PAIR_REQUIRED");
        try{
            if(observed.init.fields.eventMap.get(event)!=registrations||observed.init.fields.eventOrder.get(event)!=order||key==null||key.getClass()!=observed.init.fields.id.getType()
                    ||callback==null||!observed.init.fields.callback.getType().isInstance(callback)||registrations.get(key)!=callback)throw new IllegalStateException("CLIENT_OVERLAY_REGISTRATION_CHANGED");
            int matches=0;for(Object entry:order)if(entry==key)matches++;if(matches!=1)throw new IllegalStateException("CLIENT_OVERLAY_ORDER_REGISTRATION_CHANGED");
            Module[] modules=union(ControlBridge.invokingSources(),currentSources(),origins(callback));
            OverlayRegistration slot=new OverlayRegistration(new Ref<>(callback),modules);overlayProtect(slot,slot.callback(),modules);
            synchronized(SOURCES){observed.registrations.put(key,slot);}
        }catch(ReflectiveOperationException unavailable){throw new IllegalStateException("CLIENT_OVERLAY_REGISTRATION_CAPTURE_FAILED",unavailable);}
    }
    public static void overlayPublished(Class<?> manager,Map<?,?> registrations,List<?> order){
        var frame=boundary();overlayBoundary(frame,OVERLAY_MANAGER,"init","()V");OverlayInit init=OVERLAY_INITIALIZING.get();
        if(init==null||init.ended||init.thread!=Thread.currentThread()||init.fields.type!=manager||frame.getDeclaringClass()!=manager||init.event==null
                ||init.event.map.get()!=registrations||init.event.order.get()!=order)throw new SecurityException("ACTUAL_OVERLAY_PUBLICATION_PAIR_REQUIRED");
        try{
            Object list=init.fields.list.get(null),map=init.fields.map.get(null);
            if(!init.fields.list.getType().isInstance(list)||!init.fields.map.getType().isInstance(map))throw new IllegalStateException("CLIENT_OVERLAY_PUBLICATION_TYPES_CHANGED");
            List<?> named=(List<?>)list;Map<?,?> byName=(Map<?,?>)map;if(named.size()!=order.size()||byName.size()!=named.size()||registrations.size()!=named.size())throw new IllegalStateException("CLIENT_OVERLAY_PUBLICATION_SIZE_CHANGED");
            List<OverlaySlot> slots=new ArrayList<>(named.size());
            for(int index=0;index<named.size();index++){
                Object value=named.get(index);if(value==null||value.getClass()!=init.fields.named||!role(value.getClass()))throw new IllegalStateException("CLIENT_NAMED_OVERLAY_TYPE_CHANGED");
                Object key=init.fields.id.get(value),callback=init.fields.callback.get(value);
                if(key!=order.get(index)||registrations.get(key)!=callback||byName.get(key)!=value)throw new IllegalStateException("CLIENT_OVERLAY_PUBLICATION_ASSOCIATION_CHANGED");
                OverlayRegistration observed; synchronized(SOURCES){observed=init.event.registrations.get(key);}
                if(observed!=null&&observed.callback().get()!=callback)throw new IllegalStateException("CLIENT_OVERLAY_CALLBACK_CHANGED_AFTER_REGISTRATION");
                Module[] modules=observed==null?origins(callback):union(observed.modules(),origins(callback));
                slots.add(new OverlaySlot(init.fields,init.generation,key,value,callback,modules,observed!=null));
            }
            init.fields.gate.lock();try{synchronized(SOURCES){
                if(init.fields.generation!=init.generation||init.fields.list.get(null)!=list||init.fields.map.get(null)!=map)throw new IllegalStateException("CLIENT_OVERLAY_PUBLICATION_GENERATION_CHANGED");
                OverlayRegistry registry=new OverlayRegistry(init.fields,init.generation,list,map,slots);OVERLAY_REGISTRIES.put(new Key(manager,true),registry);
                for(OverlaySlot slot:slots)OVERLAY_NAMES.put(new Key(slot.named.get(),true),slot);init.published=true;init.fields.pending="";
            }}finally{init.fields.gate.unlock();}
        }catch(ReflectiveOperationException|RuntimeException unavailable){synchronized(SOURCES){init.fields.pending="CLIENT_OVERLAY_PUBLICATION_CAPTURE_FAILED";}throw new IllegalStateException("CLIENT_OVERLAY_PUBLICATION_CAPTURE_FAILED",unavailable);}
    }
    private static Module[] overlaySources(OverlaySlot slot){return union(slot.modules,origins(slot.callback.get()));}
    private static boolean overlayMatches(OverlaySlot slot,Object named)throws ReflectiveOperationException{
        Object callback=slot.callback.get();return named!=null&&named==slot.named.get()&&named.getClass()==slot.fields.named&&slot.fields.id.get(named)==slot.key&&callback!=null&&slot.fields.callback.get(named)==callback;
    }
    public static Object overlayEntering(Object named){
        var frame=boundary();overlayBoundary(frame,"net.minecraftforge.client.gui.overlay.ForgeGui","lambda$render$0",OVERLAY_RENDER);OverlaySlot slot;
        synchronized(SOURCES){slot=OVERLAY_NAMES.get(new Key(named,false));}
        Module[] modules=new Module[0];
        if(slot!=null)try{
            if(!overlayMatches(slot,named)){synchronized(SOURCES){slot.fields.pending="CLIENT_OVERLAY_NAMED_ASSOCIATION_CHANGED";}slot=null;}
            else{modules=overlaySources(slot);if(slot.retired||slot.observed&&exclusive(modules))return Boolean.FALSE;}
        }catch(ReflectiveOperationException unavailable){throw new IllegalStateException("CLIENT_OVERLAY_CALLBACK_CAPTURE_FAILED",unavailable);}
        Scope scope=new Scope(CURRENT.get(),union(modules,currentSources()),frame,slot);synchronized(SOURCES){ACTIVE.add(scope);overlayProtect(scope,scope.sources);}CURRENT.set(scope);return scope;
    }
    public static void overlayExiting(Object token){exit(token);}
    private static boolean overlayActive(OverlaySlot slot){synchronized(SOURCES){for(Scope scope:ACTIVE)if(!scope.ended&&scope.overlay==slot)return true;return false;}}
    private static void retireOverlays(){
        List<OverlayRegistry> registries;synchronized(SOURCES){if(REQUESTED.isEmpty())return;registries=new ArrayList<>(OVERLAY_REGISTRIES.values());}
        for(OverlayRegistry registry:registries)try{
            OverlayFields fields=registry.fields;OverlayRetirement retirement=registry.retirement;
            if(retirement==null){
                synchronized(SOURCES){if(fields.initializing!=0||fields.generation!=registry.generation)continue;}
                if(fields.list.get(null)!=registry.list||fields.map.get(null)!=registry.map){fields.pending="CLIENT_OVERLAY_CURRENT_PAIR_CHANGED";continue;}
                List<Object> retained=new ArrayList<>();Map<Object,Object> names=new LinkedHashMap<>();List<OverlaySlot> kept=new ArrayList<>(),removed=new ArrayList<>();
                for(OverlaySlot slot:registry.slots){Object named=slot.named.get();if(!overlayMatches(slot,named)){fields.pending="CLIENT_OVERLAY_NAMED_ASSOCIATION_CHANGED";removed.clear();break;}
                    if(slot.observed&&exclusive(overlaySources(slot))&&!overlayActive(slot))removed.add(slot);else{retained.add(named);names.put(slot.key,named);kept.add(slot);}}
                if(removed.isEmpty())continue;
                Object nextList=fields.listCopy.invoke(null,retained),nextMap=fields.mapCopy.invoke(null,names);
                retirement=new OverlayRetirement(registry,nextList,nextMap,kept,removed);
            }
            if(!fields.gate.tryLock())continue;
            try{synchronized(SOURCES){
                if(fields.initializing!=0||fields.generation!=registry.generation||OVERLAY_REGISTRIES.get(new Key(fields.type,false))!=registry)continue;
                for(OverlaySlot slot:retirement.removed)if(!exclusive(overlaySources(slot))||overlayActive(slot))throw new IllegalStateException("CLIENT_OVERLAY_RETIREMENT_SOURCES_CHANGED");
                Object currentList=fields.list.get(null),currentMap=fields.map.get(null);
                if((currentList!=registry.list&&currentList!=retirement.list)||(currentMap!=registry.map&&currentMap!=retirement.map)){fields.pending="CLIENT_OVERLAY_CURRENT_PAIR_CHANGED";continue;}
                registry.retirement=retirement;
                if(currentList==registry.list){fields.list.set(null,retirement.list);if(fields.list.get(null)!=retirement.list)throw new IllegalStateException("CLIENT_OVERLAY_LIST_PUBLICATION_PENDING");}
                if(fields.generation!=registry.generation||OVERLAY_REGISTRIES.get(new Key(fields.type,false))!=registry||fields.list.get(null)!=retirement.list
                        ||fields.map.get(null)!=currentMap)throw new IllegalStateException("CLIENT_OVERLAY_REENTRANT_PUBLICATION_CHANGED");
                if(currentMap==registry.map)fields.map.set(null,retirement.map);
                if(fields.generation!=registry.generation||fields.list.get(null)!=retirement.list||fields.map.get(null)!=retirement.map)throw new IllegalStateException("CLIENT_OVERLAY_PAIR_READBACK_PENDING");
                for(OverlaySlot slot:retirement.removed){slot.retired=true;retired++;}
                OVERLAY_REGISTRIES.put(new Key(fields.type,true),new OverlayRegistry(fields,registry.generation,retirement.list,retirement.map,retirement.kept));fields.pending="";
            }}finally{fields.gate.unlock();}
        }catch(ReflectiveOperationException|RuntimeException unavailable){synchronized(SOURCES){registry.fields.pending="CLIENT_OVERLAY_RETIREMENT_PENDING:"+unavailable.getClass().getSimpleName();if(registry.retirement!=null)registry.retirement.pending=registry.fields.pending;}}
    }
    private static long overlayPending(Module module){
        long count=0;
        for(OverlayRegistry registry:OVERLAY_REGISTRIES.values()){
            Set<Object> actual=Collections.newSetFromMap(new IdentityHashMap<>());boolean readable=true;
            try{
                Object list=registry.fields.list.get(null),map=registry.fields.map.get(null);
                if(list instanceof List<?> entries)actual.addAll(entries);
                if(map instanceof Map<?,?> entries)actual.addAll(entries.values());
            }catch(ReflectiveOperationException unavailable){readable=false;}
            for(OverlaySlot slot:registry.slots)if(Arrays.stream(overlaySources(slot)).anyMatch(source->source==module)){
                if(!readable){registry.fields.pending="CLIENT_OVERLAY_ACTUAL_PAIR_READ_FAILED";return -1;}
                Object named=slot.named.get();if(!actual.contains(named))continue;
                try{if(!overlayMatches(slot,named)){registry.fields.pending="CLIENT_OVERLAY_NAMED_ASSOCIATION_CHANGED";return -2;}}
                catch(ReflectiveOperationException unavailable){registry.fields.pending="CLIENT_OVERLAY_ACTUAL_PAIR_READ_FAILED";return -1;}
                count++;
            }
        }
        return count;
    }
    /** Same recoverable cause/suppressed contract as Core ClientFaults, at this bootstrap-only boundary. */
    private static boolean uiRecoverable(Throwable failure){
        if(!(failure instanceof RuntimeException||failure instanceof java.io.IOException))return false;
        Set<Throwable> seen=Collections.newSetFromMap(new IdentityHashMap<>());Deque<Throwable> pending=new ArrayDeque<>();pending.add(failure);
        while(!pending.isEmpty()){
            Throwable next=pending.removeFirst();if(!seen.add(next))continue;
            if(!(next instanceof RuntimeException||next instanceof java.io.IOException))return false;
            if(next.getCause()!=null)pending.add(next.getCause());Collections.addAll(pending,next.getSuppressed());
        }
        return true;
    }
    @SuppressWarnings("unchecked")
    private static <E extends Throwable> void uiRaise(Throwable failure)throws E{throw (E)failure;}
    private static void retirePresentation(Object owner,UiFields fields,boolean screen){
        Object target=null;Module[] modules=new Module[0];UiRetirement prior=UI_RETIRING.get();
        try{
            target=fields.current(owner,screen);if(target==null)return;modules=sources(target);
            if(!exclusive(modules))return;
            // A throwing close may already have released resources; do not replay it every frame.
            synchronized(SOURCES){if(FAILURES.containsKey(new Key(target,false)))return;}
            // Re-read the actual slot before calling its original lifecycle; unknown and shared objects stay.
            if(fields.current(owner,screen)!=target)return;
            UI_RETIRING.set(new UiRetirement(owner,target,screen));(screen?fields.setScreen:fields.setOverlay).invoke(owner,new Object[]{null});
            if(fields.current(owner,screen)==target)uiFailure(target,modules,new IllegalStateException("CLIENT_UI_ROOT_RETAINED_AFTER_SETTER"));
            else synchronized(SOURCES){retired++;}
        }catch(InvocationTargetException wrapped){
            Throwable failure=wrapped.getCause()==null?wrapped:wrapped.getCause();
            if(!uiRecoverable(failure))ClientBridge.<RuntimeException>uiRaise(failure);
            uiFailure(target==null?owner:target,modules,failure);
        }catch(ReflectiveOperationException unavailable){uiFailure(target==null?owner:target,modules,unavailable);}
        catch(RuntimeException failure){
            if(!uiRecoverable(failure))ClientBridge.<RuntimeException>uiRaise(failure);
            uiFailure(target==null?owner:target,modules,failure);
        }finally{if(prior==null)UI_RETIRING.remove();else UI_RETIRING.set(prior);}
    }
    public static Object enter(Object contribution){
        var frame=boundary();Module[] owners=union(sources(contribution),currentSources());Scope scope=new Scope(CURRENT.get(),owners,frame);
        synchronized(SOURCES){ACTIVE.add(scope);CodeSourceBridge.contributionControl(owners);}CURRENT.set(scope);return scope;
    }
    public static void exit(Object token){
        var frame=boundary();if(!(token instanceof Scope scope)||scope.type!=frame.getDeclaringClass()||!scope.method.equals(frame.getMethodName())||scope.thread!=Thread.currentThread())throw new SecurityException("ACTUAL_CLIENT_INVOCATION_REQUIRED");
        if(scope.ended)return;if(CURRENT.get()!=scope)throw new IllegalStateException("CLIENT_SCOPE_ORDER_CHANGED");CURRENT.set(scope.parent);scope.ended=true;synchronized(SOURCES){ACTIVE.remove(scope);}
    }
    static Module[] currentSources(){
        if(CURRENT==null)return new Module[0];Set<Module> sources=null;
        for(Scope scope=CURRENT.get();scope!=null;scope=scope.parent)if(scope.sources.length!=0){
            if(sources==null)sources=Collections.newSetFromMap(new IdentityHashMap<>());Collections.addAll(sources,scope.sources);
        }
        return sources==null?new Module[0]:sources.toArray(new Module[0]);
    }
    static boolean stoppedSource(){return stopped(currentSources());}
    public static boolean particleAdmitted(Object particle){
        var frame=boundary();if(!frame.getMethodName().equals("add"))throw new SecurityException("ACTUAL_PARTICLE_ADMISSION_REQUIRED");
        record(particle,union(origins(particle),ControlBridge.invokingSources()));if(stopped(sources(particle))||ControlBridge.stoppedEffects()){removeParticle(particle);return false;}return true;
    }
    public static boolean particleTick(Object particle){boundary();if(stopped(sources(particle))){removeParticle(particle);return false;}return true;}
    public static void reloadListenerRegistered(Object manager,Object listener){
        var frame=boundary();if(!frame.getDeclaringClass().getName().equals("net.minecraft.server.packs.resources.ReloadableResourceManager")||!frame.getMethodName().equals("registerReloadListener"))throw new SecurityException("ACTUAL_RELOAD_REGISTRATION_REQUIRED");
        record(listener,union(origins(listener),ControlBridge.invokingSources()));try{Collection<?> listeners=(Collection<?>)field(manager,frame.getDeclaringClass(),"listeners");synchronized(SOURCES){RELOAD_LISTENERS.put(new Key(manager,true),new Ref<>(listeners));}}
        catch(ReflectiveOperationException unavailable){throw new IllegalStateException("ACTUAL_RELOAD_LIST_UNAVAILABLE",unavailable);}
    }
    public static void reloadStarting(Object manager){
        var frame=boundary();if(!frame.getDeclaringClass().getName().equals("net.minecraft.server.packs.resources.ReloadableResourceManager")||!frame.getMethodName().equals("createReload"))throw new SecurityException("ACTUAL_RELOAD_START_REQUIRED");
        try{Collection<?> listeners=(Collection<?>)field(manager,frame.getDeclaringClass(),"listeners");listeners.removeIf(listener->exclusive(sources(listener)));synchronized(SOURCES){RELOAD_LISTENERS.put(new Key(manager,true),new Ref<>(listeners));}}
        catch(ReflectiveOperationException unavailable){throw new IllegalStateException("ACTUAL_RELOAD_LIST_UNAVAILABLE",unavailable);}
    }
    public static java.util.concurrent.CompletableFuture<Void> skipReload(Object barrier){
        var frame=boundary();try{Class<?> api=Class.forName("net.minecraft.server.packs.resources.PreparableReloadListener$PreparationBarrier",false,frame.getDeclaringClass().getClassLoader());
            if(!role(api)||barrier==null||actualBase(barrier,"net.minecraft.server.packs.resources.SimpleReloadInstance$1")==null||!api.isInstance(barrier))throw new SecurityException("ACTUAL_RELOAD_BARRIER_REQUIRED");
            Object result=api.getMethod("wait",Object.class).invoke(barrier,new Object[]{null});
            if(!(result instanceof java.util.concurrent.CompletableFuture<?> future))throw new IllegalStateException("ACTUAL_RELOAD_BARRIER_RESULT_CHANGED");
            return future.thenApply(ignored->null);
        }catch(ReflectiveOperationException unavailable){throw new IllegalStateException("ACTUAL_RELOAD_BARRIER_UNAVAILABLE",unavailable);}
    }
    public static void reloadReturned(Object listener,Object result){
        boundary();if(!(result instanceof java.util.concurrent.CompletableFuture<?> future))throw new IllegalStateException("ACTUAL_RELOAD_RESULT_UNAVAILABLE");
        Module[] owners=union(sources(listener),currentSources());synchronized(SOURCES){RELOAD_WORK.entrySet().removeIf(entry->entry.getValue().future().isDone());RELOAD_WORK.put(new Key(future,true),new ReloadWork(future,owners));CodeSourceBridge.contributionControl(owners);}
    }
    private static void retireReloadListeners(){
        List<Collection<?>> lists=new ArrayList<>();synchronized(SOURCES){for(Ref<Collection<?>> reference:RELOAD_LISTENERS.values()){Collection<?> list=reference.get();if(list!=null)lists.add(list);}}
        for(Collection<?> list:lists)SourceMapBridge.withList(list,()->list.removeIf(listener->exclusive(sources(listener))));
    }
    public static void verticesBegin(Object builder){GPU.begin(builder,boundary());}
    public static Object verticesEntering(Object builder,Object bulk){return GPU.enter(builder,bulk,boundary());}
    public static void verticesCompleted(Object builder,Object token){GPU.complete(builder,token,boundary());}
    public static void verticesStored(Object builder,Object rendered){GPU.store(builder,rendered,boundary());}
    public static void verticesSortState(Object builder,Object sorting){GPU.sortState(builder,sorting,boundary());}
    public static void verticesSortRestored(Object builder,Object sorting){GPU.sortRestore(builder,sorting,boundary());}
    public static Object verticesUploading(Object buffer,Object rendered){return GPU.uploading(buffer,rendered,boundary());}
    public static void verticesUploaded(Object buffer,Object token){GPU.uploaded(buffer,token,boundary());}
    public static Object verticesDrawing(Object buffer){return GPU.drawing(buffer,boundary());}
    public static void verticesDrawn(Object token){GPU.drawn(token,boundary());}
    public static void verticesClosed(Object buffer){GPU.closed(buffer,boundary());}
    public static List<String> shaderSource(int shader,List<String> source){return GPU.shaderSource(shader,source,boundary());}
    public static void shaderLinked(int program){GPU.linked(program,boundary());}
    public static void shaderCompiled(int shader){GPU.compiled(shader,boundary());}
    public static void shaderProgramClosed(int program){GPU.programClosed(program,boundary());}
    public static void shaderClosed(int shader){GPU.shaderClosed(shader,boundary());}
    public static void gpuContextClosing(){GPU.contextClosing(boundary());}
    public static void gpuMaintenance(){GPU.maintenance(boundary());}

    /** Original CPU vertex/index identities and a fragment mask preserve the original draw and primitive IDs. */
    private static final class GpuImages {
        private static final Class<?> I=int.class;
        private static final class Build {Object batch=new Object();final List<Module[]> vertices=new ArrayList<>();Object mode,format;String pending="";}
        private record VertexEntry(Object builder,Build build,int prior,Module[] sources,Class<?> type,String method,Thread thread){}
        private record VertexImage(Object batch,List<Module[]> vertices,Object state,Object format,Object mode,int count,int[] indices,byte[] payload,int stride,boolean indexOnly,String pending){}
        private static final class Uploaded {VertexImage image;final BitSet hidden=new BitSet(),erased=new BitSet();String pending="GPU_UPLOAD_PENDING";int vao,vertexId,indexId;long fence;Context context;boolean uploadPending,storageReleased,commandsDrained;}
        private record UploadEntry(Object buffer,Uploaded target,VertexImage image,Class<?> type){}
        private record Fragment(String sampler,String enabled,boolean supported,String original){}
        private record Linked(Fragment fragment,boolean supported){}
        private static final class Context {final Class<?> api;final Map<Integer,Fragment> shaders=new HashMap<>();final Map<Integer,Linked> programs=new HashMap<>();int texture,buffer;Context(Class<?> api){this.api=api;}}
        private record DrawEntry(Context context,Linked linked,int program,int sampler,int enabled,int oldSampler,int oldEnabled,int textureUnit,int oldTexture){}
        private final Map<Key,Build> builds=new HashMap<>();
        private final Map<Key,VertexImage> rendered=new HashMap<>(),sorting=new HashMap<>();
        private final Map<Key,Uploaded> uploaded=new HashMap<>();
        private final Map<Long,Context> contexts=new HashMap<>();
        private void require(StackWalker.StackFrame frame,String type,String... methods){if(!frame.getDeclaringClass().getName().equals(type)||!Set.of(methods).contains(frame.getMethodName()))throw new SecurityException("ACTUAL_GPU_BOUNDARY_REQUIRED");}
        private int number(Object receiver,Class<?> type,String name)throws ReflectiveOperationException{return ((Number)field(receiver,type,name)).intValue();}
        private void protect(Object value){synchronized(SOURCES){CodeSourceBridge.contributionControl(value);}}
        void begin(Object builder,StackWalker.StackFrame frame){require(frame,"com.mojang.blaze3d.vertex.BufferBuilder","begin");try{Build build=new Build();build.mode=field(builder,frame.getDeclaringClass(),"mode");build.format=field(builder,frame.getDeclaringClass(),"format");if(number(builder,frame.getDeclaringClass(),"vertices")!=0)build.pending="GPU_BEGIN_VERTEX_STATE_UNOBSERVED";protect(build.vertices);synchronized(this){builds.put(new Key(builder,true),build);}}catch(ReflectiveOperationException failure){throw new IllegalStateException("GPU_BEGIN_UNAVAILABLE",failure);}}
        Object enter(Object builder,Object bulk,StackWalker.StackFrame frame){require(frame,"com.mojang.blaze3d.vertex.BufferBuilder","endVertex","putBulkData");try{Build build;synchronized(this){build=builds.get(new Key(builder,false));}if(build==null)return null;Module[] owners=union(ControlBridge.invokingSources(),currentSources(),origins(bulk));protect(owners);return new VertexEntry(builder,build,number(builder,frame.getDeclaringClass(),"vertices"),owners,frame.getDeclaringClass(),frame.getMethodName(),Thread.currentThread());}catch(ReflectiveOperationException failure){throw new IllegalStateException("GPU_VERTEX_ENTRY_UNAVAILABLE",failure);}}
        void complete(Object builder,Object token,StackWalker.StackFrame frame){require(frame,"com.mojang.blaze3d.vertex.BufferBuilder","endVertex","putBulkData");if(token==null)return;if(!(token instanceof VertexEntry entry)||entry.builder()!=builder||entry.type()!=frame.getDeclaringClass()||!entry.method().equals(frame.getMethodName())||entry.thread()!=Thread.currentThread())throw new SecurityException("ACTUAL_VERTEX_WRITE_REQUIRED");
            try{synchronized(this){Build current=builds.get(new Key(builder,false));if(current!=entry.build())throw new IllegalStateException("GPU_VERTEX_BATCH_CHANGED");int count=number(builder,entry.type(),"vertices");if(entry.prior()!=current.vertices.size()||count<entry.prior()){current.pending="GPU_VERTEX_WRITE_SEQUENCE_UNOBSERVED";return;}while(current.vertices.size()<count)current.vertices.add(entry.sources());}}catch(ReflectiveOperationException failure){throw new IllegalStateException("GPU_VERTEX_COMPLETION_UNAVAILABLE",failure);}}
        private VertexImage image(Object builder,Object result)throws ReflectiveOperationException{
            Class<?> type=actualBase(result,"com.mojang.blaze3d.vertex.BufferBuilder$RenderedBuffer");if(type==null||type!=result.getClass())throw new SecurityException("ACTUAL_RENDERED_BUFFER_REQUIRED");Object state=field(result,type,"drawState");Class<?> shape=actualBase(state,"com.mojang.blaze3d.vertex.BufferBuilder$DrawState");if(shape==null)throw new SecurityException("ACTUAL_DRAW_STATE_REQUIRED");
            Object mode=shape.getMethod("mode").invoke(state),format=shape.getMethod("format").invoke(state);int count=((Number)shape.getMethod("vertexCount").invoke(state)).intValue(),indexCount=((Number)shape.getMethod("indexCount").invoke(state)).intValue();boolean indexOnly=(Boolean)shape.getMethod("indexOnly").invoke(state),sequential=(Boolean)shape.getMethod("sequentialIndex").invoke(state);
            Build build=builds.get(new Key(builder,false));List<Module[]> rows=build==null?List.of():List.copyOf(build.vertices);String pending=build==null?"GPU_VERTEX_BATCH_UNOBSERVED":build.pending;
            if(rows.size()!=count)pending="GPU_VERTEX_COUNT_UNOBSERVED";if(build!=null&&(build.mode!=mode||build.format!=format))pending="GPU_VERTEX_LAYOUT_CHANGED";
            int[] indices=new int[indexCount];Class<?> modeType=actualBase(mode,"com.mojang.blaze3d.vertex.VertexFormat$Mode");if(modeType==null)throw new SecurityException("ACTUAL_VERTEX_MODE_REQUIRED");
            if(sequential){String kind=((Enum<?>)mode).name();int[] offsets=kind.equals("QUADS")?new int[]{0,1,2,2,3,0}:kind.equals("LINES")?new int[]{0,1,2,3,2,1}:null;for(int n=0;n<indexCount;n++)indices[n]=offsets==null?n:n/6*4+offsets[n%6];}
            else{Object indexType=shape.getMethod("indexType").invoke(state);Class<?> indexShape=actualBase(indexType,"com.mojang.blaze3d.vertex.VertexFormat$IndexType");if(indexShape==null)throw new SecurityException("ACTUAL_INDEX_TYPE_REQUIRED");int bytes=number(indexType,indexShape,"bytes");java.nio.ByteBuffer data=((java.nio.ByteBuffer)type.getMethod("indexBuffer").invoke(result)).duplicate().order(java.nio.ByteOrder.nativeOrder());for(int n=0;n<indexCount;n++)indices[n]=bytes==2?Short.toUnsignedInt(data.getShort(n*2)):bytes==4?data.getInt(n*4):-1;}
            for(int index:indices)if(index<0||index>=count){pending="GPU_INDEX_OUTSIDE_OBSERVED_VERTICES";break;}
            Class<?> formatType=actualBase(format,"com.mojang.blaze3d.vertex.VertexFormat");if(formatType==null||format.getClass()!=formatType)throw new SecurityException("ACTUAL_VERTEX_FORMAT_REQUIRED");int stride=((Number)formatType.getMethod("getVertexSize").invoke(format)).intValue();byte[] payload=null;
            if(!indexOnly){java.nio.ByteBuffer data=((java.nio.ByteBuffer)type.getMethod("vertexBuffer").invoke(result)).duplicate();int length=Math.multiplyExact(count,stride);if(length!=data.remaining())pending="GPU_VERTEX_PAYLOAD_LENGTH_CHANGED";else{payload=new byte[length];data.get(payload);protect(payload);}}
            protect(indices);VertexImage image=new VertexImage(build==null?null:build.batch,rows,state,format,mode,count,indices,payload,stride,indexOnly,pending);protect(rows);return image;
        }
        void store(Object builder,Object result,StackWalker.StackFrame frame){require(frame,"com.mojang.blaze3d.vertex.BufferBuilder","storeRenderedBuffer");try{synchronized(this){rendered.put(new Key(result,true),image(builder,result));}}catch(ReflectiveOperationException failure){throw new IllegalStateException("GPU_RENDERED_IMAGE_UNAVAILABLE",failure);}}
        void sortState(Object builder,Object state,StackWalker.StackFrame frame){require(frame,"com.mojang.blaze3d.vertex.BufferBuilder","getSortState");synchronized(this){Build build=builds.get(new Key(builder,false));if(build==null)return;List<Module[]> rows=List.copyOf(build.vertices);protect(rows);sorting.put(new Key(state,true),new VertexImage(build.batch,rows,null,build.format,build.mode,rows.size(),new int[0],null,0,true,build.pending));}}
        void sortRestore(Object builder,Object state,StackWalker.StackFrame frame){require(frame,"com.mojang.blaze3d.vertex.BufferBuilder","restoreSortState");synchronized(this){VertexImage saved=sorting.get(new Key(state,false));Build build=builds.get(new Key(builder,false));if(build==null||saved==null){if(build!=null)build.pending="GPU_SORTING_ORIGIN_UNOBSERVED";return;}Build restored=new Build();restored.batch=saved.batch();restored.vertices.addAll(saved.vertices());restored.mode=saved.mode();restored.format=saved.format();restored.pending=saved.pending();protect(restored.vertices);builds.put(new Key(builder,true),restored);}}
        Object uploading(Object buffer,Object result,StackWalker.StackFrame frame){require(frame,"com.mojang.blaze3d.vertex.VertexBuffer","upload");synchronized(this){Uploaded gpu=uploaded.computeIfAbsent(new Key(buffer,true),ignored->new Uploaded());VertexImage incoming=rendered.get(new Key(result,false));gpu.pending="GPU_UPLOAD_PENDING";gpu.uploadPending=true;return new UploadEntry(buffer,gpu,incoming,frame.getDeclaringClass());}}
        void uploaded(Object buffer,Object token,StackWalker.StackFrame frame){require(frame,"com.mojang.blaze3d.vertex.VertexBuffer","upload");if(!(token instanceof UploadEntry entry)||entry.buffer()!=buffer||entry.type()!=frame.getDeclaringClass())throw new SecurityException("ACTUAL_GPU_UPLOAD_REQUIRED");try{synchronized(this){Uploaded gpu=uploaded.get(new Key(buffer,false));if(gpu!=entry.target())throw new IllegalStateException("GPU_UPLOAD_RECEIVER_CHANGED");VertexImage image=entry.image();if(image==null){gpu.image=null;gpu.pending="GPU_RENDERED_ORIGIN_UNOBSERVED";return;}
                if(image.indexOnly()){VertexImage previous=gpu.image;if(previous==null||previous.batch()!=image.batch()||previous.count()!=image.count()||previous.format()!=image.format()){gpu.pending="GPU_SORTED_VERTEX_STORAGE_UNOBSERVED";return;}image=new VertexImage(previous.batch(),previous.vertices(),image.state(),image.format(),image.mode(),image.count(),image.indices(),previous.payload(),previous.stride(),true,image.pending());}
                if(field(buffer,entry.type(),"format")!=image.format()||field(buffer,entry.type(),"mode")!=image.mode()||number(buffer,entry.type(),"indexCount")!=image.indices().length){gpu.pending="GPU_UPLOAD_STATE_CHANGED";return;}
                gpu.image=image;gpu.vao=number(buffer,entry.type(),"arrayObjectId");gpu.vertexId=number(buffer,entry.type(),"vertexBufferId");gpu.indexId=number(buffer,entry.type(),"indexBufferId");gpu.pending=image.pending();gpu.context=context(entry.type());gpu.uploadPending=false;gpu.storageReleased=false;if(!image.indexOnly()){gpu.hidden.clear();gpu.erased.clear();gpu.commandsDrained=false;if(gpu.fence!=0){gl(gpu.context,"GL32","glDeleteSync",new Class<?>[]{long.class},gpu.fence);gpu.fence=0;}}
            }}catch(ReflectiveOperationException failure){throw new IllegalStateException("GPU_UPLOAD_READBACK_UNAVAILABLE",failure);}}
        private Object gl(Context context,String api,String method,Class<?>[] parameters,Object...args)throws ReflectiveOperationException{return Class.forName("org.lwjgl.opengl."+api,false,context.api.getClassLoader()).getMethod(method,parameters).invoke(null,args);}
        private int integer(Context context,int property)throws ReflectiveOperationException{return ((Number)gl(context,"GL11","glGetInteger",new Class<?>[]{I},property)).intValue();}
        private Context context(Class<?> api)throws ReflectiveOperationException{long handle=((Number)Class.forName("org.lwjgl.glfw.GLFW",false,api.getClassLoader()).getMethod("glfwGetCurrentContext").invoke(null)).longValue();if(handle==0)throw new IllegalStateException("ACTUAL_GL_CONTEXT_REQUIRED");Context context=contexts.get(handle);if(context==null){context=new Context(api);contexts.put(handle,context);protect(context.shaders);protect(context.programs);}return context;}
        List<String> shaderSource(int shader,List<String> source,StackWalker.StackFrame frame){require(frame,"com.mojang.blaze3d.platform.GlStateManager","glShaderSource");try{synchronized(this){Context context=context(frame.getDeclaringClass());int type=((Number)gl(context,"GL20","glGetShaderi",new Class<?>[]{I,I},shader,35663)).intValue();if(type!=35632)return source;
                    String text=String.join("",source),visible=withoutComments(text);java.util.regex.Matcher version=java.util.regex.Pattern.compile("(?m)^\\s*#\\s*version\\s+(\\d+)").matcher(visible),main=java.util.regex.Pattern.compile("\\bvoid\\s+(main)\\s*\\(\\s*(?:void\\s*)?\\)\\s*\\{").matcher(visible);
                    boolean supported=version.find()&&Integer.parseInt(version.group(1))>=150&&main.find()&&!java.util.regex.Pattern.compile("(?m)^\\s*#\\s*define\\s+main\\b").matcher(visible).find();int start=supported?main.start(1):0,end=supported?main.end(1):0;if(supported&&main.find())supported=false;
                    String prefix="ronova_"+UUID.randomUUID().toString().replace("-","");Fragment fragment=new Fragment(prefix+"_mask",prefix+"_enabled",supported,text);context.shaders.put(shader,fragment);if(!supported)return source;
                    String replacement=text.substring(0,start)+prefix+"_original"+text.substring(end)+"\nuniform samplerBuffer "+fragment.sampler()+";\nuniform int "+fragment.enabled()+";\nvoid main(){if("+fragment.enabled()+" != 0 && gl_PrimitiveID >= 0 && texelFetch("+fragment.sampler()+", gl_PrimitiveID).r > 0.5) discard; "+prefix+"_original();}\n";return List.of(replacement);
                }}catch(ReflectiveOperationException failure){throw new IllegalStateException("GPU_FRAGMENT_SOURCE_UNAVAILABLE",failure);}}
        private String withoutComments(String source){char[] chars=source.toCharArray();for(int n=0;n+1<chars.length;n++)if(chars[n]=='/'&&chars[n+1]=='/'){chars[n++]=chars[n]=' ';while(n+1<chars.length&&chars[n+1]!='\n')chars[++n]=' ';}else if(chars[n]=='/'&&chars[n+1]=='*'){chars[n++]=chars[n]=' ';while(n+1<chars.length){if(chars[n]=='*'&&chars[n+1]=='/'){chars[n++]=chars[n]=' ';break;}if(chars[n]!='\n')chars[n]=' ';n++;}}return new String(chars);}
        void linked(int program,StackWalker.StackFrame frame){require(frame,"com.mojang.blaze3d.platform.GlStateManager","glLinkProgram");try{synchronized(this){Context context=context(frame.getDeclaringClass());int count=((Number)gl(context,"GL20","glGetProgrami",new Class<?>[]{I,I},program,35717)).intValue();int[] ids=new int[count],actual=new int[1];gl(context,"GL20","glGetAttachedShaders",new Class<?>[]{I,int[].class,int[].class},program,actual,ids);Fragment fragment=null;boolean supported=true;
                    if(((Number)gl(context,"GL20","glGetProgrami",new Class<?>[]{I,I},program,35714)).intValue()==0){for(int id:ids){Fragment applied=context.shaders.get(id);if(applied!=null&&applied.supported())restoreFragment(context,id,applied);}gl(context,"GL20","glLinkProgram",new Class<?>[]{I},program);supported=false;}
                    for(int n=0;n<actual[0];n++){int type=((Number)gl(context,"GL20","glGetShaderi",new Class<?>[]{I,I},ids[n],35663)).intValue();if(type==35632)fragment=context.shaders.get(ids[n]);else if(type!=35633)supported=false;}context.programs.put(program,new Linked(fragment,supported&&fragment!=null&&fragment.supported()));
                }}catch(ReflectiveOperationException failure){throw new IllegalStateException("GPU_LINK_IMAGE_UNAVAILABLE",failure);}}
        void compiled(int shader,StackWalker.StackFrame frame){require(frame,"com.mojang.blaze3d.platform.GlStateManager","glCompileShader");try{synchronized(this){Context context=context(frame.getDeclaringClass());Fragment applied=context.shaders.get(shader);if(applied!=null&&applied.supported()&&((Number)gl(context,"GL20","glGetShaderi",new Class<?>[]{I,I},shader,35713)).intValue()==0)restoreFragment(context,shader,applied);}}catch(ReflectiveOperationException failure){throw new IllegalStateException("GPU_MASK_COMPILE_FALLBACK_UNAVAILABLE",failure);}}
        private void restoreFragment(Context context,int shader,Fragment fragment)throws ReflectiveOperationException{gl(context,"GL20","glShaderSource",new Class<?>[]{I,CharSequence.class},shader,fragment.original());gl(context,"GL20","glCompileShader",new Class<?>[]{I},shader);context.shaders.put(shader,new Fragment(fragment.sampler(),fragment.enabled(),false,fragment.original()));}
        private int[] primitive(int mode,int n,int[] indices){return switch(mode){case 0->n<indices.length?new int[]{indices[n]}:null;case 1->n*2+1<indices.length?new int[]{indices[n*2],indices[n*2+1]}:null;case 3->n+1<indices.length?new int[]{indices[n],indices[n+1]}:null;case 4->n*3+2<indices.length?new int[]{indices[n*3],indices[n*3+1],indices[n*3+2]}:null;case 5->n+2<indices.length?new int[]{indices[n],indices[n+1],indices[n+2]}:null;case 6->n+2<indices.length?new int[]{indices[0],indices[n+1],indices[n+2]}:null;default->null;};}
        private boolean retiredSources(Module[] modules){if(modules.length==0)return false;synchronized(SOURCES){for(Module module:modules)if(!REQUESTED.contains(module)&&!ControlBridge.modStopped(module))return false;}return true;}
        private BitSet privateVertices(Uploaded gpu)throws ReflectiveOperationException{
            VertexImage image=gpu.image;BitSet selected=new BitSet(image.count()),outside=new BitSet(image.count());for(int n=0;n<image.count();n++)if(retiredSources(image.vertices().get(n)))selected.set(n);
            Class<?> modeType=actualBase(image.mode(),"com.mojang.blaze3d.vertex.VertexFormat$Mode");if(modeType==null)throw new SecurityException("ACTUAL_VERTEX_MODE_REQUIRED");int mode=number(image.mode(),modeType,"asGLMode");
            int count=0;for(int[] part;(part=primitive(mode,count,image.indices()))!=null;count++){boolean owned=true;for(int index:part)owned&=selected.get(index);if(owned)gpu.hidden.set(count);else for(int index:part)outside.set(index);}
            if(mode!=0&&mode!=1&&mode!=3&&mode!=4&&mode!=5&&mode!=6){gpu.pending="GPU_PRIMITIVE_LAYOUT_UNSUPPORTED";selected.clear();return selected;}
            selected.andNot(outside);return selected;
        }
        private boolean sameBytes(java.nio.ByteBuffer current,byte[] original,int offset,int length){for(int n=0;n<length;n++)if(current.get(n)!=original[offset+n])return false;return true;}
        private boolean zeroBytes(java.nio.ByteBuffer current){for(int n=0;n<current.capacity();n++)if(current.get(n)!=0)return false;return true;}
        private boolean eraseVertices(Uploaded gpu,BitSet selected)throws ReflectiveOperationException{
            VertexImage image=gpu.image;if(image.payload()==null||image.stride()<=0){gpu.pending="GPU_VERTEX_PAYLOAD_UNOBSERVED";return false;}Context context=gpu.context;int original=integer(context,36662);boolean complete=true;
            try{gl(context,"GL15","glBindBuffer",new Class<?>[]{I,I},36662,gpu.vertexId);long size=((Number)gl(context,"GL15","glGetBufferParameteri",new Class<?>[]{I,I},36662,34660)).longValue();if(size<image.payload().length){gpu.pending="GPU_VERTEX_STORAGE_SIZE_CHANGED";return false;}
                for(int vertex=selected.nextSetBit(0);vertex>=0;vertex=selected.nextSetBit(vertex+1)){if(gpu.erased.get(vertex))continue;int offset=Math.multiplyExact(vertex,image.stride());java.nio.ByteBuffer current=java.nio.ByteBuffer.allocateDirect(image.stride());
                    gl(context,"GL15","glGetBufferSubData",new Class<?>[]{I,long.class,java.nio.ByteBuffer.class},36662,(long)offset,current);
                    if(!sameBytes(current,image.payload(),offset,image.stride())&&!zeroBytes(current)){gpu.pending="GPU_VERTEX_CONTENT_CHANGED";complete=false;continue;}
                    if(!zeroBytes(current)){java.nio.ByteBuffer zero=java.nio.ByteBuffer.allocateDirect(image.stride());gl(context,"GL15","glBufferSubData",new Class<?>[]{I,long.class,java.nio.ByteBuffer.class},36662,(long)offset,zero);current.clear();gl(context,"GL15","glGetBufferSubData",new Class<?>[]{I,long.class,java.nio.ByteBuffer.class},36662,(long)offset,current);}
                    if(!zeroBytes(current)){gpu.pending="GPU_VERTEX_ERASE_READBACK_PENDING";complete=false;continue;}
                    Arrays.fill(image.payload(),offset,offset+image.stride(),(byte)0);gpu.erased.set(vertex);gpu.commandsDrained=false;if(gpu.fence!=0){gl(context,"GL32","glDeleteSync",new Class<?>[]{long.class},gpu.fence);gpu.fence=0;}
                }
                return complete;
            }finally{gl(context,"GL15","glBindBuffer",new Class<?>[]{I,I},36662,original);}
        }
        private boolean drain(Uploaded gpu)throws ReflectiveOperationException{
            if(gpu.commandsDrained)return true;if(gpu.fence==0){gpu.fence=((Number)gl(gpu.context,"GL32","glFenceSync",new Class<?>[]{I,I},37143,0)).longValue();if(gpu.fence==0){gpu.pending="GPU_RETIREMENT_FENCE_UNAVAILABLE";return false;}gl(gpu.context,"GL11","glFlush",new Class<?>[0]);}
            int status=((Number)gl(gpu.context,"GL32","glClientWaitSync",new Class<?>[]{long.class,I,long.class},gpu.fence,0,0L)).intValue();if(status!=37146&&status!=37148){gpu.pending=status==37149?"GPU_RETIREMENT_WAIT_FAILED":"GPU_PRIOR_COMMANDS_EXIT_PENDING";return false;}
            gl(gpu.context,"GL32","glDeleteSync",new Class<?>[]{long.class},gpu.fence);gpu.fence=0;gpu.commandsDrained=true;return true;
        }
        void maintenance(StackWalker.StackFrame frame){require(frame,"net.minecraft.client.Minecraft","runTick");synchronized(SOURCES){if(REQUESTED.isEmpty())return;}
            try{synchronized(this){Context context=context(frame.getDeclaringClass());for(var entry:uploaded.entrySet()){
                    Uploaded gpu=entry.getValue();Object receiver=entry.getKey().get();if(gpu.context!=context||gpu.image==null||gpu.uploadPending||!gpu.image.pending().isEmpty())continue;
                    if(receiver==null){gpu.pending="GPU_ORIGINAL_CARRIER_RELEASE_UNOBSERVED";continue;}Class<?> type=actualBase(receiver,"com.mojang.blaze3d.vertex.VertexBuffer");if(type==null||number(receiver,type,"vertexBufferId")!=gpu.vertexId||number(receiver,type,"indexBufferId")!=gpu.indexId||number(receiver,type,"arrayObjectId")!=gpu.vao){gpu.pending="GPU_STORAGE_IDENTITY_CHANGED";continue;}
                    BitSet selected=privateVertices(gpu);if(selected.isEmpty())continue;if(!eraseVertices(gpu,selected)||!drain(gpu))continue;
                    boolean allVertices=true;for(Module[] owners:gpu.image.vertices())allVertices&=retiredSources(owners);
                    if(allVertices&&retiredSources(sources(receiver))){Object previous=RELEASING.get();RELEASING.set(receiver);try{platformVoid(receiver,type,"close");}catch(Throwable unavailable){gpu.pending="GPU_PRIVATE_BUFFER_RELEASE:"+unavailable.getClass().getSimpleName();continue;}finally{if(previous==null)RELEASING.remove();else RELEASING.set(previous);}
                        if(number(receiver,type,"vertexBufferId")==-1&&number(receiver,type,"indexBufferId")==-1&&number(receiver,type,"arrayObjectId")==-1){gpu.storageReleased=true;gpu.pending="";}else gpu.pending="GPU_PRIVATE_BUFFER_RELEASE_PENDING";
                    }else gpu.pending="GPU_SHARED_BUFFER_OTHER_SHARES_RETAINED";
                }uploaded.entrySet().removeIf(entry->entry.getValue().storageReleased);}
            }catch(ReflectiveOperationException failure){throw new IllegalStateException("GPU_SOURCE_RETIREMENT_UNAVAILABLE",failure);}
        }
        Object drawing(Object buffer,StackWalker.StackFrame frame){require(frame,"com.mojang.blaze3d.vertex.VertexBuffer","draw");try{synchronized(this){Uploaded gpu=uploaded.get(new Key(buffer,false));if(gpu==null||gpu.image==null||gpu.uploadPending||!gpu.image.pending().isEmpty())return null;VertexImage image=gpu.image;Context context=context(frame.getDeclaringClass());if(context!=gpu.context||integer(context,34229)!=gpu.vao||number(buffer,frame.getDeclaringClass(),"vertexBufferId")!=gpu.vertexId||number(buffer,frame.getDeclaringClass(),"indexBufferId")!=gpu.indexId){gpu.pending="GPU_STORAGE_BINDING_CHANGED";return null;}
                    Class<?> modeType=actualBase(image.mode(),"com.mojang.blaze3d.vertex.VertexFormat$Mode");int mode=number(image.mode(),modeType,"asGLMode"),count=0;boolean shared=false;
                    for(int[] part;(part=primitive(mode,count,image.indices()))!=null;count++){boolean selected=true,any=false;for(int index:part){Module[] owners=image.vertices().get(index);selected&=exclusive(owners);any|=stopped(owners);}if(selected)gpu.hidden.set(count);else if(any)shared=true;}
                    if(count==0||gpu.hidden.isEmpty())return null;if(gpu.hidden.cardinality()==count){gpu.pending=shared?"GPU_SHARED_PRIMITIVE_PENDING":"GPU_PAYLOAD_RETIREMENT_PENDING";return Boolean.FALSE;}
                    int program=integer(context,35725);Linked linked=context.programs.get(program);if(linked==null||!linked.supported()){gpu.pending="GPU_FRAGMENT_MASK_BACKEND_UNAVAILABLE";return null;}
                    int sampler=((Number)gl(context,"GL20","glGetUniformLocation",new Class<?>[]{I,CharSequence.class},program,linked.fragment().sampler())).intValue(),enabled=((Number)gl(context,"GL20","glGetUniformLocation",new Class<?>[]{I,CharSequence.class},program,linked.fragment().enabled())).intValue();if(sampler<0||enabled<0){gpu.pending="GPU_FRAGMENT_MASK_LINK_CHANGED";return null;}
                    int oldSampler=((Number)gl(context,"GL20","glGetUniformi",new Class<?>[]{I,I},program,sampler)).intValue(),oldEnabled=((Number)gl(context,"GL20","glGetUniformi",new Class<?>[]{I,I},program,enabled)).intValue();float[] mask=new float[count];for(int n=gpu.hidden.nextSetBit(0);n>=0&&n<count;n=gpu.hidden.nextSetBit(n+1))mask[n]=1;
                    int[] installed=install(context,program,sampler,enabled,mask);gpu.pending=shared?"GPU_SHARED_PRIMITIVE_PENDING":"GPU_PAYLOAD_RETIREMENT_PENDING";return new DrawEntry(context,linked,program,sampler,enabled,oldSampler,oldEnabled,installed[0],installed[1]);
                }}catch(ReflectiveOperationException failure){throw new IllegalStateException("GPU_PRIMITIVE_MASK_UNAVAILABLE",failure);}}
        private int availableUnit(Context context,int program,int ownSampler)throws ReflectiveOperationException{
            int units=integer(context,35661);BitSet used=new BitSet(units);Set<Integer> samplerTypes=new HashSet<>();
            for(String name:List.of("GL20","GL21","GL30","GL31","GL32","GL33","GL40","GL42","GL43","GL45","GL46"))for(Field field:Class.forName("org.lwjgl.opengl."+name,false,context.api.getClassLoader()).getFields())if(field.getType()==int.class&&Modifier.isStatic(field.getModifiers())&&field.getName().contains("SAMPLER_"))samplerTypes.add(field.getInt(null));
            int count=((Number)gl(context,"GL20","glGetProgrami",new Class<?>[]{I,I},program,35718)).intValue();
            for(int n=0;n<count;n++){java.nio.IntBuffer size=java.nio.ByteBuffer.allocateDirect(4).order(java.nio.ByteOrder.nativeOrder()).asIntBuffer(),type=java.nio.ByteBuffer.allocateDirect(4).order(java.nio.ByteOrder.nativeOrder()).asIntBuffer();
                String name=(String)gl(context,"GL20","glGetActiveUniform",new Class<?>[]{I,I,java.nio.IntBuffer.class,java.nio.IntBuffer.class},program,n,size,type);if(!samplerTypes.contains(type.get(0)))continue;
                for(int element=0;element<size.get(0);element++){String actual=element==0?name:name.contains("[0]")?name.replaceFirst("\\[0\\]","["+element+"]"):name+"["+element+"]";int location=((Number)gl(context,"GL20","glGetUniformLocation",new Class<?>[]{I,CharSequence.class},program,actual)).intValue();if(location<0||location==ownSampler)continue;int unit=((Number)gl(context,"GL20","glGetUniformi",new Class<?>[]{I,I},program,location)).intValue();if(unit>=0&&unit<units)used.set(unit);}
            }
            for(int unit=units-1;unit>=0;unit--)if(!used.get(unit))return unit;throw new IllegalStateException("GPU_MASK_SAMPLER_UNIT_UNAVAILABLE");
        }
        private int[] install(Context context,int program,int sampler,int enabled,float[] mask)throws ReflectiveOperationException{
            int active=integer(context,34016),unit=availableUnit(context,program,sampler),buffer=integer(context,35882);gl(context,"GL13","glActiveTexture",new Class<?>[]{I},33984+unit);int texture=integer(context,35884);boolean installed=false;
            try{if(context.buffer==0)context.buffer=((Number)gl(context,"GL15","glGenBuffers",new Class<?>[0])).intValue();if(context.texture==0)context.texture=((Number)gl(context,"GL11","glGenTextures",new Class<?>[0])).intValue();gl(context,"GL15","glBindBuffer",new Class<?>[]{I,I},35882,context.buffer);gl(context,"GL15","glBufferData",new Class<?>[]{I,float[].class,I},35882,mask,35048);gl(context,"GL11","glBindTexture",new Class<?>[]{I,I},35882,context.texture);gl(context,"GL31","glTexBuffer",new Class<?>[]{I,I,I},35882,33326,context.buffer);gl(context,"GL20","glUniform1i",new Class<?>[]{I,I},sampler,unit);gl(context,"GL20","glUniform1i",new Class<?>[]{I,I},enabled,1);
                installed=true;
            }finally{gl(context,"GL15","glBindBuffer",new Class<?>[]{I,I},35882,buffer);if(!installed)gl(context,"GL11","glBindTexture",new Class<?>[]{I,I},35882,texture);gl(context,"GL13","glActiveTexture",new Class<?>[]{I},active);}return new int[]{unit,texture};
        }
        void drawn(Object token,StackWalker.StackFrame frame){require(frame,"com.mojang.blaze3d.vertex.VertexBuffer","draw");if(token==null||token==Boolean.FALSE)return;if(!(token instanceof DrawEntry entry))throw new SecurityException("ACTUAL_GPU_DRAW_TOKEN_REQUIRED");try{synchronized(this){Context context=entry.context();int active=integer(context,34016);gl(context,"GL13","glActiveTexture",new Class<?>[]{I},33984+entry.textureUnit());try{gl(context,"GL11","glBindTexture",new Class<?>[]{I,I},35882,entry.oldTexture());}finally{gl(context,"GL13","glActiveTexture",new Class<?>[]{I},active);}if(context.programs.get(entry.program())!=entry.linked())return;int current=integer(context,35725);if(current!=entry.program())gl(context,"GL20","glUseProgram",new Class<?>[]{I},entry.program());try{gl(context,"GL20","glUniform1i",new Class<?>[]{I,I},entry.enabled(),entry.oldEnabled());gl(context,"GL20","glUniform1i",new Class<?>[]{I,I},entry.sampler(),entry.oldSampler());}finally{if(current!=entry.program())gl(context,"GL20","glUseProgram",new Class<?>[]{I},current);}}}catch(ReflectiveOperationException failure){throw new IllegalStateException("GPU_MASK_SCOPE_RELEASE_UNAVAILABLE",failure);}}
        void closed(Object buffer,StackWalker.StackFrame frame){require(frame,"com.mojang.blaze3d.vertex.VertexBuffer","close");try{synchronized(this){if(number(buffer,frame.getDeclaringClass(),"vertexBufferId")==-1&&number(buffer,frame.getDeclaringClass(),"indexBufferId")==-1&&number(buffer,frame.getDeclaringClass(),"arrayObjectId")==-1){Uploaded gpu=uploaded.get(new Key(buffer,false));if(gpu!=null){gpu.storageReleased=true;gpu.pending="";if(gpu.fence!=0){gl(gpu.context,"GL32","glDeleteSync",new Class<?>[]{long.class},gpu.fence);gpu.fence=0;}}}}}catch(ReflectiveOperationException failure){throw new IllegalStateException("GPU_BUFFER_CLOSE_READBACK_UNAVAILABLE",failure);}}
        void shaderClosed(int shader,StackWalker.StackFrame frame){require(frame,"com.mojang.blaze3d.platform.GlStateManager","glDeleteShader");try{synchronized(this){context(frame.getDeclaringClass()).shaders.remove(shader);}}catch(ReflectiveOperationException failure){throw new IllegalStateException("GPU_SHADER_CLOSE_UNAVAILABLE",failure);}}
        void programClosed(int program,StackWalker.StackFrame frame){require(frame,"com.mojang.blaze3d.platform.GlStateManager","glDeleteProgram");try{synchronized(this){context(frame.getDeclaringClass()).programs.remove(program);}}catch(ReflectiveOperationException failure){throw new IllegalStateException("GPU_PROGRAM_CLOSE_UNAVAILABLE",failure);}}
        void contextClosing(StackWalker.StackFrame frame){require(frame,"net.minecraft.client.Minecraft","close");try{synchronized(this){Context context=context(frame.getDeclaringClass());if(context.buffer!=0){gl(context,"GL15","glDeleteBuffers",new Class<?>[]{I},context.buffer);if(Boolean.TRUE.equals(gl(context,"GL15","glIsBuffer",new Class<?>[]{I},context.buffer)))throw new IllegalStateException("GPU_MASK_BUFFER_RELEASE_PENDING");context.buffer=0;}if(context.texture!=0){gl(context,"GL11","glDeleteTextures",new Class<?>[]{I},context.texture);if(Boolean.TRUE.equals(gl(context,"GL11","glIsTexture",new Class<?>[]{I},context.texture)))throw new IllegalStateException("GPU_MASK_TEXTURE_RELEASE_PENDING");context.texture=0;}context.programs.clear();context.shaders.clear();contexts.values().removeIf(value->value==context);}}catch(ReflectiveOperationException failure){throw new IllegalStateException("GPU_CONTEXT_RESOURCE_RELEASE_UNAVAILABLE",failure);}}
        synchronized long pending(Module module){builds.entrySet().removeIf(entry->entry.getKey().get()==null);rendered.entrySet().removeIf(entry->entry.getKey().get()==null);sorting.entrySet().removeIf(entry->entry.getKey().get()==null);long count=0;for(Uploaded gpu:uploaded.values())if(gpu.image!=null&&gpu.image.vertices().stream().anyMatch(row->Arrays.stream(row).anyMatch(source->source==module)))count++;return count;}
        synchronized void reap(Key key){builds.remove(key);rendered.remove(key);sorting.remove(key);uploaded.remove(key);}
        Object[] controls(){return new Object[]{this,builds,rendered,sorting,uploaded,contexts};}
    }
    public static boolean soundAdmitted(Object sound){
        var frame=boundary();if(!frame.getDeclaringClass().getName().equals("net.minecraft.client.sounds.SoundEngine")||!Set.of("play","playDelayed","queueTickingSound").contains(frame.getMethodName()))throw new SecurityException("ACTUAL_SOUND_ADMISSION_REQUIRED");
        record(sound,union(origins(sound),ControlBridge.invokingSources()));return !stopped(sources(sound))&&!ControlBridge.stoppedEffects();
    }
    private static void removeSoundRows(Object engine,Class<?> type,Object sound)throws ReflectiveOperationException{
        for(String name:List.of("queuedSounds","soundDeleteTime")){Map<?,?> rows=(Map<?,?>)field(engine,type,name);rows.keySet().removeIf(key->key==sound);}
        for(String name:List.of("tickingSounds","queuedTickableSounds")){Collection<?> rows=(Collection<?>)field(engine,type,name);rows.removeIf(value->value==sound);}
        Object bySource=field(engine,type,"instanceBySource");Class<?> multimap=Class.forName("com.google.common.collect.Multimap",false,type.getClassLoader());
        Collection<?> rows=(Collection<?>)multimap.getMethod("values").invoke(bySource);rows.removeIf(value->value==sound);
    }
    public static void soundTick(Object engine){
        var frame=boundary();if(!frame.getDeclaringClass().getName().equals("net.minecraft.client.sounds.SoundEngine")||!frame.getMethodName().equals("tick"))throw new SecurityException("ACTUAL_SOUND_TICK_REQUIRED");
        synchronized(SOURCES){if(REQUESTED.isEmpty())return;}
        try{
            Class<?> type=frame.getDeclaringClass();Map<?,?> instances=(Map<?,?>)field(engine,type,"instanceToChannel");Object access=field(engine,type,"channelAccess");
            Class<?> accessType=actualBase(access,"net.minecraft.client.sounds.ChannelAccess");if(accessType==null||access.getClass()!=accessType)throw new IllegalStateException("ACTUAL_SOUND_CHANNEL_ACCESS_CHANGED");
            synchronized(SOURCES){SOUNDS.put(new Key(engine,true),new SoundRegistry(new Ref<>(instances)));}
            Set<Object> queued=Collections.newSetFromMap(new IdentityHashMap<>());
            queued.addAll(((Map<?,?>)field(engine,type,"queuedSounds")).keySet());queued.addAll((Collection<?>)field(engine,type,"queuedTickableSounds"));
            for(Object sound:queued)if(stopped(sources(sound)))removeSoundRows(engine,type,sound);
            for(var entry:new ArrayList<>(instances.entrySet())){Object sound=entry.getKey();Module[] owners=sources(sound);if(!stopped(owners))continue;
                Object handle=entry.getValue();Class<?> handleType=actualBase(handle,"net.minecraft.client.sounds.ChannelAccess$ChannelHandle");if(handleType==null||handle.getClass()!=handleType)throw new IllegalStateException("ACTUAL_SOUND_HANDLE_CHANGED");
                Object channel=field(handle,handleType,"channel");Class<?> channelType=channel==null?null:actualBase(channel,"com.mojang.blaze3d.audio.Channel");
                if(channel!=null&&(channelType==null||channel.getClass()!=channelType))throw new IllegalStateException("ACTUAL_SOUND_CHANNEL_CHANGED");
                // A shared original handle keeps its outside sound; only the selected registration is detached.
                boolean sharedHandle=instances.entrySet().stream().anyMatch(peer->peer.getValue()==handle&&peer.getKey()!=sound&&!stopped(sources(peer.getKey())));
                if(!sharedHandle){SoundStop stop;synchronized(SOURCES){stop=SOUND_STOPS.computeIfAbsent(new Key(handle,true),ignored->new SoundStop(handle,channel,access,handleType,channelType,accessType,owners));CodeSourceBridge.contributionControl(stop.modules);}
                    requestSoundStop(stop);
                }
                if(instances.get(sound)==handle)instances.remove(sound);removeSoundRows(engine,type,sound);
            }
            List<SoundStop> pending;synchronized(SOURCES){pending=new ArrayList<>(SOUND_STOPS.values());}
            for(SoundStop stop:pending){requestSoundStop(stop);if(!stop.stopped.get())continue;
                Object current=field(stop.handle,stop.handleType,"channel");boolean released=(Boolean)field(stop.handle,stop.handleType,"stopped");Collection<?> handles=(Collection<?>)field(stop.access,stop.accessType,"channels");
                if(current==null&&released&&!handles.contains(stop.handle))synchronized(SOURCES){SOUND_STOPS.remove(new Key(stop.handle,false));}
            }
        }catch(ReflectiveOperationException|RuntimeException unavailable){synchronized(SOURCES){FAILURES.put(new Key(engine,true),ControlBridge.invokingSources());}throw new IllegalStateException("CLIENT_SOUND_RETIREMENT_UNAVAILABLE",unavailable);}
    }
    private static void requestSoundStop(SoundStop stop)throws ReflectiveOperationException{
        if(stop.requested)return;if(stop.channel==null){stop.stopped.set(true);stop.requested=true;return;}
        java.util.function.Consumer<Object> action=channel->{
            if(channel!=stop.channel){stop.pending="CLIENT_ORIGINAL_SOUND_CHANNEL_CHANGED";return;}
            try{platformVoid(channel,stop.channelType,"stop");stop.stopped.set(true);}
            catch(Throwable unavailable){stop.pending="CLIENT_SOUND_STOP:"+unavailable.getClass().getSimpleName();}
        };
        stop.handleType.getMethod("execute",java.util.function.Consumer.class).invoke(stop.handle,action);stop.requested=true;
    }
    private static void removeParticle(Object particle){
        if(particle==null)return;
        for(Class<?> type=particle.getClass();type!=null;type=type.getSuperclass())if(role(type)&&type.getName().equals("net.minecraft.client.particle.Particle")){
            Object prior=RELEASING.get();RELEASING.set(particle);
            try{Field removed=type.getDeclaredField("removed");removed.setAccessible(true);removed.setBoolean(particle,true);}
            catch(ReflectiveOperationException unavailable){synchronized(SOURCES){FAILURES.put(new Key(particle,true),sources(particle));failed++;}}
            finally{if(prior==null)RELEASING.remove();else RELEASING.set(prior);}return;
        }
        synchronized(SOURCES){FAILURES.put(new Key(particle,true),sources(particle));failed++;}
    }
    /** Exact official/SRG fields for the same real image allocation and release path. */
    private static Field resourceField(Class<?> type,String name)throws ReflectiveOperationException{
        String alternate=null,descriptor=null;
        if(type.getName().equals("com.mojang.blaze3d.platform.NativeImage")){
            if(name.equals("pixels")){alternate="f_84964_";descriptor="J";}
            else if(name.equals("size")){alternate="f_84965_";descriptor="J";}
        }else if(type.getName().equals("net.minecraft.client.renderer.texture.DynamicTexture")&&name.equals("pixels")){
            alternate="f_117977_";descriptor="Lcom/mojang/blaze3d/platform/NativeImage;";
        }
        Field member;
        try{member=type.getDeclaredField(name);}
        catch(NoSuchFieldException original){if(alternate==null)throw original;member=type.getDeclaredField(alternate);}
        if(descriptor!=null&&(Modifier.isStatic(member.getModifiers())||!member.getType().descriptorString().equals(descriptor)))
            throw new IllegalStateException("CLIENT_IMAGE_FIELD_LAYOUT_CHANGED:"+type.getName()+"#"+name);
        member.setAccessible(true);return member;
    }
    private static Object field(Object object,Class<?> type,String name)throws ReflectiveOperationException{return resourceField(type,name).get(object);}
    private static Class<?> actualBase(Object object,String name){for(Class<?> type=object.getClass();type!=null;type=type.getSuperclass())if(type.getName().equals(name)&&role(type))return type;return null;}
    public static void resourceConstructed(Object object){
        var frame=boundary();Class<?> type=frame.getDeclaringClass();
        if(!frame.getMethodName().equals("<init>")&&!(type.getName().equals("net.minecraft.client.renderer.texture.DynamicTexture")&&Set.of("setPixels","m_117988_").contains(frame.getMethodName())))throw new SecurityException("ACTUAL_CLIENT_RESOURCE_PUBLICATION_REQUIRED");
        try{synchronized(SOURCES){
            if(type.getName().equals("com.mojang.blaze3d.platform.NativeImage")){
                long pointer=((Number)field(object,type,"pixels")).longValue(),size=((Number)field(object,type,"size")).longValue();ImageRecord prior=IMAGES.get(new Key(object,false));
                if(prior==null)IMAGES.put(new Key(object,true),new ImageRecord(pointer,size));
                else if(prior.pointer!=pointer||prior.size!=size)throw new IllegalStateException("CLIENT_PIXEL_ALLOCATION_CHANGED");
            }else if(type.getName().equals("net.minecraft.client.renderer.texture.DynamicTexture")){
                Object pixels=field(object,type,"pixels");if(pixels==null)PIXELS.remove(new Key(object,false));else PIXELS.put(new Key(object,true),new Ref<>(pixels));
            }else throw new SecurityException("ACTUAL_CLIENT_RESOURCE_TYPE_REQUIRED");
        }}catch(ReflectiveOperationException unavailable){throw new IllegalStateException("CLIENT_RESOURCE_ASSOCIATION_UNAVAILABLE",unavailable);}
    }
    public static void pixelsExposed(Object texture,Object image){
        var frame=boundary();if(!Set.of("getPixels","m_117991_").contains(frame.getMethodName())||!frame.getDeclaringClass().getName().equals("net.minecraft.client.renderer.texture.DynamicTexture"))throw new SecurityException("ACTUAL_PIXEL_ACCESS_REQUIRED");
        if(image==null)return;Module[] exposed=ControlBridge.invokingSources();synchronized(SOURCES){ImageRecord record=IMAGES.get(new Key(image,false));if(record==null)return;
            if(exposed.length==0)record.unknownExposure=true;else record.exposures=union(record.exposures,exposed);}
    }
    public static Object imageCloseEnter(Object image){
        var frame=boundary();if(!frame.getMethodName().equals("close")||!frame.getDeclaringClass().getName().equals("com.mojang.blaze3d.platform.NativeImage"))throw new SecurityException("ACTUAL_PIXEL_CLOSE_REQUIRED");
        ImageRecord record;synchronized(SOURCES){record=IMAGES.get(new Key(image,false));}if(record==null)return null;record.gate.lock();
        try{long pointer=((Number)field(image,frame.getDeclaringClass(),"pixels")).longValue();if(record.closed?pointer!=0:pointer!=record.pointer)throw new IllegalStateException("CLIENT_ORIGINAL_PIXEL_ALLOCATION_CHANGED");return record;}
        catch(ReflectiveOperationException|RuntimeException unavailable){record.gate.unlock();throw new IllegalStateException("CLIENT_PIXEL_RELEASE_REFUSED",unavailable);}
    }
    public static void imageCloseExit(Object image,Object token,boolean completed){
        var frame=boundary();if(!frame.getMethodName().equals("close")||!frame.getDeclaringClass().getName().equals("com.mojang.blaze3d.platform.NativeImage"))throw new SecurityException("ACTUAL_PIXEL_CLOSE_REQUIRED");
        if(token==null)return;ImageRecord record;synchronized(SOURCES){record=IMAGES.get(new Key(image,false));}
        if(record!=token||!record.gate.isHeldByCurrentThread())throw new SecurityException("ACTUAL_PIXEL_CLOSE_TOKEN_REQUIRED");
        try{if(completed&&((Number)field(image,frame.getDeclaringClass(),"pixels")).longValue()==0)synchronized(SOURCES){record.closed=true;}}
        catch(ReflectiveOperationException unavailable){throw new IllegalStateException("CLIENT_PIXEL_RELEASE_READBACK_UNAVAILABLE",unavailable);}
        finally{record.gate.unlock();}
    }
    private static void platformVoid(Object object,Class<?> declaring,String method)throws Throwable{
        java.lang.invoke.MethodHandles.Lookup access=java.lang.invoke.MethodHandles.privateLookupIn(declaring,java.lang.invoke.MethodHandles.lookup());
        access.findSpecial(declaring,method,java.lang.invoke.MethodType.methodType(void.class),declaring).bindTo(object).invokeExact();
    }
    private static String releasePixels(TextureRetirement retirement,Class<?> dynamic)throws Throwable{
        Object texture=retirement.object,image=field(texture,dynamic,"pixels"),observed;ImageRecord record;boolean foreign=false;
        synchronized(SOURCES){Ref<Object> actual=PIXELS.get(new Key(texture,false));observed=actual==null?null:actual.get();record=image==null?null:IMAGES.get(new Key(image,false));
            if(image!=observed)return "CLIENT_ORIGINAL_TEXTURE_PIXELS_CHANGED";
            if(image==null)return "";if(record==null)return "CLIENT_PIXEL_ALLOCATION_UNOBSERVED";
            if(record.unknownExposure)return "CLIENT_PIXEL_OUTSIDE_EXPOSURE_UNRESOLVED";
            foreign=!exclusive(union(record.exposures,origins(image)));
            // Empty producer sources do not erase the real associations of the observed framework image.
            if(record.exposures.length==0&&origins(image).length==0)foreign=false;
            for(var peer:PIXELS.entrySet())if(peer.getValue().get()==image){Object actualTexture=peer.getKey().get();if(actualTexture!=null&&actualTexture!=texture&&!exclusive(sources(actualTexture)))foreign=true;}
        }
        if(!foreign){Class<?> imageType=actualBase(image,"com.mojang.blaze3d.platform.NativeImage");if(imageType==null||image.getClass()!=imageType)return "CLIENT_PIXEL_CLOSE_IMPLEMENTATION_UNOBSERVED";
            Object previous=RELEASING.get();RELEASING.set(image);try{platformVoid(image,imageType,"close");}finally{if(previous==null)RELEASING.remove();else RELEASING.set(previous);}
            synchronized(SOURCES){if(!record.closed)return "CLIENT_PIXEL_RELEASE_RECEIPT_PENDING";}
        }
        Field pixels=resourceField(dynamic,"pixels");if(pixels.get(texture)!=image)return "CLIENT_TEXTURE_PIXELS_CHANGED_DURING_RELEASE";pixels.set(texture,null);
        if(pixels.get(texture)!=null)return "CLIENT_TEXTURE_PIXEL_DETACH_PENDING";synchronized(SOURCES){PIXELS.remove(new Key(texture,false));}return "";
    }
    private static String releaseTexture(TextureRetirement retirement)throws Throwable{
        if(!retirement.detached)return "CLIENT_TEXTURE_ALIAS_DETACH_PENDING";
        Object object=retirement.object;Class<?> base=actualBase(object,"net.minecraft.client.renderer.texture.AbstractTexture");if(base==null)return "ACTUAL_TEXTURE_BASE_UNAVAILABLE";
        Class<?> dynamic=actualBase(object,"net.minecraft.client.renderer.texture.DynamicTexture");Object previous=RELEASING.get();RELEASING.set(object);
        try{
            if(dynamic!=null&&!retirement.pixelsReleased){String pending=releasePixels(retirement,dynamic);if(!pending.isEmpty())return pending;retirement.pixelsReleased=true;}
            platformVoid(object,base,"releaseId");if(((Number)field(object,base,"id")).intValue()!=-1)return "CLIENT_GPU_ID_RELEASE_PENDING";
            Class<?> implementation=object.getClass().getMethod("close").getDeclaringClass();
            if(implementation!=base&&implementation!=dynamic)return "CLIENT_CUSTOM_TEXTURE_RESOURCE_RETIREMENT_PENDING";
            Class<?> http=actualBase(object,"net.minecraft.client.renderer.texture.HttpTexture");if(http!=null){Object future=field(object,http,"future");if(future instanceof java.util.concurrent.Future<?> task&&!task.isDone())return "CLIENT_TEXTURE_DOWNLOAD_EXIT_PENDING";}
            return "";
        }finally{if(previous==null)RELEASING.remove();else RELEASING.set(previous);}
    }
    private static String detachTexture(TextureRetirement retirement)throws ReflectiveOperationException{
        Object manager=retirement.manager.get();Map<?,?> map=retirement.registry.map().get();if(manager==null||map==null)return "CLIENT_TEXTURE_REGISTRY_RELEASE_UNOBSERVED";
        if(field(manager,retirement.registry.type(),"byPath")!=map)return "CLIENT_TEXTURE_REGISTRY_ASSOCIATION_CHANGED";
        List<Object> keys=new ArrayList<>();for(var entry:map.entrySet())if(entry.getValue()==retirement.object){Texture known;synchronized(SOURCES){known=retirement.registry.entries().get(entry.getKey());}
            if(known==null||known.object().get()!=retirement.object||!exclusive(known.modules()))return "CLIENT_TEXTURE_NEW_OUTSIDE_SHARE_PRESENT";keys.add(entry.getKey());}
        for(Object key:keys)if(map.get(key)==retirement.object){map.remove(key);synchronized(SOURCES){retirement.registry.entries().remove(key);}}
        @SuppressWarnings("unchecked") Set<Object> ticks=(Set<Object>)field(manager,retirement.registry.type(),"tickableTextures");ticks.remove(retirement.object);
        if(ticks.contains(retirement.object)||map.values().stream().anyMatch(value->value==retirement.object))return "CLIENT_TEXTURE_ALIAS_DETACH_PENDING";
        retirement.detached=true;return "";
    }
    public static void textureRegistered(Object manager,Object key,Object input){
        var frame=boundary();if(!frame.getMethodName().equals("register"))throw new SecurityException("ACTUAL_TEXTURE_REGISTRATION_REQUIRED");
        try{
            Map<?,?> map=(Map<?,?>)field(manager,frame.getDeclaringClass(),"byPath");Object published=map.get(key);
            Module[] owners=published==input?union(ControlBridge.invokingSources(),origins(input)):new Module[0];
            synchronized(SOURCES){reap();TextureRegistry registry=TEXTURES.get(new Key(manager,false));
                if(registry==null||registry.map().get()!=map){registry=new TextureRegistry(frame.getDeclaringClass(),new Ref<>(map),new HashMap<>());TEXTURES.put(new Key(manager,true),registry);CodeSourceBridge.contributionControl(registry.entries());}
                Module[] protectedOwners=owners.clone();CodeSourceBridge.contributionControl(protectedOwners);registry.entries().put(key,new Texture(new Ref<>(published),protectedOwners));
            }
        }catch(ReflectiveOperationException unavailable){throw new IllegalStateException("ACTUAL_TEXTURE_REGISTRY_UNAVAILABLE",unavailable);}
    }
    public static void textureTick(Object manager){
        var frame=boundary();if(!frame.getMethodName().equals("tick"))throw new SecurityException("ACTUAL_TEXTURE_TICK_REQUIRED");
        TextureRegistry registry;synchronized(SOURCES){if(REQUESTED.isEmpty())return;registry=TEXTURES.get(new Key(manager,false));}if(registry==null)return;
        Map<?,?> actual=registry.map().get();if(actual==null)return;
        List<Map.Entry<Object,Texture>> entries;synchronized(SOURCES){registry.entries().entrySet().removeIf(entry->entry.getValue().object().get()==null||actual.get(entry.getKey())!=entry.getValue().object().get());entries=new ArrayList<>(registry.entries().entrySet());}
        Set<Object> handled=Collections.newSetFromMap(new IdentityHashMap<>());long mixed=0;
        for(var entry:entries){Texture texture=entry.getValue();Object object=texture.object().get();if(object==null||!stopped(texture.modules())||!handled.add(object))continue;
            boolean privateResource=exclusive(union(texture.modules(),origins(object)));List<Object> keys=new ArrayList<>();
            for(var share:actual.entrySet())if(share.getValue()==object){Texture known;synchronized(SOURCES){known=registry.entries().get(share.getKey());}
                if(known==null||known.object().get()!=object||!exclusive(known.modules()))privateResource=false;keys.add(share.getKey());}
            if(!privateResource){mixed++;continue;}
            try{
                TextureRetirement retirement;synchronized(SOURCES){retirement=RETIREMENTS.computeIfAbsent(new Key(object,true),ignored->new TextureRetirement(object,union(texture.modules(),origins(object)),manager,registry));}
                retirement.pending=detachTexture(retirement);
            }catch(ReflectiveOperationException|RuntimeException unavailable){synchronized(SOURCES){TextureRetirement pending=RETIREMENTS.get(new Key(object,false));if(pending!=null)pending.pending="CLIENT_TEXTURE_DETACH:"+String.valueOf(unavailable.getMessage());}}
        }
        List<TextureRetirement> pending;synchronized(SOURCES){pending=new ArrayList<>(RETIREMENTS.values());}
        for(TextureRetirement retirement:pending)try{String gap=retirement.detached?"":detachTexture(retirement);if(gap.isEmpty())gap=releaseTexture(retirement);synchronized(SOURCES){retirement.pending=gap;if(gap.isEmpty()){RETIREMENTS.remove(new Key(retirement.object,false));retired++;}}}
        catch(Throwable unavailable){synchronized(SOURCES){retirement.pending="CLIENT_TEXTURE_RELEASE:"+unavailable.getClass().getSimpleName()+":"+String.valueOf(unavailable.getMessage());}}
        synchronized(SOURCES){shared=mixed;}
    }
    /** Only framework lifecycle fields of the exact object captured by the real retirement boundary. */
    static boolean cleanupField(Object receiver,Class<?> declaring,String name){
        if(RELEASING==null||RELEASING.get()!=receiver||!role(declaring))return false;
        return declaring.getName().equals("net.minecraft.client.particle.Particle")&&name.equals("removed")
                ||declaring.getName().equals("net.minecraft.client.renderer.texture.AbstractTexture")&&name.equals("id")
                ||declaring.getName().equals("net.minecraft.client.renderer.texture.DynamicTexture")&&Set.of("pixels","f_117977_").contains(name)
                ||declaring.getName().equals("com.mojang.blaze3d.platform.NativeImage")&&Set.of("pixels","f_84964_").contains(name)
                ||declaring.getName().equals("com.mojang.blaze3d.vertex.VertexBuffer")&&Set.of("vertexBufferId","indexBufferId","arrayObjectId").contains(name);
    }
    public static void retire(Module[] modules){agent();synchronized(SOURCES){Collections.addAll(REQUESTED,modules);}retireReloadListeners();}
    public static String retirementGap(Module module){agent();long gpu=GPU.pending(module);synchronized(SOURCES){reap();long active=ACTIVE.stream().filter(scope->Arrays.stream(scope.sources).anyMatch(source->source==module)).count();
        long textures=0;for(TextureRegistry registry:TEXTURES.values())for(Texture texture:registry.entries().values())if(texture.object().get()!=null&&Arrays.stream(texture.modules()).anyMatch(source->source==module))textures++;
        long resources=RETIREMENTS.values().stream().filter(row->Arrays.stream(row.modules).anyMatch(source->source==module)).count();
        long failures=FAILURES.values().stream().filter(row->Arrays.stream(row).anyMatch(source->source==module)).count();
        long sounds=SOUND_STOPS.values().stream().filter(row->Arrays.stream(row.modules).anyMatch(source->source==module)).count();
        RELOAD_WORK.entrySet().removeIf(entry->entry.getValue().future().isDone());long reloads=RELOAD_WORK.values().stream().filter(row->Arrays.stream(row.modules()).anyMatch(source->source==module)).count();
        for(Ref<Collection<?>> reference:RELOAD_LISTENERS.values()){Collection<?> listeners=reference.get();if(listeners!=null)for(Object listener:listeners)if(Arrays.stream(sources(listener)).anyMatch(source->source==module))reloads++;}
        long ui=0;for(var entry:UI_OWNERS.entrySet()){Object owner=entry.getKey().get();if(owner==null)continue;
            try{for(boolean screen:new boolean[]{true,false}){Object current=entry.getValue().current(owner,screen);if(current!=null&&Arrays.stream(sources(current)).anyMatch(source->source==module))ui++;}}
            catch(ReflectiveOperationException unavailable){ui++;}
        }
        long overlays=overlayPending(module);
        String overlayState=overlays==-1?"CLIENT_OVERLAY_ACTUAL_PAIR_READ_FAILED":overlays==-2?"CLIENT_OVERLAY_NAMED_ASSOCIATION_CHANGED":"";
        return active!=0||textures!=0||resources!=0||sounds!=0||reloads!=0||gpu!=0||failures!=0||ui!=0||overlays!=0?"CLIENT_RETIREMENT_PENDING:active="+active+":textures="+textures+":resources="+resources+":sounds="+sounds+":reloads="+reloads+":gpu="+gpu+":ui="+ui+":overlays="+(overlays<0?"unknown":Long.toString(overlays))+":overlayState="+overlayState+":shared="+shared+":releaseFailures="+failures:"";
    }}
    public static Object[] controlObjects(){agent();List<Object> roots=new ArrayList<>(Arrays.asList(SOURCES,TEXTURES,ROLES,REQUESTED,CURRENT,RELEASING,ACTIVE,DEAD,IMAGES,PIXELS,RETIREMENTS,FAILURES,SOUNDS,SOUND_STOPS,RELOAD_LISTENERS,RELOAD_WORK,UI_OWNERS,UI_RETIRING,UI_FIELDS,OVERLAY_EVENTS,OVERLAY_REGISTRIES,OVERLAY_NAMES,OVERLAY_INITIALIZING,OVERLAY_FIELDS));Collections.addAll(roots,GPU.controls());return roots.toArray();}
    private static void reap(){for(Key key;(key=(Key)DEAD.poll())!=null;){SOURCES.remove(key);TEXTURES.remove(key);IMAGES.remove(key);PIXELS.remove(key);FAILURES.remove(key);SOUNDS.remove(key);RELOAD_LISTENERS.remove(key);UI_OWNERS.remove(key);OVERLAY_EVENTS.remove(key);OVERLAY_REGISTRIES.remove(key);OVERLAY_NAMES.remove(key);}}
}
