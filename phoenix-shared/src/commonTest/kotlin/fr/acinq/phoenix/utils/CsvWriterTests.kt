package fr.acinq.phoenix.utils

import fr.acinq.bitcoin.Chain
import fr.acinq.bitcoin.OutPoint
import fr.acinq.bitcoin.PrivateKey
import fr.acinq.bitcoin.PublicKey
import fr.acinq.bitcoin.TxId
import fr.acinq.bitcoin.utils.Either
import fr.acinq.lightning.CltvExpiryDelta
import fr.acinq.lightning.Lightning.randomBytes32
import fr.acinq.lightning.MilliSatoshi
import fr.acinq.lightning.db.Bolt11IncomingPayment
import fr.acinq.lightning.db.ChannelCloseOutgoingPayment
import fr.acinq.lightning.db.ChannelCloseOutgoingPayment.ChannelClosingType
import fr.acinq.lightning.db.LightningIncomingPayment
import fr.acinq.lightning.db.LightningOutgoingPayment
import fr.acinq.lightning.db.NewChannelIncomingPayment
import fr.acinq.lightning.db.SpliceInIncomingPayment
import fr.acinq.lightning.payment.Bolt11Invoice
import fr.acinq.lightning.utils.UUID
import fr.acinq.lightning.utils.currentTimestampMillis
import fr.acinq.lightning.utils.currentTimestampSeconds
import fr.acinq.lightning.utils.msat
import fr.acinq.lightning.utils.sat
import fr.acinq.lightning.wire.LiquidityAds
import fr.acinq.phoenix.TestConstants
import fr.acinq.phoenix.data.ExchangeRate
import fr.acinq.phoenix.data.FiatCurrency
import fr.acinq.phoenix.data.WalletPaymentInfo
import fr.acinq.phoenix.data.WalletPaymentMetadata
import kotlin.test.Test
import kotlin.test.assertEquals


class CsvWriterTests {

    private val swapInAddress = "tb1qf72v4qyczf7ymmqtr8z3vfqn6dapzl3e7l6tjv"

    @Test
    fun testRow_Incoming_NewChannel() {
        val payment = SpliceInIncomingPayment(
            id = UUID.randomUUID(),
            amountReceived = 100_000_000.msat,
            miningFee = 4_000.sat,
            channelId = randomBytes32(),
            txId = TxId(randomBytes32()),
            localInputs = setOf(),
            createdAt = 1675270270000L,
            lockedAt = 1675270272445L,
            confirmedAt = 1675270273000L,
        )
        val metadata = makeMetadata(
            exchangeRate = 22999.83,
            userNotes = "Via Lightning network"
        )

        val expected = "2023-02-01T16:51:12.445Z,100000000,-4000000,22.9998 USD,-0.9199 USD,Incoming on-chain payment,L2 Top-up,Via Lightning network\r\n"
        val actual = CsvWriter.makeRow(
            info = WalletPaymentInfo(payment, metadata, null),
            localizedDescription = "L2 Top-up",
            config = makeConfig()
        )

        assertEquals(expected, actual)
    }

    @Test
    fun testRow_Incoming_Payment() {
        val payment = Bolt11IncomingPayment(
            preimage = randomBytes32(),
            paymentRequest = makePaymentRequest(),
            parts = listOf(
                LightningIncomingPayment.Part.Htlc(
                    amountReceived = 12_000_000.msat,
                    channelId = randomBytes32(),
                    htlcId = 1L,
                    fundingFee = null,
                    receivedAt = 1675270272445L,
                )
            ),
            createdAt = 1675270270000L
        )
        val metadata = makeMetadata(
            exchangeRate = 22999.83,
            userNotes = "Via Lightning network"
        )

        val expected = "2023-02-01T16:51:12.445Z,12000000,0,2.7599 USD,0.0000 USD,Incoming LN payment,Cafécito,Via Lightning network\r\n"
        val actual = CsvWriter.makeRow(
            info = WalletPaymentInfo(payment, metadata, null),
            localizedDescription = "Cafécito",
            config = makeConfig()
        )

        assertEquals(expected, actual)
    }

