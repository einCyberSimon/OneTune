package de.eincybersimon.onetune

data class TrackRef(val platform: String, val id: String)

/** [webHost] is the only host the resolver may send us to for this service. */
enum class StreamingService(val id: String, val label: String, val webHost: String) {
    Spotify("spotify", "Spotify", "open.spotify.com"),
    AppleMusic("apple", "Apple Music", "music.apple.com"),
    Tidal("tidal", "Tidal", "tidal.com"),
    Deezer("deezer", "Deezer", "www.deezer.com"),
    YouTubeMusic("ytmusic", "YouTube Music", "music.youtube.com");

    companion object {
        fun fromId(id: String?): StreamingService? = entries.firstOrNull { it.id == id }
    }
}

/**
 * Mirrors the server's parsers; both are tested against testvectors/links.json.
 */
object MusicLinks {
    private val urlPattern = Regex("""^(https?)://([^/?#:]+)(?::\d+)?(/[^?#]*)?(?:\?([^#]*))?""")
    private val anyUrl = Regex("""https?://\S+""")

    private val spotifyPath = Regex("""^/(?:intl-[a-z]{2}(?:-[a-zA-Z]+)?/)?(?:embed/)?track/([0-9A-Za-z]{22})/?$""")
    private val appleSongPath = Regex("""^/[a-z]{2}/song/(?:[^/]+/)?([0-9]+)/?$""")
    private val appleAlbumPath = Regex("""^/[a-z]{2}/album/[^/]+/[0-9]+/?$""")
    private val digits = Regex("""^[0-9]+$""")
    private val tidalPath = Regex("""^/(?:browse/)?(?:album/[0-9]+/)?track/([0-9]+)(?:/u)?/?$""")
    private val deezerPath = Regex("""^/(?:[a-z]{2}/)?track/([0-9]+)/?$""")
    private val youtubeVideoId = Regex("""^[A-Za-z0-9_-]{11}$""")
    private val sharePath = Regex("""^/(spotify|apple|tidal|deezer|ytmusic)/track/([A-Za-z0-9_-]{1,32})/?$""")

    fun parse(link: String): TrackRef? {
        val parts = urlPattern.find(link.trim())?.groupValues ?: return null
        val host = parts[2].lowercase()
        val path = parts[3]
        val query = parts[4]
        return when (host) {
            "open.spotify.com" -> spotifyPath.find(path)?.let { TrackRef("spotify", it.groupValues[1]) }
            "music.apple.com", "itunes.apple.com" -> parseApple(path, query)
            "tidal.com", "listen.tidal.com", "www.tidal.com" ->
                tidalPath.find(path)?.let { TrackRef("tidal", it.groupValues[1]) }
            "www.deezer.com", "deezer.com" -> deezerPath.find(path)?.let { TrackRef("deezer", it.groupValues[1]) }
            "music.youtube.com", "www.youtube.com", "youtube.com", "m.youtube.com" ->
                if (path == "/watch") youtube(queryParameter(query, "v")) else null
            "youtu.be" -> youtube(path.removePrefix("/"))
            else -> null
        }
    }

    /** Finds the first supported music link inside arbitrary shared text. */
    fun findIn(text: String): TrackRef? = anyUrl.findAll(text).firstNotNullOfOrNull { parse(it.value) }

    fun shareUrl(host: String, ref: TrackRef): String = "https://$host/${ref.platform}/track/${ref.id}"

    /** Reads the path of a link produced by [shareUrl]; the caller checks the host. */
    fun parseSharePath(path: String): TrackRef? =
        sharePath.find(path)?.let { TrackRef(it.groupValues[1], it.groupValues[2]) }

    private fun parseApple(path: String, query: String): TrackRef? {
        appleSongPath.find(path)?.let { return TrackRef("apple", it.groupValues[1]) }
        val trackId = queryParameter(query, "i")
        return if (appleAlbumPath.matches(path) && digits.matches(trackId)) TrackRef("apple", trackId) else null
    }

    private fun youtube(id: String): TrackRef? = if (youtubeVideoId.matches(id)) TrackRef("ytmusic", id) else null

    private fun queryParameter(query: String, name: String): String =
        query.split('&').firstNotNullOfOrNull { pair ->
            pair.substringBefore('=').takeIf { it == name }?.let { pair.substringAfter('=', "") }
        } ?: ""
}
