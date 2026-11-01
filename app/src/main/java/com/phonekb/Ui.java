package com.phonekb;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;

/** Colors, paints and small drawing helpers for the whole UI. */
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

    public static float dp(Context c, float v) {
        return v * c.getResources().getDisplayMetrics().density;
    }

    public static int dpi(Context c, float v) {
        return Math.round(dp(c, v));
    }

    public static Paint fill(int color) {
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setStyle(Paint.Style.FILL);
        p.setColor(color);
        return p;
    }

    public static Paint text(Context c, float spSize, int color) {
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setColor(color);
        p.setTextSize(spSize * c.getResources().getDisplayMetrics().scaledDensity);
        p.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        p.setTextAlign(Paint.Align.CENTER);
        return p;
    }

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