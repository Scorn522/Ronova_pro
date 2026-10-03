package dev.ronova.pro;

import java.io.*;
import java.security.MessageDigest;
import java.util.*;
import net.minecraft.nbt.*;

/** Exact NBT record projection. Paths are typed; map keys are never interpreted as list indexes. */
final class RecoveryImage {
    // A transfer block, never a limit on the total image or a selector count.
    private static final int BLOCK=65536;
    private static final Class<?>[] STANDARD={EndTag.class,ByteTag.class,ShortTag.class,IntTag.class,LongTag.class,FloatTag.class,
            DoubleTag.class,ByteArrayTag.class,StringTag.class,ListTag.class,CompoundTag.class,IntArrayTag.class,LongArrayTag.class};
    record Step(String key,int index) {
        Step { if((key==null)==(index<0))throw new IllegalArgumentException("INVALID_PATH_STEP"); }
        static Step key(String key) { return new Step(Objects.requireNonNull(key),-1); }
        static Step index(int index) { return new Step(null,index); }
    }
    record Selection(UUID subject,List<Step> path) {
        Selection { path=List.copyOf(path); }
    }
    record Projection(Encoded encoded,String baselineHash,String expectedHash,String preservedHash) implements AutoCloseable {
        @Override public void close() { encoded.close(); }
    }
    /** Immutable actual bytes. Each owner has its own lease; retirement cannot erase a live reader. */
    static final class Encoded implements AutoCloseable {
        private static final class Data {
            final NavigableMap<Long,byte[]> blocks;
            final long length;
            final String hash;
            long owners=1;
            Data(NavigableMap<Long,byte[]> blocks,long length,String hash) { this.blocks=blocks;this.length=length;this.hash=hash; }
        }
        private final Data data;
        private boolean closed;
        private Encoded(Data data) { this.data=data; }
        synchronized Encoded retain()throws IOException {
            open();synchronized(data) {
                if(data.owners==Long.MAX_VALUE)throw new IOException("IMAGE_OWNER_OVERFLOW");
                data.owners++;return new Encoded(data);
            }
        }
        private void open()throws IOException { if(closed)throw new IOException("IMAGE_RETIRED"); }
        synchronized long size()throws IOException { open();return data.length; }
        synchronized String hash()throws IOException { open();return data.hash; }
        synchronized int read(long position,byte[] target,int offset,int length)throws IOException {
            open();Objects.checkFromIndexSize(offset,length,target.length);
            if(position<0||position>data.length)throw new IOException("IMAGE_POSITION_INVALID");
            if(length==0)return 0;if(position==data.length)return -1;
            int copied=0,remaining=(int)Math.min((long)length,data.length-position);
            while(copied<remaining) {
                var block=data.blocks.floorEntry(position);if(block==null)throw new IOException("IMAGE_BLOCK_MISSING");
                int at=(int)(position-block.getKey()),count=Math.min(remaining-copied,block.getValue().length-at);
                if(count<=0)throw new IOException("IMAGE_BLOCK_GAP");
                System.arraycopy(block.getValue(),at,target,offset+copied,count);copied+=count;position+=count;
            }
            return copied;
        }
        InputStream input()throws IOException {
            Encoded lease=retain();
            return new InputStream() {
                long position;boolean closed;final byte[] single=new byte[1];
                @Override public int read()throws IOException { int count=read(single,0,1);return count<0?-1:single[0]&255; }
                @Override public int read(byte[] bytes,int offset,int length)throws IOException {
                    if(closed)throw new IOException("IMAGE_STREAM_CLOSED");
                    int count=lease.read(position,bytes,offset,length);if(count>0)position+=count;return count;
                }
                @Override public int available()throws IOException { if(closed)throw new IOException("IMAGE_STREAM_CLOSED");return (int)Math.min(Integer.MAX_VALUE,lease.size()-position); }
                @Override public void close() { if(!closed) { closed=true;lease.close(); } }
            };
        }
        void writeTo(OutputStream output)throws IOException {
            try(InputStream input=input()) { transfer(input,output); }
        }
        @Override public synchronized void close() {
            if(closed)return;closed=true;
            synchronized(data) { if(--data.owners==0) { for(byte[] block:data.blocks.values())Arrays.fill(block,(byte)0);data.blocks.clear(); } }
        }
    }
    /** Allocation follows bytes actually written; no array is sized from a declared total. */
    static final class Builder extends OutputStream {
        private NavigableMap<Long,byte[]> blocks=new TreeMap<>();
        private byte[] current;
        private int used;
        private long length;
        private final MessageDigest digest=digest();
        private boolean closed;
        @Override public void write(int value)throws IOException {
            ensure(1);if(current==null||used==current.length)block();current[used++]=(byte)value;length++;digest.update((byte)value);
        }
        @Override public void write(byte[] bytes,int offset,int count)throws IOException {
            Objects.checkFromIndexSize(offset,count,bytes.length);ensure(count);
            digest.update(bytes,offset,count);
            while(count>0) {
                if(current==null||used==current.length)block();int part=Math.min(count,current.length-used);
                System.arraycopy(bytes,offset,current,used,part);used+=part;length+=part;offset+=part;count-=part;
            }
        }
        private void ensure(int count)throws IOException {
            if(closed)throw new IOException("IMAGE_BUILDER_CLOSED");
            if(count<0||length>Long.MAX_VALUE-count)throw new IOException("IMAGE_LENGTH_OVERFLOW");
        }
        private void block() { current=new byte[BLOCK];used=0;blocks.put(length,current); }
        Encoded finish()throws IOException {
            ensure(0);closed=true;
            Encoded value=new Encoded(new Encoded.Data(blocks,length,HexFormat.of().formatHex(digest.digest())));
            blocks=null;current=null;return value;
        }
        @Override public void close() {
            if(closed)return;closed=true;for(byte[] block:blocks.values())Arrays.fill(block,(byte)0);blocks.clear();current=null;
        }
    }
    static Encoded capture(InputStream input)throws IOException {
        try(Builder builder=new Builder()) { transfer(input,builder);return builder.finish(); }
    }
    private static void transfer(InputStream input,OutputStream output)throws IOException {
        byte[] block=new byte[BLOCK];
        try {
            for(;;) { int count=input.read(block);if(count<0)return;if(count==0) { int value=input.read();if(value<0)return;output.write(value); }else output.write(block,0,count); }
        } finally { Arrays.fill(block,(byte)0); }
    }
    private static MessageDigest digest() {
        try { return MessageDigest.getInstance("SHA-256"); }
        catch(java.security.NoSuchAlgorithmException impossible) { throw new AssertionError(impossible); }
    }
    private RecoveryImage() { }
    static String hash(byte[] bytes) {
        return HexFormat.of().formatHex(digest().digest(bytes));
    }
    static String hash(InputStream input)throws IOException {
        MessageDigest digest=digest();
        transfer(input,new OutputStream() {
            @Override public void write(int value) { digest.update((byte)value); }
            @Override public void write(byte[] bytes,int offset,int count) { digest.update(bytes,offset,count); }
        });
        return HexFormat.of().formatHex(digest.digest());
    }
    static CompoundTag decode(Encoded encoded)throws IOException {
        if(encoded.size()<1)throw new IOException("EMPTY_ENCODED_IMAGE");
        try(InputStream stored=encoded.input();InputStream stream=compressed(encoded)?new java.util.zip.GZIPInputStream(stored):stored) {
            var input=new DataInputStream(stream);
            CompoundTag tag=new Reader(input,NbtAccounter.UNLIMITED).read();
            if(input.read()!=-1)throw new IOException("AMBIGUOUS_NBT_IMAGE");
            validate(tag);
            return tag;
        }
    }
    static CompoundTag read(DataInput input,NbtAccounter accounting)throws IOException {
        return new Reader(input,accounting).read();
    }
    static CompoundTag readCompressed(InputStream input)throws IOException {
        try(var stream=new DataInputStream(new net.minecraft.util.FastBufferedInputStream(new java.util.zip.GZIPInputStream(input)))) {
            return read(stream,NbtAccounter.UNLIMITED);
        }
    }
    /** NbtIo's original named-payload error boundary, retained without recursive loads. */
    private record ReadBoundary(String name,int type) {
        RuntimeException failure(IOException cause) {
            var report=net.minecraft.CrashReport.forThrowable(cause,"Loading NBT data");
            var category=report.addCategory("NBT Tag");
            if(name!=null) {
                category.setDetail("Tag name",name);
                category.setDetail("Tag type",TagTypes.getType(type).getName());
            } else category.setDetail("Tag type",type);
            return new net.minecraft.ReportedException(report);
        }
    }
    private static final class ReadFrame {
        final Tag tag;
        final int elementType;
        final ReadBoundary boundary;
        int remaining;
        ReadFrame(Tag tag,int elementType,int remaining,ReadBoundary boundary) {
            this.tag=tag;this.elementType=elementType;this.remaining=remaining;this.boundary=boundary;
        }
    }
    private static final class Reader {
        final DataInput input;
        final NbtAccounter accounting;
        final ArrayDeque<ReadFrame> frames=new ArrayDeque<>();
        Reader(DataInput input,NbtAccounter accounting) {
            this.input=Objects.requireNonNull(input);this.accounting=Objects.requireNonNull(accounting);
        }
        CompoundTag read()throws IOException {
            int type=input.readByte();accounting.accountBytes(1);
            if(type==0)throw new IOException("Root tag must be a named compound tag");
            accounting.readUTF(input.readUTF());accounting.accountBytes(4);
            ReadBoundary boundary=new ReadBoundary(null,type);Tag root;
            try { root=value(type,boundary); }
            catch(IOException failure) { throw boundary.failure(failure); }
            while(!frames.isEmpty()) {
                ReadFrame frame=frames.peekLast();boundary=frame.boundary;
                try {
                    if(frame.tag instanceof CompoundTag map) {
                        accounting.accountBytes(2);int childType=input.readByte();
                        if(childType==0) { frames.removeLast();continue; }
                        String key=accounting.readUTF(input.readUTF());
                        accounting.accountBytes(28L+2L*key.length());accounting.accountBytes(4);
                        boundary=new ReadBoundary(key,childType);
                        Tag child=value(childType,boundary);
                        if(map.put(key,child)==null)accounting.accountBytes(36);
                    } else {
                        if(frame.remaining==0) { frames.removeLast();continue; }
                        Tag child=value(frame.elementType,boundary);ListTag list=(ListTag)frame.tag;
                        if(!list.addTag(list.size(),child))throw new IOException("NBT_LIST_ELEMENT_TYPE");
                        frame.remaining--;
                    }
                } catch(IOException failure) { throw boundary.failure(failure); }
            }
            if(!(root instanceof CompoundTag compound))throw new IOException("Root tag must be a named compound tag");
            return compound;
        }
        private int length(int width,String reason)throws IOException {
            int count=input.readInt();
            if(count<0)throw new IOException(reason);
            return count;
        }
        private Tag value(int type,ReadBoundary boundary)throws IOException {
            switch(type) {
                case 10: {
                    accounting.accountBytes(48);CompoundTag map=new CompoundTag();
                    frames.addLast(new ReadFrame(map,0,0,boundary));return map;
                }
                case 9: {
                    accounting.accountBytes(37);int elementType=input.readByte(),count=input.readInt();
                    if(count<0||elementType==0&&count>0)throw new IOException("NBT_LIST_LENGTH_OR_TYPE");
                    if(count>0) {
                        minimumPayload(elementType); // Every nonempty element must consume real bytes.
                    }
                    accounting.accountBytes(4L*count);
                    // Empty lists retain even their original header type, just as ListTag.load.
                    // This bounded zero-element load cannot recurse or allocate from a count.
                    ListTag list=count==0?emptyList(elementType):new ListTag();
                    if(count>0)frames.addLast(new ReadFrame(list,elementType,count,boundary));
                    return list;
                }
                case 7: {
                    accounting.accountBytes(24);int count=length(1,"NBT_BYTE_ARRAY_LENGTH");accounting.accountBytes(count);
                    ByteArrayOutputStream value=new ByteArrayOutputStream();byte[] chunk=new byte[Math.min(count,8192)];
                    for(int remaining=count;remaining>0;) {
                        int size=Math.min(remaining,chunk.length);input.readFully(chunk,0,size);value.write(chunk,0,size);remaining-=size;
                    }
                    return new ByteArrayTag(value.toByteArray());
                }
                case 11: {
                    accounting.accountBytes(24);int count=length(4,"NBT_INT_ARRAY_LENGTH");accounting.accountBytes(4L*count);
                    int[] value=new int[Math.min(count,1024)];
                    for(int i=0;i<count;i++) {
                        int next=input.readInt();if(i==value.length)value=Arrays.copyOf(value,grown(value.length,count));value[i]=next;
                    }
                    return new IntArrayTag(value.length==count?value:Arrays.copyOf(value,count));
                }
                case 12: {
                    accounting.accountBytes(24);int count=length(8,"NBT_LONG_ARRAY_LENGTH");accounting.accountBytes(8L*count);
                    long[] value=new long[Math.min(count,1024)];
                    for(int i=0;i<count;i++) {
                        long next=input.readLong();if(i==value.length)value=Arrays.copyOf(value,grown(value.length,count));value[i]=next;
                    }
                    return new LongArrayTag(value.length==count?value:Arrays.copyOf(value,count));
                }
                default: return TagTypes.getType(type).load(input,0,accounting);
            }
        }
        private static int grown(int old,int count) { return (int)Math.min(count,Math.max(16L,(long)old*2)); }
        private static int minimumPayload(int type)throws IOException {
            return switch(type) {
                case 1,10 -> 1;
                case 2,8 -> 2;
                case 3,5,7,11,12 -> 4;
                case 9 -> 5;
                case 4,6 -> 8;
                default -> throw new IOException("NBT_LIST_ELEMENT_TYPE");
            };
        }
    }
    private static ListTag emptyList(int type)throws IOException {
        return ListTag.TYPE.load(new DataInputStream(new ByteArrayInputStream(new byte[]{(byte)type,0,0,0,0})),0,NbtAccounter.UNLIMITED);
    }
    static boolean compressed(Encoded encoded)throws IOException {
        byte[] header=new byte[2];return encoded.read(0,header,0,2)==2&&(header[0]&255)==31&&(header[1]&255)==139;
    }
    static Encoded encode(CompoundTag image)throws IOException { return encode(image,true); }
    static Encoded encode(CompoundTag image,boolean compressed)throws IOException {
        validate(image);
        try(Builder bytes=new Builder()) {
            OutputStream sink=new FilterOutputStream(bytes) {
                @Override public void write(byte[] data,int offset,int length)throws IOException { out.write(data,offset,length); }
                @Override public void close() { }
            };
            try(var output=new DataOutputStream(compressed?new BufferedOutputStream(new java.util.zip.GZIPOutputStream(sink)):sink)) {
                output.writeByte(10);output.writeUTF("");writeTree(image,output,false);
            }
            return bytes.finish();
        }
    }
    private static int type(Tag tag)throws IOException {
        if(tag!=null)for(int i=0;i<STANDARD.length;i++)if(tag.getClass()==STANDARD[i])return i;
        throw new IOException("OPAQUE_TAG_SEMANTICS");
    }
    private static boolean container(Tag tag) { return tag instanceof CompoundTag||tag instanceof ListTag; }
    /** A cursor retains only actual ancestor containers; it never calls Tag.equals/hashCode. */
    private static final class Cursor {
        final Tag tag;
        final Iterator<String> keys;
        final int size;
        int index;
        Cursor(Tag tag,boolean sorted) {
            this.tag=tag;
            keys=tag instanceof CompoundTag map?(sorted?new TreeSet<>(map.getAllKeys()):map.getAllKeys()).iterator():null;
            size=tag instanceof ListTag list?list.size():0;
        }
        Step next() {
            if(keys!=null)return keys.hasNext()?Step.key(keys.next()):null;
            return index<size?Step.index(index++):null;
        }
    }
    static void validate(Tag root)throws IOException {
        type(root);if(!container(root))return;
        Set<Tag> active=Collections.newSetFromMap(new IdentityHashMap<>());
        Set<Tag> completed=Collections.newSetFromMap(new IdentityHashMap<>());
        ArrayDeque<Cursor> frames=new ArrayDeque<>();frames.addLast(new Cursor(root,false));active.add(root);
        while(!frames.isEmpty()) {
            Cursor frame=frames.peekLast();Step step=frame.next();
            if(step==null) { frames.removeLast();active.remove(frame.tag);completed.add(frame.tag);continue; }
            Tag child=child(frame.tag,step);type(child);
            if(!container(child)||completed.contains(child))continue;
            if(!active.add(child))throw new IOException("CYCLIC_NBT_IMAGE");
            frames.addLast(new Cursor(child,false));
        }
    }
    static List<Selection> locate(CompoundTag root,Map<CompoundTag,UUID> identities)throws IOException {
        validate(root);
        List<Selection> out=new ArrayList<>();List<Step> path=new ArrayList<>();
        Set<Tag> active=Collections.newSetFromMap(new IdentityHashMap<>());
        ArrayDeque<Cursor> frames=new ArrayDeque<>();frames.addLast(new Cursor(root,true));active.add(root);
        UUID subject=identities.get(root);if(subject!=null)out.add(new Selection(subject,path));
        while(!frames.isEmpty()) {
            Cursor frame=frames.peekLast();Step step=frame.next();
            if(step==null) {
                frames.removeLast();active.remove(frame.tag);if(!path.isEmpty())path.remove(path.size()-1);continue;
            }
            Tag child=child(frame.tag,step);type(child);path.add(step);
            if(child instanceof CompoundTag map) {
                subject=identities.get(map);if(subject!=null)out.add(new Selection(subject,path));
            }
            if(container(child)) {
                if(!active.add(child))throw new IOException("CYCLIC_NBT_IMAGE");
                frames.addLast(new Cursor(child,true));
            } else path.remove(path.size()-1);
        }
        return List.copyOf(out);
    }
    static Projection project(Encoded baseline,List<Selection> all,UUID target)throws IOException { return project(baseline,all,Set.of(target)); }
    static Projection project(Encoded baseline,List<Selection> all,Set<UUID> targets)throws IOException {
        CompoundTag image=decode(baseline);PathNode selected=selectors(all,targets);
        if(!selected.targetBelow)throw new IOException("NO_OBSERVED_TARGET_RECORD");
        for(Selection selection:all)if(targets.contains(selection.subject()))resolve(image,selection.path());
        if(selected.target)image=new CompoundTag();else prune(image,selected);
        // The full projected tree, including unknown keys, is the preservation contract.
        String preserved=semanticHash(image);Encoded expected=encode(image,compressed(baseline));
        try { return new Projection(expected,baseline.hash(),expected.hash(),preserved); }
        catch(Throwable failure) { expected.close();throw failure; }
    }
    record Filtered(CompoundTag image,List<Selection> remaining) { }
    static Filtered filter(CompoundTag input,List<Selection> all,Set<UUID> targets)throws IOException {
        validate(input);PathNode selected=selectors(all,targets);CompoundTag image=copy(input);
        Map<CompoundTag,UUID> identities=new IdentityHashMap<>();
        for(Selection selection:all) {
            Tag tag=resolve(image,selection.path());
            if(!(tag instanceof CompoundTag compound))throw new IOException("RECORD_NOT_COMPOUND");
            if(!targets.contains(selection.subject()))identities.put(compound,selection.subject());
        }
        if(selected.target)image=new CompoundTag();else prune(image,selected);
        return new Filtered(image,locate(image,identities));
    }
    private record CopyFrame(Cursor source,Tag target) { }
    private static CompoundTag copy(CompoundTag root)throws IOException {
        CompoundTag result=new CompoundTag();ArrayDeque<CopyFrame> frames=new ArrayDeque<>();
        Set<Tag> active=Collections.newSetFromMap(new IdentityHashMap<>());active.add(root);
        frames.addLast(new CopyFrame(new Cursor(root,false),result));
        while(!frames.isEmpty()) {
            CopyFrame frame=frames.peekLast();Step step=frame.source.next();
            if(step==null) {
                frames.removeLast();active.remove(frame.source.tag);
                if(frame.source.tag instanceof CompoundTag from)RecoverySources.projectedCopy(from,(CompoundTag)frame.target);
                continue;
            }
            Tag original=child(frame.source.tag,step);type(original);
            Tag value=original instanceof CompoundTag?new CompoundTag():original instanceof ListTag list?
                    (list.isEmpty()?emptyList(list.getElementType()):new ListTag()):original.copy();
            if(frame.target instanceof CompoundTag map)map.put(step.key(),value);
            else if(!((ListTag)frame.target).addTag(step.index(),value))throw new IOException("NBT_LIST_ELEMENT_TYPE");
            if(container(original)) {
                if(!active.add(original))throw new IOException("CYCLIC_NBT_IMAGE");
                frames.addLast(new CopyFrame(new Cursor(original,false),value));
            }
        }
        return result;
    }
    static Tag resolve(Tag root,List<Step> path)throws IOException {
        Tag node=root;for(Step step:path)node=child(node,step);return node;
    }
    private static Tag child(Tag node,Step step)throws IOException {
        Tag result;
        if(step.key()!=null&&node instanceof CompoundTag map)result=map.get(step.key());
        else if(step.key()==null&&node instanceof ListTag list&&step.index()<list.size())result=list.get(step.index());
        else throw new IOException("RECORD_SELECTOR_MISMATCH");
        if(result==null)throw new IOException("RECORD_SELECTOR_MISSING");return result;
    }
    /** Typed prefix index: overlap checks and removals scale with the actual selectors. */
    private static final class PathNode {
        final Map<Step,PathNode> children=new HashMap<>();
        boolean target,other,targetBelow,otherBelow;
        void include(boolean selected) { if(selected)targetBelow=true;else otherBelow=true; }
    }
    private static PathNode selectors(List<Selection> all,Set<UUID> targets)throws IOException {
        PathNode root=new PathNode();List<PathNode> ends=new ArrayList<>();
        for(Selection selection:all) {
            boolean selected=targets.contains(selection.subject());PathNode node=root;node.include(selected);
            for(Step step:selection.path()) { node=node.children.computeIfAbsent(step,ignored->new PathNode());node.include(selected); }
            if(selected)node.target=true;else node.other=true;ends.add(node);
        }
        for(PathNode node:ends)if(node.target&&node.otherBelow||node.other&&node.targetBelow)throw new IOException("MIXED_SUBJECT_SUBTREE");
        return root;
    }
    private static final class PruneFrame {
        final Tag tag;
        final Iterator<Map.Entry<Step,PathNode>> entries;
        PruneFrame(Tag tag,PathNode path) {
            this.tag=tag;
            List<Map.Entry<Step,PathNode>> selected=new ArrayList<>();
            for(var entry:path.children.entrySet())if(entry.getValue().targetBelow)selected.add(entry);
            // Remove a list's original indexes from the end so later selectors keep their meaning.
            if(tag instanceof ListTag)selected.sort(Comparator.comparingInt((Map.Entry<Step,PathNode> entry)->entry.getKey().index()).reversed());
            entries=selected.iterator();
        }
    }
    private static void prune(Tag root,PathNode selected)throws IOException {
        ArrayDeque<PruneFrame> frames=new ArrayDeque<>();frames.addLast(new PruneFrame(root,selected));
        while(!frames.isEmpty()) {
            PruneFrame frame=frames.peekLast();if(!frame.entries.hasNext()) { frames.removeLast();continue; }
            var entry=frame.entries.next();Step step=entry.getKey();PathNode path=entry.getValue();Tag node=child(frame.tag,step);
            if(path.target) {
                if(frame.tag instanceof CompoundTag map)map.remove(step.key());else ((ListTag)frame.tag).remove(step.index());
            } else frames.addLast(new PruneFrame(node,path));
        }
    }
    static String semanticHash(Tag tag)throws IOException {
        validate(tag);
        try {
            MessageDigest digest=MessageDigest.getInstance("SHA-256");
            OutputStream bounded=new OutputStream() {
                @Override public void write(int value) { digest.update((byte)value); }
                @Override public void write(byte[] bytes,int offset,int length) { digest.update(bytes,offset,length); }
            };
            writeTree(tag,new DataOutputStream(bounded),true);
            return HexFormat.of().formatHex(digest.digest());
        } catch(java.security.NoSuchAlgorithmException impossible) { throw new AssertionError(impossible); }
    }
    private static void writeTree(Tag root,DataOutputStream out,boolean canonical)throws IOException {
        ArrayDeque<Cursor> frames=new ArrayDeque<>();Set<Tag> active=Collections.newSetFromMap(new IdentityHashMap<>());
        writeValue(root,out,canonical,frames,active);
        while(!frames.isEmpty()) {
            Cursor frame=frames.peekLast();Step step=frame.next();
            if(step==null) {
                if(!canonical&&frame.tag instanceof CompoundTag)out.writeByte(0);
                frames.removeLast();active.remove(frame.tag);continue;
            }
            Tag child=child(frame.tag,step);int type=type(child);
            if(frame.tag instanceof CompoundTag) {
                if(!canonical) { out.writeByte(type);if(type==0)continue; }
                out.writeUTF(step.key());
            }
            writeValue(child,out,canonical,frames,active);
        }
    }
    private static void writeValue(Tag tag,DataOutputStream out,boolean canonical,ArrayDeque<Cursor> frames,Set<Tag> active)throws IOException {
        int type=type(tag);if(canonical)out.writeByte(type);
        if(container(tag)) {
            if(!active.add(tag))throw new IOException("CYCLIC_NBT_IMAGE");
            if(tag instanceof CompoundTag map) { if(canonical)out.writeInt(map.size()); }
            else {
                ListTag list=(ListTag)tag;
                if(!canonical)out.writeByte(list.isEmpty()?0:type(list.get(0)));
                out.writeInt(list.size());
            }
            frames.addLast(new Cursor(tag,canonical));
        } else if(tag instanceof StringTag string)out.writeUTF(string.getAsString());
        else tag.write(out);
    }
}
