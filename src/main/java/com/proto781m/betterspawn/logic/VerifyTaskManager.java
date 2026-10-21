package com.proto781m.betterspawn.logic;

import com.proto781m.betterspawn.BetterSpawn;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * {@code /gotodim verify} 的延迟校验任务。
 *
 * <p>传送后等待约 3 秒，检查玩家是否落地、是否在液体/虚空中、是否偏离目标点。
 * 不使用 {@code Thread.sleep}，避免阻塞服务器线程。
 */
@EventBusSubscriber(modid = BetterSpawn.MODID, bus = EventBusSubscriber.Bus.GAME)
public final class VerifyTaskManager {

    private static final List<Task> TASKS = new ArrayList<>();

    private VerifyTaskManager() {
    }

    private record Task(ServerPlayer player, ServerLevel level, BlockPos expected, int ticksLeft) {
        Task tick() {
            return new Task(player, level, expected, ticksLeft - 1);
        }
    }

    public static void schedule(ServerPlayer player, ServerLevel level, BlockPos expected, int delayTicks) {
        TASKS.add(new Task(player, level, expected, Math.max(1, delayTicks)));
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        if (TASKS.isEmpty()) {
            return;
        }
        Iterator<Task> iterator = TASKS.iterator();
        List<Task> remaining = new ArrayList<>();
        while (iterator.hasNext()) {
            Task task = iterator.next();
            Task next = task.tick();
            if (next.ticksLeft() <= 0) {
                try {
                    verify(task);
                } catch (Exception ex) {
                    BetterSpawn.LOGGER.warn("Verify task failed: {}", ex.toString());
                }
            } else {
                remaining.add(next);
            }
        }
        TASKS.clear();
        TASKS.addAll(remaining);
    }

    private static void verify(Task task) {
        ServerPlayer player = task.player();
        ServerLevel level = task.level();
        if (player == null || !player.isAlive() || player.isRemoved()) {
            return;
        }
        if (player.level() != level) {
            player.sendSystemMessage(Component.translatable("betterspawn.msg.verify.wrong_dimension"));
            return;
        }

        BlockPos expected = task.expected();
        BlockPos groundPos = expected.below();
        BlockState ground = level.getBlockState(groundPos);
        boolean solidGround = ground.getFluidState().isEmpty()
                && !ground.getCollisionShape(level, groundPos).isEmpty();
        boolean inLiquid = player.isInFluidType();
        boolean inVoid = player.getY() < level.getMinBuildHeight() + 1;
        boolean atTarget = Math.abs(player.getY() - expected.getY()) <= 1.5;
        // 跨维度传送后 onGround 标志可能延迟；脚下有实体方块且高度正确也算成功
        boolean onGround = player.onGround() || (solidGround && atTarget);
        double distance = player.position().distanceTo(Vec3.atBottomCenterOf(expected));

        net.minecraft.network.chat.MutableComponent message =
                Component.translatable("betterspawn.msg.verify.header")
                        .append(Component.translatable(onGround
                                ? "betterspawn.msg.verify.on_ground"
                                : "betterspawn.msg.verify.not_on_ground"))
                        .append(Component.translatable(solidGround
                                ? "betterspawn.msg.verify.solid_ground"
                                : "betterspawn.msg.verify.no_solid_ground"));
        if (inLiquid) {
            message.append(Component.translatable("betterspawn.msg.verify.liquid"));
        }
        if (inVoid) {
            message.append(Component.translatable("betterspawn.msg.verify.void"));
        }
        message.append(Component.translatable("betterspawn.msg.verify.distance",
                String.format("%.2f", distance)));
        message.append(Component.translatable("betterspawn.msg.verify.y",
                String.format("%.1f", player.getY())));

        player.sendSystemMessage(message);
        BetterSpawn.diag("Verify {} at {} -> y={}, onGround={}, solidGround={}, liquid={}, distance={}",
                player.getScoreboardName(), expected, player.getY(), onGround, solidGround, inLiquid,
                String.format("%.2f", distance));
    }
}
