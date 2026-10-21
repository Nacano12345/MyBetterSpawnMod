package com.proto781m.betterspawn.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import com.proto781m.betterspawn.BetterSpawn;
import com.proto781m.betterspawn.data.PlayerSpawnData;
import com.proto781m.betterspawn.data.WorldSpawnData;
import com.proto781m.betterspawn.template.SpawnTemplateManager;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.commands.arguments.coordinates.Vec3Argument;
import net.minecraft.commands.arguments.ResourceKeyArgument;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.Collection;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * /betterspawn worldspawn 子命令（扩展版 setworldspawn）。
 *
 * <p>设计原则：
 * <ul>
 *   <li>原版 {@code /setworldspawn} 保持兼容（仅设置当前维度全局默认点）；</li>
 *   <li>扩展功能全部挂在 {@code /betterspawn worldspawn} 下，避免冲突；</li>
 *   <li>支持维度、玩家、全局三层数据的增删改查。</li>
 *   <li>参数可省略：维度默认当前维度，坐标默认玩家脚部坐标，角度默认当前朝向。</li>
 * </ul>
 */
public final class SetWorldSpawnCommand {

    private SetWorldSpawnCommand() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal("worldspawn")
                .requires(src -> src.hasPermission(2));

        // /betterspawn worldspawn global [<dimension>] [<pos>] [<angle>]
        // 全部参数可选：维度默认当前维度，坐标默认玩家脚部坐标，角度默认当前朝向
        root.then(Commands.literal("global")
                .executes(ctx -> execGlobal(ctx, getCurrentDimension(ctx), getCurrentFeetPos(ctx), getCurrentYaw(ctx)))
                .then(argumentDimension("dimension")
                        .executes(ctx -> execGlobal(ctx, ctx.getArgument("dimension", ResourceKey.class), getCurrentFeetPos(ctx), getCurrentYaw(ctx)))
                        .then(Commands.argument("pos", Vec3Argument.vec3())
                                .executes(ctx -> execGlobal(ctx, ctx.getArgument("dimension", ResourceKey.class),
                                        getVec3AsFeetPos(ctx.getArgument("pos", Vec3.class)), getCurrentYaw(ctx)))
                                .then(Commands.argument("angle", FloatArgumentType.floatArg())
                                        .executes(ctx -> execGlobal(ctx, ctx.getArgument("dimension", ResourceKey.class),
                                                getVec3AsFeetPos(ctx.getArgument("pos", Vec3.class)),
                                                FloatArgumentType.getFloat(ctx, "angle")))))));

        // /betterspawn worldspawn player <targets> [<dimension>] [<pos>] [<angle>]
        root.then(Commands.literal("player")
                .then(Commands.argument("targets", EntityArgument.players())
                        .executes(ctx -> execPlayer(ctx, EntityArgument.getPlayers(ctx, "targets"),
                                getCurrentDimension(ctx), getCurrentFeetPos(ctx), getCurrentYaw(ctx)))
                        .then(argumentDimension("dimension")
                                .executes(ctx -> execPlayer(ctx, EntityArgument.getPlayers(ctx, "targets"),
                                        ctx.getArgument("dimension", ResourceKey.class), getCurrentFeetPos(ctx), getCurrentYaw(ctx)))
                                .then(Commands.argument("pos", Vec3Argument.vec3())
                                        .executes(ctx -> execPlayer(ctx, EntityArgument.getPlayers(ctx, "targets"),
                                                ctx.getArgument("dimension", ResourceKey.class),
                                                getVec3AsFeetPos(ctx.getArgument("pos", Vec3.class)), getCurrentYaw(ctx)))
                                        .then(Commands.argument("angle", FloatArgumentType.floatArg())
                                                .executes(ctx -> execPlayer(ctx, EntityArgument.getPlayers(ctx, "targets"),
                                                        ctx.getArgument("dimension", ResourceKey.class),
                                                        getVec3AsFeetPos(ctx.getArgument("pos", Vec3.class)),
                                                        FloatArgumentType.getFloat(ctx, "angle"))))))));

