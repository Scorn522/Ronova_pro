package dev.ronova.pro.persistence;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.*;
import java.util.*;

/** Actual local artifact identities shared by the early launcher and the world journal. */
public final class ModCarriers {
    private ModCarriers(){}
    public static String describe(Path supplied)throws IOException{
        Path path=supplied.toRealPath();MessageDigest digest=digest();
        if(Files.isRegularFile(path))update(digest,path);
        else if(Files.isDirectory(path)){
            // Exploded development Mods are real carriers too. Hash once at binding,
            // never during an effect query or a tick.
            try(var children=Files.walk(path)){
                for(Path file:children.filter(Files::isRegularFile).sorted(Comparator.comparing(file->path.relativize(file).toString())).toList()){
                    byte[] name=path.relativize(file).toString().replace('\\','/').getBytes(StandardCharsets.UTF_8);
                    digest.update(ByteBuffer.allocate(12).putInt(name.length).putLong(Files.size(file)).array());digest.update(name);update(digest,file);
                }
            }
        }else throw new IOException("MOD_CARRIER_NOT_REGULAR:"+path);
        return JournalLineage.encodeText(path.toString())+":"+HexFormat.of().formatHex(digest.digest());
    }
    private static void update(MessageDigest digest,Path path)throws IOException{
        try(InputStream input=Files.newInputStream(path)){byte[] bytes=new byte[16384];for(int count;(count=input.read(bytes))>=0;)if(count!=0)digest.update(bytes,0,count);}
    }
    private static MessageDigest digest(){try{return MessageDigest.getInstance("SHA-256");}catch(NoSuchAlgorithmException impossible){throw new AssertionError(impossible);}}
    public static Map<String,String> capture(Properties files,Collection<String> selected)throws IOException{
        Set<String> wanted=new HashSet<>(selected);Map<String,String> identities=new TreeMap<>();
        for(String file:files.stringPropertyNames()){
            List<String> aliases=Arrays.asList(files.getProperty(file).split(","));if(Collections.disjoint(wanted,aliases))continue;
            String descriptor=describe(Path.of(file));
            for(String id:aliases){validId(id);String prior=identities.putIfAbsent(id,descriptor);
                if(prior!=null&&!prior.equals(descriptor))throw new IOException("AMBIGUOUS_MOD_CARRIER:"+id);}
        }
        return Map.copyOf(identities);
    }
    public static String selection(Map<String,String> identities,Collection<String> ids)throws IOException{
        StringBuilder rows=new StringBuilder();
        for(String id:new TreeSet<>(ids)){
            validId(id);String descriptor=identities.get(id);if(descriptor==null)throw new IOException("MOD_CARRIER_MISSING:"+id);
            validDescriptor(descriptor);if(rows.length()!=0)rows.append('\n');rows.append(id).append('\t').append(descriptor);
        }
        return JournalLineage.encodeText(rows.toString());
    }
    public static Map<String,String> entries(String binding)throws IOException{
        try{
            String text=JournalLineage.decodeText(binding);if(!JournalLineage.encodeText(text).equals(binding))throw new IOException("NONCANONICAL_MOD_CARRIER_BINDING");
            Map<String,String> entries=new TreeMap<>();
            if(!text.isEmpty())for(String row:text.split("\n",-1)){
                String[] fields=row.split("\t",-1);if(fields.length!=2)throw new IOException("INVALID_MOD_CARRIER_ROW");
                validId(fields[0]);validDescriptor(fields[1]);if(entries.putIfAbsent(fields[0],fields[1])!=null)throw new IOException("DUPLICATE_MOD_CARRIER_ROW");
            }
            if(!selection(entries,entries.keySet()).equals(binding))throw new IOException("UNORDERED_MOD_CARRIER_BINDING");return Map.copyOf(entries);
        }catch(IllegalArgumentException invalid){throw new IOException("INVALID_MOD_CARRIER_BINDING",invalid);}
    }
    private static void validDescriptor(String descriptor)throws IOException{
        int split=descriptor.lastIndexOf(':');if(split<0||!descriptor.substring(split+1).matches("[0-9a-f]{64}"))throw new IOException("INVALID_MOD_CARRIER_IDENTITY");
        String path=JournalLineage.decodeText(descriptor.substring(0,split));if(!Path.of(path).isAbsolute()||!JournalLineage.encodeText(path).equals(descriptor.substring(0,split)))throw new IOException("INVALID_MOD_CARRIER_PATH");
    }
    public static void validId(String id)throws IOException{
        if(!id.matches("[a-z0-9_.-]{2,64}")||Set.of("ronova_pro","minecraft","forge","java").contains(id))throw new IOException("INVALID_OR_OWN_MOD_ID:"+id);
    }
}
