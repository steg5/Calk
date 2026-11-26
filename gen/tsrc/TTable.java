package com.example.gryntsivgames.thousand;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.os.Handler;
import android.os.Looper;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;

import com.example.gryntsivgames.durak.DuCards;
import com.example.gryntsivgames.uno.Avatars;
import com.example.gryntsivgames.uno.Bg;
import com.example.gryntsivgames.uno.Card;
import com.example.gryntsivgames.uno.Theme;

import java.util.ArrayList;

// Стіл «1000»: гравці, прикуп, взятка, козир, моя рука. Панель торгів, повзунок ставки й кнопки кутів малює екран (ThousandActivity).
// Взаємодія: дотик до карти (хід або вибір карти для віддачі), потім до аватара (віддати), кнопка «Мар'яж» над рукою.
public class TTable extends View {
    public interface Listener {
        // хід картою; mar = true, якщо оголошено мар'яж цією картою
        void onPlay(Card k, boolean mar);

        // віддати карту k гравцеві to (фаза віддачі)
        void onGive(Card k, int to);

        void onAvatarTap(int idx);

        void onAvatarLong(int idx);

        void onWrong();

        void onDnd();

        void onMic(int idx, boolean down);
    }

    public Listener listener;
    public boolean hint = true;
    public boolean micOk;
    private final boolean[] listening = new boolean[4];
    private int micIdx = -1;
    private int bgId;
    private static final int TXT = 0xFFFFFFFF;
    private final DuCards painter;
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint tp = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rf = new RectF();
    private final Path path = new Path();
    private final Handler lh = new Handler(Looper.getMainLooper());
    private final int slop;

    private int n, me;
    private String[] names = new String[0];
    private int[] avatars = new int[0];
    private String[] photos = new String[0];
    private boolean[] bots = new boolean[0];
    private long[] emoUntil = new long[0];
    private String[] emoVal = new String[0];
    private TState g;
    private Card selGive;
    private String crossEnc;
    private long crossUntil;
    private final java.util.HashMap<Integer, Long> shake = new java.util.HashMap<Integer, Long>();
    private String banner;
    private long bannerAt;
    // анімація мар'яжу й показ останньої взятки
    private int fxSuit = -1, fxWho = -1;
    private long fxAt;
    private final ArrayList<Card> ghost = new ArrayList<Card>();
    private final ArrayList<Integer> ghostBy = new ArrayList<Integer>();
    private int ghostWin = -1;
    private long ghostAt;
    private static final int GHOST_HOLD = 900, GHOST_FADE = 500;

    private float scroll, downX, downY, startScroll;
    private boolean moved, longFired, dndPress;
    private int pressIdx = -1;
    private float u, avR, cxC, cyC, tcw, tch;
    private float[] sx = new float[0];
    private float[] sy = new float[0];
    private float[] hx = new float[0];
    private float hStep, hCw, hCh, hTop, hRaise, areaL, areaR, maxScroll;
    private final int[] marSuit = new int[4];
    private final Card[] marCard = new Card[4];
    private int marCnt;
    private final float[][] marRect = new float[4][4];

    private final Runnable longRun = new Runnable() {
        public void run() {
            if (pressIdx >= 0 && !moved) {
                longFired = true;
                if (listener != null) listener.onAvatarLong(pressIdx);
            }
        }
    };

    public TTable(Context ctx, int style) {
        super(ctx);
        painter = new DuCards(DuCards.themeOf(style));
        painter.setStyle(style);
        slop = ViewConfiguration.get(ctx).getScaledTouchSlop();
        tp.setTypeface(Typeface.DEFAULT_BOLD);
    }

    public void setStyle(int style) {
        painter.setStyle(style);
        invalidate();
    }

    public void setBg(int id) {
        bgId = id;
        invalidate();
    }

    public void setListening(int idx, boolean on) {
        if (idx < 0 || idx >= listening.length) return;
        listening[idx] = on;
        invalidate();
    }

    public void shakeAvatar(int idx) {
        shake.put(idx, System.currentTimeMillis());
        invalidate();
    }

    public void setPlayers(String[] names, int[] avatars, String[] photos, boolean[] bots, int me) {
        this.n = names.length;
        this.names = names;
        this.avatars = avatars;
        this.photos = photos;
        this.bots = bots;
        this.me = me;
        if (emoUntil.length != n) {
            emoUntil = new long[n];
            emoVal = new String[n];
        }
        if (sx.length != n) {
            sx = new float[n];
            sy = new float[n];
        }
        invalidate();
    }

