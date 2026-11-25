package com.example.gryntsivgames.durak;

import com.example.gryntsivgames.uno.Card;

import java.util.ArrayList;
import java.util.Random;

// Боти Дурака: 0 Аматор, 1 Студент, 2 Геній, 3 Легенда
public class DBot {
    public static final int NONE = -1, THROW = 0, DEFEND = 1, TRANSFER = 2, TAKE = 3, PASS = 4;

    public static class Act {
        public int type = NONE;
        public Card card;
        public int target = -1;
    }

    private final Random rnd = new Random();
    // пам'ять Легенди: які карти вже вийшли з гри (зіграні в «бито»)
    private final boolean[][] seen = new boolean[4][15];

    public void reset() {
        for (int s = 0; s < 4; s++) for (int r = 0; r < 15; r++) seen[s][r] = false;
    }

    public void noteDiscard(ArrayList<Card> cs) {
        for (Card c : cs) seen[c.c][c.v] = true;
    }

    private int val(DEngine e, Card c) {
        return c.v + (e.trump(c) ? 20 : 0);
    }

    private Card lowest(DEngine e, ArrayList<Card> l) {
        Card b = null;
        for (Card c : l) if (b == null || val(e, c) < val(e, b)) b = c;
        return b;
    }

    private int trumpsIn(DEngine e, int p) {
        int k = 0;
        for (Card c : e.hands.get(p)) if (e.trump(c)) k++;
        return k;
    }

    // кількість карт ранга v у руці
    private int rankCount(DEngine e, int p, int v) {
        int k = 0;
        for (Card c : e.hands.get(p)) if (c.v == v) k++;
        return k;
    }

    // вірогідність, що у суперника ще є козирі (для Легенди)
    private int trumpsUnseen(DEngine e, int p) {
        int k = 0;
        for (int r = 6; r <= 14; r++) {
            if (seen[e.ts][r]) continue;
            boolean mine = false;
            for (Card c : e.hands.get(p)) if (c.c == e.ts && c.v == r) mine = true;
            if (!mine) k++;
        }
        return k;
    }

    public Act choose(DEngine e, int p, int lvl) {
        Act a = new Act();
        if (e.over || e.out[p]) return a;
        if (p == e.def && !e.taking && e.unbeaten() > 0) return defender(e, p, lvl, a);
        if (p == e.def) return a;
        return thrower(e, p, lvl, a);
    }

    private Act defender(DEngine e, int p, int lvl, Act a) {
        ArrayList<Card> hand = e.hands.get(p);
        if (e.hopeless(p)) {
            a.type = TAKE;
            return a;
        }
        int k = -1;
        for (int i = 0; i < e.atk.size(); i++) {
            if (e.dfn.get(i) == null) {
                k = i;
                break;
            }
        }
        // перевід
        if (lvl >= 1 && e.transferOn && !e.anyBeaten()) {
            ArrayList<Card> tr = new ArrayList<Card>();
            for (Card c : hand) if (e.canTransfer(p, c) && !e.trump(c)) tr.add(c);
            if (!tr.isEmpty() && (lvl >= 2 || rnd.nextInt(3) > 0)) {
                a.type = TRANSFER;
                a.card = lowest(e, tr);
                return a;
            }
        }
        // найдешевша карта, що б'є першу невідбиту
        Card atkCard = e.atk.get(k);
        ArrayList<Card> ok = new ArrayList<Card>();
        for (Card c : hand) if (e.beats(atkCard, c)) ok.add(c);
        if (ok.isEmpty()) {
            a.type = TAKE;
            return a;
        }
        Card pick;
        if (lvl == 0) {
            pick = ok.get(rnd.nextInt(ok.size()));
            if (rnd.nextInt(10) == 0) {
                a.type = TAKE;
                return a;
            }
        } else {
            ArrayList<Card> plain = new ArrayList<Card>();
            for (Card c : ok) if (!e.trump(c)) plain.add(c);
            pick = !plain.isEmpty() ? lowest(e, plain) : lowest(e, ok);
            if (lvl >= 2 && e.trump(pick) && !e.deck.isEmpty()) {
                // не варто витрачати козир на дрібницю, поки є колода
                int cheap = e.unbeaten() + 0;
                boolean small = !e.trump(atkCard) && atkCard.v <= 10;
                if (small && pick.v >= (lvl == 2 ? 10 : 9) && e.deck.size() > 3 && cheap <= 2) {
                    a.type = TAKE;
                    return a;
                }
                if (lvl == 3 && small && pick.v >= 7 && e.deck.size() > 6 && trumpsIn(e, p) <= 3) {
                    a.type = TAKE;
                    return a;
                }
            }
        }
        a.type = DEFEND;
        a.card = pick;
        a.target = k;
        return a;
    }