        // /betterspawn worldspawn clear [<dimension>] [<targets>]
        root.then(Commands.literal("clear")
                .executes(ctx -> execClearGlobal(ctx, getCurrentDimension(ctx)))
                .then(argumentDimension("dimension")
                        .executes(ctx -> execClearGlobal(ctx, ctx.getArgument("dimension", ResourceKey.class)))
                        .then(Commands.argument("targets", EntityArgument.players())
                                .executes(ctx -> execClearPlayer(ctx, ctx.getArgument("dimension", ResourceKey.class),
                                        EntityArgument.getPlayers(ctx, "targets"))))));

        // /betterspawn worldspawn query [<dimension>] [<target>]
        root.then(Commands.literal("query")
                .executes(ctx -> execQueryGlobal(ctx, getCurrentDimension(ctx)))
                .then(argumentDimension("dimension")
                        .executes(ctx -> execQueryGlobal(ctx, ctx.getArgument("dimension", ResourceKey.class)))
                        .then(Commands.argument("target", EntityArgument.player())
                                .executes(ctx -> execQueryPlayer(ctx, ctx.getArgument("dimension", ResourceKey.class),
                                        EntityArgument.getPlayer(ctx, "target"))))));

        // /betterspawn worldspawn reload
        root.then(Commands.literal("reload")
                .executes(ctx -> {
                    SpawnTemplateManager.reload(java.util.Map.of());
                    ctx.getSource().sendSuccess(() -> Component.literal("§aBetterSpawn: 模板缓存已清空（完整 reload 请用 /reload）"), true);
                    return 1;
                }));

        // /betterspawn worldspawn list - 列出所有已加载的模板
        root.then(Commands.literal("list")
                .executes(ctx -> {
                    var templates = SpawnTemplateManager.all();
                    if (templates.isEmpty()) {
                        ctx.getSource().sendFailure(Component.literal("§c没有加载任何出生点模板"));
                        return 0;
                    }
                    ctx.getSource().sendSystemMessage(Component.literal("§e=== BetterSpawn 已加载模板 ==="));
                    for (var rule : templates) {
                        ctx.getSource().sendSystemMessage(Component.literal(
                                "  §b" + rule.dimension() + "§r: method=" + rule.method() + ", fallback=" + rule.fallback()));
                    }
                    return 1;
                }));

