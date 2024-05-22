/*
 * Copyright 2021 ACINQ SAS
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

import fr.acinq.bitcoin.ByteVector32
import fr.acinq.bitcoin.TxId
import fr.acinq.lightning.db.IncomingPayment
import fr.acinq.lightning.payment.Bolt11Invoice
import fr.acinq.lightning.utils.msat
import fr.acinq.lightning.utils.sat
import fr.acinq.phoenix.db.payments.*
import fr.acinq.secp256k1.Hex
import io.ktor.utils.io.charsets.*
import io.ktor.utils.io.core.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals

class IncomingPaymentDbTypeVersionTest {

    val channelId1 = ByteVector32.fromValidHex("3b6208285563c9adb009781acf1626f1c2a3b1a3492d5ec312ead8282c7ad6da")
    val address1 = "tb1q97tpc0y4rvdnu9wm7nu354lmmzdm8du228u3g4"
    val bolt11Invoice =
        Bolt11Invoice.read("lntb1500n1ps9u963pp5llphsu6evgmzgk8g2e73su44wn6txmwywdzwvtdwzrt9pqxc9f5sdpzxysy2umswfjhxum0yppk76twypgxzmnwvycqp7xqrrss9qy9qsqsp5qa7092geq6ptp24uzlfw0vj3w4whh2zuc9rquwca69acwx5khckqvslyw2n6dallc868vxu3uueyhw6pe00cmluynv7ca4tknz7g274rp9ucwqpx5ydejsmzl4xpegqtemcq6vwvu8alpxttlj82e7j26gspfj06gn").get()

    @Test
    fun incoming_origin_invoice() {
        val origin = IncomingPayment.Origin.Invoice(bolt11Invoice)
        val deserialized = IncomingOriginData.deserialize(IncomingOriginTypeVersion.INVOICE_V0, origin.mapToDb().second)
        assertEquals(origin, deserialized)
    }

    @Test
    fun incoming_origin_swapin() {
        val origin = IncomingPayment.Origin.SwapIn(address1)
        val deserialized = IncomingOriginData.deserialize(IncomingOriginTypeVersion.SWAPIN_V0, origin.mapToDb().second)
        assertEquals(origin, deserialized)
    }

    @Test
    fun incoming_origin_swapin_null() {
        val origin = IncomingPayment.Origin.SwapIn(null)
        val deserialized = IncomingOriginData.deserialize(IncomingOriginTypeVersion.SWAPIN_V0, origin.mapToDb().second)
        assertEquals(origin, deserialized)
    }

    @Test
    @Suppress("DEPRECATION")
    fun incoming_receivedwith_multipart_v0_lightning() {
        val receivedWith = listOf(IncomingPayment.ReceivedWith.LightningPayment(100_000.msat, ByteVector32.One, 2L))
        val deserialized = IncomingReceivedWithData.deserialize(
            IncomingReceivedWithTypeVersion.MULTIPARTS_V0,
            receivedWith.mapToDb()!!.second,
            null,
            IncomingOriginTypeVersion.INVOICE_V0
        )
        assertEquals(receivedWith.first(), deserialized.first())
    }

    @Test
    fun incoming_receivedwith_multipart_v1_lightning() {
        val receivedWith = listOf(IncomingPayment.ReceivedWith.LightningPayment(100_000.msat, ByteVector32.One, 2L))
        val deserialized = IncomingReceivedWithData.deserialize(
            IncomingReceivedWithTypeVersion.MULTIPARTS_V1,
            receivedWith.mapToDb()!!.second,
            null,
            IncomingOriginTypeVersion.INVOICE_V0
        )
        assertEquals(receivedWith.first(), deserialized.first())
    }

    @Test
    @Suppress("DEPRECATION")
    fun incoming_receivedwith_multipart_v0_newchannel_paytoopen() {
        // pay-to-open with MULTIPARTS_V0: amount contains the fee which is a special case that must be fixed when deserializing.
        val receivedWith = listOf(IncomingPayment.ReceivedWith.NewChannel(1_995_000.msat, 5_000.msat, 0.sat, channelId1, TxId(ByteVector32.Zeroes), confirmedAt = 0, lockedAt = 0))
        val deserialized = IncomingReceivedWithData.deserialize(
            IncomingReceivedWithTypeVersion.MULTIPARTS_V0,
            Hex.decode("5b7b2274797065223a2266722e6163696e712e70686f656e69782e64622e7061796d656e74732e496e636f6d696e67526563656976656457697468446174612e506172742e4e65774368616e6e656c2e5630222c22616d6f756e74223a7b226d736174223a323030303030307d2c2266656573223a7b226d736174223a353030307d2c226368616e6e656c4964223a2233623632303832383535363363396164623030393738316163663136323666316332613362316133343932643565633331326561643832383263376164366461227d5d"),
            null,
            IncomingOriginTypeVersion.INVOICE_V0
        )
        assertEquals(1, deserialized.size)
        assertEquals(receivedWith, deserialized)
    }

    @Test
    fun incoming_receivedwith_multipart_v1_newchannel_paytoopen() {
        val receivedWith = listOf(IncomingPayment.ReceivedWith.NewChannel(1_995_000.msat, 5_000.msat, 0.sat, channelId1, TxId(ByteVector32.Zeroes), confirmedAt = 10, lockedAt = 20))
        val deserialized = IncomingReceivedWithData.deserialize(
            IncomingReceivedWithTypeVersion.MULTIPARTS_V1,
            receivedWith.mapToDb()!!.second,
            null,
            IncomingOriginTypeVersion.INVOICE_V0
        )
        assertEquals(receivedWith, deserialized)
    }

    @Test
    @Suppress("DEPRECATION")
    fun incoming_receivedwith_multipart_v0_newchannel_swapin_nochannel() {
        val receivedWith = listOf(IncomingPayment.ReceivedWith.NewChannel(111111111.msat, 1000.msat, 0.sat, ByteVector32.Zeroes, TxId(ByteVector32.Zeroes), confirmedAt = 0, lockedAt = 0))
        val deserialized = IncomingReceivedWithData.deserialize(
            IncomingReceivedWithTypeVersion.MULTIPARTS_V0,
            Hex.decode("5b7b2274797065223a2266722e6163696e712e70686f656e69782e64622e7061796d656e74732e496e636f6d696e67526563656976656457697468446174612e506172742e4e65774368616e6e656c2e5630222c22616d6f756e74223a7b226d736174223a3131313131313131317d2c2266656573223a7b226d736174223a313030307d2c226368616e6e656c4964223a2230303030303030303030303030303030303030303030303030303030303030303030303030303030303030303030303030303030303030303030303030303030227d5d"),
            null,
            IncomingOriginTypeVersion.SWAPIN_V0
        )
        assertEquals(receivedWith, deserialized)
    }

    @Test
    fun incoming_receivedwith_multipart_v1_newchannel_swapin_nochannel() {
        val receivedWith = listOf(IncomingPayment.ReceivedWith.NewChannel(164495787.msat, 4058671.msat, 0.sat, ByteVector32.Zeroes, TxId(ByteVector32.Zeroes), confirmedAt = 10, lockedAt = 20))
        val deserialized = IncomingReceivedWithData.deserialize(
            IncomingReceivedWithTypeVersion.MULTIPARTS_V1,
            receivedWith.mapToDb()!!.second,
            null,
            IncomingOriginTypeVersion.SWAPIN_V0
        )
        assertEquals(receivedWith, deserialized)
    }

    @Test
    @Suppress("DEPRECATION")
    fun incoming_receivedwith_lightning_legacy() {
        val deserialized = IncomingReceivedWithData.deserialize(
            IncomingReceivedWithTypeVersion.LIGHTNING_PAYMENT_V0,
            Json.encodeToString(IncomingReceivedWithData.LightningPayment.V0).toByteArray(Charsets.UTF_8),
            999_999.msat,
            IncomingOriginTypeVersion.INVOICE_V0
        ).first() as IncomingPayment.ReceivedWith.LightningPayment

        assertEquals(999_999.msat, deserialized.amount)
        assertEquals(0.msat, deserialized.fees)
        assertEquals(ByteVector32.Zeroes, deserialized.channelId)
        assertEquals(0L, deserialized.htlcId)
    }

    @Test
    @Suppress("DEPRECATION")
    fun incoming_receivedwith_newchannel_legacy() {
        val deserialized = IncomingReceivedWithData.deserialize(
            IncomingReceivedWithTypeVersion.NEW_CHANNEL_V0,
            Json.encodeToString(IncomingReceivedWithData.NewChannel.V0(15_000.msat, channelId1)).toByteArray(Charsets.UTF_8),
            123_456.msat,
            IncomingOriginTypeVersion.SWAPIN_V0
        )
            .first() as IncomingPayment.ReceivedWith.NewChannel
        assertEquals(123_456.msat, deserialized.amount)
        assertEquals(15_000.msat, deserialized.fees)
        assertEquals(channelId1, deserialized.channelId)
    }
}