package com.proto781m.betterspawn.data;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import com.proto781m.betterspawn.util.DimensionUtil;

/**
 * Layer 2 + Layer 3 —— 玩家维度出生点 + 双槽重生点。
 *
 * <p>全部存于玩家自己的持久化 NBT（{@code player.getPersistentData()} 下的
 * {@value #ROOT} 复合标签），由 NeoForge 自动随玩家存档保存/读取，无需额外 SavedData。
 *
 * <ul>
 *   <li>Layer 2：{@code dimensionSpawns}，每个玩家在各维度的“世界出生点”；</li>
 *   <li>Layer 3：{@code spawnSlots}，指令槽 / 床槽 / 锚槽三个重生点槽。</li>
 * </ul>
 */
public final class PlayerSpawnData {

    /** 玩家 NBT 根键。 */
    public static final String ROOT = "BetterSpawn";
    public static final String KEY_DIM_SPAWNS = "dimensionSpawns";
    private static final String KEY_SLOT = "slot";

    private PlayerSpawnData() {
    }

    /** 获取玩家的 BetterSpawn 根复合标签（不存在则创建）。 */
    public static CompoundTag root(ServerPlayer player) {
        CompoundTag data = player.getPersistentData();
        if (!data.contains(ROOT)) {
            data.put(ROOT, new CompoundTag());
        }
        return data.getCompound(ROOT);
    }

    // ---------- Layer 2：玩家维度出生点 ----------

    public static Optional<SpawnPoint> getDimensionSpawn(ServerPlayer player, ResourceKey<Level> dimension) {
        CompoundTag root = root(player);
        ListTag list = root.getList(KEY_DIM_SPAWNS, Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag t = list.getCompound(i);
            if (t.getString("dim").equals(DimensionUtil.id(dimension))) {
                BlockPos pos = new BlockPos(t.getInt("x"), t.getInt("y"), t.getInt("z"));
                return Optional.of(new SpawnPoint(pos, t.getFloat("angle")));
            }
        }
        return Optional.empty();
    }

    public static void setDimensionSpawn(ServerPlayer player, ResourceKey<Level> dimension, BlockPos pos, float angle) {
        CompoundTag root = root(player);
        ListTag list = root.getList(KEY_DIM_SPAWNS, Tag.TAG_COMPOUND);
        ListTag updated = new ListTag();
        boolean found = false;
        for (int i = 0; i < list.size(); i++) {
            CompoundTag t = list.getCompound(i);
            if (t.getString("dim").equals(DimensionUtil.id(dimension))) {
                found = true;
                updated.add(writeDimensionSpawn(dimension, pos, angle));
            } else {
                updated.add(t);
            }
        }
        if (!found) {
            updated.add(writeDimensionSpawn(dimension, pos, angle));
        }
        root.put(KEY_DIM_SPAWNS, updated);
    }

    public static void clearDimensionSpawn(ServerPlayer player, ResourceKey<Level> dimension) {
        CompoundTag root = root(player);
        ListTag list = root.getList(KEY_DIM_SPAWNS, Tag.TAG_COMPOUND);
        ListTag updated = new ListTag();
        for (int i = 0; i < list.size(); i++) {
            CompoundTag t = list.getCompound(i);
            if (!t.getString("dim").equals(DimensionUtil.id(dimension))) {
                updated.add(t);
            }
        }
        root.put(KEY_DIM_SPAWNS, updated);
    }

    private static CompoundTag writeDimensionSpawn(ResourceKey<Level> dimension, BlockPos pos, float angle) {
        CompoundTag t = new CompoundTag();
        t.putString("dim", DimensionUtil.id(dimension));
        t.putInt("x", pos.getX());
        t.putInt("y", pos.getY());
        t.putInt("z", pos.getZ());
        t.putFloat("angle", angle);
        return t;
    }

    // ---------- Layer 3：双槽重生点 ----------

    public static Optional<SpawnSlot> getSlot(ServerPlayer player, SpawnSlotType type) {
        CompoundTag root = root(player);
        if (!root.contains(type.name())) {
            return Optional.empty();
        }
        return Optional.ofNullable(SpawnSlotSerializer.read(root.getCompound(type.name())));
    }

    public static Map<SpawnSlotType, SpawnSlot> getSlots(ServerPlayer player) {
        Map<SpawnSlotType, SpawnSlot> result = new HashMap<>();
        for (SpawnSlotType type : SpawnSlotType.values()) {
            getSlot(player, type).ifPresent(slot -> result.put(type, slot));
        }
        return result;
    }

    public static void setSlot(ServerPlayer player, SpawnSlot slot) {
        root(player).put(slot.type().name(), SpawnSlotSerializer.write(slot));
    }

    public static void clearSlot(ServerPlayer player, SpawnSlotType type) {
        root(player).remove(type.name());
    }

    /** 玩家是否有任何 Layer 2/3 数据（用于判断是否需要回退到维度默认出生点）。 */
    public static boolean hasAnySpawn(ServerPlayer player) {
        CompoundTag root = root(player);
        if (root.contains(KEY_DIM_SPAWNS) && root.getList(KEY_DIM_SPAWNS, Tag.TAG_COMPOUND).size() > 0) {
            return true;
        }
        for (SpawnSlotType type : SpawnSlotType.values()) {
            if (root.contains(type.name())) {
                return true;
            }
        }
        return false;
    }
}
