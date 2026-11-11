package com.phonekb;

import android.content.Context;
import android.graphics.RectF;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;

/**
 * Shared trackpad gesture logic used by every page that owns a trackpad surface: pointer moves,
 * taps, right click, two-finger scroll, long-press drag and - since v0.39.0 - the laptop-style
 * gesture set (tap-and-drag, drag lock, fling, natural scrolling, edge push, palm rejection,
 * pinch, two/three/four-finger swipes).
 *
 * <p>Latency notes: every batched {@link MotionEvent} carries up to a few historical samples, and
 * replaying all of them (instead of only the last one) keeps fast flicks from undershooting.
 * Moves are forwarded with the event timestamp so {@link HidManager} can report how much of the
 * delay happens before the report reaches the Bluetooth stack.
 *
 * <p>Gesture notes: the guestures are layered so the old behaviour stays intact when the new
 * switches are off. A two-finger move starts out undecided; it becomes a pinch, a horizontal
 * swipe, a horizontal scroll or a vertical scroll depending on what dominates, and once decided
 * it stays in that mode until the fingers lift (that is what stops a scroll from turning into a
 * pinch halfway through).
 */
public final class PadController {
    private static final String TAG = "PhoneKB-Pad";

    private static final int MODE_NONE = 0;
    private static final int MODE_PAD = 1;
    private static final int MODE_DRAG = 2;
    /** Drag lock: the left button is still held after the finger lifted; the next tap drops it. */
    private static final int MODE_LOCKED = 3;
    /** Palm rejection swallowed this gesture; every event until the finger lifts is ignored. */
    private static final int MODE_IGNORE = 4;

    private static final int MOUSE_LEFT = 1;
    private static final int MOUSE_RIGHT = 2;
    private static final int MOUSE_MIDDLE = 4;

    private static final long TAP_MAX_MS = 300;
    private static final long TAP_DRAG_WINDOW_MS = 350;
    private static final long FLING_FRAME_MS = 16;

    private static final int TWO_UNDECIDED = 0;
    private static final int TWO_SCROLL = 1;
    private static final int TWO_HSCROLL = 2;
    private static final int TWO_PINCH = 3;
    private static final int TWO_DEAD = 4;

    private static final int DIR_UP = 0;
    private static final int DIR_DOWN = 1;
    private static final int DIR_LEFT = 2;
    private static final int DIR_RIGHT = 3;

    private final View view;
    private final HidManager hid;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final float slop;
    private final float tapDragSlop;
    private final float edgeZone;
    private final float swipeThreshold;
    private final RectF bounds = new RectF();

    private int mode = MODE_NONE;
    private int maxPointers;
    private boolean moved;
    private boolean scrolled;
    private boolean pressedDown;
    private boolean tapDragging;
    /**
     * v0.40.6: true while a long-press "grab" is holding the left button down, so the view can
     * draw a grab ring and the debug OSD can prove the state instead of relying on feel.
     */
    private boolean grabbing;
    private float grabX;
    private float grabY;
    private float grabTravel;
    private boolean gestureUsed;
    private float lastX;
    private float lastY;
    private float downX;
    private float downY;
    private float accumX;
    private float accumY;
    private float scrollAcc;
    private float lastScrollY;
    private long downTime;

    /** Time / place of the last completed one-finger tap (arm window for tap-and-drag). */
    private long lastTapUpAt;
    private float lastTapX;
    private float lastTapY;

    /** Two-finger sub-gesture state. */
    private int twoGesture = TWO_UNDECIDED;
    private float twoStartX;
    private float twoStartY;
    private float twoStartDist;
    private float pinchLastDist;
    private float pinchAcc;
    private float hScrollAcc;
    private boolean ctrlHeld;
    private boolean shiftHeld;

    /** Scroll velocity in px/ms of finger travel; used by the fling animation. */
    private float scrollVel;
    private long lastScrollAt;

    /** Three / four finger start point. */
    private float multiStartX;
    private float multiStartY;
    private long multiStartAt;

    private float flingVel;
    private float flingAcc;
    private long flingAt;

    private final Runnable beginDrag = new Runnable() {
        @Override
        public void run() {
            if (mode != MODE_PAD || moved || scrolled || gestureUsed) return;
            mode = MODE_DRAG;
            tapDragging = false;
            grabbing = true;
            grabX = downX;
            grabY = downY;
            grabTravel = 0f;
            hid.mouseButtons(MOUSE_LEFT);
            pressedDown = true;
            // v0.40.6: the grab must always be felt. The old haptic() short-circuits in 极速模式,
            // which is on by default — that is exactly why a long press "had no feel" at all.
            hapticForce();
            Log.i(TAG, "grab press: left button down at " + (int) downX + "," + (int) downY);
            view.invalidate();
        }
    };

