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

package fr.acinq.phoenix.android.payments

import androidx.annotation.UiThread
import androidx.compose.foundation.*
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
import androidx.compose.ui.layout.FirstBaseline
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
import fr.acinq.phoenix.android.utils.*
import fr.acinq.phoenix.android.utils.Converter.toPrettyString
import fr.acinq.phoenix.android.utils.datastore.UserPrefs
import fr.acinq.phoenix.controllers.ControllerFactory
import fr.acinq.phoenix.controllers.ReceiveController
import fr.acinq.phoenix.controllers.payments.Receive
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

sealed class ReceiveViewState {
    object Default : ReceiveViewState()
    object EditInvoice : ReceiveViewState()
    data class Error(val e: Throwable) : ReceiveViewState()
}

private class ReceiveViewModel(controller: ReceiveController, description: String, expiry: Long) : MVIControllerViewModel<Receive.Model, Receive.Intent>(controller) {

    /** State of the view */
    var state by mutableStateOf<ReceiveViewState>(ReceiveViewState.Default)

    /** Bitmap containing the invoice/address qr code */
    var qrBitmap by mutableStateOf<ImageBitmap?>(null)
        private set

    /** Custom invoice description */
    var customDesc by mutableStateOf(description)

    /** Custom invoice amount */
    var customAmount by mutableStateOf<MilliSatoshi?>(null)

    /** Custom invoice expiry */
    var customExpiry by mutableStateOf<Long>(expiry)

    @UiThread
    fun generateInvoice() {
        val amount = customAmount
        val desc = customDesc
        val expiry = customExpiry
        viewModelScope.launch(Dispatchers.Default + CoroutineExceptionHandler { _, e ->
            log.error("failed to generate invoice with amount=$amount desc=$desc :", e)
            state = ReceiveViewState.Error(e)
        }) {
            log.info("generating invoice with amount=$amount desc=$desc")
            state = ReceiveViewState.Default
            controller.intent(Receive.Intent.Ask(amount = amount, desc = desc, expirySeconds = expiry))
        }
    }

    @UiThread
    fun generateQrCodeBitmap(invoice: String) {
        viewModelScope.launch(Dispatchers.Default) {
            log.info("generating qrcode for invoice=$invoice")
            try {
                qrBitmap = BitmapHelper.generateBitmap(invoice).asImageBitmap()
            } catch (e: Exception) {
                log.error("error when generating bitmap QR for invoice=$invoice:", e)
            }
        }
    }

    class Factory(
        private val controllerFactory: ControllerFactory,
        private val getController: ControllerFactory.() -> ReceiveController,
        private val description: String,
        private val expiry: Long,
    ) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            @Suppress("UNCHECKED_CAST")
            return ReceiveViewModel(controllerFactory.getController(), description, expiry) as T
        }
    }
}

