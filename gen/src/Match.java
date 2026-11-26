package com.example.gryntsivgames.sea;

import java.util.ArrayList;
import java.util.Random;

// Логіка бою на 2–6 гравців по колу. Господар — арбітр, гості лише застосовують його повідомлення.
public class Match {
    public interface Ev {
        // idx >= 0 — конкретному гравцю, -1 — усім гостям
        void send(int idx, String line);

        void result(int shooter, int target, int cell, int res, boolean fin);

        void turn(int idx, boolean restore);

        void out(int idx);

        void over(int winner);

        void restored(int idx);

        void emoji(int from, int k);

        void poke(int from);

        void blocked(String nick);

        void paused(int by);
    }

    public final int n;
    public final String[] names;
    public final boolean[] bot, remote; // remote — людина-гість (поле лише як огляд)
    public final Board[] boards;
    public final boolean[] alive, dnd;
    public final Bot[] bots;
    public final int me;
    public final boolean host;
    public final Ev ev;
    public int turn = -1, pauseBy = -1;
    public boolean restore;
    public boolean restoreOn = true;
    public boolean over;
    private int pShooter = -1, pTarget = -1, pCell = -1;
    private final Random rnd = new Random();

    public Match(int n, int me, boolean host, String[] names, boolean[] bot, int[] level, Ev ev) {
        this.n = n;
        this.me = me;
        this.host = host;
        this.names = names;
        this.bot = bot;
        this.ev = ev;
        remote = new boolean[n];
        boards = new Board[n];
        alive = new boolean[n];
        dnd = new boolean[n];
        bots = new Bot[n];
        for (int i = 0; i < n; i++) {
            boards[i] = new Board();
            alive[i] = true;
            if (bot[i]) bots[i] = new Bot(level == null ? 3 : level[i]);
            remote[i] = host && i != me && !bot[i];
        }
    }

    public int aliveCount() {
        int c = 0;
        for (boolean a : alive) if (a) c++;
        return c;
    }

    // ціль гравця — найближчий живий далі по колу ("зліва")
    public int tgt(int i) {
        for (int k = 1; k < n; k++) {
            int j = (i + k) % n;
            if (alive[j]) return j;
        }
        return -1;
    }

    public boolean full(int i) {
        return i == me || (host && bot[i]);
    }

    // ---------- господар ----------
    public void start(int first) {
        turn = first;
        restore = false;
        over = false;
        pShooter = -1;
        pauseBy = -1;
        ev.send(-1, "START|" + first);
        ev.turn(turn, false);
    }

    public boolean pending() {
        return pShooter >= 0;
    }

    // постріл гравця shooter; false — відхилено
    public boolean shoot(int shooter, int cell) {
        if (!host || over || restore || pShooter >= 0 || pauseBy >= 0 || turn != shooter || !alive[shooter]) return false;
        int t = tgt(shooter);
        if (t < 0 || cell < 0 || cell >= Board.N * Board.N) return false;
        if (boards[t].shot[cell] != 0) return false;
        if (full(t)) {
            resolve(shooter, t, boards[t].answer(cell));
        } else {
            pShooter = shooter;
            pTarget = t;
            pCell = cell;
            ev.send(t, "SHOT|" + shooter + "|" + cell);
        }
        return true;
    }

    private void resolve(int shooter, int t, String ans) {
        String[] f = ans.split("\\|", -1);
        int cell = Integer.parseInt(f[1]);
        int res = Integer.parseInt(f[2]);
        boolean fin = f[4].equals("1");
        if (remote[t]) boards[t].applyRes(f);
        String msg = ans + "|" + shooter + "|" + t;
        for (int i = 0; i < n; i++) if (remote[i] && i != t) ev.send(i, msg);
        pShooter = -1;
        ev.result(shooter, t, cell, res, fin);
        if (fin) {
            alive[t] = false;
            ev.out(t);
            if (over) return;
            if (aliveCount() <= 1) {
                finish(shooter);
                return;
            }
            turn = shooter;
            if (restoreOn) {
                restore = true;
                ev.send(-1, "TURN|" + turn + "|1");
                ev.turn(turn, true);
                if (bot[shooter]) botRestore(shooter);
                return;
            }
        }
        if (res == 0) turn = tgt(shooter);
        ev.send(-1, "TURN|" + turn + "|0");
        ev.turn(turn, false);
    }

