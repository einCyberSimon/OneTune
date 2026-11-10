package de.eincybersimon.onetune

import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL

data class ServiceLink(val service: StreamingService, val url: String, val isSearch: Boolean)

data class ResolvedTrack(val title: String, val artist: String, val links: List<ServiceLink>)

object Resolver {
    private const val MAX_RESPONSE_BYTES = 256 * 1024

    fun url(baseUrl: String, track: TrackRef) = "$baseUrl/${track.platform}/track/${track.id}"

    /** Blocks on the network. Returns null on any failure. */
    fun fetch(baseUrl: String, track: TrackRef): ResolvedTrack? = runCatching {
        val connection = (URL(url(baseUrl, track)).openConnection() as HttpURLConnection).apply {
            setRequestProperty("Accept", "application/json")
            instanceFollowRedirects = false
            connectTimeout = 8000
            readTimeout = 25000
        }
        try {
            if (connection.responseCode != HttpURLConnection.HTTP_OK) return@runCatching null
            parse(readLimited(connection) ?: return@runCatching null)
        } finally {
            connection.disconnect()
        }
    }.getOrNull()

    private fun readLimited(connection: HttpURLConnection): String? {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        connection.inputStream.use { input ->
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                if (output.size() + count > MAX_RESPONSE_BYTES) return null
                output.write(buffer, 0, count)
            }
        }
        return output.toString(Charsets.UTF_8.name())
    }

    // Drops links to unknown services and links that leave the service's own host.
    internal fun parse(json: String): ResolvedTrack {
        val root = JSONObject(json)
        val entity = root.getJSONObject("entity")
        val links = root.getJSONArray("links")
        val parsed = buildList {
            for (index in 0 until links.length()) {
                val item = links.getJSONObject(index)
                val service = StreamingService.fromId(item.getString("platform")) ?: continue
                val url = item.getString("url")
                if (hostOf(url) == service.webHost) add(
                    ServiceLink(
                        service,
                        url,
                        item.optBoolean("search", false)
                    )
                )
            }
        }
        return ResolvedTrack(entity.optString("title"), entity.optString("artist"), parsed)
    }

    private fun hostOf(url: String): String? {
        val parts = Regex("""^https://([^/?#:@]+)(?::\d+)?(?:[/?#]|$)""").find(url) ?: return null
        return parts.groupValues[1].lowercase()
    }
}
