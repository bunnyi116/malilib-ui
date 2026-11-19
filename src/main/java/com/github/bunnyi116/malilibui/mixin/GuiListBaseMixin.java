package com.github.bunnyi116.malilibui.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;

import fi.dy.masa.malilib.gui.GuiBase;
import fi.dy.masa.malilib.gui.GuiListBase;

import com.github.bunnyi116.malilibui.gui.ConfigLayout;
import com.github.bunnyi116.malilibui.gui.ConfigScreenManager;

/**
 * 重定向列表控件的创建坐标。
 *
 * <p>不修改 GuiListBase 内部的 listX/listY 字段（它们是 private 的，且被
 * {@code reCreateListWidget()} 直接读取）：列表控件创建时使用新布局坐标，
 * 关闭重制后自动回到原版坐标，避免"禁用重制后列表还停在改造位置"。</p>
 */
@Mixin(GuiListBase.class)
public abstract class GuiListBaseMixin {
    @ModifyArgs(method = "reCreateListWidget",
            at = @At(value = "INVOKE",
                    target = "Lfi/dy/masa/malilib/gui/GuiListBase;" +
                            "createListWidget(II)Lfi/dy/masa/malilib/gui/widgets/WidgetListBase;"))
    private void malilibui$redirectListPosition(Args args) {
        ConfigLayout layout = ConfigScreenManager.layoutFor((GuiBase) (Object) this);

        if (layout != null) {
            args.set(0, layout.listX);
            args.set(1, layout.listY);
        }
    }
}
