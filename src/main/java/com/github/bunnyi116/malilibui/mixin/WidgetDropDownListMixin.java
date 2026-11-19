package com.github.bunnyi116.malilibui.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import fi.dy.masa.malilib.gui.GuiConfigsBase;
import fi.dy.masa.malilib.gui.widgets.WidgetDropDownList;
import fi.dy.masa.malilib.util.GuiUtils;

import com.github.bunnyi116.malilibui.mixin.accessor.WidgetDropDownListAccessor;
import com.github.bunnyi116.malilibui.config.UiConfigs;

/**
 * 下拉框展开时，把滚轮事件标记为已消费，
 * 避免在滚动下拉列表的同时也滚动下方的配置列表（事件穿透）。
 */
@Mixin(WidgetDropDownList.class)
public abstract class WidgetDropDownListMixin {
    @Inject(method = "onMouseScrolledImpl", at = @At("RETURN"), cancellable = true)
    private void malilibui$consumeScrollWheel(double mouseX, double mouseY, double horizontalAmount,
                                              double verticalAmount, CallbackInfoReturnable<Boolean> cir) {
        if (!UiConfigs.General.ENABLED.getBooleanValue()) {
            return;
        }

        if (!(GuiUtils.getCurrentScreen() instanceof GuiConfigsBase)) {
            return;
        }

        if (((WidgetDropDownListAccessor) this).malilibui$isOpen()) {
            cir.setReturnValue(true);
        }
    }
}
