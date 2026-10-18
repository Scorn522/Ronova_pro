package dev.ronova.pro.launch;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;
import java.util.jar.JarFile;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/** Declared Mod aliases in one physical carrier, including its nested jars. */
public final class ModMetadata {
    private static final Pattern MOD_ID=Pattern.compile("^\\s*modId\\s*=\\s*['\"]([a-zA-Z0-9_.-]+)['\"]");
    private static final Pattern TABLE=Pattern.compile("^\\s*\\[\\[([^]]+)]]\\s*(?:#.*)?$");
    private ModMetadata(){}

    public static Set<String> ids(InputStream input)throws IOException{
        Set<String> ids=new TreeSet<>();boolean mod=false;
        for(String line:new String(input.readNBytes(1024*1024),StandardCharsets.UTF_8).split("\\R")){
            var table=TABLE.matcher(line);
            if(table.matches()){mod=table.group(1).trim().equals("mods");continue;}
            if(line.stripLeading().startsWith("[")){mod=false;continue;}
            if(mod){var id=MOD_ID.matcher(line);if(id.find())ids.add(id.group(1).toLowerCase(Locale.ROOT));}
        }
        return ids;
    }

    public static Set<String> ids(Path carrier)throws IOException{
        Set<String> ids=new TreeSet<>();
        if(Files.isDirectory(carrier)){
            try(InputStream input=Files.newInputStream(carrier.resolve("META-INF/mods.toml"))){ids.addAll(ids(input));}
        }else try(JarFile jar=new JarFile(carrier.toFile())){
            var descriptor=jar.getJarEntry("META-INF/mods.toml");
            if(descriptor!=null)try(InputStream input=jar.getInputStream(descriptor)){ids.addAll(ids(input));}
            var entries=jar.entries();
            while(entries.hasMoreElements()){
                var entry=entries.nextElement();
                if(!entry.isDirectory()&&nestedJar(entry.getName()))
                    try(InputStream input=jar.getInputStream(entry)){nestedIds(input,ids);}
            }
        }
        return Set.copyOf(ids);
    }

    private static boolean nestedJar(String name){return name.toLowerCase(Locale.ROOT).endsWith(".jar");}
    private static void nestedIds(InputStream input,Set<String> ids)throws IOException{
        // Closing an inner archive must release its inflater without closing
        // the enclosing entry stream: later siblings still belong to this file.
        try(ZipInputStream jar=new ZipInputStream(new FilterInputStream(input){@Override public void close(){}})){
            for(ZipEntry entry;(entry=jar.getNextEntry())!=null;){
                if(entry.isDirectory())continue;
                if(entry.getName().equals("META-INF/mods.toml"))ids.addAll(ids(jar));
                else if(nestedJar(entry.getName()))nestedIds(jar,ids);
            }
        }
    }
}
