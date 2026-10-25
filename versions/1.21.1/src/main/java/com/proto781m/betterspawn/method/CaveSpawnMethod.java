package com.proto781m.betterspawn.method;

import com.proto781m.betterspawn.api.ResolveResult;
import com.proto781m.betterspawn.api.SpawnContext;
import com.proto781m.betterspawn.api.SpawnMethod;
import com.proto781m.betterspawn.config.BetterSpawnConfig;
import com.proto781m.betterspawn.resolver.SpawnSafetyValidator;
import com.proto781m.betterspawn.resolver.TerrainScanner;
import com.proto781m.betterspawn.util.SafePosFinder;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;

/**
 * Cave method: scans for underground pockets with a floor and a low ceiling without
 * relying on the sky or heightmap.
 *
 * <p>This works in the Nether, the End, or any dimension without a sky, as well as in
 * Overworld caves. A position is a valid cave when:
 * <ul>
 *   <li>the block below is solid and not a fluid;</li>
 *   <li>the feet and head can stand, with at least {@code min_air_height} non-fluid air blocks above;</li>
 *   <li>a solid block is found within {@code roof_scan} blocks above as a ceiling;</li>
 *   <li>there is no liquid between the ceiling and the feet.</li>
 * </ul>
 *
 * <p>If no ceiling is found within the scan limit, the position is treated as surface
 * or open space and skipped.
 *
 * <p>Parameters:
 * <ul>
 *   <li>{@code center_x}, {@code center_z}: search center; defaults to 0,0</li>
 *   <li>{@code min_y}, {@code max_y}: vertical scan range</li>
 *   <li>{@code search_radius}: horizontal search radius; defaults to 128</li>
 *   <li>{@code ring_step}: ring step; defaults to 32</li>
 *   <li>{@code min_air_height}: minimum air height above the feet; defaults to 2</li>
 *   <li>{@code roof_scan}: maximum blocks to scan upward for a ceiling; defaults to 8</li>
 *   <li>{@code avoid_lava}: whether to avoid lava; defaults to true</li>
 *   <li>{@code max_chunk_loads}: maximum chunks loaded during this search</li>
 * </ul>
 */
public class CaveSpawnMethod implements SpawnMethod {

    @Override
    public String id() {
        return "cave";
    }

    @Override
    public ResolveResult resolve(SpawnContext ctx) {
        ServerLevel level = ctx.level();
        ResourceKey<Level> dimension = ctx.rule().dimensionKey();
        RandomSource rng = RandomSource.create(ctx.seed());

        int centerX = ctx.rule().getInt("center_x", 0);
        int centerZ = ctx.rule().getInt("center_z", 0);
        int minY = Math.max(level.getMinBuildHeight() + 1,
                ctx.rule().getInt("min_y", level.getMinBuildHeight() + 1));
        int maxY = Math.min(level.getMaxBuildHeight() - 2,
                ctx.rule().getInt("max_y", level.getMaxBuildHeight() - 2));
        int searchRadius = clamp(ctx.rule().getInt("search_radius", 128), 0, 4096);
        int ringStep = clamp(ctx.rule().getInt("ring_step", 32), 8, 512);
        int minAirHeight = clamp(ctx.rule().getInt("min_air_height", 2), 2, 8);
        int roofScan = clamp(ctx.rule().getInt("roof_scan", 8), 3, 32);
        boolean avoidLava = ctx.rule().getBool("avoid_lava", true);
        int maxChunkLoads = clamp(
                ctx.rule().getInt("max_chunk_loads", BetterSpawnConfig.MAX_CHUNK_LOADS.get()),
                1, 4096);

        TerrainScanner scanner = new TerrainScanner(level, ctx.deadlineMillis(), maxChunkLoads);
        float angle = rng.nextFloat() * 360.0F;
        int samples = 0;
        int radiusReached = 0;

        // 1. Center point
        samples++;
        CaveSite center = findCave(level, scanner, centerX, centerZ, minY, maxY,
                minAirHeight, roofScan, avoidLava);
        if (center != null) {
            ctx.trace().ring(0, 1, 1);
            return result(level, dimension, center, angle, samples, 0);
        }

        // 2. Concentric rings
        for (int radius = ringStep; radius <= searchRadius; radius += ringStep) {
            radiusReached = radius;
            int count = clamp(radius / 16, 8, 32);
            int accepted = 0;
            for (int i = 0; i < count; i++) {
                double radians = (Math.PI * 2.0) * (i + 0.5) / count;
                int x = centerX + (int) Math.round(radius * Math.cos(radians));
                int z = centerZ + (int) Math.round(radius * Math.sin(radians));
                if (Math.abs(x) > 30_000_000 || Math.abs(z) > 30_000_000) {
                    continue;
                }

                samples++;
                CaveSite site = findCave(level, scanner, x, z, minY, maxY,
                        minAirHeight, roofScan, avoidLava);
                if (site != null) {
                    accepted++;
                    ctx.trace().ring(radius, count, accepted);
                    return result(level, dimension, site, angle, samples, radius);
                }
            }
            ctx.trace().ring(radius, count, accepted);

            if (scanner.timedOut()) {
                return failed(dimension, samples, radiusReached, ResolveResult.Status.TIMEOUT,
                        List.of("Search timed out"));
            }
            if (scanner.budgetExhausted()) {
                return failed(dimension, samples, radiusReached, ResolveResult.Status.UNSAFE,
                        List.of("Chunk budget exhausted (" + maxChunkLoads + "); no cave found"));
            }
        }

        return failed(dimension, samples, radiusReached, ResolveResult.Status.UNSAFE,
                List.of("No cave with a roof found within radius " + searchRadius));
    }

