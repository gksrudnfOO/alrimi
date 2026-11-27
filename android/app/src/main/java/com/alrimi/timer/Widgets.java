package com.alrimi.timer;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.RemoteViews;

import java.util.List;

/** Draws the three home-screen widgets from the timer engine. */
final class Widgets {
    private Widgets() {}

    static final int[] ROW = {R.id.r0, R.id.r1, R.id.r2, R.id.r3, R.id.r4, R.id.r5};
    static final int[] NAME = {R.id.r0_name, R.id.r1_name, R.id.r2_name, R.id.r3_name, R.id.r4_name, R.id.r5_name};
    static final int[] STAT = {R.id.r0_stat, R.id.r1_stat, R.id.r2_stat, R.id.r3_stat, R.id.r4_stat, R.id.r5_stat};
    static final int[] CHRONO = {R.id.r0_chrono, R.id.r1_chrono, R.id.r2_chrono, R.id.r3_chrono, R.id.r4_chrono, R.id.r5_chrono};
    static final int[] TEXT = {R.id.r0_text, R.id.r1_text, R.id.r2_text, R.id.r3_text, R.id.r4_text, R.id.r5_text};
    static final int[] BTN = {R.id.r0_btn, R.id.r1_btn, R.id.r2_btn, R.id.r3_btn, R.id.r4_btn, R.id.r5_btn};

    static void updateAll(Context c) {
        Context ctx = c.getApplicationContext();
        AppWidgetManager m = AppWidgetManager.getInstance(ctx);
        List<TimerEngine.Row> rows = TimerEngine.get(ctx).rows();
        for (int id : m.getAppWidgetIds(new ComponentName(ctx, WidgetSmall.class))) m.updateAppWidget(id, single(ctx, rows, R.layout.widget_small));
        for (int id : m.getAppWidgetIds(new ComponentName(ctx, WidgetMedium.class))) m.updateAppWidget(id, single(ctx, rows, R.layout.widget_medium));
        for (int id : m.getAppWidgetIds(new ComponentName(ctx, WidgetLarge.class))) m.updateAppWidget(id, list(ctx, rows, m.getAppWidgetOptions(id)));
    }

    static String statusText(TimerEngine.Row r) {
        switch (r.status) {
            case "running": return "진행 중";
            case "delay": return "지연 중";
            case "paused": return "일시정지";
            case "done": return "완료";
            case "off": return "꺼짐";
            default: return "대기";
        }
    }

    static int statusColor(TimerEngine.Row r) {
        switch (r.status) {
            case "running": return 0xFF3DBB91;
            case "delay": case "paused": return 0xFFE0B04F;
            case "done": return 0xFF7AA7FF;
            default: return 0xFF8A9893;
        }
    }

    private static PendingIntent cmd(Context ctx, String action, String id) {
        return PendingIntent.getBroadcast(ctx, 0, CmdReceiver.intent(ctx, action, id),
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
    }

    private static PendingIntent openApp(Context ctx) {
        Intent i = new Intent(ctx, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        return PendingIntent.getActivity(ctx, 100, i, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
    }

    /** Live countdown while running (the launcher ticks it), fixed text otherwise. */
    private static void clock(RemoteViews v, int chrono, int text, TimerEngine.Row r) {
        if (r.active) {
            v.setChronometer(chrono, r.endAt, null, true);
            v.setChronometerCountDown(chrono, true);
            v.setViewVisibility(chrono, View.VISIBLE);
            v.setViewVisibility(text, View.GONE);
        } else {
            v.setChronometer(chrono, 0, null, false);
            v.setViewVisibility(chrono, View.GONE);
            v.setTextViewText(text, TimerEngine.fmt(r.remain));
            v.setViewVisibility(text, View.VISIBLE);
        }
    }

    private static String label(TimerEngine.Row r) {
        return r.delay && r.active ? "랜덤 지연 " + r.delayTotal + "초" : statusText(r);
    }

    /** Small and medium: one timer, the same one the PIP window shows. */
    private static RemoteViews single(Context ctx, List<TimerEngine.Row> rows, int layout) {
        RemoteViews v = new RemoteViews(ctx.getPackageName(), layout);
        v.setOnClickPendingIntent(R.id.w_root, openApp(ctx));
        TimerEngine.Row r = TimerEngine.pick(rows);
        if (r == null) {
            v.setTextViewText(R.id.w_name, "타이머 없음");
            v.setTextViewText(R.id.w_stat, "앱에서 추가하세요");
            v.setTextViewText(R.id.w_text, "--:--");
            v.setViewVisibility(R.id.w_chrono, View.GONE);
            v.setViewVisibility(R.id.w_text, View.VISIBLE);
            return v;
        }
        v.setTextViewText(R.id.w_name, r.name);
        v.setTextViewText(R.id.w_stat, label(r));
        v.setTextColor(R.id.w_stat, statusColor(r));
        clock(v, R.id.w_chrono, R.id.w_text, r);
        v.setImageViewResource(R.id.w_btn, r.active ? R.drawable.ic_pause : R.drawable.ic_play);
        v.setOnClickPendingIntent(R.id.w_btn, cmd(ctx, "toggle", r.id));
        if (layout == R.layout.widget_medium) v.setOnClickPendingIntent(R.id.w_reset, cmd(ctx, "reset", r.id));
        return v;
    }

    /** Large: every timer as a row; how many rows fit follows the widget's height. */
    private static RemoteViews list(Context ctx, List<TimerEngine.Row> rows, Bundle opts) {
        RemoteViews v = new RemoteViews(ctx.getPackageName(), R.layout.widget_large);
        v.setOnClickPendingIntent(R.id.w_head, openApp(ctx));
        v.setOnClickPendingIntent(R.id.w_all_start, cmd(ctx, "startAll", ""));
        v.setOnClickPendingIntent(R.id.w_all_pause, cmd(ctx, "pauseAll", ""));
        v.setOnClickPendingIntent(R.id.w_all_reset, cmd(ctx, "resetAll", ""));
        int h = opts == null ? 0 : opts.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, 0);
        int fit = h <= 0 ? ROW.length : Math.max(1, Math.min(ROW.length, (h - 52) / 46));
        int shown = Math.min(fit, rows.size());
        for (int i = 0; i < ROW.length; i++) {
            if (i >= shown) { v.setViewVisibility(ROW[i], View.GONE); continue; }
            TimerEngine.Row r = rows.get(i);
            v.setViewVisibility(ROW[i], View.VISIBLE);
            v.setTextViewText(NAME[i], r.name);
            v.setTextViewText(STAT[i], label(r));
            v.setTextColor(STAT[i], statusColor(r));
            clock(v, CHRONO[i], TEXT[i], r);
            v.setImageViewResource(BTN[i], r.active ? R.drawable.ic_pause : R.drawable.ic_play);
            v.setOnClickPendingIntent(BTN[i], cmd(ctx, "toggle", r.id));
            v.setOnClickPendingIntent(ROW[i], openApp(ctx));
        }
        v.setViewVisibility(R.id.w_empty, rows.isEmpty() ? View.VISIBLE : View.GONE);
        v.setViewVisibility(R.id.w_more, rows.size() > shown ? View.VISIBLE : View.GONE);
        v.setTextViewText(R.id.w_more, "외 " + (rows.size() - shown) + "개 · 위젯을 늘리면 더 보여요");
        return v;
    }
}
