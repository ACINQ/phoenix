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

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
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
import fr.acinq.lightning.wire.OfferTypes
import fr.acinq.phoenix.android.R
import fr.acinq.phoenix.android.Screen
import fr.acinq.phoenix.android.business
import fr.acinq.phoenix.android.components.BorderButton
import fr.acinq.phoenix.android.components.Button
import fr.acinq.phoenix.android.components.Clickable
import fr.acinq.phoenix.android.components.DefaultScreenHeader
import fr.acinq.phoenix.android.components.HSeparator
import fr.acinq.phoenix.android.components.PhoenixIcon
import fr.acinq.phoenix.android.components.SwitchView
import fr.acinq.phoenix.android.components.TextInput
import fr.acinq.phoenix.android.navController
import fr.acinq.phoenix.android.utils.copyToClipboard
import fr.acinq.phoenix.android.utils.invisibleOutlinedTextFieldColors
import fr.acinq.phoenix.android.utils.mutedTextColor
import fr.acinq.phoenix.android.utils.negativeColor
import fr.acinq.phoenix.data.ContactInfo
import kotlinx.coroutines.launch


/**
 * A contact detail is a bottom sheet dialog that display the contact's name, photo, and
 * associated offers/lnids.
 *
 * The contact may be edited and deleted from that screen.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ContactDetailsView(
    contact: ContactInfo,
    currentOffer: OfferTypes.Offer?,
    onDismiss: () -> Unit,
    onContactChange: ((ContactInfo?) -> Unit)?,
) {
    val scope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        sheetState = sheetState,
        onDismissRequest = onDismiss,
        modifier = Modifier.fillMaxHeight(.7f),
        containerColor = MaterialTheme.colors.surface,
        contentColor = MaterialTheme.colors.onSurface,
        scrimColor = MaterialTheme.colors.onBackground.copy(alpha = 0.2f),
    ) {
        val pagerState = rememberPagerState(pageCount = { 2 })
        HorizontalPager(state = pagerState, verticalAlignment = Alignment.Top) { index ->
            when (index) {
                0 -> ContactNameAndPhoto(
                    contact = contact,
                    currentOffer = currentOffer,
                    onContactChange = onContactChange,
                    onDismiss = onDismiss,
                    onOffersClick = {
                        scope.launch { pagerState.animateScrollToPage(1) }
                    }
                )
                1 -> ContactOffers(
                    offers = contact.offers,
                    onBackClick = {
                        scope.launch { pagerState.animateScrollToPage(0) }
                    }
                )
            }
        }
    }
}

@Composable
private fun ContactNameAndPhoto(
    contact: ContactInfo,
    @Suppress("UNUSED_PARAMETER") currentOffer: OfferTypes.Offer?,
    onContactChange: ((ContactInfo?) -> Unit)?,
    onDismiss: () -> Unit,
    onOffersClick: () -> Unit,
) {
    val contactsManager = business.contactsManager
    val scope = rememberCoroutineScope()

    var name by remember(contact) { mutableStateOf(contact.name) }
    var photoUri by remember(contact) { mutableStateOf(contact.photoUri) }
    var useOfferKey by remember(contact) { mutableStateOf(contact.useOfferKey) }

    Column(
        modifier = Modifier
            .verticalScroll(rememberScrollState())
            .fillMaxWidth()
            .padding(bottom = 50.dp),
    ) {
        Column(
            modifier = Modifier.padding(top = 0.dp, start = 24.dp, end = 24.dp, bottom = 0.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (onContactChange != null) {
                ContactPhotoView(photoUri = photoUri, name = contact.name, onChange = { photoUri = it }, imageSize = 120.dp, borderSize = 4.dp)
            } else {
                ContactPhotoView(photoUri = photoUri, name = contact.name, onChange = null, imageSize = 120.dp, borderSize = 4.dp)
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
                Spacer(modifier = Modifier.height(16.dp))
                Row(
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp)
                ) {
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
                        padding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                        shape = CircleShape,
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    BorderButton(
                        text = stringResource(id = R.string.contact_save_button),
                        icon = R.drawable.ic_check,
                        enabled = contact.name != name || contact.photoUri != photoUri || contact.useOfferKey != useOfferKey,
                        onClick = {
                            scope.launch {
                                val newContact = contactsManager.updateContact(contact.id, name, photoUri, useOfferKey, contact.offers)
                                onContactChange(newContact)
                                onDismiss()
                            }
                        },
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(48.dp))
        SwitchView(
            text = stringResource(id = R.string.contact_offer_key_title),
            description = if (useOfferKey) {
                stringResource(id = R.string.contact_offer_key_enabled)
            } else {
                stringResource(id = R.string.contact_offer_key_disabled)
            },
            checked = useOfferKey,
            onCheckedChange = { useOfferKey = it },
            enabled = onContactChange != null,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp)
        )

        Clickable(onClick = onOffersClick) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = stringResource(id = R.string.contact_offers_list))
                Spacer(modifier = Modifier.weight(1f))
                PhoenixIcon(resourceId = R.drawable.ic_chevron_right, tint = mutedTextColor)
            }
        }
    }
}

@Composable
private fun ContactOffers(
    onBackClick: () -> Unit,
    offers: List<OfferTypes.Offer>,
) {
    val navController = navController
    Column(modifier = Modifier.fillMaxSize()) {
        DefaultScreenHeader(title = stringResource(id = R.string.contact_offers_list), onBackClick = onBackClick)
        HSeparator()
        Column(modifier = Modifier.fillMaxWidth()) {
            offers.forEach { offer ->
                OfferAttachedToContactRow(
                    offer = offer,
                    onOfferClick = { navController.navigate("${Screen.Send.route}?input=${it.encode()}") },
                )
            }
        }
    }
}

@Composable
private fun OfferAttachedToContactRow(
    offer: OfferTypes.Offer,
    onOfferClick: (OfferTypes.Offer) -> Unit
) {
    val context = LocalContext.current
    val encoded = remember(offer) { offer.encode() }
    Clickable(onClick = { onOfferClick(offer) }) {
        Row(
            modifier = Modifier.height(IntrinsicSize.Min),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Spacer(modifier = Modifier.width(24.dp))
            Text(
                text = offer.encode(),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Button(
                icon = R.drawable.ic_copy,
                onClick = { copyToClipboard(context, encoded, "Copy offer") },
                padding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
            )
        }
    }
}
