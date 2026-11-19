package com.github.bunnyi116.malilibui.api.config;

import fi.dy.masa.malilib.util.StringUtils;

import com.github.bunnyi116.malilibui.api.MalilibUiApi;

/**
 * 折叠类型：配置列表中的分组标题（可点击折叠 / 展开其下的配置项）。
 *
 * <p>在配置界面的 {@code getConfigs()} 里用它的显示文本作为 LABEL 行即可：</p>
 *
 * <pre>{@code
 * ConfigFold group = factory.fold("examplemod.group.general");
 * entries.add(new ConfigOptionWrapper(group.getTitle()));
 * }</pre>
 */
public final class ConfigFold {
    private final String modId;
    private final String titleKey;

    public ConfigFold(String modId, String titleKey) {
        this.modId = modId;
        this.titleKey = titleKey;
    }

    public String getModId() {
        return this.modId;
    }

    public String getTitleKey() {
        return this.titleKey;
    }

    /**
     * 分组标题的显示文本（也是折叠状态的标识）。
     */
    public String getTitle() {
        return StringUtils.translate(this.titleKey);
    }

    public boolean isCollapsed() {
        return MalilibUiApi.isGroupCollapsed(this.modId, this.getTitle());
    }

    public ConfigFold setCollapsed(boolean collapsed) {
        MalilibUiApi.setGroupCollapsed(this.modId, this.getTitle(), collapsed);

        return this;
    }

    public ConfigFold toggle() {
        MalilibUiApi.toggleGroup(this.modId, this.getTitle());

        return this;
    }
}
