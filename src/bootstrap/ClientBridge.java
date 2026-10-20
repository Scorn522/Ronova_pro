package dev.ronova.pro.bootstrap;

import java.lang.ref.*;
import java.lang.reflect.*;
import java.util.*;

/** Actual client contribution objects, their invocation scopes and texture registry shares. */
public final class ClientBridge {
    private static final StackWalker WALKER=StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE);
    private static final ReferenceQueue<Object> DEAD=new ReferenceQueue<>();
    private static final Map<Key,ContributionSources> SOURCES=new HashMap<>();
    private static final Map<Key,TextureRegistry> TEXTURES=new HashMap<>();
    private static final Map<Key,ImageRecord> IMAGES=new HashMap<>();
    private static final Map<Key,Ref<Object>> PIXELS=new HashMap<>();
    private static final Map<Key,TextureRetirement> RETIREMENTS=new HashMap<>();
    private static final Map<Key,Module[]> FAILURES=new HashMap<>();
    private static final Map<Key,SoundRegistry> SOUNDS=new HashMap<>();
    private static final Map<Key,SoundStop> SOUND_STOPS=new HashMap<>();
    private static final ThreadLocal<SoundRelease> SOUND_RELEASING=new ThreadLocal<>();
    private static final Map<Key,Ref<Collection<?>>> RELOAD_LISTENERS=new HashMap<>();
    private static final Map<Key,ReloadWork> RELOAD_WORK=new HashMap<>();
    private static final Map<Key,UiFields> UI_OWNERS=new HashMap<>();
    private static final Map<Key,UiRetirement> UI_PENDING=new HashMap<>();
    private static final ThreadLocal<UiRetirement> UI_RETIRING=new ThreadLocal<>();
    private static final String SCREEN="net.minecraft.client.gui.screens.Screen",CONTAINER="net.minecraft.client.gui.components.events.AbstractContainerEventHandler";
    private static final String WIDGET="Lnet/minecraft/client/gui/components/events/GuiEventListener;",RENDERABLE="Lnet/minecraft/client/gui/components/Renderable;";
    private static final Map<Key,ScreenRegistry> SCREEN_WIDGETS=new HashMap<>();
    private static final ThreadLocal<ScreenCall> SCREEN_ADDING=new ThreadLocal<>();
    private static final ThreadLocal<WidgetRetirement> WIDGET_RETIRING=new ThreadLocal<>();
    private static final ClassValue<ScreenFields> SCREEN_FIELDS=new ClassValue<>(){
        protected ScreenFields computeValue(Class<?> type){try{return new ScreenFields(type);}catch(ReflectiveOperationException unavailable){throw new IllegalStateException("CLIENT_SCREEN_FIELDS_UNAVAILABLE",unavailable);}}
    };
    private static final String TOAST_COMPONENT="net.minecraft.client.gui.components.toasts.ToastComponent",TOAST_INSTANCE=TOAST_COMPONENT+"$ToastInstance";
    private static final String TOAST="Lnet/minecraft/client/gui/components/toasts/Toast;";
    private static final Map<Key,ToastRegistry> TOASTS=new HashMap<>();
    private static final ClassValue<ToastFields> TOAST_FIELDS=new ClassValue<>(){
        protected ToastFields computeValue(Class<?> type){try{return new ToastFields(type);}catch(ReflectiveOperationException unavailable){throw new IllegalStateException("CLIENT_TOAST_FIELDS_UNAVAILABLE",unavailable);}}
    };
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
    private static final String RENDER_SYSTEM="com.mojang.blaze3d.systems.RenderSystem",RENDER_CALL="com.mojang.blaze3d.pipeline.RenderCall";
    private static final Map<Key,Ref<RenderQueueFields>> RENDER_QUEUES=new HashMap<>();
    private static final ThreadLocal<RenderReplay> RENDER_REPLAY=new ThreadLocal<>();
    private static final ClassValue<RenderQueueFields> RENDER_FIELDS=new ClassValue<>(){
        protected RenderQueueFields computeValue(Class<?> type){try{return new RenderQueueFields(type);}catch(ReflectiveOperationException unavailable){throw new IllegalStateException("CLIENT_RENDER_QUEUE_FIELDS_UNAVAILABLE",unavailable);}}
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
    private record ContributionSources(Module[] modules,boolean unknown){}
    private record Role(String name,Ref<ClassLoader> loader,Ref<Module> module){}
    private record TextureRegistry(Class<?> type,Ref<Map<?,?>> map,Map<Object,Texture> entries){}
    private record Texture(Ref<Object> object,Module[] modules){}
    private record SoundRegistry(Ref<Map<?,?>> instances,Ref<Collection<?>> listeners){}
    private record ReloadWork(java.util.concurrent.CompletableFuture<?> future,Module[] modules){}
    private static final class RenderQueueFields {
        final Class<?> type,call;final Field queue;final Method execute;
        RenderQueueFields(Class<?> type)throws ReflectiveOperationException{
            this.type=type;if(!type.getName().equals(RENDER_SYSTEM)||!role(type))throw new SecurityException("ACTUAL_RENDER_SYSTEM_REQUIRED");
            queue=uiField(type,"recordingQueue","recordingQueue","Ljava/util/concurrent/ConcurrentLinkedQueue;",true);
            call=Class.forName(RENDER_CALL,false,type.getClassLoader());execute=call.getMethod("execute");
            if(!Modifier.isFinal(queue.getModifiers())||!call.isInterface()||!Modifier.isPublic(call.getModifiers())
                    ||call.getClassLoader()!=type.getClassLoader()||call.getModule()!=type.getModule()
                    ||execute.getDeclaringClass()!=call||Modifier.isStatic(execute.getModifiers())||execute.getReturnType()!=void.class
                    ||execute.getParameterCount()!=0||call.getDeclaredMethods().length!=1)throw new IllegalStateException("CLIENT_RENDER_CALL_CONTRACT_CHANGED");
            overlayProtect(this,queue,execute);
        }
        boolean current(Object actual)throws IllegalAccessException{return actual!=null&&actual.getClass()==java.util.concurrent.ConcurrentLinkedQueue.class&&queue.get(null)==actual;}
    }
    /** A value in the real recordingQueue, with sources belonging only to this publication. */
    private static final class RenderCallEntry implements InvocationHandler {
        final RenderQueueFields fields;final Ref<Object> queue;Object delegate,value;Module[] modules;boolean unknown,claimed,closed;
        RenderCallEntry(RenderQueueFields fields,Object queue,Object delegate,Module[] modules,boolean unknown){
            this.fields=fields;this.queue=new Ref<>(queue);this.delegate=delegate;this.modules=modules;this.unknown=unknown;overlayProtect(this,this.queue,modules);
        }
        public Object invoke(Object proxy,Method method,Object[] arguments)throws Throwable{
            if(method.getDeclaringClass()==Object.class){
                if(method.getName().equals("equals"))return proxy==arguments[0];
                if(method.getName().equals("hashCode"))return System.identityHashCode(proxy);
                if(method.getName().equals("toString"))return "Ronova RenderCall entry@"+Integer.toHexString(System.identityHashCode(proxy));
            }
            RenderReplay replay=RENDER_REPLAY.get();RenderConsumption work=replay==null?null:replay.work;
            if(!method.equals(fields.execute)||proxy!=value||work==null||work.entry!=this||work.value!=proxy
                    ||!claimed||closed||replay.fields!=fields||replay.queue!=queue.get()
                    ||!work.executing||work.dispatched||work.closed||replay.ended||replay.thread!=Thread.currentThread()
                    ||CURRENT.get()!=work.scope||work.scope.ended)throw new SecurityException("ACTUAL_RENDER_CALL_CONSUMPTION_REQUIRED");
            work.dispatched=true;
            try{renderInvoke(fields,delegate);return null;}catch(Throwable failure){work.failure=failure;throw failure;}
        }
    }
    private static final class RenderSubmission {
        final RenderQueueFields fields;final Object queue;Object input;RenderCallEntry entry;final Scope scope;
        final Thread thread=Thread.currentThread();boolean ended;
        RenderSubmission(RenderQueueFields fields,Object queue,Object input,RenderCallEntry entry,Scope scope){this.fields=fields;this.queue=queue;this.input=input;this.entry=entry;this.scope=scope;overlayProtect(this);}
    }
    private static final class RenderReplay {
        final RenderQueueFields fields;final Object queue;final Thread thread=Thread.currentThread();final RenderReplay previous=RENDER_REPLAY.get();
        RenderConsumption work;boolean ended;
        RenderReplay(RenderQueueFields fields,Object queue){this.fields=fields;this.queue=queue;overlayProtect(this);}
    }
    private static final class RenderConsumption {
        final Scope scope;Object value;RenderCallEntry entry;boolean executing,dispatched,closed;Throwable failure;
        RenderConsumption(Object value,RenderCallEntry entry,Scope scope){this.value=value;this.entry=entry;this.scope=scope;overlayProtect(this);}
    }
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
    private record OverlayRegistration(Ref<Object> callback,Module[] modules,boolean unknown){}
    private static final class OverlaySlot {
        final OverlayFields fields;final long generation;final Object key;final Ref<Object> named,callback;Module[] modules;final boolean observed;boolean unknown,retired;
        OverlaySlot(OverlayFields fields,long generation,Object key,Object named,Object callback,Module[] modules,boolean observed,boolean unknown){
            this.fields=fields;this.generation=generation;this.key=key;this.named=new Ref<>(named);this.callback=new Ref<>(callback);this.modules=modules;this.observed=observed;this.unknown=unknown;overlayProtect(this,this.named,this.callback,modules);
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
            this.type=type;instance=uiField(type,"instance","f_90981_","Lnet/minecraft/client/Minecraft;",true);thread=uiField(type,"gameThread","f_91018_","Ljava/lang/Thread;",false);
            screen=uiField(type,"screen","f_91080_","Lnet/minecraft/client/gui/screens/Screen;",false);overlay=uiField(type,"overlay","f_91081_","Lnet/minecraft/client/gui/screens/Overlay;",false);
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
    private static final class ScreenFields {
        final Class<?> type,container;final Field minecraft,children,renderables,narratables,focused,dragging,lastNarratable;final UiFields ui;
        ScreenFields(Class<?> type)throws ReflectiveOperationException{
            this.type=type;container=type.getSuperclass();
            if(!type.getName().equals(SCREEN)||!role(type)||container==null||!container.getName().equals(CONTAINER)||!role(container))throw new IllegalStateException("CLIENT_SCREEN_ACTUAL_BASE_REQUIRED");
            minecraft=uiField(type,"minecraft","f_96541_","Lnet/minecraft/client/Minecraft;",false);
            children=uiField(type,"children","f_96540_","Ljava/util/List;",false);renderables=uiField(type,"renderables","f_169369_","Ljava/util/List;",false);
            narratables=uiField(type,"narratables","f_169368_","Ljava/util/List;",false);lastNarratable=uiField(type,"lastNarratable","f_169365_","Lnet/minecraft/client/gui/narration/NarratableEntry;",false);
            focused=uiField(container,"focused","f_94673_",WIDGET,false);dragging=uiField(container,"isDragging","f_94674_","Z",false);
            if(!Modifier.isFinal(children.getModifiers())||!Modifier.isFinal(renderables.getModifiers())||!Modifier.isFinal(narratables.getModifiers())
                    ||container.getModule()!=type.getModule()||!role(minecraft.getType()))throw new IllegalStateException("CLIENT_SCREEN_LAYOUT_CHANGED");
            ui=UI_FIELDS.get(minecraft.getType());overlayProtect(this,minecraft,children,renderables,narratables,focused,dragging,lastNarratable);
        }
    }
    private static final class ScreenRegistry {
        final ScreenFields fields;final Ref<Object> owner;final Ref<List<?>> children,renderables,narratables;final Map<Key,WidgetSlot> widgets=new HashMap<>();
        int publications;String pending="";
        ScreenRegistry(ScreenFields fields,Object owner,List<?> children,List<?> renderables,List<?> narratables){
            this.fields=fields;this.owner=new Ref<>(owner);this.children=new Ref<>(children);this.renderables=new Ref<>(renderables);this.narratables=new Ref<>(narratables);
            overlayProtect(this,this.owner,this.children,this.renderables,this.narratables,widgets);
        }
    }
    private static final class WidgetSlot {
        final Ref<Object> widget;final int[] counts=new int[3];Module[] modules;boolean unknown,retired;String pending="";
        WidgetSlot(Object widget,Module[] modules,boolean unknown){this.widget=new Ref<>(widget);this.modules=modules;this.unknown=unknown;overlayProtect(this,this.widget,counts,modules);}
    }
    private static final class ScreenCall {
        final ScreenRegistry registry;final Object owner,widget;final int[] before;final boolean rootedBefore;final Scope scope;final ScreenCall previous=SCREEN_ADDING.get();
        final Class<?> type;final String method,descriptor;final Thread thread=Thread.currentThread();Module[] modules;boolean unknown,ended;
        ScreenCall(ScreenRegistry registry,Object owner,Object widget,int[] before,boolean rootedBefore,Module[] modules,boolean unknown,Scope scope,StackWalker.StackFrame at){
            this.registry=registry;this.owner=owner;this.widget=widget;this.before=before;this.rootedBefore=rootedBefore;this.modules=modules;this.unknown=unknown;this.scope=scope;
            type=at.getDeclaringClass();method=at.getMethodName();descriptor=at.getDescriptor();overlayProtect(this,before,modules);
        }
    }
    private static final class WidgetRetirement {
        final ScreenRegistry registry;final WidgetSlot slot;final Object owner,widget;final Thread thread=Thread.currentThread();boolean ended;
        WidgetRetirement(ScreenRegistry registry,WidgetSlot slot,Object owner,Object widget){this.registry=registry;this.slot=slot;this.owner=owner;this.widget=widget;overlayProtect(this);}
    }
    private static final class SoundStop {
        final Object engine,handle,channel,access;final Class<?> engineType,handleType,channelType,accessType;final Module[] modules;
        final java.util.concurrent.Executor executor;final Map<?,?> instances;final Set<Object> sounds=Collections.newSetFromMap(new IdentityHashMap<>());
        final int source;final Object initialized,library;final Class<?> libraryType;final long context;
        final java.util.concurrent.atomic.AtomicBoolean stopped=new java.util.concurrent.atomic.AtomicBoolean(),deleted=new java.util.concurrent.atomic.AtomicBoolean(),requested=new java.util.concurrent.atomic.AtomicBoolean();
        volatile boolean shared,releaseStarted,releaseCompleted;volatile String pending="";
        SoundStop(Object engine,Class<?> engineType,Map<?,?> instances,Object handle,Object channel,Object access,Class<?> handleType,Class<?> channelType,Class<?> accessType,Module[] modules,int source,Object initialized,boolean shared,Object library,Class<?> libraryType,long context,java.util.concurrent.Executor executor){this.engine=engine;this.engineType=engineType;this.instances=instances;this.handle=handle;this.channel=channel;this.access=access;this.handleType=handleType;this.channelType=channelType;this.accessType=accessType;this.modules=modules;this.source=source;this.initialized=initialized;this.shared=shared;this.library=library;this.libraryType=libraryType;this.context=context;this.executor=executor;}
    }
    private static final class SoundRelease {
        final SoundStop stop;final SoundRelease previous=SOUND_RELEASING.get();final Thread thread=Thread.currentThread();boolean ended;
        SoundRelease(SoundStop stop){this.stop=stop;}
    }
    private static final class ImageRecord {
        final long pointer,size;final java.util.concurrent.locks.ReentrantLock gate=new java.util.concurrent.locks.ReentrantLock();
        Module[] exposures=new Module[0];boolean unknownExposure,closed,closeEntered,closeReturned;
        ImageRecord(long pointer,long size){this.pointer=pointer;this.size=size;}
    }
    private static final class TextureRetirement {
        final Object object,originalPixels;final Module[] modules;final Ref<Object> manager;final TextureRegistry registry;final int originalId;String pending="";boolean pixelsReleased,detached,idReleaseStarted;
        TextureRetirement(Object object,Module[] modules,Object manager,TextureRegistry registry,int originalId,Object originalPixels){this.object=object;this.modules=modules;this.manager=new Ref<>(manager);this.registry=registry;this.originalId=originalId;this.originalPixels=originalPixels;}
    }
    private static final class Scope {
        final Scope parent;Module[] sources;final Class<?> type;final String method;final Thread thread;
        final OverlaySlot overlay;final Object toast,toastInstance;boolean sourcesUnknown;final ScreenRegistry screen;final WidgetSlot widget;boolean ended;
        Scope(Scope parent,Module[] sources,StackWalker.StackFrame frame){this(parent,sources,frame,null);}
        Scope(Scope parent,Module[] sources,StackWalker.StackFrame frame,OverlaySlot overlay){this(parent,sources,frame,overlay,null);}
        Scope(Scope parent,Module[] sources,StackWalker.StackFrame frame,OverlaySlot overlay,Object toast){this(parent,sources,frame,overlay,toast,null);}
        Scope(Scope parent,Module[] sources,StackWalker.StackFrame frame,OverlaySlot overlay,Object toast,Object toastInstance){this(parent,sources,frame,overlay,toast,toastInstance,false);}
        Scope(Scope parent,Module[] sources,StackWalker.StackFrame frame,OverlaySlot overlay,Object toast,Object toastInstance,boolean sourcesUnknown){this(parent,sources,frame,overlay,toast,toastInstance,sourcesUnknown,null,null);}
        Scope(Scope parent,Module[] sources,StackWalker.StackFrame frame,OverlaySlot overlay,Object toast,Object toastInstance,boolean sourcesUnknown,ScreenRegistry screen,WidgetSlot widget){this.parent=parent;this.sources=sources;type=frame.getDeclaringClass();method=frame.getMethodName();thread=Thread.currentThread();this.overlay=overlay;this.toast=toast;this.toastInstance=toastInstance;this.sourcesUnknown=sourcesUnknown;this.screen=screen;this.widget=widget;}
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
    private static final class ToastFields {
        final Class<?> type,instanceType;final Field minecraft,queued,visible,occupied,instanceOwner,toast,index,count,visibility,hidden,minecraftToasts;final UiFields ui;
        ToastFields(Class<?> type)throws ReflectiveOperationException{
            this.type=type;minecraft=uiField(type,"minecraft","f_94914_","Lnet/minecraft/client/Minecraft;",false);
            queued=uiField(type,"queued","f_94916_","Ljava/util/Deque;",false);visible=uiField(type,"visible","f_94915_","Ljava/util/List;",false);
            occupied=uiField(type,"occupiedSlots","f_242998_","Ljava/util/BitSet;",false);
            instanceType=Class.forName(TOAST_INSTANCE,false,type.getClassLoader());
            instanceOwner=uiField(instanceType,"this$0","f_94930_","Lnet/minecraft/client/gui/components/toasts/ToastComponent;",false);
            toast=uiField(instanceType,"toast","f_94931_",TOAST,false);index=uiField(instanceType,"index","f_242993_","I",false);count=uiField(instanceType,"slotCount","f_243000_","I",false);
            visibility=uiField(instanceType,"visibility","f_94934_","Lnet/minecraft/client/gui/components/toasts/Toast$Visibility;",false);hidden=uiField(visibility.getType(),"HIDE","HIDE",visibility.getType().descriptorString(),true);
            Class<?> client=minecraft.getType();ui=UI_FIELDS.get(client);minecraftToasts=uiField(client,"toast","f_91003_","Lnet/minecraft/client/gui/components/toasts/ToastComponent;",false);
            if(!role(type)||!role(instanceType)||!role(client)||instanceType.getModule()!=type.getModule()||instanceOwner.getType()!=type||minecraftToasts.getType()!=type
                    ||!Modifier.isFinal(queued.getModifiers())||!Modifier.isFinal(visible.getModifiers())||!Modifier.isFinal(occupied.getModifiers())
                    ||!Modifier.isFinal(toast.getModifiers())||!Modifier.isFinal(index.getModifiers())||!Modifier.isFinal(count.getModifiers()))throw new IllegalStateException("CLIENT_TOAST_LAYOUT_CHANGED");
            overlayProtect(this,minecraft,queued,visible,occupied,instanceOwner,toast,index,count,visibility,hidden,minecraftToasts);
        }
    }
    private static final class ToastRegistry {
        final ToastFields fields;final Ref<Object> owner,client;final Ref<Deque<?>> queued;final Ref<List<?>> visible;final Ref<BitSet> occupied;
        final Map<Key,ToastSlot> slots=new HashMap<>();int frames;String pending="";
        ToastRegistry(ToastFields fields,Object owner,Object client,Deque<?> queued,List<?> visible,BitSet occupied){
            this.fields=fields;this.owner=new Ref<>(owner);this.client=new Ref<>(client);this.queued=new Ref<>(queued);this.visible=new Ref<>(visible);this.occupied=new Ref<>(occupied);
            overlayProtect(this,this.owner,this.client,this.queued,this.visible,this.occupied,slots);
        }
    }
    private static final class ToastSlot {
        final Ref<Object> toast;Module[] modules;boolean unknown,queued,visible;
        ToastSlot(Object toast,Module[] modules,boolean unknown){this.toast=new Ref<>(toast);this.modules=modules;this.unknown=unknown;overlayProtect(this,this.toast,modules);}
    }
    private static final class ToastCall {
        final ToastRegistry registry;final Object owner,toast;final Module[] modules;final boolean unknown,frame;final Scope scope;
        final Class<?> type;final String method,descriptor;final Thread thread=Thread.currentThread();boolean ended;
        ToastCall(ToastRegistry registry,Object owner,Object toast,Module[] modules,boolean unknown,boolean frame,Scope scope,StackWalker.StackFrame at){
            this.registry=registry;this.owner=owner;this.toast=toast;this.modules=modules;this.unknown=unknown;this.frame=frame;this.scope=scope;
            type=at.getDeclaringClass();method=at.getMethodName();descriptor=at.getDescriptor();overlayProtect(this,modules);
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
    private static Module[] sources(Object object){Module[] real=origins(object);synchronized(SOURCES){ContributionSources prior=SOURCES.get(new Key(object,false));return union(real,prior==null?null:prior.modules());}}
    private static void record(Object object,Module[] modules){record(object,modules,false,false);}
    private static void record(Object object,Module[] modules,boolean unknown){record(object,modules,unknown,true);}
    private static void record(Object object,Module[] modules,boolean unknown,boolean observed){
        if(object==null)return;synchronized(SOURCES){
            reap();ContributionSources prior=SOURCES.get(new Key(object,false));Module[] merged=union(prior==null?null:prior.modules(),modules);
            unknown|=prior!=null&&prior.unknown();
            if(merged.length!=0||unknown||observed||prior!=null){ContributionSources next=new ContributionSources(merged,unknown);SOURCES.put(new Key(object,true),next);overlayProtect(next,(Object)merged);}
        }
    }
    private static boolean sourceUnknown(Object object){
        if(object==null)return false;
        boolean unknown=ControlBridge.objectSourceUnknown(object)||toastNullSource(ControlBridge.objectSources(object));
        synchronized(SOURCES){
            ContributionSources prior=SOURCES.get(new Key(object,false));
            if(prior!=null){
                if(unknown&&!prior.unknown()){ContributionSources next=new ContributionSources(prior.modules(),true);SOURCES.put(new Key(object,true),next);overlayProtect(next);}
                unknown|=prior.unknown();
            }
        }
        return unknown;
    }
    public static boolean allowed(Object contribution){
        boundary();WidgetSlot slot=currentWidget(contribution);
        if(slot!=null&&(slot.retired||exclusive(widgetSources(slot))&&!slot.unknown))return false;
        return !stopped(sources(contribution))&&!ControlBridge.stoppedEffects();
    }
    private static Field uiField(Class<?> type,String named,String installed,String descriptor,boolean staticMember)throws ReflectiveOperationException{
        Field result;try{result=type.getDeclaredField(named);}catch(NoSuchFieldException production){result=type.getDeclaredField(installed);}
        if(Modifier.isStatic(result.getModifiers())!=staticMember||!result.getType().descriptorString().equals(descriptor))throw new IllegalStateException("CLIENT_UI_FIELD_TYPES_CHANGED");result.setAccessible(true);return result;
    }
    private static boolean clientMember(StackWalker.StackFrame frame,String type,String named,String installed,String descriptor){
        return frame.getDeclaringClass().getName().equals(type)&&(frame.getMethodName().equals(named)||frame.getMethodName().equals(installed))&&frame.getDescriptor().equals(descriptor);
    }
    private static Method clientMethod(Class<?> type,String named,String installed,String descriptor)throws ReflectiveOperationException{
        return clientMethod(type,named,installed,descriptor,new Class<?>[0]);
    }
    private static Method clientMethod(Class<?> type,String named,String installed,String descriptor,Class<?>...parameters)throws ReflectiveOperationException{
        Method result;try{result=type.getDeclaredMethod(named,parameters);}catch(NoSuchMethodException production){result=type.getDeclaredMethod(installed,parameters);}
        if(Modifier.isStatic(result.getModifiers())||!java.lang.invoke.MethodType.methodType(result.getReturnType(),result.getParameterTypes()).descriptorString().equals(descriptor))throw new IllegalStateException("CLIENT_METHOD_LAYOUT_CHANGED:"+type.getName()+"#"+named);
        result.setAccessible(true);return result;
    }
    private static boolean renderMember(StackWalker.StackFrame at,boolean publication){
        return clientMember(at,RENDER_SYSTEM,publication?"recordRenderCall":"replayQueue",publication?"recordRenderCall":"replayQueue",publication?"(Lcom/mojang/blaze3d/pipeline/RenderCall;)V":"()V");
    }
    private static RenderQueueFields renderFields(StackWalker.StackFrame at,Object queue){
        RenderQueueFields fields=RENDER_FIELDS.get(at.getDeclaringClass());
        try{if(!fields.current(queue))throw new SecurityException("ACTUAL_RENDER_RECORDING_QUEUE_REQUIRED");}
        catch(IllegalAccessException unavailable){throw new IllegalStateException("CLIENT_RENDER_RECORDING_QUEUE_UNAVAILABLE",unavailable);}
        synchronized(SOURCES){
            Ref<RenderQueueFields> previous=RENDER_QUEUES.get(new Key(queue,false));
            if(previous==null||previous.get()==null){Ref<RenderQueueFields> reference=new Ref<>(fields);RENDER_QUEUES.put(new Key(queue,true),reference);overlayProtect(reference);}
            else if(previous.get()!=fields)throw new SecurityException("ACTUAL_RENDER_QUEUE_OWNER_CHANGED");
        }
        return fields;
    }
    private static Module[] renderOrigins(Module[] actual){return Arrays.stream(actual).filter(ControlBridge::producer).toArray(Module[]::new);}
    private static Module[] renderEntrySources(RenderCallEntry entry){
        if(entry.closed)return entry.modules;
        Module[] actual=ControlBridge.objectSources(entry.delegate);
        entry.modules=union(entry.modules,renderOrigins(actual));entry.unknown|=ControlBridge.objectSourceUnknown(entry.delegate)||toastNullSource(actual);
        overlayProtect((Object)entry.modules);return entry.modules;
    }
    private static RenderCallEntry renderEntry(RenderQueueFields fields,Object queue,Object value){
        if(value==null||!Proxy.isProxyClass(value.getClass()))return null;
        InvocationHandler handler=Proxy.getInvocationHandler(value);if(!(handler instanceof RenderCallEntry entry))return null;
        if(entry.fields!=fields||entry.queue.get()!=queue||entry.value!=value)throw new SecurityException("ACTUAL_RENDER_QUEUE_ENTRY_REQUIRED");return entry;
    }
    public static Object renderCallEntering(Object queue,Object callback){
        var at=boundary();if(!renderMember(at,true))throw new SecurityException("ACTUAL_RENDER_CALL_PUBLICATION_REQUIRED");RenderQueueFields fields=renderFields(at,queue);
        if(callback!=null&&!fields.call.isInstance(callback))throw new SecurityException("ACTUAL_RENDER_CALL_REQUIRED");
        Module[] actual=ControlBridge.objectSources(callback),invoking=ControlBridge.invokingSources(),modules=union(renderOrigins(actual),invoking,currentSources());
        boolean unknown=ControlBridge.objectSourceUnknown(callback)||ControlBridge.sourceUnknown()||toastNullSource(actual)||toastNullSource(invoking);
        if(stopped(modules)||ControlBridge.stoppedEffects())return Boolean.FALSE;
        Scope scope=new Scope(CURRENT.get(),modules,at,null,null,null,unknown);
        synchronized(SOURCES){ACTIVE.add(scope);overlayProtect(scope,modules);}CURRENT.set(scope);
        try{
            RenderCallEntry entry=callback==null?null:new RenderCallEntry(fields,queue,callback,modules,unknown);
            if(entry!=null){entry.value=Proxy.newProxyInstance(fields.call.getClassLoader(),new Class<?>[]{fields.call},entry);overlayProtect(entry.value);}
            return new RenderSubmission(fields,queue,callback,entry,scope);
        }catch(Throwable failure){exit(scope);ClientBridge.<RuntimeException>uiRaise(failure);return null;}
    }
    public static Object renderCallValue(Object queue,Object callback,Object token){
        var at=boundary();if(!renderMember(at,true)||!(token instanceof RenderSubmission submission)||submission.ended
                ||submission.thread!=Thread.currentThread()||submission.queue!=queue||submission.input!=callback
                ||submission.fields!=renderFields(at,queue)||CURRENT.get()!=submission.scope||submission.scope.ended)
            throw new SecurityException("ACTUAL_RENDER_CALL_QUEUE_WRITE_REQUIRED");
        Module[] actual=ControlBridge.objectSources(callback),invoking=ControlBridge.invokingSources();
        synchronized(SOURCES){
            Module[] modules=union(submission.scope.sources,renderOrigins(actual),invoking,currentSources());
            boolean unknown=submission.scope.sourcesUnknown||ControlBridge.objectSourceUnknown(callback)||ControlBridge.sourceUnknown()||toastNullSource(actual)||toastNullSource(invoking);
            submission.scope.sources=modules;submission.scope.sourcesUnknown=unknown;overlayProtect((Object)modules);
            RenderCallEntry entry=submission.entry;
            if(entry!=null){if(entry.claimed||entry.closed)throw new SecurityException("RENDER_CALL_ENTRY_ALREADY_CONSUMED");entry.modules=modules;entry.unknown|=unknown;}
            if(stopped(modules)||ControlBridge.stoppedEffects())return Boolean.FALSE;
            // A null argument still reaches the original CLQ.add(null) contract.
            return entry==null?null:entry.value;
        }
    }
    public static void renderCallSubmitted(Object token){
        var at=boundary();if(!renderMember(at,true)||!(token instanceof RenderSubmission submission)
                ||submission.thread!=Thread.currentThread()||submission.fields.type!=at.getDeclaringClass())throw new SecurityException("ACTUAL_RENDER_CALL_PUBLICATION_TAIL_REQUIRED");
        if(submission.ended)return;exit(submission.scope);submission.ended=true;submission.input=null;submission.entry=null;
        // The consumer can finish before add returns; this tail never revives or
        // clears a receipt merely because the publishing method threw.
    }
    public static Object renderQueueEntering(Object queue){
        var at=boundary();if(!renderMember(at,false))throw new SecurityException("ACTUAL_RENDER_QUEUE_REPLAY_REQUIRED");RenderQueueFields fields=renderFields(at,queue);
        if(ControlBridge.stoppedEffects())return Boolean.FALSE;RenderReplay replay=new RenderReplay(fields,queue);RENDER_REPLAY.set(replay);return replay;
    }
    private static RenderReplay renderReplay(StackWalker.StackFrame at){
        RenderReplay replay=RENDER_REPLAY.get();
        if(!renderMember(at,false)||replay==null||replay.ended||replay.thread!=Thread.currentThread()||replay.fields.type!=at.getDeclaringClass()
                ||replay.fields!=renderFields(at,replay.queue))throw new SecurityException("ACTUAL_RENDER_QUEUE_REPLAY_REQUIRED");return replay;
    }
    /** Remove the actual queue node and publish its active scope under one retirement lock. */
    public static Object renderCallPoll(Object queue){
        var at=boundary();RenderReplay replay=renderReplay(at);if(replay.queue!=queue||replay.work!=null)throw new SecurityException("ACTUAL_RENDER_QUEUE_POLL_REQUIRED");
        synchronized(SOURCES){
            Object value=((java.util.concurrent.ConcurrentLinkedQueue<?>)queue).poll();RenderCallEntry entry=renderEntry(replay.fields,queue,value);
            if(entry!=null&&(entry.claimed||entry.closed))throw new SecurityException("RENDER_CALL_ENTRY_ALREADY_CONSUMED");
            Module[] actual=ControlBridge.objectSources(value),invoking=ControlBridge.invokingSources();
            Module[] modules=union(entry==null?renderOrigins(actual):renderEntrySources(entry),invoking,currentSources());
            boolean unknown=entry==null||entry.unknown||ControlBridge.objectSourceUnknown(value)||ControlBridge.sourceUnknown()||toastNullSource(actual)||toastNullSource(invoking);
            Scope scope=new Scope(CURRENT.get(),modules,at,null,null,null,unknown);RenderConsumption work=new RenderConsumption(value,entry,scope);
            ACTIVE.add(scope);overlayProtect(scope,modules);CURRENT.set(scope);replay.work=work;if(entry!=null)entry.claimed=true;return value;
        }
    }
    private static void renderInvoke(RenderQueueFields fields,Object callback)throws Throwable{
        try{fields.execute.invoke(callback);}catch(InvocationTargetException failure){throw failure.getCause();}
    }
    private static void renderConsumed(RenderReplay replay,RenderConsumption work){
        if(work.closed)return;exit(work.scope);
        synchronized(SOURCES){
            work.closed=true;RenderCallEntry entry=work.entry;
            if(entry!=null){entry.closed=true;entry.delegate=null;entry.value=null;entry.modules=new Module[0];overlayProtect((Object)entry.modules);}
            work.value=null;work.entry=null;work.failure=null;work.executing=false;replay.work=null;
        }
    }
    public static void renderCallExecute(Object value){
        var at=boundary();RenderReplay replay=renderReplay(at);RenderConsumption work=replay.work;
        if(work==null||work.closed||work.value!=value||CURRENT.get()!=work.scope||work.scope.ended||work.executing)throw new SecurityException("ACTUAL_RENDER_CALL_EXECUTE_REQUIRED");
        try{
            synchronized(SOURCES){
                if(work.entry!=null){work.scope.sources=union(work.scope.sources,renderEntrySources(work.entry));work.scope.sourcesUnknown|=work.entry.unknown;overlayProtect((Object)work.scope.sources);}
            }
            if(stopped(work.scope.sources)||ControlBridge.stoppedEffects())return;
            work.executing=true;
            try{renderInvoke(replay.fields,value);}catch(Throwable failure){ClientBridge.<RuntimeException>uiRaise(work.failure==null?failure:work.failure);}
        }finally{renderConsumed(replay,work);}
    }
    public static void renderQueueExiting(Object token){
        var at=boundary();if(!(token instanceof RenderReplay replay)||!renderMember(at,false)||replay.fields.type!=at.getDeclaringClass()
                ||replay.thread!=Thread.currentThread())throw new SecurityException("ACTUAL_RENDER_QUEUE_REPLAY_TAIL_REQUIRED");
        if(replay.ended)return;if(RENDER_REPLAY.get()!=replay)throw new IllegalStateException("CLIENT_RENDER_REPLAY_ORDER_CHANGED");
        if(replay.work!=null)renderConsumed(replay,replay.work);replay.ended=true;
        if(replay.previous==null)RENDER_REPLAY.remove();else RENDER_REPLAY.set(replay.previous);
    }
    private static long renderPending(Module module){
        long pending=0;
        for(var row:RENDER_QUEUES.entrySet()){
            Object queue=row.getKey().get();RenderQueueFields fields=row.getValue().get();if(queue==null||fields==null)continue;
            try{
                if(!fields.current(queue))return -1;
                for(Object value:((java.util.concurrent.ConcurrentLinkedQueue<?>)queue).toArray()){
                    RenderCallEntry entry=renderEntry(fields,queue,value);Module[] modules=entry==null?origins(value):renderEntrySources(entry);
                    if(entry!=null&&entry.closed)continue;for(Module source:modules)if(source==module){pending++;break;}
                }
            }catch(IllegalAccessException unavailable){return -1;}
        }
        return pending;
    }
    private static Method uiSetter(Class<?> type,String named,String installed,Class<?> parameter)throws ReflectiveOperationException{
        Method result;try{result=type.getDeclaredMethod(named,parameter);}catch(NoSuchMethodException production){result=type.getDeclaredMethod(installed,parameter);}
        if(Modifier.isStatic(result.getModifiers())||result.getReturnType()!=void.class)throw new IllegalStateException("CLIENT_UI_SETTER_CHANGED");return result;
    }
    private static boolean screenSetter(StackWalker.StackFrame frame){return clientMember(frame,"net.minecraft.client.Minecraft","setScreen","m_91152_","(Lnet/minecraft/client/gui/screens/Screen;)V");}
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
        var frame=boundary();if(!screenSetter(frame)&&!clientMember(frame,"net.minecraft.client.Minecraft","setOverlay","m_91150_","(Lnet/minecraft/client/gui/screens/Overlay;)V"))throw new SecurityException("ACTUAL_UI_SETTER_REQUIRED");
        Module[] actual=ControlBridge.objectSources(input),invoking=ControlBridge.invokingSources(),modules=union(origins(input),invoking,currentSources());
        record(input,modules,ControlBridge.objectSourceUnknown(input)||ControlBridge.sourceUnknown()||toastNullSource(actual)||toastNullSource(invoking));
        if(stopped(sources(input))||ControlBridge.stoppedEffects())return Boolean.FALSE;
        boolean screen=screenSetter(frame);UiRetirement requested=UI_RETIRING.get();
        boolean retirement=requested!=null&&requested.owner==owner&&requested.screen==screen&&input==null&&requested.call==null;
        try{
            UiFields fields=uiOwner(owner,frame);Object before=fields.current(owner,screen);
            // Refuse a stale/private retirement before the original closing callbacks run.
            if(retirement&&(requested.target!=before||!presentationRetirable(owner,fields,screen,before)))return Boolean.FALSE;
            UiCall call=new UiCall(owner,input,before,fields,screen,retirement,frame.getMethodName());
            if(retirement)requested.call=call;CodeSourceBridge.contributionControl(call);return call;
        }catch(ReflectiveOperationException|IllegalStateException unavailable){uiFailure(owner,modules,unavailable);return retirement?Boolean.FALSE:null;}
    }
    private static UiCall uiCall(Object token,StackWalker.StackFrame frame){
        if(!(token instanceof UiCall call)||call.fields.type!=frame.getDeclaringClass()||!call.method.equals(frame.getMethodName())||call.thread!=Thread.currentThread())throw new SecurityException("ACTUAL_UI_INVOCATION_REQUIRED");return call;
    }
    public static boolean presentationWriting(Object owner,Object next,Object token){
        var frame=boundary();
        if(token==null){record(next,new Module[0],true);return !stopped(sources(next))&&!ControlBridge.stoppedEffects();}
        UiCall call=uiCall(token,frame);if(call.owner!=owner||call.ended)throw new SecurityException("ACTUAL_UI_SLOT_REQUIRED");
        Module[] actual=ControlBridge.objectSources(next),invoking=ControlBridge.invokingSources();
        record(next,union(origins(next),next==call.input?invoking:new Module[0],currentSources()),
                ControlBridge.objectSourceUnknown(next)||ControlBridge.sourceUnknown()||toastNullSource(actual)||toastNullSource(invoking));
        if(stopped(sources(next))||ControlBridge.stoppedEffects())return false;
        try{
            // Closing callbacks can publish a new legitimate UI. The old retirement cannot overwrite it.
            if(!call.retirement)return true;UiRetirement requested=UI_RETIRING.get();
            return requested!=null&&requested.call==call&&requested.owner==owner&&requested.screen==call.screen&&requested.target==call.before
                    &&presentationRetirable(owner,call.fields,call.screen,call.before);
        }catch(ReflectiveOperationException unavailable){uiFailure(owner,union(sources(call.before),sources(next)),unavailable);return false;}
    }
    public static void presentationExiting(Object token){
        var frame=boundary();if(token==null)return;UiCall call=uiCall(token,frame);if(call.ended)return;call.ended=true;
    }
    public static void presentationMaintenance(Object owner){
        var frame=boundary();if(!clientMember(frame,"net.minecraft.client.Minecraft","runTick","m_91383_","(Z)V"))throw new SecurityException("ACTUAL_CLIENT_FRAME_REQUIRED");
        UiFields fields;
        try{
            fields=uiOwner(owner,frame);if(fields.thread.get(owner)!=Thread.currentThread())throw new IllegalStateException("CLIENT_UI_MAIN_THREAD_REQUIRED");
        }catch(ReflectiveOperationException|IllegalStateException unavailable){uiFailure(owner,new Module[0],unavailable);return;}
        UiRetirement active=UI_RETIRING.get();if(active!=null&&active.owner==owner)return;
        retireOverlays();retirePresentation(owner,fields,false);retirePresentation(owner,fields,true);retireToasts(owner);retireWidgets(owner);
    }
    private static ScreenRegistry screenRegistry(Object owner,Class<?> type)throws ReflectiveOperationException{
        if(owner==null||actualBase(owner,SCREEN)!=type)throw new SecurityException("ACTUAL_SCREEN_OWNER_REQUIRED");
        synchronized(SOURCES){ScreenRegistry prior=SCREEN_WIDGETS.get(new Key(owner,false));if(prior!=null)return prior;}
        ScreenFields fields=SCREEN_FIELDS.get(type);Object children=fields.children.get(owner),renderables=fields.renderables.get(owner),narratables=fields.narratables.get(owner);
        for(Object list:new Object[]{children,renderables,narratables})if(list==null||list.getClass()!=ArrayList.class)throw new IllegalStateException("CLIENT_SCREEN_ACTUAL_LISTS_REQUIRED");
        ScreenRegistry registry=new ScreenRegistry(fields,owner,(List<?>)children,(List<?>)renderables,(List<?>)narratables);
        synchronized(SOURCES){ScreenRegistry prior=SCREEN_WIDGETS.get(new Key(owner,false));if(prior!=null)return prior;SCREEN_WIDGETS.put(new Key(owner,true),registry);}return registry;
    }
    private static boolean screenLists(ScreenRegistry registry)throws ReflectiveOperationException{
        Object owner=registry.owner.get();return owner!=null&&registry.fields.children.get(owner)==registry.children.get()
                &&registry.fields.renderables.get(owner)==registry.renderables.get()&&registry.fields.narratables.get(owner)==registry.narratables.get();
    }
    private static int identityCount(List<?> list,Object value){int count=0;for(int index=0;index<list.size();index++)if(list.get(index)==value)count++;return count;}
    private static int[] widgetCounts(ScreenRegistry registry,Object widget)throws ReflectiveOperationException{
        if(!screenLists(registry))throw new IllegalStateException("CLIENT_SCREEN_ACTUAL_LISTS_CHANGED");
        return new int[]{identityCount(registry.children.get(),widget),identityCount(registry.renderables.get(),widget),identityCount(registry.narratables.get(),widget)};
    }
    public static void screenConstructed(Object owner){
        var at=boundary();if(!clientMember(at,SCREEN,"<init>","<init>","(Lnet/minecraft/network/chat/Component;)V"))throw new SecurityException("ACTUAL_SCREEN_CONSTRUCTION_REQUIRED");
        try{screenRegistry(owner,at.getDeclaringClass());}catch(ReflectiveOperationException|IllegalStateException unavailable){uiFailure(owner,new Module[0],unavailable);}
    }
    private static boolean screenAddition(StackWalker.StackFrame at){
        return clientMember(at,SCREEN,"addRenderableWidget","m_142416_","("+WIDGET+")"+WIDGET)
                ||clientMember(at,SCREEN,"addWidget","m_7787_","("+WIDGET+")"+WIDGET)
                ||clientMember(at,SCREEN,"addRenderableOnly","m_169394_","("+RENDERABLE+")"+RENDERABLE)
                ||clientMember(at,SCREEN,"addEventWidget","addEventWidget","("+WIDGET+")V");
    }
    public static Object screenAdding(Object owner,Object widget){
        var at=boundary();if(!screenAddition(at))throw new SecurityException("ACTUAL_SCREEN_REGISTRATION_REQUIRED");
        Module[] actual=ControlBridge.objectSources(widget),invoking=ControlBridge.invokingSources(),modules=union(origins(widget),invoking,currentSources());
        boolean unknown=ControlBridge.objectSourceUnknown(widget)||ControlBridge.sourceUnknown()||toastNullSource(actual)||toastNullSource(invoking);
        if(stopped(modules)||ControlBridge.stoppedEffects())return Boolean.FALSE;ScreenRegistry registry=null;int[] before=null;boolean rootedBefore=false;
        try{
            registry=screenRegistry(owner,at.getDeclaringClass());before=widgetCounts(registry,widget);Object client=registry.fields.minecraft.get(owner);
            rootedBefore=registry.fields.focused.get(owner)==widget||registry.fields.lastNarratable.get(owner)==widget;
            if(client!=null&&(registry.fields.ui.instance.get(null)!=client||registry.fields.ui.thread.get(client)!=Thread.currentThread()))unknown=true;
        }catch(ReflectiveOperationException|IllegalStateException unavailable){registry=null;before=null;unknown=true;uiFailure(owner,modules,unavailable);}
        Scope scope=new Scope(CURRENT.get(),modules,at,null,null,null,unknown,registry,null);
        synchronized(SOURCES){ACTIVE.add(scope);if(registry!=null)registry.publications++;overlayProtect(scope,scope.sources);}CURRENT.set(scope);
        ScreenCall call=new ScreenCall(registry,owner,widget,before,rootedBefore,modules,unknown,scope,at);SCREEN_ADDING.set(call);return call;
    }
    private static ScreenCall screenCall(Object token,StackWalker.StackFrame at){
        if(!(token instanceof ScreenCall call)||call.thread!=Thread.currentThread()||call.type!=at.getDeclaringClass()||!call.method.equals(at.getMethodName())
                ||!call.descriptor.equals(at.getDescriptor())||call.ended||SCREEN_ADDING.get()!=call)throw new SecurityException("ACTUAL_SCREEN_PUBLICATION_CALL_REQUIRED");return call;
    }
    public static boolean screenWriting(Object list,Object widget,Object token){
        var at=boundary();ScreenCall call=screenCall(token,at);if(widget!=call.widget)throw new SecurityException("ACTUAL_SCREEN_PUBLICATION_VALUE_REQUIRED");
        if(call.registry!=null&&list!=call.registry.children.get()&&list!=call.registry.renderables.get()&&list!=call.registry.narratables.get()){
            call.registry.pending="CLIENT_SCREEN_ACTUAL_LISTS_CHANGED";call.unknown=true;call.scope.sourcesUnknown=true;
        }
        return !stopped(union(call.modules,origins(widget)))&&!ControlBridge.stoppedEffects();
    }
    public static void screenAdded(Object token){
        var at=boundary();ScreenCall call=screenCall(token,at);
        try{if(call.registry!=null&&call.before!=null){
            Module[] actual=ControlBridge.objectSources(call.widget);call.modules=union(call.modules,origins(call.widget));
            call.unknown|=ControlBridge.objectSourceUnknown(call.widget)||toastNullSource(actual);int[] after=widgetCounts(call.registry,call.widget);
            ScreenCall outer=call.previous;while(outer!=null&&(outer.registry!=call.registry||outer.widget!=call.widget))outer=outer.previous;
            synchronized(SOURCES){
                if(outer!=null){outer.modules=union(outer.modules,call.modules);outer.unknown|=call.unknown;outer.scope.sources=union(outer.scope.sources,call.modules);outer.scope.sourcesUnknown|=call.unknown;overlayProtect(outer.modules,outer.scope.sources);}
                else{
                    WidgetSlot slot=call.registry.widgets.get(new Key(call.widget,false));
                    // A newly observed registration after every old row/root is gone begins its own association.
                    if(slot!=null&&!call.rootedBefore&&Arrays.stream(call.before).noneMatch(count->count!=0)&&Arrays.stream(slot.counts).noneMatch(count->count!=0))slot=null;
                    if(slot==null){slot=new WidgetSlot(call.widget,call.modules,call.unknown);call.registry.widgets.put(new Key(call.widget,true),slot);}
                    else{slot.modules=union(slot.modules,call.modules);slot.unknown|=call.unknown;overlayProtect((Object)slot.modules);}
                    // A preexisting, unobserved occurrence cannot be assigned to this publication's actor.
                    if(!Arrays.equals(slot.counts,call.before))slot.unknown=true;
                    for(int index=0;index<after.length;index++){if(after[index]<call.before[index])slot.unknown=true;slot.counts[index]=after[index];}
                    if(Arrays.stream(after).anyMatch(count->count!=0))slot.retired=false;
                }
            }
        }}catch(ReflectiveOperationException|RuntimeException unavailable){if(call.registry!=null)call.registry.pending="CLIENT_SCREEN_PUBLICATION_CAPTURE_PENDING";uiFailure(call.owner,call.modules,unavailable);}
        finally{
            call.ended=true;synchronized(SOURCES){if(call.registry!=null)call.registry.publications--;}
            if(call.previous==null)SCREEN_ADDING.remove();else SCREEN_ADDING.set(call.previous);exit(call.scope);
        }
    }
    public static void screenWidgetsRemoved(Object owner){
        var at=boundary();if(!clientMember(at,SCREEN,"removeWidget","m_169411_","("+WIDGET+")V")&&!clientMember(at,SCREEN,"clearWidgets","m_169413_","()V"))throw new SecurityException("ACTUAL_SCREEN_REMOVAL_REQUIRED");
        ScreenRegistry registry;synchronized(SOURCES){registry=SCREEN_WIDGETS.get(new Key(owner,false));}if(registry==null)return;
        try{synchronized(SOURCES){for(WidgetSlot slot:registry.widgets.values()){
            Object widget=slot.widget.get();if(widget==null)continue;int[] after=widgetCounts(registry,widget);
            for(int index=0;index<after.length;index++){if(after[index]>slot.counts[index])slot.unknown=true;slot.counts[index]=after[index];}
        }}}catch(ReflectiveOperationException|RuntimeException unavailable){registry.pending="CLIENT_SCREEN_REMOVAL_CAPTURE_PENDING";}
    }
    private static ScreenRegistry currentScreen(){if(CURRENT==null)return null;for(Scope scope=CURRENT.get();scope!=null;scope=scope.parent)if(!scope.ended&&scope.screen!=null)return scope.screen;return null;}
    private static WidgetSlot currentWidget(Object widget){
        if(widget==null)return null;ScreenRegistry registry=currentScreen();if(registry==null||registry.owner.get()==widget)return null;
        synchronized(SOURCES){WidgetSlot slot=registry.widgets.get(new Key(widget,false));if(slot==null)return null;
            try{
                int[] rows=widgetCounts(registry,widget);Object owner=registry.owner.get();boolean rooted=registry.fields.focused.get(owner)==widget||registry.fields.lastNarratable.get(owner)==widget;
                if(Arrays.stream(rows).noneMatch(count->count!=0)&&!rooted)return null;
                if(!Arrays.equals(rows,slot.counts)||slot.retired){slot.unknown=true;slot.retired=false;slot.pending="CLIENT_SCREEN_UNOBSERVED_WIDGET_ROWS";}return slot;
            }
            catch(ReflectiveOperationException|RuntimeException unavailable){registry.pending="CLIENT_SCREEN_ACTUAL_LISTS_CHANGED";return null;}
        }
    }
    private static Module[] widgetSources(WidgetSlot slot){
        Object widget=slot.widget.get();Module[] actual=ControlBridge.objectSources(widget);
        synchronized(SOURCES){slot.unknown|=widget==null||ControlBridge.objectSourceUnknown(widget)||toastNullSource(actual);slot.modules=union(slot.modules,origins(widget));overlayProtect((Object)slot.modules);return slot.modules;}
    }
    public static Object screenEntering(Object owner){
        var at=boundary();if(!at.getDeclaringClass().getName().equals(SCREEN)||owner==null||actualBase(owner,SCREEN)!=at.getDeclaringClass())throw new SecurityException("ACTUAL_SCREEN_EXECUTION_REQUIRED");return enter(owner);
    }
    public static Object containerEntering(Object owner){
        var at=boundary();if(!clientMember(at,CONTAINER,"setFocused","m_7522_","("+WIDGET+")V")||owner==null||actualBase(owner,CONTAINER)!=at.getDeclaringClass())throw new SecurityException("ACTUAL_CONTAINER_FOCUS_REQUIRED");return enter(owner);
    }
    public static boolean widgetReferenceWriting(Object owner,Object widget){
        var at=boundary();if(!clientMember(at,CONTAINER,"setFocused","m_7522_","("+WIDGET+")V")
                &&!clientMember(at,SCREEN,"updateNarratedWidget","m_142227_","(Lnet/minecraft/client/gui/narration/NarrationElementOutput;)V"))throw new SecurityException("ACTUAL_WIDGET_REFERENCE_WRITE_REQUIRED");
        if(owner==null||actualBase(owner,at.getDeclaringClass().getName())!=at.getDeclaringClass())throw new SecurityException("ACTUAL_WIDGET_REFERENCE_OWNER_REQUIRED");
        WidgetSlot slot=currentWidget(widget);if(slot!=null){Module[] modules=widgetSources(slot);if(slot.retired||!slot.unknown&&exclusive(modules))return false;}
        return !stopped(sources(widget))&&!ControlBridge.stoppedEffects();
    }
    public static void widgetReferenceWritten(Object owner,Object widget){
        var at=boundary();boolean focus=clientMember(at,CONTAINER,"setFocused","m_7522_","("+WIDGET+")V");
        if(!focus&&!clientMember(at,SCREEN,"updateNarratedWidget","m_142227_","(Lnet/minecraft/client/gui/narration/NarrationElementOutput;)V"))throw new SecurityException("ACTUAL_WIDGET_REFERENCE_TAIL_REQUIRED");
        if(owner==null||actualBase(owner,at.getDeclaringClass().getName())!=at.getDeclaringClass())throw new SecurityException("ACTUAL_WIDGET_REFERENCE_OWNER_REQUIRED");
        if(widget==null)return;ScreenRegistry registry=currentScreen();if(registry==null||registry.owner.get()!=owner)return;
        Module[] actual=ControlBridge.objectSources(widget),invoking=ControlBridge.invokingSources(),modules=union(origins(widget),invoking,currentSources());
        boolean unknown=ControlBridge.objectSourceUnknown(widget)||ControlBridge.sourceUnknown()||toastNullSource(actual)||toastNullSource(invoking);
        try{synchronized(SOURCES){
            if((focus?registry.fields.focused:registry.fields.lastNarratable).get(owner)!=widget){registry.pending="CLIENT_SCREEN_WIDGET_REFERENCE_CHANGED";return;}
            int[] rows=widgetCounts(registry,widget);WidgetSlot slot=registry.widgets.get(new Key(widget,false));
            if(slot==null||slot.retired&&Arrays.stream(rows).noneMatch(count->count!=0)){slot=new WidgetSlot(widget,modules,unknown);registry.widgets.put(new Key(widget,true),slot);}
            else{slot.modules=union(slot.modules,modules);slot.unknown|=unknown;overlayProtect((Object)slot.modules);}
            if(!Arrays.equals(rows,slot.counts))slot.unknown=true;slot.retired=false;
        }}catch(ReflectiveOperationException|RuntimeException unavailable){registry.pending="CLIENT_SCREEN_WIDGET_REFERENCE_CAPTURE_PENDING";uiFailure(owner,modules,unavailable);}
    }
    private static boolean widgetRetirable(ScreenRegistry registry,WidgetSlot slot)throws ReflectiveOperationException{
        Object widget=slot.widget.get();if(widget==null)return false;widgetSources(slot);
        if(!Arrays.equals(widgetCounts(registry,widget),slot.counts)){slot.unknown=true;slot.pending="CLIENT_SCREEN_UNOBSERVED_WIDGET_ROWS";}
        return !slot.unknown&&exclusive(slot.modules);
    }
    private static boolean screenActive(ScreenRegistry registry){for(Scope scope:ACTIVE)if(!scope.ended&&scope.screen==registry)return true;return false;}
    private static void removeWidgetIdentity(List<?> list,Object widget){for(int index=list.size()-1;index>=0;index--)if(list.get(index)==widget)list.remove(index);}
    private static void retireWidgets(Object client){
        List<ScreenRegistry> registries;synchronized(SOURCES){if(REQUESTED.isEmpty())return;registries=new ArrayList<>(SCREEN_WIDGETS.values());}
        for(ScreenRegistry registry:registries){WidgetRetirement prior=WIDGET_RETIRING.get();
            try{synchronized(SOURCES){
                Object owner=registry.owner.get();ScreenFields fields=registry.fields;if(owner==null||fields.minecraft.get(owner)!=client)continue;
                if(fields.ui.instance.get(null)!=client||fields.ui.thread.get(client)!=Thread.currentThread()||!screenLists(registry)){registry.pending="CLIENT_SCREEN_ACTUAL_OWNER_OR_LISTS_CHANGED";continue;}
                if(registry.publications!=0||screenActive(registry))continue;Object current=fields.ui.screen.get(client);registry.pending="";
                for(WidgetSlot slot:registry.widgets.values()){
                    Object widget=slot.widget.get();if(widget==null||slot.retired||!widgetRetirable(registry,slot))continue;
                    if(fields.ui.screen.get(client)!=current||fields.minecraft.get(owner)!=client||!screenLists(registry)){registry.pending="CLIENT_SCREEN_CURRENT_OWNER_CHANGED";break;}
                    WidgetRetirement retiring=new WidgetRetirement(registry,slot,owner,widget);WIDGET_RETIRING.set(retiring);
                    try{
                        // These are the original ArrayLists; remove by identity, without invoking a widget's equals or lifecycle.
                        removeWidgetIdentity(registry.renderables.get(),widget);removeWidgetIdentity(registry.narratables.get(),widget);removeWidgetIdentity(registry.children.get(),widget);
                        if(fields.focused.get(owner)==widget){fields.focused.set(owner,null);fields.dragging.setBoolean(owner,false);}
                        if(fields.lastNarratable.get(owner)==widget)fields.lastNarratable.set(owner,null);
                        int[] after=widgetCounts(registry,widget);System.arraycopy(after,0,slot.counts,0,after.length);
                        if(Arrays.stream(after).anyMatch(count->count!=0)||fields.focused.get(owner)==widget||fields.lastNarratable.get(owner)==widget){slot.pending="CLIENT_SCREEN_WIDGET_REFERENCES_REMAIN";continue;}
                        slot.retired=true;slot.pending="";FAILURES.remove(new Key(widget,false));retired++;
                    }finally{
                        // Keep a partially completed removal at its actual remaining rows for the next frame.
                        try{int[] remaining=widgetCounts(registry,widget);for(int index=0;index<remaining.length;index++){if(remaining[index]>slot.counts[index])slot.unknown=true;slot.counts[index]=remaining[index];}}
                        catch(ReflectiveOperationException|RuntimeException unavailable){slot.pending="CLIENT_SCREEN_WIDGET_REMAINING_ROWS_UNREADABLE";}
                        retiring.ended=true;if(prior==null)WIDGET_RETIRING.remove();else WIDGET_RETIRING.set(prior);
                    }
                }
            }}catch(ReflectiveOperationException|RuntimeException unavailable){registry.pending="CLIENT_SCREEN_WIDGET_RETIREMENT_PENDING:"+unavailable.getClass().getSimpleName();}
            finally{if(prior==null)WIDGET_RETIRING.remove();else WIDGET_RETIRING.set(prior);}
        }
    }
    private static boolean widgetCleanupField(Object receiver,Class<?> declaring,String name){
        WidgetRetirement token=WIDGET_RETIRING.get();if(token==null||token.ended||token.thread!=Thread.currentThread()||token.owner!=receiver)return false;
        ScreenFields fields=token.registry.fields;
        return declaring==fields.container&&(name.equals(fields.focused.getName())||name.equals(fields.dragging.getName()))
                ||declaring==fields.type&&name.equals(fields.lastNarratable.getName());
    }
    private static boolean toastNullSource(Module[] sources){for(Module source:sources)if(source==null)return true;return false;}
    private static Module[] toastSources(ToastSlot slot){
        Object toast=slot.toast.get();Module[] actual=ControlBridge.objectSources(toast);
        slot.unknown|=toast==null||ControlBridge.objectSourceUnknown(toast)||toastNullSource(actual);
        slot.modules=union(slot.modules,actual);overlayProtect((Object)slot.modules);return slot.modules;
    }
    private static ToastRegistry toastRegistry(Object owner,Class<?> type)throws ReflectiveOperationException{
        if(owner==null||owner.getClass()!=type||!type.getName().equals(TOAST_COMPONENT)||!role(type))throw new SecurityException("ACTUAL_TOAST_COMPONENT_REQUIRED");
        synchronized(SOURCES){ToastRegistry prior=TOASTS.get(new Key(owner,false));if(prior!=null)return prior;}
        ToastFields fields=TOAST_FIELDS.get(type);Object client=fields.minecraft.get(owner),queued=fields.queued.get(owner),visible=fields.visible.get(owner),occupied=fields.occupied.get(owner);
        if(client==null||client.getClass()!=fields.ui.type||queued==null||queued.getClass()!=ArrayDeque.class||visible==null||visible.getClass()!=ArrayList.class
                ||occupied==null||occupied.getClass()!=BitSet.class)throw new IllegalStateException("CLIENT_TOAST_ACTUAL_CONTAINERS_REQUIRED");
        ToastRegistry registry=new ToastRegistry(fields,owner,client,(Deque<?>)queued,(List<?>)visible,(BitSet)occupied);
        synchronized(SOURCES){ToastRegistry prior=TOASTS.get(new Key(owner,false));if(prior!=null)return prior;TOASTS.put(new Key(owner,true),registry);FAILURES.remove(new Key(owner,false));}return registry;
    }
    private static boolean toastActual(ToastRegistry registry,boolean ownerThread)throws ReflectiveOperationException{
        Object owner=registry.owner.get(),client=registry.client.get();ToastFields fields=registry.fields;
        if(owner==null||client==null||fields.minecraft.get(owner)!=client||fields.ui.instance.get(null)!=client||fields.minecraftToasts.get(client)!=owner){registry.pending="CLIENT_TOAST_ACTUAL_OWNER_CHANGED";return false;}
        if(fields.queued.get(owner)!=registry.queued.get()||fields.visible.get(owner)!=registry.visible.get()||fields.occupied.get(owner)!=registry.occupied.get()){
            registry.pending="CLIENT_TOAST_ACTUAL_CONTAINERS_CHANGED";return false;
        }
        if(ownerThread&&fields.ui.thread.get(client)!=Thread.currentThread()){registry.pending="CLIENT_TOAST_OWNER_THREAD_PENDING";return false;}return true;
    }
    private static ToastSlot toastSlot(ToastRegistry registry,Object toast,Module[] modules,boolean unknown){
        Key lookup=new Key(toast,false);ToastSlot slot=registry.slots.get(lookup);
        if(slot==null){slot=new ToastSlot(toast,modules,unknown);registry.slots.put(new Key(toast,true),slot);}
        else{slot.modules=union(slot.modules,modules);slot.unknown|=unknown;overlayProtect((Object)slot.modules);}return slot;
    }
    private static ToastCall toastCall(ToastRegistry registry,Object owner,Object toast,Module[] modules,boolean unknown,boolean frame,StackWalker.StackFrame at){return toastCall(registry,owner,toast,modules,unknown,frame,null,at);}
    private static ToastCall toastCall(ToastRegistry registry,Object owner,Object toast,Module[] modules,boolean unknown,boolean frame,Object instance,StackWalker.StackFrame at){
        Scope scope=null;
        if(!frame){scope=new Scope(CURRENT.get(),union(modules,currentSources()),at,null,toast,instance,unknown);synchronized(SOURCES){ACTIVE.add(scope);overlayProtect(scope,scope.sources);}CURRENT.set(scope);}
        else if(registry!=null)synchronized(SOURCES){registry.frames++;}
        return new ToastCall(registry,owner,toast,modules,unknown,frame,scope,at);
    }
    private static ToastCall actualToastCall(Object token,StackWalker.StackFrame at){
        if(!(token instanceof ToastCall call)||call.thread!=Thread.currentThread()||call.type!=at.getDeclaringClass()||!call.method.equals(at.getMethodName())||!call.descriptor.equals(at.getDescriptor()))throw new SecurityException("ACTUAL_TOAST_INVOCATION_REQUIRED");return call;
    }
    public static Object toastAdding(Object owner,Object toast){
        var at=boundary();if(!clientMember(at,TOAST_COMPONENT,"addToast","m_94922_","("+TOAST+")V"))throw new SecurityException("ACTUAL_TOAST_PUBLICATION_REQUIRED");
        Module[] actual=ControlBridge.objectSources(toast),invoking=ControlBridge.invokingSources(),modules=union(actual,invoking,currentSources());
        boolean unknown=ControlBridge.objectSourceUnknown(toast)||ControlBridge.sourceUnknown()||toastNullSource(actual)||toastNullSource(invoking);
        if(stopped(modules)||ControlBridge.stoppedEffects())return Boolean.FALSE;ToastRegistry registry=null;
        try{registry=toastRegistry(owner,at.getDeclaringClass());}catch(ReflectiveOperationException|IllegalStateException unavailable){uiFailure(owner,modules,unavailable);}
        return toastCall(registry,owner,toast,modules,unknown,false,at);
    }
    public static boolean toastWriting(Object queued,Object toast,Object token){
        var at=boundary();ToastCall call=actualToastCall(token,at);
        if(call.ended||toast!=call.toast||!clientMember(at,TOAST_COMPONENT,"addToast","m_94922_","("+TOAST+")V"))throw new SecurityException("ACTUAL_TOAST_QUEUE_WRITE_REQUIRED");
        if(call.registry!=null&&queued!=call.registry.queued.get()){call.registry.pending="CLIENT_TOAST_ACTUAL_CONTAINERS_CHANGED";return false;}
        return !stopped(union(call.modules,ControlBridge.objectSources(toast)))&&!ControlBridge.stoppedEffects();
    }
    public static void toastAdded(Object queued,Object toast,Object token,boolean published){
        var at=boundary();ToastCall call=actualToastCall(token,at);if(call.ended||toast!=call.toast)throw new SecurityException("ACTUAL_TOAST_PUBLICATION_TAIL_REQUIRED");
        if(!published)return;Module[] actual=ControlBridge.objectSources(toast),modules=union(call.modules,actual);record(toast,modules);
        if(call.registry==null)return;
        synchronized(SOURCES){
            if(queued!=call.registry.queued.get()){call.registry.pending="CLIENT_TOAST_ACTUAL_CONTAINERS_CHANGED";return;}
            ToastSlot slot=toastSlot(call.registry,toast,modules,call.unknown||ControlBridge.objectSourceUnknown(toast)||toastNullSource(actual));slot.queued=true;
        }
    }
    private static boolean toastActive(Object toast){for(Scope scope:ACTIVE)if(!scope.ended&&scope.toast==toast)return true;return false;}
    private static boolean toastRetirable(ToastSlot slot){return exclusive(toastSources(slot))&&!slot.unknown&&!toastActive(slot.toast.get());}
    public static Object toastFrameEntering(Object owner){
        var at=boundary();if(!clientMember(at,TOAST_COMPONENT,"render","m_94920_","(Lnet/minecraft/client/gui/GuiGraphics;)V"))throw new SecurityException("ACTUAL_TOAST_FRAME_REQUIRED");ToastRegistry registry=null;
        try{registry=toastRegistry(owner,at.getDeclaringClass());synchronized(SOURCES){if(registry.frames==0)toastMaintain(registry,true);}}
        catch(ReflectiveOperationException|IllegalStateException|ConcurrentModificationException unavailable){uiFailure(owner,new Module[0],unavailable);}
        return toastCall(registry,owner,null,new Module[0],false,true,at);
    }
    public static Object toastQueued(Object owner,Object toast){
        var at=boundary();if(!clientMember(at,TOAST_COMPONENT,"lambda$render$1","m_243085_","("+TOAST+")Z"))throw new SecurityException("ACTUAL_TOAST_PROMOTION_REQUIRED");
        ToastRegistry registry=null;Module[] modules=ControlBridge.objectSources(toast);boolean unknown=ControlBridge.objectSourceUnknown(toast)||toastNullSource(modules);
        try{registry=toastRegistry(owner,at.getDeclaringClass());synchronized(SOURCES){
            ToastSlot slot=toastSlot(registry,toast,modules,unknown);modules=toastSources(slot);unknown=slot.unknown;
            // TRUE removes just this queued entry through the original Deque.removeIf.
            if(toastActual(registry,true)&&toastContains(registry.queued.get(),toast)){slot.queued=true;if(toastRetirable(slot))return Boolean.FALSE;}
        }}catch(ReflectiveOperationException|IllegalStateException unavailable){uiFailure(owner,modules,unavailable);}
        return toastCall(registry,owner,toast,modules,unknown,false,at);
    }
    public static Object toastDrawing(Object instance){
        var at=boundary();if(!clientMember(at,TOAST_INSTANCE,"render","m_280442_","(ILnet/minecraft/client/gui/GuiGraphics;)Z")||instance==null||instance.getClass()!=at.getDeclaringClass())throw new SecurityException("ACTUAL_TOAST_INSTANCE_DRAW_REQUIRED");
        ToastRegistry registry=null;Object owner=null,toast=null;Module[] modules=new Module[0];boolean unknown=false;
        try{
            Class<?> component=at.getDeclaringClass().getEnclosingClass();ToastFields fields=TOAST_FIELDS.get(component);owner=fields.instanceOwner.get(instance);toast=fields.toast.get(instance);
            registry=toastRegistry(owner,component);synchronized(SOURCES){
                ToastSlot slot=toastSlot(registry,toast,ControlBridge.objectSources(toast),ControlBridge.objectSourceUnknown(toast));modules=toastSources(slot);unknown=slot.unknown;
                // Only a current, nonoverlapping instance can ask the original owner to release its slots.
                if(toastActual(registry,true)&&toastContains(registry.visible.get(),instance)){slot.visible=true;if(toastRetirable(slot)&&toastRanges(registry))return Boolean.FALSE;}
            }
        }catch(ReflectiveOperationException|IllegalStateException unavailable){uiFailure(instance,modules,unavailable);}
        return toastCall(registry,owner,toast,modules,unknown,false,instance,at);
    }
    /** A stopped execution may skip a shared Toast's callback without deleting its other owners' instance. */
    public static Object toastRenderSkipped(Object toast){
        var at=boundary();Scope scope=CURRENT.get();
        if(!clientMember(at,TOAST_INSTANCE,"render","m_280442_","(ILnet/minecraft/client/gui/GuiGraphics;)Z")||scope==null||scope.type!=at.getDeclaringClass()
                ||!scope.method.equals(at.getMethodName())||scope.toast!=toast||scope.toastInstance==null||scope.ended)throw new SecurityException("ACTUAL_TOAST_CALLBACK_RESULT_REQUIRED");
        try{
            ToastFields fields=TOAST_FIELDS.get(at.getDeclaringClass().getEnclosingClass());Object instance=scope.toastInstance;
            if(fields.toast.get(instance)!=toast)throw new IllegalStateException("CLIENT_TOAST_INSTANCE_CHANGED");
            ToastRegistry registry=toastRegistry(fields.instanceOwner.get(instance),fields.type);
            synchronized(SOURCES){ToastSlot slot=registry.slots.get(new Key(toast,false));if(slot!=null){
                Module[] modules=toastSources(slot);
                if(!slot.unknown&&exclusive(modules)&&toastActual(registry,true)&&toastContains(registry.visible.get(),instance)&&toastRanges(registry))return fields.hidden.get(null);
            }}
            return fields.visibility.get(instance);
        }catch(ReflectiveOperationException unavailable){throw new IllegalStateException("CLIENT_TOAST_CALLBACK_RESULT_UNAVAILABLE",unavailable);}
    }
    public static void toastExiting(Object token){
        var at=boundary();ToastCall call=actualToastCall(token,at);if(call.ended)return;
        if(call.scope!=null)exit(call.scope);call.ended=true;
        ToastRegistry registry=call.registry;if(registry==null)return;
        synchronized(SOURCES){
            if(call.frame)registry.frames--;
            if(registry.frames==0)try{toastMaintain(registry,true);}catch(ReflectiveOperationException|IllegalStateException|ConcurrentModificationException unavailable){registry.pending="CLIENT_TOAST_RETIREMENT_PENDING:"+unavailable.getClass().getSimpleName();}
        }
    }
    public static void toastCleared(Object owner){
        var at=boundary();if(!clientMember(at,TOAST_COMPONENT,"clear","m_94919_","()V"))throw new SecurityException("ACTUAL_TOAST_CLEAR_REQUIRED");
        synchronized(SOURCES){ToastRegistry registry=TOASTS.get(new Key(owner,false));if(registry==null||registry.frames!=0)return;
            try{toastMaintain(registry,false);}catch(ReflectiveOperationException|IllegalStateException|ConcurrentModificationException unavailable){registry.pending="CLIENT_TOAST_CLEAR_READBACK_PENDING";}
        }
    }
    private static boolean toastContains(Collection<?> values,Object value){if(values==null)return false;for(Object actual:values)if(actual==value)return true;return false;}
    private static boolean toastRanges(ToastRegistry registry)throws ReflectiveOperationException{
        BitSet actual=registry.occupied.get(),expected=new BitSet(5);List<?> visible=registry.visible.get();if(actual==null||visible==null)return false;
        for(Object instance:visible){if(instance==null)continue;ToastFields fields=registry.fields;
            if(instance.getClass()!=fields.instanceType||fields.instanceOwner.get(instance)!=registry.owner.get()){registry.pending="CLIENT_TOAST_INSTANCE_OWNER_CHANGED";return false;}
            int index=fields.index.getInt(instance),count=fields.count.getInt(instance);
            if(index<0||count<=0||index>5-count||expected.nextSetBit(index)>=0&&expected.nextSetBit(index)<index+count){registry.pending="CLIENT_TOAST_OCCUPIED_RANGE_CHANGED";return false;}
            expected.set(index,index+count);
        }
        if(!expected.equals(actual)){registry.pending="CLIENT_TOAST_OCCUPIED_RANGE_CHANGED";return false;}return true;
    }
    /** Called only outside the actual framework iteration on its real Minecraft owner thread. */
    private static void toastMaintain(ToastRegistry registry,boolean retire)throws ReflectiveOperationException{
        if(registry.frames!=0||!toastActual(registry,true))return;Deque<?> queued=registry.queued.get();List<?> visible=registry.visible.get();BitSet occupied=registry.occupied.get();
        for(Object toast:queued){ToastSlot slot=toastSlot(registry,toast,ControlBridge.objectSources(toast),ControlBridge.objectSourceUnknown(toast));slot.queued=true;}
        for(Object instance:visible){if(instance==null)continue;
            if(instance.getClass()!=registry.fields.instanceType||registry.fields.instanceOwner.get(instance)!=registry.owner.get()){registry.pending="CLIENT_TOAST_INSTANCE_OWNER_CHANGED";return;}
            Object toast=registry.fields.toast.get(instance);ToastSlot slot=toastSlot(registry,toast,ControlBridge.objectSources(toast),ControlBridge.objectSourceUnknown(toast));slot.visible=true;
        }
        if(retire){
            for(var iterator=queued.iterator();iterator.hasNext();){Object toast=iterator.next();ToastSlot slot=registry.slots.get(new Key(toast,false));if(toastRetirable(slot)){iterator.remove();slot.queued=false;retired++;}}
            if(toastRanges(registry))for(var iterator=visible.iterator();iterator.hasNext();){Object instance=iterator.next();if(instance==null)continue;Object toast=registry.fields.toast.get(instance);
                ToastSlot slot=registry.slots.get(new Key(toast,false));if(!toastRetirable(slot))continue;
                int index=registry.fields.index.getInt(instance),count=registry.fields.count.getInt(instance);iterator.remove();occupied.clear(index,index+count);retired++;
            }
        }
        // Completion follows current backing rows, including naturally hidden/cleared Toasts.
        Set<Object> currentQueued=Collections.newSetFromMap(new IdentityHashMap<>()),currentVisible=Collections.newSetFromMap(new IdentityHashMap<>());
        for(Object toast:queued)currentQueued.add(toast);
        for(Object instance:visible)if(instance!=null)currentVisible.add(registry.fields.toast.get(instance));
        for(ToastSlot slot:registry.slots.values()){slot.queued=currentQueued.contains(slot.toast.get());slot.visible=currentVisible.contains(slot.toast.get());}
        registry.slots.entrySet().removeIf(entry->{ToastSlot slot=entry.getValue();return !slot.queued&&!slot.visible&&!toastActive(slot.toast.get());});
        if(toastRanges(registry))registry.pending="";
    }
    private static void retireToasts(Object client){
        synchronized(SOURCES){for(ToastRegistry registry:TOASTS.values())if(registry.client.get()==client&&registry.frames==0)
            try{toastMaintain(registry,true);}catch(ReflectiveOperationException|IllegalStateException|ConcurrentModificationException unavailable){registry.pending="CLIENT_TOAST_RETIREMENT_PENDING:"+unavailable.getClass().getSimpleName();}}
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
            Module[] actual=ControlBridge.objectSources(callback),invoking=ControlBridge.invokingSources(),modules=union(invoking,currentSources(),origins(callback));
            boolean unknown=ControlBridge.objectSourceUnknown(callback)||ControlBridge.sourceUnknown()||toastNullSource(actual)||toastNullSource(invoking);
            OverlayRegistration slot=new OverlayRegistration(new Ref<>(callback),modules,unknown);overlayProtect(slot,slot.callback(),modules);
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
                Module[] actual=ControlBridge.objectSources(callback),namedSources=ControlBridge.objectSources(value);
                Module[] modules=union(observed==null?new Module[0]:observed.modules(),origins(callback),origins(value));
                boolean unknown=observed==null||observed.unknown()||ControlBridge.objectSourceUnknown(callback)||ControlBridge.objectSourceUnknown(value)
                        ||ControlBridge.sourceUnknown()||toastNullSource(actual)||toastNullSource(namedSources);
                slots.add(new OverlaySlot(init.fields,init.generation,key,value,callback,modules,observed!=null,unknown));
            }
            init.fields.gate.lock();try{synchronized(SOURCES){
                if(init.fields.generation!=init.generation||init.fields.list.get(null)!=list||init.fields.map.get(null)!=map)throw new IllegalStateException("CLIENT_OVERLAY_PUBLICATION_GENERATION_CHANGED");
                OverlayRegistry registry=new OverlayRegistry(init.fields,init.generation,list,map,slots);OVERLAY_REGISTRIES.put(new Key(manager,true),registry);
                for(OverlaySlot slot:slots)OVERLAY_NAMES.put(new Key(slot.named.get(),true),slot);init.published=true;init.fields.pending="";
            }}finally{init.fields.gate.unlock();}
        }catch(ReflectiveOperationException|RuntimeException unavailable){synchronized(SOURCES){init.fields.pending="CLIENT_OVERLAY_PUBLICATION_CAPTURE_FAILED";}throw new IllegalStateException("CLIENT_OVERLAY_PUBLICATION_CAPTURE_FAILED",unavailable);}
    }
    private static Module[] overlaySources(OverlaySlot slot){
        Object callback=slot.callback.get(),named=slot.named.get();Module[] actual=ControlBridge.objectSources(callback),namedSources=ControlBridge.objectSources(named);
        synchronized(SOURCES){
            slot.unknown|=callback==null||named==null||ControlBridge.objectSourceUnknown(callback)||ControlBridge.objectSourceUnknown(named)
                    ||toastNullSource(actual)||toastNullSource(namedSources);
            slot.modules=union(slot.modules,origins(callback),origins(named));overlayProtect((Object)slot.modules);return slot.modules;
        }
    }
    private static boolean overlayMatches(OverlaySlot slot,Object named)throws ReflectiveOperationException{
        Object callback=slot.callback.get();return named!=null&&named==slot.named.get()&&named.getClass()==slot.fields.named&&slot.fields.id.get(named)==slot.key&&callback!=null&&slot.fields.callback.get(named)==callback;
    }
    public static Object overlayEntering(Object named){
        var frame=boundary();overlayBoundary(frame,"net.minecraftforge.client.gui.overlay.ForgeGui","lambda$render$0",OVERLAY_RENDER);OverlaySlot slot;
        synchronized(SOURCES){slot=OVERLAY_NAMES.get(new Key(named,false));}
        Module[] modules=new Module[0];boolean unknown=ControlBridge.sourceUnknown();
        if(slot!=null)try{
            if(!overlayMatches(slot,named)){synchronized(SOURCES){slot.fields.pending="CLIENT_OVERLAY_NAMED_ASSOCIATION_CHANGED";}slot=null;}
            else{modules=overlaySources(slot);unknown|=slot.unknown;if(slot.retired||slot.observed&&!slot.unknown&&exclusive(modules))return Boolean.FALSE;}
        }catch(ReflectiveOperationException unavailable){throw new IllegalStateException("CLIENT_OVERLAY_CALLBACK_CAPTURE_FAILED",unavailable);}
        unknown|=slot==null;
        Scope scope=new Scope(CURRENT.get(),union(modules,currentSources()),frame,slot,null,null,unknown);synchronized(SOURCES){ACTIVE.add(scope);overlayProtect(scope,scope.sources);}CURRENT.set(scope);return scope;
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
                    Module[] modules=overlaySources(slot);
                    if(slot.observed&&!slot.unknown&&exclusive(modules)&&!overlayActive(slot))removed.add(slot);else{retained.add(named);names.put(slot.key,named);kept.add(slot);}}
                if(removed.isEmpty())continue;
                Object nextList=fields.listCopy.invoke(null,retained),nextMap=fields.mapCopy.invoke(null,names);
                retirement=new OverlayRetirement(registry,nextList,nextMap,kept,removed);
            }
            if(!fields.gate.tryLock())continue;
            try{synchronized(SOURCES){
                if(fields.initializing!=0||fields.generation!=registry.generation||OVERLAY_REGISTRIES.get(new Key(fields.type,false))!=registry)continue;
                for(OverlaySlot slot:retirement.removed)if(!exclusive(overlaySources(slot))||!slot.observed||slot.unknown||overlayActive(slot))throw new IllegalStateException("CLIENT_OVERLAY_RETIREMENT_SOURCES_CHANGED");
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
        long count=0;boolean unknown=false;
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
                count++;unknown|=slot.unknown;
            }
        }
        return unknown?-3:count;
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
    private static boolean presentationRetirable(Object owner,UiFields fields,boolean screen,Object target)throws ReflectiveOperationException{
        if(target==null||fields.instance.get(null)!=owner||fields.thread.get(owner)!=Thread.currentThread()
                ||fields.current(owner,screen)!=target||sourceUnknown(target))return false;
        synchronized(SOURCES){ContributionSources observed=SOURCES.get(new Key(target,false));return observed!=null&&!observed.unknown()&&exclusive(sources(target));}
    }
    private static void retirePresentation(Object owner,UiFields fields,boolean screen){
        Object target=null;Module[] modules=new Module[0];UiRetirement prior=UI_RETIRING.get();
        try{
            target=fields.current(owner,screen);
            synchronized(SOURCES){
                for(var at=UI_PENDING.entrySet().iterator();at.hasNext();){var entry=at.next();UiRetirement pending=entry.getValue();
                    if(pending.owner==owner&&pending.screen==screen&&pending.target!=target){FAILURES.remove(new Key(pending.target,false));at.remove();retired++;}}
            }
            if(target==null)return;modules=sources(target);
            if(!presentationRetirable(owner,fields,screen,target))return;
            UiRetirement retirement;
            synchronized(SOURCES){retirement=UI_PENDING.get(new Key(target,false));
                if(retirement==null){retirement=new UiRetirement(owner,target,screen);UI_PENDING.put(new Key(target,true),retirement);CodeSourceBridge.contributionControl(retirement);}
                if(retirement.owner!=owner||retirement.screen!=screen)return;
                // Once the actual original setter entered its callbacks, retry only its readback.
                if(retirement.call!=null)return;
            }
            // Re-read the actual slot before calling its original lifecycle; unknown and shared objects stay.
            if(!presentationRetirable(owner,fields,screen,target))return;
            UI_RETIRING.set(retirement);(screen?fields.setScreen:fields.setOverlay).invoke(owner,new Object[]{null});
            if(fields.current(owner,screen)==target){if(retirement.call!=null&&presentationRetirable(owner,fields,screen,target))uiFailure(target,modules,new IllegalStateException("CLIENT_UI_ROOT_RETAINED_AFTER_SETTER"));}
            else synchronized(SOURCES){UI_PENDING.remove(new Key(target,false));FAILURES.remove(new Key(target,false));FAILURES.remove(new Key(owner,false));retired++;}
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
        var frame=boundary();ScreenRegistry registry=currentScreen();WidgetSlot widget=currentWidget(contribution);
        if(contribution!=null){Class<?> screen=actualBase(contribution,SCREEN);if(screen!=null)try{registry=screenRegistry(contribution,screen);widget=null;}
            catch(ReflectiveOperationException|IllegalStateException unavailable){uiFailure(contribution,sources(contribution),unavailable);}}
        Module[] owners=union(sources(contribution),widget==null?new Module[0]:widgetSources(widget),currentSources());
        boolean unknown=sourceUnknown(contribution)||ControlBridge.sourceUnknown()||widget!=null&&widget.unknown;
        Scope scope=new Scope(CURRENT.get(),owners,frame,null,null,null,unknown,registry,widget);
        synchronized(SOURCES){ACTIVE.add(scope);overlayProtect(scope,owners);}CURRENT.set(scope);return scope;
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
    static boolean sourceUnknown(){
        if(CURRENT==null)return false;for(Scope scope=CURRENT.get();scope!=null;scope=scope.parent)if(!scope.ended&&scope.sourcesUnknown)return true;return false;
    }
    static boolean stoppedSource(){return stopped(currentSources());}
    public static boolean particleAdmitted(Object particle){
        var frame=boundary();if(!clientMember(frame,"net.minecraft.client.particle.ParticleEngine","add","m_107344_","(Lnet/minecraft/client/particle/Particle;)V"))throw new SecurityException("ACTUAL_PARTICLE_ADMISSION_REQUIRED");
        record(particle,union(origins(particle),ControlBridge.invokingSources()));if(stopped(sources(particle))||ControlBridge.stoppedEffects()){removeParticle(particle);return false;}return true;
    }
    public static boolean particleTick(Object particle){var frame=boundary();if(!clientMember(frame,"net.minecraft.client.particle.ParticleEngine","tickParticle","m_107393_","(Lnet/minecraft/client/particle/Particle;)V"))throw new SecurityException("ACTUAL_PARTICLE_TICK_REQUIRED");if(stopped(sources(particle))){removeParticle(particle);return false;}return true;}
    public static void reloadListenerRegistered(Object manager,Object listener){
        var frame=boundary();if(!clientMember(frame,"net.minecraft.server.packs.resources.ReloadableResourceManager","registerReloadListener","m_7217_","(Lnet/minecraft/server/packs/resources/PreparableReloadListener;)V"))throw new SecurityException("ACTUAL_RELOAD_REGISTRATION_REQUIRED");
        record(listener,union(origins(listener),ControlBridge.invokingSources()));try{Collection<?> listeners=(Collection<?>)field(manager,frame.getDeclaringClass(),"listeners");synchronized(SOURCES){RELOAD_LISTENERS.put(new Key(manager,true),new Ref<>(listeners));}}
        catch(ReflectiveOperationException unavailable){throw new IllegalStateException("ACTUAL_RELOAD_LIST_UNAVAILABLE",unavailable);}
    }
    public static void reloadStarting(Object manager){
        var frame=boundary();if(!clientMember(frame,"net.minecraft.server.packs.resources.ReloadableResourceManager","createReload","m_142463_","(Ljava/util/concurrent/Executor;Ljava/util/concurrent/Executor;Ljava/util/concurrent/CompletableFuture;Ljava/util/List;)Lnet/minecraft/server/packs/resources/ReloadInstance;"))throw new SecurityException("ACTUAL_RELOAD_START_REQUIRED");
        try{Collection<?> listeners=(Collection<?>)field(manager,frame.getDeclaringClass(),"listeners");retireListeners(listeners);synchronized(SOURCES){RELOAD_LISTENERS.put(new Key(manager,true),new Ref<>(listeners));}}
        catch(ReflectiveOperationException unavailable){throw new IllegalStateException("ACTUAL_RELOAD_LIST_UNAVAILABLE",unavailable);}
    }
    public static java.util.concurrent.CompletableFuture<Void> skipReload(Object barrier){
        var frame=boundary();try{Class<?> api=Class.forName("net.minecraft.server.packs.resources.PreparableReloadListener$PreparationBarrier",false,frame.getDeclaringClass().getClassLoader());
            if(!role(api)||barrier==null||actualBase(barrier,"net.minecraft.server.packs.resources.SimpleReloadInstance$1")==null||!api.isInstance(barrier))throw new SecurityException("ACTUAL_RELOAD_BARRIER_REQUIRED");
            Object result=clientMethod(api,"wait","m_6769_","(Ljava/lang/Object;)Ljava/util/concurrent/CompletableFuture;",Object.class).invoke(barrier,new Object[]{null});
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
        for(Collection<?> list:lists)retireListeners(list);
    }
    private static void retireListeners(Collection<?> listeners){
        // These are the actual lists obtained from the framework owner. Their
        // ordinary JDK implementation needs no retired global container hook.
        // A custom collection may run arbitrary removal callbacks, so retain it.
        Class<?> type=listeners.getClass();
        if(type==ArrayList.class||type==LinkedList.class||type==java.util.concurrent.CopyOnWriteArrayList.class){
            listeners.removeIf(listener->!sourceUnknown(listener)&&exclusive(sources(listener)));return;
        }
        synchronized(SOURCES){
            if(!REQUESTED.isEmpty())FAILURES.put(new Key(listeners,true),union(FAILURES.get(new Key(listeners,false)),REQUESTED.toArray(Module[]::new)));
        }
    }
    public static void verticesBegin(Object builder){GPU.begin(builder,boundary());}
    public static Object verticesEntering(Object builder,Object bulk){return GPU.enter(builder,bulk,boundary());}
    public static void verticesCompleted(Object builder,Object token){GPU.complete(builder,token,boundary());}
    public static void verticesStored(Object builder,Object rendered){GPU.store(builder,rendered,boundary());}
    public static void verticesCapacityChanged(Object builder){GPU.capacityChanged(builder,boundary());}
    public static void verticesSortingChanged(Object builder){GPU.sortingChanged(builder,boundary());}
    public static void verticesReleased(Object rendered){GPU.released(rendered,boundary());}
    public static void verticesDiscarded(Object builder){GPU.discarded(builder,boundary());}
    public static void verticesSortState(Object builder,Object sorting){GPU.sortState(builder,sorting,boundary());}
    public static boolean verticesSortAllowed(Object builder,Object sorting){return GPU.sortAllowed(builder,sorting,boundary());}
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

    private static void clientPayloadWriter(Class<?> caller){
        if(!caller.getName().equals("dev.ronova.pro.ProRuntime")||!ControlBridge.coreWriter())throw new SecurityException("ACTUAL_CLIENT_PAYLOAD_CORE_REQUIRED");
        try{if(caller!=Class.forName("dev.ronova.pro.ProRuntime",false,caller.getClassLoader()))throw new SecurityException("ACTUAL_CLIENT_PAYLOAD_CORE_REQUIRED");}
        catch(ClassNotFoundException unavailable){throw new SecurityException("ACTUAL_CLIENT_PAYLOAD_CORE_REQUIRED",unavailable);}
    }
    public static boolean clientPayloadAllowed(Object proof,Object data,int offset,int length,boolean after){
        clientPayloadWriter(WALKER.getCallerClass());return GPU.payloadAllowed(proof,data,offset,length,after);
    }
    public static boolean clientSortPointAllowed(Object proof,Object point,boolean after){
        clientPayloadWriter(WALKER.getCallerClass());return GPU.sortPointAllowed(proof,point,after);
    }
    private static void clearClientPayload(Object proof,java.nio.ByteBuffer storage,int offset,int length,Class<?> game)throws ReflectiveOperationException{
        Class<?> core=Class.forName("dev.ronova.pro.ProRuntime",false,game.getClassLoader());Method method=core.getMethod("clearClientPayload",Object.class,java.nio.ByteBuffer.class,int.class,int.class);
        if(!Modifier.isStatic(method.getModifiers())||method.getReturnType()!=void.class)throw new SecurityException("ACTUAL_CLIENT_PAYLOAD_WRITER_REQUIRED");method.invoke(null,proof,storage,offset,length);
    }
    private static void clearClientSortPoint(Object proof,Object point,Class<?> game)throws ReflectiveOperationException{
        Class<?> core=Class.forName("dev.ronova.pro.ProRuntime",false,game.getClassLoader());Method method=core.getMethod("clearClientSortPoint",Object.class,Object.class);
        if(!Modifier.isStatic(method.getModifiers())||method.getReturnType()!=void.class)throw new SecurityException("ACTUAL_CLIENT_SORT_POINT_WRITER_REQUIRED");method.invoke(null,proof,point);
    }

    /** Original CPU vertex/index identities and a fragment mask preserve the original draw and primitive IDs. */
    private static final class GpuImages {
        private static final Class<?> I=int.class;
        private static final class Build {
            Object batch=new Object();final List<Module[]> vertices=new ArrayList<>();final BitSet erased=new BitSet();Object mode,format;Ref<Object> storage;int pointer,stride,capacity;long address;byte[] payload=new byte[0];boolean indexOnly;String pending="",retirementPending="";
        }
        private record VertexEntry(Object builder,Build build,int prior,Module[] sources,Class<?> type,String method,Thread thread){}
        private record VertexImage(Object batch,List<Module[]> vertices,Object state,Object format,Object mode,int count,int[] indices,byte[] payload,int stride,boolean indexOnly,String pending){}
        private static final class Uploaded {VertexImage image;final BitSet hidden=new BitSet(),erased=new BitSet();String pending="GPU_UPLOAD_PENDING";int vao,vertexId,indexId;long fence;Context context;boolean uploadPending,storageReleased,commandsDrained;}
        private static final class CpuImage {
            final VertexImage image;final Ref<Object> builder;Ref<Object> storage;final int pointer;int capacity;long address;final byte[] retained;final BitSet erased=new BitSet();boolean finished;String pending="";
            CpuImage(VertexImage image,Object builder,Object storage,int pointer,int capacity,long address,byte[] retained){this.image=image;this.builder=new Ref<>(builder);this.storage=new Ref<>(storage);this.pointer=pointer;this.capacity=capacity;this.address=address;this.retained=retained;}
        }
        private static final class SortImage {
            final VertexImage image;final Ref<Object> builder,points;final Ref<Object>[] identities;final int[] coordinates;final BitSet erased=new BitSet();String pending="";
            SortImage(VertexImage image,Object builder,Object points,Ref<Object>[] identities,int[] coordinates){this.image=image;this.builder=new Ref<>(builder);this.points=new Ref<>(points);this.identities=identities;this.coordinates=coordinates;}
        }
        private static final class CpuMaintenance {
            final Class<?> type,renderSystem;final Object owner;final Thread thread=Thread.currentThread();
            CpuMaintenance(Class<?> type,Class<?> renderSystem,Object owner){this.type=type;this.renderSystem=renderSystem;this.owner=owner;}
        }
        private static final class CpuWrite {
            final CpuMaintenance maintenance;final Object carrier,builder;final Build build;final CpuImage cpu;final java.nio.ByteBuffer storage;final int offset,length,pointer,stride,count,position,limit,capacity;final long address;final byte[] expected;final BitSet selected;byte[] outside;boolean checked,afterVerified,ended;
            CpuWrite(CpuMaintenance maintenance,Object carrier,Object builder,Build build,CpuImage cpu,java.nio.ByteBuffer storage,int offset,int length,int pointer,int stride,int count,int position,int limit,int capacity,long address,byte[] expected,BitSet selected){this.maintenance=maintenance;this.carrier=carrier;this.builder=builder;this.build=build;this.cpu=cpu;this.storage=storage;this.offset=offset;this.length=length;this.pointer=pointer;this.stride=stride;this.count=count;this.position=position;this.limit=limit;this.capacity=capacity;this.address=address;this.expected=expected;this.selected=selected;}
        }
        private static final class SortWrite {
            final CpuMaintenance maintenance;final Object carrier,point;final SortImage image;final int index;final Object[] points;Object[] before;int[] coordinates;boolean checked,afterVerified,ended;
            SortWrite(CpuMaintenance maintenance,Object carrier,SortImage image,int index,Object[] points,Object point){this.maintenance=maintenance;this.carrier=carrier;this.image=image;this.index=index;this.points=points;this.point=point;}
        }
        private static final class CpuRelease {
            final Object rendered,builder,storage;final CpuImage image;final Class<?> renderedType,builderType;final int count;final Thread thread=Thread.currentThread();
            CpuRelease(Object rendered,CpuImage image,Object builder,Object storage,Class<?> renderedType,Class<?> builderType,int count){this.rendered=rendered;this.image=image;this.builder=builder;this.storage=storage;this.renderedType=renderedType;this.builderType=builderType;this.count=count;}
        }
        private record UploadEntry(Object buffer,Uploaded target,VertexImage image,Class<?> type){}
        private record Fragment(String sampler,String enabled,boolean supported,String original){}
        private record Linked(Fragment fragment,boolean supported){}
        private static final class Context {final Class<?> api;final Map<Integer,Fragment> shaders=new HashMap<>();final Map<Integer,Linked> programs=new HashMap<>();int texture,buffer;Context(Class<?> api){this.api=api;}}
        private record DrawEntry(Context context,Linked linked,int program,int sampler,int enabled,int oldSampler,int oldEnabled,int textureUnit,int oldTexture){}
        private final Map<Key,Build> builds=new HashMap<>();
        private final Map<Key,CpuImage> rendered=new HashMap<>();
        private final Map<Key,SortImage> sorting=new HashMap<>(),buildSorting=new HashMap<>();
        private final ThreadLocal<CpuRelease> releasingCpu=new ThreadLocal<>();
        private final ThreadLocal<CpuMaintenance> maintainingCpu=new ThreadLocal<>();
        private final ThreadLocal<CpuWrite> clearingCpu=new ThreadLocal<>();
        private final ThreadLocal<SortWrite> clearingSort=new ThreadLocal<>();
        private final Map<Key,Uploaded> uploaded=new HashMap<>();
        private final Map<Long,Context> contexts=new HashMap<>();
        private void require(StackWalker.StackFrame frame,String type,String named,String installed,String descriptor){if(!clientMember(frame,type,named,installed,descriptor))throw new SecurityException("ACTUAL_GPU_BOUNDARY_REQUIRED");}
        private void requireVertexWrite(StackWalker.StackFrame frame){if(!clientMember(frame,"com.mojang.blaze3d.vertex.BufferBuilder","endVertex","m_5752_","()V")&&!clientMember(frame,"com.mojang.blaze3d.vertex.BufferBuilder","putBulkData","putBulkData","(Ljava/nio/ByteBuffer;)V"))throw new SecurityException("ACTUAL_GPU_BOUNDARY_REQUIRED");}
        private int number(Object receiver,Class<?> type,String name)throws ReflectiveOperationException{return ((Number)field(receiver,type,name)).intValue();}
        private long bufferNumber(Object receiver,String name,String descriptor)throws ReflectiveOperationException{Field member=java.nio.Buffer.class.getDeclaredField(name);if(Modifier.isStatic(member.getModifiers())||!member.getType().descriptorString().equals(descriptor))throw new IllegalStateException("GPU_CPU_BUFFER_LAYOUT_CHANGED");member.setAccessible(true);return ((Number)member.get(receiver)).longValue();}
        private void protect(Object value){synchronized(SOURCES){CodeSourceBridge.contributionControl(value);}}
        private byte[] readBytes(java.nio.ByteBuffer storage,int pointer,int length){if(pointer<0||length<0||pointer>storage.limit()-length)throw new IllegalStateException("GPU_CPU_RETAINED_RANGE_CHANGED");byte[] bytes=new byte[length];for(int n=0;n<length;n++)bytes[n]=storage.get(pointer+n);protect(bytes);return bytes;}
        private boolean cpuBytes(java.nio.ByteBuffer storage,int pointer,byte[] expected,int length){if(pointer<0||length<0||length>expected.length||pointer>storage.limit()-length)return false;for(int n=0;n<length;n++)if(storage.get(pointer+n)!=expected[n])return false;return true;}
        private void buildStorage(Object builder,Class<?> type,Build build)throws ReflectiveOperationException{
            Object storage=field(builder,type,"buffer");if(!(storage instanceof java.nio.ByteBuffer bytes)||storage.getClass().getClassLoader()!=null||!bytes.isDirect())throw new IllegalStateException("ACTUAL_CPU_VERTEX_STORAGE_REQUIRED");
            build.pointer=number(builder,type,"renderedBufferPointer");build.storage=new Ref<>(storage);build.capacity=Math.toIntExact(bufferNumber(storage,"capacity","I"));build.address=bufferNumber(storage,"address","J");build.indexOnly=(Boolean)field(builder,type,"indexOnly");
            Class<?> format=actualBase(build.format,"com.mojang.blaze3d.vertex.VertexFormat");if(format==null||build.format.getClass()!=format)throw new IllegalStateException("ACTUAL_VERTEX_FORMAT_REQUIRED");build.stride=((Number)clientMethod(format,"getVertexSize","m_86020_","()I").invoke(build.format)).intValue();if(build.stride<=0)throw new IllegalStateException("GPU_VERTEX_STRIDE_CHANGED");protect(build.storage);protect(build.erased);protect(build.payload);
        }
        void begin(Object builder,StackWalker.StackFrame frame){require(frame,"com.mojang.blaze3d.vertex.BufferBuilder","begin","m_166779_","(Lcom/mojang/blaze3d/vertex/VertexFormat$Mode;Lcom/mojang/blaze3d/vertex/VertexFormat;)V");try{Build build=new Build();build.mode=field(builder,frame.getDeclaringClass(),"mode");build.format=field(builder,frame.getDeclaringClass(),"format");buildStorage(builder,frame.getDeclaringClass(),build);if(number(builder,frame.getDeclaringClass(),"vertices")!=0)build.pending="GPU_BEGIN_VERTEX_STATE_UNOBSERVED";protect(build.vertices);synchronized(this){builds.put(new Key(builder,true),build);buildSorting.remove(new Key(builder,false));}}catch(ReflectiveOperationException failure){throw new IllegalStateException("GPU_BEGIN_UNAVAILABLE",failure);}}
        Object enter(Object builder,Object bulk,StackWalker.StackFrame frame){requireVertexWrite(frame);try{Build build;synchronized(this){build=builds.get(new Key(builder,false));}if(build==null)return null;Module[] owners=union(ControlBridge.invokingSources(),currentSources(),origins(bulk));protect(owners);return new VertexEntry(builder,build,number(builder,frame.getDeclaringClass(),"vertices"),owners,frame.getDeclaringClass(),frame.getMethodName(),Thread.currentThread());}catch(ReflectiveOperationException failure){throw new IllegalStateException("GPU_VERTEX_ENTRY_UNAVAILABLE",failure);}}
        void complete(Object builder,Object token,StackWalker.StackFrame frame){requireVertexWrite(frame);if(token==null)return;if(!(token instanceof VertexEntry entry)||entry.builder()!=builder||entry.type()!=frame.getDeclaringClass()||!entry.method().equals(frame.getMethodName())||entry.thread()!=Thread.currentThread())throw new SecurityException("ACTUAL_VERTEX_WRITE_REQUIRED");
            try{synchronized(this){Build current=builds.get(new Key(builder,false));if(current!=entry.build())throw new IllegalStateException("GPU_VERTEX_BATCH_CHANGED");int count=number(builder,entry.type(),"vertices");if(entry.prior()!=current.vertices.size()||count<entry.prior()){current.pending="GPU_VERTEX_WRITE_SEQUENCE_UNOBSERVED";return;}while(current.vertices.size()<count)current.vertices.add(entry.sources());
                if(!buildIdentity(builder,current)||current.indexOnly){current.pending="GPU_CPU_BUILD_STORAGE_CHANGED";return;}int length=Math.multiplyExact(count,current.stride),prior=Math.multiplyExact(entry.prior(),current.stride);if(current.pointer>current.capacity-length||number(builder,entry.type(),"nextElementByte")<current.pointer+length){current.pending="GPU_CPU_BUILD_RANGE_CHANGED";return;}
                if(current.payload.length<length){current.payload=Arrays.copyOf(current.payload,Math.max(length,Math.max(64,current.payload.length*2)));protect(current.payload);}java.nio.ByteBuffer storage=(java.nio.ByteBuffer)current.storage.get();for(int n=prior;n<length;n++)current.payload[n]=storage.get(current.pointer+n);
            }}catch(ReflectiveOperationException failure){throw new IllegalStateException("GPU_VERTEX_COMPLETION_UNAVAILABLE",failure);}}
        private boolean buildIdentity(Object builder,Build build)throws ReflectiveOperationException{Class<?> type=builder==null?null:actualBase(builder,"com.mojang.blaze3d.vertex.BufferBuilder");Object storage=build.storage==null?null:build.storage.get();return type!=null&&builder.getClass()==type&&storage instanceof java.nio.ByteBuffer
                &&field(builder,type,"buffer")==storage&&field(builder,type,"format")==build.format&&field(builder,type,"mode")==build.mode&&number(builder,type,"renderedBufferPointer")==build.pointer
                &&bufferNumber(storage,"address","J")==build.address&&bufferNumber(storage,"capacity","I")==build.capacity&&(Boolean)field(builder,type,"indexOnly")==build.indexOnly;}
        void capacityChanged(Object builder,StackWalker.StackFrame frame){require(frame,"com.mojang.blaze3d.vertex.BufferBuilder","ensureCapacity","m_85722_","(I)V");try{synchronized(this){Object data=field(builder,frame.getDeclaringClass(),"buffer");if(!(data instanceof java.nio.ByteBuffer storage)||data.getClass().getClassLoader()!=null||!storage.isDirect())throw new IllegalStateException("ACTUAL_CPU_VERTEX_STORAGE_REQUIRED");int capacity=Math.toIntExact(bufferNumber(data,"capacity","I"));long address=bufferNumber(data,"address","J");
            Build build=builds.get(new Key(builder,false));if(build!=null&&build.storage!=null&&build.storage.get()!=data){int length=Math.multiplyExact(build.vertices.size(),build.stride);if(build.pointer==number(builder,frame.getDeclaringClass(),"renderedBufferPointer")&&(build.indexOnly||cpuBytes(storage,build.pointer,build.payload,length))&&capacity>=build.capacity){build.storage=new Ref<>(data);build.capacity=capacity;build.address=address;protect(build.storage);}else build.pending="GPU_CPU_BUILD_REALLOCATION_READBACK_CHANGED";}
            for(var entry:rendered.entrySet()){Object result=entry.getKey().get();CpuImage cpu=entry.getValue();if(result==null||cpu.finished||cpu.builder.get()!=builder||cpu.storage.get()==data)continue;Class<?> type=actualBase(result,"com.mojang.blaze3d.vertex.BufferBuilder$RenderedBuffer");if(type!=null&&field(result,type,"this$0")==builder&&field(result,type,"drawState")==cpu.image.state()&&number(result,type,"pointer")==cpu.pointer&&capacity>=cpu.capacity&&cpuBytes(storage,cpu.pointer,cpu.retained,cpu.retained.length)){cpu.storage=new Ref<>(data);cpu.capacity=capacity;cpu.address=address;protect(cpu.storage);}else cpu.pending="GPU_CPU_REALLOCATION_READBACK_CHANGED";}
        }}catch(ReflectiveOperationException failure){throw new IllegalStateException("GPU_CPU_REALLOCATION_UNAVAILABLE",failure);}}
        private VertexImage image(Object builder,Object result)throws ReflectiveOperationException{
            Class<?> type=actualBase(result,"com.mojang.blaze3d.vertex.BufferBuilder$RenderedBuffer");if(type==null||type!=result.getClass())throw new SecurityException("ACTUAL_RENDERED_BUFFER_REQUIRED");Object state=field(result,type,"drawState");Class<?> shape=actualBase(state,"com.mojang.blaze3d.vertex.BufferBuilder$DrawState");if(shape==null)throw new SecurityException("ACTUAL_DRAW_STATE_REQUIRED");
            Object mode=clientMethod(shape,"mode","f_85735_","()Lcom/mojang/blaze3d/vertex/VertexFormat$Mode;").invoke(state),format=clientMethod(shape,"format","f_85733_","()Lcom/mojang/blaze3d/vertex/VertexFormat;").invoke(state);
            int count=((Number)clientMethod(shape,"vertexCount","f_85734_","()I").invoke(state)).intValue(),indexCount=((Number)clientMethod(shape,"indexCount","f_166797_","()I").invoke(state)).intValue();
            boolean indexOnly=(Boolean)clientMethod(shape,"indexOnly","f_166799_","()Z").invoke(state),sequential=(Boolean)clientMethod(shape,"sequentialIndex","f_166800_","()Z").invoke(state);
            Build build=builds.get(new Key(builder,false));List<Module[]> rows=build==null?List.of():List.copyOf(build.vertices);String pending=build==null?"GPU_VERTEX_BATCH_UNOBSERVED":build.pending;
            if(rows.size()!=count)pending="GPU_VERTEX_COUNT_UNOBSERVED";if(build!=null&&(build.mode!=mode||build.format!=format))pending="GPU_VERTEX_LAYOUT_CHANGED";
            int[] indices=new int[indexCount];Class<?> modeType=actualBase(mode,"com.mojang.blaze3d.vertex.VertexFormat$Mode");if(modeType==null)throw new SecurityException("ACTUAL_VERTEX_MODE_REQUIRED");
            if(sequential){String kind=((Enum<?>)mode).name();int[] offsets=kind.equals("QUADS")?new int[]{0,1,2,2,3,0}:kind.equals("LINES")?new int[]{0,1,2,3,2,1}:null;for(int n=0;n<indexCount;n++)indices[n]=offsets==null?n:n/6*4+offsets[n%6];}
            else{Object indexType=clientMethod(shape,"indexType","f_166798_","()Lcom/mojang/blaze3d/vertex/VertexFormat$IndexType;").invoke(state);Class<?> indexShape=actualBase(indexType,"com.mojang.blaze3d.vertex.VertexFormat$IndexType");if(indexShape==null)throw new SecurityException("ACTUAL_INDEX_TYPE_REQUIRED");int bytes=number(indexType,indexShape,"bytes");java.nio.ByteBuffer data=((java.nio.ByteBuffer)clientMethod(type,"indexBuffer","m_231197_","()Ljava/nio/ByteBuffer;").invoke(result)).duplicate().order(java.nio.ByteOrder.nativeOrder());for(int n=0;n<indexCount;n++)indices[n]=bytes==2?Short.toUnsignedInt(data.getShort(n*2)):bytes==4?data.getInt(n*4):-1;}
            for(int index:indices)if(index<0||index>=count){pending="GPU_INDEX_OUTSIDE_OBSERVED_VERTICES";break;}
            Class<?> formatType=actualBase(format,"com.mojang.blaze3d.vertex.VertexFormat");if(formatType==null||format.getClass()!=formatType)throw new SecurityException("ACTUAL_VERTEX_FORMAT_REQUIRED");int stride=((Number)clientMethod(formatType,"getVertexSize","m_86020_","()I").invoke(format)).intValue();byte[] payload=null;
            if(!indexOnly){java.nio.ByteBuffer data=((java.nio.ByteBuffer)clientMethod(type,"vertexBuffer","m_231196_","()Ljava/nio/ByteBuffer;").invoke(result)).duplicate();int length=Math.multiplyExact(count,stride);if(length!=data.remaining())pending="GPU_VERTEX_PAYLOAD_LENGTH_CHANGED";else{payload=new byte[length];data.get(payload);protect(payload);}}
            protect(indices);VertexImage image=new VertexImage(build==null?null:build.batch,rows,state,format,mode,count,indices,payload,stride,indexOnly,pending);protect(rows);return image;
        }
        void store(Object builder,Object result,StackWalker.StackFrame frame){require(frame,"com.mojang.blaze3d.vertex.BufferBuilder","storeRenderedBuffer","m_231177_","()Lcom/mojang/blaze3d/vertex/BufferBuilder$RenderedBuffer;");try{synchronized(this){VertexImage image=image(builder,result);Class<?> shape=actualBase(result,"com.mojang.blaze3d.vertex.BufferBuilder$RenderedBuffer");Object storage=field(builder,frame.getDeclaringClass(),"buffer");
            if(field(result,shape,"this$0")!=builder||!(storage instanceof java.nio.ByteBuffer))throw new IllegalStateException("GPU_CPU_CARRIER_ASSOCIATION_CHANGED");
            int pointer=number(result,shape,"pointer"),length=((Number)clientMethod(image.state().getClass(),"bufferSize","m_166813_","()I").invoke(image.state())).intValue();byte[] retained=readBytes((java.nio.ByteBuffer)storage,pointer,length);
            CpuImage cpu=new CpuImage(image,builder,storage,pointer,Math.toIntExact(bufferNumber(storage,"capacity","I")),bufferNumber(storage,"address","J"),retained);Build build=builds.get(new Key(builder,false));if(build!=null&&!image.indexOnly())cpu.erased.or(build.erased);protect(cpu);protect(cpu.builder);protect(cpu.storage);protect(cpu.erased);rendered.put(new Key(result,true),cpu);}}
            catch(ReflectiveOperationException failure){throw new IllegalStateException("GPU_RENDERED_IMAGE_UNAVAILABLE",failure);}}
        void released(Object result,StackWalker.StackFrame frame){require(frame,"com.mojang.blaze3d.vertex.BufferBuilder$RenderedBuffer","release","m_231200_","()V");try{synchronized(this){CpuImage cpu=rendered.get(new Key(result,false));if(cpu!=null){if(!(Boolean)field(result,frame.getDeclaringClass(),"released"))throw new IllegalStateException("GPU_CPU_RELEASE_READBACK_PENDING");cpu.finished=true;cpu.pending="";}}}catch(ReflectiveOperationException failure){throw new IllegalStateException("GPU_CPU_RELEASE_READBACK_UNAVAILABLE",failure);}}
        void discarded(Object builder,StackWalker.StackFrame frame){require(frame,"com.mojang.blaze3d.vertex.BufferBuilder","discard","m_85730_","()V");try{synchronized(this){if(number(builder,frame.getDeclaringClass(),"renderedBufferCount")!=0||number(builder,frame.getDeclaringClass(),"renderedBufferPointer")!=0||number(builder,frame.getDeclaringClass(),"nextElementByte")!=0)throw new IllegalStateException("GPU_CPU_DISCARD_READBACK_PENDING");
            for(CpuImage cpu:rendered.values())if(cpu.builder.get()==builder){cpu.finished=true;cpu.pending="";}}}catch(ReflectiveOperationException failure){throw new IllegalStateException("GPU_CPU_DISCARD_READBACK_UNAVAILABLE",failure);}}
        private int[] pointBits(Object point,Class<?> vector)throws ReflectiveOperationException{
            if(point==null||point.getClass()!=vector||!vector.getName().equals("org.joml.Vector3f"))throw new IllegalStateException("ACTUAL_QUAD_SORT_POINT_REQUIRED");int[] bits=new int[3];String[] names={"x","y","z"};for(int n=0;n<3;n++){Field value=vector.getDeclaredField(names[n]);if(value.getType()!=float.class||Modifier.isStatic(value.getModifiers())||!Modifier.isPublic(value.getModifiers()))throw new IllegalStateException("CLIENT_SORT_POINT_LAYOUT_CHANGED");bits[n]=Float.floatToRawIntBits(value.getFloat(point));}return bits;
        }
        @SuppressWarnings("unchecked") private SortImage sortImage(Object builder,Object carrier,Build build)throws ReflectiveOperationException{
            Class<?> type=actualBase(carrier,carrier==builder?"com.mojang.blaze3d.vertex.BufferBuilder":"com.mojang.blaze3d.vertex.BufferBuilder$SortState");if(type==null||carrier.getClass()!=type)throw new IllegalStateException("ACTUAL_QUAD_SORT_CARRIER_REQUIRED");Object points=field(carrier,type,"sortingPoints");List<Module[]> rows=List.copyOf(build.vertices);protect(rows);VertexImage image=new VertexImage(build.batch,rows,null,build.format,build.mode,rows.size(),new int[0],null,0,true,build.pending);
            if(points==null){SortImage result=new SortImage(image,builder,null,new Ref[0],new int[0]);protect(result);protect(result.builder);protect(result.points);protect(result.identities);protect(result.coordinates);protect(result.erased);return result;}if(!(points instanceof Object[] values)||!points.getClass().descriptorString().equals("[Lorg/joml/Vector3f;")||!(build.mode instanceof Enum<?> mode)||!mode.name().equals("QUADS")||rows.size()/4!=values.length||number(carrier,type,"vertices")!=rows.size()||field(carrier,type,"mode")!=build.mode)throw new IllegalStateException("GPU_QUAD_SORTING_LAYOUT_CHANGED");
            Ref<Object>[] identities=new Ref[values.length];int[] coordinates=new int[Math.multiplyExact(values.length,3)];Class<?> vector=points.getClass().getComponentType();for(int n=0;n<values.length;n++){identities[n]=new Ref<>(values[n]);int[] bits=pointBits(values[n],vector);System.arraycopy(bits,0,coordinates,n*3,3);protect(identities[n]);}protect(identities);protect(coordinates);SortImage result=new SortImage(image,builder,points,identities,coordinates);protect(result);protect(result.builder);protect(result.points);protect(result.erased);return result;
        }
        private boolean sortIdentity(Object carrier,SortImage saved)throws ReflectiveOperationException{
            if(carrier==null)return false;Class<?> type=actualBase(carrier,"com.mojang.blaze3d.vertex.BufferBuilder$SortState");if(type==null){type=actualBase(carrier,"com.mojang.blaze3d.vertex.BufferBuilder");Build build=builds.get(new Key(carrier,false));if(build==null||build.batch!=saved.image.batch())return false;}return type!=null&&carrier.getClass()==type&&field(carrier,type,"sortingPoints")==saved.points.get()&&field(carrier,type,"mode")==saved.image.mode()&&number(carrier,type,"vertices")==saved.image.count();
        }
        void sortingChanged(Object builder,StackWalker.StackFrame frame){require(frame,"com.mojang.blaze3d.vertex.BufferBuilder","setQuadSorting","m_277127_","(Lcom/mojang/blaze3d/vertex/VertexSorting;)V");try{synchronized(this){Build build=builds.get(new Key(builder,false));if(build!=null){SortImage previous=buildSorting.get(new Key(builder,false));if(previous==null||!sortIdentity(builder,previous))buildSorting.put(new Key(builder,true),sortImage(builder,builder,build));}}}catch(ReflectiveOperationException failure){throw new IllegalStateException("GPU_QUAD_SORTING_ORIGIN_UNAVAILABLE",failure);}}
        void sortState(Object builder,Object state,StackWalker.StackFrame frame){require(frame,"com.mojang.blaze3d.vertex.BufferBuilder","getSortState","m_166770_","()Lcom/mojang/blaze3d/vertex/BufferBuilder$SortState;");try{synchronized(this){Build build=builds.get(new Key(builder,false));if(build==null)return;SortImage saved=sortImage(builder,state,build);copySortReceipts(saved);sorting.put(new Key(state,true),saved);}}catch(ReflectiveOperationException failure){throw new IllegalStateException("GPU_SORTING_ORIGIN_UNAVAILABLE",failure);}}
        boolean sortAllowed(Object builder,Object state,StackWalker.StackFrame frame){require(frame,"com.mojang.blaze3d.vertex.BufferBuilder","restoreSortState","m_166775_","(Lcom/mojang/blaze3d/vertex/BufferBuilder$SortState;)V");if(stopped(sources(state))||ControlBridge.stoppedEffects())return false;synchronized(this){SortImage saved=sorting.get(new Key(state,false));return saved==null||saved.image.vertices().isEmpty()||!saved.image.vertices().stream().allMatch(this::retiredSources);}}
        void sortRestore(Object builder,Object state,StackWalker.StackFrame frame){require(frame,"com.mojang.blaze3d.vertex.BufferBuilder","restoreSortState","m_166775_","(Lcom/mojang/blaze3d/vertex/BufferBuilder$SortState;)V");try{synchronized(this){SortImage saved=sorting.get(new Key(state,false));Build build=builds.get(new Key(builder,false));if(build==null||saved==null){if(build!=null)build.pending="GPU_SORTING_ORIGIN_UNOBSERVED";return;}Build restored=new Build();restored.batch=saved.image.batch();restored.vertices.addAll(saved.image.vertices());restored.mode=saved.image.mode();restored.format=saved.image.format();restored.pending=saved.image.pending();buildStorage(builder,frame.getDeclaringClass(),restored);protect(restored.vertices);builds.put(new Key(builder,true),restored);SortImage points=sortImage(builder,builder,restored);copySortReceipts(points);buildSorting.put(new Key(builder,true),points);}}catch(ReflectiveOperationException failure){throw new IllegalStateException("GPU_SORTING_RESTORE_UNAVAILABLE",failure);}}
        Object uploading(Object buffer,Object result,StackWalker.StackFrame frame){require(frame,"com.mojang.blaze3d.vertex.VertexBuffer","upload","m_231221_","(Lcom/mojang/blaze3d/vertex/BufferBuilder$RenderedBuffer;)V");try{synchronized(this){CpuImage cpu=rendered.get(new Key(result,false));Class<?> type=actualBase(result,"com.mojang.blaze3d.vertex.BufferBuilder$RenderedBuffer");
            if(type==null||type!=result.getClass())throw new SecurityException("ACTUAL_RENDERED_BUFFER_REQUIRED");if((Boolean)field(result,type,"released")||cpu!=null&&cpu.finished)return Boolean.FALSE;
            Uploaded gpu=uploaded.computeIfAbsent(new Key(buffer,true),ignored->new Uploaded());VertexImage incoming=cpu==null?null:cpu.image;gpu.pending="GPU_UPLOAD_PENDING";gpu.uploadPending=true;return new UploadEntry(buffer,gpu,incoming,frame.getDeclaringClass());}}
            catch(ReflectiveOperationException failure){throw new IllegalStateException("GPU_CPU_UPLOAD_STATE_UNAVAILABLE",failure);}}
        void uploaded(Object buffer,Object token,StackWalker.StackFrame frame){require(frame,"com.mojang.blaze3d.vertex.VertexBuffer","upload","m_231221_","(Lcom/mojang/blaze3d/vertex/BufferBuilder$RenderedBuffer;)V");if(!(token instanceof UploadEntry entry)||entry.buffer()!=buffer||entry.type()!=frame.getDeclaringClass())throw new SecurityException("ACTUAL_GPU_UPLOAD_REQUIRED");try{synchronized(this){Uploaded gpu=uploaded.get(new Key(buffer,false));if(gpu!=entry.target())throw new IllegalStateException("GPU_UPLOAD_RECEIVER_CHANGED");VertexImage image=entry.image();if(image==null){gpu.image=null;gpu.pending="GPU_RENDERED_ORIGIN_UNOBSERVED";return;}
                if(image.indexOnly()){VertexImage previous=gpu.image;if(previous==null||previous.batch()!=image.batch()||previous.count()!=image.count()||previous.format()!=image.format()){gpu.pending="GPU_SORTED_VERTEX_STORAGE_UNOBSERVED";return;}image=new VertexImage(previous.batch(),previous.vertices(),image.state(),image.format(),image.mode(),image.count(),image.indices(),previous.payload(),previous.stride(),true,image.pending());}
                if(field(buffer,entry.type(),"format")!=image.format()||field(buffer,entry.type(),"mode")!=image.mode()||number(buffer,entry.type(),"indexCount")!=image.indices().length){gpu.pending="GPU_UPLOAD_STATE_CHANGED";return;}
                gpu.image=image;gpu.vao=number(buffer,entry.type(),"arrayObjectId");gpu.vertexId=number(buffer,entry.type(),"vertexBufferId");gpu.indexId=number(buffer,entry.type(),"indexBufferId");gpu.pending=image.pending();gpu.context=context(entry.type());gpu.uploadPending=false;gpu.storageReleased=false;gpu.hidden.clear();if(!image.indexOnly()){gpu.erased.clear();gpu.commandsDrained=false;if(gpu.fence!=0){gl(gpu.context,"GL32","glDeleteSync",new Class<?>[]{long.class},gpu.fence);gpu.fence=0;}}
            }}catch(ReflectiveOperationException failure){throw new IllegalStateException("GPU_UPLOAD_READBACK_UNAVAILABLE",failure);}}
        private Object gl(Context context,String api,String method,Class<?>[] parameters,Object...args)throws ReflectiveOperationException{return Class.forName("org.lwjgl.opengl."+api,false,context.api.getClassLoader()).getMethod(method,parameters).invoke(null,args);}
        private int integer(Context context,int property)throws ReflectiveOperationException{return ((Number)gl(context,"GL11","glGetInteger",new Class<?>[]{I},property)).intValue();}
        private Context context(Class<?> api)throws ReflectiveOperationException{long handle=((Number)Class.forName("org.lwjgl.glfw.GLFW",false,api.getClassLoader()).getMethod("glfwGetCurrentContext").invoke(null)).longValue();if(handle==0)throw new IllegalStateException("ACTUAL_GL_CONTEXT_REQUIRED");Context context=contexts.get(handle);if(context==null){context=new Context(api);contexts.put(handle,context);protect(context.shaders);protect(context.programs);}return context;}
        List<String> shaderSource(int shader,List<String> source,StackWalker.StackFrame frame){require(frame,"com.mojang.blaze3d.platform.GlStateManager","glShaderSource","glShaderSource","(ILjava/util/List;)V");try{synchronized(this){Context context=context(frame.getDeclaringClass());int type=((Number)gl(context,"GL20","glGetShaderi",new Class<?>[]{I,I},shader,35663)).intValue();if(type!=35632)return source;
                    String text=String.join("",source),visible=withoutComments(text);java.util.regex.Matcher version=java.util.regex.Pattern.compile("(?m)^\\s*#\\s*version\\s+(\\d+)").matcher(visible),main=java.util.regex.Pattern.compile("\\bvoid\\s+(main)\\s*\\(\\s*(?:void\\s*)?\\)\\s*\\{").matcher(visible);
                    boolean supported=version.find()&&Integer.parseInt(version.group(1))>=150&&main.find()&&!java.util.regex.Pattern.compile("(?m)^\\s*#\\s*define\\s+main\\b").matcher(visible).find();int start=supported?main.start(1):0,end=supported?main.end(1):0;if(supported&&main.find())supported=false;
                    String prefix="ronova_"+UUID.randomUUID().toString().replace("-","");Fragment fragment=new Fragment(prefix+"_mask",prefix+"_enabled",supported,text);context.shaders.put(shader,fragment);if(!supported)return source;
                    String replacement=text.substring(0,start)+prefix+"_original"+text.substring(end)+"\nuniform samplerBuffer "+fragment.sampler()+";\nuniform int "+fragment.enabled()+";\nvoid main(){if("+fragment.enabled()+" != 0 && gl_PrimitiveID >= 0 && texelFetch("+fragment.sampler()+", gl_PrimitiveID).r > 0.5) discard; "+prefix+"_original();}\n";return List.of(replacement);
                }}catch(ReflectiveOperationException failure){throw new IllegalStateException("GPU_FRAGMENT_SOURCE_UNAVAILABLE",failure);}}
        private String withoutComments(String source){char[] chars=source.toCharArray();for(int n=0;n+1<chars.length;n++)if(chars[n]=='/'&&chars[n+1]=='/'){chars[n++]=chars[n]=' ';while(n+1<chars.length&&chars[n+1]!='\n')chars[++n]=' ';}else if(chars[n]=='/'&&chars[n+1]=='*'){chars[n++]=chars[n]=' ';while(n+1<chars.length){if(chars[n]=='*'&&chars[n+1]=='/'){chars[n++]=chars[n]=' ';break;}if(chars[n]!='\n')chars[n]=' ';n++;}}return new String(chars);}
        void linked(int program,StackWalker.StackFrame frame){require(frame,"com.mojang.blaze3d.platform.GlStateManager","glLinkProgram","glLinkProgram","(I)V");try{synchronized(this){Context context=context(frame.getDeclaringClass());int count=((Number)gl(context,"GL20","glGetProgrami",new Class<?>[]{I,I},program,35717)).intValue();int[] ids=new int[count],actual=new int[1];gl(context,"GL20","glGetAttachedShaders",new Class<?>[]{I,int[].class,int[].class},program,actual,ids);Fragment fragment=null;boolean supported=true;
                    if(((Number)gl(context,"GL20","glGetProgrami",new Class<?>[]{I,I},program,35714)).intValue()==0){for(int id:ids){Fragment applied=context.shaders.get(id);if(applied!=null&&applied.supported())restoreFragment(context,id,applied);}gl(context,"GL20","glLinkProgram",new Class<?>[]{I},program);supported=false;}
                    for(int n=0;n<actual[0];n++){int type=((Number)gl(context,"GL20","glGetShaderi",new Class<?>[]{I,I},ids[n],35663)).intValue();if(type==35632)fragment=context.shaders.get(ids[n]);else if(type!=35633)supported=false;}context.programs.put(program,new Linked(fragment,supported&&fragment!=null&&fragment.supported()));
                }}catch(ReflectiveOperationException failure){throw new IllegalStateException("GPU_LINK_IMAGE_UNAVAILABLE",failure);}}
        void compiled(int shader,StackWalker.StackFrame frame){require(frame,"com.mojang.blaze3d.platform.GlStateManager","glCompileShader","glCompileShader","(I)V");try{synchronized(this){Context context=context(frame.getDeclaringClass());Fragment applied=context.shaders.get(shader);if(applied!=null&&applied.supported()&&((Number)gl(context,"GL20","glGetShaderi",new Class<?>[]{I,I},shader,35713)).intValue()==0)restoreFragment(context,shader,applied);}}catch(ReflectiveOperationException failure){throw new IllegalStateException("GPU_MASK_COMPILE_FALLBACK_UNAVAILABLE",failure);}}
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
                for(int vertex=selected.nextSetBit(0);vertex>=0;vertex=selected.nextSetBit(vertex+1)){int offset=Math.multiplyExact(vertex,image.stride());java.nio.ByteBuffer current=java.nio.ByteBuffer.allocateDirect(image.stride());
                    gl(context,"GL15","glGetBufferSubData",new Class<?>[]{I,long.class,java.nio.ByteBuffer.class},36662,(long)offset,current);
                    if(gpu.erased.get(vertex)){if(zeroBytes(current))continue;gpu.erased.clear(vertex);gpu.commandsDrained=false;gpu.pending="GPU_VERTEX_CONTENT_CHANGED";complete=false;continue;}
                    if(!sameBytes(current,image.payload(),offset,image.stride())&&!zeroBytes(current)){gpu.pending="GPU_VERTEX_CONTENT_CHANGED";complete=false;continue;}
                    if(!zeroBytes(current)){java.nio.ByteBuffer zero=java.nio.ByteBuffer.allocateDirect(image.stride());gl(context,"GL15","glBufferSubData",new Class<?>[]{I,long.class,java.nio.ByteBuffer.class},36662,(long)offset,zero);current.clear();gl(context,"GL15","glGetBufferSubData",new Class<?>[]{I,long.class,java.nio.ByteBuffer.class},36662,(long)offset,current);}
                    if(!zeroBytes(current)){gpu.pending="GPU_VERTEX_ERASE_READBACK_PENDING";complete=false;continue;}
                    Arrays.fill(image.payload(),offset,offset+image.stride(),(byte)0);gpu.erased.set(vertex);gpu.commandsDrained=false;if(gpu.fence!=0){gl(context,"GL32","glDeleteSync",new Class<?>[]{long.class},gpu.fence);gpu.fence=0;}
                }
                return complete;
            }finally{gl(context,"GL15","glBindBuffer",new Class<?>[]{I,I},36662,original);}
        }
        private boolean cpuIdentity(Object result,CpuImage cpu)throws ReflectiveOperationException{
            Class<?> type=actualBase(result,"com.mojang.blaze3d.vertex.BufferBuilder$RenderedBuffer");Object builder=cpu.builder.get(),storage=cpu.storage.get();Class<?> owner=builder==null?null:actualBase(builder,"com.mojang.blaze3d.vertex.BufferBuilder");
            if(type==null||type!=result.getClass()||owner==null||builder.getClass()!=owner||storage==null)return false;Object state=cpu.image.state();Class<?> shape=actualBase(state,"com.mojang.blaze3d.vertex.BufferBuilder$DrawState");if(shape==null)return false;
            return clientMethod(shape,"format","f_85733_","()Lcom/mojang/blaze3d/vertex/VertexFormat;").invoke(state)==cpu.image.format()&&clientMethod(shape,"mode","f_85735_","()Lcom/mojang/blaze3d/vertex/VertexFormat$Mode;").invoke(state)==cpu.image.mode()
                    &&((Number)clientMethod(shape,"vertexCount","f_85734_","()I").invoke(state)).intValue()==cpu.image.count()&&(Boolean)clientMethod(shape,"indexOnly","f_166799_","()Z").invoke(state)==cpu.image.indexOnly()&&((Number)clientMethod(shape,"bufferSize","m_166813_","()I").invoke(state)).intValue()==cpu.retained.length
                    &&field(result,type,"drawState")==cpu.image.state()&&field(result,type,"this$0")==builder&&number(result,type,"pointer")==cpu.pointer
                    &&field(builder,owner,"buffer")==storage&&bufferNumber(storage,"address","J")==cpu.address&&bufferNumber(storage,"capacity","I")==cpu.capacity;
        }
        private boolean maintenanceIdentity(CpuMaintenance token)throws ReflectiveOperationException{
            if(token==null||maintainingCpu.get()!=token||token.thread!=Thread.currentThread()||UI_FIELDS.get(token.type).instance.get(null)!=token.owner)return false;
            Method render=token.renderSystem.getDeclaredMethod("isOnRenderThread");if(!Modifier.isStatic(render.getModifiers())||render.getReturnType()!=boolean.class||!(Boolean)render.invoke(null))return false;
            return WALKER.walk(frames->frames.anyMatch(frame->frame.getDeclaringClass()==token.type&&clientMember(frame,"net.minecraft.client.Minecraft","runTick","m_91383_","(Z)V")));
        }
        private boolean stoppedCpuSources(Module[] modules){if(!retiredSources(modules))return false;for(Module module:modules)if(!ControlBridge.modStopped(module))return false;return true;}
        private BitSet eligibleVertices(VertexImage image)throws ReflectiveOperationException{
            BitSet selected=new BitSet(image.count()),outside=new BitSet(image.count());for(int n=0;n<image.count();n++)if(stoppedCpuSources(image.vertices().get(n)))selected.set(n);
            Class<?> type=actualBase(image.mode(),"com.mojang.blaze3d.vertex.VertexFormat$Mode");if(type==null)throw new IllegalStateException("ACTUAL_VERTEX_MODE_REQUIRED");int mode=number(image.mode(),type,"asGLMode");
            if(mode!=0&&mode!=1&&mode!=3&&mode!=4&&mode!=5&&mode!=6)throw new IllegalStateException("GPU_PRIMITIVE_LAYOUT_UNSUPPORTED");for(int n=0;;n++){int[] part=primitive(mode,n,image.indices());if(part==null)break;boolean owned=true;for(int vertex:part)owned&=selected.get(vertex);if(!owned)for(int vertex:part)outside.set(vertex);}selected.andNot(outside);return selected;
        }
        private VertexImage buildImage(Build build)throws ReflectiveOperationException{
            Class<?> mode=actualBase(build.mode,"com.mojang.blaze3d.vertex.VertexFormat$Mode");if(mode==null)throw new IllegalStateException("ACTUAL_VERTEX_MODE_REQUIRED");int count=build.vertices.size(),indexCount=((Number)clientMethod(mode,"indexCount","m_166958_","(I)I",int.class).invoke(build.mode,count)).intValue();int[] indices=new int[indexCount];String kind=((Enum<?>)build.mode).name();int[] offsets=kind.equals("QUADS")?new int[]{0,1,2,2,3,0}:kind.equals("LINES")?new int[]{0,1,2,3,2,1}:null;for(int n=0;n<indices.length;n++){indices[n]=offsets==null?n:n/6*4+offsets[n%6];if(indices[n]>=count)throw new IllegalStateException("GPU_INDEX_OUTSIDE_OBSERVED_VERTICES");}return new VertexImage(build.batch,build.vertices,null,build.format,build.mode,count,indices,build.payload,build.stride,build.indexOnly,build.pending);
        }
        synchronized boolean payloadAllowed(Object proof,Object data,int offset,int length,boolean after){
            if(!(proof instanceof CpuWrite token)||clearingCpu.get()!=token||token.ended||data!=token.storage||offset!=token.offset||length!=token.length)return false;
            try{if(!maintenanceIdentity(token.maintenance)||token.storage.getClass().getClassLoader()!=null||!token.storage.isDirect()||token.storage.isReadOnly()||token.stride<=0||offset<token.pointer||length<=0||(offset-token.pointer)%token.stride!=0||length%token.stride!=0
                    ||bufferNumber(data,"address","J")!=token.address||bufferNumber(data,"capacity","I")!=token.capacity||bufferNumber(data,"position","I")!=token.position||bufferNumber(data,"limit","I")!=token.limit||offset>token.limit-length)return false;
                List<Module[]> rows;if(token.cpu!=null){if(rendered.get(new Key(token.carrier,false))!=token.cpu||token.cpu.finished||token.cpu.image.indexOnly()||!cpuIdentity(token.carrier,token.cpu))return false;Class<?> type=actualBase(token.carrier,"com.mojang.blaze3d.vertex.BufferBuilder$RenderedBuffer"),owner=actualBase(token.builder,"com.mojang.blaze3d.vertex.BufferBuilder");if((Boolean)field(token.carrier,type,"released")||token.pointer>number(token.builder,owner,"renderedBufferPointer")-token.expected.length)return false;rows=token.cpu.image.vertices();}
                else{if(builds.get(new Key(token.builder,false))!=token.build||token.build.indexOnly||!buildIdentity(token.builder,token.build))return false;Class<?> type=actualBase(token.builder,"com.mojang.blaze3d.vertex.BufferBuilder");if(!(Boolean)field(token.builder,type,"building")||number(token.builder,type,"vertices")!=token.count||number(token.builder,type,"nextElementByte")<token.pointer+token.expected.length)return false;rows=token.build.vertices;}
                int first=(offset-token.pointer)/token.stride,end=first+length/token.stride;if(end>rows.size())return false;for(int n=first;n<end;n++)if(!token.selected.get(n)||!stoppedCpuSources(rows.get(n)))return false;
                if(!after){if(token.checked||!cpuBytes(token.storage,token.pointer,token.expected,token.expected.length))return false;token.outside=readBytes(token.storage,0,token.limit);token.checked=true;return true;}
                if(!token.checked||token.afterVerified||token.outside==null)return false;for(int n=0;n<token.outside.length;n++){byte expected=n>=offset&&n-offset<length?0:token.outside[n];if(token.storage.get(n)!=expected)return false;}token.afterVerified=true;return true;
            }catch(ReflectiveOperationException|RuntimeException changed){return false;}
        }
        private String cpuFailure(Throwable failure){while(failure.getCause()!=null&&failure.getCause()!=failure)failure=failure.getCause();return failure.getClass().getSimpleName()+":"+String.valueOf(failure.getMessage());}
        private void clearRanges(Object carrier,Object builder,Build build,CpuImage cpu,VertexImage image)throws ReflectiveOperationException{
            BitSet selected=eligibleVertices(image);if(selected.isEmpty())return;Object data=cpu==null?build.storage.get():cpu.storage.get();if(!(data instanceof java.nio.ByteBuffer storage))throw new IllegalStateException("GPU_CPU_STORAGE_UNAVAILABLE");int pointer=cpu==null?build.pointer:cpu.pointer,stride=image.stride(),count=image.count();if(stride<=0)throw new IllegalStateException("GPU_CPU_VERTEX_STRIDE_UNOBSERVED");
            for(int first=selected.nextSetBit(0);first>=0;){int end=selected.nextClearBit(first),offset=Math.addExact(pointer,Math.multiplyExact(first,stride)),length=Math.multiplyExact(end-first,stride);byte[] expected=cpu==null?Arrays.copyOf(build.payload,Math.multiplyExact(count,stride)):cpu.retained.clone();protect(expected);CpuWrite token=new CpuWrite(maintainingCpu.get(),carrier,builder,build,cpu,storage,offset,length,pointer,stride,count,Math.toIntExact(bufferNumber(data,"position","I")),Math.toIntExact(bufferNumber(data,"limit","I")),Math.toIntExact(bufferNumber(data,"capacity","I")),bufferNumber(data,"address","J"),expected,(BitSet)selected.clone());protect(token);protect(token.selected);CpuWrite previous=clearingCpu.get();clearingCpu.set(token);
                try{clearClientPayload(token,storage,offset,length,token.maintenance.type);if(!token.afterVerified)throw new IllegalStateException("GPU_CPU_VERTEX_ERASE_READBACK_PENDING");byte[] retained=cpu==null?build.payload:cpu.retained;Arrays.fill(retained,offset-pointer,offset-pointer+length,(byte)0);(cpu==null?build.erased:cpu.erased).set(first,end);if(cpu==null)build.retirementPending="";else cpu.pending="";}
                catch(Throwable unavailable){if(cpu==null)build.retirementPending="GPU_CPU_PAYLOAD_ERASE:"+cpuFailure(unavailable);else cpu.pending="GPU_CPU_PAYLOAD_ERASE:"+cpuFailure(unavailable);break;}
                finally{token.ended=true;if(previous==null)clearingCpu.remove();else clearingCpu.set(previous);}first=selected.nextSetBit(end);
            }
        }
        private boolean quadStopped(SortImage image,int index){int first=index*4;if(first<0||first>image.image.vertices().size()-4)return false;for(int n=first;n<first+4;n++)if(!stoppedCpuSources(image.image.vertices().get(n)))return false;return true;}
        private List<Map.Entry<Key,SortImage>> sortRecords(){List<Map.Entry<Key,SortImage>> records=new ArrayList<>(sorting.entrySet());records.addAll(buildSorting.entrySet());return records;}
        private void copySortReceipts(SortImage target)throws ReflectiveOperationException{
            Object data=target.points.get();if(!(data instanceof Object[] points))return;for(var entry:sortRecords()){SortImage prior=entry.getValue();if(prior==target||prior.points.get()!=data)continue;for(int n=0;n<points.length&&n<prior.identities.length;n++)if(prior.erased.get(n)&&target.identities[n].get()==prior.identities[n].get()&&Arrays.stream(pointBits(points[n],data.getClass().getComponentType())).allMatch(value->value==0))target.erased.set(n);}
        }
        private boolean privateSortPoint(Object point)throws ReflectiveOperationException{
            Module[] outside=origins(point);if(outside.length!=0&&!stoppedCpuSources(outside))return false;boolean known=false;for(var entry:sortRecords()){Object carrier=entry.getKey().get();SortImage saved=entry.getValue();if(carrier==null)continue;Class<?> type=actualBase(carrier,"com.mojang.blaze3d.vertex.BufferBuilder$SortState");if(type==null)type=actualBase(carrier,"com.mojang.blaze3d.vertex.BufferBuilder");if(type==null)return false;Object current=field(carrier,type,"sortingPoints");if(!(current instanceof Object[] points))continue;for(int n=0;n<points.length;n++)if(points[n]==point){known=true;if(current!=saved.points.get()||!sortIdentity(carrier,saved)||n>=saved.identities.length||saved.identities[n].get()!=point||!quadStopped(saved,n))return false;}}
            for(var entry:builds.entrySet()){Object builder=entry.getKey().get();if(builder==null)continue;Class<?> type=actualBase(builder,"com.mojang.blaze3d.vertex.BufferBuilder");if(type==null)return false;Object current=field(builder,type,"sortingPoints");if(current instanceof Object[] points&&Arrays.stream(points).anyMatch(value->value==point)){SortImage saved=buildSorting.get(new Key(builder,false));if(saved==null||saved.points.get()!=current||!sortIdentity(builder,saved))return false;}}
            return known;
        }
        synchronized boolean sortPointAllowed(Object proof,Object point,boolean after){
            if(!(proof instanceof SortWrite token)||clearingSort.get()!=token||token.ended||point!=token.point)return false;
            try{if(!maintenanceIdentity(token.maintenance)||!sortIdentity(token.carrier,token.image)||token.image.points.get()!=token.points||token.index<0||token.index>=token.points.length||token.points[token.index]!=point||token.image.identities[token.index].get()!=point||!quadStopped(token.image,token.index)||!privateSortPoint(point))return false;Class<?> vector=token.points.getClass().getComponentType();
                if(!after){if(token.checked)return false;token.before=token.points.clone();token.coordinates=new int[Math.multiplyExact(token.points.length,3)];protect(token.before);protect(token.coordinates);for(int n=0;n<token.points.length;n++){if(token.points[n]!=token.image.identities[n].get())return false;int[] bits=pointBits(token.points[n],vector);for(int axis=0;axis<3;axis++){if(bits[axis]!=token.image.coordinates[n*3+axis])return false;token.coordinates[n*3+axis]=bits[axis];}}token.checked=true;return true;}
                if(!token.checked||token.afterVerified)return false;for(int n=0;n<token.points.length;n++){if(token.points[n]!=token.before[n])return false;int[] bits=pointBits(token.points[n],vector);for(int axis=0;axis<3;axis++)if(bits[axis]!=(token.points[n]==point?0:token.coordinates[n*3+axis]))return false;}token.afterVerified=true;return true;
            }catch(ReflectiveOperationException|RuntimeException changed){return false;}
        }
        private void retireSortPoints(){
            for(var entry:sortRecords()){Object carrier=entry.getKey().get();SortImage saved=entry.getValue();Object data=saved.points.get();if(carrier==null||!(data instanceof Object[] points)||!saved.image.pending().isEmpty())continue;
                try{if(!sortIdentity(carrier,saved)){saved.pending="GPU_CPU_SORTING_IDENTITY_CHANGED";continue;}for(int n=0;n<points.length;n++){if(!quadStopped(saved,n)||!privateSortPoint(points[n]))continue;SortWrite token=new SortWrite(maintainingCpu.get(),carrier,saved,n,points,points[n]);protect(token);SortWrite previous=clearingSort.get();clearingSort.set(token);
                        try{clearClientSortPoint(token,token.point,token.maintenance.type);if(!token.afterVerified)throw new IllegalStateException("GPU_CPU_SORT_POINT_READBACK_PENDING");for(var peer:sortRecords()){Object peerCarrier=peer.getKey().get();SortImage image=peer.getValue();Object peerData=image.points.get();if(peerCarrier==null||!(peerData instanceof Object[] values)||!sortIdentity(peerCarrier,image))continue;for(int index=0;index<values.length;index++)if(values[index]==token.point&&image.identities[index].get()==token.point){Arrays.fill(image.coordinates,index*3,index*3+3,0);image.erased.set(index);image.pending="";}}}
                        catch(Throwable unavailable){saved.pending="GPU_CPU_SORT_POINT_ERASE:"+cpuFailure(unavailable);break;}
                        finally{token.ended=true;if(previous==null)clearingSort.remove();else clearingSort.set(previous);}
                    }
                }catch(ReflectiveOperationException|RuntimeException unavailable){saved.pending="GPU_CPU_SORT_POINT_ERASE:"+cpuFailure(unavailable);}
            }
        }
        private void retireBuilds(){for(var entry:builds.entrySet()){Object builder=entry.getKey().get();Build build=entry.getValue();if(builder==null||build.indexOnly||build.vertices.isEmpty()||!build.pending.isEmpty())continue;try{Class<?> type=actualBase(builder,"com.mojang.blaze3d.vertex.BufferBuilder");if(type==null||!(Boolean)field(builder,type,"building"))continue;if(!buildIdentity(builder,build)||number(builder,type,"vertices")!=build.vertices.size()){build.retirementPending="GPU_CPU_BUILD_IDENTITY_CHANGED";continue;}clearRanges(builder,builder,build,null,buildImage(build));}catch(ReflectiveOperationException|RuntimeException unavailable){build.retirementPending="GPU_CPU_PAYLOAD_ERASE:"+cpuFailure(unavailable);}}}
        private void retireCpu(){
            for(var entry:rendered.entrySet()){Object result=entry.getKey().get();CpuImage cpu=entry.getValue();if(result==null||cpu.finished||cpu.image.vertices().isEmpty()||!cpu.image.pending().isEmpty())continue;
                try{if(!cpuIdentity(result,cpu)){cpu.pending="GPU_CPU_STORAGE_IDENTITY_CHANGED";continue;}Class<?> type=actualBase(result,"com.mojang.blaze3d.vertex.BufferBuilder$RenderedBuffer");
                    if((Boolean)field(result,type,"released")){cpu.finished=true;cpu.pending="";continue;}Object builder=cpu.builder.get(),storage=cpu.storage.get();if(!cpu.image.indexOnly())clearRanges(result,builder,null,cpu,cpu.image);
                    if(!cpu.image.vertices().stream().allMatch(this::retiredSources)||!retiredSources(sources(result)))continue;Class<?> owner=actualBase(builder,"com.mojang.blaze3d.vertex.BufferBuilder");int count=number(builder,owner,"renderedBufferCount");
                    if(count<=0||count==1&&(Boolean)field(builder,owner,"building")){cpu.pending="GPU_CPU_ORIGINAL_RELEASE_NOT_SAFE";continue;}
                    int bytes=((Number)clientMethod(cpu.image.state().getClass(),"bufferSize","m_166813_","()I").invoke(cpu.image.state())).intValue();
                    if(cpu.pointer<0||bytes<0||cpu.pointer>cpu.capacity-bytes||cpu.pointer+bytes>number(builder,owner,"renderedBufferPointer")){cpu.pending="GPU_CPU_RETAINED_RANGE_CHANGED";continue;}
                    CpuRelease token=new CpuRelease(result,cpu,builder,storage,type,owner,count),previous=releasingCpu.get();protect(token);releasingCpu.set(token);
                    try{platformVoid(result,type,"release");if(!(Boolean)field(result,type,"released"))cpu.pending="GPU_CPU_ORIGINAL_RELEASE_PENDING";else{cpu.finished=true;cpu.pending="";}}
                    finally{if(previous==null)releasingCpu.remove();else releasingCpu.set(previous);}
                }catch(Throwable failure){cpu.pending="GPU_CPU_ORIGINAL_RELEASE:"+failure.getClass().getSimpleName()+":"+String.valueOf(failure.getMessage());}
            }
        }
        boolean cleanupField(Object receiver,Class<?> declaring,String name){
            CpuRelease token=releasingCpu.get();if(token==null||token.thread!=Thread.currentThread())return false;
            try{if(!cpuIdentity(token.rendered,token.image)||field(token.builder,token.builderType,"buffer")!=token.storage)return false;
                int count=number(token.builder,token.builderType,"renderedBufferCount");if(count<token.count-1||count>token.count)return false;
            }catch(ReflectiveOperationException|RuntimeException changed){return false;}
            boolean original=WALKER.walk(frames->frames.anyMatch(frame->frame.getDeclaringClass()==token.renderedType&&clientMember(frame,"com.mojang.blaze3d.vertex.BufferBuilder$RenderedBuffer","release","m_231200_","()V")));
            if(!original)return false;
            return receiver==token.rendered&&declaring==token.renderedType&&Set.of("released","f_231191_").contains(name)
                    ||receiver==token.builder&&declaring==token.builderType&&Set.of("renderedBufferCount","f_231156_","renderedBufferPointer","f_231157_","nextElementByte","f_85652_").contains(name);
        }
        private boolean drain(Uploaded gpu)throws ReflectiveOperationException{
            if(gpu.commandsDrained)return true;if(gpu.fence==0){gpu.fence=((Number)gl(gpu.context,"GL32","glFenceSync",new Class<?>[]{I,I},37143,0)).longValue();if(gpu.fence==0){gpu.pending="GPU_RETIREMENT_FENCE_UNAVAILABLE";return false;}gl(gpu.context,"GL11","glFlush",new Class<?>[0]);}
            int status=((Number)gl(gpu.context,"GL32","glClientWaitSync",new Class<?>[]{long.class,I,long.class},gpu.fence,0,0L)).intValue();if(status!=37146&&status!=37148){gpu.pending=status==37149?"GPU_RETIREMENT_WAIT_FAILED":"GPU_PRIOR_COMMANDS_EXIT_PENDING";return false;}
            gl(gpu.context,"GL32","glDeleteSync",new Class<?>[]{long.class},gpu.fence);gpu.fence=0;gpu.commandsDrained=true;return true;
        }
        void maintenance(StackWalker.StackFrame frame){require(frame,"net.minecraft.client.Minecraft","runTick","m_91383_","(Z)V");synchronized(SOURCES){if(REQUESTED.isEmpty())return;}
            CpuMaintenance maintenancePrior=maintainingCpu.get();try{Class<?> renderType=frame.getDeclaringClass(),renderSystem=Class.forName("com.mojang.blaze3d.systems.RenderSystem",false,renderType.getClassLoader());Object owner=UI_FIELDS.get(renderType).instance.get(null);if(owner==null||owner.getClass()!=renderType||!role(renderSystem))throw new SecurityException("ACTUAL_CLIENT_CPU_RENDER_OWNER_REQUIRED");CpuMaintenance scope=new CpuMaintenance(renderType,renderSystem,owner);protect(scope);maintainingCpu.set(scope);if(!maintenanceIdentity(scope))throw new SecurityException("ACTUAL_CLIENT_CPU_RENDER_THREAD_REQUIRED");
                synchronized(this){retireBuilds();retireCpu();retireSortPoints();Context context=context(frame.getDeclaringClass());for(var entry:uploaded.entrySet()){
                    Uploaded gpu=entry.getValue();Object receiver=entry.getKey().get();if(gpu.context!=context||gpu.image==null||gpu.uploadPending||!gpu.image.pending().isEmpty())continue;
                    if(receiver==null){gpu.pending="GPU_ORIGINAL_CARRIER_RELEASE_UNOBSERVED";continue;}Class<?> type=actualBase(receiver,"com.mojang.blaze3d.vertex.VertexBuffer");if(type==null||number(receiver,type,"vertexBufferId")!=gpu.vertexId||number(receiver,type,"indexBufferId")!=gpu.indexId||number(receiver,type,"arrayObjectId")!=gpu.vao){gpu.pending="GPU_STORAGE_IDENTITY_CHANGED";continue;}
                    BitSet selected=privateVertices(gpu);if(selected.isEmpty())continue;if(!eraseVertices(gpu,selected)||!drain(gpu))continue;
                    boolean allVertices=true;for(Module[] owners:gpu.image.vertices())allVertices&=retiredSources(owners);
                    if(allVertices&&retiredSources(sources(receiver))){Object previous=RELEASING.get();RELEASING.set(receiver);try{platformVoid(receiver,type,"close");}catch(Throwable unavailable){gpu.pending="GPU_PRIVATE_BUFFER_RELEASE:"+unavailable.getClass().getSimpleName();continue;}finally{if(previous==null)RELEASING.remove();else RELEASING.set(previous);}
                        if(number(receiver,type,"vertexBufferId")==-1&&number(receiver,type,"indexBufferId")==-1&&number(receiver,type,"arrayObjectId")==-1){gpu.storageReleased=true;gpu.pending="";}else gpu.pending="GPU_PRIVATE_BUFFER_RELEASE_PENDING";
                    }else gpu.pending="GPU_SHARED_BUFFER_OTHER_SHARES_RETAINED";
                }uploaded.entrySet().removeIf(entry->entry.getValue().storageReleased);}
            }catch(ReflectiveOperationException failure){throw new IllegalStateException("GPU_SOURCE_RETIREMENT_UNAVAILABLE",failure);}
            finally{if(maintenancePrior==null)maintainingCpu.remove();else maintainingCpu.set(maintenancePrior);}
        }
        Object drawing(Object buffer,StackWalker.StackFrame frame){require(frame,"com.mojang.blaze3d.vertex.VertexBuffer","draw","m_166882_","()V");try{synchronized(this){Uploaded gpu=uploaded.get(new Key(buffer,false));if(gpu==null||gpu.image==null||gpu.uploadPending||!gpu.image.pending().isEmpty())return null;VertexImage image=gpu.image;Context context=context(frame.getDeclaringClass());if(context!=gpu.context||integer(context,34229)!=gpu.vao||number(buffer,frame.getDeclaringClass(),"vertexBufferId")!=gpu.vertexId||number(buffer,frame.getDeclaringClass(),"indexBufferId")!=gpu.indexId){gpu.pending="GPU_STORAGE_BINDING_CHANGED";return null;}
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
        void drawn(Object token,StackWalker.StackFrame frame){require(frame,"com.mojang.blaze3d.vertex.VertexBuffer","draw","m_166882_","()V");if(token==null||token==Boolean.FALSE)return;if(!(token instanceof DrawEntry entry))throw new SecurityException("ACTUAL_GPU_DRAW_TOKEN_REQUIRED");try{synchronized(this){Context context=entry.context();int active=integer(context,34016);gl(context,"GL13","glActiveTexture",new Class<?>[]{I},33984+entry.textureUnit());try{gl(context,"GL11","glBindTexture",new Class<?>[]{I,I},35882,entry.oldTexture());}finally{gl(context,"GL13","glActiveTexture",new Class<?>[]{I},active);}if(context.programs.get(entry.program())!=entry.linked())return;int current=integer(context,35725);if(current!=entry.program())gl(context,"GL20","glUseProgram",new Class<?>[]{I},entry.program());try{gl(context,"GL20","glUniform1i",new Class<?>[]{I,I},entry.enabled(),entry.oldEnabled());gl(context,"GL20","glUniform1i",new Class<?>[]{I,I},entry.sampler(),entry.oldSampler());}finally{if(current!=entry.program())gl(context,"GL20","glUseProgram",new Class<?>[]{I},current);}}}catch(ReflectiveOperationException failure){throw new IllegalStateException("GPU_MASK_SCOPE_RELEASE_UNAVAILABLE",failure);}}
        void closed(Object buffer,StackWalker.StackFrame frame){require(frame,"com.mojang.blaze3d.vertex.VertexBuffer","close","close","()V");try{synchronized(this){if(number(buffer,frame.getDeclaringClass(),"vertexBufferId")==-1&&number(buffer,frame.getDeclaringClass(),"indexBufferId")==-1&&number(buffer,frame.getDeclaringClass(),"arrayObjectId")==-1){Uploaded gpu=uploaded.get(new Key(buffer,false));if(gpu!=null){gpu.storageReleased=true;gpu.pending="";if(gpu.fence!=0){gl(gpu.context,"GL32","glDeleteSync",new Class<?>[]{long.class},gpu.fence);gpu.fence=0;}}}}}catch(ReflectiveOperationException failure){throw new IllegalStateException("GPU_BUFFER_CLOSE_READBACK_UNAVAILABLE",failure);}}
        void shaderClosed(int shader,StackWalker.StackFrame frame){require(frame,"com.mojang.blaze3d.platform.GlStateManager","glDeleteShader","glDeleteShader","(I)V");try{synchronized(this){context(frame.getDeclaringClass()).shaders.remove(shader);}}catch(ReflectiveOperationException failure){throw new IllegalStateException("GPU_SHADER_CLOSE_UNAVAILABLE",failure);}}
        void programClosed(int program,StackWalker.StackFrame frame){require(frame,"com.mojang.blaze3d.platform.GlStateManager","glDeleteProgram","glDeleteProgram","(I)V");try{synchronized(this){context(frame.getDeclaringClass()).programs.remove(program);}}catch(ReflectiveOperationException failure){throw new IllegalStateException("GPU_PROGRAM_CLOSE_UNAVAILABLE",failure);}}
        void contextClosing(StackWalker.StackFrame frame){require(frame,"net.minecraft.client.Minecraft","close","close","()V");try{synchronized(this){Context context=context(frame.getDeclaringClass());if(context.buffer!=0){gl(context,"GL15","glDeleteBuffers",new Class<?>[]{I},context.buffer);if(Boolean.TRUE.equals(gl(context,"GL15","glIsBuffer",new Class<?>[]{I},context.buffer)))throw new IllegalStateException("GPU_MASK_BUFFER_RELEASE_PENDING");context.buffer=0;}if(context.texture!=0){gl(context,"GL11","glDeleteTextures",new Class<?>[]{I},context.texture);if(Boolean.TRUE.equals(gl(context,"GL11","glIsTexture",new Class<?>[]{I},context.texture)))throw new IllegalStateException("GPU_MASK_TEXTURE_RELEASE_PENDING");context.texture=0;}context.programs.clear();context.shaders.clear();contexts.values().removeIf(value->value==context);}}catch(ReflectiveOperationException failure){throw new IllegalStateException("GPU_CONTEXT_RESOURCE_RELEASE_UNAVAILABLE",failure);}}
        private long sourceVertices(List<Module[]> rows,Module module,BitSet erased,boolean completed){long count=0;for(int n=0;n<rows.size();n++)if(Arrays.stream(rows.get(n)).anyMatch(source->source==module)&&(!completed||erased==null||!erased.get(n)))count++;return count;}
        private BitSet cpuReadback(Object carrier,Build build,CpuImage cpu)throws ReflectiveOperationException{
            BitSet receipt=(BitSet)(cpu==null?build.erased:cpu.erased).clone();Object data=cpu==null?build.storage.get():cpu.storage.get();int pointer=cpu==null?build.pointer:cpu.pointer,stride=cpu==null?build.stride:cpu.image.stride();
            if(!(data instanceof java.nio.ByteBuffer storage)||stride<=0||(cpu==null?!build.pending.isEmpty()||!buildIdentity(carrier,build):!cpu.image.pending().isEmpty()||!cpuIdentity(carrier,cpu)))return new BitSet();
            for(int vertex=receipt.nextSetBit(0);vertex>=0;vertex=receipt.nextSetBit(vertex+1)){int offset=Math.addExact(pointer,Math.multiplyExact(vertex,stride));if(offset<0||offset>storage.limit()-stride){receipt.clear(vertex);continue;}for(int n=0;n<stride;n++)if(storage.get(offset+n)!=0){receipt.clear(vertex);break;}}
            return receipt;
        }
        private long buildPending(Object receiver,Build build,Module module){
            long known=sourceVertices(build.vertices,module,null,false);if(receiver==null||known==0||build.indexOnly)return 0;try{Class<?> type=actualBase(receiver,"com.mojang.blaze3d.vertex.BufferBuilder");if(type!=null&&(!(Boolean)field(receiver,type,"building")||number(receiver,type,"vertices")==0))return 0;return sourceVertices(build.vertices,module,cpuReadback(receiver,build,null),true);}catch(ReflectiveOperationException|RuntimeException unavailable){return known;}
        }
        private long cpuPending(Object receiver,CpuImage cpu,Module module){
            long known=sourceVertices(cpu.image.vertices(),module,null,false);if(receiver==null||cpu.finished||known==0||cpu.image.indexOnly())return 0;try{Class<?> type=actualBase(receiver,"com.mojang.blaze3d.vertex.BufferBuilder$RenderedBuffer");if(type!=null&&(Boolean)field(receiver,type,"released")){cpu.finished=true;return 0;}return sourceVertices(cpu.image.vertices(),module,cpuReadback(receiver,null,cpu),true);}catch(ReflectiveOperationException|RuntimeException unavailable){return known;}
        }
        private long sortPending(Object receiver,SortImage saved,Module module){
            long known=sourceVertices(saved.image.vertices(),module,null,false);if(receiver==null||known==0)return 0;try{Class<?> type=actualBase(receiver,"com.mojang.blaze3d.vertex.BufferBuilder$SortState");if(type==null)type=actualBase(receiver,"com.mojang.blaze3d.vertex.BufferBuilder");if(type==null)return known;Object data=field(receiver,type,"sortingPoints");if(data==null)return 0;if(!(data instanceof Object[] points)||!data.getClass().descriptorString().equals("[Lorg/joml/Vector3f;"))return known;List<Module[]> rows=saved.image.vertices().subList(0,Math.min(saved.image.vertices().size(),Math.multiplyExact(points.length,4)));known=sourceVertices(rows,module,null,false);if(data!=saved.points.get()||!sortIdentity(receiver,saved))return known;BitSet erased=new BitSet();for(int n=saved.erased.nextSetBit(0);n>=0;n=saved.erased.nextSetBit(n+1))if(n<points.length&&points[n]==saved.identities[n].get()&&Arrays.stream(pointBits(points[n],data.getClass().getComponentType())).allMatch(value->value==0))erased.set(n*4,n*4+4);return sourceVertices(rows,module,erased,true);}catch(ReflectiveOperationException|RuntimeException unavailable){return known;}
        }
        private boolean gpuIdentity(Object receiver,Uploaded gpu)throws ReflectiveOperationException{Class<?> type=receiver==null?null:actualBase(receiver,"com.mojang.blaze3d.vertex.VertexBuffer");return type!=null
                &&number(receiver,type,"vertexBufferId")==gpu.vertexId&&number(receiver,type,"indexBufferId")==gpu.indexId&&number(receiver,type,"arrayObjectId")==gpu.vao
                &&field(receiver,type,"format")==gpu.image.format()&&field(receiver,type,"mode")==gpu.image.mode()&&number(receiver,type,"indexCount")==gpu.image.indices().length;}
        synchronized long pending(Module module){
            builds.entrySet().removeIf(entry->entry.getKey().get()==null);rendered.entrySet().removeIf(entry->entry.getKey().get()==null);sorting.entrySet().removeIf(entry->entry.getKey().get()==null);buildSorting.entrySet().removeIf(entry->entry.getKey().get()==null);long count=0;
            for(var entry:builds.entrySet())count+=buildPending(entry.getKey().get(),entry.getValue(),module);
            for(var entry:rendered.entrySet())count+=cpuPending(entry.getKey().get(),entry.getValue(),module);
            for(var entry:sortRecords())count+=sortPending(entry.getKey().get(),entry.getValue(),module);
            for(var entry:uploaded.entrySet()){Uploaded gpu=entry.getValue();if(gpu.image==null||gpu.storageReleased)continue;boolean complete=false;
                try{complete=!gpu.uploadPending&&gpu.image.pending().isEmpty()&&gpu.commandsDrained&&gpu.fence==0&&gpuIdentity(entry.getKey().get(),gpu)
                        &&Set.of("","GPU_SHARED_BUFFER_OTHER_SHARES_RETAINED","GPU_PAYLOAD_RETIREMENT_PENDING").contains(gpu.pending);}
                catch(ReflectiveOperationException|RuntimeException unavailable){complete=false;}
                count+=sourceVertices(gpu.image.vertices(),module,gpu.erased,complete);
            }
            return count;
        }
        synchronized String gap(Module module){
            for(var entry:rendered.entrySet()){CpuImage cpu=entry.getValue();if(cpuPending(entry.getKey().get(),cpu,module)!=0)return cpu.pending.isEmpty()?"GPU_CPU_LIVE_PAYLOAD_RETIREMENT_PENDING":cpu.pending;}
            for(var entry:sortRecords()){SortImage image=entry.getValue();if(sortPending(entry.getKey().get(),image,module)!=0)return image.pending.isEmpty()?"GPU_CPU_SORTING_POINTS_RETIREMENT_PENDING":image.pending;}
            for(var entry:builds.entrySet()){Build build=entry.getValue();if(buildPending(entry.getKey().get(),build,module)!=0)return !build.retirementPending.isEmpty()?build.retirementPending:build.pending.isEmpty()?"GPU_CPU_ACTIVE_BATCH_RETIREMENT_PENDING":build.pending;}
            for(Uploaded gpu:uploaded.values())if(gpu.image!=null&&!gpu.storageReleased&&sourceVertices(gpu.image.vertices(),module,gpu.erased,gpu.commandsDrained&&gpu.fence==0)!=0)return gpu.pending.isEmpty()?"GPU_PAYLOAD_RETIREMENT_PENDING":gpu.pending;
            return "GPU_SOURCE_RECEIPT_READBACK_PENDING";
        }
        synchronized void reap(Key key){builds.remove(key);rendered.remove(key);sorting.remove(key);buildSorting.remove(key);uploaded.remove(key);}
        Object[] controls(){return new Object[]{this,builds,rendered,sorting,buildSorting,uploaded,contexts,releasingCpu,maintainingCpu,clearingCpu,clearingSort};}
    }
    public static boolean soundAdmitted(Object sound){
        var frame=boundary();if(!clientMember(frame,"net.minecraft.client.sounds.SoundEngine","play","m_120312_","(Lnet/minecraft/client/resources/sounds/SoundInstance;)V")&&!clientMember(frame,"net.minecraft.client.sounds.SoundEngine","playDelayed","m_120276_","(Lnet/minecraft/client/resources/sounds/SoundInstance;I)V")&&!clientMember(frame,"net.minecraft.client.sounds.SoundEngine","queueTickingSound","m_120282_","(Lnet/minecraft/client/resources/sounds/TickableSoundInstance;)V"))throw new SecurityException("ACTUAL_SOUND_ADMISSION_REQUIRED");
        record(sound,union(origins(sound),ControlBridge.invokingSources()));return !stopped(sources(sound))&&!ControlBridge.stoppedEffects();
    }
    private static void removeSoundRows(Object engine,Class<?> type,Object sound)throws ReflectiveOperationException{
        for(String name:List.of("queuedSounds","soundDeleteTime")){Map<?,?> rows=(Map<?,?>)field(engine,type,name);rows.keySet().removeIf(key->key==sound);}
        for(String name:List.of("tickingSounds","queuedTickableSounds")){Collection<?> rows=(Collection<?>)field(engine,type,name);rows.removeIf(value->value==sound);}
        Object bySource=field(engine,type,"instanceBySource");Class<?> multimap=Class.forName("com.google.common.collect.Multimap",false,type.getClassLoader());
        Collection<?> rows=(Collection<?>)multimap.getMethod("values").invoke(bySource);rows.removeIf(value->value==sound);
    }
    private static Object soundHandle(Map<?,?> instances,Object sound){for(var entry:instances.entrySet())if(entry.getKey()==sound)return entry.getValue();return null;}
    private static void removeSoundHandle(Map<?,?> instances,Object sound,Object handle){instances.entrySet().removeIf(entry->entry.getKey()==sound&&entry.getValue()==handle);}
    public static void soundListenerRegistered(Object engine,Object listener){
        var frame=boundary();if(!clientMember(frame,"net.minecraft.client.sounds.SoundEngine","addEventListener","m_120295_","(Lnet/minecraft/client/sounds/SoundEventListener;)V"))throw new SecurityException("ACTUAL_SOUND_LISTENER_REGISTRATION_REQUIRED");
        try{Map<?,?> instances=(Map<?,?>)field(engine,frame.getDeclaringClass(),"instanceToChannel");Collection<?> listeners=(Collection<?>)field(engine,frame.getDeclaringClass(),"listeners");
            if(listeners.stream().anyMatch(value->value==listener))record(listener,union(origins(listener),ControlBridge.invokingSources(),currentSources()));
            synchronized(SOURCES){SOUNDS.put(new Key(engine,true),new SoundRegistry(new Ref<>(instances),new Ref<>(listeners)));}
        }catch(ReflectiveOperationException unavailable){throw new IllegalStateException("ACTUAL_SOUND_LISTENER_REGISTRY_UNAVAILABLE",unavailable);}
    }
    private static boolean sameSoundChannel(Object handle,Object candidate,Object channel)throws ReflectiveOperationException{
        if(handle==candidate)return true;if(channel==null||candidate==null)return false;Class<?> type=actualBase(candidate,"net.minecraft.client.sounds.ChannelAccess$ChannelHandle");return type!=null&&field(candidate,type,"channel")==channel;
    }
    public static void soundTick(Object engine){
        var frame=boundary();if(!clientMember(frame,"net.minecraft.client.sounds.SoundEngine","tick","m_120302_","(Z)V"))throw new SecurityException("ACTUAL_SOUND_TICK_REQUIRED");
        synchronized(SOURCES){if(REQUESTED.isEmpty())return;}
        try{
            Class<?> type=frame.getDeclaringClass();Map<?,?> instances=(Map<?,?>)field(engine,type,"instanceToChannel");Object access=field(engine,type,"channelAccess");
            Class<?> accessType=actualBase(access,"net.minecraft.client.sounds.ChannelAccess");if(accessType==null||access.getClass()!=accessType)throw new IllegalStateException("ACTUAL_SOUND_CHANNEL_ACCESS_CHANGED");
            Collection<?> listeners=(Collection<?>)field(engine,type,"listeners");retireListeners(listeners);
            synchronized(SOURCES){SOUNDS.put(new Key(engine,true),new SoundRegistry(new Ref<>(instances),new Ref<>(listeners)));}
            Set<Object> queued=Collections.newSetFromMap(new IdentityHashMap<>());
            queued.addAll(((Map<?,?>)field(engine,type,"queuedSounds")).keySet());queued.addAll((Collection<?>)field(engine,type,"queuedTickableSounds"));
            for(Object sound:queued){Module[] owners=sources(sound);if(!stopped(owners))continue;
                try{removeSoundRows(engine,type,sound);if(soundHandle(instances,sound)==null)synchronized(SOURCES){FAILURES.remove(new Key(sound,false));}}
                catch(ReflectiveOperationException|RuntimeException unavailable){uiFailure(sound,owners,unavailable);}}
            for(var entry:new ArrayList<>(instances.entrySet())){Object sound=entry.getKey();Module[] owners=sources(sound);if(!stopped(owners))continue;
                try{
                Object handle=entry.getValue();Class<?> handleType=actualBase(handle,"net.minecraft.client.sounds.ChannelAccess$ChannelHandle");if(handleType==null||handle.getClass()!=handleType)throw new IllegalStateException("ACTUAL_SOUND_HANDLE_CHANGED");
                Object channel=field(handle,handleType,"channel");Class<?> channelType=channel==null?null:actualBase(channel,"com.mojang.blaze3d.audio.Channel");
                if(channel!=null&&(channelType==null||channel.getClass()!=channelType))throw new IllegalStateException("ACTUAL_SOUND_CHANNEL_CHANGED");
                if(field(handle,handleType,"this$0")!=access)throw new IllegalStateException("ACTUAL_SOUND_HANDLE_ACCESS_CHANGED");
                // A physical channel shared by any live outside registration must survive the selected detach.
                boolean sharedChannel=!retiredSoundSources(union(owners,origins(handle),origins(channel)));List<Module[]> shares=new ArrayList<>();shares.add(owners);shares.add(origins(handle));shares.add(origins(channel));
                for(var peer:instances.entrySet())if(sameSoundChannel(handle,peer.getValue(),channel)){Module[] peerOwners=sources(peer.getKey());shares.add(peerOwners);if(peer.getKey()!=sound&&!stopped(peerOwners))sharedChannel=true;}
                for(Object peer:(Collection<?>)field(access,accessType,"channels"))if(peer!=handle&&sameSoundChannel(handle,peer,channel)&&!retiredSoundSources(sources(peer)))sharedChannel=true;
                int source=channel==null?-1:((Number)field(channel,channelType,"source")).intValue();Object initialized=channel==null?null:field(channel,channelType,"initialized"),library=field(access,accessType,"library");Class<?> libraryType=actualBase(library,"com.mojang.blaze3d.audio.Library");if(libraryType==null||library.getClass()!=libraryType)throw new IllegalStateException("ACTUAL_SOUND_LIBRARY_CHANGED");long context=((Number)field(library,libraryType,"context")).longValue();Module[] selected=union(shares.toArray(Module[][]::new));SoundStop stop;
                Object executor=field(access,accessType,"executor");if(!(executor instanceof java.util.concurrent.Executor originalExecutor))throw new IllegalStateException("ACTUAL_SOUND_EXECUTOR_REQUIRED");
                synchronized(SOURCES){stop=SOUND_STOPS.get(new Key(handle,false));if(stop==null){stop=new SoundStop(engine,type,instances,handle,channel,access,handleType,channelType,accessType,selected,source,initialized,sharedChannel,library,libraryType,context,originalExecutor);SOUND_STOPS.put(new Key(handle,true),stop);CodeSourceBridge.contributionControl(stop);CodeSourceBridge.contributionControl(stop.modules);CodeSourceBridge.contributionControl(stop.sounds);}
                    if(stop.engine!=engine||stop.access!=access||stop.channel!=channel||stop.executor!=originalExecutor)throw new IllegalStateException("CLIENT_ORIGINAL_SOUND_ASSOCIATION_CHANGED");stop.sounds.add(sound);}
                stop.shared=sharedChannel;if(sharedChannel)stop.pending="CLIENT_SHARED_SOUND_CHANNEL_RETAINED";
                removeSoundHandle(instances,sound,handle);removeSoundRows(engine,type,sound);
                }catch(ReflectiveOperationException|RuntimeException unavailable){uiFailure(sound,owners,unavailable);}
            }
            List<SoundStop> pending;synchronized(SOURCES){pending=new ArrayList<>(SOUND_STOPS.values());}
            for(SoundStop stop:pending)try{
                if(!soundIdentity(stop)){stop.pending="CLIENT_ORIGINAL_SOUND_ASSOCIATION_CHANGED";continue;}
                // Detached engine rows are retained by the original stop until their exact cleanup succeeds.
                Map<?,?> original=(Map<?,?>)field(stop.engine,stop.engineType,"instanceToChannel");
                for(Object sound:stop.sounds){Object replacement=soundHandle(original,sound);if(replacement==null||replacement==stop.handle){removeSoundHandle(original,sound,stop.handle);removeSoundRows(stop.engine,stop.engineType,sound);}}
                Object current=field(stop.handle,stop.handleType,"channel");boolean released=(Boolean)field(stop.handle,stop.handleType,"stopped");Collection<?> handles=(Collection<?>)field(stop.access,stop.accessType,"channels");
                if(!stop.requested.get()&&current==null&&released&&!handles.contains(stop.handle)&&(stop.channel==null||stop.deleted.get())){
                    synchronized(SOURCES){SOUND_STOPS.remove(new Key(stop.handle,false));for(Object sound:stop.sounds)if(soundHandle(original,sound)==null)FAILURES.remove(new Key(sound,false));retired++;}
                }else requestSoundStop(stop);
            }catch(ReflectiveOperationException|RuntimeException unavailable){stop.pending="CLIENT_SOUND_RETIREMENT_UNAVAILABLE:"+unavailable.getClass().getSimpleName();}
            synchronized(SOURCES){FAILURES.remove(new Key(engine,false));}
        }catch(ReflectiveOperationException|RuntimeException unavailable){synchronized(SOURCES){FAILURES.put(new Key(engine,true),union(ControlBridge.invokingSources(),REQUESTED.toArray(Module[]::new)));}}
    }
    private static boolean retiredSoundSources(Module[] modules){if(modules.length==0)return false;synchronized(SOURCES){for(Module module:modules)if(!REQUESTED.contains(module)&&!ControlBridge.modStopped(module))return false;}return true;}
    private static boolean soundIdentity(SoundStop stop)throws ReflectiveOperationException{return field(stop.engine,stop.engineType,"channelAccess")==stop.access&&field(stop.engine,stop.engineType,"instanceToChannel")==stop.instances&&field(stop.handle,stop.handleType,"this$0")==stop.access
            &&field(stop.access,stop.accessType,"executor")==stop.executor&&field(stop.access,stop.accessType,"library")==stop.library&&((Number)field(stop.library,stop.libraryType,"context")).longValue()==stop.context
            &&(stop.channel==null||((Number)field(stop.channel,stop.channelType,"source")).intValue()==stop.source&&field(stop.channel,stop.channelType,"initialized")==stop.initialized);}
    private static boolean soundContext(SoundStop stop)throws ReflectiveOperationException{Class<?> type=Class.forName("org.lwjgl.openal.ALC10",false,stop.libraryType.getClassLoader());Method member=type.getMethod("alcGetCurrentContext");if(!Modifier.isStatic(member.getModifiers())||member.getReturnType()!=long.class)throw new IllegalStateException("ACTUAL_OPENAL_CONTEXT_QUERY_REQUIRED");return stop.context!=0&&((Number)member.invoke(null)).longValue()==stop.context;}
    private static void requestSoundStop(SoundStop stop)throws ReflectiveOperationException{
        if(stop.requested.get())return;
        Object current=field(stop.handle,stop.handleType,"channel");
        if(!soundIdentity(stop)||current!=null&&current!=stop.channel){stop.pending="CLIENT_ORIGINAL_SOUND_CHANNEL_CHANGED";return;}
        if(!stop.requested.compareAndSet(false,true))return;
        Runnable action=()->{
            try{
                if(!soundIdentity(stop)||!soundContext(stop)){stop.pending="CLIENT_ORIGINAL_SOUND_CONTEXT_CHANGED";return;}
                Object channel=field(stop.handle,stop.handleType,"channel");
                if(channel!=null&&channel!=stop.channel){stop.pending="CLIENT_ORIGINAL_SOUND_CHANNEL_CHANGED";return;}
                boolean sharedChannel=!retiredSoundSources(union(stop.modules,origins(stop.channel),origins(stop.handle)));
                Map<?,?> instances=(Map<?,?>)field(stop.engine,stop.engineType,"instanceToChannel");
                for(var peer:instances.entrySet())if(sameSoundChannel(stop.handle,peer.getValue(),stop.channel)&&!retiredSoundSources(sources(peer.getKey())))sharedChannel=true;
                for(Object peer:(Collection<?>)field(stop.access,stop.accessType,"channels"))if(peer!=stop.handle&&sameSoundChannel(stop.handle,peer,stop.channel)&&!retiredSoundSources(sources(peer)))sharedChannel=true;
                stop.shared=sharedChannel;if(sharedChannel){stop.pending="CLIENT_SHARED_SOUND_CHANNEL_RETAINED";return;}
                if(stop.channel!=null&&!stop.deleted.get()&&!((java.util.concurrent.atomic.AtomicBoolean)stop.initialized).get())soundDeleteReadback(stop);
                if(channel==stop.channel&&channel!=null&&!stop.deleted.get()&&!stop.stopped.get()){
                    int state=((Number)clientMethod(stop.channelType,"getState","m_83683_","()I").invoke(channel)).intValue();
                    if(state!=4116&&state!=4113){platformVoid(channel,stop.channelType,"stop");state=((Number)clientMethod(stop.channelType,"getState","m_83683_","()I").invoke(channel)).intValue();}
                    stop.stopped.set(state==4116||state==4113);
                }
                if(channel==stop.channel&&channel!=null&&stop.stopped.get()){
                    if(!stop.releaseStarted)platformVoid(stop.handle,stop.handleType,"release");
                    else if(!stop.releaseCompleted)stop.pending="CLIENT_SOUND_ORIGINAL_RELEASE_OUTCOME_PENDING";
                }
                if(stop.channel!=null&&!stop.deleted.get()&&!((java.util.concurrent.atomic.AtomicBoolean)stop.initialized).get())soundDeleteReadback(stop);
                Object after=field(stop.handle,stop.handleType,"channel");boolean released=(Boolean)field(stop.handle,stop.handleType,"stopped");
                if(after==null&&released&&(stop.channel==null||stop.deleted.get())){
                    Collection<?> handles=(Collection<?>)field(stop.access,stop.accessType,"channels");handles.removeIf(handle->handle==stop.handle);
                    stop.stopped.set(true);stop.pending=handles.stream().anyMatch(handle->handle==stop.handle)?"CLIENT_SOUND_HANDLE_DETACH_PENDING":"";
                }else if(stop.pending.isEmpty())stop.pending="CLIENT_SOUND_ORIGINAL_RELEASE_PENDING";
            }catch(Throwable unavailable){stop.pending="CLIENT_SOUND_STOP:"+unavailable.getClass().getSimpleName();}
            finally{stop.requested.set(false);}
        };
        try{stop.executor.execute(action);}
        catch(RuntimeException unavailable){stop.requested.set(false);throw unavailable;}
    }
    public static Object soundReleaseEntering(Object handle){
        var frame=boundary();if(!clientMember(frame,"net.minecraft.client.sounds.ChannelAccess$ChannelHandle","release","m_120156_","()V"))throw new SecurityException("ACTUAL_SOUND_HANDLE_RELEASE_REQUIRED");SoundStop stop;synchronized(SOURCES){stop=SOUND_STOPS.get(new Key(handle,false));}if(stop==null)return null;
        try{if(stop.handleType!=frame.getDeclaringClass()||field(handle,stop.handleType,"channel")!=stop.channel||field(handle,stop.handleType,"this$0")!=stop.access||!soundIdentity(stop)){stop.pending="CLIENT_SOUND_RELEASE_ASSOCIATION_CHANGED";throw new IllegalStateException(stop.pending);}
            SoundRelease scope=new SoundRelease(stop);CodeSourceBridge.contributionControl(scope);SOUND_RELEASING.set(scope);stop.releaseStarted=true;return scope;
        }catch(ReflectiveOperationException unavailable){stop.pending="CLIENT_SOUND_RELEASE_READBACK_UNAVAILABLE";throw new IllegalStateException(stop.pending,unavailable);}
    }
    public static void soundReleaseExiting(Object token,boolean completed){
        var frame=boundary();if(!clientMember(frame,"net.minecraft.client.sounds.ChannelAccess$ChannelHandle","release","m_120156_","()V"))throw new SecurityException("ACTUAL_SOUND_HANDLE_RELEASE_REQUIRED");if(token==null)return;
        if(!(token instanceof SoundRelease scope)||scope.thread!=Thread.currentThread()||scope.ended||scope.stop.handleType!=frame.getDeclaringClass()||SOUND_RELEASING.get()!=scope)throw new SecurityException("ACTUAL_SOUND_RELEASE_TOKEN_REQUIRED");
        try{if(completed){scope.stop.releaseCompleted=true;if(field(scope.stop.handle,scope.stop.handleType,"channel")==null&&(Boolean)field(scope.stop.handle,scope.stop.handleType,"stopped"))scope.stop.pending=scope.stop.deleted.get()?"CLIENT_SOUND_HANDLE_DETACH_PENDING":"CLIENT_SOUND_NATIVE_DELETE_READBACK_PENDING";}}
        catch(ReflectiveOperationException unavailable){scope.stop.pending="CLIENT_SOUND_HANDLE_RELEASE_READBACK_UNAVAILABLE";}
        finally{scope.ended=true;if(scope.previous==null)SOUND_RELEASING.remove();else SOUND_RELEASING.set(scope.previous);}
    }
    public static void soundChannelDestroyed(Object channel){
        var frame=boundary();if(!clientMember(frame,"com.mojang.blaze3d.audio.Channel","destroy","m_83665_","()V"))throw new SecurityException("ACTUAL_SOUND_CHANNEL_DESTROY_REQUIRED");List<SoundStop> stops;synchronized(SOURCES){stops=new ArrayList<>(SOUND_STOPS.values());}
        for(SoundStop stop:stops)if(stop.channel==channel)try{if(stop.channelType!=frame.getDeclaringClass()){stop.pending="CLIENT_SOUND_NATIVE_DELETE_ASSOCIATION_CHANGED";continue;}soundDeleteReadback(stop);
        }catch(ReflectiveOperationException|RuntimeException unavailable){stop.pending="CLIENT_SOUND_NATIVE_DELETE_READBACK_UNAVAILABLE";}
    }
    private static void soundDeleteReadback(SoundStop stop)throws ReflectiveOperationException{
        if(!soundIdentity(stop)||!soundContext(stop)||((java.util.concurrent.atomic.AtomicBoolean)stop.initialized).get()||field(stop.channel,stop.channelType,"stream")!=null){stop.pending="CLIENT_SOUND_NATIVE_DELETE_ASSOCIATION_CHANGED";return;}
        if(stop.deleted.get())return;
        Class<?> api=Class.forName("org.lwjgl.openal.AL10",false,stop.channelType.getClassLoader());Method member=api.getMethod("alIsSource",int.class);if(!Modifier.isStatic(member.getModifiers())||member.getReturnType()!=boolean.class)throw new IllegalStateException("ACTUAL_OPENAL_SOURCE_QUERY_REQUIRED");
        boolean exists=(Boolean)member.invoke(null,stop.source);stop.deleted.set(!exists);if(!exists)stop.stopped.set(true);stop.pending=exists?"CLIENT_SOUND_NATIVE_DELETE_READBACK_PENDING":"CLIENT_SOUND_HANDLE_DETACH_PENDING";
    }
    private static boolean soundCleanupField(Object receiver,Class<?> declaring,String name){
        SoundRelease scope=SOUND_RELEASING.get();if(scope==null||scope.ended||scope.thread!=Thread.currentThread())return false;SoundStop stop=scope.stop;
        try{if(!soundIdentity(stop)||field(stop.handle,stop.handleType,"this$0")!=stop.access)return false;Object current=field(stop.handle,stop.handleType,"channel");if(current!=stop.channel&&current!=null)return false;}
        catch(ReflectiveOperationException|RuntimeException changed){return false;}
        boolean original=WALKER.walk(frames->frames.anyMatch(frame->frame.getDeclaringClass()==stop.handleType&&clientMember(frame,"net.minecraft.client.sounds.ChannelAccess$ChannelHandle","release","m_120156_","()V")));if(!original)return false;
        if(receiver==stop.handle&&declaring==stop.handleType)return Set.of("stopped","f_120147_","channel","f_120146_").contains(name);
        return receiver==stop.channel&&declaring==stop.channelType&&Set.of("stream","f_83645_").contains(name)
                &&WALKER.walk(frames->frames.anyMatch(frame->frame.getDeclaringClass()==stop.channelType&&clientMember(frame,"com.mojang.blaze3d.audio.Channel","destroy","m_83665_","()V")));
    }
    private static void removeParticle(Object particle){
        if(particle==null)return;
        for(Class<?> type=particle.getClass();type!=null;type=type.getSuperclass())if(role(type)&&type.getName().equals("net.minecraft.client.particle.Particle")){
            Object prior=RELEASING.get();RELEASING.set(particle);
            try{resourceField(type,"removed").setBoolean(particle,true);synchronized(SOURCES){FAILURES.remove(new Key(particle,false));}}
            catch(ReflectiveOperationException unavailable){synchronized(SOURCES){FAILURES.put(new Key(particle,true),sources(particle));failed++;}}
            finally{if(prior==null)RELEASING.remove();else RELEASING.set(prior);}return;
        }
        synchronized(SOURCES){FAILURES.put(new Key(particle,true),sources(particle));failed++;}
    }
    /** Exact official/SRG fields for the observed allocation, registry and vertex metadata. */
    private static Field resourceField(Class<?> type,String name)throws ReflectiveOperationException{
        String alternate=null,descriptor=null;
        if(type.getName().equals("com.mojang.blaze3d.platform.NativeImage")){
            if(name.equals("pixels")){alternate="f_84964_";descriptor="J";}
            else if(name.equals("size")){alternate="f_84965_";descriptor="J";}
        }else if(type.getName().equals("net.minecraft.client.renderer.texture.DynamicTexture")&&name.equals("pixels")){
            alternate="f_117977_";descriptor="Lcom/mojang/blaze3d/platform/NativeImage;";
        }else if(type.getName().equals("net.minecraft.client.particle.Particle")&&name.equals("removed")){
            alternate="f_107220_";descriptor="Z";
        }else if(type.getName().equals("net.minecraft.client.renderer.texture.TextureManager")){
            if(name.equals("byPath")){alternate="f_118468_";descriptor="Ljava/util/Map;";}
            else if(name.equals("tickableTextures")){alternate="f_118469_";descriptor="Ljava/util/Set;";}
        }else if(type.getName().equals("net.minecraft.client.renderer.texture.AbstractTexture")&&name.equals("id")){
            alternate="f_117950_";descriptor="I";
        }else if(type.getName().equals("net.minecraft.client.renderer.texture.HttpTexture")&&name.equals("future")){
            alternate="f_117998_";descriptor="Ljava/util/concurrent/CompletableFuture;";
        }else if(type.getName().equals("com.mojang.blaze3d.vertex.BufferBuilder")){
            if(name.equals("mode")){alternate="f_85657_";descriptor="Lcom/mojang/blaze3d/vertex/VertexFormat$Mode;";}
            else if(name.equals("format")){alternate="f_85658_";descriptor="Lcom/mojang/blaze3d/vertex/VertexFormat;";}
            else if(name.equals("vertices")){alternate="f_85654_";descriptor="I";}
            else if(name.equals("buffer")){alternate="f_85648_";descriptor="Ljava/nio/ByteBuffer;";}
            else if(name.equals("building")){alternate="f_85661_";descriptor="Z";}
            else if(name.equals("renderedBufferCount")){alternate="f_231156_";descriptor="I";}
            else if(name.equals("renderedBufferPointer")){alternate="f_231157_";descriptor="I";}
            else if(name.equals("nextElementByte")){alternate="f_85652_";descriptor="I";}
            else if(name.equals("indexOnly")){alternate="f_166762_";descriptor="Z";}
            else if(name.equals("sortingPoints")){alternate="f_166766_";descriptor="[Lorg/joml/Vector3f;";}
        }else if(type.getName().equals("com.mojang.blaze3d.vertex.BufferBuilder$RenderedBuffer")){
            if(name.equals("drawState")){alternate="f_231190_";descriptor="Lcom/mojang/blaze3d/vertex/BufferBuilder$DrawState;";}
            else if(name.equals("pointer")){alternate="f_231189_";descriptor="I";}
            else if(name.equals("released")){alternate="f_231191_";descriptor="Z";}
            else if(name.equals("this$0")){alternate="f_231188_";descriptor="Lcom/mojang/blaze3d/vertex/BufferBuilder;";}
        }else if(type.getName().equals("com.mojang.blaze3d.vertex.BufferBuilder$SortState")){
            if(name.equals("sortingPoints")){alternate="f_166819_";descriptor="[Lorg/joml/Vector3f;";}
            else if(name.equals("vertices")){alternate="f_166818_";descriptor="I";}
            else if(name.equals("mode")){alternate="f_166817_";descriptor="Lcom/mojang/blaze3d/vertex/VertexFormat$Mode;";}
        }else if(type.getName().equals("com.mojang.blaze3d.vertex.VertexBuffer")){
            if(name.equals("format")){alternate="f_85917_";descriptor="Lcom/mojang/blaze3d/vertex/VertexFormat;";}
            else if(name.equals("mode")){alternate="f_166864_";descriptor="Lcom/mojang/blaze3d/vertex/VertexFormat$Mode;";}
            else if(name.equals("indexCount")){alternate="f_166863_";descriptor="I";}
            else if(name.equals("vertexBufferId")){alternate="f_231217_";descriptor="I";}
            else if(name.equals("indexBufferId")){alternate="f_166860_";descriptor="I";}
            else if(name.equals("arrayObjectId")){alternate="f_166862_";descriptor="I";}
        }else if(type.getName().equals("com.mojang.blaze3d.vertex.VertexFormat$Mode")&&name.equals("asGLMode")){
            alternate="f_166946_";descriptor="I";
        }else if(type.getName().equals("com.mojang.blaze3d.vertex.VertexFormat$IndexType")&&name.equals("bytes")){
            alternate="f_166924_";descriptor="I";
        }else if(type.getName().equals("net.minecraft.client.sounds.SoundEngine")){
            if(name.equals("instanceToChannel")){alternate="f_120226_";descriptor="Ljava/util/Map;";}
            else if(name.equals("channelAccess")){alternate="f_120224_";descriptor="Lnet/minecraft/client/sounds/ChannelAccess;";}
            else if(name.equals("queuedSounds")){alternate="f_120229_";descriptor="Ljava/util/Map;";}
            else if(name.equals("soundDeleteTime")){alternate="f_120230_";descriptor="Ljava/util/Map;";}
            else if(name.equals("tickingSounds")){alternate="f_120228_";descriptor="Ljava/util/List;";}
            else if(name.equals("queuedTickableSounds")){alternate="f_120232_";descriptor="Ljava/util/List;";}
            else if(name.equals("instanceBySource")){alternate="f_120227_";descriptor="Lcom/google/common/collect/Multimap;";}
            else if(name.equals("listeners")){alternate="f_120231_";descriptor="Ljava/util/List;";}
        }else if(type.getName().equals("net.minecraft.client.sounds.ChannelAccess")){
            if(name.equals("channels")){alternate="f_120121_";descriptor="Ljava/util/Set;";}
            else if(name.equals("executor")){alternate="f_120123_";descriptor="Ljava/util/concurrent/Executor;";}
            else if(name.equals("library")){alternate="f_120122_";descriptor="Lcom/mojang/blaze3d/audio/Library;";}
        }else if(type.getName().equals("net.minecraft.client.sounds.ChannelAccess$ChannelHandle")){
            if(name.equals("channel")){alternate="f_120146_";descriptor="Lcom/mojang/blaze3d/audio/Channel;";}
            else if(name.equals("stopped")){alternate="f_120147_";descriptor="Z";}
            else if(name.equals("this$0")){alternate="f_120145_";descriptor="Lnet/minecraft/client/sounds/ChannelAccess;";}
        }else if(type.getName().equals("com.mojang.blaze3d.audio.Channel")){
            if(name.equals("source")){alternate="f_83642_";descriptor="I";}
            else if(name.equals("initialized")){alternate="f_83643_";descriptor="Ljava/util/concurrent/atomic/AtomicBoolean;";}
            else if(name.equals("stream")){alternate="f_83645_";descriptor="Lnet/minecraft/client/sounds/AudioStream;";}
        }else if(type.getName().equals("com.mojang.blaze3d.audio.Library")&&name.equals("context")){
            alternate="f_83687_";descriptor="J";
        }else if(type.getName().equals("net.minecraft.server.packs.resources.ReloadableResourceManager")&&name.equals("listeners")){
            alternate="f_203816_";descriptor="Ljava/util/List;";
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
        try{long pointer=((Number)field(image,frame.getDeclaringClass(),"pixels")).longValue();if(record.closed?pointer!=0:pointer!=record.pointer)throw new IllegalStateException("CLIENT_ORIGINAL_PIXEL_ALLOCATION_CHANGED");record.closeEntered=true;return record;}
        catch(ReflectiveOperationException|RuntimeException unavailable){record.gate.unlock();throw new IllegalStateException("CLIENT_PIXEL_RELEASE_REFUSED",unavailable);}
    }
    public static void imageCloseExit(Object image,Object token,boolean completed){
        var frame=boundary();if(!frame.getMethodName().equals("close")||!frame.getDeclaringClass().getName().equals("com.mojang.blaze3d.platform.NativeImage"))throw new SecurityException("ACTUAL_PIXEL_CLOSE_REQUIRED");
        if(token==null)return;ImageRecord record;synchronized(SOURCES){record=IMAGES.get(new Key(image,false));}
        if(record!=token||!record.gate.isHeldByCurrentThread())throw new SecurityException("ACTUAL_PIXEL_CLOSE_TOKEN_REQUIRED");
        try{if(completed){record.closeReturned=true;if(((Number)field(image,frame.getDeclaringClass(),"pixels")).longValue()==0)synchronized(SOURCES){record.closed=true;}}}
        catch(ReflectiveOperationException unavailable){throw new IllegalStateException("CLIENT_PIXEL_RELEASE_READBACK_UNAVAILABLE",unavailable);}
        finally{record.gate.unlock();}
    }
    private static void platformVoid(Object object,Class<?> declaring,String method)throws Throwable{
        platformAction(object,declaring,method).invokeExact();
    }
    private static java.lang.invoke.MethodHandle platformAction(Object object,Class<?> declaring,String method)throws ReflectiveOperationException{
        java.lang.invoke.MethodHandles.Lookup access=java.lang.invoke.MethodHandles.privateLookupIn(declaring,java.lang.invoke.MethodHandles.lookup());
        String installed=declaring.getName().equals("net.minecraft.client.renderer.texture.AbstractTexture")&&method.equals("releaseId")?"m_117964_":method;
        if(declaring.getName().equals("com.mojang.blaze3d.vertex.BufferBuilder$RenderedBuffer")&&method.equals("release"))installed="m_231200_";
        if(declaring.getName().equals("com.mojang.blaze3d.audio.Channel")&&method.equals("stop"))installed="m_83679_";
        if(declaring.getName().equals("net.minecraft.client.sounds.ChannelAccess$ChannelHandle")&&method.equals("release"))installed="m_120156_";
        return access.findSpecial(declaring,clientMethod(declaring,method,installed,"()V").getName(),java.lang.invoke.MethodType.methodType(void.class),declaring).bindTo(object);
    }
    private static String releasePixels(TextureRetirement retirement,Class<?> dynamic)throws Throwable{
        Object texture=retirement.object,image=field(texture,dynamic,"pixels"),observed;ImageRecord record;boolean foreign=false;
        synchronized(SOURCES){
            if(image!=retirement.originalPixels){ImageRecord original=retirement.originalPixels==null?null:IMAGES.get(new Key(retirement.originalPixels,false));
                return image==null&&original!=null&&original.closed?"":"CLIENT_ORIGINAL_TEXTURE_PIXELS_CHANGED";}
            Ref<Object> actual=PIXELS.get(new Key(texture,false));observed=actual==null?null:actual.get();record=image==null?null:IMAGES.get(new Key(image,false));
            if(image!=observed)return "CLIENT_ORIGINAL_TEXTURE_PIXELS_CHANGED";
            if(image==null)return "";if(record==null)return "CLIENT_PIXEL_ALLOCATION_UNOBSERVED";
            if(record.unknownExposure)return "CLIENT_PIXEL_OUTSIDE_EXPOSURE_UNRESOLVED";
            foreign=!exclusive(union(record.exposures,origins(image)));
            // Empty producer sources do not erase the real associations of the observed framework image.
            if(record.exposures.length==0&&origins(image).length==0)foreign=false;
            for(var peer:PIXELS.entrySet())if(peer.getValue().get()==image){Object actualTexture=peer.getKey().get();if(actualTexture!=null&&actualTexture!=texture&&!exclusive(sources(actualTexture)))foreign=true;}
        }
        if(!foreign){Class<?> imageType=actualBase(image,"com.mojang.blaze3d.platform.NativeImage");if(imageType==null||image.getClass()!=imageType)return "CLIENT_PIXEL_CLOSE_IMPLEMENTATION_UNOBSERVED";
            record.gate.lock();try{
                long pointer=((Number)field(image,imageType,"pixels")).longValue();
                if(record.closed){if(pointer!=0)return "CLIENT_CLOSED_PIXEL_ALLOCATION_CHANGED";}
                else if(record.closeEntered){
                    if(pointer!=0)return record.closeReturned?"CLIENT_PIXEL_CLOSE_READBACK_PENDING":"CLIENT_PIXEL_CLOSE_OUTCOME_PENDING";
                    synchronized(SOURCES){record.closed=true;}
                }else{Object previous=RELEASING.get();RELEASING.set(image);try{platformVoid(image,imageType,"close");}finally{if(previous==null)RELEASING.remove();else RELEASING.set(previous);}}
                synchronized(SOURCES){if(!record.closed)return "CLIENT_PIXEL_RELEASE_RECEIPT_PENDING";}
            }finally{record.gate.unlock();}
        }
        Field pixels=resourceField(dynamic,"pixels");if(pixels.get(texture)!=image)return "CLIENT_TEXTURE_PIXELS_CHANGED_DURING_RELEASE";pixels.set(texture,null);
        if(pixels.get(texture)!=null)return "CLIENT_TEXTURE_PIXEL_DETACH_PENDING";synchronized(SOURCES){PIXELS.remove(new Key(texture,false));}return "";
    }
    private static String releaseTexture(TextureRetirement retirement)throws Throwable{
        if(!retirement.detached)return "CLIENT_TEXTURE_ALIAS_DETACH_PENDING";
        Object object=retirement.object;Class<?> base=actualBase(object,"net.minecraft.client.renderer.texture.AbstractTexture");if(base==null)return "ACTUAL_TEXTURE_BASE_UNAVAILABLE";
        Class<?> dynamic=actualBase(object,"net.minecraft.client.renderer.texture.DynamicTexture");Object previous=RELEASING.get();RELEASING.set(object);
        try{
            int current=((Number)field(object,base,"id")).intValue();
            if(current!=-1&&current!=retirement.originalId)return "CLIENT_ORIGINAL_TEXTURE_GPU_ID_CHANGED";
            if(dynamic!=null&&!retirement.pixelsReleased){String pending=releasePixels(retirement,dynamic);if(!pending.isEmpty())return pending;retirement.pixelsReleased=true;}
            if(current!=-1){
                if(retirement.idReleaseStarted)return "CLIENT_TEXTURE_GPU_RELEASE_OUTCOME_PENDING";
                java.lang.invoke.MethodHandle release=platformAction(object,base,"releaseId");retirement.idReleaseStarted=true;release.invokeExact();
            }
            if(((Number)field(object,base,"id")).intValue()!=-1)return "CLIENT_GPU_ID_RELEASE_PENDING";
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
        var frame=boundary();if(!clientMember(frame,"net.minecraft.client.renderer.texture.TextureManager","register","m_118495_","(Lnet/minecraft/resources/ResourceLocation;Lnet/minecraft/client/renderer/texture/AbstractTexture;)V"))throw new SecurityException("ACTUAL_TEXTURE_REGISTRATION_REQUIRED");
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
        var frame=boundary();if(!clientMember(frame,"net.minecraft.client.renderer.texture.TextureManager","tick","m_7673_","()V"))throw new SecurityException("ACTUAL_TEXTURE_TICK_REQUIRED");
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
                Class<?> base=actualBase(object,"net.minecraft.client.renderer.texture.AbstractTexture");if(base==null)throw new IllegalStateException("ACTUAL_TEXTURE_BASE_UNAVAILABLE");
                int originalId=((Number)field(object,base,"id")).intValue();
                Class<?> dynamic=actualBase(object,"net.minecraft.client.renderer.texture.DynamicTexture");Object originalPixels=dynamic==null?null:field(object,dynamic,"pixels");
                TextureRetirement retirement;synchronized(SOURCES){retirement=RETIREMENTS.computeIfAbsent(new Key(object,true),ignored->new TextureRetirement(object,union(texture.modules(),origins(object)),manager,registry,originalId,originalPixels));}
                retirement.pending=detachTexture(retirement);
            }catch(ReflectiveOperationException|RuntimeException unavailable){synchronized(SOURCES){TextureRetirement pending=RETIREMENTS.get(new Key(object,false));if(pending!=null)pending.pending="CLIENT_TEXTURE_DETACH:"+String.valueOf(unavailable.getMessage());}}
        }
        List<TextureRetirement> pending;synchronized(SOURCES){pending=new ArrayList<>(RETIREMENTS.values());}
        for(TextureRetirement retirement:pending)if(retirement.manager.get()==manager&&retirement.registry==registry)try{String gap=detachTexture(retirement);if(gap.isEmpty())gap=releaseTexture(retirement);synchronized(SOURCES){retirement.pending=gap;if(gap.isEmpty()){RETIREMENTS.remove(new Key(retirement.object,false));FAILURES.remove(new Key(retirement.object,false));retired++;}}}
        catch(Throwable unavailable){synchronized(SOURCES){retirement.pending="CLIENT_TEXTURE_RELEASE:"+unavailable.getClass().getSimpleName()+":"+String.valueOf(unavailable.getMessage());}}
        synchronized(SOURCES){shared=mixed;}
    }
    /** Only framework lifecycle fields of the exact object captured by the real retirement boundary. */
    static boolean cleanupField(Object receiver,Class<?> declaring,String name){
        if(RELEASING==null||!role(declaring))return false;if(widgetCleanupField(receiver,declaring,name)||soundCleanupField(receiver,declaring,name)||GPU!=null&&GPU.cleanupField(receiver,declaring,name))return true;
        if(RELEASING==null||RELEASING.get()!=receiver)return false;
        return declaring.getName().equals("net.minecraft.client.particle.Particle")&&Set.of("removed","f_107220_").contains(name)
                ||declaring.getName().equals("net.minecraft.client.renderer.texture.AbstractTexture")&&Set.of("id","f_117950_").contains(name)
                ||declaring.getName().equals("net.minecraft.client.renderer.texture.DynamicTexture")&&Set.of("pixels","f_117977_").contains(name)
                ||declaring.getName().equals("com.mojang.blaze3d.platform.NativeImage")&&Set.of("pixels","f_84964_").contains(name)
                ||declaring.getName().equals("com.mojang.blaze3d.vertex.VertexBuffer")&&Set.of("vertexBufferId","f_231217_","indexBufferId","f_166860_","arrayObjectId","f_166862_").contains(name);
    }
    public static void retire(Module[] modules){agent();synchronized(SOURCES){Collections.addAll(REQUESTED,modules);}retireReloadListeners();}
    public static String retirementGap(Module module){agent();long gpu=GPU.pending(module);String gpuState=gpu==0?"":GPU.gap(module);synchronized(SOURCES){reap();long active=ACTIVE.stream().filter(scope->Arrays.stream(scope.sources).anyMatch(source->source==module)).count();
        long textures=0;for(TextureRegistry registry:TEXTURES.values())for(Texture texture:registry.entries().values())if(texture.object().get()!=null&&Arrays.stream(texture.modules()).anyMatch(source->source==module))textures++;
        long resources=RETIREMENTS.values().stream().filter(row->Arrays.stream(row.modules).anyMatch(source->source==module)).count();
        long failures=FAILURES.values().stream().filter(row->Arrays.stream(row).anyMatch(source->source==module)).count();
        long sounds=SOUND_STOPS.values().stream().filter(row->Arrays.stream(row.modules).anyMatch(source->source==module)).count();
        String soundState="",reloadState="";
        for(SoundStop stop:SOUND_STOPS.values())if(Arrays.stream(stop.modules).anyMatch(source->source==module)&&!stop.pending.isEmpty()){soundState=stop.pending;break;}
        for(SoundRegistry registry:SOUNDS.values()){Collection<?> listeners=registry.listeners().get();if(listeners!=null){
            Module[] unavailable=FAILURES.get(new Key(listeners,false));if(unavailable!=null&&Arrays.stream(unavailable).anyMatch(source->source==module))soundState="CLIENT_LISTENER_COLLECTION_LAYOUT_UNSUPPORTED";
            for(Object listener:listeners)if(Arrays.stream(sources(listener)).anyMatch(source->source==module))sounds++;
        }}
        RELOAD_WORK.entrySet().removeIf(entry->entry.getValue().future().isDone());long reloads=RELOAD_WORK.values().stream().filter(row->Arrays.stream(row.modules()).anyMatch(source->source==module)).count();
        for(Ref<Collection<?>> reference:RELOAD_LISTENERS.values()){Collection<?> listeners=reference.get();if(listeners!=null){
            Module[] unavailable=FAILURES.get(new Key(listeners,false));if(unavailable!=null&&Arrays.stream(unavailable).anyMatch(source->source==module))reloadState="CLIENT_LISTENER_COLLECTION_LAYOUT_UNSUPPORTED";
            for(Object listener:listeners)if(Arrays.stream(sources(listener)).anyMatch(source->source==module))reloads++;
        }}
        long ui=UI_PENDING.values().stream().filter(row->Arrays.stream(sources(row.target)).anyMatch(source->source==module)).count();
        for(var entry:UI_OWNERS.entrySet()){Object owner=entry.getKey().get();if(owner==null)continue;
            try{for(boolean screen:new boolean[]{true,false}){Object current=entry.getValue().current(owner,screen);if(current!=null&&Arrays.stream(sources(current)).anyMatch(source->source==module))ui++;}}
            catch(ReflectiveOperationException unavailable){ui++;}
        }
        long renderCalls=renderPending(module),overlays=overlayPending(module);
        String overlayState=overlays==-1?"CLIENT_OVERLAY_ACTUAL_PAIR_READ_FAILED":overlays==-2?"CLIENT_OVERLAY_NAMED_ASSOCIATION_CHANGED":overlays==-3?"CLIENT_OVERLAY_SOURCE_UNKNOWN":"";
        long widgets=0,widgetShared=0,widgetUnknown=0;String widgetState="";
        for(ScreenRegistry registry:SCREEN_WIDGETS.values())for(WidgetSlot slot:registry.widgets.values()){
            Module[] modules=widgetSources(slot);if(Arrays.stream(modules).noneMatch(source->source==module))continue;Object owner=registry.owner.get(),widget=slot.widget.get();if(owner==null||widget==null)continue;
            try{
                int[] rows=widgetCounts(registry,widget);long retained=Arrays.stream(rows).asLongStream().sum();
                if(registry.fields.focused.get(owner)==widget)retained++;if(registry.fields.lastNarratable.get(owner)==widget)retained++;
                if(retained==0)continue;widgets+=retained;
                if(!Arrays.equals(rows,slot.counts)){slot.unknown=true;slot.pending="CLIENT_SCREEN_UNOBSERVED_WIDGET_ROWS";}
                if(slot.unknown){widgetUnknown++;if(widgetState.isEmpty())widgetState="CLIENT_SCREEN_WIDGET_SOURCE_UNKNOWN";}
                else if(!exclusive(modules)){widgetShared++;if(widgetState.isEmpty())widgetState="CLIENT_SCREEN_WIDGET_OTHER_SOURCES_REMAIN_LIVE";}
                if(!slot.pending.isEmpty())widgetState=slot.pending;if(!registry.pending.isEmpty())widgetState=registry.pending;
            }catch(ReflectiveOperationException|RuntimeException unavailable){widgets++;widgetUnknown++;widgetState="CLIENT_SCREEN_ACTUAL_WIDGET_READ_FAILED";}
        }
        long toastQueued=0,toastVisible=0,toastShared=0,toastUnknown=0;String toastState="";
        for(ToastRegistry registry:TOASTS.values())for(ToastSlot slot:registry.slots.values()){
            Module[] modules=toastSources(slot);if(!slot.queued&&!slot.visible||Arrays.stream(modules).noneMatch(source->source==module))continue;
            if(slot.queued)toastQueued++;if(slot.visible)toastVisible++;
            if(slot.unknown){toastUnknown++;if(toastState.isEmpty())toastState="CLIENT_TOAST_SOURCE_UNKNOWN";}
            else if(!exclusive(modules)){toastShared++;if(toastState.isEmpty())toastState="CLIENT_TOAST_OTHER_SOURCES_REMAIN_LIVE";}
            if(!registry.pending.isEmpty())toastState=registry.pending;
        }
        return active!=0||textures!=0||resources!=0||sounds!=0||reloads!=0||gpu!=0||failures!=0||ui!=0||renderCalls!=0||overlays!=0||widgets!=0||toastQueued!=0||toastVisible!=0?"CLIENT_RETIREMENT_PENDING:active="+active+":textures="+textures+":resources="+resources+":sounds="+sounds+":soundState="+soundState+":reloads="+reloads+":reloadState="+reloadState+":gpu="+gpu+":gpuState="+gpuState+":ui="+ui+":renderCalls="+(renderCalls<0?"unknown":Long.toString(renderCalls))+":overlays="+(overlays<0?"unknown":Long.toString(overlays))+":overlayState="+overlayState+":widgets="+widgets+":widgetShared="+widgetShared+":widgetUnknown="+widgetUnknown+":widgetState="+widgetState+":toastQueued="+toastQueued+":toastVisible="+toastVisible+":toastShared="+toastShared+":toastUnknown="+toastUnknown+":toastState="+toastState+":shared="+shared+":releaseFailures="+failures:"";
    }}
    public static Object[] controlObjects(){agent();List<Object> roots=new ArrayList<>(Arrays.asList(SOURCES,TEXTURES,ROLES,REQUESTED,CURRENT,RELEASING,ACTIVE,DEAD,IMAGES,PIXELS,RETIREMENTS,FAILURES,SOUNDS,SOUND_STOPS,SOUND_RELEASING,RELOAD_LISTENERS,RELOAD_WORK,UI_OWNERS,UI_PENDING,UI_RETIRING,UI_FIELDS,SCREEN_WIDGETS,SCREEN_ADDING,WIDGET_RETIRING,SCREEN_FIELDS,TOASTS,TOAST_FIELDS,OVERLAY_EVENTS,OVERLAY_REGISTRIES,OVERLAY_NAMES,OVERLAY_INITIALIZING,OVERLAY_FIELDS,RENDER_QUEUES,RENDER_REPLAY,RENDER_FIELDS));Collections.addAll(roots,GPU.controls());return roots.toArray();}
    private static void reap(){for(Key key;(key=(Key)DEAD.poll())!=null;){SOURCES.remove(key);TEXTURES.remove(key);IMAGES.remove(key);PIXELS.remove(key);FAILURES.remove(key);SOUNDS.remove(key);RELOAD_LISTENERS.remove(key);UI_OWNERS.remove(key);SCREEN_WIDGETS.remove(key);TOASTS.remove(key);OVERLAY_EVENTS.remove(key);OVERLAY_REGISTRIES.remove(key);OVERLAY_NAMES.remove(key);RENDER_QUEUES.remove(key);}}
}
