package dev.ronova.pro.validation;

import dev.ronova.pro.*;
import dev.ronova.pro.mixin.Access;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.*;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.fml.common.Mod;

/** One ordinary world driver composes the Stage A cases. It cannot supply final production evidence. */
@Mod("pro_fixture")
public final class WorldFixture {
    @Mod("pro_fixture_alias")
    public static final class GroupAlias { public GroupAlias() { } }
    /** Business calls used by the existing external boundary consumer, outside the stopped module. */
    public static final class GroupReturns {
        public static int effects;
        public GroupReturns() {effects++;}
        public static void action(){effects++;}
        public static boolean bool(){effects++;return true;}
        public static byte bytes(){effects++;return 7;}
        public static char chars(){effects++;return 'x';}
        public static short shorts(){effects++;return 7;}
        public static int ints(){effects++;return 7;}
        public static long longs(){effects++;return 7L;}
        public static float floats(){effects++;return 7F;}
        public static double doubles(){effects++;return 7D;}
        public static Object object(){effects++;return new Object();}
        public static String text(){effects++;return "live";}
        public static UUID identity(){effects++;return UUID.randomUUID();}
        public static int[] array(){effects++;return new int[]{7};}
        public static String[][] matrix(){effects++;return new String[][]{{"live"}};}
        public static java.util.OptionalInt optional(){effects++;return java.util.OptionalInt.of(7);}
        public static java.util.Iterator<?> iterator(){effects++;return List.of(7).iterator();}
        public static java.util.stream.IntStream stream(){effects++;return java.util.stream.IntStream.of(7);}
        public static Future<?> future(){effects++;return new CompletableFuture<>();}
    }
    int phase, age, enteredAt, stoppedTicks;
    int commandCalls;
    boolean groupBackingChecked;
    int removedAt=-1;
    StubbornCow target, neighbor, clone, pending;
    net.minecraft.world.entity.item.ItemEntity groupObject,groupFutureObject;
    Cow groupVanilla,groupFutureVanilla;
    net.minecraft.world.entity.item.ItemEntity groupVanillaItem,groupFutureVanillaItem;
    UUID operation, protection, blockOp, cancelledOp;
    UUID serverOnlyOp, missingClientOp;
    CompoundTag snapshot;
    BlockEntity furnace;
    int lastBurn;
    CountDownLatch gate=new CountDownLatch(1), writerEntered=new CountDownLatch(1);
    final List<String> passed=new ArrayList<>();
    boolean finished;
    public WorldFixture() {
        if(Boolean.getBoolean("ronova.pro.validation")&&"mod-group".equals(System.getProperty("ronova.pro.fixture"))) {
            var bus=net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext.get().getModEventBus();
            ModGroupBlockFixture.BLOCKS.register(bus);ModGroupBlockFixture.TYPES.register(bus);
            MinecraftForge.EVENT_BUS.addListener(EventPriority.LOWEST,this::groupTick);
            MinecraftForge.EVENT_BUS.addListener(this::groupCommands);
            // The hidden listener delegates into java.base, so stubbing this mod's methods alone cannot stop it.
            MinecraftForge.EVENT_BUS.addListener(EventPriority.LOWEST,false,TickEvent.ServerTickEvent.class,System.out::println);
            return;
        }
        if(Boolean.getBoolean("ronova.pro.validation")&&"audit-r6".equals(System.getProperty("ronova.pro.fixture"))) {AuditR6Fixture.BLOCKS.register(net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext.get().getModEventBus());new AuditR6Fixture();return;}
        if(Boolean.getBoolean("ronova.pro.validation")&&"defense".equals(System.getProperty("ronova.pro.fixture"))) {new DefenseFixture();return;}
        if(Boolean.getBoolean("ronova.pro.validation")&&"movement".equals(System.getProperty("ronova.pro.fixture"))) {new MovementFixture();return;}
        if(Boolean.getBoolean("ronova.pro.validation")&&"fault-isolation".equals(System.getProperty("ronova.pro.fixture")))return;
        if(Boolean.getBoolean("ronova.pro.validation")&&"source-module".equals(System.getProperty("ronova.pro.fixture"))) {
            new SourceModuleFixture();return;
        }
        if(Boolean.getBoolean("ronova.pro.validation")&&"raw-backing".equals(System.getProperty("ronova.pro.fixture"))) {
            new RawBackingFixture();return;
        }
        if(Boolean.getBoolean("ronova.pro.validation")&&"alias-method".equals(System.getProperty("ronova.pro.fixture"))) {
            new AliasMethodFixture();return;
        }
        if(Boolean.getBoolean("ronova.pro.validation")&&"source-admission".equals(System.getProperty("ronova.pro.fixture"))) {
            new SourceAdmissionFixture();return;
        }
        if(Boolean.getBoolean("ronova.pro.validation")&&"clear-probe".equals(System.getProperty("ronova.pro.fixture"))) {
            new ClearProbeFixture();return;
        }
        if(Boolean.getBoolean("ronova.pro.validation")&&"guard-probe".equals(System.getProperty("ronova.pro.fixture"))) {
            new GuardProbeFixture();return;
        }
        if(Boolean.getBoolean("ronova.pro.validation")&&"wrapper-respawn".equals(System.getProperty("ronova.pro.fixture"))) {
            new WrapperRespawnFixture();return;
        }
        if(Boolean.getBoolean("ronova.pro.validation")&&System.getProperty("ronova.pro.fixture","").startsWith("chain-")) {
            new ChainFixture();return;
        }
        if(Boolean.getBoolean("ronova.pro.validation")&&"record-references".equals(System.getProperty("ronova.pro.fixture"))) {
            new RecordReferenceFixture();return;
        }
        if(Boolean.getBoolean("ronova.pro.validation")&&"producer-selectors".equals(System.getProperty("ronova.pro.fixture"))) {
            var bus=net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext.get().getModEventBus();
            ProducerSelectorsFixture.BLOCKS.register(bus);ProducerSelectorsFixture.TYPES.register(bus);
            new ProducerSelectorsFixture();return;
        }
        if(Boolean.getBoolean("ronova.pro.validation")&&"task-burst".equals(System.getProperty("ronova.pro.fixture"))) {
            new TaskBurstFixture();return;
        }
        if(Boolean.getBoolean("ronova.pro.validation")&&"task-matrix".equals(System.getProperty("ronova.pro.fixture"))) {
            new TaskMatrixFixture();return;
        }
        if(Boolean.getBoolean("ronova.pro.validation")&&"task-a".equals(System.getProperty("ronova.pro.fixture"))) {
            new TaskMatrixFixture(true);return;
        }
        if(Boolean.getBoolean("ronova.pro.validation")&&"storage-races".equals(System.getProperty("ronova.pro.fixture"))) {
            new StorageRaceFixture();return;
        }
        if(Boolean.getBoolean("ronova.pro.validation")&&"strength".equals(System.getProperty("ronova.pro.fixture"))) {
            new StrengthFixture();return;
        }
        System.out.println("PRO_FIXTURE_ENABLED="+Boolean.getBoolean("ronova.pro.validation"));
        if(Boolean.getBoolean("ronova.pro.validation"))
            MinecraftForge.EVENT_BUS.addListener(EventPriority.LOWEST,this::tick);
    }
    private void groupCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(net.minecraft.commands.Commands.literal("pro_fixture_ping").executes(context->{
            commandCalls++;
            try {Files.writeString(Path.of("mod-group-command.txt"),Integer.toString(commandCalls));}
            catch(java.io.IOException unavailable) {throw new IllegalStateException(unavailable);}
            return 1;
        }));
    }
    private void groupTick(TickEvent.ServerTickEvent event) {
        if(event.phase!=TickEvent.Phase.END)return;
        age++;
        if(age==20) {
            ServerLevel level=event.getServer().overworld();
            int y=level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,2,4);
            target=spawn(level,2,y,"ModGroupTarget");
            groupObject=new StubbornDrop(level,3,y,4);groupObject.setCustomName(Component.literal("ModGroupObject"));
            require(level.addFreshEntity(groupObject),"spawn ModGroupObject");
            groupVanilla=new Cow(EntityType.COW,level);groupVanilla.moveTo(4,y,4,0,0);
            groupVanilla.setCustomName(Component.literal("ModGroupVanillaFactory"));
            require(level.addFreshEntity(groupVanilla),"publish a vanilla body from the actual mod factory");
            groupVanillaItem=new net.minecraft.world.entity.item.ItemEntity(level,4,y,5,new ItemStack(Items.DIAMOND));
            groupVanillaItem.setCustomName(Component.literal("ModGroupVanillaItem"));groupVanillaItem.setUnlimitedLifetime();
            require(level.addFreshEntity(groupVanillaItem),"publish a vanilla item body from the actual mod factory");
            BlockPos blockPos=new BlockPos(8,y,4);
            require(level.setBlock(blockPos,ModGroupBlockFixture.BLOCK.get().defaultBlockState(),3),"place ModGroupBlock");
            require(level.getBlockEntity(blockPos) instanceof ModGroupBlockFixture.Holder,"publish non-ticking block holder");
            require(level.setBlock(blockPos.offset(1,0,0),Blocks.CHEST.defaultBlockState(),3),"place unrelated block holder");
            require(level.getBlockEntity(blockPos.offset(1,0,0))!=null,"publish unrelated block holder");
            try {Files.writeString(Path.of("mod-group-block-pos.txt"),blockPos.getX()+" "+blockPos.getY()+" "+blockPos.getZ());}
            catch(java.io.IOException unavailable) {throw new IllegalStateException(unavailable);}
        }
        if(age==100&&"protect".equals(System.getProperty("ronova.pro.fixture.variant"))) {
            ServerLevel level=event.getServer().overworld();
            int y=level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,5,4);
            neighbor=spawn(level,5,y,"ModGroupFuture");
            groupFutureObject=new StubbornDrop(level,6,y,4);groupFutureObject.setCustomName(Component.literal("ModGroupFutureObject"));
            require(level.addFreshEntity(groupFutureObject),"spawn ModGroupFutureObject");
            groupFutureVanilla=new Cow(EntityType.COW,level);groupFutureVanilla.moveTo(7,y,4,0,0);
            groupFutureVanilla.setCustomName(Component.literal("ModGroupFutureVanillaFactory"));
            require(level.addFreshEntity(groupFutureVanilla),"publish a future vanilla body from the actual mod factory");
            groupFutureVanillaItem=new net.minecraft.world.entity.item.ItemEntity(level,7,y,5,new ItemStack(Items.DIAMOND));
            groupFutureVanillaItem.setCustomName(Component.literal("ModGroupFutureVanillaItem"));groupFutureVanillaItem.setUnlimitedLifetime();
            require(level.addFreshEntity(groupFutureVanillaItem),"publish a future vanilla item from the actual mod factory");
        }
        if(!groupBackingChecked&&age>=80&&"protect".equals(System.getProperty("ronova.pro.fixture.variant"))) {
            ServerLevel level=event.getServer().overworld();
            int y=level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,2,4);
            BlockPos pos=new BlockPos(8,y,4),neighborPos=pos.offset(1,0,0);
            var chunk=level.getChunkAt(pos);
            var map=chunk.getBlockEntities();
            BlockEntity current=map.get(pos);
            require(current instanceof ModGroupBlockFixture.Holder,"protected holder before backing attack");
            map.remove(pos);
            require(map.get(pos)==current,"direct map removal denied");
            map.clear();
            require(map.get(pos)==current,"direct map clear preserves protected holder");
            require(map.get(neighborPos)==null,"direct map clear still removes unrelated holder");
            var entry=map.entrySet().stream().filter(row->row.getKey().equals(pos)).findFirst().orElseThrow();
            entry.setValue(new ModGroupBlockFixture.Holder(pos,level.getBlockState(pos)));
            require(map.get(pos)==current,"direct map entry value write denied");
            try {
                java.lang.reflect.Field removed;
                try {removed=BlockEntity.class.getDeclaredField("remove");}
                catch(NoSuchFieldException mapped) {removed=BlockEntity.class.getDeclaredField("f_58859_");}
                removed.setAccessible(true);
                try {removed.setBoolean(current,true);}
                catch(IllegalAccessException refused) {
                    if(!"RONOVA_PROTECTED_FIELD_WRITE_REFUSED".equals(refused.getMessage()))throw refused;
                }
                require(!removed.getBoolean(current),"reflected raw remove field denied");
                current.setRemoved();
                require(!removed.getBoolean(current),"ordinary raw remove field denied");
                var unsafeAccess=sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
                unsafeAccess.setAccessible(true);
                var unsafe=(sun.misc.Unsafe)unsafeAccess.get(null);
                unsafe.putBoolean(current,unsafe.objectFieldOffset(removed),true);
                require(!removed.getBoolean(current),"Unsafe raw remove field denied");
                java.lang.reflect.Field levelField,posField,mapField;
                try {levelField=BlockEntity.class.getDeclaredField("level");}
                catch(NoSuchFieldException mapped) {levelField=BlockEntity.class.getDeclaredField("f_58857_");}
                try {posField=BlockEntity.class.getDeclaredField("worldPosition");}
                catch(NoSuchFieldException mapped) {posField=BlockEntity.class.getDeclaredField("f_58858_");}
                try {mapField=net.minecraft.world.level.chunk.ChunkAccess.class.getDeclaredField("blockEntities");}
                catch(NoSuchFieldException mapped) {mapField=net.minecraft.world.level.chunk.ChunkAccess.class.getDeclaredField("f_187610_");}
                unsafe.putObject(current,unsafe.objectFieldOffset(levelField),null);
                unsafe.putObject(current,unsafe.objectFieldOffset(posField),pos.offset(10,0,0));
                unsafe.putObject(chunk,unsafe.objectFieldOffset(mapField),new java.util.HashMap<>());
                require(((Access.Block)current).pro$level()==level,"Unsafe block level retained");
                require(((Access.Block)current).pro$pos().equals(pos),"Unsafe block position retained");
                require(chunk.getBlockEntities()==map&&map.get(pos)==current,"Unsafe block container retained");
            } catch(ReflectiveOperationException unavailable) {throw new IllegalStateException(unavailable);}
            groupBackingChecked=true;
            try {Files.writeString(Path.of("mod-group-backing.txt"),"BLOCK_HOLDER_MAP_FIELD_PROTECTED");}
            catch(java.io.IOException unavailable) {throw new IllegalStateException(unavailable);}
        }
        if(age%20==0)try {
            Files.writeString(Path.of("mod-group-ticks.txt"),Integer.toString(age),java.nio.charset.StandardCharsets.UTF_8);
            if("protect".equals(System.getProperty("ronova.pro.fixture.variant"))&&target!=null) {
                ProRuntime runtime=ProRuntime.get(event.getServer());
                String state="current="+runtime.protectionStatus(target)+",removed="+((Access.EntityState)(Object)target).pro$removal()
                        +",health="+target.getHealth()+",indexed="+(event.getServer().overworld().getEntity(target.getUUID())==target);
                if(neighbor!=null) {
                    var rawFuture=(Access.EntityState)(Object)neighbor;
                    var record=new EntityRecords(event.getServer().overworld(),neighbor);
                    state+=";future="+runtime.protectionStatus(neighbor)+",removed="+rawFuture.pro$removal()
                            +",indexed="+(event.getServer().overworld().getEntity(neighbor.getUUID())==neighbor)
                            +",registered="+record.registered()+",bound="+record.bound()+",conflict="+record.conflict()
                            +",callback="+(rawFuture.pro$callback()==null?"null":rawFuture.pro$callback().getClass().getName());
                }
                if(groupObject!=null)state+=";object="+runtime.protectionStatus(groupObject)
                        +",removed="+((Access.EntityState)groupObject).pro$removal()
                        +",indexed="+(event.getServer().overworld().getEntity(groupObject.getUUID())==groupObject);
                if(groupFutureObject!=null)state+=";futureObject="+runtime.protectionStatus(groupFutureObject)
                        +",removed="+((Access.EntityState)groupFutureObject).pro$removal()
                        +",indexed="+(event.getServer().overworld().getEntity(groupFutureObject.getUUID())==groupFutureObject);
                ServerLevel blockLevel=event.getServer().overworld();
                int y=blockLevel.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,2,4);
                state+=";blockHolder="+(blockLevel.getBlockEntity(new BlockPos(8,y,4)) instanceof ModGroupBlockFixture.Holder);
                Files.writeString(Path.of("mod-protect-state.txt"),state,java.nio.charset.StandardCharsets.UTF_8);
            }
        } catch(java.io.IOException unavailable) {throw new IllegalStateException(unavailable);}
    }
    static final class StubbornCow extends Cow {
        int calls;
        net.minecraft.server.level.ServerBossEvent boss;
        StubbornCow(ServerLevel level) { super(EntityType.COW,level); }
        @Override public void tick() { calls++;super.tick(); }
        @Override public void remove(RemovalReason reason) { /* ordinary hostile business override */ }
    }
    static final class StubbornDrop extends net.minecraft.world.entity.item.ItemEntity {
        StubbornDrop(ServerLevel level,double x,double y,double z) {super(level,x,y,z,new ItemStack(Items.STICK));}
    }
    StubbornCow spawn(ServerLevel level,double x,double y,String name) {
        StubbornCow c=new StubbornCow(level);
        c.setPos(x,y,4);c.setNoAi(true);c.setCustomName(Component.literal(name));
        require(level.addFreshEntity(c),"spawn "+name);
        return c;
    }
    void next(int stage) { phase=stage;enteredAt=age; }
    int elapsed() { return age-enteredAt; }
    void pass(String name) { passed.add(name);System.out.println("PRO_CASE_PASS "+name); }
    static void require(boolean condition,String message) { if(!condition)throw new AssertionError(message); }
    void tick(TickEvent.ServerTickEvent event) {
        if(finished || event.phase!=TickEvent.Phase.END)return;
        if("client-policy".equals(System.getProperty("ronova.pro.fixture"))) { policyTick(event);return; }
        var server=event.getServer();var r=ProRuntime.get(server);if(r==null)return;
        age++;
        try {
            if(age>2400)throw new AssertionError("timeout phase="+phase+" operation="+(operation==null?"none":r.query(operation).state()+"/"+r.query(operation).reason()));
            ServerLevel level=server.overworld();
            if(phase==2 && target.isRemoved() && removedAt<0)removedAt=age;
            if(phase==0) {
                if(server.getPlayerList().getPlayers().isEmpty())return;
                var player=server.getPlayerList().getPlayers().get(0);
                level.setChunkForced(0,0,true);
                int y=level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,2,4);
                player.connection.teleport(0,y,0,0,0);
                target=spawn(level,2,y,"ProTarget");
                neighbor=spawn(level,5,y,"ProNeighbor");
                snapshot=target.saveWithoutId(new CompoundTag()).copy();
                next(1);
            } else if(phase==1 && elapsed()>35) {
                require(target.calls>5,"target really ticks before removal");
                require(!new EntityRecords(level,target).participants().isEmpty(),"real required tracker client");
                operation=FixtureCommands.clear(r,target);next(2);
            } else if(phase==2 && r.query(operation).complete()) {
                require(removedAt>=0 && age-removedAt>=20,"completion must survive a real twenty-tick window");
                require(target.isRemoved() && level.getEntity(target.getUUID())!=target,"body and UUID lookup");
                require(new EntityRecords(level,target).remaining().isEmpty(),"all registered records clear");
                require(r.query(operation).reason().equals("REQUIRED_CLIENTS_CONFIRMED"),"independent client confirmation");
                stoppedTicks=target.calls;
                pass("C39_BODY_LOOKUP_SECTION_TICK_TRACKING_CLIENT_O01");
                next(3);
            } else if(phase==3 && elapsed()>12 && r.query(operation).receiptDurable()) {
                require(target.calls==stoppedTicks,"no real ticks after clear");
                require(level.getEntity(neighbor.getUUID())==neighbor && neighbor.calls>0,"neighbor retained");
                boolean admitted=level.addFreshEntity(target);
                require(!admitted && level.getEntity(target.getUUID())!=target,"exact old body re-entry blocked");
                pass("C39_ENTRY_GUARD_UNRELATED_PRESERVED");
                clone=new StubbornCow(level);
                clone.load(snapshot.copy());
                require(!r.query(operation).complete(),"new source immediately invalidates the current receipt");
                require(r.historicalOperation(operation).stream().anyMatch(v->v.startsWith("RECEIPT\t")),"historical receipt retained after invalidation");
                pass("C17_REAL_STABLE_WINDOW_NEW_SOURCE_AND_HISTORY");
                clone.setUUID(UUID.randomUUID());
                clone.setCustomName(Component.literal("ProClone"));
                require(level.addFreshEntity(clone),"normal rebuilt business body initially enters");
                require(!clone.getUUID().equals(target.getUUID()),"new UUID");
                next(4);
            } else if(phase==4 && elapsed()>40 && clone.isRemoved()) {
                require(new EntityRecords(level,clone).remaining().isEmpty(),"automatic reconstructed body cleanup");
                require(level.getEntity(neighbor.getUUID())==neighbor,"clone source isolation");
                require(r.operations().size()>=2,"independent production work for new incarnation");
                pass("C39_PRODUCTION_SAVE_COPY_LOAD_SOURCE_NEW_UUID_REDISPOSITION");
                protection=FixtureCommands.protect(r,neighbor);next(5);
            } else if(phase==5 && r.query(protection).state().equals("PROTECTION_ACTIVE")) {
                neighbor.setHealth(2);neighbor.setRemainingFireTicks(100);
                var manager=(Access.Manager)((Access.Server)level).pro$manager();
                var lookup=(Access.Lookup)manager.pro$lookup();
                lookup.pro$ids().remove(neighbor.getId());
                lookup.pro$uuids().remove(neighbor.getUUID());
                neighbor.setRemoved(net.minecraft.world.entity.Entity.RemovalReason.DISCARDED);
                require(!neighbor.isRemoved(),"real removal entry guard");
                next(6);
            } else if(phase==6 && elapsed()>3) {
                require(neighbor.getHealth()>2 && neighbor.getRemainingFireTicks()<=0,"life and fire state repaired");
                require(new EntityRecords(level,neighbor).registered(),"exact registered records repaired");
                pass("C39_SOFT_LIFE_STATE_LIST_PROTECTION");
                BlockPos pos=new BlockPos(7,neighbor.getBlockY(),4);
                level.setBlock(pos,Blocks.FURNACE.defaultBlockState(),3);
                furnace=level.getBlockEntity(pos);
                require(furnace instanceof AbstractFurnaceBlockEntity,"ordinary furnace source");
                ((AbstractFurnaceBlockEntity)furnace).setItem(0,new ItemStack(Items.RAW_IRON));
                ((AbstractFurnaceBlockEntity)furnace).setItem(1,new ItemStack(Items.COAL));
                next(7);
            } else if(phase==7 && elapsed()>15) {
                require(furnace.saveWithoutMetadata().getShort("BurnTime")>0,"furnace really ticks before clear");
                blockOp=r.clearBlock(level,furnace.getBlockPos());next(8);
            } else if(phase==8 && r.query(blockOp).complete()) {
                lastBurn=furnace.saveWithoutMetadata().getShort("BurnTime");
                require(furnace.isRemoved() && level.getChunkAt(furnace.getBlockPos()).getBlockEntities().get(furnace.getBlockPos())!=furnace,"actual block holder clear");
                next(9);
            } else if(phase==9 && elapsed()>12) {
                require(lastBurn==furnace.saveWithoutMetadata().getShort("BurnTime"),"furnace stops real ticking");
                pass("C39_BLOCK_ENTITY_RECORD_AND_TICK");
                pending=spawn(level,8,neighbor.getY(),"ProPending");
                // Timing fault only: occupy the owned durable writer before its real append/force.
                var journalField=ProRuntime.class.getDeclaredField("journal");journalField.setAccessible(true);
                Object journal=journalField.get(r);
                var writerField=IntentJournal.class.getDeclaredField("writer");writerField.setAccessible(true);
                ((Executor)writerField.get(journal)).execute(()-> {
                    writerEntered.countDown();
                    try { gate.await(10,TimeUnit.SECONDS); }catch(InterruptedException ex){Thread.currentThread().interrupt();}
                });
                next(10);
            } else if(phase==10 && writerEntered.getCount()==0) {
                cancelledOp=FixtureCommands.clear(r,pending);
                neighbor.setHealth(2);neighbor.setRemainingFireTicks(100);
                next(11);
            } else if(phase==11 && elapsed()>12) {
                require(r.query(cancelledOp).state().equals("WAIT_ACK") && !pending.isRemoved(),"HARD waits actual durable force");
                require(neighbor.getHealth()>2 && neighbor.getRemainingFireTicks()<=0,"SOFT not waiting on same blocked writer");
                pass("C37_SOFT_EXECUTES_WHILE_HARD_ACK_DELAYED");
                FixtureCommands.revoke(r,pending);gate.countDown();next(12);
            } else if(phase==12 && elapsed()>15) {
                require(r.query(cancelledOp).state().equals("FENCED") && !pending.isRemoved(),"revocation before final gate");
                pass("C38_ACK_AND_REVOCATION_FINAL_GATE");
                FixtureCommands.revoke(r,neighbor);neighbor.setHealth(3);next(13);
            } else if(phase==13 && elapsed()>4) {
                require(neighbor.getHealth()==3,"new legal state not overwritten after release");
                pass("C16_PROTECTION_GENERATION_RELEASE");
                // An actual untracked entity: another forced chunk, outside this client's tracking range.
                level.setChunkForced(64,0,true);
                int y=level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,1026,4);
                pending=spawn(level,1026,y,"ProServerOnly");
                next(14);
            } else if(phase==14 && elapsed()>5 && new EntityRecords(level,pending).registered()) {
                require(new EntityRecords(level,pending).participants().isEmpty(),"actually no tracking participant");
                serverOnlyOp=FixtureCommands.clear(r,pending);
                r.participant(serverOnlyOp,UUID.randomUUID(),false);
                next(15);
            } else if(phase==15 && r.query(serverOnlyOp).complete()) {
                require(r.query(serverOnlyOp).reason().equals("SERVER_SCOPE_NO_REQUIRED_CLIENT"),"honest server-only scope");
                require(r.query(serverOnlyOp).obligations().values().stream().anyMatch(v->v.startsWith("OPTIONAL_UNOBSERVED")),"missing optional is not a claimed client success");
                require(FixtureCommands.clear(r,pending).equals(serverOnlyOp),"same intent keeps original work key");
                pass("C24_SERVER_ONLY_AND_OPTIONAL_APPLICABILITY");
                pending=spawn(level,1028,pending.getY(),"ProRequiredMissing");
                missingClientOp=FixtureCommands.clear(r,pending);
                r.participant(missingClientOp,UUID.randomUUID(),true);
                next(16);
            } else if(phase==16 && elapsed()>35) {
                require(pending.isRemoved() && !r.query(missingClientOp).complete(),"body clear cannot discharge missing required client");
                require(r.query(missingClientOp).reason().contains("CLIENT_DISCONNECTED"),"specific missing client reason");
                int operationsBefore=r.operations().size();
                try { r.clearPresentation(UUID.randomUUID(),UUID.randomUUID());throw new AssertionError("unsupported presentation accepted"); }
                catch(UnsupportedOperationException expected) {
                    require(expected.getMessage().contains("OWNER_ADAPTER_REQUIRED"),"explicit missing presentation owner");
                }
                require(r.operations().size()==operationsBefore,"presentation rejection cannot create fake Entity work");
                pass("C24_REQUIRED_UNKNOWN_NOT_NOT_APPLICABLE");
                FixtureCommands.cancel(r,missingClientOp);
                next(17);
            } else if(phase==17 && elapsed()>3) {
                require(r.query(missingClientOp).state().equals("FENCED"),"explicit cancellation keeps physical effect but revokes current completion");
                pass("C16_CANCEL_PRESERVES_EFFECT_AND_HISTORY");
                finish(server,null);
            }
        } catch(Throwable ex) { finish(server,ex); }
    }
    void finish(net.minecraft.server.MinecraftServer server,Throwable failure) {
        finished=true;gate.countDown();
        try {
            String result="{\"passed\":"+ (failure==null)+",\"phase\":"+phase+",\"cases\":"+new com.google.gson.Gson().toJson(passed)+",\"failure\":"+new com.google.gson.Gson().toJson(failure==null?null:failure.toString())+"}";
            Files.writeString(Path.of("server-result.json"),result);
            System.out.println("PRO_STAGE_A_FIXTURE_RESULT "+result);
        }catch(Exception ex){ex.printStackTrace();}
        if(failure!=null)failure.printStackTrace();
        server.halt(false);
    }
    private final java.util.Properties policyExchange=new java.util.Properties();
    private net.minecraft.server.level.ServerPlayer policyPlayer;
    private String policyPhase="";
    private static final Path POLICY_EXCHANGE=Path.of("..","exchange").toAbsolutePath().normalize();
    private void policyStage(String name) throws java.io.IOException {
        policyPhase=name;enteredAt=age;policyExchange.setProperty("phase",name);
        Files.createDirectories(POLICY_EXCHANGE);
        try(var writer=Files.newBufferedWriter(POLICY_EXCHANGE.resolve("server.properties"))) { policyExchange.store(writer,"fixture scenario coordination only"); }
    }
    private void policyTick(TickEvent.ServerTickEvent event) {
        var server=event.getServer();var runtime=ProRuntime.get(server);if(runtime==null)return;
        age++;
        try {
            if(Files.exists(POLICY_EXCHANGE.resolve("client-failed.txt")))throw new AssertionError(Files.readString(POLICY_EXCHANGE.resolve("client-failed.txt")));
            if(age>9000||!policyPhase.isEmpty()&&age-enteredAt>2400)throw new AssertionError("client policy timeout phase="+policyPhase);
            String ack=Files.exists(POLICY_EXCHANGE.resolve("client-ack.txt"))?Files.readString(POLICY_EXCHANGE.resolve("client-ack.txt")).trim():"";
            if(policyPhase.isEmpty()) {
                if(server.getPlayerList().getPlayers().isEmpty())return;
                policyPlayer=server.getPlayerList().getPlayers().get(0);
                ServerLevel level=policyPlayer.serverLevel();level.setChunkForced(0,0,true);
                int y=level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,2,4);
                policyPlayer.connection.teleport(0,y+1,0,0,0);
                target=spawn(level,2,y,"ProPolicyGuard");neighbor=spawn(level,5,y,"ProPolicyNeighbour");
                target.boss=new net.minecraft.server.level.ServerBossEvent(Component.literal("fixture exact boss"),
                        net.minecraft.world.BossEvent.BossBarColor.PINK,net.minecraft.world.BossEvent.BossBarOverlay.PROGRESS);
                target.boss.addPlayer(policyPlayer);
                FixtureCommands.protect(runtime,target);
                policyExchange.setProperty("targetId",Integer.toString(target.getId()));
                policyExchange.setProperty("targetUuid",target.getUUID().toString());
                policyExchange.setProperty("neighbourId",Integer.toString(neighbor.getId()));
                policyExchange.setProperty("bossId",target.boss.getId().toString());
                policyStage("ready");
            } else if(policyPhase.equals("ready")&&ack.equals("ready")) {
                pass("CLIENT_INITIAL_PROTECTION_RECEIVED");
                policyPlayer.connection.send(new net.minecraft.network.protocol.game.ClientboundRemoveEntitiesPacket(target.getId(),neighbor.getId()));
                policyPlayer.connection.send(net.minecraft.network.protocol.game.ClientboundBossEventPacket.createRemovePacket(target.boss.getId()));
                policyStage("mixed");
            } else if(policyPhase.equals("mixed")&&ack.equals("mixed")) {
                pass("CLIENT_MIXED_REMOVE_ONLY_EXACT_PROTECTED_ENTRY_WITHHELD");
                FixtureCommands.revoke(runtime,target);
                policyPlayer.connection.send(new net.minecraft.network.protocol.game.ClientboundRemoveEntitiesPacket(target.getId()));
                policyPlayer.connection.send(net.minecraft.network.protocol.game.ClientboundBossEventPacket.createRemovePacket(target.boss.getId()));
                policyStage("revoked");
            } else if(policyPhase.equals("revoked")&&ack.equals("revoked")) {
                pass("CLIENT_REVOKE_PRECEDES_FOLLOWING_REMOVE_PACKET");
                ServerLevel level=policyPlayer.serverLevel();int id=target.getId();
                UUID old=target.getUUID();FixtureCommands.clear(runtime,target);
                require(level.getEntity(old)==null,"old target physically cleared before id reuse");
                clone=new StubbornCow(level);clone.setId(id);clone.setPos(3,target.getY(),4);clone.setNoAi(true);
                clone.setCustomName(net.minecraft.network.chat.Component.literal("ProPolicyReusedId"));
                require(!clone.getUUID().equals(old)&&level.addFreshEntity(clone),"ordinary new identity registration");
                FixtureCommands.protect(runtime,clone);
                policyExchange.setProperty("newUuid",clone.getUUID().toString());policyStage("reused");
            } else if(policyPhase.equals("reused")&&ack.equals("reused")) {
                pass("CLIENT_ID_REUSE_MATCHES_NEW_UUID_ONLY");policyStage("reconnect");
            } else if(policyPhase.equals("reconnect")&&ack.equals("rejoined")) {
                require(!server.getPlayerList().getPlayers().isEmpty(),"rejoined player exists");
                var joined=server.getPlayerList().getPlayers().get(0);
                require(joined.connection!=policyPlayer.connection,"actual new server connection");policyPlayer=joined;
                pass("CLIENT_NEW_CONNECTION_RECEIVES_EXISTING_POLICY");
                FixtureCommands.protect(runtime,policyPlayer);
                policyStage("player-protected");
            } else if(policyPhase.equals("player-protected")&&ack.equals("player-protected")) {
                ServerLevel destination=server.getLevel(net.minecraft.world.level.Level.NETHER);
                require(destination!=null,"nether exists");
                try {
                    policyPlayer.changeDimension(destination,new net.minecraftforge.common.util.ITeleporter() {
                        @Override public net.minecraft.world.level.portal.PortalInfo getPortalInfo(net.minecraft.world.entity.Entity entity,ServerLevel dest,java.util.function.Function<ServerLevel,net.minecraft.world.level.portal.PortalInfo> fallback) {
                            throw new IllegalStateException("FIXTURE_TELEPORT_PREPARATION_FAULT");
                        }
                    });
                    throw new AssertionError("teleport fault not propagated");
                } catch(IllegalStateException expected) { require("FIXTURE_TELEPORT_PREPARATION_FAULT".equals(expected.getMessage()),"exact expected teleport failure"); }
                require(ProRuntime.beginPlayerLifecycle(policyPlayer,destination)==null,"ordinary caller cannot open lifecycle permit");
                require(ProRuntime.preventRemoval(policyPlayer,net.minecraft.world.entity.Entity.RemovalReason.DISCARDED),"exceptional lifecycle exit restored removal guard");
                require(ProRuntime.preventIndexRemoval(policyPlayer),"exceptional lifecycle exit restored index guard");
                pass("PLAYER_FAILED_TELEPORT_SCOPE_CLOSED");
                net.minecraft.world.entity.Entity changed=policyPlayer.changeDimension(destination,new net.minecraftforge.common.util.ITeleporter() {
                    @Override public net.minecraft.world.level.portal.PortalInfo getPortalInfo(net.minecraft.world.entity.Entity entity,ServerLevel dest,java.util.function.Function<ServerLevel,net.minecraft.world.level.portal.PortalInfo> fallback) {
                        return new net.minecraft.world.level.portal.PortalInfo(new net.minecraft.world.phys.Vec3(0,90,0),net.minecraft.world.phys.Vec3.ZERO,0,0);
                    }
                });
                require(changed==policyPlayer&&policyPlayer.serverLevel()==destination,"real changeDimension completes");
                require(runtime.protectionStatus(policyPlayer).startsWith("ACTIVE"),"server protection survives dimension change");
                policyExchange.setProperty("dimension",destination.dimension().location().toString());policyStage("dimension");
            } else if(policyPhase.equals("dimension")&&ack.equals("dimension")) {
                pass("PLAYER_CHANGE_DIMENSION_PROTECTION_HANDOFF");
                var old=policyPlayer;
                policyPlayer=server.getPlayerList().respawn(old,false);
                policyPlayer.connection.player=policyPlayer; // Same caller handoff used by ServerGamePacketListenerImpl.
                require(policyPlayer!=old,"real PlayerList respawn creates a new body");
                require(runtime.protectionStatus(policyPlayer).startsWith("ACTIVE"),"new player inherits protection through actual clone event");
                require(!runtime.protectionStatus(old).startsWith("ACTIVE"),"old player protection retired");
                policyExchange.setProperty("dimension",policyPlayer.serverLevel().dimension().location().toString());policyStage("respawn");
            } else if(policyPhase.equals("respawn")&&ack.equals("respawn")) {
                pass("PLAYER_RESPAWN_EXACT_NEW_BODY_PROTECTED_OLD_RETIRED");
                var previous=policyPlayer;var dimension=previous.serverLevel();
                policyPlayer=server.getPlayerList().respawn(previous,false);
                policyPlayer.connection.player=policyPlayer;
                require(policyPlayer!=previous&&policyPlayer.serverLevel()==dimension,"real same-dimension respawn new player");
                require(runtime.protectionStatus(policyPlayer).startsWith("ACTIVE")&&!runtime.protectionStatus(previous).startsWith("ACTIVE"),"same dimension exact player handoff");
                policyStage("same-dimension-respawn");
            } else if(policyPhase.equals("same-dimension-respawn")&&ack.equals("same-dimension-respawn")) {
                pass("PLAYER_SAME_DIMENSION_RESPAWN_HANDOFF");
                FixtureCommands.revoke(runtime,policyPlayer);policyStage("player-released");
            } else if(policyPhase.equals("player-released")&&ack.equals("player-released")) {
                pass("CLIENT_PLAYER_RELEASE_APPLIED");FixtureCommands.protect(runtime,policyPlayer);policyStage("protected-logout");
            } else if(policyPhase.equals("protected-logout")&&ack.equals("logout-started")&&age-enteredAt>30) {
                require(server.getPlayerList().getPlayers().isEmpty(),"protected player actually logged out");
                require(policyPlayer.serverLevel().getEntity(policyPlayer.getUUID())==null,"logout body not resurrected by maintenance");
                require(!runtime.protectionStatus(policyPlayer).startsWith("ACTIVE"),"logout retires exact old body policy");
                pass("PROTECTED_LOGOUT_ALLOWED_WITHOUT_OFFLINE_BODY_REINSERTION");policyStage("done");
            } else if(policyPhase.equals("done")&&ack.equals("done")) {
                finished=true;Files.writeString(Path.of("client-policy-server-result.txt"),"CLIENT_POLICY_SERVER_PASS\n"+String.join("\n",passed));
                server.halt(false);
            }
        } catch(Throwable failure) {
            finished=true;
            try { Files.createDirectories(POLICY_EXCHANGE);Files.writeString(POLICY_EXCHANGE.resolve("server-failed.txt"),failure.toString());
                Files.writeString(Path.of("client-policy-server-result.txt"),"FAILED\nphase="+policyPhase+"\n"+failure+"\n"+String.join("\n",passed)); }
            catch(java.io.IOException secondary) { failure.addSuppressed(secondary); }
            failure.printStackTrace();server.halt(false);
        }
    }

}
