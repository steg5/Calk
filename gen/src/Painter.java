package com.example.gryntsivgames.sea;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;

public class Painter {
    public static final String[] THEMES = {"Кораблі", "Машини", "Поїзди", "Жуки", "Лего"};
    private static final int[] CAR = {0xFFEC407A, 0xFF1E88E5, 0xFFFDD835, 0xFF43A047, 0xFF8E24AA, 0xFFFB8C00};
    private static final int[] BRICK = {0xFFEC407A, 0xFF1E88E5, 0xFFFDD835, 0xFF43A047, 0xFFFB8C00, 0xFF8E24AA};
    private static final Path path = new Path();
    private static final RectF rf = new RectF();

    private static void fill(Canvas c, Paint p, int col) {
        p.setStyle(Paint.Style.FILL);
        p.setColor(col);
    }

    private static void rr(Canvas c, Paint p, float l, float t, float r, float b, float rad, int col) {
        fill(c, p, col);
        rf.set(l, t, r, b);
        c.drawRoundRect(rf, rad, rad, p);
    }

    private static void circle(Canvas c, Paint p, float x, float y, float r, int col) {
        fill(c, p, col);
        c.drawCircle(x, y, r, p);
    }

    private static int shade(int col, float f) {
        int r = Math.min(255, (int) (((col >> 16) & 255) * f));
        int g = Math.min(255, (int) (((col >> 8) & 255) * f));
        int b = Math.min(255, (int) ((col & 255) * f));
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }

    private static void line(Canvas c, Paint p, float x1, float y1, float x2, float y2, float w, int col) {
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(w);
        p.setColor(col);
        c.drawLine(x1, y1, x2, y2, p);
        p.setStyle(Paint.Style.FILL);
    }

    private static void oval(Canvas c, Paint p, float l, float t, float r, float b, int col) {
        fill(c, p, col);
        rf.set(l, t, r, b);
        c.drawOval(rf, p);
    }

    // малює фігуру довжини len у клітинках; (x,y) — лівий верхній кут, cell — розмір клітинки
    public static void draw(Canvas c, Paint p, int theme, float x, float y, float cell, int len, boolean horiz, int variant) {
        c.save();
        if (horiz) {
            c.translate(x, y);
        } else {
            c.translate(x + cell, y);
            c.rotate(90, 0, 0);
        }
        drawH(c, p, theme, cell, len, variant);
        c.restore();
    }

    private static void drawH(Canvas c, Paint p, int theme, float s, int len, int v) {
        float w = len * s;
        switch (theme) {
            case 1:
                for (int k = 0; k < len; k++) car(c, p, k * s, s, CAR[(v + k) % CAR.length], (v + k) % 3);
                break;
            case 2:
                train(c, p, s, len, v);
                break;
            case 3:
                if (len == 1) ladybug(c, p, s);
                else if (len == 2) bee(c, p, s);
                else if (len == 3) caterpillar(c, p, s, len, v);
                else centipede(c, p, s, len);
                break;
            case 4:
                lego(c, p, w, s, len, BRICK[v % BRICK.length]);
                break;
            default:
                ship(c, p, w, s, len);
        }
    }

    // ---------- кораблі ----------
    private static void hullPath(float w, float s) {
        path.reset();
        path.moveTo(0.04f * s, 0.3f * s);
        path.quadTo(0.04f * s, 0.14f * s, 0.22f * s, 0.14f * s);
        path.lineTo(w - 0.85f * s, 0.14f * s);
        path.quadTo(w - 0.28f * s, 0.18f * s, w - 0.03f * s, 0.5f * s);
        path.quadTo(w - 0.28f * s, 0.82f * s, w - 0.85f * s, 0.86f * s);
        path.lineTo(0.22f * s, 0.86f * s);
        path.quadTo(0.04f * s, 0.86f * s, 0.04f * s, 0.7f * s);
        path.close();
    }

    private static void turret(Canvas c, Paint p, float x, float y, float r, boolean fwd, float s) {
        float d = fwd ? 1 : -1;
        line(c, p, x, y - r * 0.35f, x + d * r * 2.2f, y - r * 0.35f, r * 0.45f, 0xFF263238);
        line(c, p, x, y + r * 0.35f, x + d * r * 2.2f, y + r * 0.35f, r * 0.45f, 0xFF263238);
        circle(c, p, x, y, r, 0xFF37474F);
        circle(c, p, x, y, r * 0.78f, 0xFF607D8B);
        circle(c, p, x - r * 0.2f, y - r * 0.25f, r * 0.3f, 0x55FFFFFF);
    }

