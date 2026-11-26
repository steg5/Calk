package com.example.gryntsivgames.sea;

import android.os.Handler;
import android.os.Looper;

import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;

// Зв'язок: господар (кімната до 6 гравців) і гості. Wi-Fi та інтернет можуть працювати одночасно.
public class Net {
    public static final int PORT = 45464;
    public static final int MAX = 6;

    public interface Cb {
        // господар: pid — номер гостя; гість: pid = -1
        void onConnected(int pid, String nick, int avatar);

        void onLine(int pid, String line);

        void onLost(int pid);

        void onFail(String msg);

        void onRoom(String code);
    }

    private static class Peer {
        int id;
        Link link;
        String cid = "";
        boolean relay;
    }

    private final Handler h = new Handler(Looper.getMainLooper());
    private final Cb cb;
    private volatile boolean closed;
    public boolean host;

    // господар
    private String cid = "";
    private ServerSocket server;
    private Discovery.Announcer ann;
    private Relay.HostSide rel;
    private final ArrayList<Peer> peers = new ArrayList<Peer>();
    private int nextId = 1;
    private boolean wifiOn, netOn;
    private volatile String code = "";
    private volatile boolean netBusy;
    private String wantCode = "";
    private String myNick = "Гравець";
    private int myAvatar;
    private boolean locked;
    private int bots;

    // гість
    private Client client;

    public Net(Cb cb) {
        this.cb = cb;
    }

    public void setInfo(String nick, int avatar) {
        myNick = nick.replace("|", " ");
        myAvatar = avatar;
    }

    public String hello() {
        return "HELLO|" + myNick + "|" + myAvatar;
    }

    public int count() {
        return 1 + peers.size() + bots;
    }

    public void setBots(int b) {
        bots = b;
        refresh();
    }

    public void lock(boolean on) {
        locked = on;
        refresh();
    }

    private void refresh() {
        if (ann != null) {
            ann.count = count();
            ann.inGame = locked;
        }
    }

    public int peerCount() {
        return peers.size();
    }

    public boolean isWifi() {
        return wifiOn;
    }

    public boolean isInternet() {
        return netOn;
    }

    public boolean isBusy() {
        return netBusy;
    }

    public String code() {
        return code;
    }

    // ---------- господар ----------
    public void startHost(String cid, String wantCode) {
        host = true;
        this.cid = cid;
        this.wantCode = wantCode == null ? "" : wantCode;
        setWifi(true);
        tick();
    }

    private void tick() {
        h.postDelayed(new Runnable() {
            public void run() {
                if (closed || !host) return;
                if (rel != null) {
                    for (Peer p : new ArrayList<Peer>(peers)) {
                        if (p.relay && System.currentTimeMillis() - rel.lastRx(p.cid) > 30000) dropPeer(p);
                    }
                }
                tick();
            }
        }, 5000);
    }

    public void setWifi(boolean on) {
        if (!host || closed) return;
        if (on == wifiOn) return;
        if (on) {
            try {
                final ServerSocket ss = new ServerSocket();
                ss.setReuseAddress(true);
                ss.bind(new InetSocketAddress(PORT));
                server = ss;
                Thread t = new Thread(new Runnable() {
                    public void run() {
                        while (!closed && !ss.isClosed()) {
                            try {
                                Socket s = ss.accept();
                                s.setTcpNoDelay(true);
                                accepted(s);
                            } catch (Exception e) {
                                if (ss.isClosed()) return;
                            }
                        }
                    }
                });
                t.setDaemon(true);
                t.start();
                ann = new Discovery.Announcer("Морський бій", PORT);
                ann.count = count();
                ann.inGame = locked;
                ann.start();
                wifiOn = true;
            } catch (Exception e) {
                cb.onFail("Не вдалося відкрити Wi-Fi кімнату");
            }
        } else {
            wifiOn = false;
            try {
                if (server != null) server.close();
            } catch (Exception e) {
                // ignore
            }
            server = null;
            if (ann != null) ann.stop();
            ann = null;
            for (Peer p : new ArrayList<Peer>(peers)) if (!p.relay) dropPeer(p);
        }
    }

    private void accepted(Socket s) {
        final Link.SocketLink l = new Link.SocketLink(s);
        l.readLoop(new Link.LineSink() {
            public void onLine(final String line) {
                h.post(new Runnable() {
                    public void run() {
                        fromPeer(l, "", false, line);
                    }
                });
            }

            public void onClosed() {
                h.post(new Runnable() {
                    public void run() {
                        Peer p = byLink(l);
                        if (p != null) dropPeer(p);
                    }
                });
            }
        });
    }

    private Peer byLink(Link l) {
        for (Peer p : peers) if (p.link == l) return p;
        return null;
    }

    private Peer byCid(String c) {
        for (Peer p : peers) if (p.relay && p.cid.equals(c)) return p;
        return null;
    }

