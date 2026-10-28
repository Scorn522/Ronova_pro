package dev.ronova.pro;

import java.nio.file.*;
import java.util.*;
import java.util.jar.JarFile;

/** The installed payload is the inventory; no classpath or disk-wide discovery occurs here. */
final class RuntimeClasses {
    private static volatile boolean prepared;
    private RuntimeClasses(){}
    static synchronized void prepare()throws ReflectiveOperationException {
        if(prepared)return;
        ProRuntime.prepareControlOwner();
        SortedSet<String> names=new TreeSet<>();
        Set<String> interfaces=new HashSet<>();
        try{
            Path path=net.minecraftforge.fml.ModList.get().getModFileById("ronova_pro").getFile().getFilePath();
            if(Files.isDirectory(path)){
                Path classes=path.resolve("dev/ronova/pro");
                try(var files=Files.walk(classes)){for(Path entry:files.filter(p->p.toString().endsWith(".class")).toList()){String name="dev.ronova.pro."+classes.relativize(entry).toString().replace('\\','.').replace('/','.').replaceFirst("\\.class$","");names.add(name);if(name.startsWith("dev.ronova.pro.mixin."))try(var input=Files.newInputStream(entry)){if(interfaceClass(input))interfaces.add(name);}}}
            }else try(JarFile jar=new JarFile(path.toFile())){
                var entries=jar.entries();while(entries.hasMoreElements()){var entry=entries.nextElement();String pathName=entry.getName();if(pathName.startsWith("dev/ronova/pro/")&&pathName.endsWith(".class")){String name=pathName.substring(0,pathName.length()-6).replace('/','.');names.add(name);if(name.startsWith("dev.ronova.pro.mixin."))try(var input=jar.getInputStream(entry)){if(interfaceClass(input))interfaces.add(name);}}}
            }
        }catch(java.io.IOException failure){throw new ReflectiveOperationException("RUNTIME_CLASS_INVENTORY_UNAVAILABLE",failure);}
        boolean client=net.minecraftforge.fml.loading.FMLEnvironment.dist.isClient();ClassLoader loader=ProRuntime.class.getClassLoader();
        for(String name:names){
            // Mixin definitions have their own loader lifecycle; they are not policy callbacks.
            if(name.startsWith("dev.ronova.pro.mixin.")&&!interfaces.contains(name)||name.startsWith("dev.ronova.pro.prelaunch."))continue;
            if(!client&&(name.startsWith("dev.ronova.pro.Client")||name.startsWith("dev.ronova.pro.WeaponScreen")||name.startsWith("dev.ronova.pro.mixin.Client")||name.startsWith("dev.ronova.pro.mixin.Access$Client")))continue;
            try{Class.forName(name,true,loader);}catch(LinkageError failure){throw new ReflectiveOperationException("RUNTIME_POLICY_PRELOAD_FAILED:"+name,failure);}
        }
        prepared=true;
    }
    private static boolean interfaceClass(java.io.InputStream stream)throws java.io.IOException {
        var input=new java.io.DataInputStream(stream);if(input.readInt()!=0xcafebabe)throw new java.io.IOException("INVALID_RUNTIME_CLASS");input.readUnsignedShort();input.readUnsignedShort();int count=input.readUnsignedShort();
        for(int i=1;i<count;i++)switch(input.readUnsignedByte()){
            case 1->input.skipNBytes(input.readUnsignedShort());case 3,4->input.skipNBytes(4);
            case 5,6->{input.skipNBytes(8);i++;}case 7,8,16,19,20->input.skipNBytes(2);
            case 9,10,11,12,17,18->input.skipNBytes(4);case 15->input.skipNBytes(3);
            default->throw new java.io.IOException("INVALID_RUNTIME_CONSTANT_POOL");
        }
        return (input.readUnsignedShort()&0x0200)!=0;
    }
}
