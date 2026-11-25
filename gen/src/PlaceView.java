package com.example.gryntsivgames.sea;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.MotionEvent;
import android.view.View;
import com.example.gryntsivgames.uno.Snd;

public class PlaceView extends View {
    public interface Listener {
        void onChanged();

        void onSound(int kind);

        void onTheme(int t);

        void onMix(boolean on);

        void onBack();

        void onDone();
    }

    private static final int B_THEME = 0, B_MIX = 5, B_RAND = 6, B_CLEAR = 7, B_BACK = 8, B_DONE = 9;

    public Board board = new Board();
    public Listener listener;
    public int theme;
    public boolean mix, locked;
    public String status = "";
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rf = new RectF();
    private float u, ox, oy, cell, trayX, trayY, trayCell, trayH, colW, regW;
    private float[][] btn = new float[10][4];
    private Board.Ship pick;
    private boolean fromBoard, moved;
    private int origR, origC, downBtn = -1;
    private boolean origH;
    private float downX, downY, fx, fy, offR, offC;
    private final java.util.Random rnd = new java.util.Random();

    public PlaceView(Context c) {
        super(c);
    }

    private void calc() {
        float W = getWidth(), H = getHeight();
        u = Math.min(W / 16f, H / 9f);
        cell = Math.min(H * 0.8f, W * 0.5f) / 10f;
        ox = u * 0.9f;
        oy = (H - cell * 10) / 2f + u * 0.2f;
        trayX = ox + cell * 10 + u * 0.35f;
        regW = Math.min(W - trayX - u * 0.3f, 7.6f * u);
        trayCell = Math.min(cell * 0.8f, regW / 2f / 4.3f);
        colW = 4.25f * trayCell;
        trayY = u * 2.7f;
        trayH = u * 0.74f;
        // кнопки: x, y, w, h
        float cw = regW / 5f;
        for (int i = 0; i < 5; i++) btn[B_THEME + i] = new float[]{trayX + i * cw, u * 0.95f, cw - u * 0.08f, u * 0.7f};
        btn[B_MIX] = new float[]{trayX, u * 1.78f, u * 3.4f, u * 0.7f};
        float by = H - u * 1.2f;
        float bw = u * 2.6f;
        float rEdge = W - u * 0.45f;
        btn[B_DONE] = new float[]{rEdge - bw, by, bw, u * 0.9f};
        btn[B_BACK] = new float[]{rEdge - bw * 2 - u * 0.2f, by, bw, u * 0.9f};
        float shipsBottom = trayY + 4 * trayH + trayCell;
        float iy = (shipsBottom + by) / 2f - u * 0.425f;
        btn[B_RAND] = new float[]{trayX + u * 0.1f, iy, u * 0.85f, u * 0.85f};
        btn[B_CLEAR] = new float[]{trayX + u * 2.4f, iy, u * 0.85f, u * 0.85f};
    }

    private float slotX(int i) {
        return trayX + (i % 2) * colW;
    }

    private float slotY(int i) {
        return trayY + (i / 2) * trayH;
    }

    private Board.Ship trayAt(float x, float y) {
        for (Board.Ship s : board.ships) {
            if (s.placed || s == pick) continue;
            float sx = slotX(s.idx), sy = slotY(s.idx);
            if (x >= sx - trayCell * 0.2f && x <= sx + s.len * trayCell + trayCell * 0.2f && y >= sy - trayCell * 0.2f && y <= sy + trayCell * 1.2f) return s;
        }
        return null;
    }

    private int cellAt(float x, float y) {
        int c = (int) Math.floor((x - ox) / cell), r = (int) Math.floor((y - oy) / cell);
        if (c < 0 || r < 0 || c >= 10 || r >= 10) return -1;
        return r * 10 + c;
    }

    private int hitBtn(float x, float y) {
        for (int i = 0; i < btn.length; i++) {
            float[] b = btn[i];
            if (x >= b[0] && x <= b[0] + b[2] && y >= b[1] && y <= b[1] + b[3]) return i;
        }
        return -1;
    }

