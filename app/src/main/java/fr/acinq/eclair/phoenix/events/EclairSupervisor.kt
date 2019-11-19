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

package fr.acinq.eclair.phoenix.events

import akka.actor.ActorRef
import akka.actor.Terminated
import akka.actor.UntypedActor
import fr.acinq.bitcoin.ByteVector32
import fr.acinq.eclair.MilliSatoshi
import fr.acinq.eclair.blockchain.electrum.ElectrumClient
import fr.acinq.eclair.channel.*
import fr.acinq.eclair.db.PaymentDirection
import fr.acinq.eclair.db.`BackupCompleted$`
import fr.acinq.eclair.db.`PaymentDirection$`
import fr.acinq.eclair.io.PayToOpenRequestEvent
import fr.acinq.eclair.payment.PaymentFailed
import fr.acinq.eclair.payment.PaymentLifecycle
import fr.acinq.eclair.payment.PaymentReceived
import fr.acinq.eclair.payment.PaymentSent
import fr.acinq.eclair.wire.SwapInResponse
import org.greenrobot.eventbus.EventBus
import org.slf4j.LoggerFactory

interface PayToOpenResponse
class AcceptPayToOpen(val paymentHash: ByteVector32) : PayToOpenResponse
class RejectPayToOpen(val paymentHash: ByteVector32) : PayToOpenResponse

/**
 * This actor listens to events dispatched by eclair core.
 */
class EclairSupervisor : UntypedActor() {
  private val log = LoggerFactory.getLogger(EclairSupervisor::class.java)

  // key is payment hash
  private val payToOpenMap = HashMap<ByteVector32, PayToOpenRequestEvent>()

  private fun postBalance() {
    EventBus.getDefault().post(BalanceEvent())
  }

  override fun onReceive(event: Any?) {
    log.debug("received event $event")
    when (event) {
      // -------------- CHANNELS LIFECYCLE -------------
      is ChannelCreated -> {
        log.info("channel $event has been created")
      }
      is ChannelRestored -> {
        log.debug("channel $event has been restored")
        postBalance()
      }
      is ChannelIdAssigned -> {
        log.debug("channel has been assigned id=${event.channelId()}")
      }
      is ChannelStateChanged -> {
        val data = event.currentData()
        if (data is HasCommitments) {
          // dispatch closing if the channel's goes to closing. Do NOT dispatch if the channel is restored and was already closing (i.e closing from an internal state).
          if (data is DATA_CLOSING && event.currentState() == `CLOSING$`.`MODULE$` && event.previousState() != `WAIT_FOR_INIT_INTERNAL$`.`MODULE$`) {
            log.info("closing channel ${data.channelId()}")
            EventBus.getDefault().post(ChannelClosingEvent(data.commitments().availableBalanceForSend(), data.channelId()))
          }
          postBalance()
        }
      }
      is ChannelSignatureSent -> {
        log.debug("signature sent on ${event.commitments().channelId()}")
        EventBus.getDefault().post(PaymentPending())
      }
      is ChannelSignatureReceived -> {
        log.debug("signature $event has been sent")
        postBalance()
      }
      is Terminated -> {
        log.info("channel $event has been terminated")
      }

      // -------------- ELECTRUM -------------
      is ElectrumClient.ElectrumReady -> EventBus.getDefault().post(event)
      is ElectrumClient.`ElectrumDisconnected$` -> EventBus.getDefault().post(event)

      // -------------- PAY TO OPEN -------------
      is AcceptPayToOpen -> {
        val payToOpen = payToOpenMap[event.paymentHash]
        payToOpen?.let {
          if (it.paymentPreimage().trySuccess(true)) {
            payToOpenMap.remove(event.paymentHash)
          } else {
            log.warn("success promise for $event has failed")
          }
        } ?: log.info("ignored $event because associated event for this payment_hash is unknown")
      }
      is RejectPayToOpen -> {
        val payToOpen = payToOpenMap[event.paymentHash]
        payToOpen?.let {
          if (it.paymentPreimage().trySuccess(false)) {
            log.info("payToOpen event has been rejected by user")
          } else {
            log.warn("success promise for $event has failed")
          }
        } ?: log.info("ignored $event because associated event for this payment_hash is unknown")
      }
      is PayToOpenRequestEvent -> {
        log.info("adding PendingPayToOpenRequest for payment_hash=${event.payToOpenRequest().paymentHash()}")
        payToOpenMap[event.payToOpenRequest().paymentHash()] = event
        EventBus.getDefault().post(event)
      }

      // -------------- PAYMENTS -------------
      is SwapInResponse -> {
        log.info("received swap-in response: $event")
        EventBus.getDefault().post(event)
      }
      is PaymentSent -> {
        log.info("payment has been successfully sent: $event ")
        EventBus.getDefault().post(PaymentComplete(PaymentDirection.`OutgoingPaymentDirection$`.`MODULE$`, event.id().toString()))
      }
      is PaymentFailed -> {
        log.info("payment has failed [ ${event.failures().mkString(", ")} ]")
        EventBus.getDefault().post(PaymentComplete(PaymentDirection.`OutgoingPaymentDirection$`.`MODULE$`, event.id().toString()))
      }
      is PaymentReceived -> {
        log.info("payment has been successfully received: $event ")
        EventBus.getDefault().post(PaymentComplete(PaymentDirection.`IncomingPaymentDirection$`.`MODULE$`, event.paymentHash().toString()))
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
