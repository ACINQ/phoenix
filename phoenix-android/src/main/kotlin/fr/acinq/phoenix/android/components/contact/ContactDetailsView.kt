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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import fr.acinq.bitcoin.ByteVector32
import fr.acinq.lightning.utils.UUID
import fr.acinq.lightning.utils.currentTimestampMillis
import fr.acinq.lightning.wire.OfferTypes
import fr.acinq.phoenix.android.LocalBusiness
import fr.acinq.phoenix.android.R
import fr.acinq.phoenix.android.navigation.Screen
import fr.acinq.phoenix.android.components.buttons.Button
import fr.acinq.phoenix.android.components.buttons.Clickable
import fr.acinq.phoenix.android.components.buttons.FilledButton
import fr.acinq.phoenix.android.components.PhoenixIcon
import fr.acinq.phoenix.android.components.buttons.SwitchView
import fr.acinq.phoenix.android.components.inputs.TextInput
import fr.acinq.phoenix.android.components.dialogs.ConfirmDialog
import fr.acinq.phoenix.android.components.dialogs.FullScreenDialog
import fr.acinq.phoenix.android.components.dialogs.ModalBottomSheet
import fr.acinq.phoenix.android.components.scanner.ScannerView
import fr.acinq.phoenix.android.navController
import fr.acinq.phoenix.android.utils.invisibleOutlinedTextFieldColors
import fr.acinq.phoenix.android.utils.mutedBgColor
import fr.acinq.phoenix.android.utils.mutedTextColor
import fr.acinq.phoenix.android.utils.negativeColor
import fr.acinq.phoenix.data.ContactAddress
import fr.acinq.phoenix.data.ContactInfo
import fr.acinq.phoenix.data.ContactOffer
import fr.acinq.phoenix.data.ContactPaymentCode
import fr.acinq.phoenix.utils.Parser
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch


/**
 * A contact detail is a bottom sheet dialog that displays the contact's name, photo, and
 * associated offers/ln addresses.
 *
 * It can be used to create, edit, or delete a contact.
 *
 * @param contact if null, this dialog is used to create a new contact
 * @param onContactChange if the [ContactInfo] argument is null, the contact has been deleted
 * @param newOffer if not null, the contact will be initialised/updated with this offer.
 */
@Composable
fun ContactDetailsView(
    onDismiss: () -> Unit,
    contact: ContactInfo?,
    onContactChange: (ContactInfo?) -> Unit,
    newOffer: OfferTypes.Offer? = null,
) {
    LocalBusiness.current?.let { business ->
        val contactsDb by business.databaseManager.contactsDb.collectAsState(null)

        ModalBottomSheet(
            onDismiss = onDismiss,
            skipPartiallyExpanded = true,
            isContentScrollable = false,
            contentHeight = 550.dp,
            contentWindowInsets = { WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal) },
            internalPadding = PaddingValues(0.dp),
            containerColor = Color.Transparent,
            dragHandle = null
        ) {
            ContactNameAndPhoto(
                contact = contact,
                newOffer = newOffer,
                saveContactToDb = { contactsDb?.saveContact(it) },
                deleteContactFromDb = { contactsDb?.deleteContact(it) },
                getContactForOfferInDb = { contactsDb?.contactForOffer(it) },
                getContactForAddressInDb = { contactsDb?.contactForLightningAddress(it) },
                onContactChange = { newContact ->
                    onContactChange(newContact)
                    onDismiss()
                },
            )
        }
    }
}

