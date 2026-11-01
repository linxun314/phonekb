package com.phonekb;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.os.Handler;
import android.os.Looper;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;

/** Toolbar button that sends the HID power key when held for 0.6 s (avoids accidental taps). */
public class PowerButtonView extends View {
    public interface Listener {
        void onPowerLongPress();
    }

    private static final long HOLD_MS = 600;

    private final Paint bgPaint = Ui.fill(Ui.SURFACE_2);
    private final Paint pressPaint = Ui.fill(Ui.KEY_PRESSED);
    private final Paint iconPaint = Ui.fill(Ui.TEXT);
    private final Paint iconDimPaint = Ui.fill(Ui.TEXT_DIM);
    private final RectF rect = new RectF();
    private final RectF iconBox = new RectF();
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final float radius;

    private Listener listener;
    private boolean pressed;
    private boolean fired;

    private final Runnable fire = new Runnable() {
        @Override
        public void run() {
            if (!pressed) return;
            fired = true;
            performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
            if (listener != null) listener.onPowerLongPress();
            invalidate();
        }
    };

    public PowerButtonView(Context context) {
        super(context);
        radius = Ui.dp(context, 12);
    }

    public void setListener(Listener listener) {
        this.listener = listener;
    }

    public void setConnected(boolean connected) {
        if (iconPaint == null) return;
        invalidate();
    }

    @Override
    protected void onSizeChanged(int w, int h, int ow, int oh) {
        super.onSizeChanged(w, h, ow, oh);
        rect.set(0, 0, w, h);
        float side = Math.min(w, h) * 0.62f;
        float cx = w / 2f;
        float cy = h / 2f;
        iconBox.set(cx - side / 2f, cy - side / 2f, cx + side / 2f, cy + side / 2f);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        canvas.drawRoundRect(rect, radius, radius, pressed ? pressPaint : bgPaint);
        Icons.draw(canvas, Icons.POWER, iconBox, fired ? iconDimPaint : iconPaint);
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN: {
                pressed = true;
                fired = false;
                handler.removeCallbacks(fire);
                handler.postDelayed(fire, HOLD_MS);
                invalidate();
                return true;
            }
            case MotionEvent.ACTION_MOVE: {
                if (pressed && !rect.contains(e.getX(), e.getY())) {
                    pressed = false;
                    handler.removeCallbacks(fire);
                    invalidate();
                }
                return true;
            }
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL: {
                handler.removeCallbacks(fire);
                pressed = false;
                fired = false;
                invalidate();
                return true;
            }
            default:
                return true;
        }
    }
}
