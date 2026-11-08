package com.move60.workout

import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/** Latest Health Connect sample, kept exactly as measured (never a live sensor value). */
data class WidgetHeart(val bpm: Int, val measuredAt: Long)

enum class WidgetStatus(val label: String, val highlighted: Boolean) {
    RUNNING("진행 중", true), PAUSED("일시정지", false), DONE("완료", false), IDLE("대기", false)
}

data class WidgetModel(
    val title: String,
    val status: WidgetStatus,
    val pace: String,
    /** Countdown seconds, or elapsed seconds when [countDown] is false (strength has no allotted time). */
    val remainingSeconds: Long,
    val running: Boolean,
    val countDown: Boolean = true,
    val timerLabel: String = "남은 시간",
    val heartBpm: Int?,
    val heartTime: String?
)

/** Pure builder so widget text is unit-testable without RemoteViews. */
object WorkoutWidgetModel {
    private val stageNames = arrayOf("준비운동", "유산소 ①", "근력운동", "유산소 ②", "스트레칭")

    fun build(
        state: JSONObject,
        heart: WidgetHeart?,
        nowMillis: Long,
        zone: TimeZone = TimeZone.getDefault()
    ): WidgetModel {
        val finished = state.optBoolean("finished", false)
        val running = state.optBoolean("running", false) && !finished
        val hasSession = state.optLong("sessionStartedAt", 0L) > 0L
        val stage = state.optInt("stage", 0).coerceIn(0, LAST_STAGE)
        val stageRemaining = WorkoutWidget.remainingSecondsForWidget(state, nowMillis)
        val cardio = stage == 1 || stage == 3
        val phases = phasesKeyForStage(stage)?.let { state.optJSONArray(it) }
        val machine = phases?.optJSONObject(0)?.optString("kind").orEmpty()
            .ifBlank { state.optString("cardioMachine", "") }
            .removePrefix("실내 ").trim()
        val status = when {
            finished -> WidgetStatus.DONE
            running -> WidgetStatus.RUNNING
            hasSession -> WidgetStatus.PAUSED
            else -> WidgetStatus.IDLE
        }
        val title = when (status) {
            WidgetStatus.DONE -> "오늘 운동 완료"
            WidgetStatus.IDLE -> "운동 대기"
            else -> if (cardio && machine.isNotBlank()) "${stageNames[stage]} · $machine" else stageNames[stage]
        }
        val pace = when (status) {
            WidgetStatus.DONE -> "수고하셨어요"
            WidgetStatus.IDLE -> "앱에서 오늘 운동을 시작하세요"
            else -> paceLine(state, stage, phases, stageRemaining)
        }
        // Timed stages count down the current item (same number as the phone dock); strength counts the overall time up.
        val countUp = stage == 2 && hasSession && !finished
        val timerSeconds = when {
            countUp -> elapsedTotal(state, nowMillis, running)
            finished || !hasSession -> 0L
            else -> phaseRemaining(state, stage, phases, stageRemaining) ?: stageRemaining
        }
        return WidgetModel(
            title = title, status = status, pace = pace, remainingSeconds = timerSeconds, running = running,
            countDown = !countUp, timerLabel = if (countUp) "전체시간" else "남은 시간",
            heartBpm = heart?.bpm?.takeIf { it in 30..240 },
            heartTime = heart?.takeIf { it.bpm in 30..240 && it.measuredAt > 0L }
                ?.let { formatMeasuredAt(it.measuredAt, nowMillis, zone) }
        )
    }

    private fun elapsedTotal(state: JSONObject, nowMillis: Long, running: Boolean): Long {
        val base = state.optLong("activeSeconds", 0L).coerceAtLeast(0L)
        val checkpoint = state.optLong("activeCheckpointAt", 0L)
        return if (running && checkpoint > 0L) base + ((nowMillis - checkpoint) / 1000L).coerceAtLeast(0L) else base
    }

    /** Seconds left in the current timed item, or null when the stage has no timed items. */
    private fun phaseRemaining(state: JSONObject, stage: Int, phases: org.json.JSONArray?, stageRemaining: Long): Long? {
        if (phases == null || phases.length() == 0) return null
        val total = state.optJSONArray("stageSeconds")?.optInt(stage, 0) ?: 0
        var elapsed = (total - stageRemaining).coerceAtLeast(0L)
        for (i in 0 until phases.length()) {
            val seconds = phases.optJSONObject(i)?.optInt("seconds", 60) ?: continue
            if (elapsed < seconds || i == phases.length() - 1) return (seconds - elapsed).coerceAtLeast(0L)
            elapsed -= seconds
        }
        return null
    }

    private fun paceLine(state: JSONObject, stage: Int, phases: org.json.JSONArray?, remaining: Long): String {
        if (phases == null || phases.length() == 0) {
            return when (stage) {
                0 -> "관절을 천천히 풀어주세요"
                2 -> "자세와 세트·횟수를 확인하세요"
                else -> "천천히 마무리하세요"
            }
        }
        val total = state.optJSONArray("stageSeconds")?.optInt(stage, 0) ?: 0
        var elapsed = (total - remaining).coerceAtLeast(0L)
        for (i in 0 until phases.length()) {
            val phase = phases.optJSONObject(i) ?: continue
            val seconds = phase.optInt("seconds", 60)
            if (elapsed < seconds || i == phases.length() - 1) {
                val rpe = phase.optString("rpe", "").replace('–', '-').replace('~', '-')
                val name = phase.optString("name", "유산소")
                return if (rpe.isBlank()) name else "$name · RPE $rpe"
            }
            elapsed -= seconds
        }
        return "유산소 진행 중"
    }

    /** "오후 3:12"; other days are prefixed with "M/d" so an old sample never reads as current. */
    fun formatMeasuredAt(measuredAt: Long, nowMillis: Long, zone: TimeZone): String {
        val time = SimpleDateFormat("a h:mm", Locale.KOREA).apply { timeZone = zone }.format(Date(measuredAt))
        val a = Calendar.getInstance(zone).apply { timeInMillis = measuredAt }
        val b = Calendar.getInstance(zone).apply { timeInMillis = nowMillis }
        val sameDay = a.get(Calendar.YEAR) == b.get(Calendar.YEAR) && a.get(Calendar.DAY_OF_YEAR) == b.get(Calendar.DAY_OF_YEAR)
        return if (sameDay) time else "${a.get(Calendar.MONTH) + 1}/${a.get(Calendar.DAY_OF_MONTH)} $time"
    }
}
