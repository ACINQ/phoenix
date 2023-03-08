package fr.acinq.phoenix.utils

import fr.acinq.bitcoin.Block
import fr.acinq.bitcoin.ByteVector32
import fr.acinq.bitcoin.PrivateKey
import fr.acinq.bitcoin.PublicKey
import fr.acinq.lightning.CltvExpiryDelta
import fr.acinq.lightning.Lightning.randomBytes32
import fr.acinq.lightning.MilliSatoshi
import fr.acinq.lightning.db.ChannelClosingType
import fr.acinq.lightning.db.IncomingPayment
import fr.acinq.lightning.db.OutgoingPayment
import fr.acinq.lightning.payment.PaymentRequest
import fr.acinq.lightning.utils.*
import fr.acinq.phoenix.TestConstants
import fr.acinq.phoenix.data.*
import kotlin.test.Test
import kotlin.test.assertEquals

class CsvWriterTests {

    private val swapInAddress = "tb1qf72v4qyczf7ymmqtr8z3vfqn6dapzl3e7l6tjv"

    @Test
    fun testRow_Incoming_NewChannel() {
        val payment = IncomingPayment(
            preimage = randomBytes32(),
            origin = IncomingPayment.Origin.Invoice(makePaymentRequest()),
            received = IncomingPayment.Received(
                receivedWith = setOf(
                    IncomingPayment.ReceivedWith.NewChannel(
                        id = UUID.randomUUID(),
                        amount = 12_000_000.msat,
                        serviceFee = 3_000_000.msat,
                        fundingFee = 0.sat,
                        channelId = randomBytes32(),
                        confirmed = true
                    )
                ),
                receivedAt = 1675270272445
            ),
            createdAt = 0
        )
        val metadata = makeMetadata(
            exchangeRate = 22999.83,
            userNotes = "Via Lightning network"
        )

        val expected = "2023-02-01T16:51:12.445Z,12000000,-3000000,2.7599 USD,-0.6899 USD,Incoming LN payment,L2 Top-up,Via Lightning network\r\n"
        val actual = CsvWriter.makeRow(
            info = WalletPaymentInfo(payment, metadata, WalletPaymentFetchOptions.All),
            localizedDescription = "L2 Top-up",
            config = makeConfig()
        )

        assertEquals(expected, actual)
    }

    @Test
    fun testRow_Incoming_Payment() {
        val payment = IncomingPayment(
            preimage = randomBytes32(),
            origin = IncomingPayment.Origin.Invoice(makePaymentRequest()),
            received = IncomingPayment.Received(
                receivedWith = setOf(
                    IncomingPayment.ReceivedWith.LightningPayment(
                        amount = 2_173_929.msat,
                        channelId = randomBytes32(),
                        htlcId = 0
                    )
                ),
                receivedAt = 1675270484965
            ),
            createdAt = 0
        )
        val metadata = makeMetadata(
            exchangeRate = 22999.83,
            userNotes = null
        )

        val expected = "2023-02-01T16:54:44.965Z,2173929,0,0.4999 USD,0.0000 USD,Incoming LN payment,Cafécito,\r\n"
        val actual = CsvWriter.makeRow(
            info = WalletPaymentInfo(payment, metadata, WalletPaymentFetchOptions.All),
            localizedDescription = "Cafécito",
            config = makeConfig()
        )

        assertEquals(expected, actual)
    }

    @Test
    fun testRow_Outgoing_Payment() {
        val pr = makePaymentRequest()
        val payment = OutgoingPayment(
            id = UUID.randomUUID(),
            recipientAmount = 4351000.msat,
            recipient = PublicKey.Generator,
            details = OutgoingPayment.Details.Normal(pr),
            parts = listOf(
                makeLightningPart(4_354_435.msat)
            ),
            status = OutgoingPayment.Status.Completed.Succeeded.OffChain(
                preimage = randomBytes32(),
                completedAt = 1675270582248
            )
        )
        val metadata = makeMetadata(
            exchangeRate = 22999.83,
            userNotes = "Con quesito"
        )

        val expected = "2023-02-01T16:56:22.248Z,-4354435,-3435,-1.0015 USD,-0.0007 USD,Outgoing LN payment to ${pr.nodeId.toHex()},Arepa de Choclo,Con quesito\r\n"
        val actual = CsvWriter.makeRow(
            info = WalletPaymentInfo(payment, metadata, WalletPaymentFetchOptions.All),
            localizedDescription = "Arepa de Choclo",
            config = makeConfig()
        )

        assertEquals(expected, actual)
    }

