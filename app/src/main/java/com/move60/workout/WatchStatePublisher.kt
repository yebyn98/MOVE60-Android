package com.move60.workout

import android.content.Context
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable

object WatchStatePublisher {
    fun publish(context: Context, running: Boolean, stage: String, deadline: Long) {
        val request = PutDataMapRequest.create("/move60/workout")
        request.dataMap.putBoolean("running", running)
        request.dataMap.putString("stage", stage)
        request.dataMap.putLong("deadline", deadline)
        request.dataMap.putLong("updated", System.currentTimeMillis())
        Wearable.getDataClient(context).putDataItem(request.asPutDataRequest().setUrgent())
    }
}
