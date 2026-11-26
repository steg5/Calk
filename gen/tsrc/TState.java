package com.example.gryntsivgames.thousand;

import com.example.gryntsivgames.uno.Card;

import java.util.ArrayList;

// Розібраний рядок STATE «1000» для одного гравця. Порядок полів і правила клієнта: HANDOFF.md, розділ «Протокол гри».
// parse(f): f = line.split("\\|", -1), f[0] = "STATE", усього 48 елементів. Повертає null, якщо рядок обірваний.
// Рука відсортована для показу (масть, потім за очками); legal і mar йдуть у тому самому порядку, що й hand.
public class TState {
    public int phase, dealer, cur, decl, contract, bid, bidder, trump = -1, leader, minBid, finalMin, finalMax;
    public boolean canBid, canPass;
    public ArrayList<Card> hand = new ArrayList<Card>();
    public ArrayList<Boolean> legal = new ArrayList<Boolean>();
    public ArrayList<Boolean> mar = new ArrayList<Boolean>();
    public int[] counts = new int[0], total = new int[0];
    public String msg = "";
    public boolean over;
    public int winner = -1, paused = -1;
    public int[][] rounds = new int[0][], flags = new int[0][];
    public int[] dnd = new int[0], passed = new int[0], onBarrel = new int[0], barrelRnd = new int[0], bolts = new int[0];
    public ArrayList<Card> trick = new ArrayList<Card>();
    public int[] trickBy = new int[0];
    public ArrayList<Card> talon = new ArrayList<Card>();
    public int[] giveNeed = new int[0];
    public ArrayList<Card> lastTrick = new ArrayList<Card>();
    public int[] lastTrickBy = new int[0];
    public int trickSeq, lastWinner = -1;
    public int[] tricksWon = new int[0], marPts = new int[0], marOwner = new int[0];
    public int roundNo, talonSize;
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

    public static TState parse(String[] f) {
        if (f == null || f.length < 48) return null;
        try {
            TState g = new TState();
            g.phase = pi(f[1]);
            g.dealer = pi(f[2]);
            g.cur = pi(f[3]);
            g.decl = pi(f[4]);
            g.contract = pi(f[5]);
            g.bid = pi(f[6]);
            g.bidder = pi(f[7]);
            g.trump = pi(f[8]);
            g.leader = pi(f[9]);
            g.minBid = pi(f[10]);
            g.finalMin = pi(f[11]);
            g.finalMax = pi(f[12]);
            g.canBid = f[13].equals("1");
            g.canPass = f[14].equals("1");
            final ArrayList<Card> hs = cards(f[15]);
            int[] lg = ints(f[16]), mr = ints(f[17]);
            Integer[] idx = new Integer[hs.size()];
            for (int i = 0; i < idx.length; i++) idx[i] = i;
            java.util.Arrays.sort(idx, new java.util.Comparator<Integer>() {
                public int compare(Integer x, Integer y) {
                    Card a = hs.get(x), b = hs.get(y);
                    if (a.c != b.c) return a.c - b.c;
                    return TEngine.points(a) - TEngine.points(b);
                }
            });
            for (int i = 0; i < idx.length; i++) {
                g.hand.add(hs.get(idx[i]));
                g.legal.add(idx[i] < lg.length && lg[idx[i]] == 1);
                g.mar.add(idx[i] < mr.length && mr[idx[i]] == 1);
            }
            g.counts = ints(f[18]);
            g.total = ints(f[19]);
            g.msg = f[20];
            g.over = f[21].equals("1");
            g.winner = pi(f[22]);
            g.paused = pi(f[23]);
            g.rounds = parseRounds(f[24]);
            g.flags = parseRounds(f[25]);
            g.dnd = ints(f[26]);
            g.passed = ints(f[27]);
            g.onBarrel = ints(f[28]);
            g.barrelRnd = ints(f[29]);
            g.bolts = ints(f[30]);
            g.trick = cards(f[31]);
            g.trickBy = ints(f[32]);
            g.talon = cards(f[33]);
            g.giveNeed = ints(f[34]);
            g.lastTrick = cards(f[35]);
            g.lastTrickBy = ints(f[36]);
            g.trickSeq = pi(f[37]);
            g.lastWinner = pi(f[38]);
            g.tricksWon = ints(f[39]);
            g.marPts = ints(f[40]);
            g.marOwner = ints(f[41]);
            g.roundNo = pi(f[42]);
            g.talonSize = pi(f[43]);
            g.showTalon = f[44].equals("1");
            g.marAfter = f[45].equals("1");
            g.barrelOn = f[46].equals("1");
            g.boltsOn = f[47].equals("1");
            return g;
        } catch (Exception e) {
            return null;
        }
    }

    // ---------- зручні запитання для столу й екрана ----------
    public int n() {
        return counts.length;
    }

    public boolean isTurn(int me) {
        return cur == me && !over && paused < 0;
    }

    public boolean dndOf(int i) {
        return i >= 0 && i < dnd.length && dnd[i] == 1;
    }

    public boolean passedOf(int i) {
        return i >= 0 && i < passed.length && passed[i] == 1;
    }

    public boolean onBarrelOf(int i) {
        return i >= 0 && i < onBarrel.length && onBarrel[i] == 1;
    }

    public int barrelRoundOf(int i) {
        return i >= 0 && i < barrelRnd.length ? barrelRnd[i] : 0;
    }

    public int boltsOf(int i) {
        return i >= 0 && i < bolts.length ? bolts[i] : 0;
    }

    public int totalOf(int i) {
        return i >= 0 && i < total.length ? total[i] : 0;
    }

    public int countOf(int i) {
        return i >= 0 && i < counts.length ? counts[i] : 0;
    }

    public int needOf(int i) {
        return i >= 0 && i < giveNeed.length ? giveNeed[i] : 0;
    }

    public int needTotal() {
        int s = 0;
        for (int x : giveNeed) s += x;
        return s;
    }

    public boolean canMarriageNow() {
        for (Boolean b : mar) if (b) return true;
        return false;
    }

    // масті, у яких мар'яж уже оголошено, і хто
    public int marOwnerOf(int suit) {
        return suit >= 0 && suit < marOwner.length ? marOwner[suit] : -1;
    }

    // карта, яку гравець who поклав у поточну взятку; null, якщо ще не ходив
    public Card trickCardOf(int who) {
        for (int k = 0; k < trickBy.length && k < trick.size(); k++) if (trickBy[k] == who) return trick.get(k);
        return null;
    }

    public Card lastTrickCardOf(int who) {
        for (int k = 0; k < lastTrickBy.length && k < lastTrick.size(); k++) if (lastTrickBy[k] == who) return lastTrick.get(k);
        return null;
    }

    // сума очок у руці (значок на кутовій карті)
    public int handPoints() {
        int s = 0;
        for (Card c : hand) s += TEngine.points(c);
        return s;
    }

    // чи останній рядок історії - болт, зліт з бочки тощо (прапорці TEngine.F_*) для гравця i
    public int lastFlag(int i) {
        if (rounds.length == 0 || flags.length == 0) return 0;
        int[] r = flags[flags.length - 1];
        return i >= 0 && i < r.length ? r[i] : 0;
    }

    public static String suitSym(int s) {
        return s >= 0 && s < 4 ? String.valueOf("♠♥♦♣".charAt(s)) : "";
    }
}
