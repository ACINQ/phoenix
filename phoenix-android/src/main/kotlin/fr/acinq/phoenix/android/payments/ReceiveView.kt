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

import android.content.Context
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.FirstBaseline
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import fr.acinq.lightning.MilliSatoshi
import fr.acinq.lightning.channel.Aborted
import fr.acinq.lightning.channel.Closed
import fr.acinq.lightning.channel.Closing
import fr.acinq.lightning.utils.sat
import fr.acinq.phoenix.android.*
import fr.acinq.phoenix.android.R
import fr.acinq.phoenix.android.components.*
import fr.acinq.phoenix.android.components.mvi.MVIView
import fr.acinq.phoenix.android.utils.*
import fr.acinq.phoenix.android.utils.Converter.toPrettyString
import fr.acinq.phoenix.android.utils.datastore.UserPrefs
import fr.acinq.phoenix.controllers.payments.Receive
import fr.acinq.phoenix.data.BitcoinUnit
import fr.acinq.phoenix.managers.WalletBalance
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flattenMerge
import kotlinx.coroutines.flow.map

sealed class ReceiveViewState {
    object Default : ReceiveViewState()
    object EditInvoice : ReceiveViewState()
    data class Error(val e: Throwable) : ReceiveViewState()
}

@Composable
fun ReceiveView(
    onSwapInReceived: () -> Unit
) {
    val log = logger("ReceiveView")
    val nc = navController
    val context = LocalContext.current
    val business = business
    val invoiceDefaultDesc by UserPrefs.getInvoiceDefaultDesc(context).collectAsState(null)
    val invoiceDefaultExpiry by UserPrefs.getInvoiceDefaultExpiry(context).collectAsState(null)

    safeLet(invoiceDefaultDesc, invoiceDefaultExpiry) { description, expiry ->
        val vm: ReceiveViewModel = viewModel(factory = ReceiveViewModel.Factory(controllerFactory, CF::receive, description, expiry))
        MVIView(vm) { model, postIntent ->
            // effect when a swap-in is received
            LaunchedEffect(key1 = Unit) {
                var previousBalance: WalletBalance? = null
                business.balanceManager.swapInWalletBalance.collect {
                    if (previousBalance != null && it.total > 0.sat && it != previousBalance) {
                        onSwapInReceived()
                    } else {
                        previousBalance = it
                    }
                }
            }
            // back action handler
            val onBack: () -> Unit = {
                when (vm.state) {
                    is ReceiveViewState.EditInvoice -> vm.state = ReceiveViewState.Default
                    else -> when (model) {
                        is Receive.Model.SwapIn -> {
                            vm.generateInvoice()
                        }
                        else -> nc.popBackStack()
                    }
                }
            }
            DefaultScreenLayout(horizontalAlignment = Alignment.CenterHorizontally) {
                DefaultScreenHeader(
                    title = when (vm.state) {
                        is ReceiveViewState.EditInvoice -> stringResource(id = R.string.receive__edit__title)
                        else -> null
                    },
                    onBackClick = onBack,
                    backgroundColor = Color.Unspecified,
                )
                when (vm.state) {
                    is ReceiveViewState.EditInvoice -> EditInvoiceView(
                        amount = vm.customAmount,
                        description = vm.customDesc,
                        onDescriptionChange = { vm.customDesc = it },
                        onAmountChange = { vm.customAmount = it },
                        onSubmit = { vm.generateInvoice() },
                    )
                    is ReceiveViewState.Default -> {
                        when (model) {
                            is Receive.Model.Awaiting -> {
                                LaunchedEffect(key1 = true) { vm.generateInvoice() }
                                GeneratingLightningInvoiceView({ vm.state = ReceiveViewState.EditInvoice }) { vm.state = ReceiveViewState.EditInvoice }
                            }
                            is Receive.Model.Generating -> {
                                GeneratingLightningInvoiceView({ vm.state = ReceiveViewState.EditInvoice }) { vm.state = ReceiveViewState.EditInvoice }
                            }
                            is Receive.Model.Generated -> {
                                LaunchedEffect(model.request) {
                                    vm.generateQrCodeBitmap(invoice = model.request)
                                }
                                LightningInvoiceView(
                                    context = context,
                                    amount = model.amount,
                                    description = model.desc,
                                    paymentRequest = model.request,
                                    bitmap = vm.qrBitmap,
                                    onEdit = { vm.state = ReceiveViewState.EditInvoice },
                                    onSwapInClick = { postIntent(Receive.Intent.RequestSwapIn) },
                                )
                            }
                            is Receive.Model.SwapIn -> {
                                LaunchedEffect(model.address) {
                                    vm.generateQrCodeBitmap(invoice = model.address)
                                }
                                SwapInView(context = context, address = model.address, bitmap = vm.qrBitmap)
                            }
                        }
                    }
                    is ReceiveViewState.Error -> Text("Failed to generate an invoice. Please try again.")
                }
            }
        }
    }
}

