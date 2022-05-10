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
        testRoundtrip(
            OutgoingPayment(
                id = uuid,
                amount = 1_000_000.msat,
                recipient = publicKey,
                details = OutgoingPayment.Details.SwapOut(bitcoinAddress, paymentHash)
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
        // cbor encoded blob of a successful outgoing payment that was serialized before the update adding the closing txs parts.
        val blob = Hex.decode("bf6169f6616fbf626964782435366134613832332d343461622d346437342d616538302d666535323832636465313465646d7361741a0007a12069726563697069656e7458210325821dafe5a557b7f68a656b6199b5e30c8ea9758828002abda7e5bdd0ba64f56764657461696c73bf6474797065694e4f524d414c5f563064626c6f625901197b227061796d656e7452657175657374223a226c6e626335753170333835636c6e70703533686b686e67616c6730303366686e796c77686536797077346a7537676779796a396d686e7937766666616179797a386167797164717664396838766d6d6676646a73637170737370356533717575717177796c77666e7164377472667a6c706d7970776c34386e6d387330357a757a6b70366e74656865723466636173397179397173713735706e376c7079717438337270666130793671686833787735756b7a3730657a336b7339706a6b6571726b6838713972687678743838326565363236326432646633716472686d64646b3075377472717777337a3772636865337535743264706c3374706b6371366a7a393034227dff6570617274739fbf626964782463666561303330312d306635352d346465642d623661332d313933646532643137343831646d7361741a0003d09565726f75746578863032303966373230343661333730383634636232363937303862663365346131386632633664346236323564353032313463643435643333316661653662663732333a3033306135336565633736636234343232313031626537656661656564336137666433626164633338616166333530623934343730663761316334333332643434343a66737461747573bf6274731b00000180ae1670ff64747970656c5355434345454445445f563064626c6f62584f7b22707265696d616765223a2264353034386535306266363830616434633734316537643933353261356534653836383066303130373761666461613433336437343336303038343737323431227dff696372656174656441741b00000180ae1670ffffbf626964782436626665653366632d396632632d343630342d613836622d633338623465313463616531646d7361741a0003d09565726f75746578863032303966373230343661333730383634636232363937303862663365346131386632633664346236323564353032313463643435643333316661653662663732333a3033306135336565633736636234343232313031626537656661656564336137666433626164633338616166333530623934343730663761316334333332643434343a66737461747573bf6274731b00000180ae1670ff64747970656c5355434345454445445f563064626c6f62584f7b22707265696d616765223a2264353034386535306266363830616434633734316537643933353261356534653836383066303130373761666461613433336437343336303038343737323431227dff696372656174656441741b00000180ae1670ffffff66737461747573bf6274731b00000180ae1670ff6474797065755355434345454445445f4f4646434841494e5f563064626c6f62584f7b22707265696d616765223a2264353034386535306266363830616434633734316537643933353261356534653836383066303130373761666461613433336437343336303038343737323431227dff696372656174656441741b00000180ae1670ffff617600617040ff")
        // attempt to deserialize & extract payment
        val data = CloudData.cborDeserialize(blob)
        assertNotNull(data)
        val decoded = data.outgoing?.unwrap()
        assertNotNull(decoded)
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