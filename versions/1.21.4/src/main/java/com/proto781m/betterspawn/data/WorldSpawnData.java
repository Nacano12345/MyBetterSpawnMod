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
 * Layer 1: per-dimension default spawn points.
 *
 * <p>Each dimension has one global default spawn stored in {@code SavedData}
 * (key {@code betterspawn_world_spawn}). The Overworld spawn is also written back to
 * the vanilla {@code level.dat} {@code SpawnX/Y/Z/Angle} fields; other dimensions
 * only use this data.
 *
 * <p>{@code version} supports save migration. Older versions could store a failed
 * platform fallback; the initializer re-resolves spawns when the version does not match.
 */
public class WorldSpawnData extends SavedData {

    public static final String DATA_NAME = "betterspawn_world_spawn";

    /**
     * Data version.
     * <ul>
     *   <li>0 / missing: early version that may contain an incorrect platform fallback;</li>
     *   <li>1: version with the fixed search and dimension handling.</li>
     * </ul>
     */
    public static final int CURRENT_VERSION = 1;

    private final Map<ResourceKey<Level>, SpawnPoint> spawns = new HashMap<>();
    private int version = CURRENT_VERSION;
    private ResourceKey<Level> spawnDimension = Level.OVERWORLD;

    public static WorldSpawnData get(MinecraftServer server) {
        return server.overworld()
                .getDataStorage()
                .computeIfAbsent(new SavedData.Factory<>(WorldSpawnData::new, WorldSpawnData::load), DATA_NAME);
    }

    public static WorldSpawnData load(CompoundTag tag, HolderLookup.Provider registries) {
        WorldSpawnData data = new WorldSpawnData();
        data.version = tag.contains("version") ? tag.getInt("version") : 0;
        if (tag.contains("spawn_dimension")) {
            try {
                data.spawnDimension = DimensionUtil.parse(tag.getString("spawn_dimension"));
            } catch (Exception ignored) {
                data.spawnDimension = Level.OVERWORLD;
            }
        }

        ListTag list = tag.getList("spawns", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag entry = list.getCompound(i);
            try {
                ResourceKey<Level> dimension = DimensionUtil.parse(entry.getString("dim"));
                BlockPos pos = new BlockPos(entry.getInt("x"), entry.getInt("y"), entry.getInt("z"));
                // The dimension must be stored; older code defaulted to the Overworld and broke cross-dimension respawns.
                data.spawns.put(dimension, new SpawnPoint(pos, dimension, entry.getFloat("angle")));
            } catch (Exception ignored) {
                // Skip corrupted entries so the entire SavedData load does not fail.
            }
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putInt("version", version);
        tag.putString("spawn_dimension", DimensionUtil.id(spawnDimension));
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

    /** Whether an old save needs its spawn points re-resolved. */
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

    /** Dimension to teleport new players to on first join; defaults to the Overworld. */
    public ResourceKey<Level> spawnDimension() {
        return spawnDimension;
    }

    public void setSpawnDimension(ResourceKey<Level> dimension) {
        spawnDimension = dimension == null ? Level.OVERWORLD : dimension;
        setDirty();
    }
}
