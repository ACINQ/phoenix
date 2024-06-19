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
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.journeyapps.barcodescanner.DecoratedBarcodeView
import fr.acinq.bitcoin.utils.Try
import fr.acinq.lightning.wire.OfferTypes
import fr.acinq.phoenix.android.R
import fr.acinq.phoenix.android.business
import fr.acinq.phoenix.android.components.Button
import fr.acinq.phoenix.android.components.FilledButton
import fr.acinq.phoenix.android.components.TextInput
import fr.acinq.phoenix.android.payments.CameraPermissionsView
import fr.acinq.phoenix.android.payments.ScannerView
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
fun SaveNewContactDialog(
    initialOffer: OfferTypes.Offer?,
    onDismiss: () -> Unit,
    onSaved: (ContactInfo) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var offer by remember { mutableStateOf(initialOffer?.encode() ?: "") }
    var offerErrorMessage by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    var photo by remember { mutableStateOf<ByteArray?>(null) }

    val contactsManager = business.contactsManager

    ModalBottomSheet(
        sheetState = sheetState,
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colors.surface,
        contentColor = MaterialTheme.colors.onSurface,
        scrimColor = MaterialTheme.colors.onBackground.copy(alpha = 0.2f),
    ) {

        var showScannerView by remember { mutableStateOf(false) }
        if (showScannerView) {
            OfferScanner(
                onScannerDismiss = { showScannerView = false },
                onOfferScanned = {
                    offer = it
                    offerErrorMessage = ""
                    showScannerView = false
                }
            )
        } else {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(top = 0.dp, start = 24.dp, end = 24.dp, bottom = 100.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(text = "Attach a contact to an invoice", style = MaterialTheme.typography.h4, textAlign = TextAlign.Center)
                Spacer(modifier = Modifier.height(24.dp))
                ContactPhotoView(image = photo, name = name, onChange = { photo = it })
                Spacer(modifier = Modifier.height(24.dp))
                TextInput(
                    text = name,
                    onTextChange = { name = it },
                    textStyle = MaterialTheme.typography.h3,
                    staticLabel = stringResource(id = R.string.contact_name_label),
                )
                TextInput(
                    text = offer,
                    onTextChange = { offer = it },
                    enabled = initialOffer == null,
                    enabledEffect = false,
                    staticLabel = stringResource(id = R.string.contact_offer_label),
                    trailingIcon = {
                        Button(
                            onClick = { showScannerView = true },
                            icon = R.drawable.ic_scan_qr,
                            iconTint = MaterialTheme.colors.primary
                        )
                    },
                    maxLines = 4,
                    errorMessage = offerErrorMessage,
                    showResetButton = false
                )
                Spacer(modifier = Modifier.height(24.dp))
                FilledButton(
                    text = stringResource(id = R.string.contact_add_contact),
                    icon = R.drawable.ic_check,
                    onClick = {
                        scope.launch {
                            if (initialOffer == null) {
                                offerErrorMessage = ""
                                when (val res = OfferTypes.Offer.decode(offer)) {
                                    is Try.Success -> {
                                        val decodedOffer = res.result
                                        val existingContact = contactsManager.getContactForOffer(decodedOffer)
                                        if (existingContact != null) {
                                            offerErrorMessage = "Offer already known (${existingContact.name})"
                                        } else {
                                            val contact = contactsManager.saveNewContact(name, photo, res.result)
                                            onSaved(contact)
                                        }
                                    }
                                    is Try.Failure -> { offerErrorMessage = "invalid offer" }
                                }
                            } else {
                                val existingContact = contactsManager.getContactForOffer(initialOffer)
                                if (existingContact != null) {
                                    offerErrorMessage = "Offer already known (${existingContact.name})"
                                } else {
                                    val contact = contactsManager.saveNewContact(name, photo, initialOffer)
                                    onSaved(contact)
                                }

                            }
                        }
                    },
                    shape = CircleShape,
                    modifier = Modifier.align(Alignment.End),
                )
                Spacer(modifier = Modifier.height(8.dp))
                FilledButton(
                    text = stringResource(id = R.string.contact_attach_existing_contact),
                    icon = R.drawable.ic_user_search,
                    onClick = {  },
                    shape = CircleShape,
                    modifier = Modifier.align(Alignment.End),
                    backgroundColor = Color.Transparent,
                )
            }
        }
    }
}

@Composable
private fun OfferScanner(
    onScannerDismiss: () -> Unit,
    onOfferScanned: (String) -> Unit,
) {
    var scanView by remember { mutableStateOf<DecoratedBarcodeView?>(null) }
    Box(Modifier.fillMaxSize()) {
        ScannerView(
            onScanViewBinding = { scanView = it },
            onScannedText = { onOfferScanned(it) }
        )

        CameraPermissionsView {
            LaunchedEffect(Unit) { scanView?.resume() }
        }

        // buttons at the bottom of the screen
        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .padding(24.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(MaterialTheme.colors.surface)
        ) {
            Button(
                modifier = Modifier.fillMaxWidth(),
                text = stringResource(id = R.string.btn_cancel),
                icon = R.drawable.ic_arrow_back,
                onClick = onScannerDismiss
            )
        }
    }
}
