package com.example.gryntsivgames.uno;

import android.os.Handler;
import android.os.Looper;

import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Random;

public class Host {
    public static final int PORT = 45454;
    public static final int MAXP = 6;
    private static final String[] BOT_NAMES = {"Бот Макс", "Бот Ліза", "Бот Тимко", "Бот Оля", "Бот Дан", "Бот Яна"};

    private static class P {
        String name = "Гравець";
        int avatar;
        String photo = "-";
        boolean bot;
        int level = 1;
        int delay;
        Link link;
        int total, wins, last;
        String cid = "";
        boolean replaced;
        boolean dnd;
    }

    private final ArrayList<P> ps = new ArrayList<P>();
    private final Handler h = new Handler(Looper.getMainLooper());
    private final Random rnd = new Random();
    private final HashSet<String> banned = new HashSet<String>();
    private final ArrayList<String> hist = new ArrayList<String>();
    private final HashMap<P, Long> pokeAt = new HashMap<P, Long>();
    private ServerSocket server;
    private Discovery.Announcer ann;
    private Engine eng;
    private boolean inGame;
    private boolean stopped;
    private int limit = 300;
    private int starter;
    private int botCounter;
    private int paused = -1;
    private int round;
    private int botDelay = 1800;
    private final P me = new P();
    private final ArrayList<int[]> rounds = new ArrayList<int[]>();
    private final String roomName;
    private boolean voiceBusy;
    private int voiceAcks;
    private final HashSet<P> voiceTo = new HashSet<P>();

    public interface Saver {
        void save(String data);
    }

    private Saver saver;

    public void setSaver(Saver sv) {
        saver = sv;
    }

    private final Runnable voiceFree = new Runnable() {
        public void run() {
            freeVoice();
        }
    };

    private void freeVoice() {
        h.removeCallbacks(voiceFree);
        if (!voiceBusy) return;
        voiceBusy = false;
        voiceAcks = 0;
        for (P q : voiceTo) {
            int qi = ps.indexOf(q);
            if (qi >= 0) sendAll("LIS|" + qi + "|0");
        }
        voiceTo.clear();
        sendAll("VLOCK|0");
    }

    private final Runnable botRun = new Runnable() {
        public void run() {
            botAct();
        }
    };

    public Host(String name, int avatar, String photo, String cid, Link toUi) {
        me.name = clean(name);
        me.avatar = avatar;
        me.photo = (photo == null || photo.length() == 0) ? "-" : photo;
        me.cid = cid == null ? "" : cid;
        me.link = toUi;
        ps.add(me);
        roomName = "Кімната: " + me.name;
    }

    public String start() {
        String err = setWifi(true);
        h.post(new Runnable() {
            public void run() {
                broadcastLobby();
            }
        });
        return err;
    }

    public boolean isWifi() {
        return server != null && !server.isClosed();
    }

    // вмикає або вимикає гру по Wi-Fi (відкритий порт і оголошення кімнати)
    public String setWifi(boolean on) {
        if (!on) {
            if (ann != null) ann.stop();
            ann = null;
            try {
                if (server != null) server.close();
            } catch (Exception e) {
                // ignore
            }
            server = null;
            ArrayList<Link> ls = new ArrayList<Link>();
            for (P q : ps) {
                if (q != me && q.link instanceof Link.SocketLink) ls.add(q.link);
            }
            for (Link l : ls) {
                dropped(l);
                l.close();
            }
            return null;
        }
        if (isWifi()) return null;
        final ServerSocket srv;
        try {
            srv = new ServerSocket();
            srv.setReuseAddress(true);
            srv.bind(new InetSocketAddress(PORT));
        } catch (Exception e) {
            return "Не вдалося відкрити порт " + PORT;
        }
        server = srv;
        Thread t = new Thread(new Runnable() {
            public void run() {
                while (!stopped && !srv.isClosed()) {
                    try {
                        Socket s = srv.accept();
                        s.setTcpNoDelay(true);
                        final Link.SocketLink l = new Link.SocketLink(s);
                        l.readLoop(new Link.LineSink() {
                            public void onLine(final String line) {
                                h.post(new Runnable() {
                                    public void run() {
                                        handle(l, line);
                                    }
                                });
                            }

                            public void onClosed() {
                                h.post(new Runnable() {
                                    public void run() {
                                        dropped(l);
                                    }
                                });
                            }
                        });
                    } catch (Exception e) {
                        if (stopped || srv.isClosed()) break;
                    }
                }
            }
        });
        t.setDaemon(true);
        t.start();
        ann = new Discovery.Announcer(roomName, PORT);
        ann.start();
        h.post(new Runnable() {
            public void run() {
                broadcastLobby();
            }
        });
        return null;
    }

    // ---------- гра через інтернет ----------
    public interface NetCb {
        void onNet(String code, String err);
    }

    private Relay.HostSide relay;
    private final HashMap<String, Link> rlinks = new HashMap<String, Link>();

