
using System;
using System.Runtime.InteropServices;

public static class DInputProbe {
    [StructLayout(LayoutKind.Sequential)]
    public struct DIJOYSTATE {
        public int lX, lY, lZ, lRx, lRy, lRz, lSlider0, lSlider1;
        [MarshalAs(UnmanagedType.ByValArray, SizeConst = 32)] public byte[] rgbButtons;
        public uint dwPOV, dwReserved1, dwReserved2;
    }

    [StructLayout(LayoutKind.Sequential, CharSet = CharSet.Unicode)]
    public struct DIDEVICEINSTANCEW {
        public int dwSize;
        public Guid guidInstance;
        public Guid guidProduct;
        public int dwDevType;
        [MarshalAs(UnmanagedType.ByValTStr, SizeConst = 260)] public string tszInstanceName;
        [MarshalAs(UnmanagedType.ByValTStr, SizeConst = 260)] public string tszProductName;
        public Guid guidFFDriver;
        public ushort wUsagePage, wUsage;
    }

    [DllImport("dinput8.dll", CharSet = CharSet.Unicode)]
    static extern int DirectInput8Create(IntPtr hinst, uint version, ref Guid riid, out IntPtr outPtr, IntPtr outer);
    [DllImport("kernel32.dll", CharSet = CharSet.Ansi, SetLastError = true)]
    static extern IntPtr GetProcAddress(IntPtr module, string name);
    [DllImport("kernel32.dll", CharSet = CharSet.Unicode, SetLastError = true)]
    static extern IntPtr GetModuleHandle(string name);
    [DllImport("kernel32.dll", CharSet = CharSet.Unicode, SetLastError = true)]
    static extern IntPtr LoadLibrary(string name);

    const int DI8DEVCLASS_GAMECTRL = 4;
    const int DIEDFL_ATTACHEDONLY = 1;
    const int DI_OK = 0;

    delegate int EnumCallback(ref DIDEVICEINSTANCEW inst, IntPtr ctx);