    public void setState(TState s) {
        TState old = g;
        if (s != null && old != null) {
            if (s.roundNo != old.roundNo) {
                selGive = null;
                ghost.clear();
                fxSuit = -1;
            } else {
                for (int k = 0; k < 4 && k < s.marOwner.length && k < old.marOwner.length; k++) {
                    if (old.marOwner[k] < 0 && s.marOwner[k] >= 0) {
                        fxSuit = k;
                        fxWho = s.marOwner[k];
                        fxAt = System.currentTimeMillis();
                    }
                }
                if (s.trickSeq != old.trickSeq && !s.lastTrick.isEmpty()) {
                    ghost.clear();
                    ghostBy.clear();
                    ghost.addAll(s.lastTrick);
                    for (int w : s.lastTrickBy) ghostBy.add(w);
                    ghostWin = s.lastWinner;
                    ghostAt = System.currentTimeMillis();
                }
            }
        }
        if (s == null || s.phase != TEngine.GIVE || s.decl != me || (selGive != null && !holds(s, selGive))) selGive = null;
        g = s;
        invalidate();
    }

    private static boolean holds(TState s, Card c) {
        for (Card k : s.hand) if (k.same(c)) return true;
        return false;
    }

    public void showEmoji(int idx, String e) {
        if (idx < 0 || idx >= emoUntil.length) return;
        emoUntil[idx] = System.currentTimeMillis() + 3000;
        emoVal[idx] = e;
        invalidate();
        postInvalidateDelayed(3100);
    }

    public void showBanner(String s) {
        banner = s;
        bannerAt = System.currentTimeMillis();
        invalidate();
    }

    // ---------- розкладка ----------
    private void calc(int W, int H) {
        u = Math.min(W / 16f, H / 9f);
        avR = u * 0.65f;
        hCw = u * 1.7f;
        hCh = hCw * 1.5f;
        hRaise = u * 0.24f;
        hTop = H - u * 0.12f - hCh;
        areaL = u * 2.6f;
        areaR = W - u * 2.9f;
        cxC = W / 2f;
        cyC = H * 0.45f;
        tcw = u * 1.15f;
        tch = tcw * 1.5f;
        int k = g == null ? 0 : g.hand.size();
        hx = new float[k];
        float avail = areaR - areaL;
        float gap = hCw * 0.28f;
        int gaps = 0;
        for (int i = 1; i < k; i++) if (g.hand.get(i).c != g.hand.get(i - 1).c) gaps++;
        float step = k <= 1 ? 0 : Math.min(hCw * 1.0f, (avail - hCw - gap * gaps) / (k - 1));
        step = Math.max(step, hCw * 0.42f);
        float total = k == 0 ? 0 : hCw + step * (k - 1) + gap * gaps;
        hStep = step;
        float start;
        if (total <= avail) {
            maxScroll = 0;
            scroll = 0;
            start = areaL + (avail - total) / 2f;
        } else {
            maxScroll = total - avail;
            if (scroll < 0) scroll = 0;
            if (scroll > maxScroll) scroll = maxScroll;
            start = areaL - scroll;
        }
        float x = start;
        for (int i = 0; i < k; i++) {
            if (i > 0) x += step + (g.hand.get(i).c != g.hand.get(i - 1).c ? gap : 0);
            hx[i] = x;
        }
        if (n > 0 && sx.length == n && me >= 0 && me < n) {
            sx[me] = u * 1.35f;
            sy[me] = H - u * 1.85f;
            int m = n - 1;
            float top = u * 1.3f, side = u * 4.0f;
            for (int q = 1; q <= m; q++) {
                int idx = (me + q) % n;
                float px, py;
                if (m == 1) {
                    px = W / 2f;
                    py = top;
                } else if (m == 2) {
                    px = q == 1 ? u * 1.4f : W - u * 1.4f;
                    py = side;
                } else {
                    px = q == 1 ? u * 1.4f : (q == 2 ? W / 2f : W - u * 1.4f);
                    py = q == 2 ? top : side;
                }
                sx[idx] = px;
                sy[idx] = py;
            }
        }
        layoutMar();
    }

    // центр слота взятки для гравця idx
    private float slotX(int idx) {
        return cxC + (sx[idx] - cxC) * 0.2f;
    }

    private float slotY(int idx) {
        return cyC + (sy[idx] - cyC) * 0.28f;
    }

    private boolean myMove() {
        return g != null && !g.over && g.paused < 0 && g.cur == me;
    }

    private void layoutMar() {
        marCnt = 0;
        if (g == null || g.phase != TEngine.PLAY || !myMove() || !g.canMarriageNow()) return;
        for (int i = 0; i < g.hand.size(); i++) {
            if (!g.mar.get(i)) continue;
            Card c = g.hand.get(i);
            int at = -1;
            for (int k = 0; k < marCnt; k++) if (marSuit[k] == c.c) at = k;
            if (at < 0) {
                marSuit[marCnt] = c.c;
                marCard[marCnt] = c;
                marCnt++;
            } else if (c.v == 12) {
                marCard[at] = c;
            }
        }
        float bw = u * 2.7f, bh = u * 0.85f, gp = u * 0.2f;
        float y = hTop - hRaise - bh - u * 0.15f;
        float xr = areaR;
        for (int k = 0; k < marCnt; k++) {
            marRect[k][0] = xr - bw;
            marRect[k][1] = y;
            marRect[k][2] = xr;
            marRect[k][3] = y + bh;
            xr -= bw + gp;
        }
    }