        dispatcher.register(Commands.literal("betterspawn").then(root));
    }

    /** 维度参数（可选，自动补全已注册维度） */
    private static RequiredArgumentBuilder<CommandSourceStack, ResourceKey<Level>> argumentDimension(String name) {
        return Commands.argument(name, ResourceKeyArgument.key(Registries.DIMENSION))
                .suggests((ctx, builder) -> suggestDimensions(ctx.getSource(), builder));
    }

    private static ResourceKey<Level> getCurrentDimension(CommandContext<CommandSourceStack> ctx) {
        return ctx.getSource().getLevel().dimension();
    }

    private static BlockPos getCurrentFeetPos(CommandContext<CommandSourceStack> ctx) {
        var entity = ctx.getSource().getEntity();
        if (entity != null) {
            return BlockPos.containing(entity.getX(), entity.getY(), entity.getZ());
        }
        return BlockPos.ZERO;
    }

    private static BlockPos getVec3AsFeetPos(Vec3 vec) {
        return BlockPos.containing(vec.x, vec.y, vec.z);
    }

    private static float getCurrentYaw(CommandContext<CommandSourceStack> ctx) {
        var entity = ctx.getSource().getEntity();
        return entity != null ? entity.getYRot() : 0f;
    }

    private static CompletableFuture<Suggestions> suggestDimensions(CommandSourceStack src, SuggestionsBuilder builder) {
        MinecraftServer server = src.getServer();
        if (server != null) {
            for (ResourceLocation loc : server.registryAccess().registryOrThrow(Registries.DIMENSION).keySet()) {
                builder.suggest(loc.toString());
            }
        }
        return builder.buildFuture();
    }

    // ===== 执行方法 =====

    private static int execGlobal(CommandContext<CommandSourceStack> ctx, ResourceKey<Level> dimension,
                                  BlockPos pos, float angle) throws CommandSyntaxException {
        MinecraftServer server = ctx.getSource().getServer();
        ServerLevel level = server.getLevel(dimension);
        if (level == null) {
            ctx.getSource().sendFailure(Component.literal("§c维度不存在: " + dimension.location()));
            return 0;
        }
        WorldSpawnData.get(server).set(dimension, pos, angle, level);
        ctx.getSource().sendSuccess(() -> Component.literal(
                "§a已设置 " + dimension.location() + " 全局默认出生点: " + pos + " angle=" + angle), true);
        return 1;
    }

    private static int execPlayer(CommandContext<CommandSourceStack> ctx, Collection<ServerPlayer> targets,
                                  ResourceKey<Level> dimension, BlockPos pos, float angle) throws CommandSyntaxException {
        for (ServerPlayer target : targets) {
            PlayerSpawnData.setDimensionSpawn(target, dimension, pos, angle);
        }
        ctx.getSource().sendSuccess(() -> Component.literal(
                "§a已设置 " + targets.size() + " 个玩家在 " + dimension.location() + " 的个人世界出生点: " + pos), true);
        return 1;
    }

    private static int execClearGlobal(CommandContext<CommandSourceStack> ctx, ResourceKey<Level> dimension) throws CommandSyntaxException {
        WorldSpawnData.get(ctx.getSource().getServer()).clear(dimension);
        ctx.getSource().sendSuccess(() -> Component.literal("§a已清除 " + dimension.location() + " 的全局默认出生点"), true);
        return 1;
    }

    private static int execClearPlayer(CommandContext<CommandSourceStack> ctx, ResourceKey<Level> dimension,
                                       Collection<ServerPlayer> targets) throws CommandSyntaxException {
        for (ServerPlayer target : targets) {
            PlayerSpawnData.clearDimensionSpawn(target, dimension);
        }
        ctx.getSource().sendSuccess(() -> Component.literal("§a已清除 " + targets.size() + " 个玩家在 " + dimension.location() + " 的个人世界出生点"), true);
        return 1;
    }

    private static int execQueryGlobal(CommandContext<CommandSourceStack> ctx, ResourceKey<Level> dimension) throws CommandSyntaxException {
        var opt = WorldSpawnData.get(ctx.getSource().getServer()).get(dimension);
        if (opt.isPresent()) {
            var p = opt.get();
            ctx.getSource().sendSuccess(() -> Component.literal(
                    "§e" + dimension.location() + " 全局默认出生点: " + p.pos() + " angle=" + p.angle()), false);
        } else {
            ctx.getSource().sendFailure(Component.literal("§c" + dimension.location() + " 无全局默认出生点"));
        }
        return 1;
    }

    private static int execQueryPlayer(CommandContext<CommandSourceStack> ctx, ResourceKey<Level> dimension,
                                       ServerPlayer target) throws CommandSyntaxException {
        var opt = PlayerSpawnData.getDimensionSpawn(target, dimension);
        if (opt.isPresent()) {
            var p = opt.get();
            ctx.getSource().sendSuccess(() -> Component.literal(
                    "§e" + target.getScoreboardName() + " 在 " + dimension.location() + " 的个人世界出生点: " + p.pos() + " angle=" + p.angle()), false);
        } else {
            ctx.getSource().sendSuccess(() -> Component.literal("§c" + target.getScoreboardName() + " 在 " + dimension.location() + " 无个人世界出生点"), false);
        }
        return 1;
    }
}