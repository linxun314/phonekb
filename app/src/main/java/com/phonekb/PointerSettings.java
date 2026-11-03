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

    public static final int LONG_PRESS_MIN = 250;
    public static final int LONG_PRESS_MAX = 600;
    public static final int LONG_PRESS_DEFAULT = 350;

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
    }

    public static float speed() {
        return speed;
    }

    public static int rows() {
        return rows;
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