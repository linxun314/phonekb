package com.phonekb;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;

/**
 * Touchpad page: overlay toggles on the left (digits / keyboard / menu), the trackpad in
 * the middle, a scroll column on the right and 返回 / 主页 / 后台 icon keys at the
 * bottom, following the Bluetooth Remote layout.
 *
 * A compact instance (left column hidden) is used by the touchpad overlay that opens
 * from the keyboard page. It keeps the same gestures and the same bottom nav keys.
 */
public class ControlPadView extends View {

    public interface Listener {
        void onToggleNumpad();
        void onToggleKeyboard();
    }

    public static final int OVERLAY_NONE = 0;
    public static final int OVERLAY_NUMPAD = 1;
    public static final int OVERLAY_KEYBOARD = 2;

    private static final int PRESS_NUMPAD = 0;
    private static final int PRESS_KEYBOARD = 1;
    private static final int PRESS_BACK = 2;
    private static final int PRESS_HOME = 3;
    private static final int PRESS_RECENTS = 4;
    private static final int PRESS_SCROLL_UP = 5;
    private static final int PRESS_SCROLL_DOWN = 6;
    private static final int PRESS_MENU = 7;
    private static final int PRESS_MIDDLE = 8;

    private static final int MOUSE_MIDDLE = 4;

    private final HidManager hid;
    private final PadController pad;
    private final boolean compact;
    private final boolean padOnly;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private Listener listener;
    private int overlay = OVERLAY_NONE;

    private final Paint cardPaint = Ui.fill(Ui.SURFACE);
    private final Paint keyPaint = Ui.fill(Ui.SURFACE_2);
    private final Paint pressPaint = Ui.fill(Ui.KEY_PRESSED);
    private final Paint iconPaint = Ui.fill(Ui.TEXT);
    private final Paint iconDimPaint = Ui.fill(Ui.TEXT_DIM);
    private final Paint labelPaint;
    private final Paint labelDimPaint;
    private final Paint hintPaint;
    private final Paint sensTextPaint;
    private final Paint sensBgPaint = Ui.fill(Ui.SURFACE_2);
    private final Paint sensStrokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private final RectF numpadRect = new RectF();
    private final RectF keyboardRect = new RectF();
    private final RectF menuRect = new RectF();
    private final RectF padRect = new RectF();
    private final RectF scrollUpRect = new RectF();
    private final RectF scrollDownRect = new RectF();
    /** 两颗箭头键 + 中间的缝 = 一整条「滚轮条」（v0.39.2）。 */
    private final RectF stripRect = new RectF();
    private final RectF backRect = new RectF();
    private final RectF homeRect = new RectF();
    private final RectF recentsRect = new RectF();
    private final RectF middleRect = new RectF();
    private final RectF sensChipRect = new RectF();
    private final RectF gestChipRect = new RectF();
    private final Paint gestBgPaint = Ui.fill(Ui.SURFACE_2);
    private final Paint gestStrokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint gestOnPaint;
    private final Paint gestOffPaint;
    private final Paint middleTextPaint;
    private final Paint stripRailPaint = Ui.fill(0x33FFFFFF);
    private final Paint stripRailOnPaint = Ui.fill(Ui.ACCENT);

    private final float margin;
    private final float gap;
    private final float cardRadius;
    private final float keyRadius;

    private int pressedKey = -1;
    private int scrollDir;
    private boolean padActive;
    // 滚轮条状态（v0.39.2）：按住不动 = 慢速连滚，上下拖动 = 跟手滚轮。
    private boolean stripDown;
    private boolean stripDragged;
    private float stripLastY;
    private float stripAcc;
    private float stripVel;
    private long stripLastAt;
    private float stripTouchY;
    private int stripTicks;
    private int flingDir;
    private int flingLeft;

    /** Hold cadence is user-configurable (慢 180 / 中 120 / 快 70 ms), see PointerSettings. */
    private final Runnable scrollRepeat = new Runnable() {
        @Override
        public void run() {
            if (scrollDir != 0) {
                hid.mouseWheel(scrollDir);
                handler.postDelayed(this, PointerSettings.scrollHoldMs());
            }
        }
    };

    /** 快速甩一下后的惯性：再补几格滚轮（可选，默认关）。 */
    private final Runnable stripFlingRun = new Runnable() {
        @Override
        public void run() {
            if (flingLeft <= 0) return;
            flingLeft--;
            hid.mouseWheel(flingDir);
            if (flingLeft > 0) handler.postDelayed(this, 45);
        }
    };

