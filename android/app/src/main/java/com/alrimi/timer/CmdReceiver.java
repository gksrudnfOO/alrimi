package com.alrimi.timer;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;

import java.util.List;

/** Receives button presses from home-screen widgets and the PIP window. */
public class CmdReceiver extends BroadcastReceiver {
    static final String ACTION = "com.alrimi.timer.CMD";

    /** Intent for one command; the data URI keeps PendingIntents for different buttons apart. */
    static Intent intent(Context ctx, String action, String id) {
        return new Intent(ctx, CmdReceiver.class).setAction(ACTION)
                .setData(Uri.parse("alrimi://cmd/" + action + "/" + (id == null ? "" : id)))
                .putExtra("action", action).putExtra("id", id == null ? "" : id);
    }

    @Override public void onReceive(Context ctx, Intent i) {
        String action = i.getStringExtra("action"), id = i.getStringExtra("id");
        TimerEngine e = TimerEngine.get(ctx);
        if (action != null && action.startsWith("rep")) {
            // PIP buttons act on whichever timer the window is showing now
            List<TimerEngine.Row> rows = e.rows();
            TimerEngine.Row r = TimerEngine.pick(rows);
            if (r == null) return;
            id = r.id;
            action = "repToggle".equals(action) ? "toggle" : "reset";
        }
        e.cmd(action, id);
        TimerService.kick(ctx);
        Widgets.updateAll(ctx);
        Pip.refresh();
    }
}
