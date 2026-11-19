<#
.SYNOPSIS
    malilib-ui visual verification: boot the Minecraft 26.x dev client, let the
    dev autotest hook open the litematica config screen, capture a screenshot of
    the game window, then shut the client down.

.DESCRIPTION
    Step by step:
      1. Sets JAVA_HOME to the Gradle-provisioned Temurin 25 JDK.
      2. Starts `gradlew.bat :<Version>:runClient --console=plain` through
         cmd.exe in the background (single-use Gradle JVM by default, so the
         MALILIB_UI_AUTOTEST environment variable reliably reaches the client),
         redirecting Gradle/MC output to run/client/autotest.log (+ .err.log).
      3. Polls for the Minecraft window (visible top-level window owned by the
         fabric devlaunchinjector java process, title contains "Minecraft") and
         for the DevAutoTest log lines (in both the Gradle output and the
         Minecraft logs/latest.log file).
      4. Captures the window via System.Drawing CopyFromScreen once the screen
         is ready, into run/client/screenshots/autotest-<yyyyMMdd-HHmmss>.png.
      5. Kills the runClient java process unless -KeepRunning is given.
      6. Prints the screenshot path, window info and the interesting log lines,
         and writes the same digest to run/client/autotest-report.txt.

    Exit codes:
        0 = config screen was opened and captured
        2 = client window captured, but no config screen (litematica absent)
        1 = failure (no window / crash / timeout), see the log paths

.PARAMETER Version
    Multi-version sub-project to run, defaults to 26.3.

.PARAMETER TimeoutSeconds
    Hard upper bound for the whole "start until a screen is ready" phase.

.PARAMETER OpenWaitSeconds
    How long to wait, after the window appeared, for the autotest hook to open
    the config screen (or to report that no config screen is registered).

.PARAMETER SettleSeconds
    Extra delay after the target screen is known to be active, so the frame is
    fully drawn before the screenshot is taken.

.PARAMETER KeepRunning
    Do not kill the Minecraft client at the end (useful for manual inspection;
    remember to kill it yourself afterwards).

.PARAMETER UseDaemon
    Reuse the Gradle daemon instead of a single-use JVM. Faster, but the daemon
    may not see the MALILIB_UI_AUTOTEST variable set by this script, so the
    autotest hook may not fire. Only use when a daemon started with that
    variable is already warm.

.EXAMPLE
    pwsh -NoProfile -File .\scripts\verify-gui.ps1
.EXAMPLE
    powershell -ExecutionPolicy Bypass -File .\scripts\verify-gui.ps1 -Version 26.3
#>
[CmdletBinding()]
param(
    [string]$Version = '26.3',
    [int]$TimeoutSeconds = 900,
    [int]$OpenWaitSeconds = 150,
    [int]$SettleSeconds = 5,
    [switch]$KeepRunning,
    [switch]$UseDaemon,
    [switch]$NoHover
)

$ErrorActionPreference = 'Stop'

$script:Root       = Split-Path -Parent $PSScriptRoot
$script:RunDir     = Join-Path $script:Root 'run\client'
$script:ShotDir    = Join-Path $script:RunDir 'screenshots'
$script:LogPath    = Join-Path $script:RunDir 'autotest.log'
$script:ErrPath    = Join-Path $script:RunDir 'autotest.err.log'
$script:ReportPath = Join-Path $script:RunDir 'autotest-report.txt'
$script:McLogPath  = Join-Path $script:RunDir 'logs\latest.log'
$script:JavaHome   = Join-Path $env:USERPROFILE '.gradle\jdks\eclipse_adoptium-25-amd64-windows.2'
$script:StartTime  = Get-Date

