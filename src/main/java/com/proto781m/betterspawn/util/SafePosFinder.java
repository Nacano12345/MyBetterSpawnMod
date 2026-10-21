package com.proto781m.betterspawn.util;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 出生点安全位置判定工具。
 *
 * <p>所有出生方法在返回坐标前，都应通过这里做一次安全校验，确保玩家不会
 * 卡在方块里、掉进岩浆/水里，或站在虚空上。
 *
 * <p>判定规则：
 * <ul>
 *   <li>脚下（pos 下方）必须是能站立的实体方块，且不是流体；</li>
 *   <li>脚部与头部所在格必须是空气或可替换方块（草、花等）；</li>
 *   <li>脚部与头部所在格不能是流体（水/岩浆）。</li>
 * </ul>
 */
public final class SafePosFinder {

    private SafePosFinder() {
    }

    /** 判断 {@code ground} 处是否能作为站立地面（顶面稳固且非流体）。 */
    public static boolean isSafeGround(ServerLevel level, BlockPos ground) {
        BlockState state = level.getBlockState(ground);
        return state.isFaceSturdy(level, ground, Direction.UP) && state.getFluidState().isEmpty();
    }

    /** 判断 {@code pos} 是否能作为玩家站立点（脚部位置）。 */
    public static boolean isSafeStandingPosition(ServerLevel level, BlockPos pos) {
        if (!isSafeGround(level, pos.below())) {
            return false;
        }
        BlockState feet = level.getBlockState(pos);
        BlockState head = level.getBlockState(pos.above());
        if (!isPassable(feet) || !isPassable(head)) {
            return false;
        }
        return feet.getFluidState().isEmpty() && head.getFluidState().isEmpty();
    }

    /** 是否可通过（空气或可被替换的方块，如草、花、雪）。 */
    public static boolean isPassable(BlockState state) {
        return state.isAir() || state.canBeReplaced();
    }

    /**
     * 以 {@code center} 为中心，在指定半径内寻找第一个安全站立点。
     * 搜索顺序：先上下微调，再按曼哈顿距离扩展，适合定点方法的安全修正。
     *
     * @return 找到的安全点，找不到返回 {@code null}
     */
    public static BlockPos findSafeAround(ServerLevel level, BlockPos center, int maxRadius) {
        if (isSafeStandingPosition(level, center)) {
            return center;
        }
        // 先尝试在中心附近上下找
        for (int dy = 1; dy <= 4; dy++) {
            if (isSafeStandingPosition(level, center.above(dy))) {
                return center.above(dy);
            }
            if (isSafeStandingPosition(level, center.below(dy))) {
                return center.below(dy);
            }
        }
        // 再按半径向外找
        for (int r = 1; r <= maxRadius; r++) {
            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    if (Math.abs(dx) != r && Math.abs(dz) != r) {
                        continue; // 只检查外环
                    }
                    BlockPos candidate = center.offset(dx, 0, dz);
                    if (isSafeStandingPosition(level, candidate)) {
                        return candidate;
                    }
                }
            }
        }
        return null;
    }
}
