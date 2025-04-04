package fr.acinq.phoenix.db.cloud

import fr.acinq.bitcoin.ByteVector32
import fr.acinq.bitcoin.Chain
import fr.acinq.bitcoin.Crypto
import fr.acinq.bitcoin.OutPoint
import fr.acinq.bitcoin.PublicKey
import fr.acinq.bitcoin.TxId
import fr.acinq.bitcoin.byteVector32
import fr.acinq.bitcoin.utils.Either
import fr.acinq.lightning.CltvExpiryDelta
import fr.acinq.lightning.Feature
import fr.acinq.lightning.FeatureSupport
import fr.acinq.lightning.Features
import fr.acinq.lightning.Lightning.randomBytes32
import fr.acinq.lightning.Lightning.randomKey
import fr.acinq.lightning.MilliSatoshi
import fr.acinq.lightning.db.Bolt11IncomingPayment
import fr.acinq.lightning.db.ChannelCloseOutgoingPayment
import fr.acinq.lightning.db.IncomingPayment
import fr.acinq.lightning.db.LegacyPayToOpenIncomingPayment
import fr.acinq.lightning.db.LightningIncomingPayment
import fr.acinq.lightning.db.LightningOutgoingPayment
import fr.acinq.lightning.db.NewChannelIncomingPayment
import fr.acinq.lightning.db.OutgoingPayment
import fr.acinq.lightning.db.SpliceOutgoingPayment
import fr.acinq.lightning.payment.Bolt11Invoice
import fr.acinq.lightning.payment.FinalFailure
import fr.acinq.lightning.utils.UUID
import fr.acinq.lightning.utils.msat
import fr.acinq.lightning.utils.sat
import fr.acinq.lightning.utils.toByteVector32
import fr.acinq.lightning.wire.LiquidityAds
import fr.acinq.phoenix.db.cloud.payments.CloudAsset
import fr.acinq.phoenix.db.cloud.payments.CloudData
import fr.acinq.phoenix.db.cloud.payments.cborDeserialize
import fr.acinq.phoenix.runTest
import fr.acinq.secp256k1.Hex
import kotlinx.serialization.cbor.Cbor
import kotlinx.serialization.decodeFromByteArray
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull


class CloudDataTest {

    private val preimage = randomBytes32()

    private val bitcoinAddress = "1PwLgmRdDjy5GAKWyp8eyAC4SFzWuboLLb"

    private val channelId = randomBytes32()

    private val publicKey = randomKey().publicKey()
    private val uuid = UUID.randomUUID()

    private fun testRoundtrip(incomingPayment: IncomingPayment) {
        // serialize payment into blob
        val blob = CloudData.V1(incomingPayment).serialize()

        // attempt to deserialize & extract payment
        val data = CloudData.deserialize(blob)
        assertIs<CloudData.V1>(data)
        assertIs<IncomingPayment>(data.payment)

        // test equality (no loss of information)
        assertEquals(incomingPayment, data.payment)
    }

    private fun testRoundtrip(outgoingPayment: OutgoingPayment) {
        // serialize payment into blob
        val blob = CloudData.V1(outgoingPayment).serialize()

        // attempt to deserialize & extract payment
        val data = CloudData.deserialize(blob)
        assertIs<CloudData.V1>(data)

        // test equality (no loss of information)
        assertEquals(outgoingPayment, data.payment)
    }

    @Test
    fun incoming__invoice() = runTest {
        val invoice = createBolt11Invoice(preimage, 250_000.msat)
        testRoundtrip(
            Bolt11IncomingPayment(
                preimage = preimage,
                paymentRequest = invoice,
                parts = emptyList(),
                createdAt = 100
            )
        )
    }

