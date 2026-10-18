package dev.ronova.pro;

import java.io.*;
import java.net.URLClassLoader;
import java.nio.file.*;
import java.security.*;
import java.util.*;
import java.util.jar.*;

/**
 * Detects the prelaunch/premain installation and offers runtime continuation for an unintegrated launcher.
 * Attaching after Forge started cannot provide pre-JVM or before-main coverage.
 */
public final class ProBootstrap {
    private static final String CACHE_DIRECTORY="ronova-pro/bootstrap";
    private static String state="NOT_ATTEMPTED";
    private static String detail="";
    private static boolean handOverStarted;

    private ProBootstrap() { }

    public static synchronized String state() {
        String actual=agentState();
        String current=(state.equals("ATTACHED")||state.equals("ALREADY_INSTALLED")||state.endsWith("_READY"))&&!actual.equals("INSTALLED_FULL")?"UNAVAILABLE":state;
        return current+":STAGE="+startupStage()+":AGENT="+actual+(detail.isEmpty()?"":":HANDOVER="+detail)
                +(ProRuntime.productionControlsInstalled()?"":":CORE_CONTROLS="+ProRuntime.dispatchState());
    }
    public static synchronized boolean available() {
        return agentState().equals("INSTALLED_FULL")&&ProRuntime.productionControlsInstalled();
    }

    /** Called once, as early as the mod can run. Safe to call again: an already installed agent is left alone. */
    public static synchronized void install() {
        try {
            String actual=agentState();
            if(actual.equals("INSTALLED_FULL")) { state=startupStage().startsWith("PREMAIN")?"PREMAIN_READY":"RUNTIME_CONTINUATION_READY";detail="";return; }
            // Installing, partial and failed agents retain their existing hooks. A second attach cannot repair
            // those hooks, and a timed-out helper may still have an installation running in the target JVM.
            if(!actual.equals("NOT_ATTEMPTED")&&!actual.equals("NOT_PRESENT")) { fail(actual);return; }
            if(handOverStarted) { fail("ATTACH_ALREADY_ATTEMPTED:"+actual);return; }
            // Read our own module resource: Forge UnionFileSystem code sources are not ordinary JarFile paths.
            Path cache=Path.of(System.getProperty("user.dir",".")).resolve(CACHE_DIRECTORY).toAbsolutePath().normalize();
            Files.createDirectories(cache);
            Path agent=extract(cache,true);
            Path bootstrap=extract(cache,false);
            if(agent==null) { fail("AGENT_PAYLOAD_NOT_SHIPPED");return; }
            if(bootstrap==null) { fail("BOOTSTRAP_PAYLOAD_NOT_SHIPPED");return; }
            handOverStarted=true;state="ATTACHING";detail="";
            String outcome=handOver(agent,bootstrap);
            actual=agentState();
            if(!actual.equals("INSTALLED_FULL")) { fail(outcome+":AGENT="+actual);return; }
            state="RUNTIME_CONTINUATION_READY";
            detail=agent.getFileName().toString();
        } catch(ReflectiveOperationException|IOException|RuntimeException|LinkageError unavailable) {
            fail(unavailable.getClass().getSimpleName()+":"+String.valueOf(unavailable.getMessage()));
        }
    }

    private static String agentState() {
        try {
            Class<?> active=Class.forName("dev.ronova.pro.agent.RecoveryAgent",false,ClassLoader.getSystemClassLoader());
            return String.valueOf(active.getMethod("installState").invoke(null));
        } catch(ClassNotFoundException absent) { return "NOT_PRESENT"; }
        catch(ReflectiveOperationException|RuntimeException|LinkageError unavailable) {
            return "UNAVAILABLE:"+unavailable.getClass().getSimpleName()+":"+String.valueOf(unavailable.getMessage());
        }
    }
    private static String startupStage(){
        try{return String.valueOf(Class.forName("dev.ronova.pro.agent.RecoveryAgent",false,ClassLoader.getSystemClassLoader()).getMethod("startupStage").invoke(null));}
        catch(ReflectiveOperationException|RuntimeException|LinkageError unavailable){return "PRELAUNCH_NOT_INTEGRATED:RUNTIME_CONTINUATION";}
    }
    /** Persists the user's complete local selection, including an intentionally empty set. */
    public static synchronized void saveSelection(String[] stopped,String[] protectedIds,Map<String,String> returnModes)throws IOException{
        saveSelection(stopped,protectedIds,returnModes,Map.of(),StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE).getCallerClass());
    }
    public static synchronized void saveSelection(String[] stopped,String[] protectedIds,Map<String,String> returnModes,Map<String,String> carriers)throws IOException{
        saveSelection(stopped,protectedIds,returnModes,carriers,StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE).getCallerClass());
    }
    private static void saveSelection(String[] stopped,String[] protectedIds,Map<String,String> returnModes,Map<String,String> carriers,Class<?> caller)throws IOException{
        if(caller!=ProRuntime.class)throw new SecurityException("ACTUAL_PRO_RUNTIME_REQUIRED");
        Path directory=Path.of(System.getProperty("ronova.pro.gameDirectory",System.getProperty("user.dir","."))).toAbsolutePath().normalize();
        Path file=directory.resolve("ronova-prelaunch.properties");Properties selection=new Properties();
        if(Files.isRegularFile(file))try(var reader=Files.newBufferedReader(file,java.nio.charset.StandardCharsets.UTF_8)){selection.load(reader);}
        selection.setProperty("stop",selectionIds(stopped));selection.setProperty("protect",selectionIds(protectedIds));
        Set<String> current=new HashSet<>();current.addAll(Arrays.asList(stopped));current.addAll(Arrays.asList(protectedIds));
        for(String key:new HashSet<>(selection.stringPropertyNames()))if(key.startsWith("carrier.")&&!current.contains(key.substring("carrier.".length()))&& !selection.getProperty(key).equals("ABSENT"))selection.remove(key);
        for(String id:current)if(carriers.containsKey(id))selection.setProperty("carrier."+id,carriers.get(id));
        for(String key:new HashSet<>(selection.stringPropertyNames()))if(key.startsWith("returns."))selection.remove(key);
        if(returnModes!=null)for(var entry:returnModes.entrySet()){
            String id=selectionIds(new String[]{entry.getKey()}),mode=entry.getValue();
            if(id.isEmpty()||!Set.of("default","null","empty","uuid-fixed","uuid-each","invalid-id").contains(mode))throw new IOException("INVALID_SAVED_RETURN_POLICY:"+id);
            selection.setProperty("returns."+id,mode);
        }
        Files.createDirectories(directory);Path pending=Files.createTempFile(directory,"ronova-selection-",".tmp");
        try{
            try(var writer=Files.newBufferedWriter(pending,java.nio.charset.StandardCharsets.UTF_8)){selection.store(writer,"Local Ronova selection for the next game JVM");}
            try(var channel=java.nio.channels.FileChannel.open(pending,java.nio.file.StandardOpenOption.WRITE)){channel.force(true);}
            Files.move(pending,file,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);
        }finally{Files.deleteIfExists(pending);}
    }
    private static String selectionIds(String[] values)throws IOException{
        TreeSet<String> ids=new TreeSet<>();if(values!=null)for(String raw:values){
            if(raw==null)throw new IOException("INVALID_SAVED_MOD_ID:null");String id=raw.trim().toLowerCase(Locale.ROOT);
            if(!id.matches("[a-z0-9_.-]{2,64}")||Set.of("ronova_pro","minecraft","forge","java").contains(id))throw new IOException("INVALID_SAVED_MOD_ID:"+id);ids.add(id);
        }
        return String.join(",",ids);
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
