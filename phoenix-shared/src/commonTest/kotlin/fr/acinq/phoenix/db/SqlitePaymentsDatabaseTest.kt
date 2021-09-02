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
import fr.acinq.bitcoin.Block
import fr.acinq.bitcoin.ByteVector32
import fr.acinq.bitcoin.Crypto
import fr.acinq.lightning.*
import fr.acinq.lightning.Lightning.randomBytes32
import fr.acinq.lightning.channel.ChannelUnavailable
import fr.acinq.lightning.channel.TooManyAcceptedHtlcs
import fr.acinq.lightning.db.HopDesc
import fr.acinq.lightning.db.IncomingPayment
import fr.acinq.lightning.db.OutgoingPayment
import fr.acinq.lightning.db.WalletPayment
import fr.acinq.lightning.payment.FinalFailure
import fr.acinq.lightning.payment.OutgoingPaymentFailure
import fr.acinq.lightning.payment.PaymentRequest
import fr.acinq.lightning.utils.*
import fr.acinq.lightning.wire.TemporaryNodeFailure
import fr.acinq.phoenix.db.payments.IncomingQueries
import fr.acinq.phoenix.runTest
import kotlin.test.*

class SqlitePaymentsDatabaseTest {
    private val db = SqlitePaymentsDb(testPaymentsDriver())

    private val preimage1 = randomBytes32()
    private val paymentHash1 = Crypto.sha256(preimage1).toByteVector32()
    private val origin1 = IncomingPayment.Origin.Invoice(createInvoice(preimage1))
    private val channelId1 = randomBytes32()
    private val receivedWith1 = setOf(IncomingPayment.ReceivedWith.LightningPayment(100_000.msat, channelId1, 1L))
    private val receivedWith3 = setOf(IncomingPayment.ReceivedWith.LightningPayment(150_000.msat, channelId1, 1L))

    private val preimage2 = randomBytes32()
    private val paymentHash2 = Crypto.sha256(preimage2).toByteVector32()
    private val origin2 = IncomingPayment.Origin.KeySend
    private val receivedWith2 = setOf(IncomingPayment.ReceivedWith.NewChannel(amount = 1_995_000.msat, fees = 5_000.msat, channelId = randomBytes32()))

    val origin3 = IncomingPayment.Origin.SwapIn(address = "1PwLgmRdDjy5GAKWyp8eyAC4SFzWuboLLb")

    @Test
    fun incoming__empty() = runTest {
        assertNull(db.getIncomingPayment(paymentHash1))
        assertEquals(listOf(), db.listReceivedPayments(10, 0))
        assertFailsWith(IncomingPaymentNotFound::class) { db.receivePayment(paymentHash1, receivedWith1, 5) }
    }

    @Test
    fun incoming__receive_lightning() = runTest {
        db.addIncomingPayment(preimage1, origin1, 0)
        db.listReceivedPayments(10, 0)[0].let {
            assertEquals(paymentHash1, it.paymentHash)
            assertEquals(preimage1, it.preimage)
            assertEquals(origin1, it.origin)
            assertNull(it.received)
        }
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
        db.listReceivedPayments(10, 0)[0].let {
            assertEquals(paymentHash1, it.paymentHash)
            assertEquals(preimage1, it.preimage)
            assertEquals(origin3, it.origin)
            assertNull(it.received)
        }
        db.receivePayment(paymentHash1, receivedWith2, 15)
        db.getIncomingPayment(paymentHash1)!!.let {
            assertEquals(paymentHash1, it.paymentHash)
            assertEquals(preimage1, it.preimage)
            assertEquals(origin3, it.origin)
            assertEquals(1_995_000.msat, it.amount)
            assertEquals(5_000.msat, it.fees)
            assertEquals(15, it.received?.receivedAt)
            assertEquals(receivedWith2, it.received?.receivedWith)
        }
    }

    @Test
    fun incoming__list() = runTest {
        db.addIncomingPayment(preimage1, origin1, 0)
        db.addIncomingPayment(preimage2, origin2, 15)

        // -- test ordering
        db.listReceivedPayments(10, 0).let {
            // older payments are last
            assertEquals(db.getIncomingPayment(paymentHash2), it[0])
            assertEquals(db.getIncomingPayment(paymentHash1), it[1])
        }
        db.receivePayment(paymentHash1, receivedWith1, 20)
        db.listReceivedPayments(10, 0).let {
            // reception date takes precedence over creation date
            assertEquals(db.getIncomingPayment(paymentHash1), it[0])
            assertTrue(it[0].received != null)
            assertEquals(db.getIncomingPayment(paymentHash2), it[1])
        }

        // -- test paging
        assertEquals(1, db.listReceivedPayments(10, 1).size)
        assertEquals(0, db.listReceivedPayments(10, 2).size)
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
        db.addAndReceivePayment(preimage1, origin3, receivedWith2)
        assertNotNull(db.getIncomingPayment(paymentHash1))
        assertEquals(1_995_000.msat, db.getIncomingPayment(paymentHash1)?.received?.amount)
        assertEquals(5_000.msat, db.getIncomingPayment(paymentHash1)!!.fees)
        assertEquals(origin3, db.getIncomingPayment(paymentHash1)!!.origin)
        assertEquals(receivedWith2, db.getIncomingPayment(paymentHash1)!!.received!!.receivedWith)
    }

