package com.example.gryntsivgames.durak;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Typeface;

import com.example.gryntsivgames.uno.Card;
import com.example.gryntsivgames.uno.CardPainter;
import com.example.gryntsivgames.uno.Theme;

// Малювання карт Дурака (36 карт): масті 0 піки, 1 чирви, 2 бубни, 3 хрести
public class DuCards {
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint t = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private final RectF rf = new RectF();
    public Theme th;
    private final CardPainter back;

    // стиль колоди Дурака: 0 класична, 1 портрети, 2 королівська, 3 нічна
    public int ds;
    public static final String[] DECKS = {"Класична", "Портрети", "Королівська", "Нічна"};

    public DuCards(Theme th) {
        this.th = th;
        back = new CardPainter(th);
        t.setTypeface(Typeface.DEFAULT_BOLD);
    }

    public static Theme themeOf(int ds) {
        return Theme.get(ds == 1 ? 1 : (ds == 2 ? 2 : (ds == 3 ? 3 : 0)));
    }

    public void setStyle(int ds) {
        this.ds = ds < 0 || ds >= DECKS.length ? 0 : ds;
        setTheme(themeOf(this.ds));
    }

    public void setTheme(Theme th) {
        this.th = th;
        back.th = th;
    }

    public void back(Canvas c, float x, float y, float w, float h) {
        back.th = th;
        back.back(c, x, y, w, h);
    }

    public static String rank(int v) {
        if (v <= 10) return String.valueOf(v);
        return v == 11 ? "В" : v == 12 ? "Д" : v == 13 ? "К" : "Т";
    }

    public int suitColor(int s) {
        boolean red = s == 1 || s == 2;
        if (th.style == 3) return red ? 0xFFFF1744 : 0xFF00E5FF;
        if (th.style == 4) return red ? 0xFFE57373 : 0xFF455A64;
        return red ? th.col[0] : th.black;
    }

    // малює масть із центром (cx, cy) і розміром s
    public void suit(Canvas c, int su, float cx, float cy, float s, int color) {
        p.setStyle(Paint.Style.FILL);
        p.setColor(color);
        path.reset();
        if (su == 1) {
            c.drawCircle(cx - s * 0.25f, cy - s * 0.18f, s * 0.29f, p);
            c.drawCircle(cx + s * 0.25f, cy - s * 0.18f, s * 0.29f, p);
            path.moveTo(cx - s * 0.52f, cy - s * 0.08f);
            path.lineTo(cx + s * 0.52f, cy - s * 0.08f);
            path.lineTo(cx, cy + s * 0.55f);
            path.close();
            c.drawPath(path, p);
        } else if (su == 2) {
            path.moveTo(cx, cy - s * 0.58f);
            path.lineTo(cx + s * 0.42f, cy);
            path.lineTo(cx, cy + s * 0.58f);
            path.lineTo(cx - s * 0.42f, cy);
            path.close();
            c.drawPath(path, p);
        } else if (su == 0) {
            c.drawCircle(cx - s * 0.25f, cy + s * 0.1f, s * 0.29f, p);
            c.drawCircle(cx + s * 0.25f, cy + s * 0.1f, s * 0.29f, p);
            path.moveTo(cx - s * 0.52f, cy + s * 0.0f);
            path.lineTo(cx + s * 0.52f, cy + s * 0.0f);
            path.lineTo(cx, cy - s * 0.58f);
            path.close();
            c.drawPath(path, p);
            path.reset();
            path.moveTo(cx - s * 0.08f, cy + s * 0.1f);
            path.lineTo(cx + s * 0.08f, cy + s * 0.1f);
            path.lineTo(cx + s * 0.22f, cy + s * 0.55f);
            path.lineTo(cx - s * 0.22f, cy + s * 0.55f);
            path.close();
            c.drawPath(path, p);
        } else {
            c.drawCircle(cx, cy - s * 0.27f, s * 0.26f, p);
            c.drawCircle(cx - s * 0.27f, cy + s * 0.08f, s * 0.26f, p);
            c.drawCircle(cx + s * 0.27f, cy + s * 0.08f, s * 0.26f, p);
            c.drawRect(cx - s * 0.1f, cy - s * 0.2f, cx + s * 0.1f, cy + s * 0.15f, p);
            path.moveTo(cx - s * 0.08f, cy + s * 0.1f);
            path.lineTo(cx + s * 0.08f, cy + s * 0.1f);
            path.lineTo(cx + s * 0.22f, cy + s * 0.55f);
            path.lineTo(cx - s * 0.22f, cy + s * 0.55f);
            path.close();
            c.drawPath(path, p);
        }
    }

