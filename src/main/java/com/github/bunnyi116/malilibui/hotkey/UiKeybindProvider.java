package com.github.bunnyi116.malilibui.hotkey;

import java.util.ArrayList;
import java.util.List;

import fi.dy.masa.malilib.config.IConfigBase;
import fi.dy.masa.malilib.hotkeys.IHotkey;
import fi.dy.masa.malilib.hotkeys.IKeybindManager;
import fi.dy.masa.malilib.hotkeys.IKeybindProvider;

import com.github.bunnyi116.malilibui.MalilibUiMod;
import com.github.bunnyi116.malilibui.config.UiConfigs;

/**
 * 把本模组配置中的热键注册到 malilib 的热键系统。
 *
 * <p>没有注册时，配置界面里绑定的热键（例如“布尔 + 热键”控件）不会响应按键；
 * 注册后按键会按配置项自身的回调逻辑生效。</p>
 */
public class UiKeybindProvider implements IKeybindProvider {
    @Override
    public void addKeysToMap(IKeybindManager manager) {
        for (IHotkey hotkey : this.getHotkeys()) {
            manager.addKeybindToMap(hotkey.getKeybind());
        }
    }

    @Override
    public void addHotkeys(IKeybindManager manager) {
        List<IHotkey> hotkeys = this.getHotkeys();

        if (!hotkeys.isEmpty()) {
            manager.addHotkeysForCategory(MalilibUiMod.MOD_NAME, "malilib-ui.hotkeys.category", hotkeys);
        }
    }

    private List<IHotkey> getHotkeys() {
        List<IHotkey> hotkeys = new ArrayList<>();

        for (IConfigBase config : UiConfigs.OPTIONS) {
            if (config instanceof IHotkey hotkey) {
                hotkeys.add(hotkey);
            }
        }

        return hotkeys;
    }
}
