package com.move60.workout

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.drawable.BitmapDrawable
import android.view.View
import android.view.animation.LinearInterpolator
import android.widget.FrameLayout
import android.widget.ImageView

/**
 * Two-frame exercise guide that loops start/end posture like a short clip.
 * Frames are public-domain images listed in docs/exercise-images-sources.md.
 * Exercises without a licensed image pair fall back to [ExerciseDiagram].
 */
object ExerciseGuide {
    private fun frame(context: Context, id: String, index: Int) = try {
        context.assets.open("exercise/${id}_$index.jpg").use { BitmapFactory.decodeStream(it) }
    } catch (_: Exception) { null }

    fun create(context: Context, id: String): View {
        val first = frame(context, id, 0)
        val second = frame(context, id, 1)
        if (first == null || second == null) return ExerciseDiagram(context, id)
        return GuideLoop(context, first, second)
    }

    private class GuideLoop(context: Context, a: android.graphics.Bitmap, b: android.graphics.Bitmap) : FrameLayout(context) {
        private val start = ImageView(context)
        private val end = ImageView(context)
        private val animator = ValueAnimator.ofFloat(0f, 1f)

        init {
            tag = "exercise_guide_loop"
            contentDescription = "운동 동작 반복 이미지"
            setBackgroundColor(Move60Theme.Colors.surface)
            for ((view, bitmap) in listOf(start to a, end to b)) {
                view.scaleType = ImageView.ScaleType.FIT_CENTER
                view.setImageDrawable(BitmapDrawable(resources, bitmap))
                addView(view, LayoutParams(-1, -1))
            }
            end.alpha = 0f
            animator.duration = 3200L
            animator.repeatCount = ValueAnimator.INFINITE
            animator.interpolator = LinearInterpolator()
            animator.addUpdateListener {
                // 0..0.4 hold start, 0.4..0.5 fade in, 0.5..0.9 hold end, 0.9..1 fade out
                val t = it.animatedValue as Float
                end.alpha = when {
                    t < 0.4f -> 0f
                    t < 0.5f -> (t - 0.4f) / 0.1f
                    t < 0.9f -> 1f
                    else -> 1f - (t - 0.9f) / 0.1f
                }
            }
        }

        override fun onMeasure(widthSpec: Int, heightSpec: Int) {
            val w = MeasureSpec.getSize(widthSpec)
            super.onMeasure(MeasureSpec.makeMeasureSpec(w, MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(w * 3 / 4, MeasureSpec.EXACTLY))
        }

        override fun onAttachedToWindow() { super.onAttachedToWindow(); animator.start() }
        override fun onDetachedFromWindow() { animator.cancel(); super.onDetachedFromWindow() }
    }
}
