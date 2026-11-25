package com.example.gryntsivgames.uno;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.provider.MediaStore;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputFilter;
import android.util.Base64;
import android.util.DisplayMetrics;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.Random;

public abstract class BaseActivity extends Activity {
    protected static final int REQ_PHOTO = 7;
    protected static final String[] EMOJI = {
        "😀", "😂", "😎", "😡",
        "😢", "👍", "👏", "🤔",
        "😍", "🤣", "😱", "🥳",
        "😴", "🙏", "🔥", "👎",
        "🤯", "😈", "🍿", "💔",
        "🤝", "🎉", "🙄", "🤫",
        "🦄", "👻", "🚀", "💩",
        "🤡", "🥶", "🫡", "💪"
    };
    protected static final int WHITE = 0xFFFFFFFF;

    protected SharedPreferences sp;
    protected final Handler ui = new Handler(Looper.getMainLooper());
    protected float u;
    protected String nick = "Гравець";
    protected String photo = "-";
    protected int avatar;
    protected int themeId;
    protected boolean autoPass;
    protected boolean showDraw;
    protected boolean hint = true;
    protected int bgId;
    protected int botDelay = 1500;
    protected int botSpeed = 3;
    protected Snd snd;
    protected boolean holdApp, holdGame;
    protected static final int[] MEL_DEF = {3, 6, 1, 7, 4, 2, 5};
    protected static final int[] SPEED_MS = {3000, 2200, 1500, 900, 450};
    protected static final int CAP = 0xFFCFD8DC;
    protected static final float CAPS = 0.36f;
    private int avCat;
    private LinearLayout avatarTabs, photoBox;
    private Button speedBtn;
    protected String cid = "";
    protected boolean profileChanged;
    private String profSig = "";

