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
        final Field field;final int element;Object value=UNREAD,expected=UNREAD;String pending="";boolean applied,preserved;
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
        final Set<Field> staticFields=new HashSet<>();
        final Deque<Node> scanning=new ArrayDeque<>();final Map<Entity,UUID> removals=new IdentityHashMap<>();
        final Map<Entity,Body> bodies=new IdentityHashMap<>();
        Mode mode;String state="CAPTURING",failure="";List<Node> writing;int nodeCursor,slotCursor;boolean stopped;
        Image(Collection<? extends Entity> roots,Mode mode){this.roots.addAll(roots);this.mode=mode;}
    }
    private final ProRuntime runtime;
    private final Map<UUID,Image> images=new LinkedHashMap<>();
    private final Set<Entity> retired=Collections.newSetFromMap(Collections.synchronizedMap(new WeakIdentityMap<>()));
    private int next;
    FieldGraph(ProRuntime runtime){this.runtime=runtime;}
    UUID capture(Collection<? extends Entity> targets,boolean includeStatics,boolean clear){
        if(targets.isEmpty())throw new IllegalArgumentException("FIELD_GRAPH_TARGET_REQUIRED");Image image=new Image(targets,clear?Mode.CLEAR:Mode.CAPTURE);
        runtime.graphControls(this,image,image.roots,image.nodes,image.statics,image.staticFields,image.scanning,image.removals,image.bodies);
        for(Entity root:targets){Access.EntityState actual=(Access.EntityState)root;image.bodies.put(root,new Body(root,(ServerLevel)actual.pro$level(),actual.pro$uuid(),actual.pro$id(),actual.pro$type()));add(image,root);
            if(includeStatics&&image.statics.add(root.getClass())){Node node=new Node(root.getClass(),true);runtime.graphControls(this,node,node.slots);image.nodes.put(root.getClass(),node);image.scanning.add(node);}}
        images.put(image.id,image);return image.id;
    }
    boolean restore(UUID id){Image image=images.get(id);if(image==null||!image.state.equals("CAPTURED")||image.roots.stream().anyMatch(root->runtime.graphStopped(this,root)))return false;
        for(Body body:image.bodies.values())if(!bodyCurrent(body)){image.failure="FIELD_GRAPH_BODY_VERSION_CHANGED";return false;}
        try{
            for(Node node:image.nodes.values())for(Slot slot:node.slots){
                slot.expected=image.roots.contains(node.receiver)?UNREAD:read(node,slot);slot.applied=false;slot.preserved=false;slot.pending="";
            }
        }catch(ReflectiveOperationException|RuntimeException unavailable){image.failure="FIELD_GRAPH_RESTORE_BASELINE:"+unavailable.getClass().getSimpleName()+":"+String.valueOf(unavailable.getMessage());return false;}
        image.mode=Mode.RESTORE;image.state="BODY_RETIREMENT";image.writing=new ArrayList<>(image.nodes.values());runtime.graphControls(this,image.writing);image.removals.clear();image.nodeCursor=0;image.slotCursor=0;image.stopped=false;return true;}
    boolean forget(UUID id){Image image=images.get(id);if(image==null||!Set.of("CAPTURED","CLEARED","CLEARED_PARTIAL","RESTORED","RESTORED_PARTIAL","SOURCE_RETIRED","CAPTURE_INCOMPLETE").contains(image.state))return false;
        images.remove(id);image.stopped=true;image.scanning.clear();image.nodes.clear();image.roots.clear();image.statics.clear();image.staticFields.clear();image.removals.clear();image.bodies.clear();image.writing=null;return true;}
    private static boolean bodyCurrent(Body body){
        Access.EntityState actual=(Access.EntityState)body.entity();
        if(actual.pro$level()!=body.level()||actual.pro$type()!=body.type()||!Objects.equals(actual.pro$uuid(),body.uuid())||actual.pro$id()!=body.entityId())return false;
        Entity uuid=body.level().getEntity(body.uuid()),id=body.level().getEntity(body.entityId());
        return (uuid==null||uuid==body.entity())&&(id==null||id==body.entity());
    }
    boolean retired(Entity entity){return retired.contains(entity);}
    private void add(Image image,Object value){
        if(value==null||RecoveryRecords.immutableScalar(value)||value instanceof Class<?>||value instanceof Enum<?>||image.nodes.containsKey(value))return;
        if(!runtime.graphOwns(this,image.roots,value))return;Node node=new Node(value,false);runtime.graphControls(this,node,node.slots);image.nodes.put(value,node);image.scanning.add(node);
    }
    void tick(){
        if(images.isEmpty())return;List<Image> order=new ArrayList<>(images.values());
        for(int i=0;i<order.size();i++){Image image=order.get(next++%order.size());if(next<0)next=0;if(image.stopped||Set.of("CAPTURED","CLEARED","CLEARED_PARTIAL","RESTORED","RESTORED_PARTIAL","SOURCE_RETIRED","CAPTURE_INCOMPLETE").contains(image.state))continue;
            try{
                switch(image.state){
                    case "CAPTURING" -> scan(image);
                    case "BODY_RETIREMENT" -> removeBodies(image);
                    case "BODY_PUBLICATION" -> publishBodies(image);
                    case "CLEARING","RESTORING" -> write(image);
                    default -> throw new IllegalStateException("FIELD_GRAPH_UNKNOWN_PHASE:"+image.state);
                }
                image.failure="";
            }catch(ReflectiveOperationException|RuntimeException unavailable){
                image.failure=unavailable.getClass().getSimpleName()+":"+String.valueOf(unavailable.getMessage());
            }
            break;
        }
    }
    private void prepare(Image image,Node node){
        if(node.prepared)return;
        List<Slot> slots=new ArrayList<>();
        if(!node.statics&&node.receiver.getClass().isArray()){
            for(int i=0;i<Array.getLength(node.receiver);i++)slots.add(new Slot(null,i));
        }else{
            Class<?> type=node.statics?(Class<?>)node.receiver:node.receiver.getClass();
            for(Field field:GraphFields.fields(type,node.statics))if(!node.statics||image.staticFields.add(field))slots.add(new Slot(field,-1));
        }
        node.slots.addAll(slots);node.prepared=true;
    }
    private void scan(Image image)throws ReflectiveOperationException{
        int budget=256;while(budget>0&&!image.scanning.isEmpty()){
            Node node=image.scanning.peek();prepare(image,node);
            while(budget-->0&&node.cursor<node.slots.size()){
                Slot slot=node.slots.get(node.cursor++);
                try{slot.value=read(node,slot);add(image,slot.value);}
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
    private static Object read(Node node,Slot slot)throws ReflectiveOperationException{return slot.field==null?Array.get(node.receiver,slot.element):GraphFields.read(slot.field,node.statics?null:node.receiver);}
    private static boolean same(Node node,Slot slot,Object left,Object right){return slot.field==null?GraphFields.same(node.receiver.getClass().getComponentType(),left,right):GraphFields.same(slot.field,left,right);}
    private void removeBodies(Image image)throws ReflectiveOperationException{
        boolean complete=true;for(Entity root:image.roots){if(retired(root))continue;UUID operation=image.removals.get(root);if(operation==null){
            if(image.mode==Mode.RESTORE&&!bodyCurrent(image.bodies.get(root)))throw new IllegalStateException("FIELD_GRAPH_BODY_VERSION_CHANGED");
            operation=runtime.graphRemove(this,root);image.removals.put(root,operation);
        }if(!runtime.graphBodyCleared(this,operation))complete=false;}
        if(!complete)return;
        for(Entity root:image.roots)retired.add(root);
        // Retirement intentionally changes the body's flags, callback and relations. Pin that
        // completed owner's actual image once; retries must not adopt a later writer's values.
        if(image.mode==Mode.RESTORE)for(Entity root:image.roots){Node node=image.nodes.get(root);if(node!=null)for(Slot slot:node.slots)if(slot.expected==UNREAD)slot.expected=read(node,slot);}
        image.state=image.mode==Mode.CLEAR?"CLEARING":"RESTORING";image.nodeCursor=0;image.slotCursor=0;
    }
    private void write(Image image)throws ReflectiveOperationException{
        if(image.writing==null){image.state="CAPTURE_INCOMPLETE";return;}int budget=256;boolean incomplete=false;
        while(budget>0&&image.nodeCursor<image.writing.size()){
            Node node=image.writing.get(image.nodeCursor);
            // An object observed under another body after capture is now shared.
            // Keep its original image for reporting, but never clear or restore its live fields.
            if(!node.statics&&!runtime.graphOwns(this,image.roots,node.receiver)){
                node.pending="";for(Slot slot:node.slots){slot.preserved=true;slot.pending="FIELD_GRAPH_SHARED_ORIGIN_RETAINED";}
                image.nodeCursor++;image.slotCursor=0;continue;
            }
            if(image.mode==Mode.CLEAR&&!node.resourceReleased){String resource=runtime.graphRelease(this,image.roots,node.receiver);if(!resource.isEmpty()){node.pending=resource;incomplete=true;image.nodeCursor++;image.slotCursor=0;continue;}node.resourceReleased=true;node.pending="";}
            if(image.mode==Mode.RESTORE&&runtime.graphLifetime(this,node.receiver)){node.pending="FIELD_GRAPH_RESOURCE_HISTORY_RESTORE_PENDING";incomplete=true;image.nodeCursor++;image.slotCursor=0;continue;}
            while(budget-->0&&image.slotCursor<node.slots.size()){
                Slot slot=node.slots.get(image.slotCursor++);Object desired=image.mode==Mode.CLEAR?GraphFields.zero(slot.field==null?node.receiver.getClass().getComponentType():slot.field.getType()):slot.value;
                if(!node.statics&&!runtime.graphOwns(this,image.roots,node.receiver)){slot.pending="FIELD_GRAPH_SHARED_ORIGIN_RETAINED";slot.preserved=true;continue;}
                // Inherited/interface statics remain part of the field image, but the
                // declared-static command only selects declarations on the actual roots.
                if(node.statics&&!image.statics.contains(slot.field.getDeclaringClass())){slot.pending="FIELD_GRAPH_SHARED_DECLARATION_RETAINED";slot.preserved=true;continue;}
                if(image.mode==Mode.RESTORE&&(slot.applied||slot.preserved))continue;
                if(desired==UNREAD){slot.pending="FIELD_GRAPH_VALUE_UNREAD";incomplete=true;continue;}
                if(desired!=null&&!RecoveryRecords.immutableScalar(desired)&&runtime.graphStopped(this,desired)){slot.pending="FIELD_GRAPH_STOPPED_SOURCE";incomplete=true;continue;}
                try{
                    Object prior=read(node,slot),expected=image.mode==Mode.RESTORE?slot.expected:prior;
                    if(image.mode==Mode.RESTORE&&expected==UNREAD){slot.pending="FIELD_GRAPH_RESTORE_BASELINE_UNREAD";incomplete=true;continue;}
                    if(image.mode==Mode.RESTORE&&!same(node,slot,prior,expected)&&!same(node,slot,prior,desired)){
                        slot.pending="FIELD_GRAPH_LATER_CHANGE_PRESERVED";slot.preserved=true;continue;
                    }
                    Object originLock=runtime.graphOriginLock(this);java.util.function.BooleanSupplier exclusive=()->node.statics||runtime.graphExclusive(this,image.roots,node.receiver);
                    boolean done=same(node,slot,prior,desired)||(slot.field==null?GraphFields.writeArray(node.receiver,slot.element,expected,desired,originLock,exclusive):GraphFields.write(slot.field,node.statics?null:node.receiver,expected,desired,originLock,exclusive));
                    if(done&&image.mode==Mode.RESTORE)slot.applied=true;
                    slot.pending=done?"":"FIELD_GRAPH_WRITE_CHANGED_OR_REFUSED";if(!done)incomplete=true;
                }catch(ReflectiveOperationException|RuntimeException unavailable){slot.pending=unavailable.getClass().getSimpleName()+":"+String.valueOf(unavailable.getMessage());incomplete=true;}
            }
            if(image.slotCursor==node.slots.size()){image.nodeCursor++;image.slotCursor=0;}
        }
        if(image.nodeCursor<image.writing.size())return;
        incomplete|=image.writing.stream().anyMatch(node->!node.pending.isEmpty()||node.slots.stream().anyMatch(slot->!slot.pending.isEmpty()&&!slot.preserved));
        if(incomplete){image.nodeCursor=0;image.slotCursor=0;return;}
        image.state=image.mode==Mode.CLEAR?(image.writing.stream().anyMatch(node->node.slots.stream().anyMatch(slot->slot.preserved))?"CLEARED_PARTIAL":"CLEARED"):"BODY_PUBLICATION";
        if(image.mode==Mode.CLEAR)for(Node node:image.writing)for(Slot slot:node.slots)slot.value=null;
    }
    private void publishBodies(Image image){
        boolean complete=true;for(Body body:image.bodies.values()){Node node=image.nodes.get(body.entity());boolean admitted=runtime.graphAdmit(this,body,image.roots);if(node!=null)node.pending=admitted?"":"FIELD_GRAPH_BODY_PUBLICATION_PENDING";if(!admitted)complete=false;}
        if(complete)image.state=image.writing.stream().anyMatch(node->node.slots.stream().anyMatch(slot->slot.preserved))?"RESTORED_PARTIAL":"RESTORED";
    }
    void admission(Entity entity,boolean removed){if(removed)retired.add(entity);else retired.remove(entity);}
    void retireGroup(ProRuntime.Subject group){
        for(Image image:images.values()){
            boolean selected=false;
            for(Node node:List.copyOf(image.nodes.values())){
                if(runtime.graphSubject(this,node.receiver)==group){image.nodes.remove(node.receiver);image.scanning.remove(node);if(image.writing!=null)image.writing.remove(node);selected=true;}
                else for(Slot slot:node.slots)if(slot.value!=UNREAD&&slot.value!=null&&runtime.graphSubject(this,slot.value)==group||slot.expected!=UNREAD&&slot.expected!=null&&runtime.graphSubject(this,slot.expected)==group){slot.value=null;slot.expected=null;slot.pending="FIELD_GRAPH_SOURCE_RETIRED";selected=true;}
            }
            if(image.roots.removeIf(root->runtime.groupSubject(root)==group))selected=true;
            image.statics.removeIf(type->runtime.graphSubject(this,type)==group);
            image.bodies.keySet().removeIf(root->runtime.groupSubject(root)==group);
            image.removals.keySet().removeIf(root->runtime.groupSubject(root)==group);
            if(selected){image.state="SOURCE_RETIRED";image.stopped=true;image.nodeCursor=0;image.slotCursor=0;}
        }
    }
    String state(UUID id){Image image=images.get(id);if(image==null)return "未找到字段图。";long slots=image.writing==null?image.nodes.values().stream().mapToLong(node->node.slots.size()).sum():image.writing.stream().mapToLong(node->node.slots.size()).sum();
        List<String> pending=new ArrayList<>();if(!image.failure.isEmpty())pending.add(image.failure);Collection<Node> nodes=image.writing==null?image.nodes.values():image.writing;
        for(Node node:nodes){if(!node.pending.isEmpty())pending.add(node.pending);for(Slot slot:node.slots)if(!slot.pending.isEmpty())pending.add(slot.pending);}
        return "FIELD_GRAPH="+id+";STATE="+image.state+";NODES="+image.nodes.size()+";SLOTS="+slots+";SHARED_REFERENCES=EDGES_ONLY;HISTORY=LIVE_REFERENCE_IMAGE;PENDING="+String.join(",",new LinkedHashSet<>(pending));
    }
    void close(){images.clear();retired.clear();}
    Object[] controlObjects(){return new Object[]{this,images,retired};}
}
