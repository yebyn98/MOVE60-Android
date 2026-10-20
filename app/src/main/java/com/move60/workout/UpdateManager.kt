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
        try {
            val p = activity.getSharedPreferences("move60_update", Context.MODE_PRIVATE)
            val existing = p.getLong("downloadId", -1L)
            if (existing>0 && p.getString("expectedHash", "")==sha256) {
                p.edit().putBoolean("installerShown",false).apply()
                observeDownload()
                resumePendingInstall()
                toast("기존 다운로드를 확인하고 있어요.")
                return
            }
            val request=DownloadManager.Request(Uri.parse(url))
                .setTitle("MOVE60 " + version + " 업데이트")
                .setDescription("앱 업데이트 파일을 다운로드합니다")
                .setMimeType("application/vnd.android.package-archive")
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                .setDestinationInExternalFilesDir(activity, Environment.DIRECTORY_DOWNLOADS,
                    "MOVE60-update-" + version + ".apk")
            val manager=activity.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            val id=manager.enqueue(request)
            p.edit().putLong("downloadId",id).putString("expectedHash",sha256)
                .putBoolean("installerShown",false).apply()
            observeDownload()
            resumePendingInstall()
            toast("다운로드를 시작했어요. 완료되면 설치를 안내할게요.")
        } catch (_: Exception) {
            inform("업데이트 다운로드를 시작하지 못했어요.")
        }
    }

    private var verifying=false
    private var permissionDialogShown=false
    private fun observeDownload() {
        if(receiver!=null)return
        val listener=object:BroadcastReceiver() {
            override fun onReceive(context:Context?,intent:Intent?) {
                val p=activity.getSharedPreferences("move60_update",Context.MODE_PRIVATE)
                if(intent?.action==DownloadManager.ACTION_DOWNLOAD_COMPLETE
                    && intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID,-1L)==p.getLong("downloadId",-1L)) {
                    try { resumePendingInstall() }
                    catch (e: Exception) { CrashReporter.log(activity, "다운로드 콜백: " + e.javaClass.simpleName + ": " + e.message) }
                }
            }
        }
        try {
            if(Build.VERSION.SDK_INT>=33)
                activity.registerReceiver(listener,IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE),Context.RECEIVER_EXPORTED)
            else activity.registerReceiver(listener,IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE))
            receiver=listener
        }catch(_:Exception){}
    }

    /** Allows downloading in-app, then opening the system installer after user consent. */
    fun resumePendingInstall() {
        val p=activity.getSharedPreferences("move60_update",Context.MODE_PRIVATE)
        val id=p.getLong("downloadId",-1L)
        val expected=p.getString("expectedHash",null)
        if(id<0||expected.isNullOrBlank()||verifying)return
        val dm=activity.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val status = try {
            val query = dm.query(DownloadManager.Query().setFilterById(id)) ?: return
            query.use { if (it.moveToFirst()) it.getInt(it.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
                else DownloadManager.STATUS_FAILED }
        } catch (e: Exception) {
            CrashReporter.log(activity, "다운로드 상태 조회: " + e.javaClass.simpleName + ": " + e.message)
            p.edit().remove("downloadId").remove("expectedHash").remove("installerShown").apply()
            return
        }
        if(status==DownloadManager.STATUS_FAILED) {
            p.edit().remove("downloadId").remove("expectedHash").remove("installerShown").apply()
            inform("다운로드에 실패했어요. 다시 업데이트를 눌러 주세요.")
            return
        }
        if(status!=DownloadManager.STATUS_SUCCESSFUL){observeDownload();return}
        if(p.getBoolean("installerShown",false))return
        if(Build.VERSION.SDK_INT>=26 && !activity.packageManager.canRequestPackageInstalls()){
            if(permissionDialogShown)return
            permissionDialogShown=true
            AlertDialog.Builder(activity)
                .setTitle("설치 권한 안내")
                .setMessage("다운로드 완료! MOVE60의 '이 출처 허용'을 켜고 돌아오면 설치를 이어갈게요.")
                .setNegativeButton("나중에"){_,_->permissionDialogShown=false}
                .setPositiveButton("설정 열기"){_,_->
                    try{activity.startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                        Uri.parse("package:"+activity.packageName)))}
                    catch(_:Exception){permissionDialogShown=false;inform("설정에서 MOVE60에 앱 설치 권한을 허용해 주세요.")}
                }.show()
            return
        }
        permissionDialogShown=false
        verifying=true
        thread(name="move60-verify-download"){
            try{
                val uri=dm.getUriForDownloadedFile(id)?:error("Missing APK")
                val hash=MessageDigest.getInstance("SHA-256")
                activity.contentResolver.openInputStream(uri).use{input->
                    requireNotNull(input)
                    val buffer=ByteArray(8192)
                    while(true){val n=input.read(buffer);if(n<0)break;hash.update(buffer,0,n)}
                }
                val actual=hash.digest().joinToString(""){"%02x".format(it)}
                require(actual==expected){"Hash mismatch"}
                activity.runOnUiThread{
                    verifying=false
                    if(activity.isDestroyed||activity.isFinishing)return@runOnUiThread
                    try{
                        p.edit().putBoolean("installerShown",true).apply()
                        activity.startActivity(Intent(Intent.ACTION_VIEW).apply{
                            setDataAndType(uri,"application/vnd.android.package-archive")
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        })
                    }catch(_:Exception){
                        p.edit().putBoolean("installerShown",false).apply()
                        inform("설치 화면을 열지 못했어요. 업데이트를 다시 눌러 주세요.")
                    }
                }
            }catch(_:Exception){
                activity.runOnUiThread{
                    verifying=false
                    p.edit().remove("downloadId").remove("expectedHash").remove("installerShown").apply()
                    inform("다운로드 파일 무결성 확인에 실패했어요. 설치를 중단했어요.")
                }
            }
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
