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

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidViewBinding
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.google.accompanist.permissions.shouldShowRationale
import com.google.zxing.BarcodeFormat
import com.google.zxing.ResultPoint
import com.google.zxing.client.android.Intents
import com.journeyapps.barcodescanner.BarcodeCallback
import com.journeyapps.barcodescanner.BarcodeResult
import com.journeyapps.barcodescanner.DecoratedBarcodeView
import fr.acinq.phoenix.android.CF
import fr.acinq.phoenix.android.R
import fr.acinq.phoenix.android.business
import fr.acinq.phoenix.android.components.Button
import fr.acinq.phoenix.android.components.Card
import fr.acinq.phoenix.android.components.Clickable
import fr.acinq.phoenix.android.components.Dialog
import fr.acinq.phoenix.android.components.PhoenixIcon
import fr.acinq.phoenix.android.components.ProgressView
import fr.acinq.phoenix.android.components.TextInput
import fr.acinq.phoenix.android.components.VSeparator
import fr.acinq.phoenix.android.components.contact.ContactsListModal
import fr.acinq.phoenix.android.components.mvi.MVIControllerViewModel
import fr.acinq.phoenix.android.components.mvi.MVIView
import fr.acinq.phoenix.android.controllerFactory
import fr.acinq.phoenix.android.databinding.ScanViewBinding
import fr.acinq.phoenix.android.payments.LnurlAuthView
import fr.acinq.phoenix.android.payments.LnurlPayView
import fr.acinq.phoenix.android.payments.LnurlWithdrawView
import fr.acinq.phoenix.android.payments.SendBolt11PaymentView
import fr.acinq.phoenix.android.payments.offer.SendOfferView
import fr.acinq.phoenix.android.payments.spliceout.SendSpliceOutView
import fr.acinq.phoenix.android.utils.readClipboard
import fr.acinq.phoenix.controllers.ControllerFactory
import fr.acinq.phoenix.controllers.ScanController
import fr.acinq.phoenix.controllers.payments.Scan
import fr.acinq.phoenix.data.lnurl.LnurlError


class ScanDataViewModel(controller: ScanController) : MVIControllerViewModel<Scan.Model, Scan.Intent>(controller) {
    class Factory(
        private val controllerFactory: ControllerFactory,
        private val getController: ControllerFactory.() -> ScanController
    ) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            @Suppress("UNCHECKED_CAST")
            return ScanDataViewModel(controllerFactory.getController()) as T
        }
    }
}

/**
 * @param input External input, for example a deeplink from another app, which needs to be parsed and validated.
 *              When not null, the camera should not be initialized at first as we already have a data input.
 */
@Composable
fun ScanAndSendView(
    input: String? = null,
    onBackClick: () -> Unit,
    onProcessingFinished: () -> Unit,
    onAuthSchemeInfoClick: () -> Unit,
    onFeeManagementClick: () -> Unit,
) {
    var initialInput = remember { input }
    val peer by business.peerManager.peerState.collectAsState()
    val trampolineFees = peer?.walletParams?.trampolineFees?.firstOrNull()
    val vm: ScanDataViewModel = viewModel(factory = ScanDataViewModel.Factory(controllerFactory, CF::scan))

    MVIView(vm) { model, postIntent ->
        LaunchedEffect(key1 = initialInput) {
            initialInput?.takeIf { it.isNotBlank() }?.let {
                postIntent(Scan.Intent.Parse(it))
            }
        }
        when (model) {
            Scan.Model.Ready, is Scan.Model.BadRequest, is Scan.Model.LnurlServiceFetch, is Scan.Model.ResolvingBip353 -> {
                PrepareSendView(
                    initialInput = initialInput,
                    model = model,
                    onReset = {
                        initialInput = ""
                        postIntent(Scan.Intent.Reset)
                    },
                    onInputSubmit = { postIntent(Scan.Intent.Parse(request = it)) },
                    onBackClick = onBackClick,
                )
            }
            is Scan.Model.Bolt11InvoiceFlow.Bolt11InvoiceRequest -> {
                SendBolt11PaymentView(
                    invoice = model.invoice,
                    trampolineFees = trampolineFees,
                    onBackClick = onBackClick,
                    onPayClick = { postIntent(it) }
                )
            }
            Scan.Model.Bolt11InvoiceFlow.Sending -> {
                LaunchedEffect(key1 = Unit) { onBackClick() }
            }
            is Scan.Model.OfferFlow -> {
                SendOfferView(offer = model.offer, trampolineFees = trampolineFees, onBackClick = onBackClick, onPaymentSent = onProcessingFinished)
            }
            is Scan.Model.OnchainFlow -> {
                val lightningRequest = model.uri.paymentRequest?.write() ?: model.uri.offer?.encode()
                if (lightningRequest == null) {
                    SendSpliceOutView(
                        requestedAmount = model.uri.amount,
                        address = model.uri.address,
                        onBackClick = onBackClick,
                        onSpliceOutSuccess = onProcessingFinished,
                    )
                } else {
                    var showPaymentModeDialog by remember { mutableStateOf(false) }
                    if (!showPaymentModeDialog) {
                        ChoosePaymentModeDialog(
                            onPayOffchainClick = {
                                showPaymentModeDialog = true
                                postIntent(Scan.Intent.Parse(request = lightningRequest))
                            },
                            onPayOnchainClick = {
                                showPaymentModeDialog = true
                                postIntent(Scan.Intent.Parse(request = model.uri.copy(paymentRequest = null, offer = null).write()))
                            },
                            onDismiss = {
                                showPaymentModeDialog = false
                            }
                        )
                    }
                }
            }
            is Scan.Model.LnurlPayFlow -> {
                LnurlPayView(
                    model = model,
                    trampolineFees = trampolineFees,
                    onBackClick = onBackClick,
                    onSendLnurlPayClick = { postIntent(it) }
                )
            }
            is Scan.Model.LnurlAuthFlow -> {
                LnurlAuthView(
                    model = model,
                    onBackClick = onBackClick,
                    onLoginClick = { postIntent(it) },
                    onAuthSchemeInfoClick = onAuthSchemeInfoClick,
                    onAuthDone = onProcessingFinished,
                )
            }
            is Scan.Model.LnurlWithdrawFlow -> {
                LnurlWithdrawView(
                    model = model,
                    onWithdrawClick = { postIntent(it) },
                    onFeeManagementClick = onFeeManagementClick,
                    onWithdrawDone = onProcessingFinished,
                )
            }
        }
    }
}

