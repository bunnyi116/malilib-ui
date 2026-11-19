# malilib GUI 结构与 MC 26.x 三版本差异调研

> 面向 `malilib-ui` 实现者的施工级调研报告。
>
> - 调研对象：malilib 0.28.9（MC 26.1）/ 0.29.3（MC 26.2）/ 0.30.1（MC 26.3），以及 malilib 源码 0.30.2。
> - 证据来源：`D:\source\masa\malilib@sakura-ryoko`（v0.30.2 源码）、`D:\source\masa\litematica@sakura-ryoko`（0.29.2
>   源码）、Gradle 缓存中的 0.28.9/0.29.3/0.30.1/0.30.2 jar、`javap -p -c` 反汇编、
>   `C:\Users\11607\.gradle\caches\fabric-loom\{26.1,26.2,26.3}\minecraft-client.jar` 的 MC 字节码。
> - 方式：只读静态分析，未启动 Minecraft。
> - 结论置信度：GUI 类结构/签名/三版本同构性为**高**（方法级反汇编核对）；运行时像素效果需实现后由 `scripts/verify-gui.ps1`
>   验证。

---

## 0. 结论速览（TL;DR）

| #  | 问题                           | 结论                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                     |
|----|--------------------------------|----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| 1  | `GuiBase.init` 注入点          | `public void init()` 先 `super.init()`（Screen 空实现），再虚调用 `this.initGui()`，然后记录 `openTime`。在 `init` 的 **TAIL** 注入，可拿到子类 `initGui` 已添加完的全部按钮。**注意：Tab 切换时 masa mod 是直接调 `initGui()` 而不是 `init()`**，所以还需要在 `extractRenderState` HEAD 做一次渲染前兜底。                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                              |
| 2  | `GuiBase.buttons/widgets` 私有 | 用 `@Accessor("buttons")` / `@Accessor("widgets")`（Mixin 接口挂在 `GuiBase` 上，子类实例可强转）。`ButtonBase` 的 `displayString/enabled/visible/hovered/actionListener` 全是 `protected`，用 `ButtonBaseAccessor` 的 `@Accessor` 即可，无需 `@Shadow`。                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                |
| 3  | Tab 按钮识别                   | litematica / tweakeroo / malilib 自身三个样本全部同构：`initGui()` 里 `int x=10,y=26;` 循环 `ButtonGeneric(x, y, -1, 20, tabName)`，当前 Tab `setEnabled(false)`，`x += width+2`，`addButton(..., listener)`。可靠启发式：**“`super.initGui()` 之后新增的按钮” + `y` 在顶部条带 + 高度 20**，而不是只看坐标。                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                            |
| 4  | 列表重定位                     | `GuiListBase.listX/listY` 是 private 非 final（可 accessor set）；`WidgetListBase.posX/posY` 是 `protected final`（构造后不可改）。`WidgetListConfigOptions` 构造器用入参 `x/y/width` 一次性算死搜索框坐标与宽度，`setSize()` **不会**更新搜索框。结论：必须先改 `listX/listY` 再 `reCreateListWidget()`，且必须让 `getBrowserWidth()/getBrowserHeight()` 在托管屏幕里返回新布局尺寸，否则搜索框宽度还是旧值。                                                                                                                                                                                                                                                                                                                                                                                                                                           |
| 5  | 布局相关                       | litematica / tweakeroo 的 `GuiConfigs` 都不重写 `getBrowserWidth/getBrowserHeight`；但 malilib 自带子类 `GuiModConfigs` 重写了 `getBrowserHeight()` 且**没有 Tab**，必须排除托管。`setSize()` 只更新尺寸与条目起点；`resize` 会走 `Screen.resize → repositionElements → rebuildWidgets → init()` 重建，但列表 widget 实例与 `posX/posY` 保留，需要检测位置不匹配后重建。                                                                                                                                                                                                                                                                                                                                                                                                                                                                                 |
| 6  | mod 切换下拉                   | `GuiConfigsBase.modSwitchWidget`（`WidgetDropDownList<ModInfo>`，protected，可 accessor）在 `buildConfigSwitcher()` 里创建于 `(GuiUtils.getScaledWindowWidth()-155, 6, 130, 18, maxHeight=200, maxVisible=10)`。可 `setPosition()`（其重写会把搜索框挪到 `y-18`），也可只保留逻辑、取消其 `render` 自绘外框（开着的下拉列表在 `postRenderHovered` 里仍会画）。                                                                                                                                                                                                                                                                                                                                                                                                                                                                                           |
| 7  | 行渲染                         | `WidgetListBase.reCreateListEntryWidgets()`：行 `x=listPosX+2`、`y=listPosY+4+browserEntriesOffsetY`、宽 `browserWidth-14`、高 22。`WidgetConfigOption` 内部：名称 `WidgetLabel` 画在 `(x, y+7)`，控件列 `x += labelWidth+10`，重置按钮 `x+configWidth+2`（宽 `-1` 自适应）。在 `WidgetConfigOption.render` **HEAD** 注入画行背景/hover 最安全（注意其源码先 `drawSubWidgets`，CONFIG 行再 `super.render` 会二次画 label，不影响注入）。                                                                                                                                                                                                                                                                                                                                                                                                                 |
| 8  | 26.3 渲染                      | `GuiGraphicsExtractor`（= 旧 `GuiGraphics` 的改名，见 `versions/mapping-1.21.11-26.1.txt`）只把 `GuiElementRenderState` 记录进 `GuiRenderState`，由 `GuiRenderer` 稍后统一绘制；`GuiContext` 是它的子类包装。可直接用继承来的 `ctx.fill / fillGradient / text / blit / blitSprite / outline / horizontalLine / verticalLine / enableScissor`，也可用 malilib `RenderUtils` 的 `GuiContext` 重载。**不要用**不带 `GuiContext` 的旧 immediate-mode 重载（`drawRect(int…)` / `drawGradientRect(float…)`）。自定义纹理用 `Identifier.fromNamespaceAndPath("malilib-ui","textures/gui/panel.png")`；GUI sprite 放 `assets/malilib-ui/textures/gui/sprites/...` 后 `ctx.blitSprite(...)`（GUI atlas 自动收录所有命名空间，`assets/minecraft/atlases/gui.json` 只写 `directory: gui/sprites`）。                                                                |
| 9  | 三版本差异                     | **GUI 类是同一套 API**：`GuiBase`、`GuiListBase`、`GuiConfigsBase`、`WidgetListConfigOptions(Base)`、`WidgetConfigOption(Base)`、`WidgetListBase`、`ButtonBase/ButtonGeneric`、`WidgetSearchBar(Configs)`、`WidgetDropDownList`、`GuiScrollBar`、`WidgetBase/WidgetContainer` 在 0.28.9 / 0.29.3 / 0.30.1 的字段与方法签名集合**完全相同**；核对过的目标方法字节码除常量池编号外一致。唯一新增：0.30.1 的 `GuiBase.onFilesDrop/onMouseDropFiles`、`WidgetConfigOption.addConfigComment(int,int,int,int,MutableComponent)`。**一套 mixin 可通用，不需要版本条件分支**。真正的版本差异在 MC 26.2→26.3 的 `com.mojang.blaze3d.pipeline.RenderPipeline` → `com.mojang.renderpearl.api.pipeline.RenderPipeline`、`com.mojang.blaze3d.textures.GpuTextureView/GpuSampler` → `com.mojang.renderpearl.api.textures.*` 包移动：**共享源码里不要出现这些类型名**。 |
| 10 | 入口链路                       | ModMenu `modmenu` entrypoint → `new GuiConfigs().setParent(screen)` → `Minecraft` 内部 `Gui.setScreen` → `Screen.init(int,int)`（final；首次 `init()`，之后 `repositionElements()`）→ `GuiBase.init` → `initGui`。`GuiBase.openGui(Screen)` = `Minecraft.getInstance().gui.setScreen(gui)`。**所有入口（ModMenu、热键、mod 内部 `openGui`）都必经 `GuiBase.init`。**                                                                                                                                                                                                                                                                                                                                                                                                                                                                                     |
| 11 | 搜索框/焦点                    | 搜索框坐标是相对列表的绝对屏幕坐标（构造时 `x+2,y+4`），所以“重建列表并给对 x/y/width”后自动跟随。键盘/焦点路由不依赖坐标：`GuiListBase.onKeyTyped/onCharTyped` → `WidgetListBase` → `widgetSearchBar`，重定位不影响。唯一坑：仅 `setSize()` 后搜索框宽度/位置不变。                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                     |
| 12 | 风险                           | 见 §12。最高风险：① 只挂 `init` TAIL 会漏掉 Tab 切换（用 render HEAD 兜底）；② 只 `setSize` 不重建列表 → 搜索框越界；③ 无 Tab 的 `GuiModConfigs` 被误托管；④ 26.3 源码里引用 `RenderPipeline` 等移动类型导致 26.1/26.2 编译失败；⑤ `malilib-ui.mixins.json` 列了不存在的 Mixin 类会导致启动崩溃。                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                        |

---

## 1. 版本、依赖与构建模型

### 1.1 目标版本矩阵

| MC   | 版本目录        | malilib 依赖 | malilib jar 实际 `fabric.mod.json` 约束 | litematica | tweakeroo            |
|------|-----------------|--------------|-----------------------------------------|------------|----------------------|
| 26.1 | `versions/26.1` | `0.28.9`     | `minecraft ~26.1`                       | 0.27.10    | 0.28.6               |
| 26.2 | `versions/26.2` | `0.29.3`     | `minecraft ~26.2-`                      | 0.28.4     | 0.29.2               |
| 26.3 | `versions/26.3` | `0.30.1`     | `minecraft >=26.3 <26.4`                | 0.29.0     | 0.30.1（`MtPXEGTi`） |

jar 缓存位置（本机实测）：

```
C:\Users\11607\.gradle\caches\modules-2\files-2.1\maven.modrinth\malilib\0.28.9\7bb6ea5636847eafa3007ebb5a6464c9d367cbf4\malilib-0.28.9.jar
C:\Users\11607\.gradle\caches\modules-2\files-2.1\maven.modrinth\malilib\0.29.3\c5a86e2c868d8918265361694eebbb73eee6c861\malilib-0.29.3.jar
C:\Users\11607\.gradle\caches\modules-2\files-2.1\maven.modrinth\malilib\0.30.1\a43a41725bf86e2aafd600c3088d68c2231ff195\malilib-0.30.1.jar
C:\Users\11607\.gradle\caches\modules-2\files-2.1\maven.modrinth\malilib\0.30.2\832a00c0d6417d7f195fbe1b1c643bbd4893e64a\malilib-0.30.2.jar
```

重要事实：**0.30.1 与 0.30.2 的目标 GUI 类逐字节相同**（SHA-256 相同）；因此本报告引用的 0.30.2 源码行号对 0.30.1 直接成立。

### 1.2 发布 jar 的映射名

缓存里的 malilib jar 是**已 remap 到运行时名**的发布 jar：MC 类名直接是 `net.minecraft.client.gui.GuiGraphicsExtractor`、
`net.minecraft.resources.Identifier` 这类名字（不是 intermediary `class_xxx`）。本项目 Loom 1.19-SNAPSHOT + MC 26.x
也是同一套名字。所以：

- `javap -p -classpath <malilib jar>` 得到的签名 = 运行时真实签名 = 本项目的编译期签名。
- 26.x 的 MC 运行时类名本身是“Mojang 名”，且 `GuiGraphics` 在 26.1 被改名为 `GuiGraphicsExtractor`（
  `versions/mapping-1.21.11-26.1.txt` 有
  `net.minecraft.client.gui.GuiGraphics net.minecraft.client.gui.GuiGraphicsExtractor` 条目）。

### 1.3 预处理器（preprocessor）模型对本项目的约束

- `build.gradle.kts` 里 `mainProject` = **26.2**（`versions/mainProject` 内容为 `26.2`）；节点链：`26.1 → 26.2 → 26.3`。
- `versions/mapping-26.1.2-26.2.txt` 只映射了一些 malilib 工具类（`LayerRange`、`IntBoundingBox` 等），**没有**任何 GUI/MC
  渲染类条目。
- `mc260200.link(mc260300, null)`：**26.2 → 26.3 没有任何映射文件**。
- 结论：`src/main/java` 是“按 26.2 写、复制到 26.1/26.3 编译”的共享源码。26.1 方向靠 mapping 文件改名；26.3 方向**原样复制**
  。因此：
    - 可以自由使用 26.1/26.2/26.3 三方**同名同签名**的 API；
    - 不能引用只在 26.3 存在的类/方法，也不能引用 26.3 里**挪了包**的类（见 §10.2）。
    - 需要版本差异时可以：(a) 避免用到差异 API（推荐）；(b) 在 `versions/<ver>/src/main/java/...` 放覆盖文件（preprocessor 的
      overwrite 机制）；(c) 加 `//#if` 注释指令（`com.replaymod.gradle.preprocess.CommentPreprocessor`，keywords 含
      `if/ifdef/elseif/else/endif/eval`）。

---

## 2. 类体系与对象模型

### 2.1 继承关系

```
net.minecraft.client.gui.screens.Screen
└── GuiBase (abstract, fi.dy.masa.malilib.gui.GuiBase)          ← 屏幕 + 自己的按钮/Widget/文本框列表
    ├── GuiListBase<TYPE, WIDGET, WIDGETLIST> (abstract)
    │   └── GuiConfigsBase (abstract)                            ← 配置屏基类；含 mod 切换下拉
    │       ├── MaLiLibConfigGui                                 ← malilib 自己的配置屏
    │       ├── GuiModConfigs                                    ← 通用“无 Tab”配置屏（子类可 new）
    │       └── (litematica) GuiConfigs / (tweakeroo) GuiConfigs ← masa mod 的配置屏（本 mod 的目标）
    └── ... GuiRenderLayerEditBase、GuiTextInputBase 等其它屏幕
```

Widget 体系（注意 `WidgetListBase` 自身也继承 `GuiBase`，即“列表其实是一个嵌套 Screen 对象”）：

```
WidgetBase
├── ButtonBase (abstract) → ButtonGeneric / ConfigButton*
├── WidgetContainer → WidgetListEntryBase<TYPE> → WidgetConfigOptionBase<TYPE> → WidgetConfigOption
│                   → WidgetListBase<TYPE, WIDGET>  ← 注意：WidgetListBase 实际直接继承 GuiBase
│                                                  └── WidgetListConfigOptionsBase
│                                                      └── WidgetListConfigOptions
├── WidgetSearchBar → WidgetSearchBarConfigs
├── WidgetDropDownList<T>
├── WidgetLabel / WidgetIcon / WidgetHoverInfo / WidgetHoverComponent / WidgetCheckBox ...
└── ...
```

源码证据：

- `gui/GuiBase.java:43` `public abstract class GuiBase extends Screen implements IMessageConsumer, IStringConsumer`
- `gui/GuiListBase.java:15` `extends GuiBase`
- `gui/GuiConfigsBase.java:35` `extends GuiListBase<ConfigOptionWrapper, WidgetConfigOption, WidgetListConfigOptions>`
- `gui/widgets/WidgetListBase.java:17`
  `public abstract class WidgetListBase<TYPE, WIDGET extends WidgetListEntryBase<TYPE>> extends GuiBase`
- `gui/widgets/WidgetListConfigOptions.java:17`
  `extends WidgetListConfigOptionsBase<ConfigOptionWrapper, WidgetConfigOption>`
- `gui/widgets/WidgetConfigOptionBase.java:19` `extends WidgetListEntryBase<TYPE>`；
  `gui/widgets/WidgetListEntryBase.java:5` `extends WidgetContainer`

`WidgetListBase extends GuiBase (extends Screen)` 的后果：

1. 它自带 `width/height/initGui()/resize()` 等 Screen 语义；`GuiListBase.initGui()` 里要手动 `widget.setSize(浏览器宽高)`
   ，因为它的 Screen `width/height` 不参与列表布局（列表用 `browserWidth/browserHeight`）。
2. 窗口 resize 时，`GuiListBase.resize()` 会对该嵌套对象再走一遍 `Screen.resize → rebuildWidgets → init()`，所以列表自身的
   `initGui()`（刷新条目）会跑；但**它不会改 `posX/posY`（final）**。
3. 我们自己的 mixin 如果注入 `GuiBase.init` 的 TAIL，会**同时**被这个嵌套对象触发；必须用 `instanceof GuiConfigsBase`
   之类的门控（嵌套列表对象 `instanceof GuiConfigsBase` 为 false，安全）。

### 2.2 关键字段可见性总表（0.30.1/0.30.2，三版本一致）

`GuiBase`（`gui/GuiBase.java:80-96`）：

