
using System;
using System.Collections.Generic;
using System.Runtime.InteropServices;
using System.Threading;

public static class HidWatch {
    const int DIGCF_PRESENT = 0x02;
    const int DIGCF_DEVICEINTERFACE = 0x10;
    const int GENERIC_READ = unchecked((int)0x80000000), GENERIC_WRITE = 0x40000000;
    const int FILE_SHARE_RW = 0x03, OPEN_EXISTING = 3;
    const int HIDP_INPUT = 0;
    const int HIDP_STATUS_SUCCESS = 0x00110000;

    [StructLayout(LayoutKind.Sequential)]
    struct SP_DEVICE_INTERFACE_DATA { public int cbSize; public Guid g; public int flags; public IntPtr reserved; }

    [StructLayout(LayoutKind.Sequential)]
    struct HIDP_CAPS {
        public short Usage, UsagePage, InLen, OutLen, FeatLen;
        [MarshalAs(UnmanagedType.ByValArray, SizeConst = 17)] public short[] Reserved;
        public int Nodes, InBtnCaps, InValCaps, InDataIndices, OutBtnCaps, OutValCaps, OutDataIndices, FeatBtnCaps, FeatValCaps, FeatDataIndices;
    }

    [DllImport("setupapi.dll", CharSet = CharSet.Unicode, SetLastError = true)]
    static extern IntPtr SetupDiGetClassDevs(ref Guid g, IntPtr e, IntPtr h, int f);
    [DllImport("setupapi.dll", CharSet = CharSet.Unicode, SetLastError = true)]
    static extern bool SetupDiEnumDeviceInterfaces(IntPtr set, IntPtr info, ref Guid g, int idx, ref SP_DEVICE_INTERFACE_DATA d);
    [DllImport("setupapi.dll", CharSet = CharSet.Unicode, SetLastError = true)]
    static extern bool SetupDiGetDeviceInterfaceDetail(IntPtr set, ref SP_DEVICE_INTERFACE_DATA d, IntPtr detail, int size, ref int needed, IntPtr info);
    [DllImport("setupapi.dll")] static extern bool SetupDiDestroyDeviceInfoList(IntPtr set);

    [DllImport("kernel32.dll", CharSet = CharSet.Unicode, SetLastError = true)]
    static extern IntPtr CreateFile(string name, int access, int share, IntPtr sec, int disp, int flags, IntPtr tmpl);
    [DllImport("kernel32.dll", SetLastError = true)] static extern bool CloseHandle(IntPtr h);
    [DllImport("kernel32.dll", SetLastError = true)] static extern bool ReadFile(IntPtr h, byte[] buf, int n, out int read, IntPtr ov);
    [DllImport("hid.dll")] static extern bool HidD_GetPreparsedData(IntPtr h, out IntPtr data);
    [DllImport("hid.dll")] static extern bool HidD_FreePreparsedData(IntPtr data);
    [DllImport("hid.dll")] static extern int HidP_GetCaps(IntPtr data, ref HIDP_CAPS caps);
    [DllImport("hid.dll")] static extern int HidP_GetUsages(int type, int page, int link, int[] list, ref int len, IntPtr data, byte[] report, int reportLen);
    [DllImport("hid.dll")] static extern int HidP_GetUsageValue(int type, int page, int link, int usage, out int value, IntPtr data, byte[] report, int reportLen);

    static IntPtr Open(string path, int access) {
        IntPtr h = CreateFile(path, access, FILE_SHARE_RW, IntPtr.Zero, OPEN_EXISTING, 0, IntPtr.Zero);
        if (h == new IntPtr(-1)) h = CreateFile(path, 0, FILE_SHARE_RW, IntPtr.Zero, OPEN_EXISTING, 0, IntPtr.Zero);
        return h;
    }

