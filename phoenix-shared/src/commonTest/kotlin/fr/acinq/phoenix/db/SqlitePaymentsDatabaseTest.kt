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
    private val receivedWith1 = IncomingPayment.ReceivedWith.LightningPayment

    private val preimage2 = randomBytes32()
    private val paymentHash2 = Crypto.sha256(preimage2).toByteVector32()
    private val origin2 = IncomingPayment.Origin.KeySend
    private val receivedWith2 = IncomingPayment.ReceivedWith.NewChannel(fees = MilliSatoshi(5000), channelId = randomBytes32())

    val origin3 = IncomingPayment.Origin.SwapIn(address = "1PwLgmRdDjy5GAKWyp8eyAC4SFzWuboLLb")

    @Test
    fun incoming__empty() = runTest {
        assertNull(db.getIncomingPayment(paymentHash1))
        assertEquals(listOf(), db.listReceivedPayments(10, 0))
        assertFailsWith(IncomingPaymentNotFound::class) { db.receivePayment(paymentHash1, MilliSatoshi(123), receivedWith1, 5) }
    }

    @Test
    fun incoming__receive() = runTest {
        db.addIncomingPayment(preimage1, origin1, 0)
        db.listReceivedPayments(10, 0)[0].let {
            assertEquals(paymentHash1, it.paymentHash)
            assertEquals(preimage1, it.preimage)
            assertEquals(origin1, it.origin)
            assertNull(it.received)
        }
        db.receivePayment(paymentHash1, MilliSatoshi(123), receivedWith1, 10)
        db.getIncomingPayment(paymentHash1)!!.let {
            assertEquals(paymentHash1, it.paymentHash)
            assertEquals(preimage1, it.preimage)
            assertEquals(origin1, it.origin)
            assertEquals(MilliSatoshi(123), it.received?.amount)
            assertEquals(10, it.received?.receivedAt)
            assertEquals(receivedWith1, it.received?.receivedWith)
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
        db.receivePayment(paymentHash1, MilliSatoshi(123), receivedWith1, 20)
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
        db.receivePayment(paymentHash1, MilliSatoshi(100), receivedWith1, 10)
        assertEquals(MilliSatoshi(100), db.getIncomingPayment(paymentHash1)?.received?.amount)
        db.receivePayment(paymentHash1, MilliSatoshi(50), receivedWith1, 20)
        assertEquals(MilliSatoshi(150), db.getIncomingPayment(paymentHash1)?.received?.amount)
    }

    @Test
    fun incoming__add_and_receive() = runTest {
        db.addAndReceivePayment(preimage1, origin3, MilliSatoshi(1234567), receivedWith2)
        assertNotNull(db.getIncomingPayment(paymentHash1))
        assertEquals(MilliSatoshi(1234567), db.getIncomingPayment(paymentHash1)?.received?.amount)
        assertEquals(origin3, db.getIncomingPayment(paymentHash1)!!.origin)
        assertEquals(receivedWith2, db.getIncomingPayment(paymentHash1)!!.received!!.receivedWith)
    }

    @Test
    fun incoming__is_expired() = runTest {
        val expiredInvoice =
            PaymentRequest.read("lntb1p0ufamxpp5l23zy5f8h2dcr8hxynptkcyuzdygy36pz76hgayp7n9q45a3cwuqdqqxqyjw5q9qtzqqqqqq9qsqsp5vusneyeywvawt4d7sslx3kx0eh7kk68l7j26qr0ge7z04lxhe5ssrzjqwfn3p9278ttzzpe0e00uhyxhned3j5d9acqak5emwfpflp8z2cnfluw6cwxn8wdcyqqqqlgqqqqqeqqjqmjvx0y3cfw54syp4jqw6jlj73qt97vxftjd3w3ywx6v2jqkdx9uxw3hk9qq6st9qyfpu3nzrpefwye63vmnyyzn6z8n7nkqsjj6lsaspu2p3mm")
        db.addIncomingPayment(preimage1, IncomingPayment.Origin.Invoice(expiredInvoice), 0)
        db.receivePayment(paymentHash1, MilliSatoshi(123), receivedWith1, 10)
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
        assertFails { db.updateOutgoingPart(UUID.randomUUID(), Either.Right(TemporaryNodeFailure)) }
        assertFails { db.updateOutgoingPart(UUID.randomUUID(), randomBytes32()) }

        // Other payment parts are added.
        val newParts = listOf(
            OutgoingPayment.Part(UUID.randomUUID(), 5_000.msat, listOf(HopDesc(Lightning.randomKey().publicKey(), Lightning.randomKey().publicKey())), OutgoingPayment.Part.Status.Pending, 115),
            OutgoingPayment.Part(UUID.randomUUID(), 10_000.msat, listOf(HopDesc(Lightning.randomKey().publicKey(), Lightning.randomKey().publicKey())), OutgoingPayment.Part.Status.Pending, 120),
        )
        // Parts need a valid parent.
        assertFails { db.addOutgoingParts(UUID.randomUUID(), newParts) }
        // New parts must have a unique id.
        assertFails { db.addOutgoingParts(onePartFailed.id, newParts.map { it.copy(id = p.parts[0].id) }) }

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

        val paymentSucceeded = partsSettled.copy(
            status = OutgoingPayment.Status.Completed.Succeeded.OffChain(preimage, 130),
            parts = partsSettled.parts.drop(1)
        )
        db.completeOutgoingPayment(p.id, preimage, 130)

        // Failed and pending parts are now ignored because payment has succeeded
        assertEquals(paymentSucceeded, db.getOutgoingPayment(p.id))

        // Cannot succeed a payment that does not exist
        assertFails { db.completeOutgoingPayment(UUID.randomUUID(), preimage, 130) }
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

        val paymentFailed = partsFailed.copy(status = OutgoingPayment.Status.Completed.Failed(FinalFailure.NoRouteToRecipient, 120))
        db.completeOutgoingPayment(p.id, FinalFailure.NoRouteToRecipient, 120)
        assertFails { db.completeOutgoingPayment(UUID.randomUUID(), FinalFailure.NoRouteToRecipient, 120) }
        assertEquals(paymentFailed, db.getOutgoingPayment(p.id))
        p.parts.forEach { assertEquals(paymentFailed, db.getOutgoingPart(it.id)) }
    }

    @Test
    fun outgoing__list_by_payment_hash() = runTest {

    }

    @Test
    fun outgoing__list_with_paging() = runTest {

    }

    @Test
    fun outgoing__list_all_payments() = runTest {
        val (preimage1, preimage2, preimage3, preimage4) = listOf(randomBytes32(), randomBytes32(), randomBytes32(), randomBytes32())

        val incoming1 = IncomingPayment(preimage1, IncomingPayment.Origin.Invoice(createInvoice(preimage1)), null, createdAt = 20)
        val incoming2 = IncomingPayment(preimage2, IncomingPayment.Origin.SwapIn("1PwLgmRdDjy5GAKWyp8eyAC4SFzWuboLLb"), null, createdAt = 21)
        val incoming3 = IncomingPayment(preimage3, IncomingPayment.Origin.Invoice(createInvoice(preimage3)), null, createdAt = 22)
        val incoming4 = IncomingPayment(preimage4, IncomingPayment.Origin.Invoice(createInvoice(preimage4)), null, createdAt = 23)
        listOf(incoming1, incoming2, incoming3, incoming4).forEach { db.addIncomingPayment(it.preimage, it.origin, it.createdAt) }

        // Pending incoming payments should not be listed.
        assertTrue(db.listPayments(count = 10, skip = 0).isEmpty())

        // Will succeed
        val outgoing1Parts = listOf(OutgoingPayment.Part(UUID.randomUUID(), 50_000.msat, listOf(HopDesc(Lightning.randomKey().publicKey(), Lightning.randomKey().publicKey(), ShortChannelId(42))), OutgoingPayment.Part.Status.Pending, 100))
        val outgoing1 = OutgoingPayment(UUID.randomUUID(), 50_000.msat, Lightning.randomKey().publicKey(), OutgoingPayment.Details.Normal(createInvoice(randomBytes32())), outgoing1Parts, OutgoingPayment.Status.Pending)
        // Will fail
        val outgoing2Parts = listOf(OutgoingPayment.Part(UUID.randomUUID(), 55_000.msat, listOf(HopDesc(Lightning.randomKey().publicKey(), Lightning.randomKey().publicKey(), ShortChannelId(42))), OutgoingPayment.Part.Status.Pending, 100))
        val outgoing2 = OutgoingPayment(UUID.randomUUID(), 55_000.msat, Lightning.randomKey().publicKey(), OutgoingPayment.Details.KeySend(randomBytes32()), outgoing2Parts, OutgoingPayment.Status.Pending)
        // Will succeed but first part will be a failure
        val outgoing3Parts = listOf(
            OutgoingPayment.Part(UUID.randomUUID(), 60_000.msat, listOf(HopDesc(Lightning.randomKey().publicKey(), Lightning.randomKey().publicKey(), ShortChannelId(42))), OutgoingPayment.Part.Status.Pending, 100),
            OutgoingPayment.Part(UUID.randomUUID(), 45_000.msat, listOf(HopDesc(Lightning.randomKey().publicKey(), Lightning.randomKey().publicKey(), ShortChannelId(42))), OutgoingPayment.Part.Status.Pending, 100),
            OutgoingPayment.Part(UUID.randomUUID(), 17_000.msat, listOf(HopDesc(Lightning.randomKey().publicKey(), Lightning.randomKey().publicKey(), ShortChannelId(43))), OutgoingPayment.Part.Status.Pending, 100)
        )
        val outgoing3 =
            OutgoingPayment(UUID.randomUUID(), 60_000.msat, Lightning.randomKey().publicKey(), OutgoingPayment.Details.SwapOut("1PwLgmRdDjy5GAKWyp8eyAC4SFzWuboLLb", randomBytes32()), outgoing3Parts, OutgoingPayment.Status.Pending)
        // Will stay pending
        val outgoing4 = OutgoingPayment(UUID.randomUUID(), 45_000.msat, Lightning.randomKey().publicKey(), OutgoingPayment.Details.Normal(createInvoice(randomBytes32())))
        // Will succeed but is overpaid...
        val outgoing5Parts = listOf(OutgoingPayment.Part(UUID.randomUUID(), 55_000.msat, listOf(HopDesc(Lightning.randomKey().publicKey(), Lightning.randomKey().publicKey(), ShortChannelId(42))), OutgoingPayment.Part.Status.Pending, 100))
        val outgoing5 = OutgoingPayment(UUID.randomUUID(), 35_000.msat, Lightning.randomKey().publicKey(), OutgoingPayment.Details.Normal(createInvoice(randomBytes32())), outgoing5Parts, OutgoingPayment.Status.Pending)
        val closing6 = OutgoingPayment.Details.ChannelClosing(randomBytes32(), "1PwLgmRdDjy5GAKWyp8eyAC4SFzWuboLLb", true)
        val outgoing6 = OutgoingPayment(UUID.randomUUID(), 100_000_000.msat, Lightning.randomKey().publicKey(), closing6, listOf(), OutgoingPayment.Status.Pending)

        // Add outgoing to database
        listOf(
            outgoing1, outgoing2, outgoing3, outgoing4, outgoing5, outgoing6
        ).forEach { db.addOutgoingPayment(it) }
        // Pending outgoing payments should be listed.
        assertEquals(6, db.listPayments(count = 10, skip = 0).size)

        // receive incoming1, should be last in resulting list, with a +20_000 msat amount
        db.receivePayment(incoming1.paymentHash, 20_000.msat, IncomingPayment.ReceivedWith.LightningPayment, receivedAt = 100)
        val expectedIncoming1 = incoming1.copy(received = IncomingPayment.Received(20_000.msat, IncomingPayment.ReceivedWith.LightningPayment, 100))

        // send outgoing 1 with 1 successful part
        db.updateOutgoingPart(outgoing1Parts[0].id, randomBytes32(), completedAt = 102)
        db.completeOutgoingPayment(outgoing1.id, randomBytes32(), completedAt = 103)
        val expectedOutgoing1 = db.getOutgoingPayment(outgoing1.id)
        assertNotNull(expectedOutgoing1)

        // receive incoming 2 with a bit more than expected
        db.receivePayment(incoming2.paymentHash, 25_000.msat, IncomingPayment.ReceivedWith.NewChannel(250.msat, channelId = null), receivedAt = 105)
        val expectedIncoming2 = incoming2.copy(received = IncomingPayment.Received(25_000.msat, IncomingPayment.ReceivedWith.NewChannel(250.msat, channelId = null), 105))

        // fail outgoing2
        db.updateOutgoingPart(outgoing2Parts[0].id, Either.Left(ChannelUnavailable(randomBytes32())), completedAt = 106)
        db.completeOutgoingPayment(outgoing2.id, FinalFailure.UnknownError, completedAt = 106)
        val expectedOutgoing2 = db.getOutgoingPayment(outgoing2.id)
        assertNotNull(expectedOutgoing2)

        // fail 1st part of outgoing3, succeed remaining parts
        db.updateOutgoingPart(outgoing3Parts[0].id, Either.Left(ChannelUnavailable(randomBytes32())), completedAt = 107)
        db.updateOutgoingPart(outgoing3Parts[1].id, randomBytes32(), completedAt = 107)
        db.updateOutgoingPart(outgoing3Parts[2].id, randomBytes32(), completedAt = 107)
        db.completeOutgoingPayment(outgoing3.id, randomBytes32(), completedAt = 107)
        val expectedOutgoing3 = db.getOutgoingPayment(outgoing3.id)
        assertNotNull(expectedOutgoing3)

        // receive incoming4
        db.receivePayment(incoming4.paymentHash, 10_000.msat, IncomingPayment.ReceivedWith.LightningPayment, receivedAt = 110)
        val expectedIncoming4 = incoming4.copy(received = IncomingPayment.Received(10_000.msat, IncomingPayment.ReceivedWith.LightningPayment, 110))

        // receive outgoing5, overpaying
        db.updateOutgoingPart(outgoing5Parts[0].id, randomBytes32(), completedAt = 111)
        db.completeOutgoingPayment(outgoing5.id, randomBytes32(), completedAt = 112)
        val expectedOutgoing5 = db.getOutgoingPayment(outgoing5.id)

        db.completeOutgoingPayment(outgoing6.id, randomBytes32(), completedAt = 114)
        val expectedOutgoing6 = db.getOutgoingPayment(outgoing6.id)

        // outgoing4 and incoming3 are still pending.
        val last10 = db.listPayments(count = 10, skip = 0)
        assertEquals(9, last10.size)
        assertEquals(outgoing4.id, (last10[0] as OutgoingPayment).id)
        assertEquals(OutgoingPayment.Status.Pending, (last10[0] as OutgoingPayment).status) // this payment is still pending!
        assertEquals(0.msat, (last10[0] as OutgoingPayment).parts.map { it.amount }.sum())
        assertEquals(expectedOutgoing6!!.id, (last10[1] as OutgoingPayment).id)
        assertTrue((last10[1] as OutgoingPayment).details is OutgoingPayment.Details.ChannelClosing)
        assertEquals(expectedOutgoing5!!.id, (last10[2] as OutgoingPayment).id)
        assertEquals(55_000.msat, (last10[2] as OutgoingPayment).parts.map { it.amount }.sum())
        assertEquals(expectedIncoming4.paymentHash, (last10[3] as IncomingPayment).paymentHash)
        assertEquals(expectedOutgoing3.id, (last10[4] as OutgoingPayment).id)
        assertEquals(62_000.msat, (last10[4] as OutgoingPayment).parts.map { it.amount }.sum())
        assertEquals(expectedOutgoing2.id, (last10[5] as OutgoingPayment).id)
        assertEquals(expectedIncoming2.paymentHash, (last10[6] as IncomingPayment).paymentHash)
        assertEquals(expectedOutgoing1.id, (last10[7] as OutgoingPayment).id)
        assertEquals(expectedIncoming1.paymentHash, (last10[8] as IncomingPayment).paymentHash)

        // test paging
        val last5after5 = db.listPayments(count = 5, skip = 5)
        assertEquals(expectedOutgoing2.id, (last5after5[0] as OutgoingPayment).id)
        assertEquals(expectedIncoming2.paymentHash, (last5after5[1] as IncomingPayment).paymentHash)
        assertEquals(expectedOutgoing1.id, (last5after5[2] as OutgoingPayment).id)
        assertEquals(expectedIncoming1.paymentHash, (last5after5[3] as IncomingPayment).paymentHash)
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