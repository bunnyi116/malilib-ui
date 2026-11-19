package com.github.bunnyi116.malilibui.mixin.dialog;

import fi.dy.masa.malilib.config.IConfigTable;
import fi.dy.masa.malilib.gui.GuiBase;
import fi.dy.masa.malilib.gui.GuiTableEdit;
import fi.dy.masa.malilib.gui.interfaces.IConfigGui;
import fi.dy.masa.malilib.gui.interfaces.IDialogHandler;
import fi.dy.masa.malilib.util.StringUtils;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GuiTableEdit.class)
public abstract class GuiTableEditMixin {
    @Inject(method = "<init>", at = @At("RETURN"))
    private void malilibui$retitle(IConfigTable config, IConfigGui configGui,
                                   IDialogHandler dialogHandler, Screen parent, CallbackInfo ci) {
        ((GuiBase) (Object) this).setTitle(StringUtils.translate(
                "malilib.gui.title.table_edit", config.getConfigGuiDisplayName()));
    }
}
