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
import fr.acinq.lightning.db.SpliceInIncomingPayment
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
import fr.acinq.phoenix.utils.testLoggerFactory
import okio.FileSystem
import okio.Path
import okio.Path.Companion.toPath
import kotlin.collections.List
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

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

    val onError: (String) -> Unit = { testLoggerFactory.newLogger(this::class).e { "error in migration test: $it" }}

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
        val driver = createPaymentsDbDriver(getPlatformContext(), chain = Chain.Testnet3, nodeIdHash = "fedc36138a62ceadc8a93861d2c46f5ca5e8b418", onError = onError)
        val paymentsDb = createSqlitePaymentsDb(driver, metadataQueue = null, loggerFactory = testLoggerFactory)

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

        driver.close()
    }

    @Test
    fun `read v1 db - additional`() = runTest {
        val driver = createPaymentsDbDriver(getPlatformContext(), chain = Chain.Testnet3, nodeIdHash = "a224978853d2f4c94ac8e2dbb2acf8344e0146d0", onError = onError)
        val paymentsDb = createSqlitePaymentsDb(driver, metadataQueue = null, loggerFactory = testLoggerFactory)

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

        driver.close()
    }

    @Test
    fun `read v6 db`() = runTest {
        val driver = createPaymentsDbDriver(getPlatformContext(), chain = Chain.Testnet3, nodeIdHash = "700486fc7a90d5922d6f993f2941ab9f9f1a9d85", onError = onError)
        val paymentsDb = createSqlitePaymentsDb(driver, metadataQueue = null, loggerFactory = testLoggerFactory)

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

        driver.close()
    }

    @Test
    fun `read v10 db - large dataset`() = runTest {
        val driver = createPaymentsDbDriver(getPlatformContext(), chain = Chain.Testnet3, nodeIdHash = "28903aff", onError = onError)
        val paymentsDb = createSqlitePaymentsDb(driver, metadataQueue = null, loggerFactory = testLoggerFactory)

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
                        // mining fee must be the channel opening mining fee + the mining fee for the liquidity
                        miningFee = 1135.sat + 542.sat,
                        serviceFee = 1000000.msat,
                        liquidityPurchase = LiquidityAds.Purchase.Standard(
                            amount = 1.sat,
                            fees = LiquidityAds.Fees(miningFee = 542.sat, serviceFee = 1000.sat),
                            paymentDetails = LiquidityAds.PaymentDetails.FromChannelBalance
                        ),
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
        val driver = createPaymentsDbDriver(getPlatformContext(), chain = Chain.Testnet3, nodeIdHash = "f921bddf", onError = onError)
        val paymentsDb = createSqlitePaymentsDb(driver, metadataQueue = null, loggerFactory = testLoggerFactory)

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
                            fees = LiquidityAds.Fees(miningFee = 406.sat, serviceFee = 1_000.sat),
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
                            fees = LiquidityAds.Fees(miningFee = 76_693.sat, serviceFee = 1_000.sat),
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

        driver.close()
    }

    @Test
    fun `read v10 db - liquidity`() = runTest {
        val driver = createPaymentsDbDriver(getPlatformContext(), chain = Chain.Testnet3, nodeIdHash = "6a5e6f", onError = onError)
        val paymentsDb = createSqlitePaymentsDb(driver, metadataQueue = null, loggerFactory = testLoggerFactory)

        // swap-in 200_000 sat + liquidity purchase from channel balance => received 198_433 sat after fees.
        paymentsDb.database.paymentsIncomingQueries
            .get(UUID.fromString("2226371f-c13d-4f41-b2c2-99a1596ba895"))
            .executeAsOne()
            .also {
                assertEquals(
                    NewChannelIncomingPayment(
                        id = UUID.fromString("2226371f-c13d-4f41-b2c2-99a1596ba895"),
                        amountReceived = 198_433.sat.toMilliSatoshi(),
                        miningFee = 296.sat + 271.sat,
                        serviceFee = 1_000.sat.toMilliSatoshi(),
                        liquidityPurchase = LiquidityAds.Purchase.Standard(
                            amount = 1.sat,
                            fees = LiquidityAds.Fees(miningFee = 271.sat, serviceFee = 1_000.sat),
                            paymentDetails = LiquidityAds.PaymentDetails.FromChannelBalance,
                        ),
                        channelId = ByteVector32.fromValidHex("8aca84879c0d7517445ecaf399b427d1e79ecc55e9bfe2421e227679993c461e"),
                        txId = TxId("c4c1499ac137b210a189c22926b7a061a49eb04a489a116d7f276b348bc6dc46"),
                        localInputs = setOf(OutPoint(TxHash("b4df0f80e1d057638ec5969d1c2cc5896f7affd2fd787be728c405f20c79d717"), 1)),
                        createdAt = 1738332646763L,
                        confirmedAt = 1738334286135L,
                        lockedAt = 1738332694407L,
                    ), it
                )
            }

        // swap-in 300_000 sat => received 299_584 sat after fees
        paymentsDb.database.paymentsIncomingQueries
            .get(UUID.fromString("8bbd702f-4da0-4287-8a66-000d82c34ef2"))
            .executeAsOne()
            .also {
                assertIs<SpliceInIncomingPayment>(it)
                assertEquals(299_584.sat.toMilliSatoshi(), it.amountReceived)
                assertEquals(416.sat, it.miningFee)
                assertNull(it.liquidityPurchase)
            }

        // ln-outgoing -5_044 sat including fee 24 sat
        paymentsDb.database.paymentsOutgoingQueries
            .get(UUID.fromString("f51b9ef3-bade-445b-b8f1-e2ade93193ef"))
            .executeAsOne()
            .also {
                assertIs<LightningOutgoingPayment>(it)
            }

        // splice-out -151_810 sat fee=1_810 sat
        paymentsDb.database.paymentsOutgoingQueries
            .get(UUID.fromString("d87bd8e9-4e4d-46a2-bf2e-404017dbd111"))
            .executeAsOne()
            .also {
                assertIs<SpliceOutgoingPayment>(it)
            }

        // pay-to-splice 200_000 sat + on-the-fly liquidity purchase => received 197_144 sat after fees
        paymentsDb.database.paymentsIncomingQueries
            .get(UUID.fromString("9e1f4892-15fd-4b3f-ba0b-e9da997737f6"))
            .executeAsOne()
            .also {
                assertEquals(
                    Bolt11IncomingPayment(
                        preimage = ByteVector32.fromValidHex("2a97a6923ae4fe68f0ed975bea272ce4e95d1896ec77b83597c0a960ffabcc8c"),
                        paymentRequest = Bolt11Invoice.read("lntb2m1pneec8upp5nc053ys4l54n7wsta8dfjaeh7c2zrsplqrcxkj52zcqqcf0p3v6qcqzyssp550dg5h4t6kg02s9v0u6azxrzfj8nnjsppjlx2yl5wetvafx9vufs9q7sqqqqqqqqqqqqqqqqqqqsqqqqqysgqdq4wpshjtt5dukhxurvd93k2mqz9gxqyjw5qrzjqwfn3p9278ttzzpe0e00uhyxhned3j5d9acqak5emwfpflp8z2cnflla25gajw4h0yqqqqlgqqqqqeqqjqzm4g3f8pudsudf4sq2ckuhjsuna6r543pcm7rw020er9y9kwd0fzq9scy6edqmfeffafmn2duufn0s8hcjzfkrkwrwgmyl5sxggvvespkcev7p").get(),
                        parts = listOf(
                            LightningIncomingPayment.Part.Htlc(
                                // amountReceived must be 200_000, i.e. before liquidity fees, because liquidity is FromChannelBalanceForFutureHtlc
                                amountReceived = 200_000.sat.toMilliSatoshi(),
                                channelId = ByteVector32.fromValidHex("8aca84879c0d7517445ecaf399b427d1e79ecc55e9bfe2421e227679993c461e"),
                                htlcId = 0,
                                fundingFee = LiquidityAds.FundingFee(amount = 0.msat, fundingTxId = TxId("4eeffee8bdfc4f63cbb5eb0f20408af924e122099d5d6af2cec0d5a72a7d97f7")),
                                receivedAt = 1738335394871L
                            )
                        ),
                        liquidityPurchaseDetails = LiquidityAds.LiquidityTransactionDetails(
                            txId = TxId("4eeffee8bdfc4f63cbb5eb0f20408af924e122099d5d6af2cec0d5a72a7d97f7"),
                            miningFee = 856.sat,
                            purchase = LiquidityAds.Purchase.Standard(
                                amount = 200_000.sat,
                                fees = LiquidityAds.Fees(miningFee = 406.sat, serviceFee = 2_000.sat),
                                paymentDetails = LiquidityAds.PaymentDetails.FromChannelBalanceForFutureHtlc(
                                    paymentHashes = listOf(ByteVector32.fromValidHex("9e1f489215fd2b3f3a0be9da997737f61421c03f00f06b4a8a16000c25e18b34"))
                                )
                            ),
                        ),
                        createdAt = 1738334460105L,
                    ), it
                )
                // the amount received must take the cost of the liquidity into account
                assertEquals(197_144.sat.toMilliSatoshi(), it.amountReceived)
            }

        // the pay-to-splice is tied to an auto-liquidity with a non-null `incomingPaymentReceivedAt` (i.e., not visible in the UI)
        paymentsDb.database.paymentsOutgoingQueries
            .get(UUID.fromString("1aa9ca75-ff7c-44bb-8595-9df94b404f2b"))
            .executeAsOne()
            .also {
                assertEquals(
                    AutomaticLiquidityPurchasePayment(
                        id = UUID.fromString("1aa9ca75-ff7c-44bb-8595-9df94b404f2b"),
                        miningFee = 856.sat,
                        channelId = ByteVector32.fromValidHex("8aca84879c0d7517445ecaf399b427d1e79ecc55e9bfe2421e227679993c461e"),
                        txId = TxId("4eeffee8bdfc4f63cbb5eb0f20408af924e122099d5d6af2cec0d5a72a7d97f7"),
                        liquidityPurchase = LiquidityAds.Purchase.Standard(
                            amount = 200_000.sat,
                            fees = LiquidityAds.Fees(miningFee = 406.sat, serviceFee = 2_000.sat),
                            paymentDetails = LiquidityAds.PaymentDetails.FromChannelBalanceForFutureHtlc(paymentHashes = listOf(ByteVector32.fromValidHex("9e1f489215fd2b3f3a0be9da997737f61421c03f00f06b4a8a16000c25e18b34")))
                        ),
                        createdAt = 1738334767765L,
                        confirmedAt = 1738335294588,
                        lockedAt = 1738335394430L,
                        incomingPaymentReceivedAt = 1738335394871L
                    ), it
                )
            }

        // manual-liquidity of 250_000 sat, with mining fee of 161_593 sat => total costs 164_093 sat
        paymentsDb.database.paymentsOutgoingQueries
            .get(UUID.fromString("fda1bd70-4baf-4ef3-ae1f-c61a0cc50aa1"))
            .executeAsOne()
            .also {
                assertEquals(
                    ManualLiquidityPurchasePayment(
                        id = UUID.fromString("fda1bd70-4baf-4ef3-ae1f-c61a0cc50aa1"),
                        miningFee = 161_593.sat,
                        liquidityPurchase = LiquidityAds.Purchase.Standard(
                            amount = 250_000.sat,
                            fees = LiquidityAds.Fees(miningFee = 76_693.sat, serviceFee = 2_500.sat),
                            paymentDetails = LiquidityAds.PaymentDetails.FromChannelBalance
                        ),
                        channelId = ByteVector32.fromValidHex("8aca84879c0d7517445ecaf399b427d1e79ecc55e9bfe2421e227679993c461e"),
                        txId = TxId("9621b533ed313ac4ed76f582d9523b473f8103bac35b52c9d8e0ededb7ba6962"),
                        createdAt = 1738335653481L,
                        confirmedAt = 1738336277864L,
                        lockedAt = 1738336294405L,
                    ), it
                )
            }

        // lightning-incoming => received 170_000 sat without liquidity purchase
        paymentsDb.database.paymentsIncomingQueries
            .get(UUID.fromString("90103df3-0b54-4fbf-a508-13c28a272263"))
            .executeAsOne()
            .also {
                assertEquals(
                    Bolt11IncomingPayment(
                        preimage = ByteVector32.fromValidHex("e38cb98d6d973639e1ffb63e84db39b9dca56086dc7ccbe30cf22f2f45cf69af"),
                        paymentRequest = Bolt11Invoice.read("lntb1700u1pnee6zvpp5jqgrmuct2j0mleggz0pg5fezv0uwhsqaglr3y0csff09wm7qehtscqzyssp52kxmxdcpfgzvpk0s6agl3tz7ws4w3u6madevhs35txlnlr9z29ns9q7sqqqqqqqqqqqqqqqqqqqsqqqqqysgqdpyd35kw6r5de5kueeqdehhggrpypehqmrfvdjsmqz9gxqyjw5qrzjqwfn3p9278ttzzpe0e00uhyxhned3j5d9acqak5emwfpflp8z2cnflla25gajw4h0yqqqqlgqqqqqeqqjqty3kr0qzya30cqjs9gfqs39r0eq2euz3wd6y7ljye75cx4fcm60kdp53pv50qyngqu9vrlkqv83r9xxpzjdv0u56tyhtqaa5yunv2uqphrhtwe").get(),
                        parts = listOf(
                            LightningIncomingPayment.Part.Htlc(
                                amountReceived = 170_000.sat.toMilliSatoshi(),
                                channelId = ByteVector32.fromValidHex("8aca84879c0d7517445ecaf399b427d1e79ecc55e9bfe2421e227679993c461e"),
                                htlcId = 1,
                                fundingFee = null,
                                receivedAt = 1738336348600L,
                            )
                        ),
                        liquidityPurchaseDetails = null,
                        createdAt = 1738336332667L,
                    ), it
                )
            }

        // channel close => spending 544_213 sat
        paymentsDb.database.paymentsOutgoingQueries
            .get(UUID.fromString("823feea4-eead-4aa4-9141-0fadee874f8e"))
            .executeAsOne()
            .also {
                assertEquals(
                    ChannelCloseOutgoingPayment(
                        id = UUID.fromString("823feea4-eead-4aa4-9141-0fadee874f8e"),
                        recipientAmount = 544_213.sat,
                        address = "tb1qrcppmta4c4q0ztk3an2m8hn34r32sd40xkxfnn",
                        isSentToDefaultAddress = false,
                        miningFee = 0.sat,
                        channelId = ByteVector32.fromValidHex("8aca84879c0d7517445ecaf399b427d1e79ecc55e9bfe2421e227679993c461e"),
                        txId = TxId("2b16121e6745b407993e114d7a4217f779f76c267c1cf190a2397d1731d3d653"),
                        createdAt = 1738336392835L,
                        confirmedAt = null,
                        lockedAt = 1738336477169L,
                        closingType = ChannelCloseOutgoingPayment.ChannelClosingType.Mutual,
                    ), it
                )
            }

        // pay-to-open 350_000 sat with liquidity purchase => received 344_485 sat after fees
        paymentsDb.database.paymentsIncomingQueries
            .get(UUID.fromString("e1d7fecc-8e7c-423e-b52f-cb853c2c01bd"))
            .executeAsOne()
            .also {
                assertEquals(
                    Bolt11IncomingPayment(
                        preimage = ByteVector32.fromValidHex("86cc39d5baa6a2a2757c47a6c3e1a0facd36c86127e957853889c1a44a5b1e32"),
                        paymentRequest = Bolt11Invoice.read("lntb3500u1pnee690pp5u8tlanyw0jpruaf0ewznctqph46a744n6s5tgd2vksq9pjwmpalscqzyssp5qs3qmcqhyj3e0mmu7r47zfuqm9vuzgsnf3dnzceemdd3fupeq7aq9q7sqqqqqqqqqqqqqqqqqqqsqqqqqysgqdqjwpshjtt5dukk7ur9dcmqz9gxqyjw5qrzjqwfn3p9278ttzzpe0e00uhyxhned3j5d9acqak5emwfpflp8z2cnflla25gajw4h0yqqqqlgqqqqqeqqjqsmga84r7jg2f54epdcfd4r579jgaw9x3t6r5eg82kzt65esu0ue9536caqfuep7a7w2ge0ttu4fzrpyxm3d0qdamgl2vkhlu4jh0q5gqyd4rw6").get(),
                        parts = listOf(
                            LightningIncomingPayment.Part.Htlc(
                                // amountReceived must take into account the cost of the liquidity, because the liquidity is FromFutureHtlc
                                amountReceived = 344_485.sat.toMilliSatoshi(),
                                channelId = ByteVector32.fromValidHex("887ef839742d05217508107b40898439a11511aa46f924a7e930e9e5912852c1"),
                                htlcId = 0,
                                fundingFee = LiquidityAds.FundingFee(amount = 5_515_000.msat, fundingTxId = TxId("adbad2af375305ddb78526ae52e5ccb13a220a8553b1187d05d033451edb19c0")),
                                receivedAt = 1738337194625L
                            )
                        ),
                        liquidityPurchaseDetails = LiquidityAds.LiquidityTransactionDetails(
                            txId = TxId("adbad2af375305ddb78526ae52e5ccb13a220a8553b1187d05d033451edb19c0"),
                            miningFee = 1_015.sat,
                            purchase = LiquidityAds.Purchase.Standard(
                                amount = 350_000.sat,
                                fees = LiquidityAds.Fees(miningFee = 1_015.sat, serviceFee = 4_500.sat),
                                paymentDetails = LiquidityAds.PaymentDetails.FromFutureHtlc(
                                    paymentHashes = listOf(ByteVector32.fromValidHex("e1d7fecc8e7c823e752fcb853c2c01bd75df56b3d428b4354cb40050c9db0f7f"))
                                )
                            ),
                        ),
                        createdAt = 1738336431048L,
                    ), it
                )
            }

        // the pay-to-open is tied to an auto-liquidity with a non-null `incomingPaymentReceivedAt`
        paymentsDb.database.paymentsOutgoingQueries
            .get(UUID.fromString("8c64f372-9dbe-439e-b496-e442c8549df7"))
            .executeAsOne()
            .also {
                assertEquals(
                    AutomaticLiquidityPurchasePayment(
                        id = UUID.fromString("8c64f372-9dbe-439e-b496-e442c8549df7"),
                        miningFee = 1_015.sat,
                        channelId = ByteVector32.fromValidHex("887ef839742d05217508107b40898439a11511aa46f924a7e930e9e5912852c1"),
                        txId = TxId("adbad2af375305ddb78526ae52e5ccb13a220a8553b1187d05d033451edb19c0"),
                        liquidityPurchase = LiquidityAds.Purchase.Standard(
                            amount = 350_000.sat,
                            fees = LiquidityAds.Fees(miningFee = 1_015.sat, serviceFee = 4_500.sat),
                            paymentDetails = LiquidityAds.PaymentDetails.FromFutureHtlc(paymentHashes = listOf(ByteVector32.fromValidHex("e1d7fecc8e7c823e752fcb853c2c01bd75df56b3d428b4354cb40050c9db0f7f")))
                        ),
                        createdAt = 1738336437553L,
                        confirmedAt = null,
                        lockedAt = 1738337194363L,
                        incomingPaymentReceivedAt = 1738337194625L
                    ), it
                )
            }

        driver.close()
    }
}
