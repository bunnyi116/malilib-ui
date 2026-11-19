package com.github.bunnyi116.malilibui.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import net.minecraft.client.Minecraft;
import com.github.bunnyi116.malilibui.dev.DevAutoTest;
import com.github.bunnyi116.malilibui.gui.ConfigScreenManager;

/** 把客户端 tick 转发给开发期的自动测试钩子。 */
@Mixin(Minecraft.class)
public abstract class MinecraftMixin {
    @Inject(method = "tick()V", at = @At("RETURN"))
    private void malilibui$onClientTick(CallbackInfo ci) {
        DevAutoTest.onClientTick((Minecraft) (Object) this);
        ConfigScreenManager.onClientTick();
    }
}
