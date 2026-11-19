package com.github.bunnyi116.malilibui.api.config;

import fi.dy.masa.malilib.config.options.ConfigHotkey;
import fi.dy.masa.malilib.hotkeys.KeybindSettings;

import fi.dy.masa.malilib.util.StringUtils;

import com.github.bunnyi116.malilibui.api.IVisibleConfig;

/**
 * 带动态可见性的热键配置；内部提供链式 {@link Builder}。
 */
public class UiConfigHotkey extends ConfigHotkey implements IVisibleConfig<UiConfigHotkey> {
    public UiConfigHotkey(String translationPrefix, String name, String defaultValue) {
        super(name, defaultValue);
        this.apply(translationPrefix);
        this.setPrettyName(StringUtils.translate(translationPrefix + ".name." + name));
    }

    public UiConfigHotkey(String translationPrefix, String name, String defaultStorageString,
                          KeybindSettings keybindSettings) {
        super(name, defaultStorageString, keybindSettings);
        this.apply(translationPrefix);
    }


    /** 链式构建器：可不初始化默认值，链式设置后 {@link #build()}。 */
    public static final class Builder extends UiConfigBuilderBase<UiConfigHotkey, Builder> {
        private String defaultValue = "";
        private KeybindSettings keybindSettings = KeybindSettings.DEFAULT;

        public Builder(String translationPrefix, String name) {
            super(translationPrefix, name);
        }

        public Builder defaultValue(String value) {
            this.defaultValue = value;

            return this;
        }

        /** {@link #defaultValue(String)} 的别名。 */
        public Builder defaultStorageString(String value) {
            return this.defaultValue(value);
        }

        public Builder keybindSettings(KeybindSettings settings) {
            this.keybindSettings = settings;

            return this;
        }

        @Override
        public UiConfigHotkey build() {
            UiConfigHotkey config = new UiConfigHotkey(this.translationPrefix, this.name,
                    this.defaultValue, this.keybindSettings);

            return this.finish(config);
        }
    }
}
