package dev.ronova.pro;

import dev.ronova.pro.mixin.Access;
import java.util.*;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.entity.*;

/** The one old-list removal in vanilla onMove, not a general exemption from protection. */
public final class SectionMoves {
    private record Move(Entity entity,Set<Object> lists) { }
    private static final ThreadLocal<Move> moving=new ThreadLocal<>();
    private static final StackWalker callers=StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE);
    private SectionMoves() { }
    public static boolean remove(EntitySection<EntityAccess> section,EntityAccess target,Object owner) {
        if(!(target instanceof Entity entity)||!ProRuntime.preventIndexRemoval(entity))return section.remove(target);
        var frame=callers.walk(s->s.skip(1).findFirst().orElse(null));
        if(!(owner instanceof Access.Callback callback)||frame==null||frame.getDeclaringClass()!=owner.getClass()
                ||!(frame.getMethodName().equals("onMove")||frame.getMethodName().equals("m_142044_"))
                ||owner.getClass().getNestHost()!=PersistentEntitySectionManager.class
                ||callback.pro$entity()!=entity||callback.pro$section()!=(Object)section
                ||((Access.EntityState)entity).pro$callback()!=owner
                ||!(((Access.EntityState)entity).pro$level() instanceof ServerLevel level)||!level.getServer().isSameThread()
                ||callback.pro$sectionKey()==SectionPos.asLong(((Access.EntityState)entity).pro$blockPosition()))return false;
        var group=(Access.Group)((Access.Section)section).pro$storage();
        Set<Object> lists=Collections.newSetFromMap(new IdentityHashMap<>());lists.add(group.pro$all());lists.addAll(group.pro$classes().values());
        // No foreign container callbacks get this exemption.
        if(lists.stream().anyMatch(list->list.getClass()!=ArrayList.class))return false;
        Move previous=moving.get();moving.set(new Move(entity,lists));
        boolean removed;
        try {removed=section.remove(target);}
        finally {if(previous==null)moving.remove();else moving.set(previous);}
        if(removed)ProRuntime.sectionLeft(entity,section);
        return removed;
    }
    static boolean allows(Object container,Entity entity) {
        Move move=moving.get();if(move==null)return false;
        if(move.lists().contains(container))return move.entity()==entity;
        for(Object list:move.lists())if(BackingPolicy.backingOf(list,container)) {
            // Compaction shifts other occupants' slots; their logical removals still hit the
            // exact-list guard above. Raw/Unsafe writes made by foreign callbacks stay denied.
            var writer=callers.walk(frames->frames.filter(f->!f.getClassName().startsWith("dev.ronova.pro.")).findFirst().orElse(null));
            return writer!=null&&writer.getDeclaringClass()==ArrayList.class;
        }
        return false;
    }
}