    private void finish(int winner) {
        over = true;
        restore = false;
        ev.send(-1, "END|" + winner);
        ev.over(winner);
    }

    private void botRestore(int i) {
        Board b = boards[i];
        ArrayList<Board.Ship> sunk = new ArrayList<Board.Ship>();
        for (Board.Ship s : b.ships) if (b.sunk(s)) sunk.add(s);
        if (!sunk.isEmpty()) {
            Board.Ship s = sunk.get(rnd.nextInt(sunk.size()));
            for (int t = 0; t < 400; t++) {
                boolean h = rnd.nextBoolean();
                int r = rnd.nextInt(Board.N), c = rnd.nextInt(Board.N);
                if (canRestore(b, s, r, c, h)) {
                    restoreShip(i, s.len, s.r, s.c, r, c, h);
                    return;
                }
            }
        }
        endRestore(i);
    }

    public static boolean canRestore(Board b, Board.Ship s, int r, int c, boolean h) {
        if (!b.canPlace(r, c, s.len, h, s.idx)) return false;
        int er = h ? r : r + s.len - 1, ec = h ? c + s.len - 1 : c;
        for (int rr = r; rr <= er; rr++) {
            for (int cc = c; cc <= ec; cc++) {
                if (b.shot[rr * Board.N + cc] != 0) return false;
            }
        }
        return true;
    }

    private void endRestore(int i) {
        restore = false;
        ev.send(-1, "TURN|" + i + "|0");
        ev.turn(i, false);
    }

    // перенести потоплений корабель; викликається і господарем, і гостями
    public void applyRestore(int idx, int len, int r0, int c0, int r1, int c1, boolean h) {
        Board b = boards[idx];
        for (Board.Ship s : b.ships) {
            if (s.placed && s.len == len && s.r == r0 && s.c == c0) {
                if (full(idx)) {
                    s.r = r1;
                    s.c = c1;
                    s.h = h;
                } else {
                    s.placed = false;
                }
                break;
            }
        }
        ev.restored(idx);
    }

    public void restoreShip(int idx, int len, int r0, int c0, int r1, int c1, boolean h) {
        applyRestore(idx, len, r0, c0, r1, c1, h);
        ev.send(-1, "RESTORE|" + idx + "|" + len + "|" + r0 + "|" + c0 + "|" + r1 + "|" + c1 + "|" + (h ? 1 : 0));
        endRestore(idx);
    }

    public void skipRestore(int idx) {
        endRestore(idx);
    }

    public void playerLeft(int i) {
        if (!host || over || !alive[i]) return;
        alive[i] = false;
        ev.send(-1, "OUT|" + i);
        ev.out(i);
        if (pauseBy == i) {
            pauseBy = -1;
            ev.send(-1, "PAUSE|-1");
            ev.paused(-1);
        }
        if (aliveCount() <= 1) {
            for (int k = 0; k < n; k++) if (alive[k]) {
                finish(k);
                return;
            }
            finish(-1);
            return;
        }
        if (pShooter >= 0 && (pTarget == i || pShooter == i)) pShooter = -1;
        if (turn == i) {
            restore = false;
            turn = tgt(i);
        }
        ev.send(-1, "TURN|" + turn + "|" + (restore ? 1 : 0));
        ev.turn(turn, restore);
    }

    // повідомлення від гостя idx
    public void fromGuest(int idx, String line) {
        String[] f = line.split("\\|", -1);
        String c = f[0];
        try {
            if (c.equals("SHOT")) {
                if (!shoot(idx, Integer.parseInt(f[1]))) ev.send(idx, "TURN|" + turn + "|" + (restore ? 1 : 0));
            } else if (c.equals("RES")) {
                if (pShooter >= 0 && pTarget == idx && Integer.parseInt(f[1]) == pCell) resolve(pShooter, idx, line);
            } else if (c.equals("RESTORE")) {
                if (restore && turn == idx) {
                    restoreShip(idx, Integer.parseInt(f[1]), Integer.parseInt(f[2]), Integer.parseInt(f[3]),
                        Integer.parseInt(f[4]), Integer.parseInt(f[5]), f[6].equals("1"));
                }
            } else if (c.equals("RSKIP")) {
                if (restore && turn == idx) skipRestore(idx);
            } else if (c.equals("EMO")) {
                emoji(idx, Integer.parseInt(f[1]), Integer.parseInt(f[2]));
            } else if (c.equals("POKE")) {
                poke(idx, Integer.parseInt(f[1]));
            } else if (c.equals("DND")) {
                dnd[idx] = f[1].equals("1");
            } else if (c.equals("PAUSE")) {
                pause(idx, f[1].equals("1"));
            }
        } catch (Exception e) {
            // некоректне повідомлення ігноруємо
        }
    }