# ---------------------------------------------------------------------------
# Win32 helpers
# ---------------------------------------------------------------------------
if (-not ('ReaUiWin32' -as [type]))
{
    Add-Type -TypeDefinition @'
using System;
using System.Collections.Generic;
using System.Runtime.InteropServices;
using System.Text;

public sealed class ReaUiWindowInfo
{
    public IntPtr Hwnd;
    public uint Pid;
    public string Title;
    public int Left;
    public int Top;
    public int Right;
    public int Bottom;

    public int Width  { get { return this.Right - this.Left; } }
    public int Height { get { return this.Bottom - this.Top; } }
}

public static class ReaUiWin32
{
    [StructLayout(LayoutKind.Sequential)]
    public struct RECT
    {
        public int Left;
        public int Top;
        public int Right;
        public int Bottom;
    }

    [StructLayout(LayoutKind.Sequential)]
    public struct POINT
    {
        public int X;
        public int Y;
    }

    private delegate bool EnumWindowsProc(IntPtr hWnd, IntPtr lParam);

    [DllImport("user32.dll")]
    private static extern bool EnumWindows(EnumWindowsProc lpEnumFunc, IntPtr lParam);
    [DllImport("user32.dll")]
    private static extern uint GetWindowThreadProcessId(IntPtr hWnd, out uint lpdwProcessId);
    [DllImport("user32.dll")]
    private static extern bool IsWindowVisible(IntPtr hWnd);
    [DllImport("user32.dll", CharSet = CharSet.Unicode)]
    private static extern int GetWindowText(IntPtr hWnd, StringBuilder lpString, int nMaxCount);
    [DllImport("user32.dll", CharSet = CharSet.Unicode)]
    private static extern int GetWindowTextLength(IntPtr hWnd);
    [DllImport("user32.dll")]
    private static extern bool GetWindowRect(IntPtr hWnd, out RECT lpRect);
    [DllImport("user32.dll")]
    private static extern bool SetForegroundWindow(IntPtr hWnd);
    [DllImport("user32.dll")]
    private static extern bool ShowWindow(IntPtr hWnd, int nCmdShow);
    [DllImport("user32.dll")]
    private static extern bool IsIconic(IntPtr hWnd);
    [DllImport("user32.dll")]
    private static extern IntPtr GetForegroundWindow();
    [DllImport("user32.dll")]
    private static extern bool SetWindowPos(IntPtr hWnd, IntPtr hWndInsertAfter, int X, int Y, int cx, int cy, uint uFlags);
    [DllImport("user32.dll")]
    private static extern int GetSystemMetrics(int nIndex);
    [DllImport("user32.dll")]
    private static extern bool SetCursorPos(int X, int Y);
    [DllImport("user32.dll")]
    private static extern bool GetCursorPos(out POINT lpPoint);
    [DllImport("user32.dll")]
    private static extern bool GetClientRect(IntPtr hWnd, out RECT lpRect);
    [DllImport("user32.dll")]
    private static extern bool ClientToScreen(IntPtr hWnd, ref POINT lpPoint);
    [DllImport("user32.dll")]
    private static extern bool BringWindowToTop(IntPtr hWnd);
    [DllImport("user32.dll")]
    private static extern bool AttachThreadInput(uint idAttach, uint idAttachTo, bool fAttach);
    [DllImport("user32.dll")]
    private static extern bool SetFocus(IntPtr hWnd);
    [DllImport("kernel32.dll")]
    private static extern uint GetCurrentThreadId();
    [DllImport("user32.dll")]
    private static extern bool PostMessage(IntPtr hWnd, uint Msg, IntPtr wParam, IntPtr lParam);
    [DllImport("user32.dll")]
    private static extern void mouse_event(uint dwFlags, uint dx, uint dy, uint dwData, UIntPtr dwExtraInfo);
    [DllImport("user32.dll")]
    private static extern void keybd_event(byte bVk, byte bScan, uint dwFlags, UIntPtr dwExtraInfo);
    [DllImport("user32.dll")]
    public static extern bool SetProcessDPIAware();
    [DllImport("dwmapi.dll")]
    private static extern int DwmGetWindowAttribute(IntPtr hwnd, int dwAttribute, out RECT pvAttribute, int cbAttribute);

    public static List<ReaUiWindowInfo> FindWindows(uint[] pids, string titleFilter)
    {
        List<ReaUiWindowInfo> result = new List<ReaUiWindowInfo>();
        HashSet<uint> set = new HashSet<uint>(pids);

        EnumWindows(delegate(IntPtr hWnd, IntPtr lParam)
        {
            if (!IsWindowVisible(hWnd))
            {
                return true;
            }

            uint pid;
            GetWindowThreadProcessId(hWnd, out pid);

            if (!set.Contains(pid))
            {
                return true;
            }

            int len = GetWindowTextLength(hWnd);
            if (len <= 0)
            {
                return true;
            }

            StringBuilder sb = new StringBuilder(len + 2);
            GetWindowText(hWnd, sb, sb.Capacity);
            string title = sb.ToString();

            if (titleFilter != null && title.IndexOf(titleFilter, StringComparison.OrdinalIgnoreCase) < 0)
            {
                return true;
            }

            RECT r;
            if (!GetWindowRect(hWnd, out r))
            {
                return true;
            }

            ReaUiWindowInfo info = new ReaUiWindowInfo();
            info.Hwnd = hWnd;
            info.Pid = pid;
            info.Title = title;
            info.Left = r.Left;
            info.Top = r.Top;
            info.Right = r.Right;
            info.Bottom = r.Bottom;
            result.Add(info);
            return true;
        }, IntPtr.Zero);

        return result;
    }

    public static bool FocusWindow(IntPtr hwnd)
    {
        if (IsIconic(hwnd))
        {
            ShowWindow(hwnd, 9); // SW_RESTORE
        }
        else
        {
            ShowWindow(hwnd, 5); // SW_SHOW
        }

        return SetForegroundWindow(hwnd);
    }

    public static bool GetBounds(IntPtr hwnd, out int left, out int top, out int width, out int height)
    {
        RECT r;
        // DWMWA_EXTENDED_FRAME_BOUNDS = 9 (visible bounds, no invisible resize border)
        int hr = DwmGetWindowAttribute(hwnd, 9, out r, Marshal.SizeOf(typeof(RECT)));

        if (hr != 0)
        {
            if (!GetWindowRect(hwnd, out r))
            {
                left = 0;
                top = 0;
                width = 0;
                height = 0;
                return false;
            }
        }

        left = r.Left;
        top = r.Top;
        width = r.Right - r.Left;
        height = r.Bottom - r.Top;
        return width > 0 && height > 0;
    }

    public static IntPtr GetForegroundWindowHandle()
    {
        return GetForegroundWindow();
    }

    public static int ScreenWidth
    {
        get { return GetSystemMetrics(0); } // SM_CXSCREEN
    }

    public static int ScreenHeight
    {
        get { return GetSystemMetrics(1); } // SM_CYSCREEN
    }

    public static bool SetCursorPosition(int x, int y)
    {
        return SetCursorPos(x, y);
    }

    public static bool IsForeground(IntPtr hwnd)
    {
        return GetForegroundWindow() == hwnd;
    }

    /// <summary>
    /// Best-effort activation of a background window. SetForegroundWindow alone
    /// is usually denied for a background script; attaching to the foreground
    /// thread's input queue first is the classic workaround.
    /// </summary>
    public static bool ForceForegroundWindow(IntPtr hwnd)
    {
        if (GetForegroundWindow() == hwnd)
        {
            return true;
        }

        uint dummy;
        IntPtr fg = GetForegroundWindow();
        uint fgThread = 0;
        if (fg != IntPtr.Zero)
        {
            fgThread = GetWindowThreadProcessId(fg, out dummy);
        }
        uint targetThread = GetWindowThreadProcessId(hwnd, out dummy);
        uint curThread = GetCurrentThreadId();

        bool attachedFg = false;
        bool attachedTarget = false;
        if (fgThread != 0 && fgThread != curThread)
        {
            attachedFg = AttachThreadInput(curThread, fgThread, true);
        }
        if (targetThread != 0 && targetThread != curThread)
        {
            attachedTarget = AttachThreadInput(curThread, targetThread, true);
        }

        ShowWindow(hwnd, 9); // SW_RESTORE
        BringWindowToTop(hwnd);
        bool ok = SetForegroundWindow(hwnd);
        SetFocus(hwnd);

        if (attachedTarget)
        {
            AttachThreadInput(curThread, targetThread, false);
        }
        if (attachedFg)
        {
            AttachThreadInput(curThread, fgThread, false);
        }

        return ok;
    }

    /// <summary>
    /// Returns the screen position of the client area origin (top-left, inside
    /// the title bar / borders).
    /// </summary>
    public static bool GetClientOrigin(IntPtr hwnd, out int x, out int y)
    {
        RECT r;
        if (!GetClientRect(hwnd, out r))
        {
            x = 0;
            y = 0;
            return false;
        }

        POINT p;
        p.X = 0;
        p.Y = 0;
        if (!ClientToScreen(hwnd, ref p))
        {
            x = 0;
            y = 0;
            return false;
        }

        x = p.X;
        y = p.Y;
        return true;
    }

    private static IntPtr MakeLParam(int x, int y)
    {
        return (IntPtr)((y << 16) | (x & 0xFFFF));
    }

    /// <summary>
    /// Moves the cursor over a client-relative point. Uses the real cursor when
    /// the game window is the foreground window, otherwise posts WM_MOUSEMOVE
    /// (GLFW updates its cursor position from that message even while the
    /// window is not focused).
    /// </summary>
    public static bool HoverClient(IntPtr hwnd, int clientX, int clientY)
    {
        int ox;
        int oy;
        if (!GetClientOrigin(hwnd, out ox, out oy))
        {
            return false;
        }

        if (IsForeground(hwnd))
        {
            SetCursorPos(ox + clientX, oy + clientY);
            return true;
        }

        return PostMessage(hwnd, 0x0200, IntPtr.Zero, MakeLParam(clientX, clientY));
    }

    /// <summary>
    /// Clicks a client-relative point (left button). Prefers real input when the
    /// window is foreground, falls back to WM_LBUTTONDOWN/UP otherwise.
    /// </summary>
    public static bool ClickClient(IntPtr hwnd, int clientX, int clientY)
    {
        int ox;
        int oy;
        if (!GetClientOrigin(hwnd, out ox, out oy))
        {
            return false;
        }

        if (IsForeground(hwnd))
        {
            SetCursorPos(ox + clientX, oy + clientY);
            System.Threading.Thread.Sleep(120);
            mouse_event(0x0002, 0, 0, 0, UIntPtr.Zero); // MOUSEEVENTF_LEFTDOWN
            System.Threading.Thread.Sleep(80);
            mouse_event(0x0004, 0, 0, 0, UIntPtr.Zero); // MOUSEEVENTF_LEFTUP
            return true;
        }

        IntPtr lp = MakeLParam(clientX, clientY);
        PostMessage(hwnd, 0x0200, IntPtr.Zero, lp);        // WM_MOUSEMOVE
        System.Threading.Thread.Sleep(40);
        PostMessage(hwnd, 0x0201, new IntPtr(1), lp);      // WM_LBUTTONDOWN
        System.Threading.Thread.Sleep(70);
        PostMessage(hwnd, 0x0202, IntPtr.Zero, lp);        // WM_LBUTTONUP
        return true;
    }

    /// <summary>
    /// Scrolls the mouse wheel at a client-relative point. Real wheel input when
    /// the game window is foreground, otherwise a synthetic WM_MOUSEWHEEL.
    /// </summary>
    public static bool WheelClient(IntPtr hwnd, int clientX, int clientY, int delta)
    {
        int ox;
        int oy;
        if (!GetClientOrigin(hwnd, out ox, out oy))
        {
            return false;
        }

        if (IsForeground(hwnd))
        {
            SetCursorPos(ox + clientX, oy + clientY);
            System.Threading.Thread.Sleep(80);
            mouse_event(0x0800, 0, 0, unchecked((uint)delta), UIntPtr.Zero); // MOUSEEVENTF_WHEEL
            return true;
        }

        int sx = ox + clientX;
        int sy = oy + clientY;
        IntPtr wParam = (IntPtr)((delta << 16) & unchecked((int)0xFFFF0000));
        IntPtr lParam = (IntPtr)((sy << 16) | (sx & 0xFFFF));
        return PostMessage(hwnd, 0x020A, wParam, lParam);    // WM_MOUSEWHEEL
    }

    /// <summary>
    /// Sends a raw key press (WM_KEYDOWN + WM_KEYUP) to a window.
    /// </summary>
    public static bool KeyPress(IntPtr hwnd, int virtualKey)
    {
        PostMessage(hwnd, 0x0100, new IntPtr(virtualKey), new IntPtr(1)); // WM_KEYDOWN
        System.Threading.Thread.Sleep(60);
        return PostMessage(hwnd, 0x0101, new IntPtr(virtualKey), new IntPtr(unchecked((int)0xC0000001))); // WM_KEYUP
    }

    /// <summary>
    /// Sends a real (system-injected) key press to the foreground window. This is
    /// what malilib's dialog ESC handling needs; the posted WM_KEYDOWN variant is
    /// occasionally dropped by GLFW when the window lost activation.
    /// </summary>
    public static bool KeyPressForeground(IntPtr hwnd, int virtualKey)
    {
        if (!IsForeground(hwnd))
        {
            ForceForegroundWindow(hwnd);
            System.Threading.Thread.Sleep(350);

            if (!IsForeground(hwnd))
            {
                return KeyPress(hwnd, virtualKey);
            }
        }

        keybd_event((byte)virtualKey, 0, 0, UIntPtr.Zero);   // key down
        System.Threading.Thread.Sleep(70);
        keybd_event((byte)virtualKey, 0, 2, UIntPtr.Zero);   // key up (KEYEVENTF_KEYUP)
        return true;
    }
    /// <summary>
    /// Types text into the focused text field via WM_CHAR messages.
    /// </summary>
    public static bool TypeText(IntPtr hwnd, string text)
    {
        foreach (char c in text)
        {
            PostMessage(hwnd, 0x0102, new IntPtr(c), new IntPtr(1)); // WM_CHAR
            System.Threading.Thread.Sleep(35);
        }

        return true;
    }

    public static bool TryGetCursorPosition(out int x, out int y)
    {
        POINT p;

        if (GetCursorPos(out p))
        {
            x = p.X;
            y = p.Y;
            return true;
        }

        x = 0;
        y = 0;
        return false;
    }

    /// <summary>
    /// Raises the window above all non-topmost windows (HWND_TOPMOST) and moves
    /// its top-left corner without resizing it (SWP_NOSIZE).
    /// </summary>
    public static bool MoveToTopMost(IntPtr hwnd, int x, int y)
    {
        const uint SWP_NOSIZE = 0x0001;
        const uint SWP_SHOWWINDOW = 0x0040;
        IntPtr HWND_TOPMOST = new IntPtr(-1);
        return SetWindowPos(hwnd, HWND_TOPMOST, x, y, 0, 0, SWP_NOSIZE | SWP_SHOWWINDOW);
    }

    /// <summary>
    /// Puts the window back into the normal z-order band and restores its
    /// original top-left corner.
    /// </summary>
    public static bool RestoreWindowPosition(IntPtr hwnd, int x, int y)
    {
        const uint SWP_NOSIZE = 0x0001;
        const uint SWP_SHOWWINDOW = 0x0040;
        IntPtr HWND_NOTOPMOST = new IntPtr(-2);
        return SetWindowPos(hwnd, HWND_NOTOPMOST, x, y, 0, 0, SWP_NOSIZE | SWP_SHOWWINDOW);
    }
}
'@
}

