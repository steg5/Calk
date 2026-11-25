package com.example.gryntsivgames.uno;

import android.content.Context;
import android.media.MediaPlayer;
import android.media.MediaRecorder;
import android.util.Base64;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;

public class VoiceHelper {
    private final Context ctx;
    private MediaRecorder rec;
    private MediaPlayer player;
    private long startAt;

    public VoiceHelper(Context c) {
        ctx = c.getApplicationContext();
    }

    public boolean start() {
        try {
            File f = new File(ctx.getCacheDir(), "rec.amr");
            rec = new MediaRecorder();
            rec.setAudioSource(MediaRecorder.AudioSource.MIC);
            rec.setOutputFormat(MediaRecorder.OutputFormat.AMR_NB);
            rec.setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB);
            rec.setOutputFile(f.getAbsolutePath());
            rec.setMaxDuration(11000);
            rec.prepare();
            rec.start();
            startAt = System.currentTimeMillis();
            return true;
        } catch (Exception e) {
            releaseRec();
            return false;
        }
    }

    public boolean isRecording() {
        return rec != null;
    }

    // Зупиняє запис, повертає base64 або null, якщо запис закороткий чи не вдався
    public String stop() {
        if (rec == null) return null;
        long dur = System.currentTimeMillis() - startAt;
        try {
            rec.stop();
        } catch (Exception e) {
            releaseRec();
            return null;
        }
        releaseRec();
        if (dur < 600) return null;
        try {
            File f = new File(ctx.getCacheDir(), "rec.amr");
            FileInputStream in = new FileInputStream(f);
            ByteArrayOutputStream bo = new ByteArrayOutputStream();
            byte[] buf = new byte[4096];
            int r;
            while ((r = in.read(buf)) > 0) bo.write(buf, 0, r);
            in.close();
            return Base64.encodeToString(bo.toByteArray(), Base64.NO_WRAP);
        } catch (Exception e) {
            return null;
        }
    }

    private void releaseRec() {
        try {
            if (rec != null) rec.release();
        } catch (Exception e) {
            // ignore
        }
        rec = null;
    }

    public void play(String b64, final Runnable done) {
        doneCb = done;
        try {
            byte[] d = Base64.decode(b64, Base64.NO_WRAP);
            File f = new File(ctx.getCacheDir(), "in.amr");
            FileOutputStream o = new FileOutputStream(f);
            o.write(d);
            o.close();
            stopPlay();
            player = new MediaPlayer();
            player.setDataSource(f.getAbsolutePath());
            player.prepare();
            player.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
                public void onCompletion(MediaPlayer mp) {
                    stopPlay();
                    fireDone();
                }
            });
            player.start();
        } catch (Exception e) {
            stopPlay();
            fireDone();
        }
    }

    private Runnable doneCb;

    private void fireDone() {
        Runnable r = doneCb;
        doneCb = null;
        if (r != null) r.run();
    }

    public void stopPlay() {
        try {
            if (player != null) player.release();
        } catch (Exception e) {
            // ignore
        }
        player = null;
    }

    public void release() {
        releaseRec();
        stopPlay();
    }
}
