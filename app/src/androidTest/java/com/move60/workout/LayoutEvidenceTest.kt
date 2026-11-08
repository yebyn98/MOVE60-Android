package com.move60.workout

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import android.widget.FrameLayout
import android.widget.RemoteViews
import android.widget.TextView
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Renders the real widget and notification layouts so the PR artifact carries their pictures. */
@RunWith(AndroidJUnit4::class)
class LayoutEvidenceTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    private fun render(layout: Int, widthDp: Int, name: String, fill: (RemoteViews) -> Unit): View {
        var result: View? = null
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val remote = RemoteViews(context.packageName, layout).also(fill)
            val host = FrameLayout(context)
            val view = remote.apply(context, host)
            val width = (widthDp * context.resources.displayMetrics.density).toInt()
            view.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
            view.layout(0, 0, view.measuredWidth, view.measuredHeight)
            val bitmap = Bitmap.createBitmap(view.measuredWidth, view.measuredHeight, Bitmap.Config.ARGB_8888)
            Canvas(bitmap).also { it.drawColor(android.graphics.Color.BLACK); view.draw(it) }
            File(context.getExternalFilesDir(null), name).outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            result = view
        }
        return requireNotNull(result)
    }

    private val sample = WidgetModel(
        title = "유산소 ① · 사이클", status = WidgetStatus.RUNNING, pace = "조금 빠른 페이스 · RPE 5-6",
        remainingSeconds = 750, running = false, heartBpm = 128, heartTime = "오후 3:12"
    )

    @Test fun smallWidgetShowsStageStatusTimerAndHeart() {
        val ctx = InstrumentationRegistry.getInstrumentation().targetContext
        var view: View? = null
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val host = FrameLayout(ctx)
            val v = WorkoutWidget.render(ctx, R.layout.widget_workout, sample, null).apply(ctx, host)
            measureAndSave(ctx, v, 320, "widget-small.png")
            view = v
        }
        val v = requireNotNull(view)
        assertEquals("유산소 ① · 사이클", v.findViewById<TextView>(R.id.widget_title).text.toString())
        assertEquals("진행 중", v.findViewById<TextView>(R.id.widget_chip).text.toString())
        assertEquals("128", v.findViewById<TextView>(R.id.widget_heart_value).text.toString())
        assertEquals("오후 3:12", v.findViewById<TextView>(R.id.widget_heart_time).text.toString())
        assertEquals(View.GONE, v.findViewById<View>(R.id.widget_heart_empty).visibility)
    }

    @Test fun largeWidgetShowsPaceRpeAndWaitingHeartState() {
        val ctx = InstrumentationRegistry.getInstrumentation().targetContext
        var view: View? = null
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val host = FrameLayout(ctx)
            val model = sample.copy(status = WidgetStatus.PAUSED, heartBpm = null, heartTime = null)
            val v = WorkoutWidget.render(ctx, R.layout.widget_workout_large, model, null).apply(ctx, host)
            measureAndSave(ctx, v, 320, "widget-large.png")
            view = v
        }
        val v = requireNotNull(view)
        assertEquals("조금 빠른 페이스 · RPE 5-6", v.findViewById<TextView>(R.id.widget_pace).text.toString())
        assertEquals("일시정지", v.findViewById<TextView>(R.id.widget_chip).text.toString())
        assertEquals(View.VISIBLE, v.findViewById<View>(R.id.widget_heart_empty).visibility)
        assertEquals(View.GONE, v.findViewById<View>(R.id.widget_heart_value).visibility)
    }

    private fun measureAndSave(ctx: android.content.Context, view: View, widthDp: Int, name: String) {
        val width = (widthDp * ctx.resources.displayMetrics.density).toInt()
        view.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
        view.layout(0, 0, view.measuredWidth, view.measuredHeight)
        val bitmap = Bitmap.createBitmap(view.measuredWidth, view.measuredHeight, Bitmap.Config.ARGB_8888)
        Canvas(bitmap).also { it.drawColor(android.graphics.Color.BLACK); view.draw(it) }
        File(ctx.getExternalFilesDir(null), name).outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test fun expandedNotificationShowsLogoIconChipRowAndTotal() {
        val view = render(R.layout.notification_workout_expanded, 360, "notification-expanded.png") {
            it.setTextViewText(R.id.n_stage_chip, "실내 사이클")
            it.setTextViewText(R.id.n_total, "전체시간 20/60분")
            it.setTextViewText(R.id.n_title, "빠르게 페달링")
        }
        assertEquals("전체시간 20/60분", view.findViewById<TextView>(R.id.n_total).text.toString())
        assertTrue(view.findViewById<View>(R.id.n_brand).measuredWidth > 0)
    }
    @Test fun notificationHasNoSectionBadge() {
        val ctx = InstrumentationRegistry.getInstrumentation().targetContext
        assertEquals("the n/N section badge was removed from the notification", 0,
            ctx.resources.getIdentifier("n_badge", "id", ctx.packageName))
    }
}