| 字段                                    | 可见性              | 类型                                                               | 说明                              |
|-----------------------------------------|---------------------|--------------------------------------------------------------------|-----------------------------------|
| `mc`                                    | `public final`      | `Minecraft`                                                        |                                   |
| `font` / `fontHeight`                   | `public final`      | `Font` / `int`                                                     |                                   |
| `buttons`                               | **`private final`** | `List<ButtonBase>`                                                 | 屏幕级按钮；Tab 按钮就在这里      |
| `widgets`                               | **`private final`** | `List<WidgetBase>`                                                 | 屏幕级 widget；mod 切换下拉在这里 |
| `textFields`                            | **`private final`** | `List<TextFieldWrapper<? extends GuiTextFieldGeneric>>`            | 屏幕级文本框                      |
| `textFieldsMultiLine`                   | **`private final`** | `List<TextFieldMultiLineWrapper<? extends GuiTextFieldMultiLine>>` |                                   |
| `messageRenderer`                       | **`private final`** | `MessageRenderer`                                                  |                                   |
| `openTime`                              | `private`           | `long`                                                             | `init` 里赋值                     |
| `hoveredWidget`                         | `protected`         | `WidgetBase`                                                       |                                   |
| `title`                                 | `protected`         | `String`                                                           |                                   |
| `useTitleHierarchy`                     | `protected`         | `boolean`                                                          |                                   |
| `keyInputCount`                         | `private`           | `int`                                                              | 防“开屏按键漏进搜索框”            |
| `mouseWheelHorizontal/VerticalDeltaSum` | `private`           | `double`                                                           | 滚轮取整                          |
| `parent`                                | **`private`**       | `Screen`（@Nullable）                                              |                                   |

`GuiListBase`（`gui/GuiListBase.java:17-19`）：

| 字段              | 可见性                    | 类型         | 说明                                                      |
|-------------------|---------------------------|--------------|-----------------------------------------------------------|
| `listX` / `listY` | **`private`（非 final）** | `int`        | 列表构造坐标；**可 accessor 写**                          |
| `widget`          | **`private`（非 final）** | `WIDGETLIST` | 列表实例；可 accessor 读写，`reCreateListWidget()` 会替换 |

`GuiConfigsBase`（`gui/GuiConfigsBase.java:37-44`）：

| 字段                    | 可见性                   | 类型                             | 说明                                         |
|-------------------------|--------------------------|----------------------------------|----------------------------------------------|
| `modSwitchWidget`       | `protected`（@Nullable） | `WidgetDropDownList<ModInfo>`    | mod 切换下拉                                 |
| `hotkeyChangeListeners` | `protected final`        | `List<Runnable>`                 |                                              |
| `dirtyListener`         | `protected final`        | `ButtonPressDirtyListenerSimple` |                                              |
| `modId`                 | `protected final`        | `String`                         | 构造函数传入（如 `"litematica"`）            |
| `activeKeybindButton`   | `protected`              | `ConfigButtonKeybind`            |                                              |
| `configWidth`           | `protected`              | `int`（默认 204）                | 控件列宽；子类按 Tab 重写 `getConfigWidth()` |
| `hoverInfoProvider`     | `protected` @Nullable    | `IConfigInfoProvider`            |                                              |
| `dialogHandler`         | `protected` @Nullable    | `IDialogHandler`                 |                                              |

`WidgetListBase`（`gui/widgets/WidgetListBase.java:19-46`，全部 protected）：

| 字段                                                                                                                           | 类型                                   | 说明                             |
|--------------------------------------------------------------------------------------------------------------------------------|----------------------------------------|----------------------------------|
| `listContents` / `listWidgets`                                                                                                 | `List<TYPE>` / `List<WIDGET>`（final） | 当前条目数据 / 当前可见行 widget |
| `scrollBar`                                                                                                                    | `GuiScrollBar`（final）                |                                  |
| `selectedEntries`                                                                                                              | `Set<TYPE>`（final）                   |                                  |
| `posX` / `posY`                                                                                                                | **`protected final int`**              | 列表定位；构造后不可改           |
| `totalWidth/totalHeight/browserWidth/browserHeight`                                                                            | `int`                                  | `setSize()` 更新                 |
| `entryHeight/browserEntriesStartX/Y/browserEntriesOffsetY/browserEntryWidth/Height/browserPaddingX/Y/maxVisibleBrowserEntries` | `int`                                  |                                  |
| `lastSelectedEntryIndex/lastScrollbarPosition`                                                                                 | `int`                                  |                                  |
| `allowKeyboardNavigation/allowMultiSelection/shouldSortList`                                                                   | `boolean`                              |                                  |
| `widgetSearchBar`                                                                                                              | `protected @Nullable WidgetSearchBar`  | 搜索框                           |
| `lastSelectedEntry/selectionListener`                                                                                          | private                                |                                  |

`WidgetListConfigOptions`（`gui/widgets/WidgetListConfigOptions.java:19-20`）：
`protected final GuiConfigsBase parent; protected final WidgetSearchBarConfigs widgetSearchConfigs;`

`WidgetSearchBar`（`gui/widgets/WidgetSearchBar.java:15-18`）：
`protected final WidgetIcon iconSearch; protected final LeftRight iconAlignment; protected final GuiTextFieldGeneric searchBox; protected boolean searchOpen;`
（无 `setPosition` 重写！）

`WidgetDropDownList`（`gui/widgets/WidgetDropDownList.java:36-47`）：
`scrollBar/entries/filteredEntries/searchBar/maxHeight/maxVisibleEntries/totalHeight` 为 protected final；
`isOpen/selectedIndex/scrollbarWidth` protected；`selectedEntry/stringRetriever` protected @Nullable。

`ButtonBase`（`gui/button/ButtonBase.java:29-36`）：`hoverStrings`（final）、`hoverHelp`（final）、`displayString`、`enabled`、
`visible`、`hovered`、`hoverInfoRequiresShift`、`actionListener` —— **全部 protected**，没有 `isEnabled()` getter（只有
`setEnabled`），所以取当前 Tab 状态需要 accessor。

`GuiScrollBar`（`gui/GuiScrollBar.java:12-22`）：`barTexture`（final @Nullable）、`mc`、
`mouseOver/dragging/renderScrollbarBackground`、
`currentValue/maxValue/backgroundColor/foregroundColor/dragStartValue/dragStartY`（全部 protected）。

### 2.3 三版本 GUI 类字节码对比（javap 证据）

方法：分别 `javap -p` / `javap -p -c` 四个 jar 的目标类并 diff。

**签名集合 diff（0.28.9 vs 0.30.1，忽略 `Compiled from`）：**

| 类                                                                                  | 签名差异                                                                                                                                                         |
|-------------------------------------------------------------------------------------|------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `GuiBase`                                                                           | 仅 0.30.1 新增 `public void onFilesDrop(List<Path>)`、`public boolean onMouseDropFiles(List<Path>)`                                                              |
| `GuiConfigsBase`                                                                    | 仅 0.28.9 有编译器合成方法 `access$000/access$100`（匿名下拉类访问 outer）                                                                                       |
| `GuiListBase`                                                                       | 无                                                                                                                                                               |
| `WidgetListBase`                                                                    | 无                                                                                                                                                               |
| `WidgetListConfigOptions`                                                           | 无                                                                                                                                                               |
| `WidgetListConfigOptionsBase`                                                       | 无                                                                                                                                                               |
| `WidgetConfigOption`                                                                | 仅 0.30.1 新增 `protected void addConfigComment(int,int,int,int,MutableComponent)`                                                                               |
| `WidgetConfigOptionBase`                                                            | 无                                                                                                                                                               |
| `WidgetDropDownList` / `WidgetSearchBar` / `WidgetSearchBarConfigs` / `WidgetLabel` | 无                                                                                                                                                               |
| `ButtonBase` / `ButtonGeneric` / `GuiScrollBar`                                     | 无                                                                                                                                                               |
| `GuiContext`                                                                        | 见 §9/§10：`bindTexture/setupTexture` 返回类型包名变化、`popScissor()` 返回类型变化、0.30.1 新增 `renderTooltip(...,ClientTooltipPositioner,Identifier,boolean)` |
| `RenderUtils`                                                                       | 见 §10.4：部分方法增删/签名变化（GUI 面板用的那批全部一致）                                                                                                      |

**目标方法字节码 diff（0.28.9 vs 0.30.1，去掉常量池编号后比较）：**

| 类::方法                                                 | 结果                                                                                                                                                                                                                                                           |
|----------------------------------------------------------|----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `GuiBase::init()V`                                       | 结构一致（`super.init()` → `this.initGui()` → `openTime = System.nanoTime()`）                                                                                                                                                                                 |
| `GuiBase::initGui()V`                                    | 一致（`this.clearElements()`）                                                                                                                                                                                                                                 |
| `GuiBase::extractRenderState`                            | 一致（`GuiContext.fromGuiGraphics` → `nextStratum` → `drawScreenBackground` → `drawTitle` → `drawWidgets` → `drawButtons` → `drawContents` → `drawTextFields` → `drawTextFieldsMultiLine` → `drawHoveredWidget` → `drawButtonHoverTexts` → `drawGuiMessages`） |
| `GuiBase::drawButtons`                                   | 一致（`for (ButtonBase b : buttons) b.render(ctx, ...)`）                                                                                                                                                                                                      |
| `GuiBase::addButton`                                     | 一致（`button.setActionListener(l); buttons.add(button); return button;`）                                                                                                                                                                                     |
| `GuiConfigsBase::initGui`                                | 一致（`GuiListBase.initGui()` → `buildConfigSwitcher()`）                                                                                                                                                                                                      |
| `GuiConfigsBase::buildConfigSwitcher`                    | 一致（含 `GuiUtils.getScaledWindowWidth()-155,6,130,18,200,10` 常量）                                                                                                                                                                                          |
| `GuiConfigsBase::createListWidget`                       | 一致（`new WidgetListConfigOptions(listX, listY, getBrowserWidth(), getBrowserHeight(), getConfigWidth(), 0f, useKeybindSearch(), this)`）                                                                                                                     |
| `GuiListBase::initGui` / `reCreateListWidget` / `resize` | 一致                                                                                                                                                                                                                                                           |
| `WidgetListConfigOptions::<init>`                        | 一致                                                                                                                                                                                                                                                           |
| `WidgetConfigOption::render`                             | 一致                                                                                                                                                                                                                                                           |
| `ButtonGeneric::render`                                  | 一致                                                                                                                                                                                                                                                           |

**类文件 SHA-256（前 12 hex）对照（说明 0.30.1 == 0.30.2；跨大版本 hash 变化来自常量池/内部引用重排，不代表方法体变化）：**

```
class                                                          0.28.9        0.29.3        0.30.1        0.30.2
GuiBase.class                                                  B9BFD441FD74  BB0CA55F77EA  FF2A58EBFA1E  FF2A58EBFA1E
GuiConfigsBase.class                                           E61DF9C34BC8  B968C2679AD7  832EBE337E71  832EBE337E71
GuiListBase.class                                              C5709F0D422A  C5709F0D422A  7ABE90F53067  7ABE90F53067
WidgetListBase.class                                           9E87FAEB9F7B  9E87FAEB9F7B  FDBD1EFAAACF  FDBD1EFAAACF
WidgetListConfigOptions.class                                  5C267D4C8832  5C267D4C8832  5C267D4C8832  5C267D4C8832
WidgetConfigOption.class                                       68D78BBE12D4  68D78BBE12D4  4D15E0B160E0  4D15E0B160E0
WidgetDropDownList.class                                       E94FA83A9964  E94FA83A9964  E94FA83A9964  E94FA83A9964
WidgetLabel.class                                              3626E83198B0  3626E83198B0  3626E83198B0  3626E83198B0
ButtonBase.class                                               3751F596E9DC  3751F596E9DC  2DE147D4D6C0  2DE147D4D6C0
ButtonGeneric.class                                            4415ECE409A3  4415ECE409A3  93C7E46CA5DD  93C7E46CA5DD
GuiScrollBar.class                                             F3405F4C461F  F3405F4C461F  F3405F4C461F  F3405F4C461F
```

→ **单一 mixin 集合可同时服务 26.1/26.2/26.3 的 malilib（0.28.9/0.29.3/0.30.1）。** 需要避免的只有：注入 0.30.1
才有的方法（见上表）、注入编译器合成 `access$xxx`、以及在共享源码里引用挪包的 MC 类型。

---

## 3. 生命周期与调用时序（调查点 1 / 10）

### 3.1 打开屏幕

```
ModMenu 配置按钮 / 热键 / mod 内部代码
  → new GuiConfigs()                     (litematica) 构造：super(10, 50, modId, null, titleKey, args)
  → gui.setParent(modMenuScreen)         (GuiBase.setParent，会拒绝 parent.getClass()==this.getClass())
  → Minecraft → Gui.setScreen(gui)       (26.3: Gui.setScreen 清理旧 screen: clearFocus/stopTextInput/removed)
  → Screen.init(int scaledW, int scaledH)   ★ final，所有入口必经
       - 设置 Screen.width/height
       - if (!initialized) { this.init(); this.setInitialFocus(); } else this.repositionElements();
       - initialized = true
  → GuiBase.init()  [GuiBase.java:154-161]
       super.init();                 // Screen.init() 空实现
       this.initGui();               // ★ 虚调用，进入子类
       this.openTime = System.nanoTime();
  → GuiConfigsBase.initGui() [GuiConfigsBase.java:54-59]
       super.initGui() → GuiListBase.initGui() [GuiListBase.java:70-80]
            super.initGui() → GuiBase.initGui() [GuiBase.java:163-166]   // clearElements(): 清空 buttons/widgets/文本框
            getListWidget()          // 若 widget == null 则 reCreateListWidget() → createListWidget(listX, listY)
            getListWidget().setSize(getBrowserWidth(), getBrowserHeight());
            getListWidget().initGui()   // WidgetListBase.initGui → refreshEntries → 创建可见行
       buildConfigSwitcher()          // 创建 modSwitchWidget（addWidget）
  → (子类) GuiConfigs.initGui() [litematica GuiConfigs.java:30-57]
       clearOptions();
       if (当前 Tab == RENDER_LAYERS) { GuiBase.openGui(new GuiRenderLayer()); return; }  // ← 提前返回，无 Tab 按钮
       int x = 10; int y = 26;
       for (tab : ConfigGuiTab.values()) { x += createButton(x, y, -1, tab); }  // addButton(ButtonGeneric(...,20,...))
  → 回到 GuiBase.init(): 记录 openTime
  → [我们的注入] GuiBase.init TAIL：此时 buttons 已包含全部 Tab 按钮
```

- `Screen.init(int,int)` 26.1/26.2/26.3 字节码一致：设置宽高 → 首次 `init()`（否则 `repositionElements()`）→
  `initialized=true`。
- `GuiBase.openGui(Screen)`（`GuiBase.java:916-919`）= `Minecraft.getInstance().gui.setScreen(gui)`，与 mod 直接
  `mc.gui.setScreen(...)` 完全等价。
- malilib 侧 ModMenu 兼容类：`fi.dy.masa.malilib.compat.modmenu.ModMenuImpl`（源码 `compat/modmenu/ModMenuImpl.java`）返回
  `new MaLiLibConfigGui().setParent(screen)`。litematica / tweakeroo jar 里各自的 `compat.modmenu.ModMenuImpl` 同理返回
  `new GuiConfigs().setParent(screen)`（javap 反汇编确认 lambda 内 `new GuiConfigs` + `setParent`）。
- **结论：所有入口都经过 `Screen.init(int,int)` → `GuiBase.init()`。** 只有“同一 screen 实例被重新初始化”时走
  `repositionElements()`（resize 场景）。

### 3.2 渲染抽取

```
Screen.extractRenderStateWithTooltipAndSubtitles(drawContext, mouseX, mouseY, partialTicks)  // final
  drawContext.nextStratum();
  this.extractBackground(...)        // GuiBase 重写为空（不 blur/mask）
  drawContext.nextStratum();
  this.extractRenderState(...)       // ★ GuiBase.extractRenderState [GuiBase.java:198-217]
      GuiContext ctx = GuiContext.fromGuiGraphics(drawContext);
      ctx.nextStratum();
      drawScreenBackground(ctx, mouseX, mouseY);   // 默认全屏 TOOLTIP_BACKGROUND 0xB0000000
      drawTitle(ctx, mouseX, mouseY, partialTicks);
      drawWidgets(ctx, mouseX, mouseY);            // mod 切换下拉在这里
      drawButtons(ctx, mouseX, mouseY, partialTicks);  // Tab 按钮在这里
      drawContents(ctx, mouseX, mouseY, partialTicks); // GuiListBase 重写：list.drawContents（行/滚动条/搜索框）
      drawTextFields / drawTextFieldsMultiLine
      drawHoveredWidget(ctx, ...);                 // 列表 hover 高亮/工具提示
      drawButtonHoverTexts(ctx, ...);
      drawGuiMessages(ctx);
  drawContext.extractDeferredElements(mouseX, mouseY, partialTicks);  // 工具提示等
```

绘制顺序即批次顺序：背景 → 标题 → 屏幕 widgets（下拉）→ 屏幕按钮 → 列表内容 → 屏幕文本框 → hover 层 → 工具提示。**我们的面板背景要在
`drawScreenBackground` 阶段画**（它上面的一切都在之后画）；左侧导航项可在 `drawButtons` 阶段自绘（与列表不重叠）。

### 3.3 切换 Tab（关键！）

litematica / tweakeroo / MaLiLibConfigGui 的 Tab 监听器（源码 `litematica GuiConfigs.java:181-199`，tweakeroo jar
反汇编一致）行为：

```java
public void actionPerformedWithButton(ButtonBase button, int mouseButton) {
    DataManager.setConfigGuiTab(this.tab);           // 或静态字段赋值
    this.parent.reCreateListWidget();                // ★ 用新的 configWidth 重建列表
    Objects.requireNonNull(this.parent.getListWidget()).resetScrollbarPosition();
    this.parent.initGui();                           // ★ 直接调 initGui，不是 init！
}
```

**因此：**

