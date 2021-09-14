/*
 * Copyright 2019 ACINQ SAS
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

package fr.acinq.phoenix.legacy.background

import akka.actor.UntypedActor
import android.content.Context
import android.text.format.DateUtils
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.navigation.NavDeepLinkBuilder
import fr.acinq.bitcoin.Base58Check
import fr.acinq.bitcoin.Script
import fr.acinq.eclair.MilliSatoshi
import fr.acinq.eclair.`package$`
import fr.acinq.eclair.blockchain.electrum.ElectrumClient
import fr.acinq.eclair.channel.*
import fr.acinq.eclair.io.PayToOpenRequestEvent
import fr.acinq.eclair.io.PeerConnected
import fr.acinq.eclair.io.PeerDisconnected
import fr.acinq.eclair.payment.MissedPayToOpenPayment
import fr.acinq.eclair.payment.PaymentFailed
import fr.acinq.eclair.payment.PaymentReceived
import fr.acinq.eclair.payment.PaymentSent
import fr.acinq.eclair.payment.relay.Relayer
import fr.acinq.eclair.wire.SwapInConfirmed
import fr.acinq.eclair.wire.SwapInPending
import fr.acinq.eclair.wire.SwapInResponse
import fr.acinq.eclair.wire.SwapOutResponse
import fr.acinq.phoenix.legacy.Balance
import fr.acinq.phoenix.legacy.R
import fr.acinq.phoenix.legacy.db.AppDb
import fr.acinq.phoenix.legacy.db.ClosingType
import fr.acinq.phoenix.legacy.db.PayToOpenMetaRepository
import fr.acinq.phoenix.legacy.db.PaymentMetaRepository
import fr.acinq.phoenix.legacy.utils.Constants
import fr.acinq.phoenix.legacy.utils.Converter
import fr.acinq.phoenix.legacy.utils.Prefs
import fr.acinq.phoenix.legacy.utils.Wallet
import org.greenrobot.eventbus.EventBus
import org.slf4j.LoggerFactory
import scala.collection.JavaConverters


/**
 * This actor listens to events dispatched by eclair core.
 */
class EclairSupervisor(val applicationContext: Context) : UntypedActor() {

  private val paymentMetaRepository: PaymentMetaRepository
  private val payToOpenMetaRepository: PayToOpenMetaRepository
  private val notificationManager: NotificationManagerCompat

  init {
    val appDb = AppDb.getInstance(applicationContext)
    paymentMetaRepository = PaymentMetaRepository.getInstance(appDb.paymentMetaQueries)
    payToOpenMetaRepository = PayToOpenMetaRepository.getInstance(appDb.payToOpenMetaQueries)
    notificationManager = NotificationManagerCompat.from(applicationContext)
  }

  private val log = LoggerFactory.getLogger(EclairSupervisor::class.java)