    public ControlPadView(Context context, HidManager hid) {
        this(context, hid, false, false);
    }

    /**
     * @param compact hides the left overlay-toggle column (digits / keyboard / menu),
     *                used by the touchpad overlay opened from the keyboard page.
     */
    public ControlPadView(Context context, HidManager hid, boolean compact) {
        this(context, hid, compact, false);
    }

    /**
     * @param padOnly draws only the big trackpad card (no scroll column, no bottom nav,
     *                no left toggles). Used as the top half of the portrait keyboard page.
     */
    public ControlPadView(Context context, HidManager hid, boolean compact, boolean padOnly) {
        super(context);
        this.hid = hid;
        this.compact = compact;
        this.padOnly = padOnly;
        pad = new PadController(this, hid);
        margin = Ui.dp(context, 10);
        gap = Ui.dp(context, 8);
        cardRadius = Ui.dp(context, 20);
        keyRadius = Ui.dp(context, 14);
        labelPaint = Ui.text(context, 14, Ui.TEXT);
        labelDimPaint = Ui.text(context, 14, Ui.TEXT_DIM);
        hintPaint = Ui.text(context, 13, Ui.TEXT_DIM);
        sensTextPaint = Ui.text(context, 11, Ui.ACCENT);
        sensStrokePaint.setStyle(Paint.Style.STROKE);
        sensStrokePaint.setStrokeWidth(Ui.dp(context, 1.2f));
        sensStrokePaint.setColor(Ui.ACCENT);
        gestStrokePaint.setStyle(Paint.Style.STROKE);
        gestStrokePaint.setStrokeWidth(Ui.dp(context, 1.2f));
        gestStrokePaint.setColor(Ui.ACCENT);
        gestOnPaint = Ui.text(context, 11, Ui.ACCENT);
        gestOffPaint = Ui.text(context, 11, Ui.TEXT_DIM);
        middleTextPaint = Ui.text(context, 12, Ui.TEXT);
        PointerSettings.init(context);
        PadGestures.init(context);
    }

    public void setListener(Listener listener) {
        this.listener = listener;
    }

    /** Highlights the left toggle matching the currently open overlay. */
    public void setOverlay(int mode) {
        overlay = mode;
        invalidate();
    }

