package com.phonekb;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.os.Handler;
import android.os.Looper;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;

import java.util.HashMap;
import java.util.Map;

/**
 * Media page: a media card (previous / play-pause / next on top, -10s / +10s wide keys below)
 * over a 2 x 5 grid of round function keys
 * (volume+ volume- mute screenshot recents / brightness- brightness+ back home menu).
 *
 * The -10s / +10s keys send the same seek action as before (previous/next track, or the arrow
 * keys, depending on the "media skip" preference) and repeat every {@link #LONG_REPEAT_MS} ms
 * while held. Previous / next repeat too, so holding them scrubs through tracks.
 *
 * Rewind / fast-forward consumer usages were dropped: no player on the tablet maps them
 * (getevent proved the reports arrive, Douyin and Bilibili just ignore them), while the
 * prev/next seek step works everywhere.
 */
public class MediaView extends View {

    private static final int LONG_REPEAT_MS = 100;

    private static final int KIND_CONSUMER = 0;
    private static final int KIND_BRIGHT = 1;
    private static final int KIND_SCREENSHOT = 2;
    private static final int KIND_RECENTS = 3;

    private static final class Key {
        final RectF rect = new RectF();
        final int icon;
        final String label;
        final int kind;
        /** Consumer usage id, or +1 / -1 for the brightness pair. */
        final int code;

        Key(int icon, String label, int kind, int code) {
            this.icon = icon;
            this.label = label;
            this.kind = kind;
            this.code = code;
        }
    }

    private final HidManager hid;
    private final Paint cardPaint = Ui.fill(Ui.SURFACE);
    private final Paint keyPaint = Ui.fill(Ui.SURFACE_2);
    private final Paint pressPaint = Ui.fill(Ui.KEY_PRESSED);
    private final Paint iconPaint = Ui.fill(Ui.TEXT);
    private final Paint iconDimPaint = Ui.fill(Ui.TEXT_DIM);
    private final Paint labelPaint;
    private final Paint labelDimPaint;
    private final float labelSize;

    private final RectF mediaCard = new RectF();
    private final RectF keypadCard = new RectF();

    /** Top row of the media card: previous / play-pause / next. */
    private static final int BAR_COUNT = 3;
    private static final int BAR_PREV = 0;
    private static final int BAR_NEXT = 2;
    private final RectF[] barAreas = new RectF[BAR_COUNT];
    private static final int[] BAR_ICONS = {Icons.PREV, Icons.PLAY_PAUSE, Icons.NEXT};
    private static final int[] BAR_USAGES = {
            HidConstants.CONSUMER_PREV_TRACK,
            HidConstants.CONSUMER_PLAY_PAUSE,
            HidConstants.CONSUMER_NEXT_TRACK};

    /** Bottom row of the media card: -10s / +10s wide keys, repeat while held. */
    private static final int PILL_COUNT = 2;
    private static final int PILL_BACK = 0;
    private static final int PILL_FORWARD = 1;
    private final RectF[] pillAreas = new RectF[PILL_COUNT];
    private final float pillRowHeight;

    /** 2 rows x 5 round function keys. */
    private final Key[] keys;
    private final Map<Integer, Key> pressed = new HashMap<>();

    private final float corner;
    private final float gap;
    private final float pad;
    private int pressedBar = -1;
    private int pressedPill = -1;

    private final RectF toolpadRect = new RectF();
    private final Paint toolpadOutline;
    private final Paint toolpadText;
    private final float toolpadHeight;
    private boolean toolpadRow;
    private boolean pressedToolpad;
    private Runnable touchpadAction;

    /** HID brightness usages are ignored by some OEM ROMs; the host may handle them itself. */
    public interface BrightnessListener {
        void onBrightness(boolean up);
    }

    private BrightnessListener brightnessListener;

    /** 1 = arrow keys (default), 2 = rewind / fast-forward. Debug intent can switch it. */
    private static final int SEEK_ARROW = 1;
    private static final int SEEK_CONSUMER = 2;
    private static int seekImpl = SEEK_ARROW;

    /** Debug-only hook (`--ei seek 2`) so the seek key can be swapped without a rebuild. */
    public static void setSeekImpl(int impl) {
        seekImpl = impl == SEEK_CONSUMER ? SEEK_CONSUMER : SEEK_ARROW;
    }

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable repeatBar = new Runnable() {
        @Override
        public void run() {
            if (pressedBar == BAR_PREV || pressedBar == BAR_NEXT) {
                hid.tapConsumer(BAR_USAGES[pressedBar]);
                handler.postDelayed(this, LONG_REPEAT_MS);
            } else if (pressedPill == PILL_BACK || pressedPill == PILL_FORWARD) {
                tapSkip(pressedPill == PILL_FORWARD);
                handler.postDelayed(this, LONG_REPEAT_MS);
            }
        }
    };

