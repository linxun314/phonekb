package com.phonekb.companion;

import android.accessibilityservice.AccessibilityService;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.os.Bundle;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;

/**
 * 把手机发来的文字写入平板当前获得焦点的输入框。
 * 优先用无障碍 ACTION_SET_TEXT；失败时回退到「写剪贴板 + ACTION_PASTE」。
 * 不做任何网络访问，只在本机进程内被 LinkService 调用。
 */
public class InjectorService extends AccessibilityService {
    private static final String TAG = "PhoneKB-Companion";
    private static volatile InjectorService instance;

    public static InjectorService get() {
        return instance;
    }

    public static boolean isReady() {
        return instance != null;
    }

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        instance = this;
        Log.i(TAG, "accessibility service connected");
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        // Not needed: we only inject on demand when a message arrives.
    }

    @Override
    public void onInterrupt() {
    }

    @Override
    public void onDestroy() {
        if (instance == this) instance = null;
        super.onDestroy();
    }

    /**
     * @return "ok" on success, otherwise a human readable reason.
     */
    public String injectText(String text, boolean append) {
        if (text == null) return "空文本";
        AccessibilityNodeInfo target = findInput();
        if (target == null) return "没有找到输入框（先在平板上点一下要输入的框）";
        String existing = "";
        if (append) {
            CharSequence cur = target.getText();
            if (cur != null) existing = cur.toString();
        }
        String full = existing + text;
        Bundle args = new Bundle();
        args.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, full);
        args.putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_START_INT, full.length());
        args.putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_END_INT, full.length());
        boolean ok = false;
        try {
            ok = target.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args);
        } catch (Exception e) {
            Log.w(TAG, "set text failed: " + e.getMessage());
        }
        if (!ok) {
            // Fallback: copy to the clipboard and let the focused field paste it.
            try {
                ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                if (cm != null) {
                    cm.setPrimaryClip(ClipData.newPlainText("PhoneKB", text));
                    ok = target.performAction(AccessibilityNodeInfo.ACTION_PASTE);
                }
            } catch (Exception e) {
                Log.w(TAG, "paste fallback failed: " + e.getMessage());
            }
        }
        return ok ? "ok" : "注入失败（该输入框可能不支持无障碍写入）";
    }

    private AccessibilityNodeInfo findInput() {
        AccessibilityNodeInfo root;
        try {
            root = getRootInActiveWindow();
        } catch (Exception e) {
            return null;
        }
        if (root == null) return null;
        AccessibilityNodeInfo focus = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT);
        if (focus != null && focus.isEditable()) return focus;
        return findEditable(root, 0);
    }

    private AccessibilityNodeInfo findEditable(AccessibilityNodeInfo node, int depth) {
        if (node == null || depth > 24) return null;
        if (node.isEditable() && node.isEnabled() && node.isVisibleToUser()) return node;
        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo child = null;
            try {
                child = node.getChild(i);
            } catch (Exception ignored) {
            }
            if (child == null) continue;
            AccessibilityNodeInfo result = findEditable(child, depth + 1);
            if (result != null) return result;
        }
        return null;
    }
}
