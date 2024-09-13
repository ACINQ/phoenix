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

package fr.acinq.phoenix.android.payments.send

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.indication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.material.ripple.rememberRipple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.journeyapps.barcodescanner.DecoratedBarcodeView
import fr.acinq.phoenix.android.R
import fr.acinq.phoenix.android.business
import fr.acinq.phoenix.android.components.Card
import fr.acinq.phoenix.android.components.CardHeader
import fr.acinq.phoenix.android.components.Clickable
import fr.acinq.phoenix.android.components.DefaultScreenHeader
import fr.acinq.phoenix.android.components.DefaultScreenLayout
import fr.acinq.phoenix.android.components.Dialog
import fr.acinq.phoenix.android.components.FilledButton
import fr.acinq.phoenix.android.components.PhoenixIcon
import fr.acinq.phoenix.android.components.contact.ContactPhotoView
import fr.acinq.phoenix.android.isDarkTheme
import fr.acinq.phoenix.android.utils.gray300
import fr.acinq.phoenix.android.utils.gray800
import fr.acinq.phoenix.android.utils.negativeColor
import fr.acinq.phoenix.android.utils.readClipboard
import fr.acinq.phoenix.controllers.payments.Scan
import fr.acinq.phoenix.data.ContactInfo
import fr.acinq.phoenix.data.lnurl.LnurlError
import kotlinx.coroutines.flow.map


@Composable
fun PrepareSendView(
    initialInput: String?,
    model: Scan.Model,
    onReset: () -> Unit,
    onInputSubmit: (String) -> Unit,
    onBackClick: () -> Unit,
) {
    val context = LocalContext.current

    var freeFormInput by remember { mutableStateOf(initialInput ?: "") }
    var showScanner by remember { mutableStateOf(false) }
    val keyboardManager = LocalSoftwareKeyboardController.current

    val vm = viewModel<PrepareSendViewModel>()

    DefaultScreenLayout(backgroundColor = MaterialTheme.colors.background) {
        DefaultScreenHeader(title = "Send", onBackClick = onBackClick)

        SendSmartInput(
            value = freeFormInput,
            onValueChange = {
                if (it.isBlank()) onReset()
                freeFormInput = it
            },
            onValueSubmit = { onInputSubmit(freeFormInput) },
            isProcessing = model is Scan.Model.ResolvingBip353 || model is Scan.Model.LnurlServiceFetch,
            isError = model is Scan.Model.BadRequest,
        )
        Spacer(modifier = Modifier.height(8.dp))

        if (!showScanner) {
            if (model is Scan.Model.BadRequest) {
                PaymentDataError(
                    errorMessage = translatePaymentDataError(model),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 28.dp, top = 0.dp, bottom = 8.dp, end = 16.dp),
                    textStyle = MaterialTheme.typography.body1.copy(fontSize = 15.sp, color = negativeColor)
                )
            } else {
                when (vm.readImageState) {
                    is ReadImageState.Error -> PaymentDataError(errorMessage = "This image could not be processed")
                    is ReadImageState.NotFound -> PaymentDataError(errorMessage = "No QR code found")
                    else -> {}
                }
            }
        }

        Spacer(modifier = Modifier.height(4.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min)
        ) {
            var imageUri by remember { mutableStateOf<Uri?>(null) }
            val imagePickerLauncher = rememberLauncherForActivityResult(contract = ActivityResultContracts.GetContent()) {
                imageUri = it
            }
            LaunchedEffect(key1 = imageUri) {
                imageUri?.let {
                    onReset()
                    vm.readImage(context, it, onDataFound = onInputSubmit)
                }
            }
            ReadDataButton(label = "Paste", icon = R.drawable.ic_paste, onClick = { readClipboard(context)?.let { onInputSubmit(it) } })
            ReadDataButton(label = "Choose image", icon = R.drawable.ic_image, onClick = { imagePickerLauncher.launch("image/*") }, enabled = vm.readImageState.canProcess)
            ReadDataButton(label = "Scan QR code", icon = R.drawable.ic_scan_qr, onClick = {
                onReset()
                keyboardManager?.hide()
                showScanner = true
            })
        }

        val contacts = business.contactsManager.contactsList.map { list ->
            freeFormInput.takeIf { it.isNotBlank() && it.length >= 2 }?.let { filter ->
                list.filter { it.name.contains(filter, ignoreCase = true) }
            } ?: list
        }.map { it.take(3) }.collectAsState(emptyList())

        if (contacts.value.isNotEmpty()) {
            Spacer(modifier = Modifier.height(24.dp))
            CardHeader(text = "Recently paid contacts")
            Card(modifier = Modifier.fillMaxWidth()) {
                contacts.value.map {
                    ContactRow(contactInfo = it, onClick = { it.mostRelevantOffer?.let { onInputSubmit(it.encode()) } })
                }
            }
        }
    }

    if (showScanner) {
        ScannerBox(model = model, onDismiss = { onReset() ; showScanner = false }, onReset = onReset, onSubmit = onInputSubmit)
    }
}

@Composable
private fun PaymentDataError(errorMessage: String, modifier: Modifier = Modifier, textStyle: TextStyle = MaterialTheme.typography.body1) {
    Row(modifier) {
        PhoenixIcon(resourceId = R.drawable.ic_alert_triangle, tint = negativeColor, modifier = Modifier.size(18.dp))
        Spacer(modifier = Modifier.width(8.dp))
        Text(text = errorMessage, style = textStyle)
    }
}

