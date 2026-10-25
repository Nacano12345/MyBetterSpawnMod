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
import net.minecraft.world.attribute.EnvironmentAttributes;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerSetSpawnEvent;

/**
 * Mirrors vanilla respawn-point changes into BetterSpawn's slots.
 *
 * <p>This covers every entry point without Mixins:
 * <ul>
 *   <li>Beds: {@code ServerPlayer.startSleepInBed} calls {@code setRespawnPosition};</li>
 *   <li>Respawn anchors: {@code RespawnAnchorBlock.useWithoutItem} calls {@code setRespawnPosition};</li>
 *   <li>Vanilla {@code /spawnpoint} and other mods also call {@code setRespawnPosition}.</li>
 * </ul>
 *
 * <p>The block type at {@code newSpawn} determines whether this is a bed, an anchor,
 * or a regular command point.
 */
@EventBusSubscriber(modid = BetterSpawn.MODID)
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
            // Vanilla or another mod reset the respawn point. Do not clear here: PlayerList uses
            // copyRespawnPosition(null) calls this method; clearing would delete the player's own slot.
            // Use /betterspawn spawnpoint clear when clearing is intended.
            return;
        }

        ResourceKey<Level> dimension = event.getSpawnLevel();
        ServerLevel level = player.level().getServer() == null ? null : player.level().getServer().getLevel(dimension);
        if (level == null) {
            return;
        }

        BlockState state = level.getBlockState(pos);
        SpawnSlotType type;
        if (state.getBlock() instanceof BedBlock) {
            if (!level.environmentAttributes().getValue(EnvironmentAttributes.BED_RULE, pos).canSetSpawn(level)) {
                return;
            }
            type = SpawnSlotType.BED;
        } else if (state.getBlock() instanceof RespawnAnchorBlock) {
            if (!RespawnAnchorBlock.canSetSpawn(level, pos)) {
                return;
            }
            type = SpawnSlotType.ANCHOR;
        } else {
            type = SpawnSlotType.COMMAND;
        }

        PlayerSpawnData.setSlot(player, SpawnSlot.valid(type, dimension, pos, player.getYRot()));
        BetterSpawn.diag("Recorded {} spawn slot for {} at {} in {}",
                type, player.getScoreboardName(), pos, dimension.identifier());
    }
}
