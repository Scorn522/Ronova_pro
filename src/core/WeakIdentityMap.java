package dev.ronova.pro;

import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;
import java.util.*;
import java.util.function.*;

/** Weak identity storage. Reads neither lock nor drain the retirement queue. */
final class WeakIdentityMap<K,V> extends AbstractMap<K,V> {
    private static final Object REVISION_LOCK=new Object();
    private static volatile long modificationRevision;
    private static volatile int activeWriters;
    static long revision(){long value=modificationRevision;return activeWriters==0&&value==modificationRevision?value:-1;}
    private static void writing(){synchronized(REVISION_LOCK){activeWriters++;modificationRevision++;}}
    private static void written(){synchronized(REVISION_LOCK){modificationRevision++;activeWriters--;}}
    private final ReferenceQueue<K> queue=new ReferenceQueue<>();
    private volatile Node<K,V>[] entries=table(16);
    private int count;
    private static final class Key<K,V> extends WeakReference<K> {
        final int hash;volatile V value;volatile boolean linked=true;
        Key(K key,V value,ReferenceQueue<K> queue){super(key,queue);hash=System.identityHashCode(key);this.value=value;}
    }
    private static final class Node<K,V> {
        final Key<K,V> key;final Node<K,V> next;
        Node(Key<K,V> key,Node<K,V> next){this.key=key;this.next=next;}
    }
    @SuppressWarnings("unchecked") private static <K,V> Node<K,V>[] table(int size){return (Node<K,V>[])new Node<?,?>[size];}
    private static int bucket(int hash,int size){return (hash^(hash>>>16))&(size-1);}
    private Key<K,V> find(Object key){
        if(key==null)return null;int hash=System.identityHashCode(key);var table=entries;
        for(Node<K,V> node=table[bucket(hash,table.length)];node!=null;node=node.next){
            Key<K,V> entry=node.key;if(entry.hash==hash&&entry.get()==key&&entry.linked)return entry;
        }
        return null;
    }
    private void unlink(Key<K,V> key){
        if(!key.linked)return;
        var table=entries;int at=bucket(key.hash,table.length);Node<K,V> replacement=null;
        // Copy a bucket only on mutation; readers retain an intact old chain.
        for(Node<K,V> node=table[at];node!=null;node=node.next)if(node.key!=key)replacement=new Node<>(node.key,replacement);
        key.linked=false;key.value=null;table[at]=replacement;count--;entries=table;
    }
    @SuppressWarnings("unchecked") private void reap(){
        Key<K,V> key;while((key=(Key<K,V>)queue.poll())!=null)unlink(key);
    }
    private void grow(){
        var prior=entries;if(prior.length>=1<<30)return;
        Node<K,V>[] next=table(prior.length*2);
        for(int i=0;i<prior.length;i++)for(Node<K,V> node=prior[i];node!=null;node=node.next){
            Key<K,V> key=node.key;K live=key.get();
            if(live==null){if(key.linked){key.linked=false;key.value=null;count--;}continue;}
            int at=bucket(key.hash,next.length);next[at]=new Node<>(key,next[at]);
        }
        entries=next;
    }
    @Override public V get(Object key){return getOrDefault(key,null);}
    @Override public V getOrDefault(Object key,V fallback){
        Key<K,V> entry=find(key);if(entry==null)return fallback;V value=entry.value;
        // Removal must not become an apparent stored null.
        return entry.linked?value:fallback;
    }
    @Override public boolean containsKey(Object key){Key<K,V> entry=find(key);return entry!=null&&entry.linked;}
    @Override public synchronized V put(K key,V value){
        writing();try {
        Objects.requireNonNull(key);reap();Key<K,V> entry=find(key);
        if(entry!=null){V old=entry.value;entry.value=value;return old;}
        if(count>=entries.length-(entries.length>>>2))grow();
        entry=new Key<>(key,value,queue);int at=bucket(entry.hash,entries.length);
        Node<K,V>[] table=entries;table[at]=new Node<>(entry,table[at]);count++;entries=table;return null;
        }finally{written();}
    }
    @Override public synchronized V remove(Object key){writing();try{reap();Key<K,V> entry=find(key);if(entry==null)return null;V old=entry.value;unlink(entry);return old;}finally{written();}}
    @Override public synchronized int size(){reap();return count;}
    @Override public synchronized void clear(){
        writing();try {
        for(int i=0;i<entries.length;i++)for(Node<K,V> node=entries[i];node!=null;node=node.next){node.key.linked=false;node.key.value=null;}
        entries=table(16);count=0;while(queue.poll()!=null){}
        }finally{written();}
    }
    // Preserve atomic compound updates when a SynchronizedMap wrapper is removed.
    @Override public synchronized V putIfAbsent(K key,V value){return super.putIfAbsent(key,value);}
    @Override public synchronized boolean remove(Object key,Object value){return super.remove(key,value);}
    @Override public synchronized V replace(K key,V value){return super.replace(key,value);}
    @Override public synchronized boolean replace(K key,V old,V value){return super.replace(key,old,value);}
    @Override public synchronized V computeIfAbsent(K key,Function<? super K,? extends V> f){return super.computeIfAbsent(key,f);}
    @Override public synchronized V computeIfPresent(K key,BiFunction<? super K,? super V,? extends V> f){return super.computeIfPresent(key,f);}
    @Override public synchronized V compute(K key,BiFunction<? super K,? super V,? extends V> f){return super.compute(key,f);}
    @Override public synchronized V merge(K key,V value,BiFunction<? super V,? super V,? extends V> f){return super.merge(key,value,f);}
    @Override public synchronized void putAll(Map<? extends K,? extends V> values){super.putAll(values);}
    @Override public synchronized void replaceAll(BiFunction<? super K,? super V,? extends V> f){
        Objects.requireNonNull(f);for(Entry<K,V> entry:entrySet())put(entry.getKey(),f.apply(entry.getKey(),entry.getValue()));
    }
    private synchronized List<Key<K,V>> keys(){
        reap();var keys=new ArrayList<Key<K,V>>(count);
        for(int i=0;i<entries.length;i++)for(Node<K,V> node=entries[i];node!=null;node=node.next)keys.add(node.key);
        return keys;
    }
    @Override public synchronized Set<Entry<K,V>> entrySet(){
        reap();return new AbstractSet<>(){
            @Override public int size(){return WeakIdentityMap.this.size();}
            @Override public Iterator<Entry<K,V>> iterator(){
                var keys=keys().iterator();return new Iterator<>(){
                    Key<K,V> next,last;K live;
                    @Override public boolean hasNext(){while(live==null&&keys.hasNext()){next=keys.next();if(next.linked)live=next.get();}return live!=null;}
                    @Override public Entry<K,V> next(){
                        if(!hasNext())throw new NoSuchElementException();
                        var result=new SimpleImmutableEntry<K,V>(live,get(live));last=next;next=null;live=null;return result;
                    }
                    @Override public void remove(){
                        if(last==null)throw new IllegalStateException();
                        synchronized(WeakIdentityMap.this){K key=last.get();if(last.linked)unlink(last);else if(key!=null)WeakIdentityMap.this.remove(key);}
                        last=null;
                    }
                };
            }
        };
    }
}
