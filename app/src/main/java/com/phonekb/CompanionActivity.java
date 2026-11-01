package com.phonekb;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.ContentValues;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.MediaStore;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.BinaryBitmap;
import com.google.zxing.DecodeHintType;
import com.google.zxing.MultiFormatReader;
import com.google.zxing.RGBLuminanceSource;
import com.google.zxing.common.HybridBinarizer;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * 伴侣模式（WiFi）：手机作为服务端，平板上的 PhoneKB 伴侣 App 连过来。
 * 用于蓝牙 HID 做不到的事：中文直接上屏、剪贴板双向同步。
 */
public class CompanionActivity extends Activity implements CompanionBridge.Listener {

    private static final String TAG = "PhoneKB-Companion";
    private static final int REQ_SCAN = 301;

    private CompanionBridge bridge;
    private TextView serverState;
    private TextView clientState;
    private TextView pairInfo;
    private TextView logView;
    private TextView sendStatus;
    private EditText input;
    private Uri pendingCaptureUri;

    private final List<String> logLines = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(Ui.BG);
        getWindow().setNavigationBarColor(Ui.BG);
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        bridge = CompanionBridge.get(this);
        bridge.setListener(this);
        setContentView(buildUi());
        bridge.start();
        refreshState();
        addLog("伴侣服务已监听端口 " + CompanionBridge.PORT + "；点「扫码连接平板」扫平板上的二维码即可配对");
        applyExtras(getIntent());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        applyExtras(intent);
    }

    @Override
    protected void onResume() {
        super.onResume();
        bridge.setListener(this);
        refreshState();
    }

    @Override
    protected void onDestroy() {
        bridge.setListener(null);
        // 服务线程跟随进程（KeepAliveService）继续存活，退出本页不影响已连接的平板。
        super.onDestroy();
    }

    // ---------------------------------------------------------------- UI

    private View buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Ui.BG);
        root.setPadding(dp(12), dp(10), dp(12), dp(14));

        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);
        TextView back = plain("←", 20, Ui.TEXT);
        back.setPadding(dp(10), dp(4), dp(14), dp(4));
        back.setOnClickListener(v -> finish());
        top.addView(back);
        TextView title = plain("伴侣模式 · WiFi", 18, Ui.TEXT);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        top.addView(title, new LinearLayout.LayoutParams(0, WRAP, 1f));
        TextView help = pill("说明");
        help.setOnClickListener(v -> showHelp());
        top.addView(help);
        root.addView(top, lpMatchWrap());

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(false);
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(col, lpMatchWrap());
        root.addView(scroll, new LinearLayout.LayoutParams(MATCH, 0, 1f));

        LinearLayout statusCard = card();
        serverState = plain("服务状态", 14, Ui.TEXT);
        serverState.setTypeface(Typeface.DEFAULT_BOLD);
        statusCard.addView(serverState);
        clientState = plain("", 13, Ui.TEXT_DIM);
        clientState.setPadding(0, dp(4), 0, 0);
        statusCard.addView(clientState);
        pairInfo = plain("", 13, Ui.TEXT_DIM);
        pairInfo.setPadding(0, dp(8), 0, 0);
        pairInfo.setLineSpacing(dp(4), 1f);
        pairInfo.setTextIsSelectable(true);
        statusCard.addView(pairInfo);

        // Pairing: the TABLET shows the QR code and this phone scans it with the camera,
        // which is easier than pointing the tablet camera at the phone. The scan result is
        // handed over to the tablet, which then dials back into this phone's server.
        TextView qrHint = plain("平板配对：在平板打开 PhoneKB 伴侣，页面会显示二维码；\n点下面的「扫码连接平板」，用相机拍下平板屏幕上的二维码，自动配对。", 12, Ui.TEXT_DIM);
        qrHint.setLineSpacing(dp(3), 1f);
        statusCard.addView(qrHint, lpMatchWrapWithTop(dp(10)));

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        TextView scan = pillAccent("扫码连接平板");
        scan.setOnClickListener(v -> startQrScan());
        row.addView(scan);
        TextView refresh = pill("刷新");
        refresh.setOnClickListener(v -> refreshState());
        row.addView(refresh);
        TextView copy = pill("复制配对信息");
        copy.setOnClickListener(v -> copyPairInfo());
        row.addView(copy);
        statusCard.addView(row, lpWrapWrapWithTop(dp(10)));
        col.addView(statusCard, cardLp());

        LinearLayout sendCard = card();
        sendCard.addView(sectionTitle("发送中文到平板"));
        input = new EditText(this);
        input.setHint("在这里输入中文，点「发送到平板」直接写入平板当前输入框");
        input.setHintTextColor(Ui.TEXT_DIM);
        input.setTextColor(Ui.TEXT);
        input.setTextSize(15);
        input.setBackground(Ui.rounded(this, Ui.SURFACE_2, 12));
        input.setPadding(dp(12), dp(10), dp(12), dp(10));
        input.setMinLines(2);
        input.setMaxLines(4);
        input.setGravity(Gravity.TOP | Gravity.START);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        sendCard.addView(input, lpMatchWrapWithTop(dp(8)));
        LinearLayout sendRow = new LinearLayout(this);
        sendRow.setOrientation(LinearLayout.HORIZONTAL);
        TextView send = pillAccent("发送到平板");
        send.setOnClickListener(v -> sendInput());
        sendRow.addView(send);
        TextView clip = pill("发送剪贴板");
        clip.setOnClickListener(v -> sendClipboard());
        sendRow.addView(clip);
        TextView clear = pill("清空");
        clear.setOnClickListener(v -> input.setText(""));
        sendRow.addView(clear);
        sendCard.addView(sendRow, lpWrapWrapWithTop(dp(8)));
        sendStatus = plain("", 12, Ui.TEXT_DIM);
        sendStatus.setPadding(0, dp(6), 0, 0);
        sendCard.addView(sendStatus);
        col.addView(sendCard, cardLp());

        LinearLayout logCard = card();
        logCard.addView(sectionTitle("接收 / 日志"));
        logView = plain("等待平板连接…", 12, Ui.TEXT_DIM);
        logView.setLineSpacing(dp(3), 1f);
        logView.setTextIsSelectable(true);
        logCard.addView(logView, lpMatchWrapWithTop(dp(6)));
        col.addView(logCard, cardLp());

        LinearLayout guide = card();
        guide.addView(sectionTitle("使用步骤"));
        TextView g = plain(
                "1. 平板安装并打开 PhoneKB 伴侣（build\\PhoneKB-Companion.apk）\n"
                        + "2. 手机和平板连同一个 WiFi；也可以手机开热点让平板连\n"
                        + "3. 平板点「扫码连接」拍下本页二维码，自动填好 IP / 配对码并连接\n"
                        + "   （也可以手动输入 IP、端口 " + CompanionBridge.PORT + "、配对码）\n"
                        + "4. 平板首次使用要在 设置 → 无障碍 → PhoneKB 伴侣 打开服务\n"
                        + "5. 连上后本页发送的中文会出现在平板当前输入框；平板复制的文字会回传到本页\n\n"
                        + "蓝牙 HID 不受影响，两个通道可以同时使用。", 12, Ui.TEXT_DIM);
        g.setLineSpacing(dp(3), 1f);
        guide.addView(g, lpMatchWrapWithTop(dp(6)));
        col.addView(guide, cardLp());
        return root;
    }

    private void refreshState() {
        if (bridge == null) return;
        int state = bridge.getState();
        String token = bridge.getToken();
        String ip = bridge.getLocalIp();
        if (state == CompanionBridge.STATE_CONNECTED) {
            serverState.setText("● 平板已连接");
            serverState.setTextColor(Ui.OK);
        } else if (state == CompanionBridge.STATE_LISTENING) {
            serverState.setText("● 服务运行中，等待平板连接");
            serverState.setTextColor(Ui.WARN);
        } else {
            serverState.setText("● 服务未启动（点「刷新」重试）");
            serverState.setTextColor(Ui.ERR);
            bridge.start();
        }
        String name = bridge.getClientName();
        clientState.setText(state == CompanionBridge.STATE_CONNECTED
                ? ("已连接设备：" + (name == null || name.isEmpty() ? "平板" : name))
                : "未连接设备：在平板伴侣 App 里填下面的信息");
        pairInfo.setText("手机 IP： " + (ip == null ? "（未连接 WiFi，请先连同一网络）" : ip)
                + "\n端口： " + CompanionBridge.PORT
                + "\n配对码： " + token);
        if (state == CompanionBridge.STATE_CONNECTED && sendStatus != null && sendStatus.getText().length() == 0) {
            sendStatus.setText("平板已连接，可以直接发送中文");
        }
    }

    private void copyPairInfo() {
        String ip = bridge.getLocalIp();
        String text = "手机 IP: " + (ip == null ? "" : ip) + "\n端口: " + CompanionBridge.PORT
                + "\n配对码: " + bridge.getToken();
        ClipboardManager cm = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
        if (cm != null) cm.setPrimaryClip(ClipData.newPlainText("PhoneKB 配对信息", text));
        toast("配对信息已复制，可发到平板上");
        addLog("已复制配对信息");
    }

    private void sendInput() {
        String text = input == null ? "" : input.getText().toString();
        if (text.trim().isEmpty()) {
            toast("先输入要发送的文字");
            return;
        }
        if (!bridge.isConnected()) {
            toast("平板还没连接");
            sendStatus.setText("发送失败：平板未连接");
            return;
        }
        boolean ok = bridge.sendText(text, true);
        if (ok) {
            sendStatus.setText("已发送 " + text.length() + " 个字符，等待平板确认…");
            addLog("→ 已发送：" + clipForLog(text));
            input.setText("");
        } else {
            sendStatus.setText("发送失败，连接可能已断开");
        }
    }

    private void sendClipboard() {
        ClipboardManager cm = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
        String text = null;
        if (cm != null && cm.hasPrimaryClip() && cm.getPrimaryClip() != null
                && cm.getPrimaryClip().getItemCount() > 0) {
            CharSequence cs = cm.getPrimaryClip().getItemAt(0).coerceToText(this);
            if (cs != null) text = cs.toString();
        }
        if (text == null || text.isEmpty()) {
            toast("手机剪贴板是空的");
            addLog("剪贴板为空，未发送");
            return;
        }
        if (!bridge.isConnected()) {
            toast("平板还没连接");
            return;
        }
        boolean ok = bridge.sendClip(text);
        sendStatus.setText(ok ? "已把手机剪贴板内容发到平板" : "发送失败，连接可能已断开");
        addLog(ok ? "→ 已发送剪贴板：" + clipForLog(text) : "→ 发送剪贴板失败");
    }

    // ------------------------------------------------------ tablet QR pairing

    /** Opens the system camera; the photo of the tablet QR is decoded locally with zxing. */
    private void startQrScan() {
        try {
            ContentValues values = new ContentValues();
            values.put(MediaStore.Images.Media.DISPLAY_NAME, "phonekb_pair_" + System.currentTimeMillis() + ".jpg");
            values.put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg");
            Uri uri = getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
            if (uri == null) {
                toast("无法准备相机文件");
                addLog("扫码失败：无法创建照片文件");
                return;
            }
            pendingCaptureUri = uri;
            Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
            intent.putExtra(MediaStore.EXTRA_OUTPUT, uri);
            intent.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION | Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivityForResult(intent, REQ_SCAN);
        } catch (Exception e) {
            toast("打不开相机，请手动把手机 IP 和配对码填到平板里");
            addLog("打不开相机：" + e.getClass().getSimpleName());
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQ_SCAN) return;
        if (resultCode != RESULT_OK) {
            addLog("已取消扫码");
            pendingCaptureUri = null;
            return;
        }
        String text = null;
        Uri uri = pendingCaptureUri;
        if (uri != null) text = decodeQr(uri);
        if (text == null && data != null) {
            if (data.getData() != null) {
                text = decodeQr(data.getData());
            } else {
                Bitmap thumb = data.getParcelableExtra("data");
                if (thumb != null) text = decodeBitmap(thumb);
            }
        }
        pendingCaptureUri = null;
        if (text == null) {
            toast("没识别到二维码，把平板屏幕调亮，离近一点再拍");
            addLog("扫码未识别，请重试");
            return;
        }
        handleScannedText(text);
    }

    private String decodeQr(Uri uri) {
        try {
            BitmapFactory.Options bounds = new BitmapFactory.Options();
            bounds.inJustDecodeBounds = true;
            try (InputStream in = getContentResolver().openInputStream(uri)) {
                BitmapFactory.decodeStream(in, null, bounds);
            }
            int sample = 1;
            int longest = Math.max(bounds.outWidth, bounds.outHeight);
            while (longest / sample > 1600) sample *= 2;
            BitmapFactory.Options opts = new BitmapFactory.Options();
            opts.inSampleSize = sample;
            Bitmap bmp;
            try (InputStream in = getContentResolver().openInputStream(uri)) {
                bmp = BitmapFactory.decodeStream(in, null, opts);
            }
            return bmp == null ? null : decodeBitmap(bmp);
        } catch (Throwable t) {
            addLog("读取照片失败：" + t.getClass().getSimpleName());
            return null;
        }
    }

    private String decodeBitmap(Bitmap bmp) {
        try {
            int w = bmp.getWidth();
            int h = bmp.getHeight();
            int[] pixels = new int[w * h];
            bmp.getPixels(pixels, 0, w, 0, 0, w, h);
            RGBLuminanceSource source = new RGBLuminanceSource(w, h, pixels);
            BinaryBitmap bitmap = new BinaryBitmap(new HybridBinarizer(source));
            Map<DecodeHintType, Object> hints = new EnumMap<>(DecodeHintType.class);
            hints.put(DecodeHintType.TRY_HARDER, Boolean.TRUE);
            List<BarcodeFormat> formats = new ArrayList<>();
            formats.add(BarcodeFormat.QR_CODE);
            hints.put(DecodeHintType.POSSIBLE_FORMATS, formats);
            MultiFormatReader reader = new MultiFormatReader();
            reader.setHints(hints);
            return reader.decode(bitmap).getText();
        } catch (Throwable t) {
            return null;
        }
    }

    /**
     * Tablet QR content: {"v":2,"h":"<tablet ip>","q":47654,"t":"<tablet pair token>"}
     * Older phone QR content: {"v":1,"h":...,"p":...} — reject it with a hint.
     */
    private void handleScannedText(String text) {
        try {
            String trimmed = text.trim();
            if (!trimmed.startsWith("{")) {
                toast("二维码内容不对，请刷新平板上的二维码后重试");
                addLog("扫码内容不对：" + clipForLog(text));
                return;
            }
            JSONObject o = new JSONObject(trimmed);
            String host = o.optString("h", "");
            int pairPort = o.optInt("q", 0);
            String pairToken = o.optString("t", "");
            if (!host.isEmpty() && pairPort > 0) {
                addLog("扫码成功：平板 " + host + " · 配对端口 " + pairPort);
                pairToTablet(host, pairPort, pairToken);
                return;
            }
            if (o.has("p")) {
                toast("这是手机自己的二维码，请扫平板上显示的二维码");
                addLog("扫到的是手机二维码，方向反了");
                return;
            }
            toast("二维码内容不对，请刷新平板上的二维码");
            addLog("二维码缺少平板地址：" + clipForLog(text));
        } catch (Exception e) {
            toast("二维码解析失败，请重试");
            addLog("二维码解析失败：" + clipForLog(text));
        }
    }

    /**
     * Handshake: tell the tablet our Wi-Fi address, server port and token; the tablet
     * then connects back to us like a normal companion client (existing protocol).
     */
    private void pairToTablet(String host, int pairPort, String pairToken) {
        final String ip = bridge.getLocalIp();
        final String token = bridge.getToken();
        if (ip == null) {
            toast("手机还没连 WiFi，请先连上和平板同一个网络");
            addLog("配对失败：手机没有 WiFi 地址");
            return;
        }
        bridge.start();
        addLog("正在通知平板 " + host + " …");
        new Thread(() -> {
            String result = null;
            try (Socket s = new Socket()) {
                s.connect(new InetSocketAddress(host, pairPort), 5000);
                s.setSoTimeout(9000);
                BufferedWriter out = new BufferedWriter(
                        new OutputStreamWriter(s.getOutputStream(), StandardCharsets.UTF_8));
                BufferedReader in = new BufferedReader(
                        new InputStreamReader(s.getInputStream(), StandardCharsets.UTF_8));
                JSONObject o = new JSONObject();
                o.put("t", "pair");
                o.put("v", 2);
                o.put("ip", ip);
                o.put("port", CompanionBridge.PORT);
                o.put("token", token);
                o.put("ptoken", pairToken == null ? "" : pairToken);
                o.put("name", Build.MANUFACTURER + " " + Build.MODEL);
                out.write(o.toString());
                out.write("\n");
                out.flush();
                String line = in.readLine();
                if (line == null) throw new Exception("平板没有回应");
                JSONObject reply = new JSONObject(line);
                if (!reply.optBoolean("ok")) {
                    String reason = reply.optString("reason", "平板拒绝了配对");
                    if (reason.isEmpty()) reason = "平板拒绝了配对";
                    throw new Exception(reason);
                }
                result = "ok";
            } catch (Exception e) {
                result = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
                if (result == null || result.isEmpty()) result = "连接平板失败";
            }
            final String res = result;
            runOnUiThread(() -> {
                if ("ok".equals(res)) {
                    addLog("配对成功！等待平板连接过来…");
                    toast("配对成功，正在等待平板连接");
                    sendStatus.setText("正在等待平板连接…");
                } else {
                    addLog("配对失败：" + res);
                    toast("配对失败：" + res);
                }
            });
        }, "phonekb-tablet-pair").start();
    }

    private void applyExtras(Intent intent) {
        if (intent == null) return;
        String token = intent.getStringExtra("ctoken");
        if (token != null && token.length() == 6) {
            bridge.setToken(token);
            refreshState();
            addLog("配对码已设置为 " + token);
        }
        if (intent.getBooleanExtra("ctest", false)) {
            String text = intent.getStringExtra("ctext");
            if (text == null) text = "你好，PhoneKB 伴侣测试 " + (System.currentTimeMillis() % 100000);
            boolean ok = bridge.sendText(text, true);
            addLog(ok ? "→ 测试文本已发送：" + clipForLog(text) : "→ 测试发送失败：平板未连接");
        }
        if (intent.getBooleanExtra("cclip", false)) {
            String text = intent.getStringExtra("ctext");
            if (text == null) text = "来自手机的剪贴板测试 " + (System.currentTimeMillis() % 100000);
            boolean ok = bridge.sendClip(text);
            addLog(ok ? "→ 测试剪贴板已发送：" + clipForLog(text) : "→ 测试剪贴板失败：平板未连接");
        }
        // Debug hooks: the camera itself cannot be automated, so tests can inject the
        // scanned payload / pairing target directly through the same code path.
        String pairJson = intent.getStringExtra("pairjson");
        if (pairJson != null) {
            addLog("测试：注入二维码内容");
            handleScannedText(pairJson);
        }
        String pairHost = intent.getStringExtra("pairhost");
        if (pairHost != null) {
            pairToTablet(pairHost, intent.getIntExtra("pairport", 47654), intent.getStringExtra("pairtoken"));
        }
        if (intent.getBooleanExtra("cscan", false)) startQrScan();
        refreshState();
    }

    // ------------------------------------------------------------ listener

    @Override
    public void onBridgeState(int state, String detail) {
        refreshState();
    }

    @Override
    public void onClientHello(String name) {
        addLog("平板已连接：" + name);
        sendStatus.setText("平板已连接，可以直接发送中文");
    }

    @Override
    public void onClientAck(String what, String result) {
        if ("text".equals(what)) {
            boolean ok = "ok".equals(result) || result == null || result.isEmpty();
            sendStatus.setText(ok ? "平板已写入输入框 ✓" : ("平板返回：" + result));
            addLog(ok ? "← 平板确认：文字已写入" : "← 平板写入失败：" + result);
        } else {
            addLog("← 平板确认：" + what);
        }
    }

    @Override
    public void onClipFromPad(String text) {
        if (text == null) return;
        addLog("← 平板剪贴板：" + clipForLog(text));
        ClipboardManager cm = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
        if (cm != null) cm.setPrimaryClip(ClipData.newPlainText("PhoneKB 来自平板", text));
        sendStatus.setText("已同步平板剪贴板到手机（可直接粘贴）");
    }

    @Override
    public void onLog(String line) {
        addLog(line);
    }

    // ------------------------------------------------------------- helpers

    private static final int WRAP = ViewGroup.LayoutParams.WRAP_CONTENT;
    private static final int MATCH = ViewGroup.LayoutParams.MATCH_PARENT;

    private void addLog(String line) {
        if (line == null) return;
        logLines.add(0, line);
        while (logLines.size() > 24) logLines.remove(logLines.size() - 1);
        if (logView != null) {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < logLines.size(); i++) {
                if (i > 0) sb.append('\n');
                sb.append(logLines.get(i));
            }
            logView.setText(sb.toString());
        }
    }

    private String clipForLog(String text) {
        if (text == null) return "";
        String one = text.replace('\n', ' ');
        return one.length() > 60 ? one.substring(0, 60) + "…" : one;
    }

    private void showHelp() {
        new AlertDialog.Builder(this)
                .setTitle("伴侣模式说明")
                .setMessage("蓝牙 HID 只能发按键码，所以手机上打的中文到不了平板。\n"
                        + "伴侣模式用 WiFi 直连：手机把文字发到平板 App，平板用无障碍服务写入当前输入框。\n\n"
                        + "• 配对方式：在平板打开 PhoneKB 伴侣，会显示二维码；手机点本页「扫码连接平板」拍下平板二维码即可。\n"
                        + "• 配对成功后平板会自动连回手机，本页显示「平板已连接」。\n"
                        + "• 平板需要安装 PhoneKB 伴侣 APK，并开启无障碍服务。\n"
                        + "• 两台设备连同一个 WiFi，或手机开热点给平板；完全局域网，不走外网。\n"
                        + "• 平板复制的文字会自动同步到手机剪贴板。")
                .setPositiveButton("知道了", null)
                .show();
    }

    private void toast(String msg) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
    }

    private int dp(int v) {
        return Ui.dpi(this, v);
    }

    private TextView plain(String text, int sizeSp, int color) {
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextSize(sizeSp);
        tv.setTextColor(color);
        return tv;
    }

    private TextView sectionTitle(String text) {
        TextView tv = plain(text, 15, Ui.TEXT);
        tv.setTypeface(Typeface.DEFAULT_BOLD);
        return tv;
    }

    private TextView pill(String text) {
        TextView tv = plain(text, 13, Ui.TEXT);
        tv.setPadding(dp(14), dp(8), dp(14), dp(8));
        tv.setBackground(Ui.rounded(this, Ui.SURFACE_2, 12));
        return tv;
    }

    private TextView pillAccent(String text) {
        TextView tv = plain(text, 13, Ui.BG);
        tv.setTypeface(Typeface.DEFAULT_BOLD);
        tv.setPadding(dp(16), dp(9), dp(16), dp(9));
        tv.setBackground(Ui.rounded(this, Ui.ACCENT, 12));
        return tv;
    }

    private LinearLayout card() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackground(Ui.rounded(this, Ui.SURFACE, 16));
        card.setPadding(dp(14), dp(12), dp(14), dp(12));
        return card;
    }

    private LinearLayout.LayoutParams cardLp() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(MATCH, WRAP);
        lp.setMargins(0, dp(10), 0, 0);
        return lp;
    }

    private LinearLayout.LayoutParams lpMatchWrap() {
        return new LinearLayout.LayoutParams(MATCH, WRAP);
    }

    private LinearLayout.LayoutParams lpMatchWrapWithTop(int topMargin) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(MATCH, WRAP);
        lp.setMargins(0, topMargin, 0, 0);
        return lp;
    }

    private LinearLayout.LayoutParams lpWrapWrapWithTop(int topMargin) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(WRAP, WRAP);
        lp.setMargins(0, topMargin, 0, 0);
        return lp;
    }
}
