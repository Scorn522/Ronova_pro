#!/usr/bin/env python3
"""Focused WorldArchive.ticks regression; explicit tick/NBT substitutes, no Forge.

Compiles the unchanged method text from the selected source, not a reimplementation.
Run only for the partial-selection capture defect. Requires a JDK with jdk.compiler.
Optional argument: a baseline WorldArchive.java to demonstrate the original failure.
"""
from pathlib import Path
import subprocess
import sys
import tempfile

source = Path(sys.argv[1]) if len(sys.argv) > 1 else Path(__file__).resolve().parents[1] / "src/core/WorldArchive.java"
text = source.read_text(encoding="utf-8")
start = text.index("    private static <T> ListTag ticks(")
end = text.index("    private static <T> void appendHeld(", start)
method = text[start:end]
harness = r'''
import java.util.*;
public final class WorldTickCaptureCheck {
    record BlockPos(int x, int y, int z) {}
    record Job(BlockPos low, BlockPos high) {
        boolean contains(BlockPos p) {
            return p.x()>=low.x()&&p.x()<=high.x()&&p.y()>=low.y()&&p.y()<=high.y()&&p.z()>=low.z()&&p.z()<=high.z();
        }
    }
    interface TickContainerAccess<T> {}
    record Priority(int value) { int getValue() { return value; } }
    record SavedTick<T>(T type, BlockPos pos, int delay, Priority priority) {}
    record ScheduledTick<T>(T type, BlockPos pos, long triggerTick, Priority priority, long subTickOrder) {}
    record ProtoChunkTicks<T>(List<SavedTick<T>> scheduledTicks) implements TickContainerAccess<T> {}
    record LevelChunkTicks<T>(List<ScheduledTick<T>> values) implements TickContainerAccess<T> {
        java.util.stream.Stream<ScheduledTick<T>> getAll() { return values.stream(); }
    }
    static final class Registry<T> { Object getKey(T type) { return type; } }
    static final class ListTag extends ArrayList<CompoundTag> {}
    static final class CompoundTag extends HashMap<String,Object> {
        void putString(String key, String value) { put(key,value); }
        void putLong(String key, long value) { put(key,value); }
        void putInt(String key, int value) { put(key,value); }
    }
    static final class NbtUtils { static BlockPos writeBlockPos(BlockPos pos) { return pos; } }
__PRODUCTION_METHOD__
    private static int checks;
    static void require(boolean condition, String message) {
        checks++; if(!condition)throw new AssertionError(message);
    }
    public static void main(String[] args) {
        var inside=new BlockPos(1,64,1);var outside=new BlockPos(2,64,1);
        var job=new Job(inside,inside);var priority=new Priority(-1);var registry=new Registry<String>();
        for(String type:List.of("minecraft:stone","minecraft:water")) {
            var mixed=new ProtoChunkTicks<>(List.of(new SavedTick<>(type,outside,3,priority),new SavedTick<>(type,inside,7,priority),new SavedTick<>(type,outside,9,priority)));
            var captured=ticks(mixed,job,100,registry);
            require(captured.size()==1,"partial proto selection must skip outside ticks");
            require(captured.get(0).get("Position").equals(inside),"selected position preserved");
            require(captured.get(0).get("Type").equals(type),"block/fluid type preserved");
            require(captured.get(0).get("Delay").equals(7L),"proto delay preserved");
            require(captured.get(0).get("Priority").equals(-1),"priority preserved");
            require(captured.get(0).get("Order").equals(0L),"selected tick order preserved");
        }
        require(ticks(new ProtoChunkTicks<String>(List.of()),job,100,registry).isEmpty(),"empty proto remains empty");
        var live=ticks(new LevelChunkTicks<>(List.of(new ScheduledTick<>("a",outside,110,priority,41),new ScheduledTick<>("b",inside,120,priority,42))),job,100,registry);
        require(live.size()==1&&live.get(0).get("Type").equals("b"),"live tick filtering unchanged");
        require(live.get(0).get("Delay").equals(20L)&&live.get(0).get("Order").equals(42L),"live timing/order unchanged");
        boolean refused=false;
        try { ticks(new TickContainerAccess<String>() {},job,100,registry); }
        catch(IllegalStateException expected) { refused=expected.getMessage().equals("WORLD_TICK_CONTAINER_UNAVAILABLE"); }
        require(refused,"unsupported container must be rejected rather than saved empty");
        System.out.println("WORLD_TICK_CAPTURE_PASS assertions="+checks+"; METHOD_ONLY_EXPLICIT_SUBSTITUTES_NO_FORGE");
    }
}
'''.replace("__PRODUCTION_METHOD__", method)
with tempfile.TemporaryDirectory(prefix="ronova-world-ticks-") as directory:
    java = Path(directory) / "WorldTickCaptureCheck.java"
    java.write_text(harness, encoding="utf-8")
    subprocess.run(["java", "-m", "jdk.compiler/com.sun.tools.javac.Main", "-source", "17", "-target", "17", "-d", directory, str(java)], check=True)
    subprocess.run(["java", "-cp", directory, "WorldTickCaptureCheck"], check=True)