    public void pause(int idx, boolean on) {
        if (!host) return;
        if (on && pauseBy < 0) pauseBy = idx;
        else if (!on && pauseBy == idx) pauseBy = -1;
        else return;
        ev.send(-1, "PAUSE|" + pauseBy);
        ev.paused(pauseBy);
    }

    private void deliverEmoji(int from, int to, int k) {
        if (to == me) ev.emoji(from, k);
        else if (remote[to]) ev.send(to, "EMO|" + from + "|" + k);
    }

    public void emoji(int from, int k, int to) {
        if (!host) return;
        if (to >= 0 && to < n && to != from) {
            if (dnd[to]) {
                blocked(from, to);
            } else {
                deliverEmoji(from, to, k);
            }
        } else {
            for (int i = 0; i < n; i++) {
                if (i != from && !bot[i] && !dnd[i]) deliverEmoji(from, i, k);
            }
        }
    }

    public void poke(int from, int to) {
        if (!host || to < 0 || to >= n || to == from || bot[to]) return;
        if (dnd[to]) {
            blocked(from, to);
        } else if (to == me) {
            ev.poke(from);
        } else {
            ev.send(to, "POKE|" + from);
        }
    }

    private void blocked(int from, int to) {
        if (from == me) ev.blocked(names[to]);
        else ev.send(from, "BLK|" + names[to]);
    }

    // ---------- гість (і господар для власних повідомлень) ----------
    public void fromHost(String line) {
        String[] f = line.split("\\|", -1);
        String c = f[0];
        try {
            if (c.equals("SHOT")) {
                int shooter = Integer.parseInt(f[1]), cell = Integer.parseInt(f[2]);
                String ans = boards[me].answer(cell);
                String[] rf = ans.split("\\|", -1);
                ev.send(0, ans);
                int res = Integer.parseInt(rf[2]);
                boolean fin = rf[4].equals("1");
                ev.result(shooter, me, cell, res, fin);
                if (fin) {
                    alive[me] = false;
                    ev.out(me);
                }
            } else if (c.equals("RES")) {
                int cell = Integer.parseInt(f[1]), res = Integer.parseInt(f[2]);
                int shooter = Integer.parseInt(f[5]), t = Integer.parseInt(f[6]);
                boolean fin = f[4].equals("1");
                if (t != me) boards[t].applyRes(f);
                ev.result(shooter, t, cell, res, fin);
                if (fin && t != me) {
                    alive[t] = false;
                    ev.out(t);
                }
            } else if (c.equals("TURN")) {
                turn = Integer.parseInt(f[1]);
                restore = f[2].equals("1");
                ev.turn(turn, restore);
            } else if (c.equals("RESTORE")) {
                if (Integer.parseInt(f[1]) != me) {
                    applyRestore(Integer.parseInt(f[1]), Integer.parseInt(f[2]), Integer.parseInt(f[3]), Integer.parseInt(f[4]),
                        Integer.parseInt(f[5]), Integer.parseInt(f[6]), f[7].equals("1"));
                }
            } else if (c.equals("OUT")) {
                int i = Integer.parseInt(f[1]);
                if (alive[i]) {
                    alive[i] = false;
                    ev.out(i);
                }
            } else if (c.equals("END")) {
                over = true;
                ev.over(Integer.parseInt(f[1]));
            } else if (c.equals("EMO")) {
                ev.emoji(Integer.parseInt(f[1]), Integer.parseInt(f[2]));
            } else if (c.equals("POKE")) {
                ev.poke(Integer.parseInt(f[1]));
            } else if (c.equals("BLK")) {
                ev.blocked(f[1]);
            } else if (c.equals("PAUSE")) {
                pauseBy = Integer.parseInt(f[1]);
                ev.paused(pauseBy);
            }
        } catch (Exception e) {
            // некоректне повідомлення ігноруємо
        }
    }
}
