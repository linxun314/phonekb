package com.phonekb.companion;

import android.Manifest;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.provider.MediaStore;
import android.content.pm.PackageManager;
import android.graphics.Typeface;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.accessibility.AccessibilityManager;
import android.widget.EditText;
import android.widget.ImageView;
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

import java.io.InputStream;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * 平板端：连接手机上的 PhoneKB 伴侣服务（WiFi），接收中文/剪贴板并写入本机输入框。
 */
public class MainActivity extends Activity implements LinkService.Listener {

    private static final int REQ_NOTIF = 200;
    private static final int REQ_SCAN = 201;
    private static final int WRAP = ViewGroup.LayoutParams.WRAP_CONTENT;
    private static final int MATCH = ViewGroup.LayoutParams.MATCH_PARENT;

    private static final int BG = 0xFF0F1115;
    private static final int SURFACE = 0xFF171B22;
    private static final int SURFACE_2 = 0xFF1F2530;
    private static final int ACCENT = 0xFF4C8DFF;
    private static final int TEXT = 0xFFE9ECF2;
    private static final int TEXT_DIM = 0xFF98A1B3;
    private static final int OK = 0xFF3DDC84;
    private static final int WARN = 0xFFFFB020;
    private static final int ERR = 0xFFFF5D6A;

    private SharedPreferences prefs;
    private EditText hostInput;
    private EditText tokenInput;
    private TextView netState;
    private TextView accessState;
    private TextView logView;
    private ImageView qrView;
    private TextView qrTip;
    private LinearLayout pairRow;
    private String qrContent;
    private String pairToken;

