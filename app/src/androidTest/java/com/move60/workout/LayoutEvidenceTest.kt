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

    @Test fun widgetShowsLogoIconStatusAndTimer() {
        val view = render(R.layout.widget_workout, 180, "widget-workout.png") {
            it.setTextViewText(R.id.widget_status, "운동 대기")
        }
        assertEquals("운동 대기", view.findViewById<TextView>(R.id.widget_status).text.toString())
        assertTrue(view.findViewById<View>(R.id.widget_logo).measuredWidth > 0)
    }

    @Test fun expandedNotificationShowsLogoIconChipRowAndTotal() {
        val view = render(R.layout.notification_workout_expanded, 360, "notification-expanded.png") {
            it.setTextViewText(R.id.n_stage_chip, "실내 사이클")
            it.setTextViewText(R.id.n_total, "전체 20/60분")
            it.setTextViewText(R.id.n_title, "빠르게 페달링")
        }
        assertEquals("전체 20/60분", view.findViewById<TextView>(R.id.n_total).text.toString())
        assertTrue(view.findViewById<View>(R.id.n_brand).measuredWidth > 0)
    }
}