    @Test
    fun incoming__swapIn() = runTest {
        testRoundtrip(
            NewChannelIncomingPayment(
                id = UUID.randomUUID(),
                amountReceived = 150_000_000.msat,
                miningFee = 10_000.sat,
                serviceFee = 5_000_000.msat,
                liquidityPurchase = LiquidityAds.Purchase.Standard(
                    amount = 10_000.sat,
                    fees = LiquidityAds.Fees(miningFee = 2_000.sat, serviceFee = 1_000.sat),
                    paymentDetails = LiquidityAds.PaymentDetails.FromChannelBalance
                ),
                channelId = randomBytes32(),
                txId = TxId(randomBytes32()),
                localInputs = setOf(OutPoint(TxId(randomBytes32()), 0)),
                createdAt = 0,
                lockedAt = 50,
                confirmedAt = 100,
            )
        )
    }

    @Test
    fun incoming__receivedWith_lightning() = runTest {
        testRoundtrip(
            Bolt11IncomingPayment(
                preimage = preimage,
                paymentRequest = createBolt11Invoice(preimage, 250_000.msat),
                parts = listOf(
                    LightningIncomingPayment.Part.Htlc(
                        amountReceived = 100_000.msat,
                        channelId = randomBytes32(),
                        htlcId = 1L,
                        fundingFee = null,
                        receivedAt = 150,
                    ),
                    LightningIncomingPayment.Part.Htlc(
                        amountReceived = 150_000.msat,
                        channelId = randomBytes32(),
                        htlcId = 2L,
                        fundingFee = LiquidityAds.FundingFee(amount = 1_000.msat, fundingTxId = TxId(randomBytes32())),
                        receivedAt = 200,
                    )
                ),
                createdAt = 100
            )
        )
    }

    @Test
    fun incoming__receivedWith_newChannel() = runTest {
        testRoundtrip(
            NewChannelIncomingPayment(
                id = UUID.randomUUID(),
                amountReceived = 150_000_000.msat,
                miningFee = 10_000.sat,
                serviceFee = 5_000_000.msat,
                liquidityPurchase = LiquidityAds.Purchase.Standard(
                    amount = 50_000.sat,
                    fees = LiquidityAds.Fees(miningFee = 3_000.sat, serviceFee = 2_000.sat),
                    paymentDetails = LiquidityAds.PaymentDetails.FromChannelBalance
                ),
                channelId = randomBytes32(),
                txId = TxId(randomBytes32()),
                localInputs = setOf(OutPoint(TxId(randomBytes32()), 0)),
                createdAt = 0,
                lockedAt = 50,
                confirmedAt = 100,
            )
        )
    }

