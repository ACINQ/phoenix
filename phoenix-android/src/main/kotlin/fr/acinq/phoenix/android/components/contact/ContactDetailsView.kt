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

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.acinq.bitcoin.byteVector
import fr.acinq.lightning.wire.OfferTypes
import fr.acinq.phoenix.android.R
import fr.acinq.phoenix.android.Screen
import fr.acinq.phoenix.android.business
import fr.acinq.phoenix.android.components.Button
import fr.acinq.phoenix.android.components.Clickable
import fr.acinq.phoenix.android.components.HSeparator
import fr.acinq.phoenix.android.components.TextInput
import fr.acinq.phoenix.android.navController
import fr.acinq.phoenix.android.utils.copyToClipboard
import fr.acinq.phoenix.android.utils.invisibleOutlinedTextFieldColors
import fr.acinq.phoenix.android.utils.negativeColor
import fr.acinq.phoenix.data.ContactInfo
import kotlinx.coroutines.launch


/**
 * A contact detail is a bottom sheet dialog that display the contact's name, photo, and
 * associated offers/lnids.
 *
 * The contact may be edited and deleted from that screen.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContactDetailsView(
    contact: ContactInfo,
    currentOffer: OfferTypes.Offer?,
    onDismiss: () -> Unit,
    onContactChange: ((ContactInfo?) -> Unit)?,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val contactsManager = business.contactsManager
    val scope = rememberCoroutineScope()

    var name by remember(contact) { mutableStateOf(contact.name) }
    var photoUri by remember(contact) { mutableStateOf(contact.photoUri) }
    var showAllDetails by remember { mutableStateOf(false) }

    ModalBottomSheet(
        sheetState = sheetState,
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colors.surface,
        contentColor = MaterialTheme.colors.onSurface,
        scrimColor = MaterialTheme.colors.onBackground.copy(alpha = 0.2f),
    ) {
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .padding(bottom = 100.dp),
        ) {
            Column(
                modifier = Modifier.padding(top = 0.dp, start = 24.dp, end = 24.dp, bottom = 0.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                if (onContactChange != null) {
                    ContactPhotoView(photoUri = photoUri, name = contact.name, onChange = { photoUri = it })
                } else {
                    ContactPhotoView(photoUri = photoUri, name = contact.name, onChange = null)
                }
                Spacer(modifier = Modifier.height(24.dp))
                TextInput(
                    text = name,
                    onTextChange = { name = it },
                    textStyle = MaterialTheme.typography.h3.copy(textAlign = TextAlign.Center),
                    staticLabel = null,
                    textFieldColors = invisibleOutlinedTextFieldColors(),
                    showResetButton = false,
                    enabled = onContactChange != null,
                    enabledEffect = false,
                )

                if (onContactChange != null) {
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
                            enabled = contact.name != name || contact.photoUri != photoUri,
                            onClick = {
                                scope.launch {
                                    val newContact = contactsManager.updateContact(contact.id, name, photoUri, contact.offers)
                                    onContactChange(newContact)
                                    onDismiss()
                                }
                            },
                            shape = CircleShape,
                        )
                    }
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

                Spacer(modifier = Modifier.height(12.dp))
                Box(contentAlignment = Alignment.Center) {
                    HSeparator()
                    Button(
                        text = stringResource(id = R.string.contact_offers_list_title),
                        textStyle = MaterialTheme.typography.caption.copy(fontSize = 12.sp),
                        icon = if (showAllDetails) R.drawable.ic_chevron_up else R.drawable.ic_chevron_down,
                        iconTint = MaterialTheme.typography.caption.color,
                        onClick = { showAllDetails = !showAllDetails },
                        padding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                        space = 8.dp,
                        shape = CircleShape,
                        backgroundColor = MaterialTheme.colors.surface
                    )
                }
            }

            if (showAllDetails) {
                val navController = navController
                Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    contact.offers.forEach { offer ->
                        OfferAttachedToContactRow(
                            offer = offer,
                            onOfferClick = { navController.navigate("${Screen.ScanData.route}?input=${it.encode()}") },
                            onDelete = { scope.launch { contactsManager.detachOfferFromContact(offerId = offer.offerId) } }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun OfferAttachedToContactRow(offer: OfferTypes.Offer, onDelete: () -> Unit, onOfferClick: (OfferTypes.Offer) -> Unit) {
    val context = LocalContext.current
    val encoded = remember(offer) { offer.encode() }
    Clickable(onClick = { onOfferClick(offer) }) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Spacer(modifier = Modifier.width(16.dp))
            Text(
                text = offer.encode(),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Button(
                icon = R.drawable.ic_copy,
                onClick = { copyToClipboard(context, encoded, "Copy offer") },
                padding = PaddingValues(horizontal = 12.dp, vertical = 12.dp)
            )
            Button(
                icon = R.drawable.ic_trash,
                onClick = onDelete,
                padding = PaddingValues(horizontal = 12.dp, vertical = 12.dp),
                iconTint = negativeColor,
            )
        }
    }
}
