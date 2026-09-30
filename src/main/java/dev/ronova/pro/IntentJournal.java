package dev.ronova.pro;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.*;

/** One bounded, ordered durable writer. ACK means force(true) completed, not queue acceptance. */
public final class IntentJournal implements AutoCloseable {
    private static final int VERSION = 5, MAX_FRAME = 65536;
    private final FileChannel file;
    private final FileLock lock;
    private final List<FileChannel> lineageFiles=new ArrayList<>();
    private final List<FileLock> lineageLocks=new ArrayList<>();
    private final ThreadPoolExecutor writer;
    private volatile Throwable failed;
    private final List<String> history = Collections.synchronizedList(new ArrayList<>());
    public final UUID realm;

    public IntentJournal(Path directory) throws IOException {
        Files.createDirectories(directory);
        // Retain every earlier file verbatim and hold its writer lock for this session.
        // V3 adds durable subject/work and resource-retirement semantics; old commands are never recoded.
        try {
            for(int version=1;version<=VERSION;version++) {
                var channel=FileChannel.open(directory.resolve("intent-v"+version+".log"),
                        StandardOpenOption.CREATE,StandardOpenOption.READ,StandardOpenOption.WRITE);
                lineageFiles.add(channel);
                var held=channel.tryLock();if(held==null)throw new IOException("ANOTHER_JOURNAL_WRITER");
                lineageLocks.add(held);
            }
        } catch(Exception failure) {
            for(int i=lineageLocks.size()-1;i>=0;i--)try { lineageLocks.get(i).release(); } catch(IOException suppressed) { failure.addSuppressed(suppressed); }
            for(var channel:lineageFiles)try { channel.close(); } catch(IOException suppressed) { failure.addSuppressed(suppressed); }
            throw new IOException("JOURNAL_LOCK_UNAVAILABLE",failure);
        }
        file=lineageFiles.get(VERSION-1);lock=lineageLocks.get(VERSION-1);
        UUID parsedRealm=null;
        try {
            try(var paths=Files.newDirectoryStream(directory,"intent-v*.log")) {
                for(Path path:paths) {
                    var match=java.util.regex.Pattern.compile("intent-v([0-9]+)\\.log").matcher(path.getFileName().toString());
                    if(match.matches()&&Long.parseLong(match.group(1))>VERSION&&Files.size(path)>0)
                        throw new IOException("FUTURE_JOURNAL_PRESENT_QUERY_ONLY");
                }
            }
            List<String> migrations=new ArrayList<>();
            for(int version=1;version<VERSION;version++) {
                var old=lineageFiles.get(version-1);List<String> records=new ArrayList<>();
                try { readAll(old,records); } finally { history.addAll(records); }
                if(!records.isEmpty()) {
                    UUID observed=headerRealm(records.get(0),version);
                    if(parsedRealm!=null&&!parsedRealm.equals(observed))throw new IOException("MIGRATION_REALM_MISMATCH");
                    parsedRealm=observed;
                    for(int i=0;i<version-1;i++)
                        if(records.size()<=i+1||!records.get(i+1).equals(migrations.get(i)))
                            throw new IOException("LEGACY_BYTES_CHANGED_AFTER_MIGRATION");
                }
                migrations.add("MIGRATED_V"+version+"\t"+channelHash(old)+"\t"+old.size());
            }
            List<String> fresh=new ArrayList<>();
            try { readAll(file,fresh); } finally { history.addAll(fresh); }
            if (fresh.isEmpty()) {
                parsedRealm = parsedRealm==null?UUID.randomUUID():parsedRealm;
                appendNow("HEADER\t" + VERSION + "\t" + parsedRealm);
                for(String migration:migrations)appendNow(migration);
            } else {
                UUID observed=headerRealm(fresh.get(0),VERSION);
                if(parsedRealm!=null&&!parsedRealm.equals(observed))throw new IOException("MIGRATION_REALM_MISMATCH");
                parsedRealm=observed;
                // A crash may leave a valid header or migration prefix. Resume only that exact prefix.
                for(int i=0;i<migrations.size();i++) {
                    if(fresh.size()>i+1) {
                        if(!fresh.get(i+1).equals(migrations.get(i)))throw new IOException("LEGACY_BYTES_CHANGED_AFTER_MIGRATION");
                    } else { file.position(file.size());appendNow(migrations.get(i)); }
                }
            }
            file.position(file.size());
        } catch (Exception ex) {
            // Keep the validated prefix queryable, preserve all bytes, and disable writes.
            failed=new IOException("JOURNAL_READ_ONLY_NO_REPLAY", ex);
        }
        realm=parsedRealm;
        writer = new ThreadPoolExecutor(1,1,0,TimeUnit.MILLISECONDS,new ArrayBlockingQueue<>(256),
                r -> { Thread t = new Thread(r,"ronova-pro-durable"); t.setDaemon(true); return t; },
                new ThreadPoolExecutor.AbortPolicy());
    }

    private static UUID headerRealm(String line,int version)throws IOException {
        String[] header=line.split("\t",-1);
        if(header.length!=3||!header[0].equals("HEADER")||!header[1].equals(Integer.toString(version)))
            throw new IOException("UNSUPPORTED_JOURNAL_VERSION_READ_ONLY");
        try { return UUID.fromString(header[2]); }
        catch(IllegalArgumentException invalid) { throw new IOException("INVALID_JOURNAL_REALM",invalid); }
    }
    private static String channelHash(FileChannel channel)throws IOException {
        try {
            var digest=MessageDigest.getInstance("SHA-256");channel.position(0);ByteBuffer buffer=ByteBuffer.allocate(16384);
            while(channel.read(buffer)>=0) { buffer.flip();digest.update(buffer);buffer.clear(); }
            return HexFormat.of().formatHex(digest.digest());
        } catch(java.security.NoSuchAlgorithmException impossible) { throw new AssertionError(impossible); }
    }

