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
import com.google.common.net.HostAndPort
import fr.acinq.bitcoin.ByteVector32
import fr.acinq.bitcoin.PublicKey
import fr.acinq.bitcoin.Satoshi
import fr.acinq.bitcoin.byteVector32
import fr.acinq.eclair.db.IncomingPaymentStatus
import fr.acinq.eclair.db.OutgoingPaymentStatus
import fr.acinq.eclair.db.PaymentType
import fr.acinq.eclair.db.sqlite.SqlitePaymentsDb
import fr.acinq.eclair.db.sqlite.SqliteUtils
import fr.acinq.eclair.wire.TemporaryNodeFailure
import fr.acinq.eclair.wire.TrampolineFeeInsufficient
import fr.acinq.eclair.wire.UnknownNextPeer
import fr.acinq.lightning.*
import fr.acinq.lightning.db.ChannelClosingType
import fr.acinq.lightning.db.HopDesc
import fr.acinq.lightning.db.IncomingPayment
import fr.acinq.lightning.db.OutgoingPayment
import fr.acinq.lightning.io.TcpSocket
import fr.acinq.lightning.payment.FinalFailure
import fr.acinq.lightning.payment.PaymentRequest
import fr.acinq.lightning.utils.*
import fr.acinq.phoenix.android.PhoenixApplication
import fr.acinq.phoenix.android.utils.datastore.HomeAmountDisplayMode
import fr.acinq.phoenix.android.utils.datastore.InternalData
import fr.acinq.phoenix.android.utils.datastore.UserPrefs
import fr.acinq.phoenix.data.BitcoinUnit
import fr.acinq.phoenix.data.FiatCurrency
import fr.acinq.phoenix.data.WalletPaymentId
import fr.acinq.phoenix.legacy.db.*
import fr.acinq.phoenix.legacy.utils.Prefs
import fr.acinq.phoenix.legacy.utils.ThemeHelper
import fr.acinq.phoenix.legacy.utils.Wallet
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import scala.collection.JavaConversions
import scala.collection.JavaConverters
import scala.collection.Seq
import java.io.File

object LegacyMigrationHelper {

    private val log: Logger = LoggerFactory.getLogger(this::class.java)

    /** Import the legacy app's preferences into the new app's datastores. */
    suspend fun migrateLegacyPreferences(context: Context) {
        log.info("started migrating legacy user preferences")

        // -- utils

        InternalData.saveLastUsedAppCode(context, Prefs.getLastVersionUsed(context))
        InternalData.saveMnemonicsCheckTimestamp(context, Prefs.getMnemonicsSeenTimestamp(context))
        Prefs.getFCMToken(context)?.let { InternalData.saveFcmToken(context, it) }
        InternalData.saveShowIntro(context, Prefs.showFTUE(context))

        // -- display

        UserPrefs.saveUserTheme(
            context, when (Prefs.getTheme(context)) {
                ThemeHelper.darkMode -> UserTheme.DARK
                ThemeHelper.lightMode -> UserTheme.LIGHT
                else -> UserTheme.SYSTEM
            }
        )
        UserPrefs.saveBitcoinUnit(
            context, when (Prefs.getCoinUnit(context).code()) {
                "sat" -> BitcoinUnit.Sat
                "bits" -> BitcoinUnit.Bit
                "mbtc" -> BitcoinUnit.MBtc
                else -> BitcoinUnit.Btc
            }
        )
        UserPrefs.saveHomeAmountDisplayMode(context, if (Prefs.showBalanceHome(context)) HomeAmountDisplayMode.BTC else HomeAmountDisplayMode.REDACTED)
        UserPrefs.saveIsAmountInFiat(context, Prefs.getShowAmountInFiat(context))
        UserPrefs.saveFiatCurrency(context, FiatCurrency.valueOfOrNull(Prefs.getFiatCurrency(context)) ?: FiatCurrency.USD)

        // -- security

        UserPrefs.saveIsScreenLockActive(context, Prefs.isScreenLocked(context))

        // -- electrum

        UserPrefs.saveElectrumServer(context, Prefs.getElectrumServer(context).takeIf { it.isNotBlank() }?.let {
            val hostPort = HostAndPort.fromString(it).withDefaultPort(50002)
            // TODO: handle onion addresses and TOR
            ServerAddress(hostPort.host, hostPort.port, TcpSocket.TLS.TRUSTED_CERTIFICATES)
        })

        // -- payment settings

        UserPrefs.saveInvoiceDefaultDesc(context, Prefs.getDefaultPaymentDescription(context))
        UserPrefs.saveInvoiceDefaultExpiry(context, Prefs.getPaymentsExpirySeconds(context))

        Prefs.getMaxTrampolineCustomFee(context)?.let {
            TrampolineFees(feeBase = Satoshi(it.feeBase.toLong()), feeProportional = it.feeProportionalMillionths, cltvExpiryDelta = CltvExpiryDelta(it.cltvExpiry.toInt()))
        }?.let {
            UserPrefs.saveTrampolineMaxFee(context, it)
        }

        UserPrefs.saveIsAutoPayToOpenEnabled(context, Prefs.isAutoPayToOpenEnabled(context))
        log.info("finished migration of legacy user preferences")
    }

