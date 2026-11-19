package com.github.bunnyi116.malilibui.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import fi.dy.masa.malilib.gui.GuiBase;
import fi.dy.masa.malilib.gui.GuiConfigsBase;
import fi.dy.masa.malilib.gui.button.ButtonBase;
import fi.dy.masa.malilib.gui.widgets.WidgetDropDownList;
import fi.dy.masa.malilib.render.GuiContext;
import fi.dy.masa.malilib.util.data.ModInfo;
import com.github.bunnyi116.malilibui.gui.ConfigScreenManager;
import com.github.bunnyi116.malilibui.mixin.accessor.GuiConfigsBaseAccessor;
import com.github.bunnyi116.malilibui.mixin.accessor.WidgetDropDownListAccessor;

/**
 * 重制的入口：挂钩 malilib 的 {@link GuiBase}，
 * 使所有 masa 配置界面（均继承 {@code GuiConfigsBase}）都会被重新排布与绘制。
 */
@Mixin(GuiBase.class)
public abstract class GuiBaseMixin {
    @Inject(method = "init", at = @At("TAIL"))
    private void malilibui$afterInit(CallbackInfo ci) {
        ConfigScreenManager.ensureLayout((GuiBase) (Object) this);
    }

    @Inject(method = "extractRenderState", at = @At("HEAD"))
    private void malilibui$beforeRender(GuiGraphicsExtractor drawContext, int mouseX, int mouseY,
                                        float partialTicks, CallbackInfo ci) {
        ConfigScreenManager.ensureLayout((GuiBase) (Object) this);
    }

    @Inject(method = "extractRenderState", at = @At("TAIL"))
    private void malilibui$afterRender(GuiGraphicsExtractor drawContext, int mouseX, int mouseY,
                                       float partialTicks, CallbackInfo ci) {
        GuiBase screen = (GuiBase) (Object) this;

        if (!(screen instanceof GuiConfigsBase configs) || !ConfigScreenManager.hasLayout(screen)) {
            return;
        }

        // 模组切换下拉框是普通 widget，会先于选项列表绘制；
        // 展开时其下拉面板会被列表再次覆盖。因此在帧末重新绘制一次，
        // 使其始终浮在内容之上。
        WidgetDropDownList<ModInfo> switcher = ((GuiConfigsBaseAccessor) configs).malilibui$getModSwitchWidget();

        if (switcher != null && ((WidgetDropDownListAccessor) switcher).malilibui$isOpen()) {
            GuiContext ctx = GuiContext.fromGuiGraphics(drawContext);
            switcher.postRenderHovered(ctx, mouseX, mouseY, false);
        }
    }

    @Inject(method = "drawScreenBackground", at = @At("HEAD"), cancellable = true)
    private void malilibui$drawScreenBackground(GuiContext ctx, int mouseX, int mouseY, CallbackInfo ci) {
        GuiBase screen = (GuiBase) (Object) this;

        if (ConfigScreenManager.hasLayout(screen)) {
            ConfigScreenManager.drawBackground(screen, ctx, mouseX, mouseY);
            ci.cancel();
        }
    }

    @Inject(method = "drawTitle", at = @At("HEAD"), cancellable = true)
    private void malilibui$drawTitle(GuiContext ctx, int mouseX, int mouseY, float partialTicks, CallbackInfo ci) {
        if (ConfigScreenManager.hasLayout((GuiBase) (Object) this)) {
            ci.cancel();
        }
    }

    @Redirect(method = "drawButtons",
            at = @At(value = "INVOKE",
                    target = "Lfi/dy/masa/malilib/gui/button/ButtonBase;" +
                            "render(Lfi/dy/masa/malilib/render/GuiContext;IIZ)V"))
    private void malilibui$renderButton(ButtonBase button, GuiContext ctx, int mouseX, int mouseY, boolean selected) {
        GuiBase screen = (GuiBase) (Object) this;

        if (ConfigScreenManager.isNavButton(screen, button) &&
                ConfigScreenManager.drawNavButton(screen, button, ctx, mouseX, mouseY)) {
            return;
        }

        button.render(ctx, mouseX, mouseY, selected);
    }
}