    // ---------- малювання ----------
    private void pill(Canvas c, float cx, float cy, String s, int fill, int txt, float size) {
        tp.setTextSize(size);
        tp.setTextAlign(Paint.Align.CENTER);
        float w = tp.measureText(s) + u * 0.4f;
        p.setStyle(Paint.Style.FILL);
        p.setColor(fill);
        rf.set(cx - w / 2f, cy - size * 0.75f, cx + w / 2f, cy + size * 0.35f);
        c.drawRoundRect(rf, size * 0.5f, size * 0.5f, p);
        tp.setStyle(Paint.Style.FILL);
        tp.setColor(txt);
        c.drawText(s, cx, cy, tp);
    }

    private static String cut(String s, int max) {
        return s.length() > max ? s.substring(0, max - 1) + "…" : s;
    }

    private int suitInk(int s) {
        return s == 1 || s == 2 ? 0xFFD50000 : 0xFF263238;
    }

    private void bolt(Canvas c, float x, float y, float s) {
        p.setStyle(Paint.Style.FILL);
        p.setColor(0xFFFFD54F);
        path.reset();
        path.moveTo(x + s * 0.15f, y - s * 0.5f);
        path.lineTo(x - s * 0.35f, y + s * 0.1f);
        path.lineTo(x - s * 0.02f, y + s * 0.1f);
        path.lineTo(x - s * 0.15f, y + s * 0.5f);
        path.lineTo(x + s * 0.38f, y - s * 0.12f);
        path.lineTo(x + s * 0.04f, y - s * 0.12f);
        path.close();
        c.drawPath(path, p);
    }

    private void barrel(Canvas c, float x, float y, float s) {
        p.setStyle(Paint.Style.FILL);
        p.setColor(0xFF8D5A2B);
        rf.set(x - s * 0.38f, y - s * 0.5f, x + s * 0.38f, y + s * 0.5f);
        c.drawRoundRect(rf, s * 0.25f, s * 0.25f, p);
        p.setColor(0xFF3E2723);
        c.drawRect(x - s * 0.38f, y - s * 0.26f, x + s * 0.38f, y - s * 0.17f, p);
        c.drawRect(x - s * 0.38f, y + s * 0.17f, x + s * 0.38f, y + s * 0.26f, p);
    }

