/*
 * Copyright 2022 ACINQ SAS
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

package fr.acinq.phoenix.android.utils

import android.content.Context
import fr.acinq.bitcoin.ByteVector32
import fr.acinq.bitcoin.PublicKey
import fr.acinq.bitcoin.byteVector32
import fr.acinq.eclair.db.IncomingPaymentStatus
import fr.acinq.eclair.db.OutgoingPaymentStatus
import fr.acinq.eclair.db.sqlite.SqlitePaymentsDb
import fr.acinq.eclair.db.sqlite.SqliteUtils
import fr.acinq.eclair.wire.TemporaryNodeFailure
import fr.acinq.eclair.wire.TrampolineFeeInsufficient
import fr.acinq.eclair.wire.UnknownNextPeer
import fr.acinq.lightning.Lightning
import fr.acinq.lightning.db.ChannelClosingType
import fr.acinq.lightning.db.IncomingPayment
import fr.acinq.lightning.db.OutgoingPayment
import fr.acinq.lightning.payment.FinalFailure
import fr.acinq.lightning.payment.PaymentRequest
import fr.acinq.lightning.utils.UUID
import fr.acinq.lightning.utils.msat
import fr.acinq.lightning.utils.sat
import fr.acinq.lightning.utils.toMilliSatoshi
import fr.acinq.phoenix.PhoenixBusiness
import fr.acinq.phoenix.data.WalletPaymentId
import fr.acinq.phoenix.legacy.db.*
import fr.acinq.phoenix.legacy.utils.Wallet
import org.slf4j.LoggerFactory
import scala.collection.JavaConversions
import scala.collection.JavaConverters
import java.io.File

object LegacyMigrationHelper {

    val log = LoggerFactory.getLogger(this::class.java)

    suspend fun migrateLegacyPayments(
        context: Context,
        business: PhoenixBusiness
    ) {
        val eclairDbFile = Wallet.getEclairDBFile(context)
        eclairDbFile.copyTo(File(Wallet.getChainDatadir(context), "eclairdb-migration.sqlite"), overwrite = true)
        log.info("legacy database file has been copied")

        val legacyMetaRepository = PaymentMetaRepository.getInstance(AppDb.getInstance(context).paymentMetaQueries)
        val legacyPayToOpenMetaRepository = PayToOpenMetaRepository.getInstance(AppDb.getInstance(context).payToOpenMetaQueries)
        val legacyPaymentsDb = SqlitePaymentsDb(SqliteUtils.openSqliteFile(Wallet.getChainDatadir(context), "eclairdb-migration.sqlite", true, "wal", "normal"))
        log.info("opened legacy payments db")

        val newPaymentsDb = business.databaseManager.paymentsDb()

        val outgoing = JavaConversions.asJavaCollection(legacyPaymentsDb.listAllOutgoingPayments()).toList().groupBy { it.parentId() }
        log.info("migrating ${outgoing.size} outgoing payments")
        outgoing.forEach {
            try {
                saveOutgoingLegacyPayment(it.value, newPaymentsDb, legacyMetaRepository)
            } catch (e: Exception) {
                log.error("payment migration: failed to save payment=$it", e)
            }
        }

        val incoming = JavaConversions.asJavaCollection(legacyPaymentsDb.listAllIncomingPayments()).toList()
        log.info("migrating ${incoming.size} incoming payments")
        incoming.forEach {
            try {
                saveIncomingLegacyPayment(it, newPaymentsDb, legacyMetaRepository, legacyPayToOpenMetaRepository)
            } catch (e: Exception) {
                log.error("payment migration: failed to save payment=$it", e)
            }
        }

        log.info("moving legacy database to finalize migration...")
        // eclairDbFile.renameTo(File(Wallet.getChainDatadir(context), "eclair-migrated.sqlite"))
        log.info("legacy database has been moved")
    }

    private suspend fun saveIncomingLegacyPayment(
        payment: fr.acinq.eclair.db.IncomingPayment,
        newPaymentsDb: fr.acinq.phoenix.db.SqlitePaymentsDb,
        legacyMetaRepository: PaymentMetaRepository,
        legacyPayToOpenMetaRepository: PayToOpenMetaRepository,
    ) {
        val status = payment.status()
        if (status !is IncomingPaymentStatus.Received) {
            log.debug("ignore incoming pending payment=$payment")
        } else {
            val paymentHash = payment.paymentRequest().paymentHash().bytes().toArray().byteVector32()
            val paymentMeta = legacyMetaRepository.get(paymentHash.toHex())
            val payToOpenMeta = legacyPayToOpenMetaRepository.get(paymentHash.toHex())

            // get details
            val origin: IncomingPayment.Origin = if (paymentMeta?.swap_in_address != null) {
                IncomingPayment.Origin.SwapIn(address = paymentMeta.swap_in_address)
            } else {
                IncomingPayment.Origin.Invoice(
                    paymentRequest = PaymentRequest.read(fr.acinq.eclair.payment.PaymentRequest.write(payment.paymentRequest()))
                )
            }

            // use the PayToOpen metadata to know how the payment was received
            val receivedWith = if (payToOpenMeta != null) {
                IncomingPayment.ReceivedWith.NewChannel(
                    amount = status.amount().toLong().msat,
                    fees = payToOpenMeta.fee_sat.sat.toMilliSatoshi(),
                    channelId = null
                )
            } else {
                IncomingPayment.ReceivedWith.LightningPayment(
                    amount = status.amount().toLong().msat,
                    channelId = ByteVector32.Zeroes,
                    htlcId = 0L
                )
            }

            // save the payment as received
            newPaymentsDb.addAndReceivePayment(
                preimage = payment.paymentPreimage().bytes().toArray().byteVector32(),
                origin = origin,
                receivedWith = setOf(receivedWith),
                createdAt = payment.createdAt(),
                receivedAt = status.receivedAt()
            )
        }
    }

    private suspend fun saveOutgoingLegacyPayment(
        payments: List<fr.acinq.eclair.db.OutgoingPayment>,
        newPaymentsDb: fr.acinq.phoenix.db.SqlitePaymentsDb,
        legacyMetaRepository: PaymentMetaRepository,
    ) {
        val head = payments.first()
        val id = UUID.fromString(payments.first().parentId().toString())
        val paymentMeta = legacyMetaRepository.get(payments.first().parentId().toString())

        // retrieve details from the first payment in the list
        val details = if (paymentMeta?.swap_out_address != null) {
            OutgoingPayment.Details.SwapOut(
                address = paymentMeta.swap_out_address ?: "",
                // FIXME: we use a random payment hash
                paymentHash = Lightning.randomBytes32().sha256()
            )
        } else if (head.paymentType() == "ClosingChannel") {
            OutgoingPayment.Details.ChannelClosing(
                channelId = paymentMeta?.closing_channel_id?.let { ByteVector32.fromValidHex(it) } ?: Lightning.randomBytes32().sha256(),
                closingAddress = paymentMeta?.closing_main_output_script ?: "",
                isSentToDefaultAddress = paymentMeta?.closing_type != ClosingType.Mutual.code
            )
        } else if (head.paymentRequest().isDefined) {
            OutgoingPayment.Details.Normal(
                paymentRequest = PaymentRequest.read(fr.acinq.eclair.payment.PaymentRequest.write(head.paymentRequest().get()))
            )
        } else {
            OutgoingPayment.Details.KeySend(preimage = Lightning.randomBytes32().sha256())
        }

        val parts = payments.map { part ->
            val (fees, status) = when (val partStatus = part.status()) {
                is OutgoingPaymentStatus.Succeeded -> {
                    partStatus.feesPaid().toLong().msat to OutgoingPayment.Part.Status.Succeeded(
                        preimage = partStatus.paymentPreimage().bytes().toArray().byteVector32(),
                        completedAt = partStatus.completedAt()
                    )
                }
                is OutgoingPaymentStatus.Failed -> {
                    val lastFailure = JavaConverters.asJavaCollectionConverter(partStatus.failures()).asJavaCollection().toList().lastOrNull()
                    0.msat to OutgoingPayment.Part.Status.Failed(
                        remoteFailureCode = null,
                        details = lastFailure?.failureMessage() ?: "error details unavailable",
                        completedAt = partStatus.completedAt()
                    )
                }
                else -> {
                    0.msat to OutgoingPayment.Part.Status.Pending
                }
            }
            OutgoingPayment.Part(
                id = UUID.fromString(part.id().toString()),
                amount = part.amount().toLong().msat + fees, // must include the fee!!!
                route = listOf(),
                status = status,
                createdAt = part.createdAt()
            )
        }

        // save status
        val status: OutgoingPayment.Status = when {

            payments.any { p -> p.status() is OutgoingPaymentStatus.`Pending$` } -> {
                // pending is the default status of the payment
                OutgoingPayment.Status.Pending
            }

            payments.any { p -> p.status() is OutgoingPaymentStatus.Succeeded } -> {
                val statuses = payments.map { it.status() }.filterIsInstance<OutgoingPaymentStatus.Succeeded>()
                if (paymentMeta?.swap_out_address != null && paymentMeta.swap_out_fee_sat != null && paymentMeta.swap_out_feerate_per_byte != null) {
                    // successful swap-out
                    OutgoingPayment.Status.Completed.Succeeded.OnChain(
                        txids = paymentMeta.swap_out_tx?.encodeToByteArray()?.byteVector32()?.let { listOf(it) } ?: emptyList(),
                        claimed = 0.sat,
                        closingType = ChannelClosingType.Mutual,
                        completedAt = statuses.first().completedAt()
                    )
                } else if (head.paymentType() == "ClosingChannel") {
                    // successful channel closing
                    OutgoingPayment.Status.Completed.Succeeded.OnChain(
                        txids = paymentMeta?.getSpendingTxs()?.map { ByteVector32.fromValidHex(it) } ?: emptyList(),
                        claimed = 0.sat,
                        closingType = when (paymentMeta?.closing_type) {
                            ClosingType.Mutual.code -> ChannelClosingType.Mutual
                            ClosingType.Local.code -> ChannelClosingType.Local
                            ClosingType.Remote.code -> ChannelClosingType.Remote
                            else -> ChannelClosingType.Other
                        },
                        completedAt = statuses.first().completedAt()
                    )
                } else {
                    // successful lightning payment
                    OutgoingPayment.Status.Completed.Succeeded.OffChain(
                        preimage = statuses.first().paymentPreimage().bytes().toArray().byteVector32(),
                        completedAt = statuses.first().completedAt()
                    )
                }

//                    val totalSent = parts.map { p -> p.amount().toLong().msat + (p.status() as OutgoingPaymentStatus.Succeeded).feesPaid().toLong().msat }
//                    val totalFees = totalSent - payments.first().recipientAmount().toLong().msat
//                    val completedAt = parts.map { p -> p.status() as OutgoingPaymentStatus.Succeeded }.maxOf { s -> s.completedAt() }
//                    val head = it.first()
//                    if (paymentMeta?.swap_out_address != null && paymentMeta.swap_out_fee_sat != null && paymentMeta.swap_out_feerate_per_byte != null) {
//                        val feeSwapOut = Satoshi(paymentMeta.swap_out_fee_sat)
//
//                        state.postValue(
//                            PaymentDetailsState.Outgoing.Sent.SwapOut(head.paymentType(), it, descSwapOut, amountToRecipient.`$minus`(feeSwapOut), Converter.any2Msat(feeSwapOut), completedAt,
//                                paymentMeta.swap_out_feerate_per_byte))
//                    } else if (head.paymentType() == "ClosingChannel" || (head.paymentRequest().isEmpty && head.externalId().isDefined && head.externalId().get().startsWith("closing-"))) {
//                        val descClosing = appContext.getString(R.string.paymentdetails_closing_desc, paymentMeta?.closing_channel_id?.take(10) ?: "")
//                        state.postValue(PaymentDetailsState.Outgoing.Sent.Closing(head.paymentType(), it, descClosing, amountToRecipient, fees, completedAt))
//                    } else {
//                        state.postValue(PaymentDetailsState.Outgoing.Sent.Normal(head.paymentType(), it, description, amountToRecipient, fees, completedAt))
//                    }
//                }
            }

            payments.any { p -> p.status() is OutgoingPaymentStatus.Failed } -> {
                // attempt to translate the old failure type to a kmp `FinalFailure`
                val (finalFailure, completedAt) = payments.filter { p -> p.status() is OutgoingPaymentStatus.Failed }.let {
                    val lastFailure = JavaConverters.asJavaCollectionConverter((it.last().status() as OutgoingPaymentStatus.Failed).failures()).asJavaCollection().toList().lastOrNull()
                    when {
                        lastFailure == null -> FinalFailure.UnknownError
                        lastFailure.failureMessage().contains(TrampolineFeeInsufficient.message(), ignoreCase = true) -> FinalFailure.NoRouteToRecipient
                        lastFailure.failureMessage().contains(TemporaryNodeFailure.message(), ignoreCase = true)
                                || lastFailure.failureMessage().contains(UnknownNextPeer.message(), ignoreCase = true)
                                || lastFailure.failureMessage().contains("is currently unavailable", ignoreCase = true) -> FinalFailure.RecipientUnreachable
                        lastFailure.failureMessage().contains("incorrect payment details or unknown payment hash", ignoreCase = true) -> FinalFailure.NoRouteToRecipient
                        else -> FinalFailure.UnknownError
                    } to (it.last().status() as OutgoingPaymentStatus.Failed).completedAt()
                }
                OutgoingPayment.Status.Completed.Failed(reason = finalFailure, completedAt = completedAt)
            }

            else -> OutgoingPayment.Status.Pending
        }

        // add payment to db (without status)
        newPaymentsDb.addOutgoingPayment(
            OutgoingPayment(
                id = id,
                recipientAmount = head.recipientAmount().toLong().msat,
                recipient = PublicKey.fromHex(head.recipientNodeId().toUncompressedBin().toHex()),
                details = details,
                parts = parts,
                status = status,
                createdAt = head.createdAt()
            )
        )

        // save metadata
        if (paymentMeta?.custom_desc != null) {
            newPaymentsDb.updateMetadata(
                id = WalletPaymentId.OutgoingPaymentId(id),
                userDescription = paymentMeta.custom_desc,
                userNotes = null
            )
        }


//        when (val oldStatus = head.status()) {
//            is OutgoingPaymentStatus.Failed -> {
//                newPaymentsDb.completeOutgoingPayment(
//                    id = id,
//                    completed = OutgoingPayment.Status.Completed.Failed(reason = FinalFailure.UnknownError, completedAt = oldStatus.completedAt())
//                )
//            }
//            is OutgoingPaymentStatus.Succeeded -> {
//                newPaymentsDb.completeOutgoingPayment(
//                    id = id,
//                    completed = if (paymentMeta?.swap_out_address != null) {
//                        // FIXME: we need a specific type for successful swap-outs
//                        OutgoingPayment.Status.Completed.Succeeded.OnChain(
//                            txids = emptyList(),
//                            claimed = 0.sat,
//                            closingType = ChannelClosingType.Mutual,
//                            completedAt = oldStatus.completedAt()
//                        )
//                    } else {
//                        OutgoingPayment.Status.Completed.Succeeded.OffChain(
//                            preimage = ByteVector32.fromValidHex(oldStatus.paymentPreimage().bytes().toHex()),
//                            completedAt = oldStatus.completedAt()
//                        )
//                    }
//                )
//            }
//        }
    }
}