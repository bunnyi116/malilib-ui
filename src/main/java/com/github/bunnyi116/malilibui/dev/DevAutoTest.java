package com.github.bunnyi116.malilibui.dev;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import com.github.bunnyi116.malilibui.MalilibUiMod;
import fi.dy.masa.malilib.gui.GuiBase;
import fi.dy.masa.malilib.registry.Registry;
import fi.dy.masa.malilib.util.GuiUtils;
import fi.dy.masa.malilib.util.data.ModInfo;

/**
 * 仅供开发期使用的自动测试钩子。
 *
 * <p>通过 {@code -Dmalilib-ui.autotest=<modId>} 或环境变量启用；到达标题界面后，
 * 会自动打开指定 masa 模组的配置界面，以便外部截图工具捕获渲染结果。</p>
 */
public final class DevAutoTest {
    public static final String PROPERTY = "malilib-ui.autotest";
    public static final String ENV = "MALILIB_UI_AUTOTEST";

    private static int ticks;
    private static boolean opened;
    private static boolean reported;

    private DevAutoTest() {
    }

    private static String getTarget() {
        String target = System.getProperty(PROPERTY);

        if (target == null || target.isEmpty()) {
            target = System.getenv(ENV);
        }

        return target;
    }

    public static void onClientTick(Minecraft mc) {
        String target = getTarget();

        if (target == null || target.isEmpty() || opened) {
            return;
        }

        Screen current = GuiUtils.getCurrentScreen();

        if (!(current instanceof TitleScreen)) {
            return;
        }

        if (++ticks < 60) {
            return;
        }

        opened = true;

        try {
            for (ModInfo mod : Registry.CONFIG_SCREEN.getAllModsWithConfigScreens()) {
                if (mod.modId().equals(target) && mod.configScreenSupplier() != null) {
                    GuiBase screen = mod.configScreenSupplier().get();
                    GuiBase.openGui(screen);
                    MalilibUiMod.LOGGER.info("[autotest] Opened config screen of '{}'", target);
                    return;
                }
            }

            if (!reported) {
                reported = true;
                MalilibUiMod.LOGGER.warn("[autotest] No config screen registered for '{}'", target);
            }
        } catch (Throwable t) {
            MalilibUiMod.LOGGER.error("[autotest] Failed to open config screen of '{}'", target, t);
        }
    }
}
