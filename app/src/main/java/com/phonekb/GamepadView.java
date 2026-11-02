package com.phonekb;

import android.app.Dialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;

import java.util.HashMap;
import java.util.Map;

/**
 * Gamepad page, floating "handheld console" layout (PPSSPP / Dolphin style): the movement pad
 * sits in the bottom-left thumb zone, ABXY in the bottom-right, L1/R1 float at the sides and
 * SELECT/START sit in the middle below them. The middle of the page stays empty, so nothing is
 * drawn over the game picture when the tablet mirrors it.
 *
 * <p>The movement pad has two shapes: four direction buttons or a round disc that is held and
 * slid (8 directions including diagonals). Both clusters are scaled by their own continuous
 * size factor (60%-150%), adjusted from the 「移动盘」 chip in the top-left corner.
 */
public final class GamepadView extends View {
    public static final int PAD_BUTTONS = 0;
    public static final int PAD_DISC = 1;

    private static final int KIND_DPAD = 0;
    private static final int KIND_FACE = 1;
    private static final int KIND_PILL = 2;

    private static final float SCALE_MIN = 0.60f;
    private static final float SCALE_MAX = 1.50f;
    private static final float SCALE_DEFAULT = 1.00f;
    private static final int SCALE_STEPS = 90;

    /** Gamepad page touch-pad placement: off / one visible panel in the middle / the whole empty area. */
    public static final int TOUCH_OFF = 0;
    public static final int TOUCH_PANEL = 1;
    public static final int TOUCH_FULL = 2;

    private static final int MOUSE_LEFT = 1;
    private static final int MOUSE_RIGHT = 2;
    private static final float POINTER_SPEED = 1.7f;
    private static final long TAP_MAX_MS = 220;

    private static final int HAT_NEUTRAL = 0x0F;
    private static final String[] DIR_NAMES = {"上", "右上", "右", "右下", "下", "左下", "左", "左上"};

    private static final class Btn {
        final RectF rect = new RectF();
        final int bit;
        final int kind;
        final int labelColor;
        final String label;
        int fill;
        boolean pressed;
        float radius;

        Btn(int bit, int kind, String label, int fill, int labelColor) {
            this.bit = bit;
            this.kind = kind;
            this.label = label;
            this.fill = fill;
            this.labelColor = labelColor;
        }
    }

    private final HidManager hid;
    private final SharedPreferences prefs;
    private final Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint iconPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint labelPaint;
    private final Paint smallPaint;
    private final Paint chipPaint;
    private final Paint discStroke;
    private final Paint edgePaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private final Btn up = new Btn(HidConstants.GP_UP, KIND_DPAD, "", Ui.KEY, Ui.TEXT);
    private final Btn down = new Btn(HidConstants.GP_DOWN, KIND_DPAD, "", Ui.KEY, Ui.TEXT);
    private final Btn left = new Btn(HidConstants.GP_LEFT, KIND_DPAD, "", Ui.KEY, Ui.TEXT);
    private final Btn right = new Btn(HidConstants.GP_RIGHT, KIND_DPAD, "", Ui.KEY, Ui.TEXT);
    private final Btn l1 = new Btn(HidConstants.GP_L1, KIND_PILL, "L1", Ui.KEY, Ui.TEXT);
    private final Btn r1 = new Btn(HidConstants.GP_R1, KIND_PILL, "R1", Ui.KEY, Ui.TEXT);
    private final Btn x = new Btn(HidConstants.GP_X, KIND_FACE, "X", 0xFF22405F, Ui.TEXT);
    private final Btn y = new Btn(HidConstants.GP_Y, KIND_FACE, "Y", 0xFF5A4716, Ui.TEXT);
    private final Btn a = new Btn(HidConstants.GP_A, KIND_FACE, "A", 0xFF1F5136, Ui.TEXT);
    private final Btn b = new Btn(HidConstants.GP_B, KIND_FACE, "B", 0xFF5B262B, Ui.TEXT);
    private final Btn select = new Btn(HidConstants.GP_SELECT, KIND_PILL, "SELECT", Ui.KEY, Ui.TEXT);
    private final Btn start = new Btn(HidConstants.GP_START, KIND_PILL, "START", Ui.KEY, Ui.TEXT);

    private final Btn[] all = {up, down, left, right, l1, r1, x, y, a, b, select, start};
    private final Map<Integer, Btn> pointers = new HashMap<>();
    private int mask;
    private final RectF chipRect = new RectF();
    private final RectF discRect = new RectF();

    private int padType;
    private float padScale;
    private float btnScale;
    private boolean hatCompat;
    private int touchMode;
    private boolean touchMoveOnly;
    private boolean haptics;
    private final android.os.Vibrator vibrator;
    private final RectF touchRect = new RectF();
    private final RectF touchChipRect = new RectF();
    private boolean touchChipPressed;

    // touch-pad gesture state (mirrors TouchpadView so both feel identical)
    private static final int TP_NONE = 0;
    private static final int TP_PAD = 1;
    private int tpMode = TP_NONE;
    private int tpPointerId = -1;
    private boolean tpMoved;
    private boolean tpScrolled;
    private int tpMaxPointers;
    private float tpLastX;
    private float tpLastY;
    private float tpDownX;
    private float tpDownY;
    private float tpAccumX;
    private float tpAccumY;
    private float tpScrollAcc;
    private float tpLastScrollY;
    private long tpDownTime;
    private int discHat = HAT_NEUTRAL;
    private int discPointer = -1;
    private boolean landscapeLayout;
    private float padCx;
    private float padCy;
    private float padHalf;

    public GamepadView(Context c, HidManager hid) {
        super(c);
        this.hid = hid;
        this.prefs = c.getSharedPreferences("phonekb", Context.MODE_PRIVATE);
        padType = prefs.getInt("gp_pad_type", PAD_BUTTONS);
        padScale = clampScale(prefs.getFloat("gp_pad_scale", SCALE_DEFAULT));
        btnScale = clampScale(prefs.getFloat("gp_btn_scale", SCALE_DEFAULT));
        hatCompat = prefs.getBoolean("gp_hat_compat", false);
        if (hid != null) hid.setHatCompat(hatCompat);
        touchMode = clampTouchMode(prefs.getInt("gp_touch_mode", TOUCH_PANEL));
        touchMoveOnly = prefs.getBoolean("gp_touch_move_only", false);
        haptics = prefs.getBoolean("gp_haptics", true);
        vibrator = (android.os.Vibrator) c.getSystemService(Context.VIBRATOR_SERVICE);
        labelPaint = Ui.text(c, 20, Ui.TEXT);
        smallPaint = Ui.text(c, 12, Ui.TEXT);
        chipPaint = Ui.text(c, 11, Ui.TEXT);
        iconPaint.setStyle(Paint.Style.STROKE);
        iconPaint.setStrokeCap(Paint.Cap.ROUND);
        iconPaint.setStrokeJoin(Paint.Join.ROUND);
        discStroke = new Paint(Paint.ANTI_ALIAS_FLAG);
        discStroke.setStyle(Paint.Style.STROKE);
        discStroke.setColor(blend(Ui.KEY, Ui.TEXT, 0.10f));
        discStroke.setStrokeWidth(dp(1.2f));
        edgePaint.setStyle(Paint.Style.STROKE);
        edgePaint.setStrokeWidth(dp(1.2f));
    }

