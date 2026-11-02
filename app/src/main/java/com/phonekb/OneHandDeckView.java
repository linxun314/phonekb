package com.phonekb;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;

import java.util.HashMap;
import java.util.Map;

/**
 * One-hand "remote deck": everything a thumb needs while the phone is held in
 * one hand. A trackpad on top, then three big rows: navigation (back / OK /
 * keyboard), media transport and volume. Docked to the left or right side in
 * landscape, to the bottom half in portrait.
 */
public class OneHandDeckView extends View {

    public interface Listener {
        void onSwitchToKeyboard();
    }

    private static final int B_BACK = 0;
    private static final int B_OK = 1;
    private static final int B_KB = 2;
    private static final int B_PREV = 3;
    private static final int B_PLAY = 4;
    private static final int B_NEXT = 5;
    private static final int B_VOL_UP = 6;
    private static final int B_VOL_DOWN = 7;
    private static final int B_MUTE = 8;
    private static final int BUTTON_COUNT = 9;

    private static final long TAP_MAX_MS = 300;

    private final HidManager hid;
    private Listener listener;
    private boolean rightHanded = true;
    private boolean landscape = true;

    private final Paint cardPaint = Ui.fill(Ui.SURFACE);
    private final Paint trackPaint = Ui.fill(Ui.SURFACE_2);
    private final Paint keyPaint = Ui.fill(Ui.SURFACE_2);
    private final Paint pressPaint = Ui.fill(Ui.KEY_PRESSED);
    private final Paint iconPaint = Ui.fill(Ui.TEXT);
    private final Paint iconDimPaint = Ui.fill(Ui.TEXT_DIM);
    private final Paint labelPaint;
    private final Paint labelDimPaint;
    private final Paint hintPaint;
    private final float corner;
    private final float radius;

    private final RectF panel = new RectF();
    private final RectF empty = new RectF();
    private final RectF track = new RectF();
    private final RectF[] buttons = new RectF[BUTTON_COUNT];
    private final Map<Integer, Integer> buttonPointers = new HashMap<>();

    private boolean tracking;
    private int maxPointers;
    private boolean moved;
    private boolean multiTapHandled;
    private long downTime;
    private float downX, downY, lastX, lastY;
    private float accumX, accumY, scrollAccum;

    public OneHandDeckView(Context context, HidManager hid) {
        super(context);
        this.hid = hid;
        for (int i = 0; i < BUTTON_COUNT; i++) buttons[i] = new RectF();
        corner = Ui.dp(context, 26);
        radius = Ui.dp(context, 18);
        labelPaint = Ui.text(context, 16, Ui.TEXT);
        labelDimPaint = Ui.text(context, 16, Ui.TEXT_DIM);
        hintPaint = Ui.text(context, 13, Ui.TEXT_DIM);
    }

    public void setListener(Listener listener) {
        this.listener = listener;
    }

    public void setRightHanded(boolean rightHanded) {
        this.rightHanded = rightHanded;
        layoutPanel();
        invalidate();
    }

    @Override
    protected void onSizeChanged(int w, int h, int ow, int oh) {
        super.onSizeChanged(w, h, ow, oh);
        layoutPanel();
    }

    private void layoutPanel() {
        float w = getWidth();
        float h = getHeight();
        if (w <= 0 || h <= 0) return;
        landscape = w > h;
        float margin = Ui.dp(getContext(), 10);
        float pad = Ui.dp(getContext(), 10);
        float gap = Ui.dp(getContext(), 8);
        if (landscape) {
            float panelW = w * 0.58f;
            float left = rightHanded ? w - margin - panelW : margin;
            panel.set(left, margin, left + panelW, h - margin);
            empty.set(rightHanded ? margin : panel.right, margin,
                    rightHanded ? panel.left : w - margin, h - margin);
        } else {
            float panelH = h * 0.62f;
            panel.set(margin, h - margin - panelH, w - margin, h - margin);
            empty.set(margin, margin, w - margin, panel.top);
        }
        float innerL = panel.left + pad;
        float innerR = panel.right - pad;
        float innerT = panel.top + pad;
        float innerB = panel.bottom - pad;
        float trackH = (innerB - innerT) * 0.40f;
        track.set(innerL, innerT, innerR, innerT + trackH);

        float gapTotal = gap * 2f;
        float rowH = (innerB - track.bottom - gap - gapTotal) / 3f;
        float colW = (innerR - innerL - gap * 2f) / 3f;
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 3; col++) {
                int index = row * 3 + col;
                float l = innerL + col * (colW + gap);
                float t = track.bottom + gap + row * (rowH + gap);
                buttons[index].set(l, t, l + colW, t + rowH);
            }
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        canvas.drawColor(Ui.BG);
        if (!empty.isEmpty()) {
            Ui.textCenter(canvas, landscape ? (rightHanded ? "左侧留空 · 方便握持" : "右侧留空 · 方便握持")
                    : "上半区留空 · 方便握持", empty.centerX(), empty.centerY(), hintPaint);
        }
        canvas.drawRoundRect(panel, corner, corner, cardPaint);
        canvas.drawRoundRect(track, corner * 0.85f, corner * 0.85f, trackPaint);

        Paint icon = hid.isConnected() ? iconPaint : iconDimPaint;
        Ui.textCenter(canvas, "触控区", track.centerX(), track.centerY() - Ui.dp(getContext(), 9), hintPaint);
        Ui.textCenter(canvas, "单指移动 · 点按=左键 · 双指滚动", track.centerX(),
                track.centerY() + Ui.dp(getContext(), 13), hintPaint);

