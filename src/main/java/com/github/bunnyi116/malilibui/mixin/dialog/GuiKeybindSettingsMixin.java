package com.github.bunnyi116.malilibui.mixin.dialog;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.gui.screens.Screen;

import fi.dy.masa.malilib.config.IConfigBase;
import fi.dy.masa.malilib.gui.GuiBase;
import fi.dy.masa.malilib.gui.GuiConfigsBase;
import fi.dy.masa.malilib.gui.GuiConfigsBase.ConfigOptionWrapper;
import fi.dy.masa.malilib.gui.GuiKeybindSettings;
import fi.dy.masa.malilib.gui.interfaces.IDialogHandler;
import fi.dy.masa.malilib.hotkeys.IKeybind;
import fi.dy.masa.malilib.util.GuiUtils;
import fi.dy.masa.malilib.util.StringUtils;

/**
 * 快捷键设置对话框标题原本显示的是原始配置名（例如
 * {@code enableAreaSelectionBoxesRendering}），这里改为翻译后的配置显示名。
 *
 * <p>该对话框由快捷键设置控件打开，而该控件只携带原始配置名；
 * 因此通过父配置界面的选项列表反查对应的配置对象来获取显示名。</p>
 */
@Mixin(GuiKeybindSettings.class)
public abstract class GuiKeybindSettingsMixin {
    @Inject(method = "<init>", at = @At("RETURN"))
    private void malilibui$retitle(IKeybind keybind, String name, IDialogHandler dialogHandler,
                                   Screen parent, CallbackInfo ci) {
        String displayName = resolveDisplayName(name, parent);

        if (displayName != null && !displayName.equals(name)) {
            ((GuiBase) (Object) this).setTitle(GuiBase.TXT_BOLD +
                    StringUtils.translate("malilib.gui.title.keybind_settings.advanced", displayName) +
                    GuiBase.TXT_RST);
        }
    }

    private static String resolveDisplayName(String configName, Screen parent) {
        Screen screen = parent != null ? parent : GuiUtils.getCurrentScreen();

        if (screen instanceof GuiConfigsBase configs) {
            for (ConfigOptionWrapper wrapper : configs.getConfigs()) {
                IConfigBase config = wrapper.getConfig();

                if (config != null && configName.equals(config.getName())) {
                    return config.getConfigGuiDisplayName();
                }
            }
        }

        return null;
    }
}