    public static List<string> FindGamepads() {
        var found = new List<string>();
        Guid guid = new Guid("4D1E55B2-F16F-11CF-88CB-001111000030");
        IntPtr set = SetupDiGetClassDevs(ref guid, IntPtr.Zero, IntPtr.Zero, DIGCF_PRESENT | DIGCF_DEVICEINTERFACE);
        if (set == new IntPtr(-1)) return found;
        var d = new SP_DEVICE_INTERFACE_DATA();
        d.cbSize = Marshal.SizeOf(typeof(SP_DEVICE_INTERFACE_DATA));
        for (int i = 0; SetupDiEnumDeviceInterfaces(set, IntPtr.Zero, ref guid, i, ref d); i++) {
            int needed = 0;
            SetupDiGetDeviceInterfaceDetail(set, ref d, IntPtr.Zero, 0, ref needed, IntPtr.Zero);
            if (needed <= 0) continue;
            IntPtr buf = Marshal.AllocHGlobal(needed);
            Marshal.WriteInt32(buf, IntPtr.Size == 8 ? 8 : 6);
            if (SetupDiGetDeviceInterfaceDetail(set, ref d, buf, needed, ref needed, IntPtr.Zero)) {
                string path = Marshal.PtrToStringUni(new IntPtr(buf.ToInt64() + 4));
                if (path != null && IsGamepad(path)) found.Add(path);
            }
            Marshal.FreeHGlobal(buf);
        }
        SetupDiDestroyDeviceInfoList(set);
        return found;
    }

    static bool IsGamepad(string path) {
        IntPtr h = Open(path, GENERIC_READ | GENERIC_WRITE);
        if (h == new IntPtr(-1)) return false;
        IntPtr pp = IntPtr.Zero;
        bool ok = false;
        if (HidD_GetPreparsedData(h, out pp)) {
            var caps = new HIDP_CAPS();
            if (HidP_GetCaps(pp, ref caps) == HIDP_STATUS_SUCCESS)
                ok = caps.UsagePage == 1 && (caps.Usage == 4 || caps.Usage == 5);
            HidD_FreePreparsedData(pp);
        }
        CloseHandle(h);
        return ok;
    }

    static volatile bool stop = false;

    public static void Watch(string path, int seconds) {
        IntPtr h = Open(path, GENERIC_READ | GENERIC_WRITE);
        if (h == new IntPtr(-1)) { Console.WriteLine("open failed: " + path); return; }
        IntPtr pp = IntPtr.Zero;
        HidD_GetPreparsedData(h, out pp);
        var caps = new HIDP_CAPS();
        HidP_GetCaps(pp, ref caps);
        Console.WriteLine("caps usagePage=" + caps.UsagePage + " usage=" + caps.Usage + " inLen=" + caps.InLen + " btnCaps=" + caps.InBtnCaps + " valCaps=" + caps.InValCaps);
        byte[] report = new byte[Math.Max(64, caps.InLen + 8)];
        int[] usages = new int[64];
        string last = "<none>";
        stop = false;
        var t = new Thread(() => {
            while (!stop) {
                int got = 0;
                if (!ReadFile(h, report, report.Length, out got, IntPtr.Zero)) { Thread.Sleep(100); continue; }
                if (got <= 0) continue;
                int len = 64;
                var s = "read " + got + " bytes  ";
                if (HidP_GetUsages(HIDP_INPUT, 9, 0, usages, ref len, pp, report, got) == HIDP_STATUS_SUCCESS) {
                    var b = new List<int>();
                    for (int i = 0; i < len; i++) b.Add(usages[i]);
                    b.Sort();
                    s += "buttons=[" + string.Join(",", b) + "]";
                }
                int hat = -1;
                HidP_GetUsageValue(HIDP_INPUT, 1, 0, 0x39, out hat, pp, report, got);
                s += " hat=" + hat;
                int x = 0, y = 0;
                HidP_GetUsageValue(HIDP_INPUT, 1, 0, 0x30, out x, pp, report, got);
                HidP_GetUsageValue(HIDP_INPUT, 1, 0, 0x31, out y, pp, report, got);
                s += " x=" + x + " y=" + y;
                if (s != last) { Console.WriteLine(DateTime.Now.ToString("HH:mm:ss.fff") + "  " + s); last = s; }
            }
        });
        t.IsBackground = true;
        t.Start();
        Thread.Sleep(seconds * 1000);
        stop = true;
        CloseHandle(h);
        Thread.Sleep(300);
    }
// ---------------------------------------------------------------- mouse

