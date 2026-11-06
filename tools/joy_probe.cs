
using System;
using System.Runtime.InteropServices;

public static class JoyProbe {
    [StructLayout(LayoutKind.Sequential)]
    public struct JOYCAPS {
        public ushort wMid, wPid;
        [MarshalAs(UnmanagedType.ByValTStr, SizeConst = 32)] public string szPname;
        public uint wXmin, wXmax, wYmin, wYmax, wZmin, wZmax;
        public uint wNumButtons, wPeriodMin, wPeriodMax;
        public uint wRmin, wRmax, wUmin, wUmax, wVmin, wVmax;
        public uint wCaps, wMaxAxes, wNumAxes, wMaxButtons;
        [MarshalAs(UnmanagedType.ByValTStr, SizeConst = 32)] public string szRegKey;
        [MarshalAs(UnmanagedType.ByValTStr, SizeConst = 260)] public string szOEMVxD;
    }

    [StructLayout(LayoutKind.Sequential)]
    public struct JOYINFOEX {
        public uint dwSize, dwFlags, dwXpos, dwYpos, dwZpos, dwRpos, dwUpos, dwVpos;
        public uint dwButtons, dwButtonNumber, dwPOV, dwReserved1, dwReserved2;
    }

    [DllImport("winmm.dll")] static extern uint joyGetNumDevs();
    [DllImport("winmm.dll", CharSet = CharSet.Unicode)] static extern uint joyGetDevCapsW(IntPtr id, ref JOYCAPS caps, int size);
    [DllImport("winmm.dll")] static extern uint joyGetPosEx(uint id, ref JOYINFOEX info);

    public static string Dump() {
        var sb = new System.Text.StringBuilder();
        uint n = joyGetNumDevs();
        sb.AppendLine("joyGetNumDevs=" + n);
        for (uint id = 0; id < n; id++) {
            var caps = new JOYCAPS();
            uint rc = joyGetDevCapsW((IntPtr)id, ref caps, Marshal.SizeOf(typeof(JOYCAPS)));
            if (rc != 0) { sb.AppendLine("id " + id + " not present rc=" + rc); continue; }
            var info = new JOYINFOEX();
            info.dwSize = (uint)Marshal.SizeOf(typeof(JOYINFOEX));
            info.dwFlags = 0xFF;
            uint r2 = joyGetPosEx(id, ref info);
            string pov = info.dwPOV == 0xFFFFFFFF ? "centered" : (info.dwPOV / 100.0).ToString("0") + " deg";
            sb.AppendLine("id=" + id + " name=\"" + caps.szPname + "\" vid=" + caps.wMid + " pid=" + caps.wPid +
                " axes=" + caps.wNumAxes + " buttons=" + caps.wNumButtons +
                " | pos rc=" + r2 + " X=" + info.dwXpos + " Y=" + info.dwYpos + " Z=" + info.dwZpos +
                " R=" + info.dwRpos + " U=" + info.dwUpos + " V=" + info.dwVpos +
                " buttonsMask=0x" + info.dwButtons.ToString("X") + " POV=" + pov);
        }
        return sb.ToString();
    }
}
