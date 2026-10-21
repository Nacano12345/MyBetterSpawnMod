package com.proto781m.betterspawn.method;

import com.proto781m.betterspawn.api.ResolveResult;
import com.proto781m.betterspawn.api.SpawnContext;
import com.proto781m.betterspawn.api.SpawnMethod;
import com.proto781m.betterspawn.BetterSpawn;
import com.proto781m.betterspawn.config.BetterSpawnConfig;
import com.proto781m.betterspawn.resolver.SpawnSafetyValidator;
import com.proto781m.betterspawn.resolver.TerrainScanner;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.PlayerRespawnLogic;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;

/**
 * 默认方法（主世界）：从中心点开始，按同心环带向外搜索第一个安全陆地。
 *
 * <p>参数：
 * <ul>
 *   <li>{@code center_x}, {@code center_z}：搜索中心，默认 0,0</li>
 *   <li>{@code max_radius}：最大搜索半径，默认 512，硬上限 8192</li>
 *   <li>{@code ring_step}：环带步长，默认 64</li>
 *   <li>{@code max_chunk_loads}：本次最多生成多少个区块，默认取配置值</li>
 * </ul>
 *
 * <p>实现要点：{@code level.getHeight(...)} 返回的已经是脚部 Y；
 * 早期版本错误地再 {@code +1}，导致所有候选点都悬空一格、全部回退到平台方法。
 */
public class DefaultSpawnMethod implements SpawnMethod {

    @Override
    public String id() {
        return "default";
    }

    @Override
    public ResolveResult resolve(SpawnContext ctx) {
        ServerLevel level = ctx.level();
        ResourceKey<Level> dimension = ctx.rule().dimensionKey();
        RandomSource rng = RandomSource.create(ctx.seed());

        int centerX = ctx.rule().getInt("center_x", 0);
        int centerZ = ctx.rule().getInt("center_z", 0);
        int maxRadius = clamp(ctx.rule().getInt("max_radius", 256), 0, 8192);
        int ringStep = clamp(ctx.rule().getInt("ring_step", 64), 16, 1024);
        int maxChunkLoads = clamp(
                ctx.rule().getInt("max_chunk_loads", BetterSpawnConfig.MAX_CHUNK_LOADS.get()),
                1, 4096);
        int maxSurfaceDepth = clamp(ctx.rule().getInt("max_surface_depth", 16), 1, 64);
        int minSurfaceY = ctx.rule().getInt("min_surface_y", level.getSeaLevel() - 16);

        TerrainScanner scanner = new TerrainScanner(level, ctx.deadlineMillis(), maxChunkLoads);
        float angle = rng.nextFloat() * 360.0F;
        int radiusReached = 0;

        // 1. 中心点优先
        BlockPos center = findSurface(scanner, centerX, centerZ, maxSurfaceDepth, minSurfaceY);
        if (center != null) {
            ctx.trace().ring(0, 1, 1);
            return ok(level, dimension, center, angle, scanner, 0);
        }

        // 2. 同心环带
        String budgetWarning = null;
        for (int radius = ringStep; radius <= maxRadius; radius += ringStep) {
            radiusReached = radius;
            int count = clamp(radius / 4, 16, 64);
            int accepted = 0;
            for (int i = 0; i < count; i++) {
                double radians = (Math.PI * 2.0) * (i + 0.5) / count;
                int x = centerX + (int) Math.round(radius * Math.cos(radians));
                int z = centerZ + (int) Math.round(radius * Math.sin(radians));
                if (Math.abs(x) > 30_000_000 || Math.abs(z) > 30_000_000) {
                    continue;
                }

                BlockPos feet = findSurface(scanner, x, z, maxSurfaceDepth, minSurfaceY);
                if (feet != null) {
                    accepted++;
                    ctx.trace().ring(radius, count, accepted);
                    return ok(level, dimension, feet, angle, scanner, radius);
                }
            }
            ctx.trace().ring(radius, count, accepted);

            if (scanner.timedOut()) {
                return failed(dimension, scanner, radiusReached, ResolveResult.Status.TIMEOUT,
                        List.of("搜索超时"));
            }
            if (scanner.budgetExhausted()) {
                budgetWarning = "区块加载预算耗尽（" + maxChunkLoads + "），尝试原版气候采样兜底";
                break;
            }
        }

        // 3. 原版气候采样兜底：海里/大范围无陆地时，用原版 findSpawnPosition 选气候合适的区块
        if (ctx.rule().getBool("vanilla_fallback", true)
                && System.currentTimeMillis() < ctx.deadlineMillis()) {
            BlockPos vanilla = findVanillaSpawn(level, ctx, minSurfaceY);
            if (vanilla != null) {
                return new ResolveResult(vanilla, dimension, angle, id(), false, 0L,
                        scanner.samples(), radiusReached, ResolveResult.Status.OK,
                        List.of("使用原版气候采样兜底"));
            }
        }

        return failed(dimension, scanner, radiusReached, ResolveResult.Status.UNSAFE,
                budgetWarning != null
                        ? List.of(budgetWarning, "原版气候采样兜底也未找到安全点")
                        : List.of("在半径 " + maxRadius + " 内未找到安全点，原版气候采样兜底也未找到"));
    }

    private static BlockPos findSurface(TerrainScanner scanner, int x, int z,
                                       int maxDepth, int minSurfaceY) {
        BlockPos feet = scanner.findSurface(x, z, TerrainScanner.LiquidPolicy.REJECT_LIQUID, maxDepth);
        return feet != null && feet.getY() >= minSurfaceY ? feet : null;
    }

    private static BlockPos findVanillaSpawn(ServerLevel level, SpawnContext ctx, int minSurfaceY) {
        try {
            // 原版出生点气候采样：不生成区块，只选一个气候合适的候选区块
            ChunkPos origin = new ChunkPos(
                    level.getChunkSource().randomState().sampler().findSpawnPosition());

            // 原版本身会在 11x11 区块范围内找安全落点；这里做同样的事
            for (int radius = 0; radius <= 2; radius++) {
                for (int dx = -radius; dx <= radius; dx++) {
                    for (int dz = -radius; dz <= radius; dz++) {
                        if (Math.max(Math.abs(dx), Math.abs(dz)) != radius) {
                            continue;
                        }
                        if (System.currentTimeMillis() > ctx.deadlineMillis()) {
                            return null;
                        }

                        BlockPos pos = PlayerRespawnLogic.getSpawnPosInChunk(level,
                                new ChunkPos(origin.x + dx, origin.z + dz));
                        if (pos != null && pos.getY() >= minSurfaceY
                                && SpawnSafetyValidator.isSafe(level, pos)) {
                            return pos;
                        }
                    }
                }
            }
        } catch (Exception ex) {
            BetterSpawn.LOGGER.warn("原版气候采样兜底失败: {}", ex.toString());
        }
        return null;
    }

    private static ResolveResult ok(ServerLevel level, ResourceKey<Level> dimension, BlockPos pos,
                                    float angle, TerrainScanner scanner, int radius) {
        return new ResolveResult(pos, dimension, angle, "default", false, 0L,
                scanner.samples(), radius, ResolveResult.Status.OK,
                SpawnSafetyValidator.validate(level, pos));
    }

    private static ResolveResult failed(ResourceKey<Level> dimension, TerrainScanner scanner,
                                        int radius, ResolveResult.Status status, List<String> warnings) {
        return new ResolveResult(null, dimension, 0.0F, "default", false, 0L,
                scanner.samples(), radius, status, warnings);
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
