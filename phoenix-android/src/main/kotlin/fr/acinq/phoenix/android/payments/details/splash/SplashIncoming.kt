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

package fr.acinq.phoenix.android.payments.details.splash

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import fr.acinq.bitcoin.PublicKey
import fr.acinq.lightning.db.IncomingPayment
import fr.acinq.lightning.utils.msat
import fr.acinq.lightning.utils.sat
import fr.acinq.lightning.utils.sum
import fr.acinq.lightning.utils.toMilliSatoshi
import fr.acinq.phoenix.android.LocalBitcoinUnit
import fr.acinq.phoenix.android.R
import fr.acinq.phoenix.android.business
import fr.acinq.phoenix.android.components.SplashLabelRow
import fr.acinq.phoenix.android.components.contact.ContactCompactView
import fr.acinq.phoenix.android.components.contact.OfferContactState
import fr.acinq.phoenix.android.utils.Converter.toPrettyString
import fr.acinq.phoenix.android.utils.MSatDisplayPolicy
import fr.acinq.phoenix.android.utils.smartDescription
import fr.acinq.phoenix.data.WalletPaymentId
import fr.acinq.phoenix.data.WalletPaymentMetadata
import fr.acinq.phoenix.data.walletPaymentId
import fr.acinq.phoenix.utils.extensions.incomingOfferMetadata

@Composable
fun SplashIncoming(
    payment: IncomingPayment,
    metadata: WalletPaymentMetadata,
    onMetadataDescriptionUpdate: (WalletPaymentId, String?) -> Unit,
) {
    payment.incomingOfferMetadata()?.let { meta ->
        meta.payerNote?.takeIf { it.isNotBlank() }?.let {
            OfferPayerNote(payerNote = it)
            Spacer(modifier = Modifier.height(8.dp))
        }
        OfferSentBy(payerPubkey = meta.payerKey, !meta.payerNote.isNullOrBlank())
        Spacer(modifier = Modifier.height(4.dp))
    }

    SplashDescription(
        description = payment.smartDescription(),
        userDescription = metadata.userDescription,
        paymentId = payment.walletPaymentId(),
        onMetadataDescriptionUpdate = onMetadataDescriptionUpdate,
    )
    SplashFee(payment)
}

@Composable
fun OfferPayerNote(payerNote: String) {
    Spacer(modifier = Modifier.height(8.dp))
    SplashLabelRow(label = stringResource(id = R.string.paymentdetails_offer_note_label)) {
        Text(text = payerNote)
    }
}

@Composable
private fun OfferSentBy(payerPubkey: PublicKey?, hasPayerNote: Boolean) {
    val contactsManager = business.contactsManager
    val contactState = remember { mutableStateOf<OfferContactState>(OfferContactState.Init) }
    LaunchedEffect(Unit) {
        contactState.value = payerPubkey?.let {
            contactsManager.getContactForPayerPubkey(it)
        }?.let { OfferContactState.Found(it) } ?: OfferContactState.NotFound
    }

    SplashLabelRow(label = stringResource(id = R.string.paymentdetails_offer_sender_label)) {
        when (val res = contactState.value) {
            is OfferContactState.Init -> Text(text = stringResource(id = R.string.utils_loading_data))
            is OfferContactState.NotFound -> {
                Text(text = stringResource(id = R.string.paymentdetails_offer_sender_unknown))
                if (hasPayerNote) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(text = stringResource(id = R.string.paymentdetails_offer_sender_unknown_details), style = MaterialTheme.typography.subtitle2)
                }
            }
            is OfferContactState.Found -> {
                ContactCompactView(
                    contact = res.contact,
                    currentOffer = null,
                    onContactChange = { contactState.value = if (it == null) OfferContactState.NotFound else OfferContactState.Found(it) },
                )
            }
        }
    }
}

@Composable
private fun SplashFee(
    payment: IncomingPayment
) {
    val btcUnit = LocalBitcoinUnit.current
    val receivedWithOnChain = remember(payment) { payment.received?.receivedWith?.filterIsInstance<IncomingPayment.ReceivedWith.OnChainIncomingPayment>() ?: emptyList() }
    val receivedWithLightning = remember(payment) { payment.received?.receivedWith?.filterIsInstance<IncomingPayment.ReceivedWith.LightningPayment>() ?: emptyList() }

    if (receivedWithOnChain.isNotEmpty() || receivedWithLightning.isNotEmpty()) {

        val paymentsManager = business.paymentsManager
        val txIds = remember(receivedWithLightning) { receivedWithLightning.mapNotNull { it.fundingFee?.fundingTxId } }
        val relatedLiquidityPayments by produceState(initialValue = emptyList()) {
            value = txIds.mapNotNull { paymentsManager.getLiquidityPurchaseForTxId(it) }
        }

        val serviceFee = remember(receivedWithOnChain, relatedLiquidityPayments) {
            receivedWithOnChain.map { it.serviceFee }.sum() + relatedLiquidityPayments.map { it.feePaidFromFutureHtlc.serviceFee.toMilliSatoshi() }.sum()
        }
        val miningFee = remember(receivedWithOnChain, relatedLiquidityPayments) {
            receivedWithOnChain.map { it.miningFee }.sum() + relatedLiquidityPayments.map { it.feePaidFromFutureHtlc.miningFee }.sum()
        }

        Spacer(modifier = Modifier.height(8.dp))
        if (serviceFee > 0.msat) {
            SplashLabelRow(
                label = stringResource(id = R.string.paymentdetails_service_fees_label),
                helpMessage = stringResource(R.string.paymentdetails_service_fees_desc)
            ) {
                Text(text = serviceFee.toPrettyString(btcUnit, withUnit = true, mSatDisplayPolicy = MSatDisplayPolicy.SHOW))
            }
            Spacer(modifier = Modifier.height(8.dp))
        }

        if (miningFee > 0.sat) {
            SplashLabelRow(
                label = stringResource(id = R.string.paymentdetails_funding_fees_label),
                helpMessage = stringResource(R.string.paymentdetails_funding_fees_desc)
            ) {
                Text(text = miningFee.toPrettyString(btcUnit, withUnit = true, mSatDisplayPolicy = MSatDisplayPolicy.HIDE))
            }
        }
    }
}