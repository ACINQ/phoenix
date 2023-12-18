/*
 * Copyright 2020 ACINQ SAS
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

import com.squareup.sqldelight.db.SqlDriver
import fr.acinq.bitcoin.*
import fr.acinq.lightning.*
import fr.acinq.lightning.Lightning.randomBytes32
import fr.acinq.lightning.channel.TooManyAcceptedHtlcs
import fr.acinq.lightning.db.*
import fr.acinq.lightning.payment.FinalFailure
import fr.acinq.lightning.payment.OutgoingPaymentFailure
import fr.acinq.lightning.payment.PaymentRequest
import fr.acinq.lightning.utils.*
import fr.acinq.lightning.wire.TemporaryNodeFailure
import fr.acinq.phoenix.data.WalletPaymentFetchOptions
import fr.acinq.phoenix.runTest
import fr.acinq.phoenix.utils.migrations.LegacyChannelCloseHelper
import fr.acinq.secp256k1.Hex
import org.kodein.log.LoggerFactory
import kotlin.test.*

class SqlitePaymentsDatabaseTest {
    private val db = SqlitePaymentsDb(LoggerFactory.default, testPaymentsDriver())

    private val preimage1 = randomBytes32()
    private val paymentHash1 = Crypto.sha256(preimage1).toByteVector32()
    private val origin1 = IncomingPayment.Origin.Invoice(createInvoice(preimage1))
    private val channelId1 = randomBytes32()
    private val receivedWith1 = listOf(IncomingPayment.ReceivedWith.LightningPayment(100_000.msat, channelId1, 1L))
    private val receivedWith3 = listOf(IncomingPayment.ReceivedWith.LightningPayment(150_000.msat, channelId1, 1L))

    private val preimage2 = randomBytes32()
    private val receivedWith2 = listOf(
        IncomingPayment.ReceivedWith.NewChannel(amount = 1_995_000.msat, serviceFee = 5_000.msat, channelId = randomBytes32(), txId = TxId(randomBytes32()), miningFee = 100.sat, confirmedAt = 100, lockedAt = 200)
    )

    val origin3 = IncomingPayment.Origin.SwapIn(address = "1PwLgmRdDjy5GAKWyp8eyAC4SFzWuboLLb")

    @Test
    fun incoming__receive_lightning() = runTest {
        db.addIncomingPayment(preimage1, origin1, 0)
        db.receivePayment(paymentHash1, receivedWith1, 10)
        db.getIncomingPayment(paymentHash1)!!.let {
            assertEquals(paymentHash1, it.paymentHash)
            assertEquals(preimage1, it.preimage)
            assertEquals(origin1, it.origin)
            assertEquals(100_000.msat, it.amount)
            assertEquals(0.msat, it.fees)
            assertEquals(10, it.received?.receivedAt)
            assertEquals(receivedWith1, it.received?.receivedWith)
        }
    }

    @Test
    fun incoming__receive_new_channel() = runTest {
        db.addIncomingPayment(preimage1, origin3, 0)
        db.receivePayment(paymentHash1, receivedWith2, 15)
        db.getIncomingPayment(paymentHash1)!!.let {
            assertEquals(paymentHash1, it.paymentHash)
            assertEquals(preimage1, it.preimage)
            assertEquals(origin3, it.origin)
            assertEquals(1_995_000.msat, it.amount)
            assertEquals(105_000.msat, it.fees)
            assertEquals(15, it.received?.receivedAt)
            assertEquals(receivedWith2, it.received?.receivedWith)
        }
    }

    @Test
    fun incoming__receive_new_channel_mpp_uneven_split() = runTest {
        val preimage = randomBytes32()
        val paymentHash = Crypto.sha256(preimage).toByteVector32()
        val origin = IncomingPayment.Origin.Invoice(createInvoice(preimage, 1_000_000_000.msat))
        val channelId = randomBytes32()
        val txId = TxId(randomBytes32())
        val mppPart1 = IncomingPayment.ReceivedWith.NewChannel(amount = 600_000_000.msat, serviceFee = 5_000.msat, miningFee = 100.sat, channelId = channelId, txId = txId, confirmedAt = 100, lockedAt = 50)
        val mppPart2 = IncomingPayment.ReceivedWith.NewChannel(amount = 400_000_000.msat, serviceFee = 5_000.msat, miningFee = 200.sat, channelId = channelId, txId = txId, confirmedAt = 115, lockedAt = 75)
        val receivedWith = listOf(mppPart1, mppPart2)

        db.addIncomingPayment(preimage, origin, 0)
        db.receivePayment(paymentHash, receivedWith, 150)
        assertEquals(2, db.getIncomingPayment(paymentHash)!!.received?.receivedWith?.size)
    }

    @Test
    fun incoming__receive_new_channel_mpp_even_split() = runTest {
        val preimage = randomBytes32()
        val paymentHash = Crypto.sha256(preimage).toByteVector32()
        val origin = IncomingPayment.Origin.Invoice(createInvoice(preimage, 1_000_000_000.msat))
        val channelId = randomBytes32()
        val txId = TxId(randomBytes32())
        val mppPart1 = IncomingPayment.ReceivedWith.NewChannel(amount = 500_000_000.msat, serviceFee = 5_000.msat, miningFee = 200.sat, channelId = channelId, txId = txId, confirmedAt = 100, lockedAt = 50)
        val mppPart2 = IncomingPayment.ReceivedWith.NewChannel(amount = 500_000_000.msat, serviceFee = 5_000.msat, miningFee = 150.sat, channelId = channelId, txId = txId, confirmedAt = 115, lockedAt = 75)
        val receivedWith = listOf(mppPart1, mppPart2)

        db.addIncomingPayment(preimage, origin, 0)
        db.receivePayment(paymentHash, receivedWith, 150)
        assertEquals(2, db.getIncomingPayment(paymentHash)!!.received?.receivedWith?.size)
    }

    @Test
    fun incoming__unique_payment_hash() = runTest {
        db.addIncomingPayment(preimage1, origin1, 0)
        assertFails { db.addIncomingPayment(preimage1, origin1, 0) } // payment hash is unique
    }

    @Test
    fun incoming__receive_should_sum() = runTest {
        db.addIncomingPayment(preimage1, origin1, 0)
        db.receivePayment(paymentHash1, receivedWith1, 10)
        assertEquals(100_000.msat, db.getIncomingPayment(paymentHash1)?.received?.amount)
        db.receivePayment(paymentHash1, receivedWith3, 20)
        assertEquals(250_000.msat, db.getIncomingPayment(paymentHash1)?.received?.amount)
    }

    @Test
    fun incoming__add_and_receive() = runTest {
        db.addIncomingPayment(preimage1, origin3, createdAt = 10)
        db.receivePayment(preimage1.sha256(), receivedWith2, receivedAt = 200)
        assertNotNull(db.getIncomingPayment(paymentHash1))
        assertEquals(1_995_000.msat, db.getIncomingPayment(paymentHash1)?.received?.amount)
        assertEquals(105_000.msat, db.getIncomingPayment(paymentHash1)!!.fees)
        assertEquals(origin3, db.getIncomingPayment(paymentHash1)!!.origin)
        assertEquals(receivedWith2, db.getIncomingPayment(paymentHash1)!!.received!!.receivedWith)
    }

    @Test
    fun incoming__is_expired() = runTest {
        val expiredInvoice =
            PaymentRequest.read("lntb1p0ufamxpp5l23zy5f8h2dcr8hxynptkcyuzdygy36pz76hgayp7n9q45a3cwuqdqqxqyjw5q9qtzqqqqqq9qsqsp5vusneyeywvawt4d7sslx3kx0eh7kk68l7j26qr0ge7z04lxhe5ssrzjqwfn3p9278ttzzpe0e00uhyxhned3j5d9acqak5emwfpflp8z2cnfluw6cwxn8wdcyqqqqlgqqqqqeqqjqmjvx0y3cfw54syp4jqw6jlj73qt97vxftjd3w3ywx6v2jqkdx9uxw3hk9qq6st9qyfpu3nzrpefwye63vmnyyzn6z8n7nkqsjj6lsaspu2p3mm").get()
        db.addIncomingPayment(preimage1, IncomingPayment.Origin.Invoice(expiredInvoice), 0)
        db.receivePayment(paymentHash1, receivedWith1, 10)
        assertTrue(db.getIncomingPayment(paymentHash1)!!.isExpired())
    }

    @Test
    fun incoming__purge_expired() = runTest {
        val expiredPreimage = randomBytes32()
        val expiredInvoice = PaymentRequest.create(
            chainHash = Block.TestnetGenesisBlock.hash,
            amount = 150_000.msat,
            paymentHash = Crypto.sha256(expiredPreimage).toByteVector32(),
            privateKey = Lightning.randomKey(),
            description = Either.Left("expired invoice"),
            minFinalCltvExpiryDelta = CltvExpiryDelta(16),
            features =  defaultFeatures,
            timestampSeconds = 1
        )
        db.addIncomingPayment(expiredPreimage, IncomingPayment.Origin.Invoice(expiredInvoice), 0)
        db.addIncomingPayment(preimage1, origin1, 100)
        db.receivePayment(paymentHash1, receivedWith1, 150)

        // -- the expired incoming payments list contains the expired payment
        var expiredPayments = db.listExpiredPayments(fromCreatedAt = 0, toCreatedAt = currentTimestampMillis())
        assertEquals(1, expiredPayments.size)
        assertEquals(expiredInvoice.paymentHash, expiredPayments[0].paymentHash)

        val isDeleted = db.removeIncomingPayment(expiredInvoice.paymentHash)
        assertTrue { isDeleted }

        expiredPayments = db.listExpiredPayments(fromCreatedAt = 0, toCreatedAt = currentTimestampMillis())
        assertEquals(0, expiredPayments.size)
    }

    private fun createOutgoingForLightning(): LightningOutgoingPayment {
        val (a, b, c) = listOf(Lightning.randomKey().publicKey(), Lightning.randomKey().publicKey(), Lightning.randomKey().publicKey())
        val pr = createInvoice(randomBytes32())
        return LightningOutgoingPayment(
            id = UUID.randomUUID(),
            recipientAmount = 50_000.msat,
            recipient = pr.nodeId,
            details = LightningOutgoingPayment.Details.Normal(pr),
            parts = listOf(
                LightningOutgoingPayment.Part(UUID.randomUUID(), 20_000.msat, listOf(HopDesc(a, c, ShortChannelId(42))), LightningOutgoingPayment.Part.Status.Pending, 100),
                LightningOutgoingPayment.Part(UUID.randomUUID(), 30_000.msat, listOf(HopDesc(a, b), HopDesc(b, c)), LightningOutgoingPayment.Part.Status.Pending, 105)
            ),
            status = LightningOutgoingPayment.Status.Pending,
            createdAt = 108,
        )
    }

    private fun createOutgoingForClosing() = ChannelCloseOutgoingPayment(
        id = UUID.randomUUID(),
        recipientAmount = 100_000.sat,
        address = "2MuvDe2JTFhkU3DfHCnxgyX6JdP7BKVgmaS",
        isSentToDefaultAddress = false,
        miningFees = 500.sat,
        channelId = randomBytes32(),
        txId = TxId(randomBytes32()),
        createdAt = 100,
        confirmedAt = null,
        lockedAt = null,
        closingType = ChannelClosingType.Local
    )

    @Test
    fun outgoing__get() = runTest {
        val p = createOutgoingForLightning()
        db.addOutgoingPayment(p)
        assertEquals(p, db.getLightningOutgoingPayment(p.id))
        assertNull(db.getLightningOutgoingPayment(UUID.randomUUID()))
        p.parts.forEach { assertEquals(p, db.getLightningOutgoingPaymentFromPartId(it.id)) }
        assertNull(db.getLightningOutgoingPaymentFromPartId(UUID.randomUUID()))
    }

    @Test
    fun outgoing__read_legacy_closing() = runTest {
        val close = LegacyChannelCloseHelper.convertLegacyToChannelClose(
            id = UUID.fromString("ff7f08e8-89d1-4731-be7c-ad37c9d09afc"),
            recipientAmount = 150_000.msat,
            detailsBlob = Hex.decode("7b226368616e6e656c4964223a2230303030303030303030303030303030303030303030303030303030303030303030303030303030303030303030303030303030303030303030303030303030222c22636c6f73696e6741646472657373223a22666f6f626172222c22697353656e74546f44656661756c7441646472657373223a747275657d"),
            statusBlob = Hex.decode("7b227478496473223a5b2265636632623763396366613734356532336634623661343766396365623139623066363330653064373365343434326566333236643364613234633930336635225d2c22636c61696d6564223a3130302c22636c6f73696e6754797065223a224c6f63616c227d"),
            partsAmount = 100.sat,
            partsTxId = null,
            partsClosingTypeBlob = null,
            createdAt = 100,
            confirmedAt = 200,
        )

        assertEquals("ff7f08e8-89d1-4731-be7c-ad37c9d09afc", close.id.toString())
        assertEquals(100.sat, close.recipientAmount)
        assertEquals(50_000.msat, close.fees)
        assertEquals("foobar", close.address)
        assertEquals(ByteVector32.Zeroes, close.channelId)
        assertEquals(true, close.isSentToDefaultAddress)
        assertEquals("ecf2b7c9cfa745e23f4b6a47f9ceb19b0f630e0d73e4442ef326d3da24c903f5", close.txId.toString())
        assertEquals(ChannelClosingType.Local, close.closingType)
        assertEquals(100, close.createdAt)
        assertEquals(200, close.confirmedAt)
    }

    @Test
    fun outgoing__get_status__lightning() = runTest {
        val p = createOutgoingForLightning()
        db.addOutgoingPayment(p)

        // Test status where a part has failed.
        val onePartFailed = p.copy(
            parts = listOf(
                p.parts[0].copy(
                    status = LightningOutgoingPayment.Part.Status.Failed(TemporaryNodeFailure.code, TemporaryNodeFailure.message, 110)
                ),
                p.parts[1]
            )
        )
        db.completeOutgoingLightningPart(p.parts[0].id, Either.Right(TemporaryNodeFailure), 110)
        assertEquals(onePartFailed, db.getLightningOutgoingPayment(p.id))
        p.parts.forEach { assertEquals(onePartFailed, db.getLightningOutgoingPaymentFromPartId(it.id)) }

        // Updating non-existing parts should fail.
        assertFalse { db.outQueries.updateLightningPart(UUID.randomUUID(), Either.Right(TemporaryNodeFailure), 110) }
        assertFalse { db.outQueries.updateLightningPart(UUID.randomUUID(), randomBytes32(), 110) }

        // Additional parts must have a unique id.
        val newParts = listOf(
            LightningOutgoingPayment.Part(UUID.randomUUID(), 5_000.msat, listOf(HopDesc(Lightning.randomKey().publicKey(), Lightning.randomKey().publicKey())), LightningOutgoingPayment.Part.Status.Pending, 115),
            LightningOutgoingPayment.Part(UUID.randomUUID(), 10_000.msat, listOf(HopDesc(Lightning.randomKey().publicKey(), Lightning.randomKey().publicKey())), LightningOutgoingPayment.Part.Status.Pending, 120),
        )
        assertFails { db.addOutgoingLightningParts(UUID.randomUUID(), newParts) }
        assertFails {
            db.addOutgoingLightningParts(
                parentId = onePartFailed.id,
                parts = newParts.map { it.copy(id = p.parts[0].id) }
            )
        }

        // Can add new parts to existing payment.
        db.addOutgoingLightningParts(onePartFailed.id, newParts)
        val withMoreParts = onePartFailed.copy(parts = onePartFailed.parts + newParts)
        assertEquals(withMoreParts, db.getLightningOutgoingPayment(p.id))
        withMoreParts.parts.forEach { assertEquals(withMoreParts, db.getLightningOutgoingPaymentFromPartId(it.id)) }

        // Test status when those new payments parts succeed.
        val preimage = randomBytes32()
        val partsSettled = withMoreParts.copy(
            parts = listOf(
                withMoreParts.parts[0], // this one was failed
                withMoreParts.parts[1].copy(status = LightningOutgoingPayment.Part.Status.Succeeded(preimage, 125)),
                withMoreParts.parts[2].copy(status = LightningOutgoingPayment.Part.Status.Succeeded(preimage, 126)),
                withMoreParts.parts[3].copy(status = LightningOutgoingPayment.Part.Status.Succeeded(preimage, 127)),
            )
        )
        assertEquals(LightningOutgoingPayment.Status.Pending, partsSettled.status)
        db.completeOutgoingLightningPart(withMoreParts.parts[1].id, preimage, 125)
        db.completeOutgoingLightningPart(withMoreParts.parts[2].id, preimage, 126)
        db.completeOutgoingLightningPart(withMoreParts.parts[3].id, preimage, 127)
        assertEquals(partsSettled, db.getLightningOutgoingPayment(p.id))
        partsSettled.parts.forEach { assertEquals(partsSettled, db.getLightningOutgoingPaymentFromPartId(it.id)) }

        // Parts are successful BUT parent payment is not successful yet.
        assertTrue(db.getLightningOutgoingPayment(p.id)!!.status is LightningOutgoingPayment.Status.Pending)

        val paymentStatus = LightningOutgoingPayment.Status.Completed.Succeeded.OffChain(preimage, 130)
        val paymentSucceeded = partsSettled.copy(
            status = paymentStatus,
            parts = partsSettled.parts.drop(1)
        )
        db.completeOutgoingPaymentOffchain(p.id, preimage, 130)

        // Failed and pending parts are now ignored because payment has succeeded
        assertEquals(paymentSucceeded, db.getLightningOutgoingPayment(p.id))

        // Should not be able to complete a payment that does not exist
        assertFalse {
            db.outQueries.completePayment(
                id = UUID.randomUUID(),
                completed = paymentStatus
            )
        }

        // Using failed part id does not return a settled payment
        assertNull(db.getLightningOutgoingPaymentFromPartId(partsSettled.parts[0].id))
        partsSettled.parts.drop(1).forEach {
            assertEquals(paymentSucceeded, db.getLightningOutgoingPaymentFromPartId(it.id))
        }
    }

    @Test
    fun outgoing__get_status__closing_txs() = runTest {
        val p = createOutgoingForClosing()
        db.addOutgoingPayment(p)
        db.getChannelCloseOutgoingPayment(p.id, WalletPaymentFetchOptions.None)!!.let { (paymentInDb, _) ->
            assertEquals(p, paymentInDb)
            assertEquals(null, paymentInDb.confirmedAt)
            assertEquals(null, paymentInDb.lockedAt)
        }

        db.setLocked(p.txId)
        db.getChannelCloseOutgoingPayment(p.id, WalletPaymentFetchOptions.None)!!.let { (paymentInDb, _) ->
            assertNotNull(paymentInDb.lockedAt)
        }

        db.setConfirmed(p.txId)
        db.getChannelCloseOutgoingPayment(p.id, WalletPaymentFetchOptions.None)!!.let { (paymentInDb, _) ->
            assertNotNull(paymentInDb.confirmedAt)
        }
    }

    @Test
    fun outgoing__do_not_reuse_ids() = runTest {
        val p = createOutgoingForLightning()
        db.addOutgoingPayment(p)
        assertFails { db.addOutgoingPayment(p) }
        p.copy(recipientAmount = 1000.msat).let {
            assertFails { db.addOutgoingPayment(it) }
        }
        p.copy(id = UUID.randomUUID(), parts = p.parts.map { it.copy(id = p.parts[0].id) }).let {
            assertFails { db.addOutgoingPayment(it) }
        }
    }

    @Test
    fun outgoing__fail_payment() = runTest {
        val p = createOutgoingForLightning()
        db.addOutgoingPayment(p)
        val channelId = randomBytes32()
        val partsFailed = p.copy(
            parts = listOf(
                p.parts[0].copy(status = OutgoingPaymentFailure.convertFailure(Either.Right(TemporaryNodeFailure), 110)),
                p.parts[1].copy(status = OutgoingPaymentFailure.convertFailure(Either.Left(TooManyAcceptedHtlcs(channelId, 10)), 111)),
            )
        )
        db.completeOutgoingLightningPart(p.parts[0].id, Either.Right(TemporaryNodeFailure), 110)
        db.completeOutgoingLightningPart(p.parts[1].id, Either.Left(TooManyAcceptedHtlcs(channelId, 10)), 111)
        assertEquals(partsFailed, db.getLightningOutgoingPayment(p.id))
        p.parts.forEach { assertEquals(partsFailed, db.getLightningOutgoingPaymentFromPartId(it.id)) }

        val paymentStatus = LightningOutgoingPayment.Status.Completed.Failed(
            reason = FinalFailure.NoRouteToRecipient,
            completedAt = 120
        )
        val paymentFailed = partsFailed.copy(status = paymentStatus)
        db.completeOutgoingPaymentOffchain(p.id, paymentStatus.reason, paymentStatus.completedAt)
        assertEquals(paymentFailed, db.getLightningOutgoingPayment(p.id))
        p.parts.forEach { assertEquals(paymentFailed, db.getLightningOutgoingPaymentFromPartId(it.id)) }

        // Cannot fail a payment that does not exist
        assertFalse { db.outQueries.completePayment(UUID.randomUUID(), paymentStatus) }
    }

    companion object {
        private val defaultFeatures = Features(
            Feature.VariableLengthOnion to FeatureSupport.Optional,
            Feature.PaymentSecret to FeatureSupport.Optional,
            Feature.BasicMultiPartPayment to FeatureSupport.Optional
        )

        private fun createInvoice(
            preimage: ByteVector32,
            msat: MilliSatoshi = 150_000.msat
        ): PaymentRequest {
            return PaymentRequest.create(
                chainHash = Block.LivenetGenesisBlock.hash,
                amount = msat,
                paymentHash = Crypto.sha256(preimage).toByteVector32(),
                privateKey = Lightning.randomKey(),
                description = Either.Left("invoice"),
                minFinalCltvExpiryDelta = CltvExpiryDelta(16),
                features = defaultFeatures
            )
        }
    }
}

expect fun testPaymentsDriver(): SqlDriver

// Workaround for known bugs in SQLDelight on native/iOS.
expect fun isIOS(): Boolean