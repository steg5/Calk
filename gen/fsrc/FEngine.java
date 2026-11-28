package com.example.gryntsivgames.farm;

import java.util.ArrayList;
import java.util.Random;

// Правила «Ферма люкс» (2-5 гравців, чиста Java). Тварини: 0 качка, 1 коза, 2 свиня, 3 кінь, 4 корова.
// Хід: (необов'язковий обмін) -> кидок двох кубиків -> напад, потім розведення.
// Усі дії повертають null (успіх) або текст помилки. Рушій авторитетний: хост лише передає наміри гравців.
// Формат STATE і команди: HANDOFF.md, розділ «Протокол гри Ферми».
public class FEngine {
    public static final int DUCK = 0, GOAT = 1, PIG = 2, HORSE = 3, COW = 4, KINDS = 5;
    public static final int FOX = 5, BEAR = 6;
    public static final String[] NAMES = {"качка", "коза", "свиня", "кінь", "корова"};
    // форми для відмінювання у знахідному: 1 / 2-4 / 5+ (для рядків журналу)
    private static final String[][] FORMS = {
        {"качку", "качки", "качок"}, {"козу", "кози", "кіз"}, {"свиню", "свині", "свиней"},
        {"коня", "коні", "коней"}, {"корову", "корови", "корів"}};
    public static final int[] BANK0 = {60, 24, 20, 12, 6};
    // вартість у качках (для обміну між гравцями)
    public static final int[] VAL = {1, 6, 12, 36, 72};
    // 12 граней кубиків: 6 качок, 2 кози, 2 свині, 1 корова, 1 лисиця / 6 качок, 3 кози, 1 свиня, 1 кінь, 1 ведмідь
    public static final int[] DIE1 = {0, 0, 0, 0, 0, 0, 1, 1, 2, 2, 4, FOX};
    public static final int[] DIE2 = {0, 0, 0, 0, 0, 0, 1, 1, 1, 2, 3, BEAR};
    // фази: гра йде, перемога (чекаємо вибору «Продовжити / Реванш»), матч завершено
    public static final int PLAY = 0, WON = 1, END = 2;
    // курси банку: 0 качки в козу (6:1), 1 кози в свиню (2:1), 2 свині в коня (3:1), 3 коні в корову (2:1),
    // 4 коза в малого пса (1:1), 5 кінь у великого пса (1:1). up = платиш нижче, отримуєш вище
    public static final int RATES = 6;
    public static final int[] RATE_LOW = {DUCK, GOAT, PIG, HORSE, GOAT, HORSE};
    public static final int[] RATE_N = {6, 2, 3, 2, 1, 1};
    public static final int MAX_DECLINES = 3;
    // стан гравця
    public static final int ACTIVE = 0, FINISHED = 1, LEFT = 2;

    public final int n;
    public String[] names;
    public final boolean tradesOn;
    private final Random rnd;

    public final int[][] farm;
    public final boolean[] small, big;
    public final int[] bank = new int[KINDS];
    public final int[] status;
    public final ArrayList<Integer> ranking = new ArrayList<Integer>();
    public int phase, turn, turnNo, winner = -1, start;
    public boolean exDone;
    // обмін між гравцями, що чекає відповіді
    public int pendFrom = -1, pendTo = -1, pendGive, pendGiveN, pendGet, pendGetN, declined;
    // останній кидок
    public int rollSeq, rollBy = -1, face1 = -1, face2 = -1;
    public boolean foxSaved, bearSaved, pity;
    public final int[] gained = new int[KINDS], lost = new int[KINDS];
    public String msg = "";
    public final ArrayList<String> log = new ArrayList<String>();

    private FEngine(int n, boolean tradesOn, Random rnd) {
        this.n = n;
        this.tradesOn = tradesOn;
        this.rnd = rnd;
        farm = new int[n][KINDS];
        small = new boolean[n];
        big = new boolean[n];
        status = new int[n];
    }

    // first < 0: випадковий перший гравець
    public FEngine(String[] names, boolean tradesOn, int first, Random rnd) {
        this(names.length, tradesOn, rnd);
        if (n < 2 || n > 5) throw new RuntimeException("n");
        this.names = names;
        start = (first < 0 || first >= n) ? rnd.nextInt(n) : first;
        reset();
    }

