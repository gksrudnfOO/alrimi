package com.alrimi.timer;

import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.media.AudioAttributes;
import android.media.MediaPlayer;
import android.os.Handler;
import android.os.Looper;

import java.util.HashSet;
import java.util.Set;

/** Plays the alarm clips from assets/sounds on the alarm audio stream. */
final class SoundPlayer {
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final Set<MediaPlayer> LIVE = new HashSet<>();
    static final String[] KEYS = {"dingdong", "f1", "f2", "f3"};

    private SoundPlayer() {}

    static String file(String key) {
        switch (key == null ? "" : key) {
            case "f1": case "f2": case "f3":
                return "sounds/" + key + ".mp3";
            default:
                return "sounds/dingdong.wav";
        }
    }

    /** Plays the sound {@code times} times in a row with a short gap. Safe to call from any thread. */
    static void play(Context ctx, String key, int volume, int times) {
        final Context app = ctx.getApplicationContext();
        MAIN.post(() -> playOnce(app, key, volume, Math.max(1, Math.min(20, times))));
    }

    private static void playOnce(Context ctx, String key, int volume, int left) {
        MediaPlayer mp = new MediaPlayer();
        try (AssetFileDescriptor fd = ctx.getAssets().openFd(file(key))) {
            mp.setAudioAttributes(new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build());
            mp.setDataSource(fd.getFileDescriptor(), fd.getStartOffset(), fd.getLength());
            float v = (float) Math.pow(Math.max(0, Math.min(100, volume)) / 100.0, 1.6);
            mp.setVolume(v, v);
            mp.prepare();
        } catch (Exception e) {
            mp.release();
            return;
        }
        LIVE.add(mp);
        mp.setOnCompletionListener(p -> {
            LIVE.remove(p);
            p.release();
            if (left > 1) MAIN.postDelayed(() -> playOnce(ctx, key, volume, left - 1), 350);
        });
        mp.setOnErrorListener((p, w, x) -> { LIVE.remove(p); p.release(); return true; });
        mp.start();
    }
}