    @Override
    protected void onSizeChanged(int w, int h, int ow, int oh) {
        super.onSizeChanged(w, h, ow, oh);
        float contentW = w - margin * 2f;
        float contentH = h - margin * 2f;
        if (contentW <= 0 || contentH <= 0) return;
        if (padOnly) {
            padRect.set(margin, margin, w - margin, h - margin);
            pad.setPadBounds(padRect);
            return;
        }
        boolean land = w > h;
        float bottomH = contentH * (land ? 0.2f : 0.18f);
        float mainH = contentH - bottomH - gap;
        float toggleW = compact ? 0f : Ui.dp(getContext(), land ? 62 : 54);
        float scrollW = contentW * (land ? 0.16f : 0.15f);
        float padW = contentW - scrollW - gap - (compact ? 0f : toggleW + gap);
        if (mainH <= 0 || padW <= 0) return;

        if (!compact) {
            float toggleH = (mainH - gap * 2f) / 3f;
            numpadRect.set(margin, margin, margin + toggleW, margin + toggleH);
            keyboardRect.set(margin, numpadRect.bottom + gap, margin + toggleW, numpadRect.bottom + gap + toggleH);
            menuRect.set(margin, keyboardRect.bottom + gap, margin + toggleW, margin + mainH);
        }
        float padLeft = margin + toggleW + (compact ? 0f : gap);
        padRect.set(padLeft, margin, padLeft + padW, margin + mainH);
        // Edge push needs the real pad rectangle, not the whole view.
        pad.setPadBounds(padRect);
        scrollUpRect.set(padRect.right + gap, margin, margin + contentW, margin + (mainH - gap) / 2f);
        scrollDownRect.set(scrollUpRect.left, scrollUpRect.bottom + gap, scrollUpRect.right, margin + mainH);
        // 两颗箭头 + 中间那道缝，合成一条连续的滚轮条。
        stripRect.set(scrollUpRect);
        stripRect.union(scrollDownRect);
        updateSensChip();

        float navTop = margin + mainH + gap;
        float navBottom = margin + contentH;
        // Four cells since v0.39.0: 返回 / 主页 / 后台 + the configurable middle key.
        float cellW = (contentW - gap * 3f) / 4f;
        backRect.set(margin, navTop, margin + cellW, navBottom);
        homeRect.set(backRect.right + gap, navTop, backRect.right + gap + cellW, navBottom);
        recentsRect.set(homeRect.right + gap, navTop, homeRect.right + gap + cellW, navBottom);
        middleRect.set(recentsRect.right + gap, navTop, margin + contentW, navBottom);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        canvas.drawColor(Ui.BG);
        Paint icon = hid.isConnected() ? iconPaint : iconDimPaint;

        if (padOnly) {
            canvas.drawRoundRect(padRect, cardRadius, cardRadius, cardPaint);
            Ui.textCenter(canvas, "触控区", padRect.centerX(), padRect.centerY() - Ui.dp(getContext(), 13), hintPaint);
            Ui.textCenter(canvas, "单指移动 · 点击=左键 · 双指=滚动 / 右键", padRect.centerX(),
                    padRect.centerY() + Ui.dp(getContext(), 14), hintPaint);
            return;
        }

        if (!compact) {
            drawKey(canvas, numpadRect, pressedKey == PRESS_NUMPAD || overlay == OVERLAY_NUMPAD);
            drawDots(canvas, numpadRect.centerX(), numpadRect.centerY() - Ui.dp(getContext(), 16), 3, 2, icon);
            Ui.textCenter(canvas, "数字", numpadRect.centerX(), numpadRect.centerY() + Ui.dp(getContext(), 20),
                    overlay == OVERLAY_NUMPAD ? labelPaint : labelDimPaint);

            drawKey(canvas, keyboardRect, pressedKey == PRESS_KEYBOARD || overlay == OVERLAY_KEYBOARD);
            drawDots(canvas, keyboardRect.centerX(), keyboardRect.centerY() - Ui.dp(getContext(), 16), 4, 2, icon);
            Ui.textCenter(canvas, "键盘", keyboardRect.centerX(), keyboardRect.centerY() + Ui.dp(getContext(), 20),
                    overlay == OVERLAY_KEYBOARD ? labelPaint : labelDimPaint);

            drawKey(canvas, menuRect, pressedKey == PRESS_MENU);
            float iconHalf = Ui.dp(getContext(), 13);
            RectF menuIcon = new RectF(menuRect.centerX() - iconHalf, menuRect.centerY() - Ui.dp(getContext(), 29),
                    menuRect.centerX() + iconHalf, menuRect.centerY() - Ui.dp(getContext(), 3));
            Icons.draw(canvas, Icons.MENU, menuIcon, icon);
            Ui.textCenter(canvas, "菜单", menuRect.centerX(), menuRect.centerY() + Ui.dp(getContext(), 20), labelDimPaint);
        }

        canvas.drawRoundRect(padRect, cardRadius, cardRadius, cardPaint);
        Ui.textCenter(canvas, "触控区", padRect.centerX(), padRect.centerY() - Ui.dp(getContext(), 13), hintPaint);
        Ui.textCenter(canvas, "单指移动 · 点击=左键 · 双指=滚动 / 右键", padRect.centerX(),
                padRect.centerY() + Ui.dp(getContext(), 14), hintPaint);
        drawSensChip(canvas);
        drawGestChip(canvas);

        drawKey(canvas, scrollUpRect, pressedKey == PRESS_SCROLL_UP);
        drawKey(canvas, scrollDownRect, pressedKey == PRESS_SCROLL_DOWN);
        drawArrow(canvas, scrollUpRect, Icons.UP_ARROW, icon);
        drawArrow(canvas, scrollDownRect, Icons.DOWN_ARROW, icon);
        drawStripRail(canvas);

        drawKey(canvas, backRect, pressedKey == PRESS_BACK);
        drawKey(canvas, homeRect, pressedKey == PRESS_HOME);
        drawKey(canvas, recentsRect, pressedKey == PRESS_RECENTS);
        Icons.draw(canvas, Icons.BACK, iconBox(backRect), icon);
        Icons.draw(canvas, Icons.HOME, iconBox(homeRect), icon);
        Icons.draw(canvas, Icons.RECENTS, iconBox(recentsRect), icon);
        drawKey(canvas, middleRect, pressedKey == PRESS_MIDDLE);
        Ui.textCenter(canvas, PadGestures.middleLabel(getContext()), middleRect.centerX(),
                middleRect.centerY() + Ui.dp(getContext(), 4), middleTextPaint);
    }