    @Test
    fun incoming__receivedWith_newChannel_legacy_no_uuid() = runTest {
        val blob = Hex.decode("bf6169bf68707265696d6167655820d77a5c6e17f70240c4a2aaf54fb1389188e482e85247f63c80417661e6a9b250666f726967696ebf64747970656a494e564f4943455f563064626c6f6259011b7b227061796d656e7452657175657374223a226c6e6263333530753170336464347874707035716c706868353476396e366737323439636435613463337a6735666b666b336a657377356370306b6a70393264366e377574777364717664396838766d6d6676646a73637170737370357a6a7275777a6c7677353732616e3934776b6b7630616370657077773068647a387134726466673679756b776466647032723571397179397173717738337771653868797130767061636a78336c7963777567657765787a307a3634796732343564377a717277653039676c7572716e34706466306d706539766336713065667739637533613773746575786c6a7730786e7970336d686a6565786664677a787163703564646d6b61227dff687265636569766564bf6274731b00000182172f3a3764747970656d4d554c544950415254535f563164626c6f625901ab5b7b2274797065223a2266722e6163696e712e70686f656e69782e64622e7061796d656e74732e496e636f6d696e67526563656976656457697468446174612e506172742e4e65774368616e6e656c2e5630222c22616d6f756e74223a7b226d736174223a373030303030307d2c2266656573223a7b226d736174223a333030303030307d2c226368616e6e656c4964223a2265386130653762613931613438356564363835373431356363306336306637376564613663623165626531646138343164343264376234333838636332626363227d2c7b2274797065223a2266722e6163696e712e70686f656e69782e64622e7061796d656e74732e496e636f6d696e67526563656976656457697468446174612e506172742e4e65774368616e6e656c2e5630222c22616d6f756e74223a7b226d736174223a393030303030307d2c2266656573223a7b226d736174223a363030303030307d2c226368616e6e656c4964223a2265386130653762613931613438356564363835373431356363306336306637376564613663623165626531646138343164343264376234333838636332626363227d5dff696372656174656441741b00000182172f3a37ff616ff6617600617040ff")
        val cb = Cbor.decodeFromByteArray<CloudData.V0>(blob)
        println(cb)
        val data = CloudData.deserialize(blob)
        assertIs<CloudData.V0>(data)
        val decoded = data.incoming?.unwrap()
        assertNotNull(decoded)

        val expectedPreimage = Hex.decode("d77a5c6e17f70240c4a2aaf54fb1389188e482e85247f63c80417661e6a9b250")
        val expectedChannelId = Hex.decode("e8a0e7ba91a485ed6857415cc0c60f77eda6cb1ebe1da841d42d7b4388cc2bcc").byteVector32()
        assertIs<LegacyPayToOpenIncomingPayment>(decoded)
        assertEquals(expectedPreimage.byteVector32(), decoded.paymentPreimage)

        val firstPart = decoded.parts[0]
        assertIs<LegacyPayToOpenIncomingPayment.Part.OnChain>(firstPart)
        assertEquals(expectedChannelId, firstPart.channelId)
        assertEquals(7_000_000.msat, firstPart.amountReceived)

        assertEquals(1658246347319, decoded.completedAt)
    }

    @Test
    fun outgoing__normal() = runTest {
        val invoice = createBolt11Invoice(preimage, 1_000_000.msat)
        testRoundtrip(
            LightningOutgoingPayment(
                id = uuid,
                recipientAmount = 1_000_000.msat,
                recipient = publicKey,
                details = LightningOutgoingPayment.Details.Normal(invoice)
            )
        )
    }

    @Test
    fun outgoing__swapOut() = runTest {
        val invoice = createBolt11Invoice(preimage, 1_000_000.msat)
        testRoundtrip(
            LightningOutgoingPayment(
                id = uuid,
                recipientAmount = 1_000_000.msat,
                recipient = publicKey,
                status = LightningOutgoingPayment.Status.Pending,
                parts = emptyList(),
                details = LightningOutgoingPayment.Details.SwapOut(bitcoinAddress, invoice, 2_500.sat)
            )
        )
    }

    @Test
    fun outgoing__channelClosing() = runTest {
        testRoundtrip(
            ChannelCloseOutgoingPayment(
                id = uuid,
                recipientAmount = 50_000.sat,
                address = bitcoinAddress,
                isSentToDefaultAddress = false,
                miningFee = 1_000.sat,
                txId = TxId(randomBytes32()),
                createdAt = 1000,
                confirmedAt = null,
                lockedAt = null,
                channelId = channelId,
                closingType = ChannelCloseOutgoingPayment.ChannelClosingType.Mutual
            )
        )
    }

    @Test
    fun outgoing__channelClosed() = runTest {
        testRoundtrip(
            ChannelCloseOutgoingPayment(
                id = uuid,
                recipientAmount = 50_000.sat,
                address = bitcoinAddress,
                isSentToDefaultAddress = true,
                miningFee = 5_000.sat,
                txId = TxId(randomBytes32()),
                createdAt = 1000,
                confirmedAt = 5000,
                lockedAt = 7000,
                channelId = channelId,
                closingType = ChannelCloseOutgoingPayment.ChannelClosingType.Remote
            )
        )
    }

