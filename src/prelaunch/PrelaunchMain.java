package dev.ronova.pro.prelaunch;

import java.io.InputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.TreeSet;
import java.util.jar.JarFile;

/** Launches the game JVM only after its early Ronova agent and saved target set are ready. */
public final class PrelaunchMain {
    private PrelaunchMain() { }

    public static void main(String[] arguments) throws Exception {
        if(arguments.length<4 || !arguments[0].equals("--java") || !arguments[2].equals("--core"))
            throw new IllegalArgumentException("Usage: prelaunch --java <java-or-javaw.exe> --core <ronova.jar> [game JVM args...]");
        Path java=Path.of(arguments[1]).toAbsolutePath().normalize();
        Path core=Path.of(arguments[3]).toAbsolutePath().normalize();
        Path directory=Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize();
        if(!Files.isRegularFile(java)||!Files.isRegularFile(core))throw new IOException("GAME_JAVA_OR_RONOVA_JAR_MISSING");
        Properties selection=targets(directory.resolve("ronova-prelaunch.properties"));
        RestartTargets.Selection restarted;String restartGap="";
        try{restarted=RestartTargets.read(directory,Arrays.asList(arguments).subList(4,arguments.length));}
        catch(IOException unavailable){restartGap=unavailable.getMessage();restarted=new RestartTargets.Selection(null,null,Map.of());System.err.println("RONOVA_PRELAUNCH_DURABLE_RESTORE_UNAVAILABLE:"+restartGap);}
        TreeSet<String> selectedIds=new TreeSet<>(restarted.modes().keySet());
        for(String id:selection.getProperty("stop","").split(","))if(!id.isEmpty())selectedIds.add(id);
        String selected=String.join(",",selectedIds);
        String protectedIds=selection.getProperty("protect","");
        String remoteAllowed=selection.getProperty("allow_remote_stop","");
        String returns=selection.getProperty("returns","default");
        Path cache=directory.resolve("ronova-pro/bootstrap-prelaunch");Files.createDirectories(cache);
        Path agent=extract(core,cache,"ronova-pro-agent.jar");
        Path bootstrap=extract(core,cache,"ronova-pro-bootstrap.jar");
        Path nativeControl=null,nativeStorage=null;
        if(System.getProperty("os.name","").startsWith("Windows")&&System.getProperty("os.arch","").equals("amd64")) {
            nativeControl=extract(bootstrap,cache,"ronova-native/windows-x86_64/ronova-pro-control.dll","ronova-pro-control.dll");
            nativeStorage=extract(core,cache,"ronova-pro-storage.dll");
        }
        String encoded=Base64.getUrlEncoder().withoutPadding()
                .encodeToString(bootstrap.toString().getBytes(StandardCharsets.UTF_8));
        List<String> command=new ArrayList<>();command.add(java.toString());
        if(!restartGap.isEmpty())command.add("-Dronova.pro.restartGap="+restartGap);
        if(!selected.isEmpty())command.add("-Dronova.pro.bootStop="+selected);
        if(!selected.isEmpty())command.add("-Dronova.pro.bootReturns="+returns);
        for(String id:selectedIds){
            String mode=selection.getProperty("returns."+id,restarted.modes().getOrDefault(id,returns));
            command.add("-Dronova.pro.bootReturns."+id+"="+mode);
        }
        if(restarted.realm()!=null){command.add("-Dronova.pro.bootRealm="+restarted.realm());command.add("-Dronova.pro.bootWorld="+restarted.world());}
        if(!protectedIds.isEmpty())command.add("-Dronova.pro.bootProtect="+protectedIds);
        if(!remoteAllowed.isEmpty())command.add("-Dronova.pro.clientAllowedStop="+remoteAllowed);
        if(nativeControl!=null){command.add("-Dronova.pro.control.native="+nativeControl);command.add("-agentpath:"+nativeControl);}
        if(nativeStorage!=null)command.add("-Dronova.pro.storage.native="+nativeStorage);
        command.add("-javaagent:"+agent+"=base64:"+encoded);
        for(int i=4;i<arguments.length;i++)command.add(arguments[i]);
        System.out.println("RONOVA_PRELAUNCH_READY targets="+(selected.isEmpty()?"NONE":selected));
        ProcessBuilder launch=new ProcessBuilder(command).directory(directory.toFile()).inheritIO();
        if(!selected.isEmpty())launch.environment().put("RONOVA_PRO_BOOT_STOP",selected);
        if(!protectedIds.isEmpty())launch.environment().put("RONOVA_PRO_BOOT_PROTECT",protectedIds);
        Process child=launch.start();
        int code=child.waitFor();
        if(code!=0)System.err.println("RONOVA_GAME_EXIT="+code);
        System.exit(code);
    }