    private int shipTheme(Board.Ship s) {
        return s.theme >= 0 ? s.theme : theme;
    }

    private void fitText(Canvas cv, String s, float cx, float cy, float maxW, float size, int color) {
        p.setStyle(Paint.Style.FILL);
        p.setColor(color);
        p.setTextAlign(Paint.Align.CENTER);
        p.setTextSize(size);
        float w = p.measureText(s);
        if (w > maxW) p.setTextSize(size * maxW / w);
        cv.drawText(s, cx, cy + p.getTextSize() * 0.35f, p);
    }

    private void button(Canvas cv, int id, String label, int color) {
        float[] b = btn[id];
        p.setStyle(Paint.Style.FILL);
        p.setColor(color);
        rf.set(b[0], b[1], b[0] + b[2], b[1] + b[3]);
        cv.drawRoundRect(rf, u * 0.18f, u * 0.18f, p);
        fitText(cv, label, b[0] + b[2] / 2f, b[1] + b[3] / 2f, b[2] - u * 0.16f, u * 0.32f, 0xFFFFFFFF);
    }

    private void iconBtn(Canvas cv, int id) {
        float[] b = btn[id];
        float cx = b[0] + b[2] / 2f, cy = b[1] + b[3] / 2f, r = b[2] / 2f;
        p.setStyle(Paint.Style.FILL);
        p.setColor(id == B_CLEAR ? 0xFF6D4C41 : 0xFF1E88E5);
        cv.drawCircle(cx, cy, r, p);
        p.setColor(0xFFFFFFFF);
        float k = r * 0.5f;
        if (id == B_CLEAR) {
            // кошик
            cv.drawRect(cx - k * 0.85f, cy - k * 0.55f, cx + k * 0.85f, cy - k * 0.4f, p);
            cv.drawRect(cx - k * 0.3f, cy - k * 0.8f, cx + k * 0.3f, cy - k * 0.55f, p);
            rf.set(cx - k * 0.7f, cy - k * 0.3f, cx + k * 0.7f, cy + k * 0.9f);
            cv.drawRoundRect(rf, k * 0.12f, k * 0.12f, p);
            p.setColor(0xFF6D4C41);
            cv.drawRect(cx - k * 0.32f, cy - k * 0.05f, cx - k * 0.2f, cy + k * 0.7f, p);
            cv.drawRect(cx + k * 0.2f, cy - k * 0.05f, cx + k * 0.32f, cy + k * 0.7f, p);
        } else {
            // стрілки в різні сторони
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(k * 0.28f);
            cv.drawLine(cx - k, cy + k * 0.55f, cx + k * 0.6f, cy - k * 0.55f, p);
            cv.drawLine(cx - k, cy - k * 0.55f, cx + k * 0.6f, cy + k * 0.55f, p);
            p.setStyle(Paint.Style.FILL);
            tri(cv, cx + k * 1.05f, cy - k * 0.6f, k * 0.5f, -1);
            tri(cv, cx + k * 1.05f, cy + k * 0.6f, k * 0.5f, 1);
        }
    }

    private void tri(Canvas cv, float x, float y, float s, int dir) {
        android.graphics.Path q = new android.graphics.Path();
        q.moveTo(x, y);
        q.lineTo(x - s * 1.1f, y - s * 0.85f);
        q.lineTo(x - s * 1.1f, y + s * 0.85f);
        q.close();
        cv.drawPath(q, p);
    }

