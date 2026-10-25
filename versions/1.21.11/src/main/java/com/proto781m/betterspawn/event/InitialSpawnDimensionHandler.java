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
 * Experimental "spawn dimension" feature: teleports a new player to the dimension
 * selected during world creation when they first join.
 *
 * <p>Vanilla always logs new players in at the Overworld, so this performs one
 * cross-dimension teleport after login and uses a flag in {@link PlayerSpawnData} to
 * run only once. It only applies to single-player or LAN hosts because the selection
 * comes from the create-world screen.
 */
@EventBusSubscriber(modid = BetterSpawn.MODID)
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
        MinecraftServer server = player.level().getServer();
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

        // Teleport only genuinely new players; existing players keep their position.
        int playTime = player.getStats().getValue(Stats.CUSTOM, Stats.PLAY_TIME);
        if (playTime > 0) {
            return;
        }

        ServerLevel target = server.getLevel(dimension);
        if (target == null) {
            BetterSpawn.LOGGER.warn("Spawn dimension {} is not loaded; keeping overworld",
                    dimension.identifier());
            return;
        }
        SpawnPoint point = data.get(dimension).orElse(null);
        if (point == null) {
            BetterSpawn.LOGGER.warn("No default spawn stored for {}; keeping overworld",
                    dimension.identifier());
            return;
        }

        PlayerSpawnData.setInitialDimensionApplied(player);

        // Wait one tick so the player has fully joined before the cross-dimension teleport.
        server.execute(() -> {
            if (player.hasDisconnected() || player.isRemoved() || !player.isAlive()) {
                return;
            }
            TeleportHelper.teleport(player, target,
                    point.pos().getX() + 0.5, point.pos().getY(), point.pos().getZ() + 0.5,
                    point.angle(), player.getXRot());
            player.sendSystemMessage(Component.translatable(
                    "betterspawn.msg.initial_dimension", dimension.identifier().toString()));
            BetterSpawn.diag("Initial spawn dimension: teleported {} to {} at {}",
                    player.getScoreboardName(), dimension.identifier(), point.pos());
        });
    }
}
