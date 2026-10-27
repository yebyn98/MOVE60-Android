package com.move60.workout

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Handler
import android.os.Looper
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * Real two-position exercise photographs from the public-domain free-exercise-db.
 * Only manually matched exercises are included. Cached after first network access.
 * Source: https://github.com/yuhonas/free-exercise-db (Unlicense).
 */
object ExercisePhotos {
    private val exerciseIds = mapOf(
        "legPress" to "Leg_Press",
        "gobletSquat" to "Goblet_Squat",
        "boxSquat" to "Box_Squat",
        "stepUp" to "Dumbbell_Step_Ups",
        "gluteBridge" to "Barbell_Glute_Bridge",
        "hipThrust" to "Barbell_Hip_Thrust",
        "cableKickback" to "One-Legged_Cable_Kickback",
        "legCurl" to "Seated_Leg_Curl",
        "rdl" to "Romanian_Deadlift",
        "calfRaise" to "Standing_Calf_Raises",
        "reverseLunge" to "Crossover_Reverse_Lunge",
        "latPulldown" to "Wide-Grip_Lat_Pulldown",
        "seatedRow" to "Seated_Cable_Rows",
        "cableRow" to "Seated_Cable_Rows",
        "chestPress" to "Leverage_Chest_Press",
        "dbChestPress" to "Dumbbell_Bench_Press",
        "inclinePushup" to "Incline_Push-Up",
        "facePull" to "Face_Pull",
        "lateralRaise" to "Side_Lateral_Raise",
        "bicepCurl" to "Dumbbell_Bicep_Curl",
        "tricepPushdown" to "Triceps_Pushdown",
        "deadBug" to "Dead_Bug",
        "plank" to "Plank",
        "pallofPress" to "Pallof_Press",
        "chairSquat" to "Chair_Squat",
        "farmerCarry" to "Farmers_Walk",
        "bodySquat" to "Bodyweight_Squat"
    )

    fun hasPhotos(id: String) = exerciseIds.containsKey(id)

    fun addTo(context: Context, parent: LinearLayout, id: String, text: (String) -> TextView) {
        val source = exerciseIds[id] ?: return
        parent.addView(text("실제 운동 자세 사진 · 시작 / 끝"))
        val row = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
        for (index in 0..1) {
            val frame = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
            val image = ImageView(context).apply {
                scaleType = ImageView.ScaleType.FIT_CENTER
                adjustViewBounds = true
                minimumHeight = (context.resources.displayMetrics.density * 145).toInt()
                contentDescription = if (index == 0) "시작 자세 사진" else "끝 자세 사진"
            }
            frame.addView(image, LinearLayout.LayoutParams(-1, (context.resources.displayMetrics.density * 170).toInt()))
            frame.addView(text(if (index == 0) "① 시작 자세" else "② 동작 끝"))
            row.addView(frame, LinearLayout.LayoutParams(0, -2, 1f))
            load(context.applicationContext, source, index, image)
        }
        parent.addView(row)
        parent.addView(text("출처: free-exercise-db · 실제 기구 모델이나 운동 변형은 다를 수 있습니다."))
    }

    private fun load(context: Context, source: String, index: Int, target: ImageView) {
        val file = File(context.cacheDir, "exercise_photo_${source}_${index}.jpg")
        val handler = Handler(Looper.getMainLooper())
        Thread {
            try {
                if (!file.exists() || file.length() == 0L) {
                    val url = URL("https://raw.githubusercontent.com/yuhonas/free-exercise-db/main/exercises/${source}/${index}.jpg")
                    val conn = url.openConnection() as HttpURLConnection
                    conn.connectTimeout = 7000
                    conn.readTimeout = 7000
                    try {
                        if (conn.responseCode != 200) return@Thread
                        conn.inputStream.use { input -> file.outputStream().use { output -> input.copyTo(output) } }
                    } finally { conn.disconnect() }
                }
                val bitmap: Bitmap? = BitmapFactory.decodeFile(file.absolutePath)
                if (bitmap != null) handler.post { target.setImageBitmap(bitmap) }
            } catch (_: Exception) {
                // Offline: retain the bundled movement schematic as a fallback.
            }
        }.start()
    }
}
