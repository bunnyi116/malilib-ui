package com.github.bunnyi116.malilibui.api.config;

import java.util.IdentityHashMap;
import java.util.Map;

import fi.dy.masa.malilib.config.IConfigOptionListEntry;
import fi.dy.masa.malilib.util.StringUtils;

/**
 * 选项列表条目的翻译工具：把配置项的翻译前缀与选项条目的短值名拼接成完整翻译键。
 *
 * <p>{@link UiConfigOptionList} 创建时会自动登记前缀，因此使用
 * {@link IUiOptionListEntry} 的枚举只需在 {@code getDisplayName()} 里调用
 * {@link #translate(IConfigOptionListEntry)} 即可。</p>
 */
public final class OptionListTranslations {
    private static final Map<IConfigOptionListEntry, String> PREFIXES = new IdentityHashMap<>();

    private OptionListTranslations() {
    }

    /**
     * 登记选项条目所属的翻译前缀（由配置项自动调用）。
     *
     * <p>若条目是枚举，会登记该枚举的<b>所有</b>常量，确保切换选项后仍能显示翻译。</p>
     */
    public static void register(String translationPrefix, IConfigOptionListEntry entry) {
        if (entry.getClass().isEnum()) {
            for (Object constant : entry.getClass().getEnumConstants()) {
                if (constant instanceof IConfigOptionListEntry optionEntry) {
                    PREFIXES.put(optionEntry, translationPrefix);
                }
            }

            return;
        }

        PREFIXES.put(entry, translationPrefix);
    }

    /**
     * 返回选项条目的显示名。
     *
     * <p>已登记前缀时取 {@code <前缀>.option.<值名>} 的翻译；未登记前缀时退回条目的值名
     * （注意不能调用 {@code getDisplayName()}，否则会与调用方形成无限递归）。</p>
     */
    public static String translate(IConfigOptionListEntry entry) {
        String prefix = PREFIXES.get(entry);
        String valueName = entry instanceof IUiOptionListEntry uiEntry
                ? uiEntry.getValueName()
                : entry.getStringValue();

        if (prefix != null) {
            return StringUtils.translate(prefix + ".option." + valueName);
        }

        return valueName;
    }
}
