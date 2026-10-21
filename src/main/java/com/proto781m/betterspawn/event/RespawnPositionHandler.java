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
import net.minecraft.world.level.portal.DimensionTransition;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerRespawnPositionEvent;

/**
 * 重生位置拦截器：使用 NeoForge 的 {@link PlayerRespawnPositionEvent} 应用三层优先级。
 *
 * <p>只有解析出坐标时才修改 {@link DimensionTransition}；否则保持原版行为。
 */
@EventBusSubscriber(modid = BetterSpawn.MODID, bus = EventBusSubscriber.Bus.GAME)
public final class RespawnPositionHandler {

    private RespawnPositionHandler() {
    }

    @SubscribeEvent
    public static void onPlayerRespawnPosition(PlayerRespawnPositionEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        MinecraftServer server = player.getServer();
        if (server == null) {
            return;
        }

        ResourceKey<Level> deathDimension = player.level().dimension();
        ResolveResult result = SpawnPointResolver.resolveRespawn(player, deathDimension, server);
        if (result.pos() == null) {
            // 让原版继续处理（通常是主世界默认出生点）
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
        DimensionTransition transition = new DimensionTransition(
                targetLevel,
                new Vec3(spawnPos.getX() + 0.5, spawnPos.getY(), spawnPos.getZ() + 0.5),
                Vec3.ZERO,
                result.angle(),
                0.0F,
                event.getDimensionTransition().postDimensionTransition());

        event.setDimensionTransition(transition);
        // 保留原版的床/锚 NBT，这样床/锚槽在下次重生时仍能参与优先级判断
        event.setCopyOriginalSpawnPosition(true);
        BetterSpawn.diag("Respawn {} in {} -> {} at {} in {}",
                player.getScoreboardName(), deathDimension.location(), result.usedMethod(),
                spawnPos, targetDimension.location());
    }
}
