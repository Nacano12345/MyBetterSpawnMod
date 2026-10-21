package com.proto781m.betterspawn.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import com.proto781m.betterspawn.data.PlayerSpawnData;
import com.proto781m.betterspawn.data.WorldSpawnData;
import com.proto781m.betterspawn.logic.DimensionSpawnInitializer;
import com.proto781m.betterspawn.template.SpawnTemplateManager;
import java.util.Collection;
import java.util.concurrent.CompletableFuture;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.ResourceKeyArgument;
import net.minecraft.commands.arguments.coordinates.Vec3Argument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * {@code /betterspawn worldspawn} 子命令（setworldspawn 的扩展版）。
 *
 * <p>参数可省略：维度默认当前维度，坐标默认玩家脚部位置，角度默认当前朝向。
 *
 * <pre>
 * /betterspawn worldspawn global [&lt;pos&gt; | &lt;dimension&gt;] [&lt;pos&gt;] [&lt;angle&gt;]
 * /betterspawn worldspawn player &lt;targets&gt; [&lt;pos&gt; | &lt;dimension&gt;] [&lt;pos&gt;] [&lt;angle&gt;]
 * /betterspawn worldspawn clear [&lt;dimension&gt;] [&lt;targets&gt;]
 * /betterspawn worldspawn query [&lt;dimension&gt;] [&lt;target&gt;]
 * /betterspawn worldspawn regenerate [&lt;dimension&gt;]
 * /betterspawn worldspawn list
 * /betterspawn worldspawn reload
 * </pre>
 */
public final class SetWorldSpawnCommand {

    private SetWorldSpawnCommand() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal("worldspawn")
                .requires(src -> src.hasPermission(2));

        // /betterspawn worldspawn global [<pos>] [<angle>] [<dimension>]
        root.then(Commands.literal("global")
                .executes(ctx -> execGlobal(ctx, currentDimension(ctx), currentFeet(ctx), currentYaw(ctx)))
                .then(Commands.argument("pos", Vec3Argument.vec3())
                        .suggests((ctx, b) -> {
                            b.suggest("~ ~ ~");
                            return b.buildFuture();
                        })
                        .executes(ctx -> execGlobal(ctx, currentDimension(ctx), vec(ctx), currentYaw(ctx)))
                        .then(Commands.argument("angle", FloatArgumentType.floatArg())
                                .executes(ctx -> execGlobal(ctx, currentDimension(ctx), vec(ctx),
                                        FloatArgumentType.getFloat(ctx, "angle")))
                                .then(argumentDimension("dimension")
                                        .executes(ctx -> execGlobal(ctx, dimension(ctx, "dimension"),
                                                vec(ctx), FloatArgumentType.getFloat(ctx, "angle")))))));

        // /betterspawn worldspawn player <targets> [<pos>] [<angle>] [<dimension>]
        root.then(Commands.literal("player")
                .then(Commands.argument("targets", EntityArgument.players())
                        .executes(ctx -> execPlayer(ctx, targets(ctx), null, null, null))
                        .then(Commands.argument("pos", Vec3Argument.vec3())
                                .suggests((ctx, b) -> {
                                    b.suggest("~ ~ ~");
                                    return b.buildFuture();
                                })
                                .executes(ctx -> execPlayer(ctx, targets(ctx), null, vec(ctx), null))
                                .then(Commands.argument("angle", FloatArgumentType.floatArg())
                                        .executes(ctx -> execPlayer(ctx, targets(ctx), null, vec(ctx),
                                                FloatArgumentType.getFloat(ctx, "angle")))
                                        .then(argumentDimension("dimension")
                                                .executes(ctx -> execPlayer(ctx, targets(ctx),
                                                        dimension(ctx, "dimension"), vec(ctx),
                                                        FloatArgumentType.getFloat(ctx, "angle"))))))));

        // clear [dimension] [targets]
        root.then(Commands.literal("clear")
                .executes(ctx -> execClearGlobal(ctx, currentDimension(ctx)))
                .then(argumentDimension("dimension")
                        .executes(ctx -> execClearGlobal(ctx, dimension(ctx, "dimension")))
                        .then(Commands.argument("targets", EntityArgument.players())
                                .executes(ctx -> execClearPlayer(ctx, dimension(ctx, "dimension"),
                                        targets(ctx))))));

        // query [dimension] [target]
        root.then(Commands.literal("query")
                .executes(ctx -> execQueryGlobal(ctx, currentDimension(ctx)))
                .then(argumentDimension("dimension")
                        .executes(ctx -> execQueryGlobal(ctx, dimension(ctx, "dimension")))
                        .then(Commands.argument("target", EntityArgument.player())
                                .executes(ctx -> execQueryPlayer(ctx, dimension(ctx, "dimension"),
                                        EntityArgument.getPlayer(ctx, "target"))))));

        // regenerate [dimension]
        root.then(Commands.literal("regenerate")
                .executes(ctx -> execRegenerate(ctx, currentDimension(ctx)))
                .then(argumentDimension("dimension")
                        .executes(ctx -> execRegenerate(ctx, dimension(ctx, "dimension")))));

