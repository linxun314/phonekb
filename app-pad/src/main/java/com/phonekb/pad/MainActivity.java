package com.phonekb.pad;

import android.Manifest;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

/** PhoneKB Pad：给 iPad 当蓝牙键盘 / 触控板 / 媒体键。 */
public class MainActivity extends Activity implements HidManager.Listener {

    private static final int REQ_PERM = 101;
    private static final String[] TAB_NAMES = new String[]{"键盘", "触控板", "媒体"};

    private HidManager hid;
    private TextView statusDot;
    private TextView statusTitle;
    private TextView statusDetail;
    private final TextView[] tabs = new TextView[3];
    private final View[] pages = new View[3];
    private int page;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        PadStyle.init(this);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        getWindow().setStatusBarColor(Color.TRANSPARENT);
        getWindow().setNavigationBarColor(Color.TRANSPARENT);
        setContentView(buildUi());
        applyInsets();

        hid = HidManager.get(this);
        hid.setListener(this);
        if (checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.BLUETOOTH_CONNECT}, REQ_PERM);
        } else {
            hid.start();
        }
        selectPage(0);
        onHidStatus(hid.getState(), hid.getStatusTitle(), hid.getStatusDetail());
    }

    @Override
    protected void onDestroy() {
        if (hid != null) hid.setListener(null);
        super.onDestroy();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQ_PERM) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                hid.start();
            } else {
                onHidStatus(HidManager.STATE_ERROR, "缺少蓝牙权限", "请在系统设置里允许 PhoneKB Pad 使用附近的设备");
            }
        }
    }

    private View buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setId(View.generateViewId());
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(PadStyle.BG);
        root.setTag("root");

        LinearLayout head = new LinearLayout(this);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);
        int hp = (int) PadStyle.dp(10f);
        head.setPadding(hp, hp, hp, 0);

        LinearLayout statusBox = new LinearLayout(this);
        statusBox.setOrientation(LinearLayout.VERTICAL);
        LinearLayout statusLine = new LinearLayout(this);
        statusLine.setOrientation(LinearLayout.HORIZONTAL);
        statusLine.setGravity(Gravity.CENTER_VERTICAL);

        statusDot = new TextView(this);
        statusDot.setText("\u25CF");
        statusDot.setTextSize(14f);
        statusDot.setTextColor(0xFFE0A33E);
        statusLine.addView(statusDot);
        statusLine.addView(spacer((int) PadStyle.dp(8f), 0));

        statusTitle = new TextView(this);
        statusTitle.setTextSize(15f);
        statusTitle.setTextColor(PadStyle.TEXT);
        statusLine.addView(statusTitle);
        statusBox.addView(statusLine);

        statusDetail = new TextView(this);
        statusDetail.setTextSize(11f);
        statusDetail.setTextColor(PadStyle.TEXT_DIM);
        statusBox.addView(statusDetail);

        head.addView(statusBox, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        LinearLayout tabBar = new LinearLayout(this);
        tabBar.setOrientation(LinearLayout.HORIZONTAL);
        for (int i = 0; i < 3; i++) {
            final int index = i;
            TextView tab = pill(TAB_NAMES[i]);
            tab.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    selectPage(index);
                }
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            if (i > 0) lp.leftMargin = (int) PadStyle.dp(6f);
            tabs[i] = tab;
            tabBar.addView(tab, lp);
        }
        head.addView(tabBar);
        root.addView(head);

        FrameLayout content = new FrameLayout(this);
        LinearLayout.LayoutParams contentLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f);
        contentLp.topMargin = (int) PadStyle.dp(8f);
        pages[0] = new PadKeyboardView(this);
        pages[1] = new PadTouchpadView(this);
        pages[2] = new PadMediaView(this);
        for (View page : pages) {
            content.addView(page, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        }
        root.addView(content, contentLp);

        TextView footer = new TextView(this);
        footer.setText("PhoneKB Pad 0.1.0 \u00B7 蓝牙 HID \u00B7 键盘 / 触控板 / 媒体（iPad 版）");
        footer.setTextSize(10f);
        footer.setTextColor(PadStyle.TEXT_DIM);
        footer.setGravity(Gravity.CENTER);
        int fp = (int) PadStyle.dp(6f);
        footer.setPadding(fp, fp, fp, fp);
        root.addView(footer);
        return root;
    }

    private void applyInsets() {
        final View root = findViewById(android.R.id.content);
        if (!(root instanceof ViewGroup)) return;
        final ViewGroup contentRoot = (ViewGroup) root;
        contentRoot.setOnApplyWindowInsetsListener(new View.OnApplyWindowInsetsListener() {
            @Override
            public WindowInsets onApplyWindowInsets(View v, WindowInsets insets) {
                View child = contentRoot.getChildAt(0);
                if (child != null) {
                    child.setPadding(insets.getSystemWindowInsetLeft(),
                            insets.getSystemWindowInsetTop(),
                            insets.getSystemWindowInsetRight(),
                            insets.getSystemWindowInsetBottom());
                }
                return insets;
            }
        });
    }

    private void selectPage(int index) {
        page = index;
        for (int i = 0; i < 3; i++) {
            boolean on = i == index;
            pages[i].setVisibility(on ? View.VISIBLE : View.GONE);
            TextView tab = tabs[i];
            tab.setTextColor(on ? PadStyle.TEXT : PadStyle.TEXT_DIM);
            GradientDrawable bg = new GradientDrawable();
            bg.setCornerRadius(PadStyle.dp(14f));
            bg.setColor(on ? PadStyle.SURFACE_2 : Color.TRANSPARENT);
            tab.setBackground(bg);
        }
    }

    @Override
    public void onHidStatus(int state, String title, String detail) {
        if (statusTitle == null) return;
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                statusTitle.setText(title);
                statusDetail.setText(detail);
                int color;
                if (state == HidManager.STATE_CONNECTED) {
                    color = 0xFF3DB675;
                } else if (state == HidManager.STATE_ERROR) {
                    color = PadStyle.DANGER;
                } else {
                    color = 0xFFE0A33E;
                }
                statusDot.setTextColor(color);
            }
        });
    }

    private TextView pill(String text) {
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextSize(13f);
        tv.setTextColor(PadStyle.TEXT_DIM);
        tv.setGravity(Gravity.CENTER);
        int px = (int) PadStyle.dp(14f);
        int py = (int) PadStyle.dp(6f);
        tv.setPadding(px, py, px, py);
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(PadStyle.dp(14f));
        bg.setColor(Color.TRANSPARENT);
        tv.setBackground(bg);
        return tv;
    }

    private View spacer(int w, int h) {
        View v = new View(this);
        v.setLayoutParams(new LinearLayout.LayoutParams(w, h));
        return v;
    }
}