    /** Interfaces whose top-level usage matches (2 = mouse, 4/5 = joystick/gamepad). */
    public static List<string> FindByUsage(int usage) {
        var found = new List<string>();
        Guid guid = new Guid("4D1E55B2-F16F-11CF-88CB-001111000030");
        IntPtr set = SetupDiGetClassDevs(ref guid, IntPtr.Zero, IntPtr.Zero, DIGCF_PRESENT | DIGCF_DEVICEINTERFACE);
        if (set == new IntPtr(-1)) return found;
        var d = new SP_DEVICE_INTERFACE_DATA();
        d.cbSize = Marshal.SizeOf(typeof(SP_DEVICE_INTERFACE_DATA));
        for (int i = 0; SetupDiEnumDeviceInterfaces(set, IntPtr.Zero, ref guid, i, ref d); i++) {
            int needed = 0;
            SetupDiGetDeviceInterfaceDetail(set, ref d, IntPtr.Zero, 0, ref needed, IntPtr.Zero);
            if (needed <= 0) continue;
            IntPtr buf = Marshal.AllocHGlobal(needed);
            Marshal.WriteInt32(buf, IntPtr.Size == 8 ? 8 : 6);
            if (SetupDiGetDeviceInterfaceDetail(set, ref d, buf, needed, ref needed, IntPtr.Zero)) {
                string path = Marshal.PtrToStringUni(new IntPtr(buf.ToInt64() + 4));
                if (path != null && UsageOf(path) == usage) found.Add(path);
            }
            Marshal.FreeHGlobal(buf);
        }
        SetupDiDestroyDeviceInfoList(set);
        return found;
    }

    static int UsageOf(string path) {
        IntPtr h = Open(path, GENERIC_READ | GENERIC_WRITE);
        if (h == new IntPtr(-1)) return -1;
        IntPtr pp = IntPtr.Zero;
        int usage = -1;
        if (HidD_GetPreparsedData(h, out pp)) {
            var caps = new HIDP_CAPS();
            if (HidP_GetCaps(pp, ref caps) == HIDP_STATUS_SUCCESS && caps.UsagePage == 1) usage = caps.Usage;
            HidD_FreePreparsedData(pp);
        }
        CloseHandle(h);
        return usage;
    }

    /**
     * Counts mouse reports for N seconds and prints the cadence (reports/s) plus the gap between
     * consecutive reports, which is what tells us whether the host is keeping up with the phone.
     */
    public static void WatchMouse(string path, int seconds) {
        IntPtr h = Open(path, GENERIC_READ | GENERIC_WRITE);
        if (h == new IntPtr(-1)) { Console.WriteLine("open failed: " + path); return; }
        IntPtr pp = IntPtr.Zero;
        HidD_GetPreparsedData(h, out pp);
        var caps = new HIDP_CAPS();
        HidP_GetCaps(pp, ref caps);
        Console.WriteLine("mouse caps usagePage=" + caps.UsagePage + " usage=" + caps.Usage + " inLen=" + caps.InLen);
        byte[] report = new byte[Math.Max(64, caps.InLen + 8)];
        var gaps = new List<double>();
        int total = 0;
        long lastAt = 0;
        stop = false;
        var t = new Thread(() => {
            while (!stop) {
                int got = 0;
                if (!ReadFile(h, report, report.Length, out got, IntPtr.Zero)) { Thread.Sleep(50); continue; }
                if (got <= 0) continue;
                long now = DateTime.Now.Ticks / 10000;
                if (lastAt != 0) gaps.Add(now - lastAt);
                lastAt = now;
                total++;
            }
        });
        t.IsBackground = true;
        t.Start();
        long started = DateTime.Now.Ticks / 10000;
        for (int i = 0; i < seconds; i++) {
            Thread.Sleep(1000);
            long elapsed = DateTime.Now.Ticks / 10000 - started;
            Console.WriteLine("t=" + (elapsed / 1000) + "s  reports=" + total + "  rate=" + Math.Round(total * 1000.0 / Math.Max(1, elapsed), 1) + "/s");
        }
        stop = true;
        CloseHandle(h);
        Thread.Sleep(200);
        if (gaps.Count > 0) {
            gaps.Sort();
            Console.WriteLine("gaps ms  min=" + Math.Round(gaps[0], 1)
                    + "  p50=" + Math.Round(gaps[gaps.Count / 2], 1)
                    + "  p95=" + Math.Round(gaps[(int)(gaps.Count * 0.95)], 1)
                    + "  max=" + Math.Round(gaps[gaps.Count - 1], 1));
        }
    }
}
