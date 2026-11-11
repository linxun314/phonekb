package com.phonekb;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * Shared pointer settings for every trackpad surface: pointer speed (mouse sensitivity),
 * acceleration and the wheel speed. Values are cached in static fields so the touch loops can
 * read them without touching SharedPreferences on every motion event.
 */
public final class PointerSettings {
    /** Historical finger-to-cursor factor: 1.0x keeps exactly the old feel. */
    public static final float BASE_SPEED = 1.7f;
    public static final float SPEED_MIN = 0.4f;
    public static final float SPEED_MAX = 2.5f;
    public static final float SPEED_DEFAULT = 1.0f;
    public static final int ROWS_MIN = 1;
    public static final int ROWS_MAX = 6;
    public static final int ROWS_DEFAULT = 3;

    /** Hold cadence (ms per wheel notch) of the trackpad-side up / down scroll keys. */
    public static final int SCROLL_HOLD_SLOW = 180;
    public static final int SCROLL_HOLD_MID = 120;
    public static final int SCROLL_HOLD_FAST = 70;
    public static final int SCROLL_HOLD_MIN = 50;
    public static final int SCROLL_HOLD_MAX = 300;
    public static final int SCROLL_HOLD_DEFAULT = SCROLL_HOLD_SLOW;

    /**
     * 「滚轮条」：手指按在右侧箭头条上上下拖动，每拖这么远（dp）发一格滚轮。
     * 数值越大越慢、越细腻；越小越"灵敏"。
     */
    public static final int STRIP_STEP_SLOW = 16;
    public static final int STRIP_STEP_MID = 10;
    public static final int STRIP_STEP_FAST = 6;
    public static final int STRIP_STEP_MIN = 4;
    public static final int STRIP_STEP_MAX = 40;
    public static final int STRIP_STEP_DEFAULT = STRIP_STEP_MID;

    /** 滚轮条发包节拍：一格一格均匀发出去，比「一帧攒几格一次发」顺滑得多。 */
    public static final int STRIP_SMOOTH_STD = 10;
    public static final int STRIP_SMOOTH_FAST = 8;
    public static final int STRIP_SMOOTH_SLOWEST = 14;
    public static final int STRIP_SMOOTH_MIN = 6;
    public static final int STRIP_SMOOTH_MAX = 20;
    public static final int STRIP_SMOOTH_DEFAULT = STRIP_SMOOTH_STD;

    /** 拖动时每滚动几格震一下；关掉后完全静音（有人觉得震动在加重「卡」的错觉）。 */
    public static final int STRIP_HAPTIC_EVERY = 8;

    public static final int LONG_PRESS_MIN = 200;
    public static final int LONG_PRESS_MAX = 600;
    public static final int LONG_PRESS_DEFAULT = 300;

    private static final String PREFS = "phonekb";
    private static final String KEY_SPEED = "ptr_speed";
    private static final String KEY_ROWS = "ptr_rows";
    private static final String KEY_ACCEL = "ptr_accel";
    private static final String KEY_CLICK_DOWN = "ptr_click_down";
    private static final String KEY_LONG_PRESS = "ptr_long_press";
    private static final String KEY_FAST = "ptr_fast";
    private static final String KEY_MEDIA_HOLD = "media_hold_mode";
    private static final String KEY_MEDIA_BOOST = "media_boost";
    private static final String KEY_LT_COMPAT = "gp_lt_compat";
    private static final String KEY_BOOST_POINT = "media_boost_point";
    private static final String KEY_REPORT_MS = "ptr_report_ms";
    private static final String KEY_SCROLL_HOLD = "ptr_scroll_hold";
    private static final String KEY_STRIP_STEP = "ptr_strip_step";
    private static final String KEY_STRIP_FLING = "ptr_strip_fling";
    private static final String KEY_STRIP_SMOOTH = "ptr_strip_smooth";
    private static final String KEY_STRIP_HAPTIC = "ptr_strip_haptic";