    private final Runnable flingStep = new Runnable() {
        @Override
        public void run() {
            long now = System.currentTimeMillis();
            float dt = Math.min(48f, Math.max(1f, now - flingAt));
            flingAt = now;
            flingAcc += flingVel * dt;
            float step = PointerSettings.scrollStepPx(context(), 10f);
            int steps = 0;
            while (flingAcc >= step) {
                steps++;
                flingAcc -= step;
            }
            while (flingAcc <= -step) {
                steps--;
                flingAcc += step;
            }
            if (steps != 0) wheel(steps);
            flingVel *= 0.90f;
            if (Math.abs(flingVel) > 0.015f) {
                handler.postDelayed(this, FLING_FRAME_MS);
            } else {
                flingVel = 0f;
            }
        }
    };

    public PadController(View view, HidManager hid) {
        this.view = view;
        this.hid = hid;
        Context context = view.getContext();
        PadGestures.init(context);
        slop = Ui.dp(context, 8);
        tapDragSlop = Ui.dp(context, 14);
        edgeZone = Ui.dp(context, 18);
        swipeThreshold = Ui.dp(context, 46);
    }

    /** The pad rectangle inside the view (used by edge push); empty = the whole view. */
    public void setPadBounds(RectF rect) {
        bounds.set(rect);
    }

    private Context context() {
        return view.getContext();
    }

    private boolean pcMode() {
        return PadGestures.pcMode(context());
    }

    private void haptic() {
        // Fast mode trades the tick for a couple of frames of latency.
        if (PointerSettings.fast()) return;
        view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
    }

    private float padLeft() {
        return bounds.isEmpty() ? 0f : bounds.left;
    }

    private float padTop() {
        return bounds.isEmpty() ? 0f : bounds.top;
    }

    private float padRight() {
        return bounds.isEmpty() ? view.getWidth() : bounds.right;
    }

    private float padBottom() {
        return bounds.isEmpty() ? view.getHeight() : bounds.bottom;
    }

    /** Sends wheel notches, applying the 自然滚动方向 switch in one place. */
    private void wheel(int steps) {
        if (steps == 0) return;
        hid.mouseWheel(PadGestures.natural() ? -steps : steps);
    }

