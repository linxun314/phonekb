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
 * 双手模式: left half is a touchpad, right half is a button board with navigation,
 * media, volume and the two input-panel toggles. Both thumbs stay on their own side.
 */
public class TwoHandView extends View {

    public interface Listener {
        void onToggleNumpad();

        void onToggleKeyboard();
    }

    private static final int B_BACK = 0;
    private static final int B_HOME = 1;
    private static final int B_RECENTS = 2;
    private static final int B_PREV = 3;
    private static final int B_PLAY = 4;
    private static final int B_NEXT = 5;
    private static final int B_VOL_UP = 6;
    private static final int B_MUTE = 7;
    private static final int B_VOL_DOWN = 8;
    private static final int B_NUMPAD = 9;
    private static final int B_KEYBOARD = 10;
    private static final int BUTTON_COUNT = 11;

    private final HidManager hid;
    private final PadController pad;
    private Listener listener;
    private int overlay = ControlPadView.OVERLAY_NONE;

    private final Paint cardPaint = Ui.fill(Ui.SURFACE);
    private final Paint keyPaint = Ui.fill(Ui.SURFACE_2);
    private final Paint pressPaint = Ui.fill(Ui.KEY_PRESSED);
    private final Paint iconPaint = Ui.fill(Ui.TEXT);
    private final Paint iconDimPaint = Ui.fill(Ui.TEXT_DIM);
    private final Paint hintPaint;
    private final Paint labelPaint;
    private final Paint accentPaint = Ui.fill(Ui.ACCENT);

    private final RectF padRect = new RectF();
    private final RectF[] buttons = new RectF[BUTTON_COUNT];
    private final Map<Integer, Integer> pointerButton = new HashMap<>();

    private final float margin;
    private final float gap;
    private final float cardRadius;
    private final float keyRadius;
    private boolean padActive;
    private boolean swapped;

    public TwoHandView(Context context, HidManager hid) {
        super(context);
        this.hid = hid;
        pad = new PadController(this, hid);
        for (int i = 0; i < buttons.length; i++) buttons[i] = new RectF();
        margin = Ui.dp(context, 10);
        gap = Ui.dp(context, 10);
        cardRadius = Ui.dp(context, 22);
        keyRadius = Ui.dp(context, 16);
        hintPaint = Ui.text(context, 13, Ui.TEXT_DIM);
        labelPaint = Ui.text(context, 16, Ui.TEXT);
    }

    public void setListener(Listener listener) {
        this.listener = listener;
    }

    public void setOverlay(int mode) {
        overlay = mode;
        invalidate();
    }

    /** true = button board on the left / top, touchpad on the right / bottom. */
    public void setSwapped(boolean swapped) {
        if (this.swapped == swapped) return;
        this.swapped = swapped;
        // onSizeChanged() does NOT run when only the internal arrangement changes, so
        // recompute the hit rects here — otherwise the buttons/touchpad keep the old spots.
        relayout();
        requestLayout();
        invalidate();
    }

    public boolean isSwapped() {
        return swapped;
    }

    @Override
    protected void onSizeChanged(int w, int h, int ow, int oh) {
        super.onSizeChanged(w, h, ow, oh);
        relayout();
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        super.onLayout(changed, left, top, right, bottom);
        relayout();
    }

    /** Recomputes the touchpad and button rects from the current view size and the swap flag. */
    private void relayout() {
        int w = getWidth();
        int h = getHeight();
        if (w <= 0 || h <= 0) return;
        float contentW = w - margin * 2f;
        float contentH = h - margin * 2f;
        if (contentW <= 0 || contentH <= 0) return;
        boolean land = w > h;
        float padW;
        float padH;
        if (land) {
            padW = contentW * 0.52f;
            padH = contentH;
            if (swapped) {
                padRect.set(margin + contentW - padW, margin, margin + contentW, margin + padH);
                layoutBoard(margin, margin, margin + contentW - padW - gap, margin + contentH, 4);
            } else {
                padRect.set(margin, margin, margin + padW, margin + padH);
                layoutBoard(margin + padW + gap, margin, margin + contentW, margin + contentH, 4);
            }
        } else {
            padW = contentW;
            padH = contentH * 0.52f;
            if (swapped) {
                padRect.set(margin, margin + contentH - padH, margin + padW, margin + contentH);
                layoutBoard(margin, margin, margin + contentW, margin + contentH - padH - gap, 4);
            } else {
                padRect.set(margin, margin, margin + padW, margin + padH);
                layoutBoard(margin, margin + padH + gap, margin + contentW, margin + contentH, 4);
            }
        }
    }