    private static void ship(Canvas c, Paint p, float w, float s, int len) {
        hullPath(w, s);
        fill(c, p, 0xFF455A64);
        c.drawPath(path, p);
        c.save();
        c.translate(w / 2f, s / 2f);
        c.scale((w - 0.34f * s) / w, 0.66f);
        c.translate(-w / 2f, -s / 2f);
        fill(c, p, 0xFF90A4AE);
        c.drawPath(path, p);
        c.restore();
        // дерев'яна палуба
        for (float x = 0.3f * s; x < w - 1.0f * s; x += 0.22f * s) {
            line(c, p, x, 0.3f * s, x, 0.7f * s, s * 0.015f, 0x33263238);
        }
        hullPath(w, s);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(s * 0.05f);
        p.setColor(0xFF1C2A30);
        c.drawPath(path, p);
        p.setStyle(Paint.Style.FILL);
        if (len == 1) {
            rr(c, p, w * 0.3f, s * 0.3f, w * 0.3f + s * 0.34f, s * 0.7f, s * 0.08f, 0xFFECEFF1);
            rr(c, p, w * 0.3f + s * 0.17f, s * 0.34f, w * 0.3f + s * 0.3f, s * 0.66f, s * 0.04f, 0xFF4FC3F7);
            circle(c, p, w * 0.78f, s * 0.5f, s * 0.07f, 0xFFFFB300);
            return;
        }
        float bc = len == 2 ? 0.8f * s : (len == 3 ? 1.3f * s : 1.95f * s);
        float bw = len == 2 ? 0.55f * s : 0.9f * s;
        rr(c, p, bc - bw / 2f, s * 0.28f, bc + bw / 2f, s * 0.72f, s * 0.07f, 0xFFB0BEC5);
        rr(c, p, bc - bw / 2f + s * 0.04f, s * 0.32f, bc + bw / 2f - s * 0.04f, s * 0.68f, s * 0.05f, 0xFFECEFF1);
        rr(c, p, bc + bw * 0.1f, s * 0.36f, bc + bw / 2f - s * 0.07f, s * 0.64f, s * 0.03f, 0xFF37474F);
        if (len >= 3) {
            oval(c, p, bc - bw * 0.35f, s * 0.38f, bc - bw * 0.02f, s * 0.62f, 0xFFD84315);
            oval(c, p, bc - bw * 0.3f, s * 0.42f, bc - bw * 0.07f, s * 0.58f, 0xFF263238);
        }
        circle(c, p, bc + bw * 0.3f, s * 0.5f, s * 0.05f, 0xFF90CAF9);
        if (len >= 3) turret(c, p, w - 1.05f * s, s * 0.5f, s * 0.15f, true, s);
        else turret(c, p, w - 0.62f * s, s * 0.5f, s * 0.11f, true, s);
        if (len >= 3) turret(c, p, 0.55f * s, s * 0.5f, s * 0.15f, false, s);
        if (len == 4) turret(c, p, w - 1.65f * s, s * 0.5f, s * 0.12f, true, s);
        circle(c, p, w - 0.14f * s, s * 0.5f, s * 0.04f, 0xFFFFB300);
    }