    private void reset() {
        for (int i = 0; i < n; i++) {
            java.util.Arrays.fill(farm[i], 0);
            small[i] = false;
            big[i] = false;
            if (status[i] != LEFT) {
                status[i] = ACTIVE;
                farm[i][DUCK] = 1;
            }
        }
        System.arraycopy(BANK0, 0, bank, 0, KINDS);
        ranking.clear();
        phase = PLAY;
        winner = -1;
        turnNo = 1;
        exDone = false;
        clearPend();
        declined = 0;
        rollSeq = 0;
        rollBy = -1;
        face1 = -1;
        face2 = -1;
        foxSaved = false;
        bearSaved = false;
        pity = false;
        java.util.Arrays.fill(gained, 0);
        java.util.Arrays.fill(lost, 0);
        turn = start;
        while (status[turn] != ACTIVE) turn = (turn + 1) % n;
        say("Нова гра, першим ходить " + names[turn]);
    }

    // ---------- допоміжні ----------
    public static String cnt(int kind, int k) {
        int m = k % 100, d = k % 10;
        String[] f = FORMS[kind];
        String w = (d == 1 && m != 11) ? f[0] : ((d >= 2 && d <= 4 && (m < 12 || m > 14)) ? f[1] : f[2]);
        return k + " " + w;
    }

    private static String face(int f) {
        return f == FOX ? "лисиця" : (f == BEAR ? "ведмідь" : NAMES[f]);
    }

    private void say(String s) {
        msg = s;
        log.add(s);
    }

    public boolean isActive(int p) {
        return p >= 0 && p < n && status[p] == ACTIVE;
    }

    public int activeCount() {
        int c = 0;
        for (int i = 0; i < n; i++) if (status[i] == ACTIVE) c++;
        return c;
    }

    public int animals(int p) {
        int s = 0;
        for (int k = 0; k < KINDS; k++) s += farm[p][k];
        return s;
    }

    public boolean hasAll(int p) {
        for (int k = 0; k < KINDS; k++) if (farm[p][k] <= 0) return false;
        return true;
    }

    // чи є сенс пропонувати «Продовжити»: після виходу переможця лишається щонайменше двоє
    public boolean canContinue() {
        return phase == WON && activeCount() - 1 >= 2;
    }

    private void clearPend() {
        pendFrom = -1;
        pendTo = -1;
        pendGive = 0;
        pendGiveN = 0;
        pendGet = 0;
        pendGetN = 0;
    }

    public boolean hasPending() {
        return pendFrom >= 0;
    }

    public int cur() {
        return phase == PLAY ? turn : -1;
    }

    private void nextTurn() {
        int t = turn;
        for (int k = 0; k < n; k++) {
            t = (t + 1) % n;
            if (status[t] == ACTIVE) break;
        }
        turn = t;
        turnNo++;
        exDone = false;
        declined = 0;
        clearPend();
    }

    private void checkWin(int p) {
        if (phase == PLAY && hasAll(p)) {
            phase = WON;
            winner = p;
            clearPend();
            say(names[p] + " зібрав усіх п'ятьох тварин і перемагає");
        }
    }

    private void returnAll(int p) {
        for (int k = 0; k < KINDS; k++) {
            bank[k] += farm[p][k];
            farm[p][k] = 0;
        }
        small[p] = false;
        big[p] = false;
    }

    // ---------- обмін з банком ----------
    // скільки разів можна виконати курс rate (up: платиш нижчі, отримуєш вищу); 0 = не можна
    public int maxTimes(int p, int rate, boolean up) {
        if (rate < 0 || rate >= RATES || p < 0 || p >= n) return 0;
        int lo = RATE_LOW[rate], nn = RATE_N[rate];
        if (rate >= 4) {
            boolean dog = rate == 4 ? small[p] : big[p];
            if (up) return (!dog && farm[p][lo] >= 1) ? 1 : 0;
            return (dog && bank[lo] >= 1) ? 1 : 0;
        }
        int hi = lo + 1;
        if (up) return Math.min(farm[p][lo] / nn, bank[hi]);
        return Math.min(farm[p][hi], bank[lo] / nn);
    }

    public boolean canExchange(int p) {
        return phase == PLAY && p == turn && !exDone && !hasPending();
    }

    public boolean canRoll(int p) {
        return phase == PLAY && p == turn && !hasPending();
    }