    @Override
    protected void onDraw(Canvas c) {
        int W = getWidth(), H = getHeight();
        calc(W, H);
        Bg.draw(c, W, H, bgId);
        if (g == null || n == 0 || sx.length != n || g.counts.length < n || me < 0 || me >= n) return;
        long now = System.currentTimeMillis();
        boolean anim = false;
        boolean pausedNow = g.paused >= 0;
        double pulse = 0.5 + 0.5 * Math.sin(now * 2 * Math.PI / 2000.0);

        // козир
        if (g.trump >= 0) {
            float kx = cxC - u * 4.6f, ky = cyC - u * 1.5f;
            p.setStyle(Paint.Style.FILL);
            p.setColor(0xEEF5F5F0);
            c.drawCircle(kx, ky, u * 0.7f, p);
            painter.suit(c, g.trump, kx, ky, u * 0.75f, suitInk(g.trump));
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(u * 0.05f);
            p.setColor(0xFF37474F);
            c.drawCircle(kx, ky, u * 0.7f, p);
            tp.setTextSize(u * 0.26f);
            tp.setTextAlign(Paint.Align.CENTER);
            tp.setStyle(Paint.Style.FILL);
            tp.setColor(TXT);
            c.drawText("Козир", kx, ky + u * 1.05f, tp);
        }

        // прикуп і підказки в центрі
        if (g.phase == TEngine.BID) {
            int tn = g.talonSize;
            for (int i = 0; i < tn; i++) painter.back(c, cxC - tcw / 2f + (i - (tn - 1) / 2f) * u * 0.5f, cyC - tch / 2f, tcw, tch);
            String s = g.bidder >= 0 && g.bidder < n ? "Ставка " + g.bid + " (" + cut(names[g.bidder], 10) + ")" : "Торги від 100";
            pill(c, cxC, cyC + tch / 2f + u * 0.55f, s, 0x99000000, TXT, u * 0.34f);
        } else if (g.phase == TEngine.GIVE || g.phase == TEngine.FINAL) {
            ArrayList<Card> tl = g.talon;
            int tn = tl.size() > 0 ? tl.size() : 0;
            for (int i = 0; i < tn; i++) painter.draw(c, tl.get(i), cxC - tcw / 2f + (i - (tn - 1) / 2f) * tcw * 1.05f, cyC - tch / 2f - u * 0.3f, tcw, tch, 0);
            String who = g.decl >= 0 && g.decl < n ? cut(names[g.decl], 10) : "";
            String s;
            if (g.phase == TEngine.GIVE) s = g.decl == me ? "Віддайте карти: карта, потім гравець (" + g.needTotal() + ")" : who + " віддає карти";
            else s = g.decl == me ? "Оголосіть ставку" : who + " оголошує ставку";
            pill(c, cxC, cyC + tch / 2f + u * 0.45f, s, 0x99000000, TXT, u * 0.32f);
        }

        // поточна взятка біля гравців
        for (int k = 0; k < g.trick.size() && k < g.trickBy.length; k++) {
            int who = g.trickBy[k];
            if (who < 0 || who >= n) continue;
            drawTrickCard(c, g.trick.get(k), who, false);
        }
        long gt = now - ghostAt;
        if (!ghost.isEmpty() && gt < GHOST_HOLD + GHOST_FADE) {
            int ga = gt > GHOST_HOLD ? (int) (255 * Math.max(0f, 1f - (gt - GHOST_HOLD) / (float) GHOST_FADE)) : 255;
            c.saveLayerAlpha(0, 0, W, H, ga, 31);
            for (int k = 0; k < ghost.size() && k < ghostBy.size(); k++) {
                int who = ghostBy.get(k);
                if (who >= 0 && who < n) drawTrickCard(c, ghost.get(k), who, who == ghostWin);
            }
            c.restore();
            anim = true;
        }

        // анімація мар'яжу
        long ft = now - fxAt;
        if (fxSuit >= 0 && ft < 1900) {
            float a = ft < 250 ? ft / 250f : (ft > 1500 ? (1900 - ft) / 400f : 1f);
            float sc = 1f + 0.12f * (float) Math.sin(ft / 120.0);
            int av = (int) (255 * Math.max(0f, Math.min(1f, a)));
            p.setStyle(Paint.Style.FILL);
            p.setColor((av * 0xEE / 255) << 24 | 0x00F5F5F0);
            c.drawCircle(cxC, cyC - u * 0.2f, u * 1.35f * sc, p);
            painter.suit(c, fxSuit, cxC, cyC - u * 0.2f, u * 1.5f * sc, (av << 24) | (suitInk(fxSuit) & 0x00FFFFFF));
            tp.setTextAlign(Paint.Align.CENTER);
            tp.setTextSize(u * 0.5f);
            tp.setStyle(Paint.Style.FILL);
            tp.setColor((av << 24) | 0x00FFD54F);
            c.drawText("Мар'яж +" + TEngine.MAR[fxSuit], cxC, cyC + u * 1.75f, tp);
            if (fxWho >= 0 && fxWho < n) {
                tp.setTextSize(u * 0.32f);
                tp.setColor((av << 24) | 0x00FFFFFF);
                c.drawText(cut(names[fxWho], 12), cxC, cyC + u * 2.2f, tp);
            }
            tp.setAlpha(255);
            anim = true;
        }

        // кнопки мар'яжу
        for (int k = 0; k < marCnt; k++) {
            float[] r = marRect[k];
            p.setStyle(Paint.Style.FILL);
            p.setColor(0xFFFFD54F);
            rf.set(r[0], r[1], r[2], r[3]);
            c.drawRoundRect(rf, u * 0.25f, u * 0.25f, p);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(u * 0.05f);
            p.setColor(0xFF37474F);
            c.drawRoundRect(rf, u * 0.25f, u * 0.25f, p);
            painter.suit(c, marSuit[k], r[0] + u * 0.5f, (r[1] + r[3]) / 2f, u * 0.5f, suitInk(marSuit[k]));
            tp.setTextAlign(Paint.Align.LEFT);
            tp.setTextSize(u * 0.36f);
            tp.setStyle(Paint.Style.FILL);
            tp.setColor(0xFF263238);
            c.drawText("Мар'яж +" + TEngine.MAR[marSuit[k]], r[0] + u * 0.9f, (r[1] + r[3]) / 2f + u * 0.13f, tp);
            if (pulse > 0.99) anim = true;
        }

        // рука
        c.save();
        c.clipRect(areaL - u * 0.5f, 0, areaR + u * 0.5f, H);
        boolean giving = g.phase == TEngine.GIVE && g.decl == me;
        for (int i = 0; i < g.hand.size(); i++) {
            boolean lg = g.legal.get(i);
            boolean lit = !pausedNow && (lg || !hint);
            boolean sel = selGive != null && selGive.same(g.hand.get(i));
            boolean up = !pausedNow && ((lg && hint) || sel);
            float x = hx[i];
            float y = hTop - (up ? hRaise * (sel ? 2.2f : 1f) : 0);
            Card hc = g.hand.get(i);
            painter.draw(c, hc, x, y, hCw, hCh, lit ? 0 : 1);
            if (sel) {
                p.setStyle(Paint.Style.STROKE);
                p.setStrokeWidth(u * 0.1f);
                p.setColor(0xFFFFD54F);
                rf.set(x, y, x + hCw, y + hCh);
                c.drawRoundRect(rf, hCw * 0.1f, hCw * 0.1f, p);
            }
            if (crossEnc != null && crossEnc.equals(hc.enc()) && crossUntil > now) {
                float cr = u * 0.3f;
                float ccx = x + hCw - cr - u * 0.05f, ccy = y + cr + u * 0.05f;
                p.setStyle(Paint.Style.FILL);
                p.setColor(0xFFD50000);
                c.drawCircle(ccx, ccy, cr, p);
                p.setStyle(Paint.Style.STROKE);
                p.setStrokeWidth(u * 0.07f);
                p.setColor(0xFFFFFFFF);
                c.drawLine(ccx - cr * 0.45f, ccy - cr * 0.45f, ccx + cr * 0.45f, ccy + cr * 0.45f, p);
                c.drawLine(ccx + cr * 0.45f, ccy - cr * 0.45f, ccx - cr * 0.45f, ccy + cr * 0.45f, p);
                anim = true;
            }
        }
        if (g.hand.size() > 0) {
            int last = g.hand.size() - 1;
            float yy = hTop - (!pausedNow && g.legal.get(last) && hint ? hRaise : 0);
            float bx = hx[last] + hCw + u * 0.2f, by = yy - u * 0.2f;
            String pts = String.valueOf(g.handPoints());
            p.setStyle(Paint.Style.FILL);
            p.setColor(0xCC101418);
            c.drawCircle(bx, by, u * 0.34f, p);
            tp.setStyle(Paint.Style.FILL);
            tp.setTextAlign(Paint.Align.CENTER);
            tp.setTextSize(pts.length() > 2 ? u * 0.26f : u * 0.34f);
            tp.setColor(TXT);
            c.drawText(pts, bx, by + u * 0.12f, tp);
        }
        c.restore();
        if (giving) {
            boolean any = false;
            for (int i = 0; i < n; i++) if (i != me && g.needOf(i) > 0) any = true;
            if (any && selGive != null) anim = true;
        }

        if (pausedNow) {
            c.drawColor(0x66000000);
            tp.setStyle(Paint.Style.FILL);
            tp.setTextAlign(Paint.Align.CENTER);
            tp.setColor(TXT);
            tp.setTextSize(u * 1.1f);
            c.drawText("ПАУЗА", W / 2f, cyC - u * 0.1f, tp);
            tp.setTextSize(u * 0.42f);
            String who = g.paused < names.length ? names[g.paused] : "";
            c.drawText("Поставив: " + who, W / 2f, cyC + u * 0.6f, tp);
        }

        for (int i = 0; i < n; i++) {
            if (drawPlayer(c, i, sx[i], sy[i], i == me, now, pulse)) anim = true;
        }
        for (int i = 0; i < n; i++) {
            if (i < emoUntil.length && emoUntil[i] > now && emoVal[i] != null) drawEmoji(c, i, sx[i], sy[i]);
        }

        if (banner != null) {
            long dt = now - bannerAt;
            if (dt > 1800) {
                banner = null;
            } else {
                float a = dt < 300 ? dt / 300f : (dt > 1300 ? (1800 - dt) / 500f : 1f);
                float sc = 0.85f + 0.15f * Math.min(1f, dt / 300f);
                int av = (int) (255 * Math.max(0f, Math.min(1f, a)));
                tp.setTextAlign(Paint.Align.CENTER);
                tp.setTextSize(u * 2.0f * sc);
                tp.setStyle(Paint.Style.STROKE);
                tp.setStrokeWidth(u * 0.14f);
                tp.setColor(0xFF000000);
                tp.setAlpha(av);
                c.drawText(banner, W / 2f, H * 0.5f, tp);
                tp.setStyle(Paint.Style.FILL);
                tp.setColor(0xFFFFD54F);
                tp.setAlpha(av);
                c.drawText(banner, W / 2f, H * 0.5f, tp);
                tp.setAlpha(255);
                anim = true;
            }
        }
        if (anim) postInvalidateDelayed(40);
    }

