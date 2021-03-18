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

import com.squareup.sqldelight.ColumnAdapter
import com.squareup.sqldelight.EnumColumnAdapter
import com.squareup.sqldelight.db.SqlDriver
import fr.acinq.bitcoin.ByteVector
import fr.acinq.bitcoin.ByteVector32
import fr.acinq.bitcoin.Crypto
import fr.acinq.bitcoin.PublicKey
import fr.acinq.eclair.MilliSatoshi
import fr.acinq.eclair.ShortChannelId
import fr.acinq.eclair.channel.ChannelException
import fr.acinq.eclair.db.*
import fr.acinq.eclair.payment.FinalFailure
import fr.acinq.eclair.payment.OutgoingPaymentFailure
import fr.acinq.eclair.payment.PaymentRequest
import fr.acinq.eclair.utils.Either
import fr.acinq.eclair.utils.UUID
import fr.acinq.eclair.utils.currentTimestampMillis
import fr.acinq.eclair.utils.toByteVector32
import fr.acinq.eclair.wire.FailureMessage
import fracinqphoenixdb.Incoming_payments
import fracinqphoenixdb.Outgoing_payment_parts
import fracinqphoenixdb.Outgoing_payments
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.kodein.memory.text.toHexString

class SqlitePaymentsDb(private val driver: SqlDriver) : PaymentsDb {

    enum class OutgoingFinalFailureDbEnum {
        InvalidPaymentAmount, InsufficientBalance, InvalidPaymentId, NoAvailableChannels, NoRouteToRecipient, RetryExhausted, UnknownError, WalletRestarted, RecipientUnreachable;

        companion object {
            fun toDb(failure: FinalFailure) = when (failure) {
                FinalFailure.InvalidPaymentAmount -> InvalidPaymentAmount
                FinalFailure.InsufficientBalance -> InsufficientBalance
                FinalFailure.InvalidPaymentId -> InvalidPaymentId
                FinalFailure.NoAvailableChannels -> NoAvailableChannels
                FinalFailure.NoRouteToRecipient -> NoRouteToRecipient
                FinalFailure.RetryExhausted -> RetryExhausted
                FinalFailure.UnknownError -> UnknownError
                FinalFailure.WalletRestarted -> WalletRestarted
                FinalFailure.RecipientUnreachable -> RecipientUnreachable
            }
        }
    }

    enum class IncomingOriginDbEnum {
        KeySend, Invoice, SwapIn;

        companion object {
            fun toDb(origin: IncomingPayment.Origin) = when (origin) {
                is IncomingPayment.Origin.KeySend -> KeySend
                is IncomingPayment.Origin.Invoice -> Invoice
                is IncomingPayment.Origin.SwapIn -> SwapIn
                else -> throw UnhandledIncomingOrigin(origin)
            }
        }
    }

    enum class IncomingReceivedWithDbEnum {
        LightningPayment, NewChannel;

        companion object {
            fun toDb(value: IncomingPayment.ReceivedWith) = when (value) {
                is IncomingPayment.ReceivedWith.NewChannel -> NewChannel
                is IncomingPayment.ReceivedWith.LightningPayment -> LightningPayment
                else -> throw UnhandledIncomingReceivedWith(value)
            }
        }
    }

    private val hopDescAdapter: ColumnAdapter<List<HopDesc>, String> = object : ColumnAdapter<List<HopDesc>, String> {
        override fun decode(databaseValue: String): List<HopDesc> = databaseValue.split(";").map { hop ->
            val els = hop.split(":")
            val n1 = PublicKey(ByteVector(els[0]))
            val n2 = PublicKey(ByteVector(els[1]))
            val cid = els[2].takeIf { it.isNotBlank() }?.run { ShortChannelId(this) }
            HopDesc(n1, n2, cid)
        }

        override fun encode(value: List<HopDesc>): String = value.joinToString(";") {
            "${it.nodeId}:${it.nextNodeId}:${it.shortChannelId ?: ""}"
        }
    }

