package com.phonekb;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothHidDevice;
import android.bluetooth.BluetoothHidDeviceAppSdpSettings;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Process;
import android.os.SystemClock;
import android.util.Log;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Owns the Android Bluetooth HID device (peripheral) profile and sends input reports.
 *
 * <p>Process-wide singleton so that the foreground keep-alive service and every page
 * share the same HID registration. Report layouts intentionally match the Bluetooth
 * Remote app (keyboard report id 1 with a 2-byte payload, consumer report id 2,
 * mouse report id 3) so that a host which cached that app's descriptor still
 * decodes our reports correctly.</p>
 */
public final class HidManager {
    private static final String TAG = "PhoneKB-HID";
    /** Set to true only while debugging: per-report logging costs latency. */
    private static final boolean DEBUG = false;

    private static volatile HidManager instance;

    public interface Listener {
        void onHidStatus(int state, String title, String detail);
    }

    public static final int STATE_UNKNOWN = 0;
    public static final int STATE_REGISTERING = 1;
    public static final int STATE_WAITING = 2;
    public static final int STATE_CONNECTED = 3;
    public static final int STATE_ERROR = 4;

    public static HidManager get(Context context) {
        HidManager local = instance;
        if (local == null) {
            synchronized (HidManager.class) {
                local = instance;
                if (local == null) {
                    local = new HidManager(context.getApplicationContext());
                    instance = local;
                }
            }
        }
        return local;
    }