1. Tab 切换会重建列表 widget，但**屏幕实例不变**，`GuiBase.init()` 不会跑 → 只挂 `init` TAIL 的逻辑会漏掉这次变化。
2. `reCreateListWidget()` 发生在 `initGui()` 之前，会重新走 `GuiConfigsBase.createListWidget(this.listX, this.listY)` →
   此时 `GuiListBase.listX/listY` 仍是我们上次写进去的新布局坐标（字段在实例上），所以列表位置能保持。**必须确保此时
   `getBrowserWidth()/getBrowserHeight()` 也返回新布局尺寸**，否则新列表/搜索框宽度又会回到旧值。
3. 之后 `initGui()` → `clearElements()` 清空旧 Tab 按钮 → 子类重新添加新 Tab 按钮（禁用态切到新 Tab）→ `GuiBase.init` TAIL
   不执行 → 需要 `extractRenderState` HEAD 兜底重新识别/摆位。

### 3.4 窗口尺寸变化

```
Minecraft.resizeGui() → 当前 screen.resize(w, h)
  → GuiListBase.resize() [GuiListBase.java:161-170]
      super.resize() → GuiBase.resize() [GuiBase.java:143-152]
          if (parent != null) parent.resize(w,h);
          super.resize(w,h) → Screen.resize:
               Screen.width/height = w/h
               repositionElements() → rebuildWidgets():
                   clearWidgets(); clearFocus(); this.init(); setInitialFocus();
                   → GuiBase.init() → initGui()（完整重建一次，Tab 按钮重新创建）
      getListWidget().resize(w,h)     // 列表作为嵌套 Screen 再 rebuild 一次（刷新条目）
```

- resize 会**重建一次 UI**（init TAIL 会跑），但 `GuiListBase.widget` 实例保留，`WidgetListBase.posX/posY`（final）还是旧布局坐标。
- 所以必须在 `init` TAIL / `extractRenderState` HEAD 的“ensure”逻辑里检测 `posX/posY` 与当前布局不符 → 重设
  `listX/listY` + `reCreateListWidget()`。
- 不需要 Mixin `Minecraft`/`Window`；`GuiBase.init` TAIL 已经够用。**注意：`GuiBase.init` TAIL 也会被嵌套列表对象触发**
  ，必须门控。

### 3.5 关闭

`GuiBase.onKeyTyped`（`GuiBase.java:484-587`）处理 ESC：`closeGui(showParent)` → `mc.gui.setScreen(parent)` 或 `onClose()`
。屏幕被替换时 `Minecraft`/`Gui.setScreen` 会调旧 screen 的 `removed()`；`GuiConfigsBase.removed()`（
`GuiConfigsBase.java:180-190`）会把列表里未提交的配置修改 `applyPendingModifications()` 并 `onSettingsChanged()`。**我们的额外
`initGui`/`reCreateListWidget` 调用会让这个“apply”提前发生**，见 §12 风险 16。

---

## 4. Mixin 注入点与 Accessor 写法（调查点 2）

### 4.1 推荐注入点总表

| 目标类               | 目标方法（描述符）                                                        | 注入点                                       | 目的                                                             | 三版本稳定                   |
|----------------------|---------------------------------------------------------------------------|----------------------------------------------|------------------------------------------------------------------|------------------------------|
| `GuiBase`            | `init()V`                                                                 | TAIL                                         | 子类 `initGui` 完成后识别 Tab 按钮、迁移布局、记录状态           | ✅                           |
| `GuiBase`            | `extractRenderState(Lnet/minecraft/client/gui/GuiGraphicsExtractor;IIF)V` | HEAD                                         | 每帧渲染前 ensure（覆盖 Tab 切换直接调 `initGui()` 的场景）      | ✅                           |
| `GuiBase`            | `drawScreenBackground(Lfi/dy/masa/malilib/render/GuiContext;II)V`         | HEAD + `ci.cancel()`                         | 用新面板替换原版暗色背景                                         | ✅                           |
| `GuiBase`            | `drawTitle(Lfi/.../GuiContext;IIF)V`                                      | HEAD + `ci.cancel()`                         | 隐藏原版左上角标题（改画自己的 header）                          | ✅                           |
| `GuiBase`            | `drawButtons(Lfi/.../GuiContext;IIF)V`                                    | `@Redirect` 到 `ButtonBase.render(...)` 调用 | 导航项自绘；其它按钮保持原样                                     | ✅（drawButtons 字节码一致） |
| `GuiConfigsBase`     | `initGui()V`                                                              | TAIL                                         | （可选）记录“super.initGui 之后按钮下标的起点”，用于稳健识别 Tab | ✅                           |
| `GuiConfigsBase`     | `buildConfigSwitcher()V`                                                  | TAIL                                         | 取 `modSwitchWidget` 并挪位/改样式                               | ✅                           |
| `GuiConfigsBase`     | `getBrowserWidth()I` / `getBrowserHeight()I`                              | RETURN + `cir.setReturnValue(...)`           | ★ 让列表/搜索框在构造时就拿到新尺寸                             | ✅（子类不 override 时有效） |
| `GuiListBase`        | `initGui()V` / `resize(II)V`                                              | TAIL（可选）                                 | 列表重建后的兜底修正                                             | ✅                           |
| `GuiListBase`        | `drawContents(...)V`                                                      | HEAD                                         | 画列表面板底/可选 scissor                                        | ✅                           |
| `WidgetConfigOption` | `render(Lfi/.../GuiContext;IIZ)V`                                         | HEAD                                         | 行背景/斑马纹/hover                                              | ✅                           |
| `GuiScrollBar`       | `render(Lfi/.../GuiContext;IIFIIIII)V`                                    | HEAD                                         | 主题化滚动条颜色                                                 | ✅（类文件三版本逐字节相同） |
| `WidgetDropDownList` | `render(...)`                                                             | HEAD + `ci.cancel()`（可选）                 | 自绘“mod 选择”外框（开着的列表仍由 postRenderHovered 绘制）      | ✅                           |

> 原则：优先 HEAD/TAIL；`@Redirect`/`@At(INVOKE)` 目标要跟 malilib 更新一起复核。所有注入都加 `require` 默认 1（
> `malilib-ui.mixins.json` 已配 `defaultRequire: 1`），失败会直接报错而不是静默无效——这是好事，别关掉。

### 4.2 Accessor / Invoker 写法（可直接照抄）

```java
// 1) 屏幕级按钮 / widget 列表
@Mixin(GuiBase.class)
public interface GuiBaseAccessor {
    @Accessor("buttons")
    List<ButtonBase> malilibui$getButtons();

    @Accessor("widgets")
    List<WidgetBase> malilibui$getWidgets();
}
```

```java
// 2) ButtonBase：没有 isEnabled() getter，必须 accessor
@Mixin(ButtonBase.class)
public interface ButtonBaseAccessor {
    @Accessor("displayString")
    String malilibui$getDisplayString();

    @Accessor("enabled")
    boolean malilibui$isEnabled();

    @Accessor("actionListener")
    IButtonActionListener malilibui$getActionListener();
}
```

```java
// 3) 列表位置 + 重建（private 非 final 字段可写；protected 方法用 @Invoker）
@Mixin(GuiListBase.class)
public interface GuiListBaseAccessor {
    @Accessor("listX")
    void malilibui$setListX(int x);

    @Accessor("listY")
    void malilibui$setListY(int y);

    @Accessor("listX")
    int malilibui$getListX();

    @Accessor("listY")
    int malilibui$getListY();

    @Accessor("widget")
    WidgetListBase<?, ?> malilibui$getWidgetField();

    @Invoker("reCreateListWidget")
    void malilibui$reCreateListWidget();

    @Invoker("getListWidget")
    WidgetListBase<?, ?> malilibui$getListWidget();
}
```

```java
// 4) WidgetListBase：posX/posY 是 protected final，只能读
@Mixin(WidgetListBase.class)
public interface WidgetListBaseAccessor {
    @Accessor("posX")
    int malilibui$getListPosX();

    @Accessor("posY")
    int malilibui$getListPosY();

    @Accessor("browserWidth")
    int malilibui$getBrowserWidth();

    @Accessor("browserHeight")
    int malilibui$getBrowserHeight();
    // 可选：@Accessor("widgetSearchBar") WidgetSearchBar malilibui$getSearchBar();
}
```

```java
// 5) GuiConfigsBase：mod 切换下拉
@Mixin(GuiConfigsBase.class)
public interface GuiConfigsBaseAccessor {
    @Accessor("modSwitchWidget")
    WidgetDropDownList<ModInfo> malilibui$getModSwitchWidget();
}
```

```java
// 6) GuiScrollBar：滚动条颜色
@Mixin(GuiScrollBar.class)
public interface GuiScrollBarAccessor {
    @Accessor("backgroundColor")
    void malilibui$setBackgroundColor(int color);

    @Accessor("foregroundColor")
    void malilibui$setForegroundColor(int color);
}
```

要点：

- Mixin **接口**（`interface` + `@Accessor`）挂在 `GuiBase` 上后，**子类实例也可以强转**（
  `(GuiBaseAccessor) guiConfigsBaseInstance` 合法）。同理 `ButtonBaseAccessor` 对 `ButtonGeneric` 实例有效。
- `@Accessor` 能读写 `private`/`protected` 字段并自动放宽访问级别；`final` 字段只能生成 getter。
- 在 `@Mixin(GuiConfigsBase.class)` 里**不能** `@Shadow` `GuiBase` 的 private 字段（`buttons` 等）；要么用上面的 accessor
  接口，要么另写 `@Mixin(GuiBase.class)`。
- 在 `@Mixin(GuiConfigsBase.class)` 里**可以** `@Shadow` 它自己的 protected 方法：

```java
@Mixin(GuiConfigsBase.class)
public abstract class GuiConfigsBaseMixin {
    @Shadow
    protected abstract int getConfigWidth();

    @Shadow
    protected abstract boolean useKeybindSearch();

    @Shadow
    protected abstract WidgetListConfigOptions createListWidget(int listX, int listY);
}
```

- 取当前选中 Tab：masa 用 `setEnabled(currentTab != tab)`，所以 `active = !enabled`。这是三个样本一致的语义；没有 getter 必须
  accessor。

### 4.3 `GuiBase.buttons` 的“追加捕获”技巧（比坐标启发式更稳）

`GuiBase.addButton`（`GuiBase.java:663-668`）字节码三版本一致：

```java
public <T extends ButtonBase> T addButton(T button, IButtonActionListener listener) {
    button.setActionListener(listener);
    this.buttons.add(button);
    return button;
}
```

利用 `GuiConfigsBase.initGui` 是“先 `super.initGui()`、再子类加 Tab”的结构：

1. 在 `GuiConfigsBase.initGui` **TAIL** 注入：`ConfigScreenManager.markButtonBase(gui, accessor.getButtons().size())`
   （此时列表/下拉已建好，但子类 Tab 还没加）。
2. 在 `GuiBase.init` **TAIL** 注入：`ConfigScreenManager.captureTabs(gui)`，取 `buttons.subList(mark, buttons.size())` ——
   这些就是子类在 `initGui` 里新增的按钮（masa 家族 = Tab 按钮）。
3. 再叠加几何过滤（`y` 在顶部条带、`height <= 22`）剔除极少数 mod 额外加的按钮。
4. 注意：`GuiConfigs.initGui` 可能在 `super.initGui()` 后**提前 return**（litematica 的 RENDER_LAYERS 分支）→ 捕获到 0
   个按钮 → 不托管（正确）。
5. 该状态存在 `WeakHashMap<Screen, State>` 里；Tab 切换时旧按钮实例被 `clearElements()` 清掉，用“旧按钮是否仍在 buttons
   列表中”判断状态失效，重新捕获即可。

---

## 5. Tab 按钮识别与迁移（调查点 3）

### 5.1 三个样本的证据

**(a) litematica 0.29.2 源码**（
`D:\source\masa\litematica@sakura-ryoko\src\main\java\fi\dy\masa\litematica\gui\GuiConfigs.java`）：

```java
public class GuiConfigs extends GuiConfigsBase implements IConfigGuiAllTab {
    public GuiConfigs() {
        super(10, 50, Reference.MOD_ID, null, "litematica.gui.title.configs", ...);   // listX=10, listY=50
    }

    @Override
    public void initGui() {
        super.initGui();
        this.clearOptions();

        if (DataManager.getConfigGuiTab() == ConfigGuiTab.RENDER_LAYERS) {
            GuiBase.openGui(new GuiRenderLayer());
            return;                                    // ← 提前返回，不建 Tab 按钮
        }

        int x = 10;
        int y = 26;
        for (ConfigGuiTab tab : ConfigGuiTab.values()) {
            if (!this.useAllTab() && tab == ConfigGuiTab.ALL) continue;
            x += this.createButton(x, y, -1, tab);
        }
    }

    private int createButton(int x, int y, int width, ConfigGuiTab tab) {
        ButtonGeneric button = new ButtonGeneric(x, y, width, 20, tab.getDisplayName());  // width=-1 → 文本自适应
        button.setEnabled(DataManager.getConfigGuiTab() != tab);                          // 当前 Tab 禁用
        this.addButton(button, new ButtonListener(tab, this));
        return button.getWidth() + 2;
    }
}
```

jar（0.29.0 / 0.28.4 / 0.27.10）反汇编 `GuiConfigs.<init>` 均为 `bipush 10; bipush 50` →
`super(10, 50, modId, null, titleKey, args)`；`initGui` 均为 `bipush 10; istore_1` / `bipush 26; istore_2` + 循环
`createButton(x,y,-1,tab)`。Tab 枚举：`ALL, GENERIC, INFO_OVERLAYS, VISUALS, COLORS, HOTKEYS, RENDER_LAYERS`（0.29.x；旧版本可能少
TEST 等，动态捕获即可）。

**(b) tweakeroo 0.29.2 jar**（无源码，`javap -p -c fi.dy.masa.tweakeroo.gui.GuiConfigs`）：

```
initGui():
   invokespecial GuiConfigsBase.initGui
   invokevirtual clearOptions
   bipush 10 → istore_1        // x=10
   bipush 26 → istore_2        // y=26
   for (ConfigGuiTab tab : ConfigGuiTab.values()) {
       iload_1; iload_2; iconst_m1; aload tab          // createButton(x, y, -1, tab)
       invokevirtual createButton(IIILConfigGuiTab;)I
       iadd → istore_1                                  // x += 返回值
   }
createButton():
   new ButtonGeneric; dup; iload_1; iload_2; iload_3; bipush 20; tab.getDisplayName(); new String[0]
   invokespecial ButtonGeneric.<init>(IIIILjava/lang/String;[Ljava/lang/String;)V
   ...
   getstatic tab; aload 4; if_acmpeq → setEnabled(true/false)
   invokevirtual addButton(ButtonBase, IButtonActionListener)
   getWidth() + 2 → ireturn
ButtonListener.actionPerformedWithButton():
   putstatic GuiConfigs.tab
   parent.access$000()                    // = reCreateListWidget()
   parent.access$100(); if != null → parent.access$200().resetScrollbarPosition()
   parent.initGui()                       // ★ 直接 initGui
```

**(c) malilib 自身**（`MaLiLibConfigGui.java`，源码）：`super(10, 50, ...)`；`initGui` 里 `int x=10,y=26`，
`createButton(x,y,-1,tab)` 里 `new ButtonGeneric(x,y,width,20,tab.getDisplayName())` +
`setEnabled(MaLiLibConfigGui.tab != tab)` + `addButton`。（差异：它的 `createButton` 返回 `width`，循环里再 `+2`，不影响识别。）

### 5.2 识别启发式（推荐实现）

```java
// capture 阶段
int marker = buttons.size();                       // GuiConfigsBase.initGui TAIL 记录
// GuiBase.init TAIL
List<ButtonBase> added = buttons.subList(marker, buttons.size());
List<ButtonBase> tabs = added.stream()
        .filter(b -> b.getHeight() == 20 || b.getHeight() <= 22)
        .filter(b -> b.getY() >= 20 && b.getY() <= 46)   // 顶部条带
        .filter(b -> b.getWidth() > 0)
        .collect(toList());
// 校验：至少 1 个；added 中绝大多数是 tabs（避免把别的顶部按钮当 Tab）
if (tabs.isEmpty() || tabs.size() < added.size()) { /* 记录日志；added.size()==tabs.size() 才托管更稳 */ }
```

- 位置/尺寸经验值：`y == 26`、`height == 20`、宽 `-1` 自适应（`ButtonBase` 构造里 `width<0 → 文本宽+10`，见
  `ButtonBase.java:52-55`）、x 从 10 起、间距 2。
- 顺序：`buttons` 列表的顺序 = 子类 add 的顺序 = 枚举顺序 = 视觉从左到右，直接按列表顺序作为导航项顺序。
- **不要**只靠坐标：`GuiRenderLayer` 等非配置屏幕也有 `y=26` 的 Tab 按钮（`GuiRenderLayer.initGui` 同样 `x=10,y=26`
  ），所以必须同时限制 `screen instanceof GuiConfigsBase`。
- **不要**靠按钮文本（i18n 会变）。当前高亮判定：`!accessor.isEnabled()`；若找不到或找到多个，按“无选中”处理并打日志。

### 5.3 迁移/自绘方案

**方案 1（1 行 MVP，建议先落地）**：保留原按钮在 `GuiBase.buttons` 里，只改位置/尺寸：

