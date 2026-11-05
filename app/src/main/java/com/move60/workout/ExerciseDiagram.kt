package com.move60.workout

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.view.View
import kotlin.math.cos
import kotlin.math.sin

/**
 * Exercise-specific schematic with a start and end posture. It is a simplified
 * illustration, not a substitute for instruction from a qualified trainer.
 */
class ExerciseDiagram(context: Context, private val exerciseId: String) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val body = Move60Theme.Colors.textHigh
    private val active = Move60Theme.Colors.primary
    private val machine = Move60Theme.Colors.textMedium

    private enum class Motion { SQUAT, HINGE, LUNGE, STEP, PRESS_LEG, CURL_LEG, ABDUCT,
        HIP_EXTENSION, PULL_DOWN, ROW, PRESS_CHEST, RAISE_SIDE, CURL_ARM,
        PRESS_DOWN, FACE_PULL, PALLOF, CARRY, CALF, BRIDGE }

    private fun motion(): Motion = when (exerciseId) {
        "legPress" -> Motion.PRESS_LEG
        "gobletSquat", "boxSquat", "chairSquat", "bodySquat" -> Motion.SQUAT
        "rdl" -> Motion.HINGE
        "reverseLunge" -> Motion.LUNGE
        "stepUp" -> Motion.STEP
        "legCurl" -> Motion.CURL_LEG
        "hipAbduction", "clamshell" -> Motion.ABDUCT
        "cableKickback" -> Motion.HIP_EXTENSION
        "hipThrust", "gluteBridge" -> Motion.BRIDGE
        "latPulldown" -> Motion.PULL_DOWN
        "seatedRow", "cableRow", "bandRow" -> Motion.ROW
        "chestPress", "dbChestPress", "inclinePushup", "wallPushup" -> Motion.PRESS_CHEST
        "lateralRaise" -> Motion.RAISE_SIDE
        "bicepCurl" -> Motion.CURL_ARM
        "tricepPushdown" -> Motion.PRESS_DOWN
        "facePull" -> Motion.FACE_PULL
        "pallofPress" -> Motion.PALLOF
        "farmerCarry" -> Motion.CARRY
        "calfRaise" -> Motion.CALF
        else -> Motion.SQUAT
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val measuredWidth = resolveSize((320 * resources.displayMetrics.density).toInt(), widthMeasureSpec)
        // The figures are drawn in a 360-unit-wide design space that is ~290 units tall; derive the
        // height from the width so the legs and the captions are never clipped or overlapped.
        val preferredHeight = (measuredWidth * DESIGN_HEIGHT / DESIGN_WIDTH).toInt()
        setMeasuredDimension(measuredWidth, resolveSize(preferredHeight, heightMeasureSpec))
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawColor(Move60Theme.Colors.surface)
        val scale = width / DESIGN_WIDTH
        val h = height.toFloat()
        canvas.save()
        canvas.scale(scale, scale)
        val heightUnit = h / scale
        drawPose(canvas, 92f, heightUnit * .42f, false, motion())
        drawPose(canvas, 268f, heightUnit * .42f, true, motion())
        line(canvas, 166f, heightUnit * .49f, 193f, heightUnit * .49f, active, 3f)
        line(canvas, 187f, heightUnit * .45f, 193f, heightUnit * .49f, active, 3f)
        line(canvas, 187f, heightUnit * .53f, 193f, heightUnit * .49f, active, 3f)
        text(canvas, "시작 자세", 92f, heightUnit * .94f, 13f)
        text(canvas, "동작 끝", 268f, heightUnit * .94f, 13f)
        canvas.restore()
    }

    private fun line(c: Canvas, x: Float, y: Float, xx: Float, yy: Float, color: Int = body, size: Float = 5f) {
        paint.color = color
        paint.strokeWidth = size
        paint.strokeCap = Paint.Cap.ROUND
        c.drawLine(x, y, xx, yy, paint)
    }
    private fun dot(c: Canvas, x: Float, y: Float, radius: Float = 8f) {
        paint.color = body
        c.drawCircle(x, y, radius, paint)
    }
    private fun text(c: Canvas, text: String, x: Float, y: Float, size: Float) {
        paint.color = body
        paint.textAlign = Paint.Align.CENTER
        paint.textSize = size
        c.drawText(text, x, y, paint)
    }
    private fun drawPose(c: Canvas, x: Float, cy: Float, end: Boolean, motion: Motion) {
        val seated = motion in setOf(Motion.PRESS_LEG, Motion.CURL_LEG, Motion.ABDUCT,
            Motion.PULL_DOWN, Motion.ROW, Motion.PRESS_CHEST, Motion.FACE_PULL)
        val squatting = motion == Motion.SQUAT && end
        val bending = motion == Motion.HINGE && end
        val headX = if (bending) x + 25f else x
        val headY = if (squatting) cy + 8f else if (bending) cy + 20f else cy - 36f
        val shoulderX = if (bending) x + 17f else x
        val shoulderY = headY + 24f
        val hipX = x
        val hipY = if (squatting) cy + 76f else cy + 59f
        dot(c, headX, headY)
        line(c, shoulderX, shoulderY, hipX, hipY)
        if (seated) {
            line(c, x - 30f, hipY + 3f, x + 36f, hipY + 3f, machine, 5f)
            line(c, x - 22f, hipY + 3f, x - 22f, hipY + 45f, machine, 4f)
            val footX = if (motion == Motion.PRESS_LEG && end) x + 64f else x + 33f
            line(c, hipX, hipY, x + 25f, hipY + 25f)
            line(c, x + 25f, hipY + 25f, footX, hipY + if (motion == Motion.CURL_LEG && end) 4f else 43f, active)
            line(c, footX + 9f, hipY - 12f, footX + 9f, hipY + 49f, machine, 5f)
        } else {
            val kneeY = if (squatting) hipY + 18f else hipY + 36f
            val spread = when (motion) {
                Motion.LUNGE -> if (end) 45f else 20f
                Motion.STEP -> if (end) 40f else 20f
                Motion.ABDUCT -> if (end) 43f else 22f
                else -> if (squatting) 37f else 18f
            }
            line(c, hipX, hipY, x - spread, kneeY, active)
            line(c, x - spread, kneeY, x - spread + 3f, cy + 138f)
            line(c, hipX, hipY, x + spread, kneeY, active)
            line(c, x + spread, kneeY, x + spread + 3f, cy + 138f)
            if (motion == Motion.STEP) {
                line(c, x - 56f, cy + 108f, x + 10f, cy + 108f, machine, 5f)
                line(c, x - 56f, cy + 108f, x - 56f, cy + 140f, machine, 5f)
            }
        }
        val armY = when (motion) {
            Motion.PULL_DOWN -> if (end) shoulderY + 27f else shoulderY - 48f
            Motion.RAISE_SIDE -> if (end) shoulderY else shoulderY + 51f
            Motion.CURL_ARM -> if (end) shoulderY + 8f else shoulderY + 52f
            Motion.PRESS_DOWN -> if (end) shoulderY + 60f else shoulderY + 12f
            Motion.PRESS_CHEST, Motion.PALLOF -> shoulderY + 10f
            Motion.FACE_PULL -> if (end) shoulderY - 8f else shoulderY + 19f
            else -> shoulderY + 43f
        }
        val armReach = when (motion) {
            Motion.PRESS_CHEST, Motion.PALLOF -> if (end) 65f else 24f
            Motion.ROW -> if (end) 20f else 60f
            Motion.PULL_DOWN -> 36f
            Motion.RAISE_SIDE -> 55f
            Motion.FACE_PULL -> if (end) 25f else 56f
            else -> 25f
        }
        line(c, shoulderX, shoulderY, x - armReach, armY, active)
        line(c, shoulderX, shoulderY, x + armReach, armY, active)
        if (motion == Motion.PULL_DOWN) {
            line(c, x - 48f, cy - 100f, x + 48f, cy - 100f, machine, 4f)
            line(c, x - 48f, cy - 100f, x - 48f, armY, machine, 2f)
            line(c, x + 48f, cy - 100f, x + 48f, armY, machine, 2f)
        }
        if (motion == Motion.CARRY || motion == Motion.CURL_ARM || motion == Motion.RAISE_SIDE) {
            dot(c, x - armReach, armY, 6f)
            dot(c, x + armReach, armY, 6f)
        }
        if (motion == Motion.CALF && end) {
            line(c, x - 24f, cy + 140f, x - 8f, cy + 132f, active, 4f)
            line(c, x + 8f, cy + 132f, x + 24f, cy + 140f, active, 4f)
        }
        if (motion == Motion.BRIDGE) {
            line(c, x - 65f, cy + 115f, x + 65f, cy + 115f, machine, 3f)
            line(c, x - 55f, cy + 95f, x, cy + if (end) 55f else 95f, active)
            line(c, x, cy + if (end) 55f else 95f, x + 45f, cy + 100f, active)
        }
    }
}

private const val DESIGN_WIDTH = 360f
private const val DESIGN_HEIGHT = 290f
