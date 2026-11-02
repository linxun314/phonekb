package com.phonekb;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.os.Handler;
import android.os.Looper;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;

/**
 * Trackpad laid out like Bluetooth Remote: a large pad on the left, a scroll
 * column on the right and a bottom row with left / middle / right mouse buttons.
 */
public class TouchpadView extends View {

    private static final int MODE_NONE = 0;
    private static final int MODE_PAD = 1;
    private static final int MODE_MOUSE = 2;
    private static final int MODE_SCROLL = 3;

    private static final int MOUSE_LEFT = 1;
    private static final int MOUSE_RIGHT = 2;
    private static final int MOUSE_MIDDLE = 4;

    private static final long TAP_MAX_MS = 300;

    private final HidManager hid;
    private final Handler handler = new Handler(Looper.getMainLooper());

    private final Paint bgPaint = Ui.fill(Ui.BG);
    private final Paint padPaint = Ui.fill(Ui.SURFACE);
    private final Paint keyPaint = Ui.fill(Ui.SURFACE_2);
    private final Paint pressPaint = Ui.fill(Ui.KEY_PRESSED);
    private final Paint iconPaint;
    private final Paint iconDimPaint;
    private final Paint labelPaint;

    private final RectF padRect = new RectF();
    private final RectF scrollUpRect = new RectF();
    private final RectF scrollDownRect = new RectF();
    private final RectF leftRect = new RectF();
    private final RectF middleRect = new RectF();
    private final RectF rightRect = new RectF();

    private final float margin;
    private final float gap;
    private final float corner;

    private final Runnable scrollRepeat = new Runnable() {
        @Override
        public void run() {
            if (mode == MODE_SCROLL && scrollDir != 0) {
                hid.mouseWheel(scrollDir);
                handler.postDelayed(this, 110);
            }
        }
    };

    private int mode = MODE_NONE;
    private int scrollDir;
    private int pressedMouse;

    private float lastX;
    private float lastY;
    private float downX;
    private float downY;
    private float accumX;
    private float accumY;
    private float scrollAcc;
    private float lastScrollY;
    private long downTime;
    private int maxPointers;
    private boolean moved;
    private boolean scrolled;

    public TouchpadView(Context context, HidManager hid) {
        super(context);
        this.hid = hid;
        margin = Ui.dp(context, 10);
        gap = Ui.dp(context, 3);
        corner = Ui.dp(context, 22);
        iconPaint = Ui.fill(Ui.TEXT);
        iconDimPaint = Ui.fill(Ui.TEXT_DIM);
        labelPaint = Ui.text(context, 13, Ui.TEXT_DIM);
    }

