package dev.ronova.pro;

import java.io.*;
import java.net.URLClassLoader;
import java.nio.file.*;
import java.security.*;
import java.util.*;
import java.util.jar.*;

/**
 * Zero-argument startup.
 *
 * The player drops the release JAR into mods and starts Forge normally. This class finds the agent and bootstrap
 * payloads that ship inside that same JAR, verifies their digest, extracts them into a Ronova-owned cache
 * directory, and has a short-lived helper attach the agent to this exact process. No launcher profile, system
 * setting or hand-written JVM argument is involved, and nothing outside the Ronova cache directory is touched.
 *
 * A missing or unverifiable payload is reported with its exact reason. The mod never silently continues as a
 * weaker "core only" install and never claims the early backend is present when it is not.
 */
public final class ProBootstrap {
    private static final String CACHE_DIRECTORY="ronova-pro/bootstrap";
    private static String state="NOT_ATTEMPTED";
    private static String detail="";

    private ProBootstrap() { }

    public static synchronized String state() { return state+(detail.isEmpty()?"":":"+detail)
            +(ProRuntime.productionControlsInstalled()?"":":CORE_CONTROLS="+ProRuntime.dispatchState()); }
    public static synchronized boolean available() { return (state.equals("ATTACHED")||state.equals("ALREADY_INSTALLED"))
            &&detail.startsWith("INSTALLED_FULL")&&ProRuntime.productionControlsInstalled(); }

    /** Called once, as early as the mod can run. Safe to call again: an already installed agent is left alone. */
    public static synchronized void install() {
        if(state.equals("ATTACHED")||state.equals("ALREADY_INSTALLED"))return;
        try {
            try {
                Class<?> active=Class.forName("dev.ronova.pro.agent.RecoveryAgent",false,ClassLoader.getSystemClassLoader());
                String actual=String.valueOf(active.getMethod("installState").invoke(null));
                if(actual.equals("INSTALLED_FULL")) { state="ALREADY_INSTALLED";detail=actual;return; }
            } catch(ClassNotFoundException absent) { }
            // Read our own module resource: Forge UnionFileSystem code sources are not ordinary JarFile paths.
            Path cache=Path.of(System.getProperty("user.dir",".")).resolve(CACHE_DIRECTORY).toAbsolutePath().normalize();
            Files.createDirectories(cache);
            Path agent=extract(cache,true);
            Path bootstrap=extract(cache,false);
            if(agent==null) { fail("AGENT_PAYLOAD_NOT_SHIPPED");return; }
            if(bootstrap==null) { fail("BOOTSTRAP_PAYLOAD_NOT_SHIPPED");return; }
            String outcome=handOver(agent,bootstrap);
            if(!outcome.startsWith("INSTALLED")) { fail(outcome);return; }
            state="ATTACHED";
            detail=outcome+":"+agent.getFileName();
        } catch(ReflectiveOperationException|IOException|RuntimeException|LinkageError unavailable) {
            fail(unavailable.getClass().getSimpleName()+":"+String.valueOf(unavailable.getMessage()));
        }
    }

    /**
     * Loads the agent payload with its own loader and runs the hand-over there. The mod's own loader does not
     * contain those classes, so a plain Class.forName would never find them; the payload is real code, it is
     * resolved from the extracted file. The context loader is set for the call and restored afterwards.
     */
    private static String handOver(Path agent,Path bootstrap)throws ReflectiveOperationException,IOException {
        try(URLClassLoader loader=new URLClassLoader(
                new java.net.URL[]{agent.toUri().toURL(),bootstrap.toUri().toURL()},
                ProBootstrap.class.getClassLoader())) {
            Class<?> installer=Class.forName("dev.ronova.pro.agent.SelfBootstrap",true,loader);
            ClassLoader previous=Thread.currentThread().getContextClassLoader();
            try {
                Thread.currentThread().setContextClassLoader(loader);
                Object installed=installer.getMethod("install",Path.class,Path.class,long.class)
                        .invoke(null,agent,bootstrap,60L);
                if(Boolean.TRUE.equals(installed))
                    return String.valueOf(installer.getMethod("state").invoke(null));
                return "ATTACH_HELPER_REFUSED:"+String.valueOf(installer.getMethod("state").invoke(null));
            } finally { Thread.currentThread().setContextClassLoader(previous); }
        }
    }

    /** Own nested resources work for Forge union modules as well as ordinary JAR class loaders. */
    private static Path extract(Path cache,boolean agent)throws IOException {
        String name=agent?"ronova-pro-agent.jar":"ronova-pro-bootstrap.jar";
        byte[] payload;
        try(InputStream input=ProBootstrap.class.getResourceAsStream("/ronova-payload/"+name)) {
            if(input==null)return null;
            payload=input.readNBytes(32*1024*1024+1);
        }
        if(payload.length==0||payload.length>32*1024*1024||isAgent(payload)!=agent)throw new IOException("INVALID_OWN_PAYLOAD:"+name);
        String expected=digest(payload);Path directory=cache.resolve(expected);Files.createDirectories(directory);
        Path file=directory.resolve(name);
        if(Files.isRegularFile(file,LinkOption.NOFOLLOW_LINKS)&&digest(Files.readAllBytes(file)).equals(expected))return file;
        Path pending=Files.createTempFile(directory,"payload-",".tmp");
        try {
            Files.write(pending,payload);
            try {Files.move(pending,file,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);}
            catch(AtomicMoveNotSupportedException unavailable) {Files.move(pending,file,StandardCopyOption.REPLACE_EXISTING);}
            if(!digest(Files.readAllBytes(file)).equals(expected))throw new IOException("EXTRACTED_PAYLOAD_CHANGED");
        } finally {Files.deleteIfExists(pending);}
        return file;
    }

    private static boolean isAgent(byte[] payload)throws IOException {
        try(JarInputStream stream=new JarInputStream(new ByteArrayInputStream(payload))) {
            Manifest manifest=stream.getManifest();
            if(manifest==null)return false;
            Attributes attributes=manifest.getMainAttributes();
            return attributes.getValue("Premain-Class")!=null||attributes.getValue("Agent-Class")!=null;
        }
    }

    private static String digest(byte[] payload) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(payload)); }
        catch(NoSuchAlgorithmException impossible) { throw new AssertionError(impossible); }
    }

    private static void fail(String reason) { state="UNAVAILABLE";detail=reason;System.err.println("RONOVA_BOOTSTRAP_"+reason); }
}