    private val database = PaymentsDatabase(
        driver = driver,
        outgoing_paymentsAdapter = Outgoing_payments.Adapter(final_failureAdapter = EnumColumnAdapter()),
        outgoing_payment_partsAdapter = Outgoing_payment_parts.Adapter(routeAdapter = hopDescAdapter),
        incoming_paymentsAdapter = Incoming_payments.Adapter(payment_typeAdapter = EnumColumnAdapter(), received_withAdapter = EnumColumnAdapter())
    )
    private val inQueries = database.incomingPaymentsQueries
    private val outQueries = database.outgoingPaymentsQueries
    private val aggrQueries = database.aggregatedQueriesQueries

    // ---- insert new outgoing payments

    override suspend fun addOutgoingParts(parentId: UUID, parts: List<OutgoingPayment.Part>) {
        withContext(Dispatchers.Default) {
            outQueries.transaction {
                parts.map {
                    outQueries.addOutgoingPart(
                        part_id = it.id.toString(),
                        parent_id = parentId.toString(),
                        amount_msat = it.amount.msat,
                        route = it.route,
                        created_at = it.createdAt)
                }
            }
        }
    }

    override suspend fun addOutgoingPayment(outgoingPayment: OutgoingPayment) {
        withContext(Dispatchers.Default) {
            outQueries.transaction {
                outQueries.addOutgoingPayment(
                    id = outgoingPayment.id.toString(),
                    recipient_amount_msat = outgoingPayment.recipientAmount.msat,
                    recipient_node_id = outgoingPayment.recipient.toString(),
                    payment_hash = outgoingPayment.details.paymentHash.toByteArray(),
                    created_at = currentTimestampMillis(),
                    normal_payment_request = (outgoingPayment.details as? OutgoingPayment.Details.Normal)?.paymentRequest?.write(),
                    keysend_preimage = (outgoingPayment.details as? OutgoingPayment.Details.KeySend)?.preimage?.toByteArray(),
                    swapout_address = (outgoingPayment.details as? OutgoingPayment.Details.SwapOut)?.address,
                )
                outgoingPayment.parts.map {
                    outQueries.addOutgoingPart(
                        part_id = it.id.toString(),
                        parent_id = outgoingPayment.id.toString(),
                        amount_msat = it.amount.msat,
                        route = it.route,
                        created_at = it.createdAt)
                }
            }
        }
    }

    // ---- successful outgoing payment

    override suspend fun updateOutgoingPart(partId: UUID, preimage: ByteVector32, completedAt: Long) {
        withContext(Dispatchers.Default) {
            outQueries.transaction {
                outQueries.succeedOutgoingPart(part_id = partId.toString(), preimage = preimage.toByteArray(), completed_at = completedAt)
                if (outQueries.changes().executeAsOne() != 1L) throw OutgoingPaymentPartNotFound(partId)
            }
        }
    }

    override suspend fun updateOutgoingPayment(id: UUID, preimage: ByteVector32, completedAt: Long) {
        withContext(Dispatchers.Default) {
            outQueries.transaction {
                outQueries.succeedOutgoingPayment(id = id.toString(), preimage = preimage.toByteArray(), completed_at = completedAt)
                if (outQueries.changes().executeAsOne() != 1L) throw OutgoingPaymentPartNotFound(id)
            }
        }
    }

    // ---- fail outgoing payment

    override suspend fun updateOutgoingPart(partId: UUID, failure: Either<ChannelException, FailureMessage>, completedAt: Long) {
        withContext(Dispatchers.Default) {
            val f = OutgoingPaymentFailure.convertFailure(failure)
            outQueries.transaction {
                outQueries.failOutgoingPart(
                    part_id = partId.toString(),
                    err_code = f.remoteFailureCode?.toLong(),
                    err_message = f.details,
                    completed_at = completedAt)
                if (outQueries.changes().executeAsOne() != 1L) throw OutgoingPaymentPartNotFound(partId)
            }
        }
    }

    override suspend fun updateOutgoingPayment(id: UUID, failure: FinalFailure, completedAt: Long) {
        withContext(Dispatchers.Default) {
            outQueries.transaction {
                outQueries.failOutgoingPayment(id = id.toString(), final_failure = OutgoingFinalFailureDbEnum.toDb(failure), completed_at = completedAt)
                if (outQueries.changes().executeAsOne() != 1L) throw OutgoingPaymentNotFound(id)
            }
        }
    }