    @Test
    fun incoming__is_expired() = runTest {
        val expiredInvoice =
            PaymentRequest.read("lntb1p0ufamxpp5l23zy5f8h2dcr8hxynptkcyuzdygy36pz76hgayp7n9q45a3cwuqdqqxqyjw5q9qtzqqqqqq9qsqsp5vusneyeywvawt4d7sslx3kx0eh7kk68l7j26qr0ge7z04lxhe5ssrzjqwfn3p9278ttzzpe0e00uhyxhned3j5d9acqak5emwfpflp8z2cnfluw6cwxn8wdcyqqqqlgqqqqqeqqjqmjvx0y3cfw54syp4jqw6jlj73qt97vxftjd3w3ywx6v2jqkdx9uxw3hk9qq6st9qyfpu3nzrpefwye63vmnyyzn6z8n7nkqsjj6lsaspu2p3mm")
        db.addIncomingPayment(preimage1, IncomingPayment.Origin.Invoice(expiredInvoice), 0)
        db.receivePayment(paymentHash1, receivedWith1, 10)
        assertTrue(db.getIncomingPayment(paymentHash1)!!.isExpired())
    }

    private fun createOutgoing(): OutgoingPayment {
        val (a, b, c) = listOf(Lightning.randomKey().publicKey(), Lightning.randomKey().publicKey(), Lightning.randomKey().publicKey())
        val pr = createInvoice(randomBytes32())
        return OutgoingPayment(
            UUID.randomUUID(),
            50_000.msat,
            pr.nodeId,
            OutgoingPayment.Details.Normal(pr),
            listOf(
                OutgoingPayment.Part(UUID.randomUUID(), 20_000.msat, listOf(HopDesc(a, c, ShortChannelId(42))), OutgoingPayment.Part.Status.Pending, 100),
                OutgoingPayment.Part(UUID.randomUUID(), 30_000.msat, listOf(HopDesc(a, b), HopDesc(b, c)), OutgoingPayment.Part.Status.Pending, 105)
            ),
            OutgoingPayment.Status.Pending,
            createdAt = 108,
        )
    }

    @Test
    fun outgoing__get() = runTest {
        val p = createOutgoing()
        db.addOutgoingPayment(p)
        assertEquals(p, db.getOutgoingPayment(p.id))
        assertNull(db.getOutgoingPayment(UUID.randomUUID()))
        p.parts.forEach { assertEquals(p, db.getOutgoingPart(it.id)) }
        assertNull(db.getOutgoingPart(UUID.randomUUID()))
    }

