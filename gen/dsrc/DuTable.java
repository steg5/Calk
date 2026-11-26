package com.example.gryntsivgames.durak;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.os.Handler;
import android.os.Looper;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;

import com.example.gryntsivgames.uno.Avatars;
import com.example.gryntsivgames.uno.Bg;
import com.example.gryntsivgames.uno.Card;
import com.example.gryntsivgames.uno.Theme;

import java.util.ArrayList;
import java.util.HashMap;

public class DuTable extends View {
    public interface Listener {
        void onPlay(Card k, int target);

        void onTake();

        void onAvatarTap(int idx);

        void onAvatarLong(int idx);

        void onWrong();

        void onDnd();

        void onMic(int idx, boolean down);
    }

    public Listener listener;
    public boolean hint = true;
    public boolean tapTake;
    public boolean micOk;
    private boolean[] listening = new boolean[8];
    private int micIdx = -1;
    private final android.graphics.Path path = new android.graphics.Path();
    private final ArrayList<Card> gAtk = new ArrayList<Card>();
    private final ArrayList<Card> gDfn = new ArrayList<Card>();
    private long ghostUntil, ghostStart;
    private static final int GHOST_HOLD = 450, GHOST_FADE = 550;
    private int[] gOwn = new int[0];
    private int bgId;
    private static final int TXT = 0xFFFFFFFF;
    private final HashMap<String, Long> fresh = new HashMap<String, Long>();
    private final HashMap<Integer, Long> shake = new HashMap<Integer, Long>();
    private String crossEnc;
    private long crossUntil;
    private final DuCards painter;
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint tp = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rf = new RectF();
    private final Handler lh = new Handler(Looper.getMainLooper());

    private int n, me;
    private String[] names = new String[0];
    private int[] avatars = new int[0];
    private String[] photos = new String[0];
    private boolean[] bots = new boolean[0];
    private long[] emoUntil = new long[0];
    private String[] emoVal = new String[0];
    private DState g;
    private int selTarget = -1;

    private float scroll, downX, downY, startScroll;
    private boolean moved, longFired;
    private int pressIdx = -1;
    private final int slop;
    private String banner;
    private long bannerAt;

    private float u, avR, dpw, dph, deckX, cyD, playL, playR, tTop;
    private float[] sx = new float[0];
    private float[] sy = new float[0];
    private float hStart, hStep, hCw, hCh, hTop, hRaise, areaL, areaR, maxScroll;
    private float aCnt;
    private long badgeT;

    private final Runnable longRun = new Runnable() {
        public void run() {
            if (pressIdx >= 0 && !moved) {
                longFired = true;
                if (listener != null) listener.onAvatarLong(pressIdx);
            }
        }
    };

