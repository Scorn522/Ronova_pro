package dev.ronova.pro.persistence;

import java.io.EOFException;
import java.io.IOException;
import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.charset.StandardCharsets;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.file.*;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;

/** The same durable bytes are read by the early launcher and the in-world writer. */
public final class JournalLineage {
    public static final int VERSION=6,MAX_FRAME=65536;
    private static final int SINGLE=0,FIRST=1,NEXT=2,FIRST_HEADER=37,NEXT_HEADER=5;
    public record History(UUID realm,List<String> records) {public History{records=List.copyOf(records);}}
    private JournalLineage() {}
    /** Keep legacy UTF-8 fields, but retain every Java code unit in malformed-surrogate text. */
    public static String encodeText(String value) {
        boolean raw=false;
        for(int i=0;i<value.length();i++) {
            char unit=value.charAt(i);
            if(Character.isHighSurrogate(unit)) {
                if(i+1<value.length()&&Character.isLowSurrogate(value.charAt(i+1)))i++;
                else {raw=true;break;}
            } else if(Character.isLowSurrogate(unit)) {raw=true;break;}
        }
        if(!raw)return Base64.getUrlEncoder().withoutPadding().encodeToString(value.getBytes(StandardCharsets.UTF_8));
        byte[] units=new byte[Math.multiplyExact(value.length(),Character.BYTES)];
        for(int i=0;i<value.length();i++) {
            char unit=value.charAt(i);units[i*2]=(byte)(unit>>>8);units[i*2+1]=(byte)unit;
        }
        // ':' cannot occur in a legacy Base64url field.
        return "u16:"+Base64.getUrlEncoder().withoutPadding().encodeToString(units);
    }
    public static String decodeText(String encoded) {
        if(!encoded.startsWith("u16:"))return new String(Base64.getUrlDecoder().decode(encoded),StandardCharsets.UTF_8);
        byte[] units=Base64.getUrlDecoder().decode(encoded.substring(4));
        if((units.length&1)!=0)throw new IllegalArgumentException("INVALID_JOURNAL_TEXT_UNITS");
        char[] value=new char[units.length/2];
        for(int i=0;i<value.length;i++)value[i]=(char)((units[i*2]&0xff)<<8|(units[i*2+1]&0xff));
        String text=new String(value);
        if(!encodeText(text).equals(encoded))throw new IllegalArgumentException("INVALID_JOURNAL_TEXT_ENCODING");
        return text;
    }
    public static UUID headerRealm(String line,int version)throws IOException {
        String[] fields=line.split("\t",-1);
        if(fields.length!=3||!fields[0].equals("HEADER")||!fields[1].equals(Integer.toString(version)))throw new IOException("UNSUPPORTED_JOURNAL_VERSION_READ_ONLY");
        try{return UUID.fromString(fields[2]);}catch(IllegalArgumentException invalid){throw new IOException("INVALID_JOURNAL_REALM",invalid);}
    }
    public static String hash(FileChannel file)throws IOException {
        MessageDigest digest=digest();file.position(0);ByteBuffer buffer=ByteBuffer.allocate(16384);
        while(file.read(buffer)>=0){buffer.flip();digest.update(buffer);buffer.clear();}
        return HexFormat.of().formatHex(digest.digest());
    }
    private static MessageDigest digest(){try{return MessageDigest.getInstance("SHA-256");}catch(NoSuchAlgorithmException impossible){throw new AssertionError(impossible);}}
    /** V6 bounds each physical frame, while one ordered durable write owns the complete logical record. */
    public static void writeRecord(FileChannel file,String record)throws IOException {
        byte[] value=record.getBytes(StandardCharsets.UTF_8);
        if(value.length==0)throw new IOException("EMPTY_RECORD");
        if(file.size()==0) {
            headerRealm(record,VERSION);writeFrame(file,value);return;
        }
        if(value.length<=MAX_FRAME-1) {
            byte[] frame=new byte[value.length+1];frame[0]=SINGLE;System.arraycopy(value,0,frame,1,value.length);writeFrame(file,frame);return;
        }
        byte[] expected=digest().digest(value);int offset=0;
        while(offset<value.length) {
            int header=offset==0?FIRST_HEADER:NEXT_HEADER,count=Math.min(MAX_FRAME-header,value.length-offset);
            ByteBuffer frame=ByteBuffer.allocate(header+count);
            if(offset==0)frame.put((byte)FIRST).putInt(value.length).put(expected);
            else frame.put((byte)NEXT).putInt(offset);
            frame.put(value,offset,count);writeFrame(file,frame.array());offset+=count;
        }
    }
    private static void writeFrame(FileChannel file,byte[] value)throws IOException {
        if(value.length<1||value.length>MAX_FRAME)throw new IOException("INVALID_FRAME");
        ByteBuffer frame=ByteBuffer.allocate(4+value.length+32).putInt(value.length).put(value).put(digest().digest(value));
        frame.flip();while(frame.hasRemaining())file.write(frame);
    }
    private static String logicalText(byte[] value,int offset,int length)throws IOException {
        try{return StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(value,offset,length)).toString();}
        catch(CharacterCodingException invalid){throw new IOException("INVALID_LOGICAL_RECORD_TEXT",invalid);}
    }
    public static void readAll(FileChannel file,List<String> records)throws IOException {
        file.position(0);boolean first=true,segmented=false;
        ByteArrayOutputStream pending=null;byte[] expected=null;int total=0;
        while(file.position()<file.size()){
            ByteBuffer length=ByteBuffer.allocate(4);readFully(file,length);int n=length.flip().getInt();
            if(n<1||n>MAX_FRAME)throw new IOException("INVALID_FRAME");
            ByteBuffer data=ByteBuffer.allocate(n+32);readFully(file,data);byte[] bytes=data.array(),value=Arrays.copyOf(bytes,n);
            if(!MessageDigest.isEqual(digest().digest(value),Arrays.copyOfRange(bytes,n,n+32)))throw new IOException("CORRUPT_FRAME_NO_TRUNCATION");
            if(first) {
                String header=new String(value,StandardCharsets.UTF_8);records.add(header);
                String[] fields=header.split("\t",-1);
                segmented=fields.length==3&&fields[0].equals("HEADER")&&fields[1].equals(Integer.toString(VERSION));
                first=false;continue;
            }
            if(!segmented) {records.add(new String(value,StandardCharsets.UTF_8));continue;}
            int kind=value[0]&0xff,offset,count;
            if(kind==SINGLE) {
                if(pending!=null||value.length<=1)throw new IOException("INVALID_LOGICAL_RECORD_ORDER");
                records.add(logicalText(value,1,value.length-1));continue;
            } else if(kind==FIRST) {
                if(pending!=null||value.length<=FIRST_HEADER)throw new IOException("INVALID_LOGICAL_RECORD_START");
                total=ByteBuffer.wrap(value,1,Integer.BYTES).getInt();
                if(total<=MAX_FRAME-1)throw new IOException("INVALID_LOGICAL_RECORD_LENGTH");
                expected=Arrays.copyOfRange(value,5,FIRST_HEADER);
                pending=new ByteArrayOutputStream(Math.min(total,MAX_FRAME));offset=FIRST_HEADER;
            } else if(kind==NEXT) {
                if(pending==null||value.length<=NEXT_HEADER||ByteBuffer.wrap(value,1,Integer.BYTES).getInt()!=pending.size())
                    throw new IOException("INVALID_LOGICAL_RECORD_CONTINUATION");
                offset=NEXT_HEADER;
            } else throw new IOException("UNSUPPORTED_LOGICAL_RECORD_FRAME");
            count=value.length-offset;
            if(count>total-pending.size())throw new IOException("LOGICAL_RECORD_LENGTH_MISMATCH");
            pending.write(value,offset,count);
            if(pending.size()==total) {
                byte[] complete=pending.toByteArray();
                if(!MessageDigest.isEqual(digest().digest(complete),expected))throw new IOException("CORRUPT_LOGICAL_RECORD_NO_TRUNCATION");
                records.add(logicalText(complete,0,complete.length));pending=null;expected=null;total=0;
            }
        }
        if(pending!=null)throw new EOFException("TORN_LOGICAL_RECORD_READ_ONLY");
    }
    private static void readFully(FileChannel file,ByteBuffer data)throws IOException {while(data.hasRemaining())if(file.read(data)<0)throw new EOFException("TORN_FRAME_READ_ONLY");}
    public static History read(Path directory)throws IOException {
        if(!Files.isDirectory(directory))return new History(null,List.of());
        List<FileChannel> files=new ArrayList<>();List<FileLock> locks=new ArrayList<>();
        try{
            try(var paths=Files.newDirectoryStream(directory,"intent-v*.log")){
                for(Path path:paths){var match=java.util.regex.Pattern.compile("intent-v([0-9]+)\\.log").matcher(path.getFileName().toString());
                    if(match.matches()&&Long.parseLong(match.group(1))>VERSION&&Files.size(path)>0)throw new IOException("FUTURE_JOURNAL_PRESENT_QUERY_ONLY");}
            }
            for(int version=1;version<=VERSION;version++){
                Path path=directory.resolve("intent-v"+version+".log");
                FileChannel file=Files.isRegularFile(path)?FileChannel.open(path,StandardOpenOption.READ):null;files.add(file);
                if(file!=null){FileLock lock=file.tryLock(0,Long.MAX_VALUE,true);if(lock==null)throw new IOException("ANOTHER_JOURNAL_WRITER");locks.add(lock);}
            }
            UUID realm=null;List<String> history=new ArrayList<>(),migrations=new ArrayList<>();
            for(int version=1;version<=VERSION;version++){
                FileChannel file=files.get(version-1);List<String> records=new ArrayList<>();if(file!=null)readAll(file,records);
                if(!records.isEmpty()){
                    UUID actual=headerRealm(records.get(0),version);if(realm!=null&&!realm.equals(actual))throw new IOException("MIGRATION_REALM_MISMATCH");realm=actual;
                    for(int i=0;i<version-1;i++){
                        // A header/migration-only torn prefix contains no replayable work.
                        if(records.size()<=i+1)break;
                        if(!records.get(i+1).equals(migrations.get(i)))throw new IOException("LEGACY_BYTES_CHANGED_AFTER_MIGRATION");
                    }
                    history.addAll(records);
                }
                if(version<VERSION)migrations.add("MIGRATED_V"+version+"\t"+(file==null?HexFormat.of().formatHex(digest().digest(new byte[0])):hash(file))+"\t"+(file==null?0:file.size()));
            }
            return new History(realm,history);
        }catch(java.nio.channels.OverlappingFileLockException busy){throw new IOException("ANOTHER_JOURNAL_WRITER",busy);}
        finally{
            IOException failure=null;
            for(int i=locks.size()-1;i>=0;i--)try{locks.get(i).release();}catch(IOException ex){failure=ex;}
            for(FileChannel file:files)if(file!=null)try{file.close();}catch(IOException ex){failure=ex;}
            if(failure!=null)throw failure;
        }
    }
}