    private void text(Canvas c, String s, float x, float base, float size, int color) {
        t.setTextSize(size);
        t.setTextAlign(Paint.Align.CENTER);
        t.setStyle(Paint.Style.FILL);
        t.setColor(color);
        c.drawText(s, x, base, t);
    }

    private void font() {
        if (ds == 1) t.setTypeface(Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD));
        else if (ds == 2) t.setTypeface(Typeface.create(Typeface.SERIF, Typeface.BOLD_ITALIC));
        else if (ds == 3) t.setTypeface(Typeface.create(Typeface.MONOSPACE, Typeface.BOLD));
        else t.setTypeface(Typeface.DEFAULT_BOLD);
    }

    private void shape(Canvas c, int fill, int line, float sw) {
        p.setStyle(Paint.Style.FILL);
        p.setColor(fill);
        c.drawPath(path, p);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(sw);
        p.setColor(line);
        c.drawPath(path, p);
        p.setStyle(Paint.Style.FILL);
    }

    private void disc(Canvas c, float cx, float cy, float r, int fill, int line, float sw) {
        p.setStyle(Paint.Style.FILL);
        p.setColor(fill);
        c.drawCircle(cx, cy, r, p);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(sw);
        p.setColor(line);
        c.drawCircle(cx, cy, r, p);
        p.setStyle(Paint.Style.FILL);
    }

    private void faceBlock(Canvas c, float x, float y, float w, float h, Card k, int col, float rad) {
        float fx0 = x + w * 0.38f, fx1 = x + w * 0.78f, fy0 = y + h * 0.12f, fy1 = y + h * 0.49f;
        p.setStyle(Paint.Style.FILL);
        p.setColor(ds == 3 ? 0xFF0A0A12 : (ds == 2 ? 0x40D4A017 : (col & 0x00FFFFFF) | 0x30000000));
        rf.set(fx0, fy0, fx1, fy1);
        c.drawRoundRect(rf, rad * 0.6f, rad * 0.6f, p);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(w * (ds == 2 ? 0.02f : 0.014f));
        p.setColor(ds == 3 ? col : (ds == 2 ? 0xFFB8860B : 0xFF3E2723));
        c.drawRoundRect(rf, rad * 0.6f, rad * 0.6f, p);
        if (ds == 2) {
            p.setStrokeWidth(w * 0.008f);
            p.setColor(0xFFD4A017);
            rf.set(fx0 + w * 0.02f, fy0 + w * 0.02f, fx1 - w * 0.02f, fy1 - w * 0.02f);
            c.drawRoundRect(rf, rad * 0.4f, rad * 0.4f, p);
            p.setStyle(Paint.Style.FILL);
            p.setColor(0xFFD4A017);
            float dd = w * 0.035f;
            float[][] cs = {{fx0, fy0}, {fx1, fy0}, {fx0, fy1}, {fx1, fy1}};
            for (float[] q : cs) {
                path.reset();
                path.moveTo(q[0], q[1] - dd);
                path.lineTo(q[0] + dd, q[1]);
                path.lineTo(q[0], q[1] + dd);
                path.lineTo(q[0] - dd, q[1]);
                path.close();
                c.drawPath(path, p);
            }
        }
        p.setStyle(Paint.Style.FILL);
        c.save();
        c.translate((fx0 + fx1) / 2f, (fy0 + fy1) / 2f + h * 0.005f);
        float bw = w * 0.36f, bh = h * 0.29f;
        if (ds == 1) bustCartoon(c, k.v, bw, bh, col);
        else if (ds == 3) bustNeon(c, k.v, bw, bh, col);
        else bust(c, k.v, bw, bh, col);
        c.restore();
    }

    private void glow(Canvas c, int color, float sw) {
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(sw * 3f);
        p.setColor((color & 0x00FFFFFF) | 0x33000000);
        c.drawPath(path, p);
        p.setStrokeWidth(sw);
        p.setColor(color);
        c.drawPath(path, p);
        p.setStyle(Paint.Style.FILL);
    }

    // неонові контури
    private void bustNeon(Canvas c, int v, float bw, float bh, int col) {
        float sw = Math.max(1f, bw * 0.03f);
        float hr = bh * 0.22f, hy = -bh * 0.04f;
        path.reset();
        path.moveTo(-bw * 0.46f, bh * 0.5f);
        path.lineTo(-bw * 0.3f, bh * 0.2f);
        path.lineTo(-hr * 0.5f, bh * 0.14f);
        path.moveTo(hr * 0.5f, bh * 0.14f);
        path.lineTo(bw * 0.3f, bh * 0.2f);
        path.lineTo(bw * 0.46f, bh * 0.5f);
        glow(c, col, sw);
        path.reset();
        rf.set(-hr, hy - hr * 1.1f, hr, hy + hr * 1.1f);
        path.addOval(rf, Path.Direction.CW);
        glow(c, col, sw);
        p.setColor(0xFFFFFFFF);
        p.setStyle(Paint.Style.FILL);
        c.drawCircle(-hr * 0.38f, hy - hr * 0.1f, hr * 0.1f, p);
        c.drawCircle(hr * 0.38f, hy - hr * 0.1f, hr * 0.1f, p);
        path.reset();
        if (v == 11) {
            path.moveTo(-hr * 1.1f, hy - hr * 0.5f);
            path.quadTo(0, hy - hr * 2.3f, hr * 1.1f, hy - hr * 0.5f);
            path.lineTo(hr * 1.9f, hy - hr * 0.3f);
            glow(c, col, sw);
            path.reset();
            path.moveTo(hr * 0.5f, hy - hr * 1.5f);
            path.quadTo(hr * 1.9f, hy - hr * 2.3f, hr * 1.6f, hy - hr * 0.9f);
            glow(c, 0xFFFFFFFF, sw * 0.8f);
        } else if (v == 12) {
            path.moveTo(-hr * 1.05f, hy - hr * 0.4f);
            path.quadTo(-hr * 1.7f, hy + hr * 1.5f, -hr * 0.9f, hy + hr * 2.4f);
            path.moveTo(hr * 1.05f, hy - hr * 0.4f);
            path.quadTo(hr * 1.7f, hy + hr * 1.5f, hr * 0.9f, hy + hr * 2.4f);
            glow(c, col, sw);
            path.reset();
            path.moveTo(-hr * 0.9f, hy - hr * 0.9f);
            path.lineTo(-hr * 0.5f, hy - hr * 1.6f);
            path.lineTo(0, hy - hr * 1.15f);
            path.lineTo(hr * 0.5f, hy - hr * 1.6f);
            path.lineTo(hr * 0.9f, hy - hr * 0.9f);
            glow(c, 0xFFFFFFFF, sw * 0.8f);
        } else {
            path.moveTo(-hr * 1.1f, hy - hr * 0.8f);
            path.lineTo(-hr * 1.25f, hy - hr * 2.0f);
            path.lineTo(-hr * 0.6f, hy - hr * 1.4f);
            path.lineTo(0, hy - hr * 2.2f);
            path.lineTo(hr * 0.6f, hy - hr * 1.4f);
            path.lineTo(hr * 1.25f, hy - hr * 2.0f);
            path.lineTo(hr * 1.1f, hy - hr * 0.8f);
            path.close();
            glow(c, 0xFFFFFFFF, sw * 0.8f);
            path.reset();
            path.moveTo(-hr * 0.9f, hy + hr * 0.3f);
            path.quadTo(0, hy + hr * 2.4f, hr * 0.9f, hy + hr * 0.3f);
            glow(c, col, sw);
        }
        if (v != 13) {
            path.reset();
            path.moveTo(-hr * 0.3f, hy + hr * 0.55f);
            path.quadTo(0, hy + hr * 0.85f, hr * 0.3f, hy + hr * 0.55f);
            glow(c, col, sw * 0.8f);
        }
    }

    // мультяшні портрети
    private void bustCartoon(Canvas c, int v, float bw, float bh, int col) {
        int ink = 0xFF3E2723;
        float sw = Math.max(1f, bw * 0.03f);
        float hr = bh * 0.27f, hy = -bh * 0.02f;
        if (v == 12) {
            disc(c, -hr * 1.0f, hy + hr * 0.2f, hr * 0.5f, 0xFF8D4E2A, ink, sw);
            disc(c, hr * 1.0f, hy + hr * 0.2f, hr * 0.5f, 0xFF8D4E2A, ink, sw);
        }
        path.reset();
        rf.set(-bw * 0.38f, bh * 0.14f, bw * 0.38f, bh * 0.72f);
        path.addRoundRect(rf, bh * 0.2f, bh * 0.2f, Path.Direction.CW);
        shape(c, col, ink, sw);
        p.setColor(0xFFFFFFFF);
        c.drawCircle(0, bh * 0.34f, bw * 0.035f, p);
        c.drawCircle(0, bh * 0.5f, bw * 0.035f, p);
        disc(c, 0, hy, hr, 0xFFFFCC99, ink, sw);
        if (v == 13) {
            path.reset();
            path.moveTo(-hr * 0.9f, hy + hr * 0.2f);
            path.quadTo(0, hy + hr * 0.1f, hr * 0.9f, hy + hr * 0.2f);
            path.quadTo(hr * 0.8f, hy + hr * 1.7f, 0, hy + hr * 1.75f);
            path.quadTo(-hr * 0.8f, hy + hr * 1.7f, -hr * 0.9f, hy + hr * 0.2f);
            path.close();
            shape(c, 0xFFF5F5F5, ink, sw);
            rf.set(-hr * 0.7f, hy + hr * 0.15f, hr * 0.7f, hy + hr * 0.55f);
            path.reset();
            path.addOval(rf, Path.Direction.CW);
            shape(c, 0xFFE0E0E0, ink, sw);
        }
        p.setStyle(Paint.Style.FILL);
        p.setColor(0x99FF8A80);
        c.drawCircle(-hr * 0.58f, hy + hr * 0.38f, hr * 0.17f, p);
        c.drawCircle(hr * 0.58f, hy + hr * 0.38f, hr * 0.17f, p);
        disc(c, -hr * 0.38f, hy - hr * 0.1f, hr * 0.2f, 0xFFFFFFFF, ink, sw * 0.7f);
        disc(c, hr * 0.38f, hy - hr * 0.1f, hr * 0.2f, 0xFFFFFFFF, ink, sw * 0.7f);
        p.setColor(0xFF000000);
        c.drawCircle(-hr * 0.34f, hy - hr * 0.08f, hr * 0.1f, p);
        c.drawCircle(hr * 0.42f, hy - hr * 0.08f, hr * 0.1f, p);
        if (v != 13) {
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(sw);
            p.setColor(ink);
            rf.set(-hr * 0.42f, hy + hr * 0.12f, hr * 0.42f, hy + hr * 0.7f);
            c.drawArc(rf, 20f, 140f, false, p);
            p.setStyle(Paint.Style.FILL);
        }
        if (v == 12) {
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(sw);
            p.setColor(ink);
            c.drawLine(-hr * 0.6f, hy - hr * 0.32f, -hr * 0.8f, hy - hr * 0.45f, p);
            c.drawLine(hr * 0.6f, hy - hr * 0.32f, hr * 0.8f, hy - hr * 0.45f, p);
            p.setStyle(Paint.Style.FILL);
            disc(c, -hr * 0.45f, hy - hr * 1.0f, hr * 0.38f, col, ink, sw);
            disc(c, hr * 0.45f, hy - hr * 1.0f, hr * 0.38f, col, ink, sw);
            disc(c, 0, hy - hr * 1.0f, hr * 0.2f, 0xFFFFEB3B, ink, sw);
        } else if (v == 11) {
            rf.set(-hr * 1.1f, hy - hr * 1.4f, hr * 1.1f, hy - hr * 0.25f);
            path.reset();
            path.addOval(rf, Path.Direction.CW);
            shape(c, col, ink, sw);
            path.reset();
            path.moveTo(hr * 0.4f, hy - hr * 1.25f);
            path.quadTo(hr * 1.9f, hy - hr * 2.1f, hr * 1.7f, hy - hr * 0.6f);
            path.quadTo(hr * 1.2f, hy - hr * 1.3f, hr * 0.4f, hy - hr * 0.9f);
            path.close();
            shape(c, 0xFFFFEB3B, ink, sw);
        } else {
            float cw = hr * 1.1f, top = hy - hr * 1.9f;
            path.reset();
            path.moveTo(-cw, hy - hr * 0.6f);
            path.lineTo(-cw * 1.1f, top);
            path.lineTo(-cw * 0.45f, hy - hr * 1.3f);
            path.lineTo(0, top - hr * 0.2f);
            path.lineTo(cw * 0.45f, hy - hr * 1.3f);
            path.lineTo(cw * 1.1f, top);
            path.lineTo(cw, hy - hr * 0.6f);
            path.close();
            shape(c, 0xFFFFCA28, ink, sw);
            disc(c, 0, hy - hr * 1.0f, hr * 0.16f, col, ink, sw * 0.7f);
        }
    }

    // погруддя: v 11 валет, 12 дама, 13 король; центр (0,0), ширина bw, висота bh
    private void bust(Canvas c, int v, float bw, float bh, int col) {
        int ink = ds == 3 ? 0xFF000000 : 0xFF2A1A12;
        int skin = ds == 3 ? 0xFFE8C9A0 : 0xFFFFD9B3;
        int gold = ds == 2 ? 0xFFD4A017 : 0xFFFFC107;
        int hair = v == 13 ? 0xFF8D6E63 : (v == 12 ? 0xFF4E342E : 0xFF6D4C41);
        float sw = Math.max(1f, bw * 0.022f);
        float hr = bh * 0.23f, hy = -bh * 0.03f;
        if (v == 12) {
            // довге волосся позаду голови
            path.reset();
            rf.set(-hr * 1.45f, hy - hr * 1.2f, hr * 1.45f, hy + hr * 2.6f);
            path.addRoundRect(rf, hr * 0.8f, hr * 0.8f, Path.Direction.CW);
            shape(c, hair, ink, sw);
        }
        // плечі
        path.reset();
        path.moveTo(-bw * 0.46f, bh * 0.5f);
        path.lineTo(-bw * 0.28f, bh * 0.14f);
        path.lineTo(bw * 0.28f, bh * 0.14f);
        path.lineTo(bw * 0.46f, bh * 0.5f);
        path.close();
        shape(c, col, ink, sw);
        // комір
        path.reset();
        if (v == 13) {
            rf.set(-bw * 0.3f, bh * 0.1f, bw * 0.3f, bh * 0.26f);
            path.addRoundRect(rf, bh * 0.08f, bh * 0.08f, Path.Direction.CW);
            shape(c, 0xFFFFFFFF, ink, sw);
            p.setColor(ink);
            for (int k = -2; k <= 2; k++) c.drawCircle(k * bw * 0.11f, bh * 0.18f, bw * 0.017f, p);
        } else if (v == 12) {
            path.moveTo(-bw * 0.14f, bh * 0.14f);
            path.lineTo(0, bh * 0.36f);
            path.lineTo(bw * 0.14f, bh * 0.14f);
            path.close();
            shape(c, skin, ink, sw);
            p.setColor(gold);
            for (int k = -2; k <= 2; k++) c.drawCircle(k * bw * 0.06f, bh * 0.15f + Math.abs(k) * bh * -0.0f + (2 - Math.abs(k)) * bh * 0.03f, bw * 0.02f, p);
        } else {
            path.moveTo(-bw * 0.16f, bh * 0.14f);
            path.lineTo(0, bh * 0.32f);
            path.lineTo(bw * 0.16f, bh * 0.14f);
            path.close();
            shape(c, 0xFFFFFFFF, ink, sw);
        }
        if (ds == 2) {
            rf.set(-bw * 0.42f, bh * 0.14f, bw * 0.42f, bh * 0.3f);
            path.reset();
            path.addOval(rf, Path.Direction.CW);
            shape(c, 0xFFFFFFFF, ink, sw);
            p.setColor(0xFF000000);
            for (int q = -3; q <= 3; q++) {
                if (q == 0) continue;
                rf.set(q * bw * 0.1f - bw * 0.012f, bh * 0.18f, q * bw * 0.1f + bw * 0.012f, bh * 0.25f);
                c.drawOval(rf, p);
            }
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(sw * 1.4f);
            p.setColor(0xFFD4A017);
            rf.set(-bw * 0.3f, bh * 0.2f, bw * 0.3f, bh * 0.52f);
            c.drawArc(rf, 20f, 140f, false, p);
            p.setStyle(Paint.Style.FILL);
        }
        // шия й голова
        p.setColor(skin);
        c.drawRect(-hr * 0.35f, hy + hr * 0.7f, hr * 0.35f, bh * 0.16f, p);
        disc(c, 0, hy, hr, skin, ink, sw);
        if (v == 13) {
            // борода
            path.reset();
            path.moveTo(-hr * 0.95f, hy + hr * 0.1f);
            path.quadTo(-hr * 0.9f, hy + hr * 1.75f, 0, hy + hr * 1.7f);
            path.quadTo(hr * 0.9f, hy + hr * 1.75f, hr * 0.95f, hy + hr * 0.1f);
            path.quadTo(0, hy + hr * 0.75f, -hr * 0.95f, hy + hr * 0.1f);
            path.close();
            shape(c, 0xFFE0E0E0, ink, sw);
        }
        // очі
        p.setColor(ink);
        c.drawCircle(-hr * 0.38f, hy - hr * 0.05f, hr * 0.1f, p);
        c.drawCircle(hr * 0.38f, hy - hr * 0.05f, hr * 0.1f, p);
        if (v != 13) {
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(sw);
            rf.set(-hr * 0.3f, hy + hr * 0.2f, hr * 0.3f, hy + hr * 0.62f);
            c.drawArc(rf, 20f, 140f, false, p);
            p.setStyle(Paint.Style.FILL);
        } else {
            p.setColor(ink);
            c.drawRect(-hr * 0.32f, hy + hr * 0.32f, hr * 0.32f, hy + hr * 0.4f, p);
        }
        // головний убір
        path.reset();
        if (v == 11) {
            rf.set(-hr * 1.15f, hy - hr * 1.5f, hr * 1.15f, hy - hr * 0.35f);
            path.addOval(rf, Path.Direction.CW);
            shape(c, col, ink, sw);
            path.reset();
            path.moveTo(hr * 0.5f, hy - hr * 1.3f);
            path.quadTo(hr * 1.7f, hy - hr * 2.0f, hr * 1.6f, hy - hr * 0.7f);
            path.quadTo(hr * 1.3f, hy - hr * 1.4f, hr * 0.5f, hy - hr * 1.0f);
            path.close();
            shape(c, 0xFFFFFFFF, ink, sw);
            path.reset();
            path.moveTo(-hr * 1.0f, hy - hr * 0.35f);
            path.lineTo(-hr * 1.0f, hy - hr * 0.1f);
            path.lineTo(hr * 1.0f, hy - hr * 0.1f);
            path.lineTo(hr * 1.0f, hy - hr * 0.35f);
            path.close();
            shape(c, hair, ink, sw);
        } else {
            float cw = v == 13 ? hr * 1.4f : hr * 1.15f;
            float top = hy - hr * (v == 13 ? 2.1f : 1.8f);
            path.moveTo(-cw, hy - hr * 0.7f);
            path.lineTo(-cw, top + hr * 0.2f);
            path.lineTo(-cw * 0.5f, hy - hr * 1.35f);
            path.lineTo(0, top);
            path.lineTo(cw * 0.5f, hy - hr * 1.35f);
            path.lineTo(cw, top + hr * 0.2f);
            path.lineTo(cw, hy - hr * 0.7f);
            path.close();
            shape(c, gold, ink, sw);
            p.setColor(col);
            c.drawCircle(0, hy - hr * 1.0f, hr * 0.17f, p);
            p.setColor(0xFFFFFFFF);
            c.drawCircle(-cw * 0.62f, hy - hr * 0.95f, hr * 0.11f, p);
            c.drawCircle(cw * 0.62f, hy - hr * 0.95f, hr * 0.11f, p);
        }
    }

    // розкладка мастей: рядки по y (0..1), колонки -1, 0, 1
    private static final float[][][] PIPS = {
        {{-1, 0}, {1, 0}, {-1, .5f}, {1, .5f}, {-1, 1}, {1, 1}},
        {{-1, 0}, {1, 0}, {0, .25f}, {-1, .5f}, {1, .5f}, {-1, 1}, {1, 1}},
        {{-1, 0}, {1, 0}, {0, .25f}, {-1, .5f}, {1, .5f}, {0, .75f}, {-1, 1}, {1, 1}},
        {{-1, 0}, {1, 0}, {-1, .333f}, {1, .333f}, {0, .5f}, {-1, .667f}, {1, .667f}, {-1, 1}, {1, 1}},
        {{-1, 0}, {1, 0}, {0, .167f}, {-1, .333f}, {1, .333f}, {-1, .667f}, {1, .667f}, {0, .833f}, {-1, 1}, {1, 1}}
    };

    // dim: 0 звичайна, 1 неактивна
    public void draw(Canvas c, Card k, float x, float y, float w, float h, int dim) {
        float rad = w * 0.10f;
        int face = th.style == 3 ? 0xFF14141C : (th.style == 2 ? 0xFFFFF3D6 : (th.style == 4 ? 0xFFFAF3E6 : 0xFFFAFAFA));
        int edge = th.style == 3 ? 0xFF00E5FF : (ds == 2 ? 0xFF8D6A12 : 0xFF222222);
        font();
        p.setStyle(Paint.Style.FILL);
        p.setColor(edge);
        rf.set(x, y, x + w, y + h);
        c.drawRoundRect(rf, rad, rad, p);
        p.setColor(face);
        rf.set(x + w * 0.03f, y + w * 0.03f, x + w * 0.97f, y + h - w * 0.03f);
        c.drawRoundRect(rf, rad * 0.8f, rad * 0.8f, p);
        if (ds == 2) {
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(w * 0.018f);
            p.setColor(0xFFD4A017);
            rf.set(x + w * 0.07f, y + w * 0.07f, x + w * 0.93f, y + h - w * 0.07f);
            c.drawRoundRect(rf, rad * 0.6f, rad * 0.6f, p);
            p.setStyle(Paint.Style.FILL);
        }
        int col = suitColor(k.c);
        String r = rank(k.v);
        boolean newStyle = ds != 0;
        float ix = w * 0.27f;
        float fs = r.length() > 1 ? w * 0.25f : w * 0.29f;
        float ty = y + h * 0.2f;
        float sy = y + h * 0.32f;
        float ss = w * 0.18f;
        if (!newStyle || k.v < 11 || k.v > 13) {
            if (k.v >= 11 && k.v <= 13) {
                p.setColor((col & 0x00FFFFFF) | 0x22000000);
                rf.set(x + w * 0.3f, y + h * 0.32f, x + w * 0.7f, y + h * 0.68f);
                c.drawRoundRect(rf, rad, rad, p);
                text(c, r, x + w * 0.5f, y + h * 0.55f, w * 0.42f, col);
                suit(c, k.c, x + w * 0.5f, y + h * 0.71f, w * 0.18f, col);
            } else {
                suit(c, k.c, x + w * 0.5f, y + h * 0.5f, w * (k.v == 14 ? 0.7f : 0.5f), col);
            }
        } else {
            faceBlock(c, x, y, w, h, k, col, rad);
            c.save();
            c.rotate(180f, x + w / 2f, y + h / 2f);
            faceBlock(c, x, y, w, h, k, col, rad);
            c.restore();
        }
        text(c, r, x + ix, ty, fs, col);
        suit(c, k.c, x + ix, sy, ss, col);
        c.save();
        c.rotate(180f, x + w / 2f, y + h / 2f);
        text(c, r, x + ix, ty, fs, col);
        suit(c, k.c, x + ix, sy, ss, col);
        c.restore();
        if (dim != 0) {
            p.setStyle(Paint.Style.FILL);
            p.setColor(0x66000000);
            rf.set(x, y, x + w, y + h);
            c.drawRoundRect(rf, rad, rad, p);
        }
    }
}