    private static float speed = SPEED_DEFAULT;
    private static int rows = ROWS_DEFAULT;
    private static boolean accel = true;
    private static boolean clickDown = false;
    private static int longPressMs = LONG_PRESS_DEFAULT;
    private static boolean fast = true;
    private static int mediaHoldMode = PointerSettings.MEDIA_HOLD_SEEK;
    private static boolean mediaBoost = true;
    private static boolean ltCompat = false;
    private static int boostPoint = 1;
    private static boolean loaded;
    /** Mouse report cadence in ms (see HidManager#mouseGapMs). */
    public static final int REPORT_MIN = 2;
    public static final int REPORT_MAX = 12;
    public static final int REPORT_DEFAULT = 9;
    private static int reportMs = REPORT_DEFAULT;
    private static int scrollHoldMs = SCROLL_HOLD_DEFAULT;
    private static int stripStepDp = STRIP_STEP_DEFAULT;
    private static int stripSmoothMs = STRIP_SMOOTH_DEFAULT;
    private static boolean stripHaptic = true;
    private static boolean stripFling = false;

    /** Rewind / fast-forward keys hold down the real consumer key (players keep seeking). */
    public static final int MEDIA_HOLD_SEEK = 0;
    /** Rewind / fast-forward keys repeat previous / next track (players that seek in steps). */
    public static final int MEDIA_HOLD_TRACK = 1;

    /** Park spots (host pixels) for the 2x key: it presses the screen at a fixed, safe place. */
    private static final int[][] BOOST_POINTS = {{420, 460}, {420, 820}, {430, 1180}};

    private PointerSettings() {
    }

    /** Loads the saved values once per process. Safe to call from any thread / repeatedly. */
    public static void init(Context context) {
        if (loaded || context == null) return;
        loaded = true;
        SharedPreferences p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        speed = clampSpeed(p.getFloat(KEY_SPEED, SPEED_DEFAULT));
        rows = clampRows(p.getInt(KEY_ROWS, ROWS_DEFAULT));
        accel = p.getBoolean(KEY_ACCEL, true);
        clickDown = p.getBoolean(KEY_CLICK_DOWN, false);
        longPressMs = clampLongPress(p.getInt(KEY_LONG_PRESS, LONG_PRESS_DEFAULT));
        fast = p.getBoolean(KEY_FAST, true);
        mediaHoldMode = p.getInt(KEY_MEDIA_HOLD, MEDIA_HOLD_SEEK) == MEDIA_HOLD_TRACK
                ? MEDIA_HOLD_TRACK : MEDIA_HOLD_SEEK;
        mediaBoost = p.getBoolean(KEY_MEDIA_BOOST, true);
        ltCompat = p.getBoolean(KEY_LT_COMPAT, false);
        boostPoint = clampPoint(p.getInt(KEY_BOOST_POINT, 1));
        reportMs = clampReport(p.getInt(KEY_REPORT_MS, REPORT_DEFAULT));
        scrollHoldMs = clampScrollHold(p.getInt(KEY_SCROLL_HOLD, SCROLL_HOLD_DEFAULT));
        stripStepDp = clampStripStep(p.getInt(KEY_STRIP_STEP, STRIP_STEP_DEFAULT));
        stripFling = p.getBoolean(KEY_STRIP_FLING, false);
        stripSmoothMs = clampStripSmooth(p.getInt(KEY_STRIP_SMOOTH, STRIP_SMOOTH_DEFAULT));
        stripHaptic = p.getBoolean(KEY_STRIP_HAPTIC, true);
    }

    /** How often (ms) the up / down scroll keys repeat while held down. */
    public static int scrollHoldMs() {
        return scrollHoldMs;
    }

    /** Finger travel (dp) per wheel notch on the scroll strip. */
    public static int stripStepDp() {
        return stripStepDp;
    }

    public static void setStripStepDp(Context context, int value) {
        init(context);
        stripStepDp = clampStripStep(value);
        prefs(context).edit().putInt(KEY_STRIP_STEP, stripStepDp).apply();
    }

