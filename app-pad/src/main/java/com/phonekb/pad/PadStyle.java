package com.phonekb.pad;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;

final class PadStyle {
    static final int BG = 0xFF0E1117;
    static final int SURFACE = 0xFF1A2029;
    static final int SURFACE_2 = 0xFF232B37;
    static final int TEXT = 0xFFE9EDF3;
    static final int TEXT_DIM = 0xFF97A1AF;
    static final int ACCENT = 0xFF4C8DFF;
    static final int DANGER = 0xFFD9564A;

    private static float density = 3f;

    private PadStyle() {
    }

    static void init(Context c) {
        density = c.getResources().getDisplayMetrics().density;
    }

    static float dp(float v) {
        return v * density;
    }

    static Paint fill(int color) {
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setColor(color);
        p.setStyle(Paint.Style.FILL);
        return p;
    }

    static Paint stroke(int color, float width) {
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setColor(color);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(width);
        p.setStrokeCap(Paint.Cap.ROUND);
        p.setStrokeJoin(Paint.Join.ROUND);
        return p;
    }

    static Paint label(float sp, int color) {
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setColor(color);
        p.setTextAlign(Paint.Align.CENTER);
        p.setTextSize(sp * density);
        p.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
        return p;
    }

    static void round(Canvas c, RectF r, float radius, Paint p) {
        c.drawRoundRect(r, radius, radius, p);
    }

    static void center(Canvas c, String text, float cx, float cy, Paint p) {
        Paint.FontMetrics fm = p.getFontMetrics();
        c.drawText(text, cx, cy - (fm.ascent + fm.descent) / 2f, p);
    }

    static int blend(int a, int b, float t) {
        float it = 1f - t;
        int ar = (a >> 16) & 0xFF, ag = (a >> 8) & 0xFF, ab = a & 0xFF, aa = (a >>> 24);
        int br = (b >> 16) & 0xFF, bg = (b >> 8) & 0xFF, bb = b & 0xFF, ba = (b >>> 24);
        int r = (int) (ar * it + br * t);
        int g = (int) (ag * it + bg * t);
        int bl = (int) (ab * it + bb * t);
        int al = (int) (aa * it + ba * t);
        return (al << 24) | (r << 16) | (g << 8) | bl;
    }
}