    // ---- get outgoing payment details

    override suspend fun getOutgoingPart(partId: UUID): OutgoingPayment? {
        return withContext(Dispatchers.Default) {
            outQueries.getOutgoingPart(part_id = partId.toString()).executeAsOneOrNull()?.run {
                outQueries.getOutgoingPayment(id = parent_id, ::mapOutgoingPayment).executeAsList()
            }?.run {
                groupByRawOutgoing(this).firstOrNull()
            }?.run {
                filterUselessParts(this)
                    // resulting payment must contain the request part id, or should be null
                    .takeIf { p -> p.parts.map { it.id }.contains(partId) }
            }
        }
    }

    override suspend fun getOutgoingPayment(id: UUID): OutgoingPayment? {
        return withContext(Dispatchers.Default) {
            outQueries.getOutgoingPayment(id = id.toString(), ::mapOutgoingPayment).executeAsList().run {
                groupByRawOutgoing(this).firstOrNull()
            }?.run {
                filterUselessParts(this)
            }
        }
    }

    // ---- list outgoing

    override suspend fun listOutgoingPayments(paymentHash: ByteVector32): List<OutgoingPayment> {
        return withContext(Dispatchers.Default) {
            outQueries.listOutgoingForPaymentHash(paymentHash.toByteArray(), ::mapOutgoingPayment).executeAsList()
                .run { groupByRawOutgoing(this) }
        }
    }

    @Deprecated("This method uses offset and has bad performances, use seek method instead when possible")
    override suspend fun listOutgoingPayments(count: Int, skip: Int, filters: Set<PaymentTypeFilter>): List<OutgoingPayment> {
        return withContext(Dispatchers.Default) {
            // LIMIT ?, ? : "the first expression is used as the OFFSET expression and the second as the LIMIT expression."
            outQueries.listOutgoingInOffset(skip.toLong(), count.toLong(), ::mapOutgoingPayment).executeAsList()
                .run { groupByRawOutgoing(this) }
        }
    }

    // ---- incoming payments

    override suspend fun addIncomingPayment(preimage: ByteVector32, origin: IncomingPayment.Origin, createdAt: Long) {
        withContext(Dispatchers.Default) {
            inQueries.insert(
                payment_hash = Crypto.sha256(preimage).toByteVector32().toByteArray(),
                preimage = preimage.toByteArray(),
                payment_type = IncomingOriginDbEnum.toDb(origin),
                payment_request = if (origin is IncomingPayment.Origin.Invoice) origin.paymentRequest.write() else null,
                swap_amount_msat = if (origin is IncomingPayment.Origin.SwapIn) origin.amount.msat else null,
                swap_address = if (origin is IncomingPayment.Origin.SwapIn) origin.address else null,
                created_at = createdAt
            )
        }
    }

    override suspend fun receivePayment(paymentHash: ByteVector32, amount: MilliSatoshi, receivedWith: IncomingPayment.ReceivedWith, receivedAt: Long) {
        if (amount == MilliSatoshi(0)) throw CannotReceiveZero
        withContext(Dispatchers.Default) {
            inQueries.transaction {
                inQueries.receive(
                    value = amount.msat,
                    received_at = receivedAt,
                    received_with = IncomingReceivedWithDbEnum.toDb(receivedWith),
                    received_with_fees = receivedWith.fees.msat,
                    received_with_channel_id = if (receivedWith is IncomingPayment.ReceivedWith.NewChannel) receivedWith.channelId?.toByteArray() else null,
                    payment_hash = paymentHash.toByteArray())
                if (inQueries.changes().executeAsOne() != 1L) throw IncomingPaymentNotFound(paymentHash)
            }
        }
    }

    override suspend fun getIncomingPayment(paymentHash: ByteVector32): IncomingPayment? {
        return withContext(Dispatchers.Default) {
            inQueries.get(payment_hash = paymentHash.toByteArray(), ::mapIncomingPayment).executeAsOneOrNull()
        }
    }

