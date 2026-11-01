package com.phonekb;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothHidDevice;
import android.bluetooth.BluetoothHidDeviceAppSdpSettings;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Log;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

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
                    (byte) 0, HidConstants.REPORT_DESCRIPTOR);
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
        boolean ok = false;
        try {
            ok = h.sendReport(d, reportId, data);
        } catch (Exception e) {
            Log.w(TAG, "sendReport failed", e);
        }
        long now = SystemClock.uptimeMillis();
        if (reportId != HidConstants.REPORT_MOUSE || now - lastSendLogAt > 1000) {
            lastSendLogAt = now;
            Log.i(TAG, "send id=" + reportId + " len=" + data.length + " ok=" + ok + " host=" + nameOf(d));
        }
        return ok;
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

    public void mouseMove(int dx, int dy) {
        if (dx == 0 && dy == 0) return;
        while (dx != 0 || dy != 0) {
            int sx = clamp(dx);
            int sy = clamp(dy);
            byte[] r = new byte[4];
            r[1] = (byte) sx;
            r[2] = (byte) sy;
            send(HidConstants.REPORT_MOUSE, r);
            dx -= sx;
            dy -= sy;
        }
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

    private static int clamp(int v) {
        return Math.max(-127, Math.min(127, v));
    }

    // -------------------------------------------------------- consumer control

    public void tapConsumer(int usage) {
        byte[] r = new byte[2];
        r[0] = (byte) (usage & 0xFF);
        r[1] = (byte) ((usage >> 8) & 0xFF);
        send(HidConstants.REPORT_CONSUMER, r);
        main.postDelayed(() -> send(HidConstants.REPORT_CONSUMER, new byte[2]), 35);
    }

    // ----------------------------------------------------------------- gamepad

    public void sendGamepad(int buttons) {
        byte[] r = new byte[3];
        r[0] = (byte) 0x0F; // hat switch = null
        r[1] = (byte) (buttons & 0xFF);
        r[2] = (byte) ((buttons >> 8) & 0xFF);
        send(HidConstants.REPORT_GAMEPAD, r);
    }
}
