package com.proto781m.betterspawn.data;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.proto781m.betterspawn.BetterSpawn;
import com.proto781m.betterspawn.util.DimensionUtil;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.Util;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.LevelResource;

/**
 * Direct access to BetterSpawn data inside an offline player's {@code playerdata/<uuid>.dat}.
 *
 * <p>The player-data file is read and written as a whole. Callers must reject players who are
 * currently online so live player data cannot be overwritten.
 */
public final class OfflinePlayerData {

    private static final String NEOFORGE_DATA = "NeoForgeData";
    private static final String PLAYER_PERSISTED = "PlayerPersisted";

    private final MinecraftServer server;
    private final OfflinePlayerRef ref;
    private final CompoundTag playerData;
    private final CompoundTag neoForgeData;
    private final CompoundTag persistedData;
    private final CompoundTag root;

    private OfflinePlayerData(MinecraftServer server, OfflinePlayerRef ref, CompoundTag playerData,
                              CompoundTag neoForgeData, CompoundTag persistedData, CompoundTag root) {
        this.server = server;
        this.ref = ref;
        this.playerData = playerData;
        this.neoForgeData = neoForgeData;
        this.persistedData = persistedData;
        this.root = root;
    }

    public OfflinePlayerRef ref() {
        return ref;
    }

    public static Optional<OfflinePlayerData> load(MinecraftServer server, OfflinePlayerRef ref) {
        Path path = playerPath(server, ref.id());
        if (!Files.isRegularFile(path)) {
            return Optional.empty();
        }
        try {
            CompoundTag data = NbtIo.readCompressed(path, NbtAccounter.unlimitedHeap());
            CompoundTag neo = data.contains(NEOFORGE_DATA)
                    ? data.getCompound(NEOFORGE_DATA)
                    : new CompoundTag();
            CompoundTag persisted = neo.contains(PLAYER_PERSISTED)
                    ? neo.getCompound(PLAYER_PERSISTED)
                    : new CompoundTag();
            CompoundTag root = persisted.contains(PlayerSpawnData.ROOT)
                    ? persisted.getCompound(PlayerSpawnData.ROOT)
                    : new CompoundTag();
            return Optional.of(new OfflinePlayerData(server, ref, data, neo, persisted, root));
        } catch (Exception ex) {
            BetterSpawn.LOGGER.warn("Failed to read offline player data for {}: {}", ref.displayName(), ex.toString());
            return Optional.empty();
        }
    }

    public static List<OfflinePlayerRef> list(MinecraftServer server) {
        Path directory = server.getWorldPath(LevelResource.PLAYER_DATA_DIR);
        if (!Files.isDirectory(directory)) {
            return List.of();
        }
        Map<UUID, String> names = readUserCache(server);
        List<OfflinePlayerRef> result = new ArrayList<>();
        try (Stream<Path> files = Files.list(directory)) {
            files.filter(path -> path.getFileName().toString().endsWith(".dat"))
                    .forEach(path -> {
                        String fileName = path.getFileName().toString();
                        String uuidText = fileName.substring(0, fileName.length() - 4);
                        UUID id;
                        try {
                            id = UUID.fromString(uuidText);
                        } catch (IllegalArgumentException ex) {
                            return;
                        }
                        try {
                            CompoundTag data = NbtIo.readCompressed(path, NbtAccounter.unlimitedHeap());
                            CompoundTag neo = data.contains(NEOFORGE_DATA)
                                    ? data.getCompound(NEOFORGE_DATA)
                                    : new CompoundTag();
                            CompoundTag persisted = neo.contains(PLAYER_PERSISTED)
                                    ? neo.getCompound(PLAYER_PERSISTED)
                                    : new CompoundTag();
                            if (persisted.contains(PlayerSpawnData.ROOT)) {
                                result.add(new OfflinePlayerRef(id, names.getOrDefault(id, uuidText)));
                            }
                        } catch (Exception ignored) {
                            // Ignore unreadable player-data files.
                        }
                    });
        } catch (IOException ex) {
            BetterSpawn.LOGGER.warn("Failed to list offline player data: {}", ex.toString());
        }
        result.sort(java.util.Comparator.comparing(OfflinePlayerRef::displayName,
                String.CASE_INSENSITIVE_ORDER));
        return result;
    }

    public static Optional<OfflinePlayerRef> resolve(MinecraftServer server, String token) {
        if (token == null || token.isBlank()) {
            return Optional.empty();
        }
        String value = token.trim();
        Map<UUID, String> names = readUserCache(server);
        try {
            UUID id = UUID.fromString(value);
            if (Files.isRegularFile(playerPath(server, id))) {
                return Optional.of(new OfflinePlayerRef(id, names.getOrDefault(id, id.toString())));
            }
        } catch (IllegalArgumentException ignored) {
            // Not a UUID; resolve as a name below.
        }

        for (Map.Entry<UUID, String> entry : names.entrySet()) {
            if (entry.getValue().equalsIgnoreCase(value)
                    && Files.isRegularFile(playerPath(server, entry.getKey()))) {
                return Optional.of(new OfflinePlayerRef(entry.getKey(), entry.getValue()));
            }
        }

        for (OfflinePlayerRef ref : list(server)) {
            if (ref.name().equalsIgnoreCase(value) || ref.id().toString().equalsIgnoreCase(value)) {
                return Optional.of(ref);
            }
        }
        return Optional.empty();
    }

