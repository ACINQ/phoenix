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
import fr.acinq.bitcoin.Chain
import fr.acinq.bitcoin.OutPoint
import fr.acinq.bitcoin.TxHash
import fr.acinq.bitcoin.TxId
import fr.acinq.lightning.db.AutomaticLiquidityPurchasePayment
import fr.acinq.lightning.db.Bolt11IncomingPayment
import fr.acinq.lightning.db.ChannelCloseOutgoingPayment
import fr.acinq.lightning.db.LegacyPayToOpenIncomingPayment
import fr.acinq.lightning.db.LegacySwapInIncomingPayment
import fr.acinq.lightning.db.LightningIncomingPayment
import fr.acinq.lightning.db.LightningOutgoingPayment
import fr.acinq.lightning.db.ManualLiquidityPurchasePayment
import fr.acinq.lightning.db.NewChannelIncomingPayment
import fr.acinq.lightning.db.SpliceCpfpOutgoingPayment
import fr.acinq.lightning.db.SpliceOutgoingPayment
import fr.acinq.lightning.payment.Bolt11Invoice
import fr.acinq.lightning.serialization.payment.Serialization
import fr.acinq.lightning.utils.UUID
import fr.acinq.lightning.utils.msat
import fr.acinq.lightning.utils.sat
import fr.acinq.lightning.utils.toMilliSatoshi
import fr.acinq.lightning.wire.LiquidityAds
import fr.acinq.phoenix.runTest
import fr.acinq.phoenix.utils.PlatformContext
import fr.acinq.phoenix.utils.extensions.WalletPaymentState
import fr.acinq.phoenix.utils.extensions.state
import okio.FileSystem
import okio.Path
import okio.Path.Companion.toPath
import kotlin.collections.List
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

expect abstract class UsingContextTest() {
    fun getPlatformContext(): PlatformContext

    /**
     * Sets up the original sample database files in the testing environment, so that the driver can find them when the tests are run.
     * For example, on Android, this method puts the database files in the database folder of the robolectric's simulated Android.
     */
    fun setUpDatabase(context: PlatformContext, databasePaths: List<Path>)
}

@Suppress("DEPRECATION")
class SqlitePaymentsDbTest : UsingContextTest() {

    @BeforeTest
    fun setupDatabases() {
        val sampleDbs = "src/commonTest/resources/sampledbs"
        val v1: List<Path> = FileSystem.SYSTEM.list("$sampleDbs/v1".toPath())
        val v6: List<Path> = FileSystem.SYSTEM.list("$sampleDbs/v6".toPath())
        val v10: List<Path> = FileSystem.SYSTEM.list("$sampleDbs/v10".toPath())
        setUpDatabase(getPlatformContext(), v1 + v6 + v10)
    }

    @Test
    fun `read v1 db`() = runTest {
        val driver = createPaymentsDbDriver(getPlatformContext(), chain = Chain.Testnet3, nodeIdHash = "fedc36138a62ceadc8a93861d2c46f5ca5e8b418")
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
        assertEquals(0, payments.filterIsInstance<ManualLiquidityPurchasePayment>().size)
        assertEquals(0, payments.filterIsInstance<AutomaticLiquidityPurchasePayment>().size)
        assertEquals(0, payments.filterIsInstance<ChannelCloseOutgoingPayment>().size)

        assertEquals(3 + 5, payments.size)

        val successful =
            payments.filter { it.state() == WalletPaymentState.SuccessOnChain || it.state() == WalletPaymentState.SuccessOffChain }
        assertEquals(3 + 5, successful.size)
    }

    @Test
    fun `read v1 db - additional`() = runTest {
        val driver = createPaymentsDbDriver(getPlatformContext(), chain = Chain.Testnet3, nodeIdHash = "a224978853d2f4c94ac8e2dbb2acf8344e0146d0")
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
        assertEquals(0, payments.filterIsInstance<ManualLiquidityPurchasePayment>().size)
        assertEquals(0, payments.filterIsInstance<AutomaticLiquidityPurchasePayment>().size)
        assertEquals(0, payments.filterIsInstance<ChannelCloseOutgoingPayment>().size)

        assertEquals(4 + 1 + 1 + 2, payments.size)

        val successful =
            payments.filter { it.state() == WalletPaymentState.SuccessOnChain || it.state() == WalletPaymentState.SuccessOffChain }
        assertEquals(4 + 1 + 1 + 2, successful.size)
    }

    @Test
    fun `read v6 db`() = runTest {
        val driver = createPaymentsDbDriver(getPlatformContext(), chain = Chain.Testnet3, nodeIdHash = "700486fc7a90d5922d6f993f2941ab9f9f1a9d85")
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
        assertEquals(0, payments.filterIsInstance<ManualLiquidityPurchasePayment>().size)
        assertEquals(0, payments.filterIsInstance<AutomaticLiquidityPurchasePayment>().size)
        assertEquals(4, payments.filterIsInstance<ChannelCloseOutgoingPayment>().size)

        assertEquals(1 + 4 + 6 + 4, payments.size)

        val successful =
            payments.filter { it.state() == WalletPaymentState.SuccessOnChain || it.state() == WalletPaymentState.SuccessOffChain }
        assertEquals(1 + 4 + 6 + 4, successful.size)
    }

