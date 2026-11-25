package com.example.gryntsivgames.uno;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Typeface;

public class CardPainter {
    private static final int INK = 0xFF111111;
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint txt = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path leaf = new Path();
    private final Path path2 = new Path();
    private final RectF rf = new RectF();
    public Theme th;

    public CardPainter(Theme t) {
        th = t;
        txt.setTypeface(Typeface.DEFAULT_BOLD);
        txt.setStrokeJoin(Paint.Join.ROUND);
    }

    private void outlined(Canvas c, String s, float x, float base, float size, int color, Paint.Align al, boolean shadow) {
        txt.setTextSize(size);
        txt.setTextAlign(al);
        if (shadow) {
            txt.setStyle(Paint.Style.FILL_AND_STROKE);
            txt.setStrokeWidth(size * 0.16f);
            txt.setColor(INK);
            c.drawText(s, x + size * 0.08f, base + size * 0.08f, txt);
        }
        txt.setStyle(Paint.Style.STROKE);
        txt.setStrokeWidth(size * 0.17f);
        txt.setColor(INK);
        c.drawText(s, x, base, txt);
        txt.setStyle(Paint.Style.FILL);
        txt.setColor(color);
        c.drawText(s, x, base, txt);
    }

    private void body(Canvas c, float x, float y, float w, float h, int base) {
        float rad = w * 0.10f;
        fill.setStyle(Paint.Style.FILL);
        rf.set(x, y, x + w, y + h);
        fill.setColor(0xFFF8F8F8);
        c.drawRoundRect(rf, rad, rad, fill);
        rf.set(x + w * 0.035f, y + w * 0.035f, x + w * 0.965f, y + h - w * 0.035f);
        fill.setColor(base);
        c.drawRoundRect(rf, rad * 0.8f, rad * 0.8f, fill);
    }

    public void draw(Canvas c, Card k, float x, float y, float w, float h, boolean dim) {
        draw(c, k, x, y, w, h, dim ? 1 : 0);
    }

    // dim: 0 — світла, 1 — неактивна (на 20% світліша, ніж раніше)
    public void draw(Canvas c, Card k, float x, float y, float w, float h, int dim) {
        if (th.style == 0) {
            drawClassic(c, k, x, y, w, h);
        } else {
            drawStyled(c, k, x, y, w, h);
        }
        if (dim > 0) {
            float rad = w * 0.10f;
            fill.setStyle(Paint.Style.FILL);
            fill.setColor(0x5C000000);
            rf.set(x, y, x + w, y + h);
            c.drawRoundRect(rf, rad, rad, fill);
        }
    }

    private String symbol(Card k) {
        if (k.v <= 9) return String.valueOf(k.v);
        if (k.v == Engine.SKIP) return "S";
        if (k.v == Engine.REV) return "R";
        if (k.v == Engine.D2) return "D";
        return null;
    }

    private void quarters(Canvas c, float l, float t, float r, float b) {
        float mx = (l + r) / 2f, my = (t + b) / 2f;
        fill.setStyle(Paint.Style.FILL);
        fill.setColor(th.col[0]);
        c.drawRect(l, t, mx, my, fill);
        fill.setColor(th.col[3]);
        c.drawRect(mx, t, r, my, fill);
        fill.setColor(th.col[1]);
        c.drawRect(l, my, mx, b, fill);
        fill.setColor(th.col[2]);
        c.drawRect(mx, my, r, b, fill);
    }

    private void plain(Canvas c, String s, float x, float base, float size, int color, boolean shadow) {
        txt.setTextSize(size);
        txt.setTextAlign(Paint.Align.CENTER);
        txt.setStyle(Paint.Style.FILL);
        if (shadow) {
            txt.setColor(0x66000000);
            c.drawText(s, x + size * 0.05f, base + size * 0.05f, txt);
        }
        txt.setColor(color);
        c.drawText(s, x, base, txt);
    }

