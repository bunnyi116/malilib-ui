package com.github.bunnyi116.malilibui.api.config;

import fi.dy.masa.malilib.config.options.ConfigInteger;

import fi.dy.masa.malilib.util.StringUtils;

import com.github.bunnyi116.malilibui.api.IVisibleConfig;

/**
 * 带动态可见性的整数配置；内部提供链式 {@link Builder}。
 */
public class UiConfigInteger extends ConfigInteger implements IVisibleConfig<UiConfigInteger> {
    public UiConfigInteger(String translationPrefix, String name, int defaultValue,
                           int minValue, int maxValue) {
        super(name, defaultValue, minValue, maxValue);
        this.apply(translationPrefix);
        this.setPrettyName(StringUtils.translate(translationPrefix + ".name." + name));
    }


    /** 链式构建器：可不初始化默认值，链式设置后 {@link #build()}。 */
    public static final class Builder extends UiConfigBuilderBase<UiConfigInteger, Builder> {
        private int defaultValue = 0;
        private int minValue = Integer.MIN_VALUE;
        private int maxValue = Integer.MAX_VALUE;

        public Builder(String translationPrefix, String name) {
            super(translationPrefix, name);
        }

        public Builder defaultValue(int value) {
            this.defaultValue = value;

            return this;
        }

        public Builder minValue(int value) {
            this.minValue = value;

            return this;
        }

        public Builder maxValue(int value) {
            this.maxValue = value;

            return this;
        }

        public Builder range(int min, int max) {
            this.minValue = min;
            this.maxValue = max;

            return this;
        }

        @Override
        public UiConfigInteger build() {
            UiConfigInteger config = new UiConfigInteger(this.translationPrefix, this.name,
                    this.defaultValue, this.minValue, this.maxValue);
            return this.finish(config);
        }
    }
}
