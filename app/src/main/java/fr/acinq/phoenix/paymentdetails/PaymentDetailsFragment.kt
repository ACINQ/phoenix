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
import scala.util.Either
import scala.util.Left
import scala.util.Right
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
    model.payment.observe(viewLifecycleOwner, Observer {
      it?.let {
        context?.let { ctx ->
          when {
            // OUTGOING PAYMENT
            it.isLeft && it.left().get().isNotEmpty() -> {
              val payments = it.left().get()
              val p = it.left().get().first()
              when (p.status()) {
                is OutgoingPaymentStatus.Failed -> {
                  mBinding.statusText.text = Converter.html(ctx.getString(R.string.paymentdetails_status_sent_failed))
                  showStatusIconAndDetails(ctx, R.drawable.ic_cross, ThemeHelper.color(ctx, R.attr.negativeColor))
                  val status = p.status() as OutgoingPaymentStatus.Failed
                  val errorMessages = ArrayList<String>()
                  val iterator = status.failures().iterator()
                  while (iterator.hasNext()) {
                    errorMessages += iterator.next().failureMessage()
                  }
                  mBinding.errorValue.text = errorMessages.joinToString(", ")
                }
                is OutgoingPaymentStatus.`Pending$` -> {
                  mBinding.statusText.text = Converter.html(ctx.getString(R.string.paymentdetails_status_sent_pending))
                  showStatusIconAndDetails(ctx, R.drawable.ic_send_lg, ThemeHelper.color(ctx, R.attr.mutedTextColor))
                }
                is OutgoingPaymentStatus.Succeeded -> {
                  val status = p.status() as OutgoingPaymentStatus.Succeeded
                  mBinding.statusText.text = Converter.html(ctx.getString(R.string.paymentdetails_status_sent_successful, Transcriber.relativeTime(ctx, status.completedAt())))
                  val statuses: List<OutgoingPaymentStatus.Succeeded> =
                    payments.filter { o -> o.status() is OutgoingPaymentStatus.Succeeded }.map { o -> o.status() as OutgoingPaymentStatus.Succeeded }
                  val fees = MilliSatoshi(statuses.map { o -> o.feesPaid().toLong() }.sum())

                  if (p.paymentRequest().isDefined) {
                    mBinding.feesValue.setAmount(fees)
                  } else if (p.externalId().isDefined && p.externalId().get().startsWith("closing-")) {
                    // special case: this outgoing payment represents a channel closing/closed
                    mBinding.closingDescValue.text = getString(R.string.paymentdetails_closing_mock_desc, p.externalId().get().split("-").last())
                  }
                  showStatusIconAndDetails(ctx, if (args.fromEvent) R.drawable.ic_payment_success_animated else R.drawable.ic_payment_success_static, ThemeHelper.color(ctx, R.attr.positiveColor))
                }
              }
              mBinding.amountValue.setAmount(MilliSatoshi(payments.map { o -> o.amount().toLong() }.sum()))
            }
            // INCOMING PAYMENT
            it.isRight -> {
              val p = it.right().get()
              if (p.status() is IncomingPaymentStatus.Received) {
                val status = p.status() as IncomingPaymentStatus.Received
                mBinding.statusText.text = Converter.html(ctx.getString(R.string.paymentdetails_status_received_successful, Transcriber.relativeTime(ctx, status.receivedAt())))
                mBinding.amountValue.setAmount(status.amount())
                showStatusIconAndDetails(ctx, if (args.fromEvent) R.drawable.ic_payment_success_animated else R.drawable.ic_payment_success_static, ThemeHelper.color(ctx, R.attr.positiveColor))
              } else {
                mBinding.statusText.text = Converter.html(ctx.getString(R.string.paymentdetails_status_received_pending))
                mBinding.amountValue.setAmount(MilliSatoshi(0))
                showStatusIconAndDetails(ctx, R.drawable.ic_clock, ThemeHelper.color(ctx, R.attr.positiveColor))
              }
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
      model.state.value = PaymentDetailsState.ERROR
    }) {
      model.state.value = PaymentDetailsState.RETRIEVING_PAYMENT_DATA
      if (isSentPayment) {
        val payments: List<OutgoingPayment> = appKit.getSentPaymentsFromParentId(UUID.fromString(identifier))
        if (payments.isEmpty()) {
          model.payment.value = null
          model.state.value = PaymentDetailsState.NONE
        } else {
          model.payment.value = Left.apply(payments)
          model.state.value = PaymentDetailsState.DONE
        }
      } else {
        val p = appKit.getReceivedPayment(ByteVector32.fromValidHex(identifier))
        if (p.isDefined) {
          model.payment.value = Right.apply(p.get())
          model.state.value = PaymentDetailsState.DONE
        } else {
          model.payment.value = null
          model.state.value = PaymentDetailsState.NONE
        }
      }
    }
  }
}

enum class PaymentDetailsState {
  INIT, RETRIEVING_PAYMENT_DATA, DONE, ERROR, NONE
}

class PaymentDetailsViewModel : ViewModel() {
  private val log = LoggerFactory.getLogger(PaymentDetailsViewModel::class.java)

  val payment = MutableLiveData<Either<List<OutgoingPayment>, IncomingPayment>>(null)
  val state = MutableLiveData(PaymentDetailsState.INIT)

  val description: LiveData<String> = Transformations.map(payment) {
    it?.let {
      when {
        it.isLeft && it.left().get().isNotEmpty() && it.left().get().first().paymentRequest().isDefined -> it.left().get().first().paymentRequest().get().description().left().get()
        it.isRight -> it.right().get().paymentRequest().description().left().get()
        else -> ""
      }
    } ?: ""
  }

  val destination: LiveData<String> = Transformations.map(payment) {
    it?.let {
      when {
        it.isLeft && it.left().get().isNotEmpty() -> it.left().get().first().targetNodeId().toString()
        else -> ""
      }
    } ?: ""
  }

  val paymentHash: LiveData<String> = Transformations.map(payment) {
    it?.let {
      when {
        it.isLeft && it.left().get().isNotEmpty() -> it.left().get().first().paymentHash().toString()
        else -> it.right().get().paymentRequest().paymentHash().toString()
      }
    } ?: ""
  }

  val paymentRequest: LiveData<String> = Transformations.map(payment) {
    it?.let {
      when {
        it.isLeft && it.left().get().isNotEmpty() && it.left().get().first().paymentRequest().isDefined -> PaymentRequest.write(it.left().get().first().paymentRequest().get())
        it.isRight -> PaymentRequest.write(it.right().get().paymentRequest())
        else -> ""
      }
    } ?: ""
  }

  val preimage: LiveData<String> = Transformations.map(payment) {
    it?.let {
      when {
        it.isLeft && it.left().get().isNotEmpty() && it.left().get()[0].status() is OutgoingPaymentStatus.Succeeded -> (it.left().get()[0].status() as OutgoingPaymentStatus.Succeeded).paymentPreimage().toString()
        it.isRight -> it.right().get().paymentPreimage().toString()
        else -> ""
      }
    } ?: ""
  }

  val createdAt: LiveData<String> = Transformations.map(payment) {
    it?.let {
      when {
        it.isLeft && it.left().get().isNotEmpty() -> DateFormat.getDateTimeInstance().format(it.left().get().first().createdAt())
        it.isRight -> DateFormat.getDateTimeInstance().format(it.right().get().createdAt())
        else -> ""
      }
    } ?: ""
  }

  val completedAt: LiveData<String> = Transformations.map(payment) {
    it?.let {
      when {
        it.isLeft && it.left().get().isNotEmpty() && it.left().get().first().status() is OutgoingPaymentStatus.Succeeded -> {
          val status = it.left().get().first().status() as OutgoingPaymentStatus.Succeeded
          DateFormat.getDateTimeInstance().format(status.completedAt())
        }
        it.isLeft && it.left().get().isNotEmpty() && it.isLeft && it.left().get().first().status() is OutgoingPaymentStatus.Failed -> {
          val status = it.left().get().first().status() as OutgoingPaymentStatus.Failed
          DateFormat.getDateTimeInstance().format(status.completedAt())
        }
        it.isLeft && it.left().get().isNotEmpty() -> DateFormat.getDateTimeInstance().format(it.left().get().first().createdAt())
        it.isRight && it.right().get().status() is IncomingPaymentStatus.Received -> {
          val status = it.right().get().status() as IncomingPaymentStatus.Received
          DateFormat.getDateTimeInstance().format(status.receivedAt())
        }
        else -> ""
      }
    } ?: ""
  }

  val expiredAt: LiveData<String> = Transformations.map(payment) {
    if (it != null && it.isRight && it.right().get().paymentRequest().expiry().isDefined) {
      val expiry = it.right().get().paymentRequest().expiry().get() as Long
      val timestamp = it.right().get().paymentRequest().timestamp()
      DateFormat.getDateTimeInstance().format(1000 * (timestamp + expiry))
    } else {
      ""
    }
  }

  val isFeeVisible: LiveData<Boolean> = Transformations.map(payment) {
    it != null && it.isLeft && it.left().get().isNotEmpty() && it.left().get().first().status() is OutgoingPaymentStatus.Succeeded
  }

  val isErrorVisible: LiveData<Boolean> = Transformations.map(payment) {
    it != null && it.isLeft && it.left().get().isNotEmpty() && it.left().get().first().status() is OutgoingPaymentStatus.Failed
  }

  val multiPartCount: LiveData<Int> = Transformations.map(payment) {
    it?.let {
      when {
        it.isLeft -> it.left().get().size
        else -> 1
      }
    } ?: 0
  }

  val isSent: LiveData<Boolean> = Transformations.map(payment) { it?.isLeft ?: false }

  val isClosingChannelMock: LiveData<Boolean> = Transformations.map(payment) {
    it?.let {
      it.isLeft && it.left().get().isNotEmpty() && it.left().get().first().paymentRequest().isEmpty
        && it.left().get().first().externalId().isDefined && it.left().get().first().externalId().get().startsWith("closing-")
    } ?: false
  }

}
