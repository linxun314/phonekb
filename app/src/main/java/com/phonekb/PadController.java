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
 *
 * <p>Latency notes: every batched {@link MotionEvent} carries up to a few historical samples, and
 * replaying all of them (instead of only the last one) keeps fast flicks from undershooting.
 * Moves are forwarded with the event timestamp so {@link HidManager} can report how much of the
 * delay happens before the report reaches the Bluetooth stack.
 */
public final class PadController {
    private static final int MODE_NONE = 0;
    private static final int MODE_PAD = 1;
    private static final int MODE_DRAG = 2;
    private static final int MOUSE_LEFT = 1;
    private static final int MOUSE_RIGHT = 2;
    private static final long TAP_MAX_MS = 300;

    private final View view;
    private final HidManager hid;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final float slop;

    private int mode = MODE_NONE;
    private int maxPointers;
    private boolean moved;
    private boolean scrolled;
    private boolean pressedDown;
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
            pressedDown = true;
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
        // Fast mode trades the tick for a couple of frames of latency.
        if (PointerSettings.fast()) return;
        view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
    }

    private void sendMove(float dx, float dy, long eventTime) {
        float gain = PointerSettings.gain(view.getContext(), dx, dy);
        accumX += dx * gain;
        accumY += dy * gain;
        int mx = (int) accumX;
        int my = (int) accumY;
        if (mx != 0 || my != 0) {
            hid.mouseMove(mx, my, eventTime);
            accumX -= mx;
            accumY -= my;
        }
    }

    public boolean onTouch(MotionEvent e) {
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN: {
                mode = MODE_PAD;
                maxPointers = 1;
                moved = false;
                scrolled = false;
                pressedDown = false;
                downTime = System.currentTimeMillis();
                downX = lastX = e.getX();
                downY = lastY = e.getY();
                accumX = accumY = scrollAcc = 0;
                lastScrollY = e.getY();
                handler.removeCallbacks(beginDrag);
                if (PointerSettings.clickDown()) {
                    // Click on press instead of release: the host sees the button a whole tap
                    // duration earlier. The button stays held until the finger lifts, so dragging
                    // still works and nothing can get stuck.
                    hid.mouseButtons(MOUSE_LEFT);
                    pressedDown = true;
                } else {
                    handler.postDelayed(beginDrag, PointerSettings.longPressMs());
                }
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
                final long eventTime = e.getEventTime();
                if (mode == MODE_DRAG) {
                    float px = lastX;
                    float py = lastY;
                    int hist = e.getHistorySize();
                    for (int i = 0; i < hist; i++) {
                        float hx = e.getHistoricalX(0, i);
                        float hy = e.getHistoricalY(0, i);
                        sendMove(hx - px, hy - py, eventTime);
                        px = hx;
                        py = hy;
                    }
                    float x = e.getX(0);
                    float y = e.getY(0);
                    sendMove(x - px, y - py, eventTime);
                    lastX = x;
                    lastY = y;
                    moved = true;
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
                    // Replay every batched sample so fast flicks do not undershoot.
                    float px = lastX;
                    float py = lastY;
                    int hist = e.getHistorySize();
                    for (int i = 0; i < hist; i++) {
                        float hx = e.getHistoricalX(0, i);
                        float hy = e.getHistoricalY(0, i);
                        sendMove(hx - px, hy - py, eventTime);
                        px = hx;
                        py = hy;
                    }
                    float x = e.getX(0);
                    float y = e.getY(0);
                    sendMove(x - px, y - py, eventTime);
                    lastX = x;
                    lastY = y;
                    if (Math.abs(x - downX) > slop || Math.abs(y - downY) > slop) {
                        moved = true;
                        handler.removeCallbacks(beginDrag);
                    }
                }
                return true;
            }
            case MotionEvent.ACTION_POINTER_UP: {
                if (mode == MODE_PAD) {
                    if (!moved && !scrolled && !pressedDown && maxPointers >= 2
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
                    if (PointerSettings.clickDown()) {
                        // Press already sent the click; release just lets the button go.
                        hid.mouseButtons(0);
                    } else if (!moved && !scrolled
                            && System.currentTimeMillis() - downTime < TAP_MAX_MS) {
                        haptic();
                        hid.mouseClick(maxPointers >= 2 ? MOUSE_RIGHT : MOUSE_LEFT);
                    }
                }
                pressedDown = false;
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
        if (mode == MODE_DRAG || pressedDown) hid.mouseButtons(0);
        pressedDown = false;
        mode = MODE_NONE;
    }
}