try { [void][ReaUiWin32]::SetProcessDPIAware() } catch { }

Add-Type -AssemblyName System.Drawing

# ---------------------------------------------------------------------------
# Small helpers
# ---------------------------------------------------------------------------
function Write-Info([string]$Message)
{
    Write-Host ("[{0:HH:mm:ss}] {1}" -f (Get-Date), $Message)
}

function Read-TextTail
{
    param(
        [string]$Path,
        [int]$MaxBytes = 262144
    )

    if (-not (Test-Path -LiteralPath $Path))
    {
        return ''
    }

    try
    {
        $share = [System.IO.FileShare]::ReadWrite -bor [System.IO.FileShare]::Delete
        $fs = [System.IO.File]::Open($Path, [System.IO.FileMode]::Open, [System.IO.FileAccess]::Read, $share)
        try
        {
            $len = $fs.Length
            $start = [Math]::Max(0, $len - $MaxBytes)
            [void]$fs.Seek($start, [System.IO.SeekOrigin]::Begin)
            $count = [int]($len - $start)
            $buf = New-Object byte[] $count
            $read = 0

            while ($read -lt $count)
            {
                $n = $fs.Read($buf, $read, $count - $read)
                if ($n -le 0) { break }
                $read += $n
            }

            return [System.Text.Encoding]::UTF8.GetString($buf, 0, $read)
        }
        finally
        {
            $fs.Dispose()
        }
    }
    catch
    {
        return ''
    }
}