```java
for(int i = 0; i <tabs.

size();

i++){
ButtonBase b = tabs.get(i);
    b.

setPosition(layout.navX, layout.navItemY(i));
        b.

setWidth(layout.navW);
    b.

setHeight(layout.navItemH);
}
```

- `WidgetBase.setPosition/setWidth/setHeight` 都是 public（`WidgetBase.java:46-83`）。
- 渲染走原版 `ButtonGeneric.render`（vanilla 按钮贴图）；点击走原版
  `GuiBase.onMouseClicked → ButtonBase.onMouseClickedImpl → actionListener`，**零逻辑改动**。
- 缺点：外观仍是“原版横向按钮竖着排”，当前 Tab 是 disabled 灰按钮，不够“现代”。

**方案 2（自绘，推荐最终形态）**：按钮仍留在 `buttons` 列表（保留点击/禁用语义），只把**渲染**换掉：

```java
@Redirect(method = "drawButtons",
        at = @At(value = "INVOKE",
                target = "Lfi/dy/masa/malilib/gui/button/ButtonBase;render(Lfi/dy/masa/malilib/render/GuiContext;IIZ)V"))
private void malilibui$renderButton(ButtonBase button, GuiContext ctx, int mouseX, int mouseY, boolean selected) {
    GuiBase screen = (GuiBase) (Object) this;
    if (ConfigScreenManager.isNavButton(screen, button)) {
        ConfigScreenManager.drawNavItem(screen, button, ctx, mouseX, mouseY);   // 自绘面板+文字+选中指示
        // 注意：原 render 会设置 button.hovered，自绘时不设置它；如需 hover 工具提示，自己处理
        return;
    }
    button.render(ctx, mouseX, mouseY, selected);
}
```

- 自绘内容（用 `ctx.fill/fillGradient/text` 或 `RenderUtils`）：选中项渐变底 + 左侧 3px 高亮条 + 白色文字；hover
  项半透明底；文字垂直居中 `y + (h-8)/2`。
- **点击不用管**：`button` 还在列表里，`GuiBase.onMouseClicked` 原位调用；`enabled=false` 的当前 Tab 天然不可点（
  `ButtonBase.isMouseOver(int,int)` 里带 `enabled && visible`，`ButtonBase.java:102-106`）。
- **hover 不用管**：自绘自己算；若某些 Tab 有 `hoverStrings`，原 `drawButtonHoverTexts` 依赖 `button.isMouseOver()`（读
  `hovered` 字段，只有原 render 会设置）→ 自绘时想保留工具提示的话自己画，或不需要（masa 的 Tab 没有 hover 文案）。
- 可选更省事：在 `ButtonGeneric.render` HEAD 判断 `ConfigScreenManager.isNavButton(screen, this)`（通过
  `Minecraft.getInstance().gui.screen()` 拿当前 screen）→ 自绘 + `ci.cancel()`。但 `drawButtons` 的 Redirect 上下文更明确，推荐前者。

**注意**：

- litematica 的 `RENDER_LAYERS` Tab 点击走的是 `GuiBase.openGui(new GuiRenderLayer())`（换到另一个 `GuiBase` 屏幕，不是
  `GuiConfigsBase`），迁移后该行为不变；`GuiRenderLayer` 不会套用新样式（如果要求覆盖，需要另开任务）。
- 当前 Tab 的 `enabled=false` 语义在三个样本中都成立；但 disabled 按钮**仍会被渲染**（`ButtonGeneric.render` 不检查
  enabled，只换纹理/文字颜色），自绘时用 `!enabled` 作为选中态即可。

---

## 6. 列表重定位（调查点 4/5）——最关键、最容易出错

### 6.1 为什么不能只 `setSize()` / `setPosition()`

`GuiConfigsBase.createListWidget`（`GuiConfigsBase.java:173-178`）：

```java
@Override
protected WidgetListConfigOptions createListWidget(int listX, int listY) {
    return new WidgetListConfigOptions(listX, listY,
            this.getBrowserWidth(), this.getBrowserHeight(), this.getConfigWidth(), 0.f,
            this.useKeybindSearch(), this);
}
```

`WidgetListConfigOptions` 构造器（`WidgetListConfigOptions.java:22-40`）：

```java
public WidgetListConfigOptions(int x, int y, int width, int height, int configWidth,
                               float zLevel, boolean useKeybindSearch, GuiConfigsBase parent) {
    super(x, y, width, height, configWidth);
    this.parent = parent;
    if (useKeybindSearch) {
        this.widgetSearchConfigs = new WidgetSearchBarConfigs(x + 2, y + 4, width - 14, 20, 0, MaLiLibIcons.SEARCH, LeftRight.LEFT);
        this.widgetSearchBar = this.widgetSearchConfigs;
        this.browserEntriesOffsetY = 23;
    } else {
        this.widgetSearchConfigs = null;
        this.widgetSearchBar = new WidgetSearchBar(x + 2, y + 4, width - 14, 14, 0, MaLiLibIcons.SEARCH, LeftRight.LEFT);
        this.browserEntriesOffsetY = 17;
    }
}
```

- `WidgetListBase.posX/posY` 是 **`protected final int`**（`WidgetListBase.java:23-24`），构造后不可改。
- `WidgetListBase.setSize(w,h)`（`WidgetListBase.java:435-446`）只更新
  `totalWidth/totalHeight/browserWidth/browserHeight/browserPaddingX/Y/browserEntriesStartX/Y/browserEntryWidth`，**既不移动
  posX/posY，也不移动/改宽搜索框**。
- `WidgetSearchBar` 没有重写 `setPosition`，它的 `searchBox`/`iconSearch` 在构造器里按 x/y/width 一次算死（
  `WidgetSearchBar.java:20-33`）。

**所以：列表位置必须通过“重建 list widget”来生效；列表宽度必须在重建时通过构造参数（来自 `getBrowserWidth/getBrowserHeight`
）给对。只改 size 会把搜索框留在旧坐标/旧宽度（典型症状：搜索框仍按 `screenWidth-20` 宽，溢出到面板右侧）。**

### 6.2 三个可行方案对比

| 方案                                         | 做法                                                                                                                                                                                                                                      | 优点                                                         | 缺点                                                                                                                                                                                                  |
|----------------------------------------------|-------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|--------------------------------------------------------------|-------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| **A. 改 listX/listY + 重建（当前草稿方向）** | `ListBaseAccessor.setListX/Y(layout.listX/listY)` → `reCreateListWidget()` → 确认尺寸；Tab 切换/NPC 时因为字段持久化，重建自动用新坐标                                                                                                    | 兼容 `GuiModConfigs`（无 Tab 时不会走该分支）、兼容 Tab 切换 | 需要额外让 `getBrowserWidth/Height` 返回布局尺寸，否则构造时宽度还是旧的；initialize 时会多刷新一次列表                                                                                               |
| **B. 取消 `createListWidget` 自己 new**      | `@Inject(method="createListWidget", at=HEAD, cancellable=true)`：`new WidgetListConfigOptions(layout.listX, layout.listY, layout.listW, layout.listH, shadowGetConfigWidth(), 0f, shadowUseKeybindSearch(), this); cir.setReturnValue(w)` | 一次成型、无双重 init、搜索框/条目宽度一定正确               | `createListWidget` 在 `super.initGui()` 时就调用（Tab 数量/是否无 Tab 尚不知道）；若子类重写 `createListWidget` 则注入不到；仍需 `getBrowserWidth/Height` 注入配合 `GuiListBase.initGui` 的 `setSize` |
| **C. 不重建，直接反射改 final 字段**         | 用 `Unsafe`/`Field.setAccessible` 改 `posX/posY`                                                                                                                                                                                          | 无                                                           | **不要做**（脆弱、Java 25 final 字段、其它缓存如 `browserEntriesStartX` 也要跟着改）                                                                                                                  |

**推荐：A + B 的公共补丁 = `getBrowserWidth/getBrowserHeight` 注入 + 方案 A 的重建时机。** 具体：

```java
@Mixin(GuiConfigsBase.class)
public abstract class GuiConfigsBaseMixin {
    @Inject(method = "getBrowserWidth", at = @At("RETURN"), cancellable = true)
    private void malilibui$width(CallbackInfoReturnable<Integer> cir) {
        Integer w = ConfigScreenManager.listWidthFor((GuiConfigsBase) (Object) this);
        if (w != null) cir.setReturnValue(w);
    }

    @Inject(method = "getBrowserHeight", at = @At("RETURN"), cancellable = true)
    private void malilibui$height(CallbackInfoReturnable<Integer> cir) {
        Integer h = ConfigScreenManager.listHeightFor((GuiConfigsBase) (Object) this);
        if (h != null) cir.setReturnValue(h);
    }
}
```

`listWidthFor/HeightFor` 只在“该屏幕已被托管且 layout 已就绪”时返回布局值，否则返回 null（保持原实现）。这样：

- 首次 `super.initGui()` 时还没托管（layout 未就绪）→ 列表按原版建出来（临时错位一帧）；
- `GuiBase.init` TAIL 的 ensure 里：先算 layout、标记托管 → 设 `listX/listY` → `reCreateListWidget()` → 构造器调用
  `getBrowserWidth/Height` 此时返回布局值 → **新列表 + 新搜索框一切正确**；
- Tab 切换：mod 先 `reCreateListWidget()`（此时已托管 → 正确尺寸），再 `initGui()`（`GuiListBase.initGui` 的
  `setSize(getBrowserWidth(), getBrowserHeight())` 也正确）；
- resize：ensure 检测 `posX/posY` 或尺寸不匹配 → 重建（此时 getBrowser 返回新布局值）。

**注意**：`getBrowserWidth()` 也用于 `createListWidget`（构造参数）；`getBrowserHeight()` 不用于构造，但用于
`GuiListBase.initGui` 的 `setSize`。两者都要。

### 6.3 `GuiModConfigs` 特例：可以托管，但有一个必须知道的 override

`config/gui/GuiModConfigs.java`（源码完整如下，三个版本 jar 中该类一致）：

```java
public class GuiModConfigs extends GuiConfigsBase {
    public GuiModConfigs(String modId, List<...>configs, String titleKey, Object... args) {
        super(10, 0, modId, null, titleKey, args);   // ★ listX=10, listY=0（原设计：无顶部标题/按钮条）
    }

    @Override
    protected int getBrowserHeight() {
        return this.height - 70;
    }   // ★ 重写了 getBrowserHeight

    @Override
    protected void drawTitle(...) { /* NO-OP */ }

    @Override
    public List<ConfigOptionWrapper> getConfigs() {
        return this.configs;
    }
}
```

**关键事实（javap 确认 0.28.9 / 0.29.3 / 0.30.1 都有这个 override）：** `GuiConfigsBaseMixin` 注入的是
`GuiConfigsBase.getBrowserHeight()` 方法体，**对子类 override 不生效**。所以对 `GuiModConfigs`：

- `getBrowserWidth()` 注入生效（它不 override）→ 列表/搜索框宽度 = `layout.listW` ✅
- `getBrowserHeight()` 注入**不生效** → 构造时拿到的是 `height - 70`（而 layout 期望
  `panelH - HEADER_H - PAD = height - 84`），列表比面板高 14px：列表底 = `(panelY+HEADER_H) + (height-70) = height-10`
  ，面板底 = `height-14` → 多出 4px 画到面板外（行数也可能多一行）。
- 由于 `ensureLayout` **每帧**在 `extractRenderState` HEAD 跑：首帧 `init` TAIL 里 `reCreateListWidget()`
  之后没有立刻复查尺寸 → 该帧结束时列表是 `height-70`；**紧接着同一帧的 `extractRenderState` HEAD**（下一次绘制前）position
  已正确 → 走“尺寸不对 → `setSize+initGui`”分支 → 修正为 `layout.listH`，然后才开始绘制。**所以实际不可见**（每帧 ensure 在
  draw 之前）；只是多了一次 `initGui`/`refreshEntries` 的开销。若以后把 `extractRenderState` HEAD 的 ensure 去掉，则它会变成持久差
  14px。
- 功能上不会错位/错点（行坐标、滚动都由 setSize 后的 browser 值重算），视觉上只是 `GuiModConfigs` 会多一条 46px 的空 header
  带（它原本 `listY=0`、无标题），与你“统一视觉”的取舍一致。
- `GuiModConfigs.drawTitle` 是 NO-OP override，所以 `GuiBaseMixin` 的 `drawTitle` HEAD-cancel 对它不触发也没关系（原本就不画标题）。
- 它没有 Tab 按钮 → `layout.hasSidebar=false` → 列表占满面板；`ensureModSwitcher` 仍会把它从 `GuiConfigsBase` 继承来的
  mod 切换器挪到内容标题行右侧；`WidgetConfigOptionMixin`/`GuiScrollBarMixin`/`WidgetSearchBarMixin`/`ButtonGenericMixin`
  都会对它生效（统一视觉）。
- 结论：**保留托管是可接受的**（当前实现下无持久错位）。若想消除那 1 帧瞬态 / 让任何“override getBrowser*”的子类都确定性正确，在
  `ensureList` 的“位置不对→重建”分支末尾**重新读 accessor 复查尺寸**即可：

```java
if(positionOk ==false){
        listAccess.

malilibui$setListX(layout.listX);
    listAccess.

malilibui$setListY(layout.listY);
    listAccess.

malilibui$reCreateListWidget();

widget =listAccess.

malilibui$getListWidget();
    if(widget ==null)return;
widgetAccess =(WidgetListBaseAccessor)widget;          // ← 重新取 accessor
// fall through 到下面的尺寸检查，而不是直接 return
}
        if(widgetAccess.

malilibui$getBrowserWidth() !=layout.listW ||
        widgetAccess.

malilibui$getBrowserHeight() !=layout.listH){
        widget.

setSize(layout.listW, layout.listH);
    widget.

initGui();
}
```

- 若坚持“无 Tab 不托管”也完全可以，只需在捕获结果为空时 `STATES.remove(screen); return;`（但那会让第三方 mod 的
  `GuiModConfigs` 保持原版外观）。**两种都可，推荐上一种（re-check）**，因为 `GuiModConfigs` 是 malilib 公开 API，第三方 mod
  可能用到。

### 6.4 布局几何建议（可直接沿用当前 `ConfigLayout`）

当前草稿的 `ConfigLayout`（`gui/ConfigLayout.java`）已经是可用形状，报告中确认其与 malilib 的兼容性：

- `panelX/Y = MARGIN(14)`，`panelW/H = screen - 2*MARGIN`；
- `sidebarW = clamp(screenW * 0.22, 148, 212)`（有 Tab 时）；
- `contentX = panelX + sidebarW + 1`；
- `listX = contentX + PAD(10)`，`listY = contentY + HEADER_H(46)`，`listW = contentW - 2*PAD`，
  `listH = contentH - HEADER_H - PAD`；
- `navX = sidebarX + 10`，`navY = sidebarY + NAV_TOP(62)`，`navW = sidebarW - 20`，`navItemH = 26`，`gap = 2`；Tab 多时压缩
  itemH 但 ≥18。

约束检查（用最宽的配置列验证）：

- litematica `getConfigWidth()`：`GENERIC/VISUALS=180`、`INFO_OVERLAYS=140`、`COLORS=100`、其余 `204`（
  `litematica GuiConfigs.java:76-94`）。
- tweakeroo：`GENERIC=170`、`FIXES=60`、`LISTS=200`、其余 `260`（javap 常量）。
- 行控件从 `rowX + labelWidth + 10` 开始；`labelWidth = 当前筛选结果里最长的配置显示名`（
  `WidgetListConfigOptions.reCreateListEntryWidgets` 里 `getMaxNameLengthWrapped(listContents)`，对**全部过滤结果**
  算，滚动时稳定）。
- 1280×720 下：listW = 1280-28-(212+1)-20 ≈ 1019；最宽行 260(控件)+~200(标签)+~30(重置)+20(icon) ≈ 510，安全。
- GUI scale 3 + 1920×1080 → scaled 640×360：sidebarW=min(212,max(148,140.8))=148；listW=640-28-149-20=443；260+200+30≈490 >
  443 → **可能出现横向溢出**。建议：`MIN_SCREEN_W = 480`（或按 layout 动态收窄 `configWidth` → 不可行，configWidth 来自 mod
  子类）→ 优先“小屏回退原版布局”；或在 `GuiListBase.drawContents` 上加 scissor 把列表内容裁剪到 listW 内（溢出被裁掉总比画到面板外好）。
- 行高 `browserEntryHeight = 22`（`WidgetListConfigOptionsBase.java:26`），标签行 22。

### 6.5 resize 处理流程

见 §3.4。实现要点：

1. `GuiBase.init` TAIL / `extractRenderState` HEAD 的 ensure 中，若
   `widgetAccess.getListPosX() != layout.listX || ...getListPosY() != layout.listY` → 设 listX/listY +
   `reCreateListWidget()`。
2. 若 `browserWidth/Height` 与 layout 不符 → `widget.setSize(layout.listW, layout.listH)` + `widget.initGui()`
   （这一步在“位置已对、只是尺寸变”的情况下才需要；若刚重建则尺寸已对）。
3. 不要在 `GuiListBase.resize` 里重复重建（`Screen.resize` 已经触发过 init TAIL 的 ensure），最多打日志。

---

## 7. mod 切换下拉（调查点 6）

### 7.1 创建与几何

`GuiConfigsBase.buildConfigSwitcher()`（`GuiConfigsBase.java:61-112`）：

