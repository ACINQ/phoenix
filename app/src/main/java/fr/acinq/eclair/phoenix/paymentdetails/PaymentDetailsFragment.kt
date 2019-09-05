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

import android.graphics.drawable.Animatable
import android.os.Bundle
import android.text.Html
import android.text.format.DateUtils
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
import fr.acinq.eclair.db.OutgoingPayment
import fr.acinq.eclair.db.`OutgoingPaymentStatus$`
import fr.acinq.eclair.db.`PaymentDirection$`
import fr.acinq.eclair.payment.PaymentRequest
import fr.acinq.eclair.phoenix.BaseFragment
import fr.acinq.eclair.phoenix.R
import fr.acinq.eclair.phoenix.databinding.FragmentPaymentDetailsBinding
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.launch
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import scala.util.Either
import scala.util.Left
import scala.util.Right
import java.text.DateFormat
import java.util.*
import kotlin.math.abs

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
            //
            // ========= OUTGOING PAYMENT
            //
            it.isLeft -> {
              val p = it.left().get()
              when (p.status()) {
                // ========= OUTGOING PAYMENT FAILED
                `OutgoingPaymentStatus$`.`MODULE$`.FAILED() -> {
                  mBinding.statusText.text = Html.fromHtml(ctx.getString(R.string.paymentdetails_status_sent_failed))

                  val statusDrawable = ctx.getDrawable(R.drawable.ic_cross)
                  statusDrawable?.setTint(ctx.getColor(R.color.red))
                  mBinding.statusImage.setImageDrawable(statusDrawable)
                  if (statusDrawable is Animatable) {
                    statusDrawable.start()
                  }
                }
                // ========= OUTGOING PAYMENT PENDING
                `OutgoingPaymentStatus$`.`MODULE$`.PENDING() -> {
                  mBinding.statusText.text = Html.fromHtml(ctx.getString(R.string.paymentdetails_status_sent_pending))
                }
                // ========= OUTGOING PAYMENT SUCCESS
                `OutgoingPaymentStatus$`.`MODULE$`.SUCCEEDED() -> {

                  val relativeCompletedAt = if (p.completedAt().isDefined) {
                    val completedAt: Long = p.completedAt().get() as Long
                    val delaySincePayment: Long = completedAt - System.currentTimeMillis()
                    if (abs(delaySincePayment) < 60 * 1000L) {
                      ctx.getString(R.string.utils_date_just_now)
                    } else {
                      DateUtils.getRelativeTimeSpanString(completedAt, System.currentTimeMillis(), delaySincePayment)
                    }
                  } else {
                    ctx.getString(R.string.utils_unknown)
                  }
                  mBinding.statusText.text = Html.fromHtml(ctx.getString(R.string.paymentdetails_status_sent_successful, relativeCompletedAt))

                  val statusDrawable = ctx.getDrawable(if (args.fromEvent) R.drawable.ic_payment_success_animated else R.drawable.ic_payment_success_static)
                  statusDrawable?.setTint(ctx.getColor(R.color.green))
                  mBinding.statusImage.setImageDrawable(statusDrawable)
                  if (statusDrawable is Animatable) {
                    statusDrawable.start()
                  }

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
              }
              mBinding.amountValue.setAmount(p.amount())
            }
            //
            // ========= INCOMING PAYMENT
            //
            it.isRight -> {
              val p = it.right().get()
              if (p.amount_opt().isDefined) {
                //                mBinding.amountLabel.text = ctx.getString(R.string.paymentdetails_amount_received_successful_label)
                val receivedAt = if (p.receivedAt_opt().isDefined) DateFormat.getDateTimeInstance().format(p.receivedAt_opt().get()) else context!!.getString(R.string.utils_unknown)
                mBinding.statusText.text = Html.fromHtml(ctx.getString(R.string.paymentdetails_status_received_successful, receivedAt))
                mBinding.amountValue.setAmount(p.amount_opt().get())
              } else {
                //                mBinding.amountLabel.text = ctx.getString(R.string.paymentdetails_amount_received_pending_label)
                mBinding.statusText.text = Html.fromHtml(ctx.getString(R.string.paymentdetails_status_received_pending))
                mBinding.amountValue.setAmount(MilliSatoshi(0))
              }
            }
          }
        }
      }
    })

    getPayment(args.direction == `PaymentDirection$`.`MODULE$`.OUTGOING().toString(), args.identifier)
    mBinding.model = model
  }

  override fun onStart() {
    super.onStart()
    mBinding.actionBar.setOnBackAction(View.OnClickListener { findNavController().popBackStack() })
    mBinding.showTechnicalsButton.setOnClickListener { findNavController().navigate(R.id.action_payment_details_to_payment_details_technicals) }
  }

  private fun getPayment(isSentPayment: Boolean, identifier: String) {
    lifecycleScope.launch(CoroutineExceptionHandler { _, exception ->
      log.error("error when retrieving payment from DB: ", exception)
      model.state.value = PaymentDetailsState.ERROR
    }) {
      model.state.value = PaymentDetailsState.RETRIEVING_PAYMENT_DATA
      if (isSentPayment) {
        val p = appKit.getSentPayment(UUID.fromString(identifier))
        if (p.isDefined) {
          model.payment.value = Left.apply(p.get())
        } else {
          model.payment.value = null
        }
      } else {
        val p = appKit.getReceivedPayment(ByteVector32.fromValidHex(identifier))
        if (p.isDefined) {
          model.payment.value = Right.apply(p.get())
        } else {
          model.payment.value = null
        }
      }
      model.state.value = PaymentDetailsState.DONE
    }
  }
}

