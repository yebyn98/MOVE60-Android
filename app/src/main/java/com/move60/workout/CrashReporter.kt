package com.move60.workout
import android.app.Activity
import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import java.util.Date

object CrashReporter {
    private const val PREF = "move60_crash_report"
    private const val KEY = "last_crash"
    @Volatile private var installed = false

    fun install(context: Context) {
        if (installed) return
        installed = true
        val app = context.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                val msg = Date().toString() + " · " + thread.name + "\n" + throwable.stackTraceToString().take(12000)
                app.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit().putString(KEY, msg).commit()
            } catch (_: Throwable) {}
            previous?.uncaughtException(thread, throwable)
        }
    }

    fun log(context: Context, message: String) {
        try { context.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit()
            .putString(KEY, Date().toString() + " · recovered error\n" + message).apply() } catch (_: Exception) {}
    }

    fun offerLastCrash(activity: Activity) {
        val prefs = activity.getSharedPreferences(PREF, Context.MODE_PRIVATE)
        val detail = prefs.getString(KEY, null) ?: return
        if (activity.isFinishing || activity.isDestroyed) return
        AlertDialog.Builder(activity).setTitle("지난 실행 중 오류 기록")
            .setMessage("MOVE60에서 오류가 감지됐어요. 내용을 복사하면 원인을 확인할 수 있어요.")
            .setNegativeButton("나중에", null)
            .setPositiveButton("오류 내용 복사") { _, _ -> copy(activity, detail); prefs.edit().remove(KEY).apply() }.show()
    }

    fun showDiagnostics(activity: Activity) {
        val detail = activity.getSharedPreferences(PREF, Context.MODE_PRIVATE).getString(KEY, null)
        AlertDialog.Builder(activity).setTitle("MOVE60 오류 기록")
            .setMessage(detail ?: "저장된 오류 기록이 없어요.")
            .setNegativeButton("닫기", null)
            .setPositiveButton("복사") { _, _ -> if (detail != null) copy(activity, detail) }.show()
    }

    private fun copy(context: Context, value: String) {
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("MOVE60 diagnostic", value))
    }
}
