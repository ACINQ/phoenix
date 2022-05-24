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

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidViewBinding
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.zxing.BarcodeFormat
import com.google.zxing.ResultPoint
import com.google.zxing.client.android.Intents
import com.journeyapps.barcodescanner.BarcodeCallback
import com.journeyapps.barcodescanner.BarcodeResult
import com.journeyapps.barcodescanner.DecoratedBarcodeView
import fr.acinq.phoenix.android.CF
import fr.acinq.phoenix.android.R
import fr.acinq.phoenix.android.components.Button
import fr.acinq.phoenix.android.components.mvi.MVIControllerViewModel
import fr.acinq.phoenix.android.components.mvi.MVIView
import fr.acinq.phoenix.android.controllerFactory
import fr.acinq.phoenix.android.databinding.ScanViewBinding
import fr.acinq.phoenix.android.utils.logger
import fr.acinq.phoenix.android.utils.readClipboard
import fr.acinq.phoenix.android.utils.whiteLowOp
import fr.acinq.phoenix.controllers.ControllerFactory
import fr.acinq.phoenix.controllers.ScanController
import fr.acinq.phoenix.controllers.payments.Scan
import kotlinx.coroutines.delay


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
    val vm: ScanDataViewModel = viewModel(factory = ScanDataViewModel.Factory(controllerFactory, CF::scan))
    MVIView(vm) { model, postIntent ->
        when (model) {
            Scan.Model.Ready, is Scan.Model.BadRequest, is Scan.Model.InvoiceFlow.DangerousRequest,
                is Scan.Model.LnurlWithdrawFlow, is Scan.Model.LnurlAuthFlow, is Scan.Model.LnurlServiceFetch, is Scan.Model.LnurlPayFlow -> {
                ReadDataView(
                    model = model,
                    onBackClick = onBackClick,
                    onErrorDone = { vm.model = Scan.Model.Ready },
                    onScannedText = { postIntent(Scan.Intent.Parse(request = it)) }
                )
                if (model is Scan.Model.InvoiceFlow.DangerousRequest) {
                    Column {
                        Text("Dangerous request!")
                        Button(text = "OK", onClick = {
                            postIntent(
                                Scan.Intent.InvoiceFlow.ConfirmDangerousRequest(
                                    request = model.request,
                                    paymentRequest = model.paymentRequest
                                )
                            )
                        })
                    }
                }
            }
            is Scan.Model.InvoiceFlow.InvoiceRequest -> {
                SendLightningPaymentView(
                    paymentRequest = model.paymentRequest,
                    onPayClick = { postIntent(it) }
                )
            }
            is Scan.Model.SwapOutFlow -> {
                SendSwapOutView(
                    model = model,
                    onInvalidate = { postIntent(it) },
                    onPrepareSwapOutClick = { postIntent(it) },
                    onSendSwapOutClick = { postIntent(it) }
                )
            }
            Scan.Model.InvoiceFlow.Sending -> Text("Sending payment...")
        }
    }
}


@Composable
fun ReadDataView(
    model: Scan.Model,
    onErrorDone: () -> Unit,
    onBackClick: () -> Unit,
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

        if (model is Scan.Model.BadRequest || model is Scan.Model.LnurlPayFlow || model is Scan.Model.LnurlAuthFlow || model is Scan.Model.LnurlServiceFetch || model is Scan.Model.LnurlWithdrawFlow) {
            val message = when (model) {
                is Scan.Model.BadRequest -> "Invalid request"
                is Scan.Model.LnurlServiceFetch -> "Fetching lnurl data"
                is Scan.Model.LnurlPayFlow -> "Lnurl-pay not suppported yet"
                is Scan.Model.LnurlWithdrawFlow -> "Lnurl-withdraw not suppported yet"
                is Scan.Model.LnurlAuthFlow -> "Lnurl-auth not suppported yet"
                else -> "Unhandled request!"
            }
            FeedbackText(
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(24.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(MaterialTheme.colors.surface)
                    .padding(16.dp),
                text = message,
                onTimeout = onErrorDone
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
        factory = { inflater, viewgroup, attach ->
            val binding = ScanViewBinding.inflate(inflater, viewgroup, attach)
            binding.scanView.let { scanView ->
                scanView.initializeFromIntent(Intent().apply {
                    putExtra(Intents.Scan.SCAN_TYPE, Intents.Scan.MIXED_SCAN)
                    putExtra(Intents.Scan.FORMATS, BarcodeFormat.QR_CODE.name)
                })
                scanView.decodeContinuous(object : BarcodeCallback {
                    override fun possibleResultPoints(resultPoints: MutableList<ResultPoint>?) = Unit
                    override fun barcodeResult(result: BarcodeResult?) {
                        result?.text?.let {
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
private fun FeedbackText(modifier: Modifier, text: String, onTimeout: () -> Unit = {}) {
    val currentOnTimeout by rememberUpdatedState(onTimeout)
    LaunchedEffect(true) {
        delay(2500)
        currentOnTimeout()
    }
    Text(
        text = text,
        modifier = modifier
    )
}
