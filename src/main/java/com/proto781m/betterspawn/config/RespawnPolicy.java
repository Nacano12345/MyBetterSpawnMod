package com.proto781m.betterspawn.config;

/**
 * 重生优先级策略：指令出生点与床/锚出生点谁优先。
 */
public enum RespawnPolicy {
    /** 指令出生点（/spawnpoint set）优先于床/锚。兼容原版直觉，推荐默认。 */
    COMMAND_OVER_BED,
    /** 床/锚出生点优先于指令出生点。 */
    BED_OVER_COMMAND
}