    private void drawTrickCard(Canvas c, Card k, int who, boolean win) {
        float x = slotX(who) - tcw / 2f, y = slotY(who) - tch / 2f;
        painter.draw(c, k, x, y, tcw, tch, 0);
        if (win) {
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(u * 0.1f);
            p.setColor(0xFFFFD54F);
            rf.set(x, y, x + tcw, y + tch);
            c.drawRoundRect(rf, tcw * 0.1f, tcw * 0.1f, p);
        }
    }

    private boolean drawPlayer(Canvas c, int idx, float cx, float cy, boolean mine, long now, double pulse) {
        if (idx >= names.length) return false;
        boolean anim = false;
        boolean active = !g.over && g.paused < 0 && g.cur == idx;
        int pc = Theme.pcol(idx);
        boolean giveTarget = g.phase == TEngine.GIVE && g.decl == me && selGive != null && idx != me && g.needOf(idx) > 0;
        if (active || giveTarget) {
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(u * 0.07f);
            p.setColor(giveTarget ? 0xFFFFD54F : pc);
            c.drawCircle(cx, cy, avR * 1.12f, p);
            p.setStrokeWidth(u * 0.16f);
            p.setColor((((int) (40 + 150 * pulse)) << 24) | ((giveTarget ? 0xFFD54F : pc) & 0x00FFFFFF));
            c.drawCircle(cx, cy, avR * 1.12f + u * 0.06f + u * 0.1f * (float) pulse, p);
            anim = true;
        }
        Long sk = shake.get(idx);
        float sxs = 0;
        if (sk != null) {
            long dt = now - sk;
            if (dt < 700) {
                sxs = (float) Math.sin(dt / 35.0) * u * 0.16f * (1f - dt / 700f);
                anim = true;
            } else {
                shake.remove(idx);
            }
        }
        boolean passed = g.phase == TEngine.BID && g.passedOf(idx);
        if (passed) c.saveLayer(cx - avR * 2.2f, cy - avR * 2.2f, cx + avR * 2.2f, cy + avR * 2.2f, null);
        Avatars.draw(c, cx + sxs, cy, avR, avatars[idx], Avatars.photo(photos[idx]));
        if (passed) {
            float ex = avR * 2.2f;
            p.setStyle(Paint.Style.FILL);
            p.setColor(0x99000000);
            p.setXfermode(new android.graphics.PorterDuffXfermode(android.graphics.PorterDuff.Mode.SRC_ATOP));
            c.drawRect(cx - ex, cy - ex, cx + ex, cy + ex, p);
            p.setXfermode(null);
            c.restore();
        }
        // ім'я
        tp.setStyle(Paint.Style.FILL);
        tp.setAlpha(255);
        tp.setTextAlign(Paint.Align.CENTER);
        tp.setTextSize(u * 0.3f);
        tp.setColor(pc);
        c.drawText(cut(names[idx], 12), cx, cy + avR + u * 0.4f, tp);
        // рахунок, болти, бочка
        String sc = String.valueOf(g.totalOf(idx));
        int bo = g.boltsOn ? g.boltsOf(idx) : 0;
        boolean br = g.barrelOn && g.onBarrelOf(idx);
        String bs = br ? g.barrelRoundOf(idx) + "/3" : "";
        tp.setTextSize(u * 0.36f);
        float wS = tp.measureText(sc);
        float gp = u * 0.22f, ic = u * 0.34f;
        float tw = wS + (bo > 0 ? gp + ic + tp.measureText(String.valueOf(bo)) : 0) + (br ? gp + ic + tp.measureText(bs) : 0);
        float xx = cx - tw / 2f, yy = cy + avR + u * 0.85f;
        tp.setTextAlign(Paint.Align.LEFT);
        tp.setColor(TXT);
        c.drawText(sc, xx, yy, tp);
        xx += wS;
        if (bo > 0) {
            xx += gp;
            bolt(c, xx + ic / 2f, yy - u * 0.12f, u * 0.4f);
            xx += ic;
            tp.setColor(0xFFFFD54F);
            c.drawText(String.valueOf(bo), xx, yy, tp);
            xx += tp.measureText(String.valueOf(bo));
        }
        if (br) {
            xx += gp;
            barrel(c, xx + ic / 2f, yy - u * 0.12f, u * 0.42f);
            xx += ic;
            tp.setColor(0xFFFFB74D);
            c.drawText(bs, xx, yy, tp);
        }
        // статус торгів / ставка господаря
        String st = null;
        int stFill = 0xAA000000, stTxt = TXT;
        if (g.phase == TEngine.BID) {
            if (g.passedOf(idx)) {
                st = "Пас";
                stTxt = 0xFFB0BEC5;
            } else if (idx == g.bidder) {
                st = "Ставка " + g.bid;
                stFill = 0xDDFFD54F;
                stTxt = 0xFF263238;
            }
        } else if (g.decl == idx && g.phase != TEngine.END) {
            st = "Ставка " + g.contract;
            stFill = 0xDDFFD54F;
            stTxt = 0xFF263238;
        } else if (g.decl == idx) {
            st = "Ставка " + g.contract;
            stFill = 0xDDFFD54F;
            stTxt = 0xFF263238;
        }
        if (st != null) {
            float py = cy - avR - u * 0.35f;
            if (cy < u * 2.5f) py = cy + avR + u * 1.4f;
            pill(c, cx, py, st, stFill, stTxt, u * 0.3f);
        }
        // кількість карт
        if (!mine && g.countOf(idx) > 0) {
            String cnum = String.valueOf(g.countOf(idx));
            float iw = u * 0.66f, ih = u * 0.96f;
            boolean leftSide = cx > getWidth() * 0.75f;
            float bx = leftSide ? cx - avR - u * 0.3f - iw : cx + avR + u * 0.3f;
            float by = cy - ih / 2f;
            painter.back(c, bx, by, iw, ih);
            p.setStyle(Paint.Style.FILL);
            p.setColor(0x88000000);
            rf.set(bx, by, bx + iw, by + ih);
            c.drawRoundRect(rf, u * 0.1f, u * 0.1f, p);
            tp.setTextAlign(Paint.Align.CENTER);
            tp.setTextSize(cnum.length() > 2 ? u * 0.3f : u * 0.4f);
            tp.setStyle(Paint.Style.STROKE);
            tp.setStrokeWidth(u * 0.07f);
            tp.setColor(0xFF000000);
            c.drawText(cnum, bx + iw / 2f, cy + u * 0.14f, tp);
            tp.setStyle(Paint.Style.FILL);
            tp.setColor(TXT);
            c.drawText(cnum, bx + iw / 2f, cy + u * 0.14f, tp);
        }
        // роздаючий
        if (g.dealer == idx) {
            float rx = cx - avR * 0.9f, ry = cy - avR * 0.8f;
            p.setStyle(Paint.Style.FILL);
            p.setColor(0xFF1565C0);
            c.drawCircle(rx, ry, u * 0.32f, p);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(u * 0.04f);
            p.setColor(TXT);
            c.drawCircle(rx, ry, u * 0.32f, p);
            tp.setStyle(Paint.Style.FILL);
            tp.setTextAlign(Paint.Align.CENTER);
            tp.setTextSize(u * 0.36f);
            tp.setColor(TXT);
            c.drawText("Д", rx, ry + u * 0.13f, tp);
        }
        if (mine && active) {
            tp.setTextAlign(Paint.Align.LEFT);
            tp.setTextSize(u * 0.3f);
            tp.setColor(pc);
            c.drawText("Ваш хід!", cx + avR + u * 0.3f, cy + u * 0.1f, tp);
        }
        if (micOk && !bots[idx]) {
            float mx = cx + avR * 0.9f, my = cy + avR * 0.8f;
            boolean rec = micIdx == idx;
            p.setStyle(Paint.Style.FILL);
            p.setColor(rec ? 0xFFD50000 : 0xCC37474F);
            c.drawCircle(mx, my, u * (rec ? 0.44f : 0.34f), p);
            p.setColor(TXT);
            rf.set(mx - u * 0.078f, my - u * 0.18f, mx + u * 0.078f, my + u * 0.04f);
            c.drawRoundRect(rf, u * 0.078f, u * 0.078f, p);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(u * 0.04f);
            rf.set(mx - u * 0.13f, my - u * 0.13f, mx + u * 0.13f, my + u * 0.1f);
            c.drawArc(rf, 0f, 180f, false, p);
            c.drawLine(mx, my + u * 0.1f, mx, my + u * 0.18f, p);
        }
        if (idx < listening.length && listening[idx]) {
            float mx = cx - avR * 0.9f, my = cy + avR * 0.8f;
            p.setStyle(Paint.Style.FILL);
            p.setColor(0xCC37474F);
            c.drawCircle(mx, my, u * 0.34f, p);
            p.setColor(TXT);
            c.drawRect(mx - u * 0.17f, my - u * 0.07f, mx - u * 0.07f, my + u * 0.07f, p);
            path.reset();
            path.moveTo(mx - u * 0.08f, my - u * 0.07f);
            path.lineTo(mx + u * 0.06f, my - u * 0.18f);
            path.lineTo(mx + u * 0.06f, my + u * 0.18f);
            path.lineTo(mx - u * 0.08f, my + u * 0.07f);
            path.close();
            c.drawPath(path, p);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(u * 0.04f);
            rf.set(mx - u * 0.02f, my - u * 0.12f, mx + u * 0.16f, my + u * 0.12f);
            c.drawArc(rf, -50f, 100f, false, p);
        }
        if (!bots[idx] && (mine || g.dndOf(idx))) {
            float dx0 = cx + avR * 0.9f, dy0 = cy - avR * 0.8f;
            boolean on = g.dndOf(idx);
            p.setStyle(Paint.Style.FILL);
            p.setColor(on ? 0xFFD50000 : 0xCC37474F);
            c.drawCircle(dx0, dy0, u * 0.34f, p);
            p.setColor(TXT);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(u * 0.05f);
            c.drawCircle(dx0, dy0, u * 0.19f, p);
            c.drawLine(dx0 - u * 0.135f, dy0 - u * 0.135f, dx0 + u * 0.135f, dy0 + u * 0.135f, p);
            p.setStyle(Paint.Style.FILL);
        }
        return anim;
    }

