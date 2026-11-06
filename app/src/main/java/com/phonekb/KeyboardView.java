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

import java.util.ArrayList;
import java.util.List;

/**
 * On-screen keyboard that drives the Bluetooth HID keyboard report.
 *
 * Two switchable layouts live on the keyboard page:
 *   LAYOUT_PHONE    - phone-style keyboard (digits row, letters, symbols page, big Space/Enter)
 *   LAYOUT_COMPUTER - PC-style keyboard (shortcut chips, Tab, arrows, Ctrl/Alt/Win)
 * Both share the toolbar on top: a big "切换布局" key plus the "触控板" key.
 */
public class KeyboardView extends View {

    public static final int LAYOUT_COMPUTER = 0;
    public static final int LAYOUT_PHONE = 1;

    public static final int PAGE_LETTERS = 0;
    public static final int PAGE_SYMBOLS = 1;

    public interface Listener {
        void onLayoutChanged(int layout);
    }

    private static final int TYPE_KEY = 0;
    private static final int TYPE_MOD = 1;
    private static final int TYPE_CHIP = 2;
    private static final int TYPE_ACTION = 3;
    private static final int TYPE_SYM = 4;
    private static final int TYPE_PAGE = 5;
    private static final int TYPE_TOGGLE = 6;
    private static final int TYPE_LABEL = 7;

    private static final class Key {
        final String label;
        final int type;
        final int usage;
        final int modBit;
        final int chipMods;
        final float weight;
        final RectF rect = new RectF();

        Key(String label, int type, int usage, int modBit, int chipMods, float weight) {
            this.label = label;
            this.type = type;
            this.usage = usage;
            this.modBit = modBit;
            this.chipMods = chipMods;
            this.weight = weight;
        }
    }

    private final HidManager hid;
    private final boolean simple;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final List<Key> toolbar = new ArrayList<>();
    private final List<Key> chips = new ArrayList<>();
    private final List<Key> keys = new ArrayList<>();
    private final List<List<Key>> rows = new ArrayList<>();
    private final List<Float> rowWeights = new ArrayList<>();

    private final Paint keyPaint = Ui.fill(Ui.KEY);
    private final Paint keyDownPaint = Ui.fill(Ui.KEY_PRESSED);
    private final Paint modPaint = Ui.fill(Ui.ACCENT);
    private final Paint chipPaint = Ui.fill(Ui.SURFACE_2);
    private final Paint chipFlashPaint = Ui.fill(Ui.ACCENT);
    private final Paint labelPaint;
    private final Paint chipLabelPaint;
    private final Paint chipFlashLabelPaint;
    private final Paint darkLabelPaint;
    private final Paint toolbarPaint;
    private final Paint actionChipLabelPaint;
    private final Paint actionChipStrokePaint;
    private Runnable touchpadAction;
    private Listener layoutListener;
    private int backgroundColor = Ui.BG;
    private int layout = LAYOUT_PHONE;
    private int page = PAGE_LETTERS;
    private boolean toolbarWide;
    private final float pad;
    private final float gap;
    private final float radius;
    private final float chipH;
    private final float toolbarH;
    private final float toolbarRadius;

    private int latchMods;
    private Key activeKey;
    private Key flashChip;
    /** PC mode swaps the shortcut chip row for Windows shortcuts (Win / Win+D / Win+Shift+S ...). */
    private boolean pcMode;

    public KeyboardView(Context context, HidManager hid) {
        this(context, hid, false);
    }

    /**
     * @param simple phone-style layout without the layout toolbar, used for the quick
     *               keyboard overlay on the touchpad page and the one-hand thumb keyboard.
     */
    public KeyboardView(Context context, HidManager hid, boolean simple) {
        super(context);
        this.hid = hid;
        this.simple = simple;
        pad = Ui.dp(context, 6);
        gap = Ui.dp(context, 5);
        radius = Ui.dp(context, 8);
        chipH = Ui.dp(context, 40);
        toolbarH = Ui.dp(context, 44);
        toolbarRadius = Ui.dp(context, 10);
        labelPaint = Ui.text(context, 17, Ui.TEXT);
        chipLabelPaint = Ui.text(context, 11, Ui.TEXT_DIM);
        chipFlashLabelPaint = Ui.text(context, 11, Ui.BG);
        darkLabelPaint = Ui.text(context, 13, Ui.BG);
        toolbarPaint = Ui.text(context, 16, Ui.TEXT);
        actionChipLabelPaint = Ui.text(context, 16, Ui.ACCENT);
        actionChipStrokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        actionChipStrokePaint.setStyle(Paint.Style.STROKE);
        actionChipStrokePaint.setColor(Ui.ACCENT);
        actionChipStrokePaint.setStrokeWidth(Ui.dp(context, 1.5f));
        buildKeys();
    }

