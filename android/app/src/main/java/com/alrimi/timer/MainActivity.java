package com.alrimi.timer;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.PowerManager;
import android.provider.Settings;
import android.webkit.JavascriptInterface;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

public class MainActivity extends Activity {
    private WebView web;

    @Override protected void onCreate(Bundle saved) {
        super.onCreate(saved);
        web = new WebView(this);
        web.setBackgroundColor(Color.parseColor("#0F1412"));
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setAllowFileAccess(true);
        s.setTextZoom(100);
        web.setWebViewClient(new WebViewClient());
        web.addJavascriptInterface(new Bridge(), "Native");
        setContentView(web);
        web.loadUrl("file:///android_asset/www/index.html");

        if (Build.VERSION.SDK_INT >= 33
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 1);
        }
        handleIntent(getIntent());
    }

    private long lastCheck;
    private void js(String script) { runOnUiThread(() -> { if (web != null) web.evaluateJavascript(script, null); }); }

    @Override protected void onNewIntent(Intent intent) { super.onNewIntent(intent); handleIntent(intent); }

    private void handleIntent(Intent i) {
        if (i != null && Updater.ACTION_STATUS.equals(i.getAction())) Updater.onStatus(this, i, this::js);
    }

    /** Checks GitHub for a newer APK at most every 3 hours (and on demand). */
    void autoCheck() {
        long now = System.currentTimeMillis();
        if (now - lastCheck < 3 * 60 * 60 * 1000L) return;
        lastCheck = now;
        Updater.check(this, false, this::js);
    }

    @Override protected void onResume() { super.onResume(); web.onResume(); web.evaluateJavascript("window.onNativeResume&&onNativeResume()", null); }
    @Override protected void onPause() {
        // keep the page drawing while it is shown in the PIP window
        if (!isInPictureInPictureMode()) web.onPause();
        super.onPause();
    }

    @Override protected void onStop() { web.onPause(); super.onStop(); }

    @Override protected void onStart() { super.onStart(); web.onResume(); }

    @Override public void onPictureInPictureModeChanged(boolean inPip, android.content.res.Configuration cfg) {
        super.onPictureInPictureModeChanged(inPip, cfg);
        web.evaluateJavascript("window.onPip&&onPip(" + inPip + ")", null);
        if (inPip) Pip.startUpdating(this); else Pip.stopUpdating();
    }

    @Override public void onBackPressed() {
        // keep the app (and its timers) alive; just go to the home screen
        moveTaskToBack(true);
    }

    final class Bridge {
        @JavascriptInterface public void configure(String json) {
            TimerEngine.get(MainActivity.this).configure(json);
            Widgets.updateAll(MainActivity.this);
        }

        @JavascriptInterface public boolean pipSupported() { return Pip.supported(MainActivity.this); }

        @JavascriptInterface public void enterPip() { runOnUiThread(() -> Pip.enter(MainActivity.this)); }

        @JavascriptInterface public void cmd(String action, String id) {
            TimerEngine.get(MainActivity.this).cmd(action, id);
            TimerService.kick(MainActivity.this);
            Widgets.updateAll(MainActivity.this);
        }

        @JavascriptInterface public String snapshot() { return TimerEngine.get(MainActivity.this).snapshot(); }

        @JavascriptInterface public void preview(String sound, int volume) { SoundPlayer.play(MainActivity.this, sound, volume, 1); }

        @JavascriptInterface public boolean batteryRestricted() {
            PowerManager pm = getSystemService(PowerManager.class);
            return pm != null && !pm.isIgnoringBatteryOptimizations(getPackageName());
        }

        @JavascriptInterface public void openBatterySettings() {
            runOnUiThread(() -> {
                try {
                    startActivity(new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                            Uri.parse("package:" + getPackageName())));
                } catch (Exception ex) {
                    try { startActivity(new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)); } catch (Exception ignored) { }
                }
            });
        }

        @JavascriptInterface public String version() { return Updater.currentName(MainActivity.this); }

        @JavascriptInterface public void checkUpdate(boolean manual) {
            if (manual) Updater.check(MainActivity.this, true, MainActivity.this::js);
            else runOnUiThread(MainActivity.this::autoCheck);
        }

        /** Returns false when the user must first allow installs from this app (settings opened). */
        @JavascriptInterface public boolean installUpdate(String url) {
            if (!Updater.ensureAllowed(MainActivity.this)) return false;
            Updater.install(MainActivity.this, url, MainActivity.this::js);
            return true;
        }

        @JavascriptInterface public boolean notificationsAllowed() {
            return Build.VERSION.SDK_INT < 33
                    || checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED;
        }

        @JavascriptInterface public void openNotificationSettings() {
            runOnUiThread(() -> {
                try {
                    startActivity(new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                            .putExtra(Settings.EXTRA_APP_PACKAGE, getPackageName()));
                } catch (Exception ignored) { }
            });
        }
    }
}
