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
import fr.acinq.bitcoin.TxId
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
import fr.acinq.lightning.db.*
import fr.acinq.lightning.io.Peer
import fr.acinq.lightning.io.TcpSocket
import fr.acinq.lightning.payment.FinalFailure
import fr.acinq.lightning.payment.PaymentRequest
import fr.acinq.lightning.utils.*
import fr.acinq.phoenix.android.PhoenixApplication
import fr.acinq.phoenix.android.utils.datastore.HomeAmountDisplayMode
import fr.acinq.phoenix.android.utils.datastore.UserPrefs
import fr.acinq.phoenix.data.BitcoinUnit
import fr.acinq.phoenix.data.FiatCurrency
import fr.acinq.phoenix.data.WalletPaymentId
import fr.acinq.phoenix.data.WalletPaymentInfo
import fr.acinq.phoenix.data.lnurl.LnurlAuth
import fr.acinq.phoenix.legacy.db.*
import fr.acinq.phoenix.legacy.utils.Prefs
import fr.acinq.phoenix.legacy.utils.ThemeHelper
import fr.acinq.phoenix.legacy.utils.Wallet
import fr.acinq.phoenix.managers.AppConnectionsDaemon
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import scala.collection.JavaConversions
import scala.collection.JavaConverters
import scala.collection.Seq


object LegacyMigrationHelper {

    private val log: Logger = LoggerFactory.getLogger(this::class.java)
    val migrationDescFlag = "kmp-migration-override"

    /** Import the legacy app's preferences into the new app's datastores. */
    suspend fun migrateLegacyPreferences(
        context: Context
    ) {
        log.info("started migrating legacy user preferences")

        val (business, internalData) = (context as PhoenixApplication).run { business.filterNotNull().first() to internalDataRepository }
        val appConfigurationManager = business.appConfigurationManager

        // -- utils

        internalData.saveLastUsedAppCode(Prefs.getLastVersionUsed(context))
        val backupWasDone = Prefs.getMnemonicsSeenTimestamp(context) > 0
        internalData.saveManualSeedBackupDone(backupWasDone)
        internalData.saveSeedLossDisclaimerRead(backupWasDone)
        Prefs.getFCMToken(context)?.let { internalData.saveFcmToken(it) }
        internalData.saveShowIntro(Prefs.showFTUE(context))

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

        // -- security & tor

        UserPrefs.saveIsScreenLockActive(context, Prefs.isScreenLocked(context))
        Prefs.isTorEnabled(context).let {
            UserPrefs.saveIsTorEnabled(context, it)
            appConfigurationManager.updateTorUsage(it)
        }

        // -- electrum

        Prefs.getElectrumServer(context).takeIf { it.isNotBlank() }?.let {
            val hostPort = HostAndPort.fromString(it).withDefaultPort(50002)
            // TODO: handle onion addresses and TOR
            ServerAddress(hostPort.host, hostPort.port, TcpSocket.TLS.TRUSTED_CERTIFICATES())
        }?.let {
            UserPrefs.saveElectrumServer(context, it)
            appConfigurationManager.updateElectrumConfig(it)
        }

        // -- payment settings

        UserPrefs.saveInvoiceDefaultDesc(context, Prefs.getDefaultPaymentDescription(context))
        UserPrefs.saveInvoiceDefaultExpiry(context, Prefs.getPaymentsExpirySeconds(context))

        Prefs.getMaxTrampolineCustomFee(context)?.let {
            TrampolineFees(feeBase = Satoshi(it.feeBase.toLong()), feeProportional = it.feeProportionalMillionths, cltvExpiryDelta = CltvExpiryDelta(it.cltvExpiry.toInt()))
        }?.let {
            UserPrefs.saveTrampolineMaxFee(context, it)
        }

        // use the default scheme when migrating from legacy, instead of the default one
        UserPrefs.saveLnurlAuthScheme(context, LnurlAuth.Scheme.ANDROID_LEGACY_SCHEME)

        business.appConnectionsDaemon?.forceReconnect(AppConnectionsDaemon.ControlTarget.All)

        log.info("finished migration of legacy user preferences")
    }