    @Test
    fun testRow_Outgoing_Payment() {
        val pr = makePaymentRequest()
        val payment = LightningOutgoingPayment(
            id = UUID.randomUUID(),
            recipientAmount = 4351000.msat,
            recipient = PublicKey.Generator,
            details = LightningOutgoingPayment.Details.Normal(pr),
            parts = listOf(
                makeLightningPart(4_354_435.msat)
            ),
            status = LightningOutgoingPayment.Status.Succeeded(
                preimage = randomBytes32(),
                completedAt = 1675270582248
            ),
            createdAt = currentTimestampMillis()
        )
        val metadata = makeMetadata(
            exchangeRate = 22999.83,
            userNotes = "Con quesito"
        )

        val expected = "2023-02-01T16:56:22.248Z,-4354435,-3435,-1.0015 USD,-0.0007 USD,Outgoing LN payment to ${pr.nodeId.toHex()},Arepa de Choclo,Con quesito\r\n"
        val actual = CsvWriter.makeRow(
            info = WalletPaymentInfo(payment, metadata, null),
            localizedDescription = "Arepa de Choclo",
            config = makeConfig()
        )

        assertEquals(expected, actual)
    }

    @Test
    fun testRow_Outgoing_Payment_WithCommaInNote() {
        val pr = makePaymentRequest()
        val payment = LightningOutgoingPayment(
            id = UUID.randomUUID(),
            recipientAmount = 100_000.msat,
            recipient = PublicKey.Generator,
            details = LightningOutgoingPayment.Details.Normal(pr),
            parts = listOf(
                makeLightningPart(103_010.msat)
            ),
            status = LightningOutgoingPayment.Status.Succeeded(
                preimage = randomBytes32(),
                completedAt = 1675270681099
            ),
            createdAt = currentTimestampMillis()
        )
        val metadata = makeMetadata(
            exchangeRate = 22999.83,
            userNotes = "This note, um, has a comma"
        )

        val expected = "2023-02-01T16:58:01.099Z,-103010,-3010,-0.0236 USD,-0.0006 USD,Outgoing LN payment to ${pr.nodeId.toHex()},Test 1,\"This note, um, has a comma\"\r\n"
        val actual = CsvWriter.makeRow(
            info = WalletPaymentInfo(payment, metadata, null),
            localizedDescription = "Test 1",
            config = makeConfig()
        )

        assertEquals(expected, actual)
    }

    @Test
    fun testRow_Outgoing_Payment_WithQuoteInNote() {
        val pr = makePaymentRequest()
        val payment = LightningOutgoingPayment(
            id = UUID.randomUUID(),
            recipientAmount = 100_000.msat,
            recipient = PublicKey.Generator,
            details = LightningOutgoingPayment.Details.Normal(pr),
            parts = listOf(
                makeLightningPart(103_010.msat)
            ),
            status = LightningOutgoingPayment.Status.Succeeded(
                preimage = randomBytes32(),
                completedAt = 1675270740742
            ),
            createdAt = currentTimestampMillis()
        )
        val metadata = makeMetadata(
            exchangeRate = 22999.83,
            userNotes = "This \"note\" has quotes"
        )

        val expected = "2023-02-01T16:59:00.742Z,-103010,-3010,-0.0236 USD,-0.0006 USD,Outgoing LN payment to ${pr.nodeId.toHex()},Test 2,\"This \"\"note\"\" has quotes\"\r\n"
        val actual = CsvWriter.makeRow(
            info = WalletPaymentInfo(payment, metadata, null),
            localizedDescription = "Test 2",
            config = makeConfig()
        )

        assertEquals(expected, actual)
    }

    @Test
    fun testRow_Outgoing_Payment_WithNewlineInNote() {
        val pr = makePaymentRequest()
        val payment = LightningOutgoingPayment(
            id = UUID.randomUUID(),
            recipientAmount = 100_000.msat,
            recipient = PublicKey.Generator,
            details = LightningOutgoingPayment.Details.Normal(pr),
            parts = listOf(
                makeLightningPart(103_010.msat)
            ),
            status = LightningOutgoingPayment.Status.Succeeded(
                preimage = randomBytes32(),
                completedAt = 1675270826945
            ),
            createdAt = currentTimestampMillis()
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
            info = WalletPaymentInfo(payment, metadata, null),
            localizedDescription = "Test 3",
            config = makeConfig()
        )

        assertEquals(expected, actual)
    }

