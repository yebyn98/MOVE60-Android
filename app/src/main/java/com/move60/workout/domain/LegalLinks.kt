package com.move60.workout.domain

import java.net.URI

/**
 * The privacy-policy link is shown only when the operator has configured a real, working-looking HTTPS address
 * (`res/values/legal.xml`). An empty, placeholder, example or non-HTTPS value yields null and no menu entry is shown: the app never
 * links to a made-up page. A syntactically valid URL is NOT proof that it is live; that check stays with the operator (docs/v67).
 */
object PrivacyPolicyLink {
    private val BLOCKED_HOSTS = setOf("localhost", "example.com", "example.org", "example.net", "test.com", "domain.com", "yourdomain.com")

    fun validOrNull(raw: String?): String? {
        val text = raw?.trim().orEmpty()
        if (text.isEmpty() || text.any { it.isWhitespace() }) return null
        val uri = try { URI(text) } catch (_: Exception) { return null }
        val host = uri.host?.lowercase() ?: return null
        if (uri.scheme != "https" || !host.contains('.') || host in BLOCKED_HOSTS) return null
        if (BLOCKED_HOSTS.any { host.endsWith(".$it") } || host.endsWith(".invalid") || host.endsWith(".test") || host.endsWith(".local")) return null
        if (host.all { it.isDigit() || it == '.' }) return null          // a bare IP address is not a policy page
        return text
    }
}
