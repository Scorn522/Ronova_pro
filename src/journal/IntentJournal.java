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
import static dev.ronova.pro.persistence.JournalLineage.headerRealm;
import static dev.ronova.pro.persistence.JournalLineage.readAll;
import static dev.ronova.pro.persistence.JournalLineage.hash;
import static dev.ronova.pro.persistence.JournalLineage.writeRecord;

/** One bounded, ordered durable writer. ACK means force(true) completed, not queue acceptance. */
public final class IntentJournal implements AutoCloseable {
    private static final int VERSION = dev.ronova.pro.persistence.JournalLineage.VERSION;
    private final FileChannel file;
    private final FileLock lock;
    private final List<FileChannel> lineageFiles=new ArrayList<>();
    private final List<FileLock> lineageLocks=new ArrayList<>();
    private final ThreadPoolExecutor writer;
    private static final int MAX_DURABLE_BATCH=64;
    // Writer-thread confined. Each append still runs as its own observed executor task.
    private final AppendTask[] pendingWrites=new AppendTask[MAX_DURABLE_BATCH];
    private int pendingWriteCount;
    private volatile Throwable failed;
    private final List<String> history = Collections.synchronizedList(new ArrayList<>());
    private List<String> historyView;
    private final Set<String> committed=java.util.concurrent.ConcurrentHashMap.newKeySet();
    public final UUID realm;

    public IntentJournal(Path directory) throws IOException {
        Files.createDirectories(directory);
        // Retain every earlier file verbatim and hold its writer lock for this session.
        // V7 adds actual Mod carrier/handoff semantics; V6 logical frames and all old commands remain verbatim.
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
                try { readAll(old,records); } finally { history.addAll(records);committed.addAll(records); }
                if(!records.isEmpty()) {
                    UUID observed=headerRealm(records.get(0),version);
                    if(parsedRealm!=null&&!parsedRealm.equals(observed))throw new IOException("MIGRATION_REALM_MISMATCH");
                    parsedRealm=observed;
                    for(int i=0;i<version-1;i++) {
                        // A header/migration-only prefix has no replayable obligation.
                        if(records.size()<=i+1)break;
                        if(!records.get(i+1).equals(migrations.get(i)))
                            throw new IOException("LEGACY_BYTES_CHANGED_AFTER_MIGRATION");
                    }
                }
                migrations.add("MIGRATED_V"+version+"\t"+hash(old)+"\t"+old.size());
            }
            List<String> fresh=new ArrayList<>();
            try { readAll(file,fresh); } finally { history.addAll(fresh);committed.addAll(fresh); }
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
                new ThreadPoolExecutor.AbortPolicy()) {
            @Override protected void afterExecute(Runnable task,Throwable failure) {
                super.afterExecute(task,failure);
                if(failure!=null)failPending(failure);
                // Never wait to form a batch, and flush before an unrelated executor task.
                if(pendingWriteCount!=0&&(pendingWriteCount>=MAX_DURABLE_BATCH
                        ||!(getQueue().peek() instanceof AppendTask)))flushPending();
            }
        };
    }

    /** V6 splits nonempty logical records into the original bounded physical frames. */
    static boolean frameFits(String record) {
        return !record.isEmpty();
    }
    private void appendNow(String record) throws IOException {
        writeRecord(file,record);
        file.force(true);
        synchronized(history){history.add(record);historyView=null;committed.add(record);}
    }
    private final class AppendTask implements Runnable {
        final String record;
        final CompletableFuture<Void> ack;
        AppendTask(String record,CompletableFuture<Void> ack){this.record=record;this.ack=ack;}
        @Override public void run() {
            if(failed!=null){ack.completeExceptionally(failed);return;}
            // Include the current record before I/O: a partial frame is never admission rejection.
            try { pendingWrites[pendingWriteCount++]=this;writeRecord(file,record); }
            catch(Throwable failure){failPending(failure);ack.completeExceptionally(failure);}
        }
    }
    private void failPending(Throwable failure) {
        failed=failure;
        completePending(failure);
    }
    private void completePending(Throwable failure) {
        int count=pendingWriteCount;pendingWriteCount=0;
        for(int index=0;index<count;index++){
            AppendTask task=pendingWrites[index];pendingWrites[index]=null;
            if(failure==null)task.ack.complete(null);else task.ack.completeExceptionally(failure);
        }
    }
    private void flushPending() {
        try {
            if(failed!=null)throw new IOException("WRITER_FAILED",failed);
            file.force(true);
            synchronized(history){
                for(int index=0;index<pendingWriteCount;index++){
                    AppendTask task=pendingWrites[index];history.add(task.record);committed.add(task.record);
                }
                historyView=null;
            }
        } catch(Throwable failure){failPending(failure);return;}
        // Completion can invoke arbitrary continuations; no history/queue lock is held here.
        completePending(null);
    }
    public CompletableFuture<Void> append(String record) {
        CompletableFuture<Void> ack=new CompletableFuture<>();
        if(failed!=null) { ack.completeExceptionally(failed); return ack; }
        try {
            writer.execute(new AppendTask(record,ack));
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
    public List<String> history() { synchronized(history) { if(historyView==null)historyView=List.copyOf(history);return historyView; } }
    /** Read-only replay validation on the writer's ordered turn; never re-executes an effect. */
    public CompletableFuture<String> verifyReplay(){
        CompletableFuture<String> result=new CompletableFuture<>();
        try{writer.execute(()->{
            if(failed!=null){result.completeExceptionally(failed);return;}
            long[] positions=new long[lineageFiles.size()];List<String> records=new ArrayList<>(),migrations=new ArrayList<>();Throwable failure=null;
            try{
                if(pendingWriteCount!=0)flushPending();if(failed!=null)throw new IOException("WRITER_FAILED",failed);
                for(int i=0;i<positions.length;i++)positions[i]=lineageFiles.get(i).position();
                for(int version=1;version<=VERSION;version++){
                    FileChannel channel=lineageFiles.get(version-1);List<String> part=new ArrayList<>();readAll(channel,part);
                    if(!part.isEmpty()){
                        if(!Objects.equals(realm,headerRealm(part.get(0),version)))throw new IOException("MIGRATION_REALM_MISMATCH");
                        for(int i=0;i<version-1&&part.size()>i+1;i++)if(!part.get(i+1).equals(migrations.get(i)))throw new IOException("LEGACY_BYTES_CHANGED_AFTER_MIGRATION");
                        records.addAll(part);
                    }
                    if(version<VERSION)migrations.add("MIGRATED_V"+version+"\t"+hash(channel)+"\t"+channel.size());
                }
                if(!records.equals(history()))throw new IOException("REPLAY_COMMITTED_HISTORY_MISMATCH");
            }catch(Throwable unavailable){failure=unavailable;failed=unavailable;}
            finally{for(int i=0;i<positions.length;i++)try{lineageFiles.get(i).position(positions[i]);}catch(IOException unavailable){if(failure==null)failure=unavailable;else failure.addSuppressed(unavailable);failed=unavailable;}}
            if(failure!=null)result.completeExceptionally(failure);
            else result.complete("只读重放核对完成："+records.size()+" 条已确认记录；帧摘要、版本迁移、世界身份和内存历史一致。未重执行清除或恢复动作。");
        });}catch(RejectedExecutionException unavailable){result.completeExceptionally(unavailable);}
        return result;
    }
    boolean recorded(String record){return committed.contains(record);}
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
