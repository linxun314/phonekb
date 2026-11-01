package com.phonekb;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.util.DisplayMetrics;
import android.util.Log;
import android.util.TypedValue;
import android.widget.TextView;

/**
 * Colors, paints and size helpers for the whole UI.
 *
 * <p>All sizes go through {@link #dp(Context, float)} / {@link #dpi(Context, float)} and
 * {@link #text(Context, float, int)} so the app can adapt to very different phones:
 *
 * <ul>
 *   <li>{@link #init(Context)} computes a UI scale from the screen's smallest-width dp. A
 *       393dp-wide phone (the reference device) keeps scale 1.0; a 360dp phone shrinks to
 *       ≈0.92; a 320dp phone to ≈0.81; big/tablet screens grow up to 1.15.</li>
 *   <li>The system font-size setting is respected but clamped to ≈1.15x so "huge fonts"
 *       cannot blow fixed-size keys apart. The clamp is combined with the UI scale.</li>
 *   <li>The user can override the automatic scale from 帮助 → 界面大小 (自动/小/标准/大),
 *       stored as the "ui_size" preference (multiplier 0.92 / 1.0 / 1.08 on top of auto).</li>
 *   <li>Debug builds can simulate another screen via {@link #setSimulation(int, float)}
 *       (adb extras {@code --ei simw} / {@code --ef simfont}), which is how the layout is
 *       verified on narrow-screen configurations without a second device.</li>
 * </ul>
 */
public final class Ui {
    private Ui() {}

    public static final int BG = 0xFF0F1115;
    public static final int SURFACE = 0xFF171B22;
    public static final int SURFACE_2 = 0xFF1F2530;
    public static final int KEY = 0xFF262D3A;
    public static final int KEY_PRESSED = 0xFF3B4968;
    public static final int ACCENT = 0xFF4C8DFF;
    public static final int TEXT = 0xFFE9ECF2;
    public static final int TEXT_DIM = 0xFF98A1B3;
    public static final int OK = 0xFF3DDC84;
    public static final int WARN = 0xFFFFB020;
    public static final int ERR = 0xFFFF5D6A;

    /** Smallest screen width of the reference device (Redmi K40: 393dp). */
    private static final float BASE_WIDTH_DP = 393f;
    private static final float MIN_SCALE = 0.78f;
    private static final float MAX_SCALE = 1.15f;
    /** System font scale is clamped to this factor so fixed-size keys never overflow. */
    private static final float MAX_FONT_BOOST = 1.15f;
    private static final float MIN_FONT_BOOST = 0.85f;

    private static final String PREFS = "phonekb";
    private static final String KEY_UI_SIZE = "ui_size";
    public static final int UI_SIZE_AUTO = 0;
    public static final int UI_SIZE_SMALL = 1;
    public static final int UI_SIZE_STANDARD = 2;
    public static final int UI_SIZE_LARGE = 3;

    private static boolean inited;
    private static float density = 3f;
    private static float layoutScale = 1f;
    /** >1 when the user set bigger system fonts (clamped). */
    private static float fontBoost = 1f;
    private static int realWidthDp;
    private static int simulatedWidthDp;
    private static float simulatedFontScale;
    private static int effectiveWidthDp;

    // ------------------------------------------------------------------ metrics

    /** Recompute all metrics. Call from Activity.onCreate and onConfigurationChanged. */
    public static void init(Context context) {
        if (context == null) return;
        DisplayMetrics dm = context.getResources().getDisplayMetrics();
        density = dm.density > 0f ? dm.density : 3f;
        Configuration cfg = context.getResources().getConfiguration();
        realWidthDp = cfg.smallestScreenWidthDp > 0
                ? cfg.smallestScreenWidthDp
                : Math.min(cfg.screenWidthDp, cfg.screenHeightDp);
        int widthDp = simulatedWidthDp > 0 ? simulatedWidthDp : realWidthDp;
        if (widthDp <= 0) widthDp = Math.round(BASE_WIDTH_DP);
        effectiveWidthDp = widthDp;
        float auto = widthDp / BASE_WIDTH_DP;
        auto = Math.max(MIN_SCALE, Math.min(MAX_SCALE, auto));

        int manual = readManualMode(context);
        float manualFactor = manual == UI_SIZE_SMALL ? 0.92f
                : manual == UI_SIZE_LARGE ? 1.08f : 1f;
        layoutScale = auto * manualFactor;

        float systemScale = dm.density > 0f ? dm.scaledDensity / density : 1f;
        if (simulatedFontScale > 0f) systemScale = simulatedFontScale;
        fontBoost = Math.max(MIN_FONT_BOOST, Math.min(MAX_FONT_BOOST, systemScale));
        inited = true;
        Log.i("PhoneKB-UI", "init widthDp=" + widthDp + " realWidthDp=" + realWidthDp
                + " scale=" + layoutScale + " fontBoost=" + fontBoost + " manual=" + manual);
    }