        // spawn_dimension [dimension] —— 实验性：新玩家首次进服的目标维度
        root.then(Commands.literal("spawn_dimension")
                .executes(ctx -> execQuerySpawnDimension(ctx))
                .then(argumentDimension("dimension")
                        .executes(ctx -> execSetSpawnDimension(ctx, dimension(ctx, "dimension")))));

        // list
        root.then(Commands.literal("list").executes(ctx -> {
            var templates = SpawnTemplateManager.all();
            if (templates.isEmpty()) {
                ctx.getSource().sendFailure(Component.literal("§c没有加载任何出生点模板"));
                return 0;
            }
            ctx.getSource().sendSystemMessage(Component.literal("§e=== BetterSpawn 已加载模板 ==="));
            for (var rule : templates) {
                ctx.getSource().sendSystemMessage(Component.literal(
                        "  §b" + rule.dimension() + "§r: method=" + rule.method()
                                + ", fallback=" + rule.fallback()));
            }
            return 1;
        }));

        // reload（触发一次完整数据包 reload）
        root.then(Commands.literal("reload").executes(ctx -> {
            MinecraftServer server = ctx.getSource().getServer();
            server.reloadResources(server.getPackRepository().getSelectedIds());
            ctx.getSource().sendSuccess(() -> Component.literal("§aBetterSpawn: 已触发数据包重载"), true);
            return 1;
        }));