    private float dp(float v) {
        return Ui.dp(getContext(), v);
    }

    private int dpi(float v) {
        return Math.round(Ui.dp(getContext(), v));
    }

    private static float clampScale(float scale) {
        return Math.max(SCALE_MIN, Math.min(SCALE_MAX, scale));
    }

    public boolean isHaptics() {
        return haptics;
    }

    public void setHaptics(boolean enabled) {
        haptics = enabled;
        prefs.edit().putBoolean("gp_haptics", enabled).apply();
    }

    /** Debug helper: three 60ms pulses so adb can verify the vibration actually fires. */
    public void selfTestHaptics() {
        for (int i = 0; i < 3; i++) {
            postDelayed(() -> buzz(60), i * 300L);
        }
    }

    /** Short vibration; does not depend on the system "touch vibration" switch like performHapticFeedback. */
    private void buzz(int ms) {
        if (!haptics || vibrator == null || !vibrator.hasVibrator()) return;
        try {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                vibrator.vibrate(android.os.VibrationEffect.createOneShot(
                        ms, android.os.VibrationEffect.DEFAULT_AMPLITUDE));
            } else {
                vibrator.vibrate(ms);
            }
        } catch (Exception ignored) {
        }
    }

    private static int clampTouchMode(int mode) {
        return mode == TOUCH_FULL ? TOUCH_FULL : (mode == TOUCH_OFF ? TOUCH_OFF : TOUCH_PANEL);
    }

    private static String touchModeLabel(int mode) {
        if (mode == TOUCH_OFF) return "关";
        return mode == TOUCH_FULL ? "全屏" : "中间";
    }

    // ------------------------------------------------------------- public API

    public int getPadType() {
        return padType;
    }

    /**
     * Compatibility switch for hosts that still decode the d-pad with the descriptor cached from an
     * older pairing (every direction lands one position counter-clockwise). Enabling it makes the
     * d-pad send pre-compensated hat values so the host lands on the intended direction; the only
     * casualty is the up-left diagonal, which such hosts cannot represent.
     */
    public boolean isHatCompat() {
        return hatCompat;
    }

    public void setHatCompat(boolean enabled) {
        hatCompat = enabled;
        prefs.edit().putBoolean("gp_hat_compat", enabled).apply();
        if (hid != null) hid.setHatCompat(enabled);
        resetInput();
        invalidate();
    }

    public void setPadType(int type) {
        int next = type == PAD_DISC ? PAD_DISC : PAD_BUTTONS;
        if (next == padType) return;
        resetInput();
        padType = next;
        prefs.edit().putInt("gp_pad_type", padType).apply();
        invalidate();
    }

    public int getTouchMode() {
        return touchMode;
    }

    public void setTouchMode(int mode) {
        int next = clampTouchMode(mode);
        if (next == touchMode) return;
        touchMode = next;
        prefs.edit().putInt("gp_touch_mode", touchMode).apply();
        resetTouchPad();
        invalidate();
    }

    /** Cycles 关 → 中间 → 全屏 → 关 with the small 「触控」 chip in the top bar. */
    public void cycleTouchMode() {
        int next = touchMode == TOUCH_OFF ? TOUCH_PANEL : (touchMode == TOUCH_PANEL ? TOUCH_FULL : TOUCH_OFF);
        setTouchMode(next);
    }

    public boolean isTouchMoveOnly() {
        return touchMoveOnly;
    }

    public void setTouchMoveOnly(boolean enabled) {
        touchMoveOnly = enabled;
        prefs.edit().putBoolean("gp_touch_move_only", enabled).apply();
        resetTouchPad();
        invalidate();
    }

    /** Size factor of the movement pad itself (0.6 - 1.5), independent of the page layout. */
    public float getPadScale() {
        return padScale;
    }

    public void setPadScale(float scale) {
        float next = clampScale(scale);
        if (Math.abs(next - padScale) < 0.0005f) return;
        padScale = next;
        prefs.edit().putFloat("gp_pad_scale", padScale).apply();
        invalidate();
    }

    /** Size factor of the action keys: ABXY, L1/R1 and SELECT/START (0.6 - 1.5). */
    public float getBtnScale() {
        return btnScale;
    }

    public void setBtnScale(float scale) {
        float next = clampScale(scale);
        if (Math.abs(next - btnScale) < 0.0005f) return;
        btnScale = next;
        prefs.edit().putFloat("gp_btn_scale", btnScale).apply();
        invalidate();
    }

    private void resetTouchPad() {
        tpMode = TP_NONE;
        tpMoved = false;
        tpScrolled = false;
        tpMaxPointers = 0;
        tpAccumX = 0f;
        tpAccumY = 0f;
        tpScrollAcc = 0f;
    }

    private void resetInput() {
        resetTouchPad();
        pointers.clear();
        mask = 0;
        for (Btn btn : all) btn.pressed = false;
        if (discPointer != -1) {
            discPointer = -1;
            setDiscHat(HAT_NEUTRAL);
        }
        if (hid != null) hid.sendGamepadWithHat(0, HAT_NEUTRAL);
    }

    // ---------------------------------------------------------------- layout

    private void layout() {
        float w = getWidth();
        float h = getHeight();
        if (w <= 0f || h <= 0f) return;
        landscapeLayout = w > h * 1.2f;

        // All geometry is computed in dp space first, then converted back to pixels, because dp()
        // already includes the UI layout scale (1.15 on wide screens) - mixing raw pixels and dp
        // made the keys smaller than designed and pushed the landscape row off the bottom.
        final float unit = dp(1f);
        final float wDp = w / unit;
        final float hDp = h / unit;
        final float e = 16f;   // page edge margin (dp)
        final float be = 14f;  // bottom edge margin (dp)

        chipRect.set(dp(14), dp(10), dp(14) + dp(78), dp(10) + dp(27));

        float padBaseDp = landscapeLayout ? Math.min(wDp * 0.0665f, 58f)
                : Math.min(wDp * 0.1375f, 64f);
        padBaseDp = Math.max(36f, padBaseDp);
        float rBaseDp = landscapeLayout ? Math.min(wDp * 0.0335f, 31f)
                : Math.min(wDp * 0.069f, 30f);
        rBaseDp = Math.max(15f, rBaseDp);

        // Both thumb clusters grow from the bottom corners, so a large size would push them into
        // each other. Scale both down proportionally in that case instead of overlapping keys.
        float ps = padScale;
        float bs = btnScale;
        float limitDp = wDp / 2f - e;
        float spanDp = padBaseDp * 1.43f * ps + rBaseDp * 2.48f * bs;
        if (spanDp > limitDp && spanDp > 0f) {
            float f = limitDp / spanDp;
            ps *= f;
            bs *= f;
        }

        // --- movement pad (bottom-left thumb zone)
        float s = padBaseDp * ps * unit;
        float arm = s * 0.93f;
        padHalf = arm + s / 2f;
        padCx = dp(e) + padHalf;
        padCy = h - dp(be) - padHalf;

        float discR = padHalf * 0.98f;
        discRect.set(padCx - discR, padCy - discR, padCx + discR, padCy + discR);

        if (padType == PAD_BUTTONS) {
            up.rect.set(padCx - s / 2, padCy - arm - s / 2, padCx + s / 2, padCy - arm + s / 2);
            down.rect.set(padCx - s / 2, padCy + arm - s / 2, padCx + s / 2, padCy + arm + s / 2);
            left.rect.set(padCx - arm - s / 2, padCy - s / 2, padCx - arm + s / 2, padCy + s / 2);
            right.rect.set(padCx + arm - s / 2, padCy - s / 2, padCx + arm + s / 2, padCy + s / 2);
        }

        // --- ABXY diamond (bottom-right thumb zone)
        float r = rBaseDp * bs * unit;
        float off = r * 1.48f;
        float faceHalf = off + r;
        float faceCx = w - dp(e) - faceHalf;
        float faceCy = h - dp(e) - faceHalf;
        y.rect.set(faceCx - r, faceCy - off - r, faceCx + r, faceCy - off + r);
        x.rect.set(faceCx - off - r, faceCy - r, faceCx - off + r, faceCy + r);
        b.rect.set(faceCx + off - r, faceCy - r, faceCx + off + r, faceCy + r);
        a.rect.set(faceCx - r, faceCy + off - r, faceCx + r, faceCy + off + r);
        for (Btn btn : new Btn[]{x, y, a, b}) btn.radius = r;

        // --- shoulder keys: mid sides in portrait (thumb reach), top row in landscape
        float sw = dp(96) * bs;
        float sh = dp(38) * bs;
        float shoulderTop;
        if (landscapeLayout) {
            shoulderTop = dp(e);
        } else {
            float clusterTop = Math.min(padCy - padHalf, faceCy - faceHalf);
            // Leave room for the disc hint ("按住滑动换方向") between the shoulder row and the pad.
            shoulderTop = Math.max(dp(48), clusterTop - dp(38) - sh);
        }
        l1.rect.set(dp(e), shoulderTop, dp(e) + sw, shoulderTop + sh);
        r1.rect.set(w - dp(e) - sw, shoulderTop, w - dp(e), shoulderTop + sh);

        // In landscape the shoulder row sits at the very top, so the settings chip moves to the
        // bottom centre (free space between the two thumb clusters) instead of hiding behind L1.
        if (landscapeLayout) {
            float chipLeft = (w - dp(78)) / 2f;
            float chipTop = h - dp(be) - dp(27);
            chipRect.set(chipLeft, chipTop, chipLeft + dp(78), chipTop + dp(27));
        }

        // The 「触控」 quick toggle sits right next to the settings chip in both orientations.
        touchChipRect.set(chipRect.right + dp(8), chipRect.top,
                chipRect.right + dp(8) + dp(84), chipRect.bottom);

        // --- select / start stay in the top band (portrait: right of the settings chip;
        //     landscape: between the shoulder keys) so they can never collide with the clusters.
        float mh = dp(27) * bs;
        float mw = dp(68) * bs;
        float leftBound;
        float rightBound;
        float menuTop;
        if (landscapeLayout) {
            leftBound = dp(e) + sw + dp(12);
            rightBound = w - dp(e) - sw - dp(12);
            menuTop = (shoulderTop + sh / 2f) - mh / 2f;
        } else {
            leftBound = touchChipRect.right + dp(10);
            rightBound = w - dp(e);
            menuTop = dp(10);
        }
        float space = Math.max(dp(60), rightBound - leftBound);
        mw = Math.min(mw, (space - dp(8)) / 2f);
        float menuCx = (leftBound + rightBound) / 2f;
        select.rect.set(menuCx - dp(4) - mw, menuTop, menuCx - dp(4), menuTop + mh);
        start.rect.set(menuCx + dp(4), menuTop, menuCx + dp(4) + mw, menuTop + mh);

        // --- touch pad: one visible panel in the middle of the page, or the whole empty area
        float tpLeft;
        float tpTop;
        float tpRight;
        float tpBottom;
        if (landscapeLayout) {
            tpLeft = dp(e) + sw + dp(16);
            tpRight = w - dp(e) - sw - dp(16);
            tpTop = shoulderTop + sh + dp(10);
            tpBottom = Math.min(padCy - padHalf, faceCy - faceHalf) - dp(14);
        } else {
            tpLeft = dp(e);
            tpRight = w - dp(e);
            tpTop = select.rect.bottom + dp(12);
            tpBottom = shoulderTop - dp(10);
        }
        if (tpRight - tpLeft < dp(80) || tpBottom - tpTop < dp(60)) {
            touchRect.setEmpty();
        } else {
            touchRect.set(tpLeft, tpTop, tpRight, tpBottom);
        }
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        layout();
    }

    // ------------------------------------------------------------------ draw

    @Override
    protected void onDraw(Canvas canvas) {
        layout();
        boolean connected = hid != null && hid.isConnected();
        drawTouchLayer(canvas, connected);
        drawTouchChip(canvas, connected);
        drawSettingsChip(canvas, connected);
        if (padType == PAD_DISC) {
            drawDisc(canvas, connected);
        } else {
            for (Btn btn : new Btn[]{up, down, left, right}) drawDpadKey(canvas, btn, connected);
        }
        for (Btn btn : new Btn[]{x, y, a, b}) drawFace(canvas, btn, connected);
        for (Btn btn : new Btn[]{l1, r1, select, start}) drawPill(canvas, btn, connected);
    }

    /** Draws the 「触控」 quick toggle (关 / 中间 / 全屏) right next to the settings chip. */
    private void drawTouchChip(Canvas canvas, boolean connected) {
        boolean on = touchMode != TOUCH_OFF;
        fillPaint.setColor(touchChipPressed ? blend(Ui.SURFACE_2, Ui.ACCENT, 0.30f) : Ui.SURFACE_2);
        canvas.drawRoundRect(touchChipRect, dp(12), dp(12), fillPaint);
        if (on) {
            edgePaint.setColor(Ui.ACCENT);
            edgePaint.setAlpha(150);
            canvas.drawRoundRect(touchChipRect, dp(12), dp(12), edgePaint);
            edgePaint.setAlpha(255);
        }
        chipPaint.setColor(!connected ? Ui.TEXT_DIM : (on ? Ui.ACCENT : Ui.TEXT));
        chipPaint.setTextAlign(Paint.Align.CENTER);
        String label = "触控 " + touchModeLabel(touchMode);
        Ui.fitText(chipPaint, label, touchChipRect.width() - dp(14), dp(7));
        float ty = touchChipRect.centerY() - (chipPaint.descent() + chipPaint.ascent()) / 2f;
        canvas.drawText(label, touchChipRect.centerX(), ty, chipPaint);
    }

    /** Paints the touch pad: a visible panel in the middle, or a faint hint over the empty area. */
    private void drawTouchLayer(Canvas canvas, boolean connected) {
        if (touchMode == TOUCH_OFF || touchRect.isEmpty()) return;
        smallPaint.setTextAlign(Paint.Align.CENTER);
        if (touchMode == TOUCH_FULL) {
            float cx = touchRect.centerX();
            float cy = touchRect.centerY();
            smallPaint.setTextSize(dp(13f));
            smallPaint.setColor(blend(Ui.BG, Ui.TEXT, 0.30f));
            canvas.drawText("空白处 ＝ 触控区", cx, cy - dp(8), smallPaint);
            smallPaint.setTextSize(dp(9.5f));
            smallPaint.setColor(blend(Ui.BG, Ui.TEXT, 0.20f));
            String hint = "单指移动 · 轻点＝左键 · 双指点击＝右键 · 双指滑＝滚动";
            Ui.fitText(smallPaint, hint, touchRect.width() - dp(24), dp(7));
            canvas.drawText(hint, cx, cy + dp(12), smallPaint);
            return;
        }

        // TOUCH_PANEL: visible rounded panel with a small tag, target ring and hint text.
        fillPaint.setColor(blend(Ui.BG, Ui.SURFACE, 0.55f));
        canvas.drawRoundRect(touchRect, dp(18), dp(18), fillPaint);
        edgePaint.setColor(blend(Ui.SURFACE_2, Ui.TEXT, 0.18f));
        edgePaint.setStrokeWidth(dp(1.2f));
        canvas.drawRoundRect(touchRect, dp(18), dp(18), edgePaint);
        edgePaint.setStrokeWidth(dp(1.4f));

        float cx = touchRect.centerX();
        float cy = touchRect.centerY();
        // Landscape panels are short: fall back to a compact ring + label row so nothing spills out.
        boolean compact = touchRect.height() < dp(104);

        if (!compact) {
            smallPaint.setTextSize(dp(10f));
            smallPaint.setColor(Ui.TEXT_DIM);
            smallPaint.setTextAlign(Paint.Align.LEFT);
            canvas.drawText("触控板", touchRect.left + dp(14), touchRect.top + dp(18), smallPaint);
        }

        float ringY = compact ? cy : cy - dp(14);
        edgePaint.setColor(blend(Ui.BG, Ui.TEXT, 0.45f));
        canvas.drawCircle(cx, ringY, dp(7), edgePaint);
        fillPaint.setColor(Ui.TEXT_DIM);
        canvas.drawCircle(cx, ringY, dp(2.4f), fillPaint);

        String hint = touchMoveOnly
                ? "单指移动 · 双指滑＝滚动（只移动）"
                : "单指移动 · 轻点＝左键 · 双指点击＝右键";
        if (compact) {
            smallPaint.setTextSize(dp(12f));
            smallPaint.setColor(Ui.TEXT_DIM);
            smallPaint.setTextAlign(Paint.Align.LEFT);
            float ty = ringY - (smallPaint.descent() + smallPaint.ascent()) / 2f;
            canvas.drawText("触控区", cx + dp(16), ty, smallPaint);
            return;
        }

        smallPaint.setTextAlign(Paint.Align.CENTER);
        smallPaint.setTextSize(dp(12.5f));
        smallPaint.setColor(Ui.TEXT_DIM);
        canvas.drawText("触控区", cx, cy + dp(8), smallPaint);
        smallPaint.setTextSize(dp(9.5f));
        Ui.fitText(smallPaint, hint, touchRect.width() - dp(24), dp(7));
        canvas.drawText(hint, cx, cy + dp(26), smallPaint);
    }

    private void drawSettingsChip(Canvas canvas, boolean connected) {
        fillPaint.setColor(Ui.SURFACE_2);
        canvas.drawRoundRect(chipRect, dp(12), dp(12), fillPaint);
        chipPaint.setColor(connected ? Ui.TEXT : Ui.TEXT_DIM);
        chipPaint.setTextAlign(Paint.Align.LEFT);
        float ty = chipRect.centerY() - (chipPaint.descent() + chipPaint.ascent()) / 2f;
        canvas.drawText("移动盘", chipRect.left + dp(12), ty, chipPaint);
        iconPaint.setColor(Ui.TEXT_DIM);
        iconPaint.setStrokeWidth(dp(1.6f));
        float ax = chipRect.right - dp(14);
        float ay = chipRect.centerY() - dp(1.5f);
        Path chev = new Path();
        chev.moveTo(ax - dp(3.5f), ay - dp(2));
        chev.lineTo(ax, ay + dp(2));
        chev.lineTo(ax + dp(3.5f), ay - dp(2));
        canvas.drawPath(chev, iconPaint);
    }

    private void drawDisc(Canvas canvas, boolean connected) {
        float cx = discRect.centerX();
        float cy = discRect.centerY();
        float r = discRect.width() / 2f;
        fillPaint.setColor(Ui.KEY);
        canvas.drawCircle(cx, cy, r, fillPaint);
        canvas.drawCircle(cx, cy, r, discStroke);
        if (discHat != HAT_NEUTRAL) {
            float center = -90f + discHat * 45f;
            Path sector = new Path();
            sector.moveTo(cx, cy);
            sector.arcTo(new RectF(discRect), center - 22.5f, 45f, false);
            sector.close();
            fillPaint.setColor(blend(Ui.KEY, Ui.ACCENT, 0.30f));
            canvas.drawPath(sector, fillPaint);
        }
        fillPaint.setColor(blend(Ui.KEY, Ui.TEXT, 0.22f));
        for (int i = 0; i < 4; i++) {
            double ang = Math.toRadians(45 + i * 90);
            canvas.drawCircle(cx + (float) Math.cos(ang) * r * 0.86f,
                    cy + (float) Math.sin(ang) * r * 0.86f, dp(1.6f), fillPaint);
        }
        int lit = litDirs(discHat);
        float offset = r * 0.60f;
        float arrow = r * 0.44f;
        drawDiscChevron(canvas, cx, cy - offset, 0, arrow,
                connected ? ((lit & 1) != 0 ? Ui.ACCENT : Ui.TEXT) : Ui.TEXT_DIM);
        drawDiscChevron(canvas, cx + offset, cy, 2, arrow,
                connected ? ((lit & 2) != 0 ? Ui.ACCENT : Ui.TEXT) : Ui.TEXT_DIM);
        drawDiscChevron(canvas, cx, cy + offset, 4, arrow,
                connected ? ((lit & 4) != 0 ? Ui.ACCENT : Ui.TEXT) : Ui.TEXT_DIM);
        drawDiscChevron(canvas, cx - offset, cy, 6, arrow,
                connected ? ((lit & 8) != 0 ? Ui.ACCENT : Ui.TEXT) : Ui.TEXT_DIM);
        fillPaint.setColor(Ui.SURFACE_2);
        canvas.drawCircle(cx, cy, r * 0.15f, fillPaint);
        fillPaint.setColor(Ui.TEXT_DIM);
        canvas.drawCircle(cx, cy, r * 0.055f, fillPaint);
        smallPaint.setTextSize(dp(swTextSize()));
        smallPaint.setColor(discHat == HAT_NEUTRAL || !connected ? Ui.TEXT_DIM : Ui.ACCENT);
        String hint = discHat == HAT_NEUTRAL ? "按住滑动换方向" : "当前：" + DIR_NAMES[discHat];
        if (landscapeLayout) {
            // Landscape is short: put the hint to the right of the pad (there is plenty of empty
            // space between the two thumb clusters) instead of above it where L1 sits.
            smallPaint.setTextAlign(Paint.Align.LEFT);
            Ui.fitText(smallPaint, hint, dp(150), dp(7));
            float ty = cy - (smallPaint.descent() + smallPaint.ascent()) / 2f;
            canvas.drawText(hint, cx + r + dp(14), ty, smallPaint);
        } else {
            smallPaint.setTextAlign(Paint.Align.CENTER);
            Ui.fitText(smallPaint, hint, r * 1.9f, dp(7));
            float hintY = cy + r + dp(16);
            if (hintY > getHeight() - dp(6)) hintY = cy - r - dp(10);
            canvas.drawText(hint, cx, hintY, smallPaint);
        }
    }

    private float swTextSize() {
        return 11f * Math.max(0.85f, Math.min(1.35f, padScale));
    }

    private static int litDirs(int hat) {
        switch (hat) {
            case 0: return 1;
            case 1: return 1 | 2;
            case 2: return 2;
            case 3: return 2 | 4;
            case 4: return 4;
            case 5: return 4 | 8;
            case 6: return 8;
            case 7: return 8 | 1;
            default: return 0;
        }
    }

    private void drawDiscChevron(Canvas canvas, float cx, float cy, int dir, float size, int color) {
        iconPaint.setColor(color);
        iconPaint.setStrokeWidth(Math.max(dp(2.2f), size * 0.22f));
        Path path = new Path();
        float half = size * 0.5f;
        if (dir == 0) {
            path.moveTo(cx - half, cy + half * 0.45f);
            path.lineTo(cx, cy - half * 0.55f);
            path.lineTo(cx + half, cy + half * 0.45f);
        } else if (dir == 2) {
            path.moveTo(cx - half * 0.45f, cy - half);
            path.lineTo(cx + half * 0.55f, cy);
            path.lineTo(cx - half * 0.45f, cy + half);
        } else if (dir == 4) {
            path.moveTo(cx - half, cy - half * 0.45f);
            path.lineTo(cx, cy + half * 0.55f);
            path.lineTo(cx + half, cy - half * 0.45f);
        } else {
            path.moveTo(cx + half * 0.45f, cy - half);
            path.lineTo(cx - half * 0.55f, cy);
            path.lineTo(cx + half * 0.45f, cy + half);
        }
        canvas.drawPath(path, iconPaint);
    }

    private void drawDpadKey(Canvas canvas, Btn btn, boolean connected) {
        fillPaint.setColor(btn.pressed ? Ui.KEY_PRESSED : btn.fill);
        float corner = btn.rect.width() * 0.30f;
        canvas.drawRoundRect(btn.rect, corner, corner, fillPaint);
        RectF box = new RectF(btn.rect);
        box.inset(btn.rect.width() * 0.26f, btn.rect.height() * 0.26f);
        iconPaint.setColor(connected ? Ui.TEXT : Ui.TEXT_DIM);
        float stroke = Math.min(box.width(), box.height()) * 0.17f;
        iconPaint.setStrokeWidth(stroke);
        float cx = box.centerX();
        float cy = box.centerY();
        float half = Math.min(box.width(), box.height()) * 0.30f;
        Path path = new Path();
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

    private void drawFace(Canvas canvas, Btn btn, boolean connected) {
        fillPaint.setColor(btn.pressed ? blend(btn.fill, 0xFFFFFFFF, 0.22f) : btn.fill);
        canvas.drawCircle(btn.rect.centerX(), btn.rect.centerY(), btn.radius, fillPaint);
        labelPaint.setColor(connected ? btn.labelColor : Ui.TEXT_DIM);
        labelPaint.setTextSize(btn.radius * 0.62f);
        Ui.fitText(labelPaint, btn.label, btn.radius * 1.5f, dp(7));
        Ui.textCenter(canvas, btn.label, btn.rect.centerX(), btn.rect.centerY(), labelPaint);
    }

    private void drawPill(Canvas canvas, Btn btn, boolean connected) {
        fillPaint.setColor(btn.pressed ? Ui.KEY_PRESSED : btn.fill);
        float radius = Math.min(btn.rect.height() / 2f, dp(16));
        canvas.drawRoundRect(btn.rect, radius, radius, fillPaint);
        smallPaint.setTextAlign(Paint.Align.CENTER);
        smallPaint.setTextSize(btn.rect.height() * 0.40f);
        smallPaint.setColor(connected ? Ui.TEXT : Ui.TEXT_DIM);
        Ui.fitText(smallPaint, btn.label, btn.rect.width() - dp(10), dp(6));
        Ui.textCenter(canvas, btn.label, btn.rect.centerX(), btn.rect.centerY(), smallPaint);
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
            if (btn.kind == KIND_DPAD && padType == PAD_DISC) continue;
            if (btn.kind == KIND_FACE) {
                float dx = x - btn.rect.centerX();
                float dy = y - btn.rect.centerY();
                float limit = btn.radius * 1.06f;
                if (dx * dx + dy * dy <= limit * limit) return btn;
            } else if (btn.rect.contains(x, y)) {
                return btn;
            }
        }
        return null;
    }

    // ----------------------------------------------------------------- touch

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        int action = e.getActionMasked();
        if (action == MotionEvent.ACTION_DOWN) {
            if (chipRect.contains(e.getX(), e.getY())) {
                buzz(10);
                showSettingsDialog();
                return true;
            }
            if (touchChipRect.contains(e.getX(), e.getY())) {
                buzz(10);
                touchChipPressed = true;
                invalidate();
                return true;
            }
        }
        if (action == MotionEvent.ACTION_DOWN || action == MotionEvent.ACTION_POINTER_DOWN) {
            int index = e.getActionIndex();
            float x = e.getX(index);
            float y = e.getY(index);
            if (padType == PAD_DISC && inDisc(x, y)) {
                discPointer = e.getPointerId(index);
                updateDisc(x, y);
                return true;
            }
            Btn btn = hit(x, y);
            if (btn != null) {
                pointers.put(e.getPointerId(index), btn);
                if (!btn.pressed) {
                    btn.pressed = true;
                    mask |= btn.bit;
                    buzz(10);
                    invalidate();
                    send();
                }
                return true;
            }
            // Nothing else grabbed this finger: it belongs to the touch pad.
            if (tpMode == TP_PAD) {
                tpMaxPointers = Math.max(tpMaxPointers, e.getPointerCount());
                if (tpMaxPointers >= 2) {
                    tpLastScrollY = averageY(e);
                    tpScrollAcc = 0f;
                }
                return true;
            }
            if (touchMode != TOUCH_OFF && inTouchArea(x, y)) {
                startTouchPad(e, index);
                return true;
            }
            return true;
        }
        if (action == MotionEvent.ACTION_MOVE) {
            if (tpMode == TP_PAD) {
                handleTouchPadMove(e);
                return true;
            }
            if (discPointer != -1) {
                int idx = e.findPointerIndex(discPointer);
                if (idx >= 0) updateDisc(e.getX(idx), e.getY(idx));
            }
            return true;
        }
        if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_POINTER_UP) {
            int pointerId = e.getPointerId(e.getActionIndex());
            if (touchChipPressed && action == MotionEvent.ACTION_UP) {
                touchChipPressed = false;
                cycleTouchMode();
                buzz(10);
                return true;
            }
            if (tpMode == TP_PAD && pointerId == tpPointerId) {
                finishTouchPad();
                return true;
            }
            if (tpMode == TP_PAD) {
                // A secondary finger lifted; keep tracking the primary one.
                tpMaxPointers = Math.max(1, e.getPointerCount() - 1);
                tpLastScrollY = averageY(e);
                return true;
            }
            if (padType == PAD_DISC && pointerId == discPointer) {
                discPointer = -1;
                setDiscHat(HAT_NEUTRAL);
                invalidate();
                return true;
            }
            Btn btn = pointers.remove(pointerId);
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
            if (discPointer != -1) {
                discPointer = -1;
                setDiscHat(HAT_NEUTRAL);
            }
            resetTouchPad();
            touchChipPressed = false;
            invalidate();
            send();
            return true;
        }
        return true;
    }

    // -------------------------------------------------------------- touch pad

    private boolean inTouchArea(float x, float y) {
        if (touchMode == TOUCH_PANEL) return touchRect.contains(x, y);
        // Full mode: every empty pixel, but keep a palm guard along the screen edge.
        float guard = dp(10);
        return x > guard && y > guard && x < getWidth() - guard && y < getHeight() - guard;
    }

    private void startTouchPad(MotionEvent e, int index) {
        tpMode = TP_PAD;
        tpPointerId = e.getPointerId(index);
        tpDownX = e.getX(index);
        tpDownY = e.getY(index);
        tpLastX = tpDownX;
        tpLastY = tpDownY;
        tpDownTime = System.currentTimeMillis();
        tpMoved = false;
        tpScrolled = false;
        tpMaxPointers = e.getPointerCount();
        tpAccumX = 0f;
        tpAccumY = 0f;
        tpScrollAcc = 0f;
        tpLastScrollY = averageY(e);
        invalidate();
    }

    private void handleTouchPadMove(MotionEvent e) {
        int idx = e.findPointerIndex(tpPointerId);
        if (idx < 0) return;
        if (e.getPointerCount() >= 2) {
            float y = averageY(e);
            float dy = y - tpLastScrollY;
            tpLastScrollY = y;
            tpScrollAcc += dy;
            float step = dp(20);
            int steps = (int) (tpScrollAcc / step);
            if (steps != 0) {
                tpScrollAcc -= steps * step;
                tpScrolled = true;
                if (hid != null) hid.mouseWheel(-steps);
            }
            return;
        }
        tpMaxPointers = Math.max(tpMaxPointers, 1);
        float x = e.getX(idx);
        float y = e.getY(idx);
        tpAccumX += (x - tpLastX) * POINTER_SPEED;
        tpAccumY += (y - tpLastY) * POINTER_SPEED;
        tpLastX = x;
        tpLastY = y;
        int dx = (int) tpAccumX;
        int dy = (int) tpAccumY;
        if (dx != 0 || dy != 0) {
            tpAccumX -= dx;
            tpAccumY -= dy;
            if (hid != null) hid.mouseMove(dx, dy);
        }
        float movedX = Math.abs(x - tpDownX);
        float movedY = Math.abs(y - tpDownY);
        if (!tpMoved && (movedX > dp(6) || movedY > dp(6))) tpMoved = true;
    }

    private void finishTouchPad() {
        long held = System.currentTimeMillis() - tpDownTime;
        if (!tpMoved && !tpScrolled && held < TAP_MAX_MS && !touchMoveOnly && hid != null) {
            buzz(16);
            hid.mouseClick(tpMaxPointers >= 2 ? MOUSE_RIGHT : MOUSE_LEFT);
        }
        resetTouchPad();
        tpPointerId = -1;
        invalidate();
    }

    private float averageY(MotionEvent e) {
        float sum = 0f;
        int n = e.getPointerCount();
        for (int i = 0; i < n; i++) sum += e.getY(i);
        return n == 0 ? 0f : sum / n;
    }

    private boolean inDisc(float x, float y) {
        float dx = x - discRect.centerX();
        float dy = y - discRect.centerY();
        float r = discRect.width() / 2f;
        return dx * dx + dy * dy <= (r * 1.08f) * (r * 1.08f);
    }

    private void updateDisc(float x, float y) {
        float dx = x - discRect.centerX();
        float dy = y - discRect.centerY();
        float r = discRect.width() / 2f;
        if (Math.hypot(dx, dy) < r * 0.25f) {
            setDiscHat(HAT_NEUTRAL);
            invalidate();
            return;
        }
        double deg = Math.toDegrees(Math.atan2(dy, dx));
        int hat = (int) Math.round((deg + 90.0) / 45.0) % 8;
        if (hat < 0) hat += 8;
        if (hat != discHat) {
            setDiscHat(hat);
            buzz(10);
            invalidate();
        }
    }

    private void setDiscHat(int hat) {
        discHat = hat;
        if (hid != null) hid.sendGamepadWithHat(mask, hat);
    }

    private int countPointers(Btn btn) {
        int n = 0;
        for (Btn b : pointers.values()) {
            if (b == btn) n++;
        }
        return n;
    }

    private void send() {
        if (hid == null) return;
        if (padType == PAD_DISC) {
            hid.sendGamepadWithHat(mask, discHat);
        } else {
            hid.sendGamepad(mask);
        }
    }

    // -------------------------------------------------------------- settings

    /** Opens the 「手柄设置」 dialog (movement pad type + two independent size sliders). */
    public void showSettingsDialog() {
        final Context ctx = getContext();
        final Dialog dialog = new Dialog(ctx);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        final int originalType = padType;
        final float originalPadScale = padScale;
        final float originalBtnScale = btnScale;
        final boolean originalCompat = hatCompat;
        final int originalTouch = touchMode;
        final boolean originalMoveOnly = touchMoveOnly;
        final boolean originalHaptics = haptics;

        final boolean land = getResources().getConfiguration().orientation
                == android.content.res.Configuration.ORIENTATION_LANDSCAPE;

        LinearLayout box = new LinearLayout(ctx);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dpi(20), land ? dpi(12) : dpi(18), dpi(20), land ? dpi(10) : dpi(16));
        box.setBackground(Ui.rounded(ctx, Ui.SURFACE, 22));

        TextView title = new TextView(ctx);
        title.setText("手柄设置");
        title.setTextColor(Ui.TEXT);
        title.setTextSize(17);
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        box.addView(title);

        final int[] typeHolder = {padType};
        LinearLayout typeRow = segment(ctx, new String[]{"按键", "圆盘"}, padType, which -> {
            typeHolder[0] = which == 0 ? PAD_BUTTONS : PAD_DISC;
            setPadType(typeHolder[0]);
        });
        addOption(box, ctx, "移动盘类型", typeRow, land, 16);

        LinearLayout.LayoutParams padLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        padLp.topMargin = land ? dpi(9) : dpi(14);
        box.addView(settingsLabel(ctx, "移动盘大小"), padLp);
        box.addView(sliderRow(ctx, padScale, this::setPadScale));

        LinearLayout.LayoutParams btnLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        btnLp.topMargin = land ? dpi(7) : dpi(12);
        box.addView(settingsLabel(ctx, "按键大小（ABXY / 肩键 / 菜单键）"), btnLp);
        box.addView(sliderRow(ctx, btnScale, this::setBtnScale));

        final boolean[] compatHolder = {hatCompat};
        LinearLayout compatRow = segment(ctx, new String[]{"正常", "修正一格"}, hatCompat ? 1 : 0, which -> {
            compatHolder[0] = which == 1;
            setHatCompat(compatHolder[0]);
        });
        addOption(box, ctx, "方向键方向", compatRow, land, land ? 7 : 14);

        final int[] touchHolder = {touchMode};
        int touchIndex = touchMode == TOUCH_OFF ? 0 : (touchMode == TOUCH_FULL ? 2 : 1);
        LinearLayout touchRow = segment(ctx, new String[]{"关", "中间", "全屏"}, touchIndex, which -> {
            touchHolder[0] = which == 0 ? TOUCH_OFF : (which == 2 ? TOUCH_FULL : TOUCH_PANEL);
            setTouchMode(touchHolder[0]);
        });
        addOption(box, ctx, "手柄页触控板", touchRow, land, land ? 7 : 14);

        final boolean[] hapticHolder = {haptics};
        LinearLayout hapticRow = segment(ctx, new String[]{"开", "关"}, haptics ? 0 : 1, which -> {
            hapticHolder[0] = which == 0;
            setHaptics(hapticHolder[0]);
            if (hapticHolder[0]) buzz(12);
        });
        addOption(box, ctx, "震动反馈", hapticRow, land, land ? 7 : 12);

        final boolean[] moveHolder = {touchMoveOnly};
        LinearLayout moveRow = segment(ctx, new String[]{"完整", "只移动"}, touchMoveOnly ? 1 : 0, which -> {
            moveHolder[0] = which == 1;
            setTouchMoveOnly(moveHolder[0]);
        });
        addOption(box, ctx, "触控点击", moveRow, land, land ? 7 : 12);

        TextView hint = new TextView(ctx);
        hint.setText(land
                ? "只缩放按键本身。方向键偏一格时改成「修正一格」。"
                : "只缩放按键本身，不改页面布局。圆盘：按住滑动选方向，松手回中。\n"
                + "方向键整体偏一格（按左出下）：平板缓存了旧的蓝牙描述，重新配对可根治；临时办法改成「修正一格」。");
        hint.setTextColor(Ui.TEXT_DIM);
        hint.setTextSize(11);
        LinearLayout.LayoutParams hintLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        hintLp.topMargin = land ? dpi(6) : dpi(10);
        box.addView(hint, hintLp);

        LinearLayout buttons = new LinearLayout(ctx);
        buttons.setOrientation(LinearLayout.HORIZONTAL);
        buttons.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams buttonsLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        buttonsLp.topMargin = land ? dpi(8) : dpi(14);
        TextView cancel = new TextView(ctx);
        cancel.setText("取消");
        cancel.setTextColor(Ui.TEXT_DIM);
        cancel.setTextSize(13);
        cancel.setGravity(Gravity.CENTER);
        cancel.setPadding(dpi(14), dpi(9), dpi(14), dpi(9));
        cancel.setOnClickListener(v -> {
            setPadType(originalType);
            setPadScale(originalPadScale);
            setBtnScale(originalBtnScale);
            setHatCompat(originalCompat);
            setTouchMode(originalTouch);
            setTouchMoveOnly(originalMoveOnly);
            setHaptics(originalHaptics);
            dialog.dismiss();
        });
        TextView save = new TextView(ctx);
        save.setText("保存");
        save.setTextColor(0xFF0A1018);
        save.setTextSize(13);
        save.setTypeface(null, android.graphics.Typeface.BOLD);
        save.setGravity(Gravity.CENTER);
        save.setPadding(dpi(22), dpi(9), dpi(22), dpi(9));
        save.setBackground(Ui.rounded(ctx, Ui.ACCENT, 18));
        save.setOnClickListener(v -> {
            setPadType(typeHolder[0]);
            setHatCompat(compatHolder[0]);
            setTouchMode(touchHolder[0]);
            setTouchMoveOnly(moveHolder[0]);
            setHaptics(hapticHolder[0]);
            dialog.dismiss();
        });
        LinearLayout.LayoutParams cancelLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        cancelLp.rightMargin = dpi(8);
        buttons.addView(cancel, cancelLp);
        buttons.addView(save, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        // Landscape has very little vertical room, so the option list scrolls while the 取消/保存
        // row stays pinned at the bottom of the dialog.
        LinearLayout outer = new LinearLayout(ctx);
        outer.setOrientation(LinearLayout.VERTICAL);
        outer.setBackground(Ui.rounded(ctx, Ui.SURFACE, 22));
        outer.setPadding(dpi(20), land ? dpi(12) : dpi(18), dpi(20), land ? dpi(10) : dpi(16));
        box.setBackground(null);
        box.setPadding(0, 0, 0, 0);

        ScrollView scroller = new ScrollView(ctx);
        scroller.setFillViewport(false);
        scroller.addView(box);
        outer.addView(scroller, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));
        LinearLayout.LayoutParams pinnedLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        pinnedLp.gravity = Gravity.END;
        pinnedLp.topMargin = land ? dpi(8) : dpi(14);
        outer.addView(buttons, pinnedLp);
        dialog.setContentView(outer);
        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            window.setDimAmount(0.45f);
            window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            WindowManager.LayoutParams lp = new WindowManager.LayoutParams();
            lp.copyFrom(window.getAttributes());
            lp.width = Math.round(dp(land ? 392 : 330));
            // Landscape is only ~393dp tall: bound the window so the content scrolls instead of
            // being clipped by the screen edge.
            int screenH = getResources().getDisplayMetrics().heightPixels;
            lp.height = land ? Math.round(screenH * 0.86f) : WindowManager.LayoutParams.WRAP_CONTENT;
            window.setAttributes(lp);
        }
        dialog.show();
    }

    private interface ScalePick {
        void onScale(float scale);
    }

    private static int scaleToProgress(float scale) {
        float t = (clampScale(scale) - SCALE_MIN) / (SCALE_MAX - SCALE_MIN);
        return Math.round(t * SCALE_STEPS);
    }

    private static float progressToScale(int progress) {
        return SCALE_MIN + (SCALE_MAX - SCALE_MIN) * (progress / (float) SCALE_STEPS);
    }

    /**
     * Adds one labelled option group: stacked (label above, buttons right aligned) in portrait, and
     * on a single line in landscape where vertical room is scarce.
     */
    private void addOption(LinearLayout box, Context ctx, String label, LinearLayout seg,
                           boolean land, int topMarginDp) {
        if (land) {
            LinearLayout row = new LinearLayout(ctx);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.addView(settingsLabel(ctx, label), new LinearLayout.LayoutParams(
                    0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
            row.addView(seg, new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            lp.topMargin = dpi(topMarginDp);
            box.addView(row, lp);
            return;
        }
        LinearLayout.LayoutParams labelLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        labelLp.topMargin = dpi(topMarginDp);
        box.addView(settingsLabel(ctx, label), labelLp);
        LinearLayout.LayoutParams segLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        segLp.gravity = Gravity.END;
        segLp.topMargin = dpi(6);
        box.addView(seg, segLp);
    }

    private LinearLayout sliderRow(Context ctx, float value, ScalePick pick) {
        LinearLayout row = new LinearLayout(ctx);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        final TextView valueText = new TextView(ctx);
        valueText.setTextColor(Ui.ACCENT);
        valueText.setTextSize(13);
        valueText.setGravity(Gravity.END);
        valueText.setSingleLine(true);
        valueText.setText(Math.round(clampScale(value) * 100) + "%");
        SeekBar bar = new SeekBar(ctx);
        bar.setMax(SCALE_STEPS);
        bar.setProgressTintList(ColorStateList.valueOf(Ui.ACCENT));
        bar.setThumbTintList(ColorStateList.valueOf(Ui.ACCENT));
        bar.setProgress(scaleToProgress(value));
        bar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                float scale = progressToScale(progress);
                pick.onScale(scale);
                valueText.setText(Math.round(scale * 100) + "%");
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
            }
        });
        LinearLayout.LayoutParams barLp = new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        LinearLayout.LayoutParams valueLp = new LinearLayout.LayoutParams(dpi(46),
                LinearLayout.LayoutParams.WRAP_CONTENT);
        LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        rowLp.topMargin = dpi(2);
        row.addView(bar, barLp);
        row.addView(valueText, valueLp);
        row.setLayoutParams(rowLp);
        return row;
    }

    private TextView settingsLabel(Context ctx, String text) {
        TextView tv = new TextView(ctx);
        tv.setText(text);
        tv.setTextColor(Ui.TEXT_DIM);
        tv.setTextSize(12);
        return tv;
    }

    private interface Pick {
        void onPick(int index);
    }

    private LinearLayout segment(Context ctx, String[] labels, int selected, Pick pick) {
        LinearLayout row = new LinearLayout(ctx);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setBackground(Ui.rounded(ctx, Ui.SURFACE_2, 14));
        row.setPadding(dpi(3), dpi(3), dpi(3), dpi(3));
        TextView[] views = new TextView[labels.length];
        final int[] current = {selected};
        for (int i = 0; i < labels.length; i++) {
            final int index = i;
            TextView tv = new TextView(ctx);
            tv.setText(labels[i]);
            tv.setTextSize(13);
            tv.setGravity(Gravity.CENTER);
            tv.setPadding(dpi(14), dpi(6), dpi(14), dpi(6));
            tv.setOnClickListener(v -> {
                current[0] = index;
                for (int k = 0; k < views.length; k++) styleSegment(ctx, views[k], k == index);
                pick.onPick(index);
            });
            views[i] = tv;
            row.addView(tv);
        }
        for (int k = 0; k < views.length; k++) styleSegment(ctx, views[k], k == selected);
        return row;
    }

    private void styleSegment(Context ctx, TextView tv, boolean selected) {
        tv.setTextColor(selected ? Ui.ACCENT : Ui.TEXT_DIM);
        tv.setBackground(selected ? Ui.rounded(ctx, Ui.KEY_PRESSED, 12) : null);
    }
}
