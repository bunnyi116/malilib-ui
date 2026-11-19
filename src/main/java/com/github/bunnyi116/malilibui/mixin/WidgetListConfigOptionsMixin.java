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

        return list.getMaxNameLengthWrapped(wrappers);
    }
}
