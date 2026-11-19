package com.github.bunnyi116.malilibui.api.config;

import fi.dy.masa.malilib.config.options.ConfigFloat;

import fi.dy.masa.malilib.util.StringUtils;

import com.github.bunnyi116.malilibui.api.IVisibleConfig;

/**
 * 带动态可见性的单精度浮点配置；内部提供链式 {@link Builder}。
 */
public class UiConfigFloat extends ConfigFloat implements IVisibleConfig<UiConfigFloat> {
    public UiConfigFloat(String translationPrefix, String name, float defaultValue,
                         float minValue, float maxValue) {
        super(name, defaultValue, minValue, maxValue);
        this.apply(translationPrefix);
        this.setPrettyName(StringUtils.translate(translationPrefix + ".name." + name));
    }


    /** 链式构建器：可不初始化默认值，链式设置后 {@link #build()}。 */
    public static final class Builder extends UiConfigBuilderBase<UiConfigFloat, Builder> {
        private float defaultValue = 0.0f;
        private float minValue = -Float.MAX_VALUE;
        private float maxValue = Float.MAX_VALUE;

        public Builder(String translationPrefix, String name) {
            super(translationPrefix, name);
        }

        public Builder defaultValue(float value) {
            this.defaultValue = value;

            return this;
        }

        public Builder range(float min, float max) {
            this.minValue = min;
            this.maxValue = max;

            return this;
        }

        @Override
        public UiConfigFloat build() {
            UiConfigFloat config = new UiConfigFloat(this.translationPrefix, this.name,
                    this.defaultValue, this.minValue, this.maxValue);
            return this.finish(config);
        }
    }
}