    private final Runnable netCheck = new Runnable() {
        public void run() {
            if (relay == null || stopped) return;
            long now = System.currentTimeMillis();
            ArrayList<String> dead = new ArrayList<String>();
            for (String c : rlinks.keySet()) {
                if (now - relay.lastRx(c) > 30000) dead.add(c);
            }
            for (String c : dead) {
                Link l = rlinks.remove(c);
                dropped(l);
                l.close();
            }
            h.postDelayed(this, 5000);
        }
    };

    public boolean isInternet() {
        return relay != null;
    }

    public void startInternet(String wantCode, final NetCb cb) {
        if (relay != null) return;
        final Relay.HostSide[] self = new Relay.HostSide[1];
        self[0] = new Relay.HostSide(me.cid, wantCode, new Relay.HostSide.Cb() {
            public void onLine(final String cid, final String line) {
                h.post(new Runnable() {
                    public void run() {
                        if (relay == self[0]) relayLine(cid, line);
                    }
                });
            }

            public void onReady(final String code) {
                h.post(new Runnable() {
                    public void run() {
                        if (relay != self[0]) return;
                        h.removeCallbacks(netCheck);
                        h.postDelayed(netCheck, 5000);
                        cb.onNet(code, null);
                    }
                });
            }

            public void onError(final String msg) {
                h.post(new Runnable() {
                    public void run() {
                        if (relay != self[0]) return;
                        relay = null;
                        cb.onNet(null, msg);
                    }
                });
            }
        });
        relay = self[0];
        relay.start();
    }

    public void stopInternet() {
        if (relay == null) return;
        h.removeCallbacks(netCheck);
        Relay.HostSide r = relay;
        relay = null;
        r.stop();
        ArrayList<Link> ls = new ArrayList<Link>(rlinks.values());
        rlinks.clear();
        for (Link l : ls) {
            dropped(l);
            l.close();
        }
    }

    private void relayLine(String cid, String line) {
        if (stopped || relay == null) return;
        Link l = rlinks.get(cid);
        if (line.startsWith("HELLO|")) {
            if (l != null) {
                dropped(l);
                l.close();
            }
            l = relay.newLink(cid);
            rlinks.put(cid, l);
            handle(l, line);
            return;
        }
        if (l == null) return;
        handle(l, line);
    }

    public void stop() {
        stopped = true;
        h.removeCallbacks(botRun);
        h.removeCallbacks(voiceFree);
        stopInternet();
        setWifi(false);
        for (P p : ps) {
            if (p != me && p.link != null) p.link.close();
        }
    }

    // ---------- команди хоста (локальний гравець) ----------
    public void handleLocal(String line) {
        handle(me.link, line);
    }

    public void setLimit(int v) {
        limit = v;
        broadcastLobby();
    }

    public int getLimit() {
        return limit;
    }

    public void setBotDelay(int ms) {
        botDelay = Math.max(200, ms);
        for (P q : ps) q.delay = 0;
    }

    public void setBotDelayOf(int i, int ms) {
        if (i >= 0 && i < ps.size() && ps.get(i).bot) ps.get(i).delay = Math.max(200, ms);
    }

    public int getBotDelayOf(int i) {
        if (i >= 0 && i < ps.size() && ps.get(i).delay > 0) return ps.get(i).delay;
        return botDelay;
    }

    // Повертає текст-повідомлення або null
    public String addBot() {
        if (ps.size() >= MAXP) return "Максимум гравців — " + MAXP;
        addBotNow();
        if (inGame && eng != null) {
            eng.addSleeper(ps.get(ps.size() - 1).name);
            for (int k = 0; k < rounds.size(); k++) {
                int[] r = rounds.get(k);
                if (r.length < ps.size()) {
                    int[] nr = new int[ps.size()];
                    System.arraycopy(r, 0, nr, 0, r.length);
                    rounds.set(k, nr);
                }
            }
            broadcastLobby();
            broadcastState();
            persist();
            return "Бот спить і прокинеться з наступної партії";
        }
        broadcastLobby();
        return null;
    }

    public int defLevel = 1;

    private int botCat = -1;

    private int freeAvatar() {
        if (botCat < 0) botCat = rnd.nextInt(Avatars.CAT_START.length - 1);
        ArrayList<Integer> ok = new ArrayList<Integer>();
        for (int a = Avatars.CAT_START[botCat]; a < Avatars.CAT_START[botCat + 1]; a++) if (!avatarUsed(a)) ok.add(a);
        if (ok.isEmpty()) {
            for (int a = 0; a < Avatars.TOTAL; a++) if (!avatarUsed(a)) ok.add(a);
        }
        if (ok.isEmpty()) return Avatars.CAT_START[botCat] + rnd.nextInt(Avatars.CAT_START[botCat + 1] - Avatars.CAT_START[botCat]);
        return ok.get(rnd.nextInt(ok.size()));
    }

