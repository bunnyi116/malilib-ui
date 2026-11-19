package com.github.bunnyi116.malilibui.api.config;

import fi.dy.masa.malilib.config.options.ConfigBooleanHotkeyed;
import fi.dy.masa.malilib.hotkeys.KeybindSettings;

import fi.dy.masa.malilib.util.StringUtils;

import com.github.bunnyi116.malilibui.api.IVisibleConfig;

/**
 * 带动态可见性的“布尔 + 热键”组合配置。
 *
 * <pre>{@code
 * UiConfigBooleanHotkeyed workSwitch = GROUP.booleanHotkey("workToggle", false)
 *         .defaultHotkey("CAPS_LOCK")
 *         .keybindSettings(KeybindSettings.PRESS_ALLOWEXTRA_EMPTY);
 * }</pre>
 */
public class UiConfigBooleanHotkeyed extends ConfigBooleanHotkeyed implements IVisibleConfig<UiConfigBooleanHotkeyed> {
    public UiConfigBooleanHotkeyed(String translationPrefix, String name, boolean defaultValue,
                                   String defaultStorageString, KeybindSettings keybindSettings) {
        super(name, defaultValue, defaultStorageString, keybindSettings);
        this.apply(translationPrefix);
        this.setPrettyName(StringUtils.translate(translationPrefix + ".name." + name));
    }

    /**
     * 链式构建器：可不初始化默认值 / 热键，链式设置后 {@link #build()}。
     */
    public static final class Builder extends UiConfigBuilderBase<UiConfigBooleanHotkeyed, Builder> {
        private boolean defaultValue = false;
        private String defaultStorageString = "";
        private KeybindSettings keybindSettings = KeybindSettings.DEFAULT;

        public Builder(String translationPrefix, String name) {
            super(translationPrefix, name);
        }

        public Builder defaultValue(boolean value) {
            this.defaultValue = value;

            return this;
        }

        /**
         * 默认热键（存储字符串，如 {@code "CAPS_LOCK"} 或 {@code "Z,Y"}）。
         */
        public Builder defaultHotkey(String storageString) {
            this.defaultStorageString = storageString;

            return this;
        }

        /** {@link #defaultHotkey(String)} 的别名。 */
        public Builder defaultStorageString(String storageString) {
            return this.defaultHotkey(storageString);
        }

        public Builder keybindSettings(KeybindSettings settings) {
            this.keybindSettings = settings;

            return this;
        }

        @Override
        public UiConfigBooleanHotkeyed build() {
            UiConfigBooleanHotkeyed config = new UiConfigBooleanHotkeyed(this.translationPrefix,
                    this.name, this.defaultValue, this.defaultStorageString, this.keybindSettings);

            return this.finish(config);
        }
    }
}