    private record CaveSite(BlockPos feet, int airHeight, int roofY) {
    }

    /** Scans one column from top to bottom for a cave. Returns the first position with a
     * floor, clearance, and a ceiling. */
    private CaveSite findCave(ServerLevel level, TerrainScanner scanner, int x, int z,
                              int minY, int maxY, int minAirHeight, int roofScan,
                              boolean avoidLava) {
        if (!scanner.ensureChunk(x, z)) {
            return null;
        }

        for (int y = maxY; y >= minY; y--) {
            BlockPos feet = new BlockPos(x, y, z);
            if (!SafePosFinder.isSafeStandingPosition(level, feet)) {
                continue;
            }

            BlockState ground = level.getBlockState(feet.below());
            FluidState groundFluid = ground.getFluidState();
            if (avoidLava && groundFluid.is(net.minecraft.tags.FluidTags.LAVA)) {
                continue;
            }
            if (!groundFluid.isEmpty()) {
                continue;
            }

            int air = 0;
            boolean blockedByLiquid = false;
            for (int h = 0; h < roofScan; h++) {
                BlockPos above = feet.above(h);
                BlockState state = level.getBlockState(above);
                if (!state.getFluidState().isEmpty()) {
                    blockedByLiquid = true;
                    break;
                }
                if (SafePosFinder.isPassable(state)) {
                    air++;
                } else {
                    break; // Reached the ceiling
                }
            }

            if (blockedByLiquid || air < minAirHeight || air >= roofScan) {
                continue;
            }

            // Confirm the air position is actually a solid block rather than a roofScan cutoff.
            BlockState roof = level.getBlockState(feet.above(air));
            if (roof.getFluidState().isEmpty() && !SafePosFinder.isPassable(roof)) {
                return new CaveSite(feet, air, feet.getY() + air);
            }
        }
        return null;
    }

    private ResolveResult result(ServerLevel level, ResourceKey<Level> dimension, CaveSite site,
                                 float angle, int samples, int radius) {
        List<String> warnings = new ArrayList<>(SpawnSafetyValidator.validate(level, site.feet()));
        warnings.add("Cave roof y=" + site.roofY() + ", headroom " + site.airHeight() + " blocks");
        return new ResolveResult(site.feet(), dimension, angle, id(), false, 0L,
                samples, radius, ResolveResult.Status.OK, warnings);
    }

    private static ResolveResult failed(ResourceKey<Level> dimension, int samples, int radius,
                                        ResolveResult.Status status, List<String> warnings) {
        return new ResolveResult(null, dimension, 0.0F, "cave", false, 0L,
                samples, radius, status, warnings);
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
