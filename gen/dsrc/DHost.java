package com.example.gryntsivgames.durak;

import com.example.gryntsivgames.uno.Avatars;
import com.example.gryntsivgames.uno.Card;

import android.os.Handler;
import android.os.Looper;

import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Random;

public class DHost {
    public static final int PORT = 45474;
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
    private DEngine eng;
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
    public interface Saver {
        void save(String data);
    }

    private Saver saver;

    private boolean voiceBusy;
    private int voiceAcks;
    private final HashSet<P> voiceTo = new HashSet<P>();

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

    public void setSaver(Saver sv) {
        saver = sv;
    }

    private final Runnable botRun = new Runnable() {
        public void run() {
            botAct();
        }
    };

    public DHost(String name, int avatar, String photo, String cid, Link toUi) {
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
        if (inGame) return "Додати бота можна між партіями";
        broadcastLobby();
        return null;
    }

    public int defLevel = 1;

    private int freeAvatar() {
        ArrayList<Integer> ok = new ArrayList<Integer>();
        for (int a = Avatars.CAT_START[2]; a < Avatars.CAT_START[3]; a++) if (!avatarUsed(a)) ok.add(a);
        if (ok.isEmpty()) {
            for (int a = 0; a < Avatars.TOTAL; a++) if (!avatarUsed(a)) ok.add(a);
        }
        if (ok.isEmpty()) return Avatars.CAT_START[2] + rnd.nextInt(Avatars.CAT_START[3] - Avatars.CAT_START[2]);
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
            if (eng == null || !eng.over) return "Зачекайте кінця партії";
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
        if (starter >= ps.size()) starter = -1;
        return null;
    }

