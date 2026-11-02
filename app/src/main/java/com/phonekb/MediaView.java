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
 * Media page tuned for one-handed / landscape use: a large media bar
 * (previous / play-pause / next) on top of two rows of four big keys
 * (volume up/down, mute, closed captions, then back/home/menu/power).
 *
 * The digit pad from the earlier Remote-style layout was dropped: the digits
 * already exist on the keyboard page, so the space now goes into bigger keys.
 */
public class MediaView extends View {

    private static final int TYPE_CONSUMER = 1;

    private static final class Key {
        final RectF rect = new RectF();
        final int icon;
        final String label;
        final int code;

        Key(int icon, String label, int code) {
            this.icon = icon;
            this.label = label;
            this.code = code;
        }
    }

    private final HidManager hid;
    private final Paint cardPaint = Ui.fill(Ui.SURFACE);
    private final Paint keyPaint = Ui.fill(Ui.SURFACE_2);
    private final Paint pressPaint = Ui.fill(Ui.KEY_PRESSED);
    private final Paint iconPaint = Ui.fill(Ui.TEXT);
    private final Paint iconDimPaint = Ui.fill(Ui.TEXT_DIM);
    private final Paint barPaint = Ui.fill(Ui.SURFACE_2);
    private final Paint labelPaint;
    private final Paint labelDimPaint;

    private final RectF mediaCard = new RectF();
    private final RectF keypadCard = new RectF();
    private final RectF[] barAreas = {new RectF(), new RectF(), new RectF()};
    private static final int[] BAR_ICONS = {Icons.PREV, Icons.PLAY_PAUSE, Icons.NEXT};
    private static final int[] BAR_USAGES = {
            HidConstants.CONSUMER_PREV_TRACK,
            HidConstants.CONSUMER_PLAY_PAUSE,
            HidConstants.CONSUMER_NEXT_TRACK};

    private final Key[] keys;
    private final Map<Integer, Key> pressed = new HashMap<>();

    private final float corner;
    private final float gap;
    private final float pad;
    private int pressedBar = -1;

    private final RectF[] brightAreas = {new RectF(), new RectF()};
    private static final int BRIGHT_DOWN = 0;
    private static final int BRIGHT_UP = 1;
    private static final int[] BRIGHT_USAGES = {
            HidConstants.CONSUMER_BRIGHTNESS_DECREMENT,
            HidConstants.CONSUMER_BRIGHTNESS_INCREMENT};
    private final Paint sunPaint;
    private final float brightRowHeight;
    private boolean landscapeLayout;
    private int pressedBright = -1;

    private final RectF toolpadRect = new RectF();
    private final Paint toolpadOutline;
    private final Paint toolpadText;
    private final float toolpadHeight;
    private boolean toolpadRow;
    private boolean pressedToolpad;
    private Runnable touchpadAction;

    /** HID brightness usages are ignored by some OEM ROMs; the activity also drives the WiFi companion. */
    public interface BrightnessListener {
        void onBrightness(boolean up);
    }

    private BrightnessListener brightnessListener;