    /** Debug/test hook: pretend the device is narrower / has bigger fonts. Call before init(). */
    public static void setSimulation(int widthDp, float fontScale) {
        simulatedWidthDp = Math.max(0, widthDp);
        simulatedFontScale = fontScale > 0f ? fontScale : 0f;
    }

    public static int simulatedWidthDp() { return simulatedWidthDp; }

    /** Real (not simulated) smallest width in dp, used to size the simulation container. */
    public static int realWidthDp() { return realWidthDp > 0 ? realWidthDp : Math.round(BASE_WIDTH_DP); }

    public static float layoutScale() { return layoutScale; }

    public static float fontBoost() { return fontBoost; }

    public static float dp(Context c, float v) {
        ensure(c);
        return v * density * layoutScale;
    }

    public static int dpi(Context c, float v) {
        return Math.round(dp(c, v));
    }

    private static void ensure(Context c) {
        if (!inited && c != null) init(c);
    }

    public static int readManualMode(Context c) {
        if (c == null) return UI_SIZE_AUTO;
        SharedPreferences p = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        int mode = p.getInt(KEY_UI_SIZE, UI_SIZE_AUTO);
        return mode < 0 || mode > UI_SIZE_LARGE ? UI_SIZE_AUTO : mode;
    }

    public static void writeManualMode(Context c, int mode) {
        if (c == null) return;
        c.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit().putInt(KEY_UI_SIZE, mode).apply();
    }

    // ------------------------------------------------------------------ paints

    public static Paint fill(int color) {
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setStyle(Paint.Style.FILL);
        p.setColor(color);
        return p;
    }

    public static Paint text(Context c, float spSize, int color) {
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setColor(color);
        p.setTextSize(spSize * c.getResources().getDisplayMetrics().density
                * layoutScale * fontBoost);
        p.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        p.setTextAlign(Paint.Align.CENTER);
        return p;
    }

    /**
     * Shrinks {@code paint}'s text size (never below {@code minSizePx}) so {@code value}
     * fits into {@code maxWidthPx}. Leaves the paint untouched when it already fits.
     */
    public static void fitText(Paint paint, String value, float maxWidthPx, float minSizePx) {
        if (paint == null || value == null || value.isEmpty() || maxWidthPx <= 0f) return;
        float size = paint.getTextSize();
        if (size <= 0f) return;
        float width = paint.measureText(value);
        if (width <= maxWidthPx) return;
        float target = size * (maxWidthPx / width);
        paint.setTextSize(Math.max(minSizePx, target));
    }

    /**
     * Gives a TextView an auto-shrink range expressed in "design dp" (so it follows the UI
     * scale). Useful for chrome pills/tabs with a fixed width and user font settings.
     */
    public static void autoSize(TextView tv, float maxSp, float minSp) {
        if (tv == null) return;
        Context c = tv.getContext();
        int max = Math.max(1, dpi(c, maxSp));
        int min = Math.max(1, Math.min(max - 1, dpi(c, minSp)));
        tv.setAutoSizeTextTypeUniformWithConfiguration(min, max, 1,
                TypedValue.COMPLEX_UNIT_PX);
    }

    // ----------------------------------------------------------------- drawables

    public static GradientDrawable rounded(Context c, int color, float radiusDp) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(dp(c, radiusDp));
        return d;
    }

    /** Rounded only at the top corners (bottom stays square), e.g. a docked keyboard sheet. */
    public static GradientDrawable roundedTop(Context c, int color, float radiusDp) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        float r = dp(c, radiusDp);
        d.setCornerRadii(new float[]{r, r, r, r, 0, 0, 0, 0});
        return d;
    }

    public static GradientDrawable roundedStroke(Context c, int fill, int strokeColor, float strokeDp, float radiusDp) {
        GradientDrawable d = rounded(c, fill, radiusDp);
        d.setStroke(Math.max(1, dpi(c, strokeDp)), strokeColor);
        return d;
    }

    public static void textCenter(Canvas canvas, String text, float cx, float cy, Paint paint) {
        Paint.FontMetrics fm = paint.getFontMetrics();
        canvas.drawText(text, cx, cy - (fm.ascent + fm.descent) / 2f, paint);
    }
}
