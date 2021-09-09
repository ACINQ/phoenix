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
    private val defaultCallback = URLBuilder("https://lnurl.fiatjaf.com/lnurl-withdraw/callback/6e667d407298a7381f4bb02b228e72b3b86c0666b0662f751d089e30bd729b18").build()
    private val defaultK1 = "36352c79b25544ce2cb8b7fccaaf591ed00ad42989614aafcc569ba3d384b1bb"
    private val defaultWithdraw = LNUrl.Withdraw(
        callback = defaultCallback,
        walletIdentifier = defaultK1,
        description = defaultDesc,
        minWithdrawable = defaultMin,
        maxWithdrawable = defaultMax,
    )

    private fun makeMetadata(
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
    fun testMetadata_ok() {
        val url = LNUrl.parseLNUrlMetadata(makeMetadata())
        assertTrue { url is LNUrl.Withdraw }
        assertEquals(defaultWithdraw, url)
    }

    @Test
    fun testMetadata_callback_unsafe() {
        assertFailsWith(LNUrl.Error.UnsafeCallback::class) {
            LNUrl.parseLNUrlMetadata(
                makeMetadata(callback = "http://lnurl.fiatjaf.com/lnurl-withdraw/callback/6e667d407298a7381")
            )
        }
    }

    @Test
    fun testMetadata_callback_missing() {
        assertFailsWith(LNUrl.Error.MissingCallback::class) {
            LNUrl.parseLNUrlMetadata(
                makeMetadata(callback = null)
            )
        }
    }
}