    suspend fun migrateLegacyPayments(
        context: Context,
    ) {
        val eclairDbBackupFile = File(Wallet.getChainDatadir(context), "eclair.sqlite.bak")
        if (!eclairDbBackupFile.exists()) {
            log.info("no legacy database backup file found, no migration needed.")
            return
        }

        // 1 - create a copy of the eclair database backup file we can safely work on
        eclairDbBackupFile.copyTo(File(Wallet.getChainDatadir(context), "eclair-migration.sqlite"), overwrite = true)
        log.info("legacy database backup file has been copied")

        val legacyMetaRepository = PaymentMetaRepository.getInstance(AppDb.getInstance(context).paymentMetaQueries)
        val legacyPayToOpenMetaRepository = PayToOpenMetaRepository.getInstance(AppDb.getInstance(context).payToOpenMetaQueries)
        val legacyPaymentsDb = SqlitePaymentsDb(SqliteUtils.openSqliteFile(Wallet.getChainDatadir(context), "eclair-migration.sqlite", true, "wal", "normal"))
        log.info("opened legacy payments db")

        // 2 - get the new payments database
        val business = (context as PhoenixApplication).business
        val newPaymentsDb = business.databaseManager.paymentsDb()

        // 3 - extract all outgoing payments from the legacy database, and save them to the new database
        val outgoing = groupLegacyOutgoingPayments(legacyPaymentsDb.listAllOutgoingPayments())
        log.info("migrating ${outgoing.size} outgoing payments")
        outgoing.forEach {
            try {
                val parentId = it.key
                val paymentMeta = legacyMetaRepository.get(parentId.toString())
                val payment = modernizeLegacyOutgoingPayment(business.chain.chainHash, parentId, it.value, paymentMeta)

                // save payment to database
                newPaymentsDb.addOutgoingPayment(payment)
                // status must be updated separately!
                when (val status = payment.status) {
                    is OutgoingPayment.Status.Completed.Succeeded.OffChain -> {
                        newPaymentsDb.completeOutgoingPaymentOffchain(
                            id = payment.id,
                            preimage = status.preimage,
                            completedAt = status.completedAt
                        )
                    }
                    is OutgoingPayment.Status.Completed.Succeeded.OnChain -> {
                        newPaymentsDb.completeOutgoingPaymentForClosing(
                            id = payment.id,
                            parts = emptyList(), // closing tx parts have already been inserted
                            completedAt = status.completedAt
                        )
                    }
                    is OutgoingPayment.Status.Completed.Failed -> {
                        newPaymentsDb.completeOutgoingPaymentOffchain(
                            id = payment.id,
                            finalFailure = status.reason,
                            completedAt = status.completedAt
                        )
                    }
                    OutgoingPayment.Status.Pending -> {
                        // no need to update the DB as this is the default status
                    }
                }
                payment.parts.filterIsInstance<OutgoingPayment.LightningPart>().forEach { part ->
                    when (val status = part.status) {
                        is OutgoingPayment.LightningPart.Status.Succeeded -> newPaymentsDb.completeOutgoingLightningPart(part.id, status.preimage, status.completedAt)
                        is OutgoingPayment.LightningPart.Status.Failed -> newPaymentsDb.completeOutgoingLightningPartLegacy(part.id, status, status.completedAt)
                        OutgoingPayment.LightningPart.Status.Pending -> {}
                    }
                }
                log.debug("migrated outgoing payment=$payment")

                // save metadata
                if (paymentMeta?.custom_desc != null) {
                    newPaymentsDb.updateMetadata(
                        id = WalletPaymentId.OutgoingPaymentId(parentId),
                        userDescription = paymentMeta.custom_desc,
                        userNotes = null
                    )
                    log.debug("saved custom desc=${paymentMeta.custom_desc} for payment=$parentId")
                }
                log.info("successfully migrated ${outgoing.size} outgoing payments")
            } catch (e: Exception) {
                log.error("payment migration: failed to save payment=$it: ", e)
            }
        }

        // 4 - extract all incoming payments from the legacy database, and save them to the new database
        val incoming = JavaConversions.asJavaCollection(legacyPaymentsDb.listAllIncomingPayments()).toList()
        log.info("migrating ${incoming.size} incoming payments")
        incoming.forEach {
            try {
                val paymentHash = it.paymentRequest().paymentHash().bytes().toHex()
                val paymentMeta = legacyMetaRepository.get(paymentHash)
                val payment = modernizeLegacyIncomingPayment(
                    paymentMeta = legacyMetaRepository.get(paymentHash),
                    payToOpenMeta = legacyPayToOpenMetaRepository.get(paymentHash),
                    payment = it
                )
                if (payment?.received != null) {
                    newPaymentsDb.addAndReceivePayment(
                        preimage = payment.preimage,
                        origin = payment.origin,
                        receivedWith = payment.received!!.receivedWith,
                        createdAt = it.createdAt(),
                        receivedAt = payment.received!!.receivedAt
                    )
                    log.debug("migrated incoming payment=$payment")
                }
                // save metadata
                if (paymentMeta?.custom_desc != null) {
                    newPaymentsDb.updateMetadata(
                        id = WalletPaymentId.IncomingPaymentId(paymentHash = ByteVector32.fromValidHex(paymentHash)),
                        userDescription = paymentMeta.custom_desc,
                        userNotes = null
                    )
                    log.debug("saved custom desc=${paymentMeta.custom_desc} for payment=$paymentHash")
                }
                log.info("successfully migrated ${incoming.size} incoming payments")
            } catch (e: Exception) {
                log.error("payment migration: failed to save payment=$it: ", e)
            }
        }

        log.info("moving eclair.sqlite legacy database to finalize migration...")
        legacyPaymentsDb.close()
        // move the db backup file so that when a migration successfully completes, the process will not repeat
        eclairDbBackupFile.renameTo(File(Wallet.getChainDatadir(context), "eclair.sqlite.bak.migrated"))
    }

