package com.alrimi.timer;

import android.app.Activity;
import android.app.PendingIntent;
import android.app.PictureInPictureParams;
import android.app.RemoteAction;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.drawable.Icon;
import android.os.Handler;
import android.os.Looper;
import android.util.Rational;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;

/** Picture-in-picture window: a small floating clock with play/pause and reset buttons. */
final class Pip {
    private static final Handler H = new Handler(Looper.getMainLooper());
    private static WeakReference<Activity> act = new WeakReference<>(null);
    private static Boolean lastActive;

    private Pip() {}

    static boolean supported(Context ctx) {
        return ctx.getPackageManager().hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE);
    }

    static void enter(Activity a) {
        if (!supported(a)) return;
        act = new WeakReference<>(a);
        lastActive = null;
        try { a.enterPictureInPictureMode(params(a)); } catch (Exception ignored) { }
    }

    private static PictureInPictureParams params(Activity a) {
        TimerEngine.Row r = TimerEngine.pick(TimerEngine.get(a).rows());
        boolean active = r != null && r.active;
        lastActive = active;
        List<RemoteAction> acts = new ArrayList<>();
        acts.add(action(a, "repToggle", active ? R.drawable.ic_pause : R.drawable.ic_play, active ? "일시정지" : "시작"));
        acts.add(action(a, "repReset", R.drawable.ic_reset, "초기화"));
        return new PictureInPictureParams.Builder()
                .setAspectRatio(new Rational(16, 9))
                .setActions(acts)
                .build();
    }

    private static RemoteAction action(Context ctx, String cmd, int icon, String title) {
        PendingIntent p = PendingIntent.getBroadcast(ctx, 0, CmdReceiver.intent(ctx, cmd, ""),
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        return new RemoteAction(Icon.createWithResource(ctx, icon), title, title, p);
    }

    /** Swaps the play/pause button when the shown timer starts or stops. */
    static void refresh() {
        Activity a = act.get();
        if (a == null || !a.isInPictureInPictureMode()) return;
        TimerEngine.Row r = TimerEngine.pick(TimerEngine.get(a).rows());
        boolean active = r != null && r.active;
        if (lastActive != null && lastActive == active) return;
        try { a.setPictureInPictureParams(params(a)); } catch (Exception ignored) { }
    }

    private static final Runnable LOOP = new Runnable() {
        @Override public void run() { refresh(); H.postDelayed(this, 500); }
    };

    static void startUpdating(Activity a) {
        act = new WeakReference<>(a);
        H.removeCallbacks(LOOP);
        H.post(LOOP);
    }

    static void stopUpdating() { H.removeCallbacks(LOOP); }
}
