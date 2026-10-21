package com.proto781m.betterspawn.util;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/**
 * 跨维度传送封装。
 *
 * <p>把版本相关的传送 API 集中到这里，未来适配 1.21.2+ 的
 * {@code TeleportTransition} 时只需改这一个类。
 */
public final class TeleportHelper {

    private TeleportHelper() {
    }

    /** 传送玩家到目标维度的指定坐标。 */
    public static void teleport(ServerPlayer player, ServerLevel target, double x, double y, double z) {
        player.teleportTo(target, x, y, z, player.getYRot(), player.getXRot());
    }

    /** 传送玩家到目标维度的指定坐标，并指定朝向。 */
    public static void teleport(ServerPlayer player, ServerLevel target, double x, double y, double z, float yaw, float pitch) {
        player.teleportTo(target, x, y, z, yaw, pitch);
    }
}
