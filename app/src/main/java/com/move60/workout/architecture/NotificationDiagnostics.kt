package com.move60.workout.architecture

import android.Manifest
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import com.move60.workout.WorkoutService

/** Plain-text status shown by the settings "알림 설정 · 진단" dialog. */
fun notificationDiagnostics(context: Context, workoutRunning: Boolean): String {
    val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    val channel = manager.getNotificationChannel(WorkoutService.CHANNEL_PROGRESS)
    val allowed = manager.areNotificationsEnabled() &&
        (channel == null || channel.importance != NotificationManager.IMPORTANCE_NONE) &&
        (Build.VERSION.SDK_INT < 33 ||
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED)
    val lastError = context.getSharedPreferences("move60_service_diagnostics", Context.MODE_PRIVATE)
        .getString("latest_error", null)
    val actualActive = try {
        manager.activeNotifications.any { it.id == 601 && it.packageName == context.packageName }
    } catch (_: Exception) { false }
    val cardStatus = context.getSharedPreferences("timer", Context.MODE_PRIVATE)
        .getString("card_status", "아직 운동 알림 게시 기록 없음")
    return (if (allowed) "알림 권한: 허용" else "알림 권한: 확인 필요") +
        "\n운동 알림 채널: " + (if (channel == null) "운동 시작 시 생성"
            else if (channel.importance == NotificationManager.IMPORTANCE_NONE) "차단됨" else "허용됨") +
        "\n운동 상태: " + (if (workoutRunning) "진행 중" else "대기 또는 정지") +
        "\n실제 알림 게시 여부: " + (if (actualActive) "게시됨" else "게시되지 않음") +
        "\n카드형 알림 상태: " + cardStatus +
        "\n잠금화면 표시: 기기 설정에서도 '알림 내용 표시'를 허용해야 해요" +
        (if (lastError == null) "" else "\n최근 알림 오류: $lastError") +
        "\n\n운동 시작 버튼을 누른 상태에서만 진행 알림이 표시돼요."
}

fun openNotificationSettings(context: Context) {
    try {
        context.startActivity(Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)
            .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
            .putExtra(Settings.EXTRA_CHANNEL_ID, WorkoutService.CHANNEL_PROGRESS))
    } catch (_: Exception) {
        context.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
            .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName))
    }
}