    // IDirectInput8W vtable slots (after IUnknown): CreateDevice=3, EnumDevices=4, GetDeviceStatus=5, ...
    public static string Probe(IntPtr hwnd) {
        Guid iid = new Guid("BF798031-483A-4DA2-99BD-480587061268");
        IntPtr di;
        int hr = DirectInput8Create(GetModuleHandle(null), 0x0800, ref iid, out di, IntPtr.Zero);
        if (hr != DI_OK) return "DirectInput8Create failed 0x" + hr.ToString("X8");

        var names = new System.Collections.Generic.List<string>();
        var guids = new System.Collections.Generic.List<Guid>();
        EnumCallback cb = delegate(ref DIDEVICEINSTANCEW inst, IntPtr ctx) {
            names.Add(inst.tszProductName);
            guids.Add(inst.guidInstance);
            return 1; // DIENUM_CONTINUE
        };
        var vt = Marshal.GetDelegateForFunctionPointer<VtblEnum>(Marshal.ReadIntPtr(Marshal.ReadIntPtr(di), 4 * IntPtr.Size));
        hr = vt(di, DI8DEVCLASS_GAMECTRL, cb, IntPtr.Zero, DIEDFL_ATTACHEDONLY);
        var sb = new System.Text.StringBuilder();
        sb.AppendLine("enum hr=0x" + hr.ToString("X8") + " devices=" + names.Count);
        for (int i = 0; i < names.Count; i++) sb.AppendLine("  [" + i + "] " + names[i]);

        IntPtr c_df = GetProcAddress(LoadLibrary("dinput8.dll"), "c_dfDIJoystick2");
        sb.AppendLine("c_dfDIJoystick2=0x" + c_df.ToInt64().ToString("X"));
        if (c_df == IntPtr.Zero) return sb.ToString();

        for (int i = 0; i < guids.Count; i++) {
            Guid g = guids[i];
            IntPtr dev;
            var createDev = Marshal.GetDelegateForFunctionPointer<VtblCreateDevice>(Marshal.ReadIntPtr(Marshal.ReadIntPtr(di), 3 * IntPtr.Size));
            hr = createDev(di, ref g, out dev, IntPtr.Zero);
            if (hr != DI_OK) { sb.AppendLine("  create[" + i + "] failed 0x" + hr.ToString("X8")); continue; }
            var devVt = Marshal.ReadIntPtr(dev);
            var setFormat = Marshal.GetDelegateForFunctionPointer<VtblIntPtr>(Marshal.ReadIntPtr(devVt, 12 * IntPtr.Size));
            hr = setFormat(dev, c_df);
            sb.AppendLine("  [" + i + "] SetDataFormat=0x" + hr.ToString("X8"));
            if (hr != DI_OK) { continue; }
            var setCoop = Marshal.GetDelegateForFunctionPointer<VtblCoop>(Marshal.ReadIntPtr(devVt, 14 * IntPtr.Size));
            hr = setCoop(dev, hwnd, 0x02 | 0x10); // DISCL_BACKGROUND | DISCL_NONEXCLUSIVE
            sb.AppendLine("  [" + i + "] SetCooperativeLevel=0x" + hr.ToString("X8"));
            var acquire = Marshal.GetDelegateForFunctionPointer<VtblNoArg>(Marshal.ReadIntPtr(devVt, 8 * IntPtr.Size));
            hr = acquire(dev);
            sb.AppendLine("  [" + i + "] Acquire=0x" + hr.ToString("X8"));
            var poll = Marshal.GetDelegateForFunctionPointer<VtblNoArg>(Marshal.ReadIntPtr(devVt, 26 * IntPtr.Size));
            poll(dev);
            IntPtr statePtr = Marshal.AllocHGlobal(272);
            for (int k = 0; k < 272; k++) Marshal.WriteByte(statePtr, k, 0);
            var getState = Marshal.GetDelegateForFunctionPointer<VtblState>(Marshal.ReadIntPtr(devVt, 10 * IntPtr.Size));
            hr = getState(dev, 272, statePtr);
            var st = (DIJOYSTATE)Marshal.PtrToStructure(statePtr, typeof(DIJOYSTATE));
            Marshal.FreeHGlobal(statePtr);
            var pressed = new System.Text.StringBuilder();
            for (int b = 0; b < 32; b++) if (st.rgbButtons[b] != 0) pressed.Append((b + 1) + " ");
            string pov = st.dwPOV == 0xFFFFFFFF ? "centered" : (st.dwPOV / 100.0).ToString("0") + "deg";
            sb.AppendLine("  [" + i + "] GetDeviceState=0x" + hr.ToString("X8") +
                " X=" + st.lX + " Y=" + st.lY + " Z=" + st.lZ + " Rx=" + st.lRx + " Ry=" + st.lRy +
                " POV=" + pov + " buttons=[" + pressed.ToString().Trim() + "]");
        }
        return sb.ToString();
    }

    [UnmanagedFunctionPointer(CallingConvention.StdCall)]
    delegate int VtblEnum(IntPtr self, int devClass, EnumCallback cb, IntPtr ctx, int flags);
    [UnmanagedFunctionPointer(CallingConvention.StdCall)]
    delegate int VtblCreateDevice(IntPtr self, ref Guid rguid, out IntPtr dev, IntPtr outer);
    [UnmanagedFunctionPointer(CallingConvention.StdCall)]
    delegate int VtblIntPtr(IntPtr self, IntPtr p);
    [UnmanagedFunctionPointer(CallingConvention.StdCall)]
    delegate int VtblCoop(IntPtr self, IntPtr hwnd, int flags);
    [UnmanagedFunctionPointer(CallingConvention.StdCall)]
    delegate int VtblNoArg(IntPtr self);
    [UnmanagedFunctionPointer(CallingConvention.StdCall)]
    delegate int VtblState(IntPtr self, int cb, IntPtr data);
}
