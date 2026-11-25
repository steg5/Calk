package com.example.gryntsivgames.sea;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.os.Handler;
import android.os.Looper;
import android.view.MotionEvent;
import android.view.View;
import com.example.gryntsivgames.uno.Theme;

public class BattleView extends View {
    public interface Listener {
        void onShoot(int cell);

        // торкання свого/вибраного поля зліва: to = -1 усім, інакше конкретному гравцю
        void onEmoji(int to);

        void onPoke(int to);

        void onSelect(int idx);

        void onRestoreTap(int cell);
    }

    public static class Lay {
        public float u, ec, mx, ex, y0, midX, midW, cx, fc, fleetBottom, is;
    }

    public static Lay layout(float W, float H) {
        Lay l = new Lay();
        l.u = Math.min(W / 16f, H / 9f);
        float u = l.u;
        l.ec = Math.min((H - 1.3f * u) / 10.85f, (W - 0.9f * u - 3.9f * u) / 20f);
        l.midW = W - 0.9f * u - 20 * l.ec;
        l.mx = 0.85f * l.ec;
        l.midX = l.mx + 10 * l.ec;
        l.ex = l.midX + l.midW;
        l.y0 = H - 10 * l.ec - 0.15f * u;
        l.cx = (l.midX + 0.1f * u + l.ex - 0.85f * l.ec) / 2f;
        float zone = l.ex - 0.85f * l.ec - (l.midX + 0.1f * u);
        l.fc = Math.min(0.43f * u, (zone - 0.2f * u) / 6.8f);
        l.fleetBottom = 1.2f * u + 4 * 1.3f * l.fc;
        l.is = Math.max(0.5f * u, Math.min(0.96f * u, (l.y0 + 10 * l.ec - l.fleetBottom - 0.2f * u - 0.48f * u) / 5f));
        return l;
    }

    public Board[] boards;
    public int n = 2, me, sel, rightIdx = 1, turn;
    public String[] names = new String[0];
    public boolean[] alive = new boolean[0];
    public boolean[] bots = new boolean[0];
    private int lpTo = -1;
    public int[] last = new int[8];
    public Listener listener;
    public int theme;
    public String msg = "";
    public boolean myTurn, reveal;
    // відновлення корабля: 0 ні, 1 вибір корабля, 2 вибір місця
    public int rMode, rShip = -1, gr, gc;
    public boolean gh = true, gOk;
    public int tgtOfTurn = -1;
    private int crossCell = -1, pulseCell = -1, pulseRes, pulseBoard = -1;
    private long crossUntil, pulseStart;
    private String banner = "";
    private int bannerColor;
    private long bannerStart, bannerUntil;
    private String emo = "";
    private int emoFrom = -1;
    private long emoUntil;
    private long shakeUntil;
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rf = new RectF();
    private final Handler hd = new Handler(Looper.getMainLooper());
    private Runnable lp;
    private boolean lpFired;
    private float dx0, dy0;

    public BattleView(Context c) {
        super(c);
        for (int i = 0; i < last.length; i++) last[i] = -1;
    }

    public void showEmoji(int from, String e) {
        emo = e;
        emoFrom = from;
        emoUntil = System.currentTimeMillis() + 2600;
        invalidate();
    }

    public void shake() {
        shakeUntil = System.currentTimeMillis() + 500;
        invalidate();
    }

    public void cross(int cell) {
        crossCell = cell;
        crossUntil = System.currentTimeMillis() + 700;
        invalidate();
    }

    public void pulse(int board, int cell, int res) {
        pulseBoard = board;
        pulseCell = cell;
        pulseRes = res;
        pulseStart = System.currentTimeMillis();
        invalidate();
    }

    public void banner(String s, int color, long ms) {
        banner = s;
        bannerColor = color;
        bannerStart = System.currentTimeMillis();
        bannerUntil = bannerStart + ms;
        invalidate();
    }

    private float panelTop(Lay l) {
        return l.y0 - 0.65f * l.ec;
    }