    private void drawEmoji(Canvas c, int idx, float cx, float cy) {
        float ex, ey;
        if (cy < u * 2.5f) {
            ex = cx - avR - u * 0.85f;
            ey = cy;
        } else {
            ex = cx;
            ey = cy - avR - u * 0.75f;
        }
        p.setStyle(Paint.Style.FILL);
        p.setColor(0xDDFFFFFF);
        c.drawCircle(ex, ey, u * 0.62f, p);
        tp.setStyle(Paint.Style.FILL);
        tp.setTextAlign(Paint.Align.CENTER);
        tp.setTextSize(u * 0.85f);
        tp.setColor(0xFF000000);
        c.drawText(emoVal[idx], ex, ey + u * 0.3f, tp);
    }

    // ---------- дотики ----------
    private boolean hitDnd(float x, float y) {
        if (me < 0 || me >= sx.length || me >= n) return false;
        float mx = sx[me] + avR * 0.9f, my = sy[me] - avR * 0.8f;
        float dx = x - mx, dy = y - my;
        return dx * dx + dy * dy <= u * 0.52f * u * 0.52f;
    }

    private int hitMic(float x, float y) {
        if (!micOk) return -1;
        for (int i = 0; i < n && i < sx.length; i++) {
            if (bots[i]) continue;
            float mx = sx[i] + avR * 0.9f, my = sy[i] + avR * 0.8f;
            float dx = x - mx, dy = y - my;
            if (dx * dx + dy * dy <= u * 0.52f * u * 0.52f) return i;
        }
        return -1;
    }

