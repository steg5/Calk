package com.example.gryntsivgames.thousand;

import com.example.gryntsivgames.uno.Card;

import java.util.ArrayList;

// Розбір рядка STATE гри «1000» (отримує гравець; руки суперників не передаються).
// Поля після «STATE» читаються підряд (порядок фіксований; THost.stateMsg пише їх у тому ж порядку):
//  1 phase (0 торги, 1 віддача, 2 остаточна ставка, 3 розіграш, 4 кінець партії)
//  2 dealer   3 cur (хто діє, -1 ніхто)   4 decl (господар, -1)   5 contract   6 bid   7 bidder
//  8 trump (-1 немає)   9 leader   10 minBid   11 finalMin   12 finalMax
// 13 canBid (0/1)   14 canPassBid (0/1)
// 15 рука «c:v,c:v»   16 legal по картах руки «1,0,..» (у порядку рядка 15)   17 canMar по картах руки
// 18 кількість карт у гравців   19 total (загальні очки)   20 msg   21 matchOver   22 winner   23 paused (-1)
// 24 rounds (приріст очків: рядки «;», значення «,»)   25 flags (такий самий формат, біти TEngine.F_*)
// 26 dnd по гравцях   27 passed по гравцях   28 onBarrel   29 barrelRnd   30 boltCnt
// 31 trick (карти на столі)   32 trickBy   33 talonShown (карти прикупу, видимі всім; порожньо, якщо сховано)
// 34 giveNeed по гравцях   35 lastTrick   36 lastTrickBy   37 trickSeq   38 lastWinner
// 39 tricksWon   40 marPts   41 marOwner (4 масті)   42 roundNo   43 talonN (карт у прикупі до торгів)
// 44 showTalon (0/1)   45 marAfter (0/1)   46 barrelOn   47 boltsOn
public class TState {
    public int phase, dealer, cur = -1, decl = -1, contract, bid, bidder = -1, trump = -1, leader = -1;
    public int minBid, finalMin, finalMax;
    public boolean canBid, canPassBid;
    public ArrayList<Card> hand = new ArrayList<Card>();
    public ArrayList<Boolean> legal = new ArrayList<Boolean>();
    public ArrayList<Boolean> canMar = new ArrayList<Boolean>();
    public int[] counts = new int[0], total = new int[0];
    public String msg = "";
    public boolean matchOver;
    public int winner = -1, paused = -1;
    public int[][] rounds = new int[0][], flags = new int[0][];
    public int[] dnd = new int[0], passed = new int[0], onBarrel = new int[0], barrelRnd = new int[0], boltCnt = new int[0];
    public ArrayList<Card> trick = new ArrayList<Card>(), talonShown = new ArrayList<Card>(), lastTrick = new ArrayList<Card>();
    public int[] trickBy = new int[0], lastTrickBy = new int[0];
    public int[] giveNeed = new int[0], tricksWon = new int[0], marPts = new int[0];
    public int[] marOwner = {-1, -1, -1, -1};
    public int trickSeq, lastWinner = -1, roundNo, talonN;
    public boolean showTalon, marAfter, barrelOn, boltsOn;

    public TState copy() {
        TState c = new TState();
        try {
            for (java.lang.reflect.Field f : TState.class.getFields()) {
                if (java.lang.reflect.Modifier.isStatic(f.getModifiers())) continue;
                f.set(c, f.get(this));
            }
        } catch (Exception e) {
            return this;
        }
        return c;
    }

    private static int pi(String s) {
        try {
            return Integer.parseInt(s.trim());
        } catch (Exception e) {
            return 0;
        }
    }

    public static int[] ints(String s) {
        if (s == null || s.length() == 0) return new int[0];
        String[] p = s.split(",");
        int[] r = new int[p.length];
        for (int i = 0; i < p.length; i++) r[i] = pi(p[i]);
        return r;
    }

    public static int[][] parseRounds(String s) {
        if (s == null || s.length() == 0) return new int[0][];
        String[] rs = s.split(";", -1);
        int[][] r = new int[rs.length][];
        for (int i = 0; i < rs.length; i++) r[i] = ints(rs[i]);
        return r;
    }

