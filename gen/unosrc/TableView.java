package com.example.gryntsivgames.uno;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.os.Handler;
import android.os.Looper;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;

import java.util.HashMap;

public class TableView extends View {
    public interface Listener {
        void onPlay(Card k);

        void onDraw();

        void onAvatarTap(int idx);

        void onAvatarLong(int idx);

        void onMic(int idx, boolean down);

        void onWrong();

        void onDnd();
    }

    public Listener listener;
    public boolean micOk;
    public boolean hint = true;
    public boolean picking;
    private int bgId;
    private static final int TXT = 0xFFFFFFFF;
    private final HashMap<String, Long> fresh = new HashMap<String, Long>();
    private final HashMap<Integer, Long> shake = new HashMap<Integer, Long>();
    private String crossEnc;
    private long crossUntil;
    private Theme th;
    private final CardPainter painter;
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint tp = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rf = new RectF();
    private final Path path = new Path();
    private final Handler lh = new Handler(Looper.getMainLooper());

    private int n, me;
    private String[] names = new String[0];
    private int[] avatars = new int[0];
    private String[] photos = new String[0];
    private boolean[] bots = new boolean[0];
    private long[] emoUntil = new long[0];
    private String[] emoVal = new String[0];
    private GState g;
    private boolean[] listening = new boolean[0];

    private float scroll, downX, downY, startScroll;
    private boolean moved;
    private boolean longFired;
    private int pressIdx = -1;
    private int micIdx = -1;
    private final int slop;
    private String banner;
    private long bannerAt;

    private float u, cyP, pw, ph, deckX, discX, avR;
    private float[] sx = new float[0];
    private float[] sy = new float[0];
    private float hStart, hStep, hCw, hCh, hTop, hRaise, areaL, areaR, maxScroll;

    private final Runnable longRun = new Runnable() {
        public void run() {
            if (pressIdx >= 0 && !moved) {
                longFired = true;
                if (listener != null) listener.onAvatarLong(pressIdx);
            }
        }
    };

    public TableView(Context ctx, Theme t) {
        super(ctx);
        th = t;
        painter = new CardPainter(t);
        slop = ViewConfiguration.get(ctx).getScaledTouchSlop();
        tp.setTypeface(Typeface.DEFAULT_BOLD);
    }

    public void setTheme(Theme t) {
        th = t;
        painter.th = t;
        invalidate();
    }

    public void shakeAvatar(int idx) {
        shake.put(idx, System.currentTimeMillis());
        invalidate();
    }