    @Override
    protected void onDraw(Canvas cv) {
        calc();
        Scheme.drawBg(cv, getWidth(), getHeight());
        Scheme.panel(cv, p, ox - 0.75f * cell, oy - 0.75f * cell, ox + 10.15f * cell, oy + 10.15f * cell, 0.3f * cell, 0x66000000);
        Scheme.panel(cv, p, trayX - 0.15f * u, u * 0.12f, getWidth() - u * 0.15f, getHeight() - u * 0.12f, 0.3f * u, 0x66000000);
        Painter.board(cv, p, board, ox, oy, cell, true, theme, -1, true);
        p.setStyle(Paint.Style.FILL);
        p.setColor(0xFFFFFFFF);
        p.setTextAlign(Paint.Align.LEFT);
        p.setTextSize(u * 0.5f);
        cv.drawText("Розстав кораблі", trayX, u * 0.75f, p);
        for (int i = 0; i < Painter.THEMES.length; i++) button(cv, B_THEME + i, Painter.THEMES[i], i == theme ? 0xFF2E7D32 : 0xFF455A64);
        {
            float[] m = btn[B_MIX];
            float sw = u * 1.1f, sh = u * 0.56f, sy = m[1] + (m[3] - sh) / 2f;
            p.setStyle(Paint.Style.FILL);
            p.setColor(mix ? 0xFF2E7D32 : 0xFF455A64);
            rf.set(m[0], sy, m[0] + sw, sy + sh);
            cv.drawRoundRect(rf, sh / 2f, sh / 2f, p);
            p.setColor(0xFFFFFFFF);
            cv.drawCircle(mix ? m[0] + sw - sh / 2f : m[0] + sh / 2f, sy + sh / 2f, sh * 0.38f, p);
            p.setTextAlign(Paint.Align.LEFT);
            p.setTextSize(u * 0.36f);
            cv.drawText("Поєднувати", m[0] + sw + u * 0.2f, m[1] + m[3] / 2f + u * 0.13f, p);
        }
        for (Board.Ship s : board.ships) {
            if (s.placed || s == pick) continue;
            Painter.draw(cv, p, theme, slotX(s.idx), slotY(s.idx), trayCell, s.len, true, s.idx);
        }
        iconBtn(cv, B_RAND);
        iconBtn(cv, B_CLEAR);
        button(cv, B_BACK, "Назад", 0xFF455A64);
        boolean ok = board.allPlaced() && !locked;
        button(cv, B_DONE, "Готово", ok ? 0xFF2E7D32 : 0x882E7D32);
        if (status.length() > 0) {
            p.setStyle(Paint.Style.FILL);
            p.setColor(0xFFFFD54F);
            p.setTextAlign(Paint.Align.LEFT);
            p.setTextSize(u * 0.3f);
            cv.drawText(status, trayX + u * 3.5f, btn[B_RAND][1] + u * 0.55f, p);
        }
        if (pick != null) {
            if (fx >= ox - cell && fx <= ox + cell * 11 && fy >= oy - cell && fy <= oy + cell * 11) {
                int cc = (int) Math.floor((fx - ox) / cell) - (int) offC, rr = (int) Math.floor((fy - oy) / cell) - (int) offR;
                boolean okp = board.canPlace(rr, cc, pick.len, pick.h, pick.idx);
                float w = pick.h ? pick.len * cell : cell, h = pick.h ? cell : pick.len * cell;
                p.setStyle(Paint.Style.FILL);
                p.setColor(okp ? 0x5500E676 : 0x55FF1744);
                rf.set(ox + cc * cell, oy + rr * cell, ox + cc * cell + w, oy + rr * cell + h);
                cv.drawRoundRect(rf, cell * 0.1f, cell * 0.1f, p);
                Painter.draw(cv, p, shipTheme(pick), ox + cc * cell, oy + rr * cell, cell, pick.len, pick.h, pick.idx);
            } else {
                Painter.draw(cv, p, shipTheme(pick), fx - offC * cell - cell / 2, fy - offR * cell - cell / 2, cell, pick.len, pick.h, pick.idx);
            }
        }
    }

