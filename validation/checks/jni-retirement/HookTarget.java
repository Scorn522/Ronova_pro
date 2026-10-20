package audit.jni.retirement;

/** Loaded from the check's actual named Mod jar, with its own native image. */
public final class HookTarget {
    private HookTarget() {}
    public static long[] install(String absoluteDll) {
        System.load(absoluteDll);
        return install0();
    }
    private static native long[] install0();

    /** Real JDK atomics, ZIP native calls and direct writes from this Mod's code. */
    public static int startupRoundtrip() throws Exception {
        byte[] input=new byte[128];java.util.Arrays.fill(input,(byte)37);
        byte[] zipped=new byte[256],output=new byte[128];
        java.util.concurrent.atomic.AtomicInteger count=new java.util.concurrent.atomic.AtomicInteger();
        java.util.concurrent.locks.ReentrantLock lock=new java.util.concurrent.locks.ReentrantLock();
        java.nio.ByteBuffer direct=java.nio.ByteBuffer.allocateDirect(64);
        java.util.zip.Deflater encoder=new java.util.zip.Deflater();
        java.util.zip.Inflater decoder=new java.util.zip.Inflater();
        try {
            for(int i=0;i<16;i++){
                lock.lock();
                try{count.incrementAndGet();direct.putFloat(0,i+.5f);
                    if(direct.getFloat(0)!=i+.5f)throw new AssertionError("direct-buffer write changed");
                }finally{lock.unlock();}
                encoder.reset();encoder.setInput(input);encoder.finish();
                int compressed=encoder.deflate(zipped);
                decoder.reset();decoder.setInput(zipped,0,compressed);
                if(decoder.inflate(output)!=input.length||!java.util.Arrays.equals(input,output))
                    throw new AssertionError("actual native ZIP roundtrip changed");
            }
            return count.get();
        }finally{decoder.end();encoder.end();}
    }
}
