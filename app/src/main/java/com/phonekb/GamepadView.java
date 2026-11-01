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

/** Gamepad page: left D-pad card, right ABXY diamond with L1/R1 and select/start. */
public final class GamepadView extends View {
    private static final int KIND_DPAD = 0;
    private static final int KIND_FACE = 1;
    private static final int KIND_PILL = 2;

    private static final class Btn {
        final RectF rect = new RectF();
        final int bit;
        final int kind;
        final int labelColor;
        final String label;
        int fill;
        boolean pressed;

        Btn(int bit, int kind, String label, int fill, int labelColor) {
            this.bit = bit;
            this.kind = kind;
            this.label = label;
            this.fill = fill;
            this.labelColor = labelColor;
        }
    }

    private final HidManager hid;
    private final Paint cardPaint = Ui.fill(Ui.SURFACE);
    private final Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint iconPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint labelPaint;
    private final Paint smallPaint;

    private final Btn up = new Btn(HidConstants.GP_UP, KIND_DPAD, "", Ui.KEY, Ui.TEXT);
    private final Btn down = new Btn(HidConstants.GP_DOWN, KIND_DPAD, "", Ui.KEY, Ui.TEXT);
    private final Btn left = new Btn(HidConstants.GP_LEFT, KIND_DPAD, "", Ui.KEY, Ui.TEXT);
    private final Btn right = new Btn(HidConstants.GP_RIGHT, KIND_DPAD, "", Ui.KEY, Ui.TEXT);
    private final Btn l1 = new Btn(HidConstants.GP_L1, KIND_PILL, "L1", Ui.SURFACE_2, Ui.TEXT);
    private final Btn r1 = new Btn(HidConstants.GP_R1, KIND_PILL, "R1", Ui.SURFACE_2, Ui.TEXT);
    private final Btn x = new Btn(HidConstants.GP_X, KIND_FACE, "X", 0xFF22405F, Ui.TEXT);
    private final Btn y = new Btn(HidConstants.GP_Y, KIND_FACE, "Y", 0xFF5A4716, Ui.TEXT);
    private final Btn a = new Btn(HidConstants.GP_A, KIND_FACE, "A", 0xFF1F5136, Ui.TEXT);
    private final Btn b = new Btn(HidConstants.GP_B, KIND_FACE, "B", 0xFF5B262B, Ui.TEXT);
    private final Btn select = new Btn(HidConstants.GP_SELECT, KIND_PILL, "SELECT", Ui.SURFACE_2, Ui.TEXT);
    private final Btn start = new Btn(HidConstants.GP_START, KIND_PILL, "START", Ui.SURFACE_2, Ui.TEXT);

    private final Btn[] all = {up, down, left, right, l1, r1, x, y, a, b, select, start};
    private final Map<Integer, Btn> pointers = new HashMap<>();
    private int mask;
    private final RectF leftCard = new RectF();
    private final RectF rightCard = new RectF();

    public GamepadView(Context c, HidManager hid) {
        super(c);
        this.hid = hid;
        labelPaint = Ui.text(c, 20, Ui.TEXT);
        smallPaint = Ui.text(c, 12, Ui.TEXT);
        iconPaint.setStyle(Paint.Style.STROKE);
        iconPaint.setStrokeCap(Paint.Cap.ROUND);
        iconPaint.setStrokeJoin(Paint.Join.ROUND);
    }

    private void layout() {
        float pad = Ui.dp(getContext(), 10);
        float gap = Ui.dp(getContext(), 10);
        float w = getWidth();
        float h = getHeight();
        float leftW = (w - pad * 2 - gap) * 0.42f;
        leftCard.set(pad, pad, pad + leftW, h - pad);
        rightCard.set(leftCard.right + gap, pad, w - pad, h - pad);

        // D-pad cross inside the left card.
        float s = Math.min(leftCard.width() * 0.32f, leftCard.height() * 0.28f);
        float d = s * 1.08f;
        float cx = leftCard.centerX();
        float cy = leftCard.centerY();
        up.rect.set(cx - s / 2, cy - d - s / 2, cx + s / 2, cy - d + s / 2);
        down.rect.set(cx - s / 2, cy + d - s / 2, cx + s / 2, cy + d + s / 2);
        left.rect.set(cx - d - s / 2, cy - s / 2, cx - d + s / 2, cy + s / 2);
        right.rect.set(cx + d - s / 2, cy - s / 2, cx + d + s / 2, cy + s / 2);

        // Right card: shoulder row, ABXY diamond, select/start row.
        float innerPad = Ui.dp(getContext(), 12);
        float shoulderH = Ui.dp(getContext(), 30);
        float bottomH = Ui.dp(getContext(), 28);
        float rowGap = Ui.dp(getContext(), 8);
        float pillW = (rightCard.width() - innerPad * 2 - rowGap) / 2f;
        float x0 = rightCard.left + innerPad;
        float x1 = x0 + pillW;
        float x2 = x1 + rowGap;
        float top = rightCard.top + innerPad;
        l1.rect.set(x0, top, x1, top + shoulderH);
        r1.rect.set(x2, top, x2 + pillW, top + shoulderH);
        float bottomY = rightCard.bottom - innerPad - bottomH;
        select.rect.set(x0, bottomY, x1, bottomY + bottomH);
        start.rect.set(x2, bottomY, x2 + pillW, bottomY + bottomH);

        float regionTop = l1.rect.bottom + rowGap;
        float regionBottom = select.rect.top - rowGap;
        float faceCy = (regionTop + regionBottom) / 2f;
        float faceCx = rightCard.centerX();
        float radius = Math.min(Ui.dp(getContext(), 26),
                Math.min((regionBottom - regionTop) / 2.9f, rightCard.width() * 0.13f));
        float offset = radius * 1.5f;
        y.rect.set(faceCx - radius, faceCy - offset - radius, faceCx + radius, faceCy - offset + radius);
        x.rect.set(faceCx - offset - radius, faceCy - radius, faceCx - offset + radius, faceCy + radius);
        b.rect.set(faceCx + offset - radius, faceCy - radius, faceCx + offset + radius, faceCy + radius);
        a.rect.set(faceCx - radius, faceCy + offset - radius, faceCx + radius, faceCy + offset + radius);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        layout();
        float corner = Ui.dp(getContext(), 22);
        canvas.drawRoundRect(leftCard, corner, corner, cardPaint);
        canvas.drawRoundRect(rightCard, corner, corner, cardPaint);
        boolean connected = hid != null && hid.isConnected();
        for (Btn btn : all) drawButton(canvas, btn, connected);
    }