    public MediaView(Context context, HidManager hid) {
        super(context);
        this.hid = hid;
        corner = Ui.dp(context, 24);
        gap = Ui.dp(context, 8);
        pad = Ui.dp(context, 10);
        labelPaint = Ui.text(context, 22, Ui.TEXT);
        labelDimPaint = Ui.text(context, 22, Ui.TEXT_DIM);
        labelSize = Ui.text(context, 9.5f, Ui.TEXT_DIM).getTextSize();
        toolpadHeight = Ui.dp(context, 44);
        pillRowHeight = Ui.dp(context, 44);
        toolpadOutline = new Paint(Paint.ANTI_ALIAS_FLAG);
        toolpadOutline.setStyle(Paint.Style.STROKE);
        toolpadOutline.setColor(Ui.ACCENT);
        toolpadOutline.setStrokeWidth(Ui.dp(context, 1.5f));
        toolpadText = Ui.text(context, 15, Ui.ACCENT);
        toolpadText.setTextAlign(Paint.Align.CENTER);
        for (int i = 0; i < BAR_COUNT; i++) barAreas[i] = new RectF();
        for (int i = 0; i < PILL_COUNT; i++) pillAreas[i] = new RectF();

        keys = new Key[]{
                new Key(Icons.VOL_UP, "音量+", KIND_CONSUMER, HidConstants.CONSUMER_VOLUME_UP),
                new Key(Icons.VOL_DOWN, "音量−", KIND_CONSUMER, HidConstants.CONSUMER_VOLUME_DOWN),
                new Key(Icons.MUTE, "静音", KIND_CONSUMER, HidConstants.CONSUMER_MUTE),
                new Key(Icons.SCREENSHOT, "截屏", KIND_SCREENSHOT, 0),
                new Key(Icons.RECENTS, "多任务", KIND_RECENTS, 0),

                new Key(Icons.SUN_DOWN, "亮度−", KIND_BRIGHT, -1),
                new Key(Icons.SUN_UP, "亮度+", KIND_BRIGHT, 1),
                new Key(Icons.BACK, "返回", KIND_CONSUMER, HidConstants.CONSUMER_AC_BACK),
                new Key(Icons.HOME, "主页", KIND_CONSUMER, HidConstants.CONSUMER_AC_HOME),
                new Key(Icons.MENU, "菜单", KIND_CONSUMER, HidConstants.CONSUMER_MENU)};
    }

    /** Portrait media page: a full-width touchpad button above the media controls. */
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

        boolean landscape = (right - left) > (bottom - top) * 1.2f;
        float cardTop;
        if (landscape) {
            // Side by side: media card on the left, function keys on the right.
            float mediaW = (right - left) * 0.60f;
            mediaCard.set(left, contentTop, left + mediaW, bottom);
            keypadCard.set(left + mediaW + gap, contentTop, right, bottom);
            cardTop = contentTop;
        } else {
            float mediaH = pad * 2 + Ui.dp(getContext(), 62) + gap + pillRowHeight;
            mediaH = Math.min(mediaH, (bottom - contentTop) * 0.46f);
            mediaCard.set(left, contentTop, right, contentTop + mediaH);
            keypadCard.set(left, mediaCard.bottom + gap, right, bottom);
            cardTop = mediaCard.top;
        }

