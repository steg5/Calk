package com.example.gryntsivgames.thousand;

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

// Авторитетний хост «1000». Протокол див. у HANDOFF.md (розділ «Протокол гри») і в заголовку TState.
// Потрібні в цьому ж пакеті копії Link, Client, Relay, Discovery (порт 45484, оголошення «THO1», у Relay meta g = "th").
public class THost {
    public static final int PORT = 45484;
    public static final int MAXP = 4;
    private static final String[] BOT_NAMES = {"Бот Макс", "Бот Ліза", "Бот Тимко", "Бот Оля"};

    private static class P {
        String name = "Гравець";
        int avatar;
        String photo = "-";
        boolean bot;
        int level = 1;
        int delay;
        Link link;
        String cid = "";
        boolean replaced;
        boolean dnd;
    }

    public interface Saver {
        void save(String data);
    }

    public interface NetCb {
        void onNet(String code, String err);
    }

    private final ArrayList<P> ps = new ArrayList<P>();
    private final Handler h = new Handler(Looper.getMainLooper());
    private final Random rnd = new Random();
    private final HashSet<String> banned = new HashSet<String>();
    private final ArrayList<String> hist = new ArrayList<String>();
    private final HashMap<P, Long> pokeAt = new HashMap<P, Long>();
    private final HashMap<P, TBot> bots = new HashMap<P, TBot>();
    private final P me = new P();
    private final String roomName;
    private ServerSocket server;
    private Discovery.Announcer ann;
    private TEngine eng;
    private boolean inGame, stopped;
    private boolean marAfter = true, barrelOn = true, boltsOn = true, showTalon = true;
    private int botCounter, paused = -1, botDelay = 1800, botFail;
    private Saver saver;
    private boolean voiceBusy;
    private int voiceAcks;
    private final HashSet<P> voiceTo = new HashSet<P>();
    public int defLevel = 1;

    private final Runnable voiceFree = new Runnable() {
        public void run() {
            freeVoice();
        }
    };

    private final Runnable botRun = new Runnable() {
        public void run() {
            botAct();
        }
    };

    public THost(String name, int avatar, String photo, String cid, Link toUi) {
        me.name = clean(name);
        me.avatar = avatar;
        me.photo = (photo == null || photo.length() == 0) ? "-" : photo;
        me.cid = cid == null ? "" : cid;
        me.link = toUi;
        ps.add(me);
        roomName = "Кімната: " + me.name;
    }

