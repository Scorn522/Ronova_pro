package dev.ronova.pro;

import java.lang.reflect.*;
import java.util.*;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.server.level.ServerLevel;
import dev.ronova.pro.mixin.Access;

/** Full declared field images and reference edges; writes are restricted to the explicitly selected/private nodes. */
final class FieldGraph {
    record Body(Entity entity,ServerLevel level,UUID uuid,int entityId,EntityType<?> type) { }
    private enum Mode { CAPTURE, CLEAR, RESTORE }
    private static final Object UNREAD=new Object();
    private static final class Slot {
        final Field field;final int element;Object value=UNREAD;String pending="";
        Slot(Field field,int element){this.field=field;this.element=element;}
    }
    private static final class Node {
        final Object receiver;final boolean statics;final List<Slot> slots=new ArrayList<>();
        int cursor;boolean prepared,resourceReleased;String pending="";
        Node(Object receiver,boolean statics){this.receiver=receiver;this.statics=statics;}
    }
    private static final class Image {
        final UUID id=UUID.randomUUID();final Set<Entity> roots=Collections.newSetFromMap(new IdentityHashMap<>());
        final Map<Object,Node> nodes=new IdentityHashMap<>();final Set<Class<?>> statics=Collections.newSetFromMap(new IdentityHashMap<>());
        final Deque<Node> scanning=new ArrayDeque<>();final Map<Entity,UUID> removals=new IdentityHashMap<>();
        final Map<Entity,Body> bodies=new IdentityHashMap<>();
        Mode mode;String state="CAPTURING";List<Node> writing;int nodeCursor,slotCursor;boolean stopped;
        Image(Collection<? extends Entity> roots,Mode mode){this.roots.addAll(roots);this.mode=mode;}
    }
    private final ProRuntime runtime;
    private final Map<UUID,Image> images=new LinkedHashMap<>();
    private final Set<Entity> retired=Collections.newSetFromMap(Collections.synchronizedMap(new WeakIdentityMap<>()));
    private int next;
    FieldGraph(ProRuntime runtime){this.runtime=runtime;}
    UUID capture(Collection<? extends Entity> targets,boolean includeStatics,boolean clear){
        if(targets.isEmpty())throw new IllegalArgumentException("FIELD_GRAPH_TARGET_REQUIRED");Image image=new Image(targets,clear?Mode.CLEAR:Mode.CAPTURE);
        runtime.graphControls(this,image,image.roots,image.nodes,image.statics,image.scanning,image.removals,image.bodies);
        for(Entity root:targets){Access.EntityState actual=(Access.EntityState)root;image.bodies.put(root,new Body(root,(ServerLevel)actual.pro$level(),actual.pro$uuid(),actual.pro$id(),actual.pro$type()));add(image,root);
            if(includeStatics&&image.statics.add(root.getClass())){Node node=new Node(root.getClass(),true);runtime.graphControls(this,node,node.slots);image.nodes.put(root.getClass(),node);image.scanning.add(node);}}
        images.put(image.id,image);return image.id;
    }
    boolean restore(UUID id){Image image=images.get(id);if(image==null||!image.state.equals("CAPTURED")||image.roots.stream().anyMatch(root->runtime.graphStopped(this,root)))return false;
        image.mode=Mode.RESTORE;image.state="BODY_RETIREMENT";image.writing=new ArrayList<>(image.nodes.values());runtime.graphControls(this,image.writing);image.removals.clear();image.nodeCursor=0;image.slotCursor=0;image.stopped=false;return true;}
    boolean forget(UUID id){Image image=images.get(id);if(image==null||!image.state.equals("CAPTURED")&&!image.state.equals("CLEARED")&&!image.state.equals("RESTORED")&&!image.state.equals("SOURCE_RETIRED")&&!image.state.equals("CAPTURE_INCOMPLETE"))return false;
        images.remove(id);image.stopped=true;image.scanning.clear();image.nodes.clear();image.roots.clear();image.removals.clear();image.bodies.clear();image.writing=null;return true;}
    boolean retired(Entity entity){return retired.contains(entity);}
    private void add(Image image,Object value){
        if(value==null||RecoveryReferences.immutableScalar(value)||value instanceof Class<?>||value instanceof Enum<?>||image.nodes.containsKey(value))return;
        if(!runtime.graphOwns(this,image.roots,value))return;Node node=new Node(value,false);runtime.graphControls(this,node,node.slots);image.nodes.put(value,node);image.scanning.add(node);
    }
    void tick(){
        if(images.isEmpty())return;List<Image> order=new ArrayList<>(images.values());
        for(int i=0;i<order.size();i++){Image image=order.get(next++%order.size());if(next<0)next=0;if(image.stopped||Set.of("CAPTURED","CLEARED","RESTORED","SOURCE_RETIRED","CAPTURE_INCOMPLETE").contains(image.state))continue;
            try{if(image.state.equals("CAPTURING"))scan(image);else if(image.state.equals("BODY_RETIREMENT"))removeBodies(image);else if(image.state.equals("BODY_PUBLICATION"))publishBodies(image);else write(image);}
            catch(ReflectiveOperationException|RuntimeException unavailable){image.state="PENDING";if(image.writing!=null&&image.nodeCursor<image.writing.size())image.writing.get(image.nodeCursor).pending=unavailable.getClass().getSimpleName()+":"+String.valueOf(unavailable.getMessage());}
            break;
        }
    }
    private void prepare(Node node){
        if(node.prepared)return;node.prepared=true;
        if(!node.statics&&node.receiver.getClass().isArray()){for(int i=0;i<Array.getLength(node.receiver);i++)node.slots.add(new Slot(null,i));return;}
        Class<?> type=node.statics?(Class<?>)node.receiver:node.receiver.getClass();for(Field field:GraphFields.fields(type,node.statics))node.slots.add(new Slot(field,-1));
    }
    private void scan(Image image)throws ReflectiveOperationException{
        int budget=256;while(budget>0&&!image.scanning.isEmpty()){
            Node node=image.scanning.peek();prepare(node);
            while(budget-->0&&node.cursor<node.slots.size()){
                Slot slot=node.slots.get(node.cursor++);
                try{slot.value=slot.field==null?Array.get(node.receiver,slot.element):GraphFields.read(slot.field,node.statics?null:node.receiver);add(image,slot.value);}
                catch(ReflectiveOperationException|RuntimeException unavailable){slot.pending=unavailable.getClass().getSimpleName()+":"+String.valueOf(unavailable.getMessage());}
            }
            if(node.cursor==node.slots.size())image.scanning.remove();
        }
        if(!image.scanning.isEmpty())return;
        List<Node> nodes=new ArrayList<>(image.nodes.values());
        image.writing=nodes;
        runtime.graphControls(this,image.writing);
        if(nodes.stream().anyMatch(node->!node.pending.isEmpty()||node.slots.stream().anyMatch(slot->slot.value==UNREAD))){image.state="CAPTURE_INCOMPLETE";return;}
        image.state=image.mode==Mode.CLEAR?"BODY_RETIREMENT":"CAPTURED";
    }
    private void removeBodies(Image image){
        boolean complete=true;for(Entity root:image.roots){if(retired(root))continue;UUID operation=image.removals.get(root);if(operation==null){operation=runtime.graphRemove(this,root);image.removals.put(root,operation);}if(!runtime.graphBodyCleared(this,operation))complete=false;}
        if(!complete)return;
        for(Entity root:image.roots)retired.add(root);image.state=image.mode==Mode.CLEAR?"CLEARING":"RESTORING";image.nodeCursor=0;image.slotCursor=0;
    }
    private void write(Image image)throws ReflectiveOperationException{
        if(image.writing==null){image.state="CAPTURE_INCOMPLETE";return;}int budget=256;boolean incomplete=false;
        while(budget>0&&image.nodeCursor<image.writing.size()){
            Node node=image.writing.get(image.nodeCursor);
            if(image.mode==Mode.CLEAR&&!node.resourceReleased){String resource=runtime.graphRelease(this,image.roots,node.receiver);if(!resource.isEmpty()){node.pending=resource;incomplete=true;image.nodeCursor++;image.slotCursor=0;continue;}node.resourceReleased=true;node.pending="";}
            if(image.mode==Mode.RESTORE&&runtime.graphLifetime(this,node.receiver)){node.pending="FIELD_GRAPH_RESOURCE_HISTORY_RESTORE_PENDING";incomplete=true;image.nodeCursor++;image.slotCursor=0;continue;}
            while(budget-->0&&image.slotCursor<node.slots.size()){
                Slot slot=node.slots.get(image.slotCursor++);Object desired=image.mode==Mode.CLEAR?GraphFields.zero(slot.field==null?node.receiver.getClass().getComponentType():slot.field.getType()):slot.value;
                if(desired==UNREAD){slot.pending="FIELD_GRAPH_VALUE_UNREAD";incomplete=true;continue;}
                if(desired!=null&&!RecoveryReferences.immutableScalar(desired)&&runtime.graphStopped(this,desired)){slot.pending="FIELD_GRAPH_STOPPED_SOURCE";incomplete=true;continue;}
                try{
                    Object prior=slot.field==null?Array.get(node.receiver,slot.element):GraphFields.read(slot.field,node.statics?null:node.receiver);
                    boolean done=slot.field==null?GraphFields.writeArray(node.receiver,slot.element,prior,desired):GraphFields.same(slot.field,prior,desired)||GraphFields.write(slot.field,node.statics?null:node.receiver,prior,desired);
                    slot.pending=done?"":"FIELD_GRAPH_WRITE_CHANGED_OR_REFUSED";if(!done)incomplete=true;
                }catch(ReflectiveOperationException|RuntimeException unavailable){slot.pending=unavailable.getClass().getSimpleName()+":"+String.valueOf(unavailable.getMessage());incomplete=true;}
            }
            if(image.slotCursor==node.slots.size()){image.nodeCursor++;image.slotCursor=0;}
        }
        if(image.nodeCursor<image.writing.size())return;
        incomplete|=image.writing.stream().anyMatch(node->!node.pending.isEmpty()||node.slots.stream().anyMatch(slot->!slot.pending.isEmpty()));
        if(incomplete){image.state="PENDING";image.nodeCursor=0;image.slotCursor=0;return;}
        image.state=image.mode==Mode.CLEAR?"CLEARED":"BODY_PUBLICATION";
        if(image.mode==Mode.CLEAR)for(Node node:image.writing)for(Slot slot:node.slots)slot.value=null;
    }
    private void publishBodies(Image image){
        boolean complete=true;for(Body body:image.bodies.values()){Node node=image.nodes.get(body.entity());boolean admitted=runtime.graphAdmit(this,body,image.roots);if(node!=null)node.pending=admitted?"":"FIELD_GRAPH_BODY_PUBLICATION_PENDING";if(!admitted)complete=false;}
        if(complete)image.state="RESTORED";
    }
    void admission(Entity entity,boolean removed){if(removed)retired.add(entity);else retired.remove(entity);}
    void retireGroup(ProRuntime.Subject group){
        for(Image image:images.values()){
            boolean selected=false;
            for(Node node:List.copyOf(image.nodes.values())){
                if(runtime.graphSubject(this,node.receiver)==group){image.nodes.remove(node.receiver);image.scanning.remove(node);if(image.writing!=null)image.writing.remove(node);selected=true;}
                else for(Slot slot:node.slots)if(slot.value!=UNREAD&&slot.value!=null&&runtime.graphSubject(this,slot.value)==group){slot.value=null;slot.pending="FIELD_GRAPH_SOURCE_RETIRED";selected=true;}
            }
            if(image.roots.removeIf(root->runtime.groupSubject(root)==group))selected=true;
            image.statics.removeIf(type->runtime.graphSubject(this,type)==group);
            image.bodies.keySet().removeIf(root->runtime.groupSubject(root)==group);
            image.removals.keySet().removeIf(root->runtime.groupSubject(root)==group);
            if(selected){image.state="SOURCE_RETIRED";image.stopped=true;image.nodeCursor=0;image.slotCursor=0;}
        }
    }
    String state(UUID id){Image image=images.get(id);if(image==null)return "未找到字段图。";long slots=image.writing==null?image.nodes.values().stream().mapToLong(node->node.slots.size()).sum():image.writing.stream().mapToLong(node->node.slots.size()).sum();
        List<String> pending=new ArrayList<>();Collection<Node> nodes=image.writing==null?image.nodes.values():image.writing;
        for(Node node:nodes){if(!node.pending.isEmpty())pending.add(node.pending);for(Slot slot:node.slots)if(!slot.pending.isEmpty())pending.add(slot.pending);}
        return "FIELD_GRAPH="+id+";STATE="+image.state+";NODES="+image.nodes.size()+";SLOTS="+slots+";SHARED_REFERENCES=EDGES_ONLY;HISTORY=LIVE_REFERENCE_IMAGE;PENDING="+String.join(",",new LinkedHashSet<>(pending));
    }
    void close(){images.clear();retired.clear();}
    Object[] controlObjects(){return new Object[]{this,images,retired};}
}