    private static byte[] digest(byte[] bytes) {
        try { return MessageDigest.getInstance("SHA-256").digest(bytes); }
        catch (Exception e) { throw new AssertionError(e); }
    }
    private static void readAll(FileChannel file,List<String> records) throws IOException {
        file.position(0);
        while (file.position() < file.size()) {
            ByteBuffer len = ByteBuffer.allocate(4);
            readFully(file,len);
            int n = len.flip().getInt();
            if (n < 1 || n > MAX_FRAME) throw new IOException("INVALID_FRAME");
            ByteBuffer data = ByteBuffer.allocate(n+32);
            readFully(file,data);
            byte[] frame = data.array(), value = Arrays.copyOf(frame,n);
            if (!MessageDigest.isEqual(digest(value),Arrays.copyOfRange(frame,n,n+32)))
                throw new IOException("CORRUPT_FRAME_NO_TRUNCATION");
            records.add(new String(value,StandardCharsets.UTF_8));
        }
    }
    private static void readFully(FileChannel file,ByteBuffer data) throws IOException {
        while(data.hasRemaining()) if(file.read(data)<0) throw new EOFException("TORN_FRAME_READ_ONLY");
    }
    private void appendNow(String record) throws IOException {
        byte[] value=record.getBytes(StandardCharsets.UTF_8);
        if(value.length<1 || value.length>MAX_FRAME) throw new IOException("FRAME_TOO_LARGE");
        ByteBuffer frame=ByteBuffer.allocate(4+value.length+32).putInt(value.length).put(value).put(digest(value));
        frame.flip();
        while(frame.hasRemaining()) file.write(frame);
        if(record.startsWith("STORAGE/2\tCOMMIT_ARMED\t"))ValidationFaults.hit("ACK_FORCE");
        file.force(true);
        history.add(record);
    }
    public CompletableFuture<Void> append(String record) {
        CompletableFuture<Void> ack=new CompletableFuture<>();
        if(failed!=null) { ack.completeExceptionally(failed); return ack; }
        try {
            writer.execute(() -> {
                try {
                    if(failed!=null) throw new IOException("WRITER_FAILED",failed);
                    appendNow(record);
                    ack.complete(null);
                } catch(Throwable ex) { failed=ex; ack.completeExceptionally(ex); }
            });
        } catch(RejectedExecutionException ex) { ack.completeExceptionally(ex); }
        return ack;
    }
    /** Only queue rejection proves no bytes were submitted. I/O failures are never retried here. */
    CompletableFuture<Void> retryRejected(String record,CompletableFuture<Void> ack) {
        if(record==null || ack==null || !healthy() || !ack.isCompletedExceptionally())return ack;
        try { ack.join(); }
        catch(CompletionException ex) {
            if(ex.getCause() instanceof RejectedExecutionException)return append(record);
        } catch(CancellationException cancelled) { /* cancellation is not a writer admission proof */ }
        return ack;
    }
    boolean backpressured(CompletableFuture<Void> ack) {
        if(ack==null || !healthy() || !ack.isCompletedExceptionally())return false;
        try { ack.join(); }
        catch(CompletionException ex) { return ex.getCause() instanceof RejectedExecutionException; }
        catch(CancellationException ignored) { }
        return false;
    }
    public List<String> history() { synchronized(history) { return List.copyOf(history); } }
    CompletableFuture<Void> registerSession(UUID session) {
        var process=ProcessHandle.current();
        long start=process.info().startInstant().map(java.time.Instant::toEpochMilli).orElse(-1L);
        return append("SESSION/1\t"+session+"\t"+process.pid()+"\t"+start+"\t"+realm);
    }
    /** An absent/reused OS process identity proves old JVM references cannot execute; a stop request does not. */
    boolean processEnded(UUID session) {
        for(String record:history())if(record.startsWith("SESSION/1\t"+session+"\t"))try {
            String[] fields=record.split("\t",-1);
            if(fields.length!=5||!Objects.equals(realm,UUID.fromString(fields[4])))return false;
            long pid=Long.parseLong(fields[2]),start=Long.parseLong(fields[3]);
            if(pid<=0||start<0)return false;
            var process=ProcessHandle.of(pid);if(process.isEmpty())return true;
            var current=process.get().info().startInstant();
            return current.isPresent()&&current.get().toEpochMilli()!=start;
        } catch(RuntimeException invalid) { return false; }
        return false;
    }
    public String readOnlyReason() {
        return failed==null?"":failed+ (failed.getCause()==null?"":":"+failed.getCause());
    }
    void refuseWrites(String reason) { failed=new IOException(reason); }
    public boolean healthy() { return failed==null && !writer.isShutdown(); }
    @Override public void close() throws IOException {
        writer.shutdown();
        try {
            if(!writer.awaitTermination(10,TimeUnit.SECONDS)) throw new IOException("DURABLE_WRITER_STILL_RUNNING");
        } catch(InterruptedException ex) { Thread.currentThread().interrupt(); throw new IOException(ex); }
        IOException failure=null;
        for(int i=lineageLocks.size()-1;i>=0;i--)try {
            if(lineageLocks.get(i).isValid())lineageLocks.get(i).release();
        } catch(IOException ex) { if(failure==null)failure=ex;else failure.addSuppressed(ex); }
        for(int i=lineageFiles.size()-1;i>=0;i--)try { lineageFiles.get(i).close(); }
        catch(IOException ex) { if(failure==null)failure=ex;else failure.addSuppressed(ex); }
        if(failure!=null)throw failure;
    }
}
