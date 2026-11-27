package com.example.gryntsivgames.thousand;

import com.example.gryntsivgames.uno.Card;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Random;

// Боти «1000». Чесні: бачать лише свою руку, стіл, зіграні взятки, оголошені мар'яжі, рахунок і ставку.
// Чужі руки та прикуп до віддачі не читаються: приховані карти бот лише ймовірнісно роздає собі в уяві
// (невідомі карти без урахування масті, де гравець уже не відповів) і програвляє партію до кінця.
// Рівні: 0 Аматор (груба оцінка, багато випадкових ходів), 1 Студент (мало симуляцій, не рахує карти),
// 2 Геній (рахує карти, більше симуляцій, перебір ходів), 3 Легенда (найбільше симуляцій і перебір ходів).
// Рушій викликає хост: err = bot.act(engine, seat) у чергу бота.
public class TBot {
    public static final int AMATEUR = 0, STUDENT = 1, GENIUS = 2, LEGEND = 3;
    public static final String[] NAMES = {"Аматор", "Студент", "Геній", "Легенда"};

    public static class Give {
        public Card c;
        public int to;
    }

    public static class Move {
        public Card c;
        public boolean mar;
    }

    // карта в коді k = масть * 6 + ранг; ранги за силою: 9, В, Д, К, 10, Т
    private static final int[] RK = new int[15];
    private static final int[] VR = {9, 11, 12, 13, 10, 14};
    private static final int[] PT = {0, 2, 3, 4, 10, 11};
    private static final int MARF = 32;

    static {
        RK[9] = 0;
        RK[11] = 1;
        RK[12] = 2;
        RK[13] = 3;
        RK[10] = 4;
        RK[14] = 5;
    }

    private static int code(Card c) {
        return c.c * 6 + RK[c.v];
    }

    private static Card card(int k) {
        return new Card(k / 6, VR[k % 6]);
    }

    // ---------- стан для програвання партії ----------
    private static final class St {
        int n, decl = -1, trump = -1, lead, tn, contract;
        boolean marAfter, count = true;
        final int[][] hand = new int[4][14];
        final int[] hc = new int[4];
        final int[] tcard = new int[4], tby = new int[4];
        final boolean[] played = new boolean[24];
        final int[] marOwner = {-1, -1, -1, -1};
        final int[] tw = new int[4], pts = new int[4];

        St copy() {
            St s = new St();
            s.n = n;
            s.decl = decl;
            s.trump = trump;
            s.lead = lead;
            s.tn = tn;
            s.contract = contract;
            s.marAfter = marAfter;
            s.count = count;
            for (int i = 0; i < 4; i++) System.arraycopy(hand[i], 0, s.hand[i], 0, 14);
            System.arraycopy(hc, 0, s.hc, 0, 4);
            System.arraycopy(tcard, 0, s.tcard, 0, 4);
            System.arraycopy(tby, 0, s.tby, 0, 4);
            System.arraycopy(played, 0, s.played, 0, 24);
            System.arraycopy(marOwner, 0, s.marOwner, 0, 4);
            System.arraycopy(tw, 0, s.tw, 0, 4);
            System.arraycopy(pts, 0, s.pts, 0, 4);
            return s;
        }

        boolean has(int p, int c) {
            for (int i = 0; i < hc[p]; i++) if (hand[p][i] == c) return true;
            return false;
        }

        void add(int p, int c) {
            hand[p][hc[p]++] = c;
        }

        void remove(int p, int c) {
            for (int i = 0; i < hc[p]; i++) {
                if (hand[p][i] == c) {
                    hand[p][i] = hand[p][--hc[p]];
                    return;
                }
            }
        }

        boolean allEmpty() {
            for (int i = 0; i < n; i++) if (hc[i] > 0) return false;
            return true;
        }

        int cur() {
            return (lead + tn) % n;
        }

