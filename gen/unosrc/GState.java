package com.example.gryntsivgames.uno;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;

public class GState {
    public int cur, dir, curColor, deck, pend;
    public boolean skip, canDraw, canPass, over;
    public Card top;
    public int lastP = -1;
    public ArrayList<Card> hand = new ArrayList<Card>();
    public ArrayList<Boolean> legal = new ArrayList<Boolean>();
    public int[] counts, totals, wins, last, asleep = new int[0], dnd = new int[0];
    public int paused = -1;
    public int[][] rounds = new int[0][];
    public String msg = "";

    public boolean dndOf(int i) {
        return i >= 0 && i < dnd.length && dnd[i] == 1;
    }

    public boolean sleeping(int i) {
        return i >= 0 && i < asleep.length && asleep[i] == 1;
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

    public static GState parse(String[] f) {
        GState g = new GState();
        g.cur = pi(f[1]);
        g.dir = pi(f[2]);
        g.top = Card.dec(f[3]);
        g.curColor = pi(f[4]);
        g.deck = pi(f[5]);
        g.pend = pi(f[6]);
        g.skip = f[7].equals("1");
        g.canDraw = f[8].equals("1");
        g.canPass = f[9].equals("1");
        final ArrayList<Card> hs = new ArrayList<Card>();
        ArrayList<Boolean> ls = new ArrayList<Boolean>();
        if (f[10].length() > 0) {
            String[] a = f[10].split(",");
            String[] b = f[11].split(",");
            for (int i = 0; i < a.length; i++) {
                hs.add(Card.dec(a[i]));
                ls.add(i < b.length && b[i].equals("1"));
            }
        }
        Integer[] idx = new Integer[hs.size()];
        for (int i = 0; i < idx.length; i++) idx[i] = i;
        Arrays.sort(idx, new Comparator<Integer>() {
            public int compare(Integer x, Integer y) {
                Card a = hs.get(x), b = hs.get(y);
                if (a.c != b.c) return a.c - b.c;
                return a.v - b.v;
            }
        });
        for (int i = 0; i < idx.length; i++) {
            g.hand.add(hs.get(idx[i]));
            g.legal.add(ls.get(idx[i]));
        }
        g.counts = ints(f[12]);
        g.totals = ints(f[13]);
        g.msg = f[14];
        g.over = f[15].equals("1");
        g.wins = ints(f[16]);
        g.last = ints(f[17]);
        g.paused = pi(f[18]);
        if (f.length > 19) g.rounds = parseRounds(f[19]);
        if (f.length > 20) g.asleep = ints(f[20]);
        else g.asleep = new int[g.counts.length];
        if (f.length > 21) g.dnd = ints(f[21]);
        if (f.length > 22) g.lastP = pi(f[22]);
        return g;
    }
}
