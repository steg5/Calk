package com.example.gryntsivgames.uno;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Random;

public class Engine {
    public static final int SKIP = 10, REV = 11, D2 = 12, WILD = 13, D4 = 14;
    public static final String[] COLORS = {"червоний", "жовтий", "зелений", "синій"};

    public int n;
    public String[] names;
    public boolean[] asleep;
    public final ArrayList<ArrayList<Card>> hands = new ArrayList<ArrayList<Card>>();
    public final ArrayList<Card> deck = new ArrayList<Card>();
    public final ArrayList<Card> disc = new ArrayList<Card>();
    private final Random rnd = new Random();
    public int cur;
    public int dir = 1;
    public int curColor;
    public int lastP = -1;
    public int pendDraw;
    public boolean pendFour;
    public boolean pendSkip;
    public boolean drawn;
    public boolean over;
    public int winner = -1;
    public int[] roundPts;
    public String msg = "";
    private final StringBuilder log = new StringBuilder();

    public Engine(String[] names, int starter) {
        this.names = names;
        this.n = names.length;
        this.asleep = new boolean[n];
        for (int c = 0; c < 4; c++) {
            deck.add(new Card(c, 0));
            for (int v = 1; v <= 12; v++) {
                deck.add(new Card(c, v));
                deck.add(new Card(c, v));
            }
        }
        for (int i = 0; i < 4; i++) {
            deck.add(new Card(4, WILD));
            deck.add(new Card(4, D4));
        }
        Collections.shuffle(deck, rnd);
        for (int i = 0; i < n; i++) hands.add(new ArrayList<Card>());
        for (int r = 0; r < 7; r++) {
            for (int i = 0; i < n; i++) hands.get(i).add(deck.remove(deck.size() - 1));
        }
        while (true) {
            Card k = deck.remove(deck.size() - 1);
            if (k.v <= 9) {
                disc.add(k);
                curColor = k.c;
                break;
            }
            deck.add(0, k);
        }
        cur = starter;
    }

    public Card top() {
        return disc.get(disc.size() - 1);
    }

    private int next(int p) {
        int q = p;
        for (int i = 0; i < n; i++) {
            q = ((q + dir) % n + n) % n;
            if (!asleep[q]) return q;
        }
        return q;
    }

    public int awake() {
        int a = 0;
        for (int i = 0; i < n; i++) {
            if (!asleep[i]) a++;
        }
        return a;
    }

    // Додає «сплячого» гравця: у поточній партії не бере участі, у наступній прокинеться
    public void addSleeper(String name) {
        String[] nn = new String[n + 1];
        System.arraycopy(names, 0, nn, 0, n);
        nn[n] = name;
        names = nn;
        boolean[] na = new boolean[n + 1];
        System.arraycopy(asleep, 0, na, 0, n);
        na[n] = true;
        asleep = na;
        hands.add(new ArrayList<Card>());
        if (roundPts != null) {
            int[] r = new int[n + 1];
            System.arraycopy(roundPts, 0, r, 0, Math.min(n, roundPts.length));
            roundPts = r;
        }
        n++;
    }

    public int nextOf(int p) {
        return next(p);
    }

    private void say(String s) {
        if (log.length() > 0) log.append(" — ");
        log.append(s);
    }

    private Card draw1() {
        if (deck.isEmpty() && disc.size() > 1) {
            Card t = disc.remove(disc.size() - 1);
            deck.addAll(disc);
            disc.clear();
            disc.add(t);
            Collections.shuffle(deck, rnd);
            say("Колоду перетасовано");
        }
        if (deck.isEmpty()) return null;
        return deck.remove(deck.size() - 1);
    }

    private int give(int p, int cnt) {
        int got = 0;
        for (int i = 0; i < cnt; i++) {
            Card k = draw1();
            if (k == null) break;
            hands.get(p).add(k);
            got++;
        }
        return got;
    }

    private boolean colorMatchExists(int p) {
        Card t = top();
        for (Card k : hands.get(p)) {
            if (k.v < WILD && (k.c == curColor || k.v == t.v)) return true;
        }
        return false;
    }

    public boolean canPlay(int p, Card k) {
        if (over || p != cur) return false;
        if (pendDraw > 0) return k.v == D2 && (!pendFour || k.c == curColor);
        if (pendSkip) return k.v == SKIP && k.c == curColor;
        if (k.v == WILD) return true;
        if (k.v == D4) return !colorMatchExists(p);
        return k.c == curColor || k.v == top().v;
    }

    public boolean anyPlayable(int p) {
        for (Card k : hands.get(p)) {
            if (canPlay(p, k)) return true;
        }
        return false;
    }

    public boolean canDraw(int p) {
        if (over || p != cur) return false;
        if (pendDraw > 0) return true;
        if (pendSkip || drawn) return false;
        return !anyPlayable(p);
    }