    private boolean avatarUsed(int a) {
        for (P q : ps) if (q.avatar == a && q.photo.equals("-")) return true;
        for (P q : ps) if (q.avatar == a) return true;
        return false;
    }

    public void setHostDnd(boolean on) {
        if (ps.isEmpty()) return;
        me.dnd = on;
        if (inGame && eng != null) broadcastState();
    }

    private boolean blocked(P from, P to) {
        if (to != null && !to.bot && to.dnd && to != from) {
            if (from.link != null) from.link.send("BLOCK|" + to.name.replace("|", " "));
            return true;
        }
        return false;
    }

    private void addBotNow() {
        P b = new P();
        b.bot = true;
        b.level = defLevel;
        b.name = BOT_NAMES[botCounter++ % BOT_NAMES.length];
        b.avatar = freeAvatar();
        ps.add(b);
    }

    public void setLevel(int i, int lvl) {
        if (i < 0 || i >= ps.size() || lvl < 0 || lvl > 3) return;
        P p = ps.get(i);
        if (!p.bot) return;
        p.level = lvl;
        broadcastLobby();
    }

    // Повертає текст помилки або null
    public String removePlayer(int i) {
        if (i < 0 || i >= ps.size() || ps.get(i) == me) return "Цього гравця не можна видалити";
        P p = ps.get(i);
        if (inGame) {
            if (ps.size() <= 2) return "Має лишитись щонайменше 2 гравці";
            if (eng == null) return "Зачекайте початку наступної партії";
            boolean sl = i < eng.asleep.length && eng.asleep[i];
            if (eng.over && !sl) return "Зачекайте початку наступної партії";
            if (!sl && eng.awake() <= 2) return "Має лишитись щонайменше 2 гравці";
            eng.removePlayer(i);
        }
        ps.remove(i);
        if (p.link != null) {
            p.link.send("KICK");
            p.link.close();
            if (p.cid.length() > 0) banned.add(p.cid);
        }
        for (int k = 0; k < rounds.size(); k++) {
            int[] r = rounds.get(k);
            if (r.length <= i) continue;
            int[] nr = new int[r.length - 1];
            for (int q = 0, w = 0; q < r.length; q++) {
                if (q != i) nr[w++] = r[q];
            }
            rounds.set(k, nr);
        }
        if (paused == i) paused = -1;
        else if (paused > i) paused--;
        if (starter > i) starter--;
        broadcastLobby();
        if (inGame) {
            if (starter >= ps.size()) starter = 0;
            after();
        }
        return null;
    }

    // Міняє гравця i з сусіднім (d = -1 або 1). Повертає текст помилки або null
    public String moveSeat(int i, int d) {
        int j = i + d;
        if (i < 0 || j < 0 || i >= ps.size() || j >= ps.size()) return "Далі нікуди";
        if (inGame && (eng == null || eng.over)) return "Зачекайте початку наступної партії";
        P a = ps.get(i);
        ps.set(i, ps.get(j));
        ps.set(j, a);
        for (int[] r : rounds) {
            if (r.length > i && r.length > j) {
                int t = r[i];
                r[i] = r[j];
                r[j] = t;
            }
        }
        if (inGame) {
            eng.swap(i, j);
            if (paused == i) paused = j;
            else if (paused == j) paused = i;
            if (starter == i) starter = j;
            else if (starter == j) starter = i;
        }
        broadcastLobby();
        if (inGame) {
            broadcastState();
            botTick();
        }
        return null;
    }

    public void startMatch() {
        if (inGame || ps.size() < 2) return;
        inGame = true;
        hist.clear();
        rounds.clear();
        round = 0;
        paused = -1;
        for (P p : ps) {
            p.total = 0;
            p.wins = 0;
            p.last = 0;
        }
        starter = rnd.nextInt(ps.size());
        broadcastLobby();
        for (P p : ps) {
            if (p.link != null) p.link.send("START");
        }
        newRound();
    }

    public void nextRound() {
        if (inGame && eng != null && eng.over) newRound();
    }

    // ---------- допоміжні ----------
    private static String clean(String s) {
        if (s == null) s = "";
        s = s.replace("|", " ").replace("~", " ").replace(";", " ").replace("^", " ")
            .replace("\n", " ").replace("\r", " ").replace("\t", " ").trim();
        if (s.length() > 14) s = s.substring(0, 14);
        if (s.length() == 0) s = "Гравець";
        return s;
    }

    private static int pi(String s) {
        try {
            return Integer.parseInt(s.trim());
        } catch (Exception e) {
            return 0;
        }
    }

    private P find(Link l) {
        for (P p : ps) {
            if (p.link == l) return p;
        }
        return null;
    }

    private void addHist(String s) {
        if (s == null || s.length() == 0) return;
        hist.add(s);
        while (hist.size() > 600) hist.remove(0);
    }

    private void sendAll(String m) {
        for (P q : ps) {
            if (q.link != null) q.link.send(m);
        }
    }

