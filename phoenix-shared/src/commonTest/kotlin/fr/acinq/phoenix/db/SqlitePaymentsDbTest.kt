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

import app.cash.sqldelight.db.SqlDriver
import fr.acinq.bitcoin.ByteVector32
import fr.acinq.bitcoin.OutPoint
import fr.acinq.bitcoin.TxId
import fr.acinq.lightning.db.Bolt11IncomingPayment
import fr.acinq.lightning.db.Bolt12IncomingPayment
import fr.acinq.lightning.db.ChannelCloseOutgoingPayment
import fr.acinq.lightning.db.InboundLiquidityOutgoingPayment
import fr.acinq.lightning.db.LegacyPayToOpenIncomingPayment
import fr.acinq.lightning.db.LegacySwapInIncomingPayment
import fr.acinq.lightning.db.LightningIncomingPayment
import fr.acinq.lightning.db.LightningOutgoingPayment
import fr.acinq.lightning.db.NewChannelIncomingPayment
import fr.acinq.lightning.db.SpliceCpfpOutgoingPayment
import fr.acinq.lightning.db.SpliceOutgoingPayment
import fr.acinq.lightning.payment.Bolt11Invoice
import fr.acinq.lightning.serialization.payment.Serialization
import fr.acinq.lightning.utils.UUID
import fr.acinq.lightning.utils.msat
import fr.acinq.lightning.utils.sat
import fr.acinq.lightning.wire.LiquidityAds
import fr.acinq.phoenix.runTest
import fr.acinq.phoenix.utils.extensions.WalletPaymentState
import fr.acinq.phoenix.utils.extensions.state
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs


@Suppress("DEPRECATION")
class SqlitePaymentsDbTest {

    @Test
    fun `read v1 db`() = runTest {
        val driver = testPaymentsDriverFromResource("sampledbs/v1/old.payments-testnet-fedc36138a62ceadc8a93861d2c46f5ca5e8b418.sqlite")
        val paymentsDb = createSqlitePaymentsDb(driver, contactsManager = null, currencyManager = null)

        val payments = paymentsDb.database.paymentsQueries.list(limit = Long.MAX_VALUE, offset = 0)
            .executeAsList()
            .map { Serialization.deserialize(it.data_).getOrThrow() }

        assertEquals(2, payments.filterIsInstance<Bolt11IncomingPayment>().size)
        assertEquals(1, payments.filterIsInstance<LegacyPayToOpenIncomingPayment>().size)
        assertEquals(0, payments.filterIsInstance<LegacySwapInIncomingPayment>().size)
        assertEquals(0, payments.filterIsInstance<SpliceOutgoingPayment>().size)
        assertEquals(0, payments.filterIsInstance<SpliceCpfpOutgoingPayment>().size)
        assertEquals(5, payments.filterIsInstance<LightningOutgoingPayment>().size)
        assertEquals(0, payments.filterIsInstance<InboundLiquidityOutgoingPayment>().size)
        assertEquals(0, payments.filterIsInstance<ChannelCloseOutgoingPayment>().size)

        assertEquals(3 + 5, payments.size)

        val successful =
            payments.filter { it.state() == WalletPaymentState.SuccessOnChain || it.state() == WalletPaymentState.SuccessOffChain }
        assertEquals(3 + 5, successful.size)
    }

    @Test
    fun `read v1 db - additional`() = runTest {
        val driver = testPaymentsDriverFromResource("sampledbs/v1/old.payments-testnet-a224978853d2f4c94ac8e2dbb2acf8344e0146d0.sqlite")
        val paymentsDb = createSqlitePaymentsDb(driver, contactsManager = null, currencyManager = null)

        val payments = paymentsDb.database.paymentsQueries.list(limit = Long.MAX_VALUE, offset = 0)
            .executeAsList()
            .map { Serialization.deserialize(it.data_).getOrThrow() }

        assertEquals(4, payments.filterIsInstance<Bolt11IncomingPayment>().size)
        assertEquals(1, payments.filterIsInstance<LegacyPayToOpenIncomingPayment>().size)
        assertEquals(1, payments.filterIsInstance<LegacySwapInIncomingPayment>().size)
        assertEquals(0, payments.filterIsInstance<SpliceOutgoingPayment>().size)
        assertEquals(0, payments.filterIsInstance<SpliceCpfpOutgoingPayment>().size)
        assertEquals(2, payments.filterIsInstance<LightningOutgoingPayment>().size)
        assertEquals(0, payments.filterIsInstance<InboundLiquidityOutgoingPayment>().size)
        assertEquals(0, payments.filterIsInstance<ChannelCloseOutgoingPayment>().size)

        assertEquals(4 + 1 + 1 + 2, payments.size)

        val successful =
            payments.filter { it.state() == WalletPaymentState.SuccessOnChain || it.state() == WalletPaymentState.SuccessOffChain }
        assertEquals(4 + 1 + 1 + 2, successful.size)
    }

