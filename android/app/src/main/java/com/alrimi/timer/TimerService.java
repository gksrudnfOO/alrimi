package com.alrimi.timer;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;

/**
 * Foreground service that keeps the process and CPU awake while any timer is
 * running, ticks the engine and shows the next alarm in a notification.
 */
public class TimerService extends Service {
    static final String CHANNEL = "timer_running";
    static final int NOTIF_ID = 1;
    static final String ACTION_PAUSE_ALL = "com.alrimi.timer.PAUSE_ALL";
    static final String ACTION_RESET_ALL = "com.alrimi.timer.RESET_ALL";

    private final Handler handler = new Handler(Looper.getMainLooper());
    private PowerManager.WakeLock wake;
    private TimerEngine engine;
    private String lastText = "";
    private boolean running;
    private int lastVersion = -1;

    /** Starts the service if a timer is running (call after any start command). */
    static void kick(Context ctx) {
        if (!TimerEngine.get(ctx).anyActive()) return;
        Intent i = new Intent(ctx, TimerService.class);
        try { ctx.startForegroundService(i); } catch (Exception ignored) { }
    }

    @Override public void onCreate() {
        super.onCreate();
        engine = TimerEngine.get(this);
        NotificationManager nm = getSystemService(NotificationManager.class);
        NotificationChannel ch = new NotificationChannel(CHANNEL, "실행 중인 타이머", NotificationManager.IMPORTANCE_LOW);
        ch.setShowBadge(false);
        ch.setSound(null, null);
        nm.createNotificationChannel(ch);
        PowerManager pm = getSystemService(PowerManager.class);
        wake = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "alrimi:timer");
        wake.setReferenceCounted(false);
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        String a = intent == null ? null : intent.getAction();
        Notification n = build(engine.summary());
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(NOTIF_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
        } else {
            startForeground(NOTIF_ID, n);
        }
        if (ACTION_PAUSE_ALL.equals(a)) engine.cmd("pauseAll", "");
        else if (ACTION_RESET_ALL.equals(a)) engine.cmd("resetAll", "");
        Widgets.updateAll(this);
        wake.acquire(12 * 60 * 60 * 1000L);
        if (!running) { running = true; handler.post(loop); }
        return START_NOT_STICKY;
    }

    private final Runnable loop = new Runnable() {
        @Override public void run() {
            engine.tick();
            int v = engine.version();
            if (v != lastVersion) { lastVersion = v; Widgets.updateAll(TimerService.this); }
            if (!engine.anyActive()) { stopAll(); return; }
            String text = engine.summary();
            if (!text.equals(lastText)) {
                lastText = text;
                getSystemService(NotificationManager.class).notify(NOTIF_ID, build(text));
            }
            handler.postDelayed(this, 100);
        }
    };

    private void stopAll() {
        running = false;
        handler.removeCallbacks(loop);
        if (wake.isHeld()) wake.release();
        stopForeground(STOP_FOREGROUND_REMOVE);
        stopSelf();
    }

    private Notification build(String text) {
        Intent open = new Intent(this, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent pOpen = PendingIntent.getActivity(this, 0, open, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        PendingIntent pPause = PendingIntent.getService(this, 1,
                new Intent(this, TimerService.class).setAction(ACTION_PAUSE_ALL), PendingIntent.FLAG_IMMUTABLE);
        PendingIntent pReset = PendingIntent.getService(this, 2,
                new Intent(this, TimerService.class).setAction(ACTION_RESET_ALL), PendingIntent.FLAG_IMMUTABLE);
        return new Notification.Builder(this, CHANNEL)
                .setSmallIcon(R.drawable.ic_stat)
                .setContentTitle("알림 타이머 실행 중")
                .setContentText(text)
                .setContentIntent(pOpen)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setShowWhen(false)
                .setCategory(Notification.CATEGORY_STOPWATCH)
                .addAction(new Notification.Action.Builder(null, "전체 일시정지", pPause).build())
                .addAction(new Notification.Action.Builder(null, "전체 초기화", pReset).build())
                .build();
    }

    @Override public void onDestroy() {
        handler.removeCallbacks(loop);
        if (wake != null && wake.isHeld()) wake.release();
        super.onDestroy();
    }

    @Override public IBinder onBind(Intent intent) { return null; }
}
