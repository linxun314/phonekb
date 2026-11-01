package com.phonekb;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;

/**
 * Draggable divider shown on the portrait keyboard page between the touch area and the
 * docked keyboard. Dragging it up/down changes how much of the page each half takes;
 * double tapping it restores the default ratio.
 */
public class SplitHandleView extends View {

    public interface Listener {
        /** Finger position (raw screen Y) while dragging/down. The activity maps it to a share. */
        void onDrag(float rawY);
        /** Finger released: persist the ratio. */
        void onCommit();
        /** Double tap: restore the default ratio. */
        void onReset();
    }

    private static final float SNAP_TOLERANCE = 1e-4f;

    private final float density;
    private final Paint pillPaint = Ui.fill(Ui.TEXT_DIM);
    private final Paint pillActivePaint = Ui.fill(Ui.ACCENT);
    private final Paint hintPaint;
    private final Paint valuePaint;

    private Listener listener;
    private float share = 0.58f;
    private boolean dragging;
    private boolean dockMode;
    private float minShare = 0.30f;
    private float maxShare = 0.62f;
    private long lastTapTime;
    private float lastTapY;

    public SplitHandleView(Context context) {
        super(context);
        density = context.getResources().getDisplayMetrics().density;
        hintPaint = Ui.text(context, 11, Ui.TEXT_DIM);
        hintPaint.setTextAlign(Paint.Align.LEFT);
        valuePaint = Ui.text(context, 11, Ui.TEXT_DIM);
        valuePaint.setTextAlign(Paint.Align.RIGHT);
        setClickable(true);
    }

    public void setListener(Listener l) {
        listener = l;
    }

    /** Dock pages show different labels and use a wider ratio range than the keyboard split. */
    public void setDockMode(boolean dock, float min, float max) {
        dockMode = dock;
        minShare = Math.max(0.05f, Math.min(0.95f, min));
        maxShare = Math.max(minShare, Math.min(1f, max));
        if (share < minShare) share = minShare;
        if (share > maxShare) share = maxShare;
        invalidate();
    }

    /** Called by the activity whenever the ratio changes (drag or reset). */
    public void setShare(float newShare) {
        float clamped = Math.max(minShare, Math.min(maxShare, newShare));
        boolean snapped = !dockMode && isSnap(clamped) && !isSnap(share);
        share = clamped;
        invalidate();
        if (snapped) performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
    }

    private static boolean isSnap(float v) {
        return Math.abs(v - 0.48f) < SNAP_TOLERANCE
                || Math.abs(v - 0.58f) < SNAP_TOLERANCE
                || Math.abs(v - 0.62f) < SNAP_TOLERANCE;
    }

    private float baseline(float cy, Paint paint) {
        Paint.FontMetrics fm = paint.getFontMetrics();
        return cy - (fm.ascent + fm.descent) / 2f;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int w = getWidth();
        int h = getHeight();
        if (w == 0 || h == 0) return;
        float cx = w / 2f;
        float cy = h / 2f;

        int percent = Math.round(share * 100f);
        String left;
        String right;
        if (dockMode) {
            left = dragging ? "松手保存" : "拖动调整功能区";
            right = "功能区 " + percent + "% / 留空 " + (100 - percent) + "%";
        } else {
            left = dragging ? "松手保存比例" : "拖动调整比例";
            right = percent + "% / " + (100 - percent) + "%";
        }
        int leftColor = dragging ? Ui.ACCENT : Ui.TEXT_DIM;
        hintPaint.setColor(leftColor);
        valuePaint.setColor(Ui.TEXT_DIM);
        float pad = 14f * density;
        float base = baseline(cy, hintPaint);
        canvas.drawText(left, pad, base, hintPaint);
        canvas.drawText(right, w - pad, base, valuePaint);

        float pw = 60f * density;
        float ph = 5f * density;
        Paint pill = dragging ? pillActivePaint : pillPaint;
        canvas.drawRoundRect(cx - pw / 2f, cy - ph / 2f, cx + pw / 2f, cy + ph / 2f,
                ph / 2f, ph / 2f, pill);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                dragging = true;
                if (getParent() != null) getParent().requestDisallowInterceptTouchEvent(true);
                if (listener != null) listener.onDrag(event.getRawY());
                invalidate();
                return true;
            case MotionEvent.ACTION_MOVE:
                if (listener != null) listener.onDrag(event.getRawY());
                return true;
            case MotionEvent.ACTION_UP:
                dragging = false;
                if (listener != null) listener.onDrag(event.getRawY());
                invalidate();
                long now = event.getEventTime();
                if (now - lastTapTime < 260
                        && Math.abs(event.getRawY() - lastTapY) < 40f * density) {
                    lastTapTime = 0;
                    if (listener != null) listener.onReset();
                } else {
                    lastTapTime = now;
                    lastTapY = event.getRawY();
                    if (listener != null) listener.onCommit();
                }
                return true;
            case MotionEvent.ACTION_CANCEL:
                dragging = false;
                invalidate();
                if (listener != null) listener.onCommit();
                return true;
            default:
                return super.onTouchEvent(event);
        }
    }
}
