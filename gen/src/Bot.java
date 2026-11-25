package com.example.gryntsivgames.sea;

import java.util.ArrayList;
import java.util.Random;

public class Bot {
    public static final String[] LEVELS = {"Аматор", "Студент", "Геній", "Легенда"};
    private final int level;
    private final Random rnd = new Random();
    private static final int N = Board.N;

    public Bot(int level) {
        this.level = level;
    }

    private int pick(ArrayList<Integer> l) {
        return l.get(rnd.nextInt(l.size()));
    }

    private ArrayList<Integer> unknown(int[] s) {
        ArrayList<Integer> l = new ArrayList<Integer>();
        for (int i = 0; i < s.length; i++) if (s[i] == 0) l.add(i);
        return l;
    }

    private void add(ArrayList<Integer> l, int[] s, int r, int c) {
        if (r < 0 || c < 0 || r >= N || c >= N) return;
        if (s[r * N + c] == 0 && !l.contains(r * N + c)) l.add(r * N + c);
    }

    // клітинки навколо підбитих (не потоплених) палуб
    private ArrayList<Integer> targets(int[] s, boolean line) {
        ArrayList<Integer> l = new ArrayList<Integer>();
        ArrayList<Integer> hits = new ArrayList<Integer>();
        for (int i = 0; i < s.length; i++) if (s[i] == 2) hits.add(i);
        if (hits.isEmpty()) return l;
        if (line && hits.size() >= 2) {
            int a = hits.get(0), b = hits.get(hits.size() - 1);
            int ra = a / N, ca = a % N, rb = b / N, cb = b % N;
            if (ra == rb) {
                int lo = Math.min(ca, cb), hi = Math.max(ca, cb);
                add(l, s, ra, lo - 1);
                add(l, s, ra, hi + 1);
            } else if (ca == cb) {
                int lo = Math.min(ra, rb), hi = Math.max(ra, rb);
                add(l, s, lo - 1, ca);
                add(l, s, hi + 1, ca);
            }
            if (!l.isEmpty()) return l;
        }
        for (int h : hits) {
            int r = h / N, c = h % N;
            add(l, s, r - 1, c);
            add(l, s, r + 1, c);
            add(l, s, r, c - 1);
            add(l, s, r, c + 1);
        }
        return l;
    }

    public int next(Board b) {
        int[] s = b.shot;
        if (level == 0) return pick(unknown(s));
        if (level == 1) {
            ArrayList<Integer> t = targets(s, false);
            return t.isEmpty() ? pick(unknown(s)) : pick(t);
        }
        if (level == 2) {
            ArrayList<Integer> t = targets(s, true);
            if (!t.isEmpty()) return pick(t);
            ArrayList<Integer> par = new ArrayList<Integer>();
            for (int i = 0; i < s.length; i++) {
                if (s[i] == 0 && ((i / N + i % N) % 2 == 0)) par.add(i);
            }
            return par.isEmpty() ? pick(unknown(s)) : pick(par);
        }
        return legend(b);
    }

    private int legend(Board b) {
        int[] s = b.shot;
        int[] score = new int[N * N];
        ArrayList<Integer> lens = b.aliveLens();
        boolean anyHit = false;
        boolean[] blocked = new boolean[N * N];
        for (int i = 0; i < s.length; i++) {
            if (s[i] != 2) continue;
            anyHit = true;
            int r0 = i / N, c0 = i % N;
            for (int dr = -1; dr <= 1; dr += 2) {
                for (int dc = -1; dc <= 1; dc += 2) {
                    int rr = r0 + dr, cc = c0 + dc;
                    if (rr >= 0 && cc >= 0 && rr < N && cc < N) blocked[rr * N + cc] = true;
                }
            }
        }
        for (int len : lens) {
            for (int dir = 0; dir < 2; dir++) {
                if (len == 1 && dir == 1) continue;
                for (int r = 0; r < N; r++) {
                    for (int c = 0; c < N; c++) {
                        int er = dir == 1 ? r + len - 1 : r;
                        int ec = dir == 0 ? c + len - 1 : c;
                        if (er >= N || ec >= N) continue;
                        boolean ok = true;
                        int hits = 0;
                        for (int k = 0; k < len && ok; k++) {
                            int v = s[(dir == 1 ? r + k : r) * N + (dir == 0 ? c + k : c)];
                            if (v == 1 || v == 3 || blocked[(dir == 1 ? r + k : r) * N + (dir == 0 ? c + k : c)]) ok = false;
                            else if (v == 2) hits++;
                        }
                        if (!ok) continue;
                        int w = 1 + hits * hits * 60;
                        if (anyHit && hits == 0) w = 1;
                        for (int k = 0; k < len; k++) {
                            int cell = (dir == 1 ? r + k : r) * N + (dir == 0 ? c + k : c);
                            if (s[cell] == 0) score[cell] += w;
                        }
                    }
                }
            }
        }
        int best = -1;
        ArrayList<Integer> bl = new ArrayList<Integer>();
        for (int i = 0; i < s.length; i++) {
            if (s[i] != 0) continue;
            if (score[i] > best) {
                best = score[i];
                bl.clear();
            }
            if (score[i] == best) bl.add(i);
        }
        return bl.isEmpty() ? pick(unknown(s)) : pick(bl);
    }
}