  override fun onReceive(event: Any?) {
    log.debug("received event $event")
    when (event) {
      // -------------- CHANNELS LIFECYCLE -------------
      is ChannelStateChanged -> {
        val validOriginForClosures = listOf(`NORMAL$`.`MODULE$`, `SHUTDOWN$`.`MODULE$`, `NEGOTIATING$`.`MODULE$`)
        if (event.currentData() is HasCommitments && event.currentData() is DATA_CLOSING && event.currentState() == `CLOSING$`.`MODULE$` && validOriginForClosures.contains(event.previousState())) {
          val data = event.currentData() as DATA_CLOSING
          // dispatch closing if the channel's goes to closing. Do NOT dispatch if the channel is restored and was already closing (i.e closing from an internal state).
          log.info("channel=${data.channelId()} closing from state=${event.previousState()} with data=$data")
          val balance = data.commitments().localCommit().spec().toLocal().truncateToSatoshi()
          val spendingTxs = JavaConverters.seqAsJavaListConverter(data.spendingTxes()).asJava()
          val ourSpendingAddress = spendingTxs.map { JavaConverters.seqAsJavaListConverter(it.txOut()).asJava() }.flatten()
            .firstOrNull {
              log.debug("txout with amount=${it.amount()} to script=${it.publicKeyScript().toBase58()}, against balance=${balance}")
              it.amount() == balance && Script.isPayToScript(it.publicKeyScript())
            }?.let {
              val address = Base58Check.encode(Wallet.getScriptHashVersion(), Script.publicKeyHash(it.publicKeyScript()))
              log.info("found closing txOut sending to script=${it.publicKeyScript().toBase58()} address=$address")
              address
            }
          val closingType = when {
            !data.mutualClosePublished().isEmpty && data.localCommitPublished().isEmpty && data.remoteCommitPublished().isEmpty && data.revokedCommitPublished().isEmpty -> ClosingType.Mutual
            data.mutualClosePublished().isEmpty && !data.localCommitPublished().isEmpty && data.remoteCommitPublished().isEmpty && data.revokedCommitPublished().isEmpty -> ClosingType.Local
            data.mutualClosePublished().isEmpty && data.localCommitPublished().isEmpty && !data.remoteCommitPublished().isEmpty && data.revokedCommitPublished().isEmpty -> ClosingType.Remote
            else -> ClosingType.Other
          }
          EventBus.getDefault().post(ChannelClosingEvent(data.commitments().availableBalanceForSend(), data.channelId(), closingType, spendingTxs, ourSpendingAddress))
        }
        // dispatch UI event if a channel reaches or leaves the WAIT FOR FUNDING CONFIRMED state
        if (event.currentState() == `WAIT_FOR_FUNDING_CONFIRMED$`.`MODULE$` || event.previousState() == `WAIT_FOR_FUNDING_CONFIRMED$`.`MODULE$`) {
          log.debug("channel ${event.channel()} in state ${event.currentState()} from ${event.previousState()}")
          EventBus.getDefault().post(ChannelStateChange())
        }
      }
      is ChannelErrorOccurred -> {
        if (event.channelId() != null && event.isFatal) {
          val error = event.error()
          val errorMessage = if (error is LocalError && error.t() != null) {
            error.t().message ?: error.t()::javaClass.name
          } else if (error is RemoteError) {
            if (`package$`.`MODULE$`.isAsciiPrintable(error.e().data())) {
              Converter.toAscii(error.e().data())
            } else {
              error.e().data().toString()
            }
          } else null
          errorMessage?.let { paymentMetaRepository.setChannelClosingError(event.channelId().toString(), it) }
        }
      }
      is ChannelSignatureSent -> {
        EventBus.getDefault().post(PaymentPending())
      }
      is Relayer.OutgoingChannels -> {
        val (sendable, receivable) = JavaConverters.seqAsJavaListConverter(event.channels()).asJava().map { channel ->
          channel.commitments().availableBalanceForSend() to channel.commitments().availableBalanceForReceive()
        }.fold(Pair(MilliSatoshi(0), MilliSatoshi(0)), { a, b ->
          a.first.`$plus`(b.first) to a.second.`$plus`(b.second)
        })
        log.info("receive OutgoingChannels [ count=${event.channels().size()} sendable=$sendable receivable=$receivable")
        EventBus.getDefault().post(BalanceEvent(Balance(event.channels().size(), sendable, receivable)))
      }

      // -------------- CONNECTION WATCHER --------------
      is PeerConnected -> {
        log.info("connected to ${event.nodeId()}")
        EventBus.getDefault().post(PeerConnectionChange)
      }
      is PeerDisconnected -> {
        log.info("disconnected from ${event.nodeId()}")
        EventBus.getDefault().post(PeerConnectionChange)
      }

      // -------------- ELECTRUM --------------
      is ElectrumClient.ElectrumReady -> EventBus.getDefault().post(event)
      is ElectrumClient.`ElectrumDisconnected$` -> EventBus.getDefault().post(event)

      // -------------- PAY TO OPEN -------------
      is PayToOpenRequestEvent -> {
        log.info("adding pay-to-open request=$event")
        val isAutoChannelEnabled = Prefs.isAutoPayToOpenEnabled(applicationContext)
        val result = event.decision().trySuccess(isAutoChannelEnabled)
        if (result && isAutoChannelEnabled) {
          log.info("accepted pay-to-open channel opening, saving event to meta db")
          payToOpenMetaRepository.insert(
            paymentHash = event.payToOpenRequest().paymentHash(),
            fee = event.payToOpenRequest().payToOpenFee(),
            amount = event.payToOpenRequest().amountMsat().truncateToSatoshi(),
            capacity = event.payToOpenRequest().fundingSatoshis()
          )
        } else if (result && !isAutoChannelEnabled) {
          log.info("rejected pay-to-open channel opening")
          val message = applicationContext.getString(R.string.notif_pay_to_open_missed_message)
          if (System.currentTimeMillis() - Prefs.getMissedPayToOpenNotifTimestamp(applicationContext) > 20 * DateUtils.MINUTE_IN_MILLIS) {
            notificationManager.notify(
              Constants.NOTIF_ID__MISSED_PAY_TO_OPEN, NotificationCompat.Builder(applicationContext, Constants.NOTIF_CHANNEL_ID__MISSED_PAY_TO_OPEN)
                .setSmallIcon(R.drawable.ic_phoenix_outline)
                .setContentTitle(applicationContext.getString(R.string.notif_pay_to_open_missed_title))
                .setContentText(message)
                .setStyle(NotificationCompat.BigTextStyle().bigText(message))
                .setContentIntent(
                  NavDeepLinkBuilder(applicationContext)
                    .setGraph(R.navigation.nav_graph_main)
                    .setDestination(R.id.payment_settings_fragment)
                    .createPendingIntent()
                ).setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setAutoCancel(true)
                .setOnlyAlertOnce(true)
                .build())
            Prefs.setMissedPayToOpenNotifTimestamp(applicationContext, System.currentTimeMillis())
          } else {
            log.debug("ignored missed payment notification, do not spam")
          }
        } else {
          log.warn("failed to complete pay-to-open request promise for event=$event")
        }
        EventBus.getDefault().post(event)
      }
      is MissedPayToOpenPayment -> {
        log.info("missed pay-to-open=$event")
        EventBus.getDefault().post(event)
      }

      // ------------- SWAPPING IN/OUT ------------
      is SwapInResponse -> {
        log.info("received swap-in response: $event")
        EventBus.getDefault().post(event)
      }
      is SwapInPending -> {
        log.info("received pending swap-in event=$event")
        EventBus.getDefault().post(event)
      }
      is SwapInConfirmed -> {
        log.info("received confirmed swap-in=$event")
        EventBus.getDefault().post(event)
      }
      is SwapOutResponse -> {
        log.info("received swap-out response: $event")
        EventBus.getDefault().post(event)
      }

      // -------------- PAYMENTS -------------
      is PaymentSent -> {
        log.info("payment has been successfully sent: $event")
        EventBus.getDefault().post(event)
      }
      is PaymentFailed -> {
        log.info("payment has failed [ ${event.failures().mkString(", ")} ]")
        EventBus.getDefault().post(event)
      }
      is PaymentReceived -> {
        log.info("payment has been successfully received: $event ")
        EventBus.getDefault().post(event)
      }
      // -------------- UNHANDLED -------------
      else -> {
        log.debug("unhandled event $event")
      }
    }
  }

  override fun aroundPostStop() {
    super.aroundPostStop()
    log.debug("eclair supervisor stopped")
  }
}
