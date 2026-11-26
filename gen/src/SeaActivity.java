package com.example.gryntsivgames.sea;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.content.Context;
import android.os.Vibrator;
import android.graphics.Canvas;
import android.graphics.RectF;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.DisplayMetrics;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.EditText;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;
import com.example.gryntsivgames.uno.Avatars;
import com.example.gryntsivgames.uno.BaseActivity;
import com.example.gryntsivgames.uno.Bg;
import com.example.gryntsivgames.uno.IconView;
import com.example.gryntsivgames.uno.Snd;

import java.util.ArrayList;
import java.util.Random;

public class SeaActivity extends BaseActivity {
    private static final int S_MENU = 0, S_SEA = 1, S_PLACE = 2, S_BATTLE = 3, S_SET = 4, S_LOBBY = 5, S_JOIN = 6, S_HIST = 7;
    private static final int WRAP = ViewGroup.LayoutParams.WRAP_CONTENT;
    private static final int MATCH = ViewGroup.LayoutParams.MATCH_PARENT;
    private static final int[] GOOD_EMO = {0, 1, 2, 5, 6, 8, 9, 11, 14, 21, 25, 31};
    private static final String[] BOT_NAMES = {"Нептун", "Корсар", "Адмірал", "Боцман", "Штурман", "Капітан", "Тритон", "Дельфін"};
    private static final String[] LV_SHORT = {"Аматор", "Студент", "Геній", "Легенда"};

    private final Random rnd = new Random();
    private FrameLayout root;
    private int screen, prevScreen, theme, gen;
    private Board myBoard;
    private BattleView bv;
    private Dialog endDlg, emoDlg, avDlg;
    private Toast toast;
    private long busyUntil;
    private int speed = 3;
    private Banner bnr;
    private static final String[] SPD_NAMES = {"Повільно", "Звичайно", "Швидко", "Дуже швидко", "Блискавично"};
    private static final int[] LOCK_MISS = {600, 400, 280, 170, 90};
    private static final int[] LOCK_HIT = {800, 550, 380, 230, 120};
    private static final int[] BOT_BASE = {700, 450, 280, 140, 60};
    private static final float[] BOT_MUL = {2.0f, 1.4f, 1.0f, 0.7f, 0.45f};

    // банер поверх усього
    private static class Banner extends View {
        String text = "";
        int col;
        long st, until;
        final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);

        Banner(Context c) {
            super(c);
            p.setTypeface(Typeface.DEFAULT_BOLD);
        }

        void show(String s, int color, long ms) {
            text = s;
            col = color;
            st = System.currentTimeMillis();
            until = st + ms;
            invalidate();
        }

