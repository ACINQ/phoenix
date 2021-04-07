/*
 * Copyright 2020 ACINQ SAS
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

package fr.acinq.phoenix.android.receive

import androidx.annotation.UiThread
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import fr.acinq.lightning.MilliSatoshi
import fr.acinq.phoenix.android.*
import fr.acinq.phoenix.android.R
import fr.acinq.phoenix.android.components.*
import fr.acinq.phoenix.android.components.mvi.MVIControllerViewModel
import fr.acinq.phoenix.android.components.mvi.MVIView
import fr.acinq.phoenix.android.utils.QRCode
import fr.acinq.phoenix.android.utils.copyToClipboard
import fr.acinq.phoenix.android.utils.logger
import fr.acinq.phoenix.ctrl.ControllerFactory
import fr.acinq.phoenix.ctrl.Receive
import fr.acinq.phoenix.ctrl.ReceiveController
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

sealed class ReceiveViewState {
    object Default : ReceiveViewState()
    object EditInvoice : ReceiveViewState()
    data class Error(val e: Throwable) : ReceiveViewState()
}

private class ReceiveViewModel(controller: ReceiveController) : MVIControllerViewModel<Receive.Model, Receive.Intent>(controller) {

    /** State of the view */
    var state by mutableStateOf<ReceiveViewState>(ReceiveViewState.Default)

    /** Bitmap containing the invoice/address qr code */
    var qrBitmap by mutableStateOf<ImageBitmap?>(null)
        private set

    /** Custom invoice description */
    var customDesc by mutableStateOf("")

    /** Custom invoice amount */
    var customAmount by mutableStateOf<MilliSatoshi?>(null)

    @UiThread
    fun generateInvoice() {
        val amount = customAmount
        val desc = customDesc
        viewModelScope.launch(Dispatchers.Default + CoroutineExceptionHandler { _, e ->
            log.error(e) { "failed to generate invoice with amount=$amount desc=$desc" }
            state = ReceiveViewState.Error(e)
        }) {
            log.info { "generating invoice with amount=$amount desc=$desc" }
            state = ReceiveViewState.Default
            controller.intent(Receive.Intent.Ask(amount = amount, desc = desc))
        }
    }

    @UiThread
    fun generateQrCodeBitmap(invoice: String) {
        viewModelScope.launch(Dispatchers.Default) {
            log.info { "generating qrcode for invoice=$invoice" }
            try {
                qrBitmap = QRCode.generateBitmap(invoice).asImageBitmap()
            } catch (e: Exception) {
                log.error(e) { "error when generating bitmap QR for invoice=$invoice" }
            }
        }
    }

    class Factory(
        private val controllerFactory: ControllerFactory,
        private val getController: ControllerFactory.() -> ReceiveController
    ) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            @Suppress("UNCHECKED_CAST")
            return ReceiveViewModel(controllerFactory.getController()) as T
        }
    }
}

@Composable
fun ReceiveView() {
    requireWalletPresent(inScreen = Screen.Receive) {
        val log = logger()
        val vm: ReceiveViewModel = viewModel(factory = ReceiveViewModel.Factory(controllerFactory, CF::receive))
        when (val state = vm.state) {
            is ReceiveViewState.Default -> DefaultView(vm = vm)
            is ReceiveViewState.EditInvoice -> EditInvoiceView(
                description = vm.customDesc,
                onDescriptionChange = { vm.customDesc = it },
                onAmountChange = { vm.customAmount = it },
                onSubmit = { vm.generateInvoice() },
                onCancel = { vm.state = ReceiveViewState.Default })
            is ReceiveViewState.Error -> Text("There was an error: ${state.e.localizedMessage}")
        }
    }
}

