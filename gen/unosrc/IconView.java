package com.example.gryntsivgames.uno;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.view.View;

public class IconView extends View {
    public static final int GEAR = 0, PAUSE = 1, PLAY = 2, SOUND = 3, MUSIC = 4, DND = 5, TURN = 6, EXIT = 7, EYE = 8;
    private int bgCol;
    private int type;
    private boolean crossed;
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rf = new RectF();
    private final Path path = new Path();

    public IconView(Context c, int type) {
        super(c);
        this.type = type;
    }

    public void setCrossed(boolean c) {
        crossed = c;
        invalidate();
    }

    private boolean pulse, flip;

    public void setFlip(boolean f) {
        if (flip == f) return;
        flip = f;
        invalidate();
    }

    public void setPulse(boolean on) {
        if (pulse == on) return;
        pulse = on;
        invalidate();
    }

    public void setBg(int c) {
        bgCol = c;
        invalidate();
    }

    public void setType(int t) {
        type = t;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas c) {
        float w = getWidth(), h = getHeight();
        float k = 0f;
        if (pulse) k = 0.5f + 0.5f * (float) Math.sin(System.currentTimeMillis() * 2.0 * Math.PI / 2000.0);
        rf.set(0, 0, w, h);
        p.setStyle(Paint.Style.FILL);
        p.setColor(bgCol != 0 ? bgCol : 0x88263238);
        c.drawRoundRect(rf, h * 0.25f, h * 0.25f, p);
        float cx = w / 2f, cy = h / 2f;
        float r = Math.min(w, h) * 0.30f;
        p.setColor(0xFFFFFFFF);
        if (type == GEAR) {
            for (int i = 0; i < 8; i++) {
                c.save();
                c.rotate(i * 45f, cx, cy);
                c.drawRect(cx - r * 0.2f, cy - r * 1.15f, cx + r * 0.2f, cy - r * 0.6f, p);
                c.restore();
            }
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(r * 0.42f);
            c.drawCircle(cx, cy, r * 0.78f, p);
            p.setStyle(Paint.Style.FILL);
        } else if (type == PAUSE) {
            c.drawRect(cx - r * 0.75f, cy - r, cx - r * 0.2f, cy + r, p);
            c.drawRect(cx + r * 0.2f, cy - r, cx + r * 0.75f, cy + r, p);
        } else if (type == SOUND) {
            c.drawRect(cx - r * 1.0f, cy - r * 0.38f, cx - r * 0.45f, cy + r * 0.38f, p);
            path.reset();
            path.moveTo(cx - r * 0.45f, cy - r * 0.38f);
            path.lineTo(cx + r * 0.15f, cy - r * 0.9f);
            path.lineTo(cx + r * 0.15f, cy + r * 0.9f);
            path.lineTo(cx - r * 0.45f, cy + r * 0.38f);
            path.close();
            c.drawPath(path, p);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(r * 0.2f);
            rf.set(cx - r * 0.45f, cy - r * 0.6f, cx + r * 0.55f, cy + r * 0.6f);
            c.drawArc(rf, -50, 100, false, p);
            rf.set(cx - r * 0.8f, cy - r * 1.0f, cx + r * 0.95f, cy + r * 1.0f);
            c.drawArc(rf, -50, 100, false, p);
            p.setStyle(Paint.Style.FILL);
        } else if (type == MUSIC) {
            c.drawCircle(cx - r * 0.4f, cy + r * 0.6f, r * 0.42f, p);
            c.drawRect(cx - r * 0.22f, cy - r * 0.95f, cx + r * 0.02f, cy + r * 0.6f, p);
            path.reset();
            path.moveTo(cx - r * 0.22f, cy - r * 0.95f);
            path.lineTo(cx + r * 0.8f, cy - r * 0.45f);
            path.lineTo(cx + r * 0.8f, cy - r * 0.1f);
            path.lineTo(cx - r * 0.22f, cy - r * 0.5f);
            path.close();
            c.drawPath(path, p);
        } else if (type == DND) {
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(r * 0.26f);
            c.drawCircle(cx, cy, r * 0.82f, p);
            c.drawLine(cx - r * 0.58f, cy - r * 0.58f, cx + r * 0.58f, cy + r * 0.58f, p);
            p.setStyle(Paint.Style.FILL);
        } else if (type == TURN) {
            if (flip) c.scale(-1f, 1f, cx, cy);
            c.drawRect(cx - r * 0.95f, cy - r * 0.26f, cx + r * 0.1f, cy + r * 0.26f, p);
            path.reset();
            path.moveTo(cx - r * 0.05f, cy - r * 0.8f);
            path.lineTo(cx + r * 1.0f, cy);
            path.lineTo(cx - r * 0.05f, cy + r * 0.8f);
            path.close();
            c.drawPath(path, p);
        } else if (type == EXIT) {
            rf.set(cx - r * 1.0f, cy - r * 1.05f, cx - r * 0.1f, cy + r * 1.05f);
            c.drawRoundRect(rf, r * 0.1f, r * 0.1f, p);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(r * 0.24f);
            c.drawLine(cx + r * 0.15f, cy, cx + r * 0.85f, cy, p);
            p.setStyle(Paint.Style.FILL);
            path.reset();
            path.moveTo(cx + r * 1.1f, cy);
            path.lineTo(cx + r * 0.7f, cy - r * 0.38f);
            path.lineTo(cx + r * 0.7f, cy + r * 0.38f);
            path.close();
            c.drawPath(path, p);
        } else if (type == EYE) {
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(r * 0.22f);
            rf.set(cx - r * 1.05f, cy - r * 0.62f, cx + r * 1.05f, cy + r * 0.62f);
            c.drawOval(rf, p);
            p.setStyle(Paint.Style.FILL);
            c.drawCircle(cx, cy, r * 0.3f, p);
        } else {
            if (flip) {
                c.save();
                c.scale(-1f, 1f, cx, cy);
            }
            path.reset();
            path.moveTo(cx - r * 0.6f, cy - r);
            path.lineTo(cx + r * 0.9f, cy);
            path.lineTo(cx - r * 0.6f, cy + r);
            path.close();
            c.drawPath(path, p);
            if (flip) c.restore();
        }
        if (crossed) {
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeCap(Paint.Cap.ROUND);
            p.setStrokeWidth(r * 0.42f);
            p.setColor(0xFF263238);
            c.drawLine(cx - r * 1.15f, cy - r * 1.15f, cx + r * 1.15f, cy + r * 1.15f, p);
            p.setStrokeWidth(r * 0.26f);
            p.setColor(0xFFFF5252);
            c.drawLine(cx - r * 1.15f, cy - r * 1.15f, cx + r * 1.15f, cy + r * 1.15f, p);
            p.setStyle(Paint.Style.FILL);
        }
        if (pulse) {
            float sw = h * 0.07f;
            rf.set(sw / 2f, sw / 2f, w - sw / 2f, h - sw / 2f);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(sw);
            p.setColor(0xFFFFFFFF);
            p.setAlpha((int) (70 + 185 * k));
            c.drawRoundRect(rf, h * 0.22f, h * 0.22f, p);
            p.setAlpha(255);
            p.setStyle(Paint.Style.FILL);
            postInvalidateDelayed(40);
        }
    }
}