    // стилі 1..4: плоска, ретро-овал, неон, мінімал
    private void drawStyled(Canvas c, Card k, float x, float y, float w, float h) {
        boolean wild = k.v >= Engine.WILD;
        int base = wild ? th.black : th.col[k.c];
        float cx = x + w / 2f, cy = y + h / 2f;
        String sym = symbol(k);
        int st = th.style;
        float rad = w * 0.10f;
        int cornerColor = 0xFFFFFFFF;
        boolean cornerOutline = true;
        if (st == 1) {
            body(c, x, y, w, h, base);
            if (sym != null) {
                plain(c, sym, cx, cy + h * 0.17f, h * 0.5f, 0xFFFFFFFF, true);
            } else {
                float r = w * 0.30f;
                c.save();
                path2.reset();
                rf.set(cx - r, cy - r, cx + r, cy + r);
                path2.addRoundRect(rf, new float[]{r, r, r, r, r, r, r, r}, Path.Direction.CW);
                c.clipPath(path2);
                quarters(c, cx - r, cy - r, cx + r, cy + r);
                c.restore();
                if (k.v == Engine.D4) plain(c, "+4", cx, cy + h * 0.06f, h * 0.2f, 0xFFFFFFFF, true);
            }
        } else if (st == 2) {
            body(c, x, y, w, h, base);
            c.save();
            c.rotate(-35f, cx, cy);
            float ow = w * 0.92f, oh = h * 0.5f;
            rf.set(cx - ow / 2f, cy - oh / 2f, cx + ow / 2f, cy + oh / 2f);
            path2.reset();
            path2.addRoundRect(rf, new float[]{oh / 2f, oh / 2f, oh / 2f, oh / 2f, oh / 2f, oh / 2f, oh / 2f, oh / 2f}, Path.Direction.CW);
            if (wild) {
                c.save();
                c.clipPath(path2);
                quarters(c, cx - ow / 2f, cy - oh / 2f, cx + ow / 2f, cy + oh / 2f);
                c.restore();
            } else {
                fill.setStyle(Paint.Style.FILL);
                fill.setColor(th.leaf);
                c.drawPath(path2, fill);
            }
            c.restore();
            if (sym != null) {
                plain(c, sym, cx, cy + h * 0.14f, h * 0.38f, th.col[k.c], false);
            } else if (k.v == Engine.D4) {
                outlined(c, "+4", cx, cy + h * 0.07f, h * 0.2f, 0xFFFFFFFF, Paint.Align.CENTER, false);
            }
        } else if (st == 3) {
            fill.setStyle(Paint.Style.FILL);
            fill.setColor(0xFFF8F8F8);
            rf.set(x, y, x + w, y + h);
            c.drawRoundRect(rf, rad, rad, fill);
            fill.setColor(th.black);
            rf.set(x + w * 0.03f, y + w * 0.03f, x + w * 0.97f, y + h - w * 0.03f);
            c.drawRoundRect(rf, rad * 0.8f, rad * 0.8f, fill);
            int neon = wild ? 0xFFFFFFFF : th.col[k.c];
            fill.setStyle(Paint.Style.STROKE);
            fill.setStrokeWidth(w * 0.05f);
            fill.setColor(neon);
            rf.set(x + w * 0.09f, y + w * 0.09f, x + w * 0.91f, y + h - w * 0.09f);
            c.drawRoundRect(rf, rad * 0.6f, rad * 0.6f, fill);
            cornerColor = neon;
            cornerOutline = false;
            if (sym != null) {
                float size = h * 0.46f;
                txt.setTextSize(size);
                txt.setTextAlign(Paint.Align.CENTER);
                txt.setStyle(Paint.Style.STROKE);
                txt.setStrokeWidth(size * 0.16f);
                txt.setColor((neon & 0x00FFFFFF) | 0x55000000);
                c.drawText(sym, cx, cy + h * 0.15f, txt);
                plain(c, sym, cx, cy + h * 0.15f, size, neon, false);
            } else {
                float r = w * 0.27f;
                fill.setStrokeWidth(w * 0.09f);
                int[] order = {0, 3, 2, 1};
                rf.set(cx - r, cy - r, cx + r, cy + r);
                for (int i = 0; i < 4; i++) {
                    fill.setColor(th.col[order[i]]);
                    c.drawArc(rf, -90f + i * 90f + 6f, 78f, false, fill);
                }
                if (k.v == Engine.D4) plain(c, "+4", cx, cy + h * 0.06f, h * 0.18f, 0xFFFFFFFF, false);
            }
            fill.setStyle(Paint.Style.FILL);
        } else {
            fill.setStyle(Paint.Style.FILL);
            fill.setColor(0xFFDDDDDD);
            rf.set(x, y, x + w, y + h);
            c.drawRoundRect(rf, rad, rad, fill);
            fill.setColor(th.leaf);
            rf.set(x + w * 0.02f, y + w * 0.02f, x + w * 0.98f, y + h - w * 0.02f);
            c.drawRoundRect(rf, rad * 0.9f, rad * 0.9f, fill);
            int edge = wild ? th.black : th.col[k.c];
            fill.setStyle(Paint.Style.STROKE);
            fill.setStrokeWidth(w * 0.07f);
            fill.setColor(edge);
            rf.set(x + w * 0.07f, y + w * 0.07f, x + w * 0.93f, y + h - w * 0.07f);
            c.drawRoundRect(rf, rad * 0.7f, rad * 0.7f, fill);
            fill.setStyle(Paint.Style.FILL);
            cornerColor = edge;
            cornerOutline = false;
            if (sym != null) {
                plain(c, sym, cx, cy + h * 0.16f, h * 0.48f, edge, false);
            } else {
                float r = w * 0.26f;
                c.save();
                path2.reset();
                rf.set(cx - r, cy - r, cx + r, cy + r);
                path2.addRoundRect(rf, new float[]{r, r, r, r, r, r, r, r}, Path.Direction.CW);
                c.clipPath(path2);
                quarters(c, cx - r, cy - r, cx + r, cy + r);
                c.restore();
                if (k.v == Engine.D4) plain(c, "+4", cx, cy + h * 0.06f, h * 0.18f, 0xFFFFFFFF, true);
            }
        }
        styledCorner(c, k, x, y, w, h, cornerColor, cornerOutline);
        c.save();
        c.rotate(180f, cx, cy);
        styledCorner(c, k, x, y, w, h, cornerColor, cornerOutline);
        c.restore();
    }