    /** 「灵敏度 1.0x」 pill in the top-left corner of the trackpad card (touchpad page only). */
    private void updateSensChip() {
        if (!showSensChip()) {
            sensChipRect.setEmpty();
            return;
        }
        float chipW = Ui.dp(getContext(), 92);
        float chipH = Ui.dp(getContext(), 26);
        float left = padRect.left + Ui.dp(getContext(), 10);
        float top = padRect.top + Ui.dp(getContext(), 10);
        sensChipRect.set(left, top, left + chipW, top + chipH);
        // The gesture switch sits right next to the sensitivity chip: one tap turns every
        // gesture off (and back on) without opening any dialog.
        float gestW = Ui.dp(getContext(), 82);
        float gestLeft = sensChipRect.right + Ui.dp(getContext(), 8);
        if (gestLeft + gestW < padRect.right - Ui.dp(getContext(), 8)) {
            gestChipRect.set(gestLeft, top, gestLeft + gestW, top + chipH);
        } else {
            gestChipRect.setEmpty();
        }
    }

    private boolean showSensChip() {
        return !compact && !padOnly && padRect.width() > Ui.dp(getContext(), 180)
                && padRect.height() > Ui.dp(getContext(), 140);
    }

    private void drawSensChip(Canvas canvas) {
        if (sensChipRect.isEmpty()) return;
        canvas.drawRoundRect(sensChipRect, Ui.dp(getContext(), 13), Ui.dp(getContext(), 13), sensBgPaint);
        canvas.drawRoundRect(sensChipRect, Ui.dp(getContext(), 13), Ui.dp(getContext(), 13), sensStrokePaint);
        Ui.textCenter(canvas, "灵敏度 " + PointerSettings.speedLabel(getContext()),
                sensChipRect.centerX(), sensChipRect.centerY() + Ui.dp(getContext(), 4), sensTextPaint);
    }

    /** 「手势 开 / 关」 chip — the one-tap master switch for the v0.39.0 gesture set. */
    private void drawGestChip(Canvas canvas) {
        if (gestChipRect.isEmpty()) return;
        float radius = Ui.dp(getContext(), 13);
        canvas.drawRoundRect(gestChipRect, radius, radius, gestBgPaint);
        boolean on = PadGestures.on();
        if (on) canvas.drawRoundRect(gestChipRect, radius, radius, gestStrokePaint);
        Ui.textCenter(canvas, on ? "手势 开" : "手势 关", gestChipRect.centerX(),
                gestChipRect.centerY() + Ui.dp(getContext(), 4), on ? gestOnPaint : gestOffPaint);
    }

    private void drawKey(Canvas canvas, RectF rect, boolean pressed) {
        if (rect.isEmpty()) return;
        canvas.drawRoundRect(rect, keyRadius, keyRadius, pressed ? pressPaint : keyPaint);
    }

    private void drawArrow(Canvas canvas, RectF rect, int iconId, Paint icon) {
        if (rect.isEmpty()) return;
        Icons.draw(canvas, iconId, iconBox(rect), icon);
    }

    private RectF iconBox(RectF rect) {
        RectF box = new RectF(rect);
        box.inset(rect.width() * 0.28f, rect.height() * 0.22f);
        return box;
    }

    private void drawDots(Canvas canvas, float cx, float cy, int cols, int rows, Paint paint) {
        Paint dot = new Paint(paint);
        dot.setStyle(Paint.Style.FILL);
        float r = Ui.dp(getContext(), 2.4f);
        float stepX = Ui.dp(getContext(), 9);
        float stepY = Ui.dp(getContext(), 9);
        for (int row = 0; row < rows; row++) {
            for (int col = 0; col < cols; col++) {
                float x = cx + (col - (cols - 1) / 2f) * stepX;
                float y = cy + (row - (rows - 1) / 2f) * stepY;
                canvas.drawCircle(x, y, r, dot);
            }
        }
    }

    /** Opens the 「指针灵敏度」 dialog (debug hook + the chip tap both end up here). */
    public void showSensitivityDialog() {
        SensitivityDialog.show(getContext(), this::invalidate);
    }

    private void haptic() {
        performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
    }

