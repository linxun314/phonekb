package com.phonekb;

/** HID report ids, the composite report descriptor and USB HID usage ids. */
public final class HidConstants {
    private HidConstants() {}

    public static final int REPORT_KEYBOARD = 1;
    public static final int REPORT_CONSUMER = 2;
    public static final int REPORT_MOUSE = 3;
    public static final int REPORT_GAMEPAD = 4;

    /** Keyboard modifier bits (byte 0 of the keyboard report). */
    public static final int MOD_LEFT_CTRL = 0x01;
    public static final int MOD_LEFT_SHIFT = 0x02;
    public static final int MOD_LEFT_ALT = 0x04;
    public static final int MOD_LEFT_GUI = 0x08;
    public static final int MOD_RIGHT_CTRL = 0x10;
    public static final int MOD_RIGHT_SHIFT = 0x20;
    public static final int MOD_RIGHT_ALT = 0x40;
    public static final int MOD_RIGHT_GUI = 0x80;

    public static final int KEY_A = 0x04, KEY_B = 0x05, KEY_C = 0x06, KEY_D = 0x07,
            KEY_E = 0x08, KEY_F = 0x09, KEY_G = 0x0A, KEY_H = 0x0B, KEY_I = 0x0C,
            KEY_J = 0x0D, KEY_K = 0x0E, KEY_L = 0x0F, KEY_M = 0x10, KEY_N = 0x11,
            KEY_O = 0x12, KEY_P = 0x13, KEY_Q = 0x14, KEY_R = 0x15, KEY_S = 0x16,
            KEY_T = 0x17, KEY_U = 0x18, KEY_V = 0x19, KEY_W = 0x1A, KEY_X = 0x1B,
            KEY_Y = 0x1C, KEY_Z = 0x1D;
    public static final int KEY_1 = 0x1E, KEY_2 = 0x1F, KEY_3 = 0x20, KEY_4 = 0x21,
            KEY_5 = 0x22, KEY_6 = 0x23, KEY_7 = 0x24, KEY_8 = 0x25, KEY_9 = 0x26,
            KEY_0 = 0x27;
    public static final int KEY_ENTER = 0x28, KEY_ESC = 0x29, KEY_BACKSPACE = 0x2A,
            KEY_TAB = 0x2B, KEY_SPACE = 0x2C, KEY_MINUS = 0x2D, KEY_EQUAL = 0x2E,
            KEY_LEFT_BRACKET = 0x2F, KEY_RIGHT_BRACKET = 0x30, KEY_BACKSLASH = 0x31,
            KEY_SEMICOLON = 0x33, KEY_APOSTROPHE = 0x34, KEY_GRAVE = 0x35,
            KEY_COMMA = 0x36, KEY_DOT = 0x37, KEY_SLASH = 0x38, KEY_CAPS_LOCK = 0x39;
    public static final int KEY_F1 = 0x3A, KEY_F2 = 0x3B, KEY_F3 = 0x3C, KEY_F4 = 0x3D,
            KEY_F5 = 0x3E, KEY_F6 = 0x3F, KEY_F7 = 0x40, KEY_F8 = 0x41, KEY_F9 = 0x42,
            KEY_F10 = 0x43, KEY_F11 = 0x44, KEY_F12 = 0x45;
    public static final int KEY_INSERT = 0x49, KEY_HOME = 0x4A, KEY_PAGE_UP = 0x4B,
            KEY_DELETE = 0x4C, KEY_END = 0x4D, KEY_PAGE_DOWN = 0x4E,
            KEY_RIGHT = 0x4F, KEY_LEFT = 0x50, KEY_DOWN = 0x51, KEY_UP = 0x52;

    public static final int CONSUMER_PLAY_PAUSE = 0x00CD;
    public static final int CONSUMER_NEXT_TRACK = 0x00B5;
    public static final int CONSUMER_PREV_TRACK = 0x00B6;
    public static final int CONSUMER_VOLUME_UP = 0x00E9;
    public static final int CONSUMER_VOLUME_DOWN = 0x00EA;
    public static final int CONSUMER_MUTE = 0x00E2;
    public static final int CONSUMER_STOP = 0x00B7;
    public static final int CONSUMER_REWIND = 0x00B4;
    public static final int CONSUMER_FAST_FORWARD = 0x00B3;
    public static final int CONSUMER_MENU = 0x0040;
    public static final int CONSUMER_POWER = 0x0030;
    public static final int CONSUMER_CHANNEL_UP = 0x009C;
    public static final int CONSUMER_CHANNEL_DOWN = 0x009D;
    public static final int CONSUMER_CLOSED_CAPTION = 0x0173;
    public static final int CONSUMER_AC_HOME = 0x0223;
    public static final int CONSUMER_AC_BACK = 0x0224;
    public static final int CONSUMER_AC_REFRESH = 0x0227;

    public static final int GP_UP = 0x0001, GP_DOWN = 0x0002, GP_LEFT = 0x0004, GP_RIGHT = 0x0008;
    public static final int GP_A = 0x0010, GP_B = 0x0020, GP_X = 0x0040, GP_Y = 0x0080;
    public static final int GP_L1 = 0x0100, GP_R1 = 0x0200;
    public static final int GP_SELECT = 0x0400, GP_START = 0x0800;

    /**
     * Composite HID report descriptor: consumer control, keyboard, mouse and gamepad.
     *
     * The report ids, payload sizes and the two-byte keyboard report intentionally
     * match Bluetooth Remote (Atharok/BtRemote) v0.0.7. Android tablets that paired
     * with that app keep its descriptor cached in the Bluetooth stack, so using the
     * same layout means our reports are decoded correctly even before re-pairing.
     */
    public static final byte[] REPORT_DESCRIPTOR = hex(
            // Consumer control: report id 2, one 16-bit usage
            "05 0C 09 01 A1 01 85 02 15 00 26 FF 03 19 00 2A FF 03 75 10 95 01 81 00 C0" +
            // Keyboard: report id 1, 2-byte report [modifiers, keycode], no LED report
            "05 01 09 06 A1 01 85 01 05 07 19 E0 29 E7 15 00 25 01 75 01 95 08 81 02" +
            "19 00 2A FF 00 15 00 26 FF 00 75 08 95 01 81 00 C0" +
            // Mouse: report id 3, buttons + X + Y + wheel
            "05 01 09 02 A1 01 85 03 09 01 A1 00 05 09 19 01 29 03 15 00 25 01 95 03" +
            "75 01 81 02 95 01 75 05 81 01 05 01 09 30 09 31 09 38 15 81 25 7F 75 08" +
            "95 03 81 06 C0 C0" +
            // Gamepad: report id 4, hat switch + 16 buttons
            "05 01 09 05 A1 01 85 04 15 00 25 08 35 00 46 3B 01 65 14 09 39 95 01 75 04" +
            "81 42 95 01 75 04 81 03 05 09 19 01 29 10 15 00 25 01 95 10 75 01 81 02 C0");

    private static byte[] hex(String s) {
        String compact = s.replace(" ", "").replace("\n", "").replace("\r", "");
        byte[] out = new byte[compact.length() / 2];
        for (int i = 0; i < out.length; i++) {
            out[i] = (byte) Integer.parseInt(compact.substring(i * 2, i * 2 + 2), 16);
        }
        return out;
    }
}