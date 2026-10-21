package com.proto781m.betterspawn.method;

import com.proto781m.betterspawn.api.ResolveResult;
import com.proto781m.betterspawn.api.SpawnContext;
import com.proto781m.betterspawn.api.SpawnMethod;
import com.proto781m.betterspawn.resolver.TerrainScanner;
import com.proto781m.betterspawn.util.PlatformBuilder;
import com.proto781m.betterspawn.util.SafePosFinder;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 平台方法：在指定坐标搭建一个平台，作为最后的保底出生点。
 *
 * <p>参数：
 * <ul>
 *   <li>{@code x}, {@code y}, {@code z}：平台中心地面坐标，默认 0,64,0</li>
 *   <li>{@code size}：平台边长（奇数更对称），默认 3</li>
 *   <li>{@code material}：方块 id，默认 {@code minecraft:stone}</li>
 * </ul>
 *
 * <p>本方法在 {@code simulate=true} 时只返回坐标，不修改世界。
 */
public class PlatformSpawnMethod implements SpawnMethod {

    /** 单例，供维度初始化器在模板完全失败时直接调用。 */
    public static final PlatformSpawnMethod INSTANCE = new PlatformSpawnMethod();

    private PlatformSpawnMethod() {
    }

    @Override
    public String id() {
        return "platform";
    }

    @Override
    public ResolveResult resolve(SpawnContext ctx) {
        ServerLevel level = ctx.level();
        ResourceKey<Level> dimension = level.dimension();
        RandomSource rng = RandomSource.create(ctx.seed());

        // 模板缺失时也要能工作：构造一个默认平台规则，而不是直接 NPE
        var rule = ctx.rule() != null ? ctx.rule() : defaultRule(level);

        int x = rule.getInt("x", 0);
        int y = rule.getInt("y", 64);
        int z = rule.getInt("z", 0);
        int size = Math.max(1, rule.getInt("size", 3));
        if (size % 2 == 0) {
            size++; // 保持中心对称
        }
        String materialId = rule.getString("material", "minecraft:stone");

        int minY = level.getMinBuildHeight() + 1;
        int maxY = level.getMaxBuildHeight() - 3;
        BlockPos center = new BlockPos(x, Math.max(minY, Math.min(maxY, y)), z);

        TerrainScanner scanner = new TerrainScanner(level, ctx.deadlineMillis(), 1);
        if (!scanner.ensureChunk(center.getX(), center.getZ())) {
            return new ResolveResult(null, dimension, 0.0F, id(), false, 0L,
                    scanner.samples(), 0, ResolveResult.Status.UNSAFE,
                    List.of("无法加载平台所在区块 [" + (center.getX() >> 4) + ", " + (center.getZ() >> 4) + "]"));
        }

        BlockState state = PlatformBuilder.resolveState(materialId);
        List<String> warnings = new ArrayList<>();
        warnings.add("平台方法：material=" + materialId + ", size=" + size);

        if (ctx.simulate()) {
            warnings.add("simulate 模式：未实际搭建平台");
        } else {
            PlatformBuilder.build(level, center, size, state);
            if (!SafePosFinder.isSafeStandingPosition(level, center.above())) {
                warnings.add("平台搭建后落脚点仍不安全，请检查该维度的高度限制");
            }
        }

        BlockPos standPos = center.above();
        return new ResolveResult(standPos, dimension, rng.nextFloat() * 360.0F, id(), false, 0L,
                scanner.samples(), 0, ResolveResult.Status.OK, warnings);
    }

    private static com.proto781m.betterspawn.template.SpawnRule defaultRule(ServerLevel level) {
        return new com.proto781m.betterspawn.template.SpawnRule(
                level.dimension().location(), "platform",
                Map.of("x", 0, "y", 64, "z", 0, "size", 3, "material", "minecraft:stone"),
                null, Map.of());
    }
}
