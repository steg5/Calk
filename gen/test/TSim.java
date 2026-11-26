import com.example.gryntsivgames.thousand.TEngine;
import com.example.gryntsivgames.uno.Card;

import java.util.ArrayList;
import java.util.Random;

// Тест-симулятор рушія «1000»: випадкові матчі, незалежний еталон правил, інваріанти, підрахунок, save/restore
public class TSim {
    static int errors = 0;
    static int hangs = 0;
    static int[] errKinds = new int[16];
    static final String[] KIND = {"ходи (еталон)", "мар'яж (еталон)", "карти", "очки партії", "підрахунок матчу", "save/restore",
        "переможець взятки", "мар'яж-очки", "swap", "рука/прикуп", "інше", "", "", "", "", ""};
    // статистика
    static int unfinished, matches, rounds, barrelIn, barrelDrop, barrelWin, bolts, boltPen, fails, allPass, marriages;
    static int[] marBySuit = new int[4];
    static int[] byN = new int[5];

    static void err(int kind, String s) {
        errors++;
        errKinds[kind]++;
        if (errors <= 25) System.out.println("ПОМИЛКА [" + KIND[kind] + "]: " + s);
    }

    // ---------- незалежний еталон ----------
    // порядок рангів у грі: 9 < В < Д < К < 10 < Т
    static int rk(int v) {
        switch (v) {
            case 9: return 0;
            case 11: return 1;
            case 12: return 2;
            case 13: return 3;
            case 10: return 4;
            default: return 5;
        }
    }

    static int pts(int v) {
        switch (v) {
            case 14: return 11;
            case 10: return 10;
            case 13: return 4;
            case 12: return 3;
            case 11: return 2;
            default: return 0;
        }
    }

    static boolean refLegal(ArrayList<Card> hand, ArrayList<Card> trick, int trump, Card c) {
        if (trick.isEmpty()) return true;
        int ls = trick.get(0).c;
        boolean hasLed = false;
        for (Card k : hand) if (k.c == ls) hasLed = true;
        if (hasLed) {
            if (c.c != ls) return false;
            // найстарша карта ведучої масті на столі (козир іншої масті не рахується для обов'язку бити мастю)
            int hi = -1;
            for (Card k : trick) if (k.c == ls && rk(k.v) > hi) hi = rk(k.v);
            boolean canBeat = false;
            for (Card k : hand) if (k.c == ls && rk(k.v) > hi) canBeat = true;
            if (canBeat && rk(c.v) <= hi) return false;
            return true;
        }
        if (trump >= 0) {
            boolean hasT = false;
            for (Card k : hand) if (k.c == trump) hasT = true;
            if (hasT && c.c != trump) return false;
        }
        return true;
    }

    static boolean has(ArrayList<Card> h, int s, int v) {
        for (Card k : h) if (k.c == s && k.v == v) return true;
        return false;
    }

    static boolean refMarriage(ArrayList<Card> hand, ArrayList<Card> trick, boolean[] marDone, boolean marAfter, int tricksWon, Card c) {
        if (!trick.isEmpty()) return false;
        if (c.v != 13 && c.v != 12) return false;
        if (marDone[c.c]) return false;
        if (!has(hand, c.c, c.v == 13 ? 12 : 13)) return false;
        if (marAfter && tricksWon == 0) return false;
        return true;
    }

    // переможець взятки за еталоном: індекс у списку ходів
    static int refWinner(ArrayList<Card> trick, int trump) {
        int w = 0;
        for (int k = 1; k < trick.size(); k++) {
            Card a = trick.get(w), b = trick.get(k);
            boolean at = trump >= 0 && a.c == trump, bt = trump >= 0 && b.c == trump;
            if (bt && !at) w = k;
            else if (!(at && !bt) && a.c == b.c && rk(b.v) > rk(a.v)) w = k;
        }
        return w;
    }

    static int round5(int x) {
        return (x + 2) / 5 * 5;
    }

    // ---------- дзеркало підрахунку матчу ----------
    static class Mirror {
        int n;
        int[] total, bolt, brRnd;
        boolean[] onB;
        boolean barrelOn, boltsOn;

        Mirror(TEngine e) {
            n = e.n;
            total = e.total.clone();
            bolt = new int[n];
            brRnd = new int[n];
            onB = new boolean[n];
            barrelOn = e.barrelOn;
            boltsOn = e.boltsOn;
        }