@Composable
private fun DefaultView(vm: ReceiveViewModel) {
    val context = LocalContext.current.applicationContext
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Top,
    ) {
        val nc = navController
        ScreenHeader(onBackClick = { nc.popBackStack() }, backgroundColor = Color.Unspecified)
        MVIView(vm) { model, postIntent ->
            when (model) {
                is Receive.Model.Awaiting -> {
                    SideEffect { vm.generateInvoice() }
                    Text(stringResource(id = R.string.receive__generating))
                }
                is Receive.Model.Generating -> {
                    Text(stringResource(id = R.string.receive__generating))
                }
                is Receive.Model.Generated -> {
                    vm.generateQrCodeBitmap(invoice = model.request)
                    Spacer(modifier = Modifier.height(24.dp))
                    vm.qrBitmap?.let { QRCode(it) }
                    Spacer(modifier = Modifier.height(24.dp))
                    CopyShareEditButtons(
                        onCopy = { copyToClipboard(context, data = model.request) },
                        onShare = { /*TODO*/ },
                        onEdit = { vm.state = ReceiveViewState.EditInvoice })
                    Spacer(modifier = Modifier.height(24.dp))
                    BorderButton(
                        text = R.string.receive__swapin_button,
                        icon = R.drawable.ic_swap,
                        onClick = { postIntent(Receive.Intent.RequestSwapIn) })
                    Spacer(modifier = Modifier.height(8.dp))
                    BorderButton(
                        text = R.string.receive__lnurl_withdraw,
                        icon = R.drawable.ic_scan,
                        onClick = { })
                }
                is Receive.Model.SwapIn.Requesting -> Text(stringResource(id = R.string.receive__swapin__wait))
                is Receive.Model.SwapIn.Generated -> {
                    vm.generateQrCodeBitmap(invoice = model.address)
                    Spacer(modifier = Modifier.height(24.dp))
                    vm.qrBitmap?.let { QRCode(it) }
                    Spacer(modifier = Modifier.height(24.dp))
                    Row(modifier = Modifier.padding(horizontal = 32.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(id = R.string.receive__swapin__address_label))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(model.address, style = monoTypo(), modifier = Modifier.widthIn(32.dp, 250.dp))
                    }
                    Spacer(modifier = Modifier.height(24.dp))
                    CopyShareEditButtons(
                        onCopy = { copyToClipboard(context, data = model.address) },
                        onShare = { /*TODO*/ },
                        onEdit = null
                    )
                }
            }
        }
    }
}

@Composable
fun QRCode(bitmap: ImageBitmap) {
    Surface(
        Modifier
            .clip(RoundedCornerShape(16.dp))
            .border(
                BorderStroke(1.dp, MaterialTheme.colors.primary),
                shape = RoundedCornerShape(16.dp)
            )
            .background(Color.White)
            .padding(24.dp)
    ) {
        Image(
            bitmap = bitmap,
            contentDescription = "invoice qr code",
            alignment = Alignment.Center,
            modifier = Modifier
                .width(220.dp)
                .height(220.dp)
        )
    }
}

@Composable
fun CopyShareEditButtons(
    onCopy: () -> Unit,
    onShare: () -> Unit,
    onEdit: (() -> Unit)?,
) {
    Row {
        BorderButton(
            icon = R.drawable.ic_copy,
            onClick = onCopy
        )
        Spacer(modifier = Modifier.width(16.dp))
        BorderButton(
            icon = R.drawable.ic_share,
            onClick = onShare
        )
        if (onEdit != null) {
            Spacer(modifier = Modifier.width(16.dp))
            BorderButton(
                text = R.string.receive__edit_button,
                icon = R.drawable.ic_edit,
                onClick = onEdit
            )
        }
    }
}

@Composable
private fun EditInvoiceView(
    description: String,
    onDescriptionChange: (String) -> Unit,
    onAmountChange: (MilliSatoshi?) -> Unit,
    onSubmit: () -> Unit,
    onCancel: () -> Unit
) {
    val log = logger()
    Column {
        ScreenHeader(
            title = stringResource(id = R.string.receive__edit__title),
            subtitle = stringResource(id = R.string.receive__edit__subtitle),
            onBackClick = onCancel
        )
        ScreenBody {
            Text(stringResource(id = R.string.receive__edit__amount_label), modifier = Modifier.fillMaxWidth())
            Spacer(modifier = Modifier.height(8.dp))
            AmountInput(
                initialAmount = null,
                onAmountChange = { amount, amountFiat, fiatCode ->
                    log.info { "invoice amount update amount=$amount msat fiat=$amountFiat $fiatCode" }
                    onAmountChange(amount)
                },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(24.dp))
            Text(stringResource(id = R.string.receive__edit__desc_label), modifier = Modifier.fillMaxWidth())
            Spacer(modifier = Modifier.height(8.dp))
            InputText(
                text = description, // TODO use value from prefs
                onTextChange = onDescriptionChange,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(32.dp))
            BorderButton(
                text = R.string.receive__edit__generate_button,
                icon = R.drawable.ic_qrcode,
                onClick = onSubmit
            )
        }
    }
}
