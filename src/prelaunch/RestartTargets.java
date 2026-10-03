package dev.ronova.pro.prelaunch;

import dev.ronova.pro.persistence.JournalLineage;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/** Restores only the dedicated server world actually selected by this launch. */
final class RestartTargets {
    record Selection(Path world,UUID realm,Map<String,String> modes) {}
    static Selection read(Path directory,List<String> arguments)throws IOException {
        List<String> expanded=expand(directory,arguments);
        String target=option(expanded,"--launchTarget");
        boolean server="forgeserver".equals(target)||"forgeserverdev".equals(target)
                ||expanded.contains("net.minecraft.server.Main");
        if(!server)return new Selection(null,null,Map.of());
        String universe=option(expanded,"--universe"),name=option(expanded,"--world");
        if(name==null){Properties properties=new Properties();Path configuration=directory.resolve("server.properties");
            if(Files.isRegularFile(configuration))try(var stream=Files.newInputStream(configuration)){properties.load(stream);}
            name=properties.getProperty("level-name","world");
        }
        Path root=universe==null?directory:directory.resolve(universe).normalize();Path world=root.resolve(name).toAbsolutePath().normalize();
        JournalLineage.History history=JournalLineage.read(world.resolve("ronova-pro"));
        Map<UUID,Set<String>> groups=new LinkedHashMap<>();Map<UUID,String> policies=new LinkedHashMap<>();Map<UUID,String> roots=new HashMap<>();
        for(String line:history.records())if(line.startsWith("GROUP/"))try{
            String[] fields=line.split("\t",-1);if(!fields[0].equals("GROUP/1"))throw new IllegalArgumentException("GROUP_VERSION");
            if(fields[1].equals("STOP")&&(fields.length==6||fields.length==7)){
                UUID group=UUID.fromString(fields[2]);UUID.fromString(fields[3]);if(Long.parseLong(fields[4])<1)throw new IllegalArgumentException("GROUP_GENERATION");
                TreeSet<String> aliases=new TreeSet<>();for(String id:fields[5].split(",")){validId(id);aliases.add(id);}
                String normalized=String.join(",",aliases);
                if(!normalized.equals(fields[5])||!UUID.nameUUIDFromBytes(("ronova-mod-group/1|"+normalized).getBytes(StandardCharsets.UTF_8)).equals(group))throw new IllegalArgumentException("GROUP_IDENTITY");
                Set<String> previous=groups.putIfAbsent(group,Set.copyOf(aliases));if(previous!=null&&!previous.equals(aliases))throw new IllegalArgumentException("GROUP_ROOT_CHANGED");
                String previousRoot=roots.putIfAbsent(group,line);if(previousRoot!=null&&!previousRoot.equals(line))throw new IllegalArgumentException("GROUP_ROOT_CHANGED");
                String policy=mode(fields.length==7?fields[6]:"default");if(previousRoot==null)policies.put(group,policy);
            }else if(fields[1].equals("RETURN")&&fields.length==4){UUID group=UUID.fromString(fields[2]);if(!groups.containsKey(group))throw new IllegalArgumentException("GROUP_RETURN_WITHOUT_ROOT");policies.put(group,mode(fields[3]));}
            else if(fields[1].equals("MEMBER")&&fields.length==4){if(!groups.containsKey(UUID.fromString(fields[2])))throw new IllegalArgumentException("GROUP_MEMBER_WITHOUT_ROOT");UUID.fromString(fields[3]);}
            else if(fields[1].equals("BLOCK")&&fields.length==4){if(!groups.containsKey(UUID.fromString(fields[2])))throw new IllegalArgumentException("GROUP_BLOCK_WITHOUT_ROOT");UUID.fromString(fields[3]);}
            else throw new IllegalArgumentException("GROUP_HISTORY_SHAPE");
        }catch(RuntimeException invalid){throw new IOException("GROUP_HISTORY_NO_EARLY_REPLAY",invalid);}
        Map<String,String> modes=new TreeMap<>();for(var group:groups.entrySet())for(String id:group.getValue()){
            String previous=modes.putIfAbsent(id,policies.get(group.getKey()));if(previous!=null&&!previous.equals(policies.get(group.getKey())))throw new IOException("GROUP_ALIAS_RETURN_CONFLICT");
        }
        return new Selection(world,history.realm(),Map.copyOf(modes));
    }
    static String mode(String value)throws IOException {
        if(!Set.of("default","null","empty","uuid-fixed","uuid-each","invalid-id").contains(value))throw new IOException("INVALID_RETURN_POLICY:"+value);return value;
    }
    private static void validId(String id){if(!id.matches("[a-z0-9_.-]{2,64}")||Set.of("ronova_pro","minecraft","forge","java").contains(id))throw new IllegalArgumentException("INVALID_OR_OWN_MOD_ID:"+id);}
    private static String option(List<String> args,String name)throws IOException {
        String value=null;for(int i=0;i<args.size();i++)if(args.get(i).equals(name)){
            if(i+1==args.size()||value!=null)throw new IOException("AMBIGUOUS_WORLD_LAUNCH_OPTION:"+name);value=args.get(++i);
        }return value;
    }
    private static List<String> expand(Path directory,List<String> args)throws IOException {
        List<String> result=new ArrayList<>();boolean enabled=true;
        for(String arg:args){
            if(enabled&&arg.startsWith("@")&&!arg.startsWith("@@")){
                List<String> file=tokens(Files.readString(directory.resolve(arg.substring(1)),StandardCharsets.UTF_8));
                result.addAll(file);if(file.contains("--disable-@files"))enabled=false;
            }else{result.add(enabled&&arg.startsWith("@@")?arg.substring(1):arg);if(arg.equals("--disable-@files"))enabled=false;}
        }return result;
    }
    private static List<String> tokens(String text){
        List<String> result=new ArrayList<>();StringBuilder token=new StringBuilder();char quote=0;boolean started=false;
        for(int i=0;i<text.length();i++){
            char c=text.charAt(i);
            if(quote!=0){
                if(c==quote){quote=0;continue;}
                if(c=='\\'&&i+1<text.length()){
                    c=text.charAt(++i);if(c=='\r'||c=='\n'){while(i+1<text.length()&&Character.isWhitespace(text.charAt(i+1)))i++;continue;}
                    c=switch(c){case 'n'->'\n';case 'r'->'\r';case 't'->'\t';case 'f'->'\f';default->c;};
                }
                token.append(c);continue;
            }
            if(c=='#'){while(i<text.length()&&text.charAt(i)!='\n'&&text.charAt(i)!='\r')i++;c=' ';}
            if(c=='\''||c=='"'){quote=c;started=true;continue;}
            if(Character.isWhitespace(c)){if(started){result.add(token.toString());token.setLength(0);started=false;}}else{token.append(c);started=true;}
        }
        if(started)result.add(token.toString());return result;
    }
}
