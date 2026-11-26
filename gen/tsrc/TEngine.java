package com.example.gryntsivgames.thousand;

import com.example.gryntsivgames.uno.Card;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Random;

// Правила «1000» (24 карти). Масті c: 0 піки, 1 чирви, 2 бубни, 3 хрести. Ранг v: 9, 10, 11 В, 12 Д, 13 К, 14 Т.
// Рушій веде весь матч: партії, рахунок, бочку, болти. Гравців 2-4.
// Усі дії повертають null (успіх) або текст помилки.
public class TEngine {
    // фази партії: торги, віддача карт, остаточна ставка, розіграш, кінець партії
    public static final int BID = 0, GIVE = 1, FINAL = 2, PLAY = 3, END = 4;
    // мар'яж за мастями: піки 40, чирви 100, бубни 80, хрести 60
    public static final int[] MAR = {40, 100, 80, 60};
    public static final int START_BID = 100, STEP = 5, MAX_BID = 400;
    public static final int BARREL_AT = 880, BARREL_DROP = 760, WIN = 1000, BARREL_ROUNDS = 3, BARREL_MIN_BID = 120;
    // прапорці рядка історії (flags): болт, штраф за 3 болти, на бочці, зліт з бочки, ставку не виконано, господар партії
    public static final int F_BOLT = 1, F_BOLT_PEN = 2, F_BARREL = 4, F_DROP = 8, F_FAIL = 16, F_DECL = 32;

    public int n;
    public String[] names;
    public final boolean marAfter, barrelOn, boltsOn;
    private final Random rnd;

    // партія
    public int dealer, phase, roundNo;
    public final ArrayList<ArrayList<Card>> hands = new ArrayList<ArrayList<Card>>();
    public final ArrayList<Card> talon = new ArrayList<Card>();
    // торги: bid 0 = ставок ще не було
    public int bid, bidder = -1, turn;
    public boolean[] passed;
    // господар і контракт
    public int decl = -1, contract;
    public int[] giveNeed;
    // розіграш
    public int trump = -1, leader = -1, tricksPlayed, trickSeq, lastWinner = -1;
    public final ArrayList<Card> trick = new ArrayList<Card>();
    public final ArrayList<Integer> trickBy = new ArrayList<Integer>();
    public final ArrayList<Card> lastTrick = new ArrayList<Card>();
    public final ArrayList<Integer> lastTrickBy = new ArrayList<Integer>();
    public int[] tricksWon, cardPts, marPts, raw;
    public int[] marOwner = {-1, -1, -1, -1};
    // матч
    public int[] total, boltCnt, barrelRnd;
    public boolean[] onBarrel;
    public final ArrayList<int[]> rounds = new ArrayList<int[]>();
    public final ArrayList<int[]> flags = new ArrayList<int[]>();
    public boolean matchOver;
    public int winner = -1;
    public String msg = "";
    public final ArrayList<String> log = new ArrayList<String>();

    private TEngine(Random rnd, boolean marAfter, boolean barrelOn, boolean boltsOn) {
        this.rnd = rnd;
        this.marAfter = marAfter;
        this.barrelOn = barrelOn;
        this.boltsOn = boltsOn;
    }

    // dealer < 0: випадковий роздаючий
    public TEngine(String[] names, boolean marAfter, boolean barrelOn, boolean boltsOn, int dealer, Random rnd) {
        this(rnd, marAfter, barrelOn, boltsOn);
        this.names = names;
        this.n = names.length;
        if (n < 2 || n > 4) throw new RuntimeException("n");
        total = new int[n];
        boltCnt = new int[n];
        barrelRnd = new int[n];
        onBarrel = new boolean[n];
        this.dealer = (dealer < 0 || dealer >= n) ? rnd.nextInt(n) : dealer;
        deal();
    }

    // ---------- допоміжні ----------
    public static int points(Card c) {
        switch (c.v) {
            case 14:
                return 11;
            case 10:
                return 10;
            case 13:
                return 4;
            case 12:
                return 3;
            case 11:
                return 2;
            default:
                return 0;
        }
    }