    /**
     * 滚轮条按下（v0.39.2）：立刻发一格（点一下 = 一格，和以前一样），同时预备「按住不动
     * 就慢速连滚」；手指一旦上下拖动就切成跟手滚轮，并取消连滚。
     *
     * @param tapDir 点一下要发哪一格（上半区 = +1 往上，下半区 = -1 往下）
     */
    private void beginStrip(float y, int tapDir) {
        PointerSettings.init(getContext());
        handler.removeCallbacks(stripFlingRun);
        flingLeft = 0;
        stripDown = true;
        stripDragged = false;
        stripAcc = 0f;
        stripVel = 0f;
        stripTicks = 0;
        stripLastY = y;
        stripTouchY = y;
        stripLastAt = SystemClock.uptimeMillis();
        scrollDir = tapDir;
        pressedKey = tapDir > 0 ? PRESS_SCROLL_UP : PRESS_SCROLL_DOWN;
        haptic();
        hid.mouseWheel(tapDir);
        handler.removeCallbacks(scrollRepeat);
        // 第一次重复稍微等一会儿：先把「点一下」和「按住」区分开，再进入连续慢速滚动
        handler.postDelayed(scrollRepeat, 150);
        invalidate();
    }

    /** 滚轮条拖动：手指每走 stripStepDp 就发一格滚轮。 */
    private void moveStrip(float y) {
        long now = SystemClock.uptimeMillis();
        float dt = Math.max(1f, now - stripLastAt);
        float dy = y - stripLastY;
        stripVel = stripVel * 0.6f + (dy / dt) * 0.4f;
        stripLastY = y;
        stripLastAt = now;
        stripTouchY = y;
        float step = Ui.dp(getContext(), PointerSettings.stripStepDp());
        if (step <= 0f) return;
        stripAcc -= dy; // 手指往上走记为正
        int steps = 0;
        while (stripAcc >= step) {
            steps++;
            stripAcc -= step;
        }
        while (stripAcc <= -step) {
            steps--;
            stripAcc += step;
        }
        if (steps == 0) {
            invalidate();
            return;
        }
        if (!stripDragged) {
            stripDragged = true;
            handler.removeCallbacks(scrollRepeat);
            scrollDir = 0;
        }
        int dir = steps > 0 ? 1 : -1;
        pressedKey = dir > 0 ? PRESS_SCROLL_UP : PRESS_SCROLL_DOWN;
        hid.mouseWheel(PadGestures.natural() ? -steps : steps);
        stripTicks += Math.abs(steps);
        if (stripTicks >= 3) {
            stripTicks = 0;
            haptic();
        }
        invalidate();
    }

    /** 抬手：停连滚；开了惯性且是快速甩动时，再补几格。 */
    private void endStrip(boolean allowFling) {
        handler.removeCallbacks(scrollRepeat);
        boolean wasDown = stripDown;
        stripDown = false;
        scrollDir = 0;
        pressedKey = -1;
        if (wasDown && allowFling && stripDragged && stripVel != 0f) {
            startStripFling(stripVel);
        }
        stripVel = 0f;
        stripDragged = false;
        invalidate();
    }

    private void startStripFling(float vel) {
        if (!PointerSettings.stripFling()) return;
        float dp = Math.max(0.5f, Ui.dp(getContext(), 1f));
        float dpPerMs = Math.abs(vel) / dp;
        int ticks = Math.round(dpPerMs * 4f);
        if (ticks <= 0) return;
        if (ticks > 4) ticks = 4;
        // 手指往下 = moveStrip 里的负方向，滚轮方向跟它保持一致。
        int dir = vel > 0f ? -1 : 1;
        flingDir = PadGestures.natural() ? -dir : dir;
        flingLeft = ticks;
        handler.removeCallbacks(stripFlingRun);
        handler.postDelayed(stripFlingRun, 40);
    }

    /** 两颗箭头之间那条细轨：拖动时整条变蓝，让「这里能拖」看得出来。 */
    private void drawStripRail(Canvas canvas) {
        if (stripRect.isEmpty()) return;
        boolean active = stripDown;
        float railW = Ui.dp(getContext(), 3f);
        RectF rail = new RectF(stripRect.centerX() - railW / 2f, stripRect.top + Ui.dp(getContext(), 10),
                stripRect.centerX() + railW / 2f, stripRect.bottom - Ui.dp(getContext(), 10));
        float radius = railW / 2f;
        canvas.drawRoundRect(rail, radius, radius, active ? stripRailOnPaint : stripRailPaint);
        if (active) {
            float dotR = Ui.dp(getContext(), 4f);
            float cy = Math.max(rail.top, Math.min(rail.bottom, stripTouchY));
            canvas.drawCircle(rail.centerX(), cy, dotR, stripRailOnPaint);
        }
    }

