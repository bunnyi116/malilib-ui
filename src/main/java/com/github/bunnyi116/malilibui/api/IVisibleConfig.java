package com.github.bunnyi116.malilibui.api;

import java.util.function.BooleanSupplier;

import fi.dy.masa.malilib.config.IConfigBase;
import fi.dy.masa.malilib.config.options.ConfigBoolean;

/**
 * 带动态可见性的配置项接口。
 *
 * <p>本模组继承 malilib 的各配置类型并实现该接口（例如 {@code UiConfigBoolean}），
 * 配置项创建后可以直接链式声明可见性：</p>
 *
 * <pre>{@code
 * UiConfigBoolean master = GROUP.bool("master", true);
 * UiConfigBoolean child = GROUP.bool("child", true)
 *         .visibleWhen(master::getBooleanValue);   // 返回 UiConfigBoolean 本身
 * }</pre>
 *
 * @param <T> 配置项自身类型（链式返回）
 */
public interface IVisibleConfig<T extends IVisibleConfig<T>> extends IConfigBase {
    /**
     * 注册动态可见性条件：条件返回 false 时该配置项从配置列表隐藏。
     *
     * @return 配置项本身（便于链式调用）
     */
    @SuppressWarnings("unchecked")
    default T visibleWhen(BooleanSupplier condition) {
        MalilibUiApi.registerVisibility(this, condition);

        return (T) this;
    }

    /**
     * 依赖另一个布尔配置项为 {@code true} 时可见。
     *
     * @return 配置项本身（便于链式调用）
     * @throws IllegalArgumentException {@code master} 不是布尔配置项
     */
    @SuppressWarnings("unchecked")
    default T visibleWhen(IConfigBase master) {
        if (!(master instanceof ConfigBoolean booleanConfig)) {
            throw new IllegalArgumentException("visibleWhen() requires a boolean master config");
        }

        MalilibUiApi.registerVisibility(this, booleanConfig::getBooleanValue);

        return (T) this;
    }

    /** {@link #visibleWhen(BooleanSupplier)} 的别名。 */
    default T setVisible(BooleanSupplier condition) {
        return this.visibleWhen(condition);
    }

    /** {@link #visibleWhen(IConfigBase)} 的别名。 */
    default T setVisible(IConfigBase master) {
        return this.visibleWhen(master);
    }
}