    // одна операція будь-якої кількості за одним курсом; не більше однієї за хід (разом з обміном між гравцями)
    public String exchange(int p, int rate, boolean up, int times) {
        if (phase != PLAY) return "Гра не йде";
        if (p != turn) return "Зараз не ваш хід";
        if (hasPending()) return "Очікується відповідь на пропозицію обміну";
        if (exDone) return "Обмін у цей хід уже був";
        if (rate < 0 || rate >= RATES || times < 1) return "Некоректний обмін";
        if (rate >= 4 && times != 1) return "Пес купується по одному";
        if (times > maxTimes(p, rate, up)) return "Обмін неможливий: бракує тварин у вас або в банку";
        int lo = RATE_LOW[rate], nn = RATE_N[rate];
        if (rate >= 4) {
            String dn = rate == 4 ? "малого пса" : "великого пса";
            if (up) {
                farm[p][lo]--;
                bank[lo]++;
                if (rate == 4) small[p] = true;
                else big[p] = true;
                say(names[p] + " купує " + dn + " за " + (rate == 4 ? "козу" : "коня"));
            } else {
                if (rate == 4) small[p] = false;
                else big[p] = false;
                farm[p][lo]++;
                bank[lo]--;
                say(names[p] + " продає " + dn + " за " + (rate == 4 ? "козу" : "коня"));
            }
        } else {
            int hi = lo + 1;
            if (up) {
                farm[p][lo] -= nn * times;
                bank[lo] += nn * times;
                farm[p][hi] += times;
                bank[hi] -= times;
                say(names[p] + " міняє " + cnt(lo, nn * times) + " на " + cnt(hi, times));
            } else {
                farm[p][hi] -= times;
                bank[hi] += times;
                farm[p][lo] += nn * times;
                bank[lo] -= nn * times;
                say(names[p] + " міняє " + cnt(hi, times) + " на " + cnt(lo, nn * times));
            }
        }
        exDone = true;
        checkWin(p);
        return null;
    }

    // ---------- обмін між гравцями (налаштування tradesOn) ----------
    public String offer(int p, int to, int giveKind, int giveN, int getKind, int getN) {
        if (!tradesOn) return "Обмін між гравцями вимкнено";
        if (phase != PLAY) return "Гра не йде";
        if (p != turn) return "Зараз не ваш хід";
        if (hasPending()) return "Пропозиція вже надіслана";
        if (exDone) return "Обмін у цей хід уже був";
        if (declined >= MAX_DECLINES) return "Забагато відмов у цей хід";
        if (!isActive(to) || to == p) return "Некоректний адресат";
        if (giveKind < 0 || giveKind >= KINDS || getKind < 0 || getKind >= KINDS || giveKind == getKind) return "Некоректна пропозиція";
        if (giveN < 1 || getN < 1) return "Некоректна кількість";
        if (VAL[giveKind] * giveN != VAL[getKind] * getN) return "Обмін має бути за курсом банку";
        if (farm[p][giveKind] < giveN) return "У вас немає стільки тварин";
        if (farm[to][getKind] < getN) return "У гравця немає стільки тварин";
        pendFrom = p;
        pendTo = to;
        pendGive = giveKind;
        pendGiveN = giveN;
        pendGet = getKind;
        pendGetN = getN;
        say(names[p] + " пропонує " + names[to] + ": " + cnt(giveKind, giveN) + " за " + cnt(getKind, getN));
        return null;
    }

    public String respond(int q, boolean accept) {
        if (!hasPending() || q != pendTo) return "Немає пропозиції для вас";
        int p = pendFrom;
        if (!accept) {
            say(names[q] + " відмовляє " + names[p]);
            clearPend();
            declined++;
            return null;
        }
        if (farm[p][pendGive] < pendGiveN || farm[q][pendGet] < pendGetN) {
            clearPend();
            return "Пропозиція втратила чинність";
        }
        farm[p][pendGive] -= pendGiveN;
        farm[q][pendGive] += pendGiveN;
        farm[q][pendGet] -= pendGetN;
        farm[p][pendGet] += pendGetN;
        say(names[q] + " погоджується: " + names[p] + " отримує " + cnt(pendGet, pendGetN) + ", віддає " + cnt(pendGive, pendGiveN));
        clearPend();
        exDone = true;
        checkWin(p);
        checkWin(q);
        return null;
    }

    public String cancelOffer(int p) {
        if (!hasPending() || p != pendFrom) return "Немає вашої пропозиції";
        say(names[p] + " скасовує пропозицію");
        clearPend();
        return null;
    }

    // ---------- кидок ----------
    public String roll(int p) {
        return roll(p, rnd.nextInt(12), rnd.nextInt(12));
    }

