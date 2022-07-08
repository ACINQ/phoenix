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
import fr.acinq.secp256k1.Hex
import kotlin.test.Test
import kotlin.test.assertEquals
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
        val blob = CloudData(incomingPayment, version = CloudDataVersion.V0).cborSerialize()

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
        val blob = CloudData(outgoingPayment, version = CloudDataVersion.V0).cborSerialize()

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
        testRoundtrip(
            IncomingPayment(
                preimage = preimage,
                origin = IncomingPayment.Origin.Invoice(invoice),
                received = null
            )
        )
    }

    @Test
    fun incoming__keySend() = runTest {
        testRoundtrip(
            IncomingPayment(
                preimage = preimage,
                origin = IncomingPayment.Origin.KeySend,
                received = null
            )
        )
    }

    @Test
    fun incoming__swapIn() = runTest {
        testRoundtrip(
            IncomingPayment(
                preimage = preimage,
                origin = IncomingPayment.Origin.SwapIn(bitcoinAddress),
                received = null
            )
        )
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
        testRoundtrip(
            IncomingPayment(
                preimage = preimage,
                origin = IncomingPayment.Origin.Invoice(invoice),
                received = IncomingPayment.Received(setOf(receivedWith1, receivedWith2))
            )
        )
    }

    @Test
    fun incoming__receivedWith_newChannel() = runTest {
        val invoice = createInvoice(preimage, 10_000_000.msat)
        val receivedWith = IncomingPayment.ReceivedWith.NewChannel(
            amount = 7_000_000.msat, fees = 3_000_000.msat, channelId = channelId
        )
        testRoundtrip(
            IncomingPayment(
                preimage = preimage,
                origin = IncomingPayment.Origin.Invoice(invoice),
                received = IncomingPayment.Received(setOf(receivedWith))
            )
        )
    }

    @Test
    fun outgoing__normal() = runTest {
        val invoice = createInvoice(preimage, 1_000_000.msat)
        testRoundtrip(
            OutgoingPayment(
                id = uuid,
                amount = 1_000_000.msat,
                recipient = publicKey,
                details = OutgoingPayment.Details.Normal(invoice)
            )
        )
    }

    @Test
    fun outgoing__keySend() = runTest {
        testRoundtrip(
            OutgoingPayment(
                id = uuid,
                amount = 1_000_000.msat,
                recipient = publicKey,
                details = OutgoingPayment.Details.KeySend(preimage)
            )
        )
    }

    @Test
    fun outgoing__swapOut() = runTest {
        val invoice = createInvoice(preimage, 1_000_000.msat)
        testRoundtrip(
            OutgoingPayment(
                id = uuid,
                amount = 1_000_000.msat,
                recipient = publicKey,
                details = OutgoingPayment.Details.SwapOut(bitcoinAddress, invoice, 2_500.sat)
            )
        )
    }

    @Test
    fun outgoing__channelClosing() = runTest {
        testRoundtrip(
            OutgoingPayment(
                id = uuid,
                amount = 1_000_000.msat,
                recipient = publicKey,
                details = OutgoingPayment.Details.ChannelClosing(
                    channelId = channelId,
                    closingAddress = bitcoinAddress,
                    isSentToDefaultAddress = true
                )
            )
        )
    }

    @Test
    fun outgoing__channelClosed() = runTest {
        testRoundtrip(
            OutgoingPayment(
                id = uuid,
                recipient = publicKey,
                recipientAmount = 50_000_000.msat,
                details = OutgoingPayment.Details.ChannelClosing(
                    channelId = channelId,
                    closingAddress = bitcoinAddress,
                    isSentToDefaultAddress = true
                ),
                status = OutgoingPayment.Status.Completed.Succeeded.OnChain(completedAt = 200),
                parts = listOf(
                    OutgoingPayment.ClosingTxPart(
                        id = UUID.randomUUID(),
                        txId = randomBytes32(),
                        claimed = 40_000.sat,
                        closingType = ChannelClosingType.Mutual,
                        createdAt = 100
                    ),
                    OutgoingPayment.ClosingTxPart(
                        id = UUID.randomUUID(),
                        txId = randomBytes32(),
                        claimed = 10_000.sat,
                        closingType = ChannelClosingType.Local,
                        createdAt = 110
                    ),
                ),
                createdAt = 120
            )
        )
    }

    @Test
    fun outgoing__failed() = runTest {
        val recipientAmount = 500_000.msat
        val invoice = createInvoice(preimage, recipientAmount)
        val (a, b) = listOf(randomKey().publicKey(), randomKey().publicKey())
        val part = OutgoingPayment.LightningPart(
            id = UUID.randomUUID(),
            amount = 500_005.msat,
            route = listOf(HopDesc(a, b)),
            status = OutgoingPayment.LightningPart.Status.Failed(
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
        val blob = CloudData(outgoingPayment, version = CloudDataVersion.V0).cborSerialize()

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
        val part1 = OutgoingPayment.LightningPart(
            id = UUID.randomUUID(),
            amount = 250_005.msat,
            route = listOf(HopDesc(a, b)),
            status = OutgoingPayment.LightningPart.Status.Succeeded(preimage)
        )
        val part2 = OutgoingPayment.LightningPart(
            id = UUID.randomUUID(),
            amount = 250_005.msat,
            route = listOf(HopDesc(a, b)),
            status = OutgoingPayment.LightningPart.Status.Succeeded(preimage)
        )
        testRoundtrip(
            OutgoingPayment(
                id = uuid,
                recipientAmount = recipientAmount,
                recipient = publicKey,
                details = OutgoingPayment.Details.Normal(invoice),
                parts = listOf(part1, part2),
                status = OutgoingPayment.Status.Completed.Succeeded.OffChain(preimage)
            )
        )
    }

    @Test
    fun outgoing__succeeded_offChain() = runTest {
        testRoundtrip(
            OutgoingPayment(
                id = uuid,
                recipientAmount = 1_000_000.msat,
                recipient = publicKey,
                details = OutgoingPayment.Details.ChannelClosing(
                    channelId = channelId,
                    closingAddress = bitcoinAddress,
                    isSentToDefaultAddress = true
                ),
                parts = listOf(),
                status = OutgoingPayment.Status.Completed.Succeeded.OnChain()
            )
        )
    }

    @Test
    fun outgoing__parts_before_closing_txs() {
        // Cbor encoded blob of a successful outgoing payment that was serialized before the update adding the closing txs parts.
        // That payment contains 2 closing txs spending 50 000 sat with a 2 000 sat fee, information that are stored in the payment's status.
        val blob = Hex.decode(
            "bf6169f6616fbf626964782439643966303836642d313134642d343461312d613430342d313037353139303061376564646d7361741a02faf08069726563697069656e74582103933884aaf1d6b108397e5efe5c86bcf2d8ca8d2f700eda99db9214fc2712b1346764657461696c73bf64747970656a434c4f53494e475f563064626c6f6258a67b226368616e6e656c4964223a2238306362383332643266633631376462393032326133633239623432383038666232623132366563316265383862643065343830393734653734633034623738222c22636c6f73696e6741646472657373223a22324e336d437334724a4767775a34683164593338486637764c69775a4c44564e7a6834222c22697353656e74546f44656661756c7441646472657373223a66616c73657dff6570617274739fff66737461747573bf6274731b00000180fc53a4ea6474797065745355434345454445445f4f4e434841494e5f563064626c6f6258b77b227478496473223a5b2235333664373464666635323630653039633466393433343637393837383231343362633063353364653132636636623662666561663837666466376661303030222c2232383937353938396537333261653863643461623461323833376130383331636533313164303135633638353766343938653731613462323337656465346334225d2c22636c61696d6564223a34383030302c22636c6f73696e6754797065223a224c6f63616c227dff696372656174656441741b00000180fc53a4eaff617600617040ff"
        )
        // attempt to deserialize & extract payment
        val data = CloudData.cborDeserialize(blob)
        assertNotNull(data)
        val decoded = data.outgoing?.unwrap()
        assertNotNull(decoded)
        assertEquals(50_000_000.msat, decoded.amount)
        assertEquals(50_000_000.msat, decoded.recipientAmount)
        assertEquals(2_000_000.msat, decoded.fees)
        assertTrue { decoded.parts.isNotEmpty() }
        assertEquals(2, decoded.parts.filterIsInstance<OutgoingPayment.ClosingTxPart>().size)
        assertTrue { decoded.details is OutgoingPayment.Details.ChannelClosing }
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