        // допустимі карти за правилами рушія; повертає кількість
        int legal(int p, int[] out) {
            int k = 0;
            int[] h = hand[p];
            int c = hc[p];
            if (tn == 0) {
                for (int i = 0; i < c; i++) out[k++] = h[i];
                return k;
            }
            int ls = tcard[0] / 6;
            boolean hasLed = false;
            for (int i = 0; i < c; i++) if (h[i] / 6 == ls) hasLed = true;
            if (hasLed) {
                int hi = -1;
                for (int t = 0; t < tn; t++) if (tcard[t] / 6 == ls && PT[tcard[t] % 6] > hi) hi = PT[tcard[t] % 6];
                boolean canBeat = false;
                for (int i = 0; i < c; i++) if (h[i] / 6 == ls && PT[h[i] % 6] > hi) canBeat = true;
                for (int i = 0; i < c; i++) {
                    if (h[i] / 6 == ls && (!canBeat || PT[h[i] % 6] > hi)) out[k++] = h[i];
                }
                return k;
            }
            if (trump >= 0) {
                boolean hasT = false;
                for (int i = 0; i < c; i++) if (h[i] / 6 == trump) hasT = true;
                if (hasT) {
                    for (int i = 0; i < c; i++) if (h[i] / 6 == trump) out[k++] = h[i];
                    return k;
                }
            }
            for (int i = 0; i < c; i++) out[k++] = h[i];
            return k;
        }

        boolean canMar(int p, int c) {
            if (tn != 0) return false;
            int r = c % 6, s = c / 6;
            if (r != 2 && r != 3) return false;
            if (marOwner[s] >= 0) return false;
            if (!has(p, s * 6 + (r == 3 ? 2 : 3))) return false;
            return !marAfter || tw[p] > 0;
        }

        // чи b бʼє a
        boolean beats(int a, int b) {
            boolean at = trump >= 0 && a / 6 == trump, bt = trump >= 0 && b / 6 == trump;
            if (at && !bt) return false;
            if (bt && !at) return true;
            return a / 6 == b / 6 && PT[b % 6] > PT[a % 6];
        }

        int winIdx() {
            int w = 0;
            for (int k = 1; k < tn; k++) if (beats(tcard[w], tcard[k])) w = k;
            return w;
        }

        void play(int p, int mv) {
            int c = mv & 31;
            remove(p, c);
            tcard[tn] = c;
            tby[tn] = p;
            tn++;
            played[c] = true;
            if ((mv & MARF) != 0) {
                pts[p] += TEngine.MAR[c / 6];
                marOwner[c / 6] = p;
                trump = c / 6;
            }
            if (tn == n) {
                int w = winIdx();
                int who = tby[w];
                int sum = 0;
                for (int k = 0; k < tn; k++) sum += PT[tcard[k] % 6];
                pts[who] += sum;
                tw[who]++;
                lead = who;
                tn = 0;
            }
        }

        void run() {
            int guard = 0;
            while (!allEmpty() && guard++ < 100) {
                int p = cur();
                if (hc[p] == 0) break;
                play(p, choose(p));
            }
        }

        // ---- оцінки карт ----
        boolean higherUnseen(int p, int c) {
            int s = c / 6;
            for (int r = c % 6 + 1; r < 6; r++) {
                int k = s * 6 + r;
                if (!played[k] && !has(p, k)) return true;
            }
            return false;
        }

        boolean isTop(int p, int c) {
            return count ? !higherUnseen(p, c) : c % 6 == 5;
        }

        int unseenIn(int p, int s) {
            int m = 0;
            for (int r = 0; r < 6; r++) {
                int k = s * 6 + r;
                if (!played[k] && !has(p, k)) m++;
            }
            return m;
        }

