package com.phonekb;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * WiFi 伴侣通道（手机端）：监听 TCP 端口，平板上的 PhoneKB 伴侣 App 连过来，
 * 通过「一行一个 JSON」的协议收发文字 / 剪贴板。
 *
 * 协议：
 *   平板 -> 手机  {"t":"hello","token":"123456","name":"iQOO Pad2","ver":1}
 *                {"t":"ack","what":"text","result":"ok"}
 *                {"t":"clip","text":"..."}         平板剪贴板同步给手机
 *                {"t":"log","text":"..."}
 *   手机 -> 平板  {"t":"welcome","ok":true,"name":"手机 xxx"} / {"t":"welcome","ok":false,"reason":"..."}
 *                {"t":"text","text":"...","append":true}   追加写入平板当前输入框
 *                {"t":"clip","text":"..."}                 写入平板剪贴板
 *                {"t":"ping"} / {"t":"pong"}
 */
public final class CompanionBridge {
    private static final String TAG = "PhoneKB-Companion";
    public static final int PORT = 47653;
    public static final int STATE_STOPPED = 0;
    public static final int STATE_LISTENING = 1;
    public static final int STATE_CONNECTED = 2;

    public interface Listener {
        void onBridgeState(int state, String detail);
        void onClientHello(String name);
        void onClientAck(String what, String result);
        void onClipFromPad(String text);
        void onLog(String line);
    }

    private static CompanionBridge instance;