    /** Called when the user taps the 触控板 key in the toolbar. */
    public void setTouchpadAction(Runnable action) {
        this.touchpadAction = action;
    }

    public void setListener(Listener listener) {
        this.layoutListener = listener;
    }

    public void setLayout(int newLayout) {
        layout = newLayout == LAYOUT_COMPUTER ? LAYOUT_COMPUTER : LAYOUT_PHONE;
        page = PAGE_LETTERS;
        buildKeys();
        invalidate();
    }

    public int getLayout() {
        return layout;
    }

    /** PC mode only changes the shortcut chip row of the PC-style layout. */
    public void setPcMode(boolean enabled) {
        if (pcMode == enabled) return;
        pcMode = enabled;
        rebuild();
    }

    public boolean isPcMode() {
        return pcMode;
    }

    public void setPage(int newPage) {
        page = newPage == PAGE_SYMBOLS && layout == LAYOUT_PHONE ? PAGE_SYMBOLS : PAGE_LETTERS;
        buildKeys();
        invalidate();
    }

    /** Background fill behind the keys; the docked portrait keyboard panel uses Ui.SURFACE. */
    public void setPanelBackground(int color) {
        backgroundColor = color;
        invalidate();
    }

    private Key key(String label, int usage, float weight) {
        Key k = new Key(label, TYPE_KEY, usage, 0, 0, weight);
        keys.add(k);
        return k;
    }

    private Key key(String label, int usage) {
        return key(label, usage, 1f);
    }

    private Key mod(String label, int bit, float weight) {
        Key k = new Key(label, TYPE_MOD, 0, bit, 0, weight);
        keys.add(k);
        return k;
    }

    /** Symbol key: sent together with a fixed modifier (Shift for !@#$%^&*() and friends). */
    private Key sym(String label, int mods, int usage, float weight) {
        Key k = new Key(label, TYPE_SYM, usage, 0, mods, weight);
        keys.add(k);
        return k;
    }

    private Key sym(String label, int mods, int usage) {
        return sym(label, mods, usage, 1f);
    }

    /** Page key (符号 / ABC): switches the phone layout between letters and symbols. */
    private Key pageKey(String label, int targetPage, float weight) {
        Key k = new Key(label, TYPE_PAGE, targetPage, 0, 0, weight);
        keys.add(k);
        return k;
    }

    private void chip(String label, int mods, int usage) {
        chips.add(new Key(label, TYPE_CHIP, usage, 0, mods, 1f));
    }

    private void row(float weight, List<Key> r) {
        rows.add(r);
        rowWeights.add(weight);
    }

    private void buildKeys() {
        chips.clear();
        keys.clear();
        rows.clear();
        rowWeights.clear();
        activeKey = null;
        flashChip = null;
        if (simple) buildSimpleKeys();
        else if (layout == LAYOUT_COMPUTER) buildComputerKeys();
        else if (page == PAGE_SYMBOLS) buildSymbolKeys();
        else buildPhoneKeys();
        layoutAll();
    }

    private void rebuild() {
        buildKeys();
        requestLayout();
        invalidate();
    }

