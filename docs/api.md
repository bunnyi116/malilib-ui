# MaLiLib UI 开发者 API

本模组只做两件事：

1. 在运行时重写 masa 系列模组的配置界面（malilib 的 `GuiConfigsBase`）布局；
2. 给配置界面新增两个能力：**分组折叠**、**选项动态可见性**。

本模组**不接管**配置保存 / 加载 / 界面注册——这些仍由各模组按 malilib 原生方式处理。

## API 结构

```
api/
  IVisibleConfig        ← 可见性接口（default 方法 visibleWhen）
  ConfigFactory         ← 配置工厂：传入 modId，创建下面的类型
  config/
    UiConfigBoolean     ← 继承 ConfigBoolean，实现可见性接口，带构造器
    UiConfigInteger     ← 继承 ConfigInteger ...
    UiConfigDouble / UiConfigFloat / UiConfigString / UiConfigColor
    UiConfigHotkey / UiConfigStringList / UiConfigOptionList
    ConfigFold          ← 折叠类型（分组标题，可折叠/展开）
  MalilibUiApi          ← 工具：给任意配置注册可见性、控制折叠、查询状态
```

- API 版本：`MalilibUiApi.API_VERSION`（当前为 `1`）

## 依赖方式

```json
// fabric.mod.json
{
  "depends": {
    "minecraft": ">=26.3",
    "malilib": ">=0.30.1",
    "malilib-ui": ">=0.1.0"
  }
}
```

```groovy
// build.gradle：把本模组 jar 放进 libs/
dependencies {
    modImplementation files("libs/malilib-ui-26.3-0.1.0.jar")
}
```

> 本模组是客户端模组（`environment: client`），你的模组也应当是客户端模组。

## 快速开始

```java
// 1. 配置工厂：传入 modId（翻译前缀默认 <modId>.config），自动收集全部配置项
ConfigFactory FACTORY = ConfigFactory.of("examplemod");

// 2. 创建分组（分组名自动拼成 examplemod.gui.group.general），在分组上创建配置项
ConfigGroup general = FACTORY.group("general");

UiConfigBoolean master = general.bool("master").defaultValue(true).build();
UiConfigBoolean child  = general.bool("child").defaultValue(true).build();
UiConfigInteger count  = general.integer("count").defaultValue(5).range(0, 10).build();

// 3. 动态可见性
child.visibleWhen(master::getBooleanValue);
count.visibleWhen(() -> master.getBooleanValue() && child.getBooleanValue());

// 4. 链式（可不初始化默认值）
UiConfigBoolean advanced = general.bool("advanced")
        .defaultValue(false)
        .visibleWhen(master::getBooleanValue)
        .build();

// 5. 保存/加载
ImmutableList<IConfigBase> OPTIONS = FACTORY.getOptions();  // 全部配置项
```

在配置界面的 `getConfigs()` 里把分组转换为界面条目：

```java
@Override
public List<ConfigOptionWrapper> getConfigs() {
    List<ConfigOptionWrapper> entries = new ArrayList<>();
    entries.add(new ConfigOptionWrapper(general.getTitle()));   // 分组标题（可折叠）

    for (IConfigBase config : general.getConfigs()) {
        entries.add(new ConfigOptionWrapper(config));
    }

    return entries;
}
```

保存 / 加载也用 malilib 原生方式（`ConfigUtils` + `FACTORY.getOptions()`），没有额外要求。

## 配置类型

| 工厂/分组方法 | 返回构建器 | 链式设置 |
| --- | --- | --- |
| `bool(name)` | `UiConfigBoolean.Builder` | `defaultValue(boolean)` |
| `integer(name)` | `UiConfigInteger.Builder` | `defaultValue(int)` / `range(min, max)` / `minValue` / `maxValue` |
| `doubleConfig(name)` | `UiConfigDouble.Builder` | `defaultValue(double)` / `range` |
| `floatConfig(name)` | `UiConfigFloat.Builder` | `defaultValue(float)` / `range` |
| `string(name)` | `UiConfigString.Builder` | `defaultValue(String)` |
| `color(name)` | `UiConfigColor.Builder` | `defaultValue(String)` |
| `hotkey(name)` | `UiConfigHotkey.Builder` | `defaultValue(String)` / `defaultStorageString` / `keybindSettings` |
| `stringList(name)` | `UiConfigStringList.Builder` | `defaultValue(List<String>)` |
| `optionList(name)` | `UiConfigOptionList.Builder` | `defaultValue(IConfigOptionListEntry)` |
| `booleanHotkey(name)` | `UiConfigBooleanHotkeyed.Builder` | `defaultValue(boolean)` / `defaultHotkey` / `keybindSettings` |

所有创建方法**只填配置 key**，其余通过链式设置后 `.build()`。
| `factory.fold(titleKey)` | `ConfigFold` | `(modId, titleKey)` |