    public DuTable(Context ctx, int style) {
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

    public void setListening(int idx, boolean on) {
        if (idx < 0 || idx >= 8) return;
        listening[idx] = on;
        invalidate();
    }

    public void setBg(int id) {
        bgId = id;
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

    private DState pending;
    private long holdUntil;

    public void setState(DState s) {
        if (holdUntil > System.currentTimeMillis() && s != null) {
            pending = s;
            return;
        }
        applyState(s, false);
    }

    private void releaseHold() {
        holdUntil = 0;
        DState s = pending;
        pending = null;
        if (s != null) applyState(s, true);
        invalidate();
    }

    private void applyState(DState s, boolean noGhost) {
        DState old = g;
        if (!noGhost && old != null && s != null && s.gSeq != old.gSeq && !s.gAtk.isEmpty() && s.atk.isEmpty() && !s.over) {
            // спершу зникають карти зі столу, лише потім з'являються добрані
            gAtk.clear();
            gDfn.clear();
            gAtk.addAll(s.gAtk);
            gDfn.addAll(s.gDfn);
            gOwn = s.gOwn;
            ghostStart = System.currentTimeMillis();
            ghostUntil = ghostStart + GHOST_HOLD + GHOST_FADE;
            holdUntil = ghostUntil;
            DState h = old.copy();
            h.atk = new ArrayList<Card>();
            h.dfn = new ArrayList<Card>();
            h.msg = "";
            g = h;
            pending = s;
            lh.postDelayed(new Runnable() {
                public void run() {
                    releaseHold();
                }
            }, GHOST_HOLD + GHOST_FADE + 30);
            invalidate();
            return;
        }
        if (old != null && !old.over && s != null && !s.over && s.hand.size() > old.hand.size()) {
            HashMap<String, Integer> cnt = new HashMap<String, Integer>();
            for (Card k : old.hand) {
                Integer v = cnt.get(k.enc());
                cnt.put(k.enc(), v == null ? 1 : v + 1);
            }
            long until = System.currentTimeMillis() + 2600;
            for (Card k : s.hand) {
                Integer v = cnt.get(k.enc());
                if (v != null && v > 0) cnt.put(k.enc(), v - 1);
                else fresh.put(k.enc(), until);
            }
        }
        if (s == null || s.atk.isEmpty() || selTarget >= s.atk.size() || (selTarget >= 0 && s.dfn.get(selTarget) != null)) selTarget = -1;
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
        hCw = u * 1.7f;
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
        dpw = u * 1.45f;
        dph = dpw * 1.5f;
        float cx = W / 2f;
        playL = cx - u * 1.9f;
        playR = W - u * 3.5f;
        deckX = playL - u * 0.8f;
        cyD = u * 4.45f;
        tTop = u * 3.2f;
        if (n > 0 && sx.length == n && me < n) {
            sx[me] = u * 1.35f;
            sy[me] = H - u * 1.85f;
            int m = n - 1;
            float top = u * 1.9f, side = u * 3.0f;
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
                        x = u * 1.2f;
                        y = u * 5.0f;
                    } else if (q == 2) {
                        x = u * 2.4f;
                        y = u * 2.55f;
                    } else if (q == 3) {
                        x = W / 2f;
                        y = top;
                    } else if (q == 4) {
                        x = W - u * 2.4f;
                        y = u * 2.55f;
                    } else {
                        x = W - u * 1.2f;
                        y = u * 5.0f;
                    }
                } else {
                    if (q == 1) {
                        x = u * 1.4f;
                        y = side;
                    } else if (q == 2) {
                        x = W * 0.40f;
                        y = top;
                    } else if (q == 3) {
                        x = W * 0.60f;
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

    // розміри й позиції пар на столі
    private float pcw, pch;

    private float slotX(int k, int pairs) {
        int cols = pairs <= 3 ? pairs : 3;
        float cell = (playR - playL) / 3f;
        float x0 = playL + (3 - cols) * cell / 2f;
        return x0 + (k % 3) * cell + cell / 2f;
    }

    private float slotY(int k, int pairs) {
        if (pairs <= 3) return tTop + (u * 2.1f) / 2f + u * 0.05f;
        return tTop - u * 0.1f + (k / 3) * (pch * 1.14f + u * 0.06f) + pch / 2f;
    }

    // ширина вертикального підпису збоку від ходової карти
    private float lblW() {
        return u * 0.36f;
    }

    private void slotSize(int pairs) {
        float cell = (playR - playL) / 3f;
        float maxW = (cell - lblW() - u * 0.1f) / 1.38f;
        pcw = Math.min(pairs <= 3 ? u * 1.4f : u * 0.9f, maxW);
        pch = pcw * 1.5f;
    }

    private float pairAx(float cxk) {
        return cxk - pcw / 2f - pcw * 0.19f + lblW() / 2f;
    }

    private float approach(float a, float target, float step) {
        if (a < target) return Math.min(target, a + step);
        return Math.max(target, a - step);
    }

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

    private boolean iAmActing() {
        if (g == null || g.over || g.paused >= 0) return false;
        for (Boolean b : g.legal) if (b) return true;
        return false;
    }

    @Override
    protected void onDraw(Canvas c) {
        int W = getWidth(), H = getHeight();
        calc(W, H);
        Bg.draw(c, W, H, bgId);
        if (g == null || n == 0 || sx.length != n || g.counts == null || g.counts.length < n
                || g.losses == null || g.losses.length < n) return;
        long now = System.currentTimeMillis();
        boolean anim = false;
        boolean pausedNow = g.paused >= 0;
        boolean acting = iAmActing();
        double pulse = 0.5 + 0.5 * Math.sin(now * 2 * Math.PI / 2000.0);

        // козир: карта, повернута на 90°, наполовину під колодою
        float dl = deckX - dpw / 2f;
        if (g.deck > 0) {
            float right = dl + dph * 0.2f;
            float cxr = right - dph / 2f, cyr = cyD;
            c.save();
            c.rotate(-90f, cxr, cyr);
            painter.draw(c, g.trump, cxr - dpw / 2f, cyr - dph / 2f, dpw, dph, 0);
            c.restore();
            for (int i = Math.min(2, g.deck - 1); i >= 0; i--) {
                painter.back(c, dl + i * u * 0.05f, cyD - dph / 2f - i * u * 0.05f, dpw, dph);
            }
            p.setStyle(Paint.Style.FILL);
            p.setColor(0xBB000000);
            tp.setTextSize(u * 0.3f);
            String dk = String.valueOf(g.deck);
            float dkw = tp.measureText(dk) + u * 0.3f;
            rf.set(deckX + dpw / 2f - u * 0.1f - dkw, cyD + dph / 2f - u * 0.1f - u * 0.46f, deckX + dpw / 2f - u * 0.1f, cyD + dph / 2f - u * 0.1f);
            c.drawRoundRect(rf, u * 0.2f, u * 0.2f, p);
            tp.setStyle(Paint.Style.FILL);
            tp.setTextAlign(Paint.Align.CENTER);
            tp.setColor(TXT);
            c.drawText(dk, deckX + dpw / 2f - u * 0.1f - dkw / 2f, cyD + dph / 2f - u * 0.1f - u * 0.13f, tp);
        } else {
            p.setStyle(Paint.Style.FILL);
            p.setColor(0xEEF5F5F0);
            c.drawCircle(deckX, cyD, u * 0.75f, p);
            painter.suit(c, g.ts, deckX, cyD, u * 0.8f, painter.suitColor(g.ts) == 0xFF00E5FF || painter.suitColor(g.ts) == 0xFFE57373 ? (g.ts == 1 || g.ts == 2 ? 0xFFD50000 : 0xFF263238) : painter.suitColor(g.ts));
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(u * 0.05f);
            p.setColor(0xFF37474F);
            c.drawCircle(deckX, cyD, u * 0.75f, p);
        }

        // пари на столі (після завершення розданої атаки ще коротко показуємо останній стіл)
        boolean ghost = g.atk.isEmpty() && ghostUntil > now && !g.over;
        ArrayList<Card> ta = ghost ? gAtk : g.atk;
        ArrayList<Card> td = ghost ? gDfn : g.dfn;
        if (ghost) anim = true;
        int pairs = ta.size();
        boolean ghostLayer = false;
        if (ghost) {
            long gt = now - ghostStart;
            if (gt > GHOST_HOLD) {
                int ga = (int) (255 * Math.max(0f, 1f - (gt - GHOST_HOLD) / (float) GHOST_FADE));
                c.saveLayerAlpha(0, 0, W, H, ga, 31);
                ghostLayer = true;
            }
        }
        if (pairs > 0) {
            slotSize(pairs);
            boolean meDef = !ghost && g.def == me && !g.taking && !g.over;
            for (int k = 0; k < pairs; k++) {
                float cxk = slotX(k, pairs), cyk = slotY(k, pairs);
                float ax = pairAx(cxk), ay = cyk - pch / 2f;
                painter.draw(c, ta.get(k), ax, ay, pcw, pch, 0);
                {
                    int[] ow = ghost ? gOwn : g.own;
                    int who = k < ow.length ? ow[k] : g.att;
                    if (who >= 0 && who < names.length) {
                        tp.setStyle(Paint.Style.FILL);
                        tp.setTextAlign(Paint.Align.CENTER);
                        tp.setTextSize(u * 0.28f);
                        tp.setColor(Theme.pcol(who));
                        float lx = ax - u * 0.1f, ly = ay + pch / 2f;
                        String lt = cut(names[who].startsWith("Бот ") ? names[who].substring(4) : names[who], 9);
                        float lw0 = tp.measureText(lt);
                        if (lw0 > pch * 0.95f) tp.setTextSize(u * 0.28f * pch * 0.95f / lw0);
                        c.save();
                        c.rotate(-90f, lx, ly);
                        c.drawText(lt, lx, ly, tp);
                        c.restore();
                    }
                }
                Card d = td.get(k);
                if (d != null) {
                    painter.draw(c, d, ax + pcw * 0.38f, ay + pch * 0.14f, pcw, pch, 0);
                } else if (meDef) {
                    p.setStyle(Paint.Style.STROKE);
                    boolean sel = selTarget == k;
                    p.setStrokeWidth(u * (sel ? 0.12f : 0.07f));
                    p.setColor(sel ? 0xFFFFD54F : (((int) (60 + 150 * pulse)) << 24) | 0x00FFFFFF);
                    rf.set(ax, ay, ax + pcw, ay + pch);
                    c.drawRoundRect(rf, pcw * 0.1f, pcw * 0.1f, p);
                    anim = true;
                }
            }
        }
        if (ghostLayer) c.restore();

        if (g.transferOn && g.deck >= 0) {
            tp.setStyle(Paint.Style.FILL);
            tp.setTextAlign(Paint.Align.CENTER);
            tp.setTextSize(u * 0.24f);
            float tw2 = tp.measureText("Перевідний") + u * 0.4f;
            p.setStyle(Paint.Style.FILL);
            p.setColor(0x99000000);
            float by2 = cyD + dph / 2f + u * 0.3f;
            rf.set(deckX - tw2 / 2f, by2 - u * 0.24f, deckX + tw2 / 2f, by2 + u * 0.16f);
            c.drawRoundRect(rf, u * 0.2f, u * 0.2f, p);
            tp.setColor(0xFFFFD54F);
            c.drawText("Перевідний", deckX, by2 + u * 0.07f, tp);
        }

        // рука
        c.save();
        c.clipRect(areaL - u * 0.5f, 0, areaR + u * 0.5f, H);
        for (int i = 0; i < g.hand.size(); i++) {
            boolean lg = g.legal.get(i);
            boolean lit = !pausedNow && (lg || !hint);
            boolean up = !pausedNow && lg && hint;
            float x = hStart + i * hStep;
            float y = hTop - (up ? hRaise : 0);
            Card hc = g.hand.get(i);
            painter.draw(c, hc, x, y, hCw, hCh, lit ? 0 : 1);
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
            float tC = g.hand.size() > 4 ? 1f : 0f;
            aCnt = approach(aCnt, tC, dtm / 350f);
            if (aCnt != tC) anim = true;
            int last = g.hand.size() - 1;
            if (aCnt > 0.01f) {
                float yy = hTop - (!pausedNow && g.legal.get(last) && hint ? hRaise : 0);
                badge(c, hStart + last * hStep + hCw + u * 0.2f, yy - u * 0.2f, String.valueOf(g.hand.size()), aCnt);
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
            c.drawText("ПАУЗА", W / 2f, cyD - u * 0.1f, tp);
            tp.setTextSize(u * 0.42f);
            String who = (g.paused < names.length) ? names[g.paused] : "";
            c.drawText("Поставив: " + who, W / 2f, cyD + u * 0.6f, tp);
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

    private boolean drawPlayer(Canvas c, int idx, float cx, float cy, boolean mine, long now, double pulse) {
        if (idx >= names.length) return false;
        boolean anim = false;
        boolean fin = g.outOf(idx);
        boolean active = !g.over && g.paused < 0 && g.cur == idx && !fin;
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
        if (fin) {
            float ex = avR * 2.2f;
            c.saveLayer(cx - ex, cy - ex, cx + ex, cy + ex, null);
        }
        Avatars.draw(c, cx + sxs, cy, avR, avatars[idx], Avatars.photo(photos[idx]));
        if (fin) {
            float ex = avR * 2.2f;
            p.setStyle(Paint.Style.FILL);
            p.setColor(0xAA000000);
            p.setXfermode(new android.graphics.PorterDuffXfermode(android.graphics.PorterDuff.Mode.SRC_ATOP));
            c.drawRect(cx - ex, cy - ex, cx + ex, cy + ex, p);
            p.setXfermode(null);
            c.restore();
        }
        tp.setStyle(Paint.Style.FILL);
        tp.setAlpha(255);
        tp.setTextAlign(Paint.Align.CENTER);
        tp.setTextSize(u * 0.3f);
        tp.setColor(fin ? 0xFF90A4AE : Theme.pcol(idx));
        c.drawText(cut(names[idx], 12), cx, cy + avR + u * 0.4f, tp);
        tp.setColor(TXT);
        if (mine && active) {
            tp.setColor(Theme.pcol(idx));
            c.drawText("Ваш хід!", cx, cy + avR + u * 0.8f, tp);
        }
        if (!mine && !fin) {
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
        // роль: меч — атакує, щит — відбивається, рука — бере
        if (!g.over && !fin && (idx == g.att || idx == g.def)) {
            float rx = cx - avR * 0.9f, ry = cy - avR * 0.8f;
            boolean isDef = idx == g.def;
            int colr = isDef ? (g.taking ? 0xFFEF6C00 : 0xFF1565C0) : 0xFFC62828;
            p.setStyle(Paint.Style.FILL);
            p.setColor(colr);
            c.drawCircle(rx, ry, u * 0.34f, p);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(u * 0.04f);
            p.setColor(0xFFFFFFFF);
            c.drawCircle(rx, ry, u * 0.34f, p);
            roleIcon(c, rx, ry, isDef ? (g.taking ? 2 : 1) : 0);
        }
        if (fin) {
            float rx = cx - avR * 0.9f, ry = cy - avR * 0.8f;
            p.setStyle(Paint.Style.FILL);
            p.setColor(0xDD2E7D32);
            c.drawCircle(rx, ry, u * 0.34f, p);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(u * 0.07f);
            p.setStrokeCap(Paint.Cap.ROUND);
            p.setColor(0xFFFFFFFF);
            c.drawLine(rx - u * 0.16f, ry, rx - u * 0.04f, ry + u * 0.12f, p);
            c.drawLine(rx - u * 0.04f, ry + u * 0.12f, rx + u * 0.17f, ry - u * 0.12f, p);
            p.setStrokeCap(Paint.Cap.BUTT);
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
        return anim;
    }

    // 0 меч, 1 щит, 2 рука
    private void roleIcon(Canvas c, float cx, float cy, int kind) {
        p.setStyle(Paint.Style.FILL);
        p.setColor(0xFFFFFFFF);
        if (kind == 0) {
            c.save();
            c.rotate(45f, cx, cy);
            path.reset();
            path.moveTo(cx, cy - u * 0.25f);
            path.lineTo(cx + u * 0.05f, cy - u * 0.17f);
            path.lineTo(cx + u * 0.05f, cy + u * 0.07f);
            path.lineTo(cx - u * 0.05f, cy + u * 0.07f);
            path.lineTo(cx - u * 0.05f, cy - u * 0.17f);
            path.close();
            c.drawPath(path, p);
            c.drawRect(cx - u * 0.13f, cy + u * 0.07f, cx + u * 0.13f, cy + u * 0.115f, p);
            c.drawRect(cx - u * 0.025f, cy + u * 0.115f, cx + u * 0.025f, cy + u * 0.2f, p);
            c.drawCircle(cx, cy + u * 0.22f, u * 0.04f, p);
            c.restore();
        } else if (kind == 1) {
            path.reset();
            path.moveTo(cx - u * 0.16f, cy - u * 0.18f);
            path.lineTo(cx + u * 0.16f, cy - u * 0.18f);
            path.lineTo(cx + u * 0.16f, cy + u * 0.02f);
            path.quadTo(cx + u * 0.16f, cy + u * 0.15f, cx, cy + u * 0.23f);
            path.quadTo(cx - u * 0.16f, cy + u * 0.15f, cx - u * 0.16f, cy + u * 0.02f);
            path.close();
            c.drawPath(path, p);
        } else {
            rf.set(cx - u * 0.13f, cy - u * 0.02f, cx + u * 0.13f, cy + u * 0.19f);
            c.drawRoundRect(rf, u * 0.05f, u * 0.05f, p);
            for (int k = 0; k < 4; k++) {
                float fx = cx - u * 0.13f + k * u * 0.0725f;
                rf.set(fx, cy - u * (k == 1 || k == 2 ? 0.22f : 0.18f), fx + u * 0.058f, cy + u * 0.03f);
                c.drawRoundRect(rf, u * 0.03f, u * 0.03f, p);
            }
            rf.set(cx - u * 0.22f, cy + u * 0.0f, cx - u * 0.1f, cy + u * 0.08f);
            c.drawRoundRect(rf, u * 0.04f, u * 0.04f, p);
        }
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

    private boolean hitDnd(float x, float y) {
        if (me < 0 || me >= sx.length || me >= n) return false;
        float mx = sx[me] + avR * 0.9f, my = sy[me] - avR * 0.8f;
        float dx = x - mx, dy = y - my;
        return dx * dx + dy * dy <= u * 0.52f * u * 0.52f;
    }

    private boolean dndPress;

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
        if (g == null || listener == null || g.over || g.paused >= 0) return;
        calc(getWidth(), getHeight());
        // рука
        for (int i = g.hand.size() - 1; i >= 0; i--) {
            float cx = hStart + i * hStep;
            float top = (g.legal.get(i) && hint) ? hTop - hRaise : hTop;
            if (x >= cx && x <= cx + hCw && y >= top && y <= hTop + hCh) {
                if (g.legal.get(i)) {
                    listener.onPlay(g.hand.get(i), selTarget);
                } else {
                    listener.onWrong();
                    crossEnc = g.hand.get(i).enc();
                    crossUntil = System.currentTimeMillis() + 1200;
                    invalidate();
                    postInvalidateDelayed(1300);
                }
                return;
            }
        }
        // вибір цілі серед невідбитих карт
        int pairs = g.atk.size();
        if (pairs > 0 && g.def == me && !g.taking) {
            slotSize(pairs);
            for (int k = 0; k < pairs; k++) {
                if (g.dfn.get(k) != null) continue;
                float cxk = slotX(k, pairs), cyk = slotY(k, pairs);
                float ax = pairAx(cxk), ay = cyk - pch / 2f;
                if (x >= ax && x <= ax + pcw && y >= ay && y <= ay + pch) {
                    selTarget = selTarget == k ? -1 : k;
                    invalidate();
                    return;
                }
            }
        }
        if (tapTake && g.canTake && x >= playL - u * 0.3f && x <= playR + u * 0.3f && y >= tTop - u * 0.2f && y <= tTop + u * 3.4f) {
            listener.onTake();
        }
    }
}