    // ---------- обробка повідомлень ----------
    private void handle(Link l, String line) {
        if (stopped) return;
        String[] f = line.split("\\|", -1);
        P p = find(l);
        if (f[0].equals("HELLO")) {
            if (p == null) hello(l, f);
            return;
        }
        if (p == null) return;
        int i = ps.indexOf(p);
        String c = f[0];
        if (c.equals("BYE")) {
            dropped(l);
            l.close();
        } else if (c.equals("EMO")) {
            if (f.length < 3) return;
            int t = pi(f[1]);
            String m = "EMO|" + i + "|" + t + "|" + pi(f[2]);
            if (t < 0 || t >= ps.size()) {
                for (P q : ps) {
                    if (q.link != null && (q == p || !q.dnd)) q.link.send(m);
                }
            } else {
                P q = ps.get(t);
                if (blocked(p, q)) return;
                if (p.link != null) p.link.send(m);
                if (q != p && q.link != null) q.link.send(m);
            }
        } else if (c.equals("POKE")) {
            if (f.length < 2 || paused >= 0) return;
            int t = pi(f[1]);
            if (t < 0 || t >= ps.size() || t == i) return;
            long now = System.currentTimeMillis();
            Long last = pokeAt.get(p);
            if (last != null && now - last < 1500) return;
            pokeAt.put(p, now);
            P q = ps.get(t);
            if (blocked(p, q)) return;
            if (!q.bot && q.link != null) {
                q.link.send("POKED|" + i);
            }
        } else if (c.equals("VOICE")) {
            if (f.length < 3 || f[2].length() > 90000) return;
            if (paused >= 0) {
                if (p.link != null) p.link.send("VBUSY|2");
                return;
            }
            if (voiceBusy) {
                if (p.link != null) p.link.send("VBUSY|1");
                return;
            }
            int t = pi(f[1]);
            String m = "VOICE|" + i + "|" + t + "|" + f[2];
            ArrayList<P> to = new ArrayList<P>();
            if (t < 0 || t >= ps.size()) {
                for (P q : ps) {
                    if (q != p && !q.bot && !q.dnd && q.link != null) to.add(q);
                }
            } else {
                P q = ps.get(t);
                if (q != p && blocked(p, q)) return;
                if (q != p && !q.bot && q.link != null) to.add(q);
            }
            if (to.isEmpty()) return;
            voiceBusy = true;
            voiceAcks = to.size();
            sendAll("VLOCK|1");
            voiceTo.clear();
            for (P q : to) {
                voiceTo.add(q);
                sendAll("LIS|" + ps.indexOf(q) + "|1");
            }
            for (P q : to) q.link.send(m);
            h.removeCallbacks(voiceFree);
            h.postDelayed(voiceFree, 15000);
        } else if (c.equals("VDONE")) {
            if (voiceBusy && voiceTo.remove(p)) {
                sendAll("LIS|" + i + "|0");
                if (--voiceAcks <= 0) freeVoice();
            }
        } else if (c.equals("DND")) {
            p.dnd = f.length > 1 && f[1].equals("1");
            if (inGame && eng != null) broadcastState();
        } else if (c.equals("NAME")) {
            if (f.length < 4) return;
            p.name = clean(f[1]);
            p.avatar = pi(f[2]);
            String ph = f[3];
            if (ph.length() == 0 || ph.length() > 30000) ph = "-";
            p.photo = ph;
            if (eng != null && i < eng.names.length) eng.names[i] = p.name;
            broadcastLobby();
            if (inGame && eng != null) broadcastState();
            persist();
        } else if (c.equals("PAUSE")) {
            if (inGame && eng != null && !eng.over && paused < 0) {
                paused = i;
                h.removeCallbacks(botRun);
                broadcastState();
                persist();
            }
        } else if (c.equals("RESUME")) {
            if (paused >= 0 && paused == i) {
                paused = -1;
                broadcastState();
                botTick();
            }
        } else if (c.equals("LOGREQ")) {
            StringBuilder sb = new StringBuilder("LOG|");
            for (int k = 0; k < hist.size(); k++) {
                if (k > 0) sb.append('^');
                sb.append(hist.get(k).replace("|", " ").replace("^", " "));
            }
            l.send(sb.toString());
        } else if (c.equals("PLAY")) {
            if (!inGame || eng == null || eng.over || paused >= 0 || f.length < 3) return;
            try {
                Card k = Card.dec(f[1]);
                if (eng.play(i, k, pi(f[2])) == null) {
                    watchPlay(i, k);
                    after();
                }
            } catch (Exception e) {
                // ignore
            }
        } else if (c.equals("DRAW")) {
            if (!inGame || eng == null || eng.over || paused >= 0) return;
            watchDraw(i);
            if (eng.draw(i) == null) after();
        } else if (c.equals("PASS")) {
            if (!inGame || eng == null || eng.over || paused >= 0) return;
            if (eng.pass(i) == null) after();
        }
    }

