package audit.jni.retirement;

/** Loaded from the check's actual named Mod jar, with its own native image. */
public final class HookTarget {
    private HookTarget() {}
    public static long[] install(String absoluteDll) {
        System.load(absoluteDll);
        return install0();
    }
    private static native long[] install0();
}
