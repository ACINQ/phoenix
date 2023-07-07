@file:UseSerializers(
    OutpointSerializer::class,
)

package fr.acinq.phoenix.db.serializers

import fr.acinq.bitcoin.OutPoint
import fr.acinq.bitcoin.byteVector32
import fr.acinq.lightning.Lightning.randomBytes32
import fr.acinq.phoenix.db.serializers.v1.OutpointSerializer
import fr.acinq.secp256k1.Hex
import kotlinx.serialization.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.modules.SerializersModule
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails


class OutpointDbDbSerializerTest {
    val json = Json {
        ignoreUnknownKeys = false
        serializersModule = SerializersModule {
            contextual(OutPoint::class, OutpointSerializer())
        }
    }

    @Test
    fun serialize_outpoint() {
        val txHash1 = randomBytes32()
        val outpoint1 = OutPoint(txHash1, 1) //"1:$txId"
        assertEquals("\"$txHash1:1\"", json.encodeToString(outpoint1))
        val txHash2 = randomBytes32()
        val outpoint2 = OutPoint(txHash2, 999)
        assertEquals("[\"$txHash1:1\",\"$txHash2:999\"]", json.encodeToString(listOf(outpoint1, outpoint2)))
        println(json.encodeToString(listOf(outpoint1, outpoint2)))
    }

    @Test
    fun deserialize_outpoint() {
        val data = "[\"dba843431559d17371c1c10b3d2c1c1568ca0afb4ef6a4dd2b348fc54967fcc2:1\",\"33f088b296f0a3e56fa58df6b5d362a5202a6f5c0086980feb69de1e6a8618f5:999\"]"
        assertEquals(
            Hex.decode("dba843431559d17371c1c10b3d2c1c1568ca0afb4ef6a4dd2b348fc54967fcc2").byteVector32(),
            json.decodeFromString<List<OutPoint>>(data)[0].hash
        )
        assertEquals(
            999,
            json.decodeFromString<List<OutPoint>>(data)[1].index
        )
    }

    @Test
    fun deserialize_outpoint_failure() {
        assertFails { json.decodeFromString<OutPoint>("foobar:123") }
        assertFails { json.decodeFromString<OutPoint>("${randomBytes32()}") }
        assertFails { json.decodeFromString<OutPoint>("${randomBytes32()}::123") }
        assertFails { json.decodeFromString<OutPoint>("${randomBytes32()}:abc") }
        assertFails { json.decodeFromString<OutPoint>("${randomBytes32()}:") }
        assertFails { json.decodeFromString<OutPoint>(":") }
        assertFails { json.decodeFromString<OutPoint>("") }
    }
}