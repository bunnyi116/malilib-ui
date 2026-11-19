package com.github.bunnyi116.malilibui.mixin.accessor;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import fi.dy.masa.malilib.gui.widgets.WidgetSlider;

@Mixin(WidgetSlider.class)
public interface WidgetSliderAccessor {
    @Accessor("dragging")
    boolean malilibui$isDragging();
}