        // Media card: previous / play / next on top, -10s / +10s below.
        float inset = Ui.dp(getContext(), 10);
        float innerLeft = mediaCard.left + inset;
        float innerRight = mediaCard.right - inset;
        float innerTop = mediaCard.top + inset;
        float innerBottom = mediaCard.bottom - inset;
        float innerH = innerBottom - innerTop;
        if (innerH < 1f || innerRight <= innerLeft) return;
        // Natural heights; landscape stretches them a little but never more than a thumb key.
        float naturalRow1 = Ui.dp(getContext(), 56);
        float naturalRow2 = Ui.dp(getContext(), 44);
        float row1H;
        float row2H;
        float topOff = 0f;
        float avail = innerH - gap;
        if (avail >= naturalRow1 + naturalRow2) {
            float extra = avail - naturalRow1 - naturalRow2;
            row1H = naturalRow1 + (landscape ? Math.min(extra * 0.62f, Ui.dp(getContext(), 44)) : 0f);
            row2H = naturalRow2 + (landscape ? Math.min(extra * 0.38f, Ui.dp(getContext(), 24)) : 0f);
            topOff = Math.max(0f, (innerH - row1H - gap - row2H) / 2f);
        } else {
            row1H = Math.max(Ui.dp(getContext(), 40), avail * 0.56f);
            row2H = Math.max(Ui.dp(getContext(), 30), avail - row1H);
        }
        float barTop = innerTop + topOff;
        float cellW = (innerRight - innerLeft - gap * (BAR_COUNT - 1)) / (float) BAR_COUNT;
        for (int i = 0; i < BAR_COUNT; i++) {
            float l = innerLeft + i * (cellW + gap);
            barAreas[i].set(l, barTop, l + cellW, barTop + row1H);
        }
        float pillW = (innerRight - innerLeft - gap) / (float) PILL_COUNT;
        for (int i = 0; i < PILL_COUNT; i++) {
            float l = innerLeft + i * (pillW + gap);
            pillAreas[i].set(l, barTop + row1H + gap, l + pillW, barTop + row1H + gap + row2H);
        }

