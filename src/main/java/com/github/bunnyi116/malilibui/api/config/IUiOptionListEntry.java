package com.github.bunnyi116.malilibui.api.config;

import fi.dy.masa.malilib.config.IConfigOptionListEntry;

/**
 * 选项列表条目接口：只需要提供简短的值名，显示名由本模组按配置项的翻译前缀自动拼接。
 *
 * <p>翻译键规则：{@code <配置翻译前缀>.option.<值名>}。例如配置前缀为
 * {@code malilib-ui.config}、值名为 {@code option_a} 时，显示名取自
 * {@code malilib-ui.config.option.option_a}。</p>
 *
 * <pre>{@code
 * public enum MyMode implements IUiOptionListEntry {
 *     A("mode_a"),
 *     B("mode_b");
 *
 *     private final String value;
 *
 *     MyMode(String value) {
 *         this.value = value;
 *     }
 *
 *     @Override
 *     public String getValueName() {
 *         return this.value;
 *     }
 *
 *     @Override
 *     public String getStringValue() {
 *         return this.value;
 *     }
 *
 *     @Override
 *     public String getDisplayName() {
 *         return OptionListTranslations.translate(this);
 *     }
 * }
 * }</pre>
 */
public interface IUiOptionListEntry extends IConfigOptionListEntry {
    /**
     * 短值名（用于拼接翻译键，也用于序列化存储）。
     */
    String getValueName();
}
