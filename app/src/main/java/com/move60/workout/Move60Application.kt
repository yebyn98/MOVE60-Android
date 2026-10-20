package com.move60.workout

import android.app.Application

/** Installs crash diagnostics before the first Activity or timer Service runs. */
class Move60Application : Application() {
    override fun onCreate() {
        super.onCreate()
        CrashReporter.install(this)
    }
}
