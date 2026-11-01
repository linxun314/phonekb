package com.phonekb.companion;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Enumeration;
import java.util.Locale;

/**
 * 平板端的 WiFi 连接：作为 TCP 客户端连手机上的 PhoneKB 伴侣服务。
 * 收到 text 就调 InjectorService 注入，收到 clip 就写本机剪贴板。
 */
public class LinkService extends Service {
    private static final String TAG = "PhoneKB-Companion";
    public static final int PORT = 47653;
    /** Pairing handoff port: the phone connects here first, then handshake hands over to PORT. */
    public static final int PAIR_PORT = 47654;
    public static final String ACTION_CONNECT = "com.phonekb.companion.action.CONNECT";
    public static final String ACTION_STOP = "com.phonekb.companion.action.STOP";
    public static final String ACTION_LISTEN = "com.phonekb.companion.action.LISTEN";
    public static final String EXTRA_HOST = "host";
    public static final String EXTRA_PORT = "port";
    public static final String EXTRA_TOKEN = "token";

    public static final int STATE_IDLE = 0;
    public static final int STATE_CONNECTING = 1;
    public static final int STATE_CONNECTED = 2;
    public static final int STATE_ERROR = 3;
    public static final int STATE_PAIRING = 4;

    public interface Listener {
        void onLinkState(int state, String detail);

        void onIncoming(String kind, String text);

        void onInjected(String kind, String result);
    }

    private static final String CHANNEL_ID = "companion_link";
    private static final Handler UI = new Handler(Looper.getMainLooper());
    private static volatile LinkService instance;
    private static volatile Listener listener;
    private static volatile int state = STATE_IDLE;
    private static volatile String stateDetail = "未连接";

    private volatile boolean running;
    private volatile Socket socket;
    private Thread worker;
    private volatile boolean pairRunning;
    private volatile ServerSocket pairServer;
    private Thread pairThread;

    public static void setListener(Listener l) {
        listener = l;
        if (l != null) {
            final int s = state;
            final String d = stateDetail;
            UI.post(() -> l.onLinkState(s, d));
        }
    }

    public static int getState() {
        return state;
    }

    public static String getStateDetail() {
        return stateDetail;
    }

    public static boolean isConnected() {
        return state == STATE_CONNECTED;
    }