    override suspend fun listReceivedPayments(count: Int, skip: Int, filters: Set<PaymentTypeFilter>): List<IncomingPayment> {
        return withContext(Dispatchers.Default) {
            inQueries.list(skip.toLong(), count.toLong(), ::mapIncomingPayment).executeAsList()
        }
    }

    // ---- list ALL payments

    override suspend fun listPayments(count: Int, skip: Int, filters: Set<PaymentTypeFilter>): List<WalletPayment> {
        return withContext(Dispatchers.Default) {
            aggrQueries.listAllPayments(skip.toLong(), count.toLong(), ::allPaymentsMapper).executeAsList()
        }
    }

    // ---- mappers & utilities

    /** Group a list of outgoing payments by parent id and parts. */
    private fun groupByRawOutgoing(payments: List<OutgoingPayment>) = payments
        .takeIf { it.isNotEmpty() }
        ?.groupBy { it.id }
        ?.values
        ?.map { group -> group.first().copy(parts = group.flatMap { it.parts }) }
        ?: listOf()

    // if a payment is successful do not take into accounts failed/pending parts.
    private fun filterUselessParts(payment: OutgoingPayment): OutgoingPayment = when (payment.status) {
        is OutgoingPayment.Status.Succeeded -> payment.copy(parts = payment.parts.filter { it.status is OutgoingPayment.Part.Status.Succeeded })
        else -> payment
    }

    private fun mapOutgoingPayment(
        id: String,
        recipient_amount_msat: Long,
        recipient_node_id: String,
        payment_hash: ByteArray,
        created_at: Long,
        normal_payment_request: String?,
        keysend_preimage: ByteArray?,
        swapout_address: String?,
        final_failure: OutgoingFinalFailureDbEnum?,
        preimage: ByteArray?,
        completed_at: Long?,
        // part
        part_id: String?,
        amount_msat: Long?,
        route: List<HopDesc>?,
        part_created_at: Long?,
        part_preimage: ByteArray?,
        part_completed_at: Long?,
        err_code: Long?,
        err_message: String?
    ): OutgoingPayment {
        val part = if (part_id != null && amount_msat != null && route != null && part_created_at != null) {
            listOf(OutgoingPayment.Part(
                id = UUID.fromString(part_id),
                amount = MilliSatoshi(amount_msat),
                route = route,
                status = mapOutgoingPartStatus(part_preimage, err_code, err_message, part_completed_at),
                createdAt = part_created_at
            ))
        } else emptyList()

        return OutgoingPayment(
            id = UUID.fromString(id),
            recipientAmount = MilliSatoshi(recipient_amount_msat),
            recipient = PublicKey(ByteVector(recipient_node_id)),
            details = mapOutgoingDetails(ByteVector32(payment_hash), swapout_address, keysend_preimage, normal_payment_request),
            parts = part,
            status = mapOutgoingPaymentStatus(final_failure, preimage, completed_at)
        )
    }

    private fun mapOutgoingPaymentStatus(final_failure: OutgoingFinalFailureDbEnum?, preimage: ByteArray?, completed_at: Long?): OutgoingPayment.Status = when {
        preimage != null && completed_at != null -> OutgoingPayment.Status.Succeeded(ByteVector32(preimage), completed_at)
        final_failure != null && completed_at != null -> OutgoingPayment.Status.Failed(reason = mapFinalFailure(final_failure), completedAt = completed_at)
        else -> OutgoingPayment.Status.Pending
    }

    private fun mapOutgoingPartStatus(
        preimage: ByteArray?,
        errCode: Long?,
        errMessage: String?,
        completedAt: Long?
    ) = when {
        preimage != null && completedAt != null -> OutgoingPayment.Part.Status.Succeeded(ByteVector32(preimage), completedAt)
        errMessage != null && completedAt != null -> OutgoingPayment.Part.Status.Failed(errCode?.toInt(), errMessage, completedAt)
        else -> OutgoingPayment.Part.Status.Pending
    }

