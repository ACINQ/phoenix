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

package fr.acinq.phoenix.paymentdetails

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Typeface
import android.graphics.drawable.Animatable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import androidx.lifecycle.*
import androidx.lifecycle.Observer
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import androidx.navigation.navGraphViewModels
import fr.acinq.bitcoin.ByteVector32
import fr.acinq.eclair.MilliSatoshi
import fr.acinq.eclair.db.IncomingPayment
import fr.acinq.eclair.db.IncomingPaymentStatus
import fr.acinq.eclair.db.OutgoingPayment
import fr.acinq.eclair.db.OutgoingPaymentStatus
import fr.acinq.eclair.payment.PaymentRequest
import fr.acinq.phoenix.BaseFragment
import fr.acinq.phoenix.R
import fr.acinq.phoenix.databinding.FragmentPaymentDetailsBinding
import fr.acinq.phoenix.utils.Converter
import fr.acinq.phoenix.utils.ThemeHelper
import fr.acinq.phoenix.utils.Transcriber
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.launch
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import scala.collection.JavaConverters
import java.text.DateFormat
import java.util.*

class PaymentDetailsFragment : BaseFragment() {

  companion object {
    val OUTGOING = "OUTGOING"
    val INCOMING = "INCOMING"
  }

  override val log: Logger = LoggerFactory.getLogger(this::class.java)

  private lateinit var mBinding: FragmentPaymentDetailsBinding
  // shared view model, living with payment details nested graph
  private val model: PaymentDetailsViewModel by navGraphViewModels(R.id.nav_graph_payment_details) //{ factory }
  private val args: PaymentDetailsFragmentArgs by navArgs()

