package com.example.gryntsivgames.durak;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Random;
import java.util.TreeMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

// Гра через інтернет: кімната живе у Firebase Realtime Database (REST + SSE, без SDK).
// rooms/КОД/meta, rooms/КОД/up/CID/КЛЮЧ (клієнт -> господар), rooms/КОД/down/CID/КЛЮЧ (господар -> клієнт)
public class Relay {
    public static String BASE = "https://uno-gryntsiv-default-rtdb.europe-west1.firebasedatabase.app";
    public static final String ALPHA = "0123456789";
    public static final String NOCON = "Не вдалося зв'язатися з Firebase. Перевірте інтернет";
    private static final String END = "\u0000END";
    private static long seq;

    public static String newCode() {
        Random r = new Random();
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 5; i++) sb.append(ALPHA.charAt(r.nextInt(ALPHA.length())));
        return sb.toString();
    }

    public static String cleanCode(String s) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char c = Character.toUpperCase(s.charAt(i));
            if (ALPHA.indexOf(c) >= 0) sb.append(c);
        }
        return sb.toString();
    }

    private static synchronized String newKey() {
        seq++;
        String a = String.valueOf(System.currentTimeMillis());
        String b = String.valueOf(seq % 1000000);
        StringBuilder sb = new StringBuilder();
        for (int i = a.length(); i < 13; i++) sb.append('0');
        sb.append(a);
        for (int i = b.length(); i < 6; i++) sb.append('0');
        sb.append(b);
        return sb.toString();
    }

    // ---------- JSON ----------
    public static String quote(String s) {
        StringBuilder sb = new StringBuilder("\"");
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '"') sb.append("\\\"");
            else if (c == '\\') sb.append("\\\\");
            else if (c == '\n') sb.append("\\n");
            else if (c == '\r') sb.append("\\r");
            else if (c == '\t') sb.append("\\t");
            else if (c < 0x20) sb.append(' ');
            else sb.append(c);
        }
        return sb.append('"').toString();
    }

    private static class Parser {
        final String s;
        int i;

        Parser(String s) {
            this.s = s;
        }

        void ws() {
            while (i < s.length() && s.charAt(i) <= ' ') i++;
        }

        Object value() {
            ws();
            if (i >= s.length()) return null;
            char c = s.charAt(i);
            if (c == '{') {
                i++;
                TreeMap<String, Object> m = new TreeMap<String, Object>();
                ws();
                if (i < s.length() && s.charAt(i) == '}') {
                    i++;
                    return m;
                }
                while (i < s.length()) {
                    ws();
                    String k = str();
                    ws();
                    if (i < s.length() && s.charAt(i) == ':') i++;
                    Object v = value();
                    m.put(k, v);
                    ws();
                    if (i < s.length() && s.charAt(i) == ',') {
                        i++;
                        continue;
                    }
                    if (i < s.length() && s.charAt(i) == '}') i++;
                    break;
                }
                return m;
            }
            if (c == '"') return str();
            if (c == '[') {
                int depth = 0;
                while (i < s.length()) {
                    char d = s.charAt(i++);
                    if (d == '[') depth++;
                    else if (d == ']' && --depth == 0) break;
                }
                return null;
            }
            int st = i;
            while (i < s.length() && ",}] \n\r\t".indexOf(s.charAt(i)) < 0) i++;
            String t = s.substring(st, i);
            if (t.equals("null")) return null;
            return t;
        }

        String str() {
            if (i >= s.length() || s.charAt(i) != '"') return "";
            i++;
            StringBuilder sb = new StringBuilder();
            while (i < s.length()) {
                char c = s.charAt(i++);
                if (c == '"') break;
                if (c == '\\' && i < s.length()) {
                    char d = s.charAt(i++);
                    if (d == 'n') sb.append('\n');
                    else if (d == 'r') sb.append('\r');
                    else if (d == 't') sb.append('\t');
                    else if (d == 'b') sb.append('\b');
                    else if (d == 'f') sb.append('\f');
                    else if (d == 'u' && i + 4 <= s.length()) {
                        try {
                            sb.append((char) Integer.parseInt(s.substring(i, i + 4), 16));
                        } catch (Exception e) {
                            // ignore
                        }
                        i += 4;
                    } else sb.append(d);
                } else {
                    sb.append(c);
                }
            }
            return sb.toString();
        }
    }

    public static Object parse(String s) {
        if (s == null) return null;
        try {
            return new Parser(s).value();
        } catch (Exception e) {
            return null;
        }
    }

    // ---------- HTTP ----------
    // Повертає тіло відповіді або null, якщо запит не вдався
    public static String http(String method, String path, String body) {
        try {
            URL u = new URL(BASE + "/" + path + ".json");
            HttpURLConnection c = (HttpURLConnection) u.openConnection();
            c.setConnectTimeout(8000);
            c.setReadTimeout(15000);
            c.setRequestMethod(method);
            if (body != null) {
                byte[] b = body.getBytes("UTF-8");
                c.setDoOutput(true);
                c.setRequestProperty("Content-Type", "application/json; charset=utf-8");
                c.setFixedLengthStreamingMode(b.length);
                OutputStream o = c.getOutputStream();
                o.write(b);
                o.close();
            }
            int code = c.getResponseCode();
            if (code >= 300) {
                try {
                    c.getErrorStream().close();
                } catch (Exception e) {
                    // ignore
                }
                return null;
            }
            InputStream in = c.getInputStream();
            ByteArrayOutputStream bo = new ByteArrayOutputStream();
            byte[] buf = new byte[4096];
            int n;
            while ((n = in.read(buf)) > 0) bo.write(buf, 0, n);
            in.close();
            return bo.toString("UTF-8");
        } catch (Exception e) {
            return null;
        }
    }

    public interface Items {
        void onItem(String[] segs, String line);
    }

    // ---------- читання потоку подій ----------
    public static class Stream {
        private final String path;
        private final Items cb;
        private volatile boolean stopped;
        private volatile HttpURLConnection conn;
        private final LinkedHashSet<String> seen = new LinkedHashSet<String>();
        private final LinkedBlockingQueue<String> dels = new LinkedBlockingQueue<String>();

        public Stream(String path, Items cb) {
            this.path = path;
            this.cb = cb;
        }

        public void start() {
            Thread t = new Thread(new Runnable() {
                public void run() {
                    loop();
                }
            });
            t.setDaemon(true);
            t.start();
            Thread d = new Thread(new Runnable() {
                public void run() {
                    try {
                        while (!stopped) {
                            String p = dels.poll(1, TimeUnit.SECONDS);
                            if (p != null) http("DELETE", p, null);
                        }
                    } catch (Exception e) {
                        // ignore
                    }
                }
            });
            d.setDaemon(true);
            d.start();
        }

        public void stop() {
            stopped = true;
            final HttpURLConnection c = conn;
            if (c == null) return;
            Thread t = new Thread(new Runnable() {
                public void run() {
                    try {
                        c.disconnect();
                    } catch (Exception e) {
                        // ignore
                    }
                }
            });
            t.setDaemon(true);
            t.start();
        }

        private void loop() {
            while (!stopped) {
                try {
                    URL u = new URL(BASE + "/" + path + ".json");
                    HttpURLConnection c = (HttpURLConnection) u.openConnection();
                    conn = c;
                    c.setConnectTimeout(8000);
                    c.setReadTimeout(70000);
                    c.setRequestProperty("Accept", "text/event-stream");
                    if (c.getResponseCode() == 200) {
                        BufferedReader r = new BufferedReader(new InputStreamReader(c.getInputStream(), "UTF-8"));
                        String ev = "";
                        String line;
                        while (!stopped && (line = r.readLine()) != null) {
                            if (line.startsWith("event:")) {
                                ev = line.substring(6).trim();
                            } else if (line.startsWith("data:")) {
                                event(ev, line.substring(5).trim());
                            }
                        }
                    }
                } catch (Exception e) {
                    // повторимо
                }
                if (stopped) break;
                try {
                    Thread.sleep(1500);
                } catch (InterruptedException e) {
                    break;
                }
            }
        }

        private void event(String ev, String data) {
            if (!ev.equals("put") && !ev.equals("patch")) return;
            Object o = parse(data);
            if (!(o instanceof Map)) return;
            Map<?, ?> m = (Map<?, ?>) o;
            Object pa = m.get("path");
            Object val = m.get("data");
            String p = pa == null ? "/" : pa.toString();
            if (ev.equals("put")) {
                walk(split(p), val);
            } else if (val instanceof Map) {
                for (Map.Entry<?, ?> e : ((Map<?, ?>) val).entrySet()) {
                    ArrayList<String> segs = split(p);
                    for (String s : e.getKey().toString().split("/")) {
                        if (s.length() > 0) segs.add(s);
                    }
                    walk(segs, e.getValue());
                }
            }
        }

        private ArrayList<String> split(String p) {
            ArrayList<String> r = new ArrayList<String>();
            for (String s : p.split("/")) {
                if (s.length() > 0) r.add(s);
            }
            return r;
        }

        private void walk(ArrayList<String> segs, Object val) {
            if (val == null) return;
            if (val instanceof Map) {
                for (Map.Entry<?, ?> e : ((Map<?, ?>) val).entrySet()) {
                    ArrayList<String> n = new ArrayList<String>(segs);
                    n.add(e.getKey().toString());
                    walk(n, e.getValue());
                }
                return;
            }
            StringBuilder key = new StringBuilder();
            for (String s : segs) key.append('/').append(s);
            String k = key.toString();
            if (!seen.add(k)) return;
            while (seen.size() > 3000) {
                seen.remove(seen.iterator().next());
            }
            String[] sa = segs.toArray(new String[0]);
            for (String l : val.toString().split("\n")) {
                if (l.length() > 0) {
                    try {
                        cb.onItem(sa, l);
                    } catch (Exception e) {
                        // ignore
                    }
                }
            }
            dels.offer(path + k);
        }
    }

    // ---------- запис повідомлень ----------
    public static class Writer {
        private final String path;
        private final boolean ping;
        private final boolean clear;
        private final LinkedBlockingQueue<String> q = new LinkedBlockingQueue<String>();
        private volatile boolean closed;

        public Writer(String path, boolean ping, boolean clear) {
            this.path = path;
            this.ping = ping;
            this.clear = clear;
            Thread t = new Thread(new Runnable() {
                public void run() {
                    loop();
                }
            });
            t.setDaemon(true);
            t.start();
        }

        public void send(String line) {
            if (!closed) q.offer(line);
        }

        public void close() {
            if (closed) return;
            closed = true;
            q.offer(END);
        }

        private void loop() {
            if (clear) http("DELETE", path, null);
            try {
                while (true) {
                    String first = q.poll(8, TimeUnit.SECONDS);
                    if (first == null) {
                        if (!ping) continue;
                        first = "PING";
                    }
                    if (END.equals(first)) break;
                    StringBuilder sb = new StringBuilder(first);
                    boolean end = false;
                    int cnt = 1;
                    while (cnt < 40 && sb.length() < 400000) {
                        String x = q.poll();
                        if (x == null) break;
                        if (END.equals(x)) {
                            end = true;
                            break;
                        }
                        sb.append('\n').append(x);
                        cnt++;
                    }
                    String body = quote(sb.toString());
                    String key = newKey();
                    for (int a = 0; a < 3; a++) {
                        if (http("PUT", path + "/" + key, body) != null) break;
                        Thread.sleep(700);
                    }
                    if (end) break;
                }
            } catch (Exception e) {
                // ignore
            }
        }
    }

    // ---------- посилання господаря на клієнта ----------
    public static class RelayLink extends Link {
        public final String cid;
        private final Writer w;

        RelayLink(String code, String cid) {
            this.cid = cid;
            w = new Writer("rooms/" + code + "/down/" + cid, true, true);
        }

        public void send(String line) {
            w.send(line);
        }

        public void close() {
            w.close();
        }
    }

    // ---------- сторона господаря ----------
    public static class HostSide {
        public interface Cb {
            void onLine(String cid, String line);

            void onReady(String code);

            void onError(String msg);
        }

        private final String cid, want;
        private final Cb cb;
        private volatile String code = "";
        private Stream up;
        private volatile boolean stopped;
        private final java.util.HashMap<String, Long> rx = new java.util.HashMap<String, Long>();

        public HostSide(String cid, String wantCode, Cb cb) {
            this.cid = cid;
            this.want = wantCode == null ? "" : wantCode;
            this.cb = cb;
        }

        public String code() {
            return code;
        }

        public void start() {
            Thread t = new Thread(new Runnable() {
                public void run() {
                    open();
                }
            });
            t.setDaemon(true);
            t.start();
        }

        private void open() {
            ArrayList<String> cand = new ArrayList<String>();
            if (want.length() == 5 && cleanCode(want).equals(want)) cand.add(want);
            for (int i = 0; i < 6; i++) cand.add(newCode());
            String pick = null;
            for (String c : cand) {
                String meta = http("GET", "rooms/" + c + "/meta", null);
                if (meta == null) {
                    cb.onError(NOCON);
                    return;
                }
                boolean free = meta.trim().equals("null");
                if (!free) {
                    Object o = parse(meta);
                    if (o instanceof Map && cid.equals(((Map<?, ?>) o).get("h"))) free = true;
                }
                if (free) {
                    pick = c;
                    break;
                }
            }
            if (pick == null || stopped) {
                cb.onError("Не вдалося створити кімнату");
                return;
            }
            http("DELETE", "rooms/" + pick, null);
            if (http("PUT", "rooms/" + pick + "/meta", "{\"h\":" + quote(cid) + ",\"g\":\"du\"}") == null) {
                cb.onError(NOCON);
                return;
            }
            code = pick;
            up = new Stream("rooms/" + pick + "/up", new Items() {
                public void onItem(String[] segs, String line) {
                    if (segs.length != 2) return;
                    synchronized (rx) {
                        rx.put(segs[0], System.currentTimeMillis());
                    }
                    if (line.equals("PING")) return;
                    cb.onLine(segs[0], line);
                }
            });
            up.start();
            cb.onReady(pick);
        }

        public long lastRx(String c) {
            synchronized (rx) {
                Long v = rx.get(c);
                return v == null ? 0 : v;
            }
        }

        public void touch(String c) {
            synchronized (rx) {
                rx.put(c, System.currentTimeMillis());
            }
        }

        public RelayLink newLink(String c) {
            touch(c);
            return new RelayLink(code, c);
        }

        public void stop() {
            stopped = true;
            if (up != null) up.stop();
            final String c = code;
            if (c.length() > 0) {
                Thread t = new Thread(new Runnable() {
                    public void run() {
                        http("DELETE", "rooms/" + c, null);
                    }
                });
                t.setDaemon(true);
                t.start();
            }
        }
    }

    // ---------- сторона гостя ----------
    public static class ClientSide {
        private final String code, cid, hello;
        private final Client.Cb cb;
        private volatile boolean stopped;
        private Stream down;
        private Writer w;
        public volatile String failMsg = "";

        public ClientSide(String code, String cid, String hello, Client.Cb cb) {
            this.code = code;
            this.cid = cid;
            this.hello = hello;
            this.cb = cb;
        }

        public void start() {
            Thread t = new Thread(new Runnable() {
                public void run() {
                    run0();
                }
            });
            t.setDaemon(true);
            t.start();
        }

        private void fail(String m) {
            failMsg = m;
            stop();
            cb.onFail();
        }

        private void run0() {
            String meta = http("GET", "rooms/" + code + "/meta", null);
            if (meta == null) {
                fail(NOCON);
                return;
            }
            if (meta.trim().equals("null") || !meta.contains("\"g\":\"du\"")) {
                fail("Кімнату з таким кодом не знайдено");
                return;
            }
            http("DELETE", "rooms/" + code + "/up/" + cid, null);
            http("DELETE", "rooms/" + code + "/down/" + cid, null);
            if (stopped) return;
            final long[] last = {0};
            down = new Stream("rooms/" + code + "/down/" + cid, new Items() {
                public void onItem(String[] segs, String line) {
                    last[0] = System.currentTimeMillis();
                    if (line.equals("PING")) return;
                    cb.onLine(line);
                }
            });
            down.start();
            w = new Writer("rooms/" + code + "/up/" + cid, true, false);
            w.send(hello);
            long t0 = System.currentTimeMillis();
            while (!stopped) {
                try {
                    Thread.sleep(500);
                } catch (InterruptedException e) {
                    return;
                }
                long now = System.currentTimeMillis();
                if (last[0] == 0) {
                    if (now - t0 > 12000) {
                        fail("Господар не відповідає");
                        return;
                    }
                } else if (now - last[0] > 30000) {
                    stop();
                    cb.onClosed();
                    return;
                }
            }
        }

        public void send(String line) {
            Writer x = w;
            if (x != null) x.send(line);
        }

        public void stop() {
            stopped = true;
            Writer x = w;
            if (x != null) x.close();
            if (down != null) down.stop();
        }

        public void leave() {
            Writer x = w;
            if (x != null) x.send("BYE");
            stop();
        }
    }
}