    public static String cs(Card c) {
        String r = c.v == 9 ? "9" : (c.v == 10 ? "10" : (c.v == 11 ? "В" : (c.v == 12 ? "Д" : (c.v == 13 ? "К" : "Т"))));
        return r + "♠♥♦♣".charAt(c.c);
    }

    private static int round5(int x) {
        return (x + 2) / 5 * 5;
    }

    private void say(String s) {
        msg = s;
        log.add(s);
    }

    private static int indexOf(ArrayList<Card> h, Card c) {
        for (int i = 0; i < h.size(); i++) if (h.get(i).same(c)) return i;
        return -1;
    }

    public boolean has(int p, Card c) {
        return p >= 0 && p < n && indexOf(hands.get(p), c) >= 0;
    }

    private boolean hasRank(int p, int suit, int v) {
        for (Card k : hands.get(p)) if (k.c == suit && k.v == v) return true;
        return false;
    }

    // масті, в яких гравець тримає К і Д
    public ArrayList<Integer> marriagesOf(int p) {
        ArrayList<Integer> r = new ArrayList<Integer>();
        for (int s = 0; s < 4; s++) if (hasRank(p, s, 13) && hasRank(p, s, 12)) r.add(s);
        return r;
    }

    // ---------- роздача ----------
    private void deal() {
        roundNo++;
        ArrayList<Card> deck = new ArrayList<Card>();
        for (int s = 0; s < 4; s++) for (int v = 9; v <= 14; v++) deck.add(new Card(s, v));
        Collections.shuffle(deck, rnd);
        int per = n == 2 ? 10 : (n == 3 ? 7 : 5);
        int tal = n == 3 ? 3 : 4;
        hands.clear();
        for (int i = 0; i < n; i++) {
            ArrayList<Card> h = new ArrayList<Card>();
            for (int k = 0; k < per; k++) h.add(deck.remove(deck.size() - 1));
            hands.add(h);
        }
        talon.clear();
        for (int k = 0; k < tal; k++) talon.add(deck.remove(deck.size() - 1));
        passed = new boolean[n];
        bid = 0;
        bidder = -1;
        turn = (dealer + 1) % n;
        decl = -1;
        contract = 0;
        giveNeed = new int[n];
        trump = -1;
        leader = -1;
        trick.clear();
        trickBy.clear();
        lastTrick.clear();
        lastTrickBy.clear();
        lastWinner = -1;
        tricksPlayed = 0;
        tricksWon = new int[n];
        cardPts = new int[n];
        marPts = new int[n];
        raw = new int[n];
        for (int s = 0; s < 4; s++) marOwner[s] = -1;
        phase = BID;
        say("Роздає " + names[dealer] + ", торги починає " + names[turn]);
    }

    public String nextRound() {
        if (phase != END || matchOver) return "Партія ще триває";
        dealer = (dealer + 1) % n;
        deal();
        return null;
    }

    // хто зараз має діяти; -1 якщо ніхто
    public int cur() {
        if (phase == BID) return turn;
        if (phase == GIVE || phase == FINAL) return decl;
        if (phase == PLAY) return (leader + trick.size()) % n;
        return -1;
    }

    // ---------- торги ----------
    public int minBid() {
        return bidder < 0 ? START_BID : bid + STEP;
    }

    public boolean canBid(int p) {
        return phase == BID && p == turn && minBid() <= MAX_BID;
    }

    public boolean canPassBid(int p) {
        return phase == BID && p == turn;
    }

    public String bid(int p, int amount) {
        if (phase != BID || p != turn) return "Зараз не ваша черга";
        if (amount % STEP != 0 || amount < minBid() || amount > MAX_BID) return "Некоректна ставка";
        bid = amount;
        bidder = p;
        say(names[p] + " ставить " + amount);
        advance();
        return null;
    }