    public static void start(Context context, String host, int port, String token) {
        Intent i = new Intent(context, LinkService.class)
                .setAction(ACTION_CONNECT)
                .putExtra(EXTRA_HOST, host)
                .putExtra(EXTRA_PORT, port)
                .putExtra(EXTRA_TOKEN, token);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) context.startForegroundService(i);
        else context.startService(i);
    }

    public static void stop(Context context) {
        context.stopService(new Intent(context, LinkService.class));
        notifyState(STATE_IDLE, "已断开");
    }

    /** Starts (or restarts) the pairing listener that waits for the phone to scan our QR. */
    public static void startPairing(Context context) {
        if (state == STATE_CONNECTED) return;
        Intent i = new Intent(context, LinkService.class).setAction(ACTION_LISTEN);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) context.startForegroundService(i);
        else context.startService(i);
    }

    /** Pair token shown inside the QR code; the phone echoes it back when it hands over. */
    public static String ensurePairToken(Context context) {
        SharedPreferences p = context.getSharedPreferences("companion", Context.MODE_PRIVATE);
        String t = p.getString("pair_token", "");
        if (t == null || t.length() != 6) {
            t = String.format(Locale.US, "%06d", new SecureRandom().nextInt(1000000));
            p.edit().putString("pair_token", t).apply();
        }
        return t;
    }

    /** Debug/test hook: force the pair token (used by adb-driven pairing tests). */
    public static void setPairToken(Context context, String token) {
        if (token == null || token.length() != 6) return;
        context.getSharedPreferences("companion", Context.MODE_PRIVATE)
                .edit().putString("pair_token", token).apply();
    }

    /** Best-effort local Wi-Fi IPv4 address (what the phone's camera is going to connect to). */
    public static String getLocalIp() {
        String fallback = null;
        try {
            Enumeration<NetworkInterface> ifs = NetworkInterface.getNetworkInterfaces();
            while (ifs != null && ifs.hasMoreElements()) {
                NetworkInterface ni = ifs.nextElement();
                if (!ni.isUp() || ni.isLoopback()) continue;
                String n = ni.getName();
                Enumeration<InetAddress> addrs = ni.getInetAddresses();
                while (addrs.hasMoreElements()) {
                    InetAddress a = addrs.nextElement();
                    if (!(a instanceof Inet4Address) || a.isLoopbackAddress()) continue;
                    String ip = a.getHostAddress();
                    if (ip == null || ip.startsWith("169.254.")) continue;
                    if (n != null && (n.startsWith("wlan") || n.startsWith("ap") || n.startsWith("eth"))) {
                        return ip;
                    }
                    if (fallback == null) fallback = ip;
                }
            }
        } catch (Exception ignored) {
        }
        return fallback;
    }

    private static void notifyState(int s, String detail) {
        state = s;
        stateDetail = detail == null ? "" : detail;
        final Listener l = listener;
        if (l != null) UI.post(() -> l.onLinkState(state, stateDetail));
    }

    private static void notifyIncoming(String kind, String text) {
        final Listener l = listener;
        if (l != null) UI.post(() -> l.onIncoming(kind, text));
    }

    private static void notifyInjected(String kind, String result) {
        final Listener l = listener;
        if (l != null) UI.post(() -> l.onInjected(kind, result));
    }

    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;
        createChannel();
        startForegroundCompat("等待连接手机…");
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null) return START_NOT_STICKY;
        String action = intent.getAction();
        if (ACTION_STOP.equals(action)) {
            running = false;
            closeSocket();
            stopSelf();
            return START_NOT_STICKY;
        }
        if (ACTION_LISTEN.equals(action)) {
            startPairListener();
            return START_STICKY;
        }
        if (ACTION_CONNECT.equals(action)) {
            String host = intent.getStringExtra(EXTRA_HOST);
            int port = intent.getIntExtra(EXTRA_PORT, PORT);
            String token = intent.getStringExtra(EXTRA_TOKEN);
            startConnection(host, port, token);
        }
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        running = false;
        stopPairListener();
        closeSocket();
        instance = null;
        notifyState(STATE_IDLE, "已断开");
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private synchronized void startConnection(String host, int port, String token) {
        stopPairListener();
        running = false;
        closeSocket();
        running = true;
        worker = new Thread(() -> connectLoop(host, port, token), "companion-link");
        worker.setDaemon(true);
        worker.start();
    }

    // ---------------------------------------------------------- pairing (QR)

    /**
     * The tablet shows a QR code; the phone scans it, connects to this pair listener
     * and hands over its own server address + token. We then start the normal client
     * connection back to the phone, so the existing protocol stays untouched.
     */
    private synchronized void startPairListener() {
        stopPairListener();
        pairRunning = true;
        pairThread = new Thread(this::pairLoop, "companion-pair");
        pairThread.setDaemon(true);
        pairThread.start();
    }

    private synchronized void stopPairListener() {
        pairRunning = false;
        ServerSocket ss = pairServer;
        pairServer = null;
        if (ss != null) {
            try {
                ss.close();
            } catch (Exception ignored) {
            }
        }
        pairThread = null;
    }

    private void pairLoop() {
        ServerSocket ss = null;
        try {
            ss = new ServerSocket();
            ss.setReuseAddress(true);
            ss.bind(new InetSocketAddress(PAIR_PORT));
            pairServer = ss;
            notifyState(STATE_PAIRING, "等待手机扫码配对（" + getLocalIp() + ":" + PAIR_PORT + "）");
            updateNotification("等待手机扫码配对…");
            while (pairRunning) {
                Socket s = null;
                try {
                    s = ss.accept();
                    s.setSoTimeout(8000);
                    handlePairConnection(s);
                } catch (Exception e) {
                    if (!pairRunning) break;
                    Log.w(TAG, "pair: " + e.getMessage());
                } finally {
                    if (s != null) {
                        try {
                            s.close();
                        } catch (Exception ignored) {
                        }
                    }
                }
            }
        } catch (Exception e) {
            if (pairRunning) {
                String msg = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
                Log.w(TAG, "pair listener: " + msg);
                notifyState(STATE_ERROR, "配对监听失败：" + msg);
                updateNotification("配对监听失败");
            }
        } finally {
            pairServer = null;
            if (ss != null) {
                try {
                    ss.close();
                } catch (Exception ignored) {
                }
            }
        }
    }

    private void handlePairConnection(Socket s) {
        try {
            BufferedReader in = new BufferedReader(
                    new InputStreamReader(s.getInputStream(), StandardCharsets.UTF_8));
            BufferedWriter out = new BufferedWriter(
                    new OutputStreamWriter(s.getOutputStream(), StandardCharsets.UTF_8));
            String line = in.readLine();
            if (line == null) return;
            JSONObject o = new JSONObject(line);
            if (!"pair".equals(o.optString("t"))) {
                pairReply(out, false, "不是配对请求");
                return;
            }
            String host = o.optString("ip", "").trim();
            int port = o.optInt("port", PORT);
            String token = o.optString("token", "");
            String name = o.optString("name", "手机");
            String ptoken = o.optString("ptoken", "");
            String want = pairToken();
            if (host.isEmpty() || port <= 0 || port > 65535 || token.length() != 6) {
                pairReply(out, false, "配对信息不完整");
                return;
            }
            if (want.length() == 6 && !want.equals(ptoken)) {
                pairReply(out, false, "配对码无效，请刷新二维码后重扫");
                Log.w(TAG, "pair rejected: bad pair token");
                return;
            }
            pairReply(out, true, "");
            Log.i(TAG, "paired with " + host + ":" + port + " (" + name + ")");
            stopPairListener();
            startConnection(host, port, token);
        } catch (Exception e) {
            Log.w(TAG, "pair connection: " + (e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage()));
        }
    }

    private void pairReply(BufferedWriter out, boolean ok, String reason) {
        try {
            JSONObject o = new JSONObject();
            o.put("t", "paired");
            o.put("ok", ok);
            o.put("reason", reason == null ? "" : reason);
            o.put("tablet", Build.MANUFACTURER + " " + Build.MODEL);
            writeLine(out, o);
        } catch (Exception ignored) {
        }
    }

    private String pairToken() {
        return ensurePairToken(this);
    }

    // ------------------------------------------------------------- connection

    private void connectLoop(String host, int port, String token) {
        while (running) {
            notifyState(STATE_CONNECTING, "正在连接 " + host + ":" + port);
            updateNotification("正在连接 " + host + "…");
            Socket s = null;
            try {
                s = new Socket();
                s.connect(new InetSocketAddress(host, port), 6000);
                socket = s;
                BufferedReader in = new BufferedReader(
                        new InputStreamReader(s.getInputStream(), StandardCharsets.UTF_8));
                BufferedWriter out = new BufferedWriter(
                        new OutputStreamWriter(s.getOutputStream(), StandardCharsets.UTF_8));

                JSONObject hello = new JSONObject();
                hello.put("t", "hello");
                hello.put("token", token == null ? "" : token);
                hello.put("name", Build.MANUFACTURER + " " + Build.MODEL);
                hello.put("ver", 1);
                writeLine(out, hello);

                s.setSoTimeout(10000);
                String welcomeLine = in.readLine();
                if (welcomeLine == null) throw new Exception("手机没有响应");
                s.setSoTimeout(0);
                JSONObject welcome = new JSONObject(welcomeLine);
                if (!welcome.optBoolean("ok")) {
                    String reason = welcome.optString("reason", "配对码不正确");
                    notifyState(STATE_ERROR, reason);
                    updateNotification(reason);
                    sleep(3000);
                    continue;
                }
                notifyState(STATE_CONNECTED, host + ":" + port);
                updateNotification("已连接 " + host);
                String line;
                while (running && (line = in.readLine()) != null) {
                    handleLine(line, out);
                }
                throw new Exception("连接已断开");
            } catch (Exception e) {
                if (running) {
                    String msg = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
                    Log.w(TAG, "link: " + msg);
                    notifyState(STATE_ERROR, "连接断开，正在重试…");
                    updateNotification("正在重连…");
                    sleep(1500);
                }
            } finally {
                socket = null;
                if (s != null) {
                    try {
                        s.close();
                    } catch (Exception ignored) {
                    }
                }
            }
        }
    }

    private void handleLine(String line, BufferedWriter out) {
        try {
            JSONObject o = new JSONObject(line);
            String t = o.optString("t");
            if ("text".equals(t)) {
                String text = o.optString("text", "");
                boolean append = o.optBoolean("append", true);
                notifyIncoming("text", text);
                String result = inject(text, append);
                notifyInjected("text", result);
                ack(out, "text", result);
            } else if ("clip".equals(t)) {
                String text = o.optString("text", "");
                copyClipboard(text);
                notifyIncoming("clip", text);
                ack(out, "clip", "ok");
            } else if ("ping".equals(t)) {
                JSONObject pong = new JSONObject();
                pong.put("t", "pong");
                writeLine(out, pong);
            }
        } catch (Exception e) {
            Log.w(TAG, "bad line: " + e.getMessage());
        }
    }

    private String inject(String text, boolean append) {
        InjectorService inj = InjectorService.get();
        if (inj == null) return "无障碍服务未开启";
        return inj.injectText(text, append);
    }

    private void ack(BufferedWriter out, String what, String result) {
        try {
            JSONObject o = new JSONObject();
            o.put("t", "ack");
            o.put("what", what);
            o.put("result", result == null ? "" : result);
            writeLine(out, o);
        } catch (Exception ignored) {
        }
    }

    private void copyClipboard(String text) {
        try {
            ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            if (cm != null) cm.setPrimaryClip(ClipData.newPlainText("PhoneKB", text));
        } catch (Exception e) {
            Log.w(TAG, "clipboard: " + e.getMessage());
        }
    }

    private void closeSocket() {
        Socket s = socket;
        socket = null;
        if (s != null) {
            try {
                s.close();
            } catch (Exception ignored) {
            }
        }
    }

    private static void writeLine(BufferedWriter out, JSONObject o) throws Exception {
        out.write(o.toString());
        out.write("\n");
        out.flush();
    }

    private static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException ignored) {
        }
    }

    // ------------------------------------------------------------- notification

    private void createChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return;
        NotificationManager nm = getSystemService(NotificationManager.class);
        if (nm == null) return;
        NotificationChannel ch = new NotificationChannel(
                CHANNEL_ID, "伴侣连接状态", NotificationManager.IMPORTANCE_LOW);
        ch.setDescription("保持平板与手机的 WiFi 连接");
        nm.createNotificationChannel(ch);
    }

    private void startForegroundCompat(String text) {
        Notification n = buildNotification(text);
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC);
            } else {
                startForeground(1, n);
            }
        } catch (Exception e) {
            Log.w(TAG, "startForeground failed: " + e.getMessage());
        }
    }

    private void updateNotification(String text) {
        try {
            NotificationManager nm = getSystemService(NotificationManager.class);
            if (nm != null) nm.notify(1, buildNotification(text));
        } catch (Exception ignored) {
        }
    }

    private Notification buildNotification(String text) {
        Intent open = new Intent(this, MainActivity.class);
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) flags |= PendingIntent.FLAG_IMMUTABLE;
        PendingIntent pi = PendingIntent.getActivity(this, 0, open, flags);
        Notification.Builder b;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            b = new Notification.Builder(this, CHANNEL_ID);
        } else {
            b = new Notification.Builder(this);
        }
        return b.setContentTitle("PhoneKB 伴侣")
                .setContentText(text)
                .setSmallIcon(android.R.drawable.ic_menu_edit)
                .setOngoing(true)
                .setContentIntent(pi)
                .build();
    }
}
