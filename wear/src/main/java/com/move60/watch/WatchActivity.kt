package com.move60.watch

import android.app.Activity
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.graphics.Color
import android.view.Gravity
import android.widget.Chronometer
import android.widget.LinearLayout
import android.widget.TextView
import com.google.android.gms.wearable.DataClient
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.Wearable

/** Wear OS companion displays the current phone workout stage and countdown. */
class WatchActivity : Activity(), DataClient.OnDataChangedListener {
    private lateinit var title: TextView
    private lateinit var timer: Chronometer
    private lateinit var status: TextView
    private val handler = Handler(Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(18, 18, 18, 18)
            setBackgroundColor(Color.rgb(20, 29, 39))
        }
        title = TextView(this).apply { text = "MOVE60"; textSize = 20f; setTextColor(Color.rgb(180, 245, 110)); gravity = Gravity.CENTER }
        timer = Chronometer(this).apply { textSize = 32f; setTextColor(Color.WHITE); gravity = Gravity.CENTER; setCountDown(true) }
        status = TextView(this).apply { text = "휴대폰 운동 시작을 기다리는 중"; textSize = 12f; setTextColor(Color.WHITE); gravity = Gravity.CENTER }
        container.addView(title)
        container.addView(timer)
        container.addView(status)
        setContentView(container)
    }

    override fun onResume() {
        super.onResume()
        Wearable.getDataClient(this).addListener(this)
        Wearable.getDataClient(this).getDataItems().addOnSuccessListener { items ->
            try {
                for (item in items) if (item.uri.path == "/move60/workout") {
                    showState(DataMapItem.fromDataItem(item).dataMap.getBoolean("running"),
                        DataMapItem.fromDataItem(item).dataMap.getString("stage") ?: "",
                        DataMapItem.fromDataItem(item).dataMap.getLong("deadline"))
                }
            } finally { items.release() }
        }
    }
    override fun onPause() {
        Wearable.getDataClient(this).removeListener(this)
        super.onPause()
    }
    override fun onDataChanged(events: DataEventBuffer) {
        for (event in events) {
            if (event.type == com.google.android.gms.wearable.DataEvent.TYPE_CHANGED &&
                event.dataItem.uri.path == "/move60/workout") {
                val data = DataMapItem.fromDataItem(event.dataItem).dataMap
                runOnUiThread { showState(data.getBoolean("running"), data.getString("stage") ?: "", data.getLong("deadline")) }
            }
        }
    }
    private fun showState(running: Boolean, stage: String, deadline: Long) {
        title.text = if (running) stage else "MOVE60"
        status.text = if (running) "휴대폰 운동과 동기화됨" else "운동 대기 중"
        timer.stop()
        if (running) {
            timer.base = SystemClock.elapsedRealtime() + (deadline - System.currentTimeMillis()).coerceAtLeast(0L)
            timer.start()
        } else timer.text = "00:00"
    }
}
