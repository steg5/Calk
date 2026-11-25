import com.example.gryntsivgames.durak.*;
import com.example.gryntsivgames.uno.Card;
import java.util.*;

public class DSim {
    public static void main(String[] a) {
        Random rnd = new Random(7);
        int games = 0, draws = 0, steps = 0;
        for (int g = 0; g < 3000; g++) {
            int n = 2 + rnd.nextInt(5);
            String[] nm = new String[n];
            for (int i = 0; i < n; i++) nm[i] = "P" + i;
            DEngine e = new DEngine(nm, g % 3 == 0 ? -1 : rnd.nextInt(n), 0, g % 2 == 0, rnd);
            e.setOrdered(g % 5 < 2);
            DBot bot = new DBot();
            int[] lv = new int[n];
            for (int i = 0; i < n; i++) lv[i] = rnd.nextInt(4);
            int guard = 0;
            while (!e.over && guard++ < 5000) {
                boolean acted = false;
                int[] order = new int[n];
                for (int i = 0; i < n; i++) order[i] = (e.cur() + i) % n;
                for (int p : order) {
                    DBot.Act act = bot.choose(e, p, lv[p]);
                    String r = null;
                    if (act.type == DBot.THROW) r = e.attack(p, act.card);
                    else if (act.type == DBot.DEFEND) r = e.defend(p, act.card, act.target);
                    else if (act.type == DBot.TRANSFER) r = e.transfer(p, act.card);
                    else if (act.type == DBot.TAKE) r = e.take(p);
                    else if (act.type == DBot.PASS) r = e.pass(p);
                    else continue;
                    if (r != null) throw new RuntimeException("bad " + act.type + " " + r);
                    acted = true;
                    steps++;
                    break;
                }
                int total = e.deck.size() + e.atk.size();
                for (Card c : e.dfn) if (c != null) total++;
                for (int i = 0; i < n; i++) total += e.hands.get(i).size();
                // скинуті карти не рахуємо: перевірка лише що не більше 36
                if (total > 36) throw new RuntimeException("cards " + total);
                if (!acted) throw new RuntimeException("stuck n=" + n + " att=" + e.att + " def=" + e.def + " atk=" + e.atk.size() + " unb=" + e.unbeaten() + " taking=" + e.taking);
            }
            if (!e.over) throw new RuntimeException("no end");
            games++;
            if (e.fool < 0) draws++;
        }
        System.out.println("games " + games + " draws " + draws + " steps " + steps);
    }
}