    /** True = a fast flick on the strip keeps coasting for a couple of notches. */
    public static boolean stripFling() {
        return stripFling;
    }

    public static void setStripFling(Context context, boolean value) {
        init(context);
        stripFling = value;
        prefs(context).edit().putBoolean(KEY_STRIP_FLING, value).apply();
    }

    /** 每发一格滚轮的间隔（毫秒）：标准 10 / 更顺 8。 */
    public static int stripSmoothMs() {
        return stripSmoothMs;
    }

    public static void setStripSmoothMs(Context context, int value) {
        init(context);
        stripSmoothMs = clampStripSmooth(value);
        prefs(context).edit().putInt(KEY_STRIP_SMOOTH, stripSmoothMs).apply();
    }

    public static int clampStripSmooth(int value) {
        if (value < STRIP_SMOOTH_MIN) return STRIP_SMOOTH_MIN;
        if (value > STRIP_SMOOTH_MAX) return STRIP_SMOOTH_MAX;
        return value;
    }

    /** 拖动滚轮条时要不要震动反馈（默认开）。 */
    public static boolean stripHaptic() {
        return stripHaptic;
    }

    public static void setStripHaptic(Context context, boolean value) {
        init(context);
        stripHaptic = value;
        prefs(context).edit().putBoolean(KEY_STRIP_HAPTIC, value).apply();
    }

    public static int clampStripStep(int value) {
        if (value < STRIP_STEP_MIN) return STRIP_STEP_MIN;
        if (value > STRIP_STEP_MAX) return STRIP_STEP_MAX;
        return value;
    }

    public static void setScrollHoldMs(Context context, int value) {
        init(context);
        scrollHoldMs = clampScrollHold(value);
        prefs(context).edit().putInt(KEY_SCROLL_HOLD, scrollHoldMs).apply();
    }

    public static int clampScrollHold(int value) {
        if (value < SCROLL_HOLD_MIN) return SCROLL_HOLD_MIN;
        if (value > SCROLL_HOLD_MAX) return SCROLL_HOLD_MAX;
        return value;
    }

    public static float speed() {
        return speed;
    }

    public static int rows() {
        return rows;
    }

    /** Mouse report cadence in ms: smaller = snappier, larger = fewer reports on the link. */
    public static int reportMs() {
        return reportMs;
    }

    public static void setReportMs(Context context, int value) {
        reportMs = clampReport(value);
        prefs(context).edit().putInt(KEY_REPORT_MS, reportMs).apply();
    }

    private static int clampReport(int value) {
        if (value < REPORT_MIN) return REPORT_MIN;
        if (value > REPORT_MAX) return REPORT_MAX;
        return value;
    }

    public static boolean accel() {
        return accel;
    }

    /** True = send the click on finger-down instead of finger-up (feels a bit snappier). */
    public static boolean clickDown() {
        return clickDown;
    }

    /** How long the finger must rest before a drag starts (ms). */
    public static int longPressMs() {
        return longPressMs;
    }

    /** Fast mode: skip repaints / haptics while the finger is moving, to save UI-thread time. */
    public static boolean fast() {
        return fast;
    }

    /** What a long press on the media page rewind / fast-forward keys does. */
    public static int mediaHoldMode() {
        return mediaHoldMode;
    }

    /** True = the media bar shows the extra "2x" key used for Douyin-style long-press speed-up. */
    public static boolean mediaBoost() {
        return mediaBoost;
    }

    /** True = LT / RT also send L1 / R1, for games that only map the two shoulder buttons. */
    public static boolean ltCompat() {
        return ltCompat;
    }

    /** Where the 2x key parks the pointer before pressing (host pixels). */
    public static int boostX() {
        return BOOST_POINTS[clampPoint(boostPoint)][0];
    }

    public static int boostY() {
        return BOOST_POINTS[clampPoint(boostPoint)][1];
    }