    // Міняє гравця i з сусіднім (d = -1 або 1). Повертає текст помилки або null
    public String moveSeat(int i, int d) {
        int j = i + d;
        if (i < 0 || j < 0 || i >= ps.size() || j >= ps.size()) return "Далі нікуди";
        if (inGame && (eng == null || !eng.over)) return "Змінювати місця можна між партіями";
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
            if (paused == i) paused = j;
            else if (paused == j) paused = i;
            if (starter == i) starter = j;
            else if (starter == j) starter = i;
        }
        broadcastLobby();
        return null;
    }

    private boolean transfer;

    public void setTransfer(boolean on) {
        transfer = on;
        broadcastLobby();
    }

    private boolean ordered;

    public void setOrdered(boolean on) {
        ordered = on;
        if (eng != null) eng.setOrdered(on);
    }

    public boolean getTransfer() {
        return transfer;
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
        starter = -1;
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
        } else if (c.equals("LOGREQ")) {
            StringBuilder sb = new StringBuilder("LOG|");
            for (int k = 0; k < hist.size(); k++) {
                if (k > 0) sb.append('^');
                sb.append(hist.get(k).replace("|", " ").replace("^", " "));
            }
            l.send(sb.toString());
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
        } else if (c.equals("PLAY")) {
            if (!inGame || eng == null || eng.over || paused >= 0 || f.length < 3) return;
            try {
                if (eng.attack(i, Card.dec(f[1])) == null) after();
            } catch (Exception e) {
                // ignore
            }
        } else if (c.equals("DEF")) {
            if (!inGame || eng == null || eng.over || paused >= 0 || f.length < 3) return;
            try {
                if (eng.defend(i, Card.dec(f[1]), pi(f[2])) == null) after();
            } catch (Exception e) {
                // ignore
            }
        } else if (c.equals("TRF")) {
            if (!inGame || eng == null || eng.over || paused >= 0 || f.length < 2) return;
            try {
                if (eng.transfer(i, Card.dec(f[1])) == null) after();
            } catch (Exception e) {
                // ignore
            }
        } else if (c.equals("TAKE")) {
            if (!inGame || eng == null || eng.over || paused >= 0) return;
            if (eng.take(i) == null) after();
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
        sb.append(who).append('|').append(transfer ? 1 : 0).append('|').append(inGame ? 1 : 0).append('|');
        for (int i = 0; i < ps.size(); i++) {
            P q = ps.get(i);
            if (i > 0) sb.append(';');
            sb.append(q.name).append('~').append(q.avatar).append('~').append(q.photo).append('~')
                .append(q.bot ? 1 : 0).append('~').append(q.level).append('~').append(q.replaced ? 1 : 0)
                .append('~').append(q == me ? 1 : 0);
        }
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
        sb.append(transfer ? 1 : 0).append(',').append(starter).append(',').append(round).append(',').append(botCounter)
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

    public static String describe(String d) {
        try {
            String[] sec = d.split("\u001f", -1);
            String[] m = sec[0].split(",");
            StringBuilder sb = new StringBuilder("Партія " + m[2] + (m[0].equals("1") ? ", перевідний" : "") + "\n");
            String[] pl = sec[3].split(";");
            for (int k = 0; k < pl.length; k++) {
                String[] q = pl[k].split("~", -1);
                if (k > 0) sb.append('\n');
                sb.append(q[0]).append(" — поразок: ").append(q[8]);
            }
            return sb.toString();
        } catch (Exception e) {
            return "Збережена гра";
        }
    }

    // Інші люди стають ботами (можуть повернутись за ключем). false при помилці
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
            String[] names = new String[np.size()];
            for (int k = 0; k < names.length; k++) names[k] = np.get(k).name;
            DEngine e = DEngine.restore(sec[4], names, rnd);
            if (e.n != np.size()) return false;
            ps.clear();
            ps.addAll(np);
            transfer = m[0].equals("1");
            ordered = e.ordered;
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
            inGame = true;
            paused = -1;
            return true;
        } catch (Exception ex) {
            return false;
        }
    }

    // Після loadState і start(): показує гру на паузі
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

    private void newRound() {
        String[] names = new String[ps.size()];
        for (int i = 0; i < names.length; i++) names[i] = ps.get(i).name;
        if (starter >= names.length) starter = -1;
        eng = new DEngine(names, starter, Math.max(0, ps.indexOf(me)), transfer, rnd);
        eng.setOrdered(ordered);
        for (DBot b : bots.values()) b.reset();
        round++;
        addHist("\u2014 Партія " + round);
        addHist(eng.msg);
        persist();
        paused = -1;
        h.removeCallbacks(botRun);
        broadcastState();
        botTick();
    }

    private void after() {
        for (String lg : eng.log) addHist(lg);
        eng.log.clear();
        persist();
        if (eng.bouts != lastBouts) {
            lastBouts = eng.bouts;
        }
        if (eng.over) {
            roundEnd();
            return;
        }
        broadcastState();
        botTick();
    }

    private int lastBouts;
    private final HashMap<P, DBot> bots = new HashMap<P, DBot>();

    private void roundEnd() {
        int n = ps.size();
        int[] r = new int[n];
        if (eng.fool >= 0) {
            r[eng.fool] = 1;
            ps.get(eng.fool).total++;
            // наступний ходить на того, хто програв: перший той, хто сидить перед ним
            starter = (eng.fool + n - 1) % n;
        } else {
            starter = -1;
        }
        for (int i = 0; i < n; i++) ps.get(i).last = r[i];
        rounds.add(r);
        broadcastState();
        StringBuilder sb = new StringBuilder("RES|0|").append(eng.fool).append('|').append(eng.fool).append('|');
        for (int i = 0; i < n; i++) {
            if (i > 0) sb.append(',');
            sb.append(r[i]);
        }
        sb.append('|');
        for (int i = 0; i < n; i++) {
            if (i > 0) sb.append(',');
            sb.append(ps.get(i).total);
        }
        sb.append("|0|").append(roundsStr());
        sendAll(sb.toString());
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

    private static String encList(ArrayList<Card> l) {
        StringBuilder sb = new StringBuilder();
        for (int k = 0; k < l.size(); k++) {
            if (k > 0) sb.append(',');
            sb.append(l.get(k) == null ? "-" : l.get(k).enc());
        }
        return sb.toString();
    }

    private String stateMsg(int i) {
        DEngine e = eng;
        int n = ps.size();
        StringBuilder sb = new StringBuilder("STATE|");
        sb.append(e.att).append('|').append(e.def).append('|').append(e.taking ? 1 : 0).append('|').append(e.ts).append('|');
        sb.append(e.trumpCard.enc()).append('|').append(e.deck.size()).append('|');
        sb.append(encList(e.atk)).append('|').append(encList(e.dfn)).append('|');
        sb.append(e.canTake(i) ? 1 : 0).append('|').append(e.canPass(i) ? 1 : 0).append('|');
        ArrayList<Card> hand = e.hands.get(i);
        sb.append(encList(hand)).append('|');
        for (int j = 0; j < hand.size(); j++) {
            if (j > 0) sb.append(',');
            sb.append(e.legalCard(i, hand.get(j)) ? 1 : 0);
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
        sb.append('|').append(e.msg.replace("|", " ")).append('|').append(e.over ? 1 : 0).append('|').append(e.fool);
        sb.append('|').append(paused).append('|').append(roundsStr()).append('|');
        for (int j = 0; j < n; j++) {
            if (j > 0) sb.append(',');
            sb.append(ps.get(j).dnd ? 1 : 0);
        }
        sb.append('|').append(e.cur()).append('|').append(e.transferOn ? 1 : 0).append('|');
        for (int j = 0; j < n; j++) {
            if (j > 0) sb.append(',');
            sb.append(e.out[j] ? 1 : 0);
        }
        sb.append('|').append(e.canTransferAny(i) ? 1 : 0);
        sb.append('|').append(e.gSeq).append('|').append(encCards(e.gAtk)).append('|').append(encCards(e.gDfn));
        sb.append('|').append(e.ordered ? 1 : 0);
        sb.append('|').append(encInts(e.own)).append('|').append(encInts(e.gOwn));
        return sb.toString();
    }

    private static String encInts(ArrayList<Integer> l) {
        StringBuilder sb = new StringBuilder();
        for (int k = 0; k < l.size(); k++) {
            if (k > 0) sb.append(',');
            sb.append(l.get(k));
        }
        return sb.toString();
    }

    private static String encCards(ArrayList<Card> l) {
        StringBuilder sb = new StringBuilder();
        for (int k = 0; k < l.size(); k++) {
            if (k > 0) sb.append(',');
            sb.append(l.get(k) == null ? "-" : l.get(k).enc());
        }
        return sb.toString();
    }

    // ---------- боти ----------
    private void botTick() {
        h.removeCallbacks(botRun);
        if (eng == null || eng.over || paused >= 0) return;
        for (int k = 0; k < ps.size(); k++) {
            if (ps.get(k).bot && botWants(k)) {
                h.postDelayed(botRun, getBotDelayOf(k));
                return;
            }
        }
    }

    private boolean botWants(int k) {
        DBot b = botOf(ps.get(k));
        return b.choose(eng, k, ps.get(k).level).type != DBot.NONE;
    }

    private DBot botOf(P p) {
        DBot b = bots.get(p);
        if (b == null) {
            b = new DBot();
            bots.put(p, b);
        }
        return b;
    }

    private void botAct() {
        if (stopped || eng == null || eng.over || paused >= 0) return;
        int n = ps.size();
        int start = Math.max(0, eng.cur());
        for (int d = 0; d < n; d++) {
            int k = (start + d) % n;
            P p = ps.get(k);
            if (!p.bot) continue;
            DBot.Act a = botOf(p).choose(eng, k, p.level);
            String r = null;
            if (a.type == DBot.THROW) r = eng.attack(k, a.card);
            else if (a.type == DBot.DEFEND) r = eng.defend(k, a.card, a.target);
            else if (a.type == DBot.TRANSFER) r = eng.transfer(k, a.card);
            else if (a.type == DBot.TAKE) r = eng.take(k);
            else if (a.type == DBot.PASS) r = eng.pass(k);
            else continue;
            if (r == null) {
                after();
                return;
            }
        }
        botTick();
    }
}
