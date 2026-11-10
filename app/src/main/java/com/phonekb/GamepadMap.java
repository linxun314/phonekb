package com.phonekb;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * Per-key remapping for the gamepad page.
 *
 * <p>Drivers and games disagree about which HID button is which: a game may expect the bottom face
 * key to be "A" (Xbox), "×" (PlayStation) or "B" (Switch), and a few games ignore the trigger
 * buttons (9 / 10) completely. Instead of changing the HID descriptor - which every host caches
 * from pairing, so a change means "forget the device and pair again" - every physical key on the
 * gamepad page can be mapped to any other target: another gamepad button, a d-pad direction, a
 * keyboard key, a mouse button or a media key.
 *
 * <p>Three saved slots (配置 1 / 2 / 3) let a user keep one mapping per game without rebuilding it
 * every time. Everything is stored in the normal app preferences; no root and nothing installed on
 * the host.
 */
public final class GamepadMap {
    private GamepadMap() {}

    /** Target kinds. */
    public static final int T_NONE = 0;
    public static final int T_GAME = 1;
    public static final int T_KEY = 2;
    public static final int T_MOUSE = 3;
    public static final int T_CONSUMER = 4;

    /** Face-button layouts. */
    public static final int FACE_XBOX = 0;
    public static final int FACE_PS = 1;
    public static final int FACE_SWITCH = 2;

    public static final String[] FACE_NAMES = {"Xbox", "PS", "Switch"};

    /** One possible target of a physical key. */
    public static final class Target {
        public final int id;
        public final int type;
        public final int value;
        public final int mod;
        public final String group;
        public final String label;

        Target(int id, int type, int value, int mod, String group, String label) {
            this.id = id;
            this.type = type;
            this.value = value;
            this.mod = mod;
            this.group = group;
            this.label = label;
        }
    }

    private static final String GROUP_PAD = "手柄按键";
    private static final String GROUP_DPAD = "方向键";
    private static final String GROUP_KEY = "键盘";
    private static final String GROUP_MOUSE = "鼠标";
    private static final String GROUP_MEDIA = "媒体键";

