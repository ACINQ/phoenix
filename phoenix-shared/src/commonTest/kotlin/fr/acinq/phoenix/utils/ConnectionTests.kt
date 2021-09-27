/*
 * Copyright 2021 ACINQ SAS
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package fr.acinq.phoenix.utils

import fr.acinq.lightning.utils.Connection
import plus
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