        // повертає: [0] виграв хтось (індекс або -1)
        int round(int[] raw, int decl, int contract) {
            boolean ok = raw[decl] >= contract;
            for (int i = 0; i < n; i++) {
                if (onB[i]) {
                    if (i == decl) {
                        if (ok) total[i] = 1000;
                        else {
                            total[i] = 760;
                            onB[i] = false;
                            brRnd[i] = 0;
                            barrelDrop++;
                        }
                    } else {
                        brRnd[i]++;
                        if (brRnd[i] >= 3) {
                            total[i] = 760;
                            onB[i] = false;
                            brRnd[i] = 0;
                            barrelDrop++;
                        }
                    }
                } else {
                    int x = i == decl ? (ok ? contract : -contract) : round5(raw[i]);
                    if (boltsOn) {
                        if (x == 0) {
                            bolt[i]++;
                            bolts++;
                            if (bolt[i] >= 3) {
                                x -= 100;
                                bolt[i] = 0;
                                boltPen++;
                            }
                        } else bolt[i] = 0;
                    }
                    total[i] += x;
                    if (barrelOn && total[i] >= 880 && total[i] < 1000) {
                        total[i] = 880;
                        onB[i] = true;
                        brRnd[i] = 0;
                        barrelIn++;
                    }
                }
            }
            if (total[decl] >= 1000) return decl;
            int w = -1;
            for (int i = 0; i < n; i++) if (total[i] >= 1000 && (w < 0 || total[i] > total[w])) w = i;
            return w;
        }
    }

    // ---------- перевірки стану ----------
    static void checkCards(TEngine e, String where) {
        boolean[][] seen = new boolean[4][15];
        int cnt = 0;
        boolean afterTalon = e.phase != TEngine.BID;
        for (int i = 0; i < e.n; i++) {
            for (Card k : e.hands.get(i)) {
                if (seen[k.c][k.v]) err(2, where + ": дубль карти " + TEngine.cs(k));
                seen[k.c][k.v] = true;
                cnt++;
            }
        }
        for (Card k : e.trick) {
            if (seen[k.c][k.v]) err(2, where + ": карта на столі є ще й у руці " + TEngine.cs(k));
            seen[k.c][k.v] = true;
            cnt++;
        }
        if (!afterTalon) {
            for (Card k : e.talon) {
                if (seen[k.c][k.v]) err(2, where + ": прикуп дублює карту " + TEngine.cs(k));
                seen[k.c][k.v] = true;
                cnt++;
            }
            if (cnt != 24) err(2, where + ": у торгах " + cnt + " карт замість 24");
        } else if (e.phase != TEngine.END) {
            int expect = 24 - e.tricksPlayed * e.n;
            if (cnt != expect) err(2, where + ": карт " + cnt + ", очікувалось " + expect);
        }
        if (e.phase == TEngine.BID) {
            int per = e.n == 2 ? 10 : (e.n == 3 ? 7 : 5), tal = e.n == 3 ? 3 : 4;
            for (int i = 0; i < e.n; i++) if (e.hands.get(i).size() != per) err(9, where + ": у гравця " + i + " " + e.hands.get(i).size() + " карт, мало бути " + per);
            if (e.talon.size() != tal) err(9, where + ": прикуп " + e.talon.size() + ", мало бути " + tal);
        }
        if (e.phase == TEngine.PLAY && e.tricksPlayed == 0 && e.trick.isEmpty()) {
            int per = e.n == 2 ? 12 : (e.n == 3 ? 8 : 6);
            for (int i = 0; i < e.n; i++) if (e.hands.get(i).size() != per) err(9, where + ": після віддачі у гравця " + i + " " + e.hands.get(i).size() + " карт, мало бути " + per);
        }
    }

    static TEngine roundTrip(TEngine e, Random r, String where) {
        String s = e.save();
        TEngine e2;
        try {
            e2 = TEngine.restore(s, new Random(r.nextLong()));
        } catch (Exception ex) {
            err(5, where + ": restore кинув " + ex);
            return e;
        }
        String s2 = e2.save();
        if (!s.equals(s2)) err(5, where + ": save() після restore() відрізняється");
        if (e2.cur() != e.cur() || e2.phase != e.phase || e2.trump != e.trump || e2.n != e.n) err(5, where + ": стан не збігається після restore");
        for (int i = 0; i < e.n; i++) {
            if (e2.total[i] != e.total[i] || e2.onBarrel[i] != e.onBarrel[i] || e2.boltCnt[i] != e.boltCnt[i]) err(5, where + ": рахунок не збігається після restore");
            if (e2.hands.get(i).size() != e.hands.get(i).size()) err(5, where + ": руки не збігаються після restore");
        }
        return e2;
    }

