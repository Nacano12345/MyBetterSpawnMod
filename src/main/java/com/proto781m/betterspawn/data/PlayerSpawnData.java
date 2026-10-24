package com.proto781m.betterspawn.data;

import com.proto781m.betterspawn.BetterSpawn;
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
 * Layer 2 and Layer 3: per-player dimension spawns and command/bed/anchor slots.
 *
 * <p>All data is stored in the player's persistent NBT under the {@value #ROOT}
 * compound tag, which NeoForge saves and loads with the player.
 */
public final class PlayerSpawnData {

    public static final String ROOT = "BetterSpawn";
    public static final String KEY_DIM_SPAWNS = "dimensionSpawns";
    private static final String KEY_INITIAL_DIMENSION = "initialDimensionApplied";

    private PlayerSpawnData() {
    }

    /** Returns the player's BetterSpawn root compound, creating and attaching it if absent. */
    public static CompoundTag root(ServerPlayer player) {
        CompoundTag persistent = player.getPersistentData();
        CompoundTag persisted;
        if (persistent.contains(net.minecraft.world.entity.player.Player.PERSISTED_NBT_TAG, Tag.TAG_COMPOUND)) {
            persisted = persistent.getCompound(net.minecraft.world.entity.player.Player.PERSISTED_NBT_TAG);
        } else {
            persisted = new CompoundTag();
            persistent.put(net.minecraft.world.entity.player.Player.PERSISTED_NBT_TAG, persisted);
        }

        if (!persisted.contains(ROOT, Tag.TAG_COMPOUND)) {
            persisted.put(ROOT, new CompoundTag());
        }
        return persisted.getCompound(ROOT);
    }

    /**
     * Writes the root tag back.
     *
     * <p>It must live under {@code PlayerPersisted}: {@code ServerPlayer#restoreFrom} only
     * copies that subtree to the new player after death. Data placed directly under
     * {@code NeoForgeData} would be lost on death.
     */
    private static void saveRoot(ServerPlayer player, CompoundTag root) {
        CompoundTag persistent = player.getPersistentData();
        CompoundTag persisted;
        if (persistent.contains(net.minecraft.world.entity.player.Player.PERSISTED_NBT_TAG, Tag.TAG_COMPOUND)) {
            persisted = persistent.getCompound(net.minecraft.world.entity.player.Player.PERSISTED_NBT_TAG);
        } else {
            persisted = new CompoundTag();
            persistent.put(net.minecraft.world.entity.player.Player.PERSISTED_NBT_TAG, persisted);
        }
        persisted.put(ROOT, root);
    }

    // ------------------------------------------------------------------
    // Layer 2: per-player dimension spawns
    // ------------------------------------------------------------------

    public static Optional<SpawnPoint> getDimensionSpawn(ServerPlayer player, ResourceKey<Level> dimension) {
        ListTag list = root(player).getList(KEY_DIM_SPAWNS, Tag.TAG_COMPOUND);
        String id = DimensionUtil.id(dimension);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag t = list.getCompound(i);
            if (t.getString("dim").equals(id)) {
                BlockPos pos = new BlockPos(t.getInt("x"), t.getInt("y"), t.getInt("z"));
                // Important: store the dimension; otherwise it defaults to the Overworld.
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
                // Skip corrupted entries
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
        BetterSpawn.diag("Set personal world spawn for {} in {} at {} angle={}",
                player.getScoreboardName(), dimension.location(), pos, angle);
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
    // Layer 3: respawn slots
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

    // ------------------------------------------------------------------
    // Experimental: apply the spawn dimension on first join
    // ------------------------------------------------------------------

    public static boolean hasInitialDimensionApplied(ServerPlayer player) {
        return root(player).getBoolean(KEY_INITIAL_DIMENSION);
    }

    public static void setInitialDimensionApplied(ServerPlayer player) {
        CompoundTag root = root(player);
        root.putBoolean(KEY_INITIAL_DIMENSION, true);
        saveRoot(player, root);
    }
}
