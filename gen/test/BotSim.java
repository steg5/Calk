package com.example.gryntsivgames.sea;

import java.util.Random;

public class BotSim {
    public static void main(String[] a) {
        Random r = new Random(1);
        for (int lvl = 0; lvl < 4; lvl++) {
            long total = 0;
            int games = 300;
            for (int g = 0; g < games; g++) {
                Board b = new Board();
                b.randomize(r);
                // перевірка правил: кораблі не торкаються
                for (Board.Ship s : b.ships) if (!s.placed || !b.canPlace(s.r, s.c, s.len, s.h, s.idx)) throw new RuntimeException("bad placement");
                Bot bot = new Bot(lvl);
                int shots = 0;
                while (!b.allSunk()) {
                    int c = bot.next(b);
                    if (b.shot[c] != 0) throw new RuntimeException("repeat shot lvl " + lvl);
                    b.shoot(c);
                    shots++;
                    if (shots > 100) throw new RuntimeException("too many");
                }
                total += shots;
            }
            System.out.println("level " + lvl + " avg shots " + (total / (double) games));
        }
    }
}