    // ---------- машини ----------
    private static void car(Canvas c, Paint p, float x, float s, int col, int type) {
        // колеса
        rr(c, p, x + s * 0.18f, s * 0.1f, x + s * 0.38f, s * 0.24f, s * 0.04f, 0xFF212121);
        rr(c, p, x + s * 0.66f, s * 0.1f, x + s * 0.86f, s * 0.24f, s * 0.04f, 0xFF212121);
        rr(c, p, x + s * 0.18f, s * 0.76f, x + s * 0.38f, s * 0.9f, s * 0.04f, 0xFF212121);
        rr(c, p, x + s * 0.66f, s * 0.76f, x + s * 0.86f, s * 0.9f, s * 0.04f, 0xFF212121);
        // кузов
        rr(c, p, x + s * 0.06f, s * 0.2f, x + s * 0.95f, s * 0.8f, s * 0.17f, shade(col, 0.7f));
        rr(c, p, x + s * 0.08f, s * 0.21f, x + s * 0.93f, s * 0.76f, s * 0.15f, col);
        rr(c, p, x + s * 0.12f, s * 0.23f, x + s * 0.9f, s * 0.38f, s * 0.08f, shade(col, 1.25f));
        if (type == 2) {
            // пікап: кузов з вантажем
            rr(c, p, x + s * 0.1f, s * 0.26f, x + s * 0.46f, s * 0.74f, s * 0.04f, shade(col, 0.6f));
            for (int k = 0; k < 3; k++) {
                line(c, p, x + s * (0.18f + k * 0.11f), s * 0.28f, x + s * (0.18f + k * 0.11f), s * 0.72f, s * 0.025f, shade(col, 0.82f));
            }
            rr(c, p, x + s * 0.5f, s * 0.27f, x + s * 0.66f, s * 0.73f, s * 0.05f, shade(col, 1.1f));
            rr(c, p, x + s * 0.64f, s * 0.3f, x + s * 0.76f, s * 0.7f, s * 0.04f, 0xFF1F3A52);
        } else {
            rr(c, p, x + s * 0.24f, s * 0.28f, x + s * 0.34f, s * 0.72f, s * 0.03f, 0xFF1F3A52);
            rr(c, p, x + s * 0.34f, s * 0.26f, x + s * 0.58f, s * 0.74f, s * 0.07f, shade(col, 1.12f));
            rr(c, p, x + s * 0.58f, s * 0.29f, x + s * 0.72f, s * 0.71f, s * 0.04f, 0xFF2B5A80);
            rr(c, p, x + s * 0.61f, s * 0.32f, x + s * 0.69f, s * 0.46f, s * 0.02f, 0x66FFFFFF);
        }
        if (type == 1) {
            rr(c, p, x + s * 0.1f, s * 0.46f, x + s * 0.93f, s * 0.54f, 0, 0xCCFFFFFF);
        }
        rr(c, p, x + s * 0.6f, s * 0.12f, x + s * 0.7f, s * 0.2f, s * 0.03f, shade(col, 0.7f));
        rr(c, p, x + s * 0.6f, s * 0.8f, x + s * 0.7f, s * 0.88f, s * 0.03f, shade(col, 0.7f));
        oval(c, p, x + s * 0.88f, s * 0.24f, x + s * 0.96f, s * 0.38f, 0xFFFFF59D);
        oval(c, p, x + s * 0.88f, s * 0.62f, x + s * 0.96f, s * 0.76f, 0xFFFFF59D);
        rr(c, p, x + s * 0.06f, s * 0.26f, x + s * 0.1f, s * 0.38f, s * 0.01f, 0xFFFFB300);
        rr(c, p, x + s * 0.06f, s * 0.62f, x + s * 0.1f, s * 0.74f, s * 0.01f, 0xFFFFB300);
    }

