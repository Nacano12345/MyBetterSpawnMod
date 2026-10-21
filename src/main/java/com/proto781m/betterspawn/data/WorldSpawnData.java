package com.proto781m.betterspawn.data;

import com.proto781m.betterspawn.util.DimensionUtil;
import com.proto781m.betterspawn.util.SpawnAdapter;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * Layer 1 —— 维度默认出生点。
 *
 * <p>每个维度一个全局默认出生点，存于 {@code SavedData}（key = {@code betterspawn_world_spawn}）。
 * 主世界的出生点会同步写回原版 {@code level.dat} 的 {@code SpawnX/Y/Z/Angle}，其他维度只存这里。
 *
 * <p>{@code version} 用于升级旧存档：旧版本曾经把失败回退的平台点写进存档，
 * 版本号不匹配时由初始化器重新解析。
 */
public class WorldSpawnData extends SavedData {

    public static final String DATA_NAME = "betterspawn_world_spawn";

    /**
     * 数据版本。
     * <ul>
     *   <li>0 / 缺失：早期版本，可能是错误的平台回退点；</li>
     *   <li>1：修复搜索与维度字段后的版本。</li>
     * </ul>
     */
    public static final int CURRENT_VERSION = 1;

    private final Map<ResourceKey<Level>, SpawnPoint> spawns = new HashMap<>();
    private int version = CURRENT_VERSION;

    public static WorldSpawnData get(MinecraftServer server) {
        return server.overworld()
                .getDataStorage()
                .computeIfAbsent(new SavedData.Factory<>(WorldSpawnData::new, WorldSpawnData::load), DATA_NAME);
    }

    public static WorldSpawnData load(CompoundTag tag, HolderLookup.Provider registries) {
        WorldSpawnData data = new WorldSpawnData();
        data.version = tag.contains("version") ? tag.getInt("version") : 0;

        ListTag list = tag.getList("spawns", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag entry = list.getCompound(i);
            try {
                ResourceKey<Level> dimension = DimensionUtil.parse(entry.getString("dim"));
                BlockPos pos = new BlockPos(entry.getInt("x"), entry.getInt("y"), entry.getInt("z"));
                // 必须带上维度，旧代码这里默认成了主世界，导致跨维度重生错乱
                data.spawns.put(dimension, new SpawnPoint(pos, dimension, entry.getFloat("angle")));
            } catch (Exception ignored) {
                // 跳过损坏条目，避免整个 SavedData 加载失败
            }
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putInt("version", version);
        ListTag list = new ListTag();
        for (Map.Entry<ResourceKey<Level>, SpawnPoint> entry : spawns.entrySet()) {
            CompoundTag t = new CompoundTag();
            t.putString("dim", DimensionUtil.id(entry.getKey()));
            SpawnPoint point = entry.getValue();
            t.putInt("x", point.pos().getX());
            t.putInt("y", point.pos().getY());
            t.putInt("z", point.pos().getZ());
            t.putFloat("angle", point.angle());
            list.add(t);
        }
        tag.put("spawns", list);
        return tag;
    }

    public Optional<SpawnPoint> get(ResourceKey<Level> dimension) {
        return Optional.ofNullable(spawns.get(dimension));
    }

    public Map<ResourceKey<Level>, SpawnPoint> all() {
        return Map.copyOf(spawns);
    }

    public void set(ResourceKey<Level> dimension, BlockPos pos, float angle, ServerLevel level) {
        spawns.put(dimension, new SpawnPoint(pos, dimension, angle));
        if (dimension.equals(Level.OVERWORLD) && level != null) {
            SpawnAdapter.setLevelSpawn(level, pos, angle);
        }
        setDirty();
    }

    public void clear(ResourceKey<Level> dimension) {
        if (spawns.remove(dimension) != null) {
            setDirty();
        }
    }

    public void clearAll() {
        if (!spawns.isEmpty()) {
            spawns.clear();
            setDirty();
        }
    }

    /** 旧存档需要重新解析出生点。 */
    public boolean needsMigration() {
        return version < CURRENT_VERSION;
    }

    public void markMigrated() {
        if (version != CURRENT_VERSION) {
            version = CURRENT_VERSION;
            setDirty();
        }
    }

    public int version() {
        return version;
    }
}
