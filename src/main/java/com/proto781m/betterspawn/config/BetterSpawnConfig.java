package com.proto781m.betterspawn.config;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * BetterSpawn 全局配置（COMMON 类型，服务端权威）。
 *
 * <p>所有字段都在这里集中定义，运行时通过静态 getter 读取。
 * 新增配置项时：在 {@code static} 块里 push/pop 分组，用 builder 定义，
 * 并暴露一个 {@code ModConfigSpec.ConfigValue} 字段。
 */
public final class BetterSpawnConfig {

    private BetterSpawnConfig() {
    }

    public static final ModConfigSpec SPEC;

    public static final ModConfigSpec.EnumValue<RespawnPolicy> RESPAWN_POLICY;
    public static final ModConfigSpec.EnumValue<NoPersonalSpawnBehavior> NO_PERSONAL_SPAWN;
    public static final ModConfigSpec.BooleanValue SYNC_VANILLA_NBT;
    public static final ModConfigSpec.IntValue FALLBACK_MAX_DEPTH;
    public static final ModConfigSpec.IntValue SEARCH_TIMEOUT_TICKS;
    public static final ModConfigSpec.BooleanValue ENABLE_GOTODIM;

    static {
        ModConfigSpec.Builder b = new ModConfigSpec.Builder();

        b.push("general");
        RESPAWN_POLICY = b
                .comment("重生优先级：COMMAND_OVER_BED = 指令出生点优先于床；BED_OVER_COMMAND = 床/锚优先。")
                .defineEnum("respawnPolicy", RespawnPolicy.COMMAND_OVER_BED);
        NO_PERSONAL_SPAWN = b
                .comment("无个人重生点时的行为：BACK_TO_OVERWORLD = 回主世界（兼容原版）；STAY_IN_DEATH_DIMENSION = 留在死亡维度。")
                .defineEnum("noPersonalSpawnBehavior", NoPersonalSpawnBehavior.BACK_TO_OVERWORLD);
        SYNC_VANILLA_NBT = b
                .comment("是否把生效出生点同步回原版 SpawnX/Y/Z/Dimension/Angle，保证其他模组兼容。")
                .define("syncVanillaNbt", true);
        b.pop();

        b.push("search");
        FALLBACK_MAX_DEPTH = b
                .comment("fallback 链最大递归深度，防止方法互相回退造成死循环。")
                .defineInRange("fallbackMaxDepth", 3, 1, 10);
        SEARCH_TIMEOUT_TICKS = b
                .comment("单次出生点解析的超时 tick 数（仅作软提示，真正的截止时间由 resolver 计算）。")
                .defineInRange("searchTimeoutTicks", 200, 1, 2000);
        b.pop();

        b.push("debug");
        ENABLE_GOTODIM = b
                .comment("是否启用 /gotodim 测试指令（默认启用，服务器可关闭）。")
                .define("enableGotoDim", true);
        b.pop();

        SPEC = b.build();
    }
}
