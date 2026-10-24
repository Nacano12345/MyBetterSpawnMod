package com.proto781m.betterspawn.util;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/**
 * Cross-dimension teleport wrapper.
 *
 * <p>Version-specific teleport APIs are centralized here. Future support for the
 * 1.21.2+ {@code TeleportTransition} should only require changes to this class.
 */
public final class TeleportHelper {

    private TeleportHelper() {
    }

    /** Teleports the player to the given coordinates in the target dimension. */
    public static void teleport(ServerPlayer player, ServerLevel target, double x, double y, double z) {
        player.teleportTo(target, x, y, z, player.getYRot(), player.getXRot());
    }

    /** Teleports the player to the given coordinates in the target dimension with a
     * specified facing angle. */
    public static void teleport(ServerPlayer player, ServerLevel target, double x, double y, double z, float yaw, float pitch) {
        player.teleportTo(target, x, y, z, yaw, pitch);
    }
}