    /** Every target the picker offers. The index in this array is the persisted id. */
    public static final Target[] TARGETS = {
            new Target(0, T_NONE, 0, 0, "关闭", "不发送"),
            new Target(1, T_GAME, HidConstants.GP_A, 0, GROUP_PAD, "手柄 A"),
            new Target(2, T_GAME, HidConstants.GP_B, 0, GROUP_PAD, "手柄 B"),
            new Target(3, T_GAME, HidConstants.GP_X, 0, GROUP_PAD, "手柄 X"),
            new Target(4, T_GAME, HidConstants.GP_Y, 0, GROUP_PAD, "手柄 Y"),
            new Target(5, T_GAME, HidConstants.GP_L1, 0, GROUP_PAD, "手柄 L1"),
            new Target(6, T_GAME, HidConstants.GP_R1, 0, GROUP_PAD, "手柄 R1"),
            new Target(7, T_GAME, HidConstants.GP_L2, 0, GROUP_PAD, "手柄 LT"),
            new Target(8, T_GAME, HidConstants.GP_R2, 0, GROUP_PAD, "手柄 RT"),
            new Target(9, T_GAME, HidConstants.GP_SELECT, 0, GROUP_PAD, "手柄 SELECT"),
            new Target(10, T_GAME, HidConstants.GP_START, 0, GROUP_PAD, "手柄 START"),
            new Target(11, T_GAME, HidConstants.GP_UP, 0, GROUP_DPAD, "方向 ↑"),
            new Target(12, T_GAME, HidConstants.GP_DOWN, 0, GROUP_DPAD, "方向 ↓"),
            new Target(13, T_GAME, HidConstants.GP_LEFT, 0, GROUP_DPAD, "方向 ←"),
            new Target(14, T_GAME, HidConstants.GP_RIGHT, 0, GROUP_DPAD, "方向 →"),
            new Target(15, T_KEY, HidConstants.KEY_ESC, 0, GROUP_KEY, "Esc"),
            new Target(16, T_KEY, HidConstants.KEY_TAB, 0, GROUP_KEY, "Tab"),
            new Target(17, T_KEY, HidConstants.KEY_ENTER, 0, GROUP_KEY, "回车"),
            new Target(18, T_KEY, HidConstants.KEY_SPACE, 0, GROUP_KEY, "空格"),
            new Target(19, T_KEY, HidConstants.KEY_W, 0, GROUP_KEY, "W"),
            new Target(20, T_KEY, HidConstants.KEY_A, 0, GROUP_KEY, "A 键"),
            new Target(21, T_KEY, HidConstants.KEY_S, 0, GROUP_KEY, "S"),
            new Target(22, T_KEY, HidConstants.KEY_D, 0, GROUP_KEY, "D"),
            new Target(23, T_KEY, HidConstants.KEY_UP, 0, GROUP_KEY, "键盘 ↑"),
            new Target(24, T_KEY, HidConstants.KEY_DOWN, 0, GROUP_KEY, "键盘 ↓"),
            new Target(25, T_KEY, HidConstants.KEY_LEFT, 0, GROUP_KEY, "键盘 ←"),
            new Target(26, T_KEY, HidConstants.KEY_RIGHT, 0, GROUP_KEY, "键盘 →"),
            new Target(27, T_KEY, HidConstants.KEY_1, 0, GROUP_KEY, "1"),
            new Target(28, T_KEY, HidConstants.KEY_2, 0, GROUP_KEY, "2"),
            new Target(29, T_KEY, HidConstants.KEY_3, 0, GROUP_KEY, "3"),
            new Target(30, T_KEY, HidConstants.KEY_4, 0, GROUP_KEY, "4"),
            new Target(31, T_KEY, HidConstants.KEY_E, 0, GROUP_KEY, "E"),
            new Target(32, T_KEY, HidConstants.KEY_R, 0, GROUP_KEY, "R"),
            new Target(33, T_KEY, HidConstants.KEY_F, 0, GROUP_KEY, "F"),
            new Target(34, T_KEY, HidConstants.KEY_Q, 0, GROUP_KEY, "Q"),
            new Target(35, T_KEY, 0, HidConstants.MOD_LEFT_SHIFT, GROUP_KEY, "Shift"),
            new Target(36, T_KEY, 0, HidConstants.MOD_LEFT_CTRL, GROUP_KEY, "Ctrl"),
            new Target(37, T_MOUSE, 1, 0, GROUP_MOUSE, "鼠标左键"),
            new Target(38, T_MOUSE, 4, 0, GROUP_MOUSE, "鼠标中键"),
            new Target(39, T_MOUSE, 2, 0, GROUP_MOUSE, "鼠标右键"),
            new Target(40, T_CONSUMER, HidConstants.CONSUMER_PLAY_PAUSE, 0, GROUP_MEDIA, "播放/暂停"),
            new Target(41, T_CONSUMER, HidConstants.CONSUMER_PREV_TRACK, 0, GROUP_MEDIA, "上一首"),
            new Target(42, T_CONSUMER, HidConstants.CONSUMER_NEXT_TRACK, 0, GROUP_MEDIA, "下一首"),
            new Target(43, T_CONSUMER, HidConstants.CONSUMER_VOLUME_UP, 0, GROUP_MEDIA, "音量+"),
            new Target(44, T_CONSUMER, HidConstants.CONSUMER_VOLUME_DOWN, 0, GROUP_MEDIA, "音量−"),
            new Target(45, T_CONSUMER, HidConstants.CONSUMER_MUTE, 0, GROUP_MEDIA, "静音"),
    };

    /** The physical keys of the gamepad page, in the order the mapping dialog lists them. */
    public static final int[] PHYS = {
            HidConstants.GP_UP, HidConstants.GP_DOWN, HidConstants.GP_LEFT, HidConstants.GP_RIGHT,
            HidConstants.GP_A, HidConstants.GP_B, HidConstants.GP_X, HidConstants.GP_Y,
            HidConstants.GP_L1, HidConstants.GP_R1, HidConstants.GP_L2, HidConstants.GP_R2,
            HidConstants.GP_SELECT, HidConstants.GP_START,
    };

