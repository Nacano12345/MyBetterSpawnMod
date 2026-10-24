package com.proto781m.betterspawn.template;

import java.util.Map;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import net.minecraft.resources.ResourceLocation;

/**
 * Listens to the datapack {@code spawn_rules} directory and refreshes
 * {@link SpawnTemplateManager} on reload.
 *
 * <p>Registered through {@code AddReloadListenerEvent}.
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
