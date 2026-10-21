package com.proto781m.betterspawn.api;

import com.proto781m.betterspawn.template.SpawnRule;
import net.minecraft.server.level.ServerLevel;

/**
 * 出生点解析上下文。
 *
 * @param level          目标维度
 * @param rule           该维度的 JSON 模板规则
 * @param seed           随机种子（复现用，{@code /gotodim --seed} 可覆盖）
 * @param simulate       true 表示沙盒模式（{@code /gotodim simulate}），解析器不得写入任何持久化数据
 * @param deadlineMillis 解析截止时间戳（毫秒），超时返回 {@link ResolveResult.Status#TIMEOUT}
 * @param trace          搜索诊断轨迹
 */
public record SpawnContext(
        ServerLevel level,
        SpawnRule rule,
        long seed,
        boolean simulate,
        long deadlineMillis,
        SearchTrace trace
) {
    public boolean timedOut() {
        return System.currentTimeMillis() > deadlineMillis;
    }
}