所有 `UiConfig*` 类型：
- 都是 malilib 对应配置类的**子类**（可直接 cast 回 `ConfigBoolean` 等，兼容任何 malilib API）；
- 构造器接收**翻译前缀**并自动 `apply`，不再需要每个配置项手写；
- 实现 `IVisibleConfig` 接口，自带 `visibleWhen(...)`。

### 选项列表枚举（翻译键自动拼接）

实现 `IUiOptionListEntry` 的枚举只需写**短值名**，显示名由配置项前缀自动拼接为
`<prefix>.option.<值名>`（例如 `examplemod.config.option.mode_a`）：

```java
public enum MyMode implements IUiOptionListEntry {
    MODE_A("mode_a"),
    MODE_B("mode_b");

    private final String value;

    MyMode(String value) {
        this.value = value;
    }

    @Override
    public String getValueName() {
        return this.value;
    }

    @Override
    public String getStringValue() {
        return this.value;
    }

    @Override
    public String getDisplayName() {
        return OptionListTranslations.translate(this);
    }

    @Override
    public IConfigOptionListEntry cycle(boolean forward) {
        return ...;
    }

    @Override
    public IConfigOptionListEntry fromString(String value) {
        return ...;
    }
}
```

对应语言文件只写短键：

```json
{
  "examplemod.config.option.mode_a": "模式 A",
  "examplemod.config.option.mode_b": "模式 B"
}
```

配置项创建时（`group.optionList(...)`）会自动登记前缀，不需要手动处理翻译键。

## 链式构建器（可不初始化默认值）

如果希望延迟设置默认值，使用**不带默认值**的工厂重载，得到对应类型的构建器：

```java
UiConfigBoolean enabled = factory.bool("enabled")          // 不传默认值
        .defaultValue(true)                                 // 链式设置
        .visibleWhen(master::getBooleanValue)               // 可见性也在构建器上
        .build();

UiConfigInteger count = factory.integer("count")
        .defaultValue(5)
        .range(0, 100)
        .build();

UiConfigFloat speed = factory.floatConfig("speed")
        .defaultValue(1.5f)
        .range(0.0f, 10.0f)
        .build();

UiConfigStringList list = factory.stringList("list")
        .defaultValue(List.of("alpha", "beta"))
        .build();

UiConfigOptionList mode = factory.optionList("mode")
        .defaultValue(MyEnum.OPTION_A)
        .build();
```

构建器类型：`UiConfigBoolean.Builder`、`UiConfigInteger.Builder`、`UiConfigDouble.Builder`、
`UiConfigFloat.Builder`、`UiConfigString.Builder`、`UiConfigColor.Builder`、`UiConfigHotkey.Builder`、
`UiConfigStringList.Builder`、`UiConfigOptionList.Builder`。

| 构建器 | 链式方法 | 不设置时的默认值 |
| --- | --- | --- |
| Boolean | `defaultValue(boolean)` | `false` |
| Boolean + Hotkey | `defaultValue(boolean)` / `defaultHotkey(String)` / `keybindSettings(KeybindSettings)` | `false` / 空热键 / `KeybindSettings.DEFAULT` |
| Integer | `defaultValue(int)` / `range(min, max)` / `minValue()` / `maxValue()` | `0` / 全范围 |
| Double / Float | `defaultValue(...)` / `range(...)` | `0.0` / 全范围 |
| String | `defaultValue(String)` | `""` |
| Color | `defaultValue(String)` | `"#FFFFFF"` |
| Hotkey | `defaultValue(String)` | `""` |
| StringList | `defaultValue(List<String>)` | 空列表 |
| OptionList | `defaultValue(IConfigOptionListEntry)` | `IConfigOptionListEntry.empty()` |

所有构建器都支持 `.visibleWhen(条件或布尔配置)` 与 `.build()`；
`build()` 会创建对应的 `UiConfig*` 配置项并应用已设置的可见性。

## 动态可见性（IVisibleConfig）

```java
// 依赖另一个布尔配置
child.visibleWhen(master::getBooleanValue);

// 自定义条件
count.visibleWhen(() -> master.getBooleanValue() && other.getIntegerValue() > 5);

// 依赖另一个配置对象（要求其为布尔配置）
child.visibleWhen(master);
```

行为：

- 条件每 tick 检查一次，变化后列表自动刷新；
- 某分组下配置项**全部不可见**时，分组标题一起隐藏；
- 用户手动折叠的分组保留标题（便于展开），组内选项隐藏；
- 关闭「启用界面重制」时界面恢复原版，注册信息保留。

### setVisible 别名

`visibleWhen(...)` 也提供 `setVisible(...)` 别名（与常见配置风格一致）：

```java
UiConfigInteger count = GROUP.integer("count", 5, 0, 100)
        .setVisible(master::getBooleanValue);

// 构建器上同样可用
GROUP.integer("count")
        .defaultValue(5)
        .setVisible(master::getBooleanValue)
        .build();
```

## 折叠类型（ConfigFold）

