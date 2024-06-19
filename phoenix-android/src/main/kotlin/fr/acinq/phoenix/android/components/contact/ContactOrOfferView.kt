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

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import fr.acinq.lightning.wire.OfferTypes
import fr.acinq.phoenix.android.R
import fr.acinq.phoenix.android.Screen
import fr.acinq.phoenix.android.business
import fr.acinq.phoenix.android.components.Button
import fr.acinq.phoenix.android.components.Clickable
import fr.acinq.phoenix.android.components.HSeparator
import fr.acinq.phoenix.android.components.SplashLabelRow
import fr.acinq.phoenix.android.navController
import fr.acinq.phoenix.data.ContactInfo
import kotlinx.coroutines.launch

private sealed class OfferContactState {
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
        contactState.value = contactsManager.getContactForOffer(offer)?.let { OfferContactState.Found(it) } ?: OfferContactState.NotFound
    }

    when (val state = contactState.value) {
        is OfferContactState.Init -> {
            Text(text = offer.encode(), maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        is OfferContactState.Found -> {
            ContactCompactView(
                contact = state.contact,
                currentOffer = offer,
                onContactChange = {
                    contactState.value = if (it == null) OfferContactState.NotFound else OfferContactState.Found(it)
                },
            )
        }
        is OfferContactState.NotFound -> {
            DetachedOfferView(offer = offer, onContactSaved = {
                contactState.value = OfferContactState.Found(it)
            })
        }
    }
}
