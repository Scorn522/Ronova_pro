package dev.ronova.pro;

import dev.ronova.pro.mixin.Access;
import java.lang.ref.WeakReference;
import java.lang.reflect.*;
import java.util.*;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.FoodData;
import net.minecraft.world.item.*;
import net.minecraftforge.common.extensions.IForgeMobEffect;

/** Exact live effect/food carriers. Their ownership is independent of world indices and source history. */
public final class LiveStatePolicy {
    private enum Kind { MAP, TABLE, NODE, EFFECT, FOOD, FACTOR_OPTIONAL, FACTOR, CURATIVE_LIST, CURATIVE_ARRAY, CURATIVE_STACK }
    private record Slot(Field field,long offset,int width) { }
    private static final class Owner {
        final WeakReference<LivingEntity> entity;
        final WeakReference<Object> effects,food;
        final long generation;
        Owner(LivingEntity entity,long generation,Object effects,Object food) {
            this.entity=new WeakReference<>(entity);this.generation=generation;
            this.effects=new WeakReference<>(effects);this.food=new WeakReference<>(food);
        }
    }
    private record Link(Owner owner,Kind kind) { }
    private static final class Scope {
        final Owner owner;final String operation;final Class<?> anchor;final String method,descriptor;
        final WeakReference<Thread> thread=new WeakReference<>(Thread.currentThread());final Scope previous;
        final WeakReference<Object> used,argument;
        final int occurrences;
        final List<Owner> related;
        WeakReference<Object> provider,result;
        boolean providerAccepted;
        boolean closed;
        Scope(Owner owner,String operation,StackWalker.StackFrame frame,Scope previous,Object used,Object argument,int occurrences) {
            this(owner,operation,frame,previous,used,argument,occurrences,null);
        }
        Scope(Owner owner,String operation,StackWalker.StackFrame frame,Scope previous,Object used,Object argument,int occurrences,List<Owner> related) {
            this.owner=owner;this.operation=operation;anchor=frame.getDeclaringClass();method=frame.getMethodName();
            descriptor=frame.getDescriptor();this.previous=previous;this.used=new WeakReference<>(used);
            this.argument=new WeakReference<>(argument);this.occurrences=occurrences;this.related=related;
        }
    }
    // Prepared before guard publication. A guard never initializes reflection/offset helpers inside a map write.
    private static volatile boolean initialized;
    private static Map<LivingEntity,Owner> OWNERS;
    private static Map<Object,List<Link>> CARRIERS;
    private static Map<Thread,Scope> SCOPES;
    private static Map<Class<?>,List<Slot>> FIELDS;
    private static StackWalker WALKER;
    private static Field MAP_TABLE,NODE_KEY,NODE_VALUE,NODE_NEXT,OPTIONAL_VALUE,FACTOR_CURRENT;
    private static Class<?> NODE;
    private static int REFERENCE_WIDTH;
    private LiveStatePolicy() { }

