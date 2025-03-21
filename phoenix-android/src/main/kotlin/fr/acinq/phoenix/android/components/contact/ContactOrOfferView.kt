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

package fr.acinq.phoenix.android.components.contact

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import fr.acinq.lightning.wire.OfferTypes
import fr.acinq.phoenix.android.R
import fr.acinq.phoenix.android.business
import fr.acinq.phoenix.android.components.PhoenixIcon
import fr.acinq.phoenix.android.components.SplashClickableContent
import fr.acinq.phoenix.android.utils.mutedTextColor
import fr.acinq.phoenix.data.ContactInfo

sealed class OfferContactState {
    data object Init: OfferContactState()
    data class Found(val contact: ContactInfo): OfferContactState()
    data object NotFound: OfferContactState()
}

/**
 * This component displays the contact that would match a given offer. It queries the
 * database and depending on the result, will show either a ContactCompactView, or the
 * encoded offer with a button to save a new contact.
 */
@Composable
fun ContactOrOfferView(offer: OfferTypes.Offer) {
    val contactsManager = business.contactsManager
    val contactState = remember { mutableStateOf<OfferContactState>(OfferContactState.Init) }
    LaunchedEffect(Unit) {
        contactState.value = contactsManager.contactForOffer(offer)?.let { OfferContactState.Found(it) } ?: OfferContactState.NotFound
    }

    when (val state = contactState.value) {
        is OfferContactState.Init -> {
            Text(text = offer.encode(), maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        is OfferContactState.Found -> {
            ContactCompactView(
                contact = state.contact,
                currentOffer = offer,
                onContactChange = { contactState.value = if (it == null) OfferContactState.NotFound else OfferContactState.Found(it) },
            )
        }
        is OfferContactState.NotFound -> {
            DetachedOfferView(offer = offer, onContactSaved = {
                contactState.value = OfferContactState.Found(it)
            })
        }
    }
}

@Composable
private fun DetachedOfferView(
    offer: OfferTypes.Offer,
    onContactSaved: (ContactInfo) -> Unit,
) {
    var showSaveContactDialog by remember { mutableStateOf(false) }

    val encoded = remember(offer) { offer.encode() }
    SplashClickableContent(onClick = { showSaveContactDialog = true }) {
        Text(text = encoded, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Spacer(modifier = Modifier.height(8.dp))
        Row {
            PhoenixIcon(resourceId = R.drawable.ic_user, tint = mutedTextColor)
            Spacer(modifier = Modifier.width(6.dp))
            Text(text = stringResource(id = R.string.contact_add_contact_button), style = MaterialTheme.typography.subtitle2)
        }
    }

    if (showSaveContactDialog) {
        SaveNewContactDialog(
            initialOffer = offer,
            onDismiss = { showSaveContactDialog = false },
            onSaved = { onContactSaved(it) },
        )
    }
}