@Composable
private fun ColumnScope.ContactNameAndPhoto(
    contact: ContactInfo?,
    newOffer: OfferTypes.Offer?,
    saveContactToDb: suspend (ContactInfo) -> Unit,
    deleteContactFromDb: suspend (UUID) -> Unit,
    getContactForOfferInDb: (OfferTypes.Offer) -> ContactInfo?,
    getContactForAddressInDb: (String) -> ContactInfo?,
    onContactChange: (ContactInfo?) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val navController = navController

    val contactId = remember { contact?.id ?: UUID.randomUUID() }
    var name by remember(contact) { mutableStateOf(contact?.name ?: "") }
    var nameErrorMessage by remember { mutableStateOf("") }
    var photoUri by remember(contact) { mutableStateOf(contact?.photoUri) }
    var useOfferKey by remember(contact) { mutableStateOf(contact?.useOfferKey ?: true) }

    var paymentsCodeList by remember(contact) {
        val newCodes = newOffer?.let { listOf(ContactOffer(offer = newOffer, label = "", createdAt = currentTimestampMillis())) } ?: emptyList()
        val existingCodes = contact?.paymentCodes ?: emptyList()
        val paymentCodesMap: Map<ByteVector32, ContactPaymentCode> = (newCodes + existingCodes).associateBy { it.id }
        mutableStateOf(paymentCodesMap)
    }
    val contactOffers = remember(paymentsCodeList) { paymentsCodeList.values.filterIsInstance<ContactOffer>() }
    val contactAddresses = remember(paymentsCodeList) { paymentsCodeList.values.filterIsInstance<ContactAddress>() }

    val hasContactChanged = remember(contact, name, photoUri, useOfferKey, paymentsCodeList) {
        contact == null || contact.name != name || contact.photoUri != photoUri || contact.useOfferKey != useOfferKey || contact.offers != contactOffers || contact.addresses != contactAddresses
    }

    Box(modifier = Modifier.fillMaxWidth()) {
        Row (
            modifier = Modifier
                .fillMaxWidth()
                .background(color = MaterialTheme.colors.background, shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp))
                .align(Alignment.BottomCenter)
                .padding(bottom = 24.dp)
        ) {
            var showDeleteContactConfirmation by remember { mutableStateOf(false) }
            if (showDeleteContactConfirmation) {
                ConfirmDialog(
                    message = stringResource(R.string.contact_delete_contact_confirm),
                    onConfirm = {
                        contact?.id?.let {
                            scope.launch {
                                deleteContactFromDb(it)
                                onContactChange(null)
                            }
                        }
                        showDeleteContactConfirmation = false
                    },
                    onDismiss = { showDeleteContactConfirmation = false }
                )
            }

            Button(
                text = stringResource(id = R.string.btn_delete),
                icon = R.drawable.ic_remove,
                iconTint = negativeColor,
                padding = PaddingValues(horizontal = 8.dp, vertical = 20.dp),
                horizontalArrangement = Arrangement.Center,
                maxLines = 1,
                backgroundColor = Color.Transparent,
                enabled = contact != null,
                enabledEffect = false,
                modifier = Modifier
                    .weight(1f)
                    .alpha(if (contact != null) 1f else .2f),
                onClick = { showDeleteContactConfirmation = true },
            )
            Spacer(modifier = Modifier.width(100.dp))
            Button(
                text = stringResource(id = R.string.btn_save),
                icon = R.drawable.ic_check,
                padding = PaddingValues(horizontal = 8.dp, vertical = 20.dp),
                horizontalArrangement = Arrangement.Center,
                maxLines = 1,
                backgroundColor = Color.Transparent,
                enabled = hasContactChanged,
                enabledEffect = false,
                modifier = Modifier
                    .weight(1f)
                    .alpha(if (hasContactChanged) 1f else .2f),
                onClick = {
                    if (name.isBlank()) {
                        nameErrorMessage = context.getString(R.string.contact_error_name_empty)
                        return@Button
                    }
                    scope.launch {
                        val newContact = ContactInfo(id = contactId, name = name, photoUri = photoUri, useOfferKey = useOfferKey, offers = contactOffers, addresses = contactAddresses)
                        saveContactToDb(newContact)
                        onContactChange(newContact)
                    }
                },
            )
        }
        Column(modifier = Modifier.align(Alignment.TopCenter)) {
            Surface(shape = CircleShape, elevation = 2.dp) {
                ContactPhotoView(photoUri = photoUri, name = contact?.name, onChange = { photoUri = it }, imageSize = 135.dp, borderSize = 0.dp)
            }
            Spacer(Modifier.height(3.dp))
        }
    }

    Column(
        modifier = Modifier
            .verticalScroll(rememberScrollState())
            .fillMaxWidth()
            .weight(1f)
            .background(MaterialTheme.colors.background)
            .padding(top = 8.dp, bottom = 50.dp),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            TextInput(
                text = name,
                onTextChange = { name = it },
                textStyle = MaterialTheme.typography.h3.copy(textAlign = TextAlign.Center),
                placeholder = { Text(text = stringResource(R.string.contact_name_hint), style = MaterialTheme.typography.h3.copy(textAlign = TextAlign.Center, color = mutedTextColor), modifier = Modifier.fillMaxWidth()) },
                staticLabel = null,
                textFieldColors = invisibleOutlinedTextFieldColors(),
                errorMessage = nameErrorMessage,
                showResetButton = false,
                enabledEffect = false,
            )

            Row(
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(color = MaterialTheme.colors.surface, shape = RoundedCornerShape(16.dp))
            ) {
                val paymentCodes = paymentsCodeList.values.toList()
                val onSendClick: (String) -> Unit = { navController.navigate("${Screen.BusinessNavGraph.Send.route}?input=$it&fromRoute=back") }
                var showSendToAddressPickerDialog by remember { mutableStateOf(false) }

                if (showSendToAddressPickerDialog) {
                    SendToAddressPickerDialog(paymentCodes = paymentCodes, onCodeClick = onSendClick, onDismiss = { showSendToAddressPickerDialog = false})
                }

                Button(
                    text = stringResource(R.string.contact_pay_button),
                    icon = R.drawable.ic_send,
                    enabled = contact != null && paymentsCodeList.isNotEmpty(),
                    padding = PaddingValues(horizontal = 8.dp, vertical = 16.dp),
                    horizontalArrangement = Arrangement.Center,
                    maxLines = 1,
                    modifier = Modifier.weight(1f),
                    onClick = {
                        when {
                            paymentCodes.isEmpty() -> Unit
                            paymentCodes.size == 1 -> onSendClick(paymentCodes.first().paymentCode)
                            else -> showSendToAddressPickerDialog = true
                        }
                    },
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
        SwitchView(
            text = stringResource(id = R.string.contact_offer_key_title),
            description = if (useOfferKey) {
                stringResource(id = R.string.contact_offer_key_enabled)
            } else {
                stringResource(id = R.string.contact_offer_key_disabled)
            },
            checked = useOfferKey,
            onCheckedChange = { useOfferKey = it },
            modifier = Modifier
                .padding(horizontal = 12.dp, vertical = 6.dp)
                .background(MaterialTheme.colors.surface, shape = RoundedCornerShape(16.dp))
                .padding(horizontal = 12.dp, vertical = 6.dp)
        )

        ListPaymentCodesForContact(
            contactId = contactId,
            newOffer = newOffer,
            paymentCodeList = paymentsCodeList.values.toList(),
            getContactForOfferInDb = getContactForOfferInDb,
            getContactForAddressInDb = getContactForAddressInDb,
            onSaveContactPaymentCode = {
                paymentsCodeList = paymentsCodeList + (it.id to it)
            },
            onDeletePaymentCode = {
                paymentsCodeList = paymentsCodeList - it
            }
        )
    }
}

@Composable
private fun ListPaymentCodesForContact(
    contactId: UUID,
    newOffer: OfferTypes.Offer?,
    paymentCodeList: List<ContactPaymentCode>,
    getContactForOfferInDb: (OfferTypes.Offer) -> ContactInfo?,
    getContactForAddressInDb: (String) -> ContactInfo?,
    onSaveContactPaymentCode: (ContactPaymentCode) -> Unit,
    onDeletePaymentCode: (ByteVector32) -> Unit,
) {
    var showNewPaymentCodeDialog by remember { mutableStateOf(false) }
    var selectedPaymentCode by remember { mutableStateOf<ContactPaymentCode?>(null) }

    Text(
        text = stringResource(R.string.contact_codes_list),
        style = MaterialTheme.typography.subtitle2,
        modifier = Modifier.padding(horizontal = 28.dp, vertical = 6.dp)
    )

    if (paymentCodeList.isEmpty()) {
        Clickable(onClick = { showNewPaymentCodeDialog = true }) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp)
                    .background(MaterialTheme.colors.surface, shape = RoundedCornerShape(16.dp))
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                Surface(shape = CircleShape, color = MaterialTheme.typography.caption.color.copy(alpha = .3f), modifier = Modifier.size(18.dp)) {
                    PhoenixIcon(R.drawable.ic_plus, tint = MaterialTheme.colors.surface, modifier = Modifier.size(8.dp))
                }
                Spacer(Modifier.width(8.dp))
                Text(text = stringResource(R.string.contact_codes_add_new), style = MaterialTheme.typography.body1.copy(color = MaterialTheme.colors.primary))
            }
        }
    } else {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp)
                .background(MaterialTheme.colors.surface, shape = RoundedCornerShape(16.dp))
        ) {
            paymentCodeList.forEach { paymentCode ->
                val isNewOffer = newOffer != null && paymentCode is ContactOffer && paymentCode.offer == newOffer
                val data = when (paymentCode) {
                    is ContactOffer -> paymentCode.offer.encode()
                    is ContactAddress -> paymentCode.address
                }
                Clickable(onClick = { selectedPaymentCode = paymentCode }) {
                    Row(
                        modifier = Modifier
                            .height(IntrinsicSize.Min)
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        val label = paymentCode.label?.takeIf { it.isNotBlank() }
                        if (label != null) {
                            Text(
                                text = label,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.body1
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = data,
                                maxLines = 1,
                                overflow = TextOverflow.MiddleEllipsis,
                                modifier = Modifier.widthIn(min = 40.dp),
                                style = MaterialTheme.typography.subtitle2,
                                fontWeight = if (isNewOffer) FontWeight.Bold else FontWeight.Normal,
                            )
                        } else {
                            Text(
                                text = data,
                                maxLines = 1,
                                overflow = TextOverflow.MiddleEllipsis,
                                modifier = Modifier.weight(1f),
                                fontWeight = if (isNewOffer) FontWeight.Bold else FontWeight.Normal,
                            )
                        }
                    }
                }
            }
        }
        Clickable(onClick = { showNewPaymentCodeDialog = true }) {
            Row(modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 28.dp, vertical = 12.dp)
            ) {
                Surface(shape = CircleShape, color = MaterialTheme.typography.caption.color.copy(alpha = .5f), modifier = Modifier.size(18.dp)) {
                    PhoenixIcon(R.drawable.ic_plus, tint = mutedBgColor, modifier = Modifier.size(8.dp))
                }
                Spacer(Modifier.width(8.dp))
                Text(text = stringResource(R.string.contact_codes_add_new_short), style = MaterialTheme.typography.subtitle2)
            }
        }
    }

    val onDismiss = { showNewPaymentCodeDialog = false ; selectedPaymentCode = null }
    selectedPaymentCode?.let { paymentCode ->
        ContactOfferDetailDialog(
            contactId = contactId,
            paymentCode = paymentCode,
            getContactForOfferInDb = getContactForOfferInDb,
            getContactForAddressInDb = getContactForAddressInDb,
            onSavePaymentCode = {
                onSaveContactPaymentCode(it)
                onDismiss()
            },
            onDeletePaymentCode = {
                onDeletePaymentCode(it)
                onDismiss()
            },
            onDismiss = onDismiss
        )
    } ?: run {
        if (showNewPaymentCodeDialog) {
            ContactOfferDetailDialog(
                contactId = contactId,
                paymentCode = null,
                getContactForOfferInDb = getContactForOfferInDb,
                getContactForAddressInDb = getContactForAddressInDb,
                onSavePaymentCode = {
                    onSaveContactPaymentCode(it)
                    onDismiss()
                },
                onDeletePaymentCode = {
                    onDeletePaymentCode(it)
                    onDismiss()
                },
                onDismiss = onDismiss
            )
        }
    }
}