    // ---------- поїзди ----------
    private static void train(Canvas c, Paint p, float s, int len, int v) {
        float w = len * s;
        // шпали й рейки
        for (float x = 0.1f * s; x < w; x += 0.3f * s) {
            rr(c, p, x, s * 0.06f, x + s * 0.09f, s * 0.94f, 0, 0xFF5D4037);
        }
        rr(c, p, 0, s * 0.16f, w, s * 0.21f, 0, 0xFF90A4AE);
        rr(c, p, 0, s * 0.79f, w, s * 0.84f, 0, 0xFF90A4AE);
        for (int k = 0; k < len; k++) {
            float x = k * s;
            if (k == len - 1) {
                // локомотив
                rr(c, p, x + s * 0.04f, s * 0.2f, x + s * 0.97f, s * 0.8f, s * 0.12f, 0xFF3A0F5C);
                rr(c, p, x + s * 0.06f, s * 0.22f, x + s * 0.95f, s * 0.78f, s * 0.1f, 0xFF7B1FA2);
                rr(c, p, x + s * 0.06f, s * 0.22f, x + s * 0.4f, s * 0.78f, s * 0.07f, 0xFF6A1B9A);
                rr(c, p, x + s * 0.1f, s * 0.28f, x + s * 0.18f, s * 0.72f, s * 0.02f, 0xFF263238);
                rr(c, p, x + s * 0.3f, s * 0.3f, x + s * 0.36f, s * 0.7f, s * 0.02f, 0xFF263238);
                rr(c, p, x + s * 0.42f, s * 0.3f, x + s * 0.93f, s * 0.7f, s * 0.15f, 0xFF455A64);
                rr(c, p, x + s * 0.46f, s * 0.34f, x + s * 0.9f, s * 0.44f, s * 0.05f, 0xFF78909C);
                for (int j = 0; j < 3; j++) line(c, p, x + s * (0.56f + j * 0.12f), s * 0.3f, x + s * (0.56f + j * 0.12f), s * 0.7f, s * 0.02f, 0xFF263238);
                circle(c, p, x + s * 0.74f, s * 0.54f, s * 0.11f, 0xFF212121);
                circle(c, p, x + s * 0.74f, s * 0.54f, s * 0.07f, 0xFF616161);
                circle(c, p, x + s * 0.96f, s * 0.36f, s * 0.045f, 0xFFFFF59D);
                circle(c, p, x + s * 0.96f, s * 0.64f, s * 0.045f, 0xFFFFF59D);
            } else {
                int col = BRICK[(v + k) % BRICK.length];
                rr(c, p, x + s * 0.06f, s * 0.2f, x + s * 0.96f, s * 0.8f, s * 0.06f, shade(col, 0.65f));
                rr(c, p, x + s * 0.08f, s * 0.22f, x + s * 0.94f, s * 0.78f, s * 0.05f, col);
                for (int j = 0; j < 5; j++) {
                    line(c, p, x + s * (0.2f + j * 0.15f), s * 0.24f, x + s * (0.2f + j * 0.15f), s * 0.76f, s * 0.025f, shade(col, 0.78f));
                }
                rr(c, p, x + s * 0.1f, s * 0.46f, x + s * 0.92f, s * 0.54f, 0, shade(col, 1.2f));
                rr(c, p, x - s * 0.04f, s * 0.47f, x + s * 0.08f, s * 0.53f, 0, 0xFF37474F);
            }
        }
    }

    // ---------- жуки ----------
    private static void caterpillar(Canvas c, Paint p, float s, int len, int v) {
        int base = v % 2 == 0 ? 0xFF66BB6A : 0xFFFFA726;
        for (int k = 0; k < len; k++) {
            float cx = k * s + s * 0.5f;
            line(c, p, cx - s * 0.1f, s * 0.3f, cx - s * 0.2f, s * 0.08f, s * 0.07f, 0xFF263238);
            line(c, p, cx + s * 0.1f, s * 0.3f, cx + s * 0.2f, s * 0.08f, s * 0.07f, 0xFF263238);
            line(c, p, cx - s * 0.1f, s * 0.7f, cx - s * 0.2f, s * 0.92f, s * 0.07f, 0xFF263238);
            line(c, p, cx + s * 0.1f, s * 0.7f, cx + s * 0.2f, s * 0.92f, s * 0.07f, 0xFF263238);
        }
        for (int k = 0; k < len - 1; k++) {
            float cx = k * s + s * 0.5f;
            int col = k % 2 == 0 ? base : shade(base, 0.82f);
            oval(c, p, cx - s * 0.5f, s * 0.2f, cx + s * 0.5f, s * 0.8f, shade(col, 0.6f));
            oval(c, p, cx - s * 0.47f, s * 0.2f, cx + s * 0.47f, s * 0.74f, col);
            oval(c, p, cx - s * 0.3f, s * 0.25f, cx + s * 0.1f, s * 0.42f, shade(col, 1.3f));
            circle(c, p, cx + s * 0.14f, s * 0.55f, s * 0.07f, shade(col, 0.6f));
            circle(c, p, cx - s * 0.12f, s * 0.6f, s * 0.05f, shade(col, 0.6f));
        }
        float hx = (len - 1) * s + s * 0.5f;
        circle(c, p, hx, s * 0.5f, s * 0.4f, 0xFF6A1B9A);
        circle(c, p, hx - s * 0.06f, s * 0.44f, s * 0.3f, 0xFFAB47BC);
        circle(c, p, hx + s * 0.1f, s * 0.34f, s * 0.11f, 0xFFFFFFFF);
        circle(c, p, hx + s * 0.1f, s * 0.66f, s * 0.11f, 0xFFFFFFFF);
        circle(c, p, hx + s * 0.13f, s * 0.34f, s * 0.055f, 0xFF212121);
        circle(c, p, hx + s * 0.13f, s * 0.66f, s * 0.055f, 0xFF212121);
        line(c, p, hx + s * 0.3f, s * 0.38f, hx + s * 0.34f, s * 0.62f, s * 0.04f, 0xFF212121);
        line(c, p, hx + s * 0.25f, s * 0.2f, hx + s * 0.46f, s * 0.08f, s * 0.04f, 0xFF212121);
        line(c, p, hx + s * 0.25f, s * 0.8f, hx + s * 0.46f, s * 0.92f, s * 0.04f, 0xFF212121);
        circle(c, p, hx + s * 0.46f, s * 0.08f, s * 0.05f, 0xFFFFD54F);
        circle(c, p, hx + s * 0.46f, s * 0.92f, s * 0.05f, 0xFFFFD54F);
    }

