package fr.acinq.phoenix.data.lnurl

import fr.acinq.lightning.utils.msat
import io.ktor.http.*
import kotlinx.serialization.json.*
import kotlin.test.*

class LnurlWithdrawTest {
    private val defaultLnurl = URLBuilder("https://lnurl.service.com/withdraw/token12345").build()

    @Test
    fun test_parseJson_can_read_valid() {
        val json = """
            {
                "tag":"withdrawRequest",
                "callback":"$defaultLnurl/callback",
                "k1":"whatever",
                "defaultDescription":"👍 lorem ipsum ç!@#$%^&*()_+';é`´",
                "minWithdrawable":123456789,
                "maxWithdrawable":200000000000,
                "some_field":123456
            }
        """.trimIndent().let { Json.parseToJsonElement(it).jsonObject }
        val lnurl = Lnurl.parseLnurlJson(Url("https://acinq.co"), json)

        assertIs<LnurlWithdraw>(lnurl)
        assertEquals(123_456_789.msat, lnurl.minWithdrawable)
        assertEquals(200_000_000_000.msat, lnurl.maxWithdrawable)
        assertEquals("\uD83D\uDC4D lorem ipsum ç!@#\$%^&*()_+';é`´", lnurl.defaultDescription)
        assertEquals("whatever", lnurl.k1)
        assertEquals(Url("$defaultLnurl/callback"), lnurl.callback)
        assertEquals(Url("https://acinq.co"), lnurl.initialUrl)
    }

    @Test
    fun test_parseJson_missing_minimum() {
        val json = """
            {
                "tag":"withdrawRequest",
                "callback":"$defaultLnurl/callback",
                "k1":"whatever",
                "defaultDescription":"",
                "maxWithdrawable":1234
            }
        """.trimIndent().let { Json.parseToJsonElement(it).jsonObject }
        val lnurl = Lnurl.parseLnurlJson(Url("https://acinq.co"), json)
        assertIs<LnurlWithdraw>(lnurl)
        assertEquals(0.msat, lnurl.minWithdrawable)
        assertEquals(1234.msat, lnurl.maxWithdrawable)
    }

    @Test
    fun test_parseJson_rejects_invalid_amount() {
        assertFailsWith<LnurlError.Withdraw.InvalidWithdrawalBounds> {
            val json = """
                {
                    "tag":"withdrawRequest",
                    "callback":"$defaultLnurl/callback",
                    "k1":"whatever",
                    "defaultDescription":"",
                    "minWithdrawable":1000,
                    "maxWithdrawable":256
                }
            """.trimIndent().let { Json.parseToJsonElement(it).jsonObject }
            Lnurl.parseLnurlJson(Url("https://acinq.co"), json)
        }
    }

    @Test
    fun test_parseJson_rejects_unknown_tag() {
        assertFailsWith<LnurlError.UnhandledTag> {
            val json = """
                {
                    "tag":"withdraw",
                    "callback":"$defaultLnurl/callback",
                    "k1":"whatever",
                    "defaultDescription":"",
                    "minWithdrawable":1,
                    "maxWithdrawable":2
                }
            """.trimIndent().let { Json.parseToJsonElement(it).jsonObject }
            Lnurl.parseLnurlJson(Url("https://acinq.co"), json)
        }
    }

    @Test
    fun test_parseJson_rejects_unsafe_callback() {
        assertFailsWith(LnurlError.UnsafeResource::class) {
            val json = """
                {
                    "tag":"withdrawRequest",
                    "callback":"http://lnurl.service.com/withdraw/token12345/callback",
                    "k1":"whatever",
                    "defaultDescription":"lipsum",
                    "minWithdrawable":123456789,
                    "maxWithdrawable":200000000000
                }
            """.trimIndent().let { Json.parseToJsonElement(it).jsonObject }
            Lnurl.parseLnurlJson(defaultLnurl, json)
        }
    }

    @Test
    fun test_parseJson_rejects_missing_callback() {
        assertFailsWith(LnurlError.MissingCallback::class) {
            val json = """
                {
                    "tag":"withdrawRequest",
                    "k1":"whatever",
                    "defaultDescription":"lipsum",
                    "minWithdrawable":123456789,
                    "maxWithdrawable":200000000000
                }
            """.trimIndent().let { Json.parseToJsonElement(it).jsonObject }
            Lnurl.parseLnurlJson(defaultLnurl, json)
        }
    }

    @Test
    fun test_extractLnurl_ignores_unknown_tag() {
        val lnurl = Lnurl.extractLnurl("$defaultLnurl?tag=withdraw")
        assertIs<Lnurl.Request>(lnurl)
    }
}
