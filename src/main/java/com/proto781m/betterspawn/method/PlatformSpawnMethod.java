package com.proto781m.betterspawn.method;

import com.proto781m.betterspawn.api.ResolveResult;
import java.util.List;
import com.proto781m.betterspawn.api.SpawnContext;
import com.proto781m.betterspawn.api.SpawnMethod;
import com.proto781m.betterspawn.resolver.ChunkSearchHelper;
import com.proto781m.betterspawn.resolver.SpawnSafetyValidator;
import com.proto781m.betterspawn.util.PlatformBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.Level;
import net.minecraft.core.SectionPos;

/**
 * 平台方法：在指定坐标直接搭建一个平台，保底出生点。
 *
 * <p>参数：
 * <ul>
 *   <li>{@code x}, {@code y}, {@code z}：平台中心地面坐标（默认 0, 64, 0）</li>
 *   <li>{@code size}：平台边长（默认 3）</li>
 *   <li>{@code material}：方块 id（默认 "minecraft:stone"）</li>
 * </ul>
 *
 * <p>注意：本方法 <b>总是会修改世界</b>（搭建平台）。调用方必须确保
 * {@link SpawnContext#simulate()} 为 false，或在 simulate 模式下跳过搭建。
 */
public class PlatformSpawnMethod implements SpawnMethod {

    /** 单例实例，供 DimensionSpawnInitializer 等外部调用。 */
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
        long deadline = ctx.deadlineMillis();
        RandomSource rng = RandomSource.create(ctx.seed());

        int x = ctx.rule().getInt("x", 0);
        int y = ctx.rule().getInt("y", 64);
        int z = ctx.rule().getInt("z", 0);
        int size = ctx.rule().getInt("size", 3);
        String materialId = ctx.rule().getString("material", "minecraft:stone");

        BlockPos center = new BlockPos(x, y, z);

        // 平台方法需要确保区块存在（用于放置方块），使用强制加载
        var chunk = level.getChunk(SectionPos.blockToSectionCoord(center.getX()), SectionPos.blockToSectionCoord(center.getZ()));
        if (chunk == null) {
            return new ResolveResult(null, ctx.rule().dimensionKey(), 0, id(), false, 0, 0, 0,
                    ResolveResult.Status.UNSAFE, List.of("无法加载平台区块"));
        }

        BlockState state;
        try {
            Block block = BuiltInRegistries.BLOCK.get(ResourceLocation.parse(materialId));
            state = block.defaultBlockState();
        } catch (Exception ex) {
            state = Blocks.STONE.defaultBlockState();
        }

        if (!ctx.simulate()) {
            PlatformBuilder.build(level, center, size, state);
        }

        BlockPos standPos = center.above();
        List<String> warnings = List.of("平台方法生成平台: " + materialId + " size=" + size);
        return new ResolveResult(standPos, ctx.rule().dimensionKey(), rng.nextFloat() * 360.0F, id(), false,
                0, 1, 0, ResolveResult.Status.OK, warnings);
    }
}