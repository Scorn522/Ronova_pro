package dev.ronova.pro.bootstrap;
import java.io.*;
import java.nio.file.*;
import java.security.*;
import java.util.HexFormat;
/** Optional exact Object.clone JNI call boundary. Other native bindings remain unsupported. */
public final class NativeControl {
    private static volatile boolean available;
    private static volatile String state="NOT_ATTEMPTED";
    private NativeControl() {}
    public static synchronized String install(Path bootstrapJar) {
        if(!state.equals("NOT_ATTEMPTED"))return state;
        if(!System.getProperty("os.name","").startsWith("Windows")||!System.getProperty("os.arch","").equals("amd64"))
            return state="OBJECT_CLONE_JNI_UNSUPPORTED_PLATFORM";
        try(java.util.jar.JarFile payload=new java.util.jar.JarFile(bootstrapJar.toFile())) {
            var entry=payload.getJarEntry("ronova-native/windows-x86_64/ronova-pro-control.dll");
            if(entry==null)return state="OBJECT_CLONE_JNI_PAYLOAD_ABSENT";
            byte[] bytes;try(InputStream input=payload.getInputStream(entry)) { bytes=input.readAllBytes(); }
            String digest=hash(bytes);
            Path directory=Path.of(System.getProperty("user.dir","."),"ronova-pro","native",digest).toAbsolutePath();Files.createDirectories(directory);
            Path dll=directory.resolve("ronova-pro-control.dll");
            if(!Files.isRegularFile(dll)||!hash(Files.readAllBytes(dll)).equals(digest)) {
                Path temporary=Files.createTempFile(directory,"control-",".tmp");
                try { Files.write(temporary,bytes);Files.move(temporary,dll,StandardCopyOption.REPLACE_EXISTING); }
                finally { Files.deleteIfExists(temporary); }
            }
            System.load(dll.toString());if(abi()!=1)throw new UnsatisfiedLinkError("NATIVE_CONTROL_ABI");
            available=true;return state="OBJECT_CLONE_JNI_READY";
        } catch(IOException|RuntimeException|LinkageError failure) { return state="OBJECT_CLONE_JNI_UNAVAILABLE:"+failure.getClass().getSimpleName(); }
    }
    private static String hash(byte[] bytes) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); }
        catch(NoSuchAlgorithmException impossible) { throw new AssertionError(impossible); }
    }
    public static boolean available() { return available; }
    public static String state() { return state+":OTHER_NATIVE_BINDINGS_UNSUPPORTED"; }
    static Object cloneExact(Object source)throws CloneNotSupportedException {
        if(!available)throw new IllegalStateException(state);
        return clone0(source);
    }
    private static native int abi();
    private static native Object clone0(Object source)throws CloneNotSupportedException;
}