        // наскільки карта потрібна власникові (менше = охочіше віддаю чи скидаю)
        int keep(int p, int c) {
            int s = c / 6, r = c % 6;
            int v = PT[r] * 2;
            if ((r == 2 || r == 3) && marOwner[s] < 0 && has(p, s * 6 + (r == 3 ? 2 : 3))) v += 40;
            if (s == trump) v += 10;
            int cnt = 0;
            for (int i = 0; i < hc[p]; i++) if (hand[p][i] / 6 == s) cnt++;
            if (cnt >= 3) v += 2;
            return v;
        }

        // ---- політика ходу ----
        int choose(int p) {
            if (tn == 0) return lead(p);
            return follow(p);
        }

        int lead(int p) {
            if (!marAfter || tw[p] > 0) {
                int bs = -1;
                for (int s = 0; s < 4; s++) {
                    if (marOwner[s] < 0 && has(p, s * 6 + 3) && has(p, s * 6 + 2) && (bs < 0 || TEngine.MAR[s] > TEngine.MAR[bs])) bs = s;
                }
                if (bs >= 0) {
                    int k = bs * 6 + 3, q = bs * 6 + 2;
                    return (isTop(p, k) ? k : q) | MARF;
                }
            }
            int best = -1, bv = Integer.MIN_VALUE;
            for (int i = 0; i < hc[p]; i++) {
                int c = hand[p][i];
                int s = c / 6, r = c % 6;
                int utr = (trump >= 0 && s != trump) ? unseenIn(p, trump) : 0;
                int v;
                if (isTop(p, c)) {
                    v = 60 + PT[r];
                    if (s == trump) v += 15;
                    else if (utr > 0) v -= 14;
                } else {
                    v = -PT[r] * 3;
                }
                if ((r == 2 || r == 3) && marOwner[s] < 0 && has(p, s * 6 + (r == 3 ? 2 : 3))) v -= 40;
                if (v > bv) {
                    bv = v;
                    best = c;
                }
            }
            return best;
        }

        int follow(int p) {
            int[] lg = new int[14];
            int k = legal(p, lg);
            if (k == 1) return lg[0];
            int wi = winIdx();
            int wc = tcard[wi], ww = tby[wi];
            boolean ally = n > 2 && p != decl && ww != decl && ww != p;
            boolean last = tn == n - 1;
            int cheap = -1;
            int dump = -1, dv = Integer.MAX_VALUE;
            for (int i = 0; i < k; i++) {
                int c = lg[i];
                if (beats(wc, c) && (cheap < 0 || PT[c % 6] < PT[cheap % 6])) cheap = c;
                int kv = keep(p, c);
                if (kv < dv) {
                    dv = kv;
                    dump = c;
                }
            }
            if (cheap >= 0) return cheap;
            if (ally) {
                boolean sure = last || (!higherUnseen(p, wc) && (trump < 0 || wc / 6 == trump || unseenIn(p, trump) == 0));
                if (sure) {
                    int feed = -1;
                    for (int i = 0; i < k; i++) {
                        int c = lg[i];
                        if (c / 6 == trump) continue;
                        if (keep(p, c) >= PT[c % 6] * 2 + 40) continue;
                        if (feed < 0 || PT[c % 6] > PT[feed % 6]) feed = c;
                    }
                    if (feed >= 0) return feed;
                }
            }
            return dump;
        }
    }

    // пороги ставки: очікуваний виграш (ставка * (2P - 1)) за рівнями Студент, Геній, Легенда; частка успіху на бочці
    private static final double[] BID_EV = {40, 30, 26};
    private static final double BARREL_P = 0.30;
    public final int level;
    private final Random rnd;
    // пам'ять: зіграні карти, масті, у яких гравець порожній, карти, віддані мною
    private final boolean[] played = new boolean[24];
    private final boolean[][] voidS = new boolean[4][4];
    private final int[] givenTo = new int[24];
    private int round = -1, seq;

    public TBot(int level, Random rnd) {
        this.level = Math.max(0, Math.min(3, level));
        this.rnd = rnd;
        Arrays.fill(givenTo, -1);
    }