    public static void setSpeed(Context context, float value) {
        init(context);
        speed = clampSpeed(value);
        prefs(context).edit().putFloat(KEY_SPEED, speed).apply();
    }

    public static void setRows(Context context, int value) {
        init(context);
        rows = clampRows(value);
        prefs(context).edit().putInt(KEY_ROWS, rows).apply();
    }

    public static void setAccel(Context context, boolean value) {
        init(context);
        accel = value;
        prefs(context).edit().putBoolean(KEY_ACCEL, value).apply();
    }

    public static void setClickDown(Context context, boolean value) {
        init(context);
        clickDown = value;
        prefs(context).edit().putBoolean(KEY_CLICK_DOWN, value).apply();
    }

    public static void setLongPressMs(Context context, int value) {
        init(context);
        longPressMs = clampLongPress(value);
        prefs(context).edit().putInt(KEY_LONG_PRESS, longPressMs).apply();
    }

    public static void setFast(Context context, boolean value) {
        init(context);
        fast = value;
        prefs(context).edit().putBoolean(KEY_FAST, value).apply();
    }

    public static void setMediaHoldMode(Context context, int value) {
        init(context);
        mediaHoldMode = value == MEDIA_HOLD_TRACK ? MEDIA_HOLD_TRACK : MEDIA_HOLD_SEEK;
        prefs(context).edit().putInt(KEY_MEDIA_HOLD, mediaHoldMode).apply();
    }

    public static void setMediaBoost(Context context, boolean value) {
        init(context);
        mediaBoost = value;
        prefs(context).edit().putBoolean(KEY_MEDIA_BOOST, value).apply();
    }

    public static void setLtCompat(Context context, boolean value) {
        init(context);
        ltCompat = value;
        prefs(context).edit().putBoolean(KEY_LT_COMPAT, value).apply();
    }

    public static void setBoostPoint(Context context, int value) {
        init(context);
        boostPoint = clampPoint(value);
        prefs(context).edit().putInt(KEY_BOOST_POINT, boostPoint).apply();
    }

    public static int boostPoint() {
        return clampPoint(boostPoint);
    }

    private static int clampPoint(int value) {
        if (value < 0) return 0;
        if (value > BOOST_POINTS.length - 1) return BOOST_POINTS.length - 1;
        return value;
    }

    public static int clampLongPress(int value) {
        if (value < LONG_PRESS_MIN) return LONG_PRESS_MIN;
        if (value > LONG_PRESS_MAX) return LONG_PRESS_MAX;
        return value;
    }

    /**
     * Cursor pixels per finger pixel for one movement sample. With acceleration on, a fast flick
     * gains up to ~1.9x while a slow, careful drag keeps the plain 1.0x speed.
     */
    public static float gain(Context context, float dx, float dy) {
        init(context);
        float g = BASE_SPEED * speed;
        if (!accel || context == null) return g;
        float dp = Ui.dp(context, 1f);
        if (dp <= 0f) return g;
        float mag = (float) Math.hypot(dx, dy);
        float t = Math.min(1f, mag / (dp * 14f));
        return g * (1f + 0.9f * t * t);
    }

    /** Distance (px) the two-finger scroll needs to travel per wheel notch. */
    public static float scrollStepPx(Context context, float baseDp) {
        init(context);
        float perNotchDp = baseDp * (float) ROWS_DEFAULT / Math.max(1, rows);
        return Ui.dp(context, perNotchDp);
    }

    public static float clampSpeed(float value) {
        if (value < SPEED_MIN) return SPEED_MIN;
        if (value > SPEED_MAX) return SPEED_MAX;
        return value;
    }

    public static int clampRows(int value) {
        if (value < ROWS_MIN) return ROWS_MIN;
        if (value > ROWS_MAX) return ROWS_MAX;
        return value;
    }

    /** "1.0x" / "1.4x" - one decimal is precise enough for the labels. */
    public static String speedLabel(Context context) {
        init(context);
        return String.format(java.util.Locale.US, "%.1fx", speed);
    }

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }
}