    @Test
    fun `read v6 db`() = runTest {
        val driver = testPaymentsDriverFromResource("sampledbs/v6/old.payments-testnet-700486fc7a90d5922d6f993f2941ab9f9f1a9d85.sqlite")
        val paymentsDb = createSqlitePaymentsDb(driver, contactsManager = null, currencyManager = null)

        val payments = paymentsDb.database.paymentsQueries.list(limit = Long.MAX_VALUE, offset = 0)
            .executeAsList()
            .map { Serialization.deserialize(it.data_).getOrThrow() }

        assertEquals(1, payments.filterIsInstance<Bolt11IncomingPayment>().size)
        assertEquals(0, payments.filterIsInstance<LegacyPayToOpenIncomingPayment>().size)
        assertEquals(4, payments.filterIsInstance<LegacySwapInIncomingPayment>().size)
        assertEquals(0, payments.filterIsInstance<SpliceOutgoingPayment>().size)
        assertEquals(0, payments.filterIsInstance<SpliceCpfpOutgoingPayment>().size)
        assertEquals(6, payments.filterIsInstance<LightningOutgoingPayment>().size)
        assertEquals(0, payments.filterIsInstance<InboundLiquidityOutgoingPayment>().size)
        assertEquals(4, payments.filterIsInstance<ChannelCloseOutgoingPayment>().size)

        assertEquals(1 + 4 + 6 + 4, payments.size)

        val successful =
            payments.filter { it.state() == WalletPaymentState.SuccessOnChain || it.state() == WalletPaymentState.SuccessOffChain }
        assertEquals(1 + 4 + 6 + 4, successful.size)
    }

