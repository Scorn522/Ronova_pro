package dev.ronova.pro;

import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;
import java.util.*;

/** Identity provenance without ownership. Collection never supplies a release or completion fact. */
final class WeakIdentityMap<K,V> extends AbstractMap<K,V> {
    private final ReferenceQueue<K> queue=new ReferenceQueue<>();
    private Key<K,V>[] entries=table(16);
    private int count;
    private static final class Key<K,V> extends WeakReference<K> {
        final int hash;V value;Key<K,V> next;
        Key(K key,V value,ReferenceQueue<K> queue,int hash,Key<K,V> next) {
            super(key,queue);this.value=value;this.hash=hash;this.next=next;
        }
    }
    @SuppressWarnings("unchecked") private static <K,V> Key<K,V>[] table(int size) {return (Key<K,V>[])new Key<?,?>[size];}
    private static int bucket(int hash,int size) {return (hash^(hash>>>16))&(size-1);}
    private Key<K,V> find(Object key) {
        if(key==null)return null;int hash=System.identityHashCode(key);
        for(Key<K,V> entry=entries[bucket(hash,entries.length)];entry!=null;entry=entry.next)
            if(entry.hash==hash&&entry.get()==key)return entry;
        return null;
    }
    private boolean linked(Key<K,V> key) {
        for(Key<K,V> entry=entries[bucket(key.hash,entries.length)];entry!=null;entry=entry.next)if(entry==key)return true;
        return false;
    }
    private void unlink(Key<K,V> key) {
        int at=bucket(key.hash,entries.length);Key<K,V> previous=null;
        for(Key<K,V> entry=entries[at];entry!=null;entry=entry.next){
            if(entry==key){
                if(previous==null)entries[at]=entry.next;else previous.next=entry.next;
                entry.next=null;entry.value=null;count--;return;
            }
            previous=entry;
        }
    }
    @SuppressWarnings("unchecked") private void reap() {Object ref;while((ref=queue.poll())!=null)unlink((Key<K,V>)ref);}
    private void grow() {
        if(entries.length>=1<<30)return;
        Key<K,V>[] next=table(entries.length*2);
        for(Key<K,V> first:entries)for(Key<K,V> entry=first;entry!=null;){
            Key<K,V> following=entry.next;int at=bucket(entry.hash,next.length);entry.next=next[at];next[at]=entry;entry=following;
        }
        entries=next;
    }
    // These reads allocate no temporary weak key and retain no lookup object.
    // As before, callers provide synchronization for mutation and queue reaping.
    @Override public V get(Object key) {reap();Key<K,V> entry=find(key);return entry==null?null:entry.value;}
    @Override public boolean containsKey(Object key) {reap();return find(key)!=null;}
    @Override public V put(K key,V value) {
        reap();Objects.requireNonNull(key);Key<K,V> entry=find(key);
        if(entry!=null){V previous=entry.value;entry.value=value;return previous;}
        if(count>=entries.length-(entries.length>>>2))grow();
        int hash=System.identityHashCode(key),at=bucket(hash,entries.length);
        entries[at]=new Key<>(key,value,queue,hash,entries[at]);count++;return null;
    }
    @Override public V remove(Object key) {reap();Key<K,V> entry=find(key);if(entry==null)return null;V previous=entry.value;unlink(entry);return previous;}
    @Override public int size() {reap();return count;}
    @Override public void clear() {
        // Snapshot iterators hold weak nodes, not values of removed mappings.
        for(int at=0;at<entries.length;at++){
            Key<K,V> entry=entries[at];entries[at]=null;
            while(entry!=null){Key<K,V> next=entry.next;entry.next=null;entry.value=null;entry=next;}
        }
        count=0;while(queue.poll()!=null) { }
    }
    @Override public Set<Entry<K,V>> entrySet() {
        reap();return new AbstractSet<>() {
            @Override public int size() { return WeakIdentityMap.this.size(); }
            @Override public Iterator<Entry<K,V>> iterator() {
                var snapshot=new ArrayList<Key<K,V>>(count);
                for(Key<K,V> first:entries)for(Key<K,V> entry=first;entry!=null;entry=entry.next)snapshot.add(entry);
                var keys=snapshot.iterator();
                return new Iterator<>() {
                    Key<K,V> next,last;K live;
                    @Override public boolean hasNext() {
                        while(live==null&&keys.hasNext()) { next=keys.next();live=next.get(); }
                        return live!=null;
                    }
                    @Override public Entry<K,V> next() {
                        if(!hasNext())throw new NoSuchElementException();
                        var current=find(live);var result=new SimpleImmutableEntry<>(live,current==null?null:current.value);last=next;next=null;live=null;return result;
                    }
                    @Override public void remove() {
                        if(last==null)throw new IllegalStateException();
                        // Preserve snapshot-key removal even if that identity
                        // was removed and reinserted since iterator creation.
                        if(linked(last))unlink(last);else {K key=last.get();Key<K,V> current=find(key);if(current!=null)unlink(current);}
                        last=null;
                    }
                };
            }
        };
    }
}
