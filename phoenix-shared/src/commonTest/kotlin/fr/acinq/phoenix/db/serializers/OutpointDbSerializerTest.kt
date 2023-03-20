package fr.acinq.phoenix.db.serializers

import fr.acinq.bitcoin.OutPoint
import fr.acinq.lightning.Lightning.randomBytes32
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails

class OutpointDbDbSerializerTest {
    @Test
    fun serialize_outpoint() {
        val revTxId = randomBytes32()
        val index = 1L
        val outpoint = OutPoint(hash = revTxId, index = index)
        assertEquals("$revTxId:$index", outpoint.serializeForDb())
    }

    @Test
    fun deserialize_outpoint() {
        val revTxId = "4a5a424443bafe805517d54da251026c327de04a8f3509506b64d25e55b4ff60"
        val index = 12L
        val data = "$revTxId:$index"
        assertEquals(revTxId, OutPoint.deserializeFromDb(data).hash.toHex())
        assertEquals(index, OutPoint.deserializeFromDb(data).index)
    }

    @Test
    fun deserialize_outpoint_failure() {
        assertFails { OutPoint.deserializeFromDb("foobar:123") }
        assertFails { OutPoint.deserializeFromDb("${randomBytes32()}") }
        assertFails { OutPoint.deserializeFromDb("${randomBytes32()}::123") }
        assertFails { OutPoint.deserializeFromDb("${randomBytes32()}:abc") }
        assertFails { OutPoint.deserializeFromDb("${randomBytes32()}:") }
        assertFails { OutPoint.deserializeFromDb(":") }
        assertFails { OutPoint.deserializeFromDb("") }
    }
}