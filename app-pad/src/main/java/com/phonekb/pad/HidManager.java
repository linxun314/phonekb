package com.phonekb.pad;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothHidDevice;
import android.bluetooth.BluetoothHidDeviceAppSdpSettings;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.content.Context;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Process;
import android.os.SystemClock;
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
        long age = SystemClock.uptimeMillis() - eventTimeMs;
        if (age < 0) age = 0;
        synchronized (statSample) {
            statSample[statCursor] = age;
            statCost[statCursor] = costMs;
            statCursor = (statCursor + 1) % STAT_N;
            if (statCount < STAT_N) statCount++;
        }
    }

    /** "p50/p95 age, p50/p95 send cost" - or null when nothing was measured yet. */
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
                + " cost p50=" + cost[n / 2] + "ms p95=" + cost[Math.min(n - 1, (int) (n * 0.95))] + "ms";
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
    /** Mouse moves inside this window are merged into one report (≈500 Hz ceiling). */
    private static final long MOUSE_FLUSH_MS = 2;
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
                post(STATE_CONNECTED, "已连接", nameOf(pluggedDevice));
                main.postDelayed(HidManager.this::releaseAllInputs, 300);
            } else {
                post(STATE_WAITING, "等待平板连接", "已注册 HID，请在平板蓝牙中连接手机");
            }
        }
        @Override public void onConnectionStateChanged(BluetoothDevice device, int newState) {
            Log.i(TAG, "conn " + newState + " " + nameOf(device));
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                host = device;
                reconnectAttempts = 0;
                post(STATE_CONNECTED, "已连接", nameOf(device));
                main.postDelayed(HidManager.this::releaseAllInputs, 300);
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                if (host != null && host.equals(device)) host = null;
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
            host = null;
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
        if (device == null || !appRegistered || reconnectAttempts >= 3) return;
        reconnectAttempts++;
        final int attempt = reconnectAttempts;
        main.postDelayed(() -> {
            BluetoothHidDevice h = hid;
            if (h == null || !appRegistered || state == STATE_CONNECTED) return;
            try {
                Log.i(TAG, "reconnect attempt " + attempt + " -> " + nameOf(device));
                h.connect(device);
            } catch (Exception e) {
                Log.w(TAG, "reconnect failed", e);
            }
        }, 2000L * attempt);
    }

    /** Safety net: drop every held key/button after (re)connecting. */
    private void releaseAllInputs() {
        keyboardRelease();
        mouseButtons(0);
        try {
            send(HidConstants.REPORT_GAMEPAD, new byte[]{(byte) 0x0F, 0, 0});
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
                int hat = data.length > 0 ? (data[0] & 0x0F) : 0x0F;
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
        if (now - lastMouseFlushAt >= MOUSE_FLUSH_MS) {
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
            mouseHandler().postDelayed(this::flushMouseMove, MOUSE_FLUSH_MS);
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
        int mask = buttonMask(buttons);
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

    /** Debug helper: sends a raw hat switch value (0-7, 0x0F = centred) with no buttons. */
    public void sendGamepadHat(int hat) {
        byte[] r = new byte[stickAxisRz ? 9 : 7];
        r[0] = (byte) hat;
        send(HidConstants.REPORT_GAMEPAD, r);
    }

    /** Converts the four logical d-pad bits into a HID hat switch value (0x0F = centred). */
    private static int hatFromButtons(int buttons) {
        int dx = ((buttons & HidConstants.GP_RIGHT) != 0 ? 1 : 0)
                - ((buttons & HidConstants.GP_LEFT) != 0 ? 1 : 0);
        int dy = ((buttons & HidConstants.GP_DOWN) != 0 ? 1 : 0)
                - ((buttons & HidConstants.GP_UP) != 0 ? 1 : 0);
        if (dx == 0 && dy == 0) return 0x0F;
        if (dx == 0) return dy > 0 ? 4 : 0;
        if (dy == 0) return dx > 0 ? 2 : 6;
        if (dx > 0) return dy < 0 ? 1 : 3;
        return dy > 0 ? 5 : 7;
    }
}