    public boolean canPass(int p) {
        if (over || p != cur) return false;
        if (pendDraw > 0) return false;
        return pendSkip || (drawn && (!anyPlayable(p) || wildOnly(p)));
    }

    // єдина підходяща карта — звичайний wild: після добору його можна не кидати, а пасувати
    public boolean wildOnly(int p) {
        if (over || p != cur || pendDraw > 0 || pendSkip) return false;
        boolean w = false;
        for (Card k : hands.get(p)) {
            if (canPlay(p, k)) {
                if (k.v == WILD) w = true;
                else return false;
            }
        }
        return w;
    }

    private boolean hasCounterD2(int p) {
        for (Card k : hands.get(p)) {
            if (k.v == D2 && (!pendFour || k.c == curColor)) return true;
        }
        return false;
    }

    private boolean hasCounterSkip(int p) {
        for (Card k : hands.get(p)) {
            if (k.v == SKIP && k.c == curColor) return true;
        }
        return false;
    }

    // Автоматично розв'язує штраф або пропуск, якщо в гравця немає чим відповісти
    private void settle() {
        for (int guard = 0; guard < 20; guard++) {
            if (pendDraw > 0) {
                if (hasCounterD2(cur)) return;
                int got = give(cur, pendDraw);
                say(names[cur] + " добирає " + got + " і пропускає хід");
                pendDraw = 0;
                pendFour = false;
                drawn = false;
                cur = next(cur);
            } else if (pendSkip) {
                if (hasCounterSkip(cur)) return;
                say(names[cur] + " пропускає хід");
                pendSkip = false;
                drawn = false;
                cur = next(cur);
            } else {
                return;
            }
        }
    }

    public static String label(Card k) {
        if (k.v <= 9) return String.valueOf(k.v);
        if (k.v == SKIP) return "Skip";
        if (k.v == REV) return "Reverse";
        if (k.v == D2) return "Draw two";
        if (k.v == WILD) return "Wild";
        return "Draw four";
    }

    public String play(int p, Card k, int color) {
        if (!canPlay(p, k)) return "illegal";
        ArrayList<Card> h = hands.get(p);
        int idx = -1;
        for (int i = 0; i < h.size(); i++) {
            if (h.get(i).same(k)) {
                idx = i;
                break;
            }
        }
        if (idx < 0) return "nocard";
        if (k.v >= WILD && (color < 0 || color > 3)) return "nocolor";
        log.setLength(0);
        boolean counter = (pendSkip && k.v == SKIP) || (pendDraw > 0 && (k.v == D2 || k.v == D4));
        Card played = h.remove(idx);
        disc.add(played);
        lastP = p;
        curColor = k.v >= WILD ? color : k.c;
        drawn = false;
        String s = names[p] + ": " + label(k);
        if (k.v >= WILD && !h.isEmpty()) s += " (" + COLORS[color] + ")";
        if (counter) s += " \u2014 перебиває";
        say(s);
        if (k.v == D2) {
            // штраф не сумується: завжди добирають лише 2 карти
            pendDraw = 2;
            pendFour = false;
        } else if (k.v == D4) {
            pendDraw = 4;
            pendFour = true;
        } else if (k.v == SKIP) {
            pendSkip = true;
        } else if (k.v == REV) {
            dir = -dir;
        }
        if (h.isEmpty()) {
            over = true;
            winner = p;
            if (pendDraw > 0) {
                int q = next(p);
                int got = give(q, pendDraw);
                say(names[q] + " добирає " + got);
                pendDraw = 0;
            }
            pendSkip = false;
            roundPts = new int[n];
            for (int i = 0; i < n; i++) {
                int sum = 0;
                for (Card c : hands.get(i)) sum += c.points();
                roundPts[i] = sum;
            }
            roundPts[p] = 0;
            say(names[p] + " виграє партію");
            msg = log.toString();
            return null;
        }
        if (k.v == REV && awake() == 2) {
            say("ходить ще раз");
        } else {
            cur = next(p);
        }
        settle();
        msg = log.toString();
        return null;
    }

    public String draw(int p) {
        if (!canDraw(p)) return "illegal";
        log.setLength(0);
        if (pendDraw > 0) {
            int got = give(p, pendDraw);
            say(names[p] + " добирає " + got + " і пропускає хід");
            pendDraw = 0;
            pendFour = false;
            drawn = false;
            cur = next(p);
            settle();
        } else {
            give(p, 1);
            drawn = true;
            say(names[p] + " добирає карту");
        }
        msg = log.toString();
        return null;
    }

    public String pass(int p) {
        if (!canPass(p)) return "illegal";
        log.setLength(0);
        say(names[p] + (pendSkip ? " пропускає хід" : " пасує"));
        pendSkip = false;
        drawn = false;
        cur = next(p);
        settle();
        msg = log.toString();
        return null;
    }

