package com.example.gryntsivgames.uno;

public class Theme {
    public final String title;
    public final int[] col;
    public final int black;
    public final int leaf;
    public final int bg;
    public final int text;
    public final int style;

    private Theme(String title, int[] col, int black, int leaf, int bg, int text, int style) {
        this.title = title;
        this.col = col;
        this.black = black;
        this.leaf = leaf;
        this.bg = bg;
        this.text = text;
        this.style = style;
    }

    // стилі: 0 листок, 1 плоска, 2 овал (ретро), 3 неон, 4 мінімал
    public static final Theme[] ALL = {
        new Theme("Класична",
            new int[]{0xFFE1352B, 0xFFF2B300, 0xFF1E8A3C, 0xFF3D9AE8},
            0xFF14141A, 0xFFFFFFFF, 0xFF1B5E3A, 0xFFFFFFFF, 0),
        new Theme("Сучасна",
            new int[]{0xFFE53935, 0xFFFFB300, 0xFF43A047, 0xFF1E88E5},
            0xFF263238, 0xFFFFFFFF, 0xFF1B5E3A, 0xFFFFFFFF, 1),
        new Theme("Ретро",
            new int[]{0xFFD32F2F, 0xFFF9A825, 0xFF2E7D32, 0xFF1565C0},
            0xFF1A1A1A, 0xFFFFF8E1, 0xFF1B5E3A, 0xFFFFFFFF, 2),
        new Theme("Неон",
            new int[]{0xFFFF1744, 0xFFFFEA00, 0xFF00E676, 0xFF00B0FF},
            0xFF0B0B14, 0xFFFFFFFF, 0xFF121417, 0xFFFFFFFF, 3),
        new Theme("Мінімал",
            new int[]{0xFFE57373, 0xFFFFB74D, 0xFF81C784, 0xFF64B5F6},
            0xFF37474F, 0xFFFAF3E6, 0xFFF3ECE2, 0xFF3A3A48, 4)
    };

    // кольори гравців (жовтий лишається для колоди)
    private static final int[] PCOL = {0xFFFF7043, 0xFF26C6DA, 0xFF9CCC65, 0xFFEC407A, 0xFFB388FF, 0xFFFFA726};

    public static int pcol(int i) {
        if (i < 0) i = 0;
        return PCOL[i % PCOL.length];
    }

    public static Theme get(int i) {
        if (i < 0) i = 0;
        if (i >= ALL.length) i = ALL.length - 1;
        return ALL[i];
    }
}