    private final Context context;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService btExecutor = Executors.newSingleThreadExecutor();
    /**
     * Every input report is pushed from this single thread instead of the caller (UI) thread, so a
     * slow Bluetooth stack call can never stall touch dispatch. Threads are created at max priority
     * to shave scheduling latency off each report.
     */
    private final ExecutorService sendExecutor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(() -> {
            // Audio-class priority: a report hand-off should never wait behind background work.
            android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_URGENT_AUDIO);
            r.run();
        }, "phonekb-hid-send");
        t.setPriority(Thread.MAX_PRIORITY);
        return t;
    });

    // ---- report coalescing (v0.26 latency work) -------------------------------------------
    /**
     * Only the newest payload per report id is kept. When the touch screen produces reports faster
     * than Bluetooth can drain them, older states are dropped instead of being queued, so a button
     * press can never end up behind a backlog of stale joystick/touchpad frames.
     */
    private final Object pendingLock = new Object();
    private final java.util.HashMap<Integer, byte[]> pendingReports = new java.util.HashMap<>();
    private boolean flushQueued;
    private long lastGamepadSentAt;
    private int lastGamepadButtons = -1;
    private int lastGamepadHat = -1;
    /** Hosts poll the HID channel at ~125 Hz; sending faster only burns the Bluetooth queue. */
    private static final long MIN_GAMEPAD_GAP_MS = 4;
    /** Debug switch: logs submit/sent timestamps so a host getevent run can be time-aligned. */
    private static volatile boolean timingLog;
    private static volatile int dropCount;
    private static volatile int retryCount;

    public static void setTimingLog(boolean on) {
        timingLog = on;
    }

    /** A/B switch: true restores the pre-0.26 fire-and-forget sender (used to measure the gain). */
    private static volatile boolean legacySend;

    public static void setLegacySend(boolean on) {
        legacySend = on;
    }

    public static int takeDropCount() {
        int n = dropCount;
        dropCount = 0;
        return n;
    }

    public static int takeRetryCount() {
        int n = retryCount;
        retryCount = 0;
        return n;
    }
    // ---- pointer latency statistics (v0.32): submit timestamp -> sendReport return --------------
    private static final int STAT_N = 128;
    private static final long[] statSample = new long[STAT_N];
    private static final int[] statCost = new int[STAT_N];
    private static int statCount;
    private static int statCursor;

    /** Records one pointer report: how long it took from the touch event to reaching Bluetooth. */
    private static void recordPointerLatency(long eventTimeMs, int costMs) {
        long now = SystemClock.uptimeMillis();
        long age = now - eventTimeMs;
        if (age < 0) age = 0;
        if (rateStartAt == 0L) rateStartAt = now;
        rateCount++;
        synchronized (statSample) {
            statSample[statCursor] = age;
            statCost[statCursor] = costMs;
            statCursor = (statCursor + 1) % STAT_N;
            if (statCount < STAT_N) statCount++;
        }
    }

    /** Last pacing gap actually used, so the timing log can show the cadence we are running at. */
    static final class MouseStats { static long lastGap; }

    private static long rateStartAt;
    private static int rateCount;

    /** Mouse reports actually dispatched since the last summary — the cadence we are pacing at. */
    private static String takeReportRate() {
        long now = SystemClock.uptimeMillis();
        long span = now - rateStartAt;
        int count = rateCount;
        rateStartAt = now;
        rateCount = 0;
        if (span <= 0L || count <= 0) return "节奏 —";
        return "节奏 " + Math.round(count * 1000f / span) + " 份/秒";
    }

    /** "p50/p95 age, p50/p95 send cost, paced report rate" — null when nothing was measured yet. */
    public static String takePointerLatencySummary() {
        long[] age;
        int[] cost;
        int n;
        synchronized (statSample) {
            n = statCount;
            age = new long[n];
            cost = new int[n];
            for (int i = 0; i < n; i++) {
                age[i] = statSample[i];
                cost[i] = statCost[i];
            }
            statCount = 0;
            statCursor = 0;
        }
        if (n == 0) return null;
        java.util.Arrays.sort(age);
        java.util.Arrays.sort(cost);
        return "n=" + n
                + " age p50=" + age[n / 2] + "ms p95=" + age[Math.min(n - 1, (int) (n * 0.95))] + "ms"
                + " cost p50=" + cost[n / 2] + "ms p95=" + cost[Math.min(n - 1, (int) (n * 0.95))] + "ms"
                + " · " + takeReportRate();
    }

    private final AtomicInteger pendDx = new AtomicInteger();
    private final AtomicInteger pendDy = new AtomicInteger();
    private volatile long pendEventTime;
    private final AtomicBoolean mouseFlushQueued = new AtomicBoolean(false);
    /**
     * The coalescing timer runs on its own thread. It used to be posted on the main looper, which
     * meant a busy UI thread (drawing the page every frame) could push every flush back by a whole
     * frame - that alone was up to ~16 ms of jitter on top of Bluetooth latency.
     */
    private volatile Handler mouseHandler;
    private volatile long lastMouseFlushAt;
    /** Hard floor for the pacing gap, so a bad pref cannot turn the link into a packet storm. */
    private static final long MOUSE_GAP_MIN_MS = 1;
    /**
     * Minimum gap between two mouse reports — the cadence we pace at.
     *
     * <p>A Bluetooth HID interrupt channel runs at roughly one report per 11 ms on Windows and on
     * most hosts. Pushing 240 Hz bursts at that link only builds a queue inside the Bluetooth
     * stack: the host keeps receiving stale reports, so the cursor trails behind the finger and
     * then catches up when the finger stops. Deltas are still accumulated between sends, so
     * pacing to the link rate loses no movement — it just spends one report per link slot.
     * Configurable in 更多设置 (2 / 6 / 9 / 12 ms).
     */
    public static volatile long mouseGapMs = 9;
    private BluetoothAdapter adapter;
    private BluetoothHidDevice hid;
    private volatile BluetoothDevice host;
    private volatile int state = STATE_UNKNOWN;
    private volatile String error = "";
    private volatile String statusTitle = "正在启动…";
    private volatile String statusDetail = "请稍候";
    private Listener listener;
    private boolean proxyRequested;
    private volatile boolean appRegistered;
    private volatile boolean everRegistered;
    private int registerGeneration;
    private int reconnectAttempts;
    /** How many times a dropped link is re-established before we stop and ask the user. */
    private static final int RECONNECT_MAX = 8;
    /** Longest backoff between two reconnect attempts. */
    private static final long RECONNECT_MAX_DELAY_MS = 30_000L;
    /** Remembers the last host so a cold start (or a status tap) can reconnect without re-pairing. */
    private static final String PREF_LAST_HOST = "last_host";
    /** The host the user picked in the device list; we always try to stay on it. */
    private static final String PREF_PREFERRED_HOST = "preferred_host";
    private static final long GUARD_COOLDOWN_MS = 15_000L;
    private long lastSendLogAt;

    private final BluetoothProfile.ServiceListener proxyListener = new BluetoothProfile.ServiceListener() {
        @Override public void onServiceConnected(int profile, BluetoothProfile proxy) {
            if (profile != BluetoothProfile.HID_DEVICE) return;
            hid = (BluetoothHidDevice) proxy;
            registerApp();
        }
        @Override public void onServiceDisconnected(int profile) {
            hid = null;
            host = null;
            appRegistered = false;
            proxyRequested = false;
            post(STATE_ERROR, "HID 服务已断开", "请关闭再打开蓝牙后重试（点状态文字可重试）");
        }
    };

    private final BluetoothHidDevice.Callback callback = new BluetoothHidDevice.Callback() {
        @Override public void onAppStatusChanged(BluetoothDevice pluggedDevice, boolean registered) {
            Log.i(TAG, "appStatus registered=" + registered + " plugged=" + nameOf(pluggedDevice));
            appRegistered = registered;
            if (!registered) {
                if (everRegistered) {
                    post(STATE_WAITING, "正在重新注册", "HID 注册被系统回收，自动重试中…");
                    scheduleReRegister();
                } else {
                    post(STATE_ERROR, "HID 注册失败", "可能已有别的 HID 键盘应用占用，请先强制停止它再重试");
                }
                return;
            }
            everRegistered = true;
            reconnectAttempts = 0;
            if (pluggedDevice != null) {
                host = pluggedDevice;
                rememberHost(pluggedDevice);
                rememberMeta(pluggedDevice);
                if (switchingTo != null && switchingTo.equals(pluggedDevice)) switchingTo = null;
                post(STATE_CONNECTED, "已连接", nameOf(pluggedDevice));
                main.postDelayed(HidManager.this::releaseAllInputs, 300);
            } else {
                // Cold start: if we already know a bonded host, try to pull the link up by ourselves
                // instead of making the user walk to the tablet's Bluetooth settings every time.
                BluetoothDevice remembered = lastHostDevice();
                if (remembered != null && bondStateOf(remembered) == BluetoothDevice.BOND_BONDED) {
                    scheduleReconnect(remembered);
                } else {
                    post(STATE_WAITING, "等待设备连接", "已注册 HID，请在平板 / 电脑的蓝牙中连接手机");
                }
            }
        }
        @Override public void onConnectionStateChanged(BluetoothDevice device, int newState) {
            Log.i(TAG, "conn " + newState + " " + nameOf(device));
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                host = device;
                reconnectAttempts = 0;
                rememberHost(device);
                rememberMeta(device);
                if (switchingTo != null && switchingTo.equals(device)) switchingTo = null;
                post(STATE_CONNECTED, "已连接", nameOf(device));
                main.postDelayed(HidManager.this::releaseAllInputs, 300);
                enforcePreferredHost(device);
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                if (host != null && host.equals(device)) host = null;
                if (manualDisconnect) {
                    post(STATE_WAITING, "已断开", "点「切换」连回来");
                    return;
                }
                post(STATE_WAITING, "连接已断开", "自动重连中…");
                scheduleReconnect(device);
            }
        }
        @Override public void onGetReport(BluetoothDevice device, byte type, byte id, int bufferSize) {
            try {
                BluetoothHidDevice d = hid;
                if (d != null) d.replyReport(device, type, id, new byte[Math.max(0, bufferSize)]);
            } catch (Exception ignored) { }
        }
        @Override public void onSetReport(BluetoothDevice device, byte type, byte id, byte[] data) {
            try {
                BluetoothHidDevice d = hid;
                if (d != null) d.replyReport(device, type, id, data == null ? new byte[0] : data);
            } catch (Exception ignored) { }
        }
        @Override public void onVirtualCableUnplug(BluetoothDevice device) {
            Log.i(TAG, "virtual cable unplug " + nameOf(device));
            rememberHost(device);
            host = null;
            if (manualDisconnect) {
                post(STATE_WAITING, "已断开", "点「切换」连回来");
                return;
            }
            post(STATE_WAITING, "连接已断开", "自动重连中…");
            scheduleReconnect(device);
        }
    };

    private HidManager(Context context) {
        this.context = context;
    }

    public void setListener(Listener l) {
        this.listener = l;
    }

    public int getState() {
        return state;
    }

    public String getError() {
        return error;
    }

    public String getStatusTitle() {
        return statusTitle;
    }

    public String getStatusDetail() {
        return statusDetail;
    }

    public BluetoothAdapter getAdapter() {
        return adapter;
    }

    public BluetoothDevice getHost() {
        return host;
    }

    public boolean isConnected() {
        return state == STATE_CONNECTED && host != null;
    }

    public void start() {
        BluetoothManager bm = (BluetoothManager) context.getSystemService(Context.BLUETOOTH_SERVICE);
        adapter = bm == null ? null : bm.getAdapter();
        if (adapter == null) {
            post(STATE_ERROR, "没有蓝牙", "此设备不支持蓝牙");
            return;
        }
        try {
            if (!adapter.isEnabled()) {
                post(STATE_ERROR, "蓝牙未开启", "请打开蓝牙后重试");
                return;
            }
        } catch (SecurityException e) {
            post(STATE_ERROR, "缺少蓝牙权限", "请允许“附近的设备”权限后重试");
            return;
        }
        if (hid != null) {
            if (!appRegistered) registerApp();
            return;
        }
        post(STATE_REGISTERING, "正在初始化", "注册蓝牙 HID 设备…");
        if (proxyRequested) return;
        proxyRequested = true;
        try {
            boolean ok = adapter.getProfileProxy(context, proxyListener, BluetoothProfile.HID_DEVICE);
            if (!ok) {
                proxyRequested = false;
                post(STATE_ERROR, "无法连接 HID 服务", "请关闭再打开蓝牙后重试");
            }
        } catch (Exception e) {
            proxyRequested = false;
            post(STATE_ERROR, "蓝牙访问失败", String.valueOf(e.getMessage()));
        }
    }

    private void registerApp() {
        if (hid == null) return;
        try {
            BluetoothHidDeviceAppSdpSettings sdp = new BluetoothHidDeviceAppSdpSettings(
                    "PhoneKB", "Phone as Bluetooth keyboard", "PhoneKB",
                    (byte) 0, stickAxisRz ? HidConstants.REPORT_DESCRIPTOR_RZ
                            : HidConstants.REPORT_DESCRIPTOR);
            final int generation = ++registerGeneration;
            boolean ok = hid.registerApp(sdp, null, null, btExecutor, callback);
            Log.i(TAG, "registerApp ok=" + ok);
            // Some ROMs (MIUI) return false even though onAppStatusChanged(true) follows shortly
            // after, so wait for the callback before surfacing an error.
            if (!ok) {
                main.postDelayed(() -> {
                    if (generation == registerGeneration && !appRegistered) {
                        post(STATE_ERROR, "HID 注册被拒绝", "可能有其他 HID 应用正在运行，请先强制停止它");
                    }
                }, 1500);
            }
        } catch (Exception e) {
            Log.e(TAG, "registerApp failed", e);
            post(STATE_ERROR, "HID 注册异常", String.valueOf(e.getMessage()));
        }
    }

    private void scheduleReRegister() {
        main.postDelayed(() -> {
            BluetoothAdapter a = adapter;
            if (!appRegistered && hid != null && a != null) {
                try {
                    if (!a.isEnabled()) return;
                } catch (SecurityException e) {
                    return;
                }
                Log.i(TAG, "re-register after unexpected unregister");
                registerApp();
            }
        }, 1500);
    }

    private void scheduleReconnect(final BluetoothDevice device) {
        if (device == null || !appRegistered) return;
        if (manualDisconnect) return;
        rememberHost(device);
        if (bondStateOf(device) != BluetoothDevice.BOND_BONDED) {
            // The host deleted the pairing: no amount of connect() calls can fix that.
            post(STATE_WAITING, "配对已被删除", "请点右上角「配对」，与平板重新配对一次");
            return;
        }
        if (reconnectAttempts >= RECONNECT_MAX) {
            post(STATE_WAITING, "连不上平板",
                    "平板没有响应：确认平板蓝牙已开、就在旁边；或在平板蓝牙里点一下这台手机（点状态文字可重试）");
            return;
        }
        reconnectAttempts++;
        final int attempt = reconnectAttempts;
        final long delay = Math.min(3000L * attempt, RECONNECT_MAX_DELAY_MS);
        post(STATE_WAITING, "连接已断开", "自动重连中（第 " + attempt + "/" + RECONNECT_MAX + " 次）…");
        main.postDelayed(() -> {
            if (!appRegistered || state == STATE_CONNECTED) return;
            Log.i(TAG, "reconnect attempt " + attempt + " -> " + nameOf(device));
            connectDevice(device);
        }, delay);
    }

    /** Status-text tap: forget the backoff and try the remembered host right away. */
    public void retryConnect() {
        manualDisconnect = false;
        reconnectAttempts = 0;
        BluetoothDevice device = lastHostDevice();
        if (device != null && bondStateOf(device) == BluetoothDevice.BOND_BONDED) {
            post(STATE_WAITING, "正在重新连接", nameOf(device));
            main.postDelayed(() -> connectDevice(device), 250);
            return;
        }
        start();
    }

    private void connectDevice(BluetoothDevice device) {
        BluetoothHidDevice h = hid;
        if (h == null || !appRegistered || state == STATE_CONNECTED) return;
        try {
            h.connect(device);
        } catch (Exception e) {
            Log.w(TAG, "connect failed", e);
        }
    }

    private void rememberHost(BluetoothDevice device) {
        if (device == null) return;
        try {
            String address = device.getAddress();
            if (address == null) return;
            context.getSharedPreferences("phonekb", Context.MODE_PRIVATE)
                    .edit().putString(PREF_LAST_HOST, address).apply();
        } catch (Exception ignored) { }
    }

    /** Bonded host remembered from an earlier connection, or null. */
    private BluetoothDevice lastHostDevice() {
        BluetoothDevice current = host;
        if (current != null) return current;
        try {
            if (adapter == null) return null;
            String address = context.getSharedPreferences("phonekb", Context.MODE_PRIVATE)
                    .getString(PREF_LAST_HOST, null);
            if (address == null) return null;
            return adapter.getRemoteDevice(address);
        } catch (Exception e) {
            return null;
        }
    }

    // ---------------------------------------------------------------- host switching

    /** Set while a manual switch is in flight, so the guard does not fight the user. */
    private BluetoothDevice switchingTo;
    private long guardCooldownUntil;
    /**
     * The user pressed 「断开」 in the device list. Auto-reconnect, cold-start reconnect and the
     * prefer-host guard all stand down until the user connects again or the app restarts —
     * otherwise the link would be back within two seconds and the button would be pointless.
     */
    private boolean manualDisconnect;

    public BluetoothDevice currentHost() {
        return host;
    }

    public boolean isSwitching() {
        return switchingTo != null;
    }

    /** Bonded devices that can act as a HID host, most recently used first. */
    public java.util.List<BluetoothDevice> bondedHosts() {
        java.util.ArrayList<BluetoothDevice> out = new java.util.ArrayList<>();
        try {
            if (adapter == null) return out;
            java.util.Set<BluetoothDevice> bonded = adapter.getBondedDevices();
            if (bonded == null) return out;
            for (BluetoothDevice d : bonded) {
                if (d == null || !looksLikeHost(d)) continue;
                // Computers and phones/tablets can host a Bluetooth keyboard; anything with an unknown
                // class only shows up once we have actually driven it (that filters out BLE gadgets).
                if (lastSeen(d) > 0L || isComputer(d) || isPhoneOrTablet(d)) out.add(d);
            }
        } catch (Exception ignored) { }
        java.util.Collections.sort(out, (a, b) -> Long.compare(lastSeen(b), lastSeen(a)));
        return out;
    }

    /** Headphones, speakers, watches... can never take a keyboard connection. */
    private static boolean looksLikeHost(BluetoothDevice d) {
        try {
            android.bluetooth.BluetoothClass c = d.getBluetoothClass();
            if (c == null) return true;
            int major = c.getDeviceClass() & 0x1F00;
            return major != android.bluetooth.BluetoothClass.Device.Major.AUDIO_VIDEO
                    && major != android.bluetooth.BluetoothClass.Device.Major.WEARABLE
                    && major != android.bluetooth.BluetoothClass.Device.Major.IMAGING
                    && major != android.bluetooth.BluetoothClass.Device.Major.PERIPHERAL;
        } catch (Exception e) {
            return true;
        }
    }

    private static boolean isComputer(BluetoothDevice d) {
        try {
            android.bluetooth.BluetoothClass c = d.getBluetoothClass();
            return c != null && (c.getDeviceClass() & 0x1F00)
                    == android.bluetooth.BluetoothClass.Device.Major.COMPUTER;
        } catch (Exception e) {
            return false;
        }
    }

    private static boolean isPhoneOrTablet(BluetoothDevice d) {
        try {
            android.bluetooth.BluetoothClass c = d.getBluetoothClass();
            return c != null && (c.getDeviceClass() & 0x1F00)
                    == android.bluetooth.BluetoothClass.Device.Major.PHONE;
        } catch (Exception e) {
            return false;
        }
    }

    /** 1 = 平板 / 手机, 2 = 电脑, 3 = 其他. Remembered type wins over the Bluetooth class. */
    public static int hostTypeOf(BluetoothDevice d) {
        int known = d == null ? 3 : rememberedType(d);
        if (known != 0) return known;
        try {
            android.bluetooth.BluetoothClass c = d.getBluetoothClass();
            if (c == null) return 3;
            int major = c.getDeviceClass() & 0x1F00;
            if (major == android.bluetooth.BluetoothClass.Device.Major.COMPUTER) return 2;
            if (major == android.bluetooth.BluetoothClass.Device.Major.PHONE) return 1;
            return 3;
        } catch (Exception e) {
            return 3;
        }
    }

    private static int rememberedType(BluetoothDevice d) {
        try {
            HidManager local = instance;
            String address = d.getAddress();
            if (local == null || address == null) return 0;
            return local.prefs().getInt("dev_type_" + address, 0);
        } catch (Exception e) {
            return 0;
        }
    }

    /** Last time this device was connected (wall clock), 0 when unknown. */
    public long lastSeen(BluetoothDevice d) {
        try {
            String address = d.getAddress();
            if (address == null) return 0L;
            return prefs().getLong("dev_time_" + address, 0L);
        } catch (Exception e) {
            return 0L;
        }
    }

    public BluetoothDevice preferredHost() {
        try {
            if (adapter == null) return null;
            String address = prefs().getString(PREF_PREFERRED_HOST, null);
            if (address == null) return null;
            return adapter.getRemoteDevice(address);
        } catch (Exception e) {
            return null;
        }
    }

    public void setPreferredHost(BluetoothDevice device) {
        if (device == null) return;
        try {
            String address = device.getAddress();
            if (address == null) return;
            prefs().edit().putString(PREF_PREFERRED_HOST, address).apply();
        } catch (Exception ignored) { }
    }

    /** 「设备抢连守护」: switch back when another bonded host steals the connection. */
    public boolean guardEnabled() {
        return prefs().getBoolean("host_guard", true);
    }

    public void setGuardEnabled(boolean on) {
        prefs().edit().putBoolean("host_guard", on).apply();
    }

    /** True after 「断开」 until the user connects again (never persisted: a restart re-arms auto-connect). */
    public boolean isManuallyDisconnected() {
        return manualDisconnect;
    }

    /**
     * 「断开」: drop the HID link and stay dropped. Keys are released first so nothing stays pressed on
     * the host, and every automatic reconnect path is muted until the user picks a device again.
     */
    public void disconnectCurrent() {
        final BluetoothDevice current = host;
        releaseAllInputs();
        manualDisconnect = true;
        switchingTo = null;
        reconnectAttempts = 0;
        guardCooldownUntil = SystemClock.uptimeMillis() + 60_000L;
        host = null;
        try {
            if (current != null && hid != null) hid.disconnect(current);
        } catch (Exception e) {
            Log.w(TAG, "disconnect failed", e);
        }
        post(STATE_WAITING, "已断开", "点「切换」连回来");
    }

    /**
     * Manual switch: drop the current host and pull the link up to another bonded one.
     * Android's HID device profile only holds a single host, so this is disconnect + connect,
     * not two links at once. No re-pairing is needed as long as both sides kept the bond.
     */
    public void switchTo(BluetoothDevice device) {
        if (device == null) return;
        if (bondStateOf(device) != BluetoothDevice.BOND_BONDED) {
            post(STATE_WAITING, "还没配对过", "请点「配对」，和这台设备配对一次");
            return;
        }
        if (hid == null || !appRegistered) {
            post(STATE_WAITING, "HID 还没注册好", "请稍等一秒再试");
            return;
        }
        releaseAllInputs();
        manualDisconnect = false;
        switchingTo = device;
        reconnectAttempts = 0;
        setPreferredHost(device);
        post(STATE_WAITING, "正在切换设备", nameOf(device) + " · 大概 1–2 秒");
        final BluetoothDevice current = host;
        try {
            if (current != null && !current.equals(device)) hid.disconnect(current);
        } catch (Exception e) {
            Log.w(TAG, "disconnect before switch failed", e);
        }
        main.postDelayed(() -> forceConnect(device, 1), 600);
    }

    private void forceConnect(final BluetoothDevice device, final int attempt) {
        if (switchingTo == null || !device.equals(switchingTo)) return;
        BluetoothHidDevice h = hid;
        if (h == null || !appRegistered) return;
        if (host != null && host.equals(device)) {
            switchingTo = null;
            return;
        }
        try {
            h.connect(device);
        } catch (Exception e) {
            Log.w(TAG, "switch connect failed", e);
        }
        if (attempt < 4) {
            main.postDelayed(() -> forceConnect(device, attempt + 1), 1200);
        } else {
            switchingTo = null;
            guardCooldownUntil = SystemClock.uptimeMillis() + GUARD_COOLDOWN_MS;
            post(STATE_WAITING, "切换失败", "请在 " + nameOf(device) + " 蓝牙里确认这台手机还在已配对列表中");
        }
    }

    /** Guard: if a host that is not the preferred one grabbed the link, hand it back. */
    private void enforcePreferredHost(final BluetoothDevice connected) {
        if (manualDisconnect || !guardEnabled() || switchingTo != null) return;
        if (SystemClock.uptimeMillis() < guardCooldownUntil) return;
        final BluetoothDevice preferred = preferredHost();
        if (preferred == null || preferred.equals(connected)) return;
        if (bondStateOf(preferred) != BluetoothDevice.BOND_BONDED) return;
        main.postDelayed(() -> {
            if (switchingTo != null) return;
            if (host != null && host.equals(connected)) switchTo(preferred);
        }, 2000);
    }

    private SharedPreferences prefs() {
        return context.getSharedPreferences("phonekb", Context.MODE_PRIVATE);
    }

    private void rememberMeta(BluetoothDevice device) {
        if (device == null) return;
        try {
            String address = device.getAddress();
            if (address == null) return;
            prefs().edit()
                    .putLong("dev_time_" + address, System.currentTimeMillis())
                    .putInt("dev_type_" + address, hostTypeOf(device))
                    .apply();
        } catch (Exception ignored) { }
    }

    private static int bondStateOf(BluetoothDevice device) {
        try {
            return device.getBondState();
        } catch (SecurityException e) {
            return BluetoothDevice.BOND_NONE;
        }
    }

    /**
     * Hat value sent when the d-pad is centred. 0x0F is outside the declared logical range 0..7
     * (null state), which is what Windows/DirectInput expects; some host stacks instead fold the
     * value modulo 8, so 0x0F becomes 7 = 左上 and the direction looks permanently held. Exposed
     * as a setting so a host that mis-handles the null state can be worked around without re-pairing.
     */
    public static int neutralHat = 0x0F;

    /** Safety net: drop every held key/button after (re)connecting, or when the page loses focus. */
    public void releaseAllInputs() {
        keyboardRelease();
        mouseButtons(0);
        try {
            // Full-length report: the gamepad report is hat(1) + buttons(2) + 4 axes, so a 3-byte
            // short form leaves the host's axes (and any host that caches the size) at the last value.
            sendGamepadFull(0, neutralHat, 0, 0, 0, 0);
        } catch (Exception ignored) { }
    }

    public void stop() {
        try {
            if (hid != null) hid.unregisterApp();
        } catch (Exception ignored) { }
        try {
            if (adapter != null && hid != null) adapter.closeProfileProxy(BluetoothProfile.HID_DEVICE, hid);
        } catch (Exception ignored) { }
        btExecutor.shutdownNow();
    }

    private String nameOf(BluetoothDevice d) {
        if (d == null) return "";
        try {
            String n = d.getName();
            return n == null || n.isEmpty() ? d.getAddress() : n;
        } catch (Exception e) {
            return "蓝牙设备";
        }
    }

    private void post(int newState, String title, String detail) {
        Log.i(TAG, "state=" + newState + " | " + title + " | " + detail);
        state = newState;
        statusTitle = title;
        statusDetail = detail;
        error = newState == STATE_ERROR ? detail : "";
        final Listener l = listener;
        if (l != null) main.post(() -> l.onHidStatus(newState, title, detail));
    }

    private boolean send(int reportId, byte[] data) {
        BluetoothDevice d = host;
        BluetoothHidDevice h = hid;
        if (d == null || h == null || state != STATE_CONNECTED) {
            if (reportId != HidConstants.REPORT_MOUSE) {
                Log.w(TAG, "send skipped id=" + reportId + " state=" + state + " host=" + (d != null));
            }
            return false;
        }
        final long submitAt = System.currentTimeMillis();
        if (legacySend) {
            if (timingLog) {
                Log.i("PhoneKB-HID", "submit id=" + reportId + " t=" + submitAt + " mask=" + maskOf(data));
            }
            sendExecutor.execute(() -> {
                boolean ok = false;
                try {
                    ok = h.sendReport(d, reportId, data);
                } catch (Exception e) {
                    Log.w(TAG, "sendReport failed", e);
                }
                if (!ok) dropCount++;
                if (timingLog) {
                    Log.i("PhoneKB-HID", "sent id=" + reportId + " t=" + System.currentTimeMillis()
                            + " ok=" + ok + " mask=" + maskOf(data));
                }
            });
            return true;
        }
        synchronized (pendingLock) {
            pendingReports.put(reportId, data);
            if (!flushQueued) {
                flushQueued = true;
                sendExecutor.execute(this::flushPending);
            }
        }
        if (timingLog) {
            Log.i("PhoneKB-HID", "submit id=" + reportId + " t=" + submitAt + " mask=" + maskOf(data));
        }
        long now = SystemClock.uptimeMillis();
        if (DEBUG && now - lastSendLogAt > 1000) {
            lastSendLogAt = now;
            Log.i(TAG, "send id=" + reportId + " len=" + data.length + " host=" + nameOf(d));
        }
        return true;
    }

    /**
     * Drains {@link #pendingReports}, newest state first, retrying a failed hand-off a few times.
     * A dropped report used to mean a lost button press; retrying (and letting a newer state replace
     * an older one) keeps taps reliable without piling up a queue.
     */
    private void flushPending() {
        BluetoothDevice d = host;
        BluetoothHidDevice h = hid;
        while (true) {
            int reportId;
            byte[] data;
            synchronized (pendingLock) {
                if (pendingReports.isEmpty()) {
                    flushQueued = false;
                    return;
                }
                if (pendingReports.containsKey(HidConstants.REPORT_GAMEPAD)) {
                    reportId = HidConstants.REPORT_GAMEPAD;
                } else {
                    reportId = pendingReports.keySet().iterator().next();
                }
                data = pendingReports.remove(reportId);
            }
            if (reportId == HidConstants.REPORT_GAMEPAD) {
                // Button/hat edges go out immediately; only pure stick movement is rate limited,
                // otherwise a press could wait up to a few ms behind stick frames.
                int buttons = data.length > 2 ? (data[1] & 0xFF) | ((data[2] & 0xFF) << 8) : 0;
                int hat = data.length > 0 ? (data[0] & 0x0F) : neutralHat;
                boolean edge = buttons != lastGamepadButtons || hat != lastGamepadHat;
                if (!edge) {
                    long wait = MIN_GAMEPAD_GAP_MS - (SystemClock.uptimeMillis() - lastGamepadSentAt);
                    if (wait > 0) sleepQuietly(wait);
                }
                lastGamepadButtons = buttons;
                lastGamepadHat = hat;
                lastGamepadSentAt = SystemClock.uptimeMillis();
            }
            if (d == null || h == null || state != STATE_CONNECTED) {
                d = host;
                h = hid;
                if (d == null || h == null || state != STATE_CONNECTED) {
                    dropCount++;
                    synchronized (pendingLock) {
                        flushQueued = false;
                    }
                    return;
                }
            }
            boolean ok = false;
            for (int attempt = 0; attempt < 3 && !ok; attempt++) {
                try {
                    ok = h.sendReport(d, reportId, data);
                } catch (Exception e) {
                    Log.w(TAG, "sendReport failed", e);
                }
                if (!ok) {
                    retryCount++;
                    sleepQuietly(3);
                }
            }
            if (!ok) dropCount++;
            if (timingLog) {
                Log.i("PhoneKB-HID", "sent id=" + reportId + " t=" + System.currentTimeMillis()
                        + " ok=" + ok + " mask=" + maskOf(data));
            }
        }
    }

    private static String maskOf(byte[] data) {
        if (data == null || data.length < 3) return "-";
        int mask = (data[1] & 0xFF) | ((data[2] & 0xFF) << 8);
        return String.valueOf(mask);
    }

    private static void sleepQuietly(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    // ---------------------------------------------------------------- keyboard

    public boolean keyboardPress(int modifiers, int usage) {
        byte[] r = new byte[2];
        r[0] = (byte) modifiers;
        r[1] = (byte) usage;
        return send(HidConstants.REPORT_KEYBOARD, r);
    }

    public void keyboardRelease() {
        send(HidConstants.REPORT_KEYBOARD, new byte[2]);
    }

    /** Tap a single key (down, then release shortly after). */
    public void tapKey(int modifiers, int usage) {
        keyboardPress(modifiers, usage);
        main.postDelayed(this::keyboardRelease, 26);
    }

    /** Tap a shortcut such as Ctrl+C: hold slightly longer so hosts register the combo. */
    public void tapCombo(int modifiers, int usage) {
        keyboardPress(modifiers, usage);
        main.postDelayed(this::keyboardRelease, 45);
    }

    // ------------------------------------------------------------------- mouse

    public void mouseButtons(int buttons) {
        byte[] r = new byte[4];
        r[0] = (byte) buttons;
        send(HidConstants.REPORT_MOUSE, r);
    }

    public void mouseClick(int buttons) {
        mouseButtons(buttons);
        main.postDelayed(() -> mouseButtons(0), 30);
    }

    /**
     * Queues a relative pointer move.
     *
     * <p>Deltas are accumulated and flushed at most every {@link #MOUSE_FLUSH_MS} ms. Touch screens
     * deliver moves in bursts (up to 240 Hz); sending one report per event let the Bluetooth stack
     * queue them up, which is what made the cursor lag behind the finger. Merging them keeps the
     * report count below what the link can carry, so the newest position is what the host sees.
     */
    public void mouseMove(int dx, int dy) {
        mouseMove(dx, dy, SystemClock.uptimeMillis());
    }

    /**
     * Same as {@link #mouseMove(int, int)} but keeps the touch event timestamp so the latency
     * statistics can report how old the motion already was when it reached the Bluetooth stack.
     */
    public void mouseMove(int dx, int dy, long eventTimeMs) {
        if (dx == 0 && dy == 0) return;
        final long now = SystemClock.uptimeMillis();
        final long gap = Math.max(MOUSE_GAP_MIN_MS, mouseGapMs);
        MouseStats.lastGap = gap;
        if (now - lastMouseFlushAt >= gap) {
            // Leading edge: the first move of a burst goes out right away instead of waiting for the
            // merge window, so the pointer starts moving in the same frame the finger does.
            lastMouseFlushAt = now;
            dispatchMouse(dx, dy, eventTimeMs);
            return;
        }
        pendDx.addAndGet(dx);
        pendDy.addAndGet(dy);
        pendEventTime = eventTimeMs;
        if (mouseFlushQueued.compareAndSet(false, true)) {
            mouseHandler().postDelayed(this::flushMouseMove,
                    Math.max(1L, gap - (now - lastMouseFlushAt)));
        }
    }

    private Handler mouseHandler() {
        Handler h = mouseHandler;
        if (h != null) return h;
        synchronized (this) {
            if (mouseHandler == null) {
                HandlerThread t = new HandlerThread("phonekb-mouse-coalesce",
                        Process.THREAD_PRIORITY_URGENT_DISPLAY);
                t.start();
                mouseHandler = new Handler(t.getLooper());
            }
            return mouseHandler;
        }
    }

    private void flushMouseMove() {
        mouseFlushQueued.set(false);
        lastMouseFlushAt = SystemClock.uptimeMillis();
        final int dx0 = pendDx.getAndSet(0);
        final int dy0 = pendDy.getAndSet(0);
        if (dx0 == 0 && dy0 == 0) return;
        dispatchMouse(dx0, dy0, pendEventTime);
    }

    private void dispatchMouse(final int dx0, final int dy0, final long eventTimeMs) {
        final BluetoothDevice d = host;
        final BluetoothHidDevice h = hid;
        if (d == null || h == null || state != STATE_CONNECTED) return;
        sendExecutor.execute(() -> {
            int dx = dx0;
            int dy = dy0;
            final long started = SystemClock.uptimeMillis();
            try {
                while (dx != 0 || dy != 0) {
                    int sx = clamp(dx);
                    int sy = clamp(dy);
                    byte[] r = new byte[4];
                    r[1] = (byte) sx;
                    r[2] = (byte) sy;
                    h.sendReport(d, HidConstants.REPORT_MOUSE, r);
                    dx -= sx;
                    dy -= sy;
                }
                recordPointerLatency(eventTimeMs, (int) (SystemClock.uptimeMillis() - started));
                if (timingLog) Log.i(TAG, "mouse ctx=" + (int) MouseStats.lastGap + "ms dx=" + dx0 + " dy=" + dy0);
            } catch (Exception e) {
                Log.w(TAG, "mouse flush failed", e);
            }
        });
    }

    public void mouseWheel(int steps) {
        while (steps != 0) {
            int s = Math.max(-127, Math.min(127, steps));
            byte[] r = new byte[4];
            r[3] = (byte) s;
            send(HidConstants.REPORT_MOUSE, r);
            steps -= s;
        }
    }

    /** Guards {@link #pointerHoldStart(int, int)} against being started twice from a double tap. */
    private boolean pointerHeld;

    /**
     * Long-press gesture on the host screen (Douyin style "hold for 2x speed").
     *
     * <p>The pointer is first slammed into the top-left corner - the host clamps there, so the
     * following relative move always starts from a known place - then stepped into the video area,
     * and only then is the left button pressed. Everything runs inside one send task so the press
     * can never overtake the moves.
     */
    public void pointerHoldStart(final int parkX, final int parkY) {
        final BluetoothDevice d = host;
        final BluetoothHidDevice h = hid;
        if (d == null || h == null || state != STATE_CONNECTED) return;
        if (pointerHeld) return;
        pointerHeld = true;
        sendExecutor.execute(() -> {
            try {
                sendMouseMove(h, d, -2400, -2400);
                sleepQuietly(6);
                sendMouseMove(h, d, parkX, parkY);
                sleepQuietly(10);
                byte[] r = new byte[4];
                r[0] = 0x01;
                h.sendReport(d, HidConstants.REPORT_MOUSE, r);
            } catch (Exception e) {
                pointerHeld = false;
                Log.w(TAG, "pointer hold start failed", e);
            }
        });
    }

    /** Releases the left button pressed by {@link #pointerHoldStart(int, int)}. */
    public void pointerHoldStop() {
        if (!pointerHeld) return;
        pointerHeld = false;
        byte[] r = new byte[4];
        send(HidConstants.REPORT_MOUSE, r);
    }

    /** Blocking chunked mouse move; only valid on the send thread. */
    private void sendMouseMove(BluetoothHidDevice h, BluetoothDevice d, int dx, int dy)
            throws Exception {
        while (dx != 0 || dy != 0) {
            int sx = clamp(dx);
            int sy = clamp(dy);
            byte[] r = new byte[4];
            r[1] = (byte) sx;
            r[2] = (byte) sy;
            h.sendReport(d, HidConstants.REPORT_MOUSE, r);
            dx -= sx;
            dy -= sy;
        }
    }

    private static int clamp(int v) {
        return Math.max(-127, Math.min(127, v));
    }

    // -------------------------------------------------------- consumer control

    /** Presses (and holds) one consumer control usage; release with {@link #consumerUp()}. */
    public void consumerDown(int usage) {
        byte[] r = new byte[2];
        r[0] = (byte) (usage & 0xFF);
        r[1] = (byte) ((usage >> 8) & 0xFF);
        send(HidConstants.REPORT_CONSUMER, r);
    }

    /** Releases whatever consumer control usage is currently held. */
    public void consumerUp() {
        send(HidConstants.REPORT_CONSUMER, new byte[2]);
    }

    public void tapConsumer(int usage) {
        byte[] r = new byte[2];
        r[0] = (byte) (usage & 0xFF);
        r[1] = (byte) ((usage >> 8) & 0xFF);
        send(HidConstants.REPORT_CONSUMER, r);
        main.postDelayed(() -> send(HidConstants.REPORT_CONSUMER, new byte[2]), 35);
    }

    // ----------------------------------------------------------------- gamepad

    /**
     * Gamepad report: byte 0 = hat switch, bytes 1-2 = 16 buttons (little endian).
     *
     * <p>The pushed in-gamepad buttons are our logical {@code GP_*} mask. On the host side
     * Linux numbers HID gamepad buttons 1-16 and maps them to BTN_GAMEPAD (A), BTN_EAST (B),
     * BTN_C, BTN_NORTH (X), BTN_WEST (Y), BTN_Z, BTN_TL (L1), BTN_TR (R1), BTN_TL2, BTN_TR2,
     * BTN_SELECT, BTN_START, so A/B/X/Y/L1/R1/SELECT/START must be sent as buttons
     * 1/2/4/5/7/8/11/12. The d-pad must use the hat switch instead of buttons, otherwise every
     * button lands four positions off on the host (A shows up as Y, the d-pad presses A/B/X, ...).
     */
    public void sendGamepad(int buttons) {
        sendGamepadWithHat(buttons, hatFromButtons(buttons));
    }

    /**
     * Gamepad report with an explicit hat value. The round movement disc sends the hat through
     * this method so pressing a face button does not recentre the hat of a held disc direction
     * (the plain {@link #sendGamepad(int)} derives the hat only from d-pad bits).
     */
    public void sendGamepadWithHat(int buttons, int hat) {
        sendGamepadFull(buttons, hat, stickLx, stickLy, stickRx, stickRy);
    }

    /**
     * Full gamepad report: hat, 16 buttons and the four stick axes. The last stick values are kept
     * so that pressing a face button does not recentre a stick the thumb is still holding.
     */
    public void sendGamepadFull(int buttons, int hat, int lx, int ly, int rx, int ry) {
        stickLx = clamp(lx);
        stickLy = clamp(ly);
        stickRx = clamp(rx);
        stickRy = clamp(ry);
        boolean rz = stickAxisRz;
        byte[] r = new byte[rz ? 9 : 7];
        r[0] = (byte) (encodeHat(hat) & 0x0F);
        // The d-pad is expressed with the hat switch only. Its four low bits must never reach the
        // button field: Windows / DirectInput numbers buttons sequentially, so leaking them lights
        // 按钮 1–4 while a direction is held, and a single dropped release leaves one stuck on.
        int mask = buttonMask(buttons & ~(HidConstants.GP_UP | HidConstants.GP_DOWN
                | HidConstants.GP_LEFT | HidConstants.GP_RIGHT));
        r[1] = (byte) (mask & 0xFF);
        r[2] = (byte) ((mask >> 8) & 0xFF);
        r[3] = (byte) stickLx;
        r[4] = (byte) stickLy;
        if (rz) {
            // Right stick only on Z / Rz for hosts that read the camera stick there.
            r[5] = 0;
            r[6] = 0;
            r[7] = (byte) stickRx;
            r[8] = (byte) stickRy;
        } else {
            r[5] = (byte) stickRx;
            r[6] = (byte) stickRy;
        }
        send(HidConstants.REPORT_GAMEPAD, r);
    }

    /**
     * Hosts cache the HID report descriptor from the moment a device is paired. Tablets that were
     * first paired with an older build (hat logical max 8 instead of 7) keep decoding every hat
     * value as floor(7v/8), i.e. one position counter-clockwise - pressing "left" produces
     * "down-left". Re-pairing fixes it, but for hosts that were not re-paired this compatibility
     * mode pre-compensates the value so the host lands on the intended sector.
     */
    private static final int[] HAT_COMPAT = {0, 2, 3, 4, 5, 6, 7, 7};

    private boolean hatCompat;

    public void setHatCompat(boolean enabled) {
        hatCompat = enabled;
    }

    public boolean isHatCompat() {
        return hatCompat;
    }

    private int encodeHat(int hat) {
        if (!hatCompat || hat < 0 || hat > 7) return hat;
        return HAT_COMPAT[hat];
    }

    /** Latest stick positions, so a button press never recentres a held stick. */
    private volatile int stickLx, stickLy, stickRx, stickRy;

    /**
     * True when the right stick should travel on Z / Rz instead of Rx / Ry. Changing this swaps the
     * report descriptor, so the host has to forget the device and pair again.
     */
    private volatile boolean stickAxisRz;

    public void setStickAxisRz(boolean enabled) {
        if (stickAxisRz == enabled) return;
        stickAxisRz = enabled;
        // Re-register so the new descriptor is used for the next connection.
        main.post(this::registerApp);
    }

    public boolean isStickAxisRz() {
        return stickAxisRz;
    }

    private static int buttonMask(int buttons) {
        int mask = 0;
        if ((buttons & HidConstants.GP_A) != 0) mask |= 0x0001;      // button 1  -> BTN_GAMEPAD (A)
        if ((buttons & HidConstants.GP_B) != 0) mask |= 0x0002;      // button 2  -> BTN_EAST (B)
        if ((buttons & HidConstants.GP_X) != 0) mask |= 0x0008;      // button 4  -> BTN_NORTH (X)
        if ((buttons & HidConstants.GP_Y) != 0) mask |= 0x0010;      // button 5  -> BTN_WEST (Y)
        if ((buttons & HidConstants.GP_L1) != 0) mask |= 0x0040;     // button 7  -> BTN_TL (L1)
        if ((buttons & HidConstants.GP_R1) != 0) mask |= 0x0080;     // button 8  -> BTN_TR (R1)
        if ((buttons & HidConstants.GP_L2) != 0) mask |= 0x0100;     // button 9  -> BTN_TL2 (L2)
        if ((buttons & HidConstants.GP_R2) != 0) mask |= 0x0200;     // button 10 -> BTN_TR2 (R2)
        if ((buttons & HidConstants.GP_SELECT) != 0) mask |= 0x0400; // button 11 -> BTN_SELECT
        if ((buttons & HidConstants.GP_START) != 0) mask |= 0x0800;  // button 12 -> BTN_START
        return mask;
    }

    /** Debug helper: sends a raw hat switch value (0-7, neutralHat = centred) with no buttons. */
    public void sendGamepadHat(int hat) {
        byte[] r = new byte[stickAxisRz ? 9 : 7];
        r[0] = (byte) hat;
        send(HidConstants.REPORT_GAMEPAD, r);
    }

    /** Converts the four logical d-pad bits into a HID hat switch value (neutralHat = centred). */
    private static int hatFromButtons(int buttons) {
        int dx = ((buttons & HidConstants.GP_RIGHT) != 0 ? 1 : 0)
                - ((buttons & HidConstants.GP_LEFT) != 0 ? 1 : 0);
        int dy = ((buttons & HidConstants.GP_DOWN) != 0 ? 1 : 0)
                - ((buttons & HidConstants.GP_UP) != 0 ? 1 : 0);
        if (dx == 0 && dy == 0) return neutralHat;
        if (dx == 0) return dy > 0 ? 4 : 0;
        if (dy == 0) return dx > 0 ? 2 : 6;
        if (dx > 0) return dy < 0 ? 1 : 3;
        return dy > 0 ? 5 : 7;
    }
}
