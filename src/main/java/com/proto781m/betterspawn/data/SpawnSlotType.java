package com.proto781m.betterspawn.data;

/** 玩家重生点槽类型。 */
public enum SpawnSlotType {
    /** 指令出生点（/spawnpoint set）：精确站立坐标，不依赖方块。 */
    COMMAND,
    /** 床出生点：记录床方块位置，重生时验证方块仍有效。 */
    BED,
    /** 重生锚出生点：记录锚方块位置，重生时验证 charges 与维度。 */
    ANCHOR
}
