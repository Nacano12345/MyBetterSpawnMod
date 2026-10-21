package com.proto781m.betterspawn.api;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 出生方法注册表。
 *
 * <p>框架内置的六种方法在 {@code BetterSpawn#registerMethods} 中注册；
 * 其他模组可通过 {@link #register(SpawnMethod)} 注册自定义方法（建议在自己模组的
 * 构造阶段调用）。
 *
 * <p>注意：本注册表是进程内静态的，跨存档共享，符合“方法”这种无状态实现的语义。
 */
public final class SpawnMethodRegistry {

    private static final Map<String, SpawnMethod> METHODS = new LinkedHashMap<>();

    private SpawnMethodRegistry() {
    }

    /** 注册一个方法，id 重复时后者覆盖前者。 */
    public static void register(SpawnMethod method) {
        METHODS.put(method.id(), method);
    }

    /** 按 id 获取方法，不存在返回 {@code null}。 */
    public static SpawnMethod get(String id) {
        return METHODS.get(id);
    }

    public static boolean contains(String id) {
        return METHODS.containsKey(id);
    }

    public static Collection<SpawnMethod> all() {
        return METHODS.values();
    }

    public static Map<String, SpawnMethod> getAllMethods() {
        return Map.copyOf(METHODS);
    }
}
