package com.proto781m.betterspawn.mixin;

import net.minecraft.client.gui.components.tabs.GridLayoutTab;
import net.minecraft.client.gui.layouts.GridLayout;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Exposes {@link GridLayoutTab#layout} so the MoreTab can inject the button. */
@Mixin(GridLayoutTab.class)
public interface GridLayoutTabAccessor {

    @Accessor("layout")
    GridLayout betterspawn$getLayout();
}
