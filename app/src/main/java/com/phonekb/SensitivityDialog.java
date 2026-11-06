package com.phonekb;

import android.app.Dialog;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.view.Gravity;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;

/**
 * 「指针灵敏度」 dialog shared by every trackpad page: pointer speed (0.4x - 2.5x), acceleration
 * on/off and wheel speed (1 - 6 lines per notch). Changes apply immediately; 取消 restores the
 * values the dialog was opened with.
 */
public final class SensitivityDialog {
    private static final int SPEED_STEPS = 210;

    private SensitivityDialog() {
    }

    public static void show(final Context ctx, final Runnable onChanged) {
        PointerSettings.init(ctx);
        final float originalSpeed = PointerSettings.speed();
        final int originalRows = PointerSettings.rows();
        final boolean originalAccel = PointerSettings.accel();

        final Dialog dialog = new Dialog(ctx);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        final boolean land = ctx.getResources().getConfiguration().orientation
                == android.content.res.Configuration.ORIENTATION_LANDSCAPE;

        LinearLayout box = new LinearLayout(ctx);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dpi(ctx, 20), dpi(ctx, land ? 12 : 18), dpi(ctx, 20), dpi(ctx, land ? 10 : 16));

        LinearLayout titleRow = new LinearLayout(ctx);
        titleRow.setOrientation(LinearLayout.HORIZONTAL);
        titleRow.setGravity(Gravity.CENTER_VERTICAL);
        TextView title = new TextView(ctx);
        title.setText("指针灵敏度");
        title.setTextColor(Ui.TEXT);
        title.setTextSize(17);
        title.setTypeface(null, Typeface.BOLD);
        titleRow.addView(title, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        final TextView speedValue = new TextView(ctx);
        speedValue.setTextColor(Ui.ACCENT);
        speedValue.setTextSize(20);
        speedValue.setTypeface(null, Typeface.BOLD);
        speedValue.setGravity(Gravity.END);
        titleRow.addView(speedValue);
        box.addView(titleRow);

        TextView sub = new TextView(ctx);
        sub.setText("手指滑动有多快，平板光标就跟着走多快");
        sub.setTextColor(Ui.TEXT_DIM);
        sub.setTextSize(11);
        sub.setPadding(0, dpi(ctx, 2), 0, 0);
        box.addView(sub);

        final TextView hint = new TextView(ctx);
        hint.setTextColor(Ui.TEXT);
        hint.setTextSize(12);
        hint.setPadding(0, dpi(ctx, land ? 8 : 14), 0, 0);
        box.addView(hint);

        final SeekBar speedBar = new SeekBar(ctx);
        speedBar.setMax(SPEED_STEPS);
        speedBar.setProgressTintList(ColorStateList.valueOf(Ui.ACCENT));
        speedBar.setThumbTintList(ColorStateList.valueOf(Ui.ACCENT));
        speedBar.setProgress(speedToProgress(PointerSettings.speed()));
        LinearLayout.LayoutParams speedLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        speedLp.topMargin = dpi(ctx, land ? 4 : 8);
        box.addView(speedBar, speedLp);

        LinearLayout scaleRow = new LinearLayout(ctx);
        scaleRow.setOrientation(LinearLayout.HORIZONTAL);
        TextView slow = scaleLabel(ctx, "慢");
        TextView fast = scaleLabel(ctx, "快");
        fast.setGravity(Gravity.END);
        scaleRow.addView(slow, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        scaleRow.addView(fast, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        box.addView(scaleRow);

        final LinearLayout[] presetRow = new LinearLayout[1];
        presetRow[0] = segment(ctx, new String[]{"慢", "标准", "快"}, -1, which -> {
            float v = which == 0 ? 0.6f : (which == 1 ? 1.0f : 1.6f);
            speedBar.setProgress(speedToProgress(v));
            PointerSettings.setSpeed(ctx, v);
            refreshSpeed(ctx, speedValue, hint, presetRow[0]);
            if (onChanged != null) onChanged.run();
        });
        LinearLayout.LayoutParams presetLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        presetLp.topMargin = dpi(ctx, land ? 6 : 10);
        box.addView(presetRow[0], presetLp);

        final LinearLayout[] accelRow = new LinearLayout[1];
        accelRow[0] = segment(ctx, new String[]{"加速度 开", "关"},
                PointerSettings.accel() ? 0 : 1, which -> {
                    PointerSettings.setAccel(ctx, which == 0);
                    refreshAccel(accelRow[0], which);
                    if (onChanged != null) onChanged.run();
                });
        addRow(box, ctx, "加速度（快扫自动加速）", accelRow[0], land, land ? 8 : 14);

        LinearLayout wheelHead = new LinearLayout(ctx);
        wheelHead.setOrientation(LinearLayout.HORIZONTAL);
        wheelHead.setGravity(Gravity.CENTER_VERTICAL);
        TextView wheelLabel = settingsLabel(ctx, "滚轮速度");
        wheelHead.addView(wheelLabel, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        final TextView rowsValue = new TextView(ctx);
        rowsValue.setTextColor(Ui.ACCENT);
        rowsValue.setTextSize(13);
        rowsValue.setGravity(Gravity.END);
        rowsValue.setText(PointerSettings.rows() + " 行");
        wheelHead.addView(rowsValue);
        LinearLayout.LayoutParams wheelHeadLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        wheelHeadLp.topMargin = dpi(ctx, land ? 8 : 14);
        box.addView(wheelHead, wheelHeadLp);

        final SeekBar rowsBar = new SeekBar(ctx);
        rowsBar.setMax(PointerSettings.ROWS_MAX - PointerSettings.ROWS_MIN);
        rowsBar.setProgressTintList(ColorStateList.valueOf(Ui.ACCENT));
        rowsBar.setThumbTintList(ColorStateList.valueOf(Ui.ACCENT));
        rowsBar.setProgress(PointerSettings.rows() - PointerSettings.ROWS_MIN);
        rowsBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                int rows = PointerSettings.ROWS_MIN + progress;
                PointerSettings.setRows(ctx, rows);
                rowsValue.setText(PointerSettings.rows() + " 行");
                if (onChanged != null) onChanged.run();
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
            }
        });
        box.addView(rowsBar);

        speedBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                PointerSettings.setSpeed(ctx, progressToSpeed(progress));
                refreshSpeed(ctx, speedValue, hint, presetRow[0]);
                if (onChanged != null) onChanged.run();
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
            }
        });

        TextView note = new TextView(ctx);
        note.setText("对触控板页 / 触控板浮层 / 手柄页触控区同时生效，设置会记住。");
        note.setTextColor(Ui.TEXT_DIM);
        note.setTextSize(10);
        note.setPadding(0, dpi(ctx, land ? 6 : 10), 0, 0);
        box.addView(note);

        LinearLayout buttons = new LinearLayout(ctx);
        buttons.setOrientation(LinearLayout.HORIZONTAL);
        buttons.setGravity(Gravity.END);
        TextView cancel = pillButton(ctx, "取消", Ui.SURFACE_2, Ui.TEXT);
        cancel.setOnClickListener(v -> {
            PointerSettings.setSpeed(ctx, originalSpeed);
            PointerSettings.setRows(ctx, originalRows);
            PointerSettings.setAccel(ctx, originalAccel);
            if (onChanged != null) onChanged.run();
            dialog.dismiss();
        });
        TextView save = pillButton(ctx, "保存", Ui.ACCENT, Ui.TEXT);
        save.setOnClickListener(v -> dialog.dismiss());
        LinearLayout.LayoutParams btnLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        btnLp.rightMargin = dpi(ctx, 8);
        buttons.addView(cancel, btnLp);
        buttons.addView(save);
        LinearLayout.LayoutParams buttonsLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        buttonsLp.gravity = Gravity.END;
        buttonsLp.topMargin = dpi(ctx, land ? 8 : 14);
        box.addView(buttons, buttonsLp);

        LinearLayout outer = new LinearLayout(ctx);
        outer.setOrientation(LinearLayout.VERTICAL);
        outer.setBackground(Ui.rounded(ctx, Ui.SURFACE, 22));
        outer.setPadding(dpi(ctx, 2), dpi(ctx, 2), dpi(ctx, 2), dpi(ctx, 2));
        ScrollView scroller = new ScrollView(ctx);
        scroller.setFillViewport(false);
        scroller.addView(box);
        outer.addView(scroller, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        dialog.setContentView(outer);

        refreshSpeed(ctx, speedValue, hint, presetRow[0]);
        refreshAccel(accelRow[0], PointerSettings.accel() ? 0 : 1);

        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            window.setDimAmount(0.45f);
            window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            WindowManager.LayoutParams lp = new WindowManager.LayoutParams();
            lp.copyFrom(window.getAttributes());
            lp.width = dpi(ctx, land ? 400 : 330);
            int screenH = ctx.getResources().getDisplayMetrics().heightPixels;
            lp.height = land ? Math.round(screenH * 0.88f) : WindowManager.LayoutParams.WRAP_CONTENT;
            window.setAttributes(lp);
        }
        dialog.show();
    }

    private static void refreshSpeed(Context ctx, TextView value, TextView hint, LinearLayout presets) {
        float speed = PointerSettings.speed();
        value.setText(String.format(java.util.Locale.US, "%.1fx", speed));
        hint.setText(String.format(java.util.Locale.US,
                "= 每 1 cm 手指行程 → 光标走 %.1f cm", 1.7f * speed));
        if (presets != null) {
            int active = Math.abs(speed - 0.6f) < 0.06f ? 0
                    : (Math.abs(speed - 1.0f) < 0.06f ? 1 : (Math.abs(speed - 1.6f) < 0.06f ? 2 : -1));
            styleSegments(presets, active);
        }
    }

    private static void refreshAccel(LinearLayout row, int active) {
        styleSegments(row, active);
    }

    private static void styleSegments(LinearLayout row, int active) {
        for (int i = 0; i < row.getChildCount(); i++) {
            TextView tv = (TextView) row.getChildAt(i);
            boolean on = i == active;
            tv.setTextColor(on ? Ui.TEXT : Ui.TEXT_DIM);
            tv.setBackground(on ? Ui.rounded(row.getContext(), 0xFF3A60A8, 14)
                    : Ui.rounded(row.getContext(), Ui.KEY, 14));
            tv.setTypeface(null, on ? Typeface.BOLD : Typeface.NORMAL);
        }
    }

    private interface Pick {
        void onPick(int which);
    }

    private static LinearLayout segment(Context ctx, String[] labels, int active, Pick pick) {
        LinearLayout row = new LinearLayout(ctx);
        row.setOrientation(LinearLayout.HORIZONTAL);
        for (int i = 0; i < labels.length; i++) {
            final int which = i;
            TextView tv = new TextView(ctx);
            tv.setText(labels[i]);
            tv.setTextSize(12);
            tv.setGravity(Gravity.CENTER);
            tv.setPadding(dpi(ctx, 16), dpi(ctx, 6), dpi(ctx, 16), dpi(ctx, 6));
            boolean on = i == active;
            tv.setTextColor(on ? Ui.TEXT : Ui.TEXT_DIM);
            tv.setBackground(on ? Ui.rounded(ctx, 0xFF3A60A8, 14) : Ui.rounded(ctx, Ui.KEY, 14));
            tv.setTypeface(null, on ? Typeface.BOLD : Typeface.NORMAL);
            tv.setOnClickListener(v -> pick.onPick(which));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            if (i > 0) lp.leftMargin = dpi(ctx, 6);
            row.addView(tv, lp);
        }
        return row;
    }

    private static void addRow(LinearLayout box, Context ctx, String label, LinearLayout seg,
                               boolean land, int topDp) {
        if (land) {
            LinearLayout row = new LinearLayout(ctx);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.addView(settingsLabel(ctx, label), new LinearLayout.LayoutParams(0,
                    ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            row.addView(seg);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.topMargin = dpi(ctx, topDp);
            box.addView(row, lp);
            return;
        }
        LinearLayout.LayoutParams labelLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        labelLp.topMargin = dpi(ctx, topDp);
        box.addView(settingsLabel(ctx, label), labelLp);
        LinearLayout.LayoutParams segLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        segLp.gravity = Gravity.END;
        segLp.topMargin = dpi(ctx, 6);
        box.addView(seg, segLp);
    }

    private static TextView settingsLabel(Context ctx, String text) {
        TextView tv = new TextView(ctx);
        tv.setText(text);
        tv.setTextColor(Ui.TEXT);
        tv.setTextSize(12);
        return tv;
    }

    private static TextView scaleLabel(Context ctx, String text) {
        TextView tv = new TextView(ctx);
        tv.setText(text);
        tv.setTextColor(Ui.TEXT_DIM);
        tv.setTextSize(10);
        return tv;
    }

    private static TextView pillButton(Context ctx, String text, int bg, int fg) {
        TextView tv = new TextView(ctx);
        tv.setText(text);
        tv.setTextSize(12);
        tv.setTextColor(fg);
        tv.setGravity(Gravity.CENTER);
        tv.setPadding(dpi(ctx, 20), dpi(ctx, 9), dpi(ctx, 20), dpi(ctx, 9));
        tv.setBackground(Ui.rounded(ctx, bg, 14));
        return tv;
    }

    private static int speedToProgress(float speed) {
        float t = (PointerSettings.clampSpeed(speed) - PointerSettings.SPEED_MIN)
                / (PointerSettings.SPEED_MAX - PointerSettings.SPEED_MIN);
        return Math.round(t * SPEED_STEPS);
    }

    private static float progressToSpeed(int progress) {
        return PointerSettings.SPEED_MIN + (PointerSettings.SPEED_MAX - PointerSettings.SPEED_MIN)
                * (progress / (float) SPEED_STEPS);
    }

    private static int dpi(Context ctx, float value) {
        return Math.round(Ui.dp(ctx, value));
    }
}