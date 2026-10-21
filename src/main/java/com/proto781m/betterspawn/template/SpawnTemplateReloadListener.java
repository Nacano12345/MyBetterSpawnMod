package com.proto781m.betterspawn.template;

import java.util.Map;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import net.minecraft.resources.ResourceLocation;

/**
 * 监听数据包中的 {@code spawn_rules} 目录，reload 时刷新 {@link SpawnTemplateManager}。
 *
 * <p>通过 {@code AddReloadListenerEvent} 注册。
 */
public class SpawnTemplateReloadListener extends SimpleJsonResourceReloadListener {

    public SpawnTemplateReloadListener() {
        super(new Gson(), "spawn_rules");
    }

    @Override
    protected void apply(Map<ResourceLocation, JsonElement> data, ResourceManager resourceManager, ProfilerFiller profiler) {
        SpawnTemplateManager.reload(data);
    }
}
