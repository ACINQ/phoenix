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

package fr.acinq.phoenix.db.payments

import fr.acinq.bitcoin.ByteVector32
import fr.acinq.bitcoin.Crypto
import fr.acinq.lightning.MilliSatoshi
import fr.acinq.lightning.db.IncomingPayment
import fr.acinq.lightning.utils.toByteVector32
import fr.acinq.phoenix.db.IncomingPaymentNotFound
import fracinqphoenixdb.IncomingPaymentsQueries

class IncomingQueries(private val queries: IncomingPaymentsQueries) {

    fun addIncomingPayment(preimage: ByteVector32, origin: IncomingPayment.Origin, createdAt: Long) {
        val (originType, originData) = origin.mapToDb()
        queries.insert(
            payment_hash = Crypto.sha256(preimage).toByteVector32().toByteArray(),
            preimage = preimage.toByteArray(),
            origin_type = originType,
            origin_blob = originData,
            created_at = createdAt
        )
    }

    fun receivePayment(paymentHash: ByteVector32, amount: MilliSatoshi, receivedWith: IncomingPayment.ReceivedWith, receivedAt: Long) {
        val (receivedWithType, receivedWithBlob) = receivedWith.mapToDb()
        queries.transaction {
            queries.receive(
                value = amount.msat,
                received_at = receivedAt,
                received_with_type = receivedWithType,
                received_with_blob = receivedWithBlob,
                payment_hash = paymentHash.toByteArray()
            )
            if (queries.changes().executeAsOne() != 1L) throw IncomingPaymentNotFound(paymentHash)
        }
    }

    fun addAndReceivePayment(preimage: ByteVector32, origin: IncomingPayment.Origin, amount: MilliSatoshi, receivedWith: IncomingPayment.ReceivedWith, createdAt: Long, receivedAt: Long) {
        val (originType, originData) = origin.mapToDb()
        val (receivedWithType, receivedWithBlob) = receivedWith.mapToDb()
        queries.insertAndReceive(
            payment_hash = Crypto.sha256(preimage).toByteVector32().toByteArray(),
            preimage = preimage.toByteArray(),
            origin_type = originType,
            origin_blob = originData,
            received_amount_msat = amount.msat,
            received_at = receivedAt,
            received_with_type = receivedWithType,
            received_with_blob = receivedWithBlob,
            created_at = createdAt
        )
    }

    fun getIncomingPayment(paymentHash: ByteVector32): IncomingPayment? {
        return queries.get(payment_hash = paymentHash.toByteArray(), ::mapIncomingPayment).executeAsOneOrNull()
    }

    fun listReceivedPayments(count: Int, skip: Int): List<IncomingPayment> {
        return queries.list(skip.toLong(), count.toLong(), ::mapIncomingPayment).executeAsList()
    }

    companion object {
        fun mapIncomingPayment(
            @Suppress("UNUSED_PARAMETER") payment_hash: ByteArray,
            preimage: ByteArray,
            created_at: Long,
            origin_type: IncomingOriginTypeVersion,
            origin_blob: ByteArray,
            received_amount_msat: Long?,
            received_at: Long?,
            received_with_type: IncomingReceivedWithTypeVersion?,
            received_with_blob: ByteArray?,
        ): IncomingPayment {
            return IncomingPayment(
                preimage = ByteVector32(preimage),
                origin = IncomingOriginData.deserialize(origin_type, origin_blob),
                received = mapIncomingReceived(received_amount_msat, received_at, received_with_type, received_with_blob),
                createdAt = created_at
            )
        }

        private fun mapIncomingReceived(amountMsat: Long?, receivedAt: Long?, receivedWithTypeVersion: IncomingReceivedWithTypeVersion?, receivedWithBlob: ByteArray?): IncomingPayment.Received? {
            return when {
                amountMsat == null && receivedAt == null && receivedWithTypeVersion == null && receivedWithBlob == null -> null
                amountMsat != null && receivedAt != null && receivedWithTypeVersion != null && receivedWithBlob != null ->
                    IncomingPayment.Received(MilliSatoshi(amountMsat), IncomingReceivedWithData.deserialize(receivedWithTypeVersion, receivedWithBlob), receivedAt)
                else -> throw UnreadableIncomingReceivedWith(amountMsat, receivedAt, receivedWithTypeVersion, receivedWithBlob)
            }
        }
    }
}

class UnreadableIncomingReceivedWith(amount: Long?, receivedAt: Long?, receivedWithTypeVersion: IncomingReceivedWithTypeVersion?, receivedWithBlob: ByteArray?) :
    RuntimeException("unreadable received with data [ amount=$amount, receivedAt=$receivedAt, receivedWithTypeVersion=$receivedWithTypeVersion, receivedWithBlob=$receivedWithBlob ]")