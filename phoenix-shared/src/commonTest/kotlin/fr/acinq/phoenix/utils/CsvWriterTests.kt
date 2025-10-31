package fr.acinq.phoenix.utils

import fr.acinq.bitcoin.ByteVector32
import fr.acinq.bitcoin.Chain
import fr.acinq.bitcoin.OutPoint
import fr.acinq.bitcoin.PrivateKey
import fr.acinq.bitcoin.PublicKey
import fr.acinq.bitcoin.TxId
import fr.acinq.bitcoin.utils.Either
import fr.acinq.lightning.CltvExpiryDelta
import fr.acinq.lightning.Feature
import fr.acinq.lightning.FeatureSupport
import fr.acinq.lightning.Features
import fr.acinq.lightning.Lightning.randomBytes32
import fr.acinq.lightning.MilliSatoshi
import fr.acinq.lightning.NodeParams
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
import fr.acinq.phoenix.csv.WalletPaymentCsvWriter
import fr.acinq.phoenix.data.ExchangeRate
import fr.acinq.phoenix.data.FiatCurrency
import fr.acinq.phoenix.data.WalletPaymentMetadata
import kotlin.test.Test
import kotlin.test.assertEquals


class CsvWriterTests {

    private val csvWriter = WalletPaymentCsvWriter(
        WalletPaymentCsvWriter.Configuration(
            includesFiat = true,
            includesDescription = true,
            includesNotes = true,
            includesOriginDestination = true,
        )
    )

    @Test
    fun testRow_Incoming_NewChannel() {
        val payment = SpliceInIncomingPayment(
            id = UUID.fromString("7149cca7-d1d7-428d-8ee1-d9f43e44e9d8"),
            amountReceived = 100_000_000.msat,
            miningFee = 4_000.sat,
            liquidityPurchase = null,
            channelId = randomBytes32(),
            txId = TxId("343a4bfa6531a2e06757908ff70ba53bec23a922da6335d0cff2bfafa2360805"),
            localInputs = setOf(),
            createdAt = 1675270270000L,
            lockedAt = 1675270272445L,
            confirmedAt = 1675270273000L,
        )
        val metadata = makeMetadata(
            exchangeRate = 22999.83,
            userNotes = "Via Lightning network"
        )

        val expected = "date,id,type,amount_msat,amount_fiat,fee_credit_msat,mining_fee_sat,mining_fee_fiat,service_fee_msat,service_fee_fiat,payment_hash,tx_id,destination,description\n" +
                "2023-02-01T16:51:12.445Z,7149cca7-d1d7-428d-8ee1-d9f43e44e9d8,swap_in,100000000,22.9998 USD,0,4000,0.9199 USD,0,0.0000 USD,,343a4bfa6531a2e06757908ff70ba53bec23a922da6335d0cff2bfafa2360805,,Via Lightning network\n"

        csvWriter.add(payment, metadata)
        val actual = csvWriter.dumpAndClear()

        assertEquals(expected, actual)
    }

