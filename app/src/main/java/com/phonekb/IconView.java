package com.phonekb;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.View;

/** A single {@link Icons} glyph as a view, so pills and list rows can carry icons. */
public class IconView extends View {

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF box = new RectF();
    private int icon;
    private float inset = 0.10f;

    public IconView(Context context, int icon, int color) {
        super(context);
        this.icon = icon;
        paint.setColor(color);
    }

    public void setIcon(int icon) {
        if (this.icon == icon) return;
        this.icon = icon;
        invalidate();
    }

    public void setTint(int color) {
        if (paint.getColor() == color) return;
        paint.setColor(color);
        invalidate();
    }

    /** Fraction of the shorter side kept blank on each side (default 0.10 = snug). */
    public void setInset(float inset) {
        this.inset = inset;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        float pad = Math.min(getWidth(), getHeight()) * inset;
        box.set(pad, pad, getWidth() - pad, getHeight() - pad);
        Icons.draw(canvas, icon, box, paint);
    }
}
