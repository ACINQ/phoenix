/*
 * Copyright 2024 ACINQ SAS
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

package fr.acinq.phoenix.db.migrations.v11.queries

import fr.acinq.bitcoin.TxId
import fr.acinq.lightning.db.AutomaticLiquidityPurchasePayment
import fr.acinq.lightning.db.Bolt11IncomingPayment
import fr.acinq.lightning.db.Bolt12IncomingPayment
import fr.acinq.lightning.db.IncomingPayment
import fr.acinq.lightning.db.LightningIncomingPayment
import fr.acinq.lightning.db.ManualLiquidityPurchasePayment
import fr.acinq.lightning.db.NewChannelIncomingPayment
import fr.acinq.lightning.db.OutgoingPayment
import fr.acinq.lightning.utils.UUID
import fr.acinq.lightning.utils.sat
import fr.acinq.lightning.utils.toByteVector32
import fr.acinq.lightning.utils.toMilliSatoshi
import fr.acinq.lightning.wire.LiquidityAds
import fr.acinq.phoenix.db.migrations.v11.types.liquidityads.PurchaseData

object InboundLiquidityQueries {

    fun mapPayment(
        id: String,
        mining_fees_sat: Long,
        channel_id: ByteArray,
        tx_id: ByteArray,
        lease_type: String,
        lease_blob: ByteArray,
        created_at: Long,
        confirmed_at: Long?,
        locked_at: Long?,
        incomingPayment: IncomingPayment?
    ): Pair<IncomingPayment?, OutgoingPayment?> {
        val channelId = channel_id.toByteVector32()
        val miningFee = mining_fees_sat.sat
        val txId = TxId(tx_id)
        val purchase = PurchaseData.decodeAsCanonical(lease_type, lease_blob)

        return when (incomingPayment) {
            is LightningIncomingPayment -> {
                val liquidityPurchaseDetails = LiquidityAds.LiquidityTransactionDetails(
                    txId = txId,
                    miningFee = miningFee,
                    purchase = purchase
                )
                val (incomingPayment1, incomingPaymentReceivedAt) = when (incomingPayment) {
                    is Bolt11IncomingPayment -> incomingPayment.copy(
                        liquidityPurchaseDetails = liquidityPurchaseDetails
                    ) to incomingPayment.completedAt

                    is Bolt12IncomingPayment -> incomingPayment.copy(
                        liquidityPurchaseDetails = liquidityPurchaseDetails
                    ) to incomingPayment.completedAt
                }
                val liquidityPayment = AutomaticLiquidityPurchasePayment(
                    id = UUID.fromString(id),
                    miningFee = miningFee,
                    channelId = channelId,
                    txId = txId,
                    liquidityPurchase = purchase,
                    createdAt = created_at,
                    confirmedAt = confirmed_at,
                    lockedAt = locked_at,
                    incomingPaymentReceivedAt = incomingPaymentReceivedAt
                )
                incomingPayment1 to liquidityPayment
            }

            is NewChannelIncomingPayment -> {
                val incomingPayment1 =
                    incomingPayment.copy(
                        miningFee = incomingPayment.miningFee + purchase.fees.miningFee,
                        serviceFee = purchase.fees.serviceFee.toMilliSatoshi(),
                        liquidityPurchase = purchase
                    )
                incomingPayment1 to null
            }

            null -> {
                val liquidityPayment = ManualLiquidityPurchasePayment(
                    id = UUID.fromString(id),
                    miningFee = miningFee,
                    channelId = channelId,
                    txId = txId,
                    liquidityPurchase = purchase,
                    createdAt = created_at,
                    confirmedAt = confirmed_at,
                    lockedAt = locked_at
                )
                null to liquidityPayment
            }

            else -> error("impossible")
        }
    }
}