@Composable
fun ReceiveView() {
    val log = logger("ReceiveView")

    val invoiceDefaultDesc by UserPrefs.getInvoiceDefaultDesc(LocalContext.current).collectAsState(null)
    val invoiceDefaultExpiry by UserPrefs.getInvoiceDefaultExpiry(LocalContext.current).collectAsState(null)
    safeLet(invoiceDefaultDesc, invoiceDefaultExpiry) { description, expiry ->
        val vm: ReceiveViewModel = viewModel(factory = ReceiveViewModel.Factory(controllerFactory, CF::receive, description, expiry))
        when (val state = vm.state) {
            is ReceiveViewState.Default -> DefaultView(vm = vm)
            is ReceiveViewState.EditInvoice -> EditInvoiceView(
                amount = vm.customAmount,
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
    val context = LocalContext.current
    val nc = navController
    DefaultScreenLayout(horizontalAlignment = Alignment.CenterHorizontally) {
        DefaultScreenHeader(onBackClick = { nc.popBackStack() }, backgroundColor = Color.Unspecified)
        MVIView(vm) { model, postIntent ->
            when (model) {
                is Receive.Model.Awaiting -> {
                    LaunchedEffect(key1 = true) { vm.generateInvoice() }
                    Text(stringResource(id = R.string.receive__generating))
                }
                is Receive.Model.Generating -> {
                    Text(stringResource(id = R.string.receive__generating))
                }
                is Receive.Model.Generated -> {
                    LaunchedEffect(model.request) {
                        vm.generateQrCodeBitmap(invoice = model.request)
                    }
                    val amount = model.amount
                    val description = model.desc
                    Spacer(modifier = Modifier.height(24.dp))
                    vm.qrBitmap?.let { QRCode(it) }
                    Spacer(modifier = Modifier.height(24.dp))
                    if (amount != null) {
                        QRCodeDetail(label = stringResource(id = R.string.receive__qr_amount_label), value = amount.toPrettyString(unit = LocalBitcoinUnit.current, rate = null, withUnit = true))
                    }
                    if (!description.isNullOrBlank()) {
                        QRCodeDetail(label = stringResource(id = R.string.receive__qr_desc_label), value = description)
                    }
                    Spacer(modifier = Modifier.height(24.dp))
                    CopyShareEditButtons(
                        onCopy = { copyToClipboard(context, data = model.request) },
                        onShare = { share(context, model.request, subject = "") },
                        onEdit = { vm.state = ReceiveViewState.EditInvoice })
                    Spacer(modifier = Modifier.height(24.dp))
                    BorderButton(
                        text = R.string.receive__swapin_button,
                        icon = R.drawable.ic_swap,
                        onClick = { postIntent(Receive.Intent.RequestSwapIn) })
                }
                is Receive.Model.SwapIn.Requesting -> Text(stringResource(id = R.string.receive__swapin__wait))
                is Receive.Model.SwapIn.Generated -> {
                    LaunchedEffect(model.address) {
                        vm.generateQrCodeBitmap(invoice = model.address)
                    }
                    Spacer(modifier = Modifier.height(24.dp))
                    vm.qrBitmap?.let { QRCode(it) }
                    Spacer(modifier = Modifier.height(24.dp))
                    QRCodeDetail(label = stringResource(id = R.string.receive__swapin__address_label), value = model.address)
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
            contentDescription = stringResource(id = R.string.receive__qr_about),
            alignment = Alignment.Center,
            modifier = Modifier
                .width(220.dp)
                .height(220.dp)
        )
    }
}

@Composable
private fun QRCodeDetail(label: String, value: String) {
    Row(modifier = Modifier.width(280.dp)) {
        Text(text = label.uppercase(), style = MaterialTheme.typography.caption.copy(fontSize = 12.sp), modifier = Modifier
            .alignBy(FirstBaseline)
            .weight(0.9f), textAlign = TextAlign.End)
        Spacer(modifier = Modifier.width(8.dp))
        Text(text = value, modifier = Modifier
            .alignBy(FirstBaseline)
            .weight(1f))
    }
}

@Composable
private fun CopyShareEditButtons(
    onCopy: () -> Unit,
    onShare: () -> Unit,
    onEdit: (() -> Unit)?,
) {
    Row(modifier = Modifier.padding(horizontal = 32.dp)) {
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
    amount: MilliSatoshi?,
    description: String,
    onDescriptionChange: (String) -> Unit,
    onAmountChange: (MilliSatoshi?) -> Unit,
    onSubmit: () -> Unit,
    onCancel: () -> Unit
) {
    val log = logger("EditInvoiceView")
    DefaultScreenLayout {
        DefaultScreenHeader(
            title = stringResource(id = R.string.receive__edit__title),
            subtitle = stringResource(id = R.string.receive__edit__subtitle),
            onBackClick = onCancel
        )
        Card(internalPadding = PaddingValues(16.dp)) {
            AmountInput(
                initialAmount = amount,
                label = { Text(text = stringResource(id = R.string.receive__edit__amount_label)) },
                placeholder = { Text(text = stringResource(id = R.string.receive__edit__amount_placeholder)) },
                onAmountChange = { complexAmount ->
                    log.debug { "invoice amount update amount=$complexAmount" }
                    onAmountChange(complexAmount?.amount)
                },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(16.dp))
            TextInput(
                text = description,
                onTextChange = onDescriptionChange,
                label = { Text(stringResource(id = R.string.receive__edit__desc_label)) },
                maxLines = 3,
                maxChars = 180,
                modifier = Modifier.fillMaxWidth()
            )
        }
        Card {
            SettingButton(
                text = R.string.receive__edit__generate_button,
                icon = R.drawable.ic_qrcode,
                onClick = onSubmit
            )
        }
    }
}