@Composable
private fun ContactOfferDetailDialog(
    contactId: UUID,
    paymentCode: ContactPaymentCode?,
    getContactForOfferInDb: (OfferTypes.Offer) -> ContactInfo?,
    getContactForAddressInDb: (String) -> ContactInfo?,
    onSavePaymentCode: (ContactPaymentCode) -> Unit,
    onDeletePaymentCode: (ByteVector32) -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismiss = onDismiss,
        skipPartiallyExpanded = true,
        isContentScrollable = true,
        internalPadding = PaddingValues(horizontal = 16.dp),
    ) {
        val context = LocalContext.current
        var code by remember { mutableStateOf(
            when (paymentCode) {
                is ContactOffer -> paymentCode.offer.encode()
                is ContactAddress -> paymentCode.address
                null -> ""
            }
        )}
        var label by remember { mutableStateOf(paymentCode?.label ?: "") }
        var errorMessage by remember { mutableStateOf<String?>(null) }
        var showScannerView by remember { mutableStateOf(false) }

        if (showScannerView) {
            FullScreenDialog(onDismiss = { showScannerView = false }) {
                Box(Modifier.fillMaxSize()) {
                    ScannerView(
                        onScannedText = {
                            code = Parser.trimMatchingPrefix(Parser.removeExcessInput(it), Parser.bitcoinPrefixes + Parser.lightningPrefixes)
                            errorMessage = null
                            showScannerView = false
                        },
                        isPaused = false,
                        onDismiss = { showScannerView = false }
                    )
                }
            }
        }

        Text(
            text = when (paymentCode) {
                is ContactOffer -> stringResource(R.string.contact_codedialog_title_offer)
                is ContactAddress -> stringResource(R.string.contact_codedialog_title_address)
                null -> stringResource(R.string.contact_codedialog_title_generic)
            },
            style = MaterialTheme.typography.body2,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(24.dp))
        TextInput(
            text = label,
            onTextChange = { label = it },
            staticLabel = stringResource(R.string.contact_codedialog_label_label),
            maxLines = 1,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(8.dp))
        TextInput(
            text = code,
            onTextChange = {
                errorMessage = null
                code = it
            },
            staticLabel = when (paymentCode) {
                is ContactOffer -> stringResource(R.string.contact_codedialog_label_offer)
                is ContactAddress -> stringResource(R.string.contact_codedialog_label_address)
                null -> stringResource(R.string.contact_codedialog_label_generic)
            },
            trailingIcon = when (paymentCode) {
                is ContactOffer, null -> {
                    {
                        Button(
                            onClick = { showScannerView = true },
                            icon = R.drawable.ic_scan_qr,
                            iconTint = MaterialTheme.colors.primary
                        )
                    }
                }
                is ContactAddress -> null
            },
            errorMessage = errorMessage,
            maxLines = 3,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(Modifier.height(24.dp))

        Row(modifier = Modifier.fillMaxWidth()) {
            if (paymentCode != null) {
                var showDeleteCodeConfirmation by remember { mutableStateOf(false) }
                if (showDeleteCodeConfirmation) {
                    ConfirmDialog(
                        message = stringResource(R.string.contact_delete_code_confirm),
                        onConfirm = { onDeletePaymentCode(paymentCode.id) ; showDeleteCodeConfirmation = false },
                        onDismiss = { showDeleteCodeConfirmation = false }
                    )
                }

                Spacer(Modifier.height(16.dp))
                FilledButton(
                    text = stringResource(R.string.btn_delete),
                    icon = R.drawable.ic_remove,
                    onClick = { showDeleteCodeConfirmation = true },
                    backgroundColor = Color.Transparent,
                    textStyle = MaterialTheme.typography.button,
                    iconTint = negativeColor,
                )
            }
            Spacer(Modifier.weight(1f))
            FilledButton(
                text = stringResource(R.string.btn_save),
                icon = R.drawable.ic_check,
                onClick = {

                    fun attemptOffer(code: String, onFailure: (String, Boolean) -> Unit) {
                        when (val result = Parser.readOffer(code)) {
                            null -> onFailure(context.getString(R.string.contact_error_offer_invalid), false)
                            else -> {
                                val match = getContactForOfferInDb(result)
                                when {
                                    match == null || match.id == contactId -> onSavePaymentCode(ContactOffer(offer = result, label = label.takeIf { it.isNotBlank() }, createdAt = currentTimestampMillis()))
                                    else -> onFailure(context.getString(R.string.contact_error_offer_known, match.name), true)
                                }
                            }
                        }
                    }

                    fun attemptAddress(code: String, onFailure: (String) -> Unit) {
                        when (Parser.parseEmailLikeAddress(code)) {
                            null -> onFailure(context.getString(R.string.contact_error_address_invalid))
                            else -> {
                                val match = getContactForAddressInDb(code)
                                when {
                                    match == null || match.id == contactId -> onSavePaymentCode(ContactAddress(address = code, label = label.takeIf { it.isNotBlank() }, createdAt = currentTimestampMillis()))
                                    else -> onFailure(context.getString(R.string.contact_error_address_known, match.name))
                                }
                            }
                        }
                    }

                    when (paymentCode) {
                        is ContactOffer -> attemptOffer(code, onFailure = { message, _ -> errorMessage = message })
                        is ContactAddress -> attemptAddress(code, onFailure = { errorMessage = it })
                        null -> attemptOffer(code, onFailure = { message, isFatal ->
                            if (isFatal) errorMessage = message
                            else attemptAddress(code, onFailure = { errorMessage = context.getString(R.string.contact_error_invalid) })
                        })
                    }
                }
            )
        }

        Spacer(Modifier.height(40.dp))
    }
}

