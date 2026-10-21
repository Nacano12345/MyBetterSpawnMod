package com.proto781m.betterspawn.config;

/**
 * 玩家没有任何个人重生点时的回退行为。
 */
public enum NoPersonalSpawnBehavior {
    /** 回主世界的全局默认出生点（兼容原版行为）。 */
    BACK_TO_OVERWORLD,
    /** 留在死亡维度的玩家/全局出生点。 */
    STAY_IN_DEATH_DIMENSION
}
