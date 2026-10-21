package com.proto781m.betterspawn.data;

import java.util.HashMap;
import java.util.List;
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
import com.proto781m.betterspawn.util.DimensionUtil;
import com.proto781m.betterspawn.util.SpawnAdapter;

/**
 * Layer 1 —— 维度默认出生点。
 *
 * <p>每个维度一个全局默认出生点，存于 {@code SavedData}（key = {@code betterspawn_world_spawn}）。
 * 主世界的出生点会同步写回原版 {@code level.dat} 的 {@code SpawnX/Y/Z/Angle}，其他维度只存这里。
 *
 * <p>获取方式：{@link #get(MinecraftServer)}，服务器启动时加载一次。
 */
public class WorldSpawnData extends SavedData {

    public static final String DATA_NAME = "betterspawn_world_spawn";

    private final Map<ResourceKey<Level>, SpawnPoint> spawns = new HashMap<>();

    public static WorldSpawnData get(MinecraftServer server) {
        return server.overworld()
                .getDataStorage()
                .computeIfAbsent(new SavedData.Factory<>(WorldSpawnData::new, WorldSpawnData::load), DATA_NAME);
    }

    @SuppressWarnings("unchecked")
    public static WorldSpawnData load(CompoundTag tag, HolderLookup.Provider registries) {
        WorldSpawnData data = new WorldSpawnData();
        ListTag list = tag.getList("spawns", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag entry = list.getCompound(i);
            ResourceKey<Level> dim = DimensionUtil.parse(entry.getString("dim"));
            BlockPos pos = new BlockPos(entry.getInt("x"), entry.getInt("y"), entry.getInt("z"));
            data.spawns.put(dim, new SpawnPoint(pos, entry.getFloat("angle")));
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
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
        spawns.put(dimension, new SpawnPoint(pos, angle));
        if (dimension.equals(Level.OVERWORLD)) {
            // 同步回原版 level.dat
            SpawnAdapter.setLevelSpawn(level, pos, angle);
        }
        setDirty();
    }

    public void clear(ResourceKey<Level> dimension) {
        if (spawns.remove(dimension) != null) {
            setDirty();
        }
    }
}
