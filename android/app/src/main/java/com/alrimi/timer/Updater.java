package com.alrimi.timer;

import android.app.Activity;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageInstaller;
import android.net.Uri;
import android.provider.Settings;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Checks the GitHub "latest release" for a newer APK, downloads it into a
 * PackageInstaller session and asks Android to install it. Sideloaded apps
 * can't update silently, so the user taps 설치 on the system dialog once.
 */
final class Updater {
    static final String LATEST = "https://api.github.com/repos/gksrudnfOO/alrimi/releases/latest";
    static final String ACTION_STATUS = "com.alrimi.timer.INSTALL_STATUS";
    private static final Pattern TAG = Pattern.compile("android-v\\d+\\.\\d+\\.(\\d+)");

    interface Js { void call(String script); }

    private Updater() {}

    static long currentCode(Context ctx) {
        try {
            PackageInfo p = ctx.getPackageManager().getPackageInfo(ctx.getPackageName(), 0);
            return p.getLongVersionCode();
        } catch (Exception e) { return 0; }
    }

    static String currentName(Context ctx) {
        try { return ctx.getPackageManager().getPackageInfo(ctx.getPackageName(), 0).versionName; }
        catch (Exception e) { return ""; }
    }

    private static HttpURLConnection open(String url) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(15000);
        c.setReadTimeout(30000);
        c.setInstanceFollowRedirects(true);
        c.setRequestProperty("User-Agent", "alrimi-timer-android");
        c.setRequestProperty("Accept", "application/vnd.github+json");
        return c;
    }

    /** Looks for a newer release; calls window.onUpdate({...}) in the page when one exists. */
    static void check(Context ctx, boolean manual, Js js) {
        new Thread(() -> {
            try {
                HttpURLConnection c = open(LATEST);
                if (c.getResponseCode() != 200) throw new Exception("HTTP " + c.getResponseCode());
                ByteArrayOutputStream buf = new ByteArrayOutputStream();
                try (InputStream in = c.getInputStream()) {
                    byte[] b = new byte[8192]; int n;
                    while ((n = in.read(b)) > 0) buf.write(b, 0, n);
                }
                JSONObject rel = new JSONObject(buf.toString("UTF-8"));
                Matcher m = TAG.matcher(rel.optString("tag_name", ""));
                String apk = null;
                JSONArray assets = rel.optJSONArray("assets");
                for (int i = 0; assets != null && i < assets.length(); i++) {
                    JSONObject a = assets.getJSONObject(i);
                    if (a.optString("name").endsWith(".apk")) { apk = a.optString("browser_download_url"); break; }
                }
                long latest = m.find() ? Long.parseLong(m.group(1)) : 0;
                JSONObject out = new JSONObject();
                out.put("current", currentName(ctx));
                out.put("newer", apk != null && latest > currentCode(ctx));
                out.put("version", "1.0." + latest);
                out.put("url", apk == null ? "" : apk);
                out.put("manual", manual);
                js.call("window.onUpdate&&onUpdate(" + out + ")");
            } catch (Exception e) {
                if (manual) js.call("window.onUpdate&&onUpdate({error:true,manual:true})");
            }
        }, "update-check").start();
    }

    /** True when Android lets this app install packages; otherwise opens that setting. */
    static boolean ensureAllowed(Activity a) {
        if (a.getPackageManager().canRequestPackageInstalls()) return true;
        try {
            a.startActivity(new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    Uri.parse("package:" + a.getPackageName())));
        } catch (Exception ignored) { }
        return false;
    }

    /** Downloads the APK straight into an install session and commits it. */
    static void install(Activity a, String url, Js js) {
        new Thread(() -> {
            PackageInstaller pi = a.getPackageManager().getPackageInstaller();
            int sid = -1;
            try {
                HttpURLConnection c = open(url);
                c.setRequestProperty("Accept", "application/octet-stream");
                if (c.getResponseCode() != 200) throw new Exception("HTTP " + c.getResponseCode());
                long total = c.getContentLengthLong();
                PackageInstaller.SessionParams p = new PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL);
                p.setAppPackageName(a.getPackageName());
                if (total > 0) p.setSize(total);
                sid = pi.createSession(p);
                try (PackageInstaller.Session s = pi.openSession(sid)) {
                    try (InputStream in = c.getInputStream(); OutputStream out = s.openWrite("alrimi.apk", 0, total > 0 ? total : -1)) {
                        byte[] b = new byte[65536]; int n; long done = 0; int last = -1;
                        while ((n = in.read(b)) > 0) {
                            out.write(b, 0, n); done += n;
                            int pct = total > 0 ? (int) (done * 100 / total) : -1;
                            if (pct != last) { last = pct; js.call("window.onUpdateProgress&&onUpdateProgress(" + pct + ")"); }
                        }
                        s.fsync(out);
                    }
                    Intent cb = new Intent(a, MainActivity.class).setAction(ACTION_STATUS)
                            .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
                    PendingIntent pend = PendingIntent.getActivity(a, 7, cb,
                            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_MUTABLE);
                    s.commit(pend.getIntentSender());
                }
                js.call("window.onUpdateProgress&&onUpdateProgress('confirm')");
            } catch (Exception e) {
                if (sid >= 0) try { pi.abandonSession(sid); } catch (Exception ignored) { }
                js.call("window.onUpdateProgress&&onUpdateProgress('fail')");
            }
        }, "update-install").start();
    }

    /** Handles the install session status sent back to MainActivity. */
    static void onStatus(Activity a, Intent i, Js js) {
        int st = i.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE);
        if (st == PackageInstaller.STATUS_PENDING_USER_ACTION) {
            Intent confirm = i.getParcelableExtra(Intent.EXTRA_INTENT);
            if (confirm != null) {
                try { a.startActivity(confirm); } catch (Exception e) { js.call("window.onUpdateProgress&&onUpdateProgress('fail')"); }
            }
        } else if (st != PackageInstaller.STATUS_SUCCESS) {
            js.call("window.onUpdateProgress&&onUpdateProgress('" + (st == PackageInstaller.STATUS_FAILURE_ABORTED ? "cancel" : "fail") + "')");
        }
    }
}
