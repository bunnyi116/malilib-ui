package com.github.bunnyi116.malilibui.api.config;

import java.util.List;

import com.google.common.collect.ImmutableList;
import fi.dy.masa.malilib.config.options.ConfigStringList;

import fi.dy.masa.malilib.util.StringUtils;

import com.github.bunnyi116.malilibui.api.IVisibleConfig;

/**
 * 带动态可见性的字符串列表配置；内部提供链式 {@link Builder}。
 */
public class UiConfigStringList extends ConfigStringList implements IVisibleConfig<UiConfigStringList> {
    public UiConfigStringList(String translationPrefix, String name, List<String> defaultValues) {
        super(name, ImmutableList.copyOf(defaultValues));
        this.apply(translationPrefix);
        this.setPrettyName(StringUtils.translate(translationPrefix + ".name." + name));
    }


    /** 链式构建器：可不初始化默认值，链式设置后 {@link #build()}。 */
    public static final class Builder extends UiConfigBuilderBase<UiConfigStringList, Builder> {
        private List<String> defaultValues = ImmutableList.of();

        public Builder(String translationPrefix, String name) {
            super(translationPrefix, name);
        }

        public Builder defaultValue(List<String> values) {
            this.defaultValues = values;

            return this;
        }

        @Override
        public UiConfigStringList build() {
            UiConfigStringList config = new UiConfigStringList(this.translationPrefix,
                    this.name, this.defaultValues);
            return this.finish(config);
        }
    }
}
