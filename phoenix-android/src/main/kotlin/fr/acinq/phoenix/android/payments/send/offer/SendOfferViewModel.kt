/*
 * Copyright 2024 ACINQ SAS
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

package fr.acinq.phoenix.android.payments.send.offer

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import fr.acinq.lightning.Lightning
import fr.acinq.lightning.MilliSatoshi
import fr.acinq.lightning.db.LightningOutgoingPayment
import fr.acinq.lightning.io.OfferNotPaid
import fr.acinq.lightning.io.PaymentNotSent
import fr.acinq.lightning.io.PaymentSent
import fr.acinq.lightning.payment.OutgoingPaymentFailure
import fr.acinq.lightning.wire.OfferTypes
import fr.acinq.phoenix.android.PhoenixApplication
import fr.acinq.phoenix.android.WalletId
import fr.acinq.phoenix.android.components.getLogger
import fr.acinq.phoenix.managers.DatabaseManager
import fr.acinq.phoenix.managers.NodeParamsManager
import fr.acinq.phoenix.managers.PeerManager
import fr.acinq.phoenix.utils.logger.LogHelper
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.seconds

sealed class OfferState {
    data object Init : OfferState()
    data object FetchingInvoice : OfferState()
    sealed class Complete : OfferState() {
        data class SendingOffer(val payment: LightningOutgoingPayment) : Complete()
        sealed class Failed : Complete() {
            data class Error(val throwable: Throwable) : Failed()
            data object CouldNotGetInvoice : Failed()
            data class PaymentNotSent(val reason: OutgoingPaymentFailure) : Failed()
            data object PayerNoteTooLong : Failed()
        }
    }
}

class SendOfferViewModel(
    val application: PhoenixApplication,
    val walletId: WalletId,
    val offer: OfferTypes.Offer,
    val peerManager: PeerManager,
    val nodeParamsManager: NodeParamsManager,
    val databaseManager: DatabaseManager,
) : ViewModel() {
    private val log = LogHelper.getLogger(application.applicationContext, walletId, this)
    var state by mutableStateOf<OfferState>(OfferState.Init)

    fun sendOffer(amount: MilliSatoshi, message: String, offer: OfferTypes.Offer) {
        if (state is OfferState.FetchingInvoice) return
        state = OfferState.FetchingInvoice

        viewModelScope.launch(Dispatchers.Default + CoroutineExceptionHandler { _, e ->
            log.error("error when paying offer payment: ", e)
        }) {
            val contact = databaseManager.contactsDb.filterNotNull().first().contactForOffer(offer)
            val useRandomKey = contact == null || !contact.useOfferKey
            val payerKey = when (useRandomKey) {
                true -> Lightning.randomKey()
                false -> nodeParamsManager.defaultOffer().privateKey
            }
            val peer = peerManager.getPeer()
            val payerNote = message.takeIf { it.isNotBlank() }
            log.info("sending amount=$amount random=$useRandomKey message=$message for offer=$offer")
            val paymentResult = peer.payOffer(
                amount = amount,
                offer = offer,
                payerKey = payerKey,
                payerNote = payerNote,
                fetchInvoiceTimeout = 30.seconds,
                // FIXME: this method should accept a trampolineFees parameter
            )
            when (paymentResult) {
                is OfferNotPaid -> state = OfferState.Complete.Failed.CouldNotGetInvoice
                is PaymentNotSent -> state = OfferState.Complete.Failed.PaymentNotSent(paymentResult.reason)
                is PaymentSent -> state = OfferState.Complete.SendingOffer(paymentResult.payment)
            }
        }
    }

    class Factory(
        val application: PhoenixApplication,
        val walletId: WalletId,
        private val offer: OfferTypes.Offer,
        private val peerManager: PeerManager,
        private val nodeParamsManager: NodeParamsManager,
        private val databaseManager: DatabaseManager,
    ) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            @Suppress("UNCHECKED_CAST")
            return SendOfferViewModel(application, walletId, offer, peerManager, nodeParamsManager, databaseManager) as T
        }
    }
}