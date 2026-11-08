package com.move60.workout

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context

/** Large home-screen widget; shares the model and renderer with [WorkoutWidget]. */
class WorkoutWidgetLarge : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        WorkoutWidget.refresh(context)
    }

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        WorkoutWidget.refresh(context)
    }
}
