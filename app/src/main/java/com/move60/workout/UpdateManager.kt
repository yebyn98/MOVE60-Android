package com.move60.workout

import android.app.Activity
import android.app.AlertDialog
import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import android.widget.Toast
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.Locale
import kotlin.concurrent.thread

/**
 * Reads a public, read-only update manifest. Never stores a GitHub token in the APK.
 * The private source repo is separate from the public binary distribution repo.
 */
class UpdateManager(private val activity: Activity) {
    companion object {
        private const val MANIFEST_URL = "https://raw.githubusercontent.com/yebyn98/MOVE60-Updates/main/latest.json"
        private const val MAX_MANIFEST_BYTES = 24 * 1024
        private const val CHECK_INTERVAL_MS = 12L * 60L * 60L * 1000L
    }

    private var receiver: BroadcastReceiver? = null

    fun check(manual: Boolean) {
        val prefs = activity.getSharedPreferences("move60_update", Context.MODE_PRIVATE)
        val now = System.currentTimeMillis()
        if (!manual && now - prefs.getLong("lastCheck", 0L) < CHECK_INTERVAL_MS) return
        prefs.edit().putLong("lastCheck", now).apply()
        if (manual) toast("최신 버전을 확인하고 있어요…")
        thread(name = "move60-update-check") {
            try {
                val connection = URL(MANIFEST_URL).openConnection() as HttpURLConnection
                connection.connectTimeout = 8000
                connection.readTimeout = 8000
                connection.instanceFollowRedirects = true
                connection.setRequestProperty("Cache-Control", "no-cache")
                try {
                    if (connection.responseCode == 404) {
                        if (manual) inform("공개 업데이트 저장소가 아직 연결되지 않았어요. 배포 연결 후 앱에서 바로 업데이트할 수 있어요.")
                        return@thread
                    }
                    if (connection.responseCode != 200) throw IllegalStateException("HTTP ${connection.responseCode}")
                    val source = connection.inputStream.use { input ->
                        val bytes = input.readBytes()
                        require(bytes.size <= MAX_MANIFEST_BYTES) { "Manifest size exceeded" }
                        bytes.toString(Charsets.UTF_8)
                    }
                    val manifest = JSONObject(source)
                    val versionCode = manifest.getLong("versionCode")
                    val versionName = manifest.optString("versionName", versionCode.toString())
                    val apkUrl = manifest.getString("apkUrl")
                    val digest = manifest.getString("sha256").lowercase(Locale.ROOT)
                    val url = Uri.parse(apkUrl)
                    require(url.scheme == "https") { "APK URL must use HTTPS" }
                    require(url.host in listOf("raw.githubusercontent.com", "github.com")) { "Untrusted APK host" }
                    require(digest.matches(Regex("[0-9a-f]{64}"))) { "Missing SHA-256 integrity hash" }
                    val packageInfo = activity.packageManager.getPackageInfo(activity.packageName, 0)
                    val current = if (Build.VERSION.SDK_INT >= 28) packageInfo.longVersionCode else packageInfo.versionCode.toLong()
                    activity.runOnUiThread {
                        if (activity.isFinishing || activity.isDestroyed) return@runOnUiThread
                        if (versionCode > current) {
                            AlertDialog.Builder(activity)
                                .setTitle("MOVE60 $versionName 업데이트")
                                .setMessage("새 버전이 준비됐어요. 운동 기록을 유지하면서 앱을 업데이트할까요? 설치는 안드로이드의 승인이 필요해요.")
                                .setNegativeButton("나중에", null)
                                .setPositiveButton("업데이트") { _, _ -> download(apkUrl, digest, versionName) }
                                .show()
                        } else if (manual) {
                            AlertDialog.Builder(activity).setTitle("최신 버전이에요")
                                .setMessage("현재 설치된 MOVE60이 최신 버전입니다.")
                                .setPositiveButton("확인", null).show()
                        }
                    }
                } finally {
                    connection.disconnect()
                }
            } catch (exception: Exception) {
                if (manual) inform("업데이트 서버에 연결할 수 없어요. 인터넷 연결 또는 공개 배포 설정을 확인해 주세요.")
            }
        }
    }

