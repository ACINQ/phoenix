/*
 * Copyright 2025 ACINQ SAS
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import fr.acinq.bitcoin.PublicKey
import fr.acinq.lightning.db.Bolt12IncomingPayment
import fr.acinq.lightning.utils.UUID
import fr.acinq.phoenix.PhoenixBusiness
import fr.acinq.phoenix.android.LocalBusiness
import fr.acinq.phoenix.android.R
import fr.acinq.phoenix.android.components.layouts.SplashLabelRow
import fr.acinq.phoenix.android.components.contact.ContactCompactView
import fr.acinq.phoenix.android.components.contact.OfferContactState
import fr.acinq.phoenix.android.utils.extensions.smartDescription
import fr.acinq.phoenix.data.WalletPaymentMetadata
import fr.acinq.phoenix.utils.extensions.incomingOfferMetadata
import fr.acinq.phoenix.utils.extensions.payerKey
import fr.acinq.phoenix.utils.extensions.payerNote
import fr.acinq.phoenix.utils.extensions.state


@Composable
fun SplashIncomingBolt12(
    business: PhoenixBusiness,
    payment: Bolt12IncomingPayment,
    metadata: WalletPaymentMetadata,
    onMetadataDescriptionUpdate: (UUID, String?) -> Unit,
) {
    SplashAmount(amount = payment.amount, state = payment.state(), isOutgoing = false)

    payment.incomingOfferMetadata()?.let { meta ->
        meta.payerNote?.takeIf { it.isNotBlank() }?.let {
            SplashOfferPayerNote(payerNote = it)
        }
        OfferSentBy(business = business, payerPubkey = meta.payerKey, !meta.payerNote.isNullOrBlank())
    }

    SplashDescription(
        description = payment.smartDescription(),
        userDescription = metadata.userDescription,
        paymentId = payment.id,
        onMetadataDescriptionUpdate = onMetadataDescriptionUpdate,
    )

    SplashFeeLightningIncoming(payment)
}

@Composable
fun SplashOfferPayerNote(payerNote: String) {
    Spacer(modifier = Modifier.height(8.dp))
    SplashLabelRow(label = stringResource(id = R.string.paymentdetails_offer_note_label)) {
        Text(text = payerNote)
    }
}

@Composable
private fun OfferSentBy(business: PhoenixBusiness, payerPubkey: PublicKey?, hasPayerNote: Boolean) {
    val contactsDb by business.databaseManager.contactsDb.collectAsState(null)
    val contactState = remember { mutableStateOf<OfferContactState>(OfferContactState.Init) }
    LaunchedEffect(contactsDb) {
        contactState.value = payerPubkey?.let {
            contactsDb?.contactForPayerPubKey(it)
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
                    onContactChange = { contactState.value = if (it == null) OfferContactState.NotFound else OfferContactState.Found(it) },
                )
            }
        }
    }
}
