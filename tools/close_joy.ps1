
Add-Type @"
using System;
using System.Runtime.InteropServices;
public static class WinCloser {
    [DllImport("user32.dll", CharSet=CharSet.Unicode)] static extern IntPtr FindWindow(string cls, string title);
    [DllImport("user32.dll")] static extern bool PostMessage(IntPtr h, uint msg, IntPtr w, IntPtr l);
    public static bool Close(string title) {
        IntPtr h = FindWindow(null, title);
        if (h == IntPtr.Zero) return false;
        PostMessage(h, 0x0010, IntPtr.Zero, IntPtr.Zero);
        return true;
    }
}
"@
"closed=" + [WinCloser]::Close("游戏控制器")