    @Test
    fun testRow_Incoming_Payment() {
        val preimage = ByteVector32("635d4df20ce9fb24452089fa573ee8e8285dee0920c0e86877c47a6437e5b641")
        val payment = Bolt11IncomingPayment(
            preimage = preimage,
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

        val expected = "date,id,type,amount_msat,amount_fiat,fee_credit_msat,mining_fee_sat,mining_fee_fiat,service_fee_msat,service_fee_fiat,payment_hash,tx_id,destination,description\n" +
                "2023-02-01T16:51:12.445Z,1823d0c9-3658-438a-8ab8-496dcd80c180,lightning_received,12000000,2.7599 USD,0,0,0.0000 USD,0,0.0000 USD,1823d0c93658338a0ab8496dcd80c1801eb44269f25bbb868bddfb40a64492c6,,,\"fake invoice\n" +
                "---\n" +
                "Via Lightning network\"\n"
        csvWriter.add(payment, metadata)
        val actual = csvWriter.dumpAndClear()

        assertEquals(expected, actual)
    }

    @Test
    fun testRow_Outgoing_Payment() {
        val payment = LightningOutgoingPayment(
            id = UUID.fromString("2226371f-c13d-4f41-b2c2-99a1596ba895"),
            recipientAmount = 4351000.msat,
            recipient = PublicKey.Generator,
            details = LightningOutgoingPayment.Details.Normal(Bolt11Invoice.read("lntb10n1pnupgtqpp5s9tgkxku0vsxea8v6etw3cutvlrtm5sqs0dvlvu7zx06k4kfnkascqzyssp59065p30zy42r0hsnlg2wusjazsyhx9lwfmur6s92nl5muqpqq67q9q7sqqqqqqqqqqqqqqqqqqqsqqqqqysgqdq4xysyymr0vd4kzcmrd9hx7mqz9gxqyz5vqrzjqwfn3p9278ttzzpe0e00uhyxhned3j5d9acqak5emwfpflp8z2cnfl6h8msfh3505gqqqqlgqqqqqeqqjq4ljnm7qg0ts68lt6c4j6a36s3dx9yncjxymvc0hd63ypc5d836pnalr20eyah626z6um0xcr2cpc5gl6jgayvark59qxek4rfxw8gvqqpuxr76").get()),
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

        val expected = "date,id,type,amount_msat,amount_fiat,fee_credit_msat,mining_fee_sat,mining_fee_fiat,service_fee_msat,service_fee_fiat,payment_hash,tx_id,destination,description\n" +
                "2023-02-01T16:56:22.248Z,2226371f-c13d-4f41-b2c2-99a1596ba895,lightning_sent,-4354435,1.0015 USD,0,0,0.0000 USD,3435,0.0007 USD,81568b1adc7b206cf4ecd656e8e38b67c6bdd20083dacfb39e119fab56c99dbb,,03be9f16ffcfe10ec7381506601b1b75c9638d5e5aa8c4e7a546573ee09bc68fa2,\"1 Blockaccino\n" +
                "---\n" +
                "Con quesito\"\n"
        csvWriter.add(payment, metadata)
        val actual = csvWriter.dumpAndClear()

        assertEquals(expected, actual)
    }

    @Test
    fun testRow_Outgoing_Payment_WithCommaInNote() {
        val payment = LightningOutgoingPayment(
            id = UUID.fromString("3dd49641-f9c0-462a-932f-766c944aa0d0"),
            recipientAmount = 100_000.msat,
            recipient = PublicKey.Generator,
            details = LightningOutgoingPayment.Details.Normal(Bolt11Invoice.read("lntb10n1pnupgtqpp5s9tgkxku0vsxea8v6etw3cutvlrtm5sqs0dvlvu7zx06k4kfnkascqzyssp59065p30zy42r0hsnlg2wusjazsyhx9lwfmur6s92nl5muqpqq67q9q7sqqqqqqqqqqqqqqqqqqqsqqqqqysgqdq4xysyymr0vd4kzcmrd9hx7mqz9gxqyz5vqrzjqwfn3p9278ttzzpe0e00uhyxhned3j5d9acqak5emwfpflp8z2cnfl6h8msfh3505gqqqqlgqqqqqeqqjq4ljnm7qg0ts68lt6c4j6a36s3dx9yncjxymvc0hd63ypc5d836pnalr20eyah626z6um0xcr2cpc5gl6jgayvark59qxek4rfxw8gvqqpuxr76").get()),
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

        val expected = "date,id,type,amount_msat,amount_fiat,fee_credit_msat,mining_fee_sat,mining_fee_fiat,service_fee_msat,service_fee_fiat,payment_hash,tx_id,destination,description\n" +
                "2023-02-01T16:58:01.099Z,3dd49641-f9c0-462a-932f-766c944aa0d0,lightning_sent,-103010,0.0236 USD,0,0,0.0000 USD,3010,0.0006 USD,81568b1adc7b206cf4ecd656e8e38b67c6bdd20083dacfb39e119fab56c99dbb,,03be9f16ffcfe10ec7381506601b1b75c9638d5e5aa8c4e7a546573ee09bc68fa2,\"1 Blockaccino\n" +
                "---\n" +
                "This note, um, has a comma\"\n"
        csvWriter.add(payment, metadata)
        val actual = csvWriter.dumpAndClear()

        assertEquals(expected, actual)
    }

    @Test
    fun testRow_Outgoing_Payment_WithQuoteInNote() {
        val payment = LightningOutgoingPayment(
            id = UUID.fromString("2886283a-a250-444a-9dd7-2f22656c4c9b"),
            recipientAmount = 100_000.msat,
            recipient = PublicKey.Generator,
            details = LightningOutgoingPayment.Details.Normal(Bolt11Invoice.read("lntb10n1pnupgtqpp5s9tgkxku0vsxea8v6etw3cutvlrtm5sqs0dvlvu7zx06k4kfnkascqzyssp59065p30zy42r0hsnlg2wusjazsyhx9lwfmur6s92nl5muqpqq67q9q7sqqqqqqqqqqqqqqqqqqqsqqqqqysgqdq4xysyymr0vd4kzcmrd9hx7mqz9gxqyz5vqrzjqwfn3p9278ttzzpe0e00uhyxhned3j5d9acqak5emwfpflp8z2cnfl6h8msfh3505gqqqqlgqqqqqeqqjq4ljnm7qg0ts68lt6c4j6a36s3dx9yncjxymvc0hd63ypc5d836pnalr20eyah626z6um0xcr2cpc5gl6jgayvark59qxek4rfxw8gvqqpuxr76").get()),
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

        val expected = "date,id,type,amount_msat,amount_fiat,fee_credit_msat,mining_fee_sat,mining_fee_fiat,service_fee_msat,service_fee_fiat,payment_hash,tx_id,destination,description\n" +
                "2023-02-01T16:59:00.742Z,2886283a-a250-444a-9dd7-2f22656c4c9b,lightning_sent,-103010,0.0236 USD,0,0,0.0000 USD,3010,0.0006 USD,81568b1adc7b206cf4ecd656e8e38b67c6bdd20083dacfb39e119fab56c99dbb,,03be9f16ffcfe10ec7381506601b1b75c9638d5e5aa8c4e7a546573ee09bc68fa2,\"1 Blockaccino\n" +
                "---\n" +
                "This \"\"note\"\" has quotes\"\n"
        csvWriter.add(payment, metadata)
        val actual = csvWriter.dumpAndClear()

        assertEquals(expected, actual)
    }

    @Test
    fun testRow_Outgoing_Payment_WithNewlineInNote() {
        val payment = LightningOutgoingPayment(
            id = UUID.fromString("2886283a-a250-444a-9dd7-2f22656c4c9b"),
            recipientAmount = 100_000.msat,
            recipient = PublicKey.Generator,
            details = LightningOutgoingPayment.Details.Normal(Bolt11Invoice.read("lntb10n1pnupgtqpp5s9tgkxku0vsxea8v6etw3cutvlrtm5sqs0dvlvu7zx06k4kfnkascqzyssp59065p30zy42r0hsnlg2wusjazsyhx9lwfmur6s92nl5muqpqq67q9q7sqqqqqqqqqqqqqqqqqqqsqqqqqysgqdq4xysyymr0vd4kzcmrd9hx7mqz9gxqyz5vqrzjqwfn3p9278ttzzpe0e00uhyxhned3j5d9acqak5emwfpflp8z2cnfl6h8msfh3505gqqqqlgqqqqqeqqjq4ljnm7qg0ts68lt6c4j6a36s3dx9yncjxymvc0hd63ypc5d836pnalr20eyah626z6um0xcr2cpc5gl6jgayvark59qxek4rfxw8gvqqpuxr76").get()),
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

        val expected = "date,id,type,amount_msat,amount_fiat,fee_credit_msat,mining_fee_sat,mining_fee_fiat,service_fee_msat,service_fee_fiat,payment_hash,tx_id,destination,description\n" +
                "2023-02-01T17:00:26.945Z,2886283a-a250-444a-9dd7-2f22656c4c9b,lightning_sent,-103010,0.0236 USD,0,0,0.0000 USD,3010,0.0006 USD,81568b1adc7b206cf4ecd656e8e38b67c6bdd20083dacfb39e119fab56c99dbb,,03be9f16ffcfe10ec7381506601b1b75c9638d5e5aa8c4e7a546573ee09bc68fa2,\"1 Blockaccino\n" +
                "---\n" +
                "This note has multiple lines:\n" +
                "Brie\n" +
                "Cheddar\n" +
                "Asiago\"\n"
        csvWriter.add(payment, metadata)
        val actual = csvWriter.dumpAndClear()

        assertEquals(expected, actual)
    }

    @Test
    fun testRow_Incoming_NewChannel_DualSwapIn() {
        val payment = NewChannelIncomingPayment(
            id = UUID.fromString("d8ca850a-4406-4ebe-a4a8-be73553f589b"),
            amountReceived = 100_000_000.msat,
            miningFee = 2_000.sat,
            serviceFee = 5_000_000.msat,
            liquidityPurchase = LiquidityAds.Purchase.Standard(amount = 1.sat, fees = LiquidityAds.Fees(serviceFee = 5_000.sat, miningFee = 0.sat), paymentDetails = LiquidityAds.PaymentDetails.FromChannelBalance),
            channelId = randomBytes32(),
            txId = TxId("343a4bfa6531a2e06757908ff70ba53bec23a922da6335d0cff2bfafa2360805"),
            localInputs = setOf(OutPoint(TxId(randomBytes32()), 0)),
            createdAt = 1675270270000L,
            lockedAt = 1675270272445L,
            confirmedAt = 1675270273000L,
        )
        val metadata = makeMetadata(
            exchangeRate = 22999.83,
            userNotes = "Via dual-funding flow"
        )

        val expected = "date,id,type,amount_msat,amount_fiat,fee_credit_msat,mining_fee_sat,mining_fee_fiat,service_fee_msat,service_fee_fiat,payment_hash,tx_id,destination,description\n" +
                "2023-02-01T16:51:12.445Z,d8ca850a-4406-4ebe-a4a8-be73553f589b,swap_in,100000000,22.9998 USD,0,2000,0.4599 USD,5000000,1.1499 USD,,343a4bfa6531a2e06757908ff70ba53bec23a922da6335d0cff2bfafa2360805,,Via dual-funding flow\n"
        csvWriter.add(payment, metadata)
        val actual = csvWriter.dumpAndClear()

        assertEquals(expected, actual)
    }

    @Suppress("DEPRECATION")
    @Test
    fun testRow_Outgoing_SwapOut() {
        val payment = LightningOutgoingPayment(
            id = UUID.fromString("d8ca850a-4406-4ebe-a4a8-be73553f589b"),
            recipientAmount = 12_820_000.msat,
            recipient = PublicKey.fromHex("03933884aaf1d6b108397e5efe5c86bcf2d8ca8d2f700eda99db9214fc2712b134"),
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

        val expected = "date,id,type,amount_msat,amount_fiat,fee_credit_msat,mining_fee_sat,mining_fee_fiat,service_fee_msat,service_fee_fiat,payment_hash,tx_id,destination,description\n" +
                "2023-02-01T22:16:54.498Z,d8ca850a-4406-4ebe-a4a8-be73553f589b,legacy_swap_out,-12820000,3.0366 USD,0,2820,0.6679 USD,0,0.0000 USD,,,tb1qlywh0dk40k87gqphpfs8kghd96hmnvus7r8hhf,\n"
        csvWriter.add(payment, metadata)
        val actual = csvWriter.dumpAndClear()

        assertEquals(expected, actual)
    }

    @Test
    fun testRow_Outgoing_ChannelClose() {
        val payment = ChannelCloseOutgoingPayment(
            id = UUID.fromString("861b22e3-b584-493a-ab5a-55512d3b3228"),
            recipientAmount = 8_690.sat,
            address = "tb1qz5gxe2450uadavle8wwcc5ngquqfj5xp4dy0ja",
            isSentToDefaultAddress = false,
            miningFee = 1_400.sat,
            channelId = randomBytes32(),
            txId = TxId("343a4bfa6531a2e06757908ff70ba53bec23a922da6335d0cff2bfafa2360805"),
            createdAt = 1675353533694,
            confirmedAt = 1675353533694,
            lockedAt = null,
            closingType = ChannelClosingType.Mutual,
        )
        val metadata = makeMetadata(
            exchangeRate = 23662.59,
            userNotes = null
        )

        val expected = "date,id,type,amount_msat,amount_fiat,fee_credit_msat,mining_fee_sat,mining_fee_fiat,service_fee_msat,service_fee_fiat,payment_hash,tx_id,destination,description\n" +
                "2023-02-02T15:58:53.694Z,861b22e3-b584-493a-ab5a-55512d3b3228,channel_close,-10090000,2.3875 USD,0,1400,0.3312 USD,0,0.0000 USD,,343a4bfa6531a2e06757908ff70ba53bec23a922da6335d0cff2bfafa2360805,tb1qz5gxe2450uadavle8wwcc5ngquqfj5xp4dy0ja,\n"
        csvWriter.add(payment, metadata)
        val actual = csvWriter.dumpAndClear()

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
            privateKey = PrivateKey(value = ByteVector32.fromValidHex("8aca84879c0d7517445ecaf399b427d1e79ecc55e9bfe2421e227679993c461e")),
            description = Either.Left("fake invoice"),
            minFinalCltvExpiryDelta = CltvExpiryDelta(128),
            features = Features(
                Feature.OptionDataLossProtect to FeatureSupport.Optional,
                Feature.VariableLengthOnion to FeatureSupport.Mandatory,
                Feature.PaymentSecret to FeatureSupport.Mandatory,
                Feature.BasicMultiPartPayment to FeatureSupport.Optional,
                Feature.Wumbo to FeatureSupport.Optional,
                Feature.StaticRemoteKey to FeatureSupport.Mandatory,
                Feature.AnchorOutputs to FeatureSupport.Optional,
            ),
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
}