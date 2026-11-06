package com.phonekb.pad;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.os.Handler;
import android.os.Looper;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;

import java.util.ArrayList;

/** iPad 用键盘页：数字行 + qwerty + 方向键 + Cmd 快捷键。 */
public class PadKeyboardView extends View {

    private static final int MOD_SHIFT = 0x02;
    private static final int MOD_GUI = 0x08;

    private static final int KIND_KEY = 0;
    private static final int KIND_SHORTCUT = 1;
    private static final int KIND_CMD = 2;
    private static final int KIND_SHIFT = 3;
    private static final int KIND_BACKSPACE = 4;

    private static final class Key {
        final String label;
        final int usage;
        final int mods;
        final int kind;
        final float weight;
        final RectF rect = new RectF();

        Key(String label, int usage, int mods, int kind, float weight) {
            this.label = label;
            this.usage = usage;
            this.mods = mods;
            this.kind = kind;
            this.weight = weight;
        }
    }

    private final ArrayList<Key[]> rows = new ArrayList<>();
    private final Paint keyPaint = PadStyle.fill(PadStyle.SURFACE);
    private final Paint keyAltPaint = PadStyle.fill(PadStyle.SURFACE_2);
    private final Paint accentPaint = PadStyle.fill(PadStyle.ACCENT);
    private final Paint labelPaint = PadStyle.label(13f, PadStyle.TEXT);
    private final Paint smallPaint = PadStyle.label(10.5f, PadStyle.TEXT);
    private final Paint shortcutPaint = PadStyle.label(11f, PadStyle.TEXT_DIM);
    private final Handler handler = new Handler(Looper.getMainLooper());

    private boolean cmdArmed;
    private boolean shiftArmed;
    private int pressedRow = -1;
    private int pressedCol = -1;
    private Runnable backspaceRepeat;

    public PadKeyboardView(Context context) {
        super(context);
        setBackgroundColor(PadStyle.BG);
        rows.add(new Key[]{
                new Key("Cmd+C", 0x06, MOD_GUI, KIND_SHORTCUT, 1f),
                new Key("Cmd+V", 0x19, MOD_GUI, KIND_SHORTCUT, 1f),
                new Key("Cmd+X", 0x1B, MOD_GUI, KIND_SHORTCUT, 1f),
                new Key("Cmd+A", 0x04, MOD_GUI, KIND_SHORTCUT, 1f),
                new Key("Cmd+Z", 0x1D, MOD_GUI, KIND_SHORTCUT, 1f)
        });
        rows.add(new Key[]{
                new Key("Cmd+Tab", 0x2B, MOD_GUI, KIND_SHORTCUT, 1.25f),
                new Key("Cmd+H", 0x0B, MOD_GUI, KIND_SHORTCUT, 1f),
                new Key("Cmd+空格", 0x2C, MOD_GUI, KIND_SHORTCUT, 1.35f),
                new Key("Cmd+Shift+3", 0x20, MOD_GUI | MOD_SHIFT, KIND_SHORTCUT, 1.5f)
        });
        rows.add(new Key[]{
                new Key("1", 0x1E, 0, KIND_KEY, 1f),
                new Key("2", 0x1F, 0, KIND_KEY, 1f),
                new Key("3", 0x20, 0, KIND_KEY, 1f),
                new Key("4", 0x21, 0, KIND_KEY, 1f),
                new Key("5", 0x22, 0, KIND_KEY, 1f),
                new Key("6", 0x23, 0, KIND_KEY, 1f),
                new Key("7", 0x24, 0, KIND_KEY, 1f),
                new Key("8", 0x25, 0, KIND_KEY, 1f),
                new Key("9", 0x26, 0, KIND_KEY, 1f),
                new Key("0", 0x27, 0, KIND_KEY, 1f),
                new Key("-", 0x2D, 0, KIND_KEY, 1f)
        });
        rows.add(new Key[]{
                new Key("q", 0x14, 0, KIND_KEY, 1f),
                new Key("w", 0x1A, 0, KIND_KEY, 1f),
                new Key("e", 0x08, 0, KIND_KEY, 1f),
                new Key("r", 0x15, 0, KIND_KEY, 1f),
                new Key("t", 0x17, 0, KIND_KEY, 1f),
                new Key("y", 0x1C, 0, KIND_KEY, 1f),
                new Key("u", 0x18, 0, KIND_KEY, 1f),
                new Key("i", 0x0C, 0, KIND_KEY, 1f),
                new Key("o", 0x12, 0, KIND_KEY, 1f),
                new Key("p", 0x13, 0, KIND_KEY, 1f),
                new Key("Del", 0x2A, 0, KIND_BACKSPACE, 1.2f)
        });
        rows.add(new Key[]{
                new Key("a", 0x04, 0, KIND_KEY, 1f),
                new Key("s", 0x16, 0, KIND_KEY, 1f),
                new Key("d", 0x07, 0, KIND_KEY, 1f),
                new Key("f", 0x09, 0, KIND_KEY, 1f),
                new Key("g", 0x0A, 0, KIND_KEY, 1f),
                new Key("h", 0x0B, 0, KIND_KEY, 1f),
                new Key("j", 0x0D, 0, KIND_KEY, 1f),
                new Key("k", 0x0E, 0, KIND_KEY, 1f),
                new Key("l", 0x0F, 0, KIND_KEY, 1f),
                new Key(";", 0x33, 0, KIND_KEY, 1f),
                new Key("'", 0x34, 0, KIND_KEY, 1f),
                new Key("Enter", 0x28, 0, KIND_KEY, 1.6f)
        });
        rows.add(new Key[]{
                new Key("Shift", 0, MOD_SHIFT, KIND_SHIFT, 1.5f),
                new Key("z", 0x1D, 0, KIND_KEY, 1f),
                new Key("x", 0x1B, 0, KIND_KEY, 1f),
                new Key("c", 0x06, 0, KIND_KEY, 1f),
                new Key("v", 0x19, 0, KIND_KEY, 1f),
                new Key("b", 0x05, 0, KIND_KEY, 1f),
                new Key("n", 0x11, 0, KIND_KEY, 1f),
                new Key("m", 0x10, 0, KIND_KEY, 1f),
                new Key(",", 0x36, 0, KIND_KEY, 1f),
                new Key(".", 0x37, 0, KIND_KEY, 1f),
                new Key("/", 0x38, 0, KIND_KEY, 1f)
        });
        rows.add(new Key[]{
                new Key("Esc", 0x29, 0, KIND_KEY, 1f),
                new Key("Tab", 0x2B, 0, KIND_KEY, 1f),
                new Key("Cmd", 0, MOD_GUI, KIND_CMD, 1f),
                new Key("Space", 0x2C, 0, KIND_KEY, 4f),
                new Key("Left", 0x50, 0, KIND_KEY, 1.1f),
                new Key("Up", 0x52, 0, KIND_KEY, 1f),
                new Key("Down", 0x51, 0, KIND_KEY, 1f),
                new Key("Right", 0x4F, 0, KIND_KEY, 1.1f)
        });
    }