    public void setBg(int id) {
        bgId = id;
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

    public void setListening(int idx, boolean on) {
        if (idx < 0 || idx >= 8) return;
        if (listening.length < 8) listening = new boolean[8];
        listening[idx] = on;
        invalidate();
    }

    public void setState(GState s) {
        GState old = g;
        if (old != null && !old.over && s != null && !s.over && s.hand.size() > old.hand.size()) {
            HashMap<String, Integer> cnt = new HashMap<String, Integer>();
            for (Card k : old.hand) {
                Integer v = cnt.get(k.enc());
                cnt.put(k.enc(), v == null ? 1 : v + 1);
            }
            long until = System.currentTimeMillis() + 2600;
            for (Card k : s.hand) {
                Integer v = cnt.get(k.enc());
                if (v != null && v > 0) {
                    cnt.put(k.enc(), v - 1);
                } else {
                    fresh.put(k.enc(), until);
                }
            }
        }
        g = s;
        invalidate();
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

    private void calc(int W, int H) {
        u = Math.min(W / 16f, H / 9f);
        avR = u * 0.65f;
        hCw = u * 1.53f;
        hCh = hCw * 1.5f;
        hRaise = u * 0.24f;
        hTop = H - u * 0.12f - hCh;
        areaL = u * 2.6f;
        areaR = W - u * 2.9f;
        int k = g == null ? 0 : g.hand.size();
        float avail = areaR - areaL;
        float step = k <= 1 ? 0 : Math.min(hCw * 1.06f, (avail - hCw) / (k - 1));
        step = Math.max(step, hCw * 0.42f);
        float total = k == 0 ? 0 : hCw + step * (k - 1);
        hStep = step;
        if (total <= avail) {
            maxScroll = 0;
            scroll = 0;
            hStart = areaL + (avail - total) / 2f;
        } else {
            maxScroll = total - avail;
            if (scroll < 0) scroll = 0;
            if (scroll > maxScroll) scroll = maxScroll;
            hStart = areaL - scroll;
        }
        pw = hCw;
        ph = hCh;
        cyP = u * 2.95f + ph / 2f;
        deckX = W / 2f - pw * 0.9f;
        discX = W / 2f + pw * 0.9f;
        if (n > 0 && sx.length == n && me < n) {
            sx[me] = u * 1.35f;
            sy[me] = H - u * 1.85f;
            int m = n - 1;
            float top = u * 1.55f, side = u * 4.0f;
            for (int q = 1; q <= m; q++) {
                int idx = (me + q) % n;
                float x, y;
                if (m == 1) {
                    x = W / 2f;
                    y = top;
                } else if (m == 2) {
                    x = q == 1 ? u * 1.4f : W - u * 1.4f;
                    y = side;
                } else if (m == 3) {
                    if (q == 1) {
                        x = u * 1.4f;
                        y = side;
                    } else if (q == 2) {
                        x = W / 2f;
                        y = top;
                    } else {
                        x = W - u * 1.4f;
                        y = side;
                    }
                } else if (m == 5) {
                    if (q == 1) {
                        x = u * 1.4f;
                        y = u * 5.0f;
                    } else if (q == 2) {
                        x = u * 2.4f;
                        y = u * 2.75f;
                    } else if (q == 3) {
                        x = W / 2f;
                        y = top;
                    } else if (q == 4) {
                        x = W - u * 2.4f;
                        y = u * 2.75f;
                    } else {
                        x = W - u * 1.4f;
                        y = u * 5.0f;
                    }
                } else {
                    if (q == 1) {
                        x = u * 1.4f;
                        y = side;
                    } else if (q == 2) {
                        x = W * 0.36f;
                        y = top;
                    } else if (q == 3) {
                        x = W * 0.64f;
                        y = top;
                    } else {
                        x = W - u * 1.4f;
                        y = side;
                    }
                }
                sx[idx] = x;
                sy[idx] = y;
            }
        }
    }

    private float aCnt, aPts;
    private long badgeT;

    private float approach(float a, float target, float step) {
        if (a < target) return Math.min(target, a + step);
        return Math.max(target, a - step);
    }

    // темна кругла плашка з числом на кутку карти
    private void badge(Canvas c, float bx, float by, String s, float a) {
        p.setStyle(Paint.Style.FILL);
        p.setColor((((int) (0xCC * a)) << 24) | 0x00101418);
        c.drawCircle(bx, by, u * 0.3f, p);
        tp.setStyle(Paint.Style.FILL);
        tp.setTextAlign(Paint.Align.CENTER);
        tp.setTextSize(s.length() > 2 ? u * 0.26f : u * 0.34f);
        tp.setColor((((int) (255 * a)) << 24) | 0x00FFFFFF);
        c.drawText(s, bx, by + u * 0.12f, tp);
        tp.setAlpha(255);
    }

    private String cut(String s, int max) {
        if (s.length() > max) return s.substring(0, max - 1) + "…";
        return s;
    }

    @Override
    protected void onDraw(Canvas c) {
        int W = getWidth(), H = getHeight();
        calc(W, H);
        Bg.draw(c, W, H, bgId);
        if (g == null || n == 0 || sx.length != n || g.counts == null || g.counts.length < n
                || g.totals == null || g.totals.length < n) return;
        long now = System.currentTimeMillis();
        boolean anim = false;
        boolean pausedNow = g.paused >= 0;
        boolean myTurn = g.cur == me && !g.over && !pausedNow;
        double pulse = 0.5 + 0.5 * Math.sin(now * 2 * Math.PI / 2000.0);

        // колода
        for (int i = 2; i >= 0; i--) {
            painter.back(c, deckX - pw / 2f + i * u * 0.05f, cyP - ph / 2f - i * u * 0.05f, pw, ph);
        }
        if (myTurn && g.canDraw) {
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(u * 0.11f);
            p.setColor((((int) (50 + 200 * pulse)) << 24) | 0x00FFD54F);
            rf.set(deckX - pw / 2f - u * 0.1f, cyP - ph / 2f - u * 0.18f, deckX + pw / 2f + u * 0.2f, cyP + ph / 2f + u * 0.1f);
            c.drawRoundRect(rf, u * 0.15f, u * 0.15f, p);
            anim = true;
        }
        p.setStyle(Paint.Style.FILL);
        p.setColor(0xBB000000);
        tp.setTextSize(u * 0.3f);
        String dk = String.valueOf(g.deck);
        float dkw = tp.measureText(dk) + u * 0.3f;
        rf.set(deckX + pw / 2f - u * 0.1f - dkw, cyP + ph / 2f - u * 0.1f - u * 0.46f, deckX + pw / 2f - u * 0.1f, cyP + ph / 2f - u * 0.1f);
        c.drawRoundRect(rf, u * 0.2f, u * 0.2f, p);
        tp.setStyle(Paint.Style.FILL);
        tp.setAlpha(255);
        tp.setTextAlign(Paint.Align.CENTER);
        tp.setColor(TXT);
        c.drawText(dk, deckX + pw / 2f - u * 0.1f - dkw / 2f, cyP + ph / 2f - u * 0.1f - u * 0.13f, tp);
        painter.draw(c, g.top, discX - pw / 2f, cyP - ph / 2f, pw, ph, false);
        if (g.lastP >= 0 && g.lastP < names.length) {
            tp.setStyle(Paint.Style.FILL);
            tp.setTextAlign(Paint.Align.CENTER);
            tp.setTextSize(u * 0.28f);
            tp.setColor(Theme.pcol(g.lastP));
            String ln = names[g.lastP].startsWith("Бот ") ? names[g.lastP].substring(4) : names[g.lastP];
            c.drawText(ln.length() > 9 ? ln.substring(0, 8) + "…" : ln, discX, cyP - ph / 2f - u * 0.22f, tp);
        }
        if (g.pend > 0) {
            p.setStyle(Paint.Style.FILL);
            p.setColor(0xFFD50000);
            c.drawCircle(discX + pw / 2f, cyP - ph / 2f, u * 0.38f, p);
            tp.setColor(0xFFFFFFFF);
            tp.setTextSize(u * 0.38f);
            c.drawText("+" + g.pend, discX + pw / 2f, cyP - ph / 2f + u * 0.13f, tp);
        }
        float colX = W / 2f;
        p.setStyle(Paint.Style.FILL);
        p.setColor(th.col[g.curColor < 0 || g.curColor > 3 ? 0 : g.curColor]);
        c.drawCircle(colX, cyP, u * 0.36f, p);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(u * 0.06f);
        p.setColor(TXT);
        c.drawCircle(colX, cyP, u * 0.36f, p);
        if (n > 2) drawDir(c, deckX - pw / 2f - u * 0.8f, cyP, u * 0.36f, g.dir >= 0);

        // повідомлення про хід: окрема плашка між колодою та картами гравця
        if (g.msg != null && g.msg.length() > 0) {
            float msgSize = u * 0.28f;
            tp.setTextSize(msgSize);
            float tw = tp.measureText(g.msg);
            float maxW = W * 0.8f - u * 0.6f;
            if (tw > maxW) {
                msgSize = msgSize * maxW / tw;
                tp.setTextSize(msgSize);
                tw = maxW;
            }
            float pcy = cyP + ph / 2f + u * 0.12f + u * 0.23f;
            p.setStyle(Paint.Style.FILL);
            p.setColor(0x77000000);
            rf.set(W / 2f - tw / 2f - u * 0.3f, pcy - u * 0.23f, W / 2f + tw / 2f + u * 0.3f, pcy + u * 0.23f);
            c.drawRoundRect(rf, u * 0.23f, u * 0.23f, p);
            tp.setStyle(Paint.Style.FILL);
            tp.setColor(TXT);
            tp.setTextAlign(Paint.Align.CENTER);
            c.drawText(g.msg, W / 2f, pcy + msgSize * 0.35f, tp);
        }

        // рука
        c.save();
        c.clipRect(areaL - u * 0.5f, 0, areaR + u * 0.5f, H);
        for (int i = 0; i < g.hand.size(); i++) {
            boolean lg = g.legal.get(i);
            boolean lit = myTurn && (lg || !hint);
            boolean up = myTurn && lg && hint;
            float x = hStart + i * hStep;
            float y = hTop - (up ? hRaise : 0);
            Card hc = g.hand.get(i);
            painter.draw(c, hc, x, y, hCw, hCh, (lit || picking) ? 0 : 1);
            Long fu = fresh.get(hc.enc());
            if (fu != null) {
                if (fu > now) {
                    float a = Math.min(1f, (fu - now) / 600f);
                    p.setStyle(Paint.Style.STROKE);
                    p.setStrokeWidth(u * 0.09f);
                    p.setColor((((int) (255 * a)) << 24) | 0x00FFD54F);
                    rf.set(x, y, x + hCw, y + hCh);
                    c.drawRoundRect(rf, hCw * 0.1f, hCw * 0.1f, p);
                    anim = true;
                } else {
                    fresh.remove(hc.enc());
                }
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
            long dtm = badgeT == 0 ? 0 : Math.min(100, now - badgeT);
            badgeT = now;
            float tC = g.hand.size() > 4 ? 1f : 0f, tP = g.hand.size() > 1 ? 1f : 0f;
            aCnt = approach(aCnt, tC, dtm / 350f);
            aPts = approach(aPts, tP, dtm / 350f);
            if (aCnt != tC || aPts != tP) anim = true;
            int last = g.hand.size() - 1;
            if (aCnt > 0.01f) {
                float yy = hTop - (myTurn && g.legal.get(last) && hint ? hRaise : 0);
                badge(c, hStart + last * hStep + hCw + u * 0.2f, yy - u * 0.2f, String.valueOf(g.hand.size()), aCnt);
            }
            if (aPts > 0.01f) {
                int sum = 0;
                for (Card hk : g.hand) sum += hk.points();
                float yy = hTop - (myTurn && g.legal.get(0) && hint ? hRaise : 0);
                badge(c, hStart - u * 0.2f, yy - u * 0.2f, String.valueOf(sum), aPts);
            }
        } else {
            badgeT = 0;
        }
        c.restore();

        if (pausedNow) {
            c.drawColor(0x66000000);
            tp.setStyle(Paint.Style.FILL);
            tp.setTextAlign(Paint.Align.CENTER);
            tp.setColor(0xFFFFFFFF);
            tp.setTextSize(u * 1.1f);
            c.drawText("ПАУЗА", W / 2f, cyP - u * 0.1f, tp);
            tp.setTextSize(u * 0.42f);
            String who = (g.paused < names.length) ? names[g.paused] : "";
            c.drawText("Поставив: " + who, W / 2f, cyP + u * 0.6f, tp);
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
                tp.setTextSize(u * 2.2f * sc);
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

    private void drawDir(Canvas c, float cx, float cy, float r, boolean cw) {
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(u * 0.09f);
        p.setStrokeCap(Paint.Cap.ROUND);
        p.setColor(TXT);
        rf.set(cx - r, cy - r, cx + r, cy + r);
        float start = cw ? -120f : -60f;
        float sweep = cw ? 250f : -250f;
        c.drawArc(rf, start, sweep, false, p);
        float a = (float) Math.toRadians(start + sweep);
        float px = cx + r * (float) Math.cos(a);
        float py = cy + r * (float) Math.sin(a);
        float tx, ty;
        if (cw) {
            tx = -(float) Math.sin(a);
            ty = (float) Math.cos(a);
        } else {
            tx = (float) Math.sin(a);
            ty = -(float) Math.cos(a);
        }
        float nx = (float) Math.cos(a), ny = (float) Math.sin(a);
        path.reset();
        path.moveTo(px + tx * u * 0.26f, py + ty * u * 0.26f);
        path.lineTo(px + nx * u * 0.18f, py + ny * u * 0.18f);
        path.lineTo(px - nx * u * 0.18f, py - ny * u * 0.18f);
        path.close();
        p.setStyle(Paint.Style.FILL);
        c.drawPath(path, p);
        p.setStrokeCap(Paint.Cap.BUTT);
    }

    // повертає true, якщо потрібна подальша анімація
    private boolean drawPlayer(Canvas c, int idx, float cx, float cy, boolean mine, long now, double pulse) {
        if (idx >= names.length) return false;
        boolean anim = false;
        boolean active = !g.over && g.paused < 0 && g.cur == idx;
        if (active) {
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(u * 0.07f);
            int pc = Theme.pcol(idx);
            p.setColor(pc);
            c.drawCircle(cx, cy, avR * 1.12f, p);
            p.setStrokeWidth(u * 0.16f);
            p.setColor((((int) (40 + 150 * pulse)) << 24) | (pc & 0x00FFFFFF));
            c.drawCircle(cx, cy, avR * 1.12f + u * 0.06f + u * 0.1f * (float) pulse, p);
            anim = true;
        }
        Long sk = shake.get(idx);
        float sx = 0;
        if (sk != null) {
            long dt = now - sk;
            if (dt < 700) {
                sx = (float) Math.sin(dt / 35.0) * u * 0.16f * (1f - dt / 700f);
                anim = true;
            } else {
                shake.remove(idx);
            }
        }
        boolean sleepy = g.sleeping(idx);
        if (sleepy) {
            float ex = avR * 2.2f;
            c.saveLayer(cx - ex, cy - ex, cx + ex, cy + ex, null);
        }
        Avatars.draw(c, cx + sx, cy, avR, avatars[idx], Avatars.photo(photos[idx]));
        if (sleepy) {
            float ex = avR * 2.2f;
            p.setStyle(Paint.Style.FILL);
            p.setColor(0xAA000000);
            p.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.SRC_ATOP));
            c.drawRect(cx - ex, cy - ex, cx + ex, cy + ex, p);
            p.setXfermode(null);
            c.restore();
            tp.setTextAlign(Paint.Align.CENTER);
            tp.setTextSize(u * 0.42f);
            tp.setStyle(Paint.Style.STROKE);
            tp.setStrokeWidth(u * 0.07f);
            tp.setColor(0xFF000000);
            c.drawText("Zzz", cx + avR * 0.55f, cy - avR * 0.35f, tp);
            tp.setStyle(Paint.Style.FILL);
            tp.setColor(0xFFB3E5FC);
            c.drawText("Zzz", cx + avR * 0.55f, cy - avR * 0.35f, tp);
        }
        {
            boolean leftDeck = cx > getWidth() * 0.75f;
            float py = leftDeck ? cy - avR * 1.0f : cy - avR * 0.62f;
            String pts = String.valueOf(g.totals[idx]);
            tp.setTextAlign(Paint.Align.RIGHT);
            tp.setTextSize(u * 0.34f);
            tp.setStyle(Paint.Style.STROKE);
            tp.setStrokeWidth(u * 0.07f);
            tp.setColor(0xFF000000);
            c.drawText(pts, cx - avR * 0.78f, py, tp);
            tp.setStyle(Paint.Style.FILL);
            tp.setColor(0xFFFFE082);
            c.drawText(pts, cx - avR * 0.78f, py, tp);
        }
        tp.setStyle(Paint.Style.FILL);
        tp.setAlpha(255);
        tp.setTextAlign(Paint.Align.CENTER);
        tp.setColor(TXT);
        tp.setTextSize(u * 0.3f);
        String nm = cut(names[idx], 12);
        tp.setColor(sleepy ? 0xFF90A4AE : Theme.pcol(idx));
        c.drawText(nm, cx, cy + avR + u * 0.4f, tp);
        tp.setColor(TXT);
        if (mine) {
            if (active) {
                tp.setColor(Theme.pcol(idx));
                c.drawText("Ваш хід!", cx, cy + avR + u * 0.8f, tp);
            }
        } else if (!sleepy) {
            String cnum = String.valueOf(g.counts[idx]);
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
        if (micOk && !bots[idx]) {
            float mx = cx + avR * 0.9f, my = cy + avR * 0.8f;
            boolean rec = micIdx == idx;
            p.setStyle(Paint.Style.FILL);
            p.setColor(rec ? 0xFFD50000 : 0xCC37474F);
            c.drawCircle(mx, my, u * (rec ? 0.44f : 0.34f), p);
            p.setColor(0xFFFFFFFF);
            rf.set(mx - u * 0.078f, my - u * 0.18f, mx + u * 0.078f, my + u * 0.04f);
            c.drawRoundRect(rf, u * 0.078f, u * 0.078f, p);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(u * 0.04f);
            rf.set(mx - u * 0.13f, my - u * 0.13f, mx + u * 0.13f, my + u * 0.1f);
            c.drawArc(rf, 0f, 180f, false, p);
            c.drawLine(mx, my + u * 0.1f, mx, my + u * 0.18f, p);
        }
        if (!bots[idx] && (mine || g.dndOf(idx))) {
            float dx0 = cx + avR * 0.9f, dy0 = cy - avR * 0.8f;
            boolean on = g.dndOf(idx);
            p.setStyle(Paint.Style.FILL);
            p.setColor(on ? 0xFFD50000 : 0xCC37474F);
            c.drawCircle(dx0, dy0, u * 0.34f, p);
            p.setColor(0xFFFFFFFF);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(u * 0.05f);
            c.drawCircle(dx0, dy0, u * 0.19f, p);
            c.drawLine(dx0 - u * 0.135f, dy0 - u * 0.135f, dx0 + u * 0.135f, dy0 + u * 0.135f, p);
            p.setStyle(Paint.Style.FILL);
        }
        if (idx < listening.length && listening[idx]) {
            float mx = cx - avR * 0.9f, my = cy + avR * 0.8f;
            p.setStyle(Paint.Style.FILL);
            p.setColor(0xCC37474F);
            c.drawCircle(mx, my, u * 0.34f, p);
            p.setColor(0xFFFFFFFF);
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
        return anim;
    }

    // смайлик малюється поверх усього; для верхніх гравців — збоку від аватара
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

    private RectF rectF(float l, float t, float r, float b) {
        rf.set(l, t, r, b);
        return rf;
    }

    private boolean hitDnd(float x, float y) {
        if (me < 0 || me >= sx.length || me >= n) return false;
        float mx = sx[me] + avR * 0.9f, my = sy[me] - avR * 0.8f;
        float dx = x - mx, dy = y - my;
        return dx * dx + dy * dy <= u * 0.52f * u * 0.52f;
    }

    private boolean dndPress;

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
                    if (!longFired && !moved && listener != null) listener.onAvatarTap(pressIdx);
                } else if (!moved) {
                    tap(x, y);
                }
            }
            pressIdx = -1;
            return true;
        }
        return true;
    }

    private void tap(float x, float y) {
        if (g == null || listener == null) return;
        calc(getWidth(), getHeight());
        boolean myTurn = g.cur == me && !g.over && g.paused < 0;
        if (!myTurn) return;
        if (g.canDraw && x >= deckX - pw / 2f && x <= deckX + pw / 2f && y >= cyP - ph / 2f && y <= cyP + ph / 2f) {
            listener.onDraw();
            return;
        }
        for (int i = g.hand.size() - 1; i >= 0; i--) {
            float cx = hStart + i * hStep;
            float top = (g.legal.get(i) && hint) ? hTop - hRaise : hTop;
            if (x >= cx && x <= cx + hCw && y >= top && y <= hTop + hCh) {
                if (g.legal.get(i)) {
                    listener.onPlay(g.hand.get(i));
                } else {
                    if (listener != null) listener.onWrong();
                    crossEnc = g.hand.get(i).enc();
                    crossUntil = System.currentTimeMillis() + 1200;
                    invalidate();
                    postInvalidateDelayed(1300);
                }
                return;
            }
        }
    }
}
