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
import fr.acinq.lightning.db.ChannelCloseOutgoingPayment
import fr.acinq.lightning.utils.UUID
import fr.acinq.lightning.utils.sat
import fr.acinq.lightning.utils.toByteVector32
import fr.acinq.phoenix.db.migrations.v11.types.OutgoingPartClosingInfoData
import fr.acinq.phoenix.db.migrations.v11.types.OutgoingPartClosingInfoTypeVersion

object ChannelCloseOutgoingQueries {

    fun mapChannelCloseOutgoingPayment(
        id: String,
        amount_sat: Long,
        address: String,
        is_default_address: Long,
        mining_fees_sat: Long,
        tx_id: ByteArray,
        created_at: Long,
        confirmed_at: Long?,
        locked_at: Long?,
        channel_id: ByteArray,
        closing_info_type: OutgoingPartClosingInfoTypeVersion,
        closing_info_blob: ByteArray
    ): ChannelCloseOutgoingPayment {
        return ChannelCloseOutgoingPayment(
            id = UUID.fromString(id),
            recipientAmount = amount_sat.sat,
            address = address,
            isSentToDefaultAddress = is_default_address == 1L,
            miningFee = mining_fees_sat.sat,
            txId = TxId(tx_id),
            createdAt = created_at,
            confirmedAt = confirmed_at,
            lockedAt = locked_at,
            channelId = channel_id.toByteVector32(),
            closingType = OutgoingPartClosingInfoData.deserialize(
                closing_info_type,
                closing_info_blob
            ),
        )
    }
}