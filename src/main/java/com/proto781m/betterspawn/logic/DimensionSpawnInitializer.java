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
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;

/**
 * 维度默认出生点初始化。
 *
 * <p>时机非常关键（早期版本就是在这里踩坑的）：
 * <ul>
 *   <li>{@link LevelEvent.CreateSpawnPosition}：新世界创建时、原版挑选出生点之前触发。
 *       这里解析并取消原版逻辑，可以把模板结果直接写进 {@code level.dat}，
 *       同时让 {@code prepareLevels} 在正确的出生点周围加载出生区块。</li>
 *   <li>{@link ServerStartedEvent}：所有维度已创建、出生区块已准备、玩家尚未登录时触发。
 *       这里补齐旧存档/其他维度缺失的默认出生点。</li>
 * </ul>
 *
 * <p><b>不要</b>在 {@code LevelEvent.Load} 或 {@code ServerAboutToStartEvent} 里搜索：
 * 此时区块还没加载，{@code getHeight} 会返回 {@code minBuildHeight}，看起来像虚空世界。
 */
@EventBusSubscriber(modid = BetterSpawn.MODID, bus = EventBusSubscriber.Bus.GAME)
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

        event.getSettings().setSpawn(result.pos(), result.angle());
        event.setCanceled(true);

        BetterSpawn.LOGGER.info("Created overworld spawn at {} using '{}' (status={})",
                result.pos(), result.usedMethod(), result.status());
        BetterSpawn.LOGGER.info("New world spawn dimension: {}", selected.location());
    }

    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        initAll(event.getServer());
    }

    /** 补齐所有维度缺失的默认出生点；旧存档数据版本不匹配时重新解析。 */
    public static void initAll(MinecraftServer server) {
        SpawnTemplateManager.ensureLoaded();
        WorldSpawnData data = WorldSpawnData.get(server);

        boolean migrating = data.needsMigration();
        boolean preserveOverworld = false;

        if (migrating) {
            // 旧版本可能把错误的平台点写进数据。若主世界 level.dat 的出生点与旧数据不同，
            // 说明管理员后来用过 /setworldspawn，迁移时保留 level.dat 的选择。
            Optional<SpawnPoint> legacy = data.get(Level.OVERWORLD);
            if (legacy.isPresent()) {
                BlockPos vanilla = server.overworld().getLevelData().getSpawnPos();
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
                    data.spawnDimension().location());
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
                BlockPos vanilla = level.getLevelData().getSpawnPos();
                float vanillaAngle = level.getLevelData().getSpawnAngle();
                data.set(dimension, vanilla, vanillaAngle, level);
                BetterSpawn.LOGGER.info("Preserved overworld spawn from level.dat during migration: {}",
                        vanilla);
                return;
            }

            ResolveResult result = SpawnPointResolver.resolveFromTemplate(server, dimension, false);
            if (result.pos() != null) {
                data.set(dimension, result.pos(), result.angle(), level);
                BetterSpawn.LOGGER.info("Initialized spawn for {} at {} using '{}' (status={})",
                        dimension.location(), result.pos(), result.usedMethod(), result.status());
                return;
            }

            BetterSpawn.LOGGER.warn("Spawn template failed for {} (status={}), running platform fallback",
                    dimension.location(), result.status());
            ResolveResult platform = PlatformSpawnMethod.INSTANCE.resolve(platformContext(level));
            if (platform.pos() != null) {
                data.set(dimension, platform.pos(), platform.angle(), level);
                BetterSpawn.LOGGER.info("Initialized platform fallback for {} at {}",
                        dimension.location(), platform.pos());
            } else {
                BetterSpawn.LOGGER.error("Platform fallback also failed for {}: {}",
                        dimension.location(), platform.warnings());
            }
            return;
        }

        // 旧存档迁移时已经清空，不需要同步；正常启动时让 /setworldspawn 对主世界生效
        if (!migrating && dimension.equals(Level.OVERWORLD)) {
            BlockPos vanillaPos = level.getLevelData().getSpawnPos();
            float vanillaAngle = level.getLevelData().getSpawnAngle();
            SpawnPoint point = stored.get();
            if (!point.pos().equals(vanillaPos) || point.angle() != vanillaAngle) {
                data.set(dimension, vanillaPos, vanillaAngle, level);
                BetterSpawn.LOGGER.info("Synced overworld spawn from level.dat: {}", vanillaPos);
            }
        }
    }

    /**
     * 重新解析某个维度的默认出生点（命令 {@code /betterspawn worldspawn regenerate} 使用）。
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
                dimension.location(), result.pos(), result.usedMethod(), result.status());
        return true;
    }

    private static SpawnContext platformContext(ServerLevel level) {
        long timeout = Math.max(1, BetterSpawnConfig.SEARCH_TIMEOUT_TICKS.get()) * 50L;
        return new SpawnContext(level, null, level.getSeed(), false,
                System.currentTimeMillis() + timeout, new SearchTrace());
    }
}