    /** PC-style layout: same keys as before the layout switch existed. */
    private void buildComputerKeys() {
        if (pcMode) {
            // Windows shortcuts: the chips a laptop keyboard would give you anyway.
            chip("Ctrl+C", HidConstants.MOD_LEFT_CTRL, HidConstants.KEY_C);
            chip("Ctrl+V", HidConstants.MOD_LEFT_CTRL, HidConstants.KEY_V);
            chip("Win", HidConstants.MOD_LEFT_GUI, 0);
            chip("Win+D", HidConstants.MOD_LEFT_GUI, HidConstants.KEY_D);
            chip("Alt+Tab", HidConstants.MOD_LEFT_ALT, HidConstants.KEY_TAB);
            chip("截屏", HidConstants.MOD_LEFT_GUI | HidConstants.MOD_LEFT_SHIFT, HidConstants.KEY_S);
            chip("锁屏", HidConstants.MOD_LEFT_GUI, HidConstants.KEY_L);
        } else {
            chip("Ctrl+C", HidConstants.MOD_LEFT_CTRL, HidConstants.KEY_C);
            chip("Ctrl+V", HidConstants.MOD_LEFT_CTRL, HidConstants.KEY_V);
            chip("Ctrl+X", HidConstants.MOD_LEFT_CTRL, HidConstants.KEY_X);
            chip("Ctrl+A", HidConstants.MOD_LEFT_CTRL, HidConstants.KEY_A);
            chip("Alt+Tab", HidConstants.MOD_LEFT_ALT, HidConstants.KEY_TAB);
        }

        List<Key> r1 = new ArrayList<>();
        for (int i = 0; i < 10; i++) r1.add(key(String.valueOf((i + 1) % 10), HidConstants.KEY_1 + i));
        r1.add(key("⌫", HidConstants.KEY_BACKSPACE, 1.3f));
        row(0.9f, r1);

        List<Key> r2 = new ArrayList<>();
        r2.add(key("Tab", HidConstants.KEY_TAB, 1.3f));
        int[] qwerty = {HidConstants.KEY_Q, HidConstants.KEY_W, HidConstants.KEY_E,
                HidConstants.KEY_R, HidConstants.KEY_T, HidConstants.KEY_Y,
                HidConstants.KEY_U, HidConstants.KEY_I, HidConstants.KEY_O,
                HidConstants.KEY_P};
        for (int i = 0; i < qwerty.length; i++) {
            r2.add(key(String.valueOf("qwertyuiop".charAt(i)), qwerty[i]));
        }
        r2.add(key("\\", HidConstants.KEY_BACKSLASH));
        row(1f, r2);

        List<Key> r3 = new ArrayList<>();
        int[] home = {HidConstants.KEY_A, HidConstants.KEY_S, HidConstants.KEY_D,
                HidConstants.KEY_F, HidConstants.KEY_G, HidConstants.KEY_H,
                HidConstants.KEY_J, HidConstants.KEY_K, HidConstants.KEY_L};
        for (int i = 0; i < home.length; i++) {
            r3.add(key(String.valueOf("asdfghjkl".charAt(i)), home[i]));
        }
        r3.add(key("'", HidConstants.KEY_APOSTROPHE));
        r3.add(key("Enter", HidConstants.KEY_ENTER, 1.7f));
        row(1f, r3);

        List<Key> r4 = new ArrayList<>();
        r4.add(mod("Shift", HidConstants.MOD_LEFT_SHIFT, 1.55f));
        int[] bottom = {HidConstants.KEY_Z, HidConstants.KEY_X, HidConstants.KEY_C,
                HidConstants.KEY_V, HidConstants.KEY_B, HidConstants.KEY_N,
                HidConstants.KEY_M};
        for (int i = 0; i < bottom.length; i++) {
            r4.add(key(String.valueOf("zxcvbnm".charAt(i)), bottom[i]));
        }
        r4.add(key(",", HidConstants.KEY_COMMA));
        r4.add(key(".", HidConstants.KEY_DOT));
        r4.add(key("/", HidConstants.KEY_SLASH));
        r4.add(key("↑", HidConstants.KEY_UP));
        row(1f, r4);

        List<Key> r5 = new ArrayList<>();
        r5.add(mod("Ctrl", HidConstants.MOD_LEFT_CTRL, 1.25f));
        r5.add(mod("Alt", HidConstants.MOD_LEFT_ALT, 1.1f));
        r5.add(mod("Win", HidConstants.MOD_LEFT_GUI, 1.1f));
        r5.add(key("Space", HidConstants.KEY_SPACE, 4.1f));
        r5.add(key("←", HidConstants.KEY_LEFT));
        r5.add(key("↓", HidConstants.KEY_DOWN));
        r5.add(key("→", HidConstants.KEY_RIGHT));
        row(1f, r5);
    }

    /** Phone-style layout: what a normal Android keyboard looks like. */
    private void buildPhoneKeys() {
        List<Key> r0 = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            r0.add(key(String.valueOf((i + 1) % 10), HidConstants.KEY_1 + i));
        }
        row(0.95f, r0);