    // d1, d2: грані кубиків 0..11 (для тестів і відтворення)
    public String roll(int p, int d1, int d2) {
        if (phase != PLAY) return "Гра не йде";
        if (p != turn) return "Зараз не ваш хід";
        if (hasPending()) return "Очікується відповідь на пропозицію обміну";
        if (d1 < 0 || d1 > 11 || d2 < 0 || d2 > 11) return "Некоректний кидок";
        int f1 = DIE1[d1], f2 = DIE2[d2];
        rollSeq++;
        rollBy = p;
        face1 = f1;
        face2 = f2;
        foxSaved = false;
        bearSaved = false;
        pity = false;
        java.util.Arrays.fill(gained, 0);
        java.util.Arrays.fill(lost, 0);
        say(names[p] + " кидає: " + face(f1) + " і " + face(f2));
        boolean[] hit = new boolean[KINDS];
        if (f1 == FOX) {
            if (small[p]) {
                small[p] = false;
                foxSaved = true;
                say("Малий пес проганяє лисицю і йде в банк");
            } else {
                take(p, DUCK, hit);
                take(p, GOAT, hit);
                say(foxLine(p));
            }
        }
        if (f2 == BEAR) {
            if (big[p]) {
                big[p] = false;
                bearSaved = true;
                say("Великий пес проганяє ведмедя і йде в банк");
            } else {
                take(p, PIG, hit);
                take(p, COW, hit);
                take(p, HORSE, hit);
                say(bearLine(p));
            }
        }
        // розведення: пар = (є + випало) / 2; вид, який зачепив напад, не розводиться
        int[] r = new int[KINDS];
        if (f1 < KINDS) r[f1]++;
        if (f2 < KINDS) r[f2]++;
        for (int k = 0; k < KINDS; k++) {
            if (r[k] == 0 || hit[k]) continue;
            int pairs = (farm[p][k] + r[k]) / 2;
            int g = Math.min(pairs, bank[k]);
            if (g > 0) {
                farm[p][k] += g;
                bank[k] -= g;
                gained[k] += g;
                say(names[p] + " отримує " + cnt(k, g) + (g < pairs ? " (у банку більше немає)" : ""));
            }
        }
        // порожня ферма без собак: банк дає качку
        if (animals(p) == 0 && !small[p] && !big[p] && bank[DUCK] > 0) {
            farm[p][DUCK]++;
            bank[DUCK]--;
            gained[DUCK]++;
            pity = true;
            say(names[p] + " лишився без тварин, банк дає качку");
        }
        if (hasAll(p)) {
            checkWin(p);
            return null;
        }
        nextTurn();
        return null;
    }

    private void take(int p, int kind, boolean[] hit) {
        hit[kind] = true;
        int c = farm[p][kind];
        if (c > 0) {
            lost[kind] += c;
            bank[kind] += c;
            farm[p][kind] = 0;
        }
    }

    private String foxLine(int p) {
        int d = lost[DUCK], g = lost[GOAT];
        if (d + g == 0) return "Лисиця приходить, але забирати нічого";
        StringBuilder s = new StringBuilder("Лисиця забирає ");
        if (d > 0) s.append(cnt(DUCK, d));
        if (d > 0 && g > 0) s.append(" і ");
        if (g > 0) s.append(cnt(GOAT, g));
        return s.toString();
    }

    private String bearLine(int p) {
        int t = lost[PIG] + lost[COW] + lost[HORSE];
        if (t == 0) return "Ведмідь приходить, але забирати нічого";
        StringBuilder s = new StringBuilder("Ведмідь забирає ");
        boolean first = true;
        int[] ks = {PIG, HORSE, COW};
        for (int k : ks) {
            if (lost[k] == 0) continue;
            if (!first) s.append(", ");
            s.append(cnt(k, lost[k]));
            first = false;
        }
        return s.toString();
    }

    // ---------- після перемоги ----------
    // переможець вибуває (його тварини йдуть у банк), решта грає далі за місцями
    public String continueGame() {
        if (phase != WON) return "Перемоги ще не було";
        int w = winner;
        ranking.add(w);
        status[w] = FINISHED;
        returnAll(w);
        winner = -1;
        int left = activeCount();
        if (left <= 1) {
            for (int i = 0; i < n; i++) if (status[i] == ACTIVE) ranking.add(i);
            phase = END;
            say("Матч завершено");
            return null;
        }
        phase = PLAY;
        turn = w;
        nextTurn();
        say("Гра триває без " + names[w] + ", ходить " + names[turn]);
        return null;
    }

