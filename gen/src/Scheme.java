package com.example.gryntsivgames.sea;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import com.example.gryntsivgames.uno.Bg;

// Кольори гри за спільним фоном (7 фонів)
public class Scheme {
    public final int bg, btn, water, grid, dot;
    public static int id;

    private static int dark(int c, float k) {
        int r = (int) (((c >> 16) & 255) * k), g = (int) (((c >> 8) & 255) * k), b = (int) ((c & 255) * k);
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }

    private Scheme(int bg, int btn, int water) {
        this.bg = bg;
        this.btn = btn;
        this.water = water;
        this.grid = (dark(water, 0.55f) & 0xFFFFFF) | 0x99000000;
        this.dot = dark(water, 0.5f);
    }

    // порядок як у Bg.NAMES: Сукно, Ніч, Захід, Океан, Ромби, Горошок, Дерево
    // вода — світлі пастельні кольори, контрастні до теми
    public static final Scheme[] ALL = {
        new Scheme(0xFF1B5E3A, 0xFF0F3D26, 0xFFD3C8F2),
        new Scheme(0xFF12121C, 0xFF37474F, 0xFFB7E6EC),
        new Scheme(0xFF3B1D3A, 0xFF8E3B2C, 0xFFB6EDE0),
        new Scheme(0xFF0D2847, 0xFF1565C0, 0xFFFFDAB9),
        new Scheme(0xFF2A2250, 0xFF5E35B1, 0xFFF5F0B2),
        new Scheme(0xFF3A1D2C, 0xFF8E2D5A, 0xFFC4EBC9),
        new Scheme(0xFF3E2C1E, 0xFF5D4037, 0xFFBEE0F6)
    };
    public static Scheme cur = ALL[0];

    public static void set(int i) {
        id = Math.max(0, Math.min(ALL.length - 1, i));
        cur = ALL[id];
    }

    public static void drawBg(Canvas c, int w, int h) {
        Bg.draw(c, w, h, id);
    }

    private static final RectF R = new RectF();

    // затемнена напівпрозора підкладка
    public static void panel(Canvas c, Paint p, float l, float t, float r, float b, float rad, int argb) {
        p.setStyle(Paint.Style.FILL);
        p.setColor(argb);
        R.set(l, t, r, b);
        c.drawRoundRect(R, rad, rad, p);
    }
}