    @Test
    fun testRow_Outgoing_Payment_WithCommaInNote() {
        val pr = makePaymentRequest()
        val payment = OutgoingPayment(
            id = UUID.randomUUID(),
            recipientAmount = 100_000.msat,
            recipient = PublicKey.Generator,
            details = OutgoingPayment.Details.Normal(pr),
            parts = listOf(
                makeLightningPart(103_010.msat)
            ),
            status = OutgoingPayment.Status.Completed.Succeeded.OffChain(
                preimage = randomBytes32(),
                completedAt = 1675270681099
            )
        )
        val metadata = makeMetadata(
            exchangeRate = 22999.83,
            userNotes = "This note, um, has a comma"
        )

        val expected = "2023-02-01T16:58:01.099Z,-103010,-3010,-0.0236 USD,-0.0006 USD,Outgoing LN payment to ${pr.nodeId.toHex()},Test 1,\"This note, um, has a comma\"\r\n"
        val actual = CsvWriter.makeRow(
            info = WalletPaymentInfo(payment, metadata, WalletPaymentFetchOptions.All),
            localizedDescription = "Test 1",
            config = makeConfig()
        )

        assertEquals(expected, actual)
    }

    @Test
    fun testRow_Outgoing_Payment_WithQuoteInNote() {
        val pr = makePaymentRequest()
        val payment = OutgoingPayment(
            id = UUID.randomUUID(),
            recipientAmount = 100_000.msat,
            recipient = PublicKey.Generator,
            details = OutgoingPayment.Details.Normal(pr),
            parts = listOf(
                makeLightningPart(103_010.msat)
            ),
            status = OutgoingPayment.Status.Completed.Succeeded.OffChain(
                preimage = randomBytes32(),
                completedAt = 1675270740742
            )
        )
        val metadata = makeMetadata(
            exchangeRate = 22999.83,
            userNotes = "This \"note\" has quotes"
        )

        val expected = "2023-02-01T16:59:00.742Z,-103010,-3010,-0.0236 USD,-0.0006 USD,Outgoing LN payment to ${pr.nodeId.toHex()},Test 2,\"This \"\"note\"\" has quotes\"\r\n"
        val actual = CsvWriter.makeRow(
            info = WalletPaymentInfo(payment, metadata, WalletPaymentFetchOptions.All),
            localizedDescription = "Test 2",
            config = makeConfig()
        )

        assertEquals(expected, actual)
    }

    @Test
    fun testRow_Outgoing_Payment_WithNewlineInNote() {
        val pr = makePaymentRequest()
        val payment = OutgoingPayment(
            id = UUID.randomUUID(),
            recipientAmount = 100_000.msat,
            recipient = PublicKey.Generator,
            details = OutgoingPayment.Details.Normal(pr),
            parts = listOf(
                makeLightningPart(103_010.msat)
            ),
            status = OutgoingPayment.Status.Completed.Succeeded.OffChain(
                preimage = randomBytes32(),
                completedAt = 1675270826945
            )
        )
        val metadata = makeMetadata(
            exchangeRate = 22999.83,
            userNotes = "This note has multiple lines:\nBrie\nCheddar\nAsiago"
        )

        val expected = "2023-02-01T17:00:26.945Z,-103010,-3010,-0.0236 USD,-0.0006 USD,Outgoing LN payment to ${pr.nodeId.toHex()},Test 3,\"This note has multiple lines:\n" +
                "Brie\n" +
                "Cheddar\n" +
                "Asiago\"\r\n"
        val actual = CsvWriter.makeRow(
            info = WalletPaymentInfo(payment, metadata, WalletPaymentFetchOptions.All),
            localizedDescription = "Test 3",
            config = makeConfig()
        )

        assertEquals(expected, actual)
    }

    @Test
    fun testRow_Incoming_NewChannel_DualSwapIn() {
        val payment = IncomingPayment(
            preimage = randomBytes32(),
            origin = IncomingPayment.Origin.DualSwapIn(localInputs = setOf()),
            received = IncomingPayment.Received(
                receivedWith = setOf(
                    IncomingPayment.ReceivedWith.NewChannel(
                        id = UUID.randomUUID(),
                        amount = 12_000_000.msat,
                        serviceFee = 2_931_000.msat,
                        fundingFee = 69.sat,
                        channelId = randomBytes32(),
                        confirmed = true
                    )
                ),
                receivedAt = 1675271683668
            ),
            createdAt = 0
        )
        val metadata = makeMetadata(
            exchangeRate = 22999.83,
            userNotes = "Via dual-funding flow"
        )

        val expected = "2023-02-01T17:14:43.668Z,12000000,-3000000,2.7599 USD,-0.6899 USD,Swap-in to tb1qf72v4qyczf7ymmqtr8z3vfqn6dapzl3e7l6tjv,L1 Top-up,Via dual-funding flow\r\n"
        val actual = CsvWriter.makeRow(
            info = WalletPaymentInfo(payment, metadata, WalletPaymentFetchOptions.All),
            localizedDescription = "L1 Top-up",
            config = makeConfig()
        )

        assertEquals(expected, actual)
    }

