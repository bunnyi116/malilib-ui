package com.github.bunnyi116.malilibui.mixin.accessor;

import fi.dy.masa.malilib.gui.GuiBase;
import fi.dy.masa.malilib.gui.GuiTextFieldGeneric;
import fi.dy.masa.malilib.gui.button.ButtonBase;
import fi.dy.masa.malilib.gui.widgets.WidgetBase;
import fi.dy.masa.malilib.gui.wrappers.TextFieldWrapper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.List;

@Mixin(GuiBase.class)
public interface GuiBaseAccessor {
    @Accessor("buttons")
    List<ButtonBase> malilibui$getButtons();

    @Accessor("widgets")
    List<WidgetBase> malilibui$getWidgets();

    @Accessor("textFields")
    List<TextFieldWrapper<? extends GuiTextFieldGeneric>> malilibui$getTextFields();
}
