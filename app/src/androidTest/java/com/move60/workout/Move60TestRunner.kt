package com.move60.workout

import android.content.Context
import android.os.Bundle
import androidx.test.runner.AndroidJUnitRunner

/**
 * Runner for the phone instrumentation tests. The first-run setup (docs/v65) is shown to a user with no history, which is
 * exactly the state of a clean emulator, so every existing test would land on it. This runner marks the setup as done before
 * any test starts, and likewise the one-time kcal notice; the tests that cover the setup itself launch the app with `move60_force_first_run` instead.
 */
class Move60TestRunner : AndroidJUnitRunner() {
    override fun onCreate(arguments: Bundle?) {
        targetContext.getSharedPreferences("move60_first_run_v1", Context.MODE_PRIVATE).edit().putBoolean("done", true).commit()
        // The kcal notice (docs/v66) would appear on the record screen of every test that already has history.
        targetContext.getSharedPreferences("move60_notices_v1", Context.MODE_PRIVATE).edit().putBoolean("kcal_hidden_seen", true).commit()
        super.onCreate(arguments)
    }
}
