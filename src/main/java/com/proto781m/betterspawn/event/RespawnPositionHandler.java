package com.proto781m.betterspawn.event;

import com.proto781m.betterspawn.BetterSpawn;
import com.proto781m.betterspawn.api.ResolveResult;
import com.proto781m.betterspawn.logic.SpawnPointResolver;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerRespawnPositionEvent;

/**
 * Respawn position interceptor. Applies the three-layer priority chain through
 * NeoForge's {@link PlayerRespawnPositionEvent}.
 *
 * <p>The {@link TeleportTransition} is modified only when a position is resolved;
 * otherwise vanilla behavior is preserved.
 */
@EventBusSubscriber(modid = BetterSpawn.MODID)
public final class RespawnPositionHandler {

    private RespawnPositionHandler() {
    }

    @SubscribeEvent
    public static void onPlayerRespawnPosition(PlayerRespawnPositionEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        MinecraftServer server = player.level().getServer();
        if (server == null) {
            return;
        }

        ResourceKey<Level> deathDimension = player.level().dimension();
        ResolveResult result = SpawnPointResolver.resolveRespawn(player, deathDimension, server);
        if (result.pos() == null) {
            // Let vanilla handle it (usually the Overworld default spawn).
            return;
        }

        ResourceKey<Level> targetDimension = result.dimension() != null
                ? result.dimension()
                : deathDimension;
        ServerLevel targetLevel = server.getLevel(targetDimension);
        if (targetLevel == null) {
            return;
        }

        BlockPos spawnPos = result.pos();
        TeleportTransition transition = new TeleportTransition(
                targetLevel,
                new Vec3(spawnPos.getX() + 0.5, spawnPos.getY(), spawnPos.getZ() + 0.5),
                Vec3.ZERO,
                result.angle(),
                0.0F,
                event.getTeleportTransition().postTeleportTransition());

        event.setTeleportTransition(transition);
        // Keep the vanilla bed/anchor NBT so those slots can still participate in the next respawn priority check.
        event.setCopyOriginalSpawnPosition(true);
        BetterSpawn.diag("Respawn {} in {} -> {} at {} in {}",
                player.getScoreboardName(), deathDimension.identifier(), result.usedMethod(),
                spawnPos, targetDimension.identifier());
    }
}
