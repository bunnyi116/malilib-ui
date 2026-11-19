package com.github.bunnyi116.malilibui.mixin.accessor;

import java.util.Collection;

import fi.dy.masa.malilib.gui.GuiScrollBar;
import fi.dy.masa.malilib.gui.widgets.WidgetListBase;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(WidgetListBase.class)
public interface WidgetListBaseAccessor {
    @Accessor("posX")
    int malilibui$getListPosX();

    @Accessor("posY")
    int malilibui$getListPosY();

    @Accessor("browserWidth")
    int malilibui$getBrowserWidth();

    @Accessor("browserHeight")
    int malilibui$getBrowserHeight();

    @Accessor("browserEntriesOffsetY")
    int malilibui$getBrowserEntriesOffsetY();

    @Accessor("browserEntriesOffsetY")
    void malilibui$setBrowserEntriesOffsetY(int value);

    @Invoker("getAllEntries")
    Collection<?> malilibui$getAllEntries();


    @Accessor("scrollBar")
    GuiScrollBar malilibui$getScrollBar();

}