@Composable
private fun GeneratingLightningInvoiceView(
    onSwapInClick: () -> Unit,
    onEdit: () -> Unit,
) {
    Spacer(modifier = Modifier.height(24.dp))
    Box(contentAlignment = Alignment.Center) {
        QRCodeView(bitmap = null, address = null)
        ProgressView(
            text = stringResource(id = R.string.receive__generating),
            modifier = Modifier
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colors.surface),
        )
    }
    Spacer(modifier = Modifier.height(24.dp))
    CopyShareEditButtons(
        onCopy = { },
        onShare = { },
        onEdit = onEdit
    )
    Spacer(modifier = Modifier.height(24.dp))
    BorderButton(
        text = stringResource(id = R.string.receive__swapin_button),
        icon = R.drawable.ic_swap,
        onClick = onSwapInClick
    )
}

@OptIn(FlowPreview::class)
@Composable
private fun LightningInvoiceView(
    context: Context,
    amount: MilliSatoshi?,
    description: String?,
    paymentRequest: String,
    bitmap: ImageBitmap?,
    onEdit: () -> Unit,
    onSwapInClick: () -> Unit
) {
    Spacer(modifier = Modifier.height(24.dp))
    QRCodeView(address = paymentRequest, bitmap = bitmap, details = {
        if (amount != null || !description.isNullOrBlank()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onEdit)
                    .padding(bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (amount != null) {
                    QRCodeDetail(label = stringResource(id = R.string.receive__qr_amount_label)) {
                        AmountWithFiatView(
                            amount = amount,
                            amountTextStyle = MaterialTheme.typography.body2.copy(fontSize = 14.sp),
                            altTextStyle = MaterialTheme.typography.caption.copy(fontSize = 14.sp),
                        )
                    }
                }
                if (!description.isNullOrBlank()) {
                    QRCodeDetail(
                        label = stringResource(id = R.string.receive__qr_desc_label),
                        value = description,
                        maxLines = 2
                    )
                }
            }
        }
    })
    Spacer(modifier = Modifier.height(24.dp))
    CopyShareEditButtons(
        onCopy = { copyToClipboard(context, data = paymentRequest) },
        onShare = { share(context, "lightning:$paymentRequest", context.getString(R.string.receive__share__subject), context.getString(R.string.receive_share_title)) },
        onEdit = onEdit
    )
    Spacer(modifier = Modifier.height(24.dp))
    LocalWalletContext.current?.payToOpen?.v1?.minFundingSat?.sat?.let { minPayToOpenAmount ->
        val channels by business.peerManager.peerState.filterNotNull().map { it.channelsFlow }.flattenMerge().collectAsState(initial = null)
        if (channels != null && channels!!.filterNot { it.value is Closed || it.value is Closing  || it.value is Aborted}.isEmpty()) {
            Text(
                text = annotatedStringResource(id = R.string.receive__min_amount_pay_to_open, minPayToOpenAmount.toPrettyString(BitcoinUnit.Sat, withUnit = true)),
                style = MaterialTheme.typography.body1.copy(fontSize = 14.sp),
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .widthIn(max = 400.dp)
                    .padding(horizontal = 32.dp)
            )
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
    BorderButton(
        text = stringResource(id = R.string.receive__swapin_button),
        icon = R.drawable.ic_swap,
        onClick = onSwapInClick
    )
}

@Composable
private fun SwapInView(
    context: Context,
    address: String,
    bitmap: ImageBitmap?
) {
    Spacer(modifier = Modifier.height(24.dp))
    QRCodeView(address = address, bitmap = bitmap) {
        QRCodeDetail(label = stringResource(id = R.string.receive__swapin__address_label), value = address)
        Spacer(modifier = Modifier.height(16.dp))
    }
    Spacer(modifier = Modifier.height(24.dp))
    CopyShareEditButtons(
        onCopy = { copyToClipboard(context, data = address) },
        onShare = { share(context, "bitcoin:$address", context.getString(R.string.receive__share__subject), context.getString(R.string.receive_share_title)) },
        onEdit = null
    )
    LocalWalletContext.current?.swapIn?.v1?.let { swapInConfig ->
        val minFunding = swapInConfig.minFundingSat.sat
        val feePercent = String.format("%.2f", 100 * (swapInConfig.feePercent))
        val minFee = swapInConfig.minFeeSat
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            text = annotatedStringResource(id = R.string.receive__swapin__disclaimer, minFunding.toPrettyString(BitcoinUnit.Sat, withUnit = true), feePercent, minFee),
            style = MaterialTheme.typography.body1.copy(fontSize = 14.sp),
            textAlign = TextAlign.Center,
            modifier = Modifier
                .widthIn(max = 400.dp)
                .padding(horizontal = 32.dp)
        )
    }
}

