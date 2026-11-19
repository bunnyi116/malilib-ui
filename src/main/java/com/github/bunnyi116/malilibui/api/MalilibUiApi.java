package com.github.bunnyi116.malilibui.api;

import java.util.function.BooleanSupplier;

import fi.dy.masa.malilib.config.IConfigBase;

import com.github.bunnyi116.malilibui.config.UiConfigs;
import com.github.bunnyi116.malilibui.gui.ConfigEntryFilter;

/**
 * MaLiLib UI 的公开 API：供其他模组对本模组重制的配置界面做扩展。
 *
 * <p>当前提供两类能力：</p>
 * <ul>
 *   <li><b>动态可见性</b>：为任意配置项注册可见性条件（条件不满足时该项从列表隐藏）；</li>
 *   <li><b>分组折叠</b>：查询 / 设置 / 切换配置列表中某个分组（LABEL 行）的折叠状态。</li>
 * </ul>
 *
 * <p>注意：这些效果只在本模组接管配置界面（开启「启用界面重制」）时体现在界面上；
 * 关闭重制后界面恢复原版，注册的信息会保留，重新开启后重新生效。</p>
 *
 * <p>第三方模组依赖方式：在 {@code fabric.mod.json} 的 depends 中声明
 * {@code "malilib-ui": ">=0.1.0"}，构建时依赖本模组的 jar 即可。</p>
 */
public final class MalilibUiApi {
    /** API 版本，供第三方做兼容判断；不兼容变更时会递增。 */
    public static final int API_VERSION = 1;

    private MalilibUiApi() {
    }

    /**
     * 注册一个配置项的动态可见性条件。
     *
     * <p>条件返回 {@code false} 时，该配置项会从配置列表中隐藏；返回 {@code true} 时恢复显示。
     * 条件在每 tick 检查一次，发生变化后列表会自动刷新（无需重新打开界面）。
     * 若某个分组下的配置项全部不可见，该分组的标题也会一起隐藏。</p>
     *
     * @param config    目标配置项（实现自 malilib 的 {@link IConfigBase}）
     * @param condition 可见性条件
     */
    public static void registerVisibility(IConfigBase config, BooleanSupplier condition) {
        ConfigEntryFilter.registerVisibility(config, condition);
    }

    /**
     * 移除一个配置项的可见性条件（恢复为始终显示）。
     *
     * @param config 目标配置项
     */
    public static void unregisterVisibility(IConfigBase config) {
        ConfigEntryFilter.unregisterVisibility(config);
    }

    /**
     * 当前是否启用了界面重制。
     */
    public static boolean isRestylingEnabled() {
        return UiConfigs.General.ENABLED.getBooleanValue();
    }

    /**
     * 查询某个配置界面中的分组当前是否处于折叠状态。
     *
     * @param modId      配置界面的模组 ID（例如 "litematica"）
     * @param groupLabel 分组标题在列表中显示的文本（{@code LABEL} 行的文字）
     */
    public static boolean isGroupCollapsed(String modId, String groupLabel) {
        return ConfigEntryFilter.isCollapsed(modId, groupLabel);
    }

    /**
     * 设置某个分组的折叠状态。
     *
     * @param modId      配置界面的模组 ID
     * @param groupLabel 分组标题在列表中显示的文本
     * @param collapsed  true 为折叠，false 为展开
     */
    public static void setGroupCollapsed(String modId, String groupLabel, boolean collapsed) {
        ConfigEntryFilter.setCollapsed(modId, groupLabel, collapsed);
    }

    /**
     * 切换某个分组的折叠状态。
     *
     * @param modId      配置界面的模组 ID
     * @param groupLabel 分组标题在列表中显示的文本
     */
    public static void toggleGroup(String modId, String groupLabel) {
        ConfigEntryFilter.toggle(modId, groupLabel);
    }

}
