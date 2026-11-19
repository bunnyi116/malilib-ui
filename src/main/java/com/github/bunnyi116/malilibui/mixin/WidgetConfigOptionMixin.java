package com.github.bunnyi116.malilibui.mixin;

import com.github.bunnyi116.malilibui.gui.ConfigListStyler;
import com.github.bunnyi116.malilibui.config.UiConfigs;
import fi.dy.masa.malilib.gui.widgets.WidgetConfigOption;
import fi.dy.masa.malilib.render.GuiContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(WidgetConfigOption.class)
public abstract class WidgetConfigOptionMixin {
    @Inject(method = "render", at = @At("HEAD"))
    private void malilibui$drawRowBackground(GuiContext ctx, int mouseX, int mouseY,
                                             boolean selected, CallbackInfo ci) {
        if (!UiConfigs.General.ENABLED.getBooleanValue()) {
            return;
        }

        ConfigListStyler.drawRow((WidgetConfigOption) (Object) this, ctx, mouseX, mouseY);
    }
}