    // прямокутники ніків над лівою панеллю: [x,y,w,h]
    private float[] chip(Lay l, int i) {
        int per = n;
        int rows = (n + per - 1) / per;
        float ch = 0.5f * l.u, gap = 0.06f * l.u;
        float w = (10 * l.ec - (per - 1) * gap) / per;
        float top = panelTop(l) - 0.1f * l.u - rows * ch - (rows - 1) * gap;
        int r = i / per, c = i % per;
        return new float[]{l.mx + c * (w + gap), top + r * (ch + gap), w, ch};
    }

    private void nameLabel(Canvas cv, String s, float x, float y, float maxW, float u, int color) {
        p.setStyle(Paint.Style.FILL);
        p.setTextAlign(Paint.Align.CENTER);
        p.setColor(color);
        p.setTextSize(u * 0.4f);
        float w = p.measureText(s);
        if (w > maxW) p.setTextSize(u * 0.4f * maxW / w);
        cv.drawText(s, x, y, p);
    }

    private void drawChip(Canvas cv, Lay l, int i) {
        float[] c = chip(l, i);
        float u = l.u;
        int col = Theme.pcol(i);
        boolean dead = i < alive.length && !alive[i];
        boolean mover = i == turn && !dead;
        boolean dim = i == rightIdx && i != me;
        p.setStyle(Paint.Style.FILL);
        p.setColor(dead ? 0x66263238 : (i == sel ? (0xFF000000 | (col & 0xFFFFFF)) : (0x99000000 | (col & 0xFFFFFF))));
        if (dim) p.setColor(0x55263238);
        rf.set(c[0], c[1], c[0] + c[2], c[1] + c[3]);
        cv.drawRoundRect(rf, c[3] * 0.3f, c[3] * 0.3f, p);
        if ((i == sel && !dim) || mover) {
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(u * (mover ? 0.09f : 0.05f));
            p.setColor(mover ? 0xFFFFEB3B : 0xFFFFFFFF);
            cv.drawRoundRect(rf, c[3] * 0.3f, c[3] * 0.3f, p);
        }
        float tx = c[0] + c[2] / 2f;
        float maxW = c[2] - 0.3f * u;
        if (i == tgtOfTurn && !dead) {
            maxW -= 0.2f * u;
            tx += 0.1f * u;
            p.setStyle(Paint.Style.FILL);
            p.setColor(0xFFFFEB3B);
            android.graphics.Path q = new android.graphics.Path();
            float ax = c[0] + 0.14f * u, ay = c[1] + c[3] / 2f, k = 0.15f * u;
            q.moveTo(ax, ay - k);
            q.lineTo(ax + k * 1.2f, ay);
            q.lineTo(ax, ay + k);
            q.close();
            cv.drawPath(q, p);
        }
        String s = names[i];
        p.setTextAlign(Paint.Align.CENTER);
        p.setStyle(Paint.Style.FILL);
        p.setColor(dead || dim ? 0x88FFFFFF : 0xFFFFFFFF);
        p.setTextSize(u * 0.34f);
        float w = p.measureText(s);
        if (w > maxW) p.setTextSize(u * 0.34f * maxW / w);
        cv.drawText(s, tx, c[1] + c[3] * 0.7f, p);
        if (dead) {
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(u * 0.05f);
            p.setColor(0xCCFFFFFF);
            cv.drawLine(c[0] + 0.1f * u, c[1] + c[3] / 2f, c[0] + c[2] - 0.1f * u, c[1] + c[3] / 2f, p);
            p.setStyle(Paint.Style.FILL);
        }
    }

    private void drawShipOutline(Canvas cv, Board.Ship s, float ox, float oy, float ec, int color) {
        float w = s.h ? s.len * ec : ec, h = s.h ? ec : s.len * ec;
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(ec * 0.1f);
        p.setColor(color);
        rf.set(ox + s.c * ec, oy + s.r * ec, ox + s.c * ec + w, oy + s.r * ec + h);
        cv.drawRoundRect(rf, ec * 0.15f, ec * 0.15f, p);
        p.setStyle(Paint.Style.FILL);
    }

