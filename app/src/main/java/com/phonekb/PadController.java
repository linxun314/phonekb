package com.phonekb;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;

/**
 * Shared trackpad gesture logic (pointer moves, taps, right-click, scroll and
 * long-press-and-drag) used by every page that owns a trackpad surface.
 */
public final class PadController {
    private static final int MODE_NONE = 0;
    private static final int MODE_PAD = 1;
    private static final int MODE_DRAG = 2;
    private static final int MOUSE_LEFT = 1;
    private static final int MOUSE_RIGHT = 2;
    private static final long TAP_MAX_MS = 300;
    private static final long LONG_PRESS_MS = 450;

    private final View view;
    private final HidManager hid;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final float slop;

    private int mode = MODE_NONE;
    private int maxPointers;
    private boolean moved;
    private boolean scrolled;
    private float lastX;
    private float lastY;
    private float downX;
    private float downY;
    private float accumX;
    private float accumY;
    private float scrollAcc;
    private float lastScrollY;
    private long downTime;

    private final Runnable beginDrag = new Runnable() {
        @Override
        public void run() {
            if (mode != MODE_PAD || moved || scrolled) return;
            mode = MODE_DRAG;
            hid.mouseButtons(MOUSE_LEFT);
            haptic();
        }
    };

    public PadController(View view, HidManager hid) {
        this.view = view;
        this.hid = hid;
        Context context = view.getContext();
        slop = Ui.dp(context, 8);
    }

    private float averageY(MotionEvent e) {
        float sum = 0;
        for (int i = 0; i < e.getPointerCount(); i++) sum += e.getY(i);
        return sum / Math.max(1, e.getPointerCount());
    }

    private void haptic() {
        view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
    }

    public boolean onTouch(MotionEvent e) {
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN: {
                mode = MODE_PAD;
                maxPointers = 1;
                moved = false;
                scrolled = false;
                downTime = System.currentTimeMillis();
                downX = lastX = e.getX();
                downY = lastY = e.getY();
                accumX = accumY = scrollAcc = 0;
                lastScrollY = e.getY();
                handler.removeCallbacks(beginDrag);
                handler.postDelayed(beginDrag, LONG_PRESS_MS);
                return true;
            }
            case MotionEvent.ACTION_POINTER_DOWN: {
                if (mode == MODE_PAD) {
                    maxPointers = Math.max(maxPointers, e.getPointerCount());
                    handler.removeCallbacks(beginDrag);
                    lastScrollY = averageY(e);
                    lastX = e.getX(0);
                    lastY = e.getY(0);
                    scrollAcc = 0;
                }
                return true;
            }
            case MotionEvent.ACTION_MOVE: {
                if (mode == MODE_NONE) return false;
                if (mode == MODE_DRAG) {
                    float x = e.getX(0);
                    float y = e.getY(0);
                    float gain = PointerSettings.gain(view.getContext(), x - lastX, y - lastY);
                    accumX += (x - lastX) * gain;
                    accumY += (y - lastY) * gain;
                    lastX = x;
                    lastY = y;
                    int dx = (int) accumX;
                    int dy = (int) accumY;
                    if (dx != 0 || dy != 0) {
                        hid.mouseMove(dx, dy);
                        accumX -= dx;
                        accumY -= dy;
                        moved = true;
                    }
                    return true;
                }
                int count = e.getPointerCount();
                if (count >= 2) {
                    float y = averageY(e);
                    scrollAcc += y - lastScrollY;
                    lastScrollY = y;
                    float step = PointerSettings.scrollStepPx(view.getContext(), 10f);
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
                        moved = true;
                    }
                    lastX = e.getX(0);
                    lastY = e.getY(0);
                } else {
                    float x = e.getX(0);
                    float y = e.getY(0);
                    float gain = PointerSettings.gain(view.getContext(), x - lastX, y - lastY);
                    accumX += (x - lastX) * gain;
                    accumY += (y - lastY) * gain;
                    lastX = x;
                    lastY = y;
                    int dx = (int) accumX;
                    int dy = (int) accumY;
                    if (dx != 0 || dy != 0) {
                        hid.mouseMove(dx, dy);
                        accumX -= dx;
                        accumY -= dy;
                    }
                    if (Math.abs(x - downX) > slop || Math.abs(y - downY) > slop) {
                        moved = true;
                        handler.removeCallbacks(beginDrag);
                    }
                }
                return true;
            }
            case MotionEvent.ACTION_POINTER_UP: {
                if (mode == MODE_PAD) {
                    if (!moved && !scrolled && maxPointers >= 2
                            && System.currentTimeMillis() - downTime < TAP_MAX_MS) {
                        hid.mouseClick(MOUSE_RIGHT);
                        scrolled = true;
                        haptic();
                    }
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
                handler.removeCallbacks(beginDrag);
                if (mode == MODE_DRAG) {
                    hid.mouseButtons(0);
                } else if (mode == MODE_PAD) {
                    if (!moved && !scrolled && System.currentTimeMillis() - downTime < TAP_MAX_MS) {
                        haptic();
                        hid.mouseClick(maxPointers >= 2 ? MOUSE_RIGHT : MOUSE_LEFT);
                    }
                }
                mode = MODE_NONE;
                return true;
            }
            case MotionEvent.ACTION_CANCEL: {
                cancel();
                return true;
            }
            default:
                return false;
        }
    }

    public void cancel() {
        handler.removeCallbacks(beginDrag);
        if (mode == MODE_DRAG) hid.mouseButtons(0);
        mode = MODE_NONE;
    }
}
