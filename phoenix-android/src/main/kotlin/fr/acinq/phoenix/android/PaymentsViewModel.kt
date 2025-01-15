/*
 * Copyright 2023 ACINQ SAS
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

package fr.acinq.phoenix.android

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import fr.acinq.lightning.db.Bolt12IncomingPayment
import fr.acinq.lightning.db.LightningOutgoingPayment
import fr.acinq.lightning.payment.OfferPaymentMetadata
import fr.acinq.lightning.utils.UUID
import fr.acinq.phoenix.data.ContactInfo
import fr.acinq.phoenix.data.WalletPaymentInfo
import fr.acinq.phoenix.db.WalletPaymentOrderRow
import fr.acinq.phoenix.managers.ContactsManager
import fr.acinq.phoenix.managers.PaymentsManager
import fr.acinq.phoenix.managers.PaymentsPageFetcher
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory

data class PaymentRowState(
    val paymentInfo: WalletPaymentInfo,
    val contactInfo: ContactInfo?,
) {
    val orderRow : WalletPaymentOrderRow
        get() = paymentInfo.toOrderRow()
}

@OptIn(ExperimentalCoroutinesApi::class)
class PaymentsViewModel(
    private val paymentsManager: PaymentsManager,
    private val contactsManager: ContactsManager,
) : ViewModel() {

    companion object {
        const val pageSize = 40
        const val paymentsCountInHome = 10
    }

    private val log = LoggerFactory.getLogger(this::class.java)

    private val _paymentsFlow = MutableStateFlow<Map<UUID, PaymentRowState>>(HashMap())
    /**
     * A flow of known payments. The key is an [UUID], the value is the payments details
     * which are basic at first, and then updated asynchronously (see [fetchPaymentDetails]).
     *
     * This flow is initialized by the view model, and then updated by [subscribeToPayments] which is
     * called by the UI when needed (paging with scrolling, see the payments history view).
     */
    val paymentsFlow: StateFlow<Map<UUID, PaymentRowState>> = _paymentsFlow.asStateFlow()

    private val homePageFetcher: PaymentsPageFetcher = paymentsManager.makePageFetcher()
    val homePaymentsFlow = homePageFetcher.paymentsPage.mapLatest { it.rows }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = emptyList(),
    )

    private val paymentsPageFetcher: PaymentsPageFetcher = paymentsManager.makePageFetcher()
    val paymentsPage = paymentsPageFetcher.paymentsPage


    init {
        paymentsPageFetcher.subscribeToAll(offset = 0, count = pageSize)
        homePageFetcher.subscribeToAll(offset = 0, count = paymentsCountInHome)

        // collect changes on the payments page that we subscribed to
        viewModelScope.launch(CoroutineExceptionHandler { _, e ->
            log.error("error when collecting payments-page items: ", e)
        }) {
            paymentsPageFetcher.paymentsPage.collect { page ->
                viewModelScope.launch(Dispatchers.Default) {
                    val newElts = page.rows.associate { newRow ->
                        val paymentId = newRow.payment.id
                        val existingData = paymentsFlow.value[paymentId]

                        // We look at the row to check if the payment has changed (the row contains timestamps)
                        if (existingData?.orderRow != newRow.toOrderRow()) {
                            fetchContactDetails(newRow)
                            paymentId to PaymentRowState(paymentInfo = newRow, contactInfo = null)
                        } else {
                            paymentId to existingData
                        }
                    }
                    if (page.offset == 0) {
                        _paymentsFlow.value = newElts
                    } else {
                        _paymentsFlow.value += newElts
                    }
                }
            }
        }
    }

    /** Fetches the contact details for a given payment and updates [paymentsFlow]. */
    private fun fetchContactDetails(walletPaymentInfo: WalletPaymentInfo) {
        when (val payment = walletPaymentInfo.payment) {
            is Bolt12IncomingPayment -> {
                val metadata = payment.metadata
                if (metadata is OfferPaymentMetadata.V1) {
                    viewModelScope.launch(Dispatchers.Main) {
                        contactsManager.getContactForPayerPubkey(metadata.payerKey)?.let {
                            _paymentsFlow.value += (payment.id to PaymentRowState(walletPaymentInfo, it))
                        }
                    }
                }
            }
            is LightningOutgoingPayment -> {
                val details = payment.details
                if (details is LightningOutgoingPayment.Details.Blinded) {
                    viewModelScope.launch(Dispatchers.Main) {
                        contactsManager.getContactForOffer(details.paymentRequest.invoiceRequest.offer)?.let {
                            _paymentsFlow.value += (payment.id to PaymentRowState(walletPaymentInfo, it))
                        }
                    }
                }
            }
            else -> Unit
        }
    }

    /** Updates the payment fetcher to listen to changes within the given count and offset, indirectly updating the [paymentsFlow]. */
    fun subscribeToPayments(offset: Int, count: Int) {
        paymentsPageFetcher.subscribeToAll(offset, count)
    }

    class Factory(
        private val paymentsManager: PaymentsManager,
        private val contactsManager: ContactsManager,
    ) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            @Suppress("UNCHECKED_CAST")
            return PaymentsViewModel(paymentsManager, contactsManager) as T
        }
    }
}
