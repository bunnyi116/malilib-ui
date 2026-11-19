package com.github.bunnyi116.malilibui.gui;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BooleanSupplier;

import fi.dy.masa.malilib.config.IConfigBase;
import fi.dy.masa.malilib.gui.GuiConfigsBase.ConfigOptionWrapper;

/**
 * 配置列表的条目过滤：可折叠分组 + 动态可见性。
 *
 * <p>折叠：列表中的 {@code LABEL} 行被当作分组标题，点击后该分组下的配置项会被隐藏，
 * 再次点击展开。所有 masa 配置界面都适用（不需要各模组提供额外信息）。</p>
 *
 * <p>可见性：配置项可以注册一个可见性条件（例如“半透明背景”关闭时隐藏“背景不透明度”），
 * 条件不满足时该配置项不会出现在列表中；若一个分组下的配置项全部不可见，
 * 该分组的标题也会一起隐藏（折叠中的分组也一样）。</p>
 */
public final class ConfigEntryFilter {
    /** 折叠状态，key 为 {@code modId + "|" + 分组标题}。 */
    private static final Map<String, Boolean> COLLAPSED = new HashMap<>();

    /** 配置项的可见性条件。 */
    private static final Map<IConfigBase, BooleanSupplier> VISIBILITY = new IdentityHashMap<>();

    /** 上一次可见性条件的状态签名，用于检测变化。 */
    private static int lastVisibilityHash = 0;

    private ConfigEntryFilter() {
    }

    /**
     * 为一个配置项注册可见性条件。
     */
    public static void registerVisibility(IConfigBase config, BooleanSupplier condition) {
        VISIBILITY.put(config, condition);
    }

    /**
     * 移除配置项的可见性条件（恢复为始终显示）。
     */
    public static void unregisterVisibility(IConfigBase config) {
        VISIBILITY.remove(config);
    }

    /**
     * 应用折叠与可见性过滤；返回实际要显示在列表中的条目。
     */
    public static List<ConfigOptionWrapper> filter(Collection<?> entries, String modId) {
        List<ConfigOptionWrapper> result = new ArrayList<>(entries.size());

        ConfigOptionWrapper pendingLabel = null;
        boolean pendingCollapsed = false;
        boolean groupHasVisible = false;
        boolean labelEmitted = false;

        for (Object raw : entries) {
            if (!(raw instanceof ConfigOptionWrapper wrapper)) {
                continue;
            }

            if (wrapper.getType() == ConfigOptionWrapper.Type.LABEL) {
                flushLabel(result, pendingLabel, labelEmitted, groupHasVisible);

                String label = wrapper.getLabel();
                pendingLabel = wrapper;
                pendingCollapsed = label != null && isCollapsed(modId, label);
                groupHasVisible = false;
                labelEmitted = false;
                continue;
            }

            IConfigBase config = wrapper.getConfig();

            if (config != null && !isVisible(config)) {
                continue;
            }

            // 折叠状态下也统计可见项：分组内没有任何可见项时，标题也要隐藏。
            groupHasVisible = true;

            if (pendingCollapsed) {
                continue;
            }

            if (!labelEmitted) {
                if (pendingLabel != null) {
                    result.add(pendingLabel);
                }

                labelEmitted = true;
            }

            result.add(wrapper);
        }

        flushLabel(result, pendingLabel, labelEmitted, groupHasVisible);

        return result;
    }

    /**
     * 折叠分组的标题：只要组内存在可见配置项就保留（便于展开），
     * 全部不可见时（即使处于折叠状态）标题也一起隐藏。
     */
    private static void flushLabel(List<ConfigOptionWrapper> result, ConfigOptionWrapper label,
                                   boolean labelEmitted, boolean groupHasVisible) {
        if (label != null && !labelEmitted && groupHasVisible) {
            result.add(label);
        }
    }

    private static boolean isVisible(IConfigBase config) {
        BooleanSupplier condition = VISIBILITY.get(config);

        return condition == null || condition.getAsBoolean();
    }

    public static boolean isCollapsed(String modId, String label) {
        return COLLAPSED.getOrDefault(modId + "|" + label, false);
    }

    /**
     * 直接设置某个分组的折叠状态。
     */
    public static void setCollapsed(String modId, String label, boolean collapsed) {
        COLLAPSED.put(modId + "|" + label, collapsed);
    }

    /**
     * 切换某个分组的折叠状态。
     */
    public static void toggle(String modId, String label) {
        String key = modId + "|" + label;
        COLLAPSED.put(key, !COLLAPSED.getOrDefault(key, false));
    }

    /**
     * 检查可见性条件是否发生了变化（配置值变化后需要重建列表）。
     */
    public static boolean hasVisibilityChanged() {
        int hash = 1;

        for (Map.Entry<IConfigBase, BooleanSupplier> entry : VISIBILITY.entrySet()) {
            hash = hash * 31 + (entry.getValue().getAsBoolean() ? 1 : 0);
        }

        if (hash != lastVisibilityHash) {
            lastVisibilityHash = hash;
            return true;
        }

        return false;
    }

    /**
     * 重置记录的状态（切换界面/重新接管时调用）。
     */
    public static void resetVisibilityHash() {
        lastVisibilityHash = 0;
    }
}
