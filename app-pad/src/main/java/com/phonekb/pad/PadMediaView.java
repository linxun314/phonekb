package com.phonekb.pad;

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

/** iPad 用媒体页：上一段/播放/下一段 + −10s/＋10s + 音量/静音 + 主屏/切App。 */
public class PadMediaView extends View {

    private static final int A_PREV = 0;
    private static final int A_PLAY = 1;
    private static final int A_NEXT = 2;
    private static final int A_BACK = 3;
    private static final int A_FWD = 4;
    private static final int A_VOL_UP = 5;
    private static final int A_VOL_DOWN = 6;
    private static final int A_MUTE = 7;
    private static final int A_HOME = 8;
    private static final int A_SWITCH = 9;
    private static final int COUNT = 10;

    private static final int CONSUMER_VOL_UP = 0x00E9;
    private static final int CONSUMER_VOL_DOWN = 0x00EA;
    private static final int CONSUMER_MUTE = 0x00E2;

    private final RectF barCard = new RectF();
    private final RectF skipCard = new RectF();
    private final RectF keyCard = new RectF();
    private final RectF[] cells = new RectF[COUNT];

    private final Paint cardPaint = PadStyle.fill(PadStyle.SURFACE);
    private final Paint cellPaint = PadStyle.fill(PadStyle.SURFACE_2);
    private final Paint accentPaint = PadStyle.fill(PadStyle.ACCENT);
    private final Paint iconPaint = PadStyle.fill(PadStyle.TEXT);
    private final Paint labelPaint = PadStyle.label(11f, PadStyle.TEXT_DIM);
    private final Paint bigLabelPaint = PadStyle.label(16f, PadStyle.TEXT);

    private final Handler handler = new Handler(Looper.getMainLooper());

    private int pressed = -1;
    private int repeatIndex = -1;
    private Runnable repeat;

    public PadMediaView(Context context) {
        super(context);
        setBackgroundColor(PadStyle.BG);
        for (int i = 0; i < COUNT; i++) cells[i] = new RectF();
    }

    private HidManager hid() {
        return HidManager.get(getContext());
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        float m = PadStyle.dp(12f);
        float gap = PadStyle.dp(10f);
        float inner = PadStyle.dp(12f);
        float left = m;
        float right = w - m;

        float barH = PadStyle.dp(72f);
        float skipH = PadStyle.dp(64f);
        float keyH = PadStyle.dp(96f);
        float total = barH + skipH + keyH + gap * 2f;
        float top = Math.max(m, (h - total) / 2f);

        barCard.set(left, top, right, top + barH);
        skipCard.set(left, barCard.bottom + gap, right, barCard.bottom + gap + skipH);
        keyCard.set(left, skipCard.bottom + gap, right, skipCard.bottom + gap + keyH);

        float bw = (barCard.width() - inner * 2f) / 3f;
        for (int i = 0; i < 3; i++) {
            cells[i].set(barCard.left + inner + bw * i, barCard.top + inner,
                    barCard.left + inner + bw * (i + 1) - PadStyle.dp(6f), barCard.bottom - inner);
        }
        float sw = (skipCard.width() - inner * 2f - PadStyle.dp(8f)) / 2f;
        cells[A_BACK].set(skipCard.left + inner, skipCard.top + inner,
                skipCard.left + inner + sw, skipCard.bottom - inner);
        cells[A_FWD].set(skipCard.right - inner - sw, skipCard.top + inner,
                skipCard.right - inner, skipCard.bottom - inner);

        int round = 5;
        float kw = (keyCard.width() - inner * 2f - PadStyle.dp(8f) * (round - 1)) / round;
        float ky = keyCard.top + inner;
        float kh = keyCard.bottom - inner - ky;
        for (int i = 0; i < round; i++) {
            float x = keyCard.left + inner + (kw + PadStyle.dp(8f)) * i;
            cells[A_VOL_UP + i].set(x, ky, x + kw, ky + kh);
        }
        cells[A_SWITCH].set(cells[A_MUTE]);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        PadStyle.round(canvas, barCard, PadStyle.dp(16f), cardPaint);
        PadStyle.round(canvas, skipCard, PadStyle.dp(16f), cardPaint);
        PadStyle.round(canvas, keyCard, PadStyle.dp(16f), cardPaint);
        drawIcon(canvas, A_PREV);
        drawIcon(canvas, A_PLAY);
        drawIcon(canvas, A_NEXT);
        drawSkip(canvas, A_BACK, "−10s");
        drawSkip(canvas, A_FWD, "＋10s");
        drawRound(canvas, A_VOL_UP, "音量+");
        drawRound(canvas, A_VOL_DOWN, "音量−");
        drawRound(canvas, A_MUTE, "静音");
        drawRound(canvas, A_HOME, "主屏");
        drawRound(canvas, A_SWITCH, "切App");
    }