@Composable
private fun SendToAddressPickerDialog(paymentCodes: List<ContactPaymentCode>, onCodeClick: (String) -> Unit, onDismiss: () -> Unit) {
    Popup(
        alignment = Alignment.TopEnd,
        offset = IntOffset(x = 0, y = 58),
        onDismissRequest = onDismiss,
    ) {
        Surface(
            modifier = Modifier.widthIn(min = 200.dp, max = 250.dp),
            shape = RoundedCornerShape(8.dp),
            color = MaterialTheme.colors.surface,
            elevation = 6.dp,
        ) {
            Column(modifier = Modifier.fillMaxWidth().heightIn(max = 300.dp)) {
                LazyColumn {
                    items(paymentCodes) { paymentCode ->
                        Clickable(onClick = { onCodeClick(paymentCode.paymentCode) }) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(12.dp, 8.dp),
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                val (main, details) = remember(paymentCode) {
                                    if (paymentCode.label.isNullOrBlank()) {
                                        paymentCode.paymentCode to null
                                    } else {
                                        paymentCode.label!! to paymentCode.paymentCode
                                    }
                                }
                                PhoenixIcon(R.drawable.ic_send, tint = MaterialTheme.colors.primary, modifier = Modifier.size(14.dp).align(Alignment.CenterVertically))
                                Text(text = main, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.alignByBaseline())
                                details?.let { Text(text = it, style = MaterialTheme.typography.subtitle2, maxLines = 1, overflow = TextOverflow.MiddleEllipsis, modifier = Modifier.alignByBaseline()) }
                            }
                        }
                    }
                }
            }
        }
    }
}
