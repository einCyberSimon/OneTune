package de.eincybersimon.onetune

import java.io.File
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class MusicLinksVectorTest {
    private val vectors = Json.parseToJsonElement(File("../testvectors/links.json").readText()).jsonObject

    @Test
    fun validLinksParse() {
        for (vector in vectors.getValue("valid").jsonArray) {
            val fields = vector.jsonObject
            val url = fields.getValue("url").jsonPrimitive.content
            val expected = TrackRef(fields.getValue("platform").jsonPrimitive.content, fields.getValue("id").jsonPrimitive.content)
            assertEquals(expected, MusicLinks.parse(url), url)
        }
    }

    @Test
    fun invalidLinksAreRejected() {
        for (url in vectors.getValue("invalid").jsonArray) {
            assertNull(MusicLinks.parse(url.jsonPrimitive.content), url.toString())
        }
    }

    @Test
    fun findsLinkInSharedText() {
        val text = "Go Back by Velogic\nhttps://music.apple.com/us/album/go-back/617154241?i=617154366\n"
        assertEquals(TrackRef("apple", "617154366"), MusicLinks.findIn(text))
        assertNull(MusicLinks.findIn("no link here"))
    }

    @Test
    fun findsTidalLinkInShareText() {
        val text = "Listen to Go Back on TIDAL\nhttps://tidal.com/track/12345678/u"
        assertEquals(TrackRef("tidal", "12345678"), MusicLinks.findIn(text))
    }

    @Test
    fun buildsShareUrl() {
        assertEquals("https://example.org/tidal/track/1", MusicLinks.shareUrl("example.org", TrackRef("tidal", "1")))
    }

    @Test
    fun parsesSharePath() {
        assertEquals(TrackRef("apple", "617154366"), MusicLinks.parseSharePath("/apple/track/617154366"))
        assertNull(MusicLinks.parseSharePath("/evil/track/1"))
        assertNull(MusicLinks.parseSharePath("/apple/track/1/../x"))
    }
}