  override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
    mBinding = FragmentPaymentDetailsBinding.inflate(inflater, container, false)
    mBinding.lifecycleOwner = this
    return mBinding.root
  }

  override fun onActivityCreated(savedInstanceState: Bundle?) {
    super.onActivityCreated(savedInstanceState)
    if (args.fromEvent) {
      mBinding.mainLayout.layoutTransition = null
    }
    model.state.observe(viewLifecycleOwner, Observer { state ->
      context?.let { ctx ->
        when (state) {
          is PaymentDetailsState.Outgoing.Pending -> {
            mBinding.statusText.text = Converter.html(ctx.getString(R.string.paymentdetails_status_sent_pending))
            showStatusIconAndDetails(ctx, R.drawable.ic_send_lg, ThemeHelper.color(ctx, R.attr.mutedTextColor))
          }
          is PaymentDetailsState.Outgoing.Failed -> {
            mBinding.statusText.text = Converter.html(ctx.getString(R.string.paymentdetails_status_sent_failed))
            // use error of the last subpayment as it's probably the most pertinent
            val failures = JavaConverters.asJavaCollectionConverter((state.parts.last().status() as OutgoingPaymentStatus.Failed).failures()).asJavaCollection().toList()
            mBinding.errorValue.text = failures.joinToString("\n") { e -> e.failureMessage() }
            mBinding.amountValue.setAmount(state.parts.last().recipientAmount())
            showStatusIconAndDetails(ctx, R.drawable.ic_cross, ThemeHelper.color(ctx, R.attr.negativeColor))
          }
          is PaymentDetailsState.Outgoing.Succeeded -> {
            val statuses: List<OutgoingPaymentStatus.Succeeded> = state.parts.map { p -> p.status() as OutgoingPaymentStatus.Succeeded }
            // handle special case where payment is a placeholder for a channel closing: no fee, no destination, special description
            val head = state.parts.first()
            if (head.paymentRequest().isDefined) {
              val totalSent = MilliSatoshi(state.parts.map { p -> p.amount().`$plus`((p.status() as OutgoingPaymentStatus.Succeeded).feesPaid()).toLong() }.sum())
              val fees = totalSent.`$minus`(head.recipientAmount())
              val description = head.paymentRequest().get().description().let { d -> if (d.isLeft) d.left().get() else d.right().get().toString() }
              mBinding.run {
                feesLabel.visibility = View.VISIBLE
                feesValue.visibility = View.VISIBLE
                destinationLabel.visibility = View.VISIBLE
                destinationValue.visibility = View.VISIBLE
                showTechnicalsButton.visibility = View.VISIBLE
                feesValue.setAmount(fees)
                if (description.isBlank()) {
                  descValue.setTypeface(Typeface.DEFAULT, Typeface.ITALIC)
                  descValue.text = ctx.getString(R.string.paymentdetails_no_description)
                } else {
                  descValue.text = description
                }
              }
            } else if (head.externalId().isDefined && head.externalId().get().startsWith("closing-")) {
              mBinding.run {
                feesLabel.visibility = View.GONE
                feesValue.visibility = View.GONE
                destinationLabel.visibility = View.GONE
                destinationValue.visibility = View.GONE
                showTechnicalsButton.visibility = View.GONE
                descValue.text = ctx.getString(R.string.paymentdetails_closing_desc, head.externalId().get().split("-").last())
              }
            }
            mBinding.amountValue.setAmount(state.parts.first().recipientAmount())
            mBinding.statusText.text = Converter.html(ctx.getString(R.string.paymentdetails_status_sent_successful, Transcriber.relativeTime(ctx, statuses.map { s -> s.completedAt() }.max()!!)))
            showStatusIconAndDetails(ctx, if (args.fromEvent) R.drawable.ic_payment_success_animated else R.drawable.ic_payment_success_static, ThemeHelper.color(ctx, R.attr.positiveColor))
          }
          is PaymentDetailsState.Incoming -> {
            mBinding.run {
              feesLabel.visibility = View.GONE
              feesValue.visibility = View.GONE
              destinationLabel.visibility = View.GONE
              destinationValue.visibility = View.GONE
              showTechnicalsButton.visibility = View.VISIBLE
            }
            if (state.payment.status() is IncomingPaymentStatus.Received) {
              val status = state.payment.status() as IncomingPaymentStatus.Received
              val description = state.payment.paymentRequest().description().let { d -> if (d.isLeft) d.left().get() else d.right().get().toString() }
              mBinding.run {
                if (description.isBlank()) {
                  descValue.setTypeface(Typeface.DEFAULT, Typeface.ITALIC)
                  descValue.text = ctx.getString(R.string.paymentdetails_no_description)
                } else {
                  descValue.text = description
                }
                statusText.text = Converter.html(ctx.getString(R.string.paymentdetails_status_received_successful, Transcriber.relativeTime(ctx, status.receivedAt())))
                amountValue.setAmount(status.amount())
              }
              showStatusIconAndDetails(ctx, if (args.fromEvent) R.drawable.ic_payment_success_animated else R.drawable.ic_payment_success_static, ThemeHelper.color(ctx, R.attr.positiveColor))
            } else {
              mBinding.statusText.text = Converter.html(ctx.getString(R.string.paymentdetails_status_received_pending))
              mBinding.amountValue.setAmount(MilliSatoshi(0))
              showStatusIconAndDetails(ctx, R.drawable.ic_clock, ThemeHelper.color(ctx, R.attr.positiveColor))
            }
          }
        }
      }
    })

    getPayment(args.direction == OUTGOING, args.identifier)
    mBinding.model = model
  }

  override fun onStart() {
    super.onStart()
    mBinding.actionBar.setOnBackAction(View.OnClickListener { findNavController().popBackStack() })
    mBinding.showTechnicalsButton.setOnClickListener { findNavController().navigate(R.id.action_payment_details_to_payment_details_technicals) }
  }

  private fun animateBottomSection() {
    mBinding.bottomSection.apply {
      alpha = 0f
      visibility = View.VISIBLE
      translationY = 30f
      animate()
        .alpha(1f)
        .setStartDelay(300)
        .setInterpolator(DecelerateInterpolator())
        .translationY(0f)
        .setDuration(600)
        .setListener(null)
    }
  }

  private fun animateMidSection() {
    mBinding.midSection.apply {
      alpha = 0f
      visibility = View.VISIBLE
      translationY = 20f
      animate()
        .alpha(1f)
        .setStartDelay(100)
        .setInterpolator(DecelerateInterpolator())
        .translationY(0f)
        .setDuration(400)
        .setListener(null)
    }
  }

  private fun showStatusIconAndDetails(context: Context, drawableResId: Int, colorResId: Int) {
    mBinding.amountSeparator.backgroundTintList = ColorStateList.valueOf(colorResId)
    val statusDrawable = context.getDrawable(drawableResId)
    statusDrawable?.setTint(colorResId)
    mBinding.statusImage.setImageDrawable(statusDrawable)
    if (statusDrawable is Animatable) {
      statusDrawable.start()
    }
    if (args.fromEvent) {
      animateMidSection()
      animateBottomSection()
    } else {
      mBinding.midSection.visibility = View.VISIBLE
      mBinding.bottomSection.visibility = View.VISIBLE
    }
  }

  private fun getPayment(isSentPayment: Boolean, identifier: String) {
    lifecycleScope.launch(CoroutineExceptionHandler { _, exception ->
      log.error("error when retrieving payment from DB: ", exception)
      model.state.value = PaymentDetailsState.Error(exception.localizedMessage ?: "")
    }) {
      model.state.value = PaymentDetailsState.RetrievingDetails
      if (isSentPayment) {
        val payments: List<OutgoingPayment> = app.getSentPaymentsFromParentId(UUID.fromString(identifier))
        when {
          payments.any { p -> p.status() is OutgoingPaymentStatus.`Pending$` } -> {
            payments.filter { p -> p.status() is OutgoingPaymentStatus.`Pending$` }.also {
              model.state.value = PaymentDetailsState.Outgoing.Pending(it.first().paymentType(), it)
            }
          }
          payments.any { p -> p.status() is OutgoingPaymentStatus.Succeeded } -> {
            payments.filter { p -> p.status() is OutgoingPaymentStatus.Succeeded }.also {
              model.state.value = PaymentDetailsState.Outgoing.Succeeded(it.first().paymentType(), it)
            }
          }
          payments.any { p -> p.status() is OutgoingPaymentStatus.Failed } -> {
            payments.filter { p -> p.status() is OutgoingPaymentStatus.Failed }.also {
              model.state.value = PaymentDetailsState.Outgoing.Failed(it.first().paymentType(), it)
            }
          }
          else -> {
            log.warn("could not find any outgoing payments for id=$identifier, with result=$payments")
            model.state.value = PaymentDetailsState.Error("No details found for this outgoing payment")
          }
        }
      } else {
        val payment = app.getReceivedPayment(ByteVector32.fromValidHex(identifier))
        if (payment.isEmpty) {
          log.warn("could not find any incoming payments for id=$identifier")
          model.state.value = PaymentDetailsState.Error("No details found for this incoming payment")
        } else {
          model.state.value = PaymentDetailsState.Incoming(payment.get())
        }
      }
    }
  }
}