    private EditText etNick;
    private LinearLayout avatarBox, themeBox, bgBox;
    private Preview preview;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        sp = getSharedPreferences("uno", MODE_PRIVATE);
        nick = sp.getString("nick", "Гравець");
        avatar = sp.getInt("avatar", 0);
        photo = sp.getString("photo", "-");
        themeId = sp.getInt("theme", 0);
        autoPass = sp.getBoolean("auto", true);
        showDraw = sp.getBoolean("showDraw", false);
        hint = sp.getBoolean("hint", true);
        snd = new Snd(this, sp);
        bgId = sp.getInt("bg", 0);
        botSpeed = Math.max(1, Math.min(5, sp.getInt("botSpeed", 3)));
        botDelay = SPEED_MS[botSpeed - 1];
        cid = sp.getString("cid", "");
        if (cid.length() == 0) {
            cid = Long.toHexString(new Random().nextLong()) + Long.toHexString(System.currentTimeMillis());
            sp.edit().putString("cid", cid).commit();
        }
        DisplayMetrics dm = getResources().getDisplayMetrics();
        float big = Math.max(dm.widthPixels, dm.heightPixels);
        float small = Math.min(dm.widthPixels, dm.heightPixels);
        u = Math.min(big / 16f, small / 9f);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    }

    @Override
    public void onWindowFocusChanged(boolean focus) {
        super.onWindowFocusChanged(focus);
        if (focus) {
            getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                    | View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN);
        }
    }

    protected void savePrefs() {
        sp.edit().putString("nick", nick).putInt("avatar", avatar).putString("photo", photo)
            .putInt("theme", themeId).putBoolean("auto", autoPass).putBoolean("showDraw", showDraw).putBoolean("hint", hint).putInt("bg", bgId)
            .putInt("botSpeed", botSpeed).commit();
    }

    protected String sendPhoto() {
        return (avatar < 0 && photo.length() > 8) ? photo : "-";
    }

    protected void onSettingsClosed() {
    }

    // додаткові пункти налаштувань гри; повертає кількість доданих пунктів
    protected String labelPass() {
        return "Кнопка «Пас»";
    }

    protected String labelDraw() {
        return "Кнопка «Добрати»";
    }

    protected int addExtraSettings(LinearLayout right) {
        return 0;
    }

    // вибір колоди: за замовчуванням п'ять тем Уно; гра може підмінити
    protected int deckCount() {
        return Theme.ALL.length;
    }

    protected String deckTitle(int i) {
        return Theme.ALL[i].title;
    }

    protected int deckSel() {
        return themeId;
    }

    protected void deckPick(int i) {
        themeId = i;
    }

    protected boolean previewDeck(Canvas c, int w, int h) {
        return false;
    }

    protected boolean musicIsGame() {
        return false;
    }

    protected boolean hostMode() {
        return false;
    }

    protected void onBotDelay(int ms) {
    }

    protected void showSized(Dialog d, float widthU) {
        d.show();
        if (d.getWindow() != null) {
            int sw = getResources().getDisplayMetrics().widthPixels;
            d.getWindow().setLayout(Math.min(px(widthU), (int) (sw * 0.92f)), ViewGroup.LayoutParams.WRAP_CONTENT);
        }
    }

    // ---------- допоміжні ----------
    protected int px(float k) {
        return Math.round(k * u);
    }

    private Toast lastToast;

    protected void toast(String s) {
        try {
            if (lastToast != null) lastToast.cancel();
        } catch (Exception e) {
            // ignore
        }
        lastToast = Toast.makeText(this, s, Toast.LENGTH_SHORT);
        lastToast.show();
    }

    protected GradientDrawable bg(int color, float radiusU) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(radiusU * u);
        return d;
    }

    protected TextView tv(String s, float size, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(TypedValue.COMPLEX_UNIT_PX, size * u);
        t.setTextColor(color);
        if (bold) t.setTypeface(Typeface.DEFAULT_BOLD);
        return t;
    }

    protected void sfx(int kind) {
        if (snd != null) snd.play(kind);
    }

    protected int bgMel(int bg) {
        int d = MEL_DEF[Math.max(0, Math.min(MEL_DEF.length - 1, bg))];
        return Math.max(1, Math.min(7, sp.getInt("mel" + bg, d)));
    }

    protected void setBgMel(int bg, int m) {
        sp.edit().putInt("mel" + bg, m).commit();
    }

    protected int btnCol() {
        return Bg.BTN[Math.max(0, Math.min(Bg.BTN.length - 1, bgId))];
    }

    protected class BgView extends View {
        public BgView(Context c) {
            super(c);
        }

        @Override
        protected void onDraw(Canvas cv) {
            Bg.draw(cv, getWidth(), getHeight(), bgId);
        }
    }

    protected void musicFor(boolean game) {
        if (snd == null) return;
        snd.music((game ? "music" : "menu") + bgMel(bgId));
    }

    protected void updateHold() {
        if (snd != null) snd.hold(holdApp || holdGame);
    }

    @Override
    protected void onPause() {
        super.onPause();
        holdApp = true;
        updateHold();
    }

    @Override
    protected void onResume() {
        super.onResume();
        holdApp = false;
        updateHold();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (snd != null) snd.release(!isTaskRoot());
    }

    protected void showVolume(final boolean music) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.addView(tv(music ? "Гучність музики" : "Гучність звуків", 0.4f, WHITE, true), lp(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, 0.1f));
        SeekBar sb = new SeekBar(this);
        sb.setMax(100);
        sb.setProgress(Math.round((music ? snd.mVol : snd.sVol) * 100f));
        sb.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar b, int p, boolean user) {
                if (music) snd.setMusicVol(p / 100f);
                else snd.setSoundVol(p / 100f);
            }

            public void onStartTrackingTouch(SeekBar b) {
            }

            public void onStopTrackingTouch(SeekBar b) {
                if (!music) snd.play(Snd.BTN);
            }
        });
        box.addView(sb, lp(ViewGroup.LayoutParams.MATCH_PARENT, 1.0f, 0.1f));
        Dialog d = makeDialog(box, true);
        showSized(d, 7f);
    }

    // пара кнопок «звук» і «музика»
    protected LinearLayout audioBar(float sizeU) {
        return audioBar(sizeU, 0.06f);
    }

    protected LinearLayout audioBar(float sizeU, float gapU) {
        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        final IconView a = new IconView(this, IconView.SOUND);
        final IconView m = new IconView(this, IconView.MUSIC);
        a.setCrossed(!snd.sound);
        m.setCrossed(!snd.music);
        a.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                snd.setSound(!snd.sound);
                a.setCrossed(!snd.sound);
                snd.play(Snd.BTN);
            }
        });
        a.setOnLongClickListener(new View.OnLongClickListener() {
            public boolean onLongClick(View v) {
                showVolume(false);
                return true;
            }
        });
        m.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                snd.setMusic(!snd.music);
                m.setCrossed(!snd.music);
                snd.play(Snd.BTN);
            }
        });
        m.setOnLongClickListener(new View.OnLongClickListener() {
            public boolean onLongClick(View v) {
                showVolume(true);
                return true;
            }
        });
        LinearLayout.LayoutParams pa = lp(sizeU, sizeU, 0.06f), pm = lp(sizeU, sizeU, 0.06f);
        pa.setMargins(px(gapU), px(0.06f), px(gapU), px(0.06f));
        pm.setMargins(px(gapU), px(0.06f), px(gapU), px(0.06f));
        bar.addView(a, pa);
        bar.addView(m, pm);
        return bar;
    }

    private static boolean isDelete(String t) {
        return t.equals("Видалити") || t.equals("Прибрати") || t.equals("Стерти всю історію") || t.startsWith("Вийти") || t.equals("Вихід");
    }

    protected Button btn(String s, int color, final View.OnClickListener l) {
        final String t0 = s;
        Button b = new Button(this);
        b.setText(s);
        b.setAllCaps(false);
        b.setTextColor(WHITE);
        b.setTextSize(TypedValue.COMPLEX_UNIT_PX, 0.34f * u);
        b.setTypeface(Typeface.DEFAULT_BOLD);
        b.setBackground(bg(color, 0.2f));
        b.setStateListAnimator(null);
        b.setMinHeight(0);
        b.setMinimumHeight(0);
        b.setMinWidth(0);
        b.setMinimumWidth(0);
        b.setPadding(px(0.3f), px(0.1f), px(0.3f), px(0.1f));
        b.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                if (!t0.equals("Пас") && !t0.equals("Добрати")) sfx(isDelete(t0) ? Snd.DEL : Snd.BTN);
                l.onClick(v);
            }
        });
        return b;
    }

    protected LinearLayout.LayoutParams lp(float w, float h, float margin) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(
            w < 0 ? (int) w : px(w), h < 0 ? (int) h : px(h));
        int m = px(margin);
        p.setMargins(m, m, m, m);
        return p;
    }

    protected Dialog makeDialog(View content, boolean cancelable) {
        LinearLayout wrap = new LinearLayout(this);
        wrap.setOrientation(LinearLayout.VERTICAL);
        wrap.setBackground(bg(0xFF263238, 0.3f));
        wrap.setPadding(px(0.5f), px(0.4f), px(0.5f), px(0.4f));
        wrap.addView(content);
        Dialog d = new Dialog(this, android.R.style.Theme_DeviceDefault_Dialog_NoActionBar);
        d.setContentView(wrap);
        d.setCancelable(cancelable);
        if (d.getWindow() != null) {
            d.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        }
        return d;
    }

    // ---------- налаштування ----------
    protected void showSettings() {
        showSettingsImpl(false);
    }

    protected void showSharedSettings() {
        showSettingsImpl(true);
    }

    private void showSettingsImpl(final boolean shared) {
        profSig = nick + "|" + avatar + "|" + photo;
        profileChanged = false;
        final Dialog dlg = new Dialog(this, android.R.style.Theme_DeviceDefault_NoActionBar_Fullscreen);
        ScrollView sv = new ScrollView(this);
        sv.setBackgroundColor(0xFF1F2A30);
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(px(0.6f), px(0.4f), px(0.6f), px(0.4f));
        box.setFocusableInTouchMode(true);
        sv.addView(box);
        LinearLayout titleRow = new LinearLayout(this);
        titleRow.setOrientation(LinearLayout.HORIZONTAL);
        titleRow.setGravity(Gravity.CENTER_VERTICAL);
        titleRow.addView(tv("Налаштування", 0.5f, WHITE, true), new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        titleRow.addView(btn("Зберегти", 0xFF2E7D32, new View.OnClickListener() {
            public void onClick(View v) {
                dlg.dismiss();
            }
        }), lp(3.2f, 0.95f, 0.05f));
        box.addView(titleRow, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        if (shared) {
            box.addView(tv("Нік", CAPS, CAP, false));
            etNick = new EditText(this);
            etNick.setText(nick);
            etNick.setSingleLine(true);
            etNick.setFilters(new InputFilter[]{new InputFilter.LengthFilter(14)});
            etNick.setTextColor(WHITE);
            etNick.setTextSize(TypedValue.COMPLEX_UNIT_PX, 0.4f * u);
            box.addView(etNick, lp(6f, ViewGroup.LayoutParams.WRAP_CONTENT, 0.05f));
        }

        LinearLayout mid = new LinearLayout(this);
        mid.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout left = new LinearLayout(this);
        left.setOrientation(LinearLayout.VERTICAL);
        left.addView(tv("Аватар", CAPS, CAP, false));
        avatarTabs = new LinearLayout(this);
        avatarTabs.setOrientation(LinearLayout.HORIZONTAL);
        left.addView(avatarTabs, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, px(0.85f)));
        avatarBox = new LinearLayout(this);
        avatarBox.setOrientation(LinearLayout.VERTICAL);
        left.addView(avatarBox, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, px(3.2f)));
        LinearLayout photoRow = new LinearLayout(this);
        photoRow.setOrientation(LinearLayout.HORIZONTAL);
        photoRow.setGravity(Gravity.CENTER_VERTICAL);
        photoRow.addView(btn("Моє фото", 0xFF455A64, new View.OnClickListener() {
            public void onClick(View v) {
                pickPhoto();
            }
        }), lp(4.4f, 0.85f, 0.02f));
        photoBox = new LinearLayout(this);
        photoRow.addView(photoBox, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        left.addView(photoRow, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        LinearLayout.LayoutParams lpl = new LinearLayout.LayoutParams(px(9.8f), px(6.5f));
        lpl.rightMargin = px(0.3f);
        if (shared) mid.addView(left, lpl);

        LinearLayout right = new LinearLayout(this);
        right.setOrientation(LinearLayout.VERTICAL);
        right.addView(onOff(labelPass(), !autoPass, new Toggle() {
            public void set(boolean on) {
                autoPass = !on;
            }
        }));
        right.addView(onOff(labelDraw(), showDraw, new Toggle() {
            public void set(boolean on) {
                showDraw = on;
            }
        }));
        right.addView(onOff("Вибір карт вручну", !hint, new Toggle() {
            public void set(boolean on) {
                hint = !on;
            }
        }));
        final int extra = addExtraSettings(right);
        if (hostMode()) {
            LinearLayout item = new LinearLayout(this);
            item.setOrientation(LinearLayout.VERTICAL);
            item.setGravity(Gravity.BOTTOM);
            speedBtn = btn("Швидкість ботів: " + botSpeed, 0xFF455A64, new View.OnClickListener() {
                public void onClick(View v) {
                    showSpeedDialog();
                }
            });
            item.addView(speedBtn, lp(ViewGroup.LayoutParams.MATCH_PARENT, 0.85f, 0.02f));
            right.addView(item, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        }
        LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(0, px(1.5f * (3 + extra + (hostMode() ? 1 : 0)) + 0.2f), 1f);
        if (!shared) mid.addView(right, rp);
        box.addView(mid, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout.LayoutParams hl = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, px(0.08f));
        hl.setMargins(0, px(0.25f), 0, px(0.15f));
        if (!shared) {
            box.addView(fancyLine(false), hl);
            box.addView(tv("Тема колоди", CAPS, CAP, false), lp(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, 0.05f));
            themeBox = new LinearLayout(this);
            themeBox.setOrientation(LinearLayout.VERTICAL);
            box.addView(themeBox, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            preview = new Preview(this);
            box.addView(preview, lp(ViewGroup.LayoutParams.MATCH_PARENT, 2.8f, 0.08f));
        } else {
            box.addView(tv(Cfg.SOUND ? "Фон і музична тема для всіх ігор (затисніть фон — вибір мелодії)" : "Фон для всіх ігор", CAPS, CAP, false));
            bgBox = new LinearLayout(this);
            bgBox.setOrientation(LinearLayout.VERTICAL);
            box.addView(bgBox, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        }

        avCat = 0;
        for (int k = 0; k < Avatars.CAT_NAMES.length; k++) {
            if (avatar >= Avatars.CAT_START[k] && avatar < Avatars.CAT_START[k + 1]) avCat = k;
        }
        refreshAvatars();
        refreshThemes();
        refreshBgs();
        dlg.setContentView(sv);
        if (dlg.getWindow() != null) {
            dlg.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN
                | WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        }
        dlg.setOnDismissListener(new android.content.DialogInterface.OnDismissListener() {
            public void onDismiss(android.content.DialogInterface d) {
                if (etNick != null) {
                    String s = etNick.getText().toString().trim().replace("|", " ").replace("~", " ").replace(";", " ");
                    if (s.length() == 0) s = "Гравець";
                    nick = s;
                }
                profileChanged = !profSig.equals(nick + "|" + avatar + "|" + photo);
                savePrefs();
                etNick = null;
                avatarBox = null;
                avatarTabs = null;
                photoBox = null;
                speedBtn = null;
                themeBox = null;
                bgBox = null;
                preview = null;
                onSettingsClosed();
            }
        });
        dlg.show();
        box.requestFocus();
    }

    // гарна лінія-роздільник із плавним згасанням до країв
    private View fancyLine(boolean vertical) {
        View v = new View(this);
        GradientDrawable g = new GradientDrawable(vertical ? GradientDrawable.Orientation.TOP_BOTTOM : GradientDrawable.Orientation.LEFT_RIGHT,
            new int[]{0x0090CAF9, 0xFF90CAF9, 0xFFFFE082, 0xFF90CAF9, 0x0090CAF9});
        v.setBackground(g);
        return v;
    }

    protected interface Toggle {
        void set(boolean on);
    }

    protected LinearLayout onOff(String text, boolean init, final Toggle cb) {
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setGravity(Gravity.BOTTOM);
        col.addView(tv(text, CAPS, CAP, false));
        final LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        col.addView(row, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        fillOnOff(row, init, cb);
        col.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        return col;
    }

    protected interface SpeedPick {
        void pick(int speed);
    }

    protected int speedOf(int ms) {
        int best = 3, d = Integer.MAX_VALUE;
        for (int k = 0; k < 5; k++) {
            if (Math.abs(SPEED_MS[k] - ms) < d) {
                d = Math.abs(SPEED_MS[k] - ms);
                best = k + 1;
            }
        }
        return best;
    }

    protected void showSpeedDialog() {
        showSpeedPick("Швидкість ботів", botSpeed, new SpeedPick() {
            public void pick(int val) {
                botSpeed = val;
                botDelay = SPEED_MS[val - 1];
                onBotDelay(botDelay);
                if (speedBtn != null) speedBtn.setText("Швидкість ботів: " + botSpeed);
            }
        });
    }

    protected void showSpeedPick(String title, int cur, final SpeedPick sp) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.addView(tv(title, 0.4f, WHITE, true), lp(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, 0.08f));
        final Dialog[] dd = new Dialog[1];
        for (int k = 1; k <= 5; k++) {
            final int val = k;
            String t = String.valueOf(k) + (k == 1 ? "  (повільно)" : k == 5 ? "  (швидко)" : "");
            box.addView(btn(t, cur == k ? 0xFF2E7D32 : 0xFF455A64, new View.OnClickListener() {
                public void onClick(View v) {
                    sp.pick(val);
                    dd[0].dismiss();
                }
            }), lp(ViewGroup.LayoutParams.MATCH_PARENT, 0.8f, 0.04f));
        }
        dd[0] = makeDialog(box, true);
        showSized(dd[0], 5.5f);
    }

    private void fillOnOff(final LinearLayout row, boolean on, final Toggle cb) {
        row.removeAllViews();
        for (int k = 0; k < 2; k++) {
            final boolean val = k == 0;
            LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, px(0.85f), 1f);
            p.setMargins(px(0.02f), px(0.02f), px(0.02f), px(0.02f));
            Button ob = btn(val ? "Увімкнено" : "Вимкнено", on == val ? (val ? 0xFF2E7D32 : 0xFFC62828) : 0xFF455A64, new View.OnClickListener() {
                public void onClick(View v) {
                    cb.set(val);
                    fillOnOff(row, val, cb);
                }
            });
            ob.setTextSize(TypedValue.COMPLEX_UNIT_PX, 0.3f * u);
            ob.setPadding(px(0.05f), px(0.05f), px(0.05f), px(0.05f));
            ob.setSingleLine(true);
            row.addView(ob, p);
        }
    }

    private void refreshBgs() {
        if (bgBox == null) return;
        bgBox.removeAllViews();
        int per = 4;
        LinearLayout row = null;
        for (int i = 0; i < Bg.NAMES.length; i++) {
            if (i % per == 0) {
                row = new LinearLayout(this);
                row.setOrientation(LinearLayout.HORIZONTAL);
                bgBox.addView(row, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            }
            final int id = i;
            BgPreview v = new BgPreview(this, id);
            v.setOnClickListener(new View.OnClickListener() {
                public void onClick(View x) {
                    bgId = id;
                    musicFor(musicIsGame());
                    refreshBgs();
                }
            });
            if (Cfg.SOUND) {
                v.setOnLongClickListener(new View.OnLongClickListener() {
                    public boolean onLongClick(View x) {
                        showMelodyPick(id);
                        return true;
                    }
                });
            }
            LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, px(1.5f), 1f);
            p.setMargins(px(0.06f), px(0.06f), px(0.06f), px(0.06f));
            row.addView(v, p);
        }
    }

    private void showMelodyPick(final int bg) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.addView(tv("Мелодія для фону «" + Bg.NAMES[bg] + "»", 0.4f, WHITE, true), lp(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, 0.08f));
        final LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        final int[] pick = {bgMel(bg)};
        final String[] playing = {""};
        final Runnable[] fill = new Runnable[1];
        fill[0] = new Runnable() {
            public void run() {
                list.removeAllViews();
                for (int k = 1; k <= 7; k++) {
                    final int kk = k;
                    LinearLayout row = new LinearLayout(this_());
                    row.setOrientation(LinearLayout.HORIZONTAL);
                    boolean has = snd.hasTrack("music" + k);
                    row.addView(btn("Мелодія " + k + (has ? "" : " (немає файлу)"), pick[0] == k ? 0xFF2E7D32 : 0xFF455A64, new View.OnClickListener() {
                        public void onClick(View v) {
                            pick[0] = kk;
                            setBgMel(bg, kk);
                            if (bg == bgId) musicFor(musicIsGame());
                            fill[0].run();
                        }
                    }), lp(5.2f, 0.8f, 0.03f));
                    boolean on = playing[0].equals("music" + k);
                    row.addView(btn(on ? "■ Стоп" : "▶ Слухати", 0xFF37474F, new View.OnClickListener() {
                        public void onClick(View v) {
                            if (playing[0].equals("music" + kk)) {
                                playing[0] = "";
                                snd.stopPreview();
                            } else {
                                playing[0] = "music" + kk;
                                snd.preview("music" + kk);
                            }
                            fill[0].run();
                        }
                    }), lp(2.6f, 0.8f, 0.03f));
                    list.addView(row);
                }
            }
        };
        fill[0].run();
        box.addView(list);
        Dialog d = makeDialog(box, true);
        d.setOnDismissListener(new android.content.DialogInterface.OnDismissListener() {
            public void onDismiss(android.content.DialogInterface x) {
                snd.stopPreview();
            }
        });
        showSized(d, 9f);
    }

    private BaseActivity this_() {
        return this;
    }

    private class BgPreview extends View {
        private final int id;
        private final android.graphics.Paint pp = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);

        BgPreview(Context c, int id) {
            super(c);
            this.id = id;
        }

        @Override
        protected void onDraw(Canvas c) {
            int w = getWidth(), h = getHeight();
            c.save();
            Bg.drawDirect(c, w, h, id);
            c.restore();
            pp.setTextAlign(android.graphics.Paint.Align.CENTER);
            pp.setTextSize(h * 0.26f);
            pp.setTypeface(Typeface.DEFAULT_BOLD);
            pp.setStyle(android.graphics.Paint.Style.FILL);
            pp.setColor(WHITE);
            c.drawText(Bg.NAMES[id], w / 2f, h * 0.62f, pp);
            if (id == bgId) {
                pp.setStyle(android.graphics.Paint.Style.STROKE);
                pp.setStrokeWidth(h * 0.07f);
                pp.setColor(0xFFFFD54F);
                c.drawRect(0, 0, w, h, pp);
            }
        }
    }

    private void refreshAvatars() {
        if (avatarBox == null) return;
        avatarTabs.removeAllViews();
        for (int k = 0; k < Avatars.CAT_NAMES.length; k++) {
            final int cat = k;
            Button t = btn(Avatars.CAT_NAMES[k], avCat == k ? 0xFF2E7D32 : 0xFF455A64, new View.OnClickListener() {
                public void onClick(View v) {
                    avCat = cat;
                    refreshAvatars();
                }
            });
            t.setTextSize(TypedValue.COMPLEX_UNIT_PX, 0.27f * u);
            t.setPadding(px(0.04f), px(0.05f), px(0.04f), px(0.05f));
            t.setSingleLine(true);
            LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f);
            tp.setMargins(px(0.03f), px(0.03f), px(0.03f), px(0.03f));
            avatarTabs.addView(t, tp);
        }
        avatarBox.removeAllViews();
        int from = Avatars.CAT_START[avCat], to = Avatars.CAT_START[avCat + 1];
        int perRow = 6;
        LinearLayout row = null;
        for (int i = from; i < to; i++) {
            if ((i - from) % perRow == 0) {
                row = new LinearLayout(this);
                row.setOrientation(LinearLayout.HORIZONTAL);
                avatarBox.addView(row);
            }
            final int idx = i;
            Avatars.AvatarView av = new Avatars.AvatarView(this);
            av.set(idx, "-", avatar == idx);
            av.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) {
                    avatar = idx;
                    refreshAvatars();
                }
            });
            row.addView(av, lp(1.5f, 1.5f, 0.05f));
        }
        photoBox.removeAllViews();
        if (photo.length() > 8) {
            Avatars.AvatarView pv = new Avatars.AvatarView(this);
            pv.set(-1, photo, avatar < 0);
            pv.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) {
                    avatar = -1;
                    refreshAvatars();
                }
            });
            LinearLayout.LayoutParams plp = lp(1.75f, 1.75f, 0.02f);
            plp.leftMargin = px(0.45f);
            photoBox.addView(pv, plp);
        }
    }

    private void refreshThemes() {
        if (themeBox == null) return;
        themeBox.removeAllViews();
        LinearLayout row = null;
        for (int i = 0; i < deckCount(); i++) {
            if (i % 3 == 0) {
                row = new LinearLayout(this);
                row.setOrientation(LinearLayout.HORIZONTAL);
                themeBox.addView(row, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            }
            final int id = i;
            LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, px(0.9f), 1f);
            p.setMargins(px(0.05f), px(0.05f), px(0.05f), px(0.05f));
            row.addView(btn(deckTitle(i), deckSel() == i ? 0xFF2E7D32 : 0xFF455A64, new View.OnClickListener() {
                public void onClick(View v) {
                    deckPick(id);
                    refreshThemes();
                }
            }), p);
        }
        if (preview != null) preview.invalidate();
    }

    private class Preview extends View {
        private final CardPainter painter = new CardPainter(Theme.get(0));
        private final Card[] cards = {new Card(0, 7), new Card(3, Engine.SKIP), new Card(2, Engine.D2), new Card(4, Engine.D4)};

        Preview(Context c) {
            super(c);
        }

        @Override
        protected void onDraw(Canvas c) {
            Theme t = Theme.get(themeId);
            painter.th = t;
            Bg.drawDirect(c, getWidth(), getHeight(), bgId);
            if (previewDeck(c, getWidth(), getHeight())) return;
            float h = getHeight() * 0.92f;
            float w = h * 0.667f;
            int k = cards.length + 1;
            float gap = Math.max(u * 0.05f, (getWidth() - k * w) / (k + 1));
            float y = getHeight() * 0.04f;
            for (int i = 0; i < cards.length; i++) {
                painter.draw(c, cards[i], gap + i * (w + gap), y, w, h, false);
            }
            painter.back(c, gap + cards.length * (w + gap), y, w, h);
        }
    }

    private void pickPhoto() {
        try {
            Intent i;
            if (Build.VERSION.SDK_INT >= 33) {
                i = new Intent(MediaStore.ACTION_PICK_IMAGES);
            } else {
                i = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
            }
            i.setType("image/*");
            startActivityForResult(i, REQ_PHOTO);
        } catch (Exception e) {
            try {
                Intent g = new Intent(Intent.ACTION_GET_CONTENT);
                g.setType("image/*");
                startActivityForResult(g, REQ_PHOTO);
            } catch (Exception e2) {
                toast("Галерею не знайдено");
            }
        }
    }

    @Override
    protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (req != REQ_PHOTO || res != RESULT_OK || data == null || data.getData() == null) return;
        try {
            Uri uri = data.getData();
            InputStream in = getContentResolver().openInputStream(uri);
            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inJustDecodeBounds = true;
            BitmapFactory.decodeStream(in, null, o);
            in.close();
            int sm = 1;
            while (Math.max(o.outWidth, o.outHeight) / (sm * 2) >= 1400) sm *= 2;
            BitmapFactory.Options o2 = new BitmapFactory.Options();
            o2.inSampleSize = sm;
            in = getContentResolver().openInputStream(uri);
            Bitmap b = BitmapFactory.decodeStream(in, null, o2);
            in.close();
            if (b == null) throw new RuntimeException("decode");
            showCrop(b);
        } catch (Exception e) {
            toast("Не вдалося завантажити фото");
        }
    }

    private void showCrop(Bitmap b) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(Gravity.CENTER_HORIZONTAL);
        box.addView(tv("Посуньте і наблизьте фото (двома пальцями)", 0.32f, WHITE, true), lp(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, 0.05f));
        final CropView cv = new CropView(this, b);
        box.addView(cv, lp(6f, 6f, 0.05f));
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        final Dialog[] dd = new Dialog[1];
        row.addView(btn("Скасувати", 0xFF455A64, new View.OnClickListener() {
            public void onClick(View v) {
                dd[0].dismiss();
            }
        }), lp(3.0f, 0.95f, 0.06f));
        row.addView(btn("Готово", 0xFF2E7D32, new View.OnClickListener() {
            public void onClick(View v) {
                try {
                    Bitmap sm = cv.result();
                    ByteArrayOutputStream bo = new ByteArrayOutputStream();
                    sm.compress(Bitmap.CompressFormat.JPEG, 82, bo);
                    photo = Base64.encodeToString(bo.toByteArray(), Base64.NO_WRAP);
                    avatar = -1;
                    savePrefs();
                    refreshAvatars();
                } catch (Exception e) {
                    toast("Не вдалося обрізати фото");
                }
                dd[0].dismiss();
            }
        }), lp(3.0f, 0.95f, 0.06f));
        box.addView(row);
        dd[0] = makeDialog(box, true);
        showSized(dd[0], 8.4f);
    }

    private class CropView extends View {
        private final Bitmap bmp;
        private float sc, tx, ty, minS;
        private int size;
        private boolean init;
        private float lastDist, lastMx, lastMy;
        private int lastCnt;
        private final Paint cp = new Paint(Paint.ANTI_ALIAS_FLAG);

        CropView(Context c, Bitmap b) {
            super(c);
            bmp = b;
        }

        private void clamp() {
            if (sc < minS) sc = minS;
            if (sc > minS * 8f) sc = minS * 8f;
            float w = bmp.getWidth() * sc, h = bmp.getHeight() * sc;
            if (tx > 0) tx = 0;
            if (ty > 0) ty = 0;
            if (tx < size - w) tx = size - w;
            if (ty < size - h) ty = size - h;
        }

        @Override
        protected void onDraw(Canvas c) {
            if (!init) {
                size = getWidth();
                minS = size / (float) Math.min(bmp.getWidth(), bmp.getHeight());
                sc = minS;
                tx = (size - bmp.getWidth() * sc) / 2f;
                ty = (size - bmp.getHeight() * sc) / 2f;
                init = true;
            }
            c.drawColor(0xFF000000);
            c.save();
            c.translate(tx, ty);
            c.scale(sc, sc);
            c.drawBitmap(bmp, 0, 0, null);
            c.restore();
            float ring = size * 0.3f;
            cp.setStyle(Paint.Style.STROKE);
            cp.setStrokeWidth(ring);
            cp.setColor(0xAA000000);
            c.drawCircle(size / 2f, size / 2f, size / 2f + ring / 2f, cp);
            cp.setStrokeWidth(Math.max(2f, size * 0.01f));
            cp.setColor(0xFFFFFFFF);
            c.drawCircle(size / 2f, size / 2f, size / 2f, cp);
        }

        @Override
        public boolean onTouchEvent(android.view.MotionEvent e) {
            int n = e.getPointerCount();
            float mx = 0, my = 0;
            for (int i = 0; i < n; i++) {
                mx += e.getX(i);
                my += e.getY(i);
            }
            mx /= n;
            my /= n;
            float dist = 0;
            if (n >= 2) {
                float dx = e.getX(0) - e.getX(1), dy = e.getY(0) - e.getY(1);
                dist = (float) Math.sqrt(dx * dx + dy * dy);
            }
            int act = e.getAction();
            if (act == android.view.MotionEvent.ACTION_DOWN || n != lastCnt) {
                lastCnt = n;
                lastMx = mx;
                lastMy = my;
                lastDist = dist;
                return true;
            }
            if (act == android.view.MotionEvent.ACTION_MOVE && init) {
                tx += mx - lastMx;
                ty += my - lastMy;
                if (n >= 2 && lastDist > 0 && dist > 0) {
                    float ns = Math.max(minS, Math.min(minS * 8f, sc * dist / lastDist));
                    float f = ns / sc;
                    tx = mx - (mx - tx) * f;
                    ty = my - (my - ty) * f;
                    sc = ns;
                }
                lastMx = mx;
                lastMy = my;
                lastDist = dist;
                clamp();
                invalidate();
            }
            return true;
        }

        Bitmap result() {
            int bw = bmp.getWidth(), bh = bmp.getHeight();
            int x = Math.max(0, (int) (-tx / sc));
            int y = Math.max(0, (int) (-ty / sc));
            int side = (int) (size / sc);
            if (x + side > bw) side = bw - x;
            if (y + side > bh) side = Math.min(side, bh - y);
            if (side < 8) side = Math.min(bw, bh);
            Bitmap crop = Bitmap.createBitmap(bmp, x, y, side, side);
            return Bitmap.createScaledBitmap(crop, 96, 96, true);
        }
    }

    // ---------- історія ----------
    protected void saveHistory(String[] names, int[] totals, int loser) {
        try {
            JSONObject o = new JSONObject();
            o.put("t", System.currentTimeMillis());
            o.put("loser", names[loser]);
            o.put("pts", totals[loser]);
            JSONArray ns = new JSONArray();
            JSONArray ts = new JSONArray();
            for (int i = 0; i < names.length; i++) {
                ns.put(names[i]);
                ts.put(totals[i]);
            }
            o.put("names", ns);
            o.put("tot", ts);
            JSONArray old = new JSONArray(sp.getString("hist", "[]"));
            JSONArray neu = new JSONArray();
            neu.put(o);
            for (int i = 0; i < old.length() && i < 99; i++) neu.put(old.get(i));
            sp.edit().putString("hist", neu.toString()).commit();
        } catch (Exception e) {
            // ignore
        }
    }

    protected View buildHistory(final Runnable after) {
        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        try {
            JSONArray arr = new JSONArray(sp.getString("hist", "[]"));
            SimpleDateFormat fmt = new SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault());
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.getJSONObject(i);
                LinearLayout card = new LinearLayout(this);
                card.setOrientation(LinearLayout.VERTICAL);
                card.setBackground(bg(0xFF2C3A41, 0.2f));
                card.setPadding(px(0.4f), px(0.25f), px(0.4f), px(0.25f));
                card.addView(tv(fmt.format(new Date(o.getLong("t"))), 0.28f, 0xFFB0BEC5, false));
                card.addView(tv("Програв: " + o.getString("loser") + " — " + o.getInt("pts") + " очок", 0.38f, WHITE, true));
                JSONArray ns = o.getJSONArray("names");
                JSONArray ts = o.getJSONArray("tot");
                StringBuilder sb = new StringBuilder();
                for (int j = 0; j < ns.length(); j++) {
                    if (j > 0) sb.append("   ");
                    sb.append(ns.getString(j)).append(": ").append(ts.getInt(j));
                }
                card.addView(tv(sb.toString(), 0.3f, 0xFFCFD8DC, false));
                final int idx = i;
                card.addView(btn("Видалити", 0xFFC62828, new View.OnClickListener() {
                    public void onClick(View v) {
                        deleteHist(idx);
                        after.run();
                    }
                }), lp(2.4f, 0.75f, 0.05f));
                list.addView(card, lp(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, 0.1f));
            }
            if (arr.length() == 0) {
                list.addView(tv("Історія порожня", 0.4f, 0xFFB0BEC5, false));
            }
        } catch (Exception e) {
            list.addView(tv("Історія порожня", 0.4f, 0xFFB0BEC5, false));
        }
        return list;
    }
    protected boolean hasHist() {
        try {
            return new JSONArray(sp.getString("hist", "[]")).length() > 0;
        } catch (Exception e) {
            return false;
        }
    }

    protected void askClearHist(final Runnable after) {
        new AlertDialog.Builder(this).setTitle("Стерти всю історію?")
            .setPositiveButton("Так", new android.content.DialogInterface.OnClickListener() {
                public void onClick(android.content.DialogInterface d, int w) {
                    sp.edit().putString("hist", "[]").commit();
                    after.run();
                }
            }).setNegativeButton("Ні", null).show();
    }

    private void deleteHist(int idx) {
        try {
            JSONArray old = new JSONArray(sp.getString("hist", "[]"));
            JSONArray neu = new JSONArray();
            for (int i = 0; i < old.length(); i++) {
                if (i != idx) neu.put(old.get(i));
            }
            sp.edit().putString("hist", neu.toString()).commit();
        } catch (Exception e) {
            // ignore
        }
    }
}