function Get-RunClientProcessInfo
{
    $procs = Get-CimInstance Win32_Process -Filter "Name='java.exe' OR Name='javaw.exe'" -ErrorAction SilentlyContinue
    $result = @()

    foreach ($p in $procs)
    {
        $cmd = $p.CommandLine
        if ([string]::IsNullOrEmpty($cmd)) { continue }

        # fabric-loom dev runs are launched through the dev launch injector and
        # carry the --username argument configured in build.fabric.gradle.kts.
        if ($cmd -match 'devlaunchinjector' -or $cmd -match '--username\s+MaLiLibUITest')
        {
            $result += [pscustomobject]@{
                ProcessId   = [int]$p.ProcessId
                CommandLine = $cmd
            }
        }
    }

    return $result
}

function Find-MinecraftWindow
{
    $runProcs = @(Get-RunClientProcessInfo)

    if ($runProcs.Count -eq 0)
    {
        return $null
    }

    $pids = [uint32[]]@($runProcs | ForEach-Object { [uint32]$_.ProcessId })
    $candidates = @([ReaUiWin32]::FindWindows($pids, 'Minecraft') |
        Where-Object { $_.Width -ge 640 -and $_.Height -ge 480 } |
        Sort-Object { $_.Width * $_.Height } -Descending)

    if ($candidates.Count -eq 0)
    {
        # Fall back to any visible, reasonably sized window of the client
        # process (the GLFW title can be empty for a moment during startup).
        $candidates = @([ReaUiWin32]::FindWindows($pids, $null) |
            Where-Object { $_.Width -ge 640 -and $_.Height -ge 480 } |
            Sort-Object { $_.Width * $_.Height } -Descending)
    }

    if ($candidates.Count -eq 0)
    {
        return $null
    }

    return $candidates[0]
}

function Get-ImageStats
{
    param([string]$Path)

    $bmp = [System.Drawing.Bitmap]::FromFile($Path)
    try
    {
        $colors = New-Object 'System.Collections.Generic.HashSet[int]'
        $stepX = [Math]::Max(1, [int]($bmp.Width / 64))
        $stepY = [Math]::Max(1, [int]($bmp.Height / 40))
        $samples = 0
        $sum = 0.0

        for ($x = 0; $x -lt $bmp.Width; $x += $stepX)
        {
            for ($y = 0; $y -lt $bmp.Height; $y += $stepY)
            {
                $c = $bmp.GetPixel($x, $y)
                [void]$colors.Add((($c.R -shl 16) -bor ($c.G -shl 8) -bor $c.B))
                $sum += ($c.R + $c.G + $c.B) / 3.0
                $samples++
            }
        }

        $avg = 0.0
        if ($samples -gt 0) { $avg = $sum / $samples }

        return [pscustomobject]@{
            Width             = $bmp.Width
            Height            = $bmp.Height
            DistinctColors    = $colors.Count
            AverageBrightness = [Math]::Round($avg, 1)
        }
    }
    finally
    {
        $bmp.Dispose()
    }
}

function Save-WindowScreenshot
{
    param(
        [IntPtr]$Hwnd,
        [string]$Path,
        [switch]$RaiseWindow,
        [int]$HoverRelX = -1,
        [int]$HoverRelY = -1
    )

    $left = 0
    $top = 0
    $width = 0
    $height = 0

    if (-not [ReaUiWin32]::GetBounds($Hwnd, [ref]$left, [ref]$top, [ref]$width, [ref]$height))
    {
        throw 'Could not query the window bounds.'
    }

    $origLeft = $left
    $origTop = $top

    if ($RaiseWindow)
    {
        # SetWindowPos(HWND_TOPMOST) works from a background script where
        # SetForegroundWindow is denied, so a covering window (chat client,
        # browser, ...) cannot end up in the screenshot. The window is only
        # moved (SWP_NOSIZE), never resized, and kept fully on screen.
        $screenW = [ReaUiWin32]::ScreenWidth
        $screenH = [ReaUiWin32]::ScreenHeight
        $targetX = 32
        $targetY = 32

        if ($screenW -gt 0 -and ($targetX + $width) -gt ($screenW - 8))
        {
            $targetX = [Math]::Max(0, $screenW - $width - 8)
        }
        if ($screenH -gt 0 -and ($targetY + $height) -gt ($screenH - 8))
        {
            $targetY = [Math]::Max(0, $screenH - $height - 8)
        }

        [void][ReaUiWin32]::MoveToTopMost($Hwnd, $targetX, $targetY)
        Start-Sleep -Milliseconds 900
        [void][ReaUiWin32]::FocusWindow($Hwnd)
        Start-Sleep -Milliseconds 700
        [void][ReaUiWin32]::GetBounds($Hwnd, [ref]$left, [ref]$top, [ref]$width, [ref]$height)

        if ($HoverRelX -ge 0 -and $HoverRelY -ge 0)
        {
            # Park the cursor over a widget inside the game window so hover
            # states are exercised in the captured frame.
            [void][ReaUiWin32]::SetCursorPosition($left + $HoverRelX, $top + $HoverRelY)
            Start-Sleep -Milliseconds 700
        }
    }
    else
    {
        [void][ReaUiWin32]::FocusWindow($Hwnd)
        Start-Sleep -Milliseconds 800
    }

    $dir = Split-Path -Parent $Path
    if (-not (Test-Path -LiteralPath $dir))
    {
        New-Item -ItemType Directory -Force -Path $dir | Out-Null
    }

    $bmp = New-Object System.Drawing.Bitmap $width, $height
    try
    {
        $g = [System.Drawing.Graphics]::FromImage($bmp)
        try
        {
            $g.CopyFromScreen($left, $top, 0, 0, (New-Object System.Drawing.Size $width, $height))
        }
        finally
        {
            $g.Dispose()
        }

        $bmp.Save($Path, [System.Drawing.Imaging.ImageFormat]::Png)
    }
    finally
    {
        $bmp.Dispose()
    }

    if ($RaiseWindow)
    {
        # Restore the normal z-order band and the original position.
        try
        {
            [void][ReaUiWin32]::RestoreWindowPosition($Hwnd, $origLeft, $origTop)
        }
        catch { }
    }

    return $Path
}