    private void drawIcon(Canvas canvas, int index) {
        RectF r = cells[index];
        Paint p = pressed == index ? accentPaint : cellPaint;
        PadStyle.round(canvas, r, PadStyle.dp(12f), p);
        float cx = r.centerX();
        float cy = r.centerY();
        float s = PadStyle.dp(14f);
        if (index == A_PLAY) {
            Path path = new Path();
            path.moveTo(cx - s * 0.5f, cy - s);
            path.lineTo(cx + s * 0.9f, cy);
            path.lineTo(cx - s * 0.5f, cy + s);
            path.close();
            canvas.drawPath(path, iconPaint);
        } else {
            float dir = index == A_PREV ? -1f : 1f;
            float barX = cx - dir * s * 1.15f;
            canvas.drawRect(barX - PadStyle.dp(1.5f), cy - s, barX + PadStyle.dp(1.5f), cy + s, iconPaint);
            Path path = new Path();
            path.moveTo(cx + dir * s, cy - s);
            path.lineTo(cx - dir * s * 0.2f, cy);
            path.lineTo(cx + dir * s, cy + s);
            path.close();
            canvas.drawPath(path, iconPaint);
        }
    }

    private void drawSkip(Canvas canvas, int index, String text) {
        RectF r = cells[index];
        Paint p = pressed == index ? accentPaint : cellPaint;
        PadStyle.round(canvas, r, PadStyle.dp(12f), p);
        float cy = r.centerY();
        float s = PadStyle.dp(11f);
        float cx = r.centerX() - PadStyle.dp(16f);
        float dir = index == A_BACK ? -1f : 1f;
        Path path = new Path();
        path.moveTo(cx + dir * -s, cy - s);
        path.lineTo(cx + dir * s, cy);
        path.lineTo(cx + dir * -s, cy + s);
        path.close();
        canvas.drawPath(path, iconPaint);
        PadStyle.center(canvas, text, cx + dir * PadStyle.dp(16f) + PadStyle.dp(22f), cy, bigLabelPaint);
    }

    private void drawRound(Canvas canvas, int index, String text) {
        RectF r = cells[index];
        float radius = Math.min(r.width(), r.height()) / 2f;
        Paint p = pressed == index ? accentPaint : cellPaint;
        canvas.drawCircle(r.centerX(), r.centerY(), radius - PadStyle.dp(2f), p);
        PadStyle.center(canvas, text, r.centerX(), r.centerY(), bigLabelPaint);
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        int action = e.getActionMasked();
        if (action == MotionEvent.ACTION_DOWN) {
            int index = hitTest(e.getX(), e.getY());
            if (index < 0) return false;
            pressed = index;
            performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
            perform(index);
            if (index == A_BACK || index == A_FWD) startRepeat(index);
            invalidate();
            return true;
        }
        if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
            stopRepeat();
            if (pressed >= 0) {
                pressed = -1;
                invalidate();
            }
            return true;
        }
        return true;
    }

    private void startRepeat(int index) {
        stopRepeat();
        repeatIndex = index;
        repeat = new Runnable() {
            @Override
            public void run() {
                if (repeatIndex < 0) return;
                perform(repeatIndex);
                handler.postDelayed(this, 120L);
            }
        };
        handler.postDelayed(repeat, 380L);
    }

    private void stopRepeat() {
        repeatIndex = -1;
        if (repeat != null) {
            handler.removeCallbacks(repeat);
            repeat = null;
        }
    }

    private void perform(int index) {
        try {
            HidManager h = hid();
            switch (index) {
                case A_PREV:
                    h.tapConsumer(HidConstants.CONSUMER_PREV_TRACK);
                    break;
                case A_PLAY:
                    h.tapConsumer(HidConstants.CONSUMER_PLAY_PAUSE);
                    break;
                case A_NEXT:
                    h.tapConsumer(HidConstants.CONSUMER_NEXT_TRACK);
                    break;
                case A_BACK:
                    h.tapKey(0, 0x50);
                    break;
                case A_FWD:
                    h.tapKey(0, 0x4F);
                    break;
                case A_VOL_UP:
                    h.tapConsumer(CONSUMER_VOL_UP);
                    break;
                case A_VOL_DOWN:
                    h.tapConsumer(CONSUMER_VOL_DOWN);
                    break;
                case A_MUTE:
                    h.tapConsumer(CONSUMER_MUTE);
                    break;
                case A_HOME:
                    h.tapKey(0x08, 0x0B);
                    break;
                case A_SWITCH:
                    h.tapKey(0x08, 0x2B);
                    break;
                default:
                    break;
            }
        } catch (Throwable ignored) {
        }
    }

    private int hitTest(float x, float y) {
        for (int i = 0; i < COUNT; i++) {
            if (cells[i].contains(x, y)) return i;
        }
        return -1;
    }
}
