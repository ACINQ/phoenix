/*
 * Copyright 2022 ACINQ SAS
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


package fr.acinq.phoenix.android.payments

import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidViewBinding
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.zxing.BarcodeFormat
import com.google.zxing.ResultPoint
import com.google.zxing.client.android.Intents
import com.journeyapps.barcodescanner.BarcodeCallback
import com.journeyapps.barcodescanner.BarcodeResult
import com.journeyapps.barcodescanner.DecoratedBarcodeView
import fr.acinq.lightning.payment.PaymentRequest
import fr.acinq.phoenix.android.CF
import fr.acinq.phoenix.android.R
import fr.acinq.phoenix.android.components.Button
import fr.acinq.phoenix.android.components.Clickable
import fr.acinq.phoenix.android.components.Dialog
import fr.acinq.phoenix.android.components.PhoenixIcon
import fr.acinq.phoenix.android.components.mvi.MVIControllerViewModel
import fr.acinq.phoenix.android.components.mvi.MVIView
import fr.acinq.phoenix.android.controllerFactory
import fr.acinq.phoenix.android.databinding.ScanViewBinding
import fr.acinq.phoenix.android.utils.annotatedStringResource
import fr.acinq.phoenix.android.utils.datastore.UserPrefs
import fr.acinq.phoenix.android.utils.logger
import fr.acinq.phoenix.android.utils.readClipboard
import fr.acinq.phoenix.android.utils.whiteLowOp
import fr.acinq.phoenix.controllers.ControllerFactory
import fr.acinq.phoenix.controllers.ScanController
import fr.acinq.phoenix.controllers.payments.MaxFees
import fr.acinq.phoenix.controllers.payments.Scan


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

@Composable
fun ScanDataView(
    onBackClick: () -> Unit,
) {
    val trampolineMaxFees by UserPrefs.getTrampolineMaxFee(LocalContext.current).collectAsState(null)
    val maxFees = trampolineMaxFees?.let { MaxFees(it.feeBase, it.feeProportional) }
    val vm: ScanDataViewModel = viewModel(factory = ScanDataViewModel.Factory(controllerFactory, CF::scan))
    MVIView(vm) { model, postIntent ->
        when (model) {
            Scan.Model.Ready, is Scan.Model.BadRequest, is Scan.Model.InvoiceFlow.DangerousRequest,
            is Scan.Model.LnurlWithdrawFlow, is Scan.Model.LnurlAuthFlow, is Scan.Model.LnurlServiceFetch, is Scan.Model.LnurlPayFlow -> {
                ReadDataView(
                    model = model,
                    onBackClick = onBackClick,
                    onFeedbackDismiss = { vm.model = Scan.Model.Ready },
                    onConfirmDangerousRequest = { request, invoice -> postIntent(Scan.Intent.InvoiceFlow.ConfirmDangerousRequest(request, invoice)) },
                    onScannedText = { postIntent(Scan.Intent.Parse(request = it)) }
                )
            }
            is Scan.Model.InvoiceFlow.InvoiceRequest -> {
                SendLightningPaymentView(
                    paymentRequest = model.paymentRequest,
                    trampolineMaxFees = maxFees,
                    onBackClick = onBackClick,
                    onPayClick = { postIntent(it) }
                )
            }
            is Scan.Model.SwapOutFlow -> {
                val paymentRequest = model.address.paymentRequest
                if (paymentRequest == null) {
                    SendSwapOutView(
                        model = model,
                        maxFees = maxFees,
                        onBackClick = onBackClick,
                        onInvalidate = { postIntent(it) },
                        onPrepareSwapOutClick = { postIntent(it) },
                        onSendSwapOutClick = { postIntent(it) }
                    )
                } else {
                    var hasPickedSwapOutMode by remember { mutableStateOf(false) }
                    if (!hasPickedSwapOutMode) {
                        SwapOutOrLightningDialog(
                            onPayWithLightningClick = {
                                hasPickedSwapOutMode = true
                                postIntent(Scan.Intent.Parse(request = paymentRequest.write()))
                            },
                            onPayWithSwapOutClick = {
                                hasPickedSwapOutMode = true
                                postIntent(Scan.Intent.Parse(request = model.address.copy(paymentRequest = null).write()))
                            }
                        )
                    }
                }
            }
            Scan.Model.InvoiceFlow.Sending -> Text("Sending payment...")
        }
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun ReadDataView(
    model: Scan.Model,
    onFeedbackDismiss: () -> Unit,
    onBackClick: () -> Unit,
    onConfirmDangerousRequest: (String, PaymentRequest) -> Unit,
    onScannedText: (String) -> Unit,
) {
    val context = LocalContext.current.applicationContext
    val log = logger("ReadDataView")

    var _scanView by remember { mutableStateOf<DecoratedBarcodeView?>(null) }

    Box {

        ScannerView(
            onScanViewBinding = { _scanView = it },
            onScannedText = onScannedText
        )

        // visor
        Box(
            Modifier
                .width(dimensionResource(id = R.dimen.scanner_size))
                .height(dimensionResource(id = R.dimen.scanner_size))
                .clip(RoundedCornerShape(24.dp))
                .background(whiteLowOp())
                .align(Alignment.Center)
        )

        val feedbackMessage = when {
            model is Scan.Model.BadRequest && model.reason is Scan.BadRequestReason.ChainMismatch -> stringResource(R.string.scan_error_invalid_chain)
            model is Scan.Model.BadRequest && model.reason is Scan.BadRequestReason.AlreadyPaidInvoice -> stringResource(R.string.scan_error_pay_to_self)
            model is Scan.Model.BadRequest && model.reason is Scan.BadRequestReason.ServiceError -> stringResource(R.string.scan_error_lnurl_service_error)
            model is Scan.Model.BadRequest && model.reason is Scan.BadRequestReason.InvalidLnUrl -> stringResource(R.string.scan_error_lnurl_invalid)
            model is Scan.Model.BadRequest && model.reason is Scan.BadRequestReason.UnsupportedLnUrl -> stringResource(R.string.scan_error_lnurl_unsupported)
            model is Scan.Model.BadRequest && model.reason is Scan.BadRequestReason.UnknownFormat -> stringResource(R.string.scan_error_invalid_generic)
            model is Scan.Model.LnurlServiceFetch -> stringResource(R.string.scan_lnurl_fetching)
            model is Scan.Model.LnurlPayFlow -> "Lnurl-pay not suppported yet"
            model is Scan.Model.LnurlWithdrawFlow -> "Lnurl-withdraw not suppported yet"
            model is Scan.Model.LnurlAuthFlow -> "Lnurl-auth not suppported yet"
            else -> null
        }
        if (feedbackMessage != null) {
            Dialog(
                onDismiss = onFeedbackDismiss,
                content = { Text(feedbackMessage, modifier = Modifier.padding(top = 24.dp, start = 24.dp, end = 24.dp)) }
            )
        } else if (model is Scan.Model.InvoiceFlow.DangerousRequest) {
            Dialog(
                onDismiss = {},
                properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false, usePlatformDefaultWidth = false),
                title = stringResource(id = R.string.scan_amountless_legacy_title),
                buttons = {
                    Button(
                        onClick = onFeedbackDismiss,
                        text = stringResource(id = R.string.btn_cancel)
                    )
                    Spacer(Modifier.width(8.dp))
                    Button(
                        onClick = { onConfirmDangerousRequest(model.request, model.paymentRequest) },
                        text = stringResource(id = R.string.btn_confirm)
                    )
                },
                content = { Text(annotatedStringResource(id = R.string.scan_amountless_legacy_message), modifier = Modifier.padding(horizontal = 24.dp)) }
            )
        }

        // buttons at the bottom of the screen
        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .padding(24.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(MaterialTheme.colors.surface)
        ) {
            if (model is Scan.Model.Ready) {
                LaunchedEffect(key1 = model) { _scanView?.resume() }
                Button(
                    modifier = Modifier.fillMaxWidth(),
                    icon = R.drawable.ic_clipboard,
                    text = stringResource(id = R.string.send_init_paste),
                    onClick = { readClipboard(context)?.let { onScannedText(it) } },
                )
            }
            Button(
                modifier = Modifier.fillMaxWidth(),
                text = stringResource(id = R.string.btn_cancel),
                icon = R.drawable.ic_arrow_back,
                onClick = onBackClick
            )
        }
    }
}

@Composable
private fun ScannerView(
    onScanViewBinding: (DecoratedBarcodeView) -> Unit,
    onScannedText: (String) -> Unit
) {
    val log = logger("ScannerView")
    // scanner view using a legacy binding
    AndroidViewBinding(
        modifier = Modifier.fillMaxWidth(),
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
                            log.debug { "scanned text=$it" }
                            onScannedText(it)
                        }
                    }
                })
                onScanViewBinding(scanView)
                scanView.resume()
            }
            binding
        })
}

@Composable
private fun SwapOutOrLightningDialog(
    onPayWithSwapOutClick: () -> Unit,
    onPayWithLightningClick: () -> Unit,
) {
    Dialog(onDismiss = {}, properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false), isScrollable = true) {
        Clickable(onClick = { onPayWithSwapOutClick() }) {
            Row(modifier = Modifier.padding(24.dp), verticalAlignment = Alignment.CenterVertically) {
                PhoenixIcon(resourceId = R.drawable.ic_chain)
                Spacer(Modifier.width(16.dp))
                Column {
                    Text(text = stringResource(id = R.string.send_paymentmode_onchain), style = MaterialTheme.typography.body2)
                    Text(text = stringResource(id = R.string.send_paymentmode_onchain_desc), style = MaterialTheme.typography.caption.copy(fontSize = 14.sp))
                }
            }
        }
        Clickable(onClick = { onPayWithLightningClick() }) {
            Row(modifier = Modifier.padding(24.dp), verticalAlignment = Alignment.CenterVertically) {
                PhoenixIcon(resourceId = R.drawable.ic_zap)
                Spacer(Modifier.width(16.dp))
                Column {
                    Text(text = stringResource(id = R.string.send_paymentmode_lightning), style = MaterialTheme.typography.body2)
                    Text(text = stringResource(id = R.string.send_paymentmode_lightning_desc), style = MaterialTheme.typography.caption.copy(fontSize = 14.sp))
                }
            }
        }
    }
}
