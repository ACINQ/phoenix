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
import fr.acinq.bitcoin.ByteVector32
import fr.acinq.bitcoin.OutPoint
import fr.acinq.bitcoin.TxId
import fr.acinq.lightning.db.Bolt11IncomingPayment
import fr.acinq.lightning.db.LegacyPayToOpenIncomingPayment
import fr.acinq.lightning.db.LightningIncomingPayment
import fr.acinq.lightning.db.NewChannelIncomingPayment
import fr.acinq.lightning.payment.Bolt11Invoice
import fr.acinq.lightning.serialization.payment.Serialization
import fr.acinq.lightning.utils.UUID
import fr.acinq.lightning.utils.msat
import fr.acinq.lightning.utils.sat
import fr.acinq.phoenix.runTest
import fr.acinq.phoenix.utils.extensions.WalletPaymentState
import fr.acinq.phoenix.utils.extensions.state
import fracinqphoenixdb.Cloudkit_payments_metadata
import fracinqphoenixdb.Cloudkit_payments_queue
import fracinqphoenixdb.Link_lightning_outgoing_payment_parts
import fracinqphoenixdb.On_chain_txs
import fracinqphoenixdb.Payments_incoming
import fracinqphoenixdb.Payments_metadata
import fracinqphoenixdb.Payments_outgoing
import kotlin.test.Test
import kotlin.test.assertEquals


@Suppress("DEPRECATION")
class PaymentsDbMigrationTest {

    @Test
    fun `read v10 db`() = runTest {
        val driver = testPaymentsDriverFromResource("sampledbs/v10/payments-testnet-28903aff.sqlite")
        val paymentsDb = SqlitePaymentsDb(
            driver, PaymentsDatabase(
                driver = driver,
                payments_incomingAdapter = Payments_incoming.Adapter(
                    UUIDAdapter,
                    ByteVector32Adapter,
                    TxIdAdapter,
                    IncomingPaymentAdapter
                ),
                payments_outgoingAdapter = Payments_outgoing.Adapter(
                    UUIDAdapter,
                    ByteVector32Adapter,
                    TxIdAdapter,
                    OutgoingPaymentAdapter
                ),
                link_lightning_outgoing_payment_partsAdapter = Link_lightning_outgoing_payment_parts.Adapter(
                    UUIDAdapter,
                    UUIDAdapter
                ),
                on_chain_txsAdapter = On_chain_txs.Adapter(UUIDAdapter, TxIdAdapter),
                payments_metadataAdapter = Payments_metadata.Adapter(
                    UUIDAdapter,
                    EnumColumnAdapter(),
                    EnumColumnAdapter(),
                    EnumColumnAdapter()
                ),
                cloudkit_payments_queueAdapter = Cloudkit_payments_queue.Adapter(UUIDAdapter),
                cloudkit_payments_metadataAdapter = Cloudkit_payments_metadata.Adapter(UUIDAdapter),
            ),
            currencyManager = null
        )

        val payments = paymentsDb.database.paymentsQueries.list(limit = Long.MAX_VALUE, offset = 0)
            .executeAsList()
            .map { Serialization.deserialize(it).getOrThrow() }

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
}

expect fun testPaymentsDriverFromResource(path: String): SqlDriver