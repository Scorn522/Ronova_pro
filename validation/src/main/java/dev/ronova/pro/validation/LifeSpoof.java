package dev.ronova.pro.validation;
import java.util.*;
import net.minecraft.world.entity.Entity;
/** Own fault scenario only: no final policy, binding, hash or completion evidence. */
public final class LifeSpoof {
    private static final Map<Entity,Float> deltas=Collections.synchronizedMap(new WeakHashMap<>());
    public static void set(Entity entity,float delta){deltas.put(entity,delta);}
    public static Float delta(Object entity){return deltas.get(entity);}
}
