package com.phonekb.pad;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;

/** iPad 用触控板页。 */
public class PadTouchpadView extends View {

    private static final float SENSITIVITY = 1.7f;

    private final RectF pad = new RectF();
    private final RectF scrollRect = new RectF();
    private final RectF homeRect = new RectF();
    private final RectF switchRect = new RectF();
    private final RectF searchRect = new RectF();
    private final RectF scratch = new RectF();
    private final float[] radii = new float[8];

    private final Paint padPaint = PadStyle.fill(PadStyle.SURFACE);
    private final Paint pillPaint = PadStyle.fill(PadStyle.SURFACE_2);
    private final Paint accentPaint = PadStyle.fill(PadStyle.ACCENT);
    private final Paint litPaint = PadStyle.fill(PadStyle.blend(PadStyle.SURFACE_2, PadStyle.ACCENT, 0.35f));
    private final Paint labelPaint = PadStyle.label(12f, PadStyle.TEXT);
    private final Paint dotPaint = PadStyle.fill(PadStyle.TEXT_DIM);
    private final Paint hintPaint = PadStyle.label(12f, PadStyle.TEXT_DIM);
    private final Paint chevronPaint = PadStyle.stroke(PadStyle.TEXT_DIM, PadStyle.dp(2f));
    private final Paint chevronAccentPaint = PadStyle.stroke(PadStyle.ACCENT, PadStyle.dp(2f));

    private final Handler handler = new Handler(Looper.getMainLooper());

    private boolean tracking;
    private boolean dragging;
    private boolean twoFinger;
    private float lastX;
    private float lastY;
    private float moved;
    private float scrollLastY;
    private float scrollAccum;
    private long downAt;
    private int scrollPressed;
    private int pressedPill = -1;
    private Runnable longPress;
    private Runnable scrollRepeat;

    public PadTouchpadView(Context context) {
        super(context);
        setBackgroundColor(PadStyle.BG);
    }

    private HidManager hid() {
        return HidManager.get(getContext());
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        float m = PadStyle.dp(12f);
        float gap = PadStyle.dp(10f);
        float leftW = PadStyle.dp(66f);
        float scrollW = PadStyle.dp(58f);
        float top = m;
        float bottom = h - m;
        float pillH = (bottom - top - gap * 2f) / 3f;
        homeRect.set(m, top, m + leftW, top + pillH);
        switchRect.set(m, top + pillH + gap, m + leftW, top + pillH * 2f + gap * 2f);
        searchRect.set(m, top + (pillH + gap) * 2f, m + leftW, bottom);
        pad.set(m + leftW + gap, top, w - m - scrollW - gap, bottom);
        scrollRect.set(w - m - scrollW, top, w - m, bottom);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        PadStyle.round(canvas, pad, PadStyle.dp(16f), padPaint);
        float cx = pad.centerX();
        float cy = pad.centerY();
        canvas.drawCircle(cx, cy - PadStyle.dp(26f), PadStyle.dp(6f), dotPaint);
        PadStyle.center(canvas, "触控区", cx, cy - PadStyle.dp(4f), labelPaint);
        PadStyle.center(canvas, "单指移动 · 轻点=左键 · 双指滑动=滚动", cx, cy + PadStyle.dp(20f), hintPaint);
        drawPill(canvas, homeRect, "主屏", pressedPill == 0);
        drawPill(canvas, switchRect, "切App", pressedPill == 1);
        drawPill(canvas, searchRect, "搜索", pressedPill == 2);
        drawScroll(canvas);
    }

    private void drawPill(Canvas canvas, RectF rect, String text, boolean pressed) {
        PadStyle.round(canvas, rect, PadStyle.dp(14f), pressed ? accentPaint : pillPaint);
        PadStyle.center(canvas, text, rect.centerX(), rect.centerY(), labelPaint);
    }