    // нова гра з тими самими гравцями; першим ходить наступний за попереднім першим
    public String rematch() {
        if (phase != WON && phase != END) return "Гру ще не завершено";
        int s = start;
        for (int k = 0; k < n; k++) {
            s = (s + 1) % n;
            if (status[s] != LEFT) break;
        }
        start = s;
        for (int i = 0; i < n; i++) if (status[i] == FINISHED) status[i] = ACTIVE;
        if (activeCount() < 2) return "Для реваншу потрібно щонайменше 2 гравці";
        reset();
        return null;
    }

    // гравець іде з гри (його тварини повертаються в банк)
    public String leave(int p) {
        if (p < 0 || p >= n || status[p] == LEFT) return "Немає такого гравця";
        boolean wasActive = status[p] == ACTIVE;
        boolean wasTurn = phase == PLAY && p == turn;
        if (hasPending() && (p == pendFrom || p == pendTo)) clearPend();
        status[p] = LEFT;
        returnAll(p);
        say(names[p] + " залишає гру");
        if (phase == PLAY && wasActive) {
            if (activeCount() <= 1) {
                for (int i = 0; i < n; i++) if (status[i] == ACTIVE) ranking.add(i);
                phase = END;
                say("Матч завершено");
            } else if (wasTurn) {
                nextTurn();
            }
        } else if (phase == WON && p == winner) {
            // переможець пішов до вибору: його місце вже здобуте
            ranking.add(p);
            winner = -1;
            if (activeCount() <= 1) {
                for (int i = 0; i < n; i++) if (status[i] == ACTIVE) ranking.add(i);
                phase = END;
            } else {
                phase = PLAY;
                turn = p;
                nextTurn();
            }
        }
        return null;
    }

    // ---------- команди протоколу ----------
    // ROLL, XCH|rate|up|times, OFFER|to|giveKind|giveN|getKind|getN, ACCEPT, REFUSE, CANCEL
    public String apply(int p, String[] f) {
        try {
            String c = f[0];
            if (c.equals("ROLL")) return roll(p);
            if (c.equals("XCH")) return exchange(p, Integer.parseInt(f[1]), f[2].equals("1"), Integer.parseInt(f[3]));
            if (c.equals("OFFER")) {
                return offer(p, Integer.parseInt(f[1]), Integer.parseInt(f[2]), Integer.parseInt(f[3]), Integer.parseInt(f[4]), Integer.parseInt(f[5]));
            }
            if (c.equals("ACCEPT")) return respond(p, true);
            if (c.equals("REFUSE")) return respond(p, false);
            if (c.equals("CANCEL")) return cancelOffer(p);
        } catch (RuntimeException e) {
            return "Некоректна команда";
        }
        return "Невідома команда";
    }

