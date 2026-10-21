package com.proto781m.betterspawn.util;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

/**
 * 版本适配层：读写世界（维度）出生点。
 *
 * <p>写入统一走 {@link ServerLevel#setDefaultSpawnPos(BlockPos, float)}，
 * 这样会同时更新 {@code level.dat}、通知客户端、并维护出生区块 ticket。
 */
public final class SpawnAdapter {

    private SpawnAdapter() {
    }

    /** 读取维度当前的原版出生点。 */
    public static com.proto781m.betterspawn.data.SpawnPoint getLevelSpawn(ServerLevel level) {
        var data = level.getLevelData();
        return new com.proto781m.betterspawn.data.SpawnPoint(
                data.getSpawnPos(), level.dimension(), data.getSpawnAngle());
    }

    /** 写入维度当前的原版出生点（同步到 level.dat 并通知客户端）。 */
    public static void setLevelSpawn(ServerLevel level, BlockPos pos, float angle) {
        level.setDefaultSpawnPos(pos, angle);
    }
}
