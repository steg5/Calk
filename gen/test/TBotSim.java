import com.example.gryntsivgames.thousand.TBot;
import com.example.gryntsivgames.thousand.TBotOld;
import com.example.gryntsivgames.thousand.TEngine;

import java.util.Random;

// Симулятор ботів «1000»: матчі бот проти бота. Параметри: [матчів] [рівні через кому, по одному на місце, напр. 3,3,3] [ліміт партій]
// Показує: середню довжину матчу, частку невиконаних ставок, болти, бочку, недограні матчі, перемоги за рівнями.
public class TBotSim {
    public static void main(String[] a) throws Exception {
        try { System.setOut(new java.io.PrintStream(System.out, true, "UTF-8")); } catch (Exception ex) { }
        int games = a.length > 0 ? Integer.parseInt(a[0]) : 600;
        String[] ls = (a.length > 1 ? a[1] : "1,1,1").split(",");
        int limit = a.length > 2 ? Integer.parseInt(a[2]) : 80;
        int n = ls.length;
        int[] lv = new int[n];
        boolean[] old = new boolean[n];
        for (int i = 0; i < n; i++) { String t = ls[i].trim(); if (t.startsWith("o")) { old[i] = true; t = t.substring(1); } lv[i] = Integer.parseInt(t); }
        int matches = 0, unfinished = 0, rounds = 0, contracts = 0, fails = 0, bolts = 0, pens = 0, barrelIn = 0, drops = 0, errors = 0, allPass = 0;
        int[] wins = new int[n];
        int[] declBy = new int[n], failBy = new int[n];
        long sumBid = 0;
        int[] hist = new int[8];
        long dDecl = 0, dDef = 0; int nDef = 0, firstBarrelSum = 0, firstBarrelCnt = 0;
        long[] tSum = new long[5], tMax = new long[5]; int[] tCnt = new int[5];
        int forced = 0, forcedFail = 0, volun = 0, volunFail = 0; long volunBid = 0;
        for (int g = 0; g < games; g++) {
            Random rnd = new Random(7000L + g);
            String[] names = new String[n];
            for (int i = 0; i < n; i++) names[i] = "B" + i;
            // місця перемішуємо, щоб позиція не давала переваги
            int[] seat = new int[n];
            for (int i = 0; i < n; i++) seat[i] = i;
            for (int i = n - 1; i > 0; i--) {
                int j = rnd.nextInt(i + 1);
                int t = seat[i]; seat[i] = seat[j]; seat[j] = t;
            }
            TEngine e = new TEngine(names, true, true, true, -1, rnd);
            TBot[] bots = new TBot[n];
            TBotOld[] obots = new TBotOld[n];
            for (int i = 0; i < n; i++) { if (old[seat[i]]) obots[i] = new TBotOld(lv[seat[i]], new Random(rnd.nextLong())); else bots[i] = new TBot(lv[seat[i]], new Random(rnd.nextLong())); }
            int r = 0;
            boolean bad = false;
            while (!e.matchOver && r < limit && !bad) {
                int guard = 0;
                while (e.phase != TEngine.END && guard++ < 400) {
                    int p = e.cur();
                    int ph0 = e.phase;
                    long t0 = System.nanoTime();
                    String err = obots[p] != null ? obots[p].act(e, p) : bots[p].act(e, p);
                    long dt = System.nanoTime() - t0;
                    tSum[ph0] += dt; tCnt[ph0]++; if (dt > tMax[ph0]) tMax[ph0] = dt;
                    if (err != null) {
                        errors++;
                        if (errors < 6) System.out.println("ПОМИЛКА бота рівня " + lv[seat[p]] + " фаза " + e.phase + ": " + err);
                        bad = true;
                        break;
                    }
                }
                if (bad) break;
                if (e.phase != TEngine.END) { errors++; bad = true; break; }
                r++;
                int[] fl = e.flags.get(e.flags.size() - 1);
                boolean fo = false;
                for (int q = e.log.size() - 1; q >= 0 && q >= e.log.size() - 400; q--) { String ln = e.log.get(q); if (ln.startsWith("Роздає")) break; if (ln.startsWith("Усі спасували")) { fo = true; break; } }
                contracts++;
                declBy[seat[e.decl]]++;
                sumBid += e.contract;
                hist[Math.min(7, Math.max(0, (e.contract - 100) / 10))]++;
                boolean fl0 = (fl[e.decl] & TEngine.F_FAIL) != 0;
                dDecl += e.rounds.get(e.rounds.size() - 1)[e.decl];
                for (int i = 0; i < n; i++) if (i != e.decl) { dDef += e.rounds.get(e.rounds.size() - 1)[i]; nDef++; }
                if (fo) { forced++; if (fl0) forcedFail++; } else { volun++; volunBid += e.contract; if (fl0) volunFail++; }
                if (fl0) { fails++; failBy[seat[e.decl]]++; }
                for (int i = 0; i < n; i++) {
                    if ((fl[i] & TEngine.F_BOLT) != 0) bolts++;
                    if ((fl[i] & TEngine.F_BOLT_PEN) != 0) pens++;
                    if ((fl[i] & TEngine.F_DROP) != 0) drops++;
                    if ((fl[i] & TEngine.F_BARREL) != 0 && !e.onBarrel[i] == false && (fl[i] & TEngine.F_DROP) == 0) barrelIn++;
                }
                if (e.matchOver) break;
                e.nextRound();
            }
            if (bad) continue;
            if (e.matchOver) {
                matches++;
                rounds += r;
                wins[seat[e.winner]]++;
            } else {
                unfinished++;
            }
        }
        System.out.println("рівні " + a[a.length > 1 ? 1 : 0].toString() + (a.length > 1 ? "" : " (за замовч. 1,1,1)") + ", матчів дограно " + matches + " з " + games + ", недограно (ліміт " + limit + ") " + unfinished + ", помилок " + errors);
        StringBuilder tb = new StringBuilder("час рішення бота, мс (середній/найдовший) за фазами торги, віддача, остаточна, розіграш:");
        for (int ph = 0; ph < 4; ph++) tb.append(" ").append(String.format("%.2f/%.1f", tCnt[ph] == 0 ? 0 : tSum[ph] / 1e6 / tCnt[ph], tMax[ph] / 1e6));
        System.out.println(tb);
        System.out.println("середня довжина матчу " + (matches == 0 ? 0 : String.format("%.1f", rounds / (double) matches)) + " партій");
        System.out.println("невиконаних ставок " + fails + " з " + contracts + " (" + (contracts == 0 ? 0 : fails * 100 / contracts) + "%), середня ставка " + (contracts == 0 ? 0 : sumBid / contracts));
        System.out.println("вимушених (усі спасували) " + forced + ", невдач серед них " + forcedFail + "; добровільних " + volun + ", невдач " + volunFail + ", середня ставка добровільних " + (volun == 0 ? 0 : volunBid / volun));
        System.out.println("середній приріст: господар " + (contracts == 0 ? 0 : dDecl / contracts) + ", інший гравець " + (nDef == 0 ? 0 : dDef / nDef));
        System.out.println("болтів " + bolts + ", штрафів за 3 болти " + pens + ", злетів з бочки " + drops + ", входів на бочку (прибл.) " + barrelIn);
        StringBuilder sb = new StringBuilder("перемоги за місцями рівнів:");
        for (int i = 0; i < n; i++) sb.append(" [рівень ").append(lv[i]).append(": ").append(wins[i]).append(", торгів ").append(declBy[i]).append(", невдач ").append(failBy[i]).append("]");
        System.out.println(sb);
        sb = new StringBuilder("розподіл ставок (100..,110..,...):");
        for (int h : hist) sb.append(' ').append(h);
        System.out.println(sb);
    }
}
