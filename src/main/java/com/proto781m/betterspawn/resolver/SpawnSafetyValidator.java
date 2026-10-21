package com.proto781m.betterspawn.resolver;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import com.proto781m.betterspawn.util.SafePosFinder;

/**
 * 出生点安全校验器：统一判定与告警。
 *
 * <p>所有方法最终都应调用 {@link #validate} 做二次确认，避免把玩家丢进岩浆、
 * 卡里、或悬空。
 */
public final class SpawnSafetyValidator {

    private SpawnSafetyValidator() {
    }

    public static boolean isSafe(ServerLevel level, BlockPos pos) {
        return SafePosFinder.isSafeStandingPosition(level, pos);
    }

    /**
     * 校验出生点并返回警告列表（空列表表示无警告）。
     */
    public static List<String> validate(ServerLevel level, BlockPos pos) {
        List<String> warnings = new ArrayList<>();
        if (pos == null) {
            warnings.add("坐标为空");
            return warnings;
        }
        if (!SafePosFinder.isSafeGround(level, pos.below())) {
            warnings.add("脚下不是稳固方块或处于流体中");
        }
        if (!SafePosFinder.isPassable(level.getBlockState(pos))) {
            warnings.add("脚部位置被方块占用");
        }
        if (!SafePosFinder.isPassable(level.getBlockState(pos.above()))) {
            warnings.add("头部位置被方块占用");
        }
        // 检查头顶是否有悬空方块遮挡视野（提示，不阻断）
        int blockedAbove = 0;
        for (int dy = 1; dy <= 4; dy++) {
            if (!SafePosFinder.isPassable(level.getBlockState(pos.above(dy)))) {
                blockedAbove++;
            }
        }
        if (blockedAbove > 0) {
            warnings.add("上方有 " + blockedAbove + " 格悬空方块，可能遮挡视野");
        }
        return warnings;
    }
}
