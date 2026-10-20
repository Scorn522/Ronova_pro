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
import java.util.Properties;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.Collections;
import java.util.Objects;
import java.util.jar.JarFile;
import dev.ronova.pro.persistence.ModCarriers;
import dev.ronova.pro.launch.ModMetadata;

/** Launches the game JVM only after its early Ronova agent and saved target set are ready. */
public final class PrelaunchMain {
    private static final String DISABLED_WRITER_INTRINSICS=writerIntrinsics();
    private PrelaunchMain() { }

    public static void main(String[] arguments) throws Exception {
        Path working=Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize();
        Path java=Path.of(ProcessHandle.current().info().command().orElseThrow(()->new IOException("GAME_JAVA_UNKNOWN"))).toAbsolutePath().normalize();
        Path core=Path.of(PrelaunchMain.class.getProtectionDomain().getCodeSource().getLocation().toURI()).toAbsolutePath().normalize();
        int first=0;Map<String,String> handoffs=new TreeMap<>();String handoffWorld=null;Path preparedArguments=null;
        while(first<arguments.length&&(arguments[first].equals("--java")||arguments[first].equals("--core")
                ||arguments[first].equals("--prepare-args")||arguments[first].equals("--handoff-world")||arguments[first].startsWith("--handoff-"))){
            String option=arguments[first++];if(first==arguments.length)throw new IllegalArgumentException("MISSING_PRELAUNCH_OPTION:"+option);
            String value=arguments[first++];
            if(option.equals("--prepare-args")){preparedArguments=working.resolve(value).toAbsolutePath().normalize();continue;}
            if(option.equals("--handoff-world")){handoffWorld=value;continue;}
            if(option.startsWith("--handoff-")){
                String action=option.substring("--handoff-".length());if(!Set.of("upgrade","replace","uninstall").contains(action))throw new IllegalArgumentException("UNKNOWN_HANDOFF_ACTION:"+action);
                for(String id:normalize(value).split(",")){if(id.isEmpty())throw new IllegalArgumentException("EMPTY_HANDOFF_SELECTION");String old=handoffs.putIfAbsent(id,action);if(old!=null&&!old.equals(action))throw new IllegalArgumentException("CONFLICTING_HANDOFF_ACTION:"+id);}continue;
            }
            Path supplied=working.resolve(value).toAbsolutePath().normalize();if(option.equals("--java"))java=supplied;else core=supplied;
        }
        // PCL's synchronous before-launch hook consumes only the generated VM
        // options. Invalidate any earlier file before preparation can fail.
        if(preparedArguments!=null)Files.deleteIfExists(preparedArguments);
        List<String> game=new ArrayList<>(Arrays.asList(arguments).subList(first,arguments.length));
        if(game.isEmpty()){
            Path saved=working.resolve("ronova-launch.args");
            if(!Files.isRegularFile(saved))throw new IOException("GAME_LAUNCH_ARGUMENTS_NOT_CONFIGURED:ronova-launch.args");
            game.addAll(RestartTargets.expand(working,List.of("@"+saved)));
        }
        Path directory=RestartTargets.gameDirectory(working,game);
        if(!Files.isRegularFile(java)||!Files.isRegularFile(core))throw new IOException("GAME_JAVA_OR_RONOVA_JAR_MISSING");
        Properties selection=targets(directory.resolve("ronova-prelaunch.properties"));
        RestartTargets.Selection restarted;
        try{restarted=RestartTargets.read(working,game);}
        catch(IOException unavailable){throw new IOException("RONOVA_PRELAUNCH_DURABLE_RESTORE_UNAVAILABLE:"+unavailable.getMessage(),unavailable);}
        if(handoffWorld!=null)restarted=RestartTargets.readWorld(directory.resolve(handoffWorld));
        TreeSet<String> selectedIds=new TreeSet<>(restarted.modes().keySet());
        for(String id:selection.getProperty("stop","").split(","))if(!id.isEmpty())selectedIds.add(id);
        Properties files=modFiles(directory);
        TreeSet<String> carrierIds=new TreeSet<>(selectedIds);carrierIds.addAll(handoffs.keySet());
        for(String id:selection.getProperty("protect","").split(","))if(!id.isEmpty())carrierIds.add(id);
        Map<String,String> carriers=ModCarriers.capture(files,carrierIds);
        Set<String> handed=new TreeSet<>(handoffs.keySet());
        for(var group:restarted.groups().values())if(!Collections.disjoint(group.aliases(),handoffs.keySet()))handed.addAll(group.aliases());
        restarted=ModHandoff.apply(restarted,handoffs,carriers);ModHandoff.requireBindings(restarted,carriers);
        for(String id:carrierIds){String expected=selection.getProperty("carrier."+id);
            boolean same="ABSENT".equals(expected)?!carriers.containsKey(id):Objects.equals(expected,carriers.get(id));
            if(expected!=null&&!same&&!handed.contains(id))throw new IOException("SAVED_MOD_CARRIER_CHANGED_HANDOFF_REQUIRED:"+id);}
        if(!handed.isEmpty())saveCarrierSelection(directory,handed,carriers);
        Map<String,String> modes=new TreeMap<>();
        String returns=selection.getProperty("returns","default");
        for(String id:selectedIds)modes.put(id,selection.getProperty("returns."+id,restarted.modes().getOrDefault(id,returns)));
        expandGroups(selectedIds,modes,files);
        TreeSet<String> protectedSet=new TreeSet<>();for(String id:selection.getProperty("protect","").split(","))if(!id.isEmpty())protectedSet.add(id);
        expandGroups(protectedSet,null,files);
        String selected=String.join(",",selectedIds);
        String protectedIds=String.join(",",protectedSet);
        String remoteAllowed=selection.getProperty("allow_remote_stop","");
        String remoteProtected=selection.getProperty("allow_remote_protect","");
        String isolated=selection.getProperty("isolate","");
        Path cache=directory.resolve("ronova-pro/bootstrap-prelaunch");Files.createDirectories(cache);
        Path fileIndex=cache.resolve("mod-files.properties");
        try(var writer=Files.newBufferedWriter(fileIndex,StandardCharsets.UTF_8)){files.store(writer,"Actual local Mod files prepared before the game JVM");}
        // HotSpot 17's Windows Instrumentation native search path cannot consume every Unicode game path.
        // Keep local rules/index in the game directory and load payloads from the system temporary cache.
        Path payloadCache=Path.of(System.getProperty("java.io.tmpdir")).toAbsolutePath().normalize().resolve("ronova-pro/bootstrap-prelaunch");
        Files.createDirectories(payloadCache);
        Path agent=extract(core,payloadCache,"ronova-pro-agent.jar");
        Path bootstrap=extract(core,payloadCache,"ronova-pro-bootstrap.jar");
        Path nativeControl=null,nativeStorage=null;
        if(System.getProperty("os.name","").startsWith("Windows")&&System.getProperty("os.arch","").equals("amd64")) {
            nativeControl=extract(bootstrap,payloadCache,"ronova-native/windows-x86_64/ronova-pro-control.dll","ronova-pro-control.dll");
            nativeStorage=extract(core,payloadCache,"ronova-pro-storage.dll");
        }
        String encoded=Base64.getUrlEncoder().withoutPadding()
                .encodeToString(bootstrap.toString().getBytes(StandardCharsets.UTF_8));
        List<String> command=new ArrayList<>();command.add(java.toString());
        // HotSpot's ccstrlist accumulates repeated DisableIntrinsic options, so
        // the original game arguments below retain their own disabled entries.
        command.add("-XX:+UnlockDiagnosticVMOptions");
        command.add("-XX:DisableIntrinsic="+DISABLED_WRITER_INTRINSICS);
        command.add("-Dronova.pro.prelaunchPrepared=true");
        command.add("-Dronova.pro.gameDirectory="+directory);
        command.add("-Dronova.pro.modFiles="+fileIndex);
        if(!isolated.isEmpty()){command.add("-Dronova.pro.clientIsolateMods="+isolated);command.add("-Dronova.pro.isolateMods="+isolated);}
        command.add("-Dronova.pro.bootStop="+selected);
        if(!selected.isEmpty())command.add("-Dronova.pro.bootReturns="+returns);
        for(String id:selectedIds){
            String mode=modes.get(id);
            command.add("-Dronova.pro.bootReturns."+id+"="+mode);
        }
        if(restarted.realm()!=null){command.add("-Dronova.pro.bootRealm="+restarted.realm());command.add("-Dronova.pro.bootWorld="+restarted.world());}
        command.add("-Dronova.pro.bootProtect="+protectedIds);
        if(!remoteAllowed.isEmpty())command.add("-Dronova.pro.clientAllowedStop="+remoteAllowed);
        if(!remoteProtected.isEmpty())command.add("-Dronova.pro.clientAllowedProtect="+remoteProtected);
        if(nativeControl!=null){command.add("-Dronova.pro.control.native="+nativeControl);command.add("-agentpath:"+nativeControl);}
        if(nativeStorage!=null)command.add("-Dronova.pro.storage.native="+nativeStorage);
        command.add("-javaagent:"+agent+"=base64:"+encoded);
        if(preparedArguments!=null){
            Files.createDirectories(preparedArguments.getParent());
            List<String> lines=new ArrayList<>();
            for(String argument:command.subList(1,command.size())){
                lines.add("\""+argument.replace("\\","\\\\").replace("\"","\\\"")
                        .replace("\r","\\r").replace("\n","\\n").replace("\t","\\t").replace("\f","\\f")+"\"");
            }
            Path pending=Files.createTempFile(preparedArguments.getParent(),"ronova-jvm-",".tmp");
            try{
                Files.write(pending,lines,StandardCharsets.UTF_8);
                try{Files.move(pending,preparedArguments,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);}
                catch(java.nio.file.AtomicMoveNotSupportedException unsupported){Files.move(pending,preparedArguments,StandardCopyOption.REPLACE_EXISTING);}
            }finally{Files.deleteIfExists(pending);}
            System.out.println("RONOVA_PRELAUNCH_ARGUMENTS_READY:"+preparedArguments);
            return; // No child JVM and no game main in preparation-only mode.
        }
        command.addAll(game);
        // The child premain publishes its actual ready/partial/failed state. This process has only prepared
        // payloads and launch arguments; individual controls decide whether their actual backend is usable.
        System.out.println("RONOVA_PRELAUNCH_PREPARED targets="+(selected.isEmpty()?"NONE":selected));
        ProcessBuilder launch=new ProcessBuilder(command).directory(working.toFile()).inheritIO();
        launch.environment().put("RONOVA_PRO_BOOT_STOP",selected);
        launch.environment().put("RONOVA_PRO_BOOT_PROTECT",protectedIds);
        Process child=launch.start();
        System.out.println("RONOVA_GAME_PID="+child.pid());
        int code=child.waitFor();
        if(code!=0)System.err.println("RONOVA_GAME_EXIT="+code);
        System.exit(code);
    }