    public void setSaver(Saver sv) {
        saver = sv;
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

    // ---------- налаштування матчу ----------
    public void setMarAfter(boolean on) {
        marAfter = on;
        broadcastLobby();
    }

    public void setBarrel(boolean on) {
        barrelOn = on;
        broadcastLobby();
    }

    public void setBolts(boolean on) {
        boltsOn = on;
        broadcastLobby();
    }

    // можна міняти й під час матчу (лише показ прикупу)
    public void setShowTalon(boolean on) {
        showTalon = on;
        broadcastLobby();
        if (inGame && eng != null) broadcastState();
    }

    public boolean getMarAfter() {
        return marAfter;
    }

    public boolean getBarrel() {
        return barrelOn;
    }

    public boolean getBolts() {
        return boltsOn;
    }

    public boolean getShowTalon() {
        return showTalon;
    }

    private int bits() {
        return (marAfter ? 1 : 0) | (barrelOn ? 2 : 0) | (boltsOn ? 4 : 0) | (showTalon ? 8 : 0);
    }

    // ---------- Wi-Fi ----------
    public boolean isWifi() {
        return server != null && !server.isClosed();
    }

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

    // ---------- інтернет ----------
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

    public void handleLocal(String line) {
        handle(me.link, line);
    }

    // ---------- боти ----------
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

    // повертає текст помилки або null
    public String addBot() {
        if (ps.size() >= MAXP) return "Максимум гравців — " + MAXP;
        if (inGame && eng != null && !eng.matchOver) return "Додати бота можна між матчами";
        addBotNow();
        broadcastLobby();
        return null;
    }

    // персонажі-«казкові» (категорія «Мультяшні»)
    private int freeAvatar() {
        ArrayList<Integer> ok = new ArrayList<Integer>();
        for (int a = Avatars.CAT_START[4]; a < Avatars.CAT_START[5]; a++) if (!avatarUsed(a)) ok.add(a);
        if (ok.isEmpty()) {
            for (int a = 0; a < Avatars.TOTAL; a++) if (!avatarUsed(a)) ok.add(a);
        }
        if (ok.isEmpty()) return Avatars.CAT_START[4] + rnd.nextInt(Avatars.CAT_START[5] - Avatars.CAT_START[4]);
        return ok.get(rnd.nextInt(ok.size()));
    }

    private boolean avatarUsed(int a) {
        for (P q : ps) if (q.avatar == a) return true;
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
        bots.remove(p);
        broadcastLobby();
    }

    private TBot botOf(P p) {
        TBot b = bots.get(p);
        if (b == null || b.level != p.level) {
            b = new TBot(p.level, rnd);
            bots.put(p, b);
        }
        return b;
    }

    public void setHostDnd(boolean on) {
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

    // повертає текст помилки або null; під час матчу видаляти не можна
    public String removePlayer(int i) {
        if (i < 0 || i >= ps.size() || ps.get(i) == me) return "Цього гравця не можна видалити";
        if (inGame && eng != null && !eng.matchOver) return "Видалити можна між матчами";
        P p = ps.get(i);
        ps.remove(i);
        bots.remove(p);
        if (p.link != null) {
            p.link.send("KICK");
            p.link.close();
            if (p.cid.length() > 0) banned.add(p.cid);
        }
        if (inGame) {
            inGame = false;
            eng = null;
            paused = -1;
            h.removeCallbacks(botRun);
        }
        broadcastLobby();
        return null;
    }

    // міняє гравця i з сусіднім (d = -1 або 1)
    public String moveSeat(int i, int d) {
        int j = i + d;
        if (i < 0 || j < 0 || i >= ps.size() || j >= ps.size()) return "Далі нікуди";
        if (inGame && (eng == null || eng.phase != TEngine.END)) return "Змінювати місця можна між партіями";
        if (inGame) {
            String r = eng.swap(i, j);
            if (r != null) return r;
            if (paused == i) paused = j;
            else if (paused == j) paused = i;
        }
        P a = ps.get(i);
        ps.set(i, ps.get(j));
        ps.set(j, a);
        broadcastLobby();
        if (inGame) broadcastState();
        return null;
    }

    // ---------- матч ----------
    public void startMatch() {
        if (ps.size() < 2 || ps.size() > MAXP) return;
        if (inGame && (eng == null || !eng.matchOver)) return;
        String[] names = new String[ps.size()];
        for (int i = 0; i < names.length; i++) names[i] = ps.get(i).name;
        inGame = true;
        hist.clear();
        paused = -1;
        botFail = 0;
        bots.clear();
        eng = new TEngine(names, marAfter, barrelOn, boltsOn, -1, rnd);
        addHist("\u2014 Партія " + eng.roundNo);
        drainLog();
        broadcastLobby();
        for (P p : ps) {
            if (p.link != null) p.link.send("START");
        }
        persist();
        broadcastState();
        botTick();
    }

    public void nextRound() {
        if (!inGame || eng == null || eng.phase != TEngine.END || eng.matchOver) return;
        if (eng.nextRound() != null) return;
        paused = -1;
        addHist("\u2014 Партія " + eng.roundNo);
        drainLog();
        persist();
        broadcastState();
        botTick();
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

    private void drainLog() {
        if (eng == null) return;
        for (String s : eng.log) addHist(s);
        eng.log.clear();
    }

    private void sendAll(String m) {
        for (P q : ps) {
            if (q.link != null) q.link.send(m);
        }
    }

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

    private boolean live() {
        return inGame && eng != null && eng.phase != TEngine.END && paused < 0;
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
            if (!q.bot && q.link != null) q.link.send("POKED|" + i);
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
            if (inGame && eng != null && eng.phase != TEngine.END && paused < 0) {
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
        } else if (c.equals("BID")) {
            if (!live() || f.length < 2) return;
            if (eng.bid(i, pi(f[1])) == null) after();
        } else if (c.equals("BPASS")) {
            if (!live()) return;
            if (eng.passBid(i) == null) after();
        } else if (c.equals("GIVE")) {
            if (!live() || f.length < 3) return;
            try {
                if (eng.give(i, Card.dec(f[1]), pi(f[2])) == null) after();
            } catch (Exception e) {
                // ignore
            }
        } else if (c.equals("FBID")) {
            if (!live() || f.length < 2) return;
            if (eng.finalBid(i, pi(f[1])) == null) after();
        } else if (c.equals("PLAY")) {
            if (!live() || f.length < 3) return;
            try {
                if (eng.play(i, Card.dec(f[1]), f[2].equals("1")) == null) after();
            } catch (Exception e) {
                // ignore
            }
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
            bots.remove(r);
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
                bots.remove(p);
                if (eng != null && i < eng.names.length) eng.names[i] = p.name;
            }
            if (paused == i) paused = -1;
            broadcastLobby();
            if (eng != null && eng.phase != TEngine.END) {
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

    // LOBBY|хто|біти налаштувань|inGame|гравці (ім'я~аватар~фото~бот~рівень~замінений~хост)
    private String lobbyMsg(int who) {
        StringBuilder sb = new StringBuilder("LOBBY|");
        sb.append(who).append('|').append(bits()).append('|').append(inGame ? 1 : 0).append('|');
        for (int i = 0; i < ps.size(); i++) {
            P q = ps.get(i);
            if (i > 0) sb.append(';');
            sb.append(q.name).append('~').append(q.avatar).append('~').append(q.photo).append('~')
                .append(q.bot ? 1 : 0).append('~').append(q.level).append('~').append(q.replaced ? 1 : 0)
                .append('~').append(q == me ? 1 : 0);
        }
        return sb.toString();
    }

    // ---------- хід гри ----------
    private void after() {
        drainLog();
        botFail = 0;
        persist();
        if (eng.phase == TEngine.END) {
            roundEnd();
            return;
        }
        broadcastState();
        botTick();
    }

    private void roundEnd() {
        h.removeCallbacks(botRun);
        broadcastState();
        int n = eng.n;
        int k = eng.rounds.size() - 1;
        int[] d = k >= 0 ? eng.rounds.get(k) : new int[n];
        int[] fl = k >= 0 ? eng.flags.get(k) : new int[n];
        boolean ok = eng.decl >= 0 && eng.raw[eng.decl] >= eng.contract;
        StringBuilder sb = new StringBuilder("RES|");
        sb.append(eng.matchOver ? 1 : 0).append('|').append(eng.winner).append('|').append(eng.decl).append('|')
            .append(eng.contract).append('|').append(ok ? 1 : 0).append('|')
            .append(encI(eng.total)).append('|').append(encI(d)).append('|').append(encI(fl)).append('|')
            .append(encR(eng.rounds)).append('|').append(encR(eng.flags));
        sendAll(sb.toString());
        persist();
    }

    // STATE для гравця i; порядок полів описано в заголовку TState
    private String stateMsg(int i) {
        TEngine e = eng;
        int n = e.n;
        StringBuilder sb = new StringBuilder("STATE");
        sb.append('|').append(e.phase).append('|').append(e.dealer).append('|').append(e.cur()).append('|').append(e.decl)
            .append('|').append(e.contract).append('|').append(e.bid).append('|').append(e.bidder).append('|').append(e.trump)
            .append('|').append(e.leader).append('|').append(e.minBid()).append('|').append(e.finalMin()).append('|').append(e.finalMax())
            .append('|').append(e.canBid(i) ? 1 : 0).append('|').append(e.canPassBid(i) ? 1 : 0);
        ArrayList<Card> hand = e.hands.get(i);
        sb.append('|').append(encC(hand)).append('|');
        for (int j = 0; j < hand.size(); j++) {
            if (j > 0) sb.append(',');
            sb.append(e.legalCard(i, hand.get(j)) ? 1 : 0);
        }
        sb.append('|');
        for (int j = 0; j < hand.size(); j++) {
            if (j > 0) sb.append(',');
            sb.append(e.canMarriage(i, hand.get(j)) ? 1 : 0);
        }
        sb.append('|');
        for (int j = 0; j < n; j++) {
            if (j > 0) sb.append(',');
            sb.append(e.hands.get(j).size());
        }
        sb.append('|').append(encI(e.total)).append('|').append(e.msg.replace("|", " ").replace("\n", " "))
            .append('|').append(e.matchOver ? 1 : 0).append('|').append(e.winner).append('|').append(paused)
            .append('|').append(encR(e.rounds)).append('|').append(encR(e.flags));
        sb.append('|');
        for (int j = 0; j < n; j++) {
            if (j > 0) sb.append(',');
            sb.append(ps.get(j).dnd ? 1 : 0);
        }
        sb.append('|').append(encB(e.passed)).append('|').append(encB(e.onBarrel)).append('|').append(encI(e.barrelRnd))
            .append('|').append(encI(e.boltCnt)).append('|').append(encC(e.trick)).append('|').append(encL(e.trickBy));
        boolean showT = showTalon && (e.phase == TEngine.GIVE || e.phase == TEngine.FINAL);
        sb.append('|').append(showT ? encC(e.talon) : "").append('|').append(encI(e.giveNeed))
            .append('|').append(encC(e.lastTrick)).append('|').append(encL(e.lastTrickBy))
            .append('|').append(e.trickSeq).append('|').append(e.lastWinner)
            .append('|').append(encI(e.tricksWon)).append('|').append(encI(e.marPts)).append('|').append(encI(e.marOwner))
            .append('|').append(e.roundNo).append('|').append(e.phase == TEngine.BID ? e.talon.size() : 0)
            .append('|').append(showTalon ? 1 : 0).append('|').append(e.marAfter ? 1 : 0)
            .append('|').append(e.barrelOn ? 1 : 0).append('|').append(e.boltsOn ? 1 : 0);
        return sb.toString();
    }

    private void broadcastState() {
        if (eng == null) return;
        for (int i = 0; i < ps.size() && i < eng.n; i++) {
            P p = ps.get(i);
            if (p.link != null) p.link.send(stateMsg(i));
        }
    }

    private static String encC(ArrayList<Card> l) {
        StringBuilder sb = new StringBuilder();
        for (int k = 0; k < l.size(); k++) {
            if (k > 0) sb.append(',');
            sb.append(l.get(k).enc());
        }
        return sb.toString();
    }

    private static String encI(int[] a) {
        StringBuilder sb = new StringBuilder();
        for (int k = 0; k < a.length; k++) {
            if (k > 0) sb.append(',');
            sb.append(a[k]);
        }
        return sb.toString();
    }

    private static String encB(boolean[] a) {
        StringBuilder sb = new StringBuilder();
        for (int k = 0; k < a.length; k++) {
            if (k > 0) sb.append(',');
            sb.append(a[k] ? 1 : 0);
        }
        return sb.toString();
    }

    private static String encL(ArrayList<Integer> l) {
        StringBuilder sb = new StringBuilder();
        for (int k = 0; k < l.size(); k++) {
            if (k > 0) sb.append(',');
            sb.append(l.get(k));
        }
        return sb.toString();
    }

    private static String encR(ArrayList<int[]> l) {
        StringBuilder sb = new StringBuilder();
        for (int k = 0; k < l.size(); k++) {
            if (k > 0) sb.append(';');
            sb.append(encI(l.get(k)));
        }
        return sb.toString();
    }

    // ---------- боти: ходи ----------
    private void botTick() {
        h.removeCallbacks(botRun);
        if (eng == null || paused >= 0 || eng.phase == TEngine.END) return;
        int c = eng.cur();
        if (c >= 0 && c < ps.size() && ps.get(c).bot) h.postDelayed(botRun, getBotDelayOf(c));
    }

    private void botAct() {
        if (stopped || eng == null || paused >= 0 || eng.phase == TEngine.END) return;
        int p = eng.cur();
        if (p < 0 || p >= ps.size() || !ps.get(p).bot) return;
        String r = botOf(ps.get(p)).act(eng, p);
        if (r != null) {
            if (++botFail < 3) {
                botTick();
                return;
            }
            fallback(p);
        }
        after();
    }

    // запасний допустимий хід, якщо бот тричі помилився
    private void fallback(int p) {
        botFail = 0;
        if (eng.phase == TEngine.BID) {
            eng.passBid(p);
        } else if (eng.phase == TEngine.GIVE) {
            for (Card c : new ArrayList<Card>(eng.hands.get(p))) {
                for (int t = 0; t < eng.n; t++) {
                    if (eng.canGive(p, c, t)) {
                        eng.give(p, c, t);
                        return;
                    }
                }
            }
        } else if (eng.phase == TEngine.FINAL) {
            eng.finalBid(p, eng.finalMin());
        } else if (eng.phase == TEngine.PLAY) {
            for (Card c : new ArrayList<Card>(eng.hands.get(p))) {
                if (eng.legalCard(p, c)) {
                    eng.play(p, c, false);
                    return;
                }
            }
        }
    }

    // ---------- збереження ----------
    private void persist() {
        if (saver == null || stopped) return;
        if (!inGame || eng == null || eng.matchOver) {
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
        sb.append(bits()).append(',').append(botCounter).append(',').append(ps.indexOf(me)).append('\u001f');
        for (int k = 0; k < hist.size(); k++) {
            if (k > 0) sb.append('^');
            sb.append(hist.get(k).replace("^", " ").replace("\u001f", " "));
        }
        sb.append('\u001f');
        for (int k = 0; k < ps.size(); k++) {
            P q = ps.get(k);
            if (k > 0) sb.append(';');
            sb.append(q.name).append('~').append(q.avatar).append('~').append(q.photo).append('~').append(q.bot ? 1 : 0)
                .append('~').append(q.level).append('~').append(q.replaced ? 1 : 0).append('~').append(q.cid)
                .append('~').append(q.delay);
        }
        sb.append('\u001f').append(eng.save());
        return sb.toString();
    }

    // короткий опис збереженої гри для вікна «Продовжити»
    public static String describe(String d) {
        try {
            String[] sec = d.split("\u001f", -1);
            TEngine e = TEngine.restore(sec[3], new Random());
            StringBuilder sb = new StringBuilder("Партія " + e.roundNo + "\n");
            for (int k = 0; k < e.n; k++) {
                if (k > 0) sb.append('\n');
                sb.append(e.names[k]).append(" — ").append(e.total[k]);
            }
            return sb.toString();
        } catch (Exception ex) {
            return "Збережена гра";
        }
    }

    // інші люди стають ботами (можуть повернутись за ключем); false при помилці
    public boolean loadState(String d) {
        try {
            String[] sec = d.split("\u001f", -1);
            String[] m = sec[0].split(",");
            int b = Integer.parseInt(m[0]);
            int meIdx = Integer.parseInt(m[2]);
            TEngine e = TEngine.restore(sec[3], rnd);
            ArrayList<P> np = new ArrayList<P>();
            String[] pl = sec[2].split(";");
            for (int k = 0; k < pl.length; k++) {
                String[] q = pl[k].split("~", -1);
                P x = (k == meIdx) ? me : new P();
                if (k != meIdx) {
                    x.name = q[0];
                    x.avatar = Integer.parseInt(q[1]);
                    x.photo = q[2];
                    x.bot = true;
                    x.replaced = !q[3].equals("1") || q[5].equals("1");
                    x.level = Integer.parseInt(q[4]);
                    x.cid = q[6];
                    x.delay = Integer.parseInt(q[7]);
                }
                np.add(x);
            }
            if (e.n != np.size()) return false;
            ps.clear();
            ps.addAll(np);
            for (int k = 0; k < e.n; k++) e.names[k] = ps.get(k).name;
            marAfter = (b & 1) != 0;
            barrelOn = (b & 2) != 0;
            boltsOn = (b & 4) != 0;
            showTalon = (b & 8) != 0;
            botCounter = Integer.parseInt(m[1]);
            hist.clear();
            if (sec[1].length() > 0) {
                for (String t : sec[1].split("\\^")) hist.add(t);
            }
            bots.clear();
            eng = e;
            inGame = true;
            paused = -1;
            return true;
        } catch (Exception ex) {
            return false;
        }
    }

    // після loadState і start(): показує гру на паузі
    public void resumeLoaded() {
        h.post(new Runnable() {
            public void run() {
                if (eng == null) return;
                broadcastLobby();
                if (eng.phase == TEngine.END) {
                    if (!eng.matchOver) nextRound();
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
}
