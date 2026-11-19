package com.github.bunnyi116.malilibui.gui;

import java.util.ArrayList;
import java.util.List;

import fi.dy.masa.malilib.config.IConfigBase;
import fi.dy.masa.malilib.gui.GuiConfigsBase;
import fi.dy.masa.malilib.gui.GuiConfigsBase.ConfigOptionWrapper;
import fi.dy.masa.malilib.gui.button.ButtonBase;
import fi.dy.masa.malilib.gui.button.ButtonGeneric;
import fi.dy.masa.malilib.gui.button.IButtonActionListener;
import fi.dy.masa.malilib.util.StringUtils;

import com.github.bunnyi116.malilibui.MalilibUiMod;
import com.github.bunnyi116.malilibui.api.config.ConfigGroup;
import com.github.bunnyi116.malilibui.config.UiConfigs;

/**
 * 本模组自身的配置界面。它注册到 malilib 的配置界面注册表中，
 * 因此也会出现在任意 masa 配置界面右上角的模组切换下拉框中。
 *
 * <p>界面分为「通用 / 布局 / 测试」三个 TAB；配置定义阶段只维护 {@link ConfigGroup}
 * 与配置项，界面条目（{@link ConfigOptionWrapper}）在 {@link #getConfigs()} 里转换。</p>
 */
public class MalilibUiConfigScreen extends GuiConfigsBase {
    private static Tab currentTab = Tab.GENERAL;

    public MalilibUiConfigScreen() {
        super(10, 50, MalilibUiMod.MOD_ID, null, "malilib-ui.gui.title.configs");
    }

    @Override
    public void initGui() {
        super.initGui();

        int x = 10;
        int y = 26;

        for (Tab tab : Tab.values()) {
            x += createButton(x, y, -1, tab);
        }
    }

    private int createButton(int x, int y, int width, Tab tab) {
        ButtonGeneric button = new ButtonGeneric(x, y, width, 20, tab.getDisplayName());
        button.setEnabled(currentTab != tab);
        this.addButton(button, new ButtonListener(tab, this));

        return button.getWidth() + 2;
    }

    @Override
    public List<ConfigOptionWrapper> getConfigs() {
        return switch (currentTab) {
            case GENERAL -> entries(UiConfigs.General.GROUP);
            case LAYOUT -> entries(UiConfigs.Layout.GROUP);
            case TEST -> entries(UiConfigs.Test.MULTI_GROUP, UiConfigs.Test.CONDITIONAL_GROUP,
                    UiConfigs.Test.BUILDER_GROUP, UiConfigs.Test.WIDGETS_GROUP);
      
            case FLAT -> entries(UiConfigs.Flat.OPTIONS);
        };
    }

    /**
     * 把配置分组转换为界面条目（分组标题 + 配置项）。
     */
    private static List<ConfigOptionWrapper> entries(ConfigGroup... groups) {
        List<ConfigOptionWrapper> entries = new ArrayList<>();

        for (ConfigGroup group : groups) {
            entries.add(new ConfigOptionWrapper(group.getTitle()));

            for (IConfigBase config : group.getConfigs()) {
                entries.add(new ConfigOptionWrapper(config));
            }
        }

        return entries;
    }

    private static List<ConfigOptionWrapper> entries(List<IConfigBase> configs) {
        return configs.stream().map(ConfigOptionWrapper::new).toList();
    }

    private record ButtonListener(Tab tab, MalilibUiConfigScreen parent) implements IButtonActionListener {
        @Override
        public void actionPerformedWithButton(ButtonBase button, int mouseButton) {
            currentTab = this.tab;
            this.parent.reCreateListWidget();

            if (this.parent.getListWidget() != null) {
                this.parent.getListWidget().resetScrollbarPosition();
            }

            this.parent.initGui();
        }
    }

    public enum Tab {
        GENERAL("malilib-ui.gui.tab.general"),
        LAYOUT("malilib-ui.gui.tab.layout"),
        TEST("malilib-ui.gui.tab.test"),
        FLAT("malilib-ui.gui.tab.flat");

        private final String tabKey;

        Tab(String tabKey) {
            this.tabKey = tabKey;
        }

        public String getDisplayName() {
            return StringUtils.translate(this.tabKey);
        }
    }
}
