package com.proto781m.betterspawn.api;

import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

/**
 * 出生点解析结果。
 *
 * @param pos             解析出的出生点（脚部坐标），失败时可能为 {@code null}
 * @param angle           朝向角度
 * @param usedMethod      实际使用的方法 id
 * @param fallbackUsed    是否触发了 fallback
 * @param elapsedMillis   解析耗时
 * @param samples         采样点数量
 * @param maxRadiusReached 搜索触及的最大半径
 * @param status          解析状态
 * @param warnings        警告信息（如“Y 上方有悬空方块”）
 */
public record ResolveResult(
        BlockPos pos,
        ResourceKey<Level> dimension,
        float angle,
        String usedMethod,
        boolean fallbackUsed,
        long elapsedMillis,
        int samples,
        int maxRadiusReached,
        Status status,
        List<String> warnings
) {

    public enum Status {
        /** 成功找到安全出生点。 */
        OK,
        /** 搜索超时。 */
        TIMEOUT,
        /** 找到坐标但安全校验不通过。 */
        UNSAFE,
        /** 走了 fallback 方法并找到位置。 */
        FALLBACK,
        /** 彻底失败（无坐标）。 */
        EMPTY
    }

    public static ResolveResult empty(String method, Status status) {
        return new ResolveResult(null, null, 0.0F, method, false, 0L, 0, 0, status, List.of());
    }

    public static ResolveResult timeout(String method) {
        return new ResolveResult(null, null, 0.0F, method, false, 0L, 0, 0, Status.TIMEOUT, List.of("搜索超时"));
    }
}
