package se.mybus

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

class StringsTest {

    private val languages = listOf(Swedish, English)

    @Test
    fun `every text is filled in for both languages`() {
        val getters = Strings::class.java.methods.filter {
            it.parameterCount == 0 && it.returnType == String::class.java && it.name.startsWith("get")
        }
        assertTrue("found only ${getters.size} texts", getters.size > 20)
        for (lang in languages) {
            for (g in getters) {
                val value = g.invoke(lang) as String
                assertFalse("${lang.javaClass.simpleName}.${g.name} is blank", value.isBlank())
            }
        }
    }

    @Test
    fun `translates SL's Nu but leaves times alone`() {
        assertEquals("Nu", Swedish.display("Nu"))
        assertEquals("Now", English.display("Nu"))
        assertEquals("3 min", English.display("3 min"))
        assertEquals("21:22", Swedish.display("21:22"))
    }

    @Test
    fun `plurals`() {
        assertEquals("1 riktning dold", Swedish.directionsHidden(1))
        assertEquals("2 riktningar dolda", Swedish.directionsHidden(2))
        assertEquals("1 line hidden", English.linesHidden(1))
        assertEquals("3 lines hidden", English.linesHidden(3))
    }

    @Test
    fun `distances`() {
        assertEquals("350 m", English.metresAway(350))
        assertEquals("1.2 km", English.metresAway(1234))
        assertEquals("1,2 km", Swedish.metresAway(1234))
    }

    @Test
    fun `no network at all says so`() {
        assertEquals(English.noInternet, friendlyError(UnknownHostException("transport.integration.sl.se"), English))
        // Wrapped somewhere down the cause chain.
        assertEquals(Swedish.noInternet, friendlyError(IOException("x", UnknownHostException()), Swedish))
    }

    @Test
    fun `timeouts and refusals don't claim there's no internet`() {
        assertEquals(English.connectionFailed, friendlyError(SocketTimeoutException(), English))
        assertEquals(English.connectionFailed, friendlyError(IOException("x", ConnectException()), English))
    }

    @Test
    fun `SL's quota refusal says to try later`() {
        assertEquals(English.slBusy, friendlyError(SlApiException("Quota has been exceeded", quota = true), English))
        assertEquals("Site not found", friendlyError(SlApiException("Site not found", quota = false), English))
    }

    @Test
    fun `anything else shows its own message, or its type`() {
        assertEquals("HTTP 500", friendlyError(IllegalStateException("HTTP 500"), English))
        assertEquals("IllegalStateException", friendlyError(IllegalStateException(), English))
    }
}