    public String passBid(int p) {
        if (phase != BID || p != turn) return "Зараз не ваша черга";
        passed[p] = true;
        say(names[p] + ": пас");
        advance();
        return null;
    }

    private void advance() {
        int left = 0, last = -1;
        for (int i = 0; i < n; i++) {
            if (!passed[i]) {
                left++;
                last = i;
            }
        }
        if (left > 1) {
            int q = turn;
            for (int k = 0; k < n; k++) {
                q = (q + 1) % n;
                if (!passed[q]) {
                    turn = q;
                    return;
                }
            }
            return;
        }
        if (bidder < 0) {
            // усі спасували: грає роздаючий
            bidder = dealer;
            bid = onBarrel[dealer] ? BARREL_MIN_BID : START_BID;
            say("Усі спасували, " + names[dealer] + " грає за " + bid);
        } else {
            bidder = last;
        }
        decl = bidder;
        contract = bid;
        hands.get(decl).addAll(talon);
        for (int i = 0; i < n; i++) giveNeed[i] = i == decl ? 0 : (n == 2 ? 2 : 1);
        phase = GIVE;
        say(names[decl] + " бере прикуп (ставка " + contract + ")");
    }

    // ---------- віддача карт ----------
    public boolean canGive(int p, Card c, int to) {
        return phase == GIVE && p == decl && has(p, c) && to >= 0 && to < n && to != decl && giveNeed[to] > 0;
    }

    public String give(int p, Card c, int to) {
        if (!canGive(p, c, to)) return "Так віддати не можна";
        ArrayList<Card> h = hands.get(p);
        Card k = h.remove(indexOf(h, c));
        hands.get(to).add(k);
        giveNeed[to]--;
        boolean done = true;
        for (int i = 0; i < n; i++) if (giveNeed[i] > 0) done = false;
        say(names[p] + " віддає карту " + names[to]);
        if (done) {
            phase = FINAL;
            say(names[decl] + " визначає остаточну ставку");
        }
        return null;
    }

    // ---------- остаточна ставка ----------
    public int finalMin() {
        int m = bid;
        if (decl >= 0 && onBarrel[decl] && m < BARREL_MIN_BID) m = BARREL_MIN_BID;
        return m;
    }

    public int finalMax() {
        int mx = 120;
        if (decl >= 0) for (int s : marriagesOf(decl)) mx += MAR[s];
        return Math.max(mx, finalMin());
    }

    public String finalBid(int p, int amount) {
        if (phase != FINAL || p != decl) return "Зараз не ваша черга";
        if (amount % STEP != 0 || amount < finalMin() || amount > finalMax()) return "Некоректна ставка";
        contract = amount;
        phase = PLAY;
        leader = decl;
        say(names[decl] + " оголошує " + amount);
        return null;
    }

    // ---------- розіграш ----------
    // null, якщо картою ходити можна (за правилами масті й козиря); інакше причина
    private String illegal(int p, Card c) {
        if (trick.isEmpty()) return null;
        int ls = trick.get(0).c;
        ArrayList<Card> h = hands.get(p);
        boolean hasLed = false;
        for (Card k : h) if (k.c == ls) hasLed = true;
        if (hasLed) {
            if (c.c != ls) return "Треба ходити в масть";
            int hi = -1;
            for (Card k : trick) if (k.c == ls && points(k) > hi) hi = points(k);
            boolean canBeat = false;
            for (Card k : h) if (k.c == ls && points(k) > hi) canBeat = true;
            if (canBeat && points(c) <= hi) return "Треба бити старшою";
            return null;
        }
        if (trump >= 0) {
            boolean hasT = false;
            for (Card k : h) if (k.c == trump) hasT = true;
            if (hasT && c.c != trump) return "Треба класти козир";
        }
        return null;
    }

    public boolean legalCard(int p, Card c) {
        if (phase == PLAY) return p == cur() && has(p, c) && illegal(p, c) == null;
        if (phase == GIVE) return p == decl && has(p, c);
        return false;
    }

