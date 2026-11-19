# Changelog

所有显著的变更都会记录在这个文件中，遵循 [Keep a Changelog](https://keepachangelog.com/zh-CN/1.0.0/) 规范。
本项目的版本号遵循 [语义化版本](https://semver.org/lang/zh-CN/)。

### v0.1.0

#### 2026-11-19

首个版本：masa 系列配置界面重制。

- 项目由 litematica-printer 骨架改造为全新的 **malilib-ui** 项目：
  - mod id 为 `malilib-ui`，包前缀为 `com.github.bunnyi116.malilibui`；
  - 移除 litematica-printer 的全部业务源码、mixin、语言文件与图标；
  - 移除 Litematica / Tweakeroo / Quick Shulker / Chest Tracker 等旧依赖；
  - 清理构建配置中残留的旧依赖属性（`lombok`、`fabric_version`、`modmenu`、
    `quickshulker`、`chesttracker`、`whereisit`、`jackfredlib`、`searchables`、`yacl`
    等）以及旧版本工程目录。
- 构建目标收敛为 Minecraft 26.1 / 26.2 / 26.3。
- 配置界面重制（基于对 MaLiLib 0.28.9 / 0.29.3 / 0.30.1 的 Mixin 注入，一套实现通用）：
  - 左侧垂直 TAB 导航：迁移各 masa 模组原有的顶部 TAB 按钮，点击行为完全保留；
  - 右侧配置选项列表：重定位列表、搜索栏、滚动条到内容区，支持搜索过滤；
  - 深色渐变主面板（半透明，可透出游戏画面）、面板阴影与描边、侧栏分隔、内容区标题强调条；
  - 配置行内按钮 / 控件保持原版 masa 风格（原版浅灰纹理，禁用态区分明显）；
  - 配置行交替背景、整行悬停高亮、分组标题样式；
  - 主题化滚动条颜色；
  - 搜索栏背景绘制、整条点击展开、搜索图标与文本框垂直居中；
  - mod 切换下拉迁移到内容区标题行右侧，展开时置顶显示；
  - 配置相关对话框标题显示翻译后的配置名（快捷键高级设置、StringList / LockedList /
    ColorList / Table 编辑）；
  - 窗口缩放自适应，小窗口自动回退原版布局。
- 本模组自身配置：
  - 配置界面注册到 malilib 的配置界面注册表，可从任意 masa 配置界面右上角的
    mod 切换下拉框进入；
  - 配置项：启用界面重制、半透明背景、侧栏宽度；保存到 `config/malilib-ui.json`。
- 多版本整合包（fabricWrapper）与 GitHub Actions 构建流程改为 malilib-ui 版本。
- 开发工具：
  - `MALILIB_UI_AUTOTEST` 开发钩子：客户端到标题屏后自动打开指定 masa 模组的配置界面；
  - `scripts/verify-gui.ps1`：一键构建、启动、截图、清理的视觉验证脚本；
  - `docs/research/malilib-gui.md`：malilib GUI 结构、注入点与三版本差异调研报告。
