package com.phonekb;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.os.Build;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

public class MainActivity extends Activity {

    private static final String TAG = "PhoneKB";

    private static final String[] TAB_NAMES = {"键盘", "触控板", "媒体", "手柄", "双手"};
    private static final int TAB_KEYBOARD = 0;
    private static final int TAB_TOUCHPAD = 1;
    private static final int TAB_MEDIA = 2;
    private static final int TAB_GAMEPAD = 3;
    private static final int TAB_TWOHAND = 4;

    private static final int OVERLAY_NONE = 0;
    private static final int OVERLAY_NUMPAD = 1;
    private static final int OVERLAY_KEYBOARD = 2;
    private static final int OVERLAY_TOUCHPAD = 3;

    private static final int REQ_PERM = 100;
    private static final int REQ_ENABLE = 101;
    private static final int REQ_DISCOVERABLE = 102;

    private HidManager hid;
    private SharedPreferences prefs;

    private LinearLayout chrome;
    private FrameLayout contentFrame;
    private FrameLayout overlayLayer;
    private LinearLayout overlayPanel;
    private TextView overlayTitle;
    private NumpadView numpadView;
    private KeyboardView overlayKeyboard;
    private ControlPadView overlayTouchpad;
    private TextView statusDot;
    private TextView statusTitle;
    private TextView statusDetail;
    private TextView pairButton;
    private final TextView[] tabs = new TextView[TAB_NAMES.length];
    private TextView oneHandPill;
    private TextView handPill;
    private TextView footer;
    private PowerButtonView powerButton;
    private TextView rotateButton;
    private TextView swapPill;
    private TextView companionPill;
    private boolean twoHandSwapped;

    private final View[] pages = new View[TAB_NAMES.length];
    private KeyboardView keyboardView;
    private ControlPadView touchpadView;
    private MediaView mediaView;
    private GamepadView gamepadView;
    private TwoHandView twoHandView;

    private FrameLayout oneHandContainer;
    private OneHandDeckView deckView;
    private LinearLayout thumbContainer;
    private FrameLayout thumbEmpty;
    private KeyboardView thumbKeyboard;

    private boolean landscape;
    private boolean oneHandMode;
    private boolean thumbMode;
    private boolean rightHanded = true;
    private int currentTab = TAB_KEYBOARD;
    private int overlay = OVERLAY_NONE;

    private static final float SPLIT_DEFAULT = 0.58f;
    private static final float SPLIT_MIN = 0.30f;
    private static final float SPLIT_MAX = 0.62f;
    private static final float[] SPLIT_SNAPS = {0.48f, 0.58f, 0.62f};

    private static final float DOCK_TOUCH_DEFAULT = 0.80f;
    private static final float DOCK_TOUCH_MIN = 0.45f;
    private static final float DOCK_MEDIA_DEFAULT = 0.78f;
    private static final float DOCK_MEDIA_MIN = 0.55f;

    private float kbSplit = SPLIT_DEFAULT;
    private LinearLayout splitColumn;
    private View splitPadView;
    private View splitKeyboardCard;
    private SplitHandleView splitHandle;
    private DockPage touchDock;
    private DockPage mediaDock;

    private final HidManager.Listener hidListener = new HidManager.Listener() {
        @Override
        public void onHidStatus(int state, String title, String detail) {
            applyStatus();
        }
    };

    /** Debug-only override: pretend this display is narrower (adb --ei simw). */
    private int simWidthDp;
    /** Debug-only override: pretend the system font scale is different (adb --ef simfont). */
    private float simFontScale;

    /** Current adaptive toolbar (landscape compact bar or portrait header), for debug hooks. */
    private ChromeBar chromeBar;

    private final Runnable retryIfError = new Runnable() {
        @Override
        public void run() {
            if (hid != null && hid.getState() == HidManager.STATE_ERROR) hid.start();
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Adaptive UI: read the debug simulation extras before anything calls Ui.dp().
        Intent launchIntent = getIntent();
        simWidthDp = launchIntent != null ? launchIntent.getIntExtra("simw", 0) : 0;
        simFontScale = launchIntent != null ? launchIntent.getFloatExtra("simfont", 0f) : 0f;
        int uiSizeExtra = launchIntent != null ? launchIntent.getIntExtra("uisize", -1) : -1;
        if (uiSizeExtra >= 0 && uiSizeExtra <= Ui.UI_SIZE_LARGE) {
            Ui.writeManualMode(this, uiSizeExtra);
        }
        Ui.setSimulation(simWidthDp, simFontScale);
        Ui.init(this);
        // Draw edge-to-edge and let installInsetsPadding() turn the reported insets into
        // padding. That is the only reliable way to be correct on notch/gesture-bar devices.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            getWindow().setDecorFitsSystemWindows(false);
        }
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        getWindow().setStatusBarColor(Ui.BG);
        getWindow().setNavigationBarColor(Ui.BG);
        prefs = getSharedPreferences("phonekb", MODE_PRIVATE);
        rightHanded = prefs.getBoolean("right_handed", true);
        oneHandMode = prefs.getBoolean("one_hand", false);
        twoHandSwapped = prefs.getBoolean("twohand_swapped", false);
        currentTab = Math.max(0, Math.min(TAB_NAMES.length - 1, prefs.getInt("last_tab", TAB_KEYBOARD)));
        landscape = getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE;
        kbSplit = Math.max(SPLIT_MIN, Math.min(SPLIT_MAX,
                prefs.getFloat("kb_split", SPLIT_DEFAULT)));

        hid = HidManager.get(this);
        hid.setListener(hidListener);

        setContentView(buildUi());
        applyStatus();
        applyModeVisibility();
        applyIntentExtras(getIntent());
        startKeepAlive();
        maybeShowFirstRunHelp();
        if (contentFrame != null) contentFrame.post(this::applyImmersive);
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        if (intent == null) return;
        int newSimW = intent.getIntExtra("simw", simWidthDp);
        float newSimF = intent.getFloatExtra("simfont", simFontScale);
        boolean hasUiSize = intent.hasExtra("uisize");
        if (hasUiSize) {
            Ui.writeManualMode(this, intent.getIntExtra("uisize", Ui.readManualMode(this)));
        }
        setIntent(intent);
        if (hasUiSize || newSimW != simWidthDp
                || Math.abs(newSimF - simFontScale) > 0.001f) {
            // Metrics changed: rebuild every view from scratch so the new scale is applied
            // everywhere (view sizes are baked in at construction time).
            recreate();
            return;
        }
        applyIntentExtras(intent);
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        Ui.setSimulation(simWidthDp, simFontScale);
        Ui.init(this);
        landscape = newConfig.orientation == Configuration.ORIENTATION_LANDSCAPE;
        int keepOverlay = overlay;
        setContentView(buildUi());
        applyStatus();
        applyModeVisibility();
        setOverlay(keepOverlay);
        if (contentFrame != null) contentFrame.post(this::applyImmersive);
    }

    @Override
    protected void onStart() {
        super.onStart();
        ensurePermission();
    }

