package com.example.gryntsivgames;

import android.content.Context;
import android.content.Intent;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import com.example.gryntsivgames.durak.DuCards;
import com.example.gryntsivgames.sea.Painter;
import com.example.gryntsivgames.uno.BaseActivity;
import com.example.gryntsivgames.uno.Card;
import com.example.gryntsivgames.uno.CardPainter;
import com.example.gryntsivgames.uno.IconView;
import com.example.gryntsivgames.uno.Snd;
import com.example.gryntsivgames.uno.Theme;

// Головний екран: вибір гри та спільні налаштування
public class MainActivity extends BaseActivity {
    private static final int WRAP = ViewGroup.LayoutParams.WRAP_CONTENT;
    private static final int MATCH = ViewGroup.LayoutParams.MATCH_PARENT;
    private FrameLayout root;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        root = new FrameLayout(this);
        setContentView(root);
        showMenu();
    }

    @Override
    protected void onResume() {
        super.onResume();
        musicFor(false);
    }

    @Override
    protected void onSettingsClosed() {
        showMenu();
    }

    @Override
    public void onBackPressed() {
        finish();
    }

    // гарний напис назви
    private class Title extends View {
        private final Paint pt = new Paint(Paint.ANTI_ALIAS_FLAG);

        Title(Context c) {
            super(c);
        }

        @Override
        protected void onDraw(Canvas cv) {
            float W = getWidth(), H = getHeight();
            String s = "Gryntsiv Games";
            pt.setTypeface(Typeface.DEFAULT_BOLD);
            pt.setTextAlign(Paint.Align.CENTER);
            pt.setTextSize(H * 0.78f);
            float w = pt.measureText(s);
            if (w > W * 0.96f) pt.setTextSize(H * 0.78f * W * 0.96f / w);
            float base = H * 0.78f;
            pt.setStyle(Paint.Style.STROKE);
            pt.setStrokeWidth(H * 0.16f);
            pt.setColor(0x99000000);
            cv.drawText(s, W / 2f + H * 0.04f, base + H * 0.06f, pt);
            pt.setColor(0xFF0B1F3A);
            cv.drawText(s, W / 2f, base, pt);
            pt.setStyle(Paint.Style.FILL);
            pt.setColor(0xFFFFD54F);
            cv.drawText(s, W / 2f, base, pt);
            pt.setColor(0x55FFFFFF);
            cv.save();
            cv.clipRect(0, 0, W, H * 0.42f);
            cv.drawText(s, W / 2f, base, pt);
            cv.restore();
        }
    }

    // квадратна плитка гри з малюнком
    private class Tile extends View {
        private final int kind;
        private final Paint pt = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF r = new RectF();
        private final CardPainter cp = new CardPainter(Theme.get(0));

        Tile(Context c, int kind) {
            super(c);
            this.kind = kind;
        }

        private void card(Canvas cv, Card k, float cx, float cy, float w, float h, float ang) {
            cv.save();
            cv.rotate(ang, cx, cy);
            cp.draw(cv, k, cx - w / 2, cy - h / 2, w, h, false);
            cv.restore();
        }

        private final DuCards dp = new DuCards(Theme.get(0));

        {
            dp.setStyle(1);
        }

        private void dcard(Canvas cv, Card k, float cx, float cy, float w, float h, float ang) {
            cv.save();
            cv.rotate(ang, cx, cy);
            dp.draw(cv, k, cx - w / 2, cy - h / 2, w, h, 0);
            cv.restore();
        }

        @Override
        protected void onDraw(Canvas cv) {
            float W = getWidth(), H = getHeight();
            pt.setStyle(Paint.Style.FILL);
            if (kind == 0) {
                pt.setColor(0xFF1B5E3A);
                r.set(0, 0, W, H);
                cv.drawRoundRect(r, W * 0.1f, W * 0.1f, pt);
                pt.setColor(0x33000000);
                r.set(W * 0.05f, H * 0.05f, W * 0.95f, H * 0.95f);
                cv.drawRoundRect(r, W * 0.08f, W * 0.08f, pt);
                card(cv, new Card(1, 10), W * 0.31f, H * 0.54f, W * 0.40f, H * 0.58f, -18);
                card(cv, new Card(3, 12), W * 0.69f, H * 0.54f, W * 0.40f, H * 0.58f, 18);
                card(cv, new Card(4, 13), W * 0.50f, H * 0.48f, W * 0.42f, H * 0.62f, 0);
            } else if (kind == 1) {
                pt.setColor(0xFF0D3B6E);
                r.set(0, 0, W, H);
                cv.drawRoundRect(r, W * 0.1f, W * 0.1f, pt);
                pt.setColor(0xFF1F78C4);
                r.set(W * 0.06f, H * 0.06f, W * 0.94f, H * 0.94f);
                cv.drawRoundRect(r, W * 0.08f, W * 0.08f, pt);
                pt.setStyle(Paint.Style.STROKE);
                pt.setStrokeWidth(Math.max(1f, W * 0.006f));
                pt.setColor(0x55FFFFFF);
                for (int i = 1; i < 8; i++) {
                    cv.drawLine(W * 0.06f + i * W * 0.11f, H * 0.06f, W * 0.06f + i * W * 0.11f, H * 0.94f, pt);
                    cv.drawLine(W * 0.06f, H * 0.06f + i * H * 0.11f, W * 0.94f, H * 0.06f + i * H * 0.11f, pt);
                }
                float cs = W * 0.11f;
                Painter.draw(cv, pt, 0, W * 0.06f + cs, H * 0.06f + cs, cs, 4, true, 0);
                Painter.draw(cv, pt, 0, W * 0.06f + cs * 4, H * 0.06f + cs * 4, cs, 3, true, 1);
                Painter.draw(cv, pt, 0, W * 0.06f + cs, H * 0.06f + cs * 5, cs, 2, false, 2);
                Painter.draw(cv, pt, 0, W * 0.06f + cs * 6, H * 0.06f + cs, cs, 1, true, 3);
                pt.setStyle(Paint.Style.STROKE);
                pt.setStrokeWidth(cs * 0.14f);
                pt.setColor(0xFFFF1744);
                float cx = W * 0.06f + cs * 5.5f, cy = H * 0.06f + cs * 4.5f, d = cs * 0.3f;
                cv.drawLine(cx - d, cy - d, cx + d, cy + d, pt);
                cv.drawLine(cx - d, cy + d, cx + d, cy - d, pt);
                pt.setStyle(Paint.Style.FILL);
                pt.setColor(0xFFE1F5FE);
                cv.drawCircle(W * 0.06f + cs * 2.5f, H * 0.06f + cs * 3.5f, cs * 0.12f, pt);
                cv.drawCircle(W * 0.06f + cs * 7.5f, H * 0.06f + cs * 6.5f, cs * 0.12f, pt);
            } else if (kind == 2) {
                pt.setColor(0xFF4A148C);
                r.set(0, 0, W, H);
                cv.drawRoundRect(r, W * 0.1f, W * 0.1f, pt);
                pt.setColor(0x33000000);
                r.set(W * 0.05f, H * 0.05f, W * 0.95f, H * 0.95f);
                cv.drawRoundRect(r, W * 0.08f, W * 0.08f, pt);
                dcard(cv, new Card(1, 11), W * 0.30f, H * 0.55f, W * 0.40f, H * 0.58f, -20);
                dcard(cv, new Card(0, 12), W * 0.70f, H * 0.55f, W * 0.40f, H * 0.58f, 20);
                dcard(cv, new Card(2, 13), W * 0.50f, H * 0.47f, W * 0.42f, H * 0.62f, 0);
            } else {
                pt.setColor(0xFF1C2226);
                r.set(0, 0, W, H);
                cv.drawRoundRect(r, W * 0.1f, W * 0.1f, pt);
                pt.setColor(0xFF2A3238);
                r.set(W * 0.05f, H * 0.05f, W * 0.95f, H * 0.95f);
                cv.drawRoundRect(r, W * 0.08f, W * 0.08f, pt);
                pt.setColor(0xFF78909C);
                pt.setTextAlign(Paint.Align.CENTER);
                pt.setTypeface(Typeface.DEFAULT_BOLD);
                pt.setTextSize(H * 0.15f);
                cv.drawText("У процесі", W / 2f, H * 0.47f, pt);
                cv.drawText("розробки", W / 2f, H * 0.47f + H * 0.18f, pt);
            }
        }
    }

    private LinearLayout tileBox(final int kind, String caption, float side) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(Gravity.CENTER);
        Tile tile = new Tile(this, kind);
        tile.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                sfx(Snd.BTN);
                if (kind == 0) startActivity(new Intent(MainActivity.this, com.example.gryntsivgames.uno.MainActivity.class));
                else if (kind == 1) startActivity(new Intent(MainActivity.this, com.example.gryntsivgames.sea.SeaActivity.class));
                else if (kind == 2) startActivity(new Intent(MainActivity.this, com.example.gryntsivgames.durak.DurakActivity.class));
                else toast("У процесі розробки");
            }
        });
        box.addView(tile, lp(side, side, 0.1f));
        box.addView(tv(caption, 0.34f, 0xFFE0E6EA, false), lp(WRAP, WRAP, 0.05f));
        return box;
    }

    private void showMenu() {
        root.removeAllViews();
        root.setBackgroundColor(0xFF1B5E3A);
        root.addView(new BgView(this), new FrameLayout.LayoutParams(MATCH, MATCH));
        FrameLayout.LayoutParams tp = new FrameLayout.LayoutParams(px(9f), px(1.4f), Gravity.TOP | Gravity.CENTER_HORIZONTAL);
        tp.setMargins(0, px(0.35f), 0, 0);
        root.addView(new Title(this), tp);
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER);
        String[] caps = {"Уно", "Морський бій", "Дурак", "1000"};
        for (int i = 0; i < 4; i++) row.addView(tileBox(i, caps[i], 3.35f), lp(WRAP, WRAP, 0.17f));
        FrameLayout.LayoutParams rp = new FrameLayout.LayoutParams(MATCH, MATCH, Gravity.CENTER);
        rp.topMargin = px(1.2f);
        root.addView(row, rp);
        FrameLayout.LayoutParams gp = new FrameLayout.LayoutParams(px(1.1f), px(1.1f), Gravity.TOP | Gravity.RIGHT);
        gp.setMargins(0, px(0.3f), px(0.3f), 0);
        IconView gear = new IconView(this, IconView.GEAR);
        gear.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                sfx(Snd.BTN);
                showSharedSettings();
            }
        });
        root.addView(gear, gp);
        FrameLayout.LayoutParams br = new FrameLayout.LayoutParams(px(3.0f), px(1.0f), Gravity.BOTTOM | Gravity.RIGHT);
        br.setMargins(0, 0, px(0.3f), px(0.3f));
        root.addView(btn("Вихід", btnCol(), new View.OnClickListener() {
            public void onClick(View v) {
                finish();
            }
        }), br);
        FrameLayout.LayoutParams bl = new FrameLayout.LayoutParams(WRAP, WRAP, Gravity.BOTTOM | Gravity.LEFT);
        bl.setMargins(px(0.3f), 0, 0, px(0.3f));
        root.addView(audioBar(1.0f), bl);
        musicFor(false);
    }
}
