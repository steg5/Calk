package com.example.gryntsivgames.sea;

import android.os.Handler;
import android.os.Looper;

import java.net.InetSocketAddress;
import java.net.Socket;

public class Client {
    public interface Cb {
        void onLine(String line);

        void onClosed();

        void onFail();
    }

    private final Handler h = new Handler(Looper.getMainLooper());
    private volatile Link.SocketLink link;
    private volatile boolean closed;
    private Relay.ClientSide rel;
    public volatile String failMsg = "";

    public void connectRelay(String code, String cid, String hello, final Cb cb) {
        rel = new Relay.ClientSide(code, cid, hello, new Cb() {
            public void onLine(final String line) {
                h.post(new Runnable() {
                    public void run() {
                        if (!closed) cb.onLine(line);
                    }
                });
            }

            public void onClosed() {
                h.post(new Runnable() {
                    public void run() {
                        if (!closed) cb.onClosed();
                    }
                });
            }

            public void onFail() {
                h.post(new Runnable() {
                    public void run() {
                        if (!closed) {
                            failMsg = rel == null ? "" : rel.failMsg;
                            cb.onFail();
                        }
                    }
                });
            }
        });
        rel.start();
    }

    public void connect(final String ip, final int port, final String hello, final Cb cb) {
        Thread t = new Thread(new Runnable() {
            public void run() {
                try {
                    Socket s = new Socket();
                    s.connect(new InetSocketAddress(ip, port), 4000);
                    s.setTcpNoDelay(true);
                    Link.SocketLink l = new Link.SocketLink(s);
                    link = l;
                    l.send(hello);
                    l.readLoop(new Link.LineSink() {
                        public void onLine(final String line) {
                            h.post(new Runnable() {
                                public void run() {
                                    if (!closed) cb.onLine(line);
                                }
                            });
                        }

                        public void onClosed() {
                            h.post(new Runnable() {
                                public void run() {
                                    if (!closed) cb.onClosed();
                                }
                            });
                        }
                    });
                } catch (Exception e) {
                    h.post(new Runnable() {
                        public void run() {
                            if (!closed) cb.onFail();
                        }
                    });
                }
            }
        });
        t.setDaemon(true);
        t.start();
    }

    public void send(String line) {
        if (rel != null) {
            rel.send(line);
            return;
        }
        Link l = link;
        if (l != null) l.send(line);
    }

    public void close() {
        closed = true;
        if (rel != null) {
            rel.leave();
            return;
        }
        Link l = link;
        if (l != null) {
            l.send("BYE");
            l.close();
        }
    }
}
