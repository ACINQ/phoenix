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
import fr.acinq.bitcoin.Crypto
import fr.acinq.bitcoin.MilliSatoshi
import fr.acinq.eclair.channel.*
import fr.acinq.eclair.db.`BackupCompleted$`
import fr.acinq.eclair.io.PayToOpenRequestEvent
import fr.acinq.eclair.payment.PaymentLifecycle
import fr.acinq.eclair.payment.PaymentReceived
import org.greenrobot.eventbus.EventBus
import org.slf4j.LoggerFactory
import org.spongycastle.crypto.Commitment

class PendingPayToOpenRequestEvent(val preimage: ByteVector32, var event: PayToOpenRequestEvent?)

interface PayToOpenResponse
class AcceptPayToOpen(val paymentHash: ByteVector32) : PayToOpenResponse
class RejectPayToOpen(val paymentHash: ByteVector32) : PayToOpenResponse

/**
 * This actor listens to events dispatched by eclair core.
 */
class NodeSupervisor : UntypedActor() {
  private val log = LoggerFactory.getLogger(NodeSupervisor::class.java)

  // key is payment hash
  private val payToOpenMap = HashMap<ByteVector32, PendingPayToOpenRequestEvent>()

  private val channelsMap = HashMap<ActorRef, Commitments>()

  private fun postBalance() {
    val balance = MilliSatoshi(channelsMap.map { c -> c.value.availableBalanceForSendMsat() }.sum())
    log.info("posting balance=${balance.amount()}")
    EventBus.getDefault().post(BalanceEvent(balance))
  }

  private fun postPayment() {
    log.info("posting payment event")
    EventBus.getDefault().post(PaymentEvent())
  }

  override fun onReceive(event: Any?) {
    log.debug("received event $event")
    when (event) {
      is ChannelCreated -> {
        log.info("channel $event has been created")
        postPayment()
      }
      is ChannelRestored -> {
        log.info("channel $event has been restored")
        channelsMap[event.channel()] = event.currentData().commitments()
        postBalance()
        postPayment()
      }
      is ChannelIdAssigned -> {
        log.info("channel has been assigned id=${event.channelId()}")
      }
      is ChannelStateChanged -> {
        val data = event.currentData()
        if (data is HasCommitments) {
          channelsMap[event.channel()] = data.commitments()
          postBalance()

        }
      }
      is ChannelSignatureSent -> {
        log.info("signature sent on ${event.commitments().channelId()}")
        postPayment()
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
      is ByteVector32 -> {
        val paymentHash = Crypto.sha256().apply(event.bytes())
        if (payToOpenMap.containsKey(paymentHash)) {
          log.warn("received preimage with hash=$paymentHash but it is already linked with a pending payToOpen request")
        } else {
          log.info("adding PendingPayToOpenRequest for payment_hash=$paymentHash")
          payToOpenMap[paymentHash] = PendingPayToOpenRequestEvent(event, null)
        }
      }
      is AcceptPayToOpen -> {
        if (payToOpenMap.containsKey(event.paymentHash)) {
          val preimage = payToOpenMap[event.paymentHash]!!.preimage
          val payToOpen = payToOpenMap[event.paymentHash]!!.event
          if (payToOpen == null) {
            log.info("ignored $event because associated event for this payment_hash is unknown")
          } else {
            if (payToOpen.paymentPreimage().trySuccess(preimage)) {
              payToOpenMap.remove(event.paymentHash)
            } else {
              log.warn("success promise for $event has failed")
            }
          }
        } else {
          log.info("ignored $event because payment_hash is unknown")
        }
      }
      is RejectPayToOpen -> {
        if (payToOpenMap.containsKey(event.paymentHash)) {
          val payToOpen = payToOpenMap[event.paymentHash]!!.event
          if (payToOpen == null) {
            log.info("ignored $event because associated event for this payment_hash is unknown")
          } else {
            if (payToOpen.paymentPreimage().tryFailure(RuntimeException("rejected by user"))) {
              payToOpenMap.remove(event.paymentHash)
            } else {
              log.warn("success promise for $event has failed")
            }
          }
        } else {
          log.info("ignored $event because payment_hash is unknown")
        }
      }
      is PayToOpenRequestEvent -> {
        log.info("peer sent an open channel request with payment_hash=${event.payToOpenRequest().paymentHash()}")
        if (payToOpenMap.containsKey(event.payToOpenRequest().paymentHash())) {
          log.info("received valid PayToOpen request with payment_hash=${event.payToOpenRequest().paymentHash()}, ask for user permission")
          payToOpenMap[event.payToOpenRequest().paymentHash()]!!.event = event
          EventBus.getDefault().post(event)
        } else {
          log.info("$event is unknown")
          event.paymentPreimage().tryFailure(RuntimeException("unknown payment hash"))
        }
      }
      is PaymentLifecycle.PaymentSucceeded -> {
        postPayment()
      }
      is PaymentLifecycle.PaymentFailed -> {
        postPayment()
      }
      is PaymentReceived -> {
        postPayment()
      }
      else -> {
        log.warn("unhandled event $event")
      }
    }
  }

  override fun aroundPostStop() {
    super.aroundPostStop()
    log.info("eclair events supervisor stopped")
  }
}