sealed class PaymentDetailsState {
  object Init : PaymentDetailsState()
  object RetrievingDetails : PaymentDetailsState()

  sealed class Outgoing : PaymentDetailsState() {
    abstract val paymentType: String
    abstract val parts: List<OutgoingPayment>

    data class Succeeded(override val paymentType: String, override val parts: List<OutgoingPayment>) : Outgoing()
    data class Failed(override val paymentType: String, override val parts: List<OutgoingPayment>) : Outgoing()
    data class Pending(override val paymentType: String, override val parts: List<OutgoingPayment>) : Outgoing()
  }

  class Incoming(val payment: IncomingPayment) : PaymentDetailsState()
  class Error(val cause: String) : PaymentDetailsState()
}

class PaymentDetailsViewModel : ViewModel() {
  private val log = LoggerFactory.getLogger(PaymentDetailsViewModel::class.java)
  val state = MutableLiveData<PaymentDetailsState>()

  init {
    state.value = PaymentDetailsState.Init
  }

  val destination: LiveData<String> = Transformations.map(state) {
    when (it) {
      is PaymentDetailsState.Outgoing -> it.parts.first().recipientNodeId().toString()
      else -> ""
    }
  }

  val paymentHash: LiveData<String> = Transformations.map(state) {
    when (it) {
      is PaymentDetailsState.Outgoing -> it.parts.first().paymentHash().toString()
      is PaymentDetailsState.Incoming -> it.payment.paymentRequest().paymentHash().toString()
      else -> ""
    }
  }

