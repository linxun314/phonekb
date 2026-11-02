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

    private static final String PREFS = "phonekb";
    private static final String KEY_SPEED = "ptr_speed";
    private static final String KEY_ROWS = "ptr_rows";
    private static final String KEY_ACCEL = "ptr_accel";

    private static float speed = SPEED_DEFAULT;
    private static int rows = ROWS_DEFAULT;
    private static boolean accel = true;
    private static boolean loaded;

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