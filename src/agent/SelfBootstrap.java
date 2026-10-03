package dev.ronova.pro.agent;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.concurrent.TimeUnit;

/**
 * Launches the short-lived attach helper for this exact process. The player supplies no JVM argument: the mod
 * starts the helper, the helper attaches the agent, and the helper exits.
 *
 * The helper's exit code is not treated as the backend result. After it exits, this class asks the agent for its
 * real installation state, so an attach call that returned while the agent failed to install is reported as a
 * failure rather than a success.
 */
public final class SelfBootstrap {
    private SelfBootstrap() { }

    /** The backend state as the agent itself reports it, not as this helper's exit code suggests. */
    public static String state() {
        try { return String.valueOf(Class.forName("dev.ronova.pro.agent.RecoveryAgent",false,ClassLoader.getSystemClassLoader())
                .getMethod("installState").invoke(null)); }
        catch(ReflectiveOperationException|LinkageError absent) { return "UNAVAILABLE:"+absent.getClass().getSimpleName(); }
    }

    /** @return true only when the helper exited cleanly AND the agent reports a completed installation. */
    public static boolean install(Path agentJar,Path bootstrapJar,long timeoutSeconds) {
        Process helper=null;
        try {
            if(agentJar==null||bootstrapJar==null)return false;
            if(!Files.isReadable(agentJar)||!Files.isReadable(bootstrapJar))return false;
            String javaBinary=Path.of(System.getProperty("java.home"),"bin",
                    System.getProperty("os.name","").toLowerCase().contains("win")?"java.exe":"java").toString();
            // The helper is a plain program: it needs its own jar plus the JDK attach module. The game's own
            // module path is deliberately not exposed to it.
            String helperJar=selfLocation();
            if(helperJar==null) { report("RONOVA_SELF_BOOTSTRAP_HELPER_LOCATION_UNAVAILABLE");return false; }
            String encoded=java.util.Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(bootstrapJar.toAbsolutePath().toString().getBytes(StandardCharsets.UTF_8));
            ProcessBuilder builder=new ProcessBuilder(javaBinary,"--add-modules","jdk.attach","-cp",helperJar,
                    AttachMain.class.getName(),
                    Long.toString(ProcessHandle.current().pid()),
                    agentJar.toAbsolutePath().toString(),
                    "base64:"+encoded);
            builder.redirectErrorStream(true);
            helper=builder.start();
            // The helper is drained on a separate thread so a full pipe buffer can never block it, and the wait
            // below is a real deadline rather than one that only starts after the process has already finished.
            StringBuffer output=new StringBuffer();
            Thread reader=drain(helper,output);
            if(!helper.waitFor(timeoutSeconds,TimeUnit.SECONDS)) {
                helper.destroyForcibly();
                report("RONOVA_SELF_BOOTSTRAP_TIMEOUT");
                return false;
            }
            reader.join(TimeUnit.SECONDS.toMillis(5));
            if(helper.exitValue()!=0) {
                String text=output.toString().trim();
                report(text.isEmpty()?"RONOVA_SELF_BOOTSTRAP_FAILED":text);
                return false;
            }
            // The attach call returning is not the backend result; the agent's own state is.
            String state=state();
            if(state.equals("INSTALLED_FULL"))return true;
            report("RONOVA_SELF_BOOTSTRAP_BACKEND_NOT_INSTALLED:"+state);
            return false;
        } catch(Throwable unavailable) {
            report("RONOVA_SELF_BOOTSTRAP_UNAVAILABLE:"+unavailable.getClass().getSimpleName());
            return false;
        } finally {
            if(helper!=null&&helper.isAlive())helper.destroyForcibly();
        }
    }

    private static Thread drain(Process process,StringBuffer sink) {
        Thread thread=new Thread(()->{
            try(InputStream input=process.getInputStream()) {
                byte[] buffer=new byte[4096];
                for(int read;(read=input.read(buffer))>=0;) {
                    int remaining=16384-sink.length();
                    if(remaining>0)sink.append(new String(buffer,0,Math.min(read,remaining),StandardCharsets.UTF_8));
                }
            } catch(IOException ignored) { }
        },"ronova-attach-output");
        thread.setDaemon(true);
        thread.start();
        return thread;
    }

    /** The agent jar that contains this class; it also carries the attach helper as its main entry. */
    private static String selfLocation() {
        try {
            var source=SelfBootstrap.class.getProtectionDomain().getCodeSource();
            if(source==null||source.getLocation()==null)return null;
            Path path=Path.of(source.getLocation().toURI());
            return Files.isRegularFile(path)?path.toAbsolutePath().toString():null;
        } catch(Exception unavailable) { return null; }
    }
    private static void report(String message) { System.err.println(message); }
}
