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
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.drawable.Animatable
import android.net.Uri
import android.os.Bundle
import android.text.InputType
import android.text.method.LinkMovementMethod
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import androidx.core.content.res.ResourcesCompat
import androidx.lifecycle.*
import androidx.lifecycle.Observer
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import androidx.navigation.navGraphViewModels
import fr.acinq.bitcoin.ByteVector32
import fr.acinq.bitcoin.Satoshi
import fr.acinq.eclair.MilliSatoshi
import fr.acinq.eclair.db.IncomingPayment
import fr.acinq.eclair.db.IncomingPaymentStatus
import fr.acinq.eclair.db.OutgoingPayment
import fr.acinq.eclair.db.OutgoingPaymentStatus
import fr.acinq.eclair.payment.PaymentRequest
import fr.acinq.phoenix.BaseFragment
import fr.acinq.phoenix.R
import fr.acinq.phoenix.databinding.FragmentPaymentDetailsBinding
import fr.acinq.phoenix.db.*
import fr.acinq.phoenix.utils.*
import fr.acinq.phoenix.utils.Wallet.simpleExecute
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import scala.Option
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
  private val args: PaymentDetailsFragmentArgs by navArgs()

  // shared view model, living with payment details nested graph
  private val model: PaymentDetailsViewModel by navGraphViewModels(R.id.nav_graph_payment_details) {
    val appContext = appContext(requireContext())
    AppDb.getInstance(appContext.applicationContext).run {
      PaymentDetailsViewModel.Factory(appContext.applicationContext, args.identifier, PaymentMetaRepository.getInstance(paymentMetaQueries),
        PayToOpenMetaRepository.getInstance(payToOpenMetaQueries), NodeMetaRepository.getInstance(nodeMetaQueries))
    }
  }

  override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
    mBinding = FragmentPaymentDetailsBinding.inflate(inflater, container, false)
    mBinding.lifecycleOwner = this
    return mBinding.root
  }

  override fun onActivityCreated(savedInstanceState: Bundle?) {
    super.onActivityCreated(savedInstanceState)
    mBinding.infoBody.movementMethod = LinkMovementMethod.getInstance()
    if (args.fromEvent) {
      mBinding.mainLayout.layoutTransition = null
    }
    model.payToOpenMeta.observe(viewLifecycleOwner, Observer {
      if (it != null) {
        mBinding.payToOpenFeesValue.setAmount(Satoshi(it.fee_sat))
      }
    })
    model.state.observe(viewLifecycleOwner, Observer { state ->
      when (state) {
        is PaymentDetailsState.Outgoing.Pending -> {
          mBinding.statusText.text = Converter.html(getString(R.string.paymentdetails_status_sent_pending))
          mBinding.amountValue.setAmount(state.amountToRecipient)
          showStatusIconAndDetails(R.drawable.ic_send_lg, R.attr.mutedTextColor)
        }
        is PaymentDetailsState.Outgoing.Failed -> {
          mBinding.statusText.text = Converter.html(getString(R.string.paymentdetails_status_sent_failed))
          // use error of the last subpayment as it's probably the most pertinent
          val failures = JavaConverters.asJavaCollectionConverter((state.parts.last().status() as OutgoingPaymentStatus.Failed).failures()).asJavaCollection().toList()
          mBinding.errorValue.text = failures.joinToString("\n") { e -> e.failureMessage() }
          mBinding.amountValue.setAmount(state.parts.last().recipientAmount())
          showStatusIconAndDetails(R.drawable.ic_cross, R.attr.negativeColor)
        }
        is PaymentDetailsState.Outgoing.Sent -> {
          mBinding.statusText.text = Converter.html(getString(R.string.paymentdetails_status_sent_successful, Transcriber.relativeTime(requireContext(), state.completedAt)))
          mBinding.feesValue.setAmount(state.fees)
          mBinding.amountValue.setAmount(state.amountToRecipient)
          showStatusIconAndDetails(if (args.fromEvent) R.drawable.ic_payment_success_animated else R.drawable.ic_payment_success_static, R.attr.positiveColor)
          if (state is PaymentDetailsState.Outgoing.Sent.Closing) {
            model.paymentMeta.value?.run {
              if (closing_type != ClosingType.Mutual.code) {
                log.info("show closing info= $this")
                mBinding.infoLayout.visibility = View.VISIBLE
                val address = app.state.value?.getFinalAddress() ?: closing_main_output_script ?: ""
                mBinding.infoBody.text = Converter.html(getString(R.string.paymentdetails_info_uniclose, address))
              }
            }
          }
        }
        is PaymentDetailsState.Incoming.Pending -> {
          mBinding.statusText.text = Converter.html(getString(R.string.paymentdetails_status_received_pending))
          mBinding.amountValue.setAmount(MilliSatoshi(0))
          showStatusIconAndDetails(R.drawable.ic_clock, R.attr.positiveColor)
        }
        is PaymentDetailsState.Incoming.Received -> {
          mBinding.statusText.text = Converter.html(getString(R.string.paymentdetails_status_received_successful, Transcriber.relativeTime(requireContext(), state.getStatus().receivedAt())))
          mBinding.amountValue.setAmount(state.getStatus().amount())
          showStatusIconAndDetails(if (args.fromEvent) R.drawable.ic_payment_success_animated else R.drawable.ic_payment_success_static, R.attr.positiveColor)
        }
      }
    })

    getPaymentDetails()
    mBinding.model = model
  }

  override fun onStart() {
    super.onStart()
    mBinding.customDescButton.setOnClickListener { handleEdit() }
    mBinding.customDescValue.setOnClickListener { handleEdit() }
    mBinding.actionBar.setOnBackAction { findNavController().popBackStack() }
    mBinding.showTechnicalsButton.setOnClickListener { findNavController().navigate(R.id.action_payment_details_to_payment_details_technicals) }
    mBinding.onchainAddressExplorerButton.setOnClickListener {
      model.onchainAddress.value?.let {
        val uri = "${Prefs.getExplorer(context)}/address/$it"
        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(uri)))
      }
    }
  }

  private fun handleEdit() {
    AlertHelper.buildWithInput(inflater = layoutInflater, title = getString(R.string.paymentdetails_desc_custom_title),
      message = getString(R.string.paymentdetails_desc_custom_info),
      defaultValue = model.paymentMeta.value?.custom_desc ?: "",
      callback = { newDescription -> model.saveCustomDescription(newDescription) },
      inputType = InputType.TYPE_TEXT_FLAG_MULTI_LINE)
      .setNegativeButton(getString(R.string.btn_cancel), null)
      .show()
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
    val color = context?.let { ThemeHelper.color(it, colorResId) }
    color?.let { mBinding.amountSeparator.backgroundTintList = ColorStateList.valueOf(color) }
    ResourcesCompat.getDrawable(resources, drawableResId, context?.theme)?.apply {
      color?.let { setTint(color) }
      if (this is Animatable) {
        start()
      }
    }.also { mBinding.statusImage.setImageDrawable(it) }
    if (args.fromEvent) {
      animateMidSection()
      animateBottomSection()
    } else {
      mBinding.midSection.visibility = View.VISIBLE
      mBinding.bottomSection.visibility = View.VISIBLE
    }
  }

  private fun getPaymentDetails() {
    val identifier = args.identifier
    lifecycleScope.launch(Dispatchers.Main + CoroutineExceptionHandler { _, exception ->
      log.error("error when retrieving payment from DB: ", exception)
      model.state.value = PaymentDetailsState.Error.Generic
    }) {
      model.state.value = PaymentDetailsState.RetrievingDetails
      model.getPaymentMeta()
      if (args.direction == OUTGOING) {
        val payments: List<OutgoingPayment> = app.service?.getSentPaymentsFromParentId(UUID.fromString(identifier)) ?: emptyList()
        model.buildOutgoing(payments)
      } else {
        val payment = app.service?.getReceivedPayment(ByteVector32.fromValidHex(identifier)) ?: Option.empty()
        model.buildIncoming(payment)
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
    abstract val amountToRecipient: MilliSatoshi
    abstract val description: String?

    sealed class Sent : Outgoing() {
      abstract val fees: MilliSatoshi
      abstract val completedAt: Long

      data class Normal(override val paymentType: String, override val parts: List<OutgoingPayment>, override val description: String?,
        override val amountToRecipient: MilliSatoshi, override val fees: MilliSatoshi, override val completedAt: Long) : Sent()

      data class SwapOut(override val paymentType: String, override val parts: List<OutgoingPayment>, override val description: String?,
        override val amountToRecipient: MilliSatoshi, override val fees: MilliSatoshi, override val completedAt: Long, val feeratePerByte: Long) : Sent()

      data class Closing(override val paymentType: String, override val parts: List<OutgoingPayment>, override val description: String?,
        override val amountToRecipient: MilliSatoshi, override val fees: MilliSatoshi, override val completedAt: Long) : Sent()
    }

    data class Failed(override val paymentType: String, override val parts: List<OutgoingPayment>, override val description: String?,
      override val amountToRecipient: MilliSatoshi) : Outgoing()

    data class Pending(override val paymentType: String, override val parts: List<OutgoingPayment>, override val description: String?,
      override val amountToRecipient: MilliSatoshi) : Outgoing()
  }

  sealed class Incoming : PaymentDetailsState() {
    abstract val payment: IncomingPayment

    data class Pending(override val payment: IncomingPayment) : Incoming()
    sealed class Received : Incoming() {
      data class Normal(override val payment: IncomingPayment) : Received()
      data class SwapIn(override val payment: IncomingPayment) : Received()

      fun getStatus(): IncomingPaymentStatus.Received = payment.status() as IncomingPaymentStatus.Received
      fun getDescription(): String = payment.paymentRequest().description().let { d -> if (d.isLeft) d.left().get() else d.right().get().toString() }
    }
  }

  sealed class Error : PaymentDetailsState() {
    object Generic : Error()
    object NotFound : Error()
  }
}

class PaymentDetailsViewModel(
  private val appContext: Context,
  private val paymentId: String,
  private val paymentMetaRepository: PaymentMetaRepository,
  private val payToOpenMetaRepository: PayToOpenMetaRepository,
  private val nodeMetaRepository: NodeMetaRepository
) : ViewModel() {

  private val log = LoggerFactory.getLogger(this::class.java)
  val state = MutableLiveData<PaymentDetailsState>()
  val recipientMeta = MutableLiveData<NodeMeta>()
  val paymentMeta = MutableLiveData<PaymentMeta>()
  val payToOpenMeta = MutableLiveData<PayToOpenMeta>()

  init {
    state.value = PaymentDetailsState.Init
  }

  val onchainAddress: LiveData<String?> = Transformations.map(paymentMeta) {
    it?.swap_in_address ?: it?.swap_out_address ?: it?.closing_main_output_script
  }

  val closingType: LiveData<String> = Transformations.map(paymentMeta) {
    when (it?.closing_type) {
      ClosingType.Mutual.code -> appContext.getString(R.string.paymentdetails_closing_type_mutual)
      ClosingType.Local.code -> appContext.getString(R.string.paymentdetails_closing_type_local)
      ClosingType.Remote.code -> appContext.getString(R.string.paymentdetails_closing_type_remote)
      ClosingType.Other.code -> appContext.getString(R.string.paymentdetails_closing_type_other)
      else -> ""
    }
  }

  val swapOutFeerate: LiveData<String> = Transformations.map(paymentMeta) {
    it?.swap_out_feerate_per_byte?.run { "$this sat/byte" } ?: ""
  }

  val pubkey: LiveData<String> = Transformations.map(state) {
    when (it) {
      is PaymentDetailsState.Outgoing.Sent.Closing -> ""
      is PaymentDetailsState.Outgoing -> it.parts.first().recipientNodeId().toString()
      else -> ""
    }
  }

  val description: LiveData<String?> = Transformations.map(state) { state ->
    (when (state) {
      is PaymentDetailsState.Outgoing -> state.description
      is PaymentDetailsState.Incoming.Received.Normal -> state.getDescription()
      else -> null
    }).takeIf { !it.isNullOrBlank() }
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
      is PaymentDetailsState.Outgoing.Sent -> (it.parts.first().status() as OutgoingPaymentStatus.Succeeded).paymentPreimage().toString()
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
      is PaymentDetailsState.Outgoing.Sent -> (it.parts.first().status() as OutgoingPaymentStatus.Succeeded).completedAt()
      is PaymentDetailsState.Outgoing.Failed -> (it.parts.first().status() as OutgoingPaymentStatus.Failed).completedAt()
      is PaymentDetailsState.Outgoing.Pending -> null
      is PaymentDetailsState.Incoming ->
        if (it.payment.status() is IncomingPaymentStatus.Received) {
          (it.payment.status() as IncomingPaymentStatus.Received).receivedAt()
        } else {
          null
        }
      else -> null
    }?.let { t -> DateFormat.getDateTimeInstance().format(t) } ?: ""
  }

  suspend fun getPaymentMeta() {
    viewModelScope.launch(Dispatchers.IO + CoroutineExceptionHandler { _, e ->
      log.error("failed to retrieve payment meta for id=$paymentId: ", e)
    }) {
      paymentMeta.postValue(paymentMetaRepository.get(paymentId))
      payToOpenMeta.postValue(payToOpenMetaRepository.get(paymentId))
    }
  }

  suspend fun buildOutgoing(payments: List<OutgoingPayment>) = withContext(viewModelScope.coroutineContext + Dispatchers.Default) {
    if (payments.isEmpty()) {
      state.postValue(PaymentDetailsState.Error.NotFound)
      return@withContext
    }
    val paymentMeta = paymentMetaRepository.get(paymentId)
    val amountToRecipient = payments.first().recipientAmount()
    updateRecipientMeta(payments.first().recipientNodeId().toString())
    val description = payments.first().paymentRequest()?.run {
      if (isDefined) {
        get().description()?.let { d -> if (d.isLeft) d.left().get() else d.right().get().toString() }
      } else null
    }
    when {
      payments.any { p -> p.status() is OutgoingPaymentStatus.`Pending$` } -> {
        payments.filter { p -> p.status() is OutgoingPaymentStatus.`Pending$` }.also {
          state.postValue(PaymentDetailsState.Outgoing.Pending(it.first().paymentType(), it, description, amountToRecipient))
        }
      }
      payments.any { p -> p.status() is OutgoingPaymentStatus.Succeeded } -> {
        payments.filter { p -> p.status() is OutgoingPaymentStatus.Succeeded }.also {
          val total = MilliSatoshi(it.map { p -> p.amount().`$plus`((p.status() as OutgoingPaymentStatus.Succeeded).feesPaid()).toLong() }.sum())
          val fees = total.`$minus`(amountToRecipient)
          val completedAt = it.map { p -> p.status() as OutgoingPaymentStatus.Succeeded }.map { s -> s.completedAt() }.max()!!
          val head = it.first()
          if (paymentMeta?.swap_out_address != null && paymentMeta.swap_out_fee_sat != null && paymentMeta.swap_out_feerate_per_byte != null) {
            val feeSwapOut = Satoshi(paymentMeta.swap_out_fee_sat)
            val descSwapOut = appContext.getString(R.string.paymentdetails_swap_out_desc)
            state.postValue(PaymentDetailsState.Outgoing.Sent.SwapOut(head.paymentType(), it, descSwapOut, amountToRecipient.`$minus`(feeSwapOut), Converter.any2Msat(feeSwapOut), completedAt,
              paymentMeta.swap_out_feerate_per_byte))
          } else if (head.paymentType() == "ClosingChannel" || (head.paymentRequest().isEmpty && head.externalId().isDefined && head.externalId().get().startsWith("closing-"))) {
            val descClosing = appContext.getString(R.string.paymentdetails_closing_desc, paymentMeta?.closing_channel_id?.take(10) ?: "")
            state.postValue(PaymentDetailsState.Outgoing.Sent.Closing(head.paymentType(), it, descClosing, amountToRecipient, fees, completedAt))
          } else {
            state.postValue(PaymentDetailsState.Outgoing.Sent.Normal(head.paymentType(), it, description, amountToRecipient, fees, completedAt))
          }
        }
      }
      payments.any { p -> p.status() is OutgoingPaymentStatus.Failed } -> {
        payments.filter { p -> p.status() is OutgoingPaymentStatus.Failed }.also {
          state.postValue(PaymentDetailsState.Outgoing.Failed(it.first().paymentType(), it, description, amountToRecipient))
        }
      }
      else -> {
        log.warn("could not find any outgoing payments for id=$paymentId, with result=$payments")
        state.postValue(PaymentDetailsState.Error.NotFound)
      }
    }
  }

  private suspend fun getSwapOutTxConf(txId: String) {
    viewModelScope.launch(Dispatchers.IO + CoroutineExceptionHandler { _, e ->
      log.error("could not retrieve explorer data for tx=$txId: ", e)
    }) {
      Wallet.httpClient.simpleExecute("${Constants.BLOCKSTREAM_EXPLORER_API}/tx/$txId") { json ->
        // swapOutTxIsConfirmed.postValue(json.getJSONObject("status").getBoolean("confirmed"))
      }
    }
  }

  suspend fun buildIncoming(paymentOpt: Option<IncomingPayment>) = withContext(viewModelScope.coroutineContext + Dispatchers.Default) {
    if (paymentOpt.isEmpty) {
      log.warn("could not find any incoming payments for payment_hash=$paymentId")
      state.postValue(PaymentDetailsState.Error.NotFound)
    } else {
      val payment = paymentOpt.get()
      if (payment.status() !is IncomingPaymentStatus.Received) {
        state.postValue(PaymentDetailsState.Incoming.Pending(payment))
      } else {
        val paymentMeta = paymentMetaRepository.get(paymentId)
        if (paymentMeta?.swap_in_address == null) {
          state.postValue(PaymentDetailsState.Incoming.Received.Normal(payment))
        } else {
          state.postValue(PaymentDetailsState.Incoming.Received.SwapIn(payment))
        }
      }
    }
  }

  private fun updateRecipientMeta(nodeId: String) {
    viewModelScope.launch(Dispatchers.IO + CoroutineExceptionHandler { _, e ->
      log.error("failed to retrieve recipient metadata: ", e)
    }) {
      nodeMetaRepository.get(nodeId)?.let { recipientMeta.postValue(it) }
    }
  }

  fun saveCustomDescription(desc: String) {
    viewModelScope.launch(Dispatchers.IO + CoroutineExceptionHandler { _, e ->
      log.error("failed to save description=$desc for payment=$paymentId: ", e)
    }) {
      log.debug("saving custom description=$desc for payment=$paymentId")
      paymentMetaRepository.setDesc(paymentId,desc)
      getPaymentMeta()
    }
  }

  class Factory(
    private val appContext: Context,
    private val paymentId: String,
    private val paymentMetaRepository: PaymentMetaRepository,
    private val payToOpenMetaRepository: PayToOpenMetaRepository,
    private val nodeMetaRepository: NodeMetaRepository,
  ) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel?> create(modelClass: Class<T>): T {
      return PaymentDetailsViewModel(appContext, paymentId, paymentMetaRepository, payToOpenMetaRepository, nodeMetaRepository) as T
    }
  }
}
