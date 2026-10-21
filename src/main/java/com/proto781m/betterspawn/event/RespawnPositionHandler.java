package com.proto781m.betterspawn.event;

import com.proto781m.betterspawn.config.BetterSpawnConfig;
import com.proto781m.betterspawn.logic.SpawnPointResolver;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.portal.DimensionTransition;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerRespawnPositionEvent;

/**
 * 重生位置拦截器：使用 NeoForge 的 {@link PlayerRespawnPositionEvent} 拦截重生逻辑，
 * 应用 BetterSpawn 的三层优先级解析。
 */
@EventBusSubscriber(modid = com.proto781m.betterspawn.BetterSpawn.MODID, bus = EventBusSubscriber.Bus.GAME)
public final class RespawnPositionHandler {

    private RespawnPositionHandler() {
    }

    @SubscribeEvent
    public static void onPlayerRespawnPosition(PlayerRespawnPositionEvent event) {
        Player player = event.getEntity();
        if (!(player instanceof ServerPlayer serverPlayer)) return;
        MinecraftServer server = serverPlayer.getServer();
        if (server == null) return;

        // 死亡维度（玩家当前所在维度）
        ResourceKey<Level> deathDimension = serverPlayer.level().dimension();

        // 解析自定义重生点
        var result = SpawnPointResolver.resolveRespawn(serverPlayer, deathDimension, server);
        if (result.status() != com.proto781m.betterspawn.api.ResolveResult.Status.OK || result.pos() == null) {
            // 解析失败：交还原版逻辑（event 不修改 dimensionTransition）
            return;
        }

        // 解析成功：使用结果中的目标维度
        ResourceKey<Level> targetDim = result.dimension() != null ? result.dimension() : deathDimension;
        ServerLevel targetLevel = server.getLevel(targetDim);
        if (targetLevel == null) return;

        // 构造新的 DimensionTransition
        BlockPos spawnPos = result.pos();
        DimensionTransition newTransition = new DimensionTransition(
                targetLevel,
                new Vec3(spawnPos.getX() + 0.5, spawnPos.getY(), spawnPos.getZ() + 0.5),
                Vec3.ZERO,
                result.angle(),
                0.0F,
                event.getDimensionTransition().postDimensionTransition()
        );

        // 设置新的重生位置
        event.setDimensionTransition(newTransition);

        // 同步原版 NBT（如果配置开启）
        if (BetterSpawnConfig.SYNC_VANILLA_NBT.get()) {
            serverPlayer.setRespawnPosition(targetDim, spawnPos, result.angle(), false, false);
        }

        // 标记复制原版重生位置（保留床/锚给下次用）
        event.setCopyOriginalSpawnPosition(true);
    }
}