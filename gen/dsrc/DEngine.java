package com.example.gryntsivgames.durak;

import com.example.gryntsivgames.uno.Card;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Random;

// Правила Дурака (36 карт). Масті c: 0 піки, 1 чирви, 2 бубни, 3 хрести. Ранг v: 6..14 (14 = туз)
public class DEngine {
    public static final int MAXT = 6;

    public final int n;
    public final String[] names;
    public final ArrayList<Card> deck = new ArrayList<Card>();
    public final ArrayList<ArrayList<Card>> hands = new ArrayList<ArrayList<Card>>();
    public final boolean[] out;
    public final boolean[] passed;
    public final ArrayList<Card> atk = new ArrayList<Card>();
    public final ArrayList<Card> dfn = new ArrayList<Card>();
    public Card trumpCard;
    public int ts;
    public int att, def;
    public boolean taking, over, transferOn;
    public int fool = -1;
    public int limit;
    public String msg = "";
    public int startedBy;
    public int bouts;
    public boolean ordered;
    public int thrower;
    public final ArrayList<String> log = new ArrayList<String>();
    public final ArrayList<Card> gAtk = new ArrayList<Card>();
    public final ArrayList<Card> gDfn = new ArrayList<Card>();
    public int gSeq;
    public final ArrayList<Integer> own = new ArrayList<Integer>();
    public final ArrayList<Integer> gOwn = new ArrayList<Integer>();

    public void setOrdered(boolean on) {
        ordered = on;
        thrower = firstThrower();
    }

    private int firstThrower() {
        for (int k = 0; k < n; k++) {
            int p = (att + k) % n;
            if (p != def && !out[p]) return p;
        }
        return att;
    }

    public DEngine(String[] names, int first, int fallback, boolean transfer, Random rnd) {
        this.n = names.length;
        this.names = names;
        this.transferOn = transfer;
        out = new boolean[n];
        passed = new boolean[n];
        for (int s = 0; s < 4; s++) {
            for (int r = 6; r <= 14; r++) deck.add(new Card(s, r));
        }
        Collections.shuffle(deck, rnd);
        for (int i = 0; i < n; i++) hands.add(new ArrayList<Card>());
        for (int k = 0; k < 6; k++) {
            for (int i = 0; i < n; i++) hands.get(i).add(deck.remove(0));
        }
        if (deck.isEmpty()) {
            ArrayList<Card> lh = hands.get(n - 1);
            trumpCard = lh.get(lh.size() - 1);
        } else {
            trumpCard = deck.get(deck.size() - 1);
        }
        ts = trumpCard.c;
        if (first < 0) {
            int best = -1, bv = 99;
            for (int i = 0; i < n; i++) {
                for (Card c : hands.get(i)) {
                    if (c.c == ts && c.v < bv) {
                        bv = c.v;
                        best = i;
                    }
                }
            }
            first = best >= 0 ? best : fallback;
        }
        if (first < 0 || first >= n) first = 0;
        startedBy = first;
        att = first;
        def = nextActive(att);
        limit = Math.min(MAXT, hands.get(def).size());
        msg = names[att] + " починає";
        thrower = att;
    }

    private static String encL(ArrayList<Card> l) {
        StringBuilder sb = new StringBuilder();
        for (int k = 0; k < l.size(); k++) {
            if (k > 0) sb.append(',');
            sb.append(l.get(k) == null ? "-" : l.get(k).enc());
        }
        return sb.toString();
    }

    private static void decL(String s, ArrayList<Card> out) {
        out.clear();
        if (s.length() == 0) return;
        for (String t : s.split(",")) out.add(t.equals("-") ? null : Card.dec(t));
    }

    public String save() {
        String R = "\u001e";
        StringBuilder sb = new StringBuilder();
        sb.append(n).append(',').append(ts).append(',').append(att).append(',').append(def).append(',')
            .append(taking ? 1 : 0).append(',').append(over ? 1 : 0).append(',').append(transferOn ? 1 : 0).append(',')
            .append(fool).append(',').append(limit).append(',').append(startedBy).append(',').append(bouts).append(',').append(ordered ? 1 : 0).append(',').append(thrower);
        sb.append(R).append(trumpCard.enc()).append(R).append(encL(deck)).append(R);
        for (int i = 0; i < n; i++) {
            if (i > 0) sb.append(';');
            sb.append(encL(hands.get(i)));
        }
        sb.append(R);
        for (int i = 0; i < n; i++) sb.append(i > 0 ? "," : "").append(out[i] ? 1 : 0);
        sb.append(R);
        for (int i = 0; i < n; i++) sb.append(i > 0 ? "," : "").append(passed[i] ? 1 : 0);
        sb.append(R).append(encL(atk)).append(R).append(encL(dfn)).append(R).append(msg.replace("\u001e", " ").replace("\u001f", " "));
        return sb.toString();
    }

