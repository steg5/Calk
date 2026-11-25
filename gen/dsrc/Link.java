package com.example.gryntsivgames.durak;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.Socket;
import java.util.concurrent.LinkedBlockingQueue;

public abstract class Link {
    public abstract void send(String line);

    public void close() {
    }

    public interface LineSink {
        void onLine(String line);

        void onClosed();
    }

    private static final String END = "\u0000END";

    public static class SocketLink extends Link {
        private final Socket s;
        private final LinkedBlockingQueue<String> q = new LinkedBlockingQueue<String>();
        private volatile boolean closed;

        public SocketLink(Socket sock) {
            this.s = sock;
            Thread t = new Thread(new Runnable() {
                public void run() {
                    try {
                        BufferedWriter w = new BufferedWriter(new OutputStreamWriter(s.getOutputStream(), "UTF-8"));
                        while (true) {
                            String m = q.take();
                            if (END.equals(m)) break;
                            w.write(m);
                            w.write("\n");
                            w.flush();
                        }
                    } catch (Exception e) {
                        // ignore
                    }
                    try {
                        s.close();
                    } catch (IOException e) {
                        // ignore
                    }
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

        public void readLoop(final LineSink sink) {
            Thread t = new Thread(new Runnable() {
                public void run() {
                    try {
                        BufferedReader r = new BufferedReader(new InputStreamReader(s.getInputStream(), "UTF-8"));
                        String line;
                        while ((line = r.readLine()) != null) {
                            sink.onLine(line);
                        }
                    } catch (Exception e) {
                        // ignore
                    }
                    sink.onClosed();
                }
            });
            t.setDaemon(true);
            t.start();
        }
    }
}
