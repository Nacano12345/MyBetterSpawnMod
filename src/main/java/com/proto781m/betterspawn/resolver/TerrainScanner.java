package com.proto781m.betterspawn.resolver;

import com.proto781m.betterspawn.BetterSpawn;
import com.proto781m.betterspawn.util.SafePosFinder;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.FluidState;

/**
 * 地形扫描器：按需加载区块、读取高度图、寻找安全站立点。
 *
 * <p>所有搜索方法共用这个类，统一处理三件容易出错的事：
 * <ol>
 *   <li><b>按需加载区块</b>：{@link net.minecraft.world.level.Level#getHeight} 在区块未加载时
 *       会直接返回 {@code minBuildHeight}，看起来像“虚空世界”；因此必须先确保区块存在。</li>
 *   <li><b>高度图语义</b>：{@code level.getHeight(...)} 返回的已经是“最高实体方块 + 1”，
 *       也就是玩家脚部 Y，不要再 {@code +1}。</li>
 *   <li><b>区块加载预算</b>：大范围搜索时限制本次解析最多加载多少个区块，
 *       避免一次生成上千区块导致卡顿或存档 IO 问题。</li>
 * </ol>
 *
 * <p>本类不是线程安全的，每次解析创建一个实例，只在服务器线程使用。
 */
public final class TerrainScanner {

    /** 流体处理策略。 */
    public enum LiquidPolicy {
        /** 遇到液体列直接放弃（适合普通陆地/浮岛）。 */
        REJECT_LIQUID,
        /** 允许沉入液体，继续向下寻找固体地面（适合水世界）。 */
        ALLOW_LIQUID
    }

    private final ServerLevel level;
    private final long deadlineMillis;
    private final int maxChunkLoads;

    private int chunkLoads;
    private int samples;
    private boolean timedOut;
    private boolean budgetExhausted;

    public TerrainScanner(ServerLevel level, long deadlineMillis, int maxChunkLoads) {
        this.level = level;
        this.deadlineMillis = deadlineMillis;
        this.maxChunkLoads = Math.max(1, maxChunkLoads);
    }

    /** 在 (x, z) 列寻找一个安全站立点（扫描到世界底部）。 */
    public BlockPos findSurface(int x, int z, LiquidPolicy policy) {
        return findSurface(x, z, policy, Integer.MAX_VALUE);
    }

    /**
     * 在 (x, z) 列寻找一个安全站立点，最多从高度图顶部向下扫描 {@code maxDepth} 格。
     * 用于“地表”方法，避免钻进玩家挖出的地下空间。
     */
    public BlockPos findSurface(int x, int z, LiquidPolicy policy, int maxDepth) {
        if (System.currentTimeMillis() > deadlineMillis) {
            timedOut = true;
            return null;
        }
        samples++;
        if (!ensureChunk(x, z)) {
            return null;
        }

        int minY = level.getMinBuildHeight();
        int topY = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
        if (topY <= minY + 1) {
            // 整列没有实体方块：虚空世界，或该区块尚未真正生成
            return null;
        }

        int maxY = Math.min(topY, level.getMaxBuildHeight() - 2);
        int lowest;
        if (maxDepth <= 0 || maxDepth >= maxY - minY) {
            lowest = minY;
        } else {
            lowest = maxY - maxDepth + 1;
        }

        for (int y = maxY; y >= lowest; y--) {
            BlockPos feet = new BlockPos(x, y, z);
            BlockPos groundPos = feet.below();
            BlockState ground = level.getBlockState(groundPos);
            FluidState groundFluid = ground.getFluidState();

            if (!groundFluid.isEmpty()) {
                if (policy == LiquidPolicy.REJECT_LIQUID) {
                    // 当前列是水面/岩浆面，直接放弃这一列，避免把玩家丢进海里
                    return null;
                }
                // ALLOW_LIQUID：继续向下，直到找到真正的固体地面
                continue;
            }

            if (!SafePosFinder.isSafeGround(level, groundPos)) {
                continue;
            }

            BlockState feetState = level.getBlockState(feet);
            BlockState headState = level.getBlockState(feet.above());
            if (!SafePosFinder.isPassable(feetState) || !SafePosFinder.isPassable(headState)) {
                continue;
            }

            if (policy == LiquidPolicy.REJECT_LIQUID
                    && (!feetState.getFluidState().isEmpty() || !headState.getFluidState().isEmpty())) {
                continue;
            }

            return feet;
        }
        return null;
    }

    /**
     * 在 (x, z) 列从 {@code fromY} 向下扫描到 {@code toY}，寻找安全站立点。
     * 用于“顶部方法”：地狱需要从基岩顶下方开始向下找，而不是从高度图顶部找。
     */
    public BlockPos findBelow(int x, int z, int fromY, int toY, LiquidPolicy policy) {
        if (System.currentTimeMillis() > deadlineMillis) {
            timedOut = true;
            return null;
        }
        samples++;
        if (!ensureChunk(x, z)) {
            return null;
        }

        int start = Math.min(fromY, level.getMaxBuildHeight() - 2);
        int end = Math.max(toY, level.getMinBuildHeight() + 1);
        for (int y = start; y >= end; y--) {
            BlockPos feet = new BlockPos(x, y, z);
            BlockPos groundPos = feet.below();
            BlockState ground = level.getBlockState(groundPos);
            FluidState fluid = ground.getFluidState();
            if (!fluid.isEmpty()) {
                if (policy == LiquidPolicy.REJECT_LIQUID) {
                    continue; // 岩浆/水：换下一个高度
                }
                continue;
            }
            if (!SafePosFinder.isSafeGround(level, groundPos)) {
                continue;
            }
            if (!SafePosFinder.isPassable(level.getBlockState(feet))
                    || !SafePosFinder.isPassable(level.getBlockState(feet.above()))) {
                continue;
            }
            if (policy == LiquidPolicy.REJECT_LIQUID
                    && (!level.getBlockState(feet).getFluidState().isEmpty()
                    || !level.getBlockState(feet.above()).getFluidState().isEmpty())) {
                continue;
            }
            return feet;
        }
        return null;
    }

    /**
     * 确保 (x, z) 所在区块已加载到 FULL 状态。未生成的区块会被同步生成。
     * 超过本次预算时返回 {@code false} 并标记 {@link #budgetExhausted()}。
     */
    public boolean ensureChunk(int x, int z) {
        int chunkX = x >> 4;
        int chunkZ = z >> 4;
        if (level.getChunkSource().hasChunk(chunkX, chunkZ)) {
            return true;
        }
        if (chunkLoads >= maxChunkLoads) {
            budgetExhausted = true;
            return false;
        }
        chunkLoads++;
        try {
            level.getChunk(chunkX, chunkZ, ChunkStatus.FULL, true);
            return level.getChunkSource().hasChunk(chunkX, chunkZ);
        } catch (Exception ex) {
            BetterSpawn.LOGGER.warn("Failed to load chunk [{}, {}]: {}", chunkX, chunkZ, ex.toString());
            return false;
        }
    }

    public int samples() {
        return samples;
    }

    public boolean timedOut() {
        return timedOut;
    }

    public boolean budgetExhausted() {
        return budgetExhausted;
    }
}