    private void styledCorner(Canvas c, Card k, float x, float y, float w, float h, int color, boolean outline) {
        String s;
        float size;
        if (k.v <= 9) {
            s = String.valueOf(k.v);
            size = h * 0.11f;
        } else {
            s = Engine.label(k);
            size = h * 0.082f;
        }
        if (outline) {
            outlined(c, s, x + w * 0.1f, y + h * 0.15f, size, color, Paint.Align.LEFT, false);
        } else {
            txt.setTextSize(size);
            txt.setTextAlign(Paint.Align.LEFT);
            txt.setStyle(Paint.Style.FILL);
            txt.setColor(color);
            c.drawText(s, x + w * 0.1f, y + h * 0.15f, txt);
        }
    }

    private void drawClassic(Canvas c, Card k, float x, float y, float w, float h) {
        boolean wildCard = k.v >= Engine.WILD;
        body(c, x, y, w, h, wildCard ? th.black : th.col[k.c]);
        float lx = x + w * 0.09f, ly = y + h * 0.21f, lr = x + w * 0.91f, lb = y + h * 0.83f;
        float big = Math.min(lr - lx, lb - ly) * 0.55f;
        float small = big * 0.25f;
        leaf.reset();
        rf.set(lx, ly, lr, lb);
        leaf.addRoundRect(rf, new float[]{big, big, small, small, big, big, small, small}, Path.Direction.CW);
        fill.setStyle(Paint.Style.FILL);
        if (k.v == Engine.D4) {
            c.save();
            c.clipPath(leaf);
            float mx = (lx + lr) / 2f, my = (ly + lb) / 2f;
            fill.setColor(th.col[0]);
            c.drawRect(lx, ly, mx, my, fill);
            fill.setColor(th.col[3]);
            c.drawRect(mx, ly, lr, my, fill);
            fill.setColor(th.col[1]);
            c.drawRect(lx, my, mx, lb, fill);
            fill.setColor(th.col[2]);
            c.drawRect(mx, my, lr, lb, fill);
            c.restore();
        } else {
            fill.setColor(th.leaf);
            c.drawPath(leaf, fill);
        }
        float cx = (lx + lr) / 2f, cy = (ly + lb) / 2f, lh = lb - ly;
        if (k.v <= 9 || k.v == Engine.SKIP || k.v == Engine.REV || k.v == Engine.D2) {
            String s;
            if (k.v <= 9) s = String.valueOf(k.v);
            else if (k.v == Engine.SKIP) s = "S";
            else if (k.v == Engine.REV) s = "R";
            else s = "D";
            float size = lh * 0.78f;
            outlined(c, s, cx, cy + size * 0.36f, size, th.col[k.c], Paint.Align.CENTER, true);
        } else if (k.v == Engine.WILD) {
            float size = lh * 0.36f;
            String[] letters = {"W", "i", "l", "d"};
            int[] cols = {th.col[1], th.col[3], th.col[0], th.col[2]};
            txt.setTextSize(size);
            float total = 0;
            float[] ws = new float[4];
            for (int i = 0; i < 4; i++) {
                ws[i] = txt.measureText(letters[i]);
                total += ws[i];
            }
            float px = cx - total / 2f;
            for (int i = 0; i < 4; i++) {
                outlined(c, letters[i], px, cy + size * 0.36f, size, cols[i], Paint.Align.LEFT, true);
                px += ws[i];
            }
        } else {
            float size = lh * 0.34f;
            outlined(c, "Wild", cx, cy + size * 0.36f, size, 0xFFFFFFFF, Paint.Align.CENTER, true);
        }
        corner(c, k, x, y, w, h);
        c.save();
        c.rotate(180f, x + w / 2f, y + h / 2f);
        corner(c, k, x, y, w, h);
        c.restore();
    }

