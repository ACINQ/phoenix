/*
 * Copyright 2024 ACINQ SAS
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

package fr.acinq.phoenix.db

import app.cash.sqldelight.db.SqlDriver
import fr.acinq.phoenix.runTest
import fr.acinq.phoenix.utils.testLoggerFactory
import kotlin.test.Test
import kotlin.test.assertEquals


class PaymentsDbMigrationTest {

    @Test
    fun `read v10 db`() = runTest {
        val paymentsDb = SqlitePaymentsDb(testLoggerFactory, testPaymentsDriverFromFile(), currencyManager = null)
        val payments = paymentsDb.listRangeSuccessfulPaymentsOrder(0, Long.MAX_VALUE, 1000, 0)
        assertEquals(648, payments.size)
    }
}

expect fun testPaymentsDriverFromFile(): SqlDriver