```java
this.modSwitchWidget = new WidgetDropDownList<>(
        GuiUtils.getScaledWindowWidth() - 155, 6, 130, 18, 200, 10,
        Registry.CONFIG_SCREEN.getAllModsWithConfigScreens())
{
    { this.selectedEntry = thisMod; }
    @Override protected void setSelectedEntry(int index) {
        super.setSelectedEntry(index);
        if (selectedEntry != null && selectedEntry.configScreenSupplier() != null)
            mc.gui.setScreen(selectedEntry.configScreenSupplier().get());   // 打开别的 mod 的配置屏
    }
    @Override protected String getDisplayString(ModInfo entry) { return entry.modName(); }
};
addWidget(this.modSwitchWidget);
```

- 仅当 `MaLiLibConfigs.Generic.ENABLE_CONFIG_SWITCHER.getBooleanValue()` 为 true 时创建（默认 true，见
  `MaLiLibConfigs.java:52`）。
- 位置/尺寸：`(screenW-155, 6, 130, 18)`；`maxHeight=200`、`maxVisibleEntries=10`；构造里还会
  `width = getRequiredWidth(width, entries, mc)`（width=130 时不变）。
- 渲染：`GuiBase.drawWidgets` 遍历 `widgets` 调 `WidgetDropDownList.render`（`WidgetDropDownList.java:267-305`
  ）：打开时画下拉列表 + 搜索框（若有过滤文本）；关闭时只画外框 + 选中文本 + 箭头图标（`MaLiLibIcons.TEXTURE` 的
  `ARROW_UP/ARROW_DOWN`）。开着的列表在 `postRenderHovered` 里**再画一次并 `ctx.elementUp()` 抬到最上层**（
  `WidgetDropDownList.java:353-367`），所以下拉展开时会盖住列表内容。
- `setPosition(x,y)` 重写（`WidgetDropDownList.java:82-89`）会同时把内部搜索框挪到 `(x+1, y-18)`（注意：搜索框固定画在**上方
  18px**）。
- 宽度/高度没有 setter 级联：`setWidth` 只改绘制宽度，内部 `searchBar` 的 `GuiTextFieldGeneric` 宽度固定为构造时
  `width-2`；`totalHeight/maxVisibleEntries` 固定。

### 7.2 迁移建议

- **最小方案（推荐 v1）**：`buildConfigSwitcher` TAIL 注入（或 ensure 里）取 `modSwitchWidget` 调 `setPosition(...)`。当前草稿
  `ensureModSwitcher` 用 `(contentX+contentW-148, contentY+18)`，注意两个细节：
    - 搜索框在 `y-18` → 当 `y = contentY+18` 时搜索框正好在 `contentY`（面板上沿），仅过滤时可见，可接受；若要求完全在面板内，取
      `y >= contentY+26`（此时下拉框底 `contentY+44 < listY=contentY+46`，不压住列表）。
    - 展开列表向下延伸 ~`18 + 10*18`，在 content header 区域会盖住列表顶部一点，正常（原版也这样）。
- **自绘外框方案**：`@Inject(method="render", at=@At("HEAD"), cancellable=true)` on `WidgetDropDownList`；只对“config 屏 +
  正是 modSwitchWidget”的自绘 `modName ▾`，然后 `ci.cancel()`。开着的列表仍由 `postRenderHovered` 绘制（因为
  `GuiBase.drawHoveredWidget` 会调 `widget.postRenderHovered`），点击逻辑不变（`onMouseClickedImpl` 只依赖 x/y/width）。需要
  `@Accessor("isOpen")`、`@Accessor("selectedEntry")`（或用 public `getSelectedEntry()`）、`@Accessor("maxVisibleEntries")`
  等读取状态。
- **不要**直接把 widget 从 `widgets` 移除再自己实现选择器：`Registry.CONFIG_SCREEN` 的注册/跳转逻辑都在这个匿名子类里，复制成本高且易漏。

---

## 8. 行布局与行渲染（调查点 7）

### 8.1 行创建（`WidgetListBase.reCreateListEntryWidgets`，`WidgetListBase.java:453-492`）

```java
int x = this.posX + 2;
int y = this.posY + 4 + this.browserEntriesOffsetY;
...
widget = this.createListEntryWidget(x, y, listIndex, (listIndex & 0x1) != 0, entry);
```

`WidgetListConfigOptions.createListEntryWidget`（`:134-138`）：

```java
return new WidgetConfigOption(x, y, this.browserEntryWidth /*browserWidth-14*/,
        this.browserEntryHeight /*22*/, this.maxLabelWidth, this.configWidth, wrapper,
        listIndex, this.parent, this);
```

- 搜索框高度不同导致 `browserEntriesOffsetY = 23`（带 keybind 搜索）或 `17`（普通搜索）——列表重定位时这个偏移由构造器按
  useKeybindSearch 决定，不需要我们动。
- 只有**可见行**被创建；滚动/搜索时 `reCreateListEntryWidgets()` 重建（`refreshBrowserEntries` / `drawContents`
  里发现滚动条值变化时）。行 widget 实例会变，因此“导航项/行悬浮状态”不能跨帧缓存 widget 实例，只能按坐标或每帧重算。

### 8.2 `WidgetConfigOption` 内部布局（`WidgetConfigOption.java:100-294`）

- `y += 1`（行内上边距）；`configHeight = 20`。
- 名称列：`addLabel(x, y + 7, labelWidth, 8, 0xFFFFFFFF, configName)`（`:140`）。
- 注释/hover 信息：`addConfigComment(x, y + 5, labelWidth, 12, comment)`（`:161-168`）→ `WidgetHoverInfo`/
  `WidgetHoverComponent`，鼠标在名称列上时在光标处画提示（经 `postRenderHovered` 链）。
- 控件列：`x += labelWidth + 10`（`:170`）。
- 重置按钮：文本 `malilib.gui.button.reset.caps`，`new ButtonGeneric(x, y, -1, 20, labelReset)`，
  `setEnabled(config.isModified())`（`WidgetConfigOptionBase.java:71-78`）；位置 `x + configWidth + 2`（按钮/选项类）或
  `resetX`（文本类）。
- 各类型控件列宽（同一行从控件列起点开始）：

| Config 类型                                                                                               | 控件                                                                                                                        | 额外                                                                                                    |
|-----------------------------------------------------------------------------------------------------------|-----------------------------------------------------------------------------------------------------------------------------|---------------------------------------------------------------------------------------------------------|
| BOOLEAN / OPTION_LIST / OPTION_VALUES / STRING_LIST / LOCKED_LIST / COLOR_LIST / BLOCK_STATE_LIST / TABLE | 按钮宽 `configWidth`、高 20，重置按钮 `x+configWidth+2`                                                                     | —                                                                                                       |
| HOTKEY                                                                                                    | keybind 按钮 `configWidth-22`；`WidgetKeybindSettings` 20×20 在右侧；再重置                                                 | `addHotkeyConfigElements`（`:378-390`）                                                                 |
| STRING / INTEGER / DOUBLE / FLOAT                                                                         | 文本框宽 `configWidth-4`（`createTextField(x, y+1, configWidth-4, 17)`），或 slider 宽 `configWidth`（`shouldUseSlider()`） | INTEGER/DOUBLE/FLOAT 时 `configWidth -= 18`，`colorDisplayPosX` 处放 18×18 的 toggle 按钮（`:243-287`） |
| COLOR                                                                                                     | 文本宽 `configWidth-22` + 18×18 颜色指示（`WidgetColorIndicator`）                                                          |                                                                                                         |
| BOOLEAN + HOTKEY（`BooleanHotkeyGuiWrapper`/`ConfigBooleanHotkeyed`）                                     | 布尔按钮 60 + keybind 按钮 `configWidth-84` + settings 20 + reset                                                           |                                                                                                         |

- LABEL 行：`else { addLabel(x, y + 7, labelWidth, 8, 0xFFFFFFFF, wrapper.getLabel()); }`（`:96`）。
- `WidgetConfigOption.render`（`:476-488`）：

```java
@Override public void render(GuiContext ctx, int mouseX, int mouseY, boolean selected) {
//  super.render(...);
    this.drawSubWidgets(ctx, mouseX, mouseY);          // 标签/注释/控件按钮
    if (wrapper.getType() == CONFIG) {
        this.drawTextFields(ctx, mouseX, mouseY);      // 文本框
        super.render(ctx, mouseX, mouseY, selected);    // WidgetContainer.render → 又画一遍 subWidgets
    }
}
```

（CONFIG 行的 subWidgets 会被画两次——原版小 bug，无害；我们的背景注入在 HEAD 不受影响。）

### 8.3 行背景/hover 推荐实现

在 `WidgetConfigOption.render` 的 **HEAD** 注入：

```java
@Inject(method = "render", at = @At("HEAD"))
private void malilibui$rowBackground(GuiContext ctx, int mouseX, int mouseY, boolean selected, CallbackInfo ci) {
    ConfigListStyler.drawRow((WidgetConfigOption) (Object) this, ctx, mouseX, mouseY);
}
```

- 行坐标直接用 `widget.getX()/getY()/getWidth()/getHeight()`（已是屏幕绝对坐标，`WidgetListBase` 创建时给的
  `posX+2 / posY+4+offset`）。
- 奇偶行：用 `row.getListIndex() % 2 == 1`（`WidgetListEntryBase.getListIndex()` public）。
- LABEL 行：`getEntry().getType() == ConfigOptionWrapper.Type.LABEL` → 画“分组标题”样式。
- hover：`mouseX >= x && mouseX < x+width && ...`（鼠标是 double → 注入参数已是 int，直接用）。
- 想画“行选中/hover 边框”也可以，但注意行内控件按钮自己会画 hover，别盖住它们（背景先画、控件后画，顺序天然正确）。
- 行背景**不要**用 `ctx.blitSprite` 每行画大贴图（性能）；纯色/渐变即可。

### 8.4 列表整体背景

若需要“右侧内容区列表底板”（现代卡片感），在 `GuiListBase.drawContents` HEAD 注入（gate：仅托管屏幕）：

```java
@Inject(method = "drawContents", at = @At("HEAD"))
private void malilibui$listPanel(GuiContext ctx, int mouseX, int mouseY, float partialTicks, CallbackInfo ci) {
    ConfigScreenManager.drawListPanel((GuiBase) (Object) this, ctx);
}
```

注意 `GuiListBase.drawContents` 也会被其它 `GuiListBase` 子屏幕（litematica 的 Scheduler/材料列表等）调用，务必
`instanceof GuiConfigsBase` + 托管状态双重门控。

---

## 9. MC 26.3 渲染 API（调查点 8）

### 9.1 模型：GuiGraphicsExtractor → GuiRenderState → GuiRenderer

- 26.x 的 GUI 渲染分两阶段：**抽取（extract）**阶段收集“渲染状态对象”（`GuiElementRenderState` 子类：
  `ColoredRectangleRenderState`、`BlitRenderState`、`GuiTextRenderState`、`GuiItemRenderState`…）进 `GuiRenderState`；
  **绘制**阶段由 `GuiRenderer` 统一提交 GPU。没有旧式 `RenderSystem.setShader`/`BufferSource` 立即绘制。
- `GuiGraphicsExtractor` 是旧 `GuiGraphics` 的改名（`versions/mapping-1.21.11-26.1.txt`：
  `GuiGraphics → GuiGraphicsExtractor`、`drawString → text`）。
- `Renderer.extractRenderStateWithTooltipAndSubtitles`（final）负责 `nextStratum()` → `extractBackground` →
  `nextStratum()` → `extractRenderState` → `extractDeferredElements()` 的顺序。
- `GuiRenderState` 有“层（stratum/layer）”概念：`nextStratum()` 进入下一层；`elementUp()`（`GuiContext` 包装
  `guiRenderState.up()`）把**之后**添加的元素抬到当前层之上（原版打开的下拉列表、工具提示用这个技巧）。`ctx.nextStratum()` 在
  `GuiBase.extractRenderState` 开头调用一次；同一层内按添加顺序绘制。
- **自定义元素**：可以实现 `net.minecraft.client.renderer.state.gui.GuiElementRenderState`（接口：
  `buildVertices(VertexConsumer)` + `pipeline()`/`textureSetup()`/`pose()`/`bounds()` 等访问器）并
  `ctx.addSimpleElement(element)`。但该接口的 `RenderPipeline` 类型在 26.3 挪包 → **共享源码不要自己实现这个接口**
  （26.1/26.2 编译不过）。用 malilib 现成的 `MaLiLibBasicRectGuiElement` / `MaLiLibGradientRectGuiElement` /
  `MaLiLibTexturedGuiElement` 或 `RenderUtils` 对应函数。

### 9.2 `GuiContext` 是什么

`render/GuiContext.java`：`public class GuiContext extends GuiGraphicsExtractor`，构造时把
`minecraft/pose/guiRenderState/mouseX/mouseY/pendingCursor/deferredTooltip/hoveredTextStyle/clickableTextStyle`
拷进来，并保存原始 `guiGraphics` 引用（`getGuiGraphics()`）。`fromGuiGraphics(GuiGraphicsExtractor)` 用原始对象的 `pose`（
**同一个 `Matrix3x2fStack` 引用**）构造包装。

对我们实现者的含义：

1. mixin 回调里拿到的 `GuiContext` 参数，**直接当 `GuiGraphicsExtractor` 用**：`ctx.fill(...)`、`ctx.fillGradient(...)`、
   `ctx.text(...)`、`ctx.blit(...)`、`ctx.blitSprite(...)`、`ctx.enableScissor(...)` 全部可用（继承自父类）。
2. malilib 自己的 `RenderUtils.xxx(GuiContext, ...)` 都可用；本报告 §9.4 只列三版本都存在的重载。
3. `ctx.fontRenderer()` → `Minecraft.getInstance().font`；`ctx.pose()` → `Matrix3x2fStack`（可
   push/pop/translate，注意元素创建时会拷贝 pose）。
4. `ctx.getGuiGraphics()` 是原始 `GuiGraphicsExtractor`（给 `GuiTextFieldGeneric.extractRenderState` 等 MC 方法用）；二者
   pose 引用相同。
5. **scissor 状态不继承**：`GuiContext` 构造时会新建自己的 `ScissorStack`（初始为全屏矩形），从
   `GuiGraphicsExtractor.fromGuiGraphics` 不会拷贝外层 scissor。如果 gui 在一个外层 scissor 内，自己 `ctx.enableScissor`
   一下。

### 9.3 可用绘制原语（`GuiGraphicsExtractor` 公共方法，三版本同名同签名）

| 方法                                                                                                            | 说明                                                                                                                        |
|-----------------------------------------------------------------------------------------------------------------|-----------------------------------------------------------------------------------------------------------------------------|
| `fill(int x1,int y1,int x2,int y2,int color)`                                                                   | 实心矩形（ARGB）                                                                                                            |
| `fillGradient(int x1,int y1,int x2,int y2,int colorTop,int colorBottom)`                                        | 垂直渐变                                                                                                                    |
| `fill(RenderPipeline, int, int, int, int, int)` / `fill(RenderPipeline, TextureSetup, ...)`                     | 指定 pipeline/纹理；**参数类型 26.3 挪包，源码里只写表达式即可**                                                            |
| `outline(int x,int y,int width,int height,int color)`                                                           | 1px 内描边                                                                                                                  |
| `horizontalLine(int x1,int x2,int y,int color)` / `verticalLine(int x,int y1,int y2,int color)`                 | 注意参数顺序                                                                                                                |
| `text(Font, String/Component/FormattedCharSequence, int x,int y,int color[, boolean shadow])`                   | 文字                                                                                                                        |
| `centeredText(Font, ..., int centerX,int y,int color)`                                                          | 居中文字                                                                                                                    |
| `textWithWordWrap(...)` / `textWithBackdrop(...)`                                                               |                                                                                                                             |
| `blit(RenderPipeline, Identifier, int x,int y, float u,float v, int w,int h, int texW,int texH[, int color?])`  | 普通纹理（Identifier 是完整纹理路径，如 `ns:textures/gui/panel.png`）                                                       |
| `blitSprite(RenderPipeline, Identifier, int x,int y,int w,int h)`                                               | GUI atlas sprite（Identifier 是 sprite id，如 `minecraft:widget/button`；会读取 `.mcmeta` 的 stretch/tile/nine_slice 缩放） |
| `nextStratum()` / `elementUp()` / `enableScissor(x1,y1,x2,y2)` / `disableScissor()` / `blurBeforeThisStratum()` | 层/裁剪                                                                                                                     |
| `addSimpleElement(GuiElementRenderState)` 等                                                                    | 自定义元素（见 §9.1 版本限制）                                                                                              |

> `ctx.shape(...)` 之类的旧 API 在 26.3 不存在；`GuiContext` 的便利方法（`drawString/centeredText/renderTooltip/...`）见
> `GuiContext.java:272-355`。

### 9.4 malilib `RenderUtils` 可用性（面板/背景/文字/贴图相关）

三版本（0.28.9 / 0.29.3 / 0.30.1）**都存在且签名相同**、可安全使用的：