function Get-LogMatches
{
    param(
        [string]$Text,
        [int]$MaxLines = 120
    )

    if ([string]::IsNullOrEmpty($Text))
    {
        return @()
    }

    $patterns = @(
        '\[autotest\]',
        'malilib-ui',
        'MaLiLib UI',
        'Mixin apply failed|MixinApplyError|InjectionError',
        'Crash report|A game crash occurred',
        'Could not find required mod|Incompatible mods found',
        'Exception in thread',
        'BUILD FAILED|FAILURE: Build failed',
        'Cleanly shut down|Stopping!'
    )

    $lines = $Text -split "`r?`n"
    $matched = New-Object System.Collections.Generic.List[string]

    foreach ($line in $lines)
    {
        foreach ($pattern in $patterns)
        {
            if ($line -match $pattern)
            {
                $matched.Add($line)
                break
            }
        }

        if ($matched.Count -ge $MaxLines) { break }
    }

    return $matched.ToArray()
}


function Invoke-GameClick
{
    param(
        [IntPtr]$Hwnd,
        [int]$ShotX,
        [int]$ShotY
    )

    # Re-assert topmost + foreground before every interaction; a background
    # window can silently drop real mouse input.
    [void][ReaUiWin32]::MoveToTopMost($Hwnd, 32, 32)
    Start-Sleep -Milliseconds 250
    [void][ReaUiWin32]::ForceForegroundWindow($Hwnd)
    Start-Sleep -Milliseconds 400

    $left = 0
    $top = 0
    $width = 0
    $height = 0
    [void][ReaUiWin32]::GetBounds($Hwnd, [ref]$left, [ref]$top, [ref]$width, [ref]$height)
    $clientX = 0
    $clientY = 0
    [void][ReaUiWin32]::GetClientOrigin($Hwnd, [ref]$clientX, [ref]$clientY)
    $relX = $clientX - $left
    $relY = $clientY - $top

    [void][ReaUiWin32]::HoverClient($Hwnd, ($ShotX - $relX), ($ShotY - $relY))
    Start-Sleep -Milliseconds 350
    [void][ReaUiWin32]::ClickClient($Hwnd, ($ShotX - $relX), ($ShotY - $relY))
}
# ---------------------------------------------------------------------------
# Pre-flight
# ---------------------------------------------------------------------------
Write-Info "malilib-ui GUI verification for Minecraft $Version"
Write-Info "workspace : $($script:Root)"
Write-Info "run dir   : $($script:RunDir)"

if (-not (Test-Path -LiteralPath (Join-Path $script:Root 'gradlew.bat')))
{
    throw "gradlew.bat not found in $($script:Root)"
}

if (-not (Test-Path -LiteralPath (Join-Path $script:JavaHome 'bin\java.exe')))
{
    throw "JAVA_HOME candidate not found: $($script:JavaHome)"
}

if (-not (Test-Path -LiteralPath $script:ShotDir))
{
    New-Item -ItemType Directory -Force -Path $script:ShotDir | Out-Null
}

$env:JAVA_HOME = $script:JavaHome
$env:MALILIB_UI_AUTOTEST = 'litematica'
Write-Info "JAVA_HOME           : $($env:JAVA_HOME)"
Write-Info "MALILIB_UI_AUTOTEST : $($env:MALILIB_UI_AUTOTEST)"

# Kill leftovers from a previous verification run: only the loom dev client is
# matched (devlaunchinjector / our fixed dev username), never a Gradle daemon.
$leftovers = @(Get-RunClientProcessInfo)
if ($leftovers.Count -gt 0)
{
    Write-Info "Killing $($leftovers.Count) leftover runClient java process(es): $($leftovers.ProcessId -join ', ')"
    foreach ($p in $leftovers)
    {
        Stop-Process -Id $p.ProcessId -Force -ErrorAction SilentlyContinue
    }
    Start-Sleep -Seconds 2
}

Remove-Item -LiteralPath $script:LogPath -Force -ErrorAction SilentlyContinue
Remove-Item -LiteralPath $script:ErrPath -Force -ErrorAction SilentlyContinue

# ---------------------------------------------------------------------------
# Launch the dev client in the background
# ---------------------------------------------------------------------------
$daemonArg = ''
if (-not $UseDaemon)
{
    # A single-use JVM inherits this process' environment, which is what makes
    # MALILIB_UI_AUTOTEST reach the Minecraft client reliably.
    $daemonArg = ' --no-daemon'
}

$gradleCommand = "$($script:Root)\gradlew.bat :${Version}:runClient --console=plain$daemonArg"
$cmdExe = $env:ComSpec
if ([string]::IsNullOrEmpty($cmdExe))
{
    $cmdExe = Join-Path $env:SystemRoot 'System32\cmd.exe'
}

Write-Info "Starting: gradlew.bat :${Version}:runClient --console=plain$daemonArg"

$launcher = Start-Process -FilePath $cmdExe `
    -ArgumentList "/d /c `"$gradleCommand`"" `
    -WorkingDirectory $script:Root `
    -WindowStyle Hidden `
    -RedirectStandardOutput $script:LogPath `
    -RedirectStandardError $script:ErrPath `
    -PassThru

Write-Info "launcher pid=$($launcher.Id); log -> $($script:LogPath)"

# ---------------------------------------------------------------------------
# Poll: window appearance, autotest log lines, fatal errors
# ---------------------------------------------------------------------------
$window = $null
$windowSeenAt = $null
$screenReadyAt = $null
$opened = $false
$noConfig = $false
$failure = $null
$fatalPattern = 'BUILD FAILED|FAILURE: Build failed|Could not find required mod|Incompatible mods found|A game crash occurred|Mixin apply failed|MixinApplyError|InjectionError'
$lastProgress = Get-Date
$pollCount = 0

