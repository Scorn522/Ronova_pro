package dev.ronova.pro;

import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;

/** Real filesystem / force / reopen tests. No Minecraft or model ACK implementation. */
public final class JournalCheck {
    public static void main(String[] args) throws Exception {
        Path base=Path.of(args[0]);Files.createDirectories(base);
        UUID realm;
        try(IntentJournal j=new IntentJournal(base.resolve("normal"))) {
            realm=j.realm;
            try { new IntentJournal(base.resolve("normal"));throw new AssertionError("second writer acquired authority"); }
            catch(java.io.IOException expected) { }
            List<CompletableFuture<Void>> acks=new ArrayList<>();
            for(int i=0;i<100;i++)acks.add(j.append("INTENT\t"+i));
            CompletableFuture.allOf(acks.toArray(CompletableFuture[]::new)).get(10,TimeUnit.SECONDS);
            require(j.history().size()==105,"real acknowledgements plus three migration digests and header");
        }
        try(IntentJournal j=new IntentJournal(base.resolve("normal"))) {
            require(j.realm.equals(realm),"realm survives reopen");
            require(j.history().size()==105,"frames survive reopen");
            for(int i=0;i<100;i++)require(j.history().get(i+5).equals("INTENT\t"+i),"ordered frame "+i);
        }
        try(IntentJournal j=new IntentJournal(base.resolve("delay"))) {
            var f=IntentJournal.class.getDeclaredField("writer");f.setAccessible(true);
            CountDownLatch release=new CountDownLatch(1),started=new CountDownLatch(1);
            ((Executor)f.get(j)).execute(()->{started.countDown();try{release.await();}catch(InterruptedException x){Thread.currentThread().interrupt();}});
            started.await();
            var ack=j.append("INTENT\tdelayed");
            require(!ack.isDone() && j.history().size()==5,"queue acceptance is not ACK");
            release.countDown();ack.get(10,TimeUnit.SECONDS);
        }
        Path torn=base.resolve("torn");
        Files.createDirectories(torn);Files.write(torn.resolve("intent-v1.log"),new byte[]{0,0,0,99,1});
        byte[] prior=Files.readAllBytes(torn.resolve("intent-v1.log"));
        try(IntentJournal j=new IntentJournal(torn)) {
            require(!j.healthy() && j.realm==null,"torn tail stays read-only");
            require(j.append("INTENT\tforbidden").isCompletedExceptionally(),"no writes over unknown tail");
        }
        require(Arrays.equals(prior,Files.readAllBytes(torn.resolve("intent-v1.log"))),"unknown tail preserved");
        Path migration=base.resolve("migration");Files.createDirectories(migration);
        UUID legacyRealm=UUID.randomUUID();
        List<String> legacy=List.of("HEADER\t1\t"+legacyRealm,"INTENT\told-command-verbatim",
                "HASH\told-hash-verbatim","ONCE\told-slot-verbatim","UNKNOWN_EFFECT\topaque-verbatim");
        Path legacyPath=migration.resolve("intent-v1.log");
        for(String line:legacy)frame(legacyPath,line);
        byte[] legacyBytes=Files.readAllBytes(legacyPath);
        try(IntentJournal j=new IntentJournal(migration)) {
            require(j.healthy()&&j.realm.equals(legacyRealm),"legacy realm retained");
            require(j.history().subList(0,legacy.size()).equals(legacy),"legacy meanings retained without re-encoding");
            j.append("STORAGE/1\tMIGRATION_CHECK_ONLY").get(10,TimeUnit.SECONDS);
        }
        require(Arrays.equals(legacyBytes,Files.readAllBytes(legacyPath)),"legacy bytes and hashes untouched");
        try(IntentJournal j=new IntentJournal(migration)) { require(j.healthy()&&j.realm.equals(legacyRealm),"V2 migrated reopen"); }
        byte[] v2=Files.readAllBytes(migration.resolve("intent-v2.log"));
        frame(legacyPath,"LATE_OLD_WRITER\tchanged");
        try(IntentJournal j=new IntentJournal(migration)) { require(!j.healthy(),"old writer change fences V2"); }
        require(Arrays.equals(v2,Files.readAllBytes(migration.resolve("intent-v2.log"))),"no rewrite on migration mismatch");
        Path future=base.resolve("future");Files.createDirectories(future);
        frame(future.resolve("intent-v2.log"),"HEADER\t3\t"+UUID.randomUUID());
        byte[] futureBytes=Files.readAllBytes(future.resolve("intent-v2.log"));
        try(IntentJournal j=new IntentJournal(future)) {
            require(!j.healthy(),"future version read-only");
            require(j.append("FORBIDDEN").isCompletedExceptionally(),"future version refuses write");
        }
        require(Arrays.equals(futureBytes,Files.readAllBytes(future.resolve("intent-v2.log"))),"future bytes preserved");
        Path v2Migration=base.resolve("v2-migration");Files.createDirectories(v2Migration);
        UUID v2Realm=UUID.randomUUID();Path v1Path=v2Migration.resolve("intent-v1.log"),v2Path=v2Migration.resolve("intent-v2.log");
        frame(v1Path,"HEADER\t1\t"+v2Realm);frame(v1Path,"ONCE\texisting-slot-and-command");
        byte[] v1Original=Files.readAllBytes(v1Path);
        frame(v2Path,"HEADER\t2\t"+v2Realm);
        frame(v2Path,"MIGRATED_V1\t"+hash(v1Original)+"\t"+v1Original.length);
        frame(v2Path,"HASH\tunchanged-v2-hash");frame(v2Path,"UNKNOWN_EFFECT\tkeep-query-only");
        byte[] v2Original=Files.readAllBytes(v2Path);
        try(IntentJournal j=new IntentJournal(v2Migration)) {
            require(j.healthy()&&v2Realm.equals(j.realm),"actual V2 -> V3 migration");
            require(j.history().contains("ONCE\texisting-slot-and-command")&&j.history().contains("UNKNOWN_EFFECT\tkeep-query-only"),"V1 V2 meanings preserved");
            UUID session=UUID.randomUUID();j.registerSession(session).get(10,TimeUnit.SECONDS);
            require(!j.processEnded(session)&&!j.processEnded(UUID.randomUUID()),"live and unknown sessions never prove process exit");
        }
        require(Arrays.equals(v1Original,Files.readAllBytes(v1Path))&&Arrays.equals(v2Original,Files.readAllBytes(v2Path)),"both legacy files byte-identical");
        try(IntentJournal j=new IntentJournal(v2Migration)) { require(j.healthy(),"three-version reopen"); }
        Path futureFile=base.resolve("future-file");Files.createDirectories(futureFile);
        frame(futureFile.resolve("intent-v6.log"),"HEADER\t6\t"+UUID.randomUUID());
        try(IntentJournal j=new IntentJournal(futureFile)) { require(!j.healthy(),"future filename rejects writes"); }
        require(Files.size(futureFile.resolve("intent-v5.log"))==0,"future file never creates a new writable V5 history");
        Path prefix=base.resolve("migration-prefix");Files.createDirectories(prefix);
        UUID prefixRealm=UUID.randomUUID();frame(prefix.resolve("intent-v5.log"),"HEADER\t5\t"+prefixRealm);
        try(IntentJournal j=new IntentJournal(prefix)) { require(j.healthy()&&j.history().size()==5,"crashed valid migration prefix resumes"); }
        Path v3Migration=base.resolve("v3-migration");Files.createDirectories(v3Migration);
        UUID v3Realm=UUID.randomUUID();Path oldV3=v3Migration.resolve("intent-v3.log");
        frame(oldV3,"HEADER\t3\t"+v3Realm);
        frame(oldV3,"MIGRATED_V1\t"+hash(new byte[0])+"\t0");frame(oldV3,"MIGRATED_V2\t"+hash(new byte[0])+"\t0");
        frame(oldV3,"ONCE\told-v3-slot-verbatim");frame(oldV3,"RECORD/2\tEDGE_RELEASED\told-meaning-verbatim");
        byte[] oldV3Bytes=Files.readAllBytes(oldV3);
        try(IntentJournal j=new IntentJournal(v3Migration)) {
            require(j.healthy()&&v3Realm.equals(j.realm),"V3 to V4 migration");
            require(j.history().contains("ONCE\told-v3-slot-verbatim")&&j.history().contains("RECORD/2\tEDGE_RELEASED\told-meaning-verbatim"),"old V3 command and edge meanings unchanged");
        }
        require(Arrays.equals(oldV3Bytes,Files.readAllBytes(oldV3)),"V3 bytes and hashes untouched");
        Path v4Migration=base.resolve("v4-migration");Files.createDirectories(v4Migration);
        UUID v4Realm=UUID.randomUUID();Path oldV4=v4Migration.resolve("intent-v4.log");
        frame(oldV4,"HEADER\t4\t"+v4Realm);
        for(int version=1;version<4;version++)frame(oldV4,"MIGRATED_V"+version+"\t"+hash(new byte[0])+"\t0");
        frame(oldV4,"ONCE\tv4-slot-verbatim");frame(oldV4,"RECORD/3\tEDGE_RELEASED\tv4-meaning-verbatim");
        byte[] oldV4Bytes=Files.readAllBytes(oldV4);
        try(IntentJournal j=new IntentJournal(v4Migration)) {
            require(j.healthy()&&v4Realm.equals(j.realm),"actual V4 -> V5 migration");
            require(j.history().contains("ONCE\tv4-slot-verbatim")&&j.history().contains("RECORD/3\tEDGE_RELEASED\tv4-meaning-verbatim"),"V4 descriptor and ONCE text preserved");
        }
        require(Arrays.equals(oldV4Bytes,Files.readAllBytes(oldV4)),"V4 bytes and hashes untouched");
        try(IntentJournal j=new IntentJournal(base.resolve("budget"))) {
            var f=IntentJournal.class.getDeclaredField("writer");f.setAccessible(true);
            CountDownLatch entered=new CountDownLatch(1),release=new CountDownLatch(1);
            ((Executor)f.get(j)).execute(()->{entered.countDown();try { release.await(); }catch(InterruptedException ex) { Thread.currentThread().interrupt(); }});
            require(entered.await(5,TimeUnit.SECONDS),"writer timing blocker entered");
            List<CompletableFuture<Void>> pending=new ArrayList<>();
            for(int i=0;i<256;i++)pending.add(j.append("BUDGET\t"+i));
            var rejected=j.append("BUDGET\tretry");require(rejected.isCompletedExceptionally()&&j.healthy(),"queue budget is backpressure, not corrupt truth");
            release.countDown();CompletableFuture.allOf(pending.toArray(CompletableFuture[]::new)).get(10,TimeUnit.SECONDS);
            j.retryRejected("BUDGET\tretry",rejected).get(10,TimeUnit.SECONDS);
            require(j.history().stream().filter("BUDGET\tretry"::equals).count()==1,"rejected admission resumes once");
        }
        System.out.println("JOURNAL_REAL_IO_CHECKS_PASS");
    }
    static String hash(byte[] bytes)throws Exception { return HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(bytes)); }
    static void frame(Path path,String record)throws Exception {
        byte[] text=record.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        byte[] hash=java.security.MessageDigest.getInstance("SHA-256").digest(text);
        var bytes=java.nio.ByteBuffer.allocate(4+text.length+hash.length).putInt(text.length).put(text).put(hash);
        Files.write(path,bytes.array(),StandardOpenOption.CREATE,StandardOpenOption.APPEND);
    }
    static void require(boolean c,String m){if(!c)throw new AssertionError(m);}
}
