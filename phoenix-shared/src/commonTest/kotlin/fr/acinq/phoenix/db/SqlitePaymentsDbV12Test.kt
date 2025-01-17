/*
 * Copyright 2025 ACINQ SAS
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

import fr.acinq.bitcoin.ByteVector32
import fr.acinq.bitcoin.OutPoint
import fr.acinq.bitcoin.TxId
import fr.acinq.lightning.db.Bolt11IncomingPayment
import fr.acinq.lightning.db.Bolt12IncomingPayment
import fr.acinq.lightning.db.ChannelCloseOutgoingPayment
import fr.acinq.lightning.db.InboundLiquidityOutgoingPayment
import fr.acinq.lightning.db.LightningOutgoingPayment
import fr.acinq.lightning.db.NewChannelIncomingPayment
import fr.acinq.lightning.db.SpliceCpfpOutgoingPayment
import fr.acinq.lightning.db.SpliceOutgoingPayment
import fr.acinq.lightning.serialization.payment.Serialization
import fr.acinq.lightning.utils.UUID
import fr.acinq.lightning.utils.msat
import fr.acinq.lightning.utils.sat
import fr.acinq.lightning.wire.LiquidityAds
import fr.acinq.phoenix.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class SqlitePaymentsDbV12Test {

    @Test
    fun `read v12 database`() = runTest {
        val driver = testPaymentsDriverFromResource("sampledbs/v12/payments-testnet-c26917df05332317c66a54c2e575c0f85dbae423.sqlite")
        val paymentsDb = createSqlitePaymentsDb(driver, currencyManager = null)

        val payments = paymentsDb.database.paymentsQueries.list(limit = Long.MAX_VALUE, offset = 0)
            .executeAsList()
            .map { Serialization.deserialize(it.data_).getOrThrow() }

        assertEquals(12, payments.size)
        assertIs<ChannelCloseOutgoingPayment>(payments[0])
        assertIs<Bolt12IncomingPayment>(payments[1])
        assertIs<InboundLiquidityOutgoingPayment>(payments[2])
        assertIs<Bolt11IncomingPayment>(payments[3])
        assertIs<LightningOutgoingPayment>(payments[4])
        assertIs<LightningOutgoingPayment>(payments[5])
        assertIs<LightningOutgoingPayment>(payments[6])
        assertIs<LightningOutgoingPayment>(payments[7])
        assertIs<SpliceCpfpOutgoingPayment>(payments[8])
        assertIs<SpliceOutgoingPayment>(payments[9])
        assertIs<NewChannelIncomingPayment>(payments[10])
        assertIs<InboundLiquidityOutgoingPayment>(payments[11])

        val oldestCompletedPayment = paymentsDb.getOldestCompletedTimestamp()
        assertEquals(1736964335587L, oldestCompletedPayment)

        val paymentsForTx = paymentsDb.listPaymentsForTxId(TxId("336207f86415d8fd5dad3c10163803d032be0d167eed53a6b31697e687b46d1f"))
        assertEquals(
            listOf(
                NewChannelIncomingPayment(
                    id = UUID.fromString("4049002b-0a3b-4ad4-af2b-707701e87fda"),
                    amountReceived = 698316000.msat, serviceFee = 0.msat, miningFee = 413.sat,
                    channelId = ByteVector32.fromValidHex("b858ecdb13a382370445cefcec602496597ead2a9957cce5a8ae7ca0db8a904c"),
                    txId = TxId("336207f86415d8fd5dad3c10163803d032be0d167eed53a6b31697e687b46d1f"),
                    localInputs = setOf(
                        OutPoint(TxId("93e4aa81ca2fe096c64a03f811e437b39e2b636e95df5201ef525e69d65f6d69"), 0),
                        OutPoint(TxId("90f81f71dc2eba820b606ffabc769302cfb748e169ed7e84796612b55d1895da"), 0),
                    ),
                    createdAt = 1736964335207,
                    confirmedAt = 1737022028411,
                    lockedAt = 1736964335587
                ),
                InboundLiquidityOutgoingPayment(
                    id = UUID.fromString("34edbeef-4564-455b-98f0-ab873eb4a9b8"),
                    channelId = ByteVector32.fromValidHex("b858ecdb13a382370445cefcec602496597ead2a9957cce5a8ae7ca0db8a904c"),
                    txId = TxId("336207f86415d8fd5dad3c10163803d032be0d167eed53a6b31697e687b46d1f"),
                    localMiningFees = 0.sat,
                    purchase = LiquidityAds.Purchase.Standard(amount = 1.sat, fees = LiquidityAds.Fees(miningFee = 271.sat, serviceFee = 1000.sat), paymentDetails = LiquidityAds.PaymentDetails.FromChannelBalance),
                    createdAt = 1736964335195,
                    confirmedAt = 1737022028411,
                    lockedAt = 1736964335587
                )
            ), paymentsForTx
        )
    }
}