        List<Key> r1 = new ArrayList<>();
        int[] qwerty = {HidConstants.KEY_Q, HidConstants.KEY_W, HidConstants.KEY_E,
                HidConstants.KEY_R, HidConstants.KEY_T, HidConstants.KEY_Y,
                HidConstants.KEY_U, HidConstants.KEY_I, HidConstants.KEY_O,
                HidConstants.KEY_P};
        for (int i = 0; i < qwerty.length; i++) {
            r1.add(key(String.valueOf("qwertyuiop".charAt(i)), qwerty[i]));
        }
        row(1f, r1);

        List<Key> r2 = new ArrayList<>();
        int[] home = {HidConstants.KEY_A, HidConstants.KEY_S, HidConstants.KEY_D,
                HidConstants.KEY_F, HidConstants.KEY_G, HidConstants.KEY_H,
                HidConstants.KEY_J, HidConstants.KEY_K, HidConstants.KEY_L};
        for (int i = 0; i < home.length; i++) {
            r2.add(key(String.valueOf("asdfghjkl".charAt(i)), home[i]));
        }
        r2.add(key("⌫", HidConstants.KEY_BACKSPACE, 1.5f));
        row(1f, r2);

        List<Key> r3 = new ArrayList<>();
        r3.add(mod("Shift", HidConstants.MOD_LEFT_SHIFT, 1.5f));
        int[] bottom = {HidConstants.KEY_Z, HidConstants.KEY_X, HidConstants.KEY_C,
                HidConstants.KEY_V, HidConstants.KEY_B, HidConstants.KEY_N,
                HidConstants.KEY_M};
        for (int i = 0; i < bottom.length; i++) {
            r3.add(key(String.valueOf("zxcvbnm".charAt(i)), bottom[i]));
        }
        r3.add(key(",", HidConstants.KEY_COMMA));
        r3.add(key(".", HidConstants.KEY_DOT));
        row(1f, r3);