    public boolean canMarriage(int p, Card c) {
        if (phase != PLAY || p != cur() || !trick.isEmpty() || !has(p, c)) return false;
        if (c.v != 13 && c.v != 12) return false;
        if (marOwner[c.c] >= 0) return false;
        if (!hasRank(p, c.c, c.v == 13 ? 12 : 13)) return false;
        return !marAfter || tricksWon[p] > 0;
    }

    public String play(int p, Card c, boolean mar) {
        if (phase != PLAY || p != cur()) return "Зараз не ваш хід";
        ArrayList<Card> h = hands.get(p);
        int idx = indexOf(h, c);
        if (idx < 0) return "Немає такої карти";
        String r = illegal(p, c);
        if (r != null) return r;
        if (mar && !canMarriage(p, c)) return "Мар'яж зараз неможливий";
        Card k = h.remove(idx);
        trick.add(k);
        trickBy.add(p);
        if (mar) {
            marPts[p] += MAR[k.c];
            marOwner[k.c] = p;
            trump = k.c;
            say(names[p] + ": мар'яж " + "♠♥♦♣".charAt(k.c) + " (+" + MAR[k.c] + "), " + cs(k));
        } else {
            say(names[p] + ": " + cs(k));
        }
        if (trick.size() == n) endTrick();
        return null;
    }

    // чи b (черговий) бʼє a (поточну найсильнішу)
    private boolean beats(Card a, Card b) {
        boolean at = trump >= 0 && a.c == trump, bt = trump >= 0 && b.c == trump;
        if (at && !bt) return false;
        if (bt && !at) return true;
        if (a.c == b.c) return points(b) > points(a);
        return false;
    }

    private void endTrick() {
        int w = 0;
        for (int k = 1; k < trick.size(); k++) if (beats(trick.get(w), trick.get(k))) w = k;
        int who = trickBy.get(w);
        int sum = 0;
        for (Card k : trick) sum += points(k);
        cardPts[who] += sum;
        tricksWon[who]++;
        tricksPlayed++;
        lastTrick.clear();
        lastTrick.addAll(trick);
        lastTrickBy.clear();
        lastTrickBy.addAll(trickBy);
        lastWinner = who;
        trickSeq++;
        trick.clear();
        trickBy.clear();
        leader = who;
        say(names[who] + " бере взятку (+" + sum + ")");
        if (hands.get(0).isEmpty()) endRound();
    }

    // ---------- підрахунок ----------
    private void endRound() {
        phase = END;
        int[] d = new int[n], fl = new int[n];
        for (int i = 0; i < n; i++) raw[i] = cardPts[i] + marPts[i];
        boolean ok = raw[decl] >= contract;
        for (int i = 0; i < n; i++) {
            int old = total[i];
            if (i == decl) fl[i] |= F_DECL;
            if (onBarrel[i]) {
                fl[i] |= F_BARREL;
                if (i == decl) {
                    if (ok) {
                        total[i] = WIN;
                    } else {
                        total[i] = BARREL_DROP;
                        onBarrel[i] = false;
                        barrelRnd[i] = 0;
                        fl[i] |= F_FAIL | F_DROP;
                    }
                } else {
                    barrelRnd[i]++;
                    if (barrelRnd[i] >= BARREL_ROUNDS) {
                        total[i] = BARREL_DROP;
                        onBarrel[i] = false;
                        barrelRnd[i] = 0;
                        fl[i] |= F_DROP;
                    }
                }
            } else {
                int x = i == decl ? (ok ? contract : -contract) : round5(raw[i]);
                if (i == decl && !ok) fl[i] |= F_FAIL;
                if (boltsOn) {
                    if (x == 0) {
                        boltCnt[i]++;
                        fl[i] |= F_BOLT;
                        if (boltCnt[i] >= 3) {
                            x -= 100;
                            boltCnt[i] = 0;
                            fl[i] |= F_BOLT_PEN;
                        }
                    } else {
                        boltCnt[i] = 0;
                    }
                }
                total[i] += x;
                if (barrelOn && total[i] >= BARREL_AT && total[i] < WIN) {
                    total[i] = BARREL_AT;
                    onBarrel[i] = true;
                    barrelRnd[i] = 0;
                    fl[i] |= F_BARREL;
                }
            }
            d[i] = total[i] - old;
        }
        rounds.add(d);
        flags.add(fl);
        say(names[decl] + (ok ? " виконав ставку " : " не виконав ставку ") + contract);
        int w = -1;
        if (total[decl] >= WIN) {
            w = decl;
        } else {
            for (int i = 0; i < n; i++) if (total[i] >= WIN && (w < 0 || total[i] > total[w])) w = i;
        }
        if (w >= 0) {
            matchOver = true;
            winner = w;
            say("Перемога: " + names[w]);
        }
    }

