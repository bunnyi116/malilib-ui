# malilib-ui 视觉验证脚本

本目录存放用于验证 malilib-ui 渲染效果的可重复脚本。

## verify-gui.ps1

启动 Minecraft 26.x 开发客户端，等待 malilib-ui 的 dev 自动测试钩子
（`DevAutoTest`，环境变量 `MALILIB_UI_AUTOTEST=litematica`）打开 litematica
配置界面，然后截取游戏窗口截图，最后关闭客户端。

### 用法

```powershell
# 默认：26.3 客户端
pwsh -NoProfile -File .\scripts\verify-gui.ps1

# 也可用 Windows PowerShell 5.1
powershell -ExecutionPolicy Bypass -File .\scripts\verify-gui.ps1

# 指定版本 / 调整等待时间
pwsh -NoProfile -File .\scripts\verify-gui.ps1 -Version 26.3 -TimeoutSeconds 1200 -SettleSeconds 6

# 截图后不关闭客户端（手动检查用，结束后请自行结束 java 进程）
pwsh -NoProfile -File .\scripts\verify-gui.ps1 -KeepRunning
```

### 产物

| 路径                                                      | 说明                                                       |
|-----------------------------------------------------------|------------------------------------------------------------|
| `run/client/autotest.log`                                 | Gradle + 客户端 stdout 日志                                |
| `run/client/autotest.err.log`                             | Gradle stderr 日志                                         |
| `run/client/logs/latest.log`                              | Minecraft 自身日志（含 `[autotest]` 行）                   |
| `run/client/screenshots/autotest-<yyyyMMdd-HHmmss>.png`   | 第 1 张截图：配置界面（成功）或标题屏（litematica 未加载） |
| `run/client/screenshots/autotest-<yyyyMMdd-HHmmss>-2.png` | 第 2 张截图（间隔 3 秒连拍）                               |
| `run/client/screenshots/autotest-<yyyyMMdd-HHmmss>-3.png` | 第 3 张截图（间隔 3 秒连拍）                               |
| `run/client/autotest-report.txt`                          | 本次运行的摘要报告（窗口信息、截图、图像统计、日志关键行） |

> 连拍期间脚本不移动鼠标，所以画面静止时 3 张截图的 SHA-256 通常完全相同；
> 这可以作为"渲染帧稳定、无闪烁/无撕裂"的证据。

### 退出码

| 退出码 | 含义                                                                                               |
|--------|----------------------------------------------------------------------------------------------------|
| 0      | 成功打开配置界面并截图（`SUCCESS_CONFIG_SCREEN`）                                                  |
| 2      | 客户端窗口已截图，但没有配置界面（`PARTIAL_TITLE_SCREEN_ONLY`，通常是 litematica 未加入 dev 依赖） |
| 1      | 失败（未出现窗口 / 崩溃 / 超时），查看日志与报告                                                   |

### 实现要点

- 通过 `cmd.exe /d /c` 启动 `gradlew.bat` 以支持 stdout/stderr 重定向
  （`Start-Process` 无法直接重定向 `.bat` 的输出）。
- 默认加 `--no-daemon`：单次构建 JVM 继承本脚本的环境变量，保证
  `MALILIB_UI_AUTOTEST` 能传递到 Minecraft 客户端（复用的 Gradle daemon
  不会继承后来设置的环境变量）。若确定 daemon 已带该变量，可用 `-UseDaemon`
  加速。
- `JAVA_HOME` 固定指向 `%USERPROFILE%\.gradle\jdks\eclipse_adoptium-25-amd64-windows.2`
  （Gradle 自动下载的 Temurin 25）。
- 窗口定位：用 `EnumWindows` 找到属于 fabric dev launch 进程
  （命令行含 `devlaunchinjector`）的可见顶层窗口，标题包含 `Minecraft`。
- **截图防遮挡**：后台脚本调用 `SetForegroundWindow` 会被 Windows 拒绝，
  之前截到过盖在 MC 上方的聊天窗口。现在截图前用
  `SetWindowPos(hwnd, HWND_TOPMOST)` 把游戏窗口临时置顶，并把它移动到屏幕内
  `(32, 32)`（只移动不缩放），截图后再用
  `SetWindowPos(hwnd, HWND_NOTOPMOST)` 恢复原位置与普通层级。
- 截图用 `System.Drawing` 的 `CopyFromScreen`，窗口内容为 1280x720（含窗口
  边框约 1298x767）。
- 结束时只杀 `devlaunchinjector` / `--username MaLiLibUITest` 的 java 进程，
  不影响 Gradle daemon 与其他 teammate 的构建进程；`autotest.err.log` 中的
  `FAILURE: ... runClient ... finished with non-zero exit value -1` 是脚本主动
  终止客户端的预期结果，不是构建失败。

