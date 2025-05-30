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
import fr.acinq.lightning.db.SpliceCpfpOutgoingPayment
import fr.acinq.lightning.utils.UUID
import fr.acinq.lightning.utils.sat
import fr.acinq.lightning.utils.toByteVector32

object SpliceCpfpOutgoingQueries {

    fun mapCpfp(
        id: String,
        mining_fees_sat: Long,
        channel_id: ByteArray,
        tx_id: ByteArray,
        created_at: Long,
        confirmed_at: Long?,
        locked_at: Long?
    ): SpliceCpfpOutgoingPayment {
        return SpliceCpfpOutgoingPayment(
            id = UUID.fromString(id),
            miningFee = mining_fees_sat.sat,
            channelId = channel_id.toByteVector32(),
            txId = TxId(tx_id),
            createdAt = created_at,
            confirmedAt = confirmed_at,
            lockedAt = locked_at
        )
    }
}