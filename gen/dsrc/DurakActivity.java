package com.example.gryntsivgames.durak;

import android.Manifest;
import android.app.Dialog;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Canvas;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.os.Bundle;
import android.os.Vibrator;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.Animation;
import android.view.animation.CycleInterpolator;
import android.view.animation.TranslateAnimation;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.example.gryntsivgames.uno.Avatars;
import com.example.gryntsivgames.uno.BaseActivity;
import com.example.gryntsivgames.uno.Card;
import com.example.gryntsivgames.uno.Cfg;
import com.example.gryntsivgames.uno.IconView;
import com.example.gryntsivgames.uno.Snd;
import com.example.gryntsivgames.uno.Theme;
import com.example.gryntsivgames.uno.Bg;
import com.example.gryntsivgames.uno.VoiceHelper;

import java.util.ArrayList;

public class DurakActivity extends BaseActivity {
    private static final int S_MENU = 0, S_JOIN = 1, S_LOBBY = 2, S_GAME = 3;
    private static final int WRAP = ViewGroup.LayoutParams.WRAP_CONTENT;
    private static final int MATCH = ViewGroup.LayoutParams.MATCH_PARENT;
    private static final String[] LEVELS = {"Аматор", "Студент", "Геній", "Легенда"};

    private FrameLayout root;
    private int screen;
    private DHost host;
    private Client client;
    private Link toServer;
    private boolean isHost;
    private Discovery.Listener disc;
    private final ArrayList<String[]> players = new ArrayList<String[]>();
    private int me;
    private boolean transferOn;
    private String pendingBanner;
    private boolean dndSent;

    private LinearLayout joinList;
    private String joinSig = "";
    private LinearLayout lobbyList;
    private TextView lobbyCount;
    private LinearLayout netBox;
    private String netCode = "";
    private boolean netBusy;
    private Button lobbyStart, lobbyBot;
    private Button lobbyTransfer;
    private TextView lobbyTrLbl;

