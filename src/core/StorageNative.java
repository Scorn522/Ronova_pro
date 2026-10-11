package dev.ronova.pro;

import java.io.FileDescriptor;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/** Loads the bundled storage backend on its first actual use; loading grants no commit authority. */
final class StorageNative {
    private static boolean attempted, available;
    private static String reason="NATIVE_NOT_CONFIGURED";
    private StorageNative() { }
    static synchronized boolean available() {
        if(attempted)return available;
        attempted=true;
        String configured=System.getProperty("ronova.pro.storage.native","");
        if(!System.getProperty("os.name","").startsWith("Windows")||!System.getProperty("os.arch","").equals("amd64")) { reason="WINDOWS_X64_NTFS_REQUIRED";return false; }
        try {
            Path path=configured.isBlank()?bundled():Path.of(configured).toAbsolutePath().normalize();
            System.load(path.toString());
            available=abi0()==3;
            reason=available?"AVAILABLE_RUNTIME_TXF_CHECK_REQUIRED":"NATIVE_ABI_MISMATCH";
        } catch(IOException|LinkageError|RuntimeException ex) { reason="NATIVE_UNAVAILABLE:"+ex.getClass().getSimpleName()+":"+ex.getMessage(); }
        return available;
    }
    private static Path bundled()throws IOException {
        byte[] bytes;
        try(InputStream input=StorageNative.class.getResourceAsStream("/ronova-payload/ronova-pro-storage.dll")) {
            if(input==null)throw new IOException("STORAGE_NATIVE_PAYLOAD_ABSENT");
            bytes=input.readAllBytes();
        }
        if(bytes.length==0)throw new IOException("STORAGE_NATIVE_PAYLOAD_EMPTY");
        String digest=hash(bytes);
        Path directory=Path.of(System.getProperty("user.dir","."),"ronova-pro","native","storage",digest).toAbsolutePath();
        Files.createDirectories(directory);Path dll=directory.resolve("ronova-pro-storage.dll");
        if(!Files.isRegularFile(dll)||!hash(Files.readAllBytes(dll)).equals(digest)) {
            Path temporary=Files.createTempFile(directory,"storage-",".tmp");
            try{Files.write(temporary,bytes);Files.move(temporary,dll,StandardCopyOption.REPLACE_EXISTING);}
            finally{Files.deleteIfExists(temporary);}
        }
        return dll;
    }
    private static String hash(byte[] bytes) {
        try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));}
        catch(NoSuchAlgorithmException impossible){throw new AssertionError(impossible);}
    }
    static synchronized String reason() { available();return reason; }
    /**
     * The state a caller must be able to see before claiming native coverage: whether the ABI actually loaded and
     * which reason applies. Loading a DLL successfully is not by itself a capability.
     */
    static String capability() {
        boolean loaded=available();
        return loaded?"STORAGE_NATIVE_ABI:"+abi0()+":"+reason:"STORAGE_NATIVE_UNAVAILABLE:"+reason;
    }
    static String identity(FileDescriptor fd,String path) {
        return available()?identity0(fd,path):null;
    }
    static native int abi0();
    private static native String identity0(FileDescriptor fd,String path);
    static native long[] prepare0(RecoveryStorage.Ticket ticket);
    static native long[] advance0(RecoveryStorage.Ticket ticket,long token);
    static native long[] finish0(RecoveryStorage.Ticket ticket,long token,RecoveryStorage.Permit permit);
    static native long[] maintain0();
    static native long[] queryTransaction0(String transaction);
}