        @Override
        protected void onDraw(Canvas cv) {
            long now = System.currentTimeMillis();
            if (until <= now) return;
            BattleView.Lay l = BattleView.layout(getWidth(), getHeight());
            long el = now - st, left = until - now;
            float sc = el < 250 ? 0.5f + 0.5f * el / 250f : 1f;
            float al = left < 350 ? left / 350f : 1f;
            p.setTextAlign(Paint.Align.CENTER);
            p.setTextSize(l.u * 1.6f * sc);
            float by = l.y0 + 5 * l.ec + l.u * 0.6f * sc;
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(l.u * 0.14f);
            p.setColor(0xFF000000);
            p.setAlpha((int) (255 * al));
            cv.drawText(text, getWidth() / 2f, by, p);
            p.setStyle(Paint.Style.FILL);
            p.setColor(col);
            p.setAlpha((int) (255 * al));
            cv.drawText(text, getWidth() / 2f, by, p);
            p.setAlpha(255);
            postInvalidateDelayed(40);
        }
    }

    private void banner(String s, int color, long ms) {
        if (bnr == null) return;
        bnr.show(s, color, ms);
        bnr.bringToFront();
    }

    // кімната
    private static class Pl {
        String nick = "";
        int avatar, kind, pid, level = 3, speed = 3; // kind: 0 господар, 1 гість, 2 бот
        boolean ready, left, rem;
    }

    private final ArrayList<Pl> room = new ArrayList<Pl>();
    private int myIdx;
    private LinearLayout lobbyBody;

    // мережа і матч
    private Net net;
    private Match m;
    private boolean myReady, started, myRematch, myTurn, awaiting, autoView, restoreWait;
    private int phase; // 0 кімната, 1 розстановка, 2 бій, 3 кінець
    private int pausedBy = -1;
    private String netCode = "";
    private LinearLayout netBox, joinList;
    private Discovery.Listener disc;
    private String joinSig = "";
    private String lastRes = "";
    private PlaceView pview;
    private IconView turnIcon, pauseIcon, dndIcon, eyeIcon;
    private Button restoreOk, restoreSkip;
    private boolean botBusy;
    private String lastWinner = "";

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        theme = sp.getInt("sbTheme", 0);
        Scheme.set(bgId);
        autoView = sp.getBoolean("sbAuto", false);
        speed = Math.max(1, Math.min(5, sp.getInt("sbSpeed2", 2)));
        root = new FrameLayout(this);
        setContentView(root);
        showSea();
        askResumeSea();
    }

    // ---------- продовження гри проти ботів ----------
    private boolean askedResume;

    private void saveSea() {
        if (m == null || !m.host || m.over || phase != 2 || humans() != 0) return;
        try {
            sp.edit().putString("saveS", serializeSea()).apply();
        } catch (Exception e) {
            // ignore
        }
    }

    private void forgetSea() {
        sp.edit().remove("saveS").apply();
    }

    private String serializeSea() {
        StringBuilder sb = new StringBuilder();
        sb.append(m.n).append(',').append(myIdx).append(',').append(m.turn).append(',').append(m.restore ? 1 : 0).append('\u001f');
        for (int i = 0; i < m.n; i++) {
            Pl q = room.get(i);
            if (i > 0) sb.append(';');
            sb.append(m.names[i]).append('~').append(q.avatar).append('~').append(m.bot[i] ? 1 : 0).append('~')
                .append(q.level).append('~').append(q.speed).append('~').append(m.alive[i] ? 1 : 0);
        }
        sb.append('\u001f');
        for (int i = 0; i < m.n; i++) {
            Board b = m.boards[i];
            if (i > 0) sb.append('\u001e');
            sb.append(b.baseTheme).append('/');
            for (int k = 0; k < b.ships.length; k++) {
                Board.Ship sh = b.ships[k];
                if (k > 0) sb.append(';');
                sb.append(sh.placed ? 1 : 0).append(',').append(sh.r).append(',').append(sh.c).append(',')
                    .append(sh.h ? 1 : 0).append(',').append(sh.theme);
            }
            sb.append('/');
            for (int k = 0; k < b.shot.length; k++) sb.append(b.shot[k]);
        }
        return sb.toString();
    }

    private static String describeSea(String d) {
        try {
            String[] sec = d.split("\u001f", -1);
            String[] pl = sec[1].split(";");
            StringBuilder sb = new StringBuilder("Гравців: " + pl.length + "\n");
            for (int k = 0; k < pl.length; k++) {
                String[] q = pl[k].split("~", -1);
                if (k > 0) sb.append(", ");
                sb.append(q[0]);
            }
            return sb.toString();
        } catch (Exception e) {
            return "Збережена гра";
        }
    }

    private boolean loadSea(String d) {
        try {
            String[] sec = d.split("\u001f", -1);
            String[] h = sec[0].split(",");
            int n = Integer.parseInt(h[0]);
            int mi = Integer.parseInt(h[1]);
            String[] pl = sec[1].split(";");
            String[] bs = sec[2].split("\u001e", -1);
            if (pl.length != n || bs.length != n) return false;
            stopNet();
            room.clear();
            String[] names = new String[n];
            boolean[] bot = new boolean[n];
            int[] lv = new int[n];
            boolean[] al = new boolean[n];
            for (int i = 0; i < n; i++) {
                String[] q = pl[i].split("~", -1);
                Pl x = new Pl();
                x.nick = q[0];
                x.avatar = Integer.parseInt(q[1]);
                x.kind = i == mi ? 0 : 2;
                x.level = Integer.parseInt(q[3]);
                x.speed = Integer.parseInt(q[4]);
                room.add(x);
                names[i] = x.nick;
                bot[i] = q[2].equals("1");
                lv[i] = x.level;
                al[i] = q[5].equals("1");
            }
            myIdx = mi;
            m = new Match(n, mi, true, names, bot, lv, ev);
            m.restoreOn = sp.getBoolean("sbRestore", true);
            for (int i = 0; i < n; i++) {
                String[] bp = bs[i].split("/", -1);
                Board b = m.boards[i];
                b.baseTheme = Integer.parseInt(bp[0]);
                String[] ss = bp[1].split(";");
                for (int k = 0; k < b.ships.length; k++) {
                    String[] f = ss[k].split(",");
                    Board.Ship sh = b.ships[k];
                    sh.placed = f[0].equals("1");
                    sh.r = Integer.parseInt(f[1]);
                    sh.c = Integer.parseInt(f[2]);
                    sh.h = f[3].equals("1");
                    sh.theme = Integer.parseInt(f[4]);
                }
                for (int k = 0; k < b.shot.length; k++) b.shot[k] = bp[2].charAt(k) - '0';
                m.alive[i] = al[i];
            }
            myBoard = m.boards[mi];
            m.turn = Integer.parseInt(h[2]);
            m.restore = h[3].equals("1");
            return true;
        } catch (Exception e) {
            m = null;
            return false;
        }
    }

    private void askResumeSea() {
        if (askedResume) return;
        askedResume = true;
        final String data = sp.getString("saveS", "");
        if (data.length() == 0) return;
        LinearLayout box = column();
        box.addView(tv("Продовжити гру?", 0.5f, WHITE, true), lp(WRAP, WRAP, 0.08f));
        box.addView(tv("Незавершений бій", 0.32f, 0xFFB0BEC5, false), lp(WRAP, WRAP, 0.04f));
        box.addView(tv(describeSea(data), 0.34f, WHITE, false), lp(WRAP, WRAP, 0.08f));
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        final Dialog[] dd = new Dialog[1];
        row.addView(btn("Продовжити", 0xFF2E7D32, new View.OnClickListener() {
            public void onClick(View v) {
                dd[0].dismiss();
                resumeSea(data);
            }
        }), lp(4.2f, 1.0f, 0.08f));
        row.addView(btn("Видалити", 0xFFC62828, new View.OnClickListener() {
            public void onClick(View v) {
                forgetSea();
                dd[0].dismiss();
            }
        }), lp(3.2f, 1.0f, 0.08f));
        box.addView(row);
        dd[0] = dialog(box, true);
        showSized(dd[0], 9.5f);
    }

    private void resumeSea(String data) {
        if (!loadSea(data)) {
            say("Не вдалося відновити гру");
            forgetSea();
            return;
        }
        started = true;
        myReady = true;
        lastRes = "";
        buildBattle();
        m.dnd[myIdx] = sp.getBoolean("dnd", false);
        m.pause(myIdx, true);
        ev.turn(m.turn, m.restore);
    }

    @Override
    protected void onDestroy() {
        gen++;
        stopNet();
        stopDisc();
        super.onDestroy();
    }

    private void say(String s) {
        toast(s);
    }

    @Override
    protected int btnCol() {
        return Scheme.cur.btn;
    }

    private int mapColor(int c) {
        return (c == 0xFF1565C0 || c == 0xFF1E88E5) ? Scheme.cur.btn : c;
    }

    @Override
    protected Button btn(String s, int color, View.OnClickListener l) {
        return super.btn(s, mapColor(color), l);
    }

    private void small(Button b, float size) {
        b.setTextSize(TypedValue.COMPLEX_UNIT_PX, size * u);
    }

    private LinearLayout column() {
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setGravity(Gravity.CENTER);
        return col;
    }

    private void begin(int s) {
        gen++;
        prevScreen = screen;
        screen = s;
        stopDisc();
        for (Dialog d : new Dialog[]{endDlg, emoDlg, avDlg}) {
            if (d != null) {
                try {
                    d.dismiss();
                } catch (Exception e) {
                    // ignore
                }
            }
        }
        endDlg = null;
        emoDlg = null;
        avDlg = null;
        endBar = null;
        root.removeAllViews();
        Scheme.set(bgId);
        root.setBackgroundColor(Scheme.cur.bg);
        root.addView(new SeaBg(this), new FrameLayout.LayoutParams(MATCH, MATCH));
        holdGame = false;
        updateHold();
        musicFor(s == S_BATTLE);
    }

    private class SeaBg extends View {
        SeaBg(Context c) {
            super(c);
        }

        @Override
        protected void onDraw(Canvas cv) {
            Scheme.drawBg(cv, getWidth(), getHeight());
        }
    }

    private void stopDisc() {
        if (disc != null) disc.stop();
        disc = null;
    }

    private void stopNet() {
        Net n = net;
        net = null;
        if (n != null) n.close();
    }

    private Avatars.AvatarView avView(int id) {
        Avatars.AvatarView av = new Avatars.AvatarView(this);
        av.set(id, id < 0 ? photo : "-", false);
        return av;
    }

    // ---------- головне меню гри ----------
    private void showSea() {
        stopNet();
        room.clear();
        lastWinner = "";
        m = null;
        phase = 0;
        begin(S_SEA);
        LinearLayout col = column();
        col.addView(tv("Морський бій", 1.0f, 0xFFFFD54F, true), lp(WRAP, WRAP, 0.1f));
        LinearLayout prof = new LinearLayout(this);
        prof.setGravity(Gravity.CENTER_VERTICAL);
        prof.addView(avView(avatar), lp(1.2f, 1.2f, 0.05f));
        prof.addView(tv(nick, 0.45f, WHITE, true), lp(WRAP, WRAP, 0.1f));
        col.addView(prof, lp(WRAP, WRAP, 0.05f));
        col.addView(btn("Створити кімнату", 0xFF1565C0, new View.OnClickListener() {
            public void onClick(View v) {
                createRoom();
            }
        }), lp(6.5f, 1.15f, 0.12f));
        col.addView(btn("Приєднатися", 0xFF1565C0, new View.OnClickListener() {
            public void onClick(View v) {
                showJoin();
            }
        }), lp(6.5f, 1.15f, 0.12f));
        col.addView(btn("Історія ігор", 0xFF1565C0, new View.OnClickListener() {
            public void onClick(View v) {
                showHistory();
            }
        }), lp(6.5f, 1.15f, 0.12f));
        root.addView(col, new FrameLayout.LayoutParams(MATCH, MATCH));
        FrameLayout.LayoutParams bl = new FrameLayout.LayoutParams(WRAP, WRAP, Gravity.BOTTOM | Gravity.LEFT);
        bl.setMargins(px(0.3f), 0, 0, px(0.3f));
        root.addView(audioBar(1.0f), bl);
        LinearLayout br = new LinearLayout(this);
        br.addView(btn("Назад", 0xFF1565C0, new View.OnClickListener() {
            public void onClick(View v) {
                finish();
            }
        }), lp(2.8f, 1.0f, 0.05f));
        br.addView(btn("Вихід", 0xFF1565C0, new View.OnClickListener() {
            public void onClick(View v) {
                finishAffinity();
            }
        }), lp(2.8f, 1.0f, 0.05f));
        FrameLayout.LayoutParams brp = new FrameLayout.LayoutParams(WRAP, WRAP, Gravity.BOTTOM | Gravity.RIGHT);
        brp.setMargins(0, 0, px(0.3f), px(0.3f));
        root.addView(br, brp);
    }

    // ---------- історія ----------
    private void addHist(String opp, boolean win) {
        String old = sp.getString("sbHist", "");
        String line = System.currentTimeMillis() + "|" + opp.replace("|", " ").replace("\n", " ") + "|" + (win ? 1 : 0);
        String[] ls = old.length() == 0 ? new String[0] : old.split("\n");
        StringBuilder sb = new StringBuilder(line);
        for (int i = 0; i < ls.length && i < 99; i++) sb.append('\n').append(ls[i]);
        sp.edit().putString("sbHist", sb.toString()).commit();
    }

    private void showHistory() {
        begin(S_HIST);
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(px(0.5f), px(0.3f), px(0.5f), px(0.3f));
        col.addView(tv("Історія ігор", 0.5f, WHITE, true));
        ScrollView sv = new ScrollView(this);
        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        String h = sp.getString("sbHist", "");
        if (h.length() == 0) {
            list.addView(tv("Ігор ще не було", 0.38f, 0xFFB0BEC5, false), lp(WRAP, WRAP, 0.1f));
        } else {
            java.text.SimpleDateFormat df = new java.text.SimpleDateFormat("dd.MM HH:mm");
            for (String ln : h.split("\n")) {
                String[] f = ln.split("\\|", -1);
                if (f.length < 3) continue;
                boolean win = f[2].equals("1");
                String when = "";
                try {
                    when = df.format(new java.util.Date(Long.parseLong(f[0])));
                } catch (Exception e) {
                    // ignore
                }
                LinearLayout row = new LinearLayout(this);
                row.setGravity(Gravity.CENTER_VERTICAL);
                row.addView(tv(when, 0.34f, 0xFFB0BEC5, false), lp(2.4f, WRAP, 0.05f));
                row.addView(tv("з " + f[1], 0.38f, WHITE, false), new LinearLayout.LayoutParams(0, WRAP, 1f));
                row.addView(tv(win ? "Перемога" : "Поразка", 0.38f, win ? 0xFF69F0AE : 0xFFFF8A80, true), lp(WRAP, WRAP, 0.05f));
                list.addView(row, new LinearLayout.LayoutParams(MATCH, WRAP));
            }
        }
        sv.addView(list);
        col.addView(sv, new LinearLayout.LayoutParams(MATCH, 0, 1f));
        LinearLayout bar = new LinearLayout(this);
        bar.addView(btn("Назад", 0xFF455A64, new View.OnClickListener() {
            public void onClick(View v) {
                showSea();
            }
        }), lp(3f, 0.95f, 0.1f));
        bar.addView(new View(this), new LinearLayout.LayoutParams(0, 1, 1f));
        if (h.length() > 0) {
            bar.addView(btn("Стерти всю історію", 0xFFB71C1C, new View.OnClickListener() {
                public void onClick(View v) {
                    new AlertDialog.Builder(SeaActivity.this).setTitle("Стерти всю історію?")
                        .setPositiveButton("Так", new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface d, int w) {
                                sp.edit().putString("sbHist", "").commit();
                                showHistory();
                            }
                        }).setNegativeButton("Ні", null).show();
                }
            }), lp(5.2f, 0.95f, 0.1f));
        }
        col.addView(bar, new LinearLayout.LayoutParams(MATCH, WRAP));
        root.addView(col, new FrameLayout.LayoutParams(MATCH, MATCH));
    }

    // ---------- мережа ----------
    private int idxOfPid(int pid) {
        for (int i = 0; i < room.size(); i++) if (room.get(i).kind == 1 && room.get(i).pid == pid && !room.get(i).left) return i;
        return -1;
    }

    private String clean(String s) {
        return s.replace("|", " ").replace("\n", " ");
    }

    private void sendRoster() {
        if (net == null || !net.host) return;
        net.setBots(botCount());
        for (int i = 0; i < room.size(); i++) {
            Pl r = room.get(i);
            if (r.kind != 1) continue;
            StringBuilder sb = new StringBuilder("ROSTER|" + i + "|" + room.size());
            for (Pl q : room) sb.append('|').append(clean(q.nick)).append('|').append(q.avatar).append('|').append(q.kind);
            net.sendTo(r.pid, sb.toString());
        }
    }

    private int botCount() {
        int c = 0;
        for (Pl q : room) if (q.kind == 2) c++;
        return c;
    }

    private Net.Cb netCb(final Net[] ref) {
        return new Net.Cb() {
            public void onConnected(int pid, String n, int a) {
                if (net != ref[0]) return;
                if (net.host) {
                    Pl p = new Pl();
                    p.nick = n;
                    p.avatar = a;
                    p.kind = 1;
                    p.pid = pid;
                    room.add(p);
                    sfx(Snd.JOIN);
                    sendRoster();
                    if (screen == S_LOBBY) fillLobby();
                } else {
                    room.clear();
                    Pl h = new Pl();
                    h.nick = n;
                    h.avatar = a;
                    room.add(h);
                    showLobbyGuest();
                }
            }

            public void onLine(int pid, String line) {
                if (net != ref[0]) return;
                if (net.host) handleHost(pid, line);
                else handleGuest(line);
            }

            public void onLost(int pid) {
                if (net != ref[0]) return;
                if (net.host) {
                    int i = idxOfPid(pid);
                    if (i < 0) return;
                    if (phase == 2 && m != null) {
                        room.get(i).left = true;
                        say(room.get(i).nick + " вийшов з гри");
                        m.playerLeft(i);
                    } else if (phase == 3) {
                        room.get(i).left = true;
                    } else {
                        say(room.get(i).nick + " вийшов");
                        room.remove(i);
                        sendRoster();
                        if (screen == S_LOBBY) fillLobby();
                        if (phase == 1) {
                            if (room.size() < 2) {
                                say("Усі гравці вийшли");
                                showSea();
                            } else {
                                checkStartHost();
                            }
                        }
                    }
                } else {
                    say("Господар закрив кімнату");
                    showSea();
                }
            }

            public void onFail(String msg) {
                if (net != ref[0]) return;
                say(msg);
                if (screen == S_JOIN) {
                    Net n = net;
                    net = null;
                    if (n != null) n.close();
                }
            }

            public void onRoom(String code) {
                if (net != ref[0]) return;
                netCode = code;
                if (code.length() > 0) sp.edit().putString("sbRoom", code).commit();
                if (screen == S_LOBBY) fillNetBar();
            }
        };
    }

    private void createRoom() {
        stopNet();
        room.clear();
        phase = 0;
        netCode = "";
        Pl me = new Pl();
        me.nick = nick;
        me.avatar = avatar;
        me.kind = 0;
        room.add(me);
        myIdx = 0;
        final Net[] ref = new Net[1];
        net = new Net(netCb(ref));
        ref[0] = net;
        net.setInfo(nick, avatar < 0 ? 0 : avatar);
        net.startHost(cid, sp.getString("sbRoom", ""));
        showLobby();
    }

    private void showLobby() {
        begin(S_LOBBY);
        phase = 0;
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(px(0.6f), px(0.3f), px(0.6f), px(0.3f));
        LinearLayout trow = new LinearLayout(this);
        trow.setGravity(Gravity.CENTER_VERTICAL);
        trow.addView(tv("Кімната", 0.6f, 0xFFFFD54F, true), new LinearLayout.LayoutParams(0, WRAP, 1f));
        netBox = new LinearLayout(this);
        netBox.setOrientation(LinearLayout.VERTICAL);
        netBox.setGravity(Gravity.RIGHT);
        trow.addView(netBox, new LinearLayout.LayoutParams(WRAP, WRAP));
        col.addView(trow, new LinearLayout.LayoutParams(MATCH, WRAP));
        lobbyBody = new LinearLayout(this);
        lobbyBody.setOrientation(LinearLayout.VERTICAL);
        col.addView(lobbyBody, new LinearLayout.LayoutParams(MATCH, 0, 1f));
        root.addView(col, new FrameLayout.LayoutParams(MATCH, MATCH));
        fillNetBar();
        fillLobby();
    }

    private LinearLayout playerRow(int av, String name) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.addView(avView(av), lp(0.95f, 0.95f, 0.03f));
        row.addView(tv(name, 0.4f, WHITE, true), new LinearLayout.LayoutParams(0, WRAP, 1f));
        return row;
    }

    private String roleOf(Pl q, int i) {
        if (q.kind == 2) return "Бот " + q.nick;
        return q.nick + (q.kind == 0 ? " - господар" : " - гість");
    }

    private void fillLobby() {
        if (lobbyBody == null || screen != S_LOBBY) return;
        lobbyBody.removeAllViews();
        ScrollView sv = new ScrollView(this);
        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        for (int i = 0; i < room.size(); i++) {
            final Pl q = room.get(i);
            LinearLayout row = playerRow(q.avatar, roleOf(q, i));
            if (q.kind == 2) {
                for (int k = 0; k < LV_SHORT.length; k++) {
                    final int lk = k;
                    Button lb = btn(LV_SHORT[k], q.level == k ? SEL : 0xFF455A64, new View.OnClickListener() {
                        public void onClick(View v) {
                            q.level = lk;
                            sp.edit().putInt("sbLvl", lk).commit();
                            fillLobby();
                        }
                    });
                    small(lb, 0.27f);
                    row.addView(lb, lp(1.9f, 0.75f, 0.02f));
                }
            }
            final int qi = i;
            row.addView(btn("↑", 0xFF455A64, new View.OnClickListener() {
                public void onClick(View v) {
                    moveSeat(qi, -1);
                }
            }), lp(0.8f, 0.75f, 0.02f));
            row.addView(btn("↓", 0xFF455A64, new View.OnClickListener() {
                public void onClick(View v) {
                    moveSeat(qi, 1);
                }
            }), lp(0.8f, 0.75f, 0.02f));
            if (q.kind != 0) {
                Button rm = btn("Прибрати", 0xFFC62828, new View.OnClickListener() {
                    public void onClick(View v) {
                        if (q.kind == 1 && net != null) net.kick(q.pid);
                        room.remove(q);
                        sendRoster();
                        fillLobby();
                    }
                });
                small(rm, 0.27f);
                row.addView(rm, lp(2.2f, 0.75f, 0.04f));
            }
            list.addView(row, new LinearLayout.LayoutParams(MATCH, WRAP));
        }
        if (room.size() < 2) {
            list.addView(tv("Очікуємо гравців… Вони можуть приєднатися по Wi-Fi або за кодом з інтернету.", 0.34f, 0xFFB0BEC5, false), lp(WRAP, WRAP, 0.1f));
        }
        sv.addView(list);
        lobbyBody.addView(sv, new LinearLayout.LayoutParams(MATCH, 0, 1f));
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.addView(tv("Гравців: " + room.size() + " з 6", 0.36f, 0xFFB0BEC5, false), lp(WRAP, WRAP, 0.1f));
        if (room.size() < Net.MAX) {
            row.addView(btn("Додати бота", 0xFF1565C0, new View.OnClickListener() {
                public void onClick(View v) {
                    addBot();
                }
            }), lp(3.6f, 1.0f, 0.06f));
        }
        Button start = btn("Старт", 0xFF2E7D32, new View.OnClickListener() {
            public void onClick(View v) {
                startFromLobby();
            }
        });
        start.setAlpha(room.size() >= 2 ? 1f : 0.4f);
        row.addView(start, lp(3.2f, 1.0f, 0.06f));
        row.addView(btn("Вийти", 0xFFC62828, new View.OnClickListener() {
            public void onClick(View v) {
                showSea();
            }
        }), lp(3f, 1.0f, 0.06f));
        lobbyBody.addView(row, new LinearLayout.LayoutParams(WRAP, WRAP));
    }

    private void moveSeat(int i, int d) {
        int j = i + d;
        if (j < 0 || j >= room.size()) return;
        Pl t = room.get(i);
        room.set(i, room.get(j));
        room.set(j, t);
        for (int k = 0; k < room.size(); k++) if (room.get(k).kind == 0) myIdx = k;
        sendRoster();
        fillLobby();
    }

    private void addBot() {
        if (room.size() >= Net.MAX) return;
        Pl b = new Pl();
        b.kind = 2;
        b.level = sp.getInt("sbLvl", 3);
        boolean ok;
        int tries = 0;
        do {
            b.nick = BOT_NAMES[rnd.nextInt(BOT_NAMES.length)];
            ok = true;
            for (Pl q : room) if (q.nick.equals(b.nick)) ok = false;
            tries++;
        } while (!ok && tries < 50);
        if (!ok) b.nick = b.nick + (room.size() + 1);
        int an = 0;
        do {
            ok = true;
            an++;
            b.avatar = an < 60 ? rnd.nextInt(Avatars.CAT_START[1]) : rnd.nextInt(Avatars.TOTAL);
            for (Pl q : room) if (q.avatar == b.avatar) ok = false;
        } while (!ok);
        room.add(b);
        sendRoster();
        fillLobby();
    }

    private int humans() {
        int c = 0;
        for (Pl q : room) if (q.kind == 1 && !q.left) c++;
        return c;
    }

    private void startFromLobby() {
        if (room.size() < 2) {
            say("Спершу додайте бота або дочекайтеся гравців");
            return;
        }
        if (humans() == 0) {
            stopNet();
        } else if (net != null) {
            net.lock(true);
            net.broadcast("GO");
        }
        showPlace();
    }

    private void showLobbyGuest() {
        begin(S_LOBBY);
        phase = 0;
        lobbyBody = new LinearLayout(this);
        lobbyBody.setOrientation(LinearLayout.VERTICAL);
        lobbyBody.setPadding(px(0.6f), px(0.3f), px(0.6f), px(0.3f));
        root.addView(lobbyBody, new FrameLayout.LayoutParams(MATCH, MATCH));
        fillLobbyGuest();
    }

    private void fillLobbyGuest() {
        if (lobbyBody == null || screen != S_LOBBY || net == null || net.host) return;
        lobbyBody.removeAllViews();
        lobbyBody.addView(tv("Кімната", 0.6f, 0xFFFFD54F, true), lp(WRAP, WRAP, 0.05f));
        ScrollView sv = new ScrollView(this);
        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        for (int i = 0; i < room.size(); i++) list.addView(playerRow(room.get(i).avatar, roleOf(room.get(i), i)), new LinearLayout.LayoutParams(MATCH, WRAP));
        list.addView(tv("Очікуємо, поки господар почне гру…", 0.34f, 0xFFB0BEC5, false), lp(WRAP, WRAP, 0.1f));
        sv.addView(list);
        lobbyBody.addView(sv, new LinearLayout.LayoutParams(MATCH, 0, 1f));
        lobbyBody.addView(btn("Вийти", 0xFFC62828, new View.OnClickListener() {
            public void onClick(View v) {
                showSea();
            }
        }), lp(3f, 1.0f, 0.08f));
    }

    private void fillNetBar() {
        if (netBox == null || net == null) return;
        netBox.removeAllViews();
        boolean w = net.isWifi();
        boolean n = net.isInternet();
        LinearLayout row = new LinearLayout(this);
        row.addView(btn("Wifi", w ? SEL : 0xFF455A64, new View.OnClickListener() {
            public void onClick(View v) {
                if (net == null) return;
                net.setWifi(!net.isWifi());
                fillNetBar();
            }
        }), lp(2.4f, 0.85f, 0.04f));
        row.addView(btn("Інтернет", n ? SEL : 0xFF455A64, new View.OnClickListener() {
            public void onClick(View v) {
                if (net == null) return;
                if (net.isInternet()) {
                    net.setInternet(false);
                    netCode = "";
                    fillNetBar();
                } else if (!net.isBusy()) {
                    say("Підключення до інтернету…");
                    net.setInternet(true);
                }
            }
        }), lp(3.0f, 0.85f, 0.04f));
        netBox.addView(row);
        if (n && netCode.length() > 0) {
            LinearLayout cr = new LinearLayout(this);
            cr.setGravity(Gravity.CENTER_VERTICAL);
            cr.addView(tv("Код: " + netCode, 0.45f, 0xFFFFD54F, true), lp(WRAP, WRAP, 0.05f));
            cr.addView(btn("Поділитися", 0xFF1E88E5, new View.OnClickListener() {
                public void onClick(View v) {
                    android.content.Intent it = new android.content.Intent(android.content.Intent.ACTION_SEND);
                    it.setType("text/plain");
                    it.putExtra(android.content.Intent.EXTRA_TEXT, "Грай зі мною в Морський бій! Код кімнати: " + netCode);
                    startActivity(android.content.Intent.createChooser(it, "Поділитися кодом"));
                }
            }), lp(3.0f, 0.75f, 0.04f));
            netBox.addView(cr);
        }
    }

    // ---------- приєднання ----------
    private void showJoin() {
        stopNet();
        begin(S_JOIN);
        joinSig = "";
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(px(0.6f), px(0.4f), px(0.6f), px(0.4f));
        col.setFocusableInTouchMode(true);
        col.addView(tv("Кімнати у вашій Wi-Fi мережі", 0.5f, WHITE, true));
        LinearLayout crow = new LinearLayout(this);
        crow.setGravity(Gravity.CENTER_VERTICAL);
        crow.addView(tv("Або код кімнати з інтернету:", 0.34f, 0xFFB0BEC5, false), lp(WRAP, WRAP, 0.05f));
        final EditText etCode = new EditText(this);
        etCode.setSingleLine(true);
        etCode.setHint("00000");
        etCode.setTextColor(WHITE);
        etCode.setTextSize(TypedValue.COMPLEX_UNIT_PX, 0.42f * u);
        etCode.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(8)});
        crow.addView(etCode, lp(3.2f, WRAP, 0.05f));
        crow.addView(btn("Увійти", 0xFF2E7D32, new View.OnClickListener() {
            public void onClick(View v) {
                String c = Relay.cleanCode(etCode.getText().toString());
                if (c.length() < 4) {
                    say("Введіть код кімнати");
                    return;
                }
                joinCode(c);
            }
        }), lp(2.6f, 0.9f, 0.05f));
        col.addView(crow, new LinearLayout.LayoutParams(MATCH, WRAP));
        ScrollView sv = new ScrollView(this);
        joinList = new LinearLayout(this);
        joinList.setOrientation(LinearLayout.VERTICAL);
        sv.addView(joinList);
        col.addView(sv, new LinearLayout.LayoutParams(MATCH, 0, 1f));
        col.addView(btn("Назад", 0xFF455A64, new View.OnClickListener() {
            public void onClick(View v) {
                showSea();
            }
        }), lp(3f, 0.95f, 0.1f));
        root.addView(col, new FrameLayout.LayoutParams(MATCH, MATCH));
        col.requestFocus();
        disc = new Discovery.Listener(this);
        disc.start();
        final int g = gen;
        ui.post(new Runnable() {
            public void run() {
                if (g != gen || screen != S_JOIN || disc == null) return;
                refreshRooms();
                ui.postDelayed(this, 1000);
            }
        });
    }

    private void refreshRooms() {
        long now = System.currentTimeMillis();
        final ArrayList<Discovery.Room> list = new ArrayList<Discovery.Room>();
        for (Discovery.Room r : disc.rooms.values()) {
            if (now - r.seen <= 5000) list.add(r);
        }
        StringBuilder sig = new StringBuilder();
        for (Discovery.Room r : list) sig.append(r.ip).append(r.port).append(r.count).append(';');
        if (sig.toString().equals(joinSig)) return;
        joinSig = sig.toString();
        joinList.removeAllViews();
        if (list.isEmpty()) {
            joinList.addView(tv("Кімнат не знайдено. Гравці мають бути в одній Wi-Fi мережі.", 0.36f, 0xFFB0BEC5, false));
            return;
        }
        for (final Discovery.Room r : list) {
            final boolean ok = r.count < Net.MAX && !r.inGame;
            String t = r.name + "   " + r.ip + "   " + r.count + "/" + Net.MAX + (ok ? "" : (r.inGame ? "   йде гра" : "   заповнена"));
            joinList.addView(btn(t, ok ? 0xFF1E88E5 : 0xFF607D8B, new View.OnClickListener() {
                public void onClick(View v) {
                    if (ok) joinLan(r.ip, r.port);
                }
            }), lp(MATCH, 1.0f, 0.08f));
        }
    }

    private Net newGuestNet() {
        Net old = net;
        net = null;
        if (old != null) old.close();
        final Net[] ref = new Net[1];
        net = new Net(netCb(ref));
        ref[0] = net;
        net.setInfo(nick, avatar < 0 ? 0 : avatar);
        say("Підключення…");
        return net;
    }

    private void joinLan(String ip, int port) {
        newGuestNet().joinLan(ip, port);
    }

    private void joinCode(String code) {
        newGuestNet().joinCode(code, cid);
    }

    // ---------- розстановка ----------
    private void showPlace() {
        myReady = false;
        started = false;
        myRematch = false;
        for (Pl q : room) {
            q.ready = false;
            q.rem = false;
        }
        begin(S_PLACE);
        phase = 1;
        final PlaceView pv = new PlaceView(this);
        pview = pv;
        pv.theme = theme;
        pv.mix = sp.getBoolean("sbMix", false);
        pv.restoreOn = sp.getBoolean("sbRestore", true);
        pv.showRestore = room.size() > 2 && (net == null || net.host);
        root.addView(pv, new FrameLayout.LayoutParams(MATCH, MATCH));
        FrameLayout.LayoutParams ap = new FrameLayout.LayoutParams(WRAP, WRAP, Gravity.TOP | Gravity.RIGHT);
        ap.setMargins(0, px(0.25f), px(0.4f), 0);
        root.addView(audioBar(0.8f), ap);
        pv.listener = new PlaceView.Listener() {
            public void onChanged() {
            }

            public void onSound(int kind) {
                snd.play(kind);
            }

            public void onTheme(int t) {
                theme = t;
                sp.edit().putInt("sbTheme", t).commit();
            }

            public void onMix(boolean on) {
                sp.edit().putBoolean("sbMix", on).commit();
            }

            public void onRestore(boolean on) {
                sp.edit().putBoolean("sbRestore", on).commit();
            }

            public void onBack() {
                showSea();
            }

            public void onDone() {
                if (!pv.board.allPlaced() || myReady) return;
                myReady = true;
                pv.locked = true;
                myBoard = pv.board;
                if (net == null || (net.host && humans() == 0)) {
                    startHostMatch();
                    return;
                }
                pv.status = "Чекаємо інших гравців…";
                if (net.host) {
                    room.get(0).ready = true;
                    checkStartHost();
                } else {
                    net.send("READY");
                }
            }
        };
    }

    private void checkStartHost() {
        if (phase != 1 || !myReady || started || net == null || !net.host) return;
        for (Pl q : room) if (q.kind == 1 && !q.left && !q.ready) return;
        startHostMatch();
    }

    private void startHostMatch() {
        if (started) return;
        started = true;
        sendRoster();
        createMatch(true);
        buildBattle();
        m.dnd[myIdx] = sp.getBoolean("dnd", false);
        int first = 0;
        for (int i = 0; i < room.size(); i++) if (lastWinner.length() > 0 && room.get(i).nick.equals(lastWinner)) first = i;
        m.start(first);
    }

    private void createMatch(boolean host) {
        int n = room.size();
        String[] names = new String[n];
        boolean[] bot = new boolean[n];
        int[] lv = new int[n];
        for (int i = 0; i < n; i++) {
            names[i] = room.get(i).nick;
            bot[i] = room.get(i).kind == 2;
            lv[i] = room.get(i).level;
        }
        m = new Match(n, myIdx, host, names, bot, lv, ev);
        m.restoreOn = sp.getBoolean("sbRestore", true);
        myBoard.baseTheme = theme;
        for (int i = 0; i < myBoard.shot.length; i++) myBoard.shot[i] = 0;
        for (int i = 0; i < n; i++) {
            m.boards[i].baseTheme = theme;
            if (host && bot[i]) {
                m.boards[i].randomize(rnd);
                if (sp.getBoolean("sbMix", false)) {
                    for (Board.Ship s : m.boards[i].ships) s.theme = rnd.nextInt(Painter.THEMES.length);
                }
            }
        }
        m.boards[myIdx] = myBoard;
        lastRes = "";
    }

    // ---------- повідомлення ----------
    private void handleHost(int pid, String line) {
        int i = idxOfPid(pid);
        if (i < 0) return;
        String c = line.split("\\|", 2)[0];
        if (c.equals("READY")) {
            if (phase == 1) {
                room.get(i).ready = true;
                checkStartHost();
            }
        } else if (c.equals("REMATCH")) {
            if (phase == 3) {
                room.get(i).rem = true;
                checkRematchHost();
            }
        } else if (m != null) {
            m.fromGuest(i, line);
        }
    }

    private void handleGuest(String line) {
        String[] f = line.split("\\|", -1);
        String c = f[0];
        try {
            if (c.equals("ROSTER")) {
                myIdx = Integer.parseInt(f[1]);
                int n = Integer.parseInt(f[2]);
                room.clear();
                for (int i = 0; i < n; i++) {
                    Pl q = new Pl();
                    q.nick = f[3 + i * 3];
                    q.avatar = Integer.parseInt(f[4 + i * 3]);
                    q.kind = Integer.parseInt(f[5 + i * 3]);
                    room.add(q);
                }
                if (screen == S_LOBBY) fillLobbyGuest();
            } else if (c.equals("GO")) {
                showPlace();
            } else if (c.equals("KICK")) {
                say("Господар вилучив вас з кімнати");
                showSea();
            } else if (c.equals("START")) {
                if (started || phase != 1 || !myReady) return;
                started = true;
                createMatch(false);
                buildBattle();
                m.dnd[myIdx] = sp.getBoolean("dnd", false);
                if (m.dnd[myIdx]) net.send("DND|1");
                m.turn = Integer.parseInt(f[1]);
                ev.turn(m.turn, false);
            } else if (m != null) {
                m.fromHost(line);
            }
        } catch (Exception e) {
            // некоректне повідомлення ігноруємо
        }
    }

    // ---------- бій ----------
    private final Match.Ev ev = new Match.Ev() {
        public void send(int idx, String line) {
            Net n = net;
            if (n == null) return;
            if (n.host) {
                if (idx < 0) n.broadcast(line);
                else if (idx < room.size() && room.get(idx).kind == 1 && !room.get(idx).left) n.sendTo(room.get(idx).pid, line);
            } else {
                n.send(line);
            }
        }

        public void result(int shooter, int target, int cell, int res, boolean fin) {
            if (bv == null || m == null) return;
            bv.last[target] = cell;
            if (res >= 1) bv.flashHit(shooter);
            if (autoView && bv.rMode == 0 && target != bv.rightIdx) bv.sel = target;
            if (target == bv.rightIdx || target == bv.sel || shooter == myIdx) {
                sounds(res);
                bv.pulse(target, cell, res);
            }
            String w = res == 0 ? "мимо" : (res == 1 ? "влучання" : "потоплено");
            if (shooter == myIdx) {
                awaiting = false;
                lastRes = res == 0 ? "Мимо" : (res == 1 ? "Влучання!" : "Потоплено!");
            } else {
                lastRes = m.names[shooter] + " → " + m.names[target] + ": " + w;
            }
            bv.invalidate();
        }

        public void turn(int idx, boolean restore) {
            if (bv == null || m == null) return;
            saveSea();
            bv.turn = idx;
            bv.tgtOfTurn = m.tgt(idx);
            if (autoView && bv.rMode == 0 && !restore && idx != myIdx && bv.tgtOfTurn >= 0 && bv.tgtOfTurn != bv.rightIdx) bv.sel = bv.tgtOfTurn;
            myTurn = idx == myIdx && m.alive[myIdx] && !restore && !m.over;
            awaiting = false;
            bv.myTurn = myTurn;
            computeRight();
            String base = lastRes.length() > 0 ? lastRes + ". " : "";
            lastRes = "";
            String text;
            if (restore) {
                text = idx == myIdx ? "Відновіть один потоплений корабель" : m.names[idx] + " відновлює корабель";
            } else if (idx == myIdx) {
                text = base + "Ваш хід — стріляйте по " + m.names[m.tgt(idx)];
            } else {
                text = base + "Ходить " + m.names[idx] + (m.n > 2 ? " → " + m.names[m.tgt(idx)] : "");
            }
            if (restore && idx == myIdx) beginRestore();
            else if (bv.rMode > 0) endRestoreUi();
            setMsg(text);
            if (m.host && !restore && !m.over && idx >= 0 && m.bot[idx]) botLater(botDelay(idx));
            refreshTurn();
        }

        public void out(int i) {
            if (bv == null || m == null) return;
            if (i == myIdx) {
                myTurn = false;
                bv.myTurn = false;
                if (bv.rMode > 0) endRestoreUi();
                banner("Поразка", 0xFFFF8A80, 1500);
                final int g = gen;
                ui.postDelayed(new Runnable() {
                    public void run() {
                        if (g == gen) snd.play(Snd.SB_LOSE);
                    }
                }, 700);
            } else {
                lastRes = lastRes + (lastRes.length() > 0 ? ". " : "") + m.names[i] + " вибуває з гри";
            }
            computeRight();
            refreshTurn();
            bv.invalidate();
            if (i == myIdx && !m.over && m.n > 2) {
                boolean botsOnly = true;
                for (int k = 0; k < m.n; k++) if (k != myIdx && !m.bot[k]) botsOnly = false;
                if (botsOnly) {
                    m.over = true;
                    over(-1);
                }
            }
        }

        public void over(final int winner) {
            if (bv == null || m == null || phase == 3) return;
            forgetSea();
            phase = 3;
            myTurn = false;
            bv.myTurn = false;
            bv.reveal = true;
            if (bv.rMode > 0) endRestoreUi();
            final boolean win = winner == myIdx;
            if (winner >= 0 && winner < m.n) lastWinner = m.names[winner];
            if (win) {
                banner("Перемога!", 0xFF69F0AE, 1500);
                final int g0 = gen;
                ui.postDelayed(new Runnable() {
                    public void run() {
                        if (g0 == gen) snd.play(Snd.SB_WIN);
                    }
                }, 600);
            }
            String opp;
            if (m.n == 2) {
                int o = 1 - myIdx;
                opp = m.bot[o] ? "ботом" + (net == null || net.host ? " (" + Bot.LEVELS[room.get(o).level] + ")" : "") : m.names[o];
            } else {
                opp = m.n + " гравців";
            }
            addHist(opp, win);
            refreshTurn();
            final int g = gen;
            ui.postDelayed(new Runnable() {
                public void run() {
                    if (g != gen) return;
                    String t = winner < 0 ? "Гру завершено" : (win ? "" : "Перемога за: " + m.names[winner]);
                    showEnd(win, t, true);
                }
            }, 1500);
        }

        public void restored(int idx) {
            if (bv == null) return;
            saveSea();
            if (idx != myIdx) lastRes = "Відновлення корабля: " + m.names[idx];
            bv.invalidate();
        }

        public void emoji(int from, int k) {
            if (bv != null && k >= 0 && k < EMOJI.length) bv.showEmoji(from, EMOJI[k]);
        }

        public void poke(int from) {
            if (bv == null) return;
            bv.shake();
            try {
                Vibrator vb = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
                if (vb != null) vb.vibrate(180);
            } catch (Exception e) {
                // ignore
            }
            sfx(Snd.POKE);
            say(m.names[from] + " штурхнув вас");
        }

        public void blocked(String nickName) {
            say("Гравець (" + nickName + ") заблокував сповіщення");
        }

        public void paused(int by) {
            pausedBy = by;
            if (pauseIcon != null) pauseIcon.setType(by == myIdx ? IconView.PLAY : IconView.PAUSE);
            setMsg(lastMsg);
            refreshTurn();
        }
    };

    private void computeRight() {
        if (bv == null || m == null) return;
        int t = m.alive[myIdx] ? m.tgt(myIdx) : (m.turn >= 0 && m.alive[m.turn] ? m.tgt(m.turn) : -1);
        if (t < 0) {
            for (int i = 0; i < m.n; i++) if (i != myIdx && m.alive[i]) {
                t = i;
                break;
            }
        }
        if (t < 0) t = (myIdx + 1) % m.n;
        bv.rightIdx = t;
        if (bv.sel == t && t != myIdx && bv.rMode == 0) bv.sel = myIdx;
    }

    private void buildBattle() {
        begin(S_BATTLE);
        phase = 2;
        pausedBy = -1;
        awaiting = false;
        restoreWait = false;
        botBusy = false;
        bv = new BattleView(this);
        bv.theme = theme;
        bv.boards = m.boards;
        bv.n = m.n;
        bv.me = myIdx;
        bv.sel = myIdx;
        bv.names = m.names;
        bv.alive = m.alive;
        bv.turn = -1;
        bv.listener = new BattleView.Listener() {
            public void onShoot(int cell) {
                myShot(cell);
            }

            public void onEmoji(int to) {
                if (bv.rMode == 0) showEmojiPicker(to);
            }

            public void onPoke(int to) {
                pokePlayer(to);
            }

            public void onSelect(int idx) {
                if (bv.rMode != 0) return;
                bv.sel = idx;
                bv.invalidate();
            }

            public void onRestoreTap(int cell) {
                restoreTap(cell);
            }
        };
        computeRight();
        root.addView(bv, new FrameLayout.LayoutParams(MATCH, MATCH));
        bv.bots = m.bot;
        addBattleIcons();
        bnr = new Banner(this);
        root.addView(bnr, new FrameLayout.LayoutParams(MATCH, MATCH));
        banner("До бою", 0xFFFFD54F, 1400);
    }

    private IconView icon(int type, View.OnClickListener l) {
        IconView v = new IconView(this, type);
        v.setOnClickListener(l);
        return v;
    }

    private void addBattleIcons() {
        final LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setGravity(Gravity.CENTER_HORIZONTAL);
        root.addView(col, new FrameLayout.LayoutParams(WRAP, WRAP, Gravity.TOP | Gravity.LEFT));
        final int gn = m.n;
        final int g0 = gen;
        root.post(new Runnable() {
            public void run() {
                if (g0 != gen || bv == null || m == null) return;
                BattleView.Lay l = BattleView.layout(root.getWidth(), root.getHeight());
                int s = Math.round(l.is), mh = Math.round(0.14f * l.u), mv = Math.round(0.06f * l.u);
                // велика кнопка ходу 2x2
                turnIcon = icon(IconView.TURN, new View.OnClickListener() {
                    public void onClick(View v) {
                        showSpeed();
                    }
                });
                LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(2 * s + 2 * mh, 2 * s + 2 * mv);
                bp.setMargins(mh, mv, mh, mv);
                col.addView(turnIcon, bp);
                // око + сповіщення
                LinearLayout r2 = new LinearLayout(SeaActivity.this);
                eyeIcon = icon(IconView.EYE, new View.OnClickListener() {
                    public void onClick(View v) {
                        if (m == null || m.n <= 2) {
                            say("Автоперегляд доступний від 3 гравців");
                            return;
                        }
                        autoView = !autoView;
                        sp.edit().putBoolean("sbAuto", autoView).commit();
                        eyeIcon.setCrossed(!autoView);
                        say(autoView ? "Автоперегляд: поле, по якому стріляють" : "Автоперегляд вимкнено");
                    }
                });
                if (gn > 2) {
                    eyeIcon.setCrossed(!autoView);
                } else {
                    eyeIcon.setBg(0x33000000);
                    eyeIcon.setAlpha(0.4f);
                }
                dndIcon = icon(IconView.DND, new View.OnClickListener() {
                    public void onClick(View v) {
                        boolean on = !sp.getBoolean("dnd", false);
                        sp.edit().putBoolean("dnd", on).commit();
                        applyDnd();
                        say(on ? "Сповіщення вимкнено" : "Сповіщення увімкнено");
                    }
                });
                LinearLayout.LayoutParams p1 = new LinearLayout.LayoutParams(s, s);
                p1.setMargins(mh, mv, mh, mv);
                LinearLayout.LayoutParams p2 = new LinearLayout.LayoutParams(s, s);
                p2.setMargins(mh, mv, mh, mv);
                r2.addView(eyeIcon, p1);
                r2.addView(dndIcon, p2);
                col.addView(r2);
                col.addView(audioBar(s / (float) px(1f), 0.14f));
                LinearLayout r4 = new LinearLayout(SeaActivity.this);
                pauseIcon = icon(IconView.PAUSE, new View.OnClickListener() {
                    public void onClick(View v) {
                        togglePause();
                    }
                });
                LinearLayout.LayoutParams p3 = new LinearLayout.LayoutParams(s, s);
                p3.setMargins(mh, mv, mh, mv);
                LinearLayout.LayoutParams p4 = new LinearLayout.LayoutParams(s, s);
                p4.setMargins(mh, mv, mh, mv);
                r4.addView(pauseIcon, p3);
                r4.addView(icon(IconView.EXIT, new View.OnClickListener() {
                    public void onClick(View v) {
                        askExit();
                    }
                }), p4);
                col.addView(r4);
                int colH = (2 * s + 4 * mv) + 3 * (s + 2 * mv), colW = 2 * s + 4 * mh;
                FrameLayout.LayoutParams ap = (FrameLayout.LayoutParams) col.getLayoutParams();
                ap.leftMargin = (int) (l.cx - colW / 2f);
                ap.topMargin = (int) (l.y0 + 10 * l.ec + mv - colH);
                col.setLayoutParams(ap);
                updateDndIcon();
                if (pausedBy >= 0 && pauseIcon != null) pauseIcon.setType(pausedBy == myIdx ? IconView.PLAY : IconView.PAUSE);
                refreshTurn();
            }
        });
    }

    private void updateDndIcon() {
        if (dndIcon != null) dndIcon.setBg(sp.getBoolean("dnd", false) ? 0xFFD50000 : 0);
    }

    private void applyDnd() {
        boolean on = sp.getBoolean("dnd", false);
        updateDndIcon();
        if (m == null) return;
        if (m.host) m.dnd[myIdx] = on;
        else if (net != null) net.send("DND|" + (on ? 1 : 0));
    }

    private void refreshTurn() {
        if (turnIcon == null) return;
        long now = System.currentTimeMillis();
        boolean ok = myTurn && !awaiting && pausedBy < 0 && now >= busyUntil && bv != null && bv.rMode == 0;
        turnIcon.setBg(ok ? 0xFF2E7D32 : 0xFFC62828);
        turnIcon.setFlip(!myTurn);
        turnIcon.setAlpha(0.8f);
        if (myTurn && now < busyUntil) {
            final int g = gen;
            ui.postDelayed(new Runnable() {
                public void run() {
                    if (g == gen) refreshTurn();
                }
            }, busyUntil - now + 30);
        }
    }

    private void setMsg(String s) {
        lastMsg = s;
        if (bv == null || m == null) return;
        if (pausedBy == myIdx) bv.msg = "Пауза — натисніть ▶, щоб продовжити";
        else if (pausedBy >= 0) bv.msg = "Пауза: " + m.names[pausedBy];
        else bv.msg = s;
        bv.invalidate();
    }

    private String lastMsg = "";

    private void myShot(int cell) {
        if (m == null || m.over || bv.rMode != 0) return;
        int t = bv.rightIdx;
        if (m.boards[t].shot[cell] != 0) return;
        if (!myTurn || awaiting || pausedBy >= 0 || System.currentTimeMillis() < busyUntil) {
            bv.cross(cell);
            snd.play(Snd.WRONG);
            return;
        }
        bv.last[t] = cell;
        awaiting = true;
        refreshTurn();
        if (m.host) {
            if (!m.shoot(myIdx, cell)) {
                awaiting = false;
                bv.cross(cell);
                snd.play(Snd.WRONG);
                refreshTurn();
            }
        } else if (net != null) {
            net.send("SHOT|" + cell);
        }
        bv.invalidate();
    }

    private void botLater(int ms) {
        if (botBusy) return;
        botBusy = true;
        final int g = gen;
        ui.postDelayed(new Runnable() {
            public void run() {
                botBusy = false;
                if (g == gen) botTurn();
            }
        }, ms);
    }

    private void botTurn() {
        if (m == null || m.over || !m.host) return;
        int b = m.turn;
        if (b < 0 || !m.bot[b] || !m.alive[b] || m.restore) return;
        long wait = busyUntil - System.currentTimeMillis();
        if (m.pauseBy >= 0 || m.pending() || wait > 0) {
            botLater(m.pauseBy >= 0 || m.pending() ? 400 : (int) Math.max(60, wait));
            return;
        }
        int t = m.tgt(b);
        if (t < 0) return;
        m.shoot(b, m.bots[b].next(m.boards[t]));
    }

    private void sounds(int res) {
        snd.play(res == 0 ? Snd.MISS : (res == 1 ? Snd.HIT : Snd.SUNK));
        busyUntil = System.currentTimeMillis() + (res == 0 ? LOCK_MISS[speed - 1] : LOCK_HIT[speed - 1]);
        refreshTurn();
    }

    private void togglePause() {
        if (m == null || m.over) return;
        if (pausedBy >= 0 && pausedBy != myIdx) {
            say("Паузу поставив " + m.names[pausedBy]);
            return;
        }
        boolean on = pausedBy < 0;
        if (m.host) m.pause(myIdx, on);
        else if (net != null) net.send("PAUSE|" + (on ? 1 : 0));
    }

    private void askExit() {
        forgetSea();
        showSea();
    }

    // ---------- відновлення корабля ----------
    private void beginRestore() {
        Board b = m.boards[myIdx];
        boolean any = false;
        for (Board.Ship s : b.ships) if (b.sunk(s)) any = true;
        if (!any) {
            final int g = gen;
            ui.post(new Runnable() {
                public void run() {
                    if (g != gen || m == null || !m.restore || m.turn != myIdx) return;
                    if (m.host) m.skipRestore(myIdx);
                    else if (net != null) net.send("RSKIP");
                }
            });
            return;
        }
        bv.rMode = 1;
        bv.rShip = -1;
        bv.gr = -1;
        bv.sel = myIdx;
        restoreOk = btn("Поставити", 0xFF2E7D32, new View.OnClickListener() {
            public void onClick(View v) {
                restoreConfirm();
            }
        });
        restoreSkip = btn("Пропустити", 0xFF455A64, new View.OnClickListener() {
            public void onClick(View v) {
                restoreSkipTap();
            }
        });
        restoreOk.setAlpha(0.4f);
        BattleView.Lay l = BattleView.layout(root.getWidth(), root.getHeight());
        int w = (int) (l.midW - 0.5f * l.u), h = (int) (0.8f * l.u);
        int x = (int) (l.midX + 0.25f * l.u);
        FrameLayout.LayoutParams p1 = new FrameLayout.LayoutParams(w, h, Gravity.TOP | Gravity.LEFT);
        p1.leftMargin = x;
        p1.topMargin = (int) (0.95f * l.u);
        FrameLayout.LayoutParams p2 = new FrameLayout.LayoutParams(w, h, Gravity.TOP | Gravity.LEFT);
        p2.leftMargin = x;
        p2.topMargin = (int) (1.85f * l.u);
        root.addView(restoreOk, p1);
        root.addView(restoreSkip, p2);
    }

    private void endRestoreUi() {
        if (bv != null) {
            bv.rMode = 0;
            bv.rShip = -1;
        }
        if (restoreOk != null) root.removeView(restoreOk);
        if (restoreSkip != null) root.removeView(restoreSkip);
        restoreOk = null;
        restoreSkip = null;
    }

    private void restoreTap(int cell) {
        if (m == null || !m.restore || m.turn != myIdx) return;
        Board b = m.boards[myIdx];
        int r = cell / 10, c = cell % 10;
        int id = b.shipAt(r, c);
        if (id >= 0 && b.sunk(b.ships[id]) && id != bv.rShip) {
            bv.rShip = id;
            bv.rMode = 2;
            bv.gr = -1;
            bv.gOk = false;
            if (restoreOk != null) restoreOk.setAlpha(0.4f);
            setMsg("Оберіть вільне місце для корабля");
            bv.invalidate();
            return;
        }
        if (bv.rMode != 2 || bv.rShip < 0) return;
        Board.Ship s = b.ships[bv.rShip];
        if (bv.gr == r && bv.gc == c) {
            bv.gh = !bv.gh;
        } else {
            bv.gr = r;
            bv.gc = c;
            bv.gh = true;
        }
        bv.gOk = Match.canRestore(b, s, r, c, bv.gh);
        if (!bv.gOk && Match.canRestore(b, s, r, c, !bv.gh)) {
            bv.gh = !bv.gh;
            bv.gOk = true;
        }
        if (restoreOk != null) restoreOk.setAlpha(bv.gOk ? 1f : 0.4f);
        bv.invalidate();
    }

    private void restoreConfirm() {
        if (m == null || !m.restore || bv.rMode != 2 || !bv.gOk || bv.rShip < 0) return;
        Board.Ship s = m.boards[myIdx].ships[bv.rShip];
        int len = s.len, r0 = s.r, c0 = s.c, r1 = bv.gr, c1 = bv.gc;
        boolean h = bv.gh;
        endRestoreUi();
        snd.play(Snd.PLACE);
        if (m.host) {
            m.restoreShip(myIdx, len, r0, c0, r1, c1, h);
        } else {
            m.applyRestore(myIdx, len, r0, c0, r1, c1, h);
            if (net != null) net.send("RESTORE|" + len + "|" + r0 + "|" + c0 + "|" + r1 + "|" + c1 + "|" + (h ? 1 : 0));
        }
    }

    private void restoreSkipTap() {
        if (m == null || !m.restore) return;
        endRestoreUi();
        if (m.host) m.skipRestore(myIdx);
        else if (net != null) net.send("RSKIP");
    }

    // ---------- смайли, штурхан ----------
    private void showEmojiPicker(final int to) {
        if (emoDlg != null) {
            try {
                emoDlg.dismiss();
            } catch (Exception e) {
                // ignore
            }
        }
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        for (int r = 0; r < 4; r++) {
            LinearLayout row = new LinearLayout(this);
            for (int c = 0; c < 8; c++) {
                final int k = r * 8 + c;
                TextView e = tv(EMOJI[k], 0.85f, WHITE, false);
                e.setGravity(Gravity.CENTER);
                e.setOnClickListener(new View.OnClickListener() {
                    public void onClick(View v) {
                        if (emoDlg != null) emoDlg.dismiss();
                        sendEmoji(k, to);
                    }
                });
                row.addView(e, new LinearLayout.LayoutParams(0, px(1.3f), 1f));
            }
            box.addView(row, new LinearLayout.LayoutParams(MATCH, WRAP));
        }
        Dialog d = dialog(box, true);
        emoDlg = d;
        d.show();
        if (d.getWindow() != null) d.getWindow().setLayout(px(12.5f), WRAP);
    }

    private void sendEmoji(int k, int to) {
        if (bv == null || m == null) return;
        bv.showEmoji(myIdx, EMOJI[k]);
        if (m.host) m.emoji(myIdx, k, to);
        else if (net != null) net.send("EMO|" + k + "|" + to);
        boolean anyHuman = false;
        for (int i = 0; i < m.n; i++) if (i != myIdx && !m.bot[i]) anyHuman = true;
        if (!anyHuman && rnd.nextInt(2) == 0) {
            final int g = gen;
            final int from = to >= 0 && m.bot[to] ? to : -1;
            ui.postDelayed(new Runnable() {
                public void run() {
                    if (g != gen || bv == null || m == null) return;
                    int who = from;
                    if (who < 0) {
                        for (int i = 0; i < m.n; i++) if (m.bot[i] && m.alive[i]) {
                            who = i;
                            break;
                        }
                    }
                    if (who >= 0) bv.showEmoji(who, EMOJI[GOOD_EMO[rnd.nextInt(GOOD_EMO.length)]]);
                }
            }, 1200 + rnd.nextInt(1000));
        }
    }

    private void pokePlayer(int to) {
        if (m == null || to == myIdx) return;
        if (m.bot[to]) {
            if (m.host) showBotDlg(to);
            else say("Налаштовує бота лише господар кімнати");
            return;
        }
        sfx(Snd.POKE);
        if (m.host) m.poke(myIdx, to);
        else if (net != null) net.send("POKE|" + to);
    }

    private int botDelay(int idx) {
        int sp2 = idx >= 0 && idx < room.size() ? room.get(idx).speed : 3;
        return Math.max(30, Math.round(BOT_BASE[speed - 1] * BOT_MUL[sp2 - 1]));
    }

    private Dialog dlgSpeed, dlgBot;

    private void showSpeed() {
        if (dlgSpeed != null && dlgSpeed.isShowing()) return;
        LinearLayout box = column();
        box.addView(tv("Швидкість гри", 0.6f, WHITE, true), lp(WRAP, WRAP, 0.1f));
        final TextView lab = tv(SPD_NAMES[speed - 1], 0.45f, 0xFFFFD54F, true);
        box.addView(lab, lp(WRAP, WRAP, 0.1f));
        SeekBar sb = new SeekBar(this);
        sb.setMax(4);
        sb.setProgress(speed - 1);
        sb.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar b, int p, boolean user) {
                speed = p + 1;
                lab.setText(SPD_NAMES[p]);
                sp.edit().putInt("sbSpeed2", speed).commit();
            }

            public void onStartTrackingTouch(SeekBar b) {
            }

            public void onStopTrackingTouch(SeekBar b) {
                snd.play(Snd.BTN);
            }
        });
        box.addView(sb, new LinearLayout.LayoutParams(px(8.0f), px(1.3f)));
        LinearLayout nums = new LinearLayout(this);
        for (int i = 1; i <= 5; i++) {
            TextView t = tv("" + i, 0.36f, 0xFFB0BEC5, false);
            t.setGravity(Gravity.CENTER);
            nums.addView(t, new LinearLayout.LayoutParams(0, WRAP, 1f));
        }
        box.addView(nums, new LinearLayout.LayoutParams(px(8.0f), WRAP));
        box.addView(tv("Анімація, постріли і ходи ботів", 0.3f, 0xFF90A4AE, false), lp(WRAP, WRAP, 0.1f));
        box.addView(btn("Готово", 0xFF1565C0, new View.OnClickListener() {
            public void onClick(View v) {
                if (dlgSpeed != null) dlgSpeed.dismiss();
            }
        }), lp(3.0f, 0.95f, 0.1f));
        dlgSpeed = dialog(box, true);
        showSized(dlgSpeed, 9.6f);
    }

    private void showBotDlg(final int idx) {
        if (m == null || idx < 0 || idx >= room.size()) return;
        final Pl q = room.get(idx);
        if (dlgBot != null && dlgBot.isShowing()) dlgBot.dismiss();
        LinearLayout box = column();
        box.addView(tv("Бот: " + q.nick, 0.6f, WHITE, true), lp(WRAP, WRAP, 0.1f));
        box.addView(tv("Складність", 0.34f, 0xFF90A4AE, false), lp(WRAP, WRAP, 0.05f));
        final Button[] lv = new Button[4];
        final Button[] sv = new Button[5];
        LinearLayout r1 = new LinearLayout(this);
        for (int k = 0; k < 4; k++) {
            final int lk = k;
            lv[k] = btn(LV_SHORT[k], q.level == k ? SEL : 0xFF455A64, new View.OnClickListener() {
                public void onClick(View v) {
                    q.level = lk;
                    if (m != null && idx < m.bots.length) m.bots[idx] = new Bot(lk);
                    for (int j = 0; j < 4; j++) lv[j].setBackground(btnBg(j == lk ? SEL : 0xFF455A64));
                }
            });
            r1.addView(lv[k], new LinearLayout.LayoutParams(px(2.0f), px(0.85f)));
            ((LinearLayout.LayoutParams) lv[k].getLayoutParams()).setMargins(px(0.04f), 0, px(0.04f), 0);
        }
        box.addView(r1);
        box.addView(tv("Швидкість ходів", 0.34f, 0xFF90A4AE, false), lp(WRAP, WRAP, 0.05f));
        LinearLayout r2 = new LinearLayout(this);
        for (int k = 0; k < 5; k++) {
            final int sk = k + 1;
            sv[k] = btn("" + sk, q.speed == sk ? SEL : 0xFF455A64, new View.OnClickListener() {
                public void onClick(View v) {
                    q.speed = sk;
                    for (int j = 0; j < 5; j++) sv[j].setBackground(btnBg(j + 1 == sk ? SEL : 0xFF455A64));
                }
            });
            r2.addView(sv[k], new LinearLayout.LayoutParams(px(1.6f), px(0.85f)));
            ((LinearLayout.LayoutParams) sv[k].getLayoutParams()).setMargins(px(0.04f), 0, px(0.04f), 0);
        }
        box.addView(r2);
        box.addView(btn("Готово", 0xFF1565C0, new View.OnClickListener() {
            public void onClick(View v) {
                if (dlgBot != null) dlgBot.dismiss();
            }
        }), lp(3.0f, 0.95f, 0.1f));
        dlgBot = dialog(box, true);
        showSized(dlgBot, 9.8f);
    }

    private Dialog dialog(View content, boolean cancel) {
        LinearLayout wrap = new LinearLayout(this);
        wrap.setOrientation(LinearLayout.VERTICAL);
        wrap.setBackground(bg(0xFF263238, 0.3f));
        wrap.setPadding(px(0.5f), px(0.4f), px(0.5f), px(0.4f));
        wrap.addView(content);
        Dialog d = new Dialog(this, android.R.style.Theme_DeviceDefault_Dialog_NoActionBar);
        d.setContentView(wrap);
        d.setCancelable(cancel);
        if (d.getWindow() != null) d.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        return d;
    }

    // ---------- кінець ----------
    private boolean lastWin;

    private void showEnd(boolean win, String text, boolean rematchOk) {
        lastWin = win;
        LinearLayout box = column();
        box.addView(tv(win ? "Перемога!" : "Кінець гри", 0.8f, win ? 0xFF69F0AE : 0xFFFFD54F, true), lp(WRAP, WRAP, 0.1f));
        if (text.length() > 0) box.addView(tv(text, 0.38f, WHITE, false), lp(WRAP, WRAP, 0.1f));
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        if (rematchOk) {
            final Button rb = btn("Реванш", 0xFF2E7D32, null);
            rb.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) {
                    snd.play(Snd.BTN);
                    if (net == null || (net.host && humans() == 0)) {
                        showPlace();
                        return;
                    }
                    if (myRematch) return;
                    myRematch = true;
                    rb.setText("Чекаємо…");
                    rb.setAlpha(0.6f);
                    if (net.host) {
                        room.get(0).rem = true;
                        checkRematchHost();
                    } else {
                        net.send("REMATCH");
                    }
                }
            });
            row.addView(rb, new LinearLayout.LayoutParams(0, px(1.0f), 1f));
        }
        if (phase == 3 && bv != null) {
            row.addView(btn("Поля", 0xFF00897B, new View.OnClickListener() {
                public void onClick(View v) {
                    snd.play(Snd.BTN);
                    if (endDlg != null) endDlg.dismiss();
                    endDlg = null;
                    showEndBar();
                }
            }), new LinearLayout.LayoutParams(0, px(1.0f), 1f));
        }
        row.addView(btn("В меню", 0xFF4FC3F7, new View.OnClickListener() {
            public void onClick(View v) {
                showSea();
            }
        }), new LinearLayout.LayoutParams(0, px(1.0f), 1f));
        for (int k = 0; k < row.getChildCount(); k++) {
            ((LinearLayout.LayoutParams) row.getChildAt(k).getLayoutParams()).setMargins(px(0.1f), px(0.1f), px(0.1f), px(0.1f));
        }
        box.addView(row, new LinearLayout.LayoutParams(px(7.2f), WRAP));
        Dialog d = dialog(box, false);
        endDlg = d;
        showSized(d, 8.6f);
    }

    private View endBar;

    // після закриття вікна кінця: невелика смужка знизу, щоб бачити поля
    private void showEndBar() {
        if (endBar != null) root.removeView(endBar);
        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.addView(btn("Меню гри", 0xFF4FC3F7, new View.OnClickListener() {
            public void onClick(View v) {
                snd.play(Snd.BTN);
                if (endBar != null) root.removeView(endBar);
                endBar = null;
                showEnd(lastWin, "", true);
            }
        }), new LinearLayout.LayoutParams(px(3.2f), px(0.9f)));
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(WRAP, WRAP, Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        lp.setMargins(0, 0, 0, px(0.2f));
        root.addView(bar, lp);
        endBar = bar;
    }

    private void checkRematchHost() {
        if (phase != 3 || net == null || !net.host || !myRematch) return;
        for (Pl q : room) if (q.kind == 1 && !q.left && !q.rem) return;
        for (int i = room.size() - 1; i >= 1; i--) if (room.get(i).left) room.remove(i);
        sendRoster();
        net.broadcast("GO");
        showPlace();
    }

    @Override
    public void onBackPressed() {
        if (screen == S_SEA) {
            finish();
        } else if (screen == S_LOBBY || screen == S_JOIN || screen == S_HIST || screen == S_PLACE) {
            showSea();
        } else if (screen == S_BATTLE) {
            askExit();
        } else {
            showSea();
        }
    }
}