    suspend fun migrateLegacyPayments(
        context: Context,
    ) {
        val eclairDbFile = Wallet.getEclairDBFile(context)
        if (!eclairDbFile.exists()) {
            log.info("no legacy database file found, no data migration needed.")
            return
        }

        // 1 - create a copy of the eclair database file we can safely work on
        eclairDbFile.copyTo(Wallet.getEclairDBMigrationFile(context), overwrite = true)
        log.info("legacy database file has been copied")

        val legacyMetaRepository = PaymentMetaRepository.getInstance(AppDb.getInstance(context).paymentMetaQueries)
        val legacyPayToOpenMetaRepository = PayToOpenMetaRepository.getInstance(AppDb.getInstance(context).payToOpenMetaQueries)
        val legacyPaymentsDb = SqlitePaymentsDb(SqliteUtils.openSqliteFile(Wallet.getChainDatadir(context), Wallet.ECLAIR_DB_FILE_MIGRATION, true, "wal", "normal"))
        log.info("opened legacy payments db")

        // 2 - get the new payments database
        val business = (context as PhoenixApplication).business.filterNotNull().first()
        val newPaymentsDb = business.databaseManager.paymentsDb()

        // 3 - extract all outgoing payments from the legacy database, and save them to the new database
        val outgoing = groupLegacyOutgoingPayments(legacyPaymentsDb.listAllOutgoingPayments())
        log.info("migrating ${outgoing.size} outgoing payments")
        outgoing.forEach {
            try {
                val parentId = it.key
                val paymentMeta = legacyMetaRepository.get(parentId.toString())
                val payment = modernizeLegacyOutgoingPayment(business.chain, parentId, it.value, paymentMeta)

                // save payment to database
                newPaymentsDb.addOutgoingPayment(payment)

                // status must be updated separately for LightningOutgoingPayments (and parts)!
                if (payment is LightningOutgoingPayment) {
                    when (val status = payment.status) {
                        is LightningOutgoingPayment.Status.Completed.Succeeded.OffChain -> {
                            newPaymentsDb.completeOutgoingPaymentOffchain(
                                id = payment.id,
                                preimage = status.preimage,
                                completedAt = status.completedAt
                            )
                        }
                        is LightningOutgoingPayment.Status.Completed.Failed -> {
                            newPaymentsDb.completeOutgoingPaymentOffchain(
                                id = payment.id,
                                finalFailure = status.reason,
                                completedAt = status.completedAt
                            )
                        }
                        LightningOutgoingPayment.Status.Pending -> {
                            // no need to update the DB as this is the default status
                        }
                    }
                    payment.parts.forEach { part ->
                        when (val status = part.status) {
                            is LightningOutgoingPayment.Part.Status.Succeeded -> newPaymentsDb.completeOutgoingLightningPart(part.id, status.preimage, status.completedAt)
                            is LightningOutgoingPayment.Part.Status.Failed -> newPaymentsDb.completeOutgoingLightningPartLegacy(part.id, status, status.completedAt)
                            LightningOutgoingPayment.Part.Status.Pending -> {}
                        }
                    }
                }
                log.debug("migrated outgoing payment=$payment")

                if (it.value.first().paymentType() == "KmpMigration") {
                    newPaymentsDb.updateMetadata(
                        id = WalletPaymentId.ChannelCloseOutgoingPaymentId(id = parentId),
                        userDescription = migrationDescFlag,
                        userNotes = migrationDescFlag
                    )
                } else {
                    // save metadata
                    if (paymentMeta?.custom_desc != null) {
                        newPaymentsDb.updateMetadata(
                            id = WalletPaymentId.LightningOutgoingPaymentId(parentId),
                            userDescription = paymentMeta.custom_desc,
                            userNotes = null
                        )
                        log.debug("saved custom desc=${paymentMeta.custom_desc} for payment=$parentId")
                    }
                }
            } catch (e: Exception) {
                log.error("payment migration: failed to save outgoing payment=$it: ${e.localizedMessage}")
            }
        }
        log.info("successfully migrated ${outgoing.size} outgoing payments")

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
                    newPaymentsDb.addIncomingPayment(
                        preimage = payment.preimage,
                        origin = payment.origin,
                        createdAt = it.createdAt()
                    )
                    newPaymentsDb.receivePayment(
                        paymentHash = payment.paymentHash,
                        receivedWith = payment.received!!.receivedWith,
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
            } catch (e: Exception) {
                log.error("payment migration: failed to save incoming payment=$it: ${e.localizedMessage}")
            }
        }
        log.info("successfully migrated ${incoming.size} incoming payments")

