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

@file:UseSerializers(
    ByteVectorSerializer::class,
    ByteVector32Serializer::class,
    ByteVector64Serializer::class,
    SatoshiSerializer::class,
    MilliSatoshiSerializer::class
)

package fr.acinq.phoenix.db.payments

import fr.acinq.bitcoin.ByteVector
import fr.acinq.bitcoin.ByteVector64
import fr.acinq.bitcoin.Satoshi
import fr.acinq.lightning.MilliSatoshi
import fr.acinq.lightning.db.InboundLiquidityOutgoingPayment
import fr.acinq.lightning.wire.LiquidityAds
import fr.acinq.phoenix.db.serializers.v1.ByteVector32Serializer
import fr.acinq.phoenix.db.serializers.v1.ByteVector64Serializer
import fr.acinq.phoenix.db.serializers.v1.ByteVectorSerializer
import fr.acinq.phoenix.db.serializers.v1.MilliSatoshiSerializer
import fr.acinq.phoenix.db.serializers.v1.SatoshiSerializer
import io.ktor.utils.io.charsets.Charsets
import io.ktor.utils.io.core.toByteArray
import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

enum class InboundLiquidityLeaseTypeVersion {
    LEASE_V0,
}

sealed class InboundLiquidityLeaseData {

    @Serializable
    data class V0(
        val amount: Satoshi,
        val miningFees: Satoshi,
        val serviceFee: Satoshi,
        val sellerSig: ByteVector64,
        val witnessFundingScript: ByteVector,
        val witnessLeaseDuration: Int,
        val witnessLeaseEnd: Int,
        val witnessMaxRelayFeeProportional: Int,
        val witnessMaxRelayFeeBase: MilliSatoshi
    ) : InboundLiquidityLeaseData()

    companion object {
        /** Deserializes a json-encoded blob containing data for an [LiquidityAds.Lease] object. */
        fun deserialize(
            typeVersion: InboundLiquidityLeaseTypeVersion,
            blob: ByteArray,
        ): LiquidityAds.Lease = DbTypesHelper.decodeBlob(blob) { json, format ->
            when (typeVersion) {
                InboundLiquidityLeaseTypeVersion.LEASE_V0 -> format.decodeFromString<V0>(json).let {
                    LiquidityAds.Lease(
                        amount = it.amount,
                        fees = LiquidityAds.LeaseFees(miningFee = it.miningFees, serviceFee = it.serviceFee),
                        sellerSig = it.sellerSig,
                        witness = LiquidityAds.LeaseWitness(
                            fundingScript = it.witnessFundingScript,
                            leaseDuration = it.witnessLeaseDuration,
                            leaseEnd = it.witnessLeaseEnd,
                            maxRelayFeeProportional = it.witnessMaxRelayFeeProportional,
                            maxRelayFeeBase = it.witnessMaxRelayFeeBase,
                        )
                    )
                }
            }
        }
    }
}

fun InboundLiquidityOutgoingPayment.mapLeaseToDb() = InboundLiquidityLeaseTypeVersion.LEASE_V0 to
        InboundLiquidityLeaseData.V0(
            amount = lease.amount,
            miningFees = lease.fees.miningFee,
            serviceFee = lease.fees.serviceFee,
            sellerSig = lease.sellerSig,
            witnessFundingScript = lease.witness.fundingScript,
            witnessLeaseDuration = lease.witness.leaseDuration,
            witnessLeaseEnd = lease.witness.leaseEnd,
            witnessMaxRelayFeeProportional = lease.witness.maxRelayFeeProportional,
            witnessMaxRelayFeeBase = lease.witness.maxRelayFeeBase,
        ).let {
            Json.encodeToString(it).toByteArray(Charsets.UTF_8)
        }
