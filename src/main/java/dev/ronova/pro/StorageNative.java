package dev.ronova.pro;

import java.io.FileDescriptor;
import java.nio.file.Path;

/** Optional backend. Merely loading Core never loads a DLL or grants storage authority. */
final class StorageNative {
    private static boolean attempted, available;
    private static String reason="NATIVE_NOT_CONFIGURED";
    private StorageNative() { }
    static synchronized boolean available() {
        if(attempted)return available;
        attempted=true;
        String configured=System.getProperty("ronova.pro.storage.native","");
        if(configured.isBlank())return false;
        if(!System.getProperty("os.name","").startsWith("Windows")) { reason="WINDOWS_NTFS_REQUIRED";return false; }
        try {
            Path path=Path.of(configured).toAbsolutePath().normalize();
            System.load(path.toString());
            available=abi0()==2;
            reason=available?"AVAILABLE_RUNTIME_TXF_CHECK_REQUIRED":"NATIVE_ABI_MISMATCH";
        } catch(LinkageError|RuntimeException ex) { reason="NATIVE_UNAVAILABLE:"+ex.getClass().getSimpleName(); }
        return available;
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
    static native long[] finish0(RecoveryStorage.Ticket ticket,long token,RecoveryStorage.Permit permit);
    static native long[] maintain0();
    static native long[] queryTransaction0(String transaction);
}