    // ---------- один матч ----------
    static void playMatch(long seed, int mi) {
        Random r = new Random(seed);
        int n = 2 + r.nextInt(3);
        boolean pureRandom = mi % 5 == 0;
        boolean marAfter = r.nextBoolean(), barrelOn = r.nextInt(4) != 0, boltsOn = r.nextInt(4) != 0;
        String[] names = new String[n];
        for (int i = 0; i < n; i++) names[i] = "Г" + i;
        TEngine e = new TEngine(names, marAfter, barrelOn, boltsOn, -1, new Random(r.nextLong()));
        if (mi % 7 == 0) {
            for (int i = 0; i < n; i++) e.total[i] = 800 + r.nextInt(8) * 10;
            for (int i = 0; i < n; i++) if (barrelOn && e.total[i] >= 880) {
                e.total[i] = 870;
            }
        }
        Mirror m = new Mirror(e);
        byN[n]++;
        double pRaise = pureRandom ? 0.4 : 0.6, pMar = pureRandom ? 0.5 : 0.75;
        int steps = 0, stepsRound = 0, roundsHere = 0;
        boolean[] marDone = new boolean[4];
        int refTrump = -1;
        int[] tw = new int[n];
        int lastSeq = e.trickSeq;
        ArrayList<Card> trickCards = new ArrayList<Card>();
        ArrayList<Integer> trickWho = new ArrayList<Integer>();
        int[] marMirror = new int[n];
        while (!e.matchOver) {
            if (++stepsRound > 300) {
                hangs++;
                System.out.println("ЗАВИСАННЯ: матч " + mi + " seed " + seed + " фаза " + e.phase);
                return;
            }
            steps++;
            String w = "матч " + mi + " крок " + steps;
            if (e.phase == TEngine.END) {
                // перевірка підрахунку партії
                int sum = 0;
                for (int i = 0; i < n; i++) {
                    sum += e.cardPts[i];
                    if (e.raw[i] != e.cardPts[i] + e.marPts[i]) err(3, w + ": raw != карти + мар'яжі");
                    if (e.marPts[i] != marMirror[i]) err(7, w + ": очки мар'яжів гравця " + i + " " + e.marPts[i] + " очікувалось " + marMirror[i]);
                }
                if (sum != 120) err(3, w + ": сума очок карт " + sum + " замість 120");
                rounds++;
                stepsRound = 0;
                if (++roundsHere >= 80) { unfinished++; return; }
                boolean wasMatchOver = e.matchOver;
                int mw = m.round(e.raw, e.decl, e.contract);
                for (int i = 0; i < n; i++) if (e.total[i] != m.total[i]) err(4, w + ": рахунок гравця " + i + " " + e.total[i] + " очікувалось " + m.total[i]);
                for (int i = 0; i < n; i++) {
                    if (e.onBarrel[i] != m.onB[i]) err(4, w + ": бочка гравця " + i + " рушій " + e.onBarrel[i] + " очікувалось " + m.onB[i]);
                    if (e.boltCnt[i] != m.bolt[i]) err(4, w + ": болти гравця " + i + " рушій " + e.boltCnt[i] + " очікувалось " + m.bolt[i]);
                }
                if (e.raw[e.decl] < e.contract) fails++;
                if (mw >= 0 && !e.matchOver) err(4, w + ": хтось набрав 1000, а матч не закінчено");
                if (mw < 0 && e.matchOver) err(4, w + ": матч закінчено без переможця");
                if (mw >= 0 && e.matchOver && e.winner != mw) err(4, w + ": переможець " + e.winner + ", очікувався " + mw);
                if (e.matchOver) {
                    if (e.onBarrel[e.winner] || true) {
                        if (m.total[e.winner] >= 1000 && e.decl == e.winner) { /* ок */ }
                    }
                    break;
                }
                // випадково перевіримо збереження між партіями та зміну місць
                if (r.nextInt(3) == 0) e = roundTrip(e, r, w);
                if (n > 1 && r.nextInt(6) == 0) {
                    int a = r.nextInt(n), b = r.nextInt(n);
                    if (a != b) {
                        int ta = e.total[a], tb = e.total[b];
                        String res = e.swap(a, b);
                        if (res != null) err(8, w + ": swap відмовив: " + res);
                        else {
                            if (e.total[a] != tb || e.total[b] != ta) err(8, w + ": swap не поміняв рахунок");
                            int t = m.total[a]; m.total[a] = m.total[b]; m.total[b] = t;
                            t = m.bolt[a]; m.bolt[a] = m.bolt[b]; m.bolt[b] = t;
                            t = m.brRnd[a]; m.brRnd[a] = m.brRnd[b]; m.brRnd[b] = t;
                            boolean bb = m.onB[a]; m.onB[a] = m.onB[b]; m.onB[b] = bb;
                        }
                    }
                }
                String res = e.nextRound();
                if (res != null) err(10, w + ": nextRound: " + res);
                marDone = new boolean[4];
                refTrump = -1;
                tw = new int[n];
                marMirror = new int[n];
                trickCards.clear();
                trickWho.clear();
                lastSeq = e.trickSeq;
                continue;
            }
            int p = e.cur();
            if (p < 0 || p >= n) {
                err(10, w + ": cur() = " + p + " у фазі " + e.phase);
                return;
            }
            checkCards(e, w);
            if (e.phase == TEngine.BID) {
                boolean canB = e.canBid(p);
                double x = r.nextDouble();
                if (canB && x < pRaise) {
                    int amt = e.minBid() + 5 * r.nextInt(pureRandom ? 20 : 6);
                    if (amt > TEngine.MAX_BID) amt = e.minBid();
                    String res = e.bid(p, amt);
                    if (res != null) err(10, w + ": bid(" + amt + "): " + res);
                } else {
                    if (!e.canPassBid(p)) err(10, w + ": canPassBid = false у свою чергу");
                    boolean noBid = e.bidder < 0;
                    String res = e.passBid(p);
                    if (res != null) err(10, w + ": passBid: " + res);
                    if (e.phase != TEngine.BID && noBid && e.bid > 0 && e.decl >= 0 && e.contract == 100 || (e.phase != TEngine.BID && e.contract == 120 && e.decl >= 0 && e.onBarrel[e.decl])) {
                        // усі спасували
                        if (e.phase == TEngine.GIVE) allPass++;
                    }
                }
            } else if (e.phase == TEngine.GIVE) {
                ArrayList<Card> h = e.hands.get(p);
                int to = -1;
                for (int i = 0; i < n; i++) if (i != p && e.giveNeed[i] > 0) {
                    to = i;
                    break;
                }
                Card c = h.get(r.nextInt(h.size()));
                String res = e.give(p, c, to);
                if (res != null) err(10, w + ": give: " + res);
            } else if (e.phase == TEngine.FINAL) {
                int lo = e.finalMin(), hi = e.finalMax();
                // еталон максимальної ставки: 120 + мар'яжі руки господаря
                int mx = 120;
                int[] MAR = {40, 100, 80, 60};
                for (int s = 0; s < 4; s++) if (has(e.hands.get(p), s, 13) && has(e.hands.get(p), s, 12)) mx += MAR[s];
                if (hi != Math.max(mx, lo)) err(10, w + ": finalMax " + hi + ", еталон " + Math.max(mx, lo));
                int steps5 = (hi - lo) / 5;
                int amt = lo + 5 * (pureRandom ? r.nextInt(steps5 + 1) : (r.nextInt(3) == 0 ? steps5 : r.nextInt(Math.min(4, steps5 + 1))));
                if (e.onBarrel[p] && amt < 120) err(4, w + ": на бочці остаточна ставка " + amt + " < 120");
                String res = e.finalBid(p, amt);
                if (res != null) err(10, w + ": finalBid(" + amt + "): " + res);
            } else if (e.phase == TEngine.PLAY) {
                ArrayList<Card> h = e.hands.get(p);
                ArrayList<Card> legal = new ArrayList<Card>();
                for (Card c : h) {
                    boolean rl = refLegal(h, e.trick, refTrump, c);
                    boolean el = e.legalCard(p, c);
                    if (rl != el) err(0, w + ": гравець " + p + " карта " + TEngine.cs(c) + " еталон " + rl + " рушій " + el
                        + " стіл " + cards(e.trick) + " рука " + cards(h) + " козир " + refTrump);
                    if (rl) legal.add(c);
                    boolean rm = refMarriage(h, e.trick, marDone, marAfter, tw[p], c);
                    boolean em = e.canMarriage(p, c);
                    if (rm != em) err(1, w + ": гравець " + p + " карта " + TEngine.cs(c) + " мар'яж еталон " + rm + " рушій " + em
                        + " рука " + cards(h) + " узято взяток " + tw[p]);
                }
                if (legal.isEmpty()) {
                    err(0, w + ": немає жодної дозволеної карти, рука " + cards(h) + " стіл " + cards(e.trick));
                    return;
                }
                Card pick = null;
                boolean mar = false;
                if (r.nextDouble() < pMar) {
                    for (Card c : legal) if (refMarriage(h, e.trick, marDone, marAfter, tw[p], c)) {
                        pick = c;
                        mar = true;
                        if (r.nextBoolean()) break;
                    }
                }
                if (pick == null) pick = legal.get(r.nextInt(legal.size()));
                Card played = new Card(pick.c, pick.v);
                String res = e.play(p, pick, mar);
                if (res != null) {
                    err(10, w + ": play: " + res);
                    return;
                }
                if (mar) {
                    marDone[played.c] = true;
                    refTrump = played.c;
                    int[] MAR = {40, 100, 80, 60};
                    marMirror[p] += MAR[played.c];
                    marriages++;
                    marBySuit[played.c]++;
                    if (e.trump != refTrump) err(7, w + ": козир " + e.trump + ", очікувався " + refTrump);
                }
                trickCards.add(played);
                trickWho.add(p);
                if (e.trickSeq != lastSeq) {
                    lastSeq = e.trickSeq;
                    int wi = refWinner(trickCards, refTrump);
                    int wp = trickWho.get(wi);
                    if (e.lastWinner != wp) err(6, w + ": взятку взяв " + e.lastWinner + ", еталон " + wp + " стіл " + cards(trickCards) + " козир " + refTrump);
                    tw[wp]++;
                    trickCards.clear();
                    trickWho.clear();
                }
            } else {
                err(10, w + ": невідома фаза " + e.phase);
                return;
            }
            if (r.nextInt(25) == 0) e = roundTrip(e, r, w);
        }
        matches++;
    }

