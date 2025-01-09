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

@file:UseSerializers(
    MilliSatoshiSerializer::class,
    TxIdSerializer::class,
)

package fr.acinq.phoenix.db.migrations.v11.types.liquidityads

import fr.acinq.bitcoin.TxId
import fr.acinq.lightning.MilliSatoshi
import fr.acinq.phoenix.db.migrations.v10.json.MilliSatoshiSerializer
import fr.acinq.phoenix.db.migrations.v10.json.TxIdSerializer
import fr.acinq.lightning.wire.LiquidityAds
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers

@Serializable
sealed class FundingFeeData {

    @Serializable
    @SerialName("fr.acinq.phoenix.db.payments.liquidityads.FundingFeeData.V0")
    data class V0(val amount: MilliSatoshi, val fundingTxId: TxId) : FundingFeeData()
}