    private Act thrower(DEngine e, int p, int lvl, Act a) {
        ArrayList<Card> hand = e.hands.get(p);
        ArrayList<Card> ok = new ArrayList<Card>();
        for (Card c : hand) if (e.canThrow(p, c)) ok.add(c);
        if (e.atk.isEmpty()) {
            if (ok.isEmpty()) return a;
            Card pick;
            if (lvl == 0) {
                pick = ok.get(rnd.nextInt(ok.size()));
            } else {
                ArrayList<Card> plain = new ArrayList<Card>();
                for (Card c : ok) if (!e.trump(c)) plain.add(c);
                ArrayList<Card> src = !plain.isEmpty() ? plain : ok;
                pick = lowest(e, src);
                if (lvl >= 2) {
                    // починати з рангу, якого більше (можна підкинути парами)
                    Card best = pick;
                    int bc = rankCount(e, p, pick.v);
                    for (Card c : src) {
                        int rc = rankCount(e, p, c.v);
                        if (rc > bc && c.v <= pick.v + 3) {
                            bc = rc;
                            best = c;
                        }
                    }
                    pick = best;
                }
                if (lvl == 3 && e.deck.isEmpty() && e.hands.get(e.def).size() <= 2 && !plain.isEmpty()) {
                    // кінцівка: тисну найвищою некозирною картою
                    Card hi = null;
                    for (Card c : plain) if (hi == null || c.v > hi.v) hi = c;
                    pick = hi;
                }
            }
            a.type = THROW;
            a.card = pick;
            return a;
        }
        if (ok.isEmpty() || e.passed[p]) {
            if (e.canPass(p)) a.type = PASS;
            return a;
        }
        boolean takingNow = e.taking;
        ArrayList<Card> plain = new ArrayList<Card>();
        for (Card c : ok) if (!e.trump(c)) plain.add(c);
        Card pick = null;
        if (lvl == 0) {
            if (rnd.nextInt(3) > 0) pick = ok.get(rnd.nextInt(ok.size()));
        } else if (takingNow) {
            // карти все одно піде захиснику: кидаю дрібні, козирі не віддаю до кінцівки
            if (!plain.isEmpty()) pick = lowest(e, plain);
            else if (e.deck.isEmpty()) pick = lowest(e, ok);
        } else {
            if (lvl >= 2 && e.unbeaten() >= (lvl == 2 ? 2 : 3) && !e.deck.isEmpty()) {
                // не засипаю захисника одразу кількома картами
                pick = null;
            } else if (!plain.isEmpty()) {
                Card c = lowest(e, plain);
                int lim = lvl == 1 ? 10 : (lvl == 2 ? 11 : 12);
                if (c.v <= lim) pick = c;
                if (lvl == 3 && e.hands.get(e.def).size() <= 3 && e.deck.isEmpty()) pick = c;
            } else if (e.deck.isEmpty() && lvl >= 2 && e.hands.get(e.def).size() <= 2) {
                pick = lowest(e, ok);
            }
        }
        if (pick == null) {
            if (e.canPass(p)) a.type = PASS;
            return a;
        }
        a.type = THROW;
        a.card = pick;
        return a;
    }
}
