package fr.acinq.phoenix.data.lnurl

import fr.acinq.lightning.MilliSatoshi
import fr.acinq.lightning.utils.msat
import fr.acinq.phoenix.data.LNUrl
import io.ktor.http.*
import kotlinx.serialization.json.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class LNUrlWithdrawTest {
    val format = Json { ignoreUnknownKeys = true }

    private val defaultDesc = "Lorem ipsum dolor sit amet"
    private val defaultMin = 4000.msat
    private val defaultMax = 16000.msat
    private val defaultLnurl = URLBuilder("https://lnurl.fiatjaf.com/foobar").build()
    private val defaultCallback = URLBuilder("https://lnurl.fiatjaf.com/lnurl-withdraw/callback/6e667d407298a7381f4bb02b228e72b3b86c0666b0662f751d089e30bd729b18").build()
    private val defaultK1 = "36352c79b25544ce2cb8b7fccaaf591ed00ad42989614aafcc569ba3d384b1bb"
    private val defaultWithdraw = LNUrl.Withdraw(
        lnurl = defaultLnurl,
        callback = defaultCallback,
        k1 = defaultK1,
        defaultDescription = defaultDesc,
        minWithdrawable = defaultMin,
        maxWithdrawable = defaultMax,
    )

    private fun makeJson(
        tag: String? = "withdrawRequest",
        k1: String? = defaultK1,
        callback: String? = defaultCallback.toString(),
        min: MilliSatoshi? = defaultMin,
        max: MilliSatoshi? = defaultMax,
        description: String? = defaultDesc,
    ): JsonObject {
        val map = mutableMapOf("randomField" to JsonPrimitive("this field should be ignored"))
        tag?.let { map.put("tag", JsonPrimitive(it)) }
        k1?.let { map.put("k1", JsonPrimitive(it)) }
        callback?.let { map.put("callback", JsonPrimitive(it)) }
        min?.let { map.put("minWithdrawable", JsonPrimitive(it.msat)) }
        max?.let { map.put("maxWithdrawable", JsonPrimitive(it.msat)) }
        description?.let { map.put("defaultDescription", JsonPrimitive(it)) }
        return JsonObject(map)
    }

    @Test
    fun testJson_ok() {
        val url = LNUrl.parseLNUrlResponse(defaultLnurl, makeJson())
        assertTrue { url is LNUrl.Withdraw }
        assertEquals(defaultWithdraw, url)
    }

    @Test
    fun testJson_callback_unsafe() {
        assertFailsWith(LNUrl.Error.UnsafeCallback::class) {
            val json = makeJson(
                callback = "http://lnurl.fiatjaf.com/lnurl-withdraw/callback/6e667d407298a7381"
            )
            LNUrl.parseLNUrlResponse(defaultLnurl, json)
        }
    }

    @Test
    fun testJson_callback_missing() {
        assertFailsWith(LNUrl.Error.MissingCallback::class) {
            val json = makeJson(callback = null)
            LNUrl.parseLNUrlResponse(defaultLnurl, json)
        }
    }
}