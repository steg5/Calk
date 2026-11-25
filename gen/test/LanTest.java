package com.example.gryntsivgames.sea;
import java.util.concurrent.CopyOnWriteArrayList;
public class LanTest {
    static Net mk(final String tag, final StringBuffer ev, final CopyOnWriteArrayList<String> log) {
        return new Net(new Net.Cb() {
            public void onConnected(int p, String n, int a) { ev.append(tag + ":conn" + p + "(" + n + ") "); }
            public void onLine(int p, String l) { log.add(tag + p + ">" + l); }
            public void onLost(int p) { ev.append(tag + ":lost" + p + " "); }
            public void onFail(String m) { ev.append(tag + ":fail(" + m + ") "); }
            public void onRoom(String c) {}
        });
    }
    public static void main(String[] a) throws Exception {
        StringBuffer ev = new StringBuffer();
        CopyOnWriteArrayList<String> log = new CopyOnWriteArrayList<String>();
        Net host = mk("H", ev, log);
        host.setInfo("Хост", 1);
        host.startHost("h", "");
        Net g1 = mk("A", ev, log), g2 = mk("B", ev, log), g3 = mk("C", ev, log);
        g1.setInfo("Аня", 2); g2.setInfo("Борис", 3); g3.setInfo("Влад", 4);
        g1.joinLan("127.0.0.1", Net.PORT);
        Thread.sleep(500);
        g2.joinLan("127.0.0.1", Net.PORT);
        Thread.sleep(500);
        host.setBots(3);          // 1 + 2 гості + 3 боти = 6
        g3.joinLan("127.0.0.1", Net.PORT);
        Thread.sleep(500);
        g1.send("SHOT|5");
        g2.send("EMO|3|-1");
        host.broadcast("TURN|1|0");
        host.sendTo(2, "ONLY2");
        Thread.sleep(500);
        System.out.println(ev);
        System.out.println(log);
        host.kick(1);
        Thread.sleep(500);
        System.out.println("after kick: " + ev);
        host.close();
        Thread.sleep(300);
        System.exit(0);
    }
}
