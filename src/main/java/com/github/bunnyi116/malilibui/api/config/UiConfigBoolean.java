package com.github.bunnyi116.malilibui.api.config;

import fi.dy.masa.malilib.config.options.ConfigBoolean;

import fi.dy.masa.malilib.util.StringUtils;

import com.github.bunnyi116.malilibui.api.IVisibleConfig;

/**
 * 带动态可见性的布尔配置；内部提供链式 {@link Builder}。
 */
public class UiConfigBoolean extends ConfigBoolean implements IVisibleConfig<UiConfigBoolean> {
    public UiConfigBoolean(String translationPrefix, String name, boolean defaultValue) {
        super(name, defaultValue);
        this.apply(translationPrefix);
        this.setPrettyName(StringUtils.translate(translationPrefix + ".name." + name));
    }


    /** 链式构建器：可不初始化默认值，链式设置后 {@link #build()}。 */
    public static final class Builder extends UiConfigBuilderBase<UiConfigBoolean, Builder> {
        private boolean defaultValue = false;

        public Builder(String translationPrefix, String name) {
            super(translationPrefix, name);
        }

        public Builder defaultValue(boolean value) {
            this.defaultValue = value;

            return this;
        }

        @Override
        public UiConfigBoolean build() {
            UiConfigBoolean config = new UiConfigBoolean(this.translationPrefix,
                    this.name, this.defaultValue);
            return this.finish(config);
        }
    }
}