    private static final String PREFS = "phonekb";
    private static final String KEY_MAP = "gp_map";
    private static final String KEY_SLOT = "gp_map_slot";
    private static final String KEY_FACE = "gp_face";

    public static final int SLOTS = 3;

    /** ids[slot][physical index] */
    private static int[][] ids;
    private static int slot;
    private static int faceLayout;
    private static boolean loaded;

    public static synchronized void init(Context context) {
        if (loaded) return;
        SharedPreferences p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        slot = clampSlot(p.getInt(KEY_SLOT, 0));
        faceLayout = clampFace(p.getInt(KEY_FACE, FACE_XBOX));
        ids = new int[SLOTS][PHYS.length];
        String raw = p.getString(KEY_MAP, "");
        String[] slots = raw.split(";", -1);
        for (int s = 0; s < SLOTS; s++) {
            resetSlot(s);
            if (s < slots.length && !slots[s].isEmpty()) {
                String[] parts = slots[s].split(",", -1);
                for (int i = 0; i < PHYS.length && i < parts.length; i++) {
                    try {
                        int id = Integer.parseInt(parts[i].trim());
                        ids[s][i] = (id >= 0 && id < TARGETS.length) ? id : ids[s][i];
                    } catch (NumberFormatException ignored) {
                    }
                }
            }
        }
        loaded = true;
    }

    private static void ensure() {
        if (ids == null) {
            ids = new int[SLOTS][PHYS.length];
            for (int s = 0; s < SLOTS; s++) resetSlot(s);
        }
    }

    /** Identity mapping for one slot: every physical key keeps its own HID button. */
    public static void resetSlot(int s) {
        ensure();
        for (int i = 0; i < PHYS.length; i++) ids[s][i] = defaultId(PHYS[i]);
    }

    private static int defaultId(int physBit) {
        for (Target t : TARGETS) {
            if (t.type == T_GAME && t.value == physBit) return t.id;
        }
        return 0;
    }

    private static int indexOfPhys(int physBit) {
        for (int i = 0; i < PHYS.length; i++) if (PHYS[i] == physBit) return i;
        return -1;
    }

    public static int slot() {
        return slot;
    }

    public static void setSlot(Context context, int newSlot) {
        ensure();
        slot = clampSlot(newSlot);
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putInt(KEY_SLOT, slot).apply();
    }

    public static int faceLayout() {
        return faceLayout;
    }

    public static void setFaceLayout(Context context, int layout) {
        ensure();
        faceLayout = clampFace(layout);
        int[][] keep = new int[SLOTS][PHYS.length];
        for (int s = 0; s < SLOTS; s++) System.arraycopy(ids[s], 0, keep[s], 0, PHYS.length);
        int active = slot;
        if (faceLayout == FACE_SWITCH) {
            // Nintendo layout: the bottom key is B, the right key is A, the left key is Y, the top
            // key is X. Only the four face buttons move; everything else keeps its own target.
            setTargetId(HidConstants.GP_A, defaultId(HidConstants.GP_B));
            setTargetId(HidConstants.GP_B, defaultId(HidConstants.GP_A));
            setTargetId(HidConstants.GP_X, defaultId(HidConstants.GP_Y));
            setTargetId(HidConstants.GP_Y, defaultId(HidConstants.GP_X));
        } else {
            setTargetId(HidConstants.GP_A, defaultId(HidConstants.GP_A));
            setTargetId(HidConstants.GP_B, defaultId(HidConstants.GP_B));
            setTargetId(HidConstants.GP_X, defaultId(HidConstants.GP_X));
            setTargetId(HidConstants.GP_Y, defaultId(HidConstants.GP_Y));
        }
        slot = active;
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putInt(KEY_FACE, faceLayout).apply();
        save(context);
    }

    public static int targetId(int physBit) {
        ensure();
        int i = indexOfPhys(physBit);
        return i < 0 ? 0 : ids[slot][i];
    }