    private static Properties modFiles(Path directory)throws IOException{
        Properties files=new Properties();Path mods=directory.resolve("mods");if(!Files.isDirectory(mods))return files;
        try(var paths=Files.list(mods)){
            for(Path file:paths.filter(path->Files.isRegularFile(path)&&path.getFileName().toString().toLowerCase(java.util.Locale.ROOT).endsWith(".jar")).toList()){
                Set<String> ids=ModMetadata.ids(file);
                // An empty value records a completed archive scan, not an early
                // Module reader miss; the Agent can reuse it during class loading.
                files.setProperty(file.toAbsolutePath().normalize().toString(),String.join(",",new TreeSet<>(ids)));
            }
        }
        return files;
    }
    private static void saveCarrierSelection(Path directory,Set<String> ids,Map<String,String> actual)throws IOException{
        Path target=directory.resolve("ronova-prelaunch.properties");Properties values=new Properties();
        if(Files.isRegularFile(target))try(var reader=Files.newBufferedReader(target,StandardCharsets.UTF_8)){values.load(reader);}
        for(String id:ids)values.setProperty("carrier."+id,actual.getOrDefault(id,"ABSENT"));
        Path pending=Files.createTempFile(directory,"ronova-handoff-",".tmp");
        try{
            try(var writer=Files.newBufferedWriter(pending,StandardCharsets.UTF_8)){values.store(writer,"Local selection and completed artifact handoff");}
            try(var file=java.nio.channels.FileChannel.open(pending,java.nio.file.StandardOpenOption.WRITE)){file.force(true);}
            Files.move(pending,target,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);
        }finally{Files.deleteIfExists(pending);}
    }
    private static void expandGroups(TreeSet<String> selected,Map<String,String> modes,Properties files)throws IOException{
        boolean changed;
        do{
            changed=false;
            for(String path:files.stringPropertyNames()){
                TreeSet<String> aliases=new TreeSet<>(Arrays.asList(files.getProperty(path).split(",")));
                if(java.util.Collections.disjoint(selected,aliases))continue;
                if(!java.util.Collections.disjoint(aliases,Set.of("ronova_pro","minecraft","forge","java")))throw new IOException("RESERVED_MOD_IN_SHARED_FILE:"+path);
                String mode=null;
                if(modes!=null)for(String id:aliases)if(selected.contains(id)){
                    String candidate=modes.get(id);if(mode!=null&&!mode.equals(candidate))throw new IOException("SHARED_MOD_FILE_RETURN_CONFLICT:"+path);mode=candidate;
                }
                changed|=selected.addAll(aliases);
                if(modes!=null)for(String id:aliases)modes.put(id,mode);
            }
        }while(changed);
    }