    // ---------- місця гравців (лише між партіями) ----------
    private int sw(int x, int i, int j) {
        return x == i ? j : (x == j ? i : x);
    }

    private static void swI(int[] a, int i, int j) {
        int t = a[i];
        a[i] = a[j];
        a[j] = t;
    }

    public String swap(int i, int j) {
        if (phase != END) return "Змінювати місця можна між партіями";
        if (i == j || i < 0 || j < 0 || i >= n || j >= n) return "Далі нікуди";
        Collections.swap(hands, i, j);
        String t = names[i];
        names[i] = names[j];
        names[j] = t;
        swI(total, i, j);
        swI(boltCnt, i, j);
        swI(barrelRnd, i, j);
        swI(tricksWon, i, j);
        swI(cardPts, i, j);
        swI(marPts, i, j);
        swI(raw, i, j);
        swI(giveNeed, i, j);
        boolean b = passed[i];
        passed[i] = passed[j];
        passed[j] = b;
        b = onBarrel[i];
        onBarrel[i] = onBarrel[j];
        onBarrel[j] = b;
        for (int[] r : rounds) swI(r, i, j);
        for (int[] r : flags) swI(r, i, j);
        dealer = sw(dealer, i, j);
        decl = sw(decl, i, j);
        bidder = sw(bidder, i, j);
        leader = sw(leader, i, j);
        turn = sw(turn, i, j);
        winner = sw(winner, i, j);
        lastWinner = sw(lastWinner, i, j);
        for (int s = 0; s < 4; s++) marOwner[s] = sw(marOwner[s], i, j);
        for (int k = 0; k < lastTrickBy.size(); k++) lastTrickBy.set(k, sw(lastTrickBy.get(k), i, j));
        return null;
    }

