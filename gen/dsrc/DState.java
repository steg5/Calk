package com.example.gryntsivgames.durak;

import com.example.gryntsivgames.uno.Card;

import java.util.ArrayList;

public class DState {
    public int att, def, ts, deck, fool, paused = -1, cur;
    public boolean taking, canTake, canPass, over, transferOn, canTransfer;
    public Card trump;
    public ArrayList<Card> atk = new ArrayList<Card>();
    public ArrayList<Card> dfn = new ArrayList<Card>();
    public ArrayList<Card> hand = new ArrayList<Card>();
    public ArrayList<Boolean> legal = new ArrayList<Boolean>();
    public int[] counts, losses, dnd = new int[0], out = new int[0];
    public int[][] rounds = new int[0][];
    public String msg = "";
    public int gSeq;
    public ArrayList<Card> gAtk = new ArrayList<Card>();
    public ArrayList<Card> gDfn = new ArrayList<Card>();
    public int[] own = new int[0], gOwn = new int[0];
    public boolean ordered;

    public DState copy() {
        DState c = new DState();
        try {
            for (java.lang.reflect.Field f : DState.class.getFields()) {
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
        if (s.length() == 0) return new int[0];
        String[] p = s.split(",");
        int[] r = new int[p.length];
        for (int i = 0; i < p.length; i++) r[i] = pi(p[i]);
        return r;
    }

    public static int[][] parseRounds(String s) {
        if (s == null || s.length() == 0) return new int[0][];
        String[] rs = s.split(";");
        int[][] r = new int[rs.length][];
        for (int i = 0; i < rs.length; i++) r[i] = ints(rs[i]);
        return r;
    }

    private static ArrayList<Card> cards(String s) {
        ArrayList<Card> l = new ArrayList<Card>();
        if (s.length() == 0) return l;
        for (String t : s.split(",")) l.add(t.equals("-") ? null : Card.dec(t));
        return l;
    }

    public boolean dndOf(int i) {
        return i >= 0 && i < dnd.length && dnd[i] == 1;
    }

    public boolean outOf(int i) {
        return i >= 0 && i < out.length && out[i] == 1;
    }

    public int unbeaten() {
        int c = 0;
        for (Card d : dfn) if (d == null) c++;
        return c;
    }

    public boolean beats(Card a, Card d) {
        if (d.c == a.c) return d.v > a.v;
        return d.c == ts && a.c != ts;
    }

    public static DState parse(String[] f) {
        DState g = new DState();
        g.att = pi(f[1]);
        g.def = pi(f[2]);
        g.taking = f[3].equals("1");
        g.ts = pi(f[4]);
        g.trump = Card.dec(f[5]);
        g.deck = pi(f[6]);
        g.atk = cards(f[7]);
        g.dfn = cards(f[8]);
        g.canTake = f[9].equals("1");
        g.canPass = f[10].equals("1");
        final ArrayList<Card> hs = cards(f[11]);
        String[] lg = f[12].length() == 0 ? new String[0] : f[12].split(",");
        final int ts = g.ts;
        Integer[] idx = new Integer[hs.size()];
        for (int i = 0; i < idx.length; i++) idx[i] = i;
        java.util.Arrays.sort(idx, new java.util.Comparator<Integer>() {
            public int compare(Integer x, Integer y) {
                Card a = hs.get(x), b = hs.get(y);
                boolean ta = a.c == ts, tb = b.c == ts;
                if (ta != tb) return ta ? 1 : -1;
                if (a.c != b.c) return a.c - b.c;
                return a.v - b.v;
            }
        });
        for (int i = 0; i < idx.length; i++) {
            g.hand.add(hs.get(idx[i]));
            g.legal.add(idx[i] < lg.length && lg[idx[i]].equals("1"));
        }
        g.counts = ints(f[13]);
        g.losses = ints(f[14]);
        g.msg = f[15];
        g.over = f[16].equals("1");
        g.fool = pi(f[17]);
        g.paused = pi(f[18]);
        g.rounds = parseRounds(f[19]);
        g.dnd = ints(f[20]);
        g.cur = pi(f[21]);
        g.transferOn = f[22].equals("1");
        g.out = ints(f[23]);
        g.canTransfer = f.length > 24 && f[24].equals("1");
        if (f.length > 27) {
            g.gSeq = pi(f[25]);
            g.gAtk = cards(f[26]);
            g.gDfn = cards(f[27]);
        }
        if (f.length > 30) {
            g.ordered = f[28].equals("1");
            g.own = ints(f[29]);
            g.gOwn = ints(f[30]);
        }
        return g;
    }
}
