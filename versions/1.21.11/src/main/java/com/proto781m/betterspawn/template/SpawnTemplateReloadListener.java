package com.proto781m.betterspawn.template;

import com.google.gson.JsonElement;
import java.util.Map;
import net.minecraft.resources.FileToIdConverter;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.util.profiling.ProfilerFiller;

/**
 * Listens to the datapack {@code spawn_rules} directory and refreshes
 * {@link SpawnTemplateManager} on reload.
 *
 * <p>Registered through {@code AddServerReloadListenersEvent}.
 */
public class SpawnTemplateReloadListener extends SimpleJsonResourceReloadListener<JsonElement> {

    public SpawnTemplateReloadListener() {
        super(ExtraCodecs.JSON, FileToIdConverter.json("spawn_rules"));
    }

    @Override
    protected void apply(Map<Identifier, JsonElement> data, ResourceManager resourceManager,
                         ProfilerFiller profiler) {
        SpawnTemplateManager.reload(data);
    }
}