    private void drawButton(Canvas canvas, Btn btn, boolean connected) {
        float pressedRadius = Ui.dp(getContext(), btn.kind == KIND_DPAD ? 16 : 14);
        if (btn.kind == KIND_FACE) {
            fillPaint.setColor(btn.pressed ? blend(btn.fill, 0xFFFFFFFF, 0.22f) : btn.fill);
            canvas.drawCircle(btn.rect.centerX(), btn.rect.centerY(), btn.rect.width() / 2f, fillPaint);
            labelPaint.setColor(connected ? Ui.TEXT : Ui.TEXT_DIM);
            Ui.textCenter(canvas, btn.label, btn.rect.centerX(), btn.rect.centerY(), labelPaint);
            return;
        }
        fillPaint.setColor(btn.pressed ? Ui.KEY_PRESSED : btn.fill);
        canvas.drawRoundRect(btn.rect, pressedRadius, pressedRadius, fillPaint);
        if (btn.kind == KIND_DPAD) {
            drawArrow(canvas, btn, connected);
            return;
        }
        smallPaint.setColor(connected ? Ui.TEXT : Ui.TEXT_DIM);
        smallPaint.setTextAlign(Paint.Align.CENTER);
        Ui.textCenter(canvas, btn.label, btn.rect.centerX(), btn.rect.centerY(), smallPaint);
    }

    private void drawArrow(Canvas canvas, Btn btn, boolean connected) {
        RectF box = new RectF(btn.rect);
        box.inset(btn.rect.width() * 0.26f, btn.rect.height() * 0.26f);
        iconPaint.setColor(connected ? Ui.TEXT : Ui.TEXT_DIM);
        float stroke = Math.min(box.width(), box.height()) * 0.17f;
        iconPaint.setStrokeWidth(stroke);
        float cx = box.centerX();
        float cy = box.centerY();
        float half = Math.min(box.width(), box.height()) * 0.30f;
        android.graphics.Path path = new android.graphics.Path();
        if (btn == up || btn == down) {
            float dir = btn == up ? -1f : 1f;
            path.moveTo(cx - half, cy - dir * half * 0.5f);
            path.lineTo(cx, cy + dir * half * 0.9f);
            path.lineTo(cx + half, cy - dir * half * 0.5f);
        } else {
            float dir = btn == left ? -1f : 1f;
            path.moveTo(cx - dir * half * 0.5f, cy - half);
            path.lineTo(cx + dir * half * 0.9f, cy);
            path.lineTo(cx - dir * half * 0.5f, cy + half);
        }
        canvas.drawPath(path, iconPaint);
    }

    private static int blend(int base, int over, float f) {
        int br = (base >> 16) & 0xFF, bg = (base >> 8) & 0xFF, bb = base & 0xFF;
        int or = (over >> 16) & 0xFF, og = (over >> 8) & 0xFF, ob = over & 0xFF;
        int r = Math.round(br + (or - br) * f);
        int g = Math.round(bg + (og - bg) * f);
        int b = Math.round(bb + (ob - bb) * f);
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }

    private Btn hit(float x, float y) {
        for (Btn btn : all) {
            if (btn.rect.contains(x, y)) return btn;
        }
        return null;
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        int action = e.getActionMasked();
        if (action == MotionEvent.ACTION_DOWN || action == MotionEvent.ACTION_POINTER_DOWN) {
            int index = e.getActionIndex();
            Btn btn = hit(e.getX(index), e.getY(index));
            if (btn != null) {
                pointers.put(e.getPointerId(index), btn);
                if (!btn.pressed) {
                    btn.pressed = true;
                    mask |= btn.bit;
                    performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
                    invalidate();
                    send();
                }
            }
            return true;
        }
        if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_POINTER_UP) {
            Btn btn = pointers.remove(e.getPointerId(e.getActionIndex()));
            if (btn != null && countPointers(btn) == 0) {
                btn.pressed = false;
                mask &= ~btn.bit;
                invalidate();
                send();
            }
            return true;
        }
        if (action == MotionEvent.ACTION_CANCEL) {
            pointers.clear();
            mask = 0;
            for (Btn btn : all) btn.pressed = false;
            invalidate();
            send();
            return true;
        }
        return true;
    }

    private int countPointers(Btn btn) {
        int n = 0;
        for (Btn b : pointers.values()) {
            if (b == btn) n++;
        }
        return n;
    }

    private void send() {
        if (hid != null) hid.sendGamepad(mask);
    }
}
