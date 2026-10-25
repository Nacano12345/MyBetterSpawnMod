package com.proto781m.betterspawn.util;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.storage.LevelData;

/**
 * Version adapter for reading and writing world (dimension) spawn points.
 *
 * <p>Writes go through {@link ServerLevel#setRespawnData(LevelData.RespawnData)}, which
 * updates {@code level.dat}, notifies clients, and maintains the spawn-chunk ticket.
 */
public final class SpawnAdapter {

    private SpawnAdapter() {
    }

    /** Writes the dimension's current vanilla spawn point (updates level.dat and notifies clients). */
    public static void setLevelSpawn(ServerLevel level, BlockPos pos, float angle) {
        level.setRespawnData(LevelData.RespawnData.of(level.dimension(), pos, angle, 0.0F));
    }
}
