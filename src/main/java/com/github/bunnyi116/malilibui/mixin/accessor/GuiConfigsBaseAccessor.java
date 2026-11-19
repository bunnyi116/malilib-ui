package com.github.bunnyi116.malilibui.mixin.accessor;

import fi.dy.masa.malilib.gui.GuiConfigsBase;
import fi.dy.masa.malilib.gui.widgets.WidgetDropDownList;
import fi.dy.masa.malilib.util.data.ModInfo;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(GuiConfigsBase.class)
public interface GuiConfigsBaseAccessor {
    @Accessor("modSwitchWidget")
    WidgetDropDownList<ModInfo> malilibui$getModSwitchWidget();

    @Invoker("getConfigWidth")
    int malilibui$getConfigWidth();
}