    private void layoutBoard(float left, float top, float right, float bottom, int rows) {
        float boardW = right - left;
        float boardH = bottom - top;
        if (boardW <= 0 || boardH <= 0) return;
        float rowH = (boardH - gap * (rows - 1)) / rows;
        // rows are laid out top -> bottom as arrays of buttons
        int[][] rowsDef = {
                {B_VOL_UP, B_VOL_DOWN, B_MUTE},
                {B_PREV, B_PLAY, B_NEXT},
                {B_BACK, B_HOME, B_RECENTS},
                {B_NUMPAD, B_KEYBOARD}
        };
        for (int row = 0; row < rowsDef.length; row++) {
            int[] def = rowsDef[row];
            float rowTop = top + row * (rowH + gap);
            float cellW = (boardW - gap * (def.length - 1)) / def.length;
            for (int col = 0; col < def.length; col++) {
                float cellLeft = left + col * (cellW + gap);
                buttons[def[col]].set(cellLeft, rowTop, cellLeft + cellW, rowTop + rowH);
            }
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        Paint icon = hid.isConnected() ? iconPaint : iconDimPaint;
        canvas.drawRoundRect(padRect, cardRadius, cardRadius, cardPaint);
        Ui.textCenter(canvas, "触控区", padRect.centerX(), padRect.centerY() - Ui.dp(getContext(), 14), hintPaint);
        Ui.textCenter(canvas, "单指移动 · 点击=左键 · 双指滚动", padRect.centerX(),
                padRect.centerY() + Ui.dp(getContext(), 14), hintPaint);

        for (int i = 0; i < buttons.length; i++) {
            RectF rect = buttons[i];
            if (rect.isEmpty()) continue;
            boolean down = pointerButton.containsValue(i);
            Paint fill = keyPaint;
            if (i == B_NUMPAD && overlay == ControlPadView.OVERLAY_NUMPAD) fill = pressPaint;
            else if (i == B_KEYBOARD && overlay == ControlPadView.OVERLAY_KEYBOARD) fill = pressPaint;
            else if (down) fill = pressPaint;
            canvas.drawRoundRect(rect, keyRadius, keyRadius, fill);
            switch (i) {
                case B_BACK:
                    Icons.draw(canvas, Icons.BACK, iconBox(rect), icon);
                    break;
                case B_HOME:
                    Icons.draw(canvas, Icons.HOME, iconBox(rect), icon);
                    break;
                case B_RECENTS:
                    Icons.draw(canvas, Icons.RECENTS, iconBox(rect), icon);
                    break;
                case B_PREV:
                    Icons.draw(canvas, Icons.PREV, iconBox(rect), icon);
                    break;
                case B_PLAY:
                    Icons.draw(canvas, Icons.PLAY_PAUSE, iconBox(rect), icon);
                    break;
                case B_NEXT:
                    Icons.draw(canvas, Icons.NEXT, iconBox(rect), icon);
                    break;
                case B_VOL_UP:
                    Icons.draw(canvas, Icons.VOL_UP, iconBox(rect), icon);
                    break;
                case B_MUTE:
                    Icons.draw(canvas, Icons.MUTE, iconBox(rect), icon);
                    break;
                case B_VOL_DOWN:
                    Icons.draw(canvas, Icons.VOL_DOWN, iconBox(rect), icon);
                    break;
                case B_NUMPAD:
                    Ui.textCenter(canvas, "数字", rect.centerX(), rect.centerY(), labelPaint);
                    break;
                case B_KEYBOARD:
                    Ui.textCenter(canvas, "键盘", rect.centerX(), rect.centerY(), labelPaint);
                    break;
                default:
                    break;
            }
        }
    }

    private RectF iconBox(RectF rect) {
        RectF box = new RectF(rect);
        box.inset(rect.width() * 0.30f, rect.height() * 0.24f);
        return box;
    }

    private int hitButton(float x, float y) {
        for (int i = 0; i < buttons.length; i++) {
            if (!buttons[i].isEmpty() && buttons[i].contains(x, y)) return i;
        }
        return -1;
    }

    private void perform(int index) {
        switch (index) {
            case B_BACK:
                hid.tapConsumer(HidConstants.CONSUMER_AC_BACK);
                break;
            case B_HOME:
                hid.tapConsumer(HidConstants.CONSUMER_AC_HOME);
                break;
            case B_RECENTS:
                hid.tapCombo(HidConstants.MOD_LEFT_GUI, HidConstants.KEY_TAB);
                break;
            case B_PREV:
                hid.tapConsumer(HidConstants.CONSUMER_PREV_TRACK);
                break;
            case B_PLAY:
                hid.tapConsumer(HidConstants.CONSUMER_PLAY_PAUSE);
                break;
            case B_NEXT:
                hid.tapConsumer(HidConstants.CONSUMER_NEXT_TRACK);
                break;
            case B_VOL_UP:
                hid.tapConsumer(HidConstants.CONSUMER_VOLUME_UP);
                break;
            case B_MUTE:
                hid.tapConsumer(HidConstants.CONSUMER_MUTE);
                break;
            case B_VOL_DOWN:
                hid.tapConsumer(HidConstants.CONSUMER_VOLUME_DOWN);
                break;
            case B_NUMPAD:
                if (listener != null) listener.onToggleNumpad();
                break;
            case B_KEYBOARD:
                if (listener != null) listener.onToggleKeyboard();
                break;
            default:
                break;
        }
    }

    private void haptic() {
        performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
    }

    private void clearPressed() {
        pointerButton.clear();
        invalidate();
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN: {
                float x = e.getX();
                float y = e.getY();
                int index = hitButton(x, y);
                if (index >= 0) {
                    pointerButton.put(e.getPointerId(0), index);
                    haptic();
                    perform(index);
                    invalidate();
                    return true;
                }
                if (padRect.contains(x, y)) {
                    padActive = true;
                    return pad.onTouch(e);
                }
                return true;
            }
            case MotionEvent.ACTION_POINTER_DOWN: {
                int idx = e.getActionIndex();
                int index = hitButton(e.getX(idx), e.getY(idx));
                if (index >= 0) {
                    pointerButton.put(e.getPointerId(idx), index);
                    haptic();
                    perform(index);
                    invalidate();
                    return true;
                }
                if (padActive) return pad.onTouch(e);
                return true;
            }
            case MotionEvent.ACTION_POINTER_UP: {
                int idx = e.getActionIndex();
                pointerButton.remove(e.getPointerId(idx));
                invalidate();
                if (padActive) return pad.onTouch(e);
                return true;
            }
            case MotionEvent.ACTION_MOVE: {
                if (padActive) return pad.onTouch(e);
                return true;
            }
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL: {
                if (padActive) {
                    boolean handled = pad.onTouch(e);
                    padActive = false;
                    clearPressed();
                    return handled;
                }
                clearPressed();
                return true;
            }
            default:
                return true;
        }
    }
}
