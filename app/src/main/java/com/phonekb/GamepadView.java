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
import android.widget.SeekBar;
import android.widget.TextView;

import java.util.HashMap;
import java.util.Map;

/**
 * Gamepad page: left movement pad card, right ABXY diamond with L1/R1 and select/start.
 *
 * <p>The movement pad has two shapes: four direction buttons (the original look) or a round disc
 * that is held and slid (8 directions, including diagonals). The pad width is a continuous share
 * of the page width, adjusted from the 「移动盘」 button in the left card's top-left corner.
 */
public final class GamepadView extends View {
    public static final int PAD_BUTTONS = 0;
    public static final int PAD_DISC = 1;

    private static final int KIND_DPAD = 0;
    private static final int KIND_FACE = 1;
    private static final int KIND_PILL = 2;

    private static final float SHARE_MIN = 0.34f;
    private static final float SHARE_MAX = 0.52f;
    private static final float SHARE_DEFAULT = 0.42f;
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
    private final Paint cardPaint = Ui.fill(Ui.SURFACE);
    private final Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint iconPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint labelPaint;
    private final Paint smallPaint;
    private final Paint chipPaint;
    private final Paint discStroke;

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
    private final RectF chipRect = new RectF();
    private final RectF discRect = new RectF();

    private int padType;
    private float padShare;
    private int discHat = HAT_NEUTRAL;
    private int discPointer = -1;

    public GamepadView(Context c, HidManager hid) {
        super(c);
        this.hid = hid;
        this.prefs = c.getSharedPreferences("phonekb", Context.MODE_PRIVATE);
        padType = prefs.getInt("gp_pad_type", PAD_BUTTONS);
        padShare = clampShare(prefs.getFloat("gp_pad_share", SHARE_DEFAULT));
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
    }

    private float dp(float v) {
        return Ui.dp(getContext(), v);
    }

    private int dpi(float v) {
        return Math.round(Ui.dp(getContext(), v));
    }

    private static float clampShare(float share) {
        return Math.max(SHARE_MIN, Math.min(SHARE_MAX, share));
    }

    // ------------------------------------------------------------- public API

    public int getPadType() {
        return padType;
    }

    public void setPadType(int type) {
        int next = type == PAD_DISC ? PAD_DISC : PAD_BUTTONS;
        if (next == padType) return;
        resetInput();
        padType = next;
        prefs.edit().putInt("gp_pad_type", padType).apply();
        invalidate();
    }

    public float getPadShare() {
        return padShare;
    }

    public void setPadShare(float share) {
        float next = clampShare(share);
        if (Math.abs(next - padShare) < 0.0005f) return;
        padShare = next;
        prefs.edit().putFloat("gp_pad_share", padShare).apply();
        invalidate();
    }

    private void resetInput() {
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
        float pad = dp(10);
        float gap = dp(10);
        float w = getWidth();
        float h = getHeight();
        float leftW = (w - pad * 2 - gap) * padShare;
        leftCard.set(pad, pad, pad + leftW, h - pad);
        rightCard.set(leftCard.right + gap, pad, w - pad, h - pad);
        chipRect.set(leftCard.left + dp(8), leftCard.top + dp(8),
                leftCard.left + dp(8) + dp(74), leftCard.top + dp(8) + dp(28));

        float cx = leftCard.centerX();
        float cy = leftCard.centerY();
        if (padType == PAD_DISC) {
            float r = Math.min(leftCard.width() * 0.40f, leftCard.height() * 0.36f);
            discRect.set(cx - r, cy - r, cx + r, cy + r);
        } else {
            // D-pad cross inside the left card.
            float s = Math.min(leftCard.width() * 0.30f, leftCard.height() * 0.26f);
            float d = s * 1.06f;
            up.rect.set(cx - s / 2, cy - d - s / 2, cx + s / 2, cy - d + s / 2);
            down.rect.set(cx - s / 2, cy + d - s / 2, cx + s / 2, cy + d + s / 2);
            left.rect.set(cx - d - s / 2, cy - s / 2, cx - d + s / 2, cy + s / 2);
            right.rect.set(cx + d - s / 2, cy - s / 2, cx + d + s / 2, cy + s / 2);
        }

        // Right card: shoulder row, ABXY diamond, select/start row.
        float innerPad = dp(12);
        float shoulderH = dp(30);
        float bottomH = dp(28);
        float rowGap = dp(8);
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
        float radius = Math.min(dp(26),
                Math.min((regionBottom - regionTop) / 2.9f, rightCard.width() * 0.13f));
        float offset = radius * 1.5f;
        y.rect.set(faceCx - radius, faceCy - offset - radius, faceCx + radius, faceCy - offset + radius);
        x.rect.set(faceCx - offset - radius, faceCy - radius, faceCx - offset + radius, faceCy + radius);
        b.rect.set(faceCx + offset - radius, faceCy - radius, faceCx + offset + radius, faceCy + radius);
        a.rect.set(faceCx - radius, faceCy + offset - radius, faceCx + radius, faceCy + offset + radius);
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
        float corner = dp(22);
        canvas.drawRoundRect(leftCard, corner, corner, cardPaint);
        canvas.drawRoundRect(rightCard, corner, corner, cardPaint);
        boolean connected = hid != null && hid.isConnected();
        drawSettingsChip(canvas, connected);
        if (padType == PAD_DISC) {
            drawDisc(canvas, connected);
        } else {
            for (Btn btn : new Btn[]{up, down, left, right}) drawButton(canvas, btn, connected);
        }
        for (Btn btn : new Btn[]{l1, r1, x, y, a, b, select, start}) drawButton(canvas, btn, connected);
    }

