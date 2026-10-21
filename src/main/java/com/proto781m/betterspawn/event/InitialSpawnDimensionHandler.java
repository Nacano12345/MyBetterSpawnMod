package com.proto781m.betterspawn.event;

import com.proto781m.betterspawn.BetterSpawn;
import com.proto781m.betterspawn.config.BetterSpawnConfig;
import com.proto781m.betterspawn.data.PlayerSpawnData;
import com.proto781m.betterspawn.data.SpawnPoint;
import com.proto781m.betterspawn.data.WorldSpawnData;
import com.proto781m.betterspawn.util.TeleportHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stats;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

/**
 * 实验性“出生维度”功能：新玩家第一次进入世界时，传送到创建世界时选择的维度。
 *
 * <p>原版新玩家总是在主世界登录，因此这里在登录后做一次跨维度传送，并用
 * {@link PlayerSpawnData} 中的标记保证只执行一次。只对单人/局域网主机有效，
 * 因为选择来自创建世界界面。
 */
@EventBusSubscriber(modid = BetterSpawn.MODID, bus = EventBusSubscriber.Bus.GAME)
public final class InitialSpawnDimensionHandler {

    private InitialSpawnDimensionHandler() {
    }

    @SubscribeEvent
    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (!BetterSpawnConfig.ENABLE_SPAWN_DIMENSION_SELECTOR.get()) {
            return;
        }
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        MinecraftServer server = player.getServer();
        if (server == null) {
            return;
        }

        WorldSpawnData data = WorldSpawnData.get(server);
        ResourceKey<Level> dimension = data.spawnDimension();
        if (dimension == null || dimension.equals(Level.OVERWORLD)) {
            return;
        }
        if (PlayerSpawnData.hasInitialDimensionApplied(player)) {
            return;
        }

        // 只有真正的新玩家才传送；已有玩家保留自己的位置
        int playTime = player.getStats().getValue(Stats.CUSTOM, Stats.PLAY_TIME);
        if (playTime > 0) {
            return;
        }

        ServerLevel target = server.getLevel(dimension);
        if (target == null) {
            BetterSpawn.LOGGER.warn("Spawn dimension {} is not loaded; keeping overworld",
                    dimension.location());
            return;
        }
        SpawnPoint point = data.get(dimension).orElse(null);
        if (point == null) {
            BetterSpawn.LOGGER.warn("No default spawn stored for {}; keeping overworld",
                    dimension.location());
            return;
        }

        PlayerSpawnData.setInitialDimensionApplied(player);

        // 等一 tick，确保玩家已经完全加入世界再跨维度传送
        server.execute(() -> {
            if (player.hasDisconnected() || player.isRemoved() || !player.isAlive()) {
                return;
            }
            TeleportHelper.teleport(player, target,
                    point.pos().getX() + 0.5, point.pos().getY(), point.pos().getZ() + 0.5,
                    point.angle(), player.getXRot());
            player.sendSystemMessage(Component.translatable(
                    "betterspawn.msg.initial_dimension", dimension.location().toString()));
            BetterSpawn.diag("Initial spawn dimension: teleported {} to {} at {}",
                    player.getScoreboardName(), dimension.location(), point.pos());
        });
    }
}
