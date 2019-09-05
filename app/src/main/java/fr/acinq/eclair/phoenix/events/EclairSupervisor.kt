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
import fr.acinq.eclair.channel.*
import fr.acinq.eclair.db.`BackupCompleted$`
import fr.acinq.eclair.db.`PaymentDirection$`
import fr.acinq.eclair.io.PayToOpenRequestEvent
import fr.acinq.eclair.payment.PaymentLifecycle
import fr.acinq.eclair.payment.PaymentReceived
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

  private val channelsMap = HashMap<ActorRef, Commitments>()

  private fun postBalance() {
    val balance = MilliSatoshi(channelsMap.map { c -> c.value.localCommit().spec().toLocal().amount() }.sum())
    log.info("posting balance=${balance.amount()}")
    EventBus.getDefault().post(BalanceEvent(balance))
  }

  override fun onReceive(event: Any?) {
    log.debug("received event $event")
    when (event) {
      is ChannelCreated -> {
        log.info("channel $event has been created")
      }
      is ChannelRestored -> {
        log.info("channel $event has been restored")
        channelsMap[event.channel()] = event.currentData().commitments()
        postBalance()
      }
      is ChannelIdAssigned -> {
        log.info("channel has been assigned id=${event.channelId()}")
      }
      is ChannelStateChanged -> {
        val data = event.currentData()
        if (data is HasCommitments) {
          if (data is DATA_CLOSING || data is DATA_SHUTDOWN) {
            channelsMap.remove(event.channel())
          } else {
            channelsMap[event.channel()] = data.commitments()
          }
          postBalance()
        }
      }
      is ChannelSignatureSent -> {
        log.info("signature sent on ${event.commitments().channelId()}")
        EventBus.getDefault().post(PaymentPending())
      }
      is ChannelSignatureReceived -> {
        log.info("signature $event has been sent")
        channelsMap[event.channel()] = event.commitments()
        postBalance()
      }
      is `BackupCompleted$` -> {
        log.info("channels have been backed up by core")
      }
      is Terminated -> {
        log.info("channel $event has been terminated")
        channelsMap.remove(event.actor)
      }
      is AcceptPayToOpen -> {
        val payToOpen = payToOpenMap[event.paymentHash]
        payToOpen?.let {
          if (it.decision().trySuccess(true)) {
            payToOpenMap.remove(event.paymentHash)
          } else {
            log.warn("success promise for $event has failed")
          }
        } ?: log.info("ignored $event because associated event for this payment_hash is unknown")
      }
      is RejectPayToOpen -> {
        val payToOpen = payToOpenMap[event.paymentHash]
        payToOpen?.let {
          if (it.decision().trySuccess(false)) {
            log.info("payToOpen event has been rejected by user")
          } else {
            log.warn("success promise for $event has failed")
          }
        } ?: log.info("ignored $event because associated event for this payment_hash is unknown")
      }
      is PayToOpenRequestEvent -> {
        log.info("adding PendingPayToOpenRequest for payment_hash=${event.paymentHash()}")
        payToOpenMap[event.paymentHash()] = event
        EventBus.getDefault().post(event)
      }
      is PaymentLifecycle.PaymentSucceeded -> {
        EventBus.getDefault().post(PaymentComplete(`PaymentDirection$`.`MODULE$`.OUTGOING(), event.id().toString()))
      }
      is PaymentLifecycle.PaymentFailed -> {
        log.info("payment has failed [ ${event.failures().mkString(", ")} ]")
        EventBus.getDefault().post(PaymentComplete(`PaymentDirection$`.`MODULE$`.OUTGOING(), event.id().toString()))
      }
      is PaymentReceived -> {
        EventBus.getDefault().post(PaymentComplete(`PaymentDirection$`.`MODULE$`.INCOMING(), event.paymentHash().toString()))
      }
      else -> {
        log.warn("unhandled event $event")
      }
    }
  }

  override fun aroundPostStop() {
    super.aroundPostStop()
    log.info("eclair supervisor stopped")
  }
}