    /** True when the physical key still sends its own HID button (nothing remapped). */
    public static boolean isDefault(int physBit) {
        int i = indexOfPhys(physBit);
        return i < 0 || ids[slot][i] == defaultId(physBit);
    }

    public static Target target(int physBit) {
        return TARGETS[targetId(physBit)];
    }

    public static void setTarget(Context context, int physBit, int targetId) {
        ensure();
        setTargetId(physBit, targetId);
        save(context);
    }

    private static void setTargetId(int physBit, int targetId) {
        int i = indexOfPhys(physBit);
        if (i >= 0) ids[slot][i] = Math.max(0, Math.min(TARGETS.length - 1, targetId));
    }

    /** Writes every slot back to the preferences. */
    public static void save(Context context) {
        ensure();
        StringBuilder sb = new StringBuilder();
        for (int s = 0; s < SLOTS; s++) {
            if (s > 0) sb.append(';');
            for (int i = 0; i < PHYS.length; i++) {
                if (i > 0) sb.append(',');
                sb.append(ids[s][i]);
            }
        }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putString(KEY_MAP, sb.toString()).apply();
    }

    /** Copies the current slot into another one and switches to it. */
    public static void copySlot(Context context, int from, int to) {
        ensure();
        int f = clampSlot(from), t = clampSlot(to);
        if (f == t) return;
        System.arraycopy(ids[f], 0, ids[t], 0, PHYS.length);
        save(context);
    }

    public static void resetCurrentSlot(Context context) {
        resetSlot(slot);
        save(context);
    }

    /** Name printed on the mapping row of one physical key. */
    public static String physName(int physBit) {
        if (physBit == HidConstants.GP_UP) return "方向键 ↑";
        if (physBit == HidConstants.GP_DOWN) return "方向键 ↓";
        if (physBit == HidConstants.GP_LEFT) return "方向键 ←";
        if (physBit == HidConstants.GP_RIGHT) return "方向键 →";
        if (physBit == HidConstants.GP_A) return "下键（A）";
        if (physBit == HidConstants.GP_B) return "右键（B）";
        if (physBit == HidConstants.GP_X) return "左键（X）";
        if (physBit == HidConstants.GP_Y) return "上键（Y）";
        if (physBit == HidConstants.GP_L1) return "L1";
        if (physBit == HidConstants.GP_R1) return "R1";
        if (physBit == HidConstants.GP_L2) return "LT";
        if (physBit == HidConstants.GP_R2) return "RT";
        if (physBit == HidConstants.GP_SELECT) return "SELECT";
        if (physBit == HidConstants.GP_START) return "START";
        return "按键";
    }

    /**
     * Label drawn on a face button: the HID button it actually sends, printed the way that vendor
     * prints it (Xbox letters, PlayStation symbols, Switch letters).
     */
    public static String faceLabel(int physBit) {
        ensure();
        int value = TARGETS[targetId(physBit)].type == T_GAME ? TARGETS[targetId(physBit)].value : physBit;
        if (faceLayout == FACE_PS) {
            if (value == HidConstants.GP_A) return "×";
            if (value == HidConstants.GP_B) return "○";
            if (value == HidConstants.GP_X) return "□";
            if (value == HidConstants.GP_Y) return "△";
        }
        if (value == HidConstants.GP_A) return "A";
        if (value == HidConstants.GP_B) return "B";
        if (value == HidConstants.GP_X) return "X";
        if (value == HidConstants.GP_Y) return "Y";
        return "";
    }

    /** True when the target of this physical key is one of the four d-pad directions. */
    public static boolean isDirection(Target t) {
        return t.type == T_GAME
                && (t.value == HidConstants.GP_UP || t.value == HidConstants.GP_DOWN
                || t.value == HidConstants.GP_LEFT || t.value == HidConstants.GP_RIGHT);
    }

    private static int clampSlot(int s) {
        return Math.max(0, Math.min(SLOTS - 1, s));
    }

    private static int clampFace(int f) {
        return Math.max(0, Math.min(FACE_NAMES.length - 1, f));
    }
}