    public static DEngine restore(String d, String[] names, Random rnd) {
        String[] sec = d.split("\u001e", -1);
        String[] m = sec[0].split(",");
        DEngine e = new DEngine(names, 0, 0, m[6].equals("1"), rnd);
        if (e.n != Integer.parseInt(m[0])) throw new RuntimeException("n");
        e.ts = Integer.parseInt(m[1]);
        e.att = Integer.parseInt(m[2]);
        e.def = Integer.parseInt(m[3]);
        e.taking = m[4].equals("1");
        e.over = m[5].equals("1");
        e.fool = Integer.parseInt(m[7]);
        e.limit = Integer.parseInt(m[8]);
        e.startedBy = Integer.parseInt(m[9]);
        e.bouts = Integer.parseInt(m[10]);
        if (m.length > 12) {
            e.ordered = m[11].equals("1");
            e.thrower = Integer.parseInt(m[12]);
        }
        e.trumpCard = Card.dec(sec[1]);
        decL(sec[2], e.deck);
        String[] hs = sec[3].split(";", -1);
        for (int i = 0; i < e.n; i++) decL(hs[i], e.hands.get(i));
        String[] o = sec[4].split(",");
        String[] ps = sec[5].split(",");
        for (int i = 0; i < e.n; i++) {
            e.out[i] = o[i].equals("1");
            e.passed[i] = ps[i].equals("1");
        }
        decL(sec[6], e.atk);
        decL(sec[7], e.dfn);
        e.msg = sec[8];
        for (int i = 0; i < e.atk.size(); i++) e.own.add(e.att);
        return e;
    }

    public int nextActive(int i) {
        for (int k = 1; k <= n; k++) {
            int j = (i + k) % n;
            if (!out[j]) return j;
        }
        return i;
    }

    public int active() {
        int c = 0;
        for (int i = 0; i < n; i++) if (!out[i]) c++;
        return c;
    }

    public boolean trump(Card c) {
        return c.c == ts;
    }

    public boolean beats(Card a, Card d) {
        if (d.c == a.c) return d.v > a.v;
        return d.c == ts && a.c != ts;
    }

    public int unbeaten() {
        int c = 0;
        for (Card d : dfn) if (d == null) c++;
        return c;
    }

    public boolean anyBeaten() {
        for (Card d : dfn) if (d != null) return true;
        return false;
    }

    private boolean rankOnTable(int v) {
        for (Card c : atk) if (c.v == v) return true;
        for (Card c : dfn) if (c != null && c.v == v) return true;
        return false;
    }

    public boolean has(int p, Card c) {
        for (Card k : hands.get(p)) if (k.same(c)) return true;
        return false;
    }

    private void drop(int p, Card c) {
        ArrayList<Card> h = hands.get(p);
        for (int i = 0; i < h.size(); i++) {
            if (h.get(i).same(c)) {
                h.remove(i);
                return;
            }
        }
    }

    // чи може p підкинути (або зробити першу атаку) картою c
    public boolean canThrow(int p, Card c) {
        if (over || p < 0 || p >= n || out[p] || p == def || !has(p, c)) return false;
        if (atk.isEmpty()) return p == att;
        if (ordered && p != thrower) return false;
        if (atk.size() >= limit) return false;
        if (unbeaten() >= hands.get(def).size() && !taking) {
            // у захисника не лишилось карт, щоб відбити більше
            return false;
        }
        return rankOnTable(c.v);
    }

    public boolean canThrowAny(int p) {
        if (over || out[p] || p == def) return false;
        for (Card c : hands.get(p)) if (canThrow(p, c)) return true;
        return false;
    }

    // індекс першої невідбитої атакуючої карти, яку c б'є; -1 якщо немає
    public int targetFor(Card c, int pref) {
        if (pref >= 0 && pref < atk.size() && dfn.get(pref) == null && beats(atk.get(pref), c)) return pref;
        for (int k = 0; k < atk.size(); k++) {
            if (dfn.get(k) == null && beats(atk.get(k), c)) return k;
        }
        return -1;
    }

