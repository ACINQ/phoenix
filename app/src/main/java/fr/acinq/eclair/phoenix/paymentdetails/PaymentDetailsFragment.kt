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

package fr.acinq.eclair.phoenix.paymentdetails

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
import fr.acinq.eclair.db.*
import fr.acinq.eclair.payment.PaymentRequest
import fr.acinq.eclair.phoenix.BaseFragment
import fr.acinq.eclair.phoenix.R
import fr.acinq.eclair.phoenix.databinding.FragmentPaymentDetailsBinding
import fr.acinq.eclair.phoenix.utils.Converter
import fr.acinq.eclair.phoenix.utils.Transcriber
import fr.acinq.eclair.phoenix.utils.Wallet
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.launch
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.spongycastle.crypto.tls.ContentType
import scala.util.Either
import scala.util.Left
import scala.util.Right
import java.text.DateFormat
import java.util.*

class PaymentDetailsFragment : BaseFragment() {

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
                  showStatusIconAndDetails(R.drawable.ic_cross, R.color.red)
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
                }
                is OutgoingPaymentStatus.Succeeded -> {
                  val status = p.status() as OutgoingPaymentStatus.Succeeded
                  mBinding.statusText.text = Converter.html(ctx.getString(R.string.paymentdetails_status_sent_successful, Transcriber.relativeTime(ctx, status.completedAt())))
                  val statuses: List<OutgoingPaymentStatus.Succeeded> = payments.filter { o -> o.status() is OutgoingPaymentStatus.Succeeded }.map { o -> o.status() as OutgoingPaymentStatus.Succeeded }
                  val fees = MilliSatoshi(statuses.map { o -> o.feesPaid().toLong() }.sum())

//                  val isTrampoline = p.paymentRequest().isDefined && statuses.map { o -> JavaConverters.seqAsJavaListConverter(o.route()).asJava().size == 1 }.reduce { acc, b -> acc && b }
                  if (p.paymentRequest().isDefined) {
                    val trampolineData = Wallet.getTrampoline(p.amount(), p.paymentRequest().get())
                    val finalFees = fees.`$plus`(trampolineData.second)
                    mBinding.feesValue.setAmount(finalFees)
                  } else if (p.externalId().isDefined && p.externalId().get().startsWith("closing-")) {
                    // special case: this outgoing payment represents a channel closing/closed
                    mBinding.closingDescValue.text = getString(R.string.paymentdetails_closing_mock_desc, p.externalId().get().split("-").last())
                  }
                  showStatusIconAndDetails(if (args.fromEvent) R.drawable.ic_payment_success_animated else R.drawable.ic_payment_success_static, R.color.green)
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
                showStatusIconAndDetails(if (args.fromEvent) R.drawable.ic_payment_success_animated else R.drawable.ic_payment_success_static, R.color.green)
              } else {
                mBinding.statusText.text = Converter.html(ctx.getString(R.string.paymentdetails_status_received_pending))
                mBinding.amountValue.setAmount(MilliSatoshi(0))
                showStatusIconAndDetails(R.drawable.ic_clock, R.color.green)
              }
            }
          }
        }
      }
    })

    getPayment(args.direction == PaymentDirection.`OutgoingPaymentDirection$`.`MODULE$`.toString(), args.identifier)
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

  private fun showStatusIconAndDetails(drawableResId: Int, colorResId: Int) {
    mBinding.amountSeparator.backgroundTintList = ColorStateList.valueOf(resources.getColor(colorResId))
    val statusDrawable = resources.getDrawable(drawableResId, context?.theme)
    statusDrawable?.setTint(resources.getColor(colorResId))
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
        val payments: List<OutgoingPayment> = if (args.fromEvent) {
          // we don't know parent id, identifier is payment id in base
          val payment = appKit.getSentPaymentFromId(UUID.fromString(identifier))
          if (payment.isDefined) listOf(payment.get()) else ArrayList()
        } else {
          appKit.getSentPaymentsFromParentId(UUID.fromString(identifier))
        }
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
      DateFormat.getDateTimeInstance().format(it.right().get().paymentRequest().expiry().get())
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