    @Test
    fun outgoing__failed() = runTest {
        val recipientAmount = 500_000.msat
        val invoice = createBolt11Invoice(preimage, recipientAmount)
        val (a, b) = listOf(randomKey().publicKey(), randomKey().publicKey())
        val part = LightningOutgoingPayment.Part(
            id = UUID.randomUUID(),
            amount = 500_005.msat,
            route = listOf(LightningOutgoingPayment.Part.HopDesc(a, b)),
            status = LightningOutgoingPayment.Part.Status.Failed(
                LightningOutgoingPayment.Part.Status.Failed.Failure.Uninterpretable("I'm a teapot")
            )
        )
        val outgoingPayment = LightningOutgoingPayment(
            id = uuid,
            recipientAmount = recipientAmount,
            recipient = publicKey,
            details = LightningOutgoingPayment.Details.Normal(invoice),
            parts = listOf(part),
            status = LightningOutgoingPayment.Status.Failed(FinalFailure.UnknownError)
        )

        // serialize payment into blob
        val blob = CloudData.V1(outgoingPayment).serialize()

        // attempt to deserialize & extract payment
        val data = CloudData.deserialize(blob)
        assertIs<CloudData.V1>(data)
        assertNotNull(data)

        // test equality (no loss of information)
        assertEquals(outgoingPayment, data.payment)
    }

    @Test
    fun outgoing__succeeded_offChain() = runTest {
        val recipientAmount = 500_000.msat
        val invoice = createBolt11Invoice(preimage, recipientAmount)
        val (a, b) = listOf(randomKey().publicKey(), randomKey().publicKey())
        val part1 = LightningOutgoingPayment.Part(
            id = UUID.randomUUID(),
            amount = 250_005.msat,
            route = listOf(LightningOutgoingPayment.Part.HopDesc(a, b)),
            status = LightningOutgoingPayment.Part.Status.Succeeded(preimage)
        )
        val part2 = LightningOutgoingPayment.Part(
            id = UUID.randomUUID(),
            amount = 250_005.msat,
            route = listOf(LightningOutgoingPayment.Part.HopDesc(a, b)),
            status = LightningOutgoingPayment.Part.Status.Succeeded(preimage)
        )
        testRoundtrip(
            LightningOutgoingPayment(
                id = uuid,
                recipientAmount = recipientAmount,
                recipient = publicKey,
                details = LightningOutgoingPayment.Details.Normal(invoice),
                parts = listOf(part1, part2),
                status = LightningOutgoingPayment.Status.Succeeded(preimage)
            )
        )
    }

    @Test
    fun outgoing__spliceOut() = runTest {
        testRoundtrip(
            SpliceOutgoingPayment(
                id = uuid,
                recipientAmount = 1_000_000.sat,
                address = bitcoinAddress,
                miningFee = 3400.sat,
                txId = TxId(randomBytes32()),
                liquidityPurchase = null,
                channelId = randomBytes32(),
                createdAt = 150,
                confirmedAt = 240,
                lockedAt = 280,
            )
        )
    }

