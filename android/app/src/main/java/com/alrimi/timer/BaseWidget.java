package com.alrimi.timer;

import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.Context;
import android.os.Bundle;

/** Shared redraw for every widget size. */
abstract class BaseWidget extends AppWidgetProvider {
    @Override public void onUpdate(Context ctx, AppWidgetManager m, int[] ids) { Widgets.updateAll(ctx); }
    @Override public void onAppWidgetOptionsChanged(Context ctx, AppWidgetManager m, int id, Bundle o) { Widgets.updateAll(ctx); }
}
