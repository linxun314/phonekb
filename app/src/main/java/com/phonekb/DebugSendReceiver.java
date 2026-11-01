package com.phonekb;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

/**
 * Debug-only injection point for automated integration tests.
 * adb input/tap is blocked by MIUI on the test device, so a broadcast lets the
 * build server drive a "phone -> tablet" message while the tablet UI stays in
 * the foreground (needed to verify accessibility injection).
 * Guarded by the pairing token, which is only known to paired devices.
 */
public class DebugSendReceiver extends BroadcastReceiver {
    public static final String ACTION = "com.phonekb.action.DEBUG_SEND";
    private static final String TAG = "PhoneKB-Debug";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null || !ACTION.equals(intent.getAction())) return;
        CompanionBridge bridge = CompanionBridge.get(context);
        bridge.start();
        String token = intent.getStringExtra("token");
        if (token == null || !token.equals(bridge.getToken())) {
            Log.w(TAG, "ignored: bad token");
            return;
        }
        String text = intent.getStringExtra("text");
        if (text == null) text = "";
        boolean clip = intent.getBooleanExtra("clip", false);
        if (clip) {
            bridge.sendClip(text);
        } else {
            bridge.sendText(text, intent.getBooleanExtra("append", true));
        }
        Log.i(TAG, "debug send clip=" + clip + " len=" + text.length());
    }
}