    @Override
    protected void onSizeChanged(int w, int h, int ow, int oh) {
        super.onSizeChanged(w, h, ow, oh);
        float contentW = w - margin * 2f;
        float contentH = h - margin * 2f;
        if (contentW <= 0 || contentH <= 0) return;

        boolean land = w > h;
        float buttonRowH = contentH * (land ? 0.24f : 0.2f);
        float padH = contentH - buttonRowH - gap;
        float scrollW = contentW * (land ? 0.18f : 0.15f);
        float padW = contentW - scrollW - gap;
        if (padW <= 0 || padH <= 0) return;

        padRect.set(margin, margin, margin + padW, margin + padH);
        scrollUpRect.set(margin + padW + gap, margin, margin + contentW, margin + (padH - gap) / 2f);
        scrollDownRect.set(scrollUpRect.left, scrollUpRect.bottom + gap, scrollUpRect.right, margin + padH);

        float top = margin + padH + gap;
        float bottom = margin + contentH;
        float usable = contentW - gap * 2f;
        float leftW = usable * 0.38f;
        float middleW = usable * 0.24f;
        leftRect.set(margin, top, margin + leftW, bottom);
        middleRect.set(leftRect.right + gap, top, leftRect.right + gap + middleW, bottom);
        rightRect.set(middleRect.right + gap, top, margin + contentW, bottom);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        canvas.drawColor(Ui.BG);
        drawCard(canvas, padRect, new float[]{corner, corner, 0, 0, 0, 0, 0, 0}, padPaint);
        drawCard(canvas, scrollUpRect, new float[]{0, 0, corner, corner, 0, 0, 0, 0}, keyPaint);
        drawCard(canvas, scrollDownRect, null, keyPaint);
        drawCard(canvas, leftRect, new float[]{0, 0, 0, 0, 0, 0, corner, corner}, keyPaint);
        drawCard(canvas, middleRect, null, keyPaint);
        drawCard(canvas, rightRect, new float[]{0, 0, 0, 0, corner, corner, 0, 0}, keyPaint);
        if (mode == MODE_SCROLL && scrollDir > 0) {
            drawCard(canvas, scrollUpRect, new float[]{0, 0, corner, corner, 0, 0, 0, 0}, pressPaint);
        } else if (mode == MODE_SCROLL && scrollDir < 0) {
            drawCard(canvas, scrollDownRect, null, pressPaint);
        }
        if (mode == MODE_MOUSE && pressedMouse == MOUSE_LEFT) {
            drawCard(canvas, leftRect, new float[]{0, 0, 0, 0, 0, 0, corner, corner}, pressPaint);
        } else if (mode == MODE_MOUSE && pressedMouse == MOUSE_MIDDLE) {
            drawCard(canvas, middleRect, null, pressPaint);
        } else if (mode == MODE_MOUSE && pressedMouse == MOUSE_RIGHT) {
            drawCard(canvas, rightRect, new float[]{0, 0, 0, 0, corner, corner, 0, 0}, pressPaint);
        }

        Paint icon = hid.isConnected() ? iconPaint : iconDimPaint;
        Icons.draw(canvas, Icons.UP_ARROW, scrollUpRect, icon);
        Icons.draw(canvas, Icons.DOWN_ARROW, scrollDownRect, icon);
        Ui.textCenter(canvas, "左键", leftRect.centerX(), leftRect.centerY(), labelPaint);
        Ui.textCenter(canvas, "中键", middleRect.centerX(), middleRect.centerY(), labelPaint);
        Ui.textCenter(canvas, "右键", rightRect.centerX(), rightRect.centerY(), labelPaint);
    }

    private void drawCard(Canvas canvas, RectF rect, float[] radii, Paint paint) {
        if (rect.isEmpty()) return;
        if (radii == null) {
            canvas.drawRect(rect, paint);
        } else {
            Path path = new Path();
            path.addRoundRect(rect, radii, Path.Direction.CW);
            canvas.drawPath(path, paint);
        }
    }

    private float averageY(MotionEvent e) {
        float sum = 0;
        for (int i = 0; i < e.getPointerCount(); i++) sum += e.getY(i);
        return sum / Math.max(1, e.getPointerCount());
    }

    private void startScroll(int dir) {
        mode = MODE_SCROLL;
        scrollDir = dir;
        performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
        hid.mouseWheel(dir);
        handler.postDelayed(scrollRepeat, 220);
        invalidate();
    }

    private void startMouse(int button) {
        mode = MODE_MOUSE;
        pressedMouse = button;
        performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
        hid.mouseButtons(button);
        invalidate();
    }