    // ---------- STATE для гравця viewer (поля 1..22, див. HANDOFF) ----------
    private static String ints(int[] a) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < a.length; i++) sb.append(i > 0 ? "," : "").append(a[i]);
        return sb.toString();
    }

    public String stateLine(int viewer) {
        StringBuilder sb = new StringBuilder("STATE");
        sb.append('|').append(phase);
        sb.append('|').append(cur());
        sb.append('|').append(turnNo);
        sb.append('|').append(exDone ? 1 : 0);
        sb.append('|');
        for (int i = 0; i < n; i++) sb.append(i > 0 ? ";" : "").append(ints(farm[i]));
        sb.append('|');
        for (int i = 0; i < n; i++) sb.append(i > 0 ? ";" : "").append(small[i] ? 1 : 0).append(',').append(big[i] ? 1 : 0);
        sb.append('|').append(ints(bank));
        sb.append('|');
        if (hasPending()) sb.append(ints(new int[]{pendFrom, pendTo, pendGive, pendGiveN, pendGet, pendGetN}));
        sb.append('|').append(declined);
        sb.append('|');
        if (rollSeq > 0) {
            sb.append(ints(new int[]{rollSeq, rollBy, face1, face2, foxSaved ? 1 : 0, bearSaved ? 1 : 0, pity ? 1 : 0}));
        }
        sb.append('|').append(ints(gained));
        sb.append('|').append(ints(lost));
        sb.append('|').append(winner);
        sb.append('|');
        for (int i = 0; i < ranking.size(); i++) sb.append(i > 0 ? "," : "").append(ranking.get(i));
        sb.append('|').append(ints(status));
        sb.append('|').append(msg.replace("|", " ").replace("\n", " "));
        sb.append('|').append(tradesOn ? 1 : 0);
        sb.append('|').append(canRoll(viewer) ? 1 : 0);
        sb.append('|').append(canExchange(viewer) ? 1 : 0);
        int[] mt = new int[RATES * 2];
        if (canExchange(viewer)) {
            for (int r = 0; r < RATES; r++) {
                mt[r * 2] = maxTimes(viewer, r, false);
                mt[r * 2 + 1] = maxTimes(viewer, r, true);
            }
        }
        sb.append('|').append(ints(mt));
        sb.append('|').append(hasPending() && pendTo == viewer ? 1 : 0);
        sb.append('|').append(canContinue() ? 1 : 0);
        return sb.toString();
    }

    // ---------- збереження ----------
    private static int[] decI(String s) {
        if (s.length() == 0) return new int[0];
        String[] p = s.split(",", -1);
        int[] r = new int[p.length];
        for (int i = 0; i < p.length; i++) r[i] = Integer.parseInt(p[i]);
        return r;
    }

    public String save() {
        String R = "\u001e";
        StringBuilder sb = new StringBuilder();
        int[] head = {n, phase, turn, turnNo, exDone ? 1 : 0, winner, tradesOn ? 1 : 0, start, rollSeq, rollBy, face1, face2,
            foxSaved ? 1 : 0, bearSaved ? 1 : 0, pity ? 1 : 0, declined, pendFrom, pendTo, pendGive, pendGiveN, pendGet, pendGetN};
        sb.append(ints(head)).append(R);
        for (int i = 0; i < n; i++) sb.append(i > 0 ? "\t" : "").append(names[i]);
        sb.append(R);
        for (int i = 0; i < n; i++) sb.append(i > 0 ? ";" : "").append(ints(farm[i]));
        sb.append(R);
        for (int i = 0; i < n; i++) sb.append(i > 0 ? ";" : "").append(small[i] ? 1 : 0).append(',').append(big[i] ? 1 : 0);
        sb.append(R).append(ints(bank)).append(R).append(ints(status)).append(R);
        for (int i = 0; i < ranking.size(); i++) sb.append(i > 0 ? "," : "").append(ranking.get(i));
        sb.append(R).append(ints(gained)).append(R).append(ints(lost)).append(R);
        sb.append(msg.replace(R, " ").replace("\n", " "));
        return sb.toString();
    }

    public static FEngine restore(String d, Random rnd) {
        String[] s = d.split("\u001e", -1);
        int[] h = decI(s[0]);
        FEngine e = new FEngine(h[0], h[6] == 1, rnd);
        e.phase = h[1];
        e.turn = h[2];
        e.turnNo = h[3];
        e.exDone = h[4] == 1;
        e.winner = h[5];
        e.start = h[7];
        e.rollSeq = h[8];
        e.rollBy = h[9];
        e.face1 = h[10];
        e.face2 = h[11];
        e.foxSaved = h[12] == 1;
        e.bearSaved = h[13] == 1;
        e.pity = h[14] == 1;
        e.declined = h[15];
        e.pendFrom = h[16];
        e.pendTo = h[17];
        e.pendGive = h[18];
        e.pendGiveN = h[19];
        e.pendGet = h[20];
        e.pendGetN = h[21];
        e.names = s[1].split("\t", -1);
        if (e.names.length != e.n) throw new RuntimeException("names");
        String[] fs = s[2].split(";", -1);
        String[] ds = s[3].split(";", -1);
        for (int i = 0; i < e.n; i++) {
            int[] f = decI(fs[i]);
            System.arraycopy(f, 0, e.farm[i], 0, KINDS);
            int[] dg = decI(ds[i]);
            e.small[i] = dg[0] == 1;
            e.big[i] = dg[1] == 1;
        }
        System.arraycopy(decI(s[4]), 0, e.bank, 0, KINDS);
        System.arraycopy(decI(s[5]), 0, e.status, 0, e.n);
        for (int v : decI(s[6])) e.ranking.add(v);
        System.arraycopy(decI(s[7]), 0, e.gained, 0, KINDS);
        System.arraycopy(decI(s[8]), 0, e.lost, 0, KINDS);
        e.msg = s[9];
        return e;
    }
}
