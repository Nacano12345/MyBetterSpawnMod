package com.proto781m.betterspawn.config;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * BetterSpawn 全局配置（COMMON 类型，服务端权威）。
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
    public static final ModConfigSpec.IntValue MAX_CHUNK_LOADS;
    public static final ModConfigSpec.BooleanValue ENABLE_GOTODIM;
    public static final ModConfigSpec.BooleanValue ENABLE_SPAWN_DIMENSION_SELECTOR;
    public static final ModConfigSpec.BooleanValue ENABLE_DIAGNOSTIC_LOGS;

    static {
        ModConfigSpec.Builder b = new ModConfigSpec.Builder();

        b.push("general");
        RESPAWN_POLICY = b
                .comment("重生优先级：COMMAND_OVER_BED = 指令出生点优先于床；BED_OVER_COMMAND = 床/锚优先。")
                .defineEnum("respawnPolicy", RespawnPolicy.COMMAND_OVER_BED);
        NO_PERSONAL_SPAWN = b
                .comment("死亡维度连全局默认出生点都没有时的最后回退：BACK_TO_OVERWORLD = 回主世界；STAY_IN_DEATH_DIMENSION = 留在死亡维度/交还原版。")
                .defineEnum("noPersonalSpawnBehavior", NoPersonalSpawnBehavior.BACK_TO_OVERWORLD);
        SYNC_VANILLA_NBT = b
                .comment("执行 /betterspawn spawnpoint set 时，是否同步写原版重生点 NBT，保证其他模组兼容。")
                .define("syncVanillaNbt", true);
        b.pop();

        b.push("search");
        FALLBACK_MAX_DEPTH = b
                .comment("fallback 链最大递归深度，防止方法互相回退造成死循环。")
                .defineInRange("fallbackMaxDepth", 3, 1, 10);
        SEARCH_TIMEOUT_TICKS = b
                .comment("单次出生点解析的超时 tick 数。")
                .defineInRange("searchTimeoutTicks", 200, 1, 2000);
        MAX_CHUNK_LOADS = b
                .comment("单次解析最多同步加载/生成多少个区块。数值过大会导致首次进服卡顿或存档 IO 压力。")
                .defineInRange("maxChunkLoads", 64, 1, 8192);
        b.pop();

        b.push("debug");
        ENABLE_GOTODIM = b
                .comment("是否启用 /gotodim 与 /betterspawn gotodim 测试指令。")
                .define("enableGotoDim", true);
        ENABLE_DIAGNOSTIC_LOGS = b
                .comment("输出详细诊断日志：重生选择链、个人出生点写入、命令解析、verify 结果等。默认关闭。")
                .define("enableDiagnosticLogs", false);
        b.pop();

        b.push("experimental");
        ENABLE_SPAWN_DIMENSION_SELECTOR = b
                .comment("实验性：在单人/局域网创建世界界面显示“出生维度”选择按钮。专用服务器不支持此界面。")
                .define("enableSpawnDimensionSelector", true);
        b.pop();

        SPEC = b.build();
    }
}