```java
ConfigFold group = factory.fold("examplemod.group.general");

group.getTitle();            // 分组标题显示文本（也是折叠状态标识）
group.isCollapsed();         // 查询是否折叠
group.setCollapsed(true);    // 设置
group.toggle();              // 切换
```

界面上所有 `LABEL` 行（分组标题）默认可点击折叠，`ConfigFold` 只是把「分组标题 + 折叠状态」封装成一个类型，方便在代码里创建和操作用。

## 配置分组（ConfigGroup）

手写 `new ConfigOptionWrapper(...)` 列表太啰嗦，用 `FACTORY.group(...)` 直接组装分组：

```java
// 创建分组，并在分组上创建配置项
ConfigGroup general = FACTORY.group("general");     // → examplemod.gui.group.general

UiConfigBoolean enabled = general.bool("enabled", true);          // 一步创建
UiConfigInteger count = general.integer("count").defaultValue(5).range(0, 100).build();
UiConfigInteger max = general.integer("max")                      // 链式创建
        .defaultValue(10)
        .range(0, 100)
        .build();

// 配置界面的 getConfigs() 直接返回
@Override
public List<ConfigOptionWrapper> getConfigs() {
    return general.getEntries();
}

// 多个分组合并（多分组 / 多 TAB）
return ConfigGroup.entries(general, advanced, experimental);
```

| 方法 | 说明 |
| --- | --- |
| `FACTORY.group(name)` | 创建分组（自动拼接 `<modId>.gui.group.<name>` 翻译键） |
| `group.bool/integer/... (name, def...)` | 在分组上创建配置项（登记到分组与 `FACTORY.getOptions()`） |
| `group.bool(name)` 等 | 返回对应 `UiConfig*.Builder`（链式，可不初始化默认值） |
| `group.getConfigs()` | 组内配置项（保存/加载用，界面条目由 `getConfigs()` 自行转换） |
| `FACTORY.getOptions()` | 工厂创建过的全部配置项 |

分组标题的翻译键规则：`FACTORY.group("general")` → `examplemod.gui.group.general`，
`FACTORY.group("test.multi")` → `examplemod.gui.group.test.multi`。

### 不使用分组（平铺列表）

完全不使用 `ConfigGroup` 也可以：直接用工厂创建配置项，界面里平铺展示（没有任何分组标题）。

```java
// 定义：全部配置项直接由工厂创建（自动登记到 FACTORY.getOptions()）
public static final UiConfigBoolean ENABLED =
        FACTORY.bool("enabled").defaultValue(true).build();
public static final UiConfigInteger COUNT =
        FACTORY.integer("count").defaultValue(5).range(0, 100).build();
public static final UiConfigBoolean CHILD =
        FACTORY.bool("child").defaultValue(false).visibleWhen(ENABLED::getBooleanValue).build();

// 界面：平铺列表（无分组标题）
@Override
public List<ConfigOptionWrapper> getConfigs() {
    return FACTORY.getOptions().stream()
            .map(ConfigOptionWrapper::new)
            .toList();
}

// 保存/加载：同样使用 FACTORY.getOptions()
```

也可以混合使用：部分配置项放在 `ConfigGroup` 里（带可折叠分组标题），
部分直接创建并被界面单独追加——`FACTORY.getOptions()` 始终包含全部配置项。

### 没有 TAB（单页面）时

不需要任何额外抽象，直接返回分组条目即可：

```java
// 单分组
@Override
public List<ConfigOptionWrapper> getConfigs() {
    List<ConfigOptionWrapper> entries = new ArrayList<>();
    entries.add(new ConfigOptionWrapper(GENERAL.getTitle()));

    for (IConfigBase config : GENERAL.getConfigs()) {
        entries.add(new ConfigOptionWrapper(config));
    }

    return entries;
}

// 多分组（仍是同一个页面，分组标题作为可折叠的分隔）
@Override
public List<ConfigOptionWrapper> getConfigs() {
    return ConfigGroup.entries(GENERAL, ADVANCED);
}

// 保存 / 加载用的配置项列表
List<IConfigBase> configs = ConfigGroup.allConfigs(GENERAL, ADVANCED);
```

有 TAB 时，每个 TAB 返回各自的 `ConfigGroup.entries(...)` 即可（本模组自己的界面就是这样做的）。

## 工具 API（MalilibUiApi）

给**任意**配置项（不一定是本模组的类型）注册可见性：

```java
MalilibUiApi.registerVisibility(config, () -> master.getBooleanValue());
MalilibUiApi.unregisterVisibility(config);
```

折叠 / 状态查询：

```java
boolean collapsed = MalilibUiApi.isGroupCollapsed("litematica", "通用");
MalilibUiApi.setGroupCollapsed("litematica", "通用", true);
MalilibUiApi.toggleGroup("litematica", "通用");
boolean enabled = MalilibUiApi.isRestylingEnabled();
```

`groupLabel` 传列表中实际显示的标题文本（翻译后的结果）。