        List<Key> r4 = new ArrayList<>();
        r4.add(pageKey("符号", PAGE_SYMBOLS, 1.3f));
        r4.add(key(",", HidConstants.KEY_COMMA));
        r4.add(key("Space", HidConstants.KEY_SPACE, 4.2f));
        r4.add(key(".", HidConstants.KEY_DOT));
        r4.add(key("Enter", HidConstants.KEY_ENTER, 1.8f));
        row(1.05f, r4);
    }

    /** Symbols page of the phone layout. */
    private void buildSymbolKeys() {
        int s = HidConstants.MOD_LEFT_SHIFT;

        List<Key> r0 = new ArrayList<>();
        r0.add(sym("!", s, HidConstants.KEY_1));
        r0.add(sym("@", s, HidConstants.KEY_2));
        r0.add(sym("#", s, HidConstants.KEY_3));
        r0.add(sym("$", s, HidConstants.KEY_4));
        r0.add(sym("%", s, HidConstants.KEY_5));
        r0.add(sym("^", s, HidConstants.KEY_6));
        r0.add(sym("&", s, HidConstants.KEY_7));
        r0.add(sym("*", s, HidConstants.KEY_8));
        r0.add(sym("(", s, HidConstants.KEY_9));
        r0.add(sym(")", s, HidConstants.KEY_0));
        row(1f, r0);

        List<Key> r1 = new ArrayList<>();
        r1.add(sym("-", 0, HidConstants.KEY_MINUS));
        r1.add(sym("_", s, HidConstants.KEY_MINUS));
        r1.add(sym("=", 0, HidConstants.KEY_EQUAL));
        r1.add(sym("+", s, HidConstants.KEY_EQUAL));
        r1.add(sym("[", 0, HidConstants.KEY_LEFT_BRACKET));
        r1.add(sym("]", 0, HidConstants.KEY_RIGHT_BRACKET));
        r1.add(sym("{", s, HidConstants.KEY_LEFT_BRACKET));
        r1.add(sym("}", s, HidConstants.KEY_RIGHT_BRACKET));
        r1.add(sym(";", 0, HidConstants.KEY_SEMICOLON));
        r1.add(sym(":", s, HidConstants.KEY_SEMICOLON));
        row(1f, r1);

        List<Key> r2 = new ArrayList<>();
        r2.add(sym("'", 0, HidConstants.KEY_APOSTROPHE));
        r2.add(sym("\"", s, HidConstants.KEY_APOSTROPHE));
        r2.add(sym(",", 0, HidConstants.KEY_COMMA));
        r2.add(sym(".", 0, HidConstants.KEY_DOT));
        r2.add(sym("/", 0, HidConstants.KEY_SLASH));
        r2.add(sym("\\", 0, HidConstants.KEY_BACKSLASH));
        r2.add(sym("|", s, HidConstants.KEY_BACKSLASH));
        r2.add(sym("~", s, HidConstants.KEY_GRAVE));
        r2.add(sym("<", s, HidConstants.KEY_COMMA));
        r2.add(sym(">", s, HidConstants.KEY_DOT));
        r2.add(sym("?", s, HidConstants.KEY_SLASH));
        row(1f, r2);

        List<Key> r3 = new ArrayList<>();
        r3.add(pageKey("ABC", PAGE_LETTERS, 1.6f));
        r3.add(key("⌫", HidConstants.KEY_BACKSPACE, 1.2f));
        r3.add(key("Space", HidConstants.KEY_SPACE, 4.6f));
        r3.add(key("Enter", HidConstants.KEY_ENTER, 1.8f));
        row(1f, r3);
    }

    /**
     * Phone-style layout for the quick keyboard overlay and the one-hand thumb keyboard:
     * digits row (also used to pick IME word candidates), the three letter rows with
     * shift/backspace, and a bottom row with comma, space, period and enter.
     */
    private void buildSimpleKeys() {
        List<Key> r0 = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            r0.add(key(String.valueOf((i + 1) % 10), HidConstants.KEY_1 + i));
        }
        row(0.9f, r0);

        int[] qwerty = {HidConstants.KEY_Q, HidConstants.KEY_W, HidConstants.KEY_E,
                HidConstants.KEY_R, HidConstants.KEY_T, HidConstants.KEY_Y,
                HidConstants.KEY_U, HidConstants.KEY_I, HidConstants.KEY_O,
                HidConstants.KEY_P};
        List<Key> r1 = new ArrayList<>();
        for (int i = 0; i < qwerty.length; i++) {
            r1.add(key(String.valueOf("qwertyuiop".charAt(i)), qwerty[i]));
        }
        row(1f, r1);

        int[] home = {HidConstants.KEY_A, HidConstants.KEY_S, HidConstants.KEY_D,
                HidConstants.KEY_F, HidConstants.KEY_G, HidConstants.KEY_H,
                HidConstants.KEY_J, HidConstants.KEY_K, HidConstants.KEY_L};
        List<Key> r2 = new ArrayList<>();
        for (int i = 0; i < home.length; i++) {
            r2.add(key(String.valueOf("asdfghjkl".charAt(i)), home[i]));
        }
        row(1f, r2);

        int[] bottom = {HidConstants.KEY_Z, HidConstants.KEY_X, HidConstants.KEY_C,
                HidConstants.KEY_V, HidConstants.KEY_B, HidConstants.KEY_N,
                HidConstants.KEY_M};
        List<Key> r3 = new ArrayList<>();
        r3.add(mod("⇧", HidConstants.MOD_LEFT_SHIFT, 1.4f));
        for (int i = 0; i < bottom.length; i++) {
            r3.add(key(String.valueOf("zxcvbnm".charAt(i)), bottom[i]));
        }
        r3.add(key("⌫", HidConstants.KEY_BACKSPACE, 1.4f));
        row(1f, r3);

        List<Key> r4 = new ArrayList<>();
        r4.add(key(",", HidConstants.KEY_COMMA));
        r4.add(key("Space", HidConstants.KEY_SPACE, 4.4f));
        r4.add(key(".", HidConstants.KEY_DOT));
        r4.add(key("Enter", HidConstants.KEY_ENTER, 2.2f));
        row(1f, r4);
    }

    private String layoutLabel() {
        return layout == LAYOUT_COMPUTER ? "电脑布局" : "手机布局";
    }

    @Override
    protected void onSizeChanged(int w, int h, int ow, int oh) {
        super.onSizeChanged(w, h, ow, oh);
        layoutAll();
    }

    private void layoutAll() {
        if (getWidth() <= 0 || getHeight() <= 0) return;
        if (simple) {
            toolbar.clear();
        } else {
            layoutToolbar();
        }
        float top = pad;
        if (!toolbar.isEmpty()) top += toolbarH + gap;
        if (!chips.isEmpty()) {
            layoutChips(top);
            top += chipH + gap * 2;
        }
        layoutKeys(top);
    }

    private void layoutToolbar() {
        toolbar.clear();
        toolbarWide = getWidth() > getHeight() * 1.2f;
        if (toolbarWide) {
            toolbar.add(new Key("切换布局", TYPE_TOGGLE, 0, 0, 0, 1.0f));
            toolbar.add(new Key("", TYPE_LABEL, 0, 0, 0, 1.7f));
            toolbar.add(new Key("触控板", TYPE_ACTION, 0, 0, 0, 1.0f));
        } else {
            toolbar.add(new Key("切换布局", TYPE_TOGGLE, 0, 0, 0, 1.05f));
            toolbar.add(new Key("触控板", TYPE_ACTION, 0, 0, 0, 1.0f));
        }
        int n = toolbar.size();
        float usable = getWidth() - pad * 2 - gap * (n - 1);
        float totalWeight = 0;
        for (Key k : toolbar) totalWeight += k.weight;
        float unit = usable / totalWeight;
        float x = pad;
        for (Key k : toolbar) {
            float kw = unit * k.weight;
            k.rect.set(x, pad, x + kw, pad + toolbarH);
            x += kw + gap;
        }
    }

    private void layoutChips(float top) {
        if (chips.isEmpty()) return;
        int n = chips.size();
        float usable = getWidth() - pad * 2 - gap * (n - 1);
        float cw = usable / n;
        for (int i = 0; i < n; i++) {
            float left = pad + i * (cw + gap);
            chips.get(i).rect.set(left, top, left + cw, top + chipH);
        }
    }

    private void layoutKeys(float top) {
        float height = getHeight() - top - pad;
        float totalWeight = 0;
        for (float f : rowWeights) totalWeight += f;
        if (totalWeight <= 0) return;
        float unitH = (height - gap * (rows.size() - 1)) / totalWeight;
        float y = top;
        for (int r = 0; r < rows.size(); r++) {
            List<Key> rowKeys = rows.get(r);
            float rowH = unitH * rowWeights.get(r);
            float units = 0;
            for (Key k : rowKeys) units += k.weight;
            float usable = getWidth() - pad * 2 - gap * (rowKeys.size() - 1);
            float u = usable / units;
            float x = pad;
            for (Key k : rowKeys) {
                float kw = u * k.weight;
                k.rect.set(x, y, x + kw, y + rowH);
                x += kw + gap;
            }
            y += rowH + gap;
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        canvas.drawColor(backgroundColor);
        float subOffset = Ui.dp(getContext(), 9);
        for (Key k : toolbar) {
            if (k.type == TYPE_LABEL) {
                drawFitted(canvas, "当前：" + layoutLabel(), k.rect.centerX(), k.rect.centerY(),
                        chipLabelPaint, k.rect.width() - Ui.dp(getContext(), 6));
                continue;
            }
            boolean flash = k == flashChip;
            canvas.drawRoundRect(k.rect, toolbarRadius, toolbarRadius,
                    flash ? chipFlashPaint : chipPaint);
            if (k.type == TYPE_ACTION && !flash) {
                canvas.drawRoundRect(k.rect, toolbarRadius, toolbarRadius, actionChipStrokePaint);
            }
            float maxW = k.rect.width() - Ui.dp(getContext(), 6);
            if (k.type == TYPE_TOGGLE) {
                if (toolbarWide) {
                    drawFitted(canvas, k.label, k.rect.centerX(), k.rect.centerY(),
                            toolbarPaint, maxW);
                } else {
                    drawFitted(canvas, k.label, k.rect.centerX(), k.rect.centerY() - subOffset,
                            toolbarPaint, maxW);
                    drawFitted(canvas, "当前：" + layoutLabel(), k.rect.centerX(),
                            k.rect.centerY() + subOffset, chipLabelPaint, maxW);
                }
            } else {
                drawFitted(canvas, k.label, k.rect.centerX(), k.rect.centerY(),
                        flash ? chipFlashLabelPaint : actionChipLabelPaint, maxW);
            }
        }
        for (Key k : chips) {
            boolean flash = k == flashChip;
            canvas.drawRoundRect(k.rect, radius, radius, flash ? chipFlashPaint : chipPaint);
            drawFitted(canvas, k.label, k.rect.centerX(), k.rect.centerY(),
                    flash ? chipFlashLabelPaint : chipLabelPaint,
                    k.rect.width() - Ui.dp(getContext(), 4));
        }
        float baseSize = labelPaint.getTextSize();
        float darkBase = darkLabelPaint.getTextSize();
        float minKeyLabel = Ui.dp(getContext(), 7);
        for (Key k : keys) {
            boolean latched = k.type == TYPE_MOD && (latchMods & k.modBit) != 0;
            boolean pressed = k == activeKey;
            Paint fill = latched ? modPaint : (pressed ? keyDownPaint : keyPaint);
            canvas.drawRoundRect(k.rect, radius, radius, fill);
            Paint text = latched ? darkLabelPaint : labelPaint;
            int len = k.label.length();
            if (len == 2) labelPaint.setTextSize(Ui.dp(getContext(), 15));
            else if (len > 2) labelPaint.setTextSize(Ui.dp(getContext(), 13));
            Ui.fitText(text, k.label, k.rect.width() - Ui.dp(getContext(), 5), minKeyLabel);
            Ui.textCenter(canvas, k.label, k.rect.centerX(), k.rect.centerY(), text);
            if (text != labelPaint) text.setTextSize(darkBase);
            labelPaint.setTextSize(baseSize);
        }
    }

    /** Draws centered text, shrinking the paint when the label is wider than {@code maxWidth}. */
    private void drawFitted(Canvas canvas, String label, float cx, float cy, Paint paint,
            float maxWidth) {
        float size = paint.getTextSize();
        Ui.fitText(paint, label, maxWidth, Math.max(Ui.dp(getContext(), 7), size * 0.55f));
        Ui.textCenter(canvas, label, cx, cy, paint);
        paint.setTextSize(size);
    }

    private Key hit(float x, float y) {
        for (Key k : toolbar) if (k.rect.contains(x, y)) return k;
        for (Key k : chips) if (k.rect.contains(x, y)) return k;
        for (Key k : keys) if (k.rect.contains(x, y)) return k;
        return null;
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN: {
                Key k = hit(e.getX(), e.getY());
                if (k == null) return true;
                performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
                switch (k.type) {
                    case TYPE_TOGGLE:
                        layout = layout == LAYOUT_COMPUTER ? LAYOUT_PHONE : LAYOUT_COMPUTER;
                        page = PAGE_LETTERS;
                        rebuild();
                        if (layoutListener != null) layoutListener.onLayoutChanged(layout);
                        return true;
                    case TYPE_LABEL:
                        return true;
                    case TYPE_PAGE:
                        page = k.usage;
                        rebuild();
                        return true;
                    case TYPE_ACTION:
                        flashChip = k;
                        if (touchpadAction != null) touchpadAction.run();
                        handler.postDelayed(() -> {
                            flashChip = null;
                            invalidate();
                        }, 150);
                        invalidate();
                        return true;
                    case TYPE_CHIP:
                        flashChip = k;
                        hid.tapCombo(k.chipMods, k.usage);
                        handler.postDelayed(() -> {
                            flashChip = null;
                            invalidate();
                        }, 150);
                        invalidate();
                        return true;
                    case TYPE_MOD:
                        latchMods ^= k.modBit;
                        invalidate();
                        return true;
                    case TYPE_SYM:
                    case TYPE_KEY:
                    default:
                        activeKey = k;
                        int mods = k.type == TYPE_SYM ? (latchMods | k.chipMods) : latchMods;
                        hid.keyboardPress(mods, k.usage);
                        latchMods = 0;
                        invalidate();
                        return true;
                }
            }
            case MotionEvent.ACTION_MOVE: {
                if (activeKey != null && !activeKey.rect.contains(e.getX(), e.getY())) {
                    releaseKey();
                }
                return true;
            }
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL: {
                releaseKey();
                return true;
            }
            default:
                return true;
        }
    }

    private void releaseKey() {
        if (activeKey == null) return;
        activeKey = null;
        hid.keyboardRelease();
        invalidate();
    }
}
