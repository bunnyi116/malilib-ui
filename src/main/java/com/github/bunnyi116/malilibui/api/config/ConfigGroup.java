package com.github.bunnyi116.malilibui.api.config;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import fi.dy.masa.malilib.config.IConfigBase;

import com.github.bunnyi116.malilibui.api.ConfigFactory;

/**
 * 配置分组：一个可折叠的分组标题 + 组内配置项。
 *
 * <p>在分组上创建配置项时只填写配置 key，其余通过链式构建器设置：</p>
 *
 * <pre>{@code
 * ConfigGroup general = FACTORY.group("general");
 *
 * UiConfigBoolean enabled = general.bool("enabled")
 *         .defaultValue(true)
 *         .build();
 *
 * UiConfigInteger count = general.integer("count")
 *         .defaultValue(5)
 *         .range(0, 100)
 *         .visibleWhen(enabled::getBooleanValue)
 *         .build();
 * }</pre>
 */
public final class ConfigGroup {
    private final ConfigFactory factory;
    private final ConfigFold fold;
    private final List<IConfigBase> configs = new ArrayList<>();

    public ConfigGroup(ConfigFactory factory, ConfigFold fold) {
        this.factory = factory;
        this.fold = fold;
    }

    public ConfigFold getFold() {
        return this.fold;
    }

    public String getTitle() {
        return this.fold.getTitle();
    }

    public List<IConfigBase> getConfigs() {
        return Collections.unmodifiableList(this.configs);
    }

    // ------------------------------------------------------------------
    //  在分组上创建配置项（只填 key，链式设置后 build）
    // ------------------------------------------------------------------

    public UiConfigBoolean.Builder bool(String name) {
        return this.factory.bool(name).onBuild(this.configs::add);
    }

    public UiConfigInteger.Builder integer(String name) {
        return this.factory.integer(name).onBuild(this.configs::add);
    }

    public UiConfigDouble.Builder doubleConfig(String name) {
        return this.factory.doubleConfig(name).onBuild(this.configs::add);
    }

    public UiConfigFloat.Builder floatConfig(String name) {
        return this.factory.floatConfig(name).onBuild(this.configs::add);
    }

    public UiConfigString.Builder string(String name) {
        return this.factory.string(name).onBuild(this.configs::add);
    }

    public UiConfigColor.Builder color(String name) {
        return this.factory.color(name).onBuild(this.configs::add);
    }

    public UiConfigHotkey.Builder hotkey(String name) {
        return this.factory.hotkey(name).onBuild(this.configs::add);
    }

    public UiConfigStringList.Builder stringList(String name) {
        return this.factory.stringList(name).onBuild(this.configs::add);
    }

    public UiConfigOptionList.Builder optionList(String name) {
        return this.factory.optionList(name).onBuild(this.configs::add);
    }

    public UiConfigBooleanHotkeyed.Builder booleanHotkey(String name) {
        return this.factory.booleanHotkey(name).onBuild(this.configs::add);
    }

    // ------------------------------------------------------------------
    //  界面条目
    // ------------------------------------------------------------------

    /**
     * 把多个分组的配置项合并成一个列表（用于保存 / 加载）。
     */
    public static List<IConfigBase> allConfigs(ConfigGroup... groups) {
        List<IConfigBase> configs = new ArrayList<>();

        for (ConfigGroup group : groups) {
            configs.addAll(group.getConfigs());
        }

        return configs;
    }
}