    private void commit(Board.Ship s, int r, int c, boolean h, boolean fresh) {
        board.place(s.idx, r, c, h);
        if (fresh) s.theme = mix ? theme : -1;
        if (listener != null) listener.onSound(Snd.PLACE);
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        calc();
        float x = e.getX(), y = e.getY();
        int a = e.getAction();
        if (a == MotionEvent.ACTION_DOWN) {
            downBtn = hitBtn(x, y);
            if (downBtn >= 0) return true;
            if (locked) return true;
            moved = false;
            downX = x;
            downY = y;
            fx = x;
            fy = y;
            int ci = cellAt(x, y);
            if (ci >= 0) {
                int id = board.shipAt(ci / 10, ci % 10);
                if (id >= 0) {
                    pick = board.ships[id];
                    fromBoard = true;
                    origR = pick.r;
                    origC = pick.c;
                    origH = pick.h;
                    offR = ci / 10 - pick.r;
                    offC = ci % 10 - pick.c;
                    pick.placed = false;
                    invalidate();
                    return true;
                }
            }
            Board.Ship t = trayAt(x, y);
            if (t != null) {
                pick = t;
                fromBoard = false;
                pick.h = true;
                offR = 1;
                offC = 0;
                invalidate();
            }
            return true;
        }
        if (downBtn >= 0) {
            if (a == MotionEvent.ACTION_UP && hitBtn(x, y) == downBtn) press(downBtn);
            if (a == MotionEvent.ACTION_UP || a == MotionEvent.ACTION_CANCEL) downBtn = -1;
            return true;
        }
        if (pick == null) return true;
        if (a == MotionEvent.ACTION_MOVE) {
            fx = x;
            fy = y;
            if (Math.abs(x - downX) + Math.abs(y - downY) > cell * 0.3f) moved = true;
            invalidate();
            return true;
        }
        if (a == MotionEvent.ACTION_UP || a == MotionEvent.ACTION_CANCEL) {
            Board.Ship s = pick;
            pick = null;
            boolean done = false;
            if (a == MotionEvent.ACTION_UP) {
                if (fromBoard && !moved) {
                    boolean nh = !origH;
                    if (board.canPlace(origR, origC, s.len, nh, s.idx)) {
                        commit(s, origR, origC, nh, false);
                        done = true;
                    }
                } else if (fromBoard && x > ox + cell * 10 + cell * 0.5f) {
                    done = true;
                } else {
                    int ci = cellAt(x, y);
                    if (ci >= 0) {
                        int cc = ci % 10 - (int) offC, rr = ci / 10 - (int) offR;
                        if (board.canPlace(rr, cc, s.len, s.h, s.idx)) {
                            commit(s, rr, cc, s.h, !fromBoard);
                            done = true;
                        }
                    }
                }
            }
            if (!done && fromBoard) board.place(s.idx, origR, origC, origH);
            invalidate();
            if (listener != null) listener.onChanged();
        }
        return true;
    }

    private void press(int id) {
        if (listener == null) return;
        listener.onSound(Snd.BTN);
        if (id < 5) {
            theme = id;
            listener.onTheme(id);
        } else if (id == B_MIX) {
            mix = !mix;
            for (Board.Ship s : board.ships) s.theme = mix && s.placed ? theme : -1;
            listener.onMix(mix);
        } else if (id == B_RAND) {
            randomize();
        } else if (id == B_CLEAR) {
            clearAll();
        } else if (id == B_BACK) {
            listener.onBack();
        } else if (id == B_DONE) {
            if (board.allPlaced() && !locked) listener.onDone();
        }
        invalidate();
    }

    public void randomize() {
        if (locked) return;
        board.randomize(rnd);
        if (mix) {
            for (Board.Ship s : board.ships) s.theme = rnd.nextInt(Painter.THEMES.length);
        } else {
            for (Board.Ship s : board.ships) s.theme = -1;
        }
        invalidate();
        if (listener != null) listener.onChanged();
    }

    public void clearAll() {
        if (locked) return;
        board.clear();
        invalidate();
        if (listener != null) listener.onChanged();
    }
}
