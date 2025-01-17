/*
 * Copyright 2022 ACINQ SAS
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

import app.cash.sqldelight.EnumColumnAdapter
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import fr.acinq.bitcoin.ByteVector32
import fr.acinq.bitcoin.Chain
import fr.acinq.bitcoin.PublicKey
import fr.acinq.bitcoin.TxId
import fr.acinq.bitcoin.byteVector32
import fr.acinq.eclair.db.sqlite.SqlitePaymentsDb
import fr.acinq.lightning.ShortChannelId
import fr.acinq.lightning.db.*
import fr.acinq.lightning.payment.Bolt11Invoice
import fr.acinq.lightning.payment.FinalFailure
import fr.acinq.lightning.utils.UUID
import fr.acinq.lightning.utils.msat
import fr.acinq.lightning.utils.sat
import fr.acinq.phoenix.android.utils.LegacyMigrationHelper
import fr.acinq.phoenix.legacy.db.Database
import fr.acinq.phoenix.legacy.db.PayToOpenMetaRepository
import fr.acinq.phoenix.legacy.db.PaymentMeta
import fr.acinq.phoenix.legacy.db.PaymentMetaRepository
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import scala.collection.JavaConversions
import java.io.InputStreamReader
import java.sql.DriverManager

class LegacyMigrationHelperTest {

    private lateinit var metadataDriver: SqlDriver
    private lateinit var metadataDb: Database
    private lateinit var paymentMetaRepository: PaymentMetaRepository
    private lateinit var payToOpenMetaRepository: PayToOpenMetaRepository

    @Before
    fun before() {
        metadataDriver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        metadataDb = Database(
            driver = metadataDriver,
            PaymentMetaAdapter = PaymentMeta.Adapter(EnumColumnAdapter())
        )
        paymentMetaRepository = PaymentMetaRepository.getInstance(metadataDb.paymentMetaQueries)
        payToOpenMetaRepository = PayToOpenMetaRepository.getInstance(metadataDb.payToOpenMetaQueries)
    }

    @Test
    fun test_migration_of_legacy_outgoing_payments() {
        val connection = DriverManager.getConnection("jdbc:sqlite::memory:")
        val legacyPaymentsDb = SqlitePaymentsDb(connection)

        // insert legacy outgoing payments
        val statement = connection.createStatement()
        InputStreamReader(this.javaClass.classLoader?.getResourceAsStream("legacy-outgoing.sql") ?: throw RuntimeException("cannot load outgoing payments from sql dump file"))
            .readLines().filter { it.isNotBlank() }.forEach {
                statement.executeUpdate(it)
            }

        // insert metadata
        InputStreamReader(this.javaClass.classLoader?.getResourceAsStream("legacy-metadata.sql") ?: throw RuntimeException("cannot load metadata from sql dump file"))
            .readLines().filter { it.isNotBlank() }.forEach {
                metadataDriver.execute(null, it, 0)
            }

        // check insertion worked as expected
        val rawParts = legacyPaymentsDb.listAllOutgoingPayments()
        Assert.assertEquals(23, rawParts.size())

        // check aggregation worked as expected (in the legacy db, each parts is a row in the payments db and they must be grouped by parent id first)
        val legacyOutgoingPayments = LegacyMigrationHelper.groupLegacyOutgoingPayments(rawParts)
        Assert.assertEquals(9, legacyOutgoingPayments.size)

        // transform legacy payments to modern OutgoingPayment objects
        val newOutgoingPayments = legacyOutgoingPayments.map {
            LegacyMigrationHelper.modernizeLegacyOutgoingPayment(
                chain = Chain.Testnet3,
                parentId = it.key,
                listOfParts = it.value,
                paymentMeta = paymentMetaRepository.get(it.key.toString())
            )
        }

        Assert.assertEquals(9, newOutgoingPayments.size)

        // 1st outgoing payment is a LN payment for 2 sat, including 1 sat fee
        val payment1 = newOutgoingPayments[0]
        Assert.assertEquals(
            LightningOutgoingPayment(
                id = UUID.fromString("5a7d44be-725b-430b-8ce3-92c5ed296074"),
                recipientAmount = 1000.msat,
                recipient = PublicKey.fromHex("020ec0c6a0c4fe5d8a79928ead294c36234a76f6e0dca896c35413612a3fd8dbf8"),
                details = LightningOutgoingPayment.Details.Normal(Bolt11Invoice.read("lntb10n1p3tnfjkpp5rrmp00akdvl35nnhh4gqfp5rhuaa0kryhkz0ky5z78u82c2p2tlqdqqcqzpgxqyz5vqsp5xmvvr6sfrxs5vjpqgxq5p7aznd2eusxmgcj9hag8d06lxmdwxe8s9qyyssqttpwlngy7qaj7jg0xa2uae2gcm9zp9g5ly3yp6gxyh0zlrw2pun4tlsw2mfjwt0vsjwhtmvvn6tl0u9fg5jfle0jvxtd59h82kfff4qqefnasg").get()),
                parts = listOf(
                    LightningOutgoingPayment.Part(
                        id = UUID.fromString("91018192-5eeb-4e23-a494-8579009a6117"),
                        amount = 2000.msat,
                        route = listOf(
                            LightningOutgoingPayment.Part.HopDesc(
                                nodeId = PublicKey.fromHex("0210d27a0a720c58ab656d9a793ee5bb2c9c1d7df9002f1a900715f4abe219b823"),
                                nextNodeId = PublicKey.fromHex("03933884aaf1d6b108397e5efe5c86bcf2d8ca8d2f700eda99db9214fc2712b134"),
                                shortChannelId = ShortChannelId.invoke("0x1480x1")
                            ),
                            LightningOutgoingPayment.Part.HopDesc(
                                nodeId = PublicKey.fromHex("03933884aaf1d6b108397e5efe5c86bcf2d8ca8d2f700eda99db9214fc2712b134"),
                                nextNodeId = PublicKey.fromHex("020ec0c6a0c4fe5d8a79928ead294c36234a76f6e0dca896c35413612a3fd8dbf8"),
                            )
                        ),
                        status = LightningOutgoingPayment.Part.Status.Succeeded(
                            preimage = ByteVector32.fromValidHex("47a406e1c82bb11d70e62da0a25252fdd3ed1ebf5232581009cbce7fd8da1648"),
                            completedAt = 1656336182723
                        ),
                        createdAt = 1656336179094
                    )
                ),
                status = LightningOutgoingPayment.Status.Completed.Succeeded.OffChain(
                    preimage = ByteVector32.fromValidHex("47a406e1c82bb11d70e62da0a25252fdd3ed1ebf5232581009cbce7fd8da1648"),
                    completedAt = 1656336182723
                ),
                createdAt = 1656336179094
            ),
            payment1
        )
        Assert.assertEquals(2000.msat, payment1.amount)
        Assert.assertEquals(1000.msat, payment1.fees)

        // 2nd outgoing payment is a swapout
        val payment2 = (newOutgoingPayments[1] as LightningOutgoingPayment)
        Assert.assertEquals(
            LightningOutgoingPayment.Details.SwapOut(
                address = "2N1sjnTPsAaG3oGMHTHonANbHEERuiqN6k6",
                paymentRequest = Bolt11Invoice.read("lntb128400n1p3tndjapp5hhluw7tvph7e7mr5qm6zyerstxmwwc6ydg3jwj2t5dyjzuvjzcdqdrhxycrqvpsypekzarnyp6x7gpjfcchx6nw23g8xstpguek736dfp2ysmmwg98xyjz9g4f826t3fcmxkd3qwa5hg6pqvejk2unpw3jn6v3sypekzap0vfuhgegsp5s55kw5r85788n2aahjqpmzanvx83a2q5ftas7h76c4ymm7cw2uvsxqzjccqzpu9q2sqqqqqysgqcd3f6mn22c5hd7c3dd96z79duczy40rgfcpfetg4ckdkghjwylcqqlesa9adhvqvvkkkz4n2tq6lvx23ju4y2wl9mluf5mheq83mecsp68xkxx").get(),
                swapOutFee = 2_840.sat
            ),
            payment2.details
        )
        Assert.assertEquals(12_840_000.msat, payment2.amount)
        Assert.assertEquals(2_840_000.msat, payment2.fees)
        Assert.assertEquals(
            PublicKey.fromHex("03933884aaf1d6b108397e5efe5c86bcf2d8ca8d2f700eda99db9214fc2712b134"),
            payment2.recipient
        )

        // 3rd outgoing payment has failed
        val payment3 = newOutgoingPayments[2] as LightningOutgoingPayment
        Assert.assertEquals(
            LightningOutgoingPayment.Status.Completed.Failed(
                reason = FinalFailure.RecipientUnreachable,
                completedAt = 1656338125658
            ),
            payment3.status
        )
        Assert.assertEquals(2, payment3.parts.size)
        Assert.assertTrue(payment3.parts.all { it.status is LightningOutgoingPayment.Part.Status.Failed })

        // 5th outgoing payment is successful and was split in 2 parts. 6 attempts made.
        val payment5 = newOutgoingPayments[4] as LightningOutgoingPayment
        Assert.assertEquals(6, payment5.parts.size)
        Assert.assertTrue(payment5.parts.takeLast(2).all {
            it.status is LightningOutgoingPayment.Part.Status.Succeeded
                && (it.status as LightningOutgoingPayment.Part.Status.Succeeded).preimage == ByteVector32("4beedef8c67733399b4d037a5bf513d7be8cce70e95e640266c1f8cdf2744a2b")
        })
        Assert.assertTrue(payment5.status is LightningOutgoingPayment.Status.Completed.Succeeded)
        Assert.assertEquals(70_040_000.msat, payment5.amount)
        Assert.assertEquals(40_000.msat, payment5.fees)

        // 8th outgoing payments closes 1 channel mutually
        Assert.assertEquals(
            ChannelCloseOutgoingPayment(
                id = UUID.fromString("a7f837c1-0e8d-434c-8a12-d68780f2c0d0"),
                recipientAmount = 30_123.sat,
                address = "2NBPdqEiX2Wb9VNTqNBXBjgCAnHvhBD8sc3",
                isSentToDefaultAddress = false,
                miningFees = 0.sat,
                txId = TxId("24893dcd47403242e86e86344acfe69042bb5c1466df11cf43696fad7d29dfe3"),
                createdAt = 1656403710302,
                confirmedAt = 1656403710302,
                channelId = ByteVector32.fromValidHex("3749642f6caa1a13a9026f966eb13bd5a970ee237fb173d78602b2b31b7bc804"),
                closingType = ChannelClosingType.Mutual,
                lockedAt = 1656403710302,
            ),
            newOutgoingPayments[7]
        )
        Assert.assertEquals(30_123_000.msat, newOutgoingPayments[7].amount)
    }

    @Test
    fun test_migration_of_legacy_incoming_payments() {
        val connection = DriverManager.getConnection("jdbc:sqlite::memory:")
        val legacyPaymentsDb = SqlitePaymentsDb(connection)

        // insert legacy incoming payments
        val statement = connection.createStatement()
        InputStreamReader(this.javaClass.classLoader?.getResourceAsStream("legacy-incoming.sql") ?: throw RuntimeException("cannot load incoming payments from sql dump file"))
            .readLines().filter { it.isNotBlank() }.forEach {
                statement.executeUpdate(it)
            }

        // insert metadata
        InputStreamReader(this.javaClass.classLoader?.getResourceAsStream("legacy-metadata.sql") ?: throw RuntimeException("cannot load metadata from sql dump file"))
            .readLines().filter { it.isNotBlank() }.forEach {
                metadataDriver.execute(null, it, 0)
            }

        val legacyIncomingPayments = JavaConversions.asJavaCollection(legacyPaymentsDb.listAllIncomingPayments()).toList()
        Assert.assertEquals(8, legacyIncomingPayments.size)

        // transform legacy payments to modern IncomingPayment objects
        val newIncomingPayments = legacyIncomingPayments.map {
            val paymentHash = it.paymentRequest().paymentHash().bytes().toArray().byteVector32().toHex()
            LegacyMigrationHelper.modernizeLegacyIncomingPayment(
                paymentMeta = paymentMetaRepository.get(paymentHash),
                payToOpenMeta = payToOpenMetaRepository.get(paymentHash),
                payment = it
            )
        }

        // first incoming payment is a swap-in
        Assert.assertEquals(
            IncomingPayment(
                preimage = ByteVector32.fromValidHex("0c8a3899af5a793a77618823cc1873596181297b61c703ab45f0a5d39205c9ad"),
                origin = IncomingPayment.Origin.SwapIn(
                    address = "tb1qwq05evgh9pugurpthes5wld2nuu5f2s7u9pt2q"
                ),
                received = IncomingPayment.Received(
                    receivedWith = listOf(
                        IncomingPayment.ReceivedWith.NewChannel(
                            amountReceived = 32_000_000.msat,
                            serviceFee = 0.msat,
                            miningFee = 0.sat,
                            channelId = ByteVector32.Zeroes,
                            txId = TxId(ByteVector32.Zeroes),
                            confirmedAt = 1656333657766,
                            lockedAt = 1656333657766,
                        )
                    ),
                    receivedAt = 1656333657766
                ),
                createdAt = 1656333656000,
            ),
            newIncomingPayments[0]
        )

        // 2nd incoming payments pays a standard LN amountless invoice
        Assert.assertEquals(
            IncomingPayment(
                preimage = ByteVector32.fromValidHex("9f700505f700dc346a6690672b086c2baeeda1b7a914fb4bfabd3730fd840ed3"),
                origin = IncomingPayment.Origin.Invoice(
                    paymentRequest = Bolt11Invoice.read("lntb1p3tndtzpp50z03lfnmhacfhmmslwdcdkhrxcnrta7nt3vsv6nvv0n2gneqhvwqdqqxqyjw5q9qtzqqqqqq9qsqsp5x3e73hu5lz6jt8jt26hvnhdczymzmphkj9lqst6g5w626rel7uyqrzjqwfn3p9278ttzzpe0e00uhyxhned3j5d9acqak5emwfpflp8z2cnflc47j47yxdcyvqqqqlgqqqqqeqqjqhj5w494f0taflevylkhhzk0wjt7gep60zvkm7cg00vc7ql66z9xjmklquzq78ca3yfvk09vt90eqss9anvmg5ppk3nualqcex64qc3cpzz6cy8").get()
                ),
                received = IncomingPayment.Received(
                    receivedWith = listOf(
                        IncomingPayment.ReceivedWith.LightningPayment(
                            amountReceived = 55_000.msat,
                            channelId = ByteVector32.Zeroes,
                            htlcId = 0,
                            fundingFee = null,
                        )
                    ),
                    receivedAt = 1656337800788
                ),
                createdAt = 1656337762000,
            ),
            newIncomingPayments[1]
        )

        // 3rd incoming payment receives more than requested
        Assert.assertEquals(
            IncomingPayment(
                preimage = ByteVector32.fromValidHex("5dbb9d8be5fd2887128a878b38312e71419c884083ec8b4d080881e75f2cd529"),
                origin = IncomingPayment.Origin.Invoice(
                    paymentRequest = Bolt11Invoice.read("lntb2580n1p3tnd5cpp5vyrhevjunf4mgnc3l3lv3lvatfclju78sawspahq43dg78szr55sdz4v3jhxcmjd9c8g6t0dcsxgefqd3sjqun9w96u82n5v5sxgefqwpskjetdv4h8ggrpdfhh2axr49jjqctkv9h8gxqyjw5q9qtzqqqqqq9qsqsp5fyg2nucw7ylnxhq0tweqmayg40fpzyaam4yllgjurcp0um06l6sqrzjqwfn3p9278ttzzpe0e00uhyxhned3j5d9acqak5emwfpflp8z2cnflc47j47yxdcyvqqqqlgqqqqqeqqjqw87qyltfk38twf0duded5tcla7gyl9tftqhd50wekt2yflcq9gdsmdakwx5y8hlhzjmc8t55xcwdlc4f0qe0f2nlgkytx7u7xew9fzspse82tn").get()
                ),
                received = IncomingPayment.Received(
                    receivedWith = listOf(
                        IncomingPayment.ReceivedWith.LightningPayment(
                            amountReceived = 350_000.msat,
                            channelId = ByteVector32.Zeroes,
                            htlcId = 0,
                            fundingFee = null,
                        )
                    ),
                    receivedAt = 1656338085497
                ),
                createdAt = 1656338072000,
            ),
            newIncomingPayments[2]
        )
        Assert.assertEquals(258_000.msat, (newIncomingPayments[2]?.origin as? IncomingPayment.Origin.Invoice)?.paymentRequest?.amount)
        Assert.assertEquals(350_000.msat, newIncomingPayments[2]?.amount)

        // 4th incoming payment is a pay-to-open
        Assert.assertEquals(
            IncomingPayment(
                preimage = ByteVector32.fromValidHex("0aad090727a9b936703f139f8ebf6a00089ded54eb767d2701b47c4fe0f9c99b"),
                origin = IncomingPayment.Origin.Invoice(
                    paymentRequest = Bolt11Invoice.read("lntb1p3tn3vvpp59ynarkswknrea32cqm9h643rfpm0qlkn3x3aywv9lmfusg3442yqdq6wpshjgr5dusx7ur9dcs0p8u3jqxqyjw5q9qtzqqqqqq9qsqsp5twl37zl60tkgdtuyztrrsn6wtku4asr5dvqavz40rynswcl4tppsrzjqwfn3p9278ttzzpe0e00uhyxhned3j5d9acqak5emwfpflp8z2cnflc47j47yxdcyvqqqqlgqqqqqeqqjq9x2drlj03tjjt3m64whev6dukrzrqr5l09edhj55g30yruqz6fw3z5zc927nwy0wld686ua2mfzgz63vlwgmcamdld8wpjzrms8m4ygphfrpe0").get()
                ),
                received = IncomingPayment.Received(
                    receivedWith = listOf(
                        IncomingPayment.ReceivedWith.NewChannel(
                            amountReceived = 57_000_000.msat,
                            channelId = ByteVector32.Zeroes,
                            serviceFee = 3_000_000.msat,
                            miningFee = 0.sat,
                            txId = TxId(ByteVector32.Zeroes),
                            confirmedAt = 1656341949234,
                            lockedAt = 1656341949234,
                        )
                    ),
                    receivedAt = 1656341949234
                ),
                createdAt = 1656341900000,
            ),
            newIncomingPayments[3]
        )
        Assert.assertEquals(3_000_000.msat, newIncomingPayments[3]?.fees)

        // 7th incoming payment is a pay-to-open for a large amount
        Assert.assertEquals(
            IncomingPayment(
                preimage = ByteVector32.fromValidHex("7812c06a3f2d73fd8e30c65fb4c41744a7d60856e011396aff566782904502ee"),
                origin = IncomingPayment.Origin.Invoice(
                    paymentRequest = Bolt11Invoice.read("lntb1p3t4dcdpp58whdhw5lq62q4plz7w9z9awwwgcze27qaa052l7663w935xray5qdqqxqyjw5q9qtzqqqqqq9qsqsp5p5krwc4thmcy8ahsyrx2dfyhp7d2dxsxxsmlqxvmwstznzzmv8kqrzjqwfn3p9278ttzzpe0e00uhyxhned3j5d9acqak5emwfpflp8z2cnflc47j47yxdcyvqqqqlgqqqqqeqqjqaednnnjuxh0kvpa8l4mtt2wsum0jxhywsu5ak4m436shxpg0cjc46y7973npskzqk4kl4lhqxr3zmqn3euzx6dy4q474awm26j9amgcpef0uwk").get()
                ),
                received = IncomingPayment.Received(
                    receivedWith = listOf(
                        IncomingPayment.ReceivedWith.NewChannel(
                            amountReceived = 562_212_000.msat,
                            channelId = ByteVector32.Zeroes,
                            txId = TxId(ByteVector32.Zeroes),
                            serviceFee = 5_678_000.msat,
                            miningFee = 0.sat,
                            confirmedAt = 1656403752448,
                            lockedAt = 1656403752448,
                        )
                    ),
                    receivedAt = 1656403752448
                ),
                createdAt = 1656403725000,
            ),
            newIncomingPayments[6]
        )
        Assert.assertEquals(5_678_000.msat, newIncomingPayments[6]?.fees)
    }
}