```java
RenderUtils.drawRect(GuiContext ctx, int x, int y, int width, int height, int color);
RenderUtils.drawRect(GuiContext ctx, int x, int y, int width, int height, int color, float scale);
RenderUtils.drawGradientRectBatched(GuiContext ctx, float left, float top, float right, float bottom, int startColor, int endColor);
RenderUtils.drawOutline(GuiContext ctx, int x, int y, int width, int height, int colorBorder);
RenderUtils.drawOutline(GuiContext ctx, int x, int y, int width, int height, int borderWidth, int colorBorder);
RenderUtils.drawOutlinedBox(GuiContext ctx, int x, int y, int width, int height, int colorBg, int colorBorder);
RenderUtils.drawTexturedRect(GuiContext ctx, Identifier texture, int x, int y, int u, int v, int width, int height);
RenderUtils.drawTexturedRect(GuiContext ctx, Identifier texture, int x, int y, int u, int v, int width, int height, float zLevel);       // zLevel 未使用
RenderUtils.drawTexturedRect(GuiContext ctx, Identifier texture, int x, int y, int u, int v, int width, int height, float zLevel, int argb); // 带颜色
RenderUtils.drawHoverText(GuiContext ctx, int x, int y, List<String> textLines);
RenderUtils.drawHorizontalLine(GuiContext ctx, int x, int y, int width, int color);
RenderUtils.drawVerticalLine(GuiContext ctx, int x, int y, int height, int color);
RenderUtils.drawScreenBlur(Minecraft mc);
```

它们的实现：`drawRect`/`drawGradient` 创建 malilib 自己的 `GuiElementRenderState` record（
`render/element/MaLiLibBasicRectGuiElement` / `MaLiLibGradientRectGuiElement`），`drawTexturedRect` 通过
`ctx.bindTexture(id)` 取纹理再 `MaLiLibTexturedGuiElement`；`drawOutline` 内部是 4 次 `drawRect`。

**不要用**（三版本不一致 / 是旧 immediate-mode / 名不对）：

- 所有**不带 `GuiContext`** 的重载：`drawRect(int,int,int,int,int[, ...])`、
  `drawGradientRect(float,float,float,float,float,int,int)`、`drawTexturedRect(Matrix4f, ...)`、
  `drawTexturedRectBatched(int, ...)`。它们走 `RenderContext`/`BufferBuilder` 立即绘制，在 GUI 抽取阶段不适用（0.30.2 源码已标
  `@Deprecated(forRemoval = true)`，见 `RenderUtils.java:204/220/236/253/271/367/501/519/538`）。
- `RenderUtils.drawTexturedRectBatched(GuiContext, Pair<...>, ...)`：`Pair` 里的 `GpuTextureView/GpuSampler` 在 26.3 挪到
  `com.mojang.renderpearl.api.textures`、26.1/26.2 在 `com.mojang.blaze3d.textures` → **共享源码不要写这个类型**。
- `RenderUtils.drawHoverText(GuiContext,int,int,Component)`：0.30.1 才有（0.28.9 没有）。
- `RenderUtils.drawTextPlate(...)` 系列：0.28.9→0.30.1 签名有变化（不建议用）。
- malilib 内部 `MaLiLibPipelines.*` / `MaLiLibBasicRectGuiElement` 等自定义 pipeline/元素 record：**不要直接引用**
  （它们字段类型含挪包的 `RenderPipeline`；跨版本编译不过）。需要什么就调用上面的 `RenderUtils` 方法。

### 9.5 自定义纹理 / sprite

**(a) 普通纹理（不走 atlas）**

```java
// 文件: src/main/resources/assets/malilib-ui/textures/gui/panel.png
Identifier tex = Identifier.fromNamespaceAndPath("malilib-ui", "textures/gui/panel.png");
ctx.blit(RenderPipelines.GUI_TEXTURED, tex, x, y, 0f, 0f, w, h, 256, 256);
// 或 malilib 封装（内部走 TextureManager.bindTexture）：
RenderUtils.drawTexturedRect(ctx, tex, x, y, u, v, w, h);
```

> `RenderUtils.drawTexturedRect` 的 id 需要带 `textures/` 前缀（它调
> `Minecraft.getInstance().getTextureManager().getTexture(id)`）；`ctx.blit(Identifier...)` 同理。

**(b) GUI sprite（推荐做“现代 MC 风格”面板/按钮，支持 9-slice）**

- 文件：`src/main/resources/assets/malilib-ui/textures/gui/sprites/widget/panel.png`；可选 `.mcmeta`：
  ```json
  { "gui": { "scaling": { "type": "nine_slice", "width": 200, "height": 20, "border": 3 } } }
  ```
  （9-slice 参考 vanilla `assets/minecraft/textures/gui/sprites/widget/tab.png.mcmeta`：
  `width:130,height:24,border:{left:2,top:2,right:2,bottom:0}`）
- GUI atlas 定义（26.3 `assets/minecraft/atlases/gui.json`）只写了
  `{"type":"minecraft:directory","prefix":"","source":"gui/sprites"}`，**没有 namespace 限制** → 所有 mod 的
  `assets/<ns>/textures/gui/sprites/**` 都会进 GUI atlas。
- 代码：
  ```java
  Identifier sprite = Identifier.fromNamespaceAndPath("malilib-ui", "widget/panel");
  ctx.blitSprite(RenderPipelines.GUI_TEXTURED, sprite, x, y, w, h);   // 自动按 .mcmeta 做 9-slice
  ```
- 可直接复用的 vanilla sprite id（26.3 jar 内确认存在）：
    - `minecraft:widget/button`、`widget/button_highlighted`、`widget/button_disabled`（9-slice 200×20 border3）
    - `minecraft:widget/tab`、`widget/tab_highlighted`、`widget/tab_selected`、`widget/tab_selected_highlighted`（9-slice
      130×24，border 左2/上2/右2/下0）——**横向 Tab 的现代原版样式**
    - `minecraft:widget/text_field`、`widget/text_field_highlighted`
    - `minecraft:widget/scroller`、`widget/scroller_background`、`widget/slider*`、`widget/checkbox*`
    - 分隔条：`Screen.HEADER_SEPARATOR` / `Screen.FOOTER_SEPARATOR` / `INWORLD_*`（public static final `Identifier`）
- 低层纹理（`Screen.MENU_BACKGROUND` / `GuiBase.BG_TEXTURE` 等）是 `textures/gui/*.png` 普通纹理，用 `(a)` 的方式画。

**(c) scissor 使用**

```java
ctx.enableScissor(x, y, x + w, y + h);      // 注意参数是 (x1,y1,x2,y2) 绝对坐标；内部会乘当前 pose
try { ... 画列表内容 ... } finally { ctx.disableScissor(); }
```

用途：把列表行/搜索框裁剪在面板内部，防止窄屏时控件横向溢出；滚动内容超界也被裁剪。注意工具提示在 `drawHoveredWidget`
阶段画，不要在 scissor 还在的时候画。

### 9.6 推荐的背景绘制组合（无自定义纹理时的“现代感”）

```java
// 1. 全屏暗色遮罩（替代原版 TOOLTIP_BACKGROUND，避免世界透出来太亮）
ctx.fill(0,0,w, h, 0xC6121017);
// 2. 面板阴影（4 层半透明矩形外扩）
// 3. 面板主体 + 垂直渐变
ctx.

fillGradient(px, py, px+pw, py+ph, 0xF21E212C,0xF0161821);
// 4. 1px 外描边 + 顶部内高光
ctx.

outline(px, py, pw, ph, 0xFF0A0B10);      // 注意 outline 画在矩形内侧
ctx.

horizontalLine(px+1, px+pw-1, py+1, 0xFF3B415A);
// 5. 侧栏渐变 + 分割线
ctx.

fillGradient(sx, sy, sx+sw, sy+sh, 0xF0161823,0xF00E0F16);
ctx.

verticalLine(sx+sw, sy, sy+sh, 0xFF2A2F42);
// 6. 选中导航项：渐变 + 左侧 3px 高亮条 + 白字
ctx.

fillGradient(nx, ny, nx+nw, ny+nh, 0xFF2E3A64,0xFF212A47);
ctx.

fill(nx, ny+4, 3,nh-8, 0xFF6C8CFF);
ctx.

text(font, label, nx+12, ny+(nh-8)/2,0xFFFFFFFF,true);
```

全部是 vanilla API，三版本源码级兼容。若想更“现代 MC”，把面板/按钮换成 vanilla 的 `widget/button`、`widget/tab_selected` 等
sprite（见 §9.5b）。

---

## 10. 三版本差异结论（调查点 9）与“一套 mixin 是否通用”

### 10.1 结论

**能。** 一套针对 malilib GUI 类的 mixin 注入可以同时兼容 0.28.9 / 0.29.3 / 0.30.1（MC 26.1/26.2/26.3），因为：

1. 目标类的字段/方法签名集合在三个 jar 中一致（javap 逐类 diff 为空，见 §2.3 表）；
2. 我们计划使用的所有注入方法（
   `init/initGui/extractRenderState/drawScreenBackground/drawTitle/drawButtons/addButton/getBrowserWidth/getBrowserHeight/buildConfigSwitcher/createListWidget/reCreateListWidget/WidgetConfigOption.render/GuiScrollBar.render`
   ）字节码结构一致（仅常量池编号不同）；
3. malilib 自己的 mixin 不涉及这些类（`mixins.malilib.json` 的 `client` 列表里没有自 GUI 类；它 mixin 的是 MC 的
   `GuiGraphicsExtractor`、`Gui`、`Screen` 子类等），所以不存在 malilib 自身 mixin 抢注入点的问题；malilib 的 mixin 优先级是
   `mixinPriority: 500`，我们默认 1000（比它后应用/更高优先级；必要时显式写 `priority = 1000`）。
4. 0.30.1 与 0.30.2 目标类逐字节相同 → 源码 0.30.2 阅读结论直接适用。

### 10.2 需要条件分支/规避的点

| 差异                                                                                                                               | 版本                                                                                 | 规避方式                                                                                                               |
|------------------------------------------------------------------------------------------------------------------------------------|--------------------------------------------------------------------------------------|------------------------------------------------------------------------------------------------------------------------|
| `GuiBase.onFilesDrop/onMouseDropFiles` 新增                                                                                        | 0.30.1+                                                                              | 不要注入                                                                                                               |
| `WidgetConfigOption.addConfigComment(...,MutableComponent)` 新增                                                                   | 0.30.1+                                                                              | 不要注入                                                                                                               |
| 编译器合成 `access$xxx`（GuiConfigsBase、GuiConfigs 里）                                                                           | 0.28.9 等                                                                            | 不要注入合成方法                                                                                                       |
| `GuiContext.popScissor()` 返回类型（0.28.9 `ScreenRectangle` → 0.30.1 `void`）                                                     | 26.1 vs 26.3                                                                         | 当语句调用（忽略返回值）即可两边编译                                                                                   |
| `GuiContext.bindTexture/setupTexture` 返回 `Pair<GpuTextureView,GpuSampler>` 的包名                                                | 26.1/26.2 `com.mojang.blaze3d.textures` → 26.3 `com.mojang.renderpearl.api.textures` | **不要显式写这个类型**；用 `var` 或直接用 `RenderUtils.drawTexturedRect`                                               |
| `com.mojang.blaze3d.pipeline.RenderPipeline` → `com.mojang.renderpearl.api.pipeline.RenderPipeline`                                | 26.2 → 26.3 挪包                                                                     | 共享源码不出现类型名；只把 `RenderPipelines.GUI/GUI_TEXTURED` 作为**实参**传给方法（javac 按各版本自己的方法签名推断） |
| `GuiGraphicsExtractor.entity(...)`：26.1 `Vector3f/Quaternionf` → 26.2+ `Vector3fc/Quaternionfc`；`skin(PlayerModel)` 在 26.2 移除 | 26.1 vs 26.2+                                                                        | 不用这两个方法                                                                                                         |
| `GuiGraphicsExtractor.tooltip/setTooltipForNextFrame` 26.3 新增 `Identifier` 重载、`tooltip` 签名变化                              | 26.2 vs 26.3                                                                         | 不用原生 tooltip API；用 malilib `RenderUtils.drawHoverText` 或 `GuiContext.renderTooltip` 中三版本都有的重载          |
| `RenderUtils.drawTexturedRectBatched(GuiContext,Pair,…)`                                                                           | 类型挪包                                                                             | 不用；用 `drawTexturedRect(GuiContext,Identifier,...)`                                                                 |
| `RenderUtils.drawHoverText(...,Component)`                                                                                         | 0.30.1+                                                                              | 用 `List<String>` 重载                                                                                                 |
| `RenderUtils.drawTextPlate(...)` 签名变化                                                                                          | 0.28.9→0.30.1                                                                        | 不用                                                                                                                   |
| malilib 其它包（`fi.dy.masa.malilib.util.LayerRange`→`.util.position.LayerRange` 等）                                              | 0.29.3→0.30.1 有 `mapping-26.1.2-26.2.txt` 覆盖（少量）                              | 我们的 GUI 代码不涉及这些 util 类                                                                                      |
| MC 26.2→26.3 无 preprocessor 映射文件                                                                                              | 26.2 vs 26.3                                                                         | 共享源码只用两版本都有的名字（本报告 §9 列出的都满足）                                                                 |

### 10.3 按需版本分支的位置（如果将来必须）

- 优先把差异隔离到 `versions/<ver>/src/main/java/**` 的**同名覆盖文件**（只 override 差异文件，其他共享）。
- 或在共享源码里用注释预处理指令：
  ```java
  //#if MC_VERSION >= 260300
  //   ...26.3 only...
  //#else
  //   ...26.1/26.2...
  //#endif
  ```
  （`CommentPreprocessor` 支持 `if/ifdef/elseif/else/endif/eval`；变量由 preprocess 扩展注入，`mcVersion` 可用性需在首次用到时验证）。
- 本项目的 GUI 改造**当前不需要任何版本分支**。

---

## 11. 搜索框、焦点与键盘路由（调查点 11）

- `WidgetSearchBar` 的坐标全部在构造时算好：`iconSearch` 在 `(x+2, y+1)`（左对齐）或 `(x+width-iw-1, y+1)`（右对齐），
  `searchBox` 在 `(x+iw+6, y, width-iw-7, height)`（`WidgetSearchBar.java:26-30`）。**重建列表时传对
  x/y/width，搜索框自动跟随。**
- `WidgetSearchBarConfigs`（带 hotkey 过滤的搜索条）：`super(x, y+3, width-160, 14, ...)` + 右侧 140×20 的
  `ConfigButtonKeybind`（`WidgetSearchBarConfigs.java:23-31`）——宽列表才会启用；`useKeybindSearch()` 由子类按 Tab
  决定（litematica ALL/GENERIC/VISUALS/HOTKEYS；tweakeroo ALL/TWEAKS/GENERIC_HOTKEYS/DISABLES）。`Tab 切换会重建 list`
  ，此值随之变化。
- 渲染：`WidgetListBase.drawContents` 末尾画 `widgetSearchBar`（`WidgetListBase.java:422-425`）；打开时才画文本框（
  `WidgetSearchBar.render`，`WidgetSearchBar.java:122-132`）。
- 命中测试：`WidgetListBase.onMouseClicked` → `onMouseClickedSearchBar` → `widgetSearchBar.onMouseClickedImpl`
  ，全部用绝对坐标，重定位后自动正确（只要重建时坐标对）。
- 键盘：`GuiBase.keyPressed → onKeyTyped` → 屏幕级 `textFields`；`GuiListBase.onKeyTyped` 先试父类、再
  `getListWidget().onKeyTyped` → `WidgetListBase.onKeyTyped` → `onKeyTypedSearchBar`（搜索框 focus 时输入）→
  `allowKeyboardNavigation`（上下/PgUp/PgDn/Home/End 移动选择）。**这套路由与屏幕坐标无关，重定位不影响。**
- 焦点：`GuiTextFieldGeneric` 自己的 focus 状态；`WidgetListBase.onMouseClicked` 点击搜索框时 `searchBox.mouseClicked` 会设置
  focus。列表重建会丢失 focus（原版 Tab 切换同样丢），可接受。
- 滚动：`WidgetListBase.onMouseScrolled` 用 `posX/posY/browserWidth/browserHeight` 判断鼠标是否在列表上（
  `WidgetListBase.java:141-146`）——用新坐标后正确。

---

## 12. 风险清单（调查点 12）与当前草稿修正建议

### 12.1 通用风险（按严重度）

1. **【高】只注入 `GuiBase.init` TAIL 会漏 Tab 切换**：masa mod 的 Tab 监听器直接 `parent.initGui()`，不经过 `init()`。必须在
   `extractRenderState` HEAD 做 per-frame ensure（当前草稿已有，保留）。
2. **【高】列表重定位只 `setSize()` 不重建**：搜索框/图标位置与宽度不更新（`WidgetSearchBar` 无 setPosition，宽度构造时固定）→
   搜索框越界。必须重建 list widget，并让 `getBrowserWidth/Height` 在托管屏幕返回布局值（见 §6.2）。
3. **【中】`GuiModConfigs`（无 Tab、override `getBrowserHeight`）托管后**：不是错误，但 `getBrowserHeight` 注入对子类 override
   不生效 → 重建出的列表高度是 `height-70`（layout 期望 `height-84`），要到**同一帧稍后的 `extractRenderState` HEAD
   ensure** 才被 `setSize` 修正（在绘制前，因此不可见，只多一次 `initGui`）。详见 §6.3；推荐补一次尺寸复查消掉这次多余
   initGui。另：原本 `listY=0 / 无标题栏` 的设计会变成“46px 空 header 带”。
