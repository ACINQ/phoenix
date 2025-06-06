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

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.acinq.lightning.wire.OfferTypes
import fr.acinq.phoenix.android.R
import fr.acinq.phoenix.android.business
import fr.acinq.phoenix.android.components.Clickable
import fr.acinq.phoenix.android.components.HSeparator
import fr.acinq.phoenix.android.components.PhoenixIcon
import fr.acinq.phoenix.android.components.SplashClickableContent
import fr.acinq.phoenix.android.components.dialogs.ModalBottomSheet
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
    val contactsDb by business.databaseManager.contactsDb.collectAsState(null)
    val contactState = remember { mutableStateOf<OfferContactState>(OfferContactState.Init) }
    LaunchedEffect(contactsDb) {
        contactsDb?.let {
            contactState.value = it.contactForOffer(offer)?.let { OfferContactState.Found(it) } ?: OfferContactState.NotFound
        }
    }

    when (val state = contactState.value) {
        is OfferContactState.Init -> {
            Text(text = offer.encode(), maxLines = 2, overflow = TextOverflow.MiddleEllipsis)
        }
        is OfferContactState.Found -> {
            ContactCompactView(
                contact = state.contact,
                onContactChange = { newContact ->
                    contactState.value = when {
                        newContact == null -> OfferContactState.NotFound
                        newContact.offers.map { it.offer }.contains(offer) -> OfferContactState.Found(newContact)
                        else -> OfferContactState.NotFound
                    }
                }
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
    var showContactPickerDialog by remember { mutableStateOf(false) }
    var addToExistingContact by remember { mutableStateOf<ContactInfo?>(null) }
    var isCreatingNewContact by remember { mutableStateOf(false) }


    val encoded = remember(offer) { offer.encode() }
    SplashClickableContent(onClick = { showContactPickerDialog = true }) {
        Text(text = encoded, maxLines = 2, overflow = TextOverflow.MiddleEllipsis)
        Spacer(modifier = Modifier.height(8.dp))
        Row {
            PhoenixIcon(resourceId = R.drawable.ic_user, tint = mutedTextColor)
            Spacer(modifier = Modifier.width(6.dp))
            Text(text = stringResource(id = R.string.contact_add_contact_button), style = MaterialTheme.typography.subtitle2)
        }
    }

    when {
        showContactPickerDialog -> SaveOrAttachOfferPickerDialog(
            onDismiss = { showContactPickerDialog = false },
            onCreateNewContact = { showContactPickerDialog = false ; isCreatingNewContact = true },
            onAddToExistingContact = { showContactPickerDialog = false ; addToExistingContact = it },
        )
        addToExistingContact != null -> ContactDetailsView(
            onDismiss = { addToExistingContact = null },
            contact = addToExistingContact,
            onContactChange = { it?.let { onContactSaved(it) } },
            newOffer = offer
        )
        isCreatingNewContact -> ContactDetailsView(
            onDismiss = { isCreatingNewContact = false },
            contact = null,
            onContactChange = { it?.let { onContactSaved(it) } },
            newOffer = offer
        )
    }
}

@Composable
private fun SaveOrAttachOfferPickerDialog(
    onDismiss: () -> Unit,
    onCreateNewContact: () -> Unit,
    onAddToExistingContact: (ContactInfo) -> Unit,
) {
    ModalBottomSheet(
        onDismiss = onDismiss,
        skipPartiallyExpanded = true,
        isContentScrollable = false,
        contentHeight = 450.dp,
        internalPadding = PaddingValues(0.dp),
        containerColor = MaterialTheme.colors.background
    ) {
        Clickable(onClick = onCreateNewContact) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp)
                    .background(MaterialTheme.colors.surface, shape = RoundedCornerShape(16.dp))
                    .padding(horizontal = 16.dp, vertical = 16.dp)
            ) {
                Surface(shape = CircleShape, color = MaterialTheme.typography.caption.color.copy(alpha = .3f), modifier = Modifier.size(18.dp)) {
                    PhoenixIcon(R.drawable.ic_plus, tint = MaterialTheme.colors.surface, modifier = Modifier.size(8.dp))
                }
                Spacer(Modifier.width(8.dp))
                Text(text = stringResource(R.string.contact_attach_offer_new), style = MaterialTheme.typography.body1.copy(color = MaterialTheme.colors.primary))
            }
        }
        Spacer(Modifier.height(14.dp))
        HSeparator(modifier = Modifier.align(Alignment.CenterHorizontally), width = 100.dp)
        Spacer(Modifier.height(8.dp))
        ContactsListView(onContactClick = onAddToExistingContact, isOnSurface = false)
        Spacer(Modifier.height(60.dp))
    }
}