    private final Context app;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final Object sendLock = new Object();
    private final ExecutorService sendExecutor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "phonekb-companion-send");
        t.setDaemon(true);
        return t;
    });

    private volatile Listener listener;
    private volatile boolean running;
    private volatile int state = STATE_STOPPED;
    private volatile ServerSocket server;
    private volatile Socket client;
    private volatile BufferedWriter out;
    private volatile String clientName = "";
    private volatile String lastError = "";

    private CompanionBridge(Context app) {
        this.app = app;
    }

    public static synchronized CompanionBridge get(Context context) {
        if (instance == null) instance = new CompanionBridge(context.getApplicationContext());
        return instance;
    }

    public void setListener(Listener l) {
        listener = l;
    }

    public int getState() {
        return state;
    }

    public String getClientName() {
        return clientName;
    }

    public String getLastError() {
        return lastError;
    }

    public boolean isConnected() {
        return state == STATE_CONNECTED;
    }

    public String getToken() {
        SharedPreferences p = app.getSharedPreferences("phonekb", Context.MODE_PRIVATE);
        String t = p.getString("companion_token", "");
        if (t == null || t.length() != 6) {
            t = String.format(Locale.US, "%06d", new SecureRandom().nextInt(1000000));
            p.edit().putString("companion_token", t).apply();
        }
        return t;
    }

    public void setToken(String token) {
        if (token == null || token.length() != 6) return;
        app.getSharedPreferences("phonekb", Context.MODE_PRIVATE)
                .edit().putString("companion_token", token).apply();
    }

    // ------------------------------------------------------------ lifecycle

    public void start() {
        synchronized (this) {
            if (running) return;
            running = true;
        }
        Thread t = new Thread(this::acceptLoop, "phonekb-companion-server");
        t.setDaemon(true);
        t.start();
    }

    public void stop() {
        running = false;
        closeQuietly();
        setState(STATE_STOPPED, "服务已停止");
    }

    private void acceptLoop() {
        while (running) {
            try {
                ServerSocket ss = new ServerSocket();
                ss.setReuseAddress(true);
                ss.bind(new InetSocketAddress(PORT), 4);
                server = ss;
                if (state == STATE_STOPPED) setState(STATE_LISTENING, "端口 " + PORT);
                while (running) {
                    Socket s = ss.accept();
                    handleClient(s);
                    if (running) setState(STATE_LISTENING, "端口 " + PORT);
                }
            } catch (Exception e) {
                if (running) {
                    lastError = String.valueOf(e.getMessage());
                    Log.w(TAG, "server loop: " + e.getMessage());
                    emitLog("服务异常，正在重试：" + e.getMessage());
                    sleep(1500);
                }
            } finally {
                closeQuietly();
            }
        }
        setState(STATE_STOPPED, "服务已停止");
    }

    private void closeQuietly() {
        ServerSocket ss = server;
        server = null;
        if (ss != null) {
            try { ss.close(); } catch (Exception ignored) {}
        }
        Socket c = client;
        client = null;
        out = null;
        if (c != null) {
            try { c.close(); } catch (Exception ignored) {}
        }
        clientName = "";
    }

    // --------------------------------------------------------------- client

    private void handleClient(Socket s) {
        BufferedReader in = null;
        BufferedWriter w = null;
        try {
            s.setSoTimeout(15000);
            in = new BufferedReader(new InputStreamReader(s.getInputStream(), StandardCharsets.UTF_8));
            w = new BufferedWriter(new OutputStreamWriter(s.getOutputStream(), StandardCharsets.UTF_8));

            String first = in.readLine();
            if (first == null) return;
            JSONObject hello = new JSONObject(first);
            if (!"hello".equals(hello.optString("t"))) {
                sendLine(w, welcome(false, "server", "first message must be hello"));
                return;
            }
            String token = hello.optString("token", "");
            String name = hello.optString("name", "平板");
            if (!getToken().equals(token)) {
                sendLine(w, welcome(false, null, "配对码不正确"));
                emitLog("平板尝试连接，但配对码不正确");
                return;
            }
            sendLine(w, welcome(true, null, null));
            client = s;
            out = w;
            clientName = name;
            s.setSoTimeout(0);
            setState(STATE_CONNECTED, name);
            emitHello(name);

            String line;
            while (running && (line = in.readLine()) != null) {
                handleLine(line);
            }
        } catch (Exception e) {
            Log.w(TAG, "client ended: " + e.getClass().getName() + ": " + e.getMessage());
        } finally {
            Log.i(TAG, "client closed");
            client = null;
            out = null;
            clientName = "";
            try { s.close(); } catch (Exception ignored) {}
        }
    }

    private void handleLine(String line) {
        try {
            JSONObject o = new JSONObject(line);
            String t = o.optString("t");
            if ("ack".equals(t)) {
                emitAck(o.optString("what"), o.optString("result"));
            } else if ("clip".equals(t)) {
                emitClip(o.optString("text"));
            } else if ("log".equals(t)) {
                emitLog(o.optString("text"));
            } else if ("ping".equals(t)) {
                JSONObject pong = new JSONObject();
                pong.put("t", "pong");
                sendJson(pong);
            }
        } catch (Exception e) {
            emitLog("收到无法解析的数据");
        }
    }

    private JSONObject welcome(boolean ok, String name, String reason) throws Exception {
        JSONObject o = new JSONObject();
        o.put("t", "welcome");
        o.put("ok", ok);
        if (ok) o.put("name", "手机 " + android.os.Build.MODEL);
        if (reason != null) o.put("reason", reason);
        return o;
    }

    private void sendLine(BufferedWriter w, JSONObject o) {
        try {
            w.write(o.toString());
            w.write("\n");
            w.flush();
        } catch (Exception ignored) {
        }
    }

    // ----------------------------------------------------------------- send

    public boolean sendText(String text, boolean append) {
        if (text == null) return false;
        try {
            JSONObject o = new JSONObject();
            o.put("t", "text");
            o.put("text", text);
            o.put("append", append);
            return sendJson(o);
        } catch (Exception e) {
            return false;
        }
    }

    public boolean sendClip(String text) {
        if (text == null) return false;
        try {
            JSONObject o = new JSONObject();
            o.put("t", "clip");
            o.put("text", text);
            return sendJson(o);
        } catch (Exception e) {
            return false;
        }
    }

    public boolean ping() {
        try {
            JSONObject o = new JSONObject();
            o.put("t", "ping");
            return sendJson(o);
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * UI buttons and broadcast receivers both call this from the main thread,
     * where Android forbids socket writes (NetworkOnMainThreadException).
     * Payloads are therefore queued onto a dedicated writer thread.
     */
    private boolean sendJson(JSONObject o) {
        final String payload = o.toString();
        if (Looper.myLooper() == Looper.getMainLooper()) {
            sendExecutor.execute(() -> writeLine(payload));
            return true;
        }
        return writeLine(payload);
    }

    private boolean writeLine(String payload) {
        synchronized (sendLock) {
            BufferedWriter w = out;
            if (w == null) return false;
            try {
                w.write(payload);
                w.write("\n");
                w.flush();
                return true;
            } catch (Exception e) {
                Log.w(TAG, "send failed: " + e.getClass().getName() + ": " + e.getMessage());
                return false;
            }
        }
    }

    // ----------------------------------------------------------------- misc

    public String getLocalIp() {
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

    private void setState(int s, String detail) {
        state = s;
        final Listener l = listener;
        if (l != null) {
            main.post(() -> l.onBridgeState(s, detail));
        }
    }

    private void emitHello(String name) {
        final Listener l = listener;
        if (l != null) main.post(() -> l.onClientHello(name));
    }

    private void emitAck(String what, String result) {
        final Listener l = listener;
        if (l != null) main.post(() -> l.onClientAck(what, result));
    }

    private void emitClip(String text) {
        final Listener l = listener;
        if (l != null) main.post(() -> l.onClipFromPad(text));
    }

    private void emitLog(String line) {
        final Listener l = listener;
        if (l != null) main.post(() -> l.onLog(line));
    }

    private static void sleep(long ms) {
        try { Thread.sleep(ms); } catch (InterruptedException ignored) {}
    }
}
