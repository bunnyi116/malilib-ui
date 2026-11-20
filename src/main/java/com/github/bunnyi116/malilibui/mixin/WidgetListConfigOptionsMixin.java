package com.github.bunnyi116.malilibui.mixin;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import fi.dy.masa.malilib.gui.GuiConfigsBase.ConfigOptionWrapper;
import fi.dy.masa.malilib.gui.widgets.WidgetListConfigOptions;

import com.github.bunnyi116.malilibui.mixin.accessor.WidgetListBaseAccessor;
import com.github.bunnyi116.malilibui.mixin.accessor.WidgetListConfigOptionsBaseAccessor;

/**
 * 固定配置列表中“名称列”的宽度。
 *
 * <p>malilib 默认用当前显示中的条目计算名称列宽度，导致动态可见性 / 折叠变化后
 * 控件列会左右跳动。这里改用完整条目列表（未过滤）计算，使控件列位置保持稳定。</p>
 */
@Mixin(WidgetListConfigOptions.class)
public abstract class WidgetListConfigOptionsMixin {
    @Redirect(method = "reCreateListEntryWidgets",
            at = @At(value = "INVOKE",
                    target = "Lfi/dy/masa/malilib/gui/widgets/WidgetListConfigOptions;" +
                            "getMaxNameLengthWrapped(Ljava/util/List;)I"))
    private int malilibui$stableLabelWidth(WidgetListConfigOptions list,
                                           List<ConfigOptionWrapper> visibleEntries) {
        Collection<?> allEntries = ((WidgetListBaseAccessor) list).malilibui$getAllEntries();

        if (allEntries == null || allEntries.isEmpty()) {
            return list.getMaxNameLengthWrapped(visibleEntries);
        }

        List<ConfigOptionWrapper> wrappers = new ArrayList<>(allEntries.size());

        for (Object entry : allEntries) {
            if (entry instanceof ConfigOptionWrapper wrapper) {
                wrappers.add(wrapper);
            }
        }

        int labelWidth = list.getMaxNameLengthWrapped(wrappers);

        // 26.x 保持原行为不动；1.21.11 限制标签宽度，避免长名称把
        // 配置控件和“重置”按钮顶出内容面板右侧。
        if (is26x()) {
            return labelWidth;
        }

        int entryWidth = ((WidgetListBaseAccessor) list).malilibui$getBrowserEntryWidth();
        int configWidth = ((WidgetListConfigOptionsBaseAccessor) list).malilibui$getConfigWidth();
        int maxLabelWidth = Math.max(40, entryWidth - configWidth - 54);

        return Math.min(labelWidth, maxLabelWidth);
    }

    private static boolean is26x() {
        try {
            Class.forName("net.minecraft.client.gui.GuiGraphicsExtractor");
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }
}
