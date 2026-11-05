package com.move60.workout

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import android.widget.RemoteViews
import org.json.JSONObject

/** Home-screen widget; keyguard hosting is offered only on devices that support it. */
class WorkoutWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        refresh(context)
    }

    companion object {
        fun refresh(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val component = ComponentName(context, WorkoutWidget::class.java)
            val ids = manager.getAppWidgetIds(component)
            if (ids.isEmpty()) return
            val state = JSONObject(context.getSharedPreferences("timer", Context.MODE_PRIVATE)
                .getString("snapshot", "{}") ?: "{}")
            val running = state.optBoolean("running", false) && !state.optBoolean("finished", false)
            val stage = state.optInt("stage", 0).coerceIn(0, 3)
            val stages = arrayOf("준비운동", "유산소 ①", "근력운동", "유산소 ②")
            val deadline = state.optLong("deadlineAt", System.currentTimeMillis())
            val remaining = ((deadline - System.currentTimeMillis()) / 1000L).coerceAtLeast(0L)
            val open = PendingIntent.getActivity(context, 601, Intent(context, MainActivity::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            val views = RemoteViews(context.packageName, R.layout.widget_workout).apply {
                setTextViewText(R.id.widget_title, if (running) "MOVE60 · ${stages[stage]}" else "MOVE60 · 운동 대기")
                setTextViewText(R.id.widget_status, if (running) "${stage + 1}/4 단계 진행 중" else "앱에서 운동을 시작하세요")
                setChronometer(R.id.widget_timer, SystemClock.elapsedRealtime() + remaining * 1000L, null, running)
                if (android.os.Build.VERSION.SDK_INT >= 24) setChronometerCountDown(R.id.widget_timer, true)
                setOnClickPendingIntent(R.id.widget_root, open)
            }
            manager.updateAppWidget(ids, views)
        }
    }
}
