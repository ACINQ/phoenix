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
import fr.acinq.phoenix.db.payments.IncomingReceivedWithData
import fr.acinq.phoenix.runTest
import fr.acinq.secp256k1.Hex
import kotlin.test.*

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
            id = UUID.randomUUID(), amount = 7_000_000.msat, fees = 3_000_000.msat, channelId = channelId
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
    fun incoming__receivedWith_newChannel_legacy_no_uuid() = runTest {
        val blob = Hex.decode("bf6169bf68707265696d6167655820d77a5c6e17f70240c4a2aaf54fb1389188e482e85247f63c80417661e6a9b250666f726967696ebf64747970656a494e564f4943455f563064626c6f6259011b7b227061796d656e7452657175657374223a226c6e6263333530753170336464347874707035716c706868353476396e366737323439636435613463337a6735666b666b336a657377356370306b6a70393264366e377574777364717664396838766d6d6676646a73637170737370357a6a7275777a6c7677353732616e3934776b6b7630616370657077773068647a387134726466673679756b776466647032723571397179397173717738337771653868797130767061636a78336c7963777567657765787a307a3634796732343564377a717277653039676c7572716e34706466306d706539766336713065667739637533613773746575786c6a7730786e7970336d686a6565786664677a787163703564646d6b61227dff687265636569766564bf6274731b00000182172f3a3764747970656d4d554c544950415254535f563164626c6f625901ab5b7b2274797065223a2266722e6163696e712e70686f656e69782e64622e7061796d656e74732e496e636f6d696e67526563656976656457697468446174612e506172742e4e65774368616e6e656c2e5630222c22616d6f756e74223a7b226d736174223a373030303030307d2c2266656573223a7b226d736174223a333030303030307d2c226368616e6e656c4964223a2265386130653762613931613438356564363835373431356363306336306637376564613663623165626531646138343164343264376234333838636332626363227d2c7b2274797065223a2266722e6163696e712e70686f656e69782e64622e7061796d656e74732e496e636f6d696e67526563656976656457697468446174612e506172742e4e65774368616e6e656c2e5630222c22616d6f756e74223a7b226d736174223a393030303030307d2c2266656573223a7b226d736174223a363030303030307d2c226368616e6e656c4964223a2265386130653762613931613438356564363835373431356363306336306637376564613663623165626531646138343164343264376234333838636332626363227d5dff696372656174656441741b00000182172f3a37ff616ff6617600617040ff")
        val data = CloudData.cborDeserialize(blob)

        assertNotNull(data)
        val decoded = data.incoming?.unwrap()
        assertNotNull(decoded)

        val (uuid1, uuid2) = decoded.received!!.receivedWith.map { (it as IncomingPayment.ReceivedWith.NewChannel).id }
        val expectedPreimage = Hex.decode("d77a5c6e17f70240c4a2aaf54fb1389188e482e85247f63c80417661e6a9b250")
        val expectedChannelId = Hex.decode("e8a0e7ba91a485ed6857415cc0c60f77eda6cb1ebe1da841d42d7b4388cc2bcc").byteVector32()
        val expectedReceived = IncomingPayment.Received(
            receivedWith = setOf(
                IncomingPayment.ReceivedWith.NewChannel(id = uuid1, amount = 7_000_000.msat, fees = 3_000_000.msat, channelId = expectedChannelId),
                IncomingPayment.ReceivedWith.NewChannel(id = uuid2, amount = 9_000_000.msat, fees = 6_000_000.msat, channelId = expectedChannelId)
            ),
            receivedAt = 1658246347319
        )

        assertEquals(expectedPreimage.byteVector32(), decoded.preimage)
        assertEquals(expectedReceived, decoded.received)
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

    /**
     * Payments for channel closings created before lightning-kmp v1.3.0 may be using [PublicKey.Generator] for the recipient field. In that case, the public key will
     * be uncompressed and will be saved as such in the cloud. We still should be able to read it.
     */
    @Test
    fun read_legacy_uncompressed_pubkey() {
        val hexWithUncompressedKey = "bf6169f6616fbf626964782466663766303865382d383964312d343733312d626537632d616433376339643039616663646d7361741a019c00a869726563697069656e7458410479be667ef9dcbbac55a06295ce870b07029bfcdb2dce28d959f2815b16f81798483ada7726a3c4655da4fbfc0e1108a8fd17b448a68554199c47d08ffb10d4b86764657461696c73bf64747970656a434c4f53494e475f563064626c6f6258a57b226368616e6e656c4964223a2266663766303865383839643162373331626537636164333763396430396166633135313561313836333937396136346430646262363532313334303939623839222c22636c6f73696e6741646472657373223a2233475a695a5a733851477248347a61385a72516b58647271446a3266486431696a79222c22697353656e74546f44656661756c7441646472657373223a66616c73657dff6570617274739fff66737461747573bf6274731b0000017c2ccf0b7c6474797065745355434345454445445f4f4e434841494e5f563064626c6f6258757b227478496473223a5b2234386631633637343434353634393261623761353561373731356238383136313136383639333839303530643762643639386162393932346666396363633937225d2c22636c61696d6564223a32373030312c22636c6f73696e6754797065223a224d757475616c227dff696372656174656441741b0000017c2c654831ff617600617058d741c87b7b9c620bb98a56aa790919d5e6312b60d47d49cbcd443e84365eea438a9deebdb6d8c24f0d1885af2375df1fb3f16b98b4c8ef28d2c023cb728272dfed61ca66241bd4a67a558d04cf61cf0bd8d1b84ec6f7697fa55bd956dea59c8e5dbeb5094a64f7c07353df5cabe48c4dfaa929d8da331d0afe6da3305af524584382920828c71ec2e30225bc92b22c9cc038880b02e2d7d0e8c422d60400dcb241f5b61dfb1ba2f3a28e62aee9f18f833311f8987918043d68b4efb3318f046f9ad0c2aea172f62f8750389962a8a90f951e6f38dbf54588ff"
        val cloudData = CloudData.cborDeserialize(Hex.decode(hexWithUncompressedKey))
        assertEquals(65, cloudData?.outgoing?.recipient?.size)
        val payment = cloudData?.outgoing?.unwrap()
        assertTrue { payment != null }
        assertEquals(33, payment?.recipient?.value?.size())
        assertEquals(33, CloudData(payment!!, CloudDataVersion.V0).outgoing?.recipient?.size)
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