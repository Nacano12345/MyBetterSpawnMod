package com.proto781m.betterspawn.mixin;

import net.minecraft.client.gui.components.tabs.GridLayoutTab;
import net.minecraft.client.gui.layouts.GridLayout;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** 暴露 {@link GridLayoutTab#layout}，供 MoreTab 注入按钮。 */
@Mixin(GridLayoutTab.class)
public interface GridLayoutTabAccessor {

    @Accessor("layout")
    GridLayout betterspawn$getLayout();
}