        legacyPaymentsDb.close()
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
                    paymentRequest = PaymentRequest.read(fr.acinq.eclair.payment.PaymentRequest.write(payment.paymentRequest())).get()
                )
            }

            // use the PayToOpen metadata to know how the payment was received
            val receivedWith = if (payToOpenMeta != null || payment.paymentType() == PaymentType.SwapIn()) {
                IncomingPayment.ReceivedWith.NewChannel(
                    amount = status.amount().toLong().msat,
                    serviceFee = payToOpenMeta?.fee_sat?.sat?.toMilliSatoshi() ?: 0.msat,
                    miningFee = 0.sat,
                    channelId = ByteVector32.Zeroes,
                    txId = TxId(ByteVector32.Zeroes),
                    confirmedAt = status.receivedAt(),
                    lockedAt = status.receivedAt()
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
                received = IncomingPayment.Received(listOf(receivedWith), status.receivedAt()),
                createdAt = payment.createdAt()
            )
        }
    }

    fun groupLegacyOutgoingPayments(payments: Seq<fr.acinq.eclair.db.OutgoingPayment>): Map<UUID, List<fr.acinq.eclair.db.OutgoingPayment>> =
        JavaConversions.asJavaCollection(payments).toList().groupBy { UUID.fromString(it.parentId().toString()) }

    fun modernizeLegacyOutgoingPayment(
        chain: NodeParams.Chain,
        parentId: UUID,
        listOfParts: List<fr.acinq.eclair.db.OutgoingPayment>,
        paymentMeta: PaymentMeta?,
    ): OutgoingPayment {
        val head = listOfParts.first()

        if (head.paymentType() == "ClosingChannel" || head.paymentType() == "KmpMigration") {
            val closedAt = when (val status = head.status()) {
                is OutgoingPaymentStatus.Failed -> status.completedAt()
                is OutgoingPaymentStatus.Succeeded -> status.completedAt()
                else -> head.createdAt()
            }
            return ChannelCloseOutgoingPayment(
                id = parentId,
                recipientAmount = head.amount().truncateToSatoshi().toLong().sat,
                address = paymentMeta?.closing_main_output_script ?: "",
                isSentToDefaultAddress = paymentMeta?.closing_type != ClosingType.Mutual.code,
                miningFees = 0.sat,
                txId = TxId(paymentMeta?.getSpendingTxs()?.firstOrNull()?.let { ByteVector32.fromValidHex(it) } ?: ByteVector32.Zeroes),
                createdAt = head.createdAt(),
                confirmedAt = closedAt,
                lockedAt = closedAt,
                channelId = paymentMeta?.closing_channel_id?.let { ByteVector32.fromValidHex(it) } ?: ByteVector32.Zeroes,
                closingType = when (paymentMeta?.closing_type) {
                    ClosingType.Mutual.code -> ChannelClosingType.Mutual
                    ClosingType.Local.code -> ChannelClosingType.Local
                    ClosingType.Remote.code -> ChannelClosingType.Remote
                    else -> ChannelClosingType.Other
                },
            )
        }

        val paymentRequest = if (head.paymentRequest().isDefined) {
            PaymentRequest.read(fr.acinq.eclair.payment.PaymentRequest.write(head.paymentRequest().get())).get()
        } else null

        // retrieve details from the first payment in the list
        val details = if (paymentMeta?.swap_out_address != null) {
            LightningOutgoingPayment.Details.SwapOut(
                address = paymentMeta.swap_out_address ?: "",
                paymentRequest = paymentRequest ?: PaymentRequest.create(
                    chainHash = chain.chainHash,
                    amount = head.recipientAmount().toLong().msat,
                    paymentHash = head.paymentHash().bytes().toArray().byteVector32(),
                    privateKey = Lightning.randomKey(),
                    description = Either.Left("swap-out to ${paymentMeta.swap_out_address} for ${paymentMeta.swap_out_feerate_per_byte} sat/b"),
                    minFinalCltvExpiryDelta = PaymentRequest.DEFAULT_MIN_FINAL_EXPIRY_DELTA,
                    features = Features.empty
                ),
                swapOutFee = paymentMeta.swap_out_fee_sat?.sat ?: 0.sat
            )
        } else if (paymentRequest != null) {
            LightningOutgoingPayment.Details.Normal(paymentRequest)
        } else {
            LightningOutgoingPayment.Details.KeySend(preimage = Lightning.randomBytes32().sha256())
        }

        val parts = listOfParts.filter { it.paymentType() == PaymentType.Standard() }.map { part ->
            when (val partStatus = part.status()) {
                is OutgoingPaymentStatus.Succeeded -> {
                    LightningOutgoingPayment.Part(
                        id = UUID.fromString(part.id().toString()),
                        amount = part.amount().toLong().msat + partStatus.feesPaid().toLong().msat, // must include the fee!!!
                        route = JavaConverters.asJavaCollectionConverter(partStatus.route()).asJavaCollection().toList().map { hop ->
                            HopDesc(
                                nodeId = PublicKey.fromHex(hop.nodeId().toString()),
                                nextNodeId = PublicKey.fromHex(hop.nextNodeId().toString()),
                                shortChannelId = if (hop.shortChannelId().isDefined) ShortChannelId(hop.shortChannelId().get().toLong()) else null
                            )
                        },
                        status = LightningOutgoingPayment.Part.Status.Succeeded(
                            preimage = partStatus.paymentPreimage().bytes().toArray().byteVector32(),
                            completedAt = partStatus.completedAt()
                        ),
                        createdAt = part.createdAt()
                    )
                }
                is OutgoingPaymentStatus.Failed -> {
                    LightningOutgoingPayment.Part(
                        id = UUID.fromString(part.id().toString()),
                        amount = part.amount().toLong().msat + 0.msat, // must include the fee!!!
                        route = listOf(),
                        status = LightningOutgoingPayment.Part.Status.Failed(
                            remoteFailureCode = null,
                            details = JavaConverters.asJavaCollectionConverter(partStatus.failures()).asJavaCollection().toList().lastOrNull()?.failureMessage() ?: "error details unavailable",
                            completedAt = partStatus.completedAt()
                        ),
                        createdAt = part.createdAt()
                    )
                }
                else -> {
                    LightningOutgoingPayment.Part(
                        id = UUID.fromString(part.id().toString()),
                        amount = part.amount().toLong().msat + 0.msat, // must include the fee!!!
                        route = listOf(),
                        status = LightningOutgoingPayment.Part.Status.Pending,
                        createdAt = part.createdAt()
                    )
                }
            }
        }

        // save status
        val status: LightningOutgoingPayment.Status = when {
            listOfParts.any { p -> p.status() is OutgoingPaymentStatus.`Pending$` } -> {
                // pending is the default status of the payment
                LightningOutgoingPayment.Status.Pending
            }
            listOfParts.any { p -> p.status() is OutgoingPaymentStatus.Succeeded } -> {
                val statuses = listOfParts.map { it.status() }.filterIsInstance<OutgoingPaymentStatus.Succeeded>()
                if (paymentMeta?.swap_out_address != null && paymentMeta.swap_out_fee_sat != null && paymentMeta.swap_out_feerate_per_byte != null) {
                    LightningOutgoingPayment.Status.Completed.Succeeded.OffChain(
                        preimage = statuses.first().paymentPreimage().bytes().toArray().byteVector32(),
                        completedAt = statuses.last().completedAt()
                    )
                } else {
                    LightningOutgoingPayment.Status.Completed.Succeeded.OffChain(
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
                LightningOutgoingPayment.Status.Completed.Failed(reason = finalFailure, completedAt = completedAt)
            }

            else -> {
                LightningOutgoingPayment.Status.Pending
            }
        }

        return LightningOutgoingPayment(
            id = parentId,
            recipientAmount = head.recipientAmount().toLong().msat,
            recipient = PublicKey.fromHex(head.recipientNodeId().toString()),
            details = details,
            parts = parts,
            status = status,
            createdAt = head.createdAt()
        )
    }
}

/** Returns true if the payment is a channel-close made by the legacy app to the node's swap-in address. Uses the [LegacyMigrationHelper.migrationDescFlag] metadata flag. */
fun WalletPaymentInfo.isLegacyMigration(peer: Peer?): Boolean? {
    val p = payment
    return when {
        p !is ChannelCloseOutgoingPayment -> false
        peer == null -> null
        p.address == peer.swapInAddress && metadata.userDescription == LegacyMigrationHelper.migrationDescFlag -> true
        else -> false
    }
}
