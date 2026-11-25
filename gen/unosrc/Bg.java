package com.example.gryntsivgames.uno;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.Shader;

// Фони ігрового столу, намальовані кодом
public class Bg {
    public static final int[] BTN = {0xFF0F3D26, 0xFF37474F, 0xFF8E3B2C, 0xFF1565C0, 0xFF5E35B1, 0xFF8E2D5A, 0xFF5D4037};
    public static final String[] NAMES = {"Сукно", "Ніч", "Захід", "Океан", "Ромби", "Горошок", "Дерево"};
    private static Bitmap cache;
    private static int cacheId = -1, cacheW, cacheH;

    public static void draw(Canvas c, int w, int h, int id) {
        if (cache == null || cacheId != id || cacheW != w || cacheH != h) {
            cache = Bitmap.createBitmap(Math.max(1, w), Math.max(1, h), Bitmap.Config.ARGB_8888);
            cacheId = id;
            cacheW = w;
            cacheH = h;
            paint(new Canvas(cache), w, h, id);
        }
        c.drawBitmap(cache, 0, 0, null);
    }

    private static void grad(Canvas c, int w, int h, float x1, float y1, float x2, float y2, int a, int b) {
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setShader(new LinearGradient(x1, y1, x2, y2, a, b, Shader.TileMode.CLAMP));
        c.drawRect(0, 0, w, h, p);
    }

    private static void vignette(Canvas c, int w, int h, int alpha) {
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        float r = (float) Math.sqrt(w * w + h * h) / 2f;
        p.setShader(new RadialGradient(w / 2f, h / 2f, r, 0x00000000, alpha << 24, Shader.TileMode.CLAMP));
        c.drawRect(0, 0, w, h, p);
    }

    // для маленьких превʼю (без кешу)
    public static void drawDirect(Canvas c, int w, int h, int id) {
        paint(c, w, h, id);
    }

    private static void paint(Canvas c, int w, int h, int id) {
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        float s = Math.min(w, h) / 9f;
        if (id == 0) {
            grad(c, w, h, 0, 0, 0, h, 0xFF1F7A4A, 0xFF0F4A2C);
            vignette(c, w, h, 150);
        } else if (id == 1) {
            grad(c, w, h, 0, 0, 0, h, 0xFF0B1026, 0xFF1D2A5B);
            p.setColor(0xCCFFFFFF);
            for (int i = 0; i < 60; i++) {
                float x = (i * 7919 % 1000) / 1000f * w;
                float y = (i * 104729 % 1000) / 1000f * h * 0.8f;
                c.drawCircle(x, y, s * (0.02f + (i % 4) * 0.012f), p);
            }
        } else if (id == 2) {
            grad(c, w, h, 0, 0, w, h, 0xFF3A1C71, 0xFFFF7E5F);
            vignette(c, w, h, 90);
        } else if (id == 3) {
            grad(c, w, h, 0, 0, 0, h, 0xFF0A4F66, 0xFF0B8793);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(s * 0.07f);
            p.setColor(0x33FFFFFF);
            Path path = new Path();
            for (int row = 0; row < 7; row++) {
                float y0 = h * (0.15f + row * 0.13f);
                path.reset();
                path.moveTo(0, y0);
                for (float x = 0; x < w; x += s) {
                    path.quadTo(x + s * 0.5f, y0 - s * 0.35f, x + s, y0);
                }
                c.drawPath(path, p);
            }
        } else if (id == 4) {
            c.drawColor(0xFF6A1B2B);
            p.setStyle(Paint.Style.FILL);
            p.setColor(0xFF7C2236);
            float d = s * 1.2f;
            Path path = new Path();
            for (int i = 0; i * d < w + d; i++) {
                for (int j = 0; j * d < h + d; j++) {
                    if ((i + j) % 2 != 0) continue;
                    float cx = i * d, cy = j * d;
                    path.reset();
                    path.moveTo(cx, cy - d / 2f);
                    path.lineTo(cx + d / 2f, cy);
                    path.lineTo(cx, cy + d / 2f);
                    path.lineTo(cx - d / 2f, cy);
                    path.close();
                    c.drawPath(path, p);
                }
            }
            vignette(c, w, h, 120);
        } else if (id == 5) {
            c.drawColor(0xFF0E5560);
            p.setStyle(Paint.Style.FILL);
            p.setColor(0xFF136C79);
            float d = s * 0.9f;
            for (int i = 0; i * d < w + d; i++) {
                for (int j = 0; j * d < h + d; j++) {
                    float off = (j % 2) * d / 2f;
                    c.drawCircle(i * d + off, j * d, s * 0.18f, p);
                }
            }
            vignette(c, w, h, 110);
        } else {
            c.drawColor(0xFF5D4037);
            p.setStyle(Paint.Style.FILL);
            float d = s * 0.55f;
            for (int j = 0; j * d < h + d; j++) {
                p.setColor((j % 2 == 0) ? 0xFF6D4C41 : 0xFF5A3B31);
                c.drawRect(0, j * d, w, j * d + d * 0.92f, p);
            }
            p.setColor(0x22000000);
            for (int j = 0; j < 14; j++) {
                float y = (j * 6151 % 1000) / 1000f * h;
                c.drawRect((j * 3331 % 1000) / 1000f * w, y, (j * 3331 % 1000) / 1000f * w + s * 1.6f, y + s * 0.03f, p);
            }
            vignette(c, w, h, 140);
        }
    }
}
