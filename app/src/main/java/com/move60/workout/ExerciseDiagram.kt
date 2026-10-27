package com.move60.workout

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.view.View

/** Offline vector illustration for exercise movement direction, not a photograph. */
class ExerciseDiagram(context: Context, private val exerciseId: String) : View(context) {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val accent = Color.rgb(180, 245, 110)
    private val ink = Color.rgb(220, 232, 245)
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), (resources.displayMetrics.density * 190).toInt())
    }
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        canvas.drawColor(Color.rgb(24, 37, 57))
        fun line(x1: Float,y1: Float,x2: Float,y2: Float,color: Int=ink,stroke: Float=7f) {
            p.color=color;p.strokeWidth=stroke;p.strokeCap=Paint.Cap.ROUND
            canvas.drawLine(x1,y1,x2,y2,p)
        }
        fun circle(x: Float,y: Float,r: Float,color: Int=ink) {p.color=color;canvas.drawCircle(x,y,r,p)}
        fun figure(cx: Float, active: Boolean) {
            val seated = exerciseId.contains("Press",true) || exerciseId.contains("Row",true) || exerciseId.contains("Pulldown",true)
            val headY = h*0.27f
            circle(cx,headY,12f)
            val shoulderY=headY+25f
            val hipY=if(seated) h*.64f else h*.61f
            line(cx,shoulderY,cx,hipY)
            if(seated) {
                line(cx,hipY,cx+24f,hipY+20f);line(cx+24f,hipY+20f,cx+24f,h*.85f)
                line(cx,hipY,cx-20f,hipY+25f);line(cx-20f,hipY+25f,cx-20f,h*.85f)
                line(cx-30f,hipY+5f,cx+34f,hipY+5f,Color.GRAY,4f)
            } else {
                line(cx,hipY,cx-22f,h*.85f);line(cx,hipY,cx+22f,h*.85f)
            }
            val reach=if(active) 40f else 20f
            val handY=if(active) shoulderY-24f else shoulderY+36f
            line(cx,shoulderY,cx-reach,handY,accent)
            line(cx,shoulderY,cx+reach,handY,accent)
            circle(cx-reach,handY,6f,accent);circle(cx+reach,handY,6f,accent)
        }
        figure(w*.27f,false)
        figure(w*.73f,true)
        p.color=ink;p.textSize=27f;p.textAlign=Paint.Align.CENTER
        canvas.drawText("시작",w*.27f,h*.96f,p)
        canvas.drawText("동작",w*.73f,h*.96f,p)
        line(w*.43f,h*.49f,w*.57f,h*.49f,accent,4f)
        line(w*.54f,h*.45f,w*.57f,h*.49f,accent,4f)
        line(w*.54f,h*.53f,w*.57f,h*.49f,accent,4f)
    }
}
