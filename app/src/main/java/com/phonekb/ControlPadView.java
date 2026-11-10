package com.phonekb;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
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
    // 苹果风格滚轮条（v0.40.0，F 版 macOS 胶囊滑块）：磨砂胶囊轨道 + 顶部一道高光 +
    // 上下细 chevron + 一根常驻的 30 dp 磨砂白滑块（中间三条细纹）；按住拖动时滑块变亮并跟随手指。
    private final Paint stripTrackPaint = Ui.fill(stripBlend(0xFFFFFF, 0.07f, Ui.SURFACE));
    private final Paint stripSheenPaint = Ui.fill(stripBlend(0xFFFFFF, 0.04f, Ui.SURFACE));
    private final Paint stripThumbPaint = Ui.fill(stripBlend(0xFFFFFF, 0.50f, Ui.SURFACE));
    private final Paint stripThumbOnPaint = Ui.fill(stripBlend(0xFFFFFF, 0.88f, Ui.SURFACE));
    private final Paint stripThumbOffPaint = Ui.fill(stripBlend(0xFFFFFF, 0.18f, Ui.SURFACE));
    private final Paint stripGripPaint = Ui.fill(stripBlend(0x282C34, 0.55f, Ui.SURFACE));
    private final Paint stripStrokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint stripThumbStrokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint stripChevPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    /** 把 fg 按 alpha 混到 bg 上（都当不透明 RGB），用来拼出 macOS 那种磨砂色阶。 */
    private static int stripBlend(int fg, float a, int bg) {
        int fr = (fg >> 16) & 0xFF, fg2 = (fg >> 8) & 0xFF, fb = fg & 0xFF;
        int br = (bg >> 16) & 0xFF, bg2 = (bg >> 8) & 0xFF, bb = bg & 0xFF;
        int r = Math.round(fr * a + br * (1f - a));
        int g = Math.round(fg2 * a + bg2 * (1f - a));
        int b = Math.round(fb * a + bb * (1f - a));
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }

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
    /** 这次按下是不是真的按在滑块上（v0.40.0：只有抓到滑块才跟着手指走）。 */
    private boolean stripGrab;
    /** 滑块当前所在的矩形（drawStrip 里更新），用于判断手指有没有抓到滑块。 */
    private final RectF stripThumbRect = new RectF();
    private float stripLastY;
    private float stripAcc;
    private float stripVel;
    private long stripLastAt;
    private float stripTouchY;
    private int stripTicks;
    private int flingDir;
    private int flingLeft;

    /** 待发的滚轮格数（正 = 手指往上）；队列上限见 STRIP_QUEUE_MAX。 */
    private int stripQueue;
    private boolean stripQueueRunning;
    private static final int STRIP_QUEUE_MAX = 6;

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

    /**
     * 滚轮平滑队列：拖动只往 stripQueue 里加格数，这里按固定节拍一格一格发出去。
     * 以前是「一帧算几步就一口气发几步」，主机同时收到 2–3 格会跳一下，还会撞蓝牙通道上限。
     */
    private final Runnable stripQueueRun = new Runnable() {
        @Override
        public void run() {
            if (stripQueue == 0) {
                stripQueueRunning = false;
                return;
            }
            int s = stripQueue > 0 ? 1 : -1;
            stripQueue -= s;
            hid.mouseWheel(PadGestures.natural() ? -s : s);
            stripTicks++;
            if (stripTicks >= PointerSettings.STRIP_HAPTIC_EVERY) {
                stripTicks = 0;
                if (PointerSettings.stripHaptic()) haptic();
            }
            if (stripQueue != 0) {
                handler.postDelayed(this, Math.max(4, PointerSettings.stripSmoothMs()));
            } else {
                stripQueueRunning = false;
            }
        }
    };

    /** 往平滑队列里加格数（超上限丢最老的，宁可少滚一点也不要越滚越延迟）。 */
    private void enqueueStrip(int steps) {
        if (steps == 0) return;
        stripQueue += steps;
        if (stripQueue > STRIP_QUEUE_MAX) stripQueue = STRIP_QUEUE_MAX;
        if (stripQueue < -STRIP_QUEUE_MAX) stripQueue = -STRIP_QUEUE_MAX;
        if (!stripQueueRunning) {
            stripQueueRunning = true;
            handler.post(stripQueueRun);
        }
    }

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
        stripStrokePaint.setStyle(Paint.Style.STROKE);
        stripStrokePaint.setStrokeWidth(Ui.dp(context, 0.9f));
        stripStrokePaint.setColor(0x21FFFFFF);
        stripThumbStrokePaint.setStyle(Paint.Style.STROKE);
        stripThumbStrokePaint.setStrokeWidth(Ui.dp(context, 0.8f));
        stripThumbStrokePaint.setColor(0x33FFFFFF);
        stripChevPaint.setStyle(Paint.Style.STROKE);
        stripChevPaint.setStrokeCap(Paint.Cap.ROUND);
        stripChevPaint.setStrokeJoin(Paint.Join.ROUND);
        stripChevPaint.setStrokeWidth(Ui.dp(context, 1.4f));
        stripChevPaint.setColor(0x8CFFFFFF);
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

        drawStrip(canvas);

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
     * 手指是不是按在滑块上（命中区比滑块本身放宽一圈，方便抓）。
     * stripThumbRect 只在绘制时更新；还没画过时按轨道中心兜底。
     */
    private boolean stripThumbHit(float x, float y) {
        RectF hit = new RectF(stripThumbRect);
        if (hit.isEmpty()) {
            float trackW = Math.min(stripRect.width(), Ui.dp(getContext(), 56f));
            float cx = stripRect.centerX();
            float hw = Ui.dp(getContext(), 15f);
            float hh = Ui.dp(getContext(), 37f);
            hit.set(cx - hw, stripRect.centerY() - hh, cx + hw, stripRect.centerY() + hh);
            if (trackW <= 0f) return false;
        }
        float padX = Ui.dp(getContext(), 12f);
        float padY = Ui.dp(getContext(), 14f);
        hit.inset(-padX, -padY);
        // 别让命中区伸到整列外面去（否则点最上方箭头也会被判成抓滑块）
        float maxX = Math.min(stripRect.right, hit.right);
        float minY = Math.max(stripRect.top, hit.top);
        float maxY = Math.min(stripRect.bottom, hit.bottom);
        return x >= hit.left && x <= maxX && y >= minY && y <= maxY;
    }

    /**
     * 滚轮条按下（v0.39.2）：立刻发一格（点一下 = 一格，和以前一样），同时预备「按住不动
     * 就慢速连滚」；手指一旦上下拖动就切成跟手滚轮，并取消连滚。
     *
     * @param tapDir 点一下要发哪一格（上半区 = +1 往上，下半区 = -1 往下）
     * @param grab   手指是不是按在滑块上：只有 true 才会跟着手指拖动
     */
    private void beginStrip(float y, int tapDir, boolean grab) {
        PointerSettings.init(getContext());
        handler.removeCallbacks(stripFlingRun);
        flingLeft = 0;
        stripDown = true;
        stripGrab = grab;
        stripDragged = false;
        stripAcc = 0f;
        stripVel = 0f;
        stripTicks = 0;
        stripLastY = y;
        if (grab) stripTouchY = y; // 没抓住滑块就别挪它
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

    /** 滚轮条拖动：手指每走 stripStepDp 就发一格滚轮（只有抓住滑块时才跟手）。 */
    private void moveStrip(float y) {
        if (!stripGrab) return; // 点的是箭头 / 轨道空白：只保留「按住连滚」，滑块不动
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
        pressedKey = steps > 0 ? PRESS_SCROLL_UP : PRESS_SCROLL_DOWN;
        enqueueStrip(steps);
        invalidate();
    }

    /** 抬手：停连滚；开了惯性且是快速甩动时，再补几格。 */
    private void endStrip(boolean allowFling) {
        handler.removeCallbacks(scrollRepeat);
        boolean wasDown = stripDown;
        stripDown = false;
        scrollDir = 0;
        pressedKey = -1;
        if (wasDown && allowFling && stripDragged && stripVel != 0f && stripQueue == 0) {
            startStripFling(stripVel);
        }
        stripVel = 0f;
        stripDragged = false;
        stripGrab = false;
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

    /**
     * 滚轮条（v0.40.0，macOS 胶囊滑块 F 版）：磨砂胶囊轨道 + 顶部一道很淡的高光 + 上下两个细
     * chevron + 一根常驻的 30 dp 磨砂白滑块（中间三条细纹）。按住拖动时滑块变亮、变粗一点点并
     * 跟着手指走，对应方向的箭头同时提亮；断开时整体压暗。
     */
    private void drawStrip(Canvas canvas) {
        if (stripRect.isEmpty()) return;
        // 横屏时这一列很宽，轨道不能跟着变宽（会撑成一个超宽大胶囊）：轨道固定最宽 56 dp，
        // 居中放在整列里，但命中区仍然是整列，好按。
        float trackW = Math.min(stripRect.width(), Ui.dp(getContext(), 56f));
        float cx = stripRect.centerX();
        RectF track = new RectF(cx - trackW / 2f, stripRect.top, cx + trackW / 2f, stripRect.bottom);
        float radius = Ui.dp(getContext(), 16f);
        canvas.drawRoundRect(track, radius, radius, stripTrackPaint);
        canvas.drawRoundRect(track, radius, radius, stripStrokePaint);
        // 顶部一道高光，做出玻璃感
        float sheenInset = Ui.dp(getContext(), 1.2f);
        RectF sheen = new RectF(track.left + sheenInset, track.top + sheenInset,
                track.right - sheenInset, track.top + Ui.dp(getContext(), 9f));
        canvas.drawRoundRect(sheen, Ui.dp(getContext(), 8f), Ui.dp(getContext(), 8f), stripSheenPaint);

        // 只有真正抓住滑块才算「拖动中」：点箭头 / 点轨道空白时滑块一动不动，也不变白。
        boolean grabbed = stripDown && stripGrab;
        boolean connected = hid.isConnected();
        float inset = Ui.dp(getContext(), 16f);
        float chevHalf = Ui.dp(getContext(), 3.6f);
        float chevH = Ui.dp(getContext(), 2.6f);
        float topY = track.top + inset;
        float botY = track.bottom - inset;

        Path chev = new Path();
        chev.moveTo(cx - chevHalf, topY + chevH);
        chev.lineTo(cx, topY);
        chev.lineTo(cx + chevHalf, topY + chevH);
        canvas.drawPath(chev, chevronPaint(pressedKey == PRESS_SCROLL_UP, connected));
        chev.reset();
        chev.moveTo(cx - chevHalf, botY - chevH);
        chev.lineTo(cx, botY);
        chev.lineTo(cx + chevHalf, botY - chevH);
        canvas.drawPath(chev, chevronPaint(pressedKey == PRESS_SCROLL_DOWN, connected));

        // 常驻滑块：宽 30 dp、高 74 dp 的磨砂胶囊（抓住时宽 32 dp、略高，看得见"抓起来了"）
        float thumbW = Ui.dp(getContext(), grabbed ? 32f : 30f);
        float thumbH = Ui.dp(getContext(), grabbed ? 80f : 74f);
        float minCenter = topY + chevH + Ui.dp(getContext(), 4f) + thumbH / 2f;
        float maxCenter = botY - chevH - Ui.dp(getContext(), 4f) - thumbH / 2f;
        float cy = grabbed ? stripTouchY : track.centerY();
        if (cy < minCenter) cy = minCenter;
        if (cy > maxCenter) cy = maxCenter;
        RectF thumb = new RectF(cx - thumbW / 2f, cy - thumbH / 2f, cx + thumbW / 2f, cy + thumbH / 2f);
        stripThumbRect.set(thumb);
        float tr = Ui.dp(getContext(), 15f);
        Paint thumbFill = !connected ? stripThumbOffPaint : (grabbed ? stripThumbOnPaint : stripThumbPaint);
        canvas.drawRoundRect(thumb, tr, tr, thumbFill);
        canvas.drawRoundRect(thumb, tr, tr, stripThumbStrokePaint);
        // 三条细纹：一眼看出这根东西可以抓
        float gripHalf = Ui.dp(getContext(), 7f);
        float gripW = Ui.dp(getContext(), 1.2f);
        for (int i = -1; i <= 1; i++) {
            float gy = cy + i * Ui.dp(getContext(), 6f);
            RectF g = new RectF(cx - gripHalf, gy - gripW / 2f, cx + gripHalf, gy + gripW / 2f);
            canvas.drawRoundRect(g, gripW / 2f, gripW / 2f, stripGripPaint);
        }
    }

    /** chevron 的颜色 / 粗细：按下时更白更粗，未连接时整体压暗。 */
    private Paint chevronPaint(boolean on, boolean connected) {
        stripChevPaint.setStrokeWidth(Ui.dp(getContext(), on ? 2f : 1.4f));
        if (!connected) stripChevPaint.setColor(0x4DFFFFFF);
        else stripChevPaint.setColor(on ? 0xFFFFFFFF : 0x8CFFFFFF);
        return stripChevPaint;
    }

    /**
     * 调试用：把滚轮条摆成「正在拖动」的样子（pct = 0..100 手指在条上的位置），
     * 4 秒后自动复原。截图验证用，MIUI 上没法用 adb 模拟真实触摸。
     */
    public void debugShowStripActive(int pct) {
        if (stripRect.isEmpty()) return;
        int p = Math.max(0, Math.min(100, pct));
        stripDown = true;
        stripGrab = true;
        stripDragged = true;
        stripTouchY = stripRect.top + stripRect.height() * p / 100f;
        pressedKey = PRESS_SCROLL_DOWN;
        invalidate();
        handler.postDelayed(() -> {
            stripDown = false;
            stripGrab = false;
            stripDragged = false;
            pressedKey = -1;
            invalidate();
        }, 4000);
    }

    /**
     * 调试用：模拟「点了箭头」的样子 —— 箭头提亮、滑块留在中间一动不动（v0.40.0）。
     */
    public void debugShowStripTap(int dir) {
        if (stripRect.isEmpty()) return;
        stripDown = true;
        stripGrab = false;
        stripDragged = false;
        pressedKey = dir >= 0 ? PRESS_SCROLL_UP : PRESS_SCROLL_DOWN;
        invalidate();
        handler.postDelayed(() -> {
            stripDown = false;
            pressedKey = -1;
            invalidate();
        }, 4000);
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
                    // 点上半区 = 上一格，下半区 = 下一格。
                    // v0.40.0：只有手指真的落在滑块上才进入「抓着滑」；点箭头 / 点轨道空白
                    // 时滑块一动不动，只让箭头提亮（不然滑块会跟着乱跳）。
                    boolean grab = stripThumbHit(x, y);
                    beginStrip(y, y < stripRect.centerY() ? 1 : -1, grab);
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