    private static void bee(Canvas c, Paint p, float s) {
        float w = 2 * s;
        // крильця
        oval(c, p, w * 0.30f, s * 0.0f, w * 0.62f, s * 0.42f, 0xAAE1F5FE);
        oval(c, p, w * 0.30f, s * 0.58f, w * 0.62f, s * 1.0f, 0xAAE1F5FE);
        // лапки
        for (int k = 0; k < 3; k++) {
            float x = w * (0.32f + k * 0.12f);
            line(c, p, x, s * 0.3f, x - s * 0.08f, s * 0.12f, s * 0.05f, 0xFF212121);
            line(c, p, x, s * 0.7f, x - s * 0.08f, s * 0.88f, s * 0.05f, 0xFF212121);
        }
        // тіло
        oval(c, p, w * 0.06f, s * 0.2f, w * 0.78f, s * 0.8f, 0xFF4E3A00);
        oval(c, p, w * 0.07f, s * 0.2f, w * 0.77f, s * 0.75f, 0xFFFFC107);
        for (int k = 0; k < 3; k++) {
            float x = w * (0.2f + k * 0.17f);
            rr(c, p, x, s * 0.22f, x + w * 0.07f, s * 0.76f, s * 0.03f, 0xFF212121);
        }
        oval(c, p, w * 0.1f, s * 0.24f, w * 0.4f, s * 0.38f, 0x66FFFFFF);
        // жало
        line(c, p, w * 0.06f, s * 0.5f, 0, s * 0.5f, s * 0.05f, 0xFF212121);
        // голова
        circle(c, p, w * 0.86f, s * 0.5f, s * 0.2f, 0xFF212121);
        circle(c, p, w * 0.89f, s * 0.42f, s * 0.06f, 0xFFFFFFFF);
        circle(c, p, w * 0.89f, s * 0.58f, s * 0.06f, 0xFFFFFFFF);
        line(c, p, w * 0.9f, s * 0.36f, w * 0.98f, s * 0.2f, s * 0.03f, 0xFF212121);
        line(c, p, w * 0.9f, s * 0.64f, w * 0.98f, s * 0.8f, s * 0.03f, 0xFF212121);
    }

    private static void centipede(Canvas c, Paint p, float s, int len) {
        int segs = len * 2;
        float step = s * 0.5f;
        for (int k = 0; k < segs; k++) {
            float cx = k * step + step * 0.5f;
            line(c, p, cx, s * 0.35f, cx - s * 0.06f, s * 0.04f, s * 0.045f, 0xFF4E342E);
            line(c, p, cx, s * 0.65f, cx - s * 0.06f, s * 0.96f, s * 0.045f, 0xFF4E342E);
        }
        for (int k = 0; k < segs - 1; k++) {
            float cx = k * step + step * 0.5f;
            int col = k % 2 == 0 ? 0xFFFF8F00 : 0xFFFFB74D;
            circle(c, p, cx, s * 0.5f, s * 0.27f, shade(col, 0.55f));
            circle(c, p, cx, s * 0.48f, s * 0.25f, col);
            circle(c, p, cx - s * 0.05f, s * 0.4f, s * 0.07f, 0x66FFFFFF);
        }
        float hx = (segs - 1) * step + step * 0.5f;
        circle(c, p, hx + s * 0.04f, s * 0.5f, s * 0.3f, 0xFF4A148C);
        circle(c, p, hx + s * 0.02f, s * 0.46f, s * 0.24f, 0xFF8E24AA);
        circle(c, p, hx + s * 0.1f, s * 0.38f, s * 0.07f, 0xFFFFFFFF);
        circle(c, p, hx + s * 0.1f, s * 0.62f, s * 0.07f, 0xFFFFFFFF);
        circle(c, p, hx + s * 0.12f, s * 0.38f, s * 0.035f, 0xFF212121);
        circle(c, p, hx + s * 0.12f, s * 0.62f, s * 0.035f, 0xFF212121);
        line(c, p, hx + s * 0.22f, s * 0.36f, hx + s * 0.34f, s * 0.12f, s * 0.035f, 0xFF212121);
        line(c, p, hx + s * 0.22f, s * 0.64f, hx + s * 0.34f, s * 0.88f, s * 0.035f, 0xFF212121);
    }

