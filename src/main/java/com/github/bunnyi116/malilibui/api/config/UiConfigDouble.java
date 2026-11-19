package com.github.bunnyi116.malilibui.api.config;

import fi.dy.masa.malilib.config.options.ConfigDouble;

import fi.dy.masa.malilib.util.StringUtils;

import com.github.bunnyi116.malilibui.api.IVisibleConfig;

/**
 * 带动态可见性的双精度浮点配置；内部提供链式 {@link Builder}。
 */
public class UiConfigDouble extends ConfigDouble implements IVisibleConfig<UiConfigDouble> {
    public UiConfigDouble(String translationPrefix, String name, double defaultValue,
                          double minValue, double maxValue) {
        super(name, defaultValue, minValue, maxValue);
        this.apply(translationPrefix);
        this.setPrettyName(StringUtils.translate(translationPrefix + ".name." + name));
    }


    /** 链式构建器：可不初始化默认值，链式设置后 {@link #build()}。 */
    public static final class Builder extends UiConfigBuilderBase<UiConfigDouble, Builder> {
        private double defaultValue = 0.0;
        private double minValue = -Double.MAX_VALUE;
        private double maxValue = Double.MAX_VALUE;

        public Builder(String translationPrefix, String name) {
            super(translationPrefix, name);
        }

        public Builder defaultValue(double value) {
            this.defaultValue = value;

            return this;
        }

        public Builder range(double min, double max) {
            this.minValue = min;
            this.maxValue = max;

            return this;
        }

        @Override
        public UiConfigDouble build() {
            UiConfigDouble config = new UiConfigDouble(this.translationPrefix, this.name,
                    this.defaultValue, this.minValue, this.maxValue);
            return this.finish(config);
        }
    }
}
