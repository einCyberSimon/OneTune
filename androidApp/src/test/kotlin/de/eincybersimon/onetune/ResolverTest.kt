package de.eincybersimon.onetune

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import org.json.JSONException

class ResolverTest {
    private fun response(vararg links: String) =
        """{"entity":{"title":"Go Back","artist":"Velogic"},"links":[${links.joinToString(",")}]}"""

    private fun link(platform: String, url: String, search: Boolean? = null) =
        """{"platform":"$platform","url":"$url"${search?.let { ""","search":$it""" }.orEmpty()}}"""

    @Test
    fun buildsUrlFromBaseUrlAndTrack() {
        assertEquals(
            "https://example.org/apple/track/42",
            Resolver.url("https://example.org", TrackRef("apple", "42")),
        )
    }

    @Test
    fun parsesEntityAndLinks() {
        val track = Resolver.parse(
            response(
                link("spotify", "https://open.spotify.com/track/1"),
                link("tidal", "https://tidal.com/search?q=x", search = true),
            ),
        )
        assertEquals("Go Back", track.title)
        assertEquals("Velogic", track.artist)
        assertEquals(
            listOf(
                ServiceLink(StreamingService.Spotify, "https://open.spotify.com/track/1", isSearch = false),
                ServiceLink(StreamingService.Tidal, "https://tidal.com/search?q=x", isSearch = true),
            ),
            track.links,
        )
    }

    @Test
    fun dropsLinksThatLeaveTheServicesHost() {
        val offending = listOf(
            "https://evil.example/track/1",
            "https://open.spotify.com.evil.example/track/1",
            "https://evil.example/open.spotify.com/track/1",
            "https://open.spotify.com@evil.example/track/1",
            "http://open.spotify.com/track/1",
            "javascript:alert(1)",
            "open.spotify.com/track/1",
        )
        for (url in offending) {
            assertEquals(emptyList(), Resolver.parse(response(link("spotify", url))).links, url)
        }
    }

    @Test
    fun acceptsHostCaseInsensitivelyAndWithPort() {
        val track = Resolver.parse(response(link("spotify", "https://OPEN.Spotify.com:443/track/1")))
        assertEquals(1, track.links.size)
    }

    @Test
    fun dropsLinksToUnknownServices() {
        assertEquals(emptyList(), Resolver.parse(response(link("napster", "https://napster.com/track/1"))).links)
    }

    @Test
    fun rejectsMalformedJson() {
        assertFailsWith<JSONException> { Resolver.parse("not json") }
        assertFailsWith<JSONException> { Resolver.parse("""{"links":[]}""") }
    }
}
