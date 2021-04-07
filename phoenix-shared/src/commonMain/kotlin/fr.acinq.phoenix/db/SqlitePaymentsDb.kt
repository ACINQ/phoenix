/*
 * Copyright 2020 ACINQ SAS
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

import com.squareup.sqldelight.EnumColumnAdapter
import com.squareup.sqldelight.db.SqlDriver
import com.squareup.sqldelight.runtime.coroutines.asFlow
import com.squareup.sqldelight.runtime.coroutines.mapToList
import fr.acinq.bitcoin.ByteVector
import fr.acinq.bitcoin.ByteVector32
import fr.acinq.bitcoin.PublicKey
import fr.acinq.lightning.MilliSatoshi
import fr.acinq.lightning.channel.ChannelException
import fr.acinq.lightning.db.*
import fr.acinq.lightning.utils.Either
import fr.acinq.lightning.utils.UUID
import fr.acinq.lightning.wire.FailureMessage
import fr.acinq.phoenix.db.payments.*
import fracinqphoenixdb.Incoming_payments
import fracinqphoenixdb.Outgoing_payment_parts
import fracinqphoenixdb.Outgoing_payments
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

class SqlitePaymentsDb(private val driver: SqlDriver) : PaymentsDb {

    private val database = PaymentsDatabase(
        driver = driver,
        outgoing_payment_partsAdapter = Outgoing_payment_parts.Adapter(part_routeAdapter = OutgoingQueries.hopDescAdapter, part_status_typeAdapter = EnumColumnAdapter()),
        outgoing_paymentsAdapter = Outgoing_payments.Adapter(status_typeAdapter = EnumColumnAdapter(), details_typeAdapter = EnumColumnAdapter()),
        incoming_paymentsAdapter = Incoming_payments.Adapter(origin_typeAdapter = EnumColumnAdapter(), received_with_typeAdapter = EnumColumnAdapter())
    )
    internal val inQueries = IncomingQueries(database.incomingPaymentsQueries)
    private val outQueries = OutgoingQueries(database.outgoingPaymentsQueries)
    private val aggrQueries = database.aggregatedQueriesQueries

    override suspend fun addOutgoingParts(parentId: UUID, parts: List<OutgoingPayment.Part>) {
        withContext(Dispatchers.Default) {
            outQueries.addOutgoingParts(parentId, parts)
        }
    }

    override suspend fun addOutgoingPayment(outgoingPayment: OutgoingPayment) {
        withContext(Dispatchers.Default) {
            outQueries.addOutgoingPayment(outgoingPayment)
        }
    }

    override suspend fun completeOutgoingPayment(id: UUID, completed: OutgoingPayment.Status.Completed) {
        withContext(Dispatchers.Default) {
            outQueries.completeOutgoingPayment(id, completed)
        }
    }

    override suspend fun updateOutgoingPart(partId: UUID, preimage: ByteVector32, completedAt: Long) {
        withContext(Dispatchers.Default) {
            outQueries.updateOutgoingPart(partId, preimage, completedAt)
        }
    }

    override suspend fun updateOutgoingPart(partId: UUID, failure: Either<ChannelException, FailureMessage>, completedAt: Long) {
        withContext(Dispatchers.Default) {
            outQueries.updateOutgoingPart(partId, failure, completedAt)
        }
    }

    override suspend fun getOutgoingPart(partId: UUID): OutgoingPayment? {
        return withContext(Dispatchers.Default) {
            outQueries.getOutgoingPart(partId)
        }
    }

    override suspend fun getOutgoingPayment(id: UUID): OutgoingPayment? {
        return withContext(Dispatchers.Default) {
            outQueries.getOutgoingPayment(id)
        }
    }

    // ---- list outgoing

    override suspend fun listOutgoingPayments(paymentHash: ByteVector32): List<OutgoingPayment> {
        return withContext(Dispatchers.Default) {
            outQueries.listOutgoingPayments(paymentHash)
        }
    }

    @Deprecated("This method uses offset and has bad performances, use seek method instead when possible")
    override suspend fun listOutgoingPayments(count: Int, skip: Int, filters: Set<PaymentTypeFilter>): List<OutgoingPayment> {
        return withContext(Dispatchers.Default) {
            outQueries.listOutgoingPayments(count, skip)
        }
    }

    // ---- incoming payments

    override suspend fun addIncomingPayment(preimage: ByteVector32, origin: IncomingPayment.Origin, createdAt: Long) {
        withContext(Dispatchers.Default) {
            inQueries.addIncomingPayment(preimage, origin, createdAt)
        }
    }

    override suspend fun receivePayment(paymentHash: ByteVector32, amount: MilliSatoshi, receivedWith: IncomingPayment.ReceivedWith, receivedAt: Long) {
        withContext(Dispatchers.Default) {
            inQueries.receivePayment(paymentHash, amount, receivedWith, receivedAt)
        }
    }

    override suspend fun addAndReceivePayment(preimage: ByteVector32, origin: IncomingPayment.Origin, amount: MilliSatoshi, receivedWith: IncomingPayment.ReceivedWith, createdAt: Long, receivedAt: Long) {
        withContext(Dispatchers.Default) {
            inQueries.addAndReceivePayment(preimage, origin, amount, receivedWith, createdAt, receivedAt)
        }
    }

    override suspend fun getIncomingPayment(paymentHash: ByteVector32): IncomingPayment? {
        return withContext(Dispatchers.Default) {
            inQueries.getIncomingPayment(paymentHash)
        }
    }

    override suspend fun listReceivedPayments(count: Int, skip: Int, filters: Set<PaymentTypeFilter>): List<IncomingPayment> {
        return withContext(Dispatchers.Default) {
            inQueries.listReceivedPayments(count, skip)
        }
    }

    // ---- list ALL payments

    override suspend fun listPayments(count: Int, skip: Int, filters: Set<PaymentTypeFilter>): List<WalletPayment> {
        return withContext(Dispatchers.Default) {
            aggrQueries.listAllPayments(skip.toLong(), count.toLong(), ::allPaymentsMapper).executeAsList()
        }
    }

    suspend fun listPaymentsFlow(count: Int, skip: Int, filters: Set<PaymentTypeFilter>): Flow<List<WalletPayment>> {
        return withContext(Dispatchers.Default) {
            aggrQueries.listAllPayments(skip.toLong(), count.toLong(), ::allPaymentsMapper).asFlow().mapToList()
        }
    }


    private fun allPaymentsMapper(
        direction: String,
        outgoing_payment_id: String?,
        payment_hash: ByteArray,
        parts_total_succeeded: Long?,
        amount: Long,
        outgoing_recipient: String?,
        outgoing_details_type: OutgoingDetailsTypeVersion?,
        outgoing_details_blob: ByteArray?,
        outgoing_status_type: OutgoingStatusTypeVersion?,
        outgoing_status_blob: ByteArray?,
        incoming_preimage: ByteArray?,
        incoming_origin_type: IncomingOriginTypeVersion?,
        incoming_origin_blob: ByteArray?,
        incoming_received_with_type: IncomingReceivedWithTypeVersion?,
        incoming_received_with_blob: ByteArray?,
        created_at: Long,
        completed_at: Long?
    ): WalletPayment = when (direction.toLowerCase()) {
        "outgoing" -> {
            val details = OutgoingDetailsData.deserialize(outgoing_details_type!!, outgoing_details_blob!!)

            // An OutgoingPayment is split between 2 tables:
            // - outgoing_payments
            // - outgoing_payment_parts
            //
            // But for this query, we're only fetching the SUM from outgoing_payment_parts.
            // This means we will not have a proper OutgoingPayment.parts list.
            // But we do have the SUM, which we can use to relay information about the fees.
            //
            val parts: List<OutgoingPayment.Part> =
                if (parts_total_succeeded != null) {
                    // For normal payments, we create a FAKE parts list.
                    // This allows us to properly calculate the fees associated with the payment.
                    // Note that we need to know ALL of the following:
                    // - totalAmount
                    // - feesAmount
                    //
                    listOf(
                        OutgoingPayment.Part(
                            id = UUID.fromString("00000000-0000-0000-0000-000000000000"),
                            amount = MilliSatoshi(parts_total_succeeded),
                            route = listOf(),
                            status = OutgoingPayment.Part.Status.Succeeded(
                                preimage = ByteVector32.Zeroes,
                                completedAt = 0
                            ),
                            createdAt = 0 // <= always zero for fake parts
                        )
                    )
                } else {
                    listOf()
                }
            OutgoingPayment(
                id = UUID.fromString(outgoing_payment_id!!),
                recipientAmount = MilliSatoshi(amount),
                recipient = PublicKey(ByteVector(outgoing_recipient!!)),
                details = details,
                parts = parts,
                status = OutgoingQueries.mapOutgoingPaymentStatus(
                    completed_at = completed_at,
                    status_type = outgoing_status_type,
                    status = outgoing_status_blob,
                ),
                createdAt = created_at
            )
        }
        "incoming" -> IncomingQueries.mapIncomingPayment(
            payment_hash = payment_hash,
            preimage = incoming_preimage!!,
            created_at = created_at,
            origin_type = incoming_origin_type!!,
            origin_blob = incoming_origin_blob!!,
            received_amount_msat = amount,
            received_at = completed_at,
            received_with_type = incoming_received_with_type,
            received_with_blob = incoming_received_with_blob,
        )
        else -> throw UnhandledDirection(direction)
    }
}

class OutgoingPaymentPartNotFound(partId: UUID) : RuntimeException("could not find outgoing payment part with part_id=$partId")
class IncomingPaymentNotFound(paymentHash: ByteVector32) : RuntimeException("missing payment for payment_hash=$paymentHash")
class UnhandledDirection(direction: String) : RuntimeException("unhandled direction=$direction")