    // виконує хід бота у свою чергу; null = успіх
    public String act(TEngine e, int p) {
        try {
            obs(e);
            return actImpl(e, p);
        } catch (RuntimeException ex) {
            return safeAct(e, p);
        }
    }

    private String actImpl(TEngine e, int p) {
        switch (e.phase) {
            case TEngine.BID: {
                int a = bidAmount(e, p);
                return a > 0 ? e.bid(p, a) : e.passBid(p);
            }
            case TEngine.GIVE: {
                Give g = give(e, p);
                return e.give(p, g.c, g.to);
            }
            case TEngine.FINAL:
                return e.finalBid(p, finalAmount(e, p));
            case TEngine.PLAY: {
                Move m = play(e, p);
                return e.play(p, m.c, m.mar);
            }
            default:
                return "Зараз ходу немає";
        }
    }

    // найпростіший допустимий хід, якщо розрахунок зламався
    private String safeAct(TEngine e, int p) {
        switch (e.phase) {
            case TEngine.BID:
                return e.passBid(p);
            case TEngine.GIVE:
                for (Card c : new ArrayList<Card>(e.hands.get(p))) {
                    for (int t = 0; t < e.n; t++) if (e.canGive(p, c, t)) return e.give(p, c, t);
                }
                return "Нема що віддати";
            case TEngine.FINAL:
                return e.finalBid(p, e.finalMin());
            case TEngine.PLAY:
                for (Card c : new ArrayList<Card>(e.hands.get(p))) if (e.legalCard(p, c)) return e.play(p, c, false);
                return "Нема ходу";
            default:
                return "Зараз ходу немає";
        }
    }

    // ---------- пам'ять ----------
    private void obs(TEngine e) {
        if (e.roundNo != round) {
            round = e.roundNo;
            Arrays.fill(played, false);
            Arrays.fill(givenTo, -1);
            for (boolean[] a : voidS) Arrays.fill(a, false);
            seq = e.trickSeq;
        }
        if (e.trickSeq != seq) {
            seq = e.trickSeq;
            if (!e.lastTrick.isEmpty()) {
                int led = e.lastTrick.get(0).c;
                for (int k = 0; k < e.lastTrick.size(); k++) {
                    Card c = e.lastTrick.get(k);
                    played[code(c)] = true;
                    int who = e.lastTrickBy.get(k);
                    if (c.c != led) {
                        voidS[who][led] = true;
                        // не поклав ні масті, ні козиря: козиря в нього теж немає
                        if (e.trump >= 0 && c.c != e.trump) voidS[who][e.trump] = true;
                    }
                }
            }
        }
    }

    // ---------- допоміжне ----------
    private static int handPts(ArrayList<Card> h) {
        int s = 0;
        for (Card c : h) s += TEngine.points(c);
        return s;
    }

    private static int marSum(TEngine e, int p) {
        int s = 0;
        for (int m : e.marriagesOf(p)) s += TEngine.MAR[m];
        return s;
    }

    private static int r5(int x) {
        return x < 0 ? 0 : x / 5 * 5;
    }

    private boolean stud() {
        return level >= STUDENT;
    }

    // ---------- вибірка прихованих рук ----------
    // кладе у стан s чужі руки з невідомого пулу; cap[i] - скільки карт потрібно гравцеві i; повертає false, якщо не вийшло
    private boolean fillHands(St s, int me, ArrayList<Integer> pool, int[] cap, boolean useVoid) {
        ArrayList<Integer> pl = new ArrayList<Integer>(pool);
        Collections.shuffle(pl, rnd);
        int[] el = new int[4];
        for (int c : pl) {
            int m = 0;
            for (int i = 0; i < s.n; i++) {
                if (i == me || cap[i] <= 0) continue;
                if (useVoid && voidS[i][c / 6]) continue;
                el[m++] = i;
            }
            if (m == 0) {
                boolean full = true;
                for (int i = 0; i < s.n; i++) if (i != me && cap[i] > 0) full = false;
                if (full) break;
                return false;
            }
            int who = el[rnd.nextInt(m)];
            s.add(who, c);
            cap[who]--;
        }
        return true;
    }

