package com.proto781m.betterspawn.method;

import com.proto781m.betterspawn.api.ResolveResult;
import com.proto781m.betterspawn.api.SpawnContext;
import com.proto781m.betterspawn.api.SpawnMethod;
import com.proto781m.betterspawn.config.BetterSpawnConfig;
import com.proto781m.betterspawn.resolver.SpawnSafetyValidator;
import com.proto781m.betterspawn.resolver.TerrainScanner;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
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
        int maxRadius = clamp(ctx.rule().getInt("max_radius", 512), 0, 8192);
        int ringStep = clamp(ctx.rule().getInt("ring_step", 64), 16, 1024);
        int maxChunkLoads = clamp(
                ctx.rule().getInt("max_chunk_loads", BetterSpawnConfig.MAX_CHUNK_LOADS.get()),
                1, 4096);

        TerrainScanner scanner = new TerrainScanner(level, ctx.deadlineMillis(), maxChunkLoads);
        float angle = rng.nextFloat() * 360.0F;
        int radiusReached = 0;

        // 1. 中心点优先
        BlockPos center = scanner.findSurface(centerX, centerZ, TerrainScanner.LiquidPolicy.REJECT_LIQUID);
        if (center != null) {
            ctx.trace().ring(0, 1, 1);
            return ok(level, dimension, center, angle, scanner, 0);
        }

        // 2. 同心环带
        for (int radius = ringStep; radius <= maxRadius; radius += ringStep) {
            radiusReached = radius;
            int count = clamp(radius / 16, 8, 48);
            int accepted = 0;
            for (int i = 0; i < count; i++) {
                double radians = (Math.PI * 2.0) * (i + 0.5) / count;
                int x = centerX + (int) Math.round(radius * Math.cos(radians));
                int z = centerZ + (int) Math.round(radius * Math.sin(radians));
                if (Math.abs(x) > 30_000_000 || Math.abs(z) > 30_000_000) {
                    continue;
                }

                BlockPos feet = scanner.findSurface(x, z, TerrainScanner.LiquidPolicy.REJECT_LIQUID);
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
                return failed(dimension, scanner, radiusReached, ResolveResult.Status.UNSAFE,
                        List.of("区块加载预算耗尽（" + maxChunkLoads + "），未找到安全点"));
            }
        }

        return failed(dimension, scanner, radiusReached, ResolveResult.Status.UNSAFE,
                List.of("在半径 " + maxRadius + " 内未找到安全点"));
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
