package dev.ronova.pro;

import java.io.*;
import java.security.MessageDigest;
import java.util.*;
import net.minecraft.nbt.*;

/** Exact NBT record projection. Paths are typed; map keys are never interpreted as list indexes. */
final class RecoveryImage {
    static final int MAX_BYTES=16*1024*1024, MAX_NODES=131072, MAX_DEPTH=128;
    record Step(String key,int index) {
        Step { if((key==null)==(index<0))throw new IllegalArgumentException("INVALID_PATH_STEP"); }
        static Step key(String key) { return new Step(Objects.requireNonNull(key),-1); }
        static Step index(int index) { return new Step(null,index); }
    }
    record Selection(UUID subject,List<Step> path) {
        Selection { path=List.copyOf(path); }
    }
    record Projection(byte[] encoded,String baselineHash,String expectedHash,String preservedHash) { }
    private RecoveryImage() { }
    static String hash(byte[] bytes) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); }
        catch(java.security.NoSuchAlgorithmException impossible) { throw new AssertionError(impossible); }
    }
    static CompoundTag decode(byte[] encoded) throws IOException {
        if(encoded.length<1||encoded.length>MAX_BYTES)throw new IOException("ENCODED_IMAGE_LIMIT");
        // Bound decompressed bytes too; compressed bombs never get an unbounded NBT decoder.
        try(var gzip=new java.util.zip.GZIPInputStream(new ByteArrayInputStream(encoded))) {
            byte[] raw=gzip.readNBytes(MAX_BYTES+1);
            if(raw.length>MAX_BYTES)throw new IOException("DECODED_IMAGE_LIMIT");
            var input=new DataInputStream(new ByteArrayInputStream(raw));
            CompoundTag tag=NbtIo.read(input,new NbtAccounter(MAX_BYTES));
            if(tag==null||input.available()!=0)throw new IOException("AMBIGUOUS_NBT_IMAGE");
            validate(tag,0,new int[]{0});
            return tag;
        }
    }
    static byte[] encode(CompoundTag image)throws IOException {
        validate(image,0,new int[]{0});
        ByteArrayOutputStream bytes=new ByteArrayOutputStream();
        NbtIo.writeCompressed(image,new FilterOutputStream(bytes) {
            int size;
            @Override public void write(int value)throws IOException { limit(1);out.write(value); }
            @Override public void write(byte[] data,int off,int len)throws IOException { limit(len);out.write(data,off,len); }
            private void limit(int n)throws IOException { if(n<0||size>MAX_BYTES-n)throw new IOException("CANDIDATE_LIMIT");size+=n; }
        });
        return bytes.toByteArray();
    }
    static void validate(Tag tag,int depth,int[] nodes)throws IOException {
        if(depth>MAX_DEPTH||++nodes[0]>MAX_NODES)throw new IOException("NBT_TRAVERSAL_LIMIT");
        Class<?>[] standard={EndTag.class,ByteTag.class,ShortTag.class,IntTag.class,LongTag.class,FloatTag.class,
                DoubleTag.class,ByteArrayTag.class,StringTag.class,ListTag.class,CompoundTag.class,IntArrayTag.class,LongArrayTag.class};
        int id=-1;for(int i=0;i<standard.length;i++)if(tag.getClass()==standard[i]) { id=i;break; }
        if(id<0)throw new IOException("OPAQUE_TAG_SEMANTICS");
        if(tag instanceof CompoundTag map)for(String key:map.getAllKeys())validate(map.get(key),depth+1,nodes);
        else if(tag instanceof ListTag list)for(Tag child:list)validate(child,depth+1,nodes);
    }
    static List<Selection> locate(CompoundTag root,Map<CompoundTag,UUID> identities)throws IOException {
        validate(root,0,new int[]{0});
        List<Selection> out=new ArrayList<>();
        locate(root,identities,new ArrayList<>(),out);
        if(out.size()>4096)throw new IOException("SOURCE_RECORD_LIMIT");
        return List.copyOf(out);
    }
    private static void locate(Tag node,Map<CompoundTag,UUID> identities,List<Step> path,List<Selection> out) {
        if(node instanceof CompoundTag map) {
            UUID subject=identities.get(map);
            if(subject!=null)out.add(new Selection(subject,path));
            for(String key:new TreeSet<>(map.getAllKeys())) {
                path.add(Step.key(key));locate(map.get(key),identities,path,out);path.remove(path.size()-1);
            }
        } else if(node instanceof ListTag list)for(int i=0;i<list.size();i++) {
            path.add(Step.index(i));locate(list.get(i),identities,path,out);path.remove(path.size()-1);
        }
    }
    static Projection project(byte[] baseline,List<Selection> all,UUID target)throws IOException {
        CompoundTag image=decode(baseline);
        List<List<Step>> paths=all.stream().filter(s->s.subject.equals(target)).map(Selection::path).toList();
        if(paths.isEmpty())throw new IOException("NO_OBSERVED_TARGET_RECORD");
        for(List<Step> path:paths) {
            if(path.isEmpty())throw new IOException("ROOT_RECORD_DISPOSITION_UNSUPPORTED");
            for(Selection other:all)if(!other.subject.equals(target)&&(prefix(path,other.path)||prefix(other.path,path)))
                throw new IOException("MIXED_SUBJECT_SUBTREE");
            resolve(image,path); // Every selector must resolve in the actual encoded baseline.
        }
        prune(image,new ArrayList<>(),paths);
        byte[] expected=encode(image);
        // The full projected tree, including unknown keys, is the preservation contract.
        return new Projection(expected,hash(baseline),hash(expected),semanticHash(image));
    }
    static Tag resolve(Tag root,List<Step> path)throws IOException {
        Tag node=root;
        for(Step step:path) {
            if(step.key!=null&&node instanceof CompoundTag map)node=map.get(step.key);
            else if(step.key==null&&node instanceof ListTag list&&step.index<list.size())node=list.get(step.index);
            else throw new IOException("RECORD_SELECTOR_MISMATCH");
            if(node==null)throw new IOException("RECORD_SELECTOR_MISSING");
        }
        return node;
    }
    private static boolean prefix(List<Step> left,List<Step> right) {
        return left.size()<=right.size()&&left.equals(right.subList(0,left.size()));
    }
    private static void prune(Tag node,List<Step> path,List<List<Step>> selected) {
        if(node instanceof CompoundTag map)for(String key:new ArrayList<>(map.getAllKeys())) {
            path.add(Step.key(key));
            if(selected.contains(path))map.remove(key);else prune(map.get(key),path,selected);
            path.remove(path.size()-1);
        } else if(node instanceof ListTag list)for(int i=list.size()-1;i>=0;i--) {
            path.add(Step.index(i));
            if(selected.contains(path))list.remove(i);else prune(list.get(i),path,selected);
            path.remove(path.size()-1);
        }
    }
    static String semanticHash(Tag tag)throws IOException {
        validate(tag,0,new int[]{0});
        try {
            MessageDigest digest=MessageDigest.getInstance("SHA-256");
            OutputStream bounded=new OutputStream() {
                int size;
                private void limit(int count)throws IOException { if(count<0||size>MAX_BYTES-count)throw new IOException("SEMANTIC_IMAGE_LIMIT");size+=count; }
                @Override public void write(int value)throws IOException { limit(1);digest.update((byte)value); }
                @Override public void write(byte[] bytes,int offset,int length)throws IOException { limit(length);digest.update(bytes,offset,length); }
            };
            canonical(tag,new DataOutputStream(bounded));
            return HexFormat.of().formatHex(digest.digest());
        } catch(java.security.NoSuchAlgorithmException impossible) { throw new AssertionError(impossible); }
    }
    private static void canonical(Tag tag,DataOutputStream out)throws IOException {
        out.writeByte(tag.getId());
        if(tag instanceof CompoundTag map) {
            var keys=new TreeSet<>(map.getAllKeys());out.writeInt(keys.size());
            for(String key:keys) { out.writeUTF(key);canonical(map.get(key),out); }
        } else if(tag instanceof ListTag list) {
            out.writeInt(list.size());for(Tag value:list)canonical(value,out);
        } else tag.write(out);
    }
}
