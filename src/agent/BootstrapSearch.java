package dev.ronova.pro.agent;

import java.io.IOException;
import java.lang.instrument.Instrumentation;
import java.nio.file.*;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.jar.JarFile;

/** Windows JPLIS uses a locale code page which can differ from HotSpot's ACP. */
final class BootstrapSearch {
    private static final int MAX_PAYLOAD=32*1024*1024;
    record Opened(JarFile jar,Path payload,String route){}
    private BootstrapSearch(){}

    static Opened append(Instrumentation api,Path input)throws IOException{
        Path source=input.toAbsolutePath().normalize();
        if(!System.getProperty("os.name","").startsWith("Windows")||ascii(source))
            return appendAt(api,source,"DIRECT");
        Throwable previous=null;
        Path relative=relative(source);
        if(relative!=null){
            try{return appendAt(api,relative,"RELATIVE");}
            catch(IllegalArgumentException unsupported){previous=unsupported;}
        }
        // A Chinese filename or a different drive cannot always be expressed
        // as an ASCII relative path. Keep only an identical payload cache,
        // not a second project checkout or a changed game working directory.
        byte[] bytes;
        try(var stream=Files.newInputStream(source)){bytes=stream.readNBytes(MAX_PAYLOAD+1);}
        if(bytes.length==0||bytes.length>MAX_PAYLOAD)throw new IOException("BOOTSTRAP_PAYLOAD_INVALID:"+source);
        String digest;
        try{digest=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));}
        catch(NoSuchAlgorithmException impossible){throw new AssertionError(impossible);}
        try{
            Path local=cache(Path.of("ronova-pro","bootstrap-search"),digest,bytes);
            return appendAt(api,local,"LOCAL_CACHE");
        }catch(IOException|IllegalArgumentException unsupported){
            if(previous!=null)unsupported.addSuppressed(previous);previous=unsupported;
        }
        // Native code may have changed the process working directory, or the
        // launcher may override user.dir. An absolute ASCII temp path needs
        // neither the locale conversion nor a matching working directory.
        Path temporary=Path.of(System.getProperty("java.io.tmpdir")).toAbsolutePath().normalize()
                .resolve("ronova-pro/bootstrap-search");
        if(ascii(temporary)){
            try{
                Path cached=cache(temporary,digest,bytes);
                return appendAt(api,cached,"TEMP_CACHE");
            }catch(IOException|IllegalArgumentException unsupported){unsupported.addSuppressed(previous);throw unsupported;}
        }
        throw new IOException("BOOTSTRAP_NATIVE_SEARCH_PATH_UNAVAILABLE:"+source,previous);
    }
    private static Opened appendAt(Instrumentation api,Path path,String route)throws IOException{
        // Do not absolutize the JarFile name: JPLIS forwards getName() to
        // its native search API. The absolute payload path is only for Java I/O.
        JarFile jar=new JarFile(path.toFile());
        try{
            api.appendToBootstrapClassLoaderSearch(jar);
            return new Opened(jar,path.toAbsolutePath().normalize(),route);
        }catch(RuntimeException|Error failure){
            try{jar.close();}catch(IOException close){failure.addSuppressed(close);}
            throw failure;
        }
    }
    private static Path relative(Path source)throws IOException{
        Path working=Path.of("").toAbsolutePath().normalize();
        if(!working.getRoot().equals(source.getRoot()))return null;
        Path relative=working.relativize(source);
        return ascii(relative)&&Files.isSameFile(relative,source)?relative:null;
    }
    private static boolean ascii(Path path){
        String name=path.toString();
        for(int i=0;i<name.length();i++)if(name.charAt(i)>127)return false;
        return true;
    }
    private static Path cache(Path base,String digest,byte[] bytes)throws IOException{
        Path directory=base.resolve(digest);Files.createDirectories(directory);
        Path target=directory.resolve("ronova-pro-bootstrap.jar");
        if(Files.isRegularFile(target,LinkOption.NOFOLLOW_LINKS)&&Files.size(target)==bytes.length
                &&MessageDigest.isEqual(bytes,Files.readAllBytes(target)))return target;
        Path pending=Files.createTempFile(directory,"bootstrap-",".tmp");
        try{
            Files.write(pending,bytes);
            try{Files.move(pending,target,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);}
            catch(AtomicMoveNotSupportedException unsupported){Files.move(pending,target,StandardCopyOption.REPLACE_EXISTING);}
        }finally{Files.deleteIfExists(pending);}
        return target;
    }
}