    @Test
    fun `read v10 db`() = runTest {
        val driver = testPaymentsDriverFromResource("sampledbs/v10/payments-testnet-28903aff.sqlite")
        val paymentsDb = createSqlitePaymentsDb(driver, contactsManager = null, currencyManager = null)

        val payments = paymentsDb.database.paymentsQueries.list(limit = Long.MAX_VALUE, offset = 0)
            .executeAsList()
            .map { Serialization.deserialize(it.data_).getOrThrow() }

        assertEquals(737, payments.size)

        val successful =
            payments.filter { it.state() == WalletPaymentState.SuccessOnChain || it.state() == WalletPaymentState.SuccessOffChain }
        assertEquals(648, successful.size)

        paymentsDb.database.paymentsIncomingQueries
            .getByPaymentHash(ByteVector32("47fef2d23757298bf95386655d96fbe0c17f782713b1ba8880b625fd000190f4"))
            .executeAsOne()
            .also {
                assertEquals(
                    Bolt11IncomingPayment(
                        preimage = ByteVector32("838f6d8dfbbd07c93e39bb75a07f203f602947e80a4710f97b1d9f8cad71ce10"),
                        paymentRequest = Bolt11Invoice.read("lntb1pj7saw8pp5gll0953h2u5ch72nsej4m9hmurqh77p8zwcm4zyqkcjl6qqpjr6qcqpjsp5kvrdl2lgg95ph9etu3vy4lwv5jg7q4l5pg4d3s9pvk0h4s5n0hqq9q7sqqqqqqqqqqqqqqqqqqqsqqqqqysgqdqqmqz9gxqyjw5qrzjqwfn3p9278ttzzpe0e00uhyxhned3j5d9acqak5emwfpflp8z2cnflaa4zk6wkdlk5qqqqlgqqqqqeqqjqx8dfja6x4cqywqrvl3usj4743c5vu59uysp7f2wt8n2a8dz0l4ws628k904eqzh5vnzcs8fuvzsvc5qr43ksd7tt9pvqdxmdcwvv64sqnkfzjs")
                            .get(),
                        parts = listOf(
                            LightningIncomingPayment.Part.Htlc(
                                amountReceived = 5000.msat,
                                channelId = ByteVector32("4f3f787926fd915dbdcc9795c4db577c761a64a01c2b06b70b768078cf16a44a"),
                                htlcId = 3,
                                fundingFee = null,
                                receivedAt = 1709733399131L
                            )
                        ),
                        createdAt = 1709733319047L
                    ), it
                )
            }

        paymentsDb.database.paymentsIncomingQueries
            .getByPaymentHash(ByteVector32("1823d0c93658338a0ab8496dcd80c1801eb44269f25bbb868bddfb40a64492c6"))
            .executeAsOne()
            .also {
                assertEquals(
                    LegacyPayToOpenIncomingPayment(
                        paymentPreimage = ByteVector32("635d4df20ce9fb24452089fa573ee8e8285dee0920c0e86877c47a6437e5b641"),
                        origin = LegacyPayToOpenIncomingPayment.Origin.Invoice(
                            Bolt11Invoice.read("lntb1pju594dpp5rq3apjfktqec5z4cf9kumqxpsq0tgsnf7fdmhp5tmha5pfjyjtrqcqpjsp5g75grccuuk6hd9wg4zncpqyj55r6et0f3687m0mujjrq8vypqzzq9q7sqqqqqqqqqqqqqqqqqqqsqqqqqysgqdqqmqz9gxqyjw5qrzjqwfn3p9278ttzzpe0e00uhyxhned3j5d9acqak5emwfpflp8z2cnflaa4zk6wkdlk5qqqqlgqqqqqeqqjq6w97x92wswfc9awc9rtwecn4vwzmetkf2nz0h8mlae7w74u2xksnl2r2et5cw7h570pjkpapvuq72hng9l3y9a8dea37pq5w4es6fngp6uvec5")
                                .get()
                        ),
                        parts = listOf(
                            LegacyPayToOpenIncomingPayment.Part.OnChain(
                                amountReceived = 297326000.msat,
                                serviceFee = 1000000.msat,
                                miningFee = 1674.sat,
                                channelId = ByteVector32("4f3f787926fd915dbdcc9795c4db577c761a64a01c2b06b70b768078cf16a44a"),
                                txId = TxId("343a4bfa6531a2e06757908ff70ba53bec23a922da6335d0cff2bfafa2360805"),
                                confirmedAt = 1707743422214L,
                                lockedAt = 1707742959020L
                            )
                        ),
                        createdAt = 1707742893971L,
                        completedAt = 1707742959020L
                    ), it
                )
            }

        paymentsDb.database.paymentsIncomingQueries
            .get(UUID.fromString("6ab99b1d-2345-4ec5-bfc4-817f93fd8548"))
            .executeAsOne()
            .also {
                assertEquals(
                    NewChannelIncomingPayment(
                        id = UUID.fromString("6ab99b1d-2345-4ec5-bfc4-817f93fd8548"),
                        amountReceived = 878721000.msat,
                        serviceFee = 1000000.msat,
                        miningFee = 1135.sat,
                        channelId = ByteVector32("7d508efbcd8070244db638062a7da90a2b68491f807dab2cdc2a4fe95afb235b"),
                        txId = TxId("2e1ed22ea8871365260c8bc413e765cc7435e97f67a8f363b1a74298f4c423ec"),
                        localInputs = setOf(
                            OutPoint(TxId("9c75119309e343e5058872c3c5d5844d37ef2653aa78f45cc589cd14287b0cd1"), 0)
                        ),
                        createdAt = 1728059628455L,
                        confirmedAt = 1728114719132L,
                        lockedAt = 1728059628814L
                    ), it
                )
            }
    }

    @Test
    fun `read v12 db`() = runTest {
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

expect fun testPaymentsDriverFromResource(path: String): SqlDriver