    // стан гри за відкритою інформацією + моя рука; руки інших порожні (їх додає fillWorld)
    private St baseState(TEngine e, int me, boolean count) {
        St s = new St();
        s.n = e.n;
        s.decl = e.decl;
        s.trump = e.trump;
        s.lead = e.phase == TEngine.PLAY ? e.leader : e.decl;
        s.marAfter = e.marAfter;
        s.contract = e.contract;
        s.count = count;
        for (int i = 0; i < e.n; i++) {
            s.tw[i] = e.tricksWon[i];
            s.pts[i] = e.cardPts[i] + e.marPts[i];
        }
        for (int k = 0; k < 4; k++) s.marOwner[k] = e.marOwner[k];
        System.arraycopy(played, 0, s.played, 0, 24);
        for (Card c : e.hands.get(me)) s.add(me, code(c));
        for (int k = 0; k < e.trick.size(); k++) {
            int c = code(e.trick.get(k));
            s.tcard[k] = c;
            s.tby[k] = e.trickBy.get(k);
            s.played[c] = true;
            s.tn++;
        }
        return s;
    }

    // роздає чужі руки: відомі карти (віддані мною, пара оголошеного мар'яжу) на свої місця, решта випадково
    private boolean fillWorld(TEngine e, St s, int me) {
        int[] cap = new int[4];
        for (int i = 0; i < e.n; i++) cap[i] = i == me ? 0 : e.hands.get(i).size();
        boolean[] fixed = new boolean[24];
        for (int c = 0; c < 24; c++) {
            if (s.played[c] || s.has(me, c) || givenTo[c] < 0 || givenTo[c] == me) continue;
            int o = givenTo[c];
            if (cap[o] > 0) {
                s.add(o, c);
                cap[o]--;
                fixed[c] = true;
            }
        }
        for (int su = 0; su < 4; su++) {
            int q = e.marOwner[su];
            if (q < 0 || q == me) continue;
            for (int r = 2; r <= 3; r++) {
                int c = su * 6 + r;
                if (s.played[c] || s.has(me, c) || fixed[c] || cap[q] <= 0) continue;
                s.add(q, c);
                cap[q]--;
                fixed[c] = true;
            }
        }
        ArrayList<Integer> pool = new ArrayList<Integer>();
        for (int c = 0; c < 24; c++) if (!s.played[c] && !s.has(me, c) && !fixed[c]) pool.add(c);
        int[] save = cap.clone();
        int[][] h0 = new int[4][];
        int[] c0 = s.hc.clone();
        for (int i = 0; i < 4; i++) h0[i] = s.hand[i].clone();
        for (int attempt = 0; attempt < 12; attempt++) {
            if (fillHands(s, me, pool, cap, attempt < 9) && full(e, s, me)) return true;
            cap = save.clone();
            System.arraycopy(c0, 0, s.hc, 0, 4);
            for (int i = 0; i < 4; i++) System.arraycopy(h0[i], 0, s.hand[i], 0, 14);
        }
        return false;
    }

    private static boolean full(TEngine e, St s, int me) {
        for (int i = 0; i < e.n; i++) if (i != me && s.hc[i] != e.hands.get(i).size()) return false;
        return true;
    }

