package com.alrimi.timer;

import android.content.Context;
import android.os.SystemClock;
import android.os.VibrationEffect;
import android.os.Vibrator;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Runs every timer natively so alarms fire on time even when the WebView is
 * paused or the screen is off. The web UI only sends settings and commands
 * and polls {@link #snapshot()} to draw.
 */
final class TimerEngine {
    /** After a phase ends, keep showing 00:00 this long so the last tick is seen. */
    static final long ZERO_HOLD = 450;

    static final class T {
        String id, name = "", sound = "dingdong";
        boolean enabled = true, autoRestart, multi;
        long durMs;
        int volume = 80, count = 3;
        // runtime: idle | running | delay | paused | done
        String status = "idle";
        long endAt, remaining;
        boolean inDelay;
        int delayTotal;
        long holdUntil; boolean holdDelay; int holdTotal;
        int fired;
    }

    private static TimerEngine inst;
    static synchronized TimerEngine get(Context ctx) {
        if (inst == null) inst = new TimerEngine(ctx.getApplicationContext());
        return inst;
    }

    private final Context ctx;
    private final Map<String, T> timers = new LinkedHashMap<>();
    private final Random rnd = new Random();
    private boolean randomDelay;
    private int randomMax = 3;

    /** Bumped whenever a timer's state changes, so widgets know to redraw. */
    private int version;
    synchronized int version() { return version; }

    private TimerEngine(Context ctx) {
        this.ctx = ctx;
        // restore saved settings so widgets work before the app is opened
        String saved = ctx.getSharedPreferences("engine", Context.MODE_PRIVATE).getString("cfg", null);
        if (saved != null) apply(saved);
    }

    private static long now() { return SystemClock.elapsedRealtime(); }

    /** Applies the saved settings from the web UI. Running timers keep running. */
    synchronized void configure(String json) {
        ctx.getSharedPreferences("engine", Context.MODE_PRIVATE).edit().putString("cfg", json).apply();
        apply(json);
        version++;
    }

    private void apply(String json) {
        try {
            JSONObject o = new JSONObject(json);
            randomDelay = o.optBoolean("randomDelay", false);
            randomMax = Math.max(0, Math.min(600, o.optInt("randomMax", 3)));
            JSONArray arr = o.optJSONArray("timers");
            Map<String, T> next = new LinkedHashMap<>();
            for (int i = 0; arr != null && i < arr.length(); i++) {
                JSONObject j = arr.getJSONObject(i);
                String id = j.getString("id");
                T t = timers.get(id);
                if (t == null) { t = new T(); t.id = id; }
                t.name = j.optString("name", "");
                t.enabled = j.optBoolean("enabled", true);
                t.durMs = Math.max(0, j.optLong("min", 0) * 60 + j.optLong("sec", 0)) * 1000L;
                t.sound = j.optString("sound", "dingdong");
                t.volume = j.optInt("volume", 80);
                t.multi = "multi".equals(j.optString("alertMode", "once"));
                t.count = j.optInt("alertCount", 3);
                t.autoRestart = j.optBoolean("autoRestart", false);
                if (!t.enabled) t.status = "idle";
                if ("idle".equals(t.status)) { t.remaining = t.durMs; t.inDelay = false; }
                next.put(id, t);
            }
            timers.clear();
            timers.putAll(next);
        } catch (Exception ignored) { }
    }

    synchronized void start(String id) { T t = timers.get(id); if (t != null) start(t); }
    synchronized void pause(String id) { T t = timers.get(id); if (t != null) pause(t); }
    synchronized void reset(String id) { T t = timers.get(id); if (t != null) reset(t); }
    synchronized void toggle(String id) {
        T t = timers.get(id); if (t == null) return;
        if (isActive(t)) pause(t); else start(t);
    }
    /** Runs a command from the app, a widget or the PIP window. */
    synchronized void cmd(String action, String id) {
        if (action == null) return;
        switch (action) {
            case "start": start(id); break;
            case "pause": pause(id); break;
            case "toggle": toggle(id); break;
            case "reset": reset(id); break;
            case "startAll": startAll(); break;
            case "pauseAll": pauseAll(); break;
            case "resetAll": resetAll(); break;
            default: return;
        }
        version++;
    }

    /** Copy of one timer's state for widgets and the PIP window. */
    static final class Row {
        String id, name, status; boolean active, delay; long endAt, remain; int delayTotal;
    }

    synchronized List<Row> rows() {
        long n = now();
        List<Row> out = new ArrayList<>();
        int i = 1;
        for (T t : timers.values()) {
            Row r = new Row();
            r.id = t.id;
            r.name = t.name == null || t.name.trim().isEmpty() ? "타이머 " + i : t.name.trim();
            r.status = t.enabled ? t.status : "off";
            r.active = isActive(t);
            r.delay = t.inDelay;
            r.delayTotal = t.delayTotal;
            r.endAt = t.endAt;
            r.remain = remainOf(t, n);
            out.add(r);
            i++;
        }
        return out;
    }

    /** The timer a one-timer view shows: soonest running, else paused, else first enabled. */
    static Row pick(List<Row> rows) {
        Row best = null;
        for (Row r : rows) if (r.active && (best == null || r.endAt < best.endAt)) best = r;
        if (best != null) return best;
        for (Row r : rows) if ("paused".equals(r.status)) return r;
        for (Row r : rows) if (!"off".equals(r.status)) return r;
        return rows.isEmpty() ? null : rows.get(0);
    }

    synchronized void startAll() { for (T t : timers.values()) start(t); }
    synchronized void pauseAll() { for (T t : timers.values()) pause(t); }
    synchronized void resetAll() { for (T t : timers.values()) reset(t); }

    private void start(T t) {
        if (!t.enabled) return;
        long n = now();
        if ("paused".equals(t.status)) {
            if (t.inDelay) { t.status = "delay"; t.endAt = n + t.remaining; }
            else { t.status = "running"; t.endAt = n + t.remaining; }
            return;
        }
        if (isActive(t)) return;
        t.remaining = t.durMs; t.inDelay = false;
        if (t.remaining <= 0) return;
        t.status = "running"; t.endAt = n + t.remaining;
    }

    private void pause(T t) {
        if (isActive(t)) { t.remaining = Math.max(0, t.endAt - now()); t.status = "paused"; }
    }

    private void reset(T t) {
        t.status = "idle"; t.inDelay = false; t.holdUntil = 0; t.remaining = t.durMs;
    }

    private static boolean isActive(T t) { return "running".equals(t.status) || "delay".equals(t.status); }

    synchronized boolean anyActive() {
        for (T t : timers.values()) if (isActive(t)) return true;
        return false;
    }

    /** Advances every timer; plays alarms that are due. Called by TimerService. */
    synchronized void tick() {
        long n = now();
        for (T t : timers.values()) {
            if (!isActive(t) || n < t.endAt) continue;
            if ("running".equals(t.status) && randomDelay && randomMax > 0) {
                // countdown done: wait a random 1..max seconds before the alarm
                t.delayTotal = 1 + rnd.nextInt(randomMax);
                t.holdUntil = n + ZERO_HOLD; t.holdDelay = false;
                t.status = "delay"; t.inDelay = true;
                t.endAt = n + ZERO_HOLD + t.delayTotal * 1000L;
                version++;
                continue;
            }
            complete(t, n);
        }
    }

    private void complete(T t, long n) {
        SoundPlayer.play(ctx, t.sound, t.volume, t.multi ? t.count : 1);
        vibrate();
        t.fired++;
        version++;
        t.holdUntil = n + ZERO_HOLD; t.holdDelay = t.inDelay; t.holdTotal = t.delayTotal;
        t.inDelay = false;
        if (t.autoRestart && t.durMs > 0) { t.status = "running"; t.remaining = t.durMs; t.endAt = n + t.durMs; }
        else { t.status = "done"; t.remaining = 0; }
    }

    private void vibrate() {
        try {
            Vibrator v = ctx.getSystemService(Vibrator.class);
            if (v != null && v.hasVibrator()) v.vibrate(VibrationEffect.createWaveform(new long[]{0, 180, 120, 180}, -1));
        } catch (Exception ignored) { }
    }

    private static long remainOf(T t, long n) {
        switch (t.status) {
            case "running": case "delay": return Math.max(0, t.endAt - n);
            case "paused": return t.remaining;
            case "done": return 0;
            default: return t.durMs;
        }
    }

    /** JSON for the web UI: per-timer status, remaining ms, delay label, flash. */
    synchronized String snapshot() {
        long n = now();
        JSONObject out = new JSONObject();
        try {
            JSONObject ts = new JSONObject();
            for (T t : timers.values()) {
                JSONObject j = new JSONObject();
                boolean held = n < t.holdUntil;
                boolean delaying = held ? t.holdDelay : t.inDelay;
                j.put("status", t.enabled ? t.status : "off");
                j.put("remain", held ? 0 : remainOf(t, n));
                j.put("delay", delaying);
                j.put("delayTotal", held ? t.holdTotal : t.delayTotal);
                j.put("held", held);
                j.put("fired", t.fired);
                ts.put(t.id, j);
            }
            out.put("timers", ts);
            out.put("active", anyActive());
        } catch (Exception ignored) { }
        return out.toString();
    }

    /** One line for the ongoing notification, e.g. "보스 01:23 · 외 2개". */
    synchronized String summary() {
        long n = now();
        List<T> act = new ArrayList<>();
        for (T t : timers.values()) if (isActive(t)) act.add(t);
        if (act.isEmpty()) return "실행 중인 타이머 없음";
        T next = act.get(0);
        for (T t : act) if (t.endAt < next.endAt) next = t;
        int idx = 1;
        for (T t : timers.values()) { if (t == next) break; idx++; }
        String name = next.name == null || next.name.isEmpty() ? "타이머 " + idx : next.name;
        String s = name + (next.inDelay ? " (랜덤 지연) " : " ") + fmt(remainOf(next, n));
        if (act.size() > 1) s += " · 외 " + (act.size() - 1) + "개";
        return s;
    }

    static String fmt(long ms) {
        long s = (ms + 999) / 1000, h = s / 3600, m = s % 3600 / 60, ss = s % 60;
        return h > 0 ? String.format("%d:%02d:%02d", h, m, ss) : String.format("%02d:%02d", m, ss);
    }
}