    private static void ladybug(Canvas c, Paint p, float s) {
        for (int k = 0; k < 3; k++) {
            float y = s * (0.3f + k * 0.2f);
            line(c, p, s * (0.38f + k * 0.08f), y, s * 0.1f, y + (k - 1) * s * 0.08f, s * 0.05f, 0xFF212121);
            line(c, p, s * (0.62f - k * 0.08f), y, s * 0.9f, y + (k - 1) * s * 0.08f, s * 0.05f, 0xFF212121);
        }
        oval(c, p, s * 0.12f, s * 0.16f, s * 0.8f, s * 0.84f, 0xFF4A148C);
        oval(c, p, s * 0.14f, s * 0.16f, s * 0.78f, s * 0.78f, 0xFFAB47BC);
        oval(c, p, s * 0.22f, s * 0.2f, s * 0.5f, s * 0.36f, 0x66FFFFFF);
        line(c, p, s * 0.2f, s * 0.5f, s * 0.74f, s * 0.5f, s * 0.03f, 0xFF212121);
        circle(c, p, s * 0.34f, s * 0.36f, s * 0.07f, 0xFF212121);
        circle(c, p, s * 0.34f, s * 0.64f, s * 0.07f, 0xFF212121);
        circle(c, p, s * 0.56f, s * 0.3f, s * 0.06f, 0xFF212121);
        circle(c, p, s * 0.56f, s * 0.7f, s * 0.06f, 0xFF212121);
        circle(c, p, s * 0.82f, s * 0.5f, s * 0.15f, 0xFF212121);
        circle(c, p, s * 0.86f, s * 0.43f, s * 0.04f, 0xFFFFFFFF);
        circle(c, p, s * 0.86f, s * 0.57f, s * 0.04f, 0xFFFFFFFF);
        line(c, p, s * 0.88f, s * 0.4f, s * 0.97f, s * 0.26f, s * 0.025f, 0xFF212121);
        line(c, p, s * 0.88f, s * 0.6f, s * 0.97f, s * 0.74f, s * 0.025f, 0xFF212121);
    }

    // ---------- лего ----------
    private static void lego(Canvas c, Paint p, float w, float s, int len, int col) {
        rr(c, p, s * 0.04f, s * 0.1f, w - s * 0.04f, s * 0.94f, s * 0.06f, shade(col, 0.55f));
        rr(c, p, s * 0.04f, s * 0.08f, w - s * 0.04f, s * 0.86f, s * 0.06f, col);
        rr(c, p, s * 0.07f, s * 0.1f, w - s * 0.07f, s * 0.22f, s * 0.04f, shade(col, 1.25f));
        rr(c, p, s * 0.04f, s * 0.76f, w - s * 0.04f, s * 0.86f, s * 0.05f, shade(col, 0.8f));
        for (int k = 0; k < len * 2; k++) {
            float cx = k * s * 0.5f + s * 0.25f;
            for (int j = 0; j < 2; j++) {
                float cy = s * (0.3f + j * 0.34f);
                circle(c, p, cx + s * 0.02f, cy + s * 0.03f, s * 0.14f, shade(col, 0.55f));
                circle(c, p, cx, cy, s * 0.14f, shade(col, 1.08f));
                circle(c, p, cx, cy, s * 0.09f, col);
                circle(c, p, cx - s * 0.04f, cy - s * 0.04f, s * 0.04f, 0x88FFFFFF);
            }
        }
    }

