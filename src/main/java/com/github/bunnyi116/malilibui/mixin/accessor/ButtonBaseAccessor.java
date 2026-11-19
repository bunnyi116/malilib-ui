package com.github.bunnyi116.malilibui.mixin.accessor;

import fi.dy.masa.malilib.gui.button.ButtonBase;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(ButtonBase.class)
public interface ButtonBaseAccessor {
    @Accessor("displayString")
    String malilibui$getDisplayString();

    @Accessor("enabled")
    boolean malilibui$isEnabled();
}
