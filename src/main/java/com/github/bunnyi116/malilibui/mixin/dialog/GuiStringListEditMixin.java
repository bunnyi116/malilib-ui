package com.github.bunnyi116.malilibui.mixin.dialog;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.gui.screens.Screen;

import fi.dy.masa.malilib.config.IConfigStringList;
import fi.dy.masa.malilib.gui.GuiBase;
import fi.dy.masa.malilib.gui.GuiStringListEdit;
import fi.dy.masa.malilib.gui.interfaces.IConfigGui;
import fi.dy.masa.malilib.gui.interfaces.IDialogHandler;
import fi.dy.masa.malilib.util.StringUtils;

/**
 * 原版这些对话框的标题使用 {@code config.getName()}（原始配置名），
 * 这里改为配置的翻译显示名。
 */
@Mixin(GuiStringListEdit.class)
public abstract class GuiStringListEditMixin {
    @Inject(method = "<init>", at = @At("RETURN"))
    private void malilibui$retitle(IConfigStringList config, IConfigGui configGui,
                                   IDialogHandler dialogHandler, Screen parent, CallbackInfo ci) {
        ((GuiBase) (Object) this).setTitle(StringUtils.translate(
                "malilib.gui.title.string_list_edit", config.getConfigGuiDisplayName()));
    }
}