    // ---------- торги ----------
    // очки господаря, якщо взяти прикуп, віддати карти й зіграти: N випадкових світів (прикуп теж невідомий)
    private int[] rawAfterTalon(TEngine e, int p, int N) {
        int[] out = new int[N];
        int m = 0;
        ArrayList<Integer> pool = new ArrayList<Integer>();
        boolean[] mine = new boolean[24];
        for (Card c : e.hands.get(p)) mine[code(c)] = true;
        for (int c = 0; c < 24; c++) if (!mine[c]) pool.add(c);
        int T = e.talon.size();
        int need = e.n == 2 ? 2 : 1;
        for (int w = 0; w < N; w++) {
            St s = new St();
            s.n = e.n;
            s.decl = p;
            s.marAfter = e.marAfter;
            s.lead = p;
            s.count = true;
            for (Card c : e.hands.get(p)) s.add(p, code(c));
            ArrayList<Integer> pl = new ArrayList<Integer>(pool);
            Collections.shuffle(pl, rnd);
            int at = 0;
            for (int k = 0; k < T; k++) s.add(p, pl.get(at++));
            for (int i = 0; i < e.n; i++) {
                if (i == p) continue;
                int sz = e.hands.get(i).size();
                for (int k = 0; k < sz; k++) s.add(i, pl.get(at++));
            }
            giveSim(s, p, need);
            s.run();
            out[m++] = s.pts[p];
        }
        return out;
    }

    // віддає найменш потрібні карти по need кожному суперникові (по черзі)
    private static void giveSim(St s, int p, int need) {
        int[] left = new int[4];
        for (int i = 0; i < s.n; i++) left[i] = i == p ? 0 : need;
        int total = (s.n - 1) * need;
        for (int t = 0; t < total; t++) {
            int worst = -1, wv = Integer.MAX_VALUE;
            for (int i = 0; i < s.hc[p]; i++) {
                int c = s.hand[p][i];
                int v = s.keep(p, c);
                if (v < wv) {
                    wv = v;
                    worst = c;
                }
            }
            int to = -1;
            for (int i = 0; i < s.n; i++) if (left[i] > 0) {
                to = i;
                break;
            }
            s.remove(p, worst);
            s.add(to, worst);
            left[to]--;
        }
    }

    // частка світів, де очки >= c
    private static double share(int[] raw, int c) {
        int k = 0;
        for (int x : raw) if (x >= c) k++;
        return raw.length == 0 ? 0 : k / (double) raw.length;
    }

    private int bidAmount(TEngine e, int p) {
        if (!e.canBid(p)) return 0;
        int mn = e.minBid();
        ArrayList<Card> h = e.hands.get(p);
        if (level == AMATEUR) {
            int cap = (int) (handPts(h) * 0.9f) + marSum(e, p) + 12 + rnd.nextInt(41) - 20;
            if (e.onBarrel[p]) return mn <= 125 && cap >= 115 ? mn : 0;
            return mn <= cap - 10 ? mn : 0;
        }
        int N = level == STUDENT ? 14 : (level == GENIUS ? 36 : 80);
        int[] raw = rawAfterTalon(e, p, N);
        int c = e.onBarrel[p] ? Math.max(mn, TEngine.BARREL_MIN_BID) : mn;
        double pr = share(raw, c);
        if (e.onBarrel[p]) return mn <= 125 && pr >= BARREL_P ? mn : 0;
        double ev = c * (2 * pr - 1);
        double need = BID_EV[level - 1];
        return ev >= need ? mn : 0;
    }

    // ---------- віддача карт ----------
    public Give give(TEngine e, int p) {
        ArrayList<Card> h = e.hands.get(p);
        St s = baseState(e, p, true);
        Card worst = null;
        int wv = 1 << 30;
        for (Card c : h) {
            int v = s.keep(p, code(c)) * 4 + rnd.nextInt(3);
            if (level == AMATEUR) v = rnd.nextInt(100) + s.keep(p, code(c));
            if (v < wv) {
                wv = v;
                worst = c;
            }
        }
        Give g = new Give();
        g.c = worst;
        g.to = -1;
        int best = -1 << 30;
        for (int i = 0; i < e.n; i++) {
            if (!e.canGive(p, worst, i)) continue;
            int sc = level >= GENIUS ? e.total[i] : (level == AMATEUR ? rnd.nextInt(100) : 0);
            if (g.to < 0 || sc > best) {
                best = sc;
                g.to = i;
            }
        }
        if (g.to >= 0) givenTo[code(worst)] = g.to;
        return g;
    }

