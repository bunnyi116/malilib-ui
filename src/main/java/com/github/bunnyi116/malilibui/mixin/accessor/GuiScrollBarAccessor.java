package com.github.bunnyi116.malilibui.mixin.accessor;

import fi.dy.masa.malilib.gui.GuiScrollBar;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(GuiScrollBar.class)
public interface GuiScrollBarAccessor {
    @Accessor("backgroundColor")
    void malilibui$setBackgroundColor(int color);

    @Accessor("foregroundColor")
    void malilibui$setForegroundColor(int color);
}