        for (int i = 0; i < BUTTON_COUNT; i++) {
            RectF rect = buttons[i];
            boolean pressed = buttonPointers.containsValue(i);
            canvas.drawRoundRect(rect, radius, radius, pressed ? pressPaint : keyPaint);
            float cx = rect.centerX();
            float cy = rect.centerY();
            int iconId = iconFor(i);
            if (iconId != 0) {
                RectF box = new RectF(rect);
                box.inset(rect.width() * 0.32f, rect.height() * 0.22f);
                Icons.draw(canvas, iconId, box, icon);
            } else {
                Paint text = hid.isConnected() ? labelPaint : labelDimPaint;
                Ui.textCenter(canvas, labelFor(i), cx, cy, text);
            }
        }
    }

    private int iconFor(int index) {
        switch (index) {
            case B_PREV: return Icons.PREV;
            case B_PLAY: return Icons.PLAY_PAUSE;
            case B_NEXT: return Icons.NEXT;
            case B_VOL_UP: return Icons.VOL_UP;
            case B_VOL_DOWN: return Icons.VOL_DOWN;
            case B_MUTE: return Icons.MUTE;
            default: return 0;
        }
    }

    private String labelFor(int index) {
        switch (index) {
            case B_BACK: return "返回";
            case B_OK: return "确定";
            case B_KB: return "键盘";
            default: return "";
        }
    }

    private int hitButton(float x, float y) {
        for (int i = 0; i < BUTTON_COUNT; i++) {
            if (buttons[i].contains(x, y)) return i;
        }
        return -1;
    }

    private void perform(int index) {
        switch (index) {
            case B_BACK: hid.tapConsumer(HidConstants.CONSUMER_AC_BACK); break;
            case B_OK: hid.tapKey(0, HidConstants.KEY_ENTER); break;
            case B_KB: if (listener != null) listener.onSwitchToKeyboard(); break;
            case B_PREV: hid.tapConsumer(HidConstants.CONSUMER_PREV_TRACK); break;
            case B_PLAY: hid.tapConsumer(HidConstants.CONSUMER_PLAY_PAUSE); break;
            case B_NEXT: hid.tapConsumer(HidConstants.CONSUMER_NEXT_TRACK); break;
            case B_VOL_UP: hid.tapConsumer(HidConstants.CONSUMER_VOLUME_UP); break;
            case B_VOL_DOWN: hid.tapConsumer(HidConstants.CONSUMER_VOLUME_DOWN); break;
            case B_MUTE: hid.tapConsumer(HidConstants.CONSUMER_MUTE); break;
            default: break;
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        float slop = Ui.dp(getContext(), 8);
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN: {
                float x = e.getX();
                float y = e.getY();
                if (track.contains(x, y)) {
                    tracking = true;
                    maxPointers = 1;
                    moved = false;
                    multiTapHandled = false;
                    downTime = System.currentTimeMillis();
                    downX = lastX = x;
                    downY = lastY = y;
                    accumX = accumY = scrollAccum = 0;
                    return true;
                }
                int button = hitButton(x, y);
                if (button >= 0) {
                    buttonPointers.put(e.getPointerId(0), button);
                    performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
                    perform(button);
                    invalidate();
                }
                return true;
            }
            case MotionEvent.ACTION_POINTER_DOWN: {
                if (tracking) {
                    maxPointers = Math.max(maxPointers, e.getPointerCount());
                    lastX = e.getX(0);
                    lastY = e.getY(0);
                    scrollAccum = 0;
                    return true;
                }
                int index = e.getActionIndex();
                int button = hitButton(e.getX(index), e.getY(index));
                if (button >= 0) {
                    buttonPointers.put(e.getPointerId(index), button);
                    performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
                    perform(button);
                    invalidate();
                }
                return true;
            }
            case MotionEvent.ACTION_MOVE: {
                if (!tracking) return true;
                int count = e.getPointerCount();
                if (count >= 2) {
                    float y = e.getY(0);
                    scrollAccum += (y - lastY);
                    lastY = y;
                    lastX = e.getX(0);
                    float step = PointerSettings.scrollStepPx(getContext(), 18f);
                    int steps = (int) (scrollAccum / step);
                    if (steps != 0) {
                        hid.mouseWheel(steps);
                        scrollAccum -= steps * step;
                        moved = true;
                    }
                } else {
                    float x = e.getX(0);
                    float y = e.getY(0);
                    float gain = PointerSettings.gain(getContext(), x - lastX, y - lastY);
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
                    if (Math.abs(x - downX) + Math.abs(y - downY) > slop) moved = true;
                }
                return true;
            }
            case MotionEvent.ACTION_POINTER_UP: {
                if (tracking && !moved && maxPointers >= 2
                        && System.currentTimeMillis() - downTime < TAP_MAX_MS) {
                    hid.mouseClick(2);
                    multiTapHandled = true;
                }
                buttonPointers.remove(e.getPointerId(e.getActionIndex()));
                invalidate();
                return true;
            }
            case MotionEvent.ACTION_UP: {
                if (tracking) {
                    if (!moved && !multiTapHandled
                            && System.currentTimeMillis() - downTime < TAP_MAX_MS) {
                        hid.mouseClick(1);
                    }
                    tracking = false;
                }
                buttonPointers.remove(e.getPointerId(e.getActionIndex()));
                invalidate();
                return true;
            }
            case MotionEvent.ACTION_CANCEL: {
                tracking = false;
                buttonPointers.clear();
                invalidate();
                return true;
            }
            default:
                return true;
        }
    }
}
