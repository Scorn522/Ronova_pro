package dev.ronova.pro.validation;

import dev.ronova.pro.ProRuntime;
import dev.ronova.pro.mixin.Access;
import java.lang.invoke.*;
import java.lang.reflect.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.level.entity.EntityLookup;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import sun.misc.Unsafe;

/** Actual world-backed objects. The fixture only calls ordinary and raw writers; Core supplies all bindings. */
public final class RawBackingFixture {
    private static final class ConstructorWriter extends Cow {
        ConstructorWriter(ServerLevel level) {super(EntityType.COW,level);}
        ConstructorWriter(ConstructorWriter victim,ServerLevel level) {this(level);victim.deathTime=20;}
        int deathTicks(){return deathTime;}
    }
    private final List<String> facts=new ArrayList<>();
    private int age;
    private boolean done;
    private Cow guarded,neighbor;
    RawBackingFixture(){MinecraftForge.EVENT_BUS.addListener(EventPriority.LOWEST,this::tick);}
    private static void require(boolean value,String reason){if(!value)throw new AssertionError(reason);}
    private static void expectDirectRequestDenied(Runnable action) {
        try {action.run();throw new AssertionError("external direct runtime request was accepted");}
        catch(SecurityException expected) { }
    }
    private static Unsafe unsafe()throws Exception {
        Field singleton=Unsafe.class.getDeclaredField("theUnsafe");singleton.setAccessible(true);return (Unsafe)singleton.get(null);
    }
    private static Object field(Object owner,String name,Unsafe unsafe)throws Exception {
        Field field=null;for(Class<?> type=owner.getClass();type!=null&&field==null;type=type.getSuperclass())
            try {field=type.getDeclaredField(name);}catch(NoSuchFieldException absent) { }
        if(field==null)throw new NoSuchFieldException(name);
        long offset=unsafe.objectFieldOffset(field);
        if(field.getType()==int.class)return unsafe.getInt(owner,offset);
        if(field.getType()==long.class)return unsafe.getLong(owner,offset);
        if(field.getType()==boolean.class)return unsafe.getBoolean(owner,offset);
        return unsafe.getObject(owner,offset);
    }
    private static Cow spawn(ServerLevel level,int x) {
        Cow cow=new Cow(EntityType.COW,level);cow.setPos(x,90,10);cow.setNoAi(true);
        require(level.addFreshEntity(cow),"real spawn");return cow;
    }
    private void tick(TickEvent.ServerTickEvent event) {
        if(done||event.phase!=TickEvent.Phase.END)return;
        var runtime=ProRuntime.get(event.getServer());if(runtime==null)return;
        try {
            if(++age>140)throw new AssertionError("RAW_BACKING_TIMEOUT");
            ServerLevel level=event.getServer().overworld();
            if(age==20) {
                guarded=spawn(level,42);neighbor=spawn(level,46);
                UUID protectedOperation=FixtureCommands.protect(runtime,guarded);runtime.tick();
                expectDirectRequestDenied(()->runtime.revoke(guarded));
                expectDirectRequestDenied(()->runtime.cancel(protectedOperation));
                expectDirectRequestDenied(()->runtime.protect(guarded));
                expectDirectRequestDenied(()->runtime.clear(guarded));
                facts.add("DIRECT_RUNTIME_POLICY_MUTATIONS_REFUSED");
                require(runtime.protectionStatus(guarded).startsWith("ACTIVE"),"real protection");
                ConstructorWriter constructorVictim=new ConstructorWriter(level);
                constructorVictim.setPos(50,90,10);require(level.addFreshEntity(constructorVictim),"constructor victim spawned");
                FixtureCommands.protect(runtime,constructorVictim);
                new ConstructorWriter(constructorVictim,level);
                require(constructorVictim.deathTicks()==0,"constructor direct field write refused on protected body");
                FixtureCommands.revoke(runtime,constructorVictim);
                constructorVictim.remove(Entity.RemovalReason.DISCARDED);
                facts.add("CONSTRUCTOR_EXTERNAL_TARGET_WRITE_REFUSED");
            }
            if(age==35) {
                exercise(level,runtime);
                require(level.getEntity(guarded.getUUID())==guarded,"protected lookup retained");
                require(level.getEntity(neighbor.getUUID())==neighbor,"neighbor retained");
                finish(event,"RAW_BACKING_PASS\n"+String.join("\n",facts));
            }
        }catch(Throwable failure){failure.printStackTrace();finish(event,"FAILED\n"+failure+"\n"+String.join("\n",facts));}
    }
    private void exercise(ServerLevel level,ProRuntime runtime)throws Throwable {
        Unsafe unsafe=unsafe();
        var manager=(Access.Manager)((Access.Server)level).pro$manager();
        var lookup=(Access.Lookup)manager.pro$lookup();
        var sections=(Access.Sections)manager.pro$sections();
        List<Entity> members=null;
        for(var row:sections.pro$all().values()) {
            List<Entity> list=((Access.Group)((Access.Section)row).pro$storage()).pro$all();
            if(list.stream().anyMatch(value->value==guarded)) {members=list;break;}
        }
        require(members instanceof ArrayList<?>,"actual section list");
        int member=members.indexOf(guarded);
        Object[] storage=(Object[])field(members,"elementData",unsafe);
        require(storage[member]==guarded,"actual ArrayList elementData");
        Array.set(storage,member,null);require(storage[member]==guarded,"reflective array setter refused");
        Array.class.getMethod("set",Object.class,int.class,Object.class).invoke(null,storage,member,null);
        require(storage[member]==guarded,"Method.invoke Array.set refused");
        MethodHandles.arrayElementSetter(Object[].class).invokeWithArguments(storage,member,null);
        require(storage[member]==guarded,"arrayElementSetter refused");
        MethodHandles.arrayElementVarHandle(Object[].class).set(storage,member,null);
        require(storage[member]==guarded,"plain VarHandle array store refused");
        System.arraycopy(new Object[]{null},0,storage,member,1);
        require(storage[member]==guarded,"arraycopy refused");
        Arrays.fill(storage,member,member+1,null);
        require(storage[member]==guarded,"Arrays.fill refused");
        require(members.set(member,null)==guarded&&members.get(member)==guarded,"ArrayList.set retained exact member");
        facts.add("SECTION_ARRAY_REFLECTION_METHOD_HANDLE_VARHANDLE_COPY_FILL_AND_LIST_RETAINED");

        Set<UUID> known=manager.pro$known();require(known instanceof HashSet<?>,"known UUID HashSet layout");
        HashMap<?,?> backing=(HashMap<?,?>)field(known,"map",unsafe);
        Object[] table=(Object[])field(backing,"table",unsafe);int cell=-1;
        for(int i=0;i<table.length;i++)for(Object node=table[i];node!=null;node=field(node,"next",unsafe))
            if(guarded.getUUID().equals(field(node,"key",unsafe)))cell=i;
        require(cell>=0,"actual known UUID node");Object original=table[cell];
        Array.set(table,cell,null);require(table[cell]==original&&known.contains(guarded.getUUID()),"HashSet backing table retained");
        facts.add("KNOWN_UUID_HASHSET_NODE_ARRAY_RETAINED");

        Object ids=lookup.pro$ids();int[] keys=(int[])field(ids,"key",unsafe);Object[] values=(Object[])field(ids,"value",unsafe);
        int slot=-1;for(int i=0;i<values.length;i++)if(values[i]==guarded) {slot=i;break;}
        require(slot>=0&&keys[slot]==guarded.getId(),"actual fastutil ID slot");
        Array.set(values,slot,null);require(values[slot]==guarded,"fastutil value array retained");
        int before=keys[slot];Array.setInt(keys,slot,0);require(keys[slot]==before,"fastutil key array retained");
        long offset=unsafe.arrayBaseOffset(int[].class)+(long)slot*unsafe.arrayIndexScale(int[].class);
        unsafe.putInt(keys,offset,0);require(keys[slot]==before,"Unsafe.putInt key retained");
        require(unsafe.getAndSetInt(keys,offset,0)==before&&keys[slot]==before,"Unsafe.getAndSet key retained");
        require(unsafe.getAndAddInt(keys,offset,1)==before&&keys[slot]==before,"Unsafe.getAndAdd key retained");
        unsafe.setMemory(keys,offset,4,(byte)0);require(keys[slot]==before,"Unsafe.setMemory protected key retained");
        unsafe.copyMemory(new int[]{0},unsafe.arrayBaseOffset(int[].class),keys,offset,4);
        require(keys[slot]==before,"Unsafe.copyMemory protected key retained");
        VarHandle primitive=MethodHandles.arrayElementVarHandle(int[].class);
        primitive.set(keys,slot,0);require(keys[slot]==before,"primitive plain VarHandle retained");
        require(!(boolean)primitive.compareAndSet(keys,slot,before,0)&&keys[slot]==before,"primitive CAS retained");
        facts.add("FASTUTIL_KEY_VALUE_DIRECT_UNSAFE_ATOMIC_BULK_VARHANDLE_RETAINED");

        Object[] foreign={guarded,neighbor};Array.set(foreign,0,null);
        require(foreign[0]==null&&foreign[1]==neighbor,"unbound array unaffected");
        int[] ordinary={7};unsafe.setMemory(ordinary,unsafe.arrayBaseOffset(int[].class),4,(byte)0);
        require(ordinary[0]==0,"unbound memory unaffected");
        facts.add("UNBOUND_ARRAY_AND_MEMORY_STILL_WRITABLE");

        Object ticks=((Access.Server)level).pro$ticks();Object live=((Access.Ticks)ticks).pro$active();Field active=null;
        // Production JARs use SRG field names. Locate the actual container we are about to
        // attack by identity, without relying on the development-only name "active".
        for(Field field:ticks.getClass().getDeclaredFields())if(!Modifier.isStatic(field.getModifiers())
                &&Map.class.isAssignableFrom(field.getType())&&unsafe.getObject(ticks,unsafe.objectFieldOffset(field))==live) {
            require(active==null,"tick container field is unambiguous");active=field;
        }
        require(active!=null,"actual tick container field found");long activeOffset=unsafe.objectFieldOffset(active);
        unsafe.putObject(ticks,activeOffset,new it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap<>());
        require(unsafe.getObject(ticks,activeOffset)==live,"whole tick container replacement refused");
        require(((Access.Ticks)ticks).pro$active().get(guarded.getId())==guarded,"tick body retained");
        facts.add("WHOLE_TICK_CONTAINER_REPLACEMENT_REFUSED");

        var sectionKeys=sections.pro$keys();
        long key=net.minecraft.core.SectionPos.asLong(guarded.blockPosition());
        require(sectionKeys.contains(key),"actual protected section key exists");
        require(!sectionKeys.remove(key)&&sectionKeys.contains(key),"primitive section key removal refused");
        var iterator=sectionKeys.iterator();while(iterator.hasNext())if(iterator.nextLong()==key){iterator.remove();break;}
        require(sectionKeys.contains(key),"iterator section-key removal refused");
        long spare=net.minecraft.core.SectionPos.asLong(300,7,300);sectionKeys.add(spare);
        sectionKeys.subSet(key,key+1).clear();require(sectionKeys.contains(key),"subset section-key removal refused");
        sectionKeys.clear();require(sectionKeys.contains(key)&&!sectionKeys.contains(spare),"mixed clear preserves protected key only");
        Object tree=field(sectionKeys,"tree",unsafe);Object count=field(sectionKeys,"count",unsafe);
        Field treeField=sectionKeys.getClass().getDeclaredField("tree"),countField=sectionKeys.getClass().getDeclaredField("count");
        unsafe.putObject(sectionKeys,unsafe.objectFieldOffset(treeField),null);
        unsafe.putInt(sectionKeys,unsafe.objectFieldOffset(countField),0);
        require(field(sectionKeys,"tree",unsafe)==tree&&field(sectionKeys,"count",unsafe).equals(count),"section tree and metadata retained");
        Object idsMap=lookup.pro$ids();int maxFill=(int)field(idsMap,"maxFill",unsafe);
        Field max=idsMap.getClass().getDeclaredField("maxFill");unsafe.putInt(idsMap,unsafe.objectFieldOffset(max),0);
        require((int)field(idsMap,"maxFill",unsafe)==maxFill,"fastutil structural capacity retained");
        facts.add("SECTION_KEYS_REMOVE_ITERATOR_SUBSET_CLEAR_TREE_AND_CAPACITY_RETAINED");

        Field retirement=ProRuntime.class.getDeclaredField("RETIREMENTS");
        ExecutorService shared=(ExecutorService)unsafe.getObject(unsafe.staticFieldBase(retirement),unsafe.staticFieldOffset(retirement));
        shared.shutdownNow();require(!shared.isShutdown(),"Ronova-owned shared resource retained");
        facts.add("OWNED_RESOURCE_EXTERNAL_SHUTDOWN_REFUSED");
        Object bus=MinecraftForge.EVENT_BUS;
        Map<?,?> listeners=(Map<?,?>)field(bus,"listeners",unsafe);int retained=0;
        for(Object listener:new ArrayList<>(listeners.keySet()))if(listener.getClass().getModule()==ProRuntime.class.getModule()
                &&listener.getClass().getName().startsWith("dev.ronova.pro.")) {
            Object registration=listeners.get(listener);MinecraftForge.EVENT_BUS.unregister(listener);
            require(listeners.get(listener)==registration,"own real event registration retained");retained++;
        }
        require(retained>0,"actual own listeners attempted");
        java.util.function.Consumer<TickEvent.ServerTickEvent> unrelated=event->{};
        MinecraftForge.EVENT_BUS.addListener(unrelated);require(listeners.containsKey(unrelated),"unrelated listener registered");
        MinecraftForge.EVENT_BUS.unregister(unrelated);require(!listeners.containsKey(unrelated),"unrelated listener removed normally");
        facts.add("OWN_EVENT_LISTENERS_RETAINED_UNRELATED_LISTENER_REMOVED");
        controlEntries(unsafe);
    }
    private void controlEntries(Unsafe unsafe)throws Exception {
        Class<?> boot=Class.forName("dev.ronova.pro.bootstrap.TaskBridge",false,null);
        Field refs=boot.getDeclaredField("controlObjects");
        Object[] captured=(Object[])unsafe.getObject(unsafe.staticFieldBase(refs),unsafe.staticFieldOffset(refs));int checked=0;
        for(Object value:captured) {
            var reference=(java.lang.ref.Reference<?>)value;Object before=reference.get();if(before==null)continue;
            reference.clear();reference.enqueue();require(reference.get()==before,"critical weak registration retained");checked++;
        }
        require(checked>0,"actual critical registrations attempted");
        facts.add("CRITICAL_REGISTRATION_CLEAR_AND_ENQUEUE_REFUSED");
        Class<?> agent=Class.forName("dev.ronova.pro.agent.RecoveryAgent",false,ClassLoader.getSystemClassLoader());
        Field field=agent.getDeclaredField("instrumentation");
        var api=(java.lang.instrument.Instrumentation)unsafe.getObject(unsafe.staticFieldBase(field),unsafe.staticFieldOffset(field));
        Object manager=field(api,"mRetransfomableTransformerManager",unsafe);
        Object[] entries=(Object[])field(manager,"mTransformerList",unsafe);int removals=0;
        for(Object entry:entries) {
            var transformer=(java.lang.instrument.ClassFileTransformer)field(entry,"mTransformer",unsafe);
            if(transformer.getClass().getName().startsWith("dev.ronova.pro.agent.")) {
                require(!api.removeTransformer(transformer),"own transformer removal refused at actual manager");removals++;
            }
        }
        require(removals==3,"all three actual control transformers retained");
        byte[] raw;
        try(var stream=Field.class.getResourceAsStream("/java/lang/reflect/Field.class")){raw=stream.readAllBytes();}
        var attempts=new java.util.concurrent.atomic.AtomicInteger();
        java.lang.instrument.ClassFileTransformer overwrite=new java.lang.instrument.ClassFileTransformer(){
            @Override public byte[] transform(Module module,ClassLoader loader,String name,Class<?> type,
                    java.security.ProtectionDomain domain,byte[] bytes) {
                if(name.equals("java/lang/reflect/Field")){attempts.incrementAndGet();return raw.clone();}return null;
            }
        };
        api.addTransformer(overwrite,true);
        try {
            Object[] reordered=(Object[])field(manager,"mTransformerList",unsafe);int foreign=-1,firstOwn=-1;
            for(int i=0;i<reordered.length;i++) {
                Object transformer=field(reordered[i],"mTransformer",unsafe);
                if(transformer==overwrite)foreign=i;
                if(firstOwn<0&&transformer.getClass().getName().startsWith("dev.ronova.pro.agent."))firstOwn=i;
            }
            require(foreign>=0&&foreign<firstOwn,"own controls kept after actual later registration");
            api.retransformClasses(Field.class);require(attempts.get()>0,"external overwrite really attempted");
            Field state=agent.getDeclaredField("reflectionGuarded");state.setAccessible(true);
            try {state.setBoolean(null,false);throw new AssertionError("agent field overwrite escaped");}
            catch(IllegalAccessException refused) { }
            Field fire;try{fire=Entity.class.getDeclaredField("f_19831_");}
            catch(NoSuchFieldException development){fire=Entity.class.getDeclaredField("remainingFireTicks");}fire.setAccessible(true);
            int before=((Access.EntityState)guarded).pro$fire();
            try {fire.setInt(guarded,before+100);throw new AssertionError("retransformed Field guard lost");}
            catch(IllegalAccessException refused) { }
            fire.setInt(neighbor,170);require(((Access.EntityState)neighbor).pro$fire()==170,"neighbor still writable after overwrite");
            require(((Access.EntityState)guarded).pro$fire()==before,"protected side effect absent after overwrite");
        } finally {require(api.removeTransformer(overwrite),"unrelated transformer removal works");}
        facts.add("ACTUAL_TRANSFORMER_REMOVE_ORDER_RETRANSFORM_AND_AGENT_FIELD_GUARDS");
    }
    private void finish(TickEvent.ServerTickEvent event,String result) {
        done=true;try {Files.writeString(Path.of("raw-backing-result.txt"),result);}catch(Exception unavailable){unavailable.printStackTrace();}
        event.getServer().halt(false);
    }
}
