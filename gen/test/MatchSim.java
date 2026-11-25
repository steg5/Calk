package com.example.gryntsivgames.sea;

import java.util.ArrayDeque;
import java.util.Random;

public class MatchSim {
    static ArrayDeque<Runnable> q = new ArrayDeque<Runnable>();
    static Random rnd = new Random(7);
    static int games, restores, maxTurns;

    static class P implements Match.Ev {
        final int idx;
        final boolean hostSide;
        Match m;
        P[] all;
        boolean human;
        int ended = -2;

        P(int idx, boolean hostSide) {
            this.idx = idx;
            this.hostSide = hostSide;
        }

        public void send(final int to, final String line) {
            if (hostSide) {
                for (int i = 0; i < all.length; i++) {
                    if (i == 0) continue;
                    if (!all[i].human) continue;
                    if (to >= 0 && to != i) continue;
                    final P g = all[i];
                    q.add(new Runnable() {
                        public void run() {
                            g.m.fromHost(line);
                        }
                    });
                }
            } else {
                final P h = all[0];
                final int me = idx;
                q.add(new Runnable() {
                    public void run() {
                        h.m.fromGuest(me, line);
                    }
                });
            }
        }

        public void result(int s, int t, int cell, int res, boolean fin) {
        }

        public void turn(final int who, boolean restore) {
            if (who != idx || m.over) return;
            if (restore) {
                q.add(new Runnable() {
                    public void run() {
                        doRestore();
                    }
                });
            } else if (m.alive[idx]) {
                q.add(new Runnable() {
                    public void run() {
                        act();
                    }
                });
            }
        }

        void doRestore() {
            if (m.over || !m.restore || m.turn != idx) return;
            Board b = m.boards[idx];
            Board.Ship pick = null;
            for (Board.Ship s : b.ships) if (b.sunk(s)) pick = s;
            if (pick != null) {
                for (int k = 0; k < 400; k++) {
                    boolean h = rnd.nextBoolean();
                    int r = rnd.nextInt(10), c = rnd.nextInt(10);
                    if (Match.canRestore(b, pick, r, c, h)) {
                        restores++;
                        if (hostSide) {
                            m.restoreShip(idx, pick.len, pick.r, pick.c, r, c, h);
                        } else {
                            int len = pick.len, r0 = pick.r, c0 = pick.c;
                            m.applyRestore(idx, len, r0, c0, r, c, h);
                            send(0, "RESTORE|" + len + "|" + r0 + "|" + c0 + "|" + r + "|" + c + "|" + (h ? 1 : 0));
                        }
                        return;
                    }
                }
            }
            if (hostSide) m.skipRestore(idx);
            else send(0, "RSKIP");
        }

        void act() {
            if (m.over || m.turn != idx || !m.alive[idx]) return;
            int t = m.tgt(idx);
            int cell = new Bot(3).next(m.boards[t]);
            if (hostSide) m.shoot(idx, cell);
            else send(0, "SHOT|" + cell);
        }

        public void out(int i) {
        }

        public void over(int w) {
            ended = w;
        }

        public void restored(int i) {
        }

        public void emoji(int f, int k) {
        }

        public void poke(int f) {
        }

        public void blocked(String n) {
        }

        public void paused(int by) {
        }
    }

    static Board randBoard() {
        Board b = new Board();
        b.randomize(rnd);
        return b;
    }

    static void play(int n, int humans) {
        P[] all = new P[n];
        String[] names = new String[n];
        boolean[] bot = new boolean[n];
        for (int i = 0; i < n; i++) {
            names[i] = "P" + i;
            bot[i] = i > humans - 1 + 0 && i >= humans;
        }
        // humans: індекси 0..humans-1 (0 — господар), решта — боти
        P host = new P(0, true);
        all[0] = host;
        host.all = all;
        host.human = true;
        host.m = new Match(n, 0, true, names, bot, new int[]{3, 3, 3, 3, 3, 3}, host);
        host.m.boards[0] = randBoard();
        for (int i = 1; i < n; i++) {
            if (bot[i]) {
                host.m.boards[i] = randBoard();
                all[i] = new P(i, true); // заглушка
                all[i].human = false;
                all[i].all = all;
            } else {
                P g = new P(i, false);
                g.human = true;
                g.all = all;
                g.m = new Match(n, i, false, names, bot, null, g);
                g.m.boards[i] = randBoard();
                all[i] = g;
            }
        }
        final Match hm = host.m;
        // бот-ходи: імітуємо у циклі
        hm.start(rnd.nextInt(n));
        for (int i = 1; i < n; i++) if (all[i].human) all[i].m.turn = hm.turn;
        // стартове TURN для гостей не потрібне (START вже в черзі), але ініціюємо дію
        int guard = 0;
        // перший хід
        for (P p : all) if (p.human && !p.hostSide) { /* чекає на TURN */ }
        while (guard++ < 200000) {
            Runnable r = q.poll();
            if (r != null) {
                r.run();
                continue;
            }
            if (hm.over) break;
            int t = hm.turn;
            if (hm.bot[t] && !hm.restore && !hm.pending()) {
                int tg = hm.tgt(t);
                hm.shoot(t, hm.bots[t].next(hm.boards[tg]));
            } else {
                // ініціатор дії: перевірити, чи людина має ходити
                P p = all[t];
                if (p.human) {
                    if (hm.restore) p.doRestore();
                    else p.act();
                } else {
                    throw new RuntimeException("stuck: turn=" + t + " restore=" + hm.restore + " pending=" + hm.pending());
                }
            }
        }
        if (!hm.over) throw new RuntimeException("not finished n=" + n + " humans=" + humans);
        // перевірки
        if (hm.aliveCount() != 1) throw new RuntimeException("alive=" + hm.aliveCount());
        while (!q.isEmpty()) q.poll().run();
        for (int i = 1; i < n; i++) {
            if (!all[i].human) continue;
            Match g = all[i].m;
            if (!g.over) throw new RuntimeException("guest " + i + " not over");
            for (int k = 0; k < n; k++) {
                if (g.alive[k] != hm.alive[k]) throw new RuntimeException("alive mismatch guest " + i + " player " + k);
            }
            for (int k = 0; k < n; k++) {
                if (k == i) continue;
                // огляд гостя за гравцем k має збігатися з видом господаря (shot-масив)
                Board a = g.boards[k];
                Board b = hm.boards[k];
                for (int c = 0; c < 100; c++) {
                    if (a.shot[c] != b.shot[c]) throw new RuntimeException("view mismatch guest " + i + " board " + k + " cell " + c + " " + a.shot[c] + " vs " + b.shot[c]);
                }
            }
        }
        games++;
        if (hm.turn > maxTurns) maxTurns = hm.turn;
    }

    public static void main(String[] a) {
        for (int it = 0; it < 300; it++) {
            int n = 2 + rnd.nextInt(5);
            int humans = 1 + rnd.nextInt(n);
            play(n, humans);
        }
        System.out.println("games " + games + " restores " + restores + " OK");
    }
}