@Composable
fun ReadDataView(
    initialInput: String?,
    model: Scan.Model,
    onFeedbackDismiss: () -> Unit,
    onScannedText: (String) -> Unit,
    onBackClick: () -> Unit,
) {
    val context = LocalContext.current.applicationContext

    var showContactsList by remember { mutableStateOf(false) }
    var showManualInputDialog by remember { mutableStateOf(false) }
    var scanView by remember { mutableStateOf<DecoratedBarcodeView?>(null) }

    Box(Modifier.fillMaxSize()) {

        if (initialInput.isNullOrBlank()) {
            ScannerView(
                onScanViewBinding = { scanView = it },
                onScannedText = onScannedText
            )
            CameraPermissionsView {
                DisposableEffect(key1 = model, key2 = initialInput) {
                    if (model is Scan.Model.Ready && initialInput.isNullOrBlank()) scanView?.resume()
                    onDispose {
                        scanView?.pause()
                    }
                }
            }
        }

        // buttons at the bottom of the screen
        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(24.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(MaterialTheme.colors.surface)
                .height(IntrinsicSize.Min),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BackHandler(onBack = onBackClick)
            Button(icon = R.drawable.ic_arrow_back, onClick = onBackClick, modifier = Modifier.fillMaxHeight(), padding = PaddingValues(horizontal = 12.dp))
            VSeparator(height = 48.dp)
            Column(modifier = Modifier.weight(1f)) {
                if (model is Scan.Model.Ready) {
                    Button(
                        modifier = Modifier.fillMaxWidth(),
                        icon = R.drawable.ic_user_search,
                        text = stringResource(id = R.string.settings_contacts),
                        onClick = { showContactsList = true },
                        maxLines = 1,
                    )
                    Button(
                        modifier = Modifier.fillMaxWidth(),
                        icon = R.drawable.ic_clipboard,
                        text = stringResource(id = R.string.scan_paste_button),
                        onClick = { readClipboard(context)?.let { onScannedText(it) } },
                        maxLines = 1,
                    )
                    Button(
                        modifier = Modifier.fillMaxWidth(),
                        icon = R.drawable.ic_input,
                        text = stringResource(id = R.string.scan_manual_input_button),
                        onClick = { showManualInputDialog = true },
                        maxLines = 1,
                    )
                }
            }
        }

        if (model is Scan.Model.BadRequest) {
            ScanErrorView(model, onFeedbackDismiss)
        }

        if (model is Scan.Model.LnurlServiceFetch) {
            Card(modifier = Modifier.align(Alignment.Center), internalPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)) {
                ProgressView(text = stringResource(R.string.scan_lnurl_fetching))
            }
        }

        if (model is Scan.Model.ResolvingBip353) {
            Card(modifier = Modifier.align(Alignment.Center), internalPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)) {
                ProgressView(text = stringResource(R.string.scan_bip353_resolving))
            }
        }

        if (showManualInputDialog) {
            ManualInputDialog(onInputConfirm = onScannedText, onDismiss = { showManualInputDialog = false })
        }

        if (showContactsList) {
            ContactsListModal(onDismiss = { showContactsList = false })
        }
    }
}