4. **【中】`malilib-ui.mixins.json` 与 `src` 文件必须严格一一对应**：当前（2026-11-19 检查）列出的
   `ButtonGenericMixin/GuiBaseMixin/GuiConfigsBaseMixin/GuiScrollBarMixin/MinecraftMixin/WidgetConfigOptionMixin/WidgetSearchBarMixin`
   与 6 个 accessor 均已有对应文件；但以后新增/删除 mixin 时漏改 json 会在启动时直接崩（`defaultRequire: 1` +
   类找不到）。建议实现后跑一次 dev 客户端确认 mixin 全部 apply。
5. **【高】共享源码引用 26.3 挪包类型**（`com.mojang.renderpearl.api.*` 或 `com.mojang.blaze3d.pipeline.RenderPipeline`）→
   26.1/26.2 编译失败。坚持只用 §9.3/§9.4 列出的 API。
6. **【中】窗口 resize 后列表坐标过期**：`posX/posY` final，必须按 §6.5 检测并重建。不需要 mixin `Minecraft`/`Window`。
7. **【中】小窗口回退会留下“半改造”状态**：当前 `ensureLayout` 在 `screenW < MIN_SCREEN_W || screenH < MIN_SCREEN_H` 时直接
   `STATES.remove(screen)`（见 `ConfigScreenManager.java:62-67` 附近）。但 Tab 按钮与列表在之前帧已被移到新布局坐标：
    - 若只是裁到很小（`Screen.resize → rebuildWidgets → init()` 会重建按钮，Tab 按钮回到原版位置），列表 widget 被保留且
      `posX/posY`（final）仍是新布局坐标，`setSize()` 用回原版尺寸 → **列表停留在“半改造”坐标**，与恢复的原版背景/标题叠在一起。
    - 修复建议（二选一）：(a) 小屏不放弃托管，改为 clamp 布局（`sidebarW` 下限 120、`listW` 下限 200），保持视觉一致；(b) 在
      `ConfigScreenState` 里保存首次改造前的 `originalListX/originalListY`，回退时恢复 `listX/listY` +
      `reCreateListWidget()` + 恢复原尺寸。当前代码没有原值备份，需补。
8. **【中】误识别非 Tab 按钮**：若某 mod 在 `super.initGui()` 后还加了顶部按钮（例如自定义“重置全部”），仅按 y/height 会被当
   Tab 搬走。用“super 之后新增 + 同 y/同高 + 全部满足”的启发式，并打日志；`captureTabs.size() != newlyAdded.size()`
   时不托管（宁可原版）。
9. **【中】其它 mod 的 mixin 冲突**：`@Redirect`（drawButtons）比 `@Inject` 更容易和其它重定向打架；priority 显式 `1000`，避免
   `@Overwrite`。malilib 自身不 mixin 这些 GUI 类，但其它 UI mod 可能。实现后建议 `-Dmixin.debug.countInjections=true`
   跑一次，确认全部命中。
10. **【中】`ButtonGenericMixin` 依赖两个“非公开 API 名”**：`@Inject(method="drawBackground")`（`private` 方法，
    `ButtonGeneric.java:105`）与 `@Shadow protected boolean renderDefaultBackground`（`ButtonGeneric.java:17`
    ）。0.28.9/0.29.3/0.30.1 三版本一致（0.30.1 与 0.30.2 该类逐字节同），但这是 malilib 内部实现细节，升级到 0.31+
    时要优先复核这两个名字。
11. **【低】`WidgetSearchBarMixin` 的“整条点击展开”只 setSearchOpen，不转发 click 给内部 textField**：`setSearchOpen(true)`
    内部只做 `searchBox.setFocused(true)`；首次点击展开后光标位置/选择范围可能不在点击处，需要再点一次才精确定位（小 UX
    问题，不影响功能）。若在意，可改成 `bar.setSearchOpen(true); bar.searchBox.onClick(click, false);`（需要
    `@Accessor("searchBox")`）。
12. **【低】内容区标题在“未识别到 Tab”时为空**：`drawContentHeader` 用 `activeTabLabel(state)`（遍历 navButtons 找 disabled
    项）；如果某 mod 的 Tab 未能被识别（或 RENDER_LAYERS 分支提前返回导致 navButtons 为空），header 会只剩一条横线。建议
    fallback 到 `screen.getTitleString()`（public，返回翻译后的标题，可能含版本号）。
13. **【低】mod 切换下拉的内部搜索框位置**：`WidgetDropDownList.setPosition(x,y)` 会把筛选输入框画在 `y-18`。当前
    `ensureModSwitcher` 用 `y = contentY + 18`，则筛选框在 `contentY`（面板上沿）。想让它完全在面板内，取
    `y >= contentY + 26`（下拉框底 `contentY+44` 仍 < 列表顶 `contentY+46`）。只在输入过滤文本时可见，属外观细节。
14. **【低】`MinecraftMixin` 的 `tick()V`**：已核对 26.1/26.2/26.3 三个 MC jar 都是 `public void tick();`，签名稳定；它只服务
    dev 自动测试钩子（`DevAutoTest`），不影响正式功能。若最终发布不想带 dev 钩子，可把该 mixin 与 `dev/` 包一起删除并同步
    `malilib-ui.mixins.json`。
10. **【中】额外 `initGui()/reCreateListWidget()` 的副作用**：`WidgetListConfigOptionsBase.reCreateListEntryWidgets()` 会
    `applyPendingModifications()`（把文本框里的未提交值写回 config、置 `configsModified`）；我们在 resize/首帧/布局变化时重建会
    **提前应用**（原版只在关闭屏幕/Tab 切换时应用）。通常无害，但如果配置值“输入中未按回车”就被应用，属于可见行为变化；可接受（原版
    Tab 切换也会 apply）。
11. **【中】滚动位置**：`reCreateListWidget` 会新建 `GuiScrollBar`（value=0），我们 ensure 重建后滚动位置回到顶部；Tab 切换原版会
    `resetScrollbarPosition()`（本来就回顶），resize 时回顶是轻微体验损失。若要保留，重建后把旧 `scrollBar.getValue()`
    写回（accessor）。
12. **【低】当前 Tab 判定依赖 `enabled=false` 语义**：三样本一致；若未来 mod 用别的标记，会丢失选中高亮（点击仍可用）。用
    `!enabled` 兜底 + 日志。
13. **【低】语言切换**：Tab 文案在 `ButtonGeneric` 创建时 `StringUtils.translate` 固定（原版行为），语言切换要重开屏幕。
14. **【低】litematica RENDER_LAYERS Tab** 打开 `GuiRenderLayer`（`GuiBase` 而非 `GuiConfigsBase`）→ 不套新样式；返回时重新打开
    `GuiConfigs`。若要求全覆盖，另立任务（`GuiRenderLayerEditBase` 也有 y=26 的 Tab）。
15. **【低】其它 `GuiListBase` 屏幕**（litematica 的 Scheduler/材料列表、malilib 的文件浏览器等）：如果我们在 `GuiListBase`/
    `WidgetListBase` 上做未门控的注入（行背景、列表底板、滚动条），会意外影响这些屏幕。所有注入都要
    `screen instanceof GuiConfigsBase` + 托管状态门控。
16. **【低】mod 切换下拉遮住 header/标题**：默认位置在屏幕右上 `(w-155,6)`，与我们的 content header 重叠；必须 `setPosition`
    （或隐藏自绘）。其内部搜索框固定 `y-18`，位置别放太靠上。
17. **【低】tab 按钮 hover 状态**：自绘渲染跳过 `ButtonGeneric.render` 后 `button.hovered` 不再更新；原版
    `drawButtonHoverTexts` 依赖它（Tab 无 hover 文案，实际无影响）。
18. **【低】`GuiConfigsBase.removed()`**：屏幕关闭时 `applyPendingModifications()`；若 `getListWidget()` 为 null 会直接
    return（源码已判空）。
19. **【低】性能**：ensure 每帧跑一次（render HEAD），只是遍历十几个按钮+比较坐标，可忽略；但不要在每帧都 `reCreateListWidget`（用
    pos/size 比较短路）。
20. **【低】`WidgetConfigOption.render` 的 subWidgets 二次绘制**是原版行为，注入行背景不会改变绘制顺序，但如果你在 HEAD
    里依赖“本次 render 只画一次”做状态累加，会算两次。

### 12.2 对当前实现的复核结论（2026-11-19 收尾时）

> 收尾时已确认的当前实现状态：26.3 build 通过、真实客户端截图验证（左侧 TAB + 右侧选项 + 深色主题生效）；
> `getBrowserWidth/getBrowserHeight` RETURN 注入、`GuiModConfigs` 托管、mixin 文件齐备均已落地。以下只列**仍需注意的遗留项**。

1. **【建议，低风险】`ensureList` 重建分支后补一次尺寸复查**（§6.3 代码）：对 `GuiModConfigs` 这类 override
   `getBrowserHeight()` 的子类，当前是靠下一次 `extractRenderState` HEAD 的 ensure 才修正尺寸（发生在同一帧绘制前，
   **不可见**，但多一次 `initGui` 开销）。补上复查可让同一次 ensure 内收敛，并让“任何 getBrowser*
   override”确定性正确。当前实现不改也能正常工作。
2. **【建议，低风险】小屏回退的“半改造”状态**（§12.1-7）：`STATES.remove` 后 Tab 按钮/列表可能仍停在改造后的坐标。要么保留托管并
   clamp 布局，要么保存原始 `listX/listY` 并回退时恢复。当前 `MIN_SCREEN_W=420/MIN_SCREEN_H=280` 触发概率低（正常窗口/GUI
   scale 不会到），但发生时会明显错位。
3. **【建议，低风险】内容区标题 fallback**：`activeTabLabel` 找不到选中项时用 `screen.getTitleString()` 兜底，避免空 header。
4. **【可选】`ensureModSwitcher` 的 y**：当前 `contentY+18` 会让下拉内部筛选框落在 `contentY`（面板上沿，仅过滤时可见）；改
   `contentY+26` 可完全收进面板（§12.1-13）。
5. **【可选】`WidgetSearchBarMixin` 展开点击转发**：首次点击只 focus 不定位光标；需要时
   `bar.searchBox.onClick(click, false)`（§12.1-11）。
6. **【保留】`isTabButton` 纯坐标启发式**：litematica/tweakeroo/malilib 已验证通过；minihud/itemscroller 未验证（本机无
   jar/源码）。若后续遇到误判，再补“`super.initGui()` 之后新增按钮”的 marker 方案（§4.3），无需现在改。
7. **【保留】`GuiModConfigs` 托管**：无持久错位，视觉上多一条空 header 带（原本 `listY=0`）；这是“统一视觉”的取舍，已确认可接受。
8. **【性能备注】** `ensureLayout` 每帧 `new ConfigLayout` 一次 + 遍历十几个按钮，可忽略；`WeakHashMap<Screen,State>`
   在屏幕关闭后由 GC 回收，无泄漏。若想省一点，可按 `(screenW,screenH,tabCount)` 缓存 layout 对象。

---

## 13. 实现清单（可直接照写）

### 13.1 Mixin 文件清单（建议）

| 文件                                         | 目标                 | 内容                                                                                                                                                                            |
|----------------------------------------------|----------------------|---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `mixin/GuiBaseMixin.java`                    | `GuiBase`            | `init` TAIL（ensure/capture）、`extractRenderState` HEAD（ensure）、`drawScreenBackground` HEAD cancel（自绘面板）、`drawTitle` HEAD cancel、`drawButtons` Redirect（导航自绘） |
| `mixin/GuiConfigsBaseMixin.java`             | `GuiConfigsBase`     | `getBrowserWidth/getBrowserHeight` RETURN（布局值）、`initGui` TAIL（button marker）、`buildConfigSwitcher` TAIL（挪 mod 下拉/改样式）                                          |
| `mixin/GuiListBaseMixin.java`（可选）        | `GuiListBase`        | `drawContents` HEAD（列表面板底/scissor）                                                                                                                                       |
| `mixin/WidgetConfigOptionMixin.java`         | `WidgetConfigOption` | `render` HEAD（行背景）                                                                                                                                                         |
| `mixin/GuiScrollBarMixin.java`               | `GuiScrollBar`       | `render` HEAD（颜色）                                                                                                                                                           |
| `mixin/WidgetDropDownListMixin.java`（可选） | `WidgetDropDownList` | `render` HEAD cancel（自绘 mod 选择器外框）                                                                                                                                     |
| `mixin/accessor/GuiBaseAccessor.java`        | `GuiBase`            | `buttons`、`widgets`                                                                                                                                                            |
| `mixin/accessor/GuiConfigsBaseAccessor.java` | `GuiConfigsBase`     | `modSwitchWidget`                                                                                                                                                               |
| `mixin/accessor/GuiListBaseAccessor.java`    | `GuiListBase`        | `listX/listY` 读写、`widget`、`reCreateListWidget`、`getListWidget`                                                                                                             |
| `mixin/accessor/WidgetListBaseAccessor.java` | `WidgetListBase`     | `posX/posY/browserWidth/browserHeight`（可选 `widgetSearchBar`）                                                                                                                |
| `mixin/accessor/ButtonBaseAccessor.java`     | `ButtonBase`         | `displayString/enabled/actionListener`                                                                                                                                          |
| `mixin/accessor/GuiScrollBarAccessor.java`   | `GuiScrollBar`       | `backgroundColor/foregroundColor`                                                                                                                                               |

`malilib-ui.mixins.json` 的 `client` 数组必须与上述文件一一对应；`compatibilityLevel` 用 `${compatibility_level}`（
`buildSrc` 对 26.x 展开为 `JAVA_25`）；`package` 为 `com.github.bunnyi116.malilibui.mixin`。

### 13.2 核心流程伪代码

```java
// GuiBase.init TAIL 与 extractRenderState HEAD 都调：
public static void ensureLayout(GuiBase screen) {
    if (!(screen instanceof GuiConfigsBase cfg)) return;
    int w = screen.getScreenWidth(), h = screen.getScreenHeight();
    if (w < MIN_W || h < MIN_H) {
        STATES.remove(screen);
        return;
    }

    State state = STATES.computeIfAbsent(screen, s -> new State((GuiConfigsBase) s));
    List<ButtonBase> buttons = ((GuiBaseAccessor) screen).malilibui$getButtons();

    // 1) 失效检测（Tab 切换后旧按钮不在列表里）
    if (!state.navButtons.isEmpty() && !buttons.containsAll(state.navButtons)) state.navButtons.clear();
    // 2) 捕获（首次 / 重建后）
    if (state.navButtons.isEmpty()) {
        List<ButtonBase> added = state.marker >= 0 && buttons.size() > state.marker
                ? buttons.subList(state.marker, buttons.size()) : buttons;
        for (ButtonBase b : added)
            if (isTabLike(b)) state.navButtons.add(b);
        if (state.navButtons.isEmpty()) {
            STATES.remove(screen);
            return;
        }   // 无 Tab：不托管
        state.navButtons.sort(Comparator.comparingInt(ButtonBase::getX));   // 保证顺序稳定
    }
    // 3) 布局
    state.layout = ConfigLayout.compute(w, h, state.navButtons.size());
    // 4) 搬按钮
    for (int i = 0; i < state.navButtons.size(); i++) { ...setPosition / setWidth / setHeight ...}
    // 5) 列表
    ensureList(cfg, state);
    // 6) 下拉
    ensureModSwitcher(cfg, state);
    state.ready = true;
}

private static void ensureList(GuiConfigsBase cfg, State state) {
    ConfigLayout l = state.layout;
    GuiListBaseAccessor la = (GuiListBaseAccessor) cfg;
    WidgetListBase<?, ?> list = la.malilibui$getListWidget();
    if (list == null) return;
    WidgetListBaseAccessor wa = (WidgetListBaseAccessor) list;
    if (wa.malilibui$getListPosX() != l.listX || wa.malilibui$getListPosY() != l.listY) {
        la.malilibui$setListX(l.listX);
        la.malilibui$setListY(l.listY);
        la.malilibui$reCreateListWidget();           // ← 构造时 getBrowserWidth/Height 已返回布局值
        list = la.malilibui$getListWidget();
        if (list == null) return;
        wa = (WidgetListBaseAccessor) list;
    }
    if (wa.malilibui$getBrowserWidth() != l.listW || wa.malilibui$getBrowserHeight() != l.listH) {
        list.setSize(l.listW, l.listH);
        list.initGui();
    }
    state.listWidget = list;
}
```

`getBrowserWidth/Height` 的注入实现：

```java
public static Integer listWidthFor(GuiConfigsBase cfg) {
    State s = STATES.get(cfg);
    return s != null && s.ready && s.layout != null ? s.layout.listW : null;
}
```

> 注意：`listWidthFor` 依赖 `state.ready`，而首次 `super.initGui()` 时 `ready=false` → 返回 null（原版宽度）；ensure 里重建时
> `ready` 已先置 true（或至少在调用 `reCreateListWidget` 前置 true）。若不想引入 ready 的时序坑，用
> `layout != null && layout.screenW == cfg.getScreenWidth()` 判断即可。

---

## 14. 运行时验证建议（非本报告执行范围）

- 项目已有 `scripts/verify-gui.ps1`（启动 `:26.3:runClient`，通过 `MALILIB_UI_AUTOTEST` 钩子自动打开 litematica 配置屏并截图到
  `run/client/screenshots/`），实现完成后用它验证三件事：
    1. litematica / tweakeroo 配置屏：左侧导航、右侧列表、搜索框不越界；
    2. Tab 切换（点击导航）后新列表位置/宽度正确，当前 Tab 高亮切换；
    3. 窗口缩放后布局不塌（列表与面板对齐）。