    private final List<String> logs = new ArrayList<>();
    private Uri pendingCaptureUri;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);
        prefs = getSharedPreferences("companion", MODE_PRIVATE);
        pairToken = LinkService.ensurePairToken(this);
        setContentView(buildUi());
        LinkService.setListener(this);
        hostInput.setText(prefs.getString("host", ""));
        tokenInput.setText(prefs.getString("token", ""));
        maybeRequestNotificationPermission();
        refreshAccessibility();
        refreshNet();
        LinkService.startPairing(this);
        renderQr();
        addLog("平板伴侣已启动。用手机 PhoneKB → 顶栏「伴侣」→ 点「扫码连接平板」扫描本页二维码。");
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
        LinkService.setListener(this);
        refreshAccessibility();
        refreshNet();
        if (LinkService.getState() != LinkService.STATE_CONNECTED) {
            LinkService.startPairing(this);
        }
        renderQr();
    }

    @Override
    protected void onDestroy() {
        LinkService.setListener(null);
        super.onDestroy();
    }

    // ---------------------------------------------------------------- UI

    private View buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(BG);
        root.setPadding(dp(14), dp(12), dp(14), dp(16));

        TextView title = plain("PhoneKB 伴侣", 20, TEXT);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        root.addView(title);
        TextView sub = plain("平板端 · 接收手机发来的文字和剪贴板（WiFi 直连）", 12, TEXT_DIM);
        sub.setPadding(0, dp(3), 0, 0);
        root.addView(sub);

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(false);
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(col, new LinearLayout.LayoutParams(MATCH, WRAP));
        root.addView(scroll, new LinearLayout.LayoutParams(MATCH, 0, 1f));

        // 连接卡
        LinearLayout connectCard = card();
        connectCard.addView(sectionTitle("手机扫码配对"));
        LinearLayout qrRow = new LinearLayout(this);
        qrRow.setOrientation(LinearLayout.HORIZONTAL);
        qrRow.setGravity(Gravity.CENTER_VERTICAL);
        qrView = new ImageView(this);
        qrView.setBackgroundColor(0xFFFFFFFF);
        qrView.setPadding(dp(6), dp(6), dp(6), dp(6));
        qrRow.addView(qrView, new LinearLayout.LayoutParams(dp(168), dp(168)));
        LinearLayout qrCol = new LinearLayout(this);
        qrCol.setOrientation(LinearLayout.VERTICAL);
        qrCol.setPadding(dp(14), 0, 0, 0);
        TextView qrTitle = plain("手机扫这个码", 14, TEXT);
        qrTitle.setTypeface(Typeface.DEFAULT_BOLD);
        qrCol.addView(qrTitle);
        TextView qrHint = plain("手机打开 PhoneKB → 顶栏「伴侣」\n点「扫码连接平板」，对着本页二维码拍一张即可\n自动配对，不用手输 IP 和配对码", 12, TEXT_DIM);
        qrHint.setLineSpacing(dp(3), 1f);
        qrHint.setPadding(0, dp(6), 0, 0);
        qrCol.addView(qrHint);
        qrTip = plain("", 12, TEXT_DIM);
        qrTip.setLineSpacing(dp(3), 1f);
        qrTip.setPadding(0, dp(8), 0, 0);
        qrTip.setTextIsSelectable(true);
        qrCol.addView(qrTip);
        qrRow.addView(qrCol, new LinearLayout.LayoutParams(0, WRAP, 1f));
        pairRow = qrRow;
        connectCard.addView(qrRow, lpMatchWrapWithTop(dp(10)));

        hostInput = new EditText(this);
        hostInput.setHint("手机 IP，例如 192.168.1.5");
        styleInput(hostInput);
        hostInput.setInputType(InputType.TYPE_CLASS_TEXT);
        connectCard.addView(hostInput, lpMatchWrapWithTop(dp(8)));

        tokenInput = new EditText(this);
        tokenInput.setHint("6 位配对码，见手机伴侣页");
        styleInput(tokenInput);
        tokenInput.setInputType(InputType.TYPE_CLASS_NUMBER);
        connectCard.addView(tokenInput, lpMatchWrapWithTop(dp(8)));

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        TextView refreshQr = pillAccent("刷新二维码");
        refreshQr.setOnClickListener(v -> {
            pairToken = LinkService.ensurePairToken(this);
            renderQr();
            LinkService.startPairing(this);
            refreshNet();
            addLog("二维码已刷新");
        });
        row.addView(refreshQr);
        TextView scan = pill("扫手机码（备用）");
        scan.setOnClickListener(v -> startQrScan());
        row.addView(scan);
        TextView connect = pill("手动连接");
        connect.setOnClickListener(v -> connect());
        row.addView(connect);
        TextView disconnect = pill("断开");
        disconnect.setOnClickListener(v -> disconnect());
        row.addView(disconnect);
        connectCard.addView(row, lpWrapWrapWithTop(dp(10)));

        TextView manualTip = plain("下面两项只在扫码失败时手动填写（手机伴侣页也会显示 IP / 配对码）", 11, TEXT_DIM);
        manualTip.setPadding(0, dp(8), 0, 0);
        connectCard.addView(manualTip);

        netState = plain("未连接", 13, TEXT_DIM);
        netState.setPadding(0, dp(8), 0, 0);
        connectCard.addView(netState);
        col.addView(connectCard, cardLp());

        // 无障碍卡
        LinearLayout accCard = card();
        accCard.addView(sectionTitle("无障碍服务（必需）"));
        accessState = plain("", 13, TEXT_DIM);
        accessState.setPadding(0, dp(6), 0, 0);
        accessCard(accCard);
        col.addView(accCard, cardLp());

        // 日志卡
        LinearLayout logCard = card();
        logCard.addView(sectionTitle("最近消息"));
        logView = plain("还没有收到消息。", 12, TEXT_DIM);
        logView.setLineSpacing(dp(3), 1f);
        logView.setTextIsSelectable(true);
        logCard.addView(logView, lpMatchWrapWithTop(dp(6)));
        col.addView(logCard, cardLp());

        // 步骤卡
        LinearLayout guide = card();
        guide.addView(sectionTitle("使用步骤"));
        TextView g = plain(
                "1. 手机和平板连同一个 WiFi；没有路由器就让手机开热点，平板连手机热点\n"
                        + "2. 手机打开 PhoneKB → 顶栏「伴侣」→ 点「扫码连接平板」，对着本页二维码拍一张，自动配对\n"
                        + "3. 开启下面的无障碍服务（只需一次）\n"
                        + "4. 在平板点一下要输入的输入框，手机伴侣页发送中文即可上屏\n"
                        + "5. 平板复制的文字会自动同步到手机剪贴板\n\n"
                        + "也可以反过来：点「扫手机码（备用）」拍手机屏幕上的二维码（旧版配对方式）。\n"
                        + "蓝牙 HID 和伴侣模式互不影响。", 12, TEXT_DIM);
        g.setLineSpacing(dp(3), 1f);
        guide.addView(g, lpMatchWrapWithTop(dp(6)));
        col.addView(guide, cardLp());
        return root;
    }

    private void accessCard(LinearLayout accCard) {
        accCard.addView(accessState);
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        TextView open = pill("打开无障碍设置");
        open.setOnClickListener(v -> {
            try {
                startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
                toast("找到「PhoneKB 伴侣」并打开");
            } catch (Exception e) {
                toast("无法打开系统设置");
            }
        });
        row.addView(open);
        TextView alive = pill("后台保活设置");
        alive.setOnClickListener(v -> openBatterySettings());
        row.addView(alive);
        accCard.addView(row, lpWrapWrapWithTop(dp(10)));
        TextView note = plain(
                "提示：平板重启、或系统清理后台后，无障碍服务可能被系统关掉，需要回来重新打开一次。"
                        + "把本 App 加入「自启动 / 后台运行」白名单，并在最近任务里下拉锁定，可以明显减少重开次数。",
                12, TEXT_DIM);
        note.setLineSpacing(dp(3), 1f);
        accCard.addView(note, lpMatchWrapWithTop(dp(8)));
    }

    // ------------------------------------------------------------- actions

    private void connect() {
        String host = hostInput.getText().toString().trim();
        String token = tokenInput.getText().toString().trim();
        if (host.isEmpty()) {
            toast("先填手机 IP（手机伴侣页上有）");
            return;
        }
        if (token.length() != 6) {
            toast("配对码是手机伴侣页显示的 6 位数字");
            return;
        }
        prefs.edit().putString("host", host).putString("token", token).apply();
        if (!InjectorService.isReady() && !isAccessibilityEnabled()) {
            toast("提醒：无障碍服务还没开，文字会写不进平板（剪贴板仍可用）");
            addLog("⚠ 无障碍未开启，文字无法写入平板");
        }
        LinkService.start(this, host, LinkService.PORT, token);
        addLog("正在连接 " + host + ":" + LinkService.PORT + " …");
        refreshNet();
    }

    private void disconnect() {
        LinkService.stop(this);
        addLog("已断开连接");
        // Go back to "waiting for a phone to scan" so the QR becomes valid again.
        LinkService.startPairing(this);
        renderQr();
        refreshNet();
    }

    // ------------------------------------------------------- QR pairing

    /** Opens the system camera app; the returned photo is decoded locally with zxing. */
    private void startQrScan() {
        try {
            ContentValues values = new ContentValues();
            values.put(MediaStore.Images.Media.DISPLAY_NAME, "phonekb_qr_" + System.currentTimeMillis() + ".jpg");
            values.put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg");
            Uri uri = getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
            if (uri == null) {
                toast("无法准备相机文件，请手动输入 IP");
                return;
            }
            pendingCaptureUri = uri;
            Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
            intent.putExtra(MediaStore.EXTRA_OUTPUT, uri);
            intent.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION | Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivityForResult(intent, REQ_SCAN);
        } catch (Exception e) {
            toast("打不开相机，请手动输入 IP");
            addLog("扫码失败：" + e.getClass().getSimpleName());
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
            toast("没识别到二维码，离手机屏幕近一点再拍一次");
            addLog("扫码未识别，请重试");
            return;
        }
        handlePairText(text);
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

    /** Parses {"v":1,"h":"ip","p":47653,"t":"123456"} from the phone's QR code. */
    private void handlePairText(String text) {
        try {
            String trimmed = text.trim();
            String host;
            String token;
            int port = LinkService.PORT;
            if (trimmed.startsWith("{")) {
                JSONObject o = new JSONObject(trimmed);
                host = o.optString("h", "");
                token = o.optString("t", "");
                port = o.optInt("p", LinkService.PORT);
            } else {
                String[] parts = trimmed.split("\\s+");
                if (parts.length < 2) throw new IllegalArgumentException("bad pair payload");
                host = parts[0];
                token = parts[1];
            }
            if (host.isEmpty() || token.length() != 6) {
                toast("二维码内容不对，请重新生成");
                addLog("扫码内容不对：" + clipForLog(text));
                return;
            }
            hostInput.setText(host);
            tokenInput.setText(token);
            prefs.edit().putString("host", host).putString("token", token).apply();
            addLog("扫码成功：" + host + "，配对码 " + token);
            toast("扫码成功，正在连接…");
            LinkService.start(this, host, port, token);
            refreshNet();
        } catch (Exception e) {
            toast("二维码解析失败，请手动输入 IP");
            addLog("扫码解析失败：" + clipForLog(text));
        }
    }

    /** Battery / background whitelist, the usual reason accessibility gets auto-disabled. */
    private void openBatterySettings() {
        try {
            startActivity(new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                    Uri.parse("package:" + getPackageName())));
            toast("把「PhoneKB 伴侣」设为不限制后台");
            return;
        } catch (Exception ignored) {
        }
        try {
            startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.parse("package:" + getPackageName())));
            toast("在「电池 / 后台运行」里设为不限制");
        } catch (Exception e) {
            toast("请手动到 设置 → 应用管理 → PhoneKB 伴侣 设置后台权限");
        }
    }

    private void applyExtras(Intent intent) {
        if (intent == null) return;
        // Debug/test hook: force the pair token so adb-driven pairing tests are repeatable.
        if (intent.hasExtra("pairtoken")) {
            LinkService.setPairToken(this, intent.getStringExtra("pairtoken"));
            pairToken = LinkService.ensurePairToken(this);
            renderQr();
            LinkService.startPairing(this);
        }
        boolean hasConnect = intent.hasExtra("connect");
        String host = intent.getStringExtra("host");
        String token = intent.getStringExtra("token");
        if (host != null) {
            hostInput.setText(host);
            prefs.edit().putString("host", host).apply();
        }
        if (token != null) {
            tokenInput.setText(token);
            prefs.edit().putString("token", token).apply();
        }
        if (hasConnect) {
            if (intent.getIntExtra("connect", 1) != 0) connect();
            else disconnect();
        }
        // Debug/test hook: put the caret into the first field so an automated
        // integration test has an editable target for text injection.
        if (intent.getIntExtra("focus", 0) == 1 && hostInput != null) {
            hostInput.requestFocus();
        }
    }

    private void maybeRequestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, REQ_NOTIF);
        }
    }

    private void refreshNet() {
        if (netState == null) return;
        int state = LinkService.getState();
        String detail = LinkService.getStateDetail();
        if (state == LinkService.STATE_CONNECTED) {
            netState.setText("● 已连接手机（" + detail + "）");
            netState.setTextColor(OK);
        } else if (state == LinkService.STATE_PAIRING) {
            netState.setText("● 等待手机扫码配对…");
            netState.setTextColor(WARN);
        } else if (state == LinkService.STATE_CONNECTING) {
            netState.setText("● 正在连接… " + detail);
            netState.setTextColor(WARN);
        } else if (state == LinkService.STATE_ERROR) {
            netState.setText("● " + detail);
            netState.setTextColor(ERR);
        } else {
            netState.setText("● 未连接");
            netState.setTextColor(TEXT_DIM);
        }
        // While already connected the QR is pointless (and the pair listener is stopped,
        // so scanning it would fail) — hide it and let the user disconnect first.
        if (pairRow != null) {
            pairRow.setVisibility(state == LinkService.STATE_CONNECTED ? View.GONE : View.VISIBLE);
        }
    }

    /** Renders {"v":2,"h":"<tablet ip>","q":47654,"t":"<pair token>"} for the phone camera. */
    private void renderQr() {
        if (qrView == null) return;
        String ip = LinkService.getLocalIp();
        if (ip == null) {
            qrView.setImageBitmap(null);
            if (qrTip != null) {
                qrTip.setText("没有找到 WiFi 地址：请先把平板和手机连到同一个 WiFi，或让手机开热点给平板连。");
            }
            return;
        }
        String content = "{\"v\":2,\"h\":\"" + ip + "\",\"q\":" + LinkService.PAIR_PORT
                + ",\"t\":\"" + (pairToken == null ? "" : pairToken) + "\"}";
        if (!content.equals(qrContent)) {
            Bitmap bmp = QrCode.encode(content, 512);
            if (bmp != null) {
                qrContent = content;
                qrView.setImageBitmap(bmp);
            }
        }
        if (qrTip != null) {
            qrTip.setText("本机 IP：" + ip + " · 配对端口 " + LinkService.PAIR_PORT
                    + "\n手机扫本页二维码即可，不需要手输 IP / 配对码");
        }
    }

    private void refreshAccessibility() {
        if (accessState == null) return;
        boolean on = InjectorService.isReady() || isAccessibilityEnabled();
        if (on) {
            accessState.setText("● 无障碍服务已开启，可以接收文字");
            accessState.setTextColor(OK);
        } else {
            accessState.setText("● 未开启：点下面的按钮，在列表里打开「PhoneKB 伴侣」");
            accessState.setTextColor(WARN);
        }
    }

    private boolean isAccessibilityEnabled() {
        try {
            AccessibilityManager am = (AccessibilityManager) getSystemService(Context.ACCESSIBILITY_SERVICE);
            if (am == null) return false;
            List<AccessibilityServiceInfo> list =
                    am.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK);
            String want = InjectorService.class.getName();
            for (AccessibilityServiceInfo info : list) {
                String id = info.getId();
                if (id != null && id.contains(want)) return true;
            }
        } catch (Exception ignored) {
        }
        return false;
    }

    // ------------------------------------------------------------ listener

    @Override
    public void onLinkState(int state, String detail) {
        refreshNet();
        if (state == LinkService.STATE_CONNECTED) addLog("● 已连接手机：" + detail);
        else if (state == LinkService.STATE_ERROR) addLog("● 连接异常：" + detail);
    }

    @Override
    public void onIncoming(String kind, String text) {
        if ("clip".equals(kind)) {
            addLog("← 收到剪贴板：" + clipForLog(text));
        } else {
            addLog("← 收到文字：" + clipForLog(text));
        }
        refreshAccessibility();
    }

    @Override
    public void onInjected(String kind, String result) {
        if ("ok".equals(result)) addLog("→ 已写入输入框 ✓");
        else addLog("→ 写入失败：" + result);
        refreshAccessibility();
    }

    // ------------------------------------------------------------- helpers

    private void addLog(String line) {
        if (line == null) return;
        logs.add(0, line);
        while (logs.size() > 30) logs.remove(logs.size() - 1);
        if (logView != null) {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < logs.size(); i++) {
                if (i > 0) sb.append('\n');
                sb.append(logs.get(i));
            }
            logView.setText(sb.toString());
        }
    }

    private String clipForLog(String text) {
        if (text == null) return "";
        String one = text.replace('\n', ' ');
        return one.length() > 60 ? one.substring(0, 60) + "…" : one;
    }

    private void toast(String msg) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
    }

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density + 0.5f);
    }

    private TextView plain(String text, int sizeSp, int color) {
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextSize(sizeSp);
        tv.setTextColor(color);
        return tv;
    }

    private TextView sectionTitle(String text) {
        TextView tv = plain(text, 15, TEXT);
        tv.setTypeface(Typeface.DEFAULT_BOLD);
        return tv;
    }

    private void styleInput(EditText input) {
        input.setHintTextColor(TEXT_DIM);
        input.setTextColor(TEXT);
        input.setTextSize(15);
        input.setBackground(rounded(SURFACE_2, 12));
        input.setPadding(dp(12), dp(10), dp(12), dp(10));
        input.setSingleLine(true);
    }

    private TextView pill(String text) {
        TextView tv = plain(text, 13, TEXT);
        tv.setPadding(dp(14), dp(9), dp(14), dp(9));
        tv.setBackground(rounded(SURFACE_2, 12));
        return tv;
    }

    private TextView pillAccent(String text) {
        TextView tv = plain(text, 13, BG);
        tv.setTypeface(Typeface.DEFAULT_BOLD);
        tv.setPadding(dp(18), dp(10), dp(18), dp(10));
        tv.setBackground(rounded(ACCENT, 12));
        return tv;
    }

    private android.graphics.drawable.GradientDrawable rounded(int color, int radiusDp) {
        android.graphics.drawable.GradientDrawable d = new android.graphics.drawable.GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(dp(radiusDp));
        return d;
    }

    private LinearLayout card() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackground(rounded(SURFACE, 16));
        card.setPadding(dp(14), dp(12), dp(14), dp(14));
        return card;
    }

    private LinearLayout.LayoutParams cardLp() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(MATCH, WRAP);
        lp.setMargins(0, dp(12), 0, 0);
        return lp;
    }

    private LinearLayout.LayoutParams lpMatchWrapWithTop(int top) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(MATCH, WRAP);
        lp.setMargins(0, top, 0, 0);
        return lp;
    }

    private LinearLayout.LayoutParams lpWrapWrapWithTop(int top) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(WRAP, WRAP);
        lp.setMargins(0, top, 0, 0);
        return lp;
    }
}