    /**
     * Payments for channel closings created before lightning-kmp v1.3.0 may be using [PublicKey.Generator] for the recipient field. In that case, the public key will
     * be uncompressed and will be saved as such in the cloud. We still should be able to read it.
     *
     * Note that with the splices update, the recipient node id has been removed from the channel-closing object. So we just check we can read the data.
     */
    @Test
    fun read_legacy_uncompressed_pubkey() {
        val hexWithUncompressedKey = "bf6169f6616fbf626964782466663766303865382d383964312d343733312d626537632d616433376339643039616663646d7361741a019c00a869726563697069656e7458410479be667ef9dcbbac55a06295ce870b07029bfcdb2dce28d959f2815b16f81798483ada7726a3c4655da4fbfc0e1108a8fd17b448a68554199c47d08ffb10d4b86764657461696c73bf64747970656a434c4f53494e475f563064626c6f6258a57b226368616e6e656c4964223a2266663766303865383839643162373331626537636164333763396430396166633135313561313836333937396136346430646262363532313334303939623839222c22636c6f73696e6741646472657373223a2233475a695a5a733851477248347a61385a72516b58647271446a3266486431696a79222c22697353656e74546f44656661756c7441646472657373223a66616c73657dff6570617274739fff66737461747573bf6274731b0000017c2ccf0b7c6474797065745355434345454445445f4f4e434841494e5f563064626c6f6258757b227478496473223a5b2234386631633637343434353634393261623761353561373731356238383136313136383639333839303530643762643639386162393932346666396363633937225d2c22636c61696d6564223a32373030312c22636c6f73696e6754797065223a224d757475616c227dff696372656174656441741b0000017c2c654831ff617600617058d741c87b7b9c620bb98a56aa790919d5e6312b60d47d49cbcd443e84365eea438a9deebdb6d8c24f0d1885af2375df1fb3f16b98b4c8ef28d2c023cb728272dfed61ca66241bd4a67a558d04cf61cf0bd8d1b84ec6f7697fa55bd956dea59c8e5dbeb5094a64f7c07353df5cabe48c4dfaa929d8da331d0afe6da3305af524584382920828c71ec2e30225bc92b22c9cc038880b02e2d7d0e8c422d60400dcb241f5b61dfb1ba2f3a28e62aee9f18f833311f8987918043d68b4efb3318f046f9ad0c2aea172f62f8750389962a8a90f951e6f38dbf54588ff"
        val data = CloudData.deserialize(Hex.decode(hexWithUncompressedKey))
        assertNotNull(data)
        assertIs<CloudData.V0>(data)
        assertEquals(65, data.outgoing?.recipient?.size)
        val payment = data.outgoing?.unwrap()
        assertNotNull(payment)
        assertIs<ChannelCloseOutgoingPayment>(payment)
        assertEquals(27001000.msat, payment.amount)
        assertEquals("3GZiZZs8QGrH4za8ZrQkXdrqDj2fHd1ijy", payment.address)
        assertEquals(ByteVector32.fromValidHex("ff7f08e889d1b731be7cad37c9d09afc1515a1863979a64d0dbb652134099b89"), payment.channelId)
    }

    @Test
    fun outgoing__parts_before_closing_txs() {
        // Cbor encoded blob of a successful outgoing payment that was serialized before the update adding the closing txs parts.
        // That payment contains 2 closing txs spending 50 000 sat with a 2 000 sat fee, information that are stored in the payment's status.
        val blob = Hex.decode(
            "bf6169f6616fbf626964782439643966303836642d313134642d343461312d613430342d313037353139303061376564646d7361741a02faf08069726563697069656e74582103933884aaf1d6b108397e5efe5c86bcf2d8ca8d2f700eda99db9214fc2712b1346764657461696c73bf64747970656a434c4f53494e475f563064626c6f6258a67b226368616e6e656c4964223a2238306362383332643266633631376462393032326133633239623432383038666232623132366563316265383862643065343830393734653734633034623738222c22636c6f73696e6741646472657373223a22324e336d437334724a4767775a34683164593338486637764c69775a4c44564e7a6834222c22697353656e74546f44656661756c7441646472657373223a66616c73657dff6570617274739fff66737461747573bf6274731b00000180fc53a4ea6474797065745355434345454445445f4f4e434841494e5f563064626c6f6258b77b227478496473223a5b2235333664373464666635323630653039633466393433343637393837383231343362633063353364653132636636623662666561663837666466376661303030222c2232383937353938396537333261653863643461623461323833376130383331636533313164303135633638353766343938653731613462323337656465346334225d2c22636c61696d6564223a34383030302c22636c6f73696e6754797065223a224c6f63616c227dff696372656174656441741b00000180fc53a4eaff617600617040ff"
        )
        // attempt to deserialize & extract payment
        val data = CloudData.deserialize(blob)
        assertIs<CloudData.V0>(data)
        val decoded = data.outgoing?.unwrap()
        assertNotNull(decoded)
        assertIs<ChannelCloseOutgoingPayment>(decoded)
        assertEquals(50_000_000.msat, decoded.amount)
        assertEquals(2_000_000.msat, decoded.fees)
        assertEquals("2N3mCs4rJGgwZ4h1dY38Hf7vLiwZLDVNzh4", decoded.address)
        assertEquals(ByteVector32.fromValidHex("80cb832d2fc617db9022a3c29b42808fb2b126ec1be88bd0e480974e74c04b78"), decoded.channelId)
        assertEquals(false, decoded.isSentToDefaultAddress)
        assertEquals(1653500781802, decoded.createdAt)
        assertEquals(1653500781802, decoded.confirmedAt)
        assertEquals(1653500781802, decoded.lockedAt)
    }

