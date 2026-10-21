package com.proto781m.betterspawn.data;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

/**
 * {@link SpawnSlot} 的 NBT 序列化工具。
 *
 * <p>NBT 结构：
 * <pre>
 * slot = {
 *   type: "COMMAND" | "BED" | "ANCHOR",
 *   dim:  "minecraft:overworld",
 *   x, y, z: int,
 *   angle: float
 * }
 * </pre>
 *
 * <p>{@code valid} 是运行时校验结果，不持久化（每次重生前重新校验）。
 */
public final class SpawnSlotSerializer {

    public static final String KEY_TYPE = "type";
    public static final String KEY_DIM = "dim";
    public static final String KEY_X = "x";
    public static final String KEY_Y = "y";
    public static final String KEY_Z = "z";
    public static final String KEY_ANGLE = "angle";

    private SpawnSlotSerializer() {
    }

    public static CompoundTag write(SpawnSlot slot) {
        CompoundTag tag = new CompoundTag();
        tag.putString(KEY_TYPE, slot.type().name());
        tag.putString(KEY_DIM, slot.dimension().location().toString());
        tag.putInt(KEY_X, slot.pos().getX());
        tag.putInt(KEY_Y, slot.pos().getY());
        tag.putInt(KEY_Z, slot.pos().getZ());
        tag.putFloat(KEY_ANGLE, slot.angle());
        return tag;
    }

    public static SpawnSlot read(CompoundTag tag) {
        SpawnSlotType type;
        try {
            type = SpawnSlotType.valueOf(tag.getString(KEY_TYPE));
        } catch (IllegalArgumentException ex) {
            return null;
        }
        ResourceKey<Level> dim;
        try {
            dim = com.proto781m.betterspawn.util.DimensionUtil.parse(tag.getString(KEY_DIM));
        } catch (Exception ex) {
            return null;
        }
        BlockPos pos = new BlockPos(tag.getInt(KEY_X), tag.getInt(KEY_Y), tag.getInt(KEY_Z));
        return SpawnSlot.valid(type, dim, pos, tag.getFloat(KEY_ANGLE));
    }
}
