package com.github.bunnyi116.malilibui.mixin.accessor;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import fi.dy.masa.malilib.gui.widgets.WidgetListConfigOptionsBase;

@Mixin(WidgetListConfigOptionsBase.class)
public interface WidgetListConfigOptionsBaseAccessor
{
    @Accessor("configWidth")
    int malilibui$getConfigWidth();

    @Accessor("configWidth")
    void malilibui$setConfigWidth(int configWidth);
}
