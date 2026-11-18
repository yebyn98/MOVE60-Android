package com.move60.workout

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.drawable.BitmapDrawable
import android.view.View
import android.view.animation.LinearInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import com.move60.workout.domain.ExerciseContentApproval

/**
 * Two-frame exercise guide that loops start/end posture like a short clip.
 * Frames are public-domain images listed in docs/exercise-images-sources.md, shown only for exercises approved in
 * [ExerciseContentApproval]. Every other exercise shows an honest empty state (docs/v66 PO decision 4, B in docs/v67): the images and the
 * hand-drawn [ExerciseDiagram] have no recorded movement/safety review, so they are NOT shown. Files and code are kept for later re-use.
 */
object ExerciseGuide {
    private fun frame(context: Context, id: String, index: Int) = try {
        context.assets.open("exercise/${id}_$index.jpg").use { BitmapFactory.decodeStream(it) }
    } catch (_: Exception) { null }

    const val EMPTY_TEXT = "이 운동의 동작 안내는 아직 제공되지 않아요."
    const val EMPTY_TAG = "exercise_guide_empty"

    /**
     * True only for an exercise whose guidance was verified (rights + movement accuracy, [ExerciseContentApproval]) AND that has a
     * start/end image pair. The image files of unverified exercises stay in the app, unshown.
     */
    fun hasGuide(context: Context, id: String): Boolean =
        ExerciseContentApproval.isApproved(id) && frame(context, id, 0) != null && frame(context, id, 1) != null

    fun create(context: Context, id: String): View {
        if (!ExerciseContentApproval.isApproved(id)) return emptyState(context)
        val first = frame(context, id, 0)
        val second = frame(context, id, 1)
        if (first == null || second == null) return emptyState(context)
        return GuideLoop(context, first, second)
    }

    /** Doing and recording the exercise works exactly the same without a guide. */
    private fun emptyState(context: Context): View = android.widget.TextView(context).apply {
        tag = EMPTY_TAG
        text = EMPTY_TEXT
        contentDescription = EMPTY_TEXT
        textSize = 13f
        setTextColor(Move60Theme.Colors.textMuted)
        gravity = android.view.Gravity.CENTER
        setBackgroundColor(Move60Theme.Colors.surface)
        val pad = Move60Theme.dp(context, Move60Theme.Space.three)
        setPadding(pad, pad, pad, pad)
        minHeight = Move60Theme.dp(context, 96)
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