    private void resetPressed() {
        pressedKey = -1;
        invalidate();
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN: {
                float x = e.getX();
                float y = e.getY();
                if (padOnly) {
                    if (padRect.contains(x, y)) {
                        padActive = true;
                        return pad.onTouch(e);
                    }
                    return true;
                }
                if (!compact) {
                    if (numpadRect.contains(x, y)) {
                        pressedKey = PRESS_NUMPAD;
                        haptic();
                        if (listener != null) listener.onToggleNumpad();
                        invalidate();
                        return true;
                    }
                    if (keyboardRect.contains(x, y)) {
                        pressedKey = PRESS_KEYBOARD;
                        haptic();
                        if (listener != null) listener.onToggleKeyboard();
                        invalidate();
                        return true;
                    }
                    if (menuRect.contains(x, y)) {
                        pressedKey = PRESS_MENU;
                        haptic();
                        hid.tapConsumer(HidConstants.CONSUMER_MENU);
                        invalidate();
                        return true;
                    }
                }
                if (!sensChipRect.isEmpty() && sensChipRect.contains(x, y)) {
                    haptic();
                    showSensitivityDialog();
                    return true;
                }
                if (!gestChipRect.isEmpty() && gestChipRect.contains(x, y)) {
                    boolean next = !PadGestures.on();
                    PadGestures.setOn(getContext(), next);
                    haptic();
                    invalidate();
                    android.widget.Toast.makeText(getContext(),
                            next ? "已开启触控板手势"
                                    : "已关闭触控板手势（只剩移动 / 点击 / 双指滚动）",
                            android.widget.Toast.LENGTH_SHORT).show();
                    return true;
                }
                if (!stripRect.isEmpty() && stripRect.contains(x, y)) {
                    // 点上半区 = 上一格，下半区 = 下一格；接着上下拖就是跟手滚轮。
                    beginStrip(y, y < stripRect.centerY() ? 1 : -1);
                    return true;
                }
                if (backRect.contains(x, y)) {
                    pressedKey = PRESS_BACK;
                    haptic();
                    hid.tapConsumer(HidConstants.CONSUMER_AC_BACK);
                    invalidate();
                    return true;
                }
                if (homeRect.contains(x, y)) {
                    pressedKey = PRESS_HOME;
                    haptic();
                    hid.tapConsumer(HidConstants.CONSUMER_AC_HOME);
                    invalidate();
                    return true;
                }
                if (recentsRect.contains(x, y)) {
                    pressedKey = PRESS_RECENTS;
                    haptic();
                    hid.tapCombo(HidConstants.MOD_LEFT_GUI, HidConstants.KEY_TAB);
                    invalidate();
                    return true;
                }
                if (middleRect.contains(x, y)) {
                    pressedKey = PRESS_MIDDLE;
                    haptic();
                    // 电脑模式 always sends the mouse middle button: Windows uses it (open in a new
                    // tab); on Android the key can be re-pointed at 返回 / 主页 where middle does
                    // nothing at all.
                    int m = PadGestures.pcMode(getContext())
                            ? PadGestures.MIDDLE_MOUSE : PadGestures.middle();
                    if (m == PadGestures.MIDDLE_BACK) {
                        hid.tapConsumer(HidConstants.CONSUMER_AC_BACK);
                    } else if (m == PadGestures.MIDDLE_HOME) {
                        hid.tapConsumer(HidConstants.CONSUMER_AC_HOME);
                    } else {
                        hid.mouseClick(MOUSE_MIDDLE);
                    }
                    invalidate();
                    return true;
                }
                if (padRect.contains(x, y)) {
                    padActive = true;
                    return pad.onTouch(e);
                }
                return true;
            }
            case MotionEvent.ACTION_POINTER_DOWN:
            case MotionEvent.ACTION_POINTER_UP:
            case MotionEvent.ACTION_MOVE: {
                if (padActive) return pad.onTouch(e);
                if (stripDown) {
                    moveStrip(e.getY());
                    return true;
                }
                return true;
            }
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL: {
                if (padActive) {
                    boolean handled = pad.onTouch(e);
                    padActive = false;
                    resetPressed();
                    return handled;
                }
                endStrip(e.getActionMasked() == MotionEvent.ACTION_UP);
                resetPressed();
                return true;
            }
            default:
                return true;
        }
    }
}
