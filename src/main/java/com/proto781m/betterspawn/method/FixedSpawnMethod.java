package com.proto781m.betterspawn.method;

import com.proto781m.betterspawn.api.ResolveResult;
import com.proto781m.betterspawn.api.SpawnContext;
import com.proto781m.betterspawn.api.SpawnMethod;
import com.proto781m.betterspawn.resolver.SpawnSafetyValidator;
import com.proto781m.betterspawn.resolver.TerrainScanner;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;

/**
 * 定点坐标方法（末地）：直接使用 JSON 指定的 {@code x, y, z}。
 *
 * <p>参数：
 * <ul>
 *   <li>{@code x}, {@code y}, {@code z}：目标脚部坐标</li>
 *   <li>{@code angle}：朝向角度，默认随机</li>
 *   <li>{@code safe_check}：是否做安全校验，默认 true；不通过则交给 fallback</li>
 * </ul>
 */
public class FixedSpawnMethod implements SpawnMethod {

    @Override
    public String id() {
        return "fixed";
    }

    @Override
    public ResolveResult resolve(SpawnContext ctx) {
        ServerLevel level = ctx.level();
        ResourceKey<Level> dimension = ctx.rule().dimensionKey();
        RandomSource rng = RandomSource.create(ctx.seed());

        int x = ctx.rule().getInt("x", 0);
        int y = ctx.rule().getInt("y", 64);
        int z = ctx.rule().getInt("z", 0);
        float angle = (float) ctx.rule().getDouble("angle", rng.nextFloat() * 360.0);
        boolean safeCheck = ctx.rule().getBool("safe_check", true);

        BlockPos pos = new BlockPos(x, y, z);
        TerrainScanner scanner = new TerrainScanner(level, ctx.deadlineMillis(), 1);
        if (!scanner.ensureChunk(x, z)) {
            return new ResolveResult(null, dimension, angle, id(), false, 0L,
                    scanner.samples(), 0, ResolveResult.Status.UNSAFE,
                    List.of("Could not load target chunk [" + (x >> 4) + ", " + (z >> 4) + "]"));
        }

        if (safeCheck && !SpawnSafetyValidator.isSafe(level, pos)) {
            return new ResolveResult(pos, dimension, angle, id(), false, 0L,
                    scanner.samples(), 0, ResolveResult.Status.UNSAFE,
                    SpawnSafetyValidator.validate(level, pos));
        }

        List<String> warnings = safeCheck ? SpawnSafetyValidator.validate(level, pos) : List.of();
        return new ResolveResult(pos, dimension, angle, id(), false, 0L,
                scanner.samples(), 0, ResolveResult.Status.OK, warnings);
    }
}
