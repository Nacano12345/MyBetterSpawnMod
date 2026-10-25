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
 * Terrain scanner: loads chunks on demand, reads heightmaps, and finds safe standing
 * positions.
 *
 * <p>All search methods share this class to handle three common failure modes:
 * <ol>
 *   <li><b>Chunk loading</b>: {@link net.minecraft.world.level.Level#getHeight}
 *       returns {@code minBuildHeight} when the chunk is not loaded, which looks like
 *       an empty world; the chunk must exist first.</li>
 *   <li><b>Heightmap semantics</b>: {@code level.getHeight(...)} already returns the
 *       first air block above the highest solid block, which is the player's feet Y;
 *       do not add one.</li>
 *   <li><b>Chunk budget</b>: large searches limit how many chunks may be loaded so a
 *       single resolution cannot generate thousands of chunks or cause excessive I/O.</li>
 * </ol>
 *
 * <p>This class is not thread-safe. Create one instance per resolution and use it only
 * on the server thread.
 */
public final class TerrainScanner {

    /** Fluid handling strategy. */
    public enum LiquidPolicy {
        /** Abandon a liquid column immediately (for normal land and floating islands). */
        REJECT_LIQUID,
        /** Allow descending into liquid and continue looking for solid ground (for water worlds). */
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

    /** Finds a safe standing position in the (x,z) column, scanning to the world bottom. */
    public BlockPos findSurface(int x, int z, LiquidPolicy policy) {
        return findSurface(x, z, policy, Integer.MAX_VALUE);
    }

    /**
     * Finds a safe standing position in the (x,z) column, scanning down at most
     * {@code maxDepth} blocks from the heightmap top. Used by surface methods to avoid
     * caves excavated by players.
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

        int minY = level.getMinY();
        int topY = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
        if (topY <= minY + 1) {
            // The whole column has no solid block: a void world, or the chunk has not really generated.
            return null;
        }

        int maxY = Math.min(topY, level.getMaxY() - 2);
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
                    // This column is a water/lava surface; skip it so players are not dropped into the sea.
                    return null;
                }
                // ALLOW_LIQUID: keep descending until a real solid floor is found.
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
     * Scans the (x,z) column downward from {@code fromY} to {@code toY} for a safe
     * standing position. Used by the top method: the Nether must start below the bedrock
     * ceiling instead of at the heightmap top.
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

        int start = Math.min(fromY, level.getMaxY() - 2);
        int end = Math.max(toY, level.getMinY() + 1);
        for (int y = start; y >= end; y--) {
            BlockPos feet = new BlockPos(x, y, z);
            BlockPos groundPos = feet.below();
            BlockState ground = level.getBlockState(groundPos);
            FluidState fluid = ground.getFluidState();
            if (!fluid.isEmpty()) {
                if (policy == LiquidPolicy.REJECT_LIQUID) {
                    continue; // Lava/water: try the next height
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
     * Ensures the chunk containing (x,z) is loaded to FULL status, generating it if
     * necessary. Returns {@code false} and sets {@link #budgetExhausted()} when the
     * chunk budget is exhausted.
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