    @Override
    protected void onDraw(Canvas cv) {
        Lay l = layout(getWidth(), getHeight());
        float u = l.u;
        long now = System.currentTimeMillis();
        Scheme.drawBg(cv, getWidth(), getHeight());
        if (boards == null || boards.length < n) return;
        cv.save();
        if (shakeUntil > now) {
            float k = (shakeUntil - now) / 500f;
            cv.translate((float) Math.sin(now / 28.0) * u * 0.18f * k, 0);
        }
        Board left = boards[sel], right = boards[rightIdx];
        float pt = panelTop(l);
        Scheme.panel(cv, p, l.mx - 0.8f * l.ec, pt, l.mx + 10.2f * l.ec, l.y0 + 10.2f * l.ec, 0.3f * l.ec, 0x66000000);
        Scheme.panel(cv, p, l.ex - 0.8f * l.ec, pt, l.ex + 10.2f * l.ec, l.y0 + 10.2f * l.ec, 0.3f * l.ec, 0x66000000);
        Painter.board(cv, p, right, l.ex, l.y0, l.ec, reveal, theme, last[rightIdx], true);
        Painter.board(cv, p, left, l.mx, l.y0, l.ec, sel == me || reveal, theme, last[sel], true, rMode == 0);
        // відновлення корабля
        if (rMode > 0 && sel == me) {
            Board b = boards[me];
            float pulse = 0.5f + 0.5f * (float) Math.sin(now / 160.0);
            for (Board.Ship s : b.ships) {
                if (b.sunk(s) && (rMode == 1 || s.idx != rShip)) {
                    drawShipOutline(cv, s, l.mx, l.y0, l.ec, ((int) (120 + 135 * pulse) << 24) | 0x69F0AE);
                }
            }
            if (rMode == 2 && rShip >= 0) {
                Board.Ship s = b.ships[rShip];
                drawShipOutline(cv, s, l.mx, l.y0, l.ec, 0xFFFFEB3B);
                if (gr >= 0) {
                    p.setStyle(Paint.Style.FILL);
                    p.setColor(gOk ? 0x9969F0AE : 0x99FF5252);
                    for (int k = 0; k < s.len; k++) {
                        int rr = gh ? gr : gr + k, cc = gh ? gc + k : gc;
                        if (rr < 0 || cc < 0 || rr >= Board.N || cc >= Board.N) continue;
                        rf.set(l.mx + cc * l.ec + l.ec * 0.06f, l.y0 + rr * l.ec + l.ec * 0.06f, l.mx + (cc + 1) * l.ec - l.ec * 0.06f, l.y0 + (rr + 1) * l.ec - l.ec * 0.06f);
                        cv.drawRoundRect(rf, l.ec * 0.15f, l.ec * 0.15f, p);
                    }
                }
            }
        }
        p.setStyle(Paint.Style.FILL);
        p.setTextAlign(Paint.Align.CENTER);
        p.setTextSize(u * 0.4f);
        float mw = p.measureText(msg);
        if (mw > getWidth() * 0.9f) p.setTextSize(u * 0.4f * getWidth() * 0.9f / mw);
        p.setColor(myTurn ? 0xFF69F0AE : 0xFFFFD54F);
        cv.drawText(msg, getWidth() / 2f, u * 0.5f, p);
        // ніки
        if (n > 2) {
            for (int i = 0; i < n; i++) drawChip(cv, l, i);
        } else {
            nameLabel(cv, names[me], l.mx + 5 * l.ec, pt - 0.18f * u, l.ec * 10, u, turn == me ? 0xFFFFEB3B : 0xFFE0E6EA);
        }
        nameLabel(cv, names[rightIdx], l.ex + 5 * l.ec, pt - 0.18f * u, l.ec * 10, u, turn == rightIdx ? 0xFFFFEB3B : 0xFFE0E6EA);
        // флот цілі посередині
        p.setTextAlign(Paint.Align.CENTER);
        p.setTextSize(u * 0.32f);
        p.setColor(0xFFB0BEC5);
        float cx = l.cx;
        float zone = l.ex - 0.85f * l.ec - (l.midX + 0.1f * u);
        cv.drawText("Флот ворога", cx, u * 1.0f, p);
        float fc = l.fc, gap = fc * 0.4f, rowH = fc * 1.3f;
        int ns = right.ships.length;
        int[] row = new int[ns];
        float[] rowW = new float[12];
        int r = 0;
        for (int i = 0; i < ns; i++) {
            if (i > 0 && right.ships[i].len != right.ships[i - 1].len) r++;
            row[i] = r;
            rowW[r] += (rowW[r] > 0 ? gap : 0) + right.ships[i].len * fc;
        }
        float maxW = 0;
        for (int i = 0; i <= r; i++) maxW = Math.max(maxW, rowW[i]);
        float lf = cx - maxW / 2f;
        float[] pos = new float[12];
        for (int i = 0; i < ns; i++) {
            Board.Ship s = right.ships[i];
            float x = lf + pos[row[i]];
            float y = u * 1.2f + row[i] * rowH;
            int th = s.theme >= 0 ? s.theme : theme;
            Painter.draw(cv, p, th, x, y, fc, s.len, true, s.idx);
            if (right.sunk(s)) {
                p.setStyle(Paint.Style.FILL);
                p.setColor(0xB8000000);
                rf.set(x, y + fc * 0.05f, x + s.len * fc, y + fc * 0.95f);
                cv.drawRoundRect(rf, fc * 0.15f, fc * 0.15f, p);
            }
            pos[row[i]] += s.len * fc + gap;
        }
        p.setStyle(Paint.Style.FILL);
        boolean anim = rMode > 0 || shakeUntil > now;
        if (crossUntil > now && crossCell >= 0) {
            float cx2 = l.ex + (crossCell % 10) * l.ec + l.ec / 2f, cy2 = l.y0 + (crossCell / 10) * l.ec + l.ec / 2f;
            float al = Math.min(1f, (crossUntil - now) / 250f);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(l.ec * 0.12f);
            p.setColor(0xFFFF9800);
            p.setAlpha((int) (255 * al));
            float d = l.ec * 0.24f;
            cv.drawCircle(cx2, cy2, l.ec * 0.34f, p);
            cv.drawLine(cx2 - d, cy2 - d, cx2 + d, cy2 + d, p);
            p.setAlpha(255);
            p.setStyle(Paint.Style.FILL);
            anim = true;
        }
        if (pulseCell >= 0 && now - pulseStart < 600) {
            float bx = pulseBoard == rightIdx ? l.ex : (pulseBoard == sel ? l.mx : -1f);
            if (bx >= 0) {
                float f = (now - pulseStart) / 600f;
                float cx2 = bx + (pulseCell % 10) * l.ec + l.ec / 2f, cy2 = l.y0 + (pulseCell / 10) * l.ec + l.ec / 2f;
                p.setStyle(Paint.Style.STROKE);
                p.setStrokeWidth(l.ec * 0.09f);
                p.setColor(pulseRes == 0 ? 0xFFE1F5FE : (pulseRes == 1 ? 0xFFFFB300 : 0xFFFFFFFF));
                p.setAlpha((int) (255 * (1f - f)));
                cv.drawCircle(cx2, cy2, l.ec * (0.2f + 0.9f * f), p);
                p.setAlpha(255);
                p.setStyle(Paint.Style.FILL);
                anim = true;
            }
        }
        if (emoUntil > now) {
            float ex = emoFrom == rightIdx ? l.ex + 5 * l.ec : l.mx + 5 * l.ec;
            emo(cv, emo, ex, l.y0 + 5 * l.ec, emoUntil - now, u);
            if (emoFrom != rightIdx && emoFrom != sel && emoFrom >= 0 && emoFrom < names.length) {
                nameLabel(cv, names[emoFrom], ex, l.y0 + 7.2f * l.ec, l.ec * 8, u, 0xFFFFFFFF);
            }
            anim = true;
        }
        cv.restore();
        if (bannerUntil > now) {
            long el = now - bannerStart, left2 = bannerUntil - now;
            float sc = el < 250 ? 0.5f + 0.5f * el / 250f : 1f;
            float al = left2 < 350 ? left2 / 350f : 1f;
            p.setTextAlign(Paint.Align.CENTER);
            p.setTextSize(u * 1.6f * sc);
            float by = l.y0 + 5 * l.ec + u * 0.6f * sc;
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(u * 0.14f);
            p.setColor(0xFF000000);
            p.setAlpha((int) (255 * al));
            cv.drawText(banner, getWidth() / 2f, by, p);
            p.setStyle(Paint.Style.FILL);
            p.setColor(bannerColor);
            p.setAlpha((int) (255 * al));
            cv.drawText(banner, getWidth() / 2f, by, p);
            p.setAlpha(255);
            anim = true;
        }
        if (anim) postInvalidateDelayed(40);
    }

