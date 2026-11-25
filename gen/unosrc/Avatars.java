package com.example.gryntsivgames.uno;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.RectF;
import android.util.Base64;
import android.view.View;

import java.util.HashMap;

public class Avatars {
    public static final String[] EMO = {
        "🐱", "🐶", "🦊", "🐼", "🐸", "🦁",
        "🐵", "🐯", "🐰", "🐻", "🐧", "🦄"
    };
    private static final int[] BG = {
        0xFFFFB74D, 0xFF90CAF9, 0xFFFFCC80, 0xFFB0BEC5, 0xFFA5D6A7, 0xFFFFE082,
        0xFFBCAAA4, 0xFFFFAB91, 0xFFF8BBD0, 0xFFD7CCC8, 0xFF81D4FA, 0xFFCE93D8
    };
    private static final HashMap<String, Bitmap> CACHE = new HashMap<String, Bitmap>();
    private static final Paint P = new Paint(Paint.ANTI_ALIAS_FLAG);
    private static final Path CLIP = new Path();
    private static final Rect SRC = new Rect();
    private static final RectF DST = new RectF();

    public static Bitmap photo(String b64) {
        if (b64 == null || b64.length() < 8 || b64.equals("-")) return null;
        Bitmap b = CACHE.get(b64);
        if (b == null) {
            try {
                byte[] d = Base64.decode(b64, Base64.NO_WRAP);
                b = BitmapFactory.decodeByteArray(d, 0, d.length);
            } catch (Exception e) {
                b = null;
            }
            if (b != null) CACHE.put(b64, b);
        }
        return b;
    }

    public static final String[] CAT_NAMES = {"Тварини", "Люди", "Супергерої", "Лиходії", "Мультяшні"};
    public static final int[] CAT_START = {0, 12, 19, 25, 31, 37};
    public static final int TOTAL = 37;

    private static final float[] ESIZE = {1.9f, 1.95f, 1.9f, 1.65f, 1.65f, 1.9f, 2.15f, 1.9f, 1.9f, 1.65f, 2.25f, 1.9f};
    private static final float[] EDY = {0f, -0.04f, 0f, 0f, -0.1f, 0f, -0.2f, 0f, -0.3f, 0f, -0.2f, 0f};

    private static void content(Canvas c, float cx, float cy, float r, int a, boolean over) {
        noBg = over;
        P.setTextAlign(Paint.Align.CENTER);
        if (a < EMO.length) {
            P.setStyle(Paint.Style.FILL);
            P.setTextSize(r * ESIZE[a]);
            cy += EDY[a] * r;
            P.setColor(0xFF000000);
            Paint.FontMetrics fm = P.getFontMetrics();
            c.drawText(EMO[a], cx, cy - (fm.ascent + fm.descent) / 2f, P);
        } else {
            gc = c;
            gx = cx;
            gy = cy + 0.06f * r;
            gr = r * 1.2f;
            character(a);
        }
    }

    public static void draw(Canvas c, float cx, float cy, float r, int avatar, Bitmap photo) {
        c.save();
        CLIP.reset();
        CLIP.addCircle(cx, cy, r, Path.Direction.CW);
        c.clipPath(CLIP);
        int a = avatar;
        if (a < 0 || a >= TOTAL) a = 0;
        if (photo != null) {
            SRC.set(0, 0, photo.getWidth(), photo.getHeight());
            DST.set(cx - r, cy - r, cx + r, cy + r);
            c.drawBitmap(photo, SRC, DST, P);
        } else {
            P.setStyle(Paint.Style.FILL);
            P.setColor(a < EMO.length ? BG[a] : 0xFF455A64);
            c.drawCircle(cx, cy, r, P);
            content(c, cx, cy, r, a, false);
        }
        c.restore();
        P.setStyle(Paint.Style.STROKE);
        P.setStrokeWidth(Math.max(2f, r * 0.07f));
        P.setColor(0xCCFFFFFF);
        c.drawCircle(cx, cy, r, P);
        P.setStyle(Paint.Style.FILL);
        if (photo == null) {
            // голова поверх круга: верхня частина малюється без обрізання колом
            c.save();
            c.clipRect(cx - r * 3f, cy - r * 3f, cx + r * 3f, cy + r * (a < EMO.length ? 0.7f : 0.55f));
            content(c, cx, cy, r, a, true);
            c.restore();
            noBg = false;
        }
    }

    // ---------- малювання персонажів (координати в частках радіуса) ----------
    private static Canvas gc;
    private static float gx, gy, gr;
    private static final Path PATH = new Path();
    private static final RectF RF = new RectF();
    private static final int INK = 0xFF263238, WHITE = 0xFFFFFFFF;

