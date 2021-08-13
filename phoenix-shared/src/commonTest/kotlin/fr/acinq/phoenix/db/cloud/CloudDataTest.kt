package fr.acinq.phoenix.db.cloud

import fr.acinq.bitcoin.*
import fr.acinq.lightning.*
import fr.acinq.lightning.Lightning.randomBytes32
import fr.acinq.lightning.Lightning.randomKey
import fr.acinq.lightning.db.ChannelClosingType
import fr.acinq.lightning.db.HopDesc
import fr.acinq.lightning.db.IncomingPayment
import fr.acinq.lightning.db.OutgoingPayment
import fr.acinq.lightning.payment.FinalFailure
import fr.acinq.lightning.payment.PaymentRequest
import fr.acinq.lightning.utils.UUID
import fr.acinq.lightning.utils.msat
import fr.acinq.lightning.utils.sat
import fr.acinq.lightning.utils.toByteVector32
import fr.acinq.phoenix.runTest
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class CloudDataTest {

    private val preimage = randomBytes32()
    private val paymentHash = Crypto.sha256(preimage).toByteVector32()

    private val bitcoinAddress = "1PwLgmRdDjy5GAKWyp8eyAC4SFzWuboLLb"

    private val channelId = randomBytes32()

    private val publicKey = randomKey().publicKey()
    private val uuid = UUID.randomUUID()

    fun testRoundtrip(incomingPayment: IncomingPayment) {
        // serialize payment into blob
        val blob = CloudData(incomingPayment, version = 0).cborSerialize()

        // attempt to deserialize & extract payment
        val data = CloudData.cborDeserialize(blob)
        assertNotNull(data)
        val decoded = data.incoming?.unwrap()
        assertNotNull(decoded)

        // test equality (no loss of information)
        assertTrue { incomingPayment == decoded }
    }

    fun testRoundtrip(outgoingPayment: OutgoingPayment) {
        // serialize payment into blob
        val blob = CloudData(outgoingPayment, version = 0).cborSerialize()

        // attempt to deserialize & extract payment
        val data = CloudData.cborDeserialize(blob)
        assertNotNull(data)
        val decoded = data.outgoing?.unwrap()
        assertNotNull(decoded)

        // test equality (no loss of information)
        assertTrue { outgoingPayment == decoded }
    }

    @Test
    fun incoming__invoice() = runTest {
        val invoice = createInvoice(preimage, 250_000.msat)
        testRoundtrip(IncomingPayment(
            preimage = preimage,
            origin = IncomingPayment.Origin.Invoice(invoice),
            received = null
        ))
    }

    @Test
    fun incoming__keySend() = runTest {
        testRoundtrip(IncomingPayment(
            preimage = preimage,
            origin = IncomingPayment.Origin.KeySend,
            received = null
        ))
    }

    @Test
    fun incoming__swapIn() = runTest {
        testRoundtrip(IncomingPayment(
            preimage = preimage,
            origin = IncomingPayment.Origin.SwapIn(bitcoinAddress),
            received = null
        ))
    }

    @Test
    fun incoming__receivedWith_lightning() = runTest {
        val invoice = createInvoice(preimage, 250_000.msat)
        val receivedWith1 = IncomingPayment.ReceivedWith.LightningPayment(
            amount = 100_000.msat, channelId = channelId, htlcId = 1L
        )
        val receivedWith2 = IncomingPayment.ReceivedWith.LightningPayment(
            amount = 150_000.msat, channelId = channelId, htlcId = 1L
        )
        testRoundtrip(IncomingPayment(
            preimage = preimage,
            origin = IncomingPayment.Origin.Invoice(invoice),
            received = IncomingPayment.Received(setOf(receivedWith1, receivedWith2))
        ))
    }

    @Test
    fun incoming__receivedWith_newChannel() = runTest {
        val invoice = createInvoice(preimage, 10_000_000.msat)
        val receivedWith = IncomingPayment.ReceivedWith.NewChannel(
            amount = 7_000_000.msat, fees = 3_000_000.msat, channelId = channelId
        )
        testRoundtrip(IncomingPayment(
            preimage = preimage,
            origin = IncomingPayment.Origin.Invoice(invoice),
            received = IncomingPayment.Received(setOf(receivedWith))
        ))
    }

    @Test
    fun outgoing__normal() = runTest {
        val invoice = createInvoice(preimage, 1_000_000.msat)
        testRoundtrip(OutgoingPayment(
            id = uuid,
            amount = 1_000_000.msat,
            recipient = publicKey,
            details = OutgoingPayment.Details.Normal(invoice)
        ))
    }

    @Test
    fun outgoing__keySend() = runTest {
        testRoundtrip(OutgoingPayment(
            id = uuid,
            amount = 1_000_000.msat,
            recipient = publicKey,
            details = OutgoingPayment.Details.KeySend(preimage)
        ))
    }

    @Test
    fun outgoing__swapOut() = runTest {
        testRoundtrip(OutgoingPayment(
            id = uuid,
            amount = 1_000_000.msat,
            recipient = publicKey,
            details = OutgoingPayment.Details.SwapOut(bitcoinAddress, paymentHash)
        ))
    }

    @Test
    fun outgoing__channelClosing() = runTest {
        testRoundtrip(OutgoingPayment(
            id = uuid,
            amount = 1_000_000.msat,
            recipient = publicKey,
            details = OutgoingPayment.Details.ChannelClosing(
                channelId = channelId,
                closingAddress = bitcoinAddress,
                isSentToDefaultAddress = true
            )
        ))
    }

    @Test
    fun outgoing__failed() = runTest {
        val recipientAmount = 500_000.msat
        val invoice = createInvoice(preimage, recipientAmount)
        val (a, b) = listOf(randomKey().publicKey(), randomKey().publicKey())
        val part = OutgoingPayment.Part(
            id = UUID.randomUUID(),
            amount = 500_005.msat,
            route = listOf(HopDesc(a, b)),
            status = OutgoingPayment.Part.Status.Failed(
                remoteFailureCode = 418,
                details = "I'm a teapot"
            )
        )
        val outgoingPayment = OutgoingPayment(
            id = uuid,
            recipientAmount = recipientAmount,
            recipient = publicKey,
            details = OutgoingPayment.Details.Normal(invoice),
            parts = listOf(part),
            status = OutgoingPayment.Status.Completed.Failed(FinalFailure.UnknownError)
        )

        // serialize payment into blob
        val blob = CloudData(outgoingPayment, version = 0).cborSerialize()

        // attempt to deserialize & extract payment
        val data = CloudData.cborDeserialize(blob)
        assertNotNull(data)
        val decoded = data.outgoing?.unwrap()
        assertNotNull(decoded)

        // test equality (no loss of information)
        assertTrue { outgoingPayment == decoded }
    }

    @Test
    fun outgoing__succeeded_onChain() = runTest {
        val recipientAmount = 500_000.msat
        val invoice = createInvoice(preimage, recipientAmount)
        val (a, b) = listOf(randomKey().publicKey(), randomKey().publicKey())
        val part1 = OutgoingPayment.Part(
            id = UUID.randomUUID(),
            amount = 250_005.msat,
            route = listOf(HopDesc(a, b)),
            status = OutgoingPayment.Part.Status.Succeeded(preimage)
        )
        val part2 = OutgoingPayment.Part(
            id = UUID.randomUUID(),
            amount = 250_005.msat,
            route = listOf(HopDesc(a, b)),
            status = OutgoingPayment.Part.Status.Succeeded(preimage)
        )
        testRoundtrip(OutgoingPayment(
            id = uuid,
            recipientAmount = recipientAmount,
            recipient = publicKey,
            details = OutgoingPayment.Details.Normal(invoice),
            parts = listOf(part1, part2),
            status = OutgoingPayment.Status.Completed.Succeeded.OffChain(preimage)
        ))
    }

    @Test
    fun outgoing__succeeded_offChain() = runTest {
        testRoundtrip(OutgoingPayment(
            id = uuid,
            recipientAmount = 1_000_000.msat,
            recipient = publicKey,
            details = OutgoingPayment.Details.ChannelClosing(
                channelId = channelId,
                closingAddress = bitcoinAddress,
                isSentToDefaultAddress = true
            ),
            parts = listOf(),
            status = OutgoingPayment.Status.Completed.Succeeded.OnChain(
                txids = listOf(randomBytes32()),
                claimed = 1_000.sat,
                closingType = ChannelClosingType.Mutual
            )
        ))
    }

    companion object {
        private val defaultFeatures = Features(
            Feature.VariableLengthOnion to FeatureSupport.Optional,
            Feature.PaymentSecret to FeatureSupport.Optional,
            Feature.BasicMultiPartPayment to FeatureSupport.Optional
        )

        private fun createInvoice(preimage: ByteVector32, amount: MilliSatoshi): PaymentRequest {
            return PaymentRequest.create(
                chainHash = Block.LivenetGenesisBlock.hash,
                amount = amount,
                paymentHash = Crypto.sha256(preimage).toByteVector32(),
                privateKey = randomKey(),
                description = "invoice",
                minFinalCltvExpiryDelta = CltvExpiryDelta(16),
                features = defaultFeatures
            )
        }
    }
}