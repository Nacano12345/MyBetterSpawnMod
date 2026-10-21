package com.proto781m.betterspawn.data;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

/**
 * 单个玩家重生点槽。
 *
 * <p>{@code pos} 的含义取决于类型：
 * <ul>
 *   <li>{@link SpawnSlotType#COMMAND}：最终站立坐标；</li>
 *   <li>{@link SpawnSlotType#BED} / {@link SpawnSlotType#ANCHOR}：锚点方块位置，
 *       重生时再围绕它找安全站立点。</li>
 * </ul>
 *
 * @param valid 运行时校验结果（床是否还在、锚是否有电等），解析链据此决定是否采用
 */
public record SpawnSlot(
        SpawnSlotType type,
        ResourceKey<Level> dimension,
        BlockPos pos,
        float angle,
        boolean valid
) {

    /** 构造一个“有效”的槽。 */
    public static SpawnSlot valid(SpawnSlotType type, ResourceKey<Level> dimension, BlockPos pos, float angle) {
        return new SpawnSlot(type, dimension, pos, angle, true);
    }

    public SpawnSlot withValid(boolean valid) {
        return new SpawnSlot(type, dimension, pos, angle, valid);
    }
}
