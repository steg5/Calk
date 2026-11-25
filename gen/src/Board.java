package com.example.gryntsivgames.sea;

import java.util.ArrayList;
import java.util.Random;

public class Board {
    public static final int N = 10;
    public static final int[] FLEET = {4, 3, 3, 2, 2, 2, 1, 1, 1, 1};

    public static class Ship {
        public int len, r, c, idx, theme = -1;
        public boolean h = true, placed;

        public boolean has(int rr, int cc) {
            if (!placed) return false;
            if (h) return rr == r && cc >= c && cc < c + len;
            return cc == c && rr >= r && rr < r + len;
        }
    }

    public int baseTheme;
    public final Ship[] ships = new Ship[FLEET.length];
    // 0 нічого, 1 мимо, 2 влучання, 3 потоплено
    public final int[] shot = new int[N * N];

    public Board() {
        for (int i = 0; i < ships.length; i++) {
            ships[i] = new Ship();
            ships[i].len = FLEET[i];
            ships[i].idx = i;
        }
    }

    public int shipAt(int r, int c) {
        for (Ship s : ships) {
            if (s.has(r, c)) return s.idx;
        }
        return -1;
    }

    public boolean canPlace(int r, int c, int len, boolean h, int ignore) {
        int er = h ? r : r + len - 1;
        int ec = h ? c + len - 1 : c;
        if (r < 0 || c < 0 || er >= N || ec >= N) return false;
        for (int rr = r - 1; rr <= er + 1; rr++) {
            for (int cc = c - 1; cc <= ec + 1; cc++) {
                if (rr < 0 || cc < 0 || rr >= N || cc >= N) continue;
                int id = shipAt(rr, cc);
                if (id >= 0 && id != ignore) return false;
            }
        }
        return true;
    }

    public void place(int idx, int r, int c, boolean h) {
        Ship s = ships[idx];
        s.r = r;
        s.c = c;
        s.h = h;
        s.placed = true;
    }

    public void clear() {
        for (Ship s : ships) {
            s.placed = false;
            s.theme = -1;
        }
    }

    public boolean allPlaced() {
        for (Ship s : ships) if (!s.placed) return false;
        return true;
    }

    public void randomize(Random rnd) {
        while (true) {
            clear();
            boolean ok = true;
            for (int i = 0; i < ships.length && ok; i++) {
                boolean done = false;
                for (int t = 0; t < 300 && !done; t++) {
                    boolean h = rnd.nextBoolean();
                    int r = rnd.nextInt(N), c = rnd.nextInt(N);
                    if (canPlace(r, c, ships[i].len, h, i)) {
                        place(i, r, c, h);
                        done = true;
                    }
                }
                if (!done) ok = false;
            }
            if (ok) return;
        }
    }

    public boolean sunk(Ship s) {
        if (!s.placed) return false;
        for (int k = 0; k < s.len; k++) {
            int r = s.h ? s.r : s.r + k;
            int c = s.h ? s.c + k : s.c;
            if (shot[r * N + c] < 2) return false;
        }
        return true;
    }

    // 0 мимо, 1 влучив, 2 потопив
    public int shoot(int cell) {
        int r = cell / N, c = cell % N;
        int id = shipAt(r, c);
        if (id < 0) {
            shot[cell] = 1;
            return 0;
        }
        shot[cell] = 2;
        Ship s = ships[id];
        if (!sunk(s)) return 1;
        markSunk(s);
        return 2;
    }

    private void markSunk(Ship s) {
        for (int k = 0; k < s.len; k++) {
            int rr = s.h ? s.r : s.r + k;
            int cc = s.h ? s.c + k : s.c;
            shot[rr * N + cc] = 3;
        }
        for (int rr = s.r - 1; rr <= (s.h ? s.r : s.r + s.len - 1) + 1; rr++) {
            for (int cc = s.c - 1; cc <= (s.h ? s.c + s.len - 1 : s.c) + 1; cc++) {
                if (rr < 0 || cc < 0 || rr >= N || cc >= N) continue;
                if (shot[rr * N + cc] == 0) shot[rr * N + cc] = 1;
            }
        }
    }

    // відповідь на постріл суперника: RES|клітинка|результат|r,c,довжина,h|кінець
    public String answer(int cell) {
        if (cell < 0 || cell >= N * N) return "RES|" + cell + "|0||0";
        int res = shot[cell] != 0 ? (shot[cell] == 1 ? 0 : 1) : shoot(cell);
        String extra = "";
        if (res == 2) {
            Ship s = ships[shipAt(cell / N, cell % N)];
            extra = s.r + "," + s.c + "," + s.len + "," + (s.h ? 1 : 0) + "," + (s.theme >= 0 ? s.theme : baseTheme);
        }
        return "RES|" + cell + "|" + res + "|" + extra + "|" + (allSunk() ? 1 : 0);
    }

    // застосувати відповідь суперника до поля-огляду
    public void applyRes(String[] f) {
        int cell = Integer.parseInt(f[1]);
        int res = Integer.parseInt(f[2]);
        if (res == 0) {
            shot[cell] = 1;
            return;
        }
        shot[cell] = 2;
        if (res == 2 && f[3].length() > 0) {
            String[] e = f[3].split(",");
            int r = Integer.parseInt(e[0]), c = Integer.parseInt(e[1]), len = Integer.parseInt(e[2]);
            boolean hz = e[3].equals("1");
            for (Ship s : ships) {
                if (s.len == len && !s.placed) {
                    place(s.idx, r, c, hz);
                    if (e.length > 4) s.theme = Integer.parseInt(e[4]);
                    markSunk(s);
                    break;
                }
            }
        }
    }

    public boolean allSunk() {
        for (Ship s : ships) if (!sunk(s)) return false;
        return true;
    }

    public ArrayList<Integer> aliveLens() {
        ArrayList<Integer> l = new ArrayList<Integer>();
        for (Ship s : ships) if (!sunk(s)) l.add(s.len);
        return l;
    }
}