    // ---- поле ----
    public static void board(Canvas c, Paint p, Board b, float ox, float oy, float cell, boolean showShips, int theme, int lastCell, boolean labels) {
        board(c, p, b, ox, oy, cell, showShips, theme, lastCell, labels, false);
    }

    public static void board(Canvas c, Paint p, Board b, float ox, float oy, float cell, boolean showShips, int theme, int lastCell, boolean labels, boolean dim) {
        int n = Board.N;
        float sz = cell * n;
        rr(c, p, ox, oy, ox + sz, oy + sz, cell * 0.12f, Scheme.cur.water);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(Math.max(1f, cell * 0.03f));
        p.setColor(Scheme.cur.grid);
        for (int i = 0; i <= n; i++) {
            c.drawLine(ox + i * cell, oy, ox + i * cell, oy + sz, p);
            c.drawLine(ox, oy + i * cell, ox + sz, oy + i * cell, p);
        }
        if (dim) rr(c, p, ox, oy, ox + sz, oy + sz, cell * 0.12f, 0x44000000);
        if (labels) {
            p.setStyle(Paint.Style.FILL);
            p.setColor(0xFFB0BEC5);
            p.setTextSize(cell * 0.45f);
            p.setTextAlign(Paint.Align.CENTER);
            String abc = "АБВГҐДЕЄЖЗ";
            for (int i = 0; i < n; i++) {
                c.drawText(String.valueOf(abc.charAt(i)), ox + i * cell + cell / 2, oy - cell * 0.18f, p);
                c.drawText(String.valueOf(i + 1), ox - cell * 0.32f, oy + i * cell + cell * 0.66f, p);
            }
        }
        for (Board.Ship s : b.ships) {
            if (!s.placed) continue;
            boolean sunk = b.sunk(s);
            if (!showShips && !sunk) continue;
            draw(c, p, s.theme >= 0 ? s.theme : theme, ox + s.c * cell, oy + s.r * cell, cell, s.len, s.h, s.idx);
            if (dim) {
                float w0 = s.h ? s.len * cell : cell, h0 = s.h ? cell : s.len * cell;
                rr(c, p, ox + s.c * cell, oy + s.r * cell, ox + s.c * cell + w0, oy + s.r * cell + h0, cell * 0.1f, 0x33FFFFFF);
            }
            if (sunk) {
                float w = s.h ? s.len * cell : cell, h = s.h ? cell : s.len * cell;
                rr(c, p, ox + s.c * cell + cell * 0.04f, oy + s.r * cell + cell * 0.04f, ox + s.c * cell + w - cell * 0.04f, oy + s.r * cell + h - cell * 0.04f, cell * 0.1f, 0x66B71C1C);
            }
        }
        for (int i = 0; i < n * n; i++) {
            int v = b.shot[i];
            if (v == 0) continue;
            float cx = ox + (i % n) * cell + cell / 2, cy = oy + (i / n) * cell + cell / 2;
            if (v == 1) {
                circle(c, p, cx, cy, cell * 0.12f, Scheme.cur.dot);
            } else {
                p.setStyle(Paint.Style.STROKE);
                p.setStrokeCap(Paint.Cap.ROUND);
                float d = cell * 0.26f;
                p.setStrokeWidth(cell * 0.2f);
                p.setColor(0xFF000000);
                c.drawLine(cx - d, cy - d, cx + d, cy + d, p);
                c.drawLine(cx - d, cy + d, cx + d, cy - d, p);
                p.setStrokeWidth(cell * 0.1f);
                p.setColor(0xFFFFFFFF);
                c.drawLine(cx - d, cy - d, cx + d, cy + d, p);
                c.drawLine(cx - d, cy + d, cx + d, cy - d, p);
                p.setStrokeCap(Paint.Cap.BUTT);
            }
        }
        if (lastCell >= 0) {
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(cell * 0.08f);
            p.setColor(0xFFFFEB3B);
            rf.set(ox + (lastCell % n) * cell + cell * 0.06f, oy + (lastCell / n) * cell + cell * 0.06f,
                ox + (lastCell % n) * cell + cell * 0.94f, oy + (lastCell / n) * cell + cell * 0.94f);
            c.drawRoundRect(rf, cell * 0.1f, cell * 0.1f, p);
        }
        p.setStyle(Paint.Style.FILL);
    }
}