        dispatcher.register(Commands.literal("betterspawn").then(root));
    }

    // ------------------------------------------------------------------
    // 参数辅助
    // ------------------------------------------------------------------

    private static RequiredArgumentBuilder<CommandSourceStack, ResourceKey<Level>> argumentDimension(String name) {
        return Commands.argument(name, ResourceKeyArgument.key(Registries.DIMENSION))
                .suggests((ctx, builder) -> suggestDimensions(ctx.getSource(), builder));
    }

    @SuppressWarnings("unchecked")
    private static ResourceKey<Level> dimension(CommandContext<CommandSourceStack> ctx, String name) {
        return (ResourceKey<Level>) ctx.getArgument(name, ResourceKey.class);
    }

    private static Collection<ServerPlayer> targets(CommandContext<CommandSourceStack> ctx)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        return EntityArgument.getPlayers(ctx, "targets");
    }

    private static BlockPos vec(CommandContext<CommandSourceStack> ctx) {
        Vec3 value = Vec3Argument.getVec3(ctx, "pos");
        return BlockPos.containing(value.x, value.y, value.z);
    }

    private static ResourceKey<Level> currentDimension(CommandContext<CommandSourceStack> ctx) {
        return ctx.getSource().getLevel().dimension();
    }

    private static BlockPos currentFeet(CommandContext<CommandSourceStack> ctx) {
        Vec3 position = ctx.getSource().getPosition();
        return BlockPos.containing(position.x, position.y, position.z);
    }

    private static float currentYaw(CommandContext<CommandSourceStack> ctx) {
        return ctx.getSource().getEntity() != null ? ctx.getSource().getEntity().getYRot() : 0.0F;
    }

    private static CompletableFuture<Suggestions> suggestDimensions(CommandSourceStack src, SuggestionsBuilder builder) {
        MinecraftServer server = src.getServer();
        if (server != null) {
            for (ResourceLocation location : server.registryAccess()
                    .registryOrThrow(Registries.DIMENSION).keySet()) {
                builder.suggest(location.toString());
            }
        }
        return builder.buildFuture();
    }

    // ------------------------------------------------------------------
    // 执行
    // ------------------------------------------------------------------

    private static int execGlobal(CommandContext<CommandSourceStack> ctx, ResourceKey<Level> dimension,
                                  BlockPos pos, float angle) {
        MinecraftServer server = ctx.getSource().getServer();
        ServerLevel level = server.getLevel(dimension);
        if (level == null) {
            ctx.getSource().sendFailure(Component.literal("§c维度不存在: " + dimension.location()));
            return 0;
        }

        WorldSpawnData.get(server).set(dimension, pos, angle, level);
        String message = "§a已设置 " + dimension.location() + " 全局默认出生点: " + pos + " angle=" + angle;
        ctx.getSource().sendSuccess(() -> Component.literal(message), true);
        return 1;
    }

    private static int execPlayer(CommandContext<CommandSourceStack> ctx, Collection<ServerPlayer> targets,
                                  ResourceKey<Level> dimension, BlockPos pos, Float angle) {
        int changed = 0;
        for (ServerPlayer target : targets) {
            ResourceKey<Level> actualDimension = dimension != null
                    ? dimension
                    : target.level().dimension();
            BlockPos actualPos = pos != null
                    ? pos
                    : BlockPos.containing(target.getX(), target.getY(), target.getZ());
            float actualAngle = angle != null ? angle : target.getYRot();

            PlayerSpawnData.setDimensionSpawn(target, actualDimension, actualPos, actualAngle);
            changed++;
        }

        String message = "§a已设置 " + changed + " 个玩家在 "
                + (dimension != null ? dimension.location() : "各自维度") + " 的个人世界出生点";
        ctx.getSource().sendSuccess(() -> Component.literal(message), true);
        return changed > 0 ? 1 : 0;
    }

    private static int execClearGlobal(CommandContext<CommandSourceStack> ctx, ResourceKey<Level> dimension) {
        WorldSpawnData.get(ctx.getSource().getServer()).clear(dimension);
        String message = "§a已清除 " + dimension.location() + " 的全局默认出生点";
        ctx.getSource().sendSuccess(() -> Component.literal(message), true);
        return 1;
    }

    private static int execClearPlayer(CommandContext<CommandSourceStack> ctx, ResourceKey<Level> dimension,
                                       Collection<ServerPlayer> targets) {
        for (ServerPlayer target : targets) {
            PlayerSpawnData.clearDimensionSpawn(target, dimension);
        }
        String message = "§a已清除 " + targets.size() + " 个玩家在 " + dimension.location() + " 的个人世界出生点";
        ctx.getSource().sendSuccess(() -> Component.literal(message), true);
        return 1;
    }

    private static int execQueryGlobal(CommandContext<CommandSourceStack> ctx, ResourceKey<Level> dimension) {
        MinecraftServer server = ctx.getSource().getServer();
        ServerLevel level = server.getLevel(dimension);
        var entry = WorldSpawnData.get(server).get(dimension);

        // 主世界的 level.dat 是最终权威（/setworldspawn 直接写这里），查询时以它为准
        BlockPos pos;
        float angle;
        if (dimension.equals(Level.OVERWORLD) && level != null) {
            pos = level.getLevelData().getSpawnPos();
            angle = level.getLevelData().getSpawnAngle();
        } else if (entry.isPresent()) {
            pos = entry.get().pos();
            angle = entry.get().angle();
        } else {
            ctx.getSource().sendFailure(Component.literal("§c" + dimension.location() + " 无全局默认出生点"));
            return 0;
        }

        BlockPos finalPos = pos;
        float finalAngle = angle;
        String message = "§e" + dimension.location() + " 全局默认出生点: " + finalPos + " angle=" + finalAngle;
        ctx.getSource().sendSuccess(() -> Component.literal(message), false);
        return 1;
    }

    private static int execQueryPlayer(CommandContext<CommandSourceStack> ctx, ResourceKey<Level> dimension,
                                       ServerPlayer target) {
        var entry = PlayerSpawnData.getDimensionSpawn(target, dimension);
        if (entry.isEmpty()) {
            String message = "§c" + target.getScoreboardName() + " 在 " + dimension.location() + " 无个人世界出生点";
            ctx.getSource().sendSuccess(() -> Component.literal(message), false);
            return 0;
        }
        var point = entry.get();
        String message = "§e" + target.getScoreboardName() + " 在 " + dimension.location()
                + " 的个人世界出生点: " + point.pos() + " angle=" + point.angle();
        ctx.getSource().sendSuccess(() -> Component.literal(message), false);
        return 1;
    }

    private static int execRegenerate(CommandContext<CommandSourceStack> ctx, ResourceKey<Level> dimension) {
        boolean ok = DimensionSpawnInitializer.regenerate(ctx.getSource().getServer(), dimension);
        if (!ok) {
            ctx.getSource().sendFailure(Component.literal("§c重新解析失败: " + dimension.location()));
            return 0;
        }
        var point = WorldSpawnData.get(ctx.getSource().getServer()).get(dimension);
        String message = point.map(p -> "§a已重新解析 " + dimension.location() + " 的默认出生点: "
                        + p.pos() + " angle=" + p.angle())
                .orElse("§a已重新解析 " + dimension.location());
        ctx.getSource().sendSuccess(() -> Component.literal(message), true);
        return 1;
    }

    private static int execSetSpawnDimension(CommandContext<CommandSourceStack> ctx,
                                             ResourceKey<Level> dimension) {
        MinecraftServer server = ctx.getSource().getServer();
        if (server.getLevel(dimension) == null) {
            ctx.getSource().sendFailure(Component.literal("§c维度不存在: " + dimension.location()));
            return 0;
        }
        WorldSpawnData.get(server).setSpawnDimension(dimension);
        String message = "§a实验性出生维度已设为 " + dimension.location()
                + "（只影响之后首次进入世界的新玩家）";
        ctx.getSource().sendSuccess(() -> Component.literal(message), true);
        return 1;
    }

    private static int execQuerySpawnDimension(CommandContext<CommandSourceStack> ctx) {
        ResourceKey<Level> dimension = WorldSpawnData.get(ctx.getSource().getServer()).spawnDimension();
        String message = "§e实验性出生维度: " + dimension.location();
        ctx.getSource().sendSuccess(() -> Component.literal(message), false);
        return 1;
    }
}