    static synchronized void initialize()throws ReflectiveOperationException {
        if(initialized)return;
        Class<?> unsafeType=Class.forName("sun.misc.Unsafe",false,ClassLoader.getPlatformClassLoader());
        Field singleton=unsafeType.getDeclaredField("theUnsafe");singleton.setAccessible(true);Object unsafe=singleton.get(null);
        Method offset=unsafeType.getMethod("objectFieldOffset",Field.class);
        REFERENCE_WIDTH=((Number)unsafeType.getMethod("arrayIndexScale",Class.class).invoke(unsafe,Object[].class)).intValue();
        if(REFERENCE_WIDTH<=0)throw new IllegalStateException("LIVE_STATE_REFERENCE_WIDTH_UNAVAILABLE");
        NODE=Class.forName("java.util.HashMap$Node",false,null);
        Class<?> tree=Class.forName("java.util.HashMap$TreeNode",false,null);
        Class<?>[] carriers={LivingEntity.class,Player.class,FoodData.class,MobEffectInstance.class,MobEffectInstance.FactorData.class,
                Optional.class,HashMap.class,LinkedHashMap.class,NODE,tree};
        Class.forName("dev.ronova.pro.agent.RecoveryAgent",false,ClassLoader.getSystemClassLoader())
                .getMethod("openBackingAccess",Class.class,Class[].class).invoke(null,LiveStatePolicy.class,(Object)carriers);
        Map<Class<?>,List<Slot>> fields=new HashMap<>();
        for(Class<?> type:carriers) {
            List<Slot> slots=new ArrayList<>();
            for(Class<?> owner=type;owner!=null&&owner!=Object.class;owner=owner.getSuperclass())for(Field field:owner.getDeclaredFields()) {
                if(Modifier.isStatic(field.getModifiers())||!selected(field))continue;
                if(!field.trySetAccessible())throw new IllegalStateException("LIVE_STATE_FIELD_ACCESS:"+field);
                Class<?> value=field.getType();int width=value==long.class||value==double.class?8:value==int.class||value==float.class?4:value==short.class||value==char.class?2:1;
                if(!value.isPrimitive())width=REFERENCE_WIDTH;
                slots.add(new Slot(field,(Long)offset.invoke(unsafe,field),width));
            }
            fields.put(type,List.copyOf(slots));
        }
        MAP_TABLE=HashMap.class.getDeclaredField("table");MAP_TABLE.setAccessible(true);
        NODE_KEY=NODE.getDeclaredField("key");NODE_KEY.setAccessible(true);
        NODE_VALUE=NODE.getDeclaredField("value");NODE_VALUE.setAccessible(true);
        NODE_NEXT=NODE.getDeclaredField("next");NODE_NEXT.setAccessible(true);
        OPTIONAL_VALUE=Optional.class.getDeclaredField("value");OPTIONAL_VALUE.setAccessible(true);
        try{FACTOR_CURRENT=MobEffectInstance.FactorData.class.getDeclaredField("factorCurrent");}
        catch(NoSuchFieldException production){FACTOR_CURRENT=MobEffectInstance.FactorData.class.getDeclaredField("f_216911_");}
        FACTOR_CURRENT.setAccessible(true);
        OWNERS=Collections.synchronizedMap(new WeakIdentityMap<>());
        CARRIERS=Collections.synchronizedMap(new WeakIdentityMap<>());
        SCOPES=Collections.synchronizedMap(new WeakIdentityMap<>());
        FIELDS=Map.copyOf(fields);WALKER=StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE);
        initialized=true;
    }
    private static boolean selected(Field field) {
        Class<?> owner=field.getDeclaringClass();String name=field.getName();
        if(owner==LivingEntity.class)return name.equals("activeEffects")||name.equals("f_20945_")||name.equals("effectsDirty")||name.equals("f_20948_");
        if(owner==Player.class)return name.equals("foodData")||name.equals("f_36097_");
        if(owner==FoodData.class||owner==MobEffectInstance.class||owner==MobEffectInstance.FactorData.class)return true;
        if(owner==Optional.class)return name.equals("value");
        if(owner==HashMap.class)return Set.of("table","size","modCount","threshold","loadFactor").contains(name);
        return NODE!=null&&NODE.isAssignableFrom(owner);
    }
    private static Object effects(LivingEntity entity){return ((Access.Living)entity).pro$effects();}
    private static Object food(LivingEntity entity){return entity instanceof Player player?((Access.PlayerState)player).pro$foodData():null;}
    private static boolean active(Owner owner) {
        LivingEntity entity=owner==null?null:owner.entity.get();
        return entity!=null&&ProRuntime.liveStateGeneration(entity)==owner.generation;
    }
    private static boolean current(Owner owner,Kind kind,Object carrier) {
        if(!active(owner))return false;LivingEntity entity=owner.entity.get();
        if(kind==Kind.FOOD)return owner.food.get()==carrier&&food(entity)==carrier;
        Object map=owner.effects.get();if(map==null||effects(entity)!=map)return false;
        if(kind==Kind.MAP)return carrier==map;
        try {
            Object table=MAP_TABLE.get(map);
            if(kind==Kind.TABLE)return table==carrier;
            if(table instanceof Object[] buckets) {
                if(buckets.length>2048)throw new IllegalStateException("LIVE_STATE_MAP_STRUCTURE_UNRESOLVED");
                Object[] seen=new Object[2048];int count=0;
                for(Object head:buckets)for(Object node=head;node!=null;node=NODE_NEXT.get(node)) {
                    if(count==seen.length||containsIdentity(seen,count,node))throw new IllegalStateException("LIVE_STATE_MAP_STRUCTURE_UNRESOLVED");seen[count++]=node;
                    if(kind==Kind.NODE&&node==carrier)return true;
                    if((kind==Kind.EFFECT||kind==Kind.FACTOR_OPTIONAL||kind==Kind.FACTOR
                            ||kind==Kind.CURATIVE_LIST||kind==Kind.CURATIVE_ARRAY||kind==Kind.CURATIVE_STACK)&&effectContains(NODE_VALUE.get(node),carrier,kind))return true;
                }
            }
        }catch(IllegalAccessException unavailable){throw new IllegalStateException("LIVE_STATE_CARRIER_ACCESS",unavailable);}
        return false;
    }
    private static boolean effectContains(Object first,Object expected,Kind kind) {
        Object cursor=first;int remaining=256;
        Object[] seen=new Object[remaining];int count=0;
        while(cursor instanceof MobEffectInstance) {
            if(count==seen.length||containsIdentity(seen,count,cursor))throw new IllegalStateException("LIVE_STATE_EFFECT_CHAIN_UNRESOLVED");seen[count++]=cursor;
            if(kind==Kind.EFFECT&&cursor==expected)return true;
            Object optional=((Access.EffectState)cursor).pro$factorData();
            if(kind==Kind.FACTOR_OPTIONAL&&optional==expected)return true;
            if(kind==Kind.FACTOR&&factorValue(optional)==expected)return true;
            if(kind==Kind.CURATIVE_LIST||kind==Kind.CURATIVE_ARRAY||kind==Kind.CURATIVE_STACK) {
                Object curatives=((Access.EffectState)cursor).pro$curativeItems();
                if(curatives!=null) {
                    if(kind==Kind.CURATIVE_LIST&&curatives==expected)return true;
                    EquipmentPolicy.CurativeList layout=EquipmentPolicy.knownCurativeLayout(curatives);
                    if(layout!=null) {
                        if(kind==Kind.CURATIVE_ARRAY&&layout.values()==expected)return true;
                        if(kind==Kind.CURATIVE_STACK)for(int at=0;at<layout.size();at++)if(layout.values()[at]==expected)return true;
                    }
                }
            }
            cursor=((Access.EffectState)cursor).pro$hiddenEffect();
        }
        return false;
    }
    private static Object factorValue(Object optional) {
        if(!(optional instanceof Optional<?>))throw new IllegalStateException("LIVE_STATE_FACTOR_OPTIONAL_UNRESOLVED");
        try {
            Object value=OPTIONAL_VALUE.get(optional);
            if(value!=null&&!(value instanceof MobEffectInstance.FactorData))throw new IllegalStateException("LIVE_STATE_FACTOR_VALUE_UNRESOLVED");
            return value;
        }catch(IllegalAccessException unavailable){throw new IllegalStateException("LIVE_STATE_FACTOR_ACCESS",unavailable);}
    }
    private static void link(Owner owner,Object value,Kind kind) {
        if(value==null)return;
        synchronized(CARRIERS) {
            List<Link> next=new ArrayList<>();boolean found=false;
            for(Link link:CARRIERS.getOrDefault(value,List.of())) {Owner prior=link.owner;if(prior!=owner&&!active(prior))continue;
                if(prior==owner&&link.kind==kind)found=true;next.add(link);}
            if(!found)next.add(new Link(owner,kind));CARRIERS.put(value,List.copyOf(next));
        }
    }
    private static void linkEffect(Owner owner,Object value) {
        Object[] seen=new Object[256];int count=0;
        while(value instanceof MobEffectInstance) {
            if(count==seen.length||containsIdentity(seen,count,value))throw new IllegalStateException("LIVE_STATE_EFFECT_CHAIN_UNRESOLVED");seen[count++]=value;
            link(owner,value,Kind.EFFECT);
            Object optional=((Access.EffectState)value).pro$factorData();
            Object factor=factorValue(optional);link(owner,optional,Kind.FACTOR_OPTIONAL);link(owner,factor,Kind.FACTOR);
            Object curatives=((Access.EffectState)value).pro$curativeItems();
            if(curatives!=null)EquipmentPolicy.captureCurativeItems(owner.entity.get(),owner.generation,curatives);
            value=((Access.EffectState)value).pro$hiddenEffect();
        }
    }
    static void publish(Entity entity,long generation) {
        if(!initialized||!(entity instanceof LivingEntity living))return;
        if(generation<=0){OWNERS.remove(living);return;}
        Object map=effects(living),food=food(living);Owner owner=OWNERS.get(living);
        if(owner==null||owner.generation!=generation||owner.effects.get()!=map||owner.food.get()!=food) {
            owner=new Owner(living,generation,map,food);OWNERS.put(living,owner);
        }
        link(owner,map,Kind.MAP);link(owner,food,Kind.FOOD);
        if(map!=null&&map.getClass()!=HashMap.class&&map.getClass()!=LinkedHashMap.class)
            throw new IllegalStateException("LIVE_EFFECT_MAP_IMPLEMENTATION_UNAVAILABLE");
        refresh(owner);
    }
    private static void refresh(Owner owner) {
        Object map=owner.effects.get();if(map==null)return;
        try {
            Object table=MAP_TABLE.get(map);link(owner,table,Kind.TABLE);
            if(table instanceof Object[] buckets) {
                if(buckets.length>2048)throw new IllegalStateException("LIVE_STATE_MAP_STRUCTURE_UNRESOLVED");
                Object[] seen=new Object[2048];int count=0;
                for(Object head:buckets)for(Object node=head;node!=null;node=NODE_NEXT.get(node)) {
                    if(count==seen.length||containsIdentity(seen,count,node))throw new IllegalStateException("LIVE_STATE_MAP_STRUCTURE_UNRESOLVED");seen[count++]=node;
                    link(owner,node,Kind.NODE);linkEffect(owner,NODE_VALUE.get(node));
                }
            }
        }catch(IllegalAccessException unavailable){throw new IllegalStateException("LIVE_STATE_CAPTURE_UNAVAILABLE",unavailable);}
    }
    public static void changed(LivingEntity entity) {
        if(!initialized)return;Owner owner=OWNERS.get(entity);if(active(owner))refresh(owner);
    }
    private static List<Link> links(Object value){return !initialized||value==null?List.of():CARRIERS.getOrDefault(value,List.of());}
    private static boolean containsIdentity(Object[] values,int length,Object value){for(int i=0;i<length;i++)if(values[i]==value)return true;return false;}
    static boolean known(Object value){for(Link link:links(value))if(current(link.owner,link.kind,value))return true;return false;}
    private static boolean curativeCurrent(LivingEntity entity,long generation,Object value,Kind kind) {
        if(!initialized||entity==null||value==null)return false;Owner owner=OWNERS.get(entity);
        return owner!=null&&owner.generation==generation&&current(owner,kind,value);
    }
    static boolean curativeListCurrent(LivingEntity entity,long generation,Object value){return curativeCurrent(entity,generation,value,Kind.CURATIVE_LIST);}
    static boolean curativeArrayCurrent(LivingEntity entity,long generation,Object value){return curativeCurrent(entity,generation,value,Kind.CURATIVE_ARRAY);}
    static boolean curativeStackCurrent(LivingEntity entity,long generation,ItemStack value){return value!=ItemStack.EMPTY&&curativeCurrent(entity,generation,value,Kind.CURATIVE_STACK);}

    /** Only exact original bodies woven by SaveFinallyPlugin can create a scope. */
    public static Object begin(Object receiver,Object argument,String operation) {
        if(!initialized)return null;
        if(operation.equals("curative-read"))return beginCurative(receiver);
        if(operation.equals("factor-tick")||operation.equals("factor-read"))return beginFactor(receiver,argument,operation);
        List<StackWalker.StackFrame> frames=WALKER.walk(stream->stream.filter(frame->frame.getDeclaringClass().getNestHost()!=LiveStatePolicy.class).toList());
        if(frames.size()<2)return null;StackWalker.StackFrame entry=frames.get(0),caller=frames.get(1);
        LivingEntity entity;Owner owner;Object used=null;
        if(operation.equals("food-tick")) {
            if(entry.getDeclaringClass()!=FoodData.class||!named(entry,"tick","m_38710_")||!(argument instanceof Player player)
                    ||caller.getDeclaringClass()!=Player.class||!named(caller,"tick","m_8119_"))return null;
            entity=player;owner=OWNERS.get(entity);if(!active(owner)||owner.food.get()!=receiver||food(entity)!=receiver)return null;
        }else {
            if(!(receiver instanceof LivingEntity living)||entry.getDeclaringClass()!=(operation.equals("food-action")?Player.class:LivingEntity.class))return null;
            entity=living;owner=OWNERS.get(living);if(!active(owner))return null;
            if(operation.equals("effects-tick")) {
                if(!named(entry,"tickEffects","m_21217_")||caller.getDeclaringClass()!=LivingEntity.class||!named(caller,"baseTick","m_6075_"))return null;
            }else if(operation.equals("consume")) {
                if(!named(entry,"completeUsingItem","m_8095_")||caller.getDeclaringClass()!=LivingEntity.class
                        ||!named(caller,"updateUsingItem","m_142106_")&&!named(caller,"updatingUsingItem","m_21329_"))return null;
                used=((Access.Living)living).pro$useItem();if(used==null||used==ItemStack.EMPTY)return null;
            }else if(operation.equals("food-action")) {
                if(entry.getDeclaringClass()!=Player.class||!named(entry,"causeFoodExhaustion","m_36399_")
                        ||caller.getDeclaringClass()!=Player.class||!Set.of("jumpFromGround","m_6135_","checkMovementStatistics","m_36378_","attack","m_5706_").contains(caller.getMethodName()))return null;
            }else return null;
        }
        Scope previous=SCOPES.get(Thread.currentThread());
        for(Scope active=previous;active!=null;active=active.previous)
            if(!active.closed&&active.owner==owner&&active.operation.equals(operation))return null;
        int occurrences=0;
        for(var frame:frames)if(frame.getDeclaringClass()==entry.getDeclaringClass()&&frame.getMethodName().equals(entry.getMethodName())&&frame.getDescriptor().equals(entry.getDescriptor()))occurrences++;
        Scope scope=new Scope(owner,operation,entry,previous,used,null,occurrences);SCOPES.put(Thread.currentThread(),scope);return scope;
    }
    private static boolean sameFrame(StackWalker.StackFrame frame,Class<?> declaring,String official,String srg,String descriptor) {
        return frame.getDeclaringClass()==declaring&&named(frame,official,srg)&&frame.getDescriptor().equals(descriptor);
    }
    private static boolean sameAnchor(StackWalker.StackFrame frame,Scope scope) {
        return frame.getDeclaringClass()==scope.anchor&&frame.getMethodName().equals(scope.method)&&frame.getDescriptor().equals(scope.descriptor);
    }
    private static Object beginCurative(Object receiver) {
        if(!(receiver instanceof MobEffectInstance instance)||((Access.EffectState)instance).pro$curativeItems()!=null)return null;
        List<Owner> owners=new ArrayList<>();
        for(Link link:links(instance))if(link.kind==Kind.EFFECT&&current(link.owner,link.kind,instance)&&!owners.contains(link.owner))owners.add(link.owner);
        if(owners.isEmpty())return null;
        List<StackWalker.StackFrame> frames=WALKER.walk(stream->stream.filter(frame->frame.getDeclaringClass().getNestHost()!=LiveStatePolicy.class).toList());
        if(frames.isEmpty()||!sameFrame(frames.get(0),MobEffectInstance.class,"getCurativeItems","getCurativeItems","()Ljava/util/List;"))return null;
        Scope previous=SCOPES.get(Thread.currentThread());
        for(Scope scope=previous;scope!=null;scope=scope.previous)if(!scope.closed&&scope.operation.equals("curative-read")&&scope.used.get()==instance)return null;
        StackWalker.StackFrame entry=frames.get(0);int occurrences=0;
        for(var frame:frames)if(frame.getDeclaringClass()==entry.getDeclaringClass()&&frame.getMethodName().equals(entry.getMethodName())&&frame.getDescriptor().equals(entry.getDescriptor()))occurrences++;
        Scope scope=new Scope(owners.get(0),"curative-read",entry,previous,instance,null,occurrences,List.copyOf(owners));
        SCOPES.put(Thread.currentThread(),scope);return scope;
    }
    private static boolean curativePath(Scope scope) {
        if(scope==null||scope.closed||scope.thread.get()!=Thread.currentThread()||scope!=SCOPES.get(Thread.currentThread()))return false;
        return WALKER.walk(stream->{List<StackWalker.StackFrame> frames=stream.toList();int occurrences=0;
            for(var frame:frames)if(sameAnchor(frame,scope))occurrences++;
            if(occurrences!=scope.occurrences)return false;
            for(var frame:frames) {
                if(sameAnchor(frame,scope))return true;
                if(!infrastructure(frame.getDeclaringClass()))return false;
            }
            return false;
        });
    }
    private static Scope curativeScope(MobEffectInstance instance) {
        Scope scope=SCOPES.get(Thread.currentThread());
        if(scope!=null&&scope.operation.equals("curative-read")&&scope.used.get()==instance&&curativePath(scope))return scope;
        for(Link link:links(instance))if(link.kind==Kind.EFFECT&&current(link.owner,link.kind,instance))
            throw new IllegalStateException("CURATIVE_ORIGINAL_PUBLICATION_REQUIRED");
        return null;
    }
    /** Called immediately before the real getter's actual provider invocation. */
    public static void curativeProvider(MobEffectInstance instance,MobEffect provider) {
        if(!initialized)return;Scope scope=curativeScope(instance);if(scope==null)return;
        if(((Access.EffectState)instance).pro$curativeItems()!=null||provider==null)throw new IllegalStateException("CURATIVE_ORIGINAL_INPUT_CHANGED");
        try {
            Method method=provider.getClass().getMethod("getCurativeItems");
            if(method.getDeclaringClass()!=IForgeMobEffect.class||method.getReturnType()!=List.class||Modifier.isStatic(method.getModifiers()))
                throw new IllegalStateException("CURATIVE_PROVIDER_IMPLEMENTATION_UNRESOLVED");
        }catch(NoSuchMethodException unavailable){throw new IllegalStateException("CURATIVE_PROVIDER_IMPLEMENTATION_UNRESOLVED",unavailable);}
        scope.provider=new WeakReference<>(provider);scope.providerAccepted=true;
    }
    /** The value is the real invocation's return, still on its original store stack. */
    public static void curativeResult(MobEffectInstance instance,List<?> proposed) {
        if(!initialized)return;Scope scope=curativeScope(instance);if(scope==null)return;
        if(!scope.providerAccepted||scope.provider==null||scope.provider.get()==null||proposed==null
                ||((Access.EffectState)instance).pro$curativeItems()!=null)throw new IllegalStateException("CURATIVE_ORIGINAL_RESULT_REQUIRED");
        for(Owner owner:scope.related)if(current(owner,Kind.EFFECT,instance))EquipmentPolicy.captureCurativeItems(owner.entity.get(),owner.generation,proposed);
        scope.result=new WeakReference<>(proposed);
    }
    private static boolean curativePublicationAllowed(Owner owner,Object receiver,Object proposed) {
        Scope scope=SCOPES.get(Thread.currentThread());
        return proposed!=null&&scope!=null&&scope.operation.equals("curative-read")&&scope.used.get()==receiver
                &&scope.related.contains(owner)&&scope.providerAccepted&&scope.result!=null&&scope.result.get()==proposed
                &&((Access.EffectState)receiver).pro$curativeItems()==null&&curativePath(scope);
    }
    private static boolean curativeUpdateWriter() {
        return WALKER.walk(stream->{var frame=stream.filter(entry->!infrastructure(entry.getDeclaringClass())).findFirst().orElse(null);
            return frame!=null&&(sameFrame(frame,MobEffectInstance.class,"setDetailsFrom","m_19548_","(Lnet/minecraft/world/effect/MobEffectInstance;)V")
                    ||sameFrame(frame,MobEffectInstance.class,"setCurativeItems","setCurativeItems","(Ljava/util/List;)V"));
        });
    }
    private static boolean factorTickPath(Owner owner,List<StackWalker.StackFrame> frames) {
        if(frames.size()<4
                ||!sameFrame(frames.get(1),MobEffectInstance.class,"lambda$tick$0","m_267554_","(Lnet/minecraft/world/effect/MobEffectInstance$FactorData;)V")
                ||!sameFrame(frames.get(2),Optional.class,"ifPresent","ifPresent","(Ljava/util/function/Consumer;)V")
                ||!sameFrame(frames.get(3),MobEffectInstance.class,"tick","m_19552_","(Lnet/minecraft/world/entity/LivingEntity;Ljava/lang/Runnable;)Z"))return false;
        for(Scope scope=SCOPES.get(Thread.currentThread());scope!=null;scope=scope.previous) {
            if(scope.closed||scope.owner!=owner||scope.thread.get()!=Thread.currentThread()||!scope.operation.equals("effects-tick"))continue;
            int occurrences=0;for(var frame:frames)if(sameAnchor(frame,scope))occurrences++;
            if(occurrences!=scope.occurrences)continue;
            boolean path=true;
            // Optional.ifPresent is accepted only in the exact original tick
            // sequence above, never as a general Consumer/callback permission.
            for(int at=3;at<frames.size();at++) {
                var frame=frames.get(at);
                if(sameAnchor(frame,scope)){if(path)return true;break;}
                if(!infrastructure(frame.getDeclaringClass())&&!gameFrame(frame,scope)){path=false;break;}
            }
        }
        return false;
    }
    private static Object beginFactor(Object receiver,Object argument,String operation) {
        if(!(receiver instanceof MobEffectInstance.FactorData))return null;
        List<StackWalker.StackFrame> frames=WALKER.walk(stream->stream.filter(frame->frame.getDeclaringClass().getNestHost()!=LiveStatePolicy.class).toList());
        if(frames.isEmpty())return null;StackWalker.StackFrame entry=frames.get(0);boolean tick=operation.equals("factor-tick");
        if(!sameFrame(entry,MobEffectInstance.FactorData.class,tick?"tick":"getFactor",tick?"m_267690_":"m_238413_",
                tick?"(Lnet/minecraft/world/effect/MobEffectInstance;)V":"(Lnet/minecraft/world/entity/LivingEntity;F)F"))return null;
        for(Link link:links(receiver))if(link.kind==Kind.FACTOR&&current(link.owner,link.kind,receiver)) {
            Owner owner=link.owner;
            if(tick) {
                if(!(argument instanceof MobEffectInstance effect)||!current(owner,Kind.EFFECT,effect)
                        ||factorValue(((Access.EffectState)effect).pro$factorData())!=receiver||!factorTickPath(owner,frames))continue;
            }else if(argument!=owner.entity.get())continue;
            Scope previous=SCOPES.get(Thread.currentThread());
            for(Scope active=previous;active!=null;active=active.previous)
                if(!active.closed&&active.owner==owner&&active.operation.equals(operation))return null;
            int occurrences=0;
            for(var frame:frames)if(frame.getDeclaringClass()==entry.getDeclaringClass()&&frame.getMethodName().equals(entry.getMethodName())&&frame.getDescriptor().equals(entry.getDescriptor()))occurrences++;
            Scope scope=new Scope(owner,operation,entry,previous,receiver,argument,occurrences);
            SCOPES.put(Thread.currentThread(),scope);return scope;
        }
        return null;
    }
    public static void end(Object token) {
        if(!(token instanceof Scope scope)||scope.closed)return;
        if(scope.thread.get()!=Thread.currentThread()||SCOPES.get(Thread.currentThread())!=scope)throw new IllegalMonitorStateException("LIVE_STATE_SCOPE_ORDER");
        try{if(active(scope.owner)&&!scope.operation.equals("factor-tick")&&!scope.operation.equals("factor-read")&&!scope.operation.equals("curative-read"))refresh(scope.owner);}finally {
            scope.closed=true;if(scope.previous==null)SCOPES.remove(Thread.currentThread());else SCOPES.put(Thread.currentThread(),scope.previous);
        }
    }
    public static void failed(Object token,Throwable failure) {
        try{end(token);}catch(RuntimeException|Error cleanup){if(cleanup!=failure)failure.addSuppressed(cleanup);}
    }
    public static boolean anchorDenied(Object receiver,String operation,Object token) {
        if(!initialized)return false;Owner owner=receiver instanceof LivingEntity living?OWNERS.get(living):null;
        if(receiver instanceof MobEffectInstance.FactorData) {
            boolean protectedFactor=false;
            for(Link link:links(receiver))if(link.kind==Kind.FACTOR&&current(link.owner,link.kind,receiver)) {
                protectedFactor=true;
                if(token instanceof Scope scope&&scope.owner==link.owner&&scope.used.get()==receiver&&scope==SCOPES.get(Thread.currentThread())
                        &&!scope.closed&&scope.thread.get()==Thread.currentThread()&&scope.operation.equals(operation))return false;
            }
            return protectedFactor;
        }
        if(receiver instanceof FoodData)for(Link link:links(receiver))if(link.kind==Kind.FOOD&&current(link.owner,link.kind,receiver)){owner=link.owner;break;}
        if(!active(owner))return false;
        return !(token instanceof Scope scope)||scope!=SCOPES.get(Thread.currentThread())||scope.owner!=owner||scope.closed
                ||scope.thread.get()!=Thread.currentThread()||!scope.operation.equals(operation);
    }
    private static boolean named(StackWalker.StackFrame frame,String official,String srg){return frame.getMethodName().equals(official)||frame.getMethodName().equals(srg);}
    private static boolean infrastructure(Class<?> type) {
        if(type.getModule()==LiveStatePolicy.class.getModule()&&type.getClassLoader()==LiveStatePolicy.class.getClassLoader()
                &&type.getName().startsWith("dev.ronova.pro."))return true;
        if(type.getClassLoader()!=null)return false;Class<?> host=type.getNestHost();String name=host.getName();
        if(Set.of("dev.ronova.pro.bootstrap.ControlBridge","dev.ronova.pro.bootstrap.BackingBridge","dev.ronova.pro.bootstrap.SourceMapBridge",
                "dev.ronova.pro.bootstrap.CodeSourceBridge","dev.ronova.pro.bootstrap.ExecutionFlow","dev.ronova.pro.bootstrap.NativeControl",
                "dev.ronova.pro.bootstrap.ResourceBridge","dev.ronova.pro.bootstrap.TaskBridge").contains(name))return true;
        return host==HashMap.class||host==LinkedHashMap.class||host==AbstractMap.class||host==ArrayList.class
                ||name.equals("java.lang.reflect.Field")||name.equals("java.lang.reflect.Method")||name.equals("sun.misc.Unsafe")||name.equals("jdk.internal.misc.Unsafe")
                ||name.equals("jdk.internal.reflect.NativeMethodAccessorImpl")||name.equals("jdk.internal.reflect.DelegatingMethodAccessorImpl");
    }
    private static boolean gameFrame(StackWalker.StackFrame frame,Scope scope) {
        Class<?> type=frame.getDeclaringClass(),host=type.getNestHost();String name=frame.getMethodName();
        if(host==MobEffectInstance.class)return Set.of("tick","m_19552_","tickDownDuration","m_19579_","mapDuration","m_267696_","update","m_19558_","setDetailsFrom","m_19548_","setCurativeItems","getCurativeItems","lambda$tickDownDuration$1","lambda$tick$0","m_267553_","m_267554_","applyAsInt").contains(name);
        if(type==FoodData.class)return Set.of("tick","m_38710_","eat","m_38707_","m_38712_","addExhaustion","m_38703_","setFoodLevel","m_38705_","setSaturation","m_38717_","setExhaustion","m_150378_").contains(name);
        if(type==LivingEntity.class||type==Player.class)return Set.of("tickEffects","m_21217_","addEffect","m_7292_","m_147207_","forceAddEffect","m_147215_","removeEffect","m_21195_","removeEffectNoUpdate","m_6234_","removeAllEffects","m_21219_","curePotionEffects","onEffectAdded","m_142540_","onEffectUpdated","m_141973_","onEffectRemoved","m_7285_","eat","m_5584_","addEatEffect","m_21063_","completeUsingItem","m_8095_","causeFoodExhaustion","m_36399_","lambda$tickEffects$3","lambda$tickEffects$4","m_21264_").contains(name);
        if(type==ItemStack.class)return name.equals("finishUsingItem")||name.equals("m_41671_");
        return Item.class.isAssignableFrom(type)&&scope.operation.equals("consume")&&scope.used.get() instanceof ItemStack stack
                &&EquipmentPolicy.consumptionFrame(frame,scope.owner.entity.get(),stack);
    }
    private static boolean permitted(Owner owner,boolean effects){return permitted(owner,effects,false);}
    private static boolean permitted(Owner owner,boolean effects,boolean entryQuery) {
        if(!active(owner))return false;
        LivingEntity entity=owner.entity.get();if(ProRuntime.liveStateLifecycleAllowed(entity))return true;
        for(Scope scope=SCOPES.get(Thread.currentThread());scope!=null;scope=scope.previous) {
            if(scope.closed||scope.owner!=owner||scope.thread.get()!=Thread.currentThread())continue;
            if(effects&&!scope.operation.equals("effects-tick")&&!scope.operation.equals("consume"))continue;
            if(!effects&&!scope.operation.equals("food-tick")&&!scope.operation.equals("consume")&&!scope.operation.equals("food-action"))continue;
            if(scope.operation.equals("consume")&&scope.used.get()!=((Access.Living)entity).pro$useItem())continue;
            Scope selected=scope;
            boolean path=WALKER.walk(frames->{var iterator=frames.iterator();boolean first=true;while(iterator.hasNext()) {
                var frame=iterator.next();Class<?> type=frame.getDeclaringClass();
                if(type==selected.anchor&&frame.getMethodName().equals(selected.method)&&frame.getDescriptor().equals(selected.descriptor))return true;
                if(infrastructure(type))continue;
                // This is only the injected entry's query, never a write permit.
                // Mutation guards run after that helper returned and skip no merged methods.
                if(first&&entryQuery&&(type==LivingEntity.class||type==Player.class||type==FoodData.class||type==MobEffectInstance.class)
                        &&frame.getMethodName().contains("$ronova$live")){first=false;continue;}
                first=false;
                if(!gameFrame(frame,selected))return false;
            }return false;});
            if(path)return true;
        }
        return false;
    }
    public static boolean effectEntryDenied(LivingEntity entity,String operation,MobEffectInstance proposed) {
        if(!initialized)return false;Owner owner=OWNERS.get(entity);if(!active(owner))return false;
        if(!permitted(owner,true,true))return true;
        if(proposed!=null)linkEffect(owner,proposed);return false;
    }
    public static boolean effectTickDenied(MobEffectInstance instance,LivingEntity entity) {
        for(Link link:links(instance))if(link.kind==Kind.EFFECT&&current(link.owner,link.kind,instance)) {
            Owner owner=link.owner;if(owner.entity.get()!=entity||!permitted(owner,true,true))return true;
        }return false;
    }
    public static boolean foodEntryDenied(FoodData data,String operation) {
        for(Link link:links(data))if(link.kind==Kind.FOOD&&current(link.owner,link.kind,data)&&!permitted(link.owner,false,true))return true;
        return false;
    }
    static boolean dispatchDenied(Object receiver,String operation,Object value) {
        if(receiver instanceof MobEffectInstance&&Set.of("update","m_19558_","setDetailsFrom","m_19548_").contains(operation)) {
            for(Link link:links(receiver))if(link.kind==Kind.EFFECT&&current(link.owner,link.kind,receiver)&&!permitted(link.owner,true,true))return true;
            return false;
        }
        if(receiver instanceof LivingEntity living&&Set.of("addEffect","m_7292_","m_147207_","forceAddEffect","m_147215_","removeEffect","m_21195_","removeEffectNoUpdate","m_6234_","removeAllEffects","m_21219_","curePotionEffects","onEffectAdded","m_142540_","onEffectUpdated","m_141973_","onEffectRemoved","m_7285_").contains(operation))
            return effectEntryDenied(living,operation,value instanceof MobEffectInstance effect?effect:null);
        if(receiver instanceof FoodData food&&Set.of("eat","m_38707_","m_38712_","addExhaustion","m_38703_","setFoodLevel","m_38705_","setSaturation","m_38717_","setExhaustion","m_150378_","readAdditionalSaveData","m_38715_").contains(operation))return foodEntryDenied(food,operation);
        return false;
    }
    static boolean elementDenied(Object container,Object old,Object next) {
        if(old==next&&old!=null)return false;
        for(Link link:links(container))if((link.kind==Kind.MAP||link.kind==Kind.NODE||link.kind==Kind.TABLE)&&current(link.owner,link.kind,container)) {
            Owner owner=link.owner;if(!permitted(owner,true))return true;
            if(next instanceof MobEffectInstance)linkEffect(owner,next);
            else if(NODE.isInstance(next))link(owner,next,Kind.NODE);
        }return false;
    }
    static boolean arrayDenied(Object array,int index,Object next){return elementDenied(array,null,next);}
    private static boolean factorFieldDenied(Owner owner,Object receiver,String name,Object next) {
        // The original constructor is the only writer of the padding setting.
        if(next==null||name.equals("paddingDuration")||name.equals("f_216908_"))return true;
        for(Scope scope=SCOPES.get(Thread.currentThread());scope!=null;scope=scope.previous) {
            if(scope.closed||scope.owner!=owner||scope.used.get()!=receiver||scope.thread.get()!=Thread.currentThread())continue;
            if(scope.operation.equals("factor-tick")) {
                if(!(scope.argument.get() instanceof MobEffectInstance effect)||!current(owner,Kind.EFFECT,effect)
                        ||factorValue(((Access.EffectState)effect).pro$factorData())!=receiver)continue;
            }else if(scope.operation.equals("factor-read")) {
                if(scope.argument.get()!=owner.entity.get()||!name.equals("factorPreviousFrame")&&!name.equals("f_216913_")||!(next instanceof Float value))continue;
                try{if(Float.floatToRawIntBits(value)!=Float.floatToRawIntBits(FACTOR_CURRENT.getFloat(receiver)))continue;}
                catch(IllegalAccessException unavailable){throw new IllegalStateException("LIVE_STATE_FACTOR_ACCESS",unavailable);}
            }else continue;
            Scope selected=scope;
            boolean path=WALKER.walk(stream->{List<StackWalker.StackFrame> frames=stream.toList();int occurrences=0;
                for(var frame:frames)if(sameAnchor(frame,selected))occurrences++;
                if(occurrences!=selected.occurrences)return false;
                for(var frame:frames) {
                    if(sameAnchor(frame,selected))return true;
                    if(!infrastructure(frame.getDeclaringClass()))return false;
                }
                return false;
            });
            if(path)return false;
        }
        return true;
    }
    static boolean fieldDenied(Object receiver,Class<?> declaring,String name,Object next) {
        if(!initialized||receiver==null||declaring==null)return false;
        if(receiver instanceof LivingEntity living&&declaring==LivingEntity.class&&(name.equals("activeEffects")||name.equals("f_20945_"))) {
            Owner owner=OWNERS.get(living);return active(owner)&&next!=effects(living);
        }
        if(receiver instanceof Player player&&declaring==Player.class&&(name.equals("foodData")||name.equals("f_36097_"))) {
            Owner owner=OWNERS.get(player);return active(owner)&&next!=food(player);
        }
        for(Link link:links(receiver))if(current(link.owner,link.kind,receiver)) {
            Slot selected=null;for(Slot slot:slots(receiver))if(slot.field.getDeclaringClass()==declaring&&slot.field.getName().equals(name)){selected=slot;break;}
            if(selected==null)return false;
            if(link.kind==Kind.FACTOR_OPTIONAL) {
                // Optional is immutable, including the shared empty instance.
                // A bulk writer's unknown value must not look like a null no-op.
                return true;
            }
            if(link.kind==Kind.FACTOR) {
                if(factorFieldDenied(link.owner,receiver,name,next))return true;
                continue;
            }
            if(link.kind==Kind.EFFECT&&declaring==MobEffectInstance.class&&(name.equals("factorData")||name.equals("f_216885_"))) {
                if(next!=((Access.EffectState)receiver).pro$factorData())return true;
                continue;
            }
            if(link.kind==Kind.EFFECT&&declaring==MobEffectInstance.class&&name.equals("curativeItems")) {
                if(curativePublicationAllowed(link.owner,receiver,next))continue;
                if(!curativeUpdateWriter()||!permitted(link.owner,true))return true;
                if(next!=null)EquipmentPolicy.captureCurativeItems(link.owner.entity.get(),link.owner.generation,next);
                continue;
            }
            Owner owner=link.owner;if(!permitted(owner,link.kind!=Kind.FOOD))return true;
            if(next==null&&selected.field.getType().isPrimitive())return true;
            if(next instanceof Number number&&declaring==FoodData.class) {
                double value=number.doubleValue();if(!Double.isFinite(value)||value<0||selected.field.getType()==float.class&&value>40)return true;
                if(Set.of("foodLevel","lastFoodLevel","saturationLevel","f_38696_","f_38700_","f_38697_").contains(name)&&value>20)return true;
            }
            if(next instanceof MobEffectInstance)linkEffect(owner,next);
            else if(next!=null&&next.getClass().isArray())link(owner,next,Kind.TABLE);
            else if(NODE.isInstance(next))link(owner,next,Kind.NODE);
        }
        if(receiver instanceof LivingEntity living&&declaring==LivingEntity.class&&(name.equals("effectsDirty")||name.equals("f_20948_"))) {
            Owner owner=OWNERS.get(living);return active(owner)&&!permitted(owner,true);
        }
        return false;
    }
    private static List<Slot> slots(Object receiver) {
        if(receiver instanceof FoodData)return FIELDS.get(FoodData.class);
        if(receiver instanceof MobEffectInstance)return FIELDS.get(MobEffectInstance.class);
        if(receiver instanceof MobEffectInstance.FactorData)return FIELDS.get(MobEffectInstance.FactorData.class);
        if(receiver instanceof Optional<?>)return FIELDS.get(Optional.class);
        if(receiver instanceof Player)return FIELDS.get(Player.class);
        if(receiver instanceof LivingEntity)return FIELDS.get(LivingEntity.class);
        return FIELDS.getOrDefault(receiver.getClass(),List.of());
    }
    private static boolean overlap(long first,long length,long second,long width) {
        return first>=0&&length>0&&second>=0&&width>0&&first<=Long.MAX_VALUE-length&&second<=Long.MAX_VALUE-width&&first<second+width&&second<first+length;
    }
    static boolean offsetDenied(Object receiver,long offset,Object next) {
        if(!initialized||receiver==null)return false;
        int width=next==null||!(next instanceof Number||next instanceof Boolean||next instanceof Character)?REFERENCE_WIDTH:next instanceof Long||next instanceof Double?8:next instanceof Byte||next instanceof Boolean?1:next instanceof Short||next instanceof Character?2:4;
        for(Slot slot:slots(receiver))if(overlap(offset,width,slot.offset,slot.width)) {
            Object value=offset==slot.offset&&width==slot.width?next:null;
            if(slot.field.getType()==float.class&&value instanceof Integer bits)value=Float.intBitsToFloat(bits);
            if(fieldDenied(receiver,slot.field.getDeclaringClass(),slot.field.getName(),value))return true;
        }
        return false;
    }
    static boolean memoryDenied(Object receiver,long offset,long length) {
        if(!initialized||receiver==null||length<=0)return false;
        if(receiver.getClass().isArray())return known(receiver)&&elementDenied(receiver,null,null);
        for(Slot slot:slots(receiver))if(overlap(offset,length,slot.offset,slot.width)
                &&fieldDenied(receiver,slot.field.getDeclaringClass(),slot.field.getName(),null))return true;
        return false;
    }
    static List<Field> unsafeFields() {
        if(!initialized)return List.of();List<Field> result=new ArrayList<>();
        for(Class<?> type:List.of(LivingEntity.class,Player.class,FoodData.class,MobEffectInstance.class,MobEffectInstance.FactorData.class,Optional.class))for(Slot slot:FIELDS.get(type))if(!result.contains(slot.field))result.add(slot.field);
        return result;
    }
}
