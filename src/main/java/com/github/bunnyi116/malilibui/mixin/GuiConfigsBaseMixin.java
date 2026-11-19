package com.github.bunnyi116.malilibui.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import fi.dy.masa.malilib.gui.GuiBase;
import fi.dy.masa.malilib.gui.GuiConfigsBase;
import com.github.bunnyi116.malilibui.gui.ConfigLayout;
import com.github.bunnyi116.malilibui.gui.ConfigScreenManager;

/**
 * 把受管配置界面的浏览器尺寸重定向到新布局尺寸。
 *
 * <p>选项列表控件在构造时会向屏幕查询浏览器宽高（搜索栏与条目区域宽度也在此计算），
 * 因此该重定向能让重建后的列表及其搜索栏使用右侧内容区的宽度。</p>
 */
@Mixin(GuiConfigsBase.class)
public abstract class GuiConfigsBaseMixin {
    @Inject(method = "getBrowserWidth", at = @At("RETURN"), cancellable = true)
    private void malilibui$getBrowserWidth(CallbackInfoReturnable<Integer> cir) {
        ConfigLayout layout = ConfigScreenManager.layoutFor((GuiBase) (Object) this);

        if (layout != null) {
            cir.setReturnValue(layout.listW);
        }
    }

    @Inject(method = "getBrowserHeight", at = @At("RETURN"), cancellable = true)
    private void malilibui$getBrowserHeight(CallbackInfoReturnable<Integer> cir) {
        ConfigLayout layout = ConfigScreenManager.layoutFor((GuiBase) (Object) this);

        if (layout != null) {
            cir.setReturnValue(layout.listH);
        }
    }
}