@Composable
private fun ScannerBox(
    model: Scan.Model,
    onReset: () -> Unit,
    onDismiss: () -> Unit,
    onSubmit: (String) -> Unit,
) {
    var scanView by remember { mutableStateOf<DecoratedBarcodeView?>(null) }
    Box(Modifier.fillMaxSize()) {
        ScannerView(
            onScanViewBinding = { scanView = it },
            onScannedText = onSubmit
        )
        CameraPermissionsView {
            DisposableEffect(Unit) {
                scanView?.resume()
                onDispose { scanView?.pause() }
            }
        }

        BackHandler(onBack = onDismiss)
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
        ) {
            if (model is Scan.Model.BadRequest) {
                Dialog(onDismiss = {
                    onReset()
                    scanView?.resume()
                }) {
                    PaymentDataError(errorMessage = translatePaymentDataError(model), modifier = Modifier.padding(16.dp))
                }
                Spacer(modifier = Modifier.height(16.dp))
            }
            FilledButton(
                text = stringResource(id = R.string.btn_cancel),
                icon = R.drawable.ic_arrow_back,
                iconTint = MaterialTheme.colors.onSurface,
                backgroundColor = MaterialTheme.colors.surface,
                textStyle = MaterialTheme.typography.button,
                padding = PaddingValues(16.dp),
                onClick = onDismiss,
                modifier = Modifier
                    .padding(horizontal = 16.dp)
                    .fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun RowScope.ReadDataButton(
    label: String,
    icon: Int,
    onClick: () -> Unit,
    enabled: Boolean = true,
) {
    val interactionSource = remember { MutableInteractionSource() }
    Clickable(
        modifier = Modifier
            .weight(1f)
            .fillMaxHeight()
            .alpha(if (enabled) 1f else 0.35f),
        onClick = onClick,
        interactionSource = interactionSource,
        indication = null,
        enabled = enabled,
    ) {
        Column(
            modifier = Modifier.padding(top = 12.dp, start = 8.dp, end = 8.dp, bottom = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .indication(interactionSource, rememberRipple(bounded = false, color = if (isDarkTheme) gray300 else gray800, radius = 32.dp))
                    .clip(CircleShape)
                    .border(width = 1.dp, color = MaterialTheme.colors.primary, shape = CircleShape)
                    .background(MaterialTheme.colors.surface)
                    .padding(14.dp),
            ) {
                PhoenixIcon(resourceId = icon, tint = MaterialTheme.colors.primary, modifier = Modifier.size(22.dp))
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(text = label, style = MaterialTheme.typography.body2.copy(fontSize = 12.sp), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun ContactRow(
    contactInfo: ContactInfo,
    onClick: () -> Unit,
) {
    Clickable(modifier = Modifier.fillMaxWidth(), onClick = onClick) {
        Row(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            ContactPhotoView(photoUri = contactInfo.photoUri, name = contactInfo.name, onChange = null, imageSize = 28.dp)
            Spacer(modifier = Modifier.width(8.dp))
            Text(text = contactInfo.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
fun translatePaymentDataError(
    model: Scan.Model.BadRequest
): String {
    return when (val reason = model.reason) {
        is Scan.BadRequestReason.Expired -> stringResource(R.string.scan_error_expired)
        is Scan.BadRequestReason.ChainMismatch -> stringResource(R.string.scan_error_invalid_chain)
        is Scan.BadRequestReason.AlreadyPaidInvoice -> stringResource(R.string.scan_error_already_paid)
        is Scan.BadRequestReason.ServiceError -> when (val error = reason.error) {
            is LnurlError.RemoteFailure.Code -> stringResource(R.string.lnurl_error_remote_code, reason.url.host, error.code.value.toString())
            is LnurlError.RemoteFailure.CouldNotConnect -> stringResource(R.string.lnurl_error_remote_connection, reason.url.host)
            is LnurlError.RemoteFailure.Detailed -> stringResource(R.string.lnurl_error_remote_details, reason.url.host, error.reason)
            is LnurlError.RemoteFailure.Unreadable -> stringResource(R.string.lnurl_error_remote_unreadable, reason.url.host)
        }
        is Scan.BadRequestReason.InvalidLnurl -> stringResource(R.string.scan_error_lnurl_invalid)
        is Scan.BadRequestReason.UnsupportedLnurl -> stringResource(R.string.scan_error_lnurl_unsupported)
        is Scan.BadRequestReason.UnknownFormat -> stringResource(R.string.scan_error_invalid_generic)
        is Scan.BadRequestReason.Bip353NameNotFound -> stringResource(id = R.string.scan_error_bip353_name_not_found, reason.username, reason.domain)
        is Scan.BadRequestReason.Bip353InvalidUri -> stringResource(id = R.string.scan_error_bip353_invalid_uri)
        is Scan.BadRequestReason.Bip353InvalidOffer -> stringResource(id = R.string.scan_error_bip353_invalid_offer)
        is Scan.BadRequestReason.Bip353NoDNSSEC -> stringResource(id = R.string.scan_error_bip353_dnssec)
    }
}
