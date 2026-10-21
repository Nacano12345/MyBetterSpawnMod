package com.proto781m.betterspawn.util;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.storage.WritableLevelData;

/**
 * 版本适配层：读写世界（维度）出生点。
 *
 * <p>1.21 中 {@code ServerLevel#getLevelData()} 返回 {@link net.minecraft.world.level.storage.LevelData}，
 * 实际实现是 {@code ServerLevelData}（继承 {@link WritableLevelData}），因此这里做一次向下转型调用
 * {@code setSpawn(BlockPos, float)}。
 *
 * <p>未来迁移到 1.21.2+（{@code RespawnData} 重构）时，只需替换本类实现，
 * 上层逻辑无需改动。
 */
public final class SpawnAdapter {

    private SpawnAdapter() {
    }

    /** 读取维度当前的原版出生点。 */
    public static com.proto781m.betterspawn.data.SpawnPoint getLevelSpawn(ServerLevel level) {
        var data = level.getLevelData();
        return new com.proto781m.betterspawn.data.SpawnPoint(data.getSpawnPos(), data.getSpawnAngle());
    }

    /** 写入维度当前的原版出生点（同步到 level.dat）。 */
    public static void setLevelSpawn(ServerLevel level, BlockPos pos, float angle) {
        ((WritableLevelData) level.getLevelData()).setSpawn(pos, angle);
    }
}