    private void corner(Canvas c, Card k, float x, float y, float w, float h) {
        String s;
        float size;
        if (k.v <= 9) {
            s = String.valueOf(k.v);
            size = h * 0.11f;
        } else {
            s = Engine.label(k);
            size = h * 0.088f;
        }
        outlined(c, s, x + w * 0.09f, y + h * 0.145f, size, 0xFFFFFFFF, Paint.Align.LEFT, false);
    }

    public void back(Canvas c, float x, float y, float w, float h) {
        body(c, x, y, w, h, th.black);
        c.save();
        c.clipRect(x + w * 0.04f, y + w * 0.04f, x + w * 0.96f, y + h - w * 0.04f);
        float ang = (float) Math.toDegrees(Math.atan2(h, w));
        c.rotate(-ang, x + w / 2f, y + h / 2f);
        float diag = (float) Math.sqrt(w * w + h * h);
        rf.set(x + w / 2f - diag * 0.46f, y + h / 2f - w * 0.27f, x + w / 2f + diag * 0.46f, y + h / 2f + w * 0.27f);
        fill.setStyle(Paint.Style.FILL);
        fill.setColor(th.col[0]);
        c.drawOval(rf, fill);
        float size = diag * 0.165f;
        outlined(c, "Gryntsiv", x + w / 2f, y + h / 2f + size * 0.36f, size, th.col[1], Paint.Align.CENTER, true);
        c.restore();
    }
}
