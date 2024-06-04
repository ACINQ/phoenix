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

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import fr.acinq.bitcoin.ByteVector
import fr.acinq.bitcoin.byteVector
import fr.acinq.lightning.utils.toByteVector
import fr.acinq.lightning.wire.OfferTypes
import fr.acinq.phoenix.android.R
import fr.acinq.phoenix.android.business
import fr.acinq.phoenix.android.components.Button
import fr.acinq.phoenix.android.components.HSeparator
import fr.acinq.phoenix.android.components.SettingWithCopy
import fr.acinq.phoenix.android.components.TextInput
import fr.acinq.phoenix.android.utils.copyToClipboard
import fr.acinq.phoenix.android.utils.discreteOutlinedTextFieldColors
import fr.acinq.phoenix.android.utils.negativeColor
import fr.acinq.phoenix.data.ContactInfo
import kotlinx.coroutines.launch


/**
 * A contact detail is a bottom sheet dialog that display the contact's name, photo, and
 * associated offers/lnids.
 *
 * The contact can be edited and deleted from that screen.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContactDetailsView(
    contact: ContactInfo,
    currentOffer: OfferTypes.Offer?,
    onDismiss: () -> Unit,
    onContactChange: (ContactInfo?) -> Unit,
    onSendPayment: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)
    val contactsManager = business.contactsManager
    val scope = rememberCoroutineScope()

    var name by remember(contact) { mutableStateOf(contact.name) }
    var photo by remember(contact) { mutableStateOf(contact.photo?.toByteArray()) }

    ModalBottomSheet(
        sheetState = sheetState,
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colors.surface,
        contentColor = MaterialTheme.colors.onSurface,
        scrimColor = MaterialTheme.colors.onBackground.copy(alpha = 0.1f),
    ) {
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .padding(top = 0.dp, start = 24.dp, end = 24.dp, bottom = 70.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            ContactPhotoView(image = photo, name = contact.name, onChange = { photo = it })
            Spacer(modifier = Modifier.height(24.dp))
            TextInput(
                text = name,
                onTextChange = { name = it },
                textStyle = MaterialTheme.typography.h3.copy(textAlign = TextAlign.Center),
                staticLabel = null,
                defaultOutlineColors = discreteOutlinedTextFieldColors(),
                showResetButton = false
            )

            Row(horizontalArrangement = Arrangement.Center) {
                Button(
                    text = stringResource(id = R.string.contact_delete_button),
                    icon = R.drawable.ic_trash,
                    iconTint = negativeColor,
                    onClick = {
                        scope.launch {
                            contactsManager.deleteContact(contact.id)
                            onContactChange(null)
                            onDismiss()
                        }
                    },
                    shape = CircleShape,
                )
                Spacer(modifier = Modifier.width(8.dp))
                Button(
                    text = stringResource(id = R.string.contact_save_button),
                    icon = R.drawable.ic_check,
                    enabled = contact.name != name || contact.photo != photo?.byteVector(),
                    onClick = {
                        scope.launch {
                            val newContact = contactsManager.updateContact(contact.id, name, photo, contact.offers)
                            onContactChange(newContact)
                            onDismiss()
                        }
                    },
                    shape = CircleShape,
                )
            }
            
            if (currentOffer != null) {
                Spacer(modifier = Modifier.height(24.dp))
                TextInput(
                    text = currentOffer.encode(),
                    onTextChange = {},
                    enabled = false,
                    enabledEffect = false,
                    staticLabel = stringResource(id = R.string.contact_offer_label),
                    maxLines = 4,
                    showResetButton = false
                )
            }

//            Spacer(modifier = Modifier.height(12.dp))
//            HSeparator()
//            Spacer(modifier = Modifier.height(12.dp))
//            Text(
//                text = "${contact.offers.size} invoices attached",
//                style = MaterialTheme.typography.body2,
//            )
//            contact.offers.forEachIndexed { index, offer ->
//                OfferAttachedToContactRow(index = index, offer = offer, onDelete = {
//                    scope.launch { contactsManager.detachOfferFromContact(offerId = offer.offerId) }
//                })
//            }
        }
    }
}

@Composable
private fun OfferAttachedToContactRow(index: Int, offer: OfferTypes.Offer, onDelete: () -> Unit) {
    val context = LocalContext.current
    val encoded = remember(offer) { offer.encode() }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(text = "$index")
        Text(text = encoded, style = MaterialTheme.typography.subtitle2, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Button(
            icon = R.drawable.ic_copy,
            onClick = { copyToClipboard(context, encoded, "Copy offer") }
        )
        Button(
            icon = R.drawable.ic_trash,
            onClick = onDelete
        )
    }
}
