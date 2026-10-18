package dev.ronova.pro.prelaunch;

import dev.ronova.pro.persistence.JournalLineage;
import dev.ronova.pro.persistence.GroupLineage;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/** Restores only the dedicated server world actually selected by this launch. */
final class RestartTargets {
    record Selection(Path world,UUID realm,Map<String,String> modes,Map<UUID,GroupLineage.Group> groups) {}
    static Selection read(Path directory,List<String> arguments)throws IOException {
        List<String> expanded=expand(directory,arguments);
        String target=option(expanded,"--launchTarget");
        boolean server="forgeserver".equals(target)||"forgeserverdev".equals(target)
                ||expanded.contains("net.minecraft.server.Main");
        if(!server)return new Selection(null,null,Map.of(),Map.of());
        String universe=option(expanded,"--universe"),name=option(expanded,"--world");
        if(name==null){Properties properties=new Properties();Path configuration=directory.resolve("server.properties");
            if(Files.isRegularFile(configuration))try(var stream=Files.newInputStream(configuration)){properties.load(stream);}
            name=properties.getProperty("level-name","world");
        }
        if(name.isEmpty())throw new IOException("INVALID_WORLD_LAUNCH_NAME");
        Path root=(universe==null?directory:directory.resolve(universe)).toAbsolutePath().normalize();
        Path world=root.resolve(name).toAbsolutePath().normalize();
        if(world.equals(root))throw new IOException("WORLD_LAUNCH_EQUALS_UNIVERSE");
        return readWorld(world);
    }
    static Selection readWorld(Path supplied)throws IOException{
        Path world=supplied.toAbsolutePath().normalize();JournalLineage.History history=JournalLineage.read(world.resolve("ronova-pro"));
        Map<UUID,GroupLineage.Group> groups=GroupLineage.read(history.records());Map<String,String> modes=new TreeMap<>();
        for(var group:groups.values())for(String id:group.aliases()){
            String previous=modes.putIfAbsent(id,group.mode());if(previous!=null&&!previous.equals(group.mode()))throw new IOException("GROUP_ALIAS_RETURN_CONFLICT");
        }
        return new Selection(world,history.realm(),Map.copyOf(modes),groups);
    }
    static String mode(String value)throws IOException {
        if(!Set.of("default","null","empty","uuid-fixed","uuid-each","invalid-id").contains(value))throw new IOException("INVALID_RETURN_POLICY:"+value);return value;
    }
    private static void validId(String id){if(!id.matches("[a-z0-9_.-]{2,64}")||Set.of("ronova_pro","minecraft","forge","java").contains(id))throw new IllegalArgumentException("INVALID_OR_OWN_MOD_ID:"+id);}
    private static String option(List<String> args,String name)throws IOException {
        String value=null;
        for(int i=0;i<args.size();i++){
            String argument=args.get(i);if(argument.equals("--"))break;
            String next;
            if(argument.equals(name)){
                if(i+1==args.size()||args.get(i+1).startsWith("--"))throw new IOException("MISSING_WORLD_LAUNCH_OPTION:"+name);
                next=args.get(++i);
            }else if(argument.startsWith(name+"="))next=argument.substring(name.length()+1);
            else continue;
            if(value!=null)throw new IOException("AMBIGUOUS_WORLD_LAUNCH_OPTION:"+name);
            if(next.isEmpty())throw new IOException("EMPTY_WORLD_LAUNCH_OPTION:"+name);
            value=next;
        }
        return value;
    }
    static Path gameDirectory(Path working,List<String> args)throws IOException{
        String selected=option(expand(working,args),"--gameDir");
        return selected==null?working:working.resolve(selected).toAbsolutePath().normalize();
    }
    static List<String> expand(Path directory,List<String> args)throws IOException {
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
