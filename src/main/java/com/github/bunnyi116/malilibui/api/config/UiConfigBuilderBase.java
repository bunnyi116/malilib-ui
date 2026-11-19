package com.github.bunnyi116.malilibui.api.config;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

import fi.dy.masa.malilib.config.IConfigBase;
import fi.dy.masa.malilib.config.options.ConfigBoolean;

import com.github.bunnyi116.malilibui.api.MalilibUiApi;

/**
 * 配置构建器基类：允许不初始化默认值，链式设置后通过 {@link #build()} 创建配置项。
 *
 * <pre>{@code
 * UiConfigBoolean enabled = factory.bool("enabled")     // 不传默认值
 *         .defaultValue(true)                            // 链式设置
 *         .visibleWhen(master::getBooleanValue)
 *         .build();
 * }</pre>
 */
public abstract class UiConfigBuilderBase<T extends IConfigBase, B extends UiConfigBuilderBase<T, B>> {
    protected final String translationPrefix;
    protected final String name;

    private BooleanSupplier visibility;
    private final List<Consumer<T>> onBuild = new ArrayList<>();

    protected UiConfigBuilderBase(String translationPrefix, String name) {
        this.translationPrefix = translationPrefix;
        this.name = name;
    }

    @SuppressWarnings("unchecked")
    protected B self() {
        return (B) this;
    }

    /** 设置动态可见性条件。 */
    public B visibleWhen(BooleanSupplier condition) {
        this.visibility = condition;

        return this.self();
    }

    /** 依赖另一个布尔配置项为 true 时可见。 */
    public B visibleWhen(IConfigBase master) {
        if (!(master instanceof ConfigBoolean booleanConfig)) {
            throw new IllegalArgumentException("visibleWhen() requires a boolean master config");
        }

        return this.visibleWhen(booleanConfig::getBooleanValue);
    }

    /** {@link #visibleWhen(BooleanSupplier)} 的别名。 */
    public B setVisible(BooleanSupplier condition) {
        return this.visibleWhen(condition);
    }

    /** {@link #visibleWhen(IConfigBase)} 的别名。 */
    public B setVisible(IConfigBase master) {
        return this.visibleWhen(master);
    }

    /** 创建配置项。 */
    public abstract T build();

    /** 追加创建完成后的回调（工厂 / 分组用它收集配置项）。 */
    public B onBuild(Consumer<T> callback) {
        this.onBuild.add(callback);

        return this.self();
    }

    /** 应用已注册的可见性并触发回调（build 内部调用）。 */
    protected T finish(T config) {
        if (this.visibility != null) {
            MalilibUiApi.registerVisibility(config, this.visibility);
        }

        for (Consumer<T> callback : this.onBuild) {
            callback.accept(config);
        }

        return config;
    }
}
