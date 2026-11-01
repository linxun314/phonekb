package com.phonekb;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** A grid of big tappable tiles, used for media controls and the gamepad. */
public class TilePadView extends View {

    public interface Listener {
        void onTilePressed(TilePadView view, Tile tile);
        void onTileReleased(TilePadView view, Tile tile);
    }

    public static final class Tile {
        public final String label;
        public final int tint;
        public final Object tag;

        public Tile(String label) {
            this(label, 0, null);
        }

        public Tile(String label, int tint, Object tag) {
            this.label = label;
            this.tint = tint;
            this.tag = tag;
        }

        public boolean isBlank() {
            return label == null || label.isEmpty();
        }
    }

    private final List<Tile[]> rows = new ArrayList<>();
    private final List<RectF[]> rectRows = new ArrayList<>();
    private final Map<Integer, Tile> pointerTiles = new HashMap<>();

    private final Paint bgPaint = Ui.fill(Ui.SURFACE);
    private final Paint tilePaint = Ui.fill(Ui.KEY);
    private final Paint pressedPaint = Ui.fill(Ui.KEY_PRESSED);
    private final Paint tintPaint = Ui.fill(0);
    private final Paint textPaint;
    private final Paint bigTextPaint;
    private final float gap;
    private final float radius;
    private final float pad;

    private Listener listener;

    public TilePadView(Context context) {
        super(context);
        gap = Ui.dp(context, 8);
        radius = Ui.dp(context, 12);
        pad = Ui.dp(context, 8);
        textPaint = Ui.text(context, 15, Ui.TEXT);
        bigTextPaint = Ui.text(context, 24, Ui.TEXT);
    }

    public void setListener(Listener l) {
        this.listener = l;
    }

    public void setRows(List<Tile[]> newRows) {
        rows.clear();
        rows.addAll(newRows);
        computeLayout();
        invalidate();
    }

    @Override
    protected void onSizeChanged(int w, int h, int ow, int oh) {
        super.onSizeChanged(w, h, ow, oh);
        computeLayout();
    }

    private void computeLayout() {
        rectRows.clear();
        int w = getWidth() - Math.round(pad * 2);
        int h = getHeight() - Math.round(pad * 2);
        if (w <= 0 || h <= 0 || rows.isEmpty()) return;
        int rowCount = rows.size();
        float cellH = (h - gap * (rowCount - 1)) / rowCount;
        float top = pad;
        for (Tile[] row : rows) {
            int cols = row.length;
            float cellW = (w - gap * (cols - 1)) / cols;
            RectF[] rects = new RectF[cols];
            float left = pad;
            for (int c = 0; c < cols; c++) {
                rects[c] = new RectF(left, top, left + cellW, top + cellH);
                left += cellW + gap;
            }
            rectRows.add(rects);
            top += cellH + gap;
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        canvas.drawColor(Ui.BG);
        canvas.drawRoundRect(new RectF(0, 0, getWidth(), getHeight()), Ui.dp(getContext(), 16), Ui.dp(getContext(), 16), bgPaint);
        for (int r = 0; r < rectRows.size() && r < rows.size(); r++) {
            Tile[] row = rows.get(r);
            RectF[] rects = rectRows.get(r);
            for (int c = 0; c < row.length; c++) {
                Tile t = row[c];
                if (t == null || t.isBlank()) continue;
                RectF rect = rects[c];
                boolean pressed = pointerTiles.containsValue(t);
                Paint fill;
                if (pressed) {
                    fill = pressedPaint;
                } else if (t.tint != 0) {
                    tintPaint.setColor(t.tint);
                    fill = tintPaint;
                } else {
                    fill = tilePaint;
                }
                canvas.drawRoundRect(rect, radius, radius, fill);
                Paint text = t.label.length() <= 2 ? bigTextPaint : textPaint;
                Ui.textCenter(canvas, t.label, rect.centerX(), rect.centerY(), text);
            }
        }
    }

    private Tile hit(float x, float y) {
        for (int r = 0; r < rectRows.size() && r < rows.size(); r++) {
            RectF[] rects = rectRows.get(r);
            for (int c = 0; c < rects.length; c++) {
                if (rects[c].contains(x, y)) {
                    Tile t = rows.get(r)[c];
                    return t == null || t.isBlank() ? null : t;
                }
            }
        }
        return null;
    }

    private boolean stillOn(Tile t, float x, float y) {
        for (int r = 0; r < rectRows.size() && r < rows.size(); r++) {
            Tile[] row = rows.get(r);
            RectF[] rects = rectRows.get(r);
            for (int c = 0; c < row.length; c++) {
                if (row[c] == t) return rects[c].contains(x, y);
            }
        }
        return false;
    }

    private void release(int pointerId) {
        Tile t = pointerTiles.remove(pointerId);
        if (t != null && listener != null) listener.onTileReleased(this, t);
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
            case MotionEvent.ACTION_POINTER_DOWN: {
                int idx = e.getActionIndex();
                int pid = e.getPointerId(idx);
                Tile t = hit(e.getX(idx), e.getY(idx));
                if (t == null) return true;
                performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
                pointerTiles.put(pid, t);
                if (listener != null) listener.onTilePressed(this, t);
                invalidate();
                return true;
            }
            case MotionEvent.ACTION_MOVE: {
                boolean changed = false;
                for (int i = 0; i < e.getPointerCount(); i++) {
                    int pid = e.getPointerId(i);
                    Tile t = pointerTiles.get(pid);
                    if (t != null && !stillOn(t, e.getX(i), e.getY(i))) {
                        release(pid);
                        changed = true;
                    }
                }
                if (changed) invalidate();
                return true;
            }
            case MotionEvent.ACTION_POINTER_UP:
            case MotionEvent.ACTION_UP: {
                release(e.getPointerId(e.getActionIndex()));
                invalidate();
                return true;
            }
            case MotionEvent.ACTION_CANCEL: {
                for (Integer pid : new ArrayList<>(pointerTiles.keySet())) release(pid);
                invalidate();
                return true;
            }
            default:
                return true;
        }
    }
}