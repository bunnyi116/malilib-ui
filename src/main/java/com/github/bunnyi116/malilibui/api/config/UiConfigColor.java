package com.github.bunnyi116.malilibui.api.config;

import fi.dy.masa.malilib.config.options.ConfigColor;

import fi.dy.masa.malilib.util.StringUtils;

import com.github.bunnyi116.malilibui.api.IVisibleConfig;

/**
 * 带动态可见性的颜色配置；内部提供链式 {@link Builder}。
 */
public class UiConfigColor extends ConfigColor implements IVisibleConfig<UiConfigColor> {
    public UiConfigColor(String translationPrefix, String name, String defaultValue) {
        super(name, defaultValue);
        this.apply(translationPrefix);
        this.setPrettyName(StringUtils.translate(translationPrefix + ".name." + name));
    }


    /** 链式构建器：可不初始化默认值，链式设置后 {@link #build()}。 */
    public static final class Builder extends UiConfigBuilderBase<UiConfigColor, Builder> {
        private String defaultValue = "#FFFFFF";

        public Builder(String translationPrefix, String name) {
            super(translationPrefix, name);
        }

        public Builder defaultValue(String value) {
            this.defaultValue = value;

            return this;
        }

        @Override
        public UiConfigColor build() {
            UiConfigColor config = new UiConfigColor(this.translationPrefix, this.name, this.defaultValue);
            return this.finish(config);
        }
    }
}