    private static float X(float a) {
        return gx + a * gr;
    }

    private static float Y(float a) {
        return gy + a * gr;
    }

    private static boolean noBg;

    private static void cir(float x, float y, float rad, int col) {
        if (noBg && x == 0 && y == 0 && rad >= 1.0f) return;
        P.setStyle(Paint.Style.FILL);
        P.setColor(col);
        gc.drawCircle(X(x), Y(y), rad * gr, P);
    }

    private static void ring(float x, float y, float rad, float w, int col) {
        P.setStyle(Paint.Style.STROKE);
        P.setStrokeWidth(w * gr);
        P.setColor(col);
        gc.drawCircle(X(x), Y(y), rad * gr, P);
        P.setStyle(Paint.Style.FILL);
    }

    private static void ovl(float x, float y, float rx, float ry, int col) {
        P.setStyle(Paint.Style.FILL);
        P.setColor(col);
        RF.set(X(x - rx), Y(y - ry), X(x + rx), Y(y + ry));
        gc.drawOval(RF, P);
    }

    private static void rct(float l, float t, float r, float b, int col) {
        P.setStyle(Paint.Style.FILL);
        P.setColor(col);
        gc.drawRect(X(l), Y(t), X(r), Y(b), P);
    }

    private static void rrc(float l, float t, float r, float b, float rad, int col) {
        P.setStyle(Paint.Style.FILL);
        P.setColor(col);
        RF.set(X(l), Y(t), X(r), Y(b));
        gc.drawRoundRect(RF, rad * gr, rad * gr, P);
    }

    private static void tri(float x1, float y1, float x2, float y2, float x3, float y3, int col) {
        P.setStyle(Paint.Style.FILL);
        P.setColor(col);
        PATH.reset();
        PATH.moveTo(X(x1), Y(y1));
        PATH.lineTo(X(x2), Y(y2));
        PATH.lineTo(X(x3), Y(y3));
        PATH.close();
        gc.drawPath(PATH, P);
    }

    private static void arcF(float x, float y, float rx, float ry, float st, float sw, int col) {
        P.setStyle(Paint.Style.FILL);
        P.setColor(col);
        RF.set(X(x - rx), Y(y - ry), X(x + rx), Y(y + ry));
        gc.drawArc(RF, st, sw, true, P);
    }

    private static void arcS(float x, float y, float rx, float ry, float st, float sw, float w, int col) {
        P.setStyle(Paint.Style.STROKE);
        P.setStrokeWidth(w * gr);
        P.setColor(col);
        RF.set(X(x - rx), Y(y - ry), X(x + rx), Y(y + ry));
        gc.drawArc(RF, st, sw, false, P);
        P.setStyle(Paint.Style.FILL);
    }

    private static void lin(float x1, float y1, float x2, float y2, float w, int col) {
        P.setStyle(Paint.Style.STROKE);
        P.setStrokeWidth(w * gr);
        P.setColor(col);
        gc.drawLine(X(x1), Y(y1), X(x2), Y(y2), P);
        P.setStyle(Paint.Style.FILL);
    }

    private static void eyes(float y, float dx, float rad) {
        cir(-dx, y, rad, INK);
        cir(dx, y, rad, INK);
        cir(-dx + rad * 0.3f, y - rad * 0.3f, rad * 0.32f, WHITE);
        cir(dx + rad * 0.3f, y - rad * 0.3f, rad * 0.32f, WHITE);
    }

    private static void smile(float y, float w) {
        arcS(0, y, w, w * 0.8f, 25, 130, 0.055f, INK);
    }

    private static void cheeks(float y, float dx) {
        cir(-dx, y, 0.09f, 0x66FF5252);
        cir(dx, y, 0.09f, 0x66FF5252);
    }

    private static void body(int col) {
        ovl(0, 1.12f, 0.88f, 0.56f, col);
    }

    private static void head(int skin) {
        cir(-0.5f, 0.05f, 0.09f, skin);
        cir(0.5f, 0.05f, 0.09f, skin);
        rct(-0.14f, 0.4f, 0.14f, 0.75f, skin);
        ovl(0, 0.02f, 0.5f, 0.58f, skin);
    }

