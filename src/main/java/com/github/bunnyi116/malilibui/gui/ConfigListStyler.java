package com.github.bunnyi116.malilibui.gui;

import fi.dy.masa.malilib.gui.GuiConfigsBase;
import fi.dy.masa.malilib.gui.GuiConfigsBase.ConfigOptionWrapper;
import fi.dy.masa.malilib.gui.widgets.WidgetConfigOption;
import fi.dy.masa.malilib.render.GuiContext;
import fi.dy.masa.malilib.util.GuiUtils;

/**
 * 负责列表内单个配置行的样式：交替行背景、悬停高亮与分组标题。
 *
 * <p>注意：malilib 在 {@code listX + 2} 处创建行控件，并把配置名称标签画在同一 x 坐标，
 * 也就是说行控件本身没有左侧内边距。因此悬停背景与其强调条被画进列表左侧的留白区
 * （从行控件左移 {@link #ROW_GUTTER} 像素开始），以保证强调条与文字之间有舒适间距。</p>
 *
 * <p>分组标题行在右侧绘制折叠指示三角（▶ 已折叠 / ▼ 已展开），
 * 提示该标题可以点击折叠 / 展开。</p>
 */
public final class ConfigListStyler {
    /** 行强调条的宽度。 */
    private static final int ACCENT_WIDTH = 2;
    /** 行背景与强调条向列表左侧留白区延伸的距离。 */
    private static final int ROW_GUTTER = 6;
    /** 右侧折叠指示三角的宽度 / 高度。 */
    private static final int FOLD_ARROW_SIZE = 8;

    private ConfigListStyler() {
    }

    public static void drawRow(WidgetConfigOption row, GuiContext ctx, int mouseX, int mouseY) {
        int x = row.getX();
        int y = row.getY();
        int width = row.getWidth();
        int height = row.getHeight();

        if (width <= 0 || height <= 0) {
            return;
        }

        ConfigOptionWrapper wrapper = row.getEntry();

        if (wrapper == null) {
            return;
        }

        boolean isLabel = wrapper.getType() == ConfigOptionWrapper.Type.LABEL;
        boolean hovered = mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;

        if (isLabel) {
            drawSectionHeader(ctx, x, y, width, height, hovered, wrapper.getLabel());
        } else {
            drawOptionRow(ctx, x, y, width, height, hovered, row.getListIndex() % 2 == 1);
        }
    }

    private static void drawOptionRow(GuiContext ctx, int x, int y, int width, int height,
                                      boolean hovered, boolean odd) {
        if (hovered) {
            UiTheme.fill(ctx, x - ROW_GUTTER, y, width + ROW_GUTTER, height, 0x1AFFFFFF);
            UiTheme.fill(ctx, x - ROW_GUTTER, y + 2, ACCENT_WIDTH, height - 4,
                    UiTheme.TEXT_ACCENT);
        } else if (odd) {
            UiTheme.fill(ctx, x - ROW_GUTTER, y, width + ROW_GUTTER, height, 0x0A000000);
        }

        // 行与行之间的细分隔线。
        UiTheme.hline(ctx, x - ROW_GUTTER + 4, y + height - 1, width + ROW_GUTTER - 8,
                0x0CFFFFFF);
    }

    /**
     * 绘制分组标题行：背景、左侧强调条、右侧折叠指示器。
     */
    private static void drawSectionHeader(GuiContext ctx, int x, int y, int width, int height,
                                          boolean hovered, String label) {
        UiTheme.fill(ctx, x - ROW_GUTTER, y, width + ROW_GUTTER, height,
                hovered ? 0x26FFFFFF : 0x18000000);
        UiTheme.fill(ctx, x - ROW_GUTTER, y, ACCENT_WIDTH, height,
                UiTheme.TEXT_ACCENT & 0xAAFFFFFF);

        boolean collapsed = isCollapsed(label);
        int arrowCenterX = x + width - 12;
        int arrowCenterY = y + height / 2;

        // 右侧箭头：提示该分组可以点击折叠 / 展开。
        UiTheme.drawFoldArrow(ctx, arrowCenterX, arrowCenterY, FOLD_ARROW_SIZE, collapsed,
                hovered ? 0xFFFFFFFF : UiTheme.TEXT_TITLE);

        UiTheme.hline(ctx, x - ROW_GUTTER + 4, y + height - 1, width + ROW_GUTTER - 8,
                0x22FFFFFF);
    }

    private static boolean isCollapsed(String label) {
        if (label == null || label.isEmpty()) {
            return false;
        }

        if (GuiUtils.getCurrentScreen() instanceof GuiConfigsBase configs) {
            return ConfigEntryFilter.isCollapsed(configs.getModId(), label);
        }

        return false;
    }
}