    @Test
    fun outgoing__get_status() = runTest {
        val p = createOutgoing()
        db.addOutgoingPayment(p)
        val onePartFailed = p.copy(
            parts = listOf(
                p.parts[0].copy(status = OutgoingPayment.Part.Status.Failed(TemporaryNodeFailure.code, TemporaryNodeFailure.message, 110)),
                p.parts[1]
            )
        )
        db.updateOutgoingPart(p.parts[0].id, Either.Right(TemporaryNodeFailure), 110)
        assertEquals(onePartFailed, db.getOutgoingPayment(p.id))
        p.parts.forEach { assertEquals(onePartFailed, db.getOutgoingPart(it.id)) }

        // We should never update non-existing parts.
        assertFalse { db.outQueries.updateOutgoingPart(UUID.randomUUID(), Either.Right(TemporaryNodeFailure), 110) }
        assertFalse { db.outQueries.updateOutgoingPart(UUID.randomUUID(), randomBytes32(), 110) }

        // Other payment parts are added.
        val newParts = listOf(
            OutgoingPayment.Part(UUID.randomUUID(), 5_000.msat, listOf(HopDesc(Lightning.randomKey().publicKey(), Lightning.randomKey().publicKey())), OutgoingPayment.Part.Status.Pending, 115),
            OutgoingPayment.Part(UUID.randomUUID(), 10_000.msat, listOf(HopDesc(Lightning.randomKey().publicKey(), Lightning.randomKey().publicKey())), OutgoingPayment.Part.Status.Pending, 120),
        )
        // Parts need a valid parent.
        if (isIOS()) {
            // This is a known bug in SQLDelight on iOS.
            // The problem is that foreign key constraints are disabled.
            // See iosDbFactory.kt for discussion.
        } else {
            assertFails { db.addOutgoingParts(UUID.randomUUID(), newParts) }
            // New parts must have a unique id.
            assertFails { db.addOutgoingParts(
                parentId = onePartFailed.id,
                parts = newParts.map { it.copy(id = p.parts[0].id) }
            ) }
        }

        // Can add new parts to existing payment.
        db.addOutgoingParts(onePartFailed.id, newParts)
        val withMoreParts = onePartFailed.copy(parts = onePartFailed.parts + newParts)
        assertEquals(withMoreParts, db.getOutgoingPayment(p.id))
        withMoreParts.parts.forEach { assertEquals(withMoreParts, db.getOutgoingPart(it.id)) }

        // Payment parts succeed.
        val preimage = randomBytes32()
        val partsSettled = withMoreParts.copy(
            parts = listOf(
                withMoreParts.parts[0], // this one was failed
                withMoreParts.parts[1].copy(status = OutgoingPayment.Part.Status.Succeeded(preimage, 125)),
                withMoreParts.parts[2].copy(status = OutgoingPayment.Part.Status.Succeeded(preimage, 126)),
                withMoreParts.parts[3].copy(status = OutgoingPayment.Part.Status.Succeeded(preimage, 127)),
            )
        )
        assertEquals(OutgoingPayment.Status.Pending, partsSettled.status)
        db.updateOutgoingPart(withMoreParts.parts[1].id, preimage, 125)
        db.updateOutgoingPart(withMoreParts.parts[2].id, preimage, 126)
        db.updateOutgoingPart(withMoreParts.parts[3].id, preimage, 127)
        assertEquals(partsSettled, db.getOutgoingPayment(p.id))
        partsSettled.parts.forEach { assertEquals(partsSettled, db.getOutgoingPart(it.id)) }

        // Parts are successful BUT parent payment is not successful yet.
        assertTrue(db.getOutgoingPayment(p.id)!!.status is OutgoingPayment.Status.Pending)

        val paymentStatus = OutgoingPayment.Status.Completed.Succeeded.OffChain(preimage, 130)
        val paymentSucceeded = partsSettled.copy(
            status = paymentStatus,
            parts = partsSettled.parts.drop(1)
        )
        db.completeOutgoingPayment(p.id, preimage, 130)

        // Failed and pending parts are now ignored because payment has succeeded
        assertEquals(paymentSucceeded, db.getOutgoingPayment(p.id))

        // Cannot succeed a payment that does not exist
        assertFalse { db.outQueries.completeOutgoingPayment(
            id = UUID.randomUUID(),
            completed = paymentStatus
        ) }
        // Using failed part id does not return a settled payment
        assertNull(db.getOutgoingPart(partsSettled.parts[0].id))
        partsSettled.parts.drop(1).forEach {
            assertEquals(paymentSucceeded, db.getOutgoingPart(it.id))
        }
    }

    @Test
    fun outgoing__do_not_reuse_ids() = runTest {
        val p = createOutgoing()
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
        val p = createOutgoing()
        db.addOutgoingPayment(p)
        val channelId = randomBytes32()
        val partsFailed = p.copy(
            parts = listOf(
                p.parts[0].copy(status = OutgoingPaymentFailure.convertFailure(Either.Right(TemporaryNodeFailure), 110)),
                p.parts[1].copy(status = OutgoingPaymentFailure.convertFailure(Either.Left(TooManyAcceptedHtlcs(channelId, 10)), 111)),
            )
        )
        db.updateOutgoingPart(p.parts[0].id, Either.Right(TemporaryNodeFailure), 110)
        db.updateOutgoingPart(p.parts[1].id, Either.Left(TooManyAcceptedHtlcs(channelId, 10)), 111)
        assertEquals(partsFailed, db.getOutgoingPayment(p.id))
        p.parts.forEach { assertEquals(partsFailed, db.getOutgoingPart(it.id)) }

        val paymentStatus = OutgoingPayment.Status.Completed.Failed(
            reason = FinalFailure.NoRouteToRecipient,
            completedAt = 120
        )
        val paymentFailed = partsFailed.copy(status = paymentStatus)
        db.completeOutgoingPayment(p.id, paymentStatus)
        assertEquals(paymentFailed, db.getOutgoingPayment(p.id))
        p.parts.forEach { assertEquals(paymentFailed, db.getOutgoingPart(it.id)) }

        // Cannot fail a payment that does not exist
        assertFalse { db.outQueries.completeOutgoingPayment(UUID.randomUUID(), paymentStatus) }
    }

    companion object {
        private val defaultFeatures = Features(
            Feature.VariableLengthOnion to FeatureSupport.Optional,
            Feature.PaymentSecret to FeatureSupport.Optional,
            Feature.BasicMultiPartPayment to FeatureSupport.Optional
        )

        private fun createInvoice(preimage: ByteVector32): PaymentRequest {
            return PaymentRequest.create(Block.LivenetGenesisBlock.hash, 150_000.msat, Crypto.sha256(preimage).toByteVector32(), Lightning.randomKey(), "invoice", CltvExpiryDelta(16), defaultFeatures)
        }
    }
}

expect fun testPaymentsDriver(): SqlDriver

// Workaround for known bugs in SQLDelight on native/iOS.
expect fun isIOS(): Boolean