    public static boolean isOnline(MinecraftServer server, OfflinePlayerRef ref) {
        return server.getPlayerList().getPlayer(ref.id()) != null;
    }

    public Map<ResourceKey<Level>, SpawnPoint> dimensionSpawns() {
        Map<ResourceKey<Level>, SpawnPoint> result = new HashMap<>();
        ListTag list = root.getList(PlayerSpawnData.KEY_DIM_SPAWNS, Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag tag = list.getCompound(i);
            try {
                ResourceKey<Level> dimension = DimensionUtil.parse(tag.getString("dim"));
                BlockPos pos = new BlockPos(tag.getInt("x"), tag.getInt("y"), tag.getInt("z"));
                result.put(dimension, new SpawnPoint(pos, dimension, tag.getFloat("angle")));
            } catch (Exception ignored) {
                // Skip malformed entries.
            }
        }
        return result;
    }

    public void setDimensionSpawn(ResourceKey<Level> dimension, BlockPos pos, float angle) {
        ListTag list = root.getList(PlayerSpawnData.KEY_DIM_SPAWNS, Tag.TAG_COMPOUND);
        ListTag updated = new ListTag();
        String id = DimensionUtil.id(dimension);
        boolean found = false;
        for (int i = 0; i < list.size(); i++) {
            CompoundTag tag = list.getCompound(i);
            if (tag.getString("dim").equals(id)) {
                found = true;
                updated.add(writeDimensionSpawn(dimension, pos, angle));
            } else {
                updated.add(tag);
            }
        }
        if (!found) {
            updated.add(writeDimensionSpawn(dimension, pos, angle));
        }
        root.put(PlayerSpawnData.KEY_DIM_SPAWNS, updated);
    }

    public void clearDimensionSpawn(ResourceKey<Level> dimension) {
        ListTag list = root.getList(PlayerSpawnData.KEY_DIM_SPAWNS, Tag.TAG_COMPOUND);
        ListTag updated = new ListTag();
        String id = DimensionUtil.id(dimension);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag tag = list.getCompound(i);
            if (!tag.getString("dim").equals(id)) {
                updated.add(tag);
            }
        }
        root.put(PlayerSpawnData.KEY_DIM_SPAWNS, updated);
    }

    public Optional<SpawnSlot> slot(SpawnSlotType type) {
        if (!root.contains(type.name())) {
            return Optional.empty();
        }
        return Optional.ofNullable(SpawnSlotSerializer.read(root.getCompound(type.name())));
    }

    public Map<SpawnSlotType, SpawnSlot> slots() {
        Map<SpawnSlotType, SpawnSlot> result = new HashMap<>();
        for (SpawnSlotType type : SpawnSlotType.values()) {
            slot(type).ifPresent(slot -> result.put(type, slot));
        }
        return result;
    }

    public void setSlot(SpawnSlot slot) {
        root.put(slot.type().name(), SpawnSlotSerializer.write(slot));
    }

    public void clearSlot(SpawnSlotType type) {
        root.remove(type.name());
    }

    public void save() throws IOException {
        neoForgeData.put(PLAYER_PERSISTED, persistedData);
        persistedData.put(PlayerSpawnData.ROOT, root);
        playerData.put(NEOFORGE_DATA, neoForgeData);

        Path directory = server.getWorldPath(LevelResource.PLAYER_DATA_DIR);
        Path target = directory.resolve(ref.id() + ".dat");
        Path temporary = Files.createTempFile(directory, ref.id() + "-", ".dat");
        NbtIo.writeCompressed(playerData, temporary);
        Util.safeReplaceFile(target, temporary, directory.resolve(ref.id() + ".dat_old"));
    }

    private static CompoundTag writeDimensionSpawn(ResourceKey<Level> dimension, BlockPos pos, float angle) {
        CompoundTag tag = new CompoundTag();
        tag.putString("dim", DimensionUtil.id(dimension));
        tag.putInt("x", pos.getX());
        tag.putInt("y", pos.getY());
        tag.putInt("z", pos.getZ());
        tag.putFloat("angle", angle);
        return tag;
    }

    private static Path playerPath(MinecraftServer server, UUID id) {
        return server.getWorldPath(LevelResource.PLAYER_DATA_DIR).resolve(id + ".dat");
    }

    private static Map<UUID, String> readUserCache(MinecraftServer server) {
        Map<UUID, String> result = new HashMap<>();
        Path file = server.getServerDirectory().resolve("usercache.json");
        if (!Files.isRegularFile(file)) {
            return result;
        }
        try {
            JsonElement parsed = JsonParser.parseString(Files.readString(file));
            if (!parsed.isJsonArray()) {
                return result;
            }
            JsonArray array = parsed.getAsJsonArray();
            for (JsonElement element : array) {
                if (!element.isJsonObject()) {
                    continue;
                }
                JsonObject object = element.getAsJsonObject();
                if (!object.has("uuid") || !object.has("name")) {
                    continue;
                }
                try {
                    result.put(UUID.fromString(object.get("uuid").getAsString()),
                            object.get("name").getAsString());
                } catch (IllegalArgumentException ignored) {
                    // Skip malformed cache entries.
                }
            }
        } catch (Exception ex) {
            BetterSpawn.LOGGER.warn("Failed to read usercache.json: {}", ex.toString());
        }
        return result;
    }
}