    private void drawSettingsChip(Canvas canvas, boolean connected) {
        fillPaint.setColor(Ui.SURFACE_2);
        canvas.drawRoundRect(chipRect, dp(12), dp(12), fillPaint);
        chipPaint.setColor(connected ? Ui.TEXT : Ui.TEXT_DIM);
        chipPaint.setTextAlign(Paint.Align.LEFT);
        float ty = chipRect.centerY() - (chipPaint.descent() + chipPaint.ascent()) / 2f;
        canvas.drawText("移动盘", chipRect.left + dp(12), ty, chipPaint);
        iconPaint.setColor(connected ? Ui.TEXT_DIM : Ui.TEXT_DIM);
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
        // Diagonal hint dots.
        fillPaint.setColor(blend(Ui.KEY, Ui.TEXT, 0.22f));
        for (int i = 0; i < 4; i++) {
            double ang = Math.toRadians(45 + i * 90);
            canvas.drawCircle(cx + (float) Math.cos(ang) * r * 0.86f,
                    cy + (float) Math.sin(ang) * r * 0.86f, dp(1.6f), fillPaint);
        }
        int lit = litDirs(discHat);
        drawDiscChevron(canvas, cx, cy - r * 0.60f, 0, r * 0.44f,
                connected ? ((lit & 1) != 0 ? Ui.ACCENT : Ui.TEXT) : Ui.TEXT_DIM);
        drawDiscChevron(canvas, cx + r * 0.60f, cy, 2, r * 0.44f,
                connected ? ((lit & 2) != 0 ? Ui.ACCENT : Ui.TEXT) : Ui.TEXT_DIM);
        drawDiscChevron(canvas, cx, cy + r * 0.60f, 4, r * 0.44f,
                connected ? ((lit & 4) != 0 ? Ui.ACCENT : Ui.TEXT) : Ui.TEXT_DIM);
        drawDiscChevron(canvas, cx - r * 0.60f, cy, 6, r * 0.44f,
                connected ? ((lit & 8) != 0 ? Ui.ACCENT : Ui.TEXT) : Ui.TEXT_DIM);
        // Centre dot.
        fillPaint.setColor(Ui.SURFACE_2);
        canvas.drawCircle(cx, cy, r * 0.15f, fillPaint);
        fillPaint.setColor(Ui.TEXT_DIM);
        canvas.drawCircle(cx, cy, r * 0.055f, fillPaint);
        smallPaint.setTextAlign(Paint.Align.CENTER);
        smallPaint.setColor(discHat == HAT_NEUTRAL || !connected ? Ui.TEXT_DIM : Ui.ACCENT);
        String hint = discHat == HAT_NEUTRAL ? "按住滑动换方向" : "当前：" + DIR_NAMES[discHat];
        canvas.drawText(hint, cx, cy + r + dp(16), smallPaint);
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

    private void drawButton(Canvas canvas, Btn btn, boolean connected) {
        float pressedRadius = dp(btn.kind == KIND_DPAD ? 16 : 14);
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
            if (btn.rect.contains(x, y)) return btn;
        }
        return null;
    }