    private fun mapOutgoingDetails(paymentHash: ByteVector32, swapoutAddress: String?, keysendPreimage: ByteArray?, normalPaymentRequest: String?): OutgoingPayment.Details = when {
        swapoutAddress != null -> OutgoingPayment.Details.SwapOut(address = swapoutAddress, paymentHash = ByteVector32(paymentHash))
        keysendPreimage != null -> OutgoingPayment.Details.KeySend(preimage = ByteVector32(keysendPreimage))
        normalPaymentRequest != null -> OutgoingPayment.Details.Normal(paymentRequest = PaymentRequest.read(normalPaymentRequest))
        else -> throw UnhandledOutgoingDetails
    }

    private fun mapFinalFailure(finalFailure: OutgoingFinalFailureDbEnum): FinalFailure = when (finalFailure) {
        OutgoingFinalFailureDbEnum.InvalidPaymentAmount -> FinalFailure.InvalidPaymentAmount
        OutgoingFinalFailureDbEnum.InsufficientBalance -> FinalFailure.InsufficientBalance
        OutgoingFinalFailureDbEnum.InvalidPaymentId -> FinalFailure.InvalidPaymentId
        OutgoingFinalFailureDbEnum.NoAvailableChannels -> FinalFailure.NoAvailableChannels
        OutgoingFinalFailureDbEnum.NoRouteToRecipient -> FinalFailure.NoRouteToRecipient
        OutgoingFinalFailureDbEnum.RetryExhausted -> FinalFailure.RetryExhausted
        OutgoingFinalFailureDbEnum.UnknownError -> FinalFailure.UnknownError
        OutgoingFinalFailureDbEnum.WalletRestarted -> FinalFailure.WalletRestarted
        OutgoingFinalFailureDbEnum.RecipientUnreachable -> FinalFailure.RecipientUnreachable
    }

    private fun mapIncomingPayment(
        payment_hash: ByteArray,
        created_at: Long,
        preimage: ByteArray,
        payment_type: IncomingOriginDbEnum,
        payment_request: String?,
        swap_amount_msat: Long?,
        swap_address: String?,
        received_amount_msat: Long?,
        received_at: Long?,
        received_with: IncomingReceivedWithDbEnum?,
        received_with_fees: Long?,
        received_with_channel_id: ByteArray?
    ): IncomingPayment {
        return IncomingPayment(
            preimage = ByteVector32(preimage),
            origin = mapIncomingOrigin(payment_type, swap_amount_msat, swap_address, payment_request),
            received = mapIncomingReceived(received_amount_msat, received_at, received_with, received_with_fees, received_with_channel_id),
            createdAt = created_at
        )
    }

    private fun mapIncomingOrigin(origin: IncomingOriginDbEnum, swapAmount: Long?, swapAddress: String?, paymentRequest: String?) = when {
        origin == IncomingOriginDbEnum.KeySend -> IncomingPayment.Origin.KeySend
        origin == IncomingOriginDbEnum.SwapIn && swapAmount != null && swapAddress != null -> IncomingPayment.Origin.SwapIn(MilliSatoshi(swapAmount), swapAddress, null)
        origin == IncomingOriginDbEnum.Invoice && paymentRequest != null -> IncomingPayment.Origin.Invoice(PaymentRequest.read(paymentRequest))
        else -> throw UnreadableIncomingOriginInDatabase(origin, swapAmount, swapAddress, paymentRequest)
    }

    private fun mapIncomingReceived(amount: Long?, receivedAt: Long?, receivedWithEnum: IncomingReceivedWithDbEnum?, receivedWithAmount: Long?, receivedWithChannelId: ByteArray?): IncomingPayment.Received? {
        return when {
            amount == null && receivedAt == null && receivedWithEnum == null -> null
            amount != null && receivedAt != null && receivedWithEnum == IncomingReceivedWithDbEnum.LightningPayment ->
                IncomingPayment.Received(MilliSatoshi(amount), IncomingPayment.ReceivedWith.LightningPayment, receivedAt)
            amount != null && receivedAt != null && receivedWithEnum == IncomingReceivedWithDbEnum.NewChannel && receivedWithAmount != null ->
                IncomingPayment.Received(MilliSatoshi(amount), IncomingPayment.ReceivedWith.NewChannel(MilliSatoshi(receivedWithAmount), receivedWithChannelId?.run { ByteVector32(this) }), receivedAt)
            else -> throw UnreadableIncomingPaymentStatusInDatabase(amount, receivedAt, receivedWithEnum, receivedWithAmount, receivedWithChannelId)
        }
    }

