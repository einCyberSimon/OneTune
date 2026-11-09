package de.eincybersimon.onetune

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ServerPreferenceTest {
    @Test
    fun acceptsBareHttpsOrigins() {
        assertEquals("https://example.org", ServerPreference.normalize("https://example.org"))
        assertEquals("https://example.org", ServerPreference.normalize("example.org"))
        assertEquals("https://example.org", ServerPreference.normalize("  https://example.org/  "))
        assertEquals("https://example.org:8443", ServerPreference.normalize("https://example.org:8443"))
        assertEquals("https://example.org", ServerPreference.normalize("HTTPS://Example.ORG"))
    }

    @Test
    fun rejectsEverythingElse() {
        val invalid = listOf(
            "",
            "   ",
            "http://example.org",
            "ftp://example.org",
            "https://example.org/path",
            "https://example.org?query=1",
            "https://example.org#fragment",
            "https://user@example.org",
            "https://user:secret@example.org",
            "https://",
            "https://exa mple.org",
            "https://exa_mple.org",
        )
        for (input in invalid) assertNull(ServerPreference.normalize(input), input)
    }
}