    private fun download(url: String, sha256: String, version: String) {
        // Android prevents silent APK installs; each install must receive user consent.
        if (Build.VERSION.SDK_INT >= 26 && !activity.packageManager.canRequestPackageInstalls()) {
            AlertDialog.Builder(activity)
                .setTitle("업데이트 설치 권한")
                .setMessage("MOVE60에서 새 버전을 설치할 수 있도록 '이 출처 허용'을 켜 주세요. 돌아온 뒤 업데이트를 다시 눌러 주세요.")
                .setNegativeButton("취소", null)
                .setPositiveButton("설정 열기") { _, _ ->
                    activity.startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${activity.packageName}")))
                }.show()
            return
        }
        try {
            val request = DownloadManager.Request(Uri.parse(url))
                .setTitle("MOVE60 $version 업데이트")
                .setDescription("업데이트 APK 다운로드 중")
                .setMimeType("application/vnd.android.package-archive")
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, "MOVE60-$version.apk")
            val manager = activity.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            val id = manager.enqueue(request)
            receiver?.let { try { activity.unregisterReceiver(it) } catch (_: Exception) {} }
            val listener = object : BroadcastReceiver() {
                override fun onReceive(context: Context?, intent: Intent?) {
                    if (intent?.action != DownloadManager.ACTION_DOWNLOAD_COMPLETE ||
                        intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1) != id) return
                    try { activity.unregisterReceiver(this) } catch (_: Exception) {}
                    receiver = null
                    thread(name = "move60-apk-check") {
                        val fileUri = manager.getUriForDownloadedFile(id)
                        if (fileUri == null) {
                            inform("다운로드에 실패했어요. 다운로드 알림을 확인해 주세요.")
                            return@thread
                        }
                        try {
                            val digest = MessageDigest.getInstance("SHA-256")
                            activity.contentResolver.openInputStream(fileUri).use { stream ->
                                requireNotNull(stream) { "Downloaded file inaccessible" }
                                val buffer = ByteArray(8192)
                                while (true) {
                                    val count = stream.read(buffer)
                                    if (count < 0) break
                                    digest.update(buffer, 0, count)
                                }
                            }
                            val actual = digest.digest().joinToString("") { "%02x".format(it) }
                            require(actual == sha256) { "SHA-256 mismatch" }
                            activity.runOnUiThread {
                                try {
                                    activity.startActivity(Intent(Intent.ACTION_VIEW).apply {
                                        setDataAndType(fileUri, "application/vnd.android.package-archive")
                                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                    })
                                } catch (e: Exception) {
                                    inform("다운로드는 완료됐어요. 파일 앱의 다운로드 폴더에서 MOVE60 APK를 열어 주세요.")
                                }
                            }
                        } catch (e: Exception) {
                            inform("APK 무결성을 확인하지 못해 설치를 중단했어요. 공개 배포 파일을 확인해 주세요.")
                        }
                    }
                }
            }
            receiver = listener
            if (Build.VERSION.SDK_INT >= 33) activity.registerReceiver(listener, IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE), Context.RECEIVER_EXPORTED)
            else activity.registerReceiver(listener, IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE))
            toast("다운로드를 시작했어요. 완료되면 설치 화면이 열려요.")
        } catch (exception: Exception) {
            inform("다운로드를 시작하지 못했어요. 인터넷 연결과 설치 권한을 확인해 주세요.")
        }
    }

    fun onDestroy() {
        receiver?.let { try { activity.unregisterReceiver(it) } catch (_: Exception) {} }
        receiver = null
    }

    private fun toast(message: String) = activity.runOnUiThread { if (!activity.isFinishing) Toast.makeText(activity, message, Toast.LENGTH_LONG).show() }
    private fun inform(message: String) = activity.runOnUiThread {
        if (!activity.isFinishing && !activity.isDestroyed) AlertDialog.Builder(activity)
            .setTitle("MOVE60 업데이트").setMessage(message).setPositiveButton("확인", null).show()
    }
}
