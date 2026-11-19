package com.github.bunnyi116.malilibui.api.config;

import fi.dy.masa.malilib.config.IConfigOptionListEntry;
import fi.dy.masa.malilib.config.options.ConfigOptionList;

import fi.dy.masa.malilib.util.StringUtils;

import com.github.bunnyi116.malilibui.api.IVisibleConfig;

/**
 * 带动态可见性的选项列表配置；内部提供链式 {@link Builder}。
 */
public class UiConfigOptionList extends ConfigOptionList implements IVisibleConfig<UiConfigOptionList> {
    public UiConfigOptionList(String translationPrefix, String name, IConfigOptionListEntry defaultValue) {
        super(name, defaultValue);
        this.apply(translationPrefix);
        this.setPrettyName(StringUtils.translate(translationPrefix + ".name." + name));
        // 登记前缀，供 IUiOptionListEntry 的 getDisplayName() 自动拼接翻译键
        OptionListTranslations.register(translationPrefix, defaultValue);
    }


    /** 链式构建器：可不初始化默认值，链式设置后 {@link #build()}。 */
    public static final class Builder extends UiConfigBuilderBase<UiConfigOptionList, Builder> {
        private IConfigOptionListEntry defaultValue = IConfigOptionListEntry.empty();

        public Builder(String translationPrefix, String name) {
            super(translationPrefix, name);
        }

        public Builder defaultValue(IConfigOptionListEntry value) {
            this.defaultValue = value;
            // 提前登记前缀，保证 build() 过程中（配置项构造阶段）翻译可用
            OptionListTranslations.register(this.translationPrefix, value);

            return this;
        }

        @Override
        public UiConfigOptionList build() {
            UiConfigOptionList config = new UiConfigOptionList(this.translationPrefix,
                    this.name, this.defaultValue);
            return this.finish(config);
        }
    }
}