    private void drawScroll(Canvas canvas) {
        float r = PadStyle.dp(12f);
        PadStyle.round(canvas, scrollRect, r, pillPaint);
        if (scrollPressed != 0) {
            boolean up = scrollPressed > 0;
            float midY = scrollRect.centerY();
            scratch.set(scrollRect.left, up ? scrollRect.top : midY, scrollRect.right, up ? midY : scrollRect.bottom);
            radii[0] = up ? r : 0f;
            radii[1] = up ? r : 0f;
            radii[2] = up ? r : 0f;
            radii[3] = up ? r : 0f;
            radii[4] = up ? 0f : r;
            radii[5] = up ? 0f : r;
            radii[6] = up ? 0f : r;
            radii[7] = up ? 0f : r;
            Path path = new Path();
            path.addRoundRect(scratch, radii, Path.Direction.CW);
            canvas.drawPath(path, litPaint);
        }
        float cx = scrollRect.centerX();
        float half = PadStyle.dp(7f);
        float lift = PadStyle.dp(7f);
        float topY = scrollRect.top + PadStyle.dp(30f);
        float bottomY = scrollRect.bottom - PadStyle.dp(30f);
        Path up = new Path();
        up.moveTo(cx - half, topY);
        up.lineTo(cx, topY - lift);
        up.lineTo(cx + half, topY);
        canvas.drawPath(up, scrollPressed > 0 ? chevronAccentPaint : chevronPaint);
        Path down = new Path();
        down.moveTo(cx - half, bottomY);
        down.lineTo(cx, bottomY + lift);
        down.lineTo(cx + half, bottomY);
        canvas.drawPath(down, scrollPressed < 0 ? chevronAccentPaint : chevronPaint);
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        int action = e.getActionMasked();
        if (action == MotionEvent.ACTION_DOWN) {
            float x = e.getX();
            float y = e.getY();
            if (scrollRect.contains(x, y)) {
                startScroll(y < scrollRect.centerY() ? 1 : -1);
                return true;
            }
            if (homeRect.contains(x, y)) {
                pressedPill = 0;
                tapGui(0x0B);
                invalidate();
                return true;
            }
            if (switchRect.contains(x, y)) {
                pressedPill = 1;
                tapGui(0x2B);
                invalidate();
                return true;
            }
            if (searchRect.contains(x, y)) {
                pressedPill = 2;
                tapGui(0x2C);
                invalidate();
                return true;
            }
            if (pad.contains(x, y)) {
                beginTrack(x, y);
                return true;
            }
            return false;
        }
        if (action == MotionEvent.ACTION_POINTER_DOWN) {
            twoFinger = true;
            tracking = false;
            cancelPendingLongPress();
            scrollLastY = averageY(e);
            scrollAccum = 0f;
            return true;
        }
        if (action == MotionEvent.ACTION_MOVE) {
            if (twoFinger && e.getPointerCount() >= 2) {
                float avg = averageY(e);
                scrollAccum += avg - scrollLastY;
                scrollLastY = avg;
                float step = PadStyle.dp(18f);
                while (scrollAccum >= step) {
                    wheel(1);
                    scrollAccum -= step;
                }
                while (scrollAccum <= -step) {
                    wheel(-1);
                    scrollAccum += step;
                }
                return true;
            }
            if (tracking) {
                float x = e.getX();
                float y = e.getY();
                float dx = x - lastX;
                float dy = y - lastY;
                lastX = x;
                lastY = y;
                moved += Math.abs(dx) + Math.abs(dy);
                if (moved > PadStyle.dp(6f)) cancelPendingLongPress();
                int mx = Math.round(dx * SENSITIVITY);
                int my = Math.round(dy * SENSITIVITY);
                if (mx != 0 || my != 0) {
                    try {
                        hid().mouseMove(mx, my);
                    } catch (Throwable ignored) {
                    }
                }
            }
            return true;
        }
        if (action == MotionEvent.ACTION_UP) {
            stopScroll();
            pressedPill = -1;
            if (tracking) {
                cancelPendingLongPress();
                long dur = SystemClock.uptimeMillis() - downAt;
                if (dragging) {
                    try {
                        hid().mouseButtons(0);
                    } catch (Throwable ignored) {
                    }
                } else if (moved < PadStyle.dp(12f) && dur < 450L) {
                    try {
                        hid().mouseClick(1);
                    } catch (Throwable ignored) {
                    }
                }
            }
            resetTracking();
            invalidate();
            return true;
        }
        if (action == MotionEvent.ACTION_CANCEL) {
            stopScroll();
            pressedPill = -1;
            cancelPendingLongPress();
            if (dragging) {
                try {
                    hid().mouseButtons(0);
                } catch (Throwable ignored) {
                }
            }
            resetTracking();
            invalidate();
            return true;
        }
        return true;
    }

    private void beginTrack(float x, float y) {
        tracking = true;
        dragging = false;
        twoFinger = false;
        lastX = x;
        lastY = y;
        moved = 0f;
        scrollAccum = 0f;
        downAt = SystemClock.uptimeMillis();
        longPress = new Runnable() {
            @Override
            public void run() {
                if (!tracking || twoFinger || moved > PadStyle.dp(12f)) return;
                dragging = true;
                try {
                    hid().mouseButtons(1);
                } catch (Throwable ignored) {
                }
                performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
                invalidate();
            }
        };
        handler.postDelayed(longPress, 350L);
    }

    private void resetTracking() {
        tracking = false;
        dragging = false;
        twoFinger = false;
        moved = 0f;
        scrollAccum = 0f;
    }

    private void cancelPendingLongPress() {
        if (longPress != null) {
            handler.removeCallbacks(longPress);
            longPress = null;
        }
    }

    private float averageY(MotionEvent e) {
        float sum = 0f;
        int n = e.getPointerCount();
        for (int i = 0; i < n; i++) sum += e.getY(i);
        return sum / Math.max(1, n);
    }

    private void wheel(int steps) {
        try {
            hid().mouseWheel(steps);
        } catch (Throwable ignored) {
        }
    }

    private void tapGui(int usage) {
        performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
        try {
            hid().tapKey(0x08, usage);
        } catch (Throwable ignored) {
        }
    }

    private void startScroll(int dir) {
        scrollPressed = dir;
        performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
        wheel(dir);
        if (scrollRepeat != null) handler.removeCallbacks(scrollRepeat);
        scrollRepeat = new Runnable() {
            @Override
            public void run() {
                if (scrollPressed == 0) return;
                wheel(scrollPressed);
                handler.postDelayed(this, 110L);
            }
        };
        handler.postDelayed(scrollRepeat, 220L);
        invalidate();
    }

    private void stopScroll() {
        if (scrollRepeat != null) {
            handler.removeCallbacks(scrollRepeat);
            scrollRepeat = null;
        }
        if (scrollPressed != 0) {
            scrollPressed = 0;
            invalidate();
        }
    }
}
