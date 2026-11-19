package com.github.bunnyi116.malilibui.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.client.input.MouseButtonEvent;

import fi.dy.masa.malilib.gui.widgets.WidgetSlider;
import fi.dy.masa.malilib.render.GuiContext;

import com.github.bunnyi116.malilibui.gui.ConfigScreenManager;
import com.github.bunnyi116.malilibui.mixin.accessor.WidgetSliderAccessor;

/**
 * 跟踪滑条的拖拽状态。
 *
 * <p>拖动滑条（例如侧栏宽度）时配置值会持续变化，若布局在拖动过程中重建，
 * 列表里的滑条控件会被销毁重建，导致拖拽中断。这里把拖拽状态同步给
 * {@link ConfigScreenManager}，在拖动期间冻结布局重建。</p>
 */
@Mixin(WidgetSlider.class)
public abstract class WidgetSliderMixin {
    @Inject(method = "onMouseClickedImpl", at = @At("HEAD"))
    private void malilibui$beginSliderDrag(MouseButtonEvent click, boolean doubleClick,
                                           CallbackInfoReturnable<Boolean> cir) {
        ConfigScreenManager.setSliderDragging(true);
    }

    @Inject(method = "onMouseReleasedImpl", at = @At("HEAD"))
    private void malilibui$endSliderDrag(MouseButtonEvent click, CallbackInfo ci) {
        ConfigScreenManager.setSliderDragging(false);
    }

    @Inject(method = "render", at = @At("HEAD"))
    private void malilibui$trackSliderDrag(GuiContext ctx, int mouseX, int mouseY, boolean selected,
                                           CallbackInfo ci) {
        ConfigScreenManager.markSliderRendering();

        // 每帧根据实际拖拽状态复位标记（即使释放事件没有到达滑条也能恢复）。
        if (!((WidgetSliderAccessor) (Object) this).malilibui$isDragging()) {
            ConfigScreenManager.setSliderDragging(false);
        }
    }
}