    private void hello(Link l, String[] f) {
        String cid = f.length > 4 ? f[4].trim() : "";
        if (cid.length() > 0 && banned.contains(cid)) {
            l.send("FULL");
            l.close();
            return;
        }
        String name = clean(f.length > 1 ? f[1] : "");
        int av = f.length > 2 ? pi(f[2]) : 0;
        String ph = f.length > 3 ? f[3] : "-";
        if (ph.length() == 0 || ph.length() > 30000) ph = "-";
        if (inGame) {
            int idx = -1;
            for (int k = 0; k < ps.size(); k++) {
                P q = ps.get(k);
                if (q.replaced && cid.length() > 0 && cid.equals(q.cid)) idx = k;
            }
            if (idx < 0) {
                l.send("FULL");
                l.close();
                return;
            }
            P r = ps.get(idx);
            r.name = name;
            r.avatar = av;
            r.photo = ph;
            r.bot = false;
            r.replaced = false;
            r.link = l;
            if (eng != null && idx < eng.names.length) eng.names[idx] = name;
            broadcastLobby();
            if (eng != null) broadcastState();
            botTick();
            return;
        }
        if (ps.size() >= MAXP) {
            l.send("FULL");
            l.close();
            return;
        }
        P p = new P();
        p.name = name;
        p.avatar = av;
        p.photo = ph;
        p.cid = cid;
        p.link = l;
        ps.add(p);
        broadcastLobby();
    }

    private void dropped(Link l) {
        P p = find(l);
        if (p == null || p == me) return;
        int i = ps.indexOf(p);
        p.link = null;
        if (inGame) {
            if (!p.bot) {
                p.bot = true;
                p.replaced = true;
                p.level = 1;
                p.name = BOT_NAMES[botCounter++ % BOT_NAMES.length];
                p.avatar = freeAvatar();
                p.photo = "-";
                if (eng != null && i < eng.names.length) eng.names[i] = p.name;
            }
            if (paused == i) paused = -1;
            broadcastLobby();
            if (eng != null && !eng.over) {
                broadcastState();
                botTick();
            }
        } else {
            ps.remove(p);
            broadcastLobby();
        }
    }

    private void broadcastLobby() {
        if (ann != null) {
            int rj = 0;
            for (P q : ps) {
                if (q.replaced) rj++;
            }
            ann.count = ps.size();
            ann.inGame = inGame;
            ann.rejoin = rj;
        }
        for (int i = 0; i < ps.size(); i++) {
            P p = ps.get(i);
            if (p.link != null) p.link.send(lobbyMsg(i));
        }
    }

    private String lobbyMsg(int who) {
        StringBuilder sb = new StringBuilder("LOBBY|");
        sb.append(who).append('|').append(limit).append('|').append(inGame ? 1 : 0).append('|');
        for (int i = 0; i < ps.size(); i++) {
            P q = ps.get(i);
            if (i > 0) sb.append(';');
            sb.append(q.name).append('~').append(q.avatar).append('~').append(q.photo).append('~')
                .append(q.bot ? 1 : 0).append('~').append(q.level).append('~').append(q.replaced ? 1 : 0)
                .append('~').append(q == me ? 1 : 0);
        }
        return sb.toString();
    }

    private void newRound() {
        String[] names = new String[ps.size()];
        for (int i = 0; i < names.length; i++) names[i] = ps.get(i).name;
        if (starter < 0 || starter >= names.length) starter = 0;
        eng = new Engine(names, starter);
        lack = new boolean[names.length][4];
        round++;
        paused = -1;
        addHist("— Партія " + round + " — починає " + names[starter]);
        h.removeCallbacks(botRun);
        broadcastState();
        botTick();
        persist();
    }

    private void after() {
        addHist(eng.msg);
        if (eng.over) {
            roundEnd();
            return;
        }
        broadcastState();
        botTick();
        persist();
    }

    private void roundEnd() {
        int n = ps.size();
        boolean matchOver = false;
        ps.get(eng.winner).wins++;
        for (int i = 0; i < n; i++) {
            P q = ps.get(i);
            q.total += eng.roundPts[i];
            q.last = eng.roundPts[i];
            if (q.total >= limit) matchOver = true;
        }
        rounds.add(eng.roundPts.clone());
        starter = eng.winner;
        int loser = -1;
        int max = -1;
        if (matchOver) {
            for (int i = 0; i < n; i++) {
                if (ps.get(i).total > max) {
                    max = ps.get(i).total;
                    loser = i;
                }
            }
            addHist("— Кінець матчу —");
        }
        broadcastState();
        StringBuilder sb = new StringBuilder("RES|");
        sb.append(matchOver ? 1 : 0).append('|').append(loser).append('|').append(eng.winner).append('|');
        for (int i = 0; i < n; i++) {
            if (i > 0) sb.append(',');
            sb.append(eng.roundPts[i]);
        }
        sb.append('|');
        for (int i = 0; i < n; i++) {
            if (i > 0) sb.append(',');
            sb.append(ps.get(i).total);
        }
        sb.append('|');
        for (int i = 0; i < n; i++) {
            if (i > 0) sb.append(',');
            sb.append(ps.get(i).wins);
        }
        sb.append('|').append(roundsStr());
        sendAll(sb.toString());
        if (matchOver) inGame = false;
        h.removeCallbacks(botRun);
        persist();
    }

