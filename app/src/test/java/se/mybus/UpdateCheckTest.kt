package se.mybus

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateCheckTest {

    @Test
    fun `newer versions are found by number, not as text`() {
        assertTrue(UpdateCheck.isNewer("1.3.0", "1.2.0"))
        assertTrue(UpdateCheck.isNewer("1.10.0", "1.9.2"))
        assertTrue(UpdateCheck.isNewer("2.0", "1.9.9"))
        assertTrue(UpdateCheck.isNewer("1.2.1", "1.2"))
    }

    @Test
    fun `the same or an older version is not offered`() {
        assertFalse(UpdateCheck.isNewer("1.2.0", "1.2.0"))
        assertFalse(UpdateCheck.isNewer("1.2", "1.2.0"))
        assertFalse(UpdateCheck.isNewer("1.1.0", "1.2.0"))
    }

    @Test
    fun `a leading v and a suffix are ignored`() {
        assertTrue(UpdateCheck.isNewer("v1.3.0", "1.2.0"))
        assertFalse(UpdateCheck.isNewer("v1.2.0", "1.2.0"))
        assertFalse(UpdateCheck.isNewer("1.2.0-beta", "1.2.0"))
    }

    @Test
    fun `a tag that isn't a version is never offered`() {
        assertFalse(UpdateCheck.isNewer("latest", "1.2.0"))
        assertFalse(UpdateCheck.isNewer("", "1.2.0"))
    }

    @Test
    fun `GitHub's latest release is read from its answer`() {
        val body = """{"tag_name": "v1.3.0", "html_url": "https://github.com/Qualmeru/MySL/releases/tag/v1.3.0", "name": "MySL 1.3.0"}"""
        val update = UpdateCheck.parseLatest(body)!!
        assertEquals("v1.3.0", update.tag)
        assertEquals("1.3.0", update.version)
        assertEquals("https://github.com/Qualmeru/MySL/releases/tag/v1.3.0", update.url)
    }

    @Test
    fun `an answer without a release gives nothing`() {
        assertNull(UpdateCheck.parseLatest("""{"message": "Not Found"}"""))
        assertNull(UpdateCheck.parseLatest("not json"))
    }
}
