package com.move60.workout

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.TimeZone

class WorkoutWidgetModelTest {
    private val seoul = TimeZone.getTimeZone("Asia/Seoul")

    private fun cardioState(running: Boolean, remaining: Long, deadline: Long = 0L) = JSONObject()
        .put("running", running).put("finished", false).put("sessionStartedAt", 1L)
        .put("stage", 1).put("remaining", remaining).put("deadlineAt", deadline)
        .put("stageSeconds", JSONArray(listOf(300, 1200, 1800, 600)))
        .put("phases", JSONArray()
            .put(JSONObject().put("name", "편안한 페이스").put("seconds", 300).put("rpe", "3–4").put("kind", "실내 사이클"))
            .put(JSONObject().put("name", "조금 빠른 페이스").put("seconds", 900).put("rpe", "5–6").put("kind", "실내 사이클")))

    @Test fun cardioStageShowsMachinePaceAndRpe() {
        val m = WorkoutWidgetModel.build(cardioState(false, 800), null, 0L, seoul)
        assertEquals("유산소 ① · 사이클", m.title)
        assertEquals(WidgetStatus.PAUSED, m.status)
        assertEquals("조금 빠른 페이스 · RPE 5-6", m.pace) // elapsed 400s -> second phase
        assertEquals(800L, m.remainingSeconds)
    }

    @Test fun idleAndFinishedStates() {
        assertEquals("운동 대기", WorkoutWidgetModel.build(JSONObject(), null, 0L, seoul).title)
        val done = JSONObject().put("finished", true).put("sessionStartedAt", 1L)
        val m = WorkoutWidgetModel.build(done, null, 0L, seoul)
        assertEquals("오늘 운동 완료", m.title)
        assertEquals(WidgetStatus.DONE, m.status)
    }

    @Test fun heartShowsOnlyRealSampleWithTime() {
        val at = java.util.Calendar.getInstance(seoul).apply { set(2026, 10, 8, 15, 12, 0) }.timeInMillis
        val m = WorkoutWidgetModel.build(JSONObject(), WidgetHeart(128, at), at + 60_000L, seoul)
        assertEquals(128, m.heartBpm)
        assertEquals("오후 3:12", m.heartTime)
        assertNull(WorkoutWidgetModel.build(JSONObject(), null, at, seoul).heartBpm)
        assertNull(WorkoutWidgetModel.build(JSONObject(), WidgetHeart(0, at), at, seoul).heartBpm)
    }

    @Test fun oldSampleCarriesItsDate() {
        val at = java.util.Calendar.getInstance(seoul).apply { set(2026, 10, 7, 15, 12, 0) }.timeInMillis
        val now = at + 24L * 3600_000L
        assertEquals("11/7 오후 3:12", WorkoutWidgetModel.formatMeasuredAt(at, now, seoul))
    }
}