    private DuTable table;
    private IconView ivPause, ivGear;
    private Button btnTake, btnPass, btnResume;
    private TextView pokeLbl;
    private DState gs, lastGs;
    private Dialog resultDlg, roomDlg;
    private LinearLayout roomList;
    private boolean settingsPaused, autoPaused;
    private int dstyle;
    private boolean wantLog;
    private VoiceHelper voice;
    private int micTarget = -2, pendingMicIdx = -2;
    private boolean voiceLock;
    private boolean askedResume, autoPassSent;
    private static final int REQ_MIC = 21;
    private DuCards prevPainter;
    private final Runnable micStop = new Runnable() {
        public void run() {
            finishMic();
        }
    };

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        dstyle = sp.getInt("dstyle", 1);
        voice = new VoiceHelper(this);
        root = new FrameLayout(this);
        setContentView(root);
        showMenu();
        askResume();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        stopNet();
        if (voice != null) voice.release();
    }

    // ---------- вибір колоди Дурака ----------
    @Override
    protected int deckCount() {
        return DuCards.DECKS.length;
    }

    @Override
    protected String deckTitle(int i) {
        return DuCards.DECKS[i];
    }

    @Override
    protected int deckSel() {
        return dstyle;
    }

    @Override
    protected void deckPick(int i) {
        dstyle = i;
        sp.edit().putInt("dstyle", i).commit();
    }

    @Override
    protected boolean previewDeck(Canvas c, int w, int h) {
        if (prevPainter == null) prevPainter = new DuCards(Theme.get(0));
        prevPainter.setStyle(dstyle);
        Card[] cs = {new Card(1, 12), new Card(0, 13), new Card(2, 11), new Card(3, 14), new Card(1, 9), new Card(0, 6)};
        float ch = h * 0.92f;
        float cw = ch * 0.667f;
        int k = cs.length;
        float gap = Math.max(u * 0.05f, (w - k * cw) / (k + 1));
        float y = h * 0.04f;
        for (int i = 0; i < cs.length; i++) prevPainter.draw(c, cs[i], gap + i * (cw + gap), y, cw, ch, 0);
        return true;
    }

    @Override
    public void onBackPressed() {
        if (screen == S_MENU) super.onBackPressed();
        else if (screen == S_GAME) leaveToMenu();
        else showMenu();
    }

    @Override
    protected boolean hostMode() {
        return isHost && host != null;
    }

    @Override
    protected boolean showHostItems() {
        return (isHost && host != null) || screen == S_MENU;
    }

    @Override
    protected void onBotDelay(int ms) {
        if (host != null) host.setBotDelay(ms);
    }

    @Override
    protected boolean musicIsGame() {
        return screen == S_GAME;
    }

    @Override
    protected String labelPass() {
        return "«Бито»: показувати постійно";
    }

    @Override
    protected String labelDraw() {
        return "«Беру»: показувати постійно";
    }

    @Override
    protected int addExtraSettings(LinearLayout right) {
        if (!showHostItems()) return 0;
        right.addView(onOff("Перевідний за замовчуванням", sp.getBoolean("transfer", false), new Toggle() {
            public void set(boolean on) {
                sp.edit().putBoolean("transfer", on).commit();
                if (host != null && isHost) {
                    host.setTransfer(on);
                    send("NOTE|" + (on ? "Наступної партії буде перевідний" : "Наступної партії перевідного не буде"));
                }
            }
        }));
        right.addView(onOff("Підкидати по черзі", sp.getBoolean("ordered", false), new Toggle() {
            public void set(boolean on) {
                sp.edit().putBoolean("ordered", on).commit();
                if (host != null && isHost) host.setOrdered(on);
            }
        }));
        return 2;
    }

    @Override
    protected void onSettingsClosed() {
        musicFor(screen == S_GAME);
        if (profileChanged && (screen == S_GAME || screen == S_LOBBY)) {
            send("NAME|" + nick + "|" + (avatar < 0 ? -1 : avatar) + "|" + sendPhoto());
        }
        if (settingsPaused) {
            settingsPaused = false;
            send("RESUME");
        }
        if (screen == S_MENU) {
            showMenu();
        } else if (screen == S_GAME && table != null) {
            table.setStyle(dstyle);
            table.hint = hint;
            table.setBg(bgId);
            applyState();
        }
    }

    private static int pi(String s) {
        try {
            return Integer.parseInt(s.trim());
        } catch (Exception e) {
            return 0;
        }
    }

    private String pname(int i) {
        return (i >= 0 && i < players.size()) ? players.get(i)[0] : "?";
    }

    private void send(String s) {
        if (toServer != null) toServer.send(s);
    }

    // ---------- мережа ----------
    private void stopDisc() {
        if (disc != null) {
            disc.stop();
            disc = null;
        }
    }

    private void stopNet() {
        stopDisc();
        if (host != null) {
            host.stop();
            host = null;
        }
        if (client != null) {
            client.close();
            client = null;
        }
        toServer = null;
        gs = null;
        lastGs = null;
        table = null;
        players.clear();
    }

    private void leaveToMenu() {
        if (host != null) host.forget();
        if (resultDlg != null && resultDlg.isShowing()) resultDlg.dismiss();
        if (roomDlg != null && roomDlg.isShowing()) roomDlg.dismiss();
        showMenu();
    }

    private void createRoom() {
        createRoom(null);
    }

    private void createRoom(String saved) {
        stopNet();
        netCode = "";
        netBusy = false;
        isHost = true;
        dndSent = false;
        pendingBanner = null;
        final DHost[] ref = new DHost[1];
        Link toUi = new Link() {
            public void send(final String l) {
                ui.post(new Runnable() {
                    public void run() {
                        if (host != null && host == ref[0]) onLine(l);
                    }
                });
            }
        };
        host = new DHost(nick, avatar < 0 ? -1 : avatar, sendPhoto(), cid, toUi);
        host.defLevel = bestLevel();
        host.setBotDelay(botDelay);
        host.setSaver(new DHost.Saver() {
            public void save(String d) {
                if (d == null) sp.edit().remove("saveD").apply();
                else sp.edit().putString("saveD", d).apply();
            }
        });
        if (saved != null && !host.loadState(saved)) {
            toast("Не вдалося відновити гру");
            sp.edit().remove("saveD").commit();
            host = null;
            showMenu();
            return;
        }
        ref[0] = host;
        final DHost hh = host;
        toServer = new Link() {
            public void send(String l) {
                hh.handleLocal(l);
            }
        };
        String err = host.start();
        if (err != null) {
            toast(err);
            host = null;
            showMenu();
            return;
        }
        if (saved != null) {
            host.resumeLoaded();
        } else {
            host.setTransfer(sp.getBoolean("transfer", false));
            host.setOrdered(sp.getBoolean("ordered", false));
            showLobby();
        }
    }

    private void connectCommon() {
        stopDisc();
        isHost = false;
        dndSent = false;
        pendingBanner = null;
        players.clear();
        client = new Client();
        final Client cc = client;
        toServer = new Link() {
            public void send(String l) {
                cc.send(l);
            }
        };
        toast("Підключення…");
    }

    private Client.Cb clientCb() {
        final Client cc = client;
        return new Client.Cb() {
            public void onLine(String line) {
                DurakActivity.this.onLine(line);
            }

            public void onClosed() {
                if (screen == S_MENU) return;
                toast("З'єднання втрачено");
                leaveToMenu();
            }

            public void onFail() {
                String m = cc.failMsg;
                toast(m == null || m.length() == 0 ? "Не вдалося підключитись" : m);
                showJoin();
            }
        };
    }

    private void joinRoom(Discovery.Room r) {
        if (r.inGame ? r.rejoin <= 0 : r.count >= 6) {
            toast("Кімната заповнена або гра вже триває");
            return;
        }
        connectCommon();
        client.connect(r.ip, r.port, "HELLO|" + nick + "|" + (avatar < 0 ? -1 : avatar) + "|" + sendPhoto() + "|" + cid, clientCb());
    }

    private void joinCode(String code) {
        connectCommon();
        client.connectRelay(code, cid, "HELLO|" + nick + "|" + (avatar < 0 ? -1 : avatar) + "|" + sendPhoto() + "|" + cid, clientCb());
    }

    private void onLine(String line) {
        String[] f = line.split("\\|", -1);
        try {
            if (f[0].equals("LOBBY")) {
                onLobby(f);
            } else if (f[0].equals("START")) {
                pendingBanner = "Поїхали";
                lastGs = null;
                if (screen == S_GAME && table != null) table.showBanner("Поїхали");
            } else if (f[0].equals("STATE")) {
                gs = DState.parse(f);
                if (screen != S_GAME) showGame();
                applyState();
            } else if (f[0].equals("EMO")) {
                int k = pi(f[3]);
                if (table != null && k >= 0 && k < EMOJI.length) table.showEmoji(pi(f[1]), EMOJI[k]);
                sfx(Snd.EMO);
            } else if (f[0].equals("LIS")) {
                if (table != null) table.setListening(pi(f[1]), f[2].equals("1"));
            } else if (f[0].equals("VLOCK")) {
                voiceLock = f[1].equals("1");
            } else if (f[0].equals("VBUSY")) {
                toast(f[1].equals("2") ? "Гра на паузі" : "Зачекайте, поки прозвучить попереднє повідомлення");
            } else if (f[0].equals("VOICE")) {
                int from = pi(f[1]);
                if (table != null) table.showEmoji(from, "🎤");
                if (voice != null) {
                    voice.play(f[3], new Runnable() {
                        public void run() {
                            send("VDONE");
                        }
                    });
                } else {
                    send("VDONE");
                }
            } else if (f[0].equals("LOG")) {
                if (wantLog) {
                    wantLog = false;
                    showLog(f.length > 1 ? f[1] : "");
                }
            } else if (f[0].equals("BLOCK")) {
                toast("Гравець (" + f[1] + ") заблокував сповіщення");
            } else if (f[0].equals("NOTE")) {
                toast(f[1]);
            } else if (f[0].equals("POKED")) {
                onPoked(pi(f[1]));
            } else if (f[0].equals("RES")) {
                onResult(f);
            } else if (f[0].equals("KICK")) {
                toast("Хост видалив вас з гри");
                leaveToMenu();
            } else if (f[0].equals("FULL")) {
                toast("Кімната заповнена, гра вже триває або доступ закрито");
                leaveToMenu();
            }
        } catch (Exception e) {
            // некоректне повідомлення ігноруємо
        }
    }

    private void applyDnd() {
        boolean on = sp.getBoolean("dnd", false);
        if (isHost) {
            if (host != null) host.setHostDnd(on);
        } else {
            send("DND|" + (on ? 1 : 0));
        }
    }

    private void onLobby(String[] f) {
        if (!dndSent) {
            dndSent = true;
            if (sp.getBoolean("dnd", false)) applyDnd();
        }
        me = pi(f[1]);
        transferOn = f[2].equals("1");
        int before = players.size();
        players.clear();
        if (f[4].length() > 0) {
            for (String s : f[4].split(";")) {
                String[] p = s.split("~", -1);
                if (p.length >= 6) players.add(p);
            }
        }
        if (before > 0 && players.size() > before && (screen == S_LOBBY || screen == S_GAME)) sfx(Snd.JOIN);
        if (screen == S_GAME) {
            applyPlayers();
            fillRoom();
        } else if (screen == S_LOBBY) {
            refreshLobby();
        } else if (f[3].equals("0")) {
            showLobby();
        }
    }

    // ---------- меню ----------
    private void showMenu() {
        stopNet();
        screen = S_MENU;
        root.removeAllViews();
        root.setBackgroundColor(0xFF1B5E3A);
        root.addView(new BgView(this), new FrameLayout.LayoutParams(MATCH, MATCH));
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setGravity(Gravity.CENTER);
        col.addView(tv("Дурак Gryntsiv", 1.0f, WHITE, true), lp(WRAP, WRAP, 0.1f));
        LinearLayout prof = new LinearLayout(this);
        prof.setOrientation(LinearLayout.HORIZONTAL);
        prof.setGravity(Gravity.CENTER_VERTICAL);
        Avatars.AvatarView av = new Avatars.AvatarView(this);
        av.set(avatar, avatar < 0 ? photo : "-", false);
        prof.addView(av, lp(1.2f, 1.2f, 0.05f));
        prof.addView(tv(nick, 0.45f, WHITE, true), lp(WRAP, WRAP, 0.1f));
        col.addView(prof, lp(WRAP, WRAP, 0.05f));
        col.addView(btn("Створити кімнату", btnCol(), new View.OnClickListener() {
            public void onClick(View v) {
                createRoom();
            }
        }), lp(6.5f, 1.15f, 0.12f));
        col.addView(btn("Приєднатися", btnCol(), new View.OnClickListener() {
            public void onClick(View v) {
                showJoin();
            }
        }), lp(6.5f, 1.15f, 0.12f));
        col.addView(btn("Налаштування", btnCol(), new View.OnClickListener() {
            public void onClick(View v) {
                showSettings();
            }
        }), lp(6.5f, 1.15f, 0.12f));
        root.addView(col, new FrameLayout.LayoutParams(MATCH, MATCH));
        FrameLayout.LayoutParams bl = new FrameLayout.LayoutParams(WRAP, WRAP, Gravity.BOTTOM | Gravity.LEFT);
        bl.setMargins(px(0.3f), 0, 0, px(0.3f));
        if (Cfg.SOUND) root.addView(audioBar(1.0f), bl);
        LinearLayout brow = new LinearLayout(this);
        brow.addView(btn("Назад", btnCol(), new View.OnClickListener() {
            public void onClick(View v) {
                finish();
            }
        }), lp(2.8f, 1.0f, 0.05f));
        brow.addView(btn("Вихід", btnCol(), new View.OnClickListener() {
            public void onClick(View v) {
                finishAffinity();
            }
        }), lp(2.8f, 1.0f, 0.05f));
        FrameLayout.LayoutParams br = new FrameLayout.LayoutParams(WRAP, WRAP, Gravity.BOTTOM | Gravity.RIGHT);
        br.setMargins(0, 0, px(0.25f), px(0.25f));
        root.addView(brow, br);
        holdGame = false;
        updateHold();
        musicFor(false);
    }

    // ---------- пошук кімнат ----------
    private void showJoin() {
        musicFor(false);
        stopNet();
        screen = S_JOIN;
        joinSig = "";
        root.removeAllViews();
        root.setBackgroundColor(0xFF1B5E3A);
        root.addView(new BgView(this), new FrameLayout.LayoutParams(MATCH, MATCH));
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(px(0.5f), px(0.3f), px(0.5f), px(0.3f));
        col.addView(tv("Кімнати у вашій Wi-Fi мережі", 0.5f, WHITE, true));
        LinearLayout crow = new LinearLayout(this);
        crow.setOrientation(LinearLayout.HORIZONTAL);
        crow.setGravity(Gravity.CENTER_VERTICAL);
        crow.addView(tv("Або код кімнати з інтернету:", 0.34f, 0xFFB0BEC5, false), lp(WRAP, WRAP, 0.05f));
        final EditText etCode = new EditText(this);
        etCode.setSingleLine(true);
        etCode.setHint("00000");
        etCode.setTextColor(WHITE);
        etCode.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, 0.42f * u);
        etCode.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(8)});
        crow.addView(etCode, lp(3.2f, WRAP, 0.05f));
        crow.addView(btn("Увійти", 0xFF2E7D32, new View.OnClickListener() {
            public void onClick(View v) {
                String c = Relay.cleanCode(etCode.getText().toString());
                if (c.length() < 4) {
                    toast("Введіть код кімнати");
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
                showMenu();
            }
        }), lp(3f, 0.95f, 0.1f));
        root.addView(col, new FrameLayout.LayoutParams(MATCH, MATCH));
        disc = new Discovery.Listener(this);
        disc.start();
        ui.post(new Runnable() {
            public void run() {
                if (screen != S_JOIN || disc == null) return;
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
        for (Discovery.Room r : list) {
            sig.append(r.ip).append(r.port).append(r.name).append(r.count).append(r.inGame).append(r.rejoin).append(';');
        }
        if (sig.toString().equals(joinSig)) return;
        joinSig = sig.toString();
        joinList.removeAllViews();
        if (list.isEmpty()) {
            joinList.addView(tv("Кімнат не знайдено. Усі гравці мають бути в одній Wi-Fi мережі.", 0.36f, 0xFFB0BEC5, false));
            return;
        }
        for (final Discovery.Room r : list) {
            boolean ok = r.inGame ? r.rejoin > 0 : r.count < 6;
            String t = r.name + "   " + r.count + "/6" + (r.inGame ? (r.rejoin > 0 ? "   гра триває — повернутись" : "   гра триває") : "");
            joinList.addView(btn(t, ok ? 0xFF1E88E5 : 0xFF607D8B, new View.OnClickListener() {
                public void onClick(View v) {
                    joinRoom(r);
                }
            }), lp(MATCH, 1.1f, 0.08f));
        }
    }

    // ---------- лобі ----------
    private void setTransferBtn() {
        if (lobbyTransfer != null) {
            lobbyTransfer.setBackground(btnBg(transferOn ? SEL : 0xFF455A64));
        }
        if (lobbyTrLbl != null) lobbyTrLbl.setText("Перевідний: " + (transferOn ? "так" : "ні"));
    }

    private void showLobby() {
        musicFor(false);
        stopDisc();
        screen = S_LOBBY;
        root.removeAllViews();
        root.setBackgroundColor(0xFF1B5E3A);
        root.addView(new BgView(this), new FrameLayout.LayoutParams(MATCH, MATCH));
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(px(0.5f), px(0.3f), px(0.5f), px(0.3f));
        if (isHost) {
            LinearLayout trow = new LinearLayout(this);
            trow.setOrientation(LinearLayout.HORIZONTAL);
            trow.setGravity(Gravity.CENTER_VERTICAL);
            trow.addView(tv("Ваша кімната", 0.5f, WHITE, true), new LinearLayout.LayoutParams(0, WRAP, 1f));
            trow.addView(netBar(), new LinearLayout.LayoutParams(WRAP, WRAP));
            col.addView(trow, new LinearLayout.LayoutParams(MATCH, WRAP));
        } else {
            col.addView(tv("Кімната", 0.5f, WHITE, true));
        }
        lobbyCount = tv("", 0.32f, 0xFFB0BEC5, false);
        col.addView(lobbyCount);
        ScrollView sv = new ScrollView(this);
        lobbyList = new LinearLayout(this);
        lobbyList.setOrientation(LinearLayout.VERTICAL);
        sv.addView(lobbyList);
        col.addView(sv, new LinearLayout.LayoutParams(MATCH, 0, 1f));
        lobbyStart = null;
        lobbyBot = null;
        lobbyTransfer = null;
        lobbyTrLbl = null;
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        if (isHost) {
            lobbyBot = btn("Додати бота", 0xFF455A64, new View.OnClickListener() {
                public void onClick(View v) {
                    if (host != null) host.addBot();
                }
            });
            lobbyStart = btn("Старт", 0xFF2E7D32, new View.OnClickListener() {
                public void onClick(View v) {
                    if (host != null) host.startMatch();
                }
            });
            row.addView(lobbyBot, lp(3.6f, 1.0f, 0.08f));
            row.addView(lobbyStart, lp(3.6f, 1.0f, 0.08f));
            row.addView(btn("Вийти", 0xFFC62828, new View.OnClickListener() {
                public void onClick(View v) {
                    showMenu();
                }
            }), lp(3f, 1.0f, 0.08f));
            View sp0 = new View(this);
            row.addView(sp0, new LinearLayout.LayoutParams(0, 1, 1f));
            lobbyTransfer = btn("Перевідний", transferOn ? SEL : 0xFF455A64, new View.OnClickListener() {
                public void onClick(View v) {
                    if (host == null) return;
                    boolean on = !host.getTransfer();
                    sp.edit().putBoolean("transfer", on).commit();
                    host.setTransfer(on);
                    transferOn = on;
                    setTransferBtn();
                }
            });
            row.addView(lobbyTransfer, lp(3.8f, 1.0f, 0.08f));
        } else {
            row.addView(btn("Вийти", 0xFFC62828, new View.OnClickListener() {
                public void onClick(View v) {
                    showMenu();
                }
            }), lp(3f, 1.0f, 0.08f));
            View sp0 = new View(this);
            row.addView(sp0, new LinearLayout.LayoutParams(0, 1, 1f));
            lobbyTrLbl = tv("", 0.36f, WHITE, true);
            row.addView(lobbyTrLbl, lp(WRAP, WRAP, 0.1f));
            col.addView(tv("Очікуємо, поки Господар почне гру…", 0.34f, 0xFFB0BEC5, false));
        }
        col.addView(row, new LinearLayout.LayoutParams(MATCH, WRAP));
        root.addView(col, new FrameLayout.LayoutParams(MATCH, MATCH));
        refreshLobby();
    }

    private LinearLayout netBar() {
        netBox = new LinearLayout(this);
        netBox.setOrientation(LinearLayout.VERTICAL);
        netBox.setGravity(Gravity.RIGHT);
        fillNetBar();
        return netBox;
    }

    private void fillNetBar() {
        if (netBox == null) return;
        netBox.removeAllViews();
        boolean w = host != null && host.isWifi();
        boolean n = host != null && host.isInternet();
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.addView(btn("Wifi", w ? SEL : 0xFF455A64, new View.OnClickListener() {
            public void onClick(View v) {
                if (host == null) return;
                if (host.isWifi()) {
                    host.setWifi(false);
                } else {
                    hostErr(host.setWifi(true));
                }
                fillNetBar();
            }
        }), lp(2.4f, 0.85f, 0.04f));
        row.addView(btn("Інтернет", n ? SEL : 0xFF455A64, new View.OnClickListener() {
            public void onClick(View v) {
                if (host == null || netBusy) return;
                if (host.isInternet()) {
                    host.stopInternet();
                    netCode = "";
                    fillNetBar();
                    return;
                }
                netBusy = true;
                toast("Підключення до інтернету…");
                final DHost hh = host;
                hh.startInternet(sp.getString("roomCodeD", ""), new DHost.NetCb() {
                    public void onNet(String code, String err) {
                        netBusy = false;
                        if (host != hh) return;
                        if (code == null) {
                            toast(err);
                        } else {
                            netCode = code;
                            sp.edit().putString("roomCodeD", code).commit();
                        }
                        fillNetBar();
                    }
                });
            }
        }), lp(3.0f, 0.85f, 0.04f));
        netBox.addView(row);
        if (n && netCode.length() > 0) {
            LinearLayout cr = new LinearLayout(this);
            cr.setOrientation(LinearLayout.HORIZONTAL);
            cr.setGravity(Gravity.CENTER_VERTICAL);
            cr.addView(tv("Код: " + netCode, 0.42f, 0xFFFFD54F, true), lp(WRAP, WRAP, 0.05f));
            cr.addView(btn("Поділитися", 0xFF1E88E5, new View.OnClickListener() {
                public void onClick(View v) {
                    android.content.Intent it = new android.content.Intent(android.content.Intent.ACTION_SEND);
                    it.setType("text/plain");
                    it.putExtra(android.content.Intent.EXTRA_TEXT, "Грай зі мною в Дурака Gryntsiv! Код кімнати: " + netCode);
                    startActivity(android.content.Intent.createChooser(it, "Поділитися кодом"));
                }
            }), lp(3.0f, 0.75f, 0.04f));
            netBox.addView(cr);
        }
    }

    private void hostErr(String e) {
        if (e != null) toast(e);
    }

    private LinearLayout playerRow(final int idx, final boolean inRoom) {
        String[] p = players.get(idx);
        boolean bot = p[3].equals("1");
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        Avatars.AvatarView av = new Avatars.AvatarView(this);
        av.set(pi(p[1]), p[2], false);
        row.addView(av, lp(1.0f, 1.0f, 0.05f));
        final boolean hostRow = p.length > 6 && p[6].equals("1");
        String label = bot ? p[0] : (p[0] + (hostRow ? " - господар" : " - гість"));
        row.addView(tv(label, bot && isHost ? 0.3f : 0.38f, WHITE, true), new LinearLayout.LayoutParams(0, WRAP, 1f));
        if (isHost) {
            if (bot && !inRoom) {
                int lv = pi(p[4]);
                for (int k = 0; k < LEVELS.length; k++) {
                    final int lk = k;
                    Button lb = btn(LEVELS[k], lv == k ? SEL : 0xFF455A64, new View.OnClickListener() {
                        public void onClick(View v) {
                            if (host != null) {
                                host.setLevel(idx, lk);
                                countLevel(lk);
                            }
                        }
                    });
                    lb.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, 0.26f * u);
                    lb.setPadding(px(0.05f), px(0.1f), px(0.05f), px(0.1f));
                    row.addView(lb, lp(1.65f, 0.8f, 0.02f));
                }
            }
            row.addView(btn("↑", 0xFF455A64, new View.OnClickListener() {
                public void onClick(View v) {
                    if (host != null) hostErr(host.moveSeat(idx, -1));
                }
            }), lp(0.8f, 0.8f, 0.02f));
            row.addView(btn("↓", 0xFF455A64, new View.OnClickListener() {
                public void onClick(View v) {
                    if (host != null) hostErr(host.moveSeat(idx, 1));
                }
            }), lp(0.8f, 0.8f, 0.02f));
            if (!hostRow) {
                Button rb = btn(bot ? "Прибрати" : "Видалити", 0xFFC62828, new View.OnClickListener() {
                    public void onClick(View v) {
                        if (host != null) hostErr(host.removePlayer(idx));
                    }
                });
                rb.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, 0.28f * u);
                rb.setPadding(px(0.05f), px(0.1f), px(0.05f), px(0.1f));
                row.addView(rb, lp(2.0f, 0.8f, 0.02f));
            }
        }
        LinearLayout outer = new LinearLayout(this);
        outer.setOrientation(LinearLayout.VERTICAL);
        outer.setBackground(bg(0xFF2C3A41, 0.2f));
        outer.setPadding(px(0.2f), px(0.08f), px(0.3f), px(0.08f));
        outer.addView(row, new LinearLayout.LayoutParams(MATCH, WRAP));
        return outer;
    }

    private void countLevel(int lk) {
        try {
            sp.edit().putInt("bl" + lk, sp.getInt("bl" + lk, 0) + 1).commit();
        } catch (Exception e) {
            // ignore
        }
        if (host != null) host.defLevel = bestLevel();
    }

    private int bestLevel() {
        int best = 1, bc = 0;
        for (int k = 0; k < LEVELS.length; k++) {
            int c = sp.getInt("bl" + k, 0);
            if (c > bc) {
                bc = c;
                best = k;
            }
        }
        return best;
    }

    private void refreshLobby() {
        if (lobbyList == null) return;
        lobbyList.removeAllViews();
        for (int i = 0; i < players.size(); i++) {
            lobbyList.addView(playerRow(i, false), lp(MATCH, WRAP, 0.06f));
        }
        lobbyCount.setText("Гравці: " + players.size() + " з 6 (потрібно від 2)");
        setTransferBtn();
        if (lobbyStart != null) {
            boolean ok = players.size() >= 2;
            lobbyStart.setEnabled(ok);
            lobbyStart.setAlpha(ok ? 1f : 0.4f);
        }
        if (lobbyBot != null) {
            boolean ok = players.size() < 6;
            lobbyBot.setEnabled(ok);
            lobbyBot.setAlpha(ok ? 1f : 0.4f);
        }
    }

    // ---------- гра ----------
    private TextView smallBtn(String s, View.OnClickListener l) {
        TextView t = tv(s, 0.34f, WHITE, true);
        t.setGravity(Gravity.CENTER);
        t.setBackground(bg(0x66000000, 0.2f));
        t.setPadding(px(0.15f), px(0.2f), px(0.15f), px(0.2f));
        final View.OnClickListener ll = l;
        t.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                sfx(Snd.BTN);
                ll.onClick(v);
            }
        });
        return t;
    }

    private void onPlayCard(Card k, int target) {
        if (gs == null) return;
        if (gs.def == me && !gs.taking) {
            boolean allOpen = gs.unbeaten() == gs.atk.size() && !gs.atk.isEmpty();
            boolean canTr = gs.canTransfer && allOpen && k.v == gs.atk.get(0).v;
            if (target >= 0) send("DEF|" + k.enc() + "|" + target);
            else if (canTr) send("TRF|" + k.enc());
            else send("DEF|" + k.enc() + "|-1");
        } else {
            send("PLAY|" + k.enc() + "|0");
        }
    }

    private void showGame() {
        stopDisc();
        screen = S_GAME;
        root.removeAllViews();
        root.setBackgroundColor(0xFF000000);
        table = new DuTable(this, dstyle);
        table.micOk = getPackageManager().hasSystemFeature(PackageManager.FEATURE_MICROPHONE);
        table.hint = hint;
        table.setBg(bgId);
        table.listener = new DuTable.Listener() {
            public void onPlay(Card k, int target) {
                onPlayCard(k, target);
            }

            public void onTake() {
                send("TAKE");
            }

            public void onAvatarTap(int idx) {
                showEmojiPicker(idx == me ? -1 : idx);
            }

            public void onWrong() {
                sfx(Snd.WRONG);
            }

            public void onAvatarLong(int idx) {
                if (idx < 0 || idx >= players.size() || idx == me) return;
                if (players.get(idx)[3].equals("1")) {
                    if (isHost && host != null) {
                        final int bi = idx;
                        showBotMenu("Бот: " + pname(idx), LEVELS, pi(players.get(idx)[4]), speedOf(host.getBotDelayOf(idx)), new LevelPick() {
                            public void pick(int lv) {
                                if (host != null) {
                                    host.setLevel(bi, lv);
                                    countLevel(lv);
                                }
                            }
                        }, new SpeedPick() {
                            public void pick(int val) {
                                host.setBotDelayOf(bi, SPEED_MS[val - 1]);
                            }
                        });
                    }
                    return;
                }
                if (gs != null && gs.paused >= 0) {
                    toast("Під час паузи штурхати не можна");
                    return;
                }
                send("POKE|" + idx);
                if (table != null) table.shakeAvatar(idx);
                sfx(Snd.POKE);
            }

            public void onMic(int idx, boolean down) {
                if (down) startMic(idx);
                else finishMic();
            }

            public void onDnd() {
                boolean on = !sp.getBoolean("dnd", false);
                sp.edit().putBoolean("dnd", on).apply();
                applyDnd();
                toast(on ? "Сповіщення вимкнено" : "Сповіщення увімкнено");
            }
        };
        root.addView(table, new FrameLayout.LayoutParams(MATCH, MATCH));

        LinearLayout tl = new LinearLayout(this);
        tl.setOrientation(LinearLayout.VERTICAL);
        tl.addView(smallBtn("Партії", new View.OnClickListener() {
            public void onClick(View v) {
                showScores();
            }
        }), lp(2.8f, WRAP, 0.03f));
        tl.addView(smallBtn("Історія", new View.OnClickListener() {
            public void onClick(View v) {
                wantLog = true;
                send("LOGREQ");
            }
        }), lp(2.8f, WRAP, 0.03f));
        LinearLayout topLeft = new LinearLayout(this);
        topLeft.setOrientation(LinearLayout.HORIZONTAL);
        topLeft.setGravity(Gravity.TOP);
        topLeft.addView(tl, lp(WRAP, WRAP, 0.0f));
        if (Cfg.SOUND) {
            LinearLayout ab = audioBar(0.9f);
            ab.setOrientation(LinearLayout.HORIZONTAL);
            LinearLayout.LayoutParams lpa = lp(WRAP, WRAP, 0.0f);
            lpa.leftMargin = px(0.15f);
            topLeft.addView(ab, lpa);
        }
        FrameLayout.LayoutParams lpScore = new FrameLayout.LayoutParams(WRAP, WRAP, Gravity.TOP | Gravity.LEFT);
        lpScore.setMargins(px(0.2f), px(0.12f), 0, 0);
        root.addView(topLeft, lpScore);

        LinearLayout tr = new LinearLayout(this);
        tr.setOrientation(LinearLayout.HORIZONTAL);
        tr.setGravity(Gravity.CENTER_VERTICAL);
        ivPause = new IconView(this, IconView.PAUSE);
        ivPause.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                if (gs == null) return;
                sfx(Snd.PAUSE);
                if (gs.paused < 0) send("PAUSE");
                else if (gs.paused == me) send("RESUME");
            }
        });
        tr.addView(ivPause, lp(0.9f, 0.9f, 0.05f));
        ivGear = new IconView(this, IconView.GEAR);
        ivGear.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                sfx(Snd.BTN);
                if (gs != null && gs.paused < 0 && !gs.over) {
                    settingsPaused = true;
                    send("PAUSE");
                }
                showSettings();
            }
        });
        tr.addView(ivGear, lp(0.9f, 0.9f, 0.05f));
        if (isHost) {
            tr.addView(btn("Кімната", 0xAA37474F, new View.OnClickListener() {
                public void onClick(View v) {
                    showRoom();
                }
            }), lp(2.1f, 0.85f, 0.05f));
        }
        tr.addView(btn("Вихід", 0xAAC62828, new View.OnClickListener() {
            public void onClick(View v) {
                leaveToMenu();
            }
        }), lp(1.7f, 0.85f, 0.05f));
        FrameLayout.LayoutParams lpTr = new FrameLayout.LayoutParams(WRAP, WRAP, Gravity.TOP | Gravity.RIGHT);
        lpTr.setMargins(0, px(0.1f), px(0.15f), 0);
        root.addView(tr, lpTr);

        LinearLayout bc = new LinearLayout(this);
        bc.setOrientation(LinearLayout.VERTICAL);
        btnTake = btn("Беру", 0xFF1E88E5, new View.OnClickListener() {
            public void onClick(View v) {
                send("TAKE");
            }
        });
        btnPass = btn("Бито", 0xFFEF6C00, new View.OnClickListener() {
            public void onClick(View v) {
                send("PASS");
            }
        });
        bc.addView(btnTake, lp(2.3f, 0.95f, 0.08f));
        bc.addView(btnPass, lp(2.3f, 0.95f, 0.08f));
        FrameLayout.LayoutParams lpBc = new FrameLayout.LayoutParams(WRAP, WRAP, Gravity.BOTTOM | Gravity.RIGHT);
        lpBc.setMargins(0, 0, px(0.25f), px(0.35f));
        root.addView(bc, lpBc);

        btnResume = btn("Продовжити", 0xFF2E7D32, new View.OnClickListener() {
            public void onClick(View v) {
                send("RESUME");
            }
        });
        btnResume.setVisibility(View.GONE);
        FrameLayout.LayoutParams lpRes = new FrameLayout.LayoutParams(px(4f), px(1.1f), Gravity.CENTER);
        lpRes.topMargin = px(2.2f);
        root.addView(btnResume, lpRes);

        pokeLbl = tv("", 0.45f, WHITE, true);
        pokeLbl.setBackground(bg(0xDD000000, 0.3f));
        pokeLbl.setPadding(px(0.5f), px(0.2f), px(0.5f), px(0.2f));
        pokeLbl.setVisibility(View.GONE);
        FrameLayout.LayoutParams lpPoke = new FrameLayout.LayoutParams(WRAP, WRAP, Gravity.TOP | Gravity.CENTER_HORIZONTAL);
        lpPoke.topMargin = px(1.2f);
        root.addView(pokeLbl, lpPoke);

        musicFor(true);
        applyPlayers();
        if (pendingBanner != null) {
            final String bn = pendingBanner;
            pendingBanner = null;
            ui.post(new Runnable() {
                public void run() {
                    if (table != null) table.showBanner(bn);
                }
            });
        }
    }

    private void applyPlayers() {
        if (table == null) return;
        int n = players.size();
        String[] names = new String[n];
        int[] av = new int[n];
        String[] ph = new String[n];
        boolean[] bots = new boolean[n];
        for (int i = 0; i < n; i++) {
            String[] p = players.get(i);
            names[i] = p[0];
            av[i] = pi(p[1]);
            ph[i] = p[2];
            bots[i] = p[3].equals("1");
        }
        table.setPlayers(names, av, ph, bots, me);
    }

    private void setBtn(Button b, boolean on) {
        b.setEnabled(on);
        b.setAlpha(on ? 1f : 0.35f);
    }

    private static int tableCards(DState g) {
        int c = g.atk.size();
        for (Card d : g.dfn) if (d != null) c++;
        return c;
    }

    private void stateSounds(DState p, DState g) {
        if (p == null || p.over || g.counts == null || p.counts == null || g.counts.length != p.counts.length) return;
        int inc = 0;
        for (int i = 0; i < g.counts.length; i++) {
            if (g.counts[i] > p.counts[i]) inc += g.counts[i] - p.counts[i];
        }
        if (tableCards(g) > tableCards(p)) sfx(Snd.THROW);
        else if (inc > 0) sfx(Snd.DRAW);
        else if (tableCards(g) == 0 && tableCards(p) > 0) sfx(Snd.PASS);
    }

    private void applyState() {
        if (table == null || gs == null) return;
        stateSounds(lastGs, gs);
        lastGs = gs;
        fillRoom();
        applyPlayers();
        table.setState(gs);
        boolean paused = gs.paused >= 0;
        holdGame = paused && !settingsPaused;
        updateHold();
        boolean canT = gs.canTake && !paused && !gs.over;
        boolean canP = gs.canPass && !paused && !gs.over;
        boolean anyLegal = false;
        for (Boolean bl : gs.legal) {
            if (bl) anyLegal = true;
        }
        setBtn(btnTake, canT);
        btnTake.setVisibility(showDraw || canT ? View.VISIBLE : View.GONE);
        setBtn(btnPass, canP);
        btnPass.setVisibility(!autoPass || (canP && anyLegal) ? View.VISIBLE : View.GONE);
        if (autoPass && canP && !anyLegal) {
            if (!autoPassSent) {
                autoPassSent = true;
                ui.postDelayed(new Runnable() {
                    public void run() {
                        if (screen == S_GAME && gs != null && gs.canPass && !gs.over && gs.paused < 0) send("PASS");
                    }
                }, 900);
            }
        } else {
            autoPassSent = false;
        }
        btnResume.setVisibility(gs.paused == me ? View.VISIBLE : View.GONE);
        ivPause.setType(gs.paused == me ? IconView.PLAY : IconView.PAUSE);
        ivPause.setAlpha(paused && gs.paused != me ? 0.35f : 1f);
        if (resultDlg != null && resultDlg.isShowing() && !gs.over) resultDlg.dismiss();
    }

    // ---------- штурхання ----------
    @Override
    protected void onPause() {
        super.onPause();
        if (screen == S_GAME && gs != null && !gs.over && gs.paused < 0 && !isFinishing()) {
            autoPaused = true;
            send("PAUSE");
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (autoPaused) {
            autoPaused = false;
            send("RESUME");
        }
    }

    private void onPoked(int from) {
        try {
            Animation a = new TranslateAnimation(-px(0.35f), px(0.35f), 0, 0);
            a.setDuration(500);
            a.setInterpolator(new CycleInterpolator(5));
            root.startAnimation(a);
        } catch (Exception e) {
            // ignore
        }
        try {
            Vibrator vb = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
            if (vb != null) vb.vibrate(180);
        } catch (Exception e) {
            // ignore
        }
        sfx(Snd.POKE);
        if (pokeLbl != null) {
            pokeLbl.setText(pname(from) + " штурхнув вас");
            pokeLbl.setVisibility(View.VISIBLE);
            ui.postDelayed(new Runnable() {
                public void run() {
                    if (pokeLbl != null) pokeLbl.setVisibility(View.GONE);
                }
            }, 2000);
        }
    }

    // ---------- діалоги ----------
    private void showEmojiPicker(final int target) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.addView(tv(target < 0 ? "Емоція для всіх" : "Емоція для: " + pname(target), 0.34f, 0xFFB0BEC5, false), lp(WRAP, WRAP, 0.05f));
        final Dialog[] dd = new Dialog[1];
        for (int r = 0; r < 4; r++) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            for (int c = 0; c < 8; c++) {
                final int k = r * 8 + c;
                TextView e = tv(EMOJI[k], 0.85f, WHITE, false);
                e.setGravity(Gravity.CENTER);
                e.setOnClickListener(new View.OnClickListener() {
                    public void onClick(View v) {
                        dd[0].dismiss();
                        send("EMO|" + target + "|" + k);
                    }
                });
                row.addView(e, new LinearLayout.LayoutParams(0, px(1.4f), 1f));
            }
            box.addView(row, new LinearLayout.LayoutParams(MATCH, WRAP));
        }
        dd[0] = makeDialog(box, true);
        showSized(dd[0], 13f);
    }

    private void showRoom() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        LinearLayout trow = new LinearLayout(this);
        trow.setOrientation(LinearLayout.HORIZONTAL);
        trow.setGravity(Gravity.CENTER_VERTICAL);
        trow.addView(tv("Кімната", 0.45f, WHITE, true), new LinearLayout.LayoutParams(0, WRAP, 1f));
        trow.addView(netBar(), new LinearLayout.LayoutParams(WRAP, WRAP));
        box.addView(trow, new LinearLayout.LayoutParams(MATCH, WRAP));
        ScrollView sv = new ScrollView(this);
        roomList = new LinearLayout(this);
        roomList.setOrientation(LinearLayout.VERTICAL);
        sv.addView(roomList);
        box.addView(sv, new LinearLayout.LayoutParams(MATCH, px(5.2f)));
        final Dialog[] dd = new Dialog[1];
        LinearLayout brow = new LinearLayout(this);
        brow.setOrientation(LinearLayout.HORIZONTAL);
        brow.setGravity(Gravity.CENTER_HORIZONTAL);
        brow.addView(btn("Додати бота", 0xFF2E7D32, new View.OnClickListener() {
            public void onClick(View v) {
                if (host == null) return;
                String m = host.addBot();
                if (m != null) toast(m);
            }
        }), lp(3.8f, 0.9f, 0.08f));
        brow.addView(btn("Закрити", 0xFF455A64, new View.OnClickListener() {
            public void onClick(View v) {
                dd[0].dismiss();
            }
        }), lp(3f, 0.9f, 0.08f));
        box.addView(brow, new LinearLayout.LayoutParams(MATCH, WRAP));
        dd[0] = makeDialog(box, true);
        roomDlg = dd[0];
        showSized(dd[0], 15.6f);
        fillRoom();
    }

    private void fillRoom() {
        if (roomDlg == null || !roomDlg.isShowing() || roomList == null) return;
        roomList.removeAllViews();
        for (int i = 0; i < players.size(); i++) {
            roomList.addView(playerRow(i, true), lp(MATCH, WRAP, 0.05f));
        }
    }

    // ---------- таблиця партій ----------
    private View divider() {
        View v = new View(this);
        v.setBackgroundColor(0x66FFFFFF);
        v.setLayoutParams(new LinearLayout.LayoutParams(Math.max(1, px(0.02f)), MATCH));
        return v;
    }

    private View hline() {
        View line = new View(this);
        line.setBackgroundColor(0x66FFFFFF);
        line.setLayoutParams(new LinearLayout.LayoutParams(MATCH, Math.max(1, px(0.02f))));
        return line;
    }

    private TextView hcell(String s, int color, boolean bold, float size) {
        TextView t = tv(s, size, color, bold);
        t.setGravity(Gravity.CENTER);
        t.setSingleLine(true);
        t.setPadding(px(0.05f), px(0.08f), px(0.05f), px(0.08f));
        return t;
    }

    private static int at(int[] a, int i) {
        return (a != null && i >= 0 && i < a.length) ? a[i] : 0;
    }

    private View scoreTable(final int[][] rounds, int[] tot, int fool) {
        final LinearLayout t = new LinearLayout(this);
        t.setOrientation(LinearLayout.VERTICAL);
        t.setBackground(bg(0xFF2C3A41, 0.15f));
        final int n = Math.min(players.size(), tot == null ? 0 : tot.length);
        float colW = (13.5f - 1.4f) / (n + 1.2f);
        float avS = Math.min(0.85f, colW * 0.5f);
        LinearLayout head = new LinearLayout(this);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.addView(new View(this), new LinearLayout.LayoutParams(0, 1, 1.2f));
        for (int i = 0; i < n; i++) {
            head.addView(divider());
            LinearLayout cc = new LinearLayout(this);
            cc.setOrientation(LinearLayout.VERTICAL);
            LinearLayout top = new LinearLayout(this);
            top.setOrientation(LinearLayout.HORIZONTAL);
            top.setGravity(Gravity.CENTER);
            String[] p = players.get(i);
            Avatars.AvatarView av = new Avatars.AvatarView(this);
            av.set(pi(p[1]), p[2], false);
            top.addView(av, lp(avS, avS, 0.03f));
            top.addView(tv(String.valueOf(at(tot, i)), 0.36f, 0xFFEF9A9A, true), lp(WRAP, WRAP, 0.03f));
            cc.addView(top, new LinearLayout.LayoutParams(MATCH, WRAP));
            String nm = pname(i);
            if (nm.length() > 9) nm = nm.substring(0, 8) + "…";
            cc.addView(hcell(nm, Theme.pcol(i), true, 0.26f));
            head.addView(cc, new LinearLayout.LayoutParams(0, WRAP, 1f));
        }
        t.addView(head, new LinearLayout.LayoutParams(MATCH, WRAP));
        t.addView(hline());
        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        int rc = rounds == null ? 0 : rounds.length;
        if (rc == 0) body.addView(hcell("Партій ще не було", 0xFFB0BEC5, false, 0.3f));
        for (int k = 0; k < rc; k++) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            TextView l = hcell("Партія " + (k + 1), 0xFFB0BEC5, true, 0.28f);
            l.setGravity(Gravity.LEFT | Gravity.CENTER_VERTICAL);
            l.setPadding(px(0.15f), px(0.08f), px(0.05f), px(0.08f));
            row.addView(l, new LinearLayout.LayoutParams(0, WRAP, 1.2f));
            for (int i = 0; i < n; i++) {
                row.addView(divider());
                int v = at(rounds[k], i);
                row.addView(hcell(v > 0 ? "✗" : "", 0xFFEF5350, true, 0.34f), new LinearLayout.LayoutParams(0, WRAP, 1f));
            }
            body.addView(row, new LinearLayout.LayoutParams(MATCH, WRAP));
            body.addView(hline());
        }
        ScrollView sv = new ScrollView(this);
        sv.addView(body);
        t.addView(sv, new LinearLayout.LayoutParams(MATCH, rc > 4 ? px(3.4f) : WRAP));
        if (rc > 4) {
            final ScrollView sv2 = sv;
            sv.post(new Runnable() {
                public void run() {
                    sv2.fullScroll(View.FOCUS_DOWN);
                }
            });
        }
        return t;
    }

    private void showScores() {
        if (gs == null || gs.losses == null) return;
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.addView(tv("Партії", 0.45f, WHITE, true), lp(WRAP, WRAP, 0.05f));
        box.addView(tv("Праворуч від аватара — усього поразок", 0.24f, 0xFFB0BEC5, false), lp(WRAP, WRAP, 0.03f));
        box.addView(scoreTable(gs.rounds, gs.losses, -1), lp(MATCH, WRAP, 0.05f));
        final Dialog[] dd = new Dialog[1];
        LinearLayout.LayoutParams cp = lp(3f, 0.9f, 0.1f);
        cp.gravity = Gravity.CENTER_HORIZONTAL;
        box.addView(btn("Закрити", 0xFF455A64, new View.OnClickListener() {
            public void onClick(View v) {
                dd[0].dismiss();
            }
        }), cp);
        dd[0] = makeDialog(box, true);
        showSized(dd[0], 13.5f);
    }

    private void onResult(String[] f) {
        final int fool = pi(f[2]);
        lastGs = null;
        final int snd = fool < 0 ? Snd.RWIN : (fool == me ? Snd.GLOSE : Snd.GWIN);
        ui.postDelayed(new Runnable() {
            public void run() {
                if (screen == S_GAME) sfx(snd);
            }
        }, 1200);
        final int[] tot = DState.ints(f[5]);
        final int[][] rounds = DState.parseRounds(f.length > 7 ? f[7] : "");
        ui.postDelayed(new Runnable() {
            public void run() {
                if (screen == S_GAME) showResults(fool, rounds, tot);
            }
        }, 1500);
    }

    private void showResults(int fool, int[][] rounds, int[] tot) {
        if (resultDlg != null && resultDlg.isShowing()) resultDlg.dismiss();
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.addView(tv("Партію завершено", 0.5f, WHITE, true), lp(WRAP, WRAP, 0.05f));
        if (fool >= 0 && fool < players.size()) {
            box.addView(tv("Дурак: " + pname(fool), 0.38f, 0xFFEF9A9A, true), lp(WRAP, WRAP, 0.05f));
        } else {
            box.addView(tv("Нічия — дурня немає", 0.36f, 0xFFFFD54F, true), lp(WRAP, WRAP, 0.05f));
        }
        box.addView(scoreTable(rounds, tot, fool), lp(MATCH, WRAP, 0.05f));
        LinearLayout nr = new LinearLayout(this);
        nr.setOrientation(LinearLayout.HORIZONTAL);
        nr.setGravity(Gravity.CENTER_HORIZONTAL);
        if (isHost) {
            nr.addView(btn("Наступна партія", 0xFF2E7D32, new View.OnClickListener() {
                public void onClick(View v) {
                    if (players.size() < 2) {
                        toast("Потрібно щонайменше 2 гравці");
                        return;
                    }
                    if (resultDlg != null) resultDlg.dismiss();
                    if (host != null) host.nextRound();
                }
            }), lp(4.8f, 1.0f, 0.1f));
        } else {
            box.addView(tv("Очікуємо, поки Господар почне наступну партію…", 0.32f, 0xFFB0BEC5, false), lp(WRAP, WRAP, 0.06f));
        }
        nr.addView(btn("Вийти", 0xFFC62828, new View.OnClickListener() {
            public void onClick(View v) {
                leaveToMenu();
            }
        }), lp(3.2f, 1.0f, 0.1f));
        box.addView(nr, new LinearLayout.LayoutParams(MATCH, WRAP));
        resultDlg = makeDialog(box, false);
        showSized(resultDlg, 13.5f);
    }

    // ---------- продовження гри ----------
    private void askResume() {
        if (askedResume) return;
        askedResume = true;
        final String data = sp.getString("saveD", "");
        if (data.length() == 0) return;
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.addView(tv("Продовжити гру?", 0.5f, WHITE, true), lp(WRAP, WRAP, 0.08f));
        box.addView(tv("Незавершена гра:", 0.32f, 0xFFB0BEC5, false), lp(WRAP, WRAP, 0.04f));
        box.addView(tv(DHost.describe(data), 0.36f, WHITE, false), lp(WRAP, WRAP, 0.08f));
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        final Dialog[] dd = new Dialog[1];
        row.addView(btn("Продовжити", 0xFF2E7D32, new View.OnClickListener() {
            public void onClick(View v) {
                dd[0].dismiss();
                createRoom(data);
            }
        }), lp(4.2f, 1.0f, 0.08f));
        row.addView(btn("Видалити", 0xFFC62828, new View.OnClickListener() {
            public void onClick(View v) {
                sp.edit().remove("saveD").commit();
                dd[0].dismiss();
            }
        }), lp(3.2f, 1.0f, 0.08f));
        box.addView(row);
        dd[0] = makeDialog(box, true);
        showSized(dd[0], 9.5f);
    }

    // ---------- голос ----------
    private void startMic(int idx) {
        if (gs != null && gs.paused >= 0) {
            toast("Під час паузи голосові недоступні");
            return;
        }
        if (voiceLock) {
            toast("Зачекайте, поки прозвучить повідомлення");
            return;
        }
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            pendingMicIdx = idx;
            requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, REQ_MIC);
            return;
        }
        micTarget = idx == me ? -1 : idx;
        if (voice.start()) {
            sfx(Snd.VOICE);
            toast("Говоріть… (до 10 с)");
            ui.removeCallbacks(micStop);
            ui.postDelayed(micStop, 10000);
        } else {
            micTarget = -2;
            toast("Не вдалося почати запис");
        }
    }

    private void finishMic() {
        ui.removeCallbacks(micStop);
        if (voice == null || !voice.isRecording()) return;
        String b = voice.stop();
        int t = micTarget;
        micTarget = -2;
        if (b != null && t > -2) {
            send("VOICE|" + t + "|" + b);
            toast("Надіслано");
        }
    }

    @Override
    public void onRequestPermissionsResult(int req, String[] perms, int[] res) {
        super.onRequestPermissionsResult(req, perms, res);
        if (req == REQ_MIC) {
            if (res.length > 0 && res[0] == PackageManager.PERMISSION_GRANTED) toast("Дозвіл надано, утримуйте мікрофон ще раз");
            else toast("Без дозволу голосові повідомлення недоступні");
        }
    }

    // ---------- історія ходів ----------
    private void showLog(String data) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.addView(tv("Історія ходів", 0.45f, WHITE, true), lp(WRAP, WRAP, 0.05f));
        final ArrayList<ArrayList<String>> rs = new ArrayList<ArrayList<String>>();
        if (data.length() > 0) {
            for (String line : data.split("\\^")) {
                if (line.startsWith("\u2014") || rs.isEmpty()) rs.add(new ArrayList<String>());
                rs.get(rs.size() - 1).add(line);
            }
        }
        final LinearLayout tabs = new LinearLayout(this);
        tabs.setOrientation(LinearLayout.HORIZONTAL);
        final HorizontalScrollView hs = new HorizontalScrollView(this);
        hs.addView(tabs);
        if (rs.size() > 1) box.addView(hs, new LinearLayout.LayoutParams(MATCH, WRAP));
        ScrollView sv = new ScrollView(this);
        final LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        sv.addView(list);
        box.addView(sv, new LinearLayout.LayoutParams(MATCH, px(5.0f)));
        final int[] sel = {rs.size() - 1};
        final boolean[] allTabs = {false};
        final Runnable[] fill = new Runnable[1];
        fill[0] = new Runnable() {
            public void run() {
                tabs.removeAllViews();
                int from = (rs.size() > 4 && !allTabs[0]) ? rs.size() - 4 : 0;
                if (rs.size() > 4) {
                    tabs.addView(btn(allTabs[0] ? "◂" : "…", 0xFF37474F, new View.OnClickListener() {
                        public void onClick(View v) {
                            allTabs[0] = !allTabs[0];
                            fill[0].run();
                        }
                    }), lp(1.1f, 0.8f, 0.04f));
                }
                for (int k = from; k < rs.size(); k++) {
                    final int kk = k;
                    tabs.addView(btn(String.valueOf(k + 1), sel[0] == k ? SEL : 0xFF455A64, new View.OnClickListener() {
                        public void onClick(View v) {
                            sel[0] = kk;
                            fill[0].run();
                        }
                    }), lp(1.1f, 0.8f, 0.04f));
                }
                fillLog(list, sel[0] >= 0 ? rs.get(sel[0]) : null);
            }
        };
        fill[0].run();
        final Dialog[] dd = new Dialog[1];
        Button close = btn("Закрити", 0xFF455A64, new View.OnClickListener() {
            public void onClick(View v) {
                dd[0].dismiss();
            }
        });
        LinearLayout.LayoutParams cp = lp(3f, 0.9f, 0.08f);
        cp.gravity = Gravity.CENTER_HORIZONTAL;
        box.addView(close, cp);
        dd[0] = makeDialog(box, true);
        showSized(dd[0], 10f);
    }

    private void fillLog(LinearLayout list, ArrayList<String> ls) {
        list.removeAllViews();
        if (ls == null || ls.isEmpty()) {
            list.addView(tv("Поки порожньо", 0.34f, 0xFFB0BEC5, false));
            return;
        }
        android.graphics.Paint mp = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
        mp.setTextSize(0.32f * u);
        mp.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        float mw = 0;
        for (int j = 0; j < players.size(); j++) mw = Math.max(mw, mp.measureText(pname(j)));
        final int nameCol = (int) mw + px(0.3f);
        for (int i = ls.size() - 1; i >= 0; i--) {
            String line = ls.get(i);
            boolean head = line.startsWith("\u2014");
            int who = -1;
            if (!head) {
                for (int j = 0; j < players.size(); j++) {
                    String nm = pname(j);
                    if (nm.length() > 0 && line.startsWith(nm) && (who < 0 || nm.length() > pname(who).length())) who = j;
                }
            }
            LinearLayout lr = new LinearLayout(this);
            lr.setOrientation(LinearLayout.HORIZONTAL);
            String rest = line;
            if (who >= 0) {
                TextView nt = tv(pname(who), 0.32f, Theme.pcol(who), true);
                lr.addView(nt, new LinearLayout.LayoutParams(nameCol, WRAP));
                rest = line.substring(pname(who).length());
                if (rest.startsWith(":")) rest = rest.substring(1);
                rest = rest.trim();
            }
            TextView lt = tv(rest, 0.32f, head ? 0xFFFFD54F : 0xFFE0E0E0, head);
            SpannableString ss = new SpannableString(rest);
            for (int j = 0; j < players.size(); j++) {
                String nm = pname(j);
                if (nm.length() == 0) continue;
                int from = 0;
                while (true) {
                    int at = rest.indexOf(nm, from);
                    if (at < 0) break;
                    ss.setSpan(new ForegroundColorSpan(Theme.pcol(j)), at, at + nm.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                    from = at + nm.length();
                }
            }
            lt.setText(ss);
            lr.addView(lt, new LinearLayout.LayoutParams(0, WRAP, 1f));
            list.addView(lr, lp(MATCH, WRAP, 0.03f));
        }
    }
}
