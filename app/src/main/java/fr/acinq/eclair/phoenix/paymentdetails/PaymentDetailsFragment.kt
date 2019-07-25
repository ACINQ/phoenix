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

import android.os.Bundle
import android.text.Html
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.lifecycle.*
import androidx.lifecycle.Observer
import androidx.navigation.fragment.navArgs
import fr.acinq.bitcoin.ByteVector32
import fr.acinq.bitcoin.MilliSatoshi
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
import org.slf4j.LoggerFactory
import scala.util.Either
import scala.util.Left
import scala.util.Right
import java.text.DateFormat
import java.util.*

class PaymentDetailsFragment : BaseFragment() {

  private lateinit var mBinding: FragmentPaymentDetailsBinding
  private lateinit var model: PaymentDetailsViewModel
  private val args: PaymentDetailsFragmentArgs by navArgs()

  override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
    mBinding = FragmentPaymentDetailsBinding.inflate(inflater, container, false)
    mBinding.lifecycleOwner = this
    return mBinding.root
  }

  override fun onActivityCreated(savedInstanceState: Bundle?) {
    super.onActivityCreated(savedInstanceState)
    model = ViewModelProviders.of(this).get(PaymentDetailsViewModel::class.java)

    model.payment.observe(viewLifecycleOwner, Observer {
      it?.let {
        if (context != null) {
          when {
            it.isLeft -> {
              val p = it.left().get()
              when (p.status()) {
                `OutgoingPaymentStatus$`.`MODULE$`.FAILED() -> {
                  mBinding.amountLabel.text = context!!.getString(R.string.paymentdetails_amount_sent_failed_label)
                  mBinding.statusText.text = Html.fromHtml(context!!.getString(R.string.paymentdetails_status_sent_failed))
                }
                `OutgoingPaymentStatus$`.`MODULE$`.PENDING() -> {
                  mBinding.amountLabel.text = context!!.getString(R.string.paymentdetails_amount_sent_pending_label)
                  mBinding.statusText.text = Html.fromHtml(context!!.getString(R.string.paymentdetails_status_sent_pending, DateFormat.getDateTimeInstance().format(p.createdAt())))
                }
                `OutgoingPaymentStatus$`.`MODULE$`.SUCCEEDED() -> {
                  mBinding.amountLabel.text = context!!.getString(R.string.paymentdetails_amount_sent_successful_label)
                  val completedAtDate = if (p.completedAt().isDefined) DateFormat.getDateTimeInstance().format(p.completedAt().get()) else context!!.getString(R.string.utils_unknown)
                  mBinding.statusText.text = Html.fromHtml(context!!.getString(R.string.paymentdetails_status_sent_successful, completedAtDate))
                }
              }
              mBinding.amountValue.setAmount(MilliSatoshi(p.amountMsat()))
            }
            it.isRight-> {
              val p = it.right().get()
              if (p.amountMsat_opt().isDefined) {
                mBinding.amountLabel.text = context!!.getString(R.string.paymentdetails_amount_received_successful_label)
                val receivedAt = if (p.receivedAt_opt().isDefined) DateFormat.getDateTimeInstance().format(p.receivedAt_opt().get()) else context!!.getString(R.string.utils_unknown)
                mBinding.statusText.text = Html.fromHtml(context!!.getString(R.string.paymentdetails_status_received_successful, receivedAt))
                mBinding.amountValue.setAmount(MilliSatoshi(p.amountMsat_opt().get() as Long))
              } else {
                mBinding.amountLabel.text = context!!.getString(R.string.paymentdetails_amount_received_pending_label)
                mBinding.statusText.text = Html.fromHtml(context!!.getString(R.string.paymentdetails_status_received_pending))
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

  val isSent: LiveData<Boolean> = Transformations.map(payment) { it?.isLeft ?: false }

}