enum class PaymentDetailsState {
  INIT, RETRIEVING_PAYMENT_DATA, DONE, ERROR
}

class PaymentDetailsViewModel : ViewModel() {
  private val log = LoggerFactory.getLogger(PaymentDetailsViewModel::class.java)

  val payment = MutableLiveData<Either<OutgoingPayment, IncomingPayment>>(null)
  val state = MutableLiveData(PaymentDetailsState.INIT)

  val description: LiveData<String> = Transformations.map(payment) {
    it?.let {
      when {
        it.isLeft && it.left().get().paymentRequest_opt().isDefined -> {
          PaymentRequest.readDescription(it.left().get().paymentRequest_opt().get())
        }
        it.isRight && it.right().get().paymentRequest_opt().isDefined -> {
          PaymentRequest.readDescription(it.right().get().paymentRequest_opt().get())
        }
        else -> ""
      }
    } ?: ""
  }

  val destination: LiveData<String> = Transformations.map(payment) {
    it?.let {
      when {
        it.isLeft && it.left().get().targetNodeId().isDefined -> {
          it.left().get().targetNodeId().get().toString()
        }
        else -> ""
      }
    } ?: ""
  }

  val paymentHash: LiveData<String> = Transformations.map(payment) {
    it?.let {
      when {
        it.isLeft -> it.left().get().paymentHash().toString()
        else -> it.right().get().paymentHash().toString()
      }
    } ?: ""
  }

  val paymentRequest: LiveData<String> = Transformations.map(payment) {
    it?.let {
      when {
        it.isLeft && it.left().get().paymentRequest_opt().isDefined -> it.left().get().paymentRequest_opt().get()
        it.isRight && it.right().get().paymentRequest_opt().isDefined -> it.right().get().paymentRequest_opt().get()
        else -> ""
      }
    } ?: ""
  }

  val preimage: LiveData<String> = Transformations.map(payment) {
    it?.let {
      when {
        it.isRight && it.right().get().preimage_opt().isDefined -> {
          it.right().get().preimage_opt().get().toString()
        }
        else -> ""
      }
    } ?: ""
  }

  val createdAt: LiveData<String> = Transformations.map(payment) {
    it?.let {
      when {
        it.isLeft -> DateFormat.getDateTimeInstance().format(it.left().get().createdAt())
        it.isRight -> DateFormat.getDateTimeInstance().format(it.right().get().createdAt())
        else -> ""
      }
    } ?: ""
  }

  val completedAt: LiveData<String> = Transformations.map(payment) {
    it?.let {
      when {
        it.isLeft && it.left().get().completedAt().isDefined -> DateFormat.getDateTimeInstance().format(it.left().get().completedAt().get())
        it.isRight && it.right().get().receivedAt_opt().isDefined -> DateFormat.getDateTimeInstance().format(it.right().get().receivedAt_opt().get())
        else -> ""
      }
    } ?: ""
  }

  val expiredAt: LiveData<String> = Transformations.map(payment) {
    it?.let {
      when {
        it.isRight && it.right().get().expireAt_opt().isDefined -> DateFormat.getDateTimeInstance().format(it.right().get().expireAt_opt().get())
        else -> ""
      }
    } ?: ""
  }

  val isSent: LiveData<Boolean> = Transformations.map(payment) { it?.isLeft ?: false }

}