    private void emo(Canvas cv, String e, float x, float y, long left, float u) {
        float k = left > 400 ? 1f : left / 400f;
        p.setTextAlign(Paint.Align.CENTER);
        p.setTextSize(u * 2.2f * (0.6f + 0.4f * k));
        p.setAlpha((int) (255 * k));
        p.setColor(0xFFFFFFFF);
        cv.drawText(e, x, y + u * 0.7f, p);
        p.setAlpha(255);
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        Lay l = layout(getWidth(), getHeight());
        float x = e.getX(), y = e.getY();
        boolean inLeft = x >= l.mx && x < l.mx + 10 * l.ec && y >= l.y0 && y < l.y0 + 10 * l.ec;
        int a = e.getAction();
        if (a == MotionEvent.ACTION_DOWN) {
            lpFired = false;
            dx0 = x;
            dy0 = y;
            cancelLp();
            lpTo = -1;
            if (listener != null && rMode == 0) {
                if (inLeft && sel != me) {
                    lpTo = sel;
                } else {
                    if (n > 2) {
                        for (int i = 0; i < n; i++) {
                            float[] c = chip(l, i);
                            if (i != me && i < bots.length && bots[i] && x >= c[0] && x < c[0] + c[2] && y >= c[1] && y < c[1] + c[3]) lpTo = i;
                        }
                    }
                    if (lpTo < 0 && inRightArea(l, x, y) && rightIdx != me && rightIdx >= 0 && rightIdx < n) lpTo = rightIdx;
                }
            }
            if (lpTo >= 0) {
                lp = new Runnable() {
                    public void run() {
                        lpFired = true;
                        if (listener != null && lpTo >= 0) listener.onPoke(lpTo);
                    }
                };
                hd.postDelayed(lp, 600);
            }
            return true;
        }
        if (a == MotionEvent.ACTION_MOVE) {
            if (Math.abs(x - dx0) > l.u * 0.3f || Math.abs(y - dy0) > l.u * 0.3f) cancelLp();
            return true;
        }
        if (a == MotionEvent.ACTION_CANCEL) {
            cancelLp();
            return true;
        }
        if (a != MotionEvent.ACTION_UP) return true;
        cancelLp();
        if (lpFired || listener == null) return true;
        if (n > 2) {
            for (int i = 0; i < n; i++) {
                float[] c = chip(l, i);
                if (x >= c[0] && x < c[0] + c[2] && y >= c[1] && y < c[1] + c[3]) {
                    if (i != rightIdx || i == me) listener.onSelect(i);
                    return true;
                }
            }
        }
        if (n > 2 && rMode == 0 && rightIdx != me && x >= l.ex && x < l.ex + 10 * l.ec && y >= panelTop(l) - 0.9f * l.u && y < panelTop(l) + 0.1f * l.u) {
            listener.onEmoji(rightIdx);
            return true;
        }
        if (inLeft) {
            int c = (int) ((x - l.mx) / l.ec), r = (int) ((y - l.y0) / l.ec);
            if (rMode > 0) listener.onRestoreTap(r * 10 + c);
            else listener.onEmoji(sel == me ? -1 : sel);
            return true;
        }
        int c = (int) Math.floor((x - l.ex) / l.ec), r = (int) Math.floor((y - l.y0) / l.ec);
        if (c < 0 || r < 0 || c >= 10 || r >= 10) return true;
        listener.onShoot(r * 10 + c);
        return true;
    }

    private boolean inRightArea(Lay l, float x, float y) {
        return x >= l.ex && x < l.ex + 10 * l.ec && y >= panelTop(l) - 0.9f * l.u && y < l.y0 + 10 * l.ec;
    }

    private void cancelLp() {
        if (lp != null) hd.removeCallbacks(lp);
        lp = null;
    }
}