    @Test
    fun outgoing__lnurl_pay() {
        val paymentBlob = Hex.decode(
            "bf6169f6616fbf626964782465366662336662612d666461332d346163392d396633382d656239356234633064353962646d7361741a000f424069726563697069656e7458210397eabc70be6e6e9dd831d7887bf579fdf6500f0f0e07ed8922e64471ee39f1fb6764657461696c73bf6474797065694e4f524d414c5f563064626c6f625901a07b227061796d656e7452657175657374223a226c6e746231307531703336797934787070357134303539613937636a6367637232376d3235366677387132377064633671377074786c643364736a65306c666c3971776d6d736471686765736b6b6566716433683832756e76393463787a37676371706a73703534747a666b37346d73613565677473356b393333617a343261646a6b7a726b716e707072396b74767a75686a6d743238777967733971377371717171717171717171717171717171717171737171717171797367716d717a39677871797a357671727a6a7177666e33703932373874747a7a70653065303075687978686e6564336a356439616371616b35656d776670666c70387a32636e666c6c7867336337757730336c76717171716c6771717171716571716a7137356a616c6d70787a653074667170766b656b7336327171766b3465337a6467683077726d6b72707061327433377a33376472686371306361346336343733363964666e74657239737a3778666774646e306a656163356d3334786668667a63616c65673570737073616d7a3564227dff6570617274739fbf626964782434366631383031302d323066652d343538302d623431622d613137396564663337363637646d7361741a000f4e5c65726f7574657901183033393765616263373062653665366539646438333164373838376266353739666466363530306630663065303765643839323265363434373165653339663166623a3033393333383834616166316436623130383339376535656665356338366263663264386361386432663730306564613939646239323134666332373132623133343a30783839383337313278303b3033393333383834616166316436623130383339376535656665356338366263663264386361386432663730306564613939646239323134666332373132623133343a3033393765616263373062653665366539646438333164373838376266353739666466363530306630663065303765643839323265363434373165653339663166623a66737461747573bf6274731b000001853118ebff64747970656c5355434345454445445f563064626c6f62584f7b22707265696d616765223a2263643065333562343337393632363066653162343535333631346639363866663732653936633833396632376337383864353435656661353362613133373030227dff696372656174656441741b000001853118e314ffff66737461747573bf6274731b000001853118ee256474797065755355434345454445445f4f4646434841494e5f563064626c6f62584f7b22707265696d616765223a2263643065333562343337393632363066653162343535333631346639363866663732653936633833396632376337383864353435656661353362613133373030227dff696372656174656441741b000001853118dc2fff61760061705874580a3af70036ff704b08ef289b3db31f9b090dca2da10180d9e7196a98119b777d72c178511f96652dd02e30eec72656b4041692625a8eaedab9fb780ab1cab350ef1e71cffbec3b38cfc3dc43aa8b3ef70c306fc12f5fb6a3b494cac4d35a016ddb1967a24c34796669381af63c0ab2c07f8831ff"
        )
        val metadataBlob = Hex.decode(
            "bf6176016a6c6e75726c5f62617365bf6474797065665041595f563064626c6f62587fbf656c6e75726c7768747470733a2f2f66616b652e69742f696e697469616c6863616c6c6261636b781868747470733a2f2f66616b652e69742f63616c6c6261636b6f6d696e53656e6461626c654d7361741a000f42406f6d617853656e6461626c654d7361741a001e8480706d6178436f6d6d656e744c656e677468f6ffff6e6c6e75726c5f6d65746164617461bf6474797065665041595f563064626c6f62582abf6372617778225b5b22746578742f706c61696e222c202246616b65206c6e75726c2d706179225d5dffff736c6e75726c5f73756363657373416374696f6ef6716c6e75726c5f6465736372697074696f6e6e46616b65206c6e75726c2d70617970757365725f6465736372697074696f6ef66a757365725f6e6f746573606d6f726967696e616c5f66696174bf6474797065635553446472617465fb40d07e27ae147ae1ffff"
        )

        val paymentData = CloudData.deserialize(paymentBlob)
        assertIs<CloudData.V0>(paymentData)
        val outgoingPayment = paymentData.outgoing?.unwrap()
        assertNotNull(outgoingPayment)
        assertIs<LightningOutgoingPayment>(outgoingPayment)
        assertEquals(1_003_100.msat, outgoingPayment.amount)
        assertEquals(1_000_000.msat, outgoingPayment.recipientAmount)
        assertEquals(3_100.msat, outgoingPayment.fees)
        assertEquals(1, outgoingPayment.parts.size)

        val metadataRow = CloudAsset.cborDeserialize(metadataBlob)
        assertNotNull(metadataRow)
        val metadata = metadataRow.unwrap().deserialize()
        val lnurlPay = metadata.lnurl
        assertNotNull(lnurlPay)
        assertEquals("https://fake.it/initial", lnurlPay.pay.initialUrl.toString())
        assertEquals(1_000_000.msat, lnurlPay.pay.minSendable)
        assertEquals(2_000_000.msat, lnurlPay.pay.maxSendable)
        assertEquals("Fake lnurl-pay", lnurlPay.pay.metadata.plainText)
        assertEquals("Fake lnurl-pay", lnurlPay.description)
    }