    @Test
    fun testRow_Incoming_NewChannel_DualSwapIn() {
        val payment = NewChannelIncomingPayment(
            id = UUID.randomUUID(),
            amountReceived = 100_000_000.msat,
            liquidityPurchase = LiquidityAds.Purchase.Standard(amount = 0.sat, fees = LiquidityAds.Fees(serviceFee = 5_000.sat, miningFee = 0.sat), paymentDetails = LiquidityAds.PaymentDetails.FromChannelBalance),
            miningFee = 2_000.sat,
            channelId = randomBytes32(),
            txId = TxId(randomBytes32()),
            localInputs = setOf(OutPoint(TxId(randomBytes32()), 0)),
            createdAt = 1675270270000L,
            lockedAt = 1675270272445L,
            confirmedAt = 1675270273000L,
        )
        val metadata = makeMetadata(
            exchangeRate = 22999.83,
            userNotes = "Via dual-funding flow"
        )

        val expected = "2023-02-01T16:51:12.445Z,100000000,-7000000,22.9998 USD,-1.6099 USD,Incoming on-chain payment,L1 Top-up,Via dual-funding flow\r\n"
        val actual = CsvWriter.makeRow(
            info = WalletPaymentInfo(payment, metadata, null),
            localizedDescription = "L1 Top-up",
            config = makeConfig()
        )

        assertEquals(expected, actual)
    }

    @Test
    fun testRow_Outgoing_SwapOut() {
        val payment = LightningOutgoingPayment(
            id = UUID.randomUUID(),
            recipientAmount = 12_820_000.msat,
            recipient = PublicKey.Generator,
            details = LightningOutgoingPayment.Details.SwapOut(
                address = "tb1qlywh0dk40k87gqphpfs8kghd96hmnvus7r8hhf",
                paymentRequest = makePaymentRequest(),
                swapOutFee = 2_820.sat
            ),
            parts = listOf(
                makeLightningPart(12_000_000.msat),
                makeLightningPart(820_000.msat)
            ),
            status = LightningOutgoingPayment.Status.Succeeded(
                preimage = randomBytes32(),
                completedAt = 1675289814498
            ),
            createdAt = currentTimestampMillis()
        )
        val metadata = makeMetadata(
            exchangeRate = 23686.60,
            userNotes = null
        )

        val expected = "2023-02-01T22:16:54.498Z,-12820000,-2820000,-3.0366 USD,-0.6679 USD,Outgoing Swap to tb1qlywh0dk40k87gqphpfs8kghd96hmnvus7r8hhf,Swap for cash,\r\n"
        val actual = CsvWriter.makeRow(
            info = WalletPaymentInfo(payment, metadata, null),
            localizedDescription = "Swap for cash",
            config = makeConfig()
        )

        assertEquals(expected, actual)
    }

    @Test
    fun testRow_Outgoing_ChannelClose() {
        val payment = ChannelCloseOutgoingPayment(
            id = UUID.randomUUID(),
            recipientAmount = 8_690.sat,
            address = "tb1qz5gxe2450uadavle8wwcc5ngquqfj5xp4dy0ja",
            isSentToDefaultAddress = false,
            miningFees = 1_400.sat,
            channelId = randomBytes32(),
            txId = TxId(randomBytes32()),
            createdAt = 1675353533694,
            confirmedAt = 1675353533694,
            lockedAt = null,
            closingType = ChannelClosingType.Mutual,
        )
        val metadata = makeMetadata(
            exchangeRate = 23662.59,
            userNotes = null
        )

        val expected = "2023-02-02T15:58:53.694Z,-10090000,-1400000,-2.3875 USD,-0.3312 USD,Channel closing to tb1qz5gxe2450uadavle8wwcc5ngquqfj5xp4dy0ja,Channel closing,\r\n"
        val actual = CsvWriter.makeRow(
            info = WalletPaymentInfo(payment, metadata, null),
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
        Bolt11Invoice.create(
            chain = Chain.Testnet3,
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
        LightningOutgoingPayment.Part(
            id = UUID.randomUUID(),
            amount = amount,
            route = listOf(),
            status = LightningOutgoingPayment.Part.Status.Succeeded(
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
        )
}