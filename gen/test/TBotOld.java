package com.example.gryntsivgames.thousand;

import com.example.gryntsivgames.uno.Card;

import java.util.ArrayList;
import java.util.Random;

// Боти «1000». Чесні: бачать лише свою руку, стіл, зіграні взятки, оголошені мар'яжі, рахунок і ставку.
// Чужі руки та прикуп до віддачі не читаються. Один бот на одне місце; пам'ять скидається на початку кожної партії.
// Рівні: 0 Аматор, 1 Студент, 2 Геній (рахує карти), 3 Легенда (ще й пустує масті суперників, точніша ставка).
// Рушій викликає хост: err = bot.act(engine, seat) у чергу бота.
public class TBotOld {
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

    public final int level;
    private final Random rnd;
    private final boolean[][] seen = new boolean[4][15];
    private final boolean[][] voidS = new boolean[4][4];
    private int round = -1, seq;

    public TBotOld(int level, Random rnd) {
        this.level = Math.max(0, Math.min(3, level));
        this.rnd = rnd;
    }

    // виконує хід бота у свою чергу; null = успіх
    public String act(TEngine e, int p) {
        obs(e);
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

    // ---------- пам'ять ----------
    private void obs(TEngine e) {
        if (e.roundNo != round) {
            round = e.roundNo;
            for (boolean[] a : seen) java.util.Arrays.fill(a, false);
            for (boolean[] a : voidS) java.util.Arrays.fill(a, false);
            seq = e.trickSeq;
        }
        if (e.trickSeq != seq) {
            seq = e.trickSeq;
            if (!e.lastTrick.isEmpty()) {
                int led = e.lastTrick.get(0).c;
                for (int k = 0; k < e.lastTrick.size(); k++) {
                    Card c = e.lastTrick.get(k);
                    seen[c.c][c.v] = true;
                    if (c.c != led) voidS[e.lastTrickBy.get(k)][led] = true;
                }
            }
        }
    }

    private static boolean inTrick(TEngine e, Card c) {
        for (Card k : e.trick) if (k.same(c)) return true;
        return false;
    }

    private boolean unseen(TEngine e, int p, Card c) {
        return !seen[c.c][c.v] && !e.has(p, c) && !inTrick(e, c);
    }

    // чи лишилась невідома карта цієї масті, що старша за c
    private boolean beatable(TEngine e, int p, Card c) {
        for (int v = 9; v <= 14; v++) {
            Card k = new Card(c.c, v);
            if (TEngine.points(k) > TEngine.points(c) && unseen(e, p, k)) return true;
        }
        return false;
    }

    private int unseenOfSuit(TEngine e, int p, int s) {
        int n = 0;
        for (int v = 9; v <= 14; v++) if (unseen(e, p, new Card(s, v))) n++;
        return n;
    }

    // ---------- оцінки ----------
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

    private static boolean pair(TEngine e, ArrayList<Card> h, Card c) {
        if (c.v != 12 && c.v != 13 || e.marOwner[c.c] >= 0) return false;
        int o = c.v == 13 ? 12 : 13;
        for (Card k : h) if (k.c == c.c && k.v == o) return true;
        return false;
    }

    // наскільки карта потрібна мені (чим менше, тим охочіше віддаю чи скидаю)
    private int keep(TEngine e, ArrayList<Card> h, Card c) {
        int v = TEngine.points(c) * 2;
        if (pair(e, h, c)) v += 40;
        if (e.trump == c.c) v += 10;
        int cnt = 0;
        for (Card k : h) if (k.c == c.c) cnt++;
        if (cnt >= 3) v += 2;
        return v;
    }

    // ---------- торги ----------
    private int bidAmount(TEngine e, int p) {
        if (!e.canBid(p)) return 0;
        ArrayList<Card> h = e.hands.get(p);
        int cap = handPts(h) + marSum(e, p) + e.talon.size() * 2;
        if (level == AMATEUR) {
            cap += rnd.nextInt(31) - 15;
        } else if (level >= GENIUS) {
            int aces = 0;
            for (Card c : h) if (c.v == 14) aces++;
            if (aces >= 2) cap += 5;
            if (e.marriagesOf(p).isEmpty()) cap -= 5;
        }
        int mn = e.minBid();
        if (e.onBarrel[p]) return mn <= 125 ? mn : 0;
        if (mn > cap) return 0;
        int amt = mn;
        if (level == AMATEUR && rnd.nextInt(4) == 0) amt = Math.min(cap / 5 * 5, mn + 5 * (1 + rnd.nextInt(2)));
        return Math.max(mn, Math.min(amt, TEngine.MAX_BID));
    }

    // ---------- віддача карт ----------
    public Give give(TEngine e, int p) {
        ArrayList<Card> h = e.hands.get(p);
        Card worst = null;
        int wv = 1 << 30;
        for (Card c : h) {
            int v = keep(e, h, c) * 4 + rnd.nextInt(3);
            if (level == AMATEUR) v = rnd.nextInt(100) + keep(e, h, c);
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
        return g;
    }

    // ---------- остаточна ставка ----------
    private int finalAmount(TEngine e, int p) {
        int lo = e.finalMin(), hi = e.finalMax();
        int est = handPts(e.hands.get(p)) + marSum(e, p) + 8;
        int margin = level == AMATEUR ? 25 + rnd.nextInt(15) : (level == STUDENT ? 20 : 15);
        int v = r5(est - margin);
        return Math.max(lo, Math.min(hi, v));
    }

    // ---------- розіграш ----------
    private static boolean beats(Card a, Card b, int trump) {
        boolean at = trump >= 0 && a.c == trump, bt = trump >= 0 && b.c == trump;
        if (at && !bt) return false;
        if (bt && !at) return true;
        return a.c == b.c && TEngine.points(b) > TEngine.points(a);
    }

    private static int bestIdx(TEngine e) {
        int w = 0;
        for (int k = 1; k < e.trick.size(); k++) if (beats(e.trick.get(w), e.trick.get(k), e.trump)) w = k;
        return w;
    }

    private Move mv(Card c, boolean mar) {
        Move m = new Move();
        m.c = c;
        m.mar = mar;
        return m;
    }

    public Move play(TEngine e, int p) {
        ArrayList<Card> h = e.hands.get(p);
        ArrayList<Card> legal = new ArrayList<Card>();
        for (Card c : h) if (e.legalCard(p, c)) legal.add(c);
        if (level == AMATEUR && rnd.nextInt(100) < 55) {
            Card c = legal.get(rnd.nextInt(legal.size()));
            return mv(c, e.canMarriage(p, c) && rnd.nextInt(100) < 60);
        }
        if (e.trick.isEmpty()) return lead(e, p, h, legal);
        return follow(e, p, h, legal);
    }

    private Move lead(TEngine e, int p, ArrayList<Card> h, ArrayList<Card> legal) {
        // мар'яж: оголошуємо найціннішу масть; веду ту карту пари, яку менш ризиковано програти
        Card bm = null;
        for (Card c : legal) {
            if (!e.canMarriage(p, c)) continue;
            if (bm == null || TEngine.MAR[c.c] > TEngine.MAR[bm.c]) {
                bm = c;
            } else if (TEngine.MAR[c.c] == TEngine.MAR[bm.c]) {
                boolean safeK = level >= GENIUS && !beatable(e, p, new Card(c.c, 13));
                if (safeK ? c.v == 13 : (c.v == 12 && level >= GENIUS) || (c.v == 13 && level < GENIUS)) bm = c;
            }
        }
        if (bm != null) return mv(bm, true);

        Card pick = null;
        if (level >= GENIUS) {
            // найстарша «певна» карта: жодна невидима карта масті її не б'є
            for (Card c : legal) {
                if (beatable(e, p, c)) continue;
                if (level == LEGEND && c.c != e.trump && e.trump >= 0 && opponentVoid(e, p, c.c) && unseenOfSuit(e, p, e.trump) > 0) continue;
                int sc = TEngine.points(c) + (c.c == e.trump && unseenOfSuit(e, p, e.trump) > 0 ? 20 : 0);
                if (pick == null || sc > TEngine.points(pick) + (pick.c == e.trump && unseenOfSuit(e, p, e.trump) > 0 ? 20 : 0)) pick = c;
            }
        } else {
            for (Card c : legal) if (c.v == 14 && (pick == null || c.c != e.trump)) pick = c;
        }
        if (pick != null) return mv(pick, false);
        // інакше веду найдешевшу карту (не козир і не частину пари)
        Card low = null;
        int lv = 1 << 30;
        for (Card c : legal) {
            int v = keep(e, h, c) * 4 + rnd.nextInt(3);
            if (level == LEGEND && opponentVoid(e, p, c.c)) v += 30;
            if (v < lv) {
                lv = v;
                low = c;
            }
        }
        return mv(low, false);
    }

    private boolean opponentVoid(TEngine e, int p, int s) {
        for (int i = 0; i < e.n; i++) if (i != p && (e.decl == p || i == e.decl) && voidS[i][s]) return true;
        return false;
    }

    private Move follow(TEngine e, int p, ArrayList<Card> h, ArrayList<Card> legal) {
        int bi = bestIdx(e);
        Card best = e.trick.get(bi);
        int winner = e.trickBy.get(bi);
        boolean ally = p != e.decl && winner != e.decl && winner != p;
        boolean last = e.trick.size() == e.n - 1;
        int tp = 0;
        for (Card k : e.trick) tp += TEngine.points(k);

        Card cheap = null, rich = null;
        for (Card c : legal) {
            if (!beats(best, c, e.trump)) continue;
            if (cheap == null || TEngine.points(c) < TEngine.points(cheap)) cheap = c;
            if (rich == null || TEngine.points(c) > TEngine.points(rich)) rich = c;
        }
        // скид: найменш потрібна карта
        Card dump = null;
        int dv = 1 << 30;
        for (Card c : legal) {
            int v = keep(e, h, c) * 4 + rnd.nextInt(3);
            if (v < dv) {
                dv = v;
                dump = c;
            }
        }
        if (level >= GENIUS && ally) {
            // союзник бере: якщо я останній, підкидаю найбільше очок (не козир)
            if (last) {
                Card feed = null;
                for (Card c : legal) if (c.c != e.trump && (feed == null || TEngine.points(c) > TEngine.points(feed))) feed = c;
                if (feed != null && !pair(e, h, feed)) return mv(feed, false);
            }
            return mv(dump, false);
        }
        if (cheap != null) {
            if (level >= GENIUS && cheap.c == e.trump && best.c != e.trump && tp < 4 && !last) return mv(dump, false);
            return mv(cheap, false);
        }
        return mv(dump, false);
    }
}