    private void releaseAll() {
        if (mode == MODE_MOUSE) hid.mouseButtons(0);
        handler.removeCallbacks(scrollRepeat);
        mode = MODE_NONE;
        scrollDir = 0;
        pressedMouse = 0;
        invalidate();
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN: {
                float x = e.getX();
                float y = e.getY();
                if (scrollUpRect.contains(x, y)) {
                    startScroll(1);
                } else if (scrollDownRect.contains(x, y)) {
                    startScroll(-1);
                } else if (leftRect.contains(x, y)) {
                    startMouse(MOUSE_LEFT);
                } else if (middleRect.contains(x, y)) {
                    startMouse(MOUSE_MIDDLE);
                } else if (rightRect.contains(x, y)) {
                    startMouse(MOUSE_RIGHT);
                } else if (padRect.contains(x, y)) {
                    mode = MODE_PAD;
                    lastX = x;
                    lastY = y;
                    downX = x;
                    downY = y;
                    downTime = System.currentTimeMillis();
                    accumX = 0;
                    accumY = 0;
                    scrollAcc = 0;
                    lastScrollY = y;
                    maxPointers = 1;
                    moved = false;
                    scrolled = false;
                }
                return true;
            }
            case MotionEvent.ACTION_POINTER_DOWN: {
                if (mode == MODE_PAD) {
                    maxPointers = Math.max(maxPointers, e.getPointerCount());
                    if (e.getPointerCount() >= 2) {
                        lastScrollY = averageY(e);
                        scrolled = false;
                    }
                }
                return true;
            }
            case MotionEvent.ACTION_MOVE: {
                if (mode != MODE_PAD) return true;
                int count = e.getPointerCount();
                if (count == 1) {
                    // Replay every sample the system batched into this event, not just the last
                    // one: dropping the in-between points is what makes fast flicks undershoot.
                    float px = lastX;
                    float py = lastY;
                    int hist = e.getHistorySize();
                    for (int i = 0; i < hist; i++) {
                        float hx = e.getHistoricalX(0, i);
                        float hy = e.getHistoricalY(0, i);
                        float gain = PointerSettings.gain(getContext(), hx - px, hy - py);
                        accumX += (hx - px) * gain;
                        accumY += (hy - py) * gain;
                        px = hx;
                        py = hy;
                    }
                    float x = e.getX();
                    float y = e.getY();
                    float gain = PointerSettings.gain(getContext(), x - px, y - py);
                    accumX += (x - px) * gain;
                    accumY += (y - py) * gain;
                    lastX = x;
                    lastY = y;
                    int dx = (int) accumX;
                    int dy = (int) accumY;
                    if (dx != 0 || dy != 0) {
                        hid.mouseMove(dx, dy);
                        accumX -= dx;
                        accumY -= dy;
                    }
                    if (Math.abs(x - downX) > Ui.dp(getContext(), 9)
                            || Math.abs(y - downY) > Ui.dp(getContext(), 9)) {
                        moved = true;
                    }
                } else if (count == 2) {
                    float y = (e.getY(0) + e.getY(1)) / 2f;
                    scrollAcc += y - lastScrollY;
                    lastScrollY = y;
                    float step = PointerSettings.scrollStepPx(getContext(), 20f);
                    int steps = 0;
                    while (scrollAcc >= step) {
                        steps++;
                        scrollAcc -= step;
                    }
                    while (scrollAcc <= -step) {
                        steps--;
                        scrollAcc += step;
                    }
                    if (steps != 0) {
                        hid.mouseWheel(steps);
                        scrolled = true;
                    }
                }
                return true;
            }
            case MotionEvent.ACTION_POINTER_UP: {
                if (mode == MODE_PAD) {
                    int index = e.getActionIndex();
                    for (int i = 0; i < e.getPointerCount(); i++) {
                        if (i != index) {
                            lastX = e.getX(i);
                            lastY = e.getY(i);
                            break;
                        }
                    }
                }
                return true;
            }
            case MotionEvent.ACTION_UP: {
                if (mode == MODE_MOUSE) {
                    hid.mouseButtons(0);
                    mode = MODE_NONE;
                    pressedMouse = 0;
                } else if (mode == MODE_SCROLL) {
                    handler.removeCallbacks(scrollRepeat);
                    mode = MODE_NONE;
                    scrollDir = 0;
                } else if (mode == MODE_PAD) {
                    long duration = System.currentTimeMillis() - downTime;
                    if (!scrolled && !moved && duration < TAP_MAX_MS
                            && maxPointers >= 1 && maxPointers <= 3) {
                        performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
                        if (maxPointers == 1) {
                            hid.mouseClick(MOUSE_LEFT);
                        } else if (maxPointers == 2) {
                            hid.mouseClick(MOUSE_RIGHT);
                        } else {
                            hid.mouseClick(MOUSE_MIDDLE);
                        }
                    }
                    mode = MODE_NONE;
                }
                invalidate();
                return true;
            }
            case MotionEvent.ACTION_CANCEL: {
                releaseAll();
                return true;
            }
            default:
                return true;
        }
    }
}
