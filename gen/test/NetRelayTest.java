package com.example.gryntsivgames.sea;
import android.os.Handler;
import com.sun.net.httpserver.*;
import java.io.*;
import java.net.InetSocketAddress;
import java.util.*;
import java.util.concurrent.*;

public class NetRelayTest {
    static final Object LOCK = new Object();
    static TreeMap<String, Object> root = new TreeMap<String, Object>();
    static class Sub { String path; OutputStream out; }
    static final ArrayList<Sub> subs = new ArrayList<Sub>();

    static String[] segs(String p) { ArrayList<String> r = new ArrayList<String>(); for (String s : p.split("/")) if (s.length() > 0) r.add(s); return r.toArray(new String[0]); }
    @SuppressWarnings("unchecked")
    static Object get(String p) { Object cur = root; for (String s : segs(p)) { if (!(cur instanceof Map)) return null; cur = ((Map<String, Object>) cur).get(s); } return cur; }
    @SuppressWarnings("unchecked")
    static void set(String p, Object v) {
        String[] sg = segs(p);
        Map<String, Object> cur = root;
        for (int i = 0; i < sg.length - 1; i++) {
            Object n = cur.get(sg[i]);
            if (!(n instanceof Map)) { if (v == null) return; n = new TreeMap<String, Object>(); cur.put(sg[i], n); }
            cur = (Map<String, Object>) n;
        }
        if (v == null) cur.remove(sg[sg.length - 1]); else cur.put(sg[sg.length - 1], v);
    }
    static String ser(Object o) {
        if (o == null) return "null";
        if (o instanceof String) return Relay.quote((String) o);
        StringBuilder sb = new StringBuilder("{");
        boolean f = true;
        for (Map.Entry<?, ?> e : ((Map<?, ?>) o).entrySet()) { if (!f) sb.append(','); f = false; sb.append(Relay.quote(e.getKey().toString())).append(':').append(ser(e.getValue())); }
        return sb.append('}').toString();
    }
    static void emit(Sub s, String rel, Object data) {
        try { String d = "{\"path\":" + Relay.quote(rel) + ",\"data\":" + ser(data) + "}"; s.out.write(("event: put\ndata: " + d + "\n\n").getBytes("UTF-8")); s.out.flush(); } catch (Exception e) { }
    }
    static void changed(String p) {
        for (Sub s : new ArrayList<Sub>(subs)) {
            if (p.startsWith(s.path)) emit(s, p.substring(s.path.length()).isEmpty() ? "/" : p.substring(s.path.length()), get(p));
            else if (s.path.startsWith(p)) emit(s, "/", get(s.path));
        }
    }
    static int httpCount;