        // Function keys: 2 rows x 5 equal cells.
        float gridPad = Ui.dp(getContext(), 8);
        float gridLeft = keypadCard.left + gridPad;
        float gridRight = keypadCard.right - gridPad;
        float gridTop = keypadCard.top + gridPad;
        float gridBottom = keypadCard.bottom - gridPad;
        if (gridRight <= gridLeft || gridBottom <= gridTop) return;
        float cw = (gridRight - gridLeft) / 5f;
        float ch = (gridBottom - gridTop) / 2f;
        for (int i = 0; i < keys.length; i++) {
            int row = i / 5;
            int col = i % 5;
            float l = gridLeft + col * cw;
            float t = gridTop + row * ch;
            keys[i].rect.set(l, t, l + cw, t + ch);
        }
    }

    private float keyRadius(Key key) {
        float labelRoom = Ui.dp(getContext(), 15);
        float byHeight = (key.rect.height() - labelRoom) * 0.5f;
        float byWidth = key.rect.width() * 0.44f;
        return Math.max(Ui.dp(getContext(), 14), Math.min(byHeight, byWidth));
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
        for (int i = 0; i < BAR_COUNT; i++) {
            RectF area = barAreas[i];
            if (area.width() <= 0f) continue;
            if (i == pressedBar) {
                RectF hit = new RectF(area);
                hit.inset(Ui.dp(getContext(), 2), Ui.dp(getContext(), 2));
                canvas.drawRoundRect(hit, Ui.dp(getContext(), 16), Ui.dp(getContext(), 16), keyPaint);
            }
            RectF box = new RectF(area);
            box.inset(area.width() * 0.26f, area.height() * 0.26f);
            Icons.draw(canvas, BAR_ICONS[i], box, icon);
        }

        drawSkip(canvas, pillAreas[PILL_BACK], PILL_BACK);
        drawSkip(canvas, pillAreas[PILL_FORWARD], PILL_FORWARD);

        Paint label = hid.isConnected() ? labelDimPaint : iconDimPaint;
        for (Key key : keys) {
            float radius = keyRadius(key);
            float cx = key.rect.centerX();
            float cy = key.rect.centerY() - Ui.dp(getContext(), 6);
            canvas.drawCircle(cx, cy, radius, pressed.containsValue(key) ? pressPaint : keyPaint);
            RectF box = new RectF(cx - radius, cy - radius, cx + radius, cy + radius);
            box.inset(radius * 0.42f, radius * 0.42f);
            Icons.draw(canvas, key.icon, box, icon);
            label.setTextSize(labelSize);
            label.setTextAlign(Paint.Align.CENTER);
            canvas.drawText(key.label, cx, cy + radius + Ui.dp(getContext(), 11), label);
        }
    }

    /** -10s / +10s wide keys: seek glyph on the left, the step on the right. */
    private void drawSkip(Canvas canvas, RectF area, int index) {
        if (area.width() <= 0f || area.height() <= 0f) return;
        float h = area.height();
        float radius = Math.min(Ui.dp(getContext(), 16), Math.min(h, area.width()) / 2f);
        canvas.drawRoundRect(area, radius, radius, index == pressedPill ? pressPaint : keyPaint);
        Paint icon = hid.isConnected() ? iconPaint : iconDimPaint;
        Paint text = hid.isConnected() ? labelPaint : labelDimPaint;
        float glyph = Math.min(Math.min(h * 0.30f, area.width() * 0.09f), Ui.dp(getContext(), 15f));
        RectF box = new RectF(area.left + area.width() * 0.12f, area.centerY() - glyph,
                area.left + area.width() * 0.12f + glyph * 2f, area.centerY() + glyph);
        Icons.draw(canvas, index == PILL_BACK ? Icons.REWIND : Icons.FORWARD, box, icon);
        boolean back = index == PILL_BACK;
        text.setTextSize(Math.min(Math.min(h * 0.42f, area.width() * 0.22f), Ui.dp(getContext(), 22f)));
        text.setTextAlign(Paint.Align.CENTER);
        canvas.drawText(back ? "−10s" : "＋10s", area.left + area.width() * 0.60f,
                area.centerY() - (text.ascent() + text.descent()) / 2f, text);
    }

    private void haptic() {
        if (PointerSettings.fast()) return;
        performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
    }

    private void tapBar(int index) {
        hid.tapConsumer(BAR_USAGES[index]);
    }

    private void startBarRepeat(int bar) {
        pressedBar = bar;
        handler.removeCallbacks(repeatBar);
        if (bar == BAR_PREV || bar == BAR_NEXT) handler.postDelayed(repeatBar, 320);
    }

    private void stopBarRepeat() {
        handler.removeCallbacks(repeatBar);
        pressedBar = -1;
        pressedPill = -1;
    }

    /** -10s / +10s: player seek only — the arrow keys (REWIND / FAST_FORWARD as a debug fallback). */
    private void tapSkip(boolean forward) {
        if (seekImpl == SEEK_CONSUMER) {
            hid.tapConsumer(forward ? HidConstants.CONSUMER_FAST_FORWARD : HidConstants.CONSUMER_REWIND);
        } else {
            hid.tapKey(0, forward ? HidConstants.KEY_RIGHT : HidConstants.KEY_LEFT);
        }
    }

    private void activate(Key key) {
        switch (key.kind) {
            case KIND_BRIGHT: {
                boolean up = key.code > 0;
                hid.tapConsumer(up ? HidConstants.CONSUMER_BRIGHTNESS_INCREMENT
                        : HidConstants.CONSUMER_BRIGHTNESS_DECREMENT);
                if (brightnessListener != null) brightnessListener.onBrightness(up);
                break;
            }
            case KIND_SCREENSHOT:
                hid.tapKey(0, HidConstants.KEY_SYSRQ);
                break;
            case KIND_RECENTS:
                // No HID usage maps to the recents key, so use the Android keyboard shortcut.
                hid.tapKey(HidConstants.MOD_LEFT_ALT, HidConstants.KEY_TAB);
                break;
            default:
                hid.tapConsumer(key.code);
                break;
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        // Never leave a key pressed when the page goes away.
        stopBarRepeat();
        super.onDetachedFromWindow();
    }

    private Key find(float x, float y) {
        for (Key key : keys) {
            float radius = keyRadius(key);
            float cx = key.rect.centerX();
            float cy = key.rect.centerY() - Ui.dp(getContext(), 6);
            float dx = x - cx;
            float dy = y - cy;
            if (dx * dx + dy * dy <= radius * radius * 1.12f) return key;
        }
        return null;
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
                for (int i = 0; i < PILL_COUNT; i++) {
                    if (!pillAreas[i].contains(x, y)) continue;
                    pressedPill = i;
                    haptic();
                    tapSkip(i == PILL_FORWARD);
                    handler.removeCallbacks(repeatBar);
                    handler.postDelayed(repeatBar, 380);
                    invalidate();
                    return true;
                }
                for (int i = 0; i < BAR_COUNT; i++) {
                    if (barAreas[i].contains(x, y)) {
                        haptic();
                        tapBar(i);
                        startBarRepeat(i);
                        invalidate();
                        return true;
                    }
                }
                Key key = find(x, y);
                if (key == null) return true;
                pressed.put(e.getPointerId(0), key);
                haptic();
                activate(key);
                invalidate();
                return true;
            }
            case MotionEvent.ACTION_POINTER_DOWN: {
                int index = e.getActionIndex();
                Key key = find(e.getX(index), e.getY(index));
                if (key != null) {
                    pressed.put(e.getPointerId(index), key);
                    haptic();
                    activate(key);
                    invalidate();
                }
                return true;
            }
            case MotionEvent.ACTION_UP: {
                pressed.remove(e.getPointerId(e.getActionIndex()));
                stopBarRepeat();
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
                stopBarRepeat();
                pressedToolpad = false;
                invalidate();
                return true;
            }
            default:
                return true;
        }
    }
}