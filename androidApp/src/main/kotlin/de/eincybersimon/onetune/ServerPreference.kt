package de.eincybersimon.onetune

import android.content.Context
import androidx.core.content.edit
import java.net.URI

/** The resolver server: the build's default unless the user points the app at a self-hosted one. */
class ServerPreference(context: Context) {
    private val preferences = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    /** Normalized `https://host[:port]`, or null for the default server. */
    var custom: String?
        get() = preferences.getString(KEY, null)
        set(value) = preferences.edit { putString(KEY, value) }

    val baseUrl: String get() = custom ?: BuildConfig.RESOLVER_BASE_URL

    /** Host (and port) used in the links this app shares. */
    val linkHost: String get() = custom?.removePrefix("https://") ?: BuildConfig.LINK_HOST

    companion object {
        private const val KEY = "server_url"

        /** Returns `https://host[:port]` for a bare origin (scheme optional), or null if the input is not one. */
        fun normalize(input: String): String? {
            val text = input.trim().trimEnd('/')
            val uri = runCatching { URI(if ("://" in text) text else "https://$text") }.getOrNull() ?: return null
            val isBareHttpsOrigin = uri.scheme.equals("https", ignoreCase = true) &&
                !uri.host.isNullOrEmpty() &&
                uri.rawUserInfo == null &&
                uri.rawPath.isNullOrEmpty() &&
                uri.rawQuery == null &&
                uri.rawFragment == null
            return if (isBareHttpsOrigin) "https://${uri.rawAuthority}".lowercase() else null
        }
    }
}