    private String roundsStr() {
        StringBuilder sb = new StringBuilder();
        for (int k = 0; k < rounds.size(); k++) {
            if (k > 0) sb.append(';');
            int[] r = rounds.get(k);
            for (int q = 0; q < r.length; q++) {
                if (q > 0) sb.append(',');
                sb.append(r[q]);
            }
        }
        return sb.toString();
    }

    private void broadcastState() {
        for (int i = 0; i < ps.size(); i++) {
            P p = ps.get(i);
            if (p.link != null) p.link.send(stateMsg(i));
        }
    }

    private String stateMsg(int i) {
        Engine e = eng;
        int n = ps.size();
        StringBuilder sb = new StringBuilder("STATE|");
        sb.append(e.cur).append('|').append(e.dir).append('|').append(e.top().enc()).append('|');
        sb.append(e.curColor).append('|').append(e.deck.size()).append('|').append(e.pendDraw).append('|');
        sb.append(e.pendSkip ? 1 : 0).append('|').append(e.canDraw(i) ? 1 : 0).append('|');
        sb.append(e.canPass(i) ? 1 : 0).append('|');
        ArrayList<Card> hand = e.hands.get(i);
        for (int j = 0; j < hand.size(); j++) {
            if (j > 0) sb.append(',');
            sb.append(hand.get(j).enc());
        }
        sb.append('|');
        for (int j = 0; j < hand.size(); j++) {
            if (j > 0) sb.append(',');
            sb.append(e.canPlay(i, hand.get(j)) ? 1 : 0);
        }
        sb.append('|');
        for (int j = 0; j < n; j++) {
            if (j > 0) sb.append(',');
            sb.append(e.hands.get(j).size());
        }
        sb.append('|');
        for (int j = 0; j < n; j++) {
            if (j > 0) sb.append(',');
            sb.append(ps.get(j).total);
        }
        sb.append('|').append(e.msg.replace("|", " ")).append('|').append(e.over ? 1 : 0).append('|');
        for (int j = 0; j < n; j++) {
            if (j > 0) sb.append(',');
            sb.append(ps.get(j).wins);
        }
        sb.append('|');
        for (int j = 0; j < n; j++) {
            if (j > 0) sb.append(',');
            sb.append(ps.get(j).last);
        }
        sb.append('|').append(paused).append('|').append(roundsStr());
        sb.append('|');
        for (int j = 0; j < n; j++) {
            if (j > 0) sb.append(',');
            sb.append(j < e.asleep.length && e.asleep[j] ? 1 : 0);
        }
        sb.append('|');
        for (int j = 0; j < n; j++) {
            if (j > 0) sb.append(',');
            sb.append(ps.get(j).dnd ? 1 : 0);
        }
        sb.append('|').append(e.lastP);
        return sb.toString();
    }

    // ---------- збереження гри ----------
    private void persist() {
        if (saver == null || stopped) return;
        if (!inGame || eng == null) {
            saver.save(null);
            return;
        }
        try {
            saver.save(serialize());
        } catch (Exception e) {
            // ignore
        }
    }

    public void forget() {
        if (saver != null) saver.save(null);
    }

    private String serialize() {
        StringBuilder sb = new StringBuilder();
        sb.append(limit).append(',').append(starter).append(',').append(round).append(',').append(botCounter)
            .append(',').append(ps.indexOf(me)).append('\u001f');
        for (int k = 0; k < hist.size(); k++) {
            if (k > 0) sb.append('^');
            sb.append(hist.get(k).replace("^", " ").replace("\u001f", " "));
        }
        sb.append('\u001f').append(roundsStr()).append('\u001f');
        for (int k = 0; k < ps.size(); k++) {
            P q = ps.get(k);
            if (k > 0) sb.append(';');
            sb.append(q.name).append('~').append(q.avatar).append('~').append(q.photo).append('~').append(q.bot ? 1 : 0)
                .append('~').append(q.level).append('~').append(q.replaced ? 1 : 0).append('~').append(q.cid)
                .append('~').append(q.delay).append('~').append(q.total).append('~').append(q.wins).append('~').append(q.last);
        }
        sb.append('\u001f').append(eng.save());
        return sb.toString();
    }

