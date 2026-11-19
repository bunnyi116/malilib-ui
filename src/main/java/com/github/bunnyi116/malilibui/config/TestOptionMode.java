package com.github.bunnyi116.malilibui.config;

import fi.dy.masa.malilib.config.IConfigOptionListEntry;

import com.github.bunnyi116.malilibui.api.config.IUiOptionListEntry;
import com.github.bunnyi116.malilibui.api.config.OptionListTranslations;

/**
 * 控件展示用的选项列表枚举（用于演示 malilib 的下拉选项控件）。
 *
 * <p>这里只写短值名，显示名由配置项的翻译前缀自动拼接为
 * {@code malilib-ui.config.option.<值名>}。</p>
 */
public enum TestOptionMode implements IUiOptionListEntry {
    OPTION_A("option_a"),
    OPTION_B("option_b"),
    OPTION_C("option_c");

    private final String value;

    TestOptionMode(String value) {
        this.value = value;
    }

    @Override
    public String getValueName() {
        return this.value;
    }

    @Override
    public String getStringValue() {
        return this.value;
    }

    @Override
    public String getDisplayName() {
        return OptionListTranslations.translate(this);
    }

    @Override
    public IConfigOptionListEntry cycle(boolean forward) {
        TestOptionMode[] values = values();
        int index = this.ordinal() + (forward ? 1 : -1);

        return values[(index + values.length) % values.length];
    }

    @Override
    public IConfigOptionListEntry fromString(String value) {
        for (TestOptionMode entry : values()) {
            if (entry.value.equals(value)) {
                return entry;
            }
        }

        return OPTION_A;
    }
}
