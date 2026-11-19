package com.github.bunnyi116.malilibui.mixin.accessor;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

import fi.dy.masa.malilib.gui.GuiListBase;
import fi.dy.masa.malilib.gui.widgets.WidgetListBase;

@Mixin(GuiListBase.class)
public interface GuiListBaseAccessor {
    @Accessor("widget")
    WidgetListBase<?, ?> malilibui$getListWidgetField();

    @Accessor("widget")
    void malilibui$setListWidgetField(WidgetListBase<?, ?> widget);

    @Invoker("reCreateListWidget")
    void malilibui$reCreateListWidget();

    @Invoker("getListWidget")
    WidgetListBase<?, ?> malilibui$getListWidget();
}