    private int hitAvatar(float x, float y) {
        for (int i = 0; i < n && i < sx.length; i++) {
            float dx = x - sx[i], dy = y - sy[i];
            float r = avR * 1.15f;
            if (dx * dx + dy * dy <= r * r) return i;
        }
        return -1;
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        float x = e.getX(), y = e.getY();
        int a = e.getAction();
        if (a == MotionEvent.ACTION_DOWN) {
            downX = x;
            downY = y;
            startScroll = scroll;
            moved = false;
            longFired = false;
            pressIdx = -1;
            micIdx = -1;
            dndPress = false;
            if (g != null) {
                calc(getWidth(), getHeight());
                if (hitDnd(x, y)) {
                    dndPress = true;
                    return true;
                }
                int mi = hitMic(x, y);
                if (mi >= 0) {
                    micIdx = mi;
                    if (listener != null) listener.onMic(mi, true);
                    invalidate();
                    return true;
                }
                int ai = hitAvatar(x, y);
                if (ai >= 0) {
                    pressIdx = ai;
                    lh.postDelayed(longRun, ViewConfiguration.getLongPressTimeout());
                }
            }
            return true;
        }
        if (a == MotionEvent.ACTION_MOVE) {
            if (micIdx >= 0 || dndPress) return true;
            if (Math.abs(x - downX) > slop || Math.abs(y - downY) > slop) {
                if (!moved) {
                    moved = true;
                    lh.removeCallbacks(longRun);
                }
            }
            if (pressIdx < 0 && moved && maxScroll > 0) {
                float s = startScroll + (downX - x);
                if (s < 0) s = 0;
                if (s > maxScroll) s = maxScroll;
                scroll = s;
                invalidate();
            }
            return true;
        }
        if (a == MotionEvent.ACTION_UP || a == MotionEvent.ACTION_CANCEL) {
            lh.removeCallbacks(longRun);
            if (dndPress) {
                dndPress = false;
                if (a == MotionEvent.ACTION_UP && listener != null) listener.onDnd();
                return true;
            }
            if (micIdx >= 0) {
                int mi = micIdx;
                micIdx = -1;
                if (listener != null) listener.onMic(mi, false);
                invalidate();
                return true;
            }
            if (a == MotionEvent.ACTION_UP) {
                if (pressIdx >= 0) {
                    if (!longFired && !moved) avatarTap(pressIdx);
                } else if (!moved) {
                    tap(x, y);
                }
            }
            pressIdx = -1;
            return true;
        }
        return true;
    }