    private static ArrayList<Card> cards(String s) {
        ArrayList<Card> l = new ArrayList<Card>();
        if (s == null || s.length() == 0) return l;
        for (String t : s.split(",")) l.add(Card.dec(t));
        return l;
    }

    public boolean dndOf(int i) {
        return i >= 0 && i < dnd.length && dnd[i] == 1;
    }

    public boolean passedOf(int i) {
        return i >= 0 && i < passed.length && passed[i] == 1;
    }

    public boolean barrelOf(int i) {
        return i >= 0 && i < onBarrel.length && onBarrel[i] == 1;
    }

    public int at(int[] a, int i) {
        return (a != null && i >= 0 && i < a.length) ? a[i] : 0;
    }

    // біти TEngine.F_* для партії k гравця i
    public int flagAt(int k, int i) {
        if (k < 0 || k >= flags.length) return 0;
        return at(flags[k], i);
    }

    public int sumHand() {
        int s = 0;
        for (Card c : hand) s += TEngine.points(c);
        return s;
    }

    private static final class Rd {
        final String[] f;
        int i = 1;

        Rd(String[] f) {
            this.f = f;
        }

        String s() {
            return i < f.length ? f[i++] : "";
        }

        int n() {
            return pi(s());
        }

        boolean b() {
            return s().equals("1");
        }
    }

    public static TState parse(String[] f) {
        Rd r = new Rd(f);
        TState g = new TState();
        g.phase = r.n();
        g.dealer = r.n();
        g.cur = r.n();
        g.decl = r.n();
        g.contract = r.n();
        g.bid = r.n();
        g.bidder = r.n();
        g.trump = r.n();
        g.leader = r.n();
        g.minBid = r.n();
        g.finalMin = r.n();
        g.finalMax = r.n();
        g.canBid = r.b();
        g.canPassBid = r.b();
        final ArrayList<Card> hs = cards(r.s());
        String ls = r.s(), ms = r.s();
        String[] lg = ls.length() == 0 ? new String[0] : ls.split(",");
        String[] mg = ms.length() == 0 ? new String[0] : ms.split(",");
        final int tr = g.trump;
        Integer[] idx = new Integer[hs.size()];
        for (int i = 0; i < idx.length; i++) idx[i] = i;
        java.util.Arrays.sort(idx, new java.util.Comparator<Integer>() {
            public int compare(Integer x, Integer y) {
                Card a = hs.get(x), b = hs.get(y);
                boolean ta = tr >= 0 && a.c == tr, tb = tr >= 0 && b.c == tr;
                if (ta != tb) return ta ? 1 : -1;
                if (a.c != b.c) return a.c - b.c;
                return a.v - b.v;
            }
        });
        for (int i = 0; i < idx.length; i++) {
            g.hand.add(hs.get(idx[i]));
            g.legal.add(idx[i] < lg.length && lg[idx[i]].equals("1"));
            g.canMar.add(idx[i] < mg.length && mg[idx[i]].equals("1"));
        }
        g.counts = ints(r.s());
        g.total = ints(r.s());
        g.msg = r.s();
        g.matchOver = r.b();
        g.winner = r.n();
        g.paused = r.n();
        g.rounds = parseRounds(r.s());
        g.flags = parseRounds(r.s());
        g.dnd = ints(r.s());
        g.passed = ints(r.s());
        g.onBarrel = ints(r.s());
        g.barrelRnd = ints(r.s());
        g.boltCnt = ints(r.s());
        g.trick = cards(r.s());
        g.trickBy = ints(r.s());
        g.talonShown = cards(r.s());
        g.giveNeed = ints(r.s());
        g.lastTrick = cards(r.s());
        g.lastTrickBy = ints(r.s());
        g.trickSeq = r.n();
        g.lastWinner = r.n();
        g.tricksWon = ints(r.s());
        g.marPts = ints(r.s());
        int[] mo = ints(r.s());
        if (mo.length == 4) g.marOwner = mo;
        g.roundNo = r.n();
        g.talonN = r.n();
        g.showTalon = r.b();
        g.marAfter = r.b();
        g.barrelOn = r.b();
        g.boltsOn = r.b();
        return g;
    }
}
