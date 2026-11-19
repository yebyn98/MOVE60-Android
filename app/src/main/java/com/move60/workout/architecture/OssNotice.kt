package com.move60.workout.architecture

import android.content.Context
import org.json.JSONObject

data class OssLibrary(val name: String, val version: String, val licenses: List<String>)
data class OssNotice(
    val scope: String,
    val libraries: List<OssLibrary>,
    val auditedCount: Int = 0,
    val unresolvedCount: Int = 0,
    val legalNoticesVerified: Boolean = false
)

/**
 * Reads `assets/oss_licenses.json`, written by `scripts/gen-oss-notice.py` from each library's published POM. Only licenses the POM
 * states are in the file; an unreadable or empty file is reported as "unavailable", never shown as an empty-but-complete list.
 */
object OssNoticeReader {
    fun parse(json: String): OssNotice? = try {
        val root = JSONObject(json)
        val libs = root.getJSONArray("libraries")
        val list = (0 until libs.length()).map { i ->
            val o = libs.getJSONObject(i)
            val lic = o.getJSONArray("licenses")
            OssLibrary("${o.getString("group")}:${o.getString("artifact")}", o.getString("version"),
                (0 until lic.length()).mapNotNull { lic.getJSONObject(it).optString("name").takeIf { n -> n.isNotBlank() } })
        }
        if (list.isEmpty() || list.any { it.licenses.isEmpty() }) null else {
            val audit = root.optJSONObject("audit")
            val complete = audit != null && listOf(
                "releaseGraphReverified", "licenseTextsVerified", "copyrightVerified",
                "noticesVerified", "operatorApproved"
            ).all { audit.optBoolean(it, false) }
            OssNotice(
                scope = root.optString("scope"),
                libraries = list,
                auditedCount = audit?.optInt("expectedRuntimeModules", 0) ?: list.size,
                unresolvedCount = audit?.optJSONArray("unresolved")?.length() ?: 0,
                legalNoticesVerified = complete
            )
        }
    } catch (_: Exception) { null }

    fun load(context: Context): OssNotice? = try {
        parse(context.assets.open("oss_licenses.json").bufferedReader().use { it.readText() })
    } catch (_: Exception) { null }
}