    // ----------------------------------------------------------------- touch

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        int action = e.getActionMasked();
        if (action == MotionEvent.ACTION_DOWN) {
            if (chipRect.contains(e.getX(), e.getY())) {
                performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
                showSettingsDialog();
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
                    performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
                    invalidate();
                    send();
                }
            }
            return true;
        }
        if (action == MotionEvent.ACTION_MOVE) {
            if (discPointer != -1) {
                int idx = e.findPointerIndex(discPointer);
                if (idx >= 0) updateDisc(e.getX(idx), e.getY(idx));
            }
            return true;
        }
        if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_POINTER_UP) {
            int pointerId = e.getPointerId(e.getActionIndex());
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
            invalidate();
            send();
            return true;
        }
        return true;
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
            performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
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

    /** Opens the 「手柄设置」 dialog (movement pad type + continuous size). */
    public void showSettingsDialog() {
        final Context ctx = getContext();
        final Dialog dialog = new Dialog(ctx);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        int originalType = padType;
        float originalShare = padShare;

        LinearLayout box = new LinearLayout(ctx);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dpi(20), dpi(18), dpi(20), dpi(16));
        GradientDrawable bg = Ui.rounded(ctx, Ui.SURFACE, 22);
        box.setBackground(bg);

        TextView title = new TextView(ctx);
        title.setText("手柄设置");
        title.setTextColor(Ui.TEXT);
        title.setTextSize(17);
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        box.addView(title);

        TextView typeLabel = settingsLabel(ctx, "移动盘类型");
        LinearLayout.LayoutParams labelLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        labelLp.topMargin = dpi(18);
        box.addView(typeLabel, labelLp);

        final int[] typeHolder = {padType};
        LinearLayout typeRow = segment(ctx, new String[]{"按键", "圆盘"}, padType, which -> {
            typeHolder[0] = which == 0 ? PAD_BUTTONS : PAD_DISC;
            setPadType(typeHolder[0]);
        });
        LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        rowLp.gravity = Gravity.END;
        rowLp.topMargin = dpi(6);
        box.addView(typeRow, rowLp);

        TextView sizeLabel = settingsLabel(ctx, "移动盘大小");
        LinearLayout.LayoutParams label2 = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        label2.topMargin = dpi(16);
        box.addView(sizeLabel, label2);

        LinearLayout sizeRow = new LinearLayout(ctx);
        sizeRow.setOrientation(LinearLayout.HORIZONTAL);
        sizeRow.setGravity(Gravity.CENTER_VERTICAL);
        final TextView valueText = new TextView(ctx);
        valueText.setTextColor(Ui.ACCENT);
        valueText.setTextSize(13);
        valueText.setGravity(Gravity.END);
        valueText.setSingleLine(true);
        LinearLayout.LayoutParams valueLp = new LinearLayout.LayoutParams(dpi(46),
                LinearLayout.LayoutParams.WRAP_CONTENT);
        SeekBar bar = new SeekBar(ctx);
        bar.setMax(200);
        bar.setProgressTintList(ColorStateList.valueOf(Ui.ACCENT));
        bar.setThumbTintList(ColorStateList.valueOf(Ui.ACCENT));
        bar.setProgress(shareToProgress(padShare));
        LinearLayout.LayoutParams barLp = new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        bar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                float share = progressToShare(progress);
                setPadShare(share);
                valueText.setText(Math.round(share * 100) + "%");
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
            }
        });
        valueText.setText(Math.round(padShare * 100) + "%");
        sizeRow.addView(bar, barLp);
        sizeRow.addView(valueText, valueLp);
        LinearLayout.LayoutParams sizeLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        sizeLp.topMargin = dpi(4);
        box.addView(sizeRow, sizeLp);

        TextView hint = new TextView(ctx);
        hint.setText("圆盘：按住并滑动选择方向，松手回中");
        hint.setTextColor(Ui.TEXT_DIM);
        hint.setTextSize(11);
        LinearLayout.LayoutParams hintLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        hintLp.topMargin = dpi(10);
        box.addView(hint, hintLp);

        LinearLayout buttons = new LinearLayout(ctx);
        buttons.setOrientation(LinearLayout.HORIZONTAL);
        buttons.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams buttonsLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        buttonsLp.topMargin = dpi(14);
        TextView cancel = new TextView(ctx);
        cancel.setText("取消");
        cancel.setTextColor(Ui.TEXT_DIM);
        cancel.setTextSize(13);
        cancel.setGravity(Gravity.CENTER);
        cancel.setPadding(dpi(14), dpi(9), dpi(14), dpi(9));
        cancel.setOnClickListener(v -> {
            setPadType(originalType);
            setPadShare(originalShare);
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
            dialog.dismiss();
        });
        LinearLayout.LayoutParams cancelLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        cancelLp.rightMargin = dpi(8);
        buttons.addView(cancel, cancelLp);
        buttons.addView(save, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        box.addView(buttons, buttonsLp);

        dialog.setContentView(box);
        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            window.setDimAmount(0.45f);
            window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            WindowManager.LayoutParams lp = new WindowManager.LayoutParams();
            lp.copyFrom(window.getAttributes());
            lp.width = Math.round(dp(322));
            lp.height = WindowManager.LayoutParams.WRAP_CONTENT;
            window.setAttributes(lp);
        }
        dialog.show();
    }

    private TextView settingsLabel(Context ctx, String text) {
        TextView tv = new TextView(ctx);
        tv.setText(text);
        tv.setTextColor(Ui.TEXT_DIM);
        tv.setTextSize(12);
        return tv;
    }

    private static int shareToProgress(float share) {
        float t = (clampShare(share) - SHARE_MIN) / (SHARE_MAX - SHARE_MIN);
        return Math.round(t * 200);
    }

    private static float progressToShare(int progress) {
        return SHARE_MIN + (SHARE_MAX - SHARE_MIN) * (progress / 200f);
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
