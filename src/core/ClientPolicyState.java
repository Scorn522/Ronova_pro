package dev.ronova.pro;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/** Main-thread policy mirror. The transport connection is supplied by the receiver, never by a packet. */
final class ClientPolicyState {
    private static final int CAPACITY=131072;
    private final Map<Integer,Entry> entries=new HashMap<>();
    private final Map<Integer,Long> revisions=new HashMap<>();
    Object[] controlObjects(){return new Object[]{entries,revisions};}
    private Object connection;
    private UUID session;
    private String dimension;
    private long scope=-1;
    private boolean overflow;
    private record Entry(UUID identity,long revision,boolean protectedNow) { }
    record Address(int id,UUID uuid) { }

    synchronized void connection(Object active) {
        if(connection==active)return;
        connection=active;session=null;dimension=null;scope=-1;overflow=false;entries.clear();revisions.clear();
    }
    synchronized boolean reset(Object source,UUID session,long scope,String dimension) {
        if(source==null||source!=connection||session==null||dimension==null||scope<=0)return false;
        if(this.session!=null&&!this.session.equals(session))return false;
        if(scope<this.scope)return false;
        if(scope==this.scope)return Objects.equals(this.dimension,dimension)&&!overflow;
        this.session=session;this.scope=scope;this.dimension=dimension;overflow=false;entries.clear();revisions.clear();return true;
    }
    synchronized boolean accept(Object source,UUID session,long scope,String dimension,long revision,int id,UUID uuid,boolean protect) {
        if(source==null||source!=connection||this.session==null||!this.session.equals(session)
                ||this.scope!=scope||!Objects.equals(this.dimension,dimension)||revision<=0||uuid==null)return false;
        Entry old=entries.get(id);
        // A false entry is a revocation tombstone; never delete it within the active scope.
        if(revision<=revisions.getOrDefault(id,0L))return false;
        if(old==null&&entries.size()>=CAPACITY) {
            // Keep known active decisions and tombstones; a new scope recovers capacity.
            overflow=true;return false;
        }
        revisions.put(id,revision);
        // Revoking an older UUID cannot remove a newer protected body which reused the id.
        if(protect||old==null||old.identity().equals(uuid)||!old.protectedNow())entries.put(id,new Entry(uuid,revision,protect));
        return true;
    }
    synchronized boolean denies(Object active,String dimension,int id,UUID uuid) {
        if(active==null||active!=connection||!Objects.equals(this.dimension,dimension)||uuid==null)return false;
        Entry current=entries.get(id);
        return current!=null&&current.protectedNow()&&uuid.equals(current.identity());
    }
    /** A scope activation applies retained terminal decisions once to the new world's actual mirrors. */
    synchronized List<Address> activeAddresses(Object active,String dimension) {
        if(active==null||active!=connection||!Objects.equals(this.dimension,dimension))return List.of();
        List<Address> result=new ArrayList<>();
        for(var entry:entries.entrySet())if(entry.getValue().protectedNow())result.add(new Address(entry.getKey(),entry.getValue().identity()));
        return List.copyOf(result);
    }
    synchronized boolean overflowed() { return overflow; }
    synchronized String diagnostic() { return overflow?"POLICY_CAPACITY_REACHED_KNOWN_DECISIONS_RETAINED_SCOPE_ROTATION_REQUIRED":"POLICY_STATE_TRACKED"; }
    synchronized UUID session() { return session; }
    synchronized long scope() { return scope; }
    synchronized String dimension() { return dimension; }
}