    private void fromPeer(Link l, String c, boolean relay, String line) {
        if (closed) return;
        Peer p = relay ? byCid(c) : byLink(l);
        if (line.startsWith("HELLO")) {
            if (p == null) {
                if (!relay) {
                    // той самий пристрій не може бути двома гравцями; мертві з'єднання прибираємо
                    String ip = l.remote();
                    for (Peer o : new ArrayList<Peer>(peers)) {
                        if (o.relay || o.link == l) continue;
                        if (o.link.dead() || (ip.length() > 0 && ip.equals(o.link.remote()))) dropPeer(o);
                    }
                }
                if (!locked && count() < MAX) {
                    p = new Peer();
                    p.id = nextId++;
                    p.link = l;
                    p.cid = c;
                    p.relay = relay;
                    peers.add(p);
                    refresh();
                    l.send("WELCOME|" + myNick + "|" + myAvatar);
                    String[] f = line.split("\\|", -1);
                    cb.onConnected(p.id, f.length > 1 ? f[1] : "Гравець", f.length > 2 ? num(f[2]) : 0);
                } else {
                    l.send("FULL");
                    l.close();
                }
            }
            return;
        }
        if (p == null) return;
        if (line.equals("BYE")) {
            dropPeer(p);
            return;
        }
        cb.onLine(p.id, line);
    }

    private static int num(String s) {
        try {
            return Integer.parseInt(s.trim());
        } catch (Exception e) {
            return 0;
        }
    }

    private void dropPeer(Peer p) {
        if (!peers.remove(p)) return;
        refresh();
        p.link.close();
        cb.onLost(p.id);
    }

    public void kick(int pid) {
        for (Peer p : new ArrayList<Peer>(peers)) {
            if (p.id == pid) {
                p.link.send("KICK");
                dropPeer(p);
            }
        }
    }

    public void setInternet(boolean on) {
        if (!host || closed) return;
        if (!on) {
            netOn = false;
            code = "";
            if (rel != null) rel.stop();
            rel = null;
            for (Peer p : new ArrayList<Peer>(peers)) if (p.relay) dropPeer(p);
            cb.onRoom("");
            return;
        }
        if (netOn || netBusy) return;
        netBusy = true;
        final Relay.HostSide hs = new Relay.HostSide(cid, wantCode, new Relay.HostSide.Cb() {
            public void onLine(final String c, final String line) {
                h.post(new Runnable() {
                    public void run() {
                        if (rel == null) return;
                        Peer p = byCid(c);
                        Link l = p != null ? p.link : rel.newLink(c);
                        fromPeer(l, c, true, line);
                    }
                });
            }

            public void onReady(final String cd) {
                h.post(new Runnable() {
                    public void run() {
                        netBusy = false;
                        if (closed || rel == null) return;
                        code = cd;
                        netOn = true;
                        cb.onRoom(cd);
                    }
                });
            }

            public void onError(final String msg) {
                h.post(new Runnable() {
                    public void run() {
                        netBusy = false;
                        rel = null;
                        cb.onFail(msg);
                        cb.onRoom("");
                    }
                });
            }
        });
        rel = hs;
        hs.start();
    }

    // ---------- гість ----------
    private Client.Cb guestCb() {
        return new Client.Cb() {
            public void onLine(String line) {
                if (line.startsWith("WELCOME")) {
                    String[] f = line.split("\\|", -1);
                    cb.onConnected(-1, f.length > 1 ? f[1] : "Гравець", f.length > 2 ? num(f[2]) : 0);
                } else if (line.equals("FULL")) {
                    cb.onFail("Кімната заповнена або гра вже йде");
                } else if (line.equals("BYE")) {
                    cb.onLost(-1);
                } else {
                    cb.onLine(-1, line);
                }
            }

            public void onClosed() {
                cb.onLost(-1);
            }

            public void onFail() {
                String m = client == null ? "" : client.failMsg;
                cb.onFail(m.length() > 0 ? m : "Не вдалося підключитися");
            }
        };
    }

    public void joinLan(String ip, int port) {
        host = false;
        client = new Client();
        client.connect(ip, port, hello(), guestCb());
    }

    public void joinCode(String code, String cid) {
        host = false;
        client = new Client();
        client.connectRelay(code, cid, hello(), guestCb());
    }

    // ---------- спільне ----------
    public void sendTo(int pid, String line) {
        for (Peer p : peers) if (p.id == pid) p.link.send(line);
    }

    public void broadcast(String line) {
        for (Peer p : peers) p.link.send(line);
    }

    // гість — до господаря
    public void send(String line) {
        if (!host && client != null) client.send(line);
    }

    public boolean connected() {
        return host ? !peers.isEmpty() : client != null;
    }

    public void close() {
        if (closed) return;
        if (host) broadcast("BYE");
        closed = true;
        try {
            if (server != null) server.close();
        } catch (Exception e) {
            // ignore
        }
        if (ann != null) ann.stop();
        if (rel != null) rel.stop();
        for (Peer p : peers) p.link.close();
        peers.clear();
        if (client != null) client.close();
    }
}