    static String cards(ArrayList<Card> l) {
        StringBuilder sb = new StringBuilder();
        for (Card k : l) sb.append(TEngine.cs(k)).append(' ');
        return sb.toString().trim();
    }

    public static void main(String[] a) {
        try { System.setOut(new java.io.PrintStream(System.out, true, "UTF-8")); } catch (Exception ex) { }
        int games = a.length > 0 ? Integer.parseInt(a[0]) : 3000;
        for (int i = 0; i < games; i++) {
            try {
                playMatch(1000L + i, i);
            } catch (Throwable t) {
                err(10, "матч " + i + ": виняток " + t);
                if (errors < 5) t.printStackTrace(System.out);
            }
        }
        System.out.println("матчів " + matches + " (2 гравці " + byN[2] + ", 3 гравці " + byN[3] + ", 4 гравці " + byN[4] + "), партій " + rounds
            + ", середньо " + (matches == 0 ? 0 : rounds / matches) + " партій у матчі");
        System.out.println("недограно (ліміт 80 партій) " + unfinished + ", зависань " + hangs + ", помилок " + errors);
        System.out.println("бочка: входів " + barrelIn + ", злетів " + barrelDrop + "; болтів " + bolts + ", штрафів за 3 болти " + boltPen
            + "; невиконаних ставок " + fails + "; мар'яжів " + marriages + " (♠" + marBySuit[0] + " ♥" + marBySuit[1] + " ♦" + marBySuit[2] + " ♣" + marBySuit[3] + ")");
        if (errors > 0) {
            StringBuilder sb = new StringBuilder("помилки за типами:");
            for (int i = 0; i < errKinds.length; i++) if (errKinds[i] > 0) sb.append(" ").append(KIND[i]).append("=").append(errKinds[i]);
            System.out.println(sb);
            System.exit(1);
        }
        System.out.println("OK");
    }
}
