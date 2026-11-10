package com.phonekb;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * Trackpad gesture switches (v0.39.0). Everything here is independent of the pointer feel
 * settings in {@link PointerSettings}: that class owns speed / acceleration / report cadence,
 * this one owns which gestures the touch surface recognises.
 *
 * <p>Defaults follow what a normal laptop trackpad does: tap-and-drag, drag lock, fling, edge
 * push and palm rejection are on; the gesture that can misfire in games (pinch, four fingers)
 * starts off.
 */
public final class PadGestures {
    /** Middle key of the touchpad page sends the mouse middle button. */
    public static final int MIDDLE_MOUSE = 0;
    /** Middle key sends the Android back usage (tablet mode). */
    public static final int MIDDLE_BACK = 1;
    /** Middle key sends the Android home usage (tablet mode). */
    public static final int MIDDLE_HOME = 2;

    private static final String PREFS = "phonekb";
    private static final String K_ON = "pg_on";
    private static final String K_TAP_DRAG = "pg_tapdrag";
    private static final String K_DRAG_LOCK = "pg_draglock";
    private static final String K_FLING = "pg_fling";
    private static final String K_NATURAL = "pg_natural";
    private static final String K_EDGE = "pg_edge";
    private static final String K_PALM = "pg_palm";
    private static final String K_PINCH = "pg_pinch";
    private static final String K_PC_HSCROLL = "pg_pc_hscroll";
    private static final String K_PC_BACK = "pg_pc_back";
    private static final String K_THREE = "pg_three";
    private static final String K_FOUR = "pg_four";
    private static final String K_MIDDLE = "pg_middle";
    /** Written by {@code MainActivity.setPcMode}: 1 = 电脑模式. Only read here. */
    private static final String K_MODE = "device_mode";

    private static boolean loaded;
    private static boolean on = true;
    private static boolean tapDrag = true;
    private static boolean dragLock = true;
    private static boolean fling = true;
    private static boolean natural = false;
    private static boolean edge = true;
    private static boolean palm = true;
    private static boolean pinch = false;
    private static boolean pcHScroll = true;
    private static boolean pcBack = true;
    private static boolean three = true;
    private static boolean four = false;
    private static int middle = MIDDLE_MOUSE;

    private PadGestures() {
    }

    public static void init(Context context) {
        if (loaded || context == null) return;
        loaded = true;
        SharedPreferences p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        on = p.getBoolean(K_ON, true);
        tapDrag = p.getBoolean(K_TAP_DRAG, true);
        dragLock = p.getBoolean(K_DRAG_LOCK, true);
        fling = p.getBoolean(K_FLING, true);
        natural = p.getBoolean(K_NATURAL, false);
        edge = p.getBoolean(K_EDGE, true);
        palm = p.getBoolean(K_PALM, true);
        pinch = p.getBoolean(K_PINCH, false);
        pcHScroll = p.getBoolean(K_PC_HSCROLL, true);
        pcBack = p.getBoolean(K_PC_BACK, true);
        three = p.getBoolean(K_THREE, true);
        four = p.getBoolean(K_FOUR, false);
        middle = clampMiddle(p.getInt(K_MIDDLE, MIDDLE_MOUSE));
    }

    /** True when 电脑模式 is active (keyboard / media / touchpad gestures talk to Windows). */
    public static boolean pcMode(Context context) {
        if (context == null) return false;
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getInt(K_MODE, 0) == 1;
    }

    public static boolean on() { return on; }
    public static boolean tapDrag() { return on && tapDrag; }
    public static boolean dragLock() { return on && dragLock; }
    public static boolean fling() { return on && fling; }
    public static boolean natural() { return natural; }
    public static boolean edge() { return on && edge; }
    public static boolean palm() { return on && palm; }
    public static boolean pinch() { return on && pinch; }
    public static boolean pcHScroll() { return on && pcHScroll; }
    public static boolean pcBack() { return on && pcBack; }
    public static boolean three() { return on && three; }
    public static boolean four() { return on && four; }
    public static int middle() { return middle; }

    public static String middleLabel(Context context) {
        int m = middle();
        if (pcMode(context)) return "中键";
        if (m == MIDDLE_BACK) return "返回";
        if (m == MIDDLE_HOME) return "主页";
        return "中键";
    }

    public static void setOn(Context context, boolean value) { on = value; put(context, K_ON, value); }
    public static void setTapDrag(Context context, boolean value) { tapDrag = value; put(context, K_TAP_DRAG, value); }
    public static void setDragLock(Context context, boolean value) { dragLock = value; put(context, K_DRAG_LOCK, value); }
    public static void setFling(Context context, boolean value) { fling = value; put(context, K_FLING, value); }
    public static void setNatural(Context context, boolean value) { natural = value; put(context, K_NATURAL, value); }
    public static void setEdge(Context context, boolean value) { edge = value; put(context, K_EDGE, value); }
    public static void setPalm(Context context, boolean value) { palm = value; put(context, K_PALM, value); }
    public static void setPinch(Context context, boolean value) { pinch = value; put(context, K_PINCH, value); }
    public static void setPcHScroll(Context context, boolean value) { pcHScroll = value; put(context, K_PC_HSCROLL, value); }
    public static void setPcBack(Context context, boolean value) { pcBack = value; put(context, K_PC_BACK, value); }
    public static void setThree(Context context, boolean value) { three = value; put(context, K_THREE, value); }
    public static void setFour(Context context, boolean value) { four = value; put(context, K_FOUR, value); }

    public static void setMiddle(Context context, int value) {
        middle = clampMiddle(value);
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putInt(K_MIDDLE, middle).apply();
    }

    /** Puts every switch back to its factory value. */
    public static void resetAll(Context context) {
        setOn(context, true);
        setTapDrag(context, true);
        setDragLock(context, true);
        setFling(context, true);
        setNatural(context, false);
        setEdge(context, true);
        setPalm(context, true);
        setPinch(context, false);
        setPcHScroll(context, true);
        setPcBack(context, true);
        setThree(context, true);
        setFour(context, false);
        setMiddle(context, MIDDLE_MOUSE);
    }

    private static void put(Context context, String key, boolean value) {
        if (context == null) return;
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putBoolean(key, value).apply();
    }

    private static int clampMiddle(int value) {
        if (value < MIDDLE_MOUSE) return MIDDLE_MOUSE;
        if (value > MIDDLE_HOME) return MIDDLE_HOME;
        return value;
    }
}
