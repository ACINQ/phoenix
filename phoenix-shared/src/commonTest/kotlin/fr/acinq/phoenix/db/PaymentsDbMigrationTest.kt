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

import app.cash.sqldelight.EnumColumnAdapter
import app.cash.sqldelight.db.SqlDriver
import fr.acinq.lightning.utils.currentTimestampMillis
import fr.acinq.phoenix.data.WalletPaymentFetchOptions
import fr.acinq.phoenix.runTest
import fracinqphoenixdb.Cloudkit_payments_metadata
import fracinqphoenixdb.Cloudkit_payments_queue
import fracinqphoenixdb.Link_lightning_outgoing_payment_parts
import fracinqphoenixdb.On_chain_txs
import fracinqphoenixdb.Payments_incoming
import fracinqphoenixdb.Payments_metadata
import fracinqphoenixdb.Payments_outgoing
import kotlin.test.Test
import kotlin.test.assertEquals


class PaymentsDbMigrationTest {

    @Test
    fun `read v10 db`() = runTest {
        val driver = testPaymentsDriverFromFile()
        val paymentsDb = SqlitePaymentsDb(driver, PaymentsDatabase(
                driver = driver,
                payments_incomingAdapter = Payments_incoming.Adapter(UUIDAdapter, ByteVector32Adapter, TxIdAdapter, IncomingPaymentAdapter),
                payments_outgoingAdapter = Payments_outgoing.Adapter(UUIDAdapter, ByteVector32Adapter, TxIdAdapter, OutgoingPaymentAdapter),
                link_lightning_outgoing_payment_partsAdapter = Link_lightning_outgoing_payment_parts.Adapter(UUIDAdapter, UUIDAdapter),
                on_chain_txsAdapter = On_chain_txs.Adapter(UUIDAdapter, TxIdAdapter),
                payments_metadataAdapter = Payments_metadata.Adapter(UUIDAdapter, EnumColumnAdapter(), EnumColumnAdapter(), EnumColumnAdapter()),
                cloudkit_payments_queueAdapter = Cloudkit_payments_queue.Adapter(UUIDAdapter),
                cloudkit_payments_metadataAdapter = Cloudkit_payments_metadata.Adapter(UUIDAdapter),
            ),
            currencyManager = null)
        val payments = paymentsDb.database.paymentsQueries.list(Long.MAX_VALUE, 0).executeAsList()

        assertEquals(648, payments.size)
    }
}

expect fun testPaymentsDriverFromFile(): SqlDriver