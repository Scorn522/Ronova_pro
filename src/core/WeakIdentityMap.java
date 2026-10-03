package dev.ronova.pro;

import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;
import java.util.*;

/** Identity provenance without ownership. Collection never supplies a release or completion fact. */
final class WeakIdentityMap<K,V> extends AbstractMap<K,V> {
    private final ReferenceQueue<K> queue=new ReferenceQueue<>();
    private final Map<Key<K>,V> entries=new HashMap<>();
    private static final class Key<K> extends WeakReference<K> {
        final int hash;
        Key(K value,ReferenceQueue<K> queue) { super(value,queue);hash=System.identityHashCode(value); }
        @Override public int hashCode() { return hash; }
        @Override public boolean equals(Object other) {
            if(this==other)return true;
            Object value=get();return value!=null&&other instanceof Key<?> key&&value==key.get();
        }
    }
    private void reap() { Object ref;while((ref=queue.poll())!=null)entries.remove(ref); }
    @SuppressWarnings("unchecked") private Key<K> lookup(Object value) { return new Key<>((K)value,null); }
    @Override public V get(Object key) { reap();return key==null?null:entries.get(lookup(key)); }
    @Override public boolean containsKey(Object key) { reap();return key!=null&&entries.containsKey(lookup(key)); }
    @Override public V put(K key,V value) { reap();return entries.put(new Key<>(Objects.requireNonNull(key),queue),value); }
    @Override public V remove(Object key) { reap();return key==null?null:entries.remove(lookup(key)); }
    @Override public int size() { reap();return entries.size(); }
    @Override public void clear() { entries.clear();while(queue.poll()!=null) { } }
    @Override public Set<Entry<K,V>> entrySet() {
        reap();return new AbstractSet<>() {
            @Override public int size() { return WeakIdentityMap.this.size(); }
            @Override public Iterator<Entry<K,V>> iterator() {
                var keys=new ArrayList<>(entries.keySet()).iterator();
                return new Iterator<>() {
                    Key<K> next,last;K live;
                    @Override public boolean hasNext() {
                        while(live==null&&keys.hasNext()) { next=keys.next();live=next.get(); }
                        return live!=null;
                    }
                    @Override public Entry<K,V> next() {
                        if(!hasNext())throw new NoSuchElementException();
                        var result=new SimpleImmutableEntry<>(live,entries.get(next));last=next;next=null;live=null;return result;
                    }
                    @Override public void remove() {
                        if(last==null)throw new IllegalStateException();entries.remove(last);last=null;
                    }
                };
            }
        };
    }
}
