package com.github.bunnyi116.malilibui.mixin.accessor;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import fi.dy.masa.malilib.gui.widgets.WidgetListEntryBase;

@Mixin(WidgetListEntryBase.class)
public interface WidgetListEntryBaseAccessor {
    @Accessor("entry")
    Object malilibui$getEntry();
}