    @Test
    fun outgoing__lnurl_pay_legacy() {
        val paymentBlob = Hex.decode(
            "bf6169f6616fbf626964782465366662336662612d666461332d346163392d396633382d656239356234633064353962646d7361741a000f424069726563697069656e7458210397eabc70be6e6e9dd831d7887bf579fdf6500f0f0e07ed8922e64471ee39f1fb6764657461696c73bf6474797065694e4f524d414c5f563064626c6f625901a07b227061796d656e7452657175657374223a226c6e746231307531703336797934787070357134303539613937636a6367637232376d3235366677387132377064633671377074786c643364736a65306c666c3971776d6d736471686765736b6b6566716433683832756e76393463787a37676371706a73703534747a666b37346d73613565677473356b393333617a343261646a6b7a726b716e707072396b74767a75686a6d743238777967733971377371717171717171717171717171717171717171737171717171797367716d717a39677871797a357671727a6a7177666e33703932373874747a7a70653065303075687978686e6564336a356439616371616b35656d776670666c70387a32636e666c6c7867336337757730336c76717171716c6771717171716571716a7137356a616c6d70787a653074667170766b656b7336327171766b3465337a6467683077726d6b72707061327433377a33376472686371306361346336343733363964666e74657239737a3778666774646e306a656163356d3334786668667a63616c65673570737073616d7a3564227dff6570617274739fbf626964782434366631383031302d323066652d343538302d623431622d613137396564663337363637646d7361741a000f4e5c65726f7574657901183033393765616263373062653665366539646438333164373838376266353739666466363530306630663065303765643839323265363434373165653339663166623a3033393333383834616166316436623130383339376535656665356338366263663264386361386432663730306564613939646239323134666332373132623133343a30783839383337313278303b3033393333383834616166316436623130383339376535656665356338366263663264386361386432663730306564613939646239323134666332373132623133343a3033393765616263373062653665366539646438333164373838376266353739666466363530306630663065303765643839323265363434373165653339663166623a66737461747573bf6274731b000001853118ebff64747970656c5355434345454445445f563064626c6f62584f7b22707265696d616765223a2263643065333562343337393632363066653162343535333631346639363866663732653936633833396632376337383864353435656661353362613133373030227dff696372656174656441741b000001853118e314ffff66737461747573bf6274731b000001853118ee256474797065755355434345454445445f4f4646434841494e5f563064626c6f62584f7b22707265696d616765223a2263643065333562343337393632363066653162343535333631346639363866663732653936633833396632376337383864353435656661353362613133373030227dff696372656174656441741b000001853118dc2fff61760061705874580a3af70036ff704b08ef289b3db31f9b090dca2da10180d9e7196a98119b777d72c178511f96652dd02e30eec72656b4041692625a8eaedab9fb780ab1cab350ef1e71cffbec3b38cfc3dc43aa8b3ef70c306fc12f5fb6a3b494cac4d35a016ddb1967a24c34796669381af63c0ab2c07f8831ff"
        )
        val metadataBlob = Hex.decode(
            "bf6176016a6c6e75726c5f62617365bf6474797065665041595f563064626c6f62587fbf656c6e75726c7768747470733a2f2f66616b652e69742f696e697469616c6863616c6c6261636b781868747470733a2f2f66616b652e69742f63616c6c6261636b6f6d696e53656e6461626c654d7361741a000f42406f6d617853656e6461626c654d7361741a001e8480706d6178436f6d6d656e744c656e677468f6ffff6e6c6e75726c5f6d65746164617461bf6474797065665041595f563064626c6f62582abf6372617778225b5b22746578742f706c61696e222c202246616b65206c6e75726c2d706179225d5dffff736c6e75726c5f73756363657373416374696f6ef6716c6e75726c5f6465736372697074696f6e6e46616b65206c6e75726c2d70617970757365725f6465736372697074696f6ef66a757365725f6e6f746573606d6f726967696e616c5f66696174bf6474797065635553446472617465fb40d073d51eb851ecffff"
        )

        val paymentData = CloudData.deserialize(paymentBlob)
        assertIs<CloudData.V0>(paymentData)
        val outgoingPayment = paymentData.outgoing?.unwrap()
        assertNotNull(outgoingPayment)
        assertIs<LightningOutgoingPayment>(outgoingPayment)
        assertEquals(1_003_100.msat, outgoingPayment.amount)
        assertEquals(1_000_000.msat, outgoingPayment.recipientAmount)
        assertEquals(3_100.msat, outgoingPayment.fees)
        assertEquals(1, outgoingPayment.parts.size)

        val metadataRow = CloudAsset.cborDeserialize(metadataBlob)
        assertNotNull(metadataRow)
        val metadata = metadataRow.unwrap().deserialize()
        val lnurlPay = metadata.lnurl
        assertNotNull(lnurlPay)
        assertEquals("https://fake.it/initial", lnurlPay.pay.initialUrl.toString())
        assertEquals(1_000_000.msat, lnurlPay.pay.minSendable)
        assertEquals(2_000_000.msat, lnurlPay.pay.maxSendable)
        assertEquals("Fake lnurl-pay", lnurlPay.pay.metadata.plainText)
        assertEquals("Fake lnurl-pay", lnurlPay.description)
    }

    companion object {
        private val defaultFeatures = Features(
            Feature.VariableLengthOnion to FeatureSupport.Optional,
            Feature.PaymentSecret to FeatureSupport.Optional,
            Feature.BasicMultiPartPayment to FeatureSupport.Optional
        )

        private fun createBolt11Invoice(preimage: ByteVector32, amount: MilliSatoshi): Bolt11Invoice {
            return Bolt11Invoice.create(
                chain = Chain.Mainnet,
                amount = amount,
                paymentHash = Crypto.sha256(preimage).toByteVector32(),
                privateKey = randomKey(),
                description = Either.Left("invoice"),
                minFinalCltvExpiryDelta = CltvExpiryDelta(16),
                features = defaultFeatures
            )
        }
    }
}