    private void sendMove(float dx, float dy, long eventTime, float x, float y) {
        float gain = PointerSettings.gain(context(), dx, dy);
        if (PadGestures.edge()) gain *= edgeGain(dx, dy, x, y);
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

    /**
     * Edge push: once the finger reaches the border of the pad and keeps pushing outwards, the
     * pointer keeps travelling (a real trackpad does the same). Fingers never leave the glass, so
     * without this the cursor stops exactly where the finger stops.
     */
    private float edgeGain(float dx, float dy, float x, float y) {
        boolean outX = (dx < 0 && x < padLeft() + edgeZone) || (dx > 0 && x > padRight() - edgeZone);
        boolean outY = (dy < 0 && y < padTop() + edgeZone) || (dy > 0 && y > padBottom() - edgeZone);
        return outX || outY ? 1.9f : 1f;
    }

    private float averageY(MotionEvent e) {
        float sum = 0;
        for (int i = 0; i < e.getPointerCount(); i++) sum += e.getY(i);
        return sum / Math.max(1, e.getPointerCount());
    }

    private float averageX(MotionEvent e) {
        float sum = 0;
        for (int i = 0; i < e.getPointerCount(); i++) sum += e.getX(i);
        return sum / Math.max(1, e.getPointerCount());
    }

    private float distance(MotionEvent e) {
        if (e.getPointerCount() < 2) return 0f;
        return (float) Math.hypot(e.getX(0) - e.getX(1), e.getY(0) - e.getY(1));
    }

    private boolean palmSized(MotionEvent e, int index) {
        // MotionEvent reports the contact size normalised to 0..1; a palm is a lot wider than a
        // fingertip (typically < 0.3). Swallowing it here stops the cursor from jumping when the
        // hand rests on the glass while typing.
        if (!PadGestures.palm()) return false;
        try {
            return e.getSize(index) > 0.55f;
        } catch (Exception ignored) {
            return false;
        }
    }

    private void releaseModifiers() {
        if (ctrlHeld || shiftHeld) {
            hid.keyboardRelease();
            ctrlHeld = false;
            shiftHeld = false;
        }
    }

    private void holdCtrl() {
        if (ctrlHeld) return;
        if (shiftHeld) {
            hid.keyboardRelease();
            shiftHeld = false;
        }
        hid.keyboardPress(HidConstants.MOD_LEFT_CTRL, 0);
        ctrlHeld = true;
    }

    private void holdShift() {
        if (shiftHeld) return;
        if (ctrlHeld) {
            hid.keyboardRelease();
            ctrlHeld = false;
        }
        hid.keyboardPress(HidConstants.MOD_LEFT_SHIFT, 0);
        shiftHeld = true;
    }

    private void stopFling() {
        handler.removeCallbacks(flingStep);
        flingVel = 0f;
        flingAcc = 0f;
    }

    private void startFling() {
        if (!PadGestures.fling()) return;
        if (Math.abs(scrollVel) < 0.06f) return;
        Log.i(TAG, "fling vel=" + scrollVel);
        flingVel = scrollVel;
        flingAcc = 0f;
        flingAt = System.currentTimeMillis();
        handler.removeCallbacks(flingStep);
        handler.post(flingStep);
    }

    private void resetTwoFinger(MotionEvent e) {
        twoGesture = TWO_UNDECIDED;
        twoStartX = averageX(e);
        twoStartY = averageY(e);
        twoStartDist = distance(e);
        pinchLastDist = twoStartDist;
        pinchAcc = 0f;
        hScrollAcc = 0f;
        scrollAcc = 0f;
        scrollVel = 0f;
        lastScrollAt = e.getEventTime();
        lastScrollY = averageY(e);
    }

    private void doThreeFinger(int dir) {
        if (hid == null) return;
        Log.i(TAG, "three fingers dir=" + dir + " pc=" + pcMode());
        haptic();
        if (pcMode()) {
            if (dir == DIR_UP) {
                hid.tapCombo(HidConstants.MOD_LEFT_GUI, HidConstants.KEY_TAB);
            } else if (dir == DIR_DOWN) {
                hid.tapCombo(HidConstants.MOD_LEFT_GUI, HidConstants.KEY_D);
            } else {
                hid.tapCombo(HidConstants.MOD_LEFT_ALT, HidConstants.KEY_TAB);
            }
        } else {
            if (dir == DIR_DOWN) {
                hid.tapConsumer(HidConstants.CONSUMER_AC_HOME);
            } else {
                // Android has no task view key: Alt+Tab is the system app switcher.
                hid.tapCombo(HidConstants.MOD_LEFT_ALT, HidConstants.KEY_TAB);
            }
        }
    }

    private void doFourFinger(int dir) {
        if (hid == null) return;
        Log.i(TAG, "four fingers dir=" + dir + " pc=" + pcMode());
        haptic();
        if (!pcMode()) return;
        hid.tapCombo(HidConstants.MOD_LEFT_CTRL | HidConstants.MOD_LEFT_GUI,
                dir == DIR_LEFT ? HidConstants.KEY_LEFT : HidConstants.KEY_RIGHT);
    }

    private void doTwoFingerSwipe(boolean right) {
        if (hid == null) return;
        Log.i(TAG, "two finger swipe right=" + right + " pc=" + pcMode());
        haptic();
        if (pcMode()) {
            // Windows trackpad convention: swipe right = back, swipe left = forward.
            hid.tapCombo(HidConstants.MOD_LEFT_ALT,
                    right ? HidConstants.KEY_LEFT : HidConstants.KEY_RIGHT);
        } else if (right) {
            hid.tapConsumer(HidConstants.CONSUMER_AC_BACK);
        }
    }

    public boolean onTouch(MotionEvent e) {
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN: {
                stopFling();
                if (palmSized(e, 0)) {
                    mode = MODE_IGNORE;
                    return true;
                }
                if (mode == MODE_LOCKED) {
                    // The locked drag is dropped by this tap and the tap itself does nothing else.
                    hid.mouseButtons(0);
                    pressedDown = false;
                    mode = MODE_NONE;
                    return true;
                }
                mode = MODE_PAD;
                maxPointers = 1;
                moved = false;
                scrolled = false;
                pressedDown = false;
                tapDragging = false;
                gestureUsed = false;
                twoGesture = TWO_UNDECIDED;
                ctrlHeld = shiftHeld = false;
                downTime = System.currentTimeMillis();
                downX = lastX = e.getX();
                downY = lastY = e.getY();
                accumX = accumY = scrollAcc = 0;
                lastScrollY = e.getY();
                scrollVel = 0f;
                handler.removeCallbacks(beginDrag);
                boolean nearLastTap = PadGestures.tapDrag() && !PointerSettings.clickDown()
                        && lastTapUpAt > 0
                        && downTime - lastTapUpAt < TAP_DRAG_WINDOW_MS
                        && Math.hypot(downX - lastTapX, downY - lastTapY) < tapDragSlop;
                if (PointerSettings.clickDown()) {
                    // Click on press instead of release: the host sees the button a whole tap
                    // duration earlier. The button stays held until the finger lifts, so dragging
                    // still works and nothing can get stuck.
                    hid.mouseButtons(MOUSE_LEFT);
                    pressedDown = true;
                } else if (nearLastTap) {
                    // Tap-and-drag: a second tap that starts moving drags without waiting for the
                    // long press, which is how every laptop trackpad behaves.
                    mode = MODE_DRAG;
                    tapDragging = true;
                    hid.mouseButtons(MOUSE_LEFT);
                    pressedDown = true;
                    haptic();
                } else {
                    handler.postDelayed(beginDrag, PointerSettings.longPressMs());
                }
                return true;
            }
            case MotionEvent.ACTION_POINTER_DOWN: {
                if (mode == MODE_IGNORE) return true;
                if (mode == MODE_DRAG && !tapDragging) {
                    // A second finger aborts a long-press drag instead of mixing both gestures.
                    return true;
                }
                if (mode == MODE_PAD) {
                    maxPointers = Math.max(maxPointers, e.getPointerCount());
                    handler.removeCallbacks(beginDrag);
                    lastX = e.getX(0);
                    lastY = e.getY(0);
                    if (e.getPointerCount() == 2) {
                        resetTwoFinger(e);
                    }
                    if (e.getPointerCount() >= 3) {
                        multiStartX = averageX(e);
                        multiStartY = averageY(e);
                        multiStartAt = System.currentTimeMillis();
                        scrolled = true;
                    }
                }
                return true;
            }
            case MotionEvent.ACTION_MOVE: {
                if (mode == MODE_IGNORE) return true;
                if (mode == MODE_LOCKED) return true;
                if (mode == MODE_NONE) return false;
                final long eventTime = e.getEventTime();
                if (mode == MODE_DRAG) {
                    dragMove(e, eventTime);
                    return true;
                }
                int count = e.getPointerCount();
                if (count >= 3) {
                    multiFingerMove(e);
                    return true;
                }
                if (count == 2) {
                    twoFingerMove(e, eventTime);
                    return true;
                }
                // Single finger: replay every batched sample so fast flicks do not undershoot.
                float px = lastX;
                float py = lastY;
                int hist = e.getHistorySize();
                for (int i = 0; i < hist; i++) {
                    float hx = e.getHistoricalX(0, i);
                    float hy = e.getHistoricalY(0, i);
                    sendMove(hx - px, hy - py, eventTime, hx, hy);
                    px = hx;
                    py = hy;
                }
                float x = e.getX(0);
                float y = e.getY(0);
                sendMove(x - px, y - py, eventTime, x, y);
                lastX = x;
                lastY = y;
                if (Math.abs(x - downX) > slop || Math.abs(y - downY) > slop) {
                    moved = true;
                    handler.removeCallbacks(beginDrag);
                }
                return true;
            }
            case MotionEvent.ACTION_POINTER_UP: {
                if (mode == MODE_IGNORE) return true;
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
                    if (e.getPointerCount() - 1 <= 1) {
                        releaseModifiers();
                        startFling();
                        twoGesture = TWO_UNDECIDED;
                    }
                }
                return true;
            }
            case MotionEvent.ACTION_UP: {
                handler.removeCallbacks(beginDrag);
                if (mode == MODE_IGNORE) {
                    mode = MODE_NONE;
                    return true;
                }
                if (mode == MODE_DRAG) {
                    if (tapDragging && PadGestures.dragLock()) {
                        // Drag lock: keep holding the button after the finger lifts; the next tap
                        // (handled in ACTION_DOWN) drops whatever is being dragged.
                        mode = MODE_LOCKED;
                        Log.i(TAG, "drag locked");
                        haptic();
                    } else {
                        hid.mouseButtons(0);
                        mode = MODE_NONE;
                    }
                    if (grabbing) {
                        grabbing = false;
                        Log.i(TAG, "grab release: travel=" + (int) grabTravel + "px");
                        view.invalidate();
                    }
                    pressedDown = false;
                    recordTap(downX, downY);
                    releaseModifiers();
                    return true;
                }
                if (mode == MODE_PAD) {
                    long held = System.currentTimeMillis() - downTime;
                    boolean tap = !moved && !scrolled && held < TAP_MAX_MS;
                    if (PointerSettings.clickDown()) {
                        // Press already sent the click; release just lets the button go.
                        hid.mouseButtons(0);
                    } else if (tap) {
                        haptic();
                        hid.mouseClick(maxPointers >= 2 ? MOUSE_RIGHT : MOUSE_LEFT);
                    }
                    if (tap) recordTap(downX, downY);
                    if (scrolled) startFling();
                }
                releaseModifiers();
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

    private void recordTap(float x, float y) {
        lastTapUpAt = System.currentTimeMillis();
        lastTapX = x;
        lastTapY = y;
    }

    private void dragMove(MotionEvent e, long eventTime) {
        float px = lastX;
        float py = lastY;
        int hist = e.getHistorySize();
        for (int i = 0; i < hist; i++) {
            float hx = e.getHistoricalX(0, i);
            float hy = e.getHistoricalY(0, i);
            sendMove(hx - px, hy - py, eventTime, hx, hy);
            px = hx;
            py = hy;
        }
        float x = e.getX(0);
        float y = e.getY(0);
        sendMove(x - px, y - py, eventTime, x, y);
        lastX = x;
        lastY = y;
        moved = true;
        if (grabbing) {
            grabTravel += (float) Math.hypot(x - grabX, y - grabY);
            grabX = x;
            grabY = y;
            view.invalidate();
        }
    }

    /** Two fingers: pinch / horizontal swipe / horizontal scroll / vertical scroll. */
    private void twoFingerMove(MotionEvent e, long eventTime) {
        float dy = averageY(e) - lastScrollY;
        lastScrollY = averageY(e);
        lastX = e.getX(0);
        lastY = e.getY(0);
        if (twoGesture == TWO_UNDECIDED) {
            twoGesture = decideTwoFinger(e);
            Log.i(TAG, "two fingers decided=" + twoGesture);
            if (twoGesture == TWO_PINCH) holdCtrl();
            if (twoGesture == TWO_HSCROLL) holdShift();
            if (twoGesture == TWO_DEAD) return;
        }
        if (twoGesture == TWO_PINCH) {
            float dist = distance(e);
            pinchAcc += dist - pinchLastDist;
            pinchLastDist = dist;
            float step = Ui.dp(context(), 24);
            int steps = 0;
            while (pinchAcc >= step) {
                steps++;
                pinchAcc -= step;
            }
            while (pinchAcc <= -step) {
                steps--;
                pinchAcc += step;
            }
            if (steps != 0) {
                // Ctrl is held for the whole pinch, so the host sees a zoom and not a scroll.
                hid.mouseWheel(steps);
                scrolled = true;
            }
            return;
        }
        if (twoGesture == TWO_HSCROLL) {
            float dx = averageX(e) - twoStartX;
            hScrollAcc += dx;
            twoStartX = averageX(e);
            float step = PointerSettings.scrollStepPx(context(), 10f);
            int steps = 0;
            while (hScrollAcc >= step) {
                steps++;
                hScrollAcc -= step;
            }
            while (hScrollAcc <= -step) {
                steps--;
                hScrollAcc += step;
            }
            if (steps != 0) {
                hid.mouseWheel(-steps);
                scrolled = true;
            }
            return;
        }
        // Vertical scroll (the historical gesture).
        scrollAcc += dy;
        long now = eventTime;
        float dt = Math.max(1f, now - lastScrollAt);
        lastScrollAt = now;
        scrollVel = scrollVel * 0.6f + (dy / dt) * 0.4f;
        float step = PointerSettings.scrollStepPx(context(), 10f);
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
            wheel(steps);
            scrolled = true;
            moved = true;
        }
    }

    /** Decides once per two-finger gesture which sub-gesture it is. */
    private int decideTwoFinger(MotionEvent e) {
        float dx = averageX(e) - twoStartX;
        float dy = averageY(e) - twoStartY;
        boolean pc = pcMode();
        if (PadGestures.pinch() && Math.abs(distance(e) - twoStartDist) > Ui.dp(context(), 16)
                && Math.abs(distance(e) - twoStartDist) > Math.abs(dy) * 1.2f) {
            return TWO_PINCH;
        }
        boolean horizontal = Math.abs(dx) > Math.abs(dy) * 1.5f && Math.abs(dx) > Ui.dp(context(), 14);
        if (horizontal) {
            boolean allowed = pc ? (PadGestures.pcBack() || PadGestures.pcHScroll())
                    : PadGestures.pcBack();
            if (!allowed) return TWO_DEAD;
            boolean fast = Math.abs(dx) > swipeThreshold
                    && System.currentTimeMillis() - downTime < 420;
            boolean wantSwipe = pc ? PadGestures.pcBack() : PadGestures.pcBack();
            if (fast && wantSwipe && !scrolled) {
                doTwoFingerSwipe(dx > 0);
                scrolled = true;
                moved = true;
                return TWO_DEAD;
            }
            if (pc && PadGestures.pcHScroll()) return TWO_HSCROLL;
            return TWO_DEAD;
        }
        return TWO_SCROLL;
    }

    /** Three / four fingers: once one direction wins, the gesture is consumed. */
    private void multiFingerMove(MotionEvent e) {
        if (gestureUsed) return;
        int count = e.getPointerCount();
        float dx = averageX(e) - multiStartX;
        float dy = averageY(e) - multiStartY;
        float threshold = Ui.dp(context(), 25);
        if (Math.abs(dx) < threshold && Math.abs(dy) < threshold) return;
        if (System.currentTimeMillis() - multiStartAt > 900) {
            // Held too long: treat as a dead gesture so it can never fire late.
            gestureUsed = true;
            return;
        }
        if (count >= 4 && PadGestures.four()) {
            if (Math.abs(dx) > Math.abs(dy)) {
                doFourFinger(dx > 0 ? DIR_RIGHT : DIR_LEFT);
                gestureUsed = true;
            }
            return;
        }
        if (count >= 3 && PadGestures.three()) {
            int dir;
            if (Math.abs(dy) >= Math.abs(dx)) {
                dir = dy > 0 ? DIR_DOWN : DIR_UP;
            } else {
                dir = dx > 0 ? DIR_RIGHT : DIR_LEFT;
            }
            doThreeFinger(dir);
            gestureUsed = true;
        }
    }

    /** True while the long-press grab is holding the left button (drawn as a ring + 抓 chip). */
    public boolean grabbing() {
        return grabbing;
    }

    public float grabX() {
        return grabX;
    }

    public float grabY() {
        return grabY;
    }

    /** Finger travel since the grab started, in view pixels (debug OSD). */
    public float grabTravel() {
        return grabTravel;
    }

    /**
     * v0.40.6: a grab has to be felt. haptic() is silenced by 极速模式 (on by default), so the
     * grab uses a long-press tick that always fires.
     */
    private void hapticForce() {
        if (view != null) view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
    }

    /**
     * Debug hook (--ei grabhold 1): fake a grab in the middle of the pad and hold the left button
     * for a few seconds, so a screenshot (and logcat) can show the grabbed state. MIUI refuses
     * "adb shell input", so the real gesture cannot be scripted.
     */
    public void debugGrabHold() {
        cancel();
        mode = MODE_DRAG;
        grabbing = true;
        grabX = bounds.centerX();
        grabY = bounds.centerY();
        grabTravel = 0f;
        pressedDown = true;
        hid.mouseButtons(MOUSE_LEFT);
        hapticForce();
        Log.i(TAG, "debug grab hold: left button down");
        view.invalidate();
        handler.postDelayed(() -> {
            grabbing = false;
            hid.mouseButtons(0);
            pressedDown = false;
            mode = MODE_NONE;
            view.invalidate();
            Log.i(TAG, "debug grab hold: released");
        }, 6000);
    }

    public void cancel() {
        handler.removeCallbacks(beginDrag);
        stopFling();
        if (mode == MODE_DRAG || mode == MODE_LOCKED || pressedDown) hid.mouseButtons(0);
        releaseModifiers();
        pressedDown = false;
        tapDragging = false;
        grabbing = false;
        mode = MODE_NONE;
        twoGesture = TWO_UNDECIDED;
        gestureUsed = false;
    }
}