    public MediaView(Context context, HidManager hid) {
        super(context);
        this.hid = hid;
        corner = Ui.dp(context, 24);
        gap = Ui.dp(context, 8);
        pad = Ui.dp(context, 10);
        labelPaint = Ui.text(context, 24, Ui.TEXT);
        labelDimPaint = Ui.text(context, 24, Ui.TEXT_DIM);
        toolpadHeight = Ui.dp(context, 44);
        brightRowHeight = Ui.dp(context, 40);
        sunPaint = Ui.fill(Ui.TEXT);
        toolpadOutline = new Paint(Paint.ANTI_ALIAS_FLAG);
        toolpadOutline.setStyle(Paint.Style.STROKE);
        toolpadOutline.setColor(Ui.ACCENT);
        toolpadOutline.setStrokeWidth(Ui.dp(context, 1.5f));
        toolpadText = Ui.text(context, 15, Ui.ACCENT);
        toolpadText.setTextAlign(Paint.Align.CENTER);

        keys = new Key[]{
                new Key(Icons.VOL_UP, null, HidConstants.CONSUMER_VOLUME_UP),
                new Key(Icons.VOL_DOWN, null, HidConstants.CONSUMER_VOLUME_DOWN),
                new Key(Icons.MUTE, null, HidConstants.CONSUMER_MUTE),
                new Key(0, "CC", HidConstants.CONSUMER_CLOSED_CAPTION),

                new Key(Icons.BACK, null, HidConstants.CONSUMER_AC_BACK),
                new Key(Icons.HOME, null, HidConstants.CONSUMER_AC_HOME),
                new Key(Icons.MENU, null, HidConstants.CONSUMER_MENU),
                new Key(Icons.POWER, null, HidConstants.CONSUMER_POWER)};
    }

    /** Portrait media page: a full-width 触控板 button above the media controls. */
    public void setToolpadRow(boolean show) {
        toolpadRow = show;
        invalidate();
    }

    public void setTouchpadAction(Runnable action) {
        touchpadAction = action;
    }

    public void setBrightnessListener(BrightnessListener l) {
        brightnessListener = l;
    }

