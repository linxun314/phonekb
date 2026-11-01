package com.phonekb;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;

/** Big 3x4 digit keypad overlay for lock-screen PINs and numeric fields. */
public class NumpadView extends View {

    private static final String[] LABELS = {
            "1", "2", "3",
            "4", "5", "6",
            "7", "8", "9",
            "退格", "0", "Enter"
    };

    private static final int[] USAGES = {
            HidConstants.KEY_1, HidConstants.KEY_2, HidConstants.KEY_3,
            HidConstants.KEY_4, HidConstants.KEY_5, HidConstants.KEY_6,
            HidConstants.KEY_7, HidConstants.KEY_8, HidConstants.KEY_9,
            HidConstants.KEY_BACKSPACE, HidConstants.KEY_0, HidConstants.KEY_ENTER
    };

    private final HidManager hid;
    private final RectF[] rects = new RectF[LABELS.length];
    private final Paint keyPaint = Ui.fill(Ui.SURFACE_2);
    private final Paint pressPaint = Ui.fill(Ui.KEY_PRESSED);
    private final Paint accentPaint = Ui.fill(Ui.ACCENT);
    private final Paint labelPaint;
    private final Paint darkLabelPaint;
    private final Paint smallLabelPaint;
    private final float pad;
    private final float gap;
    private final float radius;

    private int pressed = -1;

    public NumpadView(Context context, HidManager hid) {
        super(context);
        this.hid = hid;
        for (int i = 0; i < rects.length; i++) rects[i] = new RectF();
        pad = Ui.dp(context, 8);
        gap = Ui.dp(context, 8);
        radius = Ui.dp(context, 16);
        labelPaint = Ui.text(context, 26, Ui.TEXT);
        darkLabelPaint = Ui.text(context, 18, Ui.BG);
        smallLabelPaint = Ui.text(context, 17, Ui.TEXT);
    }

    @Override
    protected void onSizeChanged(int w, int h, int ow, int oh) {
        super.onSizeChanged(w, h, ow, oh);
        float contentW = w - pad * 2f;
        float contentH = h - pad * 2f;
        if (contentW <= 0 || contentH <= 0) return;
        float colW = (contentW - gap * 2f) / 3f;
        float rowH = (contentH - gap * 3f) / 4f;
        for (int row = 0; row < 4; row++) {
            for (int col = 0; col < 3; col++) {
                int index = row * 3 + col;
                float left = pad + col * (colW + gap);
                float top = pad + row * (rowH + gap);
                rects[index].set(left, top, left + colW, top + rowH);
            }
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        for (int i = 0; i < rects.length; i++) {
            boolean enter = i == rects.length - 1;
            boolean down = i == pressed;
            Paint fill = enter ? (down ? pressPaint : accentPaint) : (down ? pressPaint : keyPaint);
            canvas.drawRoundRect(rects[i], radius, radius, fill);
            String label = LABELS[i];
            Paint text = enter ? darkLabelPaint : (label.length() > 1 ? smallLabelPaint : labelPaint);
            Ui.textCenter(canvas, label, rects[i].centerX(), rects[i].centerY(), text);
        }
    }

    private int hit(float x, float y) {
        for (int i = 0; i < rects.length; i++) {
            if (rects[i].contains(x, y)) return i;
        }
        return -1;
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN: {
                int index = hit(e.getX(), e.getY());
                if (index < 0) return true;
                pressed = index;
                performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
                hid.tapKey(0, USAGES[index]);
                invalidate();
                return true;
            }
            case MotionEvent.ACTION_MOVE: {
                if (pressed >= 0 && !rects[pressed].contains(e.getX(), e.getY())) {
                    pressed = -1;
                    invalidate();
                }
                return true;
            }
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL: {
                pressed = -1;
                invalidate();
                return true;
            }
            default:
                return true;
        }
    }
}
