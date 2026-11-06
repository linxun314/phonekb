
using System;
using System.Runtime.InteropServices;

public static class DiTry {
    [DllImport("dinput8.dll", CharSet = CharSet.Unicode)]
    static extern int DirectInput8Create(IntPtr hinst, uint version, ref Guid riid, out IntPtr outPtr, IntPtr outer);
    [DllImport("kernel32.dll", CharSet = CharSet.Unicode)]
    static extern IntPtr GetModuleHandle(string name);

    public static string Try() {
        var sb = new System.Text.StringBuilder();
        string[] ids = { "BF798030-483A-4DA2-99BD-480587061268", "BF798031-483A-4DA2-99BD-480587061268" };
        foreach (var s in ids) {
            var g = new Guid(s);
            IntPtr p;
            int hr = DirectInput8Create(GetModuleHandle(null), 0x0800, ref g, out p, IntPtr.Zero);
            sb.AppendLine(s + " -> 0x" + hr.ToString("X8"));
        }
        // also try hinst = IntPtr.Zero
        var g2 = new Guid("BF798031-483A-4DA2-99BD-480587061268");
        IntPtr p2;
        int hr2 = DirectInput8Create(IntPtr.Zero, 0x0800, ref g2, out p2, IntPtr.Zero);
        sb.AppendLine("hinst=0 -> 0x" + hr2.ToString("X8"));
        return sb.ToString();
    }
}
