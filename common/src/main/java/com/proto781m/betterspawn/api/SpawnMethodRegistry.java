package com.proto781m.betterspawn.api;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Registry of spawn methods.
 *
 * <p>Built-in methods are registered in {@code BetterSpawn#registerMethods}. Other mods
 * may register custom methods with {@link #register(SpawnMethod)}, preferably during
 * their own mod construction.
 *
 * <p>The registry is process-wide and shared across worlds, which suits stateless
 * method implementations.
 */
public final class SpawnMethodRegistry {

    private static final Map<String, SpawnMethod> METHODS = new LinkedHashMap<>();

    private SpawnMethodRegistry() {
    }

    /** Registers a method. A later registration with the same ID replaces the earlier one. */
    public static void register(SpawnMethod method) {
        METHODS.put(method.id(), method);
    }

    /** Returns the method with the given ID, or {@code null} if it does not exist. */
    public static SpawnMethod get(String id) {
        return METHODS.get(id);
    }

    public static Map<String, SpawnMethod> getAllMethods() {
        return Map.copyOf(METHODS);
    }
}