    fun modernizeLegacyIncomingPayment(
        paymentMeta: PaymentMeta?,
        payToOpenMeta: PayToOpenMeta?,
        payment: fr.acinq.eclair.db.IncomingPayment,
    ): IncomingPayment? {
        val status = payment.status()
        return if (status !is IncomingPaymentStatus.Received) {
            log.debug("ignore incoming pending payment=$payment")
            null
        } else {
            // get details
            val origin: IncomingPayment.Origin = if (payment.paymentType() == "SwapIn") {
                if (paymentMeta?.swap_in_address != null) {
                    IncomingPayment.Origin.SwapIn(address = paymentMeta.swap_in_address)
                } else {
                    IncomingPayment.Origin.SwapIn(address = "")
                }
            } else {
                IncomingPayment.Origin.Invoice(
                    paymentRequest = PaymentRequest.read(fr.acinq.eclair.payment.PaymentRequest.write(payment.paymentRequest()))
                )
            }

            // use the PayToOpen metadata to know how the payment was received
            val receivedWith = if (payToOpenMeta != null || payment.paymentType() == PaymentType.SwapIn()) {
                IncomingPayment.ReceivedWith.NewChannel(
                    id = UUID.randomUUID(),
                    amount = status.amount().toLong().msat,
                    serviceFee = payToOpenMeta?.fee_sat?.sat?.toMilliSatoshi() ?: 0.msat,
                    fundingFee = 0.sat,
                    channelId = ByteVector32.Zeroes
                )
            } else {
                IncomingPayment.ReceivedWith.LightningPayment(
                    amount = status.amount().toLong().msat,
                    channelId = ByteVector32.Zeroes,
                    htlcId = 0L
                )
            }

            IncomingPayment(
                preimage = payment.paymentPreimage().bytes().toArray().byteVector32(),
                origin = origin,
                received = IncomingPayment.Received(setOf(receivedWith), status.receivedAt()),
                createdAt = payment.createdAt()
            )
        }
    }

