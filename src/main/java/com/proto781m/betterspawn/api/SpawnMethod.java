package com.proto781m.betterspawn.api;

/**
 * 出生点计算方法接口。
 *
 * <p>每种方法只负责“计算坐标”，不负责落库；由调用方（{@code SpawnMethodResolver}
 * 或 {@code DimensionSpawnInitializer}）决定是否写入持久化数据。
 */
public interface SpawnMethod {

    /** 方法 id，必须与 JSON 模板中的 {@code method} 字段一致，全小写。 */
    String id();

    /**
     * 解析出生点。
     *
     * @param ctx 解析上下文
     * @return 解析结果（可能 {@link ResolveResult.Status#UNSAFE} / {@code TIMEOUT} / {@code EMPTY}）
     */
    ResolveResult resolve(SpawnContext ctx);
}