    public boolean canDefend(int p, Card c) {
        if (over || p != def || taking || !has(p, c)) return false;
        if (hopeless(p)) return false;
        return targetFor(c, -1) >= 0;
    }

    public boolean canTransfer(int p, Card c) {
        if (over || !transferOn || p != def || taking || !has(p, c) || atk.isEmpty() || anyBeaten()) return false;
        if (c.v != atk.get(0).v) return false;
        int nd = nextActive(def);
        if (nd == def) return false;
        return hands.get(nd).size() >= atk.size() + 1;
    }

    public boolean canTransferAny(int p) {
        for (Card c : hands.get(p)) if (canTransfer(p, c)) return true;
        return false;
    }

    public int ownerOf(int i) {
        return i < own.size() ? own.get(i) : att;
    }

    // чи може захисник побити всі непобиті карти (паросполучення)
    public boolean canBeatAll(int p) {
        ArrayList<Card> h = hands.get(p);
        ArrayList<Card> need = new ArrayList<Card>();
        for (int i = 0; i < atk.size(); i++) if (dfn.get(i) == null) need.add(atk.get(i));
        if (need.size() > h.size()) return false;
        int[] match = new int[h.size()];
        for (int i = 0; i < match.length; i++) match[i] = -1;
        for (int a = 0; a < need.size(); a++) {
            if (!augment(need, h, a, match, new boolean[h.size()])) return false;
        }
        return true;
    }

    private boolean augment(ArrayList<Card> need, ArrayList<Card> h, int a, int[] match, boolean[] seen) {
        for (int j = 0; j < h.size(); j++) {
            if (seen[j] || !beats(need.get(a), h.get(j))) continue;
            seen[j] = true;
            if (match[j] < 0 || augment(need, h, match[j], match, seen)) {
                match[j] = a;
                return true;
            }
        }
        return false;
    }

    // захисник вже не відіб'ється: лишається брати
    public boolean hopeless(int p) {
        if (over || p != def || taking || atk.isEmpty() || unbeaten() == 0) return false;
        return !canBeatAll(p) && !canTransferAny(p);
    }

    public boolean canTake(int p) {
        return !over && p == def && !taking && unbeaten() > 0;
    }

    public boolean canPass(int p) {
        if (over || out[p] || p == def || atk.isEmpty() || passed[p]) return false;
        if (ordered && p != thrower) return false;
        return unbeaten() == 0 || taking;
    }

    public boolean legalCard(int p, Card c) {
        return canThrow(p, c) || canDefend(p, c) || canTransfer(p, c);
    }

    private void reset() {
        for (int i = 0; i < n; i++) passed[i] = false;
        thrower = firstThrower();
    }

    private static String cs(Card c) {
        String r = c.v <= 10 ? String.valueOf(c.v) : (c.v == 11 ? "В" : c.v == 12 ? "Д" : c.v == 13 ? "К" : "Т");
        return r + "♠♥♦♣".charAt(c.c);
    }

    public String attack(int p, Card c) {
        if (!canThrow(p, c)) return "Так ходити не можна";
        drop(p, c);
        atk.add(c);
        dfn.add(null);
        own.add(p);
        if (!ordered) reset();
        msg = names[p] + (p == att && atk.size() == 1 ? " ходить " : " підкидає ") + cs(c);
        log.add(msg);
        settle();
        return null;
    }

    public String defend(int p, Card c, int pref) {
        if (!canDefend(p, c)) return "Цією картою не відбитись";
        int k = targetFor(c, pref);
        drop(p, c);
        dfn.set(k, c);
        if (!ordered) reset();
        msg = names[p] + " б'є " + cs(atk.get(k)) + " картою " + cs(c);
        log.add(msg);
        settle();
        return null;
    }

    public String transfer(int p, Card c) {
        if (!canTransfer(p, c)) return "Перевести не можна";
        int nd = nextActive(def);
        drop(p, c);
        atk.add(c);
        dfn.add(null);
        own.add(p);
        msg = names[p] + " переводить " + cs(c) + " на " + names[nd];
        log.add(msg);
        att = p;
        def = nd;
        limit = Math.min(MAXT, hands.get(def).size());
        reset();
        settle();
        return null;
    }