    private static void character(int id) {
        int S1 = 0xFFFFCCA0, S2 = 0xFFFFD6B8, S3 = 0xFFF1C27D, S4 = 0xFFF0C8A0;
        switch (id) {
            case 12: // Тато
                cir(0, 0, 1.1f, 0xFF90CAF9);
                body(0xFF1976D2);
                head(S1);
                arcF(0, -0.1f, 0.54f, 0.56f, 180, 180, 0xFF4E342E);
                arcF(0, 0.1f, 0.5f, 0.5f, 10, 160, 0x30000000);
                eyes(-0.03f, 0.2f, 0.07f);
                rrc(-0.24f, 0.14f, 0.24f, 0.23f, 0.05f, 0xFF4E342E);
                arcS(0, 0.27f, 0.15f, 0.1f, 20, 140, 0.045f, INK);
                break;
            case 13: // Мама
                cir(0, 0, 1.1f, 0xFFF8BBD0);
                ovl(0, 0.25f, 0.7f, 0.88f, 0xFF6D4C41);
                body(0xFFE91E63);
                head(S2);
                arcF(0, -0.08f, 0.54f, 0.52f, 180, 180, 0xFF6D4C41);
                ovl(-0.28f, -0.2f, 0.3f, 0.18f, 0xFF6D4C41);
                eyes(0.0f, 0.2f, 0.07f);
                lin(-0.3f, -0.08f, -0.24f, -0.04f, 0.03f, INK);
                lin(0.3f, -0.08f, 0.24f, -0.04f, 0.03f, INK);
                arcS(0, 0.22f, 0.15f, 0.1f, 20, 140, 0.06f, 0xFFD32F2F);
                cheeks(0.14f, 0.3f);
                break;
            case 14: // Син
                cir(0, 0, 1.1f, 0xFFA5D6A7);
                body(0xFFFFC107);
                head(S3);
                arcF(0, -0.1f, 0.52f, 0.5f, 180, 180, 0xFF3E2723);
                for (int i = 0; i < 4; i++) {
                    float x = -0.36f + i * 0.24f;
                    tri(x - 0.14f, -0.36f, x, -0.82f, x + 0.14f, -0.36f, 0xFF3E2723);
                }
                eyes(0.0f, 0.2f, 0.08f);
                cir(-0.14f, 0.12f, 0.025f, 0xFFB5651D);
                cir(0.0f, 0.14f, 0.025f, 0xFFB5651D);
                cir(0.14f, 0.12f, 0.025f, 0xFFB5651D);
                arcF(0, 0.2f, 0.2f, 0.16f, 0, 180, 0xFFB71C1C);
                arcF(0, 0.2f, 0.2f, 0.06f, 0, 180, WHITE);
                break;
            case 15: // Донька
                cir(0, 0, 1.1f, 0xFFD1C4E9);
                cir(-0.62f, 0.12f, 0.22f, 0xFFFF8F00);
                cir(0.62f, 0.12f, 0.22f, 0xFFFF8F00);
                body(0xFFEC407A);
                head(S2);
                arcF(0, -0.1f, 0.54f, 0.54f, 180, 180, 0xFFFF8F00);
                tri(-0.5f, -0.28f, -0.72f, -0.42f, -0.72f, -0.14f, 0xFFE91E63);
                tri(-0.5f, -0.28f, -0.28f, -0.42f, -0.28f, -0.14f, 0xFFE91E63);
                tri(0.5f, -0.28f, 0.72f, -0.42f, 0.72f, -0.14f, 0xFFE91E63);
                tri(0.5f, -0.28f, 0.28f, -0.42f, 0.28f, -0.14f, 0xFFE91E63);
                eyes(0.02f, 0.2f, 0.08f);
                smile(0.2f, 0.17f);
                cheeks(0.16f, 0.3f);
                break;
            case 16: // Дід
                cir(0, 0, 1.1f, 0xFFCFD8DC);
                body(0xFF795548);
                head(S4);
                ovl(-0.5f, 0.0f, 0.12f, 0.22f, 0xFFF5F5F5);
                ovl(0.5f, 0.0f, 0.12f, 0.22f, 0xFFF5F5F5);
                lin(-0.32f, -0.15f, -0.1f, -0.18f, 0.05f, 0xFFF5F5F5);
                lin(0.32f, -0.15f, 0.1f, -0.18f, 0.05f, 0xFFF5F5F5);
                eyes(-0.02f, 0.2f, 0.05f);
                ring(-0.2f, -0.02f, 0.14f, 0.03f, 0xFF455A64);
                ring(0.2f, -0.02f, 0.14f, 0.03f, 0xFF455A64);
                lin(-0.06f, -0.02f, 0.06f, -0.02f, 0.03f, 0xFF455A64);
                cir(0, 0.1f, 0.07f, 0xFFE8A98A);
                ovl(-0.13f, 0.22f, 0.17f, 0.08f, 0xFFF5F5F5);
                ovl(0.13f, 0.22f, 0.17f, 0.08f, 0xFFF5F5F5);
                break;
            case 17: // Баба
                cir(0, 0, 1.1f, 0xFFFFCCBC);
                cir(0, -0.72f, 0.24f, 0xFFBDBDBD);
                body(0xFF8E24AA);
                head(S4);
                arcF(0, -0.08f, 0.54f, 0.54f, 180, 180, 0xFFBDBDBD);
                eyes(0.0f, 0.2f, 0.05f);
                ring(-0.2f, 0.0f, 0.14f, 0.03f, 0xFFAD1457);
                ring(0.2f, 0.0f, 0.14f, 0.03f, 0xFFAD1457);
                lin(-0.06f, 0.0f, 0.06f, 0.0f, 0.03f, 0xFFAD1457);
                cheeks(0.16f, 0.32f);
                smile(0.2f, 0.17f);
                break;
            case 18: { // Малюк
                cir(0, 0, 1.1f, 0xFFFFF59D);
                body(0xFF81D4FA);
                ovl(0, 1.05f, 0.3f, 0.25f, WHITE);
                float og = gr, oy = gy;
                gr = og * 0.8f;
                gy = oy - 0.04f * og;
                rct(-0.14f, 0.4f, 0.14f, 0.85f, S2);
                cir(-0.62f, 0.12f, 0.1f, S2);
                cir(0.62f, 0.12f, 0.1f, S2);
                ovl(0, 0.1f, 0.62f, 0.68f, S2);
                arcS(0, -0.62f, 0.14f, 0.2f, 180, 200, 0.07f, 0xFF6D4C41);
                eyes(0.06f, 0.22f, 0.11f);
                cheeks(0.3f, 0.36f);
                ovl(0, 0.4f, 0.08f, 0.06f, 0xFFD84315);
                gr = og;
                gy = oy;
                break;
            }
            case 19: // Герой у плащі
                cir(0, 0, 1.1f, 0xFF1565C0);
                tri(-1.0f, 1.0f, -0.1f, -0.1f, 1.0f, 1.0f, 0xFFC62828);
                body(0xFF283593);
                cir(0, 0.95f, 0.2f, 0xFFFFD600);
                head(S1);
                arcF(0, -0.1f, 0.54f, 0.54f, 180, 180, 0xFF212121);
                rrc(-0.5f, -0.14f, 0.5f, 0.1f, 0.08f, 0xFF1A237E);
                ovl(-0.2f, -0.02f, 0.11f, 0.07f, WHITE);
                ovl(0.2f, -0.02f, 0.11f, 0.07f, WHITE);
                smile(0.22f, 0.2f);
                break;
            case 20: // Герой у шоломі
                cir(0, 0, 1.1f, 0xFFB2EBF2);
                body(0xFF00838F);
                rct(-0.14f, 0.4f, 0.14f, 0.75f, 0xFF006064);
                ovl(0, 0.0f, 0.56f, 0.64f, 0xFF00ACC1);
                tri(0, -0.78f, 0.1f, -0.4f, -0.1f, -0.4f, 0xFF00838F);
                rrc(-0.44f, -0.16f, 0.44f, 0.1f, 0.07f, 0xFFFFEB3B);
                lin(-0.3f, -0.08f, -0.1f, -0.1f, 0.04f, WHITE);
                arcF(0, 0.2f, 0.42f, 0.36f, 0, 180, 0xFF0097A7);
                break;
            case 21: // Героїня
                cir(0, 0, 1.1f, 0xFFFFE0B2);
                tri(-1.0f, 1.0f, 0.0f, 0.0f, 1.0f, 1.0f, 0xFFAD1457);
                body(0xFFEC407A);
                head(S2);
                arcF(0, -0.1f, 0.54f, 0.54f, 180, 180, 0xFF6A1B9A);
                ovl(-0.3f, -0.22f, 0.3f, 0.16f, 0xFF6A1B9A);
                rrc(-0.5f, -0.1f, 0.5f, 0.1f, 0.08f, 0xFFAD1457);
                ovl(-0.2f, 0.0f, 0.1f, 0.06f, WHITE);
                ovl(0.2f, 0.0f, 0.1f, 0.06f, WHITE);
                arcS(0, 0.22f, 0.15f, 0.1f, 20, 140, 0.06f, 0xFFD32F2F);
                break;
            case 22: // Герой з блискавкою
                cir(0, 0, 1.1f, 0xFFFFF59D);
                body(0xFFFF6F00);
                PATH.reset();
                PATH.moveTo(X(0.08f), Y(0.78f));
                PATH.lineTo(X(-0.14f), Y(1.02f));
                PATH.lineTo(X(0.0f), Y(1.02f));
                PATH.lineTo(X(-0.1f), Y(1.3f));
                PATH.lineTo(X(0.16f), Y(0.98f));
                PATH.lineTo(X(0.02f), Y(0.98f));
                PATH.close();
                P.setStyle(Paint.Style.FILL);
                P.setColor(0xFFFFEB3B);
                gc.drawPath(PATH, P);
                head(S3);
                ovl(0, -0.08f, 0.56f, 0.5f, 0xFFE65100);
                ovl(0, 0.12f, 0.46f, 0.4f, S3);
                rrc(-0.46f, -0.14f, 0.46f, 0.08f, 0.08f, 0xFFE65100);
                ovl(-0.2f, -0.03f, 0.11f, 0.06f, WHITE);
                ovl(0.2f, -0.03f, 0.11f, 0.06f, WHITE);
                tri(0.55f, -0.5f, 0.85f, -0.65f, 0.7f, -0.35f, 0xFFFFEB3B);
                smile(0.24f, 0.18f);
                break;
            case 23: // Герой у капюшоні
                cir(0, 0, 1.1f, 0xFFC8E6C9);
                body(0xFF1B5E3A);
                ovl(0, 0.0f, 0.64f, 0.72f, 0xFF2E7D32);
                ovl(0, 0.1f, 0.42f, 0.5f, S4);
                rrc(-0.4f, -0.06f, 0.4f, 0.08f, 0.04f, 0xFF1B5E3A);
                ovl(-0.17f, 0.01f, 0.09f, 0.05f, WHITE);
                ovl(0.17f, 0.01f, 0.09f, 0.05f, WHITE);
                smile(0.3f, 0.15f);
                tri(0.0f, -0.82f, 0.25f, -0.55f, -0.25f, -0.55f, 0xFF2E7D32);
                break;
            case 24: // Герой в окулярах і шарфі
                cir(0, 0, 1.1f, 0xFFBBDEFB);
                body(0xFF5D4037);
                head(S1);
                rct(-0.5f, 0.55f, 0.5f, 0.75f, 0xFFC62828);
                tri(0.3f, 0.7f, 0.6f, 0.7f, 0.5f, 1.1f, 0xFFC62828);
                ovl(0, -0.12f, 0.58f, 0.46f, 0xFF6D4C41);
                rct(-0.58f, -0.16f, 0.58f, -0.06f, 0xFF3E2723);
                ring(-0.22f, -0.02f, 0.16f, 0.07f, 0xFF3E2723);
                ring(0.22f, -0.02f, 0.16f, 0.07f, 0xFF3E2723);
                cir(-0.22f, -0.02f, 0.13f, 0xAA4FC3F7);
                cir(0.22f, -0.02f, 0.13f, 0xAA4FC3F7);
                smile(0.26f, 0.18f);
                break;
            case 25: // Чарівник
                cir(0, 0, 1.1f, 0xFF311B92);
                body(0xFF4527A0);
                ovl(0, 0.5f, 0.5f, 0.6f, 0xFFEEEEEE);
                ovl(0, -0.02f, 0.44f, 0.46f, S4);
                ovl(0, -0.3f, 0.78f, 0.14f, 0xFF4527A0);
                tri(-0.55f, -0.3f, 0.0f, -1.3f, 0.55f, -0.3f, 0xFF5E35B1);
                cir(0.1f, -0.7f, 0.07f, 0xFFFFEB3B);
                cir(-0.15f, -0.5f, 0.05f, 0xFFFFEB3B);
                lin(-0.3f, -0.12f, -0.08f, -0.06f, 0.05f, 0xFFEEEEEE);
                lin(0.3f, -0.12f, 0.08f, -0.06f, 0.05f, 0xFFEEEEEE);
                eyes(0.0f, 0.19f, 0.05f);
                cir(0, 0.14f, 0.09f, 0xFFE8A98A);
                ovl(0, 0.3f, 0.2f, 0.07f, 0xFFEEEEEE);
                break;
            case 26: // Пірат
                cir(0, 0, 1.1f, 0xFF37474F);
                body(0xFFB71C1C);
                head(S3);
                arcF(0, 0.14f, 0.5f, 0.52f, 0, 180, 0xFF212121);
                ovl(0, 0.32f, 0.2f, 0.08f, S3);
                ovl(0, -0.22f, 0.78f, 0.17f, 0xFF212121);
                arcF(0, -0.2f, 0.5f, 0.55f, 180, 180, 0xFF212121);
                cir(0, -0.42f, 0.09f, WHITE);
                rct(-0.06f, -0.36f, 0.06f, -0.3f, WHITE);
                lin(-0.5f, -0.1f, 0.4f, -0.05f, 0.03f, 0xFF000000);
                ovl(-0.2f, 0.02f, 0.12f, 0.1f, 0xFF000000);
                cir(0.2f, 0.0f, 0.06f, INK);
                smile(0.3f, 0.15f);
                cir(0.07f, 0.32f, 0.03f, 0xFFFFD600);
                break;
            case 27: // Вампір
                cir(0, 0, 1.1f, 0xFF4A1942);
                tri(-1.0f, 1.0f, -0.55f, 0.1f, -0.15f, 1.0f, 0xFFB71C1C);
                tri(1.0f, 1.0f, 0.55f, 0.1f, 0.15f, 1.0f, 0xFFB71C1C);
                body(0xFF212121);
                head(0xFFE8E0F0);
                arcF(0, -0.1f, 0.54f, 0.54f, 180, 180, 0xFF111111);
                tri(-0.2f, -0.3f, 0.0f, -0.05f, 0.2f, -0.3f, 0xFFE8E0F0);
                eyes(0.04f, 0.2f, 0.07f);
                cir(-0.2f, 0.04f, 0.045f, 0xFFD50000);
                cir(0.2f, 0.04f, 0.045f, 0xFFD50000);
                lin(-0.3f, -0.06f, -0.1f, -0.02f, 0.04f, INK);
                lin(0.3f, -0.06f, 0.1f, -0.02f, 0.04f, INK);
                arcS(0, 0.26f, 0.15f, 0.08f, 20, 140, 0.04f, 0xFF8E0000);
                tri(-0.1f, 0.3f, -0.04f, 0.3f, -0.07f, 0.42f, WHITE);
                tri(0.1f, 0.3f, 0.04f, 0.3f, 0.07f, 0.42f, WHITE);
                break;
            case 28: // Привид
                cir(0, 0, 1.1f, 0xFF4A148C);
                ovl(0, -0.1f, 0.62f, 0.7f, WHITE);
                rct(-0.62f, -0.1f, 0.62f, 0.85f, WHITE);
                cir(-0.41f, 0.85f, 0.21f, WHITE);
                cir(0.0f, 0.9f, 0.21f, WHITE);
                cir(0.41f, 0.85f, 0.21f, WHITE);
                ovl(-0.22f, -0.12f, 0.1f, 0.16f, INK);
                ovl(0.22f, -0.12f, 0.1f, 0.16f, INK);
                ovl(0, 0.28f, 0.12f, 0.17f, INK);
                cheeks(0.12f, 0.38f);
                break;
            case 29: // Клоун
                cir(0, 0, 1.1f, 0xFFFFCDD2);
                cir(-0.55f, -0.05f, 0.3f, 0xFFFF6F00);
                cir(0.55f, -0.05f, 0.3f, 0xFFFF6F00);
                cir(0, -0.62f, 0.26f, 0xFFFF6F00);
                body(0xFF7B1FA2);
                cir(-0.4f, 1.0f, 0.08f, 0xFFFFEB3B);
                cir(0.4f, 1.0f, 0.08f, 0xFFFFEB3B);
                ovl(0, 0.02f, 0.5f, 0.56f, 0xFFFAFAFA);
                eyes(-0.06f, 0.2f, 0.06f);
                tri(-0.2f, -0.28f, -0.12f, -0.18f, -0.28f, -0.18f, 0xFF1E88E5);
                tri(0.2f, -0.28f, 0.12f, -0.18f, 0.28f, -0.18f, 0xFF1E88E5);
                cir(0, 0.1f, 0.14f, 0xFFD50000);
                arcS(0, 0.2f, 0.3f, 0.22f, 15, 150, 0.09f, 0xFFD50000);
                break;
            case 30: // Робот-диктатор
                cir(0, 0, 1.1f, 0xFF212121);
                body(0xFF455A64);
                cir(-0.4f, 1.0f, 0.06f, 0xFFD50000);
                cir(0.4f, 1.0f, 0.06f, 0xFFD50000);
                rct(-0.1f, 0.5f, 0.1f, 0.75f, 0xFF37474F);
                rrc(-0.55f, -0.5f, 0.55f, 0.55f, 0.12f, 0xFF607D8B);
                lin(0, -0.5f, 0, -0.85f, 0.04f, 0xFF90A4AE);
                cir(0, -0.88f, 0.08f, 0xFFD50000);
                rrc(-0.45f, -0.22f, 0.45f, 0.06f, 0.05f, 0xFF000000);
                cir(-0.2f, -0.08f, 0.07f, 0xFFFF1744);
                cir(0.2f, -0.08f, 0.07f, 0xFFFF1744);
                lin(-0.45f, -0.3f, -0.1f, -0.22f, 0.05f, 0xFF263238);
                lin(0.45f, -0.3f, 0.1f, -0.22f, 0.05f, 0xFF263238);
                for (int i = 0; i < 5; i++) lin(-0.25f + i * 0.125f, 0.22f, -0.25f + i * 0.125f, 0.42f, 0.035f, 0xFF263238);
                break;
            case 31: // Дружній робот
                cir(0, 0, 1.1f, 0xFFB3E5FC);
                body(0xFF78909C);
                rct(-0.62f, -0.1f, -0.5f, 0.2f, 0xFF90A4AE);
                rct(0.5f, -0.1f, 0.62f, 0.2f, 0xFF90A4AE);
                lin(0, -0.45f, 0, -0.8f, 0.04f, 0xFF607D8B);
                cir(0, -0.85f, 0.09f, 0xFFFFEB3B);
                rrc(-0.52f, -0.45f, 0.52f, 0.55f, 0.1f, 0xFFB0BEC5);
                cir(-0.22f, -0.05f, 0.17f, WHITE);
                cir(0.22f, -0.05f, 0.17f, WHITE);
                cir(-0.22f, -0.05f, 0.09f, 0xFF1E88E5);
                cir(0.22f, -0.05f, 0.09f, 0xFF1E88E5);
                rrc(-0.25f, 0.25f, 0.25f, 0.4f, 0.04f, 0xFF455A64);
                lin(-0.12f, 0.25f, -0.12f, 0.4f, 0.025f, 0xFFB0BEC5);
                lin(0.0f, 0.25f, 0.0f, 0.4f, 0.025f, 0xFFB0BEC5);
                lin(0.12f, 0.25f, 0.12f, 0.4f, 0.025f, 0xFFB0BEC5);
                break;
            case 32: // Інопланетянин
                cir(0, 0, 1.1f, 0xFF1A237E);
                if (!noBg) {
                    cir(-0.7f, -0.7f, 0.04f, WHITE);
                    cir(0.75f, -0.4f, 0.03f, WHITE);
                }
                body(0xFF9E9E9E);
                lin(-0.25f, -0.55f, -0.45f, -0.9f, 0.04f, 0xFF7CB342);
                lin(0.25f, -0.55f, 0.45f, -0.9f, 0.04f, 0xFF7CB342);
                cir(-0.47f, -0.92f, 0.07f, 0xFFFFEB3B);
                cir(0.47f, -0.92f, 0.07f, 0xFFFFEB3B);
                rct(-0.12f, 0.5f, 0.12f, 0.8f, 0xFF8BC34A);
                ovl(0, 0.0f, 0.55f, 0.7f, 0xFF8BC34A);
                ovl(-0.22f, -0.02f, 0.14f, 0.23f, 0xFF000000);
                ovl(0.22f, -0.02f, 0.14f, 0.23f, 0xFF000000);
                cir(-0.18f, -0.1f, 0.04f, WHITE);
                cir(0.26f, -0.1f, 0.04f, WHITE);
                smile(0.38f, 0.1f);
                break;
            case 33: // Єдиноріг
                cir(0, 0, 1.1f, 0xFFF8BBD0);
                cir(-0.55f, -0.1f, 0.32f, 0xFFF06292);
                cir(0.55f, -0.1f, 0.32f, 0xFFBA68C8);
                cir(-0.5f, 0.35f, 0.25f, 0xFFBA68C8);
                cir(0.5f, 0.35f, 0.25f, 0xFFF06292);
                tri(-0.42f, -0.35f, -0.28f, -0.8f, -0.12f, -0.4f, WHITE);
                tri(0.42f, -0.35f, 0.28f, -0.8f, 0.12f, -0.4f, WHITE);
                ovl(0, 0.12f, 0.45f, 0.62f, WHITE);
                tri(-0.1f, -0.42f, 0.1f, -0.42f, 0.0f, -1.15f, 0xFFFFC107);
                lin(-0.07f, -0.62f, 0.07f, -0.6f, 0.03f, 0xFFFF8F00);
                lin(-0.05f, -0.82f, 0.05f, -0.8f, 0.03f, 0xFFFF8F00);
                ovl(0, 0.5f, 0.32f, 0.26f, 0xFFFFE4EC);
                cir(-0.1f, 0.52f, 0.03f, 0xFFAD1457);
                cir(0.1f, 0.52f, 0.03f, 0xFFAD1457);
                cir(-0.2f, 0.02f, 0.09f, INK);
                cir(0.2f, 0.02f, 0.09f, INK);
                cir(-0.17f, -0.01f, 0.03f, WHITE);
                cir(0.23f, -0.01f, 0.03f, WHITE);
                cheeks(0.25f, 0.32f);
                break;
            case 34: // Дракончик
                cir(0, 0, 1.1f, 0xFFFFE0B2);
                tri(-0.35f, -0.4f, -0.5f, -0.9f, -0.15f, -0.5f, 0xFFFFA726);
                tri(0.35f, -0.4f, 0.5f, -0.9f, 0.15f, -0.5f, 0xFFFFA726);
                tri(-0.62f, 0.0f, -0.95f, -0.2f, -0.6f, 0.3f, 0xFF43A047);
                tri(0.62f, 0.0f, 0.95f, -0.2f, 0.6f, 0.3f, 0xFF43A047);
                for (int i = 0; i < 3; i++) tri(-0.12f + i * 0.12f, -0.5f, -0.06f + i * 0.12f, -0.72f, 0.0f + i * 0.12f, -0.5f, 0xFFFFA726);
                ovl(0, 0.05f, 0.56f, 0.58f, 0xFF66BB6A);
                ovl(0, 0.4f, 0.36f, 0.26f, 0xFF9CCC65);
                cir(-0.12f, 0.38f, 0.04f, 0xFF33691E);
                cir(0.12f, 0.38f, 0.04f, 0xFF33691E);
                cir(-0.22f, -0.1f, 0.14f, WHITE);
                cir(0.22f, -0.1f, 0.14f, WHITE);
                cir(-0.22f, -0.08f, 0.07f, INK);
                cir(0.22f, -0.08f, 0.07f, INK);
                smile(0.5f, 0.14f);
                break;
            case 35: // Фея
                cir(0, 0, 1.1f, 0xFFB39DDB);
                ovl(-0.78f, -0.15f, 0.3f, 0.55f, 0xAAE1F5FE);
                ovl(0.78f, -0.15f, 0.3f, 0.55f, 0xAAE1F5FE);
                body(0xFFF06292);
                head(S2);
                cir(0, -0.66f, 0.22f, 0xFFFFC107);
                arcF(0, -0.1f, 0.54f, 0.54f, 180, 180, 0xFFFFC107);
                lin(0.1f, -0.9f, 0.2f, -0.78f, 0.04f, 0xFFFFEB3B);
                tri(-0.12f, -0.9f, 0.12f, -0.9f, 0.0f, -1.05f, 0xFFFFEB3B);
                eyes(0.02f, 0.2f, 0.08f);
                smile(0.2f, 0.16f);
                cheeks(0.16f, 0.3f);
                if (!noBg) {
                    cir(0.85f, 0.35f, 0.07f, 0xFFFFEB3B);
                    cir(-0.85f, 0.5f, 0.05f, WHITE);
                }
                break;
            default: // 36 Монстрик
                cir(0, 0, 1.1f, 0xFFD1C4E9);
                for (int i = 0; i < 12; i++) {
                    double ang = i * Math.PI / 6.0;
                    cir((float) Math.cos(ang) * 0.68f, (float) Math.sin(ang) * 0.68f, 0.2f, 0xFF42A5F5);
                }
                cir(0, 0, 0.7f, 0xFF42A5F5);
                tri(-0.4f, -0.55f, -0.55f, -0.95f, -0.2f, -0.65f, WHITE);
                tri(0.4f, -0.55f, 0.55f, -0.95f, 0.2f, -0.65f, WHITE);
                cir(-0.24f, -0.12f, 0.2f, WHITE);
                cir(0.24f, -0.12f, 0.2f, WHITE);
                cir(-0.22f, -0.1f, 0.09f, INK);
                cir(0.22f, -0.1f, 0.09f, INK);
                ovl(0, 0.34f, 0.32f, 0.2f, 0xFF4A148C);
                tri(-0.22f, 0.2f, -0.1f, 0.2f, -0.16f, 0.32f, WHITE);
                tri(0.22f, 0.2f, 0.1f, 0.2f, 0.16f, 0.32f, WHITE);
                break;
        }
    }

    public static class AvatarView extends View {
        private int avatar;
        private String photo = "-";
        private boolean selected;

        public AvatarView(android.content.Context ctx) {
            super(ctx);
        }

        public void set(int avatar, String photo, boolean selected) {
            this.avatar = avatar;
            this.photo = photo;
            this.selected = selected;
            invalidate();
        }

        @Override
        protected void onDraw(Canvas c) {
            float r = Math.min(getWidth(), getHeight()) / 2f * 0.88f;
            float cx = getWidth() / 2f, cy = getHeight() / 2f;
            if (selected) {
                P.setStyle(Paint.Style.STROKE);
                P.setStrokeWidth(r * 0.14f);
                P.setColor(0xFFFFD54F);
                c.drawCircle(cx, cy, r * 1.08f, P);
                P.setStyle(Paint.Style.FILL);
            }
            Avatars.draw(c, cx, cy, r * 0.8f, avatar, Avatars.photo(photo));
        }
    }
}