    // ---------- остаточна ставка ----------
    private int finalAmount(TEngine e, int p) {
        int lo = e.finalMin(), hi = e.finalMax();
        if (level == AMATEUR) {
            int est = handPts(e.hands.get(p)) + marSum(e, p) + 8;
            int margin = 25 + rnd.nextInt(15);
            return Math.max(lo, Math.min(hi, r5(est - margin)));
        }
        if (e.onBarrel[p] || hi <= lo) return lo;
        int N = level == STUDENT ? 16 : (level == GENIUS ? 60 : 120);
        int[] raw = new int[N];
        int m = 0;
        for (int w = 0; w < N; w++) {
            St s = baseState(e, p, true);
            s.lead = p;
            if (!fillWorld(e, s, p)) continue;
            s.run();
            raw[m++] = s.pts[p];
        }
        raw = Arrays.copyOf(raw, m);
        if (m == 0) return lo;
        int bestC = lo;
        double bestEv = -1e9;
        for (int c = lo; c <= hi; c += TEngine.STEP) {
            double pr = share(raw, c);
            double ev = c * (2 * pr - 1);
            if (level == STUDENT && pr < 0.7) ev -= 40;
            if (ev > bestEv + 1e-9) {
                bestEv = ev;
                bestC = c;
            }
        }
        return bestC;
    }

    // ---------- розіграш ----------
    private Move mv(int code, boolean mar) {
        Move m = new Move();
        m.c = card(code);
        m.mar = mar;
        return m;
    }

    public Move play(TEngine e, int p) {
        St s = baseState(e, p, level >= GENIUS);
        int[] lg = new int[14];
        int k = s.legal(p, lg);
        if (level == AMATEUR && rnd.nextInt(100) < 55) {
            int c = lg[rnd.nextInt(k)];
            return mv(c, s.canMar(p, c) && rnd.nextInt(100) < 60);
        }
        int heur = s.choose(p);
        if (level >= GENIUS && k > 1) {
            int worlds = level == GENIUS ? 24 : 64;
            int pick = search(e, s, p, lg, k, heur, worlds);
            return mv(pick & 31, (pick & MARF) != 0);
        }
        return mv(heur & 31, (heur & MARF) != 0);
    }

    private double score(St t, int p) {
        int d = t.decl;
        boolean ok = t.pts[d] >= t.contract;
        if (p == d) return (ok ? 100 : 0) + t.pts[d] * 0.25;
        return (ok ? 0 : 100) + t.pts[p] * 0.4 - t.pts[d] * 0.1;
    }

    // перебір ходів: кожен хід програвається у випадкових світах до кінця партії
    private int search(TEngine e, St base, int p, int[] lg, int k, int heur, int worlds) {
        int[] cand = new int[k * 2];
        int m = 0;
        for (int i = 0; i < k; i++) {
            cand[m++] = lg[i];
            if (base.canMar(p, lg[i])) cand[m++] = lg[i] | MARF;
        }
        if (m == 1) return cand[0];
        double[] sc = new double[m];
        int used = 0;
        for (int w = 0; w < worlds; w++) {
            St world = base.copy();
            world.count = true;
            if (!fillWorld(e, world, p)) continue;
            used++;
            for (int j = 0; j < m; j++) {
                St t = world.copy();
                t.play(p, cand[j]);
                t.run();
                sc[j] += score(t, p);
            }
        }
        if (used == 0) return heur;
        int best = 0;
        double bv = -1e18;
        for (int j = 0; j < m; j++) {
            double v = sc[j] / used + (cand[j] == heur ? 0.5 : 0);
            if (v > bv) {
                bv = v;
                best = j;
            }
        }
        return cand[best];
    }
}
