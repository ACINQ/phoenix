/*
 * Copyright 2023 ACINQ SAS
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

package fr.acinq.phoenix.utils.migrations

import fr.acinq.bitcoin.ByteVector32
import fr.acinq.bitcoin.Satoshi
import fr.acinq.lightning.MilliSatoshi
import fr.acinq.lightning.db.ChannelCloseOutgoingPayment
import fr.acinq.lightning.utils.UUID
import fr.acinq.lightning.utils.sat
import fr.acinq.phoenix.db.payments.OutgoingDetailsData
import fr.acinq.phoenix.db.payments.OutgoingPartClosingInfoData
import fr.acinq.phoenix.db.payments.OutgoingPartClosingInfoTypeVersion
import fr.acinq.phoenix.db.payments.OutgoingStatusData

object LegacyChannelCloseHelper {

    /**
     * Create a [ChannelCloseOutgoingPayment] object from a bunch of legacy data that were stored in closing-tx parts
     * and old outgoing status/details.
     */
    fun convertLegacyToChannelClose(
        id: UUID,
        recipientAmount: MilliSatoshi,
        detailsBlob: ByteArray?,
        statusBlob: ByteArray?,
        partsAmount: Satoshi?,
        partsTxId: ByteVector32?,
        partsClosingTypeBlob: ByteArray?,
        createdAt: Long,
        confirmedAt: Long?,
    ): ChannelCloseOutgoingPayment {
        val closingDetails = try {
            detailsBlob?.let { OutgoingDetailsData.deserializeLegacyClosingDetails(it) }
        } catch (e: Exception) {
            null
        }
        val statusInfoV0 = try {
            statusBlob?.let { OutgoingStatusData.deserializeLegacyClosingStatus(it) }
        } catch (e: Exception) {
            null
        }

        val fees = (partsAmount ?: statusInfoV0?.claimed)?.let { recipientAmount.truncateToSatoshi() - it }
            ?.takeIf { it > 0.sat } ?: 0.sat

        val closingTxId = partsTxId ?: statusInfoV0?.txIds?.firstOrNull()
        val closingType = partsClosingTypeBlob?.let {
            OutgoingPartClosingInfoData.deserialize(
                typeVersion = OutgoingPartClosingInfoTypeVersion.CLOSING_INFO_V0,
                blob = it
            )
        } ?: statusInfoV0?.closingType?.let {
            try {
                ChannelCloseOutgoingPayment.ChannelClosingType.valueOf(it)
            } catch (e: Exception) {
                null
            }
        }
        return ChannelCloseOutgoingPayment(
            id = id,
            amountSatoshi = recipientAmount.truncateToSatoshi(),
            address = closingDetails?.closingAddress ?: "",
            isSentToDefaultAddress = closingDetails?.isSentToDefaultAddress
                ?: (closingType == ChannelCloseOutgoingPayment.ChannelClosingType.Local
                        || closingType == ChannelCloseOutgoingPayment.ChannelClosingType.Revoked
                        || closingType == ChannelCloseOutgoingPayment.ChannelClosingType.Remote
                        || closingType == ChannelCloseOutgoingPayment.ChannelClosingType.Other),
            miningFees = fees,
            txId = closingTxId ?: ByteVector32.Zeroes,
            createdAt = createdAt,
            confirmedAt = confirmedAt ?: createdAt,
            channelId = closingDetails?.channelId ?: ByteVector32.Zeroes,
            closingType = closingType ?: ChannelCloseOutgoingPayment.ChannelClosingType.Mutual,
        )
    }
}