    private HidManager hid() {
        return HidManager.get(getContext());
    }

    private void layoutKeys(int width, int height) {
        float margin = PadStyle.dp(7f);
        float gap = PadStyle.dp(6f);
        int count = rows.size();
        float rowHeight = (height - margin * 2f - gap * (count - 1)) / count;
        for (int r = 0; r < count; r++) {
            Key[] keys = rows.get(r);
            float total = 0f;
            for (Key k : keys) total += k.weight;
            float usable = width - margin * 2f - gap * (keys.length - 1);
            float unit = usable / total;
            float x = margin;
            float top = margin + r * (rowHeight + gap);
            for (Key k : keys) {
                float w = unit * k.weight;
                k.rect.set(x, top, x + w, top + rowHeight);
                x += w + gap;
            }
        }
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        layoutKeys(w, h);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float radius = PadStyle.dp(9f);
        for (int r = 0; r < rows.size(); r++) {
            Key[] keys = rows.get(r);
            for (int c = 0; c < keys.length; c++) {
                Key k = keys[c];
                boolean pressed = r == pressedRow && c == pressedCol;
                boolean armed = (k.kind == KIND_CMD && cmdArmed) || (k.kind == KIND_SHIFT && shiftArmed);
                Paint bg = pressed || armed ? accentPaint : (k.kind == KIND_SHORTCUT ? keyAltPaint : keyPaint);
                PadStyle.round(canvas, k.rect, radius, bg);
                Paint text = k.kind == KIND_SHORTCUT ? shortcutPaint : labelPaint;
                PadStyle.center(canvas, k.label, k.rect.centerX(), k.rect.centerY(), text);
            }
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        int action = e.getActionMasked();
        if (action == MotionEvent.ACTION_DOWN) {
            int[] hit = hitTest(e.getX(), e.getY());
            if (hit == null) return false;
            pressedRow = hit[0];
            pressedCol = hit[1];
            Key k = rows.get(pressedRow)[pressedCol];
            performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
            if (k.kind == KIND_CMD) {
                cmdArmed = !cmdArmed;
                invalidate();
                return true;
            }
            if (k.kind == KIND_SHIFT) {
                shiftArmed = !shiftArmed;
                invalidate();
                return true;
            }
            send(k);
            if (k.kind == KIND_BACKSPACE) startBackspaceRepeat();
            invalidate();
            return true;
        }
        if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
            stopBackspaceRepeat();
            pressedRow = -1;
            pressedCol = -1;
            invalidate();
            return true;
        }
        return true;
    }

    private int[] hitTest(float x, float y) {
        for (int r = 0; r < rows.size(); r++) {
            Key[] keys = rows.get(r);
            for (int c = 0; c < keys.length; c++) {
                if (keys[c].rect.contains(x, y)) return new int[]{r, c};
            }
        }
        return null;
    }

    private void send(Key k) {
        int mods = k.mods | (cmdArmed ? MOD_GUI : 0) | (shiftArmed ? MOD_SHIFT : 0);
        try {
            hid().tapKey(mods, k.usage);
        } catch (Throwable ignored) {
        }
        cmdArmed = false;
        shiftArmed = false;
    }

    private void startBackspaceRepeat() {
        stopBackspaceRepeat();
        backspaceRepeat = new Runnable() {
            @Override
            public void run() {
                try {
                    hid().tapKey(0, 0x2A);
                } catch (Throwable ignored) {
                }
                handler.postDelayed(this, 90L);
            }
        };
        handler.postDelayed(backspaceRepeat, 400L);
    }

    private void stopBackspaceRepeat() {
        if (backspaceRepeat != null) {
            handler.removeCallbacks(backspaceRepeat);
            backspaceRepeat = null;
        }
    }
}
