package com.move60.workout.architecture

import android.content.Context

/**
 * Offline copies of the standardized SPDX license templates bundled in assets.
 *
 * They are reference copies only. For MIT/BSD, the SPDX text contains
 * placeholder copyright owners; these do NOT replace each dependency's exact
 * copyright, NOTICE or redistribution obligations. Android SDK terms are
 * intentionally outside this OSS list and are not included here.
 */
object OssLicenseReference {
    val knownIds: List<String> = listOf("Apache-2.0", "MIT", "BSD-3-Clause")

    fun read(context: Context, licenseId: String): String? {
        if (licenseId !in knownIds) return null
        return try {
            context.assets.open("oss/license-texts/$licenseId.txt")
                .bufferedReader(Charsets.UTF_8).use { it.readText() }
                .takeIf { it.isNotBlank() }
        } catch (_: Exception) {
            null
        }
    }
}