    // Короткий опис збереженої гри для вікна «Продовжити»
    public static String describe(String d) {
        try {
            String[] sec = d.split("\u001f", -1);
            String[] m = sec[0].split(",");
            StringBuilder sb = new StringBuilder("Партія " + m[2] + ", ліміт " + m[0] + " очок\n");
            String[] pl = sec[3].split(";");
            for (int k = 0; k < pl.length; k++) {
                String[] q = pl[k].split("~", -1);
                if (k > 0) sb.append('\n');
                sb.append(q[0]).append(" — ").append(q[8]);
            }
            return sb.toString();
        } catch (Exception e) {
            return "Збережена гра";
        }
    }

    // Відновлює гру. Інші люди стають ботами (можуть повернутись за ключем). Повертає false при помилці
    public boolean loadState(String d) {
        try {
            String[] sec = d.split("\u001f", -1);
            String[] m = sec[0].split(",");
            int meIdx = Integer.parseInt(m[4]);
            ArrayList<P> np = new ArrayList<P>();
            String[] pl = sec[3].split(";");
            for (int k = 0; k < pl.length; k++) {
                String[] q = pl[k].split("~", -1);
                P x = (k == meIdx) ? me : new P();
                if (k != meIdx) {
                    x.name = q[0];
                    x.avatar = Integer.parseInt(q[1]);
                    x.photo = q[2];
                    x.bot = true;
                    x.replaced = !q[3].equals("1");
                    x.level = Integer.parseInt(q[4]);
                    x.cid = q[6];
                    x.delay = Integer.parseInt(q[7]);
                    if (q[3].equals("1")) x.replaced = q[5].equals("1");
                }
                x.total = Integer.parseInt(q[8]);
                x.wins = Integer.parseInt(q[9]);
                x.last = Integer.parseInt(q[10]);
                np.add(x);
            }
            Engine e = Engine.restore(sec[4]);
            if (e.n != np.size()) return false;
            ps.clear();
            ps.addAll(np);
            limit = Integer.parseInt(m[0]);
            starter = Integer.parseInt(m[1]);
            round = Integer.parseInt(m[2]);
            botCounter = Integer.parseInt(m[3]);
            hist.clear();
            if (sec[1].length() > 0) {
                for (String t : sec[1].split("\\^")) hist.add(t);
            }
            rounds.clear();
            if (sec[2].length() > 0) {
                for (String t : sec[2].split(";")) {
                    String[] a = t.split(",");
                    int[] r = new int[a.length];
                    for (int z = 0; z < a.length; z++) r[z] = Integer.parseInt(a[z]);
                    rounds.add(r);
                }
            }
            eng = e;
            for (int k = 0; k < ps.size(); k++) eng.names[k] = ps.get(k).name;
            inGame = true;
            paused = -1;
            return true;
        } catch (Exception ex) {
            return false;
        }
    }

    // Після loadState і start(): показує гру (на паузі), якщо партія ще йшла
    public void resumeLoaded() {
        h.post(new Runnable() {
            public void run() {
                if (eng == null) return;
                broadcastLobby();
                if (eng.over) {
                    newRound();
                } else {
                    paused = ps.indexOf(me);
                    for (P q : ps) {
                        if (q.link != null) q.link.send("START");
                    }
                    broadcastState();
                }
            }
        });
    }

    // ---------- боти ----------
    private void botTick() {
        h.removeCallbacks(botRun);
        if (eng != null && !eng.over && paused < 0 && eng.cur < ps.size() && ps.get(eng.cur).bot) {
            h.postDelayed(botRun, getBotDelayOf(eng.cur));
        }
    }

    private void botAct() {
        if (stopped || eng == null || eng.over || paused >= 0) return;
        int p = eng.cur;
        if (p >= ps.size() || !ps.get(p).bot) return;
        int lvl = ps.get(p).level;
        ArrayList<Card> legal = new ArrayList<Card>();
        for (Card k : eng.hands.get(p)) {
            if (eng.canPlay(p, k)) legal.add(k);
        }
        if (!legal.isEmpty()) {
            if (lvl >= 3 && legalWildOnly(legal) && eng.canPass(p) && eng.hands.get(p).size() > 2
                    && eng.hands.get(eng.nextOf(p)).size() > 2) {
                eng.pass(p);
                after();
                return;
            }
            Card pick = choose(legal, p, lvl);
            int col = pick.v >= Engine.WILD ? bestColor(p, lvl) : pick.c;
            if (eng.play(p, pick, col) == null) watchPlay(p, pick);
        } else if (eng.canDraw(p)) {
            watchDraw(p);
            eng.draw(p);
        } else if (eng.canPass(p)) {
            eng.pass(p);
        } else {
            return;
        }
        after();
    }

    // ---------- пам'ять рівня «Легенда» ----------
    private boolean[][] lack = new boolean[0][4];

    private boolean[] lackOf(int p) {
        if (p >= lack.length) {
            boolean[][] n = new boolean[Math.max(p + 1, ps.size())][4];
            System.arraycopy(lack, 0, n, 0, lack.length);
            lack = n;
        }
        return lack[p];
    }