    @Override
    protected void onSizeChanged(int w, int h, int ow, int oh) {
        super.onSizeChanged(w, h, ow, oh);
        float left = pad;
        float right = w - pad;
        float top = pad;
        float bottom = h - pad;
        if (right <= left || bottom <= top) return;

        float contentTop = top;
        if (toolpadRow) {
            toolpadRect.set(left, top, right, top + toolpadHeight);
            contentTop = toolpadRect.bottom + gap;
        }

        float mediaH = Math.max(Ui.dp(getContext(), 72), (bottom - contentTop) * 0.26f);
        mediaH = Math.min(mediaH, Ui.dp(getContext(), 140));

        // Brightness keys: landscape puts them left/right of the media bar (no extra row),
        // portrait puts them on their own row under the media bar.
        landscapeLayout = (right - left) > (bottom - top) * 1.2f;
        float keysTop;
        if (landscapeLayout) {
            float bw = Math.min(Math.max(Ui.dp(getContext(), 88), (right - left) * 0.105f), (right - left) * 0.16f);
            brightAreas[0].set(left, contentTop, left + bw, contentTop + mediaH);
            brightAreas[1].set(right - bw, contentTop, right, contentTop + mediaH);
            mediaCard.set(left + bw + gap, contentTop, right - bw - gap, contentTop + mediaH);
            keysTop = mediaCard.bottom;
        } else {
            mediaCard.set(left, contentTop, right, contentTop + mediaH);
            float rowTop = mediaCard.bottom + gap;
            float half = (right - left - gap) / 2f;
            brightAreas[0].set(left, rowTop, left + half, rowTop + brightRowHeight);
            brightAreas[1].set(left + half + gap, rowTop, right, rowTop + brightRowHeight);
            keysTop = brightAreas[0].bottom;
        }

        float keypadTop = keysTop + gap;
        keypadCard.set(left, keypadTop, right, bottom);

        // media bar: three touch areas with 24 / 40 / 24 weights inside an inset card
        float inset = Ui.dp(getContext(), 10);
        float innerLeft = mediaCard.left + inset;
        float innerRight = mediaCard.right - inset;
        float innerTop = mediaCard.top + inset;
        float innerBottom = mediaCard.bottom - inset;
        float unit = (innerRight - innerLeft) / 88f;
        barAreas[0].set(innerLeft, innerTop, innerLeft + unit * 24f, innerBottom);
        barAreas[1].set(barAreas[0].right, innerTop, barAreas[0].right + unit * 40f, innerBottom);
        barAreas[2].set(barAreas[1].right, innerTop, barAreas[1].right + unit * 24f, innerBottom);

        // 2 rows x 4 large keys, centred in the keypad card
        float gridPad = Ui.dp(getContext(), 8);
        float gridLeft = keypadCard.left + gridPad;
        float gridRight = keypadCard.right - gridPad;
        float gridTop = keypadCard.top + gridPad;
        float gridBottom = keypadCard.bottom - gridPad;
        float cellW = (gridRight - gridLeft) / 4f;
        float cellH = (gridBottom - gridTop) / 2f;
        for (int i = 0; i < keys.length; i++) {
            int row = i / 4;
            int col = i % 4;
            float l = gridLeft + col * cellW;
            float t = gridTop + row * cellH;
            keys[i].rect.set(l, t, l + cellW, t + cellH);
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        canvas.drawColor(Ui.BG);
        if (toolpadRow) {
            float radius = Ui.dp(getContext(), 12);
            canvas.drawRoundRect(toolpadRect, radius, radius, pressedToolpad ? pressPaint : keyPaint);
            canvas.drawRoundRect(toolpadRect, radius, radius, toolpadOutline);
            toolpadText.setColor(hid.isConnected() ? Ui.ACCENT : Ui.TEXT_DIM);
            Paint.FontMetrics fm = toolpadText.getFontMetrics();
            canvas.drawText("触控板", toolpadRect.centerX(),
                    toolpadRect.centerY() - (fm.ascent + fm.descent) / 2f, toolpadText);
        }
        canvas.drawRoundRect(mediaCard, corner, corner, cardPaint);
        canvas.drawRoundRect(keypadCard, corner, corner, cardPaint);

        Paint icon = hid.isConnected() ? iconPaint : iconDimPaint;
        for (int i = 0; i < 3; i++) {
            RectF area = barAreas[i];
            if (i == pressedBar) {
                RectF hit = new RectF(area);
                hit.inset(Ui.dp(getContext(), 2), Ui.dp(getContext(), 2));
                canvas.drawRoundRect(hit, Ui.dp(getContext(), 16), Ui.dp(getContext(), 16), barPaint);
            }
            RectF box = new RectF(area);
            box.inset(area.width() * 0.3f, area.height() * 0.24f);
            Icons.draw(canvas, BAR_ICONS[i], box, icon);
        }

        drawBrightness(canvas, brightAreas[BRIGHT_DOWN], false, pressedBright == BRIGHT_DOWN);
        drawBrightness(canvas, brightAreas[BRIGHT_UP], true, pressedBright == BRIGHT_UP);

        for (Key key : keys) {
            float radius = Math.min(key.rect.width(), key.rect.height()) * 0.46f;
            float cx = key.rect.centerX();
            float cy = key.rect.centerY();
            canvas.drawCircle(cx, cy, radius, pressed.containsValue(key) ? pressPaint : keyPaint);
            if (key.icon != 0) {
                RectF box = new RectF(cx - radius, cy - radius, cx + radius, cy + radius);
                box.inset(radius * 0.40f, radius * 0.40f);
                Icons.draw(canvas, key.icon, box, icon);
            } else {
                float textSize = radius * 0.9f;
                Paint text = hid.isConnected() ? labelPaint : labelDimPaint;
                text.setTextSize(textSize);
                canvas.drawText(key.label, cx, cy - (text.ascent() + text.descent()) / 2f, text);
            }
        }
    }

    private void drawBrightness(Canvas canvas, RectF area, boolean up, boolean pressedState) {
        if (area.width() <= 0f || area.height() <= 0f) return;
        float h = area.height();
        float radius = Math.min(Ui.dp(getContext(), 16), h / 2f);
        canvas.drawRoundRect(area, radius, radius, pressedState ? pressPaint : keyPaint);
        Paint icon = hid.isConnected() ? iconPaint : iconDimPaint;
        boolean stacked = area.width() < Ui.dp(getContext(), 150);
        float sunR = stacked ? h * 0.15f : h * 0.20f;
        float sunX;
        float sunY;
        float textX;
        float textY;
        float textSize;
        if (stacked) {
            sunX = area.centerX();
            sunY = area.top + h * 0.34f;
            textX = area.centerX();
            textY = area.top + h * 0.76f;
            textSize = h * 0.24f;
        } else {
            sunX = area.left + h * 0.55f;
            sunY = area.centerY();
            textX = area.centerX() + h * 0.30f;
            textY = area.centerY();
            textSize = h * 0.32f;
        }
        drawSun(canvas, sunX, sunY, sunR, icon);
        Paint text = hid.isConnected() ? labelPaint : labelDimPaint;
        text.setTextSize(textSize);
        canvas.drawText(up ? "亮度 ＋" : "亮度 −", textX,
                textY - (text.ascent() + text.descent()) / 2f, text);
    }

    private void drawSun(Canvas canvas, float cx, float cy, float r, Paint paint) {
        float stroke = Math.max(Ui.dp(getContext(), 1.2f), r * 0.20f);
        Paint ray = new Paint(paint);
        ray.setStyle(Paint.Style.STROKE);
        ray.setStrokeWidth(stroke);
        ray.setStrokeCap(Paint.Cap.ROUND);
        canvas.drawCircle(cx, cy, r * 0.62f, ray);
        float outer = r * 1.28f;
        for (int i = 0; i < 8; i++) {
            double angle = Math.PI * i / 4.0;
            float dx = (float) Math.cos(angle);
            float dy = (float) Math.sin(angle);
            canvas.drawLine(cx + dx * r * 0.92f, cy + dy * r * 0.92f,
                    cx + dx * outer, cy + dy * outer, ray);
        }
    }

    private Key find(float x, float y) {
        for (Key key : keys) {
            if (key.rect.contains(x, y)) return key;
        }
        return null;
    }

    private void activate(Key key) {
        hid.tapConsumer(key.code);
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN: {
                float x = e.getX();
                float y = e.getY();
                if (toolpadRow && toolpadRect.contains(x, y)) {
                    pressedToolpad = true;
                    performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
                    if (touchpadAction != null) touchpadAction.run();
                    invalidate();
                    return true;
                }
                for (int i = 0; i < 2; i++) {
                    if (brightAreas[i].contains(x, y)) {
                        pressedBright = i;
                        performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
                        hid.tapConsumer(BRIGHT_USAGES[i]);
                        if (brightnessListener != null) {
                            brightnessListener.onBrightness(i == BRIGHT_UP);
                        }
                        invalidate();
                        return true;
                    }
                }
                for (int i = 0; i < 3; i++) {
                    if (barAreas[i].contains(x, y)) {
                        pressedBar = i;
                        performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
                        hid.tapConsumer(BAR_USAGES[i]);
                        invalidate();
                        return true;
                    }
                }
                Key key = find(x, y);
                if (key == null) return true;
                pressed.put(e.getPointerId(0), key);
                performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
                activate(key);
                invalidate();
                return true;
            }
            case MotionEvent.ACTION_POINTER_DOWN: {
                int index = e.getActionIndex();
                Key key = find(e.getX(index), e.getY(index));
                if (key != null) {
                    pressed.put(e.getPointerId(index), key);
                    performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
                    activate(key);
                    invalidate();
                }
                return true;
            }
            case MotionEvent.ACTION_UP: {
                pressed.remove(e.getPointerId(e.getActionIndex()));
                pressedBar = -1;
                pressedBright = -1;
                pressedToolpad = false;
                invalidate();
                return true;
            }
            case MotionEvent.ACTION_POINTER_UP: {
                pressed.remove(e.getPointerId(e.getActionIndex()));
                invalidate();
                return true;
            }
            case MotionEvent.ACTION_CANCEL: {
                pressed.clear();
                pressedBar = -1;
                pressedBright = -1;
                pressedToolpad = false;
                invalidate();
                return true;
            }
            default:
                return true;
        }
    }
}
