package com.example.gryntsivgames.uno;

public class Card {
    // c: 0 червоний, 1 жовтий, 2 зелений, 3 синій, 4 дикий
    // v: 0-9 цифри, 10 skip, 11 reverse, 12 draw two, 13 wild, 14 draw four
    public final int c;
    public final int v;

    public Card(int c, int v) {
        this.c = c;
        this.v = v;
    }

    public int points() {
        if (v <= 9) return v;
        if (v <= 12) return 20;
        return 50;
    }

    public boolean same(Card o) {
        return o != null && o.c == c && o.v == v;
    }

    public String enc() {
        return c + ":" + v;
    }

    public static Card dec(String s) {
        String[] p = s.split(":");
        return new Card(Integer.parseInt(p[0]), Integer.parseInt(p[1]));
    }
}
