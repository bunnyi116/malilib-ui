package com.github.bunnyi116.malilibui.api;

import java.util.ArrayList;
import java.util.List;

import com.google.common.collect.ImmutableList;
import fi.dy.masa.malilib.config.IConfigBase;

import com.github.bunnyi116.malilibui.api.config.ConfigFold;
import com.github.bunnyi116.malilibui.api.config.ConfigGroup;
import com.github.bunnyi116.malilibui.api.config.UiConfigBoolean;
import com.github.bunnyi116.malilibui.api.config.UiConfigBooleanHotkeyed;
import com.github.bunnyi116.malilibui.api.config.UiConfigColor;
import com.github.bunnyi116.malilibui.api.config.UiConfigDouble;
import com.github.bunnyi116.malilibui.api.config.UiConfigFloat;
import com.github.bunnyi116.malilibui.api.config.UiConfigHotkey;
import com.github.bunnyi116.malilibui.api.config.UiConfigInteger;
import com.github.bunnyi116.malilibui.api.config.UiConfigOptionList;
import com.github.bunnyi116.malilibui.api.config.UiConfigString;
import com.github.bunnyi116.malilibui.api.config.UiConfigStringList;

/**
 * 配置工厂：传入模组 ID（用于翻译前缀），创建配置分组与各类配置的链式构建器。
 *
 * <p>创建配置项时只需要配置 key，其余（默认值、范围、热键、可见性等）通过链式构建器设置：</p>
 *
 * <pre>{@code
 * ConfigGroup general = FACTORY.group("general");
 *
 * UiConfigBoolean enabled = general.bool("enabled").defaultValue(true).build();
 * UiConfigInteger count = general.integer("count").defaultValue(5).range(0, 100).build();
 * }</pre>
 */
public final class ConfigFactory {
    private final String modId;
    private final String translationPrefix;
    private final List<IConfigBase> options = new ArrayList<>();

    private ConfigFactory(String modId, String translationPrefix) {
        this.modId = modId;
        this.translationPrefix = translationPrefix;
    }

    /**
     * 创建配置工厂（翻译前缀默认 {@code <modId>.config}）。
     *
     * @param modId 模组 ID（必须与 fabric.mod.json 的 id 一致）
     */
    public static ConfigFactory of(String modId) {
        return of(modId, modId + ".config");
    }

    /**
     * 创建配置工厂（自定义翻译前缀）。
     */
    public static ConfigFactory of(String modId, String translationPrefix) {
        return new ConfigFactory(modId, translationPrefix);
    }

    public String getModId() {
        return this.modId;
    }

    public String getTranslationPrefix() {
        return this.translationPrefix;
    }

    /**
     * 本工厂创建过的全部配置项（保存 / 加载用）。
     */
    public ImmutableList<IConfigBase> getOptions() {
        return ImmutableList.copyOf(this.options);
    }

    // ------------------------------------------------------------------
    //  链式构建器（只填 key）
    // ------------------------------------------------------------------

    public UiConfigBoolean.Builder bool(String name) {
        return new UiConfigBoolean.Builder(this.translationPrefix, name)
                .onBuild(this.options::add);
    }

    public UiConfigInteger.Builder integer(String name) {
        return new UiConfigInteger.Builder(this.translationPrefix, name)
                .onBuild(this.options::add);
    }

    public UiConfigDouble.Builder doubleConfig(String name) {
        return new UiConfigDouble.Builder(this.translationPrefix, name)
                .onBuild(this.options::add);
    }

    public UiConfigFloat.Builder floatConfig(String name) {
        return new UiConfigFloat.Builder(this.translationPrefix, name)
                .onBuild(this.options::add);
    }

    public UiConfigString.Builder string(String name) {
        return new UiConfigString.Builder(this.translationPrefix, name)
                .onBuild(this.options::add);
    }

    public UiConfigColor.Builder color(String name) {
        return new UiConfigColor.Builder(this.translationPrefix, name)
                .onBuild(this.options::add);
    }

    public UiConfigHotkey.Builder hotkey(String name) {
        return new UiConfigHotkey.Builder(this.translationPrefix, name)
                .onBuild(this.options::add);
    }

    public UiConfigStringList.Builder stringList(String name) {
        return new UiConfigStringList.Builder(this.translationPrefix, name)
                .onBuild(this.options::add);
    }

    public UiConfigOptionList.Builder optionList(String name) {
        return new UiConfigOptionList.Builder(this.translationPrefix, name)
                .onBuild(this.options::add);
    }

    public UiConfigBooleanHotkeyed.Builder booleanHotkey(String name) {
        return new UiConfigBooleanHotkeyed.Builder(this.translationPrefix, name)
                .onBuild(this.options::add);
    }

    // ------------------------------------------------------------------
    //  分组与折叠
    // ------------------------------------------------------------------

    /**
     * 创建折叠分组（配置列表中的分组标题，可点击折叠 / 展开）。
     *
     * @param titleKey 分组标题的翻译键
     */
    public ConfigFold fold(String titleKey) {
        return new ConfigFold(this.modId, titleKey);
    }

    /**
     * 创建配置分组：分组名会自动拼接翻译键 {@code <modId>.gui.group.<name>}，
     * 因此只需传简短的分组名（例如 {@code "general"}、{@code "test.multi"}）。
     *
     * @param name 分组名
     */
    public ConfigGroup group(String name) {
        return new ConfigGroup(this, this.fold(this.modId + ".gui.group." + name));
    }
}
