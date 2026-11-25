package com.example.gryntsivgames.sea;

import android.content.Context;
import android.net.wifi.WifiManager;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.InterfaceAddress;
import java.net.NetworkInterface;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.concurrent.ConcurrentHashMap;

public class Discovery {
    public static final int PORT = 45465;

    public static class Room {
        public String name, ip;
        public int port, count, rejoin;
        public boolean inGame;
        public long seen;
    }

    // Хост розсилає оголошення кімнати
    public static class Announcer {
        private volatile boolean run;
        private final String name;
        private final int tcpPort;
        public volatile int count = 1;
        public volatile boolean inGame;
        public volatile int rejoin;

        public Announcer(String name, int tcpPort) {
            this.name = name.replace("|", " ");
            this.tcpPort = tcpPort;
        }

        public void start() {
            run = true;
            Thread t = new Thread(new Runnable() {
                public void run() {
                    DatagramSocket ds = null;
                    try {
                        ds = new DatagramSocket();
                        ds.setBroadcast(true);
                        while (run) {
                            String msg = "SBT1|" + name + "|" + tcpPort + "|" + count + "|" + (inGame ? 1 : 0) + "|" + rejoin;
                            byte[] b = msg.getBytes("UTF-8");
                            ArrayList<InetAddress> targets = broadcastAddresses();
                            for (InetAddress a : targets) {
                                try {
                                    ds.send(new DatagramPacket(b, b.length, a, PORT));
                                } catch (Exception e) {
                                    // ignore
                                }
                            }
                            Thread.sleep(1500);
                        }
                    } catch (Exception e) {
                        // ignore
                    } finally {
                        if (ds != null) ds.close();
                    }
                }
            });
            t.setDaemon(true);
            t.start();
        }

        public void stop() {
            run = false;
        }
    }

    static ArrayList<InetAddress> broadcastAddresses() {
        ArrayList<InetAddress> list = new ArrayList<InetAddress>();
        try {
            Enumeration<NetworkInterface> en = NetworkInterface.getNetworkInterfaces();
            while (en != null && en.hasMoreElements()) {
                NetworkInterface ni = en.nextElement();
                if (!ni.isUp() || ni.isLoopback()) continue;
                for (InterfaceAddress ia : ni.getInterfaceAddresses()) {
                    InetAddress b = ia.getBroadcast();
                    if (b != null && !list.contains(b)) list.add(b);
                }
            }
        } catch (Exception e) {
            // ignore
        }
        try {
            InetAddress all = InetAddress.getByName("255.255.255.255");
            if (!list.contains(all)) list.add(all);
        } catch (Exception e) {
            // ignore
        }
        return list;
    }

    // Гравець слухає оголошення кімнат
    public static class Listener {
        public final ConcurrentHashMap<String, Room> rooms = new ConcurrentHashMap<String, Room>();
        private volatile boolean run;
        private DatagramSocket ds;
        private WifiManager.MulticastLock lock;
        private final Context ctx;

        public Listener(Context ctx) {
            this.ctx = ctx.getApplicationContext();
        }

        public void start() {
            try {
                WifiManager wm = (WifiManager) ctx.getSystemService(Context.WIFI_SERVICE);
                if (wm != null) {
                    lock = wm.createMulticastLock("gryntsivgames");
                    lock.setReferenceCounted(false);
                    lock.acquire();
                }
            } catch (Exception e) {
                // ignore
            }
            run = true;
            Thread t = new Thread(new Runnable() {
                public void run() {
                    try {
                        ds = new DatagramSocket(null);
                        ds.setReuseAddress(true);
                        ds.bind(new InetSocketAddress(PORT));
                        ds.setBroadcast(true);
                        ds.setSoTimeout(1000);
                        byte[] buf = new byte[512];
                        while (run) {
                            try {
                                DatagramPacket p = new DatagramPacket(buf, buf.length);
                                ds.receive(p);
                                String s = new String(p.getData(), 0, p.getLength(), "UTF-8");
                                String[] f = s.split("\\|", -1);
                                if (f.length < 6 || !f[0].equals("SBT1")) continue;
                                Room r = new Room();
                                r.name = f[1];
                                r.ip = p.getAddress().getHostAddress();
                                r.port = Integer.parseInt(f[2]);
                                r.count = Integer.parseInt(f[3]);
                                r.inGame = f[4].equals("1");
                                r.rejoin = Integer.parseInt(f[5].trim());
                                r.seen = System.currentTimeMillis();
                                rooms.put(r.ip + ":" + r.port, r);
                            } catch (java.net.SocketTimeoutException e) {
                                // чекаємо далі
                            }
                        }
                    } catch (Exception e) {
                        // ignore
                    } finally {
                        if (ds != null) ds.close();
                    }
                }
            });
            t.setDaemon(true);
            t.start();
        }

        public void stop() {
            run = false;
            try {
                if (ds != null) ds.close();
            } catch (Exception e) {
                // ignore
            }
            try {
                if (lock != null && lock.isHeld()) lock.release();
            } catch (Exception e) {
                // ignore
            }
        }
    }
}
