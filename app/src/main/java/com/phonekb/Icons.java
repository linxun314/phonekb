package com.phonekb;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;

/** Small vector icon set drawn with Canvas for the remote-style control pages. */
public final class Icons {
    private Icons() {}

    public static final int PREV = 1;
    public static final int NEXT = 2;
    public static final int PLAY_PAUSE = 3;
    public static final int STOP = 4;
    public static final int REWIND = 5;
    public static final int FORWARD = 6;
    public static final int VOL_UP = 7;
    public static final int VOL_DOWN = 8;
    public static final int MUTE = 9;
    public static final int BACK = 10;
    public static final int HOME = 11;
    public static final int MENU = 12;
    public static final int POWER = 13;
    public static final int UP_ARROW = 14;
    public static final int DOWN_ARROW = 15;
    public static final int RECENTS = 16;
    public static final int SCREENSHOT = 17;
    public static final int SUN_UP = 18;
    public static final int SUN_DOWN = 19;

    /** Draws one icon centered inside the given box, using the paint's color. */
    public static void draw(Canvas canvas, int icon, RectF box, Paint fillPaint) {
        float s = Math.min(box.width(), box.height()) * 0.66f;
        float cx = box.centerX();
        float cy = box.centerY();
        Path path = new Path();
        Paint stroke = new Paint(fillPaint);
        stroke.setStyle(Paint.Style.STROKE);
        stroke.setStrokeWidth(s * 0.11f);
        stroke.setStrokeCap(Paint.Cap.ROUND);
        stroke.setStrokeJoin(Paint.Join.ROUND);

        switch (icon) {
            case PREV: {
                path.moveTo(cx + 0.40f * s, cy - 0.38f * s);
                path.lineTo(cx + 0.40f * s, cy + 0.38f * s);
                path.lineTo(cx - 0.10f * s, cy);
                path.close();
                canvas.drawPath(path, fillPaint);
                canvas.drawRoundRect(new RectF(cx - 0.46f * s, cy - 0.38f * s,
                        cx - 0.30f * s, cy + 0.38f * s), 0.05f * s, 0.05f * s, fillPaint);
                break;
            }
            case NEXT: {
                path.moveTo(cx - 0.40f * s, cy - 0.38f * s);
                path.lineTo(cx - 0.40f * s, cy + 0.38f * s);
                path.lineTo(cx + 0.10f * s, cy);
                path.close();
                canvas.drawPath(path, fillPaint);
                canvas.drawRoundRect(new RectF(cx + 0.30f * s, cy - 0.38f * s,
                        cx + 0.46f * s, cy + 0.38f * s), 0.05f * s, 0.05f * s, fillPaint);
                break;
            }
            case PLAY_PAUSE: {
                path.moveTo(cx - 0.46f * s, cy - 0.40f * s);
                path.lineTo(cx - 0.46f * s, cy + 0.40f * s);
                path.lineTo(cx - 0.02f * s, cy);
                path.close();
                canvas.drawPath(path, fillPaint);
                canvas.drawRoundRect(new RectF(cx + 0.14f * s, cy - 0.40f * s,
                        cx + 0.26f * s, cy + 0.40f * s), 0.05f * s, 0.05f * s, fillPaint);
                canvas.drawRoundRect(new RectF(cx + 0.32f * s, cy - 0.40f * s,
                        cx + 0.44f * s, cy + 0.40f * s), 0.05f * s, 0.05f * s, fillPaint);
                break;
            }
            case STOP: {
                canvas.drawRoundRect(new RectF(cx - 0.30f * s, cy - 0.30f * s,
                        cx + 0.30f * s, cy + 0.30f * s), 0.08f * s, 0.08f * s, fillPaint);
                break;
            }
            case REWIND: {
                path.moveTo(cx + 0.18f * s, cy - 0.34f * s);
                path.lineTo(cx - 0.30f * s, cy);
                path.lineTo(cx + 0.18f * s, cy + 0.34f * s);
                path.close();
                canvas.drawPath(path, fillPaint);
                path.reset();
                path.moveTo(cx + 0.48f * s, cy - 0.34f * s);
                path.lineTo(cx, cy);
                path.lineTo(cx + 0.48f * s, cy + 0.34f * s);
                path.close();
                canvas.drawPath(path, fillPaint);
                break;
            }
            case FORWARD: {
                path.moveTo(cx - 0.18f * s, cy - 0.34f * s);
                path.lineTo(cx + 0.30f * s, cy);
                path.lineTo(cx - 0.18f * s, cy + 0.34f * s);
                path.close();
                canvas.drawPath(path, fillPaint);
                path.reset();
                path.moveTo(cx - 0.48f * s, cy - 0.34f * s);
                path.lineTo(cx, cy);
                path.lineTo(cx - 0.48f * s, cy + 0.34f * s);
                path.close();
                canvas.drawPath(path, fillPaint);
                break;
            }
            case VOL_UP: {
                speaker(path, cx, cy, s);
                canvas.drawPath(path, fillPaint);
                canvas.drawArc(new RectF(cx - 0.30f * s, cy - 0.26f * s,
                        cx + 0.22f * s, cy + 0.26f * s), -52f, 104f, false, stroke);
                canvas.drawArc(new RectF(cx - 0.46f * s, cy - 0.42f * s,
                        cx + 0.38f * s, cy + 0.42f * s), -52f, 104f, false, stroke);
                break;
            }
            case VOL_DOWN: {
                speaker(path, cx, cy, s);
                canvas.drawPath(path, fillPaint);
                canvas.drawArc(new RectF(cx - 0.30f * s, cy - 0.26f * s,
                        cx + 0.22f * s, cy + 0.26f * s), -52f, 104f, false, stroke);
                break;
            }
            case MUTE: {
                speaker(path, cx, cy, s);
                canvas.drawPath(path, fillPaint);
                canvas.drawLine(cx + 0.08f * s, cy - 0.24f * s, cx + 0.42f * s, cy + 0.22f * s, stroke);
                canvas.drawLine(cx + 0.42f * s, cy - 0.24f * s, cx + 0.08f * s, cy + 0.22f * s, stroke);
                break;
            }
            case BACK: {
                canvas.drawLine(cx + 0.38f * s, cy, cx - 0.34f * s, cy, stroke);
                path.moveTo(cx - 0.04f * s, cy - 0.30f * s);
                path.lineTo(cx - 0.36f * s, cy);
                path.lineTo(cx - 0.04f * s, cy + 0.30f * s);
                canvas.drawPath(path, stroke);
                break;
            }
            case HOME: {
                path.moveTo(cx - 0.42f * s, cy + 0.04f * s);
                path.lineTo(cx, cy - 0.40f * s);
                path.lineTo(cx + 0.42f * s, cy + 0.04f * s);
                canvas.drawPath(path, stroke);
                path.reset();
                path.moveTo(cx - 0.30f * s, cy - 0.02f * s);
                path.lineTo(cx - 0.30f * s, cy + 0.40f * s);
                path.lineTo(cx + 0.30f * s, cy + 0.40f * s);
                path.lineTo(cx + 0.30f * s, cy - 0.02f * s);
                canvas.drawPath(path, stroke);
                break;
            }
            case RECENTS: {
                RectF backCard = new RectF(cx - 0.38f * s, cy - 0.38f * s, cx + 0.20f * s, cy + 0.20f * s);
                canvas.drawRoundRect(backCard, 0.08f * s, 0.08f * s, stroke);
                RectF frontCard = new RectF(cx - 0.14f * s, cy - 0.14f * s, cx + 0.42f * s, cy + 0.42f * s);
                canvas.drawRoundRect(frontCard, 0.08f * s, 0.08f * s, stroke);
                break;
            }
            case SCREENSHOT: {
                canvas.drawRoundRect(new RectF(cx - 0.46f * s, cy - 0.30f * s,
                        cx + 0.46f * s, cy + 0.34f * s), 0.10f * s, 0.10f * s, stroke);
                canvas.drawCircle(cx - 0.02f * s, cy + 0.02f * s, 0.16f * s, stroke);
                canvas.drawRoundRect(new RectF(cx + 0.12f * s, cy - 0.38f * s,
                        cx + 0.32f * s, cy - 0.24f * s), 0.04f * s, 0.04f * s, fillPaint);
                break;
            }
            case SUN_UP:
            case SUN_DOWN: {
                float sc = cx - 0.12f * s;
                canvas.drawCircle(sc, cy, 0.19f * s, stroke);
                for (int i = 0; i < 8; i++) {
                    double a = Math.PI * i / 4.0;
                    float dx = (float) Math.cos(a);
                    float dy = (float) Math.sin(a);
                    canvas.drawLine(sc + dx * 0.28f * s, cy + dy * 0.28f * s,
                            sc + dx * 0.42f * s, cy + dy * 0.42f * s, stroke);
                }
                canvas.drawLine(cx + 0.24f * s, cy, cx + 0.46f * s, cy, stroke);
                if (icon == SUN_UP) {
                    canvas.drawLine(cx + 0.35f * s, cy - 0.11f * s, cx + 0.35f * s, cy + 0.11f * s, stroke);
                }
                break;
            }
            case MENU: {
                for (int i = -1; i <= 1; i++) {
                    float y = cy + i * 0.28f * s;
                    canvas.drawLine(cx - 0.32f * s, y, cx + 0.32f * s, y, stroke);
                }
                break;
            }
            case POWER: {
                canvas.drawArc(new RectF(cx - 0.34f * s, cy - 0.30f * s,
                        cx + 0.34f * s, cy + 0.38f * s), -60f, 300f, false, stroke);
                canvas.drawLine(cx, cy - 0.44f * s, cx, cy - 0.02f * s, stroke);
                break;
            }
            case UP_ARROW: {
                canvas.drawLine(cx, cy + 0.36f * s, cx, cy - 0.34f * s, stroke);
                canvas.drawLine(cx - 0.26f * s, cy - 0.08f * s, cx, cy - 0.34f * s, stroke);
                canvas.drawLine(cx + 0.26f * s, cy - 0.08f * s, cx, cy - 0.34f * s, stroke);
                break;
            }
            case DOWN_ARROW: {
                canvas.drawLine(cx, cy - 0.36f * s, cx, cy + 0.34f * s, stroke);
                canvas.drawLine(cx - 0.26f * s, cy + 0.08f * s, cx, cy + 0.34f * s, stroke);
                canvas.drawLine(cx + 0.26f * s, cy + 0.08f * s, cx, cy + 0.34f * s, stroke);
                break;
            }
            default:
                break;
        }
    }

    private static void speaker(Path p, float cx, float cy, float s) {
        p.reset();
        p.moveTo(cx - 0.46f * s, cy - 0.17f * s);
        p.lineTo(cx - 0.28f * s, cy - 0.17f * s);
        p.lineTo(cx - 0.06f * s, cy - 0.40f * s);
        p.lineTo(cx - 0.06f * s, cy + 0.40f * s);
        p.lineTo(cx - 0.28f * s, cy + 0.17f * s);
        p.lineTo(cx - 0.46f * s, cy + 0.17f * s);
        p.close();
    }
}