    @Test
    fun testRow_Outgoing_SwapOut() {
        val payment = OutgoingPayment(
            id = UUID.randomUUID(),
            recipientAmount = 12_820_000.msat,
            recipient = PublicKey.Generator,
            details = OutgoingPayment.Details.SwapOut(
                address = "tb1qlywh0dk40k87gqphpfs8kghd96hmnvus7r8hhf",
                paymentRequest = makePaymentRequest(),
                swapOutFee = 2_820.sat
            ),
            parts = listOf(
                makeLightningPart(12_000_000.msat),
                makeLightningPart(820_000.msat)
            ),
            status = OutgoingPayment.Status.Completed.Succeeded.OffChain(
                preimage = randomBytes32(),
                completedAt = 1675289814498
            )
        )
        val metadata = makeMetadata(
            exchangeRate = 23686.60,
            userNotes = null
        )

        val expected = "2023-02-01T22:16:54.498Z,-12820000,-2820000,-3.0366 USD,-0.6679 USD,Swap-out to tb1qlywh0dk40k87gqphpfs8kghd96hmnvus7r8hhf,Swap for cash,\r\n"
        val actual = CsvWriter.makeRow(
            info = WalletPaymentInfo(payment, metadata, WalletPaymentFetchOptions.All),
            localizedDescription = "Swap for cash",
            config = makeConfig()
        )

        assertEquals(expected, actual)
    }

    @Test
    fun testRow_Outgoing_ChannelClose() {
        val payment = OutgoingPayment(
            id = UUID.randomUUID(),
            recipientAmount = 8_690_464.msat,
            recipient = PublicKey.Generator,
            details = OutgoingPayment.Details.ChannelClosing(
                channelId = randomBytes32(),
                closingAddress = "tb1qz5gxe2450uadavle8wwcc5ngquqfj5xp4dy0ja",
                isSentToDefaultAddress = false
            ),
            parts = listOf(
                OutgoingPayment.ClosingTxPart(
                    id = UUID.randomUUID(),
                    txId = randomBytes32(),
                    claimed = 8_690.sat,
                    closingType = ChannelClosingType.Mutual,
                    createdAt = 0
                )
            ),
            status = OutgoingPayment.Status.Completed.Succeeded.OnChain(
                completedAt = 1675353533694
            )
        )
        val metadata = makeMetadata(
            exchangeRate = 23662.59,
            userNotes = null
        )

        val expected = "2023-02-02T15:58:53.694Z,-8690464,-464,-2.0563 USD,-0.0001 USD,Channel closing to tb1qz5gxe2450uadavle8wwcc5ngquqfj5xp4dy0ja,Channel closing,\r\n"
        val actual = CsvWriter.makeRow(
            info = WalletPaymentInfo(payment, metadata, WalletPaymentFetchOptions.All),
            localizedDescription = "Channel closing",
            config = makeConfig()
        )

        assertEquals(expected, actual)
    }

    /**
     * The only thing the CsvWriter reads from a PaymentRequest is the paymentHash.
     * So everything else can be fake.
     */
    private fun makePaymentRequest() =
        PaymentRequest.create(
            chainHash = Block.TestnetGenesisBlock.hash,
            amount = 10_000.msat,
            paymentHash = randomBytes32(),
            privateKey = PrivateKey(value = randomBytes32()),
            description = Either.Left("fake invoice"),
            minFinalCltvExpiryDelta = CltvExpiryDelta(128),
            features = TestConstants.Bob.nodeParams.features,
            paymentSecret = randomBytes32(),
            paymentMetadata = null,
            expirySeconds = null,
            extraHops = listOf(),
            timestampSeconds = currentTimestampSeconds()
        )

    private fun makeLightningPart(amount: MilliSatoshi) =
        OutgoingPayment.LightningPart(
            id = UUID.randomUUID(),
            amount = amount,
            route = listOf(),
            status = OutgoingPayment.LightningPart.Status.Succeeded(
                preimage = randomBytes32(),
                completedAt = 0
            ),
            createdAt = 0
        )

    private fun makeMetadata(exchangeRate: Double, userNotes: String?) =
        WalletPaymentMetadata(
            lnurl = null,
            originalFiat = ExchangeRate.BitcoinPriceRate(
                fiatCurrency = FiatCurrency.USD,
                price = exchangeRate,
                source = "originalFiat",
                timestampMillis = 0
            ),
            userDescription = null,
            userNotes = userNotes,
            modifiedAt = null
        )

    private fun makeConfig() =
        CsvWriter.Configuration(
            includesFiat = true,
            includesDescription = true,
            includesNotes = true,
            includesOriginDestination = true,
            swapInAddress = swapInAddress
        )
}