    private static Properties targets(Path configuration) throws IOException {
        if(!Files.isRegularFile(configuration))return new Properties();
        Properties properties=new Properties();
        try(var reader=Files.newBufferedReader(configuration,StandardCharsets.UTF_8)){properties.load(reader);}
        Properties selected=new Properties();
        for(String action:List.of("stop","protect","allow_remote_stop"))
            selected.setProperty(action,normalize(properties.getProperty(action,"")));
        String returns=properties.getProperty("returns","default").trim().toLowerCase(java.util.Locale.ROOT);
        if(!List.of("default","null","empty","uuid-fixed","uuid-each","invalid-id").contains(returns))
            throw new IOException("INVALID_RETURN_POLICY:"+returns);
        selected.setProperty("returns",returns);
        for(String key:properties.stringPropertyNames())if(key.startsWith("returns.")){
            String id=key.substring("returns.".length());if(!normalize(id).equals(id))throw new IOException("INVALID_RETURN_MOD_ID:"+id);
            selected.setProperty(key,RestartTargets.mode(properties.getProperty(key).trim().toLowerCase(java.util.Locale.ROOT)));
        }
        return selected;
    }
    private static String normalize(String selection) throws IOException {
        TreeSet<String> names=new TreeSet<>();
        for(String item:selection.split(",")) {
            String name=item.trim().toLowerCase(java.util.Locale.ROOT);if(name.isEmpty())continue;
            if(!name.matches("[a-z0-9_.-]{2,64}")||name.equals("ronova_pro"))
                throw new IOException("INVALID_OR_OWN_MOD_ID:"+name);
            names.add(name);
        }
        return String.join(",",names);
    }

    private static Path extract(Path core,Path cache,String name) throws Exception {
        return extract(core,cache,"ronova-payload/"+name,name);
    }
    private static Path extract(Path core,Path cache,String entryName,String name) throws Exception {
        byte[] bytes;
        try(JarFile jar=new JarFile(core.toFile())) {
            var entry=jar.getJarEntry(entryName);
            if(entry==null||entry.getSize()>32*1024*1024)throw new IOException("RONOVA_PAYLOAD_MISSING_OR_OVERSIZE:"+name);
            try(InputStream stream=jar.getInputStream(entry)){bytes=stream.readNBytes(32*1024*1024+1);}
        }
        if(bytes.length==0||bytes.length>32*1024*1024)throw new IOException("RONOVA_PAYLOAD_INVALID:"+name);
        String digest=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        Path folder=cache.resolve(digest);Files.createDirectories(folder);
        Path target=folder.resolve(name);
        if(Files.isRegularFile(target)&&Files.size(target)==bytes.length
                &&MessageDigest.isEqual(bytes,Files.readAllBytes(target)))return target;
        Path temporary=Files.createTempFile(folder,"ronova-", ".tmp");
        try {
            Files.write(temporary,bytes);
            try {Files.move(temporary,target,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);}
            catch(java.nio.file.AtomicMoveNotSupportedException unsupported) {
                Files.move(temporary,target,StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {Files.deleteIfExists(temporary);}
        return target;
    }
}