  val paymentRequest: LiveData<String> = Transformations.map(state) {
    when {
      it is PaymentDetailsState.Outgoing && it.parts.first().paymentRequest().isDefined -> PaymentRequest.write(it.parts.first().paymentRequest().get())
      it is PaymentDetailsState.Incoming -> PaymentRequest.write(it.payment.paymentRequest())
      else -> ""
    }
  }

  val preimage: LiveData<String> = Transformations.map(state) {
    when (it) {
      is PaymentDetailsState.Outgoing.Succeeded -> (it.parts.first().status() as OutgoingPaymentStatus.Succeeded).paymentPreimage().toString()
      is PaymentDetailsState.Incoming -> it.payment.paymentPreimage().toString()
      else -> ""
    }
  }

  val createdAt: LiveData<String> = Transformations.map(state) {
    when (it) {
      is PaymentDetailsState.Outgoing -> DateFormat.getDateTimeInstance().format(it.parts.first().createdAt())
      is PaymentDetailsState.Incoming -> DateFormat.getDateTimeInstance().format(it.payment.createdAt())
      else -> ""
    }
  }

  val completedAt: LiveData<String> = Transformations.map(state) {
    when (it) {
      is PaymentDetailsState.Outgoing.Succeeded -> (it.parts.first().status() as OutgoingPaymentStatus.Succeeded).completedAt()
      is PaymentDetailsState.Outgoing.Failed -> (it.parts.first().status() as OutgoingPaymentStatus.Failed).completedAt()
      is PaymentDetailsState.Outgoing.Pending -> it.parts.first().createdAt()
      is PaymentDetailsState.Incoming ->
        if (it.payment.status() is IncomingPaymentStatus.Received) {
          (it.payment.status() as IncomingPaymentStatus.Received).receivedAt()
        } else {
          null
        }
      else -> null
    }?.let { t -> DateFormat.getDateTimeInstance().format(t) } ?: ""
  }

  val expiredAt: LiveData<String> = Transformations.map(state) {
    when {
      it is PaymentDetailsState.Outgoing && it.parts.first().paymentRequest().isDefined -> {
        val pr = it.parts.first().paymentRequest().get()
        val expiry = (if (pr.expiry().isDefined) pr.expiry().get() else PaymentRequest.DEFAULT_EXPIRY_SECONDS().toLong()) as Long
        val timestamp = pr.timestamp()
        DateFormat.getDateTimeInstance().format(1000 * (timestamp + expiry))
      }
      it is PaymentDetailsState.Incoming -> {
        val expiry = (if (it.payment.paymentRequest().expiry().isDefined) it.payment.paymentRequest().expiry().get() else PaymentRequest.DEFAULT_EXPIRY_SECONDS().toLong()) as Long
        val timestamp = it.payment.paymentRequest().timestamp()
        DateFormat.getDateTimeInstance().format(1000 * (timestamp + expiry))
      }
      else -> ""
    }
  }

}