- 建议同时在 dev 环境里用 `-Dmixin.debug.countInjections=true` 一次，确认所有注入命中（`defaultRequire: 1` 已保证不静默失败）。

---

## 附录 A：本报告使用的 javap 命令

```powershell
$javap='C:\Users\11607\.gradle\jdks\eclipse_adoptium-25-amd64-windows.2\bin\javap.exe'
$j='C:\Users\11607\.gradle\caches\modules-2\files-2.1\maven.modrinth\malilib\0.30.1\a43a41725bf86e2aafd600c3088d68c2231ff195\malilib-0.30.1.jar'
& $javap -p -classpath $j fi.dy.masa.malilib.gui.GuiBase
& $javap -p -c -classpath $j fi.dy.masa.malilib.gui.GuiConfigsBase
# MC 侧：
$mc='C:\Users\11607\.gradle\caches\fabric-loom\26.3\minecraft-client.jar'
& $javap -p -c -classpath $mc net.minecraft.client.gui.screens.Screen
& $javap -p -c -classpath $mc net.minecraft.client.gui.screens.GuiGraphicsExtractor   # 真实类名 net.minecraft.client.gui.GuiGraphicsExtractor
```

三版本对比时把同一命令的 `-classpath` 换成对应 jar，输出到文件后用 `Compare-Object`/diff（注意先剔除 `#123`
形式的常量池编号，否则全是噪声）。

## 附录 B：关键源码位置（malilib 0.30.2 = 0.30.1 GUI 行为）

| 内容                                                                                          | 位置                                                                                                                                                                                         |
|-----------------------------------------------------------------------------------------------|----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `GuiBase.init/initGui/clearElements`                                                          | `gui/GuiBase.java:154-166, 737-763`                                                                                                                                                          |
| `GuiBase.extractRenderState` / 绘制顺序                                                       | `gui/GuiBase.java:198-217`                                                                                                                                                                   |
| `GuiBase.drawScreenBackground`                                                                | `gui/GuiBase.java:771-775`                                                                                                                                                                   |
| `GuiBase.drawButtons/drawWidgets/drawHoveredWidget`                                           | `gui/GuiBase.java:808-882`                                                                                                                                                                   |
| `GuiBase.addButton/addWidget/addTextField`                                                    | `gui/GuiBase.java:663-703`                                                                                                                                                                   |
| `GuiBase.onMouseClicked/onKeyTyped`                                                           | `gui/GuiBase.java:337-385, 484-587`                                                                                                                                                          |
| `GuiBase.openGui/isMouseOver/closeGui`                                                        | `gui/GuiBase.java:916-919, 884-887, 168-178`                                                                                                                                                 |
| `GuiListBase` 全部                                                                            | `gui/GuiListBase.java`（listX/Y 私有、initGui 70-80、reCreateListWidget 65-68、resize 161-170、drawContents 172-179）                                                                        |
| `GuiConfigsBase.initGui/buildConfigSwitcher/getBrowser*/createListWidget/removed`             | `gui/GuiConfigsBase.java:54-124, 173-190`                                                                                                                                                    |
| `GuiConfigsBase.ConfigOptionWrapper`                                                          | `gui/GuiConfigsBase.java:307-364`                                                                                                                                                            |
| `WidgetListBase.setSize/drawContents/reCreateListEntryWidgets/refreshEntries/onMouseScrolled` | `gui/widgets/WidgetListBase.java:435-446, 384-433, 453-492, 524-527, 130-149`                                                                                                                |
| `WidgetListConfigOptions` 构造器/搜索框                                                       | `gui/widgets/WidgetListConfigOptions.java:22-40`                                                                                                                                             |
| `WidgetSearchBar(Configs)`                                                                    | `gui/widgets/WidgetSearchBar.java:20-33, 122-132`；`WidgetSearchBarConfigs.java:23-31`                                                                                                       |
| `WidgetConfigOption` 行布局/渲染                                                              | `gui/widgets/WidgetConfigOption.java:40-98, 100-294, 476-488`                                                                                                                                |
| `WidgetConfigOptionBase.createResetButton`                                                    | `gui/widgets/WidgetConfigOptionBase.java:71-78`                                                                                                                                              |
| `ButtonBase` 字段/点击                                                                        | `gui/button/ButtonBase.java:29-36, 67-70, 82-93, 102-106`                                                                                                                                    |
| `ButtonGeneric.render/drawText`                                                               | `gui/button/ButtonGeneric.java:90-161`                                                                                                                                                       |
| `WidgetDropDownList`                                                                          | `gui/widgets/WidgetDropDownList.java:49-80, 82-89, 267-367`                                                                                                                                  |
| `GuiScrollBar.render`                                                                         | `gui/GuiScrollBar.java:76-109`                                                                                                                                                               |
| `GuiContext`                                                                                  | `render/GuiContext.java`（fromGuiGraphics 61-78、绘制包装 272-355）                                                                                                                          |
| `RenderUtils`（GuiContext 重载）                                                              | `render/RenderUtils.java:151-193（outline/outlinedBox）、316-343（drawRect）、430-488（drawTexturedRect）、618-694（drawHoverText）、746-756（drawGradientRectBatched）、831-850（横/竖线）` |
| `MaLiLibBasicRectGuiElement`/`MaLiLibGradientRectGuiElement`/`MaLiLibTexturedGuiElement`      | `render/element/`                                                                                                                                                                            |
| malilib 自身 mixin 清单                                                                       | `src/main/resources/mixins.malilib.json`                                                                                                                                                     |
| malilib accesswidener                                                                         | `src/main/resources/malilib.accesswidener`                                                                                                                                                   |
| mod 菜单入口                                                                                  | `compat/modmenu/ModMenuImpl.java`；litematica/tweakeroo 各自 `compat.modmenu.ModMenuImpl`（jar 内）                                                                                          |
| 配置屏注册表                                                                                  | `gui/config/registry/ConfigScreenRegistry.java`；`util/data/ModInfo.java`                                                                                                                    |
| `GuiModConfigs`（无 Tab 特例）                                                                | `config/gui/GuiModConfigs.java:16-37`                                                                                                                                                        |
| litematica `GuiConfigs`                                                                       | `D:\source\masa\litematica@sakura-ryoko\src\main\java\fi\dy\masa\litematica\gui\GuiConfigs.java:22-224`                                                                                      |
| `MaLiLibConfigGui`                                                                            | `D:\source\masa\malilib@sakura-ryoko\src\main\java\fi\dy\masa\malilib\MaLiLibConfigGui.java`                                                                                                 |

## 附录 C：javap 关键反汇编片段

1) `GuiBase.init()`（4 个 jar 结构一致）：

```
public void init();
  Code:
     0: aload_0
     1: invokespecial Screen.init:()V
     4: aload_0
     5: invokevirtual GuiBase.initGui:()V
     8: aload_0
     9: invokestatic  System.nanoTime:()J
    12: putfield      GuiBase.openTime:J
    15: return
```

2) `GuiConfigsBase.createListWidget(int,int)`：

```
new WidgetListConfigOptions
  iload_1 (listX); iload_2 (listY)
  aload_0; invokevirtual getBrowserWidth:()I
  aload_0; invokevirtual getBrowserHeight:()I
  aload_0; invokevirtual getConfigWidth:()I
  fconst_0 (zLevel=0f)
  aload_0; invokevirtual useKeybindSearch:()Z
  aload_0 (parent)
  invokespecial WidgetListConfigOptions.<init>(IIIIIFZLfi/dy/masa/malilib/gui/GuiConfigsBase;)V
```

3) tweakeroo 0.29.2 `GuiConfigs.initGui` 顶部（证明与 litematica 同构）：

```
aload_0; invokespecial GuiConfigsBase.initGui:()V
aload_0; invokevirtual clearOptions:()V
bipush 10; istore_1          // x = 10
bipush 26; istore_2          // y = 26
invokestatic ConfigGuiTab.values:()[...
for ( ... ) {
    invokevirtual useAllTab:()Z ...
    iload_1; aload_0; iload_1; iload_2; iconst_m1; aload tab
    invokevirtual createButton:(IIILConfigGuiTab;)I
    iadd; istore_1
}
```

4) `WidgetListConfigOptions` 构造器里的搜索框（决定“必须重建列表”的原因）：

```
new WidgetSearchBarConfigs / WidgetSearchBar
  iload_1; iconst_2; iadd        // x + 2
  iload_2; iconst_4; iadd        // y + 4
  iload_3; bipush 14; isub       // width - 14
  ...
browserEntriesOffsetY = 23 (keybind search) / 17 (plain)
```

5) `Screen.init(int,int)`（26.3；26.1/26.2 字节码一致）：

```
width = w; height = h;
if (!initialized) { this.init(); this.setInitialFocus(); } else this.repositionElements();
initialized = true;
```

---

## 附录 D：v0.28.9 → v0.30.1 差异清单（GUI 相关，按 Lead 要求单独汇总）

> 方法：`javap -p` 签名集合 diff + `javap -c` 目标方法体 diff（剔除常量池编号）+ class 文件 SHA-256（0.30.1 与 0.30.2
> 逐字节相同）。结论见 §10：**GUI 类同构，一套 mixin 通用**。

### D.1 我们直接注入/反射的类：三版本差异 = 无（除下列新增方法）

| 类                                                                      | 0.28.9 → 0.30.1 差异                                                                                                                                                                                                                                                                                                                                                                                                                                               | 影响我们的注入点？                                                                                                           |
|-------------------------------------------------------------------------|--------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|------------------------------------------------------------------------------------------------------------------------------|
| `fi.dy.masa.malilib.gui.GuiBase`                                        | 新增 `public void onFilesDrop(List<Path>)`、`public boolean onMouseDropFiles(List<Path>)`（0.30.1）。其余字段/方法全同。                                                                                                                                                                                                                                                                                                                                           | 否（不注入这两个方法）                                                                                                       |
| `fi.dy.masa.malilib.gui.GuiListBase`                                    | 无                                                                                                                                                                                                                                                                                                                                                                                                                                                                 | 否                                                                                                                           |
| `fi.dy.masa.malilib.gui.GuiConfigsBase`                                 | 0.28.9 有编译器合成 `access$000(GuiConfigsBase)Minecraft`、`access$100`（匿名下拉类访问 outer）；0.30.1 没有（改为 record/lambda 形态？）。签名集合其余全同。                                                                                                                                                                                                                                                                                                      | 否（不注入合成方法）                                                                                                         |
| `gui.widgets.WidgetListBase`                                            | 无                                                                                                                                                                                                                                                                                                                                                                                                                                                                 | 否                                                                                                                           |
| `gui.widgets.WidgetListConfigOptions`                                   | 无（0.30.1/0.30.2 同名类逐字节相同）                                                                                                                                                                                                                                                                                                                                                                                                                               | 否                                                                                                                           |
| `gui.widgets.WidgetListConfigOptionsBase`                               | 无                                                                                                                                                                                                                                                                                                                                                                                                                                                                 | 否                                                                                                                           |
| `gui.widgets.WidgetConfigOption`                                        | 新增 `protected void addConfigComment(int,int,int,int,MutableComponent)`（0.30.1）                                                                                                                                                                                                                                                                                                                                                                                 | 否（只注入 `render`）                                                                                                        |
| `gui.widgets.WidgetConfigOptionBase` / `WidgetBase` / `WidgetContainer` | 无                                                                                                                                                                                                                                                                                                                                                                                                                                                                 | 否                                                                                                                           |
| `gui.widgets.WidgetSearchBar` / `WidgetSearchBarConfigs`                | 无                                                                                                                                                                                                                                                                                                                                                                                                                                                                 | 否（注入 `render` HEAD 稳定）                                                                                                |
| `gui.widgets.WidgetDropDownList` / `WidgetLabel`                        | 无（`WidgetDropDownList` 四个 jar 逐字节相同）                                                                                                                                                                                                                                                                                                                                                                                                                     | 否                                                                                                                           |
| `gui.button.ButtonBase` / `ButtonGeneric`                               | 无                                                                                                                                                                                                                                                                                                                                                                                                                                                                 | 否（`drawBackground`/`renderDefaultBackground` 名字三版本一致）                                                              |
| `gui.GuiScrollBar`                                                      | 无（四个 jar 逐字节相同）                                                                                                                                                                                                                                                                                                                                                                                                                                          | 否                                                                                                                           |
| `render.GuiContext`                                                     | ① `bindTexture(Identifier)` 返回 `Pair<GpuTextureView,GpuSampler>` 的包名：0.28.9 `com.mojang.blaze3d.textures` → 0.30.1 `com.mojang.renderpearl.api.textures`；② 同理 `setupTexture(Pair)`；③ `popScissor()`：0.28.9 返回 `ScreenRectangle` → 0.30.1 `void`；④ 0.30.1 新增 `renderTooltip(..., ClientTooltipPositioner, Identifier, boolean)`。其余（`drawString/centeredText/text/fontRenderer/pose/getGuiGraphics/nextStratum/elementUp/enableScissor...`）同。 | 只调用 `drawString/fontRenderer/pose` 等公共项 → 安全；**不要写 `Pair<GpuTextureView,...>` 类型**；`popScissor()` 当语句调用 |
| `render.RenderUtils`                                                    | 面板用的一批（§9.4）全部同签名存在；差异集中在：`drawHoverText(...,Component)` 新增（0.30.1）、`drawTexturedRectBatched(GuiContext,Pair,…)` 的 Pair 包名变化、`drawTextPlate(...)` 系列签名变化、`registerSpecialGuiRenderers` 参数变化、`mainTarget()/fb()`/`nodes()` 等非 GUI 方法变化                                                                                                                                                                           | 只用 §9.4 列出的安全子集 → 无影响                                                                                            |
| `render.element.MaLiLib*GuiElement` record                              | 字段/构造器含 `RenderPipeline`（26.3 挪包），record 本身三版本都是同一个 malilib 版本各自的类型                                                                                                                                                                                                                                                                                                                                                                    | **不要在共享源码引用**（§9.4）                                                                                               |

### D.2 非 GUI 类差异（与本次改造无关，但可解释 jar 间 709 vs 748 个 class 的差别）

0.30.1 相对 0.28.9 新增/重命名了一批非 GUI 类，例如：

- 新增配置类型：`config/IConfigBlockStateList`、`config/options/ConfigBlockStateList`、
  `gui/button/ConfigButtonBlockStateList`、`gui/GuiBlockStateListEdit`、`gui/widgets/WidgetListBlockStateListEdit(Entry)`；
- 新增 `gui/GuiConfirmFileDrop`、`gui/widgets/WidgetHoverComponent`、`gui/widgets/WidgetInfoIconComponent`、
  `compat/sodium/SodiumCompat`、`render/uniform/ChunkFixUniform`；
- 包移动：`util/FileCopier`→`util/file_ops/FileCopier` 等、`util/input/*` 大改（`KeyCodes`/`ScanCodes`/`ActionCodes`）、
  `util/LayerRange`→`util/position/LayerRange`、`util/InputUtils`、`util/KeyCodes`、`util/IntBoundingBox`、
  `util/SubChunkPos` 等；
- mixin 清单变化：`mixin/render/*`、`mixin/screen/*`、`mixin/network/*` 等（`mixins.malilib.json` 内）。

这些都不在 `malilib-ui` 的注入面内；`mapping-26.1.2-26.2.txt` 只覆盖了 `LayerRange` / `IntBoundingBox` / `WorldUtils`
等少量名字，进一步说明 GUI 面无需映射。

### D.3 若未来升级到 malilib 0.31+ 的复核清单

1. `GuiBase.init` / `extractRenderState` 的调用序列是否变化（最影响布局时机）；
2. `GuiConfigsBase.initGui` → `GuiListBase.initGui` → `createListWidget` 的调用关系是否变化；
3. `GuiListBase.listX/listY/widget` 字段名与可见性、`reCreateListWidget()/getListWidget()` 是否仍在；
4. `WidgetListBase.posX/posY/browserWidth/browserHeight` 字段名；`WidgetListConfigOptions`
   构造器参数顺序（x,y,w,h,configWidth,zLevel,useKeybindSearch,parent）；
5. `WidgetSearchBar` 构造器与 `searchBox/iconSearch` 字段；
6. `WidgetConfigOption.render`、`WidgetDropDownList.render/setPosition`、`GuiScrollBar.render` 签名；
7. `ButtonBase` 字段名（`displayString/enabled`）、`ButtonGeneric.renderDefaultBackground/drawBackground`；
8. `getBrowserWidth/getBrowserHeight` 是否仍是非 final 可注入（若被改 final/内联则方案要换）；
9. `RenderUtils` 的 §9.4 安全子集是否仍在（尤其 `drawGradientRectBatched(GuiContext,...)`）；
10. MC 侧的 `Screen.init(int,int)/resize/repositionElements` 与 `GuiGraphicsExtractor` 的
    `fill/fillGradient/text/blit/blitSprite/enableScissor` 是否仍存在（26.x 内已验证一致）。

---

*报告结束。实现中如发现字节码/签名与本文不符（例如 malilib 升级到 0.31），请按 §10.2 / 附录 D.3 的表逐项复核，并优先以
`javap` 重新对比为准确认。*
