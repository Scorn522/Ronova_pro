package net.minecraft.world.entity;
/** Standalone observer fixture only; never in a Forge fixture or production payload. */
public class Entity {
 public final long value;public final double fraction;
 public Entity(long value,double fraction) { this.value=value;this.fraction=fraction; }
}