    @Test
    fun `read v10 db`() = runTest {
        val driver = createPaymentsDbDriver(getPlatformContext(), chain = Chain.Testnet3, nodeIdHash = "28903aff")
        val paymentsDb = createSqlitePaymentsDb(driver, contactsManager = null, currencyManager = null)

        val payments = paymentsDb.database.paymentsQueries.list(limit = Long.MAX_VALUE, offset = 0)
            .executeAsList()
            .map { Serialization.deserialize(it.data_).getOrThrow() }

        assertEquals(736, payments.size)

        val successful =
            payments.filter { it.state() == WalletPaymentState.SuccessOnChain || it.state() == WalletPaymentState.SuccessOffChain }
        assertEquals(647, successful.size)

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

        // this on-chain incoming payment triggered a liquidity purchase of 1 sat which is not migrated. Instead, its fees must be included in the payment details
        paymentsDb.database.paymentsIncomingQueries
            .get(UUID.fromString("6ab99b1d-2345-4ec5-bfc4-817f93fd8548"))
            .executeAsOne()
            .also {
                assertEquals(
                    NewChannelIncomingPayment(
                        id = UUID.fromString("6ab99b1d-2345-4ec5-bfc4-817f93fd8548"),
                        amountReceived = 878721000.msat,
                        miningFee = 1135.sat,
                        serviceFee = 1000000.msat,
                        liquidityPurchase = LiquidityAds.Purchase.Standard(amount = 1.sat, fees = LiquidityAds.Fees(serviceFee = 1000.sat, miningFee = 542.sat), paymentDetails = LiquidityAds.PaymentDetails.FromChannelBalance),
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
    fun `read v10 db - fees`() = runTest {
        val driver = createPaymentsDbDriver(getPlatformContext(), chain = Chain.Testnet3, nodeIdHash = "f921bddf")
        val paymentsDb = createSqlitePaymentsDb(driver, contactsManager = null, currencyManager = null)

        // On-chain deposit of 200k sat that triggered a liquidity purchase.
        // The effectively received amount was 198 150 sat after fees.
        paymentsDb.database.paymentsIncomingQueries
            .get(UUID.fromString("7149cca7-d1d7-428d-8ee1-d9f43e44e9d8"))
            .executeAsOne()
            .also {
                assertEquals(
                    NewChannelIncomingPayment(
                        id = UUID.fromString("7149cca7-d1d7-428d-8ee1-d9f43e44e9d8"),
                        amountReceived = 198_150.sat.toMilliSatoshi(),
                        miningFee = 444.sat + 406.sat,
                        serviceFee = 1_000.sat.toMilliSatoshi(),
                        liquidityPurchase = LiquidityAds.Purchase.Standard(
                            amount = 1.sat,
                            fees = LiquidityAds.Fees(serviceFee = 1_000.sat, miningFee = 406.sat),
                            paymentDetails = LiquidityAds.PaymentDetails.FromChannelBalance
                        ),
                        channelId = ByteVector32.fromValidHex("12ac9f375e105a3e00f85e58bb820be9225a2ae5f08072e86e76632f8df768f2"),
                        txId = TxId("976dd66d48b8831b571644ad56a8aed4eb596b58f59620289b64bd4a40a70900"),
                        localInputs = setOf(OutPoint(TxHash("87de58628bfeaf8d153a0c27ddb091aa2911c159223c3b183cf913f236add35c"), 0)),
                        createdAt = 1738154761716L,
                        confirmedAt = 1738154906824L,
                        lockedAt = 1738155391461L,
                    ), it
                )
            }

        // Manual liquidity purchase for 100k sat.
        // The total fee was 162 593 sat.
        paymentsDb.database.paymentsOutgoingQueries
            .get(UUID.fromString("86a375bb-e8f7-4bf6-b3cc-2dde60f34058"))
            .executeAsOne()
            .also {
                assertEquals(
                    ManualLiquidityPurchasePayment(
                        id = UUID.fromString("86a375bb-e8f7-4bf6-b3cc-2dde60f34058"),
                        miningFee = 161_593.sat,
                        liquidityPurchase = LiquidityAds.Purchase.Standard(
                            amount = 100_000.sat,
                            fees = LiquidityAds.Fees(serviceFee = 1_000.sat, miningFee = 76_693.sat),
                            paymentDetails = LiquidityAds.PaymentDetails.FromChannelBalance
                        ),
                        channelId = ByteVector32.fromValidHex("12ac9f375e105a3e00f85e58bb820be9225a2ae5f08072e86e76632f8df768f2"),
                        txId = TxId("0c14d101796a8ae38dec800e23c90630a99051bae8d3ce5c29149f97b69ac40a"),
                        createdAt = 1738157412970L,
                        confirmedAt = 1738157661772L,
                        lockedAt = 1738158091535L,
                    ), it
                )
            }
    }
}