    fun groupLegacyOutgoingPayments(payments: Seq<fr.acinq.eclair.db.OutgoingPayment>): Map<UUID, List<fr.acinq.eclair.db.OutgoingPayment>> =
        JavaConversions.asJavaCollection(payments).toList().groupBy { UUID.fromString(it.parentId().toString()) }

    fun modernizeLegacyOutgoingPayment(
        chainHash: ByteVector32,
        parentId: UUID,
        listOfParts: List<fr.acinq.eclair.db.OutgoingPayment>,
        paymentMeta: PaymentMeta?,
    ): OutgoingPayment {
        val head = listOfParts.first()
        val paymentRequest = if (head.paymentRequest().isDefined) {
            PaymentRequest.read(fr.acinq.eclair.payment.PaymentRequest.write(head.paymentRequest().get()))
        } else null

        // retrieve details from the first payment in the list
        val details = if (paymentMeta?.swap_out_address != null) {
            OutgoingPayment.Details.SwapOut(
                address = paymentMeta.swap_out_address ?: "",
                paymentRequest = paymentRequest ?: PaymentRequest.create(
                    chainHash = chainHash,
                    amount = head.recipientAmount().toLong().msat,
                    paymentHash = head.paymentHash().bytes().toArray().byteVector32(),
                    privateKey = Lightning.randomKey(),
                    description = Either.Left("swap-out to ${paymentMeta.swap_out_address} for ${paymentMeta.swap_out_feerate_per_byte} sat/b"),
                    minFinalCltvExpiryDelta = PaymentRequest.DEFAULT_MIN_FINAL_EXPIRY_DELTA,
                    features = Features.empty
                ),
                swapOutFee = paymentMeta.swap_out_fee_sat?.sat ?: 0.sat
            )
        } else if (head.paymentType() == "ClosingChannel") {
            OutgoingPayment.Details.ChannelClosing(
                channelId = paymentMeta?.closing_channel_id?.let { ByteVector32.fromValidHex(it) } ?: Lightning.randomBytes32().sha256(),
                closingAddress = paymentMeta?.closing_main_output_script ?: "",
                isSentToDefaultAddress = paymentMeta?.closing_type != ClosingType.Mutual.code
            )
        } else if (paymentRequest != null) {
            OutgoingPayment.Details.Normal(paymentRequest)
        } else {
            OutgoingPayment.Details.KeySend(preimage = Lightning.randomBytes32().sha256())
        }

        // lightning parts
        val lightningParts = listOfParts.filter { it.paymentType() == PaymentType.Standard() }.map { part ->
            when (val partStatus = part.status()) {
                is OutgoingPaymentStatus.Succeeded -> {
                    OutgoingPayment.LightningPart(
                        id = UUID.fromString(part.id().toString()),
                        amount = part.amount().toLong().msat + partStatus.feesPaid().toLong().msat, // must include the fee!!!
                        route = JavaConverters.asJavaCollectionConverter(partStatus.route()).asJavaCollection().toList().map { hop ->
                            HopDesc(
                                nodeId = PublicKey.fromHex(hop.nodeId().toString()),
                                nextNodeId = PublicKey.fromHex(hop.nextNodeId().toString()),
                                shortChannelId = if (hop.shortChannelId().isDefined) ShortChannelId(hop.shortChannelId().get().toLong()) else null
                            )
                        },
                        status = OutgoingPayment.LightningPart.Status.Succeeded(
                            preimage = partStatus.paymentPreimage().bytes().toArray().byteVector32(),
                            completedAt = partStatus.completedAt()
                        ),
                        createdAt = part.createdAt()
                    )
                }
                is OutgoingPaymentStatus.Failed -> {
                    OutgoingPayment.LightningPart(
                        id = UUID.fromString(part.id().toString()),
                        amount = part.amount().toLong().msat + 0.msat, // must include the fee!!!
                        route = listOf(),
                        status = OutgoingPayment.LightningPart.Status.Failed(
                            remoteFailureCode = null,
                            details = JavaConverters.asJavaCollectionConverter(partStatus.failures()).asJavaCollection().toList().lastOrNull()?.failureMessage() ?: "error details unavailable",
                            completedAt = partStatus.completedAt()
                        ),
                        createdAt = part.createdAt()
                    )
                }
                else -> {
                    OutgoingPayment.LightningPart(
                        id = UUID.fromString(part.id().toString()),
                        amount = part.amount().toLong().msat + 0.msat, // must include the fee!!!
                        route = listOf(),
                        status = OutgoingPayment.LightningPart.Status.Pending,
                        createdAt = part.createdAt()
                    )
                }
            }
        }

        val closingTxs = paymentMeta?.getSpendingTxs()?.map { ByteVector32.fromValidHex(it) } ?: emptyList()
        val closingTxsParts = if (head.paymentType() == "ClosingChannel") {
            closingTxs.mapIndexed { index, tx ->
                OutgoingPayment.ClosingTxPart(
                    id = UUID.randomUUID(),
                    claimed = if (index == 0) head.amount().truncateToSatoshi().toLong().sat else 0.sat,
                    txId = tx,
                    closingType = when (paymentMeta?.closing_type) {
                        ClosingType.Mutual.code -> ChannelClosingType.Mutual
                        ClosingType.Local.code -> ChannelClosingType.Local
                        ClosingType.Remote.code -> ChannelClosingType.Remote
                        else -> ChannelClosingType.Other
                    },
                    createdAt = head.createdAt()
                )
            }
        } else {
            emptyList()
        }

        // save status
        val status: OutgoingPayment.Status = when {
            listOfParts.any { p -> p.status() is OutgoingPaymentStatus.`Pending$` } -> {
                // pending is the default status of the payment
                OutgoingPayment.Status.Pending
            }
            listOfParts.any { p -> p.status() is OutgoingPaymentStatus.Succeeded } -> {
                val statuses = listOfParts.map { it.status() }.filterIsInstance<OutgoingPaymentStatus.Succeeded>()
                if (paymentMeta?.swap_out_address != null && paymentMeta.swap_out_fee_sat != null && paymentMeta.swap_out_feerate_per_byte != null) {
                    OutgoingPayment.Status.Completed.Succeeded.OffChain(
                        preimage = statuses.first().paymentPreimage().bytes().toArray().byteVector32(),
                        completedAt = statuses.last().completedAt()
                    )
                } else if (head.paymentType() == "ClosingChannel") {
                    OutgoingPayment.Status.Completed.Succeeded.OnChain(completedAt = statuses.first().completedAt())
                } else {
                    OutgoingPayment.Status.Completed.Succeeded.OffChain(
                        preimage = statuses.first().paymentPreimage().bytes().toArray().byteVector32(),
                        completedAt = statuses.first().completedAt()
                    )
                }
            }

            listOfParts.any { p -> p.status() is OutgoingPaymentStatus.Failed } -> {
                // attempt to translate the old failure type to a kmp `FinalFailure`
                val (finalFailure, completedAt) = listOfParts.filter { p -> p.status() is OutgoingPaymentStatus.Failed }.let {
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

            else -> {
                OutgoingPayment.Status.Pending
            }
        }

        return OutgoingPayment(
            id = parentId,
            recipientAmount = head.recipientAmount().toLong().msat,
            recipient = PublicKey.fromHex(head.recipientNodeId().toString()),
            details = details,
            parts = lightningParts + closingTxsParts,
            status = status,
            createdAt = head.createdAt()
        )
    }
}