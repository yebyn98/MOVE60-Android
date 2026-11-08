package com.move60.workout

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import android.view.View
import android.widget.RemoteViews
import org.json.JSONObject

/** Home-screen widget; keyguard hosting is offered only on devices that support it. */
class WorkoutWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        refresh(context)
    }

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        refresh(context)
    }

    companion object {
        /** Deterministic widget clock logic, including the frozen paused timer. */
        internal fun remainingSecondsForWidget(state: JSONObject, nowMillis: Long): Long {
            val finished = state.optBoolean("finished", false)
            val running = state.optBoolean("running", false) && !finished
            val hasSession = state.optLong("sessionStartedAt", 0L) > 0L
            val deadline = state.optLong("deadlineAt", 0L)
            return when {
                finished -> 0L
                running -> ((deadline - nowMillis + 999L) / 1000L).coerceAtLeast(0L)
                hasSession -> state.optLong("remaining", 0L).coerceAtLeast(0L)
                else -> 0L
            }
        }

        private const val HEART_PREFS = "move60_widget_heart_v1"

        /** Persist the newest Health Connect sample (or clear it) and redraw both widgets. */
        fun saveHeart(context: Context, heart: WidgetHeart?) {
            context.getSharedPreferences(HEART_PREFS, Context.MODE_PRIVATE).edit().apply {
                if (heart == null) clear() else putInt("bpm", heart.bpm).putLong("at", heart.measuredAt)
            }.apply()
            refresh(context)
        }

        private fun loadHeart(context: Context): WidgetHeart? {
            val opted = context.getSharedPreferences("move60_health_opt_in_v1", Context.MODE_PRIVATE)
                .getBoolean("enabled", false)
            if (!opted) return null
            val p = context.getSharedPreferences(HEART_PREFS, Context.MODE_PRIVATE)
            val bpm = p.getInt("bpm", 0)
            return if (bpm > 0) WidgetHeart(bpm, p.getLong("at", 0L)) else null
        }

        fun refresh(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val state = JSONObject(context.getSharedPreferences("timer", Context.MODE_PRIVATE)
                .getString("snapshot", "{}") ?: "{}")
            val now = System.currentTimeMillis()
            val model = WorkoutWidgetModel.build(state, loadHeart(context), now)
            val open = PendingIntent.getActivity(context, 601, Intent(context, MainActivity::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            for ((provider, layout) in listOf(
                WorkoutWidget::class.java to R.layout.widget_workout,
                WorkoutWidgetLarge::class.java to R.layout.widget_workout_large
            )) {
                val ids = manager.getAppWidgetIds(ComponentName(context, provider))
                if (ids.isEmpty()) continue
                manager.updateAppWidget(ids, render(context, layout, model, open))
            }
        }

        internal fun render(context: Context, layout: Int, model: WidgetModel, open: PendingIntent?): RemoteViews =
            RemoteViews(context.packageName, layout).apply {
                setTextViewText(R.id.widget_title, model.title)
                setTextViewText(R.id.widget_chip, model.status.label)
                setInt(R.id.widget_chip, "setBackgroundResource",
                    if (model.status.highlighted) R.drawable.bg_widget_chip else R.drawable.bg_widget_chip_muted)
                setTextColor(R.id.widget_chip, context.getColor(
                    if (model.status.highlighted) R.color.move60_on_accent else R.color.move60_text))
                if (layout == R.layout.widget_workout_large) setTextViewText(R.id.widget_pace, model.pace)
                setTextColor(R.id.widget_timer, context.getColor(
                    if (model.status == WidgetStatus.PAUSED) R.color.move60_text_medium else R.color.move60_text))
                val timerBase = if (model.countDown) SystemClock.elapsedRealtime() + model.remainingSeconds * 1000L
                    else SystemClock.elapsedRealtime() - model.remainingSeconds * 1000L
                setChronometer(R.id.widget_timer, timerBase, null, model.running)
                if (android.os.Build.VERSION.SDK_INT >= 24) setChronometerCountDown(R.id.widget_timer, model.countDown)
                if (layout == R.layout.widget_workout_large) setTextViewText(R.id.widget_timer_label, model.timerLabel)
                val hasHeart = model.heartBpm != null && model.heartTime != null
                setViewVisibility(R.id.widget_heart_icon, if (hasHeart) View.VISIBLE else View.GONE)
                setViewVisibility(R.id.widget_heart_value, if (hasHeart) View.VISIBLE else View.GONE)
                setViewVisibility(R.id.widget_heart_time, if (hasHeart) View.VISIBLE else View.GONE)
                setViewVisibility(R.id.widget_heart_empty, if (hasHeart) View.GONE else View.VISIBLE)
                if (hasHeart) {
                    setTextViewText(R.id.widget_heart_value, model.heartBpm.toString())
                    setTextViewText(R.id.widget_heart_time, model.heartTime)
                }
                if (open != null) setOnClickPendingIntent(android.R.id.background, open)
            }
    }
}