    private void avatarTap(int idx) {
        if (listener == null) return;
        if (selGive != null && g != null && g.phase == TEngine.GIVE && g.decl == me && idx != me && g.needOf(idx) > 0 && g.paused < 0) {
            Card k = selGive;
            selGive = null;
            invalidate();
            listener.onGive(k, idx);
            return;
        }
        listener.onAvatarTap(idx);
    }

    private void tap(float x, float y) {
        if (g == null || listener == null || g.over || g.paused >= 0) return;
        calc(getWidth(), getHeight());
        for (int k = 0; k < marCnt; k++) {
            float[] r = marRect[k];
            if (x >= r[0] && x <= r[2] && y >= r[1] && y <= r[3]) {
                listener.onPlay(marCard[k], true);
                return;
            }
        }
        for (int i = g.hand.size() - 1; i >= 0; i--) {
            float cx = hx[i];
            float top = (g.legal.get(i) && hint) ? hTop - hRaise : hTop;
            float right = cx + (i == g.hand.size() - 1 ? hCw : Math.max(hStep, hx[i + 1] - cx));
            if (x >= cx && x <= right && y >= top && y <= hTop + hCh) {
                Card k = g.hand.get(i);
                if (g.phase == TEngine.GIVE && g.decl == me) {
                    selGive = selGive != null && selGive.same(k) ? null : k;
                    invalidate();
                } else if (g.legal.get(i) && g.cur == me) {
                    listener.onPlay(k, false);
                } else {
                    listener.onWrong();
                    crossEnc = k.enc();
                    crossUntil = System.currentTimeMillis() + 1200;
                    invalidate();
                    postInvalidateDelayed(1300);
                }
                return;
            }
        }
    }
}
