package com.proto781m.betterspawn.data;

import com.proto781m.betterspawn.util.DimensionUtil;
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

/**
 * Layer 2 + Layer 3 —— 玩家维度出生点 + 双槽重生点。
 *
 * <p>全部存于玩家自己的持久化 NBT（{@code player.getPersistentData()} 下的
 * {@value #ROOT} 复合标签），由 NeoForge 随玩家存档保存/读取。
 */
public final class PlayerSpawnData {

    public static final String ROOT = "BetterSpawn";
    public static final String KEY_DIM_SPAWNS = "dimensionSpawns";

    private PlayerSpawnData() {
    }

    /** 获取玩家的 BetterSpawn 根复合标签（不存在则创建并挂回玩家数据）。 */
    public static CompoundTag root(ServerPlayer player) {
        CompoundTag persistent = player.getPersistentData();
        if (!persistent.contains(ROOT, Tag.TAG_COMPOUND)) {
            persistent.put(ROOT, new CompoundTag());
        }
        return persistent.getCompound(ROOT);
    }

    private static void saveRoot(ServerPlayer player, CompoundTag root) {
        player.getPersistentData().put(ROOT, root);
    }

    // ------------------------------------------------------------------
    // Layer 2：玩家维度出生点
    // ------------------------------------------------------------------

    public static Optional<SpawnPoint> getDimensionSpawn(ServerPlayer player, ResourceKey<Level> dimension) {
        ListTag list = root(player).getList(KEY_DIM_SPAWNS, Tag.TAG_COMPOUND);
        String id = DimensionUtil.id(dimension);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag t = list.getCompound(i);
            if (t.getString("dim").equals(id)) {
                BlockPos pos = new BlockPos(t.getInt("x"), t.getInt("y"), t.getInt("z"));
                // 关键：带上是哪个维度，否则会默认成主世界
                return Optional.of(new SpawnPoint(pos, dimension, t.getFloat("angle")));
            }
        }
        return Optional.empty();
    }

    public static Map<ResourceKey<Level>, SpawnPoint> getAllDimensionSpawns(ServerPlayer player) {
        Map<ResourceKey<Level>, SpawnPoint> result = new HashMap<>();
        ListTag list = root(player).getList(KEY_DIM_SPAWNS, Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag t = list.getCompound(i);
            try {
                ResourceKey<Level> dimension = DimensionUtil.parse(t.getString("dim"));
                BlockPos pos = new BlockPos(t.getInt("x"), t.getInt("y"), t.getInt("z"));
                result.put(dimension, new SpawnPoint(pos, dimension, t.getFloat("angle")));
            } catch (Exception ignored) {
                // 跳过损坏条目
            }
        }
        return result;
    }

    public static void setDimensionSpawn(ServerPlayer player, ResourceKey<Level> dimension,
                                         BlockPos pos, float angle) {
        CompoundTag root = root(player);
        ListTag list = root.getList(KEY_DIM_SPAWNS, Tag.TAG_COMPOUND);
        ListTag updated = new ListTag();
        boolean found = false;
        String id = DimensionUtil.id(dimension);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag t = list.getCompound(i);
            if (t.getString("dim").equals(id)) {
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
        saveRoot(player, root);
    }

    public static void clearDimensionSpawn(ServerPlayer player, ResourceKey<Level> dimension) {
        CompoundTag root = root(player);
        ListTag list = root.getList(KEY_DIM_SPAWNS, Tag.TAG_COMPOUND);
        ListTag updated = new ListTag();
        String id = DimensionUtil.id(dimension);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag t = list.getCompound(i);
            if (!t.getString("dim").equals(id)) {
                updated.add(t);
            }
        }
        root.put(KEY_DIM_SPAWNS, updated);
        saveRoot(player, root);
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

    // ------------------------------------------------------------------
    // Layer 3：重生点槽
    // ------------------------------------------------------------------

    public static Optional<SpawnSlot> getSlot(ServerPlayer player, SpawnSlotType type) {
        CompoundTag root = root(player);
        if (!root.contains(type.name(), Tag.TAG_COMPOUND)) {
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
        CompoundTag root = root(player);
        root.put(slot.type().name(), SpawnSlotSerializer.write(slot));
        saveRoot(player, root);
    }

    public static void clearSlot(ServerPlayer player, SpawnSlotType type) {
        CompoundTag root = root(player);
        if (root.contains(type.name())) {
            root.remove(type.name());
            saveRoot(player, root);
        }
    }

    /** 玩家是否有任何 Layer 2/3 数据。 */
    public static boolean hasAnySpawn(ServerPlayer player) {
        CompoundTag root = root(player);
        if (root.getList(KEY_DIM_SPAWNS, Tag.TAG_COMPOUND).size() > 0) {
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
