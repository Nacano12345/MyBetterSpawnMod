package com.proto781m.betterspawn.resolver;

import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.ChunkAccess;

/**
 * 区块搜索辅助：只检查/加载已生成的区块，不强制生成新区块。
 *
 * <p>所有出生方法在读取某个坐标的方块前，都应先调用 {@link #ensureChunk}，
 * 否则未加载区块会读到空的占位区块，导致误判为“安全”。
 *
 * <p>注意：不强制生成未生成的区块，避免在新世界中大量生成区块导致系统崩溃。
 */
public final class ChunkSearchHelper {

    private ChunkSearchHelper() {
    }

    private static long chunkPos(BlockPos pos) {
        return (((long) pos.getX() >> 4) & 0xFFFFFFFFL) << 32 | ((long) pos.getZ() >> 4) & 0xFFFFFFFFL;
    }

    /**
     * 确保 {@code pos} 所在区块已加载到 FULL 状态（仅加载已生成区块，不强制生成）。
     * 返回区块是否已存在且已加载。
     */
    public static boolean ensureChunk(ServerLevel level, BlockPos pos) {
        long chunkPos = (((long) pos.getX() >> 4) & 0xFFFFFFFFL) << 32 | ((long) pos.getZ() >> 4) & 0xFFFFFFFFL;
        return level.getChunkSource().chunkMap.getVisibleChunkIfPresent(chunkPos(pos)) != null;
    }

    /**
     * 检查区块是否存在（不加载，仅查询）。
     */
    public static boolean chunkExists(ServerLevel level, BlockPos pos) {
        long chunkPos = (((long) pos.getX() >> 4) & 0xFFFFFFFFL) << 32 | ((long) pos.getZ() >> 4) & 0xFFFFFFFFL;
        return level.getChunkSource().chunkMap.getVisibleChunkIfPresent(chunkPos(pos)) != null;
    }
}
