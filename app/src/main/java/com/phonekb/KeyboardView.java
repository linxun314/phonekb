package com.phonekb;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Log;
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
    /** Phone layout with a 3×4 pinyin T9 pad: touch a pad, its letters pop out, slide to pick. */
    public static final int LAYOUT_PHONE_T9 = 2;

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

    private static final String TAG = "PhoneKB-KB";
    /** Same key pressed twice inside this window is treated as one press (finger tremor / double tap). */
    private static final long KEY_DEBOUNCE_MS = 30;
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
        /** T9 pads only: the letters behind this digit ("abc", "pqrs" …); null elsewhere. */
        final String letters;
        /** T9 pads only: the digit usage sent when the pad is held down. */
        final int digitUsage;
        final RectF rect = new RectF();

        Key(String label, int type, int usage, int modBit, int chipMods, float weight) {
            this(label, type, usage, modBit, chipMods, weight, null, 0);
        }

        Key(String label, int type, int usage, int modBit, int chipMods, float weight,
                String letters, int digitUsage) {
            this.label = label;
            this.type = type;
            this.usage = usage;
            this.modBit = modBit;
            this.chipMods = chipMods;
            this.weight = weight;
            this.letters = letters;
            this.digitUsage = digitUsage;
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

    /** T9 stays open this long before a held pad turns into its digit. */
    private static final long T9_LONG_MS = 420;
    /** Touch tolerance: a press survives the finger wobbling this far outside the key rect. */
    private final float slop;
    private int latchMods;
    private Key activeKey;
    private Key flashChip;
    /** Debug: --ei keylog 1 logs every hit / move-out / release with coordinates (tag PhoneKB-KB). */
    private boolean keyLog;
    private int lastUsage = -1;
    private long lastKeyAt;
    /** PC mode swaps the shortcut chip row for Windows shortcuts (Win / Win+D / Win+Shift+S ...). */
    private boolean pcMode;

    // ---- pinyin 9-key (T9) pad -------------------------------------------------
    private Key t9Key;
    private String t9Letters;
    private int t9Index;
    private float t9StartX;
    private boolean t9DigitFired;
    private final RectF t9Popup = new RectF();
    private final List<RectF> t9Cells = new ArrayList<>();
    private Paint t9PanelPaint;
    private Paint t9PickPaint;
    private Paint t9StrokePaint;
    /** Holding a T9 pad sends its digit instead of a letter (so digits stay reachable). */
    private final Runnable t9LongPress;

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
        slop = Ui.dp(context, 9);
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
        t9PanelPaint = Ui.fill(Ui.SURFACE_2);
        t9PickPaint = Ui.fill(Ui.ACCENT);
        t9StrokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        t9StrokePaint.setStyle(Paint.Style.STROKE);
        t9StrokePaint.setColor(Ui.ACCENT);
        t9StrokePaint.setStrokeWidth(Ui.dp(context, 1.2f));
        t9LongPress = () -> {
            if (t9Key == null) return;
            t9DigitFired = true;
            lastUsage = t9Key.digitUsage;
            lastKeyAt = SystemClock.uptimeMillis();
            hid.tapKey(0, t9Key.digitUsage);
            performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
            if (keyLog) Log.i(TAG, "t9 digit usage=" + t9Key.digitUsage);
        };
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
        layout = newLayout == LAYOUT_COMPUTER || newLayout == LAYOUT_PHONE_T9
                ? newLayout : LAYOUT_PHONE;
        page = PAGE_LETTERS;
        buildKeys();
        invalidate();
    }

    /** Toolbar 「切换布局」 cycles 手机 26 键 → 手机九键 → 电脑布局. */
    public static int nextLayout(int current) {
        if (current == LAYOUT_PHONE) return LAYOUT_PHONE_T9;
        if (current == LAYOUT_PHONE_T9) return LAYOUT_COMPUTER;
        return LAYOUT_PHONE;
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
        page = newPage == PAGE_SYMBOLS && layout != LAYOUT_COMPUTER ? PAGE_SYMBOLS : PAGE_LETTERS;
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

    /** T9 pad: carries its letters and the digit usage, {@code usage} itself is unused. */
    private Key t9(String label, String letters, int digitUsage, float weight) {
        Key k = new Key(label, TYPE_KEY, 0, 0, 0, weight, letters, digitUsage);
        keys.add(k);
        return k;
    }

    private Key t9(String label, String letters, int digitUsage) {
        return t9(label, letters, digitUsage, 1f);
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
        else if (layout == LAYOUT_PHONE_T9) buildT9Keys();
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

    /**
     * Pinyin 9-key pad (3×4): tap a pad and its letters pop out above your thumb; slide to the
     * letter you want and lift. Letters go out as plain HID key codes, so the tablet's own IME
     * still does the pinyin → 汉字 work. Holding a pad sends that digit instead.
     */
    private void buildT9Keys() {
        List<Key> r0 = new ArrayList<>();
        r0.add(key("1", HidConstants.KEY_1));
        r0.add(t9("2", "abc", HidConstants.KEY_2));
        r0.add(t9("3", "def", HidConstants.KEY_3));
        row(1f, r0);

        List<Key> r1 = new ArrayList<>();
        r1.add(t9("4", "ghi", HidConstants.KEY_4));
        r1.add(t9("5", "jkl", HidConstants.KEY_5));
        r1.add(t9("6", "mno", HidConstants.KEY_6));
        row(1f, r1);

        List<Key> r2 = new ArrayList<>();
        r2.add(t9("7", "pqrs", HidConstants.KEY_7));
        r2.add(t9("8", "tuv", HidConstants.KEY_8));
        r2.add(t9("9", "wxyz", HidConstants.KEY_9));
        row(1f, r2);

        List<Key> r3 = new ArrayList<>();
        r3.add(pageKey("符号", PAGE_SYMBOLS, 1.15f));
        r3.add(key("空格", HidConstants.KEY_SPACE, 2.5f));
        r3.add(key("⌫", HidConstants.KEY_BACKSPACE, 1.25f));
        r3.add(key("Enter", HidConstants.KEY_ENTER, 1.5f));
        row(1f, r3);
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
        if (layout == LAYOUT_COMPUTER) return "电脑布局";
        return layout == LAYOUT_PHONE_T9 ? "手机九键" : "手机布局";
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
            if (k.letters != null) {
                float lift = Ui.dp(getContext(), 9);
                drawFitted(canvas, k.label, k.rect.centerX(), k.rect.centerY() - lift,
                        labelPaint, k.rect.width() - Ui.dp(getContext(), 8));
                drawFitted(canvas, k.letters, k.rect.centerX(), k.rect.centerY() + lift,
                        chipLabelPaint, k.rect.width() - Ui.dp(getContext(), 8));
                continue;
            }
            Paint text = latched ? darkLabelPaint : labelPaint;
            int len = k.label.length();
            if (len == 2) labelPaint.setTextSize(Ui.dp(getContext(), 15));
            else if (len > 2) labelPaint.setTextSize(Ui.dp(getContext(), 13));
            Ui.fitText(text, k.label, k.rect.width() - Ui.dp(getContext(), 5), minKeyLabel);
            Ui.textCenter(canvas, k.label, k.rect.centerX(), k.rect.centerY(), text);
            if (text != labelPaint) text.setTextSize(darkBase);
            labelPaint.setTextSize(baseSize);
        }
        drawT9Popup(canvas);
    }

    /** Letter strip that pops above the T9 pad; the picked letter is highlighted. */
    private void drawT9Popup(Canvas canvas) {
        if (t9Key == null || t9Cells.isEmpty()) return;
        float r = radius * 1.4f;
        canvas.drawRoundRect(t9Popup, r, r, t9PanelPaint);
        canvas.drawRoundRect(t9Popup, r, r, t9StrokePaint);
        for (int i = 0; i < t9Cells.size(); i++) {
            RectF c = t9Cells.get(i);
            boolean pick = i == t9Index;
            canvas.drawRoundRect(c, radius, radius, pick ? t9PickPaint : keyPaint);
            drawFitted(canvas, String.valueOf(t9Letters.charAt(i)), c.centerX(), c.centerY(),
                    pick ? darkLabelPaint : labelPaint, c.width() - Ui.dp(getContext(), 2));
        }
    }

    /** Opens the letter strip above {@code k}; the first letter is preselected. */
    private void showT9Popup(Key k) {
        t9Letters = k.letters;
        t9Cells.clear();
        float cellW = Ui.dp(getContext(), 38);
        float cellH = Ui.dp(getContext(), 44);
        float cg = Ui.dp(getContext(), 4);
        int n = t9Letters.length();
        float totalW = n * cellW + (n - 1) * cg;
        float left = k.rect.centerX() - totalW / 2f;
        left = Math.max(pad, Math.min(left, getWidth() - pad - totalW));
        // Centred on the pressed pad: the thumb owns that row anyway, and it never covers the
        // row of pads below (which stays fully visible while picking).
        float top = k.rect.centerY() - cellH / 2f;
        top = Math.max(pad, Math.min(top, getHeight() - pad - cellH));
        t9Popup.set(left, top, left + totalW, top + cellH);
        for (int i = 0; i < n; i++) {
            float cx = left + i * (cellW + cg);
            t9Cells.add(new RectF(cx, top, cx + cellW, top + cellH));
        }
        t9Index = 0;
    }

    /** Sliding right moves the highlight through the pad's letters; no slide keeps the first. */
    private void t9pick(float x) {
        if (t9Cells.isEmpty()) return;
        float step = t9Cells.get(0).width() + (t9Cells.size() > 1
                ? t9Cells.get(1).left - t9Cells.get(0).right : 0f);
        int idx = Math.round((x - t9StartX) / step);
        idx = Math.max(0, Math.min(t9Cells.size() - 1, idx));
        if (idx != t9Index) {
            t9Index = idx;
            if (keyLog) Log.i(TAG, "t9 pick " + t9Letters.charAt(t9Index));
            invalidate();
        }
    }

    private void beginT9(Key k, float x) {
        t9Key = k;
        t9DigitFired = false;
        t9StartX = x;
        activeKey = k;
        showT9Popup(k);
        handler.removeCallbacks(t9LongPress);
        handler.postDelayed(t9LongPress, T9_LONG_MS);
        if (keyLog) Log.i(TAG, "t9 pad " + k.label + " letters=" + k.letters);
        invalidate();
    }

    /** Sends the picked letter (or nothing when the digit already went out on long press). */
    private void endT9() {
        handler.removeCallbacks(t9LongPress);
        Key k = t9Key;
        String letters = t9Letters;
        int idx = t9Index;
        boolean digit = t9DigitFired;
        t9Key = null;
        t9Letters = null;
        t9DigitFired = false;
        t9Cells.clear();
        t9Index = 0;
        activeKey = null;
        invalidate();
        if (k == null || letters == null || letters.isEmpty()) return;
        if (digit) return;
        int usage = HidConstants.KEY_A + (letters.charAt(idx) - 'a');
        int mods = latchMods;
        latchMods = 0;
        lastUsage = usage;
        lastKeyAt = SystemClock.uptimeMillis();
        hid.tapKey(mods, usage);
        if (keyLog) {
            Log.i(TAG, "t9 letter " + letters.charAt(idx) + " usage=" + usage + " mods=" + mods);
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

    /**
     * Exact hit first, then the nearest key within {@link #slop}. Keys are separated by a 5 dp gap
     * and thumbs regularly land in it; without the fallback that tap simply did nothing.
     */
    private Key hit(float x, float y) {
        for (Key k : toolbar) if (k.rect.contains(x, y)) return k;
        for (Key k : chips) if (k.rect.contains(x, y)) return k;
        for (Key k : keys) if (k.rect.contains(x, y)) return k;
        float best = slop;
        Key bestKey = null;
        for (Key k : keys) {
            float dx = Math.max(Math.max(k.rect.left - x, x - k.rect.right), 0f);
            float dy = Math.max(Math.max(k.rect.top - y, y - k.rect.bottom), 0f);
            float distance = (float) Math.hypot(dx, dy);
            if (distance <= best) {
                best = distance;
                bestKey = k;
            }
        }
        return bestKey;
    }

    /** True once the finger is further than {@link #slop} outside the key rect. */
    private boolean outsideSlop(RectF r, float x, float y) {
        return x < r.left - slop || x > r.right + slop || y < r.top - slop || y > r.bottom + slop;
    }

    private static String describe(Key k) {
        return k.label.isEmpty() ? ("action#" + k.type) : k.label;
    }

    /** Debug only: --ei keylog 1 turns on hit / move / release logging (tag PhoneKB-KB). */
    public void setKeyLog(boolean on) {
        keyLog = on;
    }

    /**
     * Debug only: presses the widest key, wobbles 6 dp outside its right edge (inside the 9 dp slop)
     * and releases. With the slop in place the log shows "hit" … "release" and no "move-out"; the
     * pre-v0.41 build released the key the moment the finger left the rect.
     */
    public void debugSlopTest() {
        Key widest = null;
        for (Key k : keys) {
            if (widest == null || k.rect.width() > widest.rect.width()) widest = k;
        }
        if (widest == null || widest.rect.width() <= 0) {
            Log.i(TAG, "slop test skipped: no keys laid out yet");
            return;
        }
        final float cx = widest.rect.centerX();
        final float cy = widest.rect.centerY();
        final float wobbleX = widest.rect.right + Ui.dp(getContext(), 6);
        long t = SystemClock.uptimeMillis();
        inject(MotionEvent.obtain(t, t, MotionEvent.ACTION_DOWN, cx, cy, 0));
        inject(MotionEvent.obtain(t, t + 30, MotionEvent.ACTION_MOVE, wobbleX, cy, 0));
        inject(MotionEvent.obtain(t, t + 60, MotionEvent.ACTION_UP, wobbleX, cy, 0));
        Log.i(TAG, "slop test key=" + describe(widest) + " rectRight=" + Math.round(widest.rect.right)
                + " wobbleX=" + Math.round(wobbleX));
        // Second phase: press straight into the gap under that key (no rect contains the point).
        final float gapY = widest.rect.bottom + gap * 0.5f;
        long t2 = SystemClock.uptimeMillis();
        inject(MotionEvent.obtain(t2, t2, MotionEvent.ACTION_DOWN, cx, gapY, 0));
        inject(MotionEvent.obtain(t2, t2 + 40, MotionEvent.ACTION_UP, cx, gapY, 0));
        Log.i(TAG, "gap test at y=" + Math.round(gapY) + " (key bottom=" + Math.round(widest.rect.bottom) + ")");
    }

    /** Debug only (--ei t9pop 1): opens the letter strip of "7 pqrs" so a screenshot can show it. */
    public void debugShowT9Popup() {
        for (Key k : keys) {
            if (k.letters != null) {
                t9Key = k;
                showT9Popup(k);
                t9Index = 1;
                invalidate();
                Log.i(TAG, "t9 popup shown for pad " + k.label + " letters=" + k.letters);
                return;
            }
        }
        Log.i(TAG, "t9 popup skipped: no T9 pads (layout=" + layout + ")");
    }

    /**
     * Debug only (--ei t9test 1): presses the "7 pqrs" pad, slides right one cell and lifts, so the
     * log shows "t9 pad 7 …" → "t9 pick q" → "t9 letter q usage=20".
     */
    public void debugT9Test() {
        Key pad = null;
        for (Key k : keys) {
            if (k.letters != null && "7".equals(k.label)) pad = k;
        }
        if (pad == null) {
            for (Key k : keys) if (k.letters != null) { pad = k; break; }
        }
        if (pad == null || pad.rect.width() <= 0) {
            Log.i(TAG, "t9 test skipped: no T9 pads (layout=" + layout + ")");
            return;
        }
        final float cx = pad.rect.centerX();
        final float cy = pad.rect.centerY();
        final float step = Ui.dp(getContext(), 42);
        long t = SystemClock.uptimeMillis();
        inject(MotionEvent.obtain(t, t, MotionEvent.ACTION_DOWN, cx, cy, 0));
        inject(MotionEvent.obtain(t, t + 40, MotionEvent.ACTION_MOVE, cx + step, cy, 0));
        inject(MotionEvent.obtain(t, t + 80, MotionEvent.ACTION_UP, cx + step, cy, 0));
        Log.i(TAG, "t9 test pad=" + pad.label + " stepX=" + Math.round(step));
    }

    private void inject(MotionEvent e) {
        dispatchTouchEvent(e);
        e.recycle();
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN: {
                Key k = hit(e.getX(), e.getY());
                if (k == null) {
                    if (keyLog) {
                        Log.i(TAG, "miss x=" + Math.round(e.getX()) + " y=" + Math.round(e.getY()));
                    }
                    return true;
                }
                if (keyLog) {
                    Log.i(TAG, "hit " + describe(k) + " type=" + k.type + " usage=" + k.usage
                            + " x=" + Math.round(e.getX()) + " y=" + Math.round(e.getY())
                            + " rect=" + Math.round(k.rect.left) + "," + Math.round(k.rect.top)
                            + "," + Math.round(k.rect.right) + "," + Math.round(k.rect.bottom));
                }
                performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
                switch (k.type) {
                    case TYPE_TOGGLE:
                        layout = nextLayout(layout);
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
                    default: {
                        if (k.type == TYPE_KEY && k.letters != null && layout == LAYOUT_PHONE_T9) {
                            beginT9(k, e.getX());
                            return true;
                        }
                        long now = SystemClock.uptimeMillis();
                        if (k.usage == lastUsage && now - lastKeyAt < KEY_DEBOUNCE_MS) {
                            if (keyLog) Log.i(TAG, "debounced " + describe(k));
                            return true;
                        }
                        lastUsage = k.usage;
                        lastKeyAt = now;
                        activeKey = k;
                        int mods = k.type == TYPE_SYM ? (latchMods | k.chipMods) : latchMods;
                        hid.keyboardPress(mods, k.usage);
                        latchMods = 0;
                        invalidate();
                        return true;
                    }
                }
            }
            case MotionEvent.ACTION_MOVE: {
                if (t9Key != null) {
                    t9pick(e.getX());
                    if (Math.abs(e.getX() - t9StartX) > slop) handler.removeCallbacks(t9LongPress);
                    return true;
                }
                if (activeKey != null && outsideSlop(activeKey.rect, e.getX(), e.getY())) {
                    if (keyLog) {
                        Log.i(TAG, "move-out " + describe(activeKey)
                                + " x=" + Math.round(e.getX()) + " y=" + Math.round(e.getY())
                                + " rect=" + Math.round(activeKey.rect.left) + ","
                                + Math.round(activeKey.rect.top) + ","
                                + Math.round(activeKey.rect.right) + ","
                                + Math.round(activeKey.rect.bottom));
                    }
                    releaseKey();
                }
                return true;
            }
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL: {
                if (t9Key != null) {
                    endT9();
                    return true;
                }
                releaseKey();
                return true;
            }
            default:
                return true;
        }
    }

    private void releaseKey() {
        if (activeKey == null) return;
        if (keyLog) {
            Log.i(TAG, "release " + describe(activeKey) + " held="
                    + (SystemClock.uptimeMillis() - lastKeyAt) + "ms");
        }
        activeKey = null;
        hid.keyboardRelease();
        invalidate();
    }
}