@Composable
private fun QRCodeView(
    address: String?,
    bitmap: ImageBitmap?,
    details: @Composable () -> Unit = {},
) {
    val context = LocalContext.current
    Column(
        modifier = Modifier
            .width(270.dp)
            .clip(RoundedCornerShape(16.dp))
            .border(
                border = BorderStroke(1.dp, MaterialTheme.colors.primary),
                shape = RoundedCornerShape(16.dp)
            )
            .background(Color.White),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        QRCodeImage(bitmap) { address?.let { copyToClipboard(context, it) } }
        details()
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun QRCodeImage(
    bitmap: ImageBitmap?,
    onLongClick: () -> Unit,
) {
    var showFullScreenQR by remember { mutableStateOf(false) }
    val image: @Composable () -> Unit = {
        if (bitmap == null) {
            Image(
                painter = painterResource(id = R.drawable.qrcode_placeholder),
                contentDescription = null,
                alignment = Alignment.Center,
                contentScale = ContentScale.FillWidth,
            )
        } else {
            Image(
                bitmap = bitmap,
                contentDescription = stringResource(id = R.string.receive__qr_about),
                alignment = Alignment.Center,
                contentScale = ContentScale.FillWidth,
            )
        }
    }
    Surface(
        Modifier
            .combinedClickable(
                role = Role.Button,
                onClick = { if (bitmap != null) showFullScreenQR = true },
                onLongClick = onLongClick,
            )
            .fillMaxWidth()
            .background(Color.White)
            .padding(24.dp)
    ) { image() }

    if (showFullScreenQR) {
        FullScreenDialog(onDismiss = { showFullScreenQR = false }) {
            Surface(
                Modifier
                    .fillMaxWidth()
                    .background(Color.White)
                    .padding(16.dp)
            ) { image() }
        }
    }
}

@Composable
private fun QRCodeDetail(label: String, value: String, maxLines: Int = Int.MAX_VALUE) {
    QRCodeDetail(label) {
        SelectionContainer {
            Text(
                text = value,
                style = MaterialTheme.typography.body2.copy(fontSize = 14.sp),
                maxLines = maxLines,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun QRCodeDetail(label: String, content: @Composable () -> Unit) {
    Row(modifier = Modifier.padding(horizontal = 24.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.caption.copy(fontSize = 14.sp, textAlign = TextAlign.End),
            modifier = Modifier.alignBy(FirstBaseline)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Column(modifier = Modifier.alignBy(FirstBaseline)) {
            content()
        }
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
                text = stringResource(id = R.string.receive__edit_button),
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
) {
    val log = logger("EditInvoiceView")
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
        Spacer(Modifier.height(8.dp))
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
