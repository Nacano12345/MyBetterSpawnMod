package com.proto781m.betterspawn.logic;

import com.proto781m.betterspawn.BetterSpawn;
import com.proto781m.betterspawn.api.ResolveResult;
import com.proto781m.betterspawn.api.SearchTrace;
import com.proto781m.betterspawn.api.SpawnContext;
import com.proto781m.betterspawn.config.BetterSpawnConfig;
import com.proto781m.betterspawn.data.SpawnPoint;
import com.proto781m.betterspawn.data.WorldSpawnData;
import com.proto781m.betterspawn.method.PlatformSpawnMethod;
import com.proto781m.betterspawn.template.SpawnTemplateManager;
import com.proto781m.betterspawn.logic.CreateWorldSpawnSelection;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.LevelData;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;

/**
 * Initializes per-dimension default spawn points.
 *
 * <p>Timing is critical:
 * <ul>
 *   <li>{@link LevelEvent.CreateSpawnPosition}: fires when a new world is created and
 *       before vanilla chooses a spawn. Resolve the template and cancel vanilla logic
 *       so the result can be written to {@code level.dat} and {@code prepareLevels}
 *       loads spawn chunks around the correct position.</li>
 *   <li>{@link ServerStartedEvent}: all dimensions exist, spawn chunks are ready, and
 *       no players have joined. Fill in missing spawns for old saves or other dimensions.</li>
 * </ul>
 *
 * <p><b>Do not</b> search during {@code LevelEvent.Load} or
 * {@code ServerAboutToStartEvent}: chunks are not loaded yet and {@code getHeight}
 * returns {@code minBuildHeight}, making the world look empty.
 */
@EventBusSubscriber(modid = BetterSpawn.MODID)
public final class DimensionSpawnInitializer {

    private DimensionSpawnInitializer() {
    }

