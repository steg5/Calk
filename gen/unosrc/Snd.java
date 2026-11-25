package com.example.gryntsivgames.uno;

import android.content.Context;
import android.content.SharedPreferences;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.media.SoundPool;

public class Snd {
    public static final int BTN = 0, THROW = 1, DRAW = 2, POKE = 3, VOICE = 4, PAUSE = 5, DEL = 6, EMO = 7,
        RWIN = 8, RLOSE = 9, GWIN = 10, GLOSE = 11, WRONG = 12, JOIN = 13, MIX = 14, PASS = 15,
        SHOT = 16, MISS = 17, HIT = 18, SUNK = 19, SB_WIN = 20, SB_LOSE = 21, PLACE = 22;
    // власні mp3 з uno_sounds (якщо є), інакше вбудований запасний звук
    private static final String[] NAMES = {"buttons|sfx_btn", "play|sfx_throw", "take|sfx_draw", "sfx_poke", "sfx_voice",
        "sfx_pause", "trash|sfx_del", "sfx_emo", "win|sfx_rwin", "lose|sfx_rlose", "victory|sfx_gwin", "defeat|sfx_glose", "sfx_wrong", "sfx_join",
        "mix|sfx_draw", "pass|sfx_pass",
        "bs_shot", "miss|bs_miss", "hit|bs_hit", "destroy|bs_sunk", "victory|sfx_gwin", "defeat|sfx_glose", "bs_place"};

    private final Context ctx;
    private final SharedPreferences sp;
    private SoundPool pool;
    private final int[] ids = new int[NAMES.length];
    private static MediaPlayer mp;
    private MediaPlayer pv;
    private static String cur = "";
    private static boolean held;
    public boolean sound = true, music = true;
    public float sVol = 0.9f, mVol = 0.35f;

    public Snd(Context c, SharedPreferences sp) {
        ctx = c.getApplicationContext();
        this.sp = sp;
        sound = sp.getBoolean("sound", true);
        music = sp.getBoolean("music", true);
        sVol = sp.getFloat("sVol", 0.9f);
        mVol = sp.getFloat("mVol", 0.35f);
        try {
            pool = new SoundPool(8, AudioManager.STREAM_MUSIC, 0);
            for (int i = 0; i < NAMES.length; i++) {
                int rid = 0;
                String[] cand = NAMES[i].split("\\|");
                for (int k = 0; k < cand.length && rid == 0; k++) {
                    rid = ctx.getResources().getIdentifier(cand[k], "raw", ctx.getPackageName());
                }
                ids[i] = rid == 0 ? 0 : pool.load(ctx, rid, 1);
            }
        } catch (Exception e) {
            pool = null;
        }
    }

    public void save() {
        sp.edit().putBoolean("sound", sound).putBoolean("music", music).putFloat("sVol", sVol).putFloat("mVol", mVol).commit();
    }

    public void play(int kind) {
        if (!sound || pool == null || kind < 0 || kind >= ids.length || ids[kind] == 0) return;
        try {
            pool.play(ids[kind], sVol, sVol, 1, 0, 1f);
        } catch (Exception e) {
            // ignore
        }
    }

    // ---- музика ----
    public void music(String name) {
        if (name.equals(cur) && mp != null) {
            applyMusic();
            return;
        }
        cur = name;
        killMusic();
        try {
            int rid = ctx.getResources().getIdentifier(name, "raw", ctx.getPackageName());
            if (rid == 0) return;
            mp = MediaPlayer.create(ctx, rid);
            if (mp == null) return;
            mp.setLooping(true);
            mp.setVolume(mVol, mVol);
            if (music && !held && pv == null) mp.start();
        } catch (Exception e) {
            killMusic();
        }
    }

    private void killMusic() {
        try {
            if (mp != null) mp.release();
        } catch (Exception e) {
            // ignore
        }
        mp = null;
    }

    private void applyMusic() {
        if (mp == null) return;
        try {
            mp.setVolume(mVol, mVol);
            boolean should = music && !held && pv == null;
            if (should && !mp.isPlaying()) mp.start();
            else if (!should && mp.isPlaying()) mp.pause();
        } catch (Exception e) {
            // ignore
        }
    }

    // тимчасова пауза (гра на паузі, застосунок у фоні)
    public void hold(boolean h) {
        held = h;
        applyMusic();
    }

    public void setMusic(boolean on) {
        music = on;
        save();
        applyMusic();
    }

    public void setSound(boolean on) {
        sound = on;
        save();
    }

    public void setMusicVol(float v) {
        mVol = Math.max(0f, Math.min(1f, v));
        save();
        applyMusic();
        try {
            if (pv != null) pv.setVolume(mVol, mVol);
        } catch (Exception e) {
            // ignore
        }
    }

    public void setSoundVol(float v) {
        sVol = Math.max(0f, Math.min(1f, v));
        save();
    }

    // прослуховування мелодії в налаштуваннях
    public void preview(String name) {
        stopPreview();
        try {
            int rid = ctx.getResources().getIdentifier(name, "raw", ctx.getPackageName());
            if (rid == 0) {
                applyMusic();
                return;
            }
            pv = MediaPlayer.create(ctx, rid);
            if (pv == null) {
                applyMusic();
                return;
            }
            pv.setVolume(Math.max(0.3f, mVol), Math.max(0.3f, mVol));
            applyMusic();
            pv.start();
        } catch (Exception e) {
            pv = null;
            applyMusic();
        }
    }

    public void stopPreview() {
        try {
            if (pv != null) pv.release();
        } catch (Exception e) {
            // ignore
        }
        pv = null;
        applyMusic();
    }

    public boolean hasTrack(String name) {
        return ctx.getResources().getIdentifier(name, "raw", ctx.getPackageName()) != 0;
    }

    public void release(boolean keepMusic) {
        stopPreview();
        if (!keepMusic) {
            killMusic();
            cur = "";
        }
        try {
            if (pool != null) pool.release();
        } catch (Exception e) {
            // ignore
        }
        pool = null;
    }
}