@Composable
fun BoxScope.ScannerView(
    onScanViewBinding: (DecoratedBarcodeView) -> Unit,
    onScannedText: (String) -> Unit
) {
    // scanner view using a legacy binding
    AndroidViewBinding(
        modifier = Modifier.fillMaxSize().background(Color.Red),
        factory = { inflater, viewGroup, attach ->
            val binding = ScanViewBinding.inflate(inflater, viewGroup, attach)
            binding.scanView.let { scanView ->
                scanView.initializeFromIntent(Intent().apply {
                    putExtra(Intents.Scan.SCAN_TYPE, Intents.Scan.MIXED_SCAN)
                    putExtra(Intents.Scan.FORMATS, BarcodeFormat.QR_CODE.name)
                })
                scanView.decodeContinuous(object : BarcodeCallback {
                    override fun possibleResultPoints(resultPoints: MutableList<ResultPoint>?) = Unit
                    override fun barcodeResult(result: BarcodeResult?) {
                        result?.text?.trim()?.takeIf { it.isNotBlank() }?.let {
                            scanView.pause()
                            onScannedText(it)
                        }
                    }
                })
                onScanViewBinding(scanView)
                scanView.resume()
            }
            binding
        }
    )
}

@Composable
fun ScanErrorView(
    model: Scan.Model.BadRequest,
    onErrorDialogDismiss: () -> Unit,
) {
    val message = when (val reason = model.reason) {
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
    Dialog(
        onDismiss = onErrorDialogDismiss,
        content = { Text(text = message, modifier = Modifier.padding(top = 24.dp, start = 24.dp, end = 24.dp)) }
    )
}

@Composable
private fun ChoosePaymentModeDialog(
    onPayOnchainClick: () -> Unit,
    onPayOffchainClick: () -> Unit,
    onDismiss: () -> Unit,
) {
    Dialog(onDismiss = onDismiss, properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = true), isScrollable = true, buttons = null) {
        Clickable(onClick = onPayOnchainClick) {
            Row(modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                PhoenixIcon(resourceId = R.drawable.ic_chain)
                Spacer(Modifier.width(16.dp))
                Column {
                    Text(text = stringResource(id = R.string.send_paymentmode_onchain), style = MaterialTheme.typography.body2)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(text = stringResource(id = R.string.send_paymentmode_onchain_desc), style = MaterialTheme.typography.caption.copy(fontSize = 14.sp))
                }
            }
        }
        Clickable(onClick = onPayOffchainClick) {
            Row(modifier = Modifier.padding(horizontal = 24.dp, vertical = 18.dp), verticalAlignment = Alignment.CenterVertically) {
                PhoenixIcon(resourceId = R.drawable.ic_zap)
                Spacer(Modifier.width(16.dp))
                Column {
                    Text(text = stringResource(id = R.string.send_paymentmode_lightning), style = MaterialTheme.typography.body2)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(text = stringResource(id = R.string.send_paymentmode_lightning_desc), style = MaterialTheme.typography.caption.copy(fontSize = 14.sp))
                }
            }
        }
    }
}

@Composable
private fun ManualInputDialog(
    onInputConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var input by remember { mutableStateOf("") }
    Dialog(
        onDismiss = onDismiss,
        title = stringResource(id = R.string.scan_manual_input_title),
        buttons = {
            Button(onClick = onDismiss, text = stringResource(id = R.string.btn_cancel), padding = PaddingValues(16.dp))
            Button(onClick = { onInputConfirm(input); onDismiss() }, text = stringResource(id = R.string.btn_ok), padding = PaddingValues(16.dp))
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
        ) {
            Text(text = stringResource(id = R.string.scan_manual_input_instructions))
            Spacer(Modifier.height(16.dp))
            TextInput(
                text = input,
                onTextChange = { input = it },
                modifier = Modifier.fillMaxWidth(),
                minLines = 2,
                maxLines = 3,
                staticLabel = stringResource(id = R.string.scan_manual_input_label),
                placeholder = { Text(text = stringResource(id = R.string.scan_manual_input_hint)) },
                keyboardType = KeyboardType.Email,
            )
        }
    }
}

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun BoxScope.CameraPermissionsView(
    onPermissionGranted: @Composable () -> Unit
) {
    val context = LocalContext.current
    val cameraPermissionState = rememberPermissionState(Manifest.permission.CAMERA)
    if (cameraPermissionState.status.isGranted) {
        onPermissionGranted()
    } else {
        Card(
            modifier = Modifier.align(Alignment.Center),
        ) {
            // if user has denied permission, open the system settings for Phoenix
            val isDenied = cameraPermissionState.status.shouldShowRationale
            Button(
                icon = R.drawable.ic_camera,
                text = stringResource(id = if (isDenied) R.string.scan_request_camera_access_denied else R.string.scan_request_camera_access),
                onClick = {
                    if (isDenied) {
                        context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                            data = Uri.fromParts("package", context.packageName, null)
                        })
                    } else {
                        cameraPermissionState.launchPermissionRequest()
                    }
                }
            )
        }
    }
}