    private fun allPaymentsMapper(
        direction: String,
        outgoing_payment_id: String?,
        payment_hash: ByteArray,
        preimage: ByteArray?,
        parts_amount: Long?,
        amount: Long,
        outgoing_recipient: String?,
        outgoing_normal_payment_request: String?,
        outgoing_keysend_preimage: ByteArray?,
        outgoing_swapout_address: String?,
        outgoing_failure: OutgoingFinalFailureDbEnum?,
        incoming_payment_type: IncomingOriginDbEnum?,
        incoming_payment_request: String?,
        incoming_swap_address: String?,
        incoming_received_with: IncomingReceivedWithDbEnum?,
        incoming_received_with_fees: Long?,
        created_at: Long,
        completed_at: Long?
    ): WalletPayment = when (direction.toLowerCase()) {
        "outgoing" -> OutgoingPayment(
            id = UUID.fromString(outgoing_payment_id!!),
            recipientAmount = parts_amount?.let { MilliSatoshi(it) } ?: MilliSatoshi(amount), //  when possible, prefer using sum of parts' amounts instead of recipient amount
            recipient = PublicKey(ByteVector(outgoing_recipient!!)),
            details = mapOutgoingDetails(ByteVector32(payment_hash), outgoing_swapout_address, outgoing_keysend_preimage, outgoing_normal_payment_request),
            parts = listOf(),
            status = when {
                outgoing_failure != null && completed_at != null -> OutgoingPayment.Status.Failed(reason = mapFinalFailure(outgoing_failure), completedAt = completed_at)
                preimage != null && completed_at != null -> OutgoingPayment.Status.Succeeded(preimage = ByteVector32(preimage), completedAt = completed_at)
                else -> OutgoingPayment.Status.Pending
            }
        )
        "incoming" -> mapIncomingPayment(payment_hash, created_at, preimage!!, incoming_payment_type!!, incoming_payment_request, 0L, incoming_swap_address,
            amount, completed_at, incoming_received_with, incoming_received_with_fees, null)
        else -> throw UnhandledDirection(direction)
    }
}

class UnreadableIncomingPaymentStatusInDatabase(
    amount: Long?,
    receivedAt: Long?,
    receivedWithEnum: SqlitePaymentsDb.IncomingReceivedWithDbEnum?,
    receivedWithAmount: Long?,
    receivedWithChannelId: ByteArray?
) : RuntimeException("unreadable data [ amount=$amount, receivedAt=$receivedAt, receivedWithEnum=$receivedWithEnum, receivedWithAmount=$receivedWithAmount, receivedWithChannelId=${receivedWithChannelId?.toHexString()} ]")

class UnreadableIncomingOriginInDatabase(
    origin: SqlitePaymentsDb.IncomingOriginDbEnum, swapAmount: Long?, swapAddress: String?, paymentRequest: String?
) : RuntimeException("unreadable data [ origin=$origin, swapAmount=$swapAmount, swapAddress=$swapAddress, paymentRequest=$paymentRequest ]")

object CannotReceiveZero : RuntimeException()
class OutgoingPaymentNotFound(id: UUID) : RuntimeException("could not find outgoing payment with id=$id")
class OutgoingPaymentPartNotFound(partId: UUID) : RuntimeException("could not find outgoing payment part with part_id=$partId")
class IncomingPaymentNotFound(paymentHash: ByteVector32) : RuntimeException("missing payment for payment_hash=$paymentHash")
class UnhandledIncomingOrigin(val origin: IncomingPayment.Origin) : RuntimeException("unhandled origin=$origin")
class UnhandledIncomingReceivedWith(receivedWith: IncomingPayment.ReceivedWith) : RuntimeException("unhandled receivedWith=$receivedWith")
object UnhandledOutgoingDetails : RuntimeException("unhandled outgoing details")
class UnhandledDirection(direction: String) : RuntimeException("unhandled direction=$direction")