    @SubscribeEvent
    public static void onCreateSpawnPosition(LevelEvent.CreateSpawnPosition event) {
        if (!(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        if (!level.dimension().equals(Level.OVERWORLD)) {
            return;
        }

        MinecraftServer server = level.getServer();
        ResolveResult result = SpawnPointResolver.resolveFromTemplate(server, Level.OVERWORLD, false);
        if (result.pos() == null) {
            BetterSpawn.LOGGER.warn("Initial overworld spawn resolution failed (status={}); deferring to vanilla: {}",
                    result.status(), result.warnings());
            return;
        }

        WorldSpawnData data = WorldSpawnData.get(server);
        data.set(Level.OVERWORLD, result.pos(), result.angle(), level);

        ResourceKey<Level> selected = BetterSpawnConfig.ENABLE_SPAWN_DIMENSION_SELECTOR.get()
                ? CreateWorldSpawnSelection.get()
                : Level.OVERWORLD;
        data.setSpawnDimension(selected);

        event.getSettings().setSpawn(LevelData.RespawnData.of(Level.OVERWORLD, result.pos(), result.angle(), 0.0F));
        event.setCanceled(true);

        BetterSpawn.LOGGER.info("Created overworld spawn at {} using '{}' (status={})",
                result.pos(), result.usedMethod(), result.status());
        BetterSpawn.LOGGER.info("New world spawn dimension: {}", selected.identifier());
    }

    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        initAll(event.getServer());
    }

    /** Fills in missing default spawns for all dimensions and re-resolves old save data
     * when its version does not match. */
    public static void initAll(MinecraftServer server) {
        SpawnTemplateManager.ensureLoaded();
        WorldSpawnData data = WorldSpawnData.get(server);

        boolean migrating = data.needsMigration();
        boolean preserveOverworld = false;

        if (migrating) {
            // Older versions may have stored an incorrect platform point. If the Overworld level.dat spawn differs from the stored value,
            // an admin used /setworldspawn later; preserve the level.dat choice during migration.
            Optional<SpawnPoint> legacy = data.get(Level.OVERWORLD);
            if (legacy.isPresent()) {
                BlockPos vanilla = server.overworld().getLevelData().getRespawnData().pos();
                preserveOverworld = !legacy.get().pos().equals(vanilla);
            }

            BetterSpawn.LOGGER.info("World spawn data version {} -> {}: regenerating dimension spawns",
                    data.version(), WorldSpawnData.CURRENT_VERSION);
            data.clearAll();
            data.markMigrated();
        }

        for (ServerLevel level : server.getAllLevels()) {
            initDimension(server, level, data, migrating, preserveOverworld);
        }
        if (data.spawnDimension() != null && server.getLevel(data.spawnDimension()) == null) {
            BetterSpawn.LOGGER.warn("Spawn dimension {} is not loaded; falling back to overworld",
                    data.spawnDimension().identifier());
            data.setSpawnDimension(Level.OVERWORLD);
        }
        data.setDirty();
    }

    private static void initDimension(MinecraftServer server, ServerLevel level,
                                      WorldSpawnData data, boolean migrating,
                                      boolean preserveOverworld) {
        ResourceKey<Level> dimension = level.dimension();
        Optional<SpawnPoint> stored = data.get(dimension);

        if (stored.isEmpty()) {
            if (dimension.equals(Level.OVERWORLD) && preserveOverworld) {
                BlockPos vanilla = level.getLevelData().getRespawnData().pos();
                float vanillaAngle = level.getLevelData().getRespawnData().yaw();
                data.set(dimension, vanilla, vanillaAngle, level);
                BetterSpawn.LOGGER.info("Preserved overworld spawn from level.dat during migration: {}",
                        vanilla);
                return;
            }

            ResolveResult result = SpawnPointResolver.resolveFromTemplate(server, dimension, false);
            if (result.pos() != null) {
                data.set(dimension, result.pos(), result.angle(), level);
                BetterSpawn.LOGGER.info("Initialized spawn for {} at {} using '{}' (status={})",
                        dimension.identifier(), result.pos(), result.usedMethod(), result.status());
                return;
            }

            BetterSpawn.LOGGER.warn("Spawn template failed for {} (status={}), running platform fallback",
                    dimension.identifier(), result.status());
            ResolveResult platform = PlatformSpawnMethod.INSTANCE.resolve(platformContext(level));
            if (platform.pos() != null) {
                data.set(dimension, platform.pos(), platform.angle(), level);
                BetterSpawn.LOGGER.info("Initialized platform fallback for {} at {}",
                        dimension.identifier(), platform.pos());
            } else {
                BetterSpawn.LOGGER.error("Platform fallback also failed for {}: {}",
                        dimension.identifier(), platform.warnings());
            }
            return;
        }

        // Migration already cleared old data, so no sync is needed; on normal startup, let /setworldspawn affect the Overworld.
        if (!migrating && dimension.equals(Level.OVERWORLD)) {
            BlockPos vanillaPos = level.getLevelData().getRespawnData().pos();
            float vanillaAngle = level.getLevelData().getRespawnData().yaw();
            SpawnPoint point = stored.get();
            if (!point.pos().equals(vanillaPos) || point.angle() != vanillaAngle) {
                data.set(dimension, vanillaPos, vanillaAngle, level);
                BetterSpawn.LOGGER.info("Synced overworld spawn from level.dat: {}", vanillaPos);
            }
        }
    }

    /**
     * Re-resolves the default spawn for one dimension
     * (used by {@code /betterspawn worldspawn regenerate}).
     */
    public static boolean regenerate(MinecraftServer server, ResourceKey<Level> dimension) {
        ServerLevel level = server.getLevel(dimension);
        if (level == null) {
            return false;
        }

        WorldSpawnData data = WorldSpawnData.get(server);
        data.clear(dimension);

        ResolveResult result = SpawnPointResolver.resolveFromTemplate(server, dimension, false);
        if (result.pos() == null) {
            result = PlatformSpawnMethod.INSTANCE.resolve(platformContext(level));
        }
        if (result.pos() == null) {
            return false;
        }

        data.set(dimension, result.pos(), result.angle(), level);
        data.setDirty();
        BetterSpawn.LOGGER.info("Regenerated spawn for {} at {} using '{}' (status={})",
                dimension.identifier(), result.pos(), result.usedMethod(), result.status());
        return true;
    }

    private static SpawnContext platformContext(ServerLevel level) {
        long timeout = Math.max(1, BetterSpawnConfig.SEARCH_TIMEOUT_TICKS.get()) * 50L;
        return new SpawnContext(level, null, level.getSeed(), false,
                System.currentTimeMillis() + timeout, new SearchTrace());
    }
}
