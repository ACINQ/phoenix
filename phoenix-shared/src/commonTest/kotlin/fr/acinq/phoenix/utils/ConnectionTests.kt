package fr.acinq.phoenix.utils

import fr.acinq.eklair.utils.Connection
import kotlin.test.Test
import kotlin.test.assertEquals

class ConnectionTests {
    @Test
    fun connectionPlusOperator() {
        assertEquals(Connection.ESTABLISHED, Connection.ESTABLISHED + Connection.ESTABLISHED)
        assertEquals(Connection.ESTABLISHING, Connection.ESTABLISHING + Connection.ESTABLISHING)
        assertEquals(Connection.CLOSED, Connection.CLOSED + Connection.CLOSED)

        assertEquals(Connection.ESTABLISHING, Connection.ESTABLISHED + Connection.ESTABLISHING)
        assertEquals(Connection.ESTABLISHING, Connection.ESTABLISHING + Connection.ESTABLISHED)
        assertEquals(Connection.ESTABLISHING, Connection.CLOSED + Connection.ESTABLISHING)
        assertEquals(Connection.ESTABLISHING, Connection.ESTABLISHING + Connection.CLOSED)

        assertEquals(Connection.CLOSED, Connection.ESTABLISHED + Connection.CLOSED)
        assertEquals(Connection.CLOSED, Connection.CLOSED + Connection.ESTABLISHED)
    }
}