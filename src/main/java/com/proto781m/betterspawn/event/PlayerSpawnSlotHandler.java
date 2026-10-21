package com.proto781m.betterspawn.event;

import com.proto781m.betterspawn.BetterSpawn;
import com.proto781m.betterspawn.data.PlayerSpawnData;
import com.proto781m.betterspawn.data.SpawnSlot;
import com.proto781m.betterspawn.data.SpawnSlotType;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.RespawnAnchorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerSetSpawnEvent;

/**
 * 把原版的重生点变更镜像到 BetterSpawn 的双槽中。
 *
 * <p>这样不需要 Mixin 也能覆盖所有入口：
 * <ul>
 *   <li>床：{@code ServerPlayer.startSleepInBed} → {@code setRespawnPosition}；</li>
 *   <li>重生锚：{@code RespawnAnchorBlock.useWithoutItem} → {@code setRespawnPosition}；</li>
 *   <li>原版 {@code /spawnpoint} 与其他模组：同样调用 {@code setRespawnPosition}。</li>
 * </ul>
 *
 * <p>通过 {@code newSpawn} 处方块类型判断是床、锚还是普通指令点。
 */
@EventBusSubscriber(modid = BetterSpawn.MODID, bus = EventBusSubscriber.Bus.GAME)
public final class PlayerSpawnSlotHandler {

    private PlayerSpawnSlotHandler() {
    }

    @SubscribeEvent
    public static void onPlayerSetSpawn(PlayerSetSpawnEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }

        BlockPos pos = event.getNewSpawn();
        if (pos == null) {
            // 原版/其他模组重置了重生点。这里不做清空：PlayerList 在重生时会用
            // copyRespawnPosition(null) 调用本方法，如果清空会把玩家自己的槽误删。
            // 需要清空时请使用 /betterspawn spawnpoint clear。
            return;
        }

        ResourceKey<Level> dimension = event.getSpawnLevel();
        ServerLevel level = player.getServer() == null ? null : player.getServer().getLevel(dimension);
        if (level == null) {
            return;
        }

        BlockState state = level.getBlockState(pos);
        SpawnSlotType type;
        if (state.getBlock() instanceof BedBlock) {
            if (!BedBlock.canSetSpawn(level)) {
                return;
            }
            type = SpawnSlotType.BED;
        } else if (state.getBlock() instanceof RespawnAnchorBlock) {
            if (!RespawnAnchorBlock.canSetSpawn(level)) {
                return;
            }
            type = SpawnSlotType.ANCHOR;
        } else {
            type = SpawnSlotType.COMMAND;
        }

        PlayerSpawnData.setSlot(player, SpawnSlot.valid(type, dimension, pos, player.getYRot()));
        BetterSpawn.diag("Recorded {} spawn slot for {} at {} in {}",
                type, player.getScoreboardName(), pos, dimension.location());
    }
}
