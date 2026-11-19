package com.github.bunnyi116.malilibui.mixin;

import java.util.Collection;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import net.minecraft.client.gui.screens.Screen;

import fi.dy.masa.malilib.gui.GuiConfigsBase;
import fi.dy.masa.malilib.gui.widgets.WidgetListBase;
import fi.dy.masa.malilib.util.GuiUtils;

import com.github.bunnyi116.malilibui.gui.ConfigEntryFilter;
import com.github.bunnyi116.malilibui.gui.ConfigScreenManager;
import com.github.bunnyi116.malilibui.mixin.accessor.WidgetListBaseAccessor;

/**
 * 在配置列表刷新条目时应用折叠与动态可见性过滤。
 *
 * <p>过滤只针对 {@code GuiConfigsBase}（masa 配置界面）列表；其他列表不受影响。</p>
 */
@Mixin(WidgetListBase.class)
public abstract class WidgetListBaseMixin {
    @Redirect(method = "refreshBrowserEntries",
            at = @At(value = "INVOKE",
                    target = "Lfi/dy/masa/malilib/gui/widgets/WidgetListBase;getAllEntries()Ljava/util/Collection;"))
    private Collection<?> malilibui$filterEntries(WidgetListBase<?, ?> list) {
        Collection<?> entries = ((WidgetListBaseAccessor) list).malilibui$getAllEntries();
        Screen current = GuiUtils.getCurrentScreen();

        // 仅在启用重制（布局生效）时应用折叠 / 可见性过滤；
        // 关闭重制后恢复原版列表内容与排序。
        if (entries != null && current instanceof GuiConfigsBase configs &&
                ConfigScreenManager.layoutFor(configs) != null) {
            return ConfigEntryFilter.filter(entries, configs.getModId());
        }

        return entries;
    }
}