    // Міняє місцями двох гравців (руки йдуть разом з гравцями)
    public void swap(int i, int j) {
        if (i == j || i < 0 || j < 0 || i >= n || j >= n) return;
        Collections.swap(hands, i, j);
        boolean ta = asleep[i];
        asleep[i] = asleep[j];
        asleep[j] = ta;
        String t = names[i];
        names[i] = names[j];
        names[j] = t;
        if (cur == i) cur = j;
        else if (cur == j) cur = i;
        if (winner == i) winner = j;
        else if (winner == j) winner = i;
        if (roundPts != null) {
            int x = roundPts[i];
            roundPts[i] = roundPts[j];
            roundPts[j] = x;
        }
    }

    // Прибирає гравця з партії, його карти повертаються в колоду
    public void removePlayer(int i) {
        if (i < 0 || i >= n || n <= 2) return;
        if (!asleep[i] && awake() <= 2) return;
        log.setLength(0);
        deck.addAll(hands.remove(i));
        Collections.shuffle(deck, rnd);
        String removed = names[i];
        String[] nn = new String[n - 1];
        for (int a = 0, b = 0; a < n; a++) {
            if (a != i) nn[b++] = names[a];
        }
        names = nn;
        boolean[] na = new boolean[n - 1];
        for (int a = 0, b = 0; a < n; a++) {
            if (a != i) na[b++] = asleep[a];
        }
        asleep = na;
        if (roundPts != null && roundPts.length == n) {
            int[] r = new int[n - 1];
            for (int a = 0, b = 0; a < n; a++) {
                if (a != i) r[b++] = roundPts[a];
            }
            roundPts = r;
        }
        n--;
        if (winner > i) winner--;
        say(removed + " виходить з гри");
        if (cur == i) {
            cur = dir > 0 ? i % n : ((i - 1) % n + n) % n;
            if (asleep[cur]) cur = next(cur);
            drawn = false;
            settle();
        } else if (cur > i) {
            cur--;
        }
        msg = log.toString();
    }

    private Engine() {
    }

    private static String cards(ArrayList<Card> l) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < l.size(); i++) {
            if (i > 0) sb.append(',');
            sb.append(l.get(i).enc());
        }
        return sb.toString();
    }

    private static void fillCards(ArrayList<Card> l, String s) {
        if (s.length() == 0) return;
        for (String t : s.split(",")) l.add(Card.dec(t));
    }

    public String save() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < n; i++) {
            if (i > 0) sb.append('\t');
            sb.append(names[i]);
        }
        sb.append('\n').append(cur).append('\n').append(dir).append('\n').append(curColor).append('\n').append(pendDraw)
            .append('\n').append(pendFour ? 1 : 0).append('\n').append(pendSkip ? 1 : 0).append('\n').append(drawn ? 1 : 0)
            .append('\n').append(over ? 1 : 0).append('\n').append(winner).append('\n');
        for (int i = 0; roundPts != null && i < roundPts.length; i++) {
            if (i > 0) sb.append(',');
            sb.append(roundPts[i]);
        }
        sb.append('\n').append(msg.replace('\n', ' ')).append('\n').append(cards(deck)).append('\n').append(cards(disc));
        for (int i = 0; i < n; i++) sb.append('\n').append(cards(hands.get(i)));
        sb.append('\n');
        for (int i = 0; i < n; i++) sb.append(asleep[i] ? '1' : '0');
        return sb.toString();
    }

    public static Engine restore(String data) {
        String[] f = data.split("\n", -1);
        Engine e = new Engine();
        e.names = f[0].split("\t", -1);
        e.n = e.names.length;
        e.cur = Integer.parseInt(f[1]);
        e.dir = Integer.parseInt(f[2]);
        e.curColor = Integer.parseInt(f[3]);
        e.pendDraw = Integer.parseInt(f[4]);
        e.pendFour = f[5].equals("1");
        e.pendSkip = f[6].equals("1");
        e.drawn = f[7].equals("1");
        e.over = f[8].equals("1");
        e.winner = Integer.parseInt(f[9]);
        if (f[10].length() > 0) {
            String[] r = f[10].split(",");
            e.roundPts = new int[r.length];
            for (int i = 0; i < r.length; i++) e.roundPts[i] = Integer.parseInt(r[i]);
        }
        e.msg = f[11];
        fillCards(e.deck, f[12]);
        fillCards(e.disc, f[13]);
        for (int i = 0; i < e.n; i++) {
            ArrayList<Card> h = new ArrayList<Card>();
            fillCards(h, f[14 + i]);
            e.hands.add(h);
        }
        e.asleep = new boolean[e.n];
        if (f.length > 14 + e.n) {
            String a = f[14 + e.n];
            for (int i = 0; i < e.n && i < a.length(); i++) e.asleep[i] = a.charAt(i) == '1';
        }
        return e;
    }
}