    public String take(int p) {
        if (!canTake(p)) return "Зараз брати не можна";
        taking = true;
        reset();
        msg = names[p] + " бере";
        log.add(msg);
        settle();
        return null;
    }

    public String pass(int p) {
        if (!canPass(p)) return "Зараз пас неможливий";
        passed[p] = true;
        if (ordered) {
            for (int k = 1; k < n; k++) {
                int q = (p + k) % n;
                if (q != def && !out[q] && !passed[q]) {
                    thrower = q;
                    break;
                }
            }
        }
        msg = names[p] + ": бито";
        log.add(msg);
        settle();
        return null;
    }

    // перевіряє, чи закінчилась атака (раунд)
    public void settle() {
        if (over || atk.isEmpty()) return;
        if (decided()) return;
        boolean done;
        if (!taking && unbeaten() == 0 && hands.get(def).isEmpty()) {
            done = true;
        } else if (taking || unbeaten() == 0) {
            done = true;
            for (int p = 0; p < n; p++) {
                if (p == def || out[p]) continue;
                if (!passed[p]) {
                    done = false;
                    break;
                }
            }
        } else {
            done = false;
        }
        if (done) endBout();
    }

    // коли в колоді порожньо і лише один гравець має карти, результат уже відомий
    private boolean decided() {
        if (!deck.isEmpty()) return false;
        int cnt = 0, who = -1;
        for (int i = 0; i < n; i++) {
            if (!hands.get(i).isEmpty()) {
                cnt++;
                who = i;
            }
        }
        if (cnt != 1) return false;
        if (who == def && hands.get(def).size() <= unbeaten()) return false;
        over = true;
        fool = who;
        for (int i = 0; i < n; i++) if (i != who) out[i] = true;
        msg = "Дурак: " + names[fool];
        return true;
    }

    private void refill(int from) {
        for (int k = 0; k < n; k++) {
            int p = (from + k) % n;
            if (p == def || out[p]) continue;
            fill(p);
        }
        if (!out[def]) fill(def);
    }

    private void fill(int p) {
        ArrayList<Card> h = hands.get(p);
        while (h.size() < 6 && !deck.isEmpty()) h.add(deck.remove(0));
    }

    private void endBout() {
        bouts++;
        boolean took = taking;
        int d = def;
        gAtk.clear();
        gDfn.clear();
        gAtk.addAll(atk);
        gOwn.clear();
        gOwn.addAll(own);
        gDfn.addAll(dfn);
        gSeq++;
        if (took) {
            ArrayList<Card> h = hands.get(d);
            for (int k = 0; k < atk.size(); k++) {
                h.add(atk.get(k));
                if (dfn.get(k) != null) h.add(dfn.get(k));
            }
            msg = names[d] + " забрав карти";
        } else {
            msg = "Бито";
        }
        log.add(msg);
        int mainAtt = att;
        atk.clear();
        dfn.clear();
        own.clear();
        taking = false;
        reset();
        refill(mainAtt);
        for (int i = 0; i < n; i++) {
            if (!out[i] && hands.get(i).isEmpty() && deck.isEmpty()) out[i] = true;
        }
        int left = active();
        if (left <= 1) {
            over = true;
            fool = -1;
            for (int i = 0; i < n; i++) if (!out[i]) fool = i;
            msg = fool >= 0 ? "Дурак: " + names[fool] : "Нічия";
            log.add(msg);
            return;
        }
        int na = took ? nextActive(d) : (out[d] ? nextActive(d) : d);
        if (out[na]) na = nextActive(na);
        att = na;
        def = nextActive(att);
        limit = Math.min(MAXT, hands.get(def).size());
        thrower = firstThrower();
    }

    // хто зараз має діяти (для підсвічування)
    public int cur() {
        if (over) return -1;
        if (atk.isEmpty()) return att;
        if (unbeaten() > 0 && !taking) return def;
        if (ordered) {
            if (thrower != def && !out[thrower] && !passed[thrower]) return thrower;
        }
        if (!out[att] && att != def && !passed[att]) return att;
        for (int k = 1; k <= n; k++) {
            int p = (att + k) % n;
            if (p != def && !out[p] && !passed[p]) return p;
        }
        return att;
    }

    public void removePlayer(int i) {
        // лише між партіями
    }
}