while ($true)
{
    $now = Get-Date
    $elapsed = ($now - $script:StartTime).TotalSeconds

    if ($elapsed -gt $TimeoutSeconds)
    {
        $failure = "Timeout after $TimeoutSeconds s while waiting for the client / autotest hook."
        break
    }

    $log = Read-TextTail -Path $script:LogPath
    $mcLog = Read-TextTail -Path $script:McLogPath
    $autotestText = $log + "`n" + $mcLog

    if ([string]::IsNullOrEmpty($screenReadyAt) -and $autotestText -match '\[autotest\]')
    {
        $screenReadyAt = $now
    }

    if ($autotestText -match '\[autotest\] Opened config screen')
    {
        $opened = $true

        if ($null -eq $window)
        {
            $window = Find-MinecraftWindow
            if ($null -ne $window) { $windowSeenAt = $now }
        }

        if ($null -ne $window) { break }
    }
    elseif ($autotestText -match '\[autotest\] No config screen registered')
    {
        $noConfig = $true

        if ($null -eq $window)
        {
            $window = Find-MinecraftWindow
            if ($null -ne $window) { $windowSeenAt = $now }
        }

        if ($null -ne $window) { break }
    }
    elseif ($autotestText -match '\[autotest\] Failed to open config screen')
    {
        $failure = 'DevAutoTest failed to open the config screen (see log).'
        break
    }

    # Mixin / loader errors abort the client. A matching line alone is not proof
    # of a crash while the window is up (mixin debug output can contain similar
    # text), so only treat it as fatal when no window showed up at all.
    if ($null -eq $window)
    {
        $fatalMatch = [regex]::Match($log, $fatalPattern)
        if ($fatalMatch.Success -and $null -eq $screenReadyAt)
        {
            $failure = "Fatal log pattern detected before the client window appeared: $($fatalMatch.Value)"
            break
        }
    }

    if ($null -eq $window)
    {
        $pollCount++
        # Process/window probing is comparatively expensive; do it every other
        # second, the log reads above are cheap.
        if (($pollCount % 2) -eq 1)
        {
            $candidate = Find-MinecraftWindow
            if ($null -ne $candidate)
            {
                $window = $candidate
                $windowSeenAt = $now
                $hwndValue = $window.Hwnd.ToInt64()
                Write-Info ("Minecraft window detected: pid={0} title='{1}' size={2}x{3} hwnd=0x{4:X}" -f `
                    $window.Pid, $window.Title, $window.Width, $window.Height, $hwndValue)
            }
        }
    }

    if ($null -ne $window)
    {
        $sinceWindow = ($now - $windowSeenAt).TotalSeconds

        if ($sinceWindow -gt $OpenWaitSeconds)
        {
            $failure = "Window was up for $([int]$sinceWindow) s without an [autotest] log line (client may be stuck in the title screen or the dev hook is inactive)."
            break
        }
    }

    if ($null -ne $screenReadyAt -and $null -eq $window)
    {
        # The hook already reported something but the window cannot be located:
        # give the OS a moment, then fail with a clear message instead of
        # polling until the global timeout.
        if (($now - $screenReadyAt).TotalSeconds -gt 30)
        {
            $failure = 'The autotest hook logged a result, but no Minecraft window belonging to the dev client could be located.'
            break
        }
    }

    if ($launcher.HasExited -and $null -eq $window -and $null -eq $screenReadyAt)
    {
        $failure = "Gradle launcher exited (code=$($launcher.ExitCode)) without a Minecraft window; check the log."
        break
    }

    if (($now - $lastProgress).TotalSeconds -ge 15)
    {
        $lastProgress = $now
        $state = 'no window yet'
        if ($null -ne $window)
        {
            $state = "window up $([int](($now - $windowSeenAt).TotalSeconds))s"
        }

        $logState = 'client not started yet'
        if ($log -match 'Setting user|Loading Minecraft|Backend library: LWJGL|Created:.*texture-atlas')
        {
            $logState = 'client booting'
        }
        if ($autotestText -match '\[autotest\]')
        {
            $logState = 'autotest active'
        }

        Write-Info ("waiting: elapsed {0:N0}s, {1}, {2}" -f $elapsed, $state, $logState)
    }

    Start-Sleep -Milliseconds 1000
}

# ---------------------------------------------------------------------------
# Capture
# ---------------------------------------------------------------------------
$stamp = Get-Date -Format 'yyyyMMdd-HHmmss'
$screenshotPath = $null
$titleShotPath = $null
$imageStats = $null
$captureError = $null
$screenshotPaths = @()
$shotStats = @()
$postClickError = $null
$cursorSavedX = 0
$cursorSavedY = 0
$cursorSaved = [ReaUiWin32]::TryGetCursorPosition([ref]$cursorSavedX, [ref]$cursorSavedY)

if ($null -eq $window)
{
    Write-Info 'No Minecraft window was found, nothing to capture.'
}
else
{
    # The window handle may have changed; re-resolve it from the live process ids.
    $freshWindow = Find-MinecraftWindow
    if ($null -ne $freshWindow)
    {
        $window = $freshWindow
    }

    try
    {
        # Raise the game window once: HWND_TOPMOST + best-effort foreground, and
        # park it fully on screen (moved, never resized).
        $winLeft = 0
        $winTop = 0
        $winW = 0
        $winH = 0
        [void][ReaUiWin32]::GetBounds($window.Hwnd, [ref]$winLeft, [ref]$winTop, [ref]$winW, [ref]$winH)
        $origWinLeft = $winLeft
        $origWinTop = $winTop

        $screenW = [ReaUiWin32]::ScreenWidth
        $screenH = [ReaUiWin32]::ScreenHeight
        $targetX = 32
        $targetY = 32
        if ($screenW -gt 0 -and ($targetX + $winW) -gt ($screenW - 8))
        {
            $targetX = [Math]::Max(0, $screenW - $winW - 8)
        }
        if ($screenH -gt 0 -and ($targetY + $winH) -gt ($screenH - 8))
        {
            $targetY = [Math]::Max(0, $screenH - $winH - 8)
        }

        [void][ReaUiWin32]::MoveToTopMost($window.Hwnd, $targetX, $targetY)
        Start-Sleep -Milliseconds 900
        [void][ReaUiWin32]::ForceForegroundWindow($window.Hwnd)
        Start-Sleep -Milliseconds 800

        $isForeground = [ReaUiWin32]::IsForeground($window.Hwnd)
        Write-Info "Window raised (topmost=on, foreground=$isForeground)"

        # The screenshot covers the whole window, but GLFW reports mouse
        # positions relative to the client area, which starts below the title bar.
        $clientX = 0
        $clientY = 0
        [void][ReaUiWin32]::GetBounds($window.Hwnd, [ref]$winLeft, [ref]$winTop, [ref]$winW, [ref]$winH)
        [void][ReaUiWin32]::GetClientOrigin($window.Hwnd, [ref]$clientX, [ref]$clientY)
        $offsetX = $clientX - $winLeft
        $offsetY = $clientY - $winTop
        Write-Info "Client-area offset inside window screenshot: $offsetX,$offsetY"

        if ($noConfig -or ($null -eq $failure -and -not $opened))
        {
            # Title screen only (litematica not present / not registered).
            Start-Sleep -Seconds $SettleSeconds
            $titleShotPath = Join-Path $script:ShotDir ("round5c-title-$stamp.png")
            [void](Save-WindowScreenshot -Hwnd $window.Hwnd -Path $titleShotPath)
            $screenshotPaths += $titleShotPath
        }
        else
        {
            # ---- Round 5c scenario: tab / reset / search preview shots -----------
            # Every click goes through Invoke-GameClick (topmost + foreground
            # re-assert), and every step is captured so the result can be verified.
            Start-Sleep -Seconds $SettleSeconds

            # H1: hover calibration - which sidebar entry lights up?
            [void][ReaUiWin32]::HoverClient($window.Hwnd, (100 - $offsetX), (218 - $offsetY))
            Start-Sleep -Milliseconds 700
            $shotH = Join-Path $script:ShotDir ("round5c-H1-hover-alltab-$stamp.png")
            [void](Save-WindowScreenshot -Hwnd $window.Hwnd -Path $shotH)
            $screenshotPaths += $shotH
            Write-Info "H1: hover calibration saved: $shotH"

            # "全部" tab
            Invoke-GameClick -Hwnd $window.Hwnd -ShotX 100 -ShotY 218
            Start-Sleep -Seconds 2
            $shotAll = Join-Path $script:ShotDir ("round5c-all-tab-$stamp.png")
            [void](Save-WindowScreenshot -Hwnd $window.Hwnd -Path $shotAll)
            $screenshotPaths += $shotAll
            Write-Info "ALL tab saved: $shotAll"

            # "信息HUD" tab (plain search variant)
            Invoke-GameClick -Hwnd $window.Hwnd -ShotX 100 -ShotY 330
            Start-Sleep -Seconds 2
            $shotPlain = Join-Path $script:ShotDir ("round5c-plain-tab-$stamp.png")
            [void](Save-WindowScreenshot -Hwnd $window.Hwnd -Path $shotPlain)
            $screenshotPaths += $shotPlain
            Write-Info "PLAIN tab saved: $shotPlain"

            # "渲染层" tab (opens the render layer editor screen)
            Invoke-GameClick -Hwnd $window.Hwnd -ShotX 100 -ShotY 554
            Start-Sleep -Seconds 3
            $shotRender = Join-Path $script:ShotDir ("round5c-render-layers-$stamp.png")
            [void](Save-WindowScreenshot -Hwnd $window.Hwnd -Path $shotRender)
            $screenshotPaths += $shotRender
            Write-Info "RENDER LAYERS saved: $shotRender"

            # Back to the "通用" tab.
            Invoke-GameClick -Hwnd $window.Hwnd -ShotX 100 -ShotY 274
            Start-Sleep -Seconds 2

            # Reset states: flip the 2nd row boolean so its reset button enables.
            Invoke-GameClick -Hwnd $window.Hwnd -ShotX 760 -ShotY 277
            Start-Sleep -Seconds 2
            $shotReset = Join-Path $script:ShotDir ("round5c-reset-states-$stamp.png")
            [void](Save-WindowScreenshot -Hwnd $window.Hwnd -Path $shotReset)
            $screenshotPaths += $shotReset
            Write-Info "RESET states saved: $shotReset"

            # Config row hover state.
            [void][ReaUiWin32]::MoveToTopMost($window.Hwnd, 32, 32)
            Start-Sleep -Milliseconds 250
            [void][ReaUiWin32]::ForceForegroundWindow($window.Hwnd)
            Start-Sleep -Milliseconds 400
            [void][ReaUiWin32]::HoverClient($window.Hwnd, (760 - $offsetX), (277 - $offsetY))
            Start-Sleep -Milliseconds 900
            $shotHover = Join-Path $script:ShotDir ("round5c-hover-row-$stamp.png")
            [void](Save-WindowScreenshot -Hwnd $window.Hwnd -Path $shotHover)
            $screenshotPaths += $shotHover
            Write-Info "HOVER row saved: $shotHover"

            # Search bar expanded state.
            Invoke-GameClick -Hwnd $window.Hwnd -ShotX 500 -ShotY 186
            Start-Sleep -Seconds 2
            $shotSearch = Join-Path $script:ShotDir ("round5c-search-expanded-$stamp.png")
            [void](Save-WindowScreenshot -Hwnd $window.Hwnd -Path $shotSearch)
            $screenshotPaths += $shotSearch
            Write-Info "SEARCH expanded saved: $shotSearch"
        }

        # Restore the normal z-order band and the original window position.
        [void][ReaUiWin32]::RestoreWindowPosition($window.Hwnd, $origWinLeft, $origWinTop)

        foreach ($shotPath in $screenshotPaths)
        {
            if (Test-Path -LiteralPath $shotPath)
            {
                $shotStats += [pscustomobject]@{
                    Path  = $shotPath
                    Stats = (Get-ImageStats -Path $shotPath)
                }
            }
        }

        if ($shotStats.Count -gt 0)
        {
            $screenshotPath = $shotStats[0].Path
            $imageStats = $shotStats[0].Stats
        }
    }
    catch
    {
        $captureError = $_.Exception.Message
        Write-Info "Screenshot failed: $captureError"
    }

    # Restore the user's mouse position if the hover probes moved it.
    if ($cursorSaved)
    {
        [void][ReaUiWin32]::SetCursorPosition($cursorSavedX, $cursorSavedY)
        Write-Info "Mouse cursor restored to ($cursorSavedX, $cursorSavedY)"
    }
}

# ---------------------------------------------------------------------------
# Log digest (collected before shutting the client down)
# ---------------------------------------------------------------------------
$logText = Read-TextTail -Path $script:LogPath -MaxBytes 1048576
$errText = Read-TextTail -Path $script:ErrPath -MaxBytes 262144
$logMatches = @(Get-LogMatches -Text $logText)

$mcLogText = Read-TextTail -Path $script:McLogPath -MaxBytes 1048576
$mcLogMatches = @(Get-LogMatches -Text $mcLogText)

# ---------------------------------------------------------------------------
# Shutdown
# ---------------------------------------------------------------------------
$killed = @()

if ($KeepRunning)
{
    Write-Info 'KeepRunning set: the Minecraft client is left running.'
}
else
{
    $targets = @(Get-RunClientProcessInfo)
    foreach ($t in $targets)
    {
        try
        {
            Stop-Process -Id $t.ProcessId -Force -ErrorAction Stop
            $killed += $t.ProcessId
        }
        catch
        {
            Write-Info "Could not stop java pid=$($t.ProcessId): $($_.Exception.Message)"
        }
    }

    if ($killed.Count -gt 0)
    {
        Write-Info "Stopped runClient java process(es): $($killed -join ', ')"
    }

    # Wait for the client process to disappear, then stop the launcher wrapper
    # if it is still around.
    for ($i = 0; $i -lt 20; $i++)
    {
        Start-Sleep -Milliseconds 500
        if (@(Get-RunClientProcessInfo).Count -eq 0) { break }
    }

    $remaining = @(Get-RunClientProcessInfo)
    foreach ($t in $remaining)
    {
        try { Stop-Process -Id $t.ProcessId -Force -ErrorAction SilentlyContinue } catch { }
    }

    if ($null -ne $launcher)
    {
        try
        {
            $launcher.Refresh()
            if (-not $launcher.HasExited)
            {
                Start-Sleep -Seconds 2
                $launcher.Refresh()
                if (-not $launcher.HasExited)
                {
                    Stop-Process -Id $launcher.Id -Force -ErrorAction SilentlyContinue
                }
            }
        }
        catch { }
    }

    $stillAlive = @(Get-RunClientProcessInfo)
    if ($stillAlive.Count -gt 0)
    {
        Write-Info "WARNING: $($stillAlive.Count) runClient java process(es) still alive: $($stillAlive.ProcessId -join ', ')"
    }
    else
    {
        Write-Info 'runClient process cleanup verified: no dev client java process left.'
    }
}

# ---------------------------------------------------------------------------
# Report
# ---------------------------------------------------------------------------
$status = 'FAILED'
$exitCode = 1

if ($opened -and $null -eq $postClickError -and $null -ne $screenshotPath -and $null -ne $imageStats -and $imageStats.DistinctColors -ge 8)
{
    $status = 'SUCCESS_CONFIG_SCREEN'
    $exitCode = 0
}
elseif ($noConfig -and $null -ne $screenshotPath -and $null -ne $imageStats)
{
    $status = 'PARTIAL_TITLE_SCREEN_ONLY'
    $exitCode = 2
}
elseif ($null -ne $window -and $null -ne $screenshotPath -and $null -eq $failure)
{
    $status = 'PARTIAL_WINDOW_ONLY'
    $exitCode = 2
}

$report = New-Object System.Collections.Generic.List[string]
$report.Add('==============================================================')
$report.Add(' malilib-ui visual verification report')
$report.Add('==============================================================')
$report.Add("timestamp      : $(Get-Date -Format 'yyyy-MM-dd HH:mm:ss zzz')")
$report.Add("status         : $status")
$report.Add("version        : $Version")
$report.Add("workspace      : $($script:Root)")
$report.Add("gradle command : $gradleCommand")
$report.Add("autotest env   : MALILIB_UI_AUTOTEST=litematica")
$report.Add("gradle log     : $($script:LogPath)")
$report.Add("stderr log     : $($script:ErrPath)")
$report.Add("minecraft log  : $($script:McLogPath)")
$report.Add("elapsed        : $([int]((Get-Date) - $script:StartTime).TotalSeconds) s")
$report.Add('')

if ($null -ne $window)
{
    $hwndText = $window.Hwnd.ToInt64().ToString('X')
    $report.Add("window         : pid=$($window.Pid) hwnd=0x$hwndText title='$($window.Title)'")
    $report.Add("window bounds  : $($window.Left),$($window.Top) - $($window.Right),$($window.Bottom) ($($window.Width)x$($window.Height))")
}
else
{
    $report.Add('window         : NOT FOUND')
}

if ($shotStats.Count -gt 0)
{
    $report.Add("screenshots    : $($shotStats.Count)")
    foreach ($entry in $shotStats)
    {
        $resolved = (Resolve-Path -LiteralPath $entry.Path).Path
        $report.Add("  screenshot   : $resolved")
        $report.Add("    image      : $($entry.Stats.Width)x$($entry.Stats.Height), distinct sampled colors=$($entry.Stats.DistinctColors), avg brightness=$($entry.Stats.AverageBrightness)")
    }
}
else
{
    $report.Add('screenshot     : NOT CAPTURED')
}

if ($null -ne $titleShotPath)
{
    $report.Add("title shot     : $((Resolve-Path -LiteralPath $titleShotPath).Path)")
}

$report.Add("opened log     : $opened")
$report.Add("no-config log  : $noConfig")
$report.Add('')

if ($null -ne $failure)
{
    $report.Add("FAILURE        : $failure")
    $report.Add('')
}

if ($null -ne $captureError)
{
    $report.Add("CAPTURE ERROR  : $captureError")
    $report.Add('')
}

if ($null -ne $postClickError)
{
    $report.Add("POST-CLICK ERR : $postClickError")
    $report.Add('')
}

$report.Add('--- gradle/console log matches ([autotest] / malilib-ui / Mixin / Crash / errors) ---')
if ($logMatches.Count -eq 0)
{
    $report.Add('(no matching lines)')
}
else
{
    foreach ($line in $logMatches) { $report.Add($line) }
}

$report.Add('')
$report.Add('--- minecraft latest.log matches ---')
if ($mcLogMatches.Count -eq 0)
{
    $report.Add('(no matching lines)')
}
else
{
    foreach ($line in $mcLogMatches) { $report.Add($line) }
}

if (-not [string]::IsNullOrWhiteSpace($errText))
{
    $report.Add('')
    $report.Add('--- gradle stderr (tail) ---')
    $tail = ($errText -split "`r?`n") | Select-Object -Last 40
    foreach ($line in $tail) { $report.Add($line) }
}

$reportText = $report -join [Environment]::NewLine

New-Item -ItemType Directory -Force -Path $script:RunDir | Out-Null
$reportText | Set-Content -LiteralPath $script:ReportPath -Encoding UTF8

Write-Host ''
Write-Host $reportText
Write-Host ''
Write-Info "Report written to: $($script:ReportPath)"

if ($exitCode -ne 0)
{
    Write-Info "Verification finished with status $status (exit code $exitCode)."
}
else
{
    Write-Info "Verification finished with status $status."
}

exit $exitCode