    // ---------- збереження ----------
    private static String encC(ArrayList<Card> l) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < l.size(); i++) {
            if (i > 0) sb.append(',');
            sb.append(l.get(i).enc());
        }
        return sb.toString();
    }

    private static void decC(String s, ArrayList<Card> out) {
        out.clear();
        if (s.length() == 0) return;
        for (String t : s.split(",")) out.add(Card.dec(t));
    }

    private static String encI(int[] a) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < a.length; i++) {
            if (i > 0) sb.append(',');
            sb.append(a[i]);
        }
        return sb.toString();
    }

    private static String encL(ArrayList<Integer> l) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < l.size(); i++) {
            if (i > 0) sb.append(',');
            sb.append(l.get(i));
        }
        return sb.toString();
    }

    private static int[] decI(String s) {
        if (s.length() == 0) return new int[0];
        String[] p = s.split(",");
        int[] r = new int[p.length];
        for (int i = 0; i < p.length; i++) r[i] = Integer.parseInt(p[i].trim());
        return r;
    }

    private static String encB(boolean[] a) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < a.length; i++) {
            if (i > 0) sb.append(',');
            sb.append(a[i] ? 1 : 0);
        }
        return sb.toString();
    }

    private static String encR(ArrayList<int[]> l) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < l.size(); i++) {
            if (i > 0) sb.append(';');
            sb.append(encI(l.get(i)));
        }
        return sb.toString();
    }

    private static void decR(String s, ArrayList<int[]> out) {
        out.clear();
        if (s.length() == 0) return;
        for (String t : s.split(";", -1)) out.add(decI(t));
    }

    public String save() {
        String R = "\u001e";
        StringBuilder sb = new StringBuilder();
        int[] head = {n, dealer, phase, roundNo, turn, bid, bidder, decl, contract, trump, leader, tricksPlayed, trickSeq,
            lastWinner, matchOver ? 1 : 0, winner, marAfter ? 1 : 0, barrelOn ? 1 : 0, boltsOn ? 1 : 0};
        sb.append(encI(head)).append(R);
        for (int i = 0; i < n; i++) sb.append(i > 0 ? "\t" : "").append(names[i]);
        sb.append(R);
        for (int i = 0; i < n; i++) sb.append(i > 0 ? ";" : "").append(encC(hands.get(i)));
        sb.append(R).append(encC(talon)).append(R).append(encB(passed)).append(R).append(encI(giveNeed)).append(R)
            .append(encI(tricksWon)).append(R).append(encI(cardPts)).append(R).append(encI(marPts)).append(R).append(encI(raw)).append(R)
            .append(encI(marOwner)).append(R).append(encC(trick)).append(R).append(encL(trickBy)).append(R)
            .append(encC(lastTrick)).append(R).append(encL(lastTrickBy)).append(R)
            .append(encI(total)).append(R).append(encI(boltCnt)).append(R).append(encI(barrelRnd)).append(R).append(encB(onBarrel)).append(R)
            .append(encR(rounds)).append(R).append(encR(flags)).append(R)
            .append(msg.replace("\u001e", " ").replace("\n", " "));
        return sb.toString();
    }

    public static TEngine restore(String d, Random rnd) {
        String[] s = d.split("\u001e", -1);
        int[] h = decI(s[0]);
        TEngine e = new TEngine(rnd, h[16] == 1, h[17] == 1, h[18] == 1);
        e.n = h[0];
        e.dealer = h[1];
        e.phase = h[2];
        e.roundNo = h[3];
        e.turn = h[4];
        e.bid = h[5];
        e.bidder = h[6];
        e.decl = h[7];
        e.contract = h[8];
        e.trump = h[9];
        e.leader = h[10];
        e.tricksPlayed = h[11];
        e.trickSeq = h[12];
        e.lastWinner = h[13];
        e.matchOver = h[14] == 1;
        e.winner = h[15];
        e.names = s[1].split("\t", -1);
        if (e.names.length != e.n) throw new RuntimeException("names");
        String[] hs = s[2].split(";", -1);
        for (int i = 0; i < e.n; i++) {
            ArrayList<Card> l = new ArrayList<Card>();
            decC(hs[i], l);
            e.hands.add(l);
        }
        decC(s[3], e.talon);
        int[] ps = decI(s[4]);
        e.passed = new boolean[e.n];
        for (int i = 0; i < e.n; i++) e.passed[i] = ps[i] == 1;
        e.giveNeed = decI(s[5]);
        e.tricksWon = decI(s[6]);
        e.cardPts = decI(s[7]);
        e.marPts = decI(s[8]);
        e.raw = decI(s[9]);
        e.marOwner = decI(s[10]);
        decC(s[11], e.trick);
        for (int v : decI(s[12])) e.trickBy.add(v);
        decC(s[13], e.lastTrick);
        for (int v : decI(s[14])) e.lastTrickBy.add(v);
        e.total = decI(s[15]);
        e.boltCnt = decI(s[16]);
        e.barrelRnd = decI(s[17]);
        int[] ob = decI(s[18]);
        e.onBarrel = new boolean[e.n];
        for (int i = 0; i < e.n; i++) e.onBarrel[i] = ob[i] == 1;
        decR(s[19], e.rounds);
        decR(s[20], e.flags);
        e.msg = s[21];
        return e;
    }
}