    // гравець взяв карту не за штрафом: у нього не було ні кольору, ні значення
    private void watchDraw(int p) {
        if (eng == null || eng.pendDraw > 0 || eng.drawn || eng.curColor < 0 || eng.curColor > 3) return;
        lackOf(p)[eng.curColor] = true;
    }

    private void watchPlay(int p, Card k) {
        if (k.v < Engine.WILD && k.c >= 0 && k.c < 4) lackOf(p)[k.c] = false;
    }

    private boolean legalWildOnly(ArrayList<Card> legal) {
        for (Card k : legal) {
            if (k.v != Engine.WILD) return false;
        }
        return true;
    }

    // скільки карт кольору c ще не видно (ні в моїй руці, ні у відбої)
    private int unseen(int p, int c) {
        int total = 25;
        for (Card k : eng.hands.get(p)) {
            if (k.v < Engine.WILD && k.c == c) total--;
        }
        for (Card k : eng.disc) {
            if (k.v < Engine.WILD && k.c == c) total--;
        }
        return Math.max(total, 0);
    }

    private int legendColor(int p) {
        int nxt = eng.nextOf(p);
        int[] cnt = colorCounts(p);
        boolean[] nl = lackOf(nxt);
        int best = 0;
        double bs = -1e9;
        for (int c = 0; c < 4; c++) {
            double sc = cnt[c] * 10 + (nl[c] ? 80 : 0) - unseen(p, c) * 1.2 + rnd.nextDouble();
            if (sc > bs) {
                bs = sc;
                best = c;
            }
        }
        return best;
    }

    private Card legendChoose(ArrayList<Card> legal, int p) {
        int nxt = eng.nextOf(p);
        int nextCnt = eng.hands.get(nxt).size();
        int myCnt = eng.hands.get(p).size();
        boolean[] nl = lackOf(nxt);
        int[] cnt = colorCounts(p);
        boolean two = eng.awake() == 2;
        Card best = null;
        double bs = -1e9;
        for (Card k : legal) {
            double sc;
            if (k.v == Engine.WILD) {
                sc = myCnt <= 2 ? 150 : (nextCnt <= 2 ? 120 : -100);
            } else if (k.v == Engine.D4) {
                sc = nextCnt <= 3 ? 220 : (myCnt <= 2 ? 160 : 20);
            } else {
                sc = cnt[k.c] * 10 + k.points() * 0.6;
                if (nl[k.c]) sc += 60;
                if (k.v == Engine.D2 || k.v == Engine.SKIP) {
                    sc += nextCnt <= 2 ? 200 : 25;
                    if (k.v == Engine.D2 && nextCnt <= 4) sc += 40;
                }
                if (k.v == Engine.REV) sc += two ? 30 : (nextCnt <= 2 ? -10 : 5);
                if (myCnt <= 2) sc += k.points();
            }
            sc += rnd.nextDouble();
            if (sc > bs) {
                bs = sc;
                best = k;
            }
        }
        return best == null ? legal.get(0) : best;
    }

    private int[] colorCounts(int p) {
        int[] cnt = new int[4];
        for (Card k : eng.hands.get(p)) {
            if (k.v < Engine.WILD) cnt[k.c]++;
        }
        return cnt;
    }

    private Card choose(ArrayList<Card> legal, int p, int lvl) {
        if (lvl >= 3) return legendChoose(legal, p);
        if (lvl == 0) return legal.get(rnd.nextInt(legal.size()));
        ArrayList<Card> plain = new ArrayList<Card>();
        for (Card k : legal) {
            if (k.v < Engine.WILD) plain.add(k);
        }
        if (lvl >= 2) {
            int nxt = eng.nextOf(p);
            int nextCnt = eng.hands.get(nxt).size();
            if (nextCnt <= 2) {
                for (Card k : legal) {
                    if (k.v == Engine.D4) return k;
                }
                for (Card k : plain) {
                    if (k.v == Engine.D2 || k.v == Engine.SKIP) return k;
                }
            }
            if (!plain.isEmpty()) {
                int[] cnt = colorCounts(p);
                Card best = null;
                int bs = -1;
                for (Card k : plain) {
                    int sc = cnt[k.c] * 100 + k.points();
                    if (sc > bs) {
                        bs = sc;
                        best = k;
                    }
                }
                return best;
            }
        } else if (!plain.isEmpty()) {
            Card best = null;
            for (Card k : plain) {
                if (best == null || k.points() > best.points() || (k.points() == best.points() && rnd.nextBoolean())) best = k;
            }
            return best;
        }
        for (Card k : legal) {
            if (k.v == Engine.WILD) return k;
        }
        return legal.get(0);
    }

    private int bestColor(int p, int lvl) {
        if (lvl >= 3) return legendColor(p);
        int best = rnd.nextInt(4);
        if (lvl == 0) return best;
        int[] cnt = colorCounts(p);
        int bc = 0;
        for (int c = 0; c < 4; c++) {
            if (cnt[c] > bc) {
                bc = cnt[c];
                best = c;
            }
        }
        return best;
    }
}
