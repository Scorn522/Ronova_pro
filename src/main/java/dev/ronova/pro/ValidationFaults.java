package dev.ronova.pro;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** Explicit isolated validation only: can interrupt a real path, never provide identity, permission or success. */
final class ValidationFaults {
    private static final String POINT=Boolean.getBoolean("ronova.pro.validation")?System.getProperty("ronova.pro.fault.point",""):"";
    private static final String ACTION=System.getProperty("ronova.pro.fault.action","throw");
    private static final Set<String> FIRED=ConcurrentHashMap.newKeySet();
    private ValidationFaults() { }
    static void hit(String point)throws IOException {
        if(!(POINT.equals(point)||POINT.equals("IO_SEQUENCE")&&Set.of("NATIVE_WRITE","NATIVE_FLUSH","READBACK").contains(point))||!FIRED.add(point))return;
        Path directory=Path.of("ronova-validation");Files.createDirectories(directory);
        Path marker=directory.resolve("fault-reached.txt");
        if(POINT.equals("IO_SEQUENCE"))marker=directory.resolve(point+"-reached.txt");
        try(var channel=FileChannel.open(marker,StandardOpenOption.CREATE_NEW,StandardOpenOption.WRITE)) {
            var bytes=StandardCharsets.UTF_8.encode(point+"\n"+ACTION+"\n");
            while(bytes.hasRemaining())channel.write(bytes);channel.force(true);
        }
        System.err.println("RONOVA_VALIDATION_FAULT "+point+" "+ACTION);
        if(ACTION.equals("halt"))Runtime.getRuntime().halt(86);
        throw new IOException("INJECTED_IO_FAILURE:"+point);
    }
}
