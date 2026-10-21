package com.proto781m.betterspawn.logic;

import com.proto781m.betterspawn.api.ResolveResult;
import com.proto781m.betterspawn.data.WorldSpawnData;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerAboutToStartEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;

/**
 * 维度首次加载时：若该维度还没有全局默认出生点，跑 JSON 模板解析并落库。
 *
 * <p>在服务器启动时（ServerAboutToStartEvent）预先初始化所有维度的出生点，
 * 确保玩家加入前出生点已就绪。
 */
@EventBusSubscriber(modid = com.proto781m.betterspawn.BetterSpawn.MODID, bus = EventBusSubscriber.Bus.GAME)
public final class DimensionSpawnInitializer {

    private DimensionSpawnInitializer() {
    }

    /** 供 {@code ServerAboutToStartEvent} 调用，预热模板缓存并初始化所有维度的出生点。 */
    @SubscribeEvent
    public static void onServerAboutToStart(ServerAboutToStartEvent event) {
        MinecraftServer server = event.getServer();
        initAll(server);
    }

    /** 初始化所有维度的出生点。 */
    public static void initAll(MinecraftServer server) {
        // 触发一次模板加载（如果还没加载）
        com.proto781m.betterspawn.template.SpawnTemplateManager.all();

        // 为每个维度初始化出生点
        for (ServerLevel level : server.getAllLevels()) {
            initDimension(server, level);
        }
    }

    private static void initDimension(MinecraftServer server, ServerLevel level) {
        ResourceKey<Level> dimension = level.dimension();
        WorldSpawnData data = WorldSpawnData.get(server);

        if (data.get(dimension).isEmpty()) {
            // 首次加载：跑模板解析（simulate=false，会写入世界）
            ResolveResult result = com.proto781m.betterspawn.logic.SpawnPointResolver.resolveFromTemplate(
                    server, level.dimension(), false);
            if (result.status() == ResolveResult.Status.OK && result.pos() != null) {
                data.set(dimension, result.pos(), result.angle(), (ServerLevel) server.getLevel(level.dimension()));
                com.proto781m.betterspawn.BetterSpawn.LOGGER.info("Initialized default spawn for {} at {} (method: {})",
                        level.dimension().location(), result.pos(), result.usedMethod());
            } else {
                // 完全失败：运行平台方法生成平台并设置出生点
                com.proto781m.betterspawn.BetterSpawn.LOGGER.warn("Spawn template failed for {}, running platform fallback", level.dimension().location());
                var platformResult = com.proto781m.betterspawn.method.PlatformSpawnMethod.INSTANCE.resolve(
                        new com.proto781m.betterspawn.api.SpawnContext(
                                (ServerLevel) server.getLevel(level.dimension()),
                                com.proto781m.betterspawn.template.SpawnTemplateManager.get(level.dimension().location()).orElse(null),
                                level.getSeed(),
                                false,
                                System.currentTimeMillis() + 60000,
                                new com.proto781m.betterspawn.api.SearchTrace()
                        ));
                if (platformResult.status() == ResolveResult.Status.OK && platformResult.pos() != null) {
                    data.set(level.dimension(), platformResult.pos(), platformResult.angle(), (ServerLevel) server.getLevel(level.dimension()));
                    server.overworld().getDataStorage().save();
                    com.proto781m.betterspawn.BetterSpawn.LOGGER.info("Initialized platform fallback spawn for {} at {}", level.dimension().location(), platformResult.pos());
                } else {
                    // 完全失败：用原版默认行为
                    BlockPos fallback = new BlockPos(0, 64, 0);
                    WorldSpawnData.get(server).set(level.dimension(), fallback, 0.0F, (ServerLevel) server.getLevel(level.dimension()));
                    com.proto781m.betterspawn.BetterSpawn.LOGGER.warn("Platform fallback also failed for {}, using vanilla fallback at {}", level.dimension().location(), fallback);
                }
            }
        }
    }
}