    private static String writerIntrinsics(){
        // Exact OpenJDK 17 writer IDs. Java fallbacks reach the existing Unsafe
        // guards; System.arraycopy separately reaches its actual native guard.
        List<String> names=new ArrayList<>(List.of("_arraycopy","_copyMemory"));
        for(String kind:List.of("Reference","Boolean","Byte","Short","Char","Int","Long","Float","Double"))
            for(String order:List.of("","Volatile","Opaque","Release"))names.add("_put"+kind+order);
        for(String kind:List.of("Short","Char","Int","Long"))names.add("_put"+kind+"Unaligned");
        for(String kind:List.of("Reference","Long","Int","Byte","Short")){
            names.add("_compareAndSet"+kind);
            for(String order:List.of("","Acquire","Release"))names.add("_compareAndExchange"+kind+order);
            for(String order:List.of("","Plain","Acquire","Release"))names.add("_weakCompareAndSet"+kind+order);
        }
        for(String kind:List.of("Int","Long","Byte","Short"))names.add("_getAndAdd"+kind);
        for(String kind:List.of("Int","Long","Byte","Short","Reference"))names.add("_getAndSet"+kind);
        return String.join(",",names);
    }

    private static Properties targets(Path configuration) throws IOException {
        if(!Files.isRegularFile(configuration))return new Properties();
        Properties properties=new Properties();
        try(var reader=Files.newBufferedReader(configuration,StandardCharsets.UTF_8)){properties.load(reader);}
        Properties selected=new Properties();
        for(String action:List.of("stop","protect","allow_remote_stop","allow_remote_protect","isolate"))
            selected.setProperty(action,normalize(properties.getProperty(action,"")));
        for(String key:properties.stringPropertyNames())if(key.startsWith("carrier.")){
            String id=key.substring("carrier.".length());if(!normalize(id).equals(id))throw new IOException("INVALID_CARRIER_MOD_ID:"+id);selected.setProperty(key,properties.getProperty(key));
        }
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
            ModCarriers.validId(name);
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