    public static void main(String[] a) throws Exception {
        HttpServer srv = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        srv.setExecutor(Executors.newCachedThreadPool());
        srv.createContext("/", new HttpHandler() { public void handle(HttpExchange ex) throws IOException {
            String path = ex.getRequestURI().getPath(); path = path.substring(0, path.length() - 5);
            String m = ex.getRequestMethod();
            synchronized (LOCK) { httpCount++; }
            if (m.equals("GET") && "text/event-stream".equals(ex.getRequestHeaders().getFirst("Accept"))) {
                ex.getResponseHeaders().add("Content-Type", "text/event-stream");
                ex.sendResponseHeaders(200, 0);
                Sub s = new Sub(); s.path = path.startsWith("/") ? path : "/" + path; s.out = ex.getResponseBody();
                synchronized (LOCK) { subs.add(s); emit(s, "/", get(s.path)); }
                try { while (true) Thread.sleep(500); } catch (Exception e) { }
                return;
            }
            byte[] resp;
            synchronized (LOCK) {
                if (m.equals("GET")) resp = ser(get(path)).getBytes("UTF-8");
                else if (m.equals("PUT")) { ByteArrayOutputStream bo = new ByteArrayOutputStream(); byte[] b = new byte[4096]; int n; InputStream in = ex.getRequestBody(); while ((n = in.read(b)) > 0) bo.write(b, 0, n);
                    String body = bo.toString("UTF-8"); set(path, Relay.parse(body)); changed(path); resp = body.getBytes("UTF-8"); }
                else { set(path, null); changed(path); resp = "null".getBytes(); }
            }
            ex.sendResponseHeaders(200, resp.length); ex.getResponseBody().write(resp); ex.close();
        } });
        srv.start();
        Relay.BASE = "http://127.0.0.1:" + srv.getAddress().getPort();

        final Handler H = new Handler(null);
        final java.util.concurrent.CopyOnWriteArrayList<String> hl = new java.util.concurrent.CopyOnWriteArrayList<String>();
        final java.util.concurrent.CopyOnWriteArrayList<String> gl = new java.util.concurrent.CopyOnWriteArrayList<String>();
        final String[] ev = {"", ""};
        final String[] codeBox = {""};
        final Net host = new Net(new Net.Cb() {
            public void onConnected(String n, int a) { ev[0] += "H:conn "; }
            public void onLine(String l) { hl.add(l); }
            public void onLost() { ev[0] += "H:lost "; }
            public void onFail(String m) { ev[0] += "H:fail(" + m + ") "; }
            public void onRoom(String c) { codeBox[0] = c; }
        });
        host.startHost("hostcid", "");
        host.setWifi(false);
        host.setInternet(true);
        Thread.sleep(1000);
        System.out.println("code=" + codeBox[0] + " meta=" + ser(get("rooms/" + codeBox[0] + "/meta")));
        // чужа кімната (Uno) - без g
        set("rooms/77777/meta", Relay.parse("{\"h\":\"x\"}"));
        final Net bad = new Net(new Net.Cb() {
            public void onConnected(String n, int a) {} public void onLine(String l) {} public void onLost() {}
            public void onFail(String m) { ev[1] += "G0:fail(" + m + ") "; } public void onRoom(String c) {}
        });
        bad.joinCode("77777", "g0");
        Thread.sleep(800);
        final Net guest = new Net(new Net.Cb() {
            public void onConnected(String n, int a) { ev[1] += "G:conn "; }
            public void onLine(String l) { gl.add(l); }
            public void onLost() { ev[1] += "G:lost "; }
            public void onFail(String m) { ev[1] += "G:fail(" + m + ") "; }
            public void onRoom(String c) {}
        });
        guest.joinCode(codeBox[0], "g1");
        Thread.sleep(1500);
        System.out.println("events host=" + ev[0] + " guest=" + ev[1]);
        // третій гравець
        final String[] third = {""};
        final Net g3 = new Net(new Net.Cb() {
            public void onConnected(String n, int a) { third[0] += "conn "; } public void onLine(String l) {} public void onLost() {}
            public void onFail(String m) { third[0] += "fail(" + m + ") "; } public void onRoom(String c) {}
        });
        g3.joinCode(codeBox[0], "g3");
        Thread.sleep(1500);
        System.out.println("third=" + third[0]);
        // гра: обмін повідомленнями і повний дуель
        final Board hb = new Board(), gb = new Board();
        hb.randomize(new java.util.Random(1));
        gb.randomize(new java.util.Random(2));
        final Board hv = new Board(), gv = new Board();
        final Bot hbot = new Bot(3), gbot = new Bot(2);
        final boolean[] done = {false};
        final String[] winner = {""};
        // обробка на кожній стороні (виконується в потоці main-sim)
        final java.util.concurrent.atomic.AtomicBoolean hTurn = new java.util.concurrent.atomic.AtomicBoolean(true);
        // господар стріляє першим
        Runnable hostShoot = new Runnable() { public void run() { host.send("SHOT|" + hbot.next(hv)); } };
        host.send("READY");
        guest.send("READY");
        Thread.sleep(500);
        System.out.println("host got=" + hl + " guest got=" + gl);
        hl.clear(); gl.clear();
        host.send("START|0");
        Thread.sleep(300);
        System.out.println("guest START=" + gl);
        gl.clear();
        // цикл: опитуємо черги
        int hi = 0, gi = 0;
        host.send("SHOT|" + hbot.next(hv));
        long end = System.currentTimeMillis() + 60000;
        int shots = 0;
        boolean hostTurn = true; // чия черга стріляти
        while (!done[0] && System.currentTimeMillis() < end) {
            Thread.sleep(10);
            while (gi < gl.size()) {
                String[] f = gl.get(gi++).split("\\|", -1);
                if (f[0].equals("SHOT")) {
                    String ans = gb.answer(Integer.parseInt(f[1])); guest.send(ans);
                    String[] r = ans.split("\\|", -1);
                    if (r[4].equals("1")) { winner[0] = "host"; done[0] = true; }
                    else if (r[2].equals("0")) guest.send("SHOT|" + gbot.next(gv));
                } else if (f[0].equals("RES")) {
                    gv.applyRes(f);
                    if (f[4].equals("1")) { winner[0] = "guest"; done[0] = true; }
                    else if (!f[2].equals("0")) guest.send("SHOT|" + gbot.next(gv));
                }
            }
            while (hi < hl.size()) {
                String[] f = hl.get(hi++).split("\\|", -1);
                if (f[0].equals("SHOT")) {
                    String ans = hb.answer(Integer.parseInt(f[1])); host.send(ans);
                    String[] r = ans.split("\\|", -1);
                    if (r[4].equals("1")) { winner[0] = "guest"; done[0] = true; }
                    else if (r[2].equals("0")) host.send("SHOT|" + hbot.next(hv));
                } else if (f[0].equals("RES")) {
                    hv.applyRes(f);
                    if (f[4].equals("1")) { winner[0] = "host"; done[0] = true; }
                    else if (!f[2].equals("0")) host.send("SHOT|" + hbot.next(hv));
                }
            }
        }
        System.out.println("duel done=" + done[0] + " winner=" + winner[0]);
        // перевірка: поле-огляд збігається з реальним
        boolean same = true;
        for (int i = 0; i < 100; i++) { int x1 = hv.shot[i], y1 = gb.shot[i]; if (x1 != y1) same = false; int c = gv.shot[i], d = hb.shot[i]; if (c != d) same = false; }
        int d1 = 0, d2 = 0;
        for (int i = 0; i < 100; i++) { if (hv.shot[i] != gb.shot[i]) { d1++; System.out.println("cell " + i + " hv=" + hv.shot[i] + " gb=" + gb.shot[i] + " shipAt=" + gb.shipAt(i / 10, i % 10)); } if (gv.shot[i] != hb.shot[i]) d2++; }
        System.out.println("views match=" + same + " hostview-vs-guestboard diffs=" + d1 + " guestview-vs-hostboard diffs=" + d2);
        // гість виходить
        guest.close();
        Thread.sleep(1500);
        System.out.println("after guest bye events host=" + ev[0]);
        host.close();
        Thread.sleep(800);
        System.out.println("rooms after stop=" + ser(get("rooms")));
        System.exit(0);
    }
}