    @Override
    protected void onPause() {
        // A floating overflow menu must not survive the activity going away (rotation, recreate, tablet switch).
        if (chromeBar != null) chromeBar.dismissOverflow();
        super.onPause();
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) applyImmersive();
    }

    @Override
    protected void onDestroy() {
        // Do NOT unregister HID or stop KeepAliveService here: MIUI destroys the activity
        // when it goes to background, which used to drop the HID registration and made the
        // tablet silently lose keyboard/mouse input. KeepAliveService owns the HID lifetime.
        super.onDestroy();
    }

    // ------------------------------------------------------------------- UI

    private View buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Ui.BG);

        chrome = new LinearLayout(this);
        chrome.setOrientation(LinearLayout.VERTICAL);
        buildChrome();
        root.addView(chrome, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        contentFrame = new FrameLayout(this);
        buildContent();
        buildOverlay();
        root.addView(contentFrame, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        footer = new TextView(this);
        footer.setText("PhoneKB v0.23.2 · 蓝牙 HID · 键盘 / 触控板 / 媒体 / 手柄 / 双手 · 伴侣 WiFi");
        footer.setTextColor(Ui.TEXT_DIM);
        footer.setTextSize(11);
        footer.setGravity(Gravity.CENTER);
        footer.setPadding(0, dp(3), 0, dp(3));
        // The footer is hidden in both orientations: in portrait the keyboard is docked to
        // the very bottom, so there is no room (and no need) for a status line.
        footer.setVisibility(View.GONE);
        root.addView(footer, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        // Window insets: instead of guessing status-bar heights, pad the real root view with
        // whatever the system reports (cutouts, gesture bar, foldable hinge included). This
        // keeps the layout correct on every device the app might be installed on.
        installInsetsPadding(root);

        // Narrow-screen simulation: run the exact same UI on a narrower, left-aligned strip
        // of this display, so layout problems are reachable from adb without another phone.
        int sim = Ui.simulatedWidthDp();
        if (sim > 0 && sim < Ui.realWidthDp()) {
            FrameLayout holder = new FrameLayout(this);
            holder.setBackgroundColor(Ui.BG);
            DisplayMetrics dm = getResources().getDisplayMetrics();
            // In portrait the simulated window is exactly sim dp wide. In landscape the same
            // device would be wider by its aspect ratio, so scale the width accordingly.
            int designWidthDp = sim;
            if (landscape) {
                float realLandscapeDp = dm.widthPixels / Math.max(1f, dm.density);
                designWidthDp = Math.round(sim * realLandscapeDp / Math.max(1f, Ui.realWidthDp()));
            }
            int simPx = Math.min(dm.widthPixels, Math.max(dp(240), Math.round(Ui.dp(this, designWidthDp))));
            FrameLayout.LayoutParams holderLp = new FrameLayout.LayoutParams(
                    simPx, ViewGroup.LayoutParams.MATCH_PARENT);
            holderLp.gravity = Gravity.START | Gravity.TOP;
            holder.addView(root, holderLp);
            return holder;
        }
        return root;
    }

    /** Pads {@code view} by the real system bar / cutout insets (API 30+ and legacy). */
    private void installInsetsPadding(View view) {
        if (view == null) return;
        view.setOnApplyWindowInsetsListener((v, insets) -> {
            int left;
            int top;
            int right;
            int bottom;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                android.graphics.Insets cut = insets.getInsets(WindowInsets.Type.displayCutout());
                android.graphics.Insets bars = insets.getInsets(WindowInsets.Type.systemBars());
                left = Math.max(cut.left, bars.left);
                top = Math.max(cut.top, bars.top);
                right = Math.max(cut.right, bars.right);
                bottom = Math.max(cut.bottom, bars.bottom);
            } else {
                left = insets.getSystemWindowInsetLeft();
                top = insets.getSystemWindowInsetTop();
                right = insets.getSystemWindowInsetRight();
                bottom = insets.getSystemWindowInsetBottom();
            }
            if (v.getPaddingLeft() != left || v.getPaddingTop() != top
                    || v.getPaddingRight() != right || v.getPaddingBottom() != bottom) {
                v.setPadding(left, top, right, bottom);
            }
            return insets;
        });
        view.requestApplyInsets();
    }

    private void buildChrome() {
        if (chrome == null) return;
        if (chromeBar != null) chromeBar.dismissOverflow();
        chrome.removeAllViews();
        chromeBar = null;
        if (landscape) buildCompactChrome();
        else {
            buildHeader();
            buildPortraitTabBar();
        }
        refreshSwapPill();
    }

    private TextView plainText(String text, float sp, int color) {
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextSize(sp);
        tv.setTextColor(color);
        tv.setGravity(Gravity.CENTER);
        tv.setTypeface(android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL));
        return tv;
    }

    private int dp(float value) {
        return (int) Ui.dp(this, value);
    }

    private void buildCompactChrome() {
        ChromeBar bar = new ChromeBar(this);
        bar.setPadding(dp(10), dp(5), dp(10), dp(5));
        bar.setMinTitleWidthDp(84);
        chromeBar = bar;
        chrome.addView(bar, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        powerButton = new PowerButtonView(this);
        powerButton.setListener(() -> hid.tapConsumer(HidConstants.CONSUMER_POWER));
        LinearLayout.LayoutParams pLp = new LinearLayout.LayoutParams(Ui.dpi(this, 54), Ui.dpi(this, 44));
        pLp.setMarginEnd(dp(10));
        bar.addFixed(powerButton, pLp);

        statusDot = plainText("●", 12, Ui.WARN);
        bar.addFixed(statusDot, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        statusTitle = plainText("未连接", 14, Ui.TEXT);
        statusTitle.setSingleLine(true);
        statusTitle.setEllipsize(TextUtils.TruncateAt.END);
        statusTitle.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
        statusTitle.setOnClickListener(v -> retryIfError.run());
        LinearLayout.LayoutParams tLp = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        tLp.leftMargin = dp(7);
        tLp.rightMargin = dp(8);
        bar.addFixed(statusTitle, tLp);

        for (int i = 0; i < TAB_NAMES.length; i++) {
            final int index = i;
            TextView tab = chromePill(TAB_NAMES[i]);
            tab.setOnClickListener(v -> selectTab(index));
            tabs[i] = tab;
            bar.addFixed(tab, chromePillLp(62));
        }

        oneHandPill = chromePill("单手");
        oneHandPill.setOnClickListener(v -> toggleOneHand());
        bar.addCollapsible(oneHandPill, chromePillLp(56), "单手", this::toggleOneHand);

        handPill = chromePill("右手");
        handPill.setOnClickListener(v -> toggleHand());
        bar.addCollapsible(handPill, chromePillLp(56),
                () -> rightHanded ? "右手" : "左手", this::toggleHand);

        swapPill = chromePill("互换");
        swapPill.setOnClickListener(v -> toggleTwoHandSwap());
        swapPill.setBackground(Ui.rounded(this, Ui.SURFACE_2, 12));
        bar.addCollapsible(swapPill, chromePillLp(52), "互换", this::toggleTwoHandSwap);

        rotateButton = chromePill("旋转");
        rotateButton.setTextColor(Ui.TEXT);
        rotateButton.setBackground(Ui.rounded(this, Ui.SURFACE_2, 12));
        rotateButton.setOnClickListener(v -> toggleOrientation());
        rotateButton.setOnLongClickListener(v -> { resetOrientationToSystem(); return true; });
        bar.addCollapsible(rotateButton, chromePillLp(52), "旋转", this::toggleOrientation);

        companionPill = chromePill("伴侣");
        companionPill.setTextColor(Ui.TEXT);
        companionPill.setBackground(Ui.rounded(this, Ui.SURFACE_2, 12));
        companionPill.setOnClickListener(v -> openCompanion());
        bar.addCollapsible(companionPill, chromePillLp(56), "伴侣", this::openCompanion);

        pairButton = chromePill("配对");
        pairButton.setTextColor(Ui.TEXT);
        pairButton.setBackground(Ui.rounded(this, Ui.SURFACE_2, 12));
        pairButton.setOnClickListener(v -> onPairClicked());
        bar.addCollapsible(pairButton, chromePillLp(60),
                () -> hid != null && hid.isConnected() ? "帮助" : "配对", this::onPairClicked);
    }

    private void buildHeader() {
        ChromeBar header = new ChromeBar(this);
        header.setPadding(dp(16), dp(10), dp(16), dp(4));
        header.setMinTitleWidthDp(96);
        chromeBar = header;
        chrome.addView(header, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        powerButton = new PowerButtonView(this);
        powerButton.setListener(() -> hid.tapConsumer(HidConstants.CONSUMER_POWER));
        LinearLayout.LayoutParams pLp = new LinearLayout.LayoutParams(Ui.dpi(this, 54), Ui.dpi(this, 48));
        pLp.setMarginEnd(dp(10));
        header.addFixed(powerButton, pLp);

        statusDot = plainText("●", 13, Ui.WARN);
        header.addFixed(statusDot, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout texts = new LinearLayout(this);
        texts.setOrientation(LinearLayout.VERTICAL);
        texts.setPadding(dp(8), 0, 0, 0);
        header.addFixed(texts, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        statusTitle = plainText("未连接", 16, Ui.TEXT);
        statusTitle.setGravity(Gravity.START);
        statusTitle.setSingleLine(true);
        statusTitle.setEllipsize(TextUtils.TruncateAt.END);
        statusTitle.setOnClickListener(v -> retryIfError.run());
        texts.addView(statusTitle, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        statusDetail = plainText("", 12, Ui.TEXT_DIM);
        statusDetail.setGravity(Gravity.START);
        statusDetail.setSingleLine(true);
        statusDetail.setEllipsize(TextUtils.TruncateAt.END);
        texts.addView(statusDetail, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        swapPill = plainText("互换", 13, Ui.TEXT_DIM);
        swapPill.setPadding(dp(12), dp(8), dp(12), dp(8));
        swapPill.setBackground(Ui.rounded(this, Ui.SURFACE_2, 12));
        swapPill.setOnClickListener(v -> toggleTwoHandSwap());
        LinearLayout.LayoutParams swLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        swLp.setMarginEnd(dp(8));
        header.addCollapsible(swapPill, swLp, "互换", this::toggleTwoHandSwap);

        rotateButton = plainText("旋转", 13, Ui.TEXT_DIM);
        rotateButton.setPadding(dp(12), dp(8), dp(12), dp(8));
        rotateButton.setBackground(Ui.rounded(this, Ui.SURFACE_2, 12));
        rotateButton.setOnClickListener(v -> toggleOrientation());
        rotateButton.setOnLongClickListener(v -> { resetOrientationToSystem(); return true; });
        LinearLayout.LayoutParams rLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        rLp.setMarginEnd(dp(8));
        header.addCollapsible(rotateButton, rLp, "旋转", this::toggleOrientation);

        companionPill = plainText("伴侣", 13, Ui.TEXT_DIM);
        companionPill.setPadding(dp(12), dp(8), dp(12), dp(8));
        companionPill.setBackground(Ui.rounded(this, Ui.SURFACE_2, 12));
        companionPill.setOnClickListener(v -> openCompanion());
        LinearLayout.LayoutParams cLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        cLp.setMarginEnd(dp(8));
        header.addCollapsible(companionPill, cLp, "伴侣", this::openCompanion);

        pairButton = plainText("配对", 13, Ui.TEXT_DIM);
        pairButton.setPadding(dp(14), dp(8), dp(14), dp(8));
        pairButton.setBackground(Ui.rounded(this, Ui.SURFACE_2, 12));
        pairButton.setOnClickListener(v -> onPairClicked());
        header.addCollapsible(pairButton, new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT),
                () -> hid != null && hid.isConnected() ? "帮助" : "配对", this::onPairClicked);
    }

    private void buildPortraitTabBar() {
        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(dp(6), dp(6), dp(6), dp(8));
        chrome.addView(bar, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        for (int i = 0; i < TAB_NAMES.length; i++) {
            final int index = i;
            TextView cell = portraitCell(TAB_NAMES[i]);
            cell.setOnClickListener(v -> selectTab(index));
            tabs[i] = cell;
            bar.addView(cell, portraitCellLp());
        }
        oneHandPill = portraitCell("单手");
        oneHandPill.setOnClickListener(v -> toggleOneHand());
        bar.addView(oneHandPill, portraitCellLp());
        handPill = portraitCell("右手");
        handPill.setOnClickListener(v -> toggleHand());
        handPill.setVisibility(View.GONE);
        bar.addView(handPill, handPillLp());
    }

    private LinearLayout.LayoutParams handPillLp() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 0f);
        lp.setMargins(dp(2), 0, dp(2), 0);
        return lp;
    }

    private TextView chromePill(String label) {
        TextView tv = plainText(label, 13, Ui.TEXT_DIM);
        tv.setSingleLine(true);
        Ui.autoSize(tv, 13, 9);
        return tv;
    }

    private LinearLayout.LayoutParams chromePillLp(int widthDp) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(Ui.dpi(this, widthDp), Ui.dpi(this, 34));
        lp.setMargins(dp(2), 0, dp(2), 0);
        return lp;
    }

    private TextView portraitCell(String label) {
        TextView tv = plainText(label, 13, Ui.TEXT_DIM);
        tv.setSingleLine(true);
        tv.setGravity(Gravity.CENTER);
        Ui.autoSize(tv, 13, 9);
        return tv;
    }

    private LinearLayout.LayoutParams portraitCellLp() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        lp.setMargins(dp(2), 0, dp(2), 0);
        lp.height = Ui.dpi(this, 38);
        return lp;
    }

    private void buildContent() {
        if (contentFrame == null) return;
        splitColumn = null;
        splitPadView = null;
        splitKeyboardCard = null;
        splitHandle = null;
        touchDock = null;
        mediaDock = null;
        keyboardView = new KeyboardView(this, hid);
        keyboardView.setLayout(prefs.getInt("kb_layout", KeyboardView.LAYOUT_PHONE));
        keyboardView.setListener(layout -> {
            prefs.edit().putInt("kb_layout", layout).apply();
            toast(layout == KeyboardView.LAYOUT_PHONE ? "已切换到手机布局" : "已切换到电脑布局");
        });
        keyboardView.setTouchpadAction(() -> toggleOverlay(OVERLAY_TOUCHPAD));
        touchpadView = new ControlPadView(this, hid);
        touchpadView.setListener(new ControlPadView.Listener() {
            @Override
            public void onToggleNumpad() {
                toggleOverlay(OVERLAY_NUMPAD);
            }

            @Override
            public void onToggleKeyboard() {
                toggleOverlay(OVERLAY_KEYBOARD);
            }
        });
        mediaView = new MediaView(this, hid);
        mediaView.setBrightnessListener(up -> {
            final CompanionBridge bridge = CompanionBridge.get(this);
            if (bridge.isConnected()) {
                bridge.sendBright(up);
            } else {
                Toast.makeText(this,
                        "这台平板不响应蓝牙亮度键。打开顶部「伴侣」并连上 WiFi 通道后，亮度键改走 WiFi 直接调系统亮度。",
                        Toast.LENGTH_LONG).show();
            }
        });
        if (!landscape) {
            mediaView.setToolpadRow(true);
            mediaView.setTouchpadAction(() -> toggleOverlay(OVERLAY_TOUCHPAD));
        }
        gamepadView = new GamepadView(this, hid);
        twoHandView = new TwoHandView(this, hid);
        twoHandView.setListener(new TwoHandView.Listener() {
            @Override
            public void onToggleNumpad() {
                toggleOverlay(OVERLAY_NUMPAD);
            }

            @Override
            public void onToggleKeyboard() {
                toggleOverlay(OVERLAY_KEYBOARD);
            }
        });
        // Portrait: the keyboard page becomes touchpad (top) + docked keyboard (bottom)
        // so the keys keep normal phone-keyboard proportions instead of stretching.
        pages[TAB_KEYBOARD] = landscape ? keyboardView : buildPortraitKeyboardPage();
        if (landscape) {
            pages[TAB_TOUCHPAD] = touchpadView;
            pages[TAB_MEDIA] = mediaView;
        } else {
            touchDock = new DockPage("dock_touch", DOCK_TOUCH_DEFAULT, DOCK_TOUCH_MIN);
            pages[TAB_TOUCHPAD] = buildDockPage(touchDock, touchpadView);
            mediaDock = new DockPage("dock_media", DOCK_MEDIA_DEFAULT, DOCK_MEDIA_MIN);
            pages[TAB_MEDIA] = buildDockPage(mediaDock, mediaView);
        }
        pages[TAB_GAMEPAD] = gamepadView;
        pages[TAB_TWOHAND] = twoHandView;
        twoHandView.setSwapped(twoHandSwapped);
        for (View page : pages) {
            page.setVisibility(View.GONE);
            contentFrame.addView(page, matchParent());
        }

        oneHandContainer = new FrameLayout(this);
        oneHandContainer.setVisibility(View.GONE);
        contentFrame.addView(oneHandContainer, matchParent());

        deckView = new OneHandDeckView(this, hid);
        deckView.setListener(this::switchToThumbKeyboard);
        deckView.setRightHanded(rightHanded);
        oneHandContainer.addView(deckView, matchParent());

        thumbContainer = new LinearLayout(this);
        oneHandContainer.addView(thumbContainer, matchParent());
        thumbEmpty = new FrameLayout(this);
        buildThumbEmpty();
        thumbKeyboard = new KeyboardView(this, hid, true);
        applyOneHandLayout();
    }

    /**
     * Portrait keyboard page: a bare touchpad card on top and the keyboard docked to the
     * bottom, with a draggable divider between them so the user can trade height between
     * the two halves. The ratio is remembered across launches.
     */
    private View buildPortraitKeyboardPage() {
        splitColumn = new LinearLayout(this);
        splitColumn.setOrientation(LinearLayout.VERTICAL);
        splitColumn.setBackgroundColor(Ui.BG);

        splitPadView = new ControlPadView(this, hid, true, true);
        splitColumn.addView(splitPadView, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, kbSplit));

        splitHandle = new SplitHandleView(this);
        splitHandle.setShare(kbSplit);
        splitHandle.setListener(new SplitHandleView.Listener() {
            @Override
            public void onDrag(float rawY) {
                dragPortraitSplit(rawY);
            }

            @Override
            public void onCommit() {
                prefs.edit().putFloat("kb_split", kbSplit).apply();
            }

            @Override
            public void onReset() {
                applyPortraitSplit(SPLIT_DEFAULT);
                prefs.edit().putFloat("kb_split", SPLIT_DEFAULT).apply();
                toast("已恢复默认比例");
            }
        });
        splitColumn.addView(splitHandle, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, splitHandleHeightPx()));

        FrameLayout keyboardCard = new FrameLayout(this);
        keyboardCard.setBackground(Ui.roundedTop(this, Ui.SURFACE, 22));
        keyboardCard.setClipToOutline(true);
        keyboardView.setPanelBackground(Ui.SURFACE);
        keyboardCard.addView(keyboardView, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        splitKeyboardCard = keyboardCard;
        LinearLayout.LayoutParams cardLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f - kbSplit);
        splitColumn.addView(keyboardCard, cardLp);
        return splitColumn;
    }

    private int splitHandleHeightPx() {
        return Ui.dpi(this, 30);
    }

    /**
     * Portrait dock page: the controls sit on the bottom inside a resizable panel and
     * the area above it is left empty, so the whole function area can be pulled down
     * into comfortable thumb reach. The divider sits on the panel's top edge.
     */
    private View buildDockPage(DockPage page, View content) {
        page.share = Math.max(page.min, Math.min(page.max, prefs.getFloat(page.key, page.def)));
        page.column = new LinearLayout(this);
        page.column.setOrientation(LinearLayout.VERTICAL);
        page.column.setBackgroundColor(Ui.BG);

        FrameLayout blankFrame = new FrameLayout(this);
        page.hint = new TextView(this);
        page.hint.setText("上方留空 · 拖动下方分隔条调整功能区高度\n（功能区贴着屏幕底部，方便拇指操作）");
        page.hint.setTextColor(Ui.TEXT_DIM);
        page.hint.setTextSize(13);
        page.hint.setGravity(Gravity.CENTER);
        page.hint.setLineSpacing(dp(3), 1f);
        FrameLayout.LayoutParams hintLp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        hintLp.gravity = Gravity.CENTER;
        blankFrame.addView(page.hint, hintLp);
        blankFrame.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) ->
                page.hint.setVisibility((b - t) >= dp(70) ? View.VISIBLE : View.INVISIBLE));
        page.blank = blankFrame;
        page.column.addView(blankFrame, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f - page.share));

        page.handle = new SplitHandleView(this);
        page.handle.setDockMode(true, page.min, page.max);
        page.handle.setShare(page.share);
        page.handle.setListener(new SplitHandleView.Listener() {
            @Override
            public void onDrag(float rawY) {
                dragDockPage(page, rawY);
            }

            @Override
            public void onCommit() {
                prefs.edit().putFloat(page.key, page.share).apply();
            }

            @Override
            public void onReset() {
                applyDockShare(page, page.def);
                prefs.edit().putFloat(page.key, page.def).apply();
                toast("已恢复默认比例");
            }
        });
        page.column.addView(page.handle, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, splitHandleHeightPx()));

        FrameLayout panel = new FrameLayout(this);
        panel.addView(content, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        page.panel = panel;
        page.column.addView(panel, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, page.share));
        return page.column;
    }

    /** Maps a raw finger Y to the docked-panel share while the divider is dragged. */
    private void dragDockPage(DockPage page, float rawY) {
        if (page.column == null || page.column.getHeight() <= 0) return;
        int[] location = new int[2];
        page.column.getLocationOnScreen(location);
        float available = page.column.getHeight() - splitHandleHeightPx();
        if (available <= 0) return;
        float dividerCenter = rawY - location[1] - splitHandleHeightPx() / 2f;
        applyDockShare(page, 1f - dividerCenter / available);
    }

    /** Applies the docked-panel share (percent of the content area) to the layout. */
    private void applyDockShare(DockPage page, float share) {
        share = Math.max(page.min, Math.min(page.max, share));
        page.share = share;
        if (page.blank != null) {
            LinearLayout.LayoutParams lp = (LinearLayout.LayoutParams) page.blank.getLayoutParams();
            float blankWeight = 1f - share;
            if (lp.weight != blankWeight) {
                lp.weight = blankWeight;
                page.blank.setLayoutParams(lp);
            }
        }
        if (page.panel != null) {
            LinearLayout.LayoutParams lp = (LinearLayout.LayoutParams) page.panel.getLayoutParams();
            if (lp.weight != share) {
                lp.weight = share;
                page.panel.setLayoutParams(lp);
            }
        }
        if (page.handle != null) page.handle.setShare(share);
    }

    private static final class DockPage {
        final String key;
        final float def;
        final float min;
        final float max = 1f;
        float share;
        LinearLayout column;
        View blank;
        TextView hint;
        SplitHandleView handle;
        View panel;

        DockPage(String key, float def, float min) {
            this.key = key;
            this.def = def;
            this.min = min;
        }
    }

    /** Maps a raw finger Y to the touch-area share while the divider is dragged. */
    private void dragPortraitSplit(float rawY) {
        if (splitColumn == null || splitColumn.getHeight() <= 0) return;
        int[] location = new int[2];
        splitColumn.getLocationOnScreen(location);
        float available = splitColumn.getHeight() - splitHandleHeightPx();
        if (available <= 0) return;
        float padPx = rawY - location[1] - splitHandleHeightPx() / 2f;
        applyPortraitSplit(padPx / available);
    }

    /** Applies the touch-area share (0.30..0.62) with snapping to the three presets. */
    private void applyPortraitSplit(float share) {
        share = Math.max(SPLIT_MIN, Math.min(SPLIT_MAX, share));
        for (float target : SPLIT_SNAPS) {
            if (Math.abs(share - target) < 0.014f) {
                share = target;
                break;
            }
        }
        kbSplit = share;
        if (splitPadView != null) {
            LinearLayout.LayoutParams lp = (LinearLayout.LayoutParams) splitPadView.getLayoutParams();
            if (lp.weight != share) {
                lp.weight = share;
                splitPadView.setLayoutParams(lp);
            }
        }
        if (splitKeyboardCard != null) {
            LinearLayout.LayoutParams lp = (LinearLayout.LayoutParams) splitKeyboardCard.getLayoutParams();
            float keyboardWeight = 1f - share;
            if (lp.weight != keyboardWeight) {
                lp.weight = keyboardWeight;
                splitKeyboardCard.setLayoutParams(lp);
            }
        }
        if (splitHandle != null) splitHandle.setShare(share);
    }

    private FrameLayout.LayoutParams matchParent() {
        return new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT);
    }

    private void buildThumbEmpty() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(Gravity.CENTER);
        TextView switchBtn = plainText("切到遥控台", 18, Ui.TEXT);
        switchBtn.setSingleLine(true);
        switchBtn.setBackground(Ui.rounded(this, Ui.SURFACE_2, 16));
        switchBtn.setOnClickListener(v -> switchToDeck());
        LinearLayout.LayoutParams bLp = new LinearLayout.LayoutParams(Ui.dpi(this, 200), Ui.dpi(this, 62));
        box.addView(switchBtn, bLp);

        TextView handBtn = plainText("切换左右手", 14, Ui.TEXT_DIM);
        handBtn.setSingleLine(true);
        handBtn.setPadding(0, dp(14), 0, dp(14));
        handBtn.setOnClickListener(v -> toggleHand());
        box.addView(handBtn, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        thumbEmpty.addView(box, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER));
    }

    private void applyOneHandLayout() {
        if (thumbContainer == null) return;
        thumbContainer.removeAllViews();
        thumbContainer.setOrientation(landscape ? LinearLayout.HORIZONTAL : LinearLayout.VERTICAL);
        LinearLayout.LayoutParams emptyLp;
        LinearLayout.LayoutParams keyboardLp;
        if (landscape) {
            emptyLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 0.38f);
            keyboardLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 0.62f);
        } else {
            emptyLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 0.38f);
            keyboardLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 0.62f);
        }
        if (rightHanded) {
            thumbContainer.addView(thumbEmpty, emptyLp);
            thumbContainer.addView(thumbKeyboard, keyboardLp);
        } else {
            thumbContainer.addView(thumbKeyboard, keyboardLp);
            thumbContainer.addView(thumbEmpty, emptyLp);
        }
    }

    private void buildOverlay() {
        if (contentFrame == null) return;
        overlayLayer = new FrameLayout(this);
        overlayLayer.setVisibility(View.GONE);

        View scrim = new View(this);
        scrim.setBackgroundColor(0xB3000000);
        scrim.setOnClickListener(v -> setOverlay(OVERLAY_NONE));
        overlayLayer.addView(scrim, matchParent());

        overlayPanel = new LinearLayout(this);
        overlayPanel.setOrientation(LinearLayout.VERTICAL);
        overlayPanel.setBackground(Ui.rounded(this, Ui.SURFACE, 20));
        int pad = dp(12);
        overlayPanel.setPadding(pad, dp(8), pad, pad);

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        overlayTitle = plainText("", 15, Ui.TEXT);
        overlayTitle.setGravity(Gravity.START);
        header.addView(overlayTitle, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextView close = plainText("收起", 14, Ui.ACCENT);
        close.setPadding(dp(12), dp(6), dp(12), dp(6));
        close.setOnClickListener(v -> setOverlay(OVERLAY_NONE));
        header.addView(close, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        overlayPanel.addView(header, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        numpadView = new NumpadView(this, hid);
        overlayPanel.addView(numpadView, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        overlayKeyboard = new KeyboardView(this, hid, true);
        overlayPanel.addView(overlayKeyboard, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        overlayTouchpad = new ControlPadView(this, hid, true);
        overlayPanel.addView(overlayTouchpad, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        DisplayMetrics dm = getResources().getDisplayMetrics();
        FrameLayout.LayoutParams lp;
        if (landscape) {
            int panelW = (int) (dm.widthPixels * 0.66f);
            int panelH = (int) (dm.heightPixels * 0.86f);
            lp = new FrameLayout.LayoutParams(panelW, panelH);
            lp.gravity = Gravity.CENTER;
        } else {
            // Portrait: dock the sheet to the bottom like a keyboard, otherwise the tall
            // stretched panel turns every key into a narrow strip.
            int panelH = (int) (dm.heightPixels * 0.45f);
            lp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, panelH);
            lp.gravity = Gravity.BOTTOM;
            lp.leftMargin = dp(6);
            lp.rightMargin = dp(6);
            overlayPanel.setBackground(Ui.roundedTop(this, Ui.SURFACE, 22));
        }
        overlayLayer.addView(overlayPanel, lp);
        contentFrame.addView(overlayLayer, matchParent());
    }

    private void toggleOverlay(int mode) {
        if (overlay == mode) setOverlay(OVERLAY_NONE);
        else setOverlay(mode);
    }

    private void setOverlay(int mode) {
        overlay = mode;
        if (overlayLayer != null) {
            overlayLayer.setVisibility(mode == OVERLAY_NONE ? View.GONE : View.VISIBLE);
        }
        if (numpadView != null) {
            numpadView.setVisibility(mode == OVERLAY_NUMPAD ? View.VISIBLE : View.GONE);
        }
        if (overlayKeyboard != null) {
            overlayKeyboard.setVisibility(mode == OVERLAY_KEYBOARD ? View.VISIBLE : View.GONE);
        }
        if (overlayTouchpad != null) {
            overlayTouchpad.setVisibility(mode == OVERLAY_TOUCHPAD ? View.VISIBLE : View.GONE);
        }
        if (overlayTitle != null) {
            overlayTitle.setText(mode == OVERLAY_NUMPAD ? "数字键盘"
                    : (mode == OVERLAY_TOUCHPAD ? "触控板" : "键盘"));
        }
        if (touchpadView != null) touchpadView.setOverlay(mode);
        if (twoHandView != null) twoHandView.setOverlay(mode);
    }

    // ------------------------------------------------------------- navigation

    private void selectTab(int index) {
        if (index < 0 || index >= TAB_NAMES.length) return;
        currentTab = index;
        oneHandMode = false;
        thumbMode = false;
        prefs.edit().putInt("last_tab", currentTab).putBoolean("one_hand", false).apply();
        setOverlay(OVERLAY_NONE);
        applyModeVisibility();
        refreshChrome();
    }

    private void toggleOneHand() {
        oneHandMode = !oneHandMode;
        thumbMode = false;
        prefs.edit().putBoolean("one_hand", oneHandMode).apply();
        applyModeVisibility();
        refreshChrome();
    }

    private void toggleHand() {
        rightHanded = !rightHanded;
        prefs.edit().putBoolean("right_handed", rightHanded).apply();
        if (deckView != null) deckView.setRightHanded(rightHanded);
        applyOneHandLayout();
        applyModeVisibility();
        refreshChrome();
    }

    /** 双手 page: swap the touchpad and the button board (left/right in landscape, top/bottom in portrait). */
    private void toggleTwoHandSwap() {
        twoHandSwapped = !twoHandSwapped;
        prefs.edit().putBoolean("twohand_swapped", twoHandSwapped).apply();
        if (twoHandView != null) twoHandView.setSwapped(twoHandSwapped);
        refreshSwapPill();
    }

    private void refreshSwapPill() {
        if (swapPill == null) return;
        boolean show = !oneHandMode && currentTab == TAB_TWOHAND;
        swapPill.setVisibility(show ? View.VISIBLE : View.GONE);
        swapPill.setTextColor(twoHandSwapped ? Ui.ACCENT : Ui.TEXT);
        swapPill.setBackground(twoHandSwapped
                ? Ui.roundedStroke(this, Ui.SURFACE_2, Ui.ACCENT, 1.5f, 12)
                : Ui.rounded(this, Ui.SURFACE_2, 12));
    }

    private void switchToThumbKeyboard() {
        thumbMode = true;
        applyModeVisibility();
        refreshChrome();
    }

    private void switchToDeck() {
        thumbMode = false;
        applyModeVisibility();
        refreshChrome();
    }

    private void applyModeVisibility() {
        for (int i = 0; i < pages.length; i++) {
            View page = pages[i];
            if (page == null) continue;
            page.setVisibility(!oneHandMode && i == currentTab ? View.VISIBLE : View.GONE);
        }
        if (oneHandContainer != null) {
            oneHandContainer.setVisibility(oneHandMode ? View.VISIBLE : View.GONE);
        }
        if (deckView != null) {
            deckView.setVisibility(oneHandMode && !thumbMode ? View.VISIBLE : View.GONE);
        }
        if (thumbContainer != null) {
            thumbContainer.setVisibility(oneHandMode && thumbMode ? View.VISIBLE : View.GONE);
        }
    }

    private void refreshChrome() {
        for (int i = 0; i < tabs.length; i++) {
            TextView tab = tabs[i];
            if (tab == null) continue;
            boolean selected = !oneHandMode && i == currentTab;
            tab.setTextColor(selected ? Ui.TEXT : Ui.TEXT_DIM);
            tab.setBackground(selected ? Ui.rounded(this, Ui.SURFACE_2, 12) : null);
            tab.setVisibility(oneHandMode ? View.GONE : View.VISIBLE);
        }
        if (oneHandPill != null) {
            oneHandPill.setTextColor(oneHandMode ? Ui.TEXT : Ui.TEXT_DIM);
            oneHandPill.setBackground(oneHandMode ? Ui.rounded(this, Ui.SURFACE_2, 12) : null);
        }
        if (handPill != null) {
            handPill.setText(rightHanded ? "右手" : "左手");
            handPill.setVisibility(oneHandMode ? View.VISIBLE : View.GONE);
        }
        if (pairButton != null) {
            pairButton.setText(hid.isConnected() ? "帮助" : "配对");
            if (!landscape) pairButton.setVisibility(View.VISIBLE);
        }
        refreshSwapPill();
    }

    private void applyStatus() {
        if (hid == null) return;
        int state = hid.getState();
        String title = hid.getStatusTitle();
        String detail = hid.getStatusDetail();
        int color;
        if (state == HidManager.STATE_CONNECTED) color = Ui.OK;
        else if (state == HidManager.STATE_ERROR) color = Ui.ERR;
        else if (state == HidManager.STATE_WAITING || state == HidManager.STATE_REGISTERING) color = Ui.WARN;
        else color = Ui.TEXT_DIM;
        if (statusDot != null) statusDot.setTextColor(color);
        if (statusTitle != null) {
            String text = title == null ? "" : title;
            if (landscape && detail != null && !detail.isEmpty()) text = text + " · " + detail;
            statusTitle.setText(text);
        }
        if (statusDetail != null) {
            boolean show = detail != null && !detail.isEmpty();
            statusDetail.setText(show ? detail : "");
            statusDetail.setVisibility(show ? View.VISIBLE : View.GONE);
        }
        refreshChrome();
    }

    // ------------------------------------------------------------- bluetooth

    private void ensurePermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{Manifest.permission.BLUETOOTH_CONNECT}, REQ_PERM);
                return;
            }
        }
        startBluetooth();
    }

    private void startBluetooth() {
        BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
        if (adapter == null) {
            toast("此设备不支持蓝牙");
            return;
        }
        if (!adapter.isEnabled()) {
            try {
                startActivityForResult(new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE), REQ_ENABLE);
            } catch (Exception e) {
                toast("请先在系统设置中打开蓝牙");
            }
            return;
        }
        hid.start();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQ_PERM) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startBluetooth();
            } else {
                toast("需要蓝牙权限才能模拟键盘");
            }
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQ_ENABLE) {
            if (resultCode == RESULT_OK) startBluetooth();
            else toast("蓝牙未打开");
        } else if (requestCode == REQ_DISCOVERABLE) {
            if (resultCode > 0) showPairingDialog();
            else toast("手机未开启可发现，无法配对");
        }
    }

    private void onPairClicked() {
        if (hid.isConnected()) {
            showHelpDialog();
            return;
        }
        try {
            Intent discoverable = new Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE);
            discoverable.putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, 300);
            startActivityForResult(discoverable, REQ_DISCOVERABLE);
        } catch (Exception e) {
            toast("无法开启蓝牙可发现模式");
        }
    }

    // ---------------------------------------------------------------- dialogs

    private void showPairingDialog() {
        String name = "本机";
        try {
            BluetoothAdapter adapter = hid.getAdapter();
            if (adapter != null && adapter.getName() != null) name = adapter.getName();
        } catch (Exception ignored) {
        }
        new AlertDialog.Builder(this)
                .setTitle("在平板上配对手机")
                .setMessage("1. 手机现已可被蓝牙搜索 5 分钟（设备名：" + name + "）\n"
                        + "2. 打开平板 设置 → 蓝牙，搜索并点击这个名字\n"
                        + "3. 两端确认配对码后，手机顶部会显示「已连接」\n\n"
                        + "如果平板里还留着旧的配对记录，先点「忘记/取消配对」，再重新配一次。\n"
                        + "若状态显示注册失败：请先在手机设置中强制停止其他蓝牙键盘应用，再点状态文字重试。")
                .setPositiveButton("知道了", null)
                .show();
    }

    private void showHelpDialog() {
        new AlertDialog.Builder(this)
                .setTitle("使用说明")
                .setMessage("键盘：字母、数字和符号直接发到平板。键盘顶部有一行大工具栏：左边「切换布局」在手机布局和电脑布局之间来回切（会记住上次的选择），右边「触控板」键弹出触控板浮层（大触控区 + 右侧 ↑↓ 滚动 + 底部 返回/主页/后台），不用切页面就能移动指针和点击。\n\n"
                        + "手机布局：和普通手机键盘一样（数字行 1-0、qwerty、Shift / ⌫、Space / Enter），左下「符号」进入符号页，符号页左下「ABC」返回字母；数字行也可以用来选中文输入法的候选词。\n\n"
                        + "电脑布局：顶部一排快捷键 Ctrl+C / Ctrl+V / Ctrl+X / Ctrl+A / Alt+Tab，下面带 Tab、方向键和 Ctrl / Alt / Win，适合敲命令、远程桌面和组合键。\n\n"
                        + "触控板：单指滑动移动指针，单指点击=左键，双指点击=右键，双指上下滑=滚动，右侧 ↑↓ 按住连续滚动；左侧「数字」弹出数字键盘（输入密码），「键盘」弹出简易手机键盘（带数字行，数字也可用来选输入法的候选词），「菜单」等于平板菜单键。\n\n"
                        + "媒体：上一首 / 播放暂停 / 下一首、音量加减、静音、CC、返回 / 主页 / 菜单 / 电源。\n\n"
                        + "手柄：浮在屏幕边缘的手机式布局——左下方向盘（或圆盘），右下 X/Y/A/B，两侧中段 L1/R1，中间下方 SELECT/START。左上的「移动盘」按钮打开设置：移动盘「按键 / 圆盘」两种形态、「移动盘大小」和「按键大小」两个 60%–150% 的滑块（只缩放按键本身），以及「手柄页触控板」和「触控点击」。旁边的「触控」小键一键切换 关 / 中间 / 全屏：中间＝页面正中一块看得见边界的触控板；全屏＝除按键外整片空白都能触控（两侧留白不响应，防掌根误触）。触控板手势和「触控板」页一致：单指移动、轻点＝左键、双指点击＝右键、双指上下滑＝滚动；「只移动」档位下只移指针不点击。\n\n"
                        + "手柄页触控板浮层：右上角的「触控板」小键点一下，右侧浮出一块触控板浮层（单指移动、轻点＝左键、双指点击＝右键、双指滑＝滚动），右下角竖排是 返回 / 主页 / 后台。点浮层外面的任何地方就会收起，左半屏的方向盘和 ABXY 照样能按；再点一次「触控板」收起。浮层左上角有个「⇔」把手：按住约 0.3 秒后左右拖动可以挪动整块浮层的位置，松手就记住，下次打开还在那儿。\n\n"
                        + "双手模式：左边是触控板，右边是按键板（从上到下：音量、媒体、返回 / 主页 / 后台、数字 / 键盘），左右手各管一半。双手页顶部有「互换」键：点一下把触控区和按键区对调（横屏左右对调，竖屏上下对调），方便左右手习惯不同的人；互换状态会记住。\n\n"
                        + "单手模式：右侧（或左侧）面板集中了大触控区和常用键；点「键盘」切成拇指键盘，点「切到遥控台」切回。\n\n"
                        + "竖屏：键盘页上半部分是触控区、下半部分是贴底的键盘（按键大小和普通手机键盘接近）；中间的分隔条可以上下拖动调整两区比例（拖到 48% / 58% / 62% 附近会吸附，双击分隔条恢复默认 58/42）。触控板页和媒体页的功能区也贴着屏幕底部，顶部分隔条可以拖动调整功能区高度（上方留空方便拇指操作，双击分隔条恢复默认：触控板页 80% / 媒体页 78%）；媒体页顶部还有「触控板」键，可以直接弹出触控板浮层；媒体页还多了「亮度 −/＋」两个键（调屏幕亮度，横屏时它们会移到媒体条左右两侧）。亮度键会先发蓝牙 HID；像 vivo 这类不响应亮度键的平板，请打开顶栏「伴侣」连上 WiFi，并在平板伴侣里授权「修改系统设置」，之后亮度键改走 WiFi 直接改平板系统亮度。数字键盘 / 键盘 / 触控板的浮层都从屏幕底部弹出。\n\n"
                        + "横屏会自动切换紧凑工具栏和全屏显示。顶部最右侧的「旋转」键可以随时手动切换横竖屏（切过去后会一直保持，直到再点一次；长按「旋转」恢复跟随系统自动旋转）；同一栏最左边的电源键要按住 0.6 秒才发送，避免误触。\n\n"
                        + "界面大小：「自动」会按屏幕宽度和系统字体大小自动适配，换手机或平板不用重新设置；如果按钮文字被截断或界面太挤，可以点下面的「界面大小」选「小」或「标准」。顶部一栏放不下时，右侧会出现「更多」按钮，单手 / 右手 / 互换 / 旋转 / 伴侣 / 帮助会按屏幕宽度自动收进菜单。\n\n"
                        + "中文输入：蓝牙键盘只能发送字母和按键，中文需要平板自己的输入法；需要直接打中文时请等后续的 WiFi 配套应用。\n\n"
                        + "配对：点右上角「配对」让手机可被发现，然后在平板上搜索并配对。\n\n"
                        + "如果平板上所有按键都突然没反应（App 更新后常见）：平板会缓存旧的蓝牙 HID 描述。请到平板 设置 → 蓝牙，找到本机设备 → 点右侧详情 →「取消配对 / 忘记设备」，然后回到 App 点「配对」重新配一次，之后所有功能恢复。")
                .setPositiveButton("知道了", null)
                .setNeutralButton("界面大小", (dialog, which) -> showUiSizeDialog())
                .show();
    }

    /** Auto / small / standard / large UI scale; changing it recreates the activity. */
    private void showUiSizeDialog() {
        String[] items = {"自动适配（推荐）", "小（界面更紧凑）", "标准", "大（界面更宽松）"};
        int current = Math.max(0, Math.min(items.length - 1, Ui.readManualMode(this)));
        new AlertDialog.Builder(this)
                .setTitle("界面大小")
                .setSingleChoiceItems(items, current, (dialog, which) -> {
                    Ui.writeManualMode(this, which);
                    dialog.dismiss();
                    recreate();
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void maybeShowFirstRunHelp() {
        if (prefs.getBoolean("help_shown", false)) return;
        prefs.edit().putBoolean("help_shown", true).apply();
        if (statusTitle != null) statusTitle.postDelayed(this::showHelpDialog, 800);
    }

    private void toast(String message) {
        android.widget.Toast.makeText(this, message, android.widget.Toast.LENGTH_SHORT).show();
    }

    // --------------------------------------------------------- orientation

    /** Top-bar 「旋转」 button: force the opposite orientation without touching system settings. */
    private void toggleOrientation() {
        boolean portraitNow = getResources().getConfiguration().orientation
                == Configuration.ORIENTATION_PORTRAIT;
        setRequestedOrientation(portraitNow
                ? android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                : android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        toast(portraitNow ? "已切到横屏（长按「旋转」恢复跟随系统）" : "已切到竖屏（长按「旋转」恢复跟随系统）");
    }

    /** Long-press 「旋转」: hand orientation control back to the system auto-rotate setting. */
    private void resetOrientationToSystem() {
        setRequestedOrientation(android.content.pm.ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED);
        toast("已恢复跟随系统旋转");
    }

    // -------------------------------------------------------- intent extras

    private void applyIntentExtras(Intent intent) {
        if (intent == null) return;
        // Debug/test hook: adb cannot change the system rotation without WRITE_SETTINGS,
        // so the app can force its own orientation for automated screenshots.
        if (intent.hasExtra("orient")) {
            int orient = intent.getIntExtra("orient", 0);
            if (orient == 1) {
                setRequestedOrientation(android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
            } else if (orient == 2) {
                setRequestedOrientation(android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
            } else {
                setRequestedOrientation(android.content.pm.ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED);
            }
        }
        // Debug/test hook: same code path as the 双手 page 「互换」 button.
        if (intent.hasExtra("twoswap")) {
            twoHandSwapped = intent.getIntExtra("twoswap", 1) != 0;
            prefs.edit().putBoolean("twohand_swapped", twoHandSwapped).apply();
            if (twoHandView != null) twoHandView.setSwapped(twoHandSwapped);
            refreshSwapPill();
        }
        // Debug/test hook: same code path as the top-bar 「旋转」 button.
        if (intent.hasExtra("rotate")) {
            contentFrame.post(this::toggleOrientation);
        }
        // Debug/test hook: pick the keyboard layout / page without tapping (adb cannot tap).
        if (intent.hasExtra("kblayout") && keyboardView != null) {
            keyboardView.setLayout(intent.getIntExtra("kblayout", KeyboardView.LAYOUT_PHONE));
        }
        if (intent.hasExtra("kbpage") && keyboardView != null) {
            keyboardView.setPage(intent.getIntExtra("kbpage", KeyboardView.PAGE_LETTERS));
        }
        // Debug/test hook: set the portrait touch-area / keyboard split without dragging.
        if (intent.hasExtra("kbsplit")) {
            applyPortraitSplit(intent.getIntExtra("kbsplit", 58) / 100f);
            prefs.edit().putFloat("kb_split", kbSplit).apply();
        }
        // Debug/test hook: set the docked touchpad / media panel heights without dragging.
        if (touchDock != null && intent.hasExtra("touchsplit")) {
            applyDockShare(touchDock, intent.getIntExtra("touchsplit", 80) / 100f);
            prefs.edit().putFloat(touchDock.key, touchDock.share).apply();
        }
        if (mediaDock != null && intent.hasExtra("mediasplit")) {
            applyDockShare(mediaDock, intent.getIntExtra("mediasplit", 78) / 100f);
            prefs.edit().putFloat(mediaDock.key, mediaDock.share).apply();
        }
        boolean changed = false;
        if (intent.hasExtra("tab")) {
            int tab = intent.getIntExtra("tab", TAB_KEYBOARD);
            if (tab >= 0 && tab < TAB_NAMES.length) {
                currentTab = tab;
                oneHandMode = false;
                thumbMode = false;
                changed = true;
            }
        }
        if (intent.hasExtra("onehand")) {
            oneHandMode = intent.getIntExtra("onehand", 1) != 0;
            changed = true;
        }
        if (intent.hasExtra("thumb")) {
            thumbMode = intent.getIntExtra("thumb", 1) != 0;
            changed = true;
        }
        if (intent.hasExtra("hand")) {
            rightHanded = intent.getIntExtra("hand", 1) != 0;
            if (deckView != null) deckView.setRightHanded(rightHanded);
            applyOneHandLayout();
            changed = true;
        }
        applyModeVisibility();
        applyStatus();
        if (intent.hasExtra("overlay")) {
            setOverlay(intent.getIntExtra("overlay", OVERLAY_NONE));
        } else if (changed) {
            setOverlay(OVERLAY_NONE);
        }
        if (intent.hasExtra("pair_dialog")) showPairingDialog();
        if (intent.hasExtra("help")) showHelpDialog();
        if (intent.getIntExtra("uisizedlg", 0) == 1) contentFrame.post(this::showUiSizeDialog);
        // Debug/test hook: set the pairing token and/or open the WiFi companion page.
        if (intent.hasExtra("ctoken")) {
            String token = intent.getStringExtra("ctoken");
            if (token != null && !token.isEmpty()) {
                CompanionBridge.get(this).setToken(token);
                CompanionBridge.get(this).start();
            }
        }
        if (intent.hasExtra("companion")) contentFrame.post(this::openCompanion);
        // Debug/test hook: open the adaptive-toolbar overflow menu so it can be screenshotted.
        if (intent.getIntExtra("moremenu", 0) == 1) {
            // The decor view survives recreation, so the callback survives an orientation change.
            getWindow().getDecorView().postDelayed(() -> {
                if (chromeBar != null) chromeBar.showMoreMenuForDebug();
            }, 700);
        }
        // Debug/test hook: movement pad type / size, and the settings dialog screenshot.
        if (gamepadView != null && intent.hasExtra("gptype")) {
            gamepadView.setPadType(intent.getIntExtra("gptype", 0));
        }
        if (gamepadView != null && intent.hasExtra("gppad")) {
            gamepadView.setPadScale(intent.getIntExtra("gppad", 100) / 100f);
        }
        if (gamepadView != null && intent.hasExtra("gpbtn")) {
            gamepadView.setBtnScale(intent.getIntExtra("gpbtn", 100) / 100f);
        }
        if (gamepadView != null && intent.hasExtra("gpsize")) {
            float both = intent.getIntExtra("gpsize", 100) / 100f;
            gamepadView.setPadScale(both);
            gamepadView.setBtnScale(both);
        }
        if (gamepadView != null && intent.hasExtra("gpcompat")) {
            gamepadView.setHatCompat(intent.getIntExtra("gpcompat", 0) == 1);
        }
        if (gamepadView != null && intent.hasExtra("gptouch")) {
            gamepadView.setTouchMode(intent.getIntExtra("gptouch", 1));
        }
        if (gamepadView != null && intent.getIntExtra("gpbuzz", 0) == 1) {
            contentFrame.postDelayed(gamepadView::selfTestHaptics, 900);
        }
        if (gamepadView != null && intent.hasExtra("gptouchx")) {
            gamepadView.setTouchMoveOnly(intent.getIntExtra("gptouchx", 0) == 1);
        }
        if (gamepadView != null && intent.getIntExtra("gpdialog", 0) == 1) {
            contentFrame.postDelayed(gamepadView::showSettingsDialog, 700);
        }
        // Debug/test hook: the floating touch pad overlay (adb cannot tap it) and its offset.
        if (gamepadView != null && intent.hasExtra("gptp")) {
            gamepadView.setTouchpadOpen(intent.getIntExtra("gptp", 0) == 1);
        }
        if (gamepadView != null && intent.hasExtra("gptpoff")) {
            gamepadView.setTouchpadOffsetDp(intent.getIntExtra("gptpoff", 0));
        }
        // Debug/test hook: emit a fixed gamepad sequence so the host input events can be captured
        // with "adb shell getevent" (adb cannot tap the on-screen gamepad).
        int gpTest = intent.getIntExtra("gptest", 0);
        if (gpTest > 0) {
            new Thread(() -> runGamepadSelfTest(gpTest), "gamepad-self-test").start();
        }
        // Debug/test hook: fire the brightness consumer usages so the host can capture them
        // with "adb shell getevent" (adb cannot tap the on-screen brightness keys).
        if (intent.getIntExtra("brighttest", 0) == 1) {
            new Thread(() -> {
                if (hid == null) return;
                sleepQuietly(1200);
                for (int i = 0; i < 2; i++) {
                    hid.tapConsumer(HidConstants.CONSUMER_BRIGHTNESS_INCREMENT);
                    sleepQuietly(600);
                    hid.tapConsumer(HidConstants.CONSUMER_BRIGHTNESS_DECREMENT);
                    sleepQuietly(600);
                }
            }, "brightness-self-test").start();
        }
        // Debug/test hook: drive the wireless (WiFi companion) brightness path directly, because
        // adb cannot tap the on-screen brightness keys on the phone.
        final int wifiBright = intent.getIntExtra("brightwifi", 0);
        if (wifiBright != 0) {
            contentFrame.postDelayed(() -> {
                for (int i = 0; i < Math.abs(wifiBright); i++) {
                    CompanionBridge.get(this).sendBright(wifiBright > 0);
                }
            }, 1500);
        }
    }

    /** Debug only: presses every gamepad control once so a host-side getevent log can verify it. */
    private void runGamepadSelfTest(int mode) {
        HidManager manager = hid;
        if (manager == null) return;
        Thread self = Thread.currentThread();
        sleepQuietly(1800);
        int[] buttons = {HidConstants.GP_A, HidConstants.GP_B, HidConstants.GP_X, HidConstants.GP_Y,
                HidConstants.GP_L1, HidConstants.GP_R1, HidConstants.GP_SELECT, HidConstants.GP_START};
        int[] dirs = {HidConstants.GP_UP, HidConstants.GP_RIGHT, HidConstants.GP_DOWN, HidConstants.GP_LEFT};
        if (mode == 1 || mode == 3) {
            for (int b : buttons) {
                if (self.isInterrupted()) return;
                gamepadPulse(manager, b);
            }
        }
        if (mode == 2 || mode == 3) {
            for (int b : dirs) {
                if (self.isInterrupted()) return;
                gamepadPulse(manager, b);
            }
        }
        if (mode == 4) {
            gamepadPulse(manager, HidConstants.GP_UP | HidConstants.GP_RIGHT);
            gamepadPulse(manager, HidConstants.GP_DOWN | HidConstants.GP_LEFT);
        }
        if (mode == 5) {
            for (int hat = 0; hat <= 7; hat++) {
                if (self.isInterrupted()) return;
                manager.sendGamepadHat(hat);
                sleepQuietly(400);
                manager.sendGamepadHat(0x0F);
                sleepQuietly(250);
            }
        }
        manager.sendGamepad(0);
    }

    private static void gamepadPulse(HidManager manager, int mask) {
        manager.sendGamepad(mask);
        sleepQuietly(320);
        manager.sendGamepad(0);
        sleepQuietly(240);
    }

    private static void sleepQuietly(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    // ------------------------------------------------------------- immersive

    private void applyImmersive() {
        View decor = getWindow().getDecorView();
        if (decor.getWindowToken() == null) return;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            WindowInsetsController controller = getWindow().getInsetsController();
            if (controller == null) return;
            if (landscape) {
                controller.hide(WindowInsets.Type.statusBars() | WindowInsets.Type.navigationBars());
                controller.setSystemBarsBehavior(
                        WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
            } else {
                controller.show(WindowInsets.Type.statusBars() | WindowInsets.Type.navigationBars());
            }
        } else {
            if (landscape) {
                decor.setSystemUiVisibility(View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                        | View.SYSTEM_UI_FLAG_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
            } else {
                decor.setSystemUiVisibility(View.SYSTEM_UI_FLAG_VISIBLE);
            }
        }
    }

    // --------------------------------------------------------------- service

    private void openCompanion() {
        try {
            // Forward our launch extras so debug/test hooks (e.g. pairjson) reach the
            // companion page; CompanionActivity is not exported, so adb cannot start it.
            Intent intent = new Intent(this, CompanionActivity.class);
            if (getIntent() != null) intent.putExtras(getIntent());
            startActivity(intent);
        } catch (Exception e) {
            toast("无法打开伴侣模式");
        }
    }

    private void startKeepAlive() {
        try {
            Intent service = new Intent(this, KeepAliveService.class);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(service);
            else startService(service);
        } catch (Exception e) {
            Log.w(TAG, "keepalive